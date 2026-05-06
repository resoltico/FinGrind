package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.PostingId;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Objects;

/** Local bookkeeping cursor for reverse-chronological posting-history pagination. */
public record PostingHistoryCursor(
    LocalDate effectiveDate, Instant recordedAt, PostingId postingId) {
  private static final byte CURSOR_FORMAT_VERSION = 1;
  private static final int FIXED_CURSOR_BYTES =
      Byte.BYTES + Long.BYTES + Long.BYTES + Integer.BYTES + Integer.BYTES;

  public PostingHistoryCursor {
    Objects.requireNonNull(effectiveDate, "effectiveDate");
    Objects.requireNonNull(recordedAt, "recordedAt");
    Objects.requireNonNull(postingId, "postingId");
  }

  /** Builds one pagination cursor from a committed posting. */
  public static PostingHistoryCursor fromPosting(CommittedPosting posting) {
    Objects.requireNonNull(posting, "posting");
    return new PostingHistoryCursor(
        posting.journalEntry().effectiveDate(),
        posting.provenance().recordedAt(),
        posting.postingId());
  }

  /** Returns the stable machine-facing wire value for this local cursor. */
  public String wireValue() {
    byte[] postingIdBytes = postingId.value().getBytes(StandardCharsets.UTF_8);
    ByteBuffer buffer = ByteBuffer.allocate(FIXED_CURSOR_BYTES + postingIdBytes.length);
    buffer.put(CURSOR_FORMAT_VERSION);
    buffer.putLong(effectiveDate.toEpochDay());
    buffer.putLong(recordedAt.getEpochSecond());
    buffer.putInt(recordedAt.getNano());
    buffer.putInt(postingIdBytes.length);
    buffer.put(postingIdBytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
  }
}
