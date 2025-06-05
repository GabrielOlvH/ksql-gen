package dev.gabrielolv.kotsql.generator

import dev.gabrielolv.kotsql.mapping.TypeMapper
import dev.gabrielolv.kotsql.model.SQLTableInfo
import dev.gabrielolv.kotsql.model.SchemaRelationships
import dev.gabrielolv.kotsql.util.NamingConventions
import dev.gabrielolv.kotsql.util.PathManager
import dev.gabrielolv.kotsql.validation.ValidationMapper

/**
 * Handles import generation for different file types
 */
object ImportGenerator {
    
    /**
     * Generate imports for entity files based on organization strategy
     */
    fun generateEntityImports(
        table: SQLTableInfo,
        includeValidation: Boolean,
        includeRelationships: Boolean,
        config: PathManager.PathConfig,
        relationships: SchemaRelationships? = null,
        currentPackage: String? = null
    ): List<String> {
        val imports = mutableSetOf<String>()
        
        // Always include serialization imports
        imports.add("kotlinx.serialization.Serializable")
        
        // Add SerialName import if any column needs it
        val needsSerialName = table.columns.any { column ->
            val propertyName = NamingConventions.columnNameToPropertyName(column.columnName)
            NamingConventions.needsSerialNameAnnotation(column.columnName, propertyName)
        }
        if (needsSerialName) {
            imports.add("kotlinx.serialization.SerialName")
        }
        
        // Add imports for composite primary keys based on organization strategy
        if (table.hasCompositePrimaryKey) {
            when (config.organizationStrategy) {
                PathManager.OrganizationStrategy.TYPE_BASED -> {
                    // Key is in separate package, need import
                    imports.add("${getBasePackage(config, currentPackage)}.keys.${NamingConventions.tableNameToClassName(table.tableName)}Key")
                }
                PathManager.OrganizationStrategy.FLAT -> {
                    // Key is in same package, no import needed
                }
                else -> {
                    // Feature-based or hybrid - key might be in same or different package
                    // For now, assume same package
                }
            }
        }
        
        // Add imports for custom types
        imports.addAll(TypeMapper.getAllRequiredImports(table.columns))
        
        // Add validation imports if enabled
        if (includeValidation) {
            val validationAnnotations = table.columns.flatMap { column ->
                ValidationMapper.getValidationAnnotations(column)
            }.map { it.name }.toSet()
            
            validationAnnotations.forEach { annotationName ->
                imports.add("dev.gabrielolv.kotsql.validation.$annotationName")
            }
            
            imports.add("dev.gabrielolv.kotsql.validation.ValidationEngine")
            imports.add("dev.gabrielolv.kotsql.validation.ValidationResult")
        }
        
        // Add relationship imports if enabled
        if (includeRelationships && relationships != null) {
            // Add imports for related entity classes
            val allRelatedTables = mutableSetOf<String>()
            
            // Get outgoing relationships (this table references others)
            relationships.getOutgoingRelationships(table.tableName).forEach { rel ->
                allRelatedTables.add(rel.toTable)
            }
            
            // Get incoming relationships (other tables reference this table)
            relationships.getIncomingRelationships(table.tableName).forEach { rel ->
                allRelatedTables.add(rel.fromTable)
            }
            
            // Add imports for related entity classes based on organization strategy
            allRelatedTables.forEach { relatedTable ->
                val relatedClassName = NamingConventions.tableNameToClassName(relatedTable)
                when (config.organizationStrategy) {
                    PathManager.OrganizationStrategy.TYPE_BASED -> {
                        imports.add("${getBasePackage(config, currentPackage)}.entities.$relatedClassName")
                    }
                    PathManager.OrganizationStrategy.FEATURE_BASED -> {
                        imports.add("${getBasePackage(config, currentPackage)}.${relatedTable.lowercase()}.$relatedClassName")
                    }
                    PathManager.OrganizationStrategy.HYBRID -> {
                        imports.add("${getBasePackage(config, currentPackage)}.${relatedTable.lowercase()}.$relatedClassName")
                    }
                    PathManager.OrganizationStrategy.FLAT -> {
                        // Same package, no import needed
                    }
                }
            }
            
            // Add relationship framework imports
            when (config.organizationStrategy) {
                PathManager.OrganizationStrategy.TYPE_BASED -> {
                    imports.add("${getBasePackage(config, currentPackage)}.relationships.TableRelationships")
                }
                else -> {
                    // Relationships in same or accessible package
                }
            }
        }
        
        return imports.sorted()
    }
    
