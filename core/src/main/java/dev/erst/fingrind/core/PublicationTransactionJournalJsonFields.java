package dev.erst.fingrind.core;

import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Owns strict primitive and shape checks for the authenticated journal JSON grammar. */
final class PublicationTransactionJournalJsonFields {
  private PublicationTransactionJournalJsonFields() {}

  static ObjectNode requireObject(JsonNode node, String label)
      throws PublicationTransactionJournalViolation {
    if (node == null || !node.isObject()) {
      throw malformed(label + " must be one JSON object.");
    }
    return node.asObject();
  }

  static ArrayNode requiredArray(ObjectNode parent, String name)
      throws PublicationTransactionJournalViolation {
    JsonNode node = parent.get(name);
    if (node == null || !node.isArray()) {
      throw malformed(name + " must be one JSON array.");
    }
    return node.asArray();
  }

  static int requiredInt(ObjectNode parent, String name)
      throws PublicationTransactionJournalViolation {
    JsonNode node = parent.get(name);
    if (node == null || !node.isInt()) {
      throw malformed(name + " must be one JSON integer.");
    }
    return node.intValue();
  }

  static String requiredString(ObjectNode parent, String name)
      throws PublicationTransactionJournalViolation {
    return requiredString(parent.get(name), name);
  }

  static String requiredHex(ObjectNode parent, String name, int length)
      throws PublicationTransactionJournalViolation {
    return requiredHexNode(parent.get(name), name, length);
  }

  static String requiredHexNode(JsonNode node, String name, int length)
      throws PublicationTransactionJournalViolation {
    String value = requiredString(node, name);
    if (value.length() != length || !value.matches("[0-9a-f]{" + length + "}")) {
      throw malformed(name + " must be lowercase hexadecimal text.");
    }
    return value;
  }

  static void requireExactProperties(
      ObjectNode object, List<String> expectedProperties, String label)
      throws PublicationTransactionJournalViolation {
    if (object.size() != expectedProperties.size()
        || !object.propertyNames().containsAll(expectedProperties)) {
      throw malformed(label + " has an unsupported JSON property set.");
    }
  }

  private static String requiredString(JsonNode node, String name)
      throws PublicationTransactionJournalViolation {
    if (node == null || !node.isString()) {
      throw malformed(name + " must be one JSON string.");
    }
    return node.stringValue();
  }

  private static PublicationTransactionJournalViolation malformed(String message) {
    return new PublicationTransactionJournalViolation(
        PublicationTransactionJournalViolation.Kind.MALFORMED, message);
  }
}
