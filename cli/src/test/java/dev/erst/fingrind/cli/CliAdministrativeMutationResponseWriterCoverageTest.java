package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AttestationRegistryMutationResult;
import dev.erst.fingrind.contract.bookkeeping.AttestationVerificationFailure;
import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.ClosedFiscalYear;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.FiscalYearCloseResult;
import dev.erst.fingrind.contract.bookkeeping.InterimResultSweepResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublication;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationCompletion;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.bookkeeping.SweptInterimResult;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.AttestationKeyFileMetadata;
import dev.erst.fingrind.contract.runtime.GeneratedBookKeyFile;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PublicationTransactionArtifact;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.core.attestation.AttestationKeyFileCreation;
import dev.erst.fingrind.core.attestation.AttestationPublicCredential;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Exercises every administrative mutation transport branch through public outcome objects. */
class CliAdministrativeMutationResponseWriterCoverageTest extends CliResponseWriterTestSupport {
  private static final Path BOOK_PATH = Path.of("books", "current.sqlite");
  private static final Path KEY_PATH = Path.of("keys", "current.key");

  @Test
  void writesBookOpeningKeyGenerationAndRekeyOutcomesAcrossEveryOutputFamily() {
    ByteArrayOutputStream openedJson = new ByteArrayOutputStream();
    writer(openedJson)
        .writeOpenBookResult(
            BOOK_PATH,
            new OpenBookResult.Opened(
                Instant.parse("2026-07-21T10:20:30Z"),
                bookIdentity(),
                attestationTrustRoot(),
                genesisCommit(),
                List.of()),
            OutputMode.JSON);
    assertJsonContains(openedJson, "\"entityName\":\"Acme Studio\"");
    assertJsonContains(openedJson, "\"attestationCommit\":");

    ByteArrayOutputStream openedText = new ByteArrayOutputStream();
    writer(openedText)
        .writeOpenBookResult(
            BOOK_PATH,
            new OpenBookResult.Opened(
                Instant.parse("2026-07-21T10:20:30Z"),
                bookIdentity(),
                attestationTrustRoot(),
                genesisCommit(),
                List.of()),
            OutputMode.TEXT);
    String opened = openedText.toString(StandardCharsets.UTF_8);
    assertTrue(opened.contains("Book Initialized"));
    assertTrue(opened.contains("Attestation order"));
    assertTrue(opened.contains("Attestation head"));

    ByteArrayOutputStream rejectedOpen = new ByteArrayOutputStream();
    writer(rejectedOpen)
        .writeOpenBookResult(
            BOOK_PATH,
            new OpenBookResult.Rejected(new BookAdministrationRejection.BookAlreadyInitialized()),
            OutputMode.JSON);
    assertJsonContains(rejectedOpen, "\"code\":\"book-already-initialized\"");

    GeneratedBookKeyFile generatedKeyFile =
        new GeneratedBookKeyFile(
            CliPublicationTransactionTestFixtures.completedArtifact(KEY_PATH),
            "base64",
            256,
            "rw-------");
    ByteArrayOutputStream generatedKeyJson = new ByteArrayOutputStream();
    writer(generatedKeyJson).writeGenerateBookKeyFileResult(generatedKeyFile, OutputMode.JSON);
    assertJsonContains(generatedKeyJson, "\"entropyBits\":256");
    assertJsonContains(generatedKeyJson, "\"publicationTransaction\"");

    ByteArrayOutputStream generatedKeyText = new ByteArrayOutputStream();
    writer(generatedKeyText).writeGenerateBookKeyFileResult(generatedKeyFile, OutputMode.TEXT);
    String generatedKey = generatedKeyText.toString(StandardCharsets.UTF_8);
    assertTrue(generatedKey.contains("Book Key File Generated"));
    assertTrue(generatedKey.contains("Publication transaction"));

    Path authoritativeGeneratedSecretPath = Path.of("keys", "authoritative-rekey.key");
    ProtectedBookPairPublication authoritativeRekeyPublicationRetention =
        CliFixtureSupport.pairPublication(BOOK_PATH, authoritativeGeneratedSecretPath);
    PublicationTransactionArtifact authoritativeGeneratedSecretPublication =
        authoritativeRekeyPublicationRetention.generatedSecretPublication();
    ByteArrayOutputStream rekeyedJson = new ByteArrayOutputStream();
    writer(rekeyedJson)
        .writeRekeyBookResult(
            new RekeyBookResult.Rekeyed(
                BOOK_PATH,
                authoritativeGeneratedSecretPath,
                attestationCommit(),
                ProtectedBookPairPublicationCompletion.PUBLISHED,
                authoritativeRekeyPublicationRetention),
            OutputMode.JSON);
    assertJsonContains(rekeyedJson, "\"status\":\"ok\"");
    assertJsonContains(
        rekeyedJson,
        "\"newBookKeyFile\":\""
            + CliPublicPaths.absoluteValue(
                authoritativeGeneratedSecretPublication.publishedArtifactPath())
            + "\"");
    assertJsonContains(
        rekeyedJson,
        "\"path\":\""
            + CliPublicPaths.absoluteValue(
                authoritativeGeneratedSecretPublication.publishedArtifactPath())
            + "\"");
    assertFalse(rekeyedJson.toString(StandardCharsets.UTF_8).contains("retainedStage"));
    assertJsonContains(rekeyedJson, "\"publicationTransaction\"");

    ByteArrayOutputStream rekeyedText = new ByteArrayOutputStream();
    writer(rekeyedText)
        .writeRekeyBookResult(
            new RekeyBookResult.Rekeyed(
                BOOK_PATH,
                authoritativeGeneratedSecretPath,
                attestationCommit(),
                ProtectedBookPairPublicationCompletion.RECOVERED,
                authoritativeRekeyPublicationRetention),
            OutputMode.TEXT);
    String rekeyedTextOutput = rekeyedText.toString(StandardCharsets.UTF_8);
    assertTrue(rekeyedTextOutput.contains("Book Rekeyed"));
    assertTrue(
        rekeyedTextOutput.contains(
            CliTextDisplay.path(authoritativeGeneratedSecretPublication.publishedArtifactPath())));
    assertTrue(rekeyedTextOutput.contains("Publication transaction"));

    ByteArrayOutputStream rejectedRekey = new ByteArrayOutputStream();
    writer(rejectedRekey)
        .writeRekeyBookResult(
            new RekeyBookResult.Rejected(
                new BookMaintenanceRejection.BackupDestinationAlreadyExists(
                    Path.of("backup.sqlite"))),
            OutputMode.JSON);
    assertJsonContains(rejectedRekey, "\"code\":\"backup-destination-already-exists\"");

    assertThrows(
        IllegalArgumentException.class,
        () ->
            writer(new ByteArrayOutputStream())
                .writeOpenBookResult(
                    BOOK_PATH,
                    new OpenBookResult.Opened(
                        Instant.parse("2026-07-21T10:20:30Z"),
                        bookIdentity(),
                        attestationTrustRoot(),
                        genesisCommit(),
                        List.of()),
                    OutputMode.CSV));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            writer(new ByteArrayOutputStream())
                .writeGenerateBookKeyFileResult(generatedKeyFile, OutputMode.CSV));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            writer(new ByteArrayOutputStream())
                .writeRekeyBookResult(
                    new RekeyBookResult.Rekeyed(
                        BOOK_PATH,
                        KEY_PATH,
                        attestationCommit(),
                        ProtectedBookPairPublicationCompletion.PUBLISHED,
                        CliFixtureSupport.pairPublication(BOOK_PATH, KEY_PATH)),
                    OutputMode.CSV));

    assertEquals(
        0,
        CliAdministrativeExitCodes.exitCodeFor(
            new OpenBookResult.Opened(
                Instant.parse("2026-07-21T10:20:30Z"),
                bookIdentity(),
                attestationTrustRoot(),
                genesisCommit(),
                List.of())));
    assertEquals(
        2,
        CliAdministrativeExitCodes.exitCodeFor(
            new OpenBookResult.Rejected(new BookAdministrationRejection.BookAlreadyInitialized())));
    assertEquals(
        0,
        CliAdministrativeExitCodes.exitCodeFor(
            new RekeyBookResult.Rekeyed(
                BOOK_PATH,
                KEY_PATH,
                attestationCommit(),
                ProtectedBookPairPublicationCompletion.PUBLISHED,
                CliFixtureSupport.pairPublication(BOOK_PATH, KEY_PATH))));
    assertEquals(
        7,
        CliAdministrativeExitCodes.exitCodeFor(
            new RekeyBookResult.Rejected(
                new BookMaintenanceRejection.BackupDestinationAlreadyExists(
                    Path.of("backup.sqlite")))));
  }

  @Test
  void writesCloseOutcomesAndDelegatedAccountDeclarationWithoutLosingOutputPolicy() {
    ByteArrayOutputStream sweepJson = new ByteArrayOutputStream();
    writer(sweepJson)
        .writeInterimResultSweepResult(
            new InterimResultSweepResult.Swept(sampleSweptInterimResult(), attestationCommit()),
            OutputMode.JSON);
    assertJsonContains(sweepJson, "\"sweepOrder\":1");

    ByteArrayOutputStream sweepText = new ByteArrayOutputStream();
    writer(sweepText)
        .writeInterimResultSweepResult(
            new InterimResultSweepResult.Swept(sampleSweptInterimResult(), attestationCommit()),
            OutputMode.TEXT);
    assertTrue(sweepText.toString(StandardCharsets.UTF_8).contains("Interim Result Swept"));

    ByteArrayOutputStream rejectedSweep = new ByteArrayOutputStream();
    writer(rejectedSweep)
        .writeInterimResultSweepResult(
            new InterimResultSweepResult.Rejected(
                new BookAdministrationRejection.BookNotInitialized()),
            OutputMode.JSON);
    assertJsonContains(rejectedSweep, "\"code\":\"administration-book-not-initialized\"");

    ByteArrayOutputStream closeJson = new ByteArrayOutputStream();
    writer(closeJson)
        .writeFiscalYearCloseResult(
            new FiscalYearCloseResult.Closed(sampleClosedFiscalYear(), false, attestationCommit()),
            OutputMode.JSON);
    assertJsonContains(closeJson, "\"idempotentReplay\":false");

    ByteArrayOutputStream closeText = new ByteArrayOutputStream();
    writer(closeText)
        .writeFiscalYearCloseResult(
            new FiscalYearCloseResult.Closed(sampleClosedFiscalYear(), true, null),
            OutputMode.TEXT);
    assertTrue(closeText.toString(StandardCharsets.UTF_8).contains("Fiscal Year Already Closed"));

    ByteArrayOutputStream rejectedClose = new ByteArrayOutputStream();
    writer(rejectedClose)
        .writeFiscalYearCloseResult(
            new FiscalYearCloseResult.Rejected(
                new BookAdministrationRejection.BookNotInitialized()),
            OutputMode.JSON);
    assertJsonContains(rejectedClose, "\"code\":\"administration-book-not-initialized\"");

    ByteArrayOutputStream accountJson = new ByteArrayOutputStream();
    writer(accountJson)
        .writeDeclareAccountResult(
            new DeclareAccountResult.Declared(declaredCashAccount(), attestationCommit()),
            OutputMode.JSON);
    assertJsonContains(accountJson, "\"outcome\":\"declared\"");

    assertThrows(
        IllegalArgumentException.class,
        () ->
            writer(new ByteArrayOutputStream())
                .writeInterimResultSweepResult(
                    new InterimResultSweepResult.Swept(
                        sampleSweptInterimResult(), attestationCommit()),
                    OutputMode.CSV));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            writer(new ByteArrayOutputStream())
                .writeFiscalYearCloseResult(
                    new FiscalYearCloseResult.Closed(
                        sampleClosedFiscalYear(), false, attestationCommit()),
                    OutputMode.CSV));

    assertEquals(
        0,
        CliAdministrativeExitCodes.exitCodeFor(
            new InterimResultSweepResult.Swept(sampleSweptInterimResult(), attestationCommit())));
    assertEquals(
        2,
        CliAdministrativeExitCodes.exitCodeFor(
            new InterimResultSweepResult.Rejected(
                new BookAdministrationRejection.BookNotInitialized())));
    assertEquals(
        0,
        CliAdministrativeExitCodes.exitCodeFor(
            new FiscalYearCloseResult.Closed(
                sampleClosedFiscalYear(), false, attestationCommit())));
    assertEquals(
        2,
        CliAdministrativeExitCodes.exitCodeFor(
            new FiscalYearCloseResult.Rejected(
                new BookAdministrationRejection.BookNotInitialized())));
  }

  @Test
  void writesEveryCredentialRegistryMutationOutcomeAcrossItsSupportedOutputFamilies() {
    AttestationRegistryMutationResult.Mutated mutated =
        new AttestationRegistryMutationResult.Mutated(
            BOOK_PATH,
            "enroll-key",
            new dev.erst.fingrind.contract.bookkeeping.AttestationCommit(
                BigInteger.ONE, "0".repeat(64)));

    ByteArrayOutputStream json = new ByteArrayOutputStream();
    writer(json)
        .writeAttestationRegistryMutationResult(OperationId.ENROLL_KEY, mutated, OutputMode.JSON);
    assertJsonContains(json, "\"operationKind\":\"enroll-key\"");

    ByteArrayOutputStream text = new ByteArrayOutputStream();
    writer(text)
        .writeAttestationRegistryMutationResult(OperationId.ENROLL_KEY, mutated, OutputMode.TEXT);
    String renderedText = text.toString(StandardCharsets.UTF_8);
    assertTrue(renderedText.contains("Attestation Registry Updated"));
    assertTrue(renderedText.contains("Attestation order"));
    assertTrue(renderedText.contains("Attestation head"));
    assertTrue(renderedText.contains("0".repeat(64)));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            writer(new ByteArrayOutputStream())
                .writeAttestationRegistryMutationResult(
                    OperationId.ENROLL_KEY, mutated, OutputMode.CSV));

    ByteArrayOutputStream rejected = new ByteArrayOutputStream();
    writer(rejected)
        .writeAttestationRegistryMutationResult(
            OperationId.REVOKE_KEY,
            new AttestationRegistryMutationResult.Rejected(
                new BookMaintenanceRejection.BackupDestinationAlreadyExists(
                    Path.of("backup.sqlite"))),
            OutputMode.JSON);
    assertJsonContains(rejected, "\"code\":\"backup-destination-already-exists\"");

    ByteArrayOutputStream authorizationRejected = new ByteArrayOutputStream();
    writer(authorizationRejected)
        .writeAttestationRegistryMutationResult(
            OperationId.ALTER_POLICY,
            new AttestationRegistryMutationResult.AuthorizationRejected(
                AttestationVerificationFailure.QUORUM_BELOW),
            OutputMode.JSON);
    assertJsonContains(authorizationRejected, "\"code\":\"attestation-quorum-below\"");
    assertEquals(0, CliAttestationExitCodes.exitCodeFor(mutated));
    assertEquals(
        2,
        CliAttestationExitCodes.exitCodeFor(
            new AttestationRegistryMutationResult.Rejected(
                new BookMaintenanceRejection.BackupDestinationAlreadyExists(
                    Path.of("backup.sqlite")))));
    assertEquals(
        2,
        CliAttestationExitCodes.exitCodeFor(
            new AttestationRegistryMutationResult.AuthorizationRejected(
                AttestationVerificationFailure.QUORUM_BELOW)));
  }

  @Test
  void writesAttestationCredentialMetadataWithoutEverRenderingPrivateMaterial() {
    AttestationKeyFileMetadata metadata =
        new AttestationKeyFileMetadata(
            Path.of("keys", "operator.fgatk"), "MCowBQYDK2VwAyEApublic", "a1b2c3d4");
    AttestationKeyFileCreation created =
        new AttestationKeyFileCreation(
            CliPublicationTransactionTestFixtures.completedArtifact(
                metadata.attestationKeyFilePath()),
            generatedPublicCredential());

    ByteArrayOutputStream generatedText = new ByteArrayOutputStream();
    writer(generatedText).writeGeneratedAttestationKeyFileResult(created, OutputMode.TEXT);
    assertTrue(
        generatedText.toString(StandardCharsets.UTF_8).contains("Attestation Key File Generated"));

    ByteArrayOutputStream inspectedText = new ByteArrayOutputStream();
    writer(inspectedText).writeAttestationKeyFileMetadata(metadata, OutputMode.TEXT);
    assertTrue(inspectedText.toString(StandardCharsets.UTF_8).contains("Credential SPKI"));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            writer(new ByteArrayOutputStream())
                .writeAttestationKeyFileMetadata(metadata, OutputMode.CSV));
  }

  private static CliAdministrativeMutationResponseWriter writer(
      ByteArrayOutputStream outputStream) {
    return new CliAdministrativeMutationResponseWriter(outputChannel(outputStream));
  }

  private static AttestationPublicCredential generatedPublicCredential() {
    try {
      return new AttestationPublicCredential(
          KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic().getEncoded());
    } catch (NoSuchAlgorithmException exception) {
      throw new AssertionError("The Java runtime must provide Ed25519.", exception);
    }
  }

  private static dev.erst.fingrind.contract.bookkeeping.AttestationCommit genesisCommit() {
    return new dev.erst.fingrind.contract.bookkeeping.AttestationCommit(
        BigInteger.ZERO, "0".repeat(64));
  }

  private static SweptInterimResult sampleSweptInterimResult() {
    return new SweptInterimResult(
        1,
        new ReportingPeriod(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
        new AccountCode("3200"),
        List.of(CurrencyBalance.ofTotals(money("EUR", "0.00"), money("EUR", "10.00"))),
        Instant.parse("2026-04-30T12:00:00Z"),
        List.of(new PostingId("98be232b-af01-324d-b4fc-6f62636fae68")));
  }

  private static ClosedFiscalYear sampleClosedFiscalYear() {
    return new ClosedFiscalYear(
        1,
        new ReportingPeriod(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31")),
        new AccountCode("3000"),
        new AccountCode("3200"),
        new AccountCode("3300"),
        Instant.parse("2026-12-31T12:00:00Z"),
        List.of(
            new PostingId("98be232b-af01-324d-b4fc-6f62636fae68"),
            new PostingId("548200b1-9743-3000-a75c-17a99ebf79b7")));
  }
}
