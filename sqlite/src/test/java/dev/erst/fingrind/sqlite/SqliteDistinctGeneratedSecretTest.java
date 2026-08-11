package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.runtime.ContractFailureException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Proves generated maintenance secrets retry collisions and never reuse the source secret. */
class SqliteDistinctGeneratedSecretTest {
  @Test
  void retriesTheSourceSecretThenReturnsTheFirstDistinctCandidate() {
    AtomicInteger attempts = new AtomicInteger();
    AtomicInteger candidates = new AtomicInteger();
    try (SqliteBookPassphrase source = passphrase("source");
        SqliteBookPassphrase generated =
            SqliteDistinctGeneratedSecret.generate(
                source,
                SqliteProtectedBookStagingCheckpoint.BACKUP_SECRET_GENERATION,
                ignored -> attempts.incrementAndGet(),
                () -> candidates.getAndIncrement() == 0 ? bytes("source") : bytes("replacement"))) {
      assertFalse(generated.hasSameSecretAs(source));
    }
    assertEquals(2, attempts.get());
  }

  @Test
  void rejectsAnEntropySourceThatCannotProduceADistinctSecret() {
    try (SqliteBookPassphrase source = passphrase("source")) {
      assertThrows(
          IllegalStateException.class,
          () ->
              SqliteDistinctGeneratedSecret.generate(
                  source,
                  SqliteProtectedBookStagingCheckpoint.RESTORE_SECRET_GENERATION,
                  SqliteProtectedBookStagingCheckpointListener.none(),
                  () -> bytes("source")));
    }
  }

  @Test
  void wipesRejectedCandidateBytesWhenTheEntropySourceYieldsMalformedUtf8() {
    byte[] malformedUtf8 = {(byte) 0xc3, (byte) 0x28};
    try (SqliteBookPassphrase source = passphrase("source")) {
      assertThrows(
          ContractFailureException.class,
          () ->
              SqliteDistinctGeneratedSecret.generate(
                  source,
                  SqliteProtectedBookStagingCheckpoint.BACKUP_SECRET_GENERATION,
                  SqliteProtectedBookStagingCheckpointListener.none(),
                  () -> malformedUtf8));
    }
    assertArrayEquals(new byte[malformedUtf8.length], malformedUtf8);
  }

  @Test
  void wipesCandidateBytesWhenTheCandidateFactoryReturnsNull() {
    byte[] candidateBytes = bytes("replacement");
    try (SqliteBookPassphrase source = passphrase("source")) {
      assertThrows(
          NullPointerException.class,
          () ->
              SqliteDistinctGeneratedSecret.generate(
                  source,
                  SqliteProtectedBookStagingCheckpoint.BACKUP_SECRET_GENERATION,
                  SqliteProtectedBookStagingCheckpointListener.none(),
                  () -> candidateBytes,
                  ignored -> nullCandidateFactoryResult()));
    }
    assertArrayEquals(new byte[candidateBytes.length], candidateBytes);
  }

  @Test
  void permitsANullCandidateByteBufferWhenTheFactoryDoesNotUseIt() {
    try (SqliteBookPassphrase source = passphrase("source");
        SqliteBookPassphrase generated =
            SqliteDistinctGeneratedSecret.generate(
                source,
                SqliteProtectedBookStagingCheckpoint.BACKUP_SECRET_GENERATION,
                SqliteProtectedBookStagingCheckpointListener.none(),
                () -> null,
                ignored -> directDistinctCandidate())) {
      assertFalse(generated.hasSameSecretAs(source));
    }
  }

  @Test
  void propagatesCandidateCloseFailureAfterZeroizingTheAcceptedCandidateBytes() {
    byte[] candidateBytes = bytes("replacement");
    RuntimeException closeFailure = new RuntimeException("candidate close failure");
    try (SqliteBookPassphrase source = passphrase("source")) {
      RuntimeException actualFailure =
          assertThrows(
              RuntimeException.class,
              () ->
                  SqliteDistinctGeneratedSecret.generate(
                      source,
                      SqliteProtectedBookStagingCheckpoint.BACKUP_SECRET_GENERATION,
                      SqliteProtectedBookStagingCheckpointListener.none(),
                      () -> candidateBytes,
                      bytes -> closeFailingCandidate(bytes, closeFailure)));
      assertSame(closeFailure, actualFailure);
    }
    assertArrayEquals(new byte[candidateBytes.length], candidateBytes);
  }

