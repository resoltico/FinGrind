package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.protocol.ProtocolBookAccessOptions;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookLiveAccessPathFailures;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException;
import java.util.Objects;

/** Projects admitted live-book path refusals into the public attestation-inspection contract. */
final class AttestationInspectionPathFailureMapper {
  private AttestationInspectionPathFailureMapper() {}

  /** Maps only the two caller-controlled live access paths admitted by attestation inspection. */
  static ContractFailure toContractFailure(ProtectedBookMaintenanceRejectionException exception) {
    ProtectedBookMaintenanceRejectionException checkedException =
        Objects.requireNonNull(exception, "exception");
    if (checkedException.rejection()
        instanceof ProtectedBookMaintenanceRejection.ArtifactPathInvalid invalidPath) {
      return mapInvalidPath(checkedException, invalidPath);
    }
    throw unexpectedInspectionRejection(checkedException);
  }

  private static ContractFailure mapInvalidPath(
      ProtectedBookMaintenanceRejectionException exception,
      ProtectedBookMaintenanceRejection.ArtifactPathInvalid invalidPath) {
    return switch (invalidPath.artifactRole()) {
      case LIVE_BOOK ->
          ProtectedBookLiveAccessPathFailures.bookFile(
              invalidPath.artifactPath(),
              invalidPath.pathFailure(),
              ProtocolBookAccessOptions.BOOK_FILE);
      case LIVE_BOOK_KEY_SOURCE ->
          ProtectedBookLiveAccessPathFailures.bookKeyFile(
              invalidPath.artifactPath(),
              invalidPath.pathFailure(),
              ProtocolBookAccessOptions.BOOK_KEY_FILE);
      case BACKUP_SOURCE,
          BACKUP_KEY_SOURCE,
          BACKUP_TARGET,
          BACKUP_KEY_TARGET,
          RESTORED_TARGET,
          NEW_BOOK_KEY_TARGET ->
          throw unexpectedInspectionRejection(exception);
    };
  }

  private static IllegalStateException unexpectedInspectionRejection(
      ProtectedBookMaintenanceRejectionException exception) {
    return new IllegalStateException(
        "Attestation inspection emitted a maintenance rejection outside its admitted live-book access boundary: "
            + exception.rejection(),
        exception);
  }
}
