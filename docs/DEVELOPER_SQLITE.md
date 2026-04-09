---
afad: "3.5"
version: "0.1.0-SNAPSHOT"
domain: DEVELOPER_SQLITE
updated: "2026-04-08"
route:
  keywords: [fingrind, sqlite, sqlite3, jdbc, xerial, storage, single-book, filesystem-path, rationale]
  questions: ["why does fingrind use sqlite3 cli instead of jdbc", "how does fingrind map one sqlite file to one book", "why was sqlite-jdbc removed from fingrind"]
---

# SQLite Developer Reference

**Purpose**: Storage rationale and implementation notes for FinGrind's SQLite adapter.
**Schema references**:
- [sqlite/SCHEMA_CORE.md](./sqlite/SCHEMA_CORE.md)
- [sqlite/SCHEMA_COUNTRY_PACKS.md](./sqlite/SCHEMA_COUNTRY_PACKS.md)

## Hard-Break Storage Stance

FinGrind currently treats one SQLite file as one book for one entity.

That means:
- the selected file path is the book identity
- the file may live anywhere on the operating-system filesystem
- there is no default database location
- `--book-file` is required for both preflight and commit
- duplicate idempotency is enforced within the selected book, not globally across files

This is a deliberate hard break. There is no backward-compatibility or migration layer for an
older multi-ledger-in-one-file model.

## Current Adapter Choice

FinGrind's durable adapter is [`SqlitePostingFactStore`](/Users/erst/Tools/FinGrind/sqlite/src/main/java/dev/erst/fingrind/sqlite/SqlitePostingFactStore.java).

Current implementation choice:
- shell out to a pinned `sqlite3` CLI, defaulting to the repo-managed SQLite 3.51.3 toolchain
- initialize the schema from the embedded bootstrap SQL
- open a fresh `sqlite3` process per operation
- rely on the chosen book path as the durable boundary

Why this is acceptable for the current stage:
- the project is SQLite-first, not database-agnostic
- the schema is small and append-only
- the process boundary avoids hidden native library loading in the JVM
- arbitrary book paths work naturally because the file path is explicit on every call
- the binary version is pinned from upstream `sqlite.org`, not left to OS package-manager drift

## Toolchain Pinning

FinGrind now pins SQLite to version `3.51.3` from the official `sqlite.org` autoconf release
tarball.

Single source of truth:
- [gradle.properties](/Users/erst/Tools/FinGrind/gradle.properties) declares the full SQLite pin
- `fingrindSqliteVersion=3.51.3` is the human-readable version
- `fingrindSqliteVersionEncoded=3510300` is the sqlite.org autoconf archive encoding
- `fingrindSqliteReleaseYear=2026` selects the sqlite.org release directory
- `fingrindSqliteAutoconfSha3_256=...` is the expected upstream archive digest

Exact bootstrap mechanics:
- [scripts/sqlite-tooling.sh](/Users/erst/Tools/FinGrind/scripts/sqlite-tooling.sh) reads those four Gradle properties directly
- it constructs the exact upstream artifact URL as `https://www.sqlite.org/<year>/sqlite-autoconf-<encoded>.tar.gz`
- it downloads that archive into `/.local/tooling/downloads/`
- it verifies the downloaded archive with SHA3-256 before any extraction or build happens
- it builds `sqlite3` from source with the host `cc` toolchain
- it installs the resulting binary into a repo-local cache under `/.local/tooling/sqlite/<version>/<platform>/bin/sqlite3`
- it refuses to reuse an existing cached binary unless `sqlite3 --version` reports the pinned version prefix

Operational wiring:
- [scripts/ensure-sqlite.sh](/Users/erst/Tools/FinGrind/scripts/ensure-sqlite.sh) is the bootstrap entrypoint and prints the installed binary path
- [scripts/sqlite3.sh](/Users/erst/Tools/FinGrind/scripts/sqlite3.sh) is the stable wrapper that provisions on demand and then execs the pinned binary
- [buildSrc/src/main/kotlin/fingrind.java-conventions.gradle.kts](/Users/erst/Tools/FinGrind/buildSrc/src/main/kotlin/fingrind.java-conventions.gradle.kts) exports `FINGRIND_SQLITE3_BINARY=scripts/sqlite3.sh` for Gradle test tasks
- [cli/build.gradle.kts](/Users/erst/Tools/FinGrind/cli/build.gradle.kts) exports the same override for `:cli:run`
- [jazzer/build.gradle.kts](/Users/erst/Tools/FinGrind/jazzer/build.gradle.kts) exports the same override for Jazzer support tests and regression replay
- [ci.yml](/Users/erst/Tools/FinGrind/.github/workflows/ci.yml) provisions and verifies the pinned toolchain on GitHub Actions runners
- [Dockerfile](/Users/erst/Tools/FinGrind/Dockerfile) builds the same pinned binary from sqlite.org source inside the image build and sets `FINGRIND_SQLITE3_BINARY=/usr/local/bin/sqlite3`

