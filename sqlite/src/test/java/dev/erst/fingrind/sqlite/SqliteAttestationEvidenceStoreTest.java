package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.BookDoctrines;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationGenesis;
import dev.erst.fingrind.core.attestation.AttestationKeyFiles;
import dev.erst.fingrind.core.attestation.AttestationPublicCredential;
import dev.erst.fingrind.core.attestation.AttestationSigningCredential;
import dev.erst.fingrind.core.attestation.AttestationVerification;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Covers durable evidence append, stale-head admission, and immutable-SQL backstops. */
class SqliteAttestationEvidenceStoreTest extends SqlitePostingFactStoreTestSupport {
  private static final UUID BOOK_ID = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
  private static final UUID PRINCIPAL_ID =
      UUID.fromString("10213243-5465-7687-98a9-babcbddceeff");
  private static final byte[] ZERO_HEAD = new byte[32];

  @Test
  void appendsVerifiedGenesisRejectsAStaleHeadAndPreventsLaterEvidenceMutation() throws Exception {
    Path bookPath = tempDirectory.resolve("attested.sqlite");
    Path signerPath = tempDirectory.resolve("founder.fgatk");
    char[] passphrase = "sqlite attestation test passphrase".toCharArray();
    AttestationPublicCredential publicCredential = AttestationKeyFiles.create(signerPath, passphrase);
    try (AttestationSigningCredential signer =
        new AttestationSigningCredential(PRINCIPAL_ID, publicCredential, signerPath, passphrase)) {
      AttestationEvidence genesis =
          AttestationGenesis.create(
              BOOK_ID,
              attestationBookIdentity(),
              Instant.parse("2026-07-21T00:00:00Z"),
              List.of(signer));
      withStandaloneDatabase(
          bookAccess(bookPath),
          database -> {
            SqliteBookSchemaBootstrap.initializeBook(database);
            database.executeStatement("begin immediate");
            AttestationVerification verification =
                SqliteAttestationEvidenceStore.append(database, ZERO_HEAD, genesis);
            database.executeStatement("commit");

            assertEquals(0, verification.headOrder().intValueExact());
            assertEquals(1, countRows(database, "attestation_operation"));
            assertArrayEquals(
                genesis.operationEnvelope(),
                SqliteAttestationEvidenceStore.loadAll(database).getFirst().operationEnvelope());

            database.executeStatement("begin immediate");
            SqliteAttestationStaleHeadException staleHead =
                assertThrows(
                    SqliteAttestationStaleHeadException.class,
                    () -> SqliteAttestationEvidenceStore.append(database, ZERO_HEAD, genesis));
            database.executeStatement("rollback");
            assertArrayEquals(ZERO_HEAD, staleHead.observedHead());
            assertArrayEquals(verification.operationHead(), staleHead.currentHead());
            assertEquals(0, staleHead.currentOrder().intValueExact());

            SqliteNativeException updateFailure =
                assertThrows(
                    SqliteNativeException.class,
                    () ->
                        database.executeStatement(
                            "update attestation_operation set operation_head_hex = operation_head_hex"));
            assertEquals("SQLITE_CONSTRAINT_TRIGGER", updateFailure.resultName());
          });
    } finally {
      java.util.Arrays.fill(passphrase, '\0');
    }
  }

  private static BookIdentity attestationBookIdentity() {
    return new BookIdentity(
        new EntityProfile(new BookEntityName("Acme Studio")),
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE,
        CurrencyUnit.of("EUR"),
        FiscalYearStart.parse("01-01"),
        LocalDate.parse("2026-01-01"));
  }
}
