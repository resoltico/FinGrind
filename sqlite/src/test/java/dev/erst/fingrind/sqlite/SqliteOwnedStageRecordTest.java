package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Contract tests for the durable ownership record that guards stage recovery. */
class SqliteOwnedStageRecordTest {
  private static final String RECORD_MAGIC = "fingrind-maintenance-stage-v1";

  @TempDir Path tempDirectory;

  @Test
  void codec_readsOneValidCanonicalRecord() throws Exception {
    Path finalPath = finalPath();
    Path stagedPath = tempDirectory.resolve("book.stage");
    SqliteOwnedStageRecord record =
        SqliteOwnedStageRecordCodec.write(finalPath, stagedPath, () -> token(1));

    assertEquals(stagedPath.toAbsolutePath().normalize(), record.stagedPath());
    assertEquals(
        stagedPath.toAbsolutePath().normalize(),
        SqliteOwnedStageRecordCodec.read(marker(finalPath, 1), finalPath)
            .orElseThrow()
            .stagedPath());
    Path generatedStage = SqliteOwnedStageRecordCodec.stagedPath(finalPath, ".probe-", ".tmp");
    assertEquals(finalPath.getParent(), generatedStage.getParent());
    assertTrue(generatedStage.getFileName().toString().startsWith(".fingrind-stage.probe-"));
    assertTrue(generatedStage.getFileName().toString().endsWith(".tmp"));

    record.discard();
  }

  @Test
  void codec_keepsNativeSqliteStagesBoundedWhenFinalArtifactNamesAreLong() {
    String finalFileName = "long-artifact-" + "x".repeat(200) + ".sqlite";
    Path finalPath = tempDirectory.resolve(finalFileName);

    Path stagedPath = SqliteOwnedStageRecordCodec.stagedPath(finalPath, ".backup-", ".sqlite");
    String stagedFileName = stagedPath.getFileName().toString();

    assertEquals(finalPath.getParent(), stagedPath.getParent());
    assertFalse(stagedFileName.contains(finalFileName));
    assertTrue(stagedFileName.startsWith(".fingrind-stage.backup-"));
    assertTrue(stagedFileName.length() < 80);
  }