  @Test
  void closesTheUntransferredGeneratedSecretWhenCandidateCloseFails() {
    byte[] candidateBytes = bytes("replacement");
    RuntimeException closeFailure = new RuntimeException("candidate close failure");
    try (SqliteBookPassphrase source = passphrase("source");
        SqliteBookPassphrase generatedSecret = passphrase("generated secret")) {
      RuntimeException actualFailure =
          assertThrows(
              RuntimeException.class,
              () ->
                  SqliteDistinctGeneratedSecret.generate(
                      source,
                      SqliteProtectedBookStagingCheckpoint.BACKUP_SECRET_GENERATION,
                      SqliteProtectedBookStagingCheckpointListener.none(),
                      () -> candidateBytes,
                      bytes ->
                          copiedSecretAndCloseFailingCandidate(
                              bytes, generatedSecret, closeFailure)));
      assertSame(closeFailure, actualFailure);
      assertArrayEquals(new byte[generatedSecret.byteLength()], generatedSecret.utf8BytesCopy());
    }
    assertArrayEquals(new byte[candidateBytes.length], candidateBytes);
  }

  @Test
  void propagatesCandidateCloseFailureAfterRejectingACollidingCandidate() {
    byte[] candidateBytes = bytes("source");
    RuntimeException closeFailure = new RuntimeException("candidate close failure");
    try (SqliteBookPassphrase source = passphrase("source")) {
      RuntimeException actualFailure =
          assertThrows(
              RuntimeException.class,
              () ->
                  SqliteDistinctGeneratedSecret.generate(
                      source,
                      SqliteProtectedBookStagingCheckpoint.BACKUP_SECRET_GENERATION,
                      SqliteProtectedBookStagingCheckpointListener.none(),
                      () -> candidateBytes,
                      bytes -> closeFailingCandidate(bytes, closeFailure)));
      assertSame(closeFailure, actualFailure);
    }
    assertArrayEquals(new byte[candidateBytes.length], candidateBytes);
  }

  @Test
  void preservesCandidateUseFailureAndSuppressesTheCloseFailure() {
    byte[] candidateBytes = bytes("replacement");
    RuntimeException useFailure = new RuntimeException("candidate use failure");
    RuntimeException closeFailure = new RuntimeException("candidate close failure");
    try (SqliteBookPassphrase source = passphrase("source")) {
      RuntimeException actualFailure =
          assertThrows(
              RuntimeException.class,
              () ->
                  SqliteDistinctGeneratedSecret.generate(
                      source,
                      SqliteProtectedBookStagingCheckpoint.BACKUP_SECRET_GENERATION,
                      SqliteProtectedBookStagingCheckpointListener.none(),
                      () -> candidateBytes,
                      bytes -> useAndCloseFailingCandidate(bytes, useFailure, closeFailure)));
      assertSame(useFailure, actualFailure);
      assertEquals(1, actualFailure.getSuppressed().length);
      assertSame(closeFailure, actualFailure.getSuppressed()[0]);
    }
    assertArrayEquals(new byte[candidateBytes.length], candidateBytes);
  }

  @Test
  void preservesCandidateUseFailureWhenCloseSucceeds() {
    byte[] candidateBytes = bytes("replacement");
    RuntimeException useFailure = new RuntimeException("candidate use failure");
    try (SqliteBookPassphrase source = passphrase("source")) {
      RuntimeException actualFailure =
          assertThrows(
              RuntimeException.class,
              () ->
                  SqliteDistinctGeneratedSecret.generate(
                      source,
                      SqliteProtectedBookStagingCheckpoint.BACKUP_SECRET_GENERATION,
                      SqliteProtectedBookStagingCheckpointListener.none(),
                      () -> candidateBytes,
                      bytes -> useFailingCandidate(bytes, useFailure)));
      assertSame(useFailure, actualFailure);
      assertEquals(0, actualFailure.getSuppressed().length);
    }
    assertArrayEquals(new byte[candidateBytes.length], candidateBytes);
  }

  @Test
  void preservesCandidateErrorWhenCloseSucceeds() {
    byte[] candidateBytes = bytes("replacement");
    AssertionError useFailure = new AssertionError("candidate error");
    try (SqliteBookPassphrase source = passphrase("source")) {
      AssertionError actualFailure =
          assertThrows(
              AssertionError.class,
              () ->
                  SqliteDistinctGeneratedSecret.generate(
                      source,
                      SqliteProtectedBookStagingCheckpoint.BACKUP_SECRET_GENERATION,
                      SqliteProtectedBookStagingCheckpointListener.none(),
                      () -> candidateBytes,
                      bytes -> errorAndClosingCandidate(bytes, useFailure)));
      assertSame(useFailure, actualFailure);
      assertEquals(0, actualFailure.getSuppressed().length);
    }
    assertArrayEquals(new byte[candidateBytes.length], candidateBytes);
  }

