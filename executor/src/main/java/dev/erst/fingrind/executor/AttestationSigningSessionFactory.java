package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.attestation.AttestationAdmissionRejectedException;
import dev.erst.fingrind.core.attestation.AttestationAuthorizationLimits;
import dev.erst.fingrind.core.attestation.AttestationCredentialSource;
import dev.erst.fingrind.core.attestation.AttestationCredentialUseException;
import dev.erst.fingrind.core.attestation.AttestationSigningSession;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Opens one custody-confined signing session for a single attested lifecycle operation. */
public final class AttestationSigningSessionFactory {
  private AttestationSigningSessionFactory() {}

  /** Opens the explicitly selected existing credentials, never creating or enrolling a key. */
  public static AttestationSigningSession open(List<AttestationCredentialSource> sources) {
    List<AttestationCredentialSource> checkedSources =
        List.copyOf(Objects.requireNonNull(sources, "sources"));
    Path activePath =
        checkedSources.isEmpty() ? null : checkedSources.getFirst().encryptedKeyFilePath();
    try {
      return AttestationSigningSession.open(checkedSources);
    } catch (AttestationAdmissionRejectedException exception) {
      throw exception;
    } catch (AttestationCredentialUseException exception) {
      throw new AttestationCredentialException(exception.credentialPath(), exception);
    } catch (IllegalArgumentException exception) {
      if (activePath == null) {
        throw new IllegalArgumentException(
            "Protected-book mutation requires "
                + AttestationAuthorizationLimits.MINIMUM_QUORUM
                + " through "
                + AttestationAuthorizationLimits.MAXIMUM_QUORUM
                + " attestation credentials.",
            exception);
      }
      throw new AttestationCredentialException(activePath, exception);
    }
  }
}
