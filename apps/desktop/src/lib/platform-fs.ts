import { isTauri } from './platform'

export async function pickAndReadTextFile(
	filters?: { name: string; extensions: string[] }[]
): Promise<{ text: string; name: string } | null> {
	if (isTauri()) {
		const { open } = await import('@tauri-apps/plugin-dialog')
		const { readTextFile } = await import('@tauri-apps/plugin-fs')
		const path = await open({ multiple: false, filters })
		if (!path) return null
		const text = await readTextFile(path as string)
		return { text, name: (path as string).split('/').pop() || 'file.ged' }
	}

	// Web: use HTML file input
	return new Promise((resolve) => {
		const input = document.createElement('input')
		input.type = 'file'
		if (filters?.[0]?.extensions) {
			input.accept = filters[0].extensions.map((e) => `.${e}`).join(',')
		}
		input.onchange = async () => {
			const file = input.files?.[0]
			if (!file) {
				resolve(null)
				return
			}
			const text = await file.text()
			resolve({ text, name: file.name })
		}
		input.click()
	})
}

export async function checkFileExists(path: string): Promise<boolean> {
	if (isTauri()) {
		const { invoke } = await import('@tauri-apps/api/core')
		return invoke<boolean>('check_file_exists', { path })
	}
	return false // Web can't check local files
}

export async function readImageBase64(path: string): Promise<string> {
	if (isTauri()) {
		const { invoke } = await import('@tauri-apps/api/core')
		return invoke<string>('read_image_base64', { path })
	}
	return '' // Web would use blob URLs instead
}
