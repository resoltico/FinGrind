from __future__ import annotations

import zipfile
from pathlib import Path

from bundle_archive_contract_support import (
    bundled_java_command,
    joined_path,
    load_bundle_manifest,
    normalize_newlines,
    normalized_command_output,
    require,
    require_match,
    require_no_match,
    resolve_bundle_target,
    verify_java_version,
)


def verify_bundled_runtime(bundle_root: Path, contract: dict[str, object]) -> None:
    java_command = bundled_java_command(bundle_root)
    runtime_environment = contract["runtimeEnvironment"]
    assert isinstance(runtime_environment, dict)
    verify_java_version(java_command, str(runtime_environment["sourceCheckoutJava"]))

    runtime_modules_output = normalized_command_output([str(java_command), "--list-modules"])
    require_no_match(
        runtime_modules_output,
        r"^jdk\.jlink@",
        "bundled Java runtime contains jdk.jlink",
    )
    require_no_match(
        runtime_modules_output,
        r"^jdk\.jpackage@",
        "bundled Java runtime contains jdk.jpackage",
    )
    require_no_match(
        runtime_modules_output,
        r"^jdk\.jdeps@",
        "bundled Java runtime contains jdk.jdeps",
    )
    require_match(
        runtime_modules_output,
        r"^jdk\.unsupported@",
        "bundled Java runtime omitted jdk.unsupported, which PDF export requires for a noise-free runtime",
    )


def verify_distributed_module_identity(bundle_root: Path, contract: dict[str, object]) -> None:
    manifest = load_bundle_manifest(bundle_root)
    _, bundle_target = resolve_bundle_target(contract, manifest)
    launcher_path = joined_path(bundle_root, str(bundle_target["launcherPath"]))
    application_jar = bundle_root / "lib" / "app" / "fingrind.jar"
    jar_manifest = bundled_jar_manifest_attributes(application_jar)
    require(
        jar_manifest.get("Automatic-Module-Name") == "dev.erst.fingrind.cli",
        "bundled application JAR did not publish the canonical automatic module identity",
    )
    require(
        jar_manifest.get("Main-Class") == "dev.erst.fingrind.cli.App",
        "bundled application JAR did not publish the canonical main class",
    )

    launcher_text = normalize_newlines(launcher_path.read_text(encoding="utf-8"))
    require_match(
        launcher_text,
        r"--enable-native-access=dev\.erst\.fingrind\.cli,dev\.erst\.fingrind\.core",
        "bundle launcher did not grant native access to the canonical module identity",
    )
    require_match(
        launcher_text,
        r"--add-opens=java\.base/java\.nio=dev\.erst\.fingrind\.cli",
        "bundle launcher did not open java.base/java.nio to the canonical module identity",
    )
    require_match(
        launcher_text,
        r"--add-exports=java\.base/sun\.nio=dev\.erst\.fingrind\.cli",
        "bundle launcher did not export java.base/sun.nio to the canonical module identity",
    )
    require_match(
        launcher_text,
        r"dev\.erst\.fingrind\.cli/dev\.erst\.fingrind\.cli\.App",
        "bundle launcher did not declare the canonical JPMS application module identity",
    )
    require_match(
        launcher_text,
        r"--module",
        "bundle launcher did not target the canonical JPMS application module",
    )
    require_no_match(
        launcher_text,
        r"\{\{[A-Za-z0-9]+\}\}",
        "bundle launcher contained unresolved template placeholders",
    )


def bundled_jar_manifest_attributes(application_jar: Path) -> dict[str, str]:
    require(application_jar.is_file(), f"missing bundled application JAR at {application_jar}")
    with zipfile.ZipFile(application_jar) as jar_file:
        manifest_text = normalize_newlines(jar_file.read("META-INF/MANIFEST.MF").decode("utf-8"))

    attributes: dict[str, str] = {}
    current_key: str | None = None
    for raw_line in manifest_text.splitlines():
        if not raw_line:
            continue
        if raw_line.startswith(" "):
            require(
                current_key is not None,
                "bundled application JAR manifest began one continuation line without one attribute",
            )
            attributes[current_key] += raw_line[1:]
            continue
        key, separator, value = raw_line.partition(":")
        require(
            separator == ":" and key.strip(),
            "bundled application JAR manifest contained one malformed attribute line",
        )
        current_key = key
        attributes[current_key] = value.lstrip()
    return attributes
