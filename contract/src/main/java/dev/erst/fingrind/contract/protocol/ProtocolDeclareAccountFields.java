package dev.erst.fingrind.contract.protocol;

import java.util.Set;

/** Canonical declare-account request field names shared by parser and machine contract surfaces. */
public final class ProtocolDeclareAccountFields {
  public static final String ACCOUNT_CODE = ProtocolSharedRequestFields.ACCOUNT_CODE;
  public static final String ACCOUNT_NAME = "accountName";
  public static final String ACCOUNT_TYPE = "accountType";
  public static final String ACCOUNT_NODE_KIND = "accountNodeKind";
  public static final String PARENT_ACCOUNT_CODE = "parentAccountCode";
  public static final String CONTRA_OF_ACCOUNT_CODE = "contraOfAccountCode";
  public static final String FINANCIAL_POSITION_LINE_CLASSIFICATION =
      "financialPositionLineClassification";
  public static final String PROFIT_AND_LOSS_LINE_CLASSIFICATION =
      "profitAndLossLineClassification";
  public static final String CASH_FLOW_ASSET_CLASSIFICATION = "cashFlowAssetClassification";
  public static final String UNIT_OF_MEASURE = "unitOfMeasure";

  /** Canonical nested field names for declare-account inventory unit-of-measure payloads. */
  public static final class UnitOfMeasure {
    public static final String TOKEN = "token";
    public static final String QUANTITY_SCALE = "quantityScale";

    private UnitOfMeasure() {}

    /** Returns the accepted nested fields for unit-of-measure payloads. */
    public static Set<String> fields() {
      return Set.of(TOKEN, QUANTITY_SCALE);
    }
  }

  private ProtocolDeclareAccountFields() {}
}
