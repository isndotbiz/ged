export interface Person {
	id: number
	xref: string
	givenName: string
	surname: string
	suffix: string
	sex: string
	isLiving: boolean
	birthDate: string
	birthPlace: string
	deathDate: string
	deathPlace: string
	sourceCount: number
	mediaCount: number
	personColor: string
	proofStatus: ProofStatus
	validationStatus: ValidationStatus
}

export type ValidationStatus = 'validated' | 'tree_only' | 'unvalidated'

export type ProofStatus = 'PROVEN' | 'DISPROVEN' | 'DISPUTED' | 'PROPOSED' | 'UNKNOWN'

export interface Family {
	id: number
	xref: string
	partner1Xref: string
	partner2Xref: string
	marriageDate: string
	marriagePlace: string
}

export interface GedcomEvent {
	id: number
	ownerXref: string
	ownerType: string
	eventType: string
	dateValue: string
	place: string
	description: string
}

export type SourceType =
	| 'online_tree'
	| 'vital_record'
	| 'census'
	| 'newspaper'
	| 'church_record'
	| 'military'
	| 'immigration'
	| 'other'
	| 'unknown'

export interface Source {
	id: number
	xref: string
	title: string
	author: string
	publisher: string
	sourceType: SourceType
}

export interface GedcomMedia {
	id: number
	xref: string
	ownerXref: string
	filePath: string
	format: string
	title: string
	category?: string
}

export interface ChildLink {
	familyXref: string
	childXref: string
	childOrder: number
}

export interface TreeIssue {
	id: string
	category: 'date' | 'relationship' | 'quality' | 'duplicate'
	severity: 'critical' | 'warning' | 'info'
	personXref: string
	familyXref: string
	title: string
	detail: string
	suggestion: string
	autoFixable: boolean
	fixDescription: string
}

export interface AppStats {
	personCount: number
	familyCount: number
	eventCount: number
	sourceCount: number
	mediaCount: number
	placeCount: number
	groupCount: number
	researchLogCount: number
	surnameDistribution: { surname: string; count: number }[]
}

// --- New competitive features ---

export interface Place {
	id: number
	name: string
	normalized: string
	latitude: number | null
	longitude: number | null
	eventCount: number
}

export interface PersonGroup {
	id: number
	name: string
	color: string
	description: string
	createdAt: string
}

export interface GroupMember {
	groupId: number
	personXref: string
	addedAt: string
}

export interface Association {
	id: number
	person1Xref: string
	person2Xref: string
	relationshipType: string
	description: string
	createdAt: string
}

export interface AlternateName {
	id: number
	personXref: string
	givenName: string
	surname: string
	suffix: string
	nameType: string
	source: string
}

export interface ResearchLogEntry {
	id: number
	personXref: string
	repository: string
	searchTerms: string
	recordsViewed: string
	conclusion: string
	sourceXref: string
	resultType: 'POSITIVE' | 'NEGATIVE' | 'INCONCLUSIVE'
	searchDate: string
	createdAt: string
}

export interface Citation {
	id: number
	sourceXref: string
	personXref: string
	eventId: string
	page: string
	quality: 'PRIMARY' | 'SECONDARY' | 'QUESTIONABLE' | 'UNKNOWN'
	text: string
	note: string
}

export interface Contradiction {
	id: string
	severity: 'critical' | 'warning' | 'info'
	category: string
	title: string
	detail: string
	personXrefs: string[]
	suggestion: string
}

export interface DNARelationshipPrediction {
	relationship: string
	averageCM: number
	minCM: number
	maxCM: number
	probability: string
}

export interface AIProvider {
	id: string
	displayName: string
	endpoint: string
	supportsSystemPrompt: boolean
	supportsVision: boolean
	defaultModel: string
	models: AIModel[]
	description: string
	capabilities: string[]
}

export interface AIModel {
	id: string
	displayName: string
	description: string
	contextWindow: number
}

export interface AIChatMessage {
	id: number
	provider: string
	model: string
	role: 'user' | 'assistant'
	content: string
	personXref: string
	timestamp: string
}

export interface ResearchNote {
	id: number
	ownerXref: string
	ownerType: string
	title: string
	content: string
	createdAt: string
	updatedAt: string
}

export interface ResearchTask {
	id: number
	personXref: string
	title: string
	description: string
	status: 'TODO' | 'IN_PROGRESS' | 'DONE'
	priority: 'HIGH' | 'MEDIUM' | 'LOW'
	dueDate: string
	createdAt: string
	completedAt: string
}

export interface Bookmark {
	id: number
	personXref: string
	label: string
	category?: string
	sortOrder?: number
	createdAt: string
}

export interface FaceDetectionBox {
	x: number
	y: number
	w: number
	h: number
	confidence: number
	imageWidth: number
	imageHeight: number
}

export interface GeneratedStory {
	id: number
	personXref: string
	familyXref: string
	storyType: 'single' | 'family'
	title: string
	content: string
	provider: string
	model: string
	tokensUsed: number
	costEstimate: number
	createdAt: string
}

// ===== Agentic Proposal System =====

export type ProposalStatus = 'pending' | 'approved' | 'rejected' | 'superseded'
export type RuleType = 'reject' | 'warn' | 'require_evidence'

export interface Proposal {
	id: number
	agentRunId: string
	entityType: string // 'person', 'family', 'event', 'source'
	entityId: string // xref
	fieldName: string // 'birthDate', 'surname', etc.
	oldValue: string
	newValue: string
	confidence: number // 0.0-1.0
	reasoning: string
	evidenceSource: string
	status: ProposalStatus
	createdAt: string
	resolvedAt: string
}

export interface ChangeLogEntry {
	id: number
	proposalId: number
	entityType: string
	entityId: string
	fieldName: string
	oldValue: string
	newValue: string
	actor: string // 'agent:claude', 'user', 'system'
	appliedAt: string
	undoneAt: string
}

export interface QualityRule {
	id: number
	name: string
	description: string
	ruleType: RuleType
	ruleKey: string // unique key for code-based checks
	isActive: number // 0 or 1
}

export interface AgentRun {
	id: number
	runId: string // UUID
	provider: string
	model: string
	personXref: string
	proposalCount: number
	status: string // 'running', 'completed', 'failed'
	startedAt: string
	completedAt: string
}
