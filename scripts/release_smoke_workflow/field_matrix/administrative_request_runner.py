"""Request-file invocation adapters for administrative mutations and preflight."""

from __future__ import annotations

from .administrative_models import AdministrativeWorld, JsonObject
from .administrative_operation_runner import _run_arguments_mutation, _run_operation
from .administrative_paths import _write_request
from .capabilities import OperationCapability


def _run_request_mutation(
    world: AdministrativeWorld,
    operation: OperationCapability,
    request: JsonObject,
    output_mode: str,
    label: str,
    *,
    extra_arguments: tuple[str, ...] = (),
) -> JsonObject | None:
    request_path = _write_request(world, label, request)
    return _run_arguments_mutation(
        world,
        operation,
        ("--request-file", request_path.argument, *extra_arguments),
        output_mode,
        label,
        request=request,
    )


def _run_request_without_credentials(
    world: AdministrativeWorld,
    operation: OperationCapability,
    request: JsonObject,
    output_mode: str,
    label: str,
) -> JsonObject | None:
    request_path = _write_request(world, label, request)
    return _run_operation(
        world,
        operation,
        (
            "--book-file",
            world.config.book.argument,
            "--book-key-file",
            world.config.book_key.argument,
            "--request-file",
            request_path.argument,
        ),
        output_mode,
        label,
        request=request,
    )
