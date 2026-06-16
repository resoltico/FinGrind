---
afad: "4.0"
version: "0.55.0"
domain: CLI_PDF_ADAPTERS
updated: "2026-06-16"
route:
  keywords: [fingrind, cli, app, pdf, report-pdf, adapter, entrypoint, process, pdfbox]
  questions: ["where is the public cli entrypoint in fingrind", "how does fingrind render pdf reports", "which module owns pdf export in fingrind"]
---

# CLI And PDF Adapter API Reference

This file documents the two exported adapter surfaces that sit at the outer edge of the system:
the public CLI process entrypoint and the dedicated PDF-report adapter module.

## `App`

`App` is the public process entrypoint for the FinGrind CLI adapter.

```java
public final class App
```

- Surface: `main(String[] args)`
- Purpose: construct one production CLI invocation, run it against `System.in` / `System.out`, and
  terminate the process with the returned exit code when non-zero
- Boundary: `App` is the exported CLI surface; the deeper CLI parsing and rendering classes are
  adapter internals, not separate public entrypoints

## `PdfReportService`

`PdfReportService` is the public PDF artifact adapter exported by the `report-pdf` module.

```java
public final class PdfReportService
```

- Purpose: render contract-owned reporting DTOs into deterministic PDF byte arrays without leaking
  PDFBox concerns into `contract`, `executor`, or command parsing
- Input surface:
  `renderAccountBalance(...)`, `renderTrialBalance(...)`, `renderAccountLedger(...)`,
  `renderPeriodSummary(...)`
- Ownership split: report semantics stay in `contract` / `executor`; document layout and PDFBox
  lifecycle stay in `report-pdf`
- I/O contract: returns `byte[]` so CLI code decides where artifacts are written
- Privacy boundary: rendered PDF content and metadata do not embed the selected source book's
  absolute filesystem path
