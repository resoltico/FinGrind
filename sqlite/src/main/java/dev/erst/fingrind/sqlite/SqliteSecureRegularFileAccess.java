package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/** Nofollow external-read boundary and exact owner-only FinGrind-owned file capability. */
final class SqliteSecureRegularFileAccess {
  /**
   * Maximum accepted byte size for one untrusted FinGrind recovery metadata file.
   *
   * <p>One MiB covers the largest supported Windows long-path pair record: six independently
   * encoded absolute paths (two targets, two stages, and at most two binding sources), plus the
   * fixed attestation fields. The same bound is enforced before FinGrind writes a record.
   */
  static final int MAXIMUM_RECOVERY_METADATA_BYTES = 1_048_576;

  private SqliteSecureRegularFileAccess() {}

  /** Opens an existing regular file for read without resolving a final symlink. */
  static InputStream openRead(Path path) throws IOException {
    requireRegular(path);
    try {
      return Files.newInputStream(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
    } catch (UnsupportedOperationException unsupported) {
      throw unsupportedNoFollow(path, unsupported);
    }
  }

  /** Reads one complete owned stage through the same nofollow regular-file boundary. */
  static byte[] readAllBytes(Path path) throws IOException {
    try (InputStream input = openRead(path)) {
      return input.readAllBytes();
    }
  }

  /** Reads small recovery metadata through the nofollow boundary without unbounded allocation. */
  static byte[] readAllBytesBounded(Path path, int maximumBytes) throws IOException {
    int checkedMaximumBytes = positiveBound(maximumBytes, "maximumBytes");
    try (InputStream input = openRead(path)) {
      byte[] bytes = input.readNBytes(checkedMaximumBytes + 1);
      if (bytes.length > checkedMaximumBytes) {
        throw new IOException("FinGrind recovery metadata exceeds its maximum allowed size.");
      }
      return bytes;
    }
  }

  private static int positiveBound(int value, String name) {
    if (value <= 0 || value == Integer.MAX_VALUE) {
      throw new IllegalArgumentException(name + " must be a positive bounded value.");
    }
    return value;
  }

  private static void requireRegular(Path path) throws IOException {
    if (!Files.isRegularFile(Objects.requireNonNull(path, "path"), LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException("Expected one regular non-symlink file at " + path + ".");
    }
  }

  private static IOException unsupportedNoFollow(Path path, UnsupportedOperationException cause) {
    return new IOException(
        "The selected filesystem cannot enforce nofollow access for FinGrind-owned evidence at "
            + path
            + ".",
        cause);
  }
}
