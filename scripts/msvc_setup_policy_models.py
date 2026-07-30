"""Immutable values and failures shared by the pure MSVC setup policy owners."""

from __future__ import annotations

from dataclasses import dataclass


class MsvcSetupPolicyError(ValueError):
    """Raised when a deterministic MSVC setup policy invariant is violated."""


@dataclass(frozen=True)
class EnvironmentEntry:
    """One ordered, case-insensitive Windows environment entry."""

    name: str
    value: str
