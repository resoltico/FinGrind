# Security Policy

## Supported Versions

FinGrind accepts security reports for:

- the current `main` branch
- the latest published release

Older releases are not supported for coordinated security fixes.

## Reporting A Vulnerability

Do not open a public issue for a suspected security vulnerability.

Use GitHub private vulnerability reporting for this repository:

1. Open the repository's `Security` tab on GitHub.
2. Choose `Report a vulnerability`.
3. Submit the report with reproduction steps, affected version or commit, impact, and any proof of concept that is safe to share privately.

If GitHub private reporting is temporarily unavailable, open a private GitHub Security Advisory draft for this repository instead of filing a public issue.

The live repository setting behind that guidance is verified by `./scripts/verify-security-policy-surface.sh`.

## What To Include

Please include:

- the affected release, tag, or commit SHA
- the operating system and runtime mode involved
- exact command lines, inputs, and file layout needed to reproduce
- observed impact and expected behavior
- whether the issue affects protected-book confidentiality, integrity, availability, or release provenance

## Response Expectations

FinGrind aims to:

- acknowledge a new private report within 5 business days
- provide an initial triage outcome or clarification request within 10 business days
- coordinate a fix and disclosure plan before public release of the report

## Disclosure Rules

- Give the maintainers reasonable time to investigate and fix the issue before public disclosure.
- Public disclosure should happen only after a coordinated fix or an agreed disclosure date.
- When a fix ships, the public record should identify the affected versions, impact, and mitigation.
