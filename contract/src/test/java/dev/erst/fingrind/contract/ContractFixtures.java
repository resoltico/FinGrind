package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.bookkeeping.AccountPage;
import dev.erst.fingrind.contract.bookkeeping.AccountPageCursor;
import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityRow;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionRow;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementRow;
import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingPage;
import dev.erst.fingrind.contract.bookkeeping.PostingPageCursor;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.SqliteRuntimeStatus;
import dev.erst.fingrind.contract.runtime.EnvironmentDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentPublicationDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentRuntimeDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentSqliteDescriptor;
import dev.erst.fingrind.contract.runtime.EnvironmentStorageDescriptor;
import dev.erst.fingrind.contract.runtime.SqliteCompileOptionsVerificationStatus;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.ApprovalDecision;
import dev.erst.fingrind.core.ApprovalId;
import dev.erst.fingrind.core.ApprovalReference;
import dev.erst.fingrind.core.ApprovalType;
import dev.erst.fingrind.core.BookDoctrines;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.ContentSha256;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingId;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Shared contract test fixtures. */
final class ContractFixtures {
  private static final Instant FIXTURE_INSTANT = Instant.parse("2026-04-07T10:15:30Z");
  private static final LocalDate FIXTURE_DATE = LocalDate.parse("2026-04-07");
  private static final String DOCUMENT_SHA256 =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

  private ContractFixtures() {}

  static BookIdentity bookIdentity() {
    return new BookIdentity(
        new EntityProfile(new BookEntityName("Acme Studio")),
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_CASH_SERVICE,
        CurrencyUnit.of("EUR"),
        FiscalYearStart.parse("01-01"));
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
              AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
              Optional.empty());
      case LIABILITY ->
          new AccountTaxonomy(
              AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.CURRENT_LIABILITY),
              Optional.empty());
      case EQUITY ->
          new AccountTaxonomy(
              AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.OTHER_EQUITY),
              Optional.empty());
      case REVENUE ->
          new AccountTaxonomy(
              AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.empty(),
              Optional.of(ProfitAndLossLineClassification.OPERATING_REVENUE));
      case EXPENSE ->
          new AccountTaxonomy(
              AccountNodeKind.POSTABLE,
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
        lineCode,
        lineName,
        accountType,
        lineRole,
        Optional.of(lineClassification),
        lineKind,
        balance);
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
        Optional.of(lineClassification),
        lineKind,
        openingBalance,
        movement,
        closingBalance);
  }

  static PostEntryCommand postEntryCommand(String idempotencyKey) {
    return new PostEntryCommand(
        new BookkeepingEntry.ReversalAdjustment(
            new JournalEntry(
                FIXTURE_DATE,
                List.of(
                    new JournalLine(
                        new AccountCode("1000"),
                        JournalLine.EntrySide.DEBIT,
                        Money.parse("EUR", "10.00")),
                    new JournalLine(
                        new AccountCode("2000"),
                        JournalLine.EntrySide.CREDIT,
                        Money.parse("EUR", "10.00")))),
            new dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal(
                new ReversalReference(new PostingId("posting-0")),
                new ReversalReason("operator reversal"))),
        accountingEvidence(idempotencyKey),
        requestProvenance(idempotencyKey),
        SourceChannel.CLI);
  }

  static AccountingEvidence accountingEvidence(String token) {
    return new AccountingEvidence(List.of(sourceDocumentReference(token)), List.of());
  }

  static RequestProvenance requestProvenance(String idempotencyKey) {
    return new RequestProvenance(
        new ActorId("actor-1"),
        ActorType.AGENT,
        new CommandId("command-1"),
        new IdempotencyKey(idempotencyKey),
        new CausationId("cause-1"),
        Optional.of(new CorrelationId("corr-1")));
  }

  static SourceDocumentReference sourceDocumentReference(String token) {
    return new SourceDocumentReference(
        new SourceDocumentId("document-" + token),
        new SourceDocumentType("cash-receipt"),
        FIXTURE_DATE,
        FIXTURE_INSTANT,
        new StorageLocator("evidence://documents/document-" + token + ".pdf"),
        new ContentSha256(DOCUMENT_SHA256));
  }

  static ApprovalReference approvalReference(String token) {
    return new ApprovalReference(
        new ApprovalId("approval-" + token),
        new ApprovalType("manager-signoff"),
        new ActorId("manager-1"),
        ActorType.PERSON,
        ApprovalDecision.APPROVED,
        FIXTURE_INSTANT);
  }

  static EnvironmentDescriptor environmentDescriptor() {
    return new EnvironmentDescriptor(
        new EnvironmentRuntimeDescriptor(
            ProtocolCatalog.distribution().sourceCheckoutRuntimeDistribution(),
            OutputMode.TEXT,
            null),
        new EnvironmentPublicationDescriptor(
            ProtocolCatalog.distribution().publicCliDistribution(),
            ProtocolCatalog.distribution().supportedPublicCliBundleTargets(),
            ProtocolCatalog.distribution().unsupportedPublicCliBundleTargets(),
            ProtocolCatalog.distribution().sourceCheckoutJava()),
        new EnvironmentStorageDescriptor(
            ProtocolCatalog.runtime().storageDriver(),
            ProtocolCatalog.runtime().storageEngine(),
            ProtocolCatalog.runtime().bookProtectionMode(),
            ProtocolCatalog.runtime().protectedBookFormat()),
        new EnvironmentSqliteDescriptor(
            ProtocolCatalog.runtime().sqliteLibraryMode(),
            ProtocolCatalog.runtime().sqliteBundleHomeSystemProperty(),
            ProtocolCatalog.managedSqlite().requiredCompileOptions(),
            ProtocolCatalog.managedSqlite().forbiddenCompileOptions(),
            ProtocolCatalog.managedSqlite().requiresSecureMemorySupport(),
            ProtocolCatalog.managedSqlite().requiredMinimumSqliteVersion(),
            ProtocolCatalog.managedSqlite().requiredSqlite3mcVersion(),
            ProtocolCatalog.managedSqlite().requiredSqliteSourceId(),
            EnvironmentSqliteDescriptor.runtime(
                SqliteCompileOptionsVerificationStatus.NOT_VERIFIED,
                SqliteRuntimeStatus.UNAVAILABLE,
                null,
                null,
                null,
                null,
                null,
                null,
                "test fixture"),
            null));
  }
}
