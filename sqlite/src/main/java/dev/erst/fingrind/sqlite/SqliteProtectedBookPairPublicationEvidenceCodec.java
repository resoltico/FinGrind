package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationBinding;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Strict v3 byte codec for immutable protected-book pair evidence copies. */
final class SqliteProtectedBookPairPublicationEvidenceCodec {
  private static final String MAGIC = "fingrind-protected-book-pair-publication-v3";
  private static final int MAXIMUM_EVIDENCE_LINES = 32;

  private SqliteProtectedBookPairPublicationEvidenceCodec() {}

  static String encoded(
      SqliteProtectedBookPairPublicationRecord record,
      SqliteProtectedBookPairPublicationEvidenceKind kind) {
    SqliteProtectedBookPairPublicationRecord checkedRecord =
        Objects.requireNonNull(record, "record");
    List<String> lines = new ArrayList<>();
    lines.add(MAGIC);
    lines.add("pair-id=" + checkedRecord.pairId);
    lines.add("evidence-kind=" + Objects.requireNonNull(kind, "kind").wireValue());
    lines.add("book-target=" + encodePath(checkedRecord.bookTargetPath));
    lines.add("secret-target=" + encodePath(checkedRecord.secretTargetPath));
    lines.add("book-stage=" + encodePath(checkedRecord.bookStagePath));
    lines.add("secret-stage=" + encodePath(checkedRecord.secretStagePath));
    lines.add("book-digest=" + HexFormat.of().formatHex(checkedRecord.bookDigest));
    lines.add("secret-digest=" + HexFormat.of().formatHex(checkedRecord.secretDigest));
    lines.add("book-target-policy=" + encodePolicy(checkedRecord.bookTargetPolicy));
    if (checkedRecord.replaceTargetDigest != null) {
      lines.add(
          "replace-target-digest=" + HexFormat.of().formatHex(checkedRecord.replaceTargetDigest));
    }
    SqliteProtectedBookPairPublicationBindingEvidenceCodec.encode(lines, checkedRecord.binding);
    lines.add("");
    return String.join("\n", lines);
  }

  static Optional<DecodedEvidence> read(Path candidate) {
    Optional<SqliteProtectedBookPairPublicationEvidenceKind> kind = evidenceKindFor(candidate);
    if (kind.isEmpty() || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
      return Optional.empty();
    }
    try {
      String content =
          new String(
              SqliteSecureRegularFileAccess.readAllBytesBounded(
                  candidate, SqliteSecureRegularFileAccess.MAXIMUM_RECOVERY_METADATA_BYTES),
              StandardCharsets.UTF_8);
      List<String> lines = content.lines().limit(MAXIMUM_EVIDENCE_LINES + 1).toList();
      if (lines.size() > MAXIMUM_EVIDENCE_LINES) {
        return Optional.empty();
      }
      if (lines.isEmpty() || !MAGIC.equals(lines.getFirst())) {
        return Optional.empty();
      }
      DecodedFields fields = DecodedFields.from(candidate.getFileSystem(), lines);
      SqliteProtectedBookPairPublicationEvidenceKind decodedKind = kind.orElseThrow();
      if (fields.kind() != decodedKind) {
        return Optional.empty();
      }
      SqliteProtectedBookPairPublicationRecord record = fields.toRecord();
      if (!encoded(record, decodedKind).equals(content)
          || !SqliteProtectedBookPathIdentity.containsNormalizedSpelling(
              record.evidencePaths(decodedKind), candidate)) {
        return Optional.empty();
      }
      return Optional.of(new DecodedEvidence(record, decodedKind));
    } catch (IOException | IllegalArgumentException | NullPointerException exception) {
      // Value-object constructors intentionally enforce non-null structural invariants. At this
      // untrusted evidence boundary, their failure means malformed current evidence, which the
      // caller classifies as unsafe rather than exposing an implementation exception.
      return Optional.empty();
    }
  }

