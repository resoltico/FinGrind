package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/** Owns recovery decisions for staged protected-book artifact publication. */
final class SqliteProtectedBookPublicationRecovery {
  /** Compares two filesystem paths for one same-file ownership check. */
  @FunctionalInterface
  interface SameFileChecker {
    /** Returns whether both paths resolve to the same file. */
    boolean isSameFile(Path firstPath, Path secondPath) throws IOException;
  }

  /** Deletes one path during protected-book publication recovery. */
  @FunctionalInterface
  interface PathDeleter {
    /** Deletes the selected path. */
    void delete(Path path) throws IOException;
  }

  private SqliteProtectedBookPublicationRecovery() {}

  /** Removes one generated secret only when the staged artifact still proves ownership. */
  static boolean removePublishedSecretIfOwned(
      Path finalSecretPath, SqliteOwnedStagedArtifact stagedSecret, String cleanupAction) {
    return removePublishedSecretIfOwned(
        finalSecretPath, stagedSecret, cleanupAction, Files::isSameFile, Files::delete);
  }

  /** Removes one generated secret using explicit filesystem operations for recovery testing. */
  static boolean removePublishedSecretIfOwned(
      Path finalSecretPath,
      SqliteOwnedStagedArtifact stagedSecret,
      String cleanupAction,
      SameFileChecker sameFileChecker,
      PathDeleter deleter) {
    try {
      if (Files.notExists(finalSecretPath)) {
        return true;
      }
      if (!sameFileChecker.isSameFile(finalSecretPath, stagedSecret.stagedPath())) {
        return false;
      }
      deleter.delete(finalSecretPath);
      return true;
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to remove one owned generated-secret publication while "
              + Objects.requireNonNull(cleanupAction, "cleanupAction")
              + ".",
          exception);
    }
  }

  /** Determines whether one regular final secret is still the same file as its owned stage. */
  static boolean isSameOwnedStage(Path finalSecretPath, Path stagedSecretPath) {
    return isSameOwnedStage(finalSecretPath, stagedSecretPath, Files::isSameFile);
  }

  /** Determines same-file ownership using one explicit filesystem comparison operation. */
  static boolean isSameOwnedStage(
      Path finalSecretPath, Path stagedSecretPath, SameFileChecker sameFileChecker) {
    if (!Files.isRegularFile(finalSecretPath, LinkOption.NOFOLLOW_LINKS)
        || !Files.isRegularFile(stagedSecretPath, LinkOption.NOFOLLOW_LINKS)) {
      return false;
    }
    try {
      return sameFileChecker.isSameFile(finalSecretPath, stagedSecretPath);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to compare one generated secret with its owned maintenance stage.", exception);
    }
  }

  /** Removes one recovery-owned generated secret. */
  static void removeRecoveredSecret(Path finalSecretPath) {
    removeRecoveredSecret(finalSecretPath, Files::delete);
  }

  /** Removes one recovery-owned generated secret through one explicit deletion operation. */
  static void removeRecoveredSecret(Path finalSecretPath, PathDeleter deleter) {
    try {
      deleter.delete(finalSecretPath);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to remove one interrupted generated-secret publication at "
              + SqliteMachinePaths.absoluteValue(finalSecretPath)
              + ".",
          exception);
    }
  }

  /**
   * Removes one final artifact whose durable owned stage proves the interrupted publication owns
   * it.
   */
  static void removeRecoveredArtifact(Path finalArtifactPath) {
    removeRecoveredSecret(finalArtifactPath);
  }
}
