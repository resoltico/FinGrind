package dev.erst.fingrind.core.attestation;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable, committed artifact inputs for the Slice 4 static verification corpus. */
final class AttestationStaticArtifactCorpusVectors {
  private static final AttestationStaticCorpus.PolicyFold BACKUP_AT_SOURCE_ORDER_THREE =
      new AttestationStaticCorpus.PolicyFold(
          BigInteger.valueOf(3), AttestationCapability.BACKUP, 1, 2, 2, 0, false);
  private static final Map<String, NegativeArtifact> NEGATIVE_ARTIFACTS =
      Map.ofEntries(
          negative("N-14a", "fa76fe6eafc393e35ca1823a5c2dbace1277bbd223be88953c082f01542d4641"),
          negative("N-14b", "80ffcd2c11d835b81f6ca8a01699b0833631eca10cbc3cd0239621d51d20ee59"),
          negative("N-14c", "a61c2181139d0e1c3c95a05518892bbcb1c23a787f5128e207da2059ef7026ff"),
          negative("N-14d", "3810cd4936a5d617308a6e818a833fb7e73c400e047141a6e29855fa4e3b97e5"));

  private AttestationStaticArtifactCorpusVectors() {}

  static Set<String> negativeIds() {
    return NEGATIVE_ARTIFACTS.keySet();
  }

  static AttestationStaticCorpus.Fixture negativeFixture(String id) {
    NegativeArtifact definition = require(id);
    NegativeMetadata metadata = negativeMetadata(id);
    if (!"B-05-artifact".equals(metadata.baseId())) {
      throw new IllegalStateException("Static negative artifact must name the B-05 base.");
    }
    AttestationStaticCorpus.Mutation mutation = mutation(id, metadata);
    negativeSource(id, definition, metadata);
    return AttestationStaticCorpus.fixture(
        id,
        AttestationStaticCorpusVectors.source(metadata.baseId()),
        mutation,
        BACKUP_AT_SOURCE_ORDER_THREE,
        AttestationStaticCorpus.VerificationScope.ARTIFACT,
        AttestationAuthorizationFailure.MANIFEST_INVALID);
  }

  /** Decodes B-05's committed snapshot bytes, never a separately chosen book fixture. */
  static AttestationBook decodeB05Snapshot(byte[] snapshot) {
    byte[] checkedSnapshot = Objects.requireNonNull(snapshot, "snapshot");
    if (!Arrays.equals(AttestationStaticCorpusVectors.source("B-02"), checkedSnapshot)) {
      throw new IllegalArgumentException(
          "B-05 snapshot bytes do not match their committed source.");
    }
    return AttestationCorpusResources.source("B-05-snapshot", checkedSnapshot).decode();
  }

  private static void negativeSource(
      String id, NegativeArtifact definition, NegativeMetadata metadata) {
    if (!metadata.sourceSha256().equals(definition.sourceSha256())) {
      throw new IllegalStateException(
          "Static negative source fingerprint is not independently pinned.");
    }
    byte[] source =
        mutation(id, metadata).apply(AttestationStaticCorpusVectors.source(metadata.baseId()));
    if (!AttestationHash.sha256(source).hex().equals(definition.sourceSha256())) {
      throw new IllegalStateException("Static corpus hash does not match negative " + id + ".");
    }
  }

  private static AttestationStaticCorpus.Mutation mutation(String id, NegativeMetadata metadata) {
    return AttestationStaticCorpus.Mutation.edits(
        AttestationStaticCorpus.Mutation.edit(
            metadata.offset(),
            metadata.replacedByteCount(),
            AttestationStaticCorpusResourceLoader.base64("negative/" + id + ".delta.b64")));
  }

  private static NegativeMetadata negativeMetadata(String id) {
    Map<String, String> fields =
        AttestationStaticCorpusResourceLoader.fields("negative/" + id + ".meta");
    return new NegativeMetadata(
        requiredField(fields, "base"),
        Integer.parseInt(requiredField(fields, "offset")),
        Integer.parseInt(requiredField(fields, "replacedByteCount")),
        requiredField(fields, "sourceSha256"));
  }

  private static String requiredField(Map<String, String> fields, String key) {
    String value = fields.get(key);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Static corpus metadata lacks " + key + ".");
    }
    return value;
  }

  private static NegativeArtifact require(String id) {
    NegativeArtifact value = NEGATIVE_ARTIFACTS.get(Objects.requireNonNull(id, "id"));
    if (value == null) {
      throw new IllegalArgumentException("Unknown static negative artifact: " + id);
    }
    return value;
  }

  private static Map.Entry<String, NegativeArtifact> negative(String id, String sourceSha256) {
    return Map.entry(id, new NegativeArtifact(sourceSha256));
  }

  private record NegativeArtifact(String sourceSha256) {
    NegativeArtifact {
      Objects.requireNonNull(sourceSha256, "sourceSha256");
    }
  }

  private record NegativeMetadata(
      String baseId, int offset, int replacedByteCount, String sourceSha256) {
    NegativeMetadata {
      Objects.requireNonNull(baseId, "baseId");
      if (offset < 0 || replacedByteCount < 0) {
        throw new IllegalArgumentException("Static negative mutation bounds must be non-negative.");
      }
      Objects.requireNonNull(sourceSha256, "sourceSha256");
    }
  }
}
