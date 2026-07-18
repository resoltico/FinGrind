package dev.erst.fingrind.contract.payroll;

/** Caller-attested payroll facts that determine whether the narrow 2026 profile is admissible. */
public record LatvianPayrollWithholdingProfile(boolean taxBookHeldAtEmployer, int dependantCount) {
  /** Returns the only withholding facts currently admitted by the owned 2026 calculation. */
  public static LatvianPayrollWithholdingProfile taxBookWithNoDependantsFor2026() {
    return new LatvianPayrollWithholdingProfile(true, 0);
  }

  /** Validates the finite, non-negative dependant fact. */
  public LatvianPayrollWithholdingProfile {
    if (dependantCount < 0) {
      throw new IllegalArgumentException("dependantCount must not be negative.");
    }
  }

  /** Rejects facts that need a statutory calculation outside the currently owned profile. */
  public void requireSupported2026Profile() {
    if (!taxBookHeldAtEmployer) {
      throw new IllegalArgumentException(
          "taxBookHeldAtEmployer must be true for the supported 2026 Latvian payroll profile.");
    }
    if (dependantCount != 0) {
      throw new IllegalArgumentException(
          "dependantCount must be 0 for the supported 2026 Latvian payroll profile.");
    }
  }
}
