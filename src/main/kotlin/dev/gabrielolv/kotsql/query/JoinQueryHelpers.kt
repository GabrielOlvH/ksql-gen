package dev.gabrielolv.kotsql.query

import dev.gabrielolv.kotsql.model.RelationshipInfo

/**
 * Helper extensions for JoinQuery to make relationship chaining easier
 */

/**
 * Extension to make it easier to chain multiple joins
 */
inline fun <T> JoinQuery<T>.chain(block: JoinQuery<T>.() -> JoinQuery<T>): JoinQuery<T> {
    return this.block()
}

/**
 * Helper to create a join chain for many-to-many relationships through junction tables
 */
fun <T> JoinQuery<T>.throughJunction(
    firstRelationship: RelationshipInfo,
    secondRelationship: RelationshipInfo
): JoinQuery<T> {
    return this.innerJoin(firstRelationship)
               .innerJoin(secondRelationship)
}

/**
 * Helper to create left join chains
 */
fun <T> JoinQuery<T>.leftJoinChain(vararg relationships: RelationshipInfo): JoinQuery<T> {
    var query = this
    relationships.forEach { relationship ->
        query = query.leftJoin(relationship)
    }
    return query
}

/**
 * Helper to create inner join chains
 */
fun <T> JoinQuery<T>.innerJoinChain(vararg relationships: RelationshipInfo): JoinQuery<T> {
    var query = this
    relationships.forEach { relationship ->
        query = query.innerJoin(relationship)
    }
    return query
}

/**
 * Extension property for easier pagination syntax
 */
data class PaginationParams(val page: Int, val pageSize: Int)

/**
 * Apply pagination using a parameters object
 */
fun <T> JoinQuery<T>.paginate(params: PaginationParams): JoinQuery<T> {
    return this.paginate(params.page, params.pageSize)
}

/**
 * Common pagination helper
 */
fun <T> JoinQuery<T>.withPagination(page: Int = 0, pageSize: Int = 20): JoinQuery<T> {
    return this.paginate(page, pageSize)
} 