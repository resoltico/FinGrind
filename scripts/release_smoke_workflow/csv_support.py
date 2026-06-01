from __future__ import annotations

import csv
from io import StringIO

from .models import ReleaseSmokeFailure


def parse_csv_rows(csv_output: str, surface_name: str) -> tuple[list[str], list[dict[str, str]]]:
    try:
        reader = csv.DictReader(StringIO(csv_output))
        fieldnames = reader.fieldnames
        if fieldnames is None:
            raise ReleaseSmokeFailure(f"{surface_name} did not render a CSV header")
        rows = list(reader)
    except csv.Error as exc:
        raise ReleaseSmokeFailure(f"{surface_name} was not valid CSV") from exc
    return list(fieldnames), rows
