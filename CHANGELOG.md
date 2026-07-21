# Changelog

Notable changes to this project are documented in this file. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Historical release notes older than `0.31.0` live in:
- [Archive: 2026-05 (`0.30.0` through `0.23.0`)](./docs/DOC_CHANGELOG_ARCHIVE_2026_MAY.md)
- [Archive: 2026-04 I (`0.22.0` through `0.12.0`)](./docs/DOC_CHANGELOG_ARCHIVE_2026_APRIL_I.md)
- [Archive: 2026-04 II (`0.11.0` through `0.1.0`)](./docs/DOC_CHANGELOG_ARCHIVE_2026_APRIL_II.md)

## [Unreleased]

### Added

- Added verifiable operation attestation to protected-book format `51` and CLI protocol `32`.
  Every mutation now carries immutable canonical request and committed-effect preimages, a
  historically authorized Ed25519 envelope, and a SHA-256 operation head. `verify-book`,
  `attestation-review`, `export-attestation-receipt`, and `verify-receipt` expose structural
  verification, non-persisted review findings, and independently retained receipt anchors.
- Added manifest-attested no-clobber backup/restore flow with exact backup acknowledgement retry,
  founder credential genesis, and public documentation of encrypted credential custody.
- Added first-class contra-account taxonomy. A declared account can now identify the active postable account it reduces; the relationship is validated as a same-type, compatible-statement relationship, normal balance follows the contra role, account readback publishes `contraOfAccountCode`, and financial statements present the row as a reduction of its named account.
- Added discoverable `retire-account` request scaffolding and named atomic setup plans for tax, fixed assets, and financing. Each setup plan declares the exact prerequisite account taxonomy before it declares or uses the bounded-context facts, while the default `print-plan-template` remains a general executable workflow.
- Added an explicit Latvian monthly-payroll withholding profile to every payroll request, retained payroll run, plan fact, and readback. The supported 2026 calculation admits only `taxBookHeldAtEmployer: true` with `dependantCount: 0` and rejects all other profiles rather than assuming their tax treatment.
- Added an independent `architecture` verification module. It imports the production class graph once per test run and protects the core-to-contract-to-executor-to-adapter-to-CLI dependency direction, CLI naming boundaries, and the exclusion of filesystem path hints from machine JSON payloads.

### Changed

- Hard-broke close-planner construction to identity-bound factories. `InterimResultSweepPlanner`
  and `FiscalYearClosePlanner` now derive their accounting kernel from the initialized book
  identity, and no longer expose an unusable constructor parameter from an internal Java module
  package.
- Hard-broke the protected-book format to `51` and made `bookStartEffectiveDate` an immutable
  initialization fact. Every posting now refuses dates before that boundary, interim-result sweeps
  use it as their exact earliest admissible date, and the first fiscal-year close covers the valid
  partial fiscal segment when a book starts mid-year. Older protected-book formats are rejected;
  no migration or compatibility layer is provided.
- Hard-broke the fixed-asset register contract so `carryingAmount` is always the live carrying
  amount and therefore zero after disposal. Disposed rows now publish the immutable
  `carryingAmountAtDisposal` reconciliation value separately across JSON, CSV, text, and PDF.
- Hard-broke the protected-book contract to format `49` and the CLI protocol to `31`. Earlier protected-book formats remain incompatible; account taxonomy now carries explicit contra-account truth, payroll runs retain explicit withholding-profile facts, selected entry-kind semantics publish described `variantFields`, and query responses use one machine envelope shape.
- Changed every non-report query success payload, including account, posting, and tax-registration collections, to publish `family`, `bookIdentity`, `resolvedQuery`, and `generatedAt` beside its family records. `resolvedQuery.cursor` now records only the accepted cursor, while a top-level optional `nextCursor` announces a further page.
- Changed fixed-asset and financing onboarding from implicit prerequisite-account repair to executable setup plans with their declared taxonomy. Realized-foreign-exchange request scaffolds now derive receivable and revenue account codes from the selected book template.
- Changed verification observability and merge safety. The canonical `check.sh` gate now emits one structured report per executed stage and a bounded Java-compiler warning manifest, Gradle wrapper integrity validation is a dependency of the required GitHub `Gate`, and the parallel published-bundle matrix reads rather than races to write Gradle cache entries.

### Fixed

- Fixed live-clock attested postings, lifecycle operations, and receipt exports. Runtime timestamps
  are now canonicalized to UTC milliseconds before durable mutation and immutable signed payload
  construction, and non-credential mutation failures are no longer reported as invalid attestation
  credentials.
- Fixed fiscal-year close admission so a period with no generated close postings returns a named
  deterministic refusal before any close fact, audit event, or attestation operation is persisted.
- Fixed restore's clean-break contract across rejection messages and operator guides: an existing
  destination is always refused, and the retired `--replace-existing-book` option is not suggested
  or documented. Backup examples now require the stable backup ID and signing credential triples
  that the public command contract requires.
- Fixed CLI mutation failure precedence so an uninitialized book returns its typed initialization
  refusal before any attestation credential is read. Widened account-ledger identifier columns so
  canonical UUIDs remain legible in PDF output.
- Fixed `execute-plan` attestation so every successful mutating plan appends exactly one signed
  aggregate operation, with ordered immutable child preimages, rather than one chain operation per
  child mutation. Query-only and assertion-only plans no longer require a signing credential.
- Fixed the attested lifecycle boundary so `rekey-book` now requires and carries the same
  principal-bound encrypted credential triples as every other protected-book mutation. The old
  credential-less invocation is rejected; no compatibility path is retained.
- Fixed unreleased next-format Genesis admission so it cannot activate an autonomous workflow
  before any system-purpose credential exists. Workflow activation now follows system credential
  enrollment and the registry's reachable-quorum check.
- Fixed unreleased next-format attestation authorization so genesis is bound to its order-zero
  signed payload and declared immutable bootstrap facts, while operation provenance is recomputed
  from the signed request preimage. System close authorization now requires the exact active
  workflow ID rather than any active workflow of the same kind.
- Fixed unreleased next-format attestation authorization so every operation, manifest, and receipt
  derives its historical position and capability from the typed payload it signs. The standalone
  authorization corpus now applies its exact published byte mutations, including fixed C-key
  capability failures, rather than substituting generated semantic equivalents.
- Fixed unreleased next-format attestation verification so deterministic complete-book, artifact,
  and receipt resources execute against their actual serialized bytes; sale profiles require their
  tax-inclusive request facts and fiscal close uses its recorded UTC date.
- Fixed unreleased next-format attestation key custody. Verification now consumes the one
  canonical Ed25519 SPKI it validated, and an encrypted PKCS#8 credential is successful only after
  its no-clobber final name has passed the native parent-directory durability barrier.
- Fixed cryptographic primitive ownership. Generic SHA-256 hashing, constant-time secret
  comparison, and secure entropy now have one documented core owner, while architecture and shared
  source policy reject direct primitive use outside that owner and the attestation crypto seam.
- Closed the unreleased next-format operation-attestation contract around historical policy and
  credential identity. The latest capability policy rule is now the only effective rule at a
  position; credential bindings prove their SPKI-derived key identity and one-principal ownership;
  rollover adds rather than silently retires a credential; and the fixture ledger names the exact
  rollover signer and binding-rejection cases. Receipt independence now has one result name, and
  artifact vectors route to their canonical protocol.
- Corrected the unreleased next-format operation-attestation contract so close operations carry a
  derived posting request, system provenance requires an all-system viable quorum, tax-inclusive
  sales and purchases have one exact rounded journal equation, and generic reversal is limited to
  the non-lifecycle postings it can completely compensate. The fixture corpus now distinguishes
  standalone envelope vectors from complete book and artifact verification resources, and closes
  the previously unspecified fixed-asset, payroll, financing, inventory-shrinkage, settlement, and
  policy-effect mappings.
- Fixed malformed typed-request recovery so every operation points to its own request scaffold and
  canonical help command. Fixed payroll discovery notes to name both supported withholding-profile
  facts, and made the pinned `uv` plus exact Python helper-tool runtime contract fail before Gradle
  receives an incompatible interpreter.
- Fixed lifecycle reversals for fixed assets, financing, and realized foreign exchange so an origin with active dependent applications or settlements is rejected by the executor with a named domain refusal before SQLite. Durable triggers remain backstops and no longer surface this accounting precondition as a runtime storage failure.
- Fixed account presentation so accumulated depreciation and other valid contra accounts no longer publish as ordinary wrong-sided peers of the account they reduce.
- Fixed built-in book initialization across all template and basis combinations. The sales-discount allowance now derives its contra account from the selected template's canonical revenue account, while durable validation continues to require compatible contra taxonomy for every other relationship. Failed exclusive `open-book` initialization now removes only its uninitialized book artifact and SQLite sidecars, so the destination can be safely retried without deleting a pre-existing or concurrently created file.
- Fixed request discovery and documentation gaps for `retire-account`, fixed-asset setup, financing setup, template-specific foreign-exchange account codes, Latvian payroll withholding assumptions, and the shared non-report query envelope.
- Fixed reversal lifecycle rejections to render the actual typed bookkeeping entry kind rather than the literal field name `entryKind`, and fixed Latvian payroll profile rejections to name the rejected field and value with a grammatical repair action.
- Fixed launcher-path rendering so long absolute invocations remain intact copyable tokens and capability lists do not repeat the launcher path for every PDF-capable report.
- Fixed advisory verification drift. Scheduled distribution-freshness failures now create one actionable GitHub issue or update the existing open issue with the latest failed run, while manual canary reruns remain issue-free.
- Fixed recurring Java compilation noise. FinGrind now requests concrete deprecation locations from the compiler and documents the only two unavoidable PDFBox test-double overrides at their source rather than emitting anonymous deprecation notices from a green gate.
- Fixed contributor-container build noise by removing the redundant ca-certificates installation from the pinned devcontainer base image, which already supplies the certificate bundle.

## [0.61.0] - 2026-07-16

### Added

- Added an accrual cut-off context for accrual-basis books. `record-prepayment`, `record-deferred-revenue`, and `record-accrued-expense` now create durable append-only cut-off aggregates; recognition and accrued-expense settlement commands apply exact manual lifecycle amounts, reversals retain compensating lifecycle facts, and `accrual-cutoff-schedule` reports original, applied, and remaining balances in text, JSON, CSV, and PDF.
- Added a fixed-assets context with `record-fixed-asset-capitalization`, executor-resolved straight-line `record-fixed-asset-depreciation`, and `record-fixed-asset-disposal`. Each asset retains its cost, depreciation schedule, carrying value, disposal state, and compensating-reversal lineage; `fixed-asset-register` exposes the durable register in text, JSON, CSV, and PDF. The model is limited to one functional-currency cost-model schedule per asset and explicitly excludes leases, impairment, revaluation, tax depreciation, and statutory external reporting.
- Added a financing context with borrowing, principal-repayment, interest-accrual, and interest-payment commands. Retained financing-arrangement facts drive `financing-register` reporting in text, JSON, CSV, and PDF, including principal outstanding, accrued interest, paid interest, and compensating-reversal lineage. The context records nominal principal and exact stated interest only; effective-interest measurement, amortized cost, fair value, covenants, tax withholding, and lender integrations remain outside the product boundary.
- Added a realized foreign-exchange context with typed foreign-currency receivable origination and one-time settlement. The protected book retains the obligation, settlement, exchange-rate evidence, derived realized gain or loss, and compensating reversals; `realized-foreign-exchange-register` exposes the reconciliation in text, JSON, CSV, and PDF. Rate sourcing, open-balance remeasurement, translation, hedging, and mixed-currency journal lines remain excluded.
- Added the contract-owned Capability Catalog with machine-checked status and operative-boundary facts for the implemented, partial, and excluded accounting capabilities. The accounting-kernel scope ADR is rendered from that catalog so published scope cannot drift from the executable contract.
- Added opaque ascending keyset pagination to `account-ledger`, with explicit `--limit` and `--cursor` inputs, accepted pagination facts in `resolvedQuery`, an optional `nextCursor` for continuation without duplicate rows, and a carried prior running balance on every continued page.
- Added one canonical atomic tax-setup plan from `print-plan-template` and `execute-plan`. It initializes a clean book, declares the prerequisite payable and recoverable accounts, and declares the tax registration in one ordered transaction whose complete journal exposes every effect or rolls all effects back.
- Added explicit `amend-account` and `retire-account` operations with typed outcomes and lifecycle audit facts. Amendments preserve account identity and declaration time; retirement preserves ledger history, blocks ordinary authored use, keeps genuine historical reversals admissible, and remains verifiable in books with historical journal lines.
- Added a narrow Latvian 2026 monthly-payroll context. It derives supported payroll components from gross EUR wages, retains immutable runs and obligation settlements with compensating reversals, persists the supported payroll facts in protected SQLite books, and exposes a reconciliation register in text, JSON, CSV, and PDF without claiming EDS filing or worker-status determination.
- Added a documented primary-source policy for jurisdiction-specific material. It links the current controlling Latvian legislative texts, VID operational guidance, and ECB reference data, explains source scope, and requires rechecking time-sensitive inputs before payroll periods or tax filings.

### Changed

- Hard-broke the public contract to protocol `28` and the protected-book format to `46`. Accrual cut-offs, Latvian payroll, fixed assets, financing, and realized foreign exchange now own typed request grammars, account roles, durable origins and applications, lifecycle ordering, append-only reconciliation, and reports; retained lifecycle values are bound at the storage boundary to their immutable typed posting and foreign-exchange facts. Earlier book formats are rejected rather than upgraded in place.
- Reworked machine reports and failures into one semantic contract. Every report now returns a family-specific JSON payload with canonical book identity, a non-replay `resolvedQuery`, generation metadata, enum tokens, and exact money objects; CSV exports one family-specific typed row table with paired currency and integer-minor-unit columns; and text and PDF remain human projections. All rejected or error JSON envelopes publish one declared `category` beside `code` and `message`, including `internal` for FinGrind software failures.
- Changed tax-obligation reporting to the same tabular CSV grammar and PDF artifact capability as the other report families.
- Changed trading income statements into a composing multi-step presentation: cost of sales precedes gross profit, operating expenses exclude cost of sales, and gross profit, operating profit, and net income follow in order.
- Changed selectable CLI output to a fixed text default. The retired `FINGRIND_DEFAULT_OUTPUT` environment override and its environment payload field are removed; a valid explicit `--output json` selects machine diagnostics even for an unknown command, while absent, malformed, duplicate, invalid, text, or CSV selections use the canonical text diagnostics renderer.
- Changed protected-book key and maintenance vocabulary. Existing key-file flags are read-only inputs; newly generated secrets use explicit absent-target flags; backups receive independently generated backup keys; restores require a fresh destination key and explicit replacement consent; and rekeying always generates its replacement key.
- Changed `declare-tax-registration` to remain a pure registration operation with no implicit account creation; complete clean-book setup belongs to the atomic tax-setup plan.
- Changed Account Registry lifecycle rules so only never-posted, unreferenced accounts can be amended, while retirement requires a zero balance and no live operational binding.
- Changed fiscal-year closing to derive its period from `--year` and the book's fiscal-year start, and interim result sweeping to derive its start from retained transfer history using `--through`.
- Updated the repository-owned Kotlin build-logic baseline to `2.4.10`, JUnit Jupiter to `6.1.2`, and Apache PDFBox to `3.0.8`.

### Fixed

- Fixed protected-book backup publication on Windows when selected paths contain Unicode, spaces, leading-dash names, or deeply nested directories. Backup export now uses SQLite's native backup interface with a short, already-reserved owned stage so SQLite can create its sidecars within the native path limit, while final pair publication remains the sole atomic no-clobber claim on each selected artifact path.
- Fixed tax-enabled posting request templates so each scaffold includes the tax-registration and tax-code selector whenever its canonical request facts admit tax. Sale templates use the output-tax placeholder, while purchase, expense, and inventory-capitalization templates use the input-tax placeholder.
- Fixed machine path and failure truth. Success artifacts, environment diagnostics, maintenance rejections, and generic deterministic filesystem failures including invalid book/key paths, occupied generated-secret targets, and maintenance leases now publish real absolute paths under typed `path` and `relatedPaths` fields; human messages carry no path, and the shared text and PDF presentation owner alone redacts filesystem locations. Internal errors and uninitialized-book rejections now emit their declared `internal` and `precondition` categories rather than being incorrectly folded into a generic domain category.
- Fixed protected-book maintenance key isolation and interruption safety. Backup, restore, and rekey now reject a generated key that matches its source and retry with a fresh secret; failure coverage proves every staging and publication boundary preserves source books, selected live destinations, and generated-secret targets byte-for-byte.
- Fixed protected-book staging failures so native backup export opens its source book read-only, retries bounded transient SQLite backup locks on its initialized native backup handle, preserves the safe SQLite result name when a native export fails, and maps backup and restore export, copy, key-generation, and re-encryption errors to deterministic named `storage-runtime-failure` responses with the selected destination path instead of exposing raw storage exceptions.
- Fixed protected-book maintenance no-clobber behavior. `open-book` now rejects an occupied `--book-file` destination with `book-destination-occupied` and uses exclusive SQLite creation to refuse a target that appears after its initial check without opening or changing it; generated-secret targets and every backup or non-replacing restore target that must remain absent are held under durable maintenance leases and atomically reserved before source verification; and restore without explicit replacement consent preserves a destination book that exists initially or appears before final publication by rejecting with `book-destination-occupied`. Ordinary rejected maintenance attempts leave source books, existing destination books, and occupied secret targets unchanged.
- Fixed protected-book machine output and discovery so JSON artifacts, payload fields, structured rejection details, and the published response descriptors consistently use canonical absolute filesystem paths while text and PDF presentation retain redacted operator-facing paths.
- Fixed protected-book pair-publication truth. Each staged artifact revalidates its durable ownership record before publication; durable key-and-book publication is now the success boundary, so a later internal cleanup failure is retained as diagnostic evidence instead of turning a committed restore, rekey, or backup into a false failure. Before inspecting a companion book, recovery proves that the generated key is the exact durable stage it owns; a foreign key is never inspected or mutated, while proven interrupted residue is reclaimed so retry can reserve the same pair again.
- Fixed backup export mutability so a successful backup no longer appends an audit fact to its live source book. A backup now changes only its declared backup file and independently generated backup key.
- Fixed fiscal-year close chronology and idempotency. An attempt to close before a retained close or transfer horizon now receives a named rejection before SQLite, a repeated close reports its already-closed outcome, and failed closes do not consume a close-order value.
- Fixed tax-obligation and trading-report correctness: tax obligations now use tabular report output across formats, while cost of sales is never counted again as an operating expense below gross profit.
- Fixed self-contained CLI bundle generation after the PDFBox upgrade by removing unused servlet-container hooks carried transitively by Commons Logging and retiring their stale runtime-module exception.
- Fixed source-checkout CLI recovery after an interrupted build. A malformed cached application JAR now triggers a fresh runtime preparation instead of exposing a raw Java archive error.

## [0.60.0] - 2026-07-11

