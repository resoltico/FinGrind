package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.JournalRecipe;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.RequestSurfaceFacts;
import dev.erst.fingrind.contract.protocol.SourceDocumentTypePolicyMode;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookkeepingEntryKind;
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
  private static final RequestSurfaceFacts REQUEST_SURFACE =
      ProtocolCatalog.domain().requestSurface();

  private PostEntrySemanticsPolicy() {}

  static PostEntrySemanticsPolicy currentKernel() {
    return CURRENT_KERNEL;
  }

  Optional<BookkeepingPostingRejection> rejectionFor(
      PostEntryCommand command, PostingValidationStore book) {
    Objects.requireNonNull(command, "command");
    Objects.requireNonNull(book, "book");
    List<BookkeepingPostingRejection.EntrySemanticsViolation> violations = new ArrayList<>();
    RequestSurfaceFacts.SourceDocumentTypeFacts sourceDocumentTypeFacts =
        REQUEST_SURFACE.evidenceProfile(evidenceProfileId(command.entry())).sourceDocumentTypes();
    Map<AccountCode, RegisteredAccount> accounts =
        book.findAccounts(referencedAccounts(command.entry()));
    switch (command.entry()) {
      case BookkeepingEntry.Journal journal -> {
        JournalRecipe recipe = journal.recipe();
        if (recipe != null) {
          validateJournalRecipe(violations, accounts, recipe);
        } else {
          requireEconomicAccountMovement(
              violations, journal.entryKind().wireValue(), journal.lines());
        }
      }
      case BookkeepingEntry.OpenAccountingPosition _ -> {}
      case BookkeepingEntry.ReversalAdjustment _ -> {}
    }
    requireSourceDocumentTypes(
        violations,
        entryLabel(command.entry()),
        sourceDocumentTypeFacts,
        command.evidence().sourceDocuments());
    if (violations.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new BookkeepingPostingRejection.EntrySemanticsViolations(violations));
  }

  private static void validateJournalRecipe(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      JournalRecipe recipe) {
    String entryLabel = recipe.recipeKind().wireValue();
    switch (recipe) {
      case JournalRecipe.CashRevenue event -> {
        requireDistinctRoleAccounts(
            violations,
            entryLabel,
            event.cashAccountCode(),
            "cashAccountCode",
            event.revenueAccountCode(),
            "revenueAccountCode");
        requireAccountType(
            violations,
            entryLabel,
            accounts,
            event.cashAccountCode(),
            "cashAccountCode",
            AccountType.ASSET);
        requireAccountType(
            violations,
            entryLabel,
            accounts,
            event.revenueAccountCode(),
            "revenueAccountCode",
            AccountType.REVENUE);
      }
      case JournalRecipe.CashExpense event -> {
        requireDistinctRoleAccounts(
            violations,
            entryLabel,
            event.expenseAccountCode(),
            "expenseAccountCode",
            event.cashAccountCode(),
            "cashAccountCode");
        requireAccountType(
            violations,
            entryLabel,
            accounts,
            event.expenseAccountCode(),
            "expenseAccountCode",
            AccountType.EXPENSE);
        requireAccountType(
            violations,
            entryLabel,
            accounts,
            event.cashAccountCode(),
            "cashAccountCode",
            AccountType.ASSET);
      }
      case JournalRecipe.EquityContribution event -> {
        requireDistinctRoleAccounts(
            violations,
            entryLabel,
            event.cashAccountCode(),
            "cashAccountCode",
            event.equityAccountCode(),
            "equityAccountCode");
        requireAccountType(
            violations,
            entryLabel,
            accounts,
            event.cashAccountCode(),
            "cashAccountCode",
            AccountType.ASSET);
        requireAccountType(
            violations,
            entryLabel,
            accounts,
            event.equityAccountCode(),
            "equityAccountCode",
            AccountType.EQUITY);
        requireFinancialPositionClassification(
            violations,
            entryLabel,
            accounts,
            event.equityAccountCode(),
            "equityAccountCode",
            FinancialPositionLineClassification.EQUITY_CONTRIBUTION);
      }
      case JournalRecipe.EquityWithdrawal event -> {
        requireDistinctRoleAccounts(
            violations,
            entryLabel,
            event.equityAccountCode(),
            "equityAccountCode",
            event.cashAccountCode(),
            "cashAccountCode");
        requireAccountType(
            violations,
            entryLabel,
            accounts,
            event.equityAccountCode(),
            "equityAccountCode",
            AccountType.EQUITY);
        requireFinancialPositionClassification(
            violations,
            entryLabel,
            accounts,
            event.equityAccountCode(),
            "equityAccountCode",
            FinancialPositionLineClassification.EQUITY_WITHDRAWAL);
        requireAccountType(
            violations,
            entryLabel,
            accounts,
            event.cashAccountCode(),
            "cashAccountCode",
            AccountType.ASSET);
      }
    }
  }

  private static Set<AccountCode> referencedAccounts(BookkeepingEntry entry) {
    return switch (Objects.requireNonNull(entry, "entry")) {
      case BookkeepingEntry.Journal journal -> referencedAccounts(journal.lines());
      case BookkeepingEntry.OpenAccountingPosition openingPosition ->
          referencedAccounts(openingPosition.lines());
      case BookkeepingEntry.ReversalAdjustment reversalAdjustment ->
          referencedAccounts(reversalAdjustment.lines());
    };
  }

  private static Set<AccountCode> referencedAccounts(
      List<dev.erst.fingrind.core.JournalLine> lines) {
    return lines.stream()
        .map(dev.erst.fingrind.core.JournalLine::accountCode)
        .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
  }

  private static void requireDistinctRoleAccounts(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      String entryLabel,
      AccountCode firstAccountCode,
      String firstField,
      AccountCode secondAccountCode,
      String secondField) {
    if (!firstAccountCode.equals(secondAccountCode)) {
      return;
    }
    violations.add(
        BookkeepingPostingRejection.distinctRoleAccountsRequired(
            entryLabel, firstField, secondField, firstAccountCode));
  }

  private static void requireEconomicAccountMovement(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      String entryLabel,
      List<dev.erst.fingrind.core.JournalLine> lines) {
    if (!JournalEconomicMovement.isEconomicallyNull(lines)) {
      return;
    }
    violations.add(BookkeepingPostingRejection.economicNullJournal(entryLabel));
  }

  private static void requireAccountType(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      String entryLabel,
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
            entryLabel, field, accountCode, expectedAccountType, account.accountType()));
  }

  private static void requireFinancialPositionClassification(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      String entryLabel,
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
            entryLabel, field, accountCode, expectedClassification, actualClassification));
  }

  private static String evidenceProfileId(BookkeepingEntry entry) {
    return switch (Objects.requireNonNull(entry, "entry")) {
      case BookkeepingEntry.Journal journal -> journalEvidenceProfileId(journal);
      case BookkeepingEntry.OpenAccountingPosition _ ->
          REQUEST_SURFACE
              .postEntryKind(BookkeepingEntryKind.OPEN_ACCOUNTING_POSITION)
              .evidenceProfileId();
      case BookkeepingEntry.ReversalAdjustment _ ->
          REQUEST_SURFACE
              .postEntryKind(BookkeepingEntryKind.REVERSAL_ADJUSTMENT)
              .evidenceProfileId();
    };
  }

  private static String entryLabel(BookkeepingEntry entry) {
    Objects.requireNonNull(entry, "entry");
    if (entry instanceof BookkeepingEntry.Journal journal) {
      return journalEntryLabel(journal);
    }
    return entry.entryKind().wireValue();
  }

  private static String journalEvidenceProfileId(BookkeepingEntry.Journal journal) {
    JournalRecipe recipe = journal.recipe();
    if (recipe == null) {
      return REQUEST_SURFACE.postEntryKind(BookkeepingEntryKind.JOURNAL).evidenceProfileId();
    }
    return REQUEST_SURFACE.journalRecipe(recipe.recipeKind()).evidenceProfileId();
  }

  private static String journalEntryLabel(BookkeepingEntry.Journal journal) {
    JournalRecipe recipe = journal.recipe();
    return recipe == null ? journal.entryKind().wireValue() : recipe.recipeKind().wireValue();
  }

  private static void requireSourceDocumentTypes(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      String entryLabel,
      RequestSurfaceFacts.SourceDocumentTypeFacts sourceDocumentTypeFacts,
      List<SourceDocumentReference> sourceDocuments) {
    if (sourceDocumentTypeFacts.mode() != SourceDocumentTypePolicyMode.ENUMERATED) {
      return;
    }
    List<String> acceptedTypes = sourceDocumentTypeFacts.acceptedValues();
    for (SourceDocumentReference sourceDocument : sourceDocuments) {
      SourceDocumentType sourceDocumentType = sourceDocument.sourceDocumentType();
      if (acceptedTypes.contains(sourceDocumentType.value())) {
        continue;
      }
      violations.add(
          BookkeepingPostingRejection.sourceDocumentTypeNotAccepted(
              entryLabel, sourceDocumentType, acceptedTypes));
    }
  }
}
