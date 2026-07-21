package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import dev.erst.fingrind.core.attestation.AttestationAccountMutationIntent;
import dev.erst.fingrind.core.attestation.AttestationAccountMutationProjection;
import dev.erst.fingrind.core.attestation.AttestationAccountSnapshot;
import dev.erst.fingrind.core.attestation.AttestationBackupAcknowledgement;
import dev.erst.fingrind.core.attestation.AttestationEffectMutation;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationGenesis;
import dev.erst.fingrind.core.attestation.AttestationKeyFiles;
import dev.erst.fingrind.core.attestation.AttestationLifecycleMutationProjection;
import dev.erst.fingrind.core.attestation.AttestationOperationSigner;
import dev.erst.fingrind.core.attestation.AttestationPlanOperationAuthorizer;
import dev.erst.fingrind.core.attestation.AttestationPublicCredential;
import dev.erst.fingrind.core.attestation.AttestationSigningCredential;
import dev.erst.fingrind.core.attestation.AttestationVerification;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Covers durable evidence append, stale-head admission, and immutable-SQL backstops. */
class SqliteAttestationEvidenceStoreTest extends SqlitePostingFactStoreTestSupport {
  private static final UUID BOOK_ID = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
  private static final UUID PRINCIPAL_ID = UUID.fromString("10213243-5465-7687-98a9-babcbddceeff");
  private static final byte[] ZERO_HEAD = new byte[32];

  @Test
  void appendsVerifiedGenesisRejectsAStaleHeadAndPreventsLaterEvidenceMutation() throws Exception {
    Path bookPath = tempDirectory.resolve("attested.sqlite");
    Path signerPath = tempDirectory.resolve("founder.fgatk");
    char[] passphrase = "sqlite attestation test passphrase".toCharArray();
    AttestationPublicCredential publicCredential =
        AttestationKeyFiles.create(signerPath, passphrase);
    try (AttestationSigningCredential signer =
        new AttestationSigningCredential(PRINCIPAL_ID, publicCredential, signerPath, passphrase)) {
      AttestationEvidence genesis =
          AttestationGenesis.create(
              BOOK_ID,
              attestationBookIdentity(),
              Instant.parse("2026-07-21T00:00:00Z"),
              List.of(signer));
      withStandaloneDatabase(
          bookAccess(bookPath),
          database -> {
            SqliteBookSchemaBootstrap.initializeBook(database);
            database.executeStatement("begin immediate");
            AttestationVerification verification =
                SqliteAttestationEvidenceStore.append(database, ZERO_HEAD, genesis);
            database.executeStatement("commit");

            assertEquals(0, verification.headOrder().intValueExact());
            assertEquals(1, countRows(database, "attestation_operation"));
            assertArrayEquals(
                genesis.operationEnvelope(),
                SqliteAttestationEvidenceStore.loadAll(database).getFirst().operationEnvelope());

            database.executeStatement("begin immediate");
            SqliteAttestationStaleHeadException staleHead =
                assertThrows(
                    SqliteAttestationStaleHeadException.class,
                    () -> SqliteAttestationEvidenceStore.append(database, ZERO_HEAD, genesis));
            database.executeStatement("rollback");
            assertArrayEquals(ZERO_HEAD, staleHead.observedHead());
            assertArrayEquals(verification.operationHead(), staleHead.currentHead());
            assertEquals(0, staleHead.currentOrder().intValueExact());

            SqliteNativeException updateFailure =
                assertThrows(
                    SqliteNativeException.class,
                    () ->
                        database.executeStatement(
                            "update attestation_operation set operation_head_hex = operation_head_hex"));
            assertEquals("SQLITE_CONSTRAINT_TRIGGER", updateFailure.resultName());
          });
    } finally {
      java.util.Arrays.fill(passphrase, '\0');
    }
  }

