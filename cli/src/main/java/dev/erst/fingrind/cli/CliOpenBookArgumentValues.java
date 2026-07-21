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
  final FounderArguments founders = new FounderArguments();
  boolean tightenParents;

  /** Keeps the three repeated founder inputs aligned during one command-tail parse. */
  static final class FounderArguments {
    final List<UUID> principalIds = new ArrayList<>();
    final List<Path> keyFiles = new ArrayList<>();
    final List<Path> passphraseFiles = new ArrayList<>();
  }
}
