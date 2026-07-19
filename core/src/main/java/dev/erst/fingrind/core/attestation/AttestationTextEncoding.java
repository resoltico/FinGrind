package dev.erst.fingrind.core.attestation;

import dev.erst.fingrind.core.CurrencyUnit;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;

/** Canonical text, bytes, date, instant, currency, and SPKI encodings. */
final class AttestationTextEncoding {
  private static final int MAX_BYTES_LENGTH = 1_048_576;
  private static final Pattern TOKEN = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
  private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");
  private static final byte[] ED25519_SPKI_PREFIX =
      new byte[] {0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00};
  private static final DateTimeFormatter INSTANT_FORMAT =
      new DateTimeFormatterBuilder()
          .appendPattern("uuuu-MM-dd'T'HH:mm:ss")
          .appendFraction(ChronoField.NANO_OF_SECOND, 3, 3, true)
          .appendLiteral('Z')
          .toFormatter()
          .withZone(ZoneOffset.UTC);

  private AttestationTextEncoding() {}

  static void appendToken(ByteArrayOutputStream output, String value, String fieldName) {
    Objects.requireNonNull(output, "output");
    Objects.requireNonNull(value, fieldName);
    byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
    if (!TOKEN.matcher(value).matches() || bytes.length > 64) {
      throw new IllegalArgumentException(
          fieldName + " must be a lowercase ASCII kebab token of at most 64 bytes.");
    }
    AttestationUnsignedEncoding.appendByte(output, bytes.length, fieldName + " length");
    output.writeBytes(bytes);
  }

  static void appendText(ByteArrayOutputStream output, String value, String fieldName) {
    Objects.requireNonNull(output, "output");
    Objects.requireNonNull(value, fieldName);
    if (!Normalizer.isNormalized(value, Normalizer.Form.NFC)) {
      throw new IllegalArgumentException(fieldName + " must be NFC-normalized.");
    }
    if (value.indexOf('\u0000') >= 0) {
      throw new IllegalArgumentException(fieldName + " must not contain NUL.");
    }
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    if (bytes.length > MAX_BYTES_LENGTH) {
      throw new IllegalArgumentException(fieldName + " must be at most 1048576 bytes.");
    }
    AttestationUnsignedEncoding.appendUnsigned(
        output, BigInteger.valueOf(bytes.length), Integer.BYTES, fieldName + " length");
    output.writeBytes(bytes);
  }

  static void appendBytes(ByteArrayOutputStream output, byte[] value, String fieldName) {
    Objects.requireNonNull(output, "output");
    Objects.requireNonNull(value, fieldName);
    if (value.length > MAX_BYTES_LENGTH) {
      throw new IllegalArgumentException(fieldName + " must be at most 1048576 bytes.");
    }
    AttestationUnsignedEncoding.appendUnsigned(
        output, BigInteger.valueOf(value.length), Integer.BYTES, fieldName + " length");
    output.writeBytes(value);
  }

  static void appendSpki(ByteArrayOutputStream output, byte[] value) {
    Objects.requireNonNull(output, "output");
    Objects.requireNonNull(value, "value");
    if (value.length == 0 || value.length > 4096) {
      throw new IllegalArgumentException("spki must contain between 1 and 4096 bytes.");
    }
    if (value.length != 44
        || !Arrays.equals(
            ED25519_SPKI_PREFIX,
            0,
            ED25519_SPKI_PREFIX.length,
            value,
            0,
            ED25519_SPKI_PREFIX.length)) {
      throw new IllegalArgumentException(
          "spki must be a DER-encoded Ed25519 SubjectPublicKeyInfo.");
    }
    AttestationUnsignedEncoding.appendUnsigned(
        output, BigInteger.valueOf(value.length), Short.BYTES, "spki length");
    output.writeBytes(value);
  }

  static void appendCurrency(ByteArrayOutputStream output, String value) {
    Objects.requireNonNull(output, "output");
    Objects.requireNonNull(value, "currency");
    if (!CURRENCY.matcher(value).matches()) {
      throw new IllegalArgumentException("currency must be exactly three uppercase ASCII letters.");
    }
    output.writeBytes(CurrencyUnit.of(value).code().getBytes(StandardCharsets.US_ASCII));
  }

  static void appendInstant(ByteArrayOutputStream output, Instant value, String fieldName) {
    Objects.requireNonNull(output, "output");
    Objects.requireNonNull(value, fieldName);
    if (!value.equals(value.truncatedTo(ChronoUnit.MILLIS))) {
      throw new IllegalArgumentException(fieldName + " must be precise to milliseconds.");
    }
    String formatted = INSTANT_FORMAT.format(value);
    if (formatted.length() != 24) {
      throw new IllegalArgumentException(
          fieldName + " must fit the four-digit UTC wire timestamp range.");
    }
    output.writeBytes(formatted.getBytes(StandardCharsets.US_ASCII));
  }

  static void appendDate(ByteArrayOutputStream output, LocalDate value, String fieldName) {
    Objects.requireNonNull(output, "output");
    Objects.requireNonNull(value, fieldName);
    String formatted = value.toString();
    if (formatted.length() != 10) {
      throw new IllegalArgumentException(
          fieldName + " must fit the four-digit Gregorian date range.");
    }
    output.writeBytes(formatted.getBytes(StandardCharsets.US_ASCII));
  }

  static void appendAscii(ByteArrayOutputStream output, String value) {
    Objects.requireNonNull(output, "output");
    output.writeBytes(value.getBytes(StandardCharsets.US_ASCII));
  }
}
