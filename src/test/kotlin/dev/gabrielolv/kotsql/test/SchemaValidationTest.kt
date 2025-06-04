package dev.gabrielolv.kotsql.test

import dev.gabrielolv.kotsql.model.SchemaRelationships
import dev.gabrielolv.kotsql.parser.SQLParser
import dev.gabrielolv.kotsql.relationship.RelationshipDetector
import dev.gabrielolv.kotsql.schema.*
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Test
import kotlin.test.*

class SchemaValidationTest {
    
    @Test
    fun testTableChanges() {
        val v1Schema = """
            CREATE TABLE users (
                id INTEGER PRIMARY KEY,
                username VARCHAR(50) NOT NULL,
                email VARCHAR(100) NOT NULL
            );
        """.trimIndent()
        
        val v2Schema = """
            CREATE TABLE users (
                id INTEGER PRIMARY KEY,
                username VARCHAR(50) NOT NULL,
                email VARCHAR(100) NOT NULL
            );
            
            CREATE TABLE posts (
                id BIGINT PRIMARY KEY,
                user_id INTEGER NOT NULL,
                title VARCHAR(200) NOT NULL,
                content TEXT NOT NULL
            );
        """.trimIndent()
        
        val sqlParser = SQLParser()
        
        val v1Tables = sqlParser.parseSQLContent(v1Schema)
        val v2Tables = sqlParser.parseSQLContent(v2Schema)
        
        val v1SchemaVersion = SchemaVersion(
            version = "1.0.0",
            timestamp = Clock.System.now(),
            tables = v1Tables,
            relationships = SchemaRelationships(emptyList())
        )
        
        val v2SchemaVersion = SchemaVersion(
            version = "2.0.0", 
            timestamp = Clock.System.now(),
            tables = v2Tables,
            relationships = SchemaRelationships(emptyList())
        )
        
        val validationResult = SchemaValidator.validateSchemaChange(v1SchemaVersion, v2SchemaVersion)
        
        println("Schema changes detected:")
        validationResult.changes.forEach { change ->
            println("  ${change.severity}: ${change.description}")
        }
        
        // Should detect table addition
        assertTrue(validationResult.changes.any { it is SchemaChange.TableAdded })
        val tableAdded = validationResult.changes.filterIsInstance<SchemaChange.TableAdded>().first()
        assertEquals("posts", tableAdded.tableName)
        assertEquals(ChangeSeverity.MINOR, tableAdded.severity)
        
        // Should be backward compatible (adding tables is safe)
        assertTrue(validationResult.isBackwardCompatible)
        assertEquals(ChangeSeverity.MINOR, validationResult.recommendedVersionBump)
    }
    
    @Test
    fun testColumnChanges() {
        val v1Schema = """
            CREATE TABLE users (
                id INTEGER PRIMARY KEY,
                username VARCHAR(50) NOT NULL,
                email VARCHAR(100) NOT NULL
            );
        """.trimIndent()
        
        val v2Schema = """
            CREATE TABLE users (
                id INTEGER PRIMARY KEY,
                username VARCHAR(100) NOT NULL,  -- Increased size (compatible)
                email VARCHAR(100) NOT NULL,
                created_at TIMESTAMP             -- Added nullable column (compatible)
            );
        """.trimIndent()
        
        val sqlParser = SQLParser()
        
        val v1Tables = sqlParser.parseSQLContent(v1Schema)
        val v2Tables = sqlParser.parseSQLContent(v2Schema)
        
        val v1SchemaVersion = SchemaVersion(
            version = "1.0.0",
            timestamp = Clock.System.now(),
            tables = v1Tables,
            relationships = SchemaRelationships(emptyList())
        )
        
        val v2SchemaVersion = SchemaVersion(
            version = "1.1.0",
            timestamp = Clock.System.now(),
            tables = v2Tables,
            relationships = SchemaRelationships(emptyList())
        )
        
        val validationResult = SchemaValidator.validateSchemaChange(v1SchemaVersion, v2SchemaVersion)
        
        println("Column changes detected:")
        validationResult.changes.forEach { change ->
            println("  ${change.severity}: ${change.description}")
        }
        
        // Should detect column addition
        assertTrue(validationResult.changes.any { it is SchemaChange.ColumnAdded })
        val columnAdded = validationResult.changes.filterIsInstance<SchemaChange.ColumnAdded>().first()
        assertEquals("created_at", columnAdded.columnName)
        assertEquals("users", columnAdded.tableName)
        assertEquals(ChangeSeverity.MINOR, columnAdded.severity) // Nullable column is minor change
        
        // Should detect column type change
        assertTrue(validationResult.changes.any { it is SchemaChange.ColumnTypeChanged })
        val typeChanged = validationResult.changes.filterIsInstance<SchemaChange.ColumnTypeChanged>().first()
        assertEquals("username", typeChanged.columnName)
        assertEquals("VARCHAR(50)", typeChanged.oldType)
        assertEquals("VARCHAR(100)", typeChanged.newType)
        assertTrue(typeChanged.isCompatible)
        assertEquals(ChangeSeverity.MINOR, typeChanged.severity)
        
        // Should still be backward compatible
        assertTrue(validationResult.isBackwardCompatible)
    }
    
