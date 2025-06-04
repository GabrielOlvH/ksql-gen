package dev.gabrielolv.kotsql.model

/**
 * Represents a composite primary key for type-safe query operations.
 * This class provides a way to work with multi-column primary keys in a type-safe manner.
 */
data class CompositeKey<T>(
    val columnNames: List<String>,
    val values: List<Any> = emptyList()
) {
    /**
     * Check if this composite key has all values set
     */
    val isComplete: Boolean
        get() = values.size == columnNames.size && values.all { it != null }
    
    /**
     * Create a new composite key with additional values
     */
    fun withValues(vararg values: Any): CompositeKey<T> {
        return copy(values = this.values + values)
    }
    
    /**
     * Get value by column name
     */
    fun getValue(columnName: String): Any? {
        val index = columnNames.indexOf(columnName)
        return if (index >= 0 && index < values.size) values[index] else null
    }
    
    /**
     * Convert to parameter map for SQL queries
     */
    fun toParameterMap(): Map<String, Any> {
        return columnNames.zip(values).toMap()
    }
    
    /**
     * Convert to WHERE clause conditions
     */
    fun toWhereConditions(): String {
        return columnNames.joinToString(" AND ") { "$it = ?" }
    }
    
    companion object {
        /**
         * Create a composite key definition with column names
         */
        fun <T> of(vararg columnNames: String): CompositeKey<T> {
            return CompositeKey(columnNames.toList())
        }
    }
} 