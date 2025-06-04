package dev.gabrielolv.kotsql.schema

import dev.gabrielolv.kotsql.model.SQLTableInfo
import dev.gabrielolv.kotsql.model.SchemaRelationships
import kotlinx.datetime.Instant

/**
 * Represents a complete database schema at a specific point in time.
 * Includes tables, relationships, and metadata for validation purposes.
 */
data class SchemaVersion(
    val version: String,
    val timestamp: Instant,
    val tables: List<SQLTableInfo>,
    val relationships: SchemaRelationships,
    val description: String? = null,
    val checksum: String? = null
) {
    /**
     * Get table by name
     */
    fun getTable(tableName: String): SQLTableInfo? {
        return tables.find { it.tableName == tableName }
    }
    
    /**
     * Get all table names
     */
    fun getTableNames(): Set<String> {
        return tables.map { it.tableName }.toSet()
    }
    
    /**
     * Get table count
     */
    fun getTableCount(): Int = tables.size
    
    /**
     * Get total column count across all tables
     */
    fun getTotalColumnCount(): Int = tables.sumOf { it.columns.size }
    
    /**
     * Get relationship count
     */
    fun getRelationshipCount(): Int = relationships.relationships.size
}

/**
 * Represents a change between two schema versions
 */
sealed class SchemaChange {
    abstract val type: ChangeType
    abstract val severity: ChangeSeverity
    abstract val description: String
    
    /**
     * Table was added
     */
    data class TableAdded(
        val tableName: String,
        val table: SQLTableInfo
    ) : SchemaChange() {
        override val type = ChangeType.TABLE_ADDED
        override val severity = ChangeSeverity.MINOR
        override val description = "Table '$tableName' was added"
    }
    
    /**
     * Table was removed
     */
    data class TableRemoved(
        val tableName: String,
        val table: SQLTableInfo
    ) : SchemaChange() {
        override val type = ChangeType.TABLE_REMOVED
        override val severity = ChangeSeverity.MAJOR
        override val description = "Table '$tableName' was removed"
    }
    
    /**
     * Table was renamed
     */
    data class TableRenamed(
        val oldName: String,
        val newName: String
    ) : SchemaChange() {
        override val type = ChangeType.TABLE_RENAMED
        override val severity = ChangeSeverity.MAJOR
        override val description = "Table '$oldName' was renamed to '$newName'"
    }
    
    /**
     * Column was added to a table
     */
    data class ColumnAdded(
        val tableName: String,
        val columnName: String,
        val column: dev.gabrielolv.kotsql.model.SQLColumnInfo
    ) : SchemaChange() {
        override val type = ChangeType.COLUMN_ADDED
        override val severity = if (column.isNullable || column.defaultValue != null) {
            ChangeSeverity.MINOR
        } else {
            ChangeSeverity.MAJOR
        }
        override val description = "Column '$columnName' was added to table '$tableName'"
    }
    
    /**
     * Column was removed from a table
     */
    data class ColumnRemoved(
        val tableName: String,
        val columnName: String,
        val column: dev.gabrielolv.kotsql.model.SQLColumnInfo
    ) : SchemaChange() {
        override val type = ChangeType.COLUMN_REMOVED
        override val severity = ChangeSeverity.MAJOR
        override val description = "Column '$columnName' was removed from table '$tableName'"
    }
    
    /**
     * Column type was changed
     */
    data class ColumnTypeChanged(
        val tableName: String,
        val columnName: String,
        val oldType: String,
        val newType: String,
        val isCompatible: Boolean
    ) : SchemaChange() {
        override val type = ChangeType.COLUMN_TYPE_CHANGED
        override val severity = if (isCompatible) ChangeSeverity.MINOR else ChangeSeverity.MAJOR
        override val description = "Column '$columnName' in table '$tableName' type changed from '$oldType' to '$newType'"
    }
    
