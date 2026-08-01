"""Typed state exchanged between administrative matrix workflow owners."""

from __future__ import annotations

from collections.abc import Callable, Mapping
from dataclasses import dataclass
from pathlib import Path

from ..attestation_head_checks import VerifiedAttestationHead
from ..models import ReleaseSmokeConfig

JsonObject = dict[str, object]
PostOutputAssertion = Callable[[JsonObject | None, str], None]


@dataclass(frozen=True)
class AdministrativeWorld:
    """One independently initialized book and its operator-controlled inputs."""

    config: ReleaseSmokeConfig
    path_anchor_config: ReleaseSmokeConfig
    operation_ids: Mapping[str, str]
    root: Path
    request_directory: Path
    artifact_directory: Path


@dataclass(frozen=True)
class ObservedBookState:
    """The immutable chain position and complete posting state around a read-only command."""

    attestation_head: VerifiedAttestationHead
    posting_state: str
