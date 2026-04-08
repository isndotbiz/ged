<script lang="ts">
  import { t } from '$lib/i18n';
  import { getAllPersons, getDb, getFamilies } from '$lib/db';
  import type { Contradiction } from '$lib/types';

  let contradictions = $state<Contradiction[]>([]);
  let isRunning = $state(false);
  let hasRun = $state(false);
  let filter = $state<string | null>(null);

  function extractYear(dateStr: string): number | null {
    const m = dateStr.match(/(\d{4})/);
    return m ? parseInt(m[1]) : null;
  }

  function haversineKm(aLat: number, aLon: number, bLat: number, bLon: number): number {
    const toRad = (deg: number) => (deg * Math.PI) / 180;
    const dLat = toRad(bLat - aLat);
    const dLon = toRad(bLon - aLon);
    const lat1 = toRad(aLat);
    const lat2 = toRad(bLat);
    const x = Math.sin(dLat / 2) ** 2 + Math.sin(dLon / 2) ** 2 * Math.cos(lat1) * Math.cos(lat2);
    return 6371 * (2 * Math.atan2(Math.sqrt(x), Math.sqrt(1 - x)));
  }

  async function runAnalysis() {
    isRunning = true;
    const results: Contradiction[] = [];
    const persons = await getAllPersons();
    const families = await getFamilies();
    const db = await getDb();
    const events = await db.select<{ ownerXref: string; eventType: string; dateValue: string; place: string }[]>(`SELECT ownerXref, eventType, dateValue, place FROM event`);
    const childLinks = await db.select<{ familyXref: string; childXref: string }[]>(`SELECT familyXref, childXref FROM child_link`);
    const places = await db.select<{ name: string; latitude: number | null; longitude: number | null }[]>(`SELECT name, latitude, longitude FROM place`);
    let idCounter = 0;

    const personByXref = new Map(persons.map((p) => [p.xref, p]));
    const eventsByOwner = new Map<string, { ownerXref: string; eventType: string; dateValue: string; place: string }[]>();
    for (const evt of events) {
      if (!eventsByOwner.has(evt.ownerXref)) eventsByOwner.set(evt.ownerXref, []);
      eventsByOwner.get(evt.ownerXref)!.push(evt);
    }
    const placeCoords = new Map(places.map((p) => [p.name.trim().toLowerCase(), { lat: p.latitude, lon: p.longitude }]));
    const childrenByFamily = new Map<string, string[]>();
    for (const cl of childLinks) {
      if (!childrenByFamily.has(cl.familyXref)) childrenByFamily.set(cl.familyXref, []);
      childrenByFamily.get(cl.familyXref)!.push(cl.childXref);
    }

    for (const person of persons) {
      const pEvents = eventsByOwner.get(person.xref) || [];
      const birth = pEvents.find(e => e.eventType === 'BIRT');
      const death = pEvents.find(e => e.eventType === 'DEAT');
      const birthYear = birth ? extractYear(birth.dateValue) : (person.birthDate ? extractYear(person.birthDate) : null);
      const deathYear = death ? extractYear(death.dateValue) : (person.deathDate ? extractYear(person.deathDate) : null);

      if (birthYear && deathYear && birthYear > deathYear) {
        results.push({ id: `c${idCounter++}`, severity: 'critical', category: 'Impossible Date', title: `Born after death: ${person.givenName} ${person.surname}`, detail: `Born ${birthYear}, died ${deathYear}`, personXrefs: [person.xref], suggestion: 'Check birth or death date' });
      }

      if (birthYear && deathYear && (deathYear - birthYear) > 120) {
        results.push({ id: `c${idCounter++}`, severity: 'warning', category: 'Unlikely Age', title: `Lived ${deathYear - birthYear} years: ${person.givenName} ${person.surname}`, detail: `Born ${birthYear}, died ${deathYear}`, personXrefs: [person.xref], suggestion: 'Verify dates' });
      }

      const duplicateKeys = new Map<string, number>();
      const sameDatePlaces = new Map<string, string[]>();
      for (const evt of pEvents) {
        const duplicateKey = `${evt.eventType}|${evt.dateValue}|${evt.place}`;
        duplicateKeys.set(duplicateKey, (duplicateKeys.get(duplicateKey) || 0) + 1);
        if (evt.dateValue && evt.place) {
          const dateKey = evt.dateValue.trim();
          if (!sameDatePlaces.has(dateKey)) sameDatePlaces.set(dateKey, []);
          sameDatePlaces.get(dateKey)!.push(evt.place);
        }
      }
      for (const [key, count] of duplicateKeys) {
        if (count < 2) continue;
        const [eventType, dateValue, place] = key.split('|');
        results.push({
          id: `c${idCounter++}`,
          severity: 'warning',
          category: 'Duplicate Event',
          title: `Possible duplicate ${eventType} event`,
          detail: `${person.givenName} ${person.surname} has ${count} events on ${dateValue || 'unknown date'} at ${place || 'unknown place'}`,
          personXrefs: [person.xref],
          suggestion: 'Merge or remove duplicate events',
        });
      }

      for (const evt of pEvents) {
        if (evt.eventType === 'BIRT' || evt.eventType === 'DEAT') continue;
        const yr = extractYear(evt.dateValue);
        if (!yr) continue;
        const type = evt.eventType === 'MARR' ? 'Marriage' : evt.eventType === 'BURI' ? 'Burial' : evt.eventType;
        if (birthYear && yr < birthYear - 1) {
          results.push({ id: `c${idCounter++}`, severity: 'critical', category: 'Impossible Date', title: `${type} before birth: ${person.givenName} ${person.surname}`, detail: `${type} in ${yr}, born ${birthYear}`, personXrefs: [person.xref], suggestion: `Check ${type.toLowerCase()} date` });
        }
        if (deathYear && yr > deathYear + 1) {
          results.push({ id: `c${idCounter++}`, severity: 'critical', category: 'Impossible Date', title: `${type} after death: ${person.givenName} ${person.surname}`, detail: `${type} in ${yr}, died ${deathYear}`, personXrefs: [person.xref], suggestion: `Check ${type.toLowerCase()} or death date` });
        }
      }

      for (const [date, placesOnDate] of sameDatePlaces) {
        if (placesOnDate.length < 2) continue;
        for (let i = 0; i < placesOnDate.length; i++) {
          for (let j = i + 1; j < placesOnDate.length; j++) {
            const a = placeCoords.get(placesOnDate[i].trim().toLowerCase());
            const b = placeCoords.get(placesOnDate[j].trim().toLowerCase());
            if (
              a?.lat === null || a?.lat === undefined ||
              a?.lon === null || a?.lon === undefined ||
              b?.lat === null || b?.lat === undefined ||
              b?.lon === null || b?.lon === undefined
            ) continue;
            const km = haversineKm(a.lat, a.lon, b.lat, b.lon);
            if (km > 500) {
              results.push({
                id: `c${idCounter++}`,
                severity: 'warning',
                category: 'Impossible Geography',
                title: `Distant places on same date: ${person.givenName} ${person.surname}`,
                detail: `${placesOnDate[i]} and ${placesOnDate[j]} on ${date} (${Math.round(km)}km apart)`,
                personXrefs: [person.xref],
                suggestion: 'Verify event dates or place names',
              });
            }
          }
        }
      }
    }

    const parentByChild = new Map<string, string[]>();
    for (const fam of families) {
      const children = childrenByFamily.get(fam.xref) || [];
      for (const childXref of children) {
        const parents = [fam.partner1Xref, fam.partner2Xref].filter(Boolean);
        if (!parentByChild.has(childXref)) parentByChild.set(childXref, []);
        parentByChild.get(childXref)!.push(...parents);

        const child = personByXref.get(childXref);
        const childBirth = extractYear(child?.birthDate || '');
        for (const parentXref of parents) {
          const parent = personByXref.get(parentXref);
          if (!parent || !childBirth) continue;
          const parentBirth = extractYear(parent.birthDate || '');
          if (parentBirth && (childBirth - parentBirth) < 12) {
            results.push({
              id: `c${idCounter++}`,
              severity: 'warning',
              category: 'Parent Age',
              title: `Parent younger than child threshold`,
              detail: `${parent.givenName} ${parent.surname} was ${childBirth - parentBirth} when ${child?.givenName} was born`,
              personXrefs: [childXref, parentXref],
              suggestion: 'Verify parent-child relationship and birth dates',
            });
          }
          if (parentBirth) {
            const parentAgeAtBirth = childBirth - parentBirth;
            if (parentAgeAtBirth < 10 || parentAgeAtBirth > 80) {
              results.push({
                id: `c${idCounter++}`,
                severity: 'warning',
                category: 'Parent Age',
                title: `Impossible age at parenthood`,
                detail: `${parent.givenName} ${parent.surname} was ${parentAgeAtBirth} when ${child?.givenName} was born`,
                personXrefs: [childXref, parentXref],
                suggestion: 'Verify parent-child links and birth dates',
              });
            }
          }
          if ((parent.sex || '').toUpperCase() === 'F' && parentBirth && (childBirth - parentBirth) > 50) {
            results.push({
              id: `c${idCounter++}`,
              severity: 'warning',
              category: 'Parent Age',
              title: `Mother older than 50 at childbirth`,
              detail: `${parent.givenName} ${parent.surname} was ${childBirth - parentBirth} when ${child?.givenName} was born`,
              personXrefs: [childXref, parentXref],
              suggestion: 'Verify mother and child birth dates',
            });
          }
        }
      }
      const marriageYear = extractYear(fam.marriageDate || '');
      if (marriageYear) {
        const children = childrenByFamily.get(fam.xref) || [];
        for (const childXref of children) {
          const child = personByXref.get(childXref);
          const childBirthYear = extractYear(child?.birthDate || '');
          if (childBirthYear && childBirthYear < marriageYear) {
            results.push({
              id: `c${idCounter++}`,
              severity: 'warning',
              category: 'Family Timeline',
              title: 'Child born before marriage',
              detail: `${child?.givenName || childXref} born in ${childBirthYear} before family marriage in ${marriageYear}`,
              personXrefs: [childXref, fam.partner1Xref, fam.partner2Xref].filter(Boolean) as string[],
              suggestion: 'Verify child birth date and marriage date',
            });
          }
        }
        for (const partnerXref of [fam.partner1Xref, fam.partner2Xref]) {
          const partner = personByXref.get(partnerXref);
          if (!partner) continue;
          const birthYear = extractYear(partner.birthDate || '');
          if (birthYear && (marriageYear - birthYear) < 14) {
            results.push({
              id: `c${idCounter++}`,
              severity: 'warning',
              category: 'Marriage Age',
              title: `Marriage before age 14`,
              detail: `${partner.givenName} ${partner.surname} married at age ${marriageYear - birthYear}`,
              personXrefs: [partnerXref],
              suggestion: 'Verify marriage date or birth date',
            });
          }
        }
      }
    }

    const seenCycles = new Set<string>();
    for (const p of persons) {
      const queue = [p.xref];
      const seen = new Set<string>();
      while (queue.length > 0) {
        const current = queue.shift()!;
        if (seen.has(current)) continue;
        seen.add(current);
        const parents = parentByChild.get(current) || [];
        for (const parentXref of parents) {
          if (parentXref === p.xref) {
            const key = `${p.xref}-cycle`;
            if (!seenCycles.has(key)) {
              seenCycles.add(key);
              results.push({
                id: `c${idCounter++}`,
                severity: 'critical',
                category: 'Circular Relationship',
                title: `Circular ancestry detected`,
                detail: `${p.givenName} ${p.surname} appears in their own ancestry chain`,
                personXrefs: [p.xref],
                suggestion: 'Review parent-child links for cycle errors',
              });
            }
          } else {
            queue.push(parentXref);
          }
        }
      }
    }

    contradictions = results.sort((a, b) => {
      const sev = { critical: 0, warning: 1, info: 2 };
      return (sev[a.severity] ?? 3) - (sev[b.severity] ?? 3);
    });
    isRunning = false;
    hasRun = true;
  }

  let filtered = $derived(filter ? contradictions.filter(c => c.category === filter) : contradictions);
  let categories = $derived([...new Set(contradictions.map(c => c.category))]);
  let criticalCount = $derived(contradictions.filter(c => c.severity === 'critical').length);
  let warningCount = $derived(contradictions.filter(c => c.severity === 'warning').length);
