package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Verifies exact-path reservations used before protected-book source inspection. */
class SqliteOwnedDestinationReservationTest
    extends SqliteProtectedBookMaintenanceStoreCoverageTestSupport {

  @Test
  void reservationClaimsTheFinalPathUntilItPublishesTheOwnedStage() throws Exception {
    Path finalPath = tempDirectory.resolve("reservation").resolve("artifact.key");
    writeArtifact("reservation/parent-ready", "ready");

    SqliteOwnedStagedArtifact publishedStage;
    try (SqliteOwnedDestinationReservation reservation =
        SqliteOwnedDestinationReservation.reserve(finalPath)) {
      assertTrue(Files.exists(finalPath));
      assertThrows(FileAlreadyExistsException.class, () -> Files.createFile(finalPath));

      publishedStage = reservation.createStage(".payload-", ".tmp");
      Files.writeString(publishedStage.stagedPath(), "published");
      reservation.publishRetainingStage(publishedStage, Files::createLink);

      assertEquals("published", Files.readString(finalPath));
      assertTrue(Files.isSameFile(finalPath, publishedStage.stagedPath()));
    }

    assertEquals("published", Files.readString(finalPath));
    publishedStage.discard();
    assertEquals("published", Files.readString(finalPath));
    assertTrue(SqliteOwnedStageRecord.findFor(finalPath).isEmpty());
  }

  @Test
  void unconsumedReservationReleasesBothItsClaimAndDurableStageRecord() throws Exception {
    Path finalPath = tempDirectory.resolve("reservation-release").resolve("artifact.key");
    writeArtifact("reservation-release/parent-ready", "ready");

    try (SqliteOwnedDestinationReservation ignored =
        SqliteOwnedDestinationReservation.reserve(finalPath)) {
      assertTrue(Files.exists(finalPath));
      assertFalse(SqliteOwnedStageRecord.findFor(finalPath).isEmpty());
    }

    assertFalse(Files.exists(finalPath));
    assertTrue(SqliteOwnedStageRecord.findFor(finalPath).isEmpty());
  }
}
