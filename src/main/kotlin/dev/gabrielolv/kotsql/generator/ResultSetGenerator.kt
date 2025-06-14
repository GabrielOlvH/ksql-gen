package dev.gabrielolv.kotsql.generator

import dev.gabrielolv.kotsql.mapping.TypeMapper
import dev.gabrielolv.kotsql.model.SQLColumnInfo
import dev.gabrielolv.kotsql.model.SQLTableInfo
import dev.gabrielolv.kotsql.model.SchemaRelationships
import dev.gabrielolv.kotsql.util.NamingConventions
import java.sql.ResultSet
import java.sql.SQLException

/**
 * Handles generation of ResultSet parsing utilities for data classes with relationship support
 */
object ResultSetGenerator {
    
    /**
     * Get all required imports for ResultSet extensions (excluding entity classes which are handled by ImportGenerator)
     */
    fun getRequiredImports(
        table: SQLTableInfo, 
        relationships: SchemaRelationships? = null
    ): List<String> {
        val imports = mutableSetOf<String>()
        
        // SQL imports
        imports.add("java.sql.ResultSet")
        imports.add("java.sql.SQLException")
        imports.add("java.sql.PreparedStatement") // For companion methods
        
        // Always include Vector import (even for tables without vector columns)
        // since parseVector utility functions are always generated
        imports.add("dev.gabrielolv.kotsql.vector.Vector")
        
        // Type-specific imports for the main table (excluding entity classes)
        imports.addAll(TypeMapper.getAllRequiredImports(table.columns))
        
        return imports.sorted()
    }
    
    /**
     * Generate ResultSet extension methods for parsing rows into data classes with smart relationship detection
     */
    fun generateResultSetExtensions(
        table: SQLTableInfo,
        className: String,
        relationships: SchemaRelationships? = null,
        allTables: List<SQLTableInfo>? = null
    ): String {
        return buildString {
            appendLine("/**")
            appendLine(" * ResultSet parsing extensions for ${table.tableName} table")
            appendLine(" * Supports automatic relationship population from joined ResultSets")
            appendLine(" */")
            appendLine()
            
            // Generate main parsing method with relationship support
            appendLine("/**")
            appendLine(" * Parse current ResultSet row into a $className instance")
            appendLine(" * Automatically detects and populates relationships from joined data")
            appendLine(" * @throws SQLException if column access fails")
            appendLine(" * @throws IllegalStateException if ResultSet is not positioned on a valid row")
            appendLine(" */")
            appendLine("fun ResultSet.to$className(): $className {")
            appendLine("    return $className(")
            
            table.columns.forEachIndexed { index, column ->
                val propertyName = NamingConventions.columnNameToPropertyName(column.columnName)
                val getter = generateResultSetGetter(column)
                appendLine("        $propertyName = $getter")
                if (index < table.columns.size - 1) {
                    appendLine(",")
                } else {
                    appendLine()
                }
            }
            
            appendLine("    )")
            appendLine("}")
            appendLine()
            
            // Generate smart list parsing with relationship detection
            if (relationships != null) {
                generateSmartListParsing(table, className, relationships, allTables)
            } else {
                generateSimpleListParsing(className)
            }
            
            // Generate nullable parsing method
            appendLine("/**")
            appendLine(" * Parse current ResultSet row into a $className instance, returning null if positioned before first or after last")
            appendLine(" */")
            appendLine("fun ResultSet.to${className}OrNull(): $className? {")
            appendLine("    return try {")
            appendLine("        if (isBeforeFirst || isAfterLast) null else to$className()")
            appendLine("    } catch (e: SQLException) {")
            appendLine("        null")
            appendLine("    }")
            appendLine("}")
            appendLine()
            
            // Generate sequence parsing method for large datasets
            appendLine("/**")
            appendLine(" * Parse ResultSet as a sequence of $className instances for memory-efficient processing")
            appendLine(" * Note: The ResultSet will be closed when the sequence is fully consumed")
            appendLine(" */")
            appendLine("fun ResultSet.to${className}Sequence(): Sequence<$className> = sequence {")
            appendLine("    try {")
            appendLine("        while (next()) {")
            appendLine("            yield(to$className())")
            appendLine("        }")
            appendLine("    } finally {")
            appendLine("        close()")
            appendLine("    }")
            appendLine("}")
            appendLine()
            
            // Generate column index-based method for performance
            appendLine("/**")
            appendLine(" * Parse current ResultSet row using column indices for better performance")
            appendLine(" * Use this when you know the exact column order in your SELECT statement")
            appendLine(" */")
            appendLine("fun ResultSet.to${className}ByIndex(): $className {")
            appendLine("    return $className(")
            
            table.columns.forEachIndexed { index, column ->
                val propertyName = NamingConventions.columnNameToPropertyName(column.columnName)
                val getter = generateResultSetGetterByIndex(column, index + 1)
                appendLine("        $propertyName = $getter")
                if (index < table.columns.size - 1) {
                    appendLine(",")
                } else {
                    appendLine()
                }
            }
            
            appendLine("    )")
            appendLine("}")
            appendLine()
        }
    }
    
