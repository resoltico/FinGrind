package dev.erst.fingrind.core.attestation;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable, committed inputs for the complete-book Slice 4 corpus.
 *
 * <p>These resources are consumed directly by verifier tests. Encoder and signing helpers may
 * author a future vector release, but they must never construct a verifier input here.
 */
final class AttestationStaticCorpusVectors {
  private static final Map<String, String> SOURCE_SHA256 =
      Map.ofEntries(
          Map.entry("B-01", "f31e017a7bee0930759acd9185b5ee656e8ddc04dda893bfbd3be5f8d1b15549"),
          Map.entry("B-02", "71c60316634958f786967b7a43452c84be7b4a0e1227a0e629d2f8209271ab1c"),
          Map.entry("B-03", "ac21bb3b01e3a834a227e31d64f606c56153b4215b53ee00022fd33f3507eb90"),
          Map.entry("B-04", "5063d4117286b8fc885a9abfb7e9d7f23f5f2e6114df4b53c952099cb81e12b5"),
          Map.entry(
              "B-05-artifact", "b92bba455ca0b086deb84c8e443e837e22ade75176a8153f1a09321a124323fe"),
          Map.entry(
              "B-05-book", "7abe7701c85cab52346165dbac6e056cd89cdf7ba50f0a24dedde0193e4f827d"),
          Map.entry("B-06", "b9b67cacfa87be40577b7ce0f7510ed514648f74ff633736e6795c6279fa3302"),
          Map.entry("B-07", "14681fc248385b6b96001e13e418c408f62cfd21940cda73fb538047058e0db0"),
          Map.entry("B-10", "f94e7c6c5c5f3dd32f3613d2a441d064e3ef2028381a336da3dd2a7cc90ec592"),
          Map.entry(
              "B-11-receipt", "6bdfc070fabc1415b634066bcddeee8cf1fd4a58f9bd39fde8c6a132402b010f"));
  private static final Map<String, String> NEGATIVE_SOURCE_SHA256 =
      Map.ofEntries(
          Map.entry("N-11", "fbc83f8f93d33905ccc91d1a82d2529f0977402d51f17a3dae3ed53a7ee61267"),
          Map.entry("N-12a", "f0cf33754b07a16b223348fa3c10feae171fd7a85a20bd110febbd4438d50e2e"),
          Map.entry("N-12b", "d3e42986090c3e166005d8e56be79bc58fcb8d5518e222b5265cc6e0584621b1"),
          Map.entry("N-16", "552e3a007c6aed431a487509a920764eb399b8d574abbc544d1f0e5c9daef06b"),
          Map.entry("N-17", "92390187f3d0d2076d63e367c58a545d0f1de5b7da0409620bebfbb3a0583c8f"),
          Map.entry("N-18", "bf4973306473f9d957ae8fa25ecb41a51efbd9515ed6461bf22860b3dc72744a"),
          Map.entry("N-19", "54a1ce360475d06ae4ade156f2f509e707d5b1a0a759c8e9895fe2d039fd006c"),
          Map.entry("N-20", "9a21f7c462037a94a42f3b520617e9b2cea4cf27c1fa6cb5c86123681907c27b"),
          Map.entry("N-21", "fbb5daabad2e28ba20f846332b83d7ea2401eb588039ebf8e626d22ad1a164a1"),
          Map.entry("N-22", "10b4b28d75097abaa4fb0b3f6454d5bb0635376dddd705088936680b0290eb7a"),
          Map.entry("N-23", "26d60bd4ea9d16887db712b835e05c39db19f2625d1da06e4d7b7d067b7a9a73"),
          Map.entry("N-24", "65c801937b7fec89dec54f141602f7e5bba3f73edf3c84e3a6edd9153681ab7a"),
          Map.entry("N-25", "a09875306c25dc7c01c4f23b4480a8c020b212b055cc6265ebaa271e4944f052"),
          Map.entry("N-26", "da4cad368b125e19bffe0673bed7bc2caac77c7d48dd50aa290ac462cd018ca9"),
          Map.entry("N-27", "9313454c3e632d8df35215b9f72cd7a71b1fce089c72bf1078184d7f7a49796f"));
  private static final Map<String, PositiveBook> POSITIVE_BOOKS = positiveBooks();
  private static final Map<String, NegativeBook> NEGATIVE_BOOKS = negativeBooks();
  private static final UUID PRINCIPAL_A = UUID.fromString("10213243-5465-7687-98a9-babcbddceeff");
  private static final UUID PRINCIPAL_B = UUID.fromString("11223344-5566-7788-99aa-bbccddeeff00");
  private static final UUID PRINCIPAL_C = UUID.fromString("22334455-6677-8899-aabb-ccddeeff0011");

