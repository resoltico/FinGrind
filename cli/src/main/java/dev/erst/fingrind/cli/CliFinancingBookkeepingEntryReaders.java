package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliJsonFieldAccess.requiredText;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.rejectUnexpectedFields;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.FinancingArrangementId;
import dev.erst.fingrind.contract.bookkeeping.FinancingBookkeepingEntryVariants;
import dev.erst.fingrind.contract.protocol.ProtocolBusinessEventFields;
import dev.erst.fingrind.contract.protocol.ProtocolFinancingPostingRequestFieldSets;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import tools.jackson.databind.node.ObjectNode;

/** Reads typed request payloads owned by the Financing context. */
final class CliFinancingBookkeepingEntryReaders {
  private CliFinancingBookkeepingEntryReaders() {}

  static BookkeepingEntry read(ObjectNode rootNode, BookkeepingEntryKind entryKind) {
    return switch (entryKind) {
      case FINANCING_BORROWING -> readBorrowing(rootNode);
      case FINANCING_PRINCIPAL_REPAYMENT -> readPrincipalRepayment(rootNode);
      case FINANCING_INTEREST_ACCRUAL -> readInterestAccrual(rootNode);
      case FINANCING_INTEREST_PAYMENT -> readInterestPayment(rootNode);
      default -> throw new IllegalArgumentException("Expected a financing entry kind.");
    };
  }

  static FinancingBookkeepingEntryVariants.Borrowing readBorrowing(ObjectNode rootNode) {
    rejectUnexpectedFields(
        rootNode, null, ProtocolFinancingPostingRequestFieldSets.borrowingFields());
    return new FinancingBookkeepingEntryVariants.Borrowing(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        financingArrangementId(rootNode),
        accountCode(rootNode, ProtocolBusinessEventFields.Core.CASH_ACCOUNT_CODE),
        accountCode(
            rootNode, ProtocolBusinessEventFields.Financing.PRINCIPAL_LIABILITY_ACCOUNT_CODE),
        accountCode(rootNode, ProtocolBusinessEventFields.Financing.INTEREST_PAYABLE_ACCOUNT_CODE),
        CliBookkeepingEntryStructureParser.requiredPositiveAmount(
            rootNode, ProtocolBusinessEventFields.Financing.PRINCIPAL_AMOUNT));
  }

  static FinancingBookkeepingEntryVariants.PrincipalRepayment readPrincipalRepayment(
      ObjectNode rootNode) {
    rejectUnexpectedFields(
        rootNode, null, ProtocolFinancingPostingRequestFieldSets.principalRepaymentFields());
    return new FinancingBookkeepingEntryVariants.PrincipalRepayment(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        financingArrangementId(rootNode),
        accountCode(rootNode, ProtocolBusinessEventFields.Core.CASH_ACCOUNT_CODE),
        CliBookkeepingEntryStructureParser.requiredPositiveAmount(
            rootNode, ProtocolBusinessEventFields.Financing.PRINCIPAL_AMOUNT),
        null);
  }

  static FinancingBookkeepingEntryVariants.InterestAccrual readInterestAccrual(
      ObjectNode rootNode) {
    rejectUnexpectedFields(
        rootNode, null, ProtocolFinancingPostingRequestFieldSets.interestAccrualFields());
    return new FinancingBookkeepingEntryVariants.InterestAccrual(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        financingArrangementId(rootNode),
        accountCode(rootNode, ProtocolBusinessEventFields.Financing.INTEREST_EXPENSE_ACCOUNT_CODE),
        CliBookkeepingEntryStructureParser.requiredPositiveAmount(
            rootNode, ProtocolBusinessEventFields.Financing.INTEREST_AMOUNT),
        null);
  }

  static FinancingBookkeepingEntryVariants.InterestPayment readInterestPayment(
      ObjectNode rootNode) {
    rejectUnexpectedFields(
        rootNode, null, ProtocolFinancingPostingRequestFieldSets.interestPaymentFields());
    return new FinancingBookkeepingEntryVariants.InterestPayment(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        financingArrangementId(rootNode),
        accountCode(rootNode, ProtocolBusinessEventFields.Core.CASH_ACCOUNT_CODE),
        CliBookkeepingEntryStructureParser.requiredPositiveAmount(
            rootNode, ProtocolBusinessEventFields.Financing.INTEREST_AMOUNT),
        null);
  }

  private static FinancingArrangementId financingArrangementId(ObjectNode rootNode) {
    return new FinancingArrangementId(
        requiredText(rootNode, ProtocolBusinessEventFields.Financing.FINANCING_ARRANGEMENT_ID));
  }

  private static AccountCode accountCode(ObjectNode rootNode, String fieldName) {
    return new AccountCode(requiredText(rootNode, fieldName));
  }
}