    /**
     * Generate smart list parsing that detects joined tables and populates relationships
     */
    private fun StringBuilder.generateSmartListParsing(
        table: SQLTableInfo,
        className: String,
        relationships: SchemaRelationships,
        allTables: List<SQLTableInfo>? = null
    ) {
        appendLine("/**")
        appendLine(" * Parse all rows from ResultSet into a list of $className instances")
        appendLine(" * Automatically detects joined tables and populates relationships")
        appendLine(" * @param closeAfter whether to close the ResultSet after parsing (default: true)")
        appendLine(" */")
        appendLine("fun ResultSet.to${className}List(closeAfter: Boolean = true): List<$className> {")
        appendLine("    try {")
        appendLine("        // Detect which tables are present in the ResultSet")
        appendLine("        val availableTables = detectJoinedTables()")
        appendLine("        ")
        appendLine("        // Use enhanced parsing when related tables are available")
        appendLine("        return when {")
        
        // Collect all conditions with their tables to avoid duplicates
        val conditionsGenerated = mutableSetOf<String>()
        
        // Generate specific parsing methods for each relationship
        val outgoingRels = relationships.getOutgoingRelationships(table.tableName)
        val incomingRels = relationships.getIncomingRelationships(table.tableName)
        
        // Generate conditions for outgoing relationships (ManyToOne, OneToOne) first
        outgoingRels.forEach { rel ->
            when (rel) {
                is dev.gabrielolv.kotsql.model.RelationshipInfo.ManyToOne,
                is dev.gabrielolv.kotsql.model.RelationshipInfo.OneToOne -> {
                    val relatedTable = rel.toTable
                    if (!conditionsGenerated.contains(relatedTable)) {
                        appendLine("            availableTables.contains(\"${relatedTable}\") -> to${className}ListWith${NamingConventions.tableNameToClassName(relatedTable)}()")
                        conditionsGenerated.add(relatedTable)
                    }
                }
                else -> {}
            }
        }
        
        // Generate conditions for OneToMany relationships where this table is the parent (outgoing)
        outgoingRels.filterIsInstance<dev.gabrielolv.kotsql.model.RelationshipInfo.OneToMany>().forEach { rel ->
            val relatedTable = rel.toTable
            if (!conditionsGenerated.contains(relatedTable)) {
                appendLine("            availableTables.contains(\"${relatedTable}\") -> to${className}ListWith${NamingConventions.tableNameToClassName(relatedTable)}Children()")
                conditionsGenerated.add(relatedTable)
            }
        }

        // Handle many-to-many relationships
        val manyToManyRels = relationships.relationships.filterIsInstance<dev.gabrielolv.kotsql.model.RelationshipInfo.ManyToMany>()
            .filter { it.fromTable == table.tableName || it.toTable == table.tableName }
        
        manyToManyRels.forEach { rel ->
            val isFromTable = table.tableName == rel.fromTable
            val relatedTable = if (isFromTable) rel.toTable else rel.fromTable
            if (!conditionsGenerated.contains(relatedTable)) {
                appendLine("            availableTables.contains(\"${relatedTable}\") -> to${className}ListWith${NamingConventions.tableNameToClassName(relatedTable)}()")
                conditionsGenerated.add(relatedTable)
            }
        }
        
        appendLine("            else -> to${className}ListSimple()")
        appendLine("        }")
        appendLine("    } finally {")
        appendLine("        if (closeAfter) {")
        appendLine("            close()")
        appendLine("        }")
        appendLine("    }")
        appendLine("}")
        appendLine()
        
        // Generate the detection method
        generateTableDetectionMethod(table, relationships, allTables)
        
        // Generate simple parsing method
        appendLine("/**")
        appendLine(" * Parse ResultSet without any relationships (simple case)")
        appendLine(" */")
        appendLine("private fun ResultSet.to${className}ListSimple(): List<$className> {")
        appendLine("    val result = mutableListOf<$className>()")
        appendLine("    while (next()) {")
        appendLine("        result.add(to$className())")
        appendLine("    }")
        appendLine("    return result")
        appendLine("}")
        appendLine()
        
        // Generate enhanced relationship parsing methods
        generateEnhancedRelationshipMethods(table, className, relationships, allTables)
        
        // Generate entity extraction methods for related tables
        generateEntityExtractionMethods(table, relationships, allTables)
    }
    
    /**
     * Generate enhanced relationship parsing methods
     */
    private fun StringBuilder.generateEnhancedRelationshipMethods(
        table: SQLTableInfo,
        className: String,
        relationships: SchemaRelationships,
        allTables: List<SQLTableInfo>?
    ) {
        val outgoingRels = relationships.getOutgoingRelationships(table.tableName)
        val incomingRels = relationships.getIncomingRelationships(table.tableName)
        val generatedMethods = mutableSetOf<String>()
        
        // Generate methods for outgoing relationships (ManyToOne, OneToOne)
        outgoingRels.forEach { rel ->
            when (rel) {
                is dev.gabrielolv.kotsql.model.RelationshipInfo.ManyToOne,
                is dev.gabrielolv.kotsql.model.RelationshipInfo.OneToOne -> {
                    val methodName = "to${className}ListWith${NamingConventions.tableNameToClassName(rel.toTable)}"
                    if (!generatedMethods.contains(methodName)) {
                        generateOutgoingRelationshipMethod(table, className, rel, allTables)
                        generatedMethods.add(methodName)
                    }
                }
                else -> {}
            }
        }
        
        // Generate methods for incoming OneToMany relationships
        outgoingRels.filterIsInstance<dev.gabrielolv.kotsql.model.RelationshipInfo.OneToMany>().forEach { rel ->
            val methodName = "to${className}ListWith${NamingConventions.tableNameToClassName(rel.toTable)}Children"
            if (!generatedMethods.contains(methodName)) {
                generateIncomingRelationshipMethod(table, className, rel, relationships, allTables)
                generatedMethods.add(methodName)
            }
        }
        
        // Generate methods for ManyToMany relationships
        val manyToManyRels = relationships.relationships.filterIsInstance<dev.gabrielolv.kotsql.model.RelationshipInfo.ManyToMany>()
            .filter { it.fromTable == table.tableName || it.toTable == table.tableName }
        
        manyToManyRels.forEach { rel ->
            val isFromTable = table.tableName == rel.fromTable
            val relatedTable = if (isFromTable) rel.toTable else rel.fromTable
            val methodName = "to${className}ListWith${NamingConventions.tableNameToClassName(relatedTable)}"
            if (!generatedMethods.contains(methodName)) {
                generateManyToManyRelationshipMethod(table, className, rel, allTables)
                generatedMethods.add(methodName)
            }
        }
    }
    
    /**
     * Generate entity extraction methods for all related tables
     */
    private fun StringBuilder.generateEntityExtractionMethods(
        table: SQLTableInfo,
        relationships: SchemaRelationships,
        allTables: List<SQLTableInfo>?
    ) {
        val relatedTables = mutableSetOf<String>()
        
        // Collect all related table names
        relationships.getOutgoingRelationships(table.tableName).forEach { rel ->
            relatedTables.add(rel.toTable)

            // If this is a OneToMany child, also include its outgoing ManyToOne/OneToOne tables
            if (rel is dev.gabrielolv.kotsql.model.RelationshipInfo.OneToMany) {
                val childOutgoing = relationships.getOutgoingRelationships(rel.toTable)
                childOutgoing.forEach { childRel ->
                    when (childRel) {
                        is dev.gabrielolv.kotsql.model.RelationshipInfo.ManyToOne,
                        is dev.gabrielolv.kotsql.model.RelationshipInfo.OneToOne -> {
                            relatedTables.add(childRel.toTable)
                        }
                        else -> {}
                    }
                }
            }
        }
        
        relationships.getIncomingRelationships(table.tableName).forEach { rel ->
            relatedTables.add(rel.fromTable)
        }
        
        // Generate extraction method for each related table
        relatedTables.forEach { relatedTableName ->
            val relatedClassName = NamingConventions.tableNameToClassName(relatedTableName)
            generateEntityExtractionMethod(relatedTableName, relatedClassName, relationships, allTables)
        }
    }
    
