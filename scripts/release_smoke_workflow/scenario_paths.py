from __future__ import annotations

from pathlib import Path

from .models import ReleaseSmokeConfig, ReleaseSmokeFailure, SmokePath

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


def sibling_smoke_path(path: SmokePath, file_name: str) -> SmokePath:
    return SmokePath(
        relative_path=path.relative_path.with_name(file_name),
        local_path=path.local_path.with_name(file_name),
        argument=str(Path(path.argument).with_name(file_name)),
    )


def smoke_path_from_local(config: ReleaseSmokeConfig, local_path: Path) -> SmokePath:
    """Map a fresh workflow artifact back to the configured CLI path mode.

    Every isolated field scenario remains below ``work_root`` so that the
    host, bundle, and container runners exercise the same physical artifact
    through their respective public path spellings.
    """
    checked_local_path = Path(local_path)
    try:
        relative_path = checked_local_path.relative_to(config.work_root)
    except ValueError as exc:
        raise ReleaseSmokeFailure(
            f"release-smoke scenario path escaped its work root: {checked_local_path}"
        ) from exc
    argument = (
        str(checked_local_path)
        if config.book.argument == str(config.book.local_path)
        else relative_path.as_posix()
    )
    return SmokePath(
        relative_path=relative_path,
        local_path=checked_local_path,
        argument=argument,
    )
