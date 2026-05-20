package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Cross-platform regression coverage for public-doc fixture canonicalization. */
class CliPublicDocsContractSupportTest extends CliPublicDocsContractSupport {

  @Test
  void canonicalization_normalizesOwnedTemporaryPathsAcrossTextAndJsonFixtures() throws Exception {
    String windowsStyleTempPath = tempDirectory.toAbsolutePath().toString().replace('/', '\\');

    assertEquals(
        "/absolute/path/books/acme.sqlite",
        canonicalizeExampleFixture(windowsStyleTempPath + "\\books\\acme.sqlite"));

    JsonNode jsonFixture =
        OBJECT_MAPPER.readTree(
            """
            {
              "bookFile": "%s\\\\books\\\\acme.sqlite"
            }
            """
                .formatted(jsonEscaped(windowsStyleTempPath)));
    assertEquals(
        "{\"bookFile\":\"/absolute/path/books/acme.sqlite\"}",
        canonicalizeJsonFixture(jsonFixture).toString());
  }

  private static String jsonEscaped(String text) {
    return text.replace("\\", "\\\\");
  }
}
