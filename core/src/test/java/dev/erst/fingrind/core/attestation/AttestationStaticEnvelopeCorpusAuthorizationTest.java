package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.assertFailure;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.credential;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Executes the Slice 3 authorization rows from the normative static corpus. */
class AttestationStaticEnvelopeCorpusAuthorizationTest {
  private static final BigInteger RESOLVING_ORDER = BigInteger.valueOf(42);
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
  private static final byte[] X25519_SPKI =
      AttestationDocumentVectors.hex(
          "302a300506032b656e032100000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");

  @Test
  void executesSharedNegativeRowsForEveryDocumentedEnvelopeForm() throws IOException {
    for (EnvelopeForm form : EnvelopeForm.values()) {
      RawEnvelope raw = rawEnvelope(form);
      AttestationRegistry validRegistry =
          verifierRegistry(
              form.capability(), 2, canonicalBindings(), List.of(), PRINCIPAL_A, PRINCIPAL_B);
      assertDoesNotThrow(() -> authorize(form, validRegistry, raw.asEnvelope()));

      assertFailure(
          AttestationAuthorizationFailure.SIGNATURE_INVALID,
          () -> authorize(form, validRegistry, raw.withTamperedFirstSignature()));
      assertFailure(
          AttestationAuthorizationFailure.QUORUM_BELOW,
          () -> authorize(form, validRegistry, raw.withEntries(List.of(raw.entries().getFirst()))));
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
                  raw.asEnvelope()));

