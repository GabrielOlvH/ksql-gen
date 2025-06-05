package dev.gabrielolv.kotsql.generator

import dev.gabrielolv.kotsql.mapping.TypeMapper
import dev.gabrielolv.kotsql.model.SQLColumnInfo
import dev.gabrielolv.kotsql.model.SQLTableInfo
import dev.gabrielolv.kotsql.model.SchemaRelationships
import dev.gabrielolv.kotsql.util.NamingConventions
import dev.gabrielolv.kotsql.validation.ValidationMapper

/**
 * Handles generation of entity classes and composite keys
 */
object EntityGenerator {
    
    /**
     * Generate the main entity class
     */
    fun generateEntityClass(
        table: SQLTableInfo,
        className: String,
        includeValidation: Boolean,
        relationships: SchemaRelationships?
    ): String {
        return buildString {
            appendLine("/**")
            appendLine(" * Generated from SQL table: ${table.tableName}")
            appendLine(" * This data class represents a row in the ${table.tableName} table.")
            if (includeValidation) {
                appendLine(" * Includes validation annotations based on SQL constraints.")
            }
            if (table.hasCompositePrimaryKey) {
                appendLine(" * This table has a composite primary key: ${table.compositePrimaryKey.joinToString(", ")}")
            }
            appendLine(" */")
            appendLine("@Serializable")
            append("data class $className(")
            
            // Generate properties
            table.columns.forEachIndexed { index, column ->
                appendLine()
                append("    ")
                append(generateProperty(column, includeValidation))
                if (index < table.columns.size - 1) {
                    append(",")
                }
            }
            
            appendLine()
            append(")")
            
            // Add class body for composite key methods only
            if (table.hasCompositePrimaryKey) {
                appendLine(" {")
                appendLine()
                
                // Add key() method for composite keys
                val keyClassName = "${className}Key"
                appendLine("    /**")
                appendLine("     * Extract the composite primary key from this instance")
                appendLine("     */")
                append("    fun key(): $keyClassName = $keyClassName(")
                table.primaryKeyColumns.forEachIndexed { index, column ->
                    val propertyName = NamingConventions.columnNameToPropertyName(column.columnName)
                    append(propertyName)
                    if (index < table.primaryKeyColumns.size - 1) {
                        append(", ")
                    }
                }
                appendLine(")")
                appendLine()
                
                // Add companion object with fromKey method only
                appendLine("    companion object {")
                appendLine("        /**")
                appendLine("         * Create an instance from a composite key with default values for non-key columns")
                appendLine("         */")
                append("        fun fromKey(key: $keyClassName")
                // Add parameters for non-key columns
                table.columns.filter { !table.isColumnPrimaryKey(it.columnName) }.forEach { column ->
                    val propertyName = NamingConventions.columnNameToPropertyName(column.columnName)
                    val kotlinType = TypeMapper.mapToKotlinType(column)
                    append(", $propertyName: $kotlinType")
                    if (column.isNullable || column.defaultValue != null) {
                        append(" = null")
                    }
                }
                appendLine("): $className {")
                append("            return $className(")
                // Map key properties first
                table.primaryKeyColumns.forEachIndexed { index, column ->
                    val propertyName = NamingConventions.columnNameToPropertyName(column.columnName)
                    append("key.$propertyName")
                    if (index < table.primaryKeyColumns.size - 1 || table.columns.size > table.primaryKeyColumns.size) {
                        append(", ")
                    }
                }
                // Map non-key properties
                table.columns.filter { !table.isColumnPrimaryKey(it.columnName) }.forEachIndexed { index, column ->
                    val propertyName = NamingConventions.columnNameToPropertyName(column.columnName)
                    append(propertyName)
                    if (index < table.columns.filter { !table.isColumnPrimaryKey(it.columnName) }.size - 1) {
                        append(", ")
                    }
                }
                appendLine(")")
                appendLine("        }")
                appendLine("    }")
                appendLine("}")
            }
        }
    }
    
