---
afad: "5.0.1"
version: "0.62.2"
domain: USER_CLI_OPERATIONAL_NOTES
updated: "2026-08-09"
route:
  keywords: [fingrind, cli, diagnostics, request-file, unsupported-book-format-version, book-key-file, passphrase, backup, restore, pagination, report-output, runtime, pair-targets-conflict, target-owner-only-required, source-artifact-identity-duplicated, source-artifact-identity-changed, protected-book-pair-publication-evidence-blocked]
  questions: ["how does fingrind protect book keys", "how does a request-file path behave", "what diagnostics does fingrind return", "how do fingrind reports and runtime contracts work", "how does FinGrind admit protected-book pair targets", "what does source-artifact-identity-changed mean"]
---

# CLI Operational Notes

**Purpose**: Explain FinGrind CLI diagnostics, protected-book handling, report publication, and runtime facts that apply across commands.
**Prerequisites**: Read [USER_CLI.md](./USER_CLI.md) for command usage and [USER_INSTALL.md](./USER_INSTALL.md) for public bundle installation.

## Diagnostics And Protected Books

- Error envelopes may include `hint` and `argument` fields to help an agent or operator repair the call without consulting docs.
- Rejected and error responses for non-plan commands are written to stderr so stdout remains reserved for successful primary results, fixed-output scaffolds, and other success-only contracts.
- A valid explicit `--output json` selects the JSON diagnostics envelope even when the command is unknown. Absent, missing, duplicate, or invalid output selection uses text diagnostics; explicit `--output text` always stays text. CSV has no failure-row grammar, so its failures use the same text diagnostics renderer.
- `help`, `version`, `capabilities`, `print-request-template`, and `print-plan-template` reject extra arguments.
- `open-book` requires an absent `--book-file` destination. An existing path returns the exit-`7` `status: "error"` envelope `book-destination-occupied` before FinGrind resolves its selected key or accesses the file. An existing caller-selected live-book or key-file parent and its resolved ancestry must already be real, owner-only, and non-mutable; FinGrind validates it only and never repairs its permissions or ACL. A missing live-book parent is created only through the atomic POSIX `0700` path and then validated; ACL-only filesystem creation fails closed. If opening does not complete after FinGrind creates artifacts, it returns `open-book-preparation-artifacts-retained` with every retained `{role,path,retainedStage}` fact; it never removes them for a retry. Preserve those paths and choose fresh ones.
- `generate-book-key-file --new-book-key-file` creates one new owner-only UTF-8 key file through an atomic fresh `0600` stage on POSIX, writes and forces it, and publishes the absent final name without replacement. Its selected parent directory must already exist and remain owner-only: FinGrind validates it without creating, weakening, or permission-repairing that caller-owned parent. Success reports `artifacts[].{format,path,retainedStage}`; the retained stage is immutable evidence, never a deletion or retry handle. Generated final files report `0600` on POSIX filesystems and `owner-only-acl` on Windows.
- `generate-attestation-key-file --attestation-custodian file-pkcs8 --new-attestation-key-file`
  publishes one no-clobber encrypted Ed25519 key file and returns only its public SPKI and key ID.
  Its required
  `--attestation-passphrase-file` is independent custody material: keep it owner-only and do not
  reuse a book key, a founder passphrase, or a command-line value.
- Every command that creates or opens private attestation material requires an explicit
  `--attestation-custodian file-pkcs8`; `file-pkcs8` is the only shipped custodian. An explicit
  unsupported selection is refused as `custodian-not-supported`, with no file-custody fallback.
