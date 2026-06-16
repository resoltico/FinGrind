---
afad: "4.0"
version: "0.55.0"
domain: DEVELOPER_UNSIGNED_DISTRIBUTION
updated: "2026-06-16"
route:
  keywords: [fingrind, macos, windows, unsigned, attestation, checksum, quarantine, smartscreen, gatekeeper, powershell, notarization, authenticode]
  questions: ["how does fingrind publish unsigned macos and windows bundles", "does fingrind require apple notarization or authenticode", "what is the trust model for fingrind macos and windows downloads"]
---

# Unsigned macOS And Windows Distribution

**Purpose**: Define the real FinGrind policy for publishing and operating unsigned macOS and
Windows bundles.
**Prerequisites**: Familiarity with [DEVELOPER_DISTRIBUTION.md](./DEVELOPER_DISTRIBUTION.md),
[DEVELOPER_RELEASE_PUBLICATION.md](./DEVELOPER_RELEASE_PUBLICATION.md), and
[USER_INSTALL.md](./USER_INSTALL.md).

## Current Policy

FinGrind publishes these self-contained bundle targets today:
- `macos-aarch64`
- `macos-x86_64`
- `linux-x86_64`
- `linux-aarch64`
- `windows-x86_64`

`windows-aarch64` remains declared in `bundle-layout-contract.json`, with
`bundle-publication-contract.json` marking it as `status = "not-published"`.

## Permanent Trust Constraint

FinGrind does not depend on Apple Developer ID signing, Apple notarization, Authenticode, EV
certificates, or any other trust-conferring code-signing program.

The publication trust chain is:
- published release asset bytes
- published `.sha256` sidecar
- GitHub artifact attestation for the published bytes

Certificate trust is intentionally not part of the contract.

## What The Contracts Own

`bundle-layout-contract.json` is the canonical owner for:
- target classifier
- launcher path and launcher command
- compatibility label
- Linux compatibility-floor facts
- per-target public publication status
- per-target proving runner metadata

`release-publication-contract.json` owns the shared publication workflow facts:
- required CI workflow and gate names
- container publication identity
- container platform set
- latest-tag policy

There is no separate public-distribution sidecar anymore. Supported and unsupported public bundle
lists are derived from the bundle-target registry itself.

## Runtime And Launcher Rules

Current launcher contract:
- Unix bundles publish `bin/fingrind`
- `windows-x86_64` publishes `bin/fingrind.ps1`
- FinGrind does not carry a parallel `.cmd` compatibility shim

Current publication verification:
- CI proves every published bundle target on its native GitHub-hosted runner before merge
- the tagged release workflow rebuilds and smoke-tests every published bundle target on its native
  runner before GitHub Release publication
- Linux bundles add the compatibility-floor rerun inside the declared Rocky Linux 9 container

## Operator Truthfulness

The repository does not promise that every download transport suppresses operating-system trust
prompts. Browser downloads can attach host trust metadata such as macOS quarantine or Windows
Mark-of-the-Web. FinGrind's supported answer is to verify checksum plus attestation first, then
follow the local operating-system unblock flow when the chosen download path adds those markers.

Do not document transport folklore as if it were a guaranteed product contract.

## Documentation Rule

Public docs must describe macOS and Windows bundles as:
- published
- unsigned
- verified by checksum plus GitHub attestation

They must not claim:
- notarization
- Authenticode
- certificate-backed first-run trust
- Linux-only public bundle publication