</script>

<div class="p-8 max-w-4xl animate-fade-in">
  <div class="flex items-center justify-between mb-8">
    <div>
      <h1 class="text-2xl font-bold tracking-tight" style="font-family: var(--font-serif); color: var(--ink);">{t('contradictions.title')}</h1>
      <p class="text-sm text-ink-muted mt-1">{t('contradictions.subtitle')}</p>
    </div>
    <button onclick={runAnalysis} disabled={isRunning} class="px-4 py-2 text-sm font-medium btn-accent text-white rounded-lg disabled:opacity-50 transition-colors" aria-label={t('common.actions')}>
      {isRunning ? t('common.analyzing') : t('contradictions.runAnalysis')}
    </button>
  </div>

  {#if hasRun}
    <div class="flex gap-3 mb-6">
      <div class="px-3 py-1.5 rounded-lg bg-red-50 text-red-700 text-sm font-medium">{criticalCount} Critical</div>
      <div class="px-3 py-1.5 rounded-lg bg-orange-50 text-orange-700 text-sm font-medium">{warningCount} Warnings</div>
      <div class="px-3 py-1.5 rounded-lg text-sm font-medium" style="background: var(--parchment); color: var(--ink-light);">{contradictions.length} Total</div>
    </div>

    {#if categories.length > 1}
      <div class="flex gap-2 mb-6">
        <button onclick={() => filter = null} class="px-3 py-1 text-xs rounded-full {filter === null ? 'btn-filter-active' : 'btn-filter'}">All</button>
        {#each categories as cat}
          <button onclick={() => filter = filter === cat ? null : cat} class="px-3 py-1 text-xs rounded-full {filter === cat ? 'btn-filter-active' : 'btn-filter'}">{cat}</button>
        {/each}
      </div>
    {/if}

    {#if filtered.length === 0}
      <div class="arch-card rounded-xl p-8 text-center">
        <p class="text-ink-muted">{t('contradictions.noContradictions')}</p>
      </div>
    {:else}
      <div class="space-y-3">
        {#each filtered as c}
          <div class="arch-card rounded-xl p-4">
            <div class="flex items-center gap-2 mb-2">
              <span class="px-2 py-0.5 text-[10px] font-semibold rounded-full {c.severity === 'critical' ? 'bg-red-100 text-red-700' : c.severity === 'warning' ? 'bg-orange-100 text-orange-700' : 'bg-blue-100 text-blue-700'}">{c.severity}</span>
              <span class="px-2 py-0.5 text-[10px] rounded-full" style="background: var(--parchment); color: var(--ink-light);">{c.category}</span>
            </div>
            <div class="font-medium text-sm text-ink">{c.title}</div>
            <div class="text-xs text-ink-muted mt-1">{c.detail}</div>
            <div class="text-xs text-blue-600 mt-1">{c.suggestion}</div>
          </div>
        {/each}
      </div>
    {/if}
  {:else}
    <div class="arch-card rounded-xl p-8 text-center">
      <p class="text-ink-muted text-sm">{t('contradictions.clickRunAnalysis')}</p>
      <p class="text-ink-faint text-xs mt-1">Checks: impossible dates, age anomalies, parent-child conflicts, event timeline issues</p>
    </div>
  {/if}
</div>
