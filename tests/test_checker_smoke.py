from pathlib import Path
from gedfix.checker import run_checks, summarize_markdown

def test_checker_runs(tmp_path: Path):
    p = tmp_path/"t.ged"
    p.write_text("""0 HEAD
0 @I1@ INDI
1 NAME John Ii /Partridge/
2 GIVN John Ii
2 SURN Partridge
1 BIRT
2 DATE 02 jan 1900
1 BIRT
2 DATE 02 jan 1900
0 @I2@ INDI
1 NAME Mary /Ash/
1 BIRT
2 DATE 1900
2 PLAC Beauly, Inverness Shire, Scotland
0 @F1@ FAM
1 HUSB @I1@
1 WIFE @I2@
1 MARR
2 DATE 1901
1 MARR
2 DATE 1901
0 TRLR
""", encoding="utf-8")
    issues = run_checks(p, level="aggressive")
    assert any("Multiple birth facts" in i.type for i in issues)
    md = summarize_markdown(issues)
    assert "consistency issues found" in md
