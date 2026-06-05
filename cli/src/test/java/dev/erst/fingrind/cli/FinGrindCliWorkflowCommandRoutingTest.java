package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccountPageCursor;
import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyRollbackResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link FinGrindCli}. */
class FinGrindCliWorkflowCommandRoutingTest extends FinGrindCliTestSupport {
  @Test
  void run_routesCommandsThroughSelectedBookWorkflow() throws IOException {
    Path requestFile = writeRequest(validRequestJson());
    Path planFile = writeNamedRequest("plan.json", validPlanJson());
    Path declareAccountFile =
        writeNamedRequest("declare.json", declareAccountJson("1000", "Cash", "DEBIT"));
    Path bookFilePath = tempDirectory.resolve("books").resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    AccountPageCursor accountCursor = new AccountPageCursor(new AccountCode("1000"));
    RecordingWorkflow workflow =
        new RecordingWorkflow(
            openedBookResult(Instant.parse("2026-04-07T12:00:00Z")),
            new RekeyBookResult.Rekeyed(Path.of("unused.sqlite")),
            new DeclareAccountResult.Declared(
                declaredAccount(
                    "1000",
                    "Cash",
                    dev.erst.fingrind.core.AccountType.ASSET,
                    NormalBalance.DEBIT,
                    true,
                    Instant.parse("2026-04-07T12:00:00Z"))),
            new ListAccountsResult.Listed(
                accountPage(
                    List.of(
                        declaredAccount(
                            "1000",
                            "Cash",
                            dev.erst.fingrind.core.AccountType.ASSET,
                            NormalBalance.DEBIT,
                            true,
                            Instant.parse("2026-04-07T12:00:00Z"))),
                    25,
                    Optional.empty())),
            new PostEntryResult.PreflightAccepted(
                new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07")),
            new PostEntryResult.Committed(
                new PostingId("posting-1"),
                new IdempotencyKey("idem-1"),
                LocalDate.parse("2026-04-07"),
                Instant.parse("2026-04-07T10:15:30Z")));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            fixedClock(),
            workflow);
    assertEquals(0, cli.run(openBookKeyFileArguments(bookFilePath, bookKeyFilePath)));
    assertEquals(
        0,
        cli.run(
            new String[] {
              "declare-account",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--request-file",
              declareAccountFile.toString()
            }));
    assertEquals(
        0,
        cli.run(
            new String[] {
              "list-accounts",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--limit",
              "25",
              "--cursor",
              accountCursor.wireValue()
            }));
    assertEquals(
        0,
        cli.run(
            new String[] {
              "preflight-entry",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--request-file",
              requestFile.toString()
            }));
    assertEquals(
        0,
        cli.run(
            new String[] {
              "post-entry",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--request-file",
              requestFile.toString()
            }));
    assertEquals(
        0,
        cli.run(
            new String[] {
              "execute-plan",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--request-file",
              planFile.toString()
            }));
    assertEquals(List.of(bookAccess(bookFilePath, bookKeyFilePath)), workflow.openBookAccesses());
    assertEquals(
        List.of(bookAccess(bookFilePath, bookKeyFilePath)), workflow.declareAccountAccesses());
    assertEquals(
        List.of(bookAccess(bookFilePath, bookKeyFilePath)), workflow.listAccountAccesses());
    assertEquals(
        List.of(new ListAccountsQuery(25, Optional.of(accountCursor))),
        workflow.listAccountQueries());
    assertEquals(List.of(bookAccess(bookFilePath, bookKeyFilePath)), workflow.preflightAccesses());
    assertEquals(List.of(bookAccess(bookFilePath, bookKeyFilePath)), workflow.commitAccesses());
    assertEquals(
        List.of(bookAccess(bookFilePath, bookKeyFilePath)), workflow.executePlanAccesses());
  }

