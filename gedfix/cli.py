import sys
import json
from pathlib import Path
import shutil
import click

from . import __version__
from .api import scan_file, fix_file, write_markdown_report


@click.group()
@click.version_option(__version__)
def main() -> None:
    """gedfix: date-safe GEDCOM 5.5.1 cleanup tool."""


@main.command()
@click.argument("input_path", type=click.Path(exists=True, dir_okay=False, path_type=Path))
@click.option("--report", "report_path", type=click.Path(dir_okay=False, path_type=Path), required=True)
def scan(input_path: Path, report_path: Path) -> None:
    """Scan INPUT.ged and write JSON report."""
    result = scan_file(input_path)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(result, indent=2), encoding="utf-8")
    click.echo(str(report_path))


@main.command()
@click.argument("input_path", type=click.Path(exists=True, dir_okay=False, path_type=Path))
@click.option("--level", type=click.Choice(["standard", "aggressive", "ultra"]), default="standard", show_default=True)
@click.option("--out", "out_path", type=click.Path(dir_okay=False, path_type=Path), required=True)
@click.option("--backup-dir", type=click.Path(file_okay=False, path_type=Path), default=Path("out/backup"), show_default=True)
@click.option("--dry-run/--no-dry-run", default=True, show_default=True)
def fix(input_path: Path, level: str, out_path: Path, backup_dir: Path, dry_run: bool) -> None:
    """Fix INPUT.ged with the given level and write to --out."""
    out_path.parent.mkdir(parents=True, exist_ok=True)
    backup_dir.mkdir(parents=True, exist_ok=True)

    if not dry_run:
        # Backup original
        backup_target = backup_dir / (input_path.name + ".bak")
        shutil.copy2(input_path, backup_target)

    report, fixed_text = fix_file(input_path, level=level, dry_run=dry_run)

    if fixed_text is not None:
        out_path.write_text(fixed_text, encoding="utf-8")
    click.echo(str(out_path))

    # Also write a JSON report next to output
    report_json_path = out_path.with_suffix(".report.json")
    report_json_path.write_text(json.dumps(report, indent=2), encoding="utf-8")


@main.command()
@click.argument("report_json", type=click.Path(exists=True, dir_okay=False, path_type=Path))
@click.option("--markdown", "markdown_path", type=click.Path(dir_okay=False, path_type=Path), required=True)
def report(report_json: Path, markdown_path: Path) -> None:
    """Convert JSON REPORT to Markdown."""
    data = json.loads(report_json.read_text(encoding="utf-8"))
    markdown = write_markdown_report(data)
    markdown_path.parent.mkdir(parents=True, exist_ok=True)
    markdown_path.write_text(markdown, encoding="utf-8")
    click.echo(str(markdown_path))


if __name__ == "__main__":
    main()


