---
afad: "3.5"
version: "0.3.1"
domain: APPLICATION
updated: "2026-04-09"
route:
  keywords: [fingrind, application, post-entry, preflight, rejection, committed, write-boundary, sealed-result]
  questions: ["how does the post-entry application boundary work", "what results can posting return in fingrind", "what deterministic rejections does fingrind expose"]
---

# Application API Reference

## `PostEntryCommand`

`PostEntryCommand` is the application-layer request to preflight or commit one journal entry.

```java
public record PostEntryCommand(
    JournalEntry journalEntry,
    Optional<CorrectionReference> correctionReference,
    RequestProvenance requestProvenance,
    SourceChannel sourceChannel)
```

- Purpose: carry the balanced journal body, correction linkage, accepted request provenance, and ingress channel into the write boundary
- Normalization: `null` correction becomes `Optional.empty()`
- Validation: rejects `null` journal entry, request provenance, and source channel

## `PostEntryResult`

`PostEntryResult` is the closed result family returned by the application write boundary.

```java
public sealed interface PostEntryResult
```

- Variants: `PreflightAccepted`, `Committed`, `Rejected`
- Purpose: keep preflight success, commit success, and deterministic rejection explicit

## `PostEntryResult.PreflightAccepted`

`PreflightAccepted` is the success variant for a validated request that has not been committed yet.

```java
public record PreflightAccepted(
    IdempotencyKey idempotencyKey,
    LocalDate effectiveDate)
    implements PostEntryResult
```

- Purpose: confirm that a later commit attempt is admissible against current state
- Validation: rejects `null` fields

## `PostEntryResult.Committed`

`Committed` is the success variant for one durably stored posting fact.

```java
public record Committed(
    PostingId postingId,
    IdempotencyKey idempotencyKey,
    LocalDate effectiveDate,
    Instant recordedAt)
    implements PostEntryResult
```

- Purpose: report durable write success with posting identity and commit timestamp
- Validation: rejects `null` fields

## `PostEntryResult.Rejected`

`Rejected` is the variant for a request that did not cross the durable write boundary.

```java
public record Rejected(
    IdempotencyKey idempotencyKey,
    PostingRejection rejection)
    implements PostEntryResult
```

- Purpose: return a deterministic domain rejection without throwing
- Validation: rejects `null` idempotency key and rejection

## `PostingApplicationService`

`PostingApplicationService` owns preflight and commit behavior for posting entries.

```java
public final class PostingApplicationService
```

- Constructor: requires `PostingFactStore`, `PostingIdGenerator`, and `Clock`
- Surface: exposes `preflight(PostEntryCommand)` and `commit(PostEntryCommand)`
- Policy: enforces duplicate idempotency, correction-target existence, correction-reason rules, one-reversal-per-target, and reversal negation
- Commit path: creates `CommittedProvenance`, generates a fresh `PostingId`, and maps runtime commit outcomes into `PostEntryResult`

## `PostingIdGenerator`

`PostingIdGenerator` supplies the next posting identity during commit.

```java
public interface PostingIdGenerator {
  PostingId nextPostingId();
}
```

- Purpose: keep posting-id generation explicit and injectable at the application boundary

## `PostingRejection`

`PostingRejection` is the closed family of deterministic domain refusals for posting requests.

```java
public sealed interface PostingRejection
```

- Variants: duplicate idempotency, correction reason required or forbidden, missing correction target, duplicate reversal, reversal mismatch
- Purpose: keep validly parsed but inadmissible requests machine-distinguishable

## `PostingRejection.CorrectionReasonForbidden`

`CorrectionReasonForbidden` rejects a non-corrective request that still supplied `provenance.reason`.

```java
public record CorrectionReasonForbidden() implements PostingRejection
```

- Purpose: keep correction reason scoped to actual corrective postings

## `PostingRejection.CorrectionReasonRequired`

`CorrectionReasonRequired` rejects a corrective posting that omitted `provenance.reason`.

```java
public record CorrectionReasonRequired() implements PostingRejection
```

- Purpose: require a human-readable reason for every amendment or reversal

## `PostingRejection.CorrectionTargetNotFound`

`CorrectionTargetNotFound` rejects a correction whose `priorPostingId` does not exist in the selected book.

```java
public record CorrectionTargetNotFound(PostingId priorPostingId)
    implements PostingRejection
```

- Purpose: keep correction lineage anchored to an existing committed posting
- Validation: rejects `null` `priorPostingId`

## `PostingRejection.DuplicateIdempotencyKey`

`DuplicateIdempotencyKey` rejects a request whose `idempotencyKey` already exists in the selected book.

```java
public record DuplicateIdempotencyKey() implements PostingRejection
```

- Purpose: make duplicate submission a stable deterministic outcome

## `PostingRejection.ReversalAlreadyExists`

`ReversalAlreadyExists` rejects a reversal attempt when the target posting already has a full reversal.

```java
public record ReversalAlreadyExists(PostingId priorPostingId)
    implements PostingRejection
```

- Purpose: enforce one reversal per target posting
- Validation: rejects `null` `priorPostingId`

## `PostingRejection.ReversalDoesNotNegateTarget`

`ReversalDoesNotNegateTarget` rejects a reversal candidate whose lines do not negate the target posting exactly.

```java
public record ReversalDoesNotNegateTarget(PostingId priorPostingId)
    implements PostingRejection
```

- Purpose: keep `REVERSAL` semantically stronger than a generic amendment
- Validation: rejects `null` `priorPostingId`