  @Test
  void run_routesRekeyBookThroughSelectedBookWorkflow() {
    Path bookFilePath = tempDirectory.resolve("books").resolve("rekey.sqlite");
    Path currentBookKeyFilePath = writeBookKey(bookFilePath);
    Path replacementBookKeyFilePath = writeNamedBookKey("replacement.key", "replacement-key");
    RecordingWorkflow workflow =
        new RecordingWorkflow(
            openedBookResult(Instant.parse("2026-04-07T12:00:00Z")),
            new RekeyBookResult.Rekeyed(bookFilePath),
            new DeclareAccountResult.Declared(
                declaredAccount(
                    "1000",
                    "Cash",
                    dev.erst.fingrind.core.AccountType.ASSET,
                    NormalBalance.DEBIT,
                    true,
                    Instant.parse("2026-04-07T12:00:00Z"))),
            new ListAccountsResult.Listed(accountPage(List.of(), 50, Optional.empty())),
            new PostEntryResult.PreflightAccepted(
                new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07")),
            new PostEntryResult.Committed(
                new PostingId("posting-1"),
                new IdempotencyKey("idem-1"),
                LocalDate.parse("2026-04-07"),
                Instant.parse("2026-04-07T10:15:30Z")));
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            fixedClock(),
            workflow);
    assertEquals(
        0,
        cli.run(
            jsonArguments(
                "rekey-book",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                currentBookKeyFilePath.toString(),
                "--replacement-book-key-file",
                replacementBookKeyFilePath.toString())));
    assertEquals(
        List.of(bookAccess(bookFilePath, currentBookKeyFilePath)), workflow.rekeyBookAccesses());
    assertEquals(
        List.of(new BookAccess.PassphraseSource.KeyFile(replacementBookKeyFilePath)),
        workflow.rekeyReplacementPassphraseSources());
    String output = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(output.contains("\"replacementPassphraseSource\""));
    assertTrue(output.contains("\"replacementBookKeyFile\""));
  }

  @Test
  void run_routesMaintenanceCommandsThroughSelectedBookWorkflow() {
    Path bookFilePath = tempDirectory.resolve("books").resolve("maintenance.sqlite");
    Path currentBookKeyFilePath = writeBookKey(bookFilePath);
    Path backupFilePath = tempDirectory.resolve("backup").resolve("maintenance.sqlite");
    Path backupBookKeyFilePath = tempDirectory.resolve("backup").resolve("maintenance.key");
    Path rollbackArtifactPath =
        tempDirectory.resolve("books").resolve("maintenance.rekey-rollback.sqlite");
    RecordingWorkflow workflow =
        new RecordingWorkflow(
            openedBookResult(Instant.parse("2026-04-07T12:00:00Z")),
            new RekeyBookResult.Rekeyed(bookFilePath),
            new DeclareAccountResult.Declared(
                declaredAccount(
                    "1000",
                    "Cash",
                    dev.erst.fingrind.core.AccountType.ASSET,
                    NormalBalance.DEBIT,
                    true,
                    Instant.parse("2026-04-07T12:00:00Z"))),
            new ListAccountsResult.Listed(accountPage(List.of(), 50, Optional.empty())),
            new PostEntryResult.PreflightAccepted(
                new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07")),
            new PostEntryResult.Committed(
                new PostingId("posting-1"),
                new IdempotencyKey("idem-1"),
                LocalDate.parse("2026-04-07"),
                Instant.parse("2026-04-07T10:15:30Z")));
    workflow.setBackupBookResult(
        new BackupBookResult.BackedUp(
            hint(bookFilePath), hint(backupFilePath), hint(backupBookKeyFilePath)));
    workflow.setRestoreBookResult(
        new RestoreBookResult.Restored(
            hint(bookFilePath), hint(backupFilePath), hint(backupBookKeyFilePath)));
    workflow.setRekeyRollbackResult(
        new RekeyRollbackResult.Restored(hint(bookFilePath), hint(rollbackArtifactPath)));

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            fixedClock(),
            workflow);

    assertEquals(
        0,
        cli.run(
            jsonArguments(
                "backup-book",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                currentBookKeyFilePath.toString(),
                "--backup-file-out",
                backupFilePath.toString(),
                "--backup-book-key-file-out",
                backupBookKeyFilePath.toString())));
    assertEquals(
        0,
        cli.run(
            jsonArguments(
                "restore-book",
                "--book-file",
                bookFilePath.toString(),
                "--backup-file",
                backupFilePath.toString(),
                "--backup-book-key-file",
                backupBookKeyFilePath.toString())));
    assertEquals(
        0,
        cli.run(
            jsonArguments(
                "restore-rekey-rollback",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                currentBookKeyFilePath.toString(),
                "--rollback-file",
                rollbackArtifactPath.toString())));

    assertEquals(
        List.of(bookAccess(bookFilePath, currentBookKeyFilePath)), workflow.backupBookAccesses());
    assertEquals(List.of(backupFilePath), workflow.backupFilePaths());
    assertEquals(List.of(backupBookKeyFilePath), workflow.backupBookKeyFilePaths());
    assertEquals(List.of(bookFilePath), workflow.restoreBookFilePaths());
    assertEquals(List.of(backupFilePath), workflow.restoreBackupFilePaths());
    assertEquals(List.of(backupBookKeyFilePath), workflow.restoreBackupBookKeyFilePaths());
    assertEquals(List.of(bookFilePath), workflow.restoreRekeyRollbackBookFilePaths());
    assertEquals(List.of(rollbackArtifactPath), workflow.restoreRekeyRollbackArtifactPaths());
    assertEquals(
        List.of(new BookAccess.PassphraseSource.KeyFile(currentBookKeyFilePath)),
        workflow.restoreRekeyRollbackExpectedPassphraseSources());
    String output = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(output.contains("\"backupFile\""));
    assertTrue(output.contains("\"backupBookKeyFile\""));
    assertTrue(output.contains("\"rollbackArtifact\""));
  }

