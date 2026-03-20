from __future__ import annotations
import json
from pathlib import Path
import click
from .api import scan as api_scan, fix as api_fix, report as api_report
from .deduplication import deduplicate_gedcom
from .manual_review import ManualReviewSession


@click.group()
def main() -> None:
    """gedfix: date-safe GEDCOM 5.5.1 cleaner with auditable AutoFix notes."""
    pass


@main.command()
def version():
    """Print gedfix version."""
    from . import __version__
    click.echo(f"gedfix {__version__}")


@main.command()
@click.argument("input_ged", type=click.Path(exists=True, dir_okay=False, path_type=Path))
@click.option("--level", type=click.Choice(["standard","aggressive","ultra"]), default="standard")
@click.option("--report", "report_path", type=click.Path(dir_okay=False, path_type=Path), default=Path("out/check_report.md"))
def check(input_ged: Path, level: str, report_path: Path):
    """Run batchable consistency checks (no date changes)."""
    from .checker import run_checks, summarize_markdown
    issues = run_checks(input_ged, level=level)
    txt = summarize_markdown(issues)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(txt, encoding="utf-8")
    click.echo(f"Wrote {report_path}")


@main.command()
@click.argument("input_ged", type=click.Path(exists=True, dir_okay=False, path_type=Path))
@click.option("--report", "report_path", type=click.Path(dir_okay=False, path_type=Path), default=Path("out/report.json"))
def scan(input_ged: Path, report_path: Path):
    """Scan INPUT_GED and write a JSON report."""
    res = api_scan(input_ged)
    api_report(res, report_path)
    click.echo(f"Wrote {report_path}")


@main.command()
@click.argument("input_ged", type=click.Path(exists=True, dir_okay=False, path_type=Path))
@click.option("--out", "out_path", type=click.Path(dir_okay=False, path_type=Path), default=Path("out/gedfix_fixed.ged"))
@click.option("--level", type=click.Choice(["standard","aggressive","ultra","comprehensive"]), default="standard")
@click.option("--backup-dir", type=click.Path(file_okay=False, path_type=Path), default=Path("out/backup"))
@click.option("--note-prefix", type=str, default=None, help="Override AutoFix NOTE prefix")
@click.option("--dry-run/--no-dry-run", default=False)
@click.option("--batch", type=click.Choice(["standard","aggressive","ultra"]), default="standard", help="Batch profile for non-date fixes.")
@click.option("--approve-dup-facts/--no-approve-dup-facts", default=False, help="Remove exact duplicate BIRT/DEAT/MARR blocks.")
@click.option("--approve-suffix/--no-approve-suffix", default=False, help="Move suffix tokens to NSFX field.")
def fix(input_ged: Path, out_path: Path, level: str, backup_dir: Path, note_prefix: str | None, dry_run: bool, batch: str, approve_dup_facts: bool, approve_suffix: bool):
    """Safely fix INPUT_GED and write output GED, preserving ambiguous dates."""
    if dry_run:
        tmp_out = Path("out/_dry_run.ged")
        res = api_fix(input_ged, tmp_out, level, backup_dir=None, note_prefix=note_prefix)
        click.echo(json.dumps(res, indent=2))
        tmp_out.unlink(missing_ok=True)
        return
    out_path.parent.mkdir(parents=True, exist_ok=True)
    res = api_fix(input_ged, out_path, level, backup_dir=backup_dir, note_prefix=note_prefix)
    from .fix_non_date import fix_duplicates_and_suffix
    tmp = out_path
    res2 = fix_duplicates_and_suffix(tmp, tmp, approve_dup_facts=approve_dup_facts, approve_suffix=approve_suffix, note_prefix=note_prefix, backup_dir=None)
    click.echo(json.dumps(res, indent=2))
    click.echo(json.dumps(res2, indent=2))
    click.echo(f"Wrote {out_path}")


@main.command()
@click.argument("input_ged", type=click.Path(exists=True, dir_okay=False, path_type=Path))
@click.option("--out", "out_path", type=click.Path(dir_okay=False, path_type=Path), default=Path("out/deduplicated.ged"))
@click.option("--threshold", type=float, default=95.0, help="Similarity threshold for duplicate detection (0-100)")
@click.option("--auto-merge-threshold", type=float, default=100.0, help="Auto-merge threshold for perfect matches")
@click.option("--backup-dir", type=click.Path(file_okay=False, path_type=Path), default=Path("out/backup"))
@click.option("--dry-run/--no-dry-run", default=False)
@click.option("--confirm/--no-confirm", default=True, help="Ask for confirmation before removing duplicates")
def dedupe(input_ged: Path, out_path: Path, threshold: float, auto_merge_threshold: float,
           backup_dir: Path, dry_run: bool, confirm: bool):
    """Remove duplicate individuals from GEDCOM file using fuzzy matching."""
    if dry_run:
        click.echo("DRY RUN MODE - No changes will be made")
        backup_dir = None
        out_path = Path("out/_dedupe_dry_run.ged")
    if confirm and not dry_run:
        click.echo(f"This will remove duplicate individuals from {input_ged}")
        click.echo(f"Similarity threshold: {threshold}%")
        click.echo(f"Auto-merge threshold: {auto_merge_threshold}%")
        click.echo(f"Output file: {out_path}")
        if not click.confirm("Continue with duplicate removal?"):
            click.echo("Aborted.")
            return
    try:
        out_path.parent.mkdir(parents=True, exist_ok=True)
        click.echo("Analyzing duplicates...")
        results = deduplicate_gedcom(
            input_path=input_ged,
            output_path=out_path,
            threshold=threshold,
            auto_merge_threshold=auto_merge_threshold,
            backup_dir=backup_dir if not dry_run else None
        )
        click.echo(json.dumps(results, indent=2))
        if dry_run:
            click.echo("Dry run completed - no files were modified")
            out_path.unlink(missing_ok=True)
        else:
            click.echo(f"Wrote deduplicated file: {out_path}")
    except Exception as e:
        click.echo(f"Error during deduplication: {e}")
        if dry_run:
            out_path.unlink(missing_ok=True)


