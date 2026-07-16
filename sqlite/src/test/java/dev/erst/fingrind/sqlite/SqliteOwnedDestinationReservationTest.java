package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
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

  @Test
  void reservationMarkerRemainsRecoverableFromItsDurableStageRecord() throws Exception {
    Path finalPath = tempDirectory.resolve("reservation-recovery").resolve("artifact.key");
    writeArtifact("reservation-recovery/parent-ready", "ready");

    try (SqliteOwnedDestinationReservation ignored =
        SqliteOwnedDestinationReservation.reserve(finalPath)) {
      SqliteOwnedStageRecord record = SqliteOwnedStageRecord.findFor(finalPath).getFirst();
      assertTrue(
          SqliteOwnedDestinationReservation.isReservationMarker(finalPath, record.stagedPath()));
    }
  }

  @Test
  void reservationMarkerRejectsAnAlteredSameLengthClaim() throws Exception {
    Path finalPath = tempDirectory.resolve("reservation-altered").resolve("artifact.key");
    writeArtifact("reservation-altered/parent-ready", "ready");

    try (SqliteOwnedDestinationReservation ignored =
        SqliteOwnedDestinationReservation.reserve(finalPath)) {
      SqliteOwnedStageRecord record = SqliteOwnedStageRecord.findFor(finalPath).getFirst();
      byte[] alteredMarker = Files.readAllBytes(finalPath);
      alteredMarker[alteredMarker.length - 1] ^= 1;
      Files.write(finalPath, alteredMarker);

      assertFalse(
          SqliteOwnedDestinationReservation.isReservationMarker(finalPath, record.stagedPath()));
    }
  }

  @Test
  void reservationMarkerSurfacesUnreadableClaimInspection() throws Exception {
    Path finalPath = tempDirectory.resolve("reservation-unreadable").resolve("artifact.key");
    writeArtifact("reservation-unreadable/parent-ready", "ready");
    Assumptions.assumeTrue(
        Files.getFileAttributeView(finalPath.getParent(), PosixFileAttributeView.class) != null,
        "The unreadable-marker probe requires POSIX file permissions.");

    try (SqliteOwnedDestinationReservation ignored =
        SqliteOwnedDestinationReservation.reserve(finalPath)) {
      SqliteOwnedStageRecord record = SqliteOwnedStageRecord.findFor(finalPath).getFirst();
      Set<PosixFilePermission> originalPermissions = Files.getPosixFilePermissions(finalPath);
      Files.setPosixFilePermissions(finalPath, Set.of());
      try {
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteOwnedDestinationReservation.isReservationMarker(
                    finalPath, record.stagedPath()));
      } finally {
        Files.setPosixFilePermissions(finalPath, originalPermissions);
      }
    }
  }

  @Test
  void reservationPublishesUnicodeAndLeadingDashTargetsWithoutUsingTheMarkerAsTheStage()
      throws Exception {
    Path finalPath =
        tempDirectory.resolve("Rīga büro").resolve("-entity backup [windows-smoke].sqlite");
    Files.createDirectories(finalPath.getParent());

    SqliteOwnedStagedArtifact publishedStage;
    try (SqliteOwnedDestinationReservation reservation =
        SqliteOwnedDestinationReservation.reserve(finalPath)) {
      Path reservationStage = SqliteOwnedStageRecord.findFor(finalPath).getFirst().stagedPath();
      assertFalse(Files.isSameFile(finalPath, reservationStage));

      publishedStage = reservation.createStage(".payload-", ".sqlite");
      Files.writeString(publishedStage.stagedPath(), "published");
      reservation.publishRetainingStage(publishedStage, Files::createLink);
    }

    assertEquals("published", Files.readString(finalPath));
    publishedStage.discard();
    assertTrue(SqliteOwnedStageRecord.findFor(finalPath).isEmpty());
  }
}
