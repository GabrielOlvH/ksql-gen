package dev.gabrielolv.kotsql.schema

import dev.gabrielolv.kotsql.model.SQLColumnInfo
import dev.gabrielolv.kotsql.util.NamingConventions
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Generates SQL migration scripts based on schema changes.
 * Creates forward and backward migration scripts for schema evolution.
 */
object MigrationGenerator {
    
    /**
     * Generate a complete migration script from schema validation result
     */
    fun generateMigration(
        validationResult: SchemaValidationResult,
        migrationName: String? = null
    ): Migration {
        val name = migrationName ?: generateMigrationName(validationResult)
        val timestamp = Clock.System.now()
        
        val upScript = generateUpScript(validationResult.changes)
        val downScript = generateDownScript(validationResult.changes)
        
        return Migration(
            name = name,
            timestamp = timestamp,
            fromVersion = validationResult.fromVersion.version,
            toVersion = validationResult.toVersion.version,
            upScript = upScript,
            downScript = downScript,
            changes = validationResult.changes,
            isReversible = isReversible(validationResult.changes)
        )
    }
    
    /**
     * Generate the forward migration script (UP)
     */
    private fun generateUpScript(changes: List<SchemaChange>): String {
        return buildString {
            appendLine("-- Forward migration")
            appendLine("-- Generated on ${Clock.System.now()}")
            appendLine()
            
            // Process changes in dependency order
            val orderedChanges = orderChangesForExecution(changes)
            
            orderedChanges.forEach { change ->
                appendLine("-- ${change.description}")
                appendLine(generateUpStatementForChange(change))
                appendLine()
            }
        }
    }
    
    /**
     * Generate the backward migration script (DOWN)
     */
    private fun generateDownScript(changes: List<SchemaChange>): String {
        return buildString {
            appendLine("-- Backward migration")
            appendLine("-- Generated on ${Clock.System.now()}")
            appendLine()
            
            // Process changes in reverse dependency order
            val orderedChanges = orderChangesForExecution(changes).reversed()
            
            orderedChanges.forEach { change ->
                val downStatement = generateDownStatementForChange(change)
                if (downStatement != null) {
                    appendLine("-- Reverse: ${change.description}")
                    appendLine(downStatement)
                    appendLine()
                }
            }
        }
    }
    
    /**
     * Generate SQL statement for forward migration
     */
    private fun generateUpStatementForChange(change: SchemaChange): String {
        return when (change) {
            is SchemaChange.TableAdded -> generateCreateTableStatement(change.table)
            is SchemaChange.TableRemoved -> "DROP TABLE IF EXISTS ${change.tableName};"
            is SchemaChange.TableRenamed -> "ALTER TABLE ${change.oldName} RENAME TO ${change.newName};"
            is SchemaChange.ColumnAdded -> generateAddColumnStatement(change)
            is SchemaChange.ColumnRemoved -> "ALTER TABLE ${change.tableName} DROP COLUMN ${change.columnName};"
            is SchemaChange.ColumnTypeChanged -> generateAlterColumnTypeStatement(change)
            is SchemaChange.ColumnNullabilityChanged -> generateAlterColumnNullabilityStatement(change)
            is SchemaChange.RelationshipAdded -> generateAddForeignKeyStatement(change.relationship)
            is SchemaChange.RelationshipRemoved -> generateDropForeignKeyStatement(change.relationship)
        }
    }
    
    /**
     * Generate SQL statement for backward migration
     */
    private fun generateDownStatementForChange(change: SchemaChange): String? {
        return when (change) {
            is SchemaChange.TableAdded -> "DROP TABLE IF EXISTS ${change.tableName};"
            is SchemaChange.TableRemoved -> generateCreateTableStatement(change.table)
            is SchemaChange.TableRenamed -> "ALTER TABLE ${change.newName} RENAME TO ${change.oldName};"
            is SchemaChange.ColumnAdded -> "ALTER TABLE ${change.tableName} DROP COLUMN ${change.columnName};"
            is SchemaChange.ColumnRemoved -> generateAddColumnStatement(
                SchemaChange.ColumnAdded(change.tableName, change.columnName, change.column)
            )
            is SchemaChange.ColumnTypeChanged -> generateAlterColumnTypeStatement(
                change.copy(oldType = change.newType, newType = change.oldType)
            )
            is SchemaChange.ColumnNullabilityChanged -> generateAlterColumnNullabilityStatement(
                change.copy(wasNullable = change.isNullable, isNullable = change.wasNullable)
            )
            is SchemaChange.RelationshipAdded -> generateDropForeignKeyStatement(change.relationship)
            is SchemaChange.RelationshipRemoved -> generateAddForeignKeyStatement(change.relationship)
        }
    }
    