### Added

- Added the exact inventory-costing kernel: exported `Quantity`, `UnitOfMeasure`, `InventoryCostingDoctrine`, and `WeightedAverageCostingMath` types define non-money quantity, account-owned units of measure, and perpetual moving weighted-average arithmetic without using a rounded unit-cost display value as an accounting input.
- Added one append-only inventory movement ledger and materialized on-hand state to protected books. The new ledger records typed acquisitions, capitalizations, count increases, opening balances, disposals, write-downs, shrinkage, and reversal compensation in deterministic per-account replay order.
- Added typed inventory-maintenance commands for settled and credit landed-cost capitalization, carrying-cost write-downs, quantity shrinkage, and count-discovered quantity increases. Each command has its own request scaffold, evidence policy, account roles, and durable inventory movement rather than relying on a raw-journal substitute.
- Added `inventory-valuation`, an as-of report with optional ordered movement detail in JSON, text, tabular CSV, and PDF. It publishes each inventory account's owned unit of measure, exact quantity on hand, exact carrying-value pool, and an explicitly informational rounded moving-average unit-cost projection.
- Added deterministic, property-based, and Jazzer replay coverage for weighted-average conservation, pool-to-zero behavior, replay ordering, and the rule that cost of sales is independent of the rounded unit-cost projection.

### Changed

- Updated the managed runtime to SQLite `3.53.3` and SQLite3 Multiple Ciphers `2.3.6`, with a complete upstream six-file amalgamation manifest verified by normalized SHA3-256 digests at build time and runtime identity checks across bundles, containers, and source checkouts.
- Updated the repository-owned developer and release tooling to Ruff `0.15.21`, PMD `7.26.0`, Shadow `9.5.1`, Spotless `8.8.0`, `actions/setup-java` `5.5.0`, `gradle/actions/setup-gradle` `6.2.0`, `docker/login-action` `4.4.0`, and `docker/build-push-action` `7.3.0`.
- Changed `OWNER_MANAGED_TRADING` into a perpetual moving weighted-average doctrine. Trading books now require `--inventory-costing WEIGHTED_AVERAGE`, persist that choice, and expose it through book inspection; service books reject an inventory-costing selection.
- Changed the inventory request contract onto exact, quantity-aware facts. Inventory accounts require `unitOfMeasure { token, quantityScale }`; purchases and count increases require `quantity` plus `unitCost`; inventory opening balances require quantity plus carrying cost; and trading-sale relief now identifies quantity rather than caller-supplied cost.
- Changed sale and shrinkage costing so the executor derives authoritative cost of sales from the exact inventory pool and replay order. `ResolvedInventoryCosting`, preflight output, committed posting readback, and `get-posting` expose derived cost of sales, relieved quantity, and a display-only `roundedMovingAverageUnitCostProjection`.
- Changed tax and foreign-exchange composition for inventory acquisitions and capitalizations. Request amounts and unit costs are pre-VAT functional-currency carrying costs; recoverable input tax remains outside the pool, nonrecoverable input tax is capitalized, and retained `SPOT_TRANSACTION` facts must agree with the executor-resolved pre-tax functional amount.
- Changed the protected-book contract incompatibly to book format `39` and public protocol `21`. The new format persists the inventory doctrine, unit metadata, typed movement ledger, exact on-hand pool, and replay-backed verification; callers must use the quantity-based inventory request vocabulary.
- Changed raw journal admission so direct journals cannot touch inventory accounts. Every inventory movement is now owned by a typed business-event command, including opening inventory positions and reversal compensation.

### Fixed

- Fixed inventory integrity at both the admission and durable-storage boundaries. FinGrind now rejects movements before an account's replay horizon, quantity decreases below zero, write-downs above carrying cost, non-contiguous replay sequences, out-of-order movement inserts, and update or delete attempts against append-only inventory movements.
- Fixed inventory admission diagnostics so unit-of-measure-incompatible quantities, acquisition costs that cannot be represented exactly, and positive acquisitions below the functional-currency minor-unit floor now produce named request rejections instead of leaking lower-level exceptions.
- Fixed foreign-exchange validation and persistence for inventory and credit-side business events. Acquisition validation now compares the full `quantity × unitCost` amount with the retained functional amount, and valid `SPOT_TRANSACTION` facts persist through the complete request-to-book path.
- Fixed opening and movement-origin integrity so an inventory opening balance is admitted only as an account's first typed opening movement, and durable validation rejects any inventory movement whose posting origin does not match its typed business event.
- Fixed `inventory-valuation --movements --output csv` so it retains every selected account's exact quantity, carrying-value pool, and informational unit-cost snapshot even where no movement detail matches the selected range.
- Fixed inventory-event help and request discovery so human help, machine contracts, request templates, and ledger-plan scaffolds consistently describe derived cost of sales, exact quantity, tax treatment, and the inventory-specific command vocabulary.
- Fixed the post-tag public-container publication verifier so the anonymous mounted-book proof now expects the live per-account `trial-balance --output text` layout, the currency-formatted totals row, and the resolved `As of` context line instead of the retired compact account-table rendering that falsely failed the `0.59.0` staging-container publication rerun path after the image itself had already published correctly.
- Fixed the replacement-based release closeout helper so `reconcile-release-primary-checkout.sh` now stages its verifier outside the replacement checkout before moving that tree into place, treats the verified replacement as authoritative before deleting the displaced backup, normalizes ordinary owner permissions inside the displaced backup before retrying deletion, and reports any still-preserved cleanup-only backup path explicitly instead of rolling the repository back or pretending the release itself failed.

## [0.59.0] - 2026-07-04

### Added

- Added one typed inventory-purchase write surface through `record-purchase-settled` and `record-purchase-on-credit`, with canonical request scaffolds, purchase-specific source-document policies, SQLite persistence and readback coverage, and trading-book discovery/help surfaces that keep ordinary stock acquisitions on the business-event path instead of dropping them into raw `post-entry` adjustments.

### Changed

- Changed `OWNER_MANAGED_TRADING` from a named built-in template into a real goods-trading doctrine on both cash and accrual bases, with seeded `inventory`, `sales-revenue`, `sales-discount-allowance`, and `cost-of-sales` accounts, trading-sale `inventoryRelief`, trading-only purchase verbs, and gross-profit-aware income-statement presentation instead of the earlier service-shaped chart semantics.
- Changed trading sale requests onto one explicit typed event shape: `record-sale-settled`, `record-sale-on-credit`, `preflight-entry`, `post-entry` readback, retained request fingerprints, SQLite caller-authored entry round-trips, and the published request/help surfaces now all carry the owned `inventoryRelief { inventoryAccountCode, costOfSalesAccountCode, amount }` block, require it on trading books, and reject it on non-trading books.
- Changed the report surface onto one shared report model across text, JSON, CSV, and PDF so the bookkeeping statements and `tax-obligation` now project one coherent row-and-section structure, one artifact line, one record-family-per-command CSV line, and one cross-format truthfulness contract instead of mixed projectors and a tax-specific CSV outlier.
- Changed reporting-period close and maintenance administration semantics onto one tighter integrity line: postings now reject future effective dates through one shared UTC horizon guard, `interim-result-sweep` derives its window from `--through`, `fiscal-year-close` derives its window from `--year`, and `backup-book` plus `restore-book` now share the canonical `--backup-file` and `--backup-key-file` vocabulary while `restore-book` re-encrypts the restored live book under the destination `--book-key-file` and publishes the restored live book and key artifacts directly.
- Changed reversal doctrine so `record-reversal` now rejects reversal targets whose own lineage is already `REVERSAL` with the published `reversal-target-is-reversal` refusal, and the request, response, and discovery surfaces now steer operators toward one fresh operational posting instead of reversal-of-reversal redo chains.
- Changed discovery, scaffolds, and command help around the live trading path: discovery now publishes `protocolVersion: "20"`, non-trading books reject `record-purchase-settled` and `record-purchase-on-credit` with the canonical `verb-requires-trading-template` refusal instead of a misleading `unknown-account` path, and trading-sale help now surfaces the required-on-trading `inventoryRelief` block before the first rejection.

### Fixed

- Fixed operator wording drift across the live onboarding and failure surfaces so protected-book and book-key path rejections now use the final owner-facing `a/an` grammar instead of stale ornamental `one` phrasing, the CLI runtime-failure hints follow that same line, and SQLite plus live CLI regression coverage now locks the launcher-visible text that previously slipped past weaker substring-only tests.
- Fixed the typed-bookkeeping integrity failure contract so command-versus-classifier mismatches now publish the dedicated `internal-defect` exit-70 family instead of falling through the generic `internal-error` path, and the discovery catalog, CLI docs, and contract coverage now all prove that split explicitly.
- Fixed classifier doctrine drift so finance-income and finance-expense accounts now resolve to the non-anchoring `AUX` role for journal classification, which keeps FX gain or loss lines from spuriously turning receipt and payment settlements into compound operational bundles and locks that behavior with both real-taxonomy classifier coverage and application-boundary raw-journal regression tests.
- Fixed posting-success surface drift so `preflight-entry` and committed posting success envelopes now publish the nested `resolvedJournal` facts they already carried at the contract boundary, text-mode mutation success now reports the resolved event class plus contained typed events, `JournalClassifier` now owns anchor-signature and cash-line derivation directly from the classified journal path, and a dedicated totality test locks the structural, typed, compound-operational, and adjustment partition against regression.
- Fixed spec-proof gaps around the new initialization and journal-classification line so dedicated coverage now proves cash-basis and accrual owner-managed service book identity round-trips through SQLite, auxiliary settlement-classifier lines stay inside their intended event families, and raw admission plus evidence-conflict branches reject or accept exactly the current public contract.
- Fixed release-publication verifier drift so the anonymous mounted-book container proof now seeds ASSET declaration fixtures with the required `cashFlowAssetClassification`, accepts the live redacted PDF artifact path contract, expects the live trial-balance context block including `Accounting basis    : Cash basis`, and fails its mock-backed regression tests if either the bank-account declaration fixture, the published text artifact path surface, or the published text context surface falls behind the current contract again.
- Fixed release-smoke maintenance drift so the bundled and Docker acceptance workflows now drive `backup-book` through `--backup-file` plus `--backup-key-file`, restore onto a distinct destination `--book-key-file`, and fail the acceptance run if the restored live book still opens with the backup key instead of only the restored destination key.
- Fixed restore-book documentation drift so the SQLite developer reference and the administration-and-reports contract reference now state the live destination-key re-encryption contract instead of claiming that restored live books continue to reuse the backup key.
- Fixed report truth and export parity so every public report family now proves the shared report model through the real JSON, CSV, text, and PDF projectors, `tax-obligation` now uses the same one-row-per-record CSV grammar and `--pdf-out` artifact contract as the other report commands, posting preflight and commit text both render the full resolved journal line table, and PDF key-value sections now end with a real bottom rule so later section headings keep a stable visible gap that is pinned by golden raster and layout assertions.
- Fixed trading-book statement truth so multi-step income statements now render `Cost of Sales` as its own section above `Gross Profit`, keep operating expenses below gross profit, and project the same truthful section structure through CSV instead of publishing subtotals that looked composable while double-counting cost of sales.
- Fixed trading-book persistence drift so the strict SQLite schema now admits `INVENTORY` financial-position classification on seeded inventory accounts and typed purchase entries now persist their originating entry facts instead of failing at commit time under the encrypted SQLite path.
- Fixed close-ordering and idempotency truth so out-of-order `fiscal-year-close` attempts now reject deterministically before SQLite instead of leaking raw trigger failures, rerunning `fiscal-year-close` against an already closed year now returns the existing close state instead of pretending to create a fresh close, and failed close attempts no longer advance close-order sequencing.
- Fixed trading inventory integrity so `preflight-entry`, the typed trading sale commands, raw `post-entry`, and ledger-plan posting admission now reject any inventory decrease that would create or deepen a credit inventory balance, the public account-state rejection family now publishes the dedicated `inventory-balance-below-zero` issue code with exact field attribution, and financial-position summaries now flag contra-normal rows instead of letting legacy negative inventory hide behind a merely balanced equation.

## [0.58.0] - 2026-06-29

### Added

- Added one first-class business-event write surface around `record-sale-settled`, `record-sale-on-credit`, `record-expense-settled`, `record-expense-on-credit`, `record-receipt`, `record-payment`, `record-owner-contribution`, `record-owner-withdrawal`, `record-opening-position`, and `record-reversal`, with typed request scaffolds, admissibility rules, event-to-posting translation, lifecycle-specific rejections, and retained evidence facts instead of treating those flows as thin recipe aliases.
- Added one first-class tax and foreign-exchange surface through `declare-tax-registration`, `list-tax-registrations`, `tax-obligation`, and typed `foreignExchange` event facts, with owned per-book tax registration state, typed sale and expense tax selection, durable applied-tax facts, quoted-rate evidence, persistence, readback, request fingerprinting, and matching discovery/help/example/response contracts.
- Added one first-class cash receipts/payments and reporting-period close surface through `cash-flow-statement`, `interim-result-sweep`, and `fiscal-year-close`, including declared cash-account classification, comparative selection, JSON/text/CSV/PDF reporting, owned sweep and close planning, result-holding selection, persistence, audit/readback, and the supporting public protocol and examples.

### Changed

- Bumped the repo-owned verification and tooling baseline to Gradle wrapper `9.6.1`, Ruff `0.15.20`, `uv` `0.11.25`, JUnit `6.1.1`, and `actions/setup-python` `6.3.0`, refreshed the matching workflow pins, helper-runtime expectations, and developer docs from the same canonical sources, and added one GitHub-native protected-surface review owner line through `.github/CODEOWNERS` plus `main` branch protection that requires code-owner review for workflows, legal/security files, and the canonical release and governance entrypoints while keeping GitHub's administrator bypass available for the solo-owner release/publication path.
- Hard-broke the CLI result-envelope contract onto one canonical top-level shape. Successful JSON responses now use one shared `status` plus `payload` envelope with optional `artifacts[]`; `execute-plan` rejections and assertion failures now lift `code` and `message` to the top level while keeping the detailed plan payload; `payload.summary` no longer duplicates `failureCode` or `failureMessage`; discovery JSON from `help`, `capabilities`, and `version` now publishes `protocolVersion: "12"` so callers can detect the live hard-break line explicitly; `--output text --pdf-out <path>` now writes one artifact confirmation block instead of the full report body plus a diagnostics side message; and `--output csv` is now rejected when combined with `--pdf-out`.
- Hard-broke the public bookkeeping write language, account doctrine, temporal parsing, close-path theory, and initialized-book identity onto one business-event-first, cash-basis-aware line. Typed business-event commands are now the primary caller-authored operating surface, raw `post-entry` and `preflight-entry` remain explicit lower-level direct-journal paths, direct journals must move at least one declared cash-and-cash-equivalent asset account, local dates and fiscal-year anchors now flow through one canonical parser, declared-account polarity derives only from account type plus classification, the kernel and book-template identity tokens drop the retired cash-doctrine labels while preserving cash business vocabulary, the close surface separates repeatable `interim-result-sweep` from `fiscal-year-close`, `CURRENT_PERIOD_RESULT` remains a derived statement-line kind rather than a stored account classification, and protected books now persist and publish `accountingBasis` explicitly.
- Hard-broke report comparative selection, posting backlinks, CSV export-family naming, durable identifier display, and account-declaration outcome publication onto one cleaner contract. `trial-balance`, `financial-position`, `income-statement`, `cash-flow-statement`, and `changes-in-equity` now accept explicit `--comparative none|prior-period|<range>` selection with published capability modes plus default; `get-posting` and `list-postings` now publish `reversesPostingId` and `reversedByPostingId`; command CSV exports now use command-specific `exportFamily` values instead of mixed families; durable ids no longer truncate; and account declaration outcomes plus audit events now distinguish declared, reactivated, renamed, and unchanged behavior on one live line.
- Hard-broke idempotent posting replay, the protected-book format, and retained evidence onto one semantic-fingerprint plus minimal-source-document line. Persisted posting requests now store fingerprint value plus version, matching idempotency replays reuse the original posting fact, conflicting replays fail deterministically, evidence profiles own the retained source-document fields, retained source-document facts now keep only `sourceDocumentId`, `sourceDocumentType`, and `documentDate`, discovery/examples no longer imply a mandatory shape-only hash, and the protected-book compatibility/schema/docs surface teaches only the durable facts FinGrind actually owns.

### Fixed

- Fixed cash-flow comparative truth, direct-journal admission, template publication, PDF-export silence, release-surface verifier return timing, pre-tag release repair tagging, public-container stderr proof, and wrapper refresh drift together. Requested comparative PDF exports now label the comparative statement explicitly and publish an explicit no-match summary when the selected comparative scope contains no cash-flow lines, raw direct journals now reject declared non-cash-only movement, cash-flow text rows no longer embed literal table delimiters inside one classification cell, ASSET declaration failures now publish accepted `cashFlowAssetClassification` values, `print-request-template` plus `print-plan-template` now default to the minimal sale scaffold instead of pre-populating optional tax or foreign-exchange branches, supported bundle, container, source-checkout, and direct modular launcher surfaces now open `java.base/java.nio` and export `java.base/sun.nio` to the FinGrind CLI module so successful PDF exports stay silent without one library-side log filter, release smoke and the public container verifier now both fail if a successful PDF export writes diagnostics, the repo-hygiene verifier now returns promptly after quick `git fsck` completions instead of idling behind its heartbeat sleep, the release-candidate tag verifier now allows an unreleased same-version repair commit to become the first public tag while keeping post-tag reruns pinned to the immutable tagged commit, and Gradle wrapper refreshes now keep FinGrind's repo-owned cache, build-logic, JaCoCo, and externalized build-root contract instead of regressing to stock launcher scripts that break the included-build plugin-jar verification path.

## [0.57.0] - 2026-06-19

### Changed

- Bumped the repo-owned GitHub Actions checkout baseline to `actions/checkout` `7.0.0`.
- Bumped the repo-owned Python lint baseline to Ruff `0.15.18`.
- Hard-broke the protected-book format contract to version `24` so the canonical posting taxonomy, SQLite schema allowlists, protected-book fixtures, and `inspect-book` compatibility surface all describe the same live direct-journal storage line.
- Hard-broke the default teaching surface onto the raw journal path. `print-request-template`, `print-plan-template`, the bundled quick-start request, the primary posting and ledger-plan examples, and the checked-in scaffold companions now all demonstrate direct balanced `JOURNAL` lines first, while recipe-backed examples remain published only as labeled shortcuts.
- Hard-broke posting readback vocabulary onto the journal-first taxonomy. `get-posting`, `list-postings`, and the checked-in response examples now report direct postings as `postingOriginKind: JOURNAL`, including recipe-backed helper commits, instead of presenting one recipe helper label as the posting origin itself.
- Hard-broke posting-side entry-semantic refusals onto one canonical violation owner. The public `entry-semantics-violations` payload now publishes ordered `details.violations[]` items with stable `code`, `field`, `message`, `category`, and `repair` fields; nested machine-contract `detailRejections`; one stable top-level count summary; no top-level repair `hint`; and text-mode deterministic rejections that render one typed issue section per violation.
- Hard-broke posting-side account-registry refusals onto the same repairable violation discipline. The public `account-state-violations` payload now publishes ordered `details.violations[]` items with stable `code`, `field`, `message`, `category`, `repair`, `accountCode`, and optional `accountNodeKind` fields; matching nested machine-contract `detailRejections`; one stable top-level count summary; no top-level repair `hint`; and text-mode deterministic rejections that render one typed issue section per violation instead of a thin aggregate blob.
- Hard-broke `execute-plan` discovery onto one ledger-plan-owned posting contract. The nested posting model remains under the ledger-plan request shape, and text plus JSON help now derive the canonical posting scaffold from the published `POST_ENTRY` plan step instead of ad hoc local selection or standalone posting-template fallback.
- Hard-broke repairable posting-rejection discovery onto full nested catalogs. `capabilities --output json --detail full` now publishes `detailRejections` for both `entry-semantics-violations` and `account-state-violations`, so agents can enumerate per-issue codes, categories, and repairs from the machine contract without scraping prose examples.

