package dev.erst.fingrind.core;

import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.NullNode;
import tools.jackson.databind.node.ObjectNode;

/** Reads a strict transaction-journal JSON shape before any caller treats it as owned state. */
final class PublicationTransactionJournalJsonReader {
  private static final List<String> ROOT_PROPERTIES =
      List.of(
          "schema",
          "transactionId",
          "nonceHex",
          "ownerKeyFingerprint",
          "createdAt",
          "members",
          "transitions",
          "integrity");
  private static final List<String> MEMBER_PROPERTIES =
      List.of(
          "memberId",
          "role",
          "finalPath",
          "stagePath",
          "physicalDirectoryIdentity",
          "publicationMode",
          "progress",
          "stagedArtifact",
          "finalizedArtifact");
  private static final List<String> STAGED_ARTIFACT_PROPERTIES =
      List.of("stagePath", "physicalIdentity", "sha256Hex");
  private static final List<String> FINALIZED_ARTIFACT_PROPERTIES =
      List.of("physicalIdentity", "sha256Hex");
  private static final List<String> TRANSITION_PROPERTIES =
      List.of("state", "recordedAt", "commitOutcome", "cleanupOutcome");

  private PublicationTransactionJournalJsonReader() {}

  static ParsedJournal read(JsonMapper mapper, byte[] encodedJournal)
      throws PublicationTransactionJournalViolation {
    final JsonNode parsed;
    try {
      parsed = mapper.readTree(encodedJournal);
    } catch (JacksonException exception) {
      throw new PublicationTransactionJournalViolation(
          PublicationTransactionJournalViolation.Kind.MALFORMED,
          "Publication transaction journal JSON is malformed.",
          exception);
    }
    JsonNode checkedParsed = Objects.requireNonNullElse(parsed, NullNode.getInstance());
    if (!checkedParsed.isObject()) {
      throw new PublicationTransactionJournalViolation(
          PublicationTransactionJournalViolation.Kind.MALFORMED,
          "Publication transaction journal must contain one JSON object.");
    }
    ObjectNode root = checkedParsed.asObject();
    requireExactProperties(root, ROOT_PROPERTIES, "publication transaction journal");
    return new ParsedJournal(parseJournal(root), requiredHex(root, "integrity", 64));
  }

  private static PublicationTransactionJournal parseJournal(ObjectNode root)
      throws PublicationTransactionJournalViolation {
    try {
      return new PublicationTransactionJournal(
          requiredInt(root, "schema"),
          new PublicationTransactionId(requiredString(root, "transactionId")),
          requiredHex(root, "nonceHex", 32),
          requiredHex(root, "ownerKeyFingerprint", 64),
          Instant.parse(requiredString(root, "createdAt")),
          parseMembers(requiredArray(root, "members")),
          parseTransitions(requiredArray(root, "transitions")));
    } catch (DateTimeParseException | IllegalArgumentException exception) {
      throw new PublicationTransactionJournalViolation(
          PublicationTransactionJournalViolation.Kind.MALFORMED,
          "Publication transaction journal fields are malformed.",
          exception);
    }
  }

  private static List<PublicationTransactionMember> parseMembers(ArrayNode members)
      throws PublicationTransactionJournalViolation {
    List<PublicationTransactionMember> parsedMembers = new ArrayList<>();
    for (JsonNode node : members) {
      ObjectNode member = requireObject(node, "publication transaction member");
      requireExactProperties(member, MEMBER_PROPERTIES, "publication transaction member");
      parsedMembers.add(
          new PublicationTransactionMember(
              requiredString(member, "memberId"),
              PublicationTransactionMemberRole.fromWireValue(requiredString(member, "role")),
              Path.of(requiredString(member, "finalPath")),
              Path.of(requiredString(member, "stagePath")),
              requiredString(member, "physicalDirectoryIdentity"),
              PublicationMode.fromWireValue(requiredString(member, "publicationMode")),
              PublicationTransactionMemberProgress.fromWireValue(
                  requiredString(member, "progress")),
              parseStagedArtifact(member.get("stagedArtifact")),
              parseFinalizedArtifact(member.get("finalizedArtifact"))));
    }
    return List.copyOf(parsedMembers);
  }

