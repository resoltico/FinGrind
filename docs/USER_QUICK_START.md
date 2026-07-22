---
afad: "5.0.1"
version: "0.61.0"
domain: USER_QUICK_START
updated: "2026-07-21"
route:
  keywords: [fingrind, quick start, first run, open book, seed template, post entry, trial balance]
  questions: ["how do I start using fingrind", "what is the fastest way to try fingrind", "how do I open a book and post the first entry in fingrind"]
---

# Quick Start

**Purpose**: Get one protected FinGrind book running, record one sale, and read one report back.
**Prerequisites**: Download one public FinGrind bundle that matches your host and unpack it. The
published Linux bundles require glibc `2.34` or newer; use the compatibility matrix below as the
authoritative target check. The public bundle already includes what it needs to run. The examples
below use the extracted bundle launcher directly. If you prefer the mounted-workspace container
surface instead, use [USER_CONTAINER.md](./USER_CONTAINER.md).

In the examples below, `fingrind` means a session-local wrapper around the launcher inside the
extracted bundle. The commands use relative paths in the current working directory. The public
bundle does not include the repository's `docs/examples/` fixtures, but it does ship one offline
`./quick-start-request.json` sample document for the first posting flow.

## 1. Pick And Verify The Download

Choose the archive that matches your host:

<!-- BEGIN GENERATED USER_QUICK_START BUNDLE MATRIX -->
| Target | Archive name pattern | Launcher path | Compatibility | Status |
|:-------|:---------------------|:--------------|:--------------|:-------|
| `macos-aarch64` | `fingrind-<version>-macos-aarch64.tar.gz` | `bin/fingrind` | `macOS aarch64` | published |
| `macos-x86_64` | `fingrind-<version>-macos-x86_64.tar.gz` | `bin/fingrind` | `macOS x86_64` | published |
| `linux-x86_64` | `fingrind-<version>-linux-x86_64.tar.gz` | `bin/fingrind` | `glibc 2.34+ Linux x86_64` | published |
| `linux-aarch64` | `fingrind-<version>-linux-aarch64.tar.gz` | `bin/fingrind` | `glibc 2.34+ Linux aarch64` | published |
| `windows-x86_64` | `fingrind-<version>-windows-x86_64.zip` | `bin/fingrind.ps1` | `Windows x86_64` | published |
| `windows-aarch64` | `fingrind-<version>-windows-aarch64.zip` | `bin/fingrind.ps1` | `Windows aarch64` | not published |
<!-- END GENERATED USER_QUICK_START BUNDLE MATRIX -->

Every published archive also has one sibling `.sha256` file plus one GitHub artifact
attestation.

Publisher-backed provenance:

```bash
gh attestation verify --repo resoltico/FinGrind <downloaded-archive>
```

Convenience checksum verification:

```bash
shasum -a 256 -c <downloaded-archive>.sha256
sha256sum -c <downloaded-archive>.sha256
```

For the fuller package matrix, published container surface, and launcher-path reference, continue
with [USER_INSTALL.md](./USER_INSTALL.md).

## 2. Check That The Download Runs

Unix shells:

```bash
tar -xzf <downloaded-archive>.tar.gz
./<extracted-directory>/bin/fingrind version
fingrind() { "./<extracted-directory>/bin/fingrind" "$@"; }
```

Windows PowerShell:

```powershell
Expand-Archive <downloaded-archive>.zip -DestinationPath .
& .\<extracted-directory>\bin\fingrind.ps1 version
function fingrind { & ".\<extracted-directory>\bin\fingrind.ps1" @Args }
```

Use the actual archive and extracted directory names from the release you downloaded.

## 3. Create A Key File

FinGrind protects each book. Start by creating one key file that will hold the secret for the
book:

```bash
mkdir -p -m 700 ./secrets ./books
fingrind generate-book-key-file --new-book-key-file ./secrets/acme.book-key
```

That command creates the file for you and refuses to overwrite an existing one. Its parent must
already exist and remain owner-only: FinGrind deliberately does not create or weaken a secret
directory on the caller's behalf. This guide creates both directories explicitly. On Windows
PowerShell, create the two directories and replace every inherited or explicit access rule with
one current-owner full-control rule before generating a key:

```powershell
@('.\secrets', '.\books') | ForEach-Object {
  New-Item -ItemType Directory -Force $_ | Out-Null
  $owner = [System.Security.Principal.WindowsIdentity]::GetCurrent().User
  $acl = Get-Acl $_
  $acl.SetAccessRuleProtection($true, $false)
  $acl.Access | ForEach-Object { [void]$acl.RemoveAccessRuleSpecific($_) }
  $acl.SetOwner($owner)
  $acl.AddAccessRule([System.Security.AccessControl.FileSystemAccessRule]::new($owner, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow'))
  Set-Acl $_ $acl
}
```

