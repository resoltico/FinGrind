---
afad: "4.0"
version: "0.51.0"
domain: CORE
updated: "2026-06-03"
route:
  keywords: [fingrind, core, source-document, storage-locator, content-sha256, reversal, source-channel, wire-value]
  questions: ["where are fingrind source-document primitives documented", "how does reversal lineage work in the core model", "what is WireValue in fingrind core"]
---

# Core Evidence And Wire Reference

This companion file continues the exported `core` reference for source-document evidence,
reversal-lineage primitives, committed source-channel provenance, and the shared machine-vocabulary
contract.

The main accounting and ledger primitives remain in
[DOC_01_Core.md](./DOC_01_Core.md).

## `SourceDocumentId`

`SourceDocumentId` is the stable identifier for one source document referenced by accounting
evidence.

```java
public record SourceDocumentId(String value)
```

- Purpose: carry one durable source-document identity through request, storage, and query
  surfaces
- Validation: rejects `null`, blank text, and values outside the public source-document-id grammar

## `SourceDocumentType`

`SourceDocumentType` is the stable public classifier for one source document referenced by
accounting evidence.

```java
public record SourceDocumentType(String value)
```

- Purpose: distinguish source-document kinds without promoting adapter-specific file semantics into
  the core model
- Validation: rejects `null`, blank text, and values outside the public source-document-type
  grammar

## `StorageLocator`

`StorageLocator` is the stable retained locator for one evidence artifact payload.

```java
public record StorageLocator(String value)
```

- Purpose: keep evidence retention references explicit without forcing one storage backend into the
  core model
- Validation: rejects `null`, blank text, and values longer than the public maximum length

## `ContentSha256`

`ContentSha256` is the canonical lowercase SHA-256 hex digest retained for one evidence artifact.

```java
public record ContentSha256(String value)
```

- Purpose: let FinGrind retain verifiable content identity for source documents
- Validation: rejects `null` and any value outside the 64-character lowercase hex grammar

## `SourceDocumentReference`

`SourceDocumentReference` is the typed evidence link to one source document.

```java
public record SourceDocumentReference(
    SourceDocumentId sourceDocumentId,
    SourceDocumentType sourceDocumentType,
    LocalDate documentDate,
    Instant capturedAt,
    StorageLocator storageLocator,
    ContentSha256 contentSha256)
```

- Purpose: keep source-document evidence structured and durable across request and
  committed-posting surfaces
- Validation: rejects `null` source-document id, source-document type, document date, capture
  timestamp, storage locator, or content digest

## `ReversalReason`

`ReversalReason` is the plain-language reason recorded for a reversal posting.

```java
public record ReversalReason(String value)
```

- Purpose: preserve operator-supplied reversal narrative in typed form
- Validation: rejects `null` and blank text after stripping surrounding whitespace

## `ReversalReference`

`ReversalReference` is the additive link from a new posting to an earlier committed posting.

```java
public record ReversalReference(PostingId priorPostingId)
```

- Purpose: model reversal lineage outside the journal grammar
- Validation: rejects `null` prior posting id

## `SourceChannel`

`SourceChannel` is the canonical owner of committed-entry ingress provenance.

```java
public enum SourceChannel implements WireValue {
  CLI,
  SYSTEM
}
```

- Purpose: record whether one committed posting came from the operator-facing CLI surface or an
  internal system workflow such as period-result transfer
- Current scope: `CLI` for operator-issued commands and `SYSTEM` for FinGrind-generated
  administrative postings
- Wire contract: `wireValue()`, `wireValues()`, `values()`, and `fromWireValue(...)` own the
  stable public vocabulary

## `WireValue`

`WireValue` is the explicit contract for stable machine-facing enum vocabulary owned by FinGrind.

```java
public interface WireValue {
  String wireValue();
  static <E extends Enum<E> & WireValue> List<String> wireValues(Class<E> enumType)
  static <E extends Enum<E> & WireValue> E fromWireValue(
      Class<E> enumType, String wireValue, String unsupportedValueLabel)
}
```

- Purpose: make stable JSON and protocol tokens a compile-time contract instead of a reflective
  convention
- Scope: implemented by exported enums whose public wire form must remain decoupled from Java enum
  constant names
- Parsing: `wireValues(...)` exposes the declaration-order public vocabulary, and
  `fromWireValue(...)` resolves one stable token through the shared cached enum-vocabulary owner
  instead of forcing each enum to reimplement its own lookup logic
