package dev.gabrielolv.kotsql.test

import dev.gabrielolv.kotsql.model.RelationshipInfo
import dev.gabrielolv.kotsql.query.JoinQuery
import dev.gabrielolv.kotsql.query.innerJoinChain
import dev.gabrielolv.kotsql.query.PaginationParams
import dev.gabrielolv.kotsql.query.paginate
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

// Simulating generated table metadata with proper relationship naming
class PurchaseOrdersTable {
    companion object {
        const val TABLE_NAME = "purchase_orders"
        
        object Relationships {
            val purchaseOrdersToPurchaseOrderItems = RelationshipInfo.OneToMany(
                fromTable = "purchase_orders",
                toTable = "purchase_order_items",
                fromColumn = "po_number", 
                toColumn = "po_id"
            )
            
            val purchaseOrdersToSuppliers = RelationshipInfo.ManyToOne(
                fromTable = "purchase_orders",
                toTable = "suppliers",
                fromColumn = "supplier_id",
                toColumn = "id",
                isOptional = false
            )
        }
        
        fun joinQuery() = JoinQuery.from<Any>(TABLE_NAME)
    }
}

class PurchaseOrderItemsTable {
    companion object {
        object Relationships {
            val purchaseOrderItemsToStockProducts = RelationshipInfo.ManyToOne(
                fromTable = "purchase_order_items",
                toTable = "stock_products", 
                fromColumn = "product_id",
                toColumn = "id",
                isOptional = false
            )
        }
    }
}

class FluentJoinQueryTest {
    
    @Test
    fun `test fluent query API for purchase orders with items and products`() {
        // Define relationships for the chain: purchase_orders -> purchase_order_items -> stock_products
        val purchaseOrdersToItems = RelationshipInfo.OneToMany(
            fromTable = "purchase_orders",
            toTable = "purchase_order_items", 
            fromColumn = "po_number",
            toColumn = "po_id"
        )
        
        val itemsToProducts = RelationshipInfo.ManyToOne(
            fromTable = "purchase_order_items",
            toTable = "stock_products",
            fromColumn = "product_id", 
            toColumn = "id",
            isOptional = false
        )
        
        // This is the fluent API you want to achieve
        val query = JoinQuery.from<Any>("purchase_orders")
            .innerJoin(purchaseOrdersToItems)
            .innerJoin(itemsToProducts)
            .paginate(0, 20) // page 1 (0-indexed), 20 items per page
        
        val result = query.build()
        
        println("Generated SQL:")
        println(result.sql)
        
        // Verify the SQL contains the expected JOINs
        assertTrue(result.sql.contains("INNER JOIN purchase_order_items"), "Should join purchase_order_items")
        assertTrue(result.sql.contains("INNER JOIN stock_products"), "Should join stock_products")
        assertTrue(result.sql.contains("purchase_orders.po_number = purchase_order_items.po_id"), "Should have correct first join condition")
        assertTrue(result.sql.contains("purchase_order_items.product_id = stock_products.id"), "Should have correct second join condition")
        assertTrue(result.sql.contains("LIMIT 20"), "Should include pagination limit")
        assertTrue(result.sql.contains("OFFSET 0"), "Should include pagination offset")
    }
    
    @Test
    fun `test fluent query API with helper methods`() {
        val purchaseOrdersToItems = RelationshipInfo.OneToMany(
            fromTable = "purchase_orders",
            toTable = "purchase_order_items", 
            fromColumn = "po_number",
            toColumn = "po_id"
        )
        
        val itemsToProducts = RelationshipInfo.ManyToOne(
            fromTable = "purchase_order_items",
            toTable = "stock_products",
            fromColumn = "product_id", 
            toColumn = "id",
            isOptional = false
        )
        
        // Using the helper method for chaining
        val query = JoinQuery.from<Any>("purchase_orders")
            .innerJoinChain(purchaseOrdersToItems, itemsToProducts)
            .paginate(PaginationParams(page = 1, pageSize = 50))
        
        val result = query.build()
        
        println("Generated SQL with helper:")
        println(result.sql)
        
        assertTrue(result.sql.contains("INNER JOIN purchase_order_items"), "Should join purchase_order_items")
        assertTrue(result.sql.contains("INNER JOIN stock_products"), "Should join stock_products")
        assertTrue(result.sql.contains("LIMIT 50"), "Should include pagination limit")
        assertTrue(result.sql.contains("OFFSET 50"), "Should include pagination offset (page 1 * 50)")
    }
    
