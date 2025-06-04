package dev.gabrielolv.kotsql.migration

import dev.gabrielolv.kotsql.schema.Migration
import dev.gabrielolv.kotsql.schema.MigrationGenerator
import dev.gabrielolv.kotsql.schema.SchemaValidationResult
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * High-level migration management system that orchestrates the entire migration workflow.
 * Provides convenient APIs for common migration operations and system management.
 */
class MigrationManager(
    private val connection: Connection,
    private val config: MigrationManagerConfig = MigrationManagerConfig()
) : AutoCloseable {
    
    private val tracker = MigrationTracker(connection)
    private val runner = MigrationRunner(connection, tracker, config.runnerConfig)
    private var initialized = false
    
    /**
     * Configuration for the migration manager
     */
    data class MigrationManagerConfig(
        val migrationDirectory: String = "migrations",
        val autoInit: Boolean = true,
        val runnerConfig: MigrationRunner.MigrationConfig = MigrationRunner.MigrationConfig(),
        val backupBeforeMigration: Boolean = false,
        val validateIntegrity: Boolean = true
    )
    
    /**
     * Migration discovery and loading
     */
    class MigrationLoader(private val migrationDirectory: String) {
        
        /**
         * Discover and load all migrations from the migration directory
         */
        fun loadMigrations(): List<Migration> {
            val migrationDir = File(migrationDirectory)
            if (!migrationDir.exists() || !migrationDir.isDirectory) {
                return emptyList()
            }
            
            val migrations = mutableListOf<Migration>()
            val migrationFiles = migrationDir.listFiles { file ->
                file.name.endsWith("_up.sql")
            }?.sortedBy { it.name } ?: return emptyList()
            
            for (upFile in migrationFiles) {
                val migrationName = upFile.name.removeSuffix("_up.sql")
                val downFile = File(migrationDir, "${migrationName}_down.sql")
                
                val upScript = upFile.readText()
                val downScript = if (downFile.exists()) downFile.readText() else ""
                val isReversible = downScript.isNotEmpty()
                
                // Parse migration metadata from filename or content
                val timestamp = extractTimestampFromName(migrationName)
                val (fromVersion, toVersion) = extractVersionsFromName(migrationName)
                
                migrations.add(
                    Migration(
                        name = migrationName,
                        timestamp = timestamp,
                        fromVersion = fromVersion,
                        toVersion = toVersion,
                        upScript = upScript,
                        downScript = downScript,
                        changes = emptyList(), // Would be populated from schema analysis
                        isReversible = isReversible
                    )
                )
            }
            
            return migrations
        }
        
        /**
         * Save a migration to the migration directory
         */
        fun saveMigration(migration: Migration) {
            val migrationDir = File(migrationDirectory)
            if (!migrationDir.exists()) {
                migrationDir.mkdirs()
            }
            
            // Save up script
            val upFile = File(migrationDir, "${migration.name}_up.sql")
            upFile.writeText(migration.upScript)
            
            // Save down script if migration is reversible
            if (migration.isReversible) {
                val downFile = File(migrationDir, "${migration.name}_down.sql")
                downFile.writeText(migration.downScript)
            }
        }
        
        private fun extractTimestampFromName(migrationName: String): kotlinx.datetime.Instant {
            // Extract timestamp from migration name (format: YYYYMMDDHHMMSS_description)
            val timestampPart = migrationName.split("_").firstOrNull()
            return try {
                if (timestampPart != null && timestampPart.length >= 14) {
                    val year = timestampPart.substring(0, 4).toInt()
                    val month = timestampPart.substring(4, 6).toInt()
                    val day = timestampPart.substring(6, 8).toInt()
                    val hour = timestampPart.substring(8, 10).toInt()
                    val minute = timestampPart.substring(10, 12).toInt()
                    val second = timestampPart.substring(12, 14).toInt()
                    
                    kotlinx.datetime.LocalDateTime(year, month, day, hour, minute, second)
                        .toInstant(TimeZone.UTC)
                } else {
                    Clock.System.now()
                }
            } catch (e: Exception) {
                Clock.System.now()
            }
        }
        
        private fun extractVersionsFromName(migrationName: String): Pair<String, String> {
            // Simple heuristic: extract versions from migration name if possible
            // In practice, this would be more sophisticated
            val parts = migrationName.split("_")
            return if (parts.size >= 3) {
                Pair(parts[1], parts[2])
            } else {
                Pair("unknown", "unknown")
            }
        }
    }
    
    private val loader = MigrationLoader(config.migrationDirectory)
    
    /**
     * Initialize the migration system
     */
    fun initialize() {
        if (!initialized) {
            tracker.initialize()
            initialized = true
        }
    }
    
    /**
     * Get current migration status
     */
    fun getStatus(): MigrationStatus {
        ensureInitialized()
        
        val allMigrations = loader.loadMigrations()
        val appliedMigrations = tracker.getAppliedMigrations()
        val pendingMigrations = tracker.getPendingMigrations(allMigrations)
        val summary = tracker.getMigrationSummary()
        val currentVersion = tracker.getCurrentSchemaVersion()
        
        return MigrationStatus(
            currentVersion = currentVersion,
            totalMigrations = allMigrations.size,
            appliedMigrations = appliedMigrations.size,
            pendingMigrations = pendingMigrations.size,
            lastMigrationAt = appliedMigrations.maxByOrNull { it.appliedAt }?.appliedAt,
            summary = summary
        )
    }
    
    /**
     * Execute all pending migrations
     */
    fun upgrade(): MigrationRunner.BatchMigrationResult {
        ensureInitialized()
        
        val allMigrations = loader.loadMigrations()
        return runner.executeUpgrade(allMigrations)
    }
    
    /**
     * Execute a specific migration
     */
    fun migrate(migrationName: String): MigrationRunner.MigrationResult {
        ensureInitialized()
        
        val migration = loadMigration(migrationName)
            ?: throw MigrationException("Migration not found: $migrationName")
        
        return runner.executeMigration(migration)
    }
    
    /**
     * Rollback a specific migration
     */
    fun rollback(migrationName: String): MigrationRunner.MigrationResult {
        ensureInitialized()
        
        val migration = loadMigration(migrationName)
            ?: throw MigrationException("Migration not found: $migrationName")
        
        return runner.rollbackMigration(migration)
    }
    
    /**
     * Rollback to a specific version
     */
    fun rollbackTo(targetVersion: String): MigrationRunner.BatchMigrationResult {
        ensureInitialized()
        
        val appliedMigrations = tracker.getAppliedMigrations()
        val migrationsToRollback = appliedMigrations
            .filter { it.toVersion != targetVersion }
            .sortedByDescending { it.appliedAt } // Rollback in reverse order
        
        val migrations = migrationsToRollback.mapNotNull { record ->
            loadMigration(record.name)
        }
        
        val results = mutableListOf<MigrationRunner.MigrationResult>()
        var successCount = 0
        var failureCount = 0
        val startTime = System.currentTimeMillis()
        
        for (migration in migrations) {
            val result = runner.rollbackMigration(migration)
            results.add(result)
            
            if (result.success) {
                successCount++
            } else {
                failureCount++
                // Stop on first failure to maintain consistency
                break
            }
        }
        
        val totalExecutionTime = System.currentTimeMillis() - startTime
        
        return MigrationRunner.BatchMigrationResult(
            results = results,
            successCount = successCount,
            failureCount = failureCount,
            totalExecutionTimeMs = totalExecutionTime,
            overallSuccess = failureCount == 0
        )
    }
    
    /**
     * Validate all migrations
     */
    fun validateMigrations(): ValidationSummary {
        val allMigrations = loader.loadMigrations()
        val results = allMigrations.map { migration ->
            migration.name to runner.validateMigration(migration)
        }.toMap()
        
        val validCount = results.values.count { it.isValid }
        val invalidCount = results.size - validCount
        val allIssues = results.values.flatMap { it.issues }
        
        return ValidationSummary(
            totalMigrations = results.size,
            validMigrations = validCount,
            invalidMigrations = invalidCount,
            results = results,
            allIssues = allIssues
        )
    }
    
    /**
     * Generate a new migration from schema validation result
     */
    fun generateMigration(
        validationResult: SchemaValidationResult,
        migrationName: String? = null
    ): Migration {
        val migration = MigrationGenerator.generateMigration(validationResult, migrationName)
        loader.saveMigration(migration)
        return migration
    }
    
    /**
     * Perform a dry run of all pending migrations
     */
    fun dryRun(): MigrationRunner.BatchMigrationResult {
        ensureInitialized()
        
        val allMigrations = loader.loadMigrations()
        val pendingMigrations = tracker.getPendingMigrations(allMigrations)
        
        val dryRunConfig = config.runnerConfig.copy(dryRun = true)
        val dryRunRunner = MigrationRunner(connection, tracker, dryRunConfig)
        
        return dryRunRunner.executeBatch(pendingMigrations)
    }
    
    /**
     * Get migration history
     */
    fun getHistory(): List<MigrationTracker.MigrationRecord> {
        ensureInitialized()
        return tracker.getAppliedMigrations()
    }
    
    /**
     * Reset migration tracking (dangerous operation)
     */
    fun resetTracking() {
        ensureInitialized()
        
        connection.createStatement().use { statement ->
            statement.execute("DELETE FROM kotsql_migration_history")
        }
    }
    
    /**
     * Export migration scripts to a directory
     */
    fun exportMigrations(exportPath: String) {
        val allMigrations = loader.loadMigrations()
        val exportDir = File(exportPath)
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }
        
        allMigrations.forEach { migration ->
            migration.saveToFiles(exportPath)
        }
    }
    
    /**
     * Load a specific migration by name
     */
    private fun loadMigration(migrationName: String): Migration? {
        return loader.loadMigrations().find { it.name == migrationName }
    }
    
    /**
     * Ensure the migration system is initialized
     */
    private fun ensureInitialized() {
        if (!initialized && config.autoInit) {
            initialize()
        }
        
        if (!initialized) {
            throw IllegalStateException("Migration system not initialized. Call initialize() first.")
        }
    }
    
    override fun close() {
        // Cleanup resources if needed
        // The connection is managed externally, so we don't close it here
    }
    
    companion object {
        /**
         * Create a migration manager with a JDBC connection string
         */
        fun create(
            jdbcUrl: String,
            username: String? = null,
            password: String? = null,
            config: MigrationManagerConfig = MigrationManagerConfig()
        ): MigrationManager {
            val connection = if (username != null && password != null) {
                DriverManager.getConnection(jdbcUrl, username, password)
            } else {
                DriverManager.getConnection(jdbcUrl)
            }
            
            return MigrationManager(connection, config)
        }
        
        /**
         * Create a migration manager with default configuration
         */
        fun createDefault(connection: Connection): MigrationManager {
            return MigrationManager(connection)
        }
    }
}

