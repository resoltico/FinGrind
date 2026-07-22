package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookDoctrines;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.UnitOfMeasure;
import dev.erst.fingrind.core.attestation.AttestationRegistryInspection;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Shared SQLite account and lifecycle fixtures used by posting-store test support. */
class SqlitePostingAccountFixtureSupport extends SqliteStoreFixtureSupport {
  static BookIdentity bookIdentity() {
    return new BookIdentity(
        new EntityProfile(new BookEntityName("Acme Studio")),
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE,
        CurrencyUnit.of("EUR"),
        FiscalYearStart.parse("01-01"),
        java.time.LocalDate.parse("2026-01-01"));
  }

  static BookOpeningOutcome.Opened openedBook(Instant initializedAt) {
    return new BookOpeningOutcome.Opened(initializedAt, bookIdentity(), attestationTrustRoot());
  }

  static BookLifecycleInspection.Initialized initializedLifecycleInspection(
      int applicationId,
      int detectedBookFormatVersion,
      int supportedBookFormatVersion,
      Instant initializedAt) {
    return new BookLifecycleInspection.Initialized(
        applicationId,
        detectedBookFormatVersion,
        supportedBookFormatVersion,
        initializedAt,
        bookIdentity());
  }

  static DeclaredAccount publishedAccount(RegisteredAccount account) {
    return BookkeepingPublishedLanguageTranslator.toPublished(account);
  }

