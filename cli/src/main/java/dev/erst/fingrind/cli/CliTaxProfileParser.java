package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliJsonFieldAccess.optionalObject;
import static dev.erst.fingrind.cli.CliJsonFieldAccess.optionalText;
import static dev.erst.fingrind.cli.CliJsonFieldAccess.rejectUnexpectedFields;
import static dev.erst.fingrind.cli.CliJsonFieldAccess.requiredArray;
import static dev.erst.fingrind.cli.CliJsonFieldAccess.requiredInt;
import static dev.erst.fingrind.cli.CliJsonFieldAccess.requiredText;
import static dev.erst.fingrind.cli.CliJsonScalarParsers.parseWireValue;

import dev.erst.fingrind.contract.protocol.ProtocolOpenBookFields;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.InteractionLimits;
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
import dev.erst.fingrind.core.TaxRegistrationStatus;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.NullNode;
import tools.jackson.databind.node.ObjectNode;

/** Parses tax-profile request shapes for open-book surfaces. */
final class CliTaxProfileParser {
  private CliTaxProfileParser() {}

  static TaxProfile readOpenBookTaxProfile(
      ObjectNode openBookNode, TaxRegistrationStatus taxRegistrationStatus) {
    Objects.requireNonNull(openBookNode, "openBookNode");
    Objects.requireNonNull(taxRegistrationStatus, "taxRegistrationStatus");
    return optionalObject(openBookNode, ProtocolOpenBookFields.TAX_PROFILE)
        .map(
            taxProfileNode ->
                readTaxProfile(taxProfileNode, "openBook." + ProtocolOpenBookFields.TAX_PROFILE))
        .map(taxProfile -> requireCompatibleTaxStatus(taxRegistrationStatus, taxProfile))
        .orElseGet(() -> requirePresenceWhenRegistered(taxRegistrationStatus));
  }

