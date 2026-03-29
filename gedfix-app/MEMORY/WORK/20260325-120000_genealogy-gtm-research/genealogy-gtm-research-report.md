# GedFix Go-To-Market Strategy Research Report

**Date:** March 25, 2026
**Researcher:** Ava Chen, Investigative Analyst
**Scope:** Comprehensive GTM intelligence for commercial genealogy software launch

---

## 1. Market Size & Opportunity

### 1.1 Global Genealogy Market Size (2024-2030)

The global genealogy products and services market shows strong, consistent growth across multiple analyst projections:

| Source | 2024 Value | 2030/2032 Projection | CAGR |
|--------|-----------|---------------------|------|
| Kings Research | $6.60B | $16.60B (2032) | 12.06% |
| Polaris Market Research | $4.57B | N/A (2034 horizon) | 11.5% |
| Verified Market Research | $4.66B | $10.10B (2031) | 11.19% |
| The Business Research Company | N/A | $8.41B (2030) | 10.1% |
| Cognitive Market Research | N/A | $5.09B (2028) | 7.97% |

**Consensus estimate:** The market was approximately $4.5-6.6B in 2024, growing at 10-12% CAGR, reaching $8-10B by 2030.

Sources:
- [Kings Research - Genealogy Products and Services Market](https://www.kingsresearch.com/genealogy-products-and-services-market-29)
- [Polaris Market Research - Genealogy Products & Services](https://www.polarismarketresearch.com/industry-analysis/genealogy-products-and-services-market)
- [Verified Market Research - Genealogy Products Services](https://www.verifiedmarketresearch.com/product/genealogy-products-services-market/)
- [The Business Research Company - Genealogy Market Report 2026](https://www.thebusinessresearchcompany.com/report/genealogy-products-and-services-global-market-report)

### 1.2 Consumer vs Professional Segments

**Consumer (Household) segment:** 59.85% market share in 2024, driven by personal ancestry and genetic heritage interest.

**Family Records & Family Tree segment:** Earned $1.60B in 2024 specifically, reflecting consumer demand for lineage tracing and ancestral history preservation.

**Professional segment:** Professional genealogists, genetic counselors, forensic genealogists, and institutional researchers represent the remaining ~40%. This segment commands higher per-user revenue but lower volume.

**Market concentration:** The top four players (Ancestry, MyHeritage, Family Tree DNA, Myriad Genetics) collectively hold ~50% market share.

### 1.3 Growth Drivers

**DNA Testing:** The direct-to-consumer genetic testing market is projected to grow from $2.45B (2024) to $21.85B (2034) at a 24.44% CAGR. This is the single largest growth engine, as each DNA test sale creates a downstream genealogy software user.

**AI Integration:** Companies are leveraging AI and machine learning to cross-reference data from various sources, improve search accuracy, and provide personalized recommendations. MyHeritage has deployed AI for photo enhancement (colorization, Deep Nostalgia animation), AI Time Machine, and resolution enhancement.

**Digitized Records:** In May 2025 alone, FamilySearch added over 118 million new historical records from 37 countries. FamilySearch received 298 million visits in 2025 and added 2.2 billion new searchable names/images in historical records during the year. This ongoing digitization dramatically expands the addressable research market.

### 1.4 Underserved Niches

**Jewish Genealogy:**
- JewishGen is the primary hub, with millions of records worldwide
- Ancestry partnered with JewishGen, AJJDC, and AJHS to create 20+ million Jewish historical records
- ~80 Jewish genealogical societies worldwide
- Key resources: Arolsen Archives (17.5 million people documented), Yizkor books (556,000+ pages digitized)
- **Gap:** No dedicated desktop software integrates JewishGen databases, Yizkor book search, and Holocaust survivor databases natively

**Eastern European Records:**
- Routes to Roots Foundation maps Jewish vital records across Eastern European state archives
- Historical directories covering Central/Eastern Europe (91,000+ pages of yizkor books)
- Many records in Cyrillic, Hebrew, and Polish requiring specialized character handling
- **Gap:** Most genealogy software handles Western European naming conventions poorly for Slavic, Yiddish, and Hebrew names

**Holocaust Research:**
- US Holocaust Memorial Museum maintains Holocaust Survivors and Victims Database
- Arolsen Archives (International Center on Nazi Persecution) is the world's most comprehensive archive
- Zekelman Holocaust Center provides tracing services
- **Gap:** No software automates cross-referencing across these disparate databases

**Other Ethnic Communities:**
- African American genealogy faces unique challenges (slave records, name changes)
- Latino/Hispanic genealogy (Catholic church records, immigration records)
- Indigenous genealogy (tribal records, oral history integration)

Sources:
- [JewishGen - The Global Home for Jewish Genealogy](https://www.jewishgen.org/)
- [FamilySearch - Jewish Genealogy Research](https://www.familysearch.org/en/wiki/Jewish_Genealogy_Research)
- [Ancestry - Jewish Genealogy](https://www.ancestry.com/c/jewish-genealogy)
- [Global Jewish Genealogy Society](https://www.globaljgs.org/jewish-genealogy/)

---

## 2. Pricing Models That Work

### 2.1 Competitor Pricing Matrix

| Product | Model | Price | Notes |
|---------|-------|-------|-------|
| **RootsMagic 11** | Perpetual license | $39.95 (new), $29.95 (upgrade) | Free "Essentials" tier available; native Mac support added |
| **MacFamilyTree 11** | Perpetual license | $69.99 (new), $49.99 (upgrade) | Mac App Store only; includes CloudTreeWeb |
| **Family Tree Maker** | Perpetual license | ~$79.99 | Exclusive Ancestry sync (FamilySync) |
| **Gramps** | Open source (GPL) | Free | Python-based; Gramps Web for collaboration |
| **Ancestry** | Subscription | $19.99-$44.99/mo (monthly), $99-$199/6mo | Three tiers: US Discovery, World Explorer, All Access |
| **MyHeritage** | Subscription | Starting $129/year ($10-30/mo) | Free tier (250 members), Premium, PremiumPlus, Data, Complete |
| **Legacy Family Tree** | Perpetual + free tier | $34.95 | Standard edition free; Deluxe paid |

Sources:
- [RootsMagic - Buy](https://www.rootsmagic.com/rootsmagic/buy)
- [MacFamilyTree - How to Buy](https://www.syniumsoftware.com/macfamilytree/howtobuy)
- [Ancestry - Subscribe](https://www.ancestry.com/offers/subscribe)
- [MyHeritage - Pricing](https://www.myheritage.com/pricing)

### 2.2 AI Feature Pricing Strategy Recommendation

**Analysis of market dynamics:**
- Desktop genealogy users expect perpetual licenses ($30-70 range)
- AI features have ongoing marginal costs (API tokens, compute)
- MyHeritage bundles AI photo features into subscriptions
- The Bessemer Venture Partners AI Pricing Playbook recommends hybrid models for AI-enhanced software

**Recommended model: Hybrid (Perpetual Base + AI Credit Subscription)**

```
GedFix Core           $49.95  (perpetual license)
  - GEDCOM processing, cleaning, validation
  - Contradiction detection (rule-based)
  - Basic reporting and family tree visualization
  - All non-AI features

GedFix AI Pack        $7.99/month or $59.99/year
  - AI story generation (unlimited)
  - AI-powered record suggestions
  - Smart merge recommendations
  - AI photo enhancement integration
  - Priority support

GedFix Complete       $99.95  (perpetual + 1 year AI Pack)
  - Best value bundle for new users
  - Renews AI at $59.99/year after first year
```

**Rationale:**
1. $49.95 perpetual is competitive with RootsMagic ($39.95) while signaling premium quality
2. AI subscription creates recurring revenue to fund ongoing AI costs
3. $7.99/mo is psychologically below the $9.99 threshold
4. The Complete bundle at $99.95 drives initial adoption with perceived savings
5. Free trial (14 days all features) removes friction
6. Consider a "GedFix Essentials" free tier for GEDCOM viewing only, to build funnel

### 2.3 Price Sensitivity Considerations

- Genealogy hobbyists skew older (55+), many on fixed incomes - price sensitivity is moderate
- Professional genealogists will pay premium for tools that save research time
- The Jewish/Eastern European niche will pay more for specialized features unavailable elsewhere
- Competing with free (Gramps) requires clear value differentiation, not price matching

Sources:
- [BVP AI Pricing Playbook](https://www.bvp.com/atlas/the-ai-pricing-and-monetization-playbook)
- [Martin Roe - Best Genealogy Software 2025 Comparison](https://martinroe.com/blog/best-genealogy-software-in-2025-a-practical-comparison/)

---

## 3. Distribution & Marketing Channels

### 3.1 Conferences

**RootsTech 2026**
- **Dates:** March 5-7, 2026
- **Location:** Salt Palace Convention Center, Salt Lake City, Utah
- **Attendance:** Millions of participants from 200+ countries (including virtual)
- **In-person tickets:** $69/day or $99/3-day (early bird)
- **Online:** Free
- **Expo Hall:** 65 exhibiting companies in 2026
- **Exhibit booth cost:** Not publicly listed; contact RootsTech directly via rootstech.org. Industry estimates for similar convention center expos: $2,000-$8,000 for a standard 10x10 booth, plus sponsorship tiers
- **Attendee profile:** Mix of beginners and experts, heavy FamilySearch/LDS community representation, strong international virtual audience
- **Note:** This is THE must-attend event. FamilySearch hosts it and actively promotes partner software

Sources:
- [RootsTech 2026 - Church Newsroom](https://newsroom.churchofjesuschrist.org/event/rootstech-2026)
- [FamilySearch - Plan Your RootsTech 2026 Schedule](https://www.familysearch.org/en/blog/rootstech-2026-schedule)
- [Visit Salt Lake - RootsTech 2026](https://www.visitsaltlake.com/event/2026-rootstech-conference/conventions_33592/)

**NGS 2026 Family History Conference**
- **Dates:** May 27-30, 2026
- **Location:** Fort Wayne, Indiana (Allen County Public Library - renowned Genealogy Center)
- **Theme:** "America at 250" (Semiquincentennial)
- **Attendee profile:** More serious/professional genealogists than RootsTech; BCG-certified genealogists, librarians, archivists
- **NGS merged with FGS** (Federation of Genealogical Societies) in 2020, consolidating the two largest genealogy organizations
- **Partnership info:** [NGS Partnerships Page](https://www.ngsgenealogy.org/partnerships/)

Sources:
- [NGS Family History Conference](https://conference.ngsgenealogy.org/)
- [NGS Partnerships](https://www.ngsgenealogy.org/partnerships/)

### 3.2 Genealogy Societies

- **Estimated count:** Hundreds of national, state, regional, and ethnic genealogical societies in the US alone
- FamilySearch maintains a directory: [United States Societies](https://www.familysearch.org/en/wiki/United_States_Societies)
- Rootsweb maintains a comprehensive list: [List of Genealogical Societies](https://wiki.rootsweb.com/wiki/index.php/List_of_Genealogical_Societies)
- **Jewish genealogical societies:** ~80 worldwide
- **Partnership approach:** Offer society members a discount code (15-20% off), provide free presentation/webinar to society meetings, sponsor society newsletters
- NGS Society and Organization membership allows institutional partnerships: [NGS Society Members](https://www.ngsgenealogy.org/society-and-organization-members/)

### 3.3 DNA Testing Company Partnerships

| Company | Partnership Potential | Notes |
|---------|---------------------|-------|
| **AncestryDNA** | Low (walled garden) | Only FTM and RootsMagic have sync access; API is restricted |
| **MyHeritage DNA** | Medium | FamilyGraph API is public, free, no NDA required |
| **FamilyTreeDNA** | Medium | More open ecosystem; used by genetic genealogists |
| **23andMe** | Low | Filed bankruptcy in 2024; future uncertain |
| **LivingDNA** | Medium | Smaller player, more open to partnerships |

### 3.4 Social Media Channels

**Top Genealogy YouTube Channels (by subscribers):**

| Channel | Subscribers | Focus |
|---------|------------|-------|
| Ancestry | 582K | Platform tutorials, stories |
| FamilySearch | 115K | Free records, research techniques |
| Genealogy TV (Connie Knox) | ~100K | How-to, beginner-friendly |
| Family History Fanatics | 94.9K | DNA tests, conferences, beginners |
| MyHeritage | 74K | Platform features, DNA |
| Lisa Louise Cooke's Genealogy Gems | 33.9K | Research techniques, tech |
| BYU Family History Library | 21.3K | Academic, LDS resources |
| Amy Johnson Crow | ~20K+ | 52 Ancestors challenge, research tips |
| Genealogy TV | ~100K | Connie Knox - tutorials |
| The Barefoot Genealogist | ~15K+ | FamilySearch tips |

Source: [Feedspot - 25 Genealogy YouTubers 2026](https://videos.feedspot.com/genealogy_youtube_channels/)

**Top Genealogy Facebook Groups:**

| Group | Members (approx) | Focus |
|-------|------------------|-------|
| Genetic Genealogy Tips & Techniques | 54K | DNA analysis |
| DNA Detectives | 100K+ | Adoptee/unknown parentage |
| Genealogy Squad | Large | General genealogy |
| U.S. South Genealogy Research | 8.3K | Regional US |
| U.S. Midwest Genealogy Research | 6.3K | Regional US |
| U.S. Northeast Genealogy Research | 5.7K | Regional US |
| Vintage African American Photographs | 7.2K | African American heritage |
| Genealogy Master List of FB Groups | Meta-list | Directory of 16,700+ groups |

Note: Comprehensive list of 16,700+ genealogy Facebook groups maintained by Cyndi Ingle at [Cyndi's List](https://cyndislist.com/social-networking/facebook/)

**Reddit:**
- r/Genealogy: 185K members, high activity, engaged community
- r/AncestryDNA: Active DNA discussion
- r/23andme: DNA results sharing

**TikTok:**
- #genealogy and #familyhistory are growing hashtags
- Notable creators: @GenealogyExplorer, @ManicPixieMom (tombstone cleaning + stories)
- TikTok genealogy content tends toward emotional discovery stories and DNA reveals - good for brand awareness

**Top Genealogy Podcasts:**

| Podcast | Host | Focus |
|---------|------|-------|
| Genealogy Gems | Lisa Louise Cooke | Research techniques (4.5/5 Apple, 362 reviews) |
| Extreme Genes | Scott Fisher | Weekly news, expert interviews |
| The Genealogy Guys | George Morgan & Drew Smith | News, book reviews, since 2005 |
| Branching Out | Elizabeth O'Neal & Tami Mize | News, tips, tech |
| Family Tree Magazine Podcast | Various | Magazine companion |

Source: [Feedspot - 40 Best Genealogy Podcasts 2026](https://podcast.feedspot.com/genealogy_podcasts/)

### 3.5 SEO Keywords

**High-volume genealogy keywords (from SimilarWeb/Ancestry traffic analysis):**

| Keyword | Estimated Monthly Volume |
|---------|------------------------|
| ancestry | 378,500 |
| ancestry.com | 97,800 |
| ancestry login | 66,500 |
| ancestry dna | 20,900 |
| family tree | High (generic) |
| genealogy | High (generic) |
| free family tree | High (generic) |
| family history | Medium-high |
| genealogy software | Medium |
| GEDCOM | Low-medium (technical) |
| family tree maker | Medium |
| genealogy records | Medium |

**Long-tail opportunities for GedFix:**
- "jewish genealogy software"
- "eastern european genealogy tools"
- "GEDCOM cleaner"
- "GEDCOM validation tool"
- "fix GEDCOM errors"
- "genealogy data cleaning"
- "AI genealogy story generator"
- "holocaust genealogy research tools"
- "gedcom duplicate finder"

Source: [SimilarWeb - ancestry.com Traffic Analytics](https://www.similarweb.com/website/ancestry.com/)

---

## 4. Business Formation Requirements

### 4.1 LLC Formation

**Recommended:** Delaware or Wyoming LLC for software companies

**Formation costs (2026):**
- State filing fee: $90-$300 depending on state
- Registered agent: $39-$125/year
- Services like Northwest Registered Agent: $39 + state fees (includes 1 year RA)
- LegalZoom: $0-$349 + state fees
- Bizee: Free + state fees only

**Key 2026 requirement:** FinCEN Beneficial Ownership Information (BOI) reporting is now mandatory for most LLCs, with $500/day penalties for non-compliance.

**Recommended formation services:**
- Northwest Registered Agent ($39 + state, privacy-focused)
- Bizee (free + state fees)
- Stripe Atlas ($500, includes Delaware C-Corp/LLC + bank account + Stripe integration)

Sources:
- [Venture Smarter - Best LLC Services 2026](https://venturesmarter.com/best-llc-services/)
- [Business Rocket - LLC Formation Services 2026](https://www.businessrocket.com/business-corner/start/llc/formation-services/)

### 4.2 Terms of Service / Privacy Policy

**Requirements for genealogy software:**
- Terms of Service covering: license grant, acceptable use, data ownership (users own their genealogy data), limitation of liability, dispute resolution
- Privacy Policy covering: what data is collected, how it's stored, third-party sharing, data retention, deletion rights
- GDPR-compliant privacy policy if serving EU users (see 4.3)
- CCPA compliance if serving California users
- Cookie consent if web-based components

**Template sources:**
- Termly.io (free generator)
- Iubenda (GDPR-focused, from $29/year)
- Attorney review recommended before launch ($500-2,000)

### 4.3 GDPR Compliance for Genealogy Data

**Key GDPR principles for genealogy software:**

1. **Deceased persons are NOT covered by GDPR** - if processing only concerns dead people, GDPR does not apply
2. **BUT:** Data about deceased persons can reveal information about living relatives, and those living relatives have GDPR rights regarding such data
3. **Legal bases:** Consent and legitimate interest (Article 6) are the applicable bases for genealogical data processing
4. **Special categories:** Names, dates/places of birth, dates of marriage, death dates/places, and titles/professions are considered necessary data for genealogical research
5. **Data subject rights:** Right to portability (structured electronic format), right to rectification, right to erasure (limited for historical/scientific research), right to restriction
6. **Data portability:** Must provide data in a structured, commonly used standard electronic format (GEDCOM qualifies)
7. **Living person flag:** Software should support marking individuals as "living" and automatically restricting their data in exports/shares

**Practical implementation for GedFix:**
- Add "living person" detection and privacy controls
- Implement data export in GEDCOM format (satisfies portability)
- Add consent mechanism for living persons' data
- Include data deletion capability
- Store minimal data (don't collect what you don't need)
- Encrypt data at rest and in transit

Sources:
- [Finland Data Protection Ombudsman - Genealogy FAQ](https://tietosuoja.fi/en/faq-genealogy)
- [Geneanet - GDPR Effects for Genealogists](https://en.geneanet.org/genealogyblog/post/2018/07/what-are-the-gdpr-effects-for-genealogists)
- [McDonald Hopkins - Data Privacy 2025-2026](https://www.mcdonaldhopkins.com/insights/news/u-s-and-international-data-privacy-developments-in-2025-and-compliance-considerations-for-2026)

### 4.4 Payment Processing

| Platform | Fee | Tax Handling | Best For |
|----------|-----|-------------|----------|
| **Paddle** | 5% + $0.50 | Full MoR (handles all global tax) | Indie developers, < $50K MRR |
| **Stripe** | 2.9% + $0.30 | You handle tax (or add Stripe Tax) | Scale, > $50K MRR |
| **Lemon Squeezy** | 5% + $0.50 | Full MoR, excellent DX | Best starting point for 2026 |
| **Gumroad** | 10% | MoR | Simplest, but expensive |
| **FastSpring** | Variable | Full MoR | Desktop software specialist |

**Recommendation for GedFix:** Start with **Paddle** or **Lemon Squeezy**. Both act as Merchant of Record, handling global sales tax/VAT collection and remittance. This eliminates the need to register for tax in every jurisdiction. The 5% fee premium over Stripe is worth it until you cross ~$50K/month.

**Why not Stripe initially:** As the merchant, you're responsible for tax registration, filings, compliance, and chargeback handling in every market you sell to. This is a significant operational burden for an indie developer.

Sources:
- [Whop - Paddle vs Stripe 2026](https://whop.com/blog/paddle-vs-stripe/)
- [UniBee - Paddle vs Stripe 2026](https://unibee.dev/blog/paddle-vs-stripe-the-ultimate-comparison/)
- [The Software Scout - Best Payment Platforms 2026](https://thesoftwarescout.com/best-payment-platforms-for-saas-developers-2026-stripe-lemon-squeezy-paddle-more/)

### 4.5 Customer Support Tools

**Recommended for indie/early-stage:**

| Tool | Starting Price | Best For |
|------|---------------|----------|
| **Plain** | Free tier available | Technical B2B products (used by Vercel, Cursor) |
| **Freshdesk** | Free tier (10 agents) | Small/mid teams, help desk |
| **Crisp** | Free tier | Chat + knowledge base combo |
| **Help Scout** | $25/user/mo | Email-based support, knowledge base |

**Also needed:**
- Knowledge base / documentation site (GitBook free tier, or Docusaurus)
- Community forum (GitHub Discussions, or Discourse)
- Bug tracking (Linear or GitHub Issues)

### 4.6 Beta Program Structure

**Recommended beta timeline for GedFix:**

```
Phase 1: Private Alpha (4-8 weeks)
  - 10-20 hand-picked testers (mix of power users, professional genealogists)
  - Focus: core GEDCOM processing, stability, data integrity
  - Channel: Direct email, private Discord/Slack

Phase 2: Closed Beta (6-12 weeks)
  - 100-500 testers via waitlist
  - Focus: UI/UX feedback, feature completeness, AI features
  - Channel: Dedicated beta community (Discord), in-app feedback widget
  - Offer: Free perpetual license for active beta testers

Phase 3: Open Beta (4-8 weeks)
  - Public download, no waitlist
  - Focus: scale testing, edge cases, pricing validation
  - Channel: Website download, ProductHunt launch consideration

Phase 4: General Availability
  - Full pricing active
  - Beta testers get grandfathered pricing or free upgrade
```

**Beta recruitment channels:**
- r/Genealogy (185K members, engaged)
- Genealogy Facebook groups
- RootsTech / NGS conferences
- Jewish genealogical societies (for niche testing)
- YouTube genealogy creators (send free copies for review)

### 4.7 Open Source vs Proprietary

| Factor | Open Source | Proprietary | Hybrid (Open Core) |
|--------|-----------|-------------|-------------------|
| **Revenue** | Donations/services only | License fees | Core free, premium paid |
| **Competition** | Gramps exists (free) | RootsMagic, FTM dominate | Best of both worlds |
| **Community** | Builds trust, contributors | Faster development | Selective contributions |
| **AI costs** | Hard to fund | License revenue funds AI | AI is the premium layer |
| **IP protection** | None | Full | Partial |

**Recommendation: Open Core (Proprietary with Open Components)**
- Open source the GEDCOM parsing/validation library (builds credibility, gets community contributions)
- Keep AI features, UI, and integrations proprietary
- This positions GedFix as both a community contributor AND a commercial product
- Precedent: many successful companies use this model (GitLab, Elastic, etc.)

---

## 5. Partnership Opportunities

### 5.1 FamilySearch Partner Program

**Status:** Actively seeking partners. The most accessible and valuable partnership in genealogy.

**Key details:**
- **URL:** [FamilySearch Innovate / Partner Developer](https://www.familysearch.org/innovate/)
- **API cost:** FREE (FamilySearch is a nonprofit)
- **Developer Portal:** [developers.familysearch.org](https://developers.familysearch.org/)
- **Support email:** devsupport@familysearch.org
- **Benefits:** Access to billions of historical records, family tree data, hints
- **Solution Provider program:** Featured in FamilySearch Solutions Gallery
- **2025 updates:** User-Owned Tree (CETs) early adopter program available; redesigned Developer Dashboard coming
- **Integration benefits:** [API Compatibility Benefits](https://www.familysearch.org/innovate/api-compatibility-benefits)

**Action:** Apply immediately. This is free, well-documented, and gives GedFix access to the world's largest free genealogy dataset.

### 5.2 Ancestry API (FamilySync)

**Status:** Highly restricted. Invite-only.

**Key details:**
- Ancestry retired the old TreeSync API and replaced it with FamilySync
- Currently, only Family Tree Maker and RootsMagic have access
- No public developer program or API documentation
- Getting access requires a direct business relationship with Ancestry

**Action:** This is a long-term goal. Build traction first, then approach Ancestry BD team. Having a FamilySearch integration and significant user base makes you a more attractive partner.

Sources:
- [Tamura Jones - Genealogy APIs](https://www.tamurajones.net/GenealogyAPIs.xhtml)
- [Behold Blog - Sync Possibilities](https://www.beholdgenealogy.com/blog/?p=3034)

### 5.3 MyHeritage API

**Status:** Publicly available. No NDA required.

**Key details:**
- **API:** FamilyGraph API (RESTful, JSON)
- **Cost:** Free (no usage fees)
- **Access:** Request an application key via form
- **GitHub:** [MyHeritage-External](https://github.com/myheritage) (51 repositories)
- **SDK:** PHP SDK available
- **Usage:** Millions of API calls daily by dozens of partners

**Action:** Apply for API key immediately. The FamilyGraph API can power family tree sync, record hints, and smart matching features.

Sources:
- [MyHeritage API - APITracker](https://apitracker.io/a/myheritage)
- [MyHeritage GitHub](https://github.com/myheritage)

### 5.4 FindMyPast

**Status:** API available for third-party integration.

**Key details:**
- Findmypast developed a Hints API for third-party use
- APIs use HTTP POST requests, return JSON
- Family Historian 6.2 was first third-party integration
- RootsMagic uses FindMyPast APIs for WebHints
- Partnerships with: Federation of Family History Societies, Society of Genealogists, FamilySearch, British Library, National Archives (UK), National Archives of Ireland
- **Partner page:** [findmypast.co.uk/partners](https://www.findmypast.co.uk/partners)

**Action:** Contact FindMyPast partnership team. Strong UK/Irish records make this valuable for users with British Isles ancestry.

Source: [Tamura Jones - FindMyPast API](https://www.tamurajones.net/FindmypastAPI.xhtml)

### 5.5 Archive Partnerships

**National Archives (NARA):**
- **Catalog API:** Publicly available at [archives.gov/research/catalog/help/api](https://www.archives.gov/research/catalog/help/api)
- Provides access to archival descriptions, authority records, digital object metadata, OCR text
- Free to use for application developers
- **Digitization partnerships page:** [archives.gov/digitization/partnerships](https://www.archives.gov/digitization/partnerships)
- Current digitization partners: Ancestry.com, FamilySearch, Fold3
- NARA plans to enter new public-private digitization partnerships with new types of partners
- **Budget concern:** Proposed FY2026 budget includes 10% overall cut and 33% cut to electronic records programs

**State Archives:**
- Contact individual state archives for partnership opportunities
- Many state archives have digitization backlogs and welcome technology partners
- Key states for Jewish/Eastern European genealogy: New York, Pennsylvania, Illinois, Massachusetts

**International Archives for Jewish/Eastern European Research:**
- Polish State Archives (Archiwum Glowne Akt Dawnych)
- Ukrainian State Archives
- Arolsen Archives (International Center on Nazi Persecution)
- Routes to Roots Foundation (maps available records)

Sources:
- [NARA Catalog API](https://www.archives.gov/research/catalog/help/api)
- [NARA Digitization Partnerships](https://www.archives.gov/digitization/partnerships)
- [NARA Strategic Plan 2022-2026](https://www.archives.gov/about/plans-reports/strategic-plan/strategic-plan-2022-2026)

---

## 6. Recommended Go-To-Market Timeline

```
Q2 2026: Foundation
  [x] Form LLC (Delaware or Wyoming)
  [ ] Apply for FamilySearch Partner API
  [ ] Apply for MyHeritage FamilyGraph API key
  [ ] Set up Paddle/Lemon Squeezy for payments
  [ ] Draft ToS, Privacy Policy (attorney review)
  [ ] Set up support infrastructure (Plain/Freshdesk + knowledge base)

Q3 2026: Beta Launch
  [ ] Private alpha with 20 hand-picked testers
  [ ] Implement FamilySearch API integration
  [ ] Post in r/Genealogy, Jewish genealogy groups
  [ ] Contact 5 genealogy YouTubers for early access

Q4 2026: Public Beta
  [ ] Open beta launch
  [ ] ProductHunt launch
  [ ] Submit to genealogy software review sites
  [ ] Begin content marketing (blog + SEO for long-tail keywords)
  [ ] Contact genealogy podcasts for interviews

Q1 2027: General Availability
  [ ] Full pricing active
  [ ] Exhibit at RootsTech 2027 (apply early, booth sells out)
  [ ] Present at NGS 2027 conference
  [ ] Partner with 10+ genealogy societies for discount codes
  [ ] Launch affiliate program for genealogy bloggers

Q2 2027: Growth
  [ ] Approach Ancestry BD team for FamilySync access
  [ ] Implement FindMyPast Hints API
  [ ] Launch AI feature marketing campaign
  [ ] Expand to international markets (GDPR-ready)
```

---

## 7. Competitive Positioning Summary

**GedFix positioning:** "The AI-powered genealogy toolkit that goes where others don't"

**Differentiation from competitors:**
- vs RootsMagic: AI-native features, Jewish/Eastern European specialization, modern UI
- vs MacFamilyTree: Cross-platform (KMP), AI features, GEDCOM validation depth
- vs Ancestry/MyHeritage: Desktop-first (own your data), no subscription required for core features, privacy-first
- vs Gramps: Professional UI, AI features, commercial support, integrated API partnerships

**Unique value propositions:**
1. AI story generation from genealogical data
2. Automated contradiction detection across sources
3. Deep GEDCOM validation and cleaning (existing strength)
4. Jewish/Eastern European genealogy specialization
5. Privacy-first desktop app (you own your data, no cloud lock-in)
