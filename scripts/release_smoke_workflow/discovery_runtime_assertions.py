from __future__ import annotations

from typing import Any

from .models import ReleaseSmokeConfig
from .support import require, require_bool, require_string, required_list, required_mapping


def assert_protected_book_format_parity(
    label: str,
    runtime_format: dict[str, Any],
    canonical_format: dict[str, Any],
) -> None:
    """Require the live storage defaults to publish the complete book-format contract.

    A protected book is not merely a cipher and page-size choice.  Every field
    in the protocol-owned format object participates in its durable format
    identity, including the hard-break format version.  Exact map parity makes
    a newly added format fact a release-smoke obligation rather than an
    accidentally unverified detail.
    """
    missing_keys = sorted(set(canonical_format) - set(runtime_format))
    unexpected_keys = sorted(set(runtime_format) - set(canonical_format))
    mismatched_keys = sorted(
        key
        for key in set(canonical_format) & set(runtime_format)
        if runtime_format[key] != canonical_format[key]
    )
    require(
        not missing_keys and not unexpected_keys and not mismatched_keys,
        f"{label} environment output did not publish the exact protected-book format "
        "contract"
        + _protected_book_format_difference_suffix(
            missing_keys,
            unexpected_keys,
            mismatched_keys,
        ),
    )


def _protected_book_format_difference_suffix(
    missing_keys: list[str],
    unexpected_keys: list[str],
    mismatched_keys: list[str],
) -> str:
    differences: list[str] = []
    if missing_keys:
        differences.append("missing " + ", ".join(missing_keys))
    if unexpected_keys:
        differences.append("unexpected " + ", ".join(unexpected_keys))
    if mismatched_keys:
        differences.append("mismatched " + ", ".join(mismatched_keys))
    return ": " + "; ".join(differences) if differences else ""


def assert_discovery_surface(
    config: ReleaseSmokeConfig,
    payload: dict[str, Any],
    runtime_surface_payload: dict[str, Any],
    publication_surface: dict[str, Any],
    storage: dict[str, Any],
    sqlite: dict[str, Any],
    runtime_surface: dict[str, Any],
    protected_book_format: dict[str, Any],
    public_distribution: dict[str, Any],
    request_input: dict[str, Any],
) -> None:
    runtime_distribution = require_string(runtime_surface, config.runtime_distribution_key)
    require(
        require_string(payload, "detail") == "full",
        f"{config.label} capabilities output did not expose the exhaustive full discovery contract",
    )
    require(
        require_string(runtime_surface_payload, "runtimeDistribution") == runtime_distribution,
        f"{config.label} environment output did not report the canonical runtime distribution",
    )
    require(
        require_string(publication_surface, "publicCliDistribution")
        == require_string(runtime_surface, "publicCliDistribution"),
        f"{config.label} environment output did not report the public CLI distribution contract",
    )
    for key, message in (
        ("supportedPublicCliBundleTargets", "supported public bundle targets"),
        ("unsupportedPublicCliBundleTargets", "current unsupported public bundle targets"),
    ):
        require(
            required_list(publication_surface, key) == required_list(public_distribution, key),
            f"{config.label} environment output did not report the {message}",
        )
    require(
        require_string(storage, "storageDriver")
        == require_string(runtime_surface, "storageDriver"),
        f"{config.label} environment output did not report the SQLite3 Multiple Ciphers storage driver",
    )
    require(
        require_string(storage, "bookProtectionMode")
        == require_string(runtime_surface, "bookProtectionMode"),
        f"{config.label} environment output did not report required book protection",
    )
    storage_format = required_mapping(storage, "defaultProtectedBookFormat")
    assert_protected_book_format_parity(
        config.label,
        storage_format,
        protected_book_format,
    )
    require(
        require_string(sqlite, "libraryMode")
        == require_string(runtime_surface, "sqliteLibraryMode"),
        f"{config.label} environment output did not report the managed-only SQLite runtime mode",
    )
    require(
        require_string(request_input, "outputOption") == "--output",
        f"{config.label} capabilities output did not report the canonical --output selector",
    )


def assert_loaded_sqlite_runtime(
    config: ReleaseSmokeConfig,
    sqlite: dict[str, Any],
    runtime: dict[str, Any],
    managed_sqlite: dict[str, Any],
    runtime_surface: dict[str, Any],
) -> None:
    if config.expect_loaded_sqlite_details:
        expected_runtime_provenance = (
            "bundle-managed" if config.expect_bundle_home_property else "source-checkout-managed"
        )
        require(
            require_string(runtime, "status") == "ready",
            f"{config.label} environment output did not report a ready SQLite runtime",
        )
        require(
            require_string(runtime, "runtimeProvenance") == expected_runtime_provenance,
            f"{config.label} environment output did not report the expected SQLite runtime provenance",
        )
        require(
            bool(require_string(runtime, "loadedLibraryPath").strip()),
            f"{config.label} environment output did not report the loaded SQLite library path",
        )
        for key, message in (
            ("requiredSqliteSourceId", "canonical SQLite source id requirement"),
            ("loadedSqliteSourceId", "canonical SQLite source id"),
        ):
            require(
                require_string(runtime if key.startswith("loaded") else sqlite, key)
                == require_string(managed_sqlite, "requiredSqliteSourceId"),
                f"{config.label} environment output did not report the {message}",
            )
        require(
            require_string(runtime, "loadedSqliteVersion")
            == require_string(managed_sqlite, "requiredMinimumSqliteVersion"),
            f"{config.label} environment output did not report the canonical SQLite version",
        )
        require(
            require_string(runtime, "loadedSqlite3mcVersion")
            == require_string(managed_sqlite, "requiredSqlite3mcVersion"),
            f"{config.label} environment output did not report the canonical SQLite3 Multiple Ciphers version",
        )
        for key, message in (
            ("requiredCompileOptions", "canonical SQLite compile options"),
            ("forbiddenCompileOptions", "canonical forbidden SQLite compile options"),
        ):
            require(
                required_list(sqlite, key) == required_list(managed_sqlite, key),
                f"{config.label} environment output did not report the {message}",
            )
        require(
            require_bool(sqlite, "requiresSecureMemorySupport")
            == require_bool(managed_sqlite, "requiresSecureMemorySupport"),
            f"{config.label} environment output did not report the canonical SQLite3MC secure-memory requirement",
        )
        require(
            require_string(runtime, "compileOptionsVerification") == "verified",
            f"{config.label} environment output did not report verified SQLite compile-option enforcement",
        )
    if config.expect_bundle_home_property:
        require(
            require_string(sqlite, "bundleHomeSystemProperty")
            == require_string(runtime_surface, "sqliteBundleHomeSystemProperty"),
            f"{config.label} environment output did not report the bundle-home system property",
        )
