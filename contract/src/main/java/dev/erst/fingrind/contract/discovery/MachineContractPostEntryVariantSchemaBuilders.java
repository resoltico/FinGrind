package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import dev.erst.fingrind.contract.protocol.RequestSurfaceFacts;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import java.util.Map;

/** Variant-specific schema builders for the post-entry request family. */
final class MachineContractPostEntryVariantSchemaBuilders {
  private MachineContractPostEntryVariantSchemaBuilders() {}

  static Map<String, Object> postEntrySchema() {
    return MachineContractSchemaSupport.rootOneOfSchema(
        "Canonical bookkeeping entry request JSON document.",
        List.of(
            journalDirectSchema(),
            saleSchema(),
            expenseSchema(),
            ownerContributionSchema(),
            ownerWithdrawalSchema(),
            openingPositionSchema(),
            reversalSchema()));
  }

  static Map<String, Object> schema(BookkeepingEntryKind entryKind) {
    return switch (entryKind) {
      case DIRECT_JOURNAL -> journalDirectSchema();
      case SALE -> saleSchema();
      case EXPENSE -> expenseSchema();
      case OWNER_CONTRIBUTION -> ownerContributionSchema();
      case OWNER_WITHDRAWAL -> ownerWithdrawalSchema();
      case OPENING_POSITION -> openingPositionSchema();
      case REVERSAL -> reversalSchema();
    };
  }

  private static Map<String, Object> journalDirectSchema() {
    RequestSurfaceFacts.BookkeepingEntryKindFacts entryKindFacts =
        entryKindFacts(BookkeepingEntryKind.DIRECT_JOURNAL);
    return MachineContractSchemaSupport.objectSchema(
        "Direct balanced operational journal written without a higher-level business entry.",
        List.of(
            MachineContractPostEntryComponentSchemas.requiredEntryKindField(
                BookkeepingEntryKind.DIRECT_JOURNAL,
                "This request records one direct balanced journal."),
            MachineContractPostEntryFieldSpecs.requiredEffectiveDateField(),
            MachineContractFieldSpec.required(
                ProtocolPostEntryFields.TopLevel.LINES,
                "Balanced non-empty array of journal lines for one direct journal.",
                MachineContractSchemaSupport.arraySchema(
                    "Balanced non-empty array of journal lines for one direct journal.",
                    MachineContractPostEntryVariantSchemas.lineSchema(),
                    2)),
            MachineContractFieldSpec.optional(
                ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE,
                "Optional owned foreign-exchange facts for one transaction-currency event translated into book functional currency.",
                MachineContractPostEntryComponentSchemas.foreignExchangeSchema()),
            MachineContractPostEntryFieldSpecs.requiredEvidenceField(entryKindFacts),
            MachineContractPostEntryFieldSpecs.requiredProvenanceField()));
  }

