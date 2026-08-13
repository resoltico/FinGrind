package dev.erst.fingrind.core;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Optional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.json.JsonMapper;

/** Writes the fixed-order canonical JSON representation for one transaction journal payload. */
final class PublicationTransactionJournalJsonWriter {
  private PublicationTransactionJournalJsonWriter() {}

  static byte[] payload(JsonMapper mapper, PublicationTransactionJournal journal) {
    return write(mapper, journal, Optional.empty());
  }

  static byte[] authenticated(
      JsonMapper mapper, PublicationTransactionJournal journal, String integrity) {
    return write(mapper, journal, Optional.of(integrity));
  }

  private static byte[] write(
      JsonMapper mapper, PublicationTransactionJournal journal, Optional<String> integrity) {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    writeTo(mapper, output, journal, integrity);
    return output.toByteArray();
  }

  static void writeTo(
      JsonMapper mapper,
      OutputStream output,
      PublicationTransactionJournal journal,
      Optional<String> integrity) {
    try (JsonGenerator generator = mapper.createGenerator(output)) {
      generator.writeStartObject();
      generator.writeNumberProperty("schema", journal.schemaVersion());
      generator.writeStringProperty("transactionId", journal.transactionId().value());
      generator.writeStringProperty("nonceHex", journal.nonceHex());
      generator.writeStringProperty("ownerKeyFingerprint", journal.ownerKeyFingerprint());
      if (journal.schemaVersion() >= PublicationTransactionJournal.CURRENT_SCHEMA_VERSION) {
        generator.writeName("ownerContext");
        if (journal.ownerContext().isEmpty()) {
          generator.writeNull();
        } else {
          generator.writeString(journal.ownerContext().orElseThrow().value());
        }
      }
      generator.writeStringProperty("createdAt", journal.createdAt().toString());
      writeMembers(generator, journal);
      writeTransitions(generator, journal);
      if (integrity.isPresent()) {
        generator.writeStringProperty("integrity", integrity.orElseThrow());
      }
      generator.writeEndObject();
      generator.flush();
    } catch (JacksonException exception) {
      throw new IllegalStateException(
          "FinGrind could not encode a publication transaction journal.", exception);
    }
  }

  private static void writeMembers(JsonGenerator generator, PublicationTransactionJournal journal) {
    generator.writeArrayPropertyStart("members");
    for (PublicationTransactionMember member : journal.members()) {
      generator.writeStartObject();
      generator.writeStringProperty("memberId", member.memberId());
      generator.writeStringProperty("role", member.role().wireValue());
      generator.writeStringProperty("finalPath", member.finalPath().toString());
      generator.writeStringProperty("stagePath", member.stagePath().toString());
      generator.writeStringProperty(
          "physicalDirectoryIdentity", member.physicalDirectoryIdentity());
      generator.writeStringProperty("publicationMode", member.publicationMode().wireValue());
      if (journal.schemaVersion() != PublicationTransactionJournal.LEGACY_SCHEMA_VERSION) {
        writeReplacementTarget(generator, member);
      }
      generator.writeStringProperty("progress", member.progress().wireValue());
      writeStagedArtifact(generator, member);
      writeFinalizedArtifact(generator, member);
      generator.writeEndObject();
    }
    generator.writeEndArray();
  }

  private static void writeReplacementTarget(
      JsonGenerator generator, PublicationTransactionMember member) {
    generator.writeName("replacementTarget");
    if (member.replacementTarget().isEmpty()) {
      generator.writeNull();
      return;
    }
    PublicationTransactionFinalizedArtifact target = member.replacementTarget().orElseThrow();
    generator.writeStartObject();
    generator.writeStringProperty("physicalIdentity", target.physicalIdentity());
    generator.writeStringProperty("sha256Hex", target.sha256Hex());
    generator.writeEndObject();
  }

  private static void writeStagedArtifact(
      JsonGenerator generator, PublicationTransactionMember member) {
    generator.writeName("stagedArtifact");
    if (member.stagedArtifact().isEmpty()) {
      generator.writeNull();
      return;
    }
    PublicationTransactionStagedArtifact artifact = member.stagedArtifact().orElseThrow();
    generator.writeStartObject();
    generator.writeStringProperty("stagePath", artifact.stagePath().toString());
    generator.writeStringProperty("physicalIdentity", artifact.physicalIdentity());
    generator.writeStringProperty("sha256Hex", artifact.sha256Hex());
    generator.writeEndObject();
  }

  private static void writeFinalizedArtifact(
      JsonGenerator generator, PublicationTransactionMember member) {
    generator.writeName("finalizedArtifact");
    if (member.finalizedArtifact().isEmpty()) {
      generator.writeNull();
      return;
    }
    PublicationTransactionFinalizedArtifact artifact = member.finalizedArtifact().orElseThrow();
    generator.writeStartObject();
    generator.writeStringProperty("physicalIdentity", artifact.physicalIdentity());
    generator.writeStringProperty("sha256Hex", artifact.sha256Hex());
    generator.writeEndObject();
  }

  private static void writeTransitions(
      JsonGenerator generator, PublicationTransactionJournal journal) {
    generator.writeArrayPropertyStart("transitions");
    for (PublicationTransactionTransition transition : journal.transitions()) {
      generator.writeStartObject();
      generator.writeStringProperty("state", transition.state().wireValue());
      generator.writeStringProperty("recordedAt", transition.recordedAt().toString());
      generator.writeStringProperty("commitOutcome", transition.outcome().commit().wireValue());
      generator.writeStringProperty("cleanupOutcome", transition.outcome().cleanup().wireValue());
      generator.writeEndObject();
    }
    generator.writeEndArray();
  }
}
