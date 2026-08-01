package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AttestationFounderInput;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.OpenBookFailureDetails;
import dev.erst.fingrind.core.ArtifactPublicationOutcomeUncertainException;
import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import dev.erst.fingrind.core.attestation.AttestationAdmissionRejectedException;
import dev.erst.fingrind.core.attestation.AttestationAuthorizationFailure;
import dev.erst.fingrind.core.attestation.AttestationCredentialSource;
import dev.erst.fingrind.core.attestation.AttestationKeyFiles;
import dev.erst.fingrind.core.attestation.AttestationSigningCredential;
import dev.erst.fingrind.core.attestation.AttestationSigningCredentialOpening;
import dev.erst.fingrind.core.attestation.AttestationSigningSession;
import dev.erst.fingrind.core.attestation.AttestationVerifier;
import dev.erst.fingrind.executor.maintenance.ProtectedBookAccess;
import dev.erst.fingrind.executor.maintenance.ProtectedBookPassphraseSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises the executor-owned credential custody and retained-preparation boundary. */
class AttestationCustodyBoundaryTest {
  private static final UUID PRINCIPAL_ID = UUID.fromString("10213243-5465-7687-98a9-babcbddceeff");
  private static final Instant RECORDED_AT = Instant.parse("2026-07-21T00:00:00Z");

  @TempDir Path temporaryDirectory;

  @BeforeEach
  void canonicalizeTemporaryDirectory() throws IOException {
    temporaryDirectory = temporaryDirectory.toRealPath();
  }

  @Test
  void createsGenesisAndScopesExistingCredentialUseToOneExecutorAction() throws IOException {
    AttestationCredentialSource source = createCredentialSource("founder");
    var preparation =
        AttestationGenesisFactory.prepare(
            ExecutorAccountingTestSupport.bookIdentity(),
            RECORDED_AT,
            List.of(
                new AttestationFounderInput(
                    dev.erst.fingrind.core.attestation.AttestationCustodian.FILE_PKCS8,
                    PRINCIPAL_ID,
                    source.encryptedKeyFilePath(),
                    source.passphraseFilePath())));

    assertEquals(
        0,
        AttestationVerifier.verifyBook(List.of(preparation.evidence()))
            .headOrder()
            .intValueExact());
    assertTrue(preparation.retainedFounderKeyArtifacts().isEmpty());
    try (AttestationSigningSession session =
        AttestationSigningSessionFactory.open(List.of(source))) {
      assertNotNull(session);
    }
    assertEquals(
        "authorized",
        AttestationMutationAuthorization.withAuthorizer(List.of(source), ignored -> "authorized"));
  }

  @Test
  void rejectsDuplicateFounderCredentialSelection() throws IOException {
    AttestationCredentialSource source = createCredentialSource("duplicate-source");

    AttestationAdmissionRejectedException failure =
        assertThrows(
            AttestationAdmissionRejectedException.class,
            () -> AttestationSigningSessionFactory.open(List.of(source, source)));

    assertEquals(AttestationAuthorizationFailure.DUPLICATE_PRINCIPAL, failure.failure());
  }

  @Test
  void validatesFounderInputsBeforeOpeningAndMapsAnOversizedCredentialSelectionToItsFirstPath()
      throws IOException {
    AttestationCredentialSource source = createCredentialSource("validated-founder");
    AttestationFounderInput founder =
        new AttestationFounderInput(
            dev.erst.fingrind.core.attestation.AttestationCustodian.FILE_PKCS8,
            PRINCIPAL_ID,
            source.encryptedKeyFilePath(),
            source.passphraseFilePath());

    AttestationGenesisFactory.validateFounderInputs(List.of(founder));
    AttestationCredentialException failure =
        assertThrows(
            AttestationCredentialException.class,
            () ->
                AttestationSigningSessionFactory.open(
                    java.util.Collections.nCopies(
                        dev.erst.fingrind.core.attestation.AttestationAuthorizationLimits
                                .MAXIMUM_QUORUM
                            + 1,
                        source)));
    assertEquals(source.encryptedKeyFilePath(), failure.credentialPath());
  }

