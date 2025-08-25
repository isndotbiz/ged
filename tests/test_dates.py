from gedfix.dates import normalize_gedcom_date_safe


def test_valid_simple_year():
    r = normalize_gedcom_date_safe("1900")
    assert r.is_valid
    assert r.normalized == "1900"


def test_valid_day_month_year_case_and_spaces():
    r = normalize_gedcom_date_safe(" 1  jan   1900 ")
    assert r.is_valid
    assert r.normalized == "1 JAN 1900"


def test_bet_and_range():
    r = normalize_gedcom_date_safe("BET 1900 AND 1910")
    assert r.is_valid
    assert r.normalized == "BET 1900 AND 1910"


def test_from_to_range():
    r = normalize_gedcom_date_safe("FROM JAN 1900 TO FEB 1901")
    assert r.is_valid
    assert r.normalized == "FROM JAN 1900 TO FEB 1901"


def test_unrecognized_freeform():
    r = normalize_gedcom_date_safe("01/02/1903")
    assert not r.is_valid
    assert r.reason == "non_gedcom_freeform"


