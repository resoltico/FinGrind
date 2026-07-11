package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.protocol.RequestSurfaceFacts;
import dev.erst.fingrind.contract.protocol.SourceDocumentTypePolicyMode;
import dev.erst.fingrind.core.AccountClassificationReachability;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.SourceDocumentReference;
import dev.erst.fingrind.core.SourceDocumentType;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingEntryModeSemanticsViolations;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingEvidenceSemanticsViolations;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.InventoryEntrySemanticsViolations;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Shared request-surface guards for published post-entry validation. */
final class PostEntryRequestValidationSupport {
  private PostEntryRequestValidationSupport() {}

  static void requireSourceDocumentTypes(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      String selectorField,
      String selectorValue,
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
          BookkeepingEvidenceSemanticsViolations.sourceDocumentTypeNotAccepted(
              selectorField, selectorValue, sourceDocumentType, acceptedTypes));
    }
  }

  static void requireOpeningWindowAccounts(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      BookkeepingEntry entry,
      String selectorField,
      String selectorValue,
      Set<AccountCode> referencedAccounts) {
    if (!(entry instanceof BookkeepingEntry.OpeningPosition openingPosition)) {
      return;
    }
    for (BookkeepingEntry.OpeningPosition.OpeningAccountBalance balance :
        openingPosition.balances()) {
      RegisteredAccount account =
          Objects.requireNonNull(accounts.get(balance.accountCode()), "account");
      boolean inventory =
          account.accountTaxonomy().financialPositionLineClassification().orElse(null)
              == FinancialPositionLineClassification.INVENTORY;
      if (inventory && balance.quantity() == null) {
        violations.add(
            InventoryEntrySemanticsViolations.openingInventoryRequiresQuantity(
                selectorField, selectorValue, balance.accountCode()));
      }
      if (!inventory && balance.quantity() != null) {
        violations.add(
            InventoryEntrySemanticsViolations.openingQuantityRequiresInventory(
                selectorField, selectorValue, balance.accountCode()));
      }
    }
    AccountCode blockedAccountCode = firstOpeningWindowBlockedAccount(accounts, referencedAccounts);
    if (blockedAccountCode != null) {
      violations.add(
          BookkeepingEntryModeSemanticsViolations.openingWindowAccountNotPermitted(
              selectorField, selectorValue, blockedAccountCode));
    }
  }

  static BookkeepingPostingRejection.@Nullable EntrySemanticsViolation rawJournalTouchesInventory(
      Map<AccountCode, RegisteredAccount> accounts,
      BookkeepingEntryKind entryKind,
      String selectorField,
      String selectorValue,
      Set<AccountCode> referencedAccounts) {
    if (entryKind != BookkeepingEntryKind.DIRECT_JOURNAL) {
      return null;
    }
    AccountCode inventoryAccountCode = firstInventoryAccount(accounts, referencedAccounts);
    if (inventoryAccountCode != null) {
      return InventoryEntrySemanticsViolations.rawJournalTouchesInventory(
          selectorField, selectorValue, inventoryAccountCode);
    }
    return null;
  }

  private static @Nullable AccountCode firstOpeningWindowBlockedAccount(
      Map<AccountCode, RegisteredAccount> accounts, Set<AccountCode> referencedAccounts) {
    Iterator<AccountCode> accountCodes = referencedAccounts.iterator();
    AccountCode blockedAccountCode = null;
    while (blockedAccountCode == null && accountCodes.hasNext()) {
      AccountCode accountCode = accountCodes.next();
      RegisteredAccount account = Objects.requireNonNull(accounts.get(accountCode), "account");
      if (account.accountTaxonomy().financialPositionLineClassification().orElse(null)
          == FinancialPositionLineClassification.INVENTORY) {
        continue;
      }
      if (!AccountClassificationReachability.openingReachable(account.accountTaxonomy())) {
        blockedAccountCode = accountCode;
      }
    }
    return blockedAccountCode;
  }

  private static @Nullable AccountCode firstInventoryAccount(
      Map<AccountCode, RegisteredAccount> accounts, Set<AccountCode> referencedAccounts) {
    for (AccountCode accountCode : referencedAccounts) {
      RegisteredAccount account = Objects.requireNonNull(accounts.get(accountCode), "account");
      if (AccountRole.from(account.accountType(), account.accountTaxonomy())
          == AccountRole.INVENTORY) {
        return accountCode;
      }
    }
    return null;
  }
}
