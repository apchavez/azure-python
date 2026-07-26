"""Supported countries for country-specific booking flows (PE / CL)."""

from enum import Enum


class CountryISO(str, Enum):
    PE = "PE"
    CL = "CL"

    @staticmethod
    def is_supported(value: str | None) -> bool:
        if value is None or value.strip() == "":
            return False
        return value in (c.value for c in CountryISO)

    @staticmethod
    def supported_values() -> str:
        return ",".join(c.value for c in CountryISO)
