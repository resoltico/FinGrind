package dev.erst.fingrind.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * Opens a caller-selected file through one retained descriptor without resolving its final link.
 */
final class CliNofollowFileInput {
  private CliNofollowFileInput() {}

  /** Opens the supplied final path through a descriptor-bound nofollow read. */
  static InputStream open(Path path) throws IOException {
    return open(path, CliNofollowFileInput::openDescriptorBound);
  }

  /** Reads at most {@code maximumBytes} bytes through one descriptor-bound nofollow read. */
  static byte[] readBounded(Path path, int maximumBytes) throws IOException {
    if (maximumBytes < 0 || maximumBytes == Integer.MAX_VALUE) {
      throw new IllegalArgumentException("maximumBytes must be a non-negative bounded value.");
    }
    try (InputStream input = openRegular(path)) {
      byte[] bytes = input.readNBytes(maximumBytes + 1);
      if (bytes.length > maximumBytes) {
        throw new FileTooLargeException(maximumBytes);
      }
      return bytes;
    }
  }

  /**
   * Opens one regular non-symlink final component through a descriptor-bound nofollow read.
   *
   * <p>The initial type admission rejects known non-regular paths. The actual read is opened
   * separately with {@link LinkOption#NOFOLLOW_LINKS}, so replacing the final path with an alias
   * after admission fails instead of changing the bytes this caller reads.
   */
  static InputStream openRegular(Path path) throws IOException {
    Path checkedPath = Objects.requireNonNull(path, "path");
    if (Files.notExists(checkedPath, LinkOption.NOFOLLOW_LINKS)) {
      throw new NoSuchFileException(checkedPath.toString());
    }
    if (!Files.isRegularFile(checkedPath, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException("Expected one regular non-symlink file at " + checkedPath + ".");
    }
    return open(checkedPath);
  }

  /** Same-package seam for testing nofollow-provider failure translation. */
  static InputStream open(Path path, InputStreamOpener inputStreamOpener) throws IOException {
    Path checkedPath = Objects.requireNonNull(path, "path");
    try {
      return Objects.requireNonNull(inputStreamOpener, "inputStreamOpener").open(checkedPath);
    } catch (UnsupportedOperationException | IllegalArgumentException unsupported) {
      throw new IOException(
          "The selected filesystem cannot enforce nofollow file access at " + checkedPath + ".",
          unsupported);
    }
  }

  private static InputStream openDescriptorBound(Path path) throws IOException {
    return Channels.newInputStream(
        FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
  }

  /** Opens a file through the caller's descriptor-bound policy. */
  @FunctionalInterface
  interface InputStreamOpener {
    /** Opens the supplied path through the caller's descriptor-bound policy. */
    InputStream open(Path path) throws IOException;
  }

  /** Signals that a descriptor-bound input exceeded its caller-supplied byte bound. */
  static final class FileTooLargeException extends IOException {
    private static final long serialVersionUID = 1L;

    FileTooLargeException(int maximumBytes) {
      super(
          "The selected file exceeds its maximum supported byte length of "
              + maximumBytes
              + " bytes.");
    }
  }
}
