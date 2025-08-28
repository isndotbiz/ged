#!/usr/bin/env python3
"""
MyHeritage PDF Parser for Genealogy Issues
Extracts actionable issues from MyHeritage Tree Consistency Checker PDFs
"""

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Dict, List, Any, Optional
from dataclasses import dataclass, asdict
from datetime import datetime

try:
    import PyPDF2
    import pdfplumber
except ImportError:
    print("Error: Required PDF processing libraries not found.")
    print("Please install them with:")
    print("  pip install PyPDF2 pdfplumber")
    sys.exit(1)

@dataclass
class MyHeritageIssue:
    """Represents a single issue found by MyHeritage"""
    issue_type: str
    severity: str
    individual_id: Optional[str]
    individual_name: Optional[str]
    description: str
    suggested_fix: Optional[str]
    location: Optional[str]  # Page or section in PDF
    raw_text: str
    category: str  # dates, places, relationships, sources, etc.

class MyHeritagePDFParser:
    """Parser for MyHeritage Tree Consistency Checker PDF reports"""
    
    def __init__(self):
        self.issues: List[MyHeritageIssue] = []
        self.statistics = {
            'total_pages': 0,
            'total_issues': 0,
            'issues_by_category': {},
            'issues_by_severity': {},
            'parsing_errors': 0
        }
        
        # Patterns for identifying different types of issues
        self.patterns = {
            'date_inconsistency': [
                r'(?i)date.*(?:inconsistent|conflict|mismatch)',
                r'(?i)birth.*after.*death',
                r'(?i)death.*before.*birth',
                r'(?i)marriage.*(?:before|after).*birth',
                r'(?i)child.*born.*before.*parent',
            ],
            'missing_dates': [
                r'(?i)missing.*(?:birth|death|marriage).*date',
                r'(?i)no.*(?:birth|death|marriage).*date',
                r'(?i)date.*not.*provided',
            ],
            'place_issues': [
                r'(?i)place.*(?:inconsistent|unclear|unknown)',
                r'(?i)location.*(?:missing|incomplete)',
                r'(?i)geographic.*(?:error|inconsistency)',
            ],
            'relationship_problems': [
                r'(?i)relationship.*(?:inconsistent|unclear)',
                r'(?i)parent.*child.*(?:conflict|mismatch)',
                r'(?i)spouse.*(?:inconsistent|duplicate)',
            ],
            'duplicate_individuals': [
                r'(?i)possible.*duplicate',
                r'(?i)similar.*individual',
                r'(?i)may.*be.*same.*person',
            ],
            'source_issues': [
                r'(?i)source.*(?:missing|incomplete)',
                r'(?i)citation.*(?:needed|incomplete)',
                r'(?i)reference.*(?:unclear|missing)',
            ]
        }
        
        # Severity indicators
        self.severity_patterns = {
            'high': [r'(?i)error', r'(?i)critical', r'(?i)major'],
            'medium': [r'(?i)warning', r'(?i)caution', r'(?i)review'],
            'low': [r'(?i)suggestion', r'(?i)minor', r'(?i)consider']
        }

    def extract_text_from_pdf(self, pdf_path: Path) -> List[tuple]:
        """Extract text from PDF with page information"""
        pages_text = []
        
        try:
            # Try with pdfplumber first (better text extraction)
            with pdfplumber.open(pdf_path) as pdf:
                self.statistics['total_pages'] = len(pdf.pages)
                for page_num, page in enumerate(pdf.pages, 1):
                    try:
                        text = page.extract_text()
                        if text:
                            pages_text.append((page_num, text))
                        else:
                            print(f"⚠️  Warning: No text extracted from page {page_num}")
                    except Exception as e:
                        print(f"⚠️  Error extracting text from page {page_num}: {e}")
                        self.statistics['parsing_errors'] += 1
                        
        except Exception as e:
            print(f"⚠️  pdfplumber failed, trying PyPDF2: {e}")
            # Fallback to PyPDF2
            try:
                with open(pdf_path, 'rb') as file:
                    pdf_reader = PyPDF2.PdfReader(file)
                    self.statistics['total_pages'] = len(pdf_reader.pages)
                    for page_num, page in enumerate(pdf_reader.pages, 1):
                        try:
                            text = page.extract_text()
                            if text:
                                pages_text.append((page_num, text))
                        except Exception as page_error:
                            print(f"⚠️  Error extracting text from page {page_num}: {page_error}")
                            self.statistics['parsing_errors'] += 1
            except Exception as fallback_error:
                print(f"❌ Both PDF extraction methods failed: {fallback_error}")
                return []
        
        return pages_text

    def identify_issue_category(self, text: str) -> str:
        """Identify the category of an issue based on text patterns"""
        text_lower = text.lower()
        
        for category, patterns in self.patterns.items():
            for pattern in patterns:
                if re.search(pattern, text_lower):
                    return category.replace('_', ' ')
        
        return 'general'

    def identify_severity(self, text: str) -> str:
        """Identify the severity of an issue"""
        text_lower = text.lower()
        
        for severity, patterns in self.severity_patterns.items():
            for pattern in patterns:
                if re.search(pattern, text_lower):
                    return severity
        
        return 'medium'  # Default severity

    def extract_individual_info(self, text: str) -> tuple:
        """Extract individual ID and name from issue text"""
        # Look for patterns like "John Smith (I123)" or "@I123@"
        id_patterns = [
            r'@([I]\d+)@',
            r'\(([I]\d+)\)',
            r'ID:\s*([I]\d+)',
            r'Individual\s+([I]\d+)'
        ]
        
        name_patterns = [
            r'([A-Z][a-z]+(?:\s+[A-Z][a-z]+)*)\s*\(',
            r'"([^"]+)"',
            r'(?:Name|Individual):\s*([A-Z][a-z]+(?:\s+[A-Z][a-z]+)*)',
        ]
        
        individual_id = None
        individual_name = None
        
        # Extract ID
        for pattern in id_patterns:
            match = re.search(pattern, text)
            if match:
                individual_id = f"@{match.group(1)}@" if not match.group(1).startswith('@') else match.group(1)
                break
        
        # Extract name
        for pattern in name_patterns:
            match = re.search(pattern, text)
            if match:
                individual_name = match.group(1).strip()
                break
        
        return individual_id, individual_name

    def extract_suggested_fix(self, text: str) -> Optional[str]:
        """Extract suggested fix from issue text"""
        fix_patterns = [
            r'(?i)suggest(?:ion|ed)?:?\s*([^.]+)',
            r'(?i)recommend(?:ation|ed)?:?\s*([^.]+)',
            r'(?i)fix:?\s*([^.]+)',
            r'(?i)solution:?\s*([^.]+)',
            r'(?i)try:?\s*([^.]+)',
        ]
        
        for pattern in fix_patterns:
            match = re.search(pattern, text)
            if match:
                return match.group(1).strip()
        
        return None

    def parse_issue_block(self, text_block: str, page_num: int) -> List[MyHeritageIssue]:
        """Parse a block of text that contains issue information"""
        issues = []
        
        # Split into potential individual issues
        # Issues often start with bullets, numbers, or individual names
        issue_separators = [
            r'\n\s*[•·▪▫]\s*',
            r'\n\s*\d+\.\s*',
            r'\n\s*[A-Z][a-z]+\s+[A-Z][a-z]+.*?:',
            r'\n\s*@I\d+@',
        ]
        
        potential_issues = [text_block]  # Start with the whole block
        
        # Try to split by common separators
        for separator in issue_separators:
            new_issues = []
            for issue_text in potential_issues:
                parts = re.split(separator, issue_text)
                new_issues.extend([part.strip() for part in parts if part.strip()])
            if len(new_issues) > len(potential_issues):
                potential_issues = new_issues
                break
        
        # Process each potential issue
        for issue_text in potential_issues:
            if len(issue_text) < 20:  # Skip very short texts
                continue
            
            # Extract information
            category = self.identify_issue_category(issue_text)
            severity = self.identify_severity(issue_text)
            individual_id, individual_name = self.extract_individual_info(issue_text)
            suggested_fix = self.extract_suggested_fix(issue_text)
            
            # Create issue object
            issue = MyHeritageIssue(
                issue_type=f"myheritage_{category.replace(' ', '_')}",
                severity=severity,
                individual_id=individual_id,
                individual_name=individual_name,
                description=issue_text[:200] + "..." if len(issue_text) > 200 else issue_text,
                suggested_fix=suggested_fix,
                location=f"Page {page_num}",
                raw_text=issue_text,
                category=category
            )
            
            issues.append(issue)
        
        return issues

    def parse_pdf(self, pdf_path: Path) -> None:
        """Parse the entire PDF and extract all issues"""
        print(f"📄 Parsing MyHeritage PDF: {pdf_path}")
        
        if not pdf_path.exists():
            raise FileNotFoundError(f"PDF file not found: {pdf_path}")
        
        # Extract text from all pages
        pages_text = self.extract_text_from_pdf(pdf_path)
        
        if not pages_text:
            raise ValueError("No text could be extracted from the PDF")
        
        print(f"📖 Extracted text from {len(pages_text)} pages")
        
        # Process each page
        for page_num, page_text in pages_text:
            # Look for sections that contain issues
            # Common section headers in MyHeritage reports
            issue_sections = [
                'inconsistencies',
                'errors',
                'warnings',
                'suggestions',
                'problems',
                'issues',
                'conflicts',
                'review',
                'missing information',
                'data quality'
            ]
            
            # Split page into sections
            sections = []
            current_section = page_text
            
            # Try to identify issue sections
            for section_name in issue_sections:
                pattern = rf'(?i){section_name}[:\s]*\n(.*?)(?=\n[A-Z][^:]*:|$)'
                matches = re.finditer(pattern, page_text, re.DOTALL)
                for match in matches:
                    sections.append(match.group(1))
            
            # If no sections found, treat the whole page as potential issues
            if not sections:
                sections = [page_text]
            
            # Parse each section
            for section_text in sections:
                if len(section_text.strip()) > 50:  # Only process substantial text
                    page_issues = self.parse_issue_block(section_text, page_num)
                    self.issues.extend(page_issues)
        
        # Update statistics
        self.statistics['total_issues'] = len(self.issues)
        
        # Count by category and severity
        for issue in self.issues:
            category = issue.category
            severity = issue.severity
            
            if category not in self.statistics['issues_by_category']:
                self.statistics['issues_by_category'][category] = 0
            self.statistics['issues_by_category'][category] += 1
            
            if severity not in self.statistics['issues_by_severity']:
                self.statistics['issues_by_severity'][severity] = 0
            self.statistics['issues_by_severity'][severity] += 1
        
        print(f"🔍 Found {len(self.issues)} potential issues")

    def export_to_json(self, output_path: Path) -> None:
        """Export parsed issues to JSON format"""
        output_data = {
            'metadata': {
                'parser_version': '1.0',
                'parsed_at': datetime.now().isoformat(),
                'source': 'MyHeritage Tree Consistency Checker'
            },
            'statistics': self.statistics,
            'issues': [asdict(issue) for issue in self.issues]
        }
        
        with open(output_path, 'w', encoding='utf-8') as f:
            json.dump(output_data, f, indent=2, ensure_ascii=False)
        
        print(f"📊 Exported {len(self.issues)} issues to {output_path}")

    def print_summary(self) -> None:
        """Print a summary of parsed issues"""
        print("\n" + "="*60)
        print("📊 MYHERITAGE PDF PARSING SUMMARY")
        print("="*60)
        
        print(f"📄 Pages processed: {self.statistics['total_pages']}")
        print(f"🔍 Total issues found: {self.statistics['total_issues']}")
        print(f"⚠️ Parsing errors: {self.statistics['parsing_errors']}")
        
        if self.statistics['issues_by_category']:
            print("\n📂 Issues by category:")
            for category, count in sorted(self.statistics['issues_by_category'].items()):
                print(f"   • {category}: {count}")
        
        if self.statistics['issues_by_severity']:
            print("\n🚨 Issues by severity:")
            for severity, count in sorted(self.statistics['issues_by_severity'].items()):
                print(f"   • {severity}: {count}")
        
        # Show some example issues
        if self.issues:
            print("\n📋 Sample issues:")
            for i, issue in enumerate(self.issues[:5]):
                print(f"\n{i+1}. {issue.category.upper()} ({issue.severity})")
                if issue.individual_name:
                    print(f"   👤 Individual: {issue.individual_name}")
                if issue.individual_id:
                    print(f"   🆔 ID: {issue.individual_id}")
                print(f"   📝 Description: {issue.description}")
                if issue.suggested_fix:
                    print(f"   🔧 Suggested fix: {issue.suggested_fix}")

