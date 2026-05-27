package dev.erst.fingrind.contract.protocol;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Coverage and invariant tests for core-owned book-model facts. */
class BookModelFactsTest {
  @Test
  void convenienceConstructorPublishesCanonicalAccessors() {
    BookModelFacts facts =
        new BookModelFacts(
            "single protected book",
            "single entity",
            "sqlite file",
            "book key",
            "open book required",
            "declared accounts",
            "single functional currency");

    assertEquals("single protected book", facts.boundary());
    assertEquals("single entity", facts.entityScope());
    assertEquals("sqlite file", facts.filesystem());
    assertEquals("book key", facts.credential());
    assertEquals("open book required", facts.initialization());
    assertEquals("declared accounts", facts.accountRegistry());
    assertEquals("single functional currency", facts.currencyScope());
  }

  @Test
  void recordConstructorRejectsMissingStructuredFacts() {
    assertThrows(
        NullPointerException.class,
        () ->
            new BookModelFacts(
                nullOf(BookBoundaryFact.class),
                new BookEntityScopeFact("entity"),
                new BookFilesystemFact("filesystem"),
                new BookCredentialFact("credential"),
                new BookInitializationFact("initialization"),
                new BookAccountRegistryFact("registry"),
                new BookCurrencyScopeFact("currency")));
  }
}
