package dev.gabrielolv.kotsql.relationship

import dev.gabrielolv.kotsql.model.RelationshipInfo
import dev.gabrielolv.kotsql.model.SQLTableInfo
import dev.gabrielolv.kotsql.model.SchemaRelationships

/**
 * Detects relationships between tables based on SQL metadata and naming conventions.
 * Identifies One-to-One, One-to-Many, Many-to-One, and Many-to-Many relationships.
 */
object RelationshipDetector {
    
    /**
     * Analyze all tables and detect relationships between them
     */
    fun detectRelationships(tables: List<SQLTableInfo>): SchemaRelationships {
        val relationships = mutableListOf<RelationshipInfo>()
        val tableMap = tables.associateBy { it.tableName }
        
        // Detect explicit foreign key relationships only
        for (table in tables) {
            for (column in table.columns) {
                // Only process columns with explicit FOREIGN KEY constraints
                val referencedTable = column.referencedTableName
                val referencedColumn = column.referencedColumnName
                
                if (referencedTable != null && referencedColumn != null && tableMap.containsKey(referencedTable)) {
                    val relationship = determineRelationshipType(
                        fromTable = table.tableName,
                        fromColumn = column.columnName,
                        toTable = referencedTable,
                        toColumn = referencedColumn,
                        isNullable = column.isNullable,
                        tables = tableMap
                    )
                    relationships.add(relationship)
                }
            }
        }
        
        // Detect many-to-many relationships via junction tables
        val junctionTables = identifyJunctionTables(tables)
        for (junctionTable in junctionTables) {
            val manyToManyRelationships = detectManyToManyRelationships(junctionTable, tableMap)
            relationships.addAll(manyToManyRelationships)
        }
        
        return SchemaRelationships(relationships)
    }
    
    /**
     * Determine the type of relationship based on table structure and constraints
     */
    private fun determineRelationshipType(
        fromTable: String,
        fromColumn: String,
        toTable: String,
        toColumn: String,
        isNullable: Boolean,
        tables: Map<String, SQLTableInfo>
    ): RelationshipInfo {
        val fromTableInfo = tables[fromTable]!!
        
        // Check if the foreign key column is unique or primary key
        val isUnique = fromTableInfo.columns.any { 
            it.columnName == fromColumn && (it.isPrimaryKey || isColumnUnique(it.columnName, fromTableInfo))
        }
        
        return when {
            // One-to-One: Foreign key is unique and typically nullable
            isUnique -> RelationshipInfo.OneToOne(
                fromTable = fromTable,
                toTable = toTable,
                fromColumn = fromColumn,
                toColumn = toColumn,
                isOptional = isNullable
            )
            
            // Many-to-One: Foreign key is not unique (default case)
            else -> RelationshipInfo.ManyToOne(
                fromTable = fromTable,
                toTable = toTable,
                fromColumn = fromColumn,
                toColumn = toColumn,
                isOptional = isNullable
            )
        }
    }
    
    /**
     * Identify tables that serve as junction tables for many-to-many relationships
     */
    private fun identifyJunctionTables(tables: List<SQLTableInfo>): List<SQLTableInfo> {
        return tables.filter { table ->
            // Junction table criteria:
            // 1. Has exactly 2 columns with explicit foreign key constraints
            // 2. Primary key is composite of those foreign keys
            // 3. May have additional metadata columns (created_at, etc.)
            
            val foreignKeyColumns = table.columns.filter { it.referencedTableName != null }
            
            // Check for composite primary key or multiple individual primary key columns
            val primaryKeyColumns = if (table.hasCompositePrimaryKey) {
                table.primaryKeyColumns
            } else {
                table.columns.filter { it.isPrimaryKey }
            }
            
            foreignKeyColumns.size == 2 && 
                primaryKeyColumns.size >= 2 &&
                foreignKeyColumns.all { fk -> primaryKeyColumns.any { pk -> pk.columnName == fk.columnName } }
        }
    }
    
    /**
     * Detect many-to-many relationships through a junction table
     */
    private fun detectManyToManyRelationships(
        junctionTable: SQLTableInfo,
        tables: Map<String, SQLTableInfo>
    ): List<RelationshipInfo> {
        val foreignKeyColumns = junctionTable.columns.filter { it.referencedTableName != null }
        
        if (foreignKeyColumns.size != 2) return emptyList()
        
        val firstFk = foreignKeyColumns[0]
        val secondFk = foreignKeyColumns[1]
        
        val firstReferencedTable = firstFk.referencedTableName!!
        val secondReferencedTable = secondFk.referencedTableName!!
        
        // Ensure both referenced tables exist
        if (!tables.containsKey(firstReferencedTable) || !tables.containsKey(secondReferencedTable)) {
            return emptyList()
        }
        
        return listOf(
            // First table to second table via junction
            RelationshipInfo.ManyToMany(
                fromTable = firstReferencedTable,
                toTable = secondReferencedTable,
                fromColumn = "id",
                toColumn = "id",
                junctionTable = junctionTable.tableName,
                junctionFromColumn = firstFk.columnName,
                junctionToColumn = secondFk.columnName
            ),
            // Second table to first table via junction (bidirectional)
            RelationshipInfo.ManyToMany(
                fromTable = secondReferencedTable,
                toTable = firstReferencedTable,
                fromColumn = "id",
                toColumn = "id",
                junctionTable = junctionTable.tableName,
                junctionFromColumn = secondFk.columnName,
                junctionToColumn = firstFk.columnName
            )
        )
    }
    
    /**
     * Check if a column has a unique constraint
     */
    private fun isColumnUnique(columnName: String, table: SQLTableInfo): Boolean {
        // For now, check if column name suggests uniqueness
        // In a real implementation, this would check actual UNIQUE constraints
        val uniquePatterns = listOf("email", "username", "slug", "code")
        return uniquePatterns.any { pattern -> 
            columnName.lowercase().contains(pattern) 
        }
    }
    
    /**
     * Generate reverse relationships (One-to-Many from Many-to-One)
     */
    fun generateReverseRelationships(relationships: SchemaRelationships): SchemaRelationships {
        val allRelationships = relationships.relationships.toMutableList()
        
        // For each Many-to-One, create the reverse One-to-Many
        val reverseRelationships = relationships.relationships
            .filterIsInstance<RelationshipInfo.ManyToOne>()
            .map { manyToOne ->
                RelationshipInfo.OneToMany(
                    fromTable = manyToOne.toTable,
                    toTable = manyToOne.fromTable,
                    fromColumn = manyToOne.toColumn,
                    toColumn = manyToOne.fromColumn
                )
            }
        
        allRelationships.addAll(reverseRelationships)
        
        return SchemaRelationships(allRelationships)
    }
    
    /**
     * Get relationship cardinality description
     */
    fun getCardinalityDescription(relationship: RelationshipInfo): String {
        return when (relationship) {
            is RelationshipInfo.OneToOne -> "1:1"
            is RelationshipInfo.OneToMany -> "1:N"
            is RelationshipInfo.ManyToOne -> "N:1"
            is RelationshipInfo.ManyToMany -> "N:N"
        }
    }
} 