  @Test
  void signsAtTheHeadObservedInsideTheImmediateTransactionBeforePersistingAccountState()
      throws Exception {
    Path bookPath = tempDirectory.resolve("attested-account.sqlite");
    Path signerPath = tempDirectory.resolve("account-founder.fgatk");
    char[] passphrase = "sqlite attestation test passphrase".toCharArray();
    AttestationPublicCredential publicCredential =
        AttestationKeyFiles.create(signerPath, passphrase);
    try (AttestationSigningCredential signer =
        new AttestationSigningCredential(PRINCIPAL_ID, publicCredential, signerPath, passphrase)) {
      AttestationEvidence genesis =
          AttestationGenesis.create(
              BOOK_ID,
              attestationBookIdentity(),
              Instant.parse("2026-07-21T00:00:00Z"),
              List.of(signer));
      AttestationAccountSnapshot account =
          new AttestationAccountSnapshot(
              new AccountCode("1010"),
              new AccountName("Operating cash"),
              AccountType.ASSET,
              AccountTaxonomy.empty(),
              null,
              true);
      var preimages =
          AttestationAccountMutationProjection.project(
              AttestationAccountMutationIntent.DECLARATION,
              "declare-account",
              account,
              account,
              AttestationEffectMutation.CREATE);

      withStandaloneDatabase(
          bookAccess(bookPath),
          database -> {
            SqliteBookSchemaBootstrap.initializeBook(database);
            database.executeStatement("begin immediate");
            AttestationVerification genesisVerification =
                SqliteAttestationEvidenceStore.append(database, ZERO_HEAD, genesis);
            AttestationVerification verification =
                SqliteAttestationEvidenceStore.appendAuthorized(
                    database,
                    "declare-account",
                    Instant.parse("2026-07-21T00:00:01Z"),
                    preimages,
                    request -> {
                      assertEquals(BOOK_ID, request.bookId());
                      assertEquals(BigInteger.ONE, request.operationOrder());
                      assertArrayEquals(
                          genesisVerification.operationHead(), request.previousHead());
                      return AttestationOperationSigner.sign(
                          request.bookId(),
                          request.operationOrder(),
                          request.operationKind(),
                          request.previousHead(),
                          request.recordedAt(),
                          request.requestPreimage(),
                          request.effectPreimage(),
                          List.of(signer));
                    });
            database.executeStatement("commit");

            assertEquals(1, verification.headOrder().intValueExact());
            assertEquals(2, countRows(database, "attestation_operation"));
          });
    } finally {
      java.util.Arrays.fill(passphrase, '\0');
    }
  }

  @Test
  void backupAcknowledgement_replaysOnlyTheExactPersistedTuple() throws Exception {
    Path bookPath = tempDirectory.resolve("attested-backup-acknowledgement.sqlite");
    Path signerPath = tempDirectory.resolve("backup-founder.fgatk");
    char[] passphrase = "sqlite attestation test passphrase".toCharArray();
    AttestationPublicCredential publicCredential =
        AttestationKeyFiles.create(signerPath, passphrase);
    try (AttestationSigningCredential signer =
        new AttestationSigningCredential(PRINCIPAL_ID, publicCredential, signerPath, passphrase)) {
      AttestationEvidence genesis =
          AttestationGenesis.create(
              BOOK_ID,
              attestationBookIdentity(),
              Instant.parse("2026-07-21T00:00:00Z"),
              List.of(signer));
      withStandaloneDatabase(
          bookAccess(bookPath),
          database -> {
            SqliteBookSchemaBootstrap.initializeBook(database);
            database.executeStatement("begin immediate");
            AttestationVerification genesisVerification =
                SqliteAttestationEvidenceStore.append(database, ZERO_HEAD, genesis);
            AttestationBackupAcknowledgement acknowledgement =
                new AttestationBackupAcknowledgement(
                    UUID.fromString("10c1b469-8572-4019-a9f6-cd29e9783e7a"),
                    new byte[] {
                      1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22,
                      23, 24, 25, 26, 27, 28, 29, 30, 31, 32
                    },
                    genesisVerification.headOrder(),
                    genesisVerification.operationHead());
            AttestationVerification backupVerification =
                SqliteAttestationEvidenceStore.appendAuthorized(
                    database,
                    "backup-created",
                    Instant.parse("2026-07-21T00:00:01Z"),
                    AttestationLifecycleMutationProjection.backupBook(
                        "backup-created", acknowledgement),
                    request -> sign(request, signer),
                    acknowledgement);
            assertEquals(1, backupVerification.headOrder().intValueExact());
            AttestationVerification replayVerification =
                SqliteAttestationEvidenceStore.appendAuthorized(
                    database,
                    "backup-created",
                    Instant.parse("2026-07-21T00:00:02Z"),
                    AttestationLifecycleMutationProjection.backupBook(
                        "backup-created", acknowledgement),
                    request -> sign(request, signer),
                    acknowledgement);
            assertEquals(backupVerification.headOrder(), replayVerification.headOrder());
            assertArrayEquals(
                backupVerification.operationHead(), replayVerification.operationHead());
            assertEquals(2, countRows(database, "attestation_operation"));

            AttestationBackupAcknowledgement conflictingAcknowledgement =
                new AttestationBackupAcknowledgement(
                    acknowledgement.backupId(),
                    new byte[] {
                      32, 31, 30, 29, 28, 27, 26, 25, 24, 23, 22, 21, 20, 19, 18, 17, 16, 15, 14,
                      13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1
                    },
                    acknowledgement.sourceOrder(),
                    acknowledgement.sourceOperationHead());
            SqliteAttestationBackupAcknowledgementConflictException conflict =
                assertThrows(
                    SqliteAttestationBackupAcknowledgementConflictException.class,
                    () ->
                        SqliteAttestationEvidenceStore.appendAuthorized(
                            database,
                            "backup-created",
                            Instant.parse("2026-07-21T00:00:03Z"),
                            AttestationLifecycleMutationProjection.backupBook(
                                "backup-created", conflictingAcknowledgement),
                            request -> sign(request, signer),
                            conflictingAcknowledgement));
            assertEquals(acknowledgement.backupId(), conflict.backupId());
            database.executeStatement("commit");
          });
    } finally {
      java.util.Arrays.fill(passphrase, '\0');
    }
  }