  private static Optional<PublicationTransactionStagedArtifact> parseStagedArtifact(JsonNode node)
      throws PublicationTransactionJournalViolation {
    if (node.isNull()) {
      return Optional.empty();
    }
    ObjectNode stagedArtifact = requireObject(node, "staged artifact");
    requireExactProperties(stagedArtifact, STAGED_ARTIFACT_PROPERTIES, "staged artifact");
    return Optional.of(
        new PublicationTransactionStagedArtifact(
            Path.of(requiredString(stagedArtifact, "stagePath")),
            requiredString(stagedArtifact, "physicalIdentity"),
            requiredHex(stagedArtifact, "sha256Hex", 64)));
  }

  private static Optional<PublicationTransactionFinalizedArtifact> parseFinalizedArtifact(
      JsonNode node) throws PublicationTransactionJournalViolation {
    if (node.isNull()) {
      return Optional.empty();
    }
    ObjectNode finalizedArtifact = requireObject(node, "finalized artifact");
    requireExactProperties(finalizedArtifact, FINALIZED_ARTIFACT_PROPERTIES, "finalized artifact");
    return Optional.of(
        new PublicationTransactionFinalizedArtifact(
            requiredString(finalizedArtifact, "physicalIdentity"),
            requiredHex(finalizedArtifact, "sha256Hex", 64)));
  }

  private static List<PublicationTransactionTransition> parseTransitions(ArrayNode transitions)
      throws PublicationTransactionJournalViolation {
    List<PublicationTransactionTransition> parsedTransitions = new ArrayList<>();
    for (JsonNode node : transitions) {
      ObjectNode transition = requireObject(node, "publication transaction transition");
      requireExactProperties(
          transition, TRANSITION_PROPERTIES, "publication transaction transition");
      parsedTransitions.add(
          new PublicationTransactionTransition(
              PublicationTransactionState.fromWireValue(requiredString(transition, "state")),
              Instant.parse(requiredString(transition, "recordedAt")),
              new PublicationTransactionOutcome(
                  PublicationCommitOutcome.fromWireValue(
                      requiredString(transition, "commitOutcome")),
                  PublicationCleanupOutcome.fromWireValue(
                      requiredString(transition, "cleanupOutcome")))));
    }
    return List.copyOf(parsedTransitions);
  }

  private static ObjectNode requireObject(JsonNode node, String label)
      throws PublicationTransactionJournalViolation {
    if (!node.isObject()) {
      throw new PublicationTransactionJournalViolation(
          PublicationTransactionJournalViolation.Kind.MALFORMED,
          label + " must be one JSON object.");
    }
    return node.asObject();
  }

  private static ArrayNode requiredArray(ObjectNode parent, String name)
      throws PublicationTransactionJournalViolation {
    JsonNode node = parent.get(name);
    if (!node.isArray()) {
      throw new PublicationTransactionJournalViolation(
          PublicationTransactionJournalViolation.Kind.MALFORMED, name + " must be one JSON array.");
    }
    return node.asArray();
  }

  private static int requiredInt(ObjectNode parent, String name)
      throws PublicationTransactionJournalViolation {
    JsonNode node = parent.get(name);
    if (!node.isInt()) {
      throw new PublicationTransactionJournalViolation(
          PublicationTransactionJournalViolation.Kind.MALFORMED,
          name + " must be one JSON integer.");
    }
    return node.intValue();
  }

  private static String requiredString(ObjectNode parent, String name)
      throws PublicationTransactionJournalViolation {
    JsonNode node = parent.get(name);
    if (!node.isString()) {
      throw new PublicationTransactionJournalViolation(
          PublicationTransactionJournalViolation.Kind.MALFORMED,
          name + " must be one JSON string.");
    }
    return node.stringValue();
  }

  private static String requiredHex(ObjectNode parent, String name, int length)
      throws PublicationTransactionJournalViolation {
    String value = requiredString(parent, name);
    if (value.length() != length || !value.matches("[0-9a-f]{" + length + "}")) {
      throw new PublicationTransactionJournalViolation(
          PublicationTransactionJournalViolation.Kind.MALFORMED,
          name + " must be lowercase hexadecimal text.");
    }
    return value;
  }

  private static void requireExactProperties(
      ObjectNode object, List<String> expectedProperties, String label)
      throws PublicationTransactionJournalViolation {
    if (object.size() != expectedProperties.size()
        || !object.propertyNames().containsAll(expectedProperties)) {
      throw new PublicationTransactionJournalViolation(
          PublicationTransactionJournalViolation.Kind.MALFORMED,
          label + " has an unsupported JSON property set.");
    }
  }

  record ParsedJournal(PublicationTransactionJournal journal, String integrity) {}
}
