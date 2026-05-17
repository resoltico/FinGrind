package dev.erst.fingrind.sqlite;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Strict SQLite codec for persisted tax-profile metadata. */
final class SqliteTaxProfileCodec {
  private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();
  private static final Set<String> TAX_PROFILE_FIELDS =
      Set.of("registrations", "taxCodeDefinitions");
  private static final Set<String> TAX_REGISTRATION_FIELDS =
      Set.of("jurisdictionCode", "registrationId", "filingFrequency");
  private static final Set<String> TAX_CODE_DEFINITION_FIELDS =
      Set.of(
          "taxCode",
          "displayName",
          "jurisdictionCode",
          "rateBasisPoints",
          "pricingMode",
          "recoverability",
          "liabilityAccountCode",
          "receivableAccountCode");

  private SqliteTaxProfileCodec() {}

  static String encode(TaxProfile taxProfile) {
    return encode(taxProfile, OBJECT_MAPPER);
  }

  static String encode(TaxProfile taxProfile, ObjectMapper objectMapper) {
    Objects.requireNonNull(taxProfile, "taxProfile");
    Objects.requireNonNull(objectMapper, "objectMapper");
    ObjectNode root = objectMapper.createObjectNode();
    ArrayNode registrationsNode = root.putArray("registrations");
    for (TaxRegistration registration : taxProfile.registrations()) {
      ObjectNode registrationNode = registrationsNode.addObject();
      registrationNode.put("jurisdictionCode", registration.jurisdictionCode().value());
      registrationNode.put("registrationId", registration.registrationId().value());
      registrationNode.put("filingFrequency", registration.filingFrequency().wireValue());
    }
    ArrayNode definitionsNode = root.putArray("taxCodeDefinitions");
    for (TaxCodeDefinition definition : taxProfile.taxCodeDefinitions()) {
      ObjectNode definitionNode = definitionsNode.addObject();
      definitionNode.put("taxCode", definition.taxCode().value());
      definitionNode.put("displayName", definition.displayName().value());
      definitionNode.put("jurisdictionCode", definition.jurisdictionCode().value());
      definitionNode.put("rateBasisPoints", definition.rate().basisPoints());
      definitionNode.put("pricingMode", definition.pricingMode().wireValue());
      definitionNode.put("recoverability", definition.recoverability().wireValue());
      definitionNode.put("liabilityAccountCode", definition.liabilityAccountCode().value());
      definition
          .receivableAccountCode()
          .ifPresent(
              receivableAccountCode ->
                  definitionNode.put("receivableAccountCode", receivableAccountCode.value()));
    }
    try {
      return objectMapper.writeValueAsString(root);
    } catch (RuntimeException exception) {
      throw new IllegalStateException("Failed to encode SQLite tax-profile metadata.", exception);
    }
  }

  static TaxProfile decode(String encodedTaxProfile) {
    return decode(encodedTaxProfile, OBJECT_MAPPER);
  }

  static TaxProfile decode(String encodedTaxProfile, ObjectMapper objectMapper) {
    Objects.requireNonNull(objectMapper, "objectMapper");
    JsonNode rootNode;
    try {
      rootNode = objectMapper.readTree(encodedTaxProfile);
    } catch (RuntimeException exception) {
      throw new IllegalStateException("Failed to parse SQLite tax-profile metadata.", exception);
    }
    ObjectNode rootObject = requireObjectNode(rootNode, "taxProfile");
    rejectUnexpectedFields(rootObject, "taxProfile", TAX_PROFILE_FIELDS);
    return new TaxProfile(
        readRegistrations(requiredArray(rootObject, "registrations")),
        readTaxCodeDefinitions(requiredArray(rootObject, "taxCodeDefinitions")));
  }

  private static List<TaxRegistration> readRegistrations(JsonNode registrationsNode) {
    List<TaxRegistration> registrations = new ArrayList<>();
    int index = 0;
    for (JsonNode registrationNode : registrationsNode) {
      ObjectNode registrationObject =
          requireObjectNode(registrationNode, "registrations[%d]".formatted(index));
      rejectUnexpectedFields(
          registrationObject, "registrations[%d]".formatted(index), TAX_REGISTRATION_FIELDS);
      registrations.add(
          new TaxRegistration(
              new TaxJurisdictionCode(requiredText(registrationObject, "jurisdictionCode")),
              new TaxRegistrationId(requiredText(registrationObject, "registrationId")),
              TaxFilingFrequency.fromWireValue(
                  requiredText(registrationObject, "filingFrequency"))));
      index++;
    }
    return List.copyOf(registrations);
  }

