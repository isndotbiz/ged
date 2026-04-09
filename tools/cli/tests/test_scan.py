from pathlib import Path
from gedfix.api import scan_file


def test_scan_reports_unrecognized_date(tmp_path: Path):
    sample = """
0 HEAD
1 SOUR TEST
1 GEDC
2 VERS 5.5.1
1 CHAR UTF-8
0 @I1@ INDI
1 NAME John /Doe/
1 BIRT
2 DATE 01/02/1903
0 TRLR
""".lstrip()
    p = tmp_path / "sample.ged"
    p.write_text(sample, encoding="utf-8")
    report = scan_file(p)
    assert any(i["rule"] == "unrecognized_date" for i in report["issues"])  # type: ignore[index]


