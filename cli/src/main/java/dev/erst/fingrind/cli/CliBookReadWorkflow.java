package dev.erst.fingrind.cli;

/** Complete read capability over one selected book, composed from focused capability families. */
interface CliBookReadWorkflow
    extends CliBookInspectionReadWorkflow,
        CliAttestationInspectionReadWorkflow,
        CliBookCatalogReadWorkflow,
        CliBookTaxReportReadWorkflow,
        CliBookCoreReportReadWorkflow,
        CliBookStatementReportReadWorkflow,
        CliBookOperationalReportReadWorkflow {}
