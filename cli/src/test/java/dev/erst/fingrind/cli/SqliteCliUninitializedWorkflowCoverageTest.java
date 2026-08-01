package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.contract.bookkeeping.AmendAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.AmendAccountResult;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.FiscalYearCloseCommand;
import dev.erst.fingrind.contract.bookkeeping.FiscalYearCloseResult;
import dev.erst.fingrind.contract.bookkeeping.InterimResultSweepCommand;
import dev.erst.fingrind.contract.bookkeeping.InterimResultSweepResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.RetireAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.RetireAccountResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationCommand;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationResult;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxCode;
import dev.erst.fingrind.contract.tax.TaxCodeDefinition;
import dev.erst.fingrind.contract.tax.TaxCodeName;
import dev.erst.fingrind.contract.tax.TaxInclusionMode;
import dev.erst.fingrind.contract.tax.TaxJurisdiction;
import dev.erst.fingrind.contract.tax.TaxObligationFrequency;
import dev.erst.fingrind.contract.tax.TaxRate;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.contract.tax.TaxRegistrationName;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.sqlite.SqliteAdministrationSession;
import dev.erst.fingrind.sqlite.SqliteAdministrationSessions;
import dev.erst.fingrind.sqlite.SqlitePassphraseIntent;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Field coverage for the real protected SQLite session before a book has been initialized. */
class SqliteCliUninitializedWorkflowCoverageTest extends CliBookWorkflowFixtureSupport {
  @Test
  void administrativeAndCloseMutations_rejectAnExistingButUninitializedProtectedBook()
      throws IOException {
    BookAccess bookAccess = uninitializedBookAccess();
    CliBookPassphraseResolver passphraseResolver =
        new CliBookPassphraseResolver(
            new ByteArrayInputStream(new byte[0]),
            prompt -> {
              throw new AssertionError("A key file must not prompt.");
            });
    try (SqliteAdministrationSession ignored =
        SqliteAdministrationSessions.openNewBookResolved(
                bookAccess, passphraseResolver, SqlitePassphraseIntent.NEW_SECRET)
            .requireAccepted()) {
      // Establish a protected SQLite database without FinGrind initialization metadata.
    }

    SqliteCliAdministrationMutations administration =
        new SqliteCliAdministrationMutations(fixedClock(), passphraseResolver);
    assertInstanceOf(
        DeclareAccountResult.Rejected.class,
        administration.declareAccount(bookAccess, accountCommand()).requireAccepted());
    assertInstanceOf(
        AmendAccountResult.Rejected.class,
        administration.amendAccount(bookAccess, amendAccountCommand()).requireAccepted());
    assertInstanceOf(
        RetireAccountResult.Rejected.class,
        administration
            .retireAccount(bookAccess, new RetireAccountCommand(new AccountCode("1000")))
            .requireAccepted());
    assertInstanceOf(
        DeclareTaxRegistrationResult.Rejected.class,
        administration
            .declareTaxRegistration(bookAccess, taxRegistrationCommand())
            .requireAccepted());

    SqliteCliReportingPeriodCloseMutations closeMutations =
        new SqliteCliReportingPeriodCloseMutations(fixedClock(), passphraseResolver);
    assertInstanceOf(
        InterimResultSweepResult.Rejected.class,
        closeMutations
            .interimResultSweep(
                bookAccess, new InterimResultSweepCommand(LocalDate.parse("2026-04-07")))
            .requireAccepted());
    assertInstanceOf(
        FiscalYearCloseResult.Rejected.class,
        closeMutations
            .fiscalYearClose(bookAccess, new FiscalYearCloseCommand(2026))
            .requireAccepted());
    assertInstanceOf(
        PostEntryResult.CommitRejected.class,
        new SqliteCliMutationWorkflow(fixedClock(), passphraseResolver)
            .commit(
                bookAccess,
                new CliRequestReader(new ByteArrayInputStream(new byte[0]))
                    .readPostEntryCommand(
                        writeRequest(CliRequestReaderTestSupport.validRequestJson(false))))
            .requireAccepted());
  }

  private BookAccess uninitializedBookAccess() {
    Path bookFile = tempDirectory.resolve("uninitialized.sqlite");
    return new BookAccess(
        bookFile, new BookAccess.PassphraseSource.KeyFile(writeBookKey(bookFile)), List.of());
  }

  private static DeclareAccountCommand accountCommand() {
    return new DeclareAccountCommand(
        new AccountCode("1000"), new AccountName("Cash"), AccountType.ASSET, assetTaxonomy());
  }

  private static AmendAccountCommand amendAccountCommand() {
    return new AmendAccountCommand(
        new AccountCode("1000"),
        new AccountName("Cash amended"),
        AccountType.ASSET,
        assetTaxonomy());
  }

  private static AccountTaxonomy assetTaxonomy() {
    return new AccountTaxonomy(
        AccountNodeKind.POSTABLE,
        Optional.empty(),
        Optional.empty(),
        Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
        Optional.empty(),
        Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT));
  }

  private static DeclareTaxRegistrationCommand taxRegistrationCommand() {
    return new DeclareTaxRegistrationCommand(
        new TaxRegistrationId("vat-lv"),
        new TaxRegistrationName("Latvia VAT"),
        new TaxJurisdiction("LV"),
        null,
        new AccountCode("2100"),
        new AccountCode("1300"),
        TaxObligationFrequency.MONTHLY,
        20,
        List.of(
            new TaxCodeDefinition(
                new TaxCode("vat-standard"),
                new TaxCodeName("Standard VAT"),
                new TaxRate(210_000),
                TaxInclusionMode.EXCLUSIVE,
                TaxApplicationKind.OUTPUT_SALE)));
  }
}
