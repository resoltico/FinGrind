package dev.erst.fingrind.contract.protocol;

import java.util.Objects;

/** Core-owned artifact export descriptor for a public FinGrind operation. */
public record ProtocolArtifactOutput(String format, String option, String description) {
  private static final String PDF_FORMAT = "pdf";
  private static final String BOOK_FILE_FORMAT = "book-file";
  private static final String BOOK_KEY_FILE_FORMAT = "book-key-file";
  private static final String BACKUP_FILE_FORMAT = "backup-file";
  private static final String BACKUP_KEY_FILE_FORMAT = "backup-key-file";
  private static final String ROLLBACK_BOOK_FILE_FORMAT = "rollback-book-file";
  private static final ProtocolArtifactOutput PDF =
      new ProtocolArtifactOutput(
          PDF_FORMAT,
          ProtocolOptions.Presentation.PDF_OUT + " <path>",
          "Writes a PDF report artifact to the selected destination while preserving the command's selected stdout output mode.");
  private static final ProtocolArtifactOutput BOOK_FILE =
      new ProtocolArtifactOutput(
          BOOK_FILE_FORMAT,
          ProtocolBookAccessOptions.BOOK_FILE + " <path>",
          "Publishes the restored live book file path selected for the restore workflow.");
  private static final ProtocolArtifactOutput GENERATED_BOOK_KEY_FILE =
      new ProtocolArtifactOutput(
          BOOK_KEY_FILE_FORMAT,
          ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE + " <path>",
          "Publishes a newly generated owner-only UTF-8 book key file at an absent target path.");
  private static final ProtocolArtifactOutput NEW_BOOK_KEY_FILE =
      new ProtocolArtifactOutput(
          BOOK_KEY_FILE_FORMAT,
          ProtocolBookAccessOptions.NEW_BOOK_KEY_FILE + " <path>",
          "Publishes the newly generated book key file for a staged rekey or restore workflow.");
  private static final ProtocolArtifactOutput NEW_BACKUP_KEY_FILE =
      new ProtocolArtifactOutput(
          BACKUP_KEY_FILE_FORMAT,
          ProtocolBookAccessOptions.NEW_BACKUP_KEY_FILE + " <path>",
          "Publishes the newly generated backup key file that reopens the exported backup copy.");
  private static final ProtocolArtifactOutput BACKUP_FILE =
      new ProtocolArtifactOutput(
          BACKUP_FILE_FORMAT,
          ProtocolBookAccessOptions.BACKUP_FILE + " <path>",
          "Writes an encrypted backup-book copy to the selected destination without overwriting an existing file.");
  private static final ProtocolArtifactOutput ROLLBACK_BOOK_FILE =
      new ProtocolArtifactOutput(
          ROLLBACK_BOOK_FILE_FORMAT,
          ProtocolBookAccessOptions.ROLLBACK_BOOK_FILE + " <path>",
          "Publishes the rollback-book file path selected or discovered during rekey recovery workflows.");
  private static final ProtocolArtifactOutput DISCOVERED_ROLLBACK_BOOK_FILE =
      new ProtocolArtifactOutput(
          ROLLBACK_BOOK_FILE_FORMAT,
          ProtocolBookAccessOptions.BOOK_FILE + " <path>",
          "Publishes rollback-book file paths discovered beside the selected live book path.");

  /** Validates an artifact-export descriptor. */
  public ProtocolArtifactOutput {
    format = requireText(format, "format");
    option = requireText(option, "option");
    description = requireText(description, "description");
  }

  /** Returns the canonical PDF export descriptor. */
  public static ProtocolArtifactOutput pdf() {
    return PDF;
  }

  /** Returns the stable wire format published for PDF report artifacts. */
  public static String pdfFormat() {
    return PDF_FORMAT;
  }

  /** Returns the canonical restored book-file descriptor. */
  public static ProtocolArtifactOutput bookFile() {
    return BOOK_FILE;
  }

  /** Returns the stable wire format published for book file artifacts. */
  public static String bookFileFormat() {
    return BOOK_FILE_FORMAT;
  }

  /** Returns the canonical generated book-key-file descriptor. */
  public static ProtocolArtifactOutput generatedBookKeyFile() {
    return GENERATED_BOOK_KEY_FILE;
  }

  /** Returns the stable wire format published for book key file artifacts. */
  public static String bookKeyFileFormat() {
    return BOOK_KEY_FILE_FORMAT;
  }

  /** Returns the canonical generated book-key-file descriptor for rekey and restore workflows. */
  public static ProtocolArtifactOutput newBookKeyFile() {
    return NEW_BOOK_KEY_FILE;
  }

  /** Returns the canonical backup-file descriptor. */
  public static ProtocolArtifactOutput backupFile() {
    return BACKUP_FILE;
  }

  /** Returns the stable wire format published for backup file artifacts. */
  public static String backupFileFormat() {
    return BACKUP_FILE_FORMAT;
  }

  /** Returns the canonical generated backup-key-file descriptor. */
  public static ProtocolArtifactOutput newBackupKeyFile() {
    return NEW_BACKUP_KEY_FILE;
  }

  /** Returns the stable wire format published for backup key file artifacts. */
  public static String backupKeyFileFormat() {
    return BACKUP_KEY_FILE_FORMAT;
  }

  /** Returns the canonical rollback-book-file descriptor for selected recovery artifacts. */
  public static ProtocolArtifactOutput rollbackBookFile() {
    return ROLLBACK_BOOK_FILE;
  }

  /** Returns the canonical rollback-book-file descriptor for sibling discovery. */
  public static ProtocolArtifactOutput discoveredRollbackBookFile() {
    return DISCOVERED_ROLLBACK_BOOK_FILE;
  }

  /** Returns the stable wire format published for rollback-book-file artifacts. */
  public static String rollbackBookFileFormat() {
    return ROLLBACK_BOOK_FILE_FORMAT;
  }

  private static String requireText(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName);
    if (value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank.");
    }
    return value;
  }
}
