package dev.erst.fingrind.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link BookAdministrationRejection}. */
class BookAdministrationRejectionTest {
  @Test
  void wireCode_isStableForEverySubtype() {
    assertEquals(
        List.of(
            "book-already-initialized",
            "book-not-initialized",
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
  void descriptors_guardAgainstUnsupportedSubtypeRequests()
      throws NoSuchMethodException, IllegalAccessException {
    MethodHandle descriptorFor =
        MethodHandles.privateLookupIn(BookAdministrationRejection.class, MethodHandles.lookup())
            .findStatic(
                BookAdministrationRejection.class,
                "descriptorFor",
                MethodType.methodType(MachineContract.RejectionDescriptor.class, Class.class));

    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () -> descriptorFor.invoke(BookAdministrationRejection.class));
    assertEquals(
        "Unsupported book-administration rejection type: "
            + BookAdministrationRejection.class.getName(),
        failure.getMessage());
  }
}