- `backup-book`, `restore-book`, and `rekey-book` share one hard-break maintenance path contract.
  Every existing caller-selected protected-book or book-key artifact parent is validation-only: it
  and its resolved ancestry must already be real, private owner-only, and non-mutable, and
  FinGrind never permission- or ACL-repairs it. Only an absent final-target parent may be created:
  FinGrind preflights its creation ancestry, atomically creates it with POSIX `0700`, and
  postvalidates the canonical parent and full ancestry. A lifecycle source parent must already
  exist. ACL-only final-target creation fails closed as `artifact-path-invalid` with
  `details.pathFailure: "atomic-owner-only-protocol-file-creation-unsupported"`. A
  non-directory component is refused. Before canonicalization, FinGrind scans every lexical
  component from the root through the selected parent without following links and refuses any
  symbolic-link or non-directory component, including a direct-parent alias. A final target leaf
  may be absent; a present symlink or non-regular type is refused, while a present regular leaf
  follows the operation's no-replace or replacement policy. A lifecycle source leaf must already
  be a regular non-symlink file before final-target preparation. An existing selected source or
  FinGrind-owned recovery artifact that needs inspection is the exit-`6`
  `artifact-path-invalid` rejection with `details.pathFailure: "target-owner-only-required"` when
  it is not owner-only; correct that artifact's ownership and permissions outside FinGrind before
  rerunning. A caller-owned ordinary output leaf is not inspected as a FinGrind artifact: a
  no-clobber command reports its exact occupied-target rejection instead.
- The complete selected source set must name independent physical files. That includes the live
  book or backup artifact and every selected file-backed key source. If a later source role is a
  hard link or other physical alias of an earlier source, FinGrind returns exit-`6`
  `artifact-path-invalid` with
  `details.pathFailure: "source-artifact-identity-duplicated"`; choose distinct source files.
- After source exclusions are held, FinGrind revalidates every selected source against its locked
  physical identity before it admits a target. A replacement or substitution returns exit-`6`
  `artifact-path-invalid` with `details.pathFailure: "source-artifact-identity-changed"`.
  Keep every selected source stable, restore the trustworthy intended source if it changed, then
  rerun the complete maintenance command.
