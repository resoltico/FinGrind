package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.bookkeeping.RekeyRecoveryAction;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountingBasis;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.BusinessActivityTag;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EntityForm;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.OwnerModel;
import dev.erst.fingrind.core.ReportingObligationStatus;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.core.TaxRegistrationStatus;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import org.jspecify.annotations.Nullable;

/** Parses lifecycle-style mutation commands such as key generation, open-book, and rekey-book. */
final class CliLifecycleMutationArguments {
  private static final CliBookArgumentParser.CommandArgumentSpec OPEN_BOOK_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(
              ProtocolOptions.ENTITY_NAME,
              ProtocolOptions.ENTITY_FORM,
              ProtocolOptions.OWNER_MODEL,
              ProtocolOptions.REPORTING_OBLIGATION_STATUS,
              ProtocolOptions.TAX_REGISTRATION_STATUS,
              ProtocolOptions.BUSINESS_ACTIVITY_TAG,
              ProtocolOptions.FUNCTIONAL_CURRENCY,
              ProtocolOptions.FISCAL_YEAR_START,
              ProtocolOptions.ACCOUNTING_BASIS,
              ProtocolOptions.OUTPUT),
          List.of());
  private static final CliBookArgumentParser.CommandArgumentSpec CLOSE_PERIOD_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(
              ProtocolOptions.CLOSING_EQUITY_ACCOUNT,
              ProtocolOptions.EFFECTIVE_DATE_FROM,
              ProtocolOptions.EFFECTIVE_DATE_TO,
              ProtocolOptions.OUTPUT),
          List.of());
  private static final CliBookArgumentParser.CommandArgumentSpec BACKUP_BOOK_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(
              ProtocolOptions.BACKUP_FILE,
              ProtocolOptions.BACKUP_BOOK_KEY_FILE,
              ProtocolOptions.OUTPUT),
          List.of());

  private CliLifecycleMutationArguments() {}

  static CliCommand parseGenerateBookKeyFileCommand(List<String> arguments) {
    Path bookKeyFilePath = null;
    @Nullable OutputMode outputMode = null;
    ListIterator<String> argumentIterator = arguments.listIterator(1);
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      switch (argument) {
        case ProtocolOptions.BOOK_KEY_FILE -> {
          if (bookKeyFilePath != null) {
            throw CliArgumentValueParser.invalid(
                ProtocolOptions.BOOK_KEY_FILE,
                "Duplicate argument: " + ProtocolOptions.BOOK_KEY_FILE);
          }
          bookKeyFilePath =
              CliArgumentValueParser.requirePathOptionValue(
                  argumentIterator, ProtocolOptions.BOOK_KEY_FILE);
        }
        case ProtocolOptions.OUTPUT ->
            outputMode =
                CliArgumentValueParser.requireOutputMode(
                    outputMode,
                    CliArgumentValueParser.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
                    CliArgumentValueParser.supportedOutputModes(OutputMode.JSON, OutputMode.HUMAN));
        default ->
            throw CliArgumentValueParser.invalid(argument, "Unsupported argument: " + argument);
      }
    }
    if (bookKeyFilePath == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BOOK_KEY_FILE,
          "A " + ProtocolOptions.BOOK_KEY_FILE + " argument is required.");
    }
    return new GenerateBookKeyFile(
        bookKeyFilePath, CliArgumentValueParser.resolvedOutputMode(outputMode));
  }

  static CliCommand parseOpenBookCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(arguments, OPEN_BOOK_ARGUMENTS);
    OpenBookArgumentValues argumentValues =
        parseOpenBookArgumentValues(parsedArguments.commandArguments());
    TaxRegistrationStatus resolvedTaxRegistrationStatus = argumentValues.resolvedTaxStatus();
    return new OpenBook(
        parsedArguments.bookAccess(),
        new OpenBookCommand(
            new BookIdentity(
                new EntityProfile(
                    requireOpenBookEntityName(argumentValues.entityName),
                    requireOpenBookEntityForm(argumentValues.entityForm),
                    argumentValues.resolvedOwnerModel(),
                    argumentValues.resolvedReportingObligationStatus(),
                    resolvedTaxRegistrationStatus,
                    argumentValues.businessActivityTags),
                requireOpenBookFunctionalCurrency(argumentValues.functionalCurrency),
                requireOpenBookFiscalYearStart(argumentValues.fiscalYearStart),
                requireOpenBookAccountingBasis(argumentValues.accountingBasis))),
        CliArgumentValueParser.resolvedOutputMode(argumentValues.outputMode));
  }

  private static OpenBookArgumentValues parseOpenBookArgumentValues(List<String> commandArguments) {
    OpenBookArgumentValues argumentValues = new OpenBookArgumentValues();
    ListIterator<String> argumentIterator = commandArguments.listIterator();
    while (argumentIterator.hasNext()) {
      applyOpenBookArgument(argumentValues, argumentIterator.next(), argumentIterator);
    }
    return argumentValues;
  }

  static void applyOpenBookArgument(
      OpenBookArgumentValues argumentValues,
      String argument,
      ListIterator<String> argumentIterator) {
    switch (argument) {
      case ProtocolOptions.ENTITY_NAME ->
          argumentValues.entityName =
              CliArgumentValueParser.parseBookEntityNameOption(
                  CliArgumentValueParser.requireValue(
                      argumentIterator, ProtocolOptions.ENTITY_NAME),
                  ProtocolOptions.ENTITY_NAME);
      case ProtocolOptions.ENTITY_FORM ->
          argumentValues.entityForm =
              CliArgumentValueParser.parseEntityFormOption(
                  CliArgumentValueParser.requireValue(
                      argumentIterator, ProtocolOptions.ENTITY_FORM),
                  ProtocolOptions.ENTITY_FORM);
      case ProtocolOptions.OWNER_MODEL ->
          argumentValues.ownerModel =
              CliArgumentValueParser.parseOwnerModelOption(
                  CliArgumentValueParser.requireValue(
                      argumentIterator, ProtocolOptions.OWNER_MODEL),
                  ProtocolOptions.OWNER_MODEL);
      case ProtocolOptions.REPORTING_OBLIGATION_STATUS ->
          argumentValues.reportingObligationStatus =
              CliArgumentValueParser.parseReportingObligationStatusOption(
                  CliArgumentValueParser.requireValue(
                      argumentIterator, ProtocolOptions.REPORTING_OBLIGATION_STATUS),
                  ProtocolOptions.REPORTING_OBLIGATION_STATUS);
      case ProtocolOptions.TAX_REGISTRATION_STATUS ->
          argumentValues.taxRegistrationStatus =
              CliArgumentValueParser.parseTaxRegistrationStatusOption(
                  CliArgumentValueParser.requireValue(
                      argumentIterator, ProtocolOptions.TAX_REGISTRATION_STATUS),
                  ProtocolOptions.TAX_REGISTRATION_STATUS);
      case ProtocolOptions.BUSINESS_ACTIVITY_TAG ->
          argumentValues.businessActivityTags.add(
              CliArgumentValueParser.parseBusinessActivityTagOption(
                  CliArgumentValueParser.requireValue(
                      argumentIterator, ProtocolOptions.BUSINESS_ACTIVITY_TAG),
                  ProtocolOptions.BUSINESS_ACTIVITY_TAG));
      case ProtocolOptions.FUNCTIONAL_CURRENCY ->
          argumentValues.functionalCurrency =
              CliArgumentValueParser.parseCurrencyUnitOption(
                  CliArgumentValueParser.requireValue(
                      argumentIterator, ProtocolOptions.FUNCTIONAL_CURRENCY),
                  ProtocolOptions.FUNCTIONAL_CURRENCY);
      case ProtocolOptions.FISCAL_YEAR_START ->
          argumentValues.fiscalYearStart =
              CliArgumentValueParser.parseFiscalYearStartOption(
                  CliArgumentValueParser.requireValue(
                      argumentIterator, ProtocolOptions.FISCAL_YEAR_START),
                  ProtocolOptions.FISCAL_YEAR_START);
      case ProtocolOptions.ACCOUNTING_BASIS ->
          argumentValues.accountingBasis =
              CliArgumentValueParser.parseAccountingBasisOption(
                  CliArgumentValueParser.requireValue(
                      argumentIterator, ProtocolOptions.ACCOUNTING_BASIS),
                  ProtocolOptions.ACCOUNTING_BASIS);
      case ProtocolOptions.OUTPUT ->
          argumentValues.outputMode =
              CliArgumentValueParser.requireOutputMode(
                  argumentValues.outputMode,
                  CliArgumentValueParser.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
                  CliArgumentValueParser.supportedOutputModes(OutputMode.JSON, OutputMode.HUMAN));
      default ->
          throw CliArgumentValueParser.invalid(argument, "Unsupported argument: " + argument);
    }
  }

  private static BookEntityName requireOpenBookEntityName(@Nullable BookEntityName entityName) {
    if (entityName == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.ENTITY_NAME,
          "A " + ProtocolOptions.ENTITY_NAME + " argument is required.");
    }
    return entityName;
  }

  private static EntityForm requireOpenBookEntityForm(@Nullable EntityForm entityForm) {
    if (entityForm == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.ENTITY_FORM,
          "A " + ProtocolOptions.ENTITY_FORM + " argument is required.");
    }
    return entityForm;
  }

  private static CurrencyUnit requireOpenBookFunctionalCurrency(
      @Nullable CurrencyUnit functionalCurrency) {
    if (functionalCurrency == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.FUNCTIONAL_CURRENCY,
          "A " + ProtocolOptions.FUNCTIONAL_CURRENCY + " argument is required.");
    }
    return functionalCurrency;
  }

  private static FiscalYearStart requireOpenBookFiscalYearStart(
      @Nullable FiscalYearStart fiscalYearStart) {
    if (fiscalYearStart == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.FISCAL_YEAR_START,
          "A " + ProtocolOptions.FISCAL_YEAR_START + " argument is required.");
    }
    return fiscalYearStart;
  }

  private static AccountingBasis requireOpenBookAccountingBasis(
      @Nullable AccountingBasis accountingBasis) {
    if (accountingBasis == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.ACCOUNTING_BASIS,
          "A " + ProtocolOptions.ACCOUNTING_BASIS + " argument is required.");
    }
    return accountingBasis;
  }

  /** Accumulates one parsed open-book argument set before required-field resolution runs. */
  static final class OpenBookArgumentValues {
    private final List<BusinessActivityTag> businessActivityTags = new ArrayList<>();
    private @Nullable BookEntityName entityName;
    private @Nullable EntityForm entityForm;
    private @Nullable OwnerModel ownerModel;
    private @Nullable ReportingObligationStatus reportingObligationStatus;
    private @Nullable TaxRegistrationStatus taxRegistrationStatus;
    private @Nullable CurrencyUnit functionalCurrency;
    private @Nullable FiscalYearStart fiscalYearStart;
    private @Nullable AccountingBasis accountingBasis;
    private @Nullable OutputMode outputMode;

    private OwnerModel resolvedOwnerModel() {
      return ownerModel == null ? OwnerModel.UNKNOWN : ownerModel;
    }

    private ReportingObligationStatus resolvedReportingObligationStatus() {
      return reportingObligationStatus == null
          ? ReportingObligationStatus.UNSPECIFIED
          : reportingObligationStatus;
    }

    private TaxRegistrationStatus resolvedTaxStatus() {
      return taxRegistrationStatus == null
          ? TaxRegistrationStatus.UNSPECIFIED
          : taxRegistrationStatus;
    }
  }

  static CliCommand parseRekeyBookCommand(List<String> arguments) {
    Path bookFilePath = null;
    Path currentBookKeyFilePath = null;
    Path replacementBookKeyFilePath = null;
    CliBookPassphraseParser.PassphraseSourceKind currentPassphraseSourceKind = null;
    CliBookPassphraseParser.PassphraseSourceKind replacementPassphraseSourceKind = null;
    @Nullable OutputMode outputMode = null;
    ListIterator<String> argumentIterator = arguments.listIterator(1);
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      switch (argument) {
        case ProtocolOptions.BOOK_FILE -> {
          if (bookFilePath != null) {
            throw CliArgumentValueParser.invalid(
                ProtocolOptions.BOOK_FILE, "Duplicate argument: " + ProtocolOptions.BOOK_FILE);
          }
          bookFilePath =
              CliArgumentValueParser.requirePathOptionValue(
                  argumentIterator, ProtocolOptions.BOOK_FILE);
        }
        case ProtocolOptions.BOOK_KEY_FILE -> {
          currentPassphraseSourceKind =
              CliBookPassphraseParser.requireSinglePassphraseSource(
                  currentPassphraseSourceKind,
                  CliBookPassphraseParser.PassphraseSourceKind.KEY_FILE);
          currentBookKeyFilePath =
              CliArgumentValueParser.requirePathOptionValue(
                  argumentIterator, ProtocolOptions.BOOK_KEY_FILE);
        }
        case ProtocolOptions.BOOK_PASSPHRASE_STDIN -> {
          currentPassphraseSourceKind =
              CliBookPassphraseParser.requireSinglePassphraseSource(
                  currentPassphraseSourceKind,
                  CliBookPassphraseParser.PassphraseSourceKind.STANDARD_INPUT);
        }
        case ProtocolOptions.BOOK_PASSPHRASE_PROMPT -> {
          currentPassphraseSourceKind =
              CliBookPassphraseParser.requireSinglePassphraseSource(
                  currentPassphraseSourceKind,
                  CliBookPassphraseParser.PassphraseSourceKind.INTERACTIVE_PROMPT);
        }
        case ProtocolOptions.REPLACEMENT_BOOK_KEY_FILE -> {
          replacementPassphraseSourceKind =
              CliBookPassphraseParser.requireSingleReplacementPassphraseSource(
                  replacementPassphraseSourceKind,
                  CliBookPassphraseParser.PassphraseSourceKind.KEY_FILE);
          replacementBookKeyFilePath =
              CliArgumentValueParser.requirePathOptionValue(
                  argumentIterator, ProtocolOptions.REPLACEMENT_BOOK_KEY_FILE);
        }
        case ProtocolOptions.REPLACEMENT_BOOK_PASSPHRASE_STDIN -> {
          replacementPassphraseSourceKind =
              CliBookPassphraseParser.requireSingleReplacementPassphraseSource(
                  replacementPassphraseSourceKind,
                  CliBookPassphraseParser.PassphraseSourceKind.STANDARD_INPUT);
        }
        case ProtocolOptions.REPLACEMENT_BOOK_PASSPHRASE_PROMPT -> {
          replacementPassphraseSourceKind =
              CliBookPassphraseParser.requireSingleReplacementPassphraseSource(
                  replacementPassphraseSourceKind,
                  CliBookPassphraseParser.PassphraseSourceKind.INTERACTIVE_PROMPT);
        }
        case ProtocolOptions.OUTPUT ->
            outputMode =
                CliArgumentValueParser.requireOutputMode(
                    outputMode,
                    CliArgumentValueParser.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
                    CliArgumentValueParser.supportedOutputModes(OutputMode.JSON, OutputMode.HUMAN));
        default ->
            throw CliArgumentValueParser.invalid(argument, "Unsupported argument: " + argument);
      }
    }
    if (bookFilePath == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BOOK_FILE, "A " + ProtocolOptions.BOOK_FILE + " argument is required.");
    }
    if (currentPassphraseSourceKind == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BOOK_KEY_FILE,
          "Exactly one current book passphrase source is required: "
              + ProtocolOptions.BOOK_KEY_FILE
              + " <path>, "
              + ProtocolOptions.BOOK_PASSPHRASE_STDIN
              + ", or "
              + ProtocolOptions.BOOK_PASSPHRASE_PROMPT
              + ".");
    }
    if (replacementPassphraseSourceKind == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.REPLACEMENT_BOOK_KEY_FILE,
          "Exactly one replacement book passphrase source is required: "
              + ProtocolOptions.REPLACEMENT_BOOK_KEY_FILE
              + " <existing-path>, "
              + ProtocolOptions.REPLACEMENT_BOOK_PASSPHRASE_STDIN
              + ", or "
              + ProtocolOptions.REPLACEMENT_BOOK_PASSPHRASE_PROMPT
              + ".");
    }
    BookAccess.PassphraseSource currentPassphraseSource =
        CliBookPassphraseParser.passphraseSource(
            currentPassphraseSourceKind, currentBookKeyFilePath);
    BookAccess.PassphraseSource replacementPassphraseSource =
        CliBookPassphraseParser.passphraseSource(
            replacementPassphraseSourceKind, replacementBookKeyFilePath);
    CliBookPathValidator.validateDistinctRekeyPaths(
        bookFilePath, currentPassphraseSource, replacementPassphraseSource);
    CliBookPathValidator.validateRekeyStandardInputUsage(
        currentPassphraseSource, replacementPassphraseSource);
    return new RekeyBook(
        new BookAccess(bookFilePath, currentPassphraseSource),
        replacementPassphraseSource,
        CliArgumentValueParser.resolvedOutputMode(outputMode));
  }

  static CliCommand parseBackupBookCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(arguments, BACKUP_BOOK_ARGUMENTS);
    Path backupFilePath = null;
    Path backupBookKeyFilePath = null;
    @Nullable OutputMode outputMode = null;
    ListIterator<String> argumentIterator = parsedArguments.commandArguments().listIterator();
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      if (ProtocolOptions.BACKUP_FILE.equals(argument)) {
        if (backupFilePath != null) {
          throw CliArgumentValueParser.invalid(
              ProtocolOptions.BACKUP_FILE, "Duplicate argument: " + ProtocolOptions.BACKUP_FILE);
        }
        backupFilePath =
            CliArgumentValueParser.requirePathOptionValue(
                argumentIterator, ProtocolOptions.BACKUP_FILE);
      } else if (ProtocolOptions.BACKUP_BOOK_KEY_FILE.equals(argument)) {
        if (backupBookKeyFilePath != null) {
          throw CliArgumentValueParser.invalid(
              ProtocolOptions.BACKUP_BOOK_KEY_FILE,
              "Duplicate argument: " + ProtocolOptions.BACKUP_BOOK_KEY_FILE);
        }
        backupBookKeyFilePath =
            CliArgumentValueParser.requirePathOptionValue(
                argumentIterator, ProtocolOptions.BACKUP_BOOK_KEY_FILE);
      } else {
        outputMode =
            CliArgumentValueParser.requireOutputMode(
                outputMode,
                CliArgumentValueParser.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
                CliArgumentValueParser.supportedOutputModes(OutputMode.JSON, OutputMode.HUMAN));
      }
    }
    if (backupFilePath == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BACKUP_FILE,
          "A " + ProtocolOptions.BACKUP_FILE + " argument is required.");
    }
    if (backupBookKeyFilePath == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BACKUP_BOOK_KEY_FILE,
          "A " + ProtocolOptions.BACKUP_BOOK_KEY_FILE + " argument is required.");
    }
    CliBookPathValidator.validateDistinctBackupPaths(
        parsedArguments.bookAccess().bookFilePath(),
        parsedArguments.bookAccess().passphraseSource(),
        backupFilePath,
        backupBookKeyFilePath);
    return new BackupBook(
        parsedArguments.bookAccess(),
        backupFilePath,
        backupBookKeyFilePath,
        CliArgumentValueParser.resolvedOutputMode(outputMode));
  }

  static CliCommand parseRestoreBookCommand(List<String> arguments) {
    Path bookFilePath = null;
    Path backupFilePath = null;
    Path backupBookKeyFilePath = null;
    @Nullable OutputMode outputMode = null;
    ListIterator<String> argumentIterator = arguments.listIterator(1);
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      switch (argument) {
        case ProtocolOptions.BOOK_FILE -> {
          if (bookFilePath != null) {
            throw CliArgumentValueParser.invalid(
                ProtocolOptions.BOOK_FILE, "Duplicate argument: " + ProtocolOptions.BOOK_FILE);
          }
          bookFilePath =
              CliArgumentValueParser.requirePathOptionValue(
                  argumentIterator, ProtocolOptions.BOOK_FILE);
        }
        case ProtocolOptions.BACKUP_FILE -> {
          if (backupFilePath != null) {
            throw CliArgumentValueParser.invalid(
                ProtocolOptions.BACKUP_FILE, "Duplicate argument: " + ProtocolOptions.BACKUP_FILE);
          }
          backupFilePath =
              CliArgumentValueParser.requirePathOptionValue(
                  argumentIterator, ProtocolOptions.BACKUP_FILE);
        }
        case ProtocolOptions.BACKUP_BOOK_KEY_FILE -> {
          if (backupBookKeyFilePath != null) {
            throw CliArgumentValueParser.invalid(
                ProtocolOptions.BACKUP_BOOK_KEY_FILE,
                "Duplicate argument: " + ProtocolOptions.BACKUP_BOOK_KEY_FILE);
          }
          backupBookKeyFilePath =
              CliArgumentValueParser.requirePathOptionValue(
                  argumentIterator, ProtocolOptions.BACKUP_BOOK_KEY_FILE);
        }
        case ProtocolOptions.OUTPUT ->
            outputMode =
                CliArgumentValueParser.requireOutputMode(
                    outputMode,
                    CliArgumentValueParser.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
                    CliArgumentValueParser.supportedOutputModes(OutputMode.JSON, OutputMode.HUMAN));
        default ->
            throw CliArgumentValueParser.invalid(argument, "Unsupported argument: " + argument);
      }
    }
    if (bookFilePath == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BOOK_FILE, "A " + ProtocolOptions.BOOK_FILE + " argument is required.");
    }
    if (backupFilePath == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BACKUP_FILE,
          "A " + ProtocolOptions.BACKUP_FILE + " argument is required.");
    }
    if (backupBookKeyFilePath == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BACKUP_BOOK_KEY_FILE,
          "A " + ProtocolOptions.BACKUP_BOOK_KEY_FILE + " argument is required.");
    }
    CliBookPathValidator.validateDistinctRestorePaths(
        bookFilePath, backupFilePath, backupBookKeyFilePath);
    return new RestoreBook(
        bookFilePath,
        backupFilePath,
        backupBookKeyFilePath,
        CliArgumentValueParser.resolvedOutputMode(outputMode));
  }

  static CliCommand parseRecoverRekeyCommand(List<String> arguments) {
    Path bookFilePath = null;
    Path rollbackArtifactPath = null;
    RekeyRecoveryAction action = RekeyRecoveryAction.INSPECT;
    @Nullable OutputMode outputMode = null;
    ListIterator<String> argumentIterator = arguments.listIterator(1);
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      switch (argument) {
        case ProtocolOptions.BOOK_FILE -> {
          if (bookFilePath != null) {
            throw CliArgumentValueParser.invalid(
                ProtocolOptions.BOOK_FILE, "Duplicate argument: " + ProtocolOptions.BOOK_FILE);
          }
          bookFilePath =
              CliArgumentValueParser.requirePathOptionValue(
                  argumentIterator, ProtocolOptions.BOOK_FILE);
        }
        case ProtocolOptions.RECOVERY_ACTION ->
            action =
                CliArgumentValueParser.requireValidArgument(
                    ProtocolOptions.RECOVERY_ACTION,
                    () ->
                        RekeyRecoveryAction.fromWireValue(
                            CliArgumentValueParser.requireValue(
                                argumentIterator, ProtocolOptions.RECOVERY_ACTION)));
        case ProtocolOptions.ROLLBACK_FILE -> {
          if (rollbackArtifactPath != null) {
            throw CliArgumentValueParser.invalid(
                ProtocolOptions.ROLLBACK_FILE,
                "Duplicate argument: " + ProtocolOptions.ROLLBACK_FILE);
          }
          rollbackArtifactPath =
              CliArgumentValueParser.requirePathOptionValue(
                  argumentIterator, ProtocolOptions.ROLLBACK_FILE);
        }
        case ProtocolOptions.OUTPUT ->
            outputMode =
                CliArgumentValueParser.requireOutputMode(
                    outputMode,
                    CliArgumentValueParser.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
                    CliArgumentValueParser.supportedOutputModes(OutputMode.JSON, OutputMode.HUMAN));
        default ->
            throw CliArgumentValueParser.invalid(argument, "Unsupported argument: " + argument);
      }
    }
    if (bookFilePath == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BOOK_FILE, "A " + ProtocolOptions.BOOK_FILE + " argument is required.");
    }
    return new RecoverRekey(
        bookFilePath,
        action,
        rollbackArtifactPath,
        CliArgumentValueParser.resolvedOutputMode(outputMode));
  }

  static CliCommand parseClosePeriodCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(arguments, CLOSE_PERIOD_ARGUMENTS);
    @Nullable LocalDate effectiveDateFrom = null;
    @Nullable LocalDate effectiveDateTo = null;
    String closingEquityAccountCodeValue = null;
    @Nullable OutputMode outputMode = null;
    ListIterator<String> argumentIterator = parsedArguments.commandArguments().listIterator();
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
      if (ProtocolOptions.CLOSING_EQUITY_ACCOUNT.equals(argument)) {
        if (closingEquityAccountCodeValue != null) {
          throw CliArgumentValueParser.invalid(
              ProtocolOptions.CLOSING_EQUITY_ACCOUNT,
              "Duplicate argument: " + ProtocolOptions.CLOSING_EQUITY_ACCOUNT);
        }
        closingEquityAccountCodeValue =
            CliArgumentValueParser.requireValue(
                argumentIterator, ProtocolOptions.CLOSING_EQUITY_ACCOUNT);
        continue;
      }
      if (ProtocolOptions.EFFECTIVE_DATE_FROM.equals(argument)) {
        effectiveDateFrom =
            CliReportArguments.requireDateOption(
                effectiveDateFrom, argumentIterator, ProtocolOptions.EFFECTIVE_DATE_FROM);
        continue;
      }
      if (ProtocolOptions.EFFECTIVE_DATE_TO.equals(argument)) {
        effectiveDateTo =
            CliReportArguments.requireDateOption(
                effectiveDateTo, argumentIterator, ProtocolOptions.EFFECTIVE_DATE_TO);
        continue;
      }
      outputMode =
          CliArgumentValueParser.requireOutputMode(
              outputMode,
              CliArgumentValueParser.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
              CliArgumentValueParser.supportedOutputModes(OutputMode.JSON, OutputMode.HUMAN));
    }
    if (effectiveDateFrom == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.EFFECTIVE_DATE_FROM,
          "A " + ProtocolOptions.EFFECTIVE_DATE_FROM + " argument is required.");
    }
    if (effectiveDateTo == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.EFFECTIVE_DATE_TO,
          "A " + ProtocolOptions.EFFECTIVE_DATE_TO + " argument is required.");
    }
    if (closingEquityAccountCodeValue == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.CLOSING_EQUITY_ACCOUNT,
          "A " + ProtocolOptions.CLOSING_EQUITY_ACCOUNT + " argument is required.");
    }
    LocalDate resolvedEffectiveDateFrom = effectiveDateFrom;
    LocalDate resolvedEffectiveDateTo = effectiveDateTo;
    String resolvedClosingEquityAccountCodeValue = closingEquityAccountCodeValue;
    CliArgumentValueParser.requireOrderedDateRange(
        resolvedEffectiveDateFrom,
        resolvedEffectiveDateTo,
        ProtocolOptions.EFFECTIVE_DATE_FROM,
        ProtocolOptions.EFFECTIVE_DATE_TO);
    ReportingPeriod reportingPeriod =
        new ReportingPeriod(resolvedEffectiveDateFrom, resolvedEffectiveDateTo);
    AccountCode closingEquityAccountCode =
        CliArgumentValueParser.requireValidArgument(
            ProtocolOptions.CLOSING_EQUITY_ACCOUNT,
            () -> new AccountCode(resolvedClosingEquityAccountCodeValue));
    return new ClosePeriod(
        parsedArguments.bookAccess(),
        reportingPeriod,
        closingEquityAccountCode,
        CliArgumentValueParser.resolvedOutputMode(outputMode));
  }
}
