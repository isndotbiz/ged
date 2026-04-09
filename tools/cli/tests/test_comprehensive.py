from pathlib import Path
from gedfix.api import fix, scan
from gedfix.dates import sanitize_date_value, convert_month_names
from gedfix.names import sanitize_name_value
from gedfix.places import sanitize_place_value

def test_comprehensive_date_conversion():
    """Test advanced date conversion features."""
    # Test month name conversion
    v, notes = sanitize_date_value("circa January 15, 1850", "comprehensive")
    assert "ABT" in v
    assert "JAN" in v
    assert len(notes) > 0

def test_comprehensive_name_formatting():
    """Test advanced name formatting."""
    v, notes = sanitize_name_value("john william /DOE/", "comprehensive")
    assert v == "John William /Doe/"
    
    # Test Scottish name handling
    v, notes = sanitize_name_value("robert /mcdonald/", "comprehensive")
    assert "McDonald" in v

def test_comprehensive_place_standardization():
    """Test place name standardization."""
    v, notes = sanitize_place_value("new york city, NY, usa", "comprehensive")
    assert "New York City" in v
    assert "New York" in v
    assert "United States" in v

def test_month_name_conversion():
    """Test month name conversion function."""
    assert convert_month_names("January 1850") == "JAN 1850"
    assert convert_month_names("circa February") == "ABT FEB"
    assert convert_month_names("Dec. 31") == "DEC 31"

def test_comprehensive_level_processing(tmp_path: Path):
    """Test comprehensive level automation on real GEDCOM file."""
    # Create test file
    test_ged = tmp_path / "test.ged"
    test_ged.write_text("""0 HEAD
1 CHAR UTF-8
0 @I1@ INDI
1 NAME john /DOE/
1 BIRT
2 DATE circa january 1850
2 PLAC nyc, ny, us
0 TRLR
""", encoding="utf-8")
    
    out_ged = tmp_path / "fixed.ged"
    
    # Apply comprehensive level fixes
    result = fix(test_ged, out_ged, level="comprehensive", backup_dir=None)
    
    # Verify results
    assert result["changed"] >= 1
    assert "comprehensive" == result["level"]
    
    # Check output content
    content = out_ged.read_text(encoding="utf-8")
    assert "John /Doe/" in content  # Name formatting
    assert "ABT JAN 1850" in content  # Date conversion
    assert "New York City, New York, United States" in content  # Place standardization
