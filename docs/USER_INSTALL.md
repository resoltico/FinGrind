---
afad: "4.0"
version: "0.53.0"
domain: USER_INSTALL
updated: "2026-06-13"
route:
  keywords: [fingrind, install, download, bundle, checksum, attestation, container, ghcr]
  questions: ["which fin grind download do i need", "how do i verify a fingrind download", "what container image does fingrind publish"]
---

# Install And Package Guide

**Purpose**: Choose the correct public FinGrind package, verify it, and know the exact launcher
surface each package exposes.
**Prerequisites**: None.

FinGrind publishes two public runtime surfaces:
- self-contained bundle archives for the supported Linux targets below
- one public container image for `linux/amd64` and `linux/arm64`

## Public Bundle Matrix

<!-- BEGIN GENERATED USER_INSTALL PACKAGE MATRIX -->
| Target | Archive name pattern | Launcher path | Compatibility | Status |
|:-------|:---------------------|:--------------|:--------------|:-------|
| `linux-x86_64` | `fingrind-<version>-linux-x86_64.tar.gz` | `bin/fingrind` | `glibc Linux x86_64` | published |
| `linux-aarch64` | `fingrind-<version>-linux-aarch64.tar.gz` | `bin/fingrind` | `glibc Linux aarch64` | published |
| `macos-aarch64` | `fingrind-<version>-macos-aarch64.tar.gz` | `bin/fingrind` | `macOS aarch64` | not published |
| `macos-x86_64` | `fingrind-<version>-macos-x86_64.tar.gz` | `bin/fingrind` | `macOS x86_64` | not published |
| `windows-x86_64` | `fingrind-<version>-windows-x86_64.zip` | `bin/fingrind.ps1` | `Windows x86_64` | not published |
| `windows-aarch64` | `fingrind-<version>-windows-aarch64.zip` | `bin/fingrind.ps1` | `Windows aarch64` | not published |
<!-- END GENERATED USER_INSTALL PACKAGE MATRIX -->

Public bundle publication is intentionally Linux-only. The macOS and Windows target identifiers
remain declared in the contract as `not published` so automation can distinguish unsupported
targets from unknown targets. On macOS or Windows, use the published container image or a source
checkout.

## Verify A Downloaded Bundle

Each published bundle archive has:
- one sibling `.sha256` file on the GitHub Release
- one GitHub artifact attestation for the archive itself

Verify the attestation first when you need publisher-backed provenance:

```bash
gh attestation verify --repo resoltico/FinGrind <downloaded-archive>
```

Verify the convenience checksum next.

Portable POSIX route:

```bash
shasum -a 256 -c <downloaded-archive>.sha256
```

GNU/Linux route:

```bash
sha256sum -c <downloaded-archive>.sha256
```

## Published Container Surface

FinGrind also publishes one public container image:

<!-- BEGIN GENERATED USER_INSTALL CONTAINER SURFACE -->
- image reference: `ghcr.io/resoltico/fingrind`
- published tags: one exact release tag such as `0.53.0` plus `latest`, where `latest` always points at the newest stable public release
- published platforms: `linux/amd64`, `linux/arm64`
- mounted launcher prefix: `docker run --rm -i -v <host-workdir>:/workspace -w /workspace ghcr.io/resoltico/fingrind:<tag>`
<!-- END GENERATED USER_INSTALL CONTAINER SURFACE -->

Use [USER_CONTAINER.md](./USER_CONTAINER.md) for the mounted-workspace command flow, passphrase/key
handling, and smoke-tested example commands.
