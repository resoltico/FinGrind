package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PercentageRate;
import dev.erst.fingrind.core.TaxCode;
import dev.erst.fingrind.core.TaxCodeDefinition;
import dev.erst.fingrind.core.TaxCodeName;
import dev.erst.fingrind.core.TaxFilingFrequency;
import dev.erst.fingrind.core.TaxJurisdictionCode;
import dev.erst.fingrind.core.TaxPricingMode;
import dev.erst.fingrind.core.TaxProfile;
import dev.erst.fingrind.core.TaxRecoverability;
import dev.erst.fingrind.core.TaxRegistration;
import dev.erst.fingrind.core.TaxRegistrationId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Direct proof for persisted tax-profile metadata encoding and validation. */
class SqliteTaxProfileCodecTest {
  @Test
  void encodeAndDecode_roundTripStructuredTaxProfiles() {
    TaxProfile taxProfile =
        new TaxProfile(
            List.of(
                new TaxRegistration(
                    new TaxJurisdictionCode("LV"),
                    new TaxRegistrationId("LV123456789"),
                    TaxFilingFrequency.MONTHLY)),
            List.of(
                new TaxCodeDefinition(
                    new TaxCode("VAT21"),
                    new TaxCodeName("Standard VAT"),
                    new TaxJurisdictionCode("LV"),
                    new PercentageRate(2_100),
                    TaxPricingMode.EXCLUSIVE,
                    TaxRecoverability.FULLY_RECOVERABLE,
                    new AccountCode("2330"),
                    Optional.empty()),
                new TaxCodeDefinition(
                    new TaxCode("VAT0"),
                    new TaxCodeName("Zero VAT"),
                    new TaxJurisdictionCode("LV"),
                    new PercentageRate(0),
                    TaxPricingMode.EXCLUSIVE,
                    TaxRecoverability.NON_RECOVERABLE,
                    new AccountCode("2331"),
                    Optional.of(new AccountCode("1410")))));

    assertEquals(
        taxProfile, SqliteTaxProfileCodec.decode(SqliteTaxProfileCodec.encode(taxProfile)));
  }

  @Test
  void encode_wrapsMapperWriteFailures() {
    ObjectMapper failingMapper =
        new ObjectMapper() {
          @Override
          public String writeValueAsString(Object value) {
            throw new IllegalStateException("boom");
          }
        };

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteTaxProfileCodec.encode(TaxProfile.empty(), failingMapper));

