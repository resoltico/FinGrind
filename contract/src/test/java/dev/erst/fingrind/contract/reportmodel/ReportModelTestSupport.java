package dev.erst.fingrind.contract.reportmodel;

import dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityRow;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionRow;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementRow;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxCode;
import dev.erst.fingrind.contract.tax.TaxCodeDefinition;
import dev.erst.fingrind.contract.tax.TaxCodeName;
import dev.erst.fingrind.contract.tax.TaxInclusionMode;
import dev.erst.fingrind.contract.tax.TaxJurisdiction;
import dev.erst.fingrind.contract.tax.TaxObligationCodeSummary;
import dev.erst.fingrind.contract.tax.TaxObligationFrequency;
import dev.erst.fingrind.contract.tax.TaxRate;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.contract.tax.TaxRegistrationName;
import dev.erst.fingrind.contract.tax.TaxRegistrationNumber;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.BookDoctrines;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PositiveMoney;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.core.SourceDocumentId;
import dev.erst.fingrind.core.SourceDocumentReference;
import dev.erst.fingrind.core.SourceDocumentType;
import dev.erst.fingrind.core.StatementLineKind;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Shared fixtures for report-model package coverage tests. */
final class ReportModelTestSupport {
  private static final Instant FIXTURE_INSTANT = Instant.parse("2026-04-07T10:15:30Z");
  private static final LocalDate FIXTURE_DATE = LocalDate.parse("2026-04-07");

  private ReportModelTestSupport() {}

  static BookIdentity bookIdentity() {
    return new BookIdentity(
        new EntityProfile(new BookEntityName("Acme Studio")),
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE,
        CurrencyUnit.of("EUR"),
        FiscalYearStart.parse("01-01"),
        LocalDate.parse("2026-01-01"));
  }

  static BookIdentity tradingBookIdentity() {
    return new BookIdentity(
        new EntityProfile(new BookEntityName("Acme Studio")),
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_TRADING,
        CurrencyUnit.of("EUR"),
        FiscalYearStart.parse("01-01"),
        LocalDate.parse("2026-01-01"));
  }

  static DeclaredAccount declaredAccount(
      String accountCode, String accountName, AccountType accountType, boolean active) {
    return new DeclaredAccount(
        new AccountCode(accountCode),
        new AccountName(accountName),
        accountType,
        accountTaxonomy(accountType),
        active,
        FIXTURE_INSTANT);
  }

  static CurrencyBalance balance(String currencyCode, String debitAmount, String creditAmount) {
    return CurrencyBalance.ofTotals(
        Money.parse(currencyCode, debitAmount), Money.parse(currencyCode, creditAmount));
  }

  static JournalLine journalLine(
      String accountCode, JournalLine.EntrySide side, String amountDecimal) {
    return new JournalLine(
        new AccountCode(accountCode), side, PositiveMoney.of(Money.parse("EUR", amountDecimal)));
  }

  static PostingFact postingFact(
      String postingId,
      PostingOriginKind postingOriginKind,
      PostingLineage postingLineage,
      JournalLine... lines) {
    String token = postingId.replace('-', '_');
    return new PostingFact(
        new PostingId(postingId),
        new JournalEntry(FIXTURE_DATE, List.of(lines)),
        postingLineage,
        PostingKind.STANDARD,
        postingOriginKind,
        accountingEvidence(token),
        committedProvenance(token));
  }

  static AccountLedgerEntry accountLedgerEntry(
      PostingFact postingFact,
      CurrencyBalance movement,
      String runningAmountDecimal,
      BalanceSide runningBalanceSide) {
    return new AccountLedgerEntry(
        postingFact,
        movement,
        Money.parse(movement.netAmount().currencyUnit().code(), runningAmountDecimal),
        runningBalanceSide);
  }

  static FinancialPositionRow financialPositionRow(
      String lineCode,
      String lineName,
      AccountType lineType,
      Optional<FinancialPositionLineClassification> lineClassification,
      StatementLineKind lineKind,
      CurrencyBalance balance) {
    return new FinancialPositionRow(
        lineCode, lineName, lineType, lineClassification, lineKind, balance);
  }

