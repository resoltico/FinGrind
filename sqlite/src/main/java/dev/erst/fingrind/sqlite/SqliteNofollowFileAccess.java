package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;

/** Descriptor-bound nofollow access to one regular managed SQLite artifact. */
final class SqliteNofollowFileAccess {
  private SqliteNofollowFileAccess() {}

  /** Requires that the final component is one readable, regular, non-symlink file. */
  static void requireReadableRegularFile(Path path) throws IOException {
    // The retained descriptor, not the preflight pathname, is the read admission boundary.
    openRegularInput(path).close();
  }

  /**
   * Opens one regular non-symlink final component through a descriptor-bound nofollow read.
   *
   * <p>The initial type admission rejects known non-regular paths. The actual read is opened
   * separately with {@link LinkOption#NOFOLLOW_LINKS}, so replacing the final path with an alias
   * after admission fails instead of changing the bytes this caller reads.
   */
  static InputStream openRegularInput(Path path) throws IOException {
    Path checkedPath = Objects.requireNonNull(path, "path");
    if (!Files.isRegularFile(checkedPath, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException(
          "Expected one regular non-symlink managed SQLite file at " + checkedPath + ".");
    }
    return openInput(checkedPath);
  }

  /** Opens the final component through a retained descriptor that does not resolve a symlink. */
  static InputStream openInput(Path path) throws IOException {
    return openInput(path, SqliteNofollowFileAccess::openDescriptorBound);
  }

  /** Same-package seam for nofollow-provider failure translation tests. */
  static InputStream openInput(Path path, InputStreamOpener inputStreamOpener) throws IOException {
    Path checkedPath = Objects.requireNonNull(path, "path");
    try {
      return Objects.requireNonNull(inputStreamOpener, "inputStreamOpener").open(checkedPath);
    } catch (UnsupportedOperationException | IllegalArgumentException unsupported) {
      throw new IOException(
          "The selected filesystem cannot enforce nofollow managed SQLite library access at "
              + checkedPath
              + ".",
          unsupported);
    }
  }

  /** Reads at most the supplied byte count of UTF-8 text through the retained nofollow stream. */
  static List<String> readUtf8LinesBounded(Path path, int maximumBytes) throws IOException {
    try (InputStream inputStream = openRegularInput(path)) {
      byte[] bytes = inputStream.readNBytes(maximumBytes + 1);
      if (bytes.length > maximumBytes) {
        throw new IOException("Managed SQLite checksum file exceeds its maximum supported size.");
      }
      return new String(bytes, StandardCharsets.UTF_8).lines().toList();
    }
  }

  private static InputStream openDescriptorBound(Path path) throws IOException {
    return Channels.newInputStream(
        FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
  }

  /** Opens one descriptor-bound input stream for the supplied path. */
  @FunctionalInterface
  interface InputStreamOpener {
    /** Opens the supplied path through the caller's descriptor-bound policy. */
    InputStream open(Path path) throws IOException;
  }
}
