package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Rejects postings that would create or deepen one negative inventory carrying balance. */
final class NonNegativeInventoryBalancePolicy {
  Optional<BookkeepingPostingRejection> rejectionFor(
      PostingRequestModel postingRequest,
      Map<AccountCode, RegisteredAccount> declaredAccounts,
      PostingValidationStore book) {
    Objects.requireNonNull(postingRequest, "postingRequest");
    Objects.requireNonNull(declaredAccounts, "declaredAccounts");
    Objects.requireNonNull(book, "book");
    List<BookkeepingPostingRejection.AccountStateViolation> violations = new ArrayList<>();
    CurrencyUnit currencyUnit = postingRequest.journalEntry().currencyUnit();
    String field = field(postingRequest);
    for (AccountCode accountCode : touchedInventoryAccounts(postingRequest, declaredAccounts)) {
      long currentSignedMinorUnits = currentSignedMinorUnits(accountCode, postingRequest, book);
      long requestedSignedMinorUnits = requestedSignedMinorUnits(accountCode, postingRequest);
      long currentNegativeFloor = Math.min(currentSignedMinorUnits, 0L);
      long resultingSignedMinorUnits =
          Math.addExact(currentSignedMinorUnits, requestedSignedMinorUnits);
      if (resultingSignedMinorUnits >= currentNegativeFloor) {
        continue;
      }
      violations.add(
          new InventoryBalanceBelowZeroViolation(
              accountCode,
              field,
              postingRequest.journalEntry().effectiveDate(),
              signedBalanceSide(currentSignedMinorUnits),
              absoluteMoney(currencyUnit, currentSignedMinorUnits),
              absoluteMoney(currencyUnit, requestedSignedMinorUnits),
              absoluteMoney(currencyUnit, resultingSignedMinorUnits)));
    }
    return violations.isEmpty()
        ? Optional.empty()
        : Optional.of(
            new BookkeepingPostingRejection.AccountStateViolations(List.copyOf(violations)));
  }

  private static List<AccountCode> touchedInventoryAccounts(
      PostingRequestModel postingRequest, Map<AccountCode, RegisteredAccount> declaredAccounts) {
    Set<AccountCode> accountCodes = new LinkedHashSet<>();
    for (JournalLine line : postingRequest.journalEntry().lines()) {
      RegisteredAccount account = declaredAccounts.get(line.accountCode());
      if (account != null && inventoryAccount(account)) {
        accountCodes.add(line.accountCode());
      }
    }
    return List.copyOf(accountCodes);
  }

  private static boolean inventoryAccount(RegisteredAccount account) {
    return AccountRole.from(account.accountType(), account.accountTaxonomy())
        == AccountRole.INVENTORY;
  }

  private static long currentSignedMinorUnits(
      AccountCode accountCode, PostingRequestModel postingRequest, PostingValidationStore book) {
    long signedMinorUnits = 0L;
    for (CommittedPosting posting :
        book.postings(EffectiveDateRange.to(postingRequest.journalEntry().effectiveDate()))) {
      for (JournalLine line : posting.journalEntry().lines()) {
        if (accountCode.equals(line.accountCode())) {
          signedMinorUnits = Math.addExact(signedMinorUnits, signedMinorUnits(line));
        }
      }
    }
    return signedMinorUnits;
  }

  private static long requestedSignedMinorUnits(
      AccountCode accountCode, PostingRequestModel postingRequest) {
    long signedMinorUnits = 0L;
    for (JournalLine line : postingRequest.journalEntry().lines()) {
      if (accountCode.equals(line.accountCode())) {
        signedMinorUnits = Math.addExact(signedMinorUnits, signedMinorUnits(line));
      }
    }
    return signedMinorUnits;
  }

  private static long signedMinorUnits(JournalLine line) {
    long minorUnits = line.amount().minorUnits();
    return line.side() == JournalLine.EntrySide.DEBIT ? minorUnits : Math.negateExact(minorUnits);
  }

  private static BalanceSide signedBalanceSide(long signedMinorUnits) {
    if (signedMinorUnits > 0L) {
      return BalanceSide.DEBIT;
    }
    if (signedMinorUnits < 0L) {
      return BalanceSide.CREDIT;
    }
    return BalanceSide.ZERO;
  }

  private static Money absoluteMoney(CurrencyUnit currencyUnit, long signedMinorUnits) {
    return Money.ofMinorUnits(
        currencyUnit,
        signedMinorUnits >= 0L ? signedMinorUnits : Math.negateExact(signedMinorUnits));
  }

  private static String field(PostingRequestModel postingRequest) {
    return postingRequest
        .callerAuthoredEntry()
        .map(NonNegativeInventoryBalancePolicy::field)
        .orElse("lines[].amount");
  }

  private static String field(BookkeepingEntry entry) {
    if (entry instanceof BookkeepingEntry.SaleSettled) {
      return "inventoryRelief.amount";
    }
    if (entry instanceof BookkeepingEntry.SaleOnCredit) {
      return "inventoryRelief.amount";
    }
    if (entry instanceof BookkeepingEntry.OpeningPosition) {
      return "openingBalances[].amount";
    }
    if (entry instanceof BookkeepingEntry.Reversal) {
      return "reversal.priorPostingId";
    }
    return "lines[].amount";
  }
}
