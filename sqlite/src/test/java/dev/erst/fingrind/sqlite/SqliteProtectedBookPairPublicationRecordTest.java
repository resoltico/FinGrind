package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Verifies that immutable pair evidence retains the operation family that created it. */
class SqliteProtectedBookPairPublicationRecordTest extends SqliteArtifactPublicationTestSupport {

  @Test
  void digestRejectsZeroByteReadsRatherThanSpinning() throws IOException {
    try (InputStream input =
        new InputStream() {
          @Override
          public int read(byte[] buffer, int offset, int length) {
            return 0;
          }

          @Override
          public int read() {
            throw new UnsupportedOperationException("byte-wise reads are not used by this test");
          }
        }) {
      IOException exception =
          assertThrows(
              IOException.class,
              () -> SqliteProtectedBookPairPublicationRecord.digest(input, "pair evidence"));

      assertEquals("The pair evidence did not make read progress.", exception.getMessage());
    }
  }

  @Test
  void recoveryOperationDistinguishesRestoreAndRekeyEvidence() {
    Path parent = tempDirectory.resolve("recovery-operation");
    Path bookTarget = parent.resolve("book.sqlite");
    Path secretTarget = parent.resolve("book.key");
    Path bookStage = parent.resolve(".book.stage");
    Path secretStage = parent.resolve(".secret.stage");

    assertEquals(
        OperationId.RESTORE_BOOK,
        record(
                bookTarget,
                secretTarget,
                bookStage,
                secretStage,
                restoreBinding(parent.resolve("backup.sqlite"), parent.resolve("backup.key")))
            .recoveryOperation());
    assertEquals(
        OperationId.REKEY_BOOK,
        record(
                bookTarget,
                secretTarget,
                bookStage,
                secretStage,
                rekeyBinding(bookTarget, parent.resolve("source.key")))
            .recoveryOperation());
  }

  @Test
  void replacementAndImmutableIdentityChecksDistinguishTheirTwoPolicies() throws Exception {
    Path parent = tempDirectory.resolve("immutable-identity");
    Files.createDirectories(parent);
    Path bookTarget = parent.resolve("book.sqlite");
    Path secretTarget = parent.resolve("book.key");
    Path bookStage = parent.resolve(".book.stage");
    Path secretStage = parent.resolve(".secret.stage");
    byte[] selectedBook = "selected-book".getBytes(StandardCharsets.UTF_8);
    Files.write(bookTarget, selectedBook);

    UUID pairId = UUID.randomUUID();
    var paths =
        new SqliteProtectedBookPairPublicationRecord.PairPaths(
            bookTarget, secretTarget, bookStage, secretStage);
    var binding = backupBinding(parent.resolve("backup.sqlite"));
    var selected =
        record(
            pairId,
            paths,
            new SqliteProtectedBookPairPublicationRecord.PairDigests(
                new byte[32], new byte[32], MessageDigest.getInstance("SHA-256").digest(selectedBook)),
            RestoredBookTargetPolicy.REPLACE_SELECTED,
            binding);
    var same =
        record(
            pairId,
            paths,
            new SqliteProtectedBookPairPublicationRecord.PairDigests(
                new byte[32], new byte[32], MessageDigest.getInstance("SHA-256").digest(selectedBook)),
            RestoredBookTargetPolicy.REPLACE_SELECTED,
            binding);
    var differentPair =
        record(
            UUID.randomUUID(),
            paths,
            new SqliteProtectedBookPairPublicationRecord.PairDigests(
                new byte[32], new byte[32], MessageDigest.getInstance("SHA-256").digest(selectedBook)),
            RestoredBookTargetPolicy.REPLACE_SELECTED,
            binding);

    assertEquals(true, selected.replaceTargetMatches());
    assertEquals(true, selected.sameImmutableRecord(same));
    assertEquals(false, selected.sameImmutableRecord(differentPair));
    assertEquals(
        false,
        record(
                bookTarget,
                secretTarget,
                bookStage,
                secretStage,
                binding)
            .replaceTargetMatches());
  }

  private static SqliteProtectedBookPairPublicationRecord record(
      Path bookTarget,
      Path secretTarget,
      Path bookStage,
      Path secretStage,
      dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationBinding binding) {
    return record(
        UUID.randomUUID(),
        new SqliteProtectedBookPairPublicationRecord.PairPaths(
            bookTarget, secretTarget, bookStage, secretStage),
        new SqliteProtectedBookPairPublicationRecord.PairDigests(new byte[32], new byte[32], null),
        RestoredBookTargetPolicy.REQUIRE_ABSENT,
        binding);
  }

  private static SqliteProtectedBookPairPublicationRecord record(
      UUID pairId,
      SqliteProtectedBookPairPublicationRecord.PairPaths paths,
      SqliteProtectedBookPairPublicationRecord.PairDigests digests,
      RestoredBookTargetPolicy targetPolicy,
      dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationBinding binding) {
    return new SqliteProtectedBookPairPublicationRecord(
        new SqliteProtectedBookPairPublicationRecord.Components(
            pairId, paths, digests, targetPolicy, binding));
  }
}
