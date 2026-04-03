import { writable } from 'svelte/store';
import { getSetting, setSetting } from './db';
import en from './i18n/en';
import es from './i18n/es';
import de from './i18n/de';
import fr from './i18n/fr';
import pt from './i18n/pt';

export type Locale = 'en' | 'es' | 'de' | 'fr' | 'pt';

const translations: Record<Locale, Record<string, string>> = { en, es, de, fr, pt };

export const locale = writable<Locale>('en');

let currentLocale: Locale = 'en';
locale.subscribe((val) => {
  currentLocale = val;
});

export function t(key: string, params?: Record<string, string | number>): string {
  const template = translations[currentLocale][key] ?? translations.en[key] ?? key;
  if (!params) return template;
  return Object.entries(params).reduce(
    (out, [k, v]) => out.replaceAll(`{${k}}`, String(v)),
    template
  );
}

export async function setLocale(next: Locale): Promise<void> {
  locale.set(next);
  await setSetting('app_locale', next);
}

export function getLocale(): Locale {
  return currentLocale;
}

export async function loadLocale(): Promise<void> {
  const saved = (await getSetting('app_locale')) as Locale | null;
  if (saved && saved in translations) {
    locale.set(saved);
  }
}
