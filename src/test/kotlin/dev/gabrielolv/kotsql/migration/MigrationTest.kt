package dev.gabrielolv.kotsql.migration

import dev.gabrielolv.kotsql.schema.Migration
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Comprehensive tests for Phase 8 Migration Support
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MigrationTest {
    
    private lateinit var connection: Connection
    private lateinit var tracker: MigrationTracker
    private lateinit var runner: MigrationRunner
    private lateinit var manager: MigrationManager
    private val tempDir = File.createTempFile("migrations", "").apply { 
        delete()
        mkdirs()
    }
    
    @BeforeAll
    fun setup() {
        // Use in-memory H2 database for testing
        connection = DriverManager.getConnection(
            "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            "sa",
            ""
        )
        
        tracker = MigrationTracker(connection)
        runner = MigrationRunner(connection, tracker)
        
        val config = MigrationManager.MigrationManagerConfig(
            migrationDirectory = tempDir.absolutePath
        )
        manager = MigrationManager(connection, config)
    }
    
    @AfterAll
    fun cleanup() {
        connection.close()
        tempDir.deleteRecursively()
    }
    
    @BeforeEach
    fun beforeEach() {
        // Clean up ALL tables before each test to ensure isolation
        try {
            // Get list of all user tables (excluding system tables)
            val tableNames = mutableListOf<String>()
            val tablesQuery = connection.createStatement().executeQuery(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'PUBLIC'"
            )
            while (tablesQuery.next()) {
                tableNames.add(tablesQuery.getString("table_name"))
            }
            
            // Drop all user tables
            for (tableName in tableNames) {
                try {
                    connection.createStatement().execute("DROP TABLE IF EXISTS $tableName CASCADE")
                } catch (e: Exception) {
                    // Ignore individual table drop failures
                }
            }
        } catch (e: Exception) {
            // If we can't clean up tables, at least try to drop the migration table
            try {
                connection.createStatement().execute("DROP TABLE IF EXISTS kotsql_migration_history")
            } catch (ex: Exception) {
                // Ignore
            }
        }
        
        // Clean up temp directory
        tempDir.listFiles()?.forEach { it.delete() }
    }
    
    @Test
    fun `test migration tracker initialization`() {
        tracker.initialize()
        
        // Verify migration history table was created
        val result = connection.createStatement().executeQuery(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'KOTSQL_MIGRATION_HISTORY'"
        )
        result.next()
        assertEquals(1, result.getInt(1))
    }
    
    @Test
    fun `test migration tracking lifecycle`() {
        tracker.initialize()
        
        val migration = createTestMigration("test_migration")
        
        // Initially not applied
        assertFalse(tracker.isMigrationApplied(migration.name))
        
        // Record migration start
        val migrationId = tracker.recordMigrationStart(migration)
        assertTrue(migrationId > 0)
        
        // Record success
        tracker.recordMigrationSuccess(migrationId, 150L)
        
        // Now should be applied
        assertTrue(tracker.isMigrationApplied(migration.name))
        
        // Check migration summary
        val summary = tracker.getMigrationSummary()
        assertEquals(1, summary.totalMigrations)
        assertEquals(1, summary.completedMigrations)
        assertEquals(0, summary.failedMigrations)
    }
    
    @Test
    fun `test migration runner execution`() {
        tracker.initialize()
        
        val migration = Migration(
            name = "create_users_table",
            timestamp = Clock.System.now(),
            fromVersion = "v1.0",
            toVersion = "v1.1",
            upScript = """
                CREATE TABLE users (
                    id BIGINT PRIMARY KEY,
                    name VARCHAR(100) NOT NULL,
                    email VARCHAR(255) UNIQUE
                );
            """.trimIndent(),
            downScript = "DROP TABLE users;",
            changes = emptyList(),
            isReversible = true
        )
        
        println("DEBUG: About to execute migration: ${migration.name}")
        println("DEBUG: Migration script: ${migration.upScript}")
        
        val result = runner.executeMigration(migration)
        
        println("DEBUG: Migration result: success=${result.success}, error=${result.error}")
        
        assertTrue(result.success)
        assertNull(result.error)
        assertTrue(result.executionTimeMs >= 0)
        
        // Check if migration was applied in tracking
        val isApplied = tracker.isMigrationApplied(migration.name)
        println("DEBUG: Migration tracked as applied: $isApplied")
        
        // Verify table was created
        var tableExists = false
        try {
            val testQuery = connection.createStatement().executeQuery("SELECT COUNT(*) FROM users")
            testQuery.close()
            tableExists = true
            println("DEBUG: Table users exists - direct query succeeded")
        } catch (e: Exception) {
            println("DEBUG: Table users does not exist - direct query failed: ${e.message}")
            tableExists = false
        }
        
        // Debug: Let's also see what tables are present according to information_schema
        val allTablesQuery = connection.createStatement().executeQuery(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'PUBLIC'"
        )
        val tableNames = mutableListOf<String>()
        while (allTablesQuery.next()) {
            tableNames.add(allTablesQuery.getString("table_name"))
        }
        println("DEBUG: Tables present in database: $tableNames")
        
        // Check information_schema count for comparison
        val infoSchemaQuery = connection.createStatement().executeQuery(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'USERS'"
        )
        infoSchemaQuery.next()
        val userTableCount = infoSchemaQuery.getInt(1)
        println("DEBUG: USERS table count from information_schema: $userTableCount")
        
        // Let's also check what's in the migration tracking table
        val migrationRecords = connection.createStatement().executeQuery(
            "SELECT name, status FROM kotsql_migration_history"
        )
        println("DEBUG: Migration records:")
        while (migrationRecords.next()) {
            println("  - ${migrationRecords.getString("name")}: ${migrationRecords.getString("status")}")
        }
        
        assertTrue(tableExists)
        
        // Verify migration was tracked
        assertTrue(tracker.isMigrationApplied(migration.name))
    }
    
    @Test
    fun `test migration rollback`() {
        tracker.initialize()
        
        val migration = Migration(
            name = "create_test_table",
            timestamp = Clock.System.now(),
            fromVersion = "v1.0",
            toVersion = "v1.1",
            upScript = "CREATE TABLE test_table (id INT PRIMARY KEY);",
            downScript = "DROP TABLE test_table;",
            changes = emptyList(),
            isReversible = true
        )
        
        // Execute migration
        val executeResult = runner.executeMigration(migration)
        assertTrue(executeResult.success)
        
        // Rollback migration
        val rollbackResult = runner.rollbackMigration(migration)
        assertTrue(rollbackResult.success)
        assertTrue(rollbackResult.rollbackPerformed)
        
        // Verify table was dropped
        val tableExists = connection.createStatement().executeQuery(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'TEST_TABLE'"
        )
        tableExists.next()
        assertEquals(0, tableExists.getInt(1))
    }
    
    @Test
    fun `test batch migration execution`() {
        tracker.initialize()
        
        val migrations = listOf(
            createTestMigration("migration_1", "CREATE TABLE table1 (id INT);"),
            createTestMigration("migration_2", "CREATE TABLE table2 (id INT);"),
            createTestMigration("migration_3", "CREATE TABLE table3 (id INT);")
        )
        
        val result = runner.executeBatch(migrations)
        
        assertTrue(result.overallSuccess)
        assertEquals(3, result.successCount)
        assertEquals(0, result.failureCount)
        assertEquals(3, result.results.size)
        
        // Verify all migrations were tracked
        migrations.forEach { migration ->
            assertTrue(tracker.isMigrationApplied(migration.name))
        }
    }
    
    @Test
    fun `test failed migration handling`() {
        tracker.initialize()
        
        val migration = Migration(
            name = "invalid_migration",
            timestamp = Clock.System.now(),
            fromVersion = "v1.0",
            toVersion = "v1.1",
            upScript = "INVALID SQL SYNTAX HERE;",
            downScript = "",
            changes = emptyList(),
            isReversible = false
        )
        
        val result = runner.executeMigration(migration)
        
        assertFalse(result.success)
        assertNotNull(result.error)
        
        // Verify migration was not tracked as successful
        assertFalse(tracker.isMigrationApplied(migration.name))
        
        // Check that failure was recorded
        val summary = tracker.getMigrationSummary()
        assertEquals(1, summary.failedMigrations)
    }
    
    @Test
    fun `test dry run execution`() {
        tracker.initialize()
        
        val config = MigrationRunner.MigrationConfig(dryRun = true)
        val dryRunRunner = MigrationRunner(connection, tracker, config)
        
        val migration = createTestMigration("dry_run_test", "CREATE TABLE dry_run_table (id INT);")
        
        val result = dryRunRunner.executeMigration(migration)
        
        assertTrue(result.success)
        
        // Verify table was NOT created (dry run)
        val tableExists = connection.createStatement().executeQuery(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'DRY_RUN_TABLE'"
        )
        tableExists.next()
        assertEquals(0, tableExists.getInt(1))
        
        // Verify migration was NOT tracked
        assertFalse(tracker.isMigrationApplied(migration.name))
    }
    
    @Test
    fun `test migration manager status`() {
        manager.initialize()
        
        // Create some test migrations
        createMigrationFile("20240101120000_create_users", 
            "CREATE TABLE users (id INT PRIMARY KEY);",
            "DROP TABLE users;")
        createMigrationFile("20240101130000_create_posts",
            "CREATE TABLE posts (id INT PRIMARY KEY, user_id INT);",
            "DROP TABLE posts;")
        
        val status = manager.getStatus()
        
        assertEquals(2, status.totalMigrations)
        assertEquals(0, status.appliedMigrations)
        assertEquals(2, status.pendingMigrations)
        assertTrue(status.isUpToDate() == false)
    }
    
    @Test
    fun `test migration manager upgrade`() {
        manager.initialize()
        
        // Create test migrations
        createMigrationFile("20240101120000_create_table1",
            "CREATE TABLE table1 (id INT PRIMARY KEY);",
            "DROP TABLE table1;")
        createMigrationFile("20240101130000_create_table2",
            "CREATE TABLE table2 (id INT PRIMARY KEY);",
            "DROP TABLE table2;")
        
        val result = manager.upgrade()
        
        assertTrue(result.overallSuccess)
        assertEquals(2, result.successCount)
        
        // Check status after upgrade
        val status = manager.getStatus()
        assertEquals(2, status.appliedMigrations)
        assertEquals(0, status.pendingMigrations)
        assertTrue(status.isUpToDate())
    }
    
    @Test
    fun `test migration validation`() {
        val validMigration = createTestMigration("valid_migration", "CREATE TABLE test (id INT);")
        val invalidMigration = createTestMigration("invalid_migration", "INVALID SQL;")
        
        val validResult = runner.validateMigration(validMigration)
        assertTrue(validResult.isValid)
        assertTrue(validResult.issues.isEmpty())
        
        val invalidResult = runner.validateMigration(invalidMigration)
        assertFalse(invalidResult.isValid)
        assertTrue(invalidResult.issues.isNotEmpty())
    }
    
    @Test
    fun `test migration manager validation summary`() {
        manager.initialize()
        
        // Create both valid and invalid migrations
        createMigrationFile("20240101120000_valid_migration",
            "CREATE TABLE valid_table (id INT PRIMARY KEY);",
            "DROP TABLE valid_table;")
        createMigrationFile("20240101130000_invalid_migration",
            "INVALID SQL SYNTAX HERE;",
            "")
        
        val summary = manager.validateMigrations()
        
        assertEquals(2, summary.totalMigrations)
        assertEquals(1, summary.validMigrations)
        assertEquals(1, summary.invalidMigrations)
        assertFalse(summary.isAllValid())
        assertTrue(summary.allIssues.isNotEmpty())
    }
    
    @Test
    fun `test transaction rollback on failure`() {
        tracker.initialize()
        
        val config = MigrationRunner.MigrationConfig(
            transactionMode = MigrationRunner.TransactionMode.PER_MIGRATION,
            continueOnError = false
        )
        val transactionalRunner = MigrationRunner(connection, tracker, config)
        
        val migrations = listOf(
            createTestMigration("success_1", "CREATE TABLE success1 (id INT);"),
            createTestMigration("failure", "INVALID SQL;"),
            createTestMigration("success_2", "CREATE TABLE success2 (id INT);")
        )
        
        val result = transactionalRunner.executeBatch(migrations)
        
        assertFalse(result.overallSuccess)
        assertEquals(1, result.successCount) // Only first one should succeed
        assertEquals(1, result.failureCount)
        
        // Verify first table exists but second doesn't (transaction rollback worked)
        val table1Exists = connection.createStatement().executeQuery(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'SUCCESS1'"
        )
        table1Exists.next()
        assertEquals(1, table1Exists.getInt(1))
        
        val table2Exists = connection.createStatement().executeQuery(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'SUCCESS2'"
        )
        table2Exists.next()
        assertEquals(0, table2Exists.getInt(1))
    }
    
    @Test
    fun `test migration history and rollback to version`() {
        manager.initialize()
        
        // Create and apply migrations
        createMigrationFile("20240101120000_v1.0_v1.1",
            "CREATE TABLE v1_table (id INT);",
            "DROP TABLE v1_table;")
        createMigrationFile("20240101130000_v1.1_v1.2",
            "CREATE TABLE v2_table (id INT);", 
            "DROP TABLE v2_table;")
        
        manager.upgrade()
        
        // Check history
        val history = manager.getHistory()
        assertEquals(2, history.size)
        
        // Rollback to v1.1
        val rollbackResult = manager.rollbackTo("v1.1")
        assertTrue(rollbackResult.overallSuccess)
        
        // Verify v2_table was dropped but v1_table remains
        val v1Exists = connection.createStatement().executeQuery(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'V1_TABLE'"
        )
        v1Exists.next()
        assertEquals(1, v1Exists.getInt(1))
        
        val v2Exists = connection.createStatement().executeQuery(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'V2_TABLE'"
        )
        v2Exists.next()
        assertEquals(0, v2Exists.getInt(1))
    }
    
    @Test
    fun `test pending migrations detection`() {
        tracker.initialize()
        
        val allMigrations = listOf(
            createTestMigration("migration_1"),
            createTestMigration("migration_2"),
            createTestMigration("migration_3")
        )
        
        // Apply first migration manually
        runner.executeMigration(allMigrations[0])
        
        val pendingMigrations = tracker.getPendingMigrations(allMigrations)
        
        assertEquals(2, pendingMigrations.size)
        assertEquals("migration_2", pendingMigrations[0].name)
        assertEquals("migration_3", pendingMigrations[1].name)
    }
    
    // Helper methods
    
    private fun createTestMigration(
        name: String,
        upScript: String = "CREATE TABLE test (id INT);",
        downScript: String = "DROP TABLE test;"
    ): Migration {
        return Migration(
            name = name,
            timestamp = Clock.System.now(),
            fromVersion = "v1.0",
            toVersion = "v1.1",
            upScript = upScript,
            downScript = downScript,
            changes = emptyList(),
            isReversible = downScript.isNotEmpty()
        )
    }
    
    private fun createMigrationFile(name: String, upScript: String, downScript: String) {
        val upFile = File(tempDir, "${name}_up.sql")
        upFile.writeText(upScript)
        
        if (downScript.isNotEmpty()) {
            val downFile = File(tempDir, "${name}_down.sql")
            downFile.writeText(downScript)
        }
    }
} 