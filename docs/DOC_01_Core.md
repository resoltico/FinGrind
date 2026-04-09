---
afad: "3.5"
version: "0.1.0"
domain: CORE
updated: "2026-04-08"
route:
  keywords: [fingrind, core, journal, money, provenance, correction, account-code, currency-code, idempotency]
  questions: ["what core value types does fingrind expose", "how does a journal entry work in fingrind", "what provenance fields are durable in fingrind"]
---

# Core API Reference

## `AccountCode`

Record representing a jurisdiction-agnostic account identifier.

### Signature
```java
public record AccountCode(String value)
```

### Parameters
| Name | Req | Semantics |
|:-----|:----|:----------|
| `value` | Y | account code text |

### Constraints
- Purpose: Identifies one account on one journal line
- Normalization: Strips surrounding whitespace
- Rejects: `null` and blank text
- State: Immutable value object

---

## `CurrencyCode`

Record representing a canonical three-letter currency code.

### Signature
```java
public record CurrencyCode(String value)
```

### Parameters
| Name | Req | Semantics |
|:-----|:----|:----------|
| `value` | Y | ISO-style currency code |

### Constraints
- Purpose: Carries the declared currency for `Money`
- Normalization: Strips whitespace and uppercases with `Locale.ROOT`
- Rejects: any value outside `[A-Z]{3}`
- State: Immutable value object

---

## `IdempotencyKey`

Record representing the caller-supplied duplicate-submission identity.

### Signature
```java
public record IdempotencyKey(String value)
```

### Parameters
| Name | Req | Semantics |
|:-----|:----|:----------|
| `value` | Y | duplicate-submission key |

### Constraints
- Purpose: Scopes duplicate rejection inside one selected book
- Normalization: Strips surrounding whitespace
- Rejects: `null` and blank text
- State: Immutable value object

---

## `PostingId`

Record representing the durable identity of one committed posting fact.

### Signature
```java
public record PostingId(String value)
```

### Parameters
| Name | Req | Semantics |
|:-----|:----|:----------|
| `value` | Y | committed posting identifier |

### Constraints
- Purpose: Names one committed posting fact
- Normalization: Strips surrounding whitespace
- Rejects: `null` and blank text
- State: Immutable value object

---

## `CorrectionReference`

Record representing additive linkage from a new posting to an earlier one.

### Signature
```java
public record CorrectionReference(
    CorrectionReference.CorrectionKind kind,
    PostingId priorPostingId)
```

### Parameters
| Name | Req | Semantics |
|:-----|:----|:----------|
| `kind` | Y | correction form |
| `priorPostingId` | Y | corrected posting identity |

### Constraints
- Purpose: Links one new posting fact to one earlier posting
- Rejects: `null` kind and `null` prior posting id
- State: Immutable value object

---

## `CorrectionReference.CorrectionKind`

Enumeration of additive correction forms.

### Signature
```java
public enum CorrectionKind {
  REVERSAL,
  AMENDMENT
}
```

### Members
| Member | Value | Semantics |
|:-------|:------|:----------|
| `REVERSAL` | `REVERSAL` | negate earlier posting effect |
| `AMENDMENT` | `AMENDMENT` | replace earlier posting additively |

### Constraints
- Purpose: Distinguishes full reversal from amendment linkage
- Type: `enum`

---

## `Money`

Record representing an exact amount in one declared currency.

### Signature
```java
public record Money(CurrencyCode currencyCode, BigDecimal amount)
```

### Parameters
| Name | Req | Semantics |
|:-----|:----|:----------|
| `currencyCode` | Y | declared currency |
| `amount` | Y | exact decimal amount |

### Constraints
- Purpose: Carries monetary value without floating-point semantics
- Normalization: strips trailing zeroes and normalizes negative scale to zero
- Rejects: `null` fields and negative amounts
- State: Immutable value object

---

## `JournalLine`

