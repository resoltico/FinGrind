package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import dev.erst.fingrind.contract.protocol.RequestSurfaceFacts;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Variant-specific schema builders for the post-entry request family. */
final class MachineContractPostEntryVariantSchemaBuilders {
  private static final List<BookkeepingEntryKind> SCHEMA_ORDER =
      List.of(BookkeepingEntryKind.values());
  private static final Map<BookkeepingEntryKind, Supplier<Map<String, Object>>> SCHEMAS =
      schemaBuilders();

  private MachineContractPostEntryVariantSchemaBuilders() {}

  private static Map<BookkeepingEntryKind, Supplier<Map<String, Object>>> schemaBuilders() {
    var schemas =
        new EnumMap<BookkeepingEntryKind, Supplier<Map<String, Object>>>(
            BookkeepingEntryKind.class);
    schemas.putAll(baseSchemas());
    schemas.putAll(standardSchemas());
    schemas.putAll(inventorySchemas());
    schemas.putAll(accrualCutoffSchemas());
    schemas.putAll(fixedAssetSchemas());
    schemas.putAll(financingSchemas());
    schemas.putAll(realizedForeignExchangeSchemas());
    schemas.putAll(latvianPayrollSchemas());
    return Map.copyOf(schemas);
  }

  private static Map<BookkeepingEntryKind, Supplier<Map<String, Object>>> baseSchemas() {
    return Map.of(
        BookkeepingEntryKind.DIRECT_JOURNAL,
        MachineContractPostEntryVariantSchemaBuilders::journalDirectSchema,
        BookkeepingEntryKind.OPENING_POSITION,
        MachineContractPostEntryVariantSchemaBuilders::openingPositionSchema,
        BookkeepingEntryKind.REVERSAL,
        MachineContractPostEntryVariantSchemaBuilders::reversalSchema);
  }

  private static Map<BookkeepingEntryKind, Supplier<Map<String, Object>>> standardSchemas() {
    return Map.ofEntries(
        Map.entry(
            BookkeepingEntryKind.SALE_SETTLED,
            MachineContractPostEntryTypedVariantSchemaBuilders::saleSettledSchema),
        Map.entry(
            BookkeepingEntryKind.SALE_ON_CREDIT,
            MachineContractPostEntryTypedVariantSchemaBuilders::saleOnCreditSchema),
        Map.entry(
            BookkeepingEntryKind.EXPENSE_SETTLED,
            MachineContractPostEntryTypedVariantSchemaBuilders::expenseSettledSchema),
        Map.entry(
            BookkeepingEntryKind.EXPENSE_ON_CREDIT,
            MachineContractPostEntryTypedVariantSchemaBuilders::expenseOnCreditSchema),
        Map.entry(
            BookkeepingEntryKind.RECEIPT,
            MachineContractPostEntryTypedVariantSchemaBuilders::receiptSchema),
        Map.entry(
            BookkeepingEntryKind.PAYMENT,
            MachineContractPostEntryTypedVariantSchemaBuilders::paymentSchema),
        Map.entry(
            BookkeepingEntryKind.OWNER_CONTRIBUTION,
            MachineContractPostEntryTypedVariantSchemaBuilders::ownerContributionSchema),
        Map.entry(
            BookkeepingEntryKind.OWNER_WITHDRAWAL,
            MachineContractPostEntryTypedVariantSchemaBuilders::ownerWithdrawalSchema));
  }

  private static Map<BookkeepingEntryKind, Supplier<Map<String, Object>>> inventorySchemas() {
    return Map.ofEntries(
        Map.entry(
            BookkeepingEntryKind.PURCHASE_SETTLED,
            MachineContractInventoryPostEntryVariantSchemaBuilders::purchaseSettledSchema),
        Map.entry(
            BookkeepingEntryKind.PURCHASE_ON_CREDIT,
            MachineContractInventoryPostEntryVariantSchemaBuilders::purchaseOnCreditSchema),
        Map.entry(
            BookkeepingEntryKind.INVENTORY_CAPITALIZATION_SETTLED,
            MachineContractInventoryPostEntryVariantSchemaBuilders
                ::inventoryCapitalizationSettledSchema),
        Map.entry(
            BookkeepingEntryKind.INVENTORY_CAPITALIZATION_ON_CREDIT,
            MachineContractInventoryPostEntryVariantSchemaBuilders
                ::inventoryCapitalizationOnCreditSchema),
        Map.entry(
            BookkeepingEntryKind.INVENTORY_WRITE_DOWN,
            MachineContractInventoryPostEntryVariantSchemaBuilders::inventoryWriteDownSchema),
        Map.entry(
            BookkeepingEntryKind.INVENTORY_SHRINKAGE,
            MachineContractInventoryPostEntryVariantSchemaBuilders::inventoryShrinkageSchema),
        Map.entry(
            BookkeepingEntryKind.INVENTORY_COUNT_INCREASE,
            MachineContractInventoryPostEntryVariantSchemaBuilders::inventoryCountIncreaseSchema));
  }

