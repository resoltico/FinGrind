package dev.erst.fingrind.core.attestation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookDoctrines;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FiscalYearStart;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises the exported file-custody genesis path without exposing a private key. */
class AttestationGenesisBoundaryTest {
  @TempDir Path temporaryDirectory;

  @Test
  void createsAndVerifiesASelfAuthorizingGenesisOperation() throws Exception {
    char[] passphrase = "test attestation passphrase".toCharArray();
    AttestationPublicCredential publicCredential =
        AttestationKeyFiles.create(temporaryDirectory.resolve("founder.fgatk"), passphrase);
    UUID principalId = UUID.fromString("10213243-5465-7687-98a9-babcbddceeff");
    try (AttestationSigningCredential signer =
        new AttestationSigningCredential(
            principalId,
            publicCredential,
            temporaryDirectory.resolve("founder.fgatk"),
            passphrase)) {
      AttestationEvidence genesis =
          AttestationGenesis.create(
              UUID.fromString("00112233-4455-6677-8899-aabbccddeeff"),
              bookIdentity(),
              Instant.parse("2026-07-21T00:00:00.123456789Z"),
              List.of(signer));
      AttestationVerification verification = AttestationVerifier.verifyBook(List.of(genesis));

      assertEquals(0, verification.headOrder().intValueExact());
      assertFalse(verification.reviewRequired());
      byte[] operationEnvelope = genesis.operationEnvelope();
      assertEquals(
          Instant.parse("2026-07-21T00:00:00.123Z"),
          AttestationOperationPayload.decode(
                  Arrays.copyOf(operationEnvelope, operationEnvelope.length - 114))
              .recordedAt());
    } finally {
      java.util.Arrays.fill(passphrase, '\0');
    }
  }

  @Test
  void opensOnlyAnExistingCredentialAndExposesOnlyAnExactOperationAuthorizer() throws Exception {
    Path keyPath = temporaryDirectory.resolve("operator.fgatk");
    Path passphrasePath = temporaryDirectory.resolve("operator.passphrase");
    Files.writeString(passphrasePath, "test attestation passphrase\n");
    AttestationPublicCredential credential =
        AttestationKeyFiles.create(keyPath, "test attestation passphrase".toCharArray());
    UUID principalId = UUID.fromString("10213243-5465-7687-98a9-babcbddceeff");
    UUID bookId = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
    AttestationEvidence genesis;
    try (AttestationSigningCredential signer =
        new AttestationSigningCredential(
            principalId, credential, keyPath, "test attestation passphrase".toCharArray())) {
      genesis =
          AttestationGenesis.create(
              bookId, bookIdentity(), Instant.parse("2026-07-21T00:00:00Z"), List.of(signer));
    }

    AttestationCredentialSource source =
        new AttestationCredentialSource(principalId, keyPath, passphrasePath);
    try (AttestationSigningSession session = AttestationSigningSession.open(List.of(source))) {
      AttestationEvidence signed =
          session.authorize(
              new AttestationOperationRequest(
                  bookId,
                  BigInteger.ZERO,
                  "book-genesis",
                  new byte[32],
                  Instant.parse("2026-07-21T00:00:00Z"),
                  genesis.requestPreimage(),
                  genesis.effectPreimage()));

      assertEquals(0, AttestationVerifier.verifyBook(List.of(signed)).headOrder().intValueExact());
    }

    Path missingKeyPath = temporaryDirectory.resolve("missing.fgatk");
    AttestationCredentialUseException failure =
        assertThrows(
            AttestationCredentialUseException.class,
            () ->
                AttestationSigningSession.open(
                    List.of(
                        new AttestationCredentialSource(
                            principalId, missingKeyPath, passphrasePath))));
    assertEquals(missingKeyPath, failure.credentialPath());
    assertInstanceOf(java.io.IOException.class, failure.getCause());
  }

  @Test
  void projectsAndSignsTheCompleteAccountRegistryEffectAtTheObservedHead() throws Exception {
    Path keyPath = temporaryDirectory.resolve("account-operator.fgatk");
    Path passphrasePath = temporaryDirectory.resolve("account-operator.passphrase");
    Files.writeString(passphrasePath, "test attestation passphrase\n");
    AttestationPublicCredential credential =
        AttestationKeyFiles.create(keyPath, "test attestation passphrase".toCharArray());
    UUID principalId = UUID.fromString("10213243-5465-7687-98a9-babcbddceeff");
    UUID bookId = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
    AttestationEvidence genesis;
    try (AttestationSigningCredential signer =
        new AttestationSigningCredential(
            principalId, credential, keyPath, "test attestation passphrase".toCharArray())) {
      genesis =
          AttestationGenesis.create(
              bookId, bookIdentity(), Instant.parse("2026-07-21T00:00:00Z"), List.of(signer));
    }
    AttestationAccountSnapshot account =
        new AttestationAccountSnapshot(
            new AccountCode("1010"),
            new AccountName("Operating cash"),
            AccountType.ASSET,
            AccountTaxonomy.empty(),
            null,
            true);
    AttestationOperationPreimages preimages =
        AttestationAccountMutationProjection.project(
            AttestationAccountMutationIntent.DECLARATION,
            "declare-account",
            account,
            account,
            AttestationEffectMutation.CREATE);

    try (AttestationSigningSession session =
        AttestationSigningSession.open(
            List.of(new AttestationCredentialSource(principalId, keyPath, passphrasePath)))) {
      AttestationEvidence declaration =
          session.authorize(
              new AttestationOperationRequest(
                  bookId,
                  BigInteger.ONE,
                  "declare-account",
                  AttestationVerifier.verifyBook(List.of(genesis)).operationHead(),
                  Instant.parse("2026-07-21T00:00:01Z"),
                  preimages.request(),
                  preimages.effect()));

      assertEquals(
          1,
          AttestationVerifier.verifyBook(List.of(genesis, declaration))
              .headOrder()
              .intValueExact());
    }
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
