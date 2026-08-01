from __future__ import annotations

import json
import os
import re
import subprocess
from datetime import UTC, datetime
from pathlib import Path


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


def require_match(text: str, pattern: str, message: str) -> None:
    if re.search(pattern, text, re.MULTILINE) is None:
        raise SystemExit(message)


def require_no_match(text: str, pattern: str, message: str) -> None:
    if re.search(pattern, text, re.MULTILINE) is not None:
        raise SystemExit(message)


def normalize_newlines(text: str) -> str:
    return text.replace("\r", "")


def joined_path(root: Path, relative_path: str) -> Path:
    segments = [segment for segment in relative_path.replace("\\", "/").split("/") if segment]
    return root.joinpath(*segments)


def load_bundle_manifest(bundle_root: Path) -> dict[str, object]:
    manifest_path = bundle_root / "bundle-manifest.json"
    require(manifest_path.is_file(), f"missing bundle file at {manifest_path}")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    require(
        isinstance(manifest, dict), f"bundle manifest at {manifest_path} must be one JSON object"
    )
    return manifest


def manifest_normalized_artifact_epoch_seconds(manifest: dict[str, object]) -> int:
    timestamp_utc = manifest.get("normalizedArtifactTimestampUtc")
    require(
        isinstance(timestamp_utc, str) and timestamp_utc.strip(),
        "bundle manifest must declare one non-blank normalizedArtifactTimestampUtc value",
    )
    normalized_timestamp = timestamp_utc.strip().replace("Z", "+00:00")
    try:
        parsed = datetime.fromisoformat(normalized_timestamp)
    except ValueError as exc:
        raise SystemExit(
            f"bundle manifest declared one malformed normalizedArtifactTimestampUtc value: {timestamp_utc}"
        ) from exc
    require(
        parsed.tzinfo is not None,
        "bundle manifest normalizedArtifactTimestampUtc must carry one timezone offset",
    )
    normalized_epoch_seconds = int(parsed.astimezone(UTC).timestamp())
    require(
        normalized_epoch_seconds % 2 == 0,
        "bundle manifest normalizedArtifactTimestampUtc must use ZIP-portable even-second granularity",
    )
    return normalized_epoch_seconds


def resolve_bundle_target(
    contract: dict[str, object], manifest: dict[str, object]
) -> tuple[str, dict[str, object]]:
    bundle_target = manifest.get("bundleTarget")
    require(
        isinstance(bundle_target, dict),
        "bundle manifest must declare one bundleTarget object",
    )
    classifier = bundle_target.get("classifier")
    require(
        isinstance(classifier, str) and classifier.strip(),
        "bundle manifest must declare one non-blank bundle target classifier",
    )
    bundle_layout = contract.get("bundleLayout")
    require(
        isinstance(bundle_layout, dict),
        "contract must declare one bundleLayout object for bundle verification",
    )
    bundle_targets = bundle_layout.get("targets")
    require(
        isinstance(bundle_targets, dict),
        "contract must declare one bundleLayout.targets object for bundle verification",
    )
    target = bundle_targets.get(classifier)
    require(
        isinstance(target, dict),
        f"bundle manifest referenced undeclared bundle target {classifier}",
    )
    return classifier, target


def bundled_java_command(bundle_root: Path) -> Path:
    candidates = [
        bundle_root / "runtime" / "bin" / "java",
        bundle_root / "runtime" / "bin" / "java.exe",
    ]
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    raise SystemExit(f"missing bundled Java runtime under {bundle_root / 'runtime' / 'bin'}")


def normalized_command_output(command: list[str]) -> str:
    completed = subprocess.run(
        command,
        check=True,
        capture_output=True,
        text=True,
    )
    return normalize_newlines(completed.stdout + completed.stderr)


def verify_java_version(java_command: Path, expected_source_checkout_java: str) -> None:
    expected_feature_version = expected_source_checkout_java.rstrip("+")
    require(
        bool(expected_feature_version),
        "source-checkout Java contract must not be blank when verifying the bundled runtime",
    )

    version_output = normalized_command_output([str(java_command), "--version"])
    first_line = next((line for line in version_output.splitlines() if line.strip()), "")
    version_tokens = [token for token in first_line.split() if token]
    require(
        len(version_tokens) >= 2
        and (
            version_tokens[1] == expected_feature_version
            or version_tokens[1].startswith(expected_feature_version + ".")
        ),
        f"bundled Java runtime did not report Java {expected_feature_version}",
    )


def require_executable(path: Path, message: str) -> None:
    require(
        os.access(path, os.X_OK),
        message,
    )
