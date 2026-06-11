# Vendored SQLite3 Multiple Ciphers Source

FinGrind vendors the official SQLite3 Multiple Ciphers 2.3.5 amalgamation in this directory so
local Gradle runs, the nested Jazzer build, GitHub Actions, and the Docker image can all build
against the same pinned protected-book native source instead of inheriting whichever `libsqlite3`
version happens to exist on the host.

Source provenance:
- project page: [https://utelle.github.io/SQLite3MultipleCiphers/](https://utelle.github.io/SQLite3MultipleCiphers/)
- upstream release: `SQLite3 Multiple Ciphers 2.3.5 (based on SQLite 3.53.2)`
- official amalgamation asset:
  [https://github.com/utelle/SQLite3MultipleCiphers/releases/download/v2.3.5/sqlite3mc-2.3.5-sqlite-3.53.2-amalgamation.zip](https://github.com/utelle/SQLite3MultipleCiphers/releases/download/v2.3.5/sqlite3mc-2.3.5-sqlite-3.53.2-amalgamation.zip)
- upstream license: [../../LICENSE-SQLITE3MULTIPLECIPHERS](../../LICENSE-SQLITE3MULTIPLECIPHERS)
- verified LF-normalized `sqlite3mc_amalgamation.c` SHA3-256:
  `330ff26dea1db7e73ffa8a8cebbdf09bf63a2cf731b94b9735c75156618b0329`

Build policy:
- the Gradle task `verifyManagedSqliteSource` verifies the vendored `sqlite3mc_amalgamation.c`
  hash after normalizing line endings to LF so Git checkout policy cannot skew the integrity check
- the Gradle task `prepareManagedSqlite` compiles a managed shared library from this source for the
  current macOS, Linux, or Windows host
- managed builds compile with `SQLITE_THREADSAFE=1`, `SQLITE_OMIT_LOAD_EXTENSION=1`,
  `SQLITE_TEMP_STORE=3`, `SQLITE_SECURE_DELETE=1`, and `SQLITE3MC_SECURE_MEMORY=1`
- managed/runtime compatibility also forbids the SQLite compile option `USE_URI`
- the Docker image compiles the same vendored source during image build

Runtime policy:
- the upstream `2.3.5` amalgamation package no longer ships separate `sqlite3.c` / `sqlite3.h`
  copies, so FinGrind treats `sqlite3mc_amalgamation.c`, `sqlite3mc_amalgamation.h`, and
  `sqlite3ext.h` as the whole canonical vendored source payload
- controlled FinGrind surfaces pin SQLite 3.53.2 together with SQLite3 Multiple Ciphers 2.3.5
- FinGrind applies `sqlite3_key()` immediately after open and relies on the upstream default
  `sqleet` / `chacha20` cipher
- the supported FinGrind passphrase transport contract is one explicit safe source
  (`--book-key-file`, `--book-passphrase-stdin`, or `--book-passphrase-prompt`) wired into
  `sqlite3_key()`, not SQLite URI `key=` or `hexkey=` parameters and not plaintext CLI or
  environment-variable secret transport
- there is no migration path or backward-compatibility layer for legacy plaintext books or other
  encryption variants
