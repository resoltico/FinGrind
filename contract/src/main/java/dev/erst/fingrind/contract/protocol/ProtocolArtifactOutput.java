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
          ProtocolOptions.PDF_OUT + " <path>",
          "Writes a PDF report artifact to the selected destination while preserving the command's selected stdout output mode.");
  private static final ProtocolArtifactOutput BOOK_FILE =
      new ProtocolArtifactOutput(
          BOOK_FILE_FORMAT,
          ProtocolOptions.BOOK_FILE + " <path>",
          "Publishes the restored live book file path selected for the restore workflow.");
  private static final ProtocolArtifactOutput GENERATED_BOOK_KEY_FILE =
      new ProtocolArtifactOutput(
          BOOK_KEY_FILE_FORMAT,
          ProtocolOptions.BOOK_KEY_FILE + " <path>",
          "Writes a new owner-only UTF-8 book key file to the selected destination.");
  private static final ProtocolArtifactOutput REPLACEMENT_BOOK_KEY_FILE =
      new ProtocolArtifactOutput(
          BOOK_KEY_FILE_FORMAT,
          ProtocolOptions.NEW_BOOK_KEY_FILE + " <existing-path>",
          "Publishes the replacement book key file path when rekey uses a key-file passphrase source.");
  private static final ProtocolArtifactOutput BOOK_KEY_FILE =
      new ProtocolArtifactOutput(
          BOOK_KEY_FILE_FORMAT,
          ProtocolOptions.BOOK_KEY_FILE + " <path>",
          "Publishes the restored live book key file path selected for the restore workflow.");
  private static final ProtocolArtifactOutput BACKUP_FILE =
      new ProtocolArtifactOutput(
          BACKUP_FILE_FORMAT,
          ProtocolOptions.BACKUP_FILE + " <path>",
          "Writes an encrypted backup-book copy to the selected destination without overwriting an existing file.");
  private static final ProtocolArtifactOutput BACKUP_KEY_FILE =
      new ProtocolArtifactOutput(
          BACKUP_KEY_FILE_FORMAT,
          ProtocolOptions.BACKUP_KEY_FILE + " <path>",
          "Writes the backup-book key file that reopens the exported backup copy without overwriting an existing file.");
  private static final ProtocolArtifactOutput ROLLBACK_BOOK_FILE =
      new ProtocolArtifactOutput(
          ROLLBACK_BOOK_FILE_FORMAT,
          ProtocolOptions.ROLLBACK_BOOK_FILE + " <path>",
          "Publishes the rollback-book file path selected or discovered during rekey recovery workflows.");
  private static final ProtocolArtifactOutput DISCOVERED_ROLLBACK_BOOK_FILE =
      new ProtocolArtifactOutput(
          ROLLBACK_BOOK_FILE_FORMAT,
          ProtocolOptions.BOOK_FILE + " <path>",
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

  /** Returns the canonical replacement book-key-file descriptor for rekey workflows. */
  public static ProtocolArtifactOutput replacementBookKeyFile() {
    return REPLACEMENT_BOOK_KEY_FILE;
  }

  /** Returns the canonical restored book-key-file descriptor. */
  public static ProtocolArtifactOutput bookKeyFile() {
    return BOOK_KEY_FILE;
  }

  /** Returns the canonical backup-file descriptor. */
  public static ProtocolArtifactOutput backupFile() {
    return BACKUP_FILE;
  }

  /** Returns the stable wire format published for backup file artifacts. */
  public static String backupFileFormat() {
    return BACKUP_FILE_FORMAT;
  }

  /** Returns the canonical backup-key-file descriptor. */
  public static ProtocolArtifactOutput backupKeyFile() {
    return BACKUP_KEY_FILE;
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
