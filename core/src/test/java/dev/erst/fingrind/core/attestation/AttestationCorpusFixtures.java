package dev.erst.fingrind.core.attestation;

import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.absent;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.bool;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.date;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.money;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.mutation;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.optionalText;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.text;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.token;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.u16;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.u32;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureFields.uuid;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureValues.backupEffect;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureValues.backupRequest;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureValues.binding;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureValues.bindingEffect;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureValues.bindingRequest;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureValues.command;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureValues.commandId;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureValues.genesisEffect;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureValues.genesisRequest;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureValues.grant;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureValues.grantEffect;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureValues.grantRequest;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureValues.initialPolicy;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureValues.journalLine;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureValues.policyEffect;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureValues.postingFact;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureValues.postingId;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureValues.preimage;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureValues.recordedAt;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureValues.rekeyEffect;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureValues.rekeyRequest;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureValues.restoreEffect;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureValues.restoreRequest;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureValues.revocationEffect;
import static dev.erst.fingrind.core.attestation.AttestationCorpusFixtureValues.revocationRequest;
import static dev.erst.fingrind.core.attestation.AttestationCorpusOperationBuilder.decodedOperation;
import static dev.erst.fingrind.core.attestation.AttestationCorpusOperationBuilder.envelope;
import static dev.erst.fingrind.core.attestation.AttestationCorpusOperationBuilder.operation;
import static dev.erst.fingrind.core.attestation.AttestationCorpusOperationBuilder.systemClose;

import dev.erst.fingrind.core.attestation.AttestationCorpusFixtureValues.Signer;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.EdECPrivateKeySpec;
import java.security.spec.NamedParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Builds the fixed B-series protected-book and artifact resources declared by the corpus. */
final class AttestationCorpusFixtures {
  static final UUID BOOK_ID = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
  static final UUID BACKUP_ID = UUID.fromString("ffeeddcc-bbaa-9988-7766-554433221100");
  static final UUID SWEEP_WORKFLOW_ID = UUID.fromString("40000000-0000-7000-8000-000000000001");
  static final UUID CLOSE_WORKFLOW_ID = UUID.fromString("40000000-0000-7000-8000-000000000002");
  static final Instant GENESIS_RECORDED_AT = Instant.parse("2026-12-31T03:00:00.000Z");
  static final Signer A =
      signer(
          "10213243-5465-7687-98a9-babcbddceeff",
          "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f",
          "302a300506032b657003210003a107bff3ce10be1d70dd18e74bc09967e4d6309ba50d5f1ddc8664125531b8");
  static final Signer B =
      signer(
          "11223344-5566-7788-99aa-bbccddeeff00",
          "202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f",
          "302a300506032b657003210029acbae141bccaf0b22e1a94d34d0bc7361e526d0bfe12c89794bc9322966dd7");
  static final Signer C =
      signer(
          "22334455-6677-8899-aabb-ccddeeff0011",
          "404142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f",
          "302a300506032b65700321002543b92ff1095511476adc8369db6ddc933665a11978dda1404ee1066ca9559d");
  static final Signer A2 =
      signerWithGeneratedPublic(
          A.principalId(), "606162636465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f");

  private AttestationCorpusFixtures() {}

  static AttestationCorpusResources.Book b01() {
    return book("B-01", List.of(genesis(List.of(A), initialPolicy(1, Map.of()))));
  }

  static AttestationCorpusResources.Book b02() {
    List<AttestationBookOperation> operations = new ArrayList<>();
    operations.add(
        genesis(
            List.of(A, B),
            initialPolicy(2, Map.of(AttestationCapability.POST, Integer.valueOf(2)))));
    operations.add(declareAccount(operations, "1000", "Cash", "asset", List.of(A, B)));
    operations.add(declareAccount(operations, "4000", "Service revenue", "income", List.of(A, B)));
    operations.add(
        sale(
            operations,
            "fixture-sale-1",
            "fixture-receipt-1",
            LocalDate.of(2026, 7, 17),
            postingId(1),
            commandId(1),
            List.of(A, B)));
    return book("B-02", operations);
  }

