from __future__ import annotations

from datetime import date

from .models import ReviewedSurface, ReviewedSurfaceApproval

REVIEWED_SURFACES = {
    "sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql": ReviewedSurface(
        relative_path="sqlite/src/main/resources/dev/erst/fingrind/sqlite/book_schema.sql",
        owner="sqlite-schema",
        reason="The canonical SQLite schema is one intentionally reviewed data catalog owned by the storage adapter.",
        split_trigger="Break out reusable generated fragments before adding another independent schema family.",
        reviewed_role_name="sqlite-schema-catalog",
        budget_variance_reason="The canonical schema catalog exceeds the default SQLite SQL budget until schema families are generated or split into narrower fragments.",
        approval=ReviewedSurfaceApproval(1145, 1099, 45, 0, 0, date(2026, 8, 1)),
    ),
}
