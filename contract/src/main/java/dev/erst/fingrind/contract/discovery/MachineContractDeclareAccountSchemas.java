package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolDeclareAccountFields;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import java.util.List;
import java.util.Map;

/** Builds executable JSON Schema documents for declare-account request shapes. */
final class MachineContractDeclareAccountSchemas {
  private MachineContractDeclareAccountSchemas() {}

  static Map<String, Object> declareAccountSchema() {
    Map<String, Object> rootSchema =
        MachineContractSchemaSupport.rootObjectSchema(
            "Canonical "
                + MachineContractSchemaSupport.operation(OperationId.DECLARE_ACCOUNT)
                + " request JSON document.",
            topLevelFields());
    return MachineContractSchemaSupport.orderedMapFromEntries(
        java.util.stream.Stream.concat(
                rootSchema.entrySet().stream()
                    .map(entry -> Map.entry(entry.getKey(), (Object) entry.getValue())),
                java.util.stream.Stream.of(
                    Map.<String, Object>entry(
                        "oneOf",
                        List.of(balanceSheetTaxonomyBranch(), profitAndLossTaxonomyBranch()))))
            .toList());
  }

  static Map<String, Object> declareAccountSchemaWithoutDialect() {
    return MachineContractSchemaSupport.stripDialect(declareAccountSchema());
  }

  static ContractRequestShapes.DeclareAccountRequestShapeDescriptor descriptor() {
    return new ContractRequestShapes.DeclareAccountRequestShapeDescriptor(
        MachineContractSchemaSupport.requestFieldDescriptors(topLevelFields()),
        List.of(
            new ContractRequestShapes.EnumVocabularyDescriptor(
                "accountType", AccountType.wireValues()),
            new ContractRequestShapes.EnumVocabularyDescriptor(
                "accountRole", AccountRole.wireValues()),
            new ContractRequestShapes.EnumVocabularyDescriptor(
                "accountNodeKind", AccountNodeKind.wireValues()),
            new ContractRequestShapes.EnumVocabularyDescriptor(
                "financialPositionLineClassification",
                FinancialPositionLineClassification.declaredAccountWireValues()),
            new ContractRequestShapes.EnumVocabularyDescriptor(
                "profitAndLossLineClassification", ProfitAndLossLineClassification.wireValues())),
        declareAccountSchema());
  }

  private static List<MachineContractFieldSpec> topLevelFields() {
    return List.of(
        MachineContractFieldSpec.required(
            ProtocolDeclareAccountFields.ACCOUNT_CODE,
            "Book-local account code used by journal lines. FinGrind accepts ASCII letters or digits followed by ASCII letters, digits, '.', '_', ':', '/', or '-'.",
            MachineContractScalarSchemas.tokenStringSchema(
                "Book-local account code used by journal lines.",
                AccountCode.pattern(),
                AccountCode.maxLength())),
        MachineContractFieldSpec.required(
            ProtocolDeclareAccountFields.ACCOUNT_NAME,
            "Non-blank display name for the declared account.",
            MachineContractScalarSchemas.nonBlankStringSchema(
                "Non-blank display name for the declared account.")),
        MachineContractFieldSpec.required(
            ProtocolDeclareAccountFields.ACCOUNT_TYPE,
            "Canonical chart-of-accounts classification for the declared account.",
            MachineContractScalarSchemas.enumStringSchema(
                "Canonical chart-of-accounts classification for the declared account.",
                AccountType.wireValues())),
        MachineContractFieldSpec.required(
            ProtocolDeclareAccountFields.ACCOUNT_ROLE,
            "Canonical doctrinal role for the declared account, including ordinary and contra polarity.",
            MachineContractScalarSchemas.enumStringSchema(
                "Canonical doctrinal role for the declared account, including ordinary and contra polarity.",
                AccountRole.wireValues())),
        MachineContractFieldSpec.required(
            ProtocolDeclareAccountFields.ACCOUNT_NODE_KIND,
            "Canonical chart node kind for the declared account. HEADER accounts organize child nodes and POSTABLE accounts accept direct postings.",
            MachineContractScalarSchemas.enumStringSchema(
                "Canonical chart node kind for the declared account. HEADER accounts organize child nodes and POSTABLE accounts accept direct postings.",
                AccountNodeKind.wireValues())),
        MachineContractFieldSpec.optional(
            ProtocolDeclareAccountFields.PARENT_ACCOUNT_CODE,
            "Optional parent account code that places this account inside the declared chart hierarchy.",
            MachineContractScalarSchemas.tokenStringSchema(
                "Optional parent account code that places this account inside the declared chart hierarchy.",
                AccountCode.pattern(),
                AccountCode.maxLength())),
        MachineContractFieldSpec.conditional(
            ProtocolDeclareAccountFields.FINANCIAL_POSITION_LINE_CLASSIFICATION,
            "Required when accountType is ASSET, LIABILITY, or EQUITY. Declares the account's canonical financial position taxonomy.",
            MachineContractScalarSchemas.enumStringSchema(
                "Required when accountType is ASSET, LIABILITY, or EQUITY. Declares the account's canonical financial position taxonomy.",
                FinancialPositionLineClassification.declaredAccountWireValues())),
        MachineContractFieldSpec.conditional(
            ProtocolDeclareAccountFields.PROFIT_AND_LOSS_LINE_CLASSIFICATION,
            "Required when accountType is REVENUE or EXPENSE. Declares the account's canonical profit-and-loss taxonomy.",
            MachineContractScalarSchemas.enumStringSchema(
                "Required when accountType is REVENUE or EXPENSE. Declares the account's canonical profit-and-loss taxonomy.",
                ProfitAndLossLineClassification.wireValues())));
  }

