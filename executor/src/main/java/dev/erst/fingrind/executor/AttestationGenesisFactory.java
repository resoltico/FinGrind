package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.AttestationFounderInput;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.attestation.AttestationSigningCredentialOpening;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Creates signed book genesis evidence while keeping encrypted-key access below the CLI layer. */
public final class AttestationGenesisFactory {
  private AttestationGenesisFactory() {}

  /**
   * Builds one self-authorizing genesis operation from explicit encrypted-key sources and retains
   * the fact of every founder key generated during that preparation.
   */
  public static AttestationGenesisPreparation prepare(
      BookIdentity bookIdentity, Instant recordedAt, List<AttestationFounderInput> founders) {
    return prepare(bookIdentity, recordedAt, founders, new FilesystemFounderCredentialAccess());
  }

  /**
   * Validates every founder input before a caller admits another protected-book artifact.
   *
   * <p>The check never creates a missing genesis key. Existing credentials are opened only long
   * enough to validate their declared principal and passphrase; missing credentials validate their
   * passphrase source and remain creation candidates for {@link #prepare}.
   */
  public static void validateFounderInputs(List<AttestationFounderInput> founders) {
    for (AttestationFounderInput founder :
        List.copyOf(Objects.requireNonNull(founders, "founders"))) {
      AttestationFounderCredentials.validateForOpening(founder);
    }
  }

  /**
   * Builds genesis through an injectable credential-custody seam for executor-level verification.
   */
  static AttestationGenesisPreparation prepare(
      BookIdentity bookIdentity,
      Instant recordedAt,
      List<AttestationFounderInput> founders,
      FounderCredentialAccess credentialAccess) {
    return new AttestationGenesisPreparer(bookIdentity, recordedAt, founders, credentialAccess)
        .prepare();
  }

  /** Opens declared founder credentials through the executor's custody boundary. */
  interface FounderCredentialAccess {
    /** Opens an already-existing declared founder credential. */
    AttestationSigningCredentialOpening openExisting(AttestationFounderInput founder);

    /** Opens an existing credential or creates its missing declared founder key. */
    AttestationSigningCredentialOpening openOrCreate(AttestationFounderInput founder);
  }

  /** Opens credentials with the production encrypted-PKCS#8 founder-key filesystem adapter. */
  private static final class FilesystemFounderCredentialAccess implements FounderCredentialAccess {
    @Override
    public AttestationSigningCredentialOpening openExisting(AttestationFounderInput founder) {
      return AttestationFounderCredentials.openExisting(founder);
    }

    @Override
    public AttestationSigningCredentialOpening openOrCreate(AttestationFounderInput founder) {
      return AttestationFounderCredentials.openOrCreate(founder);
    }
  }
}