  @Test
  void preservesCandidateErrorAndSuppressesTheCloseFailure() {
    byte[] candidateBytes = bytes("replacement");
    AssertionError useFailure = new AssertionError("candidate error");
    RuntimeException closeFailure = new RuntimeException("candidate close failure");
    try (SqliteBookPassphrase source = passphrase("source")) {
      AssertionError actualFailure =
          assertThrows(
              AssertionError.class,
              () ->
                  SqliteDistinctGeneratedSecret.generate(
                      source,
                      SqliteProtectedBookStagingCheckpoint.BACKUP_SECRET_GENERATION,
                      SqliteProtectedBookStagingCheckpointListener.none(),
                      () -> candidateBytes,
                      bytes -> errorAndCloseFailingCandidate(bytes, useFailure, closeFailure)));
      assertSame(useFailure, actualFailure);
      assertEquals(1, actualFailure.getSuppressed().length);
      assertSame(closeFailure, actualFailure.getSuppressed()[0]);
    }
    assertArrayEquals(new byte[candidateBytes.length], candidateBytes);
  }

  @Test
  void preservesCandidateCopyFailureAndSuppressesTheCloseFailure() {
    byte[] candidateBytes = bytes("replacement");
    RuntimeException copyFailure = new RuntimeException("candidate copy failure");
    RuntimeException closeFailure = new RuntimeException("candidate close failure");
    try (SqliteBookPassphrase source = passphrase("source")) {
      RuntimeException actualFailure =
          assertThrows(
              RuntimeException.class,
              () ->
                  SqliteDistinctGeneratedSecret.generate(
                      source,
                      SqliteProtectedBookStagingCheckpoint.BACKUP_SECRET_GENERATION,
                      SqliteProtectedBookStagingCheckpointListener.none(),
                      () -> candidateBytes,
                      bytes -> copyAndCloseFailingCandidate(bytes, copyFailure, closeFailure)));
      assertSame(copyFailure, actualFailure);
      assertEquals(1, actualFailure.getSuppressed().length);
      assertSame(closeFailure, actualFailure.getSuppressed()[0]);
    }
    assertArrayEquals(new byte[candidateBytes.length], candidateBytes);
  }

  private static SqliteDistinctGeneratedSecret.GeneratedSecretCandidate closeFailingCandidate(
      byte[] bytes, RuntimeException closeFailure) {
    return new CloseFailingCandidate(bytes, closeFailure);
  }

  private static SqliteDistinctGeneratedSecret.GeneratedSecretCandidate directDistinctCandidate() {
    return new SqliteDistinctGeneratedSecret.GeneratedSecretCandidate() {
      @Override
      public boolean hasSameSecretAs(SqliteBookPassphrase sourcePassphrase) {
        return false;
      }

      @Override
      public SqliteBookPassphrase copy() {
        return passphrase("replacement");
      }

      @Override
      public void close() {}
    };
  }

  private static SqliteDistinctGeneratedSecret.GeneratedSecretCandidate
      copiedSecretAndCloseFailingCandidate(
          byte[] candidateBytes,
          SqliteBookPassphrase generatedSecret,
          RuntimeException closeFailure) {
    return new CopiedSecretAndCloseFailingCandidate(candidateBytes, generatedSecret, closeFailure);
  }

  /** Returns a precreated generated secret but fails when the candidate's ownership ends. */
  private static final class CopiedSecretAndCloseFailingCandidate
      implements SqliteDistinctGeneratedSecret.GeneratedSecretCandidate {
    private final SqliteBookPassphrase candidate;
    private final RuntimeException closeFailure;
    private final SqliteBookPassphrase generatedSecret;

    private CopiedSecretAndCloseFailingCandidate(
        byte[] candidateBytes,
        SqliteBookPassphrase generatedSecret,
        RuntimeException closeFailure) {
      candidate = SqliteBookPassphrase.fromUtf8Bytes("close-failure candidate", candidateBytes);
      this.generatedSecret = generatedSecret;
      this.closeFailure = closeFailure;
    }

    @Override
    public boolean hasSameSecretAs(SqliteBookPassphrase sourcePassphrase) {
      return false;
    }

    @Override
    public SqliteBookPassphrase copy() {
      return generatedSecret;
    }

    @Override
    public void close() {
      candidate.close();
      throw closeFailure;
    }
  }