  @Test
  void planAggregation_requiresAChildMutationAndSignsOnlyTheFinalOperation() throws Exception {
    Path bookPath = tempDirectory.resolve("attested-plan.sqlite");
    Path signerPath = tempDirectory.resolve("plan-founder.fgatk");
    char[] passphrase = "sqlite attestation test passphrase".toCharArray();
    AttestationPublicCredential publicCredential =
        AttestationKeyFiles.create(signerPath, passphrase);
    try (AttestationSigningCredential signer =
        new AttestationSigningCredential(PRINCIPAL_ID, publicCredential, signerPath, passphrase)) {
      AttestationEvidence genesis =
          AttestationGenesis.create(
              BOOK_ID,
              attestationBookIdentity(),
              Instant.parse("2026-07-21T00:00:00Z"),
              List.of(signer));
      AttestationAccountSnapshot account =
          new AttestationAccountSnapshot(
              new AccountCode("1010"),
              new AccountName("Operating cash"),
              AccountType.ASSET,
              AccountTaxonomy.empty(),
              null,
              true);
      var preimages =
          AttestationAccountMutationProjection.project(
              AttestationAccountMutationIntent.DECLARATION,
              "declare-account",
              account,
              account,
              AttestationEffectMutation.CREATE);
      withStandaloneDatabase(
          bookAccess(bookPath),
          database -> {
            SqliteBookSchemaBootstrap.initializeBook(database);
            database.executeStatement("begin immediate");
            SqliteAttestationEvidenceStore.append(database, ZERO_HEAD, genesis);
            AttestationPlanOperationAuthorizer authorizer =
                new AttestationPlanOperationAuthorizer(request -> sign(request, signer));
            IllegalArgumentException emptyPlan =
                assertThrows(
                    IllegalArgumentException.class,
                    () ->
                        SqliteAttestationEvidenceStore.appendPlanAuthorized(
                            database,
                            "monthly-close",
                            Instant.parse("2026-07-21T00:00:01Z"),
                            authorizer));
            assertEquals(
                "execute-plan did not produce a mutating child step.", emptyPlan.getMessage());

            authorizer.enterStep(0);
            AttestationVerification persisted =
                SqliteAttestationEvidenceStore.appendAuthorized(
                    database,
                    "declare-account",
                    Instant.parse("2026-07-21T00:00:01Z"),
                    preimages,
                    authorizer);
            assertEquals(0, persisted.headOrder().intValueExact());
            AttestationVerification finalVerification =
                SqliteAttestationEvidenceStore.appendPlanAuthorized(
                    database, "monthly-close", Instant.parse("2026-07-21T00:00:02Z"), authorizer);
            assertEquals(1, finalVerification.headOrder().intValueExact());
            assertEquals(2, countRows(database, "attestation_operation"));
            database.executeStatement("commit");
          });
    } finally {
      java.util.Arrays.fill(passphrase, '\0');
    }
  }

