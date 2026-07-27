package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

/** Focused stream-boundary coverage for protected-book pair-publication evidence. */
class SqliteProtectedBookPairPublicationRecordTest {
  @Test
  void digest_rejectsZeroByteReadsRatherThanSpinning() throws IOException {
    try (InputStream input =
        new InputStream() {
          @Override
          public int read(byte[] buffer, int offset, int length) {
            return 0;
          }

          @Override
          public int read() {
            throw new UnsupportedOperationException("byte-wise reads are not used by this test");
          }
        }) {
      IOException exception =
          assertThrows(
              IOException.class,
              () -> SqliteProtectedBookPairPublicationRecord.digest(input, "pair evidence"));

      assertEquals("The pair evidence did not make read progress.", exception.getMessage());
    }
  }
}
