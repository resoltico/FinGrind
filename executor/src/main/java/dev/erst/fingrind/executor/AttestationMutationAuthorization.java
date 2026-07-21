package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.attestation.AttestationCredentialSource;
import dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer;
import dev.erst.fingrind.core.attestation.AttestationSigningSession;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Opens file-backed mutation credentials below the CLI boundary and scopes their authorization
 * capability to one application action.
 */
public final class AttestationMutationAuthorization {
  private AttestationMutationAuthorization() {}

  /**
   * Runs one action with the narrow authorization capability and clears credential state afterward.
   */
  public static <T> T withAuthorizer(
      List<AttestationCredentialSource> credentialSources,
      Function<AttestationOperationAuthorizer, T> action) {
    List<AttestationCredentialSource> checkedSources =
        List.copyOf(Objects.requireNonNull(credentialSources, "credentialSources"));
    Function<AttestationOperationAuthorizer, T> checkedAction =
        Objects.requireNonNull(action, "action");
    if (checkedSources.isEmpty()) {
      throw new IllegalArgumentException(
          "Protected-book mutation requires at least one attestation authorization credential.");
    }
    AttestationCredentialSource activeSource = checkedSources.getFirst();
    try (AttestationSigningSession session = AttestationSigningSession.open(checkedSources)) {
      return checkedAction.apply(session);
    } catch (IOException | IllegalArgumentException exception) {
      throw new AttestationCredentialException(activeSource.encryptedKeyFilePath(), exception);
    }
  }
}