@main.command()
@click.argument("gedcom_file", type=click.Path(exists=True, path_type=Path))
@click.option("--issues-file", type=click.Path(path_type=Path), help="JSON file to store review session")
@click.option("--start-index", type=int, default=0, help="Issue index to start from")
def review(gedcom_file: Path, issues_file, start_index: int):
    """Interactive manual review of genealogical consistency issues."""
    session = ManualReviewSession(gedcom_file, issues_file)
    session.current_index = start_index
    session.run_interactive_session()


@main.command()
@click.argument("input_ged", type=click.Path(exists=True, dir_okay=False, path_type=Path))
@click.option("--json-output/--no-json-output", "as_json", default=False, help="Output as JSON")
def stats(input_ged: Path, as_json: bool):
    """Show statistics for a GEDCOM file."""
    from .checker import build_index
    import re
    from collections import Counter

    people, families = build_index(input_ged)

    # Count surnames
    surnames: Counter[str] = Counter()
    birth_years: list[int] = []
    death_years: list[int] = []

    for p in people.values():
        for name in p.names:
            surn = name.get("SURN", "").strip()
            if surn:
                surnames[surn] += 1
        for evt in p.events.get("BIRT", []):
            d = evt.get("DATE", "")
            m = re.search(r"(\d{4})", d)
            if m:
                birth_years.append(int(m.group(1)))
        for evt in p.events.get("DEAT", []):
            d = evt.get("DATE", "")
            m = re.search(r"(\d{4})", d)
            if m:
                death_years.append(int(m.group(1)))

    result = {
        "file": str(input_ged),
        "individuals": len(people),
        "families": len(families),
        "top_surnames": dict(surnames.most_common(15)),
        "earliest_birth": min(birth_years) if birth_years else None,
        "latest_birth": max(birth_years) if birth_years else None,
        "earliest_death": min(death_years) if death_years else None,
        "latest_death": max(death_years) if death_years else None,
    }

    if as_json:
        click.echo(json.dumps(result, indent=2))
    else:
        click.echo(f"File: {input_ged}")
        click.echo(f"Individuals: {result['individuals']}")
        click.echo(f"Families: {result['families']}")
        click.echo(f"Birth range: {result['earliest_birth'] or '?'} - {result['latest_birth'] or '?'}")
        click.echo(f"Death range: {result['earliest_death'] or '?'} - {result['latest_death'] or '?'}")
        click.echo(f"Top surnames:")
        for name, count in surnames.most_common(15):
            click.echo(f"  {name}: {count}")


@main.command()
@click.argument("file_a", type=click.Path(exists=True, dir_okay=False, path_type=Path))
@click.argument("file_b", type=click.Path(exists=True, dir_okay=False, path_type=Path))
@click.option("--out", "out_path", type=click.Path(dir_okay=False, path_type=Path), default=Path("out/merged.ged"))
def merge(file_a: Path, file_b: Path, out_path: Path):
    """Merge two GEDCOM files, remapping xrefs to avoid collisions."""
    from .merge import merge_gedcom
    result = merge_gedcom(file_a, file_b, out_path)
    click.echo(json.dumps(result, indent=2))
    click.echo(f"Wrote {out_path}")


@main.command("export")
@click.argument("input_ged", type=click.Path(exists=True, dir_okay=False, path_type=Path))
@click.option("--out", "out_path", type=click.Path(dir_okay=False, path_type=Path), default=Path("out/exported.ged"))
@click.option("--filter-living/--no-filter-living", default=False, help="Redact living persons' details")
@click.option("--cutoff-years", type=int, default=110, help="Max age to assume living (default 110)")
def export_cmd(input_ged: Path, out_path: Path, filter_living: bool, cutoff_years: int):
    """Export GEDCOM with optional privacy filtering."""
    from .export import export_filtered
    result = export_filtered(input_ged, out_path, filter_living=filter_living, cutoff_years=cutoff_years)
    click.echo(json.dumps(result, indent=2))
    click.echo(f"Wrote {out_path}")
