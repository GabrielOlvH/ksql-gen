package dev.gabrielolv.kotsql.test

import dev.gabrielolv.kotsql.generator.KotlinGenerator
import dev.gabrielolv.kotsql.parser.SQLParser
import dev.gabrielolv.kotsql.relationship.RelationshipDetector
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import java.io.File
import kotlin.test.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CompositeKeyTest {
    
    private lateinit var sqlParser: SQLParser
    private lateinit var generator: KotlinGenerator
    private lateinit var allTables: List<dev.gabrielolv.kotsql.model.SQLTableInfo>
    private lateinit var relationships: dev.gabrielolv.kotsql.model.SchemaRelationships
    
    @BeforeAll
    fun setup() {
        sqlParser = SQLParser()
        generator = KotlinGenerator()
        
        val dbResourcesDir = File("src/test/resources/db")
        assertTrue(dbResourcesDir.exists(), "Database resources directory should exist")
        
        // Load all SQL files
        allTables = mutableListOf<dev.gabrielolv.kotsql.model.SQLTableInfo>().apply {
            dbResourcesDir.listFiles { _, name -> name.endsWith(".sql") }?.forEach { sqlFile ->
                addAll(sqlParser.parseFile(sqlFile))
            }
        }
        
        // Detect relationships
        relationships = RelationshipDetector.generateReverseRelationships(
            RelationshipDetector.detectRelationships(allTables)
        )
        
        println("=== Composite Key Test Setup ===")
        println("Loaded ${allTables.size} tables")
        println("Tables with composite keys:")
        allTables.filter { it.hasCompositePrimaryKey }.forEach { table ->
            println("  ${table.tableName}: ${table.compositePrimaryKey.joinToString(", ")}")
        }
        println()
    }
    
    @Test
    fun `test composite key detection in sale_order_items`() {
        println("=== Testing sale_order_items Composite Key ===")
        
        val saleOrderItems = allTables.find { it.tableName == "sale_order_items" }
        assertNotNull(saleOrderItems, "sale_order_items table should exist")
        
        // Verify composite key detection
        assertTrue(saleOrderItems.hasCompositePrimaryKey, "sale_order_items should have composite key")
        assertEquals(listOf("so_id", "product_id"), saleOrderItems.compositePrimaryKey)
        
        // Verify primary key columns
        val pkColumns = saleOrderItems.primaryKeyColumns
        assertEquals(2, pkColumns.size, "Should have 2 primary key columns")
        assertEquals("so_id", pkColumns[0].columnName)
        assertEquals("product_id", pkColumns[1].columnName)
        
        // Verify columns are not nullable (primary key constraint)
        pkColumns.forEach { column ->
            assertFalse(column.isNullable, "${column.columnName} should not be nullable")
        }
        
        // Verify non-key columns
        val regularColumns = saleOrderItems.regularColumns
        assertTrue(regularColumns.isNotEmpty(), "Should have non-key columns")
        assertTrue(regularColumns.none { it.columnName in saleOrderItems.compositePrimaryKey })
        
        println("✓ sale_order_items composite key detection successful")
    }
    
    @Test
    fun `test composite key detection in purchase_order_items`() {
        println("=== Testing purchase_order_items Composite Key ===")
        
        val purchaseOrderItems = allTables.find { it.tableName == "purchase_order_items" }
        assertNotNull(purchaseOrderItems, "purchase_order_items table should exist")
        
        // Verify composite key detection
        assertTrue(purchaseOrderItems.hasCompositePrimaryKey, "purchase_order_items should have composite key")
        assertEquals(listOf("po_id", "product_id"), purchaseOrderItems.compositePrimaryKey)
        
        // Verify primary key columns
        val pkColumns = purchaseOrderItems.primaryKeyColumns
        assertEquals(2, pkColumns.size, "Should have 2 primary key columns")
        assertEquals("po_id", pkColumns[0].columnName)
        assertEquals("product_id", pkColumns[1].columnName)
        
        // Verify columns are not nullable (primary key constraint)
        pkColumns.forEach { column ->
            assertFalse(column.isNullable, "${column.columnName} should not be nullable")
        }
        
        println("✓ purchase_order_items composite key detection successful")
    }
    
    @Test
    fun `test composite key code generation for sale_order_items`() {
        println("=== Testing Code Generation for sale_order_items ===")
        
        val saleOrderItems = allTables.find { it.tableName == "sale_order_items" }
        assertNotNull(saleOrderItems, "sale_order_items table should exist")
        
        val generatedCode = generator.generateDataClassFile(
            table = saleOrderItems,
            packageName = "com.example.generated.test",
            includeValidation = true,
            relationships = relationships
        )
        
        println("Generated code preview:")
        println(generatedCode.split('\n').take(20).joinToString("\n"))
        println("...")
        
        // Verify composite key class generation
        assertTrue(generatedCode.contains("data class SaleOrderItemsKey("), 
            "Should generate composite key class")
        assertTrue(generatedCode.contains("@Serializable"), 
            "Key class should be serializable")
        
        // Verify main class has key() method
        assertTrue(generatedCode.contains("fun key(): SaleOrderItemsKey"), 
            "Main class should have key() method")
        
        // Verify companion object with fromKey method
        assertTrue(generatedCode.contains("fun fromKey(key: SaleOrderItemsKey"), 
            "Should have fromKey factory method")
        
        // Verify table metadata includes composite key support
        assertTrue(generatedCode.contains("PRIMARY_KEY_COLUMNS"), 
            "Should include primary key columns constant")
        assertTrue(generatedCode.contains("CompositeKey<SaleOrderItemsKey>"), 
            "Should include composite key metadata")
        assertTrue(generatedCode.contains("fun findByKey(key: SaleOrderItemsKey)"), 
            "Should have findByKey method")
        
        // Verify imports
        assertTrue(generatedCode.contains("import dev.gabrielolv.kotsql.model.CompositeKey"), 
            "Should import CompositeKey")
        
        println("✓ Code generation for sale_order_items successful")
    }
    
    @Test
    fun `test composite key code generation for purchase_order_items`() {
        println("=== Testing Code Generation for purchase_order_items ===")
        
        val purchaseOrderItems = allTables.find { it.tableName == "purchase_order_items" }
        assertNotNull(purchaseOrderItems, "purchase_order_items table should exist")
        
        val generatedCode = generator.generateDataClassFile(
            table = purchaseOrderItems,
            packageName = "com.example.generated.test",
            includeValidation = true,
            relationships = relationships
        )
        
        // Verify composite key class generation
        assertTrue(generatedCode.contains("data class PurchaseOrderItemsKey("), 
            "Should generate composite key class")
        
        // Verify key validation methods
        assertTrue(generatedCode.contains("fun validateKey(): Boolean"), 
            "Should have key validation method")
        assertTrue(generatedCode.contains("fun toParameterList(): List<Any>"), 
            "Should have parameter list conversion")
        assertTrue(generatedCode.contains("fun keyToString(): String"), 
            "Should have string representation method")
        
        // Verify main data class structure
        assertTrue(generatedCode.contains("This table has a composite primary key: po_id, product_id"), 
            "Should document composite key in comments")
        
        println("✓ Code generation for purchase_order_items successful")
    }
    
    @Test
    fun `test composite key validation logic`() {
        println("=== Testing Composite Key Validation Logic ===")
        
        val compositeKeyTables = allTables.filter { it.hasCompositePrimaryKey }
        assertTrue(compositeKeyTables.isNotEmpty(), "Should have tables with composite keys")
        
        compositeKeyTables.forEach { table ->
            println("Validating table: ${table.tableName}")
            
            // Test that all primary key columns exist
            table.compositePrimaryKey.forEach { keyColumnName ->
                val column = table.columns.find { it.columnName == keyColumnName }
                assertNotNull(column, "Primary key column $keyColumnName should exist in ${table.tableName}")
                assertFalse(column.isNullable, "Primary key component $keyColumnName should not be nullable")
            }
            
            // Test isColumnPrimaryKey method
            table.compositePrimaryKey.forEach { keyColumnName ->
                assertTrue(table.isColumnPrimaryKey(keyColumnName), 
                    "$keyColumnName should be recognized as primary key")
            }
            
            // Test that non-key columns are not considered primary key
            table.regularColumns.forEach { column ->
                assertFalse(table.isColumnPrimaryKey(column.columnName),
                    "${column.columnName} should not be considered primary key")
            }
            
            // Test primaryKeyColumns property
            val pkColumns = table.primaryKeyColumns
            assertEquals(table.compositePrimaryKey.size, pkColumns.size,
                "Primary key columns count should match composite key size")
            
            pkColumns.forEach { pkColumn ->
                assertTrue(pkColumn.columnName in table.compositePrimaryKey,
                    "${pkColumn.columnName} should be in composite primary key")
            }
        }
        
        println("✓ Composite key validation logic tests passed")
    }
    
    @Test
    fun `test SQL parsing edge cases for composite keys`() {
        println("=== Testing SQL Parsing Edge Cases ===")
        
        // Test various composite key syntax variations
        val testCases = listOf(
            // Standard format
            """
            CREATE TABLE test1 (
                col1 VARCHAR(50) NOT NULL,
                col2 INTEGER NOT NULL,
                col3 TEXT,
                PRIMARY KEY (col1, col2)
            );
            """,
            
            // With constraint name
            """
            CREATE TABLE test2 (
                tenant_id VARCHAR(128) NOT NULL,
                user_id VARCHAR(128) NOT NULL,
                data TEXT,
                CONSTRAINT pk_test2 PRIMARY KEY (tenant_id, user_id)
            );
            """,
            
            // Mixed with other constraints
            """
            CREATE TABLE test3 (
                id1 BIGINT NOT NULL,
                id2 BIGINT NOT NULL,
                name VARCHAR(255) NOT NULL,
                UNIQUE (name),
                PRIMARY KEY (id1, id2),
                CHECK (id1 > 0)
            );
            """
        )
        
        testCases.forEachIndexed { index, sql ->
            println("Testing case ${index + 1}")
            val tables = sqlParser.parseSQLContent(sql)
            
            assertEquals(1, tables.size, "Should parse exactly one table")
            val table = tables.first()
            
            assertTrue(table.hasCompositePrimaryKey, "Should detect composite key")
            assertEquals(2, table.compositePrimaryKey.size, "Should have 2-column composite key")
            assertFalse(table.primaryKeyColumns.any { it.isNullable }, 
                "Primary key columns should not be nullable")
        }
        
        println("✓ SQL parsing edge cases passed")
    }
    
    @Test
    fun `test relationship detection with composite keys`() {
        println("=== Testing Relationship Detection with Composite Keys ===")
        
        // Test that junction tables with composite keys are properly detected
        val junctionTables = allTables.filter { table ->
            table.hasCompositePrimaryKey && 
            table.columns.filter { it.isLikelyForeignKey }.size == table.compositePrimaryKey.size
        }
        
        assertTrue(junctionTables.isNotEmpty(), "Should have junction tables with composite keys")
        
        junctionTables.forEach { junctionTable ->
            println("Junction table: ${junctionTable.tableName}")
            println("  Composite key: ${junctionTable.compositePrimaryKey.joinToString(", ")}")
            
            // Verify all primary key components are foreign keys
            junctionTable.primaryKeyColumns.forEach { pkColumn ->
                assertTrue(pkColumn.isLikelyForeignKey, 
                    "${pkColumn.columnName} should be a foreign key in junction table")
            }
            
            // Check for relationships involving this table
            val relatedRelationships = relationships.relationships.filter { 
                it.fromTable == junctionTable.tableName || it.toTable == junctionTable.tableName 
            }
            
            println("  Related relationships: ${relatedRelationships.size}")
        }
        
        println("✓ Relationship detection with composite keys passed")
    }
    
    @Test
    fun `test performance with composite keys`() {
        println("=== Testing Performance with Composite Keys ===")
        
        val compositeKeyTables = allTables.filter { it.hasCompositePrimaryKey }
        
        // Test code generation performance
        val startTime = System.currentTimeMillis()
        
        compositeKeyTables.forEach { table ->
            repeat(100) {
                generator.generateDataClassFile(
                    table = table,
                    packageName = "com.example.perf.test",
                    includeValidation = true,
                    relationships = relationships
                )
            }
        }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        
        println("Generated ${compositeKeyTables.size * 100} composite key classes in ${duration}ms")
        assertTrue(duration < 5000, "Should generate composite key classes efficiently")
        
        // Test parsing performance
        val parseStartTime = System.currentTimeMillis()
        
        repeat(1000) {
            val sql = """
                CREATE TABLE perf_test (
                    tenant_id VARCHAR(128) NOT NULL,
                    user_id VARCHAR(128) NOT NULL,
                    data TEXT,
                    PRIMARY KEY (tenant_id, user_id)
                );
            """
            sqlParser.parseSQLContent(sql)
        }
        
        val parseEndTime = System.currentTimeMillis()
        val parseDuration = parseEndTime - parseStartTime
        
        println("Parsed 1000 composite key tables in ${parseDuration}ms")
        assertTrue(parseDuration < 2000, "Should parse composite keys efficiently")
        
        println("✓ Performance tests with composite keys passed")
    }
} 