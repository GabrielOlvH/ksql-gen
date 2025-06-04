package dev.gabrielolv.kotsql.query

/**
 * A value class that wraps column names with type information.
 * This enables type-safe operations while keeping runtime overhead minimal.
 * The generic type parameter ensures operations match the expected column type.
 */
@JvmInline
value class Column<T>(val name: String) {

    /**
     * Create an equality condition for this column
     */
    infix fun eq(value: T): WhereCondition<T> = WhereCondition.Equals(this, value)

    infix fun neq(value: T): WhereCondition<T> = WhereCondition.NotEquals(this, value)

    /**
     * Create an IN condition for this column
     */
    infix fun `in`(values: List<T>): WhereCondition<T> = WhereCondition.In(this, values)

    /**
     * Create an IN condition for this column with vararg
     */
    fun `in`(vararg values: T): WhereCondition<T> = WhereCondition.In(this, values.toList())

    /**
     * Create a NOT NULL condition for this column
     */
    fun isNotNull(): WhereCondition<T> = WhereCondition.IsNotNull(this)

    /**
     * Create a NULL condition for this column
     */
    fun isNull(): WhereCondition<T> = WhereCondition.IsNull(this)

    override fun toString(): String = name
}

// Extension functions for type-constrained operations

/**
 * Create a LIKE condition for string columns
 */
infix fun Column<String>.like(pattern: String): WhereCondition<String> =
    WhereCondition.Like(this, pattern)

/**
 * Create a greater than condition for comparable types
 */
infix fun <T : Comparable<T>> Column<T>.gt(value: T): WhereCondition<T> =
    WhereCondition.GreaterThan(this, value)

/**
 * Create a greater than or equal condition for comparable types
 */
infix fun <T : Comparable<T>> Column<T>.gte(value: T): WhereCondition<T> =
    WhereCondition.GreaterThanOrEqual(this, value)

/**
 * Create a less than condition for comparable types
 */
infix fun <T : Comparable<T>> Column<T>.lt(value: T): WhereCondition<T> =
    WhereCondition.LessThan(this, value)

/**
 * Create a less than or equal condition for comparable types
 */
infix fun <T : Comparable<T>> Column<T>.lte(value: T): WhereCondition<T> =
    WhereCondition.LessThanOrEqual(this, value)

/**
 * Represents a WHERE condition with type safety
 */
sealed class WhereCondition<T> {
    abstract val column: Column<T>

    data class Equals<T>(override val column: Column<T>, val value: T) : WhereCondition<T>()
    data class NotEquals<T>(override val column: Column<T>, val value: T) : WhereCondition<T>()
    data class In<T>(override val column: Column<T>, val values: List<T>) : WhereCondition<T>()
    data class IsNotNull<T>(override val column: Column<T>) : WhereCondition<T>()
    data class IsNull<T>(override val column: Column<T>) : WhereCondition<T>()
    data class Like(override val column: Column<String>, val pattern: String) : WhereCondition<String>()
    data class GreaterThan<T : Comparable<T>>(override val column: Column<T>, val value: T) : WhereCondition<T>()
    data class GreaterThanOrEqual<T : Comparable<T>>(override val column: Column<T>, val value: T) : WhereCondition<T>()
    data class LessThan<T : Comparable<T>>(override val column: Column<T>, val value: T) : WhereCondition<T>()
    data class LessThanOrEqual<T : Comparable<T>>(override val column: Column<T>, val value: T) : WhereCondition<T>()

    /**
     * Convert condition to SQL string and collect parameters
     */
    fun toSQL(parameterCollector: MutableList<Any?>): String {
        return when (this) {
            is Equals -> {
                parameterCollector.add(value)
                "${column.name} = ?"
            }
            is NotEquals -> {
                parameterCollector.add(value)
                "${column.name} != ?"
            }
            is In -> {
                val placeholders = values.map {
                    parameterCollector.add(it)
                    "?"
                }.joinToString(", ")
                "${column.name} IN ($placeholders)"
            }
            is IsNotNull -> "${column.name} IS NOT NULL"
            is IsNull -> "${column.name} IS NULL"
            is Like -> {
                parameterCollector.add(pattern)
                "${column.name} LIKE ?"
            }
            is GreaterThan -> {
                parameterCollector.add(value)
                "${column.name} > ?"
            }
            is GreaterThanOrEqual -> {
                parameterCollector.add(value)
                "${column.name} >= ?"
            }
            is LessThan -> {
                parameterCollector.add(value)
                "${column.name} < ?"
            }
            is LessThanOrEqual -> {
                parameterCollector.add(value)
                "${column.name} <= ?"
            }
        }
    }
}