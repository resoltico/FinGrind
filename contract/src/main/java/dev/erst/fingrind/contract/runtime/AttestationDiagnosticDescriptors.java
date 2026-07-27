package dev.erst.fingrind.contract.runtime;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.core.WireValue;
import java.util.List;

/** Typed machine-contract descriptors for exact attestation rejection diagnostics. */
public final class AttestationDiagnosticDescriptors {
  private AttestationDiagnosticDescriptors() {}

  /** Returns the exact response descriptor record types owned by this namespace. */
  public static List<Class<? extends ResponseDescriptorType>> descriptorTypes() {
    return List.of(
        DiagnosticDescriptor.class,
        AdmissionDiagnosticsDescriptor.class,
        VerificationDiagnosticsDescriptor.class);
  }

  /** Contexts that can emit live attestation-admission diagnostics. */
  public enum AdmissionContext implements WireValue {
    ORDINARY_LIVE_ADMISSION("ordinary-live-admission"),
    REGISTRY_MUTATION("registry-mutation"),
    BACKUP_ACKNOWLEDGEMENT("backup-acknowledgement");

    private final String wireValue;

    AdmissionContext(String wireValue) {
      this.wireValue = ContractDescriptorValidation.requireText(wireValue, "wireValue");
    }

    /** Returns the stable machine context token. */
    @Override
    public String wireValue() {
      return wireValue;
    }

    @Override
    public String toString() {
      return wireValue;
    }
  }

  /** One exact code, message, and hint triplet emitted by an attestation diagnostic context. */
  public record DiagnosticDescriptor(String code, String message, String hint)
      implements ResponseDescriptorType {
    /** Validates one exact attestation diagnostic triplet. */
    public DiagnosticDescriptor {
      code = ContractDescriptorValidation.requireText(code, "code");
      message = ContractDescriptorValidation.requireText(message, "message");
      hint = ContractDescriptorValidation.requireText(hint, "hint");
    }
  }

  /** Exact live-admission diagnostics grouped by their public rendering context. */
  public record AdmissionDiagnosticsDescriptor(
      AdmissionContext context, List<DiagnosticDescriptor> diagnostics)
      implements ResponseDescriptorType {
    /** Validates one live-admission diagnostic context. */
    public AdmissionDiagnosticsDescriptor {
      context = ContractDescriptorValidation.requireValue(context, "context");
      diagnostics = ContractDescriptorValidation.copyList(diagnostics, "diagnostics");
    }
  }

  /** Exact historical verification diagnostics grouped by their emitting operation surface. */
  public record VerificationDiagnosticsDescriptor(
      OperationId surface, List<DiagnosticDescriptor> diagnostics)
      implements ResponseDescriptorType {
    /** Validates one historical verification diagnostic surface. */
    public VerificationDiagnosticsDescriptor {
      surface = ContractDescriptorValidation.requireValue(surface, "surface");
      diagnostics = ContractDescriptorValidation.copyList(diagnostics, "diagnostics");
    }
  }
}
