package dev.gabrielolv.kotsql.util

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import dev.gabrielolv.kotsql.generator.KotlinGenerator
import dev.gabrielolv.kotsql.model.SQLTableInfo
import dev.gabrielolv.kotsql.model.SchemaRelationships
import java.io.File
import java.nio.file.Files
import kotlin.io.path.exists

/**
 * Manages file generation, organization, and output for the KSP processor.
 * Coordinates between PathManager, GenerationMetadata, and the actual file writing.
 */
class FileManager(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val config: PathManager.PathConfig
) {
    
    /**
     * Context for generating files for a specific table
     */
    data class TableGenerationContext(
        val table: SQLTableInfo,
        val packageName: String,
        val includeValidation: Boolean,
        val relationships: SchemaRelationships?
    )
    
    /**
     * Result of a generation operation
     */
    data class GenerationResult(
        val generatedFiles: List<PathManager.GeneratedFile>,
        val indexFiles: List<PathManager.GeneratedFile>,
        val buildConfigFiles: Map<String, String>,
        val metadata: GenerationMetadata.GenerationInfo
    )
    
    private val generatedFiles = mutableListOf<PathManager.GeneratedFile>()
    
    // Add the enhanced generator
    private val generator = KotlinGenerator()
    
    /**
     * Generate all files for a collection of tables
     */
    fun generateAllFiles(
        tables: List<SQLTableInfo>,
        basePackageName: String,
        includeValidation: Boolean,
        relationships: SchemaRelationships?,
        sourceFile: File
    ): GenerationResult {
        
        // Validate configuration
        validateConfiguration(basePackageName)
        
        // Check if regeneration is needed
        val baseOutputDir = PathManager.validateOutputDirectory(config.outputDir)
        if (!GenerationMetadata.shouldRegenerate(sourceFile, baseOutputDir)) {
            logger.info("Source file unchanged, skipping regeneration")
            val existingMetadata = GenerationMetadata.loadGenerationMetadata(baseOutputDir)
            if (existingMetadata != null) {
                return GenerationResult(
                    generatedFiles = emptyList(),
                    indexFiles = emptyList(),
                    buildConfigFiles = emptyMap(),
                    metadata = existingMetadata
                )
            }
        }
        
        logger.info("Generating files with ${config.organizationStrategy} organization strategy")
        
        generatedFiles.clear()
        
        // Generate files for each table
        tables.forEach { table ->
            val context = TableGenerationContext(
                table = table,
                packageName = basePackageName,
                includeValidation = includeValidation,
                relationships = relationships
            )
            
            generateTableFiles(context)
        }
        
        // Generate relationship files if needed
        if (relationships != null && relationships.relationships.isNotEmpty()) {
            generateRelationshipFiles(basePackageName, relationships)
        }
        
        // Generate schema metadata files
        generateSchemaMetadataFiles(basePackageName, tables, relationships)
        
        // Generate index files
        val indexFiles = generateIndexFiles()
        
        // Generate build configuration files
        val buildConfigFiles = GenerationMetadata.generateBuildConfigurationFiles(config)
        
        // Create generation metadata
        val sourceFileHash = GenerationMetadata.generateFileHash(sourceFile)
        val allGeneratedFileNames = (generatedFiles + indexFiles).map { it.fileName }
        val metadata = GenerationMetadata.createGenerationInfo(
            sourceFile = sourceFile.absolutePath,
            sourceFileHash = sourceFileHash,
            generatedFiles = allGeneratedFileNames
        )
        
        // Write all files using KSP CodeGenerator
        writeFiles()
        writeIndexFiles(indexFiles)
        writeBuildConfigFiles(buildConfigFiles)
        
        // Save generation metadata
        GenerationMetadata.saveGenerationMetadata(metadata, baseOutputDir)
        
        // Clean up old files
        GenerationMetadata.cleanupOldFiles(allGeneratedFileNames, baseOutputDir)
        
        logger.info("Generated ${generatedFiles.size} files, ${indexFiles.size} index files")
        
        return GenerationResult(
            generatedFiles = generatedFiles.toList(),
            indexFiles = indexFiles,
            buildConfigFiles = buildConfigFiles,
            metadata = metadata
        )
    }
    
    /**
     * Generate files for a single table
     */
    private fun generateTableFiles(context: TableGenerationContext) {
        val table = context.table
        val className = NamingConventions.tableNameToClassName(table.tableName)
        
        // Generate main entity class
        val (entityPackageName, _) = PathManager.getOutputDirectory(
            config = config,
            basePackageName = context.packageName,
            tableName = table.tableName,
            category = PathManager.FileCategory.ENTITY
        )
        val entityContext = context.copy(packageName = entityPackageName)
        val entityContent = generateEntityFileContent(entityContext)
        val entityFile = PathManager.createGeneratedFile(
            config = config,
            basePackageName = context.packageName,
            fileName = className,
            content = entityContent,
            tableName = table.tableName,
            category = PathManager.FileCategory.ENTITY
        )
        generatedFiles.add(entityFile)
        
        // Generate composite key class if needed
        if (table.hasCompositePrimaryKey) {
            val (keyPackageName, _) = PathManager.getOutputDirectory(
                config = config,
                basePackageName = context.packageName,
                tableName = table.tableName,
                category = PathManager.FileCategory.KEY
            )
            val keyContext = context.copy(packageName = keyPackageName)
            val keyContent = generateKeyFileContent(keyContext)
            val keyFile = PathManager.createGeneratedFile(
                config = config,
                basePackageName = context.packageName,
                fileName = "${className}Key",
                content = keyContent,
                tableName = table.tableName,
                category = PathManager.FileCategory.KEY
            )
            generatedFiles.add(keyFile)
        }
        
        // Generate table metadata
        val (metadataPackageName, _) = PathManager.getOutputDirectory(
            config = config,
            basePackageName = context.packageName,
            tableName = table.tableName,
            category = PathManager.FileCategory.TABLE_METADATA
        )
        val metadataContext = context.copy(packageName = metadataPackageName)
        val metadataContent = generateTableMetadataContent(metadataContext)
        val metadataFile = PathManager.createGeneratedFile(
            config = config,
            basePackageName = context.packageName,
            fileName = "${className}Table",
            content = metadataContent,
            tableName = table.tableName,
            category = PathManager.FileCategory.TABLE_METADATA
        )
        generatedFiles.add(metadataFile)
        
        // Generate query helpers based on organization strategy
        if (shouldGenerateQueryFiles(context)) {
            val (queryPackageName, _) = PathManager.getOutputDirectory(
                config = config,
                basePackageName = context.packageName,
                tableName = table.tableName,
                category = PathManager.FileCategory.QUERIES
            )
            val queryContext = context.copy(packageName = queryPackageName)
            val queryContent = generateQueryFileContent(queryContext)
            val queryFile = PathManager.createGeneratedFile(
                config = config,
                basePackageName = context.packageName,
                fileName = "${className}Queries",
                content = queryContent,
                tableName = table.tableName,
                category = PathManager.FileCategory.QUERIES
            )
            generatedFiles.add(queryFile)
        }
        
        // Generate ResultSet extensions
        val (resultSetPackageName, _) = PathManager.getOutputDirectory(
            config = config,
            basePackageName = context.packageName,
            tableName = table.tableName,
            category = PathManager.FileCategory.RESULTSET_EXTENSIONS
        )
        val resultSetContext = context.copy(packageName = resultSetPackageName)
        val resultSetContent = generateResultSetExtensionsContent(resultSetContext)
        val resultSetFile = PathManager.createGeneratedFile(
            config = config,
            basePackageName = context.packageName,
            fileName = "${className}Extensions",
            content = resultSetContent,
            tableName = table.tableName,
            category = PathManager.FileCategory.RESULTSET_EXTENSIONS
        )
        generatedFiles.add(resultSetFile)
    }
    
    /**
     * Generate relationship files
     */
    private fun generateRelationshipFiles(basePackageName: String, relationships: SchemaRelationships) {
        val (relationshipPackageName, _) = PathManager.getOutputDirectory(
            config = config,
            basePackageName = basePackageName,
            tableName = null,
            category = PathManager.FileCategory.RELATIONSHIPS
        )
        val relationshipContent = generateRelationshipFileContent(relationshipPackageName, relationships)
        val relationshipFile = PathManager.createGeneratedFile(
            config = config,
            basePackageName = basePackageName,
            fileName = "TableRelationships",
            content = relationshipContent,
            tableName = null,
            category = PathManager.FileCategory.RELATIONSHIPS
        )
        generatedFiles.add(relationshipFile)
    }
    
    /**
     * Generate schema metadata files
     */
    private fun generateSchemaMetadataFiles(
        basePackageName: String,
        tables: List<SQLTableInfo>,
        relationships: SchemaRelationships?
    ) {
        val (metadataPackageName, _) = PathManager.getOutputDirectory(
            config = config,
            basePackageName = basePackageName,
            tableName = null,
            category = PathManager.FileCategory.SCHEMA_METADATA
        )
        val metadataContent = generateSchemaMetadataFileContent(metadataPackageName, tables, relationships)
        val metadataFile = PathManager.createGeneratedFile(
            config = config,
            basePackageName = basePackageName,
            fileName = "SchemaMetadata",
            content = metadataContent,
            tableName = null,
            category = PathManager.FileCategory.SCHEMA_METADATA
        )
        generatedFiles.add(metadataFile)
    }
    
    /**
     * Generate index files for packages
     */
    private fun generateIndexFiles(): List<PathManager.GeneratedFile> {
        if (!config.generateIndexFiles) return emptyList()
        
        val indexFiles = mutableListOf<PathManager.GeneratedFile>()
        val filesByPackage = generatedFiles.groupBy { it.packageName }
        
        filesByPackage.forEach { (packageName, files) ->
            if (PathManager.shouldCreateIndexFile(config, files.size)) {
                val indexContent = PathManager.generateIndexFileContent(packageName, files, config)
                val indexFile = PathManager.GeneratedFile(
                    fileName = "PackageIndex.kt",
                    content = indexContent,
                    packageName = packageName,
                    relativePath = files.first().directory.resolve("PackageIndex.kt"),
                    directory = files.first().directory,
                    fileCategory = PathManager.FileCategory.SCHEMA_METADATA
                )
                indexFiles.add(indexFile)
            }
        }
        
        return indexFiles
    }
    
    /**
     * Write generated files using KSP CodeGenerator
     */
    private fun writeFiles() {
        generatedFiles.forEach { generatedFile ->
            try {
                // Ensure the directory structure exists before writing
                // This is critical because KSP CodeGenerator might fail silently if directories don't exist
                if (config.createDirectoryStructure) {
                    val packagePath = PathManager.packageToPath(generatedFile.packageName)
                    val baseOutputDir = PathManager.validateOutputDirectory(config.outputDir)
                    val fullPackageDir = baseOutputDir.resolve(packagePath)
                    
                    if (!fullPackageDir.exists()) {
                        logger.info("Creating directory structure: $fullPackageDir")
                        Files.createDirectories(fullPackageDir)
                    }
                    
                    // Validate directory access
                    if (!PathManager.validateDirectoryAccess(fullPackageDir)) {
                        throw IllegalStateException("Cannot write to directory: $fullPackageDir")
                    }
                }
                
                val file = codeGenerator.createNewFile(
                    dependencies = Dependencies(false),
                    packageName = generatedFile.packageName,
                    fileName = generatedFile.fileName.removeSuffix(".kt")
                )
                
                file.write(generatedFile.content.toByteArray())
                file.close()
                
                logger.info("Generated: ${generatedFile.fileName} in package ${generatedFile.packageName}")
            } catch (e: Exception) {
                logger.error("Failed to generate ${generatedFile.fileName}: ${e.message}")
                logger.error("Package: ${generatedFile.packageName}, Directory: ${generatedFile.directory}")
                throw e
            }
        }
    }
    
    /**
     * Write index files
     */
    private fun writeIndexFiles(indexFiles: List<PathManager.GeneratedFile>) {
        indexFiles.forEach { indexFile ->
            try {
                // Ensure the directory structure exists before writing
                if (config.createDirectoryStructure) {
                    val packagePath = PathManager.packageToPath(indexFile.packageName)
                    val baseOutputDir = PathManager.validateOutputDirectory(config.outputDir)
                    val fullPackageDir = baseOutputDir.resolve(packagePath)
                    
                    if (!fullPackageDir.exists()) {
                        logger.info("Creating directory structure for index: $fullPackageDir")
                        Files.createDirectories(fullPackageDir)
                    }
                    
                    // Validate directory access
                    if (!PathManager.validateDirectoryAccess(fullPackageDir)) {
                        throw IllegalStateException("Cannot write index to directory: $fullPackageDir")
                    }
                }
                
                val file = codeGenerator.createNewFile(
                    dependencies = Dependencies(false),
                    packageName = indexFile.packageName,
                    fileName = indexFile.fileName.removeSuffix(".kt")
                )
                
                file.write(indexFile.content.toByteArray())
                file.close()
                
                logger.info("Generated index: ${indexFile.fileName}")
            } catch (e: Exception) {
                logger.error("Failed to generate index ${indexFile.fileName}: ${e.message}")
                logger.error("Package: ${indexFile.packageName}, Directory: ${indexFile.directory}")
            }
        }
    }
    
    /**
     * Write build configuration files
     */
    private fun writeBuildConfigFiles(buildConfigFiles: Map<String, String>) {
        val baseOutputDir = PathManager.validateOutputDirectory(config.outputDir)
        
        buildConfigFiles.forEach { (fileName, content) ->
            try {
                val file = baseOutputDir.resolve(fileName)
                
                // Ensure parent directory exists
                val parentDir = file.parent
                if (parentDir != null && !parentDir.exists()) {
                    logger.info("Creating parent directory for build config: $parentDir")
                    Files.createDirectories(parentDir)
                }
                
                Files.write(file, content.toByteArray())
                logger.info("Generated build config: $fileName")
            } catch (e: Exception) {
                logger.warn("Failed to generate build config $fileName: ${e.message}")
            }
        }
    }
    
    /**
     * Validate configuration before generation
     */
    private fun validateConfiguration(basePackageName: String) {
        // Validate package name
        if (!PathManager.validatePackageName(basePackageName)) {
            throw IllegalArgumentException("Invalid package name: $basePackageName")
        }
        
        // Validate output directory access
        val baseOutputDir = PathManager.validateOutputDirectory(config.outputDir)
        if (!PathManager.validateDirectoryAccess(baseOutputDir)) {
            throw IllegalArgumentException("Cannot write to output directory: ${config.outputDir}")
        }
        
        logger.info("Configuration validated successfully")
    }
    
    /**
     * Determine if query files should be generated based on strategy
     */
    private fun shouldGenerateQueryFiles(context: TableGenerationContext): Boolean {
        return when (config.organizationStrategy) {
            PathManager.OrganizationStrategy.FLAT -> false // Keep queries in main entity file
            PathManager.OrganizationStrategy.FEATURE_BASED -> true // Separate query files per feature
            PathManager.OrganizationStrategy.TYPE_BASED -> true // All queries in queries package
            PathManager.OrganizationStrategy.HYBRID -> true // Separate query files
        }
    }
    
    // Content generation methods now delegate to the enhanced generator
    private fun generateEntityFileContent(context: TableGenerationContext): String {
        return generator.generateEntityFile(context, config)
    }
    
    private fun generateKeyFileContent(context: TableGenerationContext): String {
        return generator.generateKeyFile(context, config)
    }
    
    private fun generateTableMetadataContent(context: TableGenerationContext): String {
        return generator.generateTableMetadataFile(context, config)
    }
    
    private fun generateQueryFileContent(context: TableGenerationContext): String {
        return generator.generateQueryFile(context, config)
    }
    
    private fun generateRelationshipFileContent(basePackageName: String, relationships: SchemaRelationships): String {
        return generator.generateRelationshipFile(basePackageName, relationships, config)
    }
    
    private fun generateSchemaMetadataFileContent(
        basePackageName: String,
        tables: List<SQLTableInfo>,
        relationships: SchemaRelationships?
    ): String {
        return generator.generateSchemaMetadataFile(basePackageName, tables, relationships, config)
    }
    
    private fun generateResultSetExtensionsContent(context: TableGenerationContext): String {
        return generator.generateResultSetExtensionsFile(context, config)
    }
} 