  @Test
  void run_routesInspectAndDeleteRollbackCommandsThroughSelectedBookWorkflow() {
    Path bookFilePath = tempDirectory.resolve("books").resolve("rollback.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    Path rollbackArtifactPath =
        tempDirectory.resolve("books").resolve("rollback.rekey-rollback.sqlite");
    RecordingWorkflow workflow =
        new RecordingWorkflow(
            openedBookResult(Instant.parse("2026-04-07T12:00:00Z")),
            new RekeyBookResult.Rekeyed(bookFilePath),
            new DeclareAccountResult.Declared(
                declaredAccount(
                    "1000",
                    "Cash",
                    dev.erst.fingrind.core.AccountType.ASSET,
                    NormalBalance.DEBIT,
                    true,
                    Instant.parse("2026-04-07T12:00:00Z"))),
            new ListAccountsResult.Listed(accountPage(List.of(), 50, Optional.empty())),
            new PostEntryResult.PreflightAccepted(
                new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07")),
            new PostEntryResult.Committed(
                new PostingId("posting-1"),
                new IdempotencyKey("idem-1"),
                LocalDate.parse("2026-04-07"),
                Instant.parse("2026-04-07T10:15:30Z")));
    workflow.setRekeyRollbackResult(
        new RekeyRollbackResult.Inspected(hint(bookFilePath), List.of(hint(rollbackArtifactPath))));

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            fixedClock(),
            workflow);

    assertEquals(
        0,
        cli.run(new String[] {"inspect-rekey-rollback", "--book-file", bookFilePath.toString()}));
    workflow.setRekeyRollbackResult(
        new RekeyRollbackResult.Deleted(hint(bookFilePath), hint(rollbackArtifactPath)));
    assertEquals(
        0,
        cli.run(
            new String[] {
              "delete-rekey-rollback",
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--rollback-file",
              rollbackArtifactPath.toString()
            }));

    assertEquals(List.of(bookFilePath), workflow.inspectRekeyRollbackBookFilePaths());
    assertEquals(List.of(bookFilePath), workflow.deleteRekeyRollbackBookFilePaths());
    assertEquals(List.of(rollbackArtifactPath), workflow.deleteRekeyRollbackArtifactPaths());
  }
}
