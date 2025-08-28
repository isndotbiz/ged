#!/usr/bin/env python3
"""
Safe Person Duplicate Detection Script

This script ONLY identifies potential duplicate people in a GEDCOM file.
It does NOT modify the file in any way - it only generates reports for manual review.
This prevents the data corruption issues we experienced with automated merging.
"""

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Dict, List, Set, Tuple, Optional
from dataclasses import dataclass
from rapidfuzz import fuzz

@dataclass
class Individual:
    """Represents a person in the GEDCOM file"""
    id: str
    name: str
    given_names: str = ""
    surname: str = ""
    sex: str = ""
    birth_date: str = ""
    birth_year: int = 0
    birth_place: str = ""
    death_date: str = ""
    death_year: int = 0
    death_place: str = ""
    families_as_spouse: List[str] = None
    families_as_child: List[str] = None
    line_start: int = 0
    line_end: int = 0

    def __post_init__(self):
        if self.families_as_spouse is None:
            self.families_as_spouse = []
        if self.families_as_child is None:
            self.families_as_child = []

@dataclass
class DuplicateMatch:
    """Represents a potential duplicate match between two individuals"""
    person1_id: str
    person1_name: str
    person2_id: str
    person2_name: str
    similarity_score: float
    match_reasons: List[str]
    confidence_level: str
    recommended_action: str

