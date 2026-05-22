package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.OpenBookCommand;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.core.AccountingPolicyProfile;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.BusinessActivityTag;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EntityForm;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.OwnerModel;
import dev.erst.fingrind.core.ReportingPeriod;
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
              ProtocolOptions.BUSINESS_ACTIVITY_TAG,
              ProtocolOptions.FUNCTIONAL_CURRENCY,
              ProtocolOptions.FISCAL_YEAR_START,
              ProtocolOptions.POLICY_PROFILE,
              ProtocolOptions.OUTPUT),
          List.of());
  private static final CliBookArgumentParser.CommandArgumentSpec CLOSE_PERIOD_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(
              ProtocolOptions.EFFECTIVE_DATE_FROM,
              ProtocolOptions.EFFECTIVE_DATE_TO,
              ProtocolOptions.OUTPUT),
          List.of());
  private static final CliBookArgumentParser.CommandArgumentSpec BACKUP_BOOK_ARGUMENTS =
      CliBookArgumentParser.commandArgumentSpec(
          List.of(
              ProtocolOptions.BACKUP_FILE_OUT,
              ProtocolOptions.BACKUP_BOOK_KEY_FILE_OUT,
              ProtocolOptions.OUTPUT),
          List.of());
  private static final String DELETE_REKEY_ROLLBACK_COMMAND =
      ProtocolCatalog.operationName(OperationId.DELETE_REKEY_ROLLBACK);
  private static final String RESTORE_REKEY_ROLLBACK_COMMAND =
      ProtocolCatalog.operationName(OperationId.RESTORE_REKEY_ROLLBACK);

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
    return new OpenBook(
        parsedArguments.bookAccess(),
        new OpenBookCommand(
            new BookIdentity(
                new EntityProfile(
                    requireOpenBookEntityName(argumentValues.entityName),
                    requireOpenBookEntityForm(argumentValues.entityForm),
                    requireOpenBookOwnerModel(argumentValues.ownerModel),
                    requireOpenBookBusinessActivityTags(argumentValues.businessActivityTags)),
                requireOpenBookFunctionalCurrency(argumentValues.functionalCurrency),
                requireOpenBookFiscalYearStart(argumentValues.fiscalYearStart),
                requireOpenBookPolicyProfile(argumentValues.policyProfile))),
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
      case ProtocolOptions.POLICY_PROFILE ->
          argumentValues.policyProfile =
              CliArgumentValueParser.parseAccountingPolicyProfileOption(
                  CliArgumentValueParser.requireValue(
                      argumentIterator, ProtocolOptions.POLICY_PROFILE),
                  ProtocolOptions.POLICY_PROFILE);
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

  private static AccountingPolicyProfile requireOpenBookPolicyProfile(
      @Nullable AccountingPolicyProfile policyProfile) {
    if (policyProfile == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.POLICY_PROFILE,
          "A " + ProtocolOptions.POLICY_PROFILE + " argument is required.");
    }
    return policyProfile;
  }

  private static OwnerModel requireOpenBookOwnerModel(@Nullable OwnerModel ownerModel) {
    if (ownerModel == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.OWNER_MODEL,
          "A " + ProtocolOptions.OWNER_MODEL + " argument is required.");
    }
    return ownerModel;
  }

  private static List<BusinessActivityTag> requireOpenBookBusinessActivityTags(
      List<BusinessActivityTag> businessActivityTags) {
    if (businessActivityTags.isEmpty()) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BUSINESS_ACTIVITY_TAG,
          "At least one " + ProtocolOptions.BUSINESS_ACTIVITY_TAG + " argument is required.");
    }
    return List.copyOf(businessActivityTags);
  }

  /** Accumulates one parsed open-book argument set before required-field resolution runs. */
  static final class OpenBookArgumentValues {
    private final List<BusinessActivityTag> businessActivityTags = new ArrayList<>();
    private @Nullable BookEntityName entityName;
    private @Nullable EntityForm entityForm;
    private @Nullable OwnerModel ownerModel;
    private @Nullable CurrencyUnit functionalCurrency;
    private @Nullable FiscalYearStart fiscalYearStart;
    private @Nullable AccountingPolicyProfile policyProfile;
    private @Nullable OutputMode outputMode;
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
      if (ProtocolOptions.BACKUP_FILE_OUT.equals(argument)) {
        if (backupFilePath != null) {
          throw CliArgumentValueParser.invalid(
              ProtocolOptions.BACKUP_FILE_OUT,
              "Duplicate argument: " + ProtocolOptions.BACKUP_FILE_OUT);
        }
        backupFilePath =
            CliArgumentValueParser.requirePathOptionValue(
                argumentIterator, ProtocolOptions.BACKUP_FILE_OUT);
      } else if (ProtocolOptions.BACKUP_BOOK_KEY_FILE_OUT.equals(argument)) {
        if (backupBookKeyFilePath != null) {
          throw CliArgumentValueParser.invalid(
              ProtocolOptions.BACKUP_BOOK_KEY_FILE_OUT,
              "Duplicate argument: " + ProtocolOptions.BACKUP_BOOK_KEY_FILE_OUT);
        }
        backupBookKeyFilePath =
            CliArgumentValueParser.requirePathOptionValue(
                argumentIterator, ProtocolOptions.BACKUP_BOOK_KEY_FILE_OUT);
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
          ProtocolOptions.BACKUP_FILE_OUT,
          "A " + ProtocolOptions.BACKUP_FILE_OUT + " argument is required.");
    }
    if (backupBookKeyFilePath == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BACKUP_BOOK_KEY_FILE_OUT,
          "A " + ProtocolOptions.BACKUP_BOOK_KEY_FILE_OUT + " argument is required.");
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

  static CliCommand parseInspectRekeyRollbackCommand(List<String> arguments) {
    ParsedRekeyRollbackArguments parsedArguments = parseRekeyRollbackArguments(arguments);
    rejectUnexpectedRollbackPath(parsedArguments.rollbackArtifactPath());
    rejectUnexpectedPassphraseSource(parsedArguments.passphraseSourceKind());
    return new InspectRekeyRollback(
        parsedArguments.bookFilePath(),
        CliArgumentValueParser.resolvedOutputMode(parsedArguments.outputMode()));
  }

  static CliCommand parseDeleteRekeyRollbackCommand(List<String> arguments) {
    ParsedRekeyRollbackArguments parsedArguments = parseRekeyRollbackArguments(arguments);
    if (parsedArguments.passphraseSourceKind() == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BOOK_KEY_FILE,
          "Delete rekey rollback requires exactly one book passphrase source: "
              + ProtocolOptions.BOOK_KEY_FILE
              + " <path>, "
              + ProtocolOptions.BOOK_PASSPHRASE_STDIN
              + ", or "
              + ProtocolOptions.BOOK_PASSPHRASE_PROMPT
              + ".");
    }
    BookAccess.PassphraseSource passphraseSource =
        CliBookPassphraseParser.passphraseSource(
            parsedArguments.passphraseSourceKind(), parsedArguments.bookKeyFilePath());
    CliBookPathValidator.validateDistinctPaths(
        parsedArguments.bookFilePath(), passphraseSource, null);
    CliBookPathValidator.validateStandardInputUsage(passphraseSource, null);
    return new DeleteRekeyRollback(
        new BookAccess(parsedArguments.bookFilePath(), passphraseSource),
        parsedArguments.rollbackArtifactPath(),
        CliArgumentValueParser.resolvedOutputMode(parsedArguments.outputMode()));
  }

  static CliCommand parseRestoreRekeyRollbackCommand(List<String> arguments) {
    ParsedRekeyRollbackArguments parsedArguments = parseRekeyRollbackArguments(arguments);
    if (parsedArguments.passphraseSourceKind() == null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BOOK_KEY_FILE,
          "Restore rekey rollback requires exactly one book passphrase source: "
              + ProtocolOptions.BOOK_KEY_FILE
              + " <path>, "
              + ProtocolOptions.BOOK_PASSPHRASE_STDIN
              + ", or "
              + ProtocolOptions.BOOK_PASSPHRASE_PROMPT
              + ".");
    }
    BookAccess.PassphraseSource expectedPassphraseSource =
        CliBookPassphraseParser.passphraseSource(
            parsedArguments.passphraseSourceKind(), parsedArguments.bookKeyFilePath());
    CliBookPathValidator.validateDistinctPaths(
        parsedArguments.bookFilePath(), expectedPassphraseSource, null);
    CliBookPathValidator.validateStandardInputUsage(expectedPassphraseSource, null);
    return new RestoreRekeyRollback(
        parsedArguments.bookFilePath(),
        parsedArguments.rollbackArtifactPath(),
        expectedPassphraseSource,
        CliArgumentValueParser.resolvedOutputMode(parsedArguments.outputMode()));
  }

  private static ParsedRekeyRollbackArguments parseRekeyRollbackArguments(List<String> arguments) {
    Path bookFilePath = null;
    Path rollbackArtifactPath = null;
    Path bookKeyFilePath = null;
    CliBookPassphraseParser.PassphraseSourceKind passphraseSourceKind = null;
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
          passphraseSourceKind =
              CliBookPassphraseParser.requireSinglePassphraseSource(
                  passphraseSourceKind, CliBookPassphraseParser.PassphraseSourceKind.KEY_FILE);
          bookKeyFilePath =
              CliArgumentValueParser.requirePathOptionValue(
                  argumentIterator, ProtocolOptions.BOOK_KEY_FILE);
        }
        case ProtocolOptions.BOOK_PASSPHRASE_STDIN ->
            passphraseSourceKind =
                CliBookPassphraseParser.requireSinglePassphraseSource(
                    passphraseSourceKind,
                    CliBookPassphraseParser.PassphraseSourceKind.STANDARD_INPUT);
        case ProtocolOptions.BOOK_PASSPHRASE_PROMPT ->
            passphraseSourceKind =
                CliBookPassphraseParser.requireSinglePassphraseSource(
                    passphraseSourceKind,
                    CliBookPassphraseParser.PassphraseSourceKind.INTERACTIVE_PROMPT);
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
    return new ParsedRekeyRollbackArguments(
        bookFilePath, rollbackArtifactPath, bookKeyFilePath, passphraseSourceKind, outputMode);
  }

  private static void rejectUnexpectedRollbackPath(@Nullable Path rollbackArtifactPath) {
    if (rollbackArtifactPath != null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.ROLLBACK_FILE,
          ProtocolOptions.ROLLBACK_FILE
              + " is accepted only when "
              + DELETE_REKEY_ROLLBACK_COMMAND
              + " or "
              + RESTORE_REKEY_ROLLBACK_COMMAND
              + " is selected.");
    }
  }

  private static void rejectUnexpectedPassphraseSource(
      CliBookPassphraseParser.@Nullable PassphraseSourceKind passphraseSourceKind) {
    if (passphraseSourceKind != null) {
      throw CliArgumentValueParser.invalid(
          ProtocolOptions.BOOK_KEY_FILE,
          "Book passphrase source arguments are accepted only when "
              + DELETE_REKEY_ROLLBACK_COMMAND
              + " or "
              + RESTORE_REKEY_ROLLBACK_COMMAND
              + " is selected.");
    }
  }

  private record ParsedRekeyRollbackArguments(
      Path bookFilePath,
      @Nullable Path rollbackArtifactPath,
      @Nullable Path bookKeyFilePath,
      CliBookPassphraseParser.@Nullable PassphraseSourceKind passphraseSourceKind,
      @Nullable OutputMode outputMode) {}

  static CliCommand parseClosePeriodCommand(List<String> arguments) {
    CliBookArgumentParser.ParsedBookArguments parsedArguments =
        CliBookArgumentParser.parseBookAndCommandArguments(arguments, CLOSE_PERIOD_ARGUMENTS);
    ParsedClosePeriodArguments parsedClosePeriodArguments =
        parseClosePeriodArguments(parsedArguments.commandArguments());
    return new ClosePeriod(
        parsedArguments.bookAccess(),
        parsedClosePeriodArguments.reportingPeriod(),
        CliArgumentValueParser.resolvedOutputMode(parsedClosePeriodArguments.outputMode()));
  }

  static ParsedClosePeriodArguments parseClosePeriodArguments(List<String> commandArguments) {
    @Nullable LocalDate effectiveDateFrom = null;
    @Nullable LocalDate effectiveDateTo = null;
    @Nullable OutputMode outputMode = null;
    ListIterator<String> argumentIterator = commandArguments.listIterator();
    while (argumentIterator.hasNext()) {
      String argument = argumentIterator.next();
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
      if (ProtocolOptions.OUTPUT.equals(argument)) {
        outputMode =
            CliArgumentValueParser.requireOutputMode(
                outputMode,
                CliArgumentValueParser.requireValue(argumentIterator, ProtocolOptions.OUTPUT),
                CliArgumentValueParser.supportedOutputModes(OutputMode.JSON, OutputMode.HUMAN));
        continue;
      }
      throw CliArgumentValueParser.invalid(argument, "Unsupported argument: " + argument);
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
    LocalDate resolvedEffectiveDateFrom = effectiveDateFrom;
    LocalDate resolvedEffectiveDateTo = effectiveDateTo;
    CliArgumentValueParser.requireOrderedDateRange(
        resolvedEffectiveDateFrom,
        resolvedEffectiveDateTo,
        ProtocolOptions.EFFECTIVE_DATE_FROM,
        ProtocolOptions.EFFECTIVE_DATE_TO);
    return new ParsedClosePeriodArguments(
        new ReportingPeriod(resolvedEffectiveDateFrom, resolvedEffectiveDateTo), outputMode);
  }

  record ParsedClosePeriodArguments(
      ReportingPeriod reportingPeriod, @Nullable OutputMode outputMode) {}
}