def main():
    parser = argparse.ArgumentParser(
        description='Parse MyHeritage Tree Consistency Checker PDF reports',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  %(prog)s report.pdf --output issues.json
  %(prog)s "Tree Consistency Checker.pdf" -o myheritage_issues.json --verbose
        """
    )
    
    parser.add_argument('pdf_file', help='MyHeritage PDF report file')
    parser.add_argument('-o', '--output', help='Output JSON file for parsed issues')
    parser.add_argument('-v', '--verbose', action='store_true', help='Verbose output')
    
    args = parser.parse_args()
    
    try:
        pdf_path = Path(args.pdf_file)
        
        # Initialize parser
        pdf_parser = MyHeritagePDFParser()
        
        # Parse the PDF
        pdf_parser.parse_pdf(pdf_path)
        
        # Show summary
        pdf_parser.print_summary()
        
        # Export to JSON if requested
        if args.output:
            output_path = Path(args.output)
            pdf_parser.export_to_json(output_path)
        else:
            # Default output file
            output_path = pdf_path.parent / f"{pdf_path.stem}_issues.json"
            pdf_parser.export_to_json(output_path)
        
        print(f"\n✅ Parsing complete! Issues saved to {output_path}")
        
    except FileNotFoundError as e:
        print(f"❌ Error: {e}")
        sys.exit(1)
    except Exception as e:
        print(f"❌ Unexpected error: {e}")
        if args.verbose:
            import traceback
            traceback.print_exc()
        sys.exit(1)

if __name__ == '__main__':
    main()
