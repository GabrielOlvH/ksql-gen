package dev.gabrielolv.kotsql.query

/**
 * TypeSafeQuery class that builds SQL queries with type safety.
 * Maintains internal lists of WHERE conditions and parameters.
 * Provides methods that accept typed Column references and ensures type matching at compile time.
 */
class TypeSafeQuery<T> private constructor(
    private val tableName: String
) {
    private val conditions = mutableListOf<WhereCondition<*>>()
    private val orderByColumns = mutableListOf<Pair<String, SortOrder>>()
    private var limitValue: Int? = null
    private var offsetValue: Int? = null
    
    enum class SortOrder { ASC, DESC }
    
    companion object {
        /**
         * Create a query for the specified table
         */
        fun <T> from(tableName: String): TypeSafeQuery<T> = TypeSafeQuery(tableName)
    }
    
    /**
     * Add a WHERE condition with type safety
     */
    fun <C> where(condition: WhereCondition<C>): TypeSafeQuery<T> {
        conditions.add(condition)
        return this
    }
    
    /**
     * Add a WHERE condition using column and value
     */
    fun <C> where(column: Column<C>, value: C): TypeSafeQuery<T> {
        return where(column eq value)
    }
    
    /**
     * Add a WHERE IN condition
     */
    fun <C> whereIn(column: Column<C>, values: List<C>): TypeSafeQuery<T> {
        return where(column `in` values)
    }
    
    /**
     * Add a WHERE IN condition with vararg
     */
    fun <C> whereIn(column: Column<C>, vararg values: C): TypeSafeQuery<T> {
        return where(column `in` values.toList())
    }
    
    /**
     * Add an AND condition
     */
    fun <C> and(condition: WhereCondition<C>): TypeSafeQuery<T> {
        return where(condition)
    }
    
    /**
     * Add an AND condition using column and value
     */
    fun <C> and(column: Column<C>, value: C): TypeSafeQuery<T> {
        return where(column, value)
    }
    
    /**
     * Add ORDER BY clause
     */
    fun <C> orderBy(column: Column<C>, order: SortOrder = SortOrder.ASC): TypeSafeQuery<T> {
        orderByColumns.add(column.name to order)
        return this
    }
    
    /**
     * Add ascending ORDER BY
     */
    fun <C> orderByAsc(column: Column<C>): TypeSafeQuery<T> {
        return orderBy(column, SortOrder.ASC)
    }
    
    /**
     * Add descending ORDER BY
     */
    fun <C> orderByDesc(column: Column<C>): TypeSafeQuery<T> {
        return orderBy(column, SortOrder.DESC)
    }
    
    /**
     * Add LIMIT clause
     */
    fun limit(count: Int): TypeSafeQuery<T> {
        limitValue = count
        return this
    }
    
    /**
     * Add OFFSET clause
     */
    fun offset(count: Int): TypeSafeQuery<T> {
        offsetValue = count
        return this
    }
    
    /**
     * Build the final SQL query and return SQL string with parameter list
     */
    fun build(): QueryResult {
        val parameters = mutableListOf<Any?>()
        val sql = buildString {
            append("SELECT * FROM $tableName")
            
            if (conditions.isNotEmpty()) {
                append(" WHERE ")
                append(conditions.joinToString(" AND ") { condition ->
                    condition.toSQL(parameters)
                })
            }
            
            if (orderByColumns.isNotEmpty()) {
                append(" ORDER BY ")
                append(orderByColumns.joinToString(", ") { (column, order) ->
                    "$column ${order.name}"
                })
            }
            
            limitValue?.let { append(" LIMIT $it") }
            offsetValue?.let { append(" OFFSET $it") }
        }
        
        return QueryResult(sql, parameters)
    }
    
    /**
     * Build SQL for SELECT with specific columns
     */
    fun select( columns: Array<Column<*>>): QueryResult {
        val parameters = mutableListOf<Any?>()
        val columnNames = columns.joinToString(", ") { it.name }
        
        val sql = buildString {
            append("SELECT $columnNames FROM $tableName")
            
            if (conditions.isNotEmpty()) {
                append(" WHERE ")
                append(conditions.joinToString(" AND ") { condition ->
                    condition.toSQL(parameters)
                })
            }
            
            if (orderByColumns.isNotEmpty()) {
                append(" ORDER BY ")
                append(orderByColumns.joinToString(", ") { (column, order) ->
                    "$column ${order.name}"
                })
            }
            
            limitValue?.let { append(" LIMIT $it") }
            offsetValue?.let { append(" OFFSET $it") }
        }
        
        return QueryResult(sql, parameters)
    }
    
    /**
     * Build SQL for COUNT query
     */
    fun count(): QueryResult {
        val parameters = mutableListOf<Any?>()
        
        val sql = buildString {
            append("SELECT COUNT(*) FROM $tableName")
            
            if (conditions.isNotEmpty()) {
                append(" WHERE ")
                append(conditions.joinToString(" AND ") { condition ->
                    condition.toSQL(parameters)
                })
            }
        }
        
        return QueryResult(sql, parameters)
    }
    
    /**
     * Build SQL for DELETE query
     */
    fun delete(): QueryResult {
        val parameters = mutableListOf<Any?>()
        
        val sql = buildString {
            append("DELETE FROM $tableName")
            
            if (conditions.isNotEmpty()) {
                append(" WHERE ")
                append(conditions.joinToString(" AND ") { condition ->
                    condition.toSQL(parameters)
                })
            }
        }
        
        return QueryResult(sql, parameters)
    }
}

/**
 * Result of building a query, containing SQL string and parameters
 */
data class QueryResult(
    val sql: String,
    val parameters: List<Any?>
) {
    override fun toString(): String = "SQL: $sql, Parameters: $parameters"
} 