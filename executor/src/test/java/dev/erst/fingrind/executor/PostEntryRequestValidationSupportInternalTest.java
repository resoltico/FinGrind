package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.account;
import static dev.erst.fingrind.executor.PostEntrySemanticsPolicyTestSupport.inventoryAssetAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.InventoryEntrySemanticsViolations;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Narrow internal-branch coverage for package-private request-validation helpers. */
class PostEntryRequestValidationSupportInternalTest {
  @Test
  void firstOpeningWindowBlockedAccount_skipsInventoryBeforeNominalBlock() {
    MethodHandle firstOpeningWindowBlockedAccount =
        privateStatic(
            "firstOpeningWindowBlockedAccount",
            MethodType.methodType(AccountCode.class, Map.class, Set.class));
    Map<AccountCode, RegisteredAccount> accounts =
        Map.of(
            new AccountCode("1400"),
            inventoryAssetAccount("1400"),
            new AccountCode("2000"),
            account("2000", AccountType.REVENUE));
    Set<AccountCode> referencedAccounts =
        new LinkedHashSet<>(List.of(new AccountCode("1400"), new AccountCode("2000")));

    assertEquals(
        new AccountCode("2000"),
        invokeAccountCode(firstOpeningWindowBlockedAccount, accounts, referencedAccounts));
  }

  @Test
  void requireOpeningWindowAccounts_rejectsBothMissingInventoryAndExtraneousQuantity() {
    AccountCode inventory = new AccountCode("1400");
    AccountCode cash = new AccountCode("1000");
    List<BookkeepingPostingRejection.EntrySemanticsViolation> violations = new ArrayList<>();
    Map<AccountCode, RegisteredAccount> accounts =
        Map.of(inventory, inventoryAssetAccount("1400"), cash, account("1000", AccountType.ASSET));
    BookkeepingEntry.OpeningPosition openingPosition =
        new BookkeepingEntry.OpeningPosition(
            LocalDate.parse("2026-04-07"),
            List.of(
                new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                    inventory,
                    JournalLine.EntrySide.DEBIT,
                    new MonetaryAmount("EUR", "1000"),
                    null),
                new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                    cash,
                    JournalLine.EntrySide.CREDIT,
                    new MonetaryAmount("EUR", "1000"),
                    new dev.erst.fingrind.contract.bookkeeping.QuantityText("1"))));

    PostEntryRequestValidationSupport.requireOpeningWindowAccounts(
        violations, accounts, openingPosition, "entryKind", "OPENING_POSITION", Set.of());

    assertEquals(
        List.of(
            InventoryEntrySemanticsViolations.openingInventoryRequiresQuantity(
                "entryKind", "OPENING_POSITION", inventory),
            InventoryEntrySemanticsViolations.openingQuantityRequiresInventory(
                "entryKind", "OPENING_POSITION", cash)),
        violations);
  }

  private static MethodHandle privateStatic(String name, MethodType type) {
    try {
      MethodHandles.Lookup lookup =
          MethodHandles.privateLookupIn(
              PostEntryRequestValidationSupport.class, MethodHandles.lookup());
      return lookup.findStatic(PostEntryRequestValidationSupport.class, name, type);
    } catch (ReflectiveOperationException exception) {
      throw new LinkageError(exception.getMessage(), exception);
    }
  }

  private static AccountCode invokeAccountCode(
      MethodHandle handle, Map<AccountCode, RegisteredAccount> accounts, Set<AccountCode> codes) {
    try {
      return (AccountCode) handle.invokeWithArguments(accounts, codes);
    } catch (RuntimeException runtimeException) {
      throw runtimeException;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new LinkageError(throwable.getMessage(), throwable);
    }
  }
}
