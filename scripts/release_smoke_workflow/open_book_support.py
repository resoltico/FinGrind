from __future__ import annotations

from .cli import run_cli
from .models import ReleaseSmokeConfig, ReleaseSmokeFailure
from .support import require


def open_book(config: ReleaseSmokeConfig, operation_ids: dict[str, str]) -> str:
    if config.open_book_mode == "generated-key-stdin":
        generated_passphrase = config.book_key.local_path.read_text(encoding="utf-8")
        require(
            bool(generated_passphrase),
            f"{config.label} generated an empty key file",
        )
        return run_cli(
            config,
            operation_ids["openBook"],
            "--book-file",
            config.book.argument,
            "--entity-name",
            config.entity_name,
            "--functional-currency",
            config.functional_currency,
            "--fiscal-year-start",
            config.fiscal_year_start,
            "--book-passphrase-stdin",
            stdin_text=generated_passphrase,
        )
    if config.open_book_mode == "book-key-file":
        return run_cli(
            config,
            operation_ids["openBook"],
            "--book-file",
            config.book.argument,
            "--entity-name",
            config.entity_name,
            "--functional-currency",
            config.functional_currency,
            "--fiscal-year-start",
            config.fiscal_year_start,
            "--book-key-file",
            config.book_key.argument,
        )
    raise ReleaseSmokeFailure(
        f"{config.label} configured unsupported open-book mode: {config.open_book_mode}"
    )
