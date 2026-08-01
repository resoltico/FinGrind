package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Owns directory-stream traversal so I/O failures retain Java's primary-failure semantics. */
final class SqliteDirectoryStreams {
  private SqliteDirectoryStreams() {}

  /** Opens exactly one directory stream for a caller-supplied directory. */
  @FunctionalInterface
  interface DirectoryOpener {
    /** Opens the selected directory without transferring its closure responsibility. */
    DirectoryStream<Path> open(Path directory) throws IOException;
  }

  /**
   * Reads one open directory stream while {@link SqliteDirectoryStreams} retains closure ownership.
   */
  @FunctionalInterface
  interface DirectoryReader<T> {
    /** Reads the supplied open stream before its owner closes it. */
    T read(DirectoryStream<Path> entries) throws IOException;
  }

  static <T> T read(Path directory, DirectoryReader<T> reader) throws IOException {
    return read(Files::newDirectoryStream, directory, reader);
  }

  static <T> T read(DirectoryOpener opener, Path directory, DirectoryReader<T> reader)
      throws IOException {
    DirectoryOpener checkedOpener = Objects.requireNonNull(opener, "opener");
    Path checkedDirectory = Objects.requireNonNull(directory, "directory");
    DirectoryReader<T> checkedReader = Objects.requireNonNull(reader, "reader");
    try (DirectoryStream<Path> entries = checkedOpener.open(checkedDirectory)) {
      return checkedReader.read(entries);
    }
  }
}
