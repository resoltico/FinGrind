package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.core.attestation.AttestationBackupAcknowledgement;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationBinding;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationSourceIdentity;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies that immutable evidence accepts only its exact canonical wire representation. */
class SqliteProtectedBookPairPublicationEvidenceCodecTest {
  @TempDir Path tempDirectory;

  @Test
  void readsAnExactCanonicalBackupEvidenceCopy() throws Exception {
    SqliteProtectedBookPairPublicationRecord record = backupRecord();
    Path evidencePath = evidencePath(record);
    Files.writeString(
        evidencePath,
        SqliteProtectedBookPairPublicationEvidenceCodec.encoded(
            record, SqliteProtectedBookPairPublicationEvidenceKind.CLAIM));

    SqliteProtectedBookPairPublicationEvidenceCodec.DecodedEvidence decoded =
        SqliteProtectedBookPairPublicationEvidenceCodec.read(evidencePath).orElseThrow();

    assertEquals(SqliteProtectedBookPairPublicationEvidenceKind.CLAIM, decoded.kind());
    assertEquals(record.pairId, decoded.record().pairId);
    assertEquals(record.bookTargetPath, decoded.record().bookTargetPath);
    assertEquals(record.secretTargetPath, decoded.record().secretTargetPath);
    assertArrayEquals(record.bookDigest, decoded.record().bookDigest);
    assertArrayEquals(record.secretDigest, decoded.record().secretDigest);
  }

  @Test
  void readsAnExactCanonicalStandardInputRekeyEvidenceCopy() throws Exception {
    SqliteProtectedBookPairPublicationRecord record = rekeyRecord();
    Path evidencePath = evidencePath(record);
    Files.writeString(
        evidencePath,
        SqliteProtectedBookPairPublicationEvidenceCodec.encoded(
            record, SqliteProtectedBookPairPublicationEvidenceKind.CLAIM));

    SqliteProtectedBookPairPublicationEvidenceCodec.DecodedEvidence decoded =
        SqliteProtectedBookPairPublicationEvidenceCodec.read(evidencePath).orElseThrow();
    ProtectedBookPairPublicationBinding.Rekey binding =
        (ProtectedBookPairPublicationBinding.Rekey) decoded.record().binding;

    assertEquals(
        ProtectedBookPairPublicationSourceIdentity.Kind.STANDARD_INPUT,
        binding.sourceIdentity().passphraseSourceKind());
    assertEquals(null, binding.sourceIdentity().keyFilePath());
  }

  @Test
  void rejectsMalformedAndNonCanonicalCurrentEvidenceWithoutEscapingTheBoundary() throws Exception {
    SqliteProtectedBookPairPublicationRecord record = backupRecord();
    String canonical =
        SqliteProtectedBookPairPublicationEvidenceCodec.encoded(
            record, SqliteProtectedBookPairPublicationEvidenceKind.CLAIM);

    assertUnreadable(record, String.join("\n", Collections.nCopies(33, "oversized-record")));
    assertUnreadable(record, "");
    assertUnreadable(record, "not-fingrind-evidence\n");

    List<String> reorderedLines = new ArrayList<>(canonical.lines().toList());
    Collections.swap(reorderedLines, 3, 4);
    assertUnreadable(record, String.join("\n", reorderedLines) + "\n");

    assertUnreadable(
        record,
        canonical.replace("book-target-policy=require-absent", "book-target-policy=unrecognized"));
    assertUnreadable(record, canonical.replace("evidence-kind=claim", "evidence-kind=unexpected"));
    assertUnreadable(record, canonical.replaceFirst("book-target=", "\nbook-target="));
    assertUnreadable(record, canonical.replaceFirst("\n$", "\nbook-target=duplicate\n"));
    assertUnreadable(record, canonical + "\n");
    assertUnreadable(record, canonical.replaceFirst("book-target=", "="));
    assertUnreadable(
        record, canonical.replaceFirst("\n$", "\nreplace-target-digest=" + "0".repeat(64) + "\n"));
  }

