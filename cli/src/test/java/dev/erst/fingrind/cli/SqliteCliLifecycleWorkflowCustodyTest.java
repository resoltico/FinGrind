package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.contract.bookkeeping.AttestationFounderInput;
import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.OpenBookFailureDetails;
import dev.erst.fingrind.core.attestation.AttestationCustodian;
import dev.erst.fingrind.executor.AttestationGenesisFactory;
import dev.erst.fingrind.executor.AttestationGenesisPreparation;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.sqlite.SqliteAdministrationSession;
import dev.erst.fingrind.sqlite.SqliteOpenBookCompletionUncertainException;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Proves founder custody survives a response fault after SQLite has returned an opened outcome. */
class SqliteCliLifecycleWorkflowCustodyTest extends CliBookWorkflowFixtureSupport {
  private static final UUID FOUNDER_PRINCIPAL_ID =
      UUID.fromString("10213243-5465-7687-98a9-babcbddceeff");

  @Test
  void completeOpenedBook_mapsPostStoreResultProjectionFailureToCompletionUncertainty()
      throws Exception {
    Path founderKeyFile = tempDirectory.resolve("durable-founder.fgatk");
    Path founderPassphraseFile = tempDirectory.resolve("durable-founder.passphrase");
    writeSecureKey(founderPassphraseFile, "durable-founder-passphrase");
    AttestationFounderInput founder =
        new AttestationFounderInput(
            AttestationCustodian.FILE_PKCS8,
            FOUNDER_PRINCIPAL_ID,
            founderKeyFile,
            founderPassphraseFile);
    AttestationGenesisPreparation preparation =
        AttestationGenesisFactory.prepare(bookIdentity(), fixedClock().instant(), List.of(founder));
    var trustRoot = attestationTrustRoot();
    BookOpeningOutcome.Opened returnedOpenedOutcome =
        new BookOpeningOutcome.Opened(
            fixedClock().instant(),
            bookIdentity(),
            trustRoot,
            new AttestationCommit(trustRoot.headOrder(), trustRoot.operationHeadHex()));
    SqliteCliLifecycleWorkflow workflow =
        new SqliteCliLifecycleWorkflow(
            fixedClock(),
            new CliBookPassphraseResolver(
                new ByteArrayInputStream(new byte[0]),
                prompt -> {
                  throw new AssertionError(
                      "The returned-outcome test must not resolve a passphrase.");
                }),
            (opened, ignoredPreparation) -> {
              throw new IllegalStateException("simulated opened-result projection failure");
            });

    ContractDecision<OpenBookResult> decision =
        workflow.completeOpenedBook(
            tempDirectory.resolve("durable-book.sqlite"),
            returnedOpeningSession(returnedOpenedOutcome),
            new OpenBookCommand(bookIdentity(), List.of(founder)),
            preparation);
    assertInstanceOf(ContractDecision.Rejected.class, decision);
    var failure = decision.requireRejected();

    assertEquals("open-book-completion-uncertain", failure.code());
    OpenBookFailureDetails.OpenBookCompletionUncertain details =
        assertInstanceOf(
            OpenBookFailureDetails.OpenBookCompletionUncertain.class, failure.details());
    assertEquals(returnedOpenedOutcome.bookIdentity(), details.bookIdentity());
    assertEquals(returnedOpenedOutcome.attestationCommit(), details.reportedAttestationCommit());
    assertEquals(
        List.of(founderKeyFile.toAbsolutePath().normalize()),
        details.retainedFounderKeyArtifacts().stream()
            .map(publication -> publication.publishedArtifactPath())
            .toList());
    assertTrue(Files.exists(founderKeyFile));
  }

  @Test
  void completeOpenedBook_mapsAnUnacknowledgedCommitWithoutRollingBackFounderCustody()
      throws Exception {
    Path founderKeyFile = tempDirectory.resolve("uncertain-founder.fgatk");
    Path founderPassphraseFile = tempDirectory.resolve("uncertain-founder.passphrase");
    writeSecureKey(founderPassphraseFile, "uncertain-founder-passphrase");
    AttestationFounderInput founder =
        new AttestationFounderInput(
            AttestationCustodian.FILE_PKCS8,
            FOUNDER_PRINCIPAL_ID,
            founderKeyFile,
            founderPassphraseFile);
    AttestationGenesisPreparation preparation =
        AttestationGenesisFactory.prepare(bookIdentity(), fixedClock().instant(), List.of(founder));
    var trustRoot = attestationTrustRoot();
    BookOpeningOutcome.Opened prebuiltOpenedOutcome =
        new BookOpeningOutcome.Opened(
            fixedClock().instant(),
            bookIdentity(),
            trustRoot,
            new AttestationCommit(trustRoot.headOrder(), trustRoot.operationHeadHex()));
    SqliteOpenBookCompletionUncertainException completionFailure =
        new SqliteOpenBookCompletionUncertainException(
            prebuiltOpenedOutcome,
            new IllegalStateException("simulated COMMIT acknowledgement loss"));
    SqliteCliLifecycleWorkflow workflow =
        new SqliteCliLifecycleWorkflow(
            fixedClock(),
            new CliBookPassphraseResolver(
                new ByteArrayInputStream(new byte[0]),
                prompt -> {
                  throw new AssertionError(
                      "The returned-outcome test must not resolve a passphrase.");
                }));

    ContractDecision<OpenBookResult> decision =
        workflow.completeOpenedBook(
            tempDirectory.resolve("uncertain-book.sqlite"),
            failingOpenSession(completionFailure),
            new OpenBookCommand(bookIdentity(), List.of(founder)),
            preparation);
    assertInstanceOf(ContractDecision.Rejected.class, decision);
    var failure = decision.requireRejected();

    assertEquals("open-book-completion-uncertain", failure.code());
    OpenBookFailureDetails.OpenBookCompletionUncertain details =
        assertInstanceOf(
            OpenBookFailureDetails.OpenBookCompletionUncertain.class, failure.details());
    assertEquals(
        prebuiltOpenedOutcome.attestationTrustRoot(), details.reportedAttestationTrustRoot());
    assertEquals(prebuiltOpenedOutcome.attestationCommit(), details.reportedAttestationCommit());
    assertTrue(Files.exists(founderKeyFile));
  }

