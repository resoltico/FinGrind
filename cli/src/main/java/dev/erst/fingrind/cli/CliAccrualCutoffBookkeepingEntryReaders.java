package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliJsonFieldAccess.requiredText;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.rejectUnexpectedFields;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffId;
import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.protocol.ProtocolAccrualCutoffPostingRequestFieldSets;
import dev.erst.fingrind.contract.protocol.ProtocolBusinessEventFields;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import tools.jackson.databind.node.ObjectNode;

/** Reads typed request payloads owned by the accrual cut-off context. */
final class CliAccrualCutoffBookkeepingEntryReaders {
  private CliAccrualCutoffBookkeepingEntryReaders() {}

  static BookkeepingEntry read(ObjectNode rootNode, BookkeepingEntryKind entryKind) {
    return switch (entryKind) {
      case PREPAYMENT -> readPrepayment(rootNode);
      case DEFERRED_REVENUE -> readDeferredRevenue(rootNode);
      case ACCRUED_EXPENSE -> readAccruedExpense(rootNode);
      case ACCRUAL_CUTOFF_RECOGNITION -> readRecognition(rootNode);
      case ACCRUED_EXPENSE_SETTLEMENT -> readSettlement(rootNode);
      default -> throw new IllegalArgumentException("Expected an accrual cut-off entry kind.");
    };
  }

  static AccrualCutoffBookkeepingEntryVariants.Prepayment readPrepayment(ObjectNode rootNode) {
    rejectUnexpectedFields(
        rootNode, null, ProtocolAccrualCutoffPostingRequestFieldSets.prepaymentFields());
    return new AccrualCutoffBookkeepingEntryVariants.Prepayment(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        accrualCutoffId(rootNode),
        accountCode(
            rootNode, ProtocolBusinessEventFields.AccrualCutoff.PREPAYMENT_ASSET_ACCOUNT_CODE),
        accountCode(rootNode, ProtocolBusinessEventFields.Inventory.EXPENSE_ACCOUNT_CODE),
        accountCode(rootNode, ProtocolBusinessEventFields.Core.CASH_ACCOUNT_CODE),
        CliBookkeepingEntryStructureParser.requiredPositiveAmount(rootNode),
        CliBookkeepingEntryNestedParser.requiredRecognitionInterval(rootNode));
  }

  static AccrualCutoffBookkeepingEntryVariants.DeferredRevenue readDeferredRevenue(
      ObjectNode rootNode) {
    rejectUnexpectedFields(
        rootNode, null, ProtocolAccrualCutoffPostingRequestFieldSets.deferredRevenueFields());
    return new AccrualCutoffBookkeepingEntryVariants.DeferredRevenue(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        accrualCutoffId(rootNode),
        accountCode(rootNode, ProtocolBusinessEventFields.Core.CASH_ACCOUNT_CODE),
        accountCode(
            rootNode, ProtocolBusinessEventFields.AccrualCutoff.DEFERRED_REVENUE_ACCOUNT_CODE),
        accountCode(rootNode, ProtocolBusinessEventFields.Core.REVENUE_ACCOUNT_CODE),
        CliBookkeepingEntryStructureParser.requiredPositiveAmount(rootNode),
        CliBookkeepingEntryNestedParser.requiredRecognitionInterval(rootNode));
  }

  static AccrualCutoffBookkeepingEntryVariants.AccruedExpense readAccruedExpense(
      ObjectNode rootNode) {
    rejectUnexpectedFields(
        rootNode, null, ProtocolAccrualCutoffPostingRequestFieldSets.accruedExpenseFields());
    return new AccrualCutoffBookkeepingEntryVariants.AccruedExpense(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        accrualCutoffId(rootNode),
        accountCode(rootNode, ProtocolBusinessEventFields.Inventory.EXPENSE_ACCOUNT_CODE),
        accountCode(
            rootNode,
            ProtocolBusinessEventFields.AccrualCutoff.ACCRUED_EXPENSE_LIABILITY_ACCOUNT_CODE),
        CliBookkeepingEntryStructureParser.requiredPositiveAmount(rootNode));
  }

  static AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition readRecognition(
      ObjectNode rootNode) {
    rejectUnexpectedFields(
        rootNode, null, ProtocolAccrualCutoffPostingRequestFieldSets.recognitionFields());
    return new AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        accrualCutoffId(rootNode),
        CliBookkeepingEntryStructureParser.requiredPositiveAmount(rootNode),
        null);
  }

  static AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement readSettlement(
      ObjectNode rootNode) {
    rejectUnexpectedFields(
        rootNode, null, ProtocolAccrualCutoffPostingRequestFieldSets.settlementFields());
    return new AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        accrualCutoffId(rootNode),
        accountCode(rootNode, ProtocolBusinessEventFields.Core.CASH_ACCOUNT_CODE),
        CliBookkeepingEntryStructureParser.requiredPositiveAmount(rootNode),
        null);
  }

  private static AccrualCutoffId accrualCutoffId(ObjectNode rootNode) {
    return new AccrualCutoffId(
        requiredText(rootNode, ProtocolBusinessEventFields.AccrualCutoff.ACCRUAL_CUTOFF_ID));
  }

  private static AccountCode accountCode(ObjectNode rootNode, String fieldName) {
    return new AccountCode(requiredText(rootNode, fieldName));
  }
}