### Fixed

- Fixed raw direct-journal persistence drift so caller-authored `JOURNAL` postings now commit through the encrypted SQLite book path instead of leaking into one schema-level `CONSTRAINT_CHECK` breach.
- Fixed CLI runtime classification drift so SQLite persistence-invariant breaches now publish `internal-error` with one opaque error id and one truthful "should have been rejected before commit" message instead of the user-repairable `storage-runtime-failure` hint family.
- Fixed preflight/commit contract drift so deterministic bookkeeping invariants are rechecked before the commit store runs, and text-mode preflight success now says `Entry Preflight Passed` plus `Commit status | Not committed` instead of implying a commit guarantee.
- Fixed plan text leakage and release-surface proof gaps so full text `execute-plan` output no longer exposes internal Java class names, and the standing bundle, public-doc, example-fixture, release-smoke, and container verification surfaces now prove both the canonical raw-journal path and the retained recipe shortcut path together.
- Fixed direct raw-journal semantic drift so caller-authored `JOURNAL` requests now reject journals whose debit-credit netting reduces every referenced account to zero, preventing no-op account movement from reaching either preflight success or commit-time storage.
- Fixed reachability proof drift so the published account-classification matrix is now exercised through the real encrypted SQLite commit boundary instead of stopping at in-memory posting-acceptance checks.
- Fixed temporal-scope surface drift so read-command help and bounded-period status output now teach each command's scope archetype, boundary flags, labels, and boundary behavior from the canonical temporal-scope owner rather than partial hand-authored text.
- Fixed help-surface drift so text discovery now teaches one shared caller-submittable posting model for `post-entry` and nested `execute-plan` help, drops `presence: forbidden` fields only from the text projection while keeping the machine contract unchanged, renders Support command pointers under subordinate labels as shell-safe literal command blocks, applies a help-only key-width cap without perturbing non-help text surfaces, renders over-cap help keys on their own line with continuation descriptions at the shared value column, and refreshes the source-checkout launcher runtime automatically when runtime-owned checkout inputs outpace the prepared manifest.
- Fixed rejection-surface publication drift so the checked-in public example set now includes stderr text fixtures for `account-state-violations` and `entry-semantics-violations`, the user guides teach the `Summary` plus `Issue N | <code>` text layout explicitly, and the governing rejection-text contract doc matches the live canonical category and repair owners.

## [0.56.0] - 2026-06-17

### Changed

- Bumped the repository-owned NullAway baseline to `0.13.7`.
- Hard-broke the public bookkeeping write model onto one journal-first contract. `JOURNAL` is now the canonical caller-authored entry kind, direct balanced journals and recipe-backed cash and equity helpers both materialize through one `BookkeepingEntry.Journal` owner, `OPEN_ACCOUNTING_POSITION` remains the opening-only adoption path, and `REVERSAL_ADJUSTMENT` remains the contingent exact-negation cleanup path.
- Hard-broke accounting reachability theory onto one executable doctrine owner. Discovery, request help, and bundled templates now publish the live per-classification matrix for declarable, opening-reachable, operational-journal-reachable, and reversal-reachable account cells, including the reserved `RESULT_HOLDING` treatment, instead of hand-maintained capability claims.
- Hard-broke the remaining posting-request surface onto one canonical metadata owner. Entry-kind semantics, recipe semantics, accepted `sourceDocumentType` vocabularies, mandatory evidence facts, temporal-scope archetypes, and unsupported-field hints now derive from one request-surface contract, and caller-facing docs, errors, and discovery use the public `entryKind` vocabulary instead of leaking persisted posting internals.
- Hard-broke ledger-plan setup onto explicit attested genesis. `execute-plan` now rejects plan-contained book initialization; callers create the book first with `open-book` and then execute plans only against that initialized identity.
- Refined the authored scaffolds and operator surfaces. `print-request-template` and `print-plan-template` now pretty-print by default, full text `execute-plan` results render typed outcome and failure sections instead of raw fact blobs, and comparative report publication now follows one explicit no-data policy across text, JSON, and CSV.

### Fixed

- Fixed contract drift across examples and verification tooling so the bundled quick-start sample, checked-in source-copy examples, Jazzer replay seeds, and release-smoke workflow fixtures all prove the live journal-first request grammar and explicit `open-book` initialization before plan execution.
- Fixed public documentation drift so the storefront README and user guides now distinguish the concrete bundled quick-start request from the placeholder-first scaffolds and describe the live journal-first bookkeeping surface, evidence-profile doctrine, and reachability facts coherently.

## [0.55.0] - 2026-06-16

### Changed

- Bumped the Gradle formatting baseline to Spotless `8.7.0`.
- Bumped the repo-owned Python lint baseline to Ruff `0.15.17`.
- Hard-broke reviewed-surface inventory ownership onto one shared registry file for the Java and Python structural-governance verifiers. Reviewed Java waivers and tracked text-resource waivers now resolve from one canonical JSON registry instead of split language-local copies, so expiry, owner, and split-trigger changes travel through one reviewed-surface catalog.
- Hard-broke public bundle publication onto two explicit contracts: bundle layout now owns package contents, compatibility labels, and canonical platform ids, while bundle publication owns which declared targets are actually shipped and which runner label proves them. Published self-contained downloads now cover `macos-aarch64`, `macos-x86_64`, `linux-x86_64`, `linux-aarch64`, and `windows-x86_64`; `windows-aarch64` remains declared but unpublished. Bundle archives now build with normalized timestamps, publish explicit archive/checksum/manifest paths at build time, record per-target compatibility labels plus Linux minimum glibc floors in the bundle manifest contract, and back those claims with reproducibility checks plus host-runner and compatibility-floor smoke lanes in CI, freshness canaries, and tagged release publication.
- Hard-broke the maintenance and reporting grammar onto explicit book and period nouns. Rekey, backup, restore, and rollback flows now use the `--new-book-key-file`, `--backup-book-file`, `--backup-book-file-out`, and `--rollback-book-file` naming family, while period-bounded reports and period-result transfer now use `--period-start` and `--period-end` instead of the broader effective-date wording.
- Hard-broke runtime and machine-discovery feedback onto a more truthful operator and agent surface. Successful primary results own stdout, deterministic failures and rejections route to stderr in both text and machine modes, `execute-plan` can return bounded text or JSON instead of one fixed JSON-only contract, and discovery now reports the active bundle target, its publication status, and operation-specific exit-code families.
- Hard-broke bookkeeping read and lifecycle ownership onto narrower initialized-book seams. Read-only workflows, posting validation, and SQLite capability sessions now reuse dedicated initialized-book and capability-view owners instead of repeating lifecycle inspection logic across command families.

### Fixed

- Fixed published Windows bundle argument transport so the PowerShell bundle bridge, public Windows launcher, and JVM boundary now share one staged UTF-8 argument-vector contract instead of passing stress-path arguments natively. Unicode book and key paths therefore survive direct Windows bundle use and the published Windows bundle smoke lane without character corruption.
- Fixed read-only SQLite contention under real bundled concurrency. Read-only query and report workflows now classify initialized-book readiness from one lock-light structural contract instead of rerunning the full integrity audit on every command, while explicit inspection keeps the deeper audited path; concurrent bundled list and trial-balance bursts therefore stop surfacing spurious `storage-runtime-failure` responses from transient `SQLITE_IOERR_LOCK` failures.
- Fixed contract-level amount validation drift so published money values now reject signed and decimal `minorUnits` deterministically at the boundary, and the checked-in Jazzer regression seeds follow that exact unsigned-integer doctrine.
- Fixed Docker rebuild durability and rerun hygiene so the container builder now stages the toolchain pieces that `jlink --strip-debug` requires, Python helper-tool freshness is covered by dependency automation, wrapper validation obeys workflow concurrency rules, and release-tag verification distinguishes first publication from immutable-tag reruns.
- Fixed release-verification cross-platform drift so the public-release PR gate now treats an absent GitHub workflow run as pending evidence instead of crashing before the aggregate `Gate` materializes, the Windows published-bundle smoke lane now proves the Docker-managed SQLite container build plan against the host platform's real absolute mount paths instead of a Unix-only test fixture, and bundle reproducibility verification now measures the extracted file mtimes that survive cross-platform archive extraction instead of non-portable directory mtimes while rejecting non-ZIP-portable odd-second normalized artifact timestamps before they can leak into a published bundle contract.
- Fixed release-version drift in the public-tag gate. Release-candidate verification now rejects any tag whose first parent already carried the same version, so the published tag must be the commit that introduces that release version onto the default-branch line rather than a later repair commit that merely inherited it.
- Fixed the CLI diagnostics-channel contract so machine-readable rejections and failures now publish on stderr instead of stdout, and machine-mode internal defects keep one parseable `internal-error` envelope without interleaved raw stack traces.
- Fixed FinGrind-owned Docker bind-mount residue during writable and maintenance workflows. SQLite activity markers and maintenance leases now use directory-shaped coordination artifacts instead of file-shaped metadata entries, so mounted volumes no longer accumulate FinGrind-owned `.smbdelete*` marker or lease remnants.
- Fixed public-doc and example drift so the storefront README, install and quick-start guides, CLI reference, request guide, and checked-in examples now agree on the published host matrix, stderr diagnostics contract, period naming, bundle verification steps, and the renamed maintenance options.

## [0.54.0] - 2026-06-14

### Changed

- Bumped the repo-owned Python lint baseline to Ruff `0.15.17`.
- Hard-broke structural-governance waivers onto one exact-snapshot contract across both Java and Python verifier surfaces. Reviewed waivers now carry one approved live snapshot plus one named reviewed role, not a second hand-maintained budget copy, and Stage 1 structural governance now scans tracked JSON resources through explicit contract-catalog, example-payload, tooling-config, and harness-topology families.
- Hard-broke three reviewed production god-file owners into narrower seams. Period-result close planning now delegates close-horizon validation, holding-account selection, draft construction, and debit-credit tallying to dedicated owners; workflow execution now isolates boundary-failure context and step-state lifecycle; and SQLite posting SQL now lives in query-family owners instead of one oversized literal catalog.
- Hard-broke the CLI discovery and machine-output surface onto one tighter contract. Commands that advertise `--output` now honor one session-wide `FINGRIND_DEFAULT_OUTPUT=text|json` selector instead of switching by stdout capture state, bare invocation now opens with one terse front-door help view, and structured stdout uses one compact canonical JSON layout across discovery envelopes plus raw request and plan template emission.
- Hard-broke report as-of semantics away from the vague “current book horizon” label. Trial-balance and financial-position outputs now resolve the effective date explicitly to either the selected date, the latest posting effective date in the selected book, or the no-postings state, and comparative windows derive from that resolved boundary instead of one unresolved null placeholder.
- Hard-broke the supported CLI packaging surface again so the retired Gradle distribution tasks now fail fast with guidance toward `:cli:bundleCliArchive` or the source-checkout launcher, and bundle packaging prints the archive, checksum, and manifest paths directly after each successful build.

### Fixed

- Fixed reviewed-surface drift and orphan handling so Python reviewed waivers now fail when a tracked file disappears or when any approved metric changes in either direction, matching the Java source-shape verifier instead of letting the two engines diverge.
- Fixed structural-governance ownership drift so the Python verifier no longer carries JSON inventory, duplication, and reviewed-surface orchestration in one oversized module, and the canonical PMD XML artifacts now stay synchronized with the build-logic rule owner wording.
- Fixed release-control and contract-fixture drift so the reviewed-surface policy contract now lives as one directory of single-scenario JSON cases instead of one oversized mixed catalog, and the PR Gate verifier now reports failing required owners before blaming an unmaterialized matrix shell.
- Fixed distribution/operator traps so `scripts/structural_governance/cli.py` now works as a direct repo script as well as a package module, and `:cli:stageDockerBuildContext` now removes the misleading checkout-local `cli/build/docker-context` path from the supported build surface by quarantining stale legacy contexts before current staging runs.
- Fixed discovery parser and help/report ergonomics so mistyped discovery flags now surface nearest-option suggestions even on the top-level `help` entrypoint, checked-in request and ledger-plan template fixtures stay synchronized with the live compact JSON output, and the CLI guide now documents the session-default output contract plus the resolved as-of language.

## [0.53.0] - 2026-06-13

### Changed

- Bumped the repo-owned baseline to SQLite3 Multiple Ciphers `2.3.5` with SQLite `3.53.2`, JaCoCo `0.8.15`, Error Prone `2.50.0`, NullAway `0.13.6`, Jackson Databind `3.2.0`, PMD `7.25.0`, SQLFluff `4.2.2`, and Alpine `3.24`; the managed runtime, container, docs, and verification surfaces now describe one current baseline, and the legacy JaCoCo snapshot metadata/scripts plus the retired vendored SQLite3 Multiple Ciphers `2.3.4` / SQLite `3.53.1` tree are gone.
- Hard-broke scaffold and discovery ownership again: `ContractTemplates` now owns posting and account-declaration scaffolds, `ContractPlanTemplates` owns AI-agent ledger-plan scaffolds, placeholder values use one `replace-before-commit-*` vocabulary, `capabilities --output json` defaults to the compact grouped descriptor surface, and help plus launcher guidance now names the canonical raw-module and container-mounted invocation forms.
- Hard-broke the contract protocol catalog and protected-book maintenance together: per-operation builders now own help/examples/discovery, while backup, restore, and rekey-rollback recovery now stage from verified books, verify restored targets before commit, and keep encrypted maintenance audit compensation tied to the verified live book.
- Hard-broke structural governance onto canonical owners: reviewed Java surfaces now use full-snapshot approvals with execution-time expiry and orphan detection, PMD derives from one build-logic source across main, test, and Jazzer, tracked Markdown scans the repository with explicit exclusions, and coverage verification reads the shared GA JaCoCo pin directly.

### Fixed

- Fixed Java source-shape evidence so newline-terminated files no longer overcount physical lines, duplication-exempt reviewed waivers no longer bypass stale-waiver removal, and reviewed-surface reports publish the approved full snapshot beside the live measurement.
- Fixed Kotlin and PMD structural-governance drift so receiver functions, `fun interface` owners, and nested-type counts use a token-aware collector, Jazzer production inherits the main production policy, Jazzer test re-adds `GodClass` deliberately, and `NcssCount` remains excluded because file and method size ownership lives in structural governance.
- Fixed maintenance verification, public-doc drift, and release-promotion wait churn so staged backup, restore, and rollback recovery reject invalid artifacts deterministically; the storefront README, install and quick-start guides, release protocol, launchers, and managed-SQLite contract docs agree on discovery defaults, template vocabulary, launcher grammar, runtime provenance, and release-prep generated-block sync; and the PR and merge-handoff verifiers now return as soon as the canonical `Gate` passes on the target commit, even when the observational Windows non-public bundle smoke lane is still running.

## [0.52.0] - 2026-06-05

### Changed

- Bumped the repository-owned static-analysis baseline to NullAway `0.13.5` and Ruff `0.15.16`.
- Hard-broke the live bookkeeping theory onto one explicit doctrine owner. `BookIdentity` now carries one composed `BookDoctrine` made from the accounting-kernel profile, accounting basis, framework position, entity form, and seed template, and the built-in owner-managed-service cash doctrine now drives discovery, examples, and operator-facing labels from one source.
- Hard-broke the public bookkeeping operation surface onto one narrower cash-bookkeeping kernel. Typed cash revenue, cash expense, equity contribution, and equity withdrawal remain the primary operating entries; opening balances now enter through the structured `OPEN_ACCOUNTING_POSITION` flow; direct administrative entries are reversal-only; and public request and workflow surfaces reject retired mixed-branch and legacy adjustment fields instead of tolerating them.
- Hard-broke the operator and runtime surface onto deterministic contracts. `help`, `version`, and `environment` now default predictably instead of switching by stdout interactivity; runtime facts and publication facts are split in the environment descriptor; prompt-plus-machine-output combinations now refuse deterministically; protected-book filesystem violations publish the stable `invalid-book-file-path` contract; and report/query text, CSV, and PDF surfaces now use one human display grammar with lighter doctrine wording and explicit financial-position equation verdicts.
- Hard-broke onboarding and package guidance onto one canonical first-run story. The storefront README, bundle README, quick-start guide, request templates, and checked-in examples now share the seeded owner-managed service template and one concrete first-post request; public self-contained bundles are Linux-only; macOS and Windows are routed to the published container or source-checkout paths; and source-checkout plus direct-Java launchers now use one Gradle-owned Java 26 toolchain manifest instead of shell-local Java discovery.
- Hard-broke structural-governance and release-control ownership farther across the repository. Reviewed waivers are now self-removing and time-aware, repo-local audit mirrors publish structural evidence from the checkout, tighter PMD and source-shape budgets force earlier file splits, managed SQLite packaging and container assembly share one Gradle-owned provenance pipeline, and draft-first immutable release publication remains the only supported release path.

### Fixed

- Fixed same-target Linux Docker publication so the Docker build context now stages its own Alpine-linked managed SQLite library instead of reusing the host-managed build, and normal SQLite verification no longer rewrites the committed protected-book compatibility fixtures during every gate run. The long-running Jazzer pruning regressions now emit progress under slower CI runners instead of tripping the release-surface stall watchdog.
- Fixed operator guidance drift so text help, machine help, discovery ladders, bundle quick-start content, Docker/source-checkout hints, and example documents all describe the same live command surface instead of overlapping older flows.
- Fixed public distribution proof so bundle verification, module-identity checks, Linux container publication, checksum and attestation guidance, and launcher/runtime contract tests all prove the same packaged surface instead of parallel partial stories.
- Fixed SQLite runtime and lifecycle failure paths so managed-runtime inspection, failed-open native cleanup, protected-book security preconditions, passphrase-source reading, and source-checkout runtime verification now share deterministic contract-owned failure shapes.
- Fixed draft-first release replay, staged-asset attestation, helper-root handoff, pinned JaCoCo snapshot verification, Windows publication-lane ownership, and long-running Jazzer release-gate keepalive behavior so post-tag repairs no longer rediscover the `0.51.0` release-control defects one at a time. The aggregate release `Gate` contract now blocks only on the Linux-owned public publication proof surfaces; the Windows non-public bundle smoke lane remains visible as observational coverage without owning release promotion. Jazzer replay and finding-list wrappers now pin repo-verification lock ownership to the wrapper process, so fast-fail and JSON tool paths do not strand stale lock owners during release-surface verification, and the replay-wrapper regression now verifies that wrapper probes return the repo lock to its inherited baseline state when the full repository gate owns the parent verification lock. Jazzer white-box patch staging now depends explicitly on the `executor` and `sqlite` fixture archive producers before those archives are expanded, which closes the hosted release-gate race on cold caches. Release-surface shell verifiers also no longer assume Bash 4-only `mapfile`, which keeps the protocol runnable on the repository's macOS Bash baseline.