This guide keeps the key under `./secrets/` and the book under `./books/` on purpose so routine
book copies do not automatically copy the unlocking secret too. Keep the `./secrets/` directory
owner-only as well as the key file itself. Keep `./books/` owner-only too.

## 4. Prepare One Founder Credential

Create a separate owner-only, nonempty UTF-8 passphrase file at
`./secrets/acme-founder.passphrase`. It protects the private founder credential, not the book key.
At book creation FinGrind creates `./secrets/acme-founder.fgatk` if it is absent and binds it to
the founder UUID. Keep both files outside the book directory.

## 5. Open The Book

Create one new book file and protect it with that key:

```bash
fingrind open-book --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key --entity-name "Acme Studio" --book-template-id OWNER_MANAGED_SERVICE --accounting-basis CASH --functional-currency EUR --fiscal-year-start 01-01 --book-start-effective-date 2026-01-01 --attestation-custodian file-pkcs8 --attestation-founder-principal-id 123e4567-e89b-12d3-a456-426614174000 --attestation-founder-key-file ./secrets/acme-founder.fgatk --attestation-founder-passphrase-file ./secrets/acme-founder.passphrase
```

If you accidentally rerun `open-book` against the same initialized file, the command is rejected
deterministically instead of mutating the existing book.

## 6. Review The Seed Template

This quick start chooses `OWNER_MANAGED_SERVICE` with `--accounting-basis CASH`. Use
`--accounting-basis ACCRUAL` when you want the accrual owner-managed service chart. The
cash-basis service chart includes these postable accounts. To open a goods-trading book instead,
use `OWNER_MANAGED_TRADING` and add `--inventory-costing WEIGHTED_AVERAGE` to `open-book`:

- `cash`
- `owner-capital`
- `owner-draws`
- `result-holding`
- `service-revenue`
- `operating-expense`

Inspect those seeded accounts directly:

```bash
fingrind list-accounts --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key --limit 10
```

## 6. Post Your First Entry

Start from the bundled quick-start example:

```bash
cp ./quick-start-request.json ./request.json
```

That bundled file is a concrete sample document. Replace the sample evidence, provenance, and
idempotency values before real-world use. Reusing one committed `idempotencyKey` against the same
book is rejected. The bundled quick-start sample follows the minimal sale request path, while the
raw direct-journal scaffold remains available through `print-request-template post-entry` and
still has to move at least one declared cash-and-cash-equivalent asset account.

`./quick-start-request.json` already contains one sale entry that uses the seeded
accounts:

```json
{
  "entryKind": "SALE_SETTLED",
  "effectiveDate": "2026-04-07",
  "cashAccountCode": "cash",
  "revenueAccountCode": "service-revenue",
  "amount": {
    "currencyCode": "EUR",
    "minorUnits": "1000"
  },
  "evidence": {
    "sourceDocuments": [
      {
        "sourceDocumentId": "quick-start-cash-receipt-1",
        "sourceDocumentType": "cash-receipt",
        "documentDate": "2026-04-07"
      }
    ],
    "approvals": []
  },
  "provenance": {
    "commandId": "018f0000-0000-7000-8000-000000000007",
    "idempotencyKey": "quick-start-idem-1",
    "causationId": "quick-start-sale-cause-1"
  }
}
```

Then check the request:

```bash
fingrind preflight-entry --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key --request-file ./request.json
```

Then commit it:

```bash
fingrind record-sale-settled --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key --request-file ./request.json --attestation-custodian file-pkcs8 --attestation-principal-id 123e4567-e89b-12d3-a456-426614174000 --attestation-key-file ./secrets/acme-founder.fgatk --attestation-passphrase-file ./secrets/acme-founder.passphrase
```

## 7. Read The Result Back

Ask for a quick reporting view:

```bash
fingrind trial-balance --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key --output text
```

Or check one account directly:

```bash
fingrind account-balance --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key --account-code cash --output text
```

## 8. Where To Go Next

- [USER_INSTALL.md](./USER_INSTALL.md) for exact public package names, launcher paths, checksums, and attestation commands
- [USER_CONTAINER.md](./USER_CONTAINER.md) for the published container image workflow
- [USER_CLI.md](./USER_CLI.md) for the full command surface and exit behavior
- [USER_REQUESTS.md](./USER_REQUESTS.md) for request shapes
- [USER_RESPONSES.md](./USER_RESPONSES.md) for response envelopes, report payloads, and deterministic error output
- [USER_EXAMPLES.md](./USER_EXAMPLES.md) for longer flows, reversals, plans, and report examples
- [README.md](../README.md) for the storefront overview
