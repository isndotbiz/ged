# Genealogy UX Research: Visualization, Interaction, and Interface Innovation

*Multi-perspective analysis across 12 dimensions. Research date: 2026-03-19.*

---

## Table of Contents

1. [Family Tree Visualization Techniques](#1-family-tree-visualization-techniques)
2. [Geographic Visualizations](#2-geographic-visualizations)
3. [Timeline Visualizations](#3-timeline-visualizations)
4. [Conflict Resolution UX](#4-conflict-resolution-ux)
5. [Research Log UX](#5-research-log-ux)
6. [Source Citation UX](#6-source-citation-ux)
7. [AI Assistant UX](#7-ai-assistant-ux-in-genealogy)
8. [Touch/Tablet UX](#8-touchtablet-genealogy-ux)
9. [D3.js/SVG Open Source Examples](#9-d3jssvg-family-tree-examples)
10. [Photo Timeline UX](#10-photo-timeline-ux)
11. [Accessibility](#11-accessibility-in-genealogy-apps)
12. [Keyboard-Driven UX](#12-keyboard-driven-ux)
13. [Synthesis: What Would Make a New App Feel Better](#13-synthesis)

---

## 1. Family Tree Visualization Techniques

### Beyond the Basic Pedigree Chart

The standard left-to-right or top-to-bottom pedigree chart is the baseline. Here is what exists beyond it, and what remains underexplored.

**Fan Chart (Radial Pedigree)**
- Individual at center, generations radiating outward in concentric rings
- FamilySearch supports up to 7 generations in fan view
- Strength: compact display of many ancestors; visually striking
- Weakness: hard to add detail per person; becomes unreadable beyond 7-8 generations
- Opportunity: color-code by geography, ethnicity, DNA contribution, or data completeness

**Descendant Chart**
- One ancestor at top, descendants flowing downward across generations
- Useful for tracking a surname line or mapping all living descendants
- Opportunity: combine with a "coverage heatmap" showing which branches have been researched vs. unexplored

**Hourglass Chart**
- Selected person in the middle; ancestors above, descendants below
- Best for showing one person's full context in both directions
- Underexplored: interactive hourglass where you can re-center on any person with a click

**Dandelion Chart**
- Offered by Charting Companion. Combines ancestor and descendant data radially
- Uses algorithms to prevent node overlap, unlike naive radial layouts
- Novel approach worth studying for dense trees

**DNA-Aware Trees**
- X-chromosome inheritance visualization (Charting Companion supports this)
- Color nodes by shared DNA segments, match likelihood, or haplogroup
- Opportunity: overlay DNA match confidence on a standard tree. Show "confirmed by DNA" vs. "paper trail only" vs. "unverified" as visual states

**Cluster Views / Graph Layouts**
- Gramps proposed new visualization techniques including graph-based layouts
- Neo4j graph database approaches model family relationships as nodes/edges natively
- Findmypast built "GRAPHical Family Trees" using graph technology
- Opportunity: force-directed graph layout for exploring interconnected families, especially when dealing with intermarriage, adoptions, or complex blended families

**What is Missing from Current Tools**
- No major tool offers a "data completeness" overlay (which ancestors have sources? which are bare stubs?)
- No tool shows "research frontier" visualization (where does your knowledge end?)
- No tool offers side-by-side comparison of the same tree from two GEDCOM files

### Key Sources
- [Family Tree Chart Types - Family Tree Magazine](https://familytreemagazine.com/resources/family-tree-chart-types/)
- [6 Unique Chart Types - Family History Fanatics](https://www.familyhistoryfanatics.com/unique-family-tree-charts)
- [Gramps GEPS 030: New Visualization Techniques](https://www.gramps-project.org/wiki/index.php?title=GEPS_030:_New_Visualization_Techniques)
- [Neo4j Genealogy Visualization - NODES2022](https://neo4j.com/videos/094-genealogy-with-different-graph-technologies-for-data-collection-and-visualization-nodes2022/)
- [GRAPHical Family Trees - Findmypast](https://tech.findmypast.com/graphical-family-tree/)

---

## 2. Geographic Visualizations

### Migration Maps and Place-Based Family Views

**Existing Tools**

| Tool | Features |
|------|----------|
| RootsMapper | Plots ancestors on interactive map from FamilySearch data; animated migration paths |
| MyHeritage Pedigree Maps | Plots life events (birth, marriage, death) on interactive maps automatically |
| YourRoots Map | GEDCOM import to interactive global map; shows birth locations, migration patterns |
| Family Atlas (RootsMagic) | Desktop mapping with place markers and migration routes |
| Flourish 3D Maps | Animated colored pulses showing births on maternal/paternal lines |

**Animated Migration Visualization (Yannick Brouwer Project)**
- Open source Processing sketch on GitHub: [ancestors-migration-visualization](https://github.com/yannickbrouwer/ancestors-migration-visualization)
- Uses CSV export from MyHeritage + Unfolding Maps library
- Animates ancestor movements across a map over time
- Demonstrates the power of seeing family movement as a temporal animation

**What Would Make a New App Better**
- Historical map overlays: show the actual borders/names of countries/regions as they existed when your ancestor lived there. No major genealogy tool does this well.
- Heatmap of ancestral density by region across generations
- "Family diaspora" view: where did the descendants of one ancestor end up?
- Animated timeline slider: scrub through years and see your family's geographic position change
- Cluster analysis: automatically identify migration waves in your family data
- Integration with place standardization (already built in your gedfix project)

### Key Sources
- [RootsMapper - FamilySearch Blog](https://www.familysearch.org/en/blog/visualize-your-family-heritage-using-rootsmapper)
- [YourRoots Map Launch](https://yourroots.com/blog/yourroots-map-launches-a-new-way-to-explore-your-family-tree-on-a-global-scale)
- [Yannick Brouwer Migration Visualization](https://github.com/yannickbrouwer/ancestors-migration-visualization)
- [Flourish Video Mapping](https://parchmentrustler.com/family-history/mapping-with-flourish/)

---

## 3. Timeline Visualizations

### Placing Family Events in Historical Context

**Current State**
- MyHeritage Family Tree Timeline: visual representation of individual + direct ancestors with lifespans on same axis
- FamilySearch: shows up to 8 historical events on a person's timeline
- Genelines (Progeny Software): dedicated timeline charting from GEDCOM files; color-coded family branches
- Pratt Institute student project: explored novel timeline representations for family trees

**Effective Patterns**
- Color-coded branches by family line
- Overlapping lifespans visible at a glance (who was alive at the same time?)
- Age-at-event annotations (how old was the mother when the child was born?)
- Concurrent world events alongside family events

**What Would Make a New App Better**
- **Dual-axis timeline**: family events on top, world/local history events on bottom. Correlate the two.
- **Generation bands**: shade alternating generations so you can visually scan "what happened in generation 3?"
- **Event clustering**: automatically group related events (all births in a family within a 5-year window)
- **Research gap visualization**: show where on the timeline you have no data. "There's a 20-year gap for this ancestor between 1850 and 1870."
- **"Witness overlay"**: for any event, show who else in the family was alive and where they were
- **Zoomable timeline**: decade view, year view, month view for densely documented families

### Key Sources
- [MyHeritage Family Tree Timeline](https://blog.myheritage.com/2022/03/introducing-the-family-tree-timeline/)
- [Genelines Sample Charts](https://progenygenealogy.com/products/timeline-charts/genelines-sample-charts/)
- [Timeline and Map in FamilySearch](https://www.familysearch.org/en/help/helpcenter/article/what-is-the-timeline-or-map-in-family-tree)
- [Family Tree Representations - Pratt Institute](https://studentwork.prattsi.org/infovis/labs/timelines/representations-of-the-family-tree/)

---

## 4. Conflict Resolution UX

### Merge, Deduplicate, and Resolve Conflicting Data

**Current Genealogy Approach (FamilySearch)**
- Side-by-side comparison of two potential duplicate records
- User selects which value to keep for each conflicting field
- Non-conflicting relationships are automatically preserved
- Merge includes a "reason" field for why the user merged
- Undo merge capability exists
- Best practice: rely on evidence quality, not just absence of conflict

**Lessons from Software Merge UX (VS Code, DiffMerge)**
- Three-way merge pattern: source A, source B, and result
- Color-coded highlighting of differences
- Point-and-click conflict resolution (select left or right)
- Mixed layout: conflicting sources on top, editable result below
- Visual indicators for resolved vs. unresolved conflicts

**What Would Make a New App Better**
- **Confidence scoring**: show why the system thinks two records might be duplicates (name similarity: 94%, same birth year, same location)
- **Evidence-based merge**: display the source documents inline during merge, not just the extracted data
- **Three-panel merge**: Record A | Result (editable) | Record B, with drag-and-drop from either side
- **Batch review queue**: show all potential duplicates ranked by confidence, with quick-action buttons (merge, skip, mark as different)
- **Merge preview**: show what the merged record will look like before committing
- **Audit trail**: every merge is logged with timestamp, user, reason, and before/after snapshots
- **Undo with context**: not just "undo merge" but "here's what was merged and here's what will be restored"
- **Relationship conflict visualization**: when merging, show family tree context -- "merging these two people would make person X have two fathers"

**Anti-patterns to Avoid**
- Auto-merging without user confirmation
- Losing alternate data (always preserve both values as "alternate" with source citations)
- Rushing complex merges; the UI should encourage careful review
- No distinction between "confirmed different people" and "not yet reviewed"

### Key Sources
- [FamilySearch Merge Duplicates Best Practices](https://www.familysearch.org/en/blog/familysearch-merge-duplicates)
- [VS Code Three-Way Merge Discussion](https://github.com/microsoft/vscode/issues/146091)
- [Salesforce Deduplication Best Practices](https://appexchange.salesforce.com/partners/servlet/servlet.FileDownload?file=00P4V000011djAeUAI)

---

## 5. Research Log UX

### Tracking What You Searched, Found, and Still Need to Do

**Current State**
- Most genealogists use spreadsheets (Excel, Google Sheets, Airtable)
- FamilySearch wiki documents research log methodology
- Goldie May (browser extension) auto-logs every genealogy page visited
- American Ancestors provides downloadable research log templates
- Legacy Family Tree and RootsMagic include built-in tracking features

**What a Research Log Should Capture**
- Source searched (repository, website, collection)
- Search parameters used (names, dates, locations queried)
- What was found (positive results with citations)
- What was NOT found (negative evidence -- critically important)
- Research questions driving the search
- Strategy notes (why this source? what theory is being tested?)
- Next steps / follow-up items
- Date of search and time spent

**What Would Make a New App Better**
- **Integrated research log**: not a separate tool, but woven into the tree. When viewing a person, see their research history.
- **Auto-logging**: like Goldie May, but built into the app. Every search, every record viewed, automatically logged.
- **Research plan templates**: for common genealogy research patterns (find birth record, confirm parentage, extend a line)
- **Negative evidence tracking**: first-class support for "I searched X and found nothing" as a finding
- **Research status per person**: traffic light system (red = no research, yellow = partial, green = well-sourced)
- **Kanban-style research board**: To Research | In Progress | Found | Dead End
- **Collaborative research log**: multiple researchers can see what each other has done
- **Smart suggestions**: "You searched for John Smith in Census 1850 but not 1860. Want to add that to your plan?"

### Key Sources
- [FamilySearch Research Logs Wiki](https://www.familysearch.org/en/wiki/Research_Logs)
- [Goldie May Research Log Tool](https://www.familyhistoryfanatics.com/goldie-may-research-log)
- [Airtable Research Logs - Family Locket](https://familylocket.com/airtable-research-logs/)
- [American Ancestors Research Templates](https://www.americanancestors.org/tools/research-templates)

---

## 6. Source Citation UX

### Making Citation Easy Instead of Painful

**The Problem**
- Genealogy source citation follows Evidence Explained (Elizabeth Shown Mills) or Chicago Manual of Style
- Both require 5 elements: who, what, when, where-in, where-is
- Current tools make this tedious: too many fields, unclear which are required, no guidance
- Users skip citations because the UX is punishing

**Current Solutions**

| Approach | Tool | How It Works |
|----------|------|-------------|
| Fully automated | Record Seek | Browser extension; one click generates citation from any genealogy website |
| Semi-automated | Family Tree Maker, Legacy, RootsMagic | Fill out a form based on Evidence Explained templates |
| Manual | Most tools | Free-text citation fields |

**What Would Make a New App Better**
- **One-click citation from URLs**: paste a URL, app extracts citation metadata automatically (like Zotero for genealogy)
- **Citation templates by source type**: select "US Census" and get a pre-filled form with the right fields
- **Smart defaults**: if you're citing a census record, auto-fill the repository (National Archives), the collection name, etc.
- **Citation quality indicator**: show whether a citation is complete, partial, or minimal
- **Bulk citation**: when attaching a source to multiple people (e.g., a family in a census), create the citation once and link it to all
- **Citation preview**: show the formatted citation in real-time as the user fills in fields
- **Image-to-citation**: photograph a source document, OCR extracts metadata, pre-fills citation
- **Source repository**: maintain a master list of sources; attach specific pages/entries to individuals
- **Drag-and-drop from record to person**: drag a search result onto a person to auto-create citation and attach

**Design Principle**: The citation UX should feel as easy as attaching a photo, not like filling out a tax form.

### Key Sources
- [Automated Source Citation Builders - Family Locket](https://familylocket.com/automated-source-citation-builders/)
- [Source Citations Simplified - Family Locket](https://familylocket.com/genealogy-source-citations-simplified/)
- [Easier Source Citation - Family Tree Magazine](https://familytreemagazine.com/research/genealogy-software-source-citations/)

---

## 7. AI Assistant UX in Genealogy

### How Should an AI Research Assistant Be Presented?

**Current Implementations (2025-2026)**

| Platform | AI Feature | UX Pattern |
|----------|-----------|------------|
| FamilySearch | AI Research Assistant | Banner on person's profile; opens to split-screen with suggestions on left, search on right |
| Ancestry | AI Assistant (beta) | Conversational chat for help and research questions |
| Ancestry | "Ideas" | Proactive suggestions appearing in the tree |
| MyHeritage | AI Biographer | Generates narrative text from genealogy data |
| Storied | AI-powered | Storytelling assistance integrated into the editing experience |

**FamilySearch's Approach (Most Developed)**
- When enabled in Labs, hints appear on the home page
- On a person's profile, a banner allows opening the assistant
- Screen splits: suggested records/people on the left, search fields on the right
- Assistant analyzes FamilySearch records, attached sources, and full-text search
- Can suggest "tree-extending hints" (new ancestors to add)
- Can answer questions about a specific ancestor

**The 2026 Shift: From Chat to Autonomous Agents**
- The field is moving from manual chat to "agentic" systems
- Autonomous agents can navigate family tree lines to find research gaps
- Agents can search library catalogs and compile results automatically
- Tools like Claude in Chrome, Perplexity Comet, and ChatGPT Atlas enable this
- This represents a fundamental UX shift from "ask a question" to "agent does research for you"

**What Would Make a New App Better**
- **Inline suggestions, not a separate chat panel**: AI observations appear as annotations directly on the tree ("This birth date conflicts with the census record")
- **Research assistant sidebar**: persistent sidebar that shows AI observations about the currently selected person
- **Proactive anomaly detection**: "I notice this person apparently had a child at age 12. Worth reviewing?"
- **Source suggestion engine**: "Based on this person's time and place, these record collections are likely to contain information"
- **Narrative generation**: one-click biography generation from the facts in the tree
- **Three modes**:
  - Passive: highlights and annotations only
  - Interactive: sidebar chat for asking questions
  - Autonomous: agent mode that researches and reports back
- **Transparency**: always show the AI's reasoning and sources, never just a conclusion
- **Confidence indicators**: "I'm 85% confident this census record matches your ancestor because..."

### Key Sources
- [FamilySearch AI Research Assistant](https://lisalouisecooke.com/2025/08/28/familysearch-ai-research-assistant/)
- [AI Research Assistant Tree-Extending Hints](https://www.familysearch.org/en/blog/ai-research-assistant-home-page-hints)
- [Agentic Browsers - Research Like a Pro with AI 2026](https://familylocket.com/agentic-browsers-and-native-integrations-inside-the-new-edition-of-research-like-a-pro-with-ai/)
- [AI Tool Comparison for Genealogy 2025](https://denyseallen.substack.com/p/ai-tool-comparison-genealogy-2025)

---

## 8. Touch/Tablet Genealogy UX

### iPad and Mobile Innovations

**Leading Apps**

| App | Platform | Key Innovation |
|-----|----------|---------------|
| MobileFamilyTree (Synium) | iOS | Desktop-grade on mobile; custom-tailored navigation for all screen sizes |
| MacFamilyTree | macOS/iOS | CloudTree sync across devices; drag-and-drop chart editing |
| ReunionTouch | iPad | Seamless Mac file integration; slick tree navigation |
| Heredis | iOS/Android | Pinch-to-zoom on tree; scroll to expand boxes |
| RootsMagic Mobile | iOS/Android | Companion app for desktop data |

**Touch-Specific Patterns**
- Pinch-to-zoom on tree views
- Tap-to-select a person, tap again for detail
- Swipe between family members (left/right for siblings, up for parents, down for children)
- Drag-and-drop for editing relationships
- Long-press for contextual actions
- Two-finger pan for navigating large trees

**What Would Make a New App Better**
- **Apple Pencil integration**: annotate documents, draw relationship lines, handwrite notes on research
- **Split-screen research**: tree on one side, record image on the other (iPad multitasking)
- **Haptic feedback**: subtle vibration when connecting two people or confirming a merge
- **Camera integration**: photograph headstones, documents, or family photos directly into a person's record
- **Offline-first architecture**: research at the cemetery or archive without connectivity
- **Voice input**: dictate notes and research findings while hands are occupied (e.g., photographing records)
- **Gesture-based navigation**: swipe up for parents, down for children, sideways for siblings -- make the tree feel physical

### Key Sources
- [MobileFamilyTree](https://www.syniumsoftware.com/mobilefamilytree)
- [MacFamilyTree](https://www.syniumsoftware.com/macfamilytree)
- [Heredis Mobile App](https://home.heredis.com/en/family-tree-app-for-phone-and-tablet/)

---

## 9. D3.js/SVG Family Tree Examples

### Open Source Interactive Visualizations

**Production-Ready Libraries**

| Project | Stars | Tech | Key Feature |
|---------|-------|------|-------------|
| [family-chart](https://github.com/donatso/family-chart) | 688 | TypeScript/D3 | Framework-agnostic (React, Vue, Angular, Svelte, vanilla); visual builder tool; WikiData integration |
| [topola-viewer](https://github.com/PeWu/topola-viewer) | 305 | TypeScript | Hourglass + all-relatives charts; GEDCOM import; PDF/PNG/SVG export; integrates with Gramps, Webtrees, WikiTree |
| [d3-pedigree-examples](https://github.com/justincy/d3-pedigree-examples) | -- | D3 v3 | 6 progressive examples from basic to bidirectional OOP trees with zoom/pan |
| [js_family_tree](https://github.com/BenPortner/js_family_tree) | 80 | TypeScript/d3-dag | Bipartite graph for complex structures; handles multiple marriages natively |
| [dTree](https://github.com/ErikGartner/dTree) | -- | D3 | NPM package (d3-dtree); multi-parent trees |
| [pedigreejs](https://ccge-boadicea.github.io/pedigreejs/) | -- | D3 | Standard pedigree nomenclature; undo/redo; save/load |
| [genealogic-d3](https://github.com/Lucas-C/genealogic-d3) | -- | D3 | Minimalist; d3.js only dependency |
| [d3-pedigree-tree](https://github.com/solgenomics/d3-pedigree-tree) | -- | D3 | DAG layout for multi-parental trees with grouped siblings |

**Modern React/TypeScript Projects**

| Project | Tech | Key Feature |
|---------|------|-------------|
| [family-tree (punit-gajjar)](https://github.com/punit-gajjar/family-tree) | React/Prisma | Dark mode, glassmorphism design, full-stack |
| [family-tree (ooanishoo)](https://github.com/ooanishoo/family-tree) | Next.js 13/TypeScript/Tailwind | BFS traversal for relationship finding |
| [react-family-tree](https://github.com/SanichKotikov/react-family-tree) | React | Reusable component; [live demo](https://sanichkotikov.github.io/react-family-tree-example/) |

**Live Demos Worth Studying**
- family-chart visual builder: https://donatso.github.io/family-chart-doc/create-tree/
- family-chart examples: https://donatso.github.io/family-chart-doc/examples/
- d3-pedigree basic: http://justincy.github.io/d3-pedigree-examples/basic.html
- d3-pedigree expandable: http://justincy.github.io/d3-pedigree-examples/expandable.html
- d3-pedigree bidirectional: http://justincy.github.io/d3-pedigree-examples/descendants.html
- Topola with JFK family: linked from topola-viewer README
- react-family-tree demo: https://sanichkotikov.github.io/react-family-tree-example/

**Recommendation for a New App**
- **family-chart** (donatso) is the strongest starting point: TypeScript, framework-agnostic, active development, visual builder, and WikiData integration
- **topola-viewer** is excellent for GEDCOM import/export and multi-format rendering
- **d3-pedigree-tree** solves the hard problem of multi-parental DAG layouts

---

## 10. Photo Timeline UX

### Displaying Family Photos Chronologically with Face Recognition

**Current Capabilities**
- Google Photos: automatic face grouping, chronological sorting, auto-generated videos by face
- FamilySearch Compare-a-Face: compares faces and gives resemblance percentages between relatives
- Related Faces (online service): AI detects and isolates faces in group photos; creates profiles for identified individuals

**Effective Patterns**
- Chronological photo strip per person: watch someone age through their photos
- Face grouping across collections: "show me all photos of Grandma"
- Cross-referencing faces with family tree data
- Resemblance matching: identify unknown photos by comparing facial features to known relatives

**What Would Make a New App Better**
- **Photo timeline per person**: horizontal scrollable strip showing all photos of one person in chronological order
- **Family photo wall**: all photos for a family group, organized by date, with faces labeled
- **Mystery photo solver**: upload an unidentified photo, AI suggests which family member it might be based on facial similarity and date range
- **Photo-to-tree linking**: tag a face in a photo and it automatically links to a person in the tree
- **Group photo annotation**: identify each person in a group photo; link to tree; record the event
- **Photo comparison view**: place two photos side-by-side to compare faces across generations
- **Temporal clustering**: automatically group photos by life stage (childhood, young adult, elderly)
- **Photo provenance tracking**: who had this photo? Where was the original? Is there a higher-resolution scan?

### Key Sources
- [Google Photos for Genealogy](https://lisalouisecooke.com/2020/09/04/google-photos-beginners/)
- [FamilySearch Compare-a-Face](https://familytreemagazine.com/websites/familysearch/familysearch-compare-a-face/)
- [Related Faces AI Service](https://familylocket.com/how-to-match-individuals-in-old-photos-using-related-faces/)

---

## 11. Accessibility in Genealogy Apps

### Current State: Mostly Absent

**The Only Standout: My Family Tree (Chronoplex Software)**
- Full support for: high DPI displays, zoom, contrast themes, color filters, screen readers, keyboard navigation, dyslexic-friendly fonts
- This is the only genealogy app that explicitly advertises comprehensive accessibility

**The Problem**
- Most genealogy apps are built around visual tree diagrams, which are inherently inaccessible to screen readers
- Interactive SVG/canvas-based trees typically have zero ARIA labels
- Color-coded family lines are meaningless to color-blind users
- Touch-based tree navigation excludes motor-impaired users

**What Would Make a New App Better**
- **ARIA labels on all tree nodes**: screen readers should be able to navigate the tree ("John Smith, born 1842, married to Mary Jones, 3 children")
- **Keyboard tree navigation**: arrow keys to move between family members (up=parent, down=child, left/right=siblings)
- **Text-based tree alternative**: a structured text view of the tree for screen reader users (nested list format)
- **High contrast mode**: not just a theme, but ensuring all information is conveyed without relying on color alone
- **Scalable UI**: support for system-level font size preferences and zoom
- **Reduced motion option**: disable animations for vestibular disorder users
- **Alt text for all photos**: auto-generated descriptions of historical photos
- **Focus indicators**: visible, clear focus rings on all interactive elements
- **Skip navigation**: jump to main content, search, or selected person
- **Voice control**: navigate the tree and enter data by voice

**Design Principle**: A family tree is data, not just a picture. The data should be accessible even when the picture is not.

### Key Sources
- [My Family Tree Accessibility Features](https://chronoplexsoftware.com/myfamilytree/)
- [Making Family History Accessible - Genealogy Gems](https://lisalouisecooke.com/2018/06/20/family-history-accessible/)
- [WebAIM Keyboard Accessibility](https://webaim.org/techniques/keyboard/)

---

## 12. Keyboard-Driven UX

### Power User Navigation for Genealogy

**Current State**
- Family Tree Maker: function keys for navigation (F3-F8 for spouse, parents, children, siblings)
- Ancestry: Genealogy Assistant browser extension adds single-key shortcuts
- My Family Tree: full keyboard navigation as part of accessibility
- Most web-based genealogy tools: minimal or no keyboard support

**What Would Make a New App Better**
- **Vim-like navigation**: h/j/k/l for tree traversal (left=spouse, up=parent, down=child, right=sibling)
- **Command palette** (Cmd+K): search for any person, any action, any view -- like VS Code or Raycast
- **Quick entry mode**: rapid keyboard data entry without touching the mouse
- **Keyboard shortcuts overlay**: press ? to see all available shortcuts (like GitHub)
- **Focus mode**: press F to expand the current person's details full-screen
- **Quick-jump**: type a number to jump N generations back
- **Search-as-you-type**: start typing a name from anywhere to find and navigate to a person
- **Tab-through fields**: when editing, Tab moves logically through fields (name -> birth date -> birth place -> death date -> death place)
- **Batch operations**: select multiple people with keyboard, apply actions to all
- **Customizable keybindings**: let power users remap shortcuts to their preference

**Design Principle**: Every action achievable by mouse should be achievable by keyboard. The tree is a data structure; navigating it by keyboard should feel as natural as navigating a file system.

### Key Sources
- [Genealogy Assistant Keyboard Shortcuts](https://www.genea.ca/add-keyboard-shortcuts-to-family-tree/)
- [My Family Tree Keyboard Navigation](https://chronoplexsoftware.com/myfamilytree/)
- [Ancestry Hot Keys](https://www.geneamusings.com/2009/04/using-ancestrycoms-hot-keys.html)

---

## 13. Synthesis: What Would Make a New App Feel Significantly Better

### The Gap Analysis

Having examined all 12 dimensions, clear patterns emerge about what existing tools do poorly and where a new app can differentiate.

### Tier 1: Highest Impact Differentiators

**1. Data Completeness Visualization**
No existing tool shows you "where don't you know things." A tree that visually communicates research coverage -- fully sourced, partially researched, or completely bare -- would be transformative. Every node in the tree should radiate its evidence quality at a glance.

**2. Integrated Research Workflow**
Current tools separate the tree (data) from the research process (log, plan, sources). A new app should unify these: when you look at a person, you see their data AND their research status, open questions, search history, and next steps. The Kanban-style research board per person or per research question is unexplored territory.

**3. AI as Inline Annotation, Not Chat**
The strongest AI UX is not a chatbot sidebar. It is inline observations: annotations that appear on the tree itself, anomaly highlights, source suggestions that appear when you hover over a gap. Chat is the fallback for complex questions, not the primary interface.

**4. Citation as Easy as Photo Attach**
One-click citation from URLs, drag-and-drop from search results to people, and smart templates by source type. The current citation UX in every genealogy app is a known pain point. Solving this alone would win users.

### Tier 2: Strong Differentiators

**5. Keyboard-First, Mouse-Optional**
A command palette, vim-like tree navigation, and rapid keyboard data entry would make power users dramatically faster. No genealogy tool does this today.

**6. Geographic + Temporal Dual View**
Map and timeline as first-class views, not afterthoughts. Historical map overlays showing borders as they were. Animated migration paths. Timeline with world events correlated to family events.

**7. Conflict Resolution Done Right**
Three-panel merge with confidence scoring, evidence display, relationship context, and full audit trail. Batch review queue ranked by likelihood. This directly applies to the existing gedfix deduplication work.

**8. Accessibility as Foundation**
Build accessibility in from the start: ARIA labels, keyboard navigation, screen reader support, text alternatives for visual trees. Being the first truly accessible genealogy app is a market differentiator.

### Tier 3: Valuable Enhancements

**9. Photo Timeline with Face Recognition**
Chronological photo strips per person, mystery photo matching, and group photo annotation.

**10. Touch-Native Gestures**
Swipe navigation through the tree, Apple Pencil annotation, camera-to-record workflows.

**11. Open Source Foundation**
Build on family-chart (donatso) for tree visualization, topola-viewer for GEDCOM handling, and d3-pedigree-tree for complex multi-parental layouts. These are proven, maintained, TypeScript-based libraries.

### The Overarching Theme

The biggest opportunity is this: **existing genealogy tools are databases that happen to have a tree view. A transformative genealogy app would be a research tool that happens to store data.** The shift is from data-entry-with-visualization to research-workflow-with-integrated-data.

Every design decision should ask: "Does this help the user figure out what they don't know yet?" That is the core job of a genealogy researcher, and no current tool serves it well.

---

## Appendix: UX Case Studies Worth Studying

- **Archoral** ([tentackles.com case study](https://tentackles.com/projects/archoral)): Genealogy web app that balanced feature density with simplicity; privacy-first; interactive onboarding
- **Storied** ([storied.com](https://storied.com/)): Storytelling-first approach; users compare it to scrolling Instagram; captures audio memories
- **webtrees** ([webtrees.net](https://webtrees.net/)): Open source, self-hosted, 12+ chart types, 60+ languages, privacy controls -- the most feature-complete open source option
- **Gramps** ([gramps-project.org](https://www.gramps-project.org/)): Open source desktop app with graph database integration possibilities via Neo4j
