package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationCommand;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationResult;
import dev.erst.fingrind.contract.tax.ListTaxRegistrationsQuery;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxCode;
import dev.erst.fingrind.contract.tax.TaxCodeDefinition;
import dev.erst.fingrind.contract.tax.TaxCodeName;
import dev.erst.fingrind.contract.tax.TaxDeclarationRejection;
import dev.erst.fingrind.contract.tax.TaxInclusionMode;
import dev.erst.fingrind.contract.tax.TaxJurisdiction;
import dev.erst.fingrind.contract.tax.TaxObligationFrequency;
import dev.erst.fingrind.contract.tax.TaxRate;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.contract.tax.TaxRegistrationName;
import dev.erst.fingrind.contract.tax.TaxRegistrationNumber;
import dev.erst.fingrind.contract.tax.TaxRegistrationPage;
import dev.erst.fingrind.contract.tax.TaxSelection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestFingerprint;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingDraft;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Integration coverage for tax registration storage, tax-bearing postings, and related views. */
class SqliteTaxStoreCoverageTest extends SqlitePostingFactStoreTestSupport {
  private static final Instant INITIALIZED_AT = Instant.parse("2026-04-07T10:15:30Z");
  private static final Instant FIRST_DECLARED_AT = Instant.parse("2026-04-08T10:15:30Z");
  private static final Instant SECOND_DECLARED_AT = Instant.parse("2026-04-09T10:15:30Z");
  private static final Instant THIRD_DECLARED_AT = Instant.parse("2026-04-10T10:15:30Z");
  private static final Instant FOURTH_DECLARED_AT = Instant.parse("2026-04-11T10:15:30Z");
  private static final LocalDate EFFECTIVE_DATE = LocalDate.parse("2026-04-25");
  private static final int SQLITE_API_PREPARE_V2 = 14;
  private static final int SQLITE_API_STEP = 19;
  private static final int SQLITE_API_FINALIZE = 20;
  private static final String NO_ROWS_SQL = "select 1 where 0";
  private static final MethodHandle LOAD_APPLIED_TAX = loadAppliedTaxHandle();
  private static final String DUPLICATE_TAX_REGISTRATION_ROW_SQL =
      """
      select
          tax_registration_id,
          tax_registration_name,
          jurisdiction,
          registration_number,
          payable_account_code,
          recoverable_account_code,
          obligation_frequency,
          due_days_after_period_end,
          declared_at
      from tax_registration
      where tax_registration_id = ?1
      union all
      select
          tax_registration_id,
          tax_registration_name,
          jurisdiction,
          registration_number,
          payable_account_code,
          recoverable_account_code,
          obligation_frequency,
          due_days_after_period_end,
          declared_at
      from tax_registration
      where tax_registration_id = ?1
      """;
  private static final String DUPLICATE_APPLIED_TAX_ROW_SQL =
      """
      select
          tax_registration_id,
          tax_code,
          tax_code_name,
          rate_parts_per_million_of_whole,
          inclusion_mode,
          application_kind,
          currency_code,
          taxable_amount_minor,
          tax_amount_minor,
          gross_amount_minor,
          tax_account_code
      from posting_applied_tax
      where posting_id = ?1
      union all
      select
          tax_registration_id,
          tax_code,
          tax_code_name,
          rate_parts_per_million_of_whole,
          inclusion_mode,
          application_kind,
          currency_code,
          taxable_amount_minor,
          tax_amount_minor,
          gross_amount_minor,
          tax_account_code
      from posting_applied_tax
      where posting_id = ?1
      """;