  @Test
  void rejectsMalformedPrimitiveFieldsBeforeTheyCanBecomeEvidencePathsOrDigests() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SqliteProtectedBookPairPublicationEvidenceCodec.decodePath(
                tempDirectory.getFileSystem(), ""));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SqliteProtectedBookPairPublicationEvidenceCodec.decodePath(
                tempDirectory.getFileSystem(), "a"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SqliteProtectedBookPairPublicationEvidenceCodec.decodePath(
                tempDirectory.getFileSystem(), "bad="));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SqliteProtectedBookPairPublicationEvidenceCodec.decodePath(
                tempDirectory.getFileSystem(),
                Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString("\u0000".getBytes(StandardCharsets.UTF_8))));
    assertThrows(
        IllegalArgumentException.class,
        () -> SqliteProtectedBookPairPublicationEvidenceCodec.decodeDigest("0".repeat(63)));
    assertThrows(
        IllegalArgumentException.class,
        () -> SqliteProtectedBookPairPublicationEvidenceCodec.required(Map.of(), "pair-id"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            SqliteProtectedBookPairPublicationEvidenceCodec.required(
                Map.of("pair-id", ""), "pair-id"));
  }

  @Test
  void rejectsWrongBindingFieldsAndImpossibleRekeyKeyMaterial() throws Exception {
    SqliteProtectedBookPairPublicationRecord backup = backupRecord();
    String backupEvidence =
        SqliteProtectedBookPairPublicationEvidenceCodec.encoded(
            backup, SqliteProtectedBookPairPublicationEvidenceKind.CLAIM);
    assertUnreadable(backup, backupEvidence.replace("binding=backup", "binding=unexpected"));
    assertUnreadable(backup, backupEvidence.replaceFirst("\n$", "\nforeign-field=true\n"));

    SqliteProtectedBookPairPublicationRecord rekey = rekeyRecord();
    String rekeyEvidence =
        SqliteProtectedBookPairPublicationEvidenceCodec.encoded(
            rekey, SqliteProtectedBookPairPublicationEvidenceKind.CLAIM);
    assertUnreadable(
        rekey,
        rekeyEvidence.replaceFirst(
            "\n$",
            "\nsource-key="
                + SqliteProtectedBookPairPublicationEvidenceCodec.encodePath(
                    tempDirectory.resolve("unexpected-source.key"))
                + "\n"));
  }

  private void assertUnreadable(SqliteProtectedBookPairPublicationRecord record, String content)
      throws Exception {
    Path evidencePath = evidencePath(record);
    Files.writeString(evidencePath, content);

    assertFalse(SqliteProtectedBookPairPublicationEvidenceCodec.read(evidencePath).isPresent());
  }

  private Path evidencePath(SqliteProtectedBookPairPublicationRecord record) {
    return record.evidencePaths(SqliteProtectedBookPairPublicationEvidenceKind.CLAIM).getFirst();
  }

  private SqliteProtectedBookPairPublicationRecord backupRecord() {
    return record(
        new ProtectedBookPairPublicationBinding.Backup(
            tempDirectory.resolve("source.sqlite"),
            new AttestationBackupAcknowledgement(
                new UUID(0L, 1L), new byte[32], BigInteger.ZERO, new byte[32])));
  }

  private SqliteProtectedBookPairPublicationRecord rekeyRecord() {
    return record(
        new ProtectedBookPairPublicationBinding.Rekey(
            new ProtectedBookPairPublicationSourceIdentity(
                tempDirectory.resolve("source.sqlite"),
                ProtectedBookPairPublicationSourceIdentity.Kind.STANDARD_INPUT,
                null),
            new AttestationCommit(BigInteger.ZERO, "0".repeat(64)),
            new AttestationCommit(BigInteger.ONE, "1".repeat(64))));
  }

  private SqliteProtectedBookPairPublicationRecord record(
      ProtectedBookPairPublicationBinding binding) {
    return new SqliteProtectedBookPairPublicationRecord(
        new SqliteProtectedBookPairPublicationRecord.Components(
            new UUID(0L, 2L),
            new SqliteProtectedBookPairPublicationRecord.PairPaths(
                tempDirectory.resolve("book.sqlite"),
                tempDirectory.resolve("book.key"),
                tempDirectory.resolve(".book.stage"),
                tempDirectory.resolve(".book-key.stage")),
            new SqliteProtectedBookPairPublicationRecord.PairDigests(
                new byte[32], new byte[32], null),
            RestoredBookTargetPolicy.REQUIRE_ABSENT,
            binding));
  }
}
