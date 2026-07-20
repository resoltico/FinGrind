package dev.erst.fingrind.core.attestation;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Raw, deterministic resource forms consumed by the executable static corpus. */
final class AttestationCorpusResources {
  private AttestationCorpusResources() {}

  static Book book(String id, List<AttestationBookOperation> operations) {
    return new Book(id, operations);
  }

  /** A complete raw artifact source. */
  static final class Artifact {
    private final String id;
    private final byte[] encoded;

    Artifact(String id, byte[] encoded) {
      this.id = Objects.requireNonNull(id, "id");
      this.encoded = Objects.requireNonNull(encoded, "encoded").clone();
    }

    String id() {
      return id;
    }

    byte[] encoded() {
      return encoded.clone();
    }
  }

  /** A receipt and the book evidence to which it is anchored. */
  static final class Receipt {
    private final String id;
    private final Book book;
    private final byte[] encoded;

    Receipt(String id, Book book, byte[] encoded) {
      this.id = Objects.requireNonNull(id, "id");
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

  /** One restore continuation with its external artifact and optional source acknowledgement. */
  static final class Restore {
    private final String id;
    private final Artifact artifact;
    private final Optional<Book> sourceAcknowledgement;
    private final Book target;

    Restore(String id, Artifact artifact, Optional<Book> sourceAcknowledgement, Book target) {
      if (Objects.requireNonNull(id, "id").isBlank()) {
        throw new IllegalArgumentException("restore resource id must not be blank.");
      }
      this.id = id;
      this.artifact = Objects.requireNonNull(artifact, "artifact");
      this.sourceAcknowledgement =
          Objects.requireNonNull(sourceAcknowledgement, "sourceAcknowledgement");
      this.target = Objects.requireNonNull(target, "target");
    }

    String id() {
      return id;
    }

    Artifact artifact() {
      return artifact;
    }

    Optional<Book> sourceAcknowledgement() {
      return sourceAcknowledgement;
    }

    Book target() {
      return target;
    }
  }

  /** A standalone authorization envelope and its explicit resolver state. */
  static final class StandaloneEnvelope {
    private final String id;
    private final byte[] encoded;
    private final AttestationRegistry registry;
    private final AttestationAuthorizationContext context;
    private final AttestationAuthorizationEnvelope envelope;

    StandaloneEnvelope(
        String id,
        byte[] encoded,
        AttestationRegistry registry,
        AttestationAuthorizationContext context,
        AttestationAuthorizationEnvelope envelope) {
      this.id = Objects.requireNonNull(id, "id");
      this.encoded = Objects.requireNonNull(encoded, "encoded").clone();
      this.registry = Objects.requireNonNull(registry, "registry");
      this.context = Objects.requireNonNull(context, "context");
      this.envelope = Objects.requireNonNull(envelope, "envelope");
    }

    String id() {
      return id;
    }

    byte[] encoded() {
      return encoded.clone();
    }

    AttestationRegistry registry() {
      return registry;
    }

    AttestationAuthorizationContext context() {
      return context;
    }

    AttestationAuthorizationEnvelope envelope() {
      return envelope;
    }
  }

  /** Complete operation evidence stored in the test-only deterministic raw snapshot framing. */
  static final class Book {
    private static final byte[] MAGIC = new byte[] {'F', 'G', 'B', 'K', '1'};

    private final String id;
    private final List<RawOperation> operations;

    Book(String id, List<AttestationBookOperation> operations) {
      this(
          id,
          Objects.requireNonNull(operations, "operations").stream()
              .map(RawOperation::from)
              .toList(),
          true);
    }

    private Book(String id, List<RawOperation> operations, boolean rawOperations) {
      if (!rawOperations) {
        throw new IllegalArgumentException("raw operation resources must be explicit.");
      }
      if (Objects.requireNonNull(id, "id").isBlank()) {
        throw new IllegalArgumentException("book resource id must not be blank.");
      }
      this.id = id;
      this.operations = List.copyOf(Objects.requireNonNull(operations, "operations"));
      if (this.operations.isEmpty()) {
        throw new IllegalArgumentException("book resource must contain genesis.");
      }
    }

    String id() {
      return id;
    }

    List<AttestationBookOperation> operations() {
      return operations.stream().map(RawOperation::decode).toList();
    }

    AttestationBook decode() {
      return new AttestationBook(operations());
    }

    byte[] encoded() {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      output.writeBytes(MAGIC);
      appendInt(output, operations.size());
      for (RawOperation operation : operations) {
        appendBytes(output, operation.envelope());
        appendBytes(output, operation.request());
        appendBytes(output, operation.effect());
      }
      return output.toByteArray();
    }

    byte[] envelope(int operationIndex) {
      return operation(operationIndex).envelope();
    }

    static Book decode(byte[] encoded) {
      return AttestationFormatFailure.decoding(
          AttestationAuthorizationFailure.PREIMAGE_INVALID,
          () -> decodeUnchecked(Objects.requireNonNull(encoded, "encoded")));
    }

    private static Book decodeUnchecked(byte[] encoded) {
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
      List<RawOperation> operations = new ArrayList<>(count);
      for (int index = 0; index < count; index++) {
        operations.add(new RawOperation(readBytes(input), readBytes(input), readBytes(input)));
      }
      if (input.hasRemaining()) {
        throw new IllegalArgumentException("book resource has trailing bytes.");
      }
      return new Book("decoded-corpus-book", operations, true);
    }

    private RawOperation operation(int operationIndex) {
      if (operationIndex < 0 || operationIndex >= operations.size()) {
        throw new IllegalArgumentException("book resource operation index is invalid.");
      }
      return operations.get(operationIndex);
    }

    private static void appendBytes(ByteArrayOutputStream output, byte[] value) {
      appendInt(output, value.length);
      output.writeBytes(value);
    }

    private static void appendInt(ByteArrayOutputStream output, int value) {
      output.write(value >>> 24);
      output.write(value >>> 16);
      output.write(value >>> 8);
      output.write(value);
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

  /** One operation's untrusted serialized envelope and canonical preimages. */
  private static final class RawOperation {
    private final byte[] envelope;
    private final byte[] request;
    private final byte[] effect;

    RawOperation(byte[] envelope, byte[] request, byte[] effect) {
      this.envelope = Objects.requireNonNull(envelope, "envelope").clone();
      this.request = Objects.requireNonNull(request, "request").clone();
      this.effect = Objects.requireNonNull(effect, "effect").clone();
    }

    static RawOperation from(AttestationBookOperation operation) {
      AttestationBookOperation checkedOperation = Objects.requireNonNull(operation, "operation");
      return new RawOperation(
          checkedOperation.envelope().encoded(),
          checkedOperation.requestPreimage().encoded(),
          checkedOperation.effectPreimage().encoded());
    }

    AttestationBookOperation decode() {
      return AttestationBookOperation.decode(envelope, request, effect);
    }

    byte[] envelope() {
      return envelope.clone();
    }

    byte[] request() {
      return request.clone();
    }

    byte[] effect() {
      return effect.clone();
    }
  }
}
