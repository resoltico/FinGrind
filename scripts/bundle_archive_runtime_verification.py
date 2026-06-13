from __future__ import annotations

import shutil
from pathlib import Path

from bundle_archive_contract_support import (
    bundled_java_command,
    joined_path,
    normalize_newlines,
    normalized_command_output,
    require,
    require_match,
    require_no_match,
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
    host_bundle_target = contract["bundleLayout"]["hostBundleTarget"]
    assert isinstance(host_bundle_target, dict)
    launcher_path = joined_path(bundle_root, str(host_bundle_target["launcherPath"]))
    application_jar = bundle_root / "lib" / "app" / "fingrind.jar"
    jar_command = shutil.which("jar")
    require(
        jar_command is not None,
        "missing host jar command while verifying the bundled module identity",
    )

    describe_output = normalized_command_output(
        [jar_command, "--describe-module", "--file", str(application_jar)],
    )
    require_match(
        describe_output,
        r"^dev\.erst\.fingrind\.cli automatic$",
        "bundled application JAR did not publish the canonical automatic module identity",
    )
    require_match(
        describe_output,
        r"^main-class dev\.erst\.fingrind\.cli\.App$",
        "bundled application JAR did not publish the canonical main class",
    )

    launcher_text = normalize_newlines(launcher_path.read_text(encoding="utf-8"))
    require_match(
        launcher_text,
        r"--enable-native-access=dev\.erst\.fingrind\.cli",
        "bundle launcher did not grant native access to the canonical module identity",
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
