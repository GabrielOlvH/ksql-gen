package dev.gabrielolv.kotsql.generator

import dev.gabrielolv.kotsql.mapping.TypeMapper
import dev.gabrielolv.kotsql.model.RelationshipInfo
import dev.gabrielolv.kotsql.model.SQLTableInfo
import dev.gabrielolv.kotsql.model.SchemaRelationships
import dev.gabrielolv.kotsql.util.NamingConventions

/**
 * Handles generation of table metadata classes
 */
object TableMetadataGenerator {
    
    /**
     * Generate table metadata class
     */
    fun generateTableMetadataClass(
        table: SQLTableInfo,
        tableClassName: String,
        relationships: SchemaRelationships?
    ): String {
        return buildString {
            appendLine("/**")
            appendLine(" * Table metadata and column definitions for ${table.tableName}")
            appendLine(" */")
            appendLine("object $tableClassName {")
            appendLine("    const val TABLE_NAME = \"${table.tableName}\"")
            appendLine()
            
            // Primary key metadata for composite keys
            if (table.hasCompositePrimaryKey) {
                val className = NamingConventions.tableNameToClassName(table.tableName)
                appendLine("    /**")
                appendLine("     * Composite primary key columns")
                appendLine("     */")
                appendLine("    val PRIMARY_KEY_COLUMNS = listOf(${table.compositePrimaryKey.joinToString(", ") { "\"$it\"" }})")
                appendLine("    val primaryKey = CompositeKey<${className}Key>(PRIMARY_KEY_COLUMNS)")
                appendLine()
            }
            
            // Column definitions
            appendLine("    object Columns {")
            table.columns.forEach { column ->
                val propertyName = NamingConventions.columnNameToPropertyName(column.columnName)
                val constantName = propertyName.uppercase()
                val kotlinType = TypeMapper.mapToKotlinType(column).removeSuffix("?") // Remove nullable marker for type parameter
                appendLine("        val $constantName = Column<$kotlinType>(\"${column.columnName}\")")
            }
            appendLine("    }")
            appendLine()
            
            // Prefixed column definitions for JOIN queries
            appendLine("    object PrefixedColumns {")
            table.columns.forEach { column ->
                val propertyName = NamingConventions.columnNameToPropertyName(column.columnName)
                val constantName = propertyName.uppercase()
                val kotlinType = TypeMapper.mapToKotlinType(column).removeSuffix("?") // Remove nullable marker for type parameter
                appendLine("        val $constantName = Column<$kotlinType>(\"${table.tableName}.${column.columnName}\")")
            }
            appendLine("    }")
            appendLine()
            
            // Column shortcuts - TableName.ColumnName as shortcuts to TableName.Columns.COLUMN_NAME
            appendLine("    // Column shortcuts for convenient access")
            table.columns.forEach { column ->
                val propertyName = NamingConventions.columnNameToPropertyName(column.columnName)
                val constantName = propertyName.uppercase()
                // Convert to Pascal case: first_name -> firstName -> FirstName
                val shortcutName = propertyName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                appendLine("    val $shortcutName get() = Columns.$constantName")
            }
            appendLine()
            
            // Prefixed column shortcuts for JOIN queries
            appendLine("    // Prefixed column shortcuts for JOIN queries")
            table.columns.forEach { column ->
                val propertyName = NamingConventions.columnNameToPropertyName(column.columnName)
                val constantName = propertyName.uppercase()
                // Convert to Pascal case: first_name -> firstName -> FirstName, but with prefix
                val shortcutName = "Prefixed" + propertyName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                appendLine("    val $shortcutName get() = PrefixedColumns.$constantName")
            }
            