## [0.51.0] - 2026-06-03

### Changed

- Bumped the release-workflow artifact staging actions to the current Node24-backed pins. `release.yml` now uses `actions/upload-artifact` `v7.0.1` and `actions/download-artifact` `v8.0.1`, and the release-workflow contract test now locks those publication-staging pins so deprecated Node20 runtime drift cannot quietly re-enter the release path.
- Bumped the repository-owned Python lint pin to Ruff `0.15.15`, the included-build Kotlin pin to the stable `2.4.0` GA line, and the pinned JaCoCo snapshot artifact contract to build `0.8.15.202606030734` resolving as `0.8.15-20260603.073432-117`.
- Hard-broke book identity, doctrine, and initialization onto one explicit built-in bookkeeping profile. Initialized books now persist and publish the accounting-kernel profile, accounting basis, framework posture, entity form, and seeded book template together, while `open-book` now initializes the owner-managed service template as the canonical first-run book shape.
- Hard-broke bookkeeping operation language toward one narrower cash-kernel write model. Public write flows now treat the seeded owner-managed service template as the canonical operating shape, use one structured `OPEN_ACCOUNTING_POSITION` workflow for initial balances, and reserve the remaining direct administrative path for explicit reversal adjustments instead of the broader retired opening-balance and manual-adjustment vocabulary.
- Hard-broke the public workflow and request boundary onto narrower contract owners. Public
  interaction limits now live at the protocol boundary instead of in the accounting core,
  ledger-plan parsing rejects recognized-but-illegal branch fields deterministically, CLI
  workflow facts now use canonical wire-value owners instead of raw discriminator strings, and
  the root verification surface now owns the Jazzer gate as a first-class repository check.
- Hard-broke operator discovery and help toward one cleaner storefront. `help` now stays
  operator-first, terminal JSON defaults to pretty-printed output, discovery focuses own distinct
  detail ladders instead of coarse overfetch-only tiers, and request and report guidance now
  branch from the shortest runnable path before deeper machine-contract material.
- Hard-broke seed-template guidance and checked-in public examples into one coherent progression. The seeded owner-managed service template remains the canonical first-run path, while extra declaration fixtures are now named and documented as supplemental template extensions rather than alternate zero-state starters.
- The self-contained public CLI bundle is now the primary Gradle `assemble` outcome. `assemble` now owns the bundle archive manifest that names the emitted archive and checksum paths, while the raw shadow JAR remains an internal packaging input rather than the primary distributable.
- Release publication now stages one Linux container image per supported public bundle target and
  promotes the verified staging images onto the public version and `latest` tags only after the
  staged release surface passes verification. Historical public GHCR package versions are no
  longer culled by a timestamp cutoff during release publication.
- Hard-broke structural governance farther across the repository control plane. PMD now fails
  god-class, method-count, complexity, and coupling violations; source-shape budgets fail
  oversized Java files; duplication checks reject large repeated translation-heavy blocks;
  reviewed structural inventory now owns every near-ceiling production Java surface; and Python
  support scripts and SQLite schema SQL now sit under the same structural governance surface.
  Markdown docs and Gradle build scripts now live under executable structural budgets too, reviewed
  waivers expire against frozen approved shape, CLI JSON fallback budgets are tighter, and managed
  SQLite runtime consumers now opt in explicitly through the dedicated
  `dev.erst.fingrind.managed-sqlite-consumer` plugin instead of path heuristics.
- Hard-broke the build and publication control plane onto manifest-owned, immutable release
  surfaces. GitHub release publication now stages and verifies draft assets before final
  promotion, while bundle and container publication share one manifest-owned archive and staging
  surface instead of log-scraped or checkout-mirrored handoff paths.
- Hard-broke managed SQLite packaging and container assembly onto one shared provenance-owning
  pipeline. The public bundle and the public container now derive their managed SQLite library,
  checksum, toolchain fingerprint, and build contract from the same Gradle-owned native surface,
  with Linux-target staging verified before Docker image assembly.
- Hard-broke the included-build control plane into narrower capability owners. Root conventions
  now delegate formatting, Python/SQL verification, coverage, and Jazzer wiring to focused
  collaborators, while Java conventions now delegate runtime, quality, and coverage wiring
  instead of carrying those concerns inline.

### Fixed

- Fixed GitHub Release publication so public tagged assets are immutable after promotion.
  Publication now verifies staged bundles, checksums, attestations, and public container surfaces
  before the release is finalized instead of deleting and replacing already public assets under
  the same tag.
- Fixed operator-surface discovery and reporting output so human help no longer interleaves
  machine-reference blocks with first-run guidance, primary query examples no longer force early
  artifact export, report and PDF identity context render one concept per row instead of
  slash-packed summaries, and response-contract discovery now exposes a meaningfully smaller
  compact slice rather than mirroring the full surface.
- Fixed starter-workflow example cohesion so checked-in posting examples, plan examples, starter
  chart language, and supplemental declaration examples now describe the same live seeded chart
  instead of mixing starter and extension flows as if they were interchangeable.
- Fixed book-administration validation so duplicate active result-holding declarations fail at
  declaration time, with the public rejection surface naming the singular-account invariant
  directly instead of surfacing that conflict only later during period close.
- Fixed passphrase-source handling so prompt, stdin, and file-backed secret bytes now flow through
  one zeroizing limit-owning path instead of separate partial readers with different cleanup
  behavior.
- Fixed SQLite runtime, bootstrap, and protected-book fixture truth so managed-runtime inspection,
  source-checkout runtime verification, staged protected-book fixtures, and public compatibility
  metadata all agree on the current format-owned contract.
- Fixed SQLite verification task hygiene so ordinary `:sqlite:test` and `:sqlite:pmdTest` runs
  no longer regenerate the committed protected-book fixture family inside source-controlled test
  resources. Protected-book fixture refresh now remains an explicit maintenance task instead of a
  release-gate side effect.
- Fixed Docker publication staging so Linux hosts no longer short-circuit container assembly onto
  the source-checkout managed SQLite runtime when the host and container classifiers match. The
  staged Docker context now remains target-toolchain-owned, and container acceptance now carries
  explicit target-architecture wiring for reproducible local and hosted verification.
- Fixed pre-merge published bundle smoke so CI now reads bundle archive and checksum paths from
  the Gradle-owned bundle manifest instead of scraping `bundleCliArchive` console output. The
  release gate now verifies the same machine-owned bundle handoff contract before merge and
  during public release publication.
- Fixed the Jazzer replay and regression-corpus surface so replay wrappers tolerate real
  verification-lock cleanup timing and the committed corpus follows the live request and
  ledger-plan grammar, seeded template accounts, and workflow semantics instead of retired fields.

## [0.50.0] - 2026-06-01

### Changed

- Bumped the current release-control and formatting dependency pins to the presently accepted
  mainline set. The CI and container workflows now use `docker/setup-buildx-action` `v4.1.0`,
  `docker/setup-qemu-action` `v4.1.0`, `docker/login-action` `v4.2.0`, and
  `docker/metadata-action` `v6.1.0`, while Gradle build tooling now pins `com.gradleup.shadow`
  `9.4.2` and `com.diffplug.spotless` `8.6.0`.
- Hard-broke structural governance into repository-owned seams instead of filename-privilege
  exceptions. PMD now fails god-class, method-count, complexity, and coupling violations;
  source-shape budgets fail oversized Java files; duplication checks reject large repeated
  translation-heavy blocks; reviewed structural inventory now owns every near-ceiling production Java surface;
  and Python support scripts and SQLite schema SQL now sit under the same structural governance surface
  instead of living outside the mechanical governance model.
- Hard-broke several large control-plane and bookkeeping-adjacent ownership seams to match that
  governance model. CLI command families, protected-book maintenance, SQLite posting SQL, SQLite
  native/result-code seams, contract discovery metadata, and template-validation owners now flow
  through narrower responsibility-held collaborators instead of broader mixed-purpose files.
- Hard-broke operator discovery and report exports toward one clearer public contract. Help text
  now leads with a runnable zero-state lifecycle that includes key-file and request-scaffold
  creation, request-file command help now shows scaffold-plus-run examples in the primary `Try It`
  path, machine discovery can be narrowed by focus and command category instead of forcing one
  coarse payload tier, and CSV outputs now publish explicit export-family, row-identity, and
  relation semantics instead of command-local ad hoc dialects.
- Hard-broke PDF and empty-scope report presentation toward answer-first surfaces. Short PDF
  reports now lead with the statement tables and compress context into a lighter metadata band,
  empty read/report text no longer borrows fictive posting dates, and list-account output now
  separates financial-position and profit-or-loss classifications instead of collapsing them into
  one overloaded human column.
- Hard-broke initialized-book identity and bookkeeping-kernel policy selection onto one explicit
  executable accounting-kernel owner. Book identity now persists and publishes the built-in
  country-agnostic-bookkeeping-kernel profile, protected-book format `22` stores that owner
  durably, and SQLite fixtures, protocol facts, request/inspection payloads, examples, and docs
  now agree on the same profile-bearing book boundary.
- Hard-broke period close from a caller-assembled sequence into one atomic capability with durable
  contiguous close-horizon enforcement. Period-result transfer now commits through one owned
  transfer surface, while ledger-plan JSON models reject impossible close/result shapes before
  execution instead of relying on deeper executor failure paths.
- Hard-broke the managed SQLite runtime identity surface to one sidecar-consistency contract.
  Bundle-managed and source-checkout-managed runtimes now verify against one sibling `.sha256`
  file, the machine-readable runtime trust basis names match that public contract, and Windows
  bundle archives now publish the canonical `bin\fingrind.ps1` launcher without the retired
  `bin\fingrind.cmd` compatibility wrapper.
- Hard-broke runtime-image and container assembly onto explicit reproducibility owners. Runtime
  module derivation now validates optional `jdeps` misses against one repo-owned allowlist before
  module closure is computed, and the Docker build now pins both base images and Alpine package
  revisions instead of floating on mutable tags or repository package updates.

### Fixed

- Fixed SQLite runtime/bootstrap truth surfaces so managed-runtime unavailability, native bootstrap
  failures, shutdown faults, and source-checkout runtime inspection now preserve causality and
  publish the current runtime contract instead of collapsing into looser failure shapes.
- Fixed release, protocol, example, and operator documentation drift so the current structural
  governance surface, accounting-kernel profile ownership, protected-book format `22`, and live
  request/inspection/report contracts now agree across checked-in docs, examples, fixtures, and
  verification scripts.
- Fixed public discovery and validation truth so top-level help no longer presumes missing key or
  request files, query/report discovery summaries now use the current book horizon language
  instead of the retired latest-posting wording, and ledger-plan date repair hints preserve the
  dotted request path in their corrective guidance.
- Fixed protected-book maintenance target handling so `backup-book` and `restore-book` now own
  missing nested output parent directories with owner-only protection, and the bundle/container
  acceptance workflows prove those maintenance paths from the shipped public surfaces instead of
  pre-seeding insecure destination directories.
- Fixed late-cycle release-control regressions so the contract-operation lint no longer falls over
  on large escaped source literals, SQLite key-file security coverage is proven directly across
  platform filesystem capability seams, and the Jazzer replay wrapper regression now tolerates
  brief repo-lock cleanup windows for non-lock contract paths inside the Stage 5 shell gate.

## [0.49.0] - 2026-05-28

### Changed

- Hard-broke structural governance beyond production Java code. God-file, duplication, and
  ownership pressure now applies across Gradle build logic, release shell surfaces, production
  Java, and test Java, with oversized CLI, contract, SQLite, and verifier helpers split into
  smaller responsibility-owned seams instead of broad utility files.
- Hard-broke the release-control plane into narrower owners. Release-smoke contract checks,
  structural-governance verification, launcher verification, and public-container verification now
  live under focused repo-owned script and helper surfaces instead of larger mixed-purpose test and
  wrapper files.
- Hard-broke bookkeeping statement doctrine out of the technical `executor.bookkeeping.read`
  slice. Financial-position, income-statement, and changes-in-equity computation now live under
  `executor.bookkeeping.reporting`, while `BookkeepingReadService` remains an orchestrator over
  query and reporting outcomes.
- Hard-broke operator discovery and help toward one clearer front door. `help --output text` is
  now the task-first operator surface, `capabilities --output text` is the factual inventory
  surface, and command help now prefers one direct runnable example before scaffolds, machine
  schemas, and deeper contract detail.
- Hard-broke the protected-book secret seam back to one tighter SQLite package boundary. Key-file,
  passphrase, and resolver types now live under `dev.erst.fingrind.sqlite`, the exported
  `dev.erst.fingrind.sqlite.secret` package is gone, raw passphrase-byte copy helpers are no
  longer public, and the internal native bridge is split into narrower purpose-owned collaborators
  instead of wider raw-handle reachability.
- Bumped the Gradle build-logic Kotlin pin from `2.4.0-RC` to `2.4.0-RC2`.

### Fixed

- Fixed the public-container verification contract so the mounted `trial-balance --output text`
  check now proves the current published section surface instead of an older pre-refactor header
  ordering. The verifier and its regression harness now assert the active `Trial Balance`,
  status, totals, accounts, and context blocks independently, matching the shipped container
  output used by release publication.
- Fixed `--pdf-out` command semantics so PDF artifact creation is now part of the requested
  operation. Report commands fail with deterministic `pdf-export-failure` instead of succeeding
  with one warning when the requested PDF cannot be written.
- Fixed discovery category serialization so machine-readable help and capabilities now publish
  stable `OperationCategory` wire values instead of deriving protocol strings from Java enum
  constant names.
- Fixed SQLite text decoding so exact-length UTF-8 values survive embedded-NUL round trips instead
  of truncating at the first zero byte during readback.
- Fixed the canonical repository check entrypoint so product verification now defers to the
  project-owned Gradle toolchain baseline instead of rejecting environments based on ambient
  `java` / `javac` product versions before the wrapper can apply the build contract.
- Fixed the front-door and supporting docs so the README quick start, operator guides, request
  guides, developer guides, and report examples now match the live trial-balance surface, current
  PDF failure semantics, current Java-toolchain ownership, and the moved bookkeeping reporting
  package.
- Fixed empty-scope read and report surfaces so no-result list pages are result-first and CSV
  empties now publish one explicit granularity vocabulary: scope-empty for list/query scopes,
  section-empty for empty report sections, and report-empty for whole-report absences.

## [0.48.0] - 2026-05-27

### Changed

- Hard-broke the machine request contract onto one shared owner for request-field inventories.
  Posting, declare-account, and ledger-plan parsers now read their accepted field sets from the
  same contract-owned vocabulary that discovery publishes, and ledger-plan date bounds now enforce
  the same canonical `YYYY-MM-DD` grammar as the rest of the machine request surface.
- Hard-broke the CLI failure boundary so uncategorized software defects no longer publish one
  generic runtime message. Public machine envelopes now expose the opaque `internal-error` code
  with one error id, while full stack traces move to the diagnostics stream.
- Hard-broke the CLI and executor publication seams away from omnibus routers. Discovery,
  mutation, book-read, report, plan, and failure rendering now flow through narrower bounded
  writers, while bookkeeping read pages, reports, and statements project through separate
  published-language translators and matching focused tests instead of one coupled read-side
  surface.
- Hard-broke the posting-register and account-ledger CSV surfaces into normalized row-kind
  contracts. One-to-many relations such as posting accounts, ledger counterpart accounts, source
  documents, and approvals now publish as dedicated child rows instead of packed multi-value CSV
  cells, and account-ledger opening and closing totals now live only in explicit summary rows.
- Hard-broke discovery and operator help toward one clearer front door. `help --output json` and
  `capabilities --output json` now default directly to the compact discovery tier, text help now
  leads with `Start Here` / `Next Step` task guidance instead of mixing workflow and grammar
  equally, and text capabilities now separate operator overview from machine-contract retrieval.
- Hard-broke Java structural governance from convention-only discipline to mechanical repository
  gates. PMD now fails god-class, method-count, complexity, and coupling violations; repo-owned
  source-shape budgets fail oversized Java files; duplication checks reject large repeated
  translation-heavy blocks; and architecture tests prove primary module boundaries and cycle
  freedom.
- Hard-broke the protected-book adapter and local verification tooling into narrower public seams.
  Protected-book access now opens through dedicated administration, read, posting,
  interim-result-sweep, fiscal-year-close, plan-execution, and rekey session families, while local Jazzer operator
  commands now flow through focused replay, finding, seed-promotion, and seed-audit owners
  instead of one broad utility surface.
- Hard-broke CLI and PDF report rendering into report-family owners plus shared support for text
  tables, statement sections, balance layouts, and page theming instead of multipurpose renderer
  accumulation.

### Fixed

- Fixed the managed SQLite distribution contract to fail closed when required or forbidden compile
  option lists are missing, null, malformed, blank, duplicated, or unexpectedly empty.
- Fixed Python tool resolution so repository checks now require the exact pinned Python baseline
  instead of probing unrelated local interpreter versions from `PATH`.
- Fixed protected-book passphrase and rollback failure surfaces so key-file, prompt, and rollback
  copy failures publish only redacted public path hints or opaque source labels such as `key file`,
  `standard input`, and `interactive prompt`.
- Fixed the user and developer docs to match the current `internal-error` contract, classified
  runtime exit codes, diagnostics-stream repair flow, and the current protected-book format line.
- Fixed the source-checkout launcher, release-smoke workflow, bundle field-audit harness, and
  checked-in report examples so the verifier surface now proves wrapped help output, normalized
  CSV/report empty and comparative contracts, and the canonical non-`--output` `execute-plan`
  failure path.
- Fixed text report and export churn across the read/report family. Account-ledger, trial-balance,
  financial-position, income-statement, changes-in-equity, and period-summary surfaces now use
  result-first text ordering, compact context sections, explicit empty-state language, and
  message-bearing CSV empties with one `recordKind` envelope vocabulary.

## [0.47.0] - 2026-05-26

### Changed

- Hard-broke the protected-book format to version `21`. Canonical `YYYY-MM-DD` local dates and
  canonical UTC instants are now owned by one shared `CanonicalTemporalText` contract that drives
  CLI parsing, machine request schemas, SQLite persistence formatting, query bindings, and
  file-format checks.
