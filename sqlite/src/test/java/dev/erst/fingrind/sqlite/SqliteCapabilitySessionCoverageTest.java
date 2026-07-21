package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.InventoryMovementKind;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.core.Quantity;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.core.RequestFingerprint;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.core.WeightedAverageCostingMath;
import dev.erst.fingrind.core.attestation.AttestationPlanOperationAuthorizer;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepDraft;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepOutcome;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepPlanner;
import dev.erst.fingrind.executor.bookkeeping.InventoryAccountState;
import dev.erst.fingrind.executor.bookkeeping.InventoryMovementRecord;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Coverage locks for the narrowed SQLite capability-session and lifecycle helpers. */
class SqliteCapabilitySessionCoverageTest extends SqlitePostingFactStoreTestSupport {
  @Test
  void capabilitySessionsExposeOperationalLifecycleAndUnderlyingStoreContext() {
    Path bookPath = tempDirectory.resolve("capability-session-coverage.sqlite");
    initializeBookOnDisk(bookPath);
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath));
        SqliteReadSession readSession = SqliteCapabilitySessions.read(postingFactStore);
        SqliteAdministrationSession administrationSession =
            SqliteCapabilitySessions.administration(postingFactStore)) {
      assertTrue(postingFactStore.allowsInitializedWorkflow());
      assertEquals(bookIdentity(), postingFactStore.requireInitializedBookIdentity());

      assertTrue(readSession.allowsInitializedWorkflow());
      assertEquals(bookIdentity(), readSession.requireInitializedBookIdentity());
      assertSame(
          postingFactStore.storeContext(),
          assertInstanceOf(SqliteReadCapabilitySession.class, readSession).storeContext());

      assertTrue(administrationSession.allowsInitializedWorkflow());
      assertEquals(bookIdentity(), administrationSession.requireInitializedBookIdentity());
      assertSame(
          postingFactStore.storeLifecycle(),
          assertInstanceOf(SqliteAdministrationCapabilitySession.class, administrationSession)
              .storeLifecycle());
      assertSame(
          postingFactStore.storeContext(),
          assertInstanceOf(SqliteAdministrationCapabilitySession.class, administrationSession)
              .storeContext());
    }
  }

  @Test
  void readCapabilitySession_exposesEveryEmptyInitializedBookReadProjection() {
    Path bookPath = tempDirectory.resolve("read-capability-session-coverage.sqlite");
    try (SqlitePostingFactStore store = openStore(bookAccess(bookPath));
        SqliteReadSession session = SqliteCapabilitySessions.read(store)) {
      initializeBookWithMinimalNumericAccounts(store);
      AccountCode cashAccountCode = new AccountCode("1000");
      PostingId missingPostingId = new PostingId("fabb2aac-3f31-38f5-af1d-e7bc3dfdc9e2");

      assertEquals(store.findAccount(cashAccountCode), session.findAccount(cashAccountCode));
      assertEquals(
          store.findAccounts(java.util.Set.of(cashAccountCode)),
          session.findAccounts(java.util.Set.of(cashAccountCode)));
      assertEquals(store.allAccounts(), session.allAccounts());
      assertEquals(
          store.listAccounts(firstAccountPage()), session.listAccounts(firstAccountPage()));
      assertEquals(
          Optional.empty(),
          session.findExistingPosting(new IdempotencyKey("read-session-idempotency")));
      assertEquals(Optional.empty(), session.findPosting(missingPostingId));
      assertEquals(Optional.empty(), session.findReversalFor(missingPostingId));
      assertEquals(
          List.of(),
          session
              .listPostings(postingHistoryQuery(Optional.empty(), null, null, 50, Optional.empty()))
              .postings());
      assertEquals(List.of(), session.postings(EffectiveDateRange.unbounded()));
      assertEquals(Optional.empty(), session.earliestPostingEffectiveDate());
      assertEquals(Optional.empty(), session.transferredThroughEffectiveDate());
      assertEquals(Optional.empty(), session.latestPostingEffectiveDate());
      assertEquals(List.of(), session.financingArrangements());
      assertFalse(
          session.hasFinancingArrangement(
              new dev.erst.fingrind.contract.bookkeeping.FinancingArrangementId(
                  "read-session-financing")));
      assertEquals(
          Optional.empty(),
          session.findFinancingArrangement(
              new dev.erst.fingrind.contract.bookkeeping.FinancingArrangementId(
                  "read-session-financing")));
      assertEquals(List.of(), session.foreignCurrencyObligations());
      assertFalse(
          session.hasForeignCurrencyObligation(
              new dev.erst.fingrind.contract.bookkeeping.ForeignCurrencyObligationId(
                  "read-session-obligation")));
      assertEquals(
          Optional.empty(),
          session.findForeignCurrencyObligation(
              new dev.erst.fingrind.contract.bookkeeping.ForeignCurrencyObligationId(
                  "read-session-obligation")));
    }
  }

  @Test
  void postingFactStoreLifecycleView_primesInspectsAndCoordinatesBothPlanTransactionOutcomes() {
    Path bookPath = tempDirectory.resolve("posting-store-lifecycle-view.sqlite");
    try (SqlitePostingFactStore store = openStore(bookAccess(bookPath))) {
      initializeBookWithMinimalNumericAccounts(store);

      assertSame(
          store,
          assertInstanceOf(
                  dev.erst.fingrind.contract.runtime.ContractDecision.Accepted.class, store.prime())
              .value());
      assertEquals(bookPath.toAbsolutePath().normalize(), store.bookPath());
      assertEquals(SqliteStoreAccessMode.READ_WRITE_CREATE, store.accessMode());
      assertSame(store.storeContext().postingReader(), store.postingReader());
      store.requireInitializedBook(store.activeNativeDatabase());

      store.beginLedgerPlanTransaction();
      store.commitLedgerPlanTransaction();
      store.beginLedgerPlanTransaction();
      store.rollbackLedgerPlanTransaction();
    }
  }

  @Test
  void reportingCloseSession_exposesItsLifecycleAccountAndPostingReadCapabilities() {
    Path bookPath = tempDirectory.resolve("reporting-close-read-capabilities.sqlite");
    try (SqlitePostingFactStore store = openStore(bookAccess(bookPath));
        SqliteReportingPeriodCloseSession session =
            SqliteCapabilitySessions.reportingPeriodClose(store)) {
      initializeBookWithMinimalNumericAccounts(store);
      ReportingPeriod period =
          new ReportingPeriod(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"));

      assertTrue(session.allowsInitializedWorkflow());
      assertEquals(bookIdentity(), session.requireInitializedBookIdentity());
      assertEquals(store.allAccounts(), session.allAccounts());
      assertEquals(
          store.listAccounts(firstAccountPage()), session.listAccounts(firstAccountPage()));
      assertEquals(List.of(), session.postings(period.effectiveDateRange()));
      assertEquals(Optional.empty(), session.earliestPostingEffectiveDate());
      assertEquals(Optional.empty(), session.transferredThroughEffectiveDate());
    }
  }

  @Test
  void planExecutionSession_commitsOnlyItsFinalAggregateAttestation() {
    Path bookPath = tempDirectory.resolve("plan-session-attestation.sqlite");
    try (SqlitePostingFactStore store = openStore(bookAccess(bookPath));
        SqlitePlanExecutionSession session = SqliteCapabilitySessions.planExecution(store)) {
      initializeBookWithMinimalNumericAccounts(store);
      AttestationPlanOperationAuthorizer authorizer =
          new AttestationPlanOperationAuthorizer(SqliteAttestationTestSupport.authorizer());

      session.beginLedgerPlanTransaction();
      session.appendPlanAttestation(
          "no-op-plan", Instant.parse("2026-07-21T12:00:00Z"), authorizer);
      assertEquals(
          3, queryInt(requireStoreDatabase(store), "select count(*) from attestation_operation"));
      authorizer.enterStep(0);
      assertInstanceOf(
          dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome.Declared.class,
          session.declareAccount(
              new AccountDeclaration(
                  new AccountCode("3000"),
                  new AccountName("Plan equity"),
                  AccountType.EQUITY,
                  financialPositionTaxonomy(
                      FinancialPositionLineClassification.EQUITY_CONTRIBUTION)),
              Instant.parse("2026-07-21T12:00:00Z"),
              authorizer));
      session.appendPlanAttestation(
          "account-plan", Instant.parse("2026-07-21T12:00:01Z"), authorizer);
      session.commitLedgerPlanTransaction();

      assertEquals(
          4, queryInt(requireStoreDatabase(store), "select count(*) from attestation_operation"));
    }
  }

  @Test
  void reportingPeriodCloseCapabilitySessionCoversDraftHelperAndStoreContext() {
    Path missingBookPath = tempDirectory.resolve("period-transfer-session-coverage.sqlite");
    Path blankBookPath = tempDirectory.resolve("period-transfer-session-blank.sqlite");
    LocalDate effectiveDate = LocalDate.parse("2026-04-07");
    Instant sweptAt = Instant.parse("2026-04-07T10:15:30Z");
    ReportingPeriod reportingPeriod = new ReportingPeriod(effectiveDate, effectiveDate);
    InterimResultSweepPlanner planner = InterimResultSweepPlanner.forBookIdentity(bookIdentity());
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(missingBookPath));
        SqliteReportingPeriodCloseSession reportingPeriodCloseSession =
            SqliteCapabilitySessions.reportingPeriodClose(postingFactStore)) {
      assertSame(
          postingFactStore.storeContext(),
          assertInstanceOf(
                  SqliteReportingPeriodCloseCapabilitySession.class, reportingPeriodCloseSession)
              .storeContext());
      assertEquals(
          new InterimResultSweepOutcome.Rejected(
              new BookkeepingAdministrationRejection.BookNotInitialized()),
          assertInstanceOf(
                  SqliteReportingPeriodCloseCapabilitySession.class, reportingPeriodCloseSession)
              .interimResultSweep(
                  emptyInterimResultSweepDraft(effectiveDate, sweptAt),
                  unusedPostingIdGenerator(),
                  SqliteAttestationTestSupport.authorizer()));
      assertEquals(
          new InterimResultSweepOutcome.Rejected(
              new BookkeepingAdministrationRejection.BookNotInitialized()),
          reportingPeriodCloseSession.interimResultSweep(
              reportingPeriod,
              bookIdentity(),
              planner,
              effectiveDate,
              sweptAt,
              unusedPostingIdGenerator(),
              SqliteAttestationTestSupport.authorizer()));
      assertEquals(
          new InterimResultSweepOutcome.Rejected(
              new BookkeepingAdministrationRejection.BookNotInitialized()),
          reportingPeriodCloseSession.interimResultSweep(
              effectiveDate,
              effectiveDate,
              bookIdentity(),
              planner,
              effectiveDate,
              sweptAt,
              unusedPostingIdGenerator(),
              SqliteAttestationTestSupport.authorizer()));
    }
    createEmptySqliteFile(blankBookPath);
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(blankBookPath));
        SqliteReportingPeriodCloseSession reportingPeriodCloseSession =
            SqliteCapabilitySessions.reportingPeriodClose(postingFactStore)) {
      assertEquals(
          new InterimResultSweepOutcome.Rejected(
              new BookkeepingAdministrationRejection.BookNotInitialized()),
          assertInstanceOf(
                  SqliteReportingPeriodCloseCapabilitySession.class, reportingPeriodCloseSession)
              .interimResultSweep(
                  emptyInterimResultSweepDraft(effectiveDate, sweptAt),
                  unusedPostingIdGenerator(),
                  SqliteAttestationTestSupport.authorizer()));
    }
  }

  @Test
  void postingFactStoreOperationalLifecycleBranchesRejectBlankBooksAndWrapStaleHandles()
      throws Exception {
    Path blankBookPath = tempDirectory.resolve("operational-lifecycle-blank.sqlite");
    createEmptySqliteFile(blankBookPath);
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(blankBookPath))) {
      assertFalse(postingFactStore.allowsInitializedWorkflow());
    }

    Path missingIdentityBookPath = tempDirectory.resolve("operational-lifecycle-missing.sqlite");
    createSchemaOnlyBook(missingIdentityBookPath);
    withStandaloneDatabase(
        bookAccess(missingIdentityBookPath), SqliteStoreFixtureSupport::insertInitializedAtRow);
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(missingIdentityBookPath))) {
      setStoreCachedBookState(
          postingFactStore,
          new SqliteBookStateSnapshot(
              SqliteBookContract.APPLICATION_ID,
              SqliteBookContract.FORMAT_VERSION,
              SqliteBookState.INITIALIZED_FINGRIND));
      IllegalStateException missingIdentity =
          assertThrows(
              IllegalStateException.class, postingFactStore::requireInitializedBookIdentity);
      assertEquals(
          "Initialized SQLite book is missing book identity.", missingIdentity.getMessage());
    }

    Path staleBookPath = tempDirectory.resolve("operational-lifecycle-stale.sqlite");
    initializeBookOnDisk(staleBookPath);
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(staleBookPath))) {
      setStoreDatabase(postingFactStore, staleDatabaseHandle(staleBookPath));
      IllegalStateException workflowFailure =
          assertThrows(IllegalStateException.class, postingFactStore::allowsInitializedWorkflow);
      assertTrue(
          Objects.requireNonNull(workflowFailure.getMessage())
              .contains("Failed to access SQLite book."));
      IllegalStateException identityFailure =
          assertThrows(
              IllegalStateException.class, postingFactStore::requireInitializedBookIdentity);
      assertTrue(
          Objects.requireNonNull(identityFailure.getMessage())
              .contains("Failed to access SQLite book."));
      setStoreDatabase(postingFactStore, null);
    }
  }

  @Test
  void transactionValidationBookOperationalLifecycleHelpersCoverMissingIdentityAndStaleHandle()
      throws Exception {
    Path missingIdentityBookPath = tempDirectory.resolve("validation-missing-identity.sqlite");
    createSchemaOnlyBook(missingIdentityBookPath);
    withStandaloneDatabase(
        bookAccess(missingIdentityBookPath), SqliteStoreFixtureSupport::insertInitializedAtRow);
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(missingIdentityBookPath))) {
      SqliteTransactionValidationBook validationBook =
          new SqliteTransactionValidationBook(
              postingFactStore.activeNativeDatabase(), postingFactStore.postingReader());
      IllegalStateException missingIdentity =
          assertThrows(IllegalStateException.class, validationBook::requireInitializedBookIdentity);
      assertEquals(
          "Initialized SQLite book is missing book identity.", missingIdentity.getMessage());
    }

    Path staleBookPath = tempDirectory.resolve("validation-stale-lifecycle.sqlite");
    initializeBookOnDisk(staleBookPath);
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(staleBookPath))) {
      SqliteTransactionValidationBook validationBook =
          new SqliteTransactionValidationBook(
              staleDatabaseHandle(staleBookPath), postingFactStore.postingReader());
      IllegalStateException workflowFailure =
          assertThrows(IllegalStateException.class, validationBook::allowsInitializedWorkflow);
      assertTrue(
          Objects.requireNonNull(workflowFailure.getMessage())
              .contains("Failed to access SQLite book."));
      IllegalStateException identityFailure =
          assertThrows(IllegalStateException.class, validationBook::requireInitializedBookIdentity);
      assertTrue(
          Objects.requireNonNull(identityFailure.getMessage())
              .contains("Failed to access SQLite book."));
    }
  }

  @Test
  void inventorySessionsAndValidationBookExposeInventoryStateAndMovementQueries() {
    Path bookPath = tempDirectory.resolve("inventory-capability-coverage.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath));
        SqliteReadSession readSession = SqliteCapabilitySessions.read(postingFactStore);
        SqlitePostingSession postingSession = SqliteCapabilitySessions.posting(postingFactStore);
        SqlitePlanExecutionSession planExecutionSession =
            SqliteCapabilitySessions.planExecution(postingFactStore)) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      DatabaseHandleRef activeDatabase =
          new DatabaseHandleRef(requireStoreDatabase(postingFactStore));
      assertEquals(Optional.empty(), postingSession.earliestPostingEffectiveDate());
      assertEquals(Optional.empty(), postingSession.transferredThroughEffectiveDate());
      assertEquals(
          new dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome.Declared(
              registeredAccount(
                  new AccountCode("1400"),
                  new AccountName("Inventory"),
                  AccountType.ASSET,
                  financialPositionTaxonomy(FinancialPositionLineClassification.INVENTORY),
                  true,
                  Instant.parse("2026-04-07T10:30:00Z"))),
          postingFactStore.declareAccount(
              new dev.erst.fingrind.executor.bookkeeping.AccountDeclaration(
                  new AccountCode("1400"),
                  new AccountName("Inventory"),
                  AccountType.ASSET,
                  financialPositionTaxonomy(FinancialPositionLineClassification.INVENTORY),
                  new dev.erst.fingrind.core.UnitOfMeasure("unit", 0)),
              Instant.parse("2026-04-07T10:30:00Z"),
              SqliteAttestationTestSupport.authorizer()));
      insertInventoryPostingFactRow(
          activeDatabase.value(),
          "7383e00e-486e-310b-a663-7672ae9d4159",
          "inventory-idem-1",
          PostingOriginKind.PURCHASE_SETTLED,
          "1400",
          "1000");
      insertInventoryPostingFactRow(
          activeDatabase.value(),
          "0ade5f8e-9609-3b94-bb31-5593699bbcb7",
          "inventory-idem-2",
          PostingOriginKind.SALE_SETTLED,
          "2000",
          "1000");
      assertEquals(
          1,
          SqliteInventoryCostingWriter.insertInventoryMovement(
              activeDatabase.value(),
              "movement-1",
              new AccountCode("1400"),
              LocalDate.parse("2026-04-07"),
              InventoryMovementKind.ACQUISITION,
              10L,
              1_000L,
              new PostingId("7383e00e-486e-310b-a663-7672ae9d4159")));
      assertEquals(
          2,
          SqliteInventoryCostingWriter.insertInventoryMovement(
              activeDatabase.value(),
              "movement-2",
              new AccountCode("1400"),
              LocalDate.parse("2026-04-07"),
              InventoryMovementKind.DISPOSAL,
              -4L,
              -400L,
              new PostingId("0ade5f8e-9609-3b94-bb31-5593699bbcb7")));
      SqliteTransactionValidationBook validationBook =
          new SqliteTransactionValidationBook(
              activeDatabase.value(), postingFactStore.postingReader());

      assertEquals(
          Optional.empty(),
          assertInstanceOf(SqliteReadCapabilitySession.class, readSession)
              .findInventoryAccountState(new AccountCode("1400")));
      assertEquals(
          Optional.empty(), validationBook.findInventoryAccountState(new AccountCode("1400")));

      SqliteInventoryCostingWriter.upsertInventoryOnHand(
          activeDatabase.value(), new AccountCode("1400"), 6L, 600L, LocalDate.parse("2026-04-07"));

      InventoryAccountState expectedState =
          new InventoryAccountState(
              new WeightedAverageCostingMath.InventoryPool(
                  Quantity.ofScaledUnits(0, 6L), Money.ofMinorUnits(CurrencyUnit.of("EUR"), 600L)),
              Optional.of(LocalDate.parse("2026-04-07")));
      List<InventoryMovementRecord> expectedAcquisitionMovements =
          List.of(
              new InventoryMovementRecord(
                  new AccountCode("1400"),
                  LocalDate.parse("2026-04-07"),
                  InventoryMovementKind.ACQUISITION,
                  10L,
                  1_000L));
      List<InventoryMovementRecord> expectedDisposalMovements =
          List.of(
              new InventoryMovementRecord(
                  new AccountCode("1400"),
                  LocalDate.parse("2026-04-07"),
                  InventoryMovementKind.DISPOSAL,
                  -4L,
                  -400L));

      assertEquals(
          Optional.of(expectedState),
          assertInstanceOf(SqliteReadCapabilitySession.class, readSession)
              .findInventoryAccountState(new AccountCode("1400")));
      assertEquals(
          Optional.empty(),
          assertInstanceOf(SqliteReadCapabilitySession.class, readSession)
              .findInventoryAccountState(new AccountCode("1000")));
      assertEquals(
          Optional.empty(),
          assertInstanceOf(SqliteReadCapabilitySession.class, readSession)
              .findInventoryAccountState(new AccountCode("9999")));
      assertEquals(
          expectedAcquisitionMovements,
          assertInstanceOf(SqliteReadCapabilitySession.class, readSession)
              .inventoryMovements(new PostingId("7383e00e-486e-310b-a663-7672ae9d4159")));
      assertEquals(
          expectedDisposalMovements,
          assertInstanceOf(SqliteReadCapabilitySession.class, readSession)
              .inventoryMovements(new PostingId("0ade5f8e-9609-3b94-bb31-5593699bbcb7")));
      assertEquals(
          List.of(),
          assertInstanceOf(SqliteReadCapabilitySession.class, readSession)
              .inventoryMovements(new PostingId("35b64143-46df-384f-898b-57d9ce1c50c1")));

      assertEquals(
          Optional.of(expectedState),
          postingSession.findInventoryAccountState(new AccountCode("1400")));
      assertEquals(
          expectedAcquisitionMovements,
          postingSession.inventoryMovements(new PostingId("7383e00e-486e-310b-a663-7672ae9d4159")));

      assertEquals(
          Optional.of(expectedState),
          planExecutionSession.findInventoryAccountState(new AccountCode("1400")));
      assertEquals(
          expectedAcquisitionMovements,
          planExecutionSession.inventoryMovements(
              new PostingId("7383e00e-486e-310b-a663-7672ae9d4159")));

      assertEquals(
          Optional.of(expectedState),
          validationBook.findInventoryAccountState(new AccountCode("1400")));
      assertEquals(
          Optional.empty(), validationBook.findInventoryAccountState(new AccountCode("1000")));
      assertEquals(
          Optional.empty(), validationBook.findInventoryAccountState(new AccountCode("9999")));
      assertEquals(
          expectedAcquisitionMovements,
          validationBook.inventoryMovements(new PostingId("7383e00e-486e-310b-a663-7672ae9d4159")));
      assertEquals(
          List.of(),
          validationBook.inventoryMovements(new PostingId("35b64143-46df-384f-898b-57d9ce1c50c1")));
    }
  }

  @Test
  void inventoryQueryOwnersRejectMalformedStateRowsAndWrapNativeFailures() throws Exception {
    Path bookPath = tempDirectory.resolve("inventory-query-failure-coverage.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath));
        SqliteReadSession readSession = SqliteCapabilitySessions.read(postingFactStore)) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      DatabaseHandleRef activeDatabase =
          new DatabaseHandleRef(requireStoreDatabase(postingFactStore));
      assertEquals(
          new dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome.Declared(
              registeredAccount(
                  new AccountCode("1400"),
                  new AccountName("Inventory"),
                  AccountType.ASSET,
                  financialPositionTaxonomy(FinancialPositionLineClassification.INVENTORY),
                  true,
                  Instant.parse("2026-04-07T10:30:00Z"))),
          postingFactStore.declareAccount(
              new dev.erst.fingrind.executor.bookkeeping.AccountDeclaration(
                  new AccountCode("1400"),
                  new AccountName("Inventory"),
                  AccountType.ASSET,
                  financialPositionTaxonomy(FinancialPositionLineClassification.INVENTORY),
                  new dev.erst.fingrind.core.UnitOfMeasure("unit", 0)),
              Instant.parse("2026-04-07T10:30:00Z"),
              SqliteAttestationTestSupport.authorizer()));
      try (SqliteStatementRedirectingDatabase missingIdentityDatabase =
              new SqliteStatementRedirectingDatabase(
                  activeDatabase.value(),
                  sql ->
                      activeDatabase
                          .value()
                          .prepare(
                              SqlitePostingSql.FIND_BOOK_IDENTITY_CORE.equals(sql)
                                  ? "select 1 where 0"
                                  : sql));
          StoreDatabaseSwap ignored =
              swapStoreDatabase(postingFactStore, missingIdentityDatabase)) {
        IllegalStateException missingIdentity =
            assertThrows(
                IllegalStateException.class,
                () ->
                    assertInstanceOf(SqliteReadCapabilitySession.class, readSession)
                        .findInventoryAccountState(new AccountCode("1400")));
        assertEquals(
            "Initialized SQLite book is missing book identity.", missingIdentity.getMessage());
      }

      try (SqliteStatementRedirectingDatabase duplicateStateRedirectingDatabase =
              new SqliteStatementRedirectingDatabase(
                  activeDatabase.value(),
                  sql ->
                      activeDatabase
                          .value()
                          .prepare(
                              SqliteInventoryCostingSql.LOAD_INVENTORY_ON_HAND_BY_ACCOUNT.equals(
                                      sql)
                                  ? """
                            select 6 as quantity, 600 as cost_pool_minor, '2026-04-07' as last_movement_date
                            where ?1 is not null
                            union all
                            select 7, 700, '2026-04-08'
                            where ?1 is not null
                            """
                                  : sql));
          StoreDatabaseSwap ignored =
              swapStoreDatabase(postingFactStore, duplicateStateRedirectingDatabase)) {
        IllegalStateException duplicateState =
            assertThrows(
                IllegalStateException.class,
                () ->
                    assertInstanceOf(SqliteReadCapabilitySession.class, readSession)
                        .findInventoryAccountState(new AccountCode("1400")));
        assertEquals(
            "SQLite inventory_on_hand query returned more than one row for account 1400.",
            duplicateState.getMessage());

        IllegalStateException duplicateStateInValidationBook =
            assertThrows(
                IllegalStateException.class,
                () ->
                    new SqliteTransactionValidationBook(
                            duplicateStateRedirectingDatabase, postingFactStore.postingReader())
                        .findInventoryAccountState(new AccountCode("1400")));
        assertEquals(
            "SQLite inventory_on_hand query returned more than one row for account 1400.",
            duplicateStateInValidationBook.getMessage());
      }

      try (StoreDatabaseSwap ignored =
          swapStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath))) {
        IllegalStateException inventoryStateFailure =
            assertThrows(
                IllegalStateException.class,
                () ->
                    assertInstanceOf(SqliteReadCapabilitySession.class, readSession)
                        .findInventoryAccountState(new AccountCode("1400")));
        assertTrue(
            Objects.requireNonNull(inventoryStateFailure.getMessage())
                .contains("Failed to query SQLite inventory state."));
        IllegalStateException inventoryMovementFailure =
            assertThrows(
                IllegalStateException.class,
                () ->
                    assertInstanceOf(SqliteReadCapabilitySession.class, readSession)
                        .inventoryMovements(new PostingId("7383e00e-486e-310b-a663-7672ae9d4159")));
        assertTrue(
            Objects.requireNonNull(inventoryMovementFailure.getMessage())
                .contains("Failed to query SQLite inventory movements."));
      }

      SqliteTransactionValidationBook failingInventoryStateValidationBook =
          new SqliteTransactionValidationBook(
              new SqliteStatementRedirectingDatabase(
                  activeDatabase.value(),
                  sql ->
                      SqliteInventoryCostingSql.LOAD_INVENTORY_ON_HAND_BY_ACCOUNT.equals(sql)
                          ? new ThrowingSqliteNativeDatabase().prepare(sql)
                          : activeDatabase.value().prepare(sql)),
              postingFactStore.postingReader());
      IllegalStateException inventoryStateFailure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  failingInventoryStateValidationBook.findInventoryAccountState(
                      new AccountCode("1400")));
      assertTrue(
          Objects.requireNonNull(inventoryStateFailure.getMessage())
              .contains("Failed to query SQLite inventory state."));
      SqliteTransactionValidationBook failingInventoryMovementValidationBook =
          new SqliteTransactionValidationBook(
              new SqliteStatementRedirectingDatabase(
                  activeDatabase.value(),
                  sql ->
                      SqliteInventoryCostingSql.LOAD_INVENTORY_MOVEMENTS_BY_POSTING_ID.equals(sql)
                          ? new ThrowingSqliteNativeDatabase().prepare(sql)
                          : activeDatabase.value().prepare(sql)),
              postingFactStore.postingReader());
      IllegalStateException inventoryMovementFailure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  failingInventoryMovementValidationBook.inventoryMovements(
                      new PostingId("7383e00e-486e-310b-a663-7672ae9d4159")));
      assertTrue(
          Objects.requireNonNull(inventoryMovementFailure.getMessage())
              .contains("Failed to query SQLite inventory movements."));
    }
  }

  private static void insertInventoryPostingFactRow(
      SqliteNativeDatabase database,
      String postingId,
      String idempotencyKey,
      PostingOriginKind postingOriginKind,
      String inventoryAccountCode,
      String counterpartyAccountCode) {
    String primaryDebitAccountCode;
    String primaryCreditAccountCode;
    String amountCurrencyCode;
    String amountMinor;
    String quantity;
    String unitCostCurrencyCode;
    String unitCostMinor;
    switch (postingOriginKind) {
      case PURCHASE_SETTLED -> {
        primaryDebitAccountCode = inventoryAccountCode;
        primaryCreditAccountCode = counterpartyAccountCode;
        amountCurrencyCode = "null";
        amountMinor = "null";
        quantity = "'1'";
        unitCostCurrencyCode = "'EUR'";
        unitCostMinor = "100";
      }
      case SALE_SETTLED -> {
        primaryDebitAccountCode = counterpartyAccountCode;
        primaryCreditAccountCode = inventoryAccountCode;
        amountCurrencyCode = "'EUR'";
        amountMinor = "100";
        quantity = "null";
        unitCostCurrencyCode = "null";
        unitCostMinor = "null";
      }
      default ->
          throw new IllegalArgumentException(
              "Inventory posting fixture supports only purchase-settled and sale-settled origins.");
    }
    database.executeStatement(
        """
        insert into posting_fact (
            posting_id,
            posting_kind,
            posting_origin_kind,
            entry_primary_debit_account_code,
            entry_primary_credit_account_code,
            entry_adjunct_account_code,
            entry_amount_currency_code,
            entry_amount_minor,
            entry_adjunct_amount_minor,
            entry_quantity,
            entry_unit_cost_currency_code,
            entry_unit_cost_minor,
            effective_date,
            recorded_at,
            command_id,
            idempotency_key,
            causation_id,
            correlation_id,
            reason,
            source_channel,
            prior_posting_id,
            request_fingerprint_version,
            request_fingerprint_sha256
        ) values (
            '%s',
            'STANDARD',
            '%s',
            '%s',
            '%s',
            null,
            %s,
            %s,
            null,
            %s,
            %s,
            %s,
            '2026-04-07',
            '2026-04-07T10:15:30Z',
            '019e26ff-0000-7002-8000-000000000001',
            '%s',
            'cause-1',
            null,
            null,
            '%s',
            null,
            %d,
            '%s'
        )
        """
            .formatted(
                postingId,
                postingOriginKind.wireValue(),
                primaryDebitAccountCode,
                primaryCreditAccountCode,
                amountCurrencyCode,
                amountMinor,
                quantity,
                unitCostCurrencyCode,
                unitCostMinor,
                idempotencyKey,
                SourceChannel.CLI.wireValue(),
                RequestFingerprint.CURRENT_VERSION,
                "0".repeat(64)));
  }

  private static PostingIdGenerator unusedPostingIdGenerator() {
    return () -> new dev.erst.fingrind.core.PostingId("1153abd3-5eb5-3203-9e2f-4900e0e136c3");
  }

  private record DatabaseHandleRef(SqliteNativeDatabase value) {}

  private static InterimResultSweepDraft emptyInterimResultSweepDraft(
      LocalDate effectiveDate, Instant sweptAt) {
    return new InterimResultSweepDraft(
        new ReportingPeriod(effectiveDate, effectiveDate),
        new AccountCode("3200"),
        List.of(),
        sweptAt,
        List.of());
  }
}
