from __future__ import annotations
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable, List, Dict, Tuple, Optional
from collections import defaultdict, Counter
from rapidfuzz import fuzz

# Minimal GED line model
@dataclass
class GedLine:
    lvl: int
    tag: str
    val: str
    xref: Optional[str] = None
    raw: str = ""

@dataclass
class Person:
    xref: str
    names: List[Dict] = field(default_factory=list)  # [{'GIVN':..., 'SURN':..., 'NSFX':..., 'TYPE':...}]
    events: Dict[str, List[Dict]] = field(default_factory=lambda: defaultdict(list))  # tag -> list of {'DATE':..., 'PLAC':...}
    famc: List[str] = field(default_factory=list)
    fams: List[str] = field(default_factory=list)

@dataclass
class Family:
    xref: str
    husb: Optional[str] = None
    wife: Optional[str] = None
    chld: List[str] = field(default_factory=list)
    marr: List[Dict] = field(default_factory=list)  # {'DATE':..., 'PLAC':...}

@dataclass
class ScanIssue:
    type: str
    subject: str
    detail: str
    fix_hint: Optional[str] = None
    rule_id: Optional[str] = None

# --- Parse a simple GEDCOM (line-oriented, tolerant) ---
def _parse_lines(p: Path) -> List[GedLine]:
    out: List[GedLine] = []
    with p.open("r", encoding="utf-8-sig", errors="replace") as f:
        for raw in f:
            s = raw.rstrip("\n")
            if not s.strip(): 
                continue
            parts = s.split(" ", 2)
            if len(parts) == 1:
                continue
            # Clean any remaining BOM characters to be safe
            if parts[0].startswith('\ufeff'):
                parts[0] = parts[0].replace('\ufeff', '')
            lvl = int(parts[0])
            rest = parts[1:]
            xref=None; tag=""; val=""
            if rest and rest[0].startswith("@") and rest[0].endswith("@") and len(rest)>=2:
                xref = rest[0]
                tag  = rest[1]
                val  = rest[2] if len(rest)==3 else ""
            else:
                tag = rest[0]
                val = rest[1] if len(rest)==2 else ""
            out.append(GedLine(lvl= lvl, tag=tag, val=val, xref=xref, raw=s))
    return out

def _group_records(lines: List[GedLine]):
    recs: List[List[GedLine]] = []
    cur: List[GedLine] = []
    for gl in lines:
        if gl.lvl == 0:
            if cur:
                recs.append(cur)
            cur = [gl]
        else:
            cur.append(gl)
    if cur:
        recs.append(cur)
    return recs

# Extract key structures we need for checks (people, families)
def build_index(p: Path) -> Tuple[Dict[str, Person], Dict[str, Family]]:
    lines = _parse_lines(p)
    recs = _group_records(lines)
    people: Dict[str, Person] = {}
    fams: Dict[str, Family] = {}
    for rec in recs:
        head = rec[0]
        if head.tag == "INDI" and head.xref:
            pr = Person(xref=head.xref)
            # NAME / sub tags (GIVN, SURN, NSFX, TYPE)
            i = 1
            while i < len(rec):
                gl = rec[i]
                if gl.tag == "NAME":
                    name_line = {"RAW": gl.val, "GIVN":"", "SURN":"", "NSFX":"", "TYPE":""}
                    # Split GIVN/SURN from "First Last" + slashes if present
                    raw = gl.val
                    if "/" in raw:
                        pre, _, post = raw.partition("/")
                        name_line["GIVN"] = " ".join(pre.strip().split())
                        name_line["SURN"] = " ".join(post.strip().split())
                    # read subtags
                    j = i+1
                    while j < len(rec) and rec[j].lvl > gl.lvl:
                        if rec[j].tag in ("GIVN","SURN","NSFX","TYPE"):
                            name_line[rec[j].tag] = rec[j].val.strip()
                        j += 1
                    pr.names.append(name_line)
                    i = j
                    continue
                # events (BIRT, DEAT, MARR under INDI, etc.)
                if gl.tag in ("BIRT","DEAT","CHR","BAPM"):
                    ev = {"DATE":"", "PLAC":""}
                    j = i+1
                    while j < len(rec) and rec[j].lvl > gl.lvl:
                        if rec[j].tag in ("DATE","PLAC"):
                            # DO NOT change DATE semantics here
                            if rec[j].tag == "DATE":
                                ev["DATE"] = rec[j].val
                            if rec[j].tag == "PLAC":
                                ev["PLAC"] = rec[j].val
                        j += 1
                    pr.events[gl.tag].append(ev)
                    i = j
                    continue
                if gl.tag == "FAMC":
                    pr.famc.append(gl.val.strip())
                if gl.tag == "FAMS":
                    pr.fams.append(gl.val.strip())
                i += 1
            people[head.xref] = pr
        elif head.tag == "FAM" and head.xref:
            fr = Family(xref=head.xref)
            i = 1
            while i < len(rec):
                gl = rec[i]
                if gl.tag == "HUSB": fr.husb = gl.val.strip()
                elif gl.tag == "WIFE": fr.wife = gl.val.strip()
                elif gl.tag == "CHIL": fr.chld.append(gl.val.strip())
                elif gl.tag == "MARR":
                    ev = {"DATE":"", "PLAC":""}
                    j = i+1
                    while j < len(rec) and rec[j].lvl > gl.lvl:
                        if rec[j].tag in ("DATE","PLAC"):
                            if rec[j].tag == "DATE":
                                ev["DATE"] = rec[j].val
                            if rec[j].tag == "PLAC":
                                ev["PLAC"] = rec[j].val
                        j += 1
                    fr.marr.append(ev)
                    i = j
                    continue
                i += 1
            fams[head.xref] = fr
    return people, fams

