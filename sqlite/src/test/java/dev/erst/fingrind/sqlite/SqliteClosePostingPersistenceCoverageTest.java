package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.QuantityText;
import dev.erst.fingrind.contract.bookkeeping.ResolvedInventoryAcquisition;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.core.Quantity;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.core.RequestFingerprint;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.core.WeightedAverageCostingMath;
import dev.erst.fingrind.executor.bookkeeping.AcceptedPosting;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.ClosedFiscalYearRecord;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearCloseDraft;
import dev.erst.fingrind.executor.bookkeeping.InventoryAccountState;
import dev.erst.fingrind.executor.bookkeeping.InventoryMovementRecord;
import dev.erst.fingrind.executor.bookkeeping.PostingAcceptancePolicy;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.SweptInterimResult;
import dev.erst.fingrind.executor.spi.PostingDraft;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Focused coverage for replay and rejection branches inside generated close persistence. */
class SqliteClosePostingPersistenceCoverageTest extends SqlitePostingFactStoreTestSupport {
  private static final ReportingPeriod APRIL_2026 =
      new ReportingPeriod(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30"));
  private static final ReportingPeriod FISCAL_YEAR_2026 =
      new ReportingPeriod(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"));
  private static final Instant FIXED_INSTANT = Instant.parse("2026-12-31T23:59:59Z");

  @Test
  void persistInterimResultSweep_reusesReplayPostingWithoutWritingAnotherPostingFact() {
    Path bookPath = tempDirectory.resolve("close-persistence-sweep-replay.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      declareCloseAccount(
          postingFactStore,
          "3200",
          "Result Holding",
          FinancialPositionLineClassification.RESULT_HOLDING);
      PostingDraft replayDraft =
          generatedPostingDraft(
              "interim-result-sweep",
              "replay-eur",
              PostingKind.INTERIM_RESULT_SWEEP,
              PostingOriginKind.INTERIM_RESULT_SWEEP,
              LocalDate.parse("2026-04-30"),
              List.of(
                  line("2000", JournalLine.EntrySide.DEBIT, "10.00"),
                  line("3200", JournalLine.EntrySide.CREDIT, "10.00")));
      CommittedPosting replayPosting =
          persistAcceptedGeneratedClosePosting(
              postingFactStore, replayDraft, new PostingId("cd390bb5-f5ec-3a89-847d-b5c055b5ce3f"));

      SweptInterimResult transferred =
          closePostingPersistence(postingFactStore)
              .persistInterimResultSweep(
                  requireStoreDatabase(postingFactStore),
                  new dev.erst.fingrind.executor.bookkeeping.InterimResultSweepDraft(
                      APRIL_2026,
                      new AccountCode("3200"),
                      List.of(),
                      FIXED_INSTANT,
                      List.of(replayDraft)),
                  () -> new PostingId("c2312115-6b61-3107-97d1-09290f6cda25"),
                  SqliteAttestationTestSupport.authorizer());

      assertEquals(List.of(replayPosting.postingId()), transferred.sweepPostingIds());
      assertEquals(
          1, queryInt(requireStoreDatabase(postingFactStore), "select count(*) from posting_fact"));
      assertEquals(
          1,
          queryInt(
              requireStoreDatabase(postingFactStore), "select count(*) from interim_result_sweep"));
      assertEquals(
          1,
          queryInt(
              requireStoreDatabase(postingFactStore),
              "select count(*) from interim_result_sweep_posting"));
    }
  }

  @Test
  void persistFiscalYearClose_reusesReplayPostingWithoutWritingAnotherPostingFact() {
    Path bookPath = tempDirectory.resolve("close-persistence-fiscal-replay.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      declareAllCloseTargets(postingFactStore);
      PostingDraft replayDraft =
          generatedPostingDraft(
              "fiscal-year-close",
              "replay-eur",
              PostingKind.FISCAL_YEAR_CLOSE,
              PostingOriginKind.FISCAL_YEAR_CLOSE,
              LocalDate.parse("2026-12-31"),
              List.of(
                  line("3200", JournalLine.EntrySide.DEBIT, "10.00"),
                  line("3300", JournalLine.EntrySide.CREDIT, "10.00")));
      CommittedPosting replayPosting =
          persistAcceptedGeneratedClosePosting(
              postingFactStore, replayDraft, new PostingId("3bfbe1d7-5663-3bdc-ab95-57adb3409b51"));

      ClosedFiscalYearRecord closedFiscalYear =
          closePostingPersistence(postingFactStore)
              .persistFiscalYearClose(
                  requireStoreDatabase(postingFactStore),
                  new FiscalYearCloseDraft(
                      FISCAL_YEAR_2026,
                      new AccountCode("3000"),
                      new AccountCode("3200"),
                      new AccountCode("3300"),
                      FIXED_INSTANT,
                      null,
                      List.of(replayDraft)),
                  () -> new PostingId("c2312115-6b61-3107-97d1-09290f6cda25"),
                  SqliteAttestationTestSupport.authorizer());

      assertEquals(List.of(replayPosting.postingId()), closedFiscalYear.closePostingIds());
      assertEquals(
          1, queryInt(requireStoreDatabase(postingFactStore), "select count(*) from posting_fact"));
      assertEquals(
          1,
          queryInt(
              requireStoreDatabase(postingFactStore), "select count(*) from fiscal_year_close"));
      assertEquals(
          1,
          queryInt(
              requireStoreDatabase(postingFactStore),
              "select count(*) from fiscal_year_close_posting"));
    }
  }

  @Test
  void persistFiscalYearClose_rejectedGeneratedPostingThrowsAcceptanceFailure() {
    Path bookPath = tempDirectory.resolve("close-persistence-fiscal-rejected.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      declareAllCloseTargets(postingFactStore);
      PostingDraft existingDraft =
          generatedPostingDraft(
              "fiscal-year-close",
              "conflict-eur",
              PostingKind.FISCAL_YEAR_CLOSE,
              PostingOriginKind.FISCAL_YEAR_CLOSE,
              LocalDate.parse("2026-12-31"),
              List.of(
                  line("3200", JournalLine.EntrySide.DEBIT, "10.00"),
                  line("3300", JournalLine.EntrySide.CREDIT, "10.00")));
      persistAcceptedGeneratedClosePosting(
          postingFactStore, existingDraft, new PostingId("846394fb-4b81-3fb9-9d26-e242e9e8c6fb"));
      PostingDraft conflictingDraft =
          generatedPostingDraft(
              "fiscal-year-close",
              "conflict-eur",
              PostingKind.FISCAL_YEAR_CLOSE,
              PostingOriginKind.FISCAL_YEAR_CLOSE,
              LocalDate.parse("2026-12-31"),
              List.of(
                  line("3200", JournalLine.EntrySide.DEBIT, "11.00"),
                  line("3300", JournalLine.EntrySide.CREDIT, "11.00")));

      IllegalStateException failure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  closePostingPersistence(postingFactStore)
                      .persistFiscalYearClose(
                          requireStoreDatabase(postingFactStore),
                          new FiscalYearCloseDraft(
                              FISCAL_YEAR_2026,
                              new AccountCode("3000"),
                              new AccountCode("3200"),
                              new AccountCode("3300"),
                              FIXED_INSTANT,
                              null,
                              List.of(conflictingDraft)),
                          () -> new PostingId("960e24c2-ef40-3d2d-846b-f1d4963a42ee"),
                          SqliteAttestationTestSupport.authorizer()));

      assertTrue(
          NullTestSupport.messageOf(failure)
              .contains("Generated fiscal year close posting failed bookkeeping acceptance"));
      assertEquals(
          1, queryInt(requireStoreDatabase(postingFactStore), "select count(*) from posting_fact"));
      assertEquals(
          0,
          queryInt(
              requireStoreDatabase(postingFactStore), "select count(*) from fiscal_year_close"));
    }
  }

