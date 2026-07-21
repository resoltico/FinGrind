package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.ApprovalDecision;
import dev.erst.fingrind.core.ApprovalId;
import dev.erst.fingrind.core.ApprovalReference;
import dev.erst.fingrind.core.ApprovalType;
import dev.erst.fingrind.core.BookDoctrines;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FiscalYearStart;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Covers file-custody, genesis, and small immutable boundary refusals. */
class AttestationBoundaryValueCoverageTest {
  private static final UUID BOOK_ID = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
  private static final Instant RECORDED_AT = Instant.parse("2026-07-21T12:00:00Z");
  private static final LocalDate EFFECTIVE_DATE = LocalDate.parse("2026-07-21");

  @TempDir Path temporaryDirectory;

  @Test
  void snapshotAndProjectionValues_rejectInvalidBoundaryShapes() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationPostingRequestSnapshot(
                " ",
                "idempotency",
                "causation",
                "cli",
                EFFECTIVE_DATE,
                "post-entry",
                null,
                null,
                List.of(
                    new AttestationPostingEvidenceDocument("source", "invoice", EFFECTIVE_DATE)),
                List.of(new AttestationPostingLine("1000", "DEBIT", "EUR", 1))));
    assertThrows(
        IllegalArgumentException.class, () -> new AttestationPostingLine(" ", "DEBIT", "EUR", 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AttestationPostingEvidenceDocument(" ", "invoice", EFFECTIVE_DATE));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationTaxRegistrationSnapshot(
                "registration",
                "name",
                "LV",
                null,
                "payable",
                "receivable",
                "MONTHLY",
                367,
                List.of(taxCode())));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationTaxRegistrationSnapshot(
                "registration",
                "name",
                "LV",
                null,
                "payable",
                "receivable",
                "MONTHLY",
                -1,
                List.of(taxCode())));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationTaxRegistrationSnapshot(
                "registration",
                "name",
                "LV",
                null,
                "payable",
                "receivable",
                "MONTHLY",
                0,
                List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationTaxRegistrationSnapshot(
                " ",
                "name",
                "LV",
                null,
                "payable",
                "receivable",
                "MONTHLY",
                0,
                List.of(taxCode())));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AttestationTaxCodeSnapshot("VAT", "name", 1_000_001, "EXCLUSIVE", "SALE"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AttestationTaxCodeSnapshot("VAT", " ", 0, "EXCLUSIVE", "SALE"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationTaxRegistrationSnapshot(
                "registration",
                "name",
                "LV",
                " ",
                "payable",
                "receivable",
                "MONTHLY",
                0,
                List.of(taxCode())));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationClosePostingSnapshot(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "idempotency",
                "cause",
                "post-entry",
                "post-entry",
                EFFECTIVE_DATE,
                RECORDED_AT,
                "system",
                List.of(new AttestationPostingLine("1000", "DEBIT", "EUR", 1))));
    assertThrows(
        IllegalArgumentException.class, () -> AttestationPreimageProjectionFields.unsigned32(-1));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationPeriodCloseMutationProjection.projectInterimResultSweep(
                "interim-result-sweep",
                new dev.erst.fingrind.core.ReportingPeriod(EFFECTIVE_DATE, EFFECTIVE_DATE),
                "3200",
                1,
                List.of(),
                List.of(closePosting("wrong-kind", "system"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationPeriodCloseMutationProjection.projectInterimResultSweep(
                "interim-result-sweep",
                new dev.erst.fingrind.core.ReportingPeriod(EFFECTIVE_DATE, EFFECTIVE_DATE),
                "3200",
                1,
                List.of(),
                List.of(closePosting("interim-result-sweep", "wrong-origin", "system"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationPeriodCloseMutationProjection.projectInterimResultSweep(
                "interim-result-sweep",
                new dev.erst.fingrind.core.ReportingPeriod(EFFECTIVE_DATE, EFFECTIVE_DATE),
                "3200",
                1,
                List.of(),
                List.of(closePosting("interim-result-sweep", "interim-result-sweep", "cli"))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationPeriodCloseMutationProjection.projectInterimResultSweep(
                " ",
                new dev.erst.fingrind.core.ReportingPeriod(EFFECTIVE_DATE, EFFECTIVE_DATE),
                "3200",
                1,
                List.of(),
                List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationPeriodCloseMutationProjection.projectInterimResultSweep(
                "interim-result-sweep",
                new dev.erst.fingrind.core.ReportingPeriod(EFFECTIVE_DATE, EFFECTIVE_DATE),
                " ",
                1,
                List.of(),
                List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationPeriodClosePreimageProjection.projectInterimResultSweep(
                " ",
                new dev.erst.fingrind.core.ReportingPeriod(EFFECTIVE_DATE, EFFECTIVE_DATE),
                "3200",
                1,
                List.of(),
                List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> closePosting("interim-result-sweep", "interim-result-sweep", " "));
    assertThrows(
        IllegalArgumentException.class,
        () -> AttestationPlanMutationProjection.project(" ", List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationPlanMutationProjection.project(
                "plan",
                List.of(
                    new AttestationPlanOperationAuthorizer.ChildMutation(
                        0,
                        "post-entry",
                        new AttestationOperationPreimages(
                            AttestationPreimage.of(List.of()).encoded(),
                            AttestationPreimage.of(List.of()).encoded())))));
    assertTrue(AttestationPreimageProjectionFields.signedMoney("EUR", -1).isPresent());
    assertTrue(AttestationPreimageProjectionFields.signedMoney("EUR", 0).isPresent());
  }

  @Test
  void intentAndOperationValues_coverEachClosedMutationBranch() {
    AttestationAccountMutationIntent.DECLARATION.requireCompatible(
        AttestationEffectMutation.CREATE);
    AttestationAccountMutationIntent.DECLARATION.requireCompatible(AttestationEffectMutation.AMEND);
    AttestationAccountMutationIntent.DECLARATION.requireCompatible(
        AttestationEffectMutation.REACTIVATE);
    AttestationAccountMutationIntent.AMENDMENT.requireCompatible(AttestationEffectMutation.AMEND);
    AttestationAccountMutationIntent.RETIREMENT.requireCompatible(AttestationEffectMutation.RETIRE);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationAccountMutationIntent.DECLARATION.requireCompatible(
                AttestationEffectMutation.RETIRE));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationAccountMutationIntent.AMENDMENT.requireCompatible(
                AttestationEffectMutation.CREATE));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationAccountMutationIntent.RETIREMENT.requireCompatible(
                AttestationEffectMutation.CREATE));
    assertEquals(
        AttestationOperationKind.BACKUP_CREATED,
        AttestationOperationKind.forWireToken(AttestationOperationKind.BACKUP_CREATED.wireToken()));
    assertThrows(
        AttestationAuthorizationException.class,
        () -> AttestationOperationKind.forWireToken("unknown"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationOperationRequest(
                BOOK_ID,
                BigInteger.valueOf(-1),
                "post-entry",
                new byte[32],
                RECORDED_AT,
                new byte[0],
                new byte[0]));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationOperationRequest(
                BOOK_ID,
                BigInteger.ONE.shiftLeft(64),
                "post-entry",
                new byte[32],
                RECORDED_AT,
                new byte[0],
                new byte[0]));
    assertEquals(
        AttestationOperationKind.values().length, AttestationOperationKind.wireTokens().size());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationOperationRequest(
                BOOK_ID,
                BigInteger.ZERO,
                "post-entry",
                new byte[31],
                RECORDED_AT,
                new byte[0],
                new byte[0]));
  }

  @Test
  void genesisBindsTheExactPersistedBookIdentity() throws Exception {
    Path keyPath = temporaryDirectory.resolve("founder.fgatk");
    char[] passphrase = "test attestation passphrase".toCharArray();
    AttestationPublicCredential credential = AttestationKeyFiles.create(keyPath, passphrase);
    AttestationEvidence genesis;
    try (AttestationSigningCredential signer =
        new AttestationSigningCredential(UUID.randomUUID(), credential, keyPath, passphrase)) {
      genesis = AttestationGenesis.create(BOOK_ID, bookIdentity(), RECORDED_AT, List.of(signer));
      assertEquals(
          BOOK_ID, AttestationGenesis.requireMatchingBookIdentity(genesis, bookIdentity()));
      assertThrows(
          IllegalArgumentException.class,
          () -> AttestationGenesis.requireMatchingBookIdentity(genesis, differentBookIdentity()));
      assertThrows(
          IllegalArgumentException.class,
          () -> AttestationGenesis.create(BOOK_ID, bookIdentity(), RECORDED_AT, List.of()));
      assertThrows(
          IllegalArgumentException.class,
          () ->
              AttestationGenesis.create(
                  BOOK_ID,
                  bookIdentity(),
                  RECORDED_AT,
                  List.of(signer, signer, signer, signer, signer, signer)));
    }
    assertThrows(
        AttestationVerificationException.class,
        () ->
            AttestationVerifier.verifyBook(
                List.of(new AttestationEvidence(new byte[0], new byte[0], new byte[0]))));
  }

  @Test
  void genesisProjection_includesTheTradingInventoryDoctrineAndRejectsDuplicateFounders() {
    KeyPair keyPair = AttestationEd25519.generateKeyPair();
    AttestationFounder founder =
        new AttestationFounder(
            UUID.randomUUID(),
            AttestationEd25519.keyId(keyPair.getPublic()),
            AttestationSpki.of(keyPair.getPublic().getEncoded()));
    BookIdentity tradingBook =
        new BookIdentity(
            new EntityProfile(new BookEntityName("Trading Studio")),
            BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_TRADING,
            CurrencyUnit.of("EUR"),
            FiscalYearStart.parse("01-01"),
            LocalDate.parse("2026-01-01"));

    AttestationOperationPreimages projected =
        AttestationGenesisPreimageProjection.project(BOOK_ID, tradingBook, List.of(founder));
    AttestationPreimage request =
        AttestationPreimage.decode(
            projected.request(), AttestationAuthorizationFailure.PREIMAGE_INVALID);

    assertTrue(
        request.records().stream()
            .filter(record -> record.recordTypeTag() == 0x0101)
            .findFirst()
            .orElseThrow()
            .fields()
            .get(7)
            .isPresent());
    assertThrows(
        IllegalArgumentException.class,
        () -> AttestationGenesisFounders.requireDistinctCredentials(List.of(founder, founder)));
    AttestationFounder sameKeyDifferentPrincipal =
        new AttestationFounder(
            UUID.randomUUID(),
            founder.keyId(),
            AttestationSpki.of(keyPair.getPublic().getEncoded()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationGenesisFounders.requireDistinctCredentials(
                List.of(founder, sameKeyDifferentPrincipal)));
  }

  @Test
  void fileCustody_createsReopensAndRejectsInvalidExternalInputs() throws Exception {
    Path keyPath = temporaryDirectory.resolve("operator.fgatk");
    Path passphrasePath = temporaryDirectory.resolve("operator.passphrase");
    Files.writeString(passphrasePath, "correct horse battery staple\r\n");
    UUID principalId = UUID.randomUUID();
    try (AttestationSigningCredential created =
        AttestationKeyFiles.openOrCreateCredential(principalId, keyPath, passphrasePath)) {
      assertEquals(principalId, created.principalId());
      assertTrue(created.sign(new byte[] {1, 2, 3}).signature().length > 0);
    }
    try (AttestationSigningCredential reopened =
        AttestationKeyFiles.openOrCreateCredential(principalId, keyPath, passphrasePath)) {
      assertEquals(
          AttestationKeyFiles.loadPublicCredential(keyPath).keyId().length,
          reopened.publicCredential().keyId().length);
    }
    Path loneNewlinePath = temporaryDirectory.resolve("lone-newline.passphrase");
    Files.writeString(loneNewlinePath, "correct horse battery staple\n");
    try (AttestationSigningCredential ignored =
        AttestationKeyFiles.openOrCreateCredential(
            UUID.randomUUID(), temporaryDirectory.resolve("lone-newline.fgatk"), loneNewlinePath)) {
      assertTrue(ignored.publicCredential().keyId().length > 0);
    }
    Path noNewlinePath = temporaryDirectory.resolve("no-newline.passphrase");
    Files.writeString(noNewlinePath, "correct horse battery staple");
    try (AttestationSigningCredential ignored =
        AttestationKeyFiles.openOrCreateCredential(
            UUID.randomUUID(), temporaryDirectory.resolve("no-newline.fgatk"), noNewlinePath)) {
      assertTrue(ignored.publicCredential().keyId().length > 0);
    }
    Files.write(passphrasePath, new byte[0]);
    assertThrows(
        IllegalArgumentException.class,
        () -> AttestationKeyFiles.openExistingCredential(principalId, keyPath, passphrasePath));
    Files.writeString(passphrasePath, "\n");
    assertThrows(
        IllegalArgumentException.class,
        () -> AttestationKeyFiles.openExistingCredential(principalId, keyPath, passphrasePath));
    Files.write(passphrasePath, new byte[] {(byte) 0xC3, (byte) 0x28});
    assertThrows(
        IllegalArgumentException.class,
        () -> AttestationKeyFiles.openExistingCredential(principalId, keyPath, passphrasePath));
    Files.write(passphrasePath, new byte[4_097]);
    assertThrows(
        IllegalArgumentException.class,
        () -> AttestationKeyFiles.openExistingCredential(principalId, keyPath, passphrasePath));
    assertThrows(
        java.io.IOException.class,
        () ->
            AttestationKeyFiles.openExistingCredential(
                principalId, temporaryDirectory.resolve("missing.fgatk"), passphrasePath));
    byte[] invalidLength = Files.readAllBytes(keyPath);
    invalidLength[38] = 0;
    invalidLength[39] = 0;
    Path invalidLengthPath = temporaryDirectory.resolve("invalid-length.fgatk");
    Files.write(invalidLengthPath, invalidLength);
    assertThrows(
        IllegalArgumentException.class,
        () -> AttestationKeyFiles.loadPublicCredential(invalidLengthPath));
    byte[] overlongSpkiLength = Files.readAllBytes(keyPath);
    overlongSpkiLength[38] = (byte) 0x7F;
    overlongSpkiLength[39] = (byte) 0xFF;
    Path overlongSpkiLengthPath = temporaryDirectory.resolve("overlong-spki-length.fgatk");
    Files.write(overlongSpkiLengthPath, overlongSpkiLength);
    assertThrows(
        IllegalArgumentException.class,
        () -> AttestationKeyFiles.loadPublicCredential(overlongSpkiLengthPath));
    byte[] invalidSpki = Files.readAllBytes(keyPath);
    invalidSpki[41] = 0;
    Path invalidSpkiPath = temporaryDirectory.resolve("invalid-spki.fgatk");
    Files.write(invalidSpkiPath, invalidSpki);
    assertThrows(
        IllegalArgumentException.class,
        () -> AttestationKeyFiles.loadPublicCredential(invalidSpkiPath));
  }

  @Test
  void credentialSessionAndPublicValues_enforceCustodyBoundaries() throws Exception {
    Path keyPath = temporaryDirectory.resolve("credential.fgatk");
    Path passphrasePath = temporaryDirectory.resolve("credential.passphrase");
    Files.writeString(passphrasePath, "passphrase\n");
    AttestationPublicCredential credential =
        AttestationKeyFiles.create(keyPath, "passphrase".toCharArray());
    UUID principalId = UUID.randomUUID();
    try (AttestationSigningCredential signingCredential =
        new AttestationSigningCredential(
            principalId, credential, keyPath, "passphrase".toCharArray())) {
      signingCredential.close();
      assertThrows(IllegalStateException.class, () -> signingCredential.sign(new byte[] {1}));
    }
    assertThrows(
        IllegalArgumentException.class,
        () -> new AttestationSigningCredential(principalId, credential, keyPath, new char[0]));
    try (AttestationSigningCredential missing =
        new AttestationSigningCredential(
            principalId,
            credential,
            temporaryDirectory.resolve("missing-signing-key.fgatk"),
            "passphrase".toCharArray())) {
      assertThrows(IllegalArgumentException.class, () -> missing.sign(new byte[] {1}));
    }
    Path differentKeyPath = temporaryDirectory.resolve("different.fgatk");
    AttestationPublicCredential differentCredential =
        AttestationKeyFiles.create(differentKeyPath, "passphrase".toCharArray());
    try (AttestationSigningCredential mismatched =
        new AttestationSigningCredential(
            principalId, differentCredential, keyPath, "passphrase".toCharArray())) {
      assertThrows(IllegalArgumentException.class, () -> mismatched.sign(new byte[] {1}));
    }
    assertThrows(IllegalArgumentException.class, () -> AttestationSigningSession.open(List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationSigningSession.open(
                List.of(source(1), source(2), source(3), source(4), source(5), source(6))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationSigningSession.open(
                List.of(
                    new AttestationCredentialSource(principalId, keyPath, passphrasePath),
                    new AttestationCredentialSource(
                        principalId, temporaryDirectory.resolve("other.fgatk"), passphrasePath))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AttestationSigningSession.open(
                List.of(
                    new AttestationCredentialSource(UUID.randomUUID(), keyPath, passphrasePath),
                    new AttestationCredentialSource(
                        UUID.randomUUID(),
                        keyPath,
                        temporaryDirectory.resolve("other.passphrase")))));
    try (AttestationSigningSession session =
        AttestationSigningSession.open(
            List.of(new AttestationCredentialSource(principalId, keyPath, passphrasePath)))) {
      session.close();
      assertThrows(IllegalStateException.class, () -> session.authorize(nullOf()));
      assertThrows(
          IllegalStateException.class,
          () -> session.createReceipt(BOOK_ID, BigInteger.ZERO, new byte[32], RECORDED_AT));
    }
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AttestationCredentialSource(
                principalId,
                temporaryDirectory.resolve("same"),
                temporaryDirectory.resolve("same")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ApprovalReference(
                new ApprovalId("approval"),
                new ApprovalType("manual"),
                " ",
                "operator",
                ApprovalDecision.APPROVED,
                RECORDED_AT));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AttestationPublicCredential(new byte[] {1, 2, 3}));
  }

  private static AttestationTaxCodeSnapshot taxCode() {
    return new AttestationTaxCodeSnapshot("VAT", "Value-added tax", 210_000, "EXCLUSIVE", "SALE");
  }

  private AttestationCredentialSource source(int index) {
    return new AttestationCredentialSource(
        UUID.randomUUID(),
        temporaryDirectory.resolve("source-" + index + ".fgatk"),
        temporaryDirectory.resolve("source-" + index + ".passphrase"));
  }

  private static AttestationClosePostingSnapshot closePosting(String kind, String sourceChannel) {
    return closePosting(kind, kind, sourceChannel);
  }

  private static AttestationClosePostingSnapshot closePosting(
      String postingKind, String postingOriginKind, String sourceChannel) {
    return new AttestationClosePostingSnapshot(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "idempotency",
        "cause",
        postingKind,
        postingOriginKind,
        EFFECTIVE_DATE,
        RECORDED_AT,
        sourceChannel,
        List.of(
            new AttestationPostingLine("1000", "DEBIT", "EUR", 1),
            new AttestationPostingLine("3200", "CREDIT", "EUR", 1)));
  }

  private static BookIdentity bookIdentity() {
    return new BookIdentity(
        new EntityProfile(new BookEntityName("Acme Studio")),
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE,
        CurrencyUnit.of("EUR"),
        FiscalYearStart.parse("01-01"),
        LocalDate.parse("2026-01-01"));
  }

  private static BookIdentity differentBookIdentity() {
    return new BookIdentity(
        new EntityProfile(new BookEntityName("Other Studio")),
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE,
        CurrencyUnit.of("EUR"),
        FiscalYearStart.parse("01-01"),
        LocalDate.parse("2026-01-01"));
  }
}