    /**
     * Generate imports for composite key files
     */
    fun generateKeyImports(
        table: SQLTableInfo,
        includeValidation: Boolean
    ): List<String> {
        val imports = mutableSetOf<String>()
        
        imports.add("kotlinx.serialization.Serializable")
        
        // Add SerialName import if any column needs it
        val needsSerialName = table.primaryKeyColumns.any { column ->
            val propertyName = NamingConventions.columnNameToPropertyName(column.columnName)
            NamingConventions.needsSerialNameAnnotation(column.columnName, propertyName)
        }
        if (needsSerialName) {
            imports.add("kotlinx.serialization.SerialName")
        }
        
        // Add type-specific imports for primary key columns
        imports.addAll(TypeMapper.getAllRequiredImports(table.primaryKeyColumns))
        
        // Add validation imports if enabled
        if (includeValidation) {
            val validationAnnotations = table.primaryKeyColumns.flatMap { column ->
                ValidationMapper.getValidationAnnotations(column)
            }.map { it.name }.toSet()
            
            validationAnnotations.forEach { annotationName ->
                imports.add("dev.gabrielolv.kotsql.validation.$annotationName")
            }
        }
        
        return imports.sorted()
    }
    
    /**
     * Generate imports for table metadata files
     */
    fun generateTableMetadataImports(
        table: SQLTableInfo,
        includeRelationships: Boolean
    ): List<String> {
        val imports = mutableSetOf<String>()
        
        imports.add("dev.gabrielolv.kotsql.query.Column")
        imports.add("dev.gabrielolv.kotsql.query.TypeSafeQuery")
        imports.add("dev.gabrielolv.kotsql.query.QueryResult")
        
        if (includeRelationships) {
            imports.add("dev.gabrielolv.kotsql.query.JoinQuery")
            imports.add("dev.gabrielolv.kotsql.model.RelationshipInfo")
        }
        
        // Add CompositeKey import for tables with composite primary keys
        if (table.hasCompositePrimaryKey) {
            imports.add("dev.gabrielolv.kotsql.model.CompositeKey")
        }
        
        // Add type-specific imports (including Vector)
        imports.addAll(TypeMapper.getAllRequiredImports(table.columns))
        
        return imports.sorted()
    }
    
    /**
     * Generate imports for query helper files
     */
    fun generateQueryImports(
        table: SQLTableInfo,
        packageName: String,
        config: PathManager.PathConfig,
        includeRelationships: Boolean
    ): List<String> {
        val imports = mutableSetOf<String>()
        
        imports.add("dev.gabrielolv.kotsql.query.TypeSafeQuery")
        imports.add("dev.gabrielolv.kotsql.query.Column")
        imports.add("dev.gabrielolv.kotsql.query.QueryResult")
        imports.add("dev.gabrielolv.kotsql.query.like")
        
        if (includeRelationships) {
            imports.add("dev.gabrielolv.kotsql.query.JoinQuery")
        }
        
        // Add imports for the entity class and table metadata based on organization strategy
        val className = NamingConventions.tableNameToClassName(table.tableName)
        when (config.organizationStrategy) {
            PathManager.OrganizationStrategy.TYPE_BASED -> {
                // Need to import from other packages
                val basePackage = packageName.substringBeforeLast('.') // Remove ".queries"
                imports.add("$basePackage.entities.$className")
                imports.add("$basePackage.tables.${className}Table")
                if (table.hasCompositePrimaryKey) {
                    imports.add("$basePackage.keys.${className}Key")
                }
            }
            PathManager.OrganizationStrategy.HYBRID -> {
                // Entity is in feature package, others are in type packages
                val basePackage = packageName.substringBeforeLast('.') // Remove ".queries"
                imports.add("$basePackage.${table.tableName.lowercase()}.$className")
                imports.add("$basePackage.tables.${className}Table")
                if (table.hasCompositePrimaryKey) {
                    imports.add("$basePackage.keys.${className}Key")
                }
            }
            PathManager.OrganizationStrategy.FEATURE_BASED -> {
                // Everything should be in the same package, but table metadata might be separate
                // Check if we're generating in the same package
                // No imports needed as everything is in the same package
            }
            PathManager.OrganizationStrategy.FLAT -> {
                // Everything is in the same package, no imports needed
            }
        }
        
        return imports.sorted()
    }
    