  @Test
  void authorizedMutation_requiresPersistedGenesisBeforeItMayPromptTheSigner() throws Exception {
    Path bookPath = tempDirectory.resolve("unattested-mutation.sqlite");
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          SqliteBookSchemaBootstrap.initializeBook(database);
          database.executeStatement("begin immediate");
          IllegalStateException failure =
              assertThrows(
                  IllegalStateException.class,
                  () ->
                      SqliteAttestationEvidenceStore.appendAuthorized(
                          database,
                          "declare-account",
                          Instant.parse("2026-07-21T00:00:00Z"),
                          new dev.erst.fingrind.core.attestation.AttestationOperationPreimages(
                              new byte[] {1}, new byte[] {2}),
                          ignored -> {
                            throw new AssertionError("The signer must not be consulted.");
                          }));
          assertEquals(
              "Protected-book mutation requires a persisted attestation genesis.",
              failure.getMessage());
          database.executeStatement("rollback");
        });
  }

  @Test
  void evidenceBoundary_rejectsMalformedHeadsOrdersAndCandidateChainsBeforePersistence()
      throws Exception {
    Path bookPath = tempDirectory.resolve("invalid-evidence-candidate.sqlite");
    AttestationEvidence genesis = genesis(tempDirectory.resolve("invalid-candidate-founder.fgatk"));
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          SqliteBookSchemaBootstrap.initializeBook(database);
          assertThrows(
              IllegalArgumentException.class,
              () -> SqliteAttestationEvidenceStore.append(database, new byte[31], genesis));

          database.executeStatement("begin immediate");
          AttestationVerification verification =
              SqliteAttestationEvidenceStore.append(database, ZERO_HEAD, genesis);
          IllegalArgumentException invalidCandidate =
              assertThrows(
                  IllegalArgumentException.class,
                  () ->
                      SqliteAttestationEvidenceStore.append(
                          database, verification.operationHead(), genesis));
          assertEquals("attestation-previous-head-invalid", invalidCandidate.getMessage());
          database.executeStatement("rollback");
        });
  }

  @Test
  void durableEvidenceReader_rejectsNoncanonicalOrdersAndMalformedBase64() throws Exception {
    Path noncanonicalBookPath = tempDirectory.resolve("noncanonical-order.sqlite");
    withStandaloneDatabase(
        bookAccess(noncanonicalBookPath),
        database -> {
          SqliteBookSchemaBootstrap.initializeBook(database);
          insertRawEvidence(database, "0000000000000001", "AA==", "AA==", "AA==", "f".repeat(64));
          IllegalStateException failure =
              assertThrows(
                  IllegalStateException.class,
                  () -> SqliteAttestationEvidenceStore.loadAll(database));
          assertEquals(
              "Persisted attestation operation order is not a canonical contiguous sequence.",
              failure.getMessage());
        });

    Path malformedBase64BookPath = tempDirectory.resolve("malformed-base64.sqlite");
    withStandaloneDatabase(
        bookAccess(malformedBase64BookPath),
        database -> {
          SqliteBookSchemaBootstrap.initializeBook(database);
          insertRawEvidence(database, "0000000000000000", "!", "AA==", "AA==", "e".repeat(64));
          IllegalStateException failure =
              assertThrows(
                  IllegalStateException.class,
                  () -> SqliteAttestationEvidenceStore.loadAll(database));
          assertEquals("Persisted attestation evidence is not valid base64.", failure.getMessage());
        });
  }

  @Test
  void authorizedOperations_rejectPersistedCorruptionBeforeTheyConsultCustody() throws Exception {
    Path bookPath = tempDirectory.resolve("corrupt-persisted-evidence.sqlite");
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          SqliteBookSchemaBootstrap.initializeBook(database);
          insertRawEvidence(database, "0000000000000000", "AA==", "AA==", "AA==", "d".repeat(64));
          database.executeStatement("begin immediate");
          IllegalStateException mutationFailure =
              assertThrows(
                  IllegalStateException.class,
                  () ->
                      SqliteAttestationEvidenceStore.appendAuthorized(
                          database,
                          "declare-account",
                          Instant.parse("2026-07-21T00:00:00Z"),
                          new dev.erst.fingrind.core.attestation.AttestationOperationPreimages(
                              new byte[] {1}, new byte[] {2}),
                          ignored -> {
                            throw new AssertionError("Custody must not see corrupted evidence.");
                          }));
          assertTrue(
              java.util.Objects.requireNonNullElse(mutationFailure.getMessage(), "")
                  .startsWith("Persisted attestation evidence violates its canonical chain:"));
          database.executeStatement("rollback");
        });

    Path planBookPath = tempDirectory.resolve("missing-plan-genesis.sqlite");
    withStandaloneDatabase(
        bookAccess(planBookPath),
        database -> {
          SqliteBookSchemaBootstrap.initializeBook(database);
          AttestationPlanOperationAuthorizer authorizer =
              new AttestationPlanOperationAuthorizer(
                  ignored -> {
                    throw new AssertionError("Custody must not be consulted without genesis.");
                  });
          authorizer.enterStep(0);
          authorizer.collectChildMutation(
              "declare-account",
              new dev.erst.fingrind.core.attestation.AttestationOperationPreimages(
                  new byte[] {1}, new byte[] {2}));
          IllegalStateException planFailure =
              assertThrows(
                  IllegalStateException.class,
                  () ->
                      SqliteAttestationEvidenceStore.appendPlanAuthorized(
                          database,
                          "missing-genesis-plan",
                          Instant.parse("2026-07-21T00:00:00Z"),
                          authorizer));
          assertEquals(
              "Protected-book mutation requires a persisted attestation genesis.",
              planFailure.getMessage());
        });
  }

  private static void insertRawEvidence(
      SqliteNativeDatabase database,
      String order,
      String envelope,
      String request,
      String effect,
      String operationHead) {
    try (SqliteNativeStatement statement = database.prepare(SqliteAttestationEvidenceSql.INSERT)) {
      statement.bindText(1, order);
      statement.bindText(2, envelope);
      statement.bindText(3, request);
      statement.bindText(4, effect);
      statement.bindText(5, operationHead);
      statement.step();
    }
  }

  private static AttestationEvidence genesis(Path signerPath) throws IOException {
    char[] passphrase = "sqlite attestation test passphrase".toCharArray();
    AttestationPublicCredential publicCredential =
        AttestationKeyFiles.create(signerPath, passphrase);
    try (AttestationSigningCredential signer =
        new AttestationSigningCredential(PRINCIPAL_ID, publicCredential, signerPath, passphrase)) {
      return AttestationGenesis.create(
          BOOK_ID,
          attestationBookIdentity(),
          Instant.parse("2026-07-21T00:00:00Z"),
          List.of(signer));
    } finally {
      java.util.Arrays.fill(passphrase, '\0');
    }
  }

  private static AttestationEvidence sign(
      dev.erst.fingrind.core.attestation.AttestationOperationRequest request,
      AttestationSigningCredential signer) {
    return AttestationOperationSigner.sign(
        request.bookId(),
        request.operationOrder(),
        request.operationKind(),
        request.previousHead(),
        request.recordedAt(),
        request.requestPreimage(),
        request.effectPreimage(),
        List.of(signer));
  }

  private static BookIdentity attestationBookIdentity() {
    return new BookIdentity(
        new EntityProfile(new BookEntityName("Acme Studio")),
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE,
        CurrencyUnit.of("EUR"),
        FiscalYearStart.parse("01-01"),
        LocalDate.parse("2026-01-01"));
  }
}
