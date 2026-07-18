package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.workflow.LedgerFact;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Focused coverage for query payloads reconstructed from ledger-fact journals. */
class CliLedgerBookQueryPayloadMapperTest {
  @Test
  void postingPayload_mapsReversalEntryFactsFromLedgerFacts() {
    var payload =
        CliLedgerBookQueryPayloadMapper.postingPayload(
            postingFacts(
                "REVERSAL",
                List.of(
                    LedgerFact.text("entryKind", "REVERSAL"),
                    LedgerFact.group(
                        "reversal",
                        List.of(
                            LedgerFact.text("priorPostingId", "posting-0"),
                            LedgerFact.text("reason", "operator reversal"))))));

    assertNotNull(payload.entry());
    assertNotNull(payload.entry().reversal());
    assertEquals("posting-0", payload.entry().reversal().priorPostingId());
    assertEquals("operator reversal", payload.entry().reversal().reason());
    assertNull(payload.entry().openingBalances());
  }

  @Test
  void postingPayload_mapsOpeningBalancesFromLedgerFacts() {
    var payload =
        CliLedgerBookQueryPayloadMapper.postingPayload(
            postingFacts(
                "OPENING_POSITION",
                List.of(
                    LedgerFact.text("entryKind", "OPENING_POSITION"),
                    LedgerFact.group(
                        "openingBalance",
                        List.of(
                            LedgerFact.text("accountCode", "1000"),
                            LedgerFact.text("side", "DEBIT"),
                            LedgerFact.money("amount", new MonetaryAmount("EUR", "1250")))),
                    LedgerFact.group(
                        "openingBalance",
                        List.of(
                            LedgerFact.text("accountCode", "3000"),
                            LedgerFact.text("side", "CREDIT"),
                            LedgerFact.money("amount", new MonetaryAmount("EUR", "1250")))))));

    assertNotNull(payload.entry());
    assertNull(payload.entry().reversal());
    assertNotNull(payload.entry().openingBalances());
    assertEquals(2, payload.entry().openingBalances().size());
    assertEquals("1000", payload.entry().openingBalances().getFirst().accountCode());
    assertEquals("3000", payload.entry().openingBalances().get(1).accountCode());
  }

  @Test
  void postingPayload_mapsSettlementAdjunctFromLedgerFacts() {
    var payload =
        CliLedgerBookQueryPayloadMapper.postingPayload(
            postingFacts(
                "RECEIPT",
                List.of(
                    LedgerFact.text("entryKind", "RECEIPT"),
                    LedgerFact.text("cashAccountCode", "1000"),
                    LedgerFact.text("receivableAccountCode", "1100"),
                    LedgerFact.money("amount", new MonetaryAmount("EUR", "1250")),
                    LedgerFact.group(
                        "settlementAdjunct",
                        List.of(
                            LedgerFact.text("accountCode", "6100"),
                            LedgerFact.money("amount", new MonetaryAmount("EUR", "50")))))));

    var entry = Objects.requireNonNull(payload.entry());
    var settlementAdjunct = Objects.requireNonNull(entry.settlementAdjunct());

    assertEquals("6100", settlementAdjunct.accountCode());
    assertEquals("50", settlementAdjunct.amount().minorUnits());
  }

  @Test
  void postingPayload_mapsInventoryReliefFromLedgerFacts() {
    var payload =
        CliLedgerBookQueryPayloadMapper.postingPayload(
            postingFacts(
                "SALE_SETTLED",
                List.of(
                    LedgerFact.text("entryKind", "SALE_SETTLED"),
                    LedgerFact.text("cashAccountCode", "1000"),
                    LedgerFact.text("revenueAccountCode", "4000"),
                    LedgerFact.money("amount", new MonetaryAmount("EUR", "1250")),
                    LedgerFact.group(
                        "inventoryRelief",
                        List.of(
                            LedgerFact.text("inventoryAccountCode", "1400"),
                            LedgerFact.text("costOfSalesAccountCode", "5000"),
                            LedgerFact.text("quantity", "4"))))));

    var entry = Objects.requireNonNull(payload.entry());
    var inventoryRelief = Objects.requireNonNull(entry.inventoryRelief());

    assertEquals("1400", inventoryRelief.inventoryAccountCode());
    assertEquals("5000", inventoryRelief.costOfSalesAccountCode());
    assertEquals("4", inventoryRelief.quantity());
  }