  @Test
  void persistInterimResultSweep_rejectedGeneratedPostingThrowsAcceptanceFailure() {
    Path bookPath = tempDirectory.resolve("close-persistence-sweep-rejected.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      declareCloseAccount(
          postingFactStore,
          "3200",
          "Result Holding",
          FinancialPositionLineClassification.RESULT_HOLDING);
      PostingDraft existingDraft =
          generatedPostingDraft(
              "interim-result-sweep",
              "conflict-eur",
              PostingKind.INTERIM_RESULT_SWEEP,
              PostingOriginKind.INTERIM_RESULT_SWEEP,
              APRIL_2026.effectiveDateTo(),
              List.of(
                  line("2000", JournalLine.EntrySide.DEBIT, "10.00"),
                  line("3200", JournalLine.EntrySide.CREDIT, "10.00")));
      persistAcceptedGeneratedClosePosting(
          postingFactStore, existingDraft, new PostingId("2fce7b0c-e8cb-3b3d-92b6-16e89f4fb41d"));
      PostingDraft conflictingDraft =
          generatedPostingDraft(
              "interim-result-sweep",
              "conflict-eur",
              PostingKind.INTERIM_RESULT_SWEEP,
              PostingOriginKind.INTERIM_RESULT_SWEEP,
              APRIL_2026.effectiveDateTo(),
              List.of(
                  line("2000", JournalLine.EntrySide.DEBIT, "11.00"),
                  line("3200", JournalLine.EntrySide.CREDIT, "11.00")));

      IllegalStateException failure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  closePostingPersistence(postingFactStore)
                      .persistInterimResultSweep(
                          requireStoreDatabase(postingFactStore),
                          new dev.erst.fingrind.executor.bookkeeping.InterimResultSweepDraft(
                              APRIL_2026,
                              new AccountCode("3200"),
                              List.of(),
                              FIXED_INSTANT,
                              List.of(conflictingDraft)),
                          () -> new PostingId("8add55e9-7295-3e68-aa20-51d9ff3abd38"),
                          SqliteAttestationTestSupport.authorizer()));

