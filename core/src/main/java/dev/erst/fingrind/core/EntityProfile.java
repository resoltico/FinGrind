package dev.erst.fingrind.core;

import java.util.List;
import java.util.Objects;

/** Canonical identity profile for one accounting entity. */
public record EntityProfile(
    BookEntityName displayName,
    EntityForm entityForm,
    OwnerModel ownerModel,
    ReportingObligationStatus reportingObligationStatus,
    List<BusinessActivityTag> businessActivityTags) {
  /** Validates one entity profile. */
  public EntityProfile {
    Objects.requireNonNull(displayName, "displayName");
    Objects.requireNonNull(entityForm, "entityForm");
    Objects.requireNonNull(ownerModel, "ownerModel");
    Objects.requireNonNull(reportingObligationStatus, "reportingObligationStatus");
    businessActivityTags =
        List.copyOf(Objects.requireNonNull(businessActivityTags, "businessActivityTags"));
  }
}
