package dev.erst.fingrind.sqlite.secret;

import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;
import java.util.Objects;

/** Windows ACL entries for one regular key file or parent directory, paired with the owner. */
record SqliteAclKeyFileSecurity(UserPrincipal owner, List<AclEntry> acl)
    implements SqliteKeyFileSecurity {
  SqliteAclKeyFileSecurity {
    Objects.requireNonNull(owner, "owner");
    acl = List.copyOf(acl);
  }
}
