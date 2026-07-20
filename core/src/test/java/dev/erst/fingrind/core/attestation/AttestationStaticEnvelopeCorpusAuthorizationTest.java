package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.assertFailure;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Executes the exact Slice 3 standalone-envelope rows from the normative static corpus. */
class AttestationStaticEnvelopeCorpusAuthorizationTest {
  private static final UUID BOOK_ID = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
  private static final UUID PRINCIPAL_A = UUID.fromString("10213243-5465-7687-98a9-babcbddceeff");
  private static final UUID PRINCIPAL_B = UUID.fromString("11223344-5566-7788-99aa-bbccddeeff00");
  private static final UUID PRINCIPAL_C = UUID.fromString("22334455-6677-8899-aabb-ccddeeff0011");
  private static final AttestationSpki SPKI_A =
      AttestationSpki.of(
          AttestationDocumentVectors.hex(
              "302a300506032b657003210003a107bff3ce10be1d70dd18e74bc09967e4d6309ba50d5f1ddc8664125531b8"));
  private static final AttestationSpki SPKI_B =
      AttestationSpki.of(
          AttestationDocumentVectors.hex(
              "302a300506032b657003210029acbae141bccaf0b22e1a94d34d0bc7361e526d0bfe12c89794bc9322966dd7"));
  private static final AttestationSpki SPKI_C =
      AttestationSpki.of(
          AttestationDocumentVectors.hex(
              "302a300506032b65700321002543b92ff1095511476adc8369db6ddc933665a11978dda1404ee1066ca9559d"));
  private static final AttestationHash KEY_C =
      AttestationHash.of(
          AttestationDocumentVectors.hex(
              "788de5096f8b530eef97a4015cffb7cfeb260c23795b846bf8112682a93b1101"));
  private static final byte[] X25519_SPKI =
      AttestationDocumentVectors.hex(
          "302a300506032b656e032100000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
  private static final AttestationHash X25519_KEY_ID = AttestationHash.sha256(X25519_SPKI);

  @Test
  void executesTheFixedByteMutationsForRowsN01ThroughN10() throws IOException {
    for (EnvelopeForm form : EnvelopeForm.values()) {
      RawEnvelope raw = rawEnvelope(form);
      AttestationRegistry validRegistry =
          verifierRegistry(
              form.capability(), 2, canonicalBindings(), List.of(), PRINCIPAL_A, PRINCIPAL_B);
      assertDoesNotThrow(() -> authorize(form, validRegistry, raw));

      assertFailure(
          AttestationAuthorizationFailure.SIGNATURE_INVALID,
          () -> authorize(form, validRegistry, raw.xorFinalSignatureByte()));
      assertFailure(
          AttestationAuthorizationFailure.QUORUM_BELOW,
          () -> authorize(form, validRegistry, raw.withEntryCountAndWithoutEntry(1, 1)));
      assertFailure(
          AttestationAuthorizationFailure.QUORUM_EXCESS,
          () ->
              authorize(
                  form,
                  verifierRegistry(
                      form.capability(),
                      1,
                      canonicalBindings(),
                      List.of(),
                      PRINCIPAL_A,
                      PRINCIPAL_B),
                  raw));

      assertFailure(
          AttestationAuthorizationFailure.DUPLICATE_PRINCIPAL,
          () -> authorize(form, validRegistry, raw.withSecondPrincipalId(raw.firstPrincipalId())));
      assertFailure(
          AttestationAuthorizationFailure.DUPLICATE_KEY,
          () -> authorize(form, validRegistry, raw.withSecondKeyId(raw.firstKeyId())));
      assertFailure(
          AttestationAuthorizationFailure.ENVELOPE_ORDER_INVALID,
          () -> authorize(form, validRegistry, raw.withSwappedEntries()));
      assertFailure(
          AttestationAuthorizationFailure.KEY_NOT_ENROLLED,
          () ->
              authorize(
                  form,
                  verifierRegistry(
                      form.capability(),
                      2,
                      List.of(
                          binding(BigInteger.ZERO, PRINCIPAL_B, SPKI_B),
                          binding(
                              form.expectedResolvingOrder().add(BigInteger.ONE),
                              PRINCIPAL_A,
                              SPKI_A)),
                      List.of(),
                      PRINCIPAL_A,
                      PRINCIPAL_B),
                  raw));
      assertFailure(
          AttestationAuthorizationFailure.KEY_REVOKED,
          () ->
              authorize(
                  form,
                  verifierRegistry(
                      form.capability(),
                      2,
                      canonicalBindings(),
                      List.of(
                          new AttestationCredentialRevocation(
                              form.expectedResolvingOrder().subtract(BigInteger.ONE),
                              PRINCIPAL_A,
                              keyId(SPKI_A))),
                      PRINCIPAL_A,
                      PRINCIPAL_B),
                  raw));
      assertFailure(
          AttestationAuthorizationFailure.KEY_PRINCIPAL_MISMATCH,
          () -> authorize(form, validRegistry, raw.withSecondPrincipalId(PRINCIPAL_C)));
      assertFailure(
          AttestationAuthorizationFailure.KEY_ALGORITHM_INVALID,
          () ->
              authorize(
                  form,
                  verifierRegistry(
                      form.capability(),
                      2,
                      List.of(
                          binding(BigInteger.ZERO, PRINCIPAL_B, AttestationSpki.of(X25519_SPKI)),
                          binding(BigInteger.ZERO, PRINCIPAL_A, SPKI_A)),
                      List.of(),
                      PRINCIPAL_A,
                      PRINCIPAL_B),
                  raw.withFirstKeyId(X25519_KEY_ID)));
    }
  }

  @Test
  void executesTheFixedBAndCRebuildsForRowsN13AndN15() throws IOException {
    assertCapabilityFailure(EnvelopeForm.MANIFEST);
    assertCapabilityFailure(EnvelopeForm.RECEIPT);
  }

  @Test
  void derivesThePublishedResolvingOrderForEachStandaloneEnvelopeForm() {
    assertEquals(BigInteger.valueOf(41), EnvelopeForm.OPERATION.context().resolvingOrder());
    assertEquals(BigInteger.valueOf(42), EnvelopeForm.MANIFEST.context().resolvingOrder());
    assertEquals(BigInteger.valueOf(42), EnvelopeForm.RECEIPT.context().resolvingOrder());
  }

  private static void assertCapabilityFailure(EnvelopeForm form) throws IOException {
    RawEnvelope raw = rawEnvelope(form);
    AttestationSignatureEntry cEntry =
        new AttestationSignatureEntry(PRINCIPAL_C, KEY_C, form.cSignature());
    AttestationSignatureEntry bEntry = raw.entries().getFirst();
    RawEnvelope bAndC = raw.withEntries(List.of(cEntry, bEntry));
    assertEquals(
        List.of(KEY_C, bEntry.keyId()),
        bAndC.entries().stream().map(AttestationSignatureEntry::keyId).toList());
    assertFailure(
        AttestationAuthorizationFailure.CAPABILITY_INVALID,
        () ->
            authorize(
                form,
                verifierRegistry(
                    form.capability(),
                    2,
                    List.of(
                        binding(BigInteger.ZERO, PRINCIPAL_A, SPKI_A),
                        binding(BigInteger.ZERO, PRINCIPAL_B, SPKI_B),
                        binding(BigInteger.ZERO, PRINCIPAL_C, SPKI_C)),
                    List.of(),
                    PRINCIPAL_A,
                    PRINCIPAL_B),
                bAndC));
  }

  private static RawEnvelope rawEnvelope(EnvelopeForm form) throws IOException {
    RawEnvelope raw =
        new RawEnvelope(
            AttestationDocumentVectors.bytes(form.document(), form.vector(), "envelope"),
            form.payloadLength());
    assertArrayEquals(form.context().payload(), raw.payload());
    return raw;
  }

  private static void authorize(
      EnvelopeForm form, AttestationRegistry registry, RawEnvelope envelope) {
    AttestationAuthorization.requireAuthorized(registry, form.context(), envelope.asEnvelope());
  }

  private static AttestationRegistry verifierRegistry(
      AttestationCapability capability,
      int quorum,
      List<AttestationCredentialBinding> bindings,
      List<AttestationCredentialRevocation> revocations,
      UUID... grantedPrincipals) {
    List<AttestationCapabilityGrant> grants =
        Arrays.stream(grantedPrincipals)
            .map(
                principalId ->
                    new AttestationCapabilityGrant(
                        BigInteger.ZERO, principalId, capability, AttestationGrantState.GRANT))
            .toList();
    return AttestationRegistry.fromVerifierFacts(
        bindings,
        revocations,
        grants,
        List.of(new AttestationPolicyRule(BigInteger.ZERO, capability, quorum)),
        List.of());
  }

  private static List<AttestationCredentialBinding> canonicalBindings() {
    return List.of(
        binding(BigInteger.ZERO, PRINCIPAL_A, SPKI_A),
        binding(BigInteger.ZERO, PRINCIPAL_B, SPKI_B));
  }

  private static AttestationCredentialBinding binding(
      BigInteger acceptedOrder, UUID principalId, AttestationSpki spki) {
    return new AttestationCredentialBinding(
        acceptedOrder,
        principalId,
        keyId(spki),
        AttestationCredentialBinding.BindingAction.ENROLL,
        spki,
        AttestationCredentialPurpose.OPERATOR,
        null);
  }

  private static AttestationHash keyId(AttestationSpki spki) {
    return AttestationHash.sha256(spki.bytes());
  }

  /** Selects one standalone signed structure and the context that its decoded payload creates. */
  private enum EnvelopeForm {
    OPERATION(
        AttestationDocumentVectors.PROTOCOL_DOCUMENT,
        "V-OP-02",
        181,
        AttestationCapability.POST,
        BigInteger.valueOf(41),
        "0000000000000000000000000000000000000000000000000000000000000000") {
      @Override
      AttestationAuthorizationContext context() {
        return AttestationAuthorizationContext.standaloneOperation(
            new AttestationOperationPayload(
                BOOK_ID,
                BigInteger.valueOf(42),
                AttestationOperationKind.RECORD_SALE_SETTLED.wireToken(),
                AttestationHash.of(
                    AttestationDocumentVectors.hex(
                        "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")),
                Instant.parse("2026-07-17T03:34:00.485Z"),
                AttestationHash.of(
                    AttestationDocumentVectors.hex(
                        "202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f")),
                AttestationHash.of(
                    AttestationDocumentVectors.hex(
                        "404142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f"))));
      }
    },
    MANIFEST(
        AttestationDocumentVectors.ARTIFACT_DOCUMENT,
        "V-MANIFEST-02",
        121,
        AttestationCapability.BACKUP,
        BigInteger.valueOf(42),
        "e68bc651ab5ee607fe5f5e5a122e58477950dc37b33794c95fc5671df28d6efaf408d460b75231b175b025276fff1e92981c942ab523602d0076a3250f8d5f0e") {
      @Override
      AttestationAuthorizationContext context() {
        return AttestationAuthorizationContext.manifest(
            new AttestationBackupManifestPayload(
                BOOK_ID,
                UUID.fromString("ffeeddcc-bbaa-9988-7766-554433221100"),
                BigInteger.valueOf(42),
                AttestationHash.of(
                    AttestationDocumentVectors.hex(
                        "d7e8fb5126e2d1a7ff28398faec6bfa0e061ca1c74ffd4d1947ea5f70a339213")),
                AttestationHash.of(
                    AttestationDocumentVectors.hex(
                        "606162636465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f"))));
      }
    },
    RECEIPT(
        AttestationDocumentVectors.ARTIFACT_DOCUMENT,
        "V-RECEIPT-02",
        97,
        AttestationCapability.ANCHOR,
        BigInteger.valueOf(42),
        "31f445e7dda739aa66fb025d965217c83d8df4a602adad8023539df5ac8cff46be999d06b8278439b201099b57519f699718c05dd0f0abdb0ca7a445cd835705") {
      @Override
      AttestationAuthorizationContext context() {
        return AttestationAuthorizationContext.receipt(
            new AttestationReceiptPayload(
                BOOK_ID,
                BigInteger.valueOf(42),
                AttestationHash.of(
                    AttestationDocumentVectors.hex(
                        "d7e8fb5126e2d1a7ff28398faec6bfa0e061ca1c74ffd4d1947ea5f70a339213")),
                Instant.parse("2026-07-17T04:00:00Z")));
      }
    };

    private final String document;
    private final String vector;
    private final int payloadLength;
    private final AttestationCapability capability;
    private final BigInteger expectedResolvingOrder;
    private final String cSignatureHex;

    EnvelopeForm(
        String document,
        String vector,
        int payloadLength,
        AttestationCapability capability,
        BigInteger expectedResolvingOrder,
        String cSignatureHex) {
      this.document = document;
      this.vector = vector;
      this.payloadLength = payloadLength;
      this.capability = capability;
      this.expectedResolvingOrder = expectedResolvingOrder;
      this.cSignatureHex = cSignatureHex;
    }

    String document() {
      return document;
    }

    String vector() {
      return vector;
    }

    int payloadLength() {
      return payloadLength;
    }

    AttestationCapability capability() {
      return capability;
    }

    byte[] cSignature() {
      return AttestationDocumentVectors.hex(cSignatureHex);
    }

    BigInteger expectedResolvingOrder() {
      return expectedResolvingOrder;
    }

    abstract AttestationAuthorizationContext context();
  }

  /**
   * Retains raw fixture bytes and performs each corpus change as a byte mutation or byte rebuild.
   */
  private static final class RawEnvelope {
    private static final int ENTRY_LENGTH = 112;
    private static final int PRINCIPAL_LENGTH = 16;
    private static final int KEY_ID_LENGTH = 32;

    private final byte[] encoded;
    private final int payloadLength;

    RawEnvelope(byte[] encoded, int payloadLength) {
      this.encoded = encoded.clone();
      this.payloadLength = payloadLength;
      AttestationDocumentVectors.entries(this.encoded, payloadLength);
    }

    byte[] payload() {
      return Arrays.copyOf(encoded, payloadLength);
    }

    List<AttestationSignatureEntry> entries() {
      return AttestationDocumentVectors.entries(encoded, payloadLength);
    }

    AttestationAuthorizationEnvelope asEnvelope() {
      return new AttestationAuthorizationEnvelope(payload(), entries());
    }

    UUID firstPrincipalId() {
      return entries().getFirst().principalId();
    }

    AttestationHash firstKeyId() {
      return entries().getFirst().keyId();
    }

    RawEnvelope xorFinalSignatureByte() {
      byte[] mutation = encoded.clone();
      mutation[mutation.length - 1] ^= 1;
      return new RawEnvelope(mutation, payloadLength);
    }

    RawEnvelope withEntryCountAndWithoutEntry(int count, int entryIndex) {
      int offset = entryOffset(entryIndex);
      byte[] mutation = new byte[encoded.length - ENTRY_LENGTH];
      System.arraycopy(encoded, 0, mutation, 0, offset);
      System.arraycopy(
          encoded, offset + ENTRY_LENGTH, mutation, offset, encoded.length - offset - ENTRY_LENGTH);
      mutation[payloadLength] = (byte) (count >>> Byte.SIZE);
      mutation[payloadLength + 1] = (byte) count;
      return new RawEnvelope(mutation, payloadLength);
    }

    RawEnvelope withSecondPrincipalId(UUID principalId) {
      return replace(secondPrincipalOffset(), uuidBytes(principalId));
    }

    RawEnvelope withSecondKeyId(AttestationHash keyId) {
      return replace(keyIdOffset(1), keyId.bytes());
    }

    RawEnvelope withFirstKeyId(AttestationHash keyId) {
      return replace(keyIdOffset(0), keyId.bytes());
    }

    RawEnvelope withSwappedEntries() {
      byte[] mutation = encoded.clone();
      byte[] first = Arrays.copyOfRange(mutation, entryOffset(0), entryOffset(0) + ENTRY_LENGTH);
      System.arraycopy(mutation, entryOffset(1), mutation, entryOffset(0), ENTRY_LENGTH);
      System.arraycopy(first, 0, mutation, entryOffset(1), ENTRY_LENGTH);
      return new RawEnvelope(mutation, payloadLength);
    }

    RawEnvelope withEntries(List<AttestationSignatureEntry> replacementEntries) {
      List<AttestationSignatureEntry> checkedEntries = List.copyOf(replacementEntries);
      byte[] mutation =
          new byte[payloadLength + Short.BYTES + checkedEntries.size() * ENTRY_LENGTH];
      System.arraycopy(encoded, 0, mutation, 0, payloadLength);
      mutation[payloadLength] = (byte) (checkedEntries.size() >>> Byte.SIZE);
      mutation[payloadLength + 1] = (byte) checkedEntries.size();
      for (int index = 0; index < checkedEntries.size(); index++) {
        AttestationSignatureEntry entry = checkedEntries.get(index);
        int offset = payloadLength + Short.BYTES + index * ENTRY_LENGTH;
        System.arraycopy(uuidBytes(entry.principalId()), 0, mutation, offset, PRINCIPAL_LENGTH);
        System.arraycopy(
            entry.keyId().bytes(), 0, mutation, offset + PRINCIPAL_LENGTH, KEY_ID_LENGTH);
        System.arraycopy(
            entry.signature(),
            0,
            mutation,
            offset + PRINCIPAL_LENGTH + KEY_ID_LENGTH,
            AttestationSignatureEntry.SIGNATURE_BYTE_LENGTH);
      }
      return new RawEnvelope(mutation, payloadLength);
    }

    private RawEnvelope replace(int offset, byte[] replacement) {
      byte[] mutation = encoded.clone();
      System.arraycopy(replacement, 0, mutation, offset, replacement.length);
      return new RawEnvelope(mutation, payloadLength);
    }

    private int secondPrincipalOffset() {
      return entryOffset(1);
    }

    private int keyIdOffset(int entryIndex) {
      return entryOffset(entryIndex) + PRINCIPAL_LENGTH;
    }

    private int entryOffset(int entryIndex) {
      return payloadLength + Short.BYTES + entryIndex * ENTRY_LENGTH;
    }

    private static byte[] uuidBytes(UUID uuid) {
      return ByteBuffer.allocate(PRINCIPAL_LENGTH)
          .putLong(uuid.getMostSignificantBits())
          .putLong(uuid.getLeastSignificantBits())
          .array();
    }
  }
}