  static AttestationCorpusResources.Book b03() {
    List<AttestationBookOperation> operations = new ArrayList<>(b02().operations());
    operations.add(
        bindingOperation(
            operations,
            AttestationOperationKind.ENROLL_KEY,
            C,
            "enroll",
            "operator",
            null,
            List.of(A, B)));
    operations.add(
        alterPolicy(
            operations, List.of(grantChange(C, AttestationCapability.POST)), List.of(A, B)));
    operations.add(
        bindingOperation(
            operations,
            AttestationOperationKind.ROLLOVER_KEY,
            A2,
            "rollover",
            "operator",
            A.keyId(),
            List.of(A, B)));
    operations.add(
        sale(
            operations,
            "fixture-sale-2",
            "fixture-receipt-2",
            LocalDate.of(2026, 12, 31),
            postingId(7),
            commandId(7),
            List.of(B, C)));
    operations.add(revoke(operations, C, List.of(A2, B)));
    return book("B-03", operations);
  }

  static AttestationCorpusResources.Book b04() {
    List<AttestationBookOperation> operations = new ArrayList<>(b02().operations());
    operations.add(
        bindingOperation(
            operations,
            AttestationOperationKind.ENROLL_KEY,
            C,
            "enroll",
            "system",
            null,
            List.of(A, B)));
    operations.add(
        alterPolicy(
            operations,
            List.of(
                policyChange(AttestationCapability.CLOSE_PERIOD, 1),
                grantChange(C, AttestationCapability.CLOSE_PERIOD),
                workflowChange(SWEEP_WORKFLOW_ID, "interim-result-sweep", "3000", null, null),
                workflowChange(CLOSE_WORKFLOW_ID, "fiscal-year-close", "3000", "3100", "3200")),
            List.of(A, B)));
    operations.add(
        declareAccount(operations, "3000", "Current-year result holding", "equity", List.of(A, B)));
    operations.add(declareAccount(operations, "3100", "Owner capital", "equity", List.of(A, B)));
    operations.add(declareAccount(operations, "3200", "Retained result", "equity", List.of(A, B)));
    operations.add(
        systemClose(
            operations,
            AttestationOperationKind.INTERIM_RESULT_SWEEP,
            SWEEP_WORKFLOW_ID,
            LocalDate.of(2026, 12, 30),
            List.of(C)));
    operations.add(
        systemClose(
            operations,
            AttestationOperationKind.FISCAL_YEAR_CLOSE,
            CLOSE_WORKFLOW_ID,
            LocalDate.of(2026, 12, 31),
            List.of(C)));
    return book("B-04", operations);
  }

  static AttestationCorpusResources.Artifact b05Artifact() {
    AttestationCorpusResources.Book snapshot = b02();
    AttestationBookVerification verification = AttestationBookVerifier.verify(snapshot.decode());
    byte[] snapshotBytes = snapshot.encoded();
    AttestationBackupManifestPayload payload =
        new AttestationBackupManifestPayload(
            BOOK_ID,
            BACKUP_ID,
            verification.headOrder(),
            verification.head(),
            AttestationHash.sha256(snapshotBytes));
    AttestationEnvelope<AttestationBackupManifestPayload> manifest = envelope(payload, List.of(A));
    return new AttestationCorpusResources.Artifact(
        "B-05", new AttestationArtifactContainer(snapshotBytes, manifest).encoded());
  }

  static AttestationCorpusResources.Book b05() {
    List<AttestationBookOperation> operations = new ArrayList<>(b02().operations());
    AttestationCorpusResources.Artifact artifact = b05Artifact();
    AttestationHash sourceHead = operations.getLast().envelope().head();
    AttestationHash artifactDigest = AttestationHash.sha256(artifact.encoded());
    AttestationPreimage request =
        preimage(
            command(
                AttestationOperationKind.BACKUP_CREATED,
                AttestationSourceChannel.CLI,
                "fixture-b-05-4"),
            backupRequest(BACKUP_ID, artifactDigest, BigInteger.valueOf(3), sourceHead));
    AttestationPreimage effect =
        preimage(backupEffect(BACKUP_ID, artifactDigest, BigInteger.valueOf(3), sourceHead));
    operations.add(
        operation(
            operations, AttestationOperationKind.BACKUP_CREATED, request, effect, List.of(A)));
    return book("B-05-book", operations);
  }

  static AttestationCorpusResources.Book b06() {
    return restoredBook("B-06");
  }

  static AttestationCorpusResources.Book b07() {
    return restoredBook("B-07");
  }

  static AttestationCorpusResources.Restore b06Restore() {
    return new AttestationCorpusResources.Restore(
        "B-06", b05Artifact(), java.util.Optional.of(b05()), b06());
  }

  static AttestationCorpusResources.Restore b07Restore() {
    return new AttestationCorpusResources.Restore(
        "B-07", b05Artifact(), java.util.Optional.empty(), b07());
  }

