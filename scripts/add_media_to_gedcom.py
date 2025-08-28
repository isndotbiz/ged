#!/usr/bin/env python3
"""
Add Media to GEDCOM Script

This script adds media object records back to a cleaned GEDCOM file.
It scans the media folder, creates proper GEDCOM media object records,
and attempts to link them to individuals based on filename analysis.
"""

import argparse
import os
import re
import sys
from pathlib import Path
from typing import Dict, List, Set, Tuple, Optional
from dataclasses import dataclass
import uuid

@dataclass
class MediaFile:
    """Represents a media file with metadata"""
    filename: str
    filepath: Path
    relative_path: str
    file_type: str
    file_size: int
    potential_names: List[str] = None
    potential_dates: List[str] = None
    
    def __post_init__(self):
        if self.potential_names is None:
            self.potential_names = []
        if self.potential_dates is None:
            self.potential_dates = []

@dataclass
class Individual:
    """Represents an individual from GEDCOM"""
    id: str
    name: str
    given_names: str = ""
    surname: str = ""
    birth_year: str = ""
    line_start: int = 0
    line_end: int = 0

class MediaGedcomProcessor:
    """Processor to add media objects to GEDCOM files"""
    
    def __init__(self, media_folder: Path, use_relative_paths: bool = True):
        self.media_folder = media_folder
        self.use_relative_paths = use_relative_paths
        self.media_files: List[MediaFile] = []
        self.individuals: Dict[str, Individual] = {}
        self.media_counter = 1
        self.gedcom_lines: List[str] = []
        
    def scan_media_folder(self) -> None:
        """Scan media folder and catalog all media files"""
        print(f"📁 Scanning media folder: {self.media_folder}")
        
        supported_extensions = {
            '.jpg', '.jpeg', '.png', '.gif', '.bmp', '.tiff', '.tif',
            '.pdf', '.doc', '.docx', '.txt', '.rtf',
            '.mp4', '.avi', '.mov', '.wmv', '.mp3', '.wav'
        }
        
        for root, dirs, files in os.walk(self.media_folder):
            for file in files:
                filepath = Path(root) / file
                extension = filepath.suffix.lower()
                
                if extension in supported_extensions:
                    # Calculate relative path from media folder
                    try:
                        relative_path = filepath.relative_to(self.media_folder)
                    except ValueError:
                        relative_path = filepath
                    
                    media_file = MediaFile(
                        filename=file,
                        filepath=filepath,
                        relative_path=str(relative_path),
                        file_type=extension[1:].upper(),
                        file_size=filepath.stat().st_size if filepath.exists() else 0
                    )
                    
                    # Extract potential names and dates from filename
                    self._extract_metadata_from_filename(media_file)
                    self.media_files.append(media_file)
        
        print(f"✅ Found {len(self.media_files)} media files")
    
    def _extract_metadata_from_filename(self, media_file: MediaFile) -> None:
        """Extract potential names and dates from filename"""
        filename = media_file.filename
        
        # Extract potential names (words that look like names)
        name_pattern = r'([A-Z][a-z]+(?:\s+[A-Z][a-z]+)*)'
        potential_names = re.findall(name_pattern, filename.replace('_', ' ').replace('-', ' '))
        media_file.potential_names = [name.strip() for name in potential_names if len(name) > 2]
        
        # Extract potential dates (4-digit years, dates)
        date_patterns = [
            r'\b(19\d{2}|20\d{2})\b',  # Years
            r'\b(\d{1,2}[-/]\d{1,2}[-/]\d{4})\b',  # MM/DD/YYYY or MM-DD-YYYY
            r'\b(\d{4}[-/]\d{1,2}[-/]\d{1,2})\b'   # YYYY/MM/DD or YYYY-MM-DD
        ]
        
        for pattern in date_patterns:
            dates = re.findall(pattern, filename)
            media_file.potential_dates.extend(dates)
    
    def load_gedcom(self, gedcom_path: Path) -> None:
        """Load GEDCOM file and extract individuals"""
        print(f"📖 Loading GEDCOM file: {gedcom_path}")
        
        with open(gedcom_path, 'r', encoding='utf-8') as f:
            self.gedcom_lines = f.readlines()
        
        # Parse individuals for potential matching
        i = 0
        while i < len(self.gedcom_lines):
            line = self.gedcom_lines[i].strip()
            
            if re.match(r'^0 @I\d+@ INDI', line):
                individual = self._parse_individual(i)
                if individual:
                    self.individuals[individual.id] = individual
            i += 1
        
        print(f"✅ Loaded {len(self.individuals)} individuals from GEDCOM")
    
    def _parse_individual(self, start_line: int) -> Optional[Individual]:
        """Parse individual record from GEDCOM lines"""
        if start_line >= len(self.gedcom_lines):
            return None
            
        header_line = self.gedcom_lines[start_line].strip()
        individual_id = header_line.split()[1]
        
        individual = Individual(id=individual_id, name="", line_start=start_line)
        
        i = start_line + 1
        while i < len(self.gedcom_lines):
            line = self.gedcom_lines[i].strip()
            
            # Stop at next level 0 record
            if line.startswith('0 '):
                break
                
            if line.startswith('1 NAME '):
                name_value = line[7:].strip()
                individual.name = name_value
                if '/' in name_value:
                    parts = name_value.split('/')
                    individual.given_names = parts[0].strip()
                    if len(parts) > 1:
                        individual.surname = parts[1].strip()
                else:
                    individual.given_names = name_value
            elif line.startswith('1 BIRT'):
                # Look for birth year
                j = i + 1
                while j < len(self.gedcom_lines) and self.gedcom_lines[j].strip().startswith('2 '):
                    date_line = self.gedcom_lines[j].strip()
                    if date_line.startswith('2 DATE '):
                        date_str = date_line[7:].strip()
                        year_match = re.search(r'\b(19|20)\d{2}\b', date_str)
                        if year_match:
                            individual.birth_year = year_match.group()
                    j += 1
            i += 1
        
        individual.line_end = i - 1
        return individual
    
    def create_media_objects(self) -> List[str]:
        """Create GEDCOM media object records"""
        print("🖼️  Creating media object records...")
        
        media_objects = []
        
        for i, media_file in enumerate(self.media_files, 1):
            media_id = f"@M{i:04d}@"
            
            # Determine file path to use
            if self.use_relative_paths:
                file_path = str(media_file.relative_path)
            else:
                file_path = str(media_file.filepath)
            
            # Create media object record
            media_record = [
                f"0 {media_id} OBJE\n",
                f"1 FILE {file_path}\n",
                f"2 FORM {media_file.file_type}\n",
                f"2 TYPE photo\n",  # Default to photo, could be enhanced
                f"1 TITL {media_file.filename}\n"
            ]
            
            # Add notes about potential connections
            if media_file.potential_names or media_file.potential_dates:
                notes = []
                if media_file.potential_names:
                    notes.append(f"Potential names: {', '.join(media_file.potential_names)}")
                if media_file.potential_dates:
                    notes.append(f"Potential dates: {', '.join(media_file.potential_dates)}")
                
                media_record.append(f"1 NOTE {'; '.join(notes)}\n")
            
            media_record.append("\n")
            media_objects.extend(media_record)
        
        print(f"✅ Created {len(self.media_files)} media object records")
        return media_objects
    
    def suggest_media_links(self) -> Dict[str, List[str]]:
        """Suggest potential links between media and individuals"""
        print("🔗 Analyzing potential media-individual connections...")
        
        suggestions = {}
        
        for media_file in self.media_files:
            potential_matches = []
            
            for individual in self.individuals.values():
                score = 0
                
                # Check name matches
                individual_names = [individual.given_names, individual.surname, individual.name]
                individual_names = [name.lower() for name in individual_names if name]
                
                for potential_name in media_file.potential_names:
                    potential_name_lower = potential_name.lower()
                    for ind_name in individual_names:
                        if potential_name_lower in ind_name or ind_name in potential_name_lower:
                            score += 10
                
                # Check date matches
                if individual.birth_year and media_file.potential_dates:
                    for date in media_file.potential_dates:
                        if individual.birth_year in date:
                            score += 5
                
                # Check filename contains individual name parts
                filename_lower = media_file.filename.lower()
                for ind_name in individual_names:
                    if len(ind_name) > 2 and ind_name in filename_lower:
                        score += 3
                
                if score > 5:  # Minimum threshold
                    potential_matches.append((individual.id, individual.name, score))
            
            if potential_matches:
                # Sort by score, keep top matches
                potential_matches.sort(key=lambda x: x[2], reverse=True)
                suggestions[media_file.filename] = potential_matches[:3]  # Top 3 matches
        
        print(f"✅ Found potential connections for {len(suggestions)} media files")
        return suggestions
    
    def write_gedcom_with_media(self, output_path: Path, suggestions: Dict[str, List[str]] = None) -> None:
        """Write GEDCOM file with media objects added.
        Strategy: write the original GEDCOM and insert all level-0 OBJE records
        immediately before the trailer (0 TRLR). This placement is GEDCOM-valid
        and avoids assumptions about where SOUR or INDI records appear.
        """
        print(f"💾 Writing GEDCOM with media to: {output_path}")
        
        media_objects = self.create_media_objects()
        media_blob = "".join(media_objects)
        
        with open(output_path, 'w', encoding='utf-8') as f:
            for line in self.gedcom_lines:
                # Just before writing the trailer, inject media objects
                if line.strip() == '0 TRLR':
                    # Ensure there is a separating newline
                    if not self.gedcom_lines[-1].endswith('\n'):
                        f.write('\n')
                    f.write(media_blob)
                f.write(line)
        
        # Generate suggestions report
        if suggestions:
            self._write_suggestions_report(output_path.parent / "media_suggestions.txt", suggestions)
    
    def _write_suggestions_report(self, report_path: Path, suggestions: Dict[str, List[str]]) -> None:
        """Write media linking suggestions to a report file"""
        print(f"📊 Writing suggestions report to: {report_path}")
        
        with open(report_path, 'w', encoding='utf-8') as f:
            f.write("MEDIA LINKING SUGGESTIONS REPORT\n")
            f.write("=" * 50 + "\n\n")
            
            f.write(f"Total media files: {len(self.media_files)}\n")
            f.write(f"Files with potential matches: {len(suggestions)}\n\n")
            
            for filename, matches in suggestions.items():
                f.write(f"Media File: {filename}\n")
                f.write("-" * 40 + "\n")
                for individual_id, name, score in matches:
                    f.write(f"  → {individual_id} - {name} (score: {score})\n")
                f.write("\n")
            
            f.write("\nNOTE: These are automated suggestions based on filename analysis.\n")
            f.write("Please review and manually link media in your genealogy software.\n")

