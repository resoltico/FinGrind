---
afad: "4.0"
version: "0.30.0"
domain: CONTRACT_EXECUTOR_INDEX
updated: "2026-05-05"
route:
  keywords: [fingrind, contract, executor, api, overview, routing, protocol, reports, ledger-plan]
  questions: ["where is the split contract and executor api reference in fingrind", "which doc covers protocol discovery versus reports in fingrind", "where should I look for posting and ledger plan contract types"]
---

# Contract And Executor API Reference

This file is the routing overview for the split `contract` and `executor` reference spine.
The detailed entries now live in smaller AFAD reference files so protocol/discovery, read/report,
and write/ledger-plan surfaces can evolve without recreating one retrieval-hostile god-file.

The public reference spine documents the published language and exported service surfaces. The
named bounded contexts behind those surfaces are documented in
[DEVELOPER_DOMAIN_MODEL.md](./DEVELOPER_DOMAIN_MODEL.md): `contract` owns the published language,
`executor.bookkeeping` owns the local bookkeeping model, and `executor.workflow` owns
plan-orchestration semantics.

Use these files:
- [DOC_02_ProtocolAndDiscovery.md](./DOC_02_ProtocolAndDiscovery.md): contract-owned protocol metadata, discovery descriptors, request/response shapes, and deterministic contract errors
- [DOC_02_AdministrationAndReports.md](./DOC_02_AdministrationAndReports.md): administration DTOs, lifecycle inspection, queries, reports, and read-side rejections
- [DOC_02_PostingAndLedgerPlans.md](./DOC_02_PostingAndLedgerPlans.md): posting commands, posting results, write-side rejections, ledger plans, plan journals, and executor write services

Routing is authoritative in [DOC_00_Index.md](./DOC_00_Index.md), which maps every public symbol to the precise file and `##` heading.