    @Test
    fun `test table metadata relationship navigation`() {
        // This test demonstrates how the generated table metadata would work
        // with your enhanced relationship structure
        
        println("=== Enhanced Table Relationships Example ===")
        
        // This is exactly the fluent API you want:
        val desiredQuery = PurchaseOrdersTable.joinQuery()
            .innerJoin(PurchaseOrdersTable.Companion.Relationships.purchaseOrdersToPurchaseOrderItems)
            .innerJoin(PurchaseOrderItemsTable.Companion.Relationships.purchaseOrderItemsToStockProducts)
            .paginate(0, 20)
        
        val result = desiredQuery.build()
        
        println("Your desired fluent API result:")
        println(result.sql)
        
        assertTrue(result.sql.contains("FROM purchase_orders"), "Should start from purchase_orders")
        assertTrue(result.sql.contains("INNER JOIN purchase_order_items"), "Should join to items table")
        assertTrue(result.sql.contains("INNER JOIN stock_products"), "Should join to products table")
        
        println("✓ Fluent API working as expected!")
    }
    
    @Test
    fun `test complex multi-table join with suppliers AND items AND products`() {
        // This demonstrates the enhanced multi-table relationship detection
        println("=== Complex Multi-Table Join Test ===")
        
        val purchaseOrdersToItems = RelationshipInfo.OneToMany(
            fromTable = "purchase_orders",
            toTable = "purchase_order_items", 
            fromColumn = "po_number",
            toColumn = "po_id"
        )
        
        val itemsToProducts = RelationshipInfo.ManyToOne(
            fromTable = "purchase_order_items",
            toTable = "stock_products",
            fromColumn = "product_id", 
            toColumn = "id",
            isOptional = false
        )
        
        val purchaseOrdersToSuppliers = RelationshipInfo.ManyToOne(
            fromTable = "purchase_orders",
            toTable = "suppliers",
            fromColumn = "supplier_id",
            toColumn = "id",
            isOptional = false
        )
        
        // Now you can join with ALL THREE relationships in one query:
        val complexQuery = PurchaseOrdersTable.joinQuery()
            .innerJoin(purchaseOrdersToSuppliers)           // Join with suppliers
            .innerJoin(purchaseOrdersToItems)               // Join with purchase_order_items
            .innerJoin(itemsToProducts)                     // Join with stock_products
            .paginate(0, 20)
        
        val result = complexQuery.build()
        
        println("Complex multi-table join SQL:")
        println(result.sql)
        
        // Verify ALL tables are joined
        assertTrue(result.sql.contains("INNER JOIN suppliers"), "Should join suppliers")
        assertTrue(result.sql.contains("INNER JOIN purchase_order_items"), "Should join purchase_order_items")
        assertTrue(result.sql.contains("INNER JOIN stock_products"), "Should join stock_products")
        assertTrue(result.sql.contains("purchase_orders.supplier_id = suppliers.id"), "Should have supplier join condition")
        assertTrue(result.sql.contains("purchase_orders.po_number = purchase_order_items.po_id"), "Should have items join condition")
        assertTrue(result.sql.contains("purchase_order_items.product_id = stock_products.id"), "Should have products join condition")
        
        println("✓ Complex multi-table join working perfectly!")
        
        // The enhanced ResultSet detection should now be able to handle this complex query
        // and populate ALL relationships: suppliers, purchaseOrderItems, AND stockProducts
    }
} 