package com.gedfix.models

/**
 * Citation and citation template models for source documentation.
 * Based on Evidence Explained standards for genealogical research.
 */

data class Citation(
    val id: String,
    val sourceXref: String,       // link to source record
    val personXref: String,       // who this citation is for
    val eventId: String,          // which event it cites
    val page: String,             // page/volume/item number
    val quality: CitationQuality, // primary/secondary/questionable
    val text: String,             // actual text from the source
    val note: String              // researcher's note
)

enum class CitationQuality(val display: String) {
    PRIMARY("Primary (original record, eyewitness)"),
    SECONDARY("Secondary (derivative, compiled)"),
    QUESTIONABLE("Questionable (unreliable or conflicting)"),
    UNKNOWN("Unknown");

    companion object {
        fun fromString(value: String): CitationQuality =
            entries.firstOrNull { it.name == value } ?: UNKNOWN
    }
}

// Pre-built citation templates based on Evidence Explained
data class CitationTemplate(
    val id: String,
    val category: String,         // "Census Records", "Vital Records", etc.
    val name: String,             // "US Federal Census, 1850-1940"
    val fields: List<TemplateField>,
    val formatString: String      // "{{author}}, {{title}}, {{repository}}, {{date}}"
) {
    fun format(values: Map<String, String>): String {
        var result = formatString
        for ((key, value) in values) {
            result = result.replace("{{$key}}", value)
        }
        // Remove unfilled placeholders
        result = result.replace(Regex("\\{\\{[^}]+}}"), "")
        // Clean up orphaned punctuation
        result = result.replace(Regex(",\\s*,"), ",")
        result = result.replace(Regex(",\\s*\\."), ".")
        result = result.trim().trimEnd(',').trim()
        return result
    }
}

data class TemplateField(
    val name: String,
    val label: String,
    val required: Boolean,
    val placeholder: String
)

/**
 * Built-in citation templates covering the 20 most common genealogy record types.
 */
object CitationTemplates {

