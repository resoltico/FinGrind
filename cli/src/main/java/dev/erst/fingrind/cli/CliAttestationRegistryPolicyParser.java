package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliJsonFieldAccess.optionalText;
import static dev.erst.fingrind.cli.CliJsonFieldAccess.requiredBoolean;
import static dev.erst.fingrind.cli.CliJsonFieldAccess.requiredInt;
import static dev.erst.fingrind.cli.CliJsonFieldAccess.requiredText;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.nullableField;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.rejectUnexpectedFields;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.requireObjectNode;

import dev.erst.fingrind.contract.protocol.ProtocolAttestationRegistryRequestFields;
import dev.erst.fingrind.core.attestation.AttestationRegistryMutation;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Parses the closed quorum, grant, and system-workflow policy language. */
final class CliAttestationRegistryPolicyParser {
  private CliAttestationRegistryPolicyParser() {}

  static AttestationRegistryMutation.AlterPolicy parse(ObjectNode rootNode) {
    rejectUnexpectedFields(
        rootNode, null, ProtocolAttestationRegistryRequestFields.alterPolicyFields());
    return new AttestationRegistryMutation.AlterPolicy(
        policyRules(rootNode), capabilityGrants(rootNode), systemWorkflowPolicies(rootNode));
  }

  private static List<AttestationRegistryMutation.PolicyRule> policyRules(ObjectNode rootNode) {
    List<AttestationRegistryMutation.PolicyRule> rules = new ArrayList<>();
    int index = 0;
    for (JsonNode node :
        optionalArray(rootNode, ProtocolAttestationRegistryRequestFields.POLICY_RULES)) {
      String context = arrayContext(ProtocolAttestationRegistryRequestFields.POLICY_RULES, index);
      index++;
      ObjectNode rule = requireObjectNode(node, context);
      rejectUnexpectedFields(
          rule, context, ProtocolAttestationRegistryRequestFields.policyRuleFields());
      rules.add(
          new AttestationRegistryMutation.PolicyRule(
              CliAttestationRegistryValueParser.capability(
                  rule, ProtocolAttestationRegistryRequestFields.CAPABILITY),
              requiredInt(rule, ProtocolAttestationRegistryRequestFields.QUORUM)));
    }
    return List.copyOf(rules);
  }

  private static List<AttestationRegistryMutation.CapabilityGrant> capabilityGrants(
      ObjectNode rootNode) {
    List<AttestationRegistryMutation.CapabilityGrant> grants = new ArrayList<>();
    int index = 0;
    for (JsonNode node :
        optionalArray(rootNode, ProtocolAttestationRegistryRequestFields.CAPABILITY_GRANTS)) {
      String context =
          arrayContext(ProtocolAttestationRegistryRequestFields.CAPABILITY_GRANTS, index);
      index++;
      ObjectNode grant = requireObjectNode(node, context);
      rejectUnexpectedFields(
          grant, context, ProtocolAttestationRegistryRequestFields.capabilityGrantFields());
      grants.add(
          new AttestationRegistryMutation.CapabilityGrant(
              CliAttestationRegistryValueParser.uuid(
                  grant, ProtocolAttestationRegistryRequestFields.PRINCIPAL_ID),
              CliAttestationRegistryValueParser.capability(
                  grant, ProtocolAttestationRegistryRequestFields.CAPABILITY),
              CliAttestationRegistryValueParser.grantState(
                  grant, ProtocolAttestationRegistryRequestFields.STATE)));
    }
    return List.copyOf(grants);
  }

  private static List<AttestationRegistryMutation.SystemWorkflowPolicy> systemWorkflowPolicies(
      ObjectNode rootNode) {
    List<AttestationRegistryMutation.SystemWorkflowPolicy> policies = new ArrayList<>();
    int index = 0;
    for (JsonNode node :
        optionalArray(
            rootNode, ProtocolAttestationRegistryRequestFields.SYSTEM_WORKFLOW_POLICIES)) {
      String context =
          arrayContext(ProtocolAttestationRegistryRequestFields.SYSTEM_WORKFLOW_POLICIES, index);
      index++;
      ObjectNode policy = requireObjectNode(node, context);
      rejectUnexpectedFields(
          policy, context, ProtocolAttestationRegistryRequestFields.systemWorkflowPolicyFields());
      policies.add(
          new AttestationRegistryMutation.SystemWorkflowPolicy(
              CliAttestationRegistryValueParser.uuid(
                  policy, ProtocolAttestationRegistryRequestFields.WORKFLOW_ID),
              CliAttestationRegistryValueParser.workflowKind(
                  policy, ProtocolAttestationRegistryRequestFields.WORKFLOW_KIND),
              requiredText(
                  policy, ProtocolAttestationRegistryRequestFields.RESULT_HOLDING_ACCOUNT_CODE),
              optionalText(policy, ProtocolAttestationRegistryRequestFields.CAPITAL_ACCOUNT_CODE)
                  .orElse(null),
              optionalText(
                      policy, ProtocolAttestationRegistryRequestFields.RETAINED_RESULT_ACCOUNT_CODE)
                  .orElse(null),
              requiredBoolean(policy, ProtocolAttestationRegistryRequestFields.ACTIVE)));
    }
    return List.copyOf(policies);
  }

  private static List<JsonNode> optionalArray(ObjectNode rootNode, String fieldName) {
    JsonNode value = nullableField(rootNode, fieldName);
    if (value == null || value.isNull()) {
      return List.of();
    }
    if (!value.isArray()) {
      throw new IllegalArgumentException("Field must be an array when present: " + fieldName);
    }
    List<JsonNode> values = new ArrayList<>();
    value.forEach(values::add);
    return List.copyOf(values);
  }

  private static String arrayContext(String fieldName, int index) {
    return "%s[%d]".formatted(fieldName, index);
  }
}