- Initial pair final-target identity is admitted after maintenance has admitted every selected parent,
  including any permitted missing-parent creation, and before it creates a final target, stage,
  reservation, claim, or pair-evidence artifact. When both final targets already exist,
  FinGrind uses `Files.isSameFile` to establish identity; one physical object is
  `pair-targets-conflict` (exit `2`). For two absent leaves whose parents resolve to one physical
  directory, exact raw equality and a collision after canonical Unicode decomposition plus
  root-locale case mapping are the same rejection. Other distinct leaves, including Unicode,
  spaces, punctuation, and leading dashes, remain valid targets when the filesystem admits them.
  A previously admitted eligible missing parent may remain; the initial refusal creates no final target,
  retained lease-control file, stage, capability witness, reservation, claim, or pair-recovery
  evidence.
  [USER_REJECTIONS.md](./USER_REJECTIONS.md#protected-book-pair-target-admission)
  carries the exact public fields and repair guidance.
- Each admitted physical maintenance directory retains a v4 directory-reservation control for
  final-target admission. Existing source objects additionally use a private per-user v4 control
  named from their explicit physical identity, so a hard-link alias in another directory cannot
  bypass a live-book activity or maintenance exclusion. A held lock is the sole liveness fact; an
  unlocked valid control is inert after a crash. FinGrind never reclaims, deletes, or rewrites
  either control. v2/v3 controls and other retired namespaces are not adopted; their residue,
  malformed controls, unavailable locks, and overlapping locks fail closed.
- v4 is an incompatible cold cutover. During a scheduled outage, stop and prevent every pre-v4
  process and automation, then independently confirm the outage before archiving the old
  per-user v2/v3 coordination roots and every v2/v3 directory control in affected live, backup,
  and target parents to private evidence names that are not controls and do not end in `.lock`.
  Do not delete, adopt, merge, or co-run the old controls. If that shutdown cannot be proven, do
  not cut over; a Windows handle that blocks archive is evidence that the old process is still
  active.
- Before `backup-book`, `restore-book`, or `rekey-book` begins any stage, probe, reservation, or
  final mutation, it acquires and scans the full source-and-target workflow scope for an owner
  record that binds the exact source, target pair, secret identity, and owner-recorded derived
  stages. A verified unresolved record for another full workflow returns the exit-`7`, `rejected`, `precondition`
  `maintenance-recovery-pending` response. Its non-null
  `details.{recoveryOperation,bookTarget,generatedSecretTarget}` names the operation and canonical
  absolute targets but does not reconstruct source, backup ID, credential, or secret material.
  Restart that named operation with complete original source, target, and secret inputs; never
  rename, overwrite, delete, recreate, or manually clean the evidence.
- `protected-book-pair-publication-uncertain` means verified evidence established an exact pair but
  not durable completion: preserve the evidence and rerun only that complete original workflow.
  Its always-present nullable `details.pairPublication.pairPublicationRetention`, when non-null,
  binds each final member to its exact retained stage; `null` never permits cleanup. The distinct
  `protected-book-pair-publication-evidence-blocked` result has `unestablished` final-member
  states, so preserve all paths and independently investigate rather than rerunning or
  reconstructing any workflow. A recovered rekey verifies the generated-key pair before it attempts
  prior-key access.
- `restore-book` publishes only to an absent `--book-file` destination. It refuses any existing or
  racing destination without overwriting it.
- `--book-key-file` must point to a non-empty single-line UTF-8 passphrase file no larger than 4096 bytes; one trailing LF or CRLF is tolerated and stripped, but embedded control characters are rejected.
- Book key files must use POSIX owner-only permissions (`0400` or `0600`) on macOS/Linux or a Windows owner-only ACL on Windows, their containing directory must also remain owner-only, and the public examples keep those files under a separate `./secrets/` tree instead of beside the book.
- `--book-passphrase-stdin` reads one UTF-8 passphrase payload from standard input and therefore cannot be paired with `--request-file -`. The accepted stdin payload is capped at 4096 bytes. Feed that stdin route from a file or secret-fetching process rather than embedding the passphrase literal in shell history.
- `--book-passphrase-prompt` reads the passphrase from the controlling terminal without echo, the accepted prompt payload is capped at 4096 UTF-8 bytes after normalization, and this prompt route is accepted only with `--output text`.
- `--request-file <path>` resolves the caller-selected path to one regular UTF-8 JSON object document capped at `1048576` bytes. Aliases to regular files are accepted; directories, named pipes, device files, and other nonregular targets are refused. Use `--request-file -` for standard input.
- `--request-file -` reads one UTF-8 JSON object document from standard input under that same `1048576`-byte limit.
- `rekey-book` requires one current passphrase source plus one absent `--new-book-key-file` target. It generates the replacement secret itself and does not accept a replacement secret through standard input or an interactive prompt.
- `rekey-book` rejects using the same key-file path for both current and new secrets.
- While its maintenance lease is held, `rekey-book` revalidates the selected live-book digest
  immediately before generated-secret publication and again before book replacement. That lease
  coordinates FinGrind, not arbitrary same-owner filesystem writes; an external write between a
  validation and the operating-system publication call is completion-uncertain
  (`protected-book-pair-publication-uncertain`), not an atomic-replacement guarantee.
- `rekey-book` may retain private workflow material while a rotation is being verified, but it
  never exposes user-managed recovery evidence. A verified owner record can be resumed only by
  rerunning the named original operation with complete original source, target, and secret inputs.
  Legacy, malformed, incomplete, or inconsistent residue is fail-closed as
  `protected-book-pair-publication-evidence-blocked`, not an operator-cleanable artifact.
- The supported backup/restore workflow is one encrypted closed-book copy plus restoration to a new absent live-book path. Do not copy a book while FinGrind is actively mutating it, and keep the copied `.sqlite` file under the same protected filesystem stance as the live book while storing key material separately from the copied book tree.
- `restore-book` uses `--backup-key-file` only to open the backup source and then re-encrypts the restored live book under the generated `--new-book-key-file`, so reopen the restored `--book-file` path with that destination key file after the restore completes.
- The packaged CLI does not require an external `sqlite3` binary and does not shell out to `sqlite3`.

## Queries And Reports

- `inspect-book` is the safest machine-readable probe before `open-book`, `declare-account`, or `post-entry`, because it reports initialization state, detected book-format version, supported book-format version, and compatibility with the current binary.
- No read or mutation command repairs permissions or ACLs on a caller-selected existing live-book, key file, or parent directory. Those paths are validated and refused when they violate the protected-book contract; only absent FinGrind-created artifacts use owner-only creation primitives, and unavailable primitives fail closed.
- `list-accounts`, `list-postings`, and `list-tax-registrations` return paginated payloads whose `resolvedQuery.cursor` records the accepted opaque input cursor (`null` on the first page) and whose optional top-level `nextCursor` can be passed back unchanged through `--cursor` when another page exists.
- `account-ledger` returns a paginated payload with `resolvedQuery.effectiveDateFrom` and `resolvedQuery.effectiveDateTo` always present (`null` for an omitted bound), `resolvedQuery.pagination.limit`, an accepted opaque `resolvedQuery.pagination.cursor`, and an optional opaque `nextCursor` that continues the canonical ascending `(effectiveDate, recordedAt, postingId)` order through `--cursor`.
- `inspect-book`, `list-accounts`, `list-postings`, `account-balance`, `trial-balance`, `account-ledger`, `period-summary`, `financial-position`, `inventory-valuation`, `accrual-cutoff-schedule`, `fixed-asset-register`, `financing-register`, `realized-foreign-exchange-register`, `latvian-payroll-register`, `income-statement`, `cash-flow-statement`, `changes-in-equity`, and `tax-obligation` accept `--output text`; all tabular read/report commands except `inspect-book` and `get-posting` also accept `--output csv`.
- `account-balance`, `trial-balance`, `account-ledger`, `period-summary`, `financial-position`, `inventory-valuation`, `accrual-cutoff-schedule`, `fixed-asset-register`, `financing-register`, `realized-foreign-exchange-register`, `latvian-payroll-register`, `income-statement`, `cash-flow-statement`, `changes-in-equity`, and `tax-obligation` can also write one PDF artifact through `--pdf-out <path>`. PDF export is explicit file output, not another stdout output mode. Its parent must already exist as a real owner-only directory and the final PDF target must be absent; FinGrind never creates or permission-repairs that caller-selected parent. Before canonicalization, FinGrind scans every lexical component from the root through the selected parent without following links and refuses any symbolic-link or non-directory component, including a direct-parent alias. Every post-admission success or failure reports the canonical physical final path rather than the input spelling. JSON success envelopes publish `artifacts[].{format:"pdf",path,retainedStage}`, while `--output text` writes one artifact confirmation block to stdout and `--output csv` is rejected when paired with `--pdf-out`. The retained stage is immutable publication evidence and is never deleted, replaced, reused, or treated as a retry input. If final-directory durability cannot be confirmed, no successful report is emitted: `artifact-publication-durability-uncertain` reports top-level `retainedStage` and `details.publishedArtifact.{path,retainedStage}`. If the no-replace link outcome is indeterminate, `artifact-publication-outcome-uncertain` reports `details.{candidateArtifact,retainedStage}` and top-level `retainedStage` when applicable. Preserve all reported evidence; inspect the final or candidate and use a fresh destination for any new attempt. A pre-final failure remains `pdf-export-failure` and includes `retainedStage` whenever applicable.
- JSON report money fields are typed exact-money objects with `currencyCode` and `minorUnits`. Report CSV pairs every money column as `...CurrencyCode` and `...MinorUnits`, without context or query metadata rows; JSON carries the full semantic report and resolved query. An income statement JSON result carries `grossProfitTotals[]` and, when comparative selection is present, `comparativeGrossProfitTotals[]`; CSV remains the table of statement rows rather than a mixed row-and-total document.
- In `fixed-asset-register`, `carryingAmount` is the current carrying value, so every disposed row reports zero. Disposed JSON and CSV rows also publish `carryingAmountAtDisposal` as the exact immutable pre-disposal amount; active rows omit that value.
- `print-plan-template` emits the accepted atomic tax-setup request shape, including the ordered account and tax-registration declarations; custom plans may also use the generic nested `assertion` object for assertion steps.
- `execute-plan` reuses the same posting and query rules as the single-command surface, but runs the whole plan inside one atomic transaction and returns a bounded `payload.summary` by default. `--result-detail full` additionally includes `payload.journal` on success or `details.plan.journal` on deterministic plan failure. Committed business-entry steps preserve their typed `record-*` journal kinds, raw direct-journal fallback steps stay `post-entry`, and journal steps now carry typed `data` records; successful `list-accounts` and `list-postings` steps keep both pagination fields and structured row arrays instead of collapsing to counts alone. Full text journals retain posting provenance: `list-postings` keeps the canonical per-row `Attestation order`, while `get-posting` prints its `Attestation order` and complete `Attestation head` (or an explicit unavailable-linkage state).

## Latvian Payroll

- `record-latvian-monthly-payroll` is a deliberately narrow Latvian 2026 monthly-payroll accrual. Its caller-authored facts include `payrollRunId`, `employeeReference`, `payrollMonth`, EUR `grossWages`, `taxBookHeldAtEmployer`, `dependantCount`, six declared account-role fields, evidence, and provenance. Command help and `help record-latvian-monthly-payroll --output json --detail full` publish the field descriptions from the same request-schema metadata that validates the request.
- The supported withholding profile requires `taxBookHeldAtEmployer: true` and `dependantCount: 0`. These are explicit facts, not defaults. The fixed calculation applies the EUR 550 monthly non-taxable minimum only to that admitted profile; FinGrind rejects a different tax-book or dependant case instead of approximating a statutory calculation it does not own.
- Before using payroll, verify the worker, withholding, filing, and period facts against the primary legislation and official administration guidance linked from [DOC_02_LatvianPayroll.md](./DOC_02_LatvianPayroll.md) and [DOC_00_PrimarySources.md](./DOC_00_PrimarySources.md). FinGrind does not determine employment status, submit EDS filings, or perform annual reconciliation.

## Runtime And Discovery

- The public packaged CLI bundles its own Java 26 runtime and managed SQLite 3.53.4 / SQLite3 Multiple Ciphers 2.4.0 native library.
- `environment.runtime.runtimeDistribution` tells you whether the current process is running from a self-contained bundle, container image, source-checkout Gradle launch, or direct Java wrapper invocation.
- `environment.publication.supportedPublicCliBundleTargets` and `environment.publication.unsupportedPublicCliBundleTargets` expose the public distribution matrix directly to automation.
- `capabilities.requestShapes.schemaDialect` declares the JSON Schema dialect, and `capabilities.requestShapes.*.schema` publishes executable request schemas alongside the field descriptor arrays.
- Request JSON must be one object document; duplicate keys and unknown fields are rejected at every object level.
- `environment` reports runtime-contract details directly under: `payload.runtime.runtimeDistribution`, `payload.publication.publicCliDistribution`, `payload.publication.sourceCheckoutJava`, `payload.publication.supportedPublicCliBundleTargets`, `payload.publication.unsupportedPublicCliBundleTargets`, `payload.sqlite.bundleHomeSystemProperty`, `payload.sqlite.requiredCompileOptions`, `payload.sqlite.forbiddenCompileOptions`, `payload.sqlite.requiresSecureMemorySupport`, `payload.sqlite.requiredMinimumSqliteVersion`, `payload.sqlite.requiredSqlite3mcVersion`, `payload.sqlite.requiredSqliteSourceId`, `payload.sqlite.runtime.compileOptionsVerification`, `payload.sqlite.runtime.status`, `payload.sqlite.runtime.runtimeProvenance`, `payload.sqlite.runtime.runtimeTrustBasis`, `payload.sqlite.runtime.loadedLibraryPath` as a canonical absolute path, `payload.sqlite.runtime.loadedSqliteVersion`, `payload.sqlite.runtime.loadedSqlite3mcVersion`, `payload.sqlite.runtime.loadedSqliteSourceId`, `payload.storage.bookProtectionMode`, and `payload.storage.defaultProtectedBookFormat.cipher`, `payload.storage.defaultProtectedBookFormat.legacyMode`, `payload.storage.defaultProtectedBookFormat.pageSize`, `payload.storage.defaultProtectedBookFormat.reservedBytes`, `payload.storage.defaultProtectedBookFormat.kdfIter`, and `payload.storage.defaultProtectedBookFormat.plaintextHeaderSize`.
- `environment.sqlite.runtime.compileOptionsVerification` is `verified` only when the managed runtime is ready, `failed` when the loaded library is present but violates the compile-option contract by missing required options or exposing forbidden options, and `not-verified` when the runtime is unavailable, when the probe resolved one runtime target but aborted before verification could finish, or when an earlier compatibility gate prevents a compile-option verdict.
- `capabilities` also reports `preflight.semantics`, `preflight.commitGuarantee`, and `currencyModel` so agents can discover the advisory preflight contract plus the single-functional-currency and owned-foreign-exchange doctrine without reading source code.
- Gradle-driven local runs, the source-checkout wrapper, and the container image use a managed SQLite 3.53.4 / SQLite3 Multiple Ciphers 2.4.0 shared library.
- The developer direct-Java wrappers auto-discover that managed SQLite3MC library and scoped native access when they run from a prepared checkout. Direct-Java launches outside that checkout shape are unsupported.
- `capabilities` is the best machine-readable contract surface.
- `capabilities.requestInput.outputOption` publishes the canonical stdout-selection flag, while `capabilities --output json` and `capabilities --output json --detail full` publish the authoritative per-command stdout and artifact contract through grouped `CommandDescriptor` objects.
- `capabilities.commands`, command groups, usage lines, aliases, output modes, artifact outputs, and summaries are rendered from the contract protocol catalog rather than copied into the CLI renderer. The text-only PDF-capable report overview is derived from those same query-command artifact descriptors.
- `print-request-template` intentionally omits committed audit fields. Callers must not send `provenance.recordedAt` or `provenance.sourceChannel`.
- `print-request-template` and `print-plan-template` intentionally emit placeholder-first sample documents whose evidence and provenance values must be replaced before real-world use.
- Create a new tax-enabled book with `open-book` before using `print-plan-template`; its prerequisite account declarations and tax-registration step then run atomically against that initialized book.

## Failure Boundaries

- `--book-passphrase-prompt` is accepted only with `--output text`; selecting `json` or `csv` with that prompt route is rejected deterministically as `invalid-request` with a repair hint that points back to `--output text`, `--book-key-file`, or `--book-passphrase-stdin`.
- When `--output text` is selected, `--book-passphrase-prompt` either reads from a supported controlling terminal or fails deterministically with `interactive-prompt-unavailable` and a repair hint that points to `--book-key-file` or `--book-passphrase-stdin`.
- FinGrind does not accept SQLite URI `key=` or `hexkey=` transport, plaintext CLI passphrase arguments, or environment-variable passphrase transport. The protected-book contract is always one explicit safe passphrase source plus the upstream default `chacha20` cipher.
- Protected-book encryption covers the SQLite book bytes themselves, but not decoded query results in process memory, copied backups, exported reports, or key files stored beside the database. Treat those artifacts as separate protection problems.
- FinGrind forces SQLite temp storage into memory. If an operator changes that policy outside the supported runtime, any temp spill files fall outside the documented encrypted-book boundary.
- Successful `post-entry` responses carry a FinGrind-generated UUID v7 `postingId`.
- Posting-side account failures are reported as `account-state-violations` with one or more structured issue objects in `details.violations[]`; their machine envelope keeps a stable summary and omits a top-level repair `hint`.
- Posting-side entry-semantic failures are reported as `entry-semantics-violations` with one or more ordered issue objects in `details.violations[]`, and each issue carries stable `category` plus action-first `repair` guidance; their machine envelope likewise keeps a stable summary and omits a top-level repair `hint`.
- The operator-facing `--output text` projection for those two nested repairable posting families renders one top-level `Summary` row plus one `Issue N | <code>` section per violation; checked-in examples live at [examples/account-state-violations-text.txt](./examples/account-state-violations-text.txt) and [examples/entry-semantics-violations-text.txt](./examples/entry-semantics-violations-text.txt).
- Wrong passphrases, damaged or truncated protected books, and foreign or otherwise unauthenticatable protected SQLite variants are reported as the deterministic `protected-book-verification-failed` error instead of leaking raw SQLite symptoms such as `SQLITE_NOTADB`. An authenticated FinGrind book whose `user_version` is non-current instead returns `unsupported-book-format-version` with `detectedBookFormatVersion` and `supportedBookFormatVersion`; it is never migrated or opened.
- In a source checkout, example payloads live under [docs/examples/](./examples/). Public release bundles do not ship those repository fixture paths.
