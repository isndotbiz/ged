# GedFix KMP (Archived)

Kotlin Multiplatform + Compose Multiplatform experiment. Archived April 2026.

**Why archived:** The Tauri app (`apps/desktop/`) covers the same platforms. KMP source dirs for Android/iOS exist but were never wired into the build file — only desktop (JVM) compiled.

**Reference value:** `composeApp/src/commonMain/kotlin/com/gedfix/` has Kotlin implementations of most domain models (GedcomModels, GedcomParser, DNATools, ContradictionDetector, RelationshipCalculator, AIService, etc.) — useful reference if native Android is ever needed. SQLDelight schema is also complete.

**Do not delete** — significant business logic in commonMain, including models not yet in the TypeScript layer.
