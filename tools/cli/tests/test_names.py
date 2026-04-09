from gedfix.names import normalize_name_case, sanitize_name_value


def test_basic_name_capitalization():
    assert normalize_name_case("john /DOE/") == "John /Doe/"


def test_surname_in_slashes_preserved():
    assert normalize_name_case("Jane /Smith/") == "Jane /Smith/"


def test_mcname_handling():
    result = normalize_name_case("john /MCDONALD/")
    assert result == "John /McDonald/"


def test_oname_handling():
    result = normalize_name_case("patrick /O'BRIEN/")
    assert result == "Patrick /O'Brien/"


def test_sanitize_name_value_standard():
    v, notes = sanitize_name_value("john william /DOE/", "standard")
    assert v == "John William /Doe/"


def test_empty_name():
    assert normalize_name_case("") == ""


def test_suffix_in_name():
    v, notes = sanitize_name_value("John /Doe/ Jr.", "standard")
    assert "John" in v
    assert "Doe" in v
