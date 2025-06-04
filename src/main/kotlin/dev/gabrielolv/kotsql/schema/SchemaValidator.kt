package dev.gabrielolv.kotsql.schema

import dev.gabrielolv.kotsql.model.SQLColumnInfo
import dev.gabrielolv.kotsql.model.SQLTableInfo
import dev.gabrielolv.kotsql.model.RelationshipInfo

/**
 * Validates schema changes between versions and detects potential conflicts.
 * Analyzes backward compatibility and provides migration recommendations.
 */
object SchemaValidator {
    
    /**
     * Compare two schema versions and detect all changes
     */
    fun validateSchemaChange(
        fromSchema: SchemaVersion,
        toSchema: SchemaVersion
    ): SchemaValidationResult {
        val changes = mutableListOf<SchemaChange>()
        
        // Detect table changes
        changes.addAll(detectTableChanges(fromSchema, toSchema))
        
        // Detect column changes for existing tables
        changes.addAll(detectColumnChanges(fromSchema, toSchema))
        
        // Detect relationship changes
        changes.addAll(detectRelationshipChanges(fromSchema, toSchema))
        
        // Determine backward compatibility
        val isBackwardCompatible = determineBackwardCompatibility(changes)
        
        // Recommend version bump
        val recommendedVersionBump = determineVersionBump(changes)
        
        return SchemaValidationResult(
            fromVersion = fromSchema,
            toVersion = toSchema,
            changes = changes,
            isBackwardCompatible = isBackwardCompatible,
            recommendedVersionBump = recommendedVersionBump
        )
    }
    
    /**
     * Detect changes in tables (added, removed, renamed)
     */
    private fun detectTableChanges(
        fromSchema: SchemaVersion,
        toSchema: SchemaVersion
    ): List<SchemaChange> {
        val changes = mutableListOf<SchemaChange>()
        
        val fromTables = fromSchema.getTableNames()
        val toTables = toSchema.getTableNames()
        
        // Detect added tables
        val addedTables = toTables - fromTables
        addedTables.forEach { tableName ->
            val table = toSchema.getTable(tableName)!!
            changes.add(SchemaChange.TableAdded(tableName, table))
        }
        
        // Detect removed tables
        val removedTables = fromTables - toTables
        removedTables.forEach { tableName ->
            val table = fromSchema.getTable(tableName)!!
            changes.add(SchemaChange.TableRemoved(tableName, table))
        }
        
        // Detect potential table renames
        val potentialRenames = detectTableRenames(
            fromSchema.tables.filter { it.tableName in removedTables },
            toSchema.tables.filter { it.tableName in addedTables }
        )
        
        potentialRenames.forEach { (oldName, newName) ->
            changes.add(SchemaChange.TableRenamed(oldName, newName))
            // Remove the corresponding add/remove changes
            changes.removeAll { change ->
                (change is SchemaChange.TableAdded && change.tableName == newName) ||
                (change is SchemaChange.TableRemoved && change.tableName == oldName)
            }
        }
        
        return changes
    }
    
    /**
     * Detect column changes within tables
     */
    private fun detectColumnChanges(
        fromSchema: SchemaVersion,
        toSchema: SchemaVersion
    ): List<SchemaChange> {
        val changes = mutableListOf<SchemaChange>()
        
        // Only check tables that exist in both schemas
        val commonTables = fromSchema.getTableNames().intersect(toSchema.getTableNames())
        
        commonTables.forEach { tableName ->
            val fromTable = fromSchema.getTable(tableName)!!
            val toTable = toSchema.getTable(tableName)!!
            
            val fromColumns = fromTable.columns.associateBy { it.columnName }
            val toColumns = toTable.columns.associateBy { it.columnName }
            
            // Detect added columns
            val addedColumnNames = toColumns.keys - fromColumns.keys
            addedColumnNames.forEach { columnName ->
                val column = toColumns[columnName]!!
                changes.add(SchemaChange.ColumnAdded(tableName, columnName, column))
            }
            
            // Detect removed columns
            val removedColumnNames = fromColumns.keys - toColumns.keys
            removedColumnNames.forEach { columnName ->
                val column = fromColumns[columnName]!!
                changes.add(SchemaChange.ColumnRemoved(tableName, columnName, column))
            }
            
            // Detect changed columns
            val commonColumns = fromColumns.keys.intersect(toColumns.keys)
            commonColumns.forEach { columnName ->
                val fromColumn = fromColumns[columnName]!!
                val toColumn = toColumns[columnName]!!
                
                // Check type changes
                if (fromColumn.sqlType != toColumn.sqlType) {
                    val isCompatible = isTypeChangeCompatible(fromColumn.sqlType, toColumn.sqlType)
                    changes.add(SchemaChange.ColumnTypeChanged(
                        tableName = tableName,
                        columnName = columnName,
                        oldType = fromColumn.sqlType,
                        newType = toColumn.sqlType,
                        isCompatible = isCompatible
                    ))
                }
                
                // Check nullability changes
                if (fromColumn.isNullable != toColumn.isNullable) {
                    changes.add(SchemaChange.ColumnNullabilityChanged(
                        tableName = tableName,
                        columnName = columnName,
                        wasNullable = fromColumn.isNullable,
                        isNullable = toColumn.isNullable
                    ))
                }
            }
        }
        
        return changes
    }
    
