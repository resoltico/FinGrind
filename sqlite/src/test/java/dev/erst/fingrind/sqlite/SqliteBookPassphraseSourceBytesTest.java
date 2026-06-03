package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.protocol.ProtocolInteractionLimits;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** Tests for {@link SqliteBookPassphraseSourceBytes}. */
class SqliteBookPassphraseSourceBytesTest {
  @Test
  void read_retriesZeroByteReadsAndReturnsTheBoundedPayload() throws IOException {
    byte[] payload = "bounded-passphrase".getBytes(StandardCharsets.UTF_8);
    try (InputStream inputStream =
        new InputStream() {
          private boolean zeroByteReadReturned;
          private int offset;

          @Override
          public int read(byte[] buffer, int bufferOffset, int length) {
            if (!zeroByteReadReturned) {
              zeroByteReadReturned = true;
              return 0;
            }
            if (offset >= payload.length) {
              return -1;
            }
            int bytesToCopy = Math.min(length, payload.length - offset);
            System.arraycopy(payload, offset, buffer, bufferOffset, bytesToCopy);
            offset += bytesToCopy;
            return bytesToCopy;
          }

          @Override
          public int read() throws IOException {
            throw new UnsupportedOperationException("byte-wise reads are not used by this test");
          }
        }) {
      assertArrayEquals(payload, SqliteBookPassphraseSourceBytes.read(inputStream));
    }
  }

  @Test
  void read_zeroizesTheTemporaryBufferWhenThePayloadIsOversized() throws IOException {
    byte[] oversizedPassphrase =
        "x"
            .repeat(ProtocolInteractionLimits.BOOK_PASSPHRASE_MAX_UTF8_BYTES + 1)
            .getBytes(StandardCharsets.UTF_8);
    try (RecordingInputStream inputStream = new RecordingInputStream(oversizedPassphrase)) {
      assertThrows(
          SqliteBookPassphraseSourceBytes.OversizedBookPassphraseSourceException.class,
          () -> SqliteBookPassphraseSourceBytes.read(inputStream));
      assertArrayEquals(
          new byte[ProtocolInteractionLimits.BOOK_PASSPHRASE_MAX_UTF8_BYTES + 1],
          inputStream.lastReadBuffer());
    }
  }

  /** Records the temporary read buffer so the test can prove oversize-path zeroization. */
  private static final class RecordingInputStream extends InputStream {
    private final byte[] sourceBytes;
    private int offset;
    private Supplier<byte[]> lastReadBufferReader = () -> new byte[0];

    private RecordingInputStream(byte[] sourceBytes) {
      this.sourceBytes = sourceBytes;
    }

    @Override
    public int read(byte[] buffer, int bufferOffset, int length) {
      lastReadBufferReader = () -> Arrays.copyOf(buffer, buffer.length);
      if (offset >= sourceBytes.length) {
        return -1;
      }
      int bytesToCopy = Math.min(length, sourceBytes.length - offset);
      System.arraycopy(sourceBytes, offset, buffer, bufferOffset, bytesToCopy);
      offset += bytesToCopy;
      return bytesToCopy;
    }

    @Override
    public int read() throws IOException {
      throw new UnsupportedOperationException("byte-wise reads are not used by this test");
    }

    private byte[] lastReadBuffer() {
      return lastReadBufferReader.get();
    }
  }
}