# --- Checks (report-only unless explicitly approved) ---

def check_multiple_marriages_same_couple(fams: Dict[str, Family]) -> List[ScanIssue]:
    out=[]
    for fam in fams.values():
        if fam.marr and len(fam.marr) > 1:
            subj = f"{fam.husb or '?'} + {fam.wife or '?'}"
            out.append(ScanIssue(
                type="Multiple marriages of same couple",
                subject=subj,
                detail=f"Has {len(fam.marr)} marriage facts",
                fix_hint="Consider removing redundant MARR facts.",
                rule_id="multi_marr_same_couple"
            ))
    return out

def check_multiple_birth_facts(people: Dict[str, Person]) -> List[ScanIssue]:
    out=[]
    for p in people.values():
        births = p.events.get("BIRT", [])
        if len(births) > 1:
            out.append(ScanIssue(
                type="Multiple birth facts of same person",
                subject=p.xref,
                detail=f"Has {len(births)} birth facts",
                fix_hint="Remove redundant BIRT facts.",
                rule_id="multi_birth"
            ))
    return out

_SUFFIX_SET = {"JR","SR","II","III","IV","V","VI","VII","VIII","IX","X","ESQ"}
def check_suffix_in_first_or_last(people: Dict[str, Person]) -> List[ScanIssue]:
    out=[]
    for p in people.values():
        for nm in p.names:
            giv = nm.get("GIVN","").strip()
            sur = nm.get("SURN","").strip()
            # endswith suffix token?
            for token in [t for t in (giv.split()+sur.split()) if t]:
                up = token.strip(",. ").upper()
                if up in _SUFFIX_SET and nm.get("NSFX","")=="":
                    # heuristics for where it was found:
                    loc = "first name" if up in (t.upper() for t in giv.split()) else "last name"
                    out.append(ScanIssue(
                        type=f"Suffix in {loc}",
                        subject=p.xref,
                        detail=f"Suffix '{token}' should move to NSFX",
                        fix_hint="Move suffix into separate NSFX field",
                        rule_id="suffix_move"
                    ))
                    break
    return out

def check_maiden_as_married(people: Dict[str, Person]) -> List[ScanIssue]:
    # If woman's maiden == husband's surname, flag "possibly married name used as maiden"
    out=[]
    # Build map of spouses
    spouses = defaultdict(set)  # person -> set of spouse surnames
    for p in people.values():
        for fam in p.fams:
            spouses[p.xref].add(fam)  # temporary, will resolve later
    # We only have fam IDs here; for robust logic we need fam index too; caller supplies additional map.
    # We'll compute in the wrapper where we have both people and fams.
    return out

def check_disconnected(people: Dict[str, Person]) -> List[ScanIssue]:
    out=[]
    for p in people.values():
        if not p.famc and not p.fams:
            out.append(ScanIssue(
                type="Disconnected from tree",
                subject=p.xref,
                detail="No relatives linked",
                fix_hint="Connect to a parent, partner, or child",
                rule_id="disconnected"
            ))
    return out

