# SQLite3 Multiple Ciphers Agent Protocol

**Version:** 1.0.0
**Updated:** 2026-08-28
**Baseline:** SQLite3 Multiple Ciphers **2.5.1** (based on SQLite **3.53.4**)
**Inherits:** [.codex/UNIVERSAL_ENGINEERING_CONTRACT.md](./UNIVERSAL_ENGINEERING_CONTRACT.md) v3.0.0+
**Layers on:** [.codex/AGENTS_SQLITE.md](./AGENTS_SQLITE.md) — load it first. This file adds only the at-rest encryption layer.
**Scope:** projects that encrypt SQLite databases with SQLite3 Multiple Ciphers — cipher and key lifecycle, encrypted database files, key/rekey flows, `PRAGMA key`/`rekey`, `ATTACH ... KEY`, the at-rest encryption boundary, and the secret-leakage surfaces around them, in any language or binding that wraps SQLite3MC.

## 0. Scope and inheritance

This protocol is the encryption layer on top of the SQLite protocol. Load `AGENTS_SQLITE.md` first: it owns version baselines, SQL features, native build/link identity, WAL/durability, migrations, and the base FFI rules. This file owns only what encryption adds: cipher and key lifecycle, the at-rest boundary, file-format cipher state, key-bearing leakage surfaces, and the encryption threat model. When SQLite3MC is used from a particular language, also apply that language protocol.

SQLite3MC is intentionally compatible with SQLite APIs, but encryption, VFS behavior, keying, and file-format cipher state add contracts that ordinary SQLite does not have. Do not infer SQLite3MC behavior from ordinary SQLite alone.

**Primary objective adds to the base:** key safety, cipher/file-format compatibility, and observability without leakage. Encryption that is not tested as encryption is not verified. A wrapper API that hides key ownership, cipher selection, or migration behavior is not finished.

### 0.1 SQLite3MC tacit gaps

In addition to the SQLite tacit gaps (AGENTS_SQLITE §0.1), watch especially for:

- Whether the compile-time, link-time, and runtime libraries are all the same SQLite3MC version — not merely the same SQLite version. The base header/library/runtime coherence check (AGENTS_SQLITE §4.3) extends to cipher identity.
- Whether the application actually loads SQLite3MC at runtime, or quietly resolves to a system SQLite with no encryption. "Drop-in replacement" is a code property, not a runtime guarantee, and here the failure is silent loss of encryption.
- Whether encrypted-database test fixtures reflect production cipher, KDF, page size, and reserve-byte settings — or were created with default settings and so prove nothing about the deployed format.
- Whether keys ever appear in URIs, `ATTACH ... KEY` statements, `PRAGMA key`/`rekey`, debug captures, query logs, crash reports, shell history, or process listings. Every one of these is a real production leak class.
- Whether `TEMP` tables, in-memory databases, or bytes 16–23 of the database file are inside or outside the threat model. The encryption boundary is non-obvious and easy to assume away.
- Whether old SQLCipher, sqleet, or SQLite Encryption Extension conventions still inform the codebase. SQLite3MC is API-compatible in many places but is not identical, and copy-pasted SQLCipher recipes can silently drift.
- Whether the secure cipher-state nullification path retained in the pinned SQLite3MC baseline is intact. It looks redundant; removing it is a security regression.

Where the answer is not derivable from code, history, or conversation, surface the gap explicitly; do not assume the convenient answer.

---

## 1. Baseline posture: SQLite3MC 2.5.1

For repositories governed by this protocol, assume SQLite3 Multiple Ciphers **2.5.1**, based on SQLite **3.53.4**. Use the repository's pinned version when it is more specific. Do not upgrade or downgrade SQLite3MC without a compatibility judgment, migration-risk assessment, and verification plan.

