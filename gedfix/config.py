from __future__ import annotations
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List, Optional, Set
import yaml
import importlib.resources as res
import os


@dataclass
class PathsConfig:
    """Default paths for output files and directories."""
    output_dir: str = "out"
    backup_dir: str = "out/backup"
    check_report: str = "out/check_report.md"
    scan_report: str = "out/report.json"
    output_ged: str = "out/gedfix_fixed.ged"
    dedupe_output: str = "out/deduplicated.ged"
    dry_run_temp: str = "out/_dry_run.ged"
    dedupe_dry_run_temp: str = "out/_dedupe_dry_run.ged"
    issues_file: str = "manual_review_issues.json"


@dataclass
class ThresholdsConfig:
    """Numerical thresholds for various operations."""
    # Deduplication thresholds
    default_dedup_threshold: float = 95.0
    auto_merge_threshold: float = 100.0
    name_duplicate_threshold: int = 85
    validation_duplicate_threshold: float = 85.0

    # Similarity scoring
    exact_birth_year_bonus: int = 20
    close_birth_year_bonus: int = 10
    large_year_diff_penalty: int = -20
    close_year_range: int = 2
    large_year_range: int = 10

    # Checker thresholds
    surname_min_occurrences: int = 2
    surname_similarity_standard: int = 90
    surname_similarity_aggressive: int = 92
    surname_similarity_ultra: int = 88
    place_similarity_standard: int = 90
    place_similarity_aggressive: int = 92
    place_similarity_ultra: int = 88


@dataclass
class NotesConfig:
    """Note prefixes and message templates."""
    note_prefix: str = "AutoFix:"
    dedup_message: str = "Duplicates removed using comprehensive automation"
    merge_message: str = "Merged duplicate individual {secondary_id}. Combined data from both records."
    date_standardized: str = "Date standardized from '{original}' to GEDCOM format."
    date_unrecognized: str = "Unrecognized or non-GEDCOM date preserved."
    name_standardized: str = "Name formatting standardized."
    name_normalized: str = "Name formatting standardized and normalized."
    place_standardized: str = "Place name formatting standardized."
    place_normalized: str = "Place name standardized and normalized."
    duplicate_removed: str = "Removed duplicate event block"
    suffix_moved: str = "Moved suffix '{suffix}' to NSFX"
    duplicate_marr_removed: str = "Removed duplicate MARR block"


@dataclass
class GedcomConfig:
    """GEDCOM-specific settings."""
    # Standard GEDCOM months
    months: Set[str] = field(default_factory=lambda: {
        "JAN", "FEB", "MAR", "APR", "MAY", "JUN",
        "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"
    })

    # Month name conversions
    month_names: Dict[str, str] = field(default_factory=lambda: {
        "JANUARY": "JAN", "FEBRUARY": "FEB", "MARCH": "MAR", "APRIL": "APR",
        "MAY": "MAY", "JUNE": "JUN", "JULY": "JUL", "AUGUST": "AUG",
        "SEPTEMBER": "SEP", "OCTOBER": "OCT", "NOVEMBER": "NOV", "DECEMBER": "DEC",
        "SEPT": "SEP", "JAN.": "JAN", "FEB.": "FEB", "MAR.": "MAR", "APR.": "APR",
        "JUN.": "JUN", "JUL.": "JUL", "AUG.": "AUG", "SEP.": "SEP", "SEPT.": "SEP",
        "OCT.": "OCT", "NOV.": "NOV", "DEC.": "DEC"
    })

    # Date qualifiers
    qualifiers: Set[str] = field(default_factory=lambda: {
        "ABT", "EST", "CAL", "INT", "BEF", "AFT", "BET", "FROM", "TO", "AND"
    })

    # Circa term mappings
    circa_terms: Dict[str, str] = field(default_factory=lambda: {
        "CIRCA": "ABT", "ABOUT": "ABT", "C.": "ABT", "CA.": "ABT", "APPROX": "ABT"
    })

    # Event tags to check for duplicates
    event_tags: Set[str] = field(default_factory=lambda: {"BIRT", "DEAT", "CHR", "BAPM"})

    # Marriage tag
    marriage_tag: str = "MARR"

    # Spouse tags
    spouse_tags: List[str] = field(default_factory=lambda: ["HUSB", "WIFE"])

    # Family member tags
    family_member_tags: List[str] = field(default_factory=lambda: ["HUSB", "WIFE", "CHIL"])

    # Header template lines
    header_lines: List[str] = field(default_factory=lambda: [
        "0 HEAD",
        "1 SOUR gedfix",
        "1 CHAR UTF-8"
    ])

    # Trailer line
    trailer: str = "0 TRLR"

    # Timestamp format for backups
    timestamp_format: str = "%Y%m%d-%H%M%S"