  @Test
  void rejectsEmptyAndUnreadableMutationCredentialsAtTheCustodyBoundary() {
    IllegalArgumentException emptySelection =
        assertThrows(
            IllegalArgumentException.class,
            () -> AttestationMutationAuthorization.withAuthorizer(List.of(), ignored -> "unused"));
    assertEquals(
        "Protected-book mutation requires at least one attestation authorization credential.",
        emptySelection.getMessage());

    AttestationCredentialSource missingSource =
        new AttestationCredentialSource(
            dev.erst.fingrind.core.attestation.AttestationCustodian.FILE_PKCS8,
            PRINCIPAL_ID,
            temporaryDirectory.resolve("missing.fgatk"),
            temporaryDirectory.resolve("missing.passphrase"));
    AttestationCredentialException unreadable =
        assertThrows(
            AttestationCredentialException.class,
            () ->
                AttestationMutationAuthorization.withAuthorizer(
                    List.of(missingSource), ignored -> "unused"));
    assertEquals(missingSource.encryptedKeyFilePath(), unreadable.credentialPath());
  }

  @Test
  void preservesActionFailuresInsteadOfMisclassifyingThemAsCredentialFailures() throws IOException {
    AttestationCredentialSource source = createCredentialSource("action-source");

    IllegalArgumentException actionFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                AttestationMutationAuthorization.withAuthorizer(
                    List.of(source),
                    ignored -> {
                      throw new IllegalArgumentException("The posting request is invalid.");
                    }));

