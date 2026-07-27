package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.regex.Pattern;

/** Establishes whether two final protected-book pair paths can name one filesystem object. */
final class SqlitePairTargetIdentity {
  private static final Pattern PORTABLE_LOWERCASE_LEAF =
      Pattern.compile("[a-z0-9](?:[a-z0-9_-]|\\.(?=[a-z0-9]))*");
  private static final Pattern WINDOWS_DEVICE_STEM =
      Pattern.compile("(?:con|prn|aux|nul|com[1-9]|lpt[1-9])");

  private SqlitePairTargetIdentity() {}

  /**
   * Returns whether the two final paths resolve to one filesystem identity.
   *
   * <p>Distinct absent leaves in one physical parent must use the portable lowercase-ASCII leaf
   * grammar, so FinGrind never infers case or normalization behavior from another probe name.
   */
  static boolean sameFinalTargetIdentity(Path bookTargetPath, Path secretTargetPath) {
    Path checkedBookTarget = Objects.requireNonNull(bookTargetPath, "bookTargetPath");
    Path checkedSecretTarget = Objects.requireNonNull(secretTargetPath, "secretTargetPath");
    boolean bookExists;
    boolean secretExists;
    try {
      bookExists = existsNoFollow(checkedBookTarget);
      secretExists = existsNoFollow(checkedSecretTarget);
    } catch (IOException | RuntimeException failure) {
      throw identityUnestablished(checkedBookTarget, failure);
    }
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
    if (bookLeaf.equals(secretLeaf)) {
      return true;
    }
    requirePortableDistinctLeaf(checkedBookTarget, bookLeaf);
    requirePortableDistinctLeaf(checkedSecretTarget, secretLeaf);
    return false;
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

  private static void requirePortableDistinctLeaf(Path targetPath, String leaf) {
    String checkedLeaf = Objects.requireNonNull(leaf, "leaf");
    if (!PORTABLE_LOWERCASE_LEAF.matcher(checkedLeaf).matches()) {
      throw portabilityRequired(targetPath);
    }
    int extensionStart = checkedLeaf.indexOf('.');
    String stem = extensionStart < 0 ? checkedLeaf : checkedLeaf.substring(0, extensionStart);
    if (WINDOWS_DEVICE_STEM.matcher(stem).matches()) {
      throw portabilityRequired(targetPath);
    }
  }

  private static SqliteCallerPathContractException portabilityRequired(Path targetPath) {
    return new SqliteCallerPathContractException(
        Objects.requireNonNull(targetPath, "targetPath"),
        SqliteCallerPathFailure.PAIR_TARGET_LEAF_PORTABILITY_REQUIRED,
        "Distinct absent protected-book pair targets in one physical parent require portable lowercase ASCII leaf names.");
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
