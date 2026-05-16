package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.bookkeeping.AccountPage;
import dev.erst.fingrind.contract.bookkeeping.AccountPageCursor;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityRow;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionRow;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementRow;
import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.contract.bookkeeping.PostingPage;
import dev.erst.fingrind.contract.bookkeeping.PostingPageCursor;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeStatus;
import dev.erst.fingrind.contract.runtime.EnvironmentDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentDistributionDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentSqliteDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentStorageDescriptor;
import dev.erst.fingrind.contract.runtime.SqliteCompileOptionsVerificationStatus;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.AccountingBasis;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.EntityForm;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.OwnerModel;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.ReportingObligationStatus;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.core.StatementLineKind;
import dev.erst.fingrind.core.TaxRegistrationStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Shared contract test fixtures. */
final class ContractFixtures {
  private ContractFixtures() {}

  static BookIdentity bookIdentity() {
    return new BookIdentity(
        new EntityProfile(
            new BookEntityName("Acme Studio"),
            EntityForm.COMPANY,
            OwnerModel.MULTI_OWNER,
            ReportingObligationStatus.INTERNAL_MANAGEMENT_ONLY,
            TaxRegistrationStatus.UNSPECIFIED,
            List.of()),
        CurrencyUnit.of("EUR"),
        FiscalYearStart.parse("01-01"),
        AccountingBasis.ACCRUAL);
  }

  static OpenBookCommand openBookCommand() {
    return new OpenBookCommand(bookIdentity());
  }

  static PostingCoverage postingCoverage() {
    return PostingCoverage.ALL_POSTING_KINDS;
  }

  static AccountPage accountPage(
      List<dev.erst.fingrind.contract.bookkeeping.DeclaredAccount> accounts,
      int limit,
      Optional<AccountPageCursor> nextCursor) {
    return new AccountPage(bookIdentity(), accounts, limit, nextCursor);
  }

  static PostingPage postingPage(
      Optional<AccountCode> accountCodeFilter,
      EffectiveDateRange effectiveDateRange,
      List<PostingFact> postings,
      int limit,
      Optional<PostingPageCursor> nextCursor) {
    return new PostingPage(
        bookIdentity(), accountCodeFilter, effectiveDateRange, postings, limit, nextCursor);
  }

