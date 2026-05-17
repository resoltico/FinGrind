package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.operations.BusinessEventPage;
import dev.erst.fingrind.contract.operations.BusinessEventPageCursor;
import dev.erst.fingrind.contract.operations.BusinessEventRecord;
import dev.erst.fingrind.contract.operations.BusinessEventRejection;
import dev.erst.fingrind.contract.operations.BusinessEventRequest;
import dev.erst.fingrind.contract.operations.CommitBusinessEventResult;
import dev.erst.fingrind.contract.operations.GetBusinessEventResult;
import dev.erst.fingrind.contract.operations.ListBusinessEventsQuery;
import dev.erst.fingrind.contract.operations.ListBusinessEventsResult;
import dev.erst.fingrind.contract.operations.PreflightBusinessEventResult;
import dev.erst.fingrind.contract.operations.RecordBusinessEventCommand;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountingBasis;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.Approval;
import dev.erst.fingrind.core.ApprovalStatus;
import dev.erst.fingrind.core.BusinessEventId;
import dev.erst.fingrind.core.BusinessEventKind;
import dev.erst.fingrind.core.BusinessEventStatus;
import dev.erst.fingrind.core.CashFlowActivity;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.Counterparty;
import dev.erst.fingrind.core.CounterpartyId;
import dev.erst.fingrind.core.CounterpartyKind;
import dev.erst.fingrind.core.CounterpartyName;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.EvidenceBundle;
import dev.erst.fingrind.core.ExchangeRate;
import dev.erst.fingrind.core.ExchangeRateEvidence;
import dev.erst.fingrind.core.ExchangeRateSourceKind;
import dev.erst.fingrind.core.FunctionalMeasurement;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.InventoryItemId;
import dev.erst.fingrind.core.InventoryItemName;
import dev.erst.fingrind.core.InventoryQuantity;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.MigrationBatchId;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.OtherComprehensiveIncomeClassification;
import dev.erst.fingrind.core.PercentageRate;
import dev.erst.fingrind.core.PositiveMoney;
import dev.erst.fingrind.core.PriorPeriodAdjustmentKind;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.core.SourceDocument;
import dev.erst.fingrind.core.SourceDocumentId;
import dev.erst.fingrind.core.SourceDocumentNumber;
import dev.erst.fingrind.core.SourceDocumentType;
import dev.erst.fingrind.core.TaxCode;
import dev.erst.fingrind.core.TaxComponent;
import dev.erst.fingrind.core.TaxJurisdictionCode;
import dev.erst.fingrind.core.TaxPricingMode;
import dev.erst.fingrind.core.TaxRecoverability;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for typed business-event public contract value types. */
class BusinessEventContractTypesTest {
  private static final CurrencyUnit EUR = CurrencyUnit.of("EUR");
  private static final CurrencyUnit USD = CurrencyUnit.of("USD");

