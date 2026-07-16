package dev.erst.fingrind.contract.protocol;

import java.util.List;

/** Canonical request grammar owned by the Fixed Assets context. */
public final class ProtocolFixedAssetRequestFields {
  private ProtocolFixedAssetRequestFields() {}

  /** Returns fixed-asset depreciation-schedule fields in stable wire order. */
  public static List<String> depreciationScheduleFields() {
    return List.of(
        DepreciationSchedule.IN_SERVICE_DATE,
        DepreciationSchedule.USEFUL_LIFE_MONTHS,
        DepreciationSchedule.RESIDUAL_VALUE);
  }

  /** Fixed-asset depreciation-schedule request fields. */
  public static final class DepreciationSchedule {
    public static final String IN_SERVICE_DATE = "inServiceDate";
    public static final String USEFUL_LIFE_MONTHS = "usefulLifeMonths";
    public static final String RESIDUAL_VALUE = "residualValue";

    private DepreciationSchedule() {}
  }
}
