package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.erst.fingrind.cli.json.CliMutationJsonModels;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.ResolvedJournal;
import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.fx.ForeignExchangeTreatmentKind;
import dev.erst.fingrind.contract.fx.QuotedExchangeRate;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxCode;
import dev.erst.fingrind.contract.tax.TaxCodeName;
import dev.erst.fingrind.contract.tax.TaxInclusionMode;
import dev.erst.fingrind.contract.tax.TaxRate;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AnchorEntry;
import dev.erst.fingrind.core.ClassificationResult;
import dev.erst.fingrind.core.EconomicEventClass;
import dev.erst.fingrind.core.EvidenceClass;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.StructuralContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Focused coverage for resolved-journal payload mapping branches and ordering. */
class CliResolvedJournalPayloadMapperTest {
  @Test
  void resolvedJournalPayload_preservesNullOptionalArtifactsAndSortsClassifierFacts() {
    CliMutationJsonModels.ResolvedJournalPayload payload =
        CliResolvedJournalPayloadMapper.resolvedJournalPayload(
            new ResolvedJournal(
                journalEntry(),
                null,
                null,
                new ClassificationResult(
                    EconomicEventClass.CREDIT_SALE,
                    Set.of(
                        new AnchorEntry(AccountRole.REVENUE, JournalLine.EntrySide.CREDIT),
                        new AnchorEntry(AccountRole.RECEIVABLE, JournalLine.EntrySide.DEBIT)),
                    Set.of(EconomicEventClass.SETTLED_SALE, EconomicEventClass.CREDIT_SALE),
                    false,
                    EvidenceClass.INVOICE,
                    new StructuralContext(Optional.of(new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69")), false))));

    assertNull(payload.appliedTax());
    assertNull(payload.foreignExchangeDetails());
    assertEquals("CREDIT_SALE", payload.classification().eventClass());
    assertEquals(List.of("RECEIVABLE", "REVENUE"), anchorRoles(payload));
    assertEquals(
        List.of("CREDIT_SALE", "SETTLED_SALE"), payload.classification().containedTypedEvents());
    assertFalse(payload.classification().hasCashLine());
    assertEquals("INVOICE", payload.classification().evidenceClass());
    assertEquals("posting-1", payload.classification().structural().reversesPriorPostingId());
  }

  @Test
  void resolvedJournalPayload_mapsPresentAppliedTaxAndForeignExchangeArtifacts() {
    CliMutationJsonModels.ResolvedJournalPayload payload =
        CliResolvedJournalPayloadMapper.resolvedJournalPayload(
            new ResolvedJournal(
                journalEntry(),
                appliedTax(),
                foreignExchangeDetails(),
                new ClassificationResult(
                    EconomicEventClass.SETTLED_SALE,
                    Set.of(new AnchorEntry(AccountRole.CASH, JournalLine.EntrySide.DEBIT)),
                    Set.of(EconomicEventClass.SETTLED_SALE),
                    true,
                    EvidenceClass.CASH_SETTLEMENT,
                    StructuralContext.ordinary())));

    assertNotNull(payload.appliedTax());
    assertEquals("vat-standard-sale", payload.appliedTax().taxCode());
    assertNotNull(payload.foreignExchangeDetails());
    assertEquals("USD", payload.foreignExchangeDetails().transactionAmount().currencyCode());
    assertEquals("SPOT_TRANSACTION", payload.foreignExchangeDetails().treatmentKind());
  }

  @Test
  void resolvedJournalPayload_ordersEqualAnchorRolesByEntrySide() {
    CliMutationJsonModels.ResolvedJournalPayload payload =
        CliResolvedJournalPayloadMapper.resolvedJournalPayload(
            new ResolvedJournal(
                journalEntry(),
                null,
                null,
                new ClassificationResult(
                    EconomicEventClass.ADJUSTMENT,
                    Set.of(
                        new AnchorEntry(AccountRole.CASH, JournalLine.EntrySide.DEBIT),
                        new AnchorEntry(AccountRole.CASH, JournalLine.EntrySide.CREDIT)),
                    Set.of(EconomicEventClass.ADJUSTMENT),
                    true,
                    EvidenceClass.OTHER,
                    StructuralContext.ordinary())));

    assertEquals(
        List.of("CASH:CREDIT", "CASH:DEBIT"),
        payload.classification().anchorSignature().stream()
            .map(entry -> entry.accountRole() + ":" + entry.side())
            .toList());
  }

  private static List<String> anchorRoles(CliMutationJsonModels.ResolvedJournalPayload payload) {
    return payload.classification().anchorSignature().stream()
        .map(CliMutationJsonModels.AnchorEntryPayload::accountRole)
        .toList();
  }

  private static JournalEntry journalEntry() {
    return new JournalEntry(
        LocalDate.parse("2026-04-07"),
        List.of(
            new JournalLine(new AccountCode("1100"), JournalLine.EntrySide.DEBIT, money("12.10")),
            new JournalLine(
                new AccountCode("4000"), JournalLine.EntrySide.CREDIT, money("12.10"))));
  }

  private static AppliedTax appliedTax() {
    return new AppliedTax(
        new TaxRegistrationId("vat-lv"),
        new TaxCode("vat-standard-sale"),
        new TaxCodeName("VAT Standard Sale"),
        new TaxRate(210_000),
        TaxInclusionMode.EXCLUSIVE,
        TaxApplicationKind.OUTPUT_SALE,
        new MonetaryAmount("EUR", "1000"),
        new MonetaryAmount("EUR", "210"),
        new MonetaryAmount("EUR", "1210"),
        new AccountCode("2100"));
  }

  private static ForeignExchangeDetails foreignExchangeDetails() {
    return new ForeignExchangeDetails(
        new MonetaryAmount("USD", "10000"),
        new MonetaryAmount("EUR", "9200"),
        new QuotedExchangeRate(
            new MonetaryAmount("USD", "10000"),
            new MonetaryAmount("EUR", "9200"),
            LocalDate.parse("2026-04-06"),
            "ecb-spot"),
        ForeignExchangeTreatmentKind.SPOT_TRANSACTION);
  }

  private static Money money(String canonicalDecimal) {
    return Money.parse("EUR", canonicalDecimal);
  }
}