    /**
     * Column nullability changed
     */
    data class ColumnNullabilityChanged(
        val tableName: String,
        val columnName: String,
        val wasNullable: Boolean,
        val isNullable: Boolean
    ) : SchemaChange() {
        override val type = ChangeType.COLUMN_NULLABILITY_CHANGED
        override val severity = if (!wasNullable && isNullable) {
            ChangeSeverity.MINOR // Making nullable is safe
        } else {
            ChangeSeverity.MAJOR // Making non-nullable is dangerous
        }
        override val description = "Column '$columnName' in table '$tableName' nullability changed from ${if (wasNullable) "nullable" else "non-null"} to ${if (isNullable) "nullable" else "non-null"}"
    }
    
    /**
     * Relationship was added
     */
    data class RelationshipAdded(
        val relationship: dev.gabrielolv.kotsql.model.RelationshipInfo
    ) : SchemaChange() {
        override val type = ChangeType.RELATIONSHIP_ADDED
        override val severity = ChangeSeverity.MINOR
        override val description = "Relationship from '${relationship.fromTable}' to '${relationship.toTable}' was added"
    }
    
    /**
     * Relationship was removed
     */
    data class RelationshipRemoved(
        val relationship: dev.gabrielolv.kotsql.model.RelationshipInfo
    ) : SchemaChange() {
        override val type = ChangeType.RELATIONSHIP_REMOVED
        override val severity = ChangeSeverity.MAJOR
        override val description = "Relationship from '${relationship.fromTable}' to '${relationship.toTable}' was removed"
    }
}

/**
 * Types of schema changes
 */
enum class ChangeType {
    TABLE_ADDED,
    TABLE_REMOVED,
    TABLE_RENAMED,
    COLUMN_ADDED,
    COLUMN_REMOVED,
    COLUMN_TYPE_CHANGED,
    COLUMN_NULLABILITY_CHANGED,
    RELATIONSHIP_ADDED,
    RELATIONSHIP_REMOVED
}

/**
 * Severity levels for schema changes
 */
enum class ChangeSeverity {
    /**
     * Patch-level changes (bug fixes, documentation updates)
     */
    PATCH,
    
    /**
     * Minor changes (new features, backward compatible)
     */
    MINOR,
    
    /**
     * Major changes (breaking changes, backward incompatible)
     */
    MAJOR
}

/**
 * Result of schema validation containing all detected changes
 */
data class SchemaValidationResult(
    val fromVersion: SchemaVersion,
    val toVersion: SchemaVersion,
    val changes: List<SchemaChange>,
    val isBackwardCompatible: Boolean,
    val recommendedVersionBump: ChangeSeverity
) {
    /**
     * Get changes by severity
     */
    fun getChangesBySeverity(severity: ChangeSeverity): List<SchemaChange> {
        return changes.filter { it.severity == severity }
    }
    
    /**
     * Get changes by type
     */
    fun getChangesByType(type: ChangeType): List<SchemaChange> {
        return changes.filter { it.type == type }
    }
    
    /**
     * Check if there are breaking changes
     */
    fun hasBreakingChanges(): Boolean {
        return changes.any { it.severity == ChangeSeverity.MAJOR }
    }
    
    /**
     * Get summary of changes
     */
    fun getSummary(): String {
        val majorChanges = getChangesBySeverity(ChangeSeverity.MAJOR).size
        val minorChanges = getChangesBySeverity(ChangeSeverity.MINOR).size
        val patchChanges = getChangesBySeverity(ChangeSeverity.PATCH).size
        
        return buildString {
            appendLine("Schema validation from ${fromVersion.version} to ${toVersion.version}:")
            appendLine("- Major changes: $majorChanges")
            appendLine("- Minor changes: $minorChanges") 
            appendLine("- Patch changes: $patchChanges")
            appendLine("- Backward compatible: $isBackwardCompatible")
            appendLine("- Recommended version bump: $recommendedVersionBump")
        }
    }
} 