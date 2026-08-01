"""Strict JSON protocol and GitHub-output policy for Windows publication verification."""

from __future__ import annotations

import re

from windows_publication_manifest_policy import validate_manifest_artifacts
from windows_publication_plan_policy import build_publication_plan
from windows_publication_policy_boundary import (
    PublicationPolicyError,
    require_only_properties,
    required_object,
    required_string,
    required_text,
)

_WORKFLOW_OUTPUT_NAME = re.compile(r"[A-Za-z_][A-Za-z0-9_-]*")


def serialize_workflow_output(*, name: str, value: str) -> str:
    """Return one GitHub workflow-output record using the portable LF wire format."""

    if not isinstance(name, str) or not _WORKFLOW_OUTPUT_NAME.fullmatch(name):
        raise PublicationPolicyError(f"workflow output name is invalid: {name}")
    if not isinstance(value, str):
        raise PublicationPolicyError(f"workflow output {name} must be one line")
    if "\r" in value or "\n" in value:
        raise PublicationPolicyError(f"workflow output {name} must be one line")
    return f"{name}={value}\n"


def process_request(request: dict[str, object]) -> dict[str, str]:
    """Dispatch one strict JSON request from the native adapter."""

    operation = required_text(request, "operation", "Windows publication policy request")
    if operation == "publication-plan":
        require_only_properties(
            request,
            (
                "operation",
                "repositoryRoot",
                "gradleProperties",
                "bundleLayoutContract",
                "expectedOperatingSystemId",
                "expectedArchitectureId",
                "bundleClassifier",
            ),
            "Windows publication policy request",
        )
        return build_publication_plan(
            repository_root=required_text(request, "repositoryRoot", "request"),
            gradle_properties=required_text(request, "gradleProperties", "request"),
            bundle_layout_contract=required_text(request, "bundleLayoutContract", "request"),
            expected_operating_system_id=required_text(
                request, "expectedOperatingSystemId", "request"
            ),
            expected_architecture_id=required_text(request, "expectedArchitectureId", "request"),
            bundle_classifier=required_text(request, "bundleClassifier", "request"),
        )
    if operation == "manifest-artifacts":
        require_only_properties(
            request,
            ("operation", "plan", "bundleArchiveManifest"),
            "Windows publication policy request",
        )
        return validate_manifest_artifacts(
            plan=required_object(request, "plan", "request"),
            bundle_archive_manifest=required_text(request, "bundleArchiveManifest", "request"),
        )
    if operation == "workflow-output-line":
        require_only_properties(
            request,
            ("operation", "name", "value"),
            "Windows publication policy request",
        )
        return {
            "line": serialize_workflow_output(
                name=required_text(request, "name", "request"),
                value=required_string(request, "value", "request"),
            )
        }
    raise PublicationPolicyError(f"unsupported Windows publication policy operation: {operation}")
