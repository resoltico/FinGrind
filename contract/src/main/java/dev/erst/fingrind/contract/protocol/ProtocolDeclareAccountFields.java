package dev.erst.fingrind.contract.protocol;

/** Canonical declare-account request field names shared by parser and machine contract surfaces. */
public final class ProtocolDeclareAccountFields {
  public static final String ACCOUNT_CODE = ProtocolSharedRequestFields.ACCOUNT_CODE;
  public static final String ACCOUNT_NAME = "accountName";
  public static final String ACCOUNT_TYPE = "accountType";
  public static final String ACCOUNT_NODE_KIND = "accountNodeKind";
  public static final String PARENT_ACCOUNT_CODE = "parentAccountCode";
  public static final String FINANCIAL_POSITION_LINE_CLASSIFICATION =
      "financialPositionLineClassification";
  public static final String PROFIT_AND_LOSS_LINE_CLASSIFICATION =
      "profitAndLossLineClassification";
  public static final String CASH_FLOW_ASSET_CLASSIFICATION = "cashFlowAssetClassification";

  private ProtocolDeclareAccountFields() {}
}
