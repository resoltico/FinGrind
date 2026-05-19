package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.runtime.ContractResponse;
import dev.erst.fingrind.contract.runtime.PublicPathHint;
import java.util.List;
import java.util.Objects;

/** Closed family of deterministic protected-book maintenance refusals. */
public sealed interface BookMaintenanceRejection
    permits BookMaintenanceRejection.BookHasBlockingArtifacts,
        BookMaintenanceRejection.BackupSourceHasBlockingArtifacts,
        BookMaintenanceRejection.ArtifactBusy,
        BookMaintenanceRejection.BackupDestinationAlreadyExists,
        BookMaintenanceRejection.BackupKeyFileAlreadyExists,
        BookMaintenanceRejection.ArtifactVerificationFailed,
        BookMaintenanceRejection.NoRollbackArtifactsFound,
        BookMaintenanceRejection.RollbackArtifactSelectionRequired,
        BookMaintenanceRejection.RollbackArtifactNotFound,
        BookMaintenanceRejection.RollbackArtifactNotForBook {

  /** Returns the stable wire code for one maintenance rejection instance. */
  static String wireCode(BookMaintenanceRejection rejection) {
    return descriptorFor(rejection).code();
  }

  /** Returns the canonical machine descriptors for every permitted maintenance rejection. */
  static List<ContractResponse.RejectionDescriptor> descriptors() {
    return Descriptor.descriptors();
  }

  /** Rejection for maintenance commands that require a clean closed live book path. */
  record BookHasBlockingArtifacts(
      PublicPathHint bookFilePath, List<PublicPathHint> blockingArtifactPaths)
      implements BookMaintenanceRejection {
    public BookHasBlockingArtifacts {
      Objects.requireNonNull(bookFilePath, "bookFilePath");
      blockingArtifactPaths =
          List.copyOf(Objects.requireNonNull(blockingArtifactPaths, "blockingArtifactPaths"));
      if (blockingArtifactPaths.isEmpty()) {
        throw new IllegalArgumentException("blockingArtifactPaths must not be empty.");
      }
    }
  }

  /** Rejection for restore commands whose backup source carries unsafe SQLite sidecars. */
  record BackupSourceHasBlockingArtifacts(
      PublicPathHint backupFilePath, List<PublicPathHint> blockingArtifactPaths)
      implements BookMaintenanceRejection {
    public BackupSourceHasBlockingArtifacts {
      Objects.requireNonNull(backupFilePath, "backupFilePath");
      blockingArtifactPaths =
          List.copyOf(Objects.requireNonNull(blockingArtifactPaths, "blockingArtifactPaths"));
      if (blockingArtifactPaths.isEmpty()) {
        throw new IllegalArgumentException("blockingArtifactPaths must not be empty.");
      }
    }
  }

  /** Rejection for maintenance commands whose selected artifact is actively in use. */
  record ArtifactBusy(BookMaintenanceArtifactRole artifactRole, PublicPathHint artifactPath)
      implements BookMaintenanceRejection {
    public ArtifactBusy {
      Objects.requireNonNull(artifactRole, "artifactRole");
      Objects.requireNonNull(artifactPath, "artifactPath");
    }
  }

  /** Rejection for backup commands that refuse to overwrite an existing encrypted backup file. */
  record BackupDestinationAlreadyExists(PublicPathHint backupFilePath)
      implements BookMaintenanceRejection {
    public BackupDestinationAlreadyExists {
      Objects.requireNonNull(backupFilePath, "backupFilePath");
    }
  }

  /** Rejection for backup commands that refuse to overwrite an existing backup key file. */
  record BackupKeyFileAlreadyExists(PublicPathHint backupBookKeyFilePath)
      implements BookMaintenanceRejection {
    public BackupKeyFileAlreadyExists {
      Objects.requireNonNull(backupBookKeyFilePath, "backupBookKeyFilePath");
    }
  }

  /** Rejection for maintenance commands whose selected artifact failed verification. */
  record ArtifactVerificationFailed(
      BookMaintenanceArtifactRole artifactRole,
      PublicPathHint artifactPath,
      BookMaintenanceVerificationFailure verificationFailure)
      implements BookMaintenanceRejection {
    public ArtifactVerificationFailed {
      Objects.requireNonNull(artifactRole, "artifactRole");
      Objects.requireNonNull(artifactPath, "artifactPath");
      Objects.requireNonNull(verificationFailure, "verificationFailure");
    }
  }

  /** Rejection for rekey-recovery commands when no sibling rollback artifact exists. */
  record NoRollbackArtifactsFound(PublicPathHint bookFilePath) implements BookMaintenanceRejection {
    public NoRollbackArtifactsFound {
      Objects.requireNonNull(bookFilePath, "bookFilePath");
    }
  }

  /** Rejection for rekey-recovery commands when more than one rollback artifact exists. */
  record RollbackArtifactSelectionRequired(
      PublicPathHint bookFilePath, List<PublicPathHint> rollbackArtifactPaths)
      implements BookMaintenanceRejection {
    public RollbackArtifactSelectionRequired {
      Objects.requireNonNull(bookFilePath, "bookFilePath");
      rollbackArtifactPaths =
          List.copyOf(Objects.requireNonNull(rollbackArtifactPaths, "rollbackArtifactPaths"));
      if (rollbackArtifactPaths.size() < 2) {
        throw new IllegalArgumentException(
            "rollbackArtifactPaths must contain at least two entries when selection is required.");
      }
    }
  }

  /** Rejection for rekey-recovery commands that name a rollback artifact that is absent. */
  record RollbackArtifactNotFound(PublicPathHint rollbackArtifactPath)
      implements BookMaintenanceRejection {
    public RollbackArtifactNotFound {
      Objects.requireNonNull(rollbackArtifactPath, "rollbackArtifactPath");
    }
  }

  /** Rejection for rekey-recovery commands that name a non-sibling or non-canonical artifact. */
  record RollbackArtifactNotForBook(
      PublicPathHint bookFilePath, PublicPathHint rollbackArtifactPath)
      implements BookMaintenanceRejection {
    public RollbackArtifactNotForBook {
      Objects.requireNonNull(bookFilePath, "bookFilePath");
      Objects.requireNonNull(rollbackArtifactPath, "rollbackArtifactPath");
    }
  }

  private static Descriptor descriptorFor(BookMaintenanceRejection rejection) {
    return switch (Objects.requireNonNull(rejection, "rejection")) {
      case BookMaintenanceRejection.BookHasBlockingArtifacts _ ->
          Descriptor.BOOK_HAS_BLOCKING_ARTIFACTS;
      case BookMaintenanceRejection.BackupSourceHasBlockingArtifacts _ ->
          Descriptor.BACKUP_SOURCE_HAS_BLOCKING_ARTIFACTS;
      case BookMaintenanceRejection.ArtifactBusy _ -> Descriptor.ARTIFACT_BUSY;
      case BookMaintenanceRejection.BackupDestinationAlreadyExists _ ->
          Descriptor.BACKUP_DESTINATION_ALREADY_EXISTS;
      case BookMaintenanceRejection.BackupKeyFileAlreadyExists _ ->
          Descriptor.BACKUP_KEY_FILE_ALREADY_EXISTS;
      case BookMaintenanceRejection.ArtifactVerificationFailed _ ->
          Descriptor.ARTIFACT_VERIFICATION_FAILED;
      case BookMaintenanceRejection.NoRollbackArtifactsFound _ ->
          Descriptor.NO_ROLLBACK_ARTIFACTS_FOUND;
      case BookMaintenanceRejection.RollbackArtifactSelectionRequired _ ->
          Descriptor.ROLLBACK_ARTIFACT_SELECTION_REQUIRED;
      case BookMaintenanceRejection.RollbackArtifactNotFound _ ->
          Descriptor.ROLLBACK_ARTIFACT_NOT_FOUND;
      case BookMaintenanceRejection.RollbackArtifactNotForBook _ ->
          Descriptor.ROLLBACK_ARTIFACT_NOT_FOR_BOOK;
    };
  }

  /** Canonical maintenance rejection metadata keyed by stable wire code. */
  enum Descriptor {
    BOOK_HAS_BLOCKING_ARTIFACTS,
    BACKUP_SOURCE_HAS_BLOCKING_ARTIFACTS,
    ARTIFACT_BUSY,
    BACKUP_DESTINATION_ALREADY_EXISTS,
    BACKUP_KEY_FILE_ALREADY_EXISTS,
    ARTIFACT_VERIFICATION_FAILED,
    NO_ROLLBACK_ARTIFACTS_FOUND,
    ROLLBACK_ARTIFACT_SELECTION_REQUIRED,
    ROLLBACK_ARTIFACT_NOT_FOUND,
    ROLLBACK_ARTIFACT_NOT_FOR_BOOK;

    String code() {
      return switch (this) {
        case BOOK_HAS_BLOCKING_ARTIFACTS -> "book-has-blocking-artifacts";
        case BACKUP_SOURCE_HAS_BLOCKING_ARTIFACTS -> "backup-source-has-blocking-artifacts";
        case ARTIFACT_BUSY -> "artifact-busy";
        case BACKUP_DESTINATION_ALREADY_EXISTS -> "backup-destination-already-exists";
        case BACKUP_KEY_FILE_ALREADY_EXISTS -> "backup-key-file-already-exists";
        case ARTIFACT_VERIFICATION_FAILED -> "artifact-verification-failed";
        case NO_ROLLBACK_ARTIFACTS_FOUND -> "no-rollback-artifacts-found";
        case ROLLBACK_ARTIFACT_SELECTION_REQUIRED -> "rollback-artifact-selection-required";
        case ROLLBACK_ARTIFACT_NOT_FOUND -> "rollback-artifact-not-found";
        case ROLLBACK_ARTIFACT_NOT_FOR_BOOK -> "rollback-artifact-not-for-book";
      };
    }

    String description() {
      return switch (this) {
        case BOOK_HAS_BLOCKING_ARTIFACTS ->
            "Maintenance command refused because the selected live book path has SQLite sidecars or stale rollback artifacts that prove the book is not in one clean closed-copy state.";
        case BACKUP_SOURCE_HAS_BLOCKING_ARTIFACTS ->
            "Restore command refused because the selected encrypted backup file has SQLite sidecars or rollback artifacts and is not one clean closed-copy source.";
        case ARTIFACT_BUSY ->
            "Maintenance command refused because the selected protected-book artifact is actively in use by another workflow or process and cannot be proven quiescent.";
        case BACKUP_DESTINATION_ALREADY_EXISTS ->
            "Backup command refused because the selected backup destination file already exists and FinGrind will not overwrite it.";
        case BACKUP_KEY_FILE_ALREADY_EXISTS ->
            "Backup command refused because the selected backup key-file destination already exists and FinGrind will not overwrite it.";
        case ARTIFACT_VERIFICATION_FAILED ->
            "Maintenance command refused because the selected protected-book artifact did not verify as one initialized FinGrind book for the requested workflow.";
        case NO_ROLLBACK_ARTIFACTS_FOUND ->
            "Rekey recovery refused because no sibling rollback artifact exists for the selected book path.";
        case ROLLBACK_ARTIFACT_SELECTION_REQUIRED ->
            "Rekey recovery refused because more than one rollback artifact exists and FinGrind requires one explicit artifact selection.";
        case ROLLBACK_ARTIFACT_NOT_FOUND ->
            "Rekey recovery refused because the named rollback artifact path does not exist.";
        case ROLLBACK_ARTIFACT_NOT_FOR_BOOK ->
            "Rekey recovery refused because the named rollback artifact does not belong to the selected book path.";
      };
    }

    private ContractResponse.RejectionDescriptor descriptor() {
      return switch (this) {
        case BOOK_HAS_BLOCKING_ARTIFACTS, BACKUP_SOURCE_HAS_BLOCKING_ARTIFACTS ->
            new ContractResponse.RejectionDescriptor(
                code(),
                description(),
                List.of(
                    new ContractResponse.FieldDescriptor(
                        "bookFile",
                        "Redacted public hint for the selected live book file or backup source file."),
                    new ContractResponse.FieldDescriptor(
                        "blockingArtifacts",
                        "Ordered list of redacted sibling artifact hints that make the closed-copy workflow unsafe.")),
                List.of());
        case ARTIFACT_BUSY ->
            new ContractResponse.RejectionDescriptor(
                code(),
                description(),
                List.of(
                    new ContractResponse.FieldDescriptor(
                        "artifactRole",
                        "Canonical maintenance artifact role that was actively in use."),
                    new ContractResponse.FieldDescriptor(
                        "artifactPath",
                        "Redacted public hint for the busy protected-book artifact.")),
                List.of());
        case BACKUP_DESTINATION_ALREADY_EXISTS ->
            new ContractResponse.RejectionDescriptor(
                code(),
                description(),
                List.of(
                    new ContractResponse.FieldDescriptor(
                        "backupFile", "Redacted public hint for the conflicting backup file.")),
                List.of());
        case BACKUP_KEY_FILE_ALREADY_EXISTS ->
            new ContractResponse.RejectionDescriptor(
                code(),
                description(),
                List.of(
                    new ContractResponse.FieldDescriptor(
                        "backupBookKeyFile",
                        "Redacted public hint for the conflicting backup key file.")),
                List.of());
        case ARTIFACT_VERIFICATION_FAILED ->
            new ContractResponse.RejectionDescriptor(
                code(),
                description(),
                List.of(
                    new ContractResponse.FieldDescriptor(
                        "artifactRole",
                        "Stable public role for the protected-book artifact that failed verification."),
                    new ContractResponse.FieldDescriptor(
                        "artifactPath",
                        "Redacted public hint for the artifact that failed verification."),
                    new ContractResponse.FieldDescriptor(
                        "verificationFailure",
                        "Stable public verification failure code for the rejected artifact.")),
                List.of());
        case NO_ROLLBACK_ARTIFACTS_FOUND ->
            new ContractResponse.RejectionDescriptor(
                code(),
                description(),
                List.of(
                    new ContractResponse.FieldDescriptor(
                        "bookFile", "Redacted public hint for the selected live book file.")),
                List.of());
        case ROLLBACK_ARTIFACT_SELECTION_REQUIRED ->
            new ContractResponse.RejectionDescriptor(
                code(),
                description(),
                List.of(
                    new ContractResponse.FieldDescriptor(
                        "bookFile", "Redacted public hint for the selected live book file."),
                    new ContractResponse.FieldDescriptor(
                        "rollbackArtifacts",
                        "Ordered list of redacted sibling rollback artifact hints that require explicit selection.")),
                List.of());
        case ROLLBACK_ARTIFACT_NOT_FOUND ->
            new ContractResponse.RejectionDescriptor(
                code(),
                description(),
                List.of(
                    new ContractResponse.FieldDescriptor(
                        "rollbackArtifact",
                        "Redacted public hint for the missing rollback artifact.")),
                List.of());
        case ROLLBACK_ARTIFACT_NOT_FOR_BOOK ->
            new ContractResponse.RejectionDescriptor(
                code(),
                description(),
                List.of(
                    new ContractResponse.FieldDescriptor(
                        "bookFile", "Redacted public hint for the selected live book file."),
                    new ContractResponse.FieldDescriptor(
                        "rollbackArtifact",
                        "Redacted public hint for the rejected rollback artifact.")),
                List.of());
      };
    }

    private static List<ContractResponse.RejectionDescriptor> descriptors() {
      return List.of(values()).stream().map(Descriptor::descriptor).toList();
    }
  }
}
