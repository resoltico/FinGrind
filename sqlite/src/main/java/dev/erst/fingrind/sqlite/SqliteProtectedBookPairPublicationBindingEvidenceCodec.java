package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.core.attestation.AttestationBackupAcknowledgement;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationBinding;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationSourceIdentity;
import java.math.BigInteger;
import java.nio.file.FileSystem;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Encodes and validates the operation-specific immutable binding portion of v3 evidence. */
final class SqliteProtectedBookPairPublicationBindingEvidenceCodec {
  private SqliteProtectedBookPairPublicationBindingEvidenceCodec() {}

  static void encode(List<String> lines, ProtectedBookPairPublicationBinding binding) {
    List<String> checkedLines = Objects.requireNonNull(lines, "lines");
    switch (Objects.requireNonNull(binding, "binding")) {
      case ProtectedBookPairPublicationBinding.Backup backup -> {
        checkedLines.add("binding=backup");
        checkedLines.add(
            "source-book="
                + SqliteProtectedBookPairPublicationEvidenceCodec.encodePath(
                    backup.sourceBookPath()));
        encodeAcknowledgement(checkedLines, backup.acknowledgement());
      }
      case ProtectedBookPairPublicationBinding.Restore restore -> {
        checkedLines.add("binding=restore");
        checkedLines.add(
            "backup-artifact="
                + SqliteProtectedBookPairPublicationEvidenceCodec.encodePath(
                    restore.backupArtifactPath()));
        checkedLines.add(
            "backup-key="
                + SqliteProtectedBookPairPublicationEvidenceCodec.encodePath(
                    restore.backupKeyPath()));
        encodeAcknowledgement(checkedLines, restore.acknowledgement());
        encodeCommit(checkedLines, "operation-commit", restore.attestationCommit());
      }
      case ProtectedBookPairPublicationBinding.Rekey rekey -> {
        checkedLines.add("binding=rekey");
        encodeSourceIdentity(checkedLines, rekey.sourceIdentity());
        encodeCommit(checkedLines, "source-commit", rekey.sourceCommit());
        encodeCommit(checkedLines, "operation-commit", rekey.attestationCommit());
      }
    }
  }

  static ProtectedBookPairPublicationBinding decode(
      FileSystem fileSystem, Map<String, String> fields) {
    FileSystem checkedFileSystem = Objects.requireNonNull(fileSystem, "fileSystem");
    Map<String, String> checkedFields = Objects.requireNonNull(fields, "fields");
    return switch (SqliteProtectedBookPairPublicationEvidenceCodec.required(
        checkedFields, "binding")) {
      case "backup" -> decodeBackup(checkedFileSystem, checkedFields);
      case "restore" -> decodeRestore(checkedFileSystem, checkedFields);
      case "rekey" -> decodeRekey(checkedFileSystem, checkedFields);
      default -> throw new IllegalArgumentException("Unknown pair recovery binding kind.");
    };
  }

  private static ProtectedBookPairPublicationBinding.Backup decodeBackup(
      FileSystem fileSystem, Map<String, String> fields) {
    ensureOnly(
        fields,
        "pair-id",
        "evidence-kind",
        "book-target",
        "secret-target",
        "book-stage",
        "secret-stage",
        "book-digest",
        "secret-digest",
        "book-target-policy",
        "replace-target-digest",
        "binding",
        "source-book",
        "backup-id",
        "backup-digest",
        "source-order",
        "source-head");
    return new ProtectedBookPairPublicationBinding.Backup(
        SqliteProtectedBookPairPublicationEvidenceCodec.decodePath(
            fileSystem,
            SqliteProtectedBookPairPublicationEvidenceCodec.required(fields, "source-book")),
        decodeAcknowledgement(fields));
  }

  private static ProtectedBookPairPublicationBinding.Restore decodeRestore(
      FileSystem fileSystem, Map<String, String> fields) {
    ensureOnly(
        fields,
        "pair-id",
        "evidence-kind",
        "book-target",
        "secret-target",
        "book-stage",
        "secret-stage",
        "book-digest",
        "secret-digest",
        "book-target-policy",
        "replace-target-digest",
        "binding",
        "backup-artifact",
        "backup-key",
        "backup-id",
        "backup-digest",
        "source-order",
        "source-head",
        "operation-commit-order",
        "operation-commit-head");
    return new ProtectedBookPairPublicationBinding.Restore(
        SqliteProtectedBookPairPublicationEvidenceCodec.decodePath(
            fileSystem,
            SqliteProtectedBookPairPublicationEvidenceCodec.required(fields, "backup-artifact")),
        SqliteProtectedBookPairPublicationEvidenceCodec.decodePath(
            fileSystem,
            SqliteProtectedBookPairPublicationEvidenceCodec.required(fields, "backup-key")),
        decodeAcknowledgement(fields),
        decodeCommit(fields, "operation-commit"));
  }