    /**
     * Generate imports for relationship files
     */
    fun generateRelationshipImports(): List<String> {
        return listOf(
            "dev.gabrielolv.kotsql.model.RelationshipInfo",
            "dev.gabrielolv.kotsql.model.SchemaRelationships"
        )
    }
    
    /**
     * Generate imports for schema metadata files
     */
    fun generateSchemaMetadataImports(
        tables: List<SQLTableInfo>,
        packageName: String,
        config: PathManager.PathConfig
    ): List<String> {
        val imports = mutableSetOf<String>()
        
        imports.add("dev.gabrielolv.kotsql.schema.SchemaVersion")
        imports.add("dev.gabrielolv.kotsql.model.SchemaRelationships")
        imports.add("kotlinx.datetime.Instant")
        
        // Add imports for entity classes based on organization strategy
        tables.forEach { table ->
            val className = NamingConventions.tableNameToClassName(table.tableName)
            when (config.organizationStrategy) {
                PathManager.OrganizationStrategy.TYPE_BASED -> {
                    // Entities are in a separate entities package
                    val basePackage = packageName.substringBeforeLast('.', packageName)
                    imports.add("$basePackage.entities.$className")
                }
                PathManager.OrganizationStrategy.HYBRID -> {
                    // Entity is in feature package
                    val basePackage = packageName.substringBeforeLast('.', packageName)
                    imports.add("$basePackage.${table.tableName.lowercase()}.$className")
                }
                PathManager.OrganizationStrategy.FEATURE_BASED -> {
                    // Everything should be in the same package, but might need import
                    if (!packageName.contains(table.tableName.lowercase())) {
                        imports.add("$packageName.${table.tableName.lowercase()}.$className")
                    }
                }
                PathManager.OrganizationStrategy.FLAT -> {
                    // Everything is in the same package, no imports needed
                }
            }
        }
        
        return imports.sorted()
    }
    
    /**
     * Generate imports for monolithic compatibility files
     */
    fun generateMonolithicImports(
        table: SQLTableInfo,
        includeValidation: Boolean,
        includeRelationships: Boolean
    ): Set<String> {
        val imports = mutableSetOf<String>()
        
        // Always include serialization imports
        imports.add("kotlinx.serialization.Serializable")
        
        // Add SerialName import if any column needs it
        val needsSerialName = table.columns.any { column ->
            val propertyName = NamingConventions.columnNameToPropertyName(column.columnName)
            NamingConventions.needsSerialNameAnnotation(column.columnName, propertyName)
        }
        if (needsSerialName) {
            imports.add("kotlinx.serialization.SerialName")
        }
        
        // Add imports for custom types
        imports.addAll(TypeMapper.getAllRequiredImports(table.columns))
        
        // Add validation imports if enabled
        if (includeValidation) {
            val validationAnnotations = table.columns.flatMap { column ->
                ValidationMapper.getValidationAnnotations(column)
            }.map { it.name }.toSet()
            
            validationAnnotations.forEach { annotationName ->
                imports.add("dev.gabrielolv.kotsql.validation.$annotationName")
            }
            
            imports.add("dev.gabrielolv.kotsql.validation.ValidationEngine")
            imports.add("dev.gabrielolv.kotsql.validation.ValidationResult")
        }
        
        // Add CompositeKey import for tables with composite primary keys
        if (table.hasCompositePrimaryKey) {
            imports.add("dev.gabrielolv.kotsql.model.CompositeKey")
        }
        
        // Add relationship imports if enabled
        if (includeRelationships) {
            imports.add("dev.gabrielolv.kotsql.model.RelationshipInfo")
            imports.add("dev.gabrielolv.kotsql.query.JoinQuery")
        }
        
        return imports
    }
    
    /**
     * Extract base package from configuration
     */
    private fun getBasePackage(config: PathManager.PathConfig, currentPackage: String? = null): String {
        // Extract base package from current package or use config
        return when {
            currentPackage != null -> {
                // Remove the last segment to get the base package
                currentPackage.substringBeforeLast('.', currentPackage)
            }
            else -> "dev.gabrielolv.generated" // fallback
        }
    }
} 