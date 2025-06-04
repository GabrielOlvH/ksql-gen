package dev.gabrielolv.kotsql.generator

import dev.gabrielolv.kotsql.model.SQLTableInfo
import dev.gabrielolv.kotsql.model.SchemaRelationships
import dev.gabrielolv.kotsql.util.FileManager
import dev.gabrielolv.kotsql.util.GenerationMetadata
import dev.gabrielolv.kotsql.util.NamingConventions
import dev.gabrielolv.kotsql.util.PathManager

/**
 * Kotlin generator that integrates with the new file management system
 * and supports all organization strategies and configurable output paths.
 * 
 * This class orchestrates the generation of different file types by delegating
 * to specialized generators for each concern.
 */
class KotlinGenerator {
    
    /**
     * Generate entity file content with proper package and imports
     */
    fun generateEntityFile(
        context: FileManager.TableGenerationContext,
        config: PathManager.PathConfig
    ): String {
        val table = context.table
        val className = NamingConventions.tableNameToClassName(table.tableName)
        val packageName = context.packageName
        
        return buildString {
            // Add file header if configured
            if (config.includeTimestamps) {
                append(GenerationMetadata.generateFileHeader("SQL table: ${table.tableName}"))
                appendLine()
            }
            
            // Package declaration
            appendLine("package $packageName")
            appendLine()
            
            // Imports
            val imports = ImportGenerator.generateEntityImports(table, context.includeValidation, context.relationships != null, config)
            imports.forEach { import ->
                appendLine("import $import")
            }
            if (imports.isNotEmpty()) {
                appendLine()
            }
            
            // Main entity class
            append(EntityGenerator.generateEntityClass(table, className, context.includeValidation, context.relationships))
        }
    }
    
    /**
     * Generate composite key file content
     */
    fun generateKeyFile(
        context: FileManager.TableGenerationContext,
        config: PathManager.PathConfig
    ): String {
        val table = context.table
        val className = NamingConventions.tableNameToClassName(table.tableName)
        val keyClassName = "${className}Key"
        val packageName = context.packageName
        
        return buildString {
            // Add file header if configured
            if (config.includeTimestamps) {
                append(GenerationMetadata.generateFileHeader("Composite key for SQL table: ${table.tableName}"))
                appendLine()
            }
            
            // Package declaration
            appendLine("package $packageName")
            appendLine()
            
            // Imports
            val imports = ImportGenerator.generateKeyImports(table, context.includeValidation)
            imports.forEach { import ->
                appendLine("import $import")
            }
            
            appendLine()
            
            // Key class
            append(EntityGenerator.generateCompositeKeyClass(table, keyClassName, context.includeValidation))
        }
    }
    
    /**
     * Generate table metadata file content
     */
    fun generateTableMetadataFile(
        context: FileManager.TableGenerationContext,
        config: PathManager.PathConfig
    ): String {
        val table = context.table
        val className = NamingConventions.tableNameToClassName(table.tableName)
        val tableClassName = NamingConventions.generateTableMetadataName(className)
        val packageName = context.packageName
        
        return buildString {
            // Add file header if configured
            if (config.includeTimestamps) {
                append(GenerationMetadata.generateFileHeader("Table metadata for SQL table: ${table.tableName}"))
                appendLine()
            }
            
            // Package declaration
            appendLine("package $packageName")
            appendLine()
            
            // Imports
            val imports = ImportGenerator.generateTableMetadataImports(table, context.relationships != null)
            imports.forEach { import ->
                appendLine("import $import")
            }
            
            if (imports.isNotEmpty()) {
                appendLine()
            }
            
            // Table metadata class
            append(TableMetadataGenerator.generateTableMetadataClass(table, tableClassName, context.relationships))
        }
    }
    
