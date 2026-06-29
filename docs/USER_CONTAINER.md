---
afad: "4.0"
version: "0.58.0"
domain: USER_CONTAINER
updated: "2026-06-29"
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

```bash
fingrind generate-book-key-file --book-key-file ./secrets/acme.book-key
fingrind open-book --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key \
  --entity-name "Acme Studio" --functional-currency EUR --fiscal-year-start 01-01
```

Create the request scaffold locally:

```bash
fingrind print-request-template > ./request.json
```

The scaffold emits one minimal sale request with placeholder evidence and provenance. The raw
direct-journal boundary remains available through `print-request-template post-entry`, but that
raw surface still has to move at least one declared cash-and-cash-equivalent asset account and the
default mounted workflow teaches the primary business-event path first.
Replace the placeholder values in `./request.json`, then validate and commit:

```bash
fingrind preflight-entry --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key --request-file ./request.json
fingrind record-sale --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key --request-file ./request.json
```

Read a report and export a PDF back into the mounted host directory:

```bash
fingrind trial-balance --book-file ./books/acme.sqlite --book-key-file ./secrets/acme.book-key --output text --pdf-out ./trial-balance.pdf
```

`./trial-balance.pdf` is written into the mounted host working directory, not into a hidden
container filesystem. When `--output text` is paired with `--pdf-out`, stdout prints one artifact
confirmation block instead of the full text report body.

## Secret Handling

- Keep the key file under a separate tree such as `./secrets/` and keep the book under `./books/`
- Mount only the working directory you actually want the container to touch
- Prefer `--book-key-file` over stdin or prompt flows when you want one repeatable non-interactive
  container session
- Delete temporary request files after use if they contain sensitive evidence or provenance data
