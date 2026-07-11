package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import dev.erst.fingrind.contract.protocol.RequestSurfaceFacts;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Variant-specific schema builders for the post-entry request family. */
final class MachineContractPostEntryVariantSchemaBuilders {
  private static final List<BookkeepingEntryKind> SCHEMA_ORDER =
      List.of(
          BookkeepingEntryKind.DIRECT_JOURNAL,
          BookkeepingEntryKind.SALE_SETTLED,
          BookkeepingEntryKind.SALE_ON_CREDIT,
          BookkeepingEntryKind.PURCHASE_SETTLED,
          BookkeepingEntryKind.PURCHASE_ON_CREDIT,
          BookkeepingEntryKind.INVENTORY_CAPITALIZATION_SETTLED,
          BookkeepingEntryKind.INVENTORY_CAPITALIZATION_ON_CREDIT,
          BookkeepingEntryKind.INVENTORY_WRITE_DOWN,
          BookkeepingEntryKind.INVENTORY_SHRINKAGE,
          BookkeepingEntryKind.INVENTORY_COUNT_INCREASE,
          BookkeepingEntryKind.EXPENSE_SETTLED,
          BookkeepingEntryKind.EXPENSE_ON_CREDIT,
          BookkeepingEntryKind.RECEIPT,
          BookkeepingEntryKind.PAYMENT,
          BookkeepingEntryKind.OWNER_CONTRIBUTION,
          BookkeepingEntryKind.OWNER_WITHDRAWAL,
          BookkeepingEntryKind.OPENING_POSITION,
          BookkeepingEntryKind.REVERSAL);
  private static final Map<BookkeepingEntryKind, Supplier<Map<String, Object>>> SCHEMAS =
      schemaBuilders();

  private MachineContractPostEntryVariantSchemaBuilders() {}

  private static Map<BookkeepingEntryKind, Supplier<Map<String, Object>>> schemaBuilders() {
    var schemas =
        new java.util.EnumMap<BookkeepingEntryKind, Supplier<Map<String, Object>>>(
            BookkeepingEntryKind.class);
    schemas.put(
        BookkeepingEntryKind.DIRECT_JOURNAL,
        MachineContractPostEntryVariantSchemaBuilders::journalDirectSchema);
    schemas.put(
        BookkeepingEntryKind.SALE_SETTLED,
        MachineContractPostEntryTypedVariantSchemaBuilders::saleSettledSchema);
    schemas.put(
        BookkeepingEntryKind.SALE_ON_CREDIT,
        MachineContractPostEntryTypedVariantSchemaBuilders::saleOnCreditSchema);
    schemas.put(
        BookkeepingEntryKind.PURCHASE_SETTLED,
        MachineContractInventoryPostEntryVariantSchemaBuilders::purchaseSettledSchema);
    schemas.put(
        BookkeepingEntryKind.PURCHASE_ON_CREDIT,
        MachineContractInventoryPostEntryVariantSchemaBuilders::purchaseOnCreditSchema);
    schemas.put(
        BookkeepingEntryKind.INVENTORY_CAPITALIZATION_SETTLED,
        MachineContractInventoryPostEntryVariantSchemaBuilders
            ::inventoryCapitalizationSettledSchema);
    schemas.put(
        BookkeepingEntryKind.INVENTORY_CAPITALIZATION_ON_CREDIT,
        MachineContractInventoryPostEntryVariantSchemaBuilders
            ::inventoryCapitalizationOnCreditSchema);
    schemas.put(
        BookkeepingEntryKind.INVENTORY_WRITE_DOWN,
        MachineContractInventoryPostEntryVariantSchemaBuilders::inventoryWriteDownSchema);
    schemas.put(
        BookkeepingEntryKind.INVENTORY_SHRINKAGE,
        MachineContractInventoryPostEntryVariantSchemaBuilders::inventoryShrinkageSchema);
    schemas.put(
        BookkeepingEntryKind.INVENTORY_COUNT_INCREASE,
        MachineContractInventoryPostEntryVariantSchemaBuilders::inventoryCountIncreaseSchema);
    schemas.put(
        BookkeepingEntryKind.EXPENSE_SETTLED,
        MachineContractPostEntryTypedVariantSchemaBuilders::expenseSettledSchema);
    schemas.put(
        BookkeepingEntryKind.EXPENSE_ON_CREDIT,
        MachineContractPostEntryTypedVariantSchemaBuilders::expenseOnCreditSchema);
    schemas.put(
        BookkeepingEntryKind.RECEIPT,
        MachineContractPostEntryTypedVariantSchemaBuilders::receiptSchema);
    schemas.put(
        BookkeepingEntryKind.PAYMENT,
        MachineContractPostEntryTypedVariantSchemaBuilders::paymentSchema);
    schemas.put(
        BookkeepingEntryKind.OWNER_CONTRIBUTION,
        MachineContractPostEntryTypedVariantSchemaBuilders::ownerContributionSchema);
    schemas.put(
        BookkeepingEntryKind.OWNER_WITHDRAWAL,
        MachineContractPostEntryTypedVariantSchemaBuilders::ownerWithdrawalSchema);
    schemas.put(
        BookkeepingEntryKind.OPENING_POSITION,
        MachineContractPostEntryVariantSchemaBuilders::openingPositionSchema);
    schemas.put(
        BookkeepingEntryKind.REVERSAL,
        MachineContractPostEntryVariantSchemaBuilders::reversalSchema);
    return Map.copyOf(schemas);
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
