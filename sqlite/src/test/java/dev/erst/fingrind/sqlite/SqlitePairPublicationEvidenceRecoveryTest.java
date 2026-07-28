package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import java.io.IOException;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Verifies that recovery evidence writing rejects a channel that cannot make progress. */
class SqlitePairPublicationEvidenceRecoveryTest extends SqliteArtifactPublicationTestSupport {

  @Test
  void writeNewFailsClosedWhenTheEvidenceChannelMakesNoProgress() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("posix"))) {
      AclFixturePath parent = fileSystem.path("\\evidence");
      parent.exists = true;
      parent.regularFile = false;
      parent.posixPermissions =
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE);
      AclFixturePath evidencePath = fileSystem.path("\\evidence\\pair.claim");
      evidencePath.returnZeroProgressFromNextWrite();
      var record =
          new SqliteProtectedBookPairPublicationRecord(
              new SqliteProtectedBookPairPublicationRecord.Components(
                  UUID.randomUUID(),
                  new SqliteProtectedBookPairPublicationRecord.PairPaths(
                      fileSystem.path("\\evidence\\book.sqlite"),
                      fileSystem.path("\\evidence\\book.key"),
                      fileSystem.path("\\evidence\\book.stage"),
                      fileSystem.path("\\evidence\\key.stage")),
                  new SqliteProtectedBookPairPublicationRecord.PairDigests(
                      new byte[32], new byte[32], null),
                  RestoredBookTargetPolicy.REQUIRE_ABSENT,
                  backupBinding(fileSystem.path("\\evidence\\source.sqlite"))));

      IOException failure =
          assertThrows(
              IOException.class,
              () ->
                  SqlitePairPublicationEvidenceRecovery.writeNew(
                      evidencePath, record, SqliteProtectedBookPairPublicationEvidenceKind.CLAIM));

      assertEquals(
          "Failed to write the complete protected-book pair recovery evidence.",
          failure.getMessage());
    }
  }
}
