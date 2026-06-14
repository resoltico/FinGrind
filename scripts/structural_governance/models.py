from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from pathlib import Path


@dataclass(frozen=True)
class FileBudget:
    role_name: str
    max_physical_lines: int
    max_logical_lines: int
    max_import_like_lines: int
    max_functions: int
    max_nested_types: int
    max_duplicate_window_lines: int
    split_hint: str


@dataclass(frozen=True)
class ReviewedSurface:
    relative_path: str
    owner: str
    reason: str
    split_trigger: str
    reviewed_role_name: str
    budget_variance_reason: str | None
    approval: "ReviewedSurfaceApproval"


@dataclass(frozen=True)
class ReviewedSurfaceApproval:
    approved_physical_lines: int
    approved_logical_lines: int
    approved_import_like_lines: int
    approved_functions: int
    approved_nested_types: int
    expires_on: date


@dataclass(frozen=True)
class FileMetrics:
    physical_lines: int
    logical_lines: int
    import_like_lines: int
    functions: int
    nested_types: int
    normalized_nonempty_lines: tuple[str, ...]


MeasuredSurface = tuple[Path, FileBudget, FileMetrics]