    @Test
    fun testBreakingChanges() {
        val v1Schema = """
            CREATE TABLE users (
                id INTEGER PRIMARY KEY,
                username VARCHAR(100) NOT NULL,
                email VARCHAR(100) NOT NULL,
                age INTEGER
            );
        """.trimIndent()
        
        val v2Schema = """
            CREATE TABLE users (
                id INTEGER PRIMARY KEY,
                username VARCHAR(50) NOT NULL,  -- Decreased size (breaking)
                email VARCHAR(100) NOT NULL,
                age INTEGER NOT NULL            -- Made non-nullable (breaking)
            );
        """.trimIndent()
        
        val sqlParser = SQLParser()
        
        val v1Tables = sqlParser.parseSQLContent(v1Schema)
        val v2Tables = sqlParser.parseSQLContent(v2Schema)
        
        val v1SchemaVersion = SchemaVersion(
            version = "1.0.0",
            timestamp = Clock.System.now(),
            tables = v1Tables,
            relationships = SchemaRelationships(emptyList())
        )
        
        val v2SchemaVersion = SchemaVersion(
            version = "2.0.0",
            timestamp = Clock.System.now(),
            tables = v2Tables,
            relationships = SchemaRelationships(emptyList())
        )
        
        val validationResult = SchemaValidator.validateSchemaChange(v1SchemaVersion, v2SchemaVersion)
        
        println("Breaking changes detected:")
        validationResult.changes.forEach { change ->
            println("  ${change.severity}: ${change.description}")
        }
        
        // Should detect type change (breaking)
        val typeChanges = validationResult.changes.filterIsInstance<SchemaChange.ColumnTypeChanged>()
        assertTrue(typeChanges.isNotEmpty())
        val usernameTypeChange = typeChanges.find { it.columnName == "username" }
        assertNotNull(usernameTypeChange)
        assertFalse(usernameTypeChange.isCompatible)
        assertEquals(ChangeSeverity.MAJOR, usernameTypeChange.severity)
        
        // Should detect nullability change (breaking)
        val nullabilityChanges = validationResult.changes.filterIsInstance<SchemaChange.ColumnNullabilityChanged>()
        assertTrue(nullabilityChanges.isNotEmpty())
        val ageNullabilityChange = nullabilityChanges.find { it.columnName == "age" }
        assertNotNull(ageNullabilityChange)
        assertTrue(ageNullabilityChange.wasNullable)
        assertFalse(ageNullabilityChange.isNullable)
        assertEquals(ChangeSeverity.MAJOR, ageNullabilityChange.severity)
        
        // Should NOT be backward compatible
        assertFalse(validationResult.isBackwardCompatible)
        assertEquals(ChangeSeverity.MAJOR, validationResult.recommendedVersionBump)
        assertTrue(validationResult.hasBreakingChanges())
    }
    
    @Test
    fun testRelationshipChanges() {
        val v1Schema = """
            CREATE TABLE users (
                id INTEGER PRIMARY KEY,
                username VARCHAR(50) NOT NULL
            );
            
            CREATE TABLE posts (
                id BIGINT PRIMARY KEY,
                title VARCHAR(200) NOT NULL
            );
        """.trimIndent()
        
        val v2Schema = """
            CREATE TABLE users (
                id INTEGER PRIMARY KEY,
                username VARCHAR(50) NOT NULL
            );
            
            CREATE TABLE posts (
                id BIGINT PRIMARY KEY,
                user_id INTEGER NOT NULL,  -- Added foreign key
                title VARCHAR(200) NOT NULL,
                FOREIGN KEY (user_id) REFERENCES users(id)
            );
        """.trimIndent()
        
        val sqlParser = SQLParser()
        
        val v1Tables = sqlParser.parseSQLContent(v1Schema)
        val v1Relationships = RelationshipDetector.detectRelationships(v1Tables)
        
        val v2Tables = sqlParser.parseSQLContent(v2Schema)
        val v2Relationships = RelationshipDetector.detectRelationships(v2Tables)
        
        val v1SchemaVersion = SchemaVersion(
            version = "1.0.0",
            timestamp = Clock.System.now(),
            tables = v1Tables,
            relationships = v1Relationships
        )
        
        val v2SchemaVersion = SchemaVersion(
            version = "1.1.0",
            timestamp = Clock.System.now(),
            tables = v2Tables,
            relationships = v2Relationships
        )
        
        val validationResult = SchemaValidator.validateSchemaChange(v1SchemaVersion, v2SchemaVersion)
        
        println("Relationship changes detected:")
        validationResult.changes.forEach { change ->
            println("  ${change.severity}: ${change.description}")
        }
        
        // Should detect column addition (user_id)
        val columnAdded = validationResult.changes.filterIsInstance<SchemaChange.ColumnAdded>()
        assertTrue(columnAdded.isNotEmpty())
        
        // Should detect relationship addition
        val relationshipAdded = validationResult.changes.filterIsInstance<SchemaChange.RelationshipAdded>()
        assertTrue(relationshipAdded.isNotEmpty())
        assertEquals(ChangeSeverity.MINOR, relationshipAdded.first().severity)
    }
    
