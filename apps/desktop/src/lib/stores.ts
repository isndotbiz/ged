import { writable } from 'svelte/store'
import type { AppStats, TreeIssue } from './types'

export const appStats = writable<AppStats>({
	personCount: 0,
	familyCount: 0,
	eventCount: 0,
	sourceCount: 0,
	mediaCount: 0,
	placeCount: 0,
	groupCount: 0,
	researchLogCount: 0,
	surnameDistribution: [],
})

export const treeIssues = writable<TreeIssue[]>([])
export const isImporting = writable(false)
export const importProgress = writable(0)
export const importMessage = writable('')
export const isAnalyzing = writable(false)
export const selectedPersonXref = writable<string | null>(null)
