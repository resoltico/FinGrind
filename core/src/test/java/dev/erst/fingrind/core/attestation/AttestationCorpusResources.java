package dev.erst.fingrind.core.attestation;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Immutable raw sources consumed by the executable static attestation corpus. */
final class AttestationCorpusResources {
  private AttestationCorpusResources() {}

  static Book source(String id, byte[] encoded) {
    return Book.named(id, encoded);
  }

  /** A complete raw artifact source. */
  static final class Artifact {
    private final String id;
    private final byte[] encoded;

    Artifact(String id, byte[] encoded) {
      this.id = requireId(id, "artifact resource");
      this.encoded = Objects.requireNonNull(encoded, "encoded").clone();
    }

    String id() {
      return id;
    }

    byte[] encoded() {
      return encoded.clone();
    }
  }

  /** A receipt and the independently committed book evidence to which it is anchored. */
  static final class Receipt {
    private final String id;
    private final Book book;
    private final byte[] encoded;

    Receipt(String id, Book book, byte[] encoded) {
      this.id = requireId(id, "receipt resource");
      this.book = Objects.requireNonNull(book, "book");
      this.encoded = Objects.requireNonNull(encoded, "encoded").clone();
    }

    String id() {
      return id;
    }

    Book book() {
      return book;
    }

    byte[] encoded() {
      return encoded.clone();
    }
  }

  /** Complete operation evidence stored in the test-only deterministic raw snapshot framing. */
  static final class Book {
    private static final byte[] MAGIC = new byte[] {'F', 'G', 'B', 'K', '1'};

    private final String id;
    private final byte[] encoded;

    private Book(String id, byte[] encoded) {
      this.id = requireId(id, "book resource");
      this.encoded = Objects.requireNonNull(encoded, "encoded").clone();
      decodeOperations(this.encoded);
    }

    static Book named(String id, byte[] encoded) {
      return AttestationFormatFailure.decoding(
          AttestationAuthorizationFailure.PREIMAGE_INVALID,
          () -> new Book(id, Objects.requireNonNull(encoded, "encoded")));
    }

    String id() {
      return id;
    }

    List<AttestationBookOperation> operations() {
      return decodeOperations(encoded);
    }

    AttestationBook decode() {
      return new AttestationBook(operations());
    }

    byte[] encoded() {
      return encoded.clone();
    }

    private static List<AttestationBookOperation> decodeOperations(byte[] encoded) {
      ByteBuffer input = ByteBuffer.wrap(encoded);
      byte[] magic = new byte[MAGIC.length];
      input.get(magic);
      if (!Arrays.equals(MAGIC, magic)) {
        throw new IllegalArgumentException("book resource has an unsupported framing.");
      }
      int count = input.getInt();
      if (count < 1 || count > 1_000_000) {
        throw new IllegalArgumentException("book resource operation count is invalid.");
      }
      List<AttestationBookOperation> operations = new ArrayList<>(count);
      for (int index = 0; index < count; index++) {
        operations.add(
            AttestationBookOperation.decode(readBytes(input), readBytes(input), readBytes(input)));
      }
      if (input.hasRemaining()) {
        throw new IllegalArgumentException("book resource has trailing bytes.");
      }
      return List.copyOf(operations);
    }

    private static byte[] readBytes(ByteBuffer input) {
      int length = input.getInt();
      if (length < 0 || length > input.remaining()) {
        throw new IllegalArgumentException("book resource segment length is invalid.");
      }
      byte[] value = new byte[length];
      input.get(value);
      return value;
    }
  }

  private static String requireId(String id, String resourceKind) {
    if (Objects.requireNonNull(id, "id").isBlank()) {
      throw new IllegalArgumentException(resourceKind + " id must not be blank.");
    }
    return id;
  }
}
