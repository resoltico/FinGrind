package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import dev.erst.fingrind.contract.tax.TaxRegistrationNumber;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountRegistryDependency;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.executor.bookkeeping.AccountAmendmentOutcome;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryLifecycleRejection;
import dev.erst.fingrind.executor.bookkeeping.AccountRetirementOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Durable Account Registry lifecycle behavior over the canonical SQLite book schema. */
class SqliteAccountRegistryLifecyclePersistenceTest extends SqlitePostingFactStoreTestSupport {
  private static final Instant DECLARED_AT = Instant.parse("2026-04-07T10:15:30Z");
  private static final Instant AMENDED_AT = Instant.parse("2026-04-08T10:15:30Z");
  private static final Instant RETIRED_AT = Instant.parse("2026-04-09T10:15:30Z");

  @Test
  void lifecycleMutations_persistAdmittedChangesAndRemainIdempotent() {
    Path bookPath = tempDirectory.resolve("account-lifecycle-admitted.sqlite");
    AccountCode accountCode = new AccountCode("1010");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath));
        SqliteAdministrationSession administrationSession =
            SqliteCapabilitySessions.administration(postingFactStore);
        SqlitePostingSession postingSession = SqliteCapabilitySessions.posting(postingFactStore)) {
      openBookWithNoDeclaredAccounts(postingFactStore);
      declareAccount(
          postingFactStore,
          accountCode,
          new AccountName("Cash reserve"),
          AccountType.ASSET,
          financialPositionTaxonomy(FinancialPositionLineClassification.CURRENT_ASSET),
          DECLARED_AT);

      AccountAmendmentOutcome.Amended amended =
          assertInstanceOf(
              AccountAmendmentOutcome.Amended.class,
              administrationSession.amendAccount(
                  nonCurrentAssetAmendment(accountCode),
                  AMENDED_AT,
                  SqliteAttestationTestSupport.authorizer()));

      assertEquals("Operating reserve", amended.account().accountName().value());
      assertEquals(
          FinancialPositionLineClassification.NONCURRENT_ASSET,
          amended.account().accountTaxonomy().financialPositionLineClassification().orElseThrow());
      assertEquals(
          "Operating reserve",
          queryText(
              requireStoreDatabase(postingFactStore),
              "select account_name from account where account_code = '1010'"));
      assertEquals(
          "NONCURRENT_ASSET",
          queryText(
              requireStoreDatabase(postingFactStore),
              "select financial_position_line_classification from account where account_code = '1010'"));
      assertEquals(
          1,
          countRowsWhereTextEquals(
              requireStoreDatabase(postingFactStore),
              "audit_event",
              "event_kind",
              "ACCOUNT_AMENDED"));

      assertInstanceOf(
          AccountAmendmentOutcome.Unchanged.class,
          postingSession.amendAccount(
              nonCurrentAssetAmendment(accountCode),
              RETIRED_AT,
              SqliteAttestationTestSupport.authorizer()));
      assertEquals(
          1,
          countRowsWhereTextEquals(
              requireStoreDatabase(postingFactStore),
              "audit_event",
              "event_kind",
              "ACCOUNT_AMENDED"));

      AccountRetirementOutcome.Retired retired =
          assertInstanceOf(
              AccountRetirementOutcome.Retired.class,
              administrationSession.retireAccount(
                  accountCode, RETIRED_AT, SqliteAttestationTestSupport.authorizer()));
      assertFalse(retired.account().active());
      assertEquals(
          0,
          queryInt(
              requireStoreDatabase(postingFactStore),
              "select active from account where account_code = '1010'"));
      assertEquals(
          1,
          countRowsWhereTextEquals(
              requireStoreDatabase(postingFactStore),
              "audit_event",
              "event_kind",
              "ACCOUNT_RETIRED"));

      assertInstanceOf(
          AccountRetirementOutcome.Unchanged.class,
          postingSession.retireAccount(
              accountCode, RETIRED_AT, SqliteAttestationTestSupport.authorizer()));
      assertEquals(
          1,
          countRowsWhereTextEquals(
              requireStoreDatabase(postingFactStore),
              "audit_event",
              "event_kind",
              "ACCOUNT_RETIRED"));
    }
  }

  @Test
  void lifecycleMutations_distinguishPostingHistoryFromNonZeroBalance() {
    Path bookPath = tempDirectory.resolve("account-lifecycle-posting-history.sqlite");
    AccountCode cashAccountCode = new AccountCode("1000");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      assertInstanceOf(
          PostingCommitResult.Committed.class,
          commitPosting(
              postingFactStore,
              postingFact("posting-1", "posting-lifecycle-1", Optional.empty(), Optional.empty())));

      AccountAmendmentOutcome.Rejected amendmentRejected =
          assertInstanceOf(
              AccountAmendmentOutcome.Rejected.class,
              postingFactStore
                  .storeMutationOperations()
                  .amendAccount(
                      nonCurrentAssetAmendment(cashAccountCode),
                      AMENDED_AT,
                      SqliteAttestationTestSupport.authorizer()));
      assertEquals(
          new AccountRegistryLifecycleRejection.AccountHasDependents(
              cashAccountCode, List.of(AccountRegistryDependency.POSTINGS)),
          amendmentRejected.rejection());

      AccountRetirementOutcome.Rejected retirementRejected =
          assertInstanceOf(
              AccountRetirementOutcome.Rejected.class,
              postingFactStore
                  .storeMutationOperations()
                  .retireAccount(
                      cashAccountCode, RETIRED_AT, SqliteAttestationTestSupport.authorizer()));
      assertEquals(
          new AccountRegistryLifecycleRejection.AccountBalanceNotZero(cashAccountCode),
          retirementRejected.rejection());
    }
  }

  @Test
  void lifecycleMutations_refuseTaxChildAndContraOperationalReferences() {
    Path bookPath = tempDirectory.resolve("account-lifecycle-operational-references.sqlite");
    AccountCode payableAccountCode = new AccountCode("2100");
    AccountCode parentAccountCode = new AccountCode("1100");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      openBookWithNoDeclaredAccounts(postingFactStore);
      declareTaxAccounts(postingFactStore);
      assertInstanceOf(
          DeclareTaxRegistrationResult.Declared.class,
          postingFactStore
              .storeMutationOperations()
              .declareTaxRegistration(
                  taxRegistration(), DECLARED_AT, SqliteAttestationTestSupport.authorizer()));

      assertDependentAccountRejection(
          assertInstanceOf(
              AccountAmendmentOutcome.Rejected.class,
              postingFactStore
                  .storeMutationOperations()
                  .amendAccount(
                      currentLiabilityAmendment(payableAccountCode),
                      AMENDED_AT,
                      SqliteAttestationTestSupport.authorizer())),
          payableAccountCode,
          AccountRegistryDependency.TAX_REGISTRATIONS);
      assertDependentAccountRejection(
          assertInstanceOf(
              AccountRetirementOutcome.Rejected.class,
              postingFactStore
                  .storeMutationOperations()
                  .retireAccount(
                      payableAccountCode, RETIRED_AT, SqliteAttestationTestSupport.authorizer())),
          payableAccountCode,
          AccountRegistryDependency.TAX_REGISTRATIONS);

      declareAccount(
          postingFactStore,
          parentAccountCode,
          new AccountName("Cash header"),
          AccountType.ASSET,
          currentAssetHeaderTaxonomy(),
          DECLARED_AT);
      declareAccount(
          postingFactStore,
          new AccountCode("1110"),
          new AccountName("Cash child"),
          AccountType.ASSET,
          currentAssetChildTaxonomy(parentAccountCode),
          DECLARED_AT);

      assertDependentAccountRejection(
          assertInstanceOf(
              AccountAmendmentOutcome.Rejected.class,
              postingFactStore
                  .storeMutationOperations()
                  .amendAccount(
                      currentAssetHeaderAmendment(parentAccountCode),
                      AMENDED_AT,
                      SqliteAttestationTestSupport.authorizer())),
          parentAccountCode,
          AccountRegistryDependency.CHILD_ACCOUNTS);

      AccountCode contraTargetAccountCode = new AccountCode("1200");
      declareAccount(
          postingFactStore,
          contraTargetAccountCode,
          new AccountName("Trade receivables"),
          AccountType.ASSET,
          financialPositionTaxonomy(FinancialPositionLineClassification.CURRENT_ASSET),
          DECLARED_AT);
      declareAccount(
          postingFactStore,
          new AccountCode("1290"),
          new AccountName("Expected credit losses"),
          AccountType.ASSET,
          currentAssetContraTaxonomy(contraTargetAccountCode),
          DECLARED_AT);

      assertDependentAccountRejection(
          assertInstanceOf(
              AccountAmendmentOutcome.Rejected.class,
              postingFactStore
                  .storeMutationOperations()
                  .amendAccount(
                      nonCurrentAssetAmendment(contraTargetAccountCode),
                      AMENDED_AT,
                      SqliteAttestationTestSupport.authorizer())),
          contraTargetAccountCode,
          AccountRegistryDependency.CONTRA_ACCOUNTS);
      assertDependentAccountRejection(
          assertInstanceOf(
              AccountRetirementOutcome.Rejected.class,
              postingFactStore
                  .storeMutationOperations()
                  .retireAccount(
                      contraTargetAccountCode,
                      RETIRED_AT,
                      SqliteAttestationTestSupport.authorizer())),
          contraTargetAccountCode,
          AccountRegistryDependency.CONTRA_ACCOUNTS);
      assertDependentAccountRejection(
          assertInstanceOf(
              AccountRetirementOutcome.Rejected.class,
              postingFactStore
                  .storeMutationOperations()
                  .retireAccount(
                      parentAccountCode, RETIRED_AT, SqliteAttestationTestSupport.authorizer())),
          parentAccountCode,
          AccountRegistryDependency.CHILD_ACCOUNTS);
    }
  }

  @Test
  void lifecycleMutations_rejectUninitializedAndUnknownAccountsWithoutDurableChange() {
    Path missingBookPath = tempDirectory.resolve("account-lifecycle-missing.sqlite");
    AccountCode accountCode = new AccountCode("1010");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(missingBookPath))) {
      assertEquals(
          new AccountAmendmentOutcome.Rejected(
              new BookkeepingAdministrationRejection.BookNotInitialized()),
          postingFactStore
              .storeMutationOperations()
              .amendAccount(
                  nonCurrentAssetAmendment(accountCode),
                  AMENDED_AT,
                  SqliteAttestationTestSupport.authorizer()));
      assertEquals(
          new AccountRetirementOutcome.Rejected(
              new BookkeepingAdministrationRejection.BookNotInitialized()),
          postingFactStore
              .storeMutationOperations()
              .retireAccount(accountCode, RETIRED_AT, SqliteAttestationTestSupport.authorizer()));
    }

    Path blankBookPath = tempDirectory.resolve("account-lifecycle-blank.sqlite");
    try {
      Files.createFile(blankBookPath);
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(blankBookPath))) {
      assertEquals(
          new dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome.Rejected(
              new BookkeepingAdministrationRejection.BookNotInitialized()),
          declareAccount(
              postingFactStore,
              accountCode,
              new AccountName("Cash reserve"),
              AccountType.ASSET,
              NormalBalance.DEBIT,
              DECLARED_AT));
      assertEquals(
          new AccountAmendmentOutcome.Rejected(
              new BookkeepingAdministrationRejection.BookNotInitialized()),
          postingFactStore
              .storeMutationOperations()
              .amendAccount(
                  nonCurrentAssetAmendment(accountCode),
                  AMENDED_AT,
                  SqliteAttestationTestSupport.authorizer()));
      assertEquals(
          new AccountRetirementOutcome.Rejected(
              new BookkeepingAdministrationRejection.BookNotInitialized()),
          postingFactStore
              .storeMutationOperations()
              .retireAccount(accountCode, RETIRED_AT, SqliteAttestationTestSupport.authorizer()));
    }

    Path initializedBookPath = tempDirectory.resolve("account-lifecycle-unknown.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(initializedBookPath))) {
      openBookWithNoDeclaredAccounts(postingFactStore);
      assertEquals(
          new AccountAmendmentOutcome.Rejected(
              new AccountRegistryLifecycleRejection.AccountNotFound(accountCode)),
          postingFactStore
              .storeMutationOperations()
              .amendAccount(
                  nonCurrentAssetAmendment(accountCode),
                  AMENDED_AT,
                  SqliteAttestationTestSupport.authorizer()));
      assertEquals(
          new AccountRetirementOutcome.Rejected(
              new AccountRegistryLifecycleRejection.AccountNotFound(accountCode)),
          postingFactStore
              .storeMutationOperations()
              .retireAccount(accountCode, RETIRED_AT, SqliteAttestationTestSupport.authorizer()));
      assertEquals(
          0,
          countRowsWhereTextEquals(
              requireStoreDatabase(postingFactStore),
              "audit_event",
              "event_kind",
              "ACCOUNT_AMENDED"));
      assertEquals(
          0,
          countRowsWhereTextEquals(
              requireStoreDatabase(postingFactStore),
              "audit_event",
              "event_kind",
              "ACCOUNT_RETIRED"));
    }
  }

  @Test
  void lifecycleMutations_rollBackAndTranslateNativeAndRuntimeStorageFailures() {
    Path bookPath = tempDirectory.resolve("account-lifecycle-storage-failures.sqlite");
    AccountCode accountCode = new AccountCode("1010");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      openBookWithNoDeclaredAccounts(postingFactStore);
      AtomicReference<SqliteNativeDatabase> realDatabase =
          new AtomicReference<>(requireStoreDatabase(postingFactStore));

      try (StoreDatabaseSwap ignored =
          swapStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath))) {
        assertNativeFailure(
            () ->
                postingFactStore
                    .storeMutationOperations()
                    .amendAccount(
                        nonCurrentAssetAmendment(accountCode),
                        AMENDED_AT,
                        SqliteAttestationTestSupport.authorizer()),
            "Failed to amend SQLite book account.");
      } catch (IOException exception) {
        throw new UncheckedIOException(exception);
      }
      try (StoreDatabaseSwap ignored =
          swapStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath))) {
        assertNativeFailure(
            () ->
                postingFactStore
                    .storeMutationOperations()
                    .retireAccount(
                        accountCode, RETIRED_AT, SqliteAttestationTestSupport.authorizer()),
            "Failed to retire SQLite book account.");
      } catch (IOException exception) {
        throw new UncheckedIOException(exception);
      }

      try (SqliteStatementRedirectingDatabase runtimeFailingDatabase =
              new SqliteStatementRedirectingDatabase(
                  realDatabase.get(),
                  sql -> {
                    if (SqlitePostingReadWriteSql.FIND_ACCOUNT_BY_CODE.equals(sql)) {
                      throw new IllegalStateException("forced account lookup failure");
                    }
                    return realDatabase.get().prepare(sql);
                  });
          StoreDatabaseSwap ignored = swapStoreDatabase(postingFactStore, runtimeFailingDatabase)) {
        assertRuntimeFailure(
            () ->
                postingFactStore
                    .storeMutationOperations()
                    .amendAccount(
                        nonCurrentAssetAmendment(accountCode),
                        AMENDED_AT,
                        SqliteAttestationTestSupport.authorizer()));
        assertRuntimeFailure(
            () ->
                postingFactStore
                    .storeMutationOperations()
                    .retireAccount(
                        accountCode, RETIRED_AT, SqliteAttestationTestSupport.authorizer()));
      }

      assertEquals(0, countRows(realDatabase.get(), "account"));
      assertEquals(
          0,
          countRowsWhereTextEquals(
              realDatabase.get(), "audit_event", "event_kind", "ACCOUNT_AMENDED"));
      assertEquals(
          0,
          countRowsWhereTextEquals(
              realDatabase.get(), "audit_event", "event_kind", "ACCOUNT_RETIRED"));
    }
  }

  @Test
  void openingAndAccountDeclaration_rollBackAndPropagateStorageFailures() {
    Path bookPath = tempDirectory.resolve("opening-and-declaration-storage-failures.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      try (StoreDatabaseSwap ignored =
          swapStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath))) {
        assertNativeFailure(
            () ->
                postingFactStore.openAttestedBook(
                    DECLARED_AT,
                    bookIdentity(),
                    List.of(),
                    SqliteAttestationTestSupport.genesis(bookIdentity(), DECLARED_AT)),
            "Failed to initialize SQLite book.");
      } catch (IOException exception) {
        throw new UncheckedIOException(exception);
      }

      openBookWithNoDeclaredAccounts(postingFactStore);
      AtomicReference<SqliteNativeDatabase> realDatabase =
          new AtomicReference<>(requireStoreDatabase(postingFactStore));
      AccountDeclaration declaration =
          new AccountDeclaration(
              new AccountCode("1010"),
              new AccountName("Cash reserve"),
              AccountType.ASSET,
              financialPositionTaxonomy(FinancialPositionLineClassification.CURRENT_ASSET));
      try (StoreDatabaseSwap ignored =
          swapStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath))) {
        assertNativeFailure(
            () ->
                postingFactStore
                    .storeMutationOperations()
                    .declareAccount(
                        declaration, DECLARED_AT, SqliteAttestationTestSupport.authorizer()),
            "Failed to declare SQLite book account.");
      } catch (IOException exception) {
        throw new UncheckedIOException(exception);
      }

      try (SqliteStatementRedirectingDatabase runtimeFailingDatabase =
              new SqliteStatementRedirectingDatabase(
                  realDatabase.get(),
                  sql -> {
                    if (SqlitePostingReadWriteSql.FIND_ACCOUNT_BY_CODE.equals(sql)) {
                      throw new IllegalStateException("forced declaration lookup failure");
                    }
                    return realDatabase.get().prepare(sql);
                  });
          StoreDatabaseSwap ignored = swapStoreDatabase(postingFactStore, runtimeFailingDatabase)) {
        IllegalStateException failure =
            assertThrows(
                IllegalStateException.class,
                () ->
                    postingFactStore
                        .storeMutationOperations()
                        .declareAccount(
                            declaration, DECLARED_AT, SqliteAttestationTestSupport.authorizer()));
        assertEquals("forced declaration lookup failure", failure.getMessage());
      }

      assertEquals(0, countRows(realDatabase.get(), "account"));
      assertEquals(
          0,
          countRowsWhereTextEquals(
              realDatabase.get(), "audit_event", "event_kind", "ACCOUNT_DECLARED"));
    }
  }

  @Test
  void taxDeclaration_rollsBackAndTranslatesNativeAndRuntimeStorageFailures() {
    Path bookPath = tempDirectory.resolve("tax-declaration-storage-failures.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      openBookWithNoDeclaredAccounts(postingFactStore);
      declareTaxAccounts(postingFactStore);
      AtomicReference<SqliteNativeDatabase> realDatabase =
          new AtomicReference<>(requireStoreDatabase(postingFactStore));

      try (StoreDatabaseSwap ignored =
          swapStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath))) {
        assertNativeFailure(
            () ->
                postingFactStore
                    .storeMutationOperations()
                    .declareTaxRegistration(
                        taxRegistration(), DECLARED_AT, SqliteAttestationTestSupport.authorizer()),
            "Failed to declare SQLite tax registration.");
      } catch (IOException exception) {
        throw new UncheckedIOException(exception);
      }

      try (SqliteStatementRedirectingDatabase runtimeFailingDatabase =
              new SqliteStatementRedirectingDatabase(
                  realDatabase.get(),
                  sql -> {
                    if (SqliteTaxSql.FIND_TAX_REGISTRATION_BY_ID.equals(sql)) {
                      throw new IllegalStateException("forced tax-registration lookup failure");
                    }
                    return realDatabase.get().prepare(sql);
                  });
          StoreDatabaseSwap ignored = swapStoreDatabase(postingFactStore, runtimeFailingDatabase)) {
        IllegalStateException failure =
            assertThrows(
                IllegalStateException.class,
                () ->
                    postingFactStore
                        .storeMutationOperations()
                        .declareTaxRegistration(
                            taxRegistration(),
                            DECLARED_AT,
                            SqliteAttestationTestSupport.authorizer()));
        assertEquals("forced tax-registration lookup failure", failure.getMessage());
      }

      AtomicInteger taxRegistrationLookups = new AtomicInteger();
      try (SqliteStatementRedirectingDatabase disappearingWriteDatabase =
              new SqliteStatementRedirectingDatabase(
                  realDatabase.get(),
                  sql -> {
                    if (SqliteTaxSql.FIND_TAX_REGISTRATION_BY_ID.equals(sql)
                        && taxRegistrationLookups.incrementAndGet() == 2) {
                      return realDatabase.get().prepare("select 1 where ?1 is not null and 0");
                    }
                    return realDatabase.get().prepare(sql);
                  });
          StoreDatabaseSwap ignored =
              swapStoreDatabase(postingFactStore, disappearingWriteDatabase)) {
        assertEquals(
            "Persisted SQLite tax registration disappeared after write: vat-lv",
            assertThrows(
                    IllegalStateException.class,
                    () ->
                        postingFactStore
                            .storeMutationOperations()
                            .declareTaxRegistration(
                                taxRegistration(),
                                DECLARED_AT,
                                SqliteAttestationTestSupport.authorizer()))
                .getMessage());
      }

      assertEquals(0, countRows(realDatabase.get(), "tax_registration"));
    }
  }

  private static void assertNativeFailure(ThrowingRunnable invocation, String expectedMessage) {
    IllegalStateException failure = assertThrows(IllegalStateException.class, invocation::run);
    assertTrue(NullTestSupport.messageOf(failure).contains(expectedMessage));
  }

  private static void assertRuntimeFailure(ThrowingRunnable invocation) {
    IllegalStateException failure = assertThrows(IllegalStateException.class, invocation::run);
    assertEquals("forced account lookup failure", failure.getMessage());
  }

  private static void assertDependentAccountRejection(
      AccountAmendmentOutcome.Rejected rejected,
      AccountCode accountCode,
      AccountRegistryDependency dependency) {
    assertEquals(
        new AccountRegistryLifecycleRejection.AccountHasDependents(
            accountCode, List.of(dependency)),
        rejected.rejection());
  }

  private static void assertDependentAccountRejection(
      AccountRetirementOutcome.Rejected rejected,
      AccountCode accountCode,
      AccountRegistryDependency dependency) {
    assertEquals(
        new AccountRegistryLifecycleRejection.AccountHasDependents(
            accountCode, List.of(dependency)),
        rejected.rejection());
  }

  private static AccountDeclaration nonCurrentAssetAmendment(AccountCode accountCode) {
    return new AccountDeclaration(
        accountCode,
        new AccountName("Operating reserve"),
        AccountType.ASSET,
        financialPositionTaxonomy(FinancialPositionLineClassification.NONCURRENT_ASSET));
  }

  private static AccountDeclaration currentLiabilityAmendment(AccountCode accountCode) {
    return new AccountDeclaration(
        accountCode,
        new AccountName("Tax payable amended"),
        AccountType.LIABILITY,
        financialPositionTaxonomy(FinancialPositionLineClassification.CURRENT_LIABILITY));
  }

  private static AccountDeclaration currentAssetHeaderAmendment(AccountCode accountCode) {
    return new AccountDeclaration(
        accountCode,
        new AccountName("Cash header amended"),
        AccountType.ASSET,
        currentAssetHeaderTaxonomy());
  }

  private static AccountTaxonomy currentAssetHeaderTaxonomy() {
    return new AccountTaxonomy(
        AccountNodeKind.HEADER,
        Optional.empty(),
        Optional.empty(),
        Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
        Optional.empty(),
        Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT));
  }

  private static AccountTaxonomy currentAssetChildTaxonomy(AccountCode parentAccountCode) {
    return new AccountTaxonomy(
        AccountNodeKind.POSTABLE,
        Optional.of(parentAccountCode),
        Optional.empty(),
        Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
        Optional.empty(),
        Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT));
  }

  private static AccountTaxonomy currentAssetContraTaxonomy(AccountCode contraTargetAccountCode) {
    return new AccountTaxonomy(
        AccountNodeKind.POSTABLE,
        Optional.empty(),
        Optional.of(contraTargetAccountCode),
        Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
        Optional.empty(),
        Optional.of(CashFlowAssetClassification.NON_CASH));
  }

  private static void declareTaxAccounts(SqlitePostingFactStore postingFactStore) {
    declareAccount(
        postingFactStore,
        new AccountCode("1300"),
        new AccountName("Recoverable tax"),
        AccountType.ASSET,
        financialPositionTaxonomy(FinancialPositionLineClassification.CURRENT_ASSET),
        DECLARED_AT);
    declareAccount(
        postingFactStore,
        new AccountCode("2100"),
        new AccountName("Tax payable"),
        AccountType.LIABILITY,
        financialPositionTaxonomy(FinancialPositionLineClassification.CURRENT_LIABILITY),
        DECLARED_AT);
  }

  private static DeclareTaxRegistrationCommand taxRegistration() {
    return new DeclareTaxRegistrationCommand(
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
                TaxApplicationKind.OUTPUT_SALE)));
  }
}
