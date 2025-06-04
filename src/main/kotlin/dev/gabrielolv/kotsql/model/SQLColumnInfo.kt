package dev.gabrielolv.kotsql.model

/**
 * Represents SQL column metadata including name, type, constraints, and optional default value.
 */
data class SQLColumnInfo(
    val columnName: String,
    val sqlType: String,
    val isNullable: Boolean,
    val isPrimaryKey: Boolean,
    val defaultValue: String? = null,
    val maxLength: Int? = null, // For VARCHAR(n) constraints or VECTOR(n) dimensions
    val constraints: List<SQLConstraint> = emptyList()
) {
    /**
     * Check if this column is a vector type
     */
    val isVectorType: Boolean
        get() = sqlType.uppercase().startsWith("VECTOR")
    
    /**
     * Get vector dimension if this is a vector column
     */
    val vectorDimension: Int?
        get() = if (isVectorType) maxLength else null
    
    /**
     * Check if this column is likely a foreign key based on naming conventions
     */
    val isLikelyForeignKey: Boolean
        get() = columnName.endsWith("_id") && columnName != "id"
    
    /**
     * Get the referenced table name if this appears to be a foreign key
     */
    val referencedTableName: String?
        get() {
            // Only check for explicit FOREIGN KEY constraints
            val foreignKeyConstraint = constraints.filterIsInstance<SQLConstraint.ForeignKey>().firstOrNull()
            return foreignKeyConstraint?.referencedTable
        }
    
    /**
     * Get the referenced column name if this is a foreign key
     */
    val referencedColumnName: String?
        get() {
            // Only check for explicit FOREIGN KEY constraints
            val foreignKeyConstraint = constraints.filterIsInstance<SQLConstraint.ForeignKey>().firstOrNull()
            return foreignKeyConstraint?.referencedColumn
        }
}

/**
 * Represents SQL constraints that can be applied to columns
 */
sealed class SQLConstraint {
    data class NotNull(val enforced: Boolean = true) : SQLConstraint()
    data class PrimaryKey(val autoIncrement: Boolean = false) : SQLConstraint()
    data class ForeignKey(val referencedTable: String, val referencedColumn: String) : SQLConstraint()
    data class Unique(val constraintName: String? = null) : SQLConstraint()
    data class Check(val expression: String) : SQLConstraint()
    data class Default(val value: String) : SQLConstraint()
} 