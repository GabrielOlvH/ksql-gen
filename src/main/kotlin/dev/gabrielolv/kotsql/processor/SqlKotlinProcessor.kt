package dev.gabrielolv.kotsql.processor

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import dev.gabrielolv.kotsql.parser.SQLParser
import dev.gabrielolv.kotsql.relationship.RelationshipDetector
import dev.gabrielolv.kotsql.schema.SchemaValidator
import dev.gabrielolv.kotsql.schema.SchemaVersion
import dev.gabrielolv.kotsql.schema.MigrationGenerator
import dev.gabrielolv.kotsql.util.PathManager
import dev.gabrielolv.kotsql.util.FileManager
import kotlinx.datetime.Clock
import java.io.File
import java.nio.file.Files

/**
 * KSP processor that reads SQL files and generates Kotlin data classes with validation, relationships,
 * and schema validation capabilities. Uses the enhanced FileManager system for organized output.
 */
class SqlKotlinProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>
) : SymbolProcessor {
    
    private val sqlParser = SQLParser()
    
    override fun process(resolver: Resolver): List<KSAnnotated> {
        logger.info("=== KotSQL Processor Starting ===")
        
        // Get configuration from processor options
        val sqlSchemaPath = options["sqlSchemaPath"] ?: "src/main/resources/schema.sql"
        val targetPackage = options["targetPackage"] ?: "generated"
        val enableValidation = options["enableValidation"]?.toBoolean() ?: true
        val enableRelationships = options["enableRelationships"]?.toBoolean() ?: true
        val enableSchemaValidation = options["enableSchemaValidation"]?.toBoolean() ?: true
        val previousSchemaPath = options["previousSchemaPath"]
        val generateMigrations = options["generateMigrations"]?.toBoolean() ?: false
        val migrationOutputPath = options["migrationOutputPath"] ?: "migrations"
        
        // Enhanced configuration options
        val organizationStrategy = options["organizationStrategy"]?.let { strategy ->
            try {
                PathManager.OrganizationStrategy.valueOf(strategy.uppercase())
            } catch (e: IllegalArgumentException) {
                logger.warn("Invalid organization strategy: $strategy, using FEATURE_BASED")
                PathManager.OrganizationStrategy.FEATURE_BASED
            }
        } ?: PathManager.OrganizationStrategy.FEATURE_BASED
        val generateIndexFiles = options["generateIndexFiles"]?.toBoolean() ?: true
        val includeTimestamps = options["includeTimestamps"]?.toBoolean() ?: true
        
        logger.info("Configuration loaded:")
        logger.info("  sqlSchemaPath: $sqlSchemaPath")
        logger.info("  targetPackage: $targetPackage")
        logger.info("  enableValidation: $enableValidation")
        logger.info("  enableRelationships: $enableRelationships")
        logger.info("  organizationStrategy: $organizationStrategy")
        logger.info("  Using KSP standard output location")
        
        // Create path configuration for KSP standard location
        val pathConfig = PathManager.PathConfig(
            outputDir = "build/generated/ksp/main/kotlin", // This is the standard KSP location
            sourceSet = "main",
            organizationStrategy = organizationStrategy,
            generateIndexFiles = generateIndexFiles,
            includeTimestamps = includeTimestamps,
            createDirectoryStructure = true
        )
        
        // Create file manager
        val fileManager = FileManager(codeGenerator, logger, pathConfig)
        
        // Ensure base output directories exist early to prevent silent failures
        try {
            val directorySetupSuccess = PathManager.ensureDirectoryStructure(
                config = pathConfig,
                basePackageName = targetPackage,
                logger = { message -> logger.info("Directory setup: $message") }
            )
            
            if (!directorySetupSuccess) {
                logger.error("Failed to set up directory structure. Check permissions and output directory configuration.")
                return emptyList()
            }
        } catch (e: Exception) {
            logger.error("Failed to create or validate output directories: ${e.message}")
            throw e
        }
        
        try {
            // Read and parse SQL files (handle multiple files)
            val sqlSchemaPaths = sqlSchemaPath.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val allTables = mutableListOf<dev.gabrielolv.kotsql.model.SQLTableInfo>()
            
            logger.info("Processing ${sqlSchemaPaths.size} SQL schema file(s):")
            
            // Get the project root directory for resolving relative paths
            val projectRoot = determineProjectRoot()
            
            logger.info("Project root directory: ${projectRoot.absolutePath}")
            logger.info("Current working directory: ${System.getProperty("user.dir")}")
            
            for (sqlPath in sqlSchemaPaths) {
                // Try multiple resolution strategies for file paths
                val sqlFile = when {
                    // Absolute path
                    File(sqlPath).isAbsolute -> File(sqlPath)
                    // Relative to project root
                    else -> File(projectRoot, sqlPath)
                }
                
                logger.info("  Checking SQL file: ${sqlFile.absolutePath}")
                
                if (!sqlFile.exists()) {
                    // Try alternative paths if the file is not found
                    val alternativePaths = listOf(
                        File(projectRoot, "src/main/resources/$sqlPath"),
                        File(projectRoot, "src/main/resources/db/$sqlPath"),
                        File(sqlPath) // Last resort: current working directory
                    )
                    
                    val foundFile = alternativePaths.find { it.exists() }
                    if (foundFile != null) {
                        logger.info("    Found at alternative path: ${foundFile.absolutePath}")
                        val tables = sqlParser.parseFile(foundFile)
                        allTables.addAll(tables)
                        logger.info("    Parsed ${tables.size} tables from ${foundFile.name}")
                    } else {
                        logger.warn("    SQL schema file not found: $sqlPath")
                        logger.warn("    Tried paths:")
                        logger.warn("      - ${sqlFile.absolutePath}")
                        alternativePaths.forEach { alt ->
                            logger.warn("      - ${alt.absolutePath}")
                        }
                        continue
                    }
                } else {
                    val tables = sqlParser.parseFile(sqlFile)
                    allTables.addAll(tables)
                    logger.info("    Parsed ${tables.size} tables from ${sqlFile.name}")
                }
            }
            
            if (allTables.isEmpty()) {
                logger.warn("No tables found in any SQL schema files: ${sqlSchemaPaths.joinToString(", ")}")
                return emptyList()
            }
            
            logger.info("Total tables found: ${allTables.size}")
            logger.info("Table names: ${allTables.map { it.tableName }}")
            
            // Use the first SQL file for metadata purposes (or create a synthetic one)
            val primarySqlFile = sqlSchemaPaths.firstOrNull()?.let { sqlPath ->
                when {
                    File(sqlPath).isAbsolute -> File(sqlPath)
                    else -> File(projectRoot, sqlPath)
                }.takeIf { it.exists() }
            } ?: File(projectRoot, "schema.sql") // Fallback
            
            logger.info("Validation enabled: $enableValidation")
            logger.info("Relationships enabled: $enableRelationships")
            logger.info("Schema validation enabled: $enableSchemaValidation")
            logger.info("Organization strategy: $organizationStrategy")
            
            val tables = allTables
            
            // Detect relationships if enabled
            val relationships = if (enableRelationships) {
                val detectedRelationships = RelationshipDetector.detectRelationships(tables)
                val withReverseRelationships = RelationshipDetector.generateReverseRelationships(detectedRelationships)
                
                logger.info("Detected ${withReverseRelationships.relationships.size} relationships:")
                withReverseRelationships.relationships.forEach { relationship ->
                    val cardinality = RelationshipDetector.getCardinalityDescription(relationship)
                    logger.info("  ${relationship.fromTable}.${relationship.fromColumn} -> ${relationship.toTable}.${relationship.toColumn} ($cardinality)")
                }
                
                withReverseRelationships
            } else {
                null
            }
            
            // Create current schema version
            val currentSchema = SchemaVersion(
                version = "current",
                timestamp = Clock.System.now(),
                tables = tables,
                relationships = relationships ?: dev.gabrielolv.kotsql.model.SchemaRelationships(emptyList())
            )
            
            // Perform schema validation if enabled and previous schema exists
            if (enableSchemaValidation && previousSchemaPath != null) {
                performSchemaValidation(
                    previousSchemaPath = previousSchemaPath,
                    currentSchema = currentSchema,
                    generateMigrations = generateMigrations,
                    migrationOutputPath = migrationOutputPath
                )
            }
            
            // Validate current schema constraints
            if (enableSchemaValidation) {
                val schemaIssues = SchemaValidator.validateSchemaConstraints(currentSchema)
                if (schemaIssues.isNotEmpty()) {
                    logger.info("Schema validation issues found:")
                    schemaIssues.forEach { issue ->
                        when (issue.severity) {
                            dev.gabrielolv.kotsql.schema.IssueSeverity.ERROR -> logger.error(issue.description)
                            dev.gabrielolv.kotsql.schema.IssueSeverity.WARNING -> logger.warn(issue.description)
                            dev.gabrielolv.kotsql.schema.IssueSeverity.INFO -> logger.info(issue.description)
                        }
                    }
                }
            }
            
            // Generate all files using the enhanced FileManager system
            val generationResult = fileManager.generateAllFiles(
                tables = tables,
                basePackageName = targetPackage,
                includeValidation = enableValidation,
                relationships = relationships,
                sourceFile = primarySqlFile
            )
            
            logger.info("File generation completed using EnhancedKotlinGenerator:")
            logger.info("  - Generated files: ${generationResult.generatedFiles.size}")
            logger.info("  - Index files: ${generationResult.indexFiles.size}")
            logger.info("  - Build config files: ${generationResult.buildConfigFiles.size}")
            logger.info("  - Organization strategy: ${pathConfig.organizationStrategy}")
            
            // Log file structure
            if (generationResult.generatedFiles.isNotEmpty()) {
                logger.info("Generated file structure:")
                val filesByPackage = generationResult.generatedFiles.groupBy { it.packageName }
                filesByPackage.forEach { (packageName, files) ->
                    logger.info("  Package: $packageName")
                    files.forEach { file ->
                        logger.info("    - ${file.fileName} (${file.fileCategory})")
                    }
                }
            }
            
        } catch (e: Exception) {
            logger.error("Error processing SQL schema: ${e.message}")
            e.printStackTrace()
        }
        
        // KSP expects us to return a list of symbols that need further processing
        // Since we're generating from SQL files, not from Kotlin annotations, we return empty list
        return emptyList()
    }
    
    /**
     * Determine the project root directory using multiple strategies
     */
    private fun determineProjectRoot(): File {
        // Strategy 1: Use current working directory
        val currentDir = File(System.getProperty("user.dir"))
        
        // Strategy 2: Look for common project indicators
        val projectIndicators = listOf(
            "build.gradle.kts",
            "build.gradle", 
            "pom.xml",
            "settings.gradle.kts",
            "settings.gradle",
            ".git"
        )
        
        // Check current directory first
        if (projectIndicators.any { File(currentDir, it).exists() }) {
            logger.info("Found project root (current dir): ${currentDir.absolutePath}")
            return currentDir
        }
        
        // Check parent directories
        var parentDir = currentDir.parentFile
        var searchDepth = 0
        val maxSearchDepth = 5
        
        while (parentDir != null && searchDepth < maxSearchDepth) {
            if (projectIndicators.any { File(parentDir, it).exists() }) {
                logger.info("Found project root (parent search): ${parentDir.absolutePath}")
                return parentDir
            }
            parentDir = parentDir.parentFile
            searchDepth++
        }
        
        // Strategy 3: Try to use gradle project directory if available via system properties
        System.getProperty("gradle.project.root")?.let { root ->
            val gradleRoot = File(root)
            if (gradleRoot.exists()) {
                logger.info("Found project root (gradle property): ${gradleRoot.absolutePath}")
                return gradleRoot
            }
        }
        
        // Fallback to current working directory with a warning
        logger.warn("Could not determine project root, using current working directory: ${currentDir.absolutePath}")
        return currentDir
    }
    
    /**
     * Perform schema validation against previous version
     */
    private fun performSchemaValidation(
        previousSchemaPath: String,
        currentSchema: SchemaVersion,
        generateMigrations: Boolean,
        migrationOutputPath: String
    ) {
        try {
            val previousSchemaFile = File(previousSchemaPath)
            if (!previousSchemaFile.exists()) {
                logger.warn("Previous schema file not found: $previousSchemaPath")
                return
            }
            
            logger.info("Comparing with previous schema: $previousSchemaPath")
            
            // Parse previous schema
            val previousTables = sqlParser.parseFile(previousSchemaFile)
            val previousRelationships = RelationshipDetector.detectRelationships(previousTables)
            val previousSchemaWithReverse = RelationshipDetector.generateReverseRelationships(previousRelationships)
            
            val previousSchema = SchemaVersion(
                version = "previous",
                timestamp = Clock.System.now(), // Use current time for comparison
                tables = previousTables,
                relationships = previousSchemaWithReverse
            )
            
            // Validate schema changes
            val validationResult = SchemaValidator.validateSchemaChange(previousSchema, currentSchema)
            
            logger.info("Schema validation completed:")
            logger.info(validationResult.getSummary())
            
            // Log individual changes
            validationResult.changes.forEach { change ->
                when (change.severity) {
                    dev.gabrielolv.kotsql.schema.ChangeSeverity.MAJOR -> logger.warn("MAJOR: ${change.description}")
                    dev.gabrielolv.kotsql.schema.ChangeSeverity.MINOR -> logger.info("MINOR: ${change.description}")
                    dev.gabrielolv.kotsql.schema.ChangeSeverity.PATCH -> logger.info("PATCH: ${change.description}")
                }
            }
            
            // Generate migrations if requested
            if (generateMigrations && validationResult.changes.isNotEmpty()) {
                val migration = MigrationGenerator.generateMigration(validationResult)
                
                // Ensure migration directory exists
                val migrationDir = File(migrationOutputPath)
                if (!migrationDir.exists()) {
                    migrationDir.mkdirs()
                }
                
                // Save migration files
                migration.saveToFiles(migrationOutputPath)
                
                logger.info("Generated migration: ${migration.name}")
                logger.info("Migration summary:")
                logger.info(migration.getSummary())
            }
            
        } catch (e: Exception) {
            logger.error("Error during schema validation: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Generate schema metadata file
     */
    private fun generateSchemaMetadata(schema: SchemaVersion, packageName: String) {
        try {
            val metadataContent = generateSchemaMetadataContent(schema, packageName)
            
            val file = codeGenerator.createNewFile(
                dependencies = Dependencies(false),
                packageName = packageName,
                fileName = "SchemaMetadata"
            )
            
            file.write(metadataContent.toByteArray())
            file.close()
            
            logger.info("Generated: SchemaMetadata.kt")
            
        } catch (e: Exception) {
            logger.error("Failed to generate schema metadata: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Generate schema metadata file content
     */
    private fun generateSchemaMetadataContent(schema: SchemaVersion, packageName: String): String {
        return buildString {
            appendLine("package $packageName")
            appendLine()
            appendLine("import dev.gabrielolv.kotsql.schema.SchemaVersion")
            appendLine("import dev.gabrielolv.kotsql.model.SchemaRelationships")
            appendLine("import kotlinx.datetime.Instant")
            appendLine()
            appendLine("/**")
            appendLine(" * Generated schema metadata")
            appendLine(" * Contains information about the current database schema")
            appendLine(" */")
            appendLine("object SchemaMetadata {")
            appendLine("    const val VERSION = \"${schema.version}\"")
            appendLine("    val TIMESTAMP = Instant.parse(\"${schema.timestamp}\")")
            appendLine("    const val TABLE_COUNT = ${schema.getTableCount()}")
            appendLine("    const val COLUMN_COUNT = ${schema.getTotalColumnCount()}")
            appendLine("    const val RELATIONSHIP_COUNT = ${schema.getRelationshipCount()}")
            appendLine()
            appendLine("    val TABLE_NAMES = setOf(")
            schema.getTableNames().forEach { tableName ->
                appendLine("        \"$tableName\",")
            }
            appendLine("    )")
            appendLine()
            appendLine("    /**")
            appendLine("     * Get the current schema version")
            appendLine("     */")
            appendLine("    fun getCurrentSchema(): SchemaVersion {")
            appendLine("        // Note: This would typically be loaded from a data source")
            appendLine("        // For now, returning basic metadata")
            appendLine("        return SchemaVersion(")
            appendLine("            version = VERSION,")
            appendLine("            timestamp = TIMESTAMP,")
            appendLine("            tables = emptyList(), // Would be populated from actual data")
            appendLine("            relationships = SchemaRelationships(emptyList())")
            appendLine("        )")
            appendLine("    }")
            appendLine("}")
        }
    }
}

/**
 * SymbolProcessorProvider that creates instances of SqlKotlinProcessor
 */
class SqlKotlinProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return SqlKotlinProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
            options = environment.options
        )
    }
} 