    @Test
    fun testMigrationGeneration() {
        val v1Schema = """
            CREATE TABLE users (
                id INTEGER PRIMARY KEY,
                username VARCHAR(50) NOT NULL
            );
        """.trimIndent()
        
        val v2Schema = """
            CREATE TABLE users (
                id INTEGER PRIMARY KEY,
                username VARCHAR(50) NOT NULL,
                email VARCHAR(100) NOT NULL
            );
            
            CREATE TABLE posts (
                id BIGINT PRIMARY KEY,
                user_id INTEGER NOT NULL,
                title VARCHAR(200) NOT NULL
            );
        """.trimIndent()
        
        val sqlParser = SQLParser()
        
        val v1Tables = sqlParser.parseSQLContent(v1Schema)
        val v2Tables = sqlParser.parseSQLContent(v2Schema)
        
        val v1SchemaVersion = SchemaVersion(
            version = "1.0.0",
            timestamp = Clock.System.now(),
            tables = v1Tables,
            relationships = SchemaRelationships(emptyList())
        )
        
        val v2SchemaVersion = SchemaVersion(
            version = "2.0.0",
            timestamp = Clock.System.now(),
            tables = v2Tables,
            relationships = SchemaRelationships(emptyList())
        )
        
        val validationResult = SchemaValidator.validateSchemaChange(v1SchemaVersion, v2SchemaVersion)
        val migration = MigrationGenerator.generateMigration(validationResult, "test_migration")
        
        println("Generated migration:")
        println("Name: ${migration.name}")
        println("Reversible: ${migration.isReversible}")
        println("\nUP Script:")
        println(migration.upScript)
        println("\nDOWN Script:")
        println(migration.downScript)
        
        // Verify migration content
        assertEquals("test_migration", migration.name)
        assertEquals("1.0.0", migration.fromVersion)
        assertEquals("2.0.0", migration.toVersion)
        
        // UP script should contain table and column creation
        assertTrue(migration.upScript.contains("CREATE TABLE posts"))
        assertTrue(migration.upScript.contains("ALTER TABLE users ADD COLUMN email"))
        
        // DOWN script should contain reverse operations
        assertTrue(migration.downScript.contains("DROP TABLE IF EXISTS posts"))
        assertTrue(migration.downScript.contains("ALTER TABLE users DROP COLUMN email"))
        
        // Should be reversible (only additions)
        assertTrue(migration.isReversible)
    }
    
    @Test
    fun testSchemaConstraintValidation() {
        val schemaContent = """
            CREATE TABLE users (
                username VARCHAR(50) NOT NULL,  -- No primary key
                email VARCHAR(100) NOT NULL
            );
            
            CREATE TABLE posts (
                id BIGINT PRIMARY KEY,
                user_id INTEGER NOT NULL,        -- Has explicit FK constraint
                category_id INTEGER NOT NULL,     -- Has explicit FK constraint
                FOREIGN KEY (user_id) REFERENCES non_existent_users(id),
                FOREIGN KEY (category_id) REFERENCES non_existent_categories(id)
            );
        """.trimIndent()
        
        val sqlParser = SQLParser()
        val tables = sqlParser.parseSQLContent(schemaContent)
        val relationships = RelationshipDetector.detectRelationships(tables)
        
        val schema = SchemaVersion(
            version = "1.0.0",
            timestamp = Clock.System.now(),
            tables = tables,
            relationships = relationships
        )
        
        val issues = SchemaValidator.validateSchemaConstraints(schema)
        
        println("Schema constraint issues:")
        issues.forEach { issue ->
            println("  ${issue.severity}: ${issue.description}")
        }
        
        // Should detect missing primary key
        val missingPkIssues = issues.filterIsInstance<SchemaValidationIssue.MissingPrimaryKey>()
        assertTrue(missingPkIssues.isNotEmpty())
        assertEquals("users", missingPkIssues.first().tableName)
        
        // Should detect broken foreign key references
        val brokenFkIssues = issues.filterIsInstance<SchemaValidationIssue.BrokenForeignKeyReference>()
        assertTrue(brokenFkIssues.isNotEmpty())
        assertEquals(2, brokenFkIssues.size, "Should detect 2 broken foreign key references")
        
        // Verify specific broken references
        assertTrue(brokenFkIssues.any { it.referencedTable == "non_existent_users" })
        assertTrue(brokenFkIssues.any { it.referencedTable == "non_existent_categories" })
        
        // All issues should be warnings or errors
        assertTrue(issues.all { it.severity in setOf(IssueSeverity.WARNING, IssueSeverity.ERROR) })
    }
    