    val all: List<CitationTemplate> = listOf(
        // Census Records
        CitationTemplate(
            id = "us-federal-census",
            category = "Census Records",
            name = "US Federal Census (1790-1950)",
            fields = listOf(
                TemplateField("year", "Census Year", true, "1880"),
                TemplateField("state", "State", true, "New York"),
                TemplateField("county", "County", true, "Kings"),
                TemplateField("township", "Township/City", false, "Brooklyn"),
                TemplateField("enumeration_district", "Enumeration District", false, "123"),
                TemplateField("page", "Page/Sheet", true, "12A"),
                TemplateField("dwelling", "Dwelling/Family No.", false, "45"),
                TemplateField("head_of_household", "Head of Household", true, "John Smith"),
                TemplateField("nara_publication", "NARA Publication", false, "T9"),
                TemplateField("nara_roll", "NARA Roll", false, "456")
            ),
            formatString = "{{year}} US Federal Census, {{county}} County, {{state}}, {{township}}, ED {{enumeration_district}}, p. {{page}}, dwelling {{dwelling}}, {{head_of_household}} household; NARA {{nara_publication}}, roll {{nara_roll}}."
        ),

        // Vital Records - Birth
        CitationTemplate(
            id = "vital-birth",
            category = "Vital Records",
            name = "State Vital Records - Birth",
            fields = listOf(
                TemplateField("state", "State", true, "Massachusetts"),
                TemplateField("county", "County", false, "Suffolk"),
                TemplateField("city", "City/Town", false, "Boston"),
                TemplateField("person_name", "Person Name", true, "John Smith"),
                TemplateField("birth_date", "Birth Date", true, "15 Mar 1892"),
                TemplateField("certificate_number", "Certificate Number", false, "12345"),
                TemplateField("volume", "Volume", false, "23"),
                TemplateField("page", "Page", false, "45"),
                TemplateField("registrar", "Registrar/Office", false, "City Clerk")
            ),
            formatString = "{{state}} Birth Certificate, {{county}} County, {{city}}, {{person_name}}, born {{birth_date}}, certificate no. {{certificate_number}}, vol. {{volume}}, p. {{page}}, {{registrar}}."
        ),

        // Vital Records - Death
        CitationTemplate(
            id = "vital-death",
            category = "Vital Records",
            name = "State Vital Records - Death",
            fields = listOf(
                TemplateField("state", "State", true, "New York"),
                TemplateField("county", "County", false, "Kings"),
                TemplateField("city", "City/Town", false, "Brooklyn"),
                TemplateField("person_name", "Person Name", true, "John Smith"),
                TemplateField("death_date", "Death Date", true, "23 Nov 1960"),
                TemplateField("certificate_number", "Certificate Number", false, "67890"),
                TemplateField("volume", "Volume", false, "12"),
                TemplateField("page", "Page", false, "34"),
                TemplateField("informant", "Informant", false, "Mary Smith")
            ),
            formatString = "{{state}} Death Certificate, {{county}} County, {{city}}, {{person_name}}, died {{death_date}}, certificate no. {{certificate_number}}, vol. {{volume}}, p. {{page}}, informant: {{informant}}."
        ),

        // Vital Records - Marriage
        CitationTemplate(
            id = "vital-marriage",
            category = "Vital Records",
            name = "State Vital Records - Marriage",
            fields = listOf(
                TemplateField("state", "State", true, "Pennsylvania"),
                TemplateField("county", "County", true, "Philadelphia"),
                TemplateField("groom", "Groom Name", true, "John Smith"),
                TemplateField("bride", "Bride Name", true, "Mary Jones"),
                TemplateField("marriage_date", "Marriage Date", true, "10 Jun 1915"),
                TemplateField("license_number", "License Number", false, "11223"),
                TemplateField("volume", "Volume", false, "5"),
                TemplateField("page", "Page", false, "78")
            ),
            formatString = "{{state}} Marriage Record, {{county}} County, {{groom}} and {{bride}}, married {{marriage_date}}, license no. {{license_number}}, vol. {{volume}}, p. {{page}}."
        ),

        // Church Records - Baptism/Christening
        CitationTemplate(
            id = "church-baptism",
            category = "Church Records",
            name = "Church Records - Baptism/Christening",
            fields = listOf(
                TemplateField("church_name", "Church Name", true, "St. Mary's Catholic Church"),
                TemplateField("city", "City/Town", true, "Baltimore"),
                TemplateField("state", "State/Country", true, "Maryland"),
                TemplateField("person_name", "Person Name", true, "John Smith"),
                TemplateField("baptism_date", "Baptism Date", true, "22 Mar 1892"),
                TemplateField("parents", "Parents' Names", false, "James and Mary Smith"),
                TemplateField("volume", "Register Volume", false, "3"),
                TemplateField("page", "Page", false, "45"),
                TemplateField("officiant", "Officiant", false, "Rev. Patrick Murphy")
            ),
            formatString = "{{church_name}}, {{city}}, {{state}}, Baptismal Register, {{person_name}}, baptized {{baptism_date}}, parents: {{parents}}, vol. {{volume}}, p. {{page}}, officiant: {{officiant}}."
        ),

        // Church Records - Marriage
        CitationTemplate(
            id = "church-marriage",
            category = "Church Records",
            name = "Church Records - Marriage",
            fields = listOf(
                TemplateField("church_name", "Church Name", true, "First Baptist Church"),
                TemplateField("city", "City/Town", true, "Richmond"),
                TemplateField("state", "State/Country", true, "Virginia"),
                TemplateField("groom", "Groom Name", true, "John Smith"),
                TemplateField("bride", "Bride Name", true, "Mary Jones"),
                TemplateField("marriage_date", "Marriage Date", true, "15 Oct 1918"),
                TemplateField("volume", "Register Volume", false, "2"),
                TemplateField("page", "Page", false, "67"),
                TemplateField("officiant", "Officiant", false, "Rev. Thomas Brown")
            ),
            formatString = "{{church_name}}, {{city}}, {{state}}, Marriage Register, {{groom}} and {{bride}}, married {{marriage_date}}, vol. {{volume}}, p. {{page}}, officiant: {{officiant}}."
        ),

        // Church Records - Burial
        CitationTemplate(
            id = "church-burial",
            category = "Church Records",
            name = "Church Records - Burial",
            fields = listOf(
                TemplateField("church_name", "Church Name", true, "Trinity Episcopal Church"),
                TemplateField("city", "City/Town", true, "New York"),
                TemplateField("state", "State/Country", true, "New York"),
                TemplateField("person_name", "Person Name", true, "John Smith"),
                TemplateField("burial_date", "Burial Date", true, "25 Nov 1960"),
                TemplateField("volume", "Register Volume", false, "4"),
                TemplateField("page", "Page", false, "89")
            ),
            formatString = "{{church_name}}, {{city}}, {{state}}, Burial Register, {{person_name}}, buried {{burial_date}}, vol. {{volume}}, p. {{page}}."
        ),

        // Immigration/Ship Manifest
        CitationTemplate(
            id = "immigration-ship",
            category = "Immigration Records",
            name = "Immigration/Ship Manifest",
            fields = listOf(
                TemplateField("port_of_arrival", "Port of Arrival", true, "New York"),
                TemplateField("ship_name", "Ship Name", true, "SS Adriatic"),
                TemplateField("arrival_date", "Arrival Date", true, "15 Apr 1910"),
                TemplateField("passenger_name", "Passenger Name", true, "John Smith"),
                TemplateField("line_number", "Line Number", false, "12"),
                TemplateField("list_number", "List/Sheet Number", false, "3"),
                TemplateField("port_of_departure", "Port of Departure", false, "Liverpool"),
                TemplateField("nara_publication", "NARA Publication", false, "T715"),
                TemplateField("nara_roll", "NARA Roll", false, "1234")
            ),
            formatString = "Passenger manifest, {{ship_name}}, arriving {{port_of_arrival}}, {{arrival_date}}, from {{port_of_departure}}, {{passenger_name}}, list {{list_number}}, line {{line_number}}; NARA {{nara_publication}}, roll {{nara_roll}}."
        ),

        // Naturalization Record
        CitationTemplate(
            id = "naturalization",
            category = "Immigration Records",
            name = "Naturalization Record",
            fields = listOf(
                TemplateField("court", "Court Name", true, "US District Court"),
                TemplateField("city", "City", true, "Philadelphia"),
                TemplateField("state", "State", true, "Pennsylvania"),
                TemplateField("person_name", "Person Name", true, "John Smith"),
                TemplateField("petition_date", "Petition Date", true, "12 May 1920"),
                TemplateField("petition_number", "Petition Number", false, "45678"),
                TemplateField("volume", "Volume", false, "10"),
                TemplateField("page", "Page", false, "23"),
                TemplateField("certificate_number", "Certificate Number", false, "98765")
            ),
            formatString = "{{court}}, {{city}}, {{state}}, Naturalization Petition, {{person_name}}, {{petition_date}}, petition no. {{petition_number}}, vol. {{volume}}, p. {{page}}, certificate no. {{certificate_number}}."
        ),

        // Military Service Record
        CitationTemplate(
            id = "military-service",
            category = "Military Records",
            name = "Military Service Record",
            fields = listOf(
                TemplateField("person_name", "Person Name", true, "John Smith"),
                TemplateField("branch", "Branch of Service", true, "US Army"),
                TemplateField("war_conflict", "War/Conflict", false, "World War I"),
                TemplateField("rank", "Rank", false, "Private"),
                TemplateField("unit", "Unit/Regiment", false, "77th Division"),
                TemplateField("service_dates", "Service Dates", false, "1917-1919"),
                TemplateField("record_group", "Record Group", false, "RG 15"),
                TemplateField("repository", "Repository", false, "National Archives")
            ),
            formatString = "Military Service Record, {{person_name}}, {{rank}}, {{branch}}, {{unit}}, {{war_conflict}}, {{service_dates}}, {{record_group}}, {{repository}}."
        ),

        // Military Pension File
        CitationTemplate(
            id = "military-pension",
            category = "Military Records",
            name = "Military Pension File",
            fields = listOf(
                TemplateField("person_name", "Person Name", true, "John Smith"),
                TemplateField("war_conflict", "War/Conflict", true, "Civil War"),
                TemplateField("application_number", "Application Number", false, "WC-12345"),
                TemplateField("certificate_number", "Certificate Number", false, "67890"),
                TemplateField("state", "State Filed", false, "Ohio"),
                TemplateField("record_group", "Record Group", false, "RG 15"),
                TemplateField("repository", "Repository", false, "National Archives")
            ),
            formatString = "{{war_conflict}} Pension File, {{person_name}}, application no. {{application_number}}, certificate no. {{certificate_number}}, {{state}}, {{record_group}}, {{repository}}."
        ),

        // Land/Property Record
        CitationTemplate(
            id = "land-property",
            category = "Land & Property",
            name = "Land/Property Record",
            fields = listOf(
                TemplateField("county", "County", true, "Montgomery"),
                TemplateField("state", "State", true, "Ohio"),
                TemplateField("grantor", "Grantor (Seller)", true, "John Smith"),
                TemplateField("grantee", "Grantee (Buyer)", true, "James Brown"),
                TemplateField("deed_date", "Deed Date", true, "5 Aug 1875"),
                TemplateField("book", "Deed Book", false, "B"),
                TemplateField("page", "Page", false, "123"),
                TemplateField("description", "Property Description", false, "Lot 5, Block 3")
            ),
            formatString = "{{county}} County, {{state}}, Deed Records, {{grantor}} to {{grantee}}, {{deed_date}}, Book {{book}}, p. {{page}}, {{description}}."
        ),

        // Probate/Will
        CitationTemplate(
            id = "probate-will",
            category = "Probate Records",
            name = "Probate/Will",
            fields = listOf(
                TemplateField("county", "County", true, "Essex"),
                TemplateField("state", "State", true, "Massachusetts"),
                TemplateField("testator", "Testator Name", true, "John Smith"),
                TemplateField("will_date", "Will Date", true, "10 Jan 1920"),
                TemplateField("probate_date", "Probate Date", false, "15 Mar 1920"),
                TemplateField("case_number", "Case/File Number", false, "P-5678"),
                TemplateField("volume", "Volume", false, "45"),
                TemplateField("page", "Page", false, "67")
            ),
            formatString = "{{county}} County, {{state}}, Probate Records, Will of {{testator}}, dated {{will_date}}, probated {{probate_date}}, case no. {{case_number}}, vol. {{volume}}, p. {{page}}."
        ),

        // Newspaper Article/Obituary
        CitationTemplate(
            id = "newspaper-obituary",
            category = "Newspapers",
            name = "Newspaper Article/Obituary",
            fields = listOf(
                TemplateField("newspaper", "Newspaper Name", true, "The New York Times"),
                TemplateField("city", "City", false, "New York"),
                TemplateField("state", "State", false, "New York"),
                TemplateField("date", "Publication Date", true, "24 Nov 1960"),
                TemplateField("page", "Page", false, "B12"),
                TemplateField("column", "Column", false, "3"),
                TemplateField("headline", "Headline/Title", false, "John Smith, 68, Retired Engineer"),
                TemplateField("article_type", "Article Type", false, "Obituary")
            ),
            formatString = "\"{{headline}},\" {{newspaper}} ({{city}}, {{state}}), {{date}}, p. {{page}}, col. {{column}}."
        ),

        // Cemetery/Headstone
        CitationTemplate(
            id = "cemetery-headstone",
            category = "Cemetery Records",
            name = "Cemetery/Headstone",
            fields = listOf(
                TemplateField("cemetery", "Cemetery Name", true, "Green-Wood Cemetery"),
                TemplateField("city", "City/Town", true, "Brooklyn"),
                TemplateField("state", "State", true, "New York"),
                TemplateField("person_name", "Person Name", true, "John Smith"),
                TemplateField("section", "Section/Lot", false, "Section 14, Lot 234"),
                TemplateField("inscription", "Inscription Text", false, ""),
                TemplateField("visited_date", "Date Visited/Photographed", false, "15 Jun 2024")
            ),
            formatString = "{{cemetery}}, {{city}}, {{state}}, headstone for {{person_name}}, {{section}}, visited {{visited_date}}."
        ),

        // City Directory
        CitationTemplate(
            id = "city-directory",
            category = "Directories",
            name = "City Directory",
            fields = listOf(
                TemplateField("directory_name", "Directory Title", true, "Boyd's Philadelphia City Directory"),
                TemplateField("year", "Year", true, "1910"),
                TemplateField("city", "City", true, "Philadelphia"),
                TemplateField("state", "State", true, "Pennsylvania"),
                TemplateField("person_name", "Person Name", true, "John Smith"),
                TemplateField("listing", "Listing Text", false, "carpenter, h 123 Main St"),
                TemplateField("page", "Page", false, "456"),
                TemplateField("publisher", "Publisher", false, "C.E. Howe Co.")
            ),
            formatString = "{{directory_name}}, {{year}} ({{city}}, {{state}}: {{publisher}}), p. {{page}}, {{person_name}}, {{listing}}."
        ),

        // Family Bible
        CitationTemplate(
            id = "family-bible",
            category = "Personal Records",
            name = "Family Bible",
            fields = listOf(
                TemplateField("owner", "Bible Owner/Family", true, "Smith Family"),
                TemplateField("publisher", "Bible Publisher", false, "American Bible Society"),
                TemplateField("pub_year", "Bible Publication Year", false, "1850"),
                TemplateField("entry_text", "Entry Text", true, "John Smith born 15 Mar 1892"),
                TemplateField("current_holder", "Current Holder", false, "Mary Smith Johnson"),
                TemplateField("location", "Location", false, "Portland, Oregon")
            ),
            formatString = "{{owner}} Bible ({{publisher}}, {{pub_year}}), entry: \"{{entry_text}},\" in possession of {{current_holder}}, {{location}}."
        ),

        // Published Family History/Book
        CitationTemplate(
            id = "published-book",
            category = "Published Sources",
            name = "Published Family History/Book",
            fields = listOf(
                TemplateField("author", "Author", true, "James Smith"),
                TemplateField("title", "Book Title", true, "The Smith Family of New England"),
                TemplateField("publisher", "Publisher", false, "Heritage Books"),
                TemplateField("pub_place", "Publication Place", false, "Baltimore"),
                TemplateField("pub_year", "Publication Year", true, "1995"),
                TemplateField("page", "Page", false, "123"),
                TemplateField("repository", "Repository/Library", false, "New England Historic Genealogical Society")
            ),
            formatString = "{{author}}, {{title}} ({{pub_place}}: {{publisher}}, {{pub_year}}), p. {{page}}."
        ),

        // Online Database
        CitationTemplate(
            id = "online-database",
            category = "Online Sources",
            name = "Online Database (Ancestry, FamilySearch, etc.)",
            fields = listOf(
                TemplateField("website", "Website Name", true, "Ancestry.com"),
                TemplateField("database_name", "Database/Collection Name", true, "1880 United States Federal Census"),
                TemplateField("url", "URL", false, "https://www.ancestry.com/..."),
                TemplateField("person_name", "Person Name", true, "John Smith"),
                TemplateField("original_source", "Original Source Citation", false, "NARA T9, roll 456"),
                TemplateField("access_date", "Date Accessed", true, "15 Mar 2024")
            ),
            formatString = "\"{{database_name}},\" {{website}}, entry for {{person_name}}, citing {{original_source}}, accessed {{access_date}}."
        ),

        // Personal Knowledge/Interview
        CitationTemplate(
            id = "personal-knowledge",
            category = "Personal Sources",
            name = "Personal Knowledge/Interview",
            fields = listOf(
                TemplateField("informant", "Informant Name", true, "Mary Smith Johnson"),
                TemplateField("relationship", "Relationship to Subject", false, "daughter"),
                TemplateField("interview_date", "Interview Date", true, "15 Jun 2024"),
                TemplateField("interview_place", "Interview Place", false, "Portland, Oregon"),
                TemplateField("interview_method", "Method", false, "in-person interview"),
                TemplateField("topic", "Topic/Subject", false, "Family history of John Smith"),
                TemplateField("interviewer", "Interviewer", false, "James Smith III")
            ),
            formatString = "{{informant}} ({{relationship}}), {{interview_method}}, {{interview_place}}, {{interview_date}}, regarding {{topic}}, interviewed by {{interviewer}}."
        )
    )

    val categories: List<String> = all.map { it.category }.distinct().sorted()

    fun byCategory(category: String): List<CitationTemplate> =
        all.filter { it.category == category }

    fun byId(id: String): CitationTemplate? =
        all.firstOrNull { it.id == id }
}
