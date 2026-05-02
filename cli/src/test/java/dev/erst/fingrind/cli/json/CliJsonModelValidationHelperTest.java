package dev.erst.fingrind.cli.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Pins helper branches that the CLI JSON transport models rely on for normalization. */
class CliJsonModelValidationHelperTest {
  @Test
  void helperBranches_coverNullAndFailingNumericCases() {
    assertEquals(List.of(), CliJsonModelValidation.copyList(null));
    assertNull(CliJsonModelValidation.requireOptionalText(null, "hint"));
    assertThrows(
        IllegalArgumentException.class,
        () -> CliJsonModelValidation.requireText("   ", "fieldName"));
    assertThrows(
        IllegalArgumentException.class, () -> CliJsonModelValidation.requirePositive(0, "limit"));
    assertThrows(
        IllegalArgumentException.class,
        () -> CliJsonModelValidation.requireNonNegative(-1, "offset"));
  }
}
