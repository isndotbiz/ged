from gedfix.places import sanitize_place_value, normalize_place_components


def test_state_abbreviation_expansion():
    components = ["New York", "NY"]
    result = normalize_place_components(components)
    assert "New York" in result


def test_basic_place_normalization():
    v, notes = sanitize_place_value("  new york,  ny,  usa  ", "aggressive")
    assert "New York" in v or "new york" in v.lower()


def test_empty_place():
    v, notes = sanitize_place_value("", "standard")
    assert v == ""


def test_place_with_commas():
    v, notes = sanitize_place_value("Springfield, IL, USA", "aggressive")
    assert "Springfield" in v
