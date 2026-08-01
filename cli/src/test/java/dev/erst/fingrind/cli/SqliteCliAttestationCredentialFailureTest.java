package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.contract.bookkeeping.AttestationFounderInput;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.contract.workflow.LedgerPlan;
import dev.erst.fingrind.contract.workflow.LedgerPlanId;
import dev.erst.fingrind.contract.workflow.LedgerStep;
import dev.erst.fingrind.contract.workflow.LedgerStepId;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.attestation.AttestationCredentialSource;
import dev.erst.fingrind.executor.AttestationFounderKeyTargetOccupiedException;
import java.io.ByteArrayInputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Field coverage for credential failures before any protected-book mutation can occur. */
class SqliteCliAttestationCredentialFailureTest extends CliBookWorkflowFixtureSupport {
  private static final UUID PRINCIPAL_ID = UUID.fromString("10213243-5465-7687-98a9-babcbddceeff");

  @Test
  void executePlan_enforcesCredentialPairingBeforeOpeningAnyProtectedBookResource() {
    CliBookPassphraseResolver passphraseResolver =
        new CliBookPassphraseResolver(
            new ByteArrayInputStream(new byte[0]),
            prompt -> {
              throw new AssertionError(
                  "Credential-pairing refusal must precede passphrase access.");
            });
    SqliteCliMutationWorkflow workflow =
        new SqliteCliMutationWorkflow(fixedClock(), passphraseResolver);
    Path bookFile = tempDirectory.resolve("missing.sqlite");
    BookAccess signedReadOnlyAccess =
        new BookAccess(
            bookFile,
            new BookAccess.PassphraseSource.KeyFile(tempDirectory.resolve("missing.key")),
            List.of(credentialSource("signed-read-only")));
    ContractDecision<?> signedReadOnlyDecision =
        workflow.executePlan(signedReadOnlyAccess, readOnlyPlan());
    assertFailure(
        signedReadOnlyDecision, ContractErrors.Descriptor.ATTESTATION_CREDENTIALS_NOT_ALLOWED);

    BookAccess unsignedMutatingAccess =
        new BookAccess(
            bookFile,
            new BookAccess.PassphraseSource.KeyFile(tempDirectory.resolve("missing.key")),
            List.of());
    ContractDecision<?> unsignedMutationDecision =
        workflow.executePlan(unsignedMutatingAccess, mutatingPlan());
    assertFailure(
        unsignedMutationDecision, ContractErrors.Descriptor.INVALID_ATTESTATION_CREDENTIAL);
    assertFalse(java.nio.file.Files.exists(bookFile));
  }

  @Test
  void mutationAuthorization_rejectsAbsentAndUnreadableCredentialsWithoutInvokingTheAction() {
    BookAccess noCredentials =
        new BookAccess(
            tempDirectory.resolve("book.sqlite"),
            new BookAccess.PassphraseSource.KeyFile(tempDirectory.resolve("book.key")),
            List.of());
    assertCredentialFailure(
        SqliteCliMutationAuthorization.withAttestationAuthorization(
            noCredentials,
            ignored -> {
              throw new AssertionError("Authorization action must not run.");
            }),
        noCredentials.bookFilePath());

    Path missingCredential = tempDirectory.resolve("missing.fgatk");
    BookAccess unreadableCredential =
        new BookAccess(
            tempDirectory.resolve("book.sqlite"),
            new BookAccess.PassphraseSource.KeyFile(tempDirectory.resolve("book.key")),
            List.of(
                new AttestationCredentialSource(
                    dev.erst.fingrind.core.attestation.AttestationCustodian.FILE_PKCS8,
                    PRINCIPAL_ID,
                    missingCredential,
                    tempDirectory.resolve("missing.passphrase"))));
    assertCredentialFailure(
        SqliteCliMutationAuthorization.withAttestationAuthorization(
            unreadableCredential,
            ignored -> {
              throw new AssertionError("Authorization action must not run.");
            }),
        missingCredential);
  }