  @Test
  void postingPayload_mapsDurableLatvianMonthlyPayrollFactsFromLedgerFacts() {
    var payload =
        CliLedgerBookQueryPayloadMapper.postingPayload(
            postingFacts(
                "LATVIAN_MONTHLY_PAYROLL",
                List.of(
                    LedgerFact.text("entryKind", "LATVIAN_MONTHLY_PAYROLL"),
                    LedgerFact.group(
                        "latvianMonthlyPayroll",
                        List.of(
                            LedgerFact.text("payrollRunId", "payroll-run-2026-07-employee-001"),
                            LedgerFact.text("employeeReference", "employee-001"),
                            LedgerFact.text("payrollMonth", "2026-07"),
                            LedgerFact.flag("taxBookHeldAtEmployer", true),
                            LedgerFact.count("dependantCount", 0),
                            LedgerFact.text("wageExpenseAccountCode", "5000"),
                            LedgerFact.text("employerSocialContributionExpenseAccountCode", "5010"),
                            LedgerFact.text("netWagesPayableAccountCode", "2200"),
                            LedgerFact.text("employeeSocialContributionPayableAccountCode", "2210"),
                            LedgerFact.text("employerSocialContributionPayableAccountCode", "2220"),
                            LedgerFact.text("personalIncomeTaxPayableAccountCode", "2230"),
                            LedgerFact.money("grossWages", new MonetaryAmount("EUR", "200000")),
                            LedgerFact.money(
                                "employeeSocialContribution", new MonetaryAmount("EUR", "21000")),
                            LedgerFact.money(
                                "employerSocialContribution", new MonetaryAmount("EUR", "47180")),
                            LedgerFact.money(
                                "monthlyNonTaxableMinimum", new MonetaryAmount("EUR", "55000")),
                            LedgerFact.money(
                                "personalIncomeTax", new MonetaryAmount("EUR", "31620")),
                            LedgerFact.money("netWages", new MonetaryAmount("EUR", "147380")))))));

    var payroll =
        Objects.requireNonNull(Objects.requireNonNull(payload.entry()).latvianMonthlyPayroll());
    assertEquals("payroll-run-2026-07-employee-001", payroll.payrollRunId());
    assertEquals("employee-001", payroll.employeeReference());
    assertTrue(payroll.taxBookHeldAtEmployer());
    assertEquals(0, payroll.dependantCount());
    assertEquals("200000", payroll.grossWages().minorUnits());
    assertEquals(
        "147380", Objects.requireNonNull(payroll.resolvedCalculation()).netWages().minorUnits());
  }

  @Test
  void postingPayload_mapsDurableLatvianPayrollSettlementFactsFromLedgerFacts() {
    var payload =
        CliLedgerBookQueryPayloadMapper.postingPayload(
            postingFacts(
                "LATVIAN_PAYROLL_STATE_REMITTANCE",
                List.of(
                    LedgerFact.text("entryKind", "LATVIAN_PAYROLL_STATE_REMITTANCE"),
                    LedgerFact.group(
                        "latvianPayrollSettlement",
                        List.of(
                            LedgerFact.text("settlementKind", "STATE_REMITTANCE"),
                            LedgerFact.text("payrollRunId", "payroll-run-2026-07-employee-001"),
                            LedgerFact.text("cashAccountCode", "1000"),
                            LedgerFact.text("netWagesPayableAccountCode", "2200"),
                            LedgerFact.text("employeeSocialContributionPayableAccountCode", "2210"),
                            LedgerFact.text("employerSocialContributionPayableAccountCode", "2220"),
                            LedgerFact.text("personalIncomeTaxPayableAccountCode", "2230"),
                            LedgerFact.money("netWages", new MonetaryAmount("EUR", "147380")),
                            LedgerFact.money(
                                "employeeSocialContribution", new MonetaryAmount("EUR", "21000")),
                            LedgerFact.money(
                                "employerSocialContribution", new MonetaryAmount("EUR", "47180")),
                            LedgerFact.money(
                                "personalIncomeTax", new MonetaryAmount("EUR", "31620")))))));

    var settlement =
        Objects.requireNonNull(Objects.requireNonNull(payload.entry()).latvianPayrollSettlement());
    assertEquals("STATE_REMITTANCE", settlement.settlementKind());
    assertEquals("payroll-run-2026-07-employee-001", settlement.payrollRunId());
    assertEquals("1000", settlement.cashAccountCode());
    assertEquals(
        "47180",
        Objects.requireNonNull(settlement.resolvedSettlement())
            .employerSocialContribution()
            .minorUnits());
  }

