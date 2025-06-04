package dev.gabrielolv.kotsql.model

/**
 * Represents different types of relationships between database tables
 */
sealed class RelationshipInfo {
    abstract val fromTable: String
    abstract val toTable: String
    abstract val fromColumn: String
    abstract val toColumn: String
    
    /**
     * One-to-One relationship (e.g., User -> UserProfile)
     */
    data class OneToOne(
        override val fromTable: String,
        override val toTable: String,
        override val fromColumn: String,
        override val toColumn: String,
        val isOptional: Boolean = false
    ) : RelationshipInfo()
    
    /**
     * One-to-Many relationship (e.g., User -> Posts)
     */
    data class OneToMany(
        override val fromTable: String,
        override val toTable: String,
        override val fromColumn: String,
        override val toColumn: String
    ) : RelationshipInfo()
    
    /**
     * Many-to-One relationship (e.g., Post -> User)
     */
    data class ManyToOne(
        override val fromTable: String,
        override val toTable: String,
        override val fromColumn: String,
        override val toColumn: String,
        val isOptional: Boolean = false
    ) : RelationshipInfo()
    
    /**
     * Many-to-Many relationship (e.g., Post <-> Categories via post_categories)
     */
    data class ManyToMany(
        override val fromTable: String,
        override val toTable: String,
        override val fromColumn: String,
        override val toColumn: String,
        val junctionTable: String,
        val junctionFromColumn: String,
        val junctionToColumn: String
    ) : RelationshipInfo()
}

/**
 * Container for all relationships in a schema
 */
data class SchemaRelationships(
    val relationships: List<RelationshipInfo>
) {
    /**
     * Get all relationships for a specific table
     */
    fun getRelationshipsForTable(tableName: String): List<RelationshipInfo> {
        return relationships.filter { 
            it.fromTable == tableName || it.toTable == tableName 
        }
    }
    
    /**
     * Get outgoing relationships from a table
     */
    fun getOutgoingRelationships(tableName: String): List<RelationshipInfo> {
        return relationships.filter { it.fromTable == tableName }
    }
    
    /**
     * Get incoming relationships to a table
     */
    fun getIncomingRelationships(tableName: String): List<RelationshipInfo> {
        return relationships.filter { it.toTable == tableName }
    }
    
    /**
     * Find relationship between two specific tables
     */
    fun findRelationship(fromTable: String, toTable: String): RelationshipInfo? {
        return relationships.find { 
            it.fromTable == fromTable && it.toTable == toTable 
        }
    }
    
    /**
     * Check if a table is a junction table
     */
    fun isJunctionTable(tableName: String): Boolean {
        return relationships.any { 
            it is RelationshipInfo.ManyToMany && it.junctionTable == tableName 
        }
    }
} 