  @Test
  void businessEventRequests_preserveCanonicalKindsDatesAndDefensiveCopies() {
    List<TaxComponent> mutableTaxComponents = new ArrayList<>(List.of(taxComponent()));

    BusinessEventRequest.IssueInvoice issueInvoice =
        new BusinessEventRequest.IssueInvoice(
            new BusinessEventId("event-issue-invoice"),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            counterparty(CounterpartyKind.CUSTOMER),
            new AccountCode("1100"),
            new AccountCode("4000"),
            PositiveMoney.parse(EUR, "120.00"),
            Optional.of(new TaxCode("VAT-OUTPUT")),
            mutableTaxComponents,
            Optional.of(functionalMeasurement()),
            evidenceBundle(SourceDocumentType.INVOICE));
    BusinessEventRequest.ReceivePayment receivePayment =
        new BusinessEventRequest.ReceivePayment(
            new BusinessEventId("event-receive-payment"),
            LocalDate.parse("2026-04-10"),
            issueInvoice.businessEventId(),
            counterparty(CounterpartyKind.CUSTOMER),
            new AccountCode("1000"),
            new AccountCode("1100"),
            PositiveMoney.parse(EUR, "120.00"),
            List.of(),
            Optional.empty(),
            evidenceBundle(SourceDocumentType.PAYMENT_RECEIPT));
    BusinessEventRequest.RecordBill recordBill =
        new BusinessEventRequest.RecordBill(
            new BusinessEventId("event-record-bill"),
            LocalDate.parse("2026-04-05"),
            LocalDate.parse("2026-04-20"),
            counterparty(CounterpartyKind.SUPPLIER),
            new AccountCode("2100"),
            new AccountCode("6100"),
            PositiveMoney.parse(EUR, "80.00"),
            Optional.of(new TaxCode("VAT-INPUT")),
            List.of(taxComponent()),
            Optional.of(functionalMeasurement()),
            evidenceBundle(SourceDocumentType.BILL));
    BusinessEventRequest.PayBill payBill =
        new BusinessEventRequest.PayBill(
            new BusinessEventId("event-pay-bill"),
            LocalDate.parse("2026-04-18"),
            recordBill.businessEventId(),
            counterparty(CounterpartyKind.SUPPLIER),
            new AccountCode("1000"),
            new AccountCode("2100"),
            PositiveMoney.parse(EUR, "80.00"),
            List.of(),
            Optional.empty(),
            evidenceBundle(SourceDocumentType.PAYMENT_RECEIPT));
    BusinessEventRequest.RecordSale recordSale =
        new BusinessEventRequest.RecordSale(
            new BusinessEventId("event-record-sale"),
            LocalDate.parse("2026-04-11"),
            counterparty(CounterpartyKind.CUSTOMER),
            new InventoryItemId("inv-1"),
            new InventoryItemName("Coffee Beans"),
            new InventoryQuantity(3L),
            new AccountCode("1000"),
            new AccountCode("4100"),
            new AccountCode("1300"),
            new AccountCode("5100"),
            PositiveMoney.parse(EUR, "90.00"),
            PositiveMoney.parse(EUR, "45.00"),
            Optional.of(new TaxCode("VAT-OUTPUT")),
            List.of(taxComponent()),
            Optional.of(functionalMeasurement()),
            evidenceBundle(SourceDocumentType.INVENTORY_ISSUE));
    BusinessEventRequest.PurchaseInventory purchaseInventory =
        new BusinessEventRequest.PurchaseInventory(
            new BusinessEventId("event-purchase-inventory"),
            LocalDate.parse("2026-04-08"),
            counterparty(CounterpartyKind.SUPPLIER),
            new InventoryItemId("inv-2"),
            new InventoryItemName("Cups"),
            new InventoryQuantity(10L),
            new AccountCode("1000"),
            new AccountCode("1300"),
            PositiveMoney.parse(EUR, "30.00"),
            Optional.of(new TaxCode("VAT-INPUT")),
            List.of(taxComponent()),
            Optional.of(functionalMeasurement()),
            evidenceBundle(SourceDocumentType.INVENTORY_RECEIPT));
    BusinessEventRequest.RecordPayrollRun payrollRun =
        new BusinessEventRequest.RecordPayrollRun(
            new BusinessEventId("event-payroll"),
            LocalDate.parse("2026-04-25"),
            new AccountCode("6200"),
            new AccountCode("2200"),
            new AccountCode("1000"),
            PositiveMoney.parse(EUR, "1000.00"),
            PositiveMoney.parse(EUR, "250.00"),
            PositiveMoney.parse(EUR, "750.00"),
            List.of(taxComponent()),
            evidenceBundle(SourceDocumentType.PAYROLL_REGISTER));
    BusinessEventRequest.RecordOwnerDraw ownerDraw =
        new BusinessEventRequest.RecordOwnerDraw(
            new BusinessEventId("event-owner-draw"),
            LocalDate.parse("2026-04-27"),
            new AccountCode("3300"),
            new AccountCode("1000"),
            PositiveMoney.parse(EUR, "25.00"),
            evidenceBundle(SourceDocumentType.OTHER));
    BusinessEventRequest.RecordBankFee bankFee =
        new BusinessEventRequest.RecordBankFee(
            new BusinessEventId("event-bank-fee"),
            LocalDate.parse("2026-04-28"),
            new AccountCode("6300"),
            new AccountCode("1000"),
            PositiveMoney.parse(EUR, "5.00"),
            evidenceBundle(SourceDocumentType.BANK_STATEMENT));
    BusinessEventRequest.RecordTaxPayment taxPayment =
        new BusinessEventRequest.RecordTaxPayment(
            new BusinessEventId("event-tax-payment"),
            LocalDate.parse("2026-04-29"),
            new AccountCode("2300"),
            new AccountCode("1000"),
            PositiveMoney.parse(EUR, "17.00"),
            evidenceBundle(SourceDocumentType.TAX_NOTICE));
    BusinessEventRequest.OpeningBalanceStatement openingBalanceStatement =
        new BusinessEventRequest.OpeningBalanceStatement(
            new BusinessEventId("event-opening-balance"),
            new MigrationBatchId("migration-1"),
            journalEntry(LocalDate.parse("2026-01-01")),
            evidenceBundle(SourceDocumentType.JOURNAL_MEMO));
    BusinessEventRequest.PriorPeriodAdjustment priorPeriodAdjustment =
        new BusinessEventRequest.PriorPeriodAdjustment(
            new BusinessEventId("event-prior-period-adjustment"),
            new MigrationBatchId("migration-2"),
            PriorPeriodAdjustmentKind.DISCOVERED_ERROR,
            journalEntry(LocalDate.parse("2025-12-31")),
            evidenceBundle(SourceDocumentType.JOURNAL_MEMO));

    mutableTaxComponents.clear();

    assertEquals(BusinessEventKind.ISSUE_INVOICE, issueInvoice.businessEventKind());
    assertEquals(LocalDate.parse("2026-04-01"), issueInvoice.effectiveDate());
    assertEquals(1, issueInvoice.taxComponents().size());
    assertEquals(BusinessEventKind.RECEIVE_PAYMENT, receivePayment.businessEventKind());
    assertEquals(LocalDate.parse("2026-04-10"), receivePayment.effectiveDate());
    assertEquals(BusinessEventKind.RECORD_BILL, recordBill.businessEventKind());
    assertEquals(LocalDate.parse("2026-04-05"), recordBill.effectiveDate());
    assertEquals(BusinessEventKind.PAY_BILL, payBill.businessEventKind());
    assertEquals(LocalDate.parse("2026-04-18"), payBill.effectiveDate());
    assertEquals(BusinessEventKind.RECORD_SALE, recordSale.businessEventKind());
    assertEquals(LocalDate.parse("2026-04-11"), recordSale.effectiveDate());
    assertEquals(BusinessEventKind.PURCHASE_INVENTORY, purchaseInventory.businessEventKind());
    assertEquals(LocalDate.parse("2026-04-08"), purchaseInventory.effectiveDate());
    assertEquals(BusinessEventKind.RECORD_PAYROLL_RUN, payrollRun.businessEventKind());
    assertEquals(LocalDate.parse("2026-04-25"), payrollRun.effectiveDate());
    assertEquals(1, payrollRun.taxComponents().size());
    assertEquals(Optional.empty(), payrollRun.functionalMeasurement());
    assertEquals(BusinessEventKind.RECORD_OWNER_DRAW, ownerDraw.businessEventKind());
    assertEquals(LocalDate.parse("2026-04-27"), ownerDraw.effectiveDate());
    assertEquals(List.of(), ownerDraw.taxComponents());
    assertEquals(Optional.empty(), ownerDraw.functionalMeasurement());
    assertEquals(BusinessEventKind.RECORD_BANK_FEE, bankFee.businessEventKind());
    assertEquals(LocalDate.parse("2026-04-28"), bankFee.effectiveDate());
    assertEquals(List.of(), bankFee.taxComponents());
    assertEquals(Optional.empty(), bankFee.functionalMeasurement());
    assertEquals(BusinessEventKind.RECORD_TAX_PAYMENT, taxPayment.businessEventKind());
    assertEquals(LocalDate.parse("2026-04-29"), taxPayment.effectiveDate());
    assertEquals(List.of(), taxPayment.taxComponents());
    assertEquals(Optional.empty(), taxPayment.functionalMeasurement());
    assertEquals(
        BusinessEventKind.OPENING_BALANCE_STATEMENT, openingBalanceStatement.businessEventKind());
    assertEquals(LocalDate.parse("2026-01-01"), openingBalanceStatement.effectiveDate());
    assertEquals(List.of(), openingBalanceStatement.taxComponents());
    assertEquals(Optional.empty(), openingBalanceStatement.functionalMeasurement());
    assertEquals(
        BusinessEventKind.PRIOR_PERIOD_ADJUSTMENT, priorPeriodAdjustment.businessEventKind());
    assertEquals(LocalDate.parse("2025-12-31"), priorPeriodAdjustment.effectiveDate());
    assertEquals(List.of(), priorPeriodAdjustment.taxComponents());
    assertEquals(Optional.empty(), priorPeriodAdjustment.functionalMeasurement());
  }

