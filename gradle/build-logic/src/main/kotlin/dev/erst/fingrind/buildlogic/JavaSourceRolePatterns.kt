package dev.erst.fingrind.buildlogic

internal val forbiddenGenericClassNamePattern =
    Regex("""^(?:[A-Z][A-Za-z0-9]*)?(?:Manager|Helper|Util|Common|Processor)\.java$""")

internal val translationHeavyClassNamePattern =
    Regex(
        """^(?:Cli)?[A-Z][A-Za-z0-9]*(?:Renderer|Formatter|Mapper|Parser|Translator|Assembler|Factory|Writer|Reader|Workflow|Store|Arguments|Executor|Loader)\.java$""",
    )

internal val catalogHeavyClassNamePattern =
    Regex(
        """^[A-Z][A-Za-z0-9]*(?:Sql|Schema|Templates|Template|Catalog|Manifest|Rejection|Contracts?|FieldSets|Fields|Facts|Keys|Options|Placeholders|Descriptors?|Response|Responses|RequestShapes|Inspection|Entry|Step|Fact|Report|Narrative|Calls|Codes|Queries)\.java$""",
    )

internal val aggregateModelClassNamePattern =
    Regex("""^[A-Z][A-Za-z0-9]*(?:JsonModels|Command|Commands|App)\.java$""")
