package dev.erst.fingrind.core.attestation;

import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.PrivateOutputFile;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;

/** Shared filesystem and key-file fixtures for the encrypted attestation credential boundary. */
final class AttestationKeyFileTestSupport {
  private AttestationKeyFileTestSupport() {}

  static long directoryEntryCount(Path temporaryDirectory) throws IOException {
    try (Stream<Path> entries = Files.list(temporaryDirectory)) {
      return entries.count();
    }
  }

  static Path canonicalPublicationPath(Path path) throws IOException {
    Path normalizedPath = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    Path parent = Objects.requireNonNull(normalizedPath.getParent(), "key path parent");
    Path fileName = Objects.requireNonNull(normalizedPath.getFileName(), "key file name");
    return parent.toRealPath().resolve(fileName);
  }

  /** Resolves a JUnit-managed fixture directory before it reaches a lexical no-follow boundary. */
  static Path canonicalTemporaryDirectory(Path temporaryDirectory) throws IOException {
    return Objects.requireNonNull(temporaryDirectory, "temporaryDirectory").toRealPath();
  }

  static Path privateOwnerOnlyDirectory(Path temporaryDirectory, String name) throws IOException {
    return privateOwnerOnlyDirectory(
        Files.createDirectories(canonicalTemporaryDirectory(temporaryDirectory).resolve(name)));
  }

  static Path privateOwnerOnlyChildDirectory(Path parent, String name) throws IOException {
    return privateOwnerOnlyDirectory(
        Files.createDirectories(
            Objects.requireNonNull(parent, "parent").toRealPath().resolve(name)));
  }

  static Path privateOwnerOnlyDirectory(Path directory) throws IOException {
    Path canonicalDirectory = Objects.requireNonNull(directory, "directory").toRealPath();
    Assumptions.assumeTrue(
        canonicalDirectory.getFileSystem().supportedFileAttributeViews().contains("posix"),
        "POSIX permissions are unavailable on this filesystem.");
    Files.setPosixFilePermissions(
        canonicalDirectory,
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE));
    return canonicalDirectory;
  }

  static void writeOwnerOnlyFile(Path path, byte[] contents) throws IOException {
    Path target = canonicalPublicationPath(path);
    try (PrivateOutputFile.OpenedFile opened = PrivateOutputFile.createNew(target)) {
      ByteBuffer pending = ByteBuffer.wrap(Objects.requireNonNull(contents, "contents"));
      while (pending.hasRemaining()) {
        if (opened.write(pending) <= 0) {
          throw new IOException("The private test fixture could not write its complete content.");
        }
      }
      opened.force();
    }
  }

  static IllegalArgumentException signingFailure(Path keyPath) {
    return assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationFilePkcs8Custodian.sign(
                keyPath, "correct horse battery staple".toCharArray(), new byte[] {4, 5, 6}));
  }

  static boolean contains(byte[] values, byte[] candidate) {
    for (int offset = 0; offset <= values.length - candidate.length; offset++) {
      if (Arrays.mismatch(values, offset, offset + candidate.length, candidate, 0, candidate.length)
          < 0) {
        return true;
      }
    }
    return false;
  }
}