    /**
     * Generate CREATE TABLE statement
     */
    private fun generateCreateTableStatement(table: dev.gabrielolv.kotsql.model.SQLTableInfo): String {
        return buildString {
            appendLine("CREATE TABLE ${table.tableName} (")
            
            val columnDefinitions = table.columns.map { column ->
                generateColumnDefinition(column)
            }
            
            append(columnDefinitions.joinToString(",\n") { "    $it" })
            
            // Add primary key constraint if exists
            table.primaryKey?.let { pk ->
                append(",\n    PRIMARY KEY (${pk.columnName})")
            }
            
            appendLine()
            appendLine(");")
        }
    }
    
    /**
     * Generate column definition for CREATE TABLE
     */
    private fun generateColumnDefinition(column: SQLColumnInfo): String {
        return buildString {
            append("${column.columnName} ${column.sqlType}")
            
            if (!column.isNullable) {
                append(" NOT NULL")
            }
            
            column.defaultValue?.let { default ->
                append(" DEFAULT $default")
            }
        }
    }
    
    /**
     * Generate ADD COLUMN statement
     */
    private fun generateAddColumnStatement(change: SchemaChange.ColumnAdded): String {
        return buildString {
            append("ALTER TABLE ${change.tableName} ADD COLUMN ")
            append(generateColumnDefinition(change.column))
            append(";")
        }
    }
    
    /**
     * Generate ALTER COLUMN TYPE statement
     */
    private fun generateAlterColumnTypeStatement(change: SchemaChange.ColumnTypeChanged): String {
        // Note: Syntax varies by database. This is a generic approach.
        return "ALTER TABLE ${change.tableName} ALTER COLUMN ${change.columnName} TYPE ${change.newType};"
    }
    
    /**
     * Generate ALTER COLUMN nullability statement
     */
    private fun generateAlterColumnNullabilityStatement(change: SchemaChange.ColumnNullabilityChanged): String {
        val nullConstraint = if (change.isNullable) "DROP NOT NULL" else "SET NOT NULL"
        return "ALTER TABLE ${change.tableName} ALTER COLUMN ${change.columnName} $nullConstraint;"
    }
    
    /**
     * Generate ADD FOREIGN KEY statement
     */
    private fun generateAddForeignKeyStatement(relationship: dev.gabrielolv.kotsql.model.RelationshipInfo): String {
        val constraintName = "fk_${relationship.fromTable}_${relationship.fromColumn}"
        return buildString {
            append("ALTER TABLE ${relationship.fromTable} ")
            append("ADD CONSTRAINT $constraintName ")
            append("FOREIGN KEY (${relationship.fromColumn}) ")
            append("REFERENCES ${relationship.toTable}(${relationship.toColumn});")
        }
    }
    
    /**
     * Generate DROP FOREIGN KEY statement
     */
    private fun generateDropForeignKeyStatement(relationship: dev.gabrielolv.kotsql.model.RelationshipInfo): String {
        val constraintName = "fk_${relationship.fromTable}_${relationship.fromColumn}"
        return "ALTER TABLE ${relationship.fromTable} DROP CONSTRAINT IF EXISTS $constraintName;"
    }
    
    /**
     * Order changes for safe execution (dependencies first)
     */
    private fun orderChangesForExecution(changes: List<SchemaChange>): List<SchemaChange> {
        val ordered = mutableListOf<SchemaChange>()
        
        // 1. Table additions first
        ordered.addAll(changes.filterIsInstance<SchemaChange.TableAdded>())
        
        // 2. Column additions
        ordered.addAll(changes.filterIsInstance<SchemaChange.ColumnAdded>())
        
        // 3. Column type changes
        ordered.addAll(changes.filterIsInstance<SchemaChange.ColumnTypeChanged>())
        
        // 4. Column nullability changes (make nullable first, then non-nullable)
        val nullabilityChanges = changes.filterIsInstance<SchemaChange.ColumnNullabilityChanged>()
        ordered.addAll(nullabilityChanges.filter { !it.wasNullable && it.isNullable }) // Make nullable
        ordered.addAll(nullabilityChanges.filter { it.wasNullable && !it.isNullable }) // Make non-nullable
        
        // 5. Relationship additions
        ordered.addAll(changes.filterIsInstance<SchemaChange.RelationshipAdded>())
        
        // 6. Table renames
        ordered.addAll(changes.filterIsInstance<SchemaChange.TableRenamed>())
        
        // 7. Relationship removals
        ordered.addAll(changes.filterIsInstance<SchemaChange.RelationshipRemoved>())
        
        // 8. Column removals
        ordered.addAll(changes.filterIsInstance<SchemaChange.ColumnRemoved>())
        
        // 9. Table removals last
        ordered.addAll(changes.filterIsInstance<SchemaChange.TableRemoved>())
        
        return ordered
    }
    
