#!/usr/bin/env python3
"""
MyHeritage Issue Analyzer
Analyzes the extracted MyHeritage issues and creates actionable reports
"""

import json
import re
from pathlib import Path
from datetime import datetime
from typing import List, Dict, Any

class MyHeritageAnalyzer:
    """Analyzes MyHeritage issues and creates actionable reports"""
    
    def __init__(self):
        self.issues = []
        self.findings = {
            'summary': {},
            'actionable_issues': [],
            'review_required': [],
            'statistics': {}
        }
    
    def extract_issues_from_text(self, raw_text: str) -> List[Dict[str, Any]]:
        """Extract specific issues from the raw MyHeritage text"""
        issues = []
        
        # Extract the count of total consistency issues
        total_match = re.search(r'(\d+)\s+consistency\s+issues\s+found', raw_text, re.IGNORECASE)
        if total_match:
            issues.append({
                'type': 'summary',
                'count': int(total_match.group(1)),
                'description': f"MyHeritage found {total_match.group(1)} total consistency issues"
            })
        
        # Extract specific issue types with counts
        issue_patterns = [
            (r'Child born after death of parent \((\d+)\)', 'child_after_parent_death', 'High Priority - Date Logic Error'),
            (r'Died too old \((\d+)\)', 'extreme_age_at_death', 'Medium Priority - Verify Dates'),
            (r'Parent too young when having a child \((\d+)\)', 'parent_too_young', 'Medium Priority - Verify Dates'),
            (r'Parent too old when having a child \((\d+)\)', 'parent_too_old', 'Medium Priority - Verify Dates'),
            (r'Fact occurring after death \((\d+)\)', 'fact_after_death', 'High Priority - Date Logic Error'),
            (r'Fact occurring before birth \((\d+)\)', 'fact_before_birth', 'High Priority - Date Logic Error'),
            (r'Siblings with close age \((\d+)\)', 'siblings_close_age', 'Low Priority - Review for Twins'),
            (r'Large spouse age difference \((\d+)\)', 'large_age_gap', 'Low Priority - Verify if Accurate'),
            (r'Married too young \((\d+)\)', 'married_too_young', 'Medium Priority - Verify Dates'),
            (r'Died too young to be a spouse \((\d+)\)', 'died_too_young_spouse', 'High Priority - Date Logic Error'),
            (r'Multiple marriages of same couple \((\d+)\)', 'duplicate_marriages', 'Medium Priority - Remove Duplicates'),
            (r'Suffix in first name \((\d+)\)', 'suffix_in_first_name', 'High Priority - Easy Fix'),
            (r'Suffix in last name \((\d+)\)', 'suffix_in_last_name', 'High Priority - Easy Fix'),
            (r'Multiple birth facts of same person \((\d+)\)', 'duplicate_birth_facts', 'High Priority - Remove Duplicates'),
            (r'Multiple death facts of same person \((\d+)\)', 'duplicate_death_facts', 'High Priority - Remove Duplicates'),
            (r'Disconnected from tree \((\d+)\)', 'orphaned_individuals', 'Medium Priority - Review Connections'),
            (r'Siblings with same first name \((\d+)\)', 'siblings_same_name', 'Low Priority - Verify Distinction'),
            (r'Inconsistent last name spelling \((\d+)\)', 'inconsistent_surnames', 'High Priority - Standardize Names'),
            (r'Inconsistent place name spelling \((\d+)\)', 'inconsistent_places', 'High Priority - Standardize Places'),
        ]
        
        for pattern, issue_type, priority in issue_patterns:
            matches = re.finditer(pattern, raw_text, re.IGNORECASE)
            for match in matches:
                count = int(match.group(1))
                issues.append({
                    'type': issue_type,
                    'count': count,
                    'priority': priority,
                    'description': f"Found {count} instances of {issue_type.replace('_', ' ')}"
                })
        
        return issues
    
    def analyze_issues(self, issues_json_path: Path) -> None:
        """Analyze the MyHeritage issues JSON file"""
        print(f"📊 Analyzing MyHeritage issues from {issues_json_path}")
        
        # Load the issues
        with open(issues_json_path, 'r', encoding='utf-8') as f:
            data = json.load(f)
        
        # Process each issue's raw text
        all_extracted_issues = []
        for issue in data['issues']:
            raw_text = issue.get('raw_text', '')
            extracted = self.extract_issues_from_text(raw_text)
            all_extracted_issues.extend(extracted)
        
        # Combine and deduplicate issues
        issue_counts = {}
        for issue in all_extracted_issues:
            issue_type = issue['type']
            if issue_type in issue_counts:
                issue_counts[issue_type]['count'] += issue.get('count', 0)
            else:
                issue_counts[issue_type] = issue
        
        # Categorize by priority
        high_priority = []
        medium_priority = []
        low_priority = []
        
        for issue_type, issue_data in issue_counts.items():
            if issue_type == 'summary':
                self.findings['summary'] = issue_data
                continue
                
            priority = issue_data.get('priority', 'Medium Priority')
            if priority.startswith('High'):
                high_priority.append(issue_data)
            elif priority.startswith('Medium'):
                medium_priority.append(issue_data)
            else:
                low_priority.append(issue_data)
        
        # Store findings
        self.findings['actionable_issues'] = {
            'high_priority': high_priority,
            'medium_priority': medium_priority,
            'low_priority': low_priority
        }
        
        self.findings['statistics'] = {
            'total_issue_types': len(issue_counts) - 1,  # Exclude summary
            'high_priority_count': len(high_priority),
            'medium_priority_count': len(medium_priority),
            'low_priority_count': len(low_priority)
        }
    
    def generate_actionable_report(self, output_path: Path) -> None:
        """Generate an actionable report for the user"""
        
        # Create markdown report
        md_path = output_path.with_suffix('.md')
        with open(md_path, 'w', encoding='utf-8') as f:
            f.write("# MyHeritage Issues - Actionable Report\\n\\n")
            f.write(f"Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\\n\\n")
            
            # Summary
            if 'count' in self.findings['summary']:
                total_issues = self.findings['summary']['count']
                f.write(f"## 📊 Summary\\n\\n")
                f.write(f"MyHeritage identified **{total_issues} total consistency issues** in your family tree.\\n\\n")
            
            # Statistics
            stats = self.findings['statistics']
            f.write(f"### Issue Categories\\n")
            f.write(f"- **High Priority (Easy Fixes)**: {stats['high_priority_count']} types\\n")
            f.write(f"- **Medium Priority (Needs Review)**: {stats['medium_priority_count']} types\\n")
            f.write(f"- **Low Priority (Optional)**: {stats['low_priority_count']} types\\n\\n")
            
            # High Priority Issues (Quick Wins)
            high_priority = self.findings['actionable_issues']['high_priority']
            if high_priority:
                f.write("## 🚀 High Priority - Quick Wins\\n\\n")
                f.write("These issues can be fixed quickly and will significantly improve your data quality:\\n\\n")
                
                for issue in high_priority:
                    count = issue.get('count', 0)
                    issue_type = issue['type'].replace('_', ' ').title()
                    f.write(f"### {issue_type} ({count} issues)\\n")
                    
                    # Add specific fix instructions
                    fix_instructions = self._get_fix_instructions(issue['type'])
                    f.write(f"{fix_instructions}\\n\\n")
            
            # Medium Priority Issues
            medium_priority = self.findings['actionable_issues']['medium_priority']
            if medium_priority:
                f.write("## ⚖️ Medium Priority - Review Required\\n\\n")
                f.write("These issues require manual review to determine the best course of action:\\n\\n")
                
                for issue in medium_priority:
                    count = issue.get('count', 0)
                    issue_type = issue['type'].replace('_', ' ').title()
                    f.write(f"### {issue_type} ({count} issues)\\n")
                    
                    # Add review guidance
                    review_guidance = self._get_review_guidance(issue['type'])
                    f.write(f"{review_guidance}\\n\\n")
            
            # Low Priority Issues
            low_priority = self.findings['actionable_issues']['low_priority']
            if low_priority:
                f.write("## 📋 Low Priority - Optional Improvements\\n\\n")
                f.write("These issues are informational and may not require immediate action:\\n\\n")
                
                for issue in low_priority:
                    count = issue.get('count', 0)
                    issue_type = issue['type'].replace('_', ' ').title()
                    f.write(f"- **{issue_type}**: {count} instances\\n")
                
                f.write("\\n")
            
            # Next Steps
            f.write("## 🎯 Recommended Action Plan\\n\\n")
            f.write("1. **Start with High Priority issues** - These provide the biggest improvement for least effort\\n")
            f.write("2. **Review Medium Priority issues** - Focus on date logic errors first\\n")
            f.write("3. **Address Low Priority issues** as time permits\\n\\n")
            f.write("4. **Use your comprehensive GEDCOM processor** to apply standardizations\\n")
            f.write("5. **Re-import into RootsMagic** to verify improvements\\n\\n")
        
        # Create JSON report for programmatic use
        json_path = output_path.with_suffix('.json')
        with open(json_path, 'w', encoding='utf-8') as f:
            json.dump(self.findings, f, indent=2)
        
        print(f"📖 Actionable report created: {md_path}")
        print(f"📊 JSON data created: {json_path}")
    
    def _get_fix_instructions(self, issue_type: str) -> str:
        """Get specific fix instructions for each issue type"""
        instructions = {
            'suffix_in_first_name': "**Fix**: Move suffixes like 'Jr.', 'Sr.', 'II', 'III' from first name to separate suffix field.\\n**Impact**: Improves name standardization across genealogy platforms.",
            'suffix_in_last_name': "**Fix**: Move suffixes like 'Jr.', 'Sr.' from last name to separate suffix field.\\n**Impact**: Improves name standardization and prevents sorting issues.",
            'duplicate_birth_facts': "**Fix**: Remove duplicate birth records, keeping the most complete one with date and place.\\n**Impact**: Eliminates confusion and improves data quality.",
            'duplicate_death_facts': "**Fix**: Remove duplicate death records, keeping the most complete one with date and place.\\n**Impact**: Eliminates confusion and improves data quality.",
            'inconsistent_surnames': "**Fix**: Standardize surname spellings to the most common variant in your tree.\\n**Impact**: Improves family grouping and search functionality.",
            'inconsistent_places': "**Fix**: Standardize place name spellings to consistent format.\\n**Impact**: Better geographic organization and mapping.",
            'fact_after_death': "**Fix**: Review and correct dates where events occurred after person's death.\\n**Impact**: Eliminates impossible date sequences.",
            'fact_before_birth': "**Fix**: Review and correct dates where events occurred before person's birth.\\n**Impact**: Eliminates impossible date sequences.",
            'child_after_parent_death': "**Fix**: Review birth/death dates where child was born after parent died.\\n**Impact**: Corrects genealogical impossibilities."
        }
        return instructions.get(issue_type, "**Action**: Review and address as appropriate for your research.")
    
    def _get_review_guidance(self, issue_type: str) -> str:
        """Get review guidance for medium priority issues"""
        guidance = {
            'extreme_age_at_death': "**Review**: People living over 100+ years. Verify birth/death dates are accurate.\\n**Consider**: These may be legitimate centenarians or date entry errors.",
            'parent_too_young': "**Review**: Parents having children at very young ages (under 15).\\n**Consider**: Verify dates are accurate, may indicate adoption or guardianship.",
            'parent_too_old': "**Review**: Parents having children at advanced ages (over 55+ for mothers).\\n**Consider**: May be legitimate late-life children or indicate step-relationships.",
            'married_too_young': "**Review**: People married at very young ages.\\n**Consider**: Historical context - marriage ages varied by era and location.",
            'duplicate_marriages': "**Review**: Same couple with multiple marriage records.\\n**Action**: Remove duplicates, keeping the most complete record.",
            'orphaned_individuals': "**Review**: People with no family connections in your tree.\\n**Action**: Research to connect them or consider if they belong in your tree."
        }
        return guidance.get(issue_type, "**Action**: Review the specific instances and use your genealogical judgment.")
    
    def print_summary(self) -> None:
        """Print a summary of the analysis"""
        print("\\n" + "="*60)
        print("📊 MYHERITAGE ANALYSIS SUMMARY")  
        print("="*60)
        
        if 'count' in self.findings['summary']:
            total_issues = self.findings['summary']['count']
            print(f"🔍 Total issues found by MyHeritage: {total_issues}")
        
        stats = self.findings['statistics']
        print(f"\\n📂 Issue categories identified:")
        print(f"   • High priority (quick fixes): {stats['high_priority_count']}")
        print(f"   • Medium priority (needs review): {stats['medium_priority_count']}")
        print(f"   • Low priority (optional): {stats['low_priority_count']}")
        
        # Show top high priority issues
        high_priority = self.findings['actionable_issues']['high_priority']
        if high_priority:
            print("\\n🚀 Top high-priority fixes:")
            for issue in high_priority[:3]:
                count = issue.get('count', 0)
                issue_name = issue['type'].replace('_', ' ').title()
                print(f"   • {issue_name}: {count} instances")

def main():
    import argparse
    
    parser = argparse.ArgumentParser(description='Analyze MyHeritage issues and create actionable reports')
    parser.add_argument('issues_json', help='MyHeritage issues JSON file')
    parser.add_argument('-o', '--output', help='Output path for reports (without extension)')
    
    args = parser.parse_args()
    
    issues_path = Path(args.issues_json)
    output_path = Path(args.output) if args.output else issues_path.parent / 'myheritage_analysis'
    
    # Analyze issues
    analyzer = MyHeritageAnalyzer()
    analyzer.analyze_issues(issues_path)
    
    # Show summary
    analyzer.print_summary()
    
    # Generate reports
    analyzer.generate_actionable_report(output_path)
    
    print(f"\\n✅ Analysis complete! Check reports at {output_path}.*")

if __name__ == '__main__':
    main()
