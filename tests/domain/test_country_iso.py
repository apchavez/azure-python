from clinic.domain.entities.country_iso import CountryISO


def test_is_supported_true_for_known_values():
    assert CountryISO.is_supported("PE") is True
    assert CountryISO.is_supported("CL") is True


def test_is_supported_false_for_unknown_value():
    assert CountryISO.is_supported("US") is False


def test_is_supported_false_for_none_or_blank():
    assert CountryISO.is_supported(None) is False
    assert CountryISO.is_supported("") is False
    assert CountryISO.is_supported("   ") is False


def test_supported_values_lists_all_countries():
    values = CountryISO.supported_values()
    assert "PE" in values
    assert "CL" in values
    assert values == "PE,CL"