  private static Map<BookkeepingEntryKind, Supplier<Map<String, Object>>> accrualCutoffSchemas() {
    return Map.of(
        BookkeepingEntryKind.PREPAYMENT,
        MachineContractAccrualCutoffPostEntryVariantSchemaBuilders::prepaymentSchema,
        BookkeepingEntryKind.DEFERRED_REVENUE,
        MachineContractAccrualCutoffPostEntryVariantSchemaBuilders::deferredRevenueSchema,
        BookkeepingEntryKind.ACCRUED_EXPENSE,
        MachineContractAccrualCutoffPostEntryVariantSchemaBuilders::accruedExpenseSchema,
        BookkeepingEntryKind.ACCRUAL_CUTOFF_RECOGNITION,
        MachineContractAccrualCutoffPostEntryVariantSchemaBuilders::recognitionSchema,
        BookkeepingEntryKind.ACCRUED_EXPENSE_SETTLEMENT,
        MachineContractAccrualCutoffPostEntryVariantSchemaBuilders::settlementSchema);
  }

  private static Map<BookkeepingEntryKind, Supplier<Map<String, Object>>> fixedAssetSchemas() {
    return Map.of(
        BookkeepingEntryKind.FIXED_ASSET_CAPITALIZATION,
        MachineContractFixedAssetPostEntryVariantSchemaBuilders::capitalizationSchema,
        BookkeepingEntryKind.FIXED_ASSET_DEPRECIATION,
        MachineContractFixedAssetPostEntryVariantSchemaBuilders::depreciationSchema,
        BookkeepingEntryKind.FIXED_ASSET_DISPOSAL,
        MachineContractFixedAssetPostEntryVariantSchemaBuilders::disposalSchema);
  }

  private static Map<BookkeepingEntryKind, Supplier<Map<String, Object>>> financingSchemas() {
    return Map.of(
        BookkeepingEntryKind.FINANCING_BORROWING,
        MachineContractFinancingPostEntryVariantSchemaBuilders::borrowingSchema,
        BookkeepingEntryKind.FINANCING_PRINCIPAL_REPAYMENT,
        MachineContractFinancingPostEntryVariantSchemaBuilders::principalRepaymentSchema,
        BookkeepingEntryKind.FINANCING_INTEREST_ACCRUAL,
        MachineContractFinancingPostEntryVariantSchemaBuilders::interestAccrualSchema,
        BookkeepingEntryKind.FINANCING_INTEREST_PAYMENT,
        MachineContractFinancingPostEntryVariantSchemaBuilders::interestPaymentSchema);
  }

  private static Map<BookkeepingEntryKind, Supplier<Map<String, Object>>>
      realizedForeignExchangeSchemas() {
    return Map.of(
        BookkeepingEntryKind.FOREIGN_CURRENCY_OBLIGATION,
        MachineContractRealizedForeignExchangePostEntryVariantSchemaBuilders
            ::foreignCurrencyObligationSchema,
        BookkeepingEntryKind.REALIZED_FOREIGN_EXCHANGE_SETTLEMENT,
        MachineContractRealizedForeignExchangePostEntryVariantSchemaBuilders::settlementSchema);
  }

  private static Map<BookkeepingEntryKind, Supplier<Map<String, Object>>> latvianPayrollSchemas() {
    return Map.of(
        BookkeepingEntryKind.LATVIAN_MONTHLY_PAYROLL,
        MachineContractLatvianPayrollPostEntryVariantSchemaBuilders::monthlyPayrollSchema,
        BookkeepingEntryKind.LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT,
        MachineContractLatvianPayrollPostEntryVariantSchemaBuilders::netWageSettlementSchema,
        BookkeepingEntryKind.LATVIAN_PAYROLL_STATE_REMITTANCE,
        MachineContractLatvianPayrollPostEntryVariantSchemaBuilders::stateRemittanceSchema);
  }

  static Map<String, Object> postEntrySchema() {
    return MachineContractSchemaSupport.rootOneOfSchema(
        "Canonical bookkeeping entry request JSON document.",
        SCHEMA_ORDER.stream().map(MachineContractPostEntryVariantSchemaBuilders::schema).toList());
  }

