package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.OverlappingFileLockException;
import java.util.Objects;

/** Performs exact retained-channel journal I/O without trusting a pathname after admission. */
final class PublicationTransactionJournalFileIO {
  private PublicationTransactionJournalFileIO() {}

  static PrivateOutputFile.HeldLock requireExclusiveLock(PrivateOutputFile.OpenedFile opened)
      throws IOException {
    PrivateOutputFile.HeldLock lock;
    try {
      lock = Objects.requireNonNull(opened, "opened").tryExclusiveLock(0L, 1L);
    } catch (OverlappingFileLockException exception) {
      throw new IOException(
          "Publication transaction journal is already held by this process.", exception);
    }
    if (lock == null) {
      throw new IOException("Publication transaction journal is already held by another process.");
    }
    return lock;
  }

  static void writeExactlyAndForce(
      PrivateOutputFile.OpenedFile opened, byte[] bytes, String artifactName) throws IOException {
    ByteBuffer source = ByteBuffer.wrap(Objects.requireNonNull(bytes, "bytes"));
    while (source.hasRemaining()) {
      if (opened.write(source) <= 0) {
        throw new IOException("Failed to write the complete " + artifactName + ".");
      }
    }
    opened.force();
  }

  static byte[] readAtMost(
      PrivateOutputFile.OpenedFile opened, int maximumBytes, String artifactName)
      throws IOException {
    long length = opened.size();
    if (length > maximumBytes) {
      throw new IOException("The " + artifactName + " has an unsupported byte length.");
    }
    ByteBuffer destination = ByteBuffer.allocate(Math.toIntExact(length));
    opened.position(0L);
    while (destination.hasRemaining()) {
      if (opened.read(destination) <= 0) {
        throw new IOException("Failed to read the complete " + artifactName + ".");
      }
    }
    return destination.array();
  }

  static byte[] readExactLength(
      PrivateOutputFile.OpenedFile opened, int expectedBytes, String artifactName)
      throws IOException {
    byte[] bytes = readAtMost(opened, expectedBytes, artifactName);
    if (bytes.length != expectedBytes) {
      throw new IOException("The " + artifactName + " has an unsupported byte length.");
    }
    return bytes;
  }

  static void closeAfterFailure(PrivateOutputFile.OpenedFile opened, IOException failure) {
    try {
      opened.close();
    } catch (IOException closeFailure) {
      failure.addSuppressed(closeFailure);
    }
  }
}