- CLI distribution, source-checkout artifact, Docker-context, runtime-image, and bundle-manifest
  ownership now live in shared Gradle build logic instead of the CLI module build script. The
  generated checkout and Docker manifests now include the Gradle wrapper and build-logic inputs
  that govern the shipped artifacts.
- Normal CI now path-proves every published Unix bundle classifier before merge through the same
  repo-owned bundle-smoke contract used by release publication.
- Hard-broke the public response contract to one `ProtocolEnvelopeStatus` vocabulary
  (`ok`, `rejected`, `error`) across machine discovery, response descriptors, and JSON envelopes.
  `execute-plan` now keeps plan-family result state inside `payload.status` instead of inventing a
  command-family outer status.
- Discovery and help now hard-break into clearer public tiers. Minimal JSON discovery exposes the
  smallest machine index, compact discovery carries the stable command and request descriptors, and
  the text help front door now leads with one shortest successful path before the deeper grammar.
- Successful JSON surfaces that mention filesystem artifacts now publish redacted public-path hints
  instead of absolute operator filesystem paths.
- Text read and report surfaces now use one explicit empty-state vocabulary, preserve report
  section skeletons when filtered sections are empty, and keep account-ledger CSV summary facts in
  the summary rows instead of repeating them on every ledger-entry row.
- SQLite key-file, passphrase, and resolver ownership now lives under dedicated
  `dev.erst.fingrind.sqlite.secret` packages and tests instead of being mixed through the broader
  store/runtime package.

### Fixed

- Fixed distributed CLI artifacts so release bundles no longer embed absolute source-checkout or
  build-root paths in their JAR manifest/runtime metadata. Checkout-local path facts now remain a
  source-checkout launcher concern instead of leaking into the shipped binary surface.
- Fixed the source-checkout and direct-Java launchers so cached raw-JAR execution now refreshes by
  manifest SHA-256 proof of tracked source inputs instead of looser freshness heuristics.
- Fixed SQLite file-format drift by adding durable schema checks for canonical posting dates,
  recorded timestamps, source-document dates/capture timestamps, approval timestamps, period-close
  dates/timestamps, audit timestamps, and the closed `source_channel` vocabulary.
- Fixed machine-readable request schemas so date and timestamp fields now publish exact regex
  assertions together with `format` hints, matching the same canonical grammar the CLI enforces at
  execution time.
- Fixed the source-checkout launcher, runtime verifiers, public-container verifier,
  protected-book format contract docs, checked-in SQLite fixtures, and release-smoke/public-doc
  owners so the published contract now matches the live format-`21` storage surface, current
  discovery/help tiers, current `ProtocolEnvelopeStatus` envelope shape, current redacted-path
  policy, and current list-postings summary payloads.
- Fixed grouped maintenance path hints so backup, restore, and rollback responses now widen their
  redacted trailing path context just enough to keep sibling artifacts distinguishable instead of
  collapsing different files into the same visible hint.

## [0.46.0] - 2026-05-25

### Changed

- Discovery JSON now has an explicit three-tier contract. `help --output json` and
  `capabilities --output json` default to a minified `minimal` index for agents, while
  `--detail compact` exposes the stable command/output descriptors and `--detail full` keeps the
  exhaustive embedded schemas and doctrine surface.
- Hard-broke the operator-output vocabulary from the old audience-labeled mode to `text` across CLI parsing, machine
  discovery, help text, request docs, examples, release-smoke owners, and repo-owned renderer/test
  naming, so the public contract now names one representation instead of guessing its audience.
- `print-request-template` and `print-plan-template` now emit unmistakable placeholder-first
  scaffolds instead of live-looking demo provenance. The checked-in template corpus, quick starts,
  and request docs now publish replace-before-submit evidence and provenance fields together with
  the current `PERSON` default actor classification.
- Committed posting facts now preserve one durable `postingOriginKind` across `get-posting`,
  `list-postings`, `account-ledger`, ledger-plan journals, and the checked-in example corpus, so
  typed entry families survive durable storage and readback instead of collapsing into generic
  `postingKind` alone.
- Typed published entries now enforce their own bookkeeping semantics before journal derivation.
  Cash, equity, and administrative entry flows validate account type or classification
  compatibility plus accepted source-document types, and deterministic failures publish structured
  `entry-semantics-violations` instead of accepting any balanced journal shape that happens to post.
- Text account, posting, ledger, and statement views are now summary-first. The operator-facing
  read/report commands render compact tables instead of repeating the same facts as one
  multi-line block per row.
- `list-postings` JSON pages now return posting summaries rather than full posting bodies, so list
  reads expose stable identifiers, movement totals, source-document ids, and origin kinds without
  forcing agents to pay full posting-detail cost for every page entry.

### Fixed

- Fixed the public container verifier and tag-driven container publication checks so the mounted
  `open-book` workflow no longer emits a malformed blank argument token. The workflow-owned fake
  Docker regression harness now rejects unexpected verifier arguments instead of silently tolerating
  them, and the CLI deterministic-failure envelope now normalizes blank optional argument metadata
  to absence instead of crashing while rendering an invalid-request response.
- Fixed the Windows CI bundle lane so the repo-owned Defender exclusion owner now treats an
  unavailable Windows Defender service as a warning instead of a release-blocking workflow
  failure. The Windows product gate keeps proving the real Gradle, runtime, and bundle smoke
  surfaces, while the antivirus exclusion remains an optional performance optimization.
- Fixed posting and report CSV exports so they are now scalar-only and rectangular. Nested evidence
  metadata no longer appears as escaped JSON inside cells, `account-ledger` uses one repeated
  entry-row model instead of mixed opening/entry/closing record kinds, and the statement CSV
  outputs no longer depend on blank-heavy multiplexed row shapes.
- Fixed the last verbose period-summary fallback. Period-summary account activity now renders the
  same compact summary table shape as the other text read/report surfaces instead of collapsing
  back to one block per account.
- Fixed the interactive prompt contract across product, docs, and release verifiers. Prompt input
  now remains a `text`-only route, machine-output prompt requests are rejected deterministically as
  `invalid-request`, non-interactive text prompt failures are verified against the published
  `interactive-prompt-unavailable` contract, and the field-audit PTY harness now proves the real
  non-echo prompt behavior instead of recording terminal echo as product output.
- Fixed the release-smoke, source-checkout launcher, and public-container verification owners so
  they derive prompt, discovery, and output-shape expectations from the current published contract
  instead of pre-break verifier assumptions.
- Refreshed the operator and machine-facing docs to match the shipped discovery defaults, compact
  text report shapes, current scaffold semantics, and current example outputs.

## [0.45.0] - 2026-05-22

### Changed

- Narrowed the initialized-book identity and bookkeeping kernel to the live
  cash-oriented single-entity contract. `open-book`, `inspect-book`, request schemas, examples,
  and machine discovery now publish only entity name, business activity tags, functional
  currency, and fiscal-year anchor, and the protected-book format advances to `19`.
- Replaced the machine-discovery `accountingBaseline` and extension-shaped `policyPack` surfaces
  with a narrower `bookkeepingKernel` contract, so help, capabilities, request docs, and
  protocol references publish only the executable bookkeeping kernel instead of a broader
  standards-baseline posture.
- Replaced generic `MANUAL_ADJUSTMENT` bookkeeping-entry language with named administrative
  adjustment entry kinds (`OPEN_ACCOUNTING_POSITION`, `REVERSAL_ADJUSTMENT`, and
  `REVERSAL_ADJUSTMENT`) across the public request contract, checked-in examples, release-smoke
  fixtures, and fuzz/replay inputs.
- Neutralized equity and period-close vocabulary across the bookkeeping kernel. Legal-form
  identity and equity-classification assumptions are gone from the active public model,
  transfer-period-result now targets `RESULT_HOLDING`, and statements/readback use the same neutral
  equity taxonomy across Java, SQLite, CLI output, and examples.
- Renamed and rewrote the accounting ADR line around current executable truth:
  [ADR_ACCOUNTING_FOUNDATION.md](./docs/ADR_ACCOUNTING_FOUNDATION.md) replaced the old
  `ADR_10X_ACCOUNTING_FOUNDATION.md`, and
  [ADR_ACCOUNTING_KERNEL_SCOPE.md](./docs/ADR_ACCOUNTING_KERNEL_SCOPE.md) replaced the old
  `ADR_ACCOUNTING_BASELINE.md`.

### Removed

- Removed `entityForm` and `ownerModel` from initialized-book identity, `open-book` grammar,
  request schemas, examples, and machine discovery so the live public contract no longer carries
  dormant legal-form vocabulary.
- Removed the public `accountingBaseline`, `nextTarget`, `policyPack`, and extension-surface
  capability facts from discovery so unsupported broader accounting-foundation posture is no
  longer published as live machine truth.

### Fixed

- Realigned the public-container release verifier with the shipped compact text trial-balance
  surface. The operator-side anonymous pull-and-run check and its shell regression harness now
  assert the current `Book ... | Currency ... | FY ... | Policy ...` header plus the published
  current-totals block instead of an older multi-line entity banner.
- Realigned the release-smoke workflow and source-checkout launcher verifier with the narrowed
  book-identity contract and named administrative adjustment vocabulary, removing retired
  entity-form, owner-model, and generic manual-adjustment assumptions from the public verification
  surface.
- Refreshed the README, quick-start guides, protocol references, request docs, and checked-in
  example corpus so the published snippets show the live bookkeeping-kernel and administrative-adjustment surfaces instead of
  superseded intermediate contract shapes.

## [0.44.0] - 2026-05-22

### Added

- Added a typed public bookkeeping-entry write contract. `preflight-entry`, `post-entry`,
  `print-request-template`, `execute-plan`, the machine-discovery schemas, and the checked-in
  example corpus now center on stable `entryKind` event shapes (`CASH_REVENUE`, `CASH_EXPENSE`,
  `EQUITY_CONTRIBUTION`, `EQUITY_WITHDRAWAL`, and `MANUAL_ADJUSTMENT`) instead of one generic raw
  posting-request object.
- Added durable retained-evidence facts to the public bookkeeping surface. Source documents now
  carry document date, capture timestamp, storage locator, and lowercase SHA-256 digest metadata,
  while approvals now carry approver identity, approver type, explicit decision, and approval
  timestamp facts across the contract, examples, and replay/fuzz fixtures.

### Changed

- Narrowed the protected-book identity and policy model to the executable bookkeeping kernel.
  `open-book` now selects entity name, entity form, owner model, business activity tags,
  functional currency, fiscal-year anchor, and one persisted policy profile, and the
  protected-book format advances to `15` with canonical month/day fiscal-year storage plus the
  same owner-model vocabulary enforced across Java, SQLite, examples, and machine discovery.
- Posting application now translates the published typed bookkeeping-entry events through the
  selected accounting policy profile instead of exposing dormant accounting-basis behavior as a
  first-class public contract.
- Account declaration and chart hierarchy now distinguish `HEADER` versus `POSTABLE` nodes,
  require parent/child statement-classification parity, and surface the same hierarchy doctrine
  through the SQLite schema, CLI request schemas, examples, and report readers.
- Trial-balance and statement/report payloads now publish richer readback facts: trial balances
  include book-wide totals plus an explicit balanced verdict, report criteria use as-of
  language for point-in-time reads, and comparative date ranges remain first-class in the
  canonical report contracts.
- Text and machine discovery are now layered more deliberately. `help` is the operator task
  guide with copy-safe literal command grammar, explicit request-document scaffold/contract lookup
  cues, and compact text report headers, while `capabilities` remains the shared machine
  inventory for automation and release-surface verifiers.
- Refreshed the checked-in examples, quick-start guides, and storefront docs so the published
  snippets show the live typed-entry, retained-evidence, policy-profile, and compact-report
  surfaces rather than older placeholder or pre-refactor interface shapes.

### Removed

- Removed `PostingRequest` as the primary public write contract. Raw journal mechanics now remain
  only inside the explicit `MANUAL_ADJUSTMENT` bookkeeping-entry path.
- Removed `ReportingObligationStatus`, `AccountingBasis`, and the dormant
  `AccountingBasisPolicy` seam from the active book identity and bookkeeping policy model, along
  with the related public wording that implied behavior FinGrind did not execute.

### Fixed

- Fixed the published machine error surface so `responseModel.errorDescriptors` now carries one
  canonical `exitCode` per deterministic error, and aligned the release-smoke plus
  source-checkout launcher verifiers to consume those published values instead of stale private
  numbers.
- Fixed the text CLI operator surfaces so command help no longer hard-wraps invocation or option
  grammar mid-command, request-document sections label scaffold and contract lookup commands
  explicitly, and repeated report/register identity headers collapse into one stable compact
  book-context row instead of reprinting the same multi-line banner on every screen.
- Fixed the source-checkout and developer direct-Java wrappers so they now verify one generated
  source-hash manifest before executing the cached raw JAR, refresh `:cli:shadowJar` plus
  `prepareManagedSqlite` automatically when the checkout has moved ahead of that artifact, and
  keep the checked-in request/plan template captures aligned with the live typed-entry contract
  instead of silently replaying stale launcher bytes.
- Fixed the bundle and release-smoke evidence fixtures so they now emit real lowercase SHA-256
  digests for retained source-document metadata, and updated the launcher/release verifiers to
  keep long-running local checks alive with explicit progress pulses instead of timing out on
  quiet-but-healthy work.
- Fixed PDF artifact export permissions on POSIX hosts so `--pdf-out` now publishes mounted report
  files with host-readable permissions instead of preserving the private temp-file mode across the
  final move, added a portable default-filesystem fallback that verifies one host-readable artifact
  contract instead of treating platform-specific permission-mutation return values as the public
  invariant, and tightened the public container verifier plus its regression harness so mounted
  book/key/PDF workflows now run as the caller's numeric `UID:GID` and host read failures are
  reported as unreadable mounted PDF artifacts rather than being misclassified as non-PDF content
  failures.
- Replaced the aging third-party Windows MSVC developer-command GitHub Action with a repo-owned
  PowerShell bootstrap that locates `VsDevCmd.bat`, exports the full developer-command
  environment for subsequent steps, parses cleanly under PowerShell before execution, and removes
  the Node 20 deprecation warning from the CI and release publication workflows.
- Fixed the post-tag release replay seam so `workflow_dispatch` reruns now materialize
  workflow-owned publication helpers from `main` while rebuilding the immutable tag checkout,
  which keeps the repaired Windows MSVC bootstrap and release verifiers available even when the
  tagged source line predates those helper files.
- Fixed the tagged container publication replay seam so `container.yml` now materializes
  workflow-owned publication helpers from `main` before it rebuilds the immutable tag checkout,
  which keeps post-tag container-verifier and release-asset verifier repairs live during reruns
  instead of silently replaying stale tag-owned helper scripts.
- Fixed the release workflow's Windows MSVC bootstrap step to remain valid YAML after the
  helper-root replay refactor, and tightened the repo-owned MSVC regression owner so malformed
  workflow YAML is rejected before GitHub drops the release workflow's dispatch contract.

## [0.43.0] - 2026-05-20

### Added

- Added a public `10/10 Accounting Foundation` ADR and refreshed the developer/core theory-holder
  references so the repository now states the exact truth-ownership doctrine plus the hard-break
  bounded-context order from the current bookkeeping kernel toward the broader accounting
  foundation.

### Changed

- Posting requests, committed posting facts, posting-history/report payloads, and ledger-plan
  posting facts now carry first-class accounting evidence. Source-document references are required
  on caller-authored postings, approvals remain first-class optional references, the canonical
  request templates and examples include the evidence scaffold, and the protected-book schema moved
  to format `12` with dedicated `posting_source_document` and `posting_approval` child tables.
- `open-book` now requires explicit owner-model, reporting-obligation, and business-activity
  doctrine instead of leaving new books semantically under-specified, and `inspect-book` plus the
  text report headers now publish those identity facts together with one direct close-readiness
  summary instead of waiting for transfer-period-result rejection to surface that doctrine.
- The tagged container publication workflow now runs the same mounted-book and native-provenance
  verifier the Step 9 operator release protocol uses, so automated post-publish checks and manual
  public-surface verification enforce one shared container contract instead of two different
  depths.
- `print-request-template` and `print-plan-template` now emit runnable agent-first sample
  documents with demo evidence and provenance values instead of `replace-before-commit-*`
  placeholders, and the quick-start/example corpus now uses that same sample-first contract.
- `execute-plan` now returns one aggregate summary plus the optional full execution journal
  instead of duplicating per-step digests under both `payload.summary` and `payload.journal`, and
  command help now folds advisory notes into example guidance instead of rendering them as peer
  grammar sections.
- Text discovery and machine output are more sharply separated: `help` is the front-door task
  guide, `capabilities` is the reference/machine-contract inventory, JSON responses are emitted as
  pretty-printed documents, and long text-facing book paths are compacted so inspection and
  report surfaces stay readable on narrower terminals.

### Fixed

- Fixed the bundle and container release-smoke acceptance fixtures so their canonical sale and
  adjustment requests now include the same mandatory evidence bundle the public posting contract,
  shipped request template, and bundle-smoke verifier require.
- Fixed the public container verifier's native-provenance probe so it checks published files
  through an explicit shell entrypoint inside the container rather than routing `test -s` through
  the FinGrind CLI entrypoint and reporting a false missing-file failure.
- Fixed the source-checkout launcher verifier, checked-in examples, and replay/fuzz fixtures so
  they now exercise the same explicit open-book identity and posting-evidence contract the public
  CLI enforces, instead of asserting older optional-profile or placeholder-era request shapes.
- Fixed the Step 9 public-container verifier and its regression harness so the mounted-book smoke
  path now seeds the same explicit open-book identity doctrine and mandatory posting evidence that
  the released CLI contract requires.
- Fixed report and query CSV evidence serialization so nested source-document and approval JSON is
  emitted in a deterministic field order, and refreshed the checked-in text/report example
  fixtures to match the current expanded book-identity header contract.
- Fixed the text path-display contract on Windows so root paths normalize to the same forward-slash
  presentation rule the rest of the compact text CLI surfaces use, and aligned the focused CLI
  regression test with that cross-platform display contract.

## [0.42.0] - 2026-05-20

### Changed

- Protected-book maintenance now records successful backup, restore, and rollback recovery facts
  inside the encrypted `audit_event` stream instead of through an adjacent plaintext maintenance
  journal, the then-current rollback-deletion command required one explicit live-book passphrase source, and the
  maintenance workflow compensates those in-book audit facts when backup publication or rollback
  deletion fails before the external filesystem mutation completes.
- The public CLI example corpus is now replayed from live commands through one deterministic
  fixture harness, and the checked-in examples were refreshed to the normalized book-format-11,
  exit-taxonomy, and report/discovery contracts the shipped binary actually emits.
- Posting admission and period close are now carried by smaller local bookkeeping owners: posting
  acceptance composes focused validation policies behind one explicit policy-pack seam, and period
  close planning now lives in `PeriodResultTransferPlanner` while `PeriodResultTransferService` coordinates
  lifecycle/store access and durable close persistence.