  private static Map<String, Object> saleSchema() {
    return roleAmountSchema(
        BookkeepingEntryKind.SALE,
        "Sale entry that debits one asset cash account and credits one revenue account.",
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
            "Declared cash account debited by this sale.",
            MachineContractPostEntryFieldSpecs.accountCodeSchema(
                "Declared cash account debited by this sale.")),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.TopLevel.REVENUE_ACCOUNT_CODE,
            "Declared revenue account credited by this sale.",
            MachineContractPostEntryFieldSpecs.accountCodeSchema(
                "Declared revenue account credited by this sale.")),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.TAX,
            "Optional declared tax selector that resolves this sale through one owned tax registration.",
            MachineContractPostEntryComponentSchemas.taxSchema()));
  }

  private static Map<String, Object> expenseSchema() {
    return roleAmountSchema(
        BookkeepingEntryKind.EXPENSE,
        "Expense entry that debits one expense account and credits one asset cash account.",
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.TopLevel.EXPENSE_ACCOUNT_CODE,
            "Declared expense account debited by this expense.",
            MachineContractPostEntryFieldSpecs.accountCodeSchema(
                "Declared expense account debited by this expense.")),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
            "Declared cash account credited by this expense.",
            MachineContractPostEntryFieldSpecs.accountCodeSchema(
                "Declared cash account credited by this expense.")),
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.TAX,
            "Optional declared tax selector that resolves this expense through one owned tax registration.",
            MachineContractPostEntryComponentSchemas.taxSchema()));
  }

  private static Map<String, Object> ownerContributionSchema() {
    return roleAmountSchema(
        BookkeepingEntryKind.OWNER_CONTRIBUTION,
        "Owner-contribution entry that debits one asset cash account and credits one equity account.",
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
            "Declared cash account debited by this owner contribution.",
            MachineContractPostEntryFieldSpecs.accountCodeSchema(
                "Declared cash account debited by this owner contribution.")),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.TopLevel.EQUITY_ACCOUNT_CODE,
            "Declared equity account credited by this owner contribution.",
            MachineContractPostEntryFieldSpecs.accountCodeSchema(
                "Declared equity account credited by this owner contribution.")));
  }

  private static Map<String, Object> ownerWithdrawalSchema() {
    return roleAmountSchema(
        BookkeepingEntryKind.OWNER_WITHDRAWAL,
        "Owner-withdrawal entry that debits one equity account and credits one asset cash account.",
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.TopLevel.EQUITY_ACCOUNT_CODE,
            "Declared equity account debited by this owner withdrawal.",
            MachineContractPostEntryFieldSpecs.accountCodeSchema(
                "Declared equity account debited by this owner withdrawal.")),
        MachineContractFieldSpec.required(
            ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
            "Declared cash account credited by this owner withdrawal.",
            MachineContractPostEntryFieldSpecs.accountCodeSchema(
                "Declared cash account credited by this owner withdrawal.")));
  }

  private static Map<String, Object> openingPositionSchema() {
    RequestSurfaceFacts.BookkeepingEntryKindFacts entryKindFacts =
        entryKindFacts(BookkeepingEntryKind.OPENING_POSITION);
    return MachineContractSchemaSupport.objectSchema(
        "Explicit opening-position entry used to seed one book before operating activity begins.",
        List.of(
            MachineContractPostEntryComponentSchemas.requiredEntryKindField(
                BookkeepingEntryKind.OPENING_POSITION,
                "This request records one opening-position entry."),
            MachineContractPostEntryFieldSpecs.requiredEffectiveDateField(),
            MachineContractFieldSpec.required(
                ProtocolPostEntryFields.TopLevel.OPENING_BALANCES,
                "Balanced non-empty array of opening balances for one opening-position entry.",
                MachineContractSchemaSupport.arraySchema(
                    "Balanced non-empty array of opening balances for one opening-position entry.",
                    MachineContractPostEntryVariantSchemas.openingBalanceSchema(),
                    2)),
            MachineContractPostEntryFieldSpecs.requiredEvidenceField(entryKindFacts),
            MachineContractPostEntryFieldSpecs.requiredProvenanceField()));
  }

  private static Map<String, Object> reversalSchema() {
    RequestSurfaceFacts.BookkeepingEntryKindFacts entryKindFacts =
        entryKindFacts(BookkeepingEntryKind.REVERSAL);
    return MachineContractSchemaSupport.objectSchema(
        "Explicit reversal entry that fully negates one previously committed posting.",
        List.of(
            MachineContractPostEntryComponentSchemas.requiredEntryKindField(
                BookkeepingEntryKind.REVERSAL, "This request records one reversal entry."),
            MachineContractPostEntryFieldSpecs.requiredEffectiveDateField(),
            MachineContractFieldSpec.required(
                ProtocolPostEntryFields.TopLevel.LINES,
                "Balanced non-empty array of journal lines for one reversal entry.",
                MachineContractSchemaSupport.arraySchema(
                    "Balanced non-empty array of journal lines for one reversal entry.",
                    MachineContractPostEntryVariantSchemas.lineSchema(),
                    2)),
            MachineContractFieldSpec.optional(
                ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE,
                "Optional owned foreign-exchange facts for one transaction-currency event translated into book functional currency.",
                MachineContractPostEntryComponentSchemas.foreignExchangeSchema()),
            MachineContractPostEntryFieldSpecs.requiredEvidenceField(entryKindFacts),
            MachineContractPostEntryFieldSpecs.requiredProvenanceField(),
            MachineContractFieldSpec.required(
                ProtocolPostEntryFields.TopLevel.REVERSAL,
                "Required reversal target descriptor for one reversal entry.",
                MachineContractPostEntryVariantSchemas.reversalTargetSchema())));
  }

  private static Map<String, Object> roleAmountSchema(
      BookkeepingEntryKind entryKind,
      String description,
      MachineContractFieldSpec debitOrSourceAccountField,
      MachineContractFieldSpec creditOrTargetAccountField,
      MachineContractFieldSpec... additionalFields) {
    RequestSurfaceFacts.BookkeepingEntryKindFacts entryKindFacts = entryKindFacts(entryKind);
    List<MachineContractFieldSpec> fields = new java.util.ArrayList<>();
    fields.add(
        MachineContractPostEntryComponentSchemas.requiredEntryKindField(
            entryKind, "This request records one typed business entry."));
    fields.add(MachineContractPostEntryFieldSpecs.requiredEffectiveDateField());
    fields.add(debitOrSourceAccountField);
    fields.add(creditOrTargetAccountField);
    fields.add(MachineContractPostEntryFieldSpecs.requiredAmountField());
    fields.add(
        MachineContractFieldSpec.optional(
            ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE,
            "Optional owned foreign-exchange facts for one transaction-currency event translated into book functional currency.",
            MachineContractPostEntryComponentSchemas.foreignExchangeSchema()));
    java.util.Collections.addAll(fields, additionalFields);
    fields.add(MachineContractPostEntryFieldSpecs.requiredEvidenceField(entryKindFacts));
    fields.add(MachineContractPostEntryFieldSpecs.requiredProvenanceField());
    return MachineContractSchemaSupport.objectSchema(description, List.copyOf(fields));
  }

  private static RequestSurfaceFacts.BookkeepingEntryKindFacts entryKindFacts(
      BookkeepingEntryKind entryKind) {
    return ProtocolCatalog.domain().requestSurface().bookkeepingEntryKind(entryKind);
  }
}
