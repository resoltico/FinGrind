package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.protocol.ProtocolBusinessEventFields;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import java.util.Map;

/** Machine request schemas owned by the Fixed Assets context. */
final class MachineContractFixedAssetPostEntryVariantSchemaBuilders {
  private MachineContractFixedAssetPostEntryVariantSchemaBuilders() {}

  static Map<String, Object> capitalizationSchema() {
    return schema(
        BookkeepingEntryKind.FIXED_ASSET_CAPITALIZATION,
        "Capitalizes one identifiable asset and fixes its straight-line depreciation schedule.",
        List.of(
            requiredFixedAssetId(),
            MachineContractPostEntryContextSchemaSupport.requiredAccount(
                ProtocolBusinessEventFields.FixedAsset.ASSET_ACCOUNT_CODE,
                "Declared non-current asset account debited by this capitalization."),
            MachineContractPostEntryContextSchemaSupport.requiredAccount(
                ProtocolBusinessEventFields.FixedAsset.ACCUMULATED_DEPRECIATION_ACCOUNT_CODE,
                "Declared contra-asset account credited by depreciation charges."),
            MachineContractPostEntryContextSchemaSupport.requiredAccount(
                ProtocolBusinessEventFields.FixedAsset.DEPRECIATION_EXPENSE_ACCOUNT_CODE,
                "Declared expense account debited by depreciation charges."),
            MachineContractPostEntryContextSchemaSupport.requiredAccount(
                ProtocolBusinessEventFields.FixedAsset.DISPOSAL_GAIN_ACCOUNT_CODE,
                "Declared income account credited by a disposal gain."),
            MachineContractPostEntryContextSchemaSupport.requiredAccount(
                ProtocolBusinessEventFields.FixedAsset.DISPOSAL_LOSS_ACCOUNT_CODE,
                "Declared expense account debited by a disposal loss."),
            MachineContractPostEntryContextSchemaSupport.requiredAccount(
                ProtocolBusinessEventFields.Core.CASH_ACCOUNT_CODE,
                "Declared cash account credited by this capitalization."),
            MachineContractPostEntryContextSchemaSupport.requiredPositiveMoney(
                ProtocolBusinessEventFields.FixedAsset.COST,
                "Exact positive functional-currency cost capitalized into the asset."),
            MachineContractFieldSpec.required(
                ProtocolBusinessEventFields.FixedAsset.DEPRECIATION_SCHEDULE,
                "Required straight-line depreciation terms for this capitalized asset.",
                MachineContractPostEntryComponentSchemas.fixedAssetDepreciationScheduleSchema())));
  }

  static Map<String, Object> depreciationSchema() {
    return schema(
        BookkeepingEntryKind.FIXED_ASSET_DEPRECIATION,
        "Charges the next executor-derived straight-line depreciation amount for one admitted asset.",
        List.of(requiredFixedAssetId()));
  }

  static Map<String, Object> disposalSchema() {
    return schema(
        BookkeepingEntryKind.FIXED_ASSET_DISPOSAL,
        "Disposes one admitted asset and lets FinGrind derive its retained carrying amount and gain or loss.",
        List.of(
            requiredFixedAssetId(),
            MachineContractPostEntryContextSchemaSupport.requiredAccount(
                ProtocolBusinessEventFields.Core.CASH_ACCOUNT_CODE,
                "Declared cash account debited by disposal proceeds."),
            MachineContractPostEntryContextSchemaSupport.requiredPositiveMoney(
                ProtocolBusinessEventFields.FixedAsset.PROCEEDS,
                "Exact positive functional-currency cash proceeds from the disposal.")));
  }

  private static Map<String, Object> schema(
      BookkeepingEntryKind kind, String description, List<MachineContractFieldSpec> contextFields) {
    return MachineContractPostEntryContextSchemaSupport.typedEventSchema(
        kind, description, "This request records a typed fixed-asset event.", contextFields);
  }

  private static MachineContractFieldSpec requiredFixedAssetId() {
    return MachineContractFieldSpec.required(
        ProtocolBusinessEventFields.FixedAsset.FIXED_ASSET_ID,
        "Stable lowercase-kebab identifier for this fixed-asset lifecycle.",
        MachineContractScalarSchemas.tokenStringSchema(
            "Stable lowercase-kebab identifier for this fixed-asset lifecycle.",
            "[a-z0-9]+(?:-[a-z0-9]+)*",
            120));
  }
}
