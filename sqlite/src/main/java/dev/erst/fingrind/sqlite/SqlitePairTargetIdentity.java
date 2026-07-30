package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;

/** Establishes whether two final protected-book pair paths can name one filesystem object. */
final class SqlitePairTargetIdentity {
  private SqlitePairTargetIdentity() {}

  /**
   * Returns whether the two final paths resolve to one filesystem identity.
   *
   * <p>For distinct absent leaves in one physical parent, a conservative alias key rejects
   * spellings that can collide under Unicode normalization or case-insensitive filename lookup. It
   * does not impose an unrelated character grammar on independently named artifacts.
   */
  static boolean sameFinalTargetIdentity(Path bookTargetPath, Path secretTargetPath) {
    Path checkedBookTarget = Objects.requireNonNull(bookTargetPath, "bookTargetPath");
    Path checkedSecretTarget = Objects.requireNonNull(secretTargetPath, "secretTargetPath");
    boolean bookExists = existsForIdentity(checkedBookTarget);
    boolean secretExists = existsForIdentity(checkedSecretTarget);
    if (bookExists && secretExists) {
      try {
        return Files.isSameFile(checkedBookTarget, checkedSecretTarget);
      } catch (IOException | RuntimeException failure) {
        throw identityUnestablished(checkedBookTarget, failure);
      }
    }
    if (bookExists || secretExists) {
      return false;
    }
    Path bookParent =
        Objects.requireNonNull(checkedBookTarget.getParent(), "bookTargetPath parent");
    Path secretParent =
        Objects.requireNonNull(checkedSecretTarget.getParent(), "secretTargetPath parent");
    boolean sameParent;
    try {
      sameParent = Files.isSameFile(bookParent, secretParent);
    } catch (IOException | RuntimeException failure) {
      throw identityUnestablished(checkedBookTarget, failure);
    }
    if (!sameParent) {
      return false;
    }
    String bookLeaf =
        Objects.requireNonNull(checkedBookTarget.getFileName(), "bookTargetPath fileName")
            .toString();
    String secretLeaf =
        Objects.requireNonNull(checkedSecretTarget.getFileName(), "secretTargetPath fileName")
            .toString();
    return bookLeaf.equals(secretLeaf)
        || conservativeAliasKey(bookLeaf).equals(conservativeAliasKey(secretLeaf));
  }

  private static boolean existsNoFollow(Path path) throws IOException {
    try {
      Files.readAttributes(
          Objects.requireNonNull(path, "path"),
          BasicFileAttributes.class,
          LinkOption.NOFOLLOW_LINKS);
      return true;
    } catch (NoSuchFileException absent) {
      return false;
    }
  }

  /** Reads one final-target existence fact without allowing an unknown identity to continue. */
  private static boolean existsForIdentity(Path targetPath) {
    try {
      return existsNoFollow(targetPath);
    } catch (IOException | RuntimeException failure) {
      throw identityUnestablished(targetPath, failure);
    }
  }

  private static String conservativeAliasKey(String leaf) {
    return Normalizer.normalize(Objects.requireNonNull(leaf, "leaf"), Normalizer.Form.NFD)
        .toUpperCase(Locale.ROOT);
  }

  private static SqliteCallerPathContractException identityUnestablished(
      Path targetPath, Throwable cause) {
    return new SqliteCallerPathContractException(
        Objects.requireNonNull(targetPath, "targetPath"),
        SqliteCallerPathFailure.TARGET_IDENTITY_UNESTABLISHED,
        "FinGrind could not establish whether the protected-book and generated-secret targets name distinct filesystem objects: "
            + SqliteMachinePaths.absoluteValue(targetPath),
        Objects.requireNonNull(cause, "cause"));
  }
}
