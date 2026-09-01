# Vendored SQLite3 Multiple Ciphers Source

FinGrind vendors the official SQLite3 Multiple Ciphers 2.5.1 amalgamation in this directory so
local Gradle runs, the nested Jazzer build, GitHub Actions, and the Docker image can all build
against the same pinned protected-book native source instead of inheriting whichever `libsqlite3`
version happens to exist on the host.

Source provenance:
- project page: [https://utelle.github.io/SQLite3MultipleCiphers/](https://utelle.github.io/SQLite3MultipleCiphers/)
- upstream release: `SQLite3 Multiple Ciphers 2.5.1 (based on SQLite 3.53.4)`
- official amalgamation asset:
  [https://github.com/utelle/SQLite3MultipleCiphers/releases/download/v2.5.1/sqlite3mc-2.5.1-sqlite-3.53.4-amalgamation.zip](https://github.com/utelle/SQLite3MultipleCiphers/releases/download/v2.5.1/sqlite3mc-2.5.1-sqlite-3.53.4-amalgamation.zip)
- official amalgamation archive SHA-256:
  `4125f8ff275ea953dabb3289331b20a0e76d4fc060f57148f4a5df3bf3b0d5e0`
- upstream project license: [../../LICENSE-SQLITE3MULTIPLECIPHERS](../../LICENSE-SQLITE3MULTIPLECIPHERS)
- embedded cryptographic implementation notices and terms:
  [../../LICENSE-SQLITE3MULTIPLECIPHERS-THIRD-PARTY](../../LICENSE-SQLITE3MULTIPLECIPHERS-THIRD-PARTY)
- CC0 legal code referenced by embedded components: [../../LICENSE-CC0-1.0](../../LICENSE-CC0-1.0)
- verified LF-normalized `sqlite3mc_amalgamation.c` SHA3-256:
  `f7db114feae1e7e7421e767ae3de09e21d8738cc1c03b009447a6ac0ac926967`

Build policy:
- the Gradle task `verifyManagedSqliteSource` verifies every vendored release file against the
  canonical SHA3-256 manifest after normalizing line endings to LF so Git checkout policy cannot
  skew the integrity check
- the Gradle task `prepareManagedSqlite` compiles a managed shared library from this source for the
  current macOS, Linux, or Windows host
- managed builds compile with `SQLITE_THREADSAFE=1`, `SQLITE_OMIT_LOAD_EXTENSION=1`,
  `SQLITE_TEMP_STORE=3`, `SQLITE_SECURE_DELETE=1`, and `SQLITE3MC_SECURE_MEMORY=1`
- managed/runtime compatibility also forbids the SQLite compile option `USE_URI`
- the Docker image compiles the same vendored source during image build

Runtime policy:
- FinGrind deliberately builds `sqlite3mc_amalgamation.c`, the canonical encrypted amalgamation
  shipped by the upstream 2.5.1 release bundle
- controlled FinGrind surfaces pin SQLite 3.53.4 together with SQLite3 Multiple Ciphers 2.5.1
- FinGrind applies `sqlite3_key()` immediately after open and relies on the upstream default
  `sqleet` / `chacha20` cipher
- the supported FinGrind passphrase transport contract is one explicit safe source
  (`--book-key-file`, `--book-passphrase-stdin`, or `--book-passphrase-prompt`) wired into
  `sqlite3_key()`, not SQLite URI `key=` or `hexkey=` parameters and not plaintext CLI or
  environment-variable secret transport
- there is no migration path or backward-compatibility layer for legacy plaintext books or other
  encryption variants
