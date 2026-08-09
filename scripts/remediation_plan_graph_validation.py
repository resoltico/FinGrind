"""Public-plan graph and R63 release-protocol validation."""

from __future__ import annotations

from remediation_plan_support import JsonValue, RemediationError

LATEST_VARIANTS = frozenset({"LATEST_POINTER_MOVE", "LATEST_VERIFY"})
R63_CHAIN = (
    "R63-ADMISSION",
    "R63-PR-GATE",
    "R63-MERGE",
    "R63-POST-MERGE-GATE",
    "R63-PRE-TAG-VERIFY",
    "R63-TAG-PUSH",
    "R63-TAG-HANDOFF-VERIFY",
    "R63-BUNDLES",
    "R63-ASSET-UPLOAD",
    "R63-ASSET-ATTESTATIONS",
    "R63-DRAFT-ASSET-VERIFY",
    "R63-STAGING-CONTAINERS",
    "R63-UAT",
    "R63-OCI-DIGEST-PUBLISH",
    "R63-OCI-DIGEST-VERIFY",
    "R63-LATEST-POLICY",
    "R63-LATEST-POINTER-MOVE",
    "R63-LATEST-VERIFY",
    "R63-FINALIZATION-PRECHECK",
    "R63-RELEASE-FINAL",
    "R63-PUBLIC-VERIFY",
)


def validate_graph(records: dict[str, dict[str, JsonValue]]) -> None:
    """Prove public dependencies are exact, acyclic, and release-reachable."""
    edges = _edges(records)
    _assert_acyclic(edges)
    _assert_reachable(records, edges)
    _validate_r63_release_protocol(records, edges)


def _edges(records: dict[str, dict[str, JsonValue]]) -> dict[str, set[str]]:
    edges: dict[str, set[str]] = {}
    for identifier, record in records.items():
        if "dependsOn" not in record:
            continue
        payload = record.get("payload")
        if not isinstance(payload, dict):
            raise RemediationError(f"node payload is malformed: {identifier}")
        dependencies = record.get("dependsOn")
        if not isinstance(dependencies, list) or any(
            not isinstance(item, str) for item in dependencies
        ):
            raise RemediationError(f"node dependencies are malformed: {identifier}")
        references = [*dependencies, *_payload_references(identifier, record, payload)]
        missing = [reference for reference in references if reference not in records]
        if missing:
            raise RemediationError(
                f"public graph has dangling reference: {identifier} -> {missing[0]}"
            )
        edges[identifier] = set(dependencies)
    return edges


def _payload_references(
    identifier: str, record: dict[str, JsonValue], payload: dict[str, JsonValue]
) -> list[str]:
    references = _string_values(payload, "workUnit", "preMergeVerificationRef", "release")
    references.extend(_id_set(payload, "guardRefs", identifier, "node guard references"))
    subject = payload.get("subjectRequirement")
    if isinstance(subject, dict):
        references.extend(_string_values(subject, "implementation", "release", "step"))
    elif subject is not None:
        raise RemediationError(f"node verification subject is malformed: {identifier}")
    target = payload.get("deliveryTarget")
    if isinstance(target, dict):
        references.extend(_string_values(target, "controlChange", "release"))
    references.extend(_id_set(payload, "membership", identifier, "release membership"))
    if record.get("kind") == "RELEASE_STEP":
        _validate_applicability(identifier, payload)
    return references


def _string_values(value: dict[str, JsonValue], *keys: str) -> list[str]:
    return [item for key in keys if isinstance(item := value.get(key), str)]


def _id_set(
    payload: dict[str, JsonValue], key: str, identifier: str, description: str
) -> list[str]:
    value = payload.get(key)
    if value is None:
        return []
    if not isinstance(value, list) or any(not isinstance(item, str) for item in value):
        raise RemediationError(f"{description} are malformed: {identifier}")
    return value


