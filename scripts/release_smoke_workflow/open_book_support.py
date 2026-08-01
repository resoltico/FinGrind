from __future__ import annotations

from .attestation_arguments import founder_credential_arguments
from .cli import run_cli, run_cli_allow_failure
from .models import ReleaseSmokeConfig, ReleaseSmokeFailure, SmokePath
from .support import require


def open_book(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    *,
    output_mode: str = "json",
) -> str:
    arguments, stdin_text = open_book_arguments(
        config,
        operation_ids,
        book=config.book,
        book_key=config.book_key,
        output_mode=output_mode,
    )
    return run_cli(config, *arguments, stdin_text=stdin_text)


def attempt_open_book(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    *,
    book: SmokePath,
    book_key: SmokePath,
    output_mode: str = "json",
) -> tuple[str, int]:
    arguments, stdin_text = open_book_arguments(
        config,
        operation_ids,
        book=book,
        book_key=book_key,
        output_mode=output_mode,
    )
    return run_cli_allow_failure(config, *arguments, stdin_text=stdin_text)


def open_book_arguments(
    config: ReleaseSmokeConfig,
    operation_ids: dict[str, str],
    *,
    book: SmokePath,
    book_key: SmokePath,
    output_mode: str,
) -> tuple[tuple[str, ...], str | None]:
    if config.open_book_mode == "generated-key-stdin":
        generated_passphrase = book_key.local_path.read_text(encoding="utf-8")
        require(
            bool(generated_passphrase),
            f"{config.label} generated an empty key file",
        )
        return (
            (
                operation_ids["openBook"],
                "--book-file",
                book.argument,
                "--entity-name",
                config.entity_name,
                "--book-template-id",
                config.book_template_id,
                "--accounting-basis",
                config.accounting_basis,
                *_inventory_costing_arguments(config),
                "--functional-currency",
                config.functional_currency,
                "--fiscal-year-start",
                config.fiscal_year_start,
                "--book-start-effective-date",
                config.book_start_effective_date,
                "--book-passphrase-stdin",
                *founder_credential_arguments(config),
                "--output",
                output_mode,
            ),
            generated_passphrase,
        )
    if config.open_book_mode == "book-key-file":
        return (
            (
                operation_ids["openBook"],
                "--book-file",
                book.argument,
                "--entity-name",
                config.entity_name,
                "--book-template-id",
                config.book_template_id,
                "--accounting-basis",
                config.accounting_basis,
                *_inventory_costing_arguments(config),
                "--functional-currency",
                config.functional_currency,
                "--fiscal-year-start",
                config.fiscal_year_start,
                "--book-start-effective-date",
                config.book_start_effective_date,
                "--book-key-file",
                book_key.argument,
                *founder_credential_arguments(config),
                "--output",
                output_mode,
            ),
            None,
        )
    raise ReleaseSmokeFailure(
        f"{config.label} configured unsupported open-book mode: {config.open_book_mode}"
    )


def _inventory_costing_arguments(config: ReleaseSmokeConfig) -> tuple[str, ...]:
    if config.inventory_costing_doctrine is None:
        return ()
    return ("--inventory-costing", config.inventory_costing_doctrine)
