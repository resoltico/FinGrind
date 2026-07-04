package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.runtime.BookAccess;
import java.nio.file.Path;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Focused path-collision rules for book, key, backup, and restore file arguments. */
final class CliBookPathCollisionRules {
  private CliBookPathCollisionRules() {}

  static void validateDistinctPaths(
      Path bookFilePath, BookAccess.PassphraseSource passphraseSource, @Nullable Path requestFile) {
    requireDistinct(
        bookFilePath,
        keyFilePath(passphraseSource),
        ProtocolOptions.BOOK_KEY_FILE,
        ProtocolOptions.BOOK_FILE,
        ProtocolOptions.BOOK_KEY_FILE);
    if (requestFile != null) {
      requireDistinct(
          bookFilePath,
          requestFile,
          ProtocolOptions.REQUEST_FILE,
          ProtocolOptions.BOOK_FILE,
          ProtocolOptions.REQUEST_FILE);
      keyFilePath(passphraseSource)
          .ifPresent(
              keyFilePath ->
                  requireDistinct(
                      keyFilePath,
                      requestFile,
                      ProtocolOptions.BOOK_KEY_FILE,
                      ProtocolOptions.BOOK_KEY_FILE,
                      ProtocolOptions.REQUEST_FILE));
    }
  }

  static void validateDistinctRekeyPaths(
      Path bookFilePath,
      BookAccess.PassphraseSource currentPassphraseSource,
      BookAccess.PassphraseSource replacementPassphraseSource) {
    validateDistinctPaths(bookFilePath, currentPassphraseSource, null);
    validateDistinctPaths(bookFilePath, replacementPassphraseSource, null);
    requireDistinct(
        keyFilePath(currentPassphraseSource),
        keyFilePath(replacementPassphraseSource),
        ProtocolOptions.NEW_BOOK_KEY_FILE,
        ProtocolOptions.BOOK_KEY_FILE,
        ProtocolOptions.NEW_BOOK_KEY_FILE);
  }

  static void validateDistinctBackupPaths(
      Path bookFilePath,
      BookAccess.PassphraseSource passphraseSource,
      Path backupFilePath,
      Path backupBookKeyFilePath) {
    validateDistinctPaths(bookFilePath, passphraseSource, null);
    requireDistinct(
        bookFilePath,
        backupFilePath,
        ProtocolOptions.BACKUP_FILE,
        ProtocolOptions.BOOK_FILE,
        ProtocolOptions.BACKUP_FILE);
    requireDistinct(
        backupFilePath,
        backupBookKeyFilePath,
        ProtocolOptions.BACKUP_KEY_FILE,
        ProtocolOptions.BACKUP_FILE,
        ProtocolOptions.BACKUP_KEY_FILE);
    requireDistinct(
        keyFilePath(passphraseSource),
        backupFilePath,
        ProtocolOptions.BACKUP_FILE,
        ProtocolOptions.BOOK_KEY_FILE,
        ProtocolOptions.BACKUP_FILE);
    requireDistinct(
        keyFilePath(passphraseSource),
        backupBookKeyFilePath,
        ProtocolOptions.BACKUP_KEY_FILE,
        ProtocolOptions.BOOK_KEY_FILE,
        ProtocolOptions.BACKUP_KEY_FILE);
  }

  static void validateDistinctRestorePaths(
      Path bookFilePath, Path bookKeyFilePath, Path backupFilePath, Path backupBookKeyFilePath) {
    requireDistinct(
        bookFilePath,
        bookKeyFilePath,
        ProtocolOptions.BOOK_KEY_FILE,
        ProtocolOptions.BOOK_FILE,
        ProtocolOptions.BOOK_KEY_FILE);
    requireDistinct(
        bookFilePath,
        backupFilePath,
        ProtocolOptions.BACKUP_FILE,
        ProtocolOptions.BOOK_FILE,
        ProtocolOptions.BACKUP_FILE);
    requireDistinct(
        bookFilePath,
        backupBookKeyFilePath,
        ProtocolOptions.BACKUP_KEY_FILE,
        ProtocolOptions.BOOK_FILE,
        ProtocolOptions.BACKUP_KEY_FILE);
    requireDistinct(
        bookKeyFilePath,
        backupFilePath,
        ProtocolOptions.BACKUP_FILE,
        ProtocolOptions.BOOK_KEY_FILE,
        ProtocolOptions.BACKUP_FILE);
    requireDistinct(
        bookKeyFilePath,
        backupBookKeyFilePath,
        ProtocolOptions.BACKUP_KEY_FILE,
        ProtocolOptions.BOOK_KEY_FILE,
        ProtocolOptions.BACKUP_KEY_FILE);
    requireDistinct(
        backupFilePath,
        backupBookKeyFilePath,
        ProtocolOptions.BACKUP_KEY_FILE,
        ProtocolOptions.BACKUP_FILE,
        ProtocolOptions.BACKUP_KEY_FILE);
  }

  static Optional<Path> keyFilePath(BookAccess.PassphraseSource passphraseSource) {
    return switch (passphraseSource) {
      case BookAccess.PassphraseSource.KeyFile keyFile -> Optional.of(keyFile.bookKeyFilePath());
      case BookAccess.PassphraseSource.StandardInput _ -> Optional.empty();
      case BookAccess.PassphraseSource.InteractivePrompt _ -> Optional.empty();
    };
  }

  private static void requireDistinct(
      Optional<Path> leftPath,
      Optional<Path> rightPath,
      String errorOption,
      String leftOption,
      String rightOption) {
    if (leftPath.isPresent() && rightPath.isPresent()) {
      requireDistinct(
          leftPath.orElseThrow(), rightPath.orElseThrow(), errorOption, leftOption, rightOption);
    }
  }

  private static void requireDistinct(
      Optional<Path> leftPath,
      Path rightPath,
      String errorOption,
      String leftOption,
      String rightOption) {
    leftPath.ifPresent(
        path -> requireDistinct(path, rightPath, errorOption, leftOption, rightOption));
  }

  private static void requireDistinct(
      Path leftPath,
      Optional<Path> rightPath,
      String errorOption,
      String leftOption,
      String rightOption) {
    rightPath.ifPresent(
        path -> requireDistinct(leftPath, path, errorOption, leftOption, rightOption));
  }

  private static void requireDistinct(
      Path leftPath, Path rightPath, String errorOption, String leftOption, String rightOption) {
    if (leftPath.toAbsolutePath().equals(rightPath.toAbsolutePath())) {
      throw CliArgumentValueParser.invalid(
          errorOption, leftOption + " and " + rightOption + " must not point to the same path.");
    }
  }
}