            // Relationships if present
            if (relationships != null) {
                val tableRelationships = relationships.relationships.filter { 
                    it.fromTable == table.tableName || it.toTable == table.tableName 
                }
                
                if (tableRelationships.isNotEmpty()) {
                    appendLine()
                    appendLine("    object Relationships {")
                    tableRelationships.forEach { relationship ->
                        val fromTableName = NamingConventions.tableNameToPropertyName(relationship.fromTable)
                        val toTableName = NamingConventions.tableNameToClassName(relationship.toTable)
                        val relationshipName = "${fromTableName}To${toTableName}"
                        val relationshipType = when (relationship) {
                            is RelationshipInfo.OneToOne -> "OneToOne"
                            is RelationshipInfo.OneToMany -> "OneToMany"
                            is RelationshipInfo.ManyToOne -> "ManyToOne"
                            is RelationshipInfo.ManyToMany -> "ManyToMany"
                        }
                        
                        appendLine("        val $relationshipName = RelationshipInfo.$relationshipType(")
                        appendLine("            fromTable = \"${relationship.fromTable}\",")
                        appendLine("            fromColumn = \"${relationship.fromColumn}\",")
                        appendLine("            toTable = \"${relationship.toTable}\",")
                        appendLine("            toColumn = \"${relationship.toColumn}\"")
                        
                        // Add type-specific parameters
                        when (relationship) {
                            is RelationshipInfo.OneToOne -> {
                                appendLine(",")
                                appendLine("            isOptional = ${relationship.isOptional}")
                            }
                            is RelationshipInfo.ManyToOne -> {
                                appendLine(",")
                                appendLine("            isOptional = ${relationship.isOptional}")
                            }
                            is RelationshipInfo.ManyToMany -> {
                                appendLine(",")
                                appendLine("            junctionTable = \"${relationship.junctionTable}\",")
                                appendLine("            junctionFromColumn = \"${relationship.junctionFromColumn}\",")
                                appendLine("            junctionToColumn = \"${relationship.junctionToColumn}\"")
                            }
                            is RelationshipInfo.OneToMany -> {
                                // OneToMany doesn't have additional parameters
                            }
                        }
                        
                        appendLine("        )")
                    }
                    appendLine("    }")
                }
            }
            
            // Add join query helper if relationships exist
            if (relationships != null) {
                val tableRelationships = relationships.relationships.filter { 
                    it.fromTable == table.tableName || it.toTable == table.tableName 
                }
                
                if (tableRelationships.isNotEmpty()) {
                    val className = NamingConventions.tableNameToClassName(table.tableName)
                    appendLine()
                    appendLine("    /**")
                    appendLine("     * Create a join query starting from this table")
                    appendLine("     */")
                    appendLine("    fun joinQuery() = JoinQuery.from<$className>(TABLE_NAME)")
                }
            }
            
            // Add CRUD operation helpers
            val className = NamingConventions.tableNameToClassName(table.tableName)
            appendLine()
            appendLine("    /**")
            appendLine("     * Create an INSERT query for this table")
            appendLine("     */")
            appendLine("    fun insert(entity: $className): QueryResult {")
            appendLine("        val columns = listOf(")
            table.columns.forEach { column ->
                appendLine("            \"${column.columnName}\",")
            }
            appendLine("        )")
            appendLine("        val values = listOf(")
            table.columns.forEach { column ->
                val propertyName = NamingConventions.columnNameToPropertyName(column.columnName)
                appendLine("            entity.$propertyName,")
            }
            appendLine("        )")
            appendLine("        val placeholders = columns.joinToString(\", \") { \"?\" }")
            appendLine("        val sql = \"INSERT INTO \$TABLE_NAME (\${columns.joinToString(\", \")}) VALUES (\$placeholders)\"")
            appendLine("        return QueryResult(sql, values)")
            appendLine("    }")
            appendLine()
            
            appendLine("    /**")
            appendLine("     * Create an UPDATE query for this table")
            appendLine("     */")
            if (table.primaryKeyColumns.size == 1) {
                val pkColumn = table.primaryKeyColumns.first()
                val pkProperty = NamingConventions.columnNameToPropertyName(pkColumn.columnName)
                appendLine("    fun update(entity: $className): QueryResult {")
                appendLine("        val setClauses = mutableListOf<String>()")
                appendLine("        val parameters = mutableListOf<Any?>()")
                table.columns.filter { !it.isPrimaryKey }.forEach { column ->
                    val propertyName = NamingConventions.columnNameToPropertyName(column.columnName)
                    appendLine("        setClauses.add(\"${column.columnName} = ?\")")
                    appendLine("        parameters.add(entity.$propertyName)")
                }
                appendLine("        parameters.add(entity.$pkProperty)")
                appendLine("        val sql = \"UPDATE \$TABLE_NAME SET \${setClauses.joinToString(\", \")} WHERE ${pkColumn.columnName} = ?\"")
                appendLine("        return QueryResult(sql, parameters)")
                appendLine("    }")
            } else if (table.hasCompositePrimaryKey) {
                appendLine("    fun update(entity: $className): QueryResult {")
                appendLine("        val setClauses = mutableListOf<String>()")
                appendLine("        val parameters = mutableListOf<Any?>()")
                table.columns.filter { !it.isPrimaryKey }.forEach { column ->
                    val propertyName = NamingConventions.columnNameToPropertyName(column.columnName)
                    appendLine("        setClauses.add(\"${column.columnName} = ?\")")
                    appendLine("        parameters.add(entity.$propertyName)")
                }
                table.primaryKeyColumns.forEach { column ->
                    val propertyName = NamingConventions.columnNameToPropertyName(column.columnName)
                    appendLine("        parameters.add(entity.$propertyName)")
                }
                val whereClause = table.primaryKeyColumns.joinToString(" AND ") { "${it.columnName} = ?" }
                appendLine("        val sql = \"UPDATE \$TABLE_NAME SET \${setClauses.joinToString(\", \")} WHERE $whereClause\"")
                appendLine("        return QueryResult(sql, parameters)")
                appendLine("    }")
            }
            appendLine()
            
