package dev.gabrielolv.kotsql.util

/**
 * Utility functions for converting between different naming conventions.
 * Handles snake_case to camelCase conversion and other naming transformations.
 */
object NamingConventions {
    
    /**
     * Convert SQL table name to PascalCase class name
     * Examples: users -> Users, user_profiles -> UserProfiles
     */
    fun tableNameToClassName(tableName: String): String {
        return tableName
            .split('_')
            .joinToString("") { part ->
                part.lowercase().replaceFirstChar { it.uppercase() }
            }
    }
    
    /**
     * Convert SQL column name to camelCase property name
     * Examples: user_name -> userName, first_name -> firstName, id -> id
     */
    fun columnNameToPropertyName(columnName: String): String {
        if (!columnName.contains('_')) {
            return columnName.lowercase()
        }
        
        val parts = columnName.split('_')
        val first = parts[0].lowercase()
        val rest = parts.drop(1).joinToString("") { part ->
            part.lowercase().replaceFirstChar { it.uppercase() }
        }
        
        return first + rest
    }
    
    /**
     * Convert camelCase property name back to snake_case
     * Examples: userName -> user_name, firstName -> first_name
     */
    fun propertyNameToColumnName(propertyName: String): String {
        return propertyName.replace(Regex("([A-Z])")) { "_${it.value.lowercase()}" }
    }
    
    /**
     * Check if original column name differs from property name
     * Used to determine if @SerialName annotation is needed
     */
    fun needsSerialNameAnnotation(columnName: String, propertyName: String): Boolean {
        return columnName != propertyName
    }
    
    /**
     * Handle edge cases in naming conversion
     */
    fun sanitizeIdentifier(identifier: String): String {
        // Handle consecutive underscores
        val withoutConsecutiveUnderscores = identifier.replace(Regex("_{2,}"), "_")
        
        // Handle numbers and special cases
        val sanitized = withoutConsecutiveUnderscores
            .replace(Regex("^[0-9]"), "_$0") // Prefix numbers with underscore
            .replace(Regex("[^a-zA-Z0-9_]"), "_") // Replace special chars with underscore
        
        return sanitized
    }
    
    /**
     * Convert to safe Kotlin identifier
     */
    fun toSafeKotlinIdentifier(name: String): String {
        val sanitized = sanitizeIdentifier(name)
        
        // Check against Kotlin keywords and escape if necessary
        return if (isKotlinKeyword(sanitized)) {
            "`$sanitized`"
        } else {
            sanitized
        }
    }
    
    /**
     * Check if a string is a Kotlin keyword
     */
    private fun isKotlinKeyword(identifier: String): Boolean {
        val kotlinKeywords = setOf(
            "as", "break", "class", "continue", "do", "else", "false", "for",
            "fun", "if", "in", "interface", "is", "null", "object", "package",
            "return", "super", "this", "throw", "true", "try", "typealias",
            "typeof", "val", "var", "when", "while", "by", "catch", "constructor",
            "delegate", "dynamic", "field", "file", "finally", "get", "import",
            "init", "param", "property", "receiver", "set", "setparam", "where",
            "actual", "abstract", "annotation", "companion", "const", "crossinline",
            "data", "enum", "expect", "external", "final", "infix", "inline",
            "inner", "internal", "lateinit", "noinline", "open", "operator",
            "out", "override", "private", "protected", "public", "reified",
            "sealed", "suspend", "tailrec", "vararg"
        )
        
        return identifier.lowercase() in kotlinKeywords
    }
    
    /**
     * Generate table metadata object name
     * Examples: Users -> UsersTable, UserProfile -> UserProfileTable
     */
    fun generateTableMetadataName(className: String): String {
        return "${className}Table"
    }
    
    /**
     * Generate columns object name
     */
    fun generateColumnsObjectName(): String {
        return "Columns"
    }
} 