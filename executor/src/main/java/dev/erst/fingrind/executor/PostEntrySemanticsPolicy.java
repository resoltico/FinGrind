package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.SourceDocumentReference;
import dev.erst.fingrind.core.SourceDocumentType;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Application-boundary semantic validation for the published post-entry command surface. */
final class PostEntrySemanticsPolicy {
  private static final PostEntrySemanticsPolicy CURRENT_KERNEL = new PostEntrySemanticsPolicy();
  private static final List<String> CASH_REVENUE_SOURCE_DOCUMENT_TYPES =
      List.of("cash-receipt", "bank-deposit", "card-settlement");
  private static final List<String> CASH_EXPENSE_SOURCE_DOCUMENT_TYPES =
      List.of("expense-receipt", "cash-disbursement", "bank-payment-confirmation");
  private static final List<String> EQUITY_CONTRIBUTION_SOURCE_DOCUMENT_TYPES =
      List.of("equity-contribution", "capital-deposit", "bank-deposit");
  private static final List<String> EQUITY_WITHDRAWAL_SOURCE_DOCUMENT_TYPES =
      List.of("equity-withdrawal", "distribution-payment", "bank-payment-confirmation");

  private PostEntrySemanticsPolicy() {}

  static PostEntrySemanticsPolicy currentKernel() {
    return CURRENT_KERNEL;
  }

  Optional<BookkeepingPostingRejection> rejectionFor(
      PostEntryCommand command, PostingValidationStore book) {
    Objects.requireNonNull(command, "command");
    Objects.requireNonNull(book, "book");
    List<BookkeepingPostingRejection.EntrySemanticsViolation> violations = new ArrayList<>();
    Map<AccountCode, RegisteredAccount> accounts =
        book.findAccounts(referencedAccounts(command.entry()));
    switch (command.entry()) {
      case BookkeepingEntry.CashRevenue event -> {
        requireAccountType(
            violations,
            command,
            accounts,
            event.cashAccountCode(),
            "cashAccountCode",
            AccountType.ASSET);
        requireAccountType(
            violations,
            command,
            accounts,
            event.revenueAccountCode(),
            "revenueAccountCode",
            AccountType.REVENUE);
        requireSourceDocumentTypes(
            violations,
            command,
            CASH_REVENUE_SOURCE_DOCUMENT_TYPES,
            command.evidence().sourceDocuments());
      }
      case BookkeepingEntry.CashExpense event -> {
        requireAccountType(
            violations,
            command,
            accounts,
            event.expenseAccountCode(),
            "expenseAccountCode",
            AccountType.EXPENSE);
        requireAccountType(
            violations,
            command,
            accounts,
            event.cashAccountCode(),
            "cashAccountCode",
            AccountType.ASSET);
        requireSourceDocumentTypes(
            violations,
            command,
            CASH_EXPENSE_SOURCE_DOCUMENT_TYPES,
            command.evidence().sourceDocuments());
      }
      case BookkeepingEntry.EquityContribution event -> {
        requireAccountType(
            violations,
            command,
            accounts,
            event.cashAccountCode(),
            "cashAccountCode",
            AccountType.ASSET);
        requireAccountType(
            violations,
            command,
            accounts,
            event.equityAccountCode(),
            "equityAccountCode",
            AccountType.EQUITY);
        requireFinancialPositionClassification(
            violations,
            command,
            accounts,
            event.equityAccountCode(),
            "equityAccountCode",
            FinancialPositionLineClassification.EQUITY_CONTRIBUTION);
        requireSourceDocumentTypes(
            violations,
            command,
            EQUITY_CONTRIBUTION_SOURCE_DOCUMENT_TYPES,
            command.evidence().sourceDocuments());
      }
      case BookkeepingEntry.EquityWithdrawal event -> {
        requireAccountType(
            violations,
            command,
            accounts,
            event.equityAccountCode(),
            "equityAccountCode",
            AccountType.EQUITY);
        requireFinancialPositionClassification(
            violations,
            command,
            accounts,
            event.equityAccountCode(),
            "equityAccountCode",
            FinancialPositionLineClassification.EQUITY_WITHDRAWAL);
        requireAccountType(
            violations,
            command,
            accounts,
            event.cashAccountCode(),
            "cashAccountCode",
            AccountType.ASSET);
        requireSourceDocumentTypes(
            violations,
            command,
            EQUITY_WITHDRAWAL_SOURCE_DOCUMENT_TYPES,
            command.evidence().sourceDocuments());
      }
      case BookkeepingEntry.OpeningBalanceAdjustment _ -> {}
      case BookkeepingEntry.CorrectionAdjustment _ -> {}
      case BookkeepingEntry.ReversalAdjustment _ -> {}
    }
    if (violations.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new BookkeepingPostingRejection.EntrySemanticsViolations(violations));
  }

  private static Set<AccountCode> referencedAccounts(BookkeepingEntry entry) {
    return switch (Objects.requireNonNull(entry, "entry")) {
      case BookkeepingEntry.CashRevenue event ->
          Set.of(event.cashAccountCode(), event.revenueAccountCode());
      case BookkeepingEntry.CashExpense event ->
          Set.of(event.expenseAccountCode(), event.cashAccountCode());
      case BookkeepingEntry.EquityContribution event ->
          Set.of(event.cashAccountCode(), event.equityAccountCode());
      case BookkeepingEntry.EquityWithdrawal event ->
          Set.of(event.equityAccountCode(), event.cashAccountCode());
      case BookkeepingEntry.OpeningBalanceAdjustment _ -> Set.of();
      case BookkeepingEntry.CorrectionAdjustment _ -> Set.of();
      case BookkeepingEntry.ReversalAdjustment _ -> Set.of();
    };
  }

  private static void requireAccountType(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      PostEntryCommand command,
      Map<AccountCode, RegisteredAccount> accounts,
      AccountCode accountCode,
      String field,
      AccountType expectedAccountType) {
    RegisteredAccount account = accounts.get(accountCode);
    if (account == null || account.accountType() == expectedAccountType) {
      return;
    }
    violations.add(
        BookkeepingPostingRejection.accountTypeMismatch(
            command.entry().entryKind(),
            field,
            accountCode,
            expectedAccountType,
            account.accountType()));
  }

  private static void requireFinancialPositionClassification(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      PostEntryCommand command,
      Map<AccountCode, RegisteredAccount> accounts,
      AccountCode accountCode,
      String field,
      FinancialPositionLineClassification expectedClassification) {
    RegisteredAccount account = accounts.get(accountCode);
    if (account == null) {
      return;
    }
    FinancialPositionLineClassification actualClassification =
        account.accountTaxonomy().financialPositionLineClassification().orElse(null);
    if (actualClassification == expectedClassification) {
      return;
    }
    violations.add(
        BookkeepingPostingRejection.financialPositionClassificationMismatch(
            command.entry().entryKind(),
            field,
            accountCode,
            expectedClassification,
            actualClassification));
  }

  private static void requireSourceDocumentTypes(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      PostEntryCommand command,
      List<String> acceptedTypes,
      List<SourceDocumentReference> sourceDocuments) {
    for (SourceDocumentReference sourceDocument : sourceDocuments) {
      SourceDocumentType sourceDocumentType = sourceDocument.sourceDocumentType();
      if (acceptedTypes.contains(sourceDocumentType.value())) {
        continue;
      }
      violations.add(
          BookkeepingPostingRejection.sourceDocumentTypeNotAccepted(
              command.entry().entryKind(), sourceDocumentType, acceptedTypes));
    }
  }
}
