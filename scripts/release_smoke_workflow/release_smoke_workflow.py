from __future__ import annotations

from .models import ReleaseSmokeConfig
from .release_smoke_field_execution import execute_release_smoke_field_matrix
from .release_smoke_initialization import initialize_release_smoke


def run_release_smoke(config: ReleaseSmokeConfig) -> None:
    context = initialize_release_smoke(config)
    execute_release_smoke_field_matrix(context)
    print(f"{config.label}: success")
