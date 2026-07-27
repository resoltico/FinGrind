package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.CryptographicPrimitives;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/** Owns immutable pair-publication member validation and SHA-256 comparisons. */
final class SqlitePairPublicationRecordIntegrity {
  private static final int SHA_256_BYTES = 32;

  private SqlitePairPublicationRecordIntegrity() {}

  static byte[] digestRegularFile(Path path, String name) throws IOException {
    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException("The " + name + " is not an intact regular file.");
    }
    try (InputStream input = SqliteSecureRegularFileAccess.openRead(path)) {
      return digest(input, name);
    }
  }

  static byte[] digest(InputStream input, String name) throws IOException {
    Objects.requireNonNull(input, "input");
    String checkedName = Objects.requireNonNull(name, "name");
    try {
      return CryptographicPrimitives.sha256(input);
    } catch (IOException exception) {
      if ("Cryptographic digest input did not make read progress.".equals(exception.getMessage())) {
        throw new IOException("The " + checkedName + " did not make read progress.", exception);
      }
      throw exception;
    }
  }

  static Path normalized(Path path, String name) {
    return Objects.requireNonNull(path, name).toAbsolutePath().normalize();
  }

  static Path parentOf(Path path) {
    return Objects.requireNonNull(
        Objects.requireNonNull(path, "path").getParent(), "protected-book pair path parent");
  }

  static byte[] checkedDigest(byte[] digest, String name) {
    byte[] copy = Objects.requireNonNull(digest, name).clone();
    if (copy.length != SHA_256_BYTES) {
      throw new IllegalArgumentException(name + " must contain exactly one SHA-256 digest.");
    }
    return copy;
  }

  static void requireAbsent(byte @org.jspecify.annotations.Nullable [] value) {
    if (value != null) {
      throw new IllegalArgumentException(
          "Only a replacing protected-book target may retain pre-publication bytes.");
    }
  }

  static boolean regularFileMatches(Path path, byte[] expectedDigest) {
    try {
      return CryptographicPrimitives.constantTimeEquals(
          digestRegularFile(path, "pair-publication artifact"), expectedDigest);
    } catch (IOException exception) {
      return false;
    }
  }

  static void validateDistinctMembers(
      Path bookTargetPath, Path secretTargetPath, Path bookStagePath, Path secretStagePath) {
    if (SqliteProtectedBookPathIdentity.sameNormalizedSpelling(bookTargetPath, secretTargetPath)) {
      throw new IllegalArgumentException("Protected-book pair targets must be distinct.");
    }
    if (SqliteProtectedBookPathIdentity.sameNormalizedSpelling(bookStagePath, bookTargetPath)
        || SqliteProtectedBookPathIdentity.sameNormalizedSpelling(bookStagePath, secretTargetPath)
        || SqliteProtectedBookPathIdentity.sameNormalizedSpelling(secretStagePath, bookTargetPath)
        || SqliteProtectedBookPathIdentity.sameNormalizedSpelling(secretStagePath, secretTargetPath)
        || SqliteProtectedBookPathIdentity.sameNormalizedSpelling(bookStagePath, secretStagePath)) {
      throw new IllegalArgumentException(
          "Protected-book pair recovery stages must be distinct from both final members and each other.");
    }
    if (!SqliteProtectedBookPathIdentity.sameExistingFilesystemObject(
            parentOf(bookTargetPath), parentOf(bookStagePath), bookTargetPath)
        || !SqliteProtectedBookPathIdentity.sameExistingFilesystemObject(
            parentOf(secretTargetPath), parentOf(secretStagePath), secretTargetPath)) {
      throw new IllegalArgumentException(
          "Protected-book pair recovery stages must remain beside their canonical final targets.");
    }
  }
}
