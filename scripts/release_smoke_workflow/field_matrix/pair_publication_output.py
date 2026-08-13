"""Journal-backed protected-book pair-publication assertions for the field matrix."""

from __future__ import annotations

from collections.abc import Mapping

from ..artifact_contracts import reported_artifact_path_matches
from ..models import ReleaseSmokeConfig, SmokePath
from ..support import require
from .administrative_constants import _JSON_MODE, _TEXT_MODE
from .administrative_models import JsonObject
from .artifact_publication_evidence import require_publication_transaction_evidence


def require_maintenance_pair_publication_transaction(
    output_mode: str,
    output: str,
    envelope: JsonObject | None,
    config: ReleaseSmokeConfig,
    label: str,
    book_publication: SmokePath,
    generated_secret_publication: SmokePath,
) -> None:
    """Require final-only pair facts bound to one complete ID-only transaction result."""
    if output_mode == _JSON_MODE:
        require(
            envelope is not None,
            f"{config.label} {label} did not expose a JSON pair-publication envelope",
        )
        if envelope is None:
            raise AssertionError("JSON pair publication requires an envelope")
        payload = envelope.get("payload")
        require(
            isinstance(payload, dict),
            f"{config.label} {label} did not expose a JSON pair-publication payload",
        )
        if not isinstance(payload, dict):
            raise TypeError("JSON pair publication requires a payload object")
        require(
            payload.get("pairPublicationCompletion") == "published",
            f"{config.label} {label} did not report a freshly published protected-book pair",
        )
        raw_publication = payload.get("pairPublication")
        require(
            isinstance(raw_publication, dict),
            f"{config.label} {label} did not publish final-only pairPublication facts",
        )
        if not isinstance(raw_publication, dict):
            raise TypeError("JSON pair publication requires a pair publication object")
        _require_json_pair_member_publication(
            raw_publication, "bookPublication", book_publication, config, label
        )
        _require_json_pair_member_publication(
            raw_publication,
            "generatedSecretPublication",
            generated_secret_publication,
            config,
            label,
        )
        require(
            "retainedStage" not in raw_publication and "pairPublicationRetention" not in payload,
            f"{config.label} {label} exposed a legacy retained stage after transaction migration",
        )
        require_publication_transaction_evidence(
            config, raw_publication.get("publicationTransaction"), label
        )
        return
    if output_mode == _TEXT_MODE:
        _require_text_pair_member_publication(
            output, "Published book file", book_publication, config, label
        )
        _require_text_pair_member_publication(
            output,
            "Published generated-secret file",
            generated_secret_publication,
            config,
            label,
        )
        transaction_id = single_labeled_text_value(
            output,
            "Publication transaction",
            f"{config.label} {label} did not publish one publication transaction identifier",
        )
        require(
            bool(transaction_id.strip()),
            f"{config.label} {label} published a blank publication transaction identifier",
        )
        require(
            "retained stage" not in output.lower(),
            f"{config.label} {label} exposed a private retained stage in text output",
        )
        return
    raise AssertionError(f"unrouted pair-publication output mode: {output_mode}")


def _require_json_pair_member_publication(
    publication: Mapping[str, object],
    member_name: str,
    expected_artifact: SmokePath,
    config: ReleaseSmokeConfig,
    label: str,
) -> None:
    raw_member = publication.get(member_name)
    require(
        isinstance(raw_member, dict),
        f"{config.label} {label} did not publish {member_name} as one final-only fact",
    )
    if not isinstance(raw_member, dict):
        raise TypeError("pair publication member requires an object")
    reported_path = raw_member.get("path")
    require(
        isinstance(reported_path, str) and bool(reported_path.strip()),
        f"{config.label} {label} did not publish {member_name}.path",
    )
    if not isinstance(reported_path, str):
        raise TypeError("pair publication member requires a path")
    require(
        reported_artifact_path_matches(config, expected_artifact, reported_path),
        f"{config.label} {label} did not bind {member_name} to its requested final artifact",
    )
    require(
        "retainedStage" not in raw_member,
        f"{config.label} {label} exposed {member_name}.retainedStage after transaction migration",
    )


def _require_text_pair_member_publication(
    output: str,
    path_label: str,
    expected_artifact: SmokePath,
    config: ReleaseSmokeConfig,
    label: str,
) -> None:
    reported_path = single_labeled_text_value(
        output, path_label, f"{config.label} {label} did not publish one {path_label} fact"
    )
    require(
        reported_artifact_path_matches(config, expected_artifact, reported_path),
        f"{config.label} {label} did not bind {path_label} to its requested final artifact",
    )


def single_labeled_text_value(output: str, label: str, message: str) -> str:
    """Read exactly one potentially wrapped labeled value from text output."""
    values: list[str] = []
    lines = output.splitlines()
    for index, line in enumerate(lines):
        prefix, separator, value = line.partition(":")
        if not separator or prefix.rstrip() != label or not value.startswith(" "):
            continue
        continuation_prefix = " " * (len(prefix) + 3)
        value_lines = [value[1:]]
        for continuation in lines[index + 1 :]:
            if not continuation.startswith(continuation_prefix):
                break
            value_lines.append(continuation.removeprefix(continuation_prefix))
        values.append(" ".join(value_lines))
    require(len(values) == 1, message)
    if len(values) != 1:
        raise AssertionError("artifact text publication requires exactly one labeled path")
    return values[0]
