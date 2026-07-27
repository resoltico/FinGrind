"""Administrative bootstrap capability-mode workflow."""

from __future__ import annotations

from collections.abc import Mapping

from ..models import ReleaseSmokeConfig
from ..support import require
from .administrative_modes import _modes_for
from .administrative_world_bootstrap import _new_world
from .capabilities import OperationCapability


def _verify_bootstrap_modes(
    config: ReleaseSmokeConfig,
    operation_ids: Mapping[str, str],
    operations: Mapping[str, OperationCapability],
) -> None:
    bootstrap_operations = (
        operations["generate-book-key-file"],
        operations["generate-attestation-key-file"],
        operations["open-book"],
    )
    for output_mode in _modes_for(*bootstrap_operations):
        world = _new_world(
            config,
            operation_ids,
            operations,
            "bootstrap",
            output_mode,
            bootstrap_output_mode=output_mode,
        )
        require(
            world.config.book.local_path.is_file(),
            f"{world.config.label} did not initialize its bootstrap book",
        )
