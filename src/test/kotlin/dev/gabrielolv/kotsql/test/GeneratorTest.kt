package dev.gabrielolv.kotsql.test

import dev.gabrielolv.kotsql.generator.KotlinGenerator
import dev.gabrielolv.kotsql.parser.SQLParser
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class GeneratorTest {
    
    private val generator = KotlinGenerator()
    
    @Test
    fun testSQLParsingAndCodeGeneration() {
        val sqlParser = SQLParser()
        val generator = KotlinGenerator()
        
        // Test SQL parsing
        val schemaFile = File("src/main/resources/schema.sql")
        assertTrue(schemaFile.exists(), "Schema file should exist")
        
        val tables = sqlParser.parseFile(schemaFile)
        assertTrue(tables.isNotEmpty(), "Should parse at least one table")
        
        println("Parsed ${tables.size} tables:")
        tables.forEach { table ->
            println("- ${table.tableName} (${table.columns.size} columns)")
            table.columns.forEach { column ->
                println("  - ${column.columnName}: ${column.sqlType} (nullable: ${column.isNullable}, pk: ${column.isPrimaryKey})")
            }
        }
        
        // Test code generation
        val generatedFiles = generator.generateAllDataClasses(tables, "dev.gabrielolv.generated")
        assertTrue(generatedFiles.isNotEmpty(), "Should generate at least one file")
        
        println("\nGenerated files:")
        generatedFiles.forEach { (fileName, content) ->
            println("\n=== $fileName ===")
            println(content)
            println("=== End of $fileName ===")
        }
    }
    
    @Test
    fun testTypeMappingAndNaming() {
        val sqlContent = """
            CREATE TABLE test_table (
                id INTEGER PRIMARY KEY,
                user_name VARCHAR(50) NOT NULL,
                created_at TIMESTAMP,
                is_active BOOLEAN,
                balance DECIMAL(10,2),
                data BLOB
            );
        """.trimIndent()
        
        val sqlParser = SQLParser()
        val generator = KotlinGenerator()
        
        val tables = sqlParser.parseSQLContent(sqlContent)
        assertTrue(tables.size == 1, "Should parse exactly one table")
        
        val table = tables.first()
        val generatedCode = generator.generateDataClassFile(
            table = table,
            packageName = "com.example.test",
            includeValidation = true
        )
        
        println("Generated code for test table:")
        println(generatedCode)
        
        // Verify the generated code contains expected elements
        assertTrue(generatedCode.contains("@Serializable"), "Should include @Serializable annotation")
        assertTrue(generatedCode.contains("data class TestTable"), "Should generate correct class name")
        assertTrue(generatedCode.contains("userName: String"), "Should convert snake_case to camelCase")
        assertTrue(generatedCode.contains("@SerialName(\"user_name\")"), "Should include SerialName annotation")
        assertTrue(generatedCode.contains("kotlinx.datetime.Instant"), "Should use kotlinx.datetime for timestamps")
        assertTrue(generatedCode.contains("isActive: Boolean"), "Should map BOOLEAN to Boolean")
        assertTrue(generatedCode.contains("balance: Double"), "Should map DECIMAL to Double")
        assertTrue(generatedCode.contains("data: ByteArray"), "Should map BLOB to ByteArray")
    }
    
    @Test
    fun `test basic data class generation`() {
        val sqlContent = """
            CREATE TABLE users (
                id INTEGER PRIMARY KEY,
                username VARCHAR(50) NOT NULL,
                email VARCHAR(100) UNIQUE,
                created_at TIMESTAMP,
                is_active BOOLEAN DEFAULT true
            );
        """.trimIndent()
        
        val parser = SQLParser()
        val tables = parser.parseSQLContent(sqlContent)
        val table = tables.first()
        
        val generatedCode = generator.generateDataClassFile(
            table = table,
            packageName = "com.example.test",
            includeValidation = true
        )
        
        println("Generated code:")
        println(generatedCode)
        
        // Verify package declaration
        assertTrue(generatedCode.contains("package com.example.test"))
        
        // Verify data class generation
        assertTrue(generatedCode.contains("data class Users("))
        assertTrue(generatedCode.contains("val id: Int"))
        assertTrue(generatedCode.contains("val username: String"))
        assertTrue(generatedCode.contains("val email: String?"))
        assertTrue(generatedCode.contains("val createdAt: kotlinx.datetime.Instant?"))
        assertTrue(generatedCode.contains("val isActive: Boolean?"))
        
        // Verify imports
        assertTrue(generatedCode.contains("import kotlinx.serialization.Serializable"))
        assertTrue(generatedCode.contains("import kotlinx.datetime.Instant"))
        
        // Verify table metadata
        assertTrue(generatedCode.contains("object UsersTable"))
        assertTrue(generatedCode.contains("const val TABLE_NAME = \"users\""))
    }
    
    @Test
    fun `test composite key generation`() {
        val sqlContent = """
            CREATE TABLE user_permissions (
                user_id INTEGER NOT NULL,
                permission_id INTEGER NOT NULL,
                granted_at TIMESTAMP,
                PRIMARY KEY (user_id, permission_id)
            );
        """.trimIndent()
        
        val parser = SQLParser()
        val tables = parser.parseSQLContent(sqlContent)
        val table = tables.first()
        
        val generatedCode = generator.generateDataClassFile(
            table = table,
            packageName = "com.example.test",
            includeValidation = true
        )
        
        println("Generated composite key code:")
        println(generatedCode)
        
        // Verify composite key class generation
        assertTrue(generatedCode.contains("data class UserPermissionsKey("))
        assertTrue(generatedCode.contains("val userId: Int"))
        assertTrue(generatedCode.contains("val permissionId: Int"))
        
        // Verify main data class
        assertTrue(generatedCode.contains("data class UserPermissions("))
        
        // Verify CompositeKey import and usage
        assertTrue(generatedCode.contains("import dev.gabrielolv.kotsql.model.CompositeKey"))
        assertTrue(generatedCode.contains("val primaryKey = CompositeKey<UserPermissionsKey>"))
    }
} 