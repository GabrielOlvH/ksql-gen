package dev.gabrielolv.kotsql.query

import dev.gabrielolv.kotsql.model.RelationshipInfo

/**
 * Enhanced query builder that supports JOIN operations based on table relationships.
 * Provides type-safe join operations with compile-time validation.
 */
class JoinQuery<T> private constructor(
    private val mainTable: String,
    private val joins: MutableList<JoinClause> = mutableListOf(),
    private val conditions: MutableList<WhereCondition<*>> = mutableListOf(),
    private val orderByColumns: MutableList<Pair<String, TypeSafeQuery.SortOrder>> = mutableListOf(),
    private var limitValue: Int? = null,
    private var offsetValue: Int? = null
) {
    
    companion object {
        /**
         * Create a join query starting from the specified table
         */
        fun <T> from(tableName: String): JoinQuery<T> = JoinQuery(tableName)
    }
    
    /**
     * Add an INNER JOIN based on a relationship
     * Table name is automatically used as the alias for prefixing
     */
    fun innerJoin(relationship: RelationshipInfo): JoinQuery<T> {
        val joinClause = JoinClause(
            type = JoinType.INNER,
            targetTable = relationship.toTable,
            alias = relationship.toTable, // Use table name as alias
            onCondition = "${relationship.fromTable}.${relationship.fromColumn} = ${relationship.toTable}.${relationship.toColumn}"
        )
        joins.add(joinClause)
        return this
    }
    
    /**
     * Add a LEFT JOIN based on a relationship
     * Table name is automatically used as the alias for prefixing
     */
    fun leftJoin(relationship: RelationshipInfo): JoinQuery<T> {
        val joinClause = JoinClause(
            type = JoinType.LEFT,
            targetTable = relationship.toTable,
            alias = relationship.toTable, // Use table name as alias
            onCondition = "${relationship.fromTable}.${relationship.fromColumn} = ${relationship.toTable}.${relationship.toColumn}"
        )
        joins.add(joinClause)
        return this
    }
    
    /**
     * Add a RIGHT JOIN based on a relationship
     * Table name is automatically used as the alias for prefixing
     */
    fun rightJoin(relationship: RelationshipInfo): JoinQuery<T> {
        val joinClause = JoinClause(
            type = JoinType.RIGHT,
            targetTable = relationship.toTable,
            alias = relationship.toTable, // Use table name as alias
            onCondition = "${relationship.fromTable}.${relationship.fromColumn} = ${relationship.toTable}.${relationship.toColumn}"
        )
        joins.add(joinClause)
        return this
    }
    
    /**
     * Add a many-to-many JOIN through a junction table
     * Table names are automatically used as aliases for prefixing
     */
    fun manyToManyJoin(relationship: RelationshipInfo.ManyToMany): JoinQuery<T> {
        // First join to junction table
        val junctionJoin = JoinClause(
            type = JoinType.INNER,
            targetTable = relationship.junctionTable,
            alias = relationship.junctionTable, // Use table name as alias
            onCondition = "${relationship.fromTable}.${relationship.fromColumn} = ${relationship.junctionTable}.${relationship.junctionFromColumn}"
        )
        joins.add(junctionJoin)
        
        // Then join to target table
        val targetJoin = JoinClause(
            type = JoinType.INNER,
            targetTable = relationship.toTable,
            alias = relationship.toTable, // Use table name as alias
            onCondition = "${relationship.junctionTable}.${relationship.junctionToColumn} = ${relationship.toTable}.${relationship.toColumn}"
        )
        joins.add(targetJoin)
        
        return this
    }
    
    /**
     * Add a WHERE condition with type safety
     */
    fun <C> where(condition: WhereCondition<C>): JoinQuery<T> {
        conditions.add(condition)
        return this
    }
    
    /**
     * Add a WHERE condition using column and value
     */
    fun <C> where(column: Column<C>, value: C): JoinQuery<T> {
        return where(column eq value)
    }
    
    /**
     * Add an AND condition
     */
    fun <C> and(condition: WhereCondition<C>): JoinQuery<T> {
        return where(condition)
    }
    
    /**
     * Add an AND condition using column and value
     */
    fun <C> and(column: Column<C>, value: C): JoinQuery<T> {
        return where(column, value)
    }
    
    /**
     * Add a WHERE IN condition
     */
    fun <C> whereIn(column: Column<C>, values: List<C>): JoinQuery<T> {
        return where(column `in` values)
    }
    
    /**
     * Add a WHERE IN condition with vararg
     */
    fun <C> whereIn(column: Column<C>, vararg values: C): JoinQuery<T> {
        return where(column `in` values.toList())
    }
    
    /**
     * Add ORDER BY clause with table qualification
     */
    fun <C> orderBy(
        tableAlias: String,
        column: Column<C>, 
        order: TypeSafeQuery.SortOrder = TypeSafeQuery.SortOrder.ASC
    ): JoinQuery<T> {
        orderByColumns.add("$tableAlias.${column.name}" to order)
        return this
    }
    
    /**
     * Add ORDER BY clause for main table
     */
    fun <C> orderBy(
        column: Column<C>, 
        order: TypeSafeQuery.SortOrder = TypeSafeQuery.SortOrder.ASC
    ): JoinQuery<T> {
        orderByColumns.add("$mainTable.${column.name}" to order)
        return this
    }
    
    /**
     * Add ascending ORDER BY for main table
     */
    fun <C> orderByAsc(column: Column<C>): JoinQuery<T> {
        return orderBy(column, TypeSafeQuery.SortOrder.ASC)
    }
    
    /**
     * Add descending ORDER BY for main table
     */
    fun <C> orderByDesc(column: Column<C>): JoinQuery<T> {
        return orderBy(column, TypeSafeQuery.SortOrder.DESC)
    }
    
    /**
     * Add ascending ORDER BY with table qualification
     */
    fun <C> orderByAsc(tableAlias: String, column: Column<C>): JoinQuery<T> {
        return orderBy(tableAlias, column, TypeSafeQuery.SortOrder.ASC)
    }
    
    /**
     * Add descending ORDER BY with table qualification
     */
    fun <C> orderByDesc(tableAlias: String, column: Column<C>): JoinQuery<T> {
        return orderBy(tableAlias, column, TypeSafeQuery.SortOrder.DESC)
    }
    
    /**
     * Add LIMIT clause
     */
    fun limit(count: Int): JoinQuery<T> {
        limitValue = count
        return this
    }
    
    /**
     * Add OFFSET clause
     */
    fun offset(count: Int): JoinQuery<T> {
        offsetValue = count
        return this
    }
    
    /**
     * Add pagination with page number and page size
     * Page numbers start from 0
     */
    fun paginate(page: Int, pageSize: Int): JoinQuery<T> {
        return limit(pageSize).offset(page * pageSize)
    }
    
    /**
     * Build the final SQL query with JOINs
     * All columns are automatically prefixed with their table names
     */
    fun build(): QueryResult {
        val parameters = mutableListOf<Any?>()
        
        // Collect all table aliases for column selection
        val allTables = mutableSetOf<String>()
        allTables.add(mainTable)
        joins.forEach { join ->
            allTables.add(join.alias ?: join.targetTable)
        }
        
        // Generate SELECT with table-prefixed columns
        val selectClause = allTables.joinToString(", ") { tableAlias ->
            "$tableAlias.*"
        }
        
        val sql = buildString {
            append("SELECT $selectClause FROM $mainTable")
            
            // Add JOIN clauses
            joins.forEach { join ->
                append(" ${join.type.sql} ${join.targetTable}")
                if (join.alias != null && join.alias != join.targetTable) {
                    append(" AS ${join.alias}")
                }
                append(" ON ${join.onCondition}")
            }
            
            // Add WHERE conditions
            if (conditions.isNotEmpty()) {
                append(" WHERE ")
                append(conditions.joinToString(" AND ") { condition ->
                    condition.toSQL(parameters)
                })
            }
            
            // Add ORDER BY
            if (orderByColumns.isNotEmpty()) {
                append(" ORDER BY ")
                append(orderByColumns.joinToString(", ") { (column, order) ->
                    "$column ${order.name}"
                })
            }
            
            // Add LIMIT and OFFSET
            limitValue?.let { append(" LIMIT $it") }
            offsetValue?.let { append(" OFFSET $it") }
        }
        
        return QueryResult(sql, parameters)
    }
    
    /**
     * Build SQL for SELECT with specific columns from different tables
     */
    fun select(vararg columnSelections: ColumnSelection): QueryResult {
        val parameters = mutableListOf<Any?>()
        
        val selectedColumns = columnSelections.joinToString(", ") { selection ->
            "${selection.tableAlias}.${selection.column.name}"
        }
        
        val sql = buildString {
            append("SELECT $selectedColumns FROM $mainTable")
            
            // Add JOIN clauses
            joins.forEach { join ->
                append(" ${join.type.sql} ${join.targetTable}")
                if (join.alias != null) {
                    append(" AS ${join.alias}")
                }
                append(" ON ${join.onCondition}")
            }
            
            // Add WHERE conditions
            if (conditions.isNotEmpty()) {
                append(" WHERE ")
                append(conditions.joinToString(" AND ") { condition ->
                    condition.toSQL(parameters)
                })
            }
            
            // Add ORDER BY
            if (orderByColumns.isNotEmpty()) {
                append(" ORDER BY ")
                append(orderByColumns.joinToString(", ") { (column, order) ->
                    "$column ${order.name}"
                })
            }
            
            // Add LIMIT and OFFSET
            limitValue?.let { append(" LIMIT $it") }
            offsetValue?.let { append(" OFFSET $it") }
        }
        
        return QueryResult(sql, parameters)
    }
    
    /**
     * Build SQL for COUNT query with JOINs
     * Counts rows from the main table considering JOIN conditions
     */
    fun count(): QueryResult {
        val parameters = mutableListOf<Any?>()
        
        val sql = buildString {
            append("SELECT COUNT(*) FROM $mainTable")
            
            // Add JOIN clauses
            joins.forEach { join ->
                append(" ${join.type.sql} ${join.targetTable}")
                if (join.alias != null && join.alias != join.targetTable) {
                    append(" AS ${join.alias}")
                }
                append(" ON ${join.onCondition}")
            }
            
            // Add WHERE conditions
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
     * Build SQL for DELETE query on the main table
     * DELETE operations only affect the main table, even with JOINs
     * JOINs are used only for filtering conditions
     */
    fun delete(): QueryResult {
        val parameters = mutableListOf<Any?>()
        
        val sql = buildString {
            if (joins.isEmpty()) {
                // Simple DELETE without JOINs
                append("DELETE FROM $mainTable")
            } else {
                // DELETE with JOINs for filtering
                append("DELETE $mainTable FROM $mainTable")
                
                // Add JOIN clauses
                joins.forEach { join ->
                    append(" ${join.type.sql} ${join.targetTable}")
                    if (join.alias != null && join.alias != join.targetTable) {
                        append(" AS ${join.alias}")
                    }
                    append(" ON ${join.onCondition}")
                }
            }
            
            // Add WHERE conditions
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
     * Build SQL for EXISTS check
     * Returns a query that checks if any matching rows exist
     */
    fun exists(): QueryResult {
        val parameters = mutableListOf<Any?>()
        
        val sql = buildString {
            append("SELECT EXISTS (SELECT 1 FROM $mainTable")
            
            // Add JOIN clauses
            joins.forEach { join ->
                append(" ${join.type.sql} ${join.targetTable}")
                if (join.alias != null && join.alias != join.targetTable) {
                    append(" AS ${join.alias}")
                }
                append(" ON ${join.onCondition}")
            }
            
            // Add WHERE conditions
            if (conditions.isNotEmpty()) {
                append(" WHERE ")
                append(conditions.joinToString(" AND ") { condition ->
                    condition.toSQL(parameters)
                })
            }
            
            append(")")
        }
        
        return QueryResult(sql, parameters)
    }
}

/**
 * Represents a JOIN clause in a SQL query
 */
data class JoinClause(
    val type: JoinType,
    val targetTable: String,
    val alias: String?,
    val onCondition: String
)

/**
 * Types of JOIN operations
 */
enum class JoinType(val sql: String) {
    INNER("INNER JOIN"),
    LEFT("LEFT JOIN"),
    RIGHT("RIGHT JOIN"),
    FULL("FULL OUTER JOIN")
}

/**
 * Represents a column selection with table alias
 */
data class ColumnSelection(
    val tableAlias: String,
    val column: Column<*>
) 