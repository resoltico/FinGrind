package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Cross-platform regression coverage for public-doc fixture canonicalization. */
class CliPublicDocsContractSupportTest extends CliPublicDocsContractSupport {

  @Test
  void canonicalization_preservesRedactedPublicPathHintsAcrossTextAndJsonFixtures()
      throws Exception {

    assertEquals(
        "<redacted>/books/acme.sqlite",
        canonicalizeExampleFixture("<redacted>\\books\\acme.sqlite"));

    JsonNode jsonFixture =
        OBJECT_MAPPER.readTree(
            """
            {
              "bookFile": "<redacted>\\\\books\\\\acme.sqlite"
            }
            """);
    assertEquals(
        "{\"bookFile\":\"<redacted>/books/acme.sqlite\"}",
        canonicalizeJsonFixture(jsonFixture).toString());
  }
}