class SafeDuplicateDetector:
    """Safe duplicate detector that only analyzes, never modifies"""
    
    def __init__(self, high_threshold: float = 95.0, medium_threshold: float = 85.0):
        self.high_threshold = high_threshold
        self.medium_threshold = medium_threshold
        self.individuals: Dict[str, Individual] = {}
        self.all_lines: List[str] = []
        
    def load_gedcom(self, file_path: Path) -> None:
        """Load GEDCOM file for analysis only"""
        print(f"📖 Loading GEDCOM file: {file_path}")
        
        with open(file_path, 'r', encoding='utf-8') as f:
            self.all_lines = f.readlines()
        
        print(f"✅ Loaded {len(self.all_lines)} lines")
        self._parse_individuals()
        
    def _parse_individuals(self) -> None:
        """Parse individuals from GEDCOM"""
        print("🔍 Parsing individuals...")
        
        i = 0
        while i < len(self.all_lines):
            line = self.all_lines[i].strip()
            
            if re.match(r'^0 @I\d+@ INDI', line):
                individual = self._parse_individual(i)
                if individual:
                    self.individuals[individual.id] = individual
            i += 1
        
        print(f"✅ Parsed {len(self.individuals)} individuals")
    
    def _parse_individual(self, start_line: int) -> Optional[Individual]:
        """Parse a single individual record"""
        if start_line >= len(self.all_lines):
            return None
            
        header_line = self.all_lines[start_line].strip()
        individual_id = header_line.split()[1]
        
        individual = Individual(id=individual_id, name="", line_start=start_line)
        
        i = start_line + 1
        while i < len(self.all_lines):
            line = self.all_lines[i].strip()
            
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
                    
            elif line.startswith('1 SEX '):
                individual.sex = line[6:].strip()
                
            elif line.startswith('1 BIRT'):
                j = i + 1
                while j < len(self.all_lines) and self.all_lines[j].strip().startswith('2 '):
                    sub_line = self.all_lines[j].strip()
                    if sub_line.startswith('2 DATE '):
                        individual.birth_date = sub_line[7:].strip()
                        year_match = re.search(r'\b(19|20)\d{2}\b', individual.birth_date)
                        if year_match:
                            individual.birth_year = int(year_match.group())
                    elif sub_line.startswith('2 PLAC '):
                        individual.birth_place = sub_line[7:].strip()
                    j += 1
                    
            elif line.startswith('1 DEAT'):
                j = i + 1
                while j < len(self.all_lines) and self.all_lines[j].strip().startswith('2 '):
                    sub_line = self.all_lines[j].strip()
                    if sub_line.startswith('2 DATE '):
                        individual.death_date = sub_line[7:].strip()
                        year_match = re.search(r'\b(19|20)\d{2}\b', individual.death_date)
                        if year_match:
                            individual.death_year = int(year_match.group())
                    elif sub_line.startswith('2 PLAC '):
                        individual.death_place = sub_line[7:].strip()
                    j += 1
                    
            elif line.startswith('1 FAMS '):
                family_id = line[7:].strip()
                individual.families_as_spouse.append(family_id)
                
            elif line.startswith('1 FAMC '):
                family_id = line[7:].strip()
                individual.families_as_child.append(family_id)
            
            i += 1
        
        individual.line_end = i - 1
        return individual
    
    def find_potential_duplicates(self) -> List[DuplicateMatch]:
        """Find potential duplicate people using multiple criteria"""
        print("🔍 Analyzing potential duplicates...")
        
        duplicates = []
        individuals_list = list(self.individuals.items())
        processed_pairs = set()
        
        for i, (id1, ind1) in enumerate(individuals_list):
            if i % 100 == 0:
                print(f"  Processed {i}/{len(individuals_list)} individuals...")
                
            for id2, ind2 in individuals_list[i+1:]:
                # Skip if already processed this pair
                pair_key = tuple(sorted([id1, id2]))
                if pair_key in processed_pairs:
                    continue
                processed_pairs.add(pair_key)
                
                # Skip if names are empty
                if not ind1.name.strip() or not ind2.name.strip():
                    continue
                
                match = self._analyze_potential_match(ind1, ind2)
                if match and match.similarity_score >= self.medium_threshold:
                    duplicates.append(match)
        
        # Sort by similarity score (descending)
        duplicates.sort(key=lambda x: x.similarity_score, reverse=True)
        
        print(f"✅ Found {len(duplicates)} potential duplicate pairs")
        return duplicates
    
    def _analyze_potential_match(self, ind1: Individual, ind2: Individual) -> Optional[DuplicateMatch]:
        """Analyze two individuals for potential match"""
        score = 0
        reasons = []
        
        # Name similarity (most important factor)
        name1_clean = ind1.name.replace('/', '').strip().upper()
        name2_clean = ind2.name.replace('/', '').strip().upper()
        
        if not name1_clean or not name2_clean:
            return None
            
        name_similarity = fuzz.ratio(name1_clean, name2_clean)
        score += name_similarity * 0.4  # 40% weight
        
        if name_similarity >= 90:
            reasons.append(f"Very similar names ({name_similarity}%)")
        elif name_similarity >= 80:
            reasons.append(f"Similar names ({name_similarity}%)")
        
        # Birth year matching
        if ind1.birth_year and ind2.birth_year:
            year_diff = abs(ind1.birth_year - ind2.birth_year)
            if year_diff == 0:
                score += 25
                reasons.append("Same birth year")
            elif year_diff <= 2:
                score += 15
                reasons.append(f"Birth years within {year_diff} years")
            elif year_diff > 10:
                score -= 20
                reasons.append(f"Birth years differ by {year_diff} years")
        
        # Death year matching
        if ind1.death_year and ind2.death_year:
            year_diff = abs(ind1.death_year - ind2.death_year)
            if year_diff == 0:
                score += 15
                reasons.append("Same death year")
            elif year_diff <= 2:
                score += 10
                reasons.append(f"Death years within {year_diff} years")
        
        # Sex matching
        if ind1.sex and ind2.sex:
            if ind1.sex == ind2.sex:
                score += 10
                reasons.append("Same sex")
            else:
                score -= 15
                reasons.append("Different sex")
        
        # Birth place similarity
        if ind1.birth_place and ind2.birth_place:
            place_similarity = fuzz.ratio(ind1.birth_place.upper(), ind2.birth_place.upper())
            if place_similarity >= 80:
                score += 10
                reasons.append(f"Similar birth places ({place_similarity}%)")
        
        # Death place similarity  
        if ind1.death_place and ind2.death_place:
            place_similarity = fuzz.ratio(ind1.death_place.upper(), ind2.death_place.upper())
            if place_similarity >= 80:
                score += 5
                reasons.append(f"Similar death places ({place_similarity}%)")
        
        # Family relationship overlap penalty (likely different people if in same families)
        common_spouse_families = set(ind1.families_as_spouse) & set(ind2.families_as_spouse)
        common_child_families = set(ind1.families_as_child) & set(ind2.families_as_child)
        
        if common_spouse_families:
            score -= 30
            reasons.append("Both are spouses in same family (likely different people)")
        if common_child_families:
            score -= 20
            reasons.append("Both are children in same family (likely siblings)")
        
        # Determine confidence level and recommendation
        if score >= self.high_threshold:
            confidence = "HIGH"
            recommendation = "RECOMMENDED for automatic merge"
        elif score >= self.medium_threshold:
            confidence = "MEDIUM"
            recommendation = "MANUAL REVIEW suggested"
        else:
            return None
        
        return DuplicateMatch(
            person1_id=ind1.id,
            person1_name=ind1.name,
            person2_id=ind2.id,
            person2_name=ind2.name,
            similarity_score=round(score, 1),
            match_reasons=reasons,
            confidence_level=confidence,
            recommended_action=recommendation
        )
    
    def generate_reports(self, duplicates: List[DuplicateMatch], output_dir: Path) -> None:
        """Generate comprehensive duplicate analysis reports"""
        print(f"📊 Generating reports in: {output_dir}")
        
        # Categorize duplicates
        high_confidence = [d for d in duplicates if d.similarity_score >= self.high_threshold]
        medium_confidence = [d for d in duplicates if self.medium_threshold <= d.similarity_score < self.high_threshold]
        
        # Generate summary report
        self._write_summary_report(duplicates, high_confidence, medium_confidence, output_dir / "duplicate_summary.txt")
        
        # Generate detailed report
        self._write_detailed_report(duplicates, output_dir / "duplicate_detailed.txt")
        
        # Generate CSV for spreadsheet analysis
        self._write_csv_report(duplicates, output_dir / "duplicates_analysis.csv")
        
        # Generate JSON for programmatic processing
        self._write_json_report(duplicates, output_dir / "duplicates_data.json")
        
        # Generate RootsMagic import instructions
        self._write_rootsmagic_instructions(high_confidence, medium_confidence, output_dir / "rootsmagic_merge_guide.txt")
    
    def _write_summary_report(self, all_dups: List[DuplicateMatch], high_conf: List[DuplicateMatch], 
                            medium_conf: List[DuplicateMatch], report_path: Path) -> None:
        """Write summary report"""
        with open(report_path, 'w', encoding='utf-8') as f:
            f.write("PERSON DUPLICATE DETECTION SUMMARY\n")
            f.write("=" * 50 + "\n\n")
            
            f.write(f"Total individuals analyzed: {len(self.individuals)}\n")
            f.write(f"Total potential duplicates found: {len(all_dups)}\n\n")
            
            f.write("CONFIDENCE BREAKDOWN:\n")
            f.write(f"  High confidence (≥{self.high_threshold}%): {len(high_conf)} pairs\n")
            f.write(f"  Medium confidence ({self.medium_threshold}%-{self.high_threshold-0.1}%): {len(medium_conf)} pairs\n\n")
            
            if high_conf:
                f.write("TOP 10 HIGH-CONFIDENCE MATCHES:\n")
                f.write("-" * 40 + "\n")
                for i, dup in enumerate(high_conf[:10], 1):
                    f.write(f"{i}. {dup.person1_name} ↔ {dup.person2_name} ({dup.similarity_score}%)\n")
                    f.write(f"   IDs: {dup.person1_id} ↔ {dup.person2_id}\n")
                    f.write(f"   Reasons: {', '.join(dup.match_reasons)}\n\n")
            
            f.write("RECOMMENDED ACTIONS:\n")
            f.write("1. Review high-confidence matches first\n")
            f.write("2. Import GEDCOM into RootsMagic 10\n") 
            f.write("3. Use Tools > Merge > Find Duplicate People for safe merging\n")
            f.write("4. Manually verify each match before merging\n")
            f.write("5. Always backup before making changes\n")
    
    def _write_detailed_report(self, duplicates: List[DuplicateMatch], report_path: Path) -> None:
        """Write detailed analysis report"""
        with open(report_path, 'w', encoding='utf-8') as f:
            f.write("DETAILED DUPLICATE ANALYSIS\n")
            f.write("=" * 50 + "\n\n")
            
            for i, dup in enumerate(duplicates, 1):
                f.write(f"MATCH #{i} - {dup.confidence_level} CONFIDENCE ({dup.similarity_score}%)\n")
                f.write("-" * 60 + "\n")
                
                # Person 1 details
                ind1 = self.individuals[dup.person1_id]
                f.write(f"Person 1: {dup.person1_id} - {dup.person1_name}\n")
                f.write(f"  Sex: {ind1.sex or 'Unknown'}\n")
                f.write(f"  Birth: {ind1.birth_date or 'Unknown'} in {ind1.birth_place or 'Unknown place'}\n")
                f.write(f"  Death: {ind1.death_date or 'Unknown'} in {ind1.death_place or 'Unknown place'}\n")
                f.write(f"  Families as spouse: {len(ind1.families_as_spouse)}\n")
                f.write(f"  Families as child: {len(ind1.families_as_child)}\n\n")
                
                # Person 2 details  
                ind2 = self.individuals[dup.person2_id]
                f.write(f"Person 2: {dup.person2_id} - {dup.person2_name}\n")
                f.write(f"  Sex: {ind2.sex or 'Unknown'}\n")
                f.write(f"  Birth: {ind2.birth_date or 'Unknown'} in {ind2.birth_place or 'Unknown place'}\n")
                f.write(f"  Death: {ind2.death_date or 'Unknown'} in {ind2.death_place or 'Unknown place'}\n")
                f.write(f"  Families as spouse: {len(ind2.families_as_spouse)}\n")
                f.write(f"  Families as child: {len(ind2.families_as_child)}\n\n")
                
                f.write(f"Match reasons: {', '.join(dup.match_reasons)}\n")
                f.write(f"Recommendation: {dup.recommended_action}\n")
                f.write("\n" + "=" * 60 + "\n\n")
    
    def _write_csv_report(self, duplicates: List[DuplicateMatch], report_path: Path) -> None:
        """Write CSV report for spreadsheet analysis"""
        import csv
        
        with open(report_path, 'w', newline='', encoding='utf-8') as f:
            writer = csv.writer(f)
            writer.writerow([
                'Person1_ID', 'Person1_Name', 'Person2_ID', 'Person2_Name',
                'Similarity_Score', 'Confidence_Level', 'Recommendation',
                'Person1_Birth', 'Person1_Death', 'Person2_Birth', 'Person2_Death',
                'Match_Reasons'
            ])
            
            for dup in duplicates:
                ind1 = self.individuals[dup.person1_id]
                ind2 = self.individuals[dup.person2_id]
                
                writer.writerow([
                    dup.person1_id, dup.person1_name, dup.person2_id, dup.person2_name,
                    dup.similarity_score, dup.confidence_level, dup.recommended_action,
                    ind1.birth_date, ind1.death_date, ind2.birth_date, ind2.death_date,
                    '; '.join(dup.match_reasons)
                ])
    
    def _write_json_report(self, duplicates: List[DuplicateMatch], report_path: Path) -> None:
        """Write JSON report for programmatic processing"""
        data = {
            'analysis_summary': {
                'total_individuals': len(self.individuals),
                'total_duplicates_found': len(duplicates),
                'high_confidence_count': len([d for d in duplicates if d.similarity_score >= self.high_threshold]),
                'medium_confidence_count': len([d for d in duplicates if self.medium_threshold <= d.similarity_score < self.high_threshold])
            },
            'duplicates': []
        }
        
        for dup in duplicates:
            ind1 = self.individuals[dup.person1_id]
            ind2 = self.individuals[dup.person2_id]
            
            data['duplicates'].append({
                'person1': {
                    'id': dup.person1_id,
                    'name': dup.person1_name,
                    'birth_date': ind1.birth_date,
                    'birth_place': ind1.birth_place,
                    'death_date': ind1.death_date,
                    'sex': ind1.sex
                },
                'person2': {
                    'id': dup.person2_id,
                    'name': dup.person2_name,
                    'birth_date': ind2.birth_date,
                    'birth_place': ind2.birth_place,
                    'death_date': ind2.death_date,
                    'sex': ind2.sex
                },
                'similarity_score': dup.similarity_score,
                'confidence_level': dup.confidence_level,
                'match_reasons': dup.match_reasons,
                'recommendation': dup.recommended_action
            })
        
        with open(report_path, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
    
    def _write_rootsmagic_instructions(self, high_conf: List[DuplicateMatch], 
                                     medium_conf: List[DuplicateMatch], report_path: Path) -> None:
        """Write RootsMagic-specific merge instructions"""
        with open(report_path, 'w', encoding='utf-8') as f:
            f.write("ROOTSMAGIC 10 DUPLICATE MERGING GUIDE\n")
            f.write("=" * 50 + "\n\n")
            
            f.write("STEP-BY-STEP SAFE MERGING PROCESS:\n")
            f.write("1. Import final_with_media.ged into RootsMagic 10\n")
            f.write("2. File > Backup Database (create safety backup)\n")
            f.write("3. Tools > Merge > Find Duplicate People\n")
            f.write("4. Review each suggested merge carefully\n")
            f.write("5. Use merge preview to verify data combination\n")
            f.write("6. Merge only when you're 100% confident\n\n")
            
            f.write("PRIORITY MERGE LIST:\n")
            f.write("-" * 30 + "\n")
            
            if high_conf:
                f.write(f"\nHIGH PRIORITY ({len(high_conf)} matches):\n")
                for i, dup in enumerate(high_conf, 1):
                    f.write(f"{i}. Search for: {dup.person1_name}\n")
                    f.write(f"   Should match: {dup.person2_name}\n")
                    f.write(f"   IDs: {dup.person1_id} ↔ {dup.person2_id}\n")
                    f.write(f"   Score: {dup.similarity_score}%\n\n")
            
            if medium_conf:
                f.write(f"\nMEDIUM PRIORITY - MANUAL REVIEW ({len(medium_conf)} matches):\n")
                for i, dup in enumerate(medium_conf[:10], 1):  # Show top 10
                    f.write(f"{i}. {dup.person1_name} vs {dup.person2_name} ({dup.similarity_score}%)\n")
            
            f.write("\nREMEMBER:\n")
            f.write("- RootsMagic's merge function is much safer than automated scripts\n")
            f.write("- You can preview exactly what will be merged before confirming\n")
            f.write("- All data from both records will be preserved\n")
            f.write("- Family relationships will be maintained correctly\n")
            f.write("- You can undo merges if needed\n")

def main():
    parser = argparse.ArgumentParser(description="Safe duplicate person detection (analysis only)")
    parser.add_argument("input_gedcom", type=Path, help="Input GEDCOM file to analyze")
    parser.add_argument("--output-dir", type=Path, default=Path("duplicate_analysis"), help="Output directory for reports")
    parser.add_argument("--high-threshold", type=float, default=95.0, help="High confidence threshold")
    parser.add_argument("--medium-threshold", type=float, default=85.0, help="Medium confidence threshold")
    parser.add_argument("--verbose", "-v", action="store_true", help="Verbose output")
    
    args = parser.parse_args()
    
    if not args.input_gedcom.exists():
        print(f"❌ Error: Input GEDCOM file not found: {args.input_gedcom}")
        sys.exit(1)
    
    # Ensure output directory exists
    args.output_dir.mkdir(parents=True, exist_ok=True)
    
    try:
        print("🚀 Starting SAFE duplicate detection (analysis only)")
        print("⚠️  This script will NOT modify your GEDCOM file")
        
        # Initialize detector
        detector = SafeDuplicateDetector(args.high_threshold, args.medium_threshold)
        
        # Load and analyze
        detector.load_gedcom(args.input_gedcom)
        duplicates = detector.find_potential_duplicates()
        
        # Generate reports
        detector.generate_reports(duplicates, args.output_dir)
        
        high_conf = len([d for d in duplicates if d.similarity_score >= args.high_threshold])
        medium_conf = len(duplicates) - high_conf
        
        print("\n✅ Safe duplicate detection completed!")
        print(f"   📊 Total potential duplicates: {len(duplicates)}")
        print(f"   🎯 High confidence (≥{args.high_threshold}%): {high_conf}")
        print(f"   📋 Medium confidence ({args.medium_threshold}-{args.high_threshold-0.1}%): {medium_conf}")
        print(f"   📁 Reports saved to: {args.output_dir}")
        print("\n🔒 Your GEDCOM file was NOT modified - only analyzed")
        print("💡 Use the reports to guide manual merging in RootsMagic 10")
        
    except Exception as e:
        print(f"❌ Error during analysis: {e}")
        if args.verbose:
            import traceback
            traceback.print_exc()
        sys.exit(1)

if __name__ == "__main__":
    main()
