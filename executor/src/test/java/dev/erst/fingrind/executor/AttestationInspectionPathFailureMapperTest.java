package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenancePathFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Covers the strict live-access boundary of attestation-inspection path error projection. */
class AttestationInspectionPathFailureMapperTest {
  private static final Path BOOK = Path.of("inspection", "book.sqlite");

  @Test
  void mapsTheOnlyTwoInspectionControlledArtifactRoles() {
    ContractFailure bookFailure =
        AttestationInspectionPathFailureMapper.toContractFailure(
            invalidPath(ProtectedBookMaintenanceArtifactRole.LIVE_BOOK));
    ContractFailure keyFailure =
        AttestationInspectionPathFailureMapper.toContractFailure(
            invalidPath(ProtectedBookMaintenanceArtifactRole.LIVE_BOOK_KEY_SOURCE));

    assertEquals("--book-file", bookFailure.argument());
    assertEquals("--book-key-file", keyFailure.argument());
  }

  @Test
  @org.jspecify.annotations.NullUnmarked
  void rejectsNullAndNoninspectionMaintenanceRejections() {
    assertThrows(
        NullPointerException.class,
        () -> AttestationInspectionPathFailureMapper.toContractFailure(null));
    assertThrows(
        IllegalStateException.class,
        () ->
            AttestationInspectionPathFailureMapper.toContractFailure(
                new ProtectedBookMaintenanceRejectionException(
                    new ProtectedBookMaintenanceRejection.ArtifactBusy(
                        ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE, BOOK))));
    assertThrows(
        IllegalStateException.class,
        () ->
            AttestationInspectionPathFailureMapper.toContractFailure(
                invalidPath(ProtectedBookMaintenanceArtifactRole.BACKUP_SOURCE)));
  }

  private static ProtectedBookMaintenanceRejectionException invalidPath(
      ProtectedBookMaintenanceArtifactRole role) {
    return new ProtectedBookMaintenanceRejectionException(
        new ProtectedBookMaintenanceRejection.ArtifactPathInvalid(
            role, BOOK, ProtectedBookMaintenancePathFailure.MISSING_PARENT_DIRECTORY));
  }
}