- Statement reporting is now split across dedicated financial-position, income-statement, and
  changes-in-equity calculators, with `BookkeepingStatementService` reduced to a coordinator over
  the local read/report slice instead of one multi-statement doctrine sink.
- Text discovery, inspection, and report output now follow one wrapped front-door contract across
  packaged, source-checkout, developer direct-Java, and raw modular launchers: grouped command
  catalogs, stable section headings, trimmed whitespace, and narrower line widths are now the
  published text-output baseline.
- Read-only SQLite native opens now keep in-process active-connection accounting without
  publishing sibling activity markers, so diagnostic and query paths no longer create marker
  artifacts merely to inspect one protected book.
- Upgraded the shared JUnit BOM to `6.1.0` and moved the Java-26-ready JaCoCo pin forward to the
  exact 2026-05-19 snapshot artifact `0.8.15-20260519.201139-107`, with the developer docs kept on
  the same published coordinates.

### Fixed

- Release-verifier headroom now matches the live CI fan-out observed during the `0.41.0` release:
  the PR Gate and merge-handoff verifiers now wait longer by default before declaring timeout, and
  the release protocol documents the same explicit timeout override path for both pre-merge and
  post-merge verification.
- Cross-platform CLI surface checks are tighter at the owner seams: shared text-format helpers now
  emit one deterministic wrapped-line contract independent of host line endings, and published
  example-fixture canonicalization now normalizes path-bearing JSON and text fixtures through the
  same owned temporary-path rules on Windows, macOS, and Linux.
- Fixed release-surface and source-checkout launcher regressions that were asserting retired help
  headings or one exact unwrapped launcher line; verification now proves the live grouped-help
  contract the launchers actually publish.
- Fixed bundle and release-smoke verification drift around text posting registers so acceptance
  checks and checked-in workflow fixtures now follow the current card-style posting surface the
  shipped binary emits.
- The release protocol now states explicitly that the Step 1 baseline gate runs before the version
  sweep, so any bundle and Docker artifact names emitted there reflect the pre-sweep checkout
  version; the Step 2 post-sweep rerun remains the authoritative release-version proof.
- The release protocol now treats any Step 2 staged-diff blob or tree read failure as a checkout
  object-store defect that requires the release to move into a clean clone before publication can
  continue.

## [0.41.0] - 2026-05-19

### Changed

- Protected-book maintenance now runs through an explicit executor-owned maintenance model instead
  of SQLite-owned workflow glue: `backup-book`, `restore-book`, and the public rekey-rollback
  inspection, restore, and deletion workflows now verify initialized sources, rollback artifacts,
  and restored targets through one typed verification surface, emit durable encrypted maintenance
  audit facts, and keep operator-selected artifact outputs distinct from redacted maintenance
  diagnostics.
- CLI help, machine discovery, and canonical examples now describe the same command grammar more
  precisely: action-specific maintenance requirements are surfaced explicitly, operator guidance is
  separated from first-class grammar sections, and report/query outputs use the normalized versus
  redacted path vocabulary consistently across text, JSON, bundle-smoke, and Docker acceptance
  surfaces.
- The current bookkeeping kernel was narrowed to executable present-day meaning: inert
  reporting/basis identity labels, dormant tax/FX/evidence policy seams, and unused
  organization/reporting identity types were removed from the active model, while workflow
  assertions now preserve structured effective-date ranges instead of degrading them to nullable
  bounds.
- Managed-SQLite runtime and release provenance contracts are tighter: discovery now distinguishes
  publisher-authenticated bundle runtimes from source-checkout-managed local-build runtimes more
  clearly, managed-toolchain probing and compiler-flag rendering verify the release contract more
  aggressively, and the public developer/runtime references align with that stronger trust
  vocabulary.
- Root Python helper-tool verification now runs through a pinned repo-owned `uv` launcher instead
  of direct ambient-package imports: `fingrindUvVersion` now pins the launcher bootstrap, Ruff is
  pinned at `0.15.13`, SQLFluff `4.2.1` now lints the canonical SQLite schema through the
  repo-owned `gradle/sqlfluff/sqlfluff.cfg` contract, the shell verification entrypoints now
  auto-resolve a Python `3.12+` runtime through `uv` when the ambient `python3` is older, hosted
  CI now bootstraps the pinned `uv` launcher explicitly before Gradle-owned Python tool tasks run,
  and the developer references point contributors at the new bootstrap contract.
- The canonical SQLite schema renamed `book_meta.key` to `book_meta.meta_key` and tightened the
  recursive account-cycle trigger query so the SQLFluff-checked schema, generated schema
  reference, and SQLite adapter vocabulary stay aligned.

### Removed

- Removed the broad `SqliteBookSession` public seam and the old SQLite-owned backup, restore, and
  rekey-recovery service entrypoints in favor of narrower administration, read, posting,
  period-close, plan-execution, and rekey session surfaces plus the executor-owned maintenance
  boundary.

### Fixed

- Fixed maintenance-lease and recovery workflows that could previously strand follow-up operations
  behind generic runtime failures or filename-only rollback selection; recovery and restore paths
  now reject invalid maintenance artifacts deterministically and verify the resulting protected
  book before reporting success.
- Fixed report-export and diagnostics path drift so explicit artifact payloads publish normalized
  paths while maintenance and request-file failure surfaces redact local filesystem topology by
  default.
- Fixed release-surface Python runtime support so repeated shell sourcing, uv fallback resolution,
  and fresh-bundle verification remain deterministic across operator environments instead of
  depending on whichever host Python executables happen to be present.
- Fixed the Gradle-owned Python tool gate on hosted Windows so Ruff and SQLFluff no longer depend
  on a multiline `python -c` version probe: the shared uv task owner now verifies Python through
  the standard `python --version` banner and build-logic regression tests guard that
  cross-platform parser directly.
- Fixed repository-hygiene and release-publication drift around orphaned Git coordination locks:
  the hygiene verifier now rejects shared or worktree Git `.lock` files before release-sensitive
  verification begins, and the release/developer runbooks now spell out the live-owner versus
  orphaned-lock decision path instead of leaving staging failures to ad hoc operator recovery.
- Fixed another release-path hygiene blind spot around suppressed Git housekeeping: repo hygiene
  now rejects a persisted `.git/gc.log` before publication or checkout-sensitive verification, and
  the release/developer runbooks now require a successful manual `git gc` cleanup before that log
  can be cleared.

## [0.40.0] - 2026-05-18

### Added

- Added first-class closed-book maintenance workflows: `backup-book`, `restore-book`, and the
  explicit rekey-rollback inspection, restore, and deletion commands now ship as public CLI
  commands with structured JSON and text result shapes, deterministic rejection contracts, and
  matching maintenance/regression coverage.
- `inspect-book` now publishes the active migration-policy facts alongside lifecycle and format
  metadata so operators and automation can distinguish the current hard-break format line from a
  generic compatibility summary.

### Changed

- Narrowed the public bookkeeping kernel and protected-book identity back to executable ledger
  concepts: `open-book` and the protected-book schema no longer carry first-class tax-profile
  payloads, the live protected-book format advances to version `8`, and the public theory holders
  now describe tax, FX, source evidence, and richer reporting as adjacent contexts rather than
  live kernel-owned state.
- Discovery surfaces now default to JSON when stdout is redirected, inline accepted enum
  vocabularies for request-file commands, and separate operator notes from command grammar more
  clearly.
- Text inspection and maintenance output now render explicit entity and reporting rows, clearer
  restore/rekey secret continuity, and more precise transfer-period-result outcome language instead of
  compressed profile summaries or ambiguous empty sections.
- Managed SQLite runtime publication and verification now align around artifact-side trusted
  checksum sidecars across the bundle, source-checkout, Docker, and release-surface verifier
  paths.

### Removed

- Removed scaffold-only public business-event contracts from `contract.operations` and placeholder
  advanced-reporting contracts for cash flows, comprehensive income, and disclosure packs until
  those surfaces have owning executable contexts and durable storage.
- Removed leaked shared-kernel placeholder types for tax profiles, tax codes, FX measurement and
  evidence, source evidence, business-event lifecycle, inventory, and adjacent reporting
  classifications, together with the matching SQLite tax-profile codec and example payloads.

### Fixed

- Release publication now gives the public container workflow enough release-asset wait budget to
  survive slower GitHub-hosted platform bundle builders before multi-arch image publication
  begins.
- Tightened the protected-book lifecycle and theory-holder surfaces again: `backup-book`,
  `restore-book`, and the explicit rekey-rollback inspection, restore, and deletion commands now
  validate path collisions, backup-pair verification, rollback-artifact handling, and secret
  reuse deterministically across CLI, SQLite, schema, and docs.
- Raw `java -jar` discovery/help no longer leaves misleading native-access noise, successful PDF
  exports no longer emit a harmless direct-buffer warning, single-record text views keep exact
  durable identifiers, and the checked-in examples and rendered text fixtures now match the live
  request and output contracts.
- Tightened release-surface verification again so the direct-Java SQLite runtime verifier exercises
  the prepared-checkout runtime path it claims to prove, and the contract schema-key floor now
  includes the maintenance-operation payload keys introduced by the new lifecycle commands.
- Windows CI runtime verification no longer carries ad hoc inline PowerShell probes: the
  direct-Java and source-checkout SQLite runtime checks now delegate to canonical PowerShell
  verifier scripts that mirror the release-surface shell owners and their wrapper contracts.

## [0.39.0] - 2026-05-17

### Changed

- Expanded the initialized-book identity and transport contract so books now persist a first-class
  `taxProfile`, `open-book` accepts `--tax-profile-file` for registered books, and the durable
  protected-book format advances to version `7` for that wider identity payload.

### Fixed

- Hardened the remaining Windows release-gate test seams: CLI key-file and workflow tests now
  harden their temporary directories to the same owner-only contract as production secrets,
  hosted Windows launcher regression coverage now asserts runtime-specific launcher examples, the
  managed-SQLite ACL regression test now drives one deterministic ACL failure instead of a
  POSIX-only path assumption, the interactive-console prompt bridge no longer loses coverage on
  hosted Windows when the Unix-only PTY probe is unavailable, and stale-handle period-close
  coverage no longer leaks one live initialized database handle during cleanup on Windows.
- Replaced the last host-bound SQLite coverage assumptions with host-independent filesystem-fixture
  proofs: rollback-artifact scan failures and managed-library private-snapshot permission paths
  now execute through deterministic fixture seams instead of depending on POSIX-only host
  behavior, so the Windows release gate proves the same SQLite coverage contract as macOS and
  Linux.
- Fixed the Windows managed-SQLite verification snapshot hardening seam so owner-only ACLs now
  keep the copied `sqlite3.dll` executable by that owner, preventing Windows native-runtime loads
  from failing after snapshot verification.
- Split the `Windows bundle smoke` CI job's root-gate and build-logic verification into separate
  fail-fast steps, and tightened the release-surface regression so one failed Windows Gradle run
  cannot be masked by later PowerShell steps in the same job.
- Aligned the SQLite test infrastructure with the Windows owner-only filesystem contract: SQLite
  integration and key-file tests now harden their temporary book and secret directories before
  use, and ad hoc fixture key directories no longer inherit broader Windows temp-root ACLs that
  would make release-gate book initialization fail only on hosted Windows runners.
- Refined the field-tested operator and AI-agent CLI surface again: posting-ledger and statement
  views now keep full posting identities in text and PDF outputs, derived statement rows no
  longer expose internal synthetic line codes, reversal wording now names posting lineage
  directly, `rekey-book` success results now publish the replacement secret source, no-op period
  closes render explicit empty generated-posting output, and the canonical checked-in examples now
  use concrete identity/reporting/tax statuses, stable illustrative paths, and a runnable
  reversal example that exactly negates the published basic posting request.
- Suppressed the harmless Java 26 PDFBox direct-buffer unmapping warning during successful PDF
  report exports, so text, bundle, and packaged CLI runs no longer emit alarming stderr noise
  when they write valid PDF artifacts.
- Repaired raw application-JAR usability for discovery and failure handling: help output now
  publishes a truthful `java -jar` launcher example, `capabilities` reports the missing
  native-access prerequisite without triggering JVM warnings, and raw JAR command failures now
  stop cleanly before any SQLite FFM lookup begins.
- Restored full opaque identifiers in single-record text result views and mutation confirmations
  so operators and AI agents can copy posting, command, causation, and idempotency values
  without truncated detail blocks.
- Restored deterministic administration rejection ordering for missing books so
  `declare-account` rejects as `administration-book-not-initialized` before any chart or
  hierarchy validation runs.
- Aligned the source-checkout launcher regression and public quick-start guidance with the
  book-and-key directory security contract so FinGrind now consistently assumes it may create a
  missing private parent directory itself while refusing any pre-existing non-private directory.
- Replaced the reopened generic `BigDecimal` FX seam with one canonical `ExchangeRate`
  plain-decimal grammar so exchange-rate evidence now owns its exact quote normalization without
  reintroducing a shared decimal escape hatch into product Java surfaces.
- Realigned the public docs, checked-in examples, root README output excerpt, and rendered SQLite
  schema reference to the live `0.38.x` contract, including the widened book identity payload,
  narrower account-ledger CSV export, live `execute-plan` summary structure, and supported book
  format version `7`.
- Moved the published container image off the operator override path and onto the same
  bundle-managed SQLite runtime contract as the extracted archive, so container runtime probes and
  release-surface checks now report one publisher-authenticated native-library seam.
- Repaired the managed SQLite native-toolchain fingerprint owner on Windows: the build-logic
  probe now captures `cl.exe` and `link.exe` version banners using platform-correct invocation
  semantics instead of assuming Unix-style `--version` support, and the shared build-logic tests
  now guard that Windows probe contract directly so Windows bundle smoke is no longer the first
  detector for MSVC toolchain regressions.

## [0.38.0] - 2026-05-16

### Changed

- Bumped the pinned Gradle wrapper distribution to `9.5.1` while preserving the repo-owned
  wrapper launcher behavior that externalizes project cache, build-logic, JaCoCo, and ordinary
  project-build state outside the checkout by default.
- Upgraded Spotless to `8.5.1` and aligned the repository formatting floor, release-surface
  regressions, and shared build logic to the same formatter baseline.

### Fixed

- Tightened the accounting-foundation contract again: books now persist an explicit entity profile
  and accounting basis instead of only a bare entity name plus currency anchor, the public
  baseline now declares one exact current target and next target, reporting coverage and missing
  baseline capabilities are published structurally instead of only through prose exclusions, and
  the neutral single-entity policy pack now owns an explicit published inventory of accounting
  policy dimensions and extension seams rather than only one comparative-reporting hook.
- Hardened the Java and SQLite engineering boundary again: machine-contract request schemas are
  now deeply immutable instead of only top-level frozen, SQLite native passphrase copies are
  zeroized through an explicit native-secret owner, Java compile conventions no longer force
  every main compile out of date, native-access JVM permissions are now scoped to the SQLite
  foreign-function seam, and the Linux CI gate now runs the canonical `./check.sh` pipeline
  instead of restating a parallel source of truth.
- Tightened the internal execution and CLI seams again: the bookkeeping executor storage boundary
  is now split into narrower lifecycle, validation, read-model, write, and ledger-plan ports;
  workflow execution keeps typed plan and step identifiers instead of demoting them to raw
  strings; posting requests are sealed and exercised through exhaustive policy logic; CLI command
  option specs now reject duplicate or overlapping grammar declarations; and the application
  bootstrap now carries one explicit runtime-environment seam for stdin, stdout, stderr, and
  clock ownership instead of partially hardwiring process globals.
- Tightened the shared-kernel and discovery contract surface again: request-file guidance now maps
  explicit operation outcomes instead of hiding behind a nullable default branch, internal
  effective-date range usage now names unbounded versus bounded state directly, `CurrencyBalance`
  now keeps only canonical debit/credit totals while deriving net amount and balance side on
  demand, and semantic text-boundary normalization is now applied more consistently across money,
  identifiers, contract resources, and SQLite runtime/bootstrap inputs.
- Repaired the remaining build-logic and regression-floor drift: aggregated JaCoCo coverage wiring
  now stays provider-backed instead of eagerly realizing subproject tasks and files at
  configuration time, stale nested Java class outputs are pruned by exact source-owner manifests
  instead of deleting whole compile output trees, Jazzer workflow fixtures and replay coverage now
  speak the new narrow executor store ports directly, and the CI release-surface regression now
  verifies the canonical root `./check.sh` gate owner instead of a duplicated workflow-local
  stage definition.
- Reduced the remaining contract and SQLite god-seam load: machine-contract quick starts and
  request templates now come from dedicated contract-catalog owners instead of one imperative
  assembler path, and SQLite lifecycle/mutation orchestration now delegates secret buffering,
  rekey execution, and ledger-plan transaction coordination to narrower internal services with
  direct regression coverage.
- Repaired the post-tag container publication rerun seam: `container.yml` now publishes from the
  staged Docker context mirrored at `cli/build/docker-context` instead of reopening the repository
  root, and the container-workflow regression plus release-publication docs now guard that shared
  staged-context assembly boundary explicitly.
- Repaired the tag-driven release publication workflow after the repo-hygiene build-root
  externalization change: `release.yml` now uses the archive and checksum paths reported by
  `:cli:bundleCliArchive` itself instead of guessing checkout-local `cli/build/distributions/...`
  paths, the bundle-output task now publishes one machine-readable path contract for both Bash and
  PowerShell workflow owners, and the release-workflow regression and bundle-pruning proofs now
  guard that contract so post-tag reruns can republish the existing immutable tag safely.
- Repaired the post-tag release rerun seam again: `release.yml` now accepts both the current
  machine-readable bundle-path lines and the older text-labeled bundle-path lines that an
  immutable tagged source can emit during `workflow_dispatch`, and the release-publication docs
  plus the release-workflow regression now guard that mixed `main`-workflow versus tagged-source
  handoff explicitly.
- Repaired the SQLite protected-book proof floor again: committed compatibility fixtures now
  match book-format version `6` and the current schema fingerprint, direct SQLite account-row
  test fixtures now carry the same taxonomy invariants enforced by the live schema, and the
  explicit fixture-refresh task now stages refreshed artifacts into test runtime resources
  without making ordinary SQLite verification rewrite committed source fixtures.
- Repaired the remaining release-smoke and launcher-surface drift: `account-ledger` CSV
  acceptance checks now validate the current narrow row contract semantically instead of pinning
  an obsolete denormalized header shape, and the source-checkout, developer direct-Java, bundle,
  and discovery help surfaces now publish launcher examples and command hints through the active
  runtime launcher contract instead of flattening every surface to one generic token.
- Repaired the root release gate on mixed operator and CI platforms: the shared `./check.sh`
  monitor now measures log growth portably across BSD and GNU userlands instead of assuming macOS
  `stat -f`, and the release-surface regression floor now proves that portability explicitly so a
  green local release gate cannot hide a Linux CI failure.

## [0.37.0] - 2026-05-14

### Fixed