def check_siblings_same_first(people: Dict[str, Person], fams: Dict[str, Family]) -> List[ScanIssue]:
    out=[]
    for fam in fams.values():
        if len(fam.chld) < 2: 
            continue
        firsts = defaultdict(list)
        for ch in fam.chld:
            if ch in people and people[ch].names:
                giv = people[ch].names[0].get("GIVN","").split()
                if giv:
                    firsts[giv[0].upper()].append(ch)
        for k, ids in firsts.items():
            if len(ids) > 1:
                out.append(ScanIssue(
                    type="Siblings with same first name",
                    subject=fam.xref,
                    detail=f"Children {', '.join(ids)} share first name '{k.title()}'",
                    fix_hint="Verify if duplicate person or intentional reuse.",
                    rule_id="siblings_same_first"
                ))
    return out

def check_inconsistent_last_name_spelling(people: Dict[str, Person], min_occ_more_common=2, sim_thresh=90) -> List[ScanIssue]:
    out=[]
    counter = Counter(nm.get("SURN","").strip() for p in people.values() for nm in p.names if nm.get("SURN"))
    surnames = [s for s in counter if s]
    for s in surnames:
        # find a "more common" close spelling
        best = None
        for t in surnames:
            if t == s: 
                continue
            score = fuzz.ratio(s, t)
            if score >= sim_thresh and counter[t] >= min_occ_more_common and counter[t] > counter[s]:
                if not best or counter[t] > counter[best]:
                    best = t
        if best:
            out.append(ScanIssue(
                type="Inconsistent last name spelling",
                subject=s,
                detail=f"'{s}' appears {counter[s]}×; similar '{best}' appears {counter[best]}×",
                fix_hint=f"Consider changing '{s}' to '{best}'",
                rule_id="surname_inconsistent"
            ))
    return out

def check_inconsistent_place_spelling(people: Dict[str, Person], sim_thresh=90) -> List[ScanIssue]:
    out=[]
    # collect places across BIRT only (conservative)
    places = Counter()
    for p in people.values():
        for ev in p.events.get("BIRT", []):
            if ev.get("PLAC"):
                places[ev["PLAC"].strip()] += 1
    plist = [k for k in places if k]
    for a in plist:
        best=None
        for b in plist:
            if a==b: continue
            score = fuzz.ratio(a, b)
            if score >= sim_thresh and places[b] > places[a]:
                if not best or places[b] > places[best]:
                    best=b
        if best:
            out.append(ScanIssue(
                type="Inconsistent place name spelling",
                subject=a,
                detail=f"'{a}' appears {places[a]}×; similar '{best}' appears {places[best]}×",
                fix_hint=f"Consider changing to '{best}'",
                rule_id="place_inconsistent"
            ))
    return out

def run_checks(inp: Path, level: str="standard") -> List[ScanIssue]:
    people, fams = build_index(inp)

    issues: List[ScanIssue] = []
    issues += check_multiple_marriages_same_couple(fams)
    issues += check_multiple_birth_facts(people)
    issues += check_suffix_in_first_or_last(people)
    issues += check_disconnected(people)
    issues += check_siblings_same_first(people, fams)
    # Level-sensitive checks
    if level in ("aggressive","ultra"):
        issues += check_inconsistent_last_name_spelling(people, sim_thresh=92 if level=="aggressive" else 88)
        issues += check_inconsistent_place_spelling(people, sim_thresh=92 if level=="aggressive" else 88)

    # TODO: maiden-as-married check using spouse surname (requires fam linkage)
    # Safer to leave as future enhancement.
    return issues

def summarize_markdown(issues: List[ScanIssue]) -> str:
    # Aggregate into MyHeritage-style counts
    groups: Dict[str, List[ScanIssue]] = defaultdict(list)
    for i in issues:
        groups[i.type].append(i)
    total = sum(len(v) for v in groups.values())
    lines = []
    lines.append(f"{total} consistency issues found")
    for g, arr in sorted(groups.items(), key=lambda kv: -len(kv[1])):
        lines.append(f"\n{g}({len(arr)})")
        # show first example
        ex = arr[0]
        lines.append(f"{ex.subject} — {ex.detail}")
        if ex.fix_hint:
            lines.append(f"Tip: {ex.fix_hint}")
        if len(arr) > 1:
            lines.append(f"View {len(arr)-1} more issues of this type")
    return "\n".join(lines)
