package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.core.AccountingBasis;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookTemplateId;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.InventoryCostingDoctrine;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Package-confined mutable state for one open-book command-tail parse. */
final class CliOpenBookArgumentValues {
  @Nullable BookEntityName entityName;
  @Nullable BookTemplateId bookTemplateId;
  @Nullable AccountingBasis accountingBasis;
  @Nullable InventoryCostingDoctrine inventoryCostingDoctrine;
  @Nullable CurrencyUnit functionalCurrency;
  @Nullable FiscalYearStart fiscalYearStart;
  @Nullable LocalDate bookStartEffectiveDate;
  @Nullable OutputMode outputMode;
  final List<UUID> founderPrincipalIds = new ArrayList<>();
  final List<Path> founderKeyFiles = new ArrayList<>();
  final List<Path> founderPassphraseFiles = new ArrayList<>();
  boolean tightenParents;
}
