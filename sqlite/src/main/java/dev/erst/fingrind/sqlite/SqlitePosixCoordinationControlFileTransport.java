package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Retains the exact POSIX file-channel lock that implements one coordination protocol claim. */
final class SqlitePosixCoordinationControlFileTransport {
  private SqlitePosixCoordinationControlFileTransport() {}

  static SqliteCoordinationControlFiles.@Nullable LockedControlFile openOrCreateAndTryExclusiveLock(
      Path controlPath, byte[] magic, long position, long size) throws IOException {
    FileChannel channel = openOrCreate(controlPath, magic);
    return tryLockAndRetain(controlPath, channel, position, size);
  }

  static SqliteCoordinationControlFiles.@Nullable LockedControlFile openExistingAndTryExclusiveLock(
      Path controlPath, byte[] magic, long position, long size) throws IOException {
    FileChannel channel = openExisting(controlPath, magic);
    return tryLockAndRetain(controlPath, channel, position, size);
  }

  static void createAtomicallySecureRecord(Path recordPath, byte[] magic) throws IOException {
    try (FileChannel channel =
        SqlitePosixCoordinationFileSecurity.openNewOwnerOnlyProtocolFile(recordPath)) {
      if (channel.size() != 0L) {
        throw new IOException("A newly created FinGrind coordination record was not empty.");
      }
      writeExact(channel, magic);
    }
  }

  static void requireExistingExactRecord(Path recordPath, byte[] magic) throws IOException {
    try (FileChannel ignored =
        SqlitePosixCoordinationFileSecurity.openExistingSecureControlFile(recordPath)) {
      requireExactMagic(ignored, magic);
    }
  }

  private static SqliteCoordinationControlFiles.@Nullable LockedControlFile tryLockAndRetain(
      Path controlPath, FileChannel channel, long position, long size) throws IOException {
    try {
      @Nullable FileLock lock = tryExclusiveLock(channel, position, size);
      if (lock == null) {
        channel.close();
        return null;
      }
      return SqliteCoordinationControlFiles.lockedControlFile(
          controlPath, () -> releaseLockAndChannel(lock, channel));
    } catch (IOException | RuntimeException failure) {
      try {
        channel.close();
      } catch (IOException closeFailure) {
        failure.addSuppressed(closeFailure);
      }
      throw failure;
    }
  }

  private static FileChannel openOrCreate(Path controlPath, byte[] magic) throws IOException {
    Path checkedPath = Objects.requireNonNull(controlPath, "controlPath");
    try {
      FileChannel channel =
          SqlitePosixCoordinationFileSecurity.openNewOwnerOnlyProtocolFile(checkedPath);
      try {
        initializeNewControl(channel, magic);
        return channel;
      } catch (IOException | RuntimeException failure) {
        try {
          channel.close();
        } catch (IOException closeFailure) {
          failure.addSuppressed(closeFailure);
        }
        throw failure;
      }
    } catch (FileAlreadyExistsException collision) {
      return openExisting(checkedPath, magic);
    }
  }

  private static FileChannel openExisting(Path controlPath, byte[] magic) throws IOException {
    FileChannel channel =
        SqlitePosixCoordinationFileSecurity.openExistingSecureControlFile(controlPath);
    try {
      requireExactMagic(channel, magic);
      return channel;
    } catch (IOException | RuntimeException failure) {
      try {
        channel.close();
      } catch (IOException closeFailure) {
        failure.addSuppressed(closeFailure);
      }
      throw failure;
    }
  }

  private static @Nullable FileLock tryExclusiveLock(FileChannel channel, long position, long size)
      throws IOException {
    try {
      return Objects.requireNonNull(channel, "channel").tryLock(position, size, false);
    } catch (OverlappingFileLockException overlap) {
      return null;
    }
  }

  private static void releaseLockAndChannel(FileLock lock, FileChannel channel) throws IOException {
    IOException failure = null;
    try {
      lock.release();
    } catch (IOException exception) {
      failure = exception;
    }
    try {
      channel.close();
    } catch (IOException exception) {
      if (failure == null) {
        failure = exception;
      } else {
        failure.addSuppressed(exception);
      }
    }
    if (failure != null) {
      throw failure;
    }
  }

  private static void initializeNewControl(FileChannel channel, byte[] magic) throws IOException {
    if (channel.size() != 0L) {
      throw new IOException("A newly created FinGrind coordination control file was not empty.");
    }
    writeExact(channel, magic);
  }

  private static void writeExact(FileChannel channel, byte[] magic) throws IOException {
    ByteBuffer bytes = ByteBuffer.wrap(magic);
    while (bytes.hasRemaining()) {
      if (channel.write(bytes) <= 0) {
        throw new IOException(
            "Failed to write the complete FinGrind coordination control-file magic.");
      }
    }
    channel.force(true);
    requireExactMagic(channel, magic);
  }

  private static void requireExactMagic(FileChannel channel, byte[] magic) throws IOException {
    if (channel.size() != magic.length) {
      throw new IOException("FinGrind coordination control-file magic has an unexpected size.");
    }
    ByteBuffer actual = ByteBuffer.allocate(magic.length);
    channel.position(0L);
    while (actual.hasRemaining()) {
      int read = channel.read(actual);
      if (read < 0) {
        throw new IOException("FinGrind coordination control-file magic ended unexpectedly.");
      }
      if (read == 0) {
        throw new IOException(
            "FinGrind coordination control-file magic did not make read progress.");
      }
    }
    if (!Arrays.equals(actual.array(), magic)) {
      throw new IOException("FinGrind coordination control-file magic is invalid.");
    }
  }
}
