package dev.erst.fingrind.sqlite;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/** Generates one distinct maintenance secret in memory before the journal admits its only stage. */
final class SqliteDistinctGeneratedSecret {
  private static final int MAXIMUM_GENERATION_ATTEMPTS = 32;

  private SqliteDistinctGeneratedSecret() {}

  /** Returns one fresh passphrase that is provably distinct from the supplied source passphrase. */
  static SqliteBookPassphrase generate(
      SqliteBookPassphrase sourcePassphrase,
      SqliteProtectedBookStagingCheckpoint checkpoint,
      SqliteProtectedBookStagingCheckpointListener checkpointListener) {
    return generate(
        sourcePassphrase,
        checkpoint,
        checkpointListener,
        SqliteBookKeyFileMaterial::encodedPassphraseBytes,
        PassphraseCandidate::fromUtf8Bytes);
  }

  /**
   * Retries a caller-supplied entropy source so collision handling remains mechanically testable.
   */
  static SqliteBookPassphrase generate(
      SqliteBookPassphrase sourcePassphrase,
      SqliteProtectedBookStagingCheckpoint checkpoint,
      SqliteProtectedBookStagingCheckpointListener checkpointListener,
      Supplier<byte[]> candidateBytesSupplier) {
    return generate(
        sourcePassphrase,
        checkpoint,
        checkpointListener,
        candidateBytesSupplier,
        PassphraseCandidate::fromUtf8Bytes);
  }

  /**
   * Retries one caller-supplied candidate factory so the resource-close failure path remains
   * observable at the secret zeroization boundary. Each non-null factory product is closed exactly
   * once before the generated secret transfers to the caller. The explicit ownership boundary
   * retains a close failure as suppressed evidence without obscuring the primary operation failure.
   */
  static SqliteBookPassphrase generate(
      SqliteBookPassphrase sourcePassphrase,
      SqliteProtectedBookStagingCheckpoint checkpoint,
      SqliteProtectedBookStagingCheckpointListener checkpointListener,
      Supplier<byte[]> candidateBytesSupplier,
      Function<byte[], GeneratedSecretCandidate> candidateFactory) {
    Objects.requireNonNull(sourcePassphrase, "sourcePassphrase");
    Objects.requireNonNull(checkpoint, "checkpoint");
    Objects.requireNonNull(checkpointListener, "checkpointListener");
    Supplier<byte[]> checkedCandidateBytesSupplier =
        Objects.requireNonNull(candidateBytesSupplier, "candidateBytesSupplier");
    Function<byte[], GeneratedSecretCandidate> checkedCandidateFactory =
        Objects.requireNonNull(candidateFactory, "candidateFactory");
    for (int attempt = 0; attempt < MAXIMUM_GENERATION_ATTEMPTS; attempt++) {
      checkpointListener.reached(checkpoint);
      byte[] candidateBytes = checkedCandidateBytesSupplier.get();
      try {
        SqliteBookPassphrase generatedSecret =
            generateCandidate(sourcePassphrase, checkedCandidateFactory, candidateBytes);
        if (generatedSecret != null) {
          return generatedSecret;
        }
      } finally {
        if (candidateBytes != null) {
          java.util.Arrays.fill(candidateBytes, (byte) 0);
        }
      }
    }
    throw new IllegalStateException(
        "Unable to generate a distinct FinGrind maintenance key after "
            + MAXIMUM_GENERATION_ATTEMPTS
            + " attempts.");
  }

  /**
   * Uses and closes exactly one candidate before returning a transferred distinct secret, if any.
   */
  private static @Nullable SqliteBookPassphrase generateCandidate(
      SqliteBookPassphrase sourcePassphrase,
      Function<byte[], GeneratedSecretCandidate> candidateFactory,
      byte[] candidateBytes) {
    GeneratedSecretCandidate candidate =
        Objects.requireNonNull(candidateFactory.apply(candidateBytes), "candidateFactory result");
    CandidateOperation operation = CandidateOperation.use(sourcePassphrase, candidate);
    return operation.completeAfterCandidateClose(closeCandidate(candidate));
  }

  /** Closes one candidate while retaining, rather than throwing, its nonfatal terminal failure. */
  private static @Nullable RuntimeException closeCandidate(GeneratedSecretCandidate candidate) {
    try {
      candidate.close();
      return null;
    } catch (RuntimeException closeFailure) {
      return closeFailure;
    }
  }

