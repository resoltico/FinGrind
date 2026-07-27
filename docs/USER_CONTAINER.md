---
afad: "5.0.1"
version: "0.61.0"
domain: USER_CONTAINER
updated: "2026-07-26"
route:
  keywords: [fingrind, container, docker, ghcr, mounted workspace, book key file]
  questions: ["how do i run fingrind in docker", "what is the fingrind container image", "how do i mount a book into the fingrind container"]
---

# Container Guide

**Purpose**: Run the published FinGrind container image against files in one mounted host working
directory.
**Prerequisites**: Docker or another compatible container runtime that can run Linux images.

FinGrind publishes one public image at `ghcr.io/resoltico/fingrind`.
Supported published platforms are `linux/amd64` and `linux/arm64`.
For exact tag and bundle/package naming guidance, start with [USER_INSTALL.md](./USER_INSTALL.md).

## Define One Session-Local Wrapper

On macOS or Linux:

```bash
fingrind() { docker run --rm -i -v "$PWD":/workspace -w /workspace ghcr.io/resoltico/fingrind:<tag> "$@"; }
```

On Windows PowerShell:

```powershell
function fingrind { docker run --rm -i -v "${PWD}:/workspace" -w /workspace ghcr.io/resoltico/fingrind:<tag> @args }
```

That wrapper keeps the book file, key file, request JSON, and exported PDFs in the mounted host
directory while the container itself stays disposable.

## Verify The Image Surface

```bash
fingrind version --output json
fingrind help
```

The image already includes the private Java runtime and the managed SQLite runtime. Do not mount a
host Java install. Any inherited `FINGRIND_SQLITE_LIBRARY` override is ignored.

## First Mounted Workflow

Create the key and book inside the mounted working directory:

Before opening the book, prepare a separate nonempty owner-only UTF-8 founder passphrase file at
`./secrets/acme-founder.passphrase`. FinGrind creates the absent founder credential at
`./secrets/acme-founder.fgatk` exactly once; do not reuse the book key or its passphrase for that
credential.

```bash
fingrind generate-book-key-file --new-book-key-file ./secrets/acme.book-key
fingrind open-book --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key \
  --entity-name "Acme Studio" --book-template-id OWNER_MANAGED_SERVICE \
  --accounting-basis CASH \
  --functional-currency EUR --fiscal-year-start 01-01 --book-start-effective-date 2026-01-01 \
  --attestation-custodian file-pkcs8 --attestation-founder-principal-id 123e4567-e89b-12d3-a456-426614174000 \
  --attestation-founder-key-file ./secrets/acme-founder.fgatk \
  --attestation-founder-passphrase-file ./secrets/acme-founder.passphrase
```

Create the request scaffold locally:

```bash
fingrind print-request-template > ./request.json
```

The scaffold emits one minimal sale request with placeholder evidence and provenance. The raw
direct-journal boundary remains available through `print-request-template post-entry`, but that
raw surface still has to move at least one declared cash-and-cash-equivalent asset account and the
default mounted workflow teaches the primary business-event path first. Use
`--accounting-basis ACCRUAL` when you want the accrual owner-managed service chart.
Replace the placeholder values in `./request.json`, then validate and commit:

```bash
fingrind preflight-entry --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key --request-file ./request.json
fingrind record-sale-settled --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key --request-file ./request.json --attestation-custodian file-pkcs8 --attestation-principal-id 123e4567-e89b-12d3-a456-426614174000 --attestation-key-file ./secrets/acme-founder.fgatk --attestation-passphrase-file ./secrets/acme-founder.passphrase
```

Read a report and export a PDF back into the mounted host directory:

```bash
mkdir -p ./private-reports
chmod 700 ./private-reports
fingrind trial-balance --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key --output text --pdf-out ./private-reports/trial-balance.pdf
```

`./private-reports/trial-balance.pdf` is written into the mounted host working directory, not into
a hidden container filesystem. The selected PDF parent must already exist as a real owner-only
directory; the POSIX commands above prepare one, while a Windows host must prepare the equivalent
owner-only ACL. FinGrind neither creates nor weakens that caller-owned output parent. When
`--output text` is paired with `--pdf-out`, stdout prints one artifact confirmation block instead
of the full text report body and reports the canonical physical final path.
If the mounted work directory itself already satisfies that same owner-only parent requirement,
`--pdf-out ./trial-balance.pdf` is equivalent; otherwise keep the dedicated private report
directory shown above.

## Secret Handling

- Keep the key file under a separate tree such as `./secrets/` and keep the book under `./books/`
- Mount only the working directory you actually want the container to touch
- Prefer `--book-key-file` over stdin or prompt flows when you want one repeatable non-interactive
  container session
- Delete temporary request files after use if they contain sensitive evidence or provenance data