  static {
    if (!NEGATIVE_SOURCE_SHA256.keySet().equals(NEGATIVE_BOOKS.keySet())) {
      throw new IllegalStateException(
          "Static negative source fingerprints must cover every vector.");
    }
  }

  private AttestationStaticCorpusVectors() {}

  static Set<String> positiveBookIds() {
    return POSITIVE_BOOKS.keySet();
  }

  static Set<String> sourceIds() {
    return SOURCE_SHA256.keySet();
  }

  static Set<String> negativeBookIds() {
    return NEGATIVE_BOOKS.keySet();
  }

  static AttestationCorpusResources.Book book(String id) {
    return AttestationCorpusResources.source(id, bookSource(id));
  }

  static AttestationCorpusResources.Artifact artifactB05() {
    return new AttestationCorpusResources.Artifact("B-05", source("B-05-artifact"));
  }

  static AttestationCorpusResources.Receipt receiptB11() {
    return new AttestationCorpusResources.Receipt("B-11", book("B-02"), source("B-11-receipt"));
  }

  static AttestationStaticCorpus.Fixture positiveBookFixture(String id) {
    PositiveBook definition = require(POSITIVE_BOOKS, id, "positive book");
    return AttestationStaticCorpus.positive(
        id,
        source(definition.sourceId()),
        definition.policy().fold(),
        AttestationStaticCorpus.VerificationScope.BOOK);
  }

  static AttestationStaticCorpus.Fixture negativeBookFixture(String id) {
    NegativeBook definition = require(NEGATIVE_BOOKS, id, "negative book");
    NegativeMetadata metadata = negativeMetadata(id);
    if (!metadata.baseId().equals(definition.baseId())) {
      throw new IllegalStateException("Static negative base does not match its registered vector.");
    }
    AttestationStaticCorpus.Mutation mutation = mutation(id, metadata);
    negativeSource(id, definition.baseId(), metadata);
    return AttestationStaticCorpus.fixture(
        id,
        source(definition.baseId()),
        mutation,
        definition.policy().fold(),
        AttestationStaticCorpus.VerificationScope.BOOK,
        definition.expectedFailure());
  }

  static void requirePolicyFold(
      String historySourceId, AttestationStaticCorpus.PolicyFold expected) {
    PolicyReference reference =
        new PolicyReference(historySourceId, expected.resolvingOrder().intValueExact(), expected);
    reference
        .fold()
        .requireMatches(registryAt(reference.historySourceId(), reference.lastIncludedOrder()));
  }

  static void requirePolicyFold(PolicyReference reference) {
    reference
        .fold()
        .requireMatches(registryAt(reference.historySourceId(), reference.lastIncludedOrder()));
  }

  static PolicyReference positivePolicy(String id) {
    return require(POSITIVE_BOOKS, id, "positive book").policy();
  }

  static PolicyReference negativePolicy(String id) {
    return require(NEGATIVE_BOOKS, id, "negative book").policy();
  }

  static StandaloneEnvelope b08() {
    byte[] source =
        documentBytes(AttestationDocumentVectors.ARTIFACT_DOCUMENT, "V-MANIFEST-02", "envelope");
    AttestationDecodedEnvelope<AttestationBackupManifestPayload> decoded =
        AttestationDecodedEnvelope.manifest(source);
    return new StandaloneEnvelope(
        "B-08",
        source,
        standaloneRegistry(AttestationCapability.BACKUP),
        AttestationAuthorizationContext.manifest(decoded.payload()),
        decoded.authorizationEnvelope(),
        new AttestationStaticCorpus.PolicyFold(
            BigInteger.valueOf(42), AttestationCapability.BACKUP, 2, 2, 2, 0, false));
  }

  static StandaloneEnvelope b09() {
    byte[] source =
        documentBytes(AttestationDocumentVectors.ARTIFACT_DOCUMENT, "V-RECEIPT-02", "envelope");
    AttestationDecodedEnvelope<AttestationReceiptPayload> decoded =
        AttestationDecodedEnvelope.receipt(source);
    return new StandaloneEnvelope(
        "B-09",
        source,
        standaloneRegistry(AttestationCapability.ANCHOR),
        AttestationAuthorizationContext.receipt(decoded.payload()),
        decoded.authorizationEnvelope(),
        new AttestationStaticCorpus.PolicyFold(
            BigInteger.valueOf(42), AttestationCapability.ANCHOR, 2, 2, 2, 0, false));
  }

