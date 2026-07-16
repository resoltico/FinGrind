package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliJsonFieldAccess.requiredInt;
import static dev.erst.fingrind.cli.CliJsonFieldAccess.requiredText;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.rejectUnexpectedFields;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.requireObjectNode;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetDepreciationSchedule;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetId;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.protocol.ProtocolFixedAssetPostingRequestFieldSets;
import dev.erst.fingrind.contract.protocol.ProtocolFixedAssetRequestFields;
import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import dev.erst.fingrind.contract.protocol.ProtocolPostingNestedFieldSets;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.CanonicalTemporalText;
import tools.jackson.databind.node.ObjectNode;

/** Reads typed request payloads owned by the fixed-assets context. */
final class CliFixedAssetBookkeepingEntryReaders {
  private CliFixedAssetBookkeepingEntryReaders() {}

  static BookkeepingEntry read(ObjectNode rootNode, BookkeepingEntryKind entryKind) {
    return switch (entryKind) {
      case FIXED_ASSET_CAPITALIZATION -> readCapitalization(rootNode);
      case FIXED_ASSET_DEPRECIATION -> readDepreciation(rootNode);
      case FIXED_ASSET_DISPOSAL -> readDisposal(rootNode);
      default -> throw new IllegalArgumentException("Expected a fixed-asset entry kind.");
    };
  }

  static FixedAssetBookkeepingEntryVariants.Capitalization readCapitalization(ObjectNode rootNode) {
    rejectUnexpectedFields(
        rootNode, null, ProtocolFixedAssetPostingRequestFieldSets.capitalizationFields());
    return new FixedAssetBookkeepingEntryVariants.Capitalization(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        fixedAssetId(rootNode),
        accountCode(rootNode, ProtocolPostEntryFields.TopLevel.ASSET_ACCOUNT_CODE),
        accountCode(
            rootNode, ProtocolPostEntryFields.TopLevel.ACCUMULATED_DEPRECIATION_ACCOUNT_CODE),
        accountCode(rootNode, ProtocolPostEntryFields.TopLevel.DEPRECIATION_EXPENSE_ACCOUNT_CODE),
        accountCode(rootNode, ProtocolPostEntryFields.TopLevel.DISPOSAL_GAIN_ACCOUNT_CODE),
        accountCode(rootNode, ProtocolPostEntryFields.TopLevel.DISPOSAL_LOSS_ACCOUNT_CODE),
        accountCode(rootNode, ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE),
        CliBookkeepingEntryStructureParser.requiredPositiveAmount(
            rootNode, ProtocolPostEntryFields.TopLevel.COST),
        requiredDepreciationSchedule(rootNode));
  }

  static FixedAssetBookkeepingEntryVariants.Depreciation readDepreciation(ObjectNode rootNode) {
    rejectUnexpectedFields(
        rootNode, null, ProtocolFixedAssetPostingRequestFieldSets.depreciationFields());
    return new FixedAssetBookkeepingEntryVariants.Depreciation(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        fixedAssetId(rootNode),
        null);
  }

  static FixedAssetBookkeepingEntryVariants.Disposal readDisposal(ObjectNode rootNode) {
    rejectUnexpectedFields(
        rootNode, null, ProtocolFixedAssetPostingRequestFieldSets.disposalFields());
    return new FixedAssetBookkeepingEntryVariants.Disposal(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        fixedAssetId(rootNode),
        accountCode(rootNode, ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE),
        CliBookkeepingEntryStructureParser.requiredPositiveAmount(
            rootNode, ProtocolPostEntryFields.TopLevel.PROCEEDS),
        null);
  }

  private static FixedAssetId fixedAssetId(ObjectNode rootNode) {
    return new FixedAssetId(
        requiredText(rootNode, ProtocolPostEntryFields.TopLevel.FIXED_ASSET_ID));
  }

  private static AccountCode accountCode(ObjectNode rootNode, String fieldName) {
    return new AccountCode(requiredText(rootNode, fieldName));
  }

  private static FixedAssetDepreciationSchedule requiredDepreciationSchedule(ObjectNode rootNode) {
    ObjectNode schedule =
        requireObjectNode(
            rootNode.get(ProtocolPostEntryFields.TopLevel.DEPRECIATION_SCHEDULE),
            ProtocolPostEntryFields.TopLevel.DEPRECIATION_SCHEDULE);
    rejectUnexpectedFields(
        schedule,
        ProtocolPostEntryFields.TopLevel.DEPRECIATION_SCHEDULE,
        ProtocolPostingNestedFieldSets.fixedAssetDepreciationScheduleFields());
    return new FixedAssetDepreciationSchedule(
        CanonicalTemporalText.parseLocalDate(
            requiredText(
                schedule, ProtocolFixedAssetRequestFields.DepreciationSchedule.IN_SERVICE_DATE),
            ProtocolFixedAssetRequestFields.DepreciationSchedule.IN_SERVICE_DATE),
        requiredInt(
            schedule, ProtocolFixedAssetRequestFields.DepreciationSchedule.USEFUL_LIFE_MONTHS),
        MonetaryAmount.of(
            CliJsonMoneyParser.requiredMoney(
                schedule, ProtocolFixedAssetRequestFields.DepreciationSchedule.RESIDUAL_VALUE)));
  }
}
