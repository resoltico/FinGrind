package dev.erst.fingrind.core.attestation;

import java.util.Map;

/** Closed value vocabulary used by the immutable-preimage record catalog. */
enum AttestationFieldType {
  UNSIGNED_8,
  UNSIGNED_16,
  UNSIGNED_32,
  UNSIGNED_64,
  SIGNED_64,
  SIGNED_128,
  UUID,
  HASH,
  SPKI,
  BYTES,
  TOKEN,
  TEXT,
  CURRENCY,
  DATE,
  INSTANT,
  MONEY,
  SCALED,
  BOOLEAN,
  MUTATION;

  static final Map<String, AttestationFieldType> WIRE_TYPES =
      Map.ofEntries(
          Map.entry("u8", UNSIGNED_8),
          Map.entry("u16", UNSIGNED_16),
          Map.entry("u32", UNSIGNED_32),
          Map.entry("u64", UNSIGNED_64),
          Map.entry("i64", SIGNED_64),
          Map.entry("i128", SIGNED_128),
          Map.entry("uuid", UUID),
          Map.entry("hash", HASH),
          Map.entry("spki", SPKI),
          Map.entry("bytes", BYTES),
          Map.entry("token", TOKEN),
          Map.entry("text", TEXT),
          Map.entry("currency", CURRENCY),
          Map.entry("date", DATE),
          Map.entry("instant", INSTANT),
          Map.entry("money", MONEY),
          Map.entry("scaled", SCALED),
          Map.entry("bool", BOOLEAN));
}
