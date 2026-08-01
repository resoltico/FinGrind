package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliJsonFieldAccess.optionalText;
import static dev.erst.fingrind.cli.CliJsonFieldAccess.requiredInt;
import static dev.erst.fingrind.cli.CliJsonFieldAccess.requiredText;
import static dev.erst.fingrind.cli.CliJsonScalarParsers.parseWireValue;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.optionalObject;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.rejectUnexpectedFields;

import dev.erst.fingrind.contract.protocol.ProtocolBookRequestFieldSets;
import dev.erst.fingrind.contract.protocol.ProtocolDeclareAccountFields;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.UnitOfMeasure;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;

/** Parses the shared top-level account-definition document used by Account Registry mutations. */
final class CliAccountDefinitionRequestParser {
  private CliAccountDefinitionRequestParser() {}

  static AccountDefinition read(
      ObjectNode rootNode, String wrapperFieldName, String wrappedPayloadMessage) {
    CliWrappedRequestShapeGuards.rejectWrappedTopLevelPayload(
        rootNode,
        wrapperFieldName,
        ProtocolBookRequestFieldSets.declareAccountFields(),
        wrappedPayloadMessage);
    rejectUnexpectedFields(rootNode, null, ProtocolBookRequestFieldSets.declareAccountFields());
    var unitOfMeasureNode =
        optionalObject(rootNode, ProtocolDeclareAccountFields.UNIT_OF_MEASURE)
            .map(
                node -> {
                  rejectUnexpectedFields(
                      node,
                      ProtocolDeclareAccountFields.UNIT_OF_MEASURE,
                      ProtocolDeclareAccountFields.UnitOfMeasure.fields());
                  return node;
                });
    return new AccountDefinition(
        new AccountCode(requiredText(rootNode, ProtocolDeclareAccountFields.ACCOUNT_CODE)),
        new AccountName(requiredText(rootNode, ProtocolDeclareAccountFields.ACCOUNT_NAME)),
        parseWireValue(
            requiredText(rootNode, ProtocolDeclareAccountFields.ACCOUNT_TYPE),
            ProtocolDeclareAccountFields.ACCOUNT_TYPE,
            AccountType.wireValues(),
            AccountType::fromWireValue),
        new AccountTaxonomy(
            parseWireValue(
                requiredText(rootNode, ProtocolDeclareAccountFields.ACCOUNT_NODE_KIND),
                ProtocolDeclareAccountFields.ACCOUNT_NODE_KIND,
                AccountNodeKind.wireValues(),
                AccountNodeKind::fromWireValue),
            optionalText(rootNode, ProtocolDeclareAccountFields.PARENT_ACCOUNT_CODE)
                .map(AccountCode::new),
            optionalText(rootNode, ProtocolDeclareAccountFields.CONTRA_OF_ACCOUNT_CODE)
                .map(AccountCode::new),
            optionalText(
                    rootNode, ProtocolDeclareAccountFields.FINANCIAL_POSITION_LINE_CLASSIFICATION)
                .map(
                    value ->
                        parseWireValue(
                            value,
                            ProtocolDeclareAccountFields.FINANCIAL_POSITION_LINE_CLASSIFICATION,
                            FinancialPositionLineClassification.declaredAccountWireValues(),
                            FinancialPositionLineClassification::fromWireValue)),
            optionalText(rootNode, ProtocolDeclareAccountFields.PROFIT_AND_LOSS_LINE_CLASSIFICATION)
                .map(
                    value ->
                        parseWireValue(
                            value,
                            ProtocolDeclareAccountFields.PROFIT_AND_LOSS_LINE_CLASSIFICATION,
                            ProfitAndLossLineClassification.wireValues(),
                            ProfitAndLossLineClassification::fromWireValue)),
            optionalText(rootNode, ProtocolDeclareAccountFields.CASH_FLOW_ASSET_CLASSIFICATION)
                .map(
                    value ->
                        parseWireValue(
                            value,
                            ProtocolDeclareAccountFields.CASH_FLOW_ASSET_CLASSIFICATION,
                            CashFlowAssetClassification.wireValues(),
                            CashFlowAssetClassification::fromWireValue))),
        unitOfMeasureNode
            .map(
                node ->
                    new UnitOfMeasure(
                        requiredText(node, ProtocolDeclareAccountFields.UnitOfMeasure.TOKEN),
                        requiredInt(
                            node, ProtocolDeclareAccountFields.UnitOfMeasure.QUANTITY_SCALE)))
            .orElse(null));
  }

  /** Validated field values shared by declaration and amendment command construction. */
  record AccountDefinition(
      AccountCode accountCode,
      AccountName accountName,
      AccountType accountType,
      AccountTaxonomy accountTaxonomy,
      @Nullable UnitOfMeasure unitOfMeasure) {}
}
