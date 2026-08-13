package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.runtime.FailureCategory;
import dev.erst.fingrind.contract.runtime.FieldDescriptor;
import dev.erst.fingrind.contract.runtime.RejectionDescriptor;
import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.Objects;

/** Descriptor owner for the closed family of published book-maintenance rejections. */
final class BookMaintenanceRejectionDescriptors {
  private BookMaintenanceRejectionDescriptors() {}

  static String wireCode(BookMaintenanceRejection rejection) {
    return descriptorFor(rejection).code();
  }

  static int exitCode(BookMaintenanceRejection rejection) {
    return descriptorFor(rejection).exitCode();
  }

  static List<RejectionDescriptor> descriptors() {
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
      case BookMaintenanceRejection.PairTargetsConflict _ -> Descriptor.PAIR_TARGETS_CONFLICT;
      case BookMaintenanceRejection.ArtifactPathInvalid _ -> Descriptor.ARTIFACT_PATH_INVALID;
      case BookMaintenanceRejection.ArtifactBusy _ -> Descriptor.ARTIFACT_BUSY;
      case BookMaintenanceRejection.BackupAcknowledgementConflict _ ->
          Descriptor.BACKUP_ACKNOWLEDGEMENT_CONFLICT;
      case BookMaintenanceRejection.BackupDestinationAlreadyExists _ ->
          Descriptor.BACKUP_DESTINATION_ALREADY_EXISTS;
      case BookMaintenanceRejection.SecretTargetOccupied _ -> Descriptor.SECRET_TARGET_OCCUPIED;
      case BookMaintenanceRejection.BookDestinationOccupied _ ->
          Descriptor.BOOK_DESTINATION_OCCUPIED;
      case BookMaintenanceRejection.RecoveryPending _ -> Descriptor.MAINTENANCE_RECOVERY_PENDING;
      case BookMaintenanceRejection.ArtifactVerificationFailed _ ->
          Descriptor.ARTIFACT_VERIFICATION_FAILED;
    };
  }

  /** Canonical maintenance rejection metadata keyed by stable wire code. */
  enum Descriptor {
    BOOK_HAS_BLOCKING_ARTIFACTS(
        "book-has-blocking-artifacts",
        7,
        "Maintenance command refused because the selected live book path has SQLite sidecars that prove the book is not in one clean closed-copy state.",
        FieldShape.BLOCKING_ARTIFACTS),
    BACKUP_SOURCE_HAS_BLOCKING_ARTIFACTS(
        "backup-source-has-blocking-artifacts",
        7,
        "Restore command refused because the selected encrypted backup file has SQLite sidecars and is not one clean closed-copy source.",
        FieldShape.BLOCKING_ARTIFACTS),
    BACKUP_SOURCE_MATCHES_LIVE_BOOK(
        "backup-source-matches-live-book",
        2,
        "Restore command refused because the selected backup source path equals the live book path and FinGrind will not replace a book from itself.",
        FieldShape.BACKUP_SOURCE_CONFLICT),
    PAIR_TARGETS_CONFLICT(
        "pair-targets-conflict",
        2,
        "Maintenance command refused because the selected protected-book and generated-secret targets resolve to one filesystem identity and cannot form two independent final members.",
        FieldShape.PAIR_TARGETS_CONFLICT),
    ARTIFACT_PATH_INVALID(
        "artifact-path-invalid",
        6,
        "Maintenance command refused because one selected artifact path or its parent-directory permissions do not satisfy the protected-book filesystem contract.",
        FieldShape.PATH_INVALID),
    ARTIFACT_BUSY(
        "artifact-busy",
        7,
        "Maintenance command refused because the selected protected-book artifact is actively in use by another workflow or process and cannot be proven quiescent.",
        FieldShape.ARTIFACT_BUSY),
    BACKUP_ACKNOWLEDGEMENT_CONFLICT(
        "backup-acknowledgement-conflict",
        7,
        "Backup acknowledgement refused because the supplied backup ID is already bound to a different immutable artifact tuple.",
        FieldShape.BACKUP_ACKNOWLEDGEMENT_CONFLICT),
    BACKUP_DESTINATION_ALREADY_EXISTS(
        "backup-destination-already-exists",
        7,
        "Backup command refused because the selected backup destination file already exists and FinGrind will not overwrite it.",
        FieldShape.BACKUP_DESTINATION),
    SECRET_TARGET_OCCUPIED(
        "secret-target-occupied",
        7,
        "Maintenance command refused because the selected generated-secret target already exists and FinGrind will not overwrite it.",
        FieldShape.SECRET_TARGET),
    BOOK_DESTINATION_OCCUPIED(
        "book-destination-occupied",
        7,
        "Restore command refused because the selected destination book already exists and FinGrind will not replace it.",
        FieldShape.BOOK_DESTINATION),
    MAINTENANCE_RECOVERY_PENDING(
        "maintenance-recovery-pending",
        7,
        "Maintenance command refused because a verified incomplete protected-book pair publication must be resumed only by its original operation and target pair before another request can proceed.",
        FieldShape.RECOVERY_PENDING),
    ARTIFACT_VERIFICATION_FAILED(
        "artifact-verification-failed",
        6,
        "Maintenance command refused because the selected protected-book artifact did not verify as one initialized FinGrind book for the requested workflow.",
        FieldShape.VERIFICATION);

    private final String code;
    private final int exitCode;
    private final String description;
    private final FieldShape fieldShape;

    Descriptor(String code, int exitCode, String description, FieldShape fieldShape) {
      this.code = code;
      this.exitCode = exitCode;
      this.description = description;
      this.fieldShape = fieldShape;
    }

    String code() {
      return code;
    }

    int exitCode() {
      return exitCode;
    }

    private RejectionDescriptor descriptor() {
      return new RejectionDescriptor(
          code,
          FailureCategory.PRECONDITION,
          exitCode,
          description,
          fieldShape.fields(),
          List.of());
    }

    private static List<RejectionDescriptor> descriptors() {
      return List.of(values()).stream().map(Descriptor::descriptor).toList();
    }
  }

  /** Reusable published field layouts for the closed maintenance rejection family. */
  private enum FieldShape {
    BLOCKING_ARTIFACTS {
      @Override
      List<FieldDescriptor> fields() {
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
      List<FieldDescriptor> fields() {
        return List.of(
            field("bookFile", "Canonical absolute path for the selected live book file."),
            field("backupFile", "Canonical absolute path for the conflicting backup source file."));
      }
    },
    PAIR_TARGETS_CONFLICT {
      @Override
      List<FieldDescriptor> fields() {
        return List.of(
            field(
                "bookTarget",
                "Normalized absolute submitted spelling for the selected protected-book final target."),
            field(
                "generatedSecretTarget",
                "Normalized absolute submitted spelling for the conflicting generated-secret final target; it can differ from bookTarget when the filesystem established a physical alias."));
      }
    },
    PATH_INVALID {
      @Override
      List<FieldDescriptor> fields() {
        return List.of(
            field(
                "artifactRole",
                artifactRoleDescription(
                    "Canonical maintenance artifact role whose selected path was invalid.")),
            field(
                "artifactPath", "Canonical absolute path for the invalid protected-book artifact."),
            field(
                "pathFailure",
                pathFailureDescription(
                    "Stable protected-book path-failure code naming the specific filesystem-contract violation.")));
      }
    },
    ARTIFACT_BUSY {
      @Override
      List<FieldDescriptor> fields() {
        return List.of(
            field(
                "artifactRole",
                artifactRoleDescription(
                    "Canonical maintenance artifact role that was actively in use.")),
            field("artifactPath", "Canonical absolute path for the busy protected-book artifact."));
      }
    },
    BACKUP_ACKNOWLEDGEMENT_CONFLICT {
      @Override
      List<FieldDescriptor> fields() {
        return List.of(
            field(
                "backupId", "Canonical UUID whose conflicting acknowledgement reuse was refused."));
      }
    },
    BACKUP_DESTINATION {
      @Override
      List<FieldDescriptor> fields() {
        return List.of(
            field("backupFile", "Canonical absolute path for the conflicting backup file."));
      }
    },
    SECRET_TARGET {
      @Override
      List<FieldDescriptor> fields() {
        return List.of(
            field(
                "secretTarget",
                "Canonical absolute path for the occupied generated-secret target."));
      }
    },
    BOOK_DESTINATION {
      @Override
      List<FieldDescriptor> fields() {
        return List.of(
            field(
                "bookFile", "Canonical absolute path for the selected existing destination book."));
      }
    },
    RECOVERY_PENDING {
      @Override
      List<FieldDescriptor> fields() {
        return List.of(
            field(
                "recoveryOperation",
                "Canonical operation identifier that must resume the retained protected-book pair publication. Closed wire vocabulary: "
                    + WireValue.wireValues(OperationId.class)
                    + "."),
            field(
                "bookTarget",
                "Canonical absolute target path for the retained protected-book pair book member."),
            field(
                "generatedSecretTarget",
                "Canonical absolute target path for the retained protected-book pair generated-secret member."));
      }
    },
    VERIFICATION {
      @Override
      List<FieldDescriptor> fields() {
        return List.of(
            field(
                "artifactRole",
                artifactRoleDescription(
                    "Stable public role for the protected-book artifact that failed verification.")),
            field(
                "artifactPath",
                "Canonical absolute path for the artifact that failed verification."),
            field(
                "verificationFailure",
                verificationFailureDescription(
                    "Stable public verification failure code for the rejected artifact.")));
      }
    };

    abstract List<FieldDescriptor> fields();

    private static FieldDescriptor field(String name, String description) {
      return new FieldDescriptor(name, description);
    }

    private static String artifactRoleDescription(String prefix) {
      return prefix + " Closed wire vocabulary: " + BookMaintenanceArtifactRole.wireValues() + ".";
    }

    private static String pathFailureDescription(String prefix) {
      return prefix + " Closed wire vocabulary: " + PublicationPathFailure.wireValues() + ".";
    }

    private static String verificationFailureDescription(String prefix) {
      return prefix
          + " Closed wire vocabulary: "
          + BookMaintenanceVerificationFailure.wireValues()
          + ".";
    }
  }
}
