package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementResult;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationQuery;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.core.ComparativeSelection;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Focused coverage for the SQLite-backed read workflow seam. */
class CliReadWorkflowCoverageTest extends CliBookWorkflowFixtureSupport {
  @Test
  void sqliteCliReadWorkflow_routesCashFlowStatementThroughReadSessionOpening() throws Exception {
    Path bookFile = tempDirectory.resolve("book.sqlite");
    Path bookKeyFile = writeBookKey(bookFile);
    CliBookPassphraseResolver resolver =
        new CliBookPassphraseResolver(
            InputStream.nullInputStream(),
            prompt -> {
              throw new AssertionError("interactive prompt should not be used");
            });
    SqliteCliLifecycleWorkflow lifecycleWorkflow =
        new SqliteCliLifecycleWorkflow(fixedClock(), resolver);
    SqliteCliReadWorkflow workflow = new SqliteCliReadWorkflow(resolver);
    BookAccess bookAccess =
        new BookAccess(bookFile, new BookAccess.PassphraseSource.KeyFile(bookKeyFile));
    OpenBookResult openBookResult =
        lifecycleWorkflow.openBook(bookAccess, openBookCommand()).requireAccepted();

    assertInstanceOf(OpenBookResult.Opened.class, openBookResult);

    var decision =
        workflow.cashFlowStatement(
            bookAccess,
            new CashFlowStatementQuery(
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-04-30"),
                ComparativeSelection.none()));

    assertNotNull(decision.requireAccepted());
    assertInstanceOf(CashFlowStatementResult.class, decision.requireAccepted());
    var inventoryDecision =
        workflow.inventoryValuation(
            bookAccess, new InventoryValuationQuery(java.util.Optional.empty(), false));
    assertNotNull(inventoryDecision.requireAccepted());
    assertInstanceOf(InventoryValuationResult.class, inventoryDecision.requireAccepted());
  }
}