    /**
     * Detect relationship changes
     */
    private fun detectRelationshipChanges(
        fromSchema: SchemaVersion,
        toSchema: SchemaVersion
    ): List<SchemaChange> {
        val changes = mutableListOf<SchemaChange>()
        
        val fromRelationships = fromSchema.relationships.relationships.toSet()
        val toRelationships = toSchema.relationships.relationships.toSet()
        
        // Detect added relationships
        val addedRelationships = toRelationships - fromRelationships
        addedRelationships.forEach { relationship ->
            changes.add(SchemaChange.RelationshipAdded(relationship))
        }
        
        // Detect removed relationships
        val removedRelationships = fromRelationships - toRelationships
        removedRelationships.forEach { relationship ->
            changes.add(SchemaChange.RelationshipRemoved(relationship))
        }
        
        return changes
    }
    
    /**
     * Detect potential table renames based on structural similarity
     */
    private fun detectTableRenames(
        removedTables: List<SQLTableInfo>,
        addedTables: List<SQLTableInfo>
    ): List<Pair<String, String>> {
        val renames = mutableListOf<Pair<String, String>>()
        
        removedTables.forEach { removedTable ->
            addedTables.forEach { addedTable ->
                val similarity = calculateTableSimilarity(removedTable, addedTable)
                if (similarity > 0.8) { // 80% similarity threshold
                    renames.add(removedTable.tableName to addedTable.tableName)
                }
            }
        }
        
        return renames
    }
    
    /**
     * Calculate structural similarity between two tables
     */
    private fun calculateTableSimilarity(table1: SQLTableInfo, table2: SQLTableInfo): Double {
        val columns1 = table1.columns.map { "${it.columnName}:${it.sqlType}" }.toSet()
        val columns2 = table2.columns.map { "${it.columnName}:${it.sqlType}" }.toSet()
        
        val intersection = columns1.intersect(columns2)
        val union = columns1.union(columns2)
        
        return if (union.isEmpty()) 0.0 else intersection.size.toDouble() / union.size
    }
    
    /**
     * Check if a type change is backward compatible
     */
    private fun isTypeChangeCompatible(fromType: String, toType: String): Boolean {
        val fromTypeNormalized = fromType.uppercase()
        val toTypeNormalized = toType.uppercase()
        
        return when {
            // Same type is always compatible
            fromTypeNormalized == toTypeNormalized -> true
            
            // Widening integer types is compatible
            fromTypeNormalized.startsWith("TINYINT") && toTypeNormalized.startsWith("SMALLINT") -> true
            fromTypeNormalized.startsWith("TINYINT") && toTypeNormalized.startsWith("INT") -> true
            fromTypeNormalized.startsWith("SMALLINT") && toTypeNormalized.startsWith("INT") -> true
            fromTypeNormalized.startsWith("INT") && toTypeNormalized.startsWith("BIGINT") -> true
            
            // Widening floating point types is compatible
            fromTypeNormalized.startsWith("FLOAT") && toTypeNormalized.startsWith("DOUBLE") -> true
            
            // Widening string types is compatible (if size increases)
            isStringWidening(fromType, toType) -> true
            
            // Everything else is potentially incompatible
            else -> false
        }
    }
    
    /**
     * Check if string type change represents widening (safe)
     */
    private fun isStringWidening(fromType: String, toType: String): Boolean {
        val fromSize = extractVarcharSize(fromType)
        val toSize = extractVarcharSize(toType)
        
        return when {
            fromSize == null || toSize == null -> false
            toSize > fromSize -> true
            fromType.uppercase().startsWith("VARCHAR") && toType.uppercase().startsWith("TEXT") -> true
            else -> false
        }
    }
    
    /**
     * Extract size from VARCHAR(n) types
     */
    private fun extractVarcharSize(type: String): Int? {
        val regex = Regex("""VARCHAR\((\d+)\)""", RegexOption.IGNORE_CASE)
        return regex.find(type)?.groupValues?.get(1)?.toInt()
    }
    
