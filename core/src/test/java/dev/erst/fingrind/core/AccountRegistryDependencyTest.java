package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link AccountRegistryDependency}. */
class AccountRegistryDependencyTest {
  @Test
  void wireVocabulary_isStable() {
    assertEquals(
        List.of("postings", "tax-registrations", "child-accounts"),
        Arrays.stream(AccountRegistryDependency.values())
            .map(AccountRegistryDependency::wireValue)
            .toList());
  }
}