/**
 * Current migration system status
 */
data class MigrationStatus(
    val currentVersion: String?,
    val totalMigrations: Int,
    val appliedMigrations: Int,
    val pendingMigrations: Int,
    val lastMigrationAt: kotlinx.datetime.Instant?,
    val summary: MigrationSummary
) {
    fun isUpToDate(): Boolean = pendingMigrations == 0
    
    fun getCompletionPercentage(): Double {
        return if (totalMigrations > 0) {
            appliedMigrations.toDouble() / totalMigrations.toDouble() * 100.0
        } else {
            100.0
        }
    }
    
    override fun toString(): String = buildString {
        appendLine("Migration Status:")
        appendLine("  Current version: ${currentVersion ?: "None"}")
        appendLine("  Progress: $appliedMigrations/$totalMigrations (${String.format("%.1f", getCompletionPercentage())}%)")
        appendLine("  Pending migrations: $pendingMigrations")
        appendLine("  Up to date: ${isUpToDate()}")
        lastMigrationAt?.let { 
            appendLine("  Last migration: $it")
        }
        appendLine()
        append(summary.toString())
    }
}

/**
 * Migration validation summary
 */
data class ValidationSummary(
    val totalMigrations: Int,
    val validMigrations: Int,
    val invalidMigrations: Int,
    val results: Map<String, MigrationRunner.ValidationResult>,
    val allIssues: List<String>
) {
    fun isAllValid(): Boolean = invalidMigrations == 0
    
    fun getValidationRate(): Double {
        return if (totalMigrations > 0) {
            validMigrations.toDouble() / totalMigrations.toDouble() * 100.0
        } else {
            100.0
        }
    }
    
    override fun toString(): String = buildString {
        appendLine("Migration Validation Summary:")
        appendLine("  Total migrations: $totalMigrations")
        appendLine("  Valid: $validMigrations")
        appendLine("  Invalid: $invalidMigrations")
        appendLine("  Validation rate: ${String.format("%.1f", getValidationRate())}%")
        
        if (allIssues.isNotEmpty()) {
            appendLine("  Issues found:")
            allIssues.forEach { issue ->
                appendLine("    - $issue")
            }
        }
    }
} 