  @Test
  void completeOpenedBook_retainsFounderCustodyWhenStorageNeverReturnsAnOutcome() throws Exception {
    Path founderKeyFile = tempDirectory.resolve("pre-outcome-founder.fgatk");
    Path founderPassphraseFile = tempDirectory.resolve("pre-outcome-founder.passphrase");
    writeSecureKey(founderPassphraseFile, "pre-outcome-founder-passphrase");
    AttestationFounderInput founder =
        new AttestationFounderInput(
            AttestationCustodian.FILE_PKCS8,
            FOUNDER_PRINCIPAL_ID,
            founderKeyFile,
            founderPassphraseFile);
    AttestationGenesisPreparation preparation =
        AttestationGenesisFactory.prepare(bookIdentity(), fixedClock().instant(), List.of(founder));
    IllegalStateException storageFailure =
        new IllegalStateException("simulated pre-outcome failure");
    SqliteCliLifecycleWorkflow workflow =
        new SqliteCliLifecycleWorkflow(
            fixedClock(),
            new CliBookPassphraseResolver(
                new ByteArrayInputStream(new byte[0]),
                prompt -> {
                  throw new AssertionError(
                      "The returned-outcome test must not resolve a passphrase.");
                }));

    ContractDecision<OpenBookResult> decision =
        workflow.completeOpenedBook(
            tempDirectory.resolve("pre-outcome-book.sqlite"),
            failingOpenSession(storageFailure),
            new OpenBookCommand(bookIdentity(), List.of(founder)),
            preparation);

    assertEquals("open-book-preparation-artifacts-retained", decision.requireRejected().code());
    assertTrue(Files.exists(founderKeyFile));
  }

  @Test
  void completeOpenedBook_retainsFounderCustodyForAReturnedPreMutationRejection() throws Exception {
    Path founderKeyFile = tempDirectory.resolve("rejected-founder.fgatk");
    Path founderPassphraseFile = tempDirectory.resolve("rejected-founder.passphrase");
    writeSecureKey(founderPassphraseFile, "rejected-founder-passphrase");
    AttestationFounderInput founder =
        new AttestationFounderInput(
            AttestationCustodian.FILE_PKCS8,
            FOUNDER_PRINCIPAL_ID,
            founderKeyFile,
            founderPassphraseFile);
    AttestationGenesisPreparation preparation =
        AttestationGenesisFactory.prepare(bookIdentity(), fixedClock().instant(), List.of(founder));
    BookOpeningOutcome.Rejected returnedRejection =
        new BookOpeningOutcome.Rejected(
            new BookkeepingAdministrationRejection.BookAlreadyInitialized());
    SqliteCliLifecycleWorkflow workflow =
        new SqliteCliLifecycleWorkflow(
            fixedClock(),
            new CliBookPassphraseResolver(
                new ByteArrayInputStream(new byte[0]),
                prompt -> {
                  throw new AssertionError(
                      "The returned-rejection test must not resolve a passphrase.");
                }));

    ContractDecision<OpenBookResult> decision =
        workflow.completeOpenedBook(
            tempDirectory.resolve("rejected-book.sqlite"),
            returnedOpeningSession(returnedRejection),
            new OpenBookCommand(bookIdentity(), List.of(founder)),
            preparation);

    assertEquals("open-book-preparation-artifacts-retained", decision.requireRejected().code());
    assertTrue(Files.exists(founderKeyFile));
  }

  private static SqliteAdministrationSession returnedOpeningSession(BookOpeningOutcome outcome) {
    return (SqliteAdministrationSession)
        Proxy.newProxyInstance(
            SqliteAdministrationSession.class.getClassLoader(),
            new Class<?>[] {SqliteAdministrationSession.class},
            (proxy, method, ignoredArguments) ->
                switch (method.getName()) {
                  case "openAttestedBook" -> outcome;
                  case "close" -> null;
                  default ->
                      throw new AssertionError(
                          "Unexpected administration-session method: " + method.getName());
                });
  }

  private static SqliteAdministrationSession failingOpenSession(RuntimeException failure) {
    return (SqliteAdministrationSession)
        Proxy.newProxyInstance(
            SqliteAdministrationSession.class.getClassLoader(),
            new Class<?>[] {SqliteAdministrationSession.class},
            (proxy, method, ignoredArguments) ->
                switch (method.getName()) {
                  case "openAttestedBook" -> throw failure;
                  case "close" -> null;
                  default ->
                      throw new AssertionError(
                          "Unexpected administration-session method: " + method.getName());
                });
  }
}
