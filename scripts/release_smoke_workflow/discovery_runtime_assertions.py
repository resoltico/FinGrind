from __future__ import annotations

from typing import Any

from .models import ReleaseSmokeConfig
from .support import require, require_bool, require_string, required_list, required_mapping


def assert_discovery_surface(
    config: ReleaseSmokeConfig,
    payload: dict[str, Any],
    distribution: dict[str, Any],
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
        require_string(distribution, "runtimeDistribution") == runtime_distribution,
        f"{config.label} environment output did not report the canonical runtime distribution",
    )
    require(
        require_string(distribution, "publicCliDistribution")
        == require_string(runtime_surface, "publicCliDistribution"),
        f"{config.label} environment output did not report the public CLI distribution contract",
    )
    for key, message in (
        ("supportedPublicCliBundleTargets", "supported public bundle targets"),
        ("unsupportedPublicCliBundleTargets", "current unsupported public bundle targets"),
    ):
        require(
            required_list(distribution, key) == required_list(public_distribution, key),
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
    for key, message in (
        ("cipher", "canonical default book cipher"),
        ("legacyMode", "canonical legacy-mode flag"),
        ("pageSize", "canonical protected-book page size"),
        ("reservedBytes", "canonical protected-book reserved bytes"),
    ):
        require(
            storage_format.get(key) == protected_book_format.get(key),
            f"{config.label} environment output did not report the {message}",
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
