package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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
                            LedgerFact.money("amount", new MonetaryAmount("EUR", "400")))))));

    var entry = Objects.requireNonNull(payload.entry());
    var inventoryRelief = Objects.requireNonNull(entry.inventoryRelief());

    assertEquals("1400", inventoryRelief.inventoryAccountCode());
    assertEquals("5000", inventoryRelief.costOfSalesAccountCode());
    assertEquals("400", inventoryRelief.amount().minorUnits());
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
