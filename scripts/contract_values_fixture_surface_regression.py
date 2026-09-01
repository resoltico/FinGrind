from __future__ import annotations

import pathlib

import contract_values
import release_publication_contract
from contract_values_regression_support import (
    assert_declared_fixture_formats_are_current,
    publication_entry,
    read_json,
    write_bundle_publication_contract,
    write_json,
)


def assert_fixture_surface_contract(
    fixture_root: pathlib.Path, protocol_root: pathlib.Path
) -> None:
    loaded = contract_values.load_contract_values(
        fixture_root, os_name="Windows 11", architecture="ARM64"
    )
    assert loaded["managedSqlite"]["requiredMinimumSqliteVersion"] == "3.53.4"
    assert loaded["protectedBookFormat"] == {
        "applicationId": 1_179_079_236,
        "formatVersion": 17,
        "cipher": "chacha20",
        "legacyMode": False,
        "pageSize": 4096,
        "reservedBytes": 32,
        "legacyPageSize": 4096,
        "kdfIter": 64007,
        "plaintextHeaderSize": 0,
    }
    assert loaded["managedSqlite"]["requiredSqlite3mcVersion"] == "2.5.1"
    assert loaded["managedSqlite"]["requiredSqliteSourceId"] == "2026-04-09 sqlite-source-id"
    assert loaded["managedSqlite"]["requiredCompileOptions"] == [
        "THREADSAFE=1",
        "OMIT_LOAD_EXTENSION",
        "TEMP_STORE=3",
        "SECURE_DELETE",
    ]
    assert loaded["managedSqlite"]["forbiddenCompileOptions"] == ["USE_URI"]
    assert loaded["managedSqlite"]["requiresSecureMemorySupport"] is True
    assert loaded["runtimeEnvironment"]["sourceCheckoutJava"] == "26+"
    assert loaded["bundleLayout"]["hostBundleTarget"]["classifier"] == "windows-aarch64"
    assert loaded["bundleLayout"]["hostBundleTarget"]["archiveFormat"] == "zip"
    assert loaded["bundleLayout"]["hostBundleTarget"]["launcherPath"] == "bin/fingrind.ps1"
    assert (
        loaded["bundleLayout"]["targets"]["linux-x86_64"]["compatibilityLabel"]
        == "glibc 2.34+ Linux x86_64"
    )
    assert loaded["bundleLayout"]["targets"]["linux-x86_64"]["minimumGlibcVersion"] == "2.34"
    assert (
        loaded["bundleLayout"]["targets"]["linux-x86_64"]["compatibilitySmokeContainerImage"]
        == "rockylinux:9@sha256:floor-proof"
    )
    assert loaded["publicDistribution"]["unsupportedPublicCliBundleTargets"] == ["windows-aarch64"]
    assert loaded["releasePublication"]["requiredCiWorkflowName"] == "CI"
    assert (
        loaded["releasePublication"]["containerStagingImageName"] == "fingrind-publication-staging"
    )
    assert loaded["releasePublication"]["containerPlatforms"] == ["linux/amd64"]
    assert loaded["operationIds"]["version"] == "version"
    assert loaded["operationIds"]["environment"] == "environment"
    assert loaded["operationIds"]["generateBookKeyFile"] == "generate-book-key-file"
    assert loaded["operationIds"]["declareTaxRegistration"] == "declare-tax-registration"
    assert loaded["operationIds"]["listTaxRegistrations"] == "list-tax-registrations"
    assert loaded["operationIds"]["taxObligation"] == "tax-obligation"
    assert loaded["operationIds"]["cashFlowStatement"] == "cash-flow-statement"

    bundle_layout_with_trailing_path_space = read_json(
        protocol_root / "bundle-layout-contract.json"
    )
    bundle_layout_with_trailing_path_space["bundleTargets"]["windows-aarch64"]["launcherPath"] = (
        "bin/fingrind.ps1 "
    )
    write_json(
        protocol_root / "bundle-layout-contract.json",
        bundle_layout_with_trailing_path_space,
    )
    try:
        contract_values.load_contract_values(
            fixture_root, os_name="Windows 11", architecture="ARM64"
        )
    except ValueError as exc:
        assert (
            "bundle layout target windows-aarch64 launcherPath must be one non-blank exact string"
            in str(exc)
        )
    else:
        raise AssertionError("expected bundle layout target path whitespace rejection")
    bundle_layout_with_trailing_path_space["bundleTargets"]["windows-aarch64"]["launcherPath"] = (
        "bin/fingrind.ps1"
    )
    write_json(
        protocol_root / "bundle-layout-contract.json",
        bundle_layout_with_trailing_path_space,
    )

    plan = release_publication_contract.load_release_publication_plan(
        fixture_root,
        version="9.9.9",
        os_name="Linux",
        architecture="x86_64",
    )
    assert plan["bundleTargets"] == [
        {
            "classifier": "linux-x86_64",
            "archiveExtension": "tar.gz",
            "operatingSystemId": "linux",
            "architectureId": "x86_64",
        }
    ]

    publication_with_retired_runner = read_json(protocol_root / "bundle-publication-contract.json")
    publication_with_retired_runner["bundleTargets"]["linux-x86_64"]["runnerLabel"] = "self-hosted"
    write_json(
        protocol_root / "bundle-publication-contract.json",
        publication_with_retired_runner,
    )
    try:
        contract_values.load_contract_values(fixture_root, os_name="Linux", architecture="x86_64")
    except ValueError as exc:
        assert (
            "bundle publication target linux-x86_64 must not declare unrecognized properties: runnerLabel"
            in str(exc)
        )
    else:
        raise AssertionError("expected retired bundle runner-label rejection")
    write_bundle_publication_contract(
        protocol_root,
        {
            "linux-x86_64": publication_entry("published"),
            "windows-aarch64": publication_entry("not-published"),
        },
    )

    release_with_retired_runner = read_json(protocol_root / "release-publication-contract.json")
    release_with_retired_runner["containerRunnerLabel"] = "self-hosted"
    write_json(
        protocol_root / "release-publication-contract.json",
        release_with_retired_runner,
    )
    try:
        contract_values.load_contract_values(fixture_root, os_name="Linux", architecture="x86_64")
    except ValueError as exc:
        assert (
            "release publication contract must not declare unrecognized properties: containerRunnerLabel"
            in str(exc)
        )
    else:
        raise AssertionError("expected retired container runner-label rejection")
    write_json(
        protocol_root / "release-publication-contract.json",
        {
            "requiredCiWorkflowName": "CI",
            "requiredCiWorkflowPath": ".github/workflows/ci.yml",
            "requiredCiGateJobName": "Gate",
            "requiredCiJobNames": ["Check", "Gate"],
            "containerRegistry": "ghcr.io",
            "containerImageName": "fingrind",
            "containerStagingImageName": "fingrind-publication-staging",
            "containerPlatforms": ["linux/amd64"],
            "latestPublicationPolicy": "newest-stable-release-only",
        },
    )

    write_bundle_publication_contract(
        protocol_root,
        {
            "linux-x86_64": publication_entry("published"),
            "windows-aarch64": publication_entry("published"),
        },
    )
    plan_with_unadmitted_target = release_publication_contract.load_release_publication_plan(
        fixture_root,
        version="9.9.9",
        os_name="Linux",
        architecture="x86_64",
    )
    assert {entry["classifier"] for entry in plan_with_unadmitted_target["bundleTargets"]} == {
        "linux-x86_64",
        "windows-aarch64",
    }
    assert all("runner" not in entry for entry in plan_with_unadmitted_target["bundleTargets"])
    write_bundle_publication_contract(
        protocol_root,
        {
            "linux-x86_64": publication_entry("published"),
            "windows-aarch64": publication_entry("not-published"),
        },
    )

    fixture_metadata_root = (
        fixture_root / "sqlite/src/test/resources/dev/erst/fingrind/sqlite/fixtures"
    )
    write_json(
        fixture_metadata_root / "current.metadata.json",
        {"bookFormatVersion": 17},
    )
    assert_declared_fixture_formats_are_current(fixture_metadata_root, 17)
    write_json(
        fixture_metadata_root / "retired.metadata.json",
        {"bookFormatVersion": 16},
    )
    try:
        assert_declared_fixture_formats_are_current(fixture_metadata_root, 17)
    except AssertionError as exc:
        assert "retired format 16" in str(exc)
    else:
        raise AssertionError("expected retired protected-book fixture format rejection")
