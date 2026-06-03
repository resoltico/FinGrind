package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.protocol.ProtocolInteractionLimits;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;

/** Reads one bounded UTF-8 passphrase-source payload while zeroizing the temporary read buffer. */
public final class SqliteBookPassphraseSourceBytes {
  private SqliteBookPassphraseSourceBytes() {}

  /** Reads one bounded UTF-8 passphrase-source payload. */
  public static byte[] read(InputStream inputStream) throws IOException {
    Objects.requireNonNull(inputStream, "inputStream");
    byte[] buffer = new byte[ProtocolInteractionLimits.BOOK_PASSPHRASE_MAX_UTF8_BYTES + 1];
    try {
      int bytesRead = readBoundedBytes(inputStream, buffer);
      if (bytesRead <= ProtocolInteractionLimits.BOOK_PASSPHRASE_MAX_UTF8_BYTES) {
        return Arrays.copyOf(buffer, bytesRead);
      }
      throw new OversizedBookPassphraseSourceException();
    } finally {
      Arrays.fill(buffer, (byte) 0);
    }
  }

  private static int readBoundedBytes(InputStream inputStream, byte[] buffer) throws IOException {
    int totalRead = 0;
    while (totalRead < buffer.length) {
      int bytesRead = inputStream.read(buffer, totalRead, buffer.length - totalRead);
      if (bytesRead < 0) {
        break;
      }
      if (bytesRead == 0) {
        continue;
      }
      totalRead += bytesRead;
    }
    return totalRead;
  }

  /** Signals that one passphrase-source payload exceeded FinGrind's byte limit. */
  public static final class OversizedBookPassphraseSourceException extends IOException {
    private static final long serialVersionUID = 1L;
  }
}
