package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.runtime.ContractResponse;
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
            "account-type-conflict",
            "account-role-conflict",
            "retained-earnings-account-missing",
            "retained-earnings-account-inactive",
            "period-close-must-start-at"),
        List.of(
            BookAdministrationRejection.wireCode(
                new BookAdministrationRejection.BookAlreadyInitialized()),
            BookAdministrationRejection.wireCode(
                new BookAdministrationRejection.BookNotInitialized()),
            BookAdministrationRejection.wireCode(
                new BookAdministrationRejection.BookContainsSchema()),
            BookAdministrationRejection.wireCode(
                new BookAdministrationRejection.AccountTypeConflict(
                    new dev.erst.fingrind.core.AccountCode("1000"),
                    dev.erst.fingrind.core.AccountType.ASSET,
                    dev.erst.fingrind.core.AccountType.EXPENSE)),
            BookAdministrationRejection.wireCode(
                new BookAdministrationRejection.AccountRoleConflict(
                    new dev.erst.fingrind.core.AccountCode("1000"),
                    dev.erst.fingrind.core.AccountRole.ORDINARY,
                    dev.erst.fingrind.core.AccountRole.CONTRA)),
            BookAdministrationRejection.wireCode(
                new BookAdministrationRejection.RetainedEarningsAccountMissing()),
            BookAdministrationRejection.wireCode(
                new BookAdministrationRejection.RetainedEarningsAccountInactive(
                    new dev.erst.fingrind.core.AccountCode("3000"))),
            BookAdministrationRejection.wireCode(
                new BookAdministrationRejection.PeriodCloseMustStartAt(
                    java.time.LocalDate.parse("2026-04-01")))));
  }

  @Test
  void descriptors_areStableAndComplete() {
    assertEquals(
        List.of(
            "book-already-initialized",
            "administration-book-not-initialized",
            "book-contains-schema",
            "account-type-conflict",
            "account-role-conflict",
            "retained-earnings-account-missing",
            "retained-earnings-account-inactive",
            "period-close-must-start-at"),
        BookAdministrationRejection.descriptors().stream()
            .map(ContractResponse.RejectionDescriptor::code)
            .toList());
  }

  @Test
  void bookNotInitializedCode_matchesTheCanonicalDescriptor() {
    assertEquals(
        BookAdministrationRejection.wireCode(new BookAdministrationRejection.BookNotInitialized()),
        BookAdministrationRejection.bookNotInitializedCode());
  }
}