- Tightened the Docker/repository hygiene boundary again: container assembly now builds only from
  the staged Docker context emitted by `:cli:stageDockerBuildContext` instead of reopening the
  repository root, that staged context now includes the Dockerfile plus a full `source-root/`
  snapshot of every checked assembly input, the context manifest now verifies those staged files
  as well as their source fingerprint, and the Docker smoke gate plus developer docs now treat
  repository-root `docker build .` as intentionally unsupported.
- Tightened repository hygiene again at the Git storage boundary: the repo-hygiene verifier now
  proves the checkout is a real readable Git repository and fails fast on corrupt or unreadable
  object stores, its shell regression now covers a deliberately corrupt loose object, and the
  release protocol plus publication reference now distinguish the normal worktree path from the
  mandatory clean-clone fallback when shared repository metadata is damaged.
- Tightened repository hygiene at the checkout boundary: Stage 1 quality gates now verify the
  repository-root allowlist before any build runs, `./gradlew` now externalizes ordinary project
  build trees outside the checkout by default instead of leaving that behavior only to fragile
  mounted filesystems, and the new repo-hygiene cleanup tooling can prune empty root clutter,
  Finder droppings, generated caches, optional scratch state, and ignored tool/editor state
  without touching tracked source; the verifier's local-state report now also classifies each root
  and points at the exact cleanup flag that removes it.
- Tightened the remaining operator-facing read/export and PDF seams: close-sensitive account
  reads now accept an explicit `--posting-coverage` filter, account-ledger and period-summary CSV
  exports now preserve their full multi-section report meaning through `recordKind` rows, posting
  inspection surfaces now publish reversal state consistently, `execute-plan` now uses one stable
  `ok` envelope whose `payload.status` carries rejected and assertion-failed outcomes, and
  multi-page PDFs now render `n / nn` page labels while vertically centering both gray-band
  headers and body rows and suppressing zero-only opening-balance sections in dense ledgers.
- Tightened bounded account-ledger reporting again so selected date ranges now publish explicit
  zero opening and closing balances instead of omitting those buckets, while the bundle-acceptance
  verifier and CLI CSV regression coverage now prove the quoted-field row width that the public
  export contract requires.
- Tightened the remaining read/report and plan-transport seams so `account-balance` now returns
  the same report identity and PDF artifact metadata that sibling report commands already expose,
  posting inspection and ledger-style views now declare `postingKind` and posting-coverage
  semantics explicitly, `execute-plan` now defaults to bounded summary output while public docs
  and examples show `--result-detail full` wherever they rely on the full execution journal, and
  the text/PDF report layout now suppresses empty statement sections while vertically centering
  gray header titles and drawing matching top and bottom header rules.
- Repaired the operator-side public container verifier to use the current mounted-book contract:
  it now opens books with explicit identity fields and seeds postings with the required
  `postingKind`, while the paired shell regression and release-publication docs now track mounted
  lifecycle and posting-grammar changes as well as text `trial-balance` layout changes.
- Tightened the bookkeeping standards boundary and reporting-policy surface so `help` and
  `capabilities` now publish one machine-readable accounting baseline plus the sanctioned
  bookkeeping policy-pack extension seams, comparative statement data is now derived and carried
  through fiscal-year-anchored report payloads instead of date metadata alone, opening balances
  now reject deterministically after the first committed posting, and the
  request/response docs now match the live statement, PDF-export, and opening-balance contract.
- Hardened the public bookkeeping-kernel boundary again: `capabilities` and `help` now publish
  explicit small-entity and organizational-position facts, the extension surface now distinguishes
  the one live comparative-policy seam from adjacent future contexts such as tax, FX, subledgers,
  and consolidation, the accounting baseline now also states the current reporting boundary, flat
  chart model, operational-domain boundary, and non-first-class tax posture explicitly, and
  opening-balance admission is now a one-time pre-posting opening-statement window rather than a
  looser “before ordinary activity” rule.
- Tightened the packaged CLI discovery and report UX surface so command-scoped JSON help now emits
  one narrow command-local contract instead of dumping the full machine catalog, `help
  execute-plan` and `print-plan-template` now publish the same canonical ledger-plan template,
  `print-request-template` can emit the declare-account scaffold directly, JSON report success
  envelopes now publish PDF artifacts explicitly, account-ledger and period-summary now carry
  book identity, text/CSV/PDF reports now expose the comparative data and presentation labels
  already present in the model, and the checked-in public docs/examples now match that live
  transport exactly.
- Realigned the bundle and release-smoke acceptance workflow to the current account-ledger CSV
  contract, so the shared verifier now expects the public identity-prefixed ledger header and rows
  that the shipped CLI actually emits instead of the retired pre-identity CSV layout.
- Realigned the source-checkout launcher shell regression to the current discovery contract, so the
  raw developer JAR now proves the generic front-door `help` surface and the direct-Java launcher
  syntax where it actually belongs in command-scoped help instead of asserting the retired
  runtime-specific quick-start block.

## [0.36.0] - 2026-05-14

### Changed

- Bumped the included Gradle build-logic compiler pin to Kotlin `2.4.0-RC2` and moved the
  Java-26-ready JaCoCo snapshot pin forward to `0.8.15-20260513.074320-106`, with the matching
  developer-build references updated to the same live coordinates.
- Hardened the current bookkeeping foundation around explicit book identity and reporting scope:
  `open-book` now requires entity name, functional currency, and fiscal-year start, the same
  identity is now returned by `open-book` and `inspect-book`, the canonical AI-agent
  `print-plan-template` scaffold now carries the nested `openBook` shape explicitly, and the
  command docs, quick-start guides, examples, and bundle README now match that live contract.
- Tightened the current reporting and provenance model for transfer-period-result and statement surfaces:
  period-close now records system-generated provenance with a non-CLI source channel, future-dated
  closes reject explicitly, close ranges may not cross the configured fiscal-year boundary,
  trial-balance/report contracts now expose whether closing entries are included, every statement
  report now carries book identity plus one comparative effective-date range, and the durable
  protected-book format is promoted to version `4` for the expanded lifecycle, identity, and
  opening-balance model.
- Promoted the caller-authored posting surface from one implicit journal family to one explicit
  doctrinal choice: direct posting requests and AI plan templates now declare `postingKind`,
  FinGrind accepts `STANDARD` and `OPENING_BALANCE` for caller-authored requests, opening-balance
  postings may seed only asset, liability, or equity accounts, every posting must match the
  selected book functional currency, and transfer-period-result now targets one explicit retained-earnings
  account instead of relying on a singleton retained-earnings bucket in the schema.
- Declared FinGrind's current accounting-standards baseline explicitly: the repo now names a
  country-agnostic bookkeeping-core target informed by the IFRS conceptual layer and functional
  currency doctrine, while also stating that full cash-flow, OCI, note/disclosure, and
  multi-currency translation layers remain intentionally out of scope for the current core line.

### Fixed

- Tightened the root README opening prose so the front page now explains FinGrind in direct,
  concrete bookkeeping language instead of relying on metaphor-heavy wording.
- Tightened the packaged CLI transport contract so every successful JSON command now uses one
  top-level `status: "ok"` envelope instead of mixing `ok`, "preflight-accepted", "committed",
  and "plan-committed", while the operation-specific meaning now lives consistently inside the
  payload. The same cleanup also corrected the published request/response examples, account-ledger
  CSV example, release-smoke verifiers, and operator docs to the live hard-break contract.
- Repaired the operator-side public container verifier so release publication now checks the
  current text `trial-balance` surface, including the first-class `Account type` and
  `Account role` columns, and aligned the mock-backed shell regression harness to the same
  mounted-book statement contract.
- Corrected contra nominal-account arithmetic so contra revenue and contra expense balances now
  offset profit and loss in the right direction, period-close generated entries remain balanced
  when contra nominal accounts are present, financial-position current-period-result projection now
  respects the same accounting doctrine, and the direct doctrine tests now assert the corrected
  sign rules instead of teaching the inverted behavior.
- Realigned the Jazzer operator and replay proof floor to the current hard-break bookkeeping
  contract: committed request fixtures now include the explicit `postingKind` field, the shared
  replay helpers open books with matching functional currency, the round-trip/workflow coverage
  harnesses speak the current `open-book` and trial-balance/report grammar, and the seed-audit
  / replay tool tests now verify the live operator surface instead of retired request shapes;
  the deterministic replay fixtures now use the required nested `openBook` payload everywhere,
  the SQLite and replay lifecycle status mappings now share one rejection-to-status owner, and
  the full Jazzer wrapper gate is back in sync with the current bookkeeping contract.
- Brought the release and bundle acceptance workflow onto the same explicit book-identity contract
  as the shipped CLI: the shared release-smoke runner now passes `--entity-name`,
  `--functional-currency`, and `--fiscal-year-start` to `open-book`, and it verifies that
  initialized books echo the same identity back in the public JSON response.
- Updated the SQLite schema-document regression harness to track the live protected-book format:
  the renderer regression now asserts the current canonical `user_version = 4` body instead of
  the retired version-2 snapshot, so release-surface verification checks the real schema line
  rather than failing on an obsolete alpha-era expectation.
- Hardened the Stage 5 release-surface proof workflow itself: the canonical release-surface
  runner now announces each subcheck before it starts, and the long Jazzer replay/seed wrapper
  regressions now emit deterministic progress checkpoints so healthy verification runs do not get
  killed as false stalls by the root-gate watchdog.
- Brought the source-checkout launcher regression onto the live initialization contract: the
  launcher and direct-Java smoke path now open books with explicit entity name, functional
  currency, and fiscal-year start, and the regression proves that both launcher surfaces echo the
  same `bookIdentity` back instead of only checking for a generic ok status. The same verifier now
  requests text `help` output explicitly when it is asserting text guidance, so the regression
  matches the shipped interactive-vs-redirected stdout contract instead of relying on a stale
  pre-hard-break default.

## [0.35.0] - 2026-05-13

### Changed

- Promoted account doctrine into the current public and durable bookkeeping model:
  `declare-account` and declared-account/report payloads now carry first-class `accountType` plus
  immutable `accountRole`, period close and retained-earnings handling are now explicit public
  operations rather than implicit future theory, FinGrind now publishes an explicit flat-chart and
  opaque account-code policy instead of leaving chart semantics implicit, and the public contract
  package is now split into semantic bookkeeping, workflow, discovery, and runtime subpackages
  rather than one flat DTO namespace.
- Promoted the protected SQLite book format to version `2` and hardened the current alpha storage
  line around the intended model directly: the account registry now persists `account_type`, the
  durable book now carries an append-only `audit_event` stream plus immutable-row triggers for
  committed posting, journal, and audit rows, and current FinGrind rejects older book formats
  instead of carrying migration code or compatibility shims.
- Added explicit aggregate and storage decision references for the current model: the docs now
  publish named consistency boundaries for lifecycle, account registry, posting ledger, reversal,
  idempotency, workflow transaction, and audit stream ownership, and they now publish the durable
  rationale for pinning SQLite `journal_mode=DELETE` on the current storage line.
- Added first-class financial-statement surfaces to the current bookkeeping model: the query/report
  contract now includes financial position, income statement, and changes in equity outputs, with
  the CLI, report rendering, discovery contract, and documentation aligned to the same named
  accounting surfaces.

### Fixed

- Moved exact balance arithmetic out of the SQLite adapter and into the shared accounting kernel,
  added direct fault-injection and bypass-corruption coverage for SQLite commit atomicity and
  book-open integrity, and tightened the public/user/developer references so request scaffolds,
  report shapes, format-version guidance, and schema references all match the implemented model.
- Tightened the accounting proof floor around the new statement and transfer-period-result surfaces:
  multi-currency statement ordering, loss-side current-period-result projection, undeclared
  profit-and-loss bypass resilience, period-close currency bucketing, and audit-event payload
  validation are now covered directly, while the shared JaCoCo XML verifier now reads only
  report-root coverage counters and the remaining dead close-policy/audit-validation branch
  artifacts were removed from the implementation.
- Fixed the operation-id discovery contract drift for transfer-period-result, `financial-position`,
  `income-statement`, and `changes-in-equity` so bundle verification, release-surface scripts,
  and other machine readers now load the same canonical semantic mapping that the published
  protocol enum and CLI discovery catalog expose.
- Updated the shared release-smoke and bundle/container acceptance expectations for the
  first-class `accountRole` column now emitted by `account-ledger --output csv`, so the public
  acceptance floor matches the current exported report surface instead of the retired
  pre-doctrine header shape.
- Tightened the packaged CLI operator surface around request repair, transfer-period-result guidance, and
  statement presentation: invalid account doctrine now rejects as `invalid-request` instead of
  `runtime-failure`, command-scoped help for request-file commands now inlines canonical templates
  plus accepted fields and enums, text rejections now surface repair hints and typed details,
  successful transfer-period-result output now reports the retained-earnings account and closed totals,
  the first transfer-period-result now accepts leading empty days before the earliest posting while later
  closes remain strictly contiguous,
  text financial statements now render named sections and totals instead of raw transport tokens,
  `print-request-template` now rejects stray flags precisely, and successful `rekey-book`
  verification no longer warns about its own transient rollback copy.
- Cleaned up the PDF statement surfaces so the packaged financial-position, income-statement, and
  changes-in-equity exports now use readable black text plus corrected vertical spacing that keeps
  section rules and table borders from cutting through headings or row text.

## [0.34.0] - 2026-05-10

### Changed

- Replaced the public decimal-string money seam with one exact-money model across core,
  contracts, CLI, workflow facts, reporting, and PDF rendering: `CurrencyUnit` now owns
  ISO-backed currency semantics and minor-unit scale from FinGrind's pinned registry snapshot,
  `Money` and `PositiveMoney` now store exact minor units instead of `BigDecimal`, public request
  and response payloads now use typed money objects with `currencyCode` and `minorUnits`, and
  journal-entry/report rendering now projects one shared canonical money model instead of mixing
  free-form decimal text with formatter-local fallback rules.
- Promoted the protected-book format to schema version 2 and broke durable journal-line storage
  from free-form decimal text to exact `amount_minor` plus `currency_code`, while SQLite
  open-time verification now proves the schema fingerprint,
  `integrity_check`, `foreign_key_check`, persisted money integrity, and durable double-entry
  balance instead of trusting only table presence and initialization markers.
- Added explicit exact-money transport bounds at the machine and CLI edge: `minorUnits` is capped
  at the 19-digit signed-64-bit non-negative range, and every request JSON document is capped at
  `1048576` UTF-8 bytes whether it is read from a file or standard input.
- Expanded the exact-money regression floor across zero-digit, two-digit, and three-digit currency
  scale buckets: committed Jazzer replay seeds now cover JPY and BHD request parsing, posting
  workflow, ledger-plan assertion execution, and SQLite round-trip durability, while focused core,
  CLI, PDF, and SQLite tests now prove exact parse/persist/render behavior across those same
  currency-scale families.
- Added a dedicated decimal-boundary reference and a repository guardrail that keeps product Java
  surfaces free of generic `BigDecimal` seams, so future tax rates, percentages, exchange rates,
  discounts, and allocation ratios must arrive as their own exact domain types instead of
  reusing the posted-money model.
- Hardened the shared Java coverage gate so each `Test` task now starts from a fresh JaCoCo
  execution-data file and module verification now fails on any missed line or branch reported in
  `jacocoTestReport.xml`, eliminating false negative drift between stale `.exec` files,
  generated reports, and the named coverage-verification task under the Java 26 toolchain.
- Promoted the repo-owned Python helper scripts into the canonical root verification surface:
  `check` now runs Ruff lint plus format checks over `scripts/**/*.py` through the shared root
  Gradle conventions, CI now pins Python explicitly with `actions/setup-python`, the contributor
  devcontainer now includes `python3 -m pip`, and the repo now ships pinned Ruff configuration and
  tool-manifest files instead of relying on ambient runner tooling.

### Fixed

- Removed the remaining dead string-money seams from committed Jazzer request corpora and replay
  metadata, regenerated the committed deterministic replay floor from the typed money contract, and
  aligned exponent-invalid replay assertions with the new authoritative `minorUnits` rejection
  boundary instead of the retired free-form `amount` parser message.
- Updated the shared release-smoke fixture generator, bundle acceptance workflow, and public
  container surface verifier to submit typed money request bodies with nested `amount`
  objects instead of the retired line-level `currencyCode` plus decimal-string `amount` shape,
  so shipped bundle and container acceptance now exercise the same exact-money contract that the
  CLI, workflow engine, and published examples describe.
- Replaced the hand-maintained SQLite schema reference with one generated document derived from the
  canonical `book_schema.sql`, so schema checks, durable money columns, version markers, indexes,
  and integrity posture cannot drift between the source schema and the published reference.
- Rewrote the remaining operator and machine-contract wording that implied the retired decimal
  money seam, so CLI help and contract schema descriptions now describe typed exact-money objects
  and ASCII-digit `minorUnits` instead of vague decimal-string amounts.

## [0.33.0] - 2026-05-08

### Added

- Added `docs/DEVELOPER_RELEASE_PUBLICATION.md` as the maintainer reference for GitHub Release
  publication topology, published-byte attestation rules, Windows ZIP canary behavior, neutral
  `gh release download` job constraints, and the safe `workflow_dispatch` repair path for
  workflow-only post-tag publication defects.
- Added project-owned Jazzer seed operators for promoting ad hoc replay inputs into committed
  regression seeds and for auditing the committed seed floor, including required coverage-intent
  metadata, duplicate-content detection, orphaned-input detection, and rejection of committed
  `unexpected-failure` expectations.

### Changed

- Hardened `jazzer/bin/seed-audit` into a full committed-corpus integrity check so it now reports
  unreadable metadata, escaped or missing input references, non-file inputs, and malformed
  committed `.json` seed bodies as first-class audit defects instead of surfacing them only
  through indirect failures.
- Tightened the Jazzer custom-seed operator surface so wrapper-side `--json` failures are
  machine-readable before Gradle starts, `promote-seed` now enforces corpus-wide
  `coverageIntent` uniqueness, and the seed-management help text prints the supported replayable
  target keys directly.
- Upgraded the managed SQLite baseline from SQLite3 Multiple Ciphers 2.3.3 / SQLite 3.53.0 to
  SQLite3 Multiple Ciphers 2.3.4 / SQLite 3.53.1 across the vendored amalgamation, managed-runtime
  contract metadata, Docker/build surfaces, nested Jazzer build, developer references, and
  operator-facing CLI/runtime documentation.
- Pinned JaCoCo to the newer Java-26-ready snapshot artifact `0.8.15-20260506.113836-98` and
  updated the developer build references to match the exact immutable coordinate resolved from the
  Sonatype Maven snapshots repository.
- Split executor and SQLite lifecycle inspection, query rejection, posting rejection, and
  workflow-fact models away from the public contract so the local seams now translate to
  `BookInspection`, `BookQueryRejection`, `PostingRejection`, and `LedgerFact` only at exported
  application-service or published-language boundaries.
