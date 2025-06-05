package dev.gabrielolv.kotsql.test

import dev.gabrielolv.kotsql.generator.KotlinGenerator
import dev.gabrielolv.kotsql.model.RelationshipInfo
import dev.gabrielolv.kotsql.parser.SQLParser
import dev.gabrielolv.kotsql.relationship.RelationshipDetector
import dev.gabrielolv.kotsql.util.FileManager
import dev.gabrielolv.kotsql.util.PathManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDateTime
import kotlin.test.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ResultSetJoinIntegrationTest {
    
    private lateinit var sqlParser: SQLParser
    private lateinit var generator: KotlinGenerator
    
    @BeforeEach
    fun setup() {
        sqlParser = SQLParser()
        generator = KotlinGenerator()
    }
    
    @Test
    fun `test complex multi-table JOIN with proper column prefixing`() {
        println("=== Testing Complex Multi-Table JOIN ===")
        
        val sqlSchema = """
            CREATE TABLE organizations (
                id VARCHAR(128) PRIMARY KEY,
                name VARCHAR(255) NOT NULL,
                address_country VARCHAR(100),
                employee_count INTEGER,
                annual_revenue DECIMAL(15,2),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
            
            CREATE TABLE suppliers (
                id VARCHAR(128) PRIMARY KEY,
                name VARCHAR(255) NOT NULL,
                contact_email VARCHAR(255),
                phone VARCHAR(50),
                address TEXT,
                organization_id VARCHAR(128),
                is_active BOOLEAN DEFAULT true,
                FOREIGN KEY (organization_id) REFERENCES organizations(id)
            );
            
            CREATE TABLE purchase_orders (
                po_number VARCHAR(128) PRIMARY KEY,
                supplier_id VARCHAR(128) NOT NULL,
                organization_id VARCHAR(128) NOT NULL,
                status VARCHAR(50) NOT NULL DEFAULT 'draft',
                total_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
                date_created TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                date_ordered DATE,
                expected_delivery_date DATE,
                notes TEXT,
                FOREIGN KEY (supplier_id) REFERENCES suppliers(id),
                FOREIGN KEY (organization_id) REFERENCES organizations(id)
            );
            
            CREATE TABLE stock_products (
                id VARCHAR(128) PRIMARY KEY,
                product_code VARCHAR(128) UNIQUE,
                name VARCHAR(255) NOT NULL,
                category_id VARCHAR(128),
                selling_price DECIMAL(10,2) NOT NULL,
                cost_price DECIMAL(10,2) NOT NULL,
                quantity_in_stock INTEGER DEFAULT 0
            );
            
            CREATE TABLE purchase_order_items (
                po_id VARCHAR(128) NOT NULL,
                product_id VARCHAR(128) NOT NULL,
                quantity_ordered INTEGER NOT NULL,
                quantity_received INTEGER DEFAULT 0,
                unit_price DECIMAL(10,2) NOT NULL,
                PRIMARY KEY (po_id, product_id),
                FOREIGN KEY (po_id) REFERENCES purchase_orders(po_number),
                FOREIGN KEY (product_id) REFERENCES stock_products(id)
            );
        """.trimIndent()
        
        val tables = sqlParser.parseSQLContent(sqlSchema)
        val relationships = RelationshipDetector.generateReverseRelationships(
            RelationshipDetector.detectRelationships(tables)
        )
        
        println("Detected ${relationships.relationships.size} relationships:")
        relationships.relationships.forEach { rel ->
            println("  ${rel.fromTable}.${rel.fromColumn} -> ${rel.toTable}.${rel.toColumn}")
        }
        
        // Generate ResultSet extensions for purchase_orders
        val poTable = tables.find { it.tableName == "purchase_orders" }!!
        val context = FileManager.TableGenerationContext(
            table = poTable,
            packageName = "com.example.test",
            includeValidation = false,
            relationships = relationships,
            allTables = tables
        )
        val config = PathManager.PathConfig()
        
        val generatedCode = generator.generateResultSetExtensionsFile(context, config)
        
        println("\n=== Generated ResultSet Extensions ===")
        println(generatedCode.take(2000) + "...")
        
        // Verify the code contains smart extraction methods
        assertTrue(generatedCode.contains("tryGetColumn"), "Should contain smart column extraction")
        assertTrue(generatedCode.contains("extractSuppliersFromResultSet"), "Should have suppliers extraction")
        assertTrue(generatedCode.contains("extractOrganizationsFromResultSet"), "Should have organizations extraction")
        assertTrue(generatedCode.contains("extractStockProductsFromResultSet"), "Should have stock products extraction")
        
        // Verify it doesn't throw IllegalStateException anymore
        assertFalse(generatedCode.contains("throw IllegalStateException"), "Should not throw exceptions for table metadata")
    }
    
    @Test
    fun `test generated SQL with various JOIN alias patterns`() {
        println("=== Testing JOIN Alias Patterns ===")
        
        // Create an in-memory H2 database for testing
        val connection = DriverManager.getConnection("jdbc:h2:mem:jointest;DB_CLOSE_DELAY=-1")
        
        // Create tables
        connection.createStatement().execute("""
            CREATE TABLE suppliers (
                id VARCHAR(128) PRIMARY KEY,
                name VARCHAR(255) NOT NULL,
                contact_email VARCHAR(255),
                phone VARCHAR(50)
            )
        """)
        
        connection.createStatement().execute("""
            CREATE TABLE purchase_orders (
                po_number VARCHAR(128) PRIMARY KEY,
                supplier_id VARCHAR(128) NOT NULL,
                status VARCHAR(50) NOT NULL DEFAULT 'draft',
                total_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
                date_created TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (supplier_id) REFERENCES suppliers(id)
            )
        """)
        
        connection.createStatement().execute("""
            CREATE TABLE stock_products (
                id VARCHAR(128) PRIMARY KEY,
                name VARCHAR(255) NOT NULL,
                selling_price DECIMAL(10,2) NOT NULL
            )
        """)
        
        connection.createStatement().execute("""
            CREATE TABLE purchase_order_items (
                po_id VARCHAR(128) NOT NULL,
                product_id VARCHAR(128) NOT NULL,
                quantity_ordered INTEGER NOT NULL,
                unit_price DECIMAL(10,2) NOT NULL,
                PRIMARY KEY (po_id, product_id),
                FOREIGN KEY (po_id) REFERENCES purchase_orders(po_number),
                FOREIGN KEY (product_id) REFERENCES stock_products(id)
            )
        """)
        
        // Insert test data
        connection.prepareStatement("""
            INSERT INTO suppliers (id, name, contact_email, phone) 
            VALUES (?, ?, ?, ?)
        """).use { stmt ->
            stmt.setString(1, "SUP001")
            stmt.setString(2, "Test Supplier")
            stmt.setString(3, "supplier@test.com")
            stmt.setString(4, "123-456-7890")
            stmt.executeUpdate()
        }
        
        connection.prepareStatement("""
            INSERT INTO purchase_orders (po_number, supplier_id, status, total_amount, date_created) 
            VALUES (?, ?, ?, ?, ?)
        """).use { stmt ->
            stmt.setString(1, "PO001")
            stmt.setString(2, "SUP001")
            stmt.setString(3, "approved")
            stmt.setDouble(4, 150.00)
            stmt.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()))
            stmt.executeUpdate()
        }
        
        connection.prepareStatement("""
            INSERT INTO stock_products (id, name, selling_price) 
            VALUES (?, ?, ?)
        """).use { stmt ->
            stmt.setString(1, "PROD001")
            stmt.setString(2, "Test Product")
            stmt.setDouble(3, 50.00)
            stmt.executeUpdate()
        }
        
        connection.prepareStatement("""
            INSERT INTO purchase_order_items (po_id, product_id, quantity_ordered, unit_price) 
            VALUES (?, ?, ?, ?)
        """).use { stmt ->
            stmt.setString(1, "PO001")
            stmt.setString(2, "PROD001")
            stmt.setInt(3, 3)
            stmt.setDouble(4, 50.00)
            stmt.executeUpdate()
        }
        
        // Test various alias patterns that our smart extraction should handle
        val testQueries = listOf(
            // Standard table.column format
            """
                SELECT 
                    purchase_orders.po_number,
                    purchase_orders.status,
                    purchase_orders.total_amount,
                    suppliers.name AS supplier_name,
                    suppliers.contact_email AS supplier_email,
                    stock_products.name AS product_name,
                    purchase_order_items.quantity_ordered
                FROM purchase_orders
                LEFT JOIN suppliers ON purchase_orders.supplier_id = suppliers.id
                LEFT JOIN purchase_order_items ON purchase_orders.po_number = purchase_order_items.po_id
                LEFT JOIN stock_products ON purchase_order_items.product_id = stock_products.id
                WHERE purchase_orders.po_number = ?
            """.trimIndent(),
            
            // Mixed alias format (table_column pattern)
            """
                SELECT 
                    po.po_number AS purchase_orders_po_number,
                    po.status AS purchase_orders_status,
                    po.total_amount AS purchase_orders_total_amount,
                    s.name AS suppliers_name,
                    s.contact_email AS suppliers_contact_email,
                    sp.name AS stock_products_name,
                    poi.quantity_ordered AS purchase_order_items_quantity_ordered
                FROM purchase_orders po
                LEFT JOIN suppliers s ON po.supplier_id = s.id
                LEFT JOIN purchase_order_items poi ON po.po_number = poi.po_id
                LEFT JOIN stock_products sp ON poi.product_id = sp.id
                WHERE po.po_number = ?
            """.trimIndent(),
            
            // Complex mixed format that should test our fallback logic
            """
                SELECT 
                    po.po_number,
                    po.status,
                    po.total_amount,
                    s.name,
                    s.contact_email,
                    sp.name,
                    poi.quantity_ordered
                FROM purchase_orders po
                LEFT JOIN suppliers s ON po.supplier_id = s.id
                LEFT JOIN purchase_order_items poi ON po.po_number = poi.po_id
                LEFT JOIN stock_products sp ON poi.product_id = sp.id
                WHERE po.po_number = ?
            """.trimIndent()
        )
        
        testQueries.forEachIndexed { index, query ->
            println("\n--- Testing Query Pattern ${index + 1} ---")
            println("Query: ${query.take(100)}...")
            
            connection.prepareStatement(query).use { stmt ->
                stmt.setString(1, "PO001")
                stmt.executeQuery().use { rs ->
                    assertTrue(rs.next(), "Should return at least one row")
                    
                    // Test that we can extract data regardless of alias format
                    val poNumber = rs.getString("po_number") ?: 
                                 rs.getString("purchase_orders_po_number") ?: 
                                 rs.getString("purchase_orders.po_number")
                    
                    assertNotNull(poNumber, "Should be able to extract po_number")
                    assertEquals("PO001", poNumber)
                    
                    println("✓ Successfully extracted po_number: $poNumber")
                    
                    // Test metadata availability
                    val metaData = rs.metaData
                    println("Column count: ${metaData.columnCount}")
                    for (i in 1..metaData.columnCount) {
                        val columnName = metaData.getColumnName(i)
                        val tableName = try { metaData.getTableName(i) } catch (e: Exception) { "unknown" }
                        println("  Column $i: $columnName (table: $tableName)")
                    }
                }
            }
        }
        
        connection.close()
    }
    
    @Test
    fun `test tryGetColumn utility function behavior`() {
        println("=== Testing tryGetColumn Utility ===")
        
        // Create a mock ResultSet scenario using H2
        val connection = DriverManager.getConnection("jdbc:h2:mem:utiltest;DB_CLOSE_DELAY=-1")
        
        connection.createStatement().execute("""
            CREATE TABLE test_table (
                id BIGINT PRIMARY KEY,
                name VARCHAR(100),
                amount DECIMAL(10,2)
            )
        """)
        
        connection.prepareStatement("INSERT INTO test_table (id, name, amount) VALUES (?, ?, ?)").use { stmt ->
            stmt.setLong(1, 1L)
            stmt.setString(2, "Test Item")
            stmt.setDouble(3, 42.50)
            stmt.executeUpdate()
        }
        
        // Test various alias scenarios our tryGetColumn should handle
        val aliasTestQueries = mapOf(
            "Standard column name" to "SELECT id, name, amount FROM test_table",
            "Table prefixed" to "SELECT test_table.id, test_table.name, test_table.amount FROM test_table",
            "Mixed aliases" to "SELECT id as test_table_id, name as test_table_name, amount FROM test_table",
            "AS aliases" to "SELECT id AS test_table_id, name AS test_table_name, amount AS test_table_amount FROM test_table"
        )
        
        aliasTestQueries.forEach { (description, query) ->
            println("\n--- Testing: $description ---")
            println("Query: $query")
            
            connection.prepareStatement(query).use { stmt ->
                stmt.executeQuery().use { rs ->
                    assertTrue(rs.next(), "Should return data")
                    
                    // Test that our smart extraction logic would work
                    val possibleIdColumns = listOf("test_table.id", "test_table_id", "id")
                    val possibleNameColumns = listOf("test_table.name", "test_table_name", "name")
                    val possibleAmountColumns = listOf("test_table.amount", "test_table_amount", "amount")
                    
                    // Simulate our tryGetColumn logic
                    fun tryGetColumn(columnNames: List<String>, getter: (String) -> Any?): Any? {
                        for (columnName in columnNames) {
                            try {
                                return getter(columnName)
                            } catch (e: Exception) {
                                continue
                            }
                        }
                        throw Exception("None of the column aliases found: ${columnNames.joinToString(", ")}")
                    }
                    
                    // Test ID extraction
                    val id = tryGetColumn(possibleIdColumns) { rs.getLong(it) }
                    assertEquals(1L, id, "Should extract ID correctly")
                    
                    // Test NAME extraction
                    val name = tryGetColumn(possibleNameColumns) { rs.getString(it) }
                    assertEquals("Test Item", name, "Should extract name correctly")
                    
                    // Test AMOUNT extraction
                    val amount = tryGetColumn(possibleAmountColumns) { rs.getDouble(it) }
                    assertEquals(42.50, amount, "Should extract amount correctly")
                    
                    println("✓ Successfully extracted: id=$id, name=$name, amount=$amount")
                }
            }
        }
        
        connection.close()
    }
    
    @Test
    fun `test dual column system generates correct metadata`() {
        println("=== Testing Dual Column System Metadata ===")
        
        val simpleSchema = """
            CREATE TABLE test_table (
                id BIGINT PRIMARY KEY,
                name VARCHAR(100) NOT NULL,
                active BOOLEAN DEFAULT true,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
        """.trimIndent()
        
        val tables = sqlParser.parseSQLContent(simpleSchema)
        val table = tables.first()
        
        // Generate table metadata
        val context = FileManager.TableGenerationContext(
            table = table,
            packageName = "com.example.metadata",
            includeValidation = false,
            relationships = null,
            allTables = tables
        )
        val config = PathManager.PathConfig()
        
        val tableMetadata = generator.generateTableMetadataFile(context, config)
        
        println("Generated table metadata:")
        println(tableMetadata)
        
        // Verify dual column system is present
        assertTrue(tableMetadata.contains("object Columns {"), "Should have Columns object")
        assertTrue(tableMetadata.contains("object PrefixedColumns {"), "Should have PrefixedColumns object")
        
        // Verify regular columns
        assertTrue(tableMetadata.contains("val ID = Column<Long>(\"id\")"), "Should have regular ID column")
        assertTrue(tableMetadata.contains("val NAME = Column<String>(\"name\")"), "Should have regular NAME column")
        
        // Verify prefixed columns
        assertTrue(tableMetadata.contains("val ID = Column<Long>(\"test_table.id\")"), "Should have prefixed ID column")
        assertTrue(tableMetadata.contains("val NAME = Column<String>(\"test_table.name\")"), "Should have prefixed NAME column")
        
        // Verify shortcuts
        assertTrue(tableMetadata.contains("val Id get() = Columns.ID"), "Should have regular shortcuts")
        assertTrue(tableMetadata.contains("val PrefixedId get() = PrefixedColumns.ID"), "Should have prefixed shortcuts")
        
        println("✓ Dual column system generated correctly")
    }
    
    @Test
    fun `test no column name conflicts in complex schema`() {
        println("=== Testing No Column Name Conflicts ===")
        
        val complexSchema = """
            CREATE TABLE users (
                id BIGINT PRIMARY KEY,
                name VARCHAR(100) NOT NULL,
                email VARCHAR(255) UNIQUE,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
            
            CREATE TABLE orders (
                id BIGINT PRIMARY KEY,
                user_id BIGINT NOT NULL,
                name VARCHAR(100), -- Same column name as users.name
                total DECIMAL(10,2),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, -- Same as users.created_at
                FOREIGN KEY (user_id) REFERENCES users(id)
            );
            
            CREATE TABLE order_items (
                id BIGINT PRIMARY KEY,
                order_id BIGINT NOT NULL,
                name VARCHAR(100), -- Same column name again
                quantity INTEGER,
                price DECIMAL(10,2),
                FOREIGN KEY (order_id) REFERENCES orders(id)
            );
        """.trimIndent()
        
        val tables = sqlParser.parseSQLContent(complexSchema)
        val relationships = RelationshipDetector.generateReverseRelationships(
            RelationshipDetector.detectRelationships(tables)
        )
        
        // Generate metadata for all tables and check for conflicts
        tables.forEach { table ->
            val context = FileManager.TableGenerationContext(
                table = table,
                packageName = "com.example.conflicts",
                includeValidation = false,
                relationships = relationships,
                allTables = tables
            )
            val config = PathManager.PathConfig()
            
            val metadata = generator.generateTableMetadataFile(context, config)
            val resultSetExt = generator.generateResultSetExtensionsFile(context, config)
            
            println("\n--- Generated for ${table.tableName} ---")
            
            // Verify prefixed columns properly disambiguate
            assertTrue(metadata.contains("\"${table.tableName}.name\""), 
                "Should have table-prefixed name column for ${table.tableName}")
            
            // Verify no SQL injection or invalid syntax
            assertFalse(metadata.contains("\"\""), "Should not have empty column names")
            assertFalse(metadata.contains(".."), "Should not have double dots")
            
            // Verify ResultSet extensions handle the aliases correctly
            assertTrue(resultSetExt.contains("tryGetColumn"), "Should use smart column extraction")
            
            println("✓ ${table.tableName} metadata generated without conflicts")
        }
    }
} 