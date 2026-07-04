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
          BookkeepingEntryKind.EXPENSE_SETTLED,
          BookkeepingEntryKind.EXPENSE_ON_CREDIT,
          BookkeepingEntryKind.RECEIPT,
          BookkeepingEntryKind.PAYMENT,
          BookkeepingEntryKind.OWNER_CONTRIBUTION,
          BookkeepingEntryKind.OWNER_WITHDRAWAL,
          BookkeepingEntryKind.OPENING_POSITION,
          BookkeepingEntryKind.REVERSAL);
  private static final Map<BookkeepingEntryKind, Supplier<Map<String, Object>>> SCHEMAS =
      Map.ofEntries(
          Map.entry(
              BookkeepingEntryKind.DIRECT_JOURNAL,
              MachineContractPostEntryVariantSchemaBuilders::journalDirectSchema),
          Map.entry(
              BookkeepingEntryKind.SALE_SETTLED,
              () ->
                  roleAmountSchema(
                      BookkeepingEntryKind.SALE_SETTLED,
                      "Settled sale entry that debits a cash account and credits a revenue account.",
                      true,
                      requiredAccountField(
                          ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
                          "Declared cash account debited by this settled sale."),
                      requiredAccountField(
                          ProtocolPostEntryFields.TopLevel.REVENUE_ACCOUNT_CODE,
                          "Declared revenue account credited by this settled sale."),
                      null,
                      MachineContractFieldSpec.optional(
                          ProtocolPostEntryFields.TopLevel.INVENTORY_RELIEF,
                          "Optional inventory-relief facts for this sale. Trading-template books require this object so one sale request carries both revenue and cost-of-sales recognition.",
                          MachineContractPostEntryComponentSchemas.inventoryReliefSchema()),
                      MachineContractFieldSpec.optional(
                          ProtocolPostEntryFields.TopLevel.TAX,
                          "Optional declared tax selector that resolves this settled sale through an owned tax registration.",
                          MachineContractPostEntryComponentSchemas.taxSchema()))),
          Map.entry(
              BookkeepingEntryKind.SALE_ON_CREDIT,
              () ->
                  roleAmountSchema(
                      BookkeepingEntryKind.SALE_ON_CREDIT,
                      "Sale-on-credit entry that debits a trade receivable account and credits a revenue account.",
                      false,
                      requiredAccountField(
                          ProtocolPostEntryFields.TopLevel.RECEIVABLE_ACCOUNT_CODE,
                          "Declared trade receivable account debited by this sale-on-credit."),
                      requiredAccountField(
                          ProtocolPostEntryFields.TopLevel.REVENUE_ACCOUNT_CODE,
                          "Declared revenue account credited by this sale-on-credit."),
                      null,
                      MachineContractFieldSpec.optional(
                          ProtocolPostEntryFields.TopLevel.INVENTORY_RELIEF,
                          "Optional inventory-relief facts for this sale-on-credit. Trading-template books require this object so one sale request carries both revenue and cost-of-sales recognition.",
                          MachineContractPostEntryComponentSchemas.inventoryReliefSchema()),
                      MachineContractFieldSpec.optional(
                          ProtocolPostEntryFields.TopLevel.TAX,
                          "Optional declared tax selector that resolves this sale-on-credit through an owned tax registration.",
                          MachineContractPostEntryComponentSchemas.taxSchema()))),
          Map.entry(
              BookkeepingEntryKind.PURCHASE_SETTLED,
              () ->
                  roleAmountSchema(
                      BookkeepingEntryKind.PURCHASE_SETTLED,
                      "Settled purchase entry that debits an inventory account and credits a cash account.",
                      true,
                      requiredAccountField(
                          ProtocolPostEntryFields.TopLevel.INVENTORY_ACCOUNT_CODE,
                          "Declared inventory account debited by this settled purchase."),
                      requiredAccountField(
                          ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
                          "Declared cash account credited by this settled purchase."),
                      null)),
          Map.entry(
              BookkeepingEntryKind.PURCHASE_ON_CREDIT,
              () ->
                  roleAmountSchema(
                      BookkeepingEntryKind.PURCHASE_ON_CREDIT,
                      "Purchase-on-credit entry that debits an inventory account and credits a trade payable account.",
                      false,
                      requiredAccountField(
                          ProtocolPostEntryFields.TopLevel.INVENTORY_ACCOUNT_CODE,
                          "Declared inventory account debited by this purchase-on-credit."),
                      requiredAccountField(
                          ProtocolPostEntryFields.TopLevel.PAYABLE_ACCOUNT_CODE,
                          "Declared trade payable account credited by this purchase-on-credit."),
                      null)),
          Map.entry(
              BookkeepingEntryKind.EXPENSE_SETTLED,
              () ->
                  roleAmountSchema(
                      BookkeepingEntryKind.EXPENSE_SETTLED,
                      "Settled expense entry that debits an expense account and credits a cash account.",
                      true,
                      requiredAccountField(
                          ProtocolPostEntryFields.TopLevel.EXPENSE_ACCOUNT_CODE,
                          "Declared expense account debited by this settled expense."),
                      requiredAccountField(
                          ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
                          "Declared cash account credited by this settled expense."),
                      null,
                      MachineContractFieldSpec.optional(
                          ProtocolPostEntryFields.TopLevel.TAX,
                          "Optional declared tax selector that resolves this settled expense through an owned tax registration.",
                          MachineContractPostEntryComponentSchemas.taxSchema()))),
          Map.entry(
              BookkeepingEntryKind.EXPENSE_ON_CREDIT,
              () ->
                  roleAmountSchema(
                      BookkeepingEntryKind.EXPENSE_ON_CREDIT,
                      "Expense-on-credit entry that debits an expense account and credits a trade payable account.",
                      false,
                      requiredAccountField(
                          ProtocolPostEntryFields.TopLevel.EXPENSE_ACCOUNT_CODE,
                          "Declared expense account debited by this expense-on-credit."),
                      requiredAccountField(
                          ProtocolPostEntryFields.TopLevel.PAYABLE_ACCOUNT_CODE,
                          "Declared trade payable account credited by this expense-on-credit."),
                      null,
                      MachineContractFieldSpec.optional(
                          ProtocolPostEntryFields.TopLevel.TAX,
                          "Optional declared tax selector that resolves this expense-on-credit through an owned tax registration.",
                          MachineContractPostEntryComponentSchemas.taxSchema()))),
          Map.entry(
              BookkeepingEntryKind.RECEIPT,
              () ->
                  roleAmountSchema(
                      BookkeepingEntryKind.RECEIPT,
                      "Receipt entry that settles a trade receivable into cash and may carry a settlement adjunct.",
                      false,
                      requiredAccountField(
                          ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
                          "Declared cash account debited by this receipt."),
                      requiredAccountField(
                          ProtocolPostEntryFields.TopLevel.RECEIVABLE_ACCOUNT_CODE,
                          "Declared trade receivable account credited by this receipt."),
                      MachineContractFieldSpec.optional(
                          ProtocolPostEntryFields.TopLevel.SETTLEMENT_ADJUNCT,
                          "Optional settlement adjunct used by this receipt.",
                          MachineContractPostEntryComponentSchemas.settlementAdjunctSchema()))),
          Map.entry(
              BookkeepingEntryKind.PAYMENT,
              () ->
                  roleAmountSchema(
                      BookkeepingEntryKind.PAYMENT,
                      "Payment entry that settles a trade payable from cash and may carry a settlement adjunct.",
                      false,
                      requiredAccountField(
                          ProtocolPostEntryFields.TopLevel.PAYABLE_ACCOUNT_CODE,
                          "Declared trade payable account debited by this payment."),
                      requiredAccountField(
                          ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
                          "Declared cash account credited by this payment."),
                      MachineContractFieldSpec.optional(
                          ProtocolPostEntryFields.TopLevel.SETTLEMENT_ADJUNCT,
                          "Optional settlement adjunct used by this payment.",
                          MachineContractPostEntryComponentSchemas.settlementAdjunctSchema()))),
          Map.entry(
              BookkeepingEntryKind.OWNER_CONTRIBUTION,
              () ->
                  roleAmountSchema(
                      BookkeepingEntryKind.OWNER_CONTRIBUTION,
                      "Owner-contribution entry that debits an asset cash account and credits an equity account.",
                      true,
                      requiredAccountField(
                          ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
                          "Declared cash account debited by this owner contribution."),
                      requiredAccountField(
                          ProtocolPostEntryFields.TopLevel.EQUITY_ACCOUNT_CODE,
                          "Declared equity account credited by this owner contribution."),
                      null)),
          Map.entry(
              BookkeepingEntryKind.OWNER_WITHDRAWAL,
              () ->
                  roleAmountSchema(
                      BookkeepingEntryKind.OWNER_WITHDRAWAL,
                      "Owner-withdrawal entry that debits an equity account and credits an asset cash account.",
                      true,
                      requiredAccountField(
                          ProtocolPostEntryFields.TopLevel.EQUITY_ACCOUNT_CODE,
                          "Declared equity account debited by this owner withdrawal."),
                      requiredAccountField(
                          ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
                          "Declared cash account credited by this owner withdrawal."),
                      null)),
          Map.entry(
              BookkeepingEntryKind.OPENING_POSITION,
              MachineContractPostEntryVariantSchemaBuilders::openingPositionSchema),
          Map.entry(
              BookkeepingEntryKind.REVERSAL,
              MachineContractPostEntryVariantSchemaBuilders::reversalSchema));

  private MachineContractPostEntryVariantSchemaBuilders() {}

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
        "Direct balanced operational journal written without a higher-level business entry.",
        List.of(
            MachineContractPostEntryComponentSchemas.requiredEntryKindField(
                BookkeepingEntryKind.DIRECT_JOURNAL,
                "This request records a direct balanced journal."),
            MachineContractPostEntryFieldSpecs.requiredEffectiveDateField(),
            MachineContractFieldSpec.required(
                ProtocolPostEntryFields.TopLevel.LINES,
                "Balanced non-empty array of journal lines for a direct journal.",
                MachineContractSchemaSupport.arraySchema(
                    "Balanced non-empty array of journal lines for a direct journal.",
                    MachineContractPostEntryVariantSchemas.lineSchema(),
                    2)),
            MachineContractFieldSpec.optional(
                ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE,
                "Optional owned foreign-exchange facts for a transaction-currency event translated into book functional currency.",
                MachineContractPostEntryComponentSchemas.foreignExchangeSchema()),
            MachineContractPostEntryFieldSpecs.requiredEvidenceField(entryKindFacts),
            MachineContractPostEntryFieldSpecs.requiredProvenanceField()));
  }

  private static Map<String, Object> openingPositionSchema() {
    RequestSurfaceFacts.BookkeepingEntryKindFacts entryKindFacts =
        entryKindFacts(BookkeepingEntryKind.OPENING_POSITION);
    return MachineContractSchemaSupport.objectSchema(
        "Explicit opening-position entry used to seed a book before operating activity begins.",
        List.of(
            MachineContractPostEntryComponentSchemas.requiredEntryKindField(
                BookkeepingEntryKind.OPENING_POSITION,
                "This request records an opening-position entry."),
            MachineContractPostEntryFieldSpecs.requiredEffectiveDateField(),
            MachineContractFieldSpec.required(
                ProtocolPostEntryFields.TopLevel.OPENING_BALANCES,
                "Balanced non-empty array of opening balances for an opening-position entry.",
                MachineContractSchemaSupport.arraySchema(
                    "Balanced non-empty array of opening balances for an opening-position entry.",
                    MachineContractPostEntryVariantSchemas.openingBalanceSchema(),
                    2)),
            MachineContractPostEntryFieldSpecs.requiredEvidenceField(entryKindFacts),
            MachineContractPostEntryFieldSpecs.requiredProvenanceField()));
  }

  private static Map<String, Object> reversalSchema() {
    RequestSurfaceFacts.BookkeepingEntryKindFacts entryKindFacts =
        entryKindFacts(BookkeepingEntryKind.REVERSAL);
    return MachineContractSchemaSupport.objectSchema(
        "Explicit reversal entry that derives a full negation from a previously committed posting.",
        List.of(
            MachineContractPostEntryComponentSchemas.requiredEntryKindField(
                BookkeepingEntryKind.REVERSAL, "This request records a reversal entry."),
            MachineContractPostEntryFieldSpecs.requiredEffectiveDateField(),
            MachineContractFieldSpec.optional(
                ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE,
                "Optional owned foreign-exchange facts for a transaction-currency event translated into book functional currency.",
                MachineContractPostEntryComponentSchemas.foreignExchangeSchema()),
            MachineContractPostEntryFieldSpecs.requiredEvidenceField(entryKindFacts),
            MachineContractPostEntryFieldSpecs.requiredProvenanceField(),
            MachineContractFieldSpec.required(
                ProtocolPostEntryFields.TopLevel.REVERSAL,
                "Required reversal target descriptor for a reversal entry.",
                MachineContractPostEntryVariantSchemas.reversalTargetSchema())));
  }

  private static Map<String, Object> roleAmountSchema(
      BookkeepingEntryKind entryKind,
      String description,
      boolean includeForeignExchange,
      MachineContractFieldSpec debitOrSourceAccountField,
      MachineContractFieldSpec creditOrTargetAccountField,
      @org.jspecify.annotations.Nullable MachineContractFieldSpec settlementAdjunctField,
      MachineContractFieldSpec... additionalFields) {
    RequestSurfaceFacts.BookkeepingEntryKindFacts entryKindFacts = entryKindFacts(entryKind);
    List<MachineContractFieldSpec> fields = new java.util.ArrayList<>();
    fields.add(
        MachineContractPostEntryComponentSchemas.requiredEntryKindField(
            entryKind, "This request records a typed business entry."));
    fields.add(MachineContractPostEntryFieldSpecs.requiredEffectiveDateField());
    fields.add(debitOrSourceAccountField);
    fields.add(creditOrTargetAccountField);
    fields.add(MachineContractPostEntryFieldSpecs.requiredAmountField());
    if (settlementAdjunctField != null) {
      fields.add(settlementAdjunctField);
    }
    if (includeForeignExchange) {
      fields.add(
          MachineContractFieldSpec.optional(
              ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE,
              "Optional owned foreign-exchange facts for a transaction-currency event translated into book functional currency.",
              MachineContractPostEntryComponentSchemas.foreignExchangeSchema()));
    }
    java.util.Collections.addAll(fields, additionalFields);
    fields.add(MachineContractPostEntryFieldSpecs.requiredEvidenceField(entryKindFacts));
    fields.add(MachineContractPostEntryFieldSpecs.requiredProvenanceField());
    return MachineContractSchemaSupport.objectSchema(description, List.copyOf(fields));
  }

  private static RequestSurfaceFacts.BookkeepingEntryKindFacts entryKindFacts(
      BookkeepingEntryKind entryKind) {
    return ProtocolCatalog.domain().requestSurface().bookkeepingEntryKind(entryKind);
  }

  private static MachineContractFieldSpec requiredAccountField(
      String fieldName, String description) {
    return MachineContractFieldSpec.required(
        fieldName, description, MachineContractPostEntryFieldSpecs.accountCodeSchema(description));
  }
}
