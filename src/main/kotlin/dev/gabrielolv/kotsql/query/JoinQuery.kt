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
     */
    fun <R> innerJoin(
        relationship: RelationshipInfo,
        alias: String? = null
    ): JoinQuery<T> {
        val joinClause = JoinClause(
            type = JoinType.INNER,
            targetTable = relationship.toTable,
            alias = alias,
            onCondition = "${relationship.fromTable}.${relationship.fromColumn} = ${relationship.toTable}.${relationship.toColumn}"
        )
        joins.add(joinClause)
        return this
    }
    
    /**
     * Add a LEFT JOIN based on a relationship
     */
    fun <R> leftJoin(
        relationship: RelationshipInfo,
        alias: String? = null
    ): JoinQuery<T> {
        val joinClause = JoinClause(
            type = JoinType.LEFT,
            targetTable = relationship.toTable,
            alias = alias,
            onCondition = "${relationship.fromTable}.${relationship.fromColumn} = ${relationship.toTable}.${relationship.toColumn}"
        )
        joins.add(joinClause)
        return this
    }
    
    /**
     * Add a RIGHT JOIN based on a relationship
     */
    fun <R> rightJoin(
        relationship: RelationshipInfo,
        alias: String? = null
    ): JoinQuery<T> {
        val joinClause = JoinClause(
            type = JoinType.RIGHT,
            targetTable = relationship.toTable,
            alias = alias,
            onCondition = "${relationship.fromTable}.${relationship.fromColumn} = ${relationship.toTable}.${relationship.toColumn}"
        )
        joins.add(joinClause)
        return this
    }
    
    /**
     * Add a many-to-many JOIN through a junction table
     */
    fun <R> manyToManyJoin(
        relationship: RelationshipInfo.ManyToMany,
        alias: String? = null
    ): JoinQuery<T> {
        // First join to junction table
        val junctionJoin = JoinClause(
            type = JoinType.INNER,
            targetTable = relationship.junctionTable,
            alias = "${relationship.junctionTable}_junction",
            onCondition = "${relationship.fromTable}.${relationship.fromColumn} = ${relationship.junctionTable}.${relationship.junctionFromColumn}"
        )
        joins.add(junctionJoin)
        
        // Then join to target table
        val targetJoin = JoinClause(
            type = JoinType.INNER,
            targetTable = relationship.toTable,
            alias = alias,
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
     * Build the final SQL query with JOINs
     */
    fun build(): QueryResult {
        val parameters = mutableListOf<Any?>()
        
        val sql = buildString {
            append("SELECT * FROM $mainTable")
            
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