  /** Captures the outcome of using a candidate before its independently owned close boundary. */
  private sealed interface CandidateOperation
      permits CandidateSuccess, CandidateRuntimeFailure, CandidateError {
    /** Uses one candidate and preserves either its result or its precise failure category. */
    static CandidateOperation use(
        SqliteBookPassphrase sourcePassphrase, GeneratedSecretCandidate candidate) {
      try {
        return new CandidateSuccess(
            candidate.hasSameSecretAs(sourcePassphrase) ? null : candidate.copy());
      } catch (RuntimeException operationFailure) {
        return new CandidateRuntimeFailure(operationFailure);
      } catch (Error operationFailure) {
        return new CandidateError(operationFailure);
      }
    }

    /** Completes the operation after the candidate close boundary has reported its outcome. */
    @Nullable SqliteBookPassphrase completeAfterCandidateClose(
        @Nullable RuntimeException candidateCloseFailure);
  }

  /**
   * A successful candidate operation, including a collision that intentionally yields no secret.
   */
  private record CandidateSuccess(@Nullable SqliteBookPassphrase generatedSecret)
      implements CandidateOperation {
    @Override
    public @Nullable SqliteBookPassphrase completeAfterCandidateClose(
        @Nullable RuntimeException candidateCloseFailure) {
      if (candidateCloseFailure != null) {
        closeUntransferredGeneratedSecret(generatedSecret);
        throw candidateCloseFailure;
      }
      return generatedSecret;
    }
  }

  /** A nonfatal candidate-use failure whose close failure must remain attached as evidence. */
  private record CandidateRuntimeFailure(RuntimeException operationFailure)
      implements CandidateOperation {
    @Override
    public SqliteBookPassphrase completeAfterCandidateClose(
        @Nullable RuntimeException candidateCloseFailure) {
      if (candidateCloseFailure != null) {
        operationFailure.addSuppressed(candidateCloseFailure);
      }
      throw operationFailure;
    }
  }

  /**
   * A fatal candidate-use failure whose nonfatal close failure must remain attached as evidence.
   */
  private record CandidateError(Error operationFailure) implements CandidateOperation {
    @Override
    public SqliteBookPassphrase completeAfterCandidateClose(
        @Nullable RuntimeException candidateCloseFailure) {
      if (candidateCloseFailure != null) {
        operationFailure.addSuppressed(candidateCloseFailure);
      }
      throw operationFailure;
    }
  }

  /** Closes an untransferred generated secret when candidate close prevents ownership transfer. */
  private static void closeUntransferredGeneratedSecret(
      @Nullable SqliteBookPassphrase generatedSecret) {
    if (generatedSecret != null) {
      generatedSecret.close();
    }
  }

  /** One owned generated-secret candidate whose terminal close zeroizes its backing bytes. */
  interface GeneratedSecretCandidate extends AutoCloseable {
    /** Returns whether this candidate matches the source secret exactly. */
    boolean hasSameSecretAs(SqliteBookPassphrase sourcePassphrase);

    /** Returns one independent copy for the accepted generated secret. */
    SqliteBookPassphrase copy();

    /** Zeroizes the candidate after it has been accepted or rejected. */
    @Override
    void close();
  }

  /** Standard candidate backed by one exact normalized passphrase value. */
  private static final class PassphraseCandidate implements GeneratedSecretCandidate {
    private final SqliteBookPassphrase passphrase;

    private PassphraseCandidate(SqliteBookPassphrase passphrase) {
      this.passphrase = Objects.requireNonNull(passphrase, "passphrase");
    }

    private static PassphraseCandidate fromUtf8Bytes(byte[] candidateBytes) {
      return new PassphraseCandidate(
          SqliteBookPassphrase.fromUtf8Bytes(
              "generated protected-book maintenance passphrase", candidateBytes));
    }

    @Override
    public boolean hasSameSecretAs(SqliteBookPassphrase sourcePassphrase) {
      return passphrase.hasSameSecretAs(sourcePassphrase);
    }

    @Override
    public SqliteBookPassphrase copy() {
      return passphrase.copy();
    }

    @Override
    public void close() {
      passphrase.close();
    }
  }
}