@dataclass
class NamesConfig:
    """Name processing configuration."""
    # Name title prefixes
    title_prefixes: Set[str] = field(default_factory=lambda: {
        "DR", "MR", "MRS", "MS", "MISS", "REV", "FR",
        "SIR", "LADY", "LORD", "COUNT", "DUKE"
    })

    # Name suffixes
    suffixes: Set[str] = field(default_factory=lambda: {
        "JR", "SR", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "ESQ", "MD", "PHD", "DDS"
    })

    # Extended suffixes for checker (includes roman numerals up to X)
    suffixes_extended: Set[str] = field(default_factory=lambda: {
        "JR", "SR", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "ESQ"
    })

    # Special name prefixes (Scottish/Irish)
    special_prefixes: List[str] = field(default_factory=lambda: ["mc", "mac"])

    # Nickname mappings
    nickname_mappings: Dict[str, str] = field(default_factory=lambda: {
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
    })


@dataclass
class PlacesConfig:
    """Place name processing configuration."""
    # US state abbreviations
    state_abbreviations: Dict[str, str] = field(default_factory=lambda: {
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
    })

    # Country abbreviations
    country_abbreviations: Dict[str, str] = field(default_factory=lambda: {
        "US": "United States", "USA": "United States", "UK": "United Kingdom",
        "GB": "United Kingdom", "DE": "Germany", "FR": "France", "IT": "Italy",
        "ES": "Spain", "NL": "Netherlands", "BE": "Belgium", "CH": "Switzerland",
        "AT": "Austria", "DK": "Denmark", "NO": "Norway", "SE": "Sweden", "FI": "Finland",
        "IE": "Ireland", "PL": "Poland", "CZ": "Czech Republic", "HU": "Hungary"
    })

    # Place name variations
    place_variations: Dict[str, str] = field(default_factory=lambda: {
        "NYC": "New York City", "LA": "Los Angeles", "SF": "San Francisco",
        "ST.": "Saint", "ST ": "Saint ", "MT.": "Mount", "MT ": "Mount ",
        "FT.": "Fort", "FT ": "Fort "
    })

    # Words to keep lowercase in place names
    lowercase_words: List[str] = field(default_factory=lambda: [
        "of", "the", "and", "in", "on", "at", "by", "for", "with"
    ])

    # State abbreviation length check
    state_abbrev_length: int = 2


@dataclass
class LevelConfig:
    """Configuration for a processing level."""
    merge_duplicates: bool = False
    fuzzy_threshold: int = 96
    fix_dates: bool = True
    fix_names: bool = True
    fix_places: bool = False
    structural_validation: bool = False
    advanced_name_resolution: bool = False
    historical_place_mapping: bool = False
    date_consistency_checks: bool = False


