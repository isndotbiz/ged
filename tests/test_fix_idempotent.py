from gedfix.fixer import fix_gedcom_text
from gedfix.rules import load_rules


def test_fix_idempotent_date_normalization():
    text = """
0 HEAD
1 CHAR UTF-8
0 @I1@ INDI
1 BIRT
2 DATE 1   jan   1900
0 TRLR
""".lstrip()
    rules = load_rules()
    fixed_once, changes1 = fix_gedcom_text(text, level="standard", rules=rules)
    fixed_twice, changes2 = fix_gedcom_text(fixed_once, level="standard", rules=rules)
    assert fixed_once == fixed_twice
    # second run should not add extra changes
    assert len(changes2) == 0


def test_fix_does_not_change_unrecognized_date_adds_note_once():
    text = """
0 HEAD
1 CHAR UTF-8
0 @I1@ INDI
1 BIRT
2 DATE 01/02/1903
0 TRLR
""".lstrip()
    rules = load_rules()
    fixed_once, changes1 = fix_gedcom_text(text, level="standard", rules=rules)
    fixed_twice, changes2 = fix_gedcom_text(fixed_once, level="standard", rules=rules)
    assert "01/02/1903" in fixed_once
    assert any(c["rule"] == "date_unrecognized_note" for c in changes1)
    assert len(changes2) == 0