  @Test
  void codec_rejectsMalformedOrUnreadableRecords() throws Exception {
    Path finalPath = finalPath();
    Path stagedPath = tempDirectory.resolve("book.stage");
    Path recordPath = marker(finalPath, 2);
    String validTarget = "target=" + encoded(finalPath);
    String validStage = "stage=" + encoded(stagedPath);

    assertTrue(SqliteOwnedStageRecordCodec.read(finalPath, finalPath).isEmpty());
    Path wrongSuffix =
        tempDirectory.resolve(".book.sqlite.fingrind-maintenance-stage-" + token(2) + ".not-owner");
    Files.writeString(wrongSuffix, recordContent(validTarget, validStage));
    assertTrue(SqliteOwnedStageRecordCodec.read(wrongSuffix, finalPath).isEmpty());
    Path wrongToken =
        tempDirectory.resolve(".book.sqlite.fingrind-maintenance-stage-invalid.owner");
    Files.writeString(wrongToken, recordContent(validTarget, validStage));
    assertTrue(SqliteOwnedStageRecordCodec.read(wrongToken, finalPath).isEmpty());
    Files.createDirectory(recordPath);
    assertTrue(SqliteOwnedStageRecordCodec.read(recordPath, finalPath).isEmpty());
    Files.delete(recordPath);

    Files.writeString(recordPath, RECORD_MAGIC + "\n");
    assertTrue(SqliteOwnedStageRecordCodec.read(recordPath, finalPath).isEmpty());
    Files.writeString(recordPath, String.join("\n", "wrong magic", validTarget, validStage, ""));
    assertTrue(SqliteOwnedStageRecordCodec.read(recordPath, finalPath).isEmpty());
    Files.writeString(recordPath, recordContent("unexpected=" + encoded(finalPath), validStage));
    assertTrue(SqliteOwnedStageRecordCodec.read(recordPath, finalPath).isEmpty());
    Files.writeString(recordPath, recordContent("target=", validStage));
    assertTrue(SqliteOwnedStageRecordCodec.read(recordPath, finalPath).isEmpty());
    Files.writeString(recordPath, recordContent("target=A", validStage));
    assertTrue(SqliteOwnedStageRecordCodec.read(recordPath, finalPath).isEmpty());
    Files.writeString(recordPath, recordContent("target=!A", validStage));
    assertTrue(SqliteOwnedStageRecordCodec.read(recordPath, finalPath).isEmpty());
    Files.writeString(recordPath, recordContent("target=Aa0-_A", validStage));
    assertTrue(SqliteOwnedStageRecordCodec.read(recordPath, finalPath).isEmpty());
    Files.writeString(recordPath, recordContent("target=AA", validStage));
    assertTrue(SqliteOwnedStageRecordCodec.read(recordPath, finalPath).isEmpty());
    Files.writeString(
        recordPath,
        recordContent("target=" + encoded(tempDirectory.resolve("other.sqlite")), validStage));
    assertTrue(SqliteOwnedStageRecordCodec.read(recordPath, finalPath).isEmpty());
    Files.writeString(
        recordPath,
        recordContent(
            validTarget, "stage=" + encoded(tempDirectory.resolve("other").resolve("stage"))));
    assertTrue(SqliteOwnedStageRecordCodec.read(recordPath, finalPath).isEmpty());
    Files.writeString(recordPath, recordContent(validTarget, "unexpected=" + encoded(stagedPath)));
    assertTrue(SqliteOwnedStageRecordCodec.read(recordPath, finalPath).isEmpty());

    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("basic"))) {
      AclFixturePath unreadableFinalPath = fileSystem.path("\\records\\book.sqlite");
      AclFixturePath unreadableRecordPath =
          fileSystem.path(
              "\\records\\.book.sqlite.fingrind-maintenance-stage-" + token(2) + ".owner");
      unreadableRecordPath.exists = true;
      unreadableRecordPath.regularFile = true;
      unreadableRecordPath.failNewByteChannelWith(new IOException("record read failure"));

      assertTrue(
          SqliteOwnedStageRecordCodec.read(unreadableRecordPath, unreadableFinalPath).isEmpty());
    }
  }

  @Test
  void codec_retriesRecordNameCollisionsAndReportsReservationFailures() throws Exception {
    Path finalPath = finalPath();
    Path stagedPath = tempDirectory.resolve("book.stage");
    Files.writeString(marker(finalPath, 3), "occupied");
    Iterator<UUID> retryTokens = List.of(token(3), token(4)).iterator();

    SqliteOwnedStageRecord record =
        SqliteOwnedStageRecordCodec.write(finalPath, stagedPath, retryTokens::next);
    assertEquals(stagedPath.toAbsolutePath().normalize(), record.stagedPath());
    record.discard();

    IllegalStateException exhausted =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteOwnedStageRecordCodec.write(finalPath, stagedPath, () -> token(3)));
    assertTrue(NullTestSupport.messageOf(exhausted).contains("Unable to record"));

    Path parentFile = Files.createFile(tempDirectory.resolve("record-parent-file"));
    IllegalStateException writeFailure =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteOwnedStageRecordCodec.write(
                    parentFile.resolve("book.sqlite"), stagedPath, () -> token(5)));
    assertTrue(NullTestSupport.messageOf(writeFailure).contains("Failed to record"));
  }

  @Test
  void record_reservesStagesAndRejectsUnsafeRelationships() throws Exception {
    Path finalPath = finalPath();
    SqliteOwnedStageRecord normal = SqliteOwnedStageRecord.create(finalPath, ".stage-", ".tmp");
    assertTrue(Files.isRegularFile(normal.stagedPath()));
    normal.discard();

    Path occupiedStage = Files.createFile(tempDirectory.resolve("occupied.stage"));
    Path freshStage = tempDirectory.resolve("fresh.stage");
    Iterator<Path> stagedPaths = List.of(occupiedStage, freshStage).iterator();
    SqliteOwnedStageRecord retried = SqliteOwnedStageRecord.create(finalPath, stagedPaths::next);
    assertEquals(freshStage, retried.stagedPath());
    retried.discard();

    IllegalStateException exhausted =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteOwnedStageRecord.create(finalPath, () -> occupiedStage));
    assertTrue(NullTestSupport.messageOf(exhausted).contains("Unable to reserve"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SqliteOwnedStageRecord.recordExisting(
                finalPath, tempDirectory.resolve("other-parent").resolve("stage.tmp")));

    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("basic"))) {
      AclFixturePath aclFinalPath = fileSystem.path("\\stages\\book.sqlite");
      AclFixturePath failingStage = fileSystem.path("\\stages\\stage.tmp");
      failingStage.failNewByteChannelWith(new IOException("stage create failure"));

      IllegalStateException stageFailure =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteOwnedStageRecord.create(aclFinalPath, () -> failingStage));
      assertTrue(NullTestSupport.messageOf(stageFailure).contains("Failed to create"));
    }

    try (AclFixtureFileSystem fileSystem =
        AclFixtureFileSystem.withViews(Set.of("basic"))
            .onPathCreated(
                path -> {
                  if (path.toString().endsWith(".owner")) {
                    path.failNewByteChannelWith(new IOException("record creation failure"));
                  }
                })) {
      AclFixturePath aclFinalPath = fileSystem.path("\\stages\\book.sqlite");
      AclFixturePath stagedPath = fileSystem.path("\\stages\\stage.tmp");

      IllegalStateException recordFailure =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteOwnedStageRecord.create(aclFinalPath, () -> stagedPath));

      assertTrue(NullTestSupport.messageOf(recordFailure).contains("Failed to create"));
    }
  }

  @Test
  void record_findsAndDiscardsOnlySafeOwnedStages() throws Exception {
    Path finalPath = finalPath();
    Path stagedPath = tempDirectory.resolve("recorded.stage");
    Files.createFile(stagedPath);
    SqliteOwnedStageRecord recorded = SqliteOwnedStageRecord.recordExisting(finalPath, stagedPath);
    assertEquals(1, SqliteOwnedStageRecord.findFor(finalPath).size());
    recorded.discard();
    assertFalse(Files.exists(stagedPath));

    Path alteredStage = Files.createDirectory(tempDirectory.resolve("altered.stage"));
    Path alteredRecordPath = Files.createFile(tempDirectory.resolve("altered.owner"));
    new SqliteOwnedStageRecord(alteredStage, alteredRecordPath).discard();
    assertTrue(Files.isDirectory(alteredStage));
    assertFalse(Files.exists(alteredRecordPath));

    SqliteOwnedStagedArtifact artifact =
        SqliteOwnedStagedArtifact.create(finalPath, ".released-", ".tmp");
    artifact.discard();
    assertThrows(IllegalStateException.class, () -> artifact.requireIntactFor(finalPath));
    artifact.discard();
    SqliteProtectedBookStagingFiles.deleteQuietlyIfPresent(null);
    assertTrue(
        SqliteOwnedStageRecord.findFor(tempDirectory.resolve("missing").resolve("book.sqlite"))
            .isEmpty());
  }

  @Test
  void record_reportsFilesystemCleanupAndDirectoryEnumerationFailures() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("basic"))) {
      AclFixturePath failingStage = fileSystem.path("\\cleanup\\stage.tmp");
      failingStage.exists = true;
      failingStage.regularFile = true;
      failingStage.failDeleteIfExistsWith(new IOException("stage delete failure"));
      AclFixturePath recordPath = fileSystem.path("\\cleanup\\record.owner");
      recordPath.exists = true;
      recordPath.regularFile = true;
      SqliteOwnedStageRecord record = new SqliteOwnedStageRecord(failingStage, recordPath);

      assertThrows(IllegalStateException.class, record::discard);
      recordPath.failDeleteIfExistsWith(new IOException("record delete failure"));
      assertThrows(IllegalStateException.class, record::discardRecord);

      AclFixturePath finalPath = fileSystem.path("\\enumeration\\book.sqlite");
      AclFixturePath parent =
          (AclFixturePath) Objects.requireNonNull(finalPath.getParent(), "fixture parent");
      parent.exists = true;
      parent.failNewDirectoryStreamWith(new IOException("directory stream failure"));
      assertThrows(IllegalStateException.class, () -> SqliteOwnedStageRecord.findFor(finalPath));
    }
  }

  private Path finalPath() {
    return tempDirectory.resolve("book.sqlite").toAbsolutePath().normalize();
  }

  private Path marker(Path finalPath, int token) {
    return SqliteOwnedStageRecordCodec.recordPath(finalPath, token(token));
  }

  private static UUID token(int value) {
    return UUID.fromString(String.format("00000000-0000-0000-0000-%012d", value));
  }

  private static String encoded(Path path) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(
            path.toAbsolutePath().normalize().toString().getBytes(StandardCharsets.UTF_8));
  }

  private static String recordContent(String targetLine, String stageLine) {
    return String.join("\n", RECORD_MAGIC, targetLine, stageLine, "");
  }
}
