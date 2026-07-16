package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.PostingId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** Stable cursor for keyset-pagination through reverse-chronological posting history. */
public record PostingPageCursor(LocalDate effectiveDate, Instant recordedAt, PostingId postingId) {
  /** Validates one posting-page cursor. */
  public PostingPageCursor {
    Objects.requireNonNull(effectiveDate, "effectiveDate");
    Objects.requireNonNull(recordedAt, "recordedAt");
    Objects.requireNonNull(postingId, "postingId");
  }

  /** Returns the stable public wire value for this cursor. */
  public String wireValue() {
    return KeysetPageCursorCodec.encode(effectiveDate, recordedAt, postingId.value());
  }

  /** Parses one stable public wire value. */
  public static PostingPageCursor fromWireValue(String wireValue) {
    KeysetPageCursorCodec.Parts parts =
        KeysetPageCursorCodec.decode(wireValue, "Unsupported posting page cursor");
    return new PostingPageCursor(
        parts.effectiveDate(), parts.recordedAt(), new PostingId(parts.identifier()));
  }

  /** Creates one cursor anchored at the supplied committed posting. */
  public static PostingPageCursor fromPosting(PostingFact postingFact) {
    Objects.requireNonNull(postingFact, "postingFact");
    return new PostingPageCursor(
        postingFact.journalEntry().effectiveDate(),
        postingFact.provenance().recordedAt(),
        postingFact.postingId());
  }
}
