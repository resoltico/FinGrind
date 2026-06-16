package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.runtime.BookAccess;
import java.nio.file.Path;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Shared path-collision and standard-input validation for book-addressing CLI commands. */
final class CliBookPathValidator {
  private CliBookPathValidator() {}

  static void validateDistinctPaths(
      Path bookFilePath, BookAccess.PassphraseSource passphraseSource, @Nullable Path requestFile) {
    Optional<Path> keyFilePath = keyFilePath(passphraseSource);
    if (keyFilePath.isPresent()
        && bookFilePath.toAbsolutePath().equals(keyFilePath.orElseThrow().toAbsolutePath())) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BOOK_KEY_FILE,
          ProtocolOptions.BOOK_FILE
              + " and "
              + ProtocolOptions.BOOK_KEY_FILE
              + " must not point to the same path.");
    }
    if (requestFile != null && bookFilePath.toAbsolutePath().equals(requestFile.toAbsolutePath())) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.REQUEST_FILE,
          ProtocolOptions.BOOK_FILE
              + " and "
              + ProtocolOptions.REQUEST_FILE
              + " must not point to the same path.");
    }
    if (requestFile != null
        && keyFilePath.isPresent()
        && keyFilePath.orElseThrow().toAbsolutePath().equals(requestFile.toAbsolutePath())) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BOOK_KEY_FILE,
          ProtocolOptions.BOOK_KEY_FILE
              + " and "
              + ProtocolOptions.REQUEST_FILE
              + " must not point to the same path.");
    }
  }

  static void validateStandardInputUsage(
      BookAccess.PassphraseSource passphraseSource, @Nullable Path requestFile) {
    if (requestFile != null
        && ProtocolOptions.STDIN_TOKEN.equals(requestFile.toString())
        && isStandardInput(passphraseSource)) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BOOK_PASSPHRASE_STDIN,
          "Standard input cannot supply both the book passphrase and the request JSON.");
    }
  }

  static void validateDistinctRekeyPaths(
      Path bookFilePath,
      BookAccess.PassphraseSource currentPassphraseSource,
      BookAccess.PassphraseSource replacementPassphraseSource) {
    validateDistinctPaths(bookFilePath, currentPassphraseSource, null);
    validateDistinctPaths(bookFilePath, replacementPassphraseSource, null);
    Optional<Path> currentKeyFilePath = keyFilePath(currentPassphraseSource);
    Optional<Path> replacementKeyFilePath = keyFilePath(replacementPassphraseSource);
    if (currentKeyFilePath.isPresent()
        && replacementKeyFilePath.isPresent()
        && currentKeyFilePath
            .orElseThrow()
            .toAbsolutePath()
            .equals(replacementKeyFilePath.orElseThrow().toAbsolutePath())) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.NEW_BOOK_KEY_FILE,
          ProtocolOptions.BOOK_KEY_FILE
              + " and "
              + ProtocolOptions.NEW_BOOK_KEY_FILE
              + " must not point to the same path.");
    }
  }

  static void validateRekeyStandardInputUsage(
      BookAccess.PassphraseSource currentPassphraseSource,
      BookAccess.PassphraseSource replacementPassphraseSource) {
    if (isStandardInput(currentPassphraseSource) && isStandardInput(replacementPassphraseSource)) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.NEW_BOOK_PASSPHRASE_STDIN,
          "Standard input cannot supply both the current and new book passphrases.");
    }
  }

  static void validateDistinctBackupPaths(
      Path bookFilePath,
      BookAccess.PassphraseSource passphraseSource,
      Path backupFilePath,
      Path backupBookKeyFilePath) {
    validateDistinctPaths(bookFilePath, passphraseSource, null);
    Path normalizedBookFilePath = bookFilePath.toAbsolutePath();
    Path normalizedBackupFilePath = backupFilePath.toAbsolutePath();
    Path normalizedBackupBookKeyFilePath = backupBookKeyFilePath.toAbsolutePath();
    if (normalizedBookFilePath.equals(normalizedBackupFilePath)) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BACKUP_BOOK_FILE_OUT,
          ProtocolOptions.BOOK_FILE
              + " and "
              + ProtocolOptions.BACKUP_BOOK_FILE_OUT
              + " must not point to the same path.");
    }
    if (normalizedBackupFilePath.equals(normalizedBackupBookKeyFilePath)) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BACKUP_BOOK_KEY_FILE_OUT,
          ProtocolOptions.BACKUP_BOOK_FILE_OUT
              + " and "
              + ProtocolOptions.BACKUP_BOOK_KEY_FILE_OUT
              + " must not point to the same path.");
    }
    Optional<Path> keyFilePath = keyFilePath(passphraseSource);
    if (keyFilePath.isPresent()
        && keyFilePath.orElseThrow().toAbsolutePath().equals(normalizedBackupFilePath)) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BACKUP_BOOK_FILE_OUT,
          ProtocolOptions.BOOK_KEY_FILE
              + " and "
              + ProtocolOptions.BACKUP_BOOK_FILE_OUT
              + " must not point to the same path.");
    }
    if (keyFilePath.isPresent()
        && keyFilePath.orElseThrow().toAbsolutePath().equals(normalizedBackupBookKeyFilePath)) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BACKUP_BOOK_KEY_FILE_OUT,
          ProtocolOptions.BOOK_KEY_FILE
              + " and "
              + ProtocolOptions.BACKUP_BOOK_KEY_FILE_OUT
              + " must not point to the same path.");
    }
  }

  static void validateDistinctRestorePaths(
      Path bookFilePath, Path backupFilePath, Path backupBookKeyFilePath) {
    Path normalizedBookFilePath = bookFilePath.toAbsolutePath();
    Path normalizedBackupFilePath = backupFilePath.toAbsolutePath();
    Path normalizedBackupBookKeyFilePath = backupBookKeyFilePath.toAbsolutePath();
    if (normalizedBookFilePath.equals(normalizedBackupFilePath)) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BACKUP_BOOK_FILE,
          ProtocolOptions.BOOK_FILE
              + " and "
              + ProtocolOptions.BACKUP_BOOK_FILE
              + " must not point to the same path.");
    }
    if (normalizedBookFilePath.equals(normalizedBackupBookKeyFilePath)) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BACKUP_BOOK_KEY_FILE,
          ProtocolOptions.BOOK_FILE
              + " and "
              + ProtocolOptions.BACKUP_BOOK_KEY_FILE
              + " must not point to the same path.");
    }
    if (normalizedBackupFilePath.equals(normalizedBackupBookKeyFilePath)) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BACKUP_BOOK_KEY_FILE,
          ProtocolOptions.BACKUP_BOOK_FILE
              + " and "
              + ProtocolOptions.BACKUP_BOOK_KEY_FILE
              + " must not point to the same path.");
    }
  }

  private static Optional<Path> keyFilePath(BookAccess.PassphraseSource passphraseSource) {
    return switch (passphraseSource) {
      case BookAccess.PassphraseSource.KeyFile keyFile -> Optional.of(keyFile.bookKeyFilePath());
      case BookAccess.PassphraseSource.StandardInput _ -> Optional.empty();
      case BookAccess.PassphraseSource.InteractivePrompt _ -> Optional.empty();
    };
  }

  private static boolean isStandardInput(BookAccess.PassphraseSource passphraseSource) {
    return switch (passphraseSource) {
      case BookAccess.PassphraseSource.KeyFile _ -> false;
      case BookAccess.PassphraseSource.StandardInput _ -> true;
      case BookAccess.PassphraseSource.InteractivePrompt _ -> false;
    };
  }
}
