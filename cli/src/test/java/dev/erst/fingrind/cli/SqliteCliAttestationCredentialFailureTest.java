package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.contract.bookkeeping.AttestationFounderInput;
import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.core.attestation.AttestationCredentialSource;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Field coverage for credential failures before any protected-book mutation can occur. */
class SqliteCliAttestationCredentialFailureTest extends CliBookWorkflowFixtureSupport {
  private static final UUID PRINCIPAL_ID = UUID.fromString("10213243-5465-7687-98a9-babcbddceeff");

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
                    PRINCIPAL_ID, missingCredential, tempDirectory.resolve("missing.passphrase"))));
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
}