  static IncomeStatementRow incomeStatementRow(
      String lineCode,
      String lineName,
      AccountType lineType,
      ProfitAndLossLineClassification lineClassification,
      StatementLineKind lineKind,
      CurrencyBalance movement) {
    return new IncomeStatementRow(
        lineCode, lineName, lineType, lineClassification, lineKind, movement);
  }

  static ChangesInEquityRow changesInEquityRow(
      String lineCode,
      String lineName,
      Optional<AccountType> lineType,
      Optional<FinancialPositionLineClassification> lineClassification,
      StatementLineKind lineKind,
      CurrencyBalance openingBalance,
      CurrencyBalance movement,
      CurrencyBalance closingBalance) {
    return new ChangesInEquityRow(
        lineCode,
        lineName,
        lineType,
        lineClassification,
        lineKind,
        openingBalance,
        movement,
        closingBalance);
  }

  static DeclaredTaxRegistration taxRegistration() {
    return new DeclaredTaxRegistration(
        new TaxRegistrationId("vat-lv"),
        new TaxRegistrationName("Latvia VAT"),
        new TaxJurisdiction("LV"),
        new TaxRegistrationNumber("LV40001234567"),
        new AccountCode("2100"),
        new AccountCode("1300"),
        TaxObligationFrequency.MONTHLY,
        20,
        List.of(
            new TaxCodeDefinition(
                new TaxCode("vat-standard-sale"),
                new TaxCodeName("VAT Standard Sale"),
                new TaxRate(210_000),
                TaxInclusionMode.EXCLUSIVE,
                TaxApplicationKind.OUTPUT_SALE),
            new TaxCodeDefinition(
                new TaxCode("vat-standard-expense"),
                new TaxCodeName("VAT Standard Expense"),
                new TaxRate(210_000),
                TaxInclusionMode.INCLUSIVE,
                TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE)),
        FIXTURE_INSTANT);
  }

  static TaxObligationCodeSummary taxCodeSummary(
      String taxCode,
      String taxCodeName,
      TaxApplicationKind applicationKind,
      int postingCount,
      String taxableMinorUnits,
      String taxMinorUnits,
      String grossMinorUnits) {
    return new TaxObligationCodeSummary(
        new TaxCode(taxCode),
        new TaxCodeName(taxCodeName),
        applicationKind,
        postingCount,
        new MonetaryAmount("EUR", taxableMinorUnits),
        new MonetaryAmount("EUR", taxMinorUnits),
        new MonetaryAmount("EUR", grossMinorUnits));
  }

  private static AccountTaxonomy accountTaxonomy(AccountType accountType) {
    return switch (accountType) {
      case ASSET ->
          new AccountTaxonomy(
              AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
              Optional.empty(),
              Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT));
      case LIABILITY ->
          new AccountTaxonomy(
              AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.CURRENT_LIABILITY),
              Optional.empty(),
              Optional.empty());
      case EQUITY ->
          new AccountTaxonomy(
              AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.OTHER_EQUITY),
              Optional.empty(),
              Optional.empty());
      case REVENUE ->
          new AccountTaxonomy(
              AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.empty(),
              Optional.empty(),
              Optional.of(ProfitAndLossLineClassification.OPERATING_REVENUE),
              Optional.empty());
      case EXPENSE ->
          new AccountTaxonomy(
              AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.empty(),
              Optional.empty(),
              Optional.of(ProfitAndLossLineClassification.OPERATING_EXPENSE),
              Optional.empty());
    };
  }

  private static AccountingEvidence accountingEvidence(String token) {
    return new AccountingEvidence(List.of(sourceDocumentReference(token)), List.of());
  }

  private static CommittedProvenance committedProvenance(String token) {
    return new CommittedProvenance(requestProvenance(token), FIXTURE_INSTANT, SourceChannel.CLI);
  }

  private static RequestProvenance requestProvenance(String token) {
    return new RequestProvenance(
        new ActorId("actor-" + token),
        ActorType.AGENT,
        new CommandId("command-" + token),
        new IdempotencyKey("idem-" + token),
        new CausationId("cause-" + token),
        Optional.of(new CorrelationId("corr-" + token)));
  }

  private static SourceDocumentReference sourceDocumentReference(String token) {
    return new SourceDocumentReference(
        new SourceDocumentId("document-" + token),
        new SourceDocumentType("cash-receipt"),
        FIXTURE_DATE);
  }
}
