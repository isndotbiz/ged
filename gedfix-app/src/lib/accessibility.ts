export function announce(message: string, politeness: 'polite' | 'assertive' = 'polite'): void {
  if (typeof document === 'undefined') return;
  const node = document.getElementById(politeness === 'assertive' ? 'toast-announcer' : 'announcer');
  if (!node) return;
  node.textContent = '';
  // Force SR re-announce by changing text in next frame
  requestAnimationFrame(() => {
    node.textContent = message;
  });
}

function getFocusable(node: HTMLElement): HTMLElement[] {
  return Array.from(
    node.querySelectorAll<HTMLElement>(
      'a[href], button:not([disabled]), textarea:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])'
    )
  ).filter((el) => !el.hasAttribute('aria-hidden'));
}

export function focusTrap(node: HTMLElement): { destroy: () => void } {
  const focusFirst = () => {
    const list = getFocusable(node);
    if (list.length > 0) list[0].focus();
    else node.focus();
  };

  const onKeydown = (event: KeyboardEvent) => {
    if (event.key !== 'Tab') return;
    const list = getFocusable(node);
    if (list.length === 0) {
      event.preventDefault();
      return;
    }
    const first = list[0];
    const last = list[list.length - 1];
    const active = document.activeElement as HTMLElement | null;
    if (event.shiftKey) {
      if (active === first || !node.contains(active)) {
        event.preventDefault();
        last.focus();
      }
      return;
    }
    if (active === last || !node.contains(active)) {
      event.preventDefault();
      first.focus();
    }
  };

  node.addEventListener('keydown', onKeydown);
  requestAnimationFrame(focusFirst);

  return {
    destroy() {
      node.removeEventListener('keydown', onKeydown);
    },
  };
}
