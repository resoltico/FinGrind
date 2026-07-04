package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.protocol.RequestSurfaceFacts;
import dev.erst.fingrind.contract.protocol.SourceDocumentTypePolicyMode;
import dev.erst.fingrind.core.AccountClassificationReachability;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.SourceDocumentReference;
import dev.erst.fingrind.core.SourceDocumentType;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingEntryModeSemanticsViolations;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingEvidenceSemanticsViolations;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
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
      BookkeepingEntryKind entryKind,
      String selectorField,
      String selectorValue,
      Set<AccountCode> referencedAccounts) {
    if (entryKind != BookkeepingEntryKind.OPENING_POSITION) {
      return;
    }
    AccountCode blockedAccountCode = firstOpeningWindowBlockedAccount(accounts, referencedAccounts);
    if (blockedAccountCode != null) {
      violations.add(
          BookkeepingEntryModeSemanticsViolations.openingWindowAccountNotPermitted(
              selectorField, selectorValue, blockedAccountCode));
    }
  }

  private static @Nullable AccountCode firstOpeningWindowBlockedAccount(
      Map<AccountCode, RegisteredAccount> accounts, Set<AccountCode> referencedAccounts) {
    Iterator<AccountCode> accountCodes = referencedAccounts.iterator();
    AccountCode blockedAccountCode = null;
    while (blockedAccountCode == null && accountCodes.hasNext()) {
      AccountCode accountCode = accountCodes.next();
      RegisteredAccount account = Objects.requireNonNull(accounts.get(accountCode), "account");
      if (!AccountClassificationReachability.openingReachable(account.accountTaxonomy())) {
        blockedAccountCode = accountCode;
      }
    }
    return blockedAccountCode;
  }
}