- Pulled bookkeeping read semantics into `BookkeepingReadService`, bookkeeping posting semantics
  into `BookkeepingPostingService`, and workflow execution semantics into
  `BookWorkflowExecutionService`, while shrinking `BookReadService`, `PostingApplicationService`,
  and `LedgerPlanService` into published-language adapters and removing the fixture-only
  `commit(CommittedPosting)` production seam from `BookStore` and SQLite.
- Forced full main-source recompilation after stale-class pruning in both the shared Java build
  conventions and the nested Jazzer build so grouped top-level command classes are regenerated
  into emptied output directories instead of disappearing behind incremental compile drift.
- Pruned nested Jazzer processed-resource destinations before each real resource sync so renamed
  or deleted committed seeds cannot linger in cached `jazzer-build/resources/` outputs and skew
  packaged corpus behavior away from `src/fuzz/resources`.

### Fixed

- Split command-scoped help so executable examples and operator notes no longer share one raw
  `Examples` section, changed invalid invocation failures to default to text repair text
  unless a recognized machine output mode is selected explicitly, and aligned text
  deterministic contract-failure rendering on the `Rejected` heading.
- Hardened container-image assembly so `docker build` now verifies the staged
  `cli/build/docker-context/` payload against a SHA3-256 fingerprint of the current CLI,
  contract, core, executor, report-PDF, SQLite, and Gradle build inputs, which turns stale
  staged Docker contexts into loud build failures instead of silently packaging an older
  application jar or Docker entrypoint.
- Removed the source book's absolute filesystem path from rendered PDF report content and PDF
  metadata, tightened the protocol-owned public-distribution and managed-SQLite contract loaders
  so required canonical array keys cannot disappear into silent empty defaults, and replaced the
  last boolean book-initialization shortcuts in SQLite tests and helpers with the inspection-first
  lifecycle seam introduced by the local bookkeeping/workflow boundary refactor.
- Tightened the managed SQLite compile contract so the canonical protocol resource now owns the
  required compile options, forbidden compile options, and SQLite3MC secure-memory requirement in
  one place, while runtime discovery, bundle metadata, build logic, and shell verifiers all prove
  the same contract instead of mixing one required-subset check with separate handwritten flags.
- Fixed public-release provenance so `.github/workflows/release.yml` now attests the exact bundle
  and checksum bytes downloaded back from the published GitHub Release on one neutral post-upload
  job instead of attesting per-runner local artifacts, which closes the Windows publication drift
  where repository attestations could point at different digests than the shipped release assets.
- Fixed the neutral published-asset attestation job so `gh release download` now receives the
  repository explicitly, retries with `--clobber`, and prints the final GitHub CLI error on
  failure instead of looping through opaque download failures.
- Raised the release verifier job timeout to fit its explicit GitHub-release propagation retry
  budget, and documented that timeout/retry alignment as part of the release protocol contract.
- Clarified the public release protocol's worktree/bootstrap handoff for cases where a live
  FinGrind verification owner already holds the repo-wide verification lock, so release operators
  are told to wait or bootstrap into a clean worktree instead of deleting a live lock or starting
  competing verification in the same checkout.
- Hardened the tag-publication release workflow so published-asset attestation now runs on one
  dedicated neutral post-upload job with the exact OIDC, attestation, and artifact-metadata
  permissions required by `actions/attest`, and clarified the release protocol's recovery path
  for workflow-only tag-publication defects: fix `main` and rerun `release.yml` or
  `container.yml` with `workflow_dispatch` against the existing release tag instead of moving or
  duplicating the tag.
- Fixed release-rerun publication convergence so `publish-github-release.sh` now replaces
  same-named GitHub Release assets when their digest differs, the release workflow's own
  verification step now carries the same release-asset propagation retry budget as the container
  workflow, and `verify-github-release.sh` now reports the exact failing sub-check and asset name
  instead of collapsing every publication defect into one generic "missing or incomplete" error.
- Narrowed test-only null escape hatches from class-wide and method-wide opt-outs to exact
  typed-null call sites, tightened CLI/contract null diagnostics and payload typing, and replaced
  the SQLite store lifecycle's nullable field mesh with an explicit session-state model so the
  compiler, tests, and runtime contracts now describe the same state transitions.
- Replaced duplicate committed Jazzer seed bytes across posting-workflow and SQLite harnesses with
  harness-specific seeds, fixed the `jazzer/bin/seed-audit` zero-target shell path under
  `set -u`, and updated the committed seed inventory/docs to reflect the stricter seed-management
  contract.
- Clarified several committed Jazzer seed coverage-intent labels so `jazzer/bin/seed-audit`
  now names exact rejected fields and persistence outcomes instead of relying on internal shorthand.
- Tightened the custom Jazzer seed operator surface so `promote-seed` now validates lower_snake_case
  seed names before Gradle launch, deterministic `--json` seed-management failures return one
  structured error payload without Gradle failure boilerplate, `jazzer/bin/regression` rejects
  stray positional arguments at the wrapper edge, and the committed `.json` regression inputs are
  syntax-checked by the deterministic Jazzer test floor after removing one corrupted
  posting-workflow seed body.
- Replaced the remaining null-to-empty constructor and helper normalization paths across core,
  contract, CLI, executor, and SQLite-support models with direct field-named null rejection,
  added explicit nullable JSON/resource seam helpers at the Jackson boundaries, and tightened the
  staged-launcher and contract-resource tests so empty or malformed inputs fail through the
  intended diagnostics instead of generic null failures while JaCoCo verification now measures
  compiled Java classes rather than treating resource outputs as uncovered code.
- Extended the stale-classfile pruning rule from the nested Jazzer build to every product-module
  `compileJava` run, so removed nested helper classes cannot linger in cached main output
  directories and reappear later as false branch-coverage failures.
- Removed the stale claim that `RejectionNarrative` owns ledger-plan failure facts now that
  workflow execution records build their local `BookWorkflowFailure` and `BookWorkflowFact`
  payloads inside the workflow context and only project public rejection prose at the outer edge.
- Updated the SQLite runtime verifier, release-smoke assertions, and bundle acceptance workflow
  to read the current `environment.sqlite.runtime` capabilities shape instead of the older flat
  runtime fields, and tightened the SQLite lifecycle coverage tests so the end-to-end gate proves
  the real deferred, created-artifact, and no-active transaction branches without reflective
  state mutation.

## [0.32.0] - 2026-05-06

### Fixed

- Hardened protected-book verification so the public `protected-book-verification-failed` contract now covers the SQLite verification families surfaced as `SQLITE_NOTADB`, `SQLITE_IOERR_BADKEY`, and `SQLITE_IOERR_CODEC` instead of letting some wrong-key or damaged-book cases escape as generic runtime crashes.
- Enforced `memory_security=fill` on every opened SQLite handle, enabled secure SQLite3MC memory support in the managed native build and Docker compiler-flag renderer, and tightened the managed-runtime identity contract so publisher-owned bundle and source-checkout runtimes are authenticated against both an embedded trusted digest and the extracted sibling `.sha256` sidecar before native symbol lookup while custom environment-configured direct-Java paths remain explicitly operator-managed.
- Fixed source-checkout managed-runtime discovery for relocated Gradle build roots by carrying the active root-project build directory through the generated launcher, developer raw-JAR wrapper, and JAR manifest instead of guessing at `repo/build/managed-sqlite`.
- Rejected missing key-file paths in the key-file security seam, capped both key-file and `--book-passphrase-stdin` passphrase payloads at 4096 bytes, hardened Windows key-file parent directories to owner-only ACLs, converted unreadable stdin failures into deterministic `invalid-book-passphrase-source` errors, and rewrote the public quick-start/help/examples to keep encrypted books under `./books/` and secrets under `./secrets/`.
- Added a public `SECURITY.md`, enabled GitHub private vulnerability reporting for the repository, and updated the security-model reference to describe the real session-scoped passphrase lifetime, checksum-backed runtime identity, attested release assets, and coordinated disclosure path.
- Added GitHub artifact attestations for every published CLI archive and checksum in `release.yml`, and tightened `verify-github-release.sh` so release verification now downloads the published assets and proves their provenance with `gh attestation verify` before treating the release handoff as complete.
- Field-tested the bundle, source-checkout, raw-JAR, and container launcher surfaces so command help now rewrites piped stdin examples to the active launcher instead of leaving `| fingrind ...` fragments behind, container launcher guidance now keeps stdin open with `docker run -i`, and successful `--pdf-out` exports now report the normalized artifact path on the diagnostics stream without changing the primary stdout contract.
- Exposed the managed SQLite runtime trust class as machine-readable `runtimeTrustBasis`, hardened key-file acceptance to require owner-only parent directories as well as owner-only files, enforced the same 4096-byte UTF-8 passphrase cap on interactive prompts as on key files and stdin, and tightened the security-reference gate so it derives trust and secret-handling facts from the live machine contract instead of only checking for documentation keywords.
- Moved the canonical paging and ledger-plan limits into shared-kernel `InteractionLimits`, replaced bookkeeping-owned public rejection imports with local rejection types plus boundary translation, and tightened managed SQLite loading so FinGrind authenticates and loads one private verified runtime snapshot instead of hashing one path and mapping another later.
- Added `./scripts/verify-security-policy-surface.sh` to verify GitHub private vulnerability reporting as part of public release verification, and updated the security reference to point at that executable evidence owner.
- Stopped read-oriented SQLite opens from rewriting book-file permissions as a side effect, added stale `*.rekey-rollback-*.sqlite` warning detection for interrupted rekeys, and clarified the security/docs contract so passphrase buffer overwrite is described as best-effort under the Java heap model rather than as guaranteed erasure.
- Tightened the public release protocol so release promotion now waits on the aggregate `Gate` check via `./scripts/verify-release-pr-gate.sh` instead of inferring merge-readiness from an earlier green `Check` job while downstream Windows and Docker fan-out is still running.
- Fixed the shared release-smoke workflow so bundle and Docker acceptance now compare the PDF-exported diagnostics path by normalized artifact identity instead of raw `--pdf-out` argument text, removing a Windows-only false failure when the CLI reports the canonical normalized path form.

## [0.31.0] - 2026-05-05

### Fixed

- Pinned `container.yml` runners to `ubuntu-24.04` (both the `container` and `cleanup` jobs used the floating `ubuntu-latest` label — the most security-sensitive workflow was the least pinned).
- Raised `container` job `timeout-minutes` from 35 to 45 to provide a clear margin between multi-arch image build time and the post-push verification step; the former 35-minute ceiling was tight enough that slow runners could cancel verification after a successful push.
- Added OCI build provenance (`provenance: mode=max`) and SBOM (`sbom: true`) attestations to the `docker/build-push-action` step; both are stored as OCI attestations attached to the published GHCR image digest, enabling supply-chain verification via `docker buildx imagetools inspect`.
- Added `id-token: write` permission to the `container` job to allow the OIDC token flow required for keyless provenance attestation signing.
- Pinned `gradle-wrapper-validation.yml` runner to `ubuntu-24.04`; it was the only remaining workflow using the floating `ubuntu-latest` label.
- Hardcoded the release-blocking check list in `verify-release-candidate-tag.sh` to `Gate` (the single aggregate CI check) and removed the `FINGRIND_RELEASE_BLOCKING_CHECKS` env-var override; the previous default included `Contributor devcontainer` which is legitimately skipped on commits that do not touch devcontainer files, causing the script to false-fail on any such release commit.
- Updated the branch protection reference in `RELEASE_PROTOCOL.md` §Step 1 to reflect the current single required status check (`Gate`) instead of the former three-check list (`Check`, `Windows bundle smoke`, `Docker smoke`).
- Tightened `RELEASE_PROTOCOL.md` so release hygiene now also closes any ordinary open PR that was superseded by the shipped release branch, instead of only triaging Dependabot leftovers.
- Raised `verify-github-release.sh` default retry count from 1 to 3 and default inter-retry delay from 0 to 5 seconds so release asset availability checks are resilient to brief GitHub API propagation lag when run outside the container workflow's explicit override values.
- Removed `isPreserveFileTimestamps = false` from the `bundleCliZip` and `bundleCliTarGz` archive tasks; the setting zeroed every file's modification time to the MS-DOS epoch minimum (1980-02-01 for ZIP) or the Unix epoch (1970-01-02 for TAR), making all files in every release package appear frozen in 1970 or 1980 in file managers and `ls -l` output. Retaining `isReproducibleFileOrder = true` keeps entries in a stable alphabetical order for auditing without clobbering timestamps.
- Pinned release workflow runners to `ubuntu-24.04`, `ubuntu-24.04-arm`, `macos-15`, and `windows-2022` instead of the floating `ubuntu-latest`, `macos-latest`, and `windows-latest` labels so runner image updates cannot silently change the native build environment across releases.
- Path-gated the `devcontainer` CI job so it fires only when devcontainer-relevant files actually change (`.devcontainer/`, `scripts/validate-devcontainer.sh`, `scripts/devcontainer-prepare-user-home.sh`, `scripts/repo-verification-lock-support.sh`, `scripts/python-runtime-support.sh`); non-devcontainer PRs skip the full Docker build-and-validate cycle, reducing typical PR wall-clock time by 15-20 minutes.
- Added a `devcontainer-changes` detection job that computes a git diff of the PR's changed files against the devcontainer trigger paths before the gate is evaluated. The `devcontainer` job no longer depends on `check` — the contributor environment is orthogonal to code correctness and should be proven whenever its files change regardless of whether the application gate passes.
- Added a `gate` aggregate required-status job using `if: always()` with explicit `${{ toJSON(needs.*.result) }}` failure detection so a correctly skipped `devcontainer` gate does not prevent `Gate` from being reported or block merge — only a failed or cancelled job prevents success. Configure branch protection to require `Gate` as the single required check.
- Added `workflow_dispatch:` to the CI trigger so maintainers can manually rerun the aggregate `Gate` against a branch when GitHub fails to attach the `pull_request` workflow on initial PR open.
- Pinned CI runners to `ubuntu-24.04` and `windows-2022` instead of the floating `ubuntu-latest` and `windows-latest` labels so runner image updates cannot silently change the build environment between runs.
- Added Windows Defender exclusions for the workspace and Gradle user home in `windows-bundle-smoke` before any Gradle operations begin, eliminating antivirus scan overhead that otherwise scans every `.class`, native library, and JAR file written during compilation.
- Promoted top-level `permissions: contents: read` to the workflow level and removed the redundant per-job declarations.
- Raised `check` job `timeout-minutes` from 15 to 40 to accommodate the Docker build inside the release-surface scripts verification step on days when apt mirrors respond slowly; the step consistently completes in under 5 minutes on fast days but has been observed to take over 23 minutes when mirrors are degraded.
- Unified the release and branch-protection check contract on the single `Gate` status across the release verifiers, bootstrap protocol, release protocol, and shell regressions so the path-gated contributor-devcontainer job can skip without false-failing post-merge or tag verification.
- Moved script-managed `GRADLE_USER_HOME` defaults for `./check.sh` and `./scripts/docker-smoke.sh` out of the checkout and into the same repo-keyed user-cache root used by the wrapper support helpers, restoring the documented mounted-checkout verification path.
- Fixed `cleanBundleOutputs` so `:cli:bundleCliArchive` removes obsolete `fingrind-*` bundle artifacts from the active distribution directory and legacy in-checkout leftovers before writing the current host bundle artifact.
- Replaced hardcoded `cli/build/...` local launcher commands with `scripts/source-checkout-cli.*` and `scripts/direct-java-cli.*` wrappers that resolve the active Gradle build directory, so CLI help, docs, and developer commands remain truthful when wrapper-owned build output is relocated out of the checkout.
- Restored the dedicated nested Jazzer build output root so the shared Java conventions no longer override it on relocated-checkout runs and stale-class pruning targets the real Jazzer compile output.
- Replaced the misleading former book-authentication-failed public error with `protected-book-verification-failed`, which truthfully covers wrong secrets, damaged or truncated protected books, and unsupported protected SQLite variants without pretending every verification failure is a passphrase mistake.
- Fixed atomic SQLite ledger-plan rollback for newly created books so assertion failures and other rejected plans remove the transient protected-book file and any empty parent directories they created instead of leaving a blank SQLite shell behind.
- Removed fragile qualified JPMS exports from the `executor` module, taught the Java source-policy gate to reject future `exports ... to` seams in repository modules, and hardened the Jazzer stale-class regression so nested compile runs fail on module-target warnings instead of printing them as benign noise.
- Split SQLite runtime verification by real provenance path so source-checkout launcher verification and environment-configured Gradle JavaExec verification are checked independently instead of one script claiming both.
- Replaced the ad-hoc Docker assembly inputs with one staged `:cli:stageDockerBuildContext` directory plus `docker-build-context-manifest.json`, updated Docker smoke and CI/container workflows to consume that single staged context, and kept Docker's SQLite compiler flags derived from the canonical managed-SQLite contract through `scripts/render-managed-sqlite-compiler-flags.py`.
- Added `DEVELOPER_SECURITY.md` plus a contract gate for the security model, consolidating the protected-book threat boundary, secret transport rules, runtime provenance model, and verification-failure semantics into one canonical theory surface.
- Upgraded `tools.jackson.core:jackson-databind` from `3.1.2` to `3.1.3`.
- Aligned every AFAD-managed documentation page with the current project version from `gradle.properties` and added a contract-lint gate so documentation frontmatter cannot drift onto a future or mixed release version.
- Replaced release-numbered extracted-bundle launcher paths in the public CLI guides with archive-derived launcher examples, and moved shared bundle-archive verification onto one Python owner used by both Bash and PowerShell bundle smoke.
- Taught `:cli:bundleCliArchive` to report the exact archive path and checksum path it emitted under the active Gradle build directory, and added a regression check so relocated build roots do not force operators or agents to hunt for the produced bundle artifact manually.
- Split the internal bookkeeping and workflow models away from the public contract DTOs, moved shared `CurrencyBalance` and `EffectiveDateRange` ownership into the `core` shared kernel, made `accounting entity` the canonical book-owner term across help/docs/contract facts, added a dedicated domain-model reference and gate, and moved account declaration/reactivation rules into the bookkeeping model instead of adapter-local reimplementations.

[Unreleased]: https://github.com/resoltico/FinGrind/compare/v0.61.0...HEAD
[0.61.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.61.0
[0.60.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.60.0
[0.59.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.59.0
[0.58.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.58.0
[0.57.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.57.0
[0.56.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.56.0
[0.55.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.55.0
[0.54.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.54.0
[0.53.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.53.0
[0.52.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.52.0
[0.51.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.51.0
[0.50.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.50.0
[0.49.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.49.0
[0.48.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.48.0
[0.47.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.47.0
[0.46.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.46.0
[0.45.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.45.0
[0.44.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.44.0
[0.43.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.43.0
[0.42.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.42.0
[0.41.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.41.0
[0.40.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.40.0
[0.39.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.39.0
[0.38.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.38.0
[0.37.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.37.0
[0.36.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.36.0
[0.35.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.35.0
[0.34.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.34.0
[0.33.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.33.0
[0.32.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.32.0
[0.31.0]: https://github.com/resoltico/FinGrind/releases/tag/v0.31.0
