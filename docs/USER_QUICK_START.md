---
afad: "3.5"
version: "0.24.0"
domain: USER_QUICK_START
updated: "2026-04-23"
route:
  keywords: [fingrind, quick start, first run, open book, declare account, post entry, trial balance]
  questions: ["how do I start using fingrind", "what is the fastest way to try fingrind", "how do I open a book and post the first entry in fingrind"]
---

# Quick Start

**Purpose**: Get one protected FinGrind book running, post one entry, and read one report back.
**Prerequisites**: Download one public FinGrind release bundle and unpack it. The public download
already includes what it needs to run.

In the examples below, `fingrind` means the launcher inside the extracted download. The commands
use relative paths in the current working directory so the same file layout works from a public
bundle on macOS, Linux, and Windows PowerShell. The public bundle does not include the repository's
`docs/examples/` fixtures, so this guide creates the needed JSON files directly.

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

## 2. Create A Key File

FinGrind protects each book. Start by creating one key file that will hold the secret for the
book:

```bash
fingrind generate-book-key-file --book-key-file ./acme.book-key
```

That command creates the file for you and refuses to overwrite an existing one.

## 3. Open The Book

Create one new book file and protect it with that key:

```bash
fingrind open-book --book-file ./acme.sqlite --book-key-file ./acme.book-key
```

## 4. Declare The Accounts You Need

Create `./declare-account-cash.json` with:

```json
{
  "accountCode": "1000",
  "accountName": "Cash",
  "normalBalance": "DEBIT"
}
```

Create `./declare-account-revenue.json` with:

```json
{
  "accountCode": "2000",
  "accountName": "Revenue",
  "normalBalance": "CREDIT"
}
```

Then declare the accounts that your first entry will use:

```bash
fingrind declare-account --book-file ./acme.sqlite --book-key-file ./acme.book-key --request-file ./declare-account-cash.json
fingrind declare-account --book-file ./acme.sqlite --book-key-file ./acme.book-key --request-file ./declare-account-revenue.json
```

## 5. Post Your First Entry

Start from the canonical posting template:

```bash
fingrind print-request-template > ./request.json
```

Replace the contents of `./request.json` with one balanced entry, for example:

```json
{
  "effectiveDate": "2026-04-08",
  "lines": [
    {
      "accountCode": "1000",
      "side": "DEBIT",
      "currencyCode": "EUR",
      "amount": "10.00"
    },
    {
      "accountCode": "2000",
      "side": "CREDIT",
      "currencyCode": "EUR",
      "amount": "10.00"
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
fingrind preflight-entry --book-file ./acme.sqlite --book-key-file ./acme.book-key --request-file ./request.json
```

Then commit it:

```bash
fingrind post-entry --book-file ./acme.sqlite --book-key-file ./acme.book-key --request-file ./request.json
```

## 6. Read The Result Back

Ask for a quick reporting view:

```bash
fingrind trial-balance --book-file ./acme.sqlite --book-key-file ./acme.book-key --output human
```

Or check one account directly:

```bash
fingrind account-balance --book-file ./acme.sqlite --book-key-file ./acme.book-key --account-code 1000 --output human
```

## 7. Where To Go Next

- [USER_CLI.md](./USER_CLI.md) for the full command surface and exit behavior
- [USER_REQUESTS.md](./USER_REQUESTS.md) for request and response shapes
- [USER_EXAMPLES.md](./USER_EXAMPLES.md) for longer flows, reversals, plans, and report examples
- [README.md](../README.md) for the storefront overview