  @Test
  void taxRegistrationSurfaces_roundTripAcrossStoreAndCapabilityViews() {
    Path bookPath = tempDirectory.resolve("tax-registration-roundtrip.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath));
        SqliteReadSession readSession = SqliteCapabilitySessions.read(postingFactStore);
        SqliteAdministrationSession administrationSession =
            SqliteCapabilitySessions.administration(postingFactStore);
        SqlitePostingSession postingSession = SqliteCapabilitySessions.posting(postingFactStore)) {
      postingFactStore.openBook(INITIALIZED_AT, bookIdentity(), List.of());
      declareTaxPostingAccounts(postingFactStore);

      assertEquals(
          List.of(),
          postingFactStore.storeReadOperations().taxRegistrations().allTaxRegistrations());
      assertEquals(
          Optional.empty(),
          postingFactStore
              .storeReadOperations()
              .taxRegistrations()
              .findTaxRegistration(new TaxRegistrationId("missing-tax")));
      assertEquals(List.of(), readSession.postings(EffectiveDateRange.unbounded()));
      assertEquals(Optional.empty(), readSession.earliestPostingEffectiveDate());
      assertEquals(Optional.empty(), readSession.transferredThroughEffectiveDate());

      DeclareTaxRegistrationResult.Declared latviaDeclared =
          assertInstanceOf(
              DeclareTaxRegistrationResult.Declared.class,
              postingFactStore
                  .storeMutationOperations()
                  .declareTaxRegistration(
                      registrationCommand(
                          "vat-lv",
                          "Latvia VAT",
                          "LV40001234567",
                          20,
                          List.of(saleTaxCode(), recoverableExpenseTaxCode())),
                      FIRST_DECLARED_AT));
      DeclareTaxRegistrationResult.Declared estoniaDeclared =
          assertInstanceOf(
              DeclareTaxRegistrationResult.Declared.class,
              administrationSession.declareTaxRegistration(
                  registrationCommand(
                      "vat-ee", "Estonia VAT", "EE40001234567", 18, List.of(saleTaxCode())),
                  SECOND_DECLARED_AT));
      DeclareTaxRegistrationResult.Updated estoniaUpdated =
          assertInstanceOf(
              DeclareTaxRegistrationResult.Updated.class,
              postingSession.declareTaxRegistration(
                  registrationCommand(
                      "vat-ee",
                      "Estonia VAT Updated",
                      null,
                      19,
                      List.of(saleTaxCode(), nonrecoverableExpenseTaxCode())),
                  THIRD_DECLARED_AT));
      DeclareTaxRegistrationResult.Updated latviaUpdated =
          assertInstanceOf(
              DeclareTaxRegistrationResult.Updated.class,
              postingFactStore
                  .storeMutationOperations()
                  .declareTaxRegistration(
                      registrationCommand(
                          "vat-lv",
                          "Latvia VAT Updated",
                          null,
                          25,
                          List.of(saleTaxCode(), nonrecoverableExpenseTaxCode())),
                      FOURTH_DECLARED_AT));
      DeclareTaxRegistrationResult.Unchanged latviaUnchanged =
          assertInstanceOf(
              DeclareTaxRegistrationResult.Unchanged.class,
              postingFactStore
                  .storeMutationOperations()
                  .declareTaxRegistration(
                      registrationCommand(
                          "vat-lv",
                          "Latvia VAT Updated",
                          null,
                          25,
                          List.of(saleTaxCode(), nonrecoverableExpenseTaxCode())),
                      Instant.parse("2026-04-12T10:15:30Z")));

      assertEquals(FIRST_DECLARED_AT, latviaDeclared.registration().declaredAt());
      assertEquals(FIRST_DECLARED_AT, latviaUpdated.registration().declaredAt());
      assertEquals(FIRST_DECLARED_AT, latviaUnchanged.registration().declaredAt());
      assertEquals(SECOND_DECLARED_AT, estoniaDeclared.registration().declaredAt());
      assertEquals(SECOND_DECLARED_AT, estoniaUpdated.registration().declaredAt());
      assertEquals(
          Optional.empty(), readSession.findTaxRegistration(new TaxRegistrationId("missing-tax")));
      assertEquals(
          latviaUpdated.registration(),
          readSession.findTaxRegistration(new TaxRegistrationId("vat-lv")).orElseThrow());

      assertEquals(
          List.of(estoniaUpdated.registration(), latviaUpdated.registration()),
          readSession.allTaxRegistrations());
      TaxRegistrationPage firstPage =
          readSession.listTaxRegistrations(new ListTaxRegistrationsQuery(1, Optional.empty()));
      assertEquals(List.of(estoniaUpdated.registration()), firstPage.registrations());
      assertTrue(firstPage.hasMore());

      TaxRegistrationPage secondPage =
          postingFactStore
              .storeReadOperations()
              .taxRegistrations()
              .listTaxRegistrations(new ListTaxRegistrationsQuery(1, firstPage.nextCursor()));
      assertEquals(List.of(latviaUpdated.registration()), secondPage.registrations());
      assertEquals(Optional.empty(), secondPage.nextCursor());
    }
  }