    /**
     * Generate composite key class
     */
    fun generateCompositeKeyClass(
        table: SQLTableInfo,
        keyClassName: String,
        includeValidation: Boolean
    ): String {
        return buildString {
            appendLine("/**")
            appendLine(" * Composite primary key for ${table.tableName} table")
            appendLine(" * Contains: ${table.compositePrimaryKey.joinToString(", ")}")
            appendLine(" */")
            appendLine("@Serializable")
            append("data class $keyClassName(")
            
            table.primaryKeyColumns.forEachIndexed { index, column ->
                appendLine()
                append("    ")
                append(generateProperty(column, includeValidation))
                if (index < table.primaryKeyColumns.size - 1) {
                    append(",")
                }
            }
            
            appendLine()
            appendLine(") {")
            
            // Add validation method for key completeness
            appendLine("    /**")
            appendLine("     * Validate that all key components are present and valid")
            appendLine("     */")
            appendLine("    fun validateKey(): Boolean {")
            table.primaryKeyColumns.forEach { column ->
                val propertyName = NamingConventions.columnNameToPropertyName(column.columnName)
                val kotlinType = TypeMapper.mapToKotlinType(column)
                if (kotlinType.endsWith("?")) {
                    appendLine("        if ($propertyName == null) return false")
                }
            }
            appendLine("        return true")
            appendLine("    }")
            appendLine()
            
            // Add key comparison methods
            appendLine("    /**")
            appendLine("     * Convert to parameter list for SQL queries")
            appendLine("     */")
            appendLine("    fun toParameterList(): List<Any> {")
            append("        return listOf(")
            table.primaryKeyColumns.forEachIndexed { index, column ->
                val propertyName = NamingConventions.columnNameToPropertyName(column.columnName)
                append(propertyName)
                if (index < table.primaryKeyColumns.size - 1) {
                    append(", ")
                }
            }
            appendLine(")")
            appendLine("    }")
            appendLine()
            
            // Add string representation for debugging
            appendLine("    /**")
            appendLine("     * Generate a string representation for debugging")
            appendLine("     */")
            appendLine("    fun keyToString(): String {")
            append("        return \"$keyClassName(")
            table.primaryKeyColumns.forEachIndexed { index, column ->
                val propertyName = NamingConventions.columnNameToPropertyName(column.columnName)
                append("${column.columnName}=\\\$$propertyName")
                if (index < table.primaryKeyColumns.size - 1) {
                    append(", ")
                }
            }
            appendLine(")\"")
            appendLine("    }")
            
            appendLine("}")
        }
    }
    
    /**
     * Generate a property for a column
     */
    fun generateProperty(column: SQLColumnInfo, includeValidation: Boolean): String {
        return buildString {
            // Add validation annotations if enabled
            if (includeValidation) {
                val validationAnnotations = ValidationMapper.getValidationAnnotations(column)
                validationAnnotations.forEach { annotation ->
                    appendLine(annotation.toAnnotationString())
                    append("    ")
                }
            }
            
            // Add @SerialName if needed
            val propertyName = NamingConventions.columnNameToPropertyName(column.columnName)
            if (NamingConventions.needsSerialNameAnnotation(column.columnName, propertyName)) {
                appendLine("@SerialName(\"${column.columnName}\")")
                append("    ")
            }
            
            // Property declaration with proper nullable type
            val kotlinType = TypeMapper.mapToKotlinType(column)
            append("val $propertyName: $kotlinType")
        }
    }
    
    /**
     * Generate validation helper methods for the data class (compatibility)
     */
    fun generateValidationHelpers(className: String): String {
        return buildString {
            appendLine("/**")
            appendLine(" * Validation helpers for $className")
            appendLine(" */")
            appendLine("object ${className}Validation {")
            appendLine("    /**")
            appendLine("     * Validate a $className instance and return validation result")
            appendLine("     */")
            appendLine("    fun validate(instance: $className): ValidationResult {")
            appendLine("        return ValidationEngine.validate(instance)")
            appendLine("    }")
            appendLine()
            appendLine("    /**")
            appendLine("     * Validate and throw exception if validation fails")
            appendLine("     */")
            appendLine("    fun validateAndThrow(instance: $className) {")
            appendLine("        ValidationEngine.validateAndThrow(instance)")
            appendLine("    }")
            appendLine()
            appendLine("    /**")
            appendLine("     * Check if a $className instance is valid")
            appendLine("     */")
            appendLine("    fun isValid(instance: $className): Boolean {")
            appendLine("        return validate(instance).isValid")
            appendLine("    }")
            appendLine("}")
        }
    }
} 