package dev.gabrielolv.kotsql.migration

import dev.gabrielolv.kotsql.schema.Migration
import kotlinx.datetime.Clock
import java.sql.Connection
import java.sql.SQLException
import java.sql.Savepoint

/**
 * Executes database migrations safely with transaction support and rollback capabilities.
 * Provides comprehensive migration execution workflow with proper error handling.
 */
class MigrationRunner(
    private val connection: Connection,
    private val tracker: MigrationTracker,
    private val config: MigrationConfig = MigrationConfig()
) {
    
    /**
     * Configuration for migration execution
     */
    data class MigrationConfig(
        val validateChecksums: Boolean = true,
        val dryRun: Boolean = false,
        val continueOnError: Boolean = false,
        val transactionMode: TransactionMode = TransactionMode.PER_MIGRATION,
        val timeoutSeconds: Int = 300, // 5 minutes default
        val maxRetries: Int = 0
    )
    
    /**
     * Transaction mode for migration execution
     */
    enum class TransactionMode {
        PER_MIGRATION,    // Each migration in its own transaction
        BATCH,           // All migrations in a single transaction
        AUTO_COMMIT      // No transaction management (use connection's auto-commit)
    }
    
    /**
     * Result of migration execution
     */
    data class MigrationResult(
        val migration: Migration,
        val success: Boolean,
        val executionTimeMs: Long,
        val error: Throwable? = null,
        val rollbackPerformed: Boolean = false
    )
    
    /**
     * Batch execution result
     */
    data class BatchMigrationResult(
        val results: List<MigrationResult>,
        val successCount: Int,
        val failureCount: Int,
        val totalExecutionTimeMs: Long,
        val overallSuccess: Boolean
    ) {
        fun getSuccessRate(): Double = if (results.isNotEmpty()) successCount.toDouble() / results.size else 0.0
        
        fun getSummary(): String = buildString {
            appendLine("Batch Migration Results:")
            appendLine("  Total migrations: ${results.size}")
            appendLine("  Successful: $successCount")
            appendLine("  Failed: $failureCount")
            appendLine("  Success rate: ${String.format("%.1f", getSuccessRate() * 100)}%")
            appendLine("  Total execution time: ${totalExecutionTimeMs}ms")
            appendLine("  Overall success: $overallSuccess")
        }
    }
    
    /**
     * Execute a single migration
     */
    fun executeMigration(migration: Migration): MigrationResult {
        if (config.dryRun) {
            return executeDryRun(migration)
        }
        
        val startTime = System.currentTimeMillis()
        var migrationId: Long? = null
        var savepoint: Savepoint? = null
        
        try {
            // Check if migration is already applied
            if (tracker.isMigrationApplied(migration.name)) {
                return MigrationResult(
                    migration = migration,
                    success = true,
                    executionTimeMs = 0,
                    error = IllegalStateException("Migration ${migration.name} is already applied")
                )
            }
            
            // Record migration start BEFORE starting transaction to ensure it's not rolled back
            migrationId = try {
                tracker.recordMigrationStart(migration)
            } catch (e: Exception) {
                // If we can't record the start, we can't proceed safely
                throw MigrationException("Failed to record migration start: ${e.message}", e)
            }
            
            // Start transaction if needed
            val originalAutoCommit = connection.autoCommit
            if (config.transactionMode == TransactionMode.PER_MIGRATION) {
                connection.autoCommit = false
                // Use a simple savepoint name without special characters
                savepoint = connection.setSavepoint("mig_${migration.name.hashCode().toString().replace("-", "n")}")
            }
            
            try {
                // Set statement timeout
                val statement = connection.createStatement()
                statement.queryTimeout = config.timeoutSeconds
                
                // Execute the migration script
                val scriptStatements = parseStatements(migration.upScript)
                for (sql in scriptStatements) {
                    if (sql.trim().isNotEmpty()) {
                        statement.execute(sql)
                    }
                }
                
                statement.close()
                
                // Commit transaction if we manage it
                if (config.transactionMode == TransactionMode.PER_MIGRATION) {
                    connection.commit()
                    connection.autoCommit = originalAutoCommit
                }
                
                val executionTime = System.currentTimeMillis() - startTime
                
                // Record successful completion
                tracker.recordMigrationSuccess(migrationId, executionTime)
                
                return MigrationResult(
                    migration = migration,
                    success = true,
                    executionTimeMs = executionTime
                )
                
            } catch (e: Exception) {
                // Rollback transaction if we manage it
                if (config.transactionMode == TransactionMode.PER_MIGRATION && savepoint != null) {
                    try {
                        connection.rollback(savepoint)
                        connection.autoCommit = originalAutoCommit
                    } catch (rollbackEx: Exception) {
                        // Log rollback failure but don't mask original exception
                        println("Warning: Failed to rollback migration ${migration.name}: ${rollbackEx.message}")
                    }
                }
                
                val executionTime = System.currentTimeMillis() - startTime
                
                // Record failure - this happens outside the migration transaction so it won't be rolled back
                try {
                    tracker.recordMigrationFailure(migrationId, executionTime)
                } catch (trackingEx: Exception) {
                    // If tracking fails, at least log it
                    println("Warning: Failed to record migration failure for ${migration.name}: ${trackingEx.message}")
                }
                
                return MigrationResult(
                    migration = migration,
                    success = false,
                    executionTimeMs = executionTime,
                    error = e,
                    rollbackPerformed = savepoint != null
                )
            }
            
        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            
            // Try to record the failure even if it's a very early failure
            if (migrationId != null) {
                try {
                    tracker.recordMigrationFailure(migrationId, executionTime)
                } catch (trackingEx: Exception) {
                    println("Warning: Failed to record migration failure for ${migration.name}: ${trackingEx.message}")
                }
            } else {
                // If we couldn't even record the start, try to do it now
                try {
                    val failedMigrationId = tracker.recordMigrationStart(migration)
                    tracker.recordMigrationFailure(failedMigrationId, executionTime)
                } catch (trackingEx: Exception) {
                    println("Warning: Failed to record migration failure for ${migration.name}: ${trackingEx.message}")
                }
            }
            
            return MigrationResult(
                migration = migration,
                success = false,
                executionTimeMs = executionTime,
                error = e
            )
        }
    }
    
    /**
     * Execute multiple migrations in batch
     */
    fun executeBatch(migrations: List<Migration>): BatchMigrationResult {
        val startTime = System.currentTimeMillis()
        val results = mutableListOf<MigrationResult>()
        var successCount = 0
        var failureCount = 0
        var batchSavepoint: Savepoint? = null
        
        try {
            // Start batch transaction if needed
            val originalAutoCommit = connection.autoCommit
            if (config.transactionMode == TransactionMode.BATCH) {
                connection.autoCommit = false
                batchSavepoint = connection.setSavepoint("batch_mig")
            }
            
            for (migration in migrations) {
                val result = if (config.transactionMode == TransactionMode.BATCH) {
                    // Execute without individual transaction management
                    executeMigrationInBatch(migration)
                } else {
                    executeMigration(migration)
                }
                
                results.add(result)
                
                if (result.success) {
                    successCount++
                } else {
                    failureCount++
                    
                    // Stop on first failure if not configured to continue
                    if (!config.continueOnError) {
                        break
                    }
                }
            }
            
            // Commit batch transaction if all succeeded
            if (config.transactionMode == TransactionMode.BATCH) {
                if (failureCount == 0 || config.continueOnError) {
                    connection.commit()
                } else {
                    connection.rollback(batchSavepoint)
                    // Mark all results as rolled back
                    results.forEach { result ->
                        if (result.success) {
                            results[results.indexOf(result)] = result.copy(rollbackPerformed = true)
                        }
                    }
                }
                connection.autoCommit = originalAutoCommit
            }
            
        } catch (e: Exception) {
            // Rollback entire batch on critical failure
            if (config.transactionMode == TransactionMode.BATCH && batchSavepoint != null) {
                try {
                    connection.rollback(batchSavepoint)
                } catch (rollbackEx: Exception) {
                    println("Warning: Failed to rollback batch migration: ${rollbackEx.message}")
                }
            }
            
            // Add error result for any unprocessed migrations
            throw MigrationException("Batch migration failed: ${e.message}", e)
        }
        
        val totalExecutionTime = System.currentTimeMillis() - startTime
        
        return BatchMigrationResult(
            results = results,
            successCount = successCount,
            failureCount = failureCount,
            totalExecutionTimeMs = totalExecutionTime,
            overallSuccess = failureCount == 0
        )
    }
    
    /**
     * Execute migration within a batch (no individual transaction management)
     */
    private fun executeMigrationInBatch(migration: Migration): MigrationResult {
        val startTime = System.currentTimeMillis()
        var migrationId: Long? = null
        
        try {
            // Check if migration is already applied
            if (tracker.isMigrationApplied(migration.name)) {
                return MigrationResult(
                    migration = migration,
                    success = true,
                    executionTimeMs = 0
                )
            }
            
            // Record migration start
            migrationId = tracker.recordMigrationStart(migration)
            
            // Execute the migration script
            val statement = connection.createStatement()
            statement.queryTimeout = config.timeoutSeconds
            
            val scriptStatements = parseStatements(migration.upScript)
            for (sql in scriptStatements) {
                if (sql.trim().isNotEmpty()) {
                    statement.execute(sql)
                }
            }
            
            statement.close()
            
            val executionTime = System.currentTimeMillis() - startTime
            
            // Record successful completion
            tracker.recordMigrationSuccess(migrationId, executionTime)
            
            return MigrationResult(
                migration = migration,
                success = true,
                executionTimeMs = executionTime
            )
            
        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            
            // Record failure if we have a migration ID
            migrationId?.let { 
                try {
                    tracker.recordMigrationFailure(it, executionTime)
                } catch (trackingEx: Exception) {
                    println("Warning: Failed to record migration failure for ${migration.name}: ${trackingEx.message}")
                }
            }
            
            return MigrationResult(
                migration = migration,
                success = false,
                executionTimeMs = executionTime,
                error = e
            )
        }
    }
    
    /**
     * Execute a dry run (validation only, no actual execution)
     */
    private fun executeDryRun(migration: Migration): MigrationResult {
        val startTime = System.currentTimeMillis()
        
        try {
            // Validate SQL syntax by preparing statements
            val scriptStatements = parseStatements(migration.upScript)
            for (sql in scriptStatements) {
                if (sql.trim().isNotEmpty()) {
                    try {
                        connection.prepareStatement(sql).close()
                    } catch (e: SQLException) {
                        throw MigrationException("SQL syntax error in migration ${migration.name}: ${e.message}", e)
                    }
                }
            }
            
            val executionTime = System.currentTimeMillis() - startTime
            
            return MigrationResult(
                migration = migration,
                success = true,
                executionTimeMs = executionTime
            )
            
        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            
            return MigrationResult(
                migration = migration,
                success = false,
                executionTimeMs = executionTime,
                error = e
            )
        }
    }
    
    /**
     * Rollback a migration using its down script
     */
    fun rollbackMigration(migration: Migration): MigrationResult {
        if (!migration.isReversible) {
            return MigrationResult(
                migration = migration,
                success = false,
                executionTimeMs = 0,
                error = MigrationException("Migration ${migration.name} is not reversible")
            )
        }
        
        val startTime = System.currentTimeMillis()
        var savepoint: Savepoint? = null
        
        try {
            // Start transaction
            val originalAutoCommit = connection.autoCommit
            connection.autoCommit = false
            // Use a simple savepoint name without special characters  
            savepoint = connection.setSavepoint("rb_${migration.name.hashCode().toString().replace("-", "n")}")
            
            // Execute down script
            val statement = connection.createStatement()
            statement.queryTimeout = config.timeoutSeconds
            
            val scriptStatements = parseStatements(migration.downScript)
            for (sql in scriptStatements) {
                if (sql.trim().isNotEmpty()) {
                    statement.execute(sql)
                }
            }
            
            statement.close()
            
            // Commit rollback
            connection.commit()
            connection.autoCommit = originalAutoCommit
            
            // Record rollback in tracking system
            tracker.recordMigrationRollback(migration.name)
            
            val executionTime = System.currentTimeMillis() - startTime
            
            return MigrationResult(
                migration = migration,
                success = true,
                executionTimeMs = executionTime,
                rollbackPerformed = true
            )
            
        } catch (e: Exception) {
            // Rollback the rollback attempt
            if (savepoint != null) {
                try {
                    connection.rollback(savepoint)
                } catch (rollbackEx: Exception) {
                    println("Warning: Failed to rollback failed rollback attempt: ${rollbackEx.message}")
                }
            }
            
            val executionTime = System.currentTimeMillis() - startTime
            
            return MigrationResult(
                migration = migration,
                success = false,
                executionTimeMs = executionTime,
                error = e,
                rollbackPerformed = false
            )
        }
    }
    
    /**
     * Execute pending migrations automatically
     */
    fun executeUpgrade(availableMigrations: List<Migration>): BatchMigrationResult {
        val pendingMigrations = tracker.getPendingMigrations(availableMigrations)
        
        if (pendingMigrations.isEmpty()) {
            return BatchMigrationResult(
                results = emptyList(),
                successCount = 0,
                failureCount = 0,
                totalExecutionTimeMs = 0,
                overallSuccess = true
            )
        }
        
        // Sort migrations by timestamp to ensure proper order
        val sortedMigrations = pendingMigrations.sortedBy { it.timestamp }
        
        return executeBatch(sortedMigrations)
    }
    
    /**
     * Parse SQL script into individual statements
     */
    private fun parseStatements(script: String): List<String> {
        // Simple statement splitting - in production, you might want a more sophisticated parser
        return script.split(';')
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("--") }
    }
    
    /**
     * Validate migration integrity
     */
    fun validateMigration(migration: Migration): ValidationResult {
        val issues = mutableListOf<String>()
        
        // Check if migration name is valid
        if (migration.name.isBlank()) {
            issues.add("Migration name cannot be blank")
        }
        
        // Check if up script exists
        if (migration.upScript.isBlank()) {
            issues.add("Migration up script cannot be blank")
        }
        
        // Check if down script exists for reversible migrations
        if (migration.isReversible && migration.downScript.isBlank()) {
            issues.add("Reversible migration must have a down script")
        }
        
        // Validate SQL syntax by actually trying to prepare statements
        try {
            val upStatements = parseStatements(migration.upScript)
            for (sql in upStatements) {
                if (sql.trim().isNotEmpty()) {
                    try {
                        connection.prepareStatement(sql).use { /* Just validate syntax */ }
                    } catch (e: Exception) {
                        issues.add("Invalid SQL syntax in up script: ${e.message}")
                        break // Stop on first error
                    }
                }
            }
        } catch (e: Exception) {
            issues.add("Failed to parse up script: ${e.message}")
        }
        
        if (migration.isReversible) {
            try {
                val downStatements = parseStatements(migration.downScript)
                for (sql in downStatements) {
                    if (sql.trim().isNotEmpty()) {
                        try {
                            connection.prepareStatement(sql).use { /* Just validate syntax */ }
                        } catch (e: Exception) {
                            issues.add("Invalid SQL syntax in down script: ${e.message}")
                            break // Stop on first error
                        }
                    }
                }
            } catch (e: Exception) {
                issues.add("Failed to parse down script: ${e.message}")
            }
        }
        
        return ValidationResult(issues.isEmpty(), issues)
    }
    
    /**
     * Migration validation result
     */
    data class ValidationResult(
        val isValid: Boolean,
        val issues: List<String>
    ) {
        fun getSummary(): String = buildString {
            appendLine("Migration Validation:")
            appendLine("  Valid: $isValid")
            if (issues.isNotEmpty()) {
                appendLine("  Issues:")
                issues.forEach { issue ->
                    appendLine("    - $issue")
                }
            }
        }
    }
} 