    /**
     * Generate parsing method for outgoing relationships (ManyToOne, OneToOne)
     */
    private fun StringBuilder.generateOutgoingRelationshipMethod(
        table: SQLTableInfo,
        className: String,
        relationship: dev.gabrielolv.kotsql.model.RelationshipInfo,
        allTables: List<SQLTableInfo>?
    ) {
        val relatedClassName = NamingConventions.tableNameToClassName(relationship.toTable)
        val methodName = "to${className}ListWith$relatedClassName"
        val relationshipPropertyName = NamingConventions.tableNameToPropertyName(relationship.toTable)
        
        appendLine("/**")
        appendLine(" * Parse $className list with ${relationship.toTable} relationship populated")
        appendLine(" * Expects ResultSet from a JOIN query with ${relationship.toTable} table")
        appendLine(" */")
        appendLine("private fun ResultSet.${methodName}(): List<$className> {")
        appendLine("    val result = mutableMapOf<String, $className>()")
        appendLine("    ")
        appendLine("    while (next()) {")
        appendLine("        // Get the primary key for deduplication")
        
        if (table.primaryKeyColumns.size == 1) {
            val pkColumn = table.primaryKeyColumns.first()
            appendLine("        val entityKey = getString(\"${pkColumn.columnName}\")")
        } else {
            // Composite key handling
            val keyParts = table.primaryKeyColumns.joinToString(" + \"-\" + ") { "getString(\"${it.columnName}\")" }
            appendLine("        val entityKey = $keyParts")
        }
        
        appendLine("        ")
        appendLine("        if (!result.containsKey(entityKey)) {")
        appendLine("            // Parse main entity")
        appendLine("            val baseEntity = to$className()")
        appendLine("            ")
        appendLine("            // Extract and populate related entity if present")
        appendLine("            val entityWithRelationship = try {")
        appendLine("                val related$relatedClassName = extract${relatedClassName}FromResultSet()")
        appendLine("                baseEntity.copy($relationshipPropertyName = related$relatedClassName)")
        appendLine("            } catch (e: Exception) {")
        appendLine("                // Related entity might be null in LEFT JOIN, use base entity")
        appendLine("                baseEntity")
        appendLine("            }")
        appendLine("            ")
        appendLine("            result[entityKey] = entityWithRelationship")
        appendLine("        }")
        appendLine("    }")
        appendLine("    ")
        appendLine("    return result.values.toList()")
        appendLine("}")
        appendLine()
    }
    
    /**
     * Generate parsing method for incoming OneToMany relationships
     */
    private fun StringBuilder.generateIncomingRelationshipMethod(
        table: SQLTableInfo,
        className: String,
        relationship: dev.gabrielolv.kotsql.model.RelationshipInfo.OneToMany,
        relationships: SchemaRelationships,
        allTables: List<SQLTableInfo>?
    ) {
        val relatedClassName = NamingConventions.tableNameToClassName(relationship.toTable)
        val methodName = "to${className}ListWith${relatedClassName}Children"
        val relationshipPropertyName = NamingConventions.tableNameToPluralPropertyName(relationship.toTable)
        
        appendLine("/**")
        appendLine(" * Parse $className list with ${relationship.toTable} children populated")
        appendLine(" * Groups related ${relationship.toTable} records by parent $className")
        appendLine(" */")
        appendLine("private fun ResultSet.${methodName}(): List<$className> {")
        appendLine("    val parentMap = mutableMapOf<String, $className>()")
        appendLine("    val childrenMap = mutableMapOf<String, MutableList<$relatedClassName>>()")
        appendLine("    ")
        appendLine("    while (next()) {")
        
        // Handle primary key for parent entity
        if (table.primaryKeyColumns.size == 1) {
            val pkColumn = table.primaryKeyColumns.first()
            appendLine("        val parentKey = getString(\"${pkColumn.columnName}\")")
        } else {
            val keyParts = table.primaryKeyColumns.joinToString(" + \"-\" + ") { "getString(\"${it.columnName}\")" }
            appendLine("        val parentKey = $keyParts")
        }
        
        appendLine("        ")
        appendLine("        // Parse parent entity if not already processed")
        appendLine("        if (!parentMap.containsKey(parentKey)) {")
        appendLine("            val parent = to$className()")
        appendLine("            parentMap[parentKey] = parent")
        appendLine("            childrenMap[parentKey] = mutableListOf()")
        appendLine("        }")
        appendLine("        ")
        appendLine("        // Parse child entity if present")
        appendLine("        try {")
        appendLine("            var child = extract${relatedClassName}FromResultSet()")

        // Populate child's outgoing ManyToOne / OneToOne relationships if available in ResultSet
        val childOutgoing = relationships.getOutgoingRelationships(relationship.toTable)
        childOutgoing.forEach { relOut ->
            when (relOut) {
                is dev.gabrielolv.kotsql.model.RelationshipInfo.ManyToOne,
                is dev.gabrielolv.kotsql.model.RelationshipInfo.OneToOne -> {
                    val targetClass = NamingConventions.tableNameToClassName(relOut.toTable)
                    val propertyName = NamingConventions.tableNameToPropertyName(relOut.toTable)
                    appendLine("            try {")
                    appendLine("                val related$targetClass = extract${targetClass}FromResultSet()")
                    appendLine("                child = child.copy($propertyName = related$targetClass)")
                    appendLine("            } catch (e: Exception) { }")
                }
                else -> {}
            }
        }

        appendLine("            childrenMap[parentKey]?.add(child)")
        appendLine("        } catch (e: Exception) {")
        appendLine("            // Child data might be null or missing, skip")
        appendLine("        }")
        appendLine("    }")
        appendLine("    ")
        appendLine("    // Create final entities with populated children")
        appendLine("    return parentMap.map { (key, parent) ->")
        appendLine("        val children = childrenMap[key] ?: emptyList()")
        appendLine("        parent.copy($relationshipPropertyName = children)")
        appendLine("    }")
        appendLine("}")
        appendLine()
    }
    
