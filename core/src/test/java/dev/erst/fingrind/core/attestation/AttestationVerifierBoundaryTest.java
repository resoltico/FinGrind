package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.credential;
import static dev.erst.fingrind.core.attestation.AttestationAuthorizationTestSupport.signedEnvelope;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.genesisContext;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.genesisEffectPreimage;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.genesisPayload;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.genesisRequestPreimage;
import static dev.erst.fingrind.core.attestation.AttestationGenesisTestSupport.signedGenesisEnvelope;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Verifies the exported raw-evidence boundary without exposing verifier internals. */
class AttestationVerifierBoundaryTest {
  @Test
  void verifiesACompleteGenesisEvidenceUnitAndDefensivelyOwnsItsBytes() {
    TestCredential founder = credential();
    AttestationPreimage request = genesisRequestPreimage(founder);
    AttestationPreimage effect = genesisEffectPreimage(founder);
    AttestationOperationPayload payload =
        genesisPayload(
            BigInteger.ZERO,
            AttestationHash.of(new byte[AttestationHash.BYTE_LENGTH]),
            request,
            effect);
    AttestationAuthorizationEnvelope authorization =
        signedGenesisEnvelope(genesisContext(founder), founder);
    byte[] envelope = AttestationEnvelope.of(payload, authorization.entries()).encoded();
    AttestationEvidence evidence =
        new AttestationEvidence(envelope, request.encoded(), effect.encoded());
    byte[] expectedEnvelope = evidence.operationEnvelope();

    envelope[0] = 0;

    AttestationVerification verification = AttestationVerifier.verifyBook(List.of(evidence));

    assertEquals(AttestationAuthorizationTestSupport.BOOK_ID, verification.bookId());
    assertEquals(BigInteger.ZERO, verification.headOrder());
    assertArrayEquals(expectedEnvelope, evidence.operationEnvelope());
  }

  @Test
  void translatesTheFirstCanonicalFailureIntoTheExportedStableCode() {
    AttestationVerificationException failure =
        assertThrows(
            AttestationVerificationException.class,
            () -> AttestationVerifier.verifyBook(List.of()));

    assertEquals("attestation-preimage-invalid", failure.code());
  }

  @Test
  void translatesAnInvalidPostingCommitmentChainIntoTheExportedStableCode() {
    AttestationVerificationException failure =
        assertThrows(
            AttestationVerificationException.class,
            () -> AttestationVerifier.verifyAndInspectPostingCommitments(List.of()));

    assertEquals("attestation-preimage-invalid", failure.code());
  }

  @Test
  void inspectsAValidChainAndAdmitsAProspectiveRegistryMutationAtItsHead() {
    TestCredential founder = credential();
    AttestationEvidence evidence = genesisEvidence(founder);

    AttestationBookInspection inspection =
        AttestationVerifier.verifyAndInspectBook(List.of(evidence));
    assertEquals(AttestationAuthorizationTestSupport.BOOK_ID, inspection.registry().bookId());
    AttestationVerifier.requireRegistryMutationAdmissible(
        List.of(evidence),
        new AttestationRegistryMutation.EnrollKey(
            java.util.UUID.randomUUID(),
            new AttestationPublicCredential(credential().pair().getPublic().getEncoded()),
            AttestationCredentialPurpose.OPERATOR));

    AttestationVerificationException invalidChain =
        assertThrows(
            AttestationVerificationException.class,
            () ->
                AttestationVerifier.requireRegistryMutationAdmissible(
                    List.of(),
                    new AttestationRegistryMutation.EnrollKey(
                        java.util.UUID.randomUUID(),
                        new AttestationPublicCredential(
                            credential().pair().getPublic().getEncoded()),
                        AttestationCredentialPurpose.OPERATOR)));
    assertEquals("attestation-preimage-invalid", invalidChain.code());
  }

  @Test
  void rejectsBookInspectionComponentsThatDoNotDescribeTheSameVerifiedHead() {
    TestCredential founder = credential();
    AttestationBookInspection inspection =
        AttestationVerifier.verifyAndInspectBook(List.of(genesisEvidence(founder)));
    AttestationVerification verification = inspection.verification();

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationBookInspection(
                verification, registryInspection(java.util.UUID.randomUUID(), BigInteger.ZERO)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationBookInspection(
                verification, registryInspection(verification.bookId(), BigInteger.ONE)));
  }

  @Test
  void projectsPostingCommitmentsOnlyFromTheCryptographicallyVerifiedOperationChain() {
    AttestationCorpusResources.Book fixture = AttestationStaticCorpusVectors.book("B-02");
    List<AttestationEvidence> evidence =
        fixture.operations().stream()
            .map(
                operation ->
                    new AttestationEvidence(
                        operation.envelope().encoded(),
                        operation.requestPreimage().encoded(),
                        operation.effectPreimage().encoded()))
            .toList();
    AttestationPostingCommitmentInspection inspection =
        AttestationVerifier.verifyAndInspectPostingCommitments(evidence);

    AttestationOperationCommitment commitment =
        inspection
            .commitmentsByPostingId()
            .get(java.util.UUID.fromString("30000000-0000-7000-8000-000000000001"));
    assertNotNull(commitment);
    assertEquals(BigInteger.valueOf(3), commitment.operationOrder());
    assertArrayEquals(inspection.verification().operationHead(), commitment.operationHead());
  }