    assertEquals("The posting request is invalid.", actionFailure.getMessage());
  }

  @Test
  void genesisPreparationRetainsAnUnpublishedFounderStageFailure() {
    Path candidateKey = temporaryDirectory.resolve("candidate-founder.fgatk");
    ArtifactPublicationRetention retention =
        new ArtifactPublicationRetention(temporaryDirectory.resolve(".candidate-founder.tmp"));
    AttestationFounderKeyRetentionException stageFailure =
        new AttestationFounderKeyRetentionException(
            List.of(retainedArtifact(retention.retainedStagePath(), retention)),
            new IllegalStateException("simulated key publication failure"));

    AttestationFounderKeyRetentionException observed =
        assertThrows(
            AttestationFounderKeyRetentionException.class,
            () ->
                AttestationGenesisFactory.prepare(
                    ExecutorAccountingTestSupport.bookIdentity(),
                    RECORDED_AT,
                    List.of(founderInput(candidateKey)),
                    new AttestationGenesisFactory.FounderCredentialAccess() {
                      @Override
                      public AttestationSigningCredentialOpening openExisting(
                          AttestationFounderInput founder) {
                        throw new AssertionError("The candidate founder key must be missing.");
                      }

                      @Override
                      public AttestationSigningCredentialOpening openOrCreate(
                          AttestationFounderInput founder) {
                        throw stageFailure;
                      }
                    }));

    assertSame(stageFailure, observed.getCause());
    assertEquals(
        List.of(retainedArtifact(retention.retainedStagePath(), retention)),
        observed.retainedFounderKeyArtifacts());
  }

  @Test
  void genesisPreparationRetainsAnIndeterminateFounderCandidate() {
    Path candidateKey = temporaryDirectory.resolve("indeterminate-founder.fgatk");
    ArtifactPublicationRetention retention =
        new ArtifactPublicationRetention(temporaryDirectory.resolve(".indeterminate-founder.tmp"));
    ArtifactPublicationOutcomeUncertainException uncertainty =
        new ArtifactPublicationOutcomeUncertainException(
            candidateKey, retention, new IOException("simulated no-replace uncertainty"));

    AttestationFounderKeyRetentionException observed =
        assertThrows(
            AttestationFounderKeyRetentionException.class,
            () ->
                AttestationGenesisFactory.prepare(
                    ExecutorAccountingTestSupport.bookIdentity(),
                    RECORDED_AT,
                    List.of(founderInput(candidateKey)),
                    new AttestationGenesisFactory.FounderCredentialAccess() {
                      @Override
                      public AttestationSigningCredentialOpening openExisting(
                          AttestationFounderInput founder) {
                        throw new AssertionError("The candidate founder key must be missing.");
                      }

                      @Override
                      public AttestationSigningCredentialOpening openOrCreate(
                          AttestationFounderInput founder) {
                        throw uncertainty;
                      }
                    }));

    assertSame(uncertainty, observed.getCause());
    assertEquals(
        List.of(retainedArtifact(candidateKey, retention)), observed.retainedFounderKeyArtifacts());
  }

  @Test
  void genesisPreparationPreservesCredentialAndUnexpectedFailuresWithoutInventingArtifacts() {
    Path credentialPath = temporaryDirectory.resolve("credential-failure.fgatk");
    dev.erst.fingrind.core.attestation.AttestationCredentialUseException credentialUseFailure =
        new dev.erst.fingrind.core.attestation.AttestationCredentialUseException(
            credentialPath, "simulated credential use failure");

    AttestationCredentialException credentialFailure =
        assertThrows(
            AttestationCredentialException.class,
            () ->
                AttestationGenesisFactory.prepare(
                    ExecutorAccountingTestSupport.bookIdentity(),
                    RECORDED_AT,
                    List.of(founderInput(credentialPath)),
                    failingFounderAccess(credentialUseFailure)));
    assertEquals(credentialPath.toAbsolutePath().normalize(), credentialFailure.credentialPath());
    assertSame(credentialUseFailure, credentialFailure.getCause());

    Path runtimePath = temporaryDirectory.resolve("runtime-failure.fgatk");
    IllegalStateException runtimeFailure = new IllegalStateException("simulated runtime failure");
    IllegalStateException observed =
        assertThrows(
            IllegalStateException.class,
            () ->
                AttestationGenesisFactory.prepare(
                    ExecutorAccountingTestSupport.bookIdentity(),
                    RECORDED_AT,
                    List.of(founderInput(runtimePath)),
                    failingFounderAccess(runtimeFailure)));
    assertSame(runtimeFailure, observed);
  }

  @Test
  void genesisPreparationRetainsPriorFounderPublicationWhenLaterPreparationFails()
      throws IOException {
    AttestationCredentialSource source = createCredentialSource("existing-fixture");
    AttestationSigningCredential firstCredential =
        AttestationKeyFiles.openExistingCredential(
            PRINCIPAL_ID, source.encryptedKeyFilePath(), source.passphraseFilePath());
    Path priorKey = temporaryDirectory.resolve("prior-founder.fgatk");
    ArtifactPublicationRetention priorRetention =
        new ArtifactPublicationRetention(temporaryDirectory.resolve(".prior-founder.tmp"));
    ArtifactPublicationResult priorPublication =
        new ArtifactPublicationResult(priorKey, priorRetention);
    Path candidateKey = temporaryDirectory.resolve("candidate-founder.fgatk");
    AttestationCredentialException laterFailure =
        new AttestationCredentialException(
            candidateKey, new IOException("simulated credential failure"));
    AtomicInteger openCalls = new AtomicInteger();

    AttestationFounderKeyRetentionException observed =
        assertThrows(
            AttestationFounderKeyRetentionException.class,
            () ->
                AttestationGenesisFactory.prepare(
                    ExecutorAccountingTestSupport.bookIdentity(),
                    RECORDED_AT,
                    List.of(founderInput(priorKey), founderInput(candidateKey)),
                    new AttestationGenesisFactory.FounderCredentialAccess() {
                      @Override
                      public AttestationSigningCredentialOpening openExisting(
                          AttestationFounderInput founder) {
                        throw new AssertionError("Both founder keys must be missing.");
                      }

                      @Override
                      public AttestationSigningCredentialOpening openOrCreate(
                          AttestationFounderInput founder) {
                        if (openCalls.getAndIncrement() == 0) {
                          return new AttestationSigningCredentialOpening(
                              firstCredential, priorPublication);
                        }
                        throw laterFailure;
                      }
                    }));

    assertSame(laterFailure, observed.getCause());
    assertEquals(
        List.of(
            OpenBookFailureDetails.RetainedOpenBookPreparationArtifact.founderKey(
                priorPublication)),
        observed.retainedFounderKeyArtifacts());
  }

  @Test
  void generatedFounderKeyRemainsRetainedAfterSuccessfulGenesisPreparation() throws IOException {
    Path founderKeyPath = temporaryDirectory.resolve("generated-founder.fgatk");
    Path passphrasePath = founderKeyPath.resolveSibling("generated-founder.fgatk.passphrase");
    Files.writeString(passphrasePath, "test attestation passphrase\n");

    var preparation =
        AttestationGenesisFactory.prepare(
            ExecutorAccountingTestSupport.bookIdentity(),
            RECORDED_AT,
            List.of(founderInput(founderKeyPath)));

    assertEquals(1, preparation.retainedFounderKeyArtifacts().size());
    ArtifactPublicationResult publication = preparation.retainedFounderKeyArtifacts().getFirst();
    assertEquals(founderKeyPath.toRealPath(), publication.publishedArtifactPath());
    assertTrue(Files.isRegularFile(founderKeyPath));
    assertTrue(Files.isRegularFile(publication.retention().retainedStagePath()));
  }

  @Test
  void retainsNoPublicationForAMissingFounderResolvedByExistingTestCustody() throws IOException {
    AttestationCredentialSource source = createCredentialSource("existing-test-custody");
    AttestationSigningCredential credential =
        AttestationKeyFiles.openExistingCredential(
            PRINCIPAL_ID, source.encryptedKeyFilePath(), source.passphraseFilePath());
    Path declaredMissingFounderKeyPath =
        temporaryDirectory.resolve("declared-missing-founder.fgatk");

    var preparation =
        AttestationGenesisFactory.prepare(
            ExecutorAccountingTestSupport.bookIdentity(),
            RECORDED_AT,
            List.of(founderInput(declaredMissingFounderKeyPath)),
            new AttestationGenesisFactory.FounderCredentialAccess() {
              @Override
              public AttestationSigningCredentialOpening openExisting(
                  AttestationFounderInput founder) {
                throw new AssertionError("The declared founder key must remain missing.");
              }

              @Override
              public AttestationSigningCredentialOpening openOrCreate(
                  AttestationFounderInput founder) {
                return new AttestationSigningCredentialOpening(credential, null);
              }
            });

    assertTrue(preparation.retainedFounderKeyArtifacts().isEmpty());
  }

  @Test
  void preservesAllPublishedPassphraseSourceAlternativesAtTheMaintenanceBoundary() {
    Path bookPath = temporaryDirectory.resolve("book.sqlite");
    Path keyPath = temporaryDirectory.resolve("book.key");
    List<PassphraseSourceRoundTrip> roundTrips =
        List.of(
            roundTrip(bookPath, new BookAccess.PassphraseSource.KeyFile(keyPath)),
            roundTrip(bookPath, BookAccess.PassphraseSource.StandardInput.INSTANCE),
            roundTrip(bookPath, BookAccess.PassphraseSource.InteractivePrompt.INSTANCE));
    for (PassphraseSourceRoundTrip roundTrip : roundTrips) {
      assertEquals(roundTrip.source(), roundTrip.local().toPublished());
      assertInstanceOf(BookAccess.class, roundTrip.access().toPublished());
    }
  }

  private static PassphraseSourceRoundTrip roundTrip(
      Path bookPath, BookAccess.PassphraseSource source) {
    ProtectedBookPassphraseSource local = ProtectedBookPassphraseSource.fromPublished(source);
    return new PassphraseSourceRoundTrip(bookPath, source, local);
  }

  /** Expected local and published forms for one passphrase-source transport. */
  private record PassphraseSourceRoundTrip(
      Path bookPath, BookAccess.PassphraseSource source, ProtectedBookPassphraseSource local) {
    private ProtectedBookAccess access() {
      return new ProtectedBookAccess(bookPath, local);
    }
  }

  private static OpenBookFailureDetails.RetainedOpenBookPreparationArtifact retainedArtifact(
      Path path, ArtifactPublicationRetention retention) {
    return new OpenBookFailureDetails.RetainedOpenBookPreparationArtifact(
        OpenBookFailureDetails.OpenBookPreparationArtifactRole.ATTESTATION_FOUNDER_KEY,
        path,
        retention);
  }

  private static AttestationGenesisFactory.FounderCredentialAccess failingFounderAccess(
      RuntimeException failure) {
    return new AttestationGenesisFactory.FounderCredentialAccess() {
      @Override
      public AttestationSigningCredentialOpening openExisting(AttestationFounderInput founder) {
        throw new AssertionError("The candidate founder key must be missing.");
      }

      @Override
      public AttestationSigningCredentialOpening openOrCreate(AttestationFounderInput founder) {
        throw failure;
      }
    };
  }

  private AttestationCredentialSource createCredentialSource(String name) throws IOException {
    Path keyPath = temporaryDirectory.resolve(name + ".fgatk");
    Path passphrasePath = temporaryDirectory.resolve(name + ".passphrase");
    char[] passphrase = "test attestation passphrase".toCharArray();
    try {
      AttestationKeyFiles.create(keyPath, passphrase);
      Files.writeString(passphrasePath, "test attestation passphrase\n");
    } finally {
      java.util.Arrays.fill(passphrase, '\0');
    }
    return new AttestationCredentialSource(
        dev.erst.fingrind.core.attestation.AttestationCustodian.FILE_PKCS8,
        PRINCIPAL_ID,
        keyPath,
        passphrasePath);
  }

  private static AttestationFounderInput founderInput(Path keyPath) {
    return new AttestationFounderInput(
        dev.erst.fingrind.core.attestation.AttestationCustodian.FILE_PKCS8,
        PRINCIPAL_ID,
        keyPath,
        keyPath.resolveSibling(keyPath.getFileName() + ".passphrase"));
  }
}
