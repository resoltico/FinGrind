package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import dev.erst.fingrind.core.PublicationTransactionOwnerContext;
import dev.erst.fingrind.core.attestation.AttestationBackupAcknowledgement;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationRecoveryRequest;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Proves that journal recovery correlation binds every immutable protected-book operation fact. */
class SqliteProtectedBookPublicationOwnerContextTest {
  private static final Path BOOK_TARGET = Path.of("target", "book.sqlite");
  private static final Path SECRET_TARGET = Path.of("target", "book.key");

  @Test
  void bindsTheExactBackupIdentityTargetPairAndTargetPolicy() {
    ProtectedBookPairPublicationRecoveryRequest.Backup request =
        new ProtectedBookPairPublicationRecoveryRequest.Backup(
            Path.of("source.sqlite"), new UUID(3L, 4L));
    PublicationTransactionOwnerContext original =
        context(request, BOOK_TARGET, SECRET_TARGET, RestoredBookTargetPolicy.REQUIRE_ABSENT);

    assertEquals(
        original,
        context(request, BOOK_TARGET, SECRET_TARGET, RestoredBookTargetPolicy.REQUIRE_ABSENT));
    assertNotEquals(
        original,
        context(
            new ProtectedBookPairPublicationRecoveryRequest.Backup(
                Path.of("source.sqlite"), new UUID(3L, 5L)),
            BOOK_TARGET,
            SECRET_TARGET,
            RestoredBookTargetPolicy.REQUIRE_ABSENT));
    assertNotEquals(
        original,
        context(
            request,
            BOOK_TARGET.resolveSibling("other.sqlite"),
            SECRET_TARGET,
            RestoredBookTargetPolicy.REQUIRE_ABSENT));
    assertNotEquals(
        original,
        context(request, BOOK_TARGET, SECRET_TARGET, RestoredBookTargetPolicy.REPLACE_SELECTED));
  }

  @Test
  void bindsRestoreAcknowledgementDigestAndSourceHead() {
    PublicationTransactionOwnerContext original =
        context(
            new ProtectedBookPairPublicationRecoveryRequest.Restore(
                Path.of("backup.sqlite"),
                Path.of("backup.key"),
                acknowledgement(new byte[32], new byte[32])),
            BOOK_TARGET,
            SECRET_TARGET,
            RestoredBookTargetPolicy.REQUIRE_ABSENT);
    byte[] changedDigest = new byte[32];
    changedDigest[0] = 1;
    byte[] changedHead = new byte[32];
    changedHead[31] = 1;

    assertNotEquals(
        original,
        context(
            new ProtectedBookPairPublicationRecoveryRequest.Restore(
                Path.of("backup.sqlite"),
                Path.of("backup.key"),
                acknowledgement(changedDigest, new byte[32])),
            BOOK_TARGET,
            SECRET_TARGET,
            RestoredBookTargetPolicy.REQUIRE_ABSENT));
    assertNotEquals(
        original,
        context(
            new ProtectedBookPairPublicationRecoveryRequest.Restore(
                Path.of("backup.sqlite"),
                Path.of("backup.key"),
                acknowledgement(new byte[32], changedHead)),
            BOOK_TARGET,
            SECRET_TARGET,
            RestoredBookTargetPolicy.REQUIRE_ABSENT));
  }

  @Test
  void bindsRekeyOperationAndExactFinalPairWithoutMutableSourceState() {
    PublicationTransactionOwnerContext original =
        context(
            ProtectedBookPairPublicationRecoveryRequest.Rekey.INSTANCE,
            BOOK_TARGET,
            SECRET_TARGET,
            RestoredBookTargetPolicy.REPLACE_SELECTED);

    assertEquals(
        original,
        context(
            ProtectedBookPairPublicationRecoveryRequest.Rekey.INSTANCE,
            BOOK_TARGET,
            SECRET_TARGET,
            RestoredBookTargetPolicy.REPLACE_SELECTED));
    assertNotEquals(
        original,
        context(
            ProtectedBookPairPublicationRecoveryRequest.Rekey.INSTANCE,
            BOOK_TARGET.resolveSibling("other.sqlite"),
            SECRET_TARGET,
            RestoredBookTargetPolicy.REPLACE_SELECTED));
    assertNotEquals(
        original,
        context(
            ProtectedBookPairPublicationRecoveryRequest.Rekey.INSTANCE,
            BOOK_TARGET,
            SECRET_TARGET,
            RestoredBookTargetPolicy.REQUIRE_ABSENT));
    assertNotEquals(
        original,
        context(
            new ProtectedBookPairPublicationRecoveryRequest.Backup(
                Path.of("source.sqlite"), new UUID(3L, 4L)),
            BOOK_TARGET,
            SECRET_TARGET,
            RestoredBookTargetPolicy.REPLACE_SELECTED));
  }

  private static PublicationTransactionOwnerContext context(
      ProtectedBookPairPublicationRecoveryRequest request,
      Path bookTarget,
      Path secretTarget,
      RestoredBookTargetPolicy policy) {
    return SqliteProtectedBookPublicationOwnerContext.forPair(
        request, bookTarget, secretTarget, policy);
  }

  private static AttestationBackupAcknowledgement acknowledgement(byte[] digest, byte[] head) {
    return new AttestationBackupAcknowledgement(new UUID(5L, 6L), digest, BigInteger.ZERO, head);
  }
}