    assertEquals("Failed to encode SQLite tax-profile metadata.", exception.getMessage());
    assertEquals("boom", NullTestSupport.messageOf(NullTestSupport.causeOf(exception)));
  }

  @Test
  void decode_wrapsMapperParseFailures() {
    ObjectMapper failingMapper =
        new ObjectMapper() {
          @Override
          public JsonNode readTree(String content) {
            throw new IllegalStateException("boom");
          }
        };

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class, () -> SqliteTaxProfileCodec.decode("{}", failingMapper));

    assertEquals("Failed to parse SQLite tax-profile metadata.", exception.getMessage());
    assertEquals("boom", NullTestSupport.messageOf(NullTestSupport.causeOf(exception)));
  }

  @Test
  void decode_rejectsNonObjectTaxProfiles() {
    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> SqliteTaxProfileCodec.decode("[]"));

    assertEquals(
        "SQLite tax-profile metadata field taxProfile must be an object.", exception.getMessage());
  }

  @Test
  void decode_rejectsMissingArraySections() {
    IllegalStateException registrationsException =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteTaxProfileCodec.decode("{\"registrations\":{},\"taxCodeDefinitions\":[]}"));
    assertEquals(
        "SQLite tax-profile metadata field registrations must be an array.",
        registrationsException.getMessage());

    IllegalStateException definitionsException =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteTaxProfileCodec.decode("{\"registrations\":[],\"taxCodeDefinitions\":{}}"));
    assertEquals(
        "SQLite tax-profile metadata field taxCodeDefinitions must be an array.",
        definitionsException.getMessage());

    IllegalStateException missingDefinitionsException =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteTaxProfileCodec.decode("{\"registrations\":[]}"));
    assertEquals(
        "SQLite tax-profile metadata field taxCodeDefinitions must be an array.",
        missingDefinitionsException.getMessage());

    IllegalStateException missingRegistrationsException =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteTaxProfileCodec.decode("{\"taxCodeDefinitions\":[]}"));
    assertEquals(
        "SQLite tax-profile metadata field registrations must be an array.",
        missingRegistrationsException.getMessage());
  }

  @Test
  void decode_rejectsNonObjectRegistrationRows() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteTaxProfileCodec.decode("{\"registrations\":[1],\"taxCodeDefinitions\":[]}"));

    assertEquals(
        "SQLite tax-profile metadata field registrations[0] must be an object.",
        exception.getMessage());
  }

  @Test
  void requireObjectNode_rejectsJavaNullNodesExplicitly() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteTaxProfileCodec.requireObjectNode(
                    NullTestSupport.nullOf(JsonNode.class), "taxProfile"));

    assertEquals(
        "SQLite tax-profile metadata field taxProfile must be an object.", exception.getMessage());
  }

  @Test
  void decode_rejectsNonTextRegistrationFields() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteTaxProfileCodec.decode(
                    """
                    {
                      "registrations": [
                        {
                          "jurisdictionCode": 5,
                          "registrationId": "LV123456789",
                          "filingFrequency": "monthly"
                        }
                      ],
                      "taxCodeDefinitions": []
                    }
                    """));

    assertEquals(
        "SQLite tax-profile metadata field jurisdictionCode must be text.", exception.getMessage());

    IllegalStateException missingFieldException =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteTaxProfileCodec.decode(
                    """
                    {
                      "registrations": [
                        {
                          "registrationId": "LV123456789",
                          "filingFrequency": "MONTHLY"
                        }
                      ],
                      "taxCodeDefinitions": []
                    }
                    """));
    assertEquals(
        "SQLite tax-profile metadata field jurisdictionCode must be text.",
        missingFieldException.getMessage());
  }

  @Test
  void decode_rejectsNonIntegerDefinitionRate() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteTaxProfileCodec.decode(
                    """
                    {
                      "registrations": [],
                      "taxCodeDefinitions": [
                        {
                          "taxCode": "VAT21",
                          "displayName": "Standard VAT",
                          "jurisdictionCode": "LV",
                          "rateBasisPoints": "2100",
                          "pricingMode": "exclusive",
                          "recoverability": "fully-recoverable",
                          "liabilityAccountCode": "2330"
                        }
                      ]
                    }
                    """));

    assertEquals(
        "SQLite tax-profile metadata field rateBasisPoints must be an integer.",
        exception.getMessage());

    IllegalStateException missingRateException =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteTaxProfileCodec.decode(
                    """
                    {
                      "registrations": [],
                      "taxCodeDefinitions": [
                        {
                          "taxCode": "VAT21",
                          "displayName": "Standard VAT",
                          "jurisdictionCode": "LV",
                          "pricingMode": "EXCLUSIVE",
                          "recoverability": "FULLY_RECOVERABLE",
                          "liabilityAccountCode": "2330"
                        }
                      ]
                    }
                    """));
    assertEquals(
        "SQLite tax-profile metadata field rateBasisPoints must be an integer.",
        missingRateException.getMessage());
  }

  @Test
  void decode_acceptsNullReceivableAccountCodesAsAbsent() {
    TaxProfile taxProfile =
        SqliteTaxProfileCodec.decode(
            """
            {
              "registrations": [],
              "taxCodeDefinitions": [
                {
                  "taxCode": "VAT21",
                  "displayName": "Standard VAT",
                  "jurisdictionCode": "LV",
                  "rateBasisPoints": 2100,
                  "pricingMode": "EXCLUSIVE",
                  "recoverability": "FULLY_RECOVERABLE",
                  "liabilityAccountCode": "2330",
                  "receivableAccountCode": null
                }
              ]
            }
            """);

    assertEquals(
        Optional.empty(), taxProfile.taxCodeDefinitions().getFirst().receivableAccountCode());
  }

  @Test
  void decode_rejectsUnexpectedFieldsAtAnyLevel() {
    IllegalStateException rootException =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteTaxProfileCodec.decode(
                    "{\"registrations\":[],\"taxCodeDefinitions\":[],\"extra\":true}"));
    assertEquals(
        "SQLite tax-profile metadata field taxProfile.extra is unsupported.",
        rootException.getMessage());

    IllegalStateException nestedException =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteTaxProfileCodec.decode(
                    """
                    {
                      "registrations": [],
                      "taxCodeDefinitions": [
                        {
                          "taxCode": "VAT21",
                          "displayName": "Standard VAT",
                          "jurisdictionCode": "LV",
                          "rateBasisPoints": 2100,
                          "pricingMode": "exclusive",
                          "recoverability": "fully-recoverable",
                          "liabilityAccountCode": "2330",
                          "unsupported": true
                        }
                      ]
                    }
                    """));
    assertEquals(
        "SQLite tax-profile metadata field taxCodeDefinitions[0].unsupported is unsupported.",
        nestedException.getMessage());
  }

  @Test
  void decode_rejectsNonTextReceivableAccountWhenPresent() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteTaxProfileCodec.decode(
                    """
                    {
                      "registrations": [],
                      "taxCodeDefinitions": [
                        {
                          "taxCode": "VAT21",
                          "displayName": "Standard VAT",
                          "jurisdictionCode": "LV",
                          "rateBasisPoints": 2100,
                          "pricingMode": "EXCLUSIVE",
                          "recoverability": "FULLY_RECOVERABLE",
                          "liabilityAccountCode": "2330",
                          "receivableAccountCode": 5
                        }
                      ]
                    }
                    """));

    assertTrue(
        NullTestSupport.messageOf(exception)
            .contains(
                "SQLite tax-profile metadata field receivableAccountCode must be text when present."));
  }
}
