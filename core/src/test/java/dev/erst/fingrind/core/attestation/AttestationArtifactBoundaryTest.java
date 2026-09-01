package dev.erst.fingrind.core.attestation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.erst.fingrind.core.BookDoctrines;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FiscalYearStart;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Exercises the public artifact boundary without exposing key or verifier implementation detail.
 */
class AttestationArtifactBoundaryTest extends AttestationKeyFileTestFixture {

  @Test
  void createsAndVerifiesAnIndependentBackupArtifactAndReceipt() throws Exception {
    UUID principalId = UUID.fromString("10213243-5465-7687-98a9-babcbddceeff");
    UUID bookId = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
    Path keyPath = temporaryDirectory.resolve("founder.fgatk");
    Path passphrasePath = temporaryDirectory.resolve("founder.passphrase");
    char[] passphrase = "test attestation passphrase".toCharArray();
    AttestationKeyFileTestSupport.writeOwnerOnlyText(
        passphrasePath, "test attestation passphrase\n");
    AttestationPublicCredential credential =
        AttestationKeyFiles.create(keyPath, passphrase).credential();
    AttestationEvidence genesis;
    try (AttestationSigningCredential signer =
        new AttestationSigningCredential(principalId, credential, keyPath, passphrase)) {
      genesis =
          AttestationGenesis.create(
              bookId, bookIdentity(), Instant.parse("2026-07-21T00:00:00Z"), List.of(signer));
    } finally {
      java.util.Arrays.fill(passphrase, '\0');
    }

    AttestationVerification source = AttestationVerifier.verifyBook(List.of(genesis));
    byte[] snapshot = new byte[] {4, 8, 15, 16, 23, 42};
    AttestationCredentialSource sourceCredential =
        new AttestationCredentialSource(
            dev.erst.fingrind.core.attestation.AttestationCustodian.FILE_PKCS8,
            principalId,
            keyPath,
            passphrasePath);
    byte[] artifact;
    byte[] receipt;
    try (AttestationSigningSession session =
        AttestationSigningSession.open(List.of(sourceCredential))) {
      artifact =
          session.createBackupArtifact(
              snapshot,
              bookId,
              UUID.fromString("00112233-4455-6677-8899-aabbccddeeff"),
              source.headOrder(),
              source.operationHead());
      receipt =
          session.createReceipt(
              bookId,
              source.headOrder(),
              source.operationHead(),
              Instant.parse("2026-07-21T00:01:00.123456789Z"));
    }

    AttestationBackupArtifactVerification artifactVerification =
        AttestationBackupArtifact.verify(artifact, ignored -> List.of(genesis));
    AttestationReceiptVerificationResult receiptVerification =
        AttestationReceipt.verify(
            receipt, List.of(genesis), AttestationReceiptRetention.INDEPENDENT);

    assertEquals(bookId, artifactVerification.bookId());
    assertEquals(source.headOrder(), artifactVerification.sourceOrder());
    assertArrayEquals(snapshot, artifactVerification.snapshot());
    assertFalse(artifactVerification.sourceVerification().reviewRequired());
    assertEquals(bookId, receiptVerification.bookId());
    assertEquals(source.headOrder(), receiptVerification.operationOrder());
    assertEquals(List.of(), receiptVerification.findings());
    assertEquals(
        Instant.parse("2026-07-21T00:01:00.123Z"),
        AttestationDecodedEnvelope.receipt(receipt).payload().receiptTimestamp());
  }

  private static BookIdentity bookIdentity() {
    return new BookIdentity(
        new EntityProfile(new BookEntityName("Acme Studio")),
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE,
        CurrencyUnit.of("EUR"),
        FiscalYearStart.parse("01-01"),
        LocalDate.parse("2026-01-01"));
  }
}
