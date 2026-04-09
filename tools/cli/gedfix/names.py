from __future__ import annotations
import re
from rapidfuzz import fuzz

# Common name prefixes and suffixes
NAME_PREFIXES = {"DR", "MR", "MRS", "MS", "MISS", "REV", "FR", "SIR", "LADY", "LORD", "COUNT", "DUKE"}
NAME_SUFFIXES = {"JR", "SR", "II", "III", "IV", "V", "ESQ", "MD", "PHD", "DDS"}

# Common nickname mappings
NICKNAME_MAPPINGS = {
    "BILL": "WILLIAM", "BILLY": "WILLIAM", "WILL": "WILLIAM", "WILLIE": "WILLIAM",
    "BOB": "ROBERT", "BOBBY": "ROBERT", "ROB": "ROBERT", "ROBBIE": "ROBERT",
    "DICK": "RICHARD", "RICK": "RICHARD", "RICKY": "RICHARD", "RICH": "RICHARD",
    "JIM": "JAMES", "JIMMY": "JAMES", "JAMIE": "JAMES",
    "JOHN": "JOHN", "JACK": "JOHN", "JOHNNY": "JOHN",
    "TOM": "THOMAS", "TOMMY": "THOMAS", "THOM": "THOMAS",
    "SAM": "SAMUEL", "SAMMY": "SAMUEL",
    "DAN": "DANIEL", "DANNY": "DANIEL",
    "MIKE": "MICHAEL", "MICKEY": "MICHAEL",
    "DAVE": "DAVID", "DAVEY": "DAVID",
    "CHRIS": "CHRISTOPHER", "CHRISTA": "CHRISTINA",
    "BETH": "ELIZABETH", "BETTY": "ELIZABETH", "LIZ": "ELIZABETH", "LIZZY": "ELIZABETH",
    "SUE": "SUSAN", "SUSIE": "SUSAN", "SUZY": "SUSAN",
    "KATE": "KATHERINE", "KATIE": "KATHERINE", "KATHY": "KATHERINE",
    "PATTY": "PATRICIA", "PAT": "PATRICIA", "TRICIA": "PATRICIA"
}

def normalize_name_case(name: str) -> str:
    """Normalize name capitalization following genealogical conventions."""
    if not name:
        return name
    
    # Handle surnames in slashes (GEDCOM format)
    if '/' in name:
        parts = name.split('/')
        if len(parts) == 3:  # "Given /Surname/"
            given = parts[0].strip()
            surname = parts[1].strip()
            suffix = parts[2].strip()
            
            # Capitalize given name properly
            given_words = []
            for word in given.split():
                if word.upper() in NAME_PREFIXES:
                    given_words.append(word.upper())
                else:
                    given_words.append(capitalize_name_word(word))
            
            # Capitalize surname properly
            surname_words = []
            for word in surname.split():
                surname_words.append(capitalize_name_word(word))
            
            return f"{' '.join(given_words)} /{' '.join(surname_words)}/"
    
    # Handle regular names
    words = []
    for word in name.split():
        if word.upper() in NAME_PREFIXES or word.upper() in NAME_SUFFIXES:
            words.append(word.upper())
        else:
            words.append(capitalize_name_word(word))
    
    return ' '.join(words)

def capitalize_name_word(word: str) -> str:
    """Capitalize a single name word with special handling for prefixes."""
    if not word:
        return word
    
    # Handle names with apostrophes (O'Connor, D'Angelo)
    if "'" in word:
        parts = word.split("'")
        return "'".join([capitalize_name_word(part) for part in parts])
    
    # Handle hyphenated names
    if '-' in word:
        parts = word.split('-')
        return '-'.join([capitalize_name_word(part) for part in parts])
    
    # Handle Scottish/Irish prefixes
    if word.lower().startswith('mc'):
        if len(word) > 2:
            return 'Mc' + word[2:].capitalize()
        return word.capitalize()
    
    if word.lower().startswith('mac'):
        if len(word) > 3:
            return 'Mac' + word[3:].capitalize()
        return word.capitalize()
    
    # Standard capitalization
    return word.capitalize()

def standardize_name_format(name: str) -> str:
    """Standardize name format and remove extra spaces."""
    # Remove extra whitespace
    name = re.sub(r'\s+', ' ', name.strip())
    
    # Normalize case
    name = normalize_name_case(name)
    
    # Ensure proper GEDCOM surname format
    if '/' not in name and ' ' in name:
        # This might be a name without surname markers
        # For now, just normalize spacing and case
        pass
    
    return name

def detect_potential_duplicates(names: list[str], threshold: int = 85) -> list[tuple[str, str, float]]:
    """Detect potential duplicate names using fuzzy matching."""
    duplicates = []
    
    for i, name1 in enumerate(names):
        for name2 in names[i+1:]:
            similarity = fuzz.ratio(name1.upper(), name2.upper())
            if similarity >= threshold:
                duplicates.append((name1, name2, similarity))
    
    return duplicates

def sanitize_name_value(raw: str, level: str = "standard") -> tuple[str, list[str]]:
    """
    Sanitize name values based on automation level.
    
    Args:
        raw: Original name value
        level: Automation level (standard, aggressive, comprehensive)
    
    Returns:
        Tuple of (sanitized_name, notes)
    """
    notes: list[str] = []
    original = raw
    
    if level == "standard":
        # Only normalize spacing and basic capitalization
        result = standardize_name_format(raw)
        if result != original:
            notes.append(f"Name formatting standardized.")
        return result, notes
    
    elif level in ["aggressive", "comprehensive"]:
        # Apply more comprehensive name standardization
        result = standardize_name_format(raw)
        
        # TODO: Add nickname resolution, duplicate detection, etc.
        # For now, just do basic standardization
        
        if result != original:
            notes.append(f"Name formatting standardized and normalized.")
        return result, notes
    
    return raw, notes