  static AccountTaxonomy accountTaxonomy(AccountType accountType) {
    return switch (accountType) {
      case ASSET ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
              Optional.empty(),
              Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT));
      case LIABILITY ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.CURRENT_LIABILITY),
              Optional.empty(),
              Optional.empty());
      case EQUITY ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.OTHER_EQUITY),
              Optional.empty(),
              Optional.empty());
      case REVENUE ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.empty(),
              Optional.empty(),
              Optional.of(ProfitAndLossLineClassification.OPERATING_REVENUE),
              Optional.empty());
      case EXPENSE ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.empty(),
              Optional.empty(),
              Optional.of(ProfitAndLossLineClassification.OPERATING_EXPENSE),
              Optional.empty());
    };
  }

  static AccountTaxonomy accountTaxonomy(AccountType accountType, NormalBalance normalBalance) {
    return SqlitePostingTaxonomyFixtures.accountTaxonomy(accountType, normalBalance);
  }

  static AccountTaxonomy financialPositionTaxonomy(
      FinancialPositionLineClassification lineClassification) {
    return new AccountTaxonomy(
        dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
        Optional.empty(),
        Optional.empty(),
        Optional.of(lineClassification),
        Optional.empty(),
        lineClassification.accountType() == AccountType.ASSET
            ? Optional.of(CashFlowAssetClassification.NON_CASH)
            : Optional.empty());
  }

  static RegisteredAccount registeredAccount(
      AccountCode accountCode,
      AccountName accountName,
      AccountType accountType,
      NormalBalance normalBalance,
      boolean active,
      Instant declaredAt) {
    return registeredAccount(
        accountCode,
        accountName,
        accountType,
        accountTaxonomy(accountType, normalBalance),
        active,
        declaredAt);
  }

  static RegisteredAccount registeredAccount(
      AccountCode accountCode,
      AccountName accountName,
      AccountType accountType,
      AccountTaxonomy accountTaxonomy,
      boolean active,
      Instant declaredAt) {
    return new RegisteredAccount(
        accountCode,
        accountName,
        accountType,
        accountTaxonomy,
        defaultUnitOfMeasure(accountTaxonomy).orElse(null),
        active,
        declaredAt);
  }

  static DeclaredAccount declaredAccount(
      AccountCode accountCode,
      AccountName accountName,
      AccountType accountType,
      NormalBalance normalBalance,
      boolean active,
      Instant declaredAt) {
    return publishedAccount(
        registeredAccount(
            accountCode, accountName, accountType, normalBalance, active, declaredAt));
  }

  static AccountDeclarationOutcome declareAccount(
      SqlitePostingFactStore postingFactStore,
      AccountCode accountCode,
      AccountName accountName,
      AccountType accountType,
      NormalBalance normalBalance,
      Instant declaredAt) {
    return declareAccount(
        postingFactStore,
        accountCode,
        accountName,
        accountType,
        accountTaxonomy(accountType, normalBalance),
        declaredAt);
  }

  static AccountDeclarationOutcome declareAccount(
      SqlitePostingFactStore postingFactStore,
      AccountCode accountCode,
      AccountName accountName,
      AccountType accountType,
      AccountTaxonomy accountTaxonomy,
      Instant declaredAt) {
    return postingFactStore.declareAccount(
        new dev.erst.fingrind.executor.bookkeeping.AccountDeclaration(
            accountCode, accountName, accountType, accountTaxonomy),
        declaredAt,
        SqliteAttestationTestSupport.authorizer());
  }

  static void openBookWithNoDeclaredAccounts(SqlitePostingFactStore postingFactStore) {
    Instant initializedAt = Instant.parse("2026-04-07T10:15:30Z");
    postingFactStore.openAttestedBook(
        initializedAt,
        bookIdentity(),
        List.of(),
        SqliteAttestationTestSupport.genesis(bookIdentity(), initializedAt));
  }

  static void openBookWithStarterTemplateAccounts(SqlitePostingFactStore postingFactStore) {
    Instant initializedAt = Instant.parse("2026-04-07T10:15:30Z");
    postingFactStore.openAttestedBook(
        initializedAt,
        bookIdentity(),
        dev.erst.fingrind.executor.bookkeeping.BookTemplateAccounts.declarations(
            bookIdentity().bookDoctrine()),
        SqliteAttestationTestSupport.genesis(bookIdentity(), initializedAt));
  }

  static void initializeBookWithMinimalNumericAccounts(SqlitePostingFactStore postingFactStore) {
    openBookWithNoDeclaredAccounts(postingFactStore);
    declareMinimalNumericAccounts(postingFactStore);
  }

  static void declareMinimalNumericAccounts(SqlitePostingFactStore postingFactStore) {
    assertEquals(
        new AccountDeclarationOutcome.Declared(
            registeredAccount(
                new AccountCode("1000"),
                new AccountName("Cash"),
                AccountType.ASSET,
                NormalBalance.DEBIT,
                true,
                Instant.parse("2026-04-07T10:15:30Z"))),
        declareAccount(
            postingFactStore,
            new AccountCode("1000"),
            new AccountName("Cash"),
            AccountType.ASSET,
            NormalBalance.DEBIT,
            Instant.parse("2026-04-07T10:15:30Z")));
    assertEquals(
        new AccountDeclarationOutcome.Declared(
            registeredAccount(
                new AccountCode("2000"),
                new AccountName("Revenue"),
                AccountType.REVENUE,
                NormalBalance.CREDIT,
                true,
                Instant.parse("2026-04-07T10:15:30Z"))),
        declareAccount(
            postingFactStore,
            new AccountCode("2000"),
            new AccountName("Revenue"),
            AccountType.REVENUE,
            NormalBalance.CREDIT,
            Instant.parse("2026-04-07T10:15:30Z")));
  }

  private static AttestationRegistryInspection attestationTrustRoot() {
    return new AttestationRegistryInspection(
        java.util.UUID.fromString("10213243-5465-7687-98a9-babcbddceeff"),
        BigInteger.ZERO,
        "0".repeat(64),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  private static Optional<UnitOfMeasure> defaultUnitOfMeasure(AccountTaxonomy accountTaxonomy) {
    return accountTaxonomy
        .financialPositionLineClassification()
        .filter(classification -> classification == FinancialPositionLineClassification.INVENTORY)
        .map(ignored -> new UnitOfMeasure("unit", 0));
  }
}
