# Vendored SQLite3 Multiple Ciphers Source

FinGrind vendors the official SQLite3 Multiple Ciphers 2.3.3 amalgamation in this directory so
local Gradle runs, the nested Jazzer build, GitHub Actions, and the Docker image can all build
against the same pinned protected-book native source instead of inheriting whichever `libsqlite3`
version happens to exist on the host.

Source provenance:
- project page: [https://utelle.github.io/SQLite3MultipleCiphers/](https://utelle.github.io/SQLite3MultipleCiphers/)
- upstream release: `SQLite3 Multiple Ciphers 2.3.3 (based on SQLite 3.53.0)`
- official amalgamation asset:
  [https://github.com/utelle/SQLite3MultipleCiphers/releases/download/v2.3.3/sqlite3mc-2.3.3-sqlite-3.53.0-amalgamation.zip](https://github.com/utelle/SQLite3MultipleCiphers/releases/download/v2.3.3/sqlite3mc-2.3.3-sqlite-3.53.0-amalgamation.zip)
- upstream license: [../../LICENSE-SQLITE3MULTIPLECIPHERS](../../LICENSE-SQLITE3MULTIPLECIPHERS)
- verified `sqlite3mc_amalgamation.c` SHA3-256:
  `84512ff957a27f066a3ed94e6ce388815622695c8a8b68f9f2de89dd8e4e9e0f`

Build policy:
- the Gradle task `verifyManagedSqliteSource` verifies the vendored `sqlite3mc_amalgamation.c`
  hash
- the Gradle task `prepareManagedSqlite` compiles a managed shared library from this source for the
  current macOS or Linux host
- managed builds compile with `SQLITE_THREADSAFE=1`, `SQLITE_OMIT_LOAD_EXTENSION=1`,
  `SQLITE_TEMP_STORE=3`, and `SQLITE_SECURE_DELETE=1`
- the Docker image compiles the same vendored source during image build

Runtime policy:
- FinGrind deliberately builds `sqlite3mc_amalgamation.c`, not the plain `sqlite3.c` copy that is
  also shipped by the upstream release bundle
- controlled FinGrind surfaces pin SQLite 3.53.0 together with SQLite3 Multiple Ciphers 2.3.3
- FinGrind applies `sqlite3_key()` immediately after open and relies on the upstream default
  `sqleet` / `chacha20` cipher
- the supported key-transport contract is a separate UTF-8 passphrase file, not SQLite URI
  `key=` or `hexkey=` parameters
- there is no migration path or backward-compatibility layer for legacy plaintext books or other
  encryption variants