  static AttestationCorpusResources.Book b10() {
    List<AttestationBookOperation> operations = new ArrayList<>(b02().operations());
    AttestationPreimage request =
        preimage(
            command(
                AttestationOperationKind.REKEY_BOOK,
                AttestationSourceChannel.CLI,
                "fixture-b-10-4"),
            rekeyRequest());
    AttestationPreimage effect = preimage(rekeyEffect(recordedAt(operations.size())));
    operations.add(
        operation(operations, AttestationOperationKind.REKEY_BOOK, request, effect, List.of(A, B)));
    return book("B-10", operations);
  }

  static AttestationCorpusResources.Receipt b11() {
    AttestationCorpusResources.Book book = b02();
    AttestationBookVerification verification = AttestationBookVerifier.verify(book.decode());
    AttestationReceiptPayload payload =
        new AttestationReceiptPayload(
            BOOK_ID,
            verification.headOrder(),
            verification.head(),
            Instant.parse("2027-01-01T00:00:00.000Z"));
    return new AttestationCorpusResources.Receipt(
        "B-11", book, envelope(payload, List.of(A)).encoded());
  }

  static AttestationCorpusResources.StandaloneEnvelope b08() {
    byte[] source =
        documentBytes(AttestationDocumentVectors.ARTIFACT_DOCUMENT, "V-MANIFEST-02", "envelope");
    AttestationDecodedEnvelope<AttestationBackupManifestPayload> decoded =
        AttestationDecodedEnvelope.manifest(source);
    return new AttestationCorpusResources.StandaloneEnvelope(
        "B-08",
        source,
        AttestationRegistry.fromVerifierFacts(
            List.of(
                binding(A, 0, "operator", null),
                binding(B, 0, "operator", null),
                binding(C, 0, "operator", null)),
            List.of(),
            List.of(
                grant(A, AttestationCapability.BACKUP, 0),
                grant(B, AttestationCapability.BACKUP, 0)),
            List.of(new AttestationPolicyRule(BigInteger.ZERO, AttestationCapability.BACKUP, 2)),
            List.of()),
        AttestationAuthorizationContext.manifest(decoded.payload()),
        decoded.authorizationEnvelope());
  }

  static AttestationCorpusResources.StandaloneEnvelope b09() {
    byte[] source =
        documentBytes(AttestationDocumentVectors.ARTIFACT_DOCUMENT, "V-RECEIPT-02", "envelope");
    AttestationDecodedEnvelope<AttestationReceiptPayload> decoded =
        AttestationDecodedEnvelope.receipt(source);
    return new AttestationCorpusResources.StandaloneEnvelope(
        "B-09",
        source,
        AttestationRegistry.fromVerifierFacts(
            List.of(
                binding(A, 0, "operator", null),
                binding(B, 0, "operator", null),
                binding(C, 0, "operator", null)),
            List.of(),
            List.of(
                grant(A, AttestationCapability.ANCHOR, 0),
                grant(B, AttestationCapability.ANCHOR, 0)),
            List.of(new AttestationPolicyRule(BigInteger.ZERO, AttestationCapability.ANCHOR, 2)),
            List.of()),
        AttestationAuthorizationContext.receipt(decoded.payload()),
        decoded.authorizationEnvelope());
  }

  static AttestationCorpusResources.Book decodeBook(byte[] source) {
    return AttestationCorpusResources.Book.decode(source);
  }

  private static AttestationCorpusResources.Book restoredBook(String id) {
    List<AttestationBookOperation> operations = new ArrayList<>(b02().operations());
    AttestationCorpusResources.Artifact artifact = b05Artifact();
    AttestationHash sourceHead = operations.getLast().envelope().head();
    AttestationHash artifactDigest = AttestationHash.sha256(artifact.encoded());
    AttestationPreimage request =
        preimage(
            command(
                AttestationOperationKind.RESTORE_BOOK,
                AttestationSourceChannel.CLI,
                "fixture-" + id.toLowerCase(Locale.ROOT) + "-4"),
            restoreRequest(BACKUP_ID, artifactDigest, BigInteger.valueOf(3), sourceHead));
    AttestationPreimage effect =
        preimage(restoreEffect(BACKUP_ID, artifactDigest, BigInteger.valueOf(3)));
    operations.add(
        operation(
            operations, AttestationOperationKind.RESTORE_BOOK, request, effect, List.of(A, B)));
    return book(id, operations);
  }

