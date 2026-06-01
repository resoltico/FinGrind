from __future__ import annotations

import re


def load_host_bundle_target(
    bundle_layout_targets: dict[str, dict[str, str]],
    *,
    os_name: str,
    architecture: str,
) -> dict[str, str]:
    classifier = operating_system_id(os_name) + "-" + architecture_id(architecture)
    try:
        target = bundle_layout_targets[classifier]
    except KeyError as exc:
        raise ValueError(
            f"host bundle target is not declared in the bundle layout contract: {classifier}"
        ) from exc
    return {"classifier": classifier, **target}


def operating_system_id(os_name: str) -> str:
    normalized = os_name.lower()
    if "mac" in normalized or "darwin" in normalized:
        return "macos"
    if "linux" in normalized:
        return "linux"
    if "windows" in normalized:
        return "windows"
    raise ValueError(
        f"FinGrind bundles currently support macOS, Linux, and Windows only: {os_name}"
    )


def architecture_id(architecture: str) -> str:
    normalized = architecture.lower()
    if normalized in {"arm64", "aarch64"}:
        return "aarch64"
    if normalized in {"amd64", "x86_64", "x64"}:
        return "x86_64"
    return re.sub(r"[^a-z0-9]+", "-", normalized).strip("-")