      AttestationSignatureEntry first = raw.entries().getFirst();
      AttestationSignatureEntry second = raw.entries().getLast();
      assertFailure(
          AttestationAuthorizationFailure.DUPLICATE_PRINCIPAL,
          () ->
              authorize(
                  form,
                  validRegistry,
                  raw.withEntries(
                      List.of(
                          first,
                          new AttestationSignatureEntry(
                              first.principalId(), second.keyId(), second.signature())))));
      assertFailure(
          AttestationAuthorizationFailure.DUPLICATE_KEY,
          () ->
              authorize(
                  form,
                  validRegistry,
                  raw.withEntries(
                      List.of(
                          first,
                          new AttestationSignatureEntry(
                              second.principalId(), first.keyId(), second.signature())))));
      assertFailure(
          AttestationAuthorizationFailure.ENVELOPE_ORDER_INVALID,
          () -> authorize(form, validRegistry, raw.withEntries(List.of(second, first))));
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
                          binding(BigInteger.valueOf(43), PRINCIPAL_A, SPKI_A)),
                      List.of(),
                      PRINCIPAL_A,
                      PRINCIPAL_B),
                  raw.asEnvelope()));
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
                              BigInteger.valueOf(41), PRINCIPAL_A, keyId(SPKI_A))),
                      PRINCIPAL_A,
                      PRINCIPAL_B),
                  raw.asEnvelope()));
      assertFailure(
          AttestationAuthorizationFailure.KEY_PRINCIPAL_MISMATCH,
          () ->
              authorize(
                  form,
                  validRegistry,
                  raw.withEntries(
                      List.of(
                          first,
                          new AttestationSignatureEntry(
                              PRINCIPAL_C, second.keyId(), second.signature())))));
      assertFailure(
          AttestationAuthorizationFailure.KEY_ALGORITHM_INVALID,
          () ->
              authorize(
                  form,
                  verifierRegistry(
                      form.capability(),
                      1,
                      List.of(
                          binding(
                              BigInteger.ZERO,
                              first.principalId(),
                              AttestationSpki.of(X25519_SPKI))),
                      List.of(),
                      first.principalId()),
                  raw.withEntries(
                      List.of(
                          new AttestationSignatureEntry(
                              first.principalId(),
                              AttestationHash.sha256(X25519_SPKI),
                              first.signature())))));
    }
  }

  @Test
  void executesTheManifestAndReceiptCapabilityRows() throws IOException {
    assertCapabilityFailure(EnvelopeForm.MANIFEST);
    assertCapabilityFailure(EnvelopeForm.RECEIPT);
  }

  private static void assertCapabilityFailure(EnvelopeForm form) throws IOException {
    RawEnvelope raw = rawEnvelope(form);
    TestCredential ungrantedCredential = credential();
    List<AttestationSignatureEntry> entries = new ArrayList<>();
    entries.add(raw.entries().getFirst());
    entries.add(
        new AttestationSignatureEntry(
            ungrantedCredential.principalId(),
            ungrantedCredential.keyId(),
            AttestationEd25519.sign(ungrantedCredential.pair().getPrivate(), raw.payload())));
    entries.sort(Comparator.comparing(AttestationSignatureEntry::keyId));
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
                        new AttestationCredentialBinding(
                            BigInteger.ZERO,
                            ungrantedCredential.principalId(),
                            ungrantedCredential.keyId(),
                            AttestationCredentialBinding.BindingAction.ENROLL,
                            AttestationSpki.of(ungrantedCredential.pair().getPublic().getEncoded()),
                            AttestationCredentialPurpose.OPERATOR,
                            null)),
                    List.of(),
                    PRINCIPAL_A,
                    PRINCIPAL_B),
                raw.withEntries(entries)));
  }

  private static RawEnvelope rawEnvelope(EnvelopeForm form) throws IOException {
    byte[] envelope = AttestationDocumentVectors.bytes(form.document(), form.vector(), "envelope");
    return new RawEnvelope(
        java.util.Arrays.copyOf(envelope, form.payloadLength()),
        AttestationDocumentVectors.entries(envelope, form.payloadLength()));
  }

  private static void authorize(
      EnvelopeForm form, AttestationRegistry registry, AttestationAuthorizationEnvelope envelope) {
    AttestationAuthorization.requireAuthorized(registry, RESOLVING_ORDER, form.scope(), envelope);
  }

  private static AttestationRegistry verifierRegistry(
      AttestationCapability capability,
      int quorum,
      List<AttestationCredentialBinding> bindings,
      List<AttestationCredentialRevocation> revocations,
      UUID... grantedPrincipals) {
    List<AttestationCapabilityGrant> grants =
        java.util.Arrays.stream(grantedPrincipals)
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

  /** Selects one standalone signed structure and its authorization context. */
  private enum EnvelopeForm {
    OPERATION(
        AttestationDocumentVectors.PROTOCOL_DOCUMENT, "V-OP-02", 181, AttestationCapability.POST) {
      @Override
      AttestationAuthorizationScope scope() {
        return AttestationAuthorizationScope.operation(
            AttestationOperationKind.RECORD_SALE_SETTLED, AttestationSourceChannel.CLI);
      }
    },
    MANIFEST(
        AttestationDocumentVectors.ARTIFACT_DOCUMENT,
        "V-MANIFEST-02",
        121,
        AttestationCapability.BACKUP) {
      @Override
      AttestationAuthorizationScope scope() {
        return AttestationAuthorizationScope.manifest();
      }
    },
    RECEIPT(
        AttestationDocumentVectors.ARTIFACT_DOCUMENT,
        "V-RECEIPT-02",
        97,
        AttestationCapability.ANCHOR) {
      @Override
      AttestationAuthorizationScope scope() {
        return AttestationAuthorizationScope.receipt();
      }
    };

    private final String document;
    private final String vector;
    private final int payloadLength;
    private final AttestationCapability capability;

    EnvelopeForm(
        String document, String vector, int payloadLength, AttestationCapability capability) {
      this.document = document;
      this.vector = vector;
      this.payloadLength = payloadLength;
      this.capability = capability;
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

    abstract AttestationAuthorizationScope scope();
  }

  /** Retains a documented payload with its received-order signature entries. */
  private static final class RawEnvelope {
    private final byte[] payload;
    private final List<AttestationSignatureEntry> entries;

    RawEnvelope(byte[] payload, List<AttestationSignatureEntry> entries) {
      this.payload = payload.clone();
      this.entries = List.copyOf(entries);
    }

    byte[] payload() {
      return payload.clone();
    }

    List<AttestationSignatureEntry> entries() {
      return entries;
    }

    AttestationAuthorizationEnvelope asEnvelope() {
      return new AttestationAuthorizationEnvelope(payload, entries);
    }

    AttestationAuthorizationEnvelope withEntries(
        List<AttestationSignatureEntry> replacementEntries) {
      return new AttestationAuthorizationEnvelope(payload, replacementEntries);
    }

    AttestationAuthorizationEnvelope withTamperedFirstSignature() {
      AttestationSignatureEntry first = entries.getFirst();
      byte[] signature = first.signature();
      signature[signature.length - 1] ^= 1;
      List<AttestationSignatureEntry> replacementEntries = new ArrayList<>(entries);
      replacementEntries.set(
          0, new AttestationSignatureEntry(first.principalId(), first.keyId(), signature));
      return withEntries(replacementEntries);
    }
  }
}