  static String encodePath(Path path) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(path.toString().getBytes(StandardCharsets.UTF_8));
  }

  static Path decodePath(FileSystem fileSystem, String value) {
    if (value.isEmpty() || value.length() % 4 == 1 || !value.matches("[A-Za-z0-9_-]+")) {
      throw new IllegalArgumentException("Malformed encoded path.");
    }
    String decoded = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    if (decoded.indexOf('\u0000') >= 0) {
      throw new IllegalArgumentException("Encoded path contains a NUL character.");
    }
    return SqlitePairPublicationRecordIntegrity.normalized(
        Objects.requireNonNull(fileSystem, "fileSystem").getPath(decoded), "decoded path");
  }

  static byte[] decodeDigest(String value) {
    if (!value.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("Malformed SHA-256 digest.");
    }
    return HexFormat.of().parseHex(value);
  }

  static String required(Map<String, String> fields, String name) {
    String value = Objects.requireNonNull(fields, "fields").get(name);
    if (value == null || value.isEmpty()) {
      throw new IllegalArgumentException("Missing pair recovery record field " + name + ".");
    }
    return value;
  }

  private static Optional<SqliteProtectedBookPairPublicationEvidenceKind> evidenceKindFor(
      Path candidate) {
    String fileName =
        Objects.requireNonNull(candidate.getFileName(), "candidate fileName").toString();
    return SqliteProtectedBookPairPublicationEvidenceKind.fromCurrentFileName(fileName);
  }

  private static String encodePolicy(RestoredBookTargetPolicy policy) {
    return switch (Objects.requireNonNull(policy, "policy")) {
      case REQUIRE_ABSENT -> "require-absent";
      case REPLACE_SELECTED -> "replace-selected";
    };
  }

  private static RestoredBookTargetPolicy decodePolicy(String value) {
    return switch (value) {
      case "require-absent" -> RestoredBookTargetPolicy.REQUIRE_ABSENT;
      case "replace-selected" -> RestoredBookTargetPolicy.REPLACE_SELECTED;
      default -> throw new IllegalArgumentException("Unknown protected-book pair target policy.");
    };
  }

  record DecodedEvidence(
      SqliteProtectedBookPairPublicationRecord record,
      SqliteProtectedBookPairPublicationEvidenceKind kind) {
    DecodedEvidence {
      Objects.requireNonNull(record, "record");
      Objects.requireNonNull(kind, "kind");
    }
  }

  private record DecodedFields(
      UUID pairId,
      SqliteProtectedBookPairPublicationEvidenceKind kind,
      Path bookTargetPath,
      Path secretTargetPath,
      Path bookStagePath,
      Path secretStagePath,
      DigestBytes bookDigest,
      DigestBytes secretDigest,
      @org.jspecify.annotations.Nullable DigestBytes replaceTargetDigest,
      RestoredBookTargetPolicy bookTargetPolicy,
      ProtectedBookPairPublicationBinding binding) {

    static DecodedFields from(FileSystem fileSystem, List<String> lines) {
      Map<String, String> fields = fieldsFrom(lines);
      RestoredBookTargetPolicy policy = decodePolicy(required(fields, "book-target-policy"));
      byte @org.jspecify.annotations.Nullable [] replaceTargetDigest = null;
      if (policy == RestoredBookTargetPolicy.REPLACE_SELECTED) {
        replaceTargetDigest = decodeDigest(required(fields, "replace-target-digest"));
      } else {
        requireAbsent(fields, "replace-target-digest");
      }
      return new DecodedFields(
          UUID.fromString(required(fields, "pair-id")),
          SqliteProtectedBookPairPublicationEvidenceKind.fromWireValue(
              required(fields, "evidence-kind")),
          decodePairPath(fileSystem, required(fields, "book-target")),
          decodePairPath(fileSystem, required(fields, "secret-target")),
          decodePairPath(fileSystem, required(fields, "book-stage")),
          decodePairPath(fileSystem, required(fields, "secret-stage")),
          new DigestBytes(decodeDigest(required(fields, "book-digest"))),
          new DigestBytes(decodeDigest(required(fields, "secret-digest"))),
          replaceTargetDigest == null ? null : new DigestBytes(replaceTargetDigest),
          policy,
          SqliteProtectedBookPairPublicationBindingEvidenceCodec.decode(fileSystem, fields));
    }

    SqliteProtectedBookPairPublicationRecord toRecord() {
      return new SqliteProtectedBookPairPublicationRecord(
          new SqliteProtectedBookPairPublicationRecord.Components(
              pairId,
              new SqliteProtectedBookPairPublicationRecord.PairPaths(
                  bookTargetPath, secretTargetPath, bookStagePath, secretStagePath),
              new SqliteProtectedBookPairPublicationRecord.PairDigests(
                  bookDigest.bytes(),
                  secretDigest.bytes(),
                  replaceTargetDigest == null ? null : replaceTargetDigest.bytes()),
              bookTargetPolicy,
              binding));
    }

    private static Path decodePairPath(FileSystem fileSystem, String value) {
      Path path = decodePath(fileSystem, value);
      if (path.getParent() == null) {
        throw new IllegalArgumentException(
            "Protected-book pair evidence paths must have one canonical parent directory.");
      }
      return path;
    }

    private static Map<String, String> fieldsFrom(List<String> lines) {
      Map<String, String> fields = new ConcurrentHashMap<>();
      for (int index = 1; index < lines.size(); index++) {
        String line = lines.get(index);
        if (line.isEmpty()) {
          if (index != lines.size() - 1) {
            throw new IllegalArgumentException("Unexpected embedded empty record line.");
          }
          continue;
        }
        int separator = line.indexOf('=');
        if (separator <= 0
            || fields.put(line.substring(0, separator), line.substring(separator + 1)) != null) {
          throw new IllegalArgumentException("Malformed or duplicate pair recovery record field.");
        }
      }
      return fields;
    }

    private static void requireAbsent(Map<String, String> fields, String name) {
      if (fields.containsKey(name)) {
        throw new IllegalArgumentException("Unexpected pair recovery record field " + name + ".");
      }
    }
  }

  /** Immutable defensively copied digest value used only while decoding untrusted evidence. */
  private static final class DigestBytes {
    private final byte[] value;

    DigestBytes(byte[] value) {
      this.value = Objects.requireNonNull(value, "value").clone();
    }

    byte[] bytes() {
      return value.clone();
    }
  }
}