  static Map<String, Object> schema(BookkeepingEntryKind entryKind) {
    return Objects.requireNonNull(SCHEMAS.get(entryKind), "entryKind").get();
  }

  private static Map<String, Object> journalDirectSchema() {
    RequestSurfaceFacts.BookkeepingEntryKindFacts entryKindFacts =
        entryKindFacts(BookkeepingEntryKind.DIRECT_JOURNAL);
    return MachineContractSchemaSupport.objectSchema(
        "Direct balanced non-inventory operational journal written without a higher-level business entry.",
        List.of(
            MachineContractPostEntryComponentSchemas.requiredEntryKindField(
                BookkeepingEntryKind.DIRECT_JOURNAL,
                "This request records a direct balanced journal."),
            MachineContractPostEntryRequiredFieldSpecs.requiredEffectiveDateField(),
            MachineContractFieldSpec.required(
                ProtocolPostEntryFields.TopLevel.LINES,
                "Balanced non-empty array of non-inventory journal lines for a direct journal.",
                MachineContractSchemaSupport.arraySchema(
                    "Balanced non-empty array of non-inventory journal lines for a direct journal.",
                    MachineContractPostEntryVariantSchemas.lineSchema(),
                    2)),
            MachineContractFieldSpec.optional(
                ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE,
                "Optional owned foreign-exchange facts for a transaction-currency event translated into book functional currency.",
                MachineContractPostEntryComponentSchemas.foreignExchangeSchema()),
            MachineContractPostEntryRequiredFieldSpecs.requiredEvidenceField(entryKindFacts),
            MachineContractPostEntryRequiredFieldSpecs.requiredProvenanceField()));
  }

  private static Map<String, Object> openingPositionSchema() {
    RequestSurfaceFacts.BookkeepingEntryKindFacts entryKindFacts =
        entryKindFacts(BookkeepingEntryKind.OPENING_POSITION);
    return MachineContractSchemaSupport.objectSchema(
        "Explicit opening-position entry used to seed a book before operating activity begins, with exact quantity required for every inventory balance.",
        List.of(
            MachineContractPostEntryComponentSchemas.requiredEntryKindField(
                BookkeepingEntryKind.OPENING_POSITION,
                "This request records an opening-position entry."),
            MachineContractPostEntryRequiredFieldSpecs.requiredEffectiveDateField(),
            MachineContractFieldSpec.required(
                ProtocolPostEntryFields.TopLevel.OPENING_BALANCES,
                "Balanced non-empty array of opening balances. Inventory balances include exact quantity alongside carrying cost; non-inventory balances omit quantity.",
                MachineContractSchemaSupport.arraySchema(
                    "Balanced non-empty array of opening balances. Inventory balances include exact quantity alongside carrying cost; non-inventory balances omit quantity.",
                    MachineContractPostEntryVariantSchemas.openingBalanceSchema(),
                    2)),
            MachineContractPostEntryRequiredFieldSpecs.requiredEvidenceField(entryKindFacts),
            MachineContractPostEntryRequiredFieldSpecs.requiredProvenanceField()));
  }

  private static Map<String, Object> reversalSchema() {
    RequestSurfaceFacts.BookkeepingEntryKindFacts entryKindFacts =
        entryKindFacts(BookkeepingEntryKind.REVERSAL);
    return MachineContractSchemaSupport.objectSchema(
        "Explicit reversal entry that derives a full negation from a previously committed posting.",
        List.of(
            MachineContractPostEntryComponentSchemas.requiredEntryKindField(
                BookkeepingEntryKind.REVERSAL, "This request records a reversal entry."),
            MachineContractPostEntryRequiredFieldSpecs.requiredEffectiveDateField(),
            MachineContractFieldSpec.optional(
                ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE,
                "Optional owned foreign-exchange facts for a transaction-currency event translated into book functional currency.",
                MachineContractPostEntryComponentSchemas.foreignExchangeSchema()),
            MachineContractPostEntryRequiredFieldSpecs.requiredEvidenceField(entryKindFacts),
            MachineContractPostEntryRequiredFieldSpecs.requiredProvenanceField(),
            MachineContractFieldSpec.required(
                ProtocolPostEntryFields.TopLevel.REVERSAL,
                "Required reversal target descriptor for a reversal entry.",
                MachineContractPostEntryVariantSchemas.reversalTargetSchema())));
  }

  private static RequestSurfaceFacts.BookkeepingEntryKindFacts entryKindFacts(
      BookkeepingEntryKind entryKind) {
    return ProtocolCatalog.domain().requestSurface().bookkeepingEntryKind(entryKind);
  }
}
