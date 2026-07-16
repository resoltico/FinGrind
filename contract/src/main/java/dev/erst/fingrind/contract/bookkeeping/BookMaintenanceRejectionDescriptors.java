package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.runtime.ContractResponse;
import java.util.List;
import java.util.Objects;

/** Descriptor owner for the closed family of published book-maintenance rejections. */
final class BookMaintenanceRejectionDescriptors {
  private BookMaintenanceRejectionDescriptors() {}

  static String wireCode(BookMaintenanceRejection rejection) {
    return descriptorFor(rejection).code();
  }

  static List<ContractResponse.RejectionDescriptor> descriptors() {
    return Descriptor.descriptors();
  }

  private static Descriptor descriptorFor(BookMaintenanceRejection rejection) {
    return switch (Objects.requireNonNull(rejection, "rejection")) {
      case BookMaintenanceRejection.BookHasBlockingArtifacts _ ->
          Descriptor.BOOK_HAS_BLOCKING_ARTIFACTS;
      case BookMaintenanceRejection.BackupSourceHasBlockingArtifacts _ ->
          Descriptor.BACKUP_SOURCE_HAS_BLOCKING_ARTIFACTS;
      case BookMaintenanceRejection.BackupSourceMatchesLiveBook _ ->
          Descriptor.BACKUP_SOURCE_MATCHES_LIVE_BOOK;
      case BookMaintenanceRejection.ArtifactPathInvalid _ -> Descriptor.ARTIFACT_PATH_INVALID;
      case BookMaintenanceRejection.ArtifactBusy _ -> Descriptor.ARTIFACT_BUSY;
      case BookMaintenanceRejection.BackupDestinationAlreadyExists _ ->
          Descriptor.BACKUP_DESTINATION_ALREADY_EXISTS;
      case BookMaintenanceRejection.SecretTargetOccupied _ -> Descriptor.SECRET_TARGET_OCCUPIED;
      case BookMaintenanceRejection.BookDestinationOccupied _ ->
          Descriptor.BOOK_DESTINATION_OCCUPIED;
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
    BOOK_HAS_BLOCKING_ARTIFACTS(
        "book-has-blocking-artifacts",
        "Maintenance command refused because the selected live book path has SQLite sidecars or stale rollback artifacts that prove the book is not in one clean closed-copy state.",
        FieldShape.BLOCKING_ARTIFACTS),
    BACKUP_SOURCE_HAS_BLOCKING_ARTIFACTS(
        "backup-source-has-blocking-artifacts",
        "Restore command refused because the selected encrypted backup file has SQLite sidecars or rollback artifacts and is not one clean closed-copy source.",
        FieldShape.BLOCKING_ARTIFACTS),
    BACKUP_SOURCE_MATCHES_LIVE_BOOK(
        "backup-source-matches-live-book",
        "Restore command refused because the selected backup source path equals the live book path and FinGrind will not replace a book from itself.",
        FieldShape.BACKUP_SOURCE_CONFLICT),
    ARTIFACT_PATH_INVALID(
        "artifact-path-invalid",
        "Maintenance command refused because one selected artifact path or its parent-directory permissions do not satisfy the protected-book filesystem contract.",
        FieldShape.PATH_INVALID),
    ARTIFACT_BUSY(
        "artifact-busy",
        "Maintenance command refused because the selected protected-book artifact is actively in use by another workflow or process and cannot be proven quiescent.",
        FieldShape.ARTIFACT_BUSY),
    BACKUP_DESTINATION_ALREADY_EXISTS(
        "backup-destination-already-exists",
        "Backup command refused because the selected backup destination file already exists and FinGrind will not overwrite it.",
        FieldShape.BACKUP_DESTINATION),
    SECRET_TARGET_OCCUPIED(
        "secret-target-occupied",
        "Maintenance command refused because the selected generated-secret target already exists and FinGrind will not overwrite it.",
        FieldShape.SECRET_TARGET),
    BOOK_DESTINATION_OCCUPIED(
        "book-destination-occupied",
        "Restore command refused because the selected destination book already exists without explicit replacement consent.",
        FieldShape.BOOK_DESTINATION),
    ARTIFACT_VERIFICATION_FAILED(
        "artifact-verification-failed",
        "Maintenance command refused because the selected protected-book artifact did not verify as one initialized FinGrind book for the requested workflow.",
        FieldShape.VERIFICATION),
    NO_ROLLBACK_ARTIFACTS_FOUND(
        "no-rollback-artifacts-found",
        "Rekey recovery refused because no sibling rollback artifact exists for the selected book path.",
        FieldShape.ROLLBACK_BOOK),
    ROLLBACK_ARTIFACT_SELECTION_REQUIRED(
        "rollback-artifact-selection-required",
        "Rekey recovery refused because more than one rollback artifact exists and FinGrind requires one explicit artifact selection.",
        FieldShape.ROLLBACK_SELECTION),
    ROLLBACK_ARTIFACT_NOT_FOUND(
        "rollback-artifact-not-found",
        "Rekey recovery refused because the named rollback artifact path does not exist.",
        FieldShape.ROLLBACK_MISSING),
    ROLLBACK_ARTIFACT_NOT_FOR_BOOK(
        "rollback-artifact-not-for-book",
        "Rekey recovery refused because the named rollback artifact does not belong to the selected book path.",
        FieldShape.ROLLBACK_NOT_FOR_BOOK);

    private final String code;
    private final String description;
    private final FieldShape fieldShape;

    Descriptor(String code, String description, FieldShape fieldShape) {
      this.code = code;
      this.description = description;
      this.fieldShape = fieldShape;
    }

    String code() {
      return code;
    }

    private ContractResponse.RejectionDescriptor descriptor() {
      return new ContractResponse.RejectionDescriptor(
          code,
          ContractResponse.FailureCategory.PRECONDITION,
          description,
          fieldShape.fields(),
          List.of());
    }

    private static List<ContractResponse.RejectionDescriptor> descriptors() {
      return List.of(values()).stream().map(Descriptor::descriptor).toList();
    }
  }

  /** Reusable published field layouts for the closed maintenance rejection family. */
  private enum FieldShape {
    BLOCKING_ARTIFACTS {
      @Override
      List<ContractResponse.FieldDescriptor> fields() {
        return List.of(
            field(
                "bookFile",
                "Canonical absolute path for the selected live book file or backup source file."),
            field(
                "blockingArtifacts",
                "Canonical absolute sibling artifact paths, in deterministic order, that make the closed-copy workflow unsafe."));
      }
    },
    BACKUP_SOURCE_CONFLICT {
      @Override
      List<ContractResponse.FieldDescriptor> fields() {
        return List.of(
            field("bookFile", "Canonical absolute path for the selected live book file."),
            field("backupFile", "Canonical absolute path for the conflicting backup source file."));
      }
    },
    PATH_INVALID {
      @Override
      List<ContractResponse.FieldDescriptor> fields() {
        return List.of(
            field(
                "artifactRole",
                "Canonical maintenance artifact role whose selected path was invalid."),
            field(
                "artifactPath", "Canonical absolute path for the invalid protected-book artifact."),
            field(
                "pathFailure",
                "Stable protected-book path-failure code naming the specific filesystem-contract violation."));
      }
    },
    ARTIFACT_BUSY {
      @Override
      List<ContractResponse.FieldDescriptor> fields() {
        return List.of(
            field("artifactRole", "Canonical maintenance artifact role that was actively in use."),
            field("artifactPath", "Canonical absolute path for the busy protected-book artifact."));
      }
    },
    BACKUP_DESTINATION {
      @Override
      List<ContractResponse.FieldDescriptor> fields() {
        return List.of(
            field("backupFile", "Canonical absolute path for the conflicting backup file."));
      }
    },
    SECRET_TARGET {
      @Override
      List<ContractResponse.FieldDescriptor> fields() {
        return List.of(
            field(
                "secretTarget",
                "Canonical absolute path for the occupied generated-secret target."));
      }
    },
    BOOK_DESTINATION {
      @Override
      List<ContractResponse.FieldDescriptor> fields() {
        return List.of(
            field(
                "bookFile", "Canonical absolute path for the selected existing destination book."));
      }
    },
    VERIFICATION {
      @Override
      List<ContractResponse.FieldDescriptor> fields() {
        return List.of(
            field(
                "artifactRole",
                "Stable public role for the protected-book artifact that failed verification."),
            field(
                "artifactPath",
                "Canonical absolute path for the artifact that failed verification."),
            field(
                "verificationFailure",
                "Stable public verification failure code for the rejected artifact."));
      }
    },
    ROLLBACK_BOOK {
      @Override
      List<ContractResponse.FieldDescriptor> fields() {
        return List.of(
            field("bookFile", "Canonical absolute path for the selected live book file."));
      }
    },
    ROLLBACK_SELECTION {
      @Override
      List<ContractResponse.FieldDescriptor> fields() {
        return List.of(
            field("bookFile", "Canonical absolute path for the selected live book file."),
            field(
                "rollbackArtifacts",
                "Canonical absolute sibling rollback artifact paths, in deterministic order, that require explicit selection."));
      }
    },
    ROLLBACK_MISSING {
      @Override
      List<ContractResponse.FieldDescriptor> fields() {
        return List.of(
            field(
                "rollbackArtifact", "Canonical absolute path for the missing rollback artifact."));
      }
    },
    ROLLBACK_NOT_FOR_BOOK {
      @Override
      List<ContractResponse.FieldDescriptor> fields() {
        return List.of(
            field("bookFile", "Canonical absolute path for the selected live book file."),
            field(
                "rollbackArtifact", "Canonical absolute path for the rejected rollback artifact."));
      }
    };

    abstract List<ContractResponse.FieldDescriptor> fields();

    private static ContractResponse.FieldDescriptor field(String name, String description) {
      return new ContractResponse.FieldDescriptor(name, description);
    }
  }
}
