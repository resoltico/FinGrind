package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.PrivateOutputFile;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Exact owner-only access capability for existing and newly created FinGrind-owned files. */
final class SqliteOwnedRegularFileAccess {
  private SqliteOwnedRegularFileAccess() {}

  /** Opens one existing FinGrind-owned file through its retained exact owner-only channel. */
  static InputStream openOwnedRead(Path path) throws IOException {
    return openOwnedRead(
        path, SqliteOwnedRegularFileAccess::openExistingOwned, Channels::newInputStream);
  }

  static InputStream openOwnedRead(
      Path path, OwnedFileOpener opener, OwnedInputStreamFactory inputStreamFactory)
      throws IOException {
    PrivateOutputFile.OpenedFile opened =
        Objects.requireNonNull(opener, "opener")
            .open(Objects.requireNonNull(path, "path"), PrivateOutputFile.Access.READ_ONLY);
    try {
      return Objects.requireNonNull(inputStreamFactory, "inputStreamFactory").open(opened);
    } catch (RuntimeException | Error failure) {
      try {
        opened.close();
      } catch (IOException closeFailure) {
        failure.addSuppressed(closeFailure);
      }
      throw failure;
    }
  }

  /** Opens one existing FinGrind-owned file for force-only write through its exact channel. */
  static PrivateOutputFile.OpenedFile openWrite(Path path) throws IOException {
    return openExistingOwned(path, PrivateOutputFile.Access.READ_WRITE);
  }

  /** Opens one existing FinGrind-owned file for truncating write through its exact channel. */
  static PrivateOutputFile.OpenedFile openTruncatingWrite(Path path) throws IOException {
    return openTruncatingWrite(path, SqliteOwnedRegularFileAccess::openExistingOwned);
  }

  static PrivateOutputFile.OpenedFile openTruncatingWrite(Path path, OwnedFileOpener opener)
      throws IOException {
    PrivateOutputFile.OpenedFile opened =
        Objects.requireNonNull(opener, "opener")
            .open(Objects.requireNonNull(path, "path"), PrivateOutputFile.Access.READ_WRITE);
    try {
      opened.truncate(0L);
      opened.position(0L);
      return opened;
    } catch (IOException | RuntimeException | Error failure) {
      try {
        opened.close();
      } catch (IOException closeFailure) {
        failure.addSuppressed(closeFailure);
      }
      throw failure;
    }
  }

  /**
   * Opens one brand-new FinGrind-owned file on the exact {@code CREATE_NEW} channel.
   *
   * <p>The core capability creates POSIX files with {@code 0600} or Windows files with the
   * protected owner-only descriptor in the creation call, never by repairing inherited access.
   */
  static PrivateOutputFile.OpenedFile openNewWrite(Path path) throws IOException {
    Path checkedPath = Objects.requireNonNull(path, "path");
    try {
      return PrivateOutputFile.createNew(checkedPath);
    } catch (PrivateOutputFile.OwnerOnlyFileViolation violation) {
      throw SqlitePrivateOutputFileFailures.map(checkedPath, violation);
    }
  }

  /** Creates one empty owned stage through the same exact owner-only creation boundary. */
  static void createNewEmptyFile(Path path) throws IOException {
    createNewEmptyFile(path, SqliteOwnedRegularFileAccess::openNewWrite);
  }

  static void createNewEmptyFile(Path path, OwnedFileCreator creator) throws IOException {
    try (PrivateOutputFile.OpenedFile channel =
        Objects.requireNonNull(creator, "creator").create(Objects.requireNonNull(path, "path"))) {
      if (channel.size() != 0L) {
        throw new IOException("A newly created FinGrind-owned stage was not empty.");
      }
    }
  }

  /** Reads bounded owned recovery metadata through the exact owner-only channel. */
  static List<String> readOwnedUtf8LinesBounded(Path path, int maximumBytes, int maximumLines)
      throws IOException {
    int checkedMaximumLines = positiveBound(maximumLines, "maximumLines");
    return decodeUtf8LinesBounded(
        readOwnedAllBytesBounded(path, maximumBytes), checkedMaximumLines);
  }

  /** Reads one complete FinGrind-owned stage through its exact owner-only channel. */
  static byte[] readOwnedAllBytes(Path path) throws IOException {
    try (InputStream input = openOwnedRead(path)) {
      return input.readAllBytes();
    }
  }

  /** Reads bounded FinGrind-owned metadata through its exact owner-only channel. */
  static byte[] readOwnedAllBytesBounded(Path path, int maximumBytes) throws IOException {
    int checkedMaximumBytes = positiveBound(maximumBytes, "maximumBytes");
    try (InputStream input = openOwnedRead(path)) {
      byte[] bytes = input.readNBytes(checkedMaximumBytes + 1);
      if (bytes.length > checkedMaximumBytes) {
        throw new IOException("FinGrind recovery metadata exceeds its maximum allowed size.");
      }
      return bytes;
    }
  }

  /** Force-confirms one existing FinGrind-owned file before its parent directory is forced. */
  static void forceFile(Path path) throws IOException {
    try (PrivateOutputFile.OpenedFile channel = openWrite(path)) {
      channel.force();
    }
  }

  private static List<String> decodeUtf8LinesBounded(byte[] bytes, int maximumLines)
      throws IOException {
    String content = new String(Objects.requireNonNull(bytes, "bytes"), StandardCharsets.UTF_8);
    List<String> lines = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
      String line = reader.readLine();
      while (line != null) {
        if (lines.size() == maximumLines) {
          throw new IOException("FinGrind recovery metadata has too many lines.");
        }
        lines.add(line);
        line = reader.readLine();
      }
    }
    return List.copyOf(lines);
  }

  private static PrivateOutputFile.OpenedFile openExistingOwned(
      Path path, PrivateOutputFile.Access access) throws IOException {
    Path checkedPath = Objects.requireNonNull(path, "path");
    try {
      return PrivateOutputFile.openExisting(checkedPath, access);
    } catch (PrivateOutputFile.OwnerOnlyFileViolation violation) {
      throw SqlitePrivateOutputFileFailures.map(checkedPath, violation);
    }
  }

  private static int positiveBound(int value, String name) {
    if (value <= 0 || value == Integer.MAX_VALUE) {
      throw new IllegalArgumentException(name + " must be a positive bounded value.");
    }
    return value;
  }

  /** Creates one exact owner-only file. */
  @FunctionalInterface
  interface OwnedFileCreator {
    /** Creates the supplied path as one exact owner-only file. */
    PrivateOutputFile.OpenedFile create(Path path) throws IOException;
  }

  /** Opens one existing exact owner-only file. */
  @FunctionalInterface
  interface OwnedFileOpener {
    /** Opens the supplied file with the requested exact access. */
    PrivateOutputFile.OpenedFile open(Path path, PrivateOutputFile.Access access)
        throws IOException;
  }

  /** Creates an input stream that owns the supplied exact opened file. */
  @FunctionalInterface
  interface OwnedInputStreamFactory {
    /** Opens one input stream over the supplied exact opened file. */
    InputStream open(PrivateOutputFile.OpenedFile opened) throws IOException;
  }
}
