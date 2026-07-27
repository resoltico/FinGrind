package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Contract tests for the durable ownership record that guards stage recovery. */
class SqliteOwnedStageRecordTest {
  private static final String RECORD_MAGIC = "fingrind-maintenance-stage-v2";

  @TempDir Path tempDirectory;

  @BeforeEach
  void hardenTempDirectory() {
    tempDirectory =
        SqliteTestPrivateDirectorySupport.canonicalizeAndHardenOwnerOnlyDirectory(tempDirectory);
  }

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
    assertFalse(marker(finalPath, 1).getFileName().toString().contains("book.sqlite"));
    assertEquals(
        stagedPath.toAbsolutePath().normalize(),
        SqliteOwnedStageRecord.findFor(finalPath).getFirst().stagedPath());
    Path generatedStage = SqliteOwnedStageRecordCodec.stagedPath(finalPath, ".probe-", ".tmp");
    assertEquals(finalPath.getParent(), generatedStage.getParent());
    assertTrue(generatedStage.getFileName().toString().startsWith(".fingrind-stage.probe-"));
    assertTrue(generatedStage.getFileName().toString().endsWith(".tmp"));

    record.releaseRetained();
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
  void codec_refusesOversizedOwnerMetadataBeforeCreatingAnyOwnerRecord() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("basic"))) {
      String oversizedLeaf =
          "x".repeat(SqliteSecureRegularFileAccess.MAXIMUM_RECOVERY_METADATA_BYTES);
      AclFixturePath finalPath = fileSystem.path("\\records\\" + oversizedLeaf);
      AclFixturePath stagedPath = fileSystem.path("\\records\\stage.tmp");
      AclFixturePath ownerPath =
          (AclFixturePath) SqliteOwnedStageRecordCodec.recordPath(finalPath, token(8));

      IllegalStateException failure =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteOwnedStageRecordCodec.write(finalPath, stagedPath, () -> token(8)));

      assertTrue(NullTestSupport.messageOf(failure).contains("Failed to record"));
      assertFalse(ownerPath.existsValue());
    }
  }

  @Test
  void codec_failsClosedWhenOwnershipRecordWriteMakesNoProgress() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath parent = fileSystem.path("\\records");
      parent.exists = true;
      parent.regularFile = false;
      parent.posixPermissions =
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE);
      AclFixturePath finalPath = fileSystem.path("\\records\\book.sqlite");
      AclFixturePath stagedPath = fileSystem.path("\\records\\book.stage");
      AclFixturePath ownerPath =
          (AclFixturePath) SqliteOwnedStageRecordCodec.recordPath(finalPath, token(17));
      ownerPath.returnZeroProgressFromNextWrite();

      IllegalStateException failure =
          assertThrows(
              IllegalStateException.class,
              () -> SqliteOwnedStageRecordCodec.write(finalPath, stagedPath, () -> token(17)));

      assertTrue(NullTestSupport.messageOf(failure).contains("Failed to record"));
      assertTrue(ownerPath.existsValue());
      assertEquals(0, ownerPath.content().length);
    }
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
        tempDirectory.resolve(".fingrind-maintenance-stage-" + token(2) + ".not-owner");
    Files.writeString(wrongSuffix, recordContent(validTarget, validStage));
    assertTrue(SqliteOwnedStageRecordCodec.read(wrongSuffix, finalPath).isEmpty());
    Path wrongToken = tempDirectory.resolve(".fingrind-maintenance-stage-invalid.owner");
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
          fileSystem.path("\\records\\.fingrind-maintenance-stage-" + token(2) + ".owner");
      unreadableRecordPath.exists = true;
      unreadableRecordPath.regularFile = true;
      unreadableRecordPath.failNewByteChannelWith(new IOException("record read failure"));

      assertTrue(
          SqliteOwnedStageRecordCodec.read(unreadableRecordPath, unreadableFinalPath).isEmpty());
    }
  }

  @Test
  void ownerResidueScanner_leavesValidOpaqueRecordsInertButFailsClosedForRetiredTargetRecords()
      throws Exception {
    Path finalPath = finalPath();
    Path secondFinalPath = tempDirectory.resolve("second.book-key").toAbsolutePath().normalize();
    Path stagePath = tempDirectory.resolve("valid.stage");
    Files.writeString(stagePath, "stage bytes");
    SqliteOwnedStageRecord valid = SqliteOwnedStageRecord.recordExisting(finalPath, stagePath);

    assertFalse(SqliteOwnedStageRecord.hasUnsafeOwnerRecordResidue(finalPath, secondFinalPath));

    Path retired =
        tempDirectory.resolve(".book.sqlite.fingrind-maintenance-stage-" + token(9) + ".owner");
    Files.writeString(retired, "retired record bytes");

    assertTrue(SqliteOwnedStageRecord.hasUnsafeOwnerRecordResidue(finalPath, secondFinalPath));
    assertTrue(Files.exists(retired));
    valid.releaseRetained();
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
    record.releaseRetained();

    IllegalStateException exhausted =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteOwnedStageRecordCodec.write(finalPath, stagedPath, () -> token(3)));
    assertTrue(NullTestSupport.messageOf(exhausted).contains("Unable to record"));

    Path parentFile = Files.createFile(tempDirectory.resolve("record-parent-file"));
    SqliteCallerPathContractException writeFailure =
        assertThrows(
            SqliteCallerPathContractException.class,
            () ->
                SqliteOwnedStageRecordCodec.write(
                    parentFile.resolve("book.sqlite"), stagedPath, () -> token(5)));
    assertEquals(SqliteCallerPathFailure.PARENT_PATH_COLLISION, writeFailure.pathFailure());
  }

  @Test
  void record_reservesStagesAndRejectsUnsafeRelationships() throws Exception {
    Path finalPath = finalPath();
    SqliteOwnedStageRecord normal = SqliteOwnedStageRecord.create(finalPath, ".stage-", ".tmp");
    assertTrue(Files.isRegularFile(normal.stagedPath()));
    normal.releaseRetained();

    Path occupiedStage = Files.createFile(tempDirectory.resolve("occupied.stage"));
    Path freshStage = tempDirectory.resolve("fresh.stage");
    Iterator<Path> stagedPaths = List.of(occupiedStage, freshStage).iterator();
    SqliteOwnedStageRecord retried = SqliteOwnedStageRecord.create(finalPath, stagedPaths::next);
    assertEquals(freshStage, retried.stagedPath());
    retried.releaseRetained();

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

      SqliteCallerPathContractException stageFailure =
          assertThrows(
              SqliteCallerPathContractException.class,
              () -> SqliteOwnedStageRecord.create(aclFinalPath, () -> failingStage));
      assertEquals(SqliteCallerPathFailure.MISSING_PARENT_DIRECTORY, stageFailure.pathFailure());
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

      SqliteCallerPathContractException recordFailure =
          assertThrows(
              SqliteCallerPathContractException.class,
              () -> SqliteOwnedStageRecord.create(aclFinalPath, () -> stagedPath));

      assertEquals(SqliteCallerPathFailure.MISSING_PARENT_DIRECTORY, recordFailure.pathFailure());
    }
  }

  @Test
  void record_createsItsStageAndOwnerEvidenceOwnerOnlyAndPreservesTheStageModeWhenWritten()
      throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(
        tempDirectory.getFileSystem().supportedFileAttributeViews().contains("posix"),
        "This filesystem cannot expose POSIX creation permissions.");
    Path finalPath = finalPath();

    SqliteOwnedStageRecord record = SqliteOwnedStageRecord.create(finalPath, ".stage-", ".tmp");
    Path ownerRecordPath;
    try (var children = Files.list(tempDirectory)) {
      ownerRecordPath =
          children
              .filter(path -> path.getFileName().toString().endsWith(".owner"))
              .findFirst()
              .orElseThrow(() -> new AssertionError("Expected one durable owner record."));
    }

    assertEquals(
        Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
        Files.getPosixFilePermissions(record.stagedPath()));
    assertEquals(
        Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
        Files.getPosixFilePermissions(ownerRecordPath));
    Files.writeString(record.stagedPath(), "encrypted stage bytes");
    assertEquals(
        Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
        Files.getPosixFilePermissions(record.stagedPath()));
    record.releaseRetained();
  }

  @Test
  void record_findsAndRetainsOwnedStagesWithoutUnlinkingThem() throws Exception {
    Path finalPath = finalPath();
    Path stagedPath = tempDirectory.resolve("recorded.stage");
    Files.createFile(stagedPath);
    SqliteOwnedStageRecord recorded = SqliteOwnedStageRecord.recordExisting(finalPath, stagedPath);
    assertEquals(1, SqliteOwnedStageRecord.findFor(finalPath).size());
    recorded.releaseRetained();
    assertTrue(Files.exists(stagedPath));

    Path alteredStage = Files.createDirectory(tempDirectory.resolve("altered.stage"));
    Path alteredRecordPath = Files.createFile(tempDirectory.resolve("altered.owner"));
    new SqliteOwnedStageRecord(alteredStage, alteredRecordPath).releaseRetained();
    assertTrue(Files.isDirectory(alteredStage));
    assertTrue(Files.exists(alteredRecordPath));

    SqliteOwnedStagedArtifact artifact =
        SqliteOwnedStagedArtifact.create(finalPath, ".released-", ".tmp");
    artifact.releaseRetained();
    assertThrows(IllegalStateException.class, () -> artifact.requireIntactFor(finalPath));
    artifact.releaseRetained();
    assertTrue(
        SqliteOwnedStageRecord.findFor(tempDirectory.resolve("missing").resolve("book.sqlite"))
            .isEmpty());
  }

  @Test
  void record_retentionNeverAttemptsUnlinkAndStillReportsDirectoryEnumerationFailures() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("basic"))) {
      AclFixturePath failingStage = fileSystem.path("\\cleanup\\stage.tmp");
      failingStage.exists = true;
      failingStage.regularFile = true;
      failingStage.failDeleteIfExistsWith(new IOException("stage delete failure"));
      AclFixturePath recordPath = fileSystem.path("\\cleanup\\record.owner");
      recordPath.exists = true;
      recordPath.regularFile = true;
      SqliteOwnedStageRecord record = new SqliteOwnedStageRecord(failingStage, recordPath);

      record.releaseRetained();
      assertTrue(failingStage.existsValue());
      assertTrue(recordPath.existsValue());
      recordPath.failDeleteIfExistsWith(new IOException("record delete failure"));
      record.releaseRetained();

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
