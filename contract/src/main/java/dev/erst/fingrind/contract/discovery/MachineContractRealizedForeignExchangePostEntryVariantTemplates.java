package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.discovery.ContractRealizedForeignExchangeTemplates.RealizedForeignExchangeTemplateDescriptor;
import dev.erst.fingrind.contract.fx.ForeignExchangeTreatmentKind;
import dev.erst.fingrind.core.BookTemplateId;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import org.jspecify.annotations.Nullable;

/** Request scaffolds owned by the Realized Foreign Exchange context. */
final class MachineContractRealizedForeignExchangePostEntryVariantTemplates {
  private static final String SAMPLE_EFFECTIVE_DATE = "2026-01-15";
  private static final String SAMPLE_OBLIGATION_ID = "customer-invoice-usd-001";

  private MachineContractRealizedForeignExchangePostEntryVariantTemplates() {}

  static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor
      foreignCurrencyObligationTemplate(@Nullable BookTemplateId bookTemplateId) {
    return template(
        BookkeepingEntryKind.FOREIGN_CURRENCY_OBLIGATION,
        null,
        "accounts-receivable",
        MachineContractPostEntryVariantTemplates.salesRevenueAccountCode(bookTemplateId),
        new RealizedForeignExchangeTemplateDescriptor(
            SAMPLE_OBLIGATION_ID, "realized-fx-gain", "realized-fx-loss"),
        foreignExchange("120000", "110000"));
  }

  static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor settlementTemplate(
      @Nullable BookTemplateId bookTemplateId) {
    return template(
        BookkeepingEntryKind.REALIZED_FOREIGN_EXCHANGE_SETTLEMENT,
        "cash",
        null,
        null,
        new RealizedForeignExchangeTemplateDescriptor(SAMPLE_OBLIGATION_ID, null, null),
        foreignExchange("120000", "115000"));
  }

  private static ContractPostingRequestTemplates.PostingRequestTemplateDescriptor template(
      BookkeepingEntryKind entryKind,
      @Nullable String cashAccountCode,
      @Nullable String receivableAccountCode,
      @Nullable String revenueAccountCode,
      RealizedForeignExchangeTemplateDescriptor realizedForeignExchange,
      ForeignExchangeTemplateDescriptor foreignExchange) {
    return new ContractPostingRequestTemplates.PostingRequestTemplateDescriptor(
        entryKind,
        SAMPLE_EFFECTIVE_DATE,
        cashAccountCode,
        receivableAccountCode,
        null,
        revenueAccountCode,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        foreignExchange,
        null,
        null,
        null,
        MachineContractPostEntryVariantTemplates.evidenceTemplate(entryKind),
        MachineContractPostEntryVariantTemplates.provenanceTemplate(),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        realizedForeignExchange);
  }

  private static ForeignExchangeTemplateDescriptor foreignExchange(
      String transactionAmount, String functionalAmount) {
    MonetaryAmount transaction = new MonetaryAmount("USD", transactionAmount);
    MonetaryAmount functional = new MonetaryAmount("EUR", functionalAmount);
    return new ForeignExchangeTemplateDescriptor(
        transaction,
        functional,
        new QuotedExchangeRateTemplateDescriptor(
            transaction, functional, SAMPLE_EFFECTIVE_DATE, "central-bank-reference-rate"),
        ForeignExchangeTreatmentKind.SPOT_TRANSACTION);
  }
}
