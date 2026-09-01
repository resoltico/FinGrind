"""Verify linked-runtime legal records and their source-JDK provenance."""

from __future__ import annotations

import hashlib
import re
from pathlib import Path

from bundle_archive_contract_support import require


def verify_runtime_legal_payload(
    bundle_root: Path,
    *,
    expected_operating_system_id: str,
    expected_architecture_id: str,
) -> None:
    runtime_directory = bundle_root / "runtime"
    legal_directory = runtime_directory / "legal"
    indexed_paths = _verify_complete_legal_index(legal_directory)
    linked_modules = _read_linked_modules(runtime_directory)
    _require_module_controlling_files(linked_modules, indexed_paths)
    _require_requested_modules(runtime_directory, linked_modules)
    _require_source_jdk_provenance(
        runtime_directory,
        expected_operating_system_id=expected_operating_system_id,
        expected_architecture_id=expected_architecture_id,
    )


def _verify_complete_legal_index(legal_directory: Path) -> set[str]:
    legal_index = legal_directory / "INDEX.sha256"
    index_lines = legal_index.read_text(encoding="utf-8").splitlines()
    require(index_lines, f"runtime legal index was empty: {legal_index}")
    indexed_paths: set[str] = set()
    for row in index_lines:
        digest, separator, relative_name = row.partition("  ")
        require(
            separator == "  "
            and re.fullmatch(r"[0-9a-f]{64}", digest) is not None
            and relative_name
            and not Path(relative_name).is_absolute()
            and ".." not in Path(relative_name).parts,
            f"invalid runtime legal index row: {row}",
        )
        legal_file = legal_directory / relative_name
        require(legal_file.is_file(), f"indexed runtime legal file was absent: {legal_file}")
        if legal_file.is_symlink():
            require(
                legal_file.resolve(strict=True).is_relative_to(
                    legal_directory.resolve(strict=True)
                ),
                f"runtime legal symlink escaped its legal tree: {legal_file}",
            )
        require(
            hashlib.sha256(legal_file.read_bytes()).hexdigest() == digest,
            f"runtime legal file digest did not match its index: {legal_file}",
        )
        require(
            relative_name not in indexed_paths, f"duplicate runtime legal path: {relative_name}"
        )
        indexed_paths.add(relative_name)
    actual_paths = {
        path.relative_to(legal_directory).as_posix()
        for path in legal_directory.rglob("*")
        if path.is_file() and path != legal_index
    }
    require(
        actual_paths == indexed_paths, "runtime legal tree differed from its complete hash index"
    )
    return indexed_paths


def _read_linked_modules(runtime_directory: Path) -> set[str]:
    release_text = (runtime_directory / "release").read_text(encoding="utf-8")
    module_match = re.search(r'^MODULES="([^"]+)"$', release_text, flags=re.MULTILINE)
    require(module_match is not None, "runtime release metadata omitted the linked module closure")
    return set(module_match.group(1).split())


def _require_module_controlling_files(linked_modules: set[str], indexed_paths: set[str]) -> None:
    for module_name in linked_modules:
        required_module_paths = {
            f"{module_name}/LICENSE",
            f"{module_name}/ADDITIONAL_LICENSE_INFO",
            f"{module_name}/ASSEMBLY_EXCEPTION",
        }
        require(
            required_module_paths <= indexed_paths,
            f"runtime legal index omitted controlling files for module {module_name}",
        )


def _require_requested_modules(runtime_directory: Path, linked_modules: set[str]) -> None:
    requested_module_text = (runtime_directory / "provenance" / "requested-modules.txt").read_text(
        encoding="utf-8"
    )
    requested_modules = {
        module.strip() for module in requested_module_text.split(",") if module.strip()
    }
    require(
        requested_modules and requested_modules <= linked_modules,
        "requested runtime module roots were not a subset of the linked module closure",
    )


def _require_source_jdk_provenance(
    runtime_directory: Path, *, expected_operating_system_id: str, expected_architecture_id: str
) -> None:
    runtime_release = (runtime_directory / "release").read_text(encoding="utf-8")
    source_release = (runtime_directory / "provenance" / "source-jdk-release").read_text(
        encoding="utf-8"
    )
    runtime_java_version = _release_value(runtime_release, "JAVA_VERSION")
    source_java_version = _release_value(source_release, "JAVA_VERSION")
    require(
        runtime_java_version == source_java_version
        and source_java_version.startswith("26.")
        and _release_value(source_release, "IMPLEMENTOR")
        and _release_value(source_release, "IMPLEMENTOR_VERSION")
        and _release_value(source_release, "JAVA_RUNTIME_VERSION").startswith(source_java_version)
        and re.search(r'^SOURCE="[^"]+"$', source_release, flags=re.MULTILINE) is not None,
        "runtime provenance did not identify the Java 26 source JDK that produced the linked runtime",
    )
    expected_os_name = {"linux": "Linux", "macos": "Darwin", "windows": "Windows"}.get(
        expected_operating_system_id
    )
    expected_arch_names = {
        "x86_64": {"x86_64", "amd64"},
        "aarch64": {"aarch64", "arm64"},
    }.get(expected_architecture_id)
    os_match = re.search(r'^OS_NAME="([^"]+)"$', source_release, flags=re.MULTILINE)
    arch_match = re.search(r'^OS_ARCH="([^"]+)"$', source_release, flags=re.MULTILINE)
    require(
        expected_os_name is not None
        and expected_arch_names is not None
        and os_match is not None
        and os_match.group(1) == expected_os_name
        and arch_match is not None
        and arch_match.group(1) in expected_arch_names,
        "runtime source JDK provenance did not match the declared bundle target",
    )


def _release_value(release_text: str, key: str) -> str:
    match = re.search(rf'^{re.escape(key)}="([^"]+)"$', release_text, flags=re.MULTILINE)
    return "" if match is None else match.group(1)
