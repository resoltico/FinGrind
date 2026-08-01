package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationAdmission;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationRecoveryRequest;
import java.nio.file.Path;
import java.util.Objects;

/**
 * SQLite implementation of one immutable source-and-pair maintenance workflow scope.
 *
 * <p>Its source lease remains held until the workflow closes. Pair admission retains independent
 * exact target references for a prepared publication, after which this scope releases only the
 * target admission placeholders. That prevents a source-and-target workflow from gaining ambient
 * authority over unrelated siblings while preserving the source lock through staging and commit.
 */
final class SqliteProtectedBookWorkflowScope
    implements ProtectedBookMaintenanceStore.HeldWorkflowScope {
  private final SqliteWorkflowLeaseScope leaseScope;
  private final SqliteProtectedBookPairPublicationPreparation pairPublicationPreparation;
  private final Path bookTargetPath;
  private final Path secretTargetPath;
  private final ProtectedBookMaintenanceArtifactRole bookTargetArtifactRole;
  private final ProtectedBookMaintenanceArtifactRole secretTargetArtifactRole;
  private boolean admitted;
  private boolean closed;

  SqliteProtectedBookWorkflowScope(
      SqliteWorkflowLeaseScope leaseScope,
      SqliteProtectedBookPairPublicationPreparation pairPublicationPreparation,
      Path bookTargetPath,
      Path secretTargetPath,
      ProtectedBookMaintenanceArtifactRole bookTargetArtifactRole,
      ProtectedBookMaintenanceArtifactRole secretTargetArtifactRole) {
    this.leaseScope = Objects.requireNonNull(leaseScope, "leaseScope");
    this.pairPublicationPreparation =
        Objects.requireNonNull(pairPublicationPreparation, "pairPublicationPreparation");
    this.bookTargetPath = Objects.requireNonNull(bookTargetPath, "bookTargetPath");
    this.secretTargetPath = Objects.requireNonNull(secretTargetPath, "secretTargetPath");
    this.bookTargetArtifactRole =
        Objects.requireNonNull(bookTargetArtifactRole, "bookTargetArtifactRole");
    this.secretTargetArtifactRole =
        Objects.requireNonNull(secretTargetArtifactRole, "secretTargetArtifactRole");
  }

  @Override
  public Path artifactPath() {
    return leaseScope.sourceArtifactPath();
  }

  @Override
  public ProtectedBookPairPublicationAdmission admitPairPublication(
      RestoredBookTargetPolicy bookTargetPolicy,
      ProtectedBookPairPublicationRecoveryRequest request) {
    requireOpenAndUnadmitted();
    admitted = true;
    try (SqliteTargetAdmissionLeases targetAdmissionLeases =
        leaseScope.takeTargetAdmissionLeases()) {
      return pairPublicationPreparation.admit(
          bookTargetPath,
          secretTargetPath,
          Objects.requireNonNull(bookTargetPolicy, "bookTargetPolicy"),
          Objects.requireNonNull(request, "request"),
          bookTargetArtifactRole,
          secretTargetArtifactRole,
          targetAdmissionLeases);
    }
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    leaseScope.close();
  }

  private void requireOpenAndUnadmitted() {
    if (closed) {
      throw new IllegalStateException("The FinGrind maintenance workflow scope is already closed.");
    }
    if (admitted) {
      throw new IllegalStateException(
          "The FinGrind maintenance workflow scope has already admitted its exact target pair.");
    }
  }
}
