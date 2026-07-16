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
  void reservationKeepsTheFinalPathAbsentUntilItPublishesTheOwnedStage() throws Exception {
    Path finalPath = tempDirectory.resolve("reservation").resolve("artifact.key");
    writeArtifact("reservation/parent-ready", "ready");

    SqliteOwnedStagedArtifact publishedStage;
    try (SqliteOwnedDestinationReservation reservation =
        SqliteOwnedDestinationReservation.reserve(finalPath)) {
      assertFalse(Files.exists(finalPath));
      assertFalse(SqliteOwnedStageRecord.findFor(finalPath).isEmpty());

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
  void unconsumedReservationReleasesItsDurableSidecarWithoutCreatingTheTarget() throws Exception {
    Path finalPath = tempDirectory.resolve("reservation-release").resolve("artifact.key");
    writeArtifact("reservation-release/parent-ready", "ready");

    try (SqliteOwnedDestinationReservation ignored =
        SqliteOwnedDestinationReservation.reserve(finalPath)) {
      assertFalse(Files.exists(finalPath));
      assertFalse(SqliteOwnedStageRecord.findFor(finalPath).isEmpty());
    }

    assertFalse(Files.exists(finalPath));
    assertTrue(SqliteOwnedStageRecord.findFor(finalPath).isEmpty());
  }

  @Test
  void reservationKeepsOneRecoverableSidecarStageWhileTheTargetStaysAbsent() throws Exception {
    Path finalPath = tempDirectory.resolve("reservation-recovery").resolve("artifact.key");
    writeArtifact("reservation-recovery/parent-ready", "ready");

    try (SqliteOwnedDestinationReservation ignored =
        SqliteOwnedDestinationReservation.reserve(finalPath)) {
      SqliteOwnedStageRecord record = SqliteOwnedStageRecord.findFor(finalPath).getFirst();
      assertTrue(Files.isRegularFile(record.stagedPath()));
      assertFalse(Files.exists(finalPath));
    }
  }

  @Test
  void reservationRejectsAnExternalTargetCreatedAfterTheSidecar() throws Exception {
    Path finalPath = tempDirectory.resolve("reservation-external").resolve("artifact.key");
    writeArtifact("reservation-external/parent-ready", "ready");

    try (SqliteOwnedDestinationReservation reservation =
        SqliteOwnedDestinationReservation.reserve(finalPath)) {
      SqliteOwnedStagedArtifact staged = reservation.createStage(".payload-", ".tmp");
      Files.writeString(staged.stagedPath(), "staged");
      Files.writeString(finalPath, "external");

      assertThrows(
          FileAlreadyExistsException.class,
          () -> reservation.publishRetainingStage(staged, Files::createLink));
      staged.discard();
    }
    assertEquals("external", Files.readString(finalPath));
  }

  @Test
  void reservationRejectsPublicationAfterItsDurableSidecarIsLost() throws Exception {
    Path finalPath = tempDirectory.resolve("reservation-lost").resolve("artifact.key");
    writeArtifact("reservation-lost/parent-ready", "ready");

    try (SqliteOwnedDestinationReservation reservation =
        SqliteOwnedDestinationReservation.reserve(finalPath)) {
      SqliteOwnedStageRecord record = SqliteOwnedStageRecord.findFor(finalPath).getFirst();
      SqliteOwnedStagedArtifact staged = reservation.createStage(".payload-", ".tmp");
      Files.writeString(staged.stagedPath(), "staged");
      Files.delete(record.stagedPath());

      assertThrows(
          IllegalStateException.class,
          () -> reservation.publishRetainingStage(staged, Files::createLink));
      staged.discard();
    }
  }

  @Test
  void reservationPublishesUnicodeAndLeadingDashTargetsWithoutMaterializingTheTargetEarly()
      throws Exception {
    Path finalPath =
        tempDirectory.resolve("Rīga büro").resolve("-entity backup [windows-smoke].sqlite");
    Files.createDirectories(finalPath.getParent());

    SqliteOwnedStagedArtifact publishedStage;
    try (SqliteOwnedDestinationReservation reservation =
        SqliteOwnedDestinationReservation.reserve(finalPath)) {
      Path reservationStage = SqliteOwnedStageRecord.findFor(finalPath).getFirst().stagedPath();
      assertTrue(Files.isRegularFile(reservationStage));
      assertFalse(Files.exists(finalPath));

      publishedStage = reservation.createStage(".payload-", ".sqlite");
      Files.writeString(publishedStage.stagedPath(), "published");
      reservation.publishRetainingStage(publishedStage, Files::createLink);
    }

    assertEquals("published", Files.readString(finalPath));
    publishedStage.discard();
    assertTrue(SqliteOwnedStageRecord.findFor(finalPath).isEmpty());
  }
}