  static byte[] source(String id) {
    String expectedHash = require(SOURCE_SHA256, id, "source hash");
    if (!AttestationStaticCorpusResourceLoader.text("source/" + id + ".sha256")
        .equals(expectedHash)) {
      throw new IllegalStateException(
          "Static corpus source fingerprint is not independently pinned.");
    }
    byte[] source = AttestationStaticCorpusResourceLoader.base64("source/" + id + ".b64");
    requireHash(source, expectedHash, "source " + id);
    return source;
  }

  private static byte[] bookSource(String id) {
    NegativeBook negative = NEGATIVE_BOOKS.get(id);
    if (negative == null) {
      return source(id);
    }
    return negativeSource(id, negative.baseId(), negativeMetadata(id));
  }

  private static byte[] negativeSource(String id, String baseId, NegativeMetadata metadata) {
    String expectedHash = require(NEGATIVE_SOURCE_SHA256, id, "negative source hash");
    if (!metadata.sourceSha256().equals(expectedHash)) {
      throw new IllegalStateException(
          "Static negative source fingerprint is not independently pinned.");
    }
    byte[] source = mutation(id, metadata).apply(source(baseId));
    requireHash(source, expectedHash, "negative " + id);
    return source;
  }

  private static AttestationStaticCorpus.Mutation mutation(String id, NegativeMetadata metadata) {
    return AttestationStaticCorpus.Mutation.edits(
        AttestationStaticCorpus.Mutation.edit(
            metadata.offset(),
            metadata.replacedByteCount(),
            AttestationStaticCorpusResourceLoader.base64("negative/" + id + ".delta.b64")));
  }

  private static AttestationRegistry registryAt(String sourceId, int lastIncludedOrder) {
    List<AttestationBookOperation> operations = book(sourceId).operations();
    if (lastIncludedOrder < 0 || lastIncludedOrder >= operations.size()) {
      throw new IllegalArgumentException(
          "Static corpus policy fold has an invalid history position.");
    }
    AttestationBookOperation genesis = operations.getFirst();
    AttestationGenesisAuthorizationContext context =
        AttestationGenesisAuthorizationContext.verify(
            genesis.envelope().payload(), genesis.requestPreimage(), genesis.effectPreimage());
    AttestationRegistryHistory history = AttestationRegistryHistory.genesis(context);
    for (int index = 1; index <= lastIncludedOrder; index++) {
      AttestationBookOperation operation = operations.get(index);
      history.accept(
          AttestationOperationKind.forWireToken(operation.envelope().payload().operationKind()),
          operation.envelope().payload().operationOrder(),
          operation.requestPreimage(),
          operation.effectPreimage());
    }
    return history.registry();
  }

  private static Map<String, PositiveBook> positiveBooks() {
    return Map.of(
        "B-01",
            positive(
                "B-01", new PolicyFacts("B-01", 0, AttestationCapability.POST, 1, 1, 1, 0, false)),
        "B-02",
            positive(
                "B-02", new PolicyFacts("B-02", 2, AttestationCapability.POST, 2, 2, 2, 0, false)),
        "B-03",
            positive(
                "B-03", new PolicyFacts("B-03", 6, AttestationCapability.POST, 2, 3, 3, 0, false)),
        "B-04",
            positive(
                "B-04",
                new PolicyFacts("B-04", 9, AttestationCapability.CLOSE_PERIOD, 1, 3, 2, 1, true)),
        "B-05",
            positive(
                "B-05-book",
                new PolicyFacts("B-05-book", 3, AttestationCapability.BACKUP, 1, 2, 2, 0, false)),
        "B-06",
            positive(
                "B-06",
                new PolicyFacts("B-06", 3, AttestationCapability.RESTORE, 2, 2, 2, 0, false)),
        "B-07",
            positive(
                "B-07",
                new PolicyFacts("B-07", 3, AttestationCapability.RESTORE, 2, 2, 2, 0, false)),
        "B-10",
            positive(
                "B-10",
                new PolicyFacts("B-10", 3, AttestationCapability.REKEY, 2, 2, 2, 0, false)));
  }