    /**
     * Check if changes are reversible
     */
    private fun isReversible(changes: List<SchemaChange>): Boolean {
        return changes.none { change ->
            when (change) {
                // Data-destructive operations are not safely reversible
                is SchemaChange.TableRemoved -> true
                is SchemaChange.ColumnRemoved -> true
                is SchemaChange.ColumnTypeChanged -> !change.isCompatible
                is SchemaChange.ColumnNullabilityChanged -> change.wasNullable && !change.isNullable
                else -> false
            }
        }
    }
    
    /**
     * Generate migration name based on changes
     */
    private fun generateMigrationName(validationResult: SchemaValidationResult): String {
        val changes = validationResult.changes
        val timestamp = Clock.System.now().toString().replace(":", "").replace("-", "").substring(0, 14)
        
        return when {
            changes.any { it is SchemaChange.TableAdded } -> {
                val addedTables = changes.filterIsInstance<SchemaChange.TableAdded>()
                "${timestamp}_add_${addedTables.first().tableName}_table"
            }
            changes.any { it is SchemaChange.ColumnAdded } -> {
                val addedColumns = changes.filterIsInstance<SchemaChange.ColumnAdded>()
                "${timestamp}_add_${addedColumns.first().columnName}_to_${addedColumns.first().tableName}"
            }
            changes.any { it is SchemaChange.TableRenamed } -> {
                val renamed = changes.filterIsInstance<SchemaChange.TableRenamed>().first()
                "${timestamp}_rename_${renamed.oldName}_to_${renamed.newName}"
            }
            else -> "${timestamp}_schema_update"
        }
    }
    
    /**
     * Generate batch migration for multiple schema versions
     */
    fun generateBatchMigration(
        validationResults: List<SchemaValidationResult>,
        batchName: String
    ): BatchMigration {
        val migrations = validationResults.map { result ->
            generateMigration(result)
        }
        
        return BatchMigration(
            name = batchName,
            timestamp = Clock.System.now(),
            migrations = migrations,
            totalChanges = migrations.sumOf { it.changes.size }
        )
    }
}

/**
 * Represents a single migration script
 */
data class Migration(
    val name: String,
    val timestamp: Instant,
    val fromVersion: String,
    val toVersion: String,
    val upScript: String,
    val downScript: String,
    val changes: List<SchemaChange>,
    val isReversible: Boolean
) {
    /**
     * Get migration summary
     */
    fun getSummary(): String {
        return buildString {
            appendLine("Migration: $name")
            appendLine("From: $fromVersion -> $toVersion")
            appendLine("Changes: ${changes.size}")
            appendLine("Reversible: $isReversible")
            appendLine("Timestamp: $timestamp")
        }
    }
    
    /**
     * Save migration to files
     */
    fun saveToFiles(basePath: String) {
        val upFile = java.io.File("$basePath/${name}_up.sql")
        val downFile = java.io.File("$basePath/${name}_down.sql")
        
        upFile.writeText(upScript)
        downFile.writeText(downScript)
    }
}

/**
 * Represents a batch of migrations
 */
data class BatchMigration(
    val name: String,
    val timestamp: Instant,
    val migrations: List<Migration>,
    val totalChanges: Int
) {
    /**
     * Get batch summary
     */
    fun getSummary(): String {
        return buildString {
            appendLine("Batch Migration: $name")
            appendLine("Migrations: ${migrations.size}")
            appendLine("Total Changes: $totalChanges")
            appendLine("Timestamp: $timestamp")
            appendLine()
            migrations.forEach { migration ->
                appendLine("  - ${migration.name} (${migration.changes.size} changes)")
            }
        }
    }
} 