  @Test
  void businessEventRequestsRejectInvalidDatesAndResultsPreserveCanonicalState() {
    IllegalArgumentException dueDateBeforeIssueDate =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BusinessEventRequest.IssueInvoice(
                    new BusinessEventId("event-invalid-invoice"),
                    LocalDate.parse("2026-04-30"),
                    LocalDate.parse("2026-04-01"),
                    counterparty(CounterpartyKind.CUSTOMER),
                    new AccountCode("1100"),
                    new AccountCode("4000"),
                    PositiveMoney.parse(EUR, "120.00"),
                    Optional.of(new TaxCode("VAT-OUTPUT")),
                    List.of(),
                    Optional.of(functionalMeasurement()),
                    evidenceBundle(SourceDocumentType.INVOICE)));
    IllegalArgumentException dueDateBeforeBillDate =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BusinessEventRequest.RecordBill(
                    new BusinessEventId("event-invalid-bill"),
                    LocalDate.parse("2026-04-30"),
                    LocalDate.parse("2026-04-01"),
                    counterparty(CounterpartyKind.SUPPLIER),
                    new AccountCode("2100"),
                    new AccountCode("6100"),
                    PositiveMoney.parse(EUR, "80.00"),
                    Optional.of(new TaxCode("VAT-INPUT")),
                    List.of(),
                    Optional.of(functionalMeasurement()),
                    evidenceBundle(SourceDocumentType.BILL)));
    BusinessEventRecord businessEventRecord =
        businessEventRecord(
            new BusinessEventRequest.RecordSale(
                new BusinessEventId("event-recorded-sale"),
                LocalDate.parse("2026-04-11"),
                counterparty(CounterpartyKind.CUSTOMER),
                new InventoryItemId("inv-3"),
                new InventoryItemName("Tea"),
                new InventoryQuantity(2L),
                new AccountCode("1000"),
                new AccountCode("4100"),
                new AccountCode("1300"),
                new AccountCode("5100"),
                PositiveMoney.parse(EUR, "30.00"),
                PositiveMoney.parse(EUR, "12.00"),
                Optional.of(new TaxCode("VAT-OUTPUT")),
                List.of(taxComponent()),
                Optional.of(functionalMeasurement()),
                evidenceBundle(SourceDocumentType.INVENTORY_ISSUE)));
    RecordBusinessEventCommand recordCommand =
        new RecordBusinessEventCommand(
            businessEventRecord.businessEventRequest(),
            requestProvenance("command-business-event"),
            SourceChannel.CLI);
    BusinessEventPageCursor cursor =
        new BusinessEventPageCursor(
            LocalDate.parse("2026-04-11"),
            Instant.parse("2026-04-11T10:15:30Z"),
            new BusinessEventId("event-recorded-sale"));
    BusinessEventPage businessEventPage =
        new BusinessEventPage(List.of(businessEventRecord), 50, Optional.of(cursor));
    ListBusinessEventsQuery listBusinessEventsQuery =
        new ListBusinessEventsQuery(
            Optional.of(BusinessEventKind.RECORD_SALE),
            Optional.of(new CounterpartyId("counterparty-customer")),
            EffectiveDateRange.of(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
            50,
            Optional.of(cursor));
    PreflightBusinessEventResult.Accepted accepted =
        new PreflightBusinessEventResult.Accepted(
            businessEventRecord.businessEventRequest().businessEventId(),
            businessEventRecord.businessEventRequest().businessEventKind(),
            businessEventRecord.businessEventRequest().effectiveDate());
    PreflightBusinessEventResult.Rejected preflightRejected =
        new PreflightBusinessEventResult.Rejected(
            new BusinessEventId("event-preflight-rejected"),
            new BusinessEventRejection.BookNotInitialized());
    CommitBusinessEventResult.Committed committed =
        new CommitBusinessEventResult.Committed(businessEventRecord);
    CommitBusinessEventResult.Rejected commitRejected =
        new CommitBusinessEventResult.Rejected(
            new BusinessEventId("event-commit-rejected"),
            new BusinessEventRejection.UnknownAccount(new AccountCode("9999")));
    GetBusinessEventResult.Found found = new GetBusinessEventResult.Found(businessEventRecord);
    GetBusinessEventResult.Rejected getRejected =
        new GetBusinessEventResult.Rejected(
            new BusinessEventRejection.BusinessEventNotFound(
                new BusinessEventId("event-not-found")));
    ListBusinessEventsResult.Listed listed = new ListBusinessEventsResult.Listed(businessEventPage);
    ListBusinessEventsResult.Rejected listRejected =
        new ListBusinessEventsResult.Rejected(
            new BusinessEventRejection.AccountingBasisUnsupported(
                AccountingBasis.CASH, BusinessEventKind.ISSUE_INVOICE));

    assertEquals(
        "Invoice due date must not be before issue date.", dueDateBeforeIssueDate.getMessage());
    assertEquals("Bill due date must not be before bill date.", dueDateBeforeBillDate.getMessage());
    assertEquals(SourceChannel.CLI, recordCommand.sourceChannel());
    assertEquals(cursor, businessEventPage.nextCursor().orElseThrow());
    assertEquals(50, listBusinessEventsQuery.limit());
    assertEquals(
        BusinessEventKind.RECORD_SALE, listBusinessEventsQuery.businessEventKind().orElseThrow());
    assertEquals(
        businessEventRecord.businessEventRequest().businessEventKind(),
        accepted.businessEventKind());
    assertInstanceOf(
        BusinessEventRejection.BookNotInitialized.class, preflightRejected.rejection());
    assertEquals(businessEventRecord, committed.businessEventRecord());
    assertInstanceOf(BusinessEventRejection.UnknownAccount.class, commitRejected.rejection());
    assertEquals(businessEventRecord, found.businessEventRecord());
    assertInstanceOf(BusinessEventRejection.BusinessEventNotFound.class, getRejected.rejection());
    assertEquals(1, listed.businessEventPage().businessEvents().size());
    assertInstanceOf(
        BusinessEventRejection.AccountingBasisUnsupported.class, listRejected.rejection());
  }

  @Test
  void businessEventSupportingTypesRejectInvalidCombinationsAndLimits() {
    IllegalArgumentException partialCashFlowPair =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BusinessEventRecord(
                    new BusinessEventRequest.RecordOwnerDraw(
                        new BusinessEventId("event-owner-draw"),
                        LocalDate.parse("2026-04-27"),
                        new AccountCode("3300"),
                        new AccountCode("1000"),
                        PositiveMoney.parse(EUR, "25.00"),
                        evidenceBundle(SourceDocumentType.OTHER)),
                    requestProvenance("command-cash-flow-invalid"),
                    SourceChannel.CLI,
                    Instant.parse("2026-04-27T10:15:30Z"),
                    BusinessEventStatus.POSTED,
                    Optional.of(CashFlowActivity.FINANCING),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    List.of()));
    IllegalArgumentException partialOciPair =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BusinessEventRecord(
                    new BusinessEventRequest.RecordOwnerDraw(
                        new BusinessEventId("event-owner-draw-oci"),
                        LocalDate.parse("2026-04-27"),
                        new AccountCode("3300"),
                        new AccountCode("1000"),
                        PositiveMoney.parse(EUR, "25.00"),
                        evidenceBundle(SourceDocumentType.OTHER)),
                    requestProvenance("command-oci-invalid"),
                    SourceChannel.CLI,
                    Instant.parse("2026-04-27T10:15:30Z"),
                    BusinessEventStatus.POSTED,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(OtherComprehensiveIncomeClassification.OTHER),
                    Optional.empty(),
                    List.of()));
    IllegalArgumentException pageLimitFailure =
        assertThrows(
            IllegalArgumentException.class,
            () -> new BusinessEventPage(List.of(), 0, Optional.empty()));
    IllegalArgumentException queryLimitFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ListBusinessEventsQuery(
                    Optional.empty(),
                    Optional.empty(),
                    EffectiveDateRange.unbounded(),
                    0,
                    Optional.empty()));
    BusinessEventRejection.ForeignExchangeUnsupported foreignExchangeUnsupported =
        new BusinessEventRejection.ForeignExchangeUnsupported(USD, EUR);
    BusinessEventRejection.FirstClassEvidenceRequired firstClassEvidenceRequired =
        new BusinessEventRejection.FirstClassEvidenceRequired(BusinessEventKind.ISSUE_INVOICE);
    BusinessEventRejection.TaxProfileRequired taxProfileRequired =
        new BusinessEventRejection.TaxProfileRequired(new TaxCode("VAT-OUTPUT"));
    BusinessEventRejection.UnknownTaxCode unknownTaxCode =
        new BusinessEventRejection.UnknownTaxCode(new TaxCode("VAT-INPUT"));
    BusinessEventRejection.InactiveAccount inactiveAccount =
        new BusinessEventRejection.InactiveAccount(new AccountCode("1999"));
    BusinessEventRejection.InventoryItemMissing inventoryItemMissing =
        new BusinessEventRejection.InventoryItemMissing(new InventoryItemId("inv-missing"));
    BusinessEventRejection.SettlementAmountExceedsOpenAmount settlementExceedsOpenAmount =
        new BusinessEventRejection.SettlementAmountExceedsOpenAmount(
            new BusinessEventId("event-open-amount"));

    assertEquals(
        "cashFlowActivity and cashFlowAmount must either both be present or both be absent.",
        partialCashFlowPair.getMessage());
    assertEquals(
        "otherComprehensiveIncomeClassification and otherComprehensiveIncomeAmount must either both be present or both be absent.",
        partialOciPair.getMessage());
    assertEquals("Business event page limit must be positive.", pageLimitFailure.getMessage());
    assertEquals("Business event page limit must be positive.", queryLimitFailure.getMessage());
    assertEquals(USD, foreignExchangeUnsupported.transactionCurrency());
    assertEquals(BusinessEventKind.ISSUE_INVOICE, firstClassEvidenceRequired.businessEventKind());
    assertEquals("VAT-OUTPUT", taxProfileRequired.taxCode().value());
    assertEquals("VAT-INPUT", unknownTaxCode.taxCode().value());
    assertEquals("1999", inactiveAccount.accountCode().value());
    assertEquals("inv-missing", inventoryItemMissing.inventoryItemId().value());
    assertEquals("event-open-amount", settlementExceedsOpenAmount.businessEventId().value());
  }

  private static BusinessEventRecord businessEventRecord(
      BusinessEventRequest businessEventRequest) {
    return new BusinessEventRecord(
        businessEventRequest,
        requestProvenance("command-record"),
        SourceChannel.CLI,
        Instant.parse("2026-04-11T10:15:30Z"),
        BusinessEventStatus.POSTED,
        Optional.of(CashFlowActivity.OPERATING),
        Optional.of(Money.parse(EUR, "30.00")),
        Optional.of(OtherComprehensiveIncomeClassification.OTHER),
        Optional.of(Money.parse(EUR, "1.50")),
        List.of(new dev.erst.fingrind.core.PostingId("posting-1")));
  }

  private static RequestProvenance requestProvenance(String commandId) {
    return new RequestProvenance(
        new ActorId("actor-1"),
        ActorType.AGENT,
        new CommandId(commandId),
        new IdempotencyKey("idem-" + commandId),
        new CausationId("cause-" + commandId),
        Optional.of(new CorrelationId("corr-" + commandId)));
  }

  private static Counterparty counterparty(CounterpartyKind counterpartyKind) {
    return new Counterparty(
        new CounterpartyId("counterparty-" + counterpartyKind.name().toLowerCase(Locale.ROOT)),
        counterpartyKind,
        new CounterpartyName("Counterparty " + counterpartyKind.name()));
  }

  private static EvidenceBundle evidenceBundle(SourceDocumentType sourceDocumentType) {
    return new EvidenceBundle(
        Optional.of(counterparty(CounterpartyKind.OTHER)),
        List.of(
            new SourceDocument(
                new SourceDocumentId("doc-" + sourceDocumentType.name().toLowerCase(Locale.ROOT)),
                sourceDocumentType,
                LocalDate.parse("2026-04-01"),
                new SourceDocumentNumber("DOC-" + sourceDocumentType.name()),
                Optional.of("Evidence for " + sourceDocumentType.name()))),
        Optional.of(
            new Approval(
                new ActorId("approver-1"),
                ApprovalStatus.APPROVED,
                Optional.of(Instant.parse("2026-04-01T09:30:00Z")),
                Optional.of("Reviewed"))));
  }

  private static FunctionalMeasurement functionalMeasurement() {
    return new FunctionalMeasurement(
        Money.parse(USD, "12.00"),
        Money.parse(EUR, "10.00"),
        new ExchangeRateEvidence(
            USD,
            EUR,
            new ExchangeRate("0.833333"),
            Instant.parse("2026-04-01T08:00:00Z"),
            ExchangeRateSourceKind.ECB,
            Optional.of("ECB-2026-04-01")));
  }

  private static TaxComponent taxComponent() {
    return new TaxComponent(
        new TaxCode("VAT-OUTPUT"),
        new TaxJurisdictionCode("LV"),
        TaxPricingMode.EXCLUSIVE,
        TaxRecoverability.FULLY_RECOVERABLE,
        new PercentageRate(2_100),
        PositiveMoney.parse(EUR, "100.00"),
        PositiveMoney.parse(EUR, "21.00"));
  }

  private static JournalEntry journalEntry(LocalDate effectiveDate) {
    return new JournalEntry(
        effectiveDate,
        List.of(
            new dev.erst.fingrind.core.JournalLine(
                new AccountCode("1000"),
                dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT,
                Money.parse(EUR, "10.00")),
            new dev.erst.fingrind.core.JournalLine(
                new AccountCode("3000"),
                dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
                Money.parse(EUR, "10.00"))));
  }
}