def _validate_applicability(identifier: str, payload: dict[str, JsonValue]) -> None:
    variant = payload.get("variant")
    status = payload.get("status")
    authorized = payload.get("notApplicableAuthorized")
    reason = payload.get("notApplicableReason")
    if not isinstance(variant, str) or not isinstance(authorized, bool):
        raise RemediationError(f"release-step applicability is malformed: {identifier}")
    if authorized != (variant in LATEST_VARIANTS):
        raise RemediationError(f"release-step applicability authority is wrong: {identifier}")
    if status == "NOT_APPLICABLE":
        if not authorized or not isinstance(reason, str) or not reason.strip():
            raise RemediationError(f"release-step non-applicability is unauthorized: {identifier}")
    elif reason is not None:
        raise RemediationError(
            f"release-step has a non-applicability reason while applicable: {identifier}"
        )


def _assert_acyclic(edges: dict[str, set[str]]) -> None:
    visited: set[str] = set()
    active: set[str] = set()

    def visit(identifier: str) -> None:
        if identifier in active:
            raise RemediationError(f"public graph has a cycle at {identifier}")
        if identifier in visited:
            return
        active.add(identifier)
        for dependency in edges.get(identifier, set()):
            visit(dependency)
        active.remove(identifier)
        visited.add(identifier)

    for identifier in edges:
        visit(identifier)


def _assert_reachable(records: dict[str, dict[str, JsonValue]], edges: dict[str, set[str]]) -> None:
    roots = [
        identifier
        for identifier, record in records.items()
        if record.get("kind") == "RELEASE_RESERVATION" and identifier in edges
    ]
    if not roots:
        raise RemediationError("public plan lacks a release-reservation graph root")
    reachable: set[str] = set()

    def mark(identifier: str) -> None:
        if identifier in reachable:
            return
        reachable.add(identifier)
        for dependency in edges.get(identifier, set()):
            mark(dependency)

    for root in roots:
        mark(root)
    live_edges = {identifier for identifier in edges if not identifier.startswith("P0-")}
    orphaned = sorted(live_edges - reachable)
    if orphaned:
        raise RemediationError(f"public plan has an unreachable graph node: {orphaned[0]}")


def _validate_r63_release_protocol(
    records: dict[str, dict[str, JsonValue]], edges: dict[str, set[str]]
) -> None:
    if "R63" not in records:
        return
    expected = {
        R63_CHAIN[0]: {"W1-PRE-MERGE-V", "W4A-PRE-MERGE-V"},
        R63_CHAIN[3]: {R63_CHAIN[2], "W1-POST-MERGE-V", "W4A-POST-MERGE-V"},
        R63_CHAIN[4]: {R63_CHAIN[3], "R63-CANDIDATE"},
        "R63-CANDIDATE": {R63_CHAIN[2]},
        "R63-LAYER5-V": {R63_CHAIN[-1]},
        "R63-COMPLETE": {"R63-LAYER5-V"},
        "R63": {"R63-COMPLETE"},
    }
    expected.update(
        {R63_CHAIN[index]: {R63_CHAIN[index - 1]} for index in range(1, len(R63_CHAIN))}
    )
    expected[R63_CHAIN[3]] = {R63_CHAIN[2], "W1-POST-MERGE-V", "W4A-POST-MERGE-V"}
    expected[R63_CHAIN[4]] = {R63_CHAIN[3], "R63-CANDIDATE"}
    for identifier, dependencies in expected.items():
        if edges.get(identifier) != dependencies:
            raise RemediationError(f"R63 release chain drifted at {identifier}")
    reservation = records["R63"].get("payload")
    if not isinstance(reservation, dict):
        raise RemediationError("R63 release reservation payload is malformed")
    members = reservation.get("membership")
    expected_members = {
        *R63_CHAIN,
        "R63-CANDIDATE",
        "R63-LAYER5-V",
        "R63-COMPLETE",
        "W1-D",
        "W1-I",
        "W1-PRE-MERGE-V",
        "W1-POST-MERGE-V",
        "W4A-D",
        "W4A-I",
        "W4A-PRE-MERGE-V",
        "W4A-POST-MERGE-V",
    }
    if not isinstance(members, list) or set(members) != expected_members:
        raise RemediationError("R63 release reservation membership drifted")