    /**
     * Generate parsing method for ManyToMany relationships
     */
    private fun StringBuilder.generateManyToManyRelationshipMethod(
        table: SQLTableInfo,
        className: String,
        relationship: dev.gabrielolv.kotsql.model.RelationshipInfo.ManyToMany,
        allTables: List<SQLTableInfo>?
    ) {
        val isFromTable = table.tableName == relationship.fromTable
        val relatedTable = if (isFromTable) relationship.toTable else relationship.fromTable
        val relatedClassName = NamingConventions.tableNameToClassName(relatedTable)
        val methodName = "to${className}ListWith$relatedClassName"
        val relationshipPropertyName = NamingConventions.tableNameToPluralPropertyName(relatedTable)
        
        appendLine("/**")
        appendLine(" * Parse $className list with $relatedTable many-to-many relationship populated")
        appendLine(" * Expects ResultSet from a JOIN query through ${relationship.junctionTable}")
        appendLine(" */")
        appendLine("private fun ResultSet.${methodName}(): List<$className> {")
        appendLine("    val entityMap = mutableMapOf<String, $className>()")
        appendLine("    val relatedMap = mutableMapOf<String, MutableList<$relatedClassName>>()")
        appendLine("    ")
        appendLine("    while (next()) {")
        
        // Handle primary key for main entity
        if (table.primaryKeyColumns.size == 1) {
            val pkColumn = table.primaryKeyColumns.first()
            appendLine("        val entityKey = getString(\"${pkColumn.columnName}\")")
        } else {
            val keyParts = table.primaryKeyColumns.joinToString(" + \"-\" + ") { "getString(\"${it.columnName}\")" }
            appendLine("        val entityKey = $keyParts")
        }
        
        appendLine("        ")
        appendLine("        // Parse main entity if not already processed")
        appendLine("        if (!entityMap.containsKey(entityKey)) {")
        appendLine("            val entity = to$className()")
        appendLine("            entityMap[entityKey] = entity")
        appendLine("            relatedMap[entityKey] = mutableListOf()")
        appendLine("        }")
        appendLine("        ")
        appendLine("        // Parse related entity if present")
        appendLine("        try {")
        appendLine("            val relatedEntity = extract${relatedClassName}FromResultSet()")
        appendLine("            relatedMap[entityKey]?.add(relatedEntity)")
        appendLine("        } catch (e: Exception) {")
        appendLine("            // Related data might be null or missing, skip")
        appendLine("        }")
        appendLine("    }")
        appendLine("    ")
        appendLine("    // Create final entities with populated relationships")
        appendLine("    return entityMap.map { (key, entity) ->")
        appendLine("        val relatedEntities = relatedMap[key] ?: emptyList()")
        appendLine("        entity.copy($relationshipPropertyName = relatedEntities)")
        appendLine("    }")
        appendLine("}")
        appendLine()
    }
    
    /**
     * Generate simple list parsing for when no relationships are available
     */
    private fun StringBuilder.generateSimpleListParsing(className: String) {
        appendLine("/**")
        appendLine(" * Parse all rows from ResultSet into a list of $className instances")
        appendLine(" * @param closeAfter whether to close the ResultSet after parsing (default: true)")
        appendLine(" */")
        appendLine("fun ResultSet.to${className}List(closeAfter: Boolean = true): List<$className> {")
        appendLine("    val result = mutableListOf<$className>()")
        appendLine("    try {")
        appendLine("        while (next()) {")
        appendLine("            result.add(to$className())")
        appendLine("        }")
        appendLine("    } finally {")
        appendLine("        if (closeAfter) {")
        appendLine("            close()")
        appendLine("        }")
        appendLine("    }")
        appendLine("    return result")
        appendLine("}")
        appendLine()
    }
    
