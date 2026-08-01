from __future__ import annotations

from .cli import run_cli
from .models import ReleaseSmokeConfig, SmokePath
from .support import parse_json_output, require


def successful_payload(
    output: str,
    config: ReleaseSmokeConfig,
    operation: str,
) -> dict[str, object]:
    envelope = parse_json_output(output, f"{config.label} {operation} output was not valid JSON")
    require(envelope.get("status") == "ok", f"{config.label} {operation} did not report ok status")
    payload = envelope.get("payload")
    require(
        isinstance(payload, dict),
        f"{config.label} {operation} did not expose a payload object",
    )
    return payload


def mutate_with_request(
    config: ReleaseSmokeConfig,
    operation: str,
    request: SmokePath,
    credentials: list[str],
    label: str,
) -> None:
    successful_payload(
        run_cli(
            config,
            operation,
            "--book-file",
            config.book.argument,
            "--book-key-file",
            config.book_key.argument,
            "--request-file",
            request.argument,
            *credentials,
            "--output",
            "json",
        ),
        config,
        label,
    )
