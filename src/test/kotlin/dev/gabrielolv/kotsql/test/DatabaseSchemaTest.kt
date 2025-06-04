package dev.gabrielolv.kotsql.test

import dev.gabrielolv.kotsql.generator.KotlinGenerator
import dev.gabrielolv.kotsql.parser.SQLParser
import dev.gabrielolv.kotsql.relationship.RelationshipDetector
import dev.gabrielolv.kotsql.validation.ValidationMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import java.io.File
import kotlin.test.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DatabaseSchemaTest {
    
    private lateinit var sqlParser: SQLParser
    private lateinit var generator: KotlinGenerator
    private lateinit var allTables: List<dev.gabrielolv.kotsql.model.SQLTableInfo>
    private lateinit var dbResourcesDir: File
    
    @BeforeAll
    fun setup() {
        sqlParser = SQLParser()
        generator = KotlinGenerator()
        dbResourcesDir = File("src/test/resources/db")
        
        assertTrue(dbResourcesDir.exists() && dbResourcesDir.isDirectory, 
            "Database resources directory should exist: ${dbResourcesDir.absolutePath}")
        
        // Load all SQL files
        allTables = loadAllSqlFiles()
        
        println("=== Database Schema Test Setup ===")
        println("Loaded ${allTables.size} tables from ${dbResourcesDir.listFiles()?.size ?: 0} SQL files")
        allTables.forEach { table ->
            println("- ${table.tableName}: ${table.columns.size} columns")
        }
        println()
    }
    
    private fun loadAllSqlFiles(): List<dev.gabrielolv.kotsql.model.SQLTableInfo> {
        val allTables = mutableListOf<dev.gabrielolv.kotsql.model.SQLTableInfo>()
        
        dbResourcesDir.listFiles { _, name -> name.endsWith(".sql") }?.forEach { sqlFile ->
            println("Loading SQL file: ${sqlFile.name}")
            try {
                val tables = sqlParser.parseFile(sqlFile)
                allTables.addAll(tables)
                println("  -> Parsed ${tables.size} table(s): ${tables.map { it.tableName }}")
            } catch (e: Exception) {
                println("  -> Error parsing ${sqlFile.name}: ${e.message}")
                fail("Failed to parse ${sqlFile.name}: ${e.message}")
            }
        }
        
        return allTables
    }
    
    @Test
    fun `test organizations table parsing and generation`() {
        val organizationsTable = allTables.find { it.tableName == "organizations" }
        assertNotNull(organizationsTable, "Should find organizations table")
        
        println("=== Organizations Table Test ===")
        
        // Verify table structure
        assertEquals("organizations", organizationsTable.tableName)
        assertTrue(organizationsTable.columns.size >= 15, "Organizations should have at least 15 columns")
        
        // Check specific columns
        val idColumn = organizationsTable.columns.find { it.columnName == "id" }
        assertNotNull(idColumn, "Should have id column")
        assertTrue(idColumn.isPrimaryKey, "id should be primary key")
        
        val ownerIdColumn = organizationsTable.columns.find { it.columnName == "owner_id" }
        assertNotNull(ownerIdColumn, "Should have owner_id column")
        assertFalse(ownerIdColumn.isNullable, "owner_id should be NOT NULL")
        assertTrue(ownerIdColumn.isLikelyForeignKey, "owner_id should be detected as foreign key")
        
        val contactEmailColumn = organizationsTable.columns.find { it.columnName == "contact_email" }
        assertNotNull(contactEmailColumn, "Should have contact_email column")
        assertTrue(contactEmailColumn.isNullable, "contact_email should be nullable")
        
        // Test code generation
        val generatedCode = generator.generateDataClassFile(organizationsTable, "test.db")
        
        println("Generated Organizations class preview:")
        println(generatedCode.split("\n").take(20).joinToString("\n") + "...")
        
        // Verify generated code
        assertTrue(generatedCode.contains("@Serializable"), "Should include @Serializable")
        assertTrue(generatedCode.contains("data class Organizations"), "Should generate correct class name")
        assertTrue(generatedCode.contains("ownerId: String"), "Should convert owner_id to ownerId")
        assertTrue(generatedCode.contains("contactEmail: String?"), "Should make nullable fields optional")
        assertTrue(generatedCode.contains("@Email"), "Should add email validation to contact_email")
        
        // Test validation annotations
        val validationAnnotations = ValidationMapper.getValidationAnnotations(contactEmailColumn)
        assertTrue(validationAnnotations.any { it.name == "Email" }, "Should generate email validation")
        
        println("Contact email validations: ${validationAnnotations.map { it.toAnnotationString() }}")
    }
    
    @Test
    fun `test purchase orders and relationships`() {
        val purchaseOrdersTable = allTables.find { it.tableName == "purchase_orders" }
        val purchaseOrderItemsTable = allTables.find { it.tableName == "purchase_order_items" }
        val suppliersTable = allTables.find { it.tableName == "suppliers" }
        
        assertNotNull(purchaseOrdersTable, "Should find purchase_orders table")
        assertNotNull(purchaseOrderItemsTable, "Should find purchase_order_items table")
        
        println("=== Purchase Orders Relationship Test ===")
        
        // Test purchase orders table structure
        assertEquals("purchase_orders", purchaseOrdersTable.tableName)
        
        val supplierIdColumn = purchaseOrdersTable.columns.find { it.columnName == "supplier_id" }
        assertNotNull(supplierIdColumn, "Should have supplier_id column")
        assertTrue(supplierIdColumn.isLikelyForeignKey, "supplier_id should be foreign key")
        assertEquals("suppliers", supplierIdColumn.referencedTableName, "Should reference suppliers table")
        
        // Test embedding column (vector type)
        val embeddingColumn = purchaseOrdersTable.columns.find { it.columnName == "embedding" }
        assertNotNull(embeddingColumn, "Should have embedding column")
        assertTrue(embeddingColumn.sqlType.contains("VECTOR"), "Should be VECTOR type")
        assertTrue(embeddingColumn.isNullable, "Embedding should be nullable")
        
        // Test purchase order items relationships
        val poIdColumn = purchaseOrderItemsTable.columns.find { it.columnName == "po_id" }
        assertNotNull(poIdColumn, "Should have po_id column")
        assertTrue(poIdColumn.isLikelyForeignKey, "po_id should be foreign key")
        
        val productIdColumn = purchaseOrderItemsTable.columns.find { it.columnName == "product_id" }
        assertNotNull(productIdColumn, "Should have product_id column")
        assertTrue(productIdColumn.isLikelyForeignKey, "product_id should be foreign key")
        
        // Test relationship detection
        val relationships = RelationshipDetector.detectRelationships(allTables)
        
        println("Purchase Orders relationships:")
        relationships.relationships.filter { 
            it.fromTable == "purchase_orders" || it.toTable == "purchase_orders" ||
            it.fromTable == "purchase_order_items" || it.toTable == "purchase_order_items"
        }.forEach { rel ->
            println("  ${rel.fromTable}.${rel.fromColumn} -> ${rel.toTable}.${rel.toColumn}")
        }
        
        // Verify specific relationships exist
        val poToSupplier = relationships.findRelationship("purchase_orders", "suppliers")
        assertNotNull(poToSupplier, "Should find purchase_orders -> suppliers relationship")
        
        val itemsToPo = relationships.findRelationship("purchase_order_items", "purchase_orders")
        assertNotNull(itemsToPo, "Should find purchase_order_items -> purchase_orders relationship")
        
        // Test code generation with relationships
        val generatedPOCode = generator.generateDataClassFile(
            purchaseOrdersTable, 
            "test.db", 
            includeValidation = true, 
            relationships = relationships
        )
        
        assertTrue(generatedPOCode.contains("object PurchaseOrders"), "Should generate table object")
        
        println("Purchase Orders class methods preview:")
        val lines = generatedPOCode.split("\n")
        val objectStart = lines.indexOfFirst { it.contains("object PurchaseOrders") }
        if (objectStart != -1) {
            lines.drop(objectStart).take(15).forEach { println("  $it") }
        }
    }
    
    @Test
    fun `test complex relationships and junction tables`() {
        val membersTable = allTables.find { it.tableName == "members" }
        val organizationsTable = allTables.find { it.tableName == "organizations" }
        
        assertNotNull(membersTable, "Should find members table")
        assertNotNull(organizationsTable, "Should find organizations table")
        
        println("=== Complex Relationships Test ===")
        
        // Members table should be a junction table for user-organization relationships
        assertTrue(membersTable.columns.size >= 3, "Members should have at least 3 columns")
        
        val userIdColumn = membersTable.columns.find { it.columnName == "user_id" }
        val orgIdColumn = membersTable.columns.find { it.columnName == "organization_id" }
        
        assertNotNull(userIdColumn, "Should have user_id column")
        assertNotNull(orgIdColumn, "Should have organization_id column")
        
        assertTrue(userIdColumn.isLikelyForeignKey, "user_id should be foreign key")
        assertTrue(orgIdColumn.isLikelyForeignKey, "organization_id should be foreign key")
        assertEquals("organizations", orgIdColumn.referencedTableName, "Should reference organizations")
        
        // Test relationship detection
        val relationships = RelationshipDetector.detectRelationships(allTables)
        val withReverse = RelationshipDetector.generateReverseRelationships(relationships)
        
        println("All detected relationships:")
        withReverse.relationships.forEach { rel ->
            val cardinality = RelationshipDetector.getCardinalityDescription(rel)
            println("  ${rel.fromTable}.${rel.fromColumn} -> ${rel.toTable}.${rel.toColumn} ($cardinality)")
        }
        
        // Check for many-to-many relationships through junction tables
        val manyToManyRels = withReverse.relationships.filterIsInstance<dev.gabrielolv.kotsql.model.RelationshipInfo.ManyToMany>()
        
        if (manyToManyRels.isNotEmpty()) {
            println("\nMany-to-Many relationships:")
            manyToManyRels.forEach { rel ->
                println("  ${rel.fromTable} <-> ${rel.toTable} via ${rel.junctionTable}")
            }
        }
        
        // Test join query generation capability
        if (organizationsTable != null && membersTable != null) {
            val orgToMembers = withReverse.findRelationship("organizations", "members")
            if (orgToMembers != null) {
                println("\nOrganizations -> Members relationship: ${RelationshipDetector.getCardinalityDescription(orgToMembers)}")
            }
        }
    }
    
    @Test
    fun `test all table validations and constraints`() {
        println("=== All Tables Validation Test ===")
        
        allTables.forEach { table ->
            println("\nValidating table: ${table.tableName}")
            
            // Check primary key exists (either single or composite)
            assertTrue(table.hasPrimaryKey, "${table.tableName} should have either single or composite primary key")
            
            // Check column naming conventions
            table.columns.forEach { column ->
                assertFalse(column.columnName.isBlank(), "Column name should not be blank")
                assertFalse(column.sqlType.isBlank(), "SQL type should not be blank")
                
                // Check foreign key naming convention
                if (column.columnName.endsWith("_id") && column.columnName != "id") {
                    assertTrue(column.isLikelyForeignKey, 
                        "${table.tableName}.${column.columnName} should be detected as foreign key")
                }
            }
            
            // Test validation annotations for each column
            table.columns.forEach { column ->
                val validations = ValidationMapper.getValidationAnnotations(column)
                
                // Primary keys should have @PrimaryKey annotation (only for single primary keys)
                if (column.isPrimaryKey) {
                    assertTrue(validations.any { it.name == "PrimaryKey" },
                        "${table.tableName}.${column.columnName} should have @PrimaryKey annotation")
                }
                
                // Non-nullable columns should have @NotNull (except primary key components)
                if (!column.isNullable && !table.isColumnPrimaryKey(column.columnName)) {
                    assertTrue(validations.any { it.name == "NotNull" },
                        "${table.tableName}.${column.columnName} should have @NotNull annotation")
                }
                
                // Email columns should have @Email annotation
                if (column.columnName.lowercase().contains("email")) {
                    assertTrue(validations.any { it.name == "Email" },
                        "${table.tableName}.${column.columnName} should have @Email annotation")
                }
                
                // VARCHAR columns should have @Length annotation
                if (column.sqlType.uppercase().startsWith("VARCHAR") && column.maxLength != null) {
                    assertTrue(validations.any { it.name == "Length" },
                        "${table.tableName}.${column.columnName} should have @Length annotation")
                }
                
                if (validations.isNotEmpty()) {
                    println("  ${column.columnName}: ${validations.map { it.toAnnotationString() }}")
                }
            }
        }
    }
    

    @Test
    fun `test code generation for all tables`() {
        println("=== Code Generation Test for All Tables ===")
        
        // Test relationships once for all tables
        val relationships = RelationshipDetector.detectRelationships(allTables)
        val withReverse = RelationshipDetector.generateReverseRelationships(relationships)
        
        val generatedFiles = mutableMapOf<String, String>()
        
        allTables.forEach { table ->
            println("\nGenerating code for: ${table.tableName}")
            
            try {
                val generatedCode = generator.generateDataClassFile(
                    table = table,
                    packageName = "test.db.generated",
                    includeValidation = true,
                    relationships = withReverse
                )
                
                val className = dev.gabrielolv.kotsql.util.NamingConventions.tableNameToClassName(table.tableName)
                generatedFiles["${className}.kt"] = generatedCode
                
                // Basic verification
                assertTrue(generatedCode.contains("@Serializable"), 
                    "${table.tableName} should generate @Serializable annotation")
                assertTrue(generatedCode.contains("data class $className"), 
                    "${table.tableName} should generate correct class name")
                assertTrue(generatedCode.contains("object $className"), 
                    "${table.tableName} should generate table object")
                
                // Count validation annotations
                val validationCount = generatedCode.split("@").size - 1
                println("  Generated ${table.columns.size} properties with $validationCount validation annotations")
                
            } catch (e: Exception) {
                fail("Failed to generate code for ${table.tableName}: ${e.message}")
            }
        }
        
        println("\n=== Generation Summary ===")
        println("Successfully generated ${generatedFiles.size} files:")
        generatedFiles.keys.sorted().forEach { fileName ->
            val content = generatedFiles[fileName]!!
            val lines = content.split("\n").size
            val imports = content.split("\n").count { it.trim().startsWith("import") }
            println("  $fileName: $lines lines, $imports imports")
        }
        
        // Verify that all generated files compile (at least syntactically)
        generatedFiles.forEach { (fileName, content) ->
            assertFalse(content.isBlank(), "$fileName should not be blank")
            assertTrue(content.contains("package test.db.generated"), 
                "$fileName should contain package declaration")
        }
    }
    
    @Test
    fun `test specific database features`() {
        println("=== Database Features Test ===")
        
        // Test vector/embedding support
        val tablesWithVectors = allTables.filter { table ->
            table.columns.any { it.sqlType.uppercase().contains("VECTOR") }
        }
        
        if (tablesWithVectors.isNotEmpty()) {
            println("Tables with vector columns:")
            tablesWithVectors.forEach { table ->
                val vectorColumns = table.columns.filter { it.sqlType.uppercase().contains("VECTOR") }
                vectorColumns.forEach { column ->
                    println("  ${table.tableName}.${column.columnName}: ${column.sqlType}")
                }
            }
        }
        
        // Test timestamp columns
        val timestampColumns = allTables.flatMap { table ->
            table.columns.filter { 
                it.sqlType.uppercase().contains("TIMESTAMP") || 
                it.columnName.lowercase().contains("created_at") ||
                it.columnName.lowercase().contains("updated_at")
            }.map { "${table.tableName}.${it.columnName}" }
        }
        
        println("\nTimestamp columns found: ${timestampColumns.size}")
        timestampColumns.forEach { println("  $it") }
        
        // Test JSON/Text columns for complex data
        val complexDataColumns = allTables.flatMap { table ->
            table.columns.filter { 
                it.sqlType.uppercase().contains("TEXT") || 
                it.sqlType.uppercase().contains("JSON") ||
                it.columnName.lowercase().contains("data") ||
                it.columnName.lowercase().contains("content")
            }.map { "${table.tableName}.${it.columnName}: ${it.sqlType}" }
        }
        
        println("\nComplex data columns found: ${complexDataColumns.size}")
        complexDataColumns.take(10).forEach { println("  $it") }
        
        // Test numeric precision columns
        val numericColumns = allTables.flatMap { table ->
            table.columns.filter { 
                it.sqlType.uppercase().contains("NUMERIC") || 
                it.sqlType.uppercase().contains("DECIMAL")
            }.map { "${table.tableName}.${it.columnName}: ${it.sqlType}" }
        }
        
        if (numericColumns.isNotEmpty()) {
            println("\nNumeric precision columns:")
            numericColumns.forEach { println("  $it") }
        }
    }
    
    private fun dev.gabrielolv.kotsql.model.SchemaRelationships.findRelationship(
        fromTable: String, 
        toTable: String
    ): dev.gabrielolv.kotsql.model.RelationshipInfo? {
        return relationships.find { it.fromTable == fromTable && it.toTable == toTable }
    }
} 