  static AttestationBookOperation genesis(
      List<Signer> founders, Map<AttestationCapability, Integer> policy) {
    AttestationPreimage request = genesisRequest(founders, policy);
    AttestationPreimage effect = genesisEffect(founders, policy);
    AttestationOperationPayload payload =
        new AttestationOperationPayload(
            BOOK_ID,
            BigInteger.ZERO,
            AttestationOperationKind.BOOK_GENESIS.wireToken(),
            AttestationHash.of(new byte[AttestationHash.BYTE_LENGTH]),
            GENESIS_RECORDED_AT,
            AttestationHash.sha256(request.encoded()),
            AttestationHash.sha256(effect.encoded()));
    return decodedOperation(payload, request, effect, founders);
  }

  private static AttestationBookOperation declareAccount(
      List<AttestationBookOperation> operations,
      String accountCode,
      String accountName,
      String accountType,
      List<Signer> signers) {
    AttestationPreimage request =
        preimage(
            command(
                AttestationOperationKind.DECLARE_ACCOUNT,
                AttestationSourceChannel.CLI,
                "fixture-account-" + accountCode),
            new AttestationPreimage.Fact(
                0x0110,
                List.of(
                    text(accountCode),
                    text(accountName),
                    token(accountType),
                    token("leaf"),
                    absent(),
                    absent())));
    AttestationPreimage effect =
        preimage(
            new AttestationPreimage.Fact(
                0x0010,
                List.of(
                    mutation(),
                    text(accountCode),
                    text(accountName),
                    token(accountType),
                    token("leaf"),
                    absent(),
                    absent(),
                    bool(true))));
    return operation(
        operations, AttestationOperationKind.DECLARE_ACCOUNT, request, effect, signers);
  }

  static AttestationBookOperation sale(
      List<AttestationBookOperation> operations,
      String idempotencyKey,
      String sourceDocumentId,
      LocalDate effectiveDate,
      UUID postingId,
      UUID commandId,
      List<Signer> signers) {
    AttestationPreimage request =
        preimage(
            command(
                AttestationOperationKind.RECORD_SALE_SETTLED,
                AttestationSourceChannel.CLI,
                idempotencyKey),
            new AttestationPreimage.Fact(
                0x0120,
                List.of(
                    u32(0),
                    token("record-sale-settled"),
                    date(effectiveDate),
                    token("standard"),
                    absent(),
                    absent())),
            new AttestationPreimage.Fact(
                0x0121, List.of(u32(0), token("cash-account"), text("1000"))),
            new AttestationPreimage.Fact(
                0x0121, List.of(u32(0), token("revenue-account"), text("4000"))),
            new AttestationPreimage.Fact(
                0x0122, List.of(u32(0), token("gross-amount"), money(10_000))),
            new AttestationPreimage.Fact(
                0x0124,
                List.of(
                    u32(0), text(sourceDocumentId), text("cash-receipt"), date(effectiveDate))));
    Instant recordedAt = recordedAt(operations.size());
    AttestationPreimage effect =
        preimage(
            postingFact(
                postingId,
                commandId,
                AttestationOperationKind.RECORD_SALE_SETTLED,
                effectiveDate,
                recordedAt,
                idempotencyKey,
                AttestationSourceChannel.CLI),
            new AttestationPreimage.Fact(
                0x0021,
                List.of(
                    mutation(),
                    uuid(postingId),
                    text(sourceDocumentId),
                    text("cash-receipt"),
                    date(effectiveDate))),
            journalLine(postingId, 0, "1000", "debit"),
            journalLine(postingId, 1, "4000", "credit"));
    return operation(
        operations, AttestationOperationKind.RECORD_SALE_SETTLED, request, effect, signers);
  }

  static AttestationBookOperation bindingOperation(
      List<AttestationBookOperation> operations,
      AttestationOperationKind kind,
      Signer signer,
      String action,
      String purpose,
      @Nullable AttestationHash predecessor,
      List<Signer> authorizers) {
    AttestationPreimage request =
        preimage(
            command(
                kind,
                AttestationSourceChannel.CLI,
                "fixture-" + kind.wireToken() + "-" + operations.size()),
            bindingRequest(signer, action, purpose, predecessor));
    AttestationPreimage effect = preimage(bindingEffect(signer, action, purpose, predecessor));
    return operation(operations, kind, request, effect, authorizers);
  }