  @Test
  void refusesAmbiguousPostingCommitmentsEvenWhenEveryOperationIsOtherwiseValid() {
    TestCredential founder = credential();
    AttestationEvidence genesis = genesisEvidence(founder);
    AttestationVerification firstHead = AttestationVerifier.verifyBook(List.of(genesis));
    UUID postingId = UUID.fromString("42617efc-7425-4b42-b990-4b4eca2843ce");
    AttestationEvidence firstPosting =
        directPostingEvidence(
            founder,
            firstHead,
            BigInteger.ONE,
            postingId,
            UUID.fromString("b2431ea7-bb0d-4677-bd1e-04cb7fcfd12f"),
            "idempotency-1",
            Instant.parse("2026-07-20T12:30:45Z"));
    AttestationVerification secondHead =
        AttestationVerifier.verifyBook(List.of(genesis, firstPosting));
    AttestationEvidence duplicatePosting =
        directPostingEvidence(
            founder,
            secondHead,
            BigInteger.TWO,
            postingId,
            UUID.fromString("d3d93f87-d85b-457c-b3e3-75b0f7bb6b9f"),
            "idempotency-2",
            Instant.parse("2026-07-20T12:30:46Z"));

    AttestationVerificationException failure =
        assertThrows(
            AttestationVerificationException.class,
            () ->
                AttestationVerifier.verifyAndInspectPostingCommitments(
                    List.of(genesis, firstPosting, duplicatePosting)));

    assertEquals("attestation-preimage-invalid", failure.code());
  }

  private static AttestationEvidence directPostingEvidence(
      TestCredential founder,
      AttestationVerification previousVerification,
      BigInteger operationOrder,
      UUID postingId,
      UUID commandId,
      String idempotencyKey,
      Instant recordedAt) {
    AttestationOperationPreimages preimages =
        AttestationPostingMutationProjection.project(
            new AttestationPostingRequestSnapshot(
                "post-entry",
                idempotencyKey,
                "causation-" + idempotencyKey,
                "CLI",
                LocalDate.parse("2026-07-20"),
                "STANDARD",
                null,
                null,
                List.of(
                    new AttestationPostingEvidenceDocument(
                        "document-" + idempotencyKey,
                        "cash-receipt",
                        LocalDate.parse("2026-07-20"))),
                List.of(
                    new AttestationPostingLine("1000", "DEBIT", "EUR", 100),
                    new AttestationPostingLine("4000", "CREDIT", "EUR", 100))),
            new AttestationPostingEffectSnapshot(
                postingId,
                "post-entry",
                "STANDARD",
                "DIRECT_JOURNAL",
                recordedAt,
                null,
                commandId));
    AttestationPreimage request =
        AttestationPreimage.decode(
            preimages.request(), AttestationAuthorizationFailure.PREIMAGE_INVALID);
    AttestationPreimage effect =
        AttestationPreimage.decode(
            preimages.effect(), AttestationAuthorizationFailure.PREIMAGE_INVALID);
    AttestationOperationPayload payload =
        new AttestationOperationPayload(
            AttestationAuthorizationTestSupport.BOOK_ID,
            operationOrder,
            AttestationOperationKind.POST_ENTRY.wireToken(),
            AttestationHash.of(previousVerification.operationHead()),
            recordedAt,
            AttestationHash.sha256(request.encoded()),
            AttestationHash.sha256(effect.encoded()));
    AttestationAuthorizationContext context =
        AttestationAuthorizationContext.operation(
            payload, AttestationVerifiedOperationProvenance.verify(payload, request));
    AttestationAuthorizationEnvelope authorization = signedEnvelope(context, founder);
    return new AttestationEvidence(
        AttestationEnvelope.of(payload, authorization.entries()).encoded(),
        request.encoded(),
        effect.encoded());
  }

  private static AttestationEvidence genesisEvidence(TestCredential founder) {
    AttestationPreimage request = genesisRequestPreimage(founder);
    AttestationPreimage effect = genesisEffectPreimage(founder);
    AttestationOperationPayload payload =
        genesisPayload(
            BigInteger.ZERO,
            AttestationHash.of(new byte[AttestationHash.BYTE_LENGTH]),
            request,
            effect);
    AttestationAuthorizationEnvelope authorization =
        signedGenesisEnvelope(genesisContext(founder), founder);
    return new AttestationEvidence(
        AttestationEnvelope.of(payload, authorization.entries()).encoded(),
        request.encoded(),
        effect.encoded());
  }

  private static AttestationRegistryInspection registryInspection(
      java.util.UUID bookId, BigInteger headOrder) {
    return new AttestationRegistryInspection(
        bookId, headOrder, "0".repeat(64), List.of(), List.of(), List.of(), List.of());
  }
}
