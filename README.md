# gedfix

Date-safe GEDCOM 5.5.1 cleanup tool.

For contributor and AI agent guidance, see [WARP.md](WARP.md).

## Install

```bash
pip install -e .[dev]
```

## CLI

```bash
gedfix scan INPUT.ged --report out/report.json
gedfix fix INPUT.ged --level standard --out out/gedfix_fixed.ged --backup-dir out/backup --dry-run
gedfix report out/report.json --markdown out/report.md
```

## Notes
- Only whitespace and month-token uppercasing are applied to valid dates.
- Unrecognized dates are preserved and a NOTE is added beginning with `AutoFix:`.
- Multiple runs are idempotent and do not duplicate notes.

## Testing

```bash
pytest -q
```
