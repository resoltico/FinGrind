package dev.erst.fingrind.contract.protocol;

import java.util.Objects;

/** Core-owned artifact export descriptor for a public FinGrind operation. */
public record ProtocolArtifactOutput(String format, String option, String description) {
  private static final String PDF_FORMAT = "pdf";
  private static final String BOOK_FILE_FORMAT = "book-file";
  private static final String BOOK_KEY_FILE_FORMAT = "book-key-file";
  private static final String ATTESTATION_KEY_FILE_FORMAT = "attestation-key-file";
  private static final String ATTESTATION_RECEIPT_FORMAT = "attestation-receipt-v1";
  private static final String BACKUP_FILE_FORMAT = "backup-file";
  private static final String BACKUP_KEY_FILE_FORMAT = "backup-key-file";
  private static final ProtocolArtifactOutput PDF =
      new ProtocolArtifactOutput(
          PDF_FORMAT,
          ProtocolOptions.Presentation.PDF_OUT + " <path>",
          "Publishes a no-clobber PDF report from a private existing output parent whose resolved ancestry resists non-owner substitution, while preserving the command's selected stdout output mode.");
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
  private static final ProtocolArtifactOutput GENERATED_ATTESTATION_KEY_FILE =
      new ProtocolArtifactOutput(
          ATTESTATION_KEY_FILE_FORMAT,
          ProtocolOptions.Attestation.NEW_KEY_FILE + " <path>",
          "Publishes a newly generated owner-only encrypted Ed25519 attestation key file at an absent target beneath a private existing parent whose resolved ancestry resists non-owner substitution.");
  private static final ProtocolArtifactOutput ATTESTATION_RECEIPT =
      new ProtocolArtifactOutput(
          ATTESTATION_RECEIPT_FORMAT,
          ProtocolOptions.Attestation.RECEIPT_FILE + " <path>",
          "Publishes an independently retained, quorum-signed attestation receipt at an absent target beneath a private existing parent whose resolved ancestry resists non-owner substitution.");
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

  /** Returns the canonical generated encrypted-attestation-key-file descriptor. */
  public static ProtocolArtifactOutput generatedAttestationKeyFile() {
    return GENERATED_ATTESTATION_KEY_FILE;
  }

  /** Returns the stable wire format published for encrypted attestation-key artifacts. */
  public static String attestationKeyFileFormat() {
    return ATTESTATION_KEY_FILE_FORMAT;
  }

  /** Returns the canonical independently retained attestation-receipt descriptor. */
  public static ProtocolArtifactOutput attestationReceipt() {
    return ATTESTATION_RECEIPT;
  }

  /** Returns the stable wire format published for attestation-receipt artifacts. */
  public static String attestationReceiptFormat() {
    return ATTESTATION_RECEIPT_FORMAT;
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

  private static String requireText(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName);
    if (value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank.");
    }
    return value;
  }
}
