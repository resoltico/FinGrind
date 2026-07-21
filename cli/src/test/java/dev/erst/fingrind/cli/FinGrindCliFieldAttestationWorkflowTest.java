package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.core.attestation.AttestationCredentialSource;
import dev.erst.fingrind.core.attestation.AttestationSigningSession;
import dev.erst.fingrind.sqlite.SqliteBookSessionMode;
import dev.erst.fingrind.sqlite.SqlitePassphraseIntent;
import dev.erst.fingrind.sqlite.SqlitePostingSessions;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Field-shaped attestation coverage through the live CLI and SQLite posting boundary. */
class FinGrindCliFieldAttestationWorkflowTest extends FinGrindCliTestSupport {
  @Test
  void commit_acceptsFieldStyleEvidenceAndUuidFiveCommandIdWithAnExistingFounderCredential()
      throws IOException {
    String founderPrincipalId = "4bc17dd7-145f-4ea7-bb55-167ca2f6ac11";
    Path requestFile =
        writeNamedRequest(
            "--sale [bundle-acceptance].json",
            """
            {
              "entryKind": "SALE_SETTLED",
              "effectiveDate": "2026-04-07",
              "cashAccountCode": "1000",
              "revenueAccountCode": "2000",
              "amount": {"currencyCode": "EUR", "minorUnits": "1000"},
              "evidence": {
                "sourceDocuments": [{
                  "sourceDocumentId": "bundle-acceptance-sale-document-1",
                  "sourceDocumentType": "cash-receipt",
                  "documentDate": "2026-04-07"
                }],
                "approvals": []
              },
              "provenance": {
                "commandId": "23161157-7aff-5d55-b340-33a484925b90",
                "idempotencyKey": "bundle-acceptance-idem-1",
                "causationId": "bundle-acceptance-cause-1"
              }
            }
            """);
    Path bookFilePath =
        tempDirectory
            .resolve("field-style-books")
            .resolve("Rīga büro")
            .resolve("-entity [bundle-acceptance].sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8)),
            utf8PrintStream(new ByteArrayOutputStream()),
            fixedClock());
    String[] openArguments = openBookStandardInputArguments(bookFilePath);
    for (int index = 0; index < openArguments.length; index++) {
      if ("10213243-5465-7687-98a9-babcbddceeff".equals(openArguments[index])) {
        openArguments[index] = founderPrincipalId;
      }
    }
    assertEquals(0, cli.run(jsonArguments(openArguments)));
    Path founderKeyPath =
        bookFilePath.resolveSibling(bookFilePath.getFileName() + ".founder.fgatk");
    Path founderPassphrasePath =
        bookFilePath.resolveSibling(bookFilePath.getFileName() + ".founder-passphrase");
    assertEquals(
        0,
        cli.run(
            withFounderCredentials(
                new String[] {
                  "declare-account",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString(),
                  "--request-file",
                  writeNamedRequest(
                          "field-style-cash.json", declareAccountJson("1000", "Cash", "DEBIT"))
                      .toString()
                },
                founderPrincipalId,
                founderKeyPath,
                founderPassphrasePath)));
    assertEquals(
        0,
        cli.run(
            withFounderCredentials(
                new String[] {
                  "declare-account",
                  "--book-file",
                  bookFilePath.toString(),
                  "--book-key-file",
                  bookKeyFilePath.toString(),
                  "--request-file",
                  writeNamedRequest(
                          "field-style-revenue.json",
                          declareAccountJson("2000", "Revenue", "CREDIT"))
                      .toString()
                },
                founderPrincipalId,
                founderKeyPath,
                founderPassphrasePath)));

    BookAccess bookAccess =
        new BookAccess(
            bookFilePath,
            new BookAccess.PassphraseSource.KeyFile(bookKeyFilePath),
            List.of(
                new AttestationCredentialSource(
                    UUID.fromString(founderPrincipalId), founderKeyPath, founderPassphrasePath)));
    var command =
        new CliRequestReader(new ByteArrayInputStream(new byte[0]))
            .readPostEntryCommand(
                requestFile, dev.erst.fingrind.contract.protocol.OperationId.RECORD_SALE_SETTLED);
    ContractDecision<dev.erst.fingrind.contract.bookkeeping.CommitEntryResult> decision;
    try (AttestationSigningSession signingSession =
        AttestationSigningSession.open(bookAccess.requireAttestationCredentialSources())) {
      decision =
          SqliteCliWorkflowSessions.withPostingSession(
              SqlitePostingSessions.openResolved(
                  bookAccess,
                  SqliteBookSessionMode.READ_WRITE_EXISTING,
                  new CliBookPassphraseResolver(
                      new ByteArrayInputStream(new byte[0]),
                      CliBookPassphraseResolver.systemTerminal()),
                  SqlitePassphraseIntent.EXISTING_SECRET),
              bookSession ->
                  SqliteCliWorkflowSessions.postingApplicationService(
                          bookSession, Clock.systemUTC())
                      .commit(command, signingSession));
    }
    assertInstanceOf(
        dev.erst.fingrind.contract.bookkeeping.PostEntryResult.Committed.class,
        decision.requireAccepted());
  }

  private static String[] withFounderCredentials(
      String[] arguments, String principalId, Path keyPath, Path passphrasePath) {
    String[] complete = Arrays.copyOf(arguments, arguments.length + 6);
    int credentialStart = arguments.length;
    complete[credentialStart] = "--attestation-principal-id";
    complete[credentialStart + 1] = principalId;
    complete[credentialStart + 2] = "--attestation-key-file";
    complete[credentialStart + 3] = keyPath.toString();
    complete[credentialStart + 4] = "--attestation-passphrase-file";
    complete[credentialStart + 5] = passphrasePath.toString();
    return complete;
  }
}