What this means in practice:
- local repo development, GitHub CI, Jazzer, and the container all converge on the same upstream SQLite release
- ambient package-manager `sqlite3` is no longer the project default
- the fallback to plain `sqlite3` on `PATH` remains only as an explicit escape hatch when `FINGRIND_SQLITE3_BINARY` is not set

Primary entrypoints:
- [scripts/ensure-sqlite.sh](/Users/erst/Tools/FinGrind/scripts/ensure-sqlite.sh)
- [scripts/sqlite3.sh](/Users/erst/Tools/FinGrind/scripts/sqlite3.sh)

The runtime override knob is `FINGRIND_SQLITE3_BINARY`.
That keeps the adapter explicit while still allowing hermetic pinning in controlled environments.

## Upgrading To A New SQLite Version

The upgrade procedure is deliberately mechanical.

1. Choose the target SQLite release from `sqlite.org`.
   Use the upstream release notes and download page to confirm the exact patch release to adopt.
2. Update the four pin fields in [gradle.properties](/Users/erst/Tools/FinGrind/gradle.properties).
   Set the human-readable version, encoded autoconf version, release year directory, and upstream SHA3-256 digest together in one change.
3. Rebuild the repo-local toolchain.
   Run `./scripts/ensure-sqlite.sh` or `./scripts/sqlite3.sh --version` and confirm the reported version matches the new pin.
4. Re-verify every enforced surface.
   Run `./gradlew check --no-daemon`, `./gradlew -p jazzer test jazzerRegression --no-daemon`, `./gradlew :cli:shadowJar --no-daemon`, and `./scripts/docker-smoke.sh`.
5. Update any user-facing version mentions in docs.
   Search for the previous SQLite version in `README.md`, `docs/`, workflow files, and smoke checks.

Upgrade notes:
- the encoded autoconf version is not dotted; for example, `3.51.3` becomes `3510300`
- the sqlite.org year directory can change across releases and must be updated explicitly
- the digest must come from upstream for the exact archive being downloaded
- old cached versions can stay in `/.local/tooling/sqlite/`; the new pin installs to a different versioned directory
- if a release changes CLI behavior in a way that affects FinGrind, the smoke tests and Jazzer regression pass are expected to catch it

## Why Not `sqlite-jdbc`

FinGrind previously carried a `sqlite-jdbc` catalog entry inherited from GridGrind scaffolding.
It is now removed.

Reasons:
- local bring-up repeatedly hung while loading the driver's native library on one macOS machine
- that observation is documented as a concrete local failure, not as a universal macOS claim
- the driver still depends on embedded native extraction and JVM-side native loading
- after the move to the `sqlite3` CLI adapter, the dependency became dead weight

FinGrind did not remove `org.xerial:sqlite-jdbc` because we proved a universal macOS bug. We
removed it because the local failure forced a design review, and the review showed the CLI-backed
adapter fit the current product shape better anyway.

## Why Not a Generic SQL or ORM Layer

Alternatives not chosen:
- JDBC plus a home-grown repository abstraction
- JDBI, Hibernate, or other generic persistence stacks
- a multi-backend database-independence facade

Reasons:
- the canonical plan is SQLite-first
- the current domain boundary is a book-local append-only posting fact store
- a generic abstraction would add indirection without buying current product value
- country-pack growth should extend the schema deliberately, not hide it behind a least-common-
  denominator repository contract

## Operational Consequences

The current adapter still requires an external `sqlite3` process, but FinGrind no longer accepts
ambient package-manager drift as the default.

That is reflected in the repo:
- [Dockerfile](/Users/erst/Tools/FinGrind/Dockerfile) builds the pinned SQLite binary from official source
- [ci.yml](/Users/erst/Tools/FinGrind/.github/workflows/ci.yml) provisions the pinned toolchain before root and Jazzer jobs
- [buildSrc/src/main/kotlin/fingrind.java-conventions.gradle.kts](/Users/erst/Tools/FinGrind/buildSrc/src/main/kotlin/fingrind.java-conventions.gradle.kts) exports `FINGRIND_SQLITE3_BINARY` for Gradle test tasks

Tradeoffs accepted for now:
- process spawn overhead per operation
- explicit responsibility for correct SQL literal quoting
- dependence on an external binary instead of a bundled Java-only driver
- maintaining a small repo-local tool bootstrap layer

Tradeoffs intentionally avoided:
- JVM-native loader hangs or dylib surprises
- cross-environment SQLite version drift between developer machines, CI, and containers
- a large persistence stack that obscures the single-book contract
- pretending FinGrind is storage-agnostic

## Revisit Criteria

The adapter choice should be revisited only if one of these changes:
- FinGrind outgrows per-operation `sqlite3` process costs in measured workloads
- a demonstrably stronger actively maintained Java SQLite binding appears
- the product requires a broader transactional surface than the current adapter shape supports

Until then, the correct default is to keep the current adapter honest and well documented rather
than abstracting it prematurely.
