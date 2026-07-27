package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.attestation.AttestationAdmissionRejectedException;
import dev.erst.fingrind.core.attestation.AttestationAuthorizationFailure;
import dev.erst.fingrind.core.attestation.AttestationCapability;
import dev.erst.fingrind.core.attestation.AttestationCredentialPurpose;
import dev.erst.fingrind.core.attestation.AttestationCredentialSource;
import dev.erst.fingrind.core.attestation.AttestationKeyFiles;
import dev.erst.fingrind.core.attestation.AttestationRegistryMutation;
import dev.erst.fingrind.core.attestation.AttestationStaleHeadException;
import dev.erst.fingrind.core.attestation.AttestationSystemWorkflowKind;
import dev.erst.fingrind.executor.maintenance.AttestedProtectedBookLifecycleWorkflow;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.maintenance.ProtectedBookAccess;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException;
import dev.erst.fingrind.executor.maintenance.ProtectedBookRegistryMutationOutcome;
import dev.erst.fingrind.executor.maintenance.ProtectedBookVerificationFailure;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.LeaseBusy;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Proves every public credential and policy mutation reaches the attested store boundary. */
class AttestedProtectedBookRegistryMutationWorkflowTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneOffset.UTC);

  @TempDir Path temporaryDirectory;

  @BeforeEach
  void canonicalizeTemporaryDirectory() throws IOException {
    temporaryDirectory = temporaryDirectory.toRealPath();
  }

  @Test
  void appendsEveryCredentialAndPolicyMutationAsOneVerifiableOperation() throws Exception {
    AttestationMaintenanceTestSupport.CredentialFixture founder =
        AttestationMaintenanceTestSupport.createCredential(temporaryDirectory);
    Path enrolledKeyPath = temporaryDirectory.resolve("enrolled.fgatk");
    Path rolloverKeyPath = temporaryDirectory.resolve("rollover.fgatk");
    Path standbyKeyPath = temporaryDirectory.resolve("standby.fgatk");
    char[] passphrase = "registry mutation test key".toCharArray();
    try {
      AttestationKeyFiles.create(enrolledKeyPath, passphrase);
      AttestationKeyFiles.create(rolloverKeyPath, passphrase);
      AttestationKeyFiles.create(standbyKeyPath, passphrase);
    } finally {
      java.util.Arrays.fill(passphrase, '\0');
    }
    var enrolled = AttestationKeyFiles.loadPublicCredential(enrolledKeyPath);
    var rollover = AttestationKeyFiles.loadPublicCredential(rolloverKeyPath);
    var standby = AttestationKeyFiles.loadPublicCredential(standbyKeyPath);
    UUID enrolledPrincipal = UUID.fromString("01234567-89ab-4cde-8fab-0123456789ab");
    UUID standbyPrincipal = UUID.fromString("fedcba98-7654-4cde-8fab-0123456789ab");
    Path bookPath = temporaryDirectory.resolve("book.sqlite");
    AttestationMaintenanceTestSupport.Store store =
        new AttestationMaintenanceTestSupport.Store(
            bookPath, List.of(AttestationMaintenanceTestSupport.genesis(founder, CLOCK.instant())));
    AttestedProtectedBookLifecycleWorkflow workflow =
        new AttestedProtectedBookLifecycleWorkflow(CLOCK, store);
    ProtectedBookAccess access =
        ProtectedBookAccess.fromPublished(
            AttestationMaintenanceTestSupport.bookAccess(bookPath, founder));

    try (var session = founder.openSession()) {
      assertMutated(
          workflow.mutateRegistry(
              access,
              new AttestationRegistryMutation.EnrollKey(
                  enrolledPrincipal, enrolled, AttestationCredentialPurpose.SYSTEM),
              session),
          "enroll-key",
          1);
      assertMutated(
          workflow.mutateRegistry(
              access,
              new AttestationRegistryMutation.RolloverKey(
                  enrolledPrincipal, rollover, AttestationCredentialPurpose.SYSTEM, enrolled),
              session),
          "rollover-key",
          2);
      assertMutated(
          workflow.mutateRegistry(
              access,
              new AttestationRegistryMutation.EnrollKey(
                  standbyPrincipal, standby, AttestationCredentialPurpose.SYSTEM),
              session),
          "enroll-key",
          3);
      assertMutated(
          workflow.mutateRegistry(
              access,
              new AttestationRegistryMutation.RevokeKey(
                  enrolledPrincipal, rollover, java.util.Optional.of("device retired")),
              session),
          "revoke-key",
          4);
      assertMutated(
          workflow.mutateRegistry(
              access,
              new AttestationRegistryMutation.AlterPolicy(
                  List.of(
                      new AttestationRegistryMutation.PolicyRule(
                          AttestationCapability.CLOSE_PERIOD, 1)),
                  List.of(
                      new AttestationRegistryMutation.CapabilityGrant(
                          standbyPrincipal,
                          AttestationCapability.CLOSE_PERIOD,
                          dev.erst.fingrind.core.attestation.AttestationGrantState.GRANT)),
                  List.of(
                      new AttestationRegistryMutation.SystemWorkflowPolicy(
                          UUID.fromString("11111111-2222-4333-8444-555555555555"),
                          AttestationSystemWorkflowKind.INTERIM_RESULT_SWEEP,
                          "3000",
                          null,
                          null,
                          true))),
              session),
          "alter-policy",
          5);
    }
  }

  @Test
  void classifiesLiveAuthorizationRefusalWithoutPretendingStorageFailed() throws Exception {
    AttestationMaintenanceTestSupport.CredentialFixture founder =
        AttestationMaintenanceTestSupport.createCredential(temporaryDirectory);
    Path unrecognizedKeyPath = temporaryDirectory.resolve("unrecognized.fgatk");
    Path unrecognizedPassphrasePath = temporaryDirectory.resolve("unrecognized.passphrase");
    char[] passphrase = "unrecognized credential passphrase".toCharArray();
    try {
      AttestationKeyFiles.create(unrecognizedKeyPath, passphrase);
      Files.writeString(unrecognizedPassphrasePath, "unrecognized credential passphrase\n");
    } finally {
      java.util.Arrays.fill(passphrase, '\0');
    }
    Path bookPath = temporaryDirectory.resolve("book.sqlite");
    AttestationMaintenanceTestSupport.Store store =
        new AttestationMaintenanceTestSupport.Store(
            bookPath, List.of(AttestationMaintenanceTestSupport.genesis(founder, CLOCK.instant())));
    AttestedProtectedBookLifecycleWorkflow workflow =
        new AttestedProtectedBookLifecycleWorkflow(CLOCK, store);
    ProtectedBookAccess access =
        ProtectedBookAccess.fromPublished(
            AttestationMaintenanceTestSupport.bookAccess(bookPath, founder));
    AttestationCredentialSource unrecognizedSource =
        new AttestationCredentialSource(
            dev.erst.fingrind.core.attestation.AttestationCustodian.FILE_PKCS8,
            UUID.fromString("01234567-89ab-4cde-8fab-0123456789ab"),
            unrecognizedKeyPath,
            unrecognizedPassphrasePath);

    try (var session = AttestationSigningSessionFactory.open(List.of(unrecognizedSource))) {
      ProtectedBookRegistryMutationOutcome.AuthorizationRejected rejected =
          assertInstanceOf(
              ProtectedBookRegistryMutationOutcome.AuthorizationRejected.class,
              accepted(
                  workflow.mutateRegistry(
                      access,
                      new AttestationRegistryMutation.AlterPolicy(
                          List.of(
                              new AttestationRegistryMutation.PolicyRule(
                                  AttestationCapability.CLOSE_PERIOD, 1)),
                          List.of(),
                          List.of()),
                      session)));
      assertEquals(
          dev.erst.fingrind.core.attestation.AttestationAuthorizationFailure.KEY_NOT_ENROLLED,
          rejected.failure());
    }
  }

  @Test
  void rejectsDuplicateRegistryEnrollmentBeforeSigningOrAppending() throws Exception {
    AttestationMaintenanceTestSupport.CredentialFixture founder =
        AttestationMaintenanceTestSupport.createCredential(temporaryDirectory);
    Path enrolledKeyPath = temporaryDirectory.resolve("enrolled.fgatk");
    char[] passphrase = "duplicate enrollment test key".toCharArray();
    try {
      AttestationKeyFiles.create(enrolledKeyPath, passphrase);
    } finally {
      java.util.Arrays.fill(passphrase, '\0');
    }
    var enrolled = AttestationKeyFiles.loadPublicCredential(enrolledKeyPath);
    UUID enrolledPrincipal = UUID.fromString("01234567-89ab-4cde-8fab-0123456789ab");
    Path bookPath = temporaryDirectory.resolve("book.sqlite");
    AttestationMaintenanceTestSupport.Store store = store(bookPath, founder);
    ProtectedBookAccess access =
        ProtectedBookAccess.fromPublished(
            AttestationMaintenanceTestSupport.bookAccess(bookPath, founder));
    AttestationRegistryMutation.EnrollKey enrollment =
        new AttestationRegistryMutation.EnrollKey(
            enrolledPrincipal, enrolled, AttestationCredentialPurpose.OPERATOR);

    try (var session = founder.openSession()) {
      assertMutated(workflow(store).mutateRegistry(access, enrollment, session), "enroll-key", 1);

      ProtectedBookRegistryMutationOutcome.AuthorizationRejected duplicatePrincipal =
          assertInstanceOf(
              ProtectedBookRegistryMutationOutcome.AuthorizationRejected.class,
              accepted(workflow(store).mutateRegistry(access, enrollment, session)));
      assertEquals(
          AttestationAuthorizationFailure.DUPLICATE_PRINCIPAL, duplicatePrincipal.failure());

      ProtectedBookRegistryMutationOutcome.AuthorizationRejected duplicateKey =
          assertInstanceOf(
              ProtectedBookRegistryMutationOutcome.AuthorizationRejected.class,
              accepted(
                  workflow(store)
                      .mutateRegistry(
                          access,
                          new AttestationRegistryMutation.EnrollKey(
                              UUID.fromString("01234567-89ab-4cde-8fab-0123456789ac"),
                              enrolled,
                              AttestationCredentialPurpose.OPERATOR),
                          session)));
      assertEquals(AttestationAuthorizationFailure.DUPLICATE_KEY, duplicateKey.failure());
    }
    assertEquals(2, store.liveEvidence().size());
  }

  @Test
  void classifiesRegistryMutationAdmissionRejections() throws Exception {
    AttestationMaintenanceTestSupport.CredentialFixture founder =
        AttestationMaintenanceTestSupport.createCredential(temporaryDirectory);
    Path bookPath = temporaryDirectory.resolve("book.sqlite");
    ProtectedBookAccess access =
        ProtectedBookAccess.fromPublished(
            AttestationMaintenanceTestSupport.bookAccess(bookPath, founder));

    AttestationMaintenanceTestSupport.Store blocked = store(bookPath, founder);
    blocked.setLiveBlockingArtifacts(List.of(bookPath.resolveSibling("book.sqlite-wal")));
    try (var session = founder.openSession()) {
      ProtectedBookRegistryMutationOutcome.Rejected rejected =
          assertInstanceOf(
              ProtectedBookRegistryMutationOutcome.Rejected.class,
              accepted(workflow(blocked).mutateRegistry(access, policyMutation(), session)));
      assertInstanceOf(
          ProtectedBookMaintenanceRejection.BookHasBlockingArtifacts.class, rejected.rejection());
    }

    AttestationMaintenanceTestSupport.Store busy = store(bookPath, founder);
    busy.setManagedLease(new LeaseBusy(bookPath));
    try (var session = founder.openSession()) {
      ProtectedBookRegistryMutationOutcome.Rejected rejected =
          assertInstanceOf(
              ProtectedBookRegistryMutationOutcome.Rejected.class,
              accepted(workflow(busy).mutateRegistry(access, policyMutation(), session)));
      assertInstanceOf(ProtectedBookMaintenanceRejection.ArtifactBusy.class, rejected.rejection());
    }
  }

  @Test
  void rejectsMalformedHistoricalEvidenceBeforeRegistryCandidateAdmission() throws Exception {
    AttestationMaintenanceTestSupport.CredentialFixture founder =
        AttestationMaintenanceTestSupport.createCredential(temporaryDirectory);
    Path bookPath = temporaryDirectory.resolve("book.sqlite");
    ProtectedBookAccess access =
        ProtectedBookAccess.fromPublished(
            AttestationMaintenanceTestSupport.bookAccess(bookPath, founder));
    AttestationMaintenanceTestSupport.Store store = store(bookPath, founder);
    store.setLiveEvidence(List.of());

    try (var session = founder.openSession()) {
      ProtectedBookRegistryMutationOutcome.Rejected rejected =
          assertInstanceOf(
              ProtectedBookRegistryMutationOutcome.Rejected.class,
              accepted(workflow(store).mutateRegistry(access, policyMutation(), session)));
      ProtectedBookMaintenanceRejection.ArtifactVerificationFailed failure =
          assertInstanceOf(
              ProtectedBookMaintenanceRejection.ArtifactVerificationFailed.class,
              rejected.rejection());
      assertEquals(ProtectedBookMaintenanceArtifactRole.LIVE_BOOK, failure.artifactRole());
      assertEquals(bookPath, failure.artifactPath());
      assertEquals(
          ProtectedBookVerificationFailure.PROTECTED_BOOK_VERIFICATION_FAILED,
          failure.verificationFailure());
    }
  }

  @Test
  void preservesRegistryMutationControlFlowAndClassifiesStorageFailures() throws Exception {
    AttestationMaintenanceTestSupport.CredentialFixture founder =
        AttestationMaintenanceTestSupport.createCredential(temporaryDirectory);
    Path bookPath = temporaryDirectory.resolve("book.sqlite");
    ProtectedBookAccess access =
        ProtectedBookAccess.fromPublished(
            AttestationMaintenanceTestSupport.bookAccess(bookPath, founder));

    AttestationMaintenanceTestSupport.Store stale = store(bookPath, founder);
    stale
        .overrides()
        .appendFailure(
            new AttestationStaleHeadException(new byte[] {1}, new byte[] {2}, BigInteger.ONE));
    try (var session = founder.openSession()) {
      assertThrows(
          AttestationStaleHeadException.class,
          () -> workflow(stale).mutateRegistry(access, policyMutation(), session));
    }

    AttestationMaintenanceTestSupport.Store rejected = store(bookPath, founder);
    rejected
        .overrides()
        .appendFailure(
            new ProtectedBookMaintenanceRejectionException(
                new ProtectedBookMaintenanceRejection.ArtifactBusy(
                    dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole
                        .LIVE_BOOK,
                    bookPath)));
    try (var session = founder.openSession()) {
      ProtectedBookRegistryMutationOutcome.Rejected outcome =
          assertInstanceOf(
              ProtectedBookRegistryMutationOutcome.Rejected.class,
              accepted(workflow(rejected).mutateRegistry(access, policyMutation(), session)));
      assertInstanceOf(ProtectedBookMaintenanceRejection.ArtifactBusy.class, outcome.rejection());
    }

    AttestationMaintenanceTestSupport.Store authorizationRejected = store(bookPath, founder);
    authorizationRejected
        .overrides()
        .appendFailure(
            AttestationAdmissionRejectedException.from(
                AttestationAuthorizationFailure.CREDENTIAL_PURPOSE_INVALID));
    try (var session = founder.openSession()) {
      ProtectedBookRegistryMutationOutcome.AuthorizationRejected outcome =
          assertInstanceOf(
              ProtectedBookRegistryMutationOutcome.AuthorizationRejected.class,
              accepted(
                  workflow(authorizationRejected)
                      .mutateRegistry(access, policyMutation(), session)));
      assertEquals(AttestationAuthorizationFailure.CREDENTIAL_PURPOSE_INVALID, outcome.failure());
    }

    AttestationMaintenanceTestSupport.Store failed = store(bookPath, founder);
    failed.overrides().appendFailure(new IllegalStateException("registry storage unavailable"));
    try (var session = founder.openSession()) {
      assertInstanceOf(
          MaintenanceDecision.Failed.class,
          workflow(failed).mutateRegistry(access, policyMutation(), session));
    }
  }

  private static AttestationRegistryMutation.AlterPolicy policyMutation() {
    return new AttestationRegistryMutation.AlterPolicy(
        List.of(new AttestationRegistryMutation.PolicyRule(AttestationCapability.CLOSE_PERIOD, 1)),
        List.of(),
        List.of());
  }

  private AttestationMaintenanceTestSupport.Store store(
      Path bookPath, AttestationMaintenanceTestSupport.CredentialFixture founder) {
    return new AttestationMaintenanceTestSupport.Store(
        bookPath, List.of(AttestationMaintenanceTestSupport.genesis(founder, CLOCK.instant())));
  }

  private static AttestedProtectedBookLifecycleWorkflow workflow(
      AttestationMaintenanceTestSupport.Store store) {
    return new AttestedProtectedBookLifecycleWorkflow(CLOCK, store);
  }

  private static void assertMutated(
      MaintenanceDecision<ProtectedBookRegistryMutationOutcome> decision,
      String operationKind,
      int headOrder) {
    ProtectedBookRegistryMutationOutcome outcome = accepted(decision);
    ProtectedBookRegistryMutationOutcome.Mutated mutated =
        assertInstanceOf(
            ProtectedBookRegistryMutationOutcome.Mutated.class,
            outcome,
            () -> "outcome=" + outcome);
    assertEquals(operationKind, mutated.operationKind());
    assertEquals(java.math.BigInteger.valueOf(headOrder), mutated.headOrder());
  }

  private static <T> T accepted(MaintenanceDecision<T> decision) {
    return decision.fold(
        value -> value,
        failure -> {
          throw new AssertionError(failure);
        });
  }
}