  @Test
  void openBook_rejectsAnUnreadableFounderCredentialBeforeCreatingTheBook() {
    Path bookFile = tempDirectory.resolve("new-book.sqlite");
    BookAccess bookAccess =
        new BookAccess(
            bookFile, new BookAccess.PassphraseSource.KeyFile(writeBookKey(bookFile)), List.of());
    Path missingFounderKey = tempDirectory.resolve("missing-founder.fgatk");
    OpenBookCommand command =
        new OpenBookCommand(
            bookIdentity(),
            List.of(
                new AttestationFounderInput(
                    dev.erst.fingrind.core.attestation.AttestationCustodian.FILE_PKCS8,
                    PRINCIPAL_ID,
                    missingFounderKey,
                    tempDirectory.resolve("missing-founder.passphrase"))));
    CliBookPassphraseResolver passphraseResolver =
        new CliBookPassphraseResolver(
            new ByteArrayInputStream(new byte[0]),
            prompt -> {
              throw new AssertionError("A key file must not prompt.");
            });

    var decision =
        new SqliteCliLifecycleWorkflow(fixedClock(), passphraseResolver)
            .openBook(bookAccess, command);

    assertCredentialFailure(decision, missingFounderKey);
    assertFalse(java.nio.file.Files.exists(bookFile));
  }

  @Test
  void founderKeyTargetCollisionMapsToTheDedicatedNoClobberFailure() {
    Path founderKeyPath =
        tempDirectory.resolve("occupied-founder.fgatk").toAbsolutePath().normalize();

    ContractFailure failure =
        CliFounderKeyCollisionFailure.from(
            new AttestationFounderKeyTargetOccupiedException(
                founderKeyPath, new FileAlreadyExistsException(founderKeyPath.toString())));

    assertEquals(ContractErrors.Descriptor.SECRET_TARGET_OCCUPIED.code(), failure.code());
    assertEquals(founderKeyPath, Objects.requireNonNull(failure.paths(), "failure paths").path());
    assertEquals("--attestation-founder-key-file", failure.argument());
    assertEquals(
        "Generated attestation founder key target already exists and will not be overwritten.",
        failure.message());
    assertEquals(
        "Choose an absent --attestation-founder-key-file path before rerunning open-book.",
        failure.hint());
  }

  private static <T> void assertCredentialFailure(
      dev.erst.fingrind.contract.runtime.ContractDecision<T> decision, Path expectedPath) {
    var rejected =
        assertInstanceOf(
            dev.erst.fingrind.contract.runtime.ContractDecision.Rejected.class, decision);
    assertEquals(
        ContractErrors.Descriptor.INVALID_ATTESTATION_CREDENTIAL.code(), rejected.failure().code());
    assertEquals(
        expectedPath.toAbsolutePath().normalize(),
        java.util.Objects.requireNonNull(rejected.failure().paths()).path());
  }

  private static LedgerPlan readOnlyPlan() {
    return new LedgerPlan(
        new LedgerPlanId("read-only-plan"),
        List.of(new LedgerStep.InspectBook(new LedgerStepId("inspect-book"))));
  }

  private static LedgerPlan mutatingPlan() {
    return new LedgerPlan(
        new LedgerPlanId("mutating-plan"),
        List.of(
            new LedgerStep.DeclareAccount(
                new LedgerStepId("declare-cash"),
                new DeclareAccountCommand(
                    new AccountCode("1000"),
                    new AccountName("Cash"),
                    AccountType.ASSET,
                    new AccountTaxonomy(
                        AccountNodeKind.POSTABLE,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                        Optional.empty(),
                        Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT))))));
  }

  private AttestationCredentialSource credentialSource(String stem) {
    return new AttestationCredentialSource(
        dev.erst.fingrind.core.attestation.AttestationCustodian.FILE_PKCS8,
        PRINCIPAL_ID,
        tempDirectory.resolve(stem + ".fgatk"),
        tempDirectory.resolve(stem + ".passphrase"));
  }

  private static void assertFailure(
      ContractDecision<?> decision, ContractErrors.Descriptor expectedDescriptor) {
    ContractDecision.Rejected<?> rejected =
        assertInstanceOf(ContractDecision.Rejected.class, decision);
    assertEquals(expectedDescriptor.code(), rejected.failure().code());
  }
}
