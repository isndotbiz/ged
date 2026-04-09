export type ShortcutHandler = () => void

export type ShortcutDefinition = {
	combo: string
	description: string
	category: 'Global' | 'Navigation' | 'Person Detail'
	action: ShortcutHandler
}

const registry = new Map<string, ShortcutDefinition>()

export function registerShortcut(
	combo: string,
	action: ShortcutHandler,
	description: string,
	category: ShortcutDefinition['category'] = 'Global'
): void {
	registry.set(combo.toLowerCase(), { combo, action, description, category })
}

export function clearShortcuts(): void {
	registry.clear()
}

export function getShortcuts(): ShortcutDefinition[] {
	return Array.from(registry.values())
}

export function shouldIgnoreShortcutTarget(target: EventTarget | null): boolean {
	const el = target as HTMLElement | null
	if (!el) return false
	const tag = el.tagName
	return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || el.isContentEditable
}

export function normalizeKeyCombo(e: KeyboardEvent): string {
	const bits: string[] = []
	if (e.metaKey || e.ctrlKey) bits.push('mod')
	if (e.shiftKey) bits.push('shift')
	if (e.altKey) bits.push('alt')
	bits.push(e.key.toLowerCase())
	return bits.join('+')
}

export function triggerShortcut(combo: string): boolean {
	const def = registry.get(combo.toLowerCase())
	if (!def) return false
	def.action()
	return true
}
