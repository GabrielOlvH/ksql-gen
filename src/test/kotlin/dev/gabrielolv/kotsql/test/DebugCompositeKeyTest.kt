package dev.gabrielolv.kotsql.test

import dev.gabrielolv.kotsql.parser.SQLParser
import java.io.File

fun main() {
    val sqlParser = SQLParser()
    
    println("=== Debugging Composite Key Detection ===")
    
    // Test parsing the sale_orders.sql file directly
    val saleOrdersFile = File("src/test/resources/db/sale_orders.sql")
    if (saleOrdersFile.exists()) {
        println("Parsing sale_orders.sql...")
        val tables = sqlParser.parseFile(saleOrdersFile)
        
        tables.forEach { table ->
            println("\nTable: ${table.tableName}")
            println("  Has composite key: ${table.hasCompositePrimaryKey}")
            println("  Composite key: ${table.compositePrimaryKey}")
            println("  Primary key columns: ${table.primaryKeyColumns.map { it.columnName }}")
            
            println("  All columns:")
            table.columns.forEach { column ->
                println("    ${column.columnName}: ${column.sqlType} (nullable: ${column.isNullable}, pk: ${column.isPrimaryKey})")
            }
        }
    } else {
        println("❌ sale_orders.sql file not found")
    }
    
    // Test parsing the purchase_orders.sql file directly
    val purchaseOrdersFile = File("src/test/resources/db/purchase_orders.sql")
    if (purchaseOrdersFile.exists()) {
        println("\n\nParsing purchase_orders.sql...")
        val tables = sqlParser.parseFile(purchaseOrdersFile)
        
        tables.forEach { table ->
            println("\nTable: ${table.tableName}")
            println("  Has composite key: ${table.hasCompositePrimaryKey}")
            println("  Composite key: ${table.compositePrimaryKey}")
            println("  Primary key columns: ${table.primaryKeyColumns.map { it.columnName }}")
            
            println("  All columns:")
            table.columns.forEach { column ->
                println("    ${column.columnName}: ${column.sqlType} (nullable: ${column.isNullable}, pk: ${column.isPrimaryKey})")
            }
        }
    } else {
        println("❌ purchase_orders.sql file not found")
    }
    
    // Test parsing a simple composite key case manually
    println("\n\n=== Testing Manual Composite Key SQL ===")
    val testSql = """
        CREATE TABLE test_composite (
            col1 VARCHAR(50) NOT NULL,
            col2 INTEGER NOT NULL,
            col3 TEXT,
            PRIMARY KEY (col1, col2)
        );
    """.trimIndent()
    
    println("Test SQL:")
    println(testSql)
    
    val testTables = sqlParser.parseSQLContent(testSql)
    testTables.forEach { table ->
        println("\nParsed table: ${table.tableName}")
        println("  Has composite key: ${table.hasCompositePrimaryKey}")
        println("  Composite key: ${table.compositePrimaryKey}")
        println("  Primary key columns: ${table.primaryKeyColumns.map { it.columnName }}")
    }
} 