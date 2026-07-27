package dev.erst.fingrind.sqlite;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Nofollow file-open boundary for FinGrind-owned stages and recovery evidence. */
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

  /** Opens an existing regular file for force-only write access without resolving a symlink. */
  static FileChannel openWrite(Path path) throws IOException {
    return openExisting(path, StandardOpenOption.WRITE);
  }

  /** Opens an existing regular file for truncating write access without resolving a symlink. */
  static FileChannel openTruncatingWrite(Path path) throws IOException {
    return openExisting(path, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
  }

  /**
   * Opens one brand-new FinGrind-owned file on the exact {@code CREATE_NEW} channel.
   *
   * <p>The protocol requires atomic POSIX {@code 0600} creation. An ACL-only filesystem cannot
   * express that guarantee through portable Java NIO, so it fails closed instead of creating a
   * readable file and repairing its access control list afterwards.
   */
  static FileChannel openNewWrite(Path path) throws IOException {
    return SqliteCoordinationControlFiles.openNewOwnerOnlyProtocolFile(
        Objects.requireNonNull(path, "path"));
  }

  /** Creates one empty owned stage through the same exact owner-only creation boundary. */
  static void createNewEmptyFile(Path path) throws IOException {
    try (FileChannel channel = openNewWrite(path)) {
      if (channel.size() != 0L) {
        throw new IOException("A newly created FinGrind-owned stage was not empty.");
      }
    }
  }

  /** Reads an owned metadata record through the nofollow boundary. */
  static List<String> readUtf8Lines(Path path) throws IOException {
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(openRead(path), StandardCharsets.UTF_8))) {
      List<String> lines = new ArrayList<>();
      String line = reader.readLine();
      while (line != null) {
        lines.add(line);
        line = reader.readLine();
      }
      return List.copyOf(lines);
    }
  }

  /**
   * Reads small recovery metadata through the nofollow boundary with fixed byte and line bounds.
   *
   * <p>The byte bound is enforced while reading rather than inferred from a pre-open stat, so an
   * untrusted file that grows after validation cannot make recovery admission consume unbounded
   * memory.
   */
  static List<String> readUtf8LinesBounded(Path path, int maximumBytes, int maximumLines)
      throws IOException {
    int checkedMaximumLines = positiveBound(maximumLines, "maximumLines");
    String content = new String(readAllBytesBounded(path, maximumBytes), StandardCharsets.UTF_8);
    List<String> lines = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
      String line = reader.readLine();
      while (line != null) {
        if (lines.size() == checkedMaximumLines) {
          throw new IOException("FinGrind recovery metadata has too many lines.");
        }
        lines.add(line);
        line = reader.readLine();
      }
    }
    return List.copyOf(lines);
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

  /** Force-confirms one existing nofollow regular file before its parent directory is forced. */
  static void forceFile(Path path) throws IOException {
    try (FileChannel channel = openWrite(path)) {
      channel.force(true);
    }
  }

  private static FileChannel openExisting(Path path, OpenOption... options) throws IOException {
    Path checkedPath = Objects.requireNonNull(path, "path");
    requireRegular(checkedPath);
    OpenOption[] checkedOptions = new OpenOption[options.length + 1];
    System.arraycopy(options, 0, checkedOptions, 0, options.length);
    checkedOptions[options.length] = LinkOption.NOFOLLOW_LINKS;
    try {
      return FileChannel.open(checkedPath, checkedOptions);
    } catch (UnsupportedOperationException unsupported) {
      throw unsupportedNoFollow(checkedPath, unsupported);
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