  private static Map<String, Object> balanceSheetTaxonomyBranch() {
    return MachineContractSchemaSupport.orderedMap(
        "description",
        "ASSET, LIABILITY, and EQUITY accounts must declare financialPositionLineClassification and must not declare profitAndLossLineClassification.",
        "properties",
        MachineContractSchemaSupport.orderedMap(
            ProtocolDeclareAccountFields.ACCOUNT_TYPE,
            MachineContractScalarSchemas.enumStringSchema(
                "Balance-sheet account types.",
                List.of(
                    AccountType.ASSET.wireValue(),
                    AccountType.LIABILITY.wireValue(),
                    AccountType.EQUITY.wireValue())),
            ProtocolDeclareAccountFields.FINANCIAL_POSITION_LINE_CLASSIFICATION,
            MachineContractScalarSchemas.enumStringSchema(
                "Required financial position taxonomy for balance-sheet accounts.",
                FinancialPositionLineClassification.declaredAccountWireValues())),
        "required",
        List.of(ProtocolDeclareAccountFields.FINANCIAL_POSITION_LINE_CLASSIFICATION),
        "not",
        MachineContractSchemaSupport.orderedMap(
            "required", List.of(ProtocolDeclareAccountFields.PROFIT_AND_LOSS_LINE_CLASSIFICATION)));
  }

  private static Map<String, Object> profitAndLossTaxonomyBranch() {
    return MachineContractSchemaSupport.orderedMap(
        "description",
        "REVENUE and EXPENSE accounts must declare profitAndLossLineClassification and must not declare financialPositionLineClassification.",
        "properties",
        MachineContractSchemaSupport.orderedMap(
            ProtocolDeclareAccountFields.ACCOUNT_TYPE,
            MachineContractScalarSchemas.enumStringSchema(
                "Nominal account types.",
                List.of(AccountType.REVENUE.wireValue(), AccountType.EXPENSE.wireValue())),
            ProtocolDeclareAccountFields.PROFIT_AND_LOSS_LINE_CLASSIFICATION,
            MachineContractScalarSchemas.enumStringSchema(
                "Required profit-and-loss taxonomy for nominal accounts.",
                ProfitAndLossLineClassification.wireValues())),
        "required",
        List.of(ProtocolDeclareAccountFields.PROFIT_AND_LOSS_LINE_CLASSIFICATION),
        "not",
        MachineContractSchemaSupport.orderedMap(
            "required",
            List.of(ProtocolDeclareAccountFields.FINANCIAL_POSITION_LINE_CLASSIFICATION)));
  }
}
