package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pins helper branches that the CLI JSON transport models rely on for normalization. */
class CliJsonModelValidationHelperTest {
  @Test
  void helperBranches_coverNullAndFailingNumericCases() {
    assertEquals(
        "values must not be null.",
        assertThrows(
                NullPointerException.class,
                () -> CliJsonModelValidation.copyList(nullOf(), "values"))
            .getMessage());
    assertEquals(
        "values[1] must not be null.",
        assertThrows(
                IllegalArgumentException.class,
                () -> CliJsonModelValidation.copyList(Arrays.asList("alpha", nullOf()), "values"))
            .getMessage());
    assertEquals(List.of("alpha"), CliJsonModelValidation.copyList(List.of("alpha"), "values"));
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