    /**
     * Generate query helpers file content
     */
    fun generateQueryFile(
        context: FileManager.TableGenerationContext,
        config: PathManager.PathConfig
    ): String {
        val table = context.table
        val className = NamingConventions.tableNameToClassName(table.tableName)
        val queryClassName = "${className}Queries"
        val packageName = context.packageName
        
        return buildString {
            // Add file header if configured
            if (config.includeTimestamps) {
                append(GenerationMetadata.generateFileHeader("Query helpers for SQL table: ${table.tableName}"))
                appendLine()
            }
            
            // Package declaration
            appendLine("package $packageName")
            appendLine()
            
            // Imports
            val imports = ImportGenerator.generateQueryImports(table, packageName, config, context.relationships != null)
            imports.forEach { import ->
                appendLine("import $import")
            }
            appendLine()
            
            // Query helpers class
            append(QueryGenerator.generateQueryHelpersClass(table, queryClassName, context.relationships))
        }
    }
    
    /**
     * Generate relationship definitions file content
     */
    fun generateRelationshipFile(
        packageName: String,
        relationships: SchemaRelationships,
        config: PathManager.PathConfig
    ): String {
        return buildString {
            // Add file header if configured
            if (config.includeTimestamps) {
                append(GenerationMetadata.generateFileHeader("Table relationships"))
                appendLine()
            }
            
            // Package declaration
            appendLine("package $packageName")
            appendLine()
            
            // Imports
            val imports = ImportGenerator.generateRelationshipImports()
            imports.forEach { import ->
                appendLine("import $import")
            }
            appendLine()
            
            // Relationships object
            append(RelationshipGenerator.generateRelationshipsObject(relationships))
        }
    }
    
    /**
     * Generate schema metadata file content
     */
    fun generateSchemaMetadataFile(
        packageName: String,
        tables: List<SQLTableInfo>,
        relationships: SchemaRelationships?,
        config: PathManager.PathConfig
    ): String {
        return buildString {
            // Add file header if configured
            if (config.includeTimestamps) {
                append(GenerationMetadata.generateFileHeader("Schema metadata"))
                appendLine()
            }
            
            // Package declaration
            appendLine("package $packageName")
            appendLine()
            
            // Imports
            val imports = ImportGenerator.generateSchemaMetadataImports()
            imports.forEach { import ->
                appendLine("import $import")
            }
            appendLine()
            
            // Schema metadata object
            append(RelationshipGenerator.generateSchemaMetadataObject(tables, relationships))
        }
    }
    
    /**
     * Compatibility method for tests and legacy code
     * Generates a monolithic file like the old KotlinDataClassGenerator
     */
    fun generateDataClassFile(
        table: SQLTableInfo,
        packageName: String,
        includeValidation: Boolean = true,
        relationships: SchemaRelationships? = null
    ): String {
        val className = NamingConventions.tableNameToClassName(table.tableName)
        
        return buildString {
            appendLine("package $packageName")
            appendLine()
            
            // Generate all imports needed for a monolithic file
            val allImports = ImportGenerator.generateMonolithicImports(table, includeValidation, relationships != null)
            
            allImports.sorted().forEach { import ->
                appendLine("import $import")
            }
            if (allImports.isNotEmpty()) {
                appendLine()
            }
            
            // Generate composite key class if needed
            if (table.hasCompositePrimaryKey) {
                val keyClassName = "${className}Key"
                append(EntityGenerator.generateCompositeKeyClass(table, keyClassName, includeValidation))
                appendLine()
                appendLine()
            }
            
            // Generate main entity class
            append(EntityGenerator.generateEntityClass(table, className, includeValidation, relationships))
            appendLine()
            appendLine()
            
            // Generate table metadata
            val tableClassName = NamingConventions.generateTableMetadataName(className)
            append(TableMetadataGenerator.generateTableMetadataClass(table, tableClassName, relationships))
            
            // Generate validation helpers if validation is enabled
            if (includeValidation) {
                appendLine()
                appendLine()
                append(EntityGenerator.generateValidationHelpers(className))
            }
        }
    }
    
    /**
     * Compatibility method for tests - generates monolithic files for multiple tables
     */
    fun generateAllDataClasses(
        tables: List<SQLTableInfo>,
        packageName: String,
        includeValidation: Boolean = true,
        relationships: SchemaRelationships? = null
    ): Map<String, String> {
        return tables.associate { table ->
            val className = NamingConventions.tableNameToClassName(table.tableName)
            val fileName = "$className.kt"
            val fileContent = generateDataClassFile(table, packageName, includeValidation, relationships)
            fileName to fileContent
        }
    }
} 