Record representing one debit or credit line inside a journal entry.

### Signature
```java
public record JournalLine(
    AccountCode accountCode,
    JournalLine.EntrySide side,
    Money amount)
```

### Parameters
| Name | Req | Semantics |
|:-----|:----|:----------|
| `accountCode` | Y | line account identifier |
| `side` | Y | debit or credit side |
| `amount` | Y | positive monetary amount |

### Constraints
- Purpose: Carries one side of the journal equation
- Rejects: `null` fields and zero amounts
- State: Immutable record

---

## `JournalLine.EntrySide`

Enumeration of the journal equation sides.

### Signature
```java
public enum EntrySide {
  DEBIT,
  CREDIT
}
```

### Members
| Member | Value | Semantics |
|:-------|:------|:----------|
| `DEBIT` | `DEBIT` | debit side |
| `CREDIT` | `CREDIT` | credit side |

### Constraints
- Purpose: Makes line polarity explicit in the type system
- Type: `enum`

---

## `JournalEntry`

Record representing a balanced journal entry ready for the write boundary.

### Signature
```java
public record JournalEntry(
    LocalDate effectiveDate,
    List<JournalLine> lines,
    Optional<CorrectionReference> correctionReference)
```

### Parameters
| Name | Req | Semantics |
|:-----|:----|:----------|
| `effectiveDate` | Y | economic posting date |
| `lines` | Y | ordered journal lines |
| `correctionReference` | N | additive correction linkage |

### Constraints
- Purpose: Carries one balanced posting request body
- Normalization: copies `lines`; `null` correction becomes `Optional.empty()`
- Rejects: empty lines, mixed currencies, unbalanced totals, `null` effective date
- State: Immutable record

---

## `ProvenanceEnvelope`

Record representing the durable audit provenance attached to one posting attempt.

### Signature
```java
public record ProvenanceEnvelope(
    String actorId,
    ProvenanceEnvelope.ActorType actorType,
    String commandId,
    IdempotencyKey idempotencyKey,
    String causationId,
    Optional<String> correlationId,
    Instant recordedAt,
    Optional<String> reason,
    ProvenanceEnvelope.SourceChannel sourceChannel)
```

### Parameters
| Name | Req | Semantics |
|:-----|:----|:----------|
| `actorId` | Y | acting identity |
| `actorType` | Y | actor classification |
| `commandId` | Y | request command identity |
| `idempotencyKey` | Y | duplicate-submission identity |
| `causationId` | Y | causal chain identifier |
| `correlationId` | N | wider correlation identifier |
| `recordedAt` | Y | durable commit instant |
| `reason` | N | human correction reason |
| `sourceChannel` | Y | ingress surface |

### Constraints
- Purpose: Preserves durable audit context for one posting attempt
- Normalization: trims required strings and strips optional strings to empty-option
- Rejects: blank required strings and `null` required fields
- State: Immutable record

---

## `ProvenanceEnvelope.ActorType`

Enumeration of durable actor classifications.

### Signature
```java
public enum ActorType {
  USER,
  SYSTEM,
  AGENT
}
```

### Members
| Member | Value | Semantics |
|:-------|:------|:----------|
| `USER` | `USER` | human operator |
| `SYSTEM` | `SYSTEM` | automated system actor |
| `AGENT` | `AGENT` | AI or agent surface |

### Constraints
- Purpose: Distinguishes audit actor origin
- Type: `enum`

---

## `ProvenanceEnvelope.SourceChannel`

Enumeration of supported request ingress surfaces.

### Signature
```java
public enum SourceChannel {
  CLI,
  API,
  TEST
}
```

### Members
| Member | Value | Semantics |
|:-------|:------|:----------|
| `CLI` | `CLI` | command-line ingress |
| `API` | `API` | service/API ingress |
| `TEST` | `TEST` | test-only ingress |

### Constraints
- Purpose: Preserves where a request entered the system
- Type: `enum`
