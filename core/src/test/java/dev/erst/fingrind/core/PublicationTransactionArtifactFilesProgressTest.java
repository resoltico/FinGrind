package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** Verifies private-source staging fails closed when either exact channel stops making progress. */
class PublicationTransactionArtifactFilesProgressTest {
  @Test
  void rejectsPrivateSourcesThatCannotMakeReadProgress() throws IOException {
    try (ReadableByteChannel stalledSource = readChannel(destination -> 0);
        WritableByteChannel destination =
            writeChannel(
                source -> {
                  throw new AssertionError("A stalled source must not write a stage.");
                })) {
      IOException failure =
          assertThrows(
              IOException.class,
              () -> PublicationTransactionArtifactFiles.copyExactly(stalledSource, destination));

      assertEquals(
          "FinGrind could not read the complete transaction private source.", failure.getMessage());
    }
  }

  @Test
  void rejectsStagesThatCannotMakeWriteProgress() throws IOException {
    AtomicBoolean unread = new AtomicBoolean(true);
    try (ReadableByteChannel source =
            readChannel(
                destination -> {
                  if (!unread.compareAndSet(true, false)) {
                    return -1;
                  }
                  destination.put((byte) 1);
                  return 1;
                });
        WritableByteChannel stalledDestination = writeChannel(ignored -> 0)) {
      IOException failure =
          assertThrows(
              IOException.class,
              () -> PublicationTransactionArtifactFiles.copyExactly(source, stalledDestination));

      assertEquals(
          "FinGrind could not write the complete transaction-owned stage.", failure.getMessage());
    }
  }

  private static ReadableByteChannel readChannel(ReadOperation operation) {
    return new ReadableByteChannel() {
      @Override
      public int read(ByteBuffer destination) throws IOException {
        return operation.read(destination);
      }

      @Override
      public boolean isOpen() {
        return true;
      }

      @Override
      public void close() {}
    };
  }

  private static WritableByteChannel writeChannel(WriteOperation operation) {
    return new WritableByteChannel() {
      @Override
      public int write(ByteBuffer source) throws IOException {
        return operation.write(source);
      }

      @Override
      public boolean isOpen() {
        return true;
      }

      @Override
      public void close() {}
    };
  }

  /** Supplies deterministic test reads from one exact source channel. */
  @FunctionalInterface
  private interface ReadOperation {
    int read(ByteBuffer destination) throws IOException;
  }

  /** Supplies deterministic test writes through one exact destination channel. */
  @FunctionalInterface
  private interface WriteOperation {
    int write(ByteBuffer source) throws IOException;
  }
}