            appendLine("    /**")
            appendLine("     * Create a DELETE query for this table")
            appendLine("     */")
            if (table.primaryKeyColumns.size == 1) {
                val pkColumn = table.primaryKeyColumns.first()
                val pkType = TypeMapper.mapToKotlinType(pkColumn).removeSuffix("?")
                appendLine("    fun deleteById(id: $pkType): QueryResult {")
                appendLine("        val sql = \"DELETE FROM \$TABLE_NAME WHERE ${pkColumn.columnName} = ?\"")
                appendLine("        return QueryResult(sql, listOf(id))")
                appendLine("    }")
                appendLine()
                appendLine("    /**")
                appendLine("     * Create a DELETE query using entity")
                appendLine("     */")
                appendLine("    fun delete(entity: $className): QueryResult {")
                val pkProperty = NamingConventions.columnNameToPropertyName(pkColumn.columnName)
                appendLine("        return deleteById(entity.$pkProperty)")
                appendLine("    }")
            } else if (table.hasCompositePrimaryKey) {
                appendLine("    fun deleteByKey(key: ${className}Key): QueryResult {")
                val whereClause = table.primaryKeyColumns.joinToString(" AND ") { "${it.columnName} = ?" }
                appendLine("        val sql = \"DELETE FROM \$TABLE_NAME WHERE $whereClause\"")
                appendLine("        return QueryResult(sql, key.toParameterList())")
                appendLine("    }")
                appendLine()
                appendLine("    /**")
                appendLine("     * Create a DELETE query using entity")
                appendLine("     */")
                appendLine("    fun delete(entity: $className): QueryResult {")
                append("        val key = ${className}Key(")
                table.primaryKeyColumns.forEachIndexed { index, column ->
                    val propertyName = NamingConventions.columnNameToPropertyName(column.columnName)
                    append("entity.$propertyName")
                    if (index < table.primaryKeyColumns.size - 1) {
                        append(", ")
                    }
                }
                appendLine(")")
                appendLine("        return deleteByKey(key)")
                appendLine("    }")
            }
            appendLine()
            
            appendLine("    /**")
            appendLine("     * Create an UPSERT (INSERT OR REPLACE) query for this table")
            appendLine("     */")
            appendLine("    fun upsert(entity: $className): QueryResult {")
            appendLine("        val columns = listOf(")
            table.columns.forEach { column ->
                appendLine("            \"${column.columnName}\",")
            }
            appendLine("        )")
            appendLine("        val values = listOf(")
            table.columns.forEach { column ->
                val propertyName = NamingConventions.columnNameToPropertyName(column.columnName)
                appendLine("            entity.$propertyName,")
            }
            appendLine("        )")
            appendLine("        val placeholders = columns.joinToString(\", \") { \"?\" }")
            appendLine("        val sql = \"INSERT OR REPLACE INTO \$TABLE_NAME (\${columns.joinToString(\", \")}) VALUES (\$placeholders)\"")
            appendLine("        return QueryResult(sql, values)")
            appendLine("    }")
            
            // Add findByKey method for composite keys
            if (table.hasCompositePrimaryKey) {
                val className = NamingConventions.tableNameToClassName(table.tableName)
                appendLine()
                appendLine("    /**")
                appendLine("     * Find a record by composite key")
                appendLine("     */")
                appendLine("    fun findByKey(key: ${className}Key) = TypeSafeQuery.from<$className>(TABLE_NAME)")
                table.primaryKeyColumns.forEachIndexed { index, column ->
                    val propertyName = NamingConventions.columnNameToPropertyName(column.columnName)
                    val columnProperty = propertyName.uppercase()
                    if (index == 0) {
                        appendLine("        .where(Columns.$columnProperty, key.$propertyName)")
                    } else {
                        appendLine("        .and(Columns.$columnProperty, key.$propertyName)")
                    }
                }
            }
            
            appendLine("}")
        }
    }
} 