      assertTrue(
          NullTestSupport.messageOf(failure)
              .contains("Generated interim result sweep posting failed bookkeeping acceptance"));
      assertEquals(
          1, queryInt(requireStoreDatabase(postingFactStore), "select count(*) from posting_fact"));
      assertEquals(
          0,
          queryInt(
              requireStoreDatabase(postingFactStore), "select count(*) from interim_result_sweep"));
    }
  }

  @Test
  void persistAcceptedPosting_persistsInventoryMovementsAndOnHandStates() {
    Path bookPath = tempDirectory.resolve("close-persistence-inventory-effects.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      declareInventoryAccount(postingFactStore, "1410", "Inventory Reserve");
      declareInventoryAccount(postingFactStore, "1400", "Inventory");

      InventoryMovementRecord reserveAcquisition =
          new InventoryMovementRecord(
              new AccountCode("1410"),
              LocalDate.parse("2026-04-07"),
              dev.erst.fingrind.core.InventoryMovementKind.ACQUISITION,
              2L,
              300L);
      InventoryMovementRecord inventoryAcquisition =
          new InventoryMovementRecord(
              new AccountCode("1400"),
              LocalDate.parse("2026-04-07"),
              dev.erst.fingrind.core.InventoryMovementKind.ACQUISITION,
              6L,
              600L);

      CommittedPosting reservePosting =
          closingMutationOperations(postingFactStore)
              .persistAcceptedPosting(
                  requireStoreDatabase(postingFactStore),
                  acceptedInventoryPosting(
                      reserveAcquisition,
                      Map.of(new AccountCode("1410"), inventoryState(2L, 300L, "2026-04-07"))),
                  new RequestFingerprint(RequestFingerprint.CURRENT_VERSION, "0".repeat(64)),
                  generatedProvenance("inventory-posting", "persist"),
                  () -> new PostingId("7383e00e-486e-310b-a663-7672ae9d4159"));
      CommittedPosting inventoryPosting =
          closePostingPersistence(postingFactStore)
              .persistAcceptedPosting(
                  requireStoreDatabase(postingFactStore),
                  acceptedInventoryPosting(
                      inventoryAcquisition,
                      Map.of(new AccountCode("1400"), inventoryState(6L, 600L, "2026-04-07"))),
                  new RequestFingerprint(RequestFingerprint.CURRENT_VERSION, "0".repeat(64)),
                  generatedProvenance("inventory-posting", "persist-second"),
                  () -> new PostingId("0ade5f8e-9609-3b94-bb31-5593699bbcb7"));

      assertEquals(
          new PostingId("7383e00e-486e-310b-a663-7672ae9d4159"), reservePosting.postingId());
      assertEquals(
          new PostingId("0ade5f8e-9609-3b94-bb31-5593699bbcb7"), inventoryPosting.postingId());
      assertEquals(
          2,
          queryInt(
              requireStoreDatabase(postingFactStore), "select count(*) from inventory_movement"));
      assertEquals(
          "0ade5f8e-9609-3b94-bb31-5593699bbcb7/inventory/1:1400:1;7383e00e-486e-310b-a663-7672ae9d4159/inventory/1:1410:1",
          queryText(
              requireStoreDatabase(postingFactStore),
              """
              select group_concat(entry, ';')
              from (
                  select movement_id || ':' || inventory_account || ':' || cast(account_sequence as text) as entry
                  from inventory_movement
                  order by movement_id
              )
              """));
      assertEquals(
          "1400:6:600:2026-04-07;1410:2:300:2026-04-07",
          queryText(
              requireStoreDatabase(postingFactStore),
              """
              select group_concat(entry, ';')
              from (
                  select inventory_account || ':' || cast(quantity as text) || ':' || cast(cost_pool_minor as text) || ':' || last_movement_date as entry
                  from inventory_on_hand
                  order by inventory_account
              )
              """));
    }
  }

  @Test
  void persistAcceptedPosting_rejectsInventoryStatesWithoutLastMovementDate() {
    Path bookPath = tempDirectory.resolve("close-persistence-inventory-state-date.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      declareInventoryAccount(postingFactStore, "1400", "Inventory");

      IllegalStateException failure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  closePostingPersistence(postingFactStore)
                      .persistAcceptedPosting(
                          requireStoreDatabase(postingFactStore),
                          acceptedInventoryPosting(
                              new InventoryMovementRecord(
                                  new AccountCode("1400"),
                                  LocalDate.parse("2026-04-07"),
                                  dev.erst.fingrind.core.InventoryMovementKind.ACQUISITION,
                                  6L,
                                  600L),
                              Map.of(
                                  new AccountCode("1400"),
                                  new InventoryAccountState(
                                      new WeightedAverageCostingMath.InventoryPool(
                                          Quantity.ofScaledUnits(0, 6L),
                                          Money.parse("EUR", "6.00")),
                                      Optional.empty()))),
                          new RequestFingerprint(
                              RequestFingerprint.CURRENT_VERSION, "0".repeat(64)),
                          generatedProvenance("inventory-posting", "missing-date"),
                          () -> new PostingId("0ade5f8e-9609-3b94-bb31-5593699bbcb7")));

      assertTrue(
          NullTestSupport.messageOf(failure)
              .contains(
                  "Inventory state persisted after movement must own one last movement date."));
    }
  }

  private static SqliteClosePostingPersistence closePostingPersistence(
      SqlitePostingFactStore postingFactStore) {
    return new SqliteClosePostingPersistence(
        postingFactStore.storeContext(),
        SqliteCommitFaultHook.NONE,
        PostingAcceptancePolicy.currentKernel());
  }

  private static SqliteClosingMutationOperations closingMutationOperations(
      SqlitePostingFactStore postingFactStore) {
    return new SqliteClosingMutationOperations(
        postingFactStore.storeContext(),
        postingFactStore.storeLifecycle(),
        SqliteCommitFaultHook.NONE,
        PostingAcceptancePolicy.currentKernel());
  }

  private static CommittedPosting persistAcceptedGeneratedClosePosting(
      SqlitePostingFactStore postingFactStore, PostingDraft postingDraft, PostingId postingId) {
    SqliteNativeDatabase database = requireStoreDatabase(postingFactStore);
    PostingAcceptancePolicy.Decision decision =
        PostingAcceptancePolicy.currentKernel()
            .decisionFor(
                postingDraft,
                new SqliteTransactionValidationBook(
                    database, postingFactStore.storeContext().postingReader(), true));
    return switch (decision) {
      case PostingAcceptancePolicy.Decision.Accepted accepted ->
          closePostingPersistence(postingFactStore)
              .persistAcceptedPosting(
                  database,
                  accepted.acceptedPosting(),
                  accepted.requestFingerprint(),
                  postingDraft.provenance(),
                  () -> postingId);
      case PostingAcceptancePolicy.Decision.Replay _ ->
          throw new IllegalStateException(
              "Close-posting replay fixture must begin with one absent draft.");
      case PostingAcceptancePolicy.Decision.Rejected rejected ->
          throw new IllegalStateException(
              "Close-posting replay fixture must be accepted: " + rejected.rejection());
    };
  }

  private static PostingDraft generatedPostingDraft(
      String operationName,
      String token,
      PostingKind postingKind,
      PostingOriginKind postingOriginKind,
      LocalDate effectiveDate,
      List<JournalLine> lines) {
    return new PostingDraft(
        new JournalEntry(effectiveDate, lines),
        dev.erst.fingrind.executor.bookkeeping.PostingLineageModel.direct(),
        postingKind,
        postingOriginKind,
        generatedEvidence(
            token,
            postingKind == PostingKind.FISCAL_YEAR_CLOSE
                ? "year-end-close-plan"
                : "interim-result-sweep-plan"),
        new RequestFingerprint(RequestFingerprint.CURRENT_VERSION, "0".repeat(64)),
        generatedProvenance(operationName, token));
  }

  private static CommittedProvenance generatedProvenance(String operationName, String token) {
    RequestProvenance requestProvenance =
        new RequestProvenance(
            new CommandId(
                java.util
                    .UUID
                    .nameUUIDFromBytes(
                        ("fingrind-test-commandid:" + operationName + ":" + token)
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .toString()),
            new IdempotencyKey(operationName + ":" + token),
            new CausationId(operationName + ":" + token),
            Optional.of(new CorrelationId(operationName + ":" + token)));
    return new CommittedProvenance(requestProvenance, FIXED_INSTANT, SourceChannel.SYSTEM);
  }

  private static void declareAllCloseTargets(SqlitePostingFactStore postingFactStore) {
    declareCloseAccount(
        postingFactStore,
        "3000",
        "Capital",
        FinancialPositionLineClassification.EQUITY_CONTRIBUTION);
    declareCloseAccount(
        postingFactStore,
        "3200",
        "Result Holding",
        FinancialPositionLineClassification.RESULT_HOLDING);
    declareCloseAccount(
        postingFactStore,
        "3300",
        "Retained Accumulated",
        FinancialPositionLineClassification.RETAINED_ACCUMULATED);
  }

  private static void declareCloseAccount(
      SqlitePostingFactStore postingFactStore,
      String accountCode,
      String accountName,
      FinancialPositionLineClassification classification) {
    assertEquals(
        new AccountDeclarationOutcome.Declared(
            new RegisteredAccount(
                new AccountCode(accountCode),
                new AccountName(accountName),
                AccountType.EQUITY,
                financialPositionTaxonomy(classification),
                true,
                FIXED_INSTANT)),
        postingFactStore.declareAccount(
            new dev.erst.fingrind.executor.bookkeeping.AccountDeclaration(
                new AccountCode(accountCode),
                new AccountName(accountName),
                AccountType.EQUITY,
                financialPositionTaxonomy(classification)),
            FIXED_INSTANT,
            SqliteAttestationTestSupport.authorizer()));
  }

  private static void declareInventoryAccount(
      SqlitePostingFactStore postingFactStore, String accountCode, String accountName) {
    assertEquals(
        new AccountDeclarationOutcome.Declared(
            registeredAccount(
                new AccountCode(accountCode),
                new AccountName(accountName),
                AccountType.ASSET,
                financialPositionTaxonomy(FinancialPositionLineClassification.INVENTORY),
                true,
                FIXED_INSTANT)),
        postingFactStore.declareAccount(
            new dev.erst.fingrind.executor.bookkeeping.AccountDeclaration(
                new AccountCode(accountCode),
                new AccountName(accountName),
                AccountType.ASSET,
                financialPositionTaxonomy(FinancialPositionLineClassification.INVENTORY),
                new dev.erst.fingrind.core.UnitOfMeasure("unit", 0)),
            FIXED_INSTANT,
            SqliteAttestationTestSupport.authorizer()));
  }

  private static AcceptedPosting acceptedInventoryPosting(
      InventoryMovementRecord inventoryMovement,
      Map<AccountCode, InventoryAccountState> resultingInventoryStates) {
    Quantity quantity = Quantity.ofScaledUnits(0, inventoryMovement.quantityDelta());
    MonetaryAmount carryingCost =
        MonetaryAmount.of(
            Money.ofMinorUnits(CurrencyUnit.of("EUR"), inventoryMovement.costDeltaMinor()));
    MonetaryAmount unitCost =
        MonetaryAmount.of(
            Money.ofMinorUnits(
                CurrencyUnit.of("EUR"),
                inventoryMovement.costDeltaMinor() / inventoryMovement.quantityDelta()));
    QuantityText requestedQuantity =
        new QuantityText(Long.toString(inventoryMovement.quantityDelta()));
    BookkeepingEntry.PurchaseSettled callerEntry =
        new BookkeepingEntry.PurchaseSettled(
            inventoryMovement.effectiveDate(),
            inventoryMovement.inventoryAccount(),
            new AccountCode("1000"),
            requestedQuantity,
            unitCost,
            null,
            null,
            null,
            null);
    BookkeepingEntry.PurchaseSettled resolvedEntry =
        new BookkeepingEntry.PurchaseSettled(
            inventoryMovement.effectiveDate(),
            inventoryMovement.inventoryAccount(),
            new AccountCode("1000"),
            requestedQuantity,
            unitCost,
            new ResolvedInventoryAcquisition(quantity, carryingCost, carryingCost),
            null,
            null,
            null);
    return new AcceptedPosting(
        resolvedEntry.journalEntry(),
        PostingLineageModel.direct(),
        PostingKind.STANDARD,
        PostingOriginKind.PURCHASE_SETTLED,
        generatedEvidence("inventory-posting", "inventory-adjustment"),
        generatedProvenance("inventory-posting", "persist").requestProvenance(),
        SourceChannel.CLI,
        callerEntry,
        resolvedEntry,
        List.of(inventoryMovement),
        resultingInventoryStates);
  }

  private static InventoryAccountState inventoryState(
      long quantity, long costPoolMinor, String lastMovementDate) {
    return new InventoryAccountState(
        new WeightedAverageCostingMath.InventoryPool(
            Quantity.ofScaledUnits(0, quantity),
            Money.ofMinorUnits(dev.erst.fingrind.core.CurrencyUnit.of("EUR"), costPoolMinor)),
        Optional.of(LocalDate.parse(lastMovementDate)));
  }
}