  @Test
  void declareTaxRegistration_rejectsMissingAndBlankBooks() {
    Path missingBookPath = tempDirectory.resolve("tax-registration-missing.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(missingBookPath))) {
      assertEquals(
          new DeclareTaxRegistrationResult.Rejected(
              new TaxDeclarationRejection.BookNotInitialized()),
          postingFactStore
              .storeMutationOperations()
              .declareTaxRegistration(
                  registrationCommand(
                      "vat-lv", "Latvia VAT", "LV40001234567", 20, List.of(saleTaxCode())),
                  FIRST_DECLARED_AT));
    }

    Path blankBookPath = tempDirectory.resolve("tax-registration-blank.sqlite");
    createEmptySqliteFile(blankBookPath);
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(blankBookPath))) {
      assertEquals(
          new DeclareTaxRegistrationResult.Rejected(
              new TaxDeclarationRejection.BookNotInitialized()),
          postingFactStore
              .storeMutationOperations()
              .declareTaxRegistration(
                  registrationCommand(
                      "vat-lv", "Latvia VAT", "LV40001234567", 20, List.of(saleTaxCode())),
                  FIRST_DECLARED_AT));
    }
  }

  @Test
  void declareTaxRegistration_rollsBackWhenPersistedRegistrationDisappears() {
    Path bookPath = tempDirectory.resolve("tax-registration-disappears.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      postingFactStore.openBook(INITIALIZED_AT, bookIdentity(), List.of());
      declareTaxPostingAccounts(postingFactStore);
      AtomicInteger registrationLookupCount = new AtomicInteger();
      try (StoreDatabaseSwap ignored =
          swapStoreDatabase(
              postingFactStore,
              redirectedDatabase(
                  requireStoreDatabase(postingFactStore),
                  (database, sql) -> {
                    if (SqliteTaxSql.FIND_TAX_REGISTRATION_BY_ID.equals(sql)
                        && registrationLookupCount.incrementAndGet() == 2) {
                      return database.prepare(
                          SqliteTaxSql.BASE_TAX_REGISTRATION_SELECT
                              + " where 0 and ?1 is not null");
                    }
                    return database.prepare(sql);
                  }))) {
        IllegalStateException failure =
            assertThrows(
                IllegalStateException.class,
                () ->
                    postingFactStore
                        .storeMutationOperations()
                        .declareTaxRegistration(
                            registrationCommand(
                                "vat-lv",
                                "Latvia VAT",
                                "LV40001234567",
                                20,
                                List.of(saleTaxCode())),
                            FIRST_DECLARED_AT));
        assertEquals(
            "Persisted SQLite tax registration disappeared after write: vat-lv",
            failure.getMessage());
        assertEquals(
            0,
            queryInt(
                requireStoreDatabase(postingFactStore), "select count(*) from tax_registration"));
        assertEquals(
            0,
            queryInt(
                requireStoreDatabase(postingFactStore),
                "select count(*) from tax_registration_code"));
      }
    }
  }

  @Test
  void declareTaxRegistration_wrapsNativeFailures() throws Exception {
    Path bookPath = tempDirectory.resolve("tax-registration-native-failure.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      postingFactStore.openBook(INITIALIZED_AT, bookIdentity(), List.of());
      try (StoreDatabaseSwap ignored =
          swapStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath))) {
        IllegalStateException failure =
            assertThrows(
                IllegalStateException.class,
                () ->
                    postingFactStore
                        .storeMutationOperations()
                        .declareTaxRegistration(
                            registrationCommand(
                                "vat-lv",
                                "Latvia VAT",
                                "LV40001234567",
                                20,
                                List.of(saleTaxCode())),
                            FIRST_DECLARED_AT));
        assertTrue(
            NullTestSupport.messageOf(failure)
                .contains("Failed to declare SQLite tax registration."));
      }
    }
  }

  @Test
  void taxBearingPostingCommits_roundTripAppliedTaxAndValidationLookups() {
    Path bookPath = tempDirectory.resolve("tax-bearing-postings.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath));
        SqliteReadSession readSession = SqliteCapabilitySessions.read(postingFactStore)) {
      postingFactStore.openBook(INITIALIZED_AT, bookIdentity(), List.of());
      declareTaxPostingAccounts(postingFactStore);
      DeclareTaxRegistrationResult.Declared declared =
          assertInstanceOf(
              DeclareTaxRegistrationResult.Declared.class,
              postingFactStore
                  .storeMutationOperations()
                  .declareTaxRegistration(
                      registrationCommand(
                          "vat-lv",
                          "Latvia VAT",
                          "LV40001234567",
                          20,
                          List.of(
                              saleTaxCode(),
                              recoverableExpenseTaxCode(),
                              nonrecoverableExpenseTaxCode())),
                      FIRST_DECLARED_AT));
      BookkeepingEntry.SaleSettled taxedSale = taxedSaleEntry();
      BookkeepingEntry.ExpenseSettled taxedNonrecoverableExpense =
          taxedNonrecoverableExpenseEntry();
      BookkeepingEntry.SaleSettled untaxedSale = untaxedSaleEntry();

      PostingCommitResult.Committed committedSale =
          assertInstanceOf(
              PostingCommitResult.Committed.class,
              postingFactStore.commit(
                  postingDraft("sale", taxedSale), () -> new PostingId("sale")));
      PostingCommitResult.Committed committedExpense =
          assertInstanceOf(
              PostingCommitResult.Committed.class,
              postingFactStore.commit(
                  postingDraft("expense", taxedNonrecoverableExpense),
                  () -> new PostingId("expense")));
      PostingCommitResult.Committed committedUntaxedSale =
          assertInstanceOf(
              PostingCommitResult.Committed.class,
              postingFactStore.commit(
                  postingDraft("untaxed-sale", untaxedSale), () -> new PostingId("untaxed-sale")));

      assertEquals(
          Optional.of(taxedSale),
          readSession.findPosting(new PostingId("sale")).orElseThrow().callerAuthoredEntry());
      assertEquals(
          Optional.of(taxedNonrecoverableExpense),
          readSession.findPosting(new PostingId("expense")).orElseThrow().callerAuthoredEntry());
      assertEquals(
          Optional.of(untaxedSale),
          readSession
              .findPosting(new PostingId("untaxed-sale"))
              .orElseThrow()
              .callerAuthoredEntry());
      assertEquals(
          List.of(
              committedUntaxedSale.postingFact(),
              committedSale.postingFact(),
              committedExpense.postingFact()),
          readSession.postings(EffectiveDateRange.unbounded()));

      SqliteTransactionValidationBook validationBook =
          new SqliteTransactionValidationBook(
              postingFactStore.activeNativeDatabase(), postingFactStore.postingReader());
      assertEquals(
          declared.registration(),
          validationBook.findTaxRegistration(new TaxRegistrationId("vat-lv")).orElseThrow());
    }
  }

  @Test
  void taxQueriesAndPostingReader_rejectDuplicateSingletonRows() {
    Path bookPath = tempDirectory.resolve("tax-singleton-row-duplicates.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      postingFactStore.openBook(INITIALIZED_AT, bookIdentity(), List.of());
      declareTaxPostingAccounts(postingFactStore);
      postingFactStore
          .storeMutationOperations()
          .declareTaxRegistration(
              registrationCommand(
                  "vat-lv",
                  "Latvia VAT",
                  "LV40001234567",
                  20,
                  List.of(saleTaxCode(), nonrecoverableExpenseTaxCode())),
              FIRST_DECLARED_AT);
      postingFactStore.commit(postingDraft("sale", taxedSaleEntry()), () -> new PostingId("sale"));

      IllegalStateException duplicateRegistration =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteTaxStatementQueries.findOneTaxRegistration(
                      redirectedDatabase(
                          requireStoreDatabase(postingFactStore),
                          (database, sql) ->
                              database.prepare(
                                  SqliteTaxSql.FIND_TAX_REGISTRATION_BY_ID.equals(sql)
                                      ? DUPLICATE_TAX_REGISTRATION_ROW_SQL
                                      : sql)),
                      new TaxRegistrationId("vat-lv")));
      assertEquals(
          "SQLite tax-registration query returned more than one row for vat-lv.",
          duplicateRegistration.getMessage());

      IllegalStateException duplicateAppliedTax =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore
                      .postingReader()
                      .findOneCommittedPosting(
                          redirectedDatabase(
                              requireStoreDatabase(postingFactStore),
                              (database, sql) ->
                                  database.prepare(
                                      SqliteTaxSql.LOAD_POSTING_APPLIED_TAX.equals(sql)
                                          ? DUPLICATE_APPLIED_TAX_ROW_SQL
                                          : sql)),
                          SqlitePostingSql.FIND_POSTING_BY_ID,
                          statement -> statement.bindText(1, "sale")));
      assertEquals(
          "SQLite posting applied-tax query returned more than one row for posting sale.",
          duplicateAppliedTax.getMessage());
    }
  }

  @Test
  void taxRegistrationPage_rejectsInitializedBookWithoutBookIdentity() {
    Path bookPath = tempDirectory.resolve("tax-page-missing-book-identity.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      postingFactStore.openBook(INITIALIZED_AT, bookIdentity(), List.of());
      try (SqliteStatementRedirectingDatabase redirectedDatabase =
          redirectedDatabase(
              requireStoreDatabase(postingFactStore),
              (database, sql) ->
                  database.prepare(
                      SqlitePostingSql.FIND_BOOK_IDENTITY_CORE.equals(sql) ? NO_ROWS_SQL : sql))) {
        IllegalStateException failure =
            assertThrows(
                IllegalStateException.class,
                () ->
                    SqliteTaxStatementQueries.loadTaxRegistrationPage(
                        redirectedDatabase, new ListTaxRegistrationsQuery(10, Optional.empty())));
        assertEquals("Initialized SQLite book is missing book identity.", failure.getMessage());
      }
    }
  }

  @Test
  void postingReader_wrapsFinalizeFailureAfterAppliedTaxMiss() {
    try (Arena arena = Arena.ofConfined();
        SqliteNativeDatabase database = finalizeFailingAppliedTaxDatabase(arena)) {
      IllegalStateException failure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  invokeLoadAppliedTax(
                      new SqlitePostingReader(), database, new PostingId("posting-without-tax")));
      assertEquals("Failed to finalize a SQLite statement.", failure.getMessage());
      assertEquals("finalize boom", NullTestSupport.messageOf(NullTestSupport.causeOf(failure)));
    }
  }

  @Test
  void postingReader_propagatesPrepareFailureWhenLoadingAppliedTax() {
    SqliteNativeException failure =
        assertThrows(
            SqliteNativeException.class,
            () ->
                invokeLoadAppliedTax(
                    new SqlitePostingReader(),
                    new SqliteStoreFixtureSupport.ThrowingSqliteNativeDatabase(),
                    new PostingId("posting-without-tax")));
    assertTrue(
        NullTestSupport.messageOf(failure).contains("prepare a SQLite statement"),
        failure.getMessage());
  }

  @Test
  void postingReader_closesStatementAfterAppliedTaxStepFailure() {
    try (Arena arena = Arena.ofConfined();
        SqliteNativeDatabase database = stepFailingAppliedTaxDatabase(arena)) {
      IllegalStateException failure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  invokeLoadAppliedTax(
                      new SqlitePostingReader(), database, new PostingId("posting-with-tax")));
      assertEquals("Failed to step a SQLite statement.", failure.getMessage());
      assertEquals("step boom", NullTestSupport.messageOf(NullTestSupport.causeOf(failure)));
    }
  }

  @Test
  void postingReader_wrapsFinalizeFailureAfterAppliedTaxLoad() {
    Path bookPath = tempDirectory.resolve("posting-reader-applied-tax-finalize-success.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      postingFactStore.openBook(INITIALIZED_AT, bookIdentity(), List.of());
      declareTaxPostingAccounts(postingFactStore);
      postingFactStore
          .storeMutationOperations()
          .declareTaxRegistration(
              registrationCommand(
                  "vat-lv", "Latvia VAT", "LV40001234567", 20, List.of(saleTaxCode())),
              FIRST_DECLARED_AT);
      postingFactStore.commit(postingDraft("sale", taxedSaleEntry()), () -> new PostingId("sale"));

      try (SqliteStatementRedirectingDatabase redirectedDatabase =
          redirectedDatabase(
              finalizeFailingDatabase(requireStoreDatabase(postingFactStore)),
              SqliteNativeDatabase::prepare)) {
        IllegalStateException failure =
            assertThrows(
                IllegalStateException.class,
                () ->
                    invokeLoadAppliedTax(
                        new SqlitePostingReader(), redirectedDatabase, new PostingId("sale")));
        assertEquals("Failed to finalize a SQLite statement.", failure.getMessage());
        assertEquals("finalize boom", NullTestSupport.messageOf(NullTestSupport.causeOf(failure)));
      }
    }
  }

  @Test
  void postingReader_suppressesFinalizeFailureAfterAppliedTaxDuplicate() {
    Path bookPath = tempDirectory.resolve("posting-reader-applied-tax-finalize.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      postingFactStore.openBook(INITIALIZED_AT, bookIdentity(), List.of());
      declareTaxPostingAccounts(postingFactStore);
      postingFactStore
          .storeMutationOperations()
          .declareTaxRegistration(
              registrationCommand(
                  "vat-lv", "Latvia VAT", "LV40001234567", 20, List.of(saleTaxCode())),
              FIRST_DECLARED_AT);
      postingFactStore.commit(postingDraft("sale", taxedSaleEntry()), () -> new PostingId("sale"));

      try (SqliteStatementRedirectingDatabase redirectedDatabase =
          redirectedDatabase(
              finalizeFailingDatabase(requireStoreDatabase(postingFactStore)),
              (database, sql) ->
                  database.prepare(
                      SqliteTaxSql.LOAD_POSTING_APPLIED_TAX.equals(sql)
                          ? DUPLICATE_APPLIED_TAX_ROW_SQL
                          : sql))) {
        IllegalStateException failure =
            assertThrows(
                IllegalStateException.class,
                () ->
                    invokeLoadAppliedTax(
                        new SqlitePostingReader(), redirectedDatabase, new PostingId("sale")));
        assertTrue(
            NullTestSupport.messageOf(failure).contains("returned more than one row"),
            failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertEquals(
            "Failed to finalize a SQLite statement.", failure.getSuppressed()[0].getMessage());
        assertEquals(
            "finalize boom",
            NullTestSupport.messageOf(NullTestSupport.causeOf(failure.getSuppressed()[0])));
      }
    }
  }

  @Test
  void transactionValidationBook_wrapsNativeFailuresOnTaxLookup() throws Exception {
    Path bookPath = tempDirectory.resolve("validation-tax-native-failure.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      postingFactStore.openBook(INITIALIZED_AT, bookIdentity(), List.of());
      SqliteTransactionValidationBook validationBook =
          new SqliteTransactionValidationBook(
              staleDatabaseHandle(bookPath), postingFactStore.postingReader());

      IllegalStateException failure =
          assertThrows(
              IllegalStateException.class,
              () -> validationBook.findTaxRegistration(new TaxRegistrationId("vat-lv")));
      assertTrue(NullTestSupport.messageOf(failure).contains("Failed to query SQLite book."));
    }
  }

  private static void declareTaxPostingAccounts(SqlitePostingFactStore postingFactStore) {
    declareAccount(
        postingFactStore,
        new AccountCode("1000"),
        new AccountName("Cash"),
        AccountType.ASSET,
        accountTaxonomy(AccountType.ASSET),
        FIRST_DECLARED_AT);
    declareAccount(
        postingFactStore,
        new AccountCode("1300"),
        new AccountName("Recoverable tax"),
        AccountType.ASSET,
        financialPositionTaxonomy(FinancialPositionLineClassification.CURRENT_ASSET),
        FIRST_DECLARED_AT);
    declareAccount(
        postingFactStore,
        new AccountCode("2000"),
        new AccountName("Revenue"),
        AccountType.REVENUE,
        accountTaxonomy(AccountType.REVENUE),
        FIRST_DECLARED_AT);
    declareAccount(
        postingFactStore,
        new AccountCode("2100"),
        new AccountName("Tax payable"),
        AccountType.LIABILITY,
        financialPositionTaxonomy(FinancialPositionLineClassification.CURRENT_LIABILITY),
        FIRST_DECLARED_AT);
    declareAccount(
        postingFactStore,
        new AccountCode("5010"),
        new AccountName("Operating expense"),
        AccountType.EXPENSE,
        accountTaxonomy(AccountType.EXPENSE),
        FIRST_DECLARED_AT);
  }

  private static PostingDraft postingDraft(String token, BookkeepingEntry entry) {
    return new PostingDraft(
        entry.journalEntry(),
        PostingLineageModel.direct(),
        entry.postingKind(),
        entry.postingOriginKind(),
        accountingEvidence(token),
        new RequestFingerprint(RequestFingerprint.CURRENT_VERSION, "0".repeat(64)),
        committedProvenance(token),
        entry,
        null);
  }

  private static CommittedProvenance committedProvenance(String token) {
    return new CommittedProvenance(
        new RequestProvenance(
            new ActorId("actor-" + token),
            ActorType.AGENT,
            new CommandId("command-" + token),
            new IdempotencyKey("idem-" + token),
            new CausationId("cause-" + token),
            Optional.of(new CorrelationId("corr-" + token))),
        Instant.parse("2026-04-07T10:20:30Z"),
        SourceChannel.CLI);
  }

  private static BookkeepingEntry.SaleSettled taxedSaleEntry() {
    return new BookkeepingEntry.SaleSettled(
        EFFECTIVE_DATE,
        new AccountCode("1000"),
        new AccountCode("2000"),
        new MonetaryAmount("EUR", "10000"),
        null,
        null,
        null,
        new TaxSelection(new TaxRegistrationId("vat-lv"), new TaxCode("vat-standard-sale")),
        new AppliedTax(
            new TaxRegistrationId("vat-lv"),
            new TaxCode("vat-standard-sale"),
            new TaxCodeName("VAT Standard Sale"),
            new TaxRate(210_000),
            TaxInclusionMode.EXCLUSIVE,
            TaxApplicationKind.OUTPUT_SALE,
            new MonetaryAmount("EUR", "10000"),
            new MonetaryAmount("EUR", "2100"),
            new MonetaryAmount("EUR", "12100"),
            new AccountCode("2100")));
  }

  private static BookkeepingEntry.ExpenseSettled taxedNonrecoverableExpenseEntry() {
    return new BookkeepingEntry.ExpenseSettled(
        EFFECTIVE_DATE.plusDays(1),
        new AccountCode("5010"),
        new AccountCode("1000"),
        new MonetaryAmount("EUR", "11200"),
        null,
        new TaxSelection(
            new TaxRegistrationId("vat-lv"), new TaxCode("vat-nonrecoverable-expense")),
        new AppliedTax(
            new TaxRegistrationId("vat-lv"),
            new TaxCode("vat-nonrecoverable-expense"),
            new TaxCodeName("VAT Nonrecoverable Expense"),
            new TaxRate(120_000),
            TaxInclusionMode.INCLUSIVE,
            TaxApplicationKind.INPUT_EXPENSE_NONRECOVERABLE,
            new MonetaryAmount("EUR", "10000"),
            new MonetaryAmount("EUR", "1200"),
            new MonetaryAmount("EUR", "11200"),
            null));
  }

  private static BookkeepingEntry.SaleSettled untaxedSaleEntry() {
    return new BookkeepingEntry.SaleSettled(
        EFFECTIVE_DATE.minusDays(1),
        new AccountCode("1000"),
        new AccountCode("2000"),
        new MonetaryAmount("EUR", "5000"),
        null,
        null,
        null,
        null,
        null);
  }

  private static DeclareTaxRegistrationCommand registrationCommand(
      String taxRegistrationId,
      String taxRegistrationName,
      @Nullable String registrationNumber,
      int dueDaysAfterPeriodEnd,
      List<TaxCodeDefinition> taxCodes) {
    return new DeclareTaxRegistrationCommand(
        new TaxRegistrationId(taxRegistrationId),
        new TaxRegistrationName(taxRegistrationName),
        new TaxJurisdiction(taxRegistrationId.endsWith("-ee") ? "EE" : "LV"),
        registrationNumber == null ? null : new TaxRegistrationNumber(registrationNumber),
        new AccountCode("2100"),
        new AccountCode("1300"),
        TaxObligationFrequency.MONTHLY,
        dueDaysAfterPeriodEnd,
        taxCodes);
  }

  private static TaxCodeDefinition saleTaxCode() {
    return new TaxCodeDefinition(
        new TaxCode("vat-standard-sale"),
        new TaxCodeName("VAT Standard Sale"),
        new TaxRate(210_000),
        TaxInclusionMode.EXCLUSIVE,
        TaxApplicationKind.OUTPUT_SALE);
  }

  private static TaxCodeDefinition recoverableExpenseTaxCode() {
    return new TaxCodeDefinition(
        new TaxCode("vat-standard-expense"),
        new TaxCodeName("VAT Standard Expense"),
        new TaxRate(210_000),
        TaxInclusionMode.INCLUSIVE,
        TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE);
  }

  private static TaxCodeDefinition nonrecoverableExpenseTaxCode() {
    return new TaxCodeDefinition(
        new TaxCode("vat-nonrecoverable-expense"),
        new TaxCodeName("VAT Nonrecoverable Expense"),
        new TaxRate(120_000),
        TaxInclusionMode.INCLUSIVE,
        TaxApplicationKind.INPUT_EXPENSE_NONRECOVERABLE);
  }

  private static SqliteNativeDatabase finalizeFailingAppliedTaxDatabase(Arena arena) {
    Object[] sqliteApiArguments = SqliteNativeBridgeTestSupport.defaultSqliteApiArguments();
    sqliteApiArguments[SQLITE_API_PREPARE_V2] =
        SqliteNativeBridgeTestSupport.constantMethodHandle(
            0,
            MemorySegment.class,
            MemorySegment.class,
            int.class,
            MemorySegment.class,
            MemorySegment.class);
    sqliteApiArguments[SQLITE_API_STEP] =
        SqliteNativeBridgeTestSupport.constantMethodHandle(
            SqliteNativeResultCode.code("DONE"), MemorySegment.class);
    sqliteApiArguments[SQLITE_API_FINALIZE] =
        SqliteNativeBridgeTestSupport.throwingMethodHandle(
            new IllegalStateException("finalize boom"), int.class, MemorySegment.class);
    return new SqliteNativeDatabase(
        arena.allocate(1), SqliteNativeBridgeTestSupport.buildSqliteApi(sqliteApiArguments)) {
      @Override
      public void close() {}
    };
  }

  private static SqliteNativeDatabase stepFailingAppliedTaxDatabase(Arena arena) {
    Object[] sqliteApiArguments = SqliteNativeBridgeTestSupport.defaultSqliteApiArguments();
    sqliteApiArguments[SQLITE_API_PREPARE_V2] =
        SqliteNativeBridgeTestSupport.constantMethodHandle(
            0,
            MemorySegment.class,
            MemorySegment.class,
            int.class,
            MemorySegment.class,
            MemorySegment.class);
    sqliteApiArguments[SQLITE_API_STEP] =
        SqliteNativeBridgeTestSupport.throwingMethodHandle(
            new IllegalStateException("step boom"), int.class, MemorySegment.class);
    sqliteApiArguments[SQLITE_API_FINALIZE] =
        SqliteNativeBridgeTestSupport.constantMethodHandle(0, MemorySegment.class);
    return new SqliteNativeDatabase(
        arena.allocate(1), SqliteNativeBridgeTestSupport.buildSqliteApi(sqliteApiArguments)) {
      @Override
      public void close() {}
    };
  }

  private static SqliteNativeDatabase finalizeFailingDatabase(SqliteNativeDatabase database) {
    SqliteNativeApi sqliteApi = database.sqliteApi();
    return new SqliteNativeDatabase(
        database.handle(),
        new SqliteNativeApi(
            sqliteApi.libraryArena(),
            sqliteApi.sqlite3OpenV2(),
            sqliteApi.sqlite3CloseV2(),
            sqliteApi.sqlite3Key(),
            sqliteApi.sqlite3Rekey(),
            sqliteApi.sqlite3Shutdown(),
            sqliteApi.sqlite3BusyTimeout(),
            sqliteApi.sqlite3ExtendedResultCodes(),
            sqliteApi.sqlite3mcConfig(),
            sqliteApi.sqlite3mcConfigCipher(),
            sqliteApi.sqlite3mcCipherName(),
            sqliteApi.sqlite3FileControl(),
            sqliteApi.sqlite3Exec(),
            sqliteApi.sqlite3Free(),
            sqliteApi.sqlite3PrepareV2(),
            sqliteApi.sqlite3BindNull(),
            sqliteApi.sqlite3BindInt(),
            sqliteApi.sqlite3BindInt64(),
            sqliteApi.sqlite3BindText(),
            sqliteApi.sqlite3Step(),
            SqliteNativeBridgeTestSupport.throwingMethodHandle(
                new IllegalStateException("finalize boom"), int.class, MemorySegment.class),
            sqliteApi.sqlite3ColumnText(),
            sqliteApi.sqlite3ColumnBytes(),
            sqliteApi.sqlite3ColumnInt(),
            sqliteApi.sqlite3ColumnInt64(),
            sqliteApi.sqlite3Errmsg(),
            sqliteApi.sqlite3Errstr(),
            sqliteApi.sqlite3ExtendedErrcode(),
            sqliteApi.loadedVersion(),
            sqliteApi.loadedSqlite3mcVersion(),
            sqliteApi.loadedSourceId(),
            sqliteApi.runtimeProvenance(),
            sqliteApi.loadedLibraryPath(),
            sqliteApi.sqlite3BackupInit(),
            sqliteApi.sqlite3BackupStep(),
            sqliteApi.sqlite3BackupFinish()));
  }

  private static SqliteStatementRedirectingDatabase redirectedDatabase(
      SqliteNativeDatabase database, StatementRedirector redirector) {
    return new SqliteStatementRedirectingDatabase(
        database, sql -> redirector.prepare(database, sql));
  }

  private static @Nullable AppliedTax invokeLoadAppliedTax(
      SqlitePostingReader postingReader, SqliteNativeDatabase activeDatabase, PostingId postingId) {
    try {
      return (@Nullable AppliedTax)
          LOAD_APPLIED_TAX.invoke(postingReader, activeDatabase, postingId);
    } catch (RuntimeException exception) {
      throw exception;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new AssertionError("Failed to invoke SQLite posting applied-tax loader.", throwable);
    }
  }

  private static MethodHandle loadAppliedTaxHandle() {
    try {
      return MethodHandles.privateLookupIn(SqlitePostingReader.class, MethodHandles.lookup())
          .findVirtual(
              SqlitePostingReader.class,
              "loadAppliedTax",
              MethodType.methodType(AppliedTax.class, SqliteNativeDatabase.class, PostingId.class));
    } catch (ReflectiveOperationException exception) {
      throw new LinkageError("Failed to resolve SQLite posting applied-tax loader.", exception);
    }
  }

  /** Redirects one prepared-statement request to a test-controlled SQL variant. */
  @FunctionalInterface
  private interface StatementRedirector {
    SqliteNativeStatement prepare(SqliteNativeDatabase database, String sql);
  }
}