  /** Owns one generated passphrase while deliberately surfacing its close failure. */
  private static final class CloseFailingCandidate
      implements SqliteDistinctGeneratedSecret.GeneratedSecretCandidate {
    private final SqliteBookPassphrase candidate;
    private final RuntimeException closeFailure;

    private CloseFailingCandidate(byte[] bytes, RuntimeException closeFailure) {
      candidate = SqliteBookPassphrase.fromUtf8Bytes("close-failure candidate", bytes);
      this.closeFailure = closeFailure;
    }

    @Override
    public boolean hasSameSecretAs(SqliteBookPassphrase sourcePassphrase) {
      return candidate.hasSameSecretAs(sourcePassphrase);
    }

    @Override
    public SqliteBookPassphrase copy() {
      return candidate.copy();
    }

    @Override
    public void close() {
      candidate.close();
      throw closeFailure;
    }
  }

  private static SqliteDistinctGeneratedSecret.GeneratedSecretCandidate useAndCloseFailingCandidate(
      byte[] bytes, RuntimeException useFailure, RuntimeException closeFailure) {
    return new SqliteDistinctGeneratedSecret.GeneratedSecretCandidate() {
      @Override
      public boolean hasSameSecretAs(SqliteBookPassphrase sourcePassphrase) {
        throw useFailure;
      }

      @Override
      public SqliteBookPassphrase copy() {
        throw new AssertionError("copy must not follow a candidate use failure");
      }

      @Override
      public void close() {
        Arrays.fill(bytes, (byte) 0);
        throw closeFailure;
      }
    };
  }

  private static SqliteDistinctGeneratedSecret.GeneratedSecretCandidate useFailingCandidate(
      byte[] bytes, RuntimeException useFailure) {
    return new SqliteDistinctGeneratedSecret.GeneratedSecretCandidate() {
      @Override
      public boolean hasSameSecretAs(SqliteBookPassphrase sourcePassphrase) {
        throw useFailure;
      }

      @Override
      public SqliteBookPassphrase copy() {
        throw new AssertionError("copy must not follow a candidate use failure");
      }

      @Override
      public void close() {
        Arrays.fill(bytes, (byte) 0);
      }
    };
  }

  private static SqliteDistinctGeneratedSecret.GeneratedSecretCandidate errorAndClosingCandidate(
      byte[] bytes, AssertionError useFailure) {
    return new SqliteDistinctGeneratedSecret.GeneratedSecretCandidate() {
      @Override
      public boolean hasSameSecretAs(SqliteBookPassphrase sourcePassphrase) {
        throw useFailure;
      }

      @Override
      public SqliteBookPassphrase copy() {
        throw new AssertionError("copy must not follow a candidate error");
      }

      @Override
      public void close() {
        Arrays.fill(bytes, (byte) 0);
      }
    };
  }

  private static SqliteDistinctGeneratedSecret.GeneratedSecretCandidate
      errorAndCloseFailingCandidate(
          byte[] bytes, AssertionError useFailure, RuntimeException closeFailure) {
    return new SqliteDistinctGeneratedSecret.GeneratedSecretCandidate() {
      @Override
      public boolean hasSameSecretAs(SqliteBookPassphrase sourcePassphrase) {
        throw useFailure;
      }

      @Override
      public SqliteBookPassphrase copy() {
        throw new AssertionError("copy must not follow a candidate error");
      }

      @Override
      public void close() {
        Arrays.fill(bytes, (byte) 0);
        throw closeFailure;
      }
    };
  }

  private static SqliteDistinctGeneratedSecret.GeneratedSecretCandidate
      copyAndCloseFailingCandidate(
          byte[] bytes, RuntimeException copyFailure, RuntimeException closeFailure) {
    return new SqliteDistinctGeneratedSecret.GeneratedSecretCandidate() {
      @Override
      public boolean hasSameSecretAs(SqliteBookPassphrase sourcePassphrase) {
        return false;
      }

      @Override
      public SqliteBookPassphrase copy() {
        throw copyFailure;
      }

      @Override
      public void close() {
        Arrays.fill(bytes, (byte) 0);
        throw closeFailure;
      }
    };
  }

  private static SqliteBookPassphrase passphrase(String value) {
    return SqliteBookPassphrase.fromUtf8Bytes("test passphrase", bytes(value));
  }

  private static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  /** Deliberately violates the non-null factory contract to exercise the production null guard. */
  @SuppressWarnings("NullAway")
  private static SqliteDistinctGeneratedSecret.GeneratedSecretCandidate
      nullCandidateFactoryResult() {
    return null;
  }
}