    /**
     * Generate table detection method
     */
    private fun StringBuilder.generateTableDetectionMethod(
        table: SQLTableInfo,
        relationships: SchemaRelationships,
        allTables: List<SQLTableInfo>?
    ) {
        appendLine("/**")
        appendLine(" * Detect which tables are present in the ResultSet based on column names and metadata")
        appendLine(" */")
        appendLine("private fun ResultSet.detectJoinedTables(): Set<String> {")
        appendLine("    val tables = mutableSetOf<String>()")
        appendLine("    val metaData = this.metaData")
        appendLine("    ")
        appendLine("    for (i in 1..metaData.columnCount) {")
        appendLine("        val columnName = metaData.getColumnName(i)")
        appendLine("        val tableName = try { metaData.getTableName(i) } catch (e: SQLException) { \"\" }")
        appendLine("        ")
        appendLine("        // Add table if explicitly specified in metadata")
        appendLine("        if (tableName.isNotBlank()) {")
        appendLine("            tables.add(tableName)")
        appendLine("        }")
        appendLine("        ")
        appendLine("        // Detect by table alias patterns (table_name.column_name)")
        appendLine("        if (columnName.contains(\".\")) {")
        appendLine("            val tableAlias = columnName.substringBefore(\".\")")
        
        // Generate validation against known tables
        val knownTables = mutableSetOf<String>()
        knownTables.add(table.tableName)
        
        // Add tables from relationships
        relationships.getOutgoingRelationships(table.tableName).forEach { rel ->
            knownTables.add(rel.toTable)
        }
        relationships.getIncomingRelationships(table.tableName).forEach { rel ->
            knownTables.add(rel.fromTable)
        }
        
        // Add all tables if available
        allTables?.forEach { knownTables.add(it.tableName) }
        
        if (knownTables.isNotEmpty()) {
            appendLine("            // Only add table if it's a known table from the schema")
            appendLine("            val knownTables = setOf(${knownTables.joinToString(", ") { "\"$it\"" }})")
            appendLine("            if (knownTables.contains(tableAlias)) {")
            appendLine("                tables.add(tableAlias)")
            appendLine("            }")
        } else {
            appendLine("            tables.add(tableAlias)")
        }
        
        appendLine("        }")
        appendLine("    }")
        appendLine("    ")
        appendLine("    return tables")
        appendLine("}")
        appendLine()
    }
    
    /**
     * Generate entity extraction method for a related table
     */
    private fun StringBuilder.generateEntityExtractionMethod(
        tableName: String,
        className: String,
        relationships: SchemaRelationships,
        allTables: List<SQLTableInfo>?
    ) {
        appendLine("/**")
        appendLine(" * Extract $className from current ResultSet row")
        appendLine(" * Uses table prefixing to avoid column name conflicts in JOINs")
        appendLine(" * Supports multiple alias formats: table.column, table_column, tablealias_column")
        appendLine(" */")
        appendLine("private fun ResultSet.extract${className}FromResultSet(): $className {")
        
        // Find the table info from allTables if available
        val tableInfo = allTables?.find { it.tableName == tableName }
        
        if (tableInfo != null) {
            appendLine("    return $className(")
            tableInfo.columns.forEachIndexed { index, column ->
                val propertyName = NamingConventions.columnNameToPropertyName(column.columnName)
                val getter = generateSmartResultSetGetter(column, tableName)
                appendLine("        $propertyName = $getter")
                if (index < tableInfo.columns.size - 1) {
                    appendLine(",")
                } else {
                    appendLine()
                }
            }
            appendLine("    )")
        } else {
            appendLine("    // Table metadata not available - cannot extract entity")
            appendLine("    throw IllegalStateException(\"Cannot extract $className: table metadata not available during generation\")")
        }
        
        appendLine("}")
        appendLine()
    }
    
    /**
     * Generate a smart ResultSet getter that tries multiple alias formats
     * Common formats: table.column, table_column, alias_column, column
     */
    private fun generateSmartResultSetGetter(column: SQLColumnInfo, tableName: String): String {
        val columnName = column.columnName
        val kotlinType = TypeMapper.mapToKotlinType(column)
        val baseType = kotlinType.removeSuffix("?")
        val isNullable = column.isNullable && !column.isPrimaryKey
        
        // Generate list of column names to try in order of preference
        val aliasFormats = listOf(
            "${tableName}.${columnName}",    // table.column (SQL standard)
            "${tableName}_${columnName}",    // table_column (common alias format)
            "${tableName.take(1)}${columnName}",  // t_column (single letter alias)
            columnName                        // column (fallback)
        )
        
        val getterCall = when (baseType) {
            "String" -> if (isNullable) "getString" else "getString"
            "Int" -> "getInt"
            "Long" -> "getLong"
            "Short" -> "getShort"
            "Byte" -> "getByte"
            "Float" -> "getFloat"
            "Double" -> "getDouble"
            "Boolean" -> "getBoolean"
            "ByteArray" -> "getBytes"
            "kotlinx.datetime.Instant" -> "getTimestamp"
            "kotlinx.datetime.LocalDate" -> "getDate"
            "kotlinx.datetime.LocalTime" -> "getTime"
            "kotlin.uuid.Uuid" -> "getString"
            "kotlinx.serialization.json.JsonElement" -> "getString"
            "Vector" -> "getString"
            "FloatArray" -> "getString"
            else -> "getString"
        }
        
        return buildString {
            append("tryGetColumn(listOf(")
            append(aliasFormats.joinToString(", ") { "\"$it\"" })
            append(")) { columnName -> ")
            
            when (baseType) {
                "String" -> {
                    if (isNullable) {
                        append("$getterCall(columnName)")
                    } else {
                        append("$getterCall(columnName) ?: \"\"")
                    }
                }
                "Int", "Long", "Short", "Byte", "Float", "Double", "Boolean" -> {
                    if (isNullable) {
                        append("$getterCall(columnName).takeIf { !wasNull() }")
                    } else {
                        append("$getterCall(columnName)")
                    }
                }
                "ByteArray" -> {
                    if (isNullable) {
                        append("$getterCall(columnName)")
                    } else {
                        append("$getterCall(columnName) ?: byteArrayOf()")
                    }
                }
                "kotlinx.datetime.Instant" -> {
                    if (isNullable) {
                        append("$getterCall(columnName)?.toKotlinInstant()")
                    } else {
                        append("$getterCall(columnName).toKotlinInstant()")
                    }
                }
                "kotlinx.datetime.LocalDate" -> {
                    if (isNullable) {
                        append("$getterCall(columnName)?.toKotlinLocalDate()")
                    } else {
                        append("$getterCall(columnName).toKotlinLocalDate()")
                    }
                }
                "kotlinx.datetime.LocalTime" -> {
                    if (isNullable) {
                        append("$getterCall(columnName)?.toKotlinLocalTime()")
                    } else {
                        append("$getterCall(columnName).toKotlinLocalTime()")
                    }
                }
                "kotlin.uuid.Uuid" -> {
                    if (isNullable) {
                        append("$getterCall(columnName)?.let { kotlin.uuid.Uuid.parse(it) }")
                    } else {
                        append("kotlin.uuid.Uuid.parse($getterCall(columnName))")
                    }
                }
                "kotlinx.serialization.json.JsonElement" -> {
                    if (isNullable) {
                        append("$getterCall(columnName)?.let { kotlinx.serialization.json.Json.parseToJsonElement(it) }")
                    } else {
                        append("kotlinx.serialization.json.Json.parseToJsonElement($getterCall(columnName))")
                    }
                }
                "Vector" -> {
                    if (isNullable) {
                        append("$getterCall(columnName)?.let { parseVector(it) }")
                    } else {
                        append("parseVector($getterCall(columnName))")
                    }
                }
                "FloatArray" -> {
                    if (isNullable) {
                        append("$getterCall(columnName)?.let { parseFloatArray(it) }")
                    } else {
                        append("parseFloatArray($getterCall(columnName))")
                    }
                }
                else -> {
                    if (isNullable) {
                        append("$getterCall(columnName)")
                    } else {
                        append("$getterCall(columnName) ?: \"\"")
                    }
                }
            }
            append(" }")
        }
    }

    /**
     * Generate a simple ResultSet getter for aliased columns
     * Uses try-catch to handle missing columns gracefully
     */
    private fun generateResultSetGetterSimple(column: SQLColumnInfo, aliasedColumnName: String): String {
        val kotlinType = TypeMapper.mapToKotlinType(column)
        val baseType = kotlinType.removeSuffix("?")
        val columnName = column.columnName
        val isNullable = column.isNullable && !column.isPrimaryKey
        
        return "try { " + when (baseType) {
            "String" -> if (isNullable) {
                "getString(\"$aliasedColumnName\")"
            } else {
                "getString(\"$aliasedColumnName\") ?: \"\""
            }
            "Int" -> if (isNullable) {
                "getInt(\"$aliasedColumnName\").takeIf { !wasNull() }"
            } else {
                "getInt(\"$aliasedColumnName\")"
            }
            "Long" -> if (isNullable) {
                "getLong(\"$aliasedColumnName\").takeIf { !wasNull() }"
            } else {
                "getLong(\"$aliasedColumnName\")"
            }
            "Short" -> if (isNullable) {
                "getShort(\"$aliasedColumnName\").takeIf { !wasNull() }"
            } else {
                "getShort(\"$aliasedColumnName\")"
            }
            "Byte" -> if (isNullable) {
                "getByte(\"$aliasedColumnName\").takeIf { !wasNull() }"
            } else {
                "getByte(\"$aliasedColumnName\")"
            }
            "Float" -> if (isNullable) {
                "getFloat(\"$aliasedColumnName\").takeIf { !wasNull() }"
            } else {
                "getFloat(\"$aliasedColumnName\")"
            }
            "Double" -> if (isNullable) {
                "getDouble(\"$aliasedColumnName\").takeIf { !wasNull() }"
            } else {
                "getDouble(\"$aliasedColumnName\")"
            }
            "Boolean" -> if (isNullable) {
                "getBoolean(\"$aliasedColumnName\").takeIf { !wasNull() }"
            } else {
                "getBoolean(\"$aliasedColumnName\")"
            }
            "ByteArray" -> if (isNullable) {
                "getBytes(\"$aliasedColumnName\")"
            } else {
                "getBytes(\"$aliasedColumnName\") ?: byteArrayOf()"
            }
            "kotlinx.datetime.Instant" -> if (isNullable) {
                "getTimestamp(\"$aliasedColumnName\")?.toKotlinInstant()"
            } else {
                "getTimestamp(\"$aliasedColumnName\").toKotlinInstant()"
            }
            "kotlinx.datetime.LocalDate" -> if (isNullable) {
                "getDate(\"$aliasedColumnName\")?.toKotlinLocalDate()"
            } else {
                "getDate(\"$aliasedColumnName\").toKotlinLocalDate()"
            }
            "kotlinx.datetime.LocalTime" -> if (isNullable) {
                "getTime(\"$aliasedColumnName\")?.toKotlinLocalTime()"
            } else {
                "getTime(\"$aliasedColumnName\").toKotlinLocalTime()"
            }
            "kotlin.uuid.Uuid" -> if (isNullable) {
                "getString(\"$aliasedColumnName\")?.let { kotlin.uuid.Uuid.parse(it) }"
            } else {
                "kotlin.uuid.Uuid.parse(getString(\"$aliasedColumnName\"))"
            }
            "kotlinx.serialization.json.JsonElement" -> if (isNullable) {
                "getString(\"$aliasedColumnName\")?.let { kotlinx.serialization.json.Json.parseToJsonElement(it) }"
            } else {
                "kotlinx.serialization.json.Json.parseToJsonElement(getString(\"$aliasedColumnName\"))"
            }
            "Vector" -> if (isNullable) {
                "getString(\"$aliasedColumnName\")?.let { parseVector(it) }"
            } else {
                "parseVector(getString(\"$aliasedColumnName\"))"
            }
            "FloatArray" -> if (isNullable) {
                "getString(\"$aliasedColumnName\")?.let { parseFloatArray(it) }"
            } else {
                "parseFloatArray(getString(\"$aliasedColumnName\"))"
            }
            else -> if (isNullable) {
                "getString(\"$aliasedColumnName\")"
            } else {
                "getString(\"$aliasedColumnName\") ?: \"\""
            }
        } + " } catch (e: SQLException) { " + when (baseType) {
            "String" -> if (isNullable) {
                "getString(\"$columnName\")"
            } else {
                "getString(\"$columnName\") ?: \"\""
            }
            "Int" -> if (isNullable) {
                "getInt(\"$columnName\").takeIf { !wasNull() }"
            } else {
                "getInt(\"$columnName\")"
            }
            "Long" -> if (isNullable) {
                "getLong(\"$columnName\").takeIf { !wasNull() }"
            } else {
                "getLong(\"$columnName\")"
            }
            "Short" -> if (isNullable) {
                "getShort(\"$columnName\").takeIf { !wasNull() }"
            } else {
                "getShort(\"$columnName\")"
            }
            "Byte" -> if (isNullable) {
                "getByte(\"$columnName\").takeIf { !wasNull() }"
            } else {
                "getByte(\"$columnName\")"
            }
            "Float" -> if (isNullable) {
                "getFloat(\"$columnName\").takeIf { !wasNull() }"
            } else {
                "getFloat(\"$columnName\")"
            }
            "Double" -> if (isNullable) {
                "getDouble(\"$columnName\").takeIf { !wasNull() }"
            } else {
                "getDouble(\"$columnName\")"
            }
            "Boolean" -> if (isNullable) {
                "getBoolean(\"$columnName\").takeIf { !wasNull() }"
            } else {
                "getBoolean(\"$columnName\")"
            }
            "ByteArray" -> if (isNullable) {
                "getBytes(\"$columnName\")"
            } else {
                "getBytes(\"$columnName\") ?: byteArrayOf()"
            }
            "kotlinx.datetime.Instant" -> if (isNullable) {
                "getTimestamp(\"$columnName\")?.toKotlinInstant()"
            } else {
                "getTimestamp(\"$columnName\").toKotlinInstant()"
            }
            "kotlinx.datetime.LocalDate" -> if (isNullable) {
                "getDate(\"$columnName\")?.toKotlinLocalDate()"
            } else {
                "getDate(\"$columnName\").toKotlinLocalDate()"
            }
            "kotlinx.datetime.LocalTime" -> if (isNullable) {
                "getTime(\"$columnName\")?.toKotlinLocalTime()"
            } else {
                "getTime(\"$columnName\").toKotlinLocalTime()"
            }
            "kotlin.uuid.Uuid" -> if (isNullable) {
                "getString(\"$columnName\")?.let { kotlin.uuid.Uuid.parse(it) }"
            } else {
                "kotlin.uuid.Uuid.parse(getString(\"$columnName\"))"
            }
            "kotlinx.serialization.json.JsonElement" -> if (isNullable) {
                "getString(\"$columnName\")?.let { kotlinx.serialization.json.Json.parseToJsonElement(it) }"
            } else {
                "kotlinx.serialization.json.Json.parseToJsonElement(getString(\"$columnName\"))"
            }
            "Vector" -> if (isNullable) {
                "getString(\"$columnName\")?.let { parseVector(it) }"
            } else {
                "parseVector(getString(\"$columnName\"))"
            }
            "FloatArray" -> if (isNullable) {
                "getString(\"$columnName\")?.let { parseFloatArray(it) }"
            } else {
                "parseFloatArray(getString(\"$columnName\"))"
            }
            else -> if (isNullable) {
                "getString(\"$columnName\")"
            } else {
                "getString(\"$columnName\") ?: \"\""
            }
        } + " }"
    }
    
    /**
     * Generate composite key parsing extensions
     */
    fun generateKeyResultSetExtensions(
        table: SQLTableInfo,
        keyClassName: String
    ): String {
        if (!table.hasCompositePrimaryKey) return ""
        
        return buildString {
            appendLine("/**")
            appendLine(" * ResultSet parsing extensions for ${table.tableName} composite key")
            appendLine(" */")
            appendLine()
            
            appendLine("/**")
            appendLine(" * Parse current ResultSet row into a $keyClassName instance")
            appendLine(" */")
            appendLine("fun ResultSet.to$keyClassName(): $keyClassName {")
            appendLine("    return $keyClassName(")
            
            table.primaryKeyColumns.forEachIndexed { index, column ->
                val propertyName = NamingConventions.columnNameToPropertyName(column.columnName)
                val getter = generateResultSetGetter(column)
                appendLine("        $propertyName = $getter")
                if (index < table.primaryKeyColumns.size - 1) {
                    appendLine(",")
                } else {
                    appendLine()
                }
            }
            
            appendLine("    )")
            appendLine("}")
            appendLine()
            
            appendLine("/**")
            appendLine(" * Parse all rows from ResultSet into a list of $keyClassName instances")
            appendLine(" */")
            appendLine("fun ResultSet.to${keyClassName}List(closeAfter: Boolean = true): List<$keyClassName> {")
            appendLine("    val result = mutableListOf<$keyClassName>()")
            appendLine("    try {")
            appendLine("        while (next()) {")
            appendLine("            result.add(to$keyClassName())")
            appendLine("        }")
            appendLine("    } finally {")
            appendLine("        if (closeAfter) {")
            appendLine("            close()")
            appendLine("        }")
            appendLine("    }")
            appendLine("    return result")
            appendLine("}")
            appendLine()
        }
    }
    
    /**
     * Generate standalone companion helper functions for database operations  
     * These are generated as standalone functions, not inside a companion object
     */
    fun generateResultSetCompanion(
        table: SQLTableInfo,
        className: String
    ): String {
        return buildString {
            appendLine("/**")
            appendLine(" * Database helper functions for ${table.tableName} table")
            appendLine(" */")
            appendLine()
            
            appendLine("/**")
            appendLine(" * Column names for ${table.tableName} table in SELECT order")
            appendLine(" */")
            appendLine("val COLUMN_NAMES = listOf(")
            table.columns.forEachIndexed { index, column ->
                append("    \"${column.columnName}\"")
                if (index < table.columns.size - 1) {
                    appendLine(",")
                } else {
                    appendLine()
                }
            }
            appendLine(")")
            appendLine()
            
            appendLine("/**")
            appendLine(" * Generate SELECT statement with all columns")
            appendLine(" */")
            appendLine("fun selectAllQuery(): String = \"SELECT \${COLUMN_NAMES.joinToString(\", \")} FROM ${table.tableName}\"")
            appendLine()
            
            appendLine("/**")
            appendLine(" * Generate SELECT statement with WHERE clause for primary key")
            appendLine(" */")
            if (table.primaryKeyColumns.size == 1) {
                val pkColumn = table.primaryKeyColumns.first()
                appendLine("fun selectByIdQuery(): String = \"\${selectAllQuery()} WHERE ${pkColumn.columnName} = ?\"")
            } else if (table.hasCompositePrimaryKey) {
                val whereClause = table.primaryKeyColumns.joinToString(" AND ") { "${it.columnName} = ?" }
                appendLine("fun selectByKeyQuery(): String = \"\${selectAllQuery()} WHERE $whereClause\"")
            }
            appendLine()
            
            appendLine("/**")
            appendLine(" * Parse a single row from a PreparedStatement execution")
            appendLine(" */")
            appendLine("fun fromQuery(statement: PreparedStatement): $className? {")
            appendLine("    return statement.executeQuery().use { rs ->")
            appendLine("        if (rs.next()) rs.to$className() else null")
            appendLine("    }")
            appendLine("}")
            appendLine()
            
            appendLine("/**")
            appendLine(" * Parse multiple rows from a PreparedStatement execution")
            appendLine(" */")
            appendLine("fun listFromQuery(statement: PreparedStatement): List<$className> {")
            appendLine("    return statement.executeQuery().use { rs ->")
            appendLine("        rs.to${className}List(closeAfter = false)")
            appendLine("    }")
            appendLine("}")
            appendLine()
        }
    }
    
    /**
     * Generate ResultSet getter for a column by name
     */
    private fun generateResultSetGetter(column: SQLColumnInfo): String {
        val kotlinType = TypeMapper.mapToKotlinType(column)
        val baseType = kotlinType.removeSuffix("?")
        val columnName = column.columnName
        val isNullable = column.isNullable && !column.isPrimaryKey
        
        return when (baseType) {
            "String" -> if (isNullable) "getString(\"$columnName\")" else "getString(\"$columnName\") ?: \"\""
            "Int" -> if (isNullable) "getInt(\"$columnName\").takeIf { !wasNull() }" else "getInt(\"$columnName\")"
            "Long" -> if (isNullable) "getLong(\"$columnName\").takeIf { !wasNull() }" else "getLong(\"$columnName\")"
            "Short" -> if (isNullable) "getShort(\"$columnName\").takeIf { !wasNull() }" else "getShort(\"$columnName\")"
            "Byte" -> if (isNullable) "getByte(\"$columnName\").takeIf { !wasNull() }" else "getByte(\"$columnName\")"
            "Float" -> if (isNullable) "getFloat(\"$columnName\").takeIf { !wasNull() }" else "getFloat(\"$columnName\")"
            "Double" -> if (isNullable) "getDouble(\"$columnName\").takeIf { !wasNull() }" else "getDouble(\"$columnName\")"
            "Boolean" -> if (isNullable) "getBoolean(\"$columnName\").takeIf { !wasNull() }" else "getBoolean(\"$columnName\")"
            "ByteArray" -> if (isNullable) "getBytes(\"$columnName\")" else "getBytes(\"$columnName\") ?: byteArrayOf()"
            "kotlinx.datetime.Instant" -> if (isNullable) "getTimestamp(\"$columnName\")?.toKotlinInstant()" else "getTimestamp(\"$columnName\").toKotlinInstant()"
            "kotlinx.datetime.LocalDate" -> if (isNullable) "getDate(\"$columnName\")?.toKotlinLocalDate()" else "getDate(\"$columnName\").toKotlinLocalDate()"
            "kotlinx.datetime.LocalTime" -> if (isNullable) "getTime(\"$columnName\")?.toKotlinLocalTime()" else "getTime(\"$columnName\").toKotlinLocalTime()"
            "kotlin.uuid.Uuid" -> if (isNullable) "getString(\"$columnName\")?.let { kotlin.uuid.Uuid.parse(it) }" else "kotlin.uuid.Uuid.parse(getString(\"$columnName\"))"
            "kotlinx.serialization.json.JsonElement" -> if (isNullable) "getString(\"$columnName\")?.let { kotlinx.serialization.json.Json.parseToJsonElement(it) }" else "kotlinx.serialization.json.Json.parseToJsonElement(getString(\"$columnName\"))"
            "Vector" -> if (isNullable) "getString(\"$columnName\")?.let { parseVector(it) }" else "parseVector(getString(\"$columnName\"))"
            "FloatArray" -> if (isNullable) "getString(\"$columnName\")?.let { parseFloatArray(it) }" else "parseFloatArray(getString(\"$columnName\"))"
            else -> if (isNullable) "getString(\"$columnName\")" else "getString(\"$columnName\") ?: \"\""
        }
    }
    
    /**
     * Generate ResultSet getter for a column by index
     */
    private fun generateResultSetGetterByIndex(column: SQLColumnInfo, index: Int): String {
        val kotlinType = TypeMapper.mapToKotlinType(column)
        val baseType = kotlinType.removeSuffix("?")
        val isNullable = column.isNullable && !column.isPrimaryKey
        
        return when (baseType) {
            "String" -> if (isNullable) "getString($index)" else "getString($index) ?: \"\""
            "Int" -> if (isNullable) "getInt($index).takeIf { !wasNull() }" else "getInt($index)"
            "Long" -> if (isNullable) "getLong($index).takeIf { !wasNull() }" else "getLong($index)"
            "Short" -> if (isNullable) "getShort($index).takeIf { !wasNull() }" else "getShort($index)"
            "Byte" -> if (isNullable) "getByte($index).takeIf { !wasNull() }" else "getByte($index)"
            "Float" -> if (isNullable) "getFloat($index).takeIf { !wasNull() }" else "getFloat($index)"
            "Double" -> if (isNullable) "getDouble($index).takeIf { !wasNull() }" else "getDouble($index)"
            "Boolean" -> if (isNullable) "getBoolean($index).takeIf { !wasNull() }" else "getBoolean($index)"
            "ByteArray" -> if (isNullable) "getBytes($index)" else "getBytes($index) ?: byteArrayOf()"
            "kotlinx.datetime.Instant" -> if (isNullable) "getTimestamp($index)?.toKotlinInstant()" else "getTimestamp($index).toKotlinInstant()"
            "kotlinx.datetime.LocalDate" -> if (isNullable) "getDate($index)?.toKotlinLocalDate()" else "getDate($index).toKotlinLocalDate()"
            "kotlinx.datetime.LocalTime" -> if (isNullable) "getTime($index)?.toKotlinLocalTime()" else "getTime($index).toKotlinLocalTime()"
            "kotlin.uuid.Uuid" -> if (isNullable) "getString($index)?.let { kotlin.uuid.Uuid.parse(it) }" else "kotlin.uuid.Uuid.parse(getString($index))"
            "kotlinx.serialization.json.JsonElement" -> if (isNullable) "getString($index)?.let { kotlinx.serialization.json.Json.parseToJsonElement(it) }" else "kotlinx.serialization.json.Json.parseToJsonElement(getString($index))"
            "Vector" -> if (isNullable) "getString($index)?.let { parseVector(it) }" else "parseVector(getString($index))"
            "FloatArray" -> if (isNullable) "getString($index)?.let { parseFloatArray(it) }" else "parseFloatArray(getString($index))"
            else -> if (isNullable) "getString($index)" else "getString($index) ?: \"\""
        }
    }
    
    /**
     * Generate utility functions needed for type conversion
     */
    fun generateResultSetUtilities(): String {
        return buildString {
            appendLine("/**")
            appendLine(" * Utility functions for ResultSet type conversion")
            appendLine(" */")
            appendLine()
            
            // Timestamp to Instant conversion (java.sql.Timestamp -> kotlinx.datetime.Instant)
            appendLine("private fun java.sql.Timestamp.toKotlinInstant(): kotlinx.datetime.Instant =")
            appendLine("    kotlinx.datetime.Instant.fromEpochMilliseconds(this.time)")
            appendLine()
            
            // Date to LocalDate conversion (java.sql.Date -> kotlinx.datetime.LocalDate)
            appendLine("private fun java.sql.Date.toKotlinLocalDate(): kotlinx.datetime.LocalDate {")
            appendLine("    val javaLocalDate = this.toLocalDate()")
            appendLine("    return kotlinx.datetime.LocalDate(")
            appendLine("        year = javaLocalDate.year,")
            appendLine("        monthNumber = javaLocalDate.monthValue,")
            appendLine("        dayOfMonth = javaLocalDate.dayOfMonth")
            appendLine("    )")
            appendLine("}")
            appendLine()
            
            // Time to LocalTime conversion (java.sql.Time -> kotlinx.datetime.LocalTime)
            appendLine("private fun java.sql.Time.toKotlinLocalTime(): kotlinx.datetime.LocalTime {")
            appendLine("    val javaLocalTime = this.toLocalTime()")
            appendLine("    return kotlinx.datetime.LocalTime(")
            appendLine("        hour = javaLocalTime.hour,")
            appendLine("        minute = javaLocalTime.minute,")
            appendLine("        second = javaLocalTime.second,")
            appendLine("        nanosecond = javaLocalTime.nano")
            appendLine("    )")
            appendLine("}")
            appendLine()
            
            // FloatArray parsing for vector types
            appendLine("private fun parseFloatArray(vectorString: String): FloatArray {")
            appendLine("    return vectorString")
            appendLine("        .removePrefix(\"[\").removeSuffix(\"]\")")
            appendLine("        .split(\",\")")
            appendLine("        .map { it.trim().toFloat() }")
            appendLine("        .toFloatArray()")
            appendLine("}")
            appendLine()
            
            // Vector parsing for Vector wrapper types
            appendLine("private fun parseVector(vectorString: String): Vector {")
            appendLine("    val floats = vectorString")
            appendLine("        .removePrefix(\"[\").removeSuffix(\"]\")")
            appendLine("        .split(\",\")")
            appendLine("        .map { it.trim().toFloat() }")
            appendLine("    return Vector.of(floats)")
            appendLine("}")
            appendLine()
            
            // Smart column getter that tries multiple alias formats
            appendLine("private fun <T> ResultSet.tryGetColumn(columnNames: List<String>, getter: (String) -> T): T {")
            appendLine("    for (columnName in columnNames) {")
            appendLine("        try {")
            appendLine("            return getter(columnName)")
            appendLine("        } catch (e: SQLException) {")
            appendLine("            // Column not found, try next alias")
            appendLine("            continue")
            appendLine("        }")
            appendLine("    }")
            appendLine("    throw SQLException(\"None of the column aliases found: \${columnNames.joinToString(\", \")}\")")
            appendLine("}")
            appendLine()
        }
    }
    

    
    // Dynamic extraction removed - not needed since we generate specific extraction methods
    
    /**
     * Get required imports for ResultSet parsing
     */
    fun getRequiredImports(table: SQLTableInfo): Set<String> {
        val imports = mutableSetOf(
            "java.sql.ResultSet",
            "java.sql.PreparedStatement",
            "java.sql.SQLException",
            "java.sql.Timestamp",
            "java.sql.Date", 
            "java.sql.Time"
        )
        
        // Always add Vector import since parseVector utility function is always generated
        imports.add("dev.gabrielolv.kotsql.vector.Vector")
        
        return imports
    }
} 