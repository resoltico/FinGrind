package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** End-to-end CLI coverage for backup and restore workflow continuity. */
class FinGrindCliBackupRestoreWorkflowTest extends CliWorkflowFixtureSupport {
  @Test
  void run_backupRestoreAndTrialBalanceThroughDefaultSqliteWorkflowPreservesReadableFacts()
      throws IOException {
    Path requestFile = writeRequest(validRequestJson());
    Path declareCashFile =
        writeNamedRequest("restore-declare-cash.json", declareAccountJson("1000", "Cash", "DEBIT"));
    Path declareRevenueFile =
        writeNamedRequest(
            "restore-declare-revenue.json", declareAccountJson("2000", "Revenue", "CREDIT"));
    Path bookFilePath = tempDirectory.resolve("restore-books").resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    Path backupFilePath = tempDirectory.resolve("restore-books").resolve("entity-backup.sqlite");
    Path backupKeyFilePath = tempDirectory.resolve("restore-books").resolve("entity-backup.key");
    Path restoredBookFilePath =
        tempDirectory.resolve("restore-books").resolve("entity-restored.sqlite");
    Path restoredBookKeyFilePath =
        tempDirectory.resolve("restore-books").resolve("entity-restored.key");

    assertEquals(
        0,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock())
            .run(jsonArguments(openBookKeyFileArguments(bookFilePath, bookKeyFilePath))));
    assertEquals(
        0,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock())
            .run(
                attestedArguments(
                    "declare-account",
                    "--book-file",
                    bookFilePath.toString(),
                    "--book-key-file",
                    bookKeyFilePath.toString(),
                    "--request-file",
                    declareCashFile.toString())));
    assertEquals(
        0,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock())
            .run(
                attestedArguments(
                    "declare-account",
                    "--book-file",
                    bookFilePath.toString(),
                    "--book-key-file",
                    bookKeyFilePath.toString(),
                    "--request-file",
                    declareRevenueFile.toString())));
    assertEquals(
        0,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock())
            .run(
                attestedJsonArguments(
                    "record-sale-settled",
                    "--book-file",
                    bookFilePath.toString(),
                    "--book-key-file",
                    bookKeyFilePath.toString(),
                    "--request-file",
                    requestFile.toString())));
    ByteArrayOutputStream snapshotVerificationOutput = new ByteArrayOutputStream();
    assertEquals(
        0,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(snapshotVerificationOutput),
                fixedClock())
            .run(
                jsonArguments(
                    "verify-book",
                    "--book-file",
                    bookFilePath.toString(),
                    "--book-key-file",
                    bookKeyFilePath.toString())));
    JsonNode snapshotVerification =
        new ObjectMapper().readTree(snapshotVerificationOutput.toByteArray());
    assertEquals("ok", snapshotVerification.path("status").stringValue());
    String snapshotBookId = snapshotVerification.path("payload").path("bookId").stringValue();
    String snapshotHeadOrder =
        snapshotVerification
            .path("payload")
            .path("verifiedAttestationHead")
            .path("operationOrder")
            .stringValue();
    String snapshotOperationHead =
        snapshotVerification
            .path("payload")
            .path("verifiedAttestationHead")
            .path("operationHead")
            .stringValue();
    assertEquals(
        0,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock())
            .run(
                attestedJsonArguments(
                    "backup-book",
                    "--book-file",
                    bookFilePath.toString(),
                    "--book-key-file",
                    bookKeyFilePath.toString(),
                    "--backup-file",
                    backupFilePath.toString(),
                    "--backup-id",
                    "018f0000-0000-7000-8000-000000000003",
                    "--new-backup-key-file",
                    backupKeyFilePath.toString())));
    ByteArrayOutputStream restoreOutput = new ByteArrayOutputStream();
    assertEquals(
        0,
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(restoreOutput), fixedClock())
            .run(
                attestedArgumentsForBook(
                    bookFilePath,
                    "restore-book",
                    "--book-file",
                    restoredBookFilePath.toString(),
                    "--new-book-key-file",
                    restoredBookKeyFilePath.toString(),
                    "--backup-file",
                    backupFilePath.toString(),
                    "--backup-key-file",
                    backupKeyFilePath.toString(),
                    "--output",
                    "json")));
    JsonNode restoreEnvelope = new ObjectMapper().readTree(restoreOutput.toByteArray());
    assertEquals("ok", restoreEnvelope.path("status").stringValue());
    assertEquals(
        CliPublicPaths.absoluteValue(restoredBookFilePath),
        restoreEnvelope.path("payload").path("bookFile").stringValue());
    assertEquals(
        CliPublicPaths.absoluteValue(restoredBookKeyFilePath),
        restoreEnvelope.path("payload").path("bookKeyFilePath").stringValue());
    ByteArrayOutputStream restoredVerificationOutput = new ByteArrayOutputStream();
    assertEquals(
        0,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(restoredVerificationOutput),
                fixedClock())
            .run(
                jsonArguments(
                    "verify-book",
                    "--book-file",
                    restoredBookFilePath.toString(),
                    "--book-key-file",
                    restoredBookKeyFilePath.toString())));
    JsonNode restoredVerification =
        new ObjectMapper().readTree(restoredVerificationOutput.toByteArray());
    assertEquals("ok", restoredVerification.path("status").stringValue());
    assertEquals(snapshotBookId, restoredVerification.path("payload").path("bookId").stringValue());
    assertEquals(
        new BigInteger(snapshotHeadOrder).add(BigInteger.ONE).toString(),
        restoredVerification
            .path("payload")
            .path("verifiedAttestationHead")
            .path("operationOrder")
            .stringValue());
    assertEquals(
        snapshotOperationHead,
        restoredVerification.path("payload").path("previousHead").stringValue());
    assertEquals(
        restoreEnvelope
            .path("payload")
            .path("attestationCommit")
            .path("operationHead")
            .stringValue(),
        restoredVerification
            .path("payload")
            .path("verifiedAttestationHead")
            .path("operationHead")
            .stringValue());

    ByteArrayOutputStream trialBalanceOutput = new ByteArrayOutputStream();
    assertEquals(
        0,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(trialBalanceOutput),
                fixedClock())
            .run(
                jsonArguments(
                    "trial-balance",
                    "--book-file",
                    restoredBookFilePath.toString(),
                    "--book-key-file",
                    restoredBookKeyFilePath.toString())));
    assertJsonContains(trialBalanceOutput, "\"status\":\"ok\"");
    assertJsonContains(trialBalanceOutput, "\"family\":\"trial-balance\"");

    ByteArrayOutputStream wrongKeyOutput = new ByteArrayOutputStream();
    assertEquals(
        6,
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(wrongKeyOutput), fixedClock())
            .run(
                jsonArguments(
                    "trial-balance",
                    "--book-file",
                    restoredBookFilePath.toString(),
                    "--book-key-file",
                    backupKeyFilePath.toString())));
    JsonNode wrongKeyEnvelope = new ObjectMapper().readTree(wrongKeyOutput.toByteArray());
    assertEquals(
        ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED.code(),
        wrongKeyEnvelope.path("code").stringValue());
  }
}
