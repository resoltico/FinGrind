from __future__ import annotations

from pathlib import Path

from .models import ReleaseSmokeFailure, SmokePath

ARGUMENT_PATH_MODE_ABSOLUTE = "absolute"
ARGUMENT_PATH_MODE_WORK_ROOT_RELATIVE = "relative-to-work-root"


def smoke_path(work_root: Path, argument_path_mode: str, relative_path: Path) -> SmokePath:
    local_path = work_root / relative_path
    if argument_path_mode == ARGUMENT_PATH_MODE_ABSOLUTE:
        return SmokePath(
            relative_path=relative_path, local_path=local_path, argument=str(local_path)
        )
    if argument_path_mode == ARGUMENT_PATH_MODE_WORK_ROOT_RELATIVE:
        return SmokePath(
            relative_path=relative_path,
            local_path=local_path,
            argument=relative_path.as_posix(),
        )
    raise ReleaseSmokeFailure("unsupported release-smoke argument path mode: " + argument_path_mode)
