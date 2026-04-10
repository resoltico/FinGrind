# Vendored SQLite Source

FinGrind vendors the official SQLite 3.53.0 amalgamation in this directory so local Gradle runs,
GitHub Actions, and the Docker image can all build against the same pinned native source instead
of inheriting whichever `libsqlite3` version happens to exist on the host.

Source provenance:
- upstream release: `3.53.0`
- upstream release log: [https://sqlite.org/releaselog/3_53_0.html](https://sqlite.org/releaselog/3_53_0.html)
- official source archive: [https://sqlite.org/2026/sqlite-amalgamation-3530000.zip](https://sqlite.org/2026/sqlite-amalgamation-3530000.zip)
- verified `sqlite3.c` SHA3-256:
  `bb317fbbd2b3bc53233ddd5894bf4d2dc6f533445f350d4235dbcc86f65af4ec`

Build policy:
- the Gradle task `verifyManagedSqliteSource` verifies the vendored `sqlite3.c` hash
- the Gradle task `prepareManagedSqlite` compiles a managed shared library from this source for the
  current macOS or Linux host
- the Docker image compiles the same vendored source during image build

This repository does not use a migration path or backward-compatibility layer for SQLite runtime
selection. Controlled FinGrind surfaces now pin SQLite 3.53.0 directly.
