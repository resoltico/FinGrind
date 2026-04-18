package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link BookAdministrationRejection}. */
class BookAdministrationRejectionTest {
  @Test
  void wireCode_isStableForEverySubtype() {
    assertEquals(
        List.of(
            "book-already-initialized",
            "administration-book-not-initialized",
            "book-contains-schema",
            "account-normal-balance-conflict"),
        List.of(
            BookAdministrationRejection.wireCode(
                new BookAdministrationRejection.BookAlreadyInitialized()),
            BookAdministrationRejection.wireCode(
                new BookAdministrationRejection.BookNotInitialized()),
            BookAdministrationRejection.wireCode(
                new BookAdministrationRejection.BookContainsSchema()),
            BookAdministrationRejection.wireCode(
                new BookAdministrationRejection.NormalBalanceConflict(
                    new dev.erst.fingrind.core.AccountCode("1000"),
                    dev.erst.fingrind.core.NormalBalance.DEBIT,
                    dev.erst.fingrind.core.NormalBalance.CREDIT))));
  }

  @Test
  void descriptors_areStableAndComplete() {
    assertEquals(
        List.of(
            "book-already-initialized",
            "administration-book-not-initialized",
            "book-contains-schema",
            "account-normal-balance-conflict"),
        BookAdministrationRejection.descriptors().stream()
            .map(ContractResponse.RejectionDescriptor::code)
            .toList());
  }
}
