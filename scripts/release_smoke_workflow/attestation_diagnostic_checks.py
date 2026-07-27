from __future__ import annotations

from .cli import run_cli_allow_failure
from .models import ReleaseSmokeConfig
from .support import require, require_labeled_text_value, require_rejected_json_diagnostic


def require_exact_rejected_diagnostic(
    config: ReleaseSmokeConfig,
    command_arguments: tuple[str, ...],
    code: str,
    message: str,
    hint: str,
    label: str,
) -> None:
    text_output, text_exit_code = run_cli_allow_failure(
        config, *command_arguments, "--output", "text"
    )
    require(text_exit_code == 2, f"{label} text output did not exit 2")
    require_labeled_text_value(
        text_output, "Code", code, f"{label} text output did not report {code}"
    )
    require_labeled_text_value(
        text_output,
        "Message",
        message,
        f"{label} text output did not report its exact message",
    )
    require_labeled_text_value(
        text_output,
        "Hint",
        hint,
        f"{label} text output did not report its exact remediation",
    )
    json_output, json_exit_code = run_cli_allow_failure(
        config, *command_arguments, "--output", "json"
    )
    require(json_exit_code == 2, f"{label} JSON output did not exit 2")
    require_rejected_json_diagnostic(json_output, code, message, hint, f"{label} JSON output")
