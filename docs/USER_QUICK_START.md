---
afad: "4.0"
version: "0.52.0"
domain: USER_QUICK_START
updated: "2026-06-05"
route:
  keywords: [fingrind, quick start, first run, open book, starter chart, post entry, trial balance]
  questions: ["how do I start using fingrind", "what is the fastest way to try fingrind", "how do I open a book and post the first entry in fingrind"]
---

# Quick Start

**Purpose**: Get one protected FinGrind book running, post one entry, and read one report back.
**Prerequisites**: Download one public FinGrind Linux bundle and unpack it on a glibc Linux host.
The public download already includes what it needs to run. On macOS or Windows, use the published
container workflow in [USER_CONTAINER.md](./USER_CONTAINER.md) instead of the bundle path.

In the examples below, `fingrind` means a session-local shell function backed by the launcher
inside the extracted Linux bundle. The commands use relative paths in the current working
directory. The public bundle does not include the repository's `docs/examples/` fixtures, but it
does ship one offline `./quick-start-request.json` sample document for the first posting flow.

## 1. Pick And Verify The Download

Choose the archive that matches your host:

<!-- BEGIN GENERATED USER_QUICK_START BUNDLE MATRIX -->
| Target | Archive name pattern | Launcher path | Compatibility | Status |
|:-------|:---------------------|:--------------|:--------------|:-------|
| `linux-x86_64` | `fingrind-<version>-linux-x86_64.tar.gz` | `bin/fingrind` | `glibc Linux x86_64` | published |
| `linux-aarch64` | `fingrind-<version>-linux-aarch64.tar.gz` | `bin/fingrind` | `glibc Linux aarch64` | published |
| `macos-aarch64` | `fingrind-<version>-macos-aarch64.tar.gz` | `bin/fingrind` | `macOS aarch64` | not published |
| `macos-x86_64` | `fingrind-<version>-macos-x86_64.tar.gz` | `bin/fingrind` | `macOS x86_64` | not published |
| `windows-x86_64` | `fingrind-<version>-windows-x86_64.zip` | `bin/fingrind.ps1` | `Windows x86_64` | not published |
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

```bash
tar -xzf <downloaded-archive>.tar.gz
./<extracted-directory>/bin/fingrind version
```

Use the actual archive and extracted directory names from the release you downloaded.

For the remaining copy-paste commands below, define `fingrind` once in your current shell session.

```bash
fingrind() { "./<extracted-directory>/bin/fingrind" "$@"; }
```

## 3. Create A Key File

FinGrind protects each book. Start by creating one key file that will hold the secret for the
book:

```bash
fingrind generate-book-key-file --book-key-file ./secrets/acme.book-key
```

That command creates the file for you and refuses to overwrite an existing one.
This guide keeps the key under `./secrets/` and the book under `./books/` on purpose so routine
book copies do not automatically copy the unlocking secret too. Keep the `./secrets/` directory
owner-only as well as the key file itself. Keep `./books/` owner-only too. If `./secrets/` or
`./books/` does not exist yet, FinGrind creates it with owner-only permissions. If either
directory already exists, keep it owner-only before you reuse that path.

## 4. Open The Book

Create one new book file and protect it with that key:

```bash
fingrind open-book --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key --entity-name "Acme Studio" --functional-currency EUR --fiscal-year-start 01-01
```

If you accidentally rerun `open-book` against the same initialized file, the command is rejected
deterministically instead of mutating the existing book.

## 5. Review The Starter Chart

`open-book` seeds the built-in starter chart for the current owner-managed service template. The
first run includes these postable accounts:

- `cash`
- `owner-capital`
- `owner-draws`
- `result-holding`
- `service-revenue`
- `operating-expense`

Inspect that chart directly:

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
book is rejected.

`./quick-start-request.json` already contains one balanced entry that uses the seeded starter
accounts:

```json
{
  "entryKind": "CASH_REVENUE",
  "effectiveDate": "2026-04-08",
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
        "documentDate": "2026-04-08",
        "capturedAt": "2026-04-08T10:15:30Z",
        "storageLocator": "vault://quick-start/cash-receipt-1",
        "contentSha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
      }
    ],
    "approvals": []
  },
  "provenance": {
    "actorId": "quick-start-operator",
    "actorType": "PERSON",
    "commandId": "quick-start-posting",
    "idempotencyKey": "quick-start-idem-1",
    "causationId": "quick-start-cause-1"
  }
}
```

Then check the request:

```bash
fingrind preflight-entry --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key --request-file ./request.json
```

Then commit it:

```bash
fingrind post-entry --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key --request-file ./request.json
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
- [USER_REQUESTS.md](./USER_REQUESTS.md) for request and response shapes
- [USER_EXAMPLES.md](./USER_EXAMPLES.md) for longer flows, reversals, plans, and report examples
- [README.md](../README.md) for the storefront overview
