#!/usr/bin/env python3
"""
Interactive command-line tool for manually reviewing genealogical consistency issues.
Allows step-by-step review and resolution of MyHeritage Tree Consistency Checker issues.
"""

from __future__ import annotations
import json
import re
from pathlib import Path
from typing import Dict, List, Optional, Any
from dataclasses import dataclass, asdict
import click
from .checker import build_index, Person, Family

@dataclass
class ManualReviewIssue:
    """Represents a single issue for manual review"""
    category: str
    subject: str
    detail: str
    suggestion: str
    status: str = "pending"  # pending, reviewed, fixed, ignored
    notes: str = ""
    xref: Optional[str] = None

class ManualReviewSession:
    """Manages an interactive manual review session"""
    
    def __init__(self, gedcom_path: Path, issues_file: Optional[Path] = None):
        self.gedcom_path = gedcom_path
        self.issues_file = issues_file or gedcom_path.parent / "manual_review_issues.json"
        self.people, self.families = build_index(gedcom_path)
        self.issues: List[ManualReviewIssue] = []
        self.current_index = 0
        
        # Load existing issues or generate from MyHeritage data
        self.load_or_generate_issues()
    
    def load_or_generate_issues(self):
        """Load existing review session or generate from available data"""
        if self.issues_file.exists():
            self.load_issues()
        else:
            self.generate_issues_from_data()
            self.save_issues()
    
    def load_issues(self):
        """Load issues from JSON file"""
        try:
            with open(self.issues_file, 'r') as f:
                data = json.load(f)
                self.issues = [ManualReviewIssue(**item) for item in data.get('issues', [])]
                self.current_index = data.get('current_index', 0)
            click.echo(f"✅ Loaded {len(self.issues)} issues from {self.issues_file}")
        except Exception as e:
            click.echo(f"⚠️  Error loading issues: {e}")
            self.generate_issues_from_data()
    
    def save_issues(self):
        """Save current issues state to JSON file"""
        data = {
            'issues': [asdict(issue) for issue in self.issues],
            'current_index': self.current_index,
            'gedcom_file': str(self.gedcom_path),
            'total_issues': len(self.issues),
            'completed': sum(1 for i in self.issues if i.status != 'pending')
        }
        with open(self.issues_file, 'w') as f:
            json.dump(data, f, indent=2)
    
    def generate_issues_from_data(self):
        """Generate issues from MyHeritage data and current GEDCOM analysis"""
        click.echo("🔍 Generating issues for manual review...")
        
        # Try multiple sources for MyHeritage issues
        sources = [
            self.gedcom_path.parent / "data/processing/reports/myheritage_issues.json",
            self.gedcom_path.parent / "data/processing/reports/MANUAL_REVIEW_REQUIRED.md",
            self.gedcom_path.parent / "data/merged/myheritage_issues.txt"
        ]
        
        issues_loaded = False
        for source_file in sources:
            if source_file.exists():
                if source_file.suffix == '.json':
                    self.parse_myheritage_json(source_file)
                    issues_loaded = True
                    break
                elif source_file.suffix == '.md':
                    self.parse_manual_review_md(source_file)
                    issues_loaded = True
                    break
                elif source_file.suffix == '.txt':
                    self.parse_myheritage_issues(source_file)
                    issues_loaded = True
                    break
        
        if not issues_loaded:
            click.echo("⚠️  No MyHeritage issues found in expected locations")
            
        # Add issues from current GEDCOM analysis
        self.add_structural_issues()
        
        click.echo(f"📋 Generated {len(self.issues)} issues for review")
    
    def parse_myheritage_issues(self, file_path: Path):
        """Parse MyHeritage issues from text file"""
        content = file_path.read_text()
        
        # Parse different issue categories
        patterns = {
            "Multiple marriages of same couple": r"(.+) and his wife (.+) have (\d+) marriage facts",
            "Suffix in first name": r"First name of (.+) ends with the suffix '(.+)'",
            "Suffix in last name": r"Last name of (.+) ends with the suffix '(.+)'",
            "Multiple birth facts of same person": r"(.+) has (\d+) birth facts",
            "Multiple death facts of same person": r"(.+) has (\d+) death facts",
            "Disconnected from tree": r"(.+) has no relatives in the family tree",
            "Siblings with same first name": r"(.+) and his brother (.+) have the same first name",
            "Inconsistent last name spelling": r"(.+) has the last name '(.+)', which appears once.+similar spelling '(.+)' appears (\d+) times",
            "Inconsistent place name spelling": r"(.+) has a Birth place '(.+)', which appears once.+similar spelling '(.+)' appears"
        }
        
        for category, pattern in patterns.items():
            matches = re.findall(pattern, content)
            for match in matches:
                if category == "Multiple marriages of same couple":
                    subject = f"{match[0]} & {match[1]}"
                    detail = f"Has {match[2]} marriage facts"
                    suggestion = "Remove redundant marriage facts, keeping the most complete one"
                elif "Suffix" in category:
                    subject = match[0]
                    detail = f"Has suffix '{match[1]}' in wrong field"
                    suggestion = f"Move '{match[1]}' to separate suffix field"
                elif "Multiple" in category and "facts" in category:
                    subject = match[0]
                    detail = f"Has {match[1]} facts of same type"
                    suggestion = "Remove redundant facts, keeping the most complete one"
                elif category == "Disconnected from tree":
                    subject = match[0]
                    detail = "No family relationships"
                    suggestion = "Connect to parent, spouse, or child"
                elif category == "Siblings with same first name":
                    subject = f"{match[0]} & {match[1]}"
                    detail = "Same first name"
                    suggestion = "Verify if same person or intentional reuse"
                elif "Inconsistent" in category:
                    subject = match[0]
                    if len(match) >= 3:
                        detail = f"Uses '{match[1]}' but '{match[2]}' is more common"
                        suggestion = f"Consider changing to '{match[2]}'"
                    else:
                        detail = "Inconsistent spelling"
                        suggestion = "Standardize spelling"
                
                # Find xref if possible
                xref = self.find_person_xref(subject.split(' & ')[0] if ' & ' in subject else subject)
                
                self.issues.append(ManualReviewIssue(
                    category=category,
                    subject=subject,
                    detail=detail,
                    suggestion=suggestion,
                    xref=xref
                ))
    
    def find_person_xref(self, name: str) -> Optional[str]:
        """Find XREF for a person by name"""
        # Clean name for matching
        clean_name = re.sub(r'\s+(Th\s+\w+|My\s+\w+)', '', name).strip()
        
        for xref, person in self.people.items():
            for name_record in person.names:
                full_name = f"{name_record.get('GIVN', '')} {name_record.get('SURN', '')}".strip()
                if clean_name.lower() in full_name.lower() or full_name.lower() in clean_name.lower():
                    return xref
        return None
    
    def parse_myheritage_json(self, file_path: Path):
        """Parse MyHeritage issues from JSON file"""
        try:
            with open(file_path) as f:
                data = json.load(f)
            
            for issue_data in data.get('issues', []):
                subject = issue_data.get('individual_name', 'Unknown')
                category = issue_data.get('issue_type', '').replace('myheritage_', '').replace('_', ' ').title()
                detail = issue_data.get('description', '')[:200] + '...' if len(issue_data.get('description', '')) > 200 else issue_data.get('description', '')
                suggestion = issue_data.get('suggested_fix', 'Manual review required')
                
                # Extract more specific details from raw_text if available
                raw_text = issue_data.get('raw_text', '')
                if raw_text:
                    detail = self.extract_detail_from_raw_text(raw_text, subject)
                
                xref = self.find_person_xref(subject)
                
                self.issues.append(ManualReviewIssue(
                    category=category,
                    subject=subject,
                    detail=detail,
                    suggestion=suggestion,
                    xref=xref
                ))
                
        except Exception as e:
            click.echo(f"⚠️  Error parsing JSON file: {e}")
    
    def parse_manual_review_md(self, file_path: Path):
        """Parse issues from MANUAL_REVIEW_REQUIRED.md file"""
        try:
            content = file_path.read_text()
            
            # Parse high priority date logic errors
            self.parse_md_section(content, "Child Born After Parent Death", "Date Logic Error", 
                                r"(.+) was born on (\d+), after the death of (?:his|her) (?:father|mother) (.+) on (\d+)")
            
            self.parse_md_section(content, "Facts Occurring After Death", "Date Logic Error",
                                r"Date of (.+) of (.+) \((.+)\) occurred after (?:his|her) death date \((.+)\)")
            
            self.parse_md_section(content, "Facts Occurring Before Birth", "Date Logic Error",
                                r"Date of (.+) of (.+) \((.+)\) occurred before (?:his|her) birth date \((.+)\)")
            
            # Parse age-related issues
            self.parse_md_section(content, "Parents Too Young", "Age Issue",
                                r"(.+) \(born (.+)\) and (?:his|her) (?:father|mother) (.+) are (\d+) years apart")
            
            self.parse_md_section(content, "Extreme Age at Death", "Age Issue",
                                r"(.+) \(born (.+), died (.+)\) was rather old at death \(at least (\d+) years old\)")
            
            # Add generic issues for categories mentioned but not parsed
            categories = [
                "Multiple Marriages of Same Couple",
                "Orphaned Individuals", 
                "Sibling Issues"
            ]
            
            for category in categories:
                if category.lower() in content.lower():
                    self.issues.append(ManualReviewIssue(
                        category=category,
                        subject="Multiple instances",
                        detail=f"See {file_path.name} for detailed list",
                        suggestion="Review document for specific cases"
                    ))
                    
        except Exception as e:
            click.echo(f"⚠️  Error parsing markdown file: {e}")
    
    def parse_md_section(self, content: str, section_name: str, category: str, pattern: str):
        """Parse a specific section of the markdown file"""
        try:
            matches = re.findall(pattern, content, re.IGNORECASE)
            for match in matches:
                if len(match) >= 2:
                    subject = match[1] if len(match) > 1 else match[0]
                    detail = f"{section_name}: {' '.join(match)}"
                    suggestion = f"Verify dates and resolve {section_name.lower()}"
                    
                    xref = self.find_person_xref(subject)
                    
                    self.issues.append(ManualReviewIssue(
                        category=category,
                        subject=subject,
                        detail=detail,
                        suggestion=suggestion,
                        xref=xref
                    ))
        except Exception as e:
            click.echo(f"⚠️  Error parsing section {section_name}: {e}")
    
    def extract_detail_from_raw_text(self, raw_text: str, subject: str) -> str:
        """Extract specific detail about the subject from raw MyHeritage text"""
        lines = raw_text.split('\n')
        for i, line in enumerate(lines):
            if subject.lower() in line.lower():
                # Return this line and possibly the next line for context
                detail = line.strip()
                if i + 1 < len(lines) and lines[i + 1].strip().startswith('Tip:'):
                    detail += f" {lines[i + 1].strip()}"
                return detail
        return raw_text[:200] + '...' if len(raw_text) > 200 else raw_text
    
    def add_structural_issues(self):
        """Add structural issues found in current GEDCOM"""
        # This would integrate with your existing checker.py
        # For now, just placeholder to show the pattern
        pass
    
    def show_progress(self):
        """Show current progress"""
        completed = sum(1 for i in self.issues if i.status != 'pending')
        if len(self.issues) == 0:
            click.echo("\n📊 No issues loaded for review")
            return
        click.echo(f"\n📊 Progress: {completed}/{len(self.issues)} issues completed ({completed/len(self.issues)*100:.1f}%)")
        
        # Show status breakdown
        status_counts = {}
        for issue in self.issues:
            status_counts[issue.status] = status_counts.get(issue.status, 0) + 1
        
        for status, count in status_counts.items():
            click.echo(f"  {status}: {count}")
    
    def show_current_issue(self):
        """Display current issue details"""
        if self.current_index >= len(self.issues):
            click.echo("🎉 All issues completed!")
            return False
            
        issue = self.issues[self.current_index]
        
        click.echo(f"\n" + "="*60)
        click.echo(f"Issue {self.current_index + 1} of {len(self.issues)}")
        click.echo(f"Category: {issue.category}")
        click.echo(f"Subject: {issue.subject}")
        click.echo(f"Detail: {issue.detail}")
        click.echo(f"Suggestion: {issue.suggestion}")
        if issue.xref:
            click.echo(f"XREF: {issue.xref}")
        if issue.status != 'pending':
            click.echo(f"Status: {issue.status}")
        if issue.notes:
            click.echo(f"Notes: {issue.notes}")
        
        # Show related GEDCOM data if xref available
        if issue.xref and issue.xref in self.people:
            self.show_person_details(issue.xref)
            
        return True
    
    def show_person_details(self, xref: str):
        """Show detailed person information from GEDCOM"""
        person = self.people.get(xref)
        if not person:
            return
            
        click.echo(f"\n📋 GEDCOM Details for {xref}:")
        
        # Names
        for i, name in enumerate(person.names):
            click.echo(f"  Name {i+1}: {name.get('GIVN', '')} {name.get('SURN', '')}")
            if name.get('NSFX'):
                click.echo(f"    Suffix: {name['NSFX']}")
        
        # Events
        for event_type, events in person.events.items():
            for i, event in enumerate(events):
                click.echo(f"  {event_type} {i+1}: {event.get('DATE', '')} {event.get('PLAC', '')}")
        
        # Family connections
        if person.famc:
            click.echo(f"  Child in families: {', '.join(person.famc)}")
        if person.fams:
            click.echo(f"  Spouse in families: {', '.join(person.fams)}")
    
    def handle_issue_action(self, action: str):
        """Handle user action on current issue"""
        issue = self.issues[self.current_index]
        
        if action == 'f':  # Fixed
            issue.status = 'fixed'
            notes = click.prompt("📝 Notes about the fix (optional)", default="", show_default=False)
            if notes:
                issue.notes = notes
            click.echo("✅ Marked as fixed")
            
        elif action == 'i':  # Ignore
            issue.status = 'ignored'
            reason = click.prompt("❓ Reason for ignoring (optional)", default="", show_default=False)
            if reason:
                issue.notes = f"Ignored: {reason}"
            click.echo("⏭️  Marked as ignored")
            
        elif action == 'n':  # Add notes
            notes = click.prompt("📝 Add notes", default=issue.notes)
            issue.notes = notes
            click.echo("📝 Notes updated")
            return  # Don't advance
            
        elif action == 'r':  # Mark as reviewed (for later action)
            issue.status = 'reviewed'
            notes = click.prompt("📝 Review notes (optional)", default="", show_default=False)
            if notes:
                issue.notes = notes
            click.echo("👁️  Marked as reviewed")
            
        elif action == 'g':  # Generate GEDCOM edit command
            self.generate_edit_command(issue)
            return  # Don't advance
            
        # Move to next issue for f, i, r actions
        self.current_index += 1
        self.save_issues()
    
    def generate_edit_command(self, issue: ManualReviewIssue):
        """Generate a gedfix command to address the issue"""
        if not issue.xref:
            click.echo("⚠️  No XREF available - cannot generate edit command")
            return
            
        click.echo(f"\n💡 Suggested gedfix commands:")
        
        if "Multiple" in issue.category and "facts" in issue.category:
            click.echo(f"  gedfix fix {self.gedcom_path} --approve-dup-facts --target-person {issue.xref}")
        elif "Suffix" in issue.category:
            click.echo(f"  gedfix fix {self.gedcom_path} --approve-suffix --target-person {issue.xref}")
        elif "Inconsistent" in issue.category:
            click.echo(f"  # Manual editing required for: {issue.detail}")
            click.echo(f"  # Search and replace in GEDCOM file")
        else:
            click.echo(f"  # Manual review required for: {issue.category}")
    
    def run_interactive_session(self):
        """Run the main interactive review session"""
        click.echo("🔍 Starting Manual Review Session")
        click.echo("Commands: (f)ixed, (i)gnore, (r)eviewed, (n)otes, (g)enerate-fix, (q)uit, (s)tatus")
        
        while self.current_index < len(self.issues):
            if not self.show_current_issue():
                break
                
            action = click.prompt(
                "\nAction",
                type=click.Choice(['f', 'i', 'r', 'n', 'g', 's', 'q'], case_sensitive=False),
                default='f'
            ).lower()
            
            if action == 'q':
                break
            elif action == 's':
                self.show_progress()
                continue
            else:
                self.handle_issue_action(action)
        
        self.show_progress()
        click.echo(f"💾 Session saved to {self.issues_file}")

@click.command()
@click.argument('gedcom_file', type=click.Path(exists=True, path_type=Path))
@click.option('--issues-file', type=click.Path(path_type=Path), help='JSON file to store review session')
@click.option('--start-index', type=int, default=0, help='Issue index to start from')
def review(gedcom_file: Path, issues_file: Optional[Path], start_index: int):
    """
    Interactive manual review of genealogical consistency issues.
    
    GEDCOM_FILE: Path to the GEDCOM file to review
    """
    
    session = ManualReviewSession(gedcom_file, issues_file)
    session.current_index = start_index
    session.run_interactive_session()

if __name__ == '__main__':
    review()
