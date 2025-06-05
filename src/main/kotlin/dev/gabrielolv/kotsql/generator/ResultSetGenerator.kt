package dev.gabrielolv.kotsql.generator

import dev.gabrielolv.kotsql.mapping.TypeMapper
import dev.gabrielolv.kotsql.model.SQLColumnInfo
import dev.gabrielolv.kotsql.model.SQLTableInfo
import dev.gabrielolv.kotsql.util.NamingConventions

/**
 * Handles generation of ResultSet parsing utilities for data classes
 */
object ResultSetGenerator {
    
    /**
     * Generate ResultSet extension methods for parsing rows into data classes
     */
    fun generateResultSetExtensions(
        table: SQLTableInfo,
        className: String
    ): String {
        return buildString {
            appendLine("/**")
            appendLine(" * ResultSet parsing extensions for ${table.tableName} table")
            appendLine(" */")
            appendLine()
            
            // Generate main parsing method
            appendLine("/**")
            appendLine(" * Parse current ResultSet row into a $className instance")
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
            
            // Generate list parsing method
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
            "Vector" -> if (isNullable) "getString(\"$columnName\")?.let { Vector.parse(it) }" else "Vector.parse(getString(\"$columnName\"))"
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
            "Vector" -> if (isNullable) "getString($index)?.let { Vector.parse(it) }" else "Vector.parse(getString($index))"
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
        }
    }
    
    /**
     * Get required imports for ResultSet parsing
     */
    fun getRequiredImports(): Set<String> {
        return setOf(
            "java.sql.ResultSet",
            "java.sql.PreparedStatement",
            "java.sql.SQLException"
        )
    }
} 