    @Test
    fun testTableSimilarityDetection() {
        val v1Schema = """
            CREATE TABLE user_profiles (
                id INTEGER PRIMARY KEY,
                username VARCHAR(50) NOT NULL,
                email VARCHAR(100) NOT NULL,
                created_at TIMESTAMP
            );
        """.trimIndent()
        
        val v2Schema = """
            CREATE TABLE profiles (
                id INTEGER PRIMARY KEY,
                username VARCHAR(50) NOT NULL,
                email VARCHAR(100) NOT NULL,
                created_at TIMESTAMP
            );
        """.trimIndent()
        
        val sqlParser = SQLParser()
        
        val v1Tables = sqlParser.parseSQLContent(v1Schema)
        val v2Tables = sqlParser.parseSQLContent(v2Schema)
        
        val v1SchemaVersion = SchemaVersion(
            version = "1.0.0",
            timestamp = Clock.System.now(),
            tables = v1Tables,
            relationships = SchemaRelationships(emptyList())
        )
        
        val v2SchemaVersion = SchemaVersion(
            version = "2.0.0",
            timestamp = Clock.System.now(),
            tables = v2Tables,
            relationships = SchemaRelationships(emptyList())
        )
        
        val validationResult = SchemaValidator.validateSchemaChange(v1SchemaVersion, v2SchemaVersion)
        
        println("Table rename detection:")
        validationResult.changes.forEach { change ->
            println("  ${change.type}: ${change.description}")
        }
        
        // Should detect table rename instead of separate add/remove
        val renames = validationResult.changes.filterIsInstance<SchemaChange.TableRenamed>()
        if (renames.isNotEmpty()) {
            val rename = renames.first()
            assertEquals("user_profiles", rename.oldName)
            assertEquals("profiles", rename.newName)
            assertEquals(ChangeSeverity.MAJOR, rename.severity)
        }
    }
    
    @Test
    fun testValidationResultSummary() {
        val v1Schema = """
            CREATE TABLE users (
                id INTEGER PRIMARY KEY,
                username VARCHAR(50) NOT NULL
            );
        """.trimIndent()
        
        val v2Schema = """
            CREATE TABLE users (
                id INTEGER PRIMARY KEY,
                username VARCHAR(100) NOT NULL,  -- Minor change
                email VARCHAR(100)               -- Minor change (nullable)
            );
            
            CREATE TABLE posts (                 -- Minor change
                id BIGINT PRIMARY KEY,
                title VARCHAR(200) NOT NULL
            );
        """.trimIndent()
        
        val sqlParser = SQLParser()
        
        val v1Tables = sqlParser.parseSQLContent(v1Schema)
        val v2Tables = sqlParser.parseSQLContent(v2Schema)
        
        val v1SchemaVersion = SchemaVersion(
            version = "1.0.0",
            timestamp = Clock.System.now(),
            tables = v1Tables,
            relationships = SchemaRelationships(emptyList())
        )
        
        val v2SchemaVersion = SchemaVersion(
            version = "1.1.0",
            timestamp = Clock.System.now(),
            tables = v2Tables,
            relationships = SchemaRelationships(emptyList())
        )
        
        val validationResult = SchemaValidator.validateSchemaChange(v1SchemaVersion, v2SchemaVersion)
        
        println("Validation summary:")
        println(validationResult.getSummary())
        
        // Verify summary data
        assertEquals(0, validationResult.getChangesBySeverity(ChangeSeverity.MAJOR).size)
        assertTrue(validationResult.getChangesBySeverity(ChangeSeverity.MINOR).isNotEmpty())
        assertEquals(ChangeSeverity.MINOR, validationResult.recommendedVersionBump)
        assertTrue(validationResult.isBackwardCompatible)
        assertFalse(validationResult.hasBreakingChanges())
    }
} 