package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.PostingId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** Stable cursor for keyset pagination through ascending account ledger order. */
public record AccountLedgerPageCursor(
    LocalDate effectiveDate, Instant recordedAt, PostingId postingId) {
  /** Validates one account ledger page cursor. */
  public AccountLedgerPageCursor {
    Objects.requireNonNull(effectiveDate, "effectiveDate");
    Objects.requireNonNull(recordedAt, "recordedAt");
    Objects.requireNonNull(postingId, "postingId");
  }

  /** Returns the stable public wire value for this cursor. */
  public String wireValue() {
    return KeysetPageCursorCodec.encode(effectiveDate, recordedAt, postingId.value());
  }

  /** Parses one stable public wire value. */
  public static AccountLedgerPageCursor fromWireValue(String wireValue) {
    KeysetPageCursorCodec.Parts parts =
        KeysetPageCursorCodec.decode(wireValue, "Unsupported account ledger page cursor");
    return new AccountLedgerPageCursor(
        parts.effectiveDate(), parts.recordedAt(), new PostingId(parts.identifier()));
  }
}
