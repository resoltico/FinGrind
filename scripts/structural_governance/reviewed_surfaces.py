from __future__ import annotations

from datetime import date

from .models import FileBudget, ReviewedSurface, ReviewedSurfaceApproval

REVIEWED_SURFACES = {
    "sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql": ReviewedSurface(
        relative_path="sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql",
        owner="sqlite-schema",
        reason="The canonical SQLite schema is one intentionally reviewed data catalog owned by the storage adapter.",
        split_trigger="Break out reusable generated fragments before adding another independent schema family.",
        budget=FileBudget(
            role_name="sqlite-schema-catalog",
            max_physical_lines=1152,
            max_logical_lines=1103,
            max_import_like_lines=48,
            max_functions=0,
            max_nested_types=0,
            max_duplicate_window_lines=36,
            split_hint="split the schema into generated or named fragments instead of growing one monolithic catalog.",
        ),
        budget_variance_reason="The canonical schema catalog exceeds the default SQLite SQL budget until schema families are generated or split into narrower fragments.",
        approval=ReviewedSurfaceApproval(1152, 1103, 48, 0, 0, date(2026, 8, 1)),
    ),
}
