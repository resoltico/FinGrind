package dev.erst.fingrind.core.attestation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the normative attestation vectors that serve as the test oracle. */
final class AttestationDocumentVectors {
  static final String PROTOCOL_DOCUMENT = "docs/DOC_02_VerifiableOperationAttestationVectors.md";
  static final String ARTIFACT_DOCUMENT = "docs/DOC_02_VerifiableOperationAttestationArtifacts.md";

  private static final Pattern DOCUMENT_VALUE =
      Pattern.compile("^\\s*([A-Za-z][A-Za-z0-9]*)\\s*=\\s*([0-9a-f]+)\\s*$");

  private AttestationDocumentVectors() {}

  static byte[] bytes(String document, String vector, String field) throws IOException {
    return hex(value(document, vector, field));
  }

  static String value(String document, String vector, String field) throws IOException {
    for (String line : vectorSection(document, vector)) {
      Matcher matcher = DOCUMENT_VALUE.matcher(line);
      if (matcher.matches() && field.equals(matcher.group(1))) {
        return matcher.group(2);
      }
    }
    throw new IllegalStateException("Cannot locate " + field + " in " + vector + ".");
  }

  static List<AttestationSignatureEntry> entries(byte[] envelope, int payloadLength) {
    int count =
        Short.toUnsignedInt((short) ((envelope[payloadLength] << 8) | envelope[payloadLength + 1]));
    int offset = payloadLength + Short.BYTES;
    List<AttestationSignatureEntry> entries = new ArrayList<>(count);
    for (int index = 0; index < count; index++) {
      java.util.UUID principalId = uuid(envelope, offset);
      offset += 16;
      AttestationHash keyId =
          AttestationHash.of(java.util.Arrays.copyOfRange(envelope, offset, offset + 32));
      offset += 32;
      entries.add(
          new AttestationSignatureEntry(
              principalId, keyId, java.util.Arrays.copyOfRange(envelope, offset, offset + 64)));
      offset += 64;
    }
    if (envelope.length != offset) {
      throw new IllegalStateException("Attestation document envelope length is inconsistent.");
    }
    return List.copyOf(entries);
  }

  static byte[] hex(String value) {
    return HexFormat.of().parseHex(value);
  }

  private static java.util.UUID uuid(byte[] bytes, int offset) {
    java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes, offset, 16);
    return new java.util.UUID(buffer.getLong(), buffer.getLong());
  }

  private static List<String> vectorSection(String document, String vector) throws IOException {
    List<String> section = new ArrayList<>();
    boolean inSection = false;
    for (String line : Files.readAllLines(documentPath(document))) {
      if (line.startsWith("### " + vector)) {
        inSection = true;
      } else if (inSection && line.startsWith("### ")) {
        break;
      } else if (inSection) {
        section.add(line);
      }
    }
    if (section.isEmpty()) {
      throw new IllegalStateException("Cannot locate " + vector + " in " + document + ".");
    }
    return List.copyOf(section);
  }

  private static Path documentPath(String document) {
    for (Path directory = Path.of("").toAbsolutePath();
        directory != null;
        directory = directory.getParent()) {
      Path candidate = directory.resolve(document);
      if (Files.isRegularFile(candidate)) {
        return candidate;
      }
    }
    throw new IllegalStateException(
        "Cannot locate " + document + " from the test working directory.");
  }
}
