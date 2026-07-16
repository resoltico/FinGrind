package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.ProtocolBookAccessOptions;
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
        ProtocolBookAccessOptions.BOOK_KEY_FILE,
        ProtocolBookAccessOptions.BOOK_FILE,
        ProtocolBookAccessOptions.BOOK_KEY_FILE);
    if (requestFile != null) {
      requireDistinct(
          bookFilePath,
          requestFile,
          ProtocolOptions.Request.FILE,
          ProtocolBookAccessOptions.BOOK_FILE,
          ProtocolOptions.Request.FILE);
      keyFilePath(passphraseSource)
          .ifPresent(
              keyFilePath ->
                  requireDistinct(
                      keyFilePath,
                      requestFile,
                      ProtocolBookAccessOptions.BOOK_KEY_FILE,
                      ProtocolBookAccessOptions.BOOK_KEY_FILE,
                      ProtocolOptions.Request.FILE));
    }
  }

  static void validateDistinctBackupPaths(
      Path bookFilePath,
      BookAccess.PassphraseSource passphraseSource,
      Path backupFilePath,
      Path newBackupKeyFilePath) {
    validateDistinctPaths(bookFilePath, passphraseSource, null);
    requireDistinct(
        bookFilePath,
        backupFilePath,
        ProtocolBookAccessOptions.BACKUP_FILE,
        ProtocolBookAccessOptions.BOOK_FILE,
        ProtocolBookAccessOptions.BACKUP_FILE);
    requireDistinct(
        backupFilePath,
        newBackupKeyFilePath,
        ProtocolBookAccessOptions.NEW_BACKUP_KEY_FILE,
        ProtocolBookAccessOptions.BACKUP_FILE,
        ProtocolBookAccessOptions.NEW_BACKUP_KEY_FILE);
    requireGeneratedSecretTargetSeparateFromExistingSource(
        bookFilePath,
        newBackupKeyFilePath,
        ProtocolBookAccessOptions.BOOK_FILE,
        ProtocolBookAccessOptions.NEW_BACKUP_KEY_FILE);
    requireDistinct(
        keyFilePath(passphraseSource),
        backupFilePath,
        ProtocolBookAccessOptions.BACKUP_FILE,
        ProtocolBookAccessOptions.BOOK_KEY_FILE,
        ProtocolBookAccessOptions.BACKUP_FILE);
    requireGeneratedSecretTargetSeparateFromExistingSource(
        keyFilePath(passphraseSource),
        newBackupKeyFilePath,
        ProtocolBookAccessOptions.BOOK_KEY_FILE,
        ProtocolBookAccessOptions.NEW_BACKUP_KEY_FILE);
  }

  static void validateDistinctRekeyTarget(
      Path bookFilePath,
      BookAccess.PassphraseSource currentPassphraseSource,
      Path newBookKeyFilePath) {
    validateDistinctPaths(bookFilePath, currentPassphraseSource, null);
    requireGeneratedSecretTargetSeparateFromExistingSource(
        bookFilePath,
        newBookKeyFilePath,
        ProtocolBookAccessOptions.BOOK_FILE,
        ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE);
    requireGeneratedSecretTargetSeparateFromExistingSource(
        keyFilePath(currentPassphraseSource),
        newBookKeyFilePath,
        ProtocolBookAccessOptions.BOOK_KEY_FILE,
        ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE);
  }

  static void validateDistinctRestorePaths(
      Path bookFilePath, Path newBookKeyFilePath, Path backupFilePath, Path backupKeyFilePath) {
    requireDistinct(
        bookFilePath,
        newBookKeyFilePath,
        ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE,
        ProtocolBookAccessOptions.BOOK_FILE,
        ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE);
    requireDistinct(
        bookFilePath,
        backupFilePath,
        ProtocolBookAccessOptions.BACKUP_FILE,
        ProtocolBookAccessOptions.BOOK_FILE,
        ProtocolBookAccessOptions.BACKUP_FILE);
    requireDistinct(
        bookFilePath,
        backupKeyFilePath,
        ProtocolBookAccessOptions.BACKUP_KEY_FILE,
        ProtocolBookAccessOptions.BOOK_FILE,
        ProtocolBookAccessOptions.BACKUP_KEY_FILE);
    requireGeneratedSecretTargetSeparateFromExistingSource(
        backupFilePath,
        newBookKeyFilePath,
        ProtocolBookAccessOptions.BACKUP_FILE,
        ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE);
    requireGeneratedSecretTargetSeparateFromExistingSource(
        backupKeyFilePath,
        newBookKeyFilePath,
        ProtocolBookAccessOptions.BACKUP_KEY_FILE,
        ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE);
    requireDistinct(
        backupFilePath,
        backupKeyFilePath,
        ProtocolBookAccessOptions.BACKUP_KEY_FILE,
        ProtocolBookAccessOptions.BACKUP_FILE,
        ProtocolBookAccessOptions.BACKUP_KEY_FILE);
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

  private static void requireGeneratedSecretTargetSeparateFromExistingSource(
      Path existingSourcePath,
      Path generatedSecretTargetPath,
      String sourceOption,
      String targetOption) {
    if (existingSourcePath.toAbsolutePath().equals(generatedSecretTargetPath.toAbsolutePath())) {
      throw CliGeneratedSecretTargetFailures.occupiedByRequiredSource(
          targetOption, sourceOption, existingSourcePath);
    }
  }

  private static void requireGeneratedSecretTargetSeparateFromExistingSource(
      Optional<Path> existingSourcePath,
      Path generatedSecretTargetPath,
      String sourceOption,
      String targetOption) {
    existingSourcePath.ifPresent(
        path ->
            requireGeneratedSecretTargetSeparateFromExistingSource(
                path, generatedSecretTargetPath, sourceOption, targetOption));
  }
}