  private static Map<String, NegativeBook> negativeBooks() {
    return Map.ofEntries(
        negative(
            "N-11",
            "B-02",
            new PolicyFacts("B-02", 2, AttestationCapability.POST, 2, 2, 2, 0, false),
            AttestationAuthorizationFailure.PREVIOUS_HEAD_INVALID),
        negative(
            "N-12a",
            "B-01",
            new PolicyFacts("B-01", 0, AttestationCapability.POST, 1, 1, 1, 0, false),
            AttestationAuthorizationFailure.GENESIS_INVALID),
        negative(
            "N-12b",
            "B-01",
            new PolicyFacts("B-01", 0, AttestationCapability.POST, 1, 1, 1, 0, false),
            AttestationAuthorizationFailure.GENESIS_INVALID),
        negative(
            "N-16",
            "B-04",
            new PolicyFacts("N-16", 5, AttestationCapability.CLOSE_PERIOD, 1, 3, 3, 0, true),
            AttestationAuthorizationFailure.POLICY_CAPACITY_INVALID),
        negative(
            "N-17",
            "B-02",
            new PolicyFacts("B-02", 2, AttestationCapability.POST, 2, 2, 2, 0, false),
            AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID),
        negative(
            "N-18",
            "B-02",
            new PolicyFacts("N-18", 4, AttestationCapability.POST, 2, 2, 2, 0, false),
            AttestationAuthorizationFailure.CAPABILITY_INVALID),
        negative(
            "N-19",
            "B-04",
            new PolicyFacts("B-04", 8, AttestationCapability.CLOSE_PERIOD, 1, 3, 2, 1, true),
            AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID),
        negative(
            "N-20",
            "B-04",
            new PolicyFacts("N-20", 5, AttestationCapability.CLOSE_PERIOD, 2, 3, 2, 1, true),
            AttestationAuthorizationFailure.POLICY_CAPACITY_INVALID),
        negative(
            "N-21",
            "B-03",
            new PolicyFacts("B-03", 5, AttestationCapability.ENROLL_KEY, 2, 2, 2, 0, false),
            AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID),
        negative(
            "N-22",
            "B-03",
            new PolicyFacts("B-03", 5, AttestationCapability.ENROLL_KEY, 2, 2, 2, 0, false),
            AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID),
        negative(
            "N-23",
            "B-03",
            new PolicyFacts("B-03", 5, AttestationCapability.ENROLL_KEY, 2, 2, 2, 0, false),
            AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID),
        negative(
            "N-24",
            "B-02",
            new PolicyFacts("B-02", 3, AttestationCapability.ENROLL_KEY, 2, 2, 2, 0, false),
            AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID),
        negative(
            "N-25",
            "B-02",
            new PolicyFacts("B-02", 3, AttestationCapability.ENROLL_KEY, 2, 2, 2, 0, false),
            AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID),
        negative(
            "N-26",
            "B-03",
            new PolicyFacts("B-03", 5, AttestationCapability.ENROLL_KEY, 2, 2, 2, 0, false),
            AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID),
        negative(
            "N-27",
            "B-02",
            new PolicyFacts("B-02", 3, AttestationCapability.REVOKE_KEY, 2, 2, 2, 0, false),
            AttestationAuthorizationFailure.REQUEST_PROFILE_INVALID));
  }

  private static PositiveBook positive(String sourceId, PolicyFacts policyFacts) {
    return new PositiveBook(sourceId, policyFacts.reference());
  }

  private static Map.Entry<String, NegativeBook> negative(
      String id,
      String baseId,
      PolicyFacts policyFacts,
      AttestationAuthorizationFailure expectedFailure) {
    return Map.entry(id, new NegativeBook(baseId, expectedFailure, policyFacts.reference()));
  }

  private static AttestationRegistry standaloneRegistry(AttestationCapability capability) {
    AttestationSpki a =
        AttestationSpki.of(
            AttestationDocumentVectors.hex(
                "302a300506032b657003210003a107bff3ce10be1d70dd18e74bc09967e4d6309ba50d5f1ddc8664125531b8"));
    AttestationSpki b =
        AttestationSpki.of(
            AttestationDocumentVectors.hex(
                "302a300506032b657003210029acbae141bccaf0b22e1a94d34d0bc7361e526d0bfe12c89794bc9322966dd7"));
    AttestationSpki c =
        AttestationSpki.of(
            AttestationDocumentVectors.hex(
                "302a300506032b65700321002543b92ff1095511476adc8369db6ddc933665a11978dda1404ee1066ca9559d"));
    return AttestationRegistry.fromVerifierFacts(
        List.of(binding(PRINCIPAL_A, a), binding(PRINCIPAL_B, b), binding(PRINCIPAL_C, c)),
        List.of(),
        List.of(grant(PRINCIPAL_A, capability), grant(PRINCIPAL_B, capability)),
        List.of(new AttestationPolicyRule(BigInteger.ZERO, capability, 2)),
        List.of());
  }

