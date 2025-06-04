package dev.gabrielolv.kotsql.generator

import dev.gabrielolv.kotsql.mapping.TypeMapper
import dev.gabrielolv.kotsql.model.SQLTableInfo
import dev.gabrielolv.kotsql.model.SchemaRelationships
import dev.gabrielolv.kotsql.util.NamingConventions

/**
 * Handles generation of query helper classes
 */
object QueryGenerator {
    
    /**
     * Generate query helpers class
     */
    fun generateQueryHelpersClass(
        table: SQLTableInfo,
        queryClassName: String,
        relationships: SchemaRelationships?
    ): String {
        return buildString {
            appendLine("/**")
            appendLine(" * Type-safe query helpers for ${table.tableName} table")
            appendLine(" */")
            appendLine("object $queryClassName {")
            
            // Basic query methods
            val className = NamingConventions.tableNameToClassName(table.tableName)
            appendLine("    fun selectAll() = TypeSafeQuery.from<$className>(\"${table.tableName}\")")
            appendLine()
            
            // Primary key lookup methods
            if (table.primaryKeyColumns.size == 1) {
                val pkColumn = table.primaryKeyColumns.first()
                val pkType = TypeMapper.mapToKotlinType(pkColumn).removeSuffix("?") // Remove nullable marker for type parameter
                val columnProperty = NamingConventions.columnNameToPropertyName(pkColumn.columnName).uppercase()
                appendLine("    fun findById(id: $pkType) = selectAll().where(${className}Table.Columns.$columnProperty, id)")
            } else if (table.hasCompositePrimaryKey) {
                appendLine("    fun findByKey(key: ${className}Key) = selectAll()")
                table.primaryKeyColumns.forEachIndexed { index, column ->
                    val propertyName = NamingConventions.columnNameToPropertyName(column.columnName)
                    val columnProperty = propertyName.uppercase()
                    if (index == 0) {
                        appendLine("        .where(${className}Table.Columns.$columnProperty, key.$propertyName)")
                    } else {
                        appendLine("        .and(${className}Table.Columns.$columnProperty, key.$propertyName)")
                    }
                }
            }
            appendLine()
            
            // Bulk operations
            appendLine("    /**")
            appendLine("     * Create a query to find multiple records by IDs")
            appendLine("     */")
            if (table.primaryKeyColumns.size == 1) {
                val pkColumn = table.primaryKeyColumns.first()
                val pkType = TypeMapper.mapToKotlinType(pkColumn).removeSuffix("?")
                val columnProperty = NamingConventions.columnNameToPropertyName(pkColumn.columnName).uppercase()
                appendLine("    fun findByIds(ids: List<$pkType>) = selectAll().whereIn(${className}Table.Columns.$columnProperty, ids)")
                appendLine()
                appendLine("    /**")
                appendLine("     * Create a batch DELETE query for multiple IDs")
                appendLine("     */")
                appendLine("    fun deleteByIds(ids: List<$pkType>): QueryResult {")
                appendLine("        val placeholders = ids.joinToString(\", \") { \"?\" }")
                appendLine("        val sql = \"DELETE FROM ${table.tableName} WHERE ${pkColumn.columnName} IN (\$placeholders)\"")
                appendLine("        return QueryResult(sql, ids)")
                appendLine("    }")
            }
            appendLine()
            
            // Advanced query helpers
            appendLine("    /**")
            appendLine("     * Create a paginated query")
            appendLine("     */")
            appendLine("    fun paginate(page: Int, pageSize: Int) = selectAll()")
            appendLine("        .limit(pageSize)")
            appendLine("        .offset(page * pageSize)")
            appendLine()
            
            appendLine("    /**")
            appendLine("     * Create a COUNT query")
            appendLine("     */")
            appendLine("    fun count() = TypeSafeQuery.from<$className>(\"${table.tableName}\").count()")
            appendLine()
            
            // Aggregation methods for numeric columns
            val numericColumns = table.columns.filter { 
                val kotlinType = TypeMapper.mapToKotlinType(it).removePrefix("?").removeSuffix("?")
                kotlinType in listOf("Int", "Long", "Double", "Float", "BigDecimal")
            }
            if (numericColumns.isNotEmpty()) {
                appendLine("    /**")
                appendLine("     * Aggregation functions for numeric columns")
                appendLine("     */")
                appendLine("    object Aggregations {")
                numericColumns.forEach { column ->
                    val propertyName = NamingConventions.columnNameToPropertyName(column.columnName)
                    val methodName = propertyName.replaceFirstChar { it.uppercase() }
                    
                    appendLine("        fun sum${methodName}(): QueryResult {")
                    appendLine("            val sql = \"SELECT SUM(${column.columnName}) FROM ${table.tableName}\"")
                    appendLine("            return QueryResult(sql, emptyList())")
                    appendLine("        }")
                    appendLine("        fun avg${methodName}(): QueryResult {")
                    appendLine("            val sql = \"SELECT AVG(${column.columnName}) FROM ${table.tableName}\"")
                    appendLine("            return QueryResult(sql, emptyList())")
                    appendLine("        }")
                    appendLine("        fun min${methodName}(): QueryResult {")
                    appendLine("            val sql = \"SELECT MIN(${column.columnName}) FROM ${table.tableName}\"")
                    appendLine("            return QueryResult(sql, emptyList())")
                    appendLine("        }")
                    appendLine("        fun max${methodName}(): QueryResult {")
                    appendLine("            val sql = \"SELECT MAX(${column.columnName}) FROM ${table.tableName}\"")
                    appendLine("            return QueryResult(sql, emptyList())")
                    appendLine("        }")
                }
                appendLine("    }")
                appendLine()
            }
            
            // Sorting helpers for all columns
            appendLine("    /**")
            appendLine("     * Pre-built sorting options")
            appendLine("     */")
            appendLine("    object OrderBy {")
            table.columns.forEach { column ->
                val propertyName = NamingConventions.columnNameToPropertyName(column.columnName)
                val columnProperty = propertyName.uppercase()
                val methodName = "by${propertyName.replaceFirstChar { it.uppercase() }}"
                
                appendLine("        fun ${methodName}Asc() = selectAll().orderBy(${className}Table.Columns.$columnProperty, TypeSafeQuery.SortOrder.ASC)")
                appendLine("        fun ${methodName}Desc() = selectAll().orderBy(${className}Table.Columns.$columnProperty, TypeSafeQuery.SortOrder.DESC)")
            }
            appendLine("    }")
            appendLine()
            
            // Add column-specific search methods for common patterns
            val stringColumns = table.columns.filter { 
                TypeMapper.mapToKotlinType(it).removePrefix("?").removeSuffix("?") == "String" 
            }
            if (stringColumns.isNotEmpty()) {
                appendLine("    /**")
                appendLine("     * Search methods for text columns")
                appendLine("     */")
                appendLine("    object Search {")
                stringColumns.forEach { column ->
                    val propertyName = NamingConventions.columnNameToPropertyName(column.columnName)
                    val columnProperty = propertyName.uppercase()
                    val methodName = "by${propertyName.replaceFirstChar { it.uppercase() }}"
                    
                    appendLine("        fun ${methodName}Contains(text: String) = selectAll()")
                    appendLine("            .where(${className}Table.Columns.$columnProperty like \"%\$text%\")")
                    appendLine("        fun ${methodName}StartsWith(text: String) = selectAll()")
                    appendLine("            .where(${className}Table.Columns.$columnProperty like \"\$text%\")")
                    appendLine("        fun ${methodName}EndsWith(text: String) = selectAll()")
                    appendLine("            .where(${className}Table.Columns.$columnProperty like \"%\$text\")")
                }
                appendLine("    }")
                appendLine()
            }
            
            // Add date/timestamp helpers if available
            val dateColumns = table.columns.filter { 
                val kotlinType = TypeMapper.mapToKotlinType(it).removePrefix("?").removeSuffix("?")
                kotlinType == "Instant" || kotlinType == "LocalDateTime" || kotlinType == "LocalDate"
            }
            if (dateColumns.isNotEmpty()) {
                appendLine("    /**")
                appendLine("     * Date range query helpers")
                appendLine("     */")
                appendLine("    object DateQueries {")
                dateColumns.forEach { column ->
                    val propertyName = NamingConventions.columnNameToPropertyName(column.columnName)
                    val columnProperty = propertyName.uppercase()
                    val methodName = "by${propertyName.replaceFirstChar { it.uppercase() }}"
                    val kotlinType = TypeMapper.mapToKotlinType(column).removePrefix("?").removeSuffix("?")
                    
                    appendLine("        fun ${methodName}Between(start: $kotlinType, end: $kotlinType) = selectAll()")
                    appendLine("            .where(${className}Table.Columns.$columnProperty greaterThanOrEqual start)")
                    appendLine("            .and(${className}Table.Columns.$columnProperty lessThanOrEqual end)")
                    appendLine("        fun ${methodName}After(date: $kotlinType) = selectAll()")
                    appendLine("            .where(${className}Table.Columns.$columnProperty greaterThan date)")
                    appendLine("        fun ${methodName}Before(date: $kotlinType) = selectAll()")
                    appendLine("            .where(${className}Table.Columns.$columnProperty lessThan date)")
                }
                appendLine("    }")
                appendLine()
            }
            
            appendLine("}")
        }
    }
} 