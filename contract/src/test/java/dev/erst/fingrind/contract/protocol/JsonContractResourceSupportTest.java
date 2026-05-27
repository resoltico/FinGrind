package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Coverage and invariant tests for protocol-owned JSON resource helpers. */
class JsonContractResourceSupportTest {
  @Test
  void loadObject_readsAndRejectsInvalidTopLevelShapes() {
    JsonNode document =
        JsonContractResourceSupport.loadObject(
            new ByteArrayInputStream(
                """
                {"alpha":"one","values":["x","y"]}
                """
                    .getBytes(StandardCharsets.UTF_8)),
            "/valid.json",
            "test contract");
    JsonNode alpha = JsonContractResourceSupport.nullableField(document, "alpha");
    assertEquals("one", JsonContractResourceSupport.requireText(document, "alpha"));
    assertNotNull(alpha);
    assertEquals("one", alpha.asText());
    assertEquals(
        List.of("x", "y"), JsonContractResourceSupport.optionalStringArray(document, "values"));
    assertThrows(
        IllegalStateException.class,
        () -> JsonContractResourceSupport.loadObject(null, "/missing.json", "test contract"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            JsonContractResourceSupport.loadObject(
                new ByteArrayInputStream("[]".getBytes(StandardCharsets.UTF_8)),
                "/array.json",
                "test contract"));
    IllegalArgumentException emptyPayload =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                JsonContractResourceSupport.loadObject(
                    new ByteArrayInputStream(new byte[0]), "/empty.json", "test contract"));
    assertEquals(
        "test contract resource must not be empty: /empty.json", emptyPayload.getMessage());
    IllegalArgumentException nullPayload =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                JsonContractResourceSupport.loadObject(
                    new ByteArrayInputStream("null".getBytes(StandardCharsets.UTF_8)),
                    "/null-top-level.json",
                    "test contract"));
    assertEquals(
        "test contract resource must not be empty: /null-top-level.json", nullPayload.getMessage());
    IllegalArgumentException whitespacePayload =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                JsonContractResourceSupport.loadObject(
                    new ByteArrayInputStream(" \n\t ".getBytes(StandardCharsets.UTF_8)),
                    "/whitespace.json",
                    "test contract"));
    assertEquals(
        "test contract resource must not be empty: /whitespace.json",
        whitespacePayload.getMessage());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            JsonContractResourceSupport.loadObject(
                new ByteArrayInputStream("{".getBytes(StandardCharsets.UTF_8)),
                "/invalid.json",
                "test contract"));
    assertThrows(
        UncheckedIOException.class,
        () ->
            JsonContractResourceSupport.loadObject(
                failingInputStream(), "/broken.json", "test contract"));
  }

  @Test
  void requireObject_rejectsMissingNullAndNonObjectValues() {
    JsonNode valid =
        JsonContractResourceSupport.loadObject(
            new ByteArrayInputStream(
                "{\"details\":{\"alpha\":\"one\"}}".getBytes(StandardCharsets.UTF_8)),
            "/valid-object.json",
            "test contract");
    JsonNode missing =
        JsonContractResourceSupport.loadObject(
            new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)),
            "/missing-object.json",
            "test contract");
    JsonNode withNull =
        JsonContractResourceSupport.loadObject(
            new ByteArrayInputStream("{\"details\":null}".getBytes(StandardCharsets.UTF_8)),
            "/null-object.json",
            "test contract");
    JsonNode withText =
        JsonContractResourceSupport.loadObject(
            new ByteArrayInputStream("{\"details\":\"wrong\"}".getBytes(StandardCharsets.UTF_8)),
            "/text-object.json",
            "test contract");

    assertEquals(
        "one",
        JsonContractResourceSupport.requireText(
            JsonContractResourceSupport.requireObject(
                valid, "details", "details must be an object."),
            "alpha"));
    assertEquals(
        "details must be an object.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    JsonContractResourceSupport.requireObject(
                        missing, "details", "details must be an object."))
            .getMessage());
    assertEquals(
        "details must be an object.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    JsonContractResourceSupport.requireObject(
                        withNull, "details", "details must be an object."))
            .getMessage());
    assertEquals(
        "details must be an object.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    JsonContractResourceSupport.requireObject(
                        withText, "details", "details must be an object."))
            .getMessage());
  }

  @Test
  void requireText_rejectsMissingNullNonTextAndBlankValues() {
    JsonNode missing =
        JsonContractResourceSupport.loadObject(
            new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)),
            "/missing.json",
            "test contract");
    JsonNode withNull =
        JsonContractResourceSupport.loadObject(
            new ByteArrayInputStream("{\"alpha\":null}".getBytes(StandardCharsets.UTF_8)),
            "/null.json",
            "test contract");
    JsonNode withNumber =
        JsonContractResourceSupport.loadObject(
            new ByteArrayInputStream("{\"alpha\":1}".getBytes(StandardCharsets.UTF_8)),
            "/number.json",
            "test contract");
    JsonNode withBlank =
        JsonContractResourceSupport.loadObject(
            new ByteArrayInputStream("{\"alpha\":\"   \"}".getBytes(StandardCharsets.UTF_8)),
            "/blank.json",
            "test contract");
    assertThrows(
        IllegalArgumentException.class,
        () -> JsonContractResourceSupport.requireText(missing, "alpha"));
    assertThrows(
        IllegalArgumentException.class,
        () -> JsonContractResourceSupport.requireText(withNull, "alpha"));
    assertThrows(
        IllegalArgumentException.class,
        () -> JsonContractResourceSupport.requireText(withNumber, "alpha"));
    assertThrows(
        IllegalArgumentException.class,
        () -> JsonContractResourceSupport.requireText(withBlank, "alpha"));
  }

  @Test
  void optionalStringArray_rejectsWrongShapesAndBlankMembers() {
    JsonNode missing =
        JsonContractResourceSupport.loadObject(
            new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)),
            "/missing-array.json",
            "test contract");
    JsonNode wrongType =
        JsonContractResourceSupport.loadObject(
            new ByteArrayInputStream("{\"values\":1}".getBytes(StandardCharsets.UTF_8)),
            "/wrong-type.json",
            "test contract");
    JsonNode wrongElementType =
        JsonContractResourceSupport.loadObject(
            new ByteArrayInputStream("{\"values\":[1]}".getBytes(StandardCharsets.UTF_8)),
            "/wrong-element.json",
            "test contract");
    JsonNode blankElement =
        JsonContractResourceSupport.loadObject(
            new ByteArrayInputStream("{\"values\":[\" \"]}".getBytes(StandardCharsets.UTF_8)),
            "/blank-element.json",
            "test contract");
    JsonNode nullValue =
        JsonContractResourceSupport.loadObject(
            new ByteArrayInputStream("{\"values\":null}".getBytes(StandardCharsets.UTF_8)),
            "/null-array.json",
            "test contract");
    assertEquals(List.of(), JsonContractResourceSupport.optionalStringArray(missing, "values"));
    assertEquals(List.of(), JsonContractResourceSupport.optionalStringArray(nullValue, "values"));
    assertThrows(
        IllegalArgumentException.class,
        () -> JsonContractResourceSupport.optionalStringArray(wrongType, "values"));
    assertThrows(
        IllegalArgumentException.class,
        () -> JsonContractResourceSupport.optionalStringArray(wrongElementType, "values"));
    assertThrows(
        IllegalArgumentException.class,
        () -> JsonContractResourceSupport.optionalStringArray(blankElement, "values"));
  }

  @Test
  void requireStringArray_rejectsMissingNullWrongShapesAndBlankMembers() {
    JsonNode missing =
        JsonContractResourceSupport.loadObject(
            new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)),
            "/missing-required-array.json",
            "test contract");
    JsonNode wrongType =
        JsonContractResourceSupport.loadObject(
            new ByteArrayInputStream("{\"values\":1}".getBytes(StandardCharsets.UTF_8)),
            "/wrong-required-type.json",
            "test contract");
    JsonNode wrongElementType =
        JsonContractResourceSupport.loadObject(
            new ByteArrayInputStream("{\"values\":[1]}".getBytes(StandardCharsets.UTF_8)),
            "/wrong-required-element.json",
            "test contract");
    JsonNode blankElement =
        JsonContractResourceSupport.loadObject(
            new ByteArrayInputStream("{\"values\":[\" \"]}".getBytes(StandardCharsets.UTF_8)),
            "/blank-required-element.json",
            "test contract");
    JsonNode nullValue =
        JsonContractResourceSupport.loadObject(
            new ByteArrayInputStream("{\"values\":null}".getBytes(StandardCharsets.UTF_8)),
            "/null-required-array.json",
            "test contract");
    assertThrows(
        IllegalArgumentException.class,
        () -> JsonContractResourceSupport.requireStringArray(missing, "values"));
    assertThrows(
        IllegalArgumentException.class,
        () -> JsonContractResourceSupport.requireStringArray(nullValue, "values"));
    assertThrows(
        IllegalArgumentException.class,
        () -> JsonContractResourceSupport.requireStringArray(wrongType, "values"));
    assertThrows(
        IllegalArgumentException.class,
        () -> JsonContractResourceSupport.requireStringArray(wrongElementType, "values"));
    assertThrows(
        IllegalArgumentException.class,
        () -> JsonContractResourceSupport.requireStringArray(blankElement, "values"));
  }

  @Test
  void requireBooleanAndRequireInt_rejectWrongShapesAndReturnTypedValues() {
    JsonNode valid =
        JsonContractResourceSupport.loadObject(
            new ByteArrayInputStream(
                "{\"flag\":true,\"count\":7}".getBytes(StandardCharsets.UTF_8)),
            "/valid-scalars.json",
            "test contract");
    JsonNode missing =
        JsonContractResourceSupport.loadObject(
            new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)),
            "/missing-scalars.json",
            "test contract");
    JsonNode withNulls =
        JsonContractResourceSupport.loadObject(
            new ByteArrayInputStream(
                "{\"flag\":null,\"count\":null}".getBytes(StandardCharsets.UTF_8)),
            "/null-scalars.json",
            "test contract");
    JsonNode withWrongTypes =
        JsonContractResourceSupport.loadObject(
            new ByteArrayInputStream(
                "{\"flag\":\"true\",\"count\":\"7\"}".getBytes(StandardCharsets.UTF_8)),
            "/wrong-scalars.json",
            "test contract");
    assertTrue(JsonContractResourceSupport.requireBoolean(valid, "flag"));
    assertEquals(7, JsonContractResourceSupport.requireInt(valid, "count"));
    assertThrows(
        IllegalArgumentException.class,
        () -> JsonContractResourceSupport.requireBoolean(missing, "flag"));
    assertThrows(
        IllegalArgumentException.class,
        () -> JsonContractResourceSupport.requireBoolean(withNulls, "flag"));
    assertThrows(
        IllegalArgumentException.class,
        () -> JsonContractResourceSupport.requireBoolean(withWrongTypes, "flag"));
    assertThrows(
        IllegalArgumentException.class,
        () -> JsonContractResourceSupport.requireInt(missing, "count"));
    assertThrows(
        IllegalArgumentException.class,
        () -> JsonContractResourceSupport.requireInt(withNulls, "count"));
    assertThrows(
        IllegalArgumentException.class,
        () -> JsonContractResourceSupport.requireInt(withWrongTypes, "count"));
  }

  private static InputStream failingInputStream() {
    return new InputStream() {
      @Override
      public int read() throws IOException {
        throw new IOException("boom");
      }

      @Override
      public int read(byte[] buffer, int offset, int length) throws IOException {
        throw new IOException("boom");
      }
    };
  }
}
