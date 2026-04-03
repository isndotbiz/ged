<script lang="ts">
  import { page } from '$app/stores';
  import { goto } from '$app/navigation';
  import { t } from '$lib/i18n';

  let {
    personCount = 0,
    pendingProposalCount = 0,
  }: { personCount?: number; pendingProposalCount?: number } = $props();

  function isActive(path: string): boolean {
    const current = $page.url.pathname;
    if (path === '/') return current === '/';
    return current.startsWith(path);
  }

  function openMore() {
    window.dispatchEvent(new CustomEvent('gedfix:open-sidebar'));
  }
</script>

<nav class="bottom-tab" aria-label={t('nav.mobileNav')}>
  <button class:active={isActive('/')} onclick={() => goto('/')}>
    <span class="icon">⌂</span>
    <span>{t('nav.home')}</span>
  </button>
  <button class:active={isActive('/people')} onclick={() => goto('/people')}>
    <span class="icon">👥</span>
    <span>{t('nav.people')}</span>
    {#if personCount > 0}
      <span class="badge">{personCount > 99 ? '99+' : personCount}</span>
    {/if}
  </button>
  <button class:active={isActive('/search')} onclick={() => goto('/search')}>
    <span class="icon">⌕</span>
    <span>{t('nav.search')}</span>
  </button>
  <button class:active={isActive('/ai-chat') || isActive('/proposals')} onclick={() => goto('/ai-chat')}>
    <span class="icon">✦</span>
    <span>{t('nav.ai')}</span>
    {#if pendingProposalCount > 0}
      <span class="badge">{pendingProposalCount > 99 ? '99+' : pendingProposalCount}</span>
    {/if}
  </button>
  <button onclick={openMore}>
    <span class="icon">☰</span>
    <span>{t('nav.more')}</span>
  </button>
</nav>

<style>
  .bottom-tab {
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    height: 56px;
    padding-bottom: env(safe-area-inset-bottom);
    display: none;
    grid-template-columns: repeat(5, minmax(0, 1fr));
    background: color-mix(in srgb, var(--paper) 92%, #000 8%);
    border-top: 1px solid var(--border-rule);
    z-index: 70;
  }

  .bottom-tab button {
    min-height: 56px;
    border: 0;
    background: transparent;
    color: var(--ink-muted);
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 2px;
    font-size: 11px;
    line-height: 1;
    position: relative;
  }

  .bottom-tab button.active {
    color: var(--accent);
  }

  .icon {
    font-size: 14px;
    line-height: 1;
  }

  .badge {
    position: absolute;
    top: 6px;
    right: calc(50% - 16px);
    min-width: 16px;
    height: 16px;
    border-radius: 999px;
    padding: 0 4px;
    background: var(--color-accent);
    color: #fff;
    font-size: 9px;
    font-weight: 700;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  @media (max-width: 768px) {
    .bottom-tab {
      display: grid;
    }
  }
</style>
