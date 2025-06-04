package dev.gabrielolv.kotsql.mapping

import dev.gabrielolv.kotsql.model.SQLColumnInfo

/**
 * Configuration for vector type generation
 */
data class VectorConfig(
    val vectorType: VectorTypeOption = VectorTypeOption.VECTOR_WRAPPER,
    val defaultDimension: Int? = null,
    val strictValidation: Boolean = true,
    val generateUtilityMethods: Boolean = true
)

/**
 * Vector type generation options
 */
enum class VectorTypeOption {
    FLOAT_ARRAY,        // Use FloatArray directly
    VECTOR_WRAPPER,     // Use Vector value class wrapper
    BOTH                // Support both options
}

/**
 * Maps SQL types to Kotlin types with support for kotlinx.datetime and vector types.
 * Handles nullable columns by adding ? to Kotlin types (except primary keys).
 */
object TypeMapper {
    
    // Default vector configuration
    var vectorConfig: VectorConfig = VectorConfig()
    
    /**
     * Maps SQL column to Kotlin type string including nullability
     */
    fun mapToKotlinType(column: SQLColumnInfo): String {
        val baseType = mapSQLTypeToKotlin(column.sqlType, column)
        
        // Primary keys are typically non-null even if not explicitly declared
        val shouldBeNullable = column.isNullable && !column.isPrimaryKey
        
        return if (shouldBeNullable) "$baseType?" else baseType
    }
    
    /**
     * Maps SQL type to base Kotlin type (without nullability)
     */
    private fun mapSQLTypeToKotlin(sqlType: String, column: SQLColumnInfo): String {
        val normalizedType = sqlType.uppercase()
        
        return when {
            // Vector types
            normalizedType.startsWith("VECTOR") -> {
                when (vectorConfig.vectorType) {
                    VectorTypeOption.FLOAT_ARRAY -> "FloatArray"
                    VectorTypeOption.VECTOR_WRAPPER -> "Vector"
                    VectorTypeOption.BOTH -> "Vector" // Default to wrapper when both
                }
            }
            
            // Integer types
            normalizedType.startsWith("TINYINT") -> "Byte"
            normalizedType.startsWith("SMALLINT") -> "Short"
            normalizedType.startsWith("INT") || normalizedType.startsWith("INTEGER") -> "Int"
            normalizedType.startsWith("BIGINT") -> "Long"
            
            // Floating point types
            normalizedType.startsWith("FLOAT") -> "Float"
            normalizedType.startsWith("DOUBLE") || normalizedType.startsWith("REAL") -> "Double"
            normalizedType.startsWith("DECIMAL") || normalizedType.startsWith("NUMERIC") -> "Double"
            
            // String types
            normalizedType.startsWith("VARCHAR") || 
            normalizedType.startsWith("CHAR") ||
            normalizedType.startsWith("TEXT") ||
            normalizedType.startsWith("CLOB") ||
            normalizedType.startsWith("NVARCHAR") ||
            normalizedType.startsWith("NCHAR") -> "String"
            
            // Boolean types
            normalizedType.startsWith("BOOLEAN") || normalizedType.startsWith("BOOL") -> "Boolean"
            normalizedType.startsWith("BIT") -> "Boolean" // Assuming single bit
            
            // Date/Time types using kotlinx.datetime
            normalizedType.startsWith("TIMESTAMP") || normalizedType.startsWith("DATETIME") -> "kotlinx.datetime.Instant"
            normalizedType.startsWith("DATE") -> "kotlinx.datetime.LocalDate"
            normalizedType.startsWith("TIME") -> "kotlinx.datetime.LocalTime"
            
            // Binary types
            normalizedType.startsWith("BLOB") ||
            normalizedType.startsWith("BINARY") ||
            normalizedType.startsWith("VARBINARY") ||
            normalizedType.startsWith("BYTEA") -> "ByteArray"
            
            // UUID types
            normalizedType.startsWith("UUID") -> "kotlin.uuid.Uuid"
            
            // JSON types
            normalizedType.startsWith("JSON") -> "kotlinx.serialization.json.JsonElement"
            
            // Fallback to String for unknown types
            else -> {
                println("Warning: Unknown SQL type '$sqlType', mapping to String")
                "String"
            }
        }
    }
    
    /**
     * Get required imports for a Kotlin type
     */
    fun getRequiredImports(kotlinType: String): Set<String> {
        val baseType = kotlinType.removeSuffix("?")
        return when (baseType) {
            "Vector" -> setOf("dev.gabrielolv.kotsql.vector.Vector")
            "kotlinx.datetime.Instant" -> setOf("kotlinx.datetime.Instant")
            "kotlinx.datetime.LocalDate" -> setOf("kotlinx.datetime.LocalDate")
            "kotlinx.datetime.LocalTime" -> setOf("kotlinx.datetime.LocalTime")
            "kotlin.uuid.Uuid" -> setOf("kotlin.uuid.Uuid")
            "kotlinx.serialization.json.JsonElement" -> setOf("kotlinx.serialization.json.JsonElement")
            else -> emptySet()
        }
    }
    
    /**
     * Get all required imports for a list of columns
     */
    fun getAllRequiredImports(columns: List<SQLColumnInfo>): Set<String> {
        return columns.flatMap { column ->
            val kotlinType = mapToKotlinType(column)
            getRequiredImports(kotlinType)
        }.toSet()
    }
} 