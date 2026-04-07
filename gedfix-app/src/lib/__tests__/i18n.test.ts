import { beforeEach, describe, expect, it, vi } from 'vitest';
import en from '$lib/i18n/en';
import de from '$lib/i18n/de';
import es from '$lib/i18n/es';
import fr from '$lib/i18n/fr';
import pt from '$lib/i18n/pt';

describe('i18n', () => {
  beforeEach(() => {
    vi.resetModules();
  });

  it('all locales have identical key sets', () => {
    const base = Object.keys(en).sort();
    for (const locale of [de, es, fr, pt]) {
      expect(Object.keys(locale).sort()).toEqual(base);
    }
  });

  it('returns translation for known keys and key fallback for unknown keys', async () => {
    vi.doMock('$lib/db', () => ({
      getSetting: vi.fn(async () => null),
      setSetting: vi.fn(async () => {}),
    }));
    const mod = await import('$lib/i18n');
    expect(mod.t('nav.overview')).toBe(en['nav.overview']);
    expect(mod.t('missing.key.example')).toBe('missing.key.example');
  });

  it('switches locale and uses selected locale values', async () => {
    vi.doMock('$lib/db', () => ({
      getSetting: vi.fn(async () => null),
      setSetting: vi.fn(async () => {}),
    }));
    const mod = await import('$lib/i18n');
    await mod.setLocale('de');
    expect(mod.getLocale()).toBe('de');
    expect(mod.t('common.save')).toBe(de['common.save']);
  });
});
