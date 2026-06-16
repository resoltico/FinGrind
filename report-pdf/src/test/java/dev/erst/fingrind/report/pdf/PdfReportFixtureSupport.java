package dev.erst.fingrind.report.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityRow;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionRow;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementRow;
import dev.erst.fingrind.contract.bookkeeping.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceRow;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.BookDoctrines;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.ContentSha256;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.core.SourceDocumentId;
import dev.erst.fingrind.core.SourceDocumentReference;
import dev.erst.fingrind.core.SourceDocumentType;
import dev.erst.fingrind.core.StatementLineKind;
import dev.erst.fingrind.core.StorageLocator;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;

/** Shared PDF report test fixtures and rendering assertions. */
final class PdfReportFixtureSupport {
  private PdfReportFixtureSupport() {}

  static final String DOCUMENT_SHA256 =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
  static final Clock CLOCK = Clock.fixed(Instant.parse("2026-04-19T10:15:30Z"), ZoneOffset.UTC);
  static final PdfReportService PDF_REPORT_SERVICE =
      new PdfReportService("FinGrind", "0.55.0", CLOCK);
  static final BookIdentity BOOK_IDENTITY =
      new BookIdentity(
          new EntityProfile(new BookEntityName("Acme Studio")),
          BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_CASH_SERVICE,
          CurrencyUnit.of("EUR"),
          FiscalYearStart.parse("01-01"));
  static final DeclaredAccount CASH_ACCOUNT =
      declaredAccount("1000", "Cash on Hand and Bank Balances", NormalBalance.DEBIT, true);
  static final DeclaredAccount REVENUE_ACCOUNT =
      declaredAccount(
          "2000", "Subscription Revenue from Enterprise Customers", NormalBalance.CREDIT, true);

  static void assertPdfMetadata(byte[] pdfBytes, String title, boolean portrait)
      throws IOException {
    try (PDDocument document = Loader.loadPDF(pdfBytes)) {
      PDDocumentInformation information = document.getDocumentInformation();
      PDRectangle mediaBox = document.getPage(0).getMediaBox();
      assertEquals(title, information.getTitle());
      assertEquals("FinGrind 0.55.0", information.getCreator());
      assertEquals(title, information.getSubject());
      assertEquals(portrait, mediaBox.getHeight() > mediaBox.getWidth());
    }
  }

  static void assertPdfPageCountAtLeast(byte[] pdfBytes, int minimumPages) throws IOException {
    try (PDDocument document = Loader.loadPDF(pdfBytes)) {
      assertTrue(document.getNumberOfPages() >= minimumPages);
    }
  }

  static String extractedText(byte[] pdfBytes) throws IOException {
    try (PDDocument document = Loader.loadPDF(pdfBytes)) {
      return new PDFTextStripper().getText(document);
    }
  }

  static List<AccountLedgerEntry> ledgerEntries(int count) {
    List<AccountLedgerEntry> entries = new ArrayList<>();
    for (int index = 0; index < count; index++) {
      String amount = Integer.toString((index + 1) * 50);
      entries.add(
          new AccountLedgerEntry(
              postingFact(index, amount),
              balance("EUR", amount, "0.00", amount, BalanceSide.DEBIT),
              money("EUR", Integer.toString((index + 1) * 50)),
              BalanceSide.DEBIT));
    }
    return entries;
  }

  static List<PeriodAccountActivityRow> accountActivityRows(int count) {
    List<PeriodAccountActivityRow> rows = new ArrayList<>();
    for (int index = 0; index < count; index++) {
      rows.add(
          new PeriodAccountActivityRow(
              declaredAccount(
                  "%04d".formatted(3000 + index),
                  "Operating Expense Category " + index + " with a deliberately long display name",
                  NormalBalance.DEBIT,
                  true),
              balance(
                  "EUR",
                  Integer.toString(index + 1),
                  "0.00",
                  Integer.toString(index + 1),
                  BalanceSide.DEBIT)));
    }
    return rows;
  }

  static PostingFact postingFact(int index, String amount) {
    LocalDate effectiveDate = LocalDate.parse("2026-04-01").plusDays(index % 28);
    PostingId postingId = new PostingId("posting-%03d".formatted(index));
    return new PostingFact(
        postingId,
        new JournalEntry(
            effectiveDate,
            List.of(
                new JournalLine(
                    CASH_ACCOUNT.accountCode(), JournalLine.EntrySide.DEBIT, money("EUR", amount)),
                new JournalLine(
                    REVENUE_ACCOUNT.accountCode(),
                    JournalLine.EntrySide.CREDIT,
                    money("EUR", amount)))),
        index % 5 == 0
            ? PostingLineage.reversal(
                new ReversalReference(new PostingId("prior-%03d".formatted(index))),
                new ReversalReason("Automated reversal %03d".formatted(index)))
            : PostingLineage.direct(),
        PostingKind.STANDARD,
        dev.erst.fingrind.core.PostingOriginKind.REVERSAL_ADJUSTMENT,
        evidence("idem-%03d".formatted(index)),
        new CommittedProvenance(
            new RequestProvenance(
                new ActorId("office-worker"),
                ActorType.PERSON,
                new CommandId("command-%03d".formatted(index)),
                new IdempotencyKey("idem-%03d".formatted(index)),
                new CausationId("cause-%03d".formatted(index)),
                Optional.of(new CorrelationId("corr-%03d".formatted(index)))),
            Instant.parse("2026-04-19T10:15:30Z").plusSeconds(index),
            SourceChannel.CLI));
  }

