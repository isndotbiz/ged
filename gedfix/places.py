from __future__ import annotations
import re
from typing import Dict, Set

# Common place abbreviations and their full forms
STATE_ABBREVIATIONS = {
    "AL": "Alabama", "AK": "Alaska", "AZ": "Arizona", "AR": "Arkansas", "CA": "California",
    "CO": "Colorado", "CT": "Connecticut", "DE": "Delaware", "FL": "Florida", "GA": "Georgia",
    "HI": "Hawaii", "ID": "Idaho", "IL": "Illinois", "IN": "Indiana", "IA": "Iowa",
    "KS": "Kansas", "KY": "Kentucky", "LA": "Louisiana", "ME": "Maine", "MD": "Maryland",
    "MA": "Massachusetts", "MI": "Michigan", "MN": "Minnesota", "MS": "Mississippi", "MO": "Missouri",
    "MT": "Montana", "NE": "Nebraska", "NV": "Nevada", "NH": "New Hampshire", "NJ": "New Jersey",
    "NM": "New Mexico", "NY": "New York", "NC": "North Carolina", "ND": "North Dakota", "OH": "Ohio",
    "OK": "Oklahoma", "OR": "Oregon", "PA": "Pennsylvania", "RI": "Rhode Island", "SC": "South Carolina",
    "SD": "South Dakota", "TN": "Tennessee", "TX": "Texas", "UT": "Utah", "VT": "Vermont",
    "VA": "Virginia", "WA": "Washington", "WV": "West Virginia", "WI": "Wisconsin", "WY": "Wyoming"
}

# Common country abbreviations
COUNTRY_ABBREVIATIONS = {
    "US": "United States", "USA": "United States", "UK": "United Kingdom", 
    "GB": "United Kingdom", "DE": "Germany", "FR": "France", "IT": "Italy",
    "ES": "Spain", "NL": "Netherlands", "BE": "Belgium", "CH": "Switzerland",
    "AT": "Austria", "DK": "Denmark", "NO": "Norway", "SE": "Sweden", "FI": "Finland",
    "IE": "Ireland", "PL": "Poland", "CZ": "Czech Republic", "HU": "Hungary"
}

# Common place name variations
PLACE_VARIATIONS = {
    "NYC": "New York City", "LA": "Los Angeles", "SF": "San Francisco",
    "ST.": "Saint", "ST ": "Saint ", "MT.": "Mount", "MT ": "Mount ",
    "FT.": "Fort", "FT ": "Fort "
}

def normalize_place_components(components: list[str]) -> list[str]:
    """Normalize individual components of a place name."""
    normalized = []
    
    for component in components:
        comp = component.strip()
        if not comp:
            continue
            
        # Handle common abbreviations
        upper_comp = comp.upper()
        
        # Check state abbreviations (only if this looks like a state component)
        if len(comp) == 2 and upper_comp in STATE_ABBREVIATIONS:
            normalized.append(STATE_ABBREVIATIONS[upper_comp])
            continue
            
        # Check country abbreviations
        if upper_comp in COUNTRY_ABBREVIATIONS:
            normalized.append(COUNTRY_ABBREVIATIONS[upper_comp])
            continue
            
        # Handle common place name variations
        for abbrev, full in PLACE_VARIATIONS.items():
            if comp.upper().startswith(abbrev):
                comp = full + comp[len(abbrev):]
                break
        
        # Capitalize properly
        comp = capitalize_place_name(comp)
        normalized.append(comp)
    
    return normalized

def capitalize_place_name(name: str) -> str:
    """Capitalize place names according to geographical conventions."""
    if not name:
        return name
    
    # Handle hyphenated place names
    if '-' in name:
        parts = name.split('-')
        return '-'.join([capitalize_place_name(part) for part in parts])
    
    # Handle names with apostrophes
    if "'" in name:
        parts = name.split("'")
        return "'".join([capitalize_place_name(part) for part in parts])
    
    # Special cases for geographical terms
    words = name.split()
    capitalized_words = []
    
    for word in words:
        lower_word = word.lower()
        
        # Keep certain words lowercase unless they're the first word
        if lower_word in ['of', 'the', 'and', 'in', 'on', 'at', 'by', 'for', 'with']:
            if len(capitalized_words) == 0:  # First word
                capitalized_words.append(word.capitalize())
            else:
                capitalized_words.append(lower_word)
        else:
            capitalized_words.append(word.capitalize())
    
    return ' '.join(capitalized_words)

def standardize_place_hierarchy(place: str) -> str:
    """Standardize place hierarchy format (City, County, State, Country)."""
    if not place:
        return place
    
    # Split by commas and normalize each component
    components = [comp.strip() for comp in place.split(',')]
    normalized_components = normalize_place_components(components)
    
    # Remove empty components
    normalized_components = [comp for comp in normalized_components if comp]
    
    # Join back with proper spacing
    return ', '.join(normalized_components)

def detect_place_inconsistencies(places: list[str]) -> list[tuple[str, str, str]]:
    """Detect potential place name inconsistencies."""
    inconsistencies = []
    
    # Group places by similarity
    place_groups: Dict[str, list[str]] = {}
    
    for place in places:
        # Create a normalized key for grouping
        key = place.upper().replace(',', '').replace('.', '').replace(' ', '')
        if key not in place_groups:
            place_groups[key] = []
        place_groups[key].append(place)
    
    # Find groups with multiple variations
    for key, group in place_groups.items():
        if len(group) > 1:
            for i, place1 in enumerate(group):
                for place2 in group[i+1:]:
                    if place1 != place2:
                        inconsistencies.append((place1, place2, "Similar but different formatting"))
    
    return inconsistencies

def sanitize_place_value(raw: str, level: str = "standard") -> tuple[str, list[str]]:
    """
    Sanitize place values based on automation level.
    
    Args:
        raw: Original place value
        level: Automation level (standard, aggressive, comprehensive)
    
    Returns:
        Tuple of (sanitized_place, notes)
    """
    notes: list[str] = []
    original = raw
    
    if level == "standard":
        # Only basic normalization
        result = standardize_place_hierarchy(raw)
        if result != original:
            notes.append("Place name formatting standardized.")
        return result, notes
    
    elif level in ["aggressive", "comprehensive"]:
        # More comprehensive place standardization
        result = standardize_place_hierarchy(raw)
        
        # TODO: Add more sophisticated place name resolution
        # - Historical place name mapping
        # - Administrative boundary changes
        # - Common misspellings correction
        
        if result != original:
            notes.append("Place name standardized and normalized.")
        return result, notes
    
    return raw, notes
