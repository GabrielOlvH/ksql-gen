package dev.gabrielolv.kotsql.model

/**
 * Represents SQL table metadata including table name and column information.
 * This is the foundation for all code generation.
 */
data class SQLTableInfo(
    val tableName: String,
    val columns: List<SQLColumnInfo>,
    val compositePrimaryKey: List<String> = emptyList() // Column names that form composite key
) {
    /**
     * Get the primary key column if it exists (single column primary key)
     */
    val primaryKey: SQLColumnInfo?
        get() = if (hasCompositePrimaryKey) null else columns.firstOrNull { it.isPrimaryKey }
    
    /**
     * Get all primary key columns (supports both single and composite keys)
     */
    val primaryKeyColumns: List<SQLColumnInfo>
        get() = if (hasCompositePrimaryKey) {
            compositePrimaryKey.mapNotNull { keyColumnName ->
                columns.find { it.columnName == keyColumnName }
            }
        } else {
            listOfNotNull(primaryKey)
        }
    
    /**
     * Check if this table has a composite primary key
     */
    val hasCompositePrimaryKey: Boolean
        get() = compositePrimaryKey.isNotEmpty()
    
    /**
     * Check if this table has any primary key (single or composite)
     */
    val hasPrimaryKey: Boolean
        get() = hasCompositePrimaryKey || primaryKey != null
    
    /**
     * Get all non-primary key columns
     */
    val regularColumns: List<SQLColumnInfo>
        get() = if (hasCompositePrimaryKey) {
            columns.filter { column -> column.columnName !in compositePrimaryKey }
        } else {
            columns.filter { !it.isPrimaryKey }
        }
    
    /**
     * Check if a column is part of the primary key
     */
    fun isColumnPrimaryKey(columnName: String): Boolean {
        return if (hasCompositePrimaryKey) {
            columnName in compositePrimaryKey
        } else {
            primaryKey?.columnName == columnName
        }
    }
} 