package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.runtime.BookAccess;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/** Shared path-collision and standard-input validation for book-addressing CLI commands. */
final class CliBookPathValidator {
  private CliBookPathValidator() {}

  static void validateDistinctPaths(
      Path bookFilePath, BookAccess.PassphraseSource passphraseSource, @Nullable Path requestFile) {
    CliBookPathCollisionRules.validateDistinctPaths(bookFilePath, passphraseSource, requestFile);
  }

  static void validateStandardInputUsage(
      BookAccess.PassphraseSource passphraseSource, @Nullable Path requestFile) {
    CliStandardInputUsageRules.validateStandardInputUsage(passphraseSource, requestFile);
  }

  static void validateDistinctRekeyTarget(
      Path bookFilePath,
      BookAccess.PassphraseSource currentPassphraseSource,
      Path newBookKeyFilePath) {
    CliBookPathCollisionRules.validateDistinctRekeyTarget(
        bookFilePath, currentPassphraseSource, newBookKeyFilePath);
  }

  static void validateDistinctBackupPaths(
      Path bookFilePath,
      BookAccess.PassphraseSource passphraseSource,
      Path backupFilePath,
      Path backupBookKeyFilePath) {
    CliBookPathCollisionRules.validateDistinctBackupPaths(
        bookFilePath, passphraseSource, backupFilePath, backupBookKeyFilePath);
  }

  static void validateDistinctRestorePaths(
      Path bookFilePath, Path bookKeyFilePath, Path backupFilePath, Path backupBookKeyFilePath) {
    CliBookPathCollisionRules.validateDistinctRestorePaths(
        bookFilePath, bookKeyFilePath, backupFilePath, backupBookKeyFilePath);
  }
}