  static TaxProfile readTaxProfileFile(
      Path taxProfileFile, TaxRegistrationStatus taxRegistrationStatus) {
    Objects.requireNonNull(taxProfileFile, "taxProfileFile");
    Objects.requireNonNull(taxRegistrationStatus, "taxRegistrationStatus");
    if (ProtocolOptions.STDIN_TOKEN.equals(taxProfileFile.toString())) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.TAX_PROFILE_FILE,
          ProtocolOptions.TAX_PROFILE_FILE + " must point to one readable JSON file.");
    }
    byte[] requestBytes;
    try {
      requestBytes = readBoundedBytes(taxProfileFile);
    } catch (IOException exception) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.TAX_PROFILE_FILE,
          "Failed to read "
              + ProtocolOptions.TAX_PROFILE_FILE
              + ": "
              + normalizedPath(taxProfileFile)
              + ".",
          exception);
    }
    if (CliJsonObjectMappers.hasDuplicateObjectKeys(requestBytes)) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.TAX_PROFILE_FILE,
          ProtocolOptions.TAX_PROFILE_FILE + " must not contain duplicate object keys.");
    }
    try {
      JsonNode document =
          Objects.requireNonNullElseGet(
              CliJsonObjectMappers.configuredObjectMapper().readTree(requestBytes),
              NullNode::getInstance);
      TaxProfile taxProfile =
          readTaxProfile(
              CliJsonFieldAccess.requireRootObject(document), ProtocolOptions.TAX_PROFILE_FILE);
      return requireCompatibleTaxStatus(taxRegistrationStatus, taxProfile);
    } catch (JacksonException exception) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.TAX_PROFILE_FILE,
          "Failed to parse " + ProtocolOptions.TAX_PROFILE_FILE + " as one JSON object.",
          exception);
    } catch (IllegalArgumentException exception) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.TAX_PROFILE_FILE,
          Objects.requireNonNullElse(exception.getMessage(), "Invalid tax profile."),
          exception);
    }
  }

  private static TaxProfile requireCompatibleTaxStatus(
      TaxRegistrationStatus taxRegistrationStatus, TaxProfile taxProfile) {
    if (taxRegistrationStatus != TaxRegistrationStatus.REGISTERED
        && (!taxProfile.registrations().isEmpty() || !taxProfile.taxCodeDefinitions().isEmpty())) {
      throw new IllegalArgumentException(
          ProtocolOpenBookFields.TAX_PROFILE + " requires taxRegistrationStatus REGISTERED.");
    }
    if (taxRegistrationStatus == TaxRegistrationStatus.REGISTERED
        && taxProfile.registrations().isEmpty()) {
      throw new IllegalArgumentException(
          ProtocolOpenBookFields.TAX_PROFILE
              + "."
              + ProtocolOpenBookFields.TaxProfileFields.REGISTRATIONS
              + " must declare at least one registration when taxRegistrationStatus is REGISTERED.");
    }
    return taxProfile;
  }

  private static TaxProfile requirePresenceWhenRegistered(
      TaxRegistrationStatus taxRegistrationStatus) {
    if (taxRegistrationStatus == TaxRegistrationStatus.REGISTERED) {
      throw new IllegalArgumentException(
          "Registered tax status requires " + ProtocolOpenBookFields.TAX_PROFILE + " details.");
    }
    return TaxProfile.empty();
  }

  private static TaxProfile readTaxProfile(ObjectNode taxProfileNode, String context) {
    rejectUnexpectedFields(taxProfileNode, context, CliJsonRequestSchemas.TAX_PROFILE_FIELDS);
    return new TaxProfile(
        readRegistrations(taxProfileNode, context),
        readTaxCodeDefinitions(taxProfileNode, context));
  }

  private static List<TaxRegistration> readRegistrations(
      ObjectNode taxProfileNode, String context) {
    JsonNode rawNode =
        CliJsonFieldAccess.nullableField(
            taxProfileNode, ProtocolOpenBookFields.TaxProfileFields.REGISTRATIONS);
    if (rawNode == null || rawNode.isNull()) {
      return List.of();
    }
    JsonNode registrationsNode =
        requiredArray(taxProfileNode, ProtocolOpenBookFields.TaxProfileFields.REGISTRATIONS);
    List<TaxRegistration> registrations = new ArrayList<>();
    int index = 0;
    for (JsonNode registrationNode : registrationsNode) {
      ObjectNode registrationObject =
          CliJsonFieldAccess.requireObjectNode(
              registrationNode,
              context
                  + "."
                  + ProtocolOpenBookFields.TaxProfileFields.REGISTRATIONS
                  + "["
                  + index
                  + "]");
      rejectUnexpectedFields(
          registrationObject,
          context + "." + ProtocolOpenBookFields.TaxProfileFields.REGISTRATIONS + "[" + index + "]",
          CliJsonRequestSchemas.TAX_REGISTRATION_FIELDS);
      registrations.add(
          new TaxRegistration(
              new TaxJurisdictionCode(
                  requiredText(
                      registrationObject,
                      ProtocolOpenBookFields.TaxRegistrationFields.JURISDICTION_CODE)),
              new TaxRegistrationId(
                  requiredText(
                      registrationObject,
                      ProtocolOpenBookFields.TaxRegistrationFields.REGISTRATION_ID)),
              parseWireValue(
                  requiredText(
                      registrationObject,
                      ProtocolOpenBookFields.TaxRegistrationFields.FILING_FREQUENCY),
                  context
                      + "."
                      + ProtocolOpenBookFields.TaxProfileFields.REGISTRATIONS
                      + "["
                      + index
                      + "]."
                      + ProtocolOpenBookFields.TaxRegistrationFields.FILING_FREQUENCY,
                  TaxFilingFrequency.wireValues(),
                  TaxFilingFrequency::fromWireValue)));
      index++;
    }
    return List.copyOf(registrations);
  }

  private static List<TaxCodeDefinition> readTaxCodeDefinitions(
      ObjectNode taxProfileNode, String context) {
    JsonNode rawNode =
        CliJsonFieldAccess.nullableField(
            taxProfileNode, ProtocolOpenBookFields.TaxProfileFields.TAX_CODE_DEFINITIONS);
    if (rawNode == null || rawNode.isNull()) {
      return List.of();
    }
    JsonNode definitionsNode =
        requiredArray(taxProfileNode, ProtocolOpenBookFields.TaxProfileFields.TAX_CODE_DEFINITIONS);
    List<TaxCodeDefinition> definitions = new ArrayList<>();
    int index = 0;
    for (JsonNode definitionNode : definitionsNode) {
      ObjectNode definitionObject =
          CliJsonFieldAccess.requireObjectNode(
              definitionNode,
              context
                  + "."
                  + ProtocolOpenBookFields.TaxProfileFields.TAX_CODE_DEFINITIONS
                  + "["
                  + index
                  + "]");
      rejectUnexpectedFields(
          definitionObject,
          context
              + "."
              + ProtocolOpenBookFields.TaxProfileFields.TAX_CODE_DEFINITIONS
              + "["
              + index
              + "]",
          CliJsonRequestSchemas.TAX_CODE_DEFINITION_FIELDS);
      definitions.add(
          new TaxCodeDefinition(
              new TaxCode(
                  requiredText(
                      definitionObject, ProtocolOpenBookFields.TaxCodeDefinitionFields.TAX_CODE)),
              new TaxCodeName(
                  requiredText(
                      definitionObject,
                      ProtocolOpenBookFields.TaxCodeDefinitionFields.DISPLAY_NAME)),
              new TaxJurisdictionCode(
                  requiredText(
                      definitionObject,
                      ProtocolOpenBookFields.TaxCodeDefinitionFields.JURISDICTION_CODE)),
              new PercentageRate(
                  requiredInt(
                      definitionObject,
                      ProtocolOpenBookFields.TaxCodeDefinitionFields.RATE_BASIS_POINTS)),
              parseWireValue(
                  requiredText(
                      definitionObject,
                      ProtocolOpenBookFields.TaxCodeDefinitionFields.PRICING_MODE),
                  context
                      + "."
                      + ProtocolOpenBookFields.TaxProfileFields.TAX_CODE_DEFINITIONS
                      + "["
                      + index
                      + "]."
                      + ProtocolOpenBookFields.TaxCodeDefinitionFields.PRICING_MODE,
                  TaxPricingMode.wireValues(),
                  TaxPricingMode::fromWireValue),
              parseWireValue(
                  requiredText(
                      definitionObject,
                      ProtocolOpenBookFields.TaxCodeDefinitionFields.RECOVERABILITY),
                  context
                      + "."
                      + ProtocolOpenBookFields.TaxProfileFields.TAX_CODE_DEFINITIONS
                      + "["
                      + index
                      + "]."
                      + ProtocolOpenBookFields.TaxCodeDefinitionFields.RECOVERABILITY,
                  TaxRecoverability.wireValues(),
                  TaxRecoverability::fromWireValue),
              new AccountCode(
                  requiredText(
                      definitionObject,
                      ProtocolOpenBookFields.TaxCodeDefinitionFields.LIABILITY_ACCOUNT_CODE)),
              optionalText(
                      definitionObject,
                      ProtocolOpenBookFields.TaxCodeDefinitionFields.RECEIVABLE_ACCOUNT_CODE)
                  .map(AccountCode::new)));
      index++;
    }
    return List.copyOf(definitions);
  }

  private static byte[] readBoundedBytes(Path requestFile) throws IOException {
    try (InputStream fileStream = Files.newInputStream(requestFile)) {
      byte[] requestBytes = fileStream.readNBytes(InteractionLimits.REQUEST_PAYLOAD_MAX_BYTES + 1);
      if (requestBytes.length > InteractionLimits.REQUEST_PAYLOAD_MAX_BYTES) {
        throw new IOException("Tax profile input exceeded the supported size limit.");
      }
      return requestBytes;
    }
  }

  private static String normalizedPath(Path path) {
    return path.toAbsolutePath().normalize().toString();
  }
}