  static AccountingEvidence evidence(String token) {
    return new AccountingEvidence(
        List.of(
            new SourceDocumentReference(
                new SourceDocumentId("document-" + token),
                new SourceDocumentType("cash-receipt"),
                LocalDate.parse("2026-04-19"),
                Instant.parse("2026-04-19T10:15:30Z"),
                new StorageLocator("evidence://documents/document-" + token + ".pdf"),
                new ContentSha256(DOCUMENT_SHA256))),
        List.of());
  }

  static DeclaredAccount declaredAccount(
      String code, String name, NormalBalance normalBalance, boolean active) {
    AccountType accountType =
        normalBalance == NormalBalance.DEBIT ? AccountType.ASSET : AccountType.REVENUE;
    return new DeclaredAccount(
        new AccountCode(code),
        new AccountName(name),
        accountType,
        AccountRole.ORDINARY,
        accountTaxonomy(accountType),
        active,
        Instant.parse("2026-04-01T08:00:00Z"));
  }

  static FinancialPositionRow financialPositionRow(
      String lineCode,
      String lineName,
      AccountType accountType,
      AccountRole accountRole,
      FinancialPositionLineClassification lineClassification,
      CurrencyBalance balance) {
    return new FinancialPositionRow(
        lineCode,
        lineName,
        accountType,
        Optional.of(accountRole),
        Optional.of(lineClassification),
        StatementLineKind.DECLARED_ACCOUNT,
        balance);
  }

  static IncomeStatementRow incomeStatementRow(
      String lineCode,
      String lineName,
      AccountType accountType,
      AccountRole accountRole,
      ProfitAndLossLineClassification lineClassification,
      CurrencyBalance movement) {
    return new IncomeStatementRow(
        lineCode,
        lineName,
        accountType,
        Optional.of(accountRole),
        lineClassification,
        StatementLineKind.DECLARED_ACCOUNT,
        movement);
  }

  static ChangesInEquityRow changesInEquityRow(
      String lineCode,
      String lineName,
      AccountRole accountRole,
      FinancialPositionLineClassification lineClassification,
      CurrencyBalance openingBalance,
      CurrencyBalance movement,
      CurrencyBalance closingBalance) {
    return new ChangesInEquityRow(
        lineCode,
        lineName,
        Optional.of(AccountType.EQUITY),
        Optional.of(accountRole),
        Optional.of(lineClassification),
        StatementLineKind.DECLARED_ACCOUNT,
        openingBalance,
        movement,
        closingBalance);
  }

  static AccountTaxonomy accountTaxonomy(AccountType accountType) {
    return switch (accountType) {
      case ASSET ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
              Optional.empty());
      case LIABILITY ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.CURRENT_LIABILITY),
              Optional.empty());
      case EQUITY ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.EQUITY_CONTRIBUTION),
              Optional.empty());
      case REVENUE ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.empty(),
              Optional.of(ProfitAndLossLineClassification.OPERATING_REVENUE));
      case EXPENSE ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.empty(),
              Optional.of(ProfitAndLossLineClassification.OPERATING_EXPENSE));
    };
  }

  static TrialBalanceReport trialBalanceReport(
      BookIdentity bookIdentity,
      Optional<LocalDate> effectiveDateTo,
      EffectiveDateRange comparativeEffectiveDateRange,
      PostingCoverage postingCoverage,
      List<TrialBalanceRow> rows,
      List<TrialBalanceRow> comparativeRows) {
    List<CurrencyBalance> totals = trialBalanceTotals(rows);
    List<CurrencyBalance> comparativeTotals = trialBalanceTotals(comparativeRows);
    return new TrialBalanceReport(
        bookIdentity,
        effectiveDateTo,
        effectiveDateTo,
        comparativeEffectiveDateRange,
        postingCoverage,
        rows,
        totals,
        isBalanced(totals),
        comparativeRows,
        comparativeTotals,
        isBalanced(comparativeTotals));
  }

  static List<CurrencyBalance> trialBalanceTotals(List<TrialBalanceRow> rows) {
    List<CurrencyBalance> totalsByCurrency = new ArrayList<>();
    for (TrialBalanceRow row : rows) {
      mergeCurrencyBalance(totalsByCurrency, row.balance());
    }
    return List.copyOf(totalsByCurrency);
  }

  static CurrencyBalance sumCurrencyBalances(CurrencyBalance left, CurrencyBalance right) {
    return CurrencyBalance.ofTotals(
        left.debitTotal().plus(right.debitTotal()), left.creditTotal().plus(right.creditTotal()));
  }

  static boolean isBalanced(List<CurrencyBalance> totals) {
    return totals.stream().allMatch(balance -> balance.balanceSide() == BalanceSide.ZERO);
  }

  static void mergeCurrencyBalance(
      List<CurrencyBalance> totalsByCurrency, CurrencyBalance candidate) {
    CurrencyUnit currencyUnit = candidate.debitTotal().currencyUnit();
    for (int index = 0; index < totalsByCurrency.size(); index++) {
      CurrencyBalance existing = totalsByCurrency.get(index);
      if (existing.debitTotal().currencyUnit().equals(currencyUnit)) {
        totalsByCurrency.set(index, sumCurrencyBalances(existing, candidate));
        return;
      }
    }
    totalsByCurrency.add(candidate);
  }

  static CurrencyBalance balance(
      String currencyCode,
      String debitTotal,
      String creditTotal,
      String netAmount,
      BalanceSide balanceSide) {
    CurrencyBalance balance =
        CurrencyBalance.ofTotals(money(currencyCode, debitTotal), money(currencyCode, creditTotal));
    if (!balance.netAmount().equals(money(currencyCode, netAmount))
        || balance.balanceSide() != balanceSide) {
      throw new IllegalArgumentException("Test fixture balance does not match derived totals.");
    }
    return balance;
  }

  static Money money(String currencyCode, String amount) {
    return Money.parse(currencyCode, amount);
  }
}