  @Test
  void accountPayload_mapsInventoryUnitOfMeasureFacts() {
    var payload =
        CliLedgerBookQueryPayloadMapper.accountPayload(
            List.of(
                LedgerFact.text("accountCode", "1400"),
                LedgerFact.text("accountName", "Inventory"),
                LedgerFact.text("accountType", "ASSET"),
                LedgerFact.text("accountNodeKind", "POSTABLE"),
                LedgerFact.text("financialPositionLineClassification", "INVENTORY"),
                LedgerFact.text("cashFlowAssetClassification", "NON_CASH"),
                LedgerFact.group(
                    "unitOfMeasure",
                    List.of(LedgerFact.text("token", "kg"), LedgerFact.count("quantityScale", 3))),
                LedgerFact.text("normalBalance", "DEBIT"),
                LedgerFact.flag("active", true),
                LedgerFact.text("declaredAt", "2026-04-23T10:15:30Z")));

    assertNotNull(payload.unitOfMeasure());
    assertEquals("kg", payload.unitOfMeasure().token());
    assertEquals(3, payload.unitOfMeasure().quantityScale());
  }

  private static List<LedgerFact> postingFacts(
      String postingOriginKind, List<LedgerFact> entryFacts) {
    List<LedgerFact> facts = new ArrayList<>();
    facts.add(LedgerFact.text("postingId", "posting-1"));
    facts.add(LedgerFact.text("postingKind", "STANDARD"));
    facts.add(LedgerFact.text("postingOriginKind", postingOriginKind));
    facts.add(LedgerFact.text("reversalState", "direct"));
    facts.add(LedgerFact.text("effectiveDate", "2026-04-07"));
    facts.add(LedgerFact.text("recordedAt", "2026-04-07T10:15:30Z"));
    facts.add(
        LedgerFact.group(
            "provenance",
            List.of(
                LedgerFact.text("actorId", "actor-1"),
                LedgerFact.text("actorType", "AGENT"),
                LedgerFact.text("commandId", "command-1"),
                LedgerFact.text("idempotencyKey", "idem-1"),
                LedgerFact.text("causationId", "cause-1"),
                LedgerFact.text("correlationId", "corr-1"),
                LedgerFact.text("sourceChannel", "CLI"))));
    facts.add(LedgerFact.group("evidence", evidenceFacts()));
    facts.add(LedgerFact.group("entry", entryFacts));
    facts.add(
        LedgerFact.group(
            "line",
            List.of(
                LedgerFact.text("accountCode", "1000"),
                LedgerFact.text("side", "DEBIT"),
                LedgerFact.money("amount", new MonetaryAmount("EUR", "1250")))));
    facts.add(
        LedgerFact.group(
            "line",
            List.of(
                LedgerFact.text("accountCode", "2000"),
                LedgerFact.text("side", "CREDIT"),
                LedgerFact.money("amount", new MonetaryAmount("EUR", "1250")))));
    return List.copyOf(facts);
  }

  private static List<LedgerFact> evidenceFacts() {
    return List.of(
        LedgerFact.group(
            "sourceDocument",
            List.of(
                LedgerFact.text("sourceDocumentId", "document-1"),
                LedgerFact.text("sourceDocumentType", "cash-receipt"),
                LedgerFact.text("documentDate", "2026-04-07"))),
        LedgerFact.group(
            "approval",
            List.of(
                LedgerFact.text("approvalId", "approval-1"),
                LedgerFact.text("approvalType", "manager-signoff"),
                LedgerFact.text("approverId", "approver-1"),
                LedgerFact.text("approverType", "PERSON"),
                LedgerFact.text("decision", "APPROVED"),
                LedgerFact.text("approvedAt", "2026-04-07T10:20:30Z"))));
  }
}
