from __future__ import annotations

import json
import pathlib
from types import SimpleNamespace

from .assertions import expected_reported_path
from .models import ReleaseSmokeFailure, SmokePath
from .support import require, require_labeled_text_value, require_rejected_json_diagnostic


def assert_contract_projection_assertions() -> None:
    assert_reported_path_projection_contract()
    assert_labeled_text_value_contract()


def assert_reported_path_projection_contract() -> None:
    relative_path = pathlib.Path("books odd/Rīga büro/nested/format-boundary/retired.sqlite")
    local_path = pathlib.Path("/private/release-smoke") / relative_path
    host_path = SmokePath(relative_path, local_path, str(local_path))
    container_path = SmokePath(relative_path, local_path, relative_path.as_posix())

    require(
        expected_reported_path(SimpleNamespace(reported_work_root=None), host_path)
        == str(local_path),
        "absolute release-smoke arguments did not retain the host-visible reported path",
    )
    require(
        expected_reported_path(
            SimpleNamespace(reported_work_root=pathlib.Path("/workdir")), container_path
        )
        == str(pathlib.Path("/workdir") / relative_path),
        "relative container release-smoke arguments did not project to the container-visible path",
    )
    require(
        expected_reported_path(
            SimpleNamespace(reported_work_root=pathlib.Path("/workdir")), host_path
        )
        == str(local_path),
        "absolute release-smoke arguments were incorrectly remapped to the container work root",
    )


def assert_labeled_text_value_contract() -> None:
    wrapped_output = (
        "Rejected\n"
        "========\n\n"
        "Code           : synthetic-diagnostic-code\n"
        "Message        : Synthetic diagnostic message with an exact\n"
        "                  terminal.\n"
        "Hint           : Synthetic diagnostic remediation with an exact terminal.\n"
    )
    exact_message = "Synthetic diagnostic message with an exact terminal."
    exact_hint = "Synthetic diagnostic remediation with an exact terminal."
    require_labeled_text_value(
        wrapped_output,
        "Message",
        exact_message,
        "wrapped diagnostic message did not reconstruct its exact logical value",
    )
    require_labeled_text_value(
        wrapped_output,
        "Hint",
        exact_hint,
        "diagnostic hint did not preserve its exact value",
    )
    rejected_json = json.dumps(
        {
            "status": "rejected",
            "code": "synthetic-diagnostic-code",
            "message": exact_message,
            "hint": exact_hint,
        }
    )
    require_rejected_json_diagnostic(
        rejected_json,
        "synthetic-diagnostic-code",
        exact_message,
        exact_hint,
        "exact rejected diagnostic regression",
    )
    try:
        require_labeled_text_value(
            wrapped_output,
            "Message",
            exact_message[:-1] + "X",
            "exact diagnostic comparison accepted a different terminal character",
        )
    except ReleaseSmokeFailure:
        pass
    else:
        raise AssertionError("exact diagnostic comparison accepted a different terminal character")
    try:
        require_rejected_json_diagnostic(
            rejected_json,
            "synthetic-diagnostic-code",
            exact_message[:-1] + "X",
            exact_hint,
            "exact rejected diagnostic regression",
        )
    except ReleaseSmokeFailure:
        return
    raise AssertionError("exact JSON diagnostic comparison accepted a different terminal character")