  private static AttestationCredentialBinding binding(UUID principalId, AttestationSpki spki) {
    return new AttestationCredentialBinding(
        BigInteger.ZERO,
        principalId,
        AttestationHash.sha256(spki.bytes()),
        AttestationCredentialBinding.BindingAction.ENROLL,
        spki,
        AttestationCredentialPurpose.OPERATOR,
        null);
  }

  private static AttestationCapabilityGrant grant(
      UUID principalId, AttestationCapability capability) {
    return new AttestationCapabilityGrant(
        BigInteger.ZERO, principalId, capability, AttestationGrantState.GRANT);
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

  private static byte[] documentBytes(String document, String vector, String field) {
    try {
      return AttestationDocumentVectors.bytes(document, vector, field);
    } catch (IOException exception) {
      throw new IllegalStateException("Normative standalone vector is unavailable.", exception);
    }
  }

  private static String requiredField(Map<String, String> fields, String key) {
    String value = fields.get(key);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Static corpus metadata lacks " + key + ".");
    }
    return value;
  }

  private static void requireHash(byte[] source, String expectedHash, String name) {
    if (!AttestationHash.sha256(source).hex().equals(expectedHash)) {
      throw new IllegalStateException("Static corpus hash does not match " + name + ".");
    }
  }

  private static <T> T require(Map<String, T> values, String id, String kind) {
    T value = values.get(Objects.requireNonNull(id, "id"));
    if (value == null) {
      throw new IllegalArgumentException("Unknown static " + kind + ": " + id);
    }
    return value;
  }

  /** Fixed standalone authorization input and the literal registry facts that resolve it. */
  static final class StandaloneEnvelope {
    private final String id;
    private final byte[] source;
    private final AttestationRegistry registry;
    private final AttestationAuthorizationContext context;
    private final AttestationAuthorizationEnvelope envelope;
    private final AttestationStaticCorpus.PolicyFold policy;

    StandaloneEnvelope(
        String id,
        byte[] source,
        AttestationRegistry registry,
        AttestationAuthorizationContext context,
        AttestationAuthorizationEnvelope envelope,
        AttestationStaticCorpus.PolicyFold policy) {
      this.id = Objects.requireNonNull(id, "id");
      this.source = Objects.requireNonNull(source, "source").clone();
      this.registry = Objects.requireNonNull(registry, "registry");
      this.context = Objects.requireNonNull(context, "context");
      this.envelope = Objects.requireNonNull(envelope, "envelope");
      this.policy = Objects.requireNonNull(policy, "policy");
    }

    String id() {
      return id;
    }

    byte[] source() {
      return source.clone();
    }

    AttestationRegistry registry() {
      return registry;
    }

    AttestationAuthorizationContext context() {
      return context;
    }

    AttestationAuthorizationEnvelope envelope() {
      return envelope;
    }

    AttestationStaticCorpus.PolicyFold policy() {
      return policy;
    }
  }

  record PolicyReference(
      String historySourceId, int lastIncludedOrder, AttestationStaticCorpus.PolicyFold fold) {
    PolicyReference {
      if (Objects.requireNonNull(historySourceId, "historySourceId").isBlank()
          || lastIncludedOrder < 0) {
        throw new IllegalArgumentException(
            "Static policy reference must name a concrete history position.");
      }
      Objects.requireNonNull(fold, "fold");
      if (!fold.resolvingOrder().equals(BigInteger.valueOf(lastIncludedOrder))) {
        throw new IllegalArgumentException(
            "Static policy fold must resolve at its declared history position.");
      }
    }
  }

  /** Complete, literal registry facts used to define one corpus policy fold. */
  private record PolicyFacts(
      String historySourceId,
      int resolvingOrder,
      AttestationCapability capability,
      int quorum,
      int eligiblePrincipalCount,
      int operatorEligiblePrincipalCount,
      int systemEligiblePrincipalCount,
      boolean activeSystemWorkflow) {
    PolicyReference reference() {
      return new PolicyReference(
          historySourceId,
          resolvingOrder,
          new AttestationStaticCorpus.PolicyFold(
              BigInteger.valueOf(resolvingOrder),
              capability,
              quorum,
              eligiblePrincipalCount,
              operatorEligiblePrincipalCount,
              systemEligiblePrincipalCount,
              activeSystemWorkflow));
    }
  }

  private record PositiveBook(String sourceId, PolicyReference policy) {}

  private record NegativeBook(
      String baseId, AttestationAuthorizationFailure expectedFailure, PolicyReference policy) {}

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