    /**
     * Determine if changes maintain backward compatibility
     */
    private fun determineBackwardCompatibility(changes: List<SchemaChange>): Boolean {
        return changes.none { it.severity == ChangeSeverity.MAJOR }
    }
    
    /**
     * Determine recommended version bump based on changes
     */
    private fun determineVersionBump(changes: List<SchemaChange>): ChangeSeverity {
        return when {
            changes.any { it.severity == ChangeSeverity.MAJOR } -> ChangeSeverity.MAJOR
            changes.any { it.severity == ChangeSeverity.MINOR } -> ChangeSeverity.MINOR
            changes.any { it.severity == ChangeSeverity.PATCH } -> ChangeSeverity.PATCH
            else -> ChangeSeverity.PATCH // Default to patch if no changes
        }
    }
    
    /**
     * Validate schema constraints and detect potential issues
     */
    fun validateSchemaConstraints(schema: SchemaVersion): List<SchemaValidationIssue> {
        val issues = mutableListOf<SchemaValidationIssue>()
        
        // Check for tables without primary keys
        schema.tables.forEach { table ->
            if (!table.hasPrimaryKey) {
                issues.add(SchemaValidationIssue.MissingPrimaryKey(table.tableName))
            }
        }
        
        // Check for foreign key references to non-existent tables
        schema.tables.forEach { table ->
            table.columns.forEach { column ->
                if (column.isLikelyForeignKey) {
                    val referencedTable = column.referencedTableName
                    if (referencedTable != null && schema.getTable(referencedTable) == null) {
                        issues.add(SchemaValidationIssue.BrokenForeignKeyReference(
                            tableName = table.tableName,
                            columnName = column.columnName,
                            referencedTable = referencedTable
                        ))
                    }
                }
            }
        }
        
        // Check for circular dependencies
        val circularDependencies = detectCircularDependencies(schema)
        circularDependencies.forEach { cycle ->
            issues.add(SchemaValidationIssue.CircularDependency(cycle))
        }
        
        return issues
    }
    
    /**
     * Detect circular dependencies in table relationships
     */
    private fun detectCircularDependencies(schema: SchemaVersion): List<List<String>> {
        val dependencies = mutableMapOf<String, MutableSet<String>>()
        
        // Build dependency graph
        schema.relationships.relationships.forEach { relationship ->
            when (relationship) {
                is RelationshipInfo.ManyToOne -> {
                    dependencies.getOrPut(relationship.fromTable) { mutableSetOf() }
                        .add(relationship.toTable)
                }
                is RelationshipInfo.OneToOne -> {
                    dependencies.getOrPut(relationship.fromTable) { mutableSetOf() }
                        .add(relationship.toTable)
                }
                // OneToMany and ManyToMany don't create direct dependencies
                else -> {}
            }
        }
        
        // Find cycles using DFS
        val cycles = mutableListOf<List<String>>()
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()
        
        fun dfs(node: String, path: MutableList<String>): Boolean {
            visited.add(node)
            recursionStack.add(node)
            path.add(node)
            
            dependencies[node]?.forEach { neighbor ->
                if (!visited.contains(neighbor)) {
                    if (dfs(neighbor, path)) return true
                } else if (recursionStack.contains(neighbor)) {
                    // Found cycle
                    val cycleStart = path.indexOf(neighbor)
                    cycles.add(path.subList(cycleStart, path.size))
                    return true
                }
            }
            
            recursionStack.remove(node)
            path.removeAt(path.size - 1)
            return false
        }
        
        schema.getTableNames().forEach { table ->
            if (!visited.contains(table)) {
                dfs(table, mutableListOf())
            }
        }
        
        return cycles
    }
}

/**
 * Issues that can be detected in a schema
 */
sealed class SchemaValidationIssue {
    abstract val severity: IssueSeverity
    abstract val description: String
    
    data class MissingPrimaryKey(
        val tableName: String
    ) : SchemaValidationIssue() {
        override val severity = IssueSeverity.WARNING
        override val description = "Table '$tableName' does not have a primary key"
    }
    
    data class BrokenForeignKeyReference(
        val tableName: String,
        val columnName: String,
        val referencedTable: String
    ) : SchemaValidationIssue() {
        override val severity = IssueSeverity.ERROR
        override val description = "Column '$columnName' in table '$tableName' references non-existent table '$referencedTable'"
    }
    
    data class CircularDependency(
        val cycle: List<String>
    ) : SchemaValidationIssue() {
        override val severity = IssueSeverity.WARNING
        override val description = "Circular dependency detected: ${cycle.joinToString(" -> ")}"
    }
}

enum class IssueSeverity {
    INFO,
    WARNING, 
    ERROR
} 