The SQLite3MC 2.3.x line hardened secure memory handling for cipher state: nullifying cipher data structures securely on freeing (issue #230, introduced in 2.3.3) and zeroing one-time keys for `chacha20`, `aegis`, and `ascon128` after encrypt/decrypt (2.3.4). These fixes are carried forward in 2.5.1. Treat any edit around cipher-state cleanup, zeroization, or nullification as security-sensitive. Do not remove these paths because they look redundant — this is exactly the kind of code where Naur's "amorphous additions" warning bites in reverse.

SQLite3MC 2.5.x retains the SQLite 3.53.4 source baseline and the existing default `chacha20` protected-file profile. Version 2.5.0 made internal AEGIS and Ascon symbols private and added optional dispatch-table compile symbols; 2.5.1 fixes compact WebAssembly import encoding. Do not enable `SQLITE3MC_USE_DISPATCH_TABLE` or change `SQLITE3MC_API_TABLE_PREFIX` as part of a version-only upgrade: those options alter the exported native-symbol contract and require a separate binding and runtime-link analysis.

The underlying SQLite version baseline, the 3.52 withdrawal warning, and the WAL-reset fix are owned by AGENTS_SQLITE §3.

---

## 2. Change loop additions

Apply the base change loop (AGENTS_SQLITE §2). Encryption adds these concerns to each system-map axis:

- **Truth:** canonical owner of default cipher, legacy flags, KDF settings, reserve bytes, plaintext-header policy, and key material/lifecycle, in addition to the base version/build owners.
- **Evidence:** encryption roundtrip, wrong-key failure, rekey, and cipher-migration proofs, in addition to base build/link/migration proofs.
- **Consequence:** stored encrypted database files and backups that may require a specific cipher to open; compliance and key-management workflows.
- **Justification:** why each cipher / page-size / KDF / legacy-mode choice is the way it is, and which are inherited rather than deliberately chosen.

Encryption-typical "smallest failing proofs" to add to the base list: encrypted open/read/write roundtrip; wrong-key rejection; rekey or decrypt migration fixture; cross-version or legacy-cipher fixture; file-header or plaintext-leak check.

When verification fails, distinguish encryption-specific root causes beyond the base set: key timing, wrong cipher configuration, runtime library shadowing that dropped encryption, or unsupported cipher.

Do not:

- log passphrases, raw keys, key-bearing URIs, PRAGMA statements containing secrets, or decrypted data;
- downgrade to vanilla SQLite accidentally, silently losing encryption;
- change cipher defaults, page sizes, reserve bytes, KDF parameters, or legacy flags without a migration and fixture evidence;
- claim encryption correctness without a wrong-key failure test and a plaintext-leak check.

---

## 3. Do not accidentally link vanilla SQLite

SQLite3MC can be used as a drop-in replacement for SQLite, but replacement is not proof of correct encryption behavior, and the failure mode here is silent loss of at-rest encryption.

Beyond the base header/library/runtime coherence check (AGENTS_SQLITE §4.3), verify:

- the actual library file packaged into the application is SQLite3MC, not vanilla SQLite;
- symbol resolution order, DLL/shared-object search path, rpath/install-name, and static-link symbol conflicts;
- transitive dependencies that also bundle SQLite;
- package manager postinstall behavior;
- runtime version reports include the SQLite3MC identity, not just a SQLite version.

If both vanilla SQLite and SQLite3MC appear in the same process, trace which consumers bind to which symbols. Avoid duplicate SQLite global state unless the project deliberately isolates it. If the repository replaces `sqlite3.c` with `sqlite3mc_amalgamation.c`, ensure every consumer that expects encryption is compiled and linked against the replacement.

Cipher-relevant compile options extend the base compile-option set (AGENTS_SQLITE §5.2): default cipher `CODEC_TYPE`; legacy compatibility flags such as sqleet or SQLCipher legacy modes; `SQLITE_TEMP_STORE` and `SQLITE_SECURE_DELETE` as they affect plaintext spillage; ZLIB-backed extension configuration. Changing any of these is file-format or security-posture state and requires tests.

---

## 4. Encryption, ciphers, and key lifecycle

### 4.1 Key ownership

Key material must have one explicit owner and lifecycle. Identify:

- where the key/passphrase originates;
- who can create, rotate, recover, or revoke it;
- how it is transported into SQLite3MC;
- whether it is a passphrase, raw key, KMS-derived secret, user credential, device secret, or test fixture;
- where it is stored, cached, zeroized, redacted, and destroyed;
- what happens on wrong key, missing key, expired key, or partial rekey failure.

Do not hard-code production keys. Do not commit real encrypted database keys. Do not add default passphrases for convenience. Test keys must be visibly test-only and isolated from production configuration.

### 4.2 Prefer safe API boundaries

Prefer a wrapper API that applies the key immediately after opening a connection and before any schema reads, migrations, PRAGMAs, or application queries.

C API posture:

- `sqlite3_key()` and `sqlite3_key_v2()` set a database key and should normally be called immediately after `sqlite3_open()` / `sqlite3_open_v2()`.
- Use `sqlite3_key_v2()` when the schema name matters, including attached databases.
- `sqlite3_rekey()` and `sqlite3_rekey_v2()` change keys. They can also decrypt a database by specifying an empty key; require explicit migration intent for that path.
- SQLite3MC-specific functions use the `sqlite3mc_` prefix. Do not assume every SQLite Encryption Extension or SQLCipher convention is identical.

SQL posture:

- `PRAGMA key` and `PRAGMA rekey` are available, but they are easier to leak in logs, traces, query capture, debugging output, and crash reports.
- `ATTACH ... KEY` can attach encrypted databases, but the key string is still sensitive.
- URI parameters can configure encryption, but key-bearing URIs are high leakage risk because URIs commonly appear in logs, diagnostics, process listings, shell history, metrics, and crash reports.

Use SQL or URI keying only when the repository has explicit redaction and logging discipline.

### 4.3 Cipher choice

For new encrypted databases, prefer the repository's existing secure default. If no repository default exists, prefer the modern authenticated default used by SQLite3MC rather than legacy compatibility modes.

SQLite3MC supports multiple cipher schemes, including:

- wxSQLite3 AES-128 CBC without HMAC;
- wxSQLite3 AES-256 CBC without HMAC;
- sqleet ChaCha20-Poly1305 HMAC;
- SQLCipher AES-256 CBC with SHA HMAC variants;
- System.Data.SQLite RC4;
- Ascon-128 v1.2;
- AEGIS family algorithms.

For new development, do not choose AES-CBC-without-HMAC or RC4 unless the task is explicitly legacy compatibility. Treat legacy modes as migration targets, not modern defaults.

Cipher configuration is file-format state. Changing cipher scheme, KDF parameters, page size, reserve bytes, plaintext header behavior, or legacy mode requires migration tests using real fixtures.

Per universal contract §1.5 (Justification), record *why* the cipher, KDF, and page-format choice is the way it is — threat model, performance budget, legacy compatibility, regulatory constraint, or inherited default. A choice without a recorded reason cannot be safely re-evaluated by the next reader.

### 4.4 Rekey and cipher migration

Rekeying is a data migration, not a simple settings edit.

Before implementing rekey or cipher migration, define: the old cipher/key format; the new cipher/key format; whether migration is in-place or copy-based; transaction and crash-safety expectations; backup/rollback plan; verification after migration; behavior for wrong old key or failed new key; user-visible recovery path.

Test rekey with fixtures, wrong keys, interrupted operations where feasible, and backup/restore workflows.

### 4.5 Attachments and multiple databases

SQLite3MC can handle encrypted and unencrypted databases together through `ATTACH`, and each database can use a different cipher scheme.

When touching `ATTACH` behavior:

- key each attached schema explicitly;
- test cross-database queries;
- test backup and detach behavior;
- verify that migration scripts do not accidentally copy plaintext into unencrypted files;
- ensure temp tables and intermediate data do not leak sensitive content to disk.

---

## 5. The at-rest encryption boundary

### 5.1 What encryption covers and does not cover

SQLite3MC encrypts database files and journal files, but not every byte or every storage path is equally protected. Important boundaries:

- `TEMP` tables are not encrypted by SQLite3MC.
- In-memory databases are not encrypted because they are not database files at rest.
- Bytes 16 through 23 of the database file contain header information that is usually not encrypted.
- Plaintext header features, if enabled, intentionally expose header bytes for compatibility.
- Application logs, caches, telemetry, memory dumps, backups, export files, and temp files are outside SQLite3MC's at-rest encryption boundary unless separately protected.

For sensitive workloads, use `SQLITE_TEMP_STORE=2` or `SQLITE_TEMP_STORE=3` where appropriate, and use `PRAGMA temp_store=MEMORY` when compile-time temp-store policy is not sufficient.

### 5.2 Encryption-aware backup and export

The base backup/restore/VACUUM rules (AGENTS_SQLITE §7.2) apply. Encryption adds:

- distinguish encrypted database backup from plaintext export;
- document and test whether backups preserve encryption, cipher settings, page size, and reserve bytes;
- check whether `VACUUM INTO` target URI parameters such as `reserve=N` affect the encryption of the generated copy;
- protect dumps, CSV exports, JSON exports, logs, and support bundles separately from SQLite3MC encryption;
- test restore from real encrypted fixtures, not only creation of new databases.

### 5.3 WAL and sidecar encryption

The base WAL discipline (AGENTS_SQLITE §7.1) applies. Additionally verify encryption of `-wal`, `-shm`, and journal sidecar files where applicable, and ensure deleting sidecar files is never used as a substitute for proper checkpoint/recovery logic on encrypted databases.

---

## 6. Encryption FFI rules

The base FFI safety rules (AGENTS_SQLITE §8.2) apply. Encryption adds key-buffer sensitivity:

- treat key buffers as ownership- and lifetime-sensitive material;
- do not keep pointers to temporary key buffers beyond their valid lifetime;
- zeroize key buffers after use where the language and runtime permit;
- ensure exceptions/panics crossing the C ABI boundary cannot leave key material in an exposed or non-zeroized state.

### 6.1 Wrapper API design

A good wrapper makes unsafe states hard to represent. Prefer APIs that:

- require keying before queries or migrations can run;
- distinguish encrypted, plaintext, and unknown database state;
- make cipher and migration intent explicit;
- preserve SQLite errors with redaction;
- close connections deterministically;
- prevent URI/PRAGMA secret leakage;
- expose version and compile-option diagnostics for support, including SQLite3MC identity;
- allow test fixtures for wrong-key and migration cases.

Avoid APIs that:

- accept optional keys with ambiguous defaults;
- silently create plaintext databases when keying fails;
- auto-migrate cipher formats without backup or user intent;
- hide native-library identity;
- expose raw database handles without lifecycle rules;
- run migrations before applying the key.

---

## 7. Testing and verification for encryption

### 7.1 Minimum verification for encryption-affecting changes

For changes that affect encryption, keying, cipher config, or persisted encrypted files, verify at least:

- create encrypted database;
- reopen with correct key;
- fail to open/read with wrong key;
- ensure the file does not contain obvious plaintext table names or inserted sentinel values where the expected encryption boundary applies;
- run schema migration on an encrypted fixture;
- backup and restore an encrypted database;
- rekey when relevant;
- verify runtime library identity is SQLite3MC and the expected compile options are present;
- verify logs/traces do not include secrets.

This sits on top of the base native/durability/performance verification (AGENTS_SQLITE §9).

### 7.2 Compatibility fixtures

Maintain real encrypted fixture files when compatibility matters: current default cipher fixture; each supported legacy cipher fixture; plaintext fixture if the application supports plaintext databases; old application-version fixture; wrong-key fixture or negative test; corrupted/truncated fixture where recovery behavior matters; WAL/journal fixture when sidecar handling matters.

Do not replace all fixture tests with mock-level tests. The encrypted file format is the contract.

---

## 8. Security and operational posture

### 8.1 Threat model clarity

SQLite3MC protects database contents at rest under defined assumptions. It does not automatically protect:

- data while the process is running;
- data returned through queries;
- temp tables unless temp storage is forced into memory;
- application logs and telemetry;
- exported files and backups;
- process memory dumps;
- keys stored beside the database;
- compromised application users or compromised hosts.

State the real threat model when changing encryption behavior. The threat model is itself theory in Naur's sense — usually held by a security stakeholder, often not in the diff. Where the agent is acting without it, surface the gap (per universal contract §0).

### 8.2 Secret redaction

Never emit secrets through: logs; metrics; traces; SQL query capture; crash reports; exception messages; debug dumps; command-line arguments; test snapshots; support bundles; root README examples.

Redact keys, passphrases, key IDs where needed, key-bearing URIs, and SQL statements containing `PRAGMA key`, `PRAGMA rekey`, or `ATTACH ... KEY`.

### 8.3 Secure defaults

For new work:

- require explicit key configuration for encrypted databases;
- fail closed if a key is missing where encryption is required;
- avoid silently falling back to plaintext;
- prefer modern authenticated ciphers;
- use memory temp storage for sensitive workloads;
- keep file permissions restrictive (base rules in AGENTS_SQLITE §7.3);
- expose diagnostics for version/build identity without exposing secrets.

### 8.4 Supply-chain safety

SQLite3MC is security-relevant native code. Treat dependency changes as security-sensitive. When touching vendored or prebuilt artifacts: verify source and artifact provenance; review changelog and security-relevant fixes; update SBOM or dependency inventory; avoid unpinned downloads in build scripts; avoid executing downloaded build tools without checksum/provenance controls; test downstream packages after update.

---

## 9. Observability without leakage

Operational feedback should prove the database subsystem works without exposing secrets or sensitive data.

Useful signals: SQLite3MC/SQLite version and source ID; compile options; database open/key/migration phase failures; busy/locked timeout counts; checkpoint and backup outcomes; migration duration and success; corrupt-file or wrong-key failure classification; native-library load path in debug diagnostics, redacted as needed; package artifact version.

Do not log full SQL statements if they can include keys or sensitive data. If query logging is necessary, redact keying operations and sensitive values first.

---

## 10. Encryption deletion and blast-radius rules

The base deletion rules (AGENTS_SQLITE §10) apply. Encryption adds: removing a cipher, KDF option, legacy-compatibility flag, or wrapper key method can strand existing encrypted databases that can only be opened with that exact configuration. Treat such deletion as a data-migration decision, not cleanup. A deletion made without the cipher/file-format theory destroys structure that *looks* redundant but is in fact load-bearing for some existing encrypted on-disk file the agent has never seen.

---

## 11. Documentation and re-cueing

Use `.codex/PROTOCOL_AFAD.md` for docs that describe SQLite3MC integration, encryption APIs, key management, migrations, or operational procedures. Encryption-specific re-cueing homes: cipher choices and migration rationale in migration notes or AFAD-managed docs; key lifecycle in wrapper API docs or security runbooks; FFI key-safety rules in safety comments; encrypted compatibility fixtures in tests; operational recovery in runbooks.

Theory the agent could not write down — production threat-model nuance, why a particular legacy fixture exists, who chose the current KDF settings, what historical incident led to a defensive zeroization path — should be flagged as a known re-cueing gap so the next reader knows where to ask.

The root `README.md` may mention that the project supports encrypted SQLite, but detailed cipher configuration, key management, and migration mechanics belong in deeper docs.

---

## 12. Completion checklist (encryption)

Apply the base SQLite checklist (AGENTS_SQLITE §12), then add:

```text
Encryption baseline:
- Did I verify the runtime library is SQLite3MC (not vanilla SQLite) and the intended version, at build time AND runtime?

Key safety:
- Did I preserve one canonical owner for cipher defaults and key lifecycle?
- Did I avoid logging, committing, or documenting real secrets or key-bearing commands?

Encryption evidence:
- Did I prove correct-key success, wrong-key failure, and absence of obvious plaintext leakage?
- Did I verify against real encrypted fixtures, not only freshly created scratch databases?

Cipher justification:
- Can I explain why each touched cipher, page-size, KDF, or legacy-mode choice is the way it is — or have I surfaced that as a known gap rather than silently changing it?

Boundary:
- Did data integrity, key safety, cipher compatibility, and migration safety remain intact?
- Did I keep the secure cipher-state nullification/zeroization paths intact?
```

Do not claim completion if runtime SQLite3MC identity is unverified, encryption behavior is untested, or existing encrypted database compatibility is unknown.
