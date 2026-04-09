---
afad: "3.5"
version: "0.1.0"
domain: APPLICATION
updated: "2026-04-08"
route:
  keywords: [fingrind, application, post-entry, preflight, rejection, committed, write-boundary, sealed-result]
  questions: ["how does the post-entry application boundary work", "what results can posting return in fingrind", "what rejects a duplicate posting in fingrind"]
---

# Application API Reference

## `PostEntryCommand`

Record representing one application-layer request to preflight or commit a journal entry.

### Signature
```java
public record PostEntryCommand(
    JournalEntry journalEntry,
    ProvenanceEnvelope provenance)
```

### Parameters
| Name | Req | Semantics |
|:-----|:----|:----------|
| `journalEntry` | Y | balanced journal body |
| `provenance` | Y | durable audit envelope |

### Constraints
- Purpose: Carries one write-boundary request into the application layer
- Rejects: `null` journal entry and `null` provenance
- State: Immutable record

---

## `PostEntryResult`

Sealed result family representing the outcome of one posting request.

### Definition
```java
public sealed interface PostEntryResult
    permits PostEntryResult.PreflightAccepted,
            PostEntryResult.Committed,
            PostEntryResult.Rejected
```

### Constraints
- Purpose: Keeps committed, preflight-only, and rejected outcomes explicit
- Narrowing: pattern-match on `PreflightAccepted`, `Committed`, or `Rejected`
- Surface: Returned by `PostingApplicationService.preflight(...)` and `commit(...)`

---

## `PostEntryResult.PreflightAccepted`

Record representing a request that is valid but not yet durably committed.

### Signature
```java
public record PreflightAccepted(
    IdempotencyKey idempotencyKey,
    LocalDate effectiveDate)
    implements PostEntryResult
```

### Parameters
| Name | Req | Semantics |
|:-----|:----|:----------|
| `idempotencyKey` | Y | validated request key |
| `effectiveDate` | Y | validated business date |

### Constraints
- Purpose: Confirms a later commit attempt is admissible
- Rejects: `null` fields
- State: Immutable result variant

---

## `PostEntryResult.Committed`

Record representing one durably committed posting fact.

### Signature
```java
public record Committed(
    PostingId postingId,
    IdempotencyKey idempotencyKey,
    LocalDate effectiveDate,
    Instant recordedAt)
    implements PostEntryResult
```

### Parameters
| Name | Req | Semantics |
|:-----|:----|:----------|
| `postingId` | Y | committed posting identity |
| `idempotencyKey` | Y | accepted request key |
| `effectiveDate` | Y | committed business date |
| `recordedAt` | Y | durable commit instant |

### Constraints
- Purpose: Reports one successful durable write
- Rejects: `null` fields
- State: Immutable result variant

---

## `PostEntryResult.Rejected`

Record representing a request that did not cross the durable write boundary.

### Signature
```java
public record Rejected(
    PostingRejectionCode code,
    String message,
    IdempotencyKey idempotencyKey)
    implements PostEntryResult
```

### Parameters
| Name | Req | Semantics |
|:-----|:----|:----------|
| `code` | Y | deterministic rejection code |
| `message` | Y | human-readable rejection text |
| `idempotencyKey` | Y | rejected request key |

### Constraints
- Purpose: Returns a deterministic rejection payload to outer adapters
- Normalization: strips surrounding whitespace from `message`
- Rejects: `null` fields and blank messages
- State: Immutable result variant

---

## `PostingRejectionCode`

Enumeration of deterministic posting rejection categories.

### Signature
```java
public enum PostingRejectionCode {
  DUPLICATE_IDEMPOTENCY_KEY
}
```

### Members
| Member | Value | Semantics |
|:-------|:------|:----------|
| `DUPLICATE_IDEMPOTENCY_KEY` | `DUPLICATE_IDEMPOTENCY_KEY` | book-local duplicate submission |

### Constraints
- Purpose: Keeps rejection classification machine-readable
- Type: `enum`

---

## `PostingApplicationService`

Class representing the explicit application write boundary for one selected book.

### Signature
```java
public final class PostingApplicationService
```

### Constraints
- Constructor: Requires `PostingFactStore` and `Supplier<PostingId>`
- Surface: Exposes `preflight(PostEntryCommand)` and `commit(PostEntryCommand)`
- Preflight: Accepts fresh idempotency keys and rejects duplicates
- Commit: Persists one `PostingFact` or returns deterministic duplicate rejection
- Failure mapping: Converts store-level duplicate `IllegalStateException` into `Rejected`
- State: Holds runtime seams only; no internal cache
