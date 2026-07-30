from __future__ import annotations

import pathlib

import contract_values
from contract_values_regression_support import (
    bundle_layout_targets,
    operation_id_contract_payload,
    publication_entry,
    read_json,
    write_bundle_publication_contract,
    write_json,
)


def assert_fixture_publication_contract(
    fixture_root: pathlib.Path, protocol_root: pathlib.Path
) -> None:
    write_json(
        protocol_root / "bundle-layout-contract.json",
        {"bundleTargets": bundle_layout_targets()},
    )
    write_bundle_publication_contract(
        protocol_root,
        {
            "linux-x86_64": publication_entry("published"),
            "windows-aarch64": publication_entry("experimental"),
        },
    )
    try:
        contract_values.load_contract_values(fixture_root, os_name="Linux", architecture="x86_64")
    except ValueError as exc:
        assert "unsupported publication status" in str(exc)
    else:
        raise AssertionError("expected unsupported publication status validation failure")

    write_json(
        protocol_root / "bundle-layout-contract.json",
        {"bundleTargets": bundle_layout_targets(include_linux_aarch64=True)},
    )
    write_bundle_publication_contract(
        protocol_root,
        {
            "linux-x86_64": publication_entry("published"),
            "linux-aarch64": publication_entry("published"),
            "windows-aarch64": publication_entry("not-published"),
        },
    )
    write_json(
        protocol_root / "release-publication-contract.json",
        {
            **read_json(protocol_root / "release-publication-contract.json"),
            "containerPlatforms": ["linux/amd64"],
        },
    )
    try:
        contract_values.load_contract_values(fixture_root, os_name="Linux", architecture="x86_64")
    except ValueError as exc:
        assert "containerPlatforms must match the supported Linux public bundle targets" in str(exc)
    else:
        raise AssertionError("expected release container-platform validation failure")

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
            "containerPlatforms": ["linux/amd64", "linux/arm64"],
            "latestPublicationPolicy": "newest-stable-release-only",
        },
    )
    write_json(
        protocol_root / "operation-id-contract.json",
        {
            **read_json(protocol_root / "operation-id-contract.json"),
            "SURPRISE__OPERATION": "surprise-operation",
        },
    )
    try:
        contract_values.load_contract_values(fixture_root, os_name="Linux", architecture="x86_64")
    except ValueError as exc:
        assert "must be one non-blank upper-snake enum name" in str(exc)
    else:
        raise AssertionError("expected malformed operation-id enum-key validation failure")

    write_json(
        protocol_root / "operation-id-contract.json",
        operation_id_contract_payload(),
    )
    (
        fixture_root
        / "contract/build/generated-resources/protocol/dev/erst/fingrind/contract/protocol/runtime-environment-contract.json"
    ).unlink()
    loaded_from_build_metadata = contract_values.load_contract_values(
        fixture_root, os_name="Linux", architecture="x86_64"
    )
    assert loaded_from_build_metadata["runtimeEnvironment"]["sourceCheckoutJava"] == "26+"
