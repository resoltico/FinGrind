---
afad: "4.0"
version: "0.37.0"
domain: USER_QUICK_START
updated: "2026-05-14"
route:
  keywords: [fingrind, quick start, first run, open book, declare account, post entry, trial balance]
  questions: ["how do I start using fingrind", "what is the fastest way to try fingrind", "how do I open a book and post the first entry in fingrind"]
---

# Quick Start

**Purpose**: Get one protected FinGrind book running, post one entry, and read one report back.
**Prerequisites**: Download one public FinGrind release bundle and unpack it. The public download
already includes what it needs to run.

In the examples below, `fingrind` means a session-local shell function backed by the launcher
inside the extracted download. The commands use relative paths in the current working directory so
the same file layout works from a public bundle on macOS, Linux, and Windows PowerShell. The
public bundle does not include the repository's `docs/examples/` fixtures, so this guide creates
the needed JSON files directly.

## 1. Check That The Download Runs

On macOS or Linux:

```bash
tar -xzf <downloaded-archive>.tar.gz
./<extracted-directory>/bin/fingrind version
```

On Windows PowerShell:

```powershell
Expand-Archive <downloaded-archive>.zip -DestinationPath .
.\<extracted-directory>\bin\fingrind.ps1 version
```

Use the actual archive and extracted directory names from the release you downloaded.

For the remaining copy-paste commands below, define `fingrind` once in your current shell session.

```bash
fingrind() { "./<extracted-directory>/bin/fingrind" "$@"; }
```

```powershell
function fingrind { & .\<extracted-directory>\bin\fingrind.ps1 @args }
```

## 2. Create A Key File

FinGrind protects each book. Start by creating one key file that will hold the secret for the
book:

```bash
fingrind generate-book-key-file --book-key-file ./secrets/acme.book-key
```

That command creates the file for you and refuses to overwrite an existing one.
This guide keeps the key under `./secrets/` and the book under `./books/` on purpose so routine
book copies do not automatically copy the unlocking secret too. Keep the `./secrets/` directory
owner-only as well as the key file itself.

## 3. Open The Book

Create one new book file and protect it with that key:

```bash
fingrind open-book --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key --entity-name "Acme Studio" --functional-currency EUR --fiscal-year-start 01-01
```

## 4. Declare The Accounts You Need

Create `./declare-account-cash.json` with:

```json
{
  "accountCode": "1000",
  "accountName": "Cash",
  "accountType": "ASSET",
  "accountRole": "ORDINARY"
}
```

Create `./declare-account-revenue.json` with:

```json
{
  "accountCode": "2000",
  "accountName": "Revenue",
  "accountType": "REVENUE",
  "accountRole": "ORDINARY"
}
```

Then declare the accounts that your first entry will use:

```bash
fingrind declare-account --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key --request-file ./declare-account-cash.json
fingrind declare-account --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key --request-file ./declare-account-revenue.json
```

## 5. Post Your First Entry

Start from the canonical posting template:

```bash
fingrind print-request-template > ./request.json
```

That scaffold intentionally uses `actorType: "AGENT"` together with
`replace-before-commit-effective-date` plus `replace-before-commit-*` provenance placeholders.
Replace every placeholder before you send the request. Reusing one committed `idempotencyKey`
against the same book is rejected.

Replace the contents of `./request.json` with one balanced entry, for example:

```json
{
  "postingKind": "STANDARD",
  "effectiveDate": "2026-04-08",
  "lines": [
    {
      "accountCode": "1000",
      "side": "DEBIT",
      "amount": {
        "currencyCode": "EUR",
        "minorUnits": "1000"
      }
    },
    {
      "accountCode": "2000",
      "side": "CREDIT",
      "amount": {
        "currencyCode": "EUR",
        "minorUnits": "1000"
      }
    }
  ],
  "provenance": {
    "actorId": "quick-start",
    "actorType": "AGENT",
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

## 6. Read The Result Back

Ask for a quick reporting view:

```bash
fingrind trial-balance --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key --output human
```

Or check one account directly:

```bash
fingrind account-balance --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key --account-code 1000 --output human
```

## 7. Where To Go Next

- [USER_CLI.md](./USER_CLI.md) for the full command surface and exit behavior
- [USER_REQUESTS.md](./USER_REQUESTS.md) for request and response shapes
- [USER_EXAMPLES.md](./USER_EXAMPLES.md) for longer flows, reversals, plans, and report examples
- [README.md](../README.md) for the storefront overview