@dataclass
class Config:
    """Main configuration container."""
    paths: PathsConfig = field(default_factory=PathsConfig)
    thresholds: ThresholdsConfig = field(default_factory=ThresholdsConfig)
    notes: NotesConfig = field(default_factory=NotesConfig)
    gedcom: GedcomConfig = field(default_factory=GedcomConfig)
    names: NamesConfig = field(default_factory=NamesConfig)
    places: PlacesConfig = field(default_factory=PlacesConfig)
    levels: Dict[str, LevelConfig] = field(default_factory=lambda: {
        "standard": LevelConfig(),
        "aggressive": LevelConfig(
            merge_duplicates=True,
            fuzzy_threshold=92,
            fix_places=True,
            structural_validation=True
        ),
        "ultra": LevelConfig(
            merge_duplicates=True,
            fuzzy_threshold=88,
            fix_places=True,
            structural_validation=True
        ),
        "comprehensive": LevelConfig(
            merge_duplicates=True,
            fuzzy_threshold=90,
            fix_places=True,
            structural_validation=True,
            advanced_name_resolution=True,
            historical_place_mapping=True,
            date_consistency_checks=True
        )
    })

    @classmethod
    def from_dict(cls, data: Dict) -> "Config":
        """Create Config from dictionary (e.g., parsed YAML)."""
        config = cls()

        if "paths" in data:
            for key, value in data["paths"].items():
                if hasattr(config.paths, key):
                    setattr(config.paths, key, value)

        if "thresholds" in data:
            for key, value in data["thresholds"].items():
                if hasattr(config.thresholds, key):
                    setattr(config.thresholds, key, value)

        if "notes" in data:
            for key, value in data["notes"].items():
                if hasattr(config.notes, key):
                    setattr(config.notes, key, value)

        if "gedcom" in data:
            for key, value in data["gedcom"].items():
                if hasattr(config.gedcom, key):
                    if isinstance(value, list):
                        if key in ("months", "qualifiers", "event_tags"):
                            setattr(config.gedcom, key, set(value))
                        else:
                            setattr(config.gedcom, key, value)
                    else:
                        setattr(config.gedcom, key, value)

        if "names" in data:
            for key, value in data["names"].items():
                if hasattr(config.names, key):
                    if isinstance(value, list) and key in ("title_prefixes", "suffixes", "suffixes_extended"):
                        setattr(config.names, key, set(value))
                    else:
                        setattr(config.names, key, value)

        if "places" in data:
            for key, value in data["places"].items():
                if hasattr(config.places, key):
                    setattr(config.places, key, value)

        if "levels" in data:
            for level_name, level_data in data["levels"].items():
                level_config = LevelConfig()
                for key, value in level_data.items():
                    if hasattr(level_config, key):
                        setattr(level_config, key, value)
                config.levels[level_name] = level_config

        return config


# Global configuration instance
_config: Optional[Config] = None


def load_config(config_path: Optional[Path] = None) -> Config:
    """Load configuration from YAML file(s).

    Configuration is loaded in order (later values override earlier):
    1. Built-in defaults (in code)
    2. Package config.yaml (if exists)
    3. User-specified config file (if provided)
    4. Environment variables (if set)
    """
    global _config

    if _config is not None and config_path is None:
        return _config

    config = Config()

    # Load package config.yaml if exists
    try:
        with res.files("gedfix").joinpath("config.yaml").open("r", encoding="utf-8") as f:
            pkg_data = yaml.safe_load(f) or {}
            if pkg_data:
                config = Config.from_dict(pkg_data)
    except (FileNotFoundError, TypeError):
        pass

    # Load user config if provided
    if config_path and config_path.exists():
        with config_path.open("r", encoding="utf-8") as f:
            user_data = yaml.safe_load(f) or {}
            if user_data:
                config = Config.from_dict(user_data)

    # Override with environment variables
    if os.environ.get("GEDFIX_NOTE_PREFIX"):
        config.notes.note_prefix = os.environ["GEDFIX_NOTE_PREFIX"]
    if os.environ.get("GEDFIX_OUTPUT_DIR"):
        config.paths.output_dir = os.environ["GEDFIX_OUTPUT_DIR"]
    if os.environ.get("GEDFIX_BACKUP_DIR"):
        config.paths.backup_dir = os.environ["GEDFIX_BACKUP_DIR"]
    if os.environ.get("GEDFIX_DEDUP_THRESHOLD"):
        config.thresholds.default_dedup_threshold = float(os.environ["GEDFIX_DEDUP_THRESHOLD"])

    _config = config
    return config


def get_config() -> Config:
    """Get the current configuration (loading if necessary)."""
    global _config
    if _config is None:
        _config = load_config()
    return _config


def reset_config() -> None:
    """Reset configuration to force reload."""
    global _config
    _config = None


# Backwards compatibility with rules.py
def load_rules() -> Dict:
    """Load rules in the old format for backwards compatibility."""
    config = get_config()
    return {
        "note_prefix": config.notes.note_prefix,
        "levels": {
            name: {
                "merge_duplicates": level.merge_duplicates,
                "fuzzy_threshold": level.fuzzy_threshold,
                "fix_dates": level.fix_dates,
                "fix_names": level.fix_names,
                "fix_places": level.fix_places,
                "structural_validation": level.structural_validation,
                "advanced_name_resolution": level.advanced_name_resolution,
                "historical_place_mapping": level.historical_place_mapping,
                "date_consistency_checks": level.date_consistency_checks,
            }
            for name, level in config.levels.items()
        }
    }
