package dev.gabrielolv.kotsql.test

import dev.gabrielolv.kotsql.generator.KotlinGenerator
import dev.gabrielolv.kotsql.parser.SQLParser
import dev.gabrielolv.kotsql.relationship.RelationshipDetector
import dev.gabrielolv.kotsql.query.*
import dev.gabrielolv.kotsql.model.RelationshipInfo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import java.io.File
import kotlin.test.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DatabaseQueryTest {
    
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
        
        println("=== Database Query Test Setup ===")
        println("Loaded ${allTables.size} tables")
        println("Detected ${relationships.relationships.size} relationships")
    }
    
    @Test
    fun `test basic type-safe queries for organizations`() {
        val organizationsTable = allTables.find { it.tableName == "organizations" }
        assertNotNull(organizationsTable, "Should find organizations table")
        
        println("=== Organizations Query Test ===")
        
        // Test basic SELECT query
        val selectQuery = TypeSafeQuery.from<Any>("organizations")
            .where(Column<String>("name") eq "ACME Corp")
            .orderBy(Column<String>("created_at"))
            .limit(10)
        
        val selectResult = selectQuery.build()
        
        println("Basic SELECT query:")
        println("SQL: ${selectResult.sql}")
        println("Parameters: ${selectResult.parameters}")
        
        assertTrue(selectResult.sql.contains("SELECT * FROM organizations"), "Should generate FROM clause")
        assertTrue(selectResult.sql.contains("WHERE name = ?"), "Should generate WHERE clause")
        assertTrue(selectResult.sql.contains("ORDER BY created_at"), "Should generate ORDER BY clause")
        assertTrue(selectResult.sql.contains("LIMIT 10"), "Should generate LIMIT clause")
        assertEquals(listOf("ACME Corp"), selectResult.parameters, "Should include parameters")
        
        // Test query with multiple conditions
        val multiConditionQuery = TypeSafeQuery.from<Any>("organizations")
            .where(Column<String>("name") eq "ACME Corp")
            .and(Column<String>("address_city") eq "New York")
            .orderBy(Column<String>("name"))
            .limit(5)
        
        val multiResult = multiConditionQuery.build()
        
        println("\nMulti-condition query:")
        println("SQL: ${multiResult.sql}")
        println("Parameters: ${multiResult.parameters}")
        
        assertTrue(multiResult.sql.contains("WHERE name = ? AND address_city = ?"), "Should generate multiple WHERE conditions")
        assertEquals(listOf("ACME Corp", "New York"), multiResult.parameters)
        
        // Test COUNT query
        val countQuery = TypeSafeQuery.from<Any>("organizations")
            .where(Column<String>("address_country") eq "USA")
        
        val countResult = countQuery.count()
        
        println("\nCOUNT query:")
        println("SQL: ${countResult.sql}")
        
        assertTrue(countResult.sql.contains("SELECT COUNT(*) FROM organizations"), "Should generate COUNT query")
        assertTrue(countResult.sql.contains("WHERE address_country = ?"), "Should include WHERE clause")
    }
    
    @Test
    fun `test complex queries with purchase orders`() {
        val purchaseOrdersTable = allTables.find { it.tableName == "purchase_orders" }
        val purchaseOrderItemsTable = allTables.find { it.tableName == "purchase_order_items" }
        
        assertNotNull(purchaseOrdersTable, "Should find purchase_orders table")
        assertNotNull(purchaseOrderItemsTable, "Should find purchase_order_items table")
        
        println("=== Purchase Orders Complex Query Test ===")
        
        // Test query with multiple conditions
        val complexQuery = TypeSafeQuery.from<Any>("purchase_orders")
            .where(Column<String>("status") eq "ordered")
            .and(Column<String>("date_created") gte "2024-01-01")
            .and(Column<Double>("total_amount") gt 1000.0)
            .orderBy(Column<String>("date_created"), TypeSafeQuery.SortOrder.DESC)
            .limit(50)
        
        val complexResult = complexQuery.build()
        
        println("Complex WHERE query:")
        println("SQL: ${complexResult.sql}")
        println("Parameters: ${complexResult.parameters}")
        
        assertTrue(complexResult.sql.contains("WHERE status = ?"), "Should have status condition")
        assertTrue(complexResult.sql.contains("AND date_created >= ?"), "Should have date condition")
        assertTrue(complexResult.sql.contains("AND total_amount > ?"), "Should have amount condition")
        assertTrue(complexResult.sql.contains("ORDER BY date_created DESC"), "Should order by date descending")
        assertEquals(listOf("ordered", "2024-01-01", 1000.0), complexResult.parameters)
        
        // Test IN query
        val inQuery = TypeSafeQuery.from<Any>("purchase_orders")
            .whereIn(Column<String>("status"), listOf("ordered", "received", "pending"))
            .orderBy(Column<String>("po_number"))
        
        val inResult = inQuery.build()
        
        println("\nIN query:")
        println("SQL: ${inResult.sql}")
        println("Parameters: ${inResult.parameters}")
        
        assertTrue(inResult.sql.contains("WHERE status IN (?, ?, ?)"), "Should use IN clause")
        assertEquals(listOf("ordered", "received", "pending"), inResult.parameters)
        
        // Test SELECT with specific columns
        val selectColumnsQuery = TypeSafeQuery.from<Any>("purchase_orders")
            .where(Column<String>("supplier_id") eq "supplier-123")
        
        val specificColumns = arrayOf(
            Column<String>("po_number"),
            Column<String>("status"),
            Column<Double>("total_amount")
        )
        
        val selectColumnsResult = selectColumnsQuery.select(specificColumns as Array<Column<*>>)
        
        println("\nSELECT specific columns:")
        println("SQL: ${selectColumnsResult.sql}")
        
        assertTrue(selectColumnsResult.sql.contains("SELECT po_number, status, total_amount"), "Should select specific columns")
        assertTrue(selectColumnsResult.sql.contains("FROM purchase_orders"), "Should include table name")
    }
    
    @Test
    fun `test relationship-based queries`() {
        val membersTable = allTables.find { it.tableName == "members" }
        val organizationsTable = allTables.find { it.tableName == "organizations" }
        
        if (membersTable == null || organizationsTable == null) {
            println("Skipping relationship test - required tables not found")
            return
        }
        
        println("=== Relationship-Based Query Test ===")
        
        // Check if members table acts as a junction table
        val userIdCol = membersTable.columns.find { it.columnName == "user_id" }
        val orgIdCol = membersTable.columns.find { it.columnName == "organization_id" }
        
        if (userIdCol != null && orgIdCol != null) {
            println("Members table structure:")
            println("  user_id: ${userIdCol.sqlType} (FK: ${userIdCol.isLikelyForeignKey})")
            println("  organization_id: ${orgIdCol.sqlType} (FK: ${orgIdCol.isLikelyForeignKey})")
            
            // Test query to find user organizations
            val userOrgsQuery = TypeSafeQuery.from<Any>("members")
                .where(Column<String>("user_id") eq "user-123")
                .orderBy(Column<String>("organization_id"))
            
            val userOrgsResult = userOrgsQuery.build()
            
            println("\nUser organizations query:")
            println("SQL: ${userOrgsResult.sql}")
            println("Parameters: ${userOrgsResult.parameters}")
            
            assertTrue(userOrgsResult.sql.contains("WHERE user_id = ?"), "Should filter by user")
            assertEquals(listOf("user-123"), userOrgsResult.parameters)
            
            // Test query for organization members
            val orgMembersQuery = TypeSafeQuery.from<Any>("members")
                .where(Column<String>("organization_id") eq "org-456")
                .orderBy(Column<String>("user_id"))
            
            val orgMembersResult = orgMembersQuery.build()
            
            println("\nOrganization members query:")
            println("SQL: ${orgMembersResult.sql}")
            
            assertTrue(orgMembersResult.sql.contains("WHERE organization_id = ?"), "Should filter by organization")
        }
        
        // Look for detected relationships
        println("\nDetected relationships:")
        relationships.relationships.take(5).forEach { rel ->
            val cardinality = RelationshipDetector.getCardinalityDescription(rel)
            println("  ${rel.fromTable}.${rel.fromColumn} -> ${rel.toTable}.${rel.toColumn} ($cardinality)")
        }
    }
    
    @Test
    fun `test advanced column queries`() {
        val tablesWithVectors = allTables.filter { table ->
            table.columns.any { it.sqlType.uppercase().contains("VECTOR") }
        }
        
        if (tablesWithVectors.isEmpty()) {
            println("No tables with vector columns found")
            return
        }
        
        println("=== Advanced Column Test ===")
        
        tablesWithVectors.forEach { table ->
            val vectorColumns = table.columns.filter { it.sqlType.uppercase().contains("VECTOR") }
            
            vectorColumns.forEach { vectorCol ->
                println("Testing vector column: ${table.tableName}.${vectorCol.columnName}")
                
                // Test basic query on vector column
                val vectorQuery = TypeSafeQuery.from<Any>(table.tableName)
                    .where(Column<String>(vectorCol.columnName).isNotNull())
                    .limit(5)
                
                val vectorResult = vectorQuery.build()
                
                println("Vector query:")
                println("SQL: ${vectorResult.sql}")
                
                assertTrue(vectorResult.sql.contains("${vectorCol.columnName} IS NOT NULL"), "Should check for non-null vectors")
                assertTrue(vectorResult.sql.contains("LIMIT 5"), "Should limit results")
            }
        }
        
        // Test timestamp queries
        val timestampTables = allTables.filter { table ->
            table.columns.any { it.sqlType.uppercase().contains("TIMESTAMP") }
        }
        
        if (timestampTables.isNotEmpty()) {
            val table = timestampTables.first()
            val timestampCol = table.columns.find { it.sqlType.uppercase().contains("TIMESTAMP") }
            
            if (timestampCol != null) {
                println("\nTesting timestamp column: ${table.tableName}.${timestampCol.columnName}")
                
                val timestampQuery = TypeSafeQuery.from<Any>(table.tableName)
                    .where(Column<String>(timestampCol.columnName) gte "2024-01-01")
                    .and(Column<String>(timestampCol.columnName) lt "2024-12-31")
                    .orderBy(Column<String>(timestampCol.columnName), TypeSafeQuery.SortOrder.DESC)
                
                val timestampResult = timestampQuery.build()
                
                println("Timestamp range query:")
                println("SQL: ${timestampResult.sql}")
                
                assertTrue(timestampResult.sql.contains("${timestampCol.columnName} >= ?"), "Should have date range start")
                assertTrue(timestampResult.sql.contains("${timestampCol.columnName} < ?"), "Should have date range end")
            }
        }
    }
    
    @Test
    fun `test query error handling and edge cases`() {
        println("=== Error Handling and Edge Cases Test ===")
        
        // Test query with no conditions
        val emptyQuery = TypeSafeQuery.from<Any>("organizations")
        
        val emptyResult = emptyQuery.build()
        assertEquals("SELECT * FROM organizations", emptyResult.sql.trim(), "Should handle query with no conditions")
        assertTrue(emptyResult.parameters.isEmpty(), "Should have no parameters")
        
        // Test query with NULL checks
        val nullQuery = TypeSafeQuery.from<Any>("organizations")
            .where(Column<String>("contact_email").isNotNull())
            .and(Column<String>("main_activity").isNull())
        
        val nullResult = nullQuery.build()
        
        println("NULL checks query:")
        println("SQL: ${nullResult.sql}")
        
        assertTrue(nullResult.sql.contains("contact_email IS NOT NULL"), "Should handle IS NOT NULL")
        assertTrue(nullResult.sql.contains("main_activity IS NULL"), "Should handle IS NULL")
        
        // Test query with LIKE patterns
        val likeQuery = TypeSafeQuery.from<Any>("organizations")
            .where(Column<String>("name") like "%Corp%")
            .and(Column<String>("address_city") like "New%")
        
        val likeResult = likeQuery.build()
        
        println("LIKE patterns query:")
        println("SQL: ${likeResult.sql}")
        
        assertTrue(likeResult.sql.contains("name LIKE ?"), "Should handle LIKE operator")
        assertTrue(likeResult.sql.contains("address_city LIKE ?"), "Should handle multiple LIKE operators")
        assertEquals(listOf("%Corp%", "New%"), likeResult.parameters)
        
        // Test order by with different directions
        val orderQuery = TypeSafeQuery.from<Any>("organizations")
            .where(Column<String>("address_country") eq "USA")
            .orderBy(Column<String>("name"), TypeSafeQuery.SortOrder.ASC)
            .orderBy(Column<String>("created_at"), TypeSafeQuery.SortOrder.DESC)
        
        val orderResult = orderQuery.build()
        
        println("Multiple ORDER BY query:")
        println("SQL: ${orderResult.sql}")
        
        assertTrue(orderResult.sql.contains("ORDER BY name ASC, created_at DESC"), "Should handle multiple order by clauses")
    }
    
    @Test
    fun `test query performance and limits`() {
        println("=== Query Performance and Limits Test ===")
        
        // Test pagination with LIMIT and OFFSET
        val paginationQuery = TypeSafeQuery.from<Any>("organizations")
            .where(Column<String>("address_country") eq "USA")
            .orderBy(Column<String>("name"))
            .limit(20)
            .offset(40)
        
        val paginationResult = paginationQuery.build()
        
        println("Pagination query:")
        println("SQL: ${paginationResult.sql}")
        
        assertTrue(paginationResult.sql.contains("LIMIT 20"), "Should include LIMIT")
        assertTrue(paginationResult.sql.contains("OFFSET 40"), "Should include OFFSET")
        
        // Test large IN clause
        val largeList = (1..100).map { "item-$it" }
        val largeInQuery = TypeSafeQuery.from<Any>("organizations")
            .whereIn(Column<String>("id"), largeList)
        
        val largeInResult = largeInQuery.build()
        
        println("Large IN clause query (first 100 chars):")
        println("SQL: ${largeInResult.sql.take(100)}...")
        
        assertEquals(100, largeInResult.parameters.size, "Should have 100 parameters")
        
        // Test query building performance
        val startTime = System.currentTimeMillis()
        repeat(1000) {
            TypeSafeQuery.from<Any>("organizations")
                .where(Column<String>("name") eq "test")
                .and(Column<String>("status") eq "active")
                .orderBy(Column<String>("created_at"))
                .limit(10)
                .build()
        }
        val endTime = System.currentTimeMillis()
        
        println("Built 1000 queries in ${endTime - startTime}ms")
        assertTrue(endTime - startTime < 1000, "Query building should be fast (< 1 second for 1000 queries)")
    }
    
    private fun dev.gabrielolv.kotsql.model.SchemaRelationships.findRelationship(
        fromTable: String, 
        toTable: String
    ): RelationshipInfo? {
        return relationships.find { it.fromTable == fromTable && it.toTable == toTable }
    }
} 