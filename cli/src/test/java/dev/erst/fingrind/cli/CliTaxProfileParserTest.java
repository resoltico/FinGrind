package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.TaxProfile;
import dev.erst.fingrind.core.TaxRecoverability;
import dev.erst.fingrind.core.TaxRegistrationStatus;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

/** Unit tests for structured tax-profile parsing on open-book surfaces. */
class CliTaxProfileParserTest extends CliIoFixtureSupport {
  @Test
  void readOpenBookTaxProfile_acceptsNullCollectionsAndOptionalAbsenceWhenUnregistered()
      throws Exception {
    ObjectNode openBookWithNullCollections =
        parseObject(
            """
            {
              "taxProfile": {
                "registrations": null,
                "taxCodeDefinitions": null
              }
            }
            """);
    ObjectNode openBookWithoutTaxProfile = parseObject("{}");

    TaxProfile nullCollectionProfile =
        CliTaxProfileParser.readOpenBookTaxProfile(
            openBookWithNullCollections, TaxRegistrationStatus.NOT_REGISTERED);
    TaxProfile absentProfile =
        CliTaxProfileParser.readOpenBookTaxProfile(
            openBookWithoutTaxProfile, TaxRegistrationStatus.UNSPECIFIED);

    assertEquals(TaxProfile.empty(), nullCollectionProfile);
    assertEquals(TaxProfile.empty(), absentProfile);

    ObjectNode openBookWithMissingRegistrations =
        parseObject(
            """
            {
              "taxProfile": {
                "taxCodeDefinitions": []
              }
            }
            """);
    TaxProfile missingRegistrationsProfile =
        CliTaxProfileParser.readOpenBookTaxProfile(
            openBookWithMissingRegistrations, TaxRegistrationStatus.UNSPECIFIED);

    assertEquals(TaxProfile.empty(), missingRegistrationsProfile);
  }

  @Test
  void readOpenBookTaxProfile_rejectsRegisteredBooksWithoutRegistrationsOrProfile()
      throws Exception {
    ObjectNode openBookWithoutTaxProfile = parseObject("{}");
    ObjectNode openBookWithoutRegistrations =
        parseObject(
            """
            {
              "taxProfile": {
                "registrations": [],
                "taxCodeDefinitions": []
              }
            }
            """);

    IllegalArgumentException missingProfile =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                CliTaxProfileParser.readOpenBookTaxProfile(
                    openBookWithoutTaxProfile, TaxRegistrationStatus.REGISTERED));
    IllegalArgumentException missingRegistrations =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                CliTaxProfileParser.readOpenBookTaxProfile(
                    openBookWithoutRegistrations, TaxRegistrationStatus.REGISTERED));

    assertEquals("Registered tax status requires taxProfile details.", missingProfile.getMessage());
    assertEquals(
        "taxProfile.registrations must declare at least one registration when taxRegistrationStatus is REGISTERED.",
        missingRegistrations.getMessage());

    ObjectNode openBookWithTaxCodeOnly =
        parseObject(
            """
            {
              "taxProfile": {
                "taxCodeDefinitions": [
                  {
                    "taxCode": "VAT21",
                    "displayName": "Standard VAT",
                    "jurisdictionCode": "LV",
                    "rateBasisPoints": 2100,
                    "pricingMode": "EXCLUSIVE",
                    "recoverability": "FULLY_RECOVERABLE",
                    "liabilityAccountCode": "2100"
                  }
                ]
              }
            }
            """);

    IllegalArgumentException incompatibleUnregistered =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                CliTaxProfileParser.readOpenBookTaxProfile(
                    openBookWithTaxCodeOnly, TaxRegistrationStatus.NOT_REGISTERED));

    assertEquals(
        "taxProfile requires taxRegistrationStatus REGISTERED.",
        incompatibleUnregistered.getMessage());
  }

  @Test
  void readTaxProfileFile_rejectsOperatorAndPayloadFailures() throws Exception {
    Path duplicateKeys =
        writeNamedRequest(
            "duplicate-tax-profile.json",
            """
            {
              "registrations": [],
              "registrations": []
            }
            """);
    Path invalidJson = writeNamedRequest("invalid-tax-profile.json", "{\"registrations\": [");
    Path oversizedPayload =
        writeNamedRequest(
            "oversized-tax-profile.json",
            "{\"registrations\":[{\"jurisdictionCode\":\"" + "X".repeat(1_100_000) + "\"}]}");
    Path incompatibleStatus =
        writeNamedRequest(
            "incompatible-tax-profile.json",
            """
            {
              "registrations": [
                {
                  "jurisdictionCode": "LV",
                  "registrationId": "LV123456789",
                  "filingFrequency": "MONTHLY"
                }
              ]
            }
            """);
    Path unexpectedField =
        writeNamedRequest(
            "unexpected-tax-profile.json",
            """
            {
              "registrations": [],
              "unexpectedField": true
            }
            """);

    CliArgumentsException stdinToken =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliTaxProfileParser.readTaxProfileFile(
                    Path.of("-"), TaxRegistrationStatus.NOT_REGISTERED));
    CliArgumentsException unreadableFile =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliTaxProfileParser.readTaxProfileFile(
                    tempDirectory.resolve("missing-tax-profile.json"),
                    TaxRegistrationStatus.NOT_REGISTERED));
    CliArgumentsException duplicateKeysFailure =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliTaxProfileParser.readTaxProfileFile(
                    duplicateKeys, TaxRegistrationStatus.NOT_REGISTERED));
    CliArgumentsException invalidJsonFailure =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliTaxProfileParser.readTaxProfileFile(
                    invalidJson, TaxRegistrationStatus.NOT_REGISTERED));
    CliArgumentsException oversizedPayloadFailure =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliTaxProfileParser.readTaxProfileFile(
                    oversizedPayload, TaxRegistrationStatus.NOT_REGISTERED));
    CliArgumentsException incompatibleStatusFailure =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliTaxProfileParser.readTaxProfileFile(
                    incompatibleStatus, TaxRegistrationStatus.NOT_REGISTERED));
    CliArgumentsException unexpectedFieldFailure =
        assertThrows(
            CliArgumentsException.class,
            () ->
                CliTaxProfileParser.readTaxProfileFile(
                    unexpectedField, TaxRegistrationStatus.REGISTERED));

    assertEquals(
        "--tax-profile-file must point to one readable JSON file.", stdinToken.getMessage());
    assertEquals("--tax-profile-file", unreadableFile.argument());
    String unreadableMessage = Objects.requireNonNull(unreadableFile.getMessage());
    assertTrue(
        unreadableMessage.contains(
            "Failed to read --tax-profile-file: "
                + tempDirectory.resolve("missing-tax-profile.json").toAbsolutePath().normalize()
                + "."));
    assertEquals(
        "--tax-profile-file must not contain duplicate object keys.",
        duplicateKeysFailure.getMessage());
    assertEquals(
        "Failed to parse --tax-profile-file as one JSON object.", invalidJsonFailure.getMessage());
    String oversizedCauseMessage =
        Objects.requireNonNull(
            assertInstanceOf(IOException.class, oversizedPayloadFailure.getCause()).getMessage());
    assertTrue(oversizedCauseMessage.contains("supported size limit"));
    assertEquals(
        "taxProfile requires taxRegistrationStatus REGISTERED.",
        incompatibleStatusFailure.getMessage());
    assertTrue(
        Objects.requireNonNull(unexpectedFieldFailure.getMessage()).contains("unexpectedField"));
  }

  @Test
  void readTaxProfileFile_readsStructuredRegistrationsAndDefinitions() throws Exception {
    Path taxProfileFile =
        writeNamedRequest(
            "structured-tax-profile.json",
            """
            {
              "registrations": [
                {
                  "jurisdictionCode": "LV",
                  "registrationId": "LV123456789",
                  "filingFrequency": "MONTHLY"
                }
              ],
              "taxCodeDefinitions": [
                {
                  "taxCode": "VAT21",
                  "displayName": "Standard VAT",
                  "jurisdictionCode": "LV",
                  "rateBasisPoints": 2100,
                  "pricingMode": "EXCLUSIVE",
                  "recoverability": "FULLY_RECOVERABLE",
                  "liabilityAccountCode": "2100",
                  "receivableAccountCode": "1300"
                }
              ]
            }
            """);

    TaxProfile taxProfile =
        CliTaxProfileParser.readTaxProfileFile(taxProfileFile, TaxRegistrationStatus.REGISTERED);

    assertEquals("LV", taxProfile.registrations().getFirst().jurisdictionCode().value());
    assertEquals("VAT21", taxProfile.taxCodeDefinitions().getFirst().taxCode().value());
    assertEquals(
        TaxRecoverability.FULLY_RECOVERABLE,
        taxProfile.taxCodeDefinitions().getFirst().recoverability());
    assertEquals(
        "1300",
        taxProfile.taxCodeDefinitions().getFirst().receivableAccountCode().orElseThrow().value());
  }

  private static ObjectNode parseObject(String json) throws IOException {
    return CliJsonFieldAccess.requireRootObject(
        CliJsonObjectMappers.configuredObjectMapper().readTree(json));
  }
}
