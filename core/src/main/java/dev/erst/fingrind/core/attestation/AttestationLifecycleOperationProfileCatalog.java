package dev.erst.fingrind.core.attestation;

import java.util.Map;

/** Registers profiles for lifecycle, authorization, and reporting-period-close operations. */
final class AttestationLifecycleOperationProfileCatalog {
  private AttestationLifecycleOperationProfileCatalog() {}

  static void register(
      Map<AttestationOperationKind, AttestationOperationProfileCatalog.TagProfile> profiles) {
    AttestationOperationProfileCatalog.associate(
        profiles,
        AttestationOperationProfileCatalog.profile(
            AttestationOperationProfileCatalog.tags(0x0100, 0x0125),
            AttestationOperationProfileCatalog.tags(0x0100, 0x0125),
            AttestationOperationProfileCatalog.tags(0x0022),
            AttestationOperationProfileCatalog.tags(0x0022)),
        AttestationOperationKind.ATTACH_POSTING_APPROVAL);
    AttestationOperationProfileCatalog.associate(
        profiles, interimResultSweepProfile(), AttestationOperationKind.INTERIM_RESULT_SWEEP);
    AttestationOperationProfileCatalog.associate(
        profiles, fiscalYearCloseProfile(), AttestationOperationKind.FISCAL_YEAR_CLOSE);
    AttestationOperationProfileCatalog.associate(
        profiles,
        AttestationOperationProfileCatalog.profile(
            AttestationOperationProfileCatalog.tags(0x0100, 0x0150),
            AttestationOperationProfileCatalog.tags(0x0100, 0x0150),
            AttestationOperationProfileCatalog.tags(0x0006),
            AttestationOperationProfileCatalog.tags(0x0006)),
        AttestationOperationKind.BACKUP_CREATED);
    AttestationOperationProfileCatalog.associate(
        profiles,
        AttestationOperationProfileCatalog.profile(
            AttestationOperationProfileCatalog.tags(0x0100, 0x0160),
            AttestationOperationProfileCatalog.tags(0x0100, 0x0160),
            AttestationOperationProfileCatalog.tags(0x00A0),
            AttestationOperationProfileCatalog.tags(0x00A0)),
        AttestationOperationKind.RESTORE_BOOK);
    AttestationOperationProfileCatalog.associate(
        profiles,
        AttestationOperationProfileCatalog.profile(
            AttestationOperationProfileCatalog.tags(0x0100, 0x0170),
            AttestationOperationProfileCatalog.tags(0x0100, 0x0170),
            AttestationOperationProfileCatalog.tags(0x0007),
            AttestationOperationProfileCatalog.tags(0x0007)),
        AttestationOperationKind.REKEY_BOOK);
    AttestationOperationProfileCatalog.associate(
        profiles,
        AttestationOperationProfileCatalog.profile(
            AttestationOperationProfileCatalog.tags(0x0100, 0x0180),
            AttestationOperationProfileCatalog.tags(0x0100, 0x0180),
            AttestationOperationProfileCatalog.tags(0x0002),
            AttestationOperationProfileCatalog.tags(0x0002)),
        AttestationOperationKind.ENROLL_KEY);
    AttestationOperationProfileCatalog.associate(
        profiles,
        AttestationOperationProfileCatalog.profile(
            AttestationOperationProfileCatalog.tags(0x0100, 0x0180, 0x0185),
            AttestationOperationProfileCatalog.tags(0x0100, 0x0180, 0x0185),
            AttestationOperationProfileCatalog.tags(0x0002, 0x0009),
            AttestationOperationProfileCatalog.tags(0x0002, 0x0009)),
        AttestationOperationKind.ROLLOVER_KEY);
    AttestationOperationProfileCatalog.associate(
        profiles,
        AttestationOperationProfileCatalog.profile(
            AttestationOperationProfileCatalog.tags(0x0100, 0x0185),
            AttestationOperationProfileCatalog.tags(0x0100, 0x0185),
            AttestationOperationProfileCatalog.tags(0x0009),
            AttestationOperationProfileCatalog.tags(0x0009)),
        AttestationOperationKind.REVOKE_KEY);
    AttestationOperationProfileCatalog.associate(
        profiles,
        AttestationOperationProfileCatalog.profile(
            AttestationOperationProfileCatalog.tags(0x0100),
            AttestationOperationProfileCatalog.tags(0x0100, 0x0182, 0x0183, 0x0184),
            AttestationOperationProfileCatalog.tags(),
            AttestationOperationProfileCatalog.tags(0x0003, 0x0005, 0x0008),
            true),
        AttestationOperationKind.ALTER_POLICY);
  }

  static AttestationOperationProfileCatalog.TagProfile executePlanProfile() {
    return AttestationOperationProfileCatalog.profile(
        AttestationOperationProfileCatalog.tags(0x0100),
        AttestationOperationProfileCatalog.tags(
            0x0100, 0x0110, 0x0111, 0x0112, 0x0113, 0x0114, 0x0120, 0x0121, 0x0122, 0x0123, 0x0124,
            0x0126, 0x0127, 0x0128, 0x0129, 0x012A, 0x0130, 0x0131, 0x0132, 0x0133, 0x0134),
        AttestationOperationProfileCatalog.tags(),
        AttestationOperationProfileCatalog.tags(
            0x0006, 0x0007, 0x0008, 0x0010, 0x0011, 0x0012, 0x0013, 0x0014, 0x0020, 0x0021, 0x0022,
            0x0023, 0x0024, 0x0025, 0x0030, 0x0031, 0x0040, 0x0041, 0x0042, 0x0043, 0x0044, 0x0050,
            0x0051, 0x0060, 0x0061, 0x0062, 0x0070, 0x0071, 0x0072, 0x0080, 0x0081, 0x0082, 0x0090,
            0x0091, 0x0092, 0x0093),
        true);
  }

  private static AttestationOperationProfileCatalog.TagProfile interimResultSweepProfile() {
    return AttestationOperationProfileCatalog.profile(
        AttestationOperationProfileCatalog.tags(0x0100, 0x0120, 0x0140),
        AttestationOperationProfileCatalog.tags(0x0100, 0x0120, 0x0140, 0x0141),
        AttestationOperationProfileCatalog.tags(0x0040),
        AttestationOperationProfileCatalog.tags(0x0020, 0x0025, 0x0040, 0x0041, 0x0042));
  }

  private static AttestationOperationProfileCatalog.TagProfile fiscalYearCloseProfile() {
    return AttestationOperationProfileCatalog.profile(
        AttestationOperationProfileCatalog.tags(0x0100, 0x0120, 0x0140),
        AttestationOperationProfileCatalog.tags(0x0100, 0x0120, 0x0140, 0x0141),
        AttestationOperationProfileCatalog.tags(0x0020, 0x0025, 0x0043, 0x0044),
        AttestationOperationProfileCatalog.tags(0x0020, 0x0025, 0x0043, 0x0044));
  }
}
