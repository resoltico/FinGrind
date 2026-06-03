from __future__ import annotations

from datetime import date

from .models import FileBudget, ReviewedSurface, ReviewedSurfaceApproval

REVIEWED_SURFACE_EXPIRY = date(2026, 8, 31)

REVIEWED_SURFACES = {
    "scripts/verify-structural-governance.py": ReviewedSurface(
        relative_path="scripts/verify-structural-governance.py",
        owner="repo-governance",
        reason="The public verifier entrypoint is one intentionally reviewed control-plane surface.",
        split_trigger="Keep the wrapper thin and move any new surface-specific logic into the structural_governance package.",
        budget=FileBudget(
            role_name="python-structural-governance",
            max_physical_lines=80,
            max_logical_lines=40,
            max_import_like_lines=6,
            max_functions=2,
            max_nested_types=2,
            max_duplicate_window_lines=24,
            split_hint="keep the entrypoint thin and route structural-governance behavior through focused support modules.",
        ),
        approval=ReviewedSurfaceApproval(11, 6, 3, 0, 0, REVIEWED_SURFACE_EXPIRY),
    ),
    "scripts/test-release-smoke-workflow-contract.py": ReviewedSurface(
        relative_path="scripts/test-release-smoke-workflow-contract.py",
        owner="release-smoke-contract",
        reason="The release smoke contract suite is a deliberately broad verification matrix over one public workflow.",
        split_trigger="Split by workflow phase before adding another independent smoke contract family.",
        budget=FileBudget(
            role_name="python-release-contract-suite",
            max_physical_lines=363,
            max_logical_lines=337,
            max_import_like_lines=18,
            max_functions=26,
            max_nested_types=8,
            max_duplicate_window_lines=28,
            split_hint="split the contract suite by release workflow phase or artifact family.",
        ),
        approval=ReviewedSurfaceApproval(363, 336, 13, 7, 0, REVIEWED_SURFACE_EXPIRY),
    ),
    "scripts/release_smoke_workflow/assertions.py": ReviewedSurface(
        relative_path="scripts/release_smoke_workflow/assertions.py",
        owner="release-smoke-assertions",
        reason="The release smoke assertions surface is the compatibility re-export seam for focused assertion owners.",
        split_trigger="Keep the façade thin and add new assertion families under focused modules instead of re-growing the umbrella file.",
        budget=FileBudget(
            role_name="python-release-assertions",
            max_physical_lines=17,
            max_logical_lines=15,
            max_import_like_lines=4,
            max_functions=0,
            max_nested_types=0,
            max_duplicate_window_lines=24,
            split_hint="keep the assertions façade thin and add focused assertion owners underneath it.",
        ),
        approval=ReviewedSurfaceApproval(17, 15, 4, 0, 0, REVIEWED_SURFACE_EXPIRY),
    ),
    "scripts/contract_values.py": ReviewedSurface(
        relative_path="scripts/contract_values.py",
        owner="contract-values",
        reason="The contract-values reader is the intentionally reviewed composition root over focused contract-family loaders.",
        split_trigger="Keep the composition root thin and push any new contract-family assembly into a focused helper owner.",
        budget=FileBudget(
            role_name="python-contract-values",
            max_physical_lines=210,
            max_logical_lines=201,
            max_import_like_lines=5,
            max_functions=24,
            max_nested_types=8,
            max_duplicate_window_lines=28,
            split_hint="split the contract-value reader by one contract family per helper cluster.",
        ),
        approval=ReviewedSurfaceApproval(29, 22, 5, 2, 0, REVIEWED_SURFACE_EXPIRY),
    ),
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
        approval=ReviewedSurfaceApproval(1152, 1103, 48, 0, 0, REVIEWED_SURFACE_EXPIRY),
    ),
}
