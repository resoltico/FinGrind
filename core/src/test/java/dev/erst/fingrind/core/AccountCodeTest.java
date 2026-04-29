package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link AccountCode}. */
class AccountCodeTest {
  @Test
  void constructor_trimsValue() {
    AccountCode accountCode = new AccountCode(" 1000 ");

    assertEquals("1000", accountCode.value());
  }

  @Test
  void constructor_rejectsBlankValue() {
    assertThrows(IllegalArgumentException.class, () -> new AccountCode("   "));
  }

  @Test
  void constructor_rejectsOverlongAndInvalidValues() {
    String tooLong = "A" + "1".repeat(AccountCode.maxLength());

    assertEquals("^[A-Za-z0-9](?:[A-Za-z0-9._:/-]{0,254})?$", AccountCode.pattern());
    assertThrows(IllegalArgumentException.class, () -> new AccountCode("cash account"));
    assertThrows(IllegalArgumentException.class, () -> new AccountCode(tooLong));
  }
}