  static AttestationBookOperation revoke(
      List<AttestationBookOperation> operations, Signer signer, List<Signer> authorizers) {
    AttestationPreimage request =
        preimage(
            command(
                AttestationOperationKind.REVOKE_KEY,
                AttestationSourceChannel.CLI,
                "fixture-revoke-" + operations.size()),
            revocationRequest(signer));
    AttestationPreimage effect = preimage(revocationEffect(signer));
    return operation(operations, AttestationOperationKind.REVOKE_KEY, request, effect, authorizers);
  }

  private static AttestationBookOperation alterPolicy(
      List<AttestationBookOperation> operations,
      List<RegistryChange> changes,
      List<Signer> signers) {
    List<AttestationPreimage.Fact> requestFacts = new ArrayList<>();
    requestFacts.add(
        command(
            AttestationOperationKind.ALTER_POLICY,
            AttestationSourceChannel.CLI,
            "fixture-alter-policy-" + operations.size()));
    List<AttestationPreimage.Fact> effectFacts = new ArrayList<>();
    for (RegistryChange change : changes) {
      requestFacts.add(change.request());
      effectFacts.add(change.effect());
    }
    return operation(
        operations,
        AttestationOperationKind.ALTER_POLICY,
        AttestationPreimage.of(requestFacts),
        AttestationPreimage.of(effectFacts),
        signers);
  }

  private static RegistryChange grantChange(Signer signer, AttestationCapability capability) {
    return new RegistryChange(grantRequest(signer, capability), grantEffect(signer, capability));
  }

  private static RegistryChange policyChange(AttestationCapability capability, int quorum) {
    return new RegistryChange(
        new AttestationPreimage.Fact(0x0182, List.of(token(capability.token()), u16(quorum))),
        policyEffect(capability, quorum));
  }

  private static RegistryChange workflowChange(
      UUID workflowId,
      String workflowKind,
      String resultHolding,
      @Nullable String capital,
      @Nullable String retained) {
    return new RegistryChange(
        new AttestationPreimage.Fact(
            0x0184,
            List.of(
                uuid(workflowId),
                token(workflowKind),
                text(resultHolding),
                optionalText(capital),
                optionalText(retained),
                bool(true))),
        new AttestationPreimage.Fact(
            0x0008,
            List.of(
                mutation(),
                uuid(workflowId),
                token(workflowKind),
                text(resultHolding),
                optionalText(capital),
                optionalText(retained),
                bool(true))));
  }

  private static AttestationCorpusResources.Book book(
      String id, List<AttestationBookOperation> operations) {
    return AttestationCorpusResources.book(id, operations);
  }

  private static Signer signer(String principalId, String seed, String spki) {
    try {
      KeyFactory factory = KeyFactory.getInstance("Ed25519");
      PrivateKey privateKey =
          factory.generatePrivate(
              new EdECPrivateKeySpec(
                  NamedParameterSpec.ED25519, AttestationDocumentVectors.hex(seed)));
      PublicKey publicKey =
          factory.generatePublic(new X509EncodedKeySpec(AttestationDocumentVectors.hex(spki)));
      return new Signer(
          UUID.fromString(principalId),
          new KeyPair(publicKey, privateKey),
          AttestationEd25519.keyId(publicKey));
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException(
          "The corpus Ed25519 fixture keys are unavailable.", exception);
    }
  }

  private static Signer signerWithGeneratedPublic(UUID principalId, String seed) {
    byte[] checkedSeed = AttestationDocumentVectors.hex(seed);
    java.security.SecureRandom random =
        new java.security.SecureRandom() {
          @Override
          public void nextBytes(byte[] bytes) {
            if (bytes.length != checkedSeed.length) {
              throw new IllegalStateException(
                  "The Ed25519 corpus generator requested an unexpected seed length.");
            }
            System.arraycopy(checkedSeed, 0, bytes, 0, bytes.length);
          }
        };
    try {
      java.security.KeyPairGenerator generator =
          java.security.KeyPairGenerator.getInstance("Ed25519");
      generator.initialize(NamedParameterSpec.ED25519, random);
      KeyPair pair = generator.generateKeyPair();
      return new Signer(principalId, pair, AttestationEd25519.keyId(pair.getPublic()));
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException(
          "The rollover corpus Ed25519 fixture key is unavailable.", exception);
    }
  }

  private static byte[] documentBytes(String document, String vector, String field) {
    try {
      return AttestationDocumentVectors.bytes(document, vector, field);
    } catch (java.io.IOException exception) {
      throw new IllegalStateException("The normative corpus vector is unavailable.", exception);
    }
  }

  private record RegistryChange(
      AttestationPreimage.Fact request, AttestationPreimage.Fact effect) {}
}