def main():
    parser = argparse.ArgumentParser(description="Add media objects to GEDCOM file")
    parser.add_argument("input_gedcom", type=Path, help="Input GEDCOM file")
    parser.add_argument("output_gedcom", type=Path, help="Output GEDCOM file with media")
    parser.add_argument("--media-folder", type=Path, required=True, help="Path to media folder")
    parser.add_argument("--absolute-paths", action="store_true", help="Use absolute paths instead of relative")
    parser.add_argument("--suggestions", action="store_true", help="Generate media linking suggestions")
    parser.add_argument("--verbose", "-v", action="store_true", help="Verbose output")
    
    args = parser.parse_args()
    
    if not args.input_gedcom.exists():
        print(f"❌ Error: Input GEDCOM file not found: {args.input_gedcom}")
        sys.exit(1)
    
    if not args.media_folder.exists():
        print(f"❌ Error: Media folder not found: {args.media_folder}")
        sys.exit(1)
    
    # Ensure output directory exists
    args.output_gedcom.parent.mkdir(parents=True, exist_ok=True)
    
    try:
        print("🚀 Starting media addition process")
        
        # Initialize processor
        processor = MediaGedcomProcessor(
            args.media_folder, 
            use_relative_paths=not args.absolute_paths
        )
        
        # Scan media folder
        processor.scan_media_folder()
        
        # Load GEDCOM
        processor.load_gedcom(args.input_gedcom)
        
        # Generate suggestions if requested
        suggestions = None
        if args.suggestions:
            suggestions = processor.suggest_media_links()
        
        # Write output file
        processor.write_gedcom_with_media(args.output_gedcom, suggestions)
        
        print("\n✅ Media addition completed successfully!")
        print(f"   Input: {len(processor.individuals)} individuals")
        print(f"   Media: {len(processor.media_files)} media files added")
        if suggestions:
            print(f"   Suggestions: {len(suggestions)} files with potential matches")
        
    except Exception as e:
        print(f"❌ Error during media addition: {e}")
        if args.verbose:
            import traceback
            traceback.print_exc()
        sys.exit(1)

if __name__ == "__main__":
    main()
