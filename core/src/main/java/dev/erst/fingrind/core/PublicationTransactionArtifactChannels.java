package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Objects;

/** Exact byte-transfer operations for owner-only publication artifact channels. */
final class PublicationTransactionArtifactChannels {
  private PublicationTransactionArtifactChannels() {}

  static void writeExactly(WritableByteChannel channel, byte[] bytes) throws IOException {
    WritableByteChannel checkedChannel = Objects.requireNonNull(channel, "channel");
    ByteBuffer pending = ByteBuffer.wrap(bytes);
    while (pending.hasRemaining()) {
      if (checkedChannel.write(pending) <= 0) {
        throw new IOException("FinGrind could not write the complete transaction-owned stage.");
      }
    }
  }

  static void copyExactly(ReadableByteChannel source, WritableByteChannel destination)
      throws IOException {
    ReadableByteChannel checkedSource = Objects.requireNonNull(source, "source");
    WritableByteChannel checkedDestination = Objects.requireNonNull(destination, "destination");
    ByteBuffer buffer = ByteBuffer.allocate(16 * 1024);
    while (true) {
      int read = checkedSource.read(buffer);
      if (read < 0) {
        return;
      }
      if (read == 0) {
        throw new IOException("FinGrind could not read the complete transaction private source.");
      }
      buffer.flip();
      while (buffer.hasRemaining()) {
        if (checkedDestination.write(buffer) <= 0) {
          throw new IOException("FinGrind could not write the complete transaction-owned stage.");
        }
      }
      buffer.clear();
    }
  }
}
