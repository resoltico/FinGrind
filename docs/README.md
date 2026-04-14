---
afad: "3.5"
version: "0.13.0"
domain: DOCUMENTATION_INDEX
updated: "2026-04-14"
route:
  keywords: [fingrind, docs, index, user-guides, developer-guides, api-reference, schema, examples, sqlite]
  questions: ["where should I start in the fingrind docs", "which docs are user-facing in fingrind", "where are the developer and api docs in fingrind"]
---

# Documentation Index

**Purpose**: Route readers to the right FinGrind documentation set quickly.
**Prerequisites**: None.

## Start Here

Start with the root [README.md](../README.md) for the storefront overview.
Then choose one of the user, developer, or reference tracks below.

## User Guides

- [USER_CLI.md](./USER_CLI.md): packaged CLI usage, commands, exit codes, and runtime requirements
- [USER_REQUESTS.md](./USER_REQUESTS.md): posting and account-declaration JSON shapes, reversal rules, rejection codes, and response envelopes
- [USER_EXAMPLES.md](./USER_EXAMPLES.md): copy-paste command flows for opening books, declaring accounts, listing accounts, preflight, commit, duplicates, stdin, and reversal templates
- [examples/basic-posting-request.json](./examples/basic-posting-request.json): minimal valid request payload
- [examples/declare-account-cash.json](./examples/declare-account-cash.json): minimal account-declaration request for a debit-balance cash account
- [examples/declare-account-revenue.json](./examples/declare-account-revenue.json): minimal account-declaration request for a credit-balance revenue account
- [examples/unknown-account-request.json](./examples/unknown-account-request.json): posting request that deterministically rejects with `unknown-account`
- [examples/basic-posting-committed-response.json](./examples/basic-posting-committed-response.json): example committed response with a UUID v7 `postingId`
- [examples/reversal-request.json](./examples/reversal-request.json): reversal request template that needs a real prior posting id
- [examples/invalid-empty-lines-request.json](./examples/invalid-empty-lines-request.json): deterministic invalid-request example

## Developer Guides

- [DEVELOPER.md](./DEVELOPER.md)
- [DEVELOPER_DISTRIBUTION.md](./DEVELOPER_DISTRIBUTION.md)
- [DEVELOPER_DOCUMENTATION.md](./DEVELOPER_DOCUMENTATION.md)
- [DEVELOPER_DOCKER.md](./DEVELOPER_DOCKER.md)
- [DEVELOPER_JAVA.md](./DEVELOPER_JAVA.md)
- [GITHUB_BOOTSTRAP_PROTOCOL.md](./GITHUB_BOOTSTRAP_PROTOCOL.md)
- [RELEASE_PROTOCOL.md](./RELEASE_PROTOCOL.md)
- [DEVELOPER_JAZZER.md](./DEVELOPER_JAZZER.md)
- [DEVELOPER_JAZZER_OPERATIONS.md](./DEVELOPER_JAZZER_OPERATIONS.md)
- [DEVELOPER_JAZZER_COVERAGE.md](./DEVELOPER_JAZZER_COVERAGE.md)
- [DEVELOPER_SQLITE.md](./DEVELOPER_SQLITE.md)

## Reference And Schema

- [DOC_00_Index.md](./DOC_00_Index.md)
- [DOC_01_Core.md](./DOC_01_Core.md)
- [DOC_02_Application.md](./DOC_02_Application.md)
- [DOC_03_BookSessionsAndAdapters.md](./DOC_03_BookSessionsAndAdapters.md)
- [sqlite/SCHEMA_CORE.md](./sqlite/SCHEMA_CORE.md)