  private static List<TaxCodeDefinition> readTaxCodeDefinitions(JsonNode definitionsNode) {
    List<TaxCodeDefinition> definitions = new ArrayList<>();
    int index = 0;
    for (JsonNode definitionNode : definitionsNode) {
      ObjectNode definitionObject =
          requireObjectNode(definitionNode, "taxCodeDefinitions[%d]".formatted(index));
      rejectUnexpectedFields(
          definitionObject, "taxCodeDefinitions[%d]".formatted(index), TAX_CODE_DEFINITION_FIELDS);
      definitions.add(
          new TaxCodeDefinition(
              new TaxCode(requiredText(definitionObject, "taxCode")),
              new TaxCodeName(requiredText(definitionObject, "displayName")),
              new TaxJurisdictionCode(requiredText(definitionObject, "jurisdictionCode")),
              new PercentageRate(requiredInt(definitionObject, "rateBasisPoints")),
              TaxPricingMode.fromWireValue(requiredText(definitionObject, "pricingMode")),
              TaxRecoverability.fromWireValue(requiredText(definitionObject, "recoverability")),
              new AccountCode(requiredText(definitionObject, "liabilityAccountCode")),
              optionalText(definitionObject, "receivableAccountCode").map(AccountCode::new)));
      index++;
    }
    return List.copyOf(definitions);
  }

  /** Same-package seam for strict object-node validation in persisted tax-profile tests. */
  static ObjectNode requireObjectNode(JsonNode node, String label) {
    if (node == null) {
      throw new IllegalStateException(
          "SQLite tax-profile metadata field " + label + " must be an object.");
    }
    if (!node.isObject()) {
      throw new IllegalStateException(
          "SQLite tax-profile metadata field " + label + " must be an object.");
    }
    return (ObjectNode) node;
  }

  private static JsonNode requiredArray(ObjectNode objectNode, String fieldName) {
    JsonNode value = objectNode.get(fieldName);
    if (value == null) {
      throw new IllegalStateException(
          "SQLite tax-profile metadata field " + fieldName + " must be an array.");
    }
    if (!value.isArray()) {
      throw new IllegalStateException(
          "SQLite tax-profile metadata field " + fieldName + " must be an array.");
    }
    return value;
  }

  private static String requiredText(ObjectNode objectNode, String fieldName) {
    JsonNode value = objectNode.get(fieldName);
    Optional<String> textValue = value == null ? Optional.empty() : value.stringValueOpt();
    if (textValue.isEmpty()) {
      throw new IllegalStateException(
          "SQLite tax-profile metadata field " + fieldName + " must be text.");
    }
    return textValue.orElseThrow();
  }

  private static int requiredInt(ObjectNode objectNode, String fieldName) {
    JsonNode value = objectNode.get(fieldName);
    OptionalInt intValue =
        value == null || !value.isIntegralNumber() ? OptionalInt.empty() : value.intValueOpt();
    if (intValue.isEmpty()) {
      throw new IllegalStateException(
          "SQLite tax-profile metadata field " + fieldName + " must be an integer.");
    }
    return intValue.orElseThrow();
  }

  private static Optional<String> optionalText(ObjectNode objectNode, String fieldName) {
    JsonNode value = objectNode.get(fieldName);
    if (value == null || value.isNull()) {
      return Optional.empty();
    }
    Optional<String> textValue = value.stringValueOpt();
    if (textValue.isEmpty()) {
      throw new IllegalStateException(
          "SQLite tax-profile metadata field " + fieldName + " must be text when present.");
    }
    return textValue;
  }

  private static void rejectUnexpectedFields(
      ObjectNode objectNode, String label, Set<String> expectedFields) {
    objectNode
        .propertyStream()
        .forEach(
            entry -> {
              if (!expectedFields.contains(entry.getKey())) {
                throw new IllegalStateException(
                    "SQLite tax-profile metadata field "
                        + label
                        + "."
                        + entry.getKey()
                        + " is unsupported.");
              }
            });
  }
}
