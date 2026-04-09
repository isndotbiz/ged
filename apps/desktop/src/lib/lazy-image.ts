import { isTauri } from './platform'

/**
 * Svelte action for lazy-loading images via IntersectionObserver.
 * Usage: <img use:lazyImage={filePath} alt="..." />
 */
export function lazyImage(node: HTMLImageElement, filePath: string) {
	if (!filePath) return

	let _src = filePath

	async function resolveSrc(path: string): Promise<string> {
		if (isTauri()) {
			const { convertFileSrc } = await import('@tauri-apps/api/core')
			return convertFileSrc(path)
		}
		return path // On web, filePath would be a blob URL or data URL
	}

	const observer = new IntersectionObserver(
		([entry]) => {
			if (entry.isIntersecting) {
				resolveSrc(filePath).then((resolved) => {
					node.src = resolved
				})
				node.onload = () => {
					node.style.opacity = '1'
				}
				node.onerror = () => {
					node.style.display = 'none'
				}
				observer.disconnect()
			}
		},
		{ rootMargin: '300px 0px', threshold: 0.01 }
	)

	// Start transparent, fade in on load
	node.style.opacity = '0'
	node.style.transition = 'opacity 200ms ease'

	observer.observe(node)

	return {
		update(newPath: string) {
			if (newPath && newPath !== filePath) {
				filePath = newPath
				resolveSrc(newPath).then((resolved) => {
					node.src = resolved
				})
			}
		},
		destroy() {
			observer.disconnect()
		},
	}
}
