package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests single-owner lifecycle transitions while acquiring publication-capability witnesses. */
class SqlitePublicationCapabilityWitnessAcquisitionResourcesTest {
  private static final String ACQUISITION_COMPLETED_MESSAGE =
      "Publication-capability witness acquisition has completed.";

  @TempDir Path tempDirectory;

  @Test
  void acquireRejectsReuseAfterTheAcquisitionOwnerCloses() {
    try (SqlitePublicationCapabilityWitnessAcquisitionResources resources =
        new SqlitePublicationCapabilityWitnessAcquisitionResources()) {
      resources.close();

      IllegalStateException failure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  resources.acquire(
                      entry(),
                      List.of(tempDirectory.resolve("same-parent-target")),
                      (ignoredTarget, ignoredStage) -> {},
                      (ignoredSource, ignoredTarget) -> {},
                      (ignoredPath, ignoredMagic) -> {},
                      ignoredParent -> {}));

      assertEquals(ACQUISITION_COMPLETED_MESSAGE, failure.getMessage());
    }
  }

  @Test
  void transferRejectsASecondTransferAfterWitnessOwnershipMoves() {
    SqlitePublicationCapabilityWitnessAcquisitionResources resources =
        new SqlitePublicationCapabilityWitnessAcquisitionResources();
    try (SqlitePublicationCapabilityWitness.Set _ = resources.transferToWitnessSet()) {
      IllegalStateException failure =
          assertThrows(IllegalStateException.class, resources::transferToWitnessSet);

      assertEquals(ACQUISITION_COMPLETED_MESSAGE, failure.getMessage());
    }
  }

  private SqlitePublicationCapabilityWitnessPlan.Entry entry() {
    Path targetPath = tempDirectory.resolve("admitted-target");
    SqlitePublicationCapabilityWitness.Requirement requirement =
        SqlitePublicationCapabilityWitness.Requirement.noReplace(targetPath);
    SqlitePublicationCapabilityWitnessKey key =
        new SqlitePublicationCapabilityWitnessKey(
            tempDirectory,
            "test-parent-fingerprint",
            SqlitePublicationCapabilityWitness.PrimitiveKind.NO_REPLACE_LINK);
    return new SqlitePublicationCapabilityWitnessPlan.Entry(key, List.of(requirement));
  }
}