  private static ProtectedBookPairPublicationBinding.Rekey decodeRekey(
      FileSystem fileSystem, Map<String, String> fields) {
    ensureOnly(
        fields,
        "pair-id",
        "evidence-kind",
        "book-target",
        "secret-target",
        "book-stage",
        "secret-stage",
        "book-digest",
        "secret-digest",
        "book-target-policy",
        "replace-target-digest",
        "binding",
        "source-book",
        "source-passphrase-kind",
        "source-key",
        "source-commit-order",
        "source-commit-head",
        "operation-commit-order",
        "operation-commit-head");
    ProtectedBookPairPublicationSourceIdentity.Kind sourceKind =
        ProtectedBookPairPublicationSourceIdentity.Kind.valueOf(
            SqliteProtectedBookPairPublicationEvidenceCodec.required(
                fields, "source-passphrase-kind"));
    java.nio.file.@org.jspecify.annotations.Nullable Path keyFilePath =
        switch (sourceKind) {
          case KEY_FILE ->
              SqliteProtectedBookPairPublicationEvidenceCodec.decodePath(
                  fileSystem,
                  SqliteProtectedBookPairPublicationEvidenceCodec.required(fields, "source-key"));
          case STANDARD_INPUT, INTERACTIVE_PROMPT -> {
            if (fields.containsKey("source-key")) {
              throw new IllegalArgumentException(
                  "Only a key-file rekey source may retain source-key evidence.");
            }
            yield null;
          }
        };
    return new ProtectedBookPairPublicationBinding.Rekey(
        new ProtectedBookPairPublicationSourceIdentity(
            SqliteProtectedBookPairPublicationEvidenceCodec.decodePath(
                fileSystem,
                SqliteProtectedBookPairPublicationEvidenceCodec.required(fields, "source-book")),
            sourceKind,
            keyFilePath),
        decodeCommit(fields, "source-commit"),
        decodeCommit(fields, "operation-commit"));
  }

  private static void encodeAcknowledgement(
      List<String> lines, AttestationBackupAcknowledgement acknowledgement) {
    lines.add("backup-id=" + acknowledgement.backupId());
    lines.add(
        "backup-digest="
            + java.util.HexFormat.of().formatHex(acknowledgement.backupArtifactDigest()));
    lines.add("source-order=" + acknowledgement.sourceOrder());
    lines.add(
        "source-head=" + java.util.HexFormat.of().formatHex(acknowledgement.sourceOperationHead()));
  }

  private static AttestationBackupAcknowledgement decodeAcknowledgement(
      Map<String, String> fields) {
    return new AttestationBackupAcknowledgement(
        UUID.fromString(
            SqliteProtectedBookPairPublicationEvidenceCodec.required(fields, "backup-id")),
        SqliteProtectedBookPairPublicationEvidenceCodec.decodeDigest(
            SqliteProtectedBookPairPublicationEvidenceCodec.required(fields, "backup-digest")),
        new BigInteger(
            SqliteProtectedBookPairPublicationEvidenceCodec.required(fields, "source-order")),
        SqliteProtectedBookPairPublicationEvidenceCodec.decodeDigest(
            SqliteProtectedBookPairPublicationEvidenceCodec.required(fields, "source-head")));
  }

  private static void encodeCommit(List<String> lines, String name, AttestationCommit commit) {
    lines.add(name + "-order=" + commit.operationOrder());
    lines.add(name + "-head=" + commit.operationHeadHex());
  }

  private static AttestationCommit decodeCommit(Map<String, String> fields, String prefix) {
    return new AttestationCommit(
        new BigInteger(
            SqliteProtectedBookPairPublicationEvidenceCodec.required(fields, prefix + "-order")),
        SqliteProtectedBookPairPublicationEvidenceCodec.required(fields, prefix + "-head"));
  }

  private static void encodeSourceIdentity(
      List<String> lines, ProtectedBookPairPublicationSourceIdentity identity) {
    lines.add(
        "source-book="
            + SqliteProtectedBookPairPublicationEvidenceCodec.encodePath(identity.bookPath()));
    lines.add("source-passphrase-kind=" + identity.passphraseSourceKind().name());
    java.nio.file.@org.jspecify.annotations.Nullable Path keyFilePath = identity.keyFilePath();
    if (keyFilePath != null) {
      lines.add(
          "source-key=" + SqliteProtectedBookPairPublicationEvidenceCodec.encodePath(keyFilePath));
    }
  }

  private static void ensureOnly(Map<String, String> fields, String... allowed) {
    if (!java.util.Set.of(allowed).containsAll(fields.keySet())) {
      throw new IllegalArgumentException(
          "Pair recovery record contains fields for another binding.");
    }
  }
}
