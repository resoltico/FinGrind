package dev.erst.fingrind.core.attestation;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Canonical signature envelope for one operation, manifest, or receipt payload. */
final class AttestationEnvelope<P extends AttestationPayload> {
  private final P payload;
  private final List<AttestationSignatureEntry> entries;

  private AttestationEnvelope(P payload, List<AttestationSignatureEntry> entries) {
    this.payload = payload;
    this.entries = entries;
  }

  static <P extends AttestationPayload> AttestationEnvelope<P> of(
      P payload, List<AttestationSignatureEntry> entries) {
    Objects.requireNonNull(payload, "payload");
    Objects.requireNonNull(entries, "entries");
    if (entries.size() >= AttestationUnsignedEncoding.uint16Limit().intValueExact()) {
      throw new IllegalArgumentException(
          "Attestation envelope may contain at most 65535 signatures.");
    }
    List<AttestationSignatureEntry> canonicalEntries = new ArrayList<>(entries.size());
    for (AttestationSignatureEntry entry : entries) {
      canonicalEntries.add(Objects.requireNonNull(entry, "entries must not contain null"));
    }
    canonicalEntries.sort(Comparator.comparing(AttestationSignatureEntry::keyId));
    requireDistinct(canonicalEntries);
    return new AttestationEnvelope<>(payload, List.copyOf(canonicalEntries));
  }

  P payload() {
    return payload;
  }

  List<AttestationSignatureEntry> entries() {
    return entries;
  }

  byte[] encoded() {
    byte[] payloadBytes = payload.encoded();
    ByteArrayOutputStream output =
        new ByteArrayOutputStream(
            Math.addExact(payloadBytes.length, Math.addExact(Short.BYTES, entries.size() * 112)));
    output.writeBytes(payloadBytes);
    AttestationUnsignedEncoding.appendUnsigned(
        output, BigInteger.valueOf(entries.size()), Short.BYTES, "sigCount");
    entries.forEach(entry -> entry.appendTo(output));
    return output.toByteArray();
  }

  AttestationHash head() {
    return AttestationHash.sha256(encoded());
  }

  private static void requireDistinct(List<AttestationSignatureEntry> entries) {
    Set<UUID> principalIds = new HashSet<>();
    Set<AttestationHash> keyIds = new HashSet<>();
    for (AttestationSignatureEntry entry : entries) {
      if (!principalIds.add(entry.principalId())) {
        throw new IllegalArgumentException("Attestation envelope principal IDs must be distinct.");
      }
      if (!keyIds.add(entry.keyId())) {
        throw new IllegalArgumentException("Attestation envelope key IDs must be distinct.");
      }
    }
  }
}
