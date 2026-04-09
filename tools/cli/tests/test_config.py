from gedfix.config import get_config, reset_config, Config, load_rules


def test_config_loads():
    """Test that config loads without errors."""
    config = get_config()
    assert config is not None
    assert config.notes.note_prefix == "AutoFix:"


def test_config_has_all_sections():
    """Test that config has all required sections."""
    config = get_config()
    assert hasattr(config, "paths")
    assert hasattr(config, "thresholds")
    assert hasattr(config, "notes")
    assert hasattr(config, "gedcom")
    assert hasattr(config, "names")
    assert hasattr(config, "places")
    assert hasattr(config, "levels")


def test_config_levels():
    """Test that processing levels are configured."""
    config = get_config()
    assert "standard" in config.levels
    assert "aggressive" in config.levels
    assert "ultra" in config.levels
    assert "comprehensive" in config.levels


def test_load_rules_backwards_compat():
    """Test backwards compatibility with load_rules()."""
    rules = load_rules()
    assert "note_prefix" in rules
    assert "levels" in rules
    assert "standard" in rules["levels"]


def test_config_thresholds():
    """Test threshold values are reasonable."""
    config = get_config()
    assert 0 <= config.thresholds.default_dedup_threshold <= 100
    assert 0 <= config.thresholds.auto_merge_threshold <= 100
    assert config.thresholds.exact_birth_year_bonus > 0
    assert config.thresholds.large_year_diff_penalty < 0


def test_config_gedcom_months():
    """Test GEDCOM month configuration."""
    config = get_config()
    assert "JAN" in config.gedcom.months
    assert "DEC" in config.gedcom.months
    assert len(config.gedcom.months) == 12


def test_config_reset():
    """Test that reset_config allows reload."""
    config1 = get_config()
    reset_config()
    config2 = get_config()
    # Should get new config instance
    assert config1 is not config2
