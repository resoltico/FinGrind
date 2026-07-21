package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.assertFailure;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Executes the exact Slice 3 standalone-envelope rows from the normative static corpus. */
class AttestationStaticEnvelopeCorpusAuthorizationTest {
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
      DecodedAuthorization decoded = decode(form, raw);
      AttestationAuthorizationContext context = decoded.context();
      AttestationRegistry validRegistry =
          verifierRegistry(
              context.capability(), 2, canonicalBindings(), List.of(), PRINCIPAL_A, PRINCIPAL_B);
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
                      context.capability(),
                      1,
                      canonicalBindings(),
                      List.of(),
                      PRINCIPAL_A,
                      PRINCIPAL_B),
                  raw));

      assertFailure(
          AttestationAuthorizationFailure.DUPLICATE_PRINCIPAL,
          () ->
              authorize(
                  form,
                  validRegistry,
                  raw.withSecondPrincipalId(
                      decoded.authorizationEnvelope().entries().getFirst().principalId())));
      assertFailure(
          AttestationAuthorizationFailure.DUPLICATE_KEY,
          () ->
              authorize(
                  form,
                  validRegistry,
                  raw.withSecondKeyId(
                      decoded.authorizationEnvelope().entries().getFirst().keyId())));
      assertFailure(
          AttestationAuthorizationFailure.ENVELOPE_ORDER_INVALID,
          () -> authorize(form, validRegistry, raw.withSwappedEntries()));
      assertFailure(
          AttestationAuthorizationFailure.KEY_NOT_ENROLLED,
          () ->
              authorize(
                  form,
                  verifierRegistry(
                      context.capability(),
                      2,
                      List.of(
                          binding(BigInteger.ZERO, PRINCIPAL_B, SPKI_B),
                          binding(
                              context.resolvingOrder().add(BigInteger.ONE), PRINCIPAL_A, SPKI_A)),
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
                      context.capability(),
                      2,
                      canonicalBindings(),
                      List.of(
                          new AttestationCredentialRevocation(
                              context.resolvingOrder().subtract(BigInteger.ONE),
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
                      context.capability(),
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
  void derivesThePublishedResolvingOrderForEachStandaloneEnvelopeForm() throws IOException {
    assertEquals(
        BigInteger.valueOf(41),
        decode(EnvelopeForm.OPERATION, rawEnvelope(EnvelopeForm.OPERATION))
            .context()
            .resolvingOrder());
    assertEquals(
        BigInteger.valueOf(42),
        decode(EnvelopeForm.MANIFEST, rawEnvelope(EnvelopeForm.MANIFEST))
            .context()
            .resolvingOrder());
    assertEquals(
        BigInteger.valueOf(42),
        decode(EnvelopeForm.RECEIPT, rawEnvelope(EnvelopeForm.RECEIPT)).context().resolvingOrder());
  }

  private static void assertCapabilityFailure(EnvelopeForm form) throws IOException {
    RawEnvelope raw = rawEnvelope(form);
    DecodedAuthorization decoded = decode(form, raw);
    AttestationSignatureEntry cEntry =
        new AttestationSignatureEntry(PRINCIPAL_C, KEY_C, form.cSignature());
    AttestationSignatureEntry bEntry = decoded.authorizationEnvelope().entries().getFirst();
    RawEnvelope bAndC = raw.withEntries(List.of(cEntry, bEntry));
    assertEquals(
        List.of(KEY_C, bEntry.keyId()),
        decode(form, bAndC).authorizationEnvelope().entries().stream()
            .map(AttestationSignatureEntry::keyId)
            .toList());
    assertFailure(
        AttestationAuthorizationFailure.CAPABILITY_INVALID,
        () ->
            authorize(
                form,
                verifierRegistry(
                    decoded.context().capability(),
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
    return new RawEnvelope(
        AttestationDocumentVectors.bytes(form.document(), form.vector(), "envelope"),
        form.payloadLength());
  }

  private static void authorize(
      EnvelopeForm form, AttestationRegistry registry, RawEnvelope envelope) {
    DecodedAuthorization decoded = decode(form, envelope);
    AttestationAuthorization.requireAuthorized(
        registry, decoded.context(), decoded.authorizationEnvelope());
  }

  private static DecodedAuthorization decode(EnvelopeForm form, RawEnvelope envelope) {
    return form.decode(envelope.encoded());
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

  /** Selects one standalone signed structure and decodes its mutated corpus envelope. */
  private enum EnvelopeForm {
    OPERATION(
        AttestationDocumentVectors.PROTOCOL_DOCUMENT,
        "V-OP-02",
        181,
        "0000000000000000000000000000000000000000000000000000000000000000") {
      @Override
      DecodedAuthorization decode(byte[] encoded) {
        AttestationDecodedEnvelope<AttestationOperationPayload> decoded =
            AttestationDecodedEnvelope.operation(encoded);
        return new DecodedAuthorization(
            AttestationAuthorizationContext.standaloneOperation(decoded.payload()),
            decoded.authorizationEnvelope());
      }
    },
    MANIFEST(
        AttestationDocumentVectors.ARTIFACT_DOCUMENT,
        "V-MANIFEST-02",
        121,
        "e68bc651ab5ee607fe5f5e5a122e58477950dc37b33794c95fc5671df28d6efaf408d460b75231b175b025276fff1e92981c942ab523602d0076a3250f8d5f0e") {
      @Override
      DecodedAuthorization decode(byte[] encoded) {
        AttestationDecodedEnvelope<AttestationBackupManifestPayload> decoded =
            AttestationDecodedEnvelope.manifest(encoded);
        return new DecodedAuthorization(
            AttestationAuthorizationContext.manifest(decoded.payload()),
            decoded.authorizationEnvelope());
      }
    },
    RECEIPT(
        AttestationDocumentVectors.ARTIFACT_DOCUMENT,
        "V-RECEIPT-02",
        97,
        "31f445e7dda739aa66fb025d965217c83d8df4a602adad8023539df5ac8cff46be999d06b8278439b201099b57519f699718c05dd0f0abdb0ca7a445cd835705") {
      @Override
      DecodedAuthorization decode(byte[] encoded) {
        AttestationDecodedEnvelope<AttestationReceiptPayload> decoded =
            AttestationDecodedEnvelope.receipt(encoded);
        return new DecodedAuthorization(
            AttestationAuthorizationContext.receipt(decoded.payload()),
            decoded.authorizationEnvelope());
      }
    };

    private final String document;
    private final String vector;
    private final int payloadLength;
    private final String cSignatureHex;

    EnvelopeForm(String document, String vector, int payloadLength, String cSignatureHex) {
      this.document = document;
      this.vector = vector;
      this.payloadLength = payloadLength;
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

    byte[] cSignature() {
      return AttestationDocumentVectors.hex(cSignatureHex);
    }

    abstract DecodedAuthorization decode(byte[] encoded);
  }

  /** The authorization inputs derived from one production-decoded corpus envelope. */
  private record DecodedAuthorization(
      AttestationAuthorizationContext context,
      AttestationAuthorizationEnvelope authorizationEnvelope) {}

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
    }

    byte[] encoded() {
      return encoded.clone();
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