  static AccountTaxonomy accountTaxonomy(AccountType accountType) {
    return switch (accountType) {
      case ASSET ->
          new AccountTaxonomy(
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
              Optional.empty());
      case LIABILITY ->
          new AccountTaxonomy(
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.CURRENT_LIABILITY),
              Optional.empty());
      case EQUITY ->
          new AccountTaxonomy(
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.OTHER_EQUITY),
              Optional.empty());
      case REVENUE ->
          new AccountTaxonomy(
              Optional.empty(),
              Optional.empty(),
              Optional.of(ProfitAndLossLineClassification.OPERATING_REVENUE));
      case EXPENSE ->
          new AccountTaxonomy(
              Optional.empty(),
              Optional.empty(),
              Optional.of(ProfitAndLossLineClassification.OPERATING_EXPENSE));
    };
  }

  static DeclaredAccount declaredAccount(
      String accountCode,
      String accountName,
      AccountType accountType,
      AccountRole accountRole,
      boolean active,
      Instant declaredAt) {
    return new DeclaredAccount(
        new AccountCode(accountCode),
        new AccountName(accountName),
        accountType,
        accountRole,
        accountTaxonomy(accountType),
        active,
        declaredAt);
  }

  static DeclareAccountCommand declareAccountCommand(
      String accountCode, String accountName, AccountType accountType, AccountRole accountRole) {
    return new DeclareAccountCommand(
        new AccountCode(accountCode),
        new AccountName(accountName),
        accountType,
        accountRole,
        accountTaxonomy(accountType));
  }

  static FinancialPositionRow financialPositionRow(
      String lineCode,
      String lineName,
      AccountType accountType,
      Optional<AccountRole> lineRole,
      FinancialPositionLineClassification lineClassification,
      StatementLineKind lineKind,
      dev.erst.fingrind.core.CurrencyBalance balance) {
    return new FinancialPositionRow(
        lineCode, lineName, accountType, lineRole, lineClassification, lineKind, balance);
  }

  static IncomeStatementRow incomeStatementRow(
      String lineCode,
      String lineName,
      AccountType accountType,
      Optional<AccountRole> lineRole,
      ProfitAndLossLineClassification lineClassification,
      StatementLineKind lineKind,
      dev.erst.fingrind.core.CurrencyBalance movement) {
    return new IncomeStatementRow(
        lineCode, lineName, accountType, lineRole, lineClassification, lineKind, movement);
  }

  static ChangesInEquityRow changesInEquityRow(
      String lineCode,
      String lineName,
      Optional<AccountType> lineType,
      Optional<AccountRole> lineRole,
      FinancialPositionLineClassification lineClassification,
      StatementLineKind lineKind,
      dev.erst.fingrind.core.CurrencyBalance openingBalance,
      dev.erst.fingrind.core.CurrencyBalance movement,
      dev.erst.fingrind.core.CurrencyBalance closingBalance) {
    return new ChangesInEquityRow(
        lineCode,
        lineName,
        lineType,
        lineRole,
        lineClassification,
        lineKind,
        openingBalance,
        movement,
        closingBalance);
  }

  static PostEntryCommand postEntryCommand(String idempotencyKey) {
    return new PostEntryCommand(
        PostingKind.STANDARD,
        new JournalEntry(
            LocalDate.parse("2026-04-07"),
            List.of(
                new JournalLine(
                    new AccountCode("1000"),
                    JournalLine.EntrySide.DEBIT,
                    Money.parse("EUR", "10.00")),
                new JournalLine(
                    new AccountCode("2000"),
                    JournalLine.EntrySide.CREDIT,
                    Money.parse("EUR", "10.00")))),
        PostingLineage.direct(),
        new RequestProvenance(
            new ActorId("actor-1"),
            ActorType.AGENT,
            new CommandId("command-1"),
            new IdempotencyKey(idempotencyKey),
            new CausationId("cause-1"),
            Optional.of(new CorrelationId("corr-1"))),
        SourceChannel.CLI);
  }

  static EnvironmentDescriptor environmentDescriptor() {
    return new EnvironmentDescriptor(
        new EnvironmentDistributionDescriptor(
            ProtocolCatalog.sourceCheckoutRuntimeDistribution(),
            ProtocolCatalog.publicCliDistribution(),
            ProtocolCatalog.supportedPublicCliBundleTargets(),
            ProtocolCatalog.unsupportedPublicCliBundleTargets(),
            ProtocolCatalog.sourceCheckoutJava()),
        new EnvironmentStorageDescriptor(
            ProtocolCatalog.storageDriver(),
            ProtocolCatalog.storageEngine(),
            ProtocolCatalog.bookProtectionMode(),
            ProtocolCatalog.protectedBookFormat()),
        new EnvironmentSqliteDescriptor(
            ProtocolCatalog.sqliteLibraryMode(),
            ProtocolCatalog.sqliteLibraryEnvironmentVariable(),
            ProtocolCatalog.sqliteBundleHomeSystemProperty(),
            ProtocolCatalog.requiredSqliteCompileOptions(),
            ProtocolCatalog.forbiddenSqliteCompileOptions(),
            ProtocolCatalog.requiresSecureMemorySupport(),
            ProtocolCatalog.requiredMinimumSqliteVersion(),
            ProtocolCatalog.requiredSqlite3mcVersion(),
            ProtocolCatalog.requiredSqliteSourceId(),
            EnvironmentSqliteDescriptor.runtime(
                SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
                SqliteRuntimeStatus.UNAVAILABLE,
                null,
                null,
                null,
                null,
                null,
                null,
                "test fixture")));
  }
}
