package dev.gabrielolv.kotsql.test

import dev.gabrielolv.kotsql.generator.KotlinGenerator
import dev.gabrielolv.kotsql.mapping.TypeMapper
import dev.gabrielolv.kotsql.mapping.VectorConfig
import dev.gabrielolv.kotsql.mapping.VectorTypeOption
import dev.gabrielolv.kotsql.parser.SQLParser
import dev.gabrielolv.kotsql.vector.Vector
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class VectorIntegrationTest {
    
    @Test
    fun `should generate data class with vector properties`() {
        val sql = """
            CREATE TABLE document_embeddings (
                id BIGINT PRIMARY KEY,
                title VARCHAR(255) NOT NULL,
                embedding VECTOR(1536) NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
        """.trimIndent()
        
        val parser = SQLParser()
        val generator = KotlinGenerator()
        
        val tables = parser.parseSQLContent(sql)
        assertEquals(1, tables.size)
        
        val table = tables[0]
        val generatedCode = generator.generateDataClassFile(
            table = table,
            packageName = "com.example.generated",
            includeValidation = true,
            relationships = null
        )
        
        // Verify the generated code contains vector-related imports and types
        assertTrue(generatedCode.contains("import dev.gabrielolv.kotsql.vector.Vector"))
        assertTrue(generatedCode.contains("val embedding: Vector"))
        assertTrue(generatedCode.contains("data class DocumentEmbeddings"))
        
        println("Generated code:")
        println(generatedCode)
    }
    
    @Test
    fun `should work with FloatArray configuration`() {
        // Configure to use FloatArray instead of Vector wrapper
        TypeMapper.vectorConfig = VectorConfig(vectorType = VectorTypeOption.FLOAT_ARRAY)
        
        val sql = """
            CREATE TABLE embeddings (
                id INT PRIMARY KEY,
                vec VECTOR(768) NOT NULL
            );
        """.trimIndent()
        
        val parser = SQLParser()
        val tables = parser.parseSQLContent(sql)
        val table = tables[0]
        
        val vecColumn = table.columns.find { it.columnName == "vec" }!!
        assertEquals("FloatArray", TypeMapper.mapToKotlinType(vecColumn))
        
        // Reset to default
        TypeMapper.vectorConfig = VectorConfig()
    }
    
    @Test
    fun `Vector operations should work correctly`() {
        val vec1 = Vector.of(1.0f, 0.0f, 0.0f)
        val vec2 = Vector.of(0.0f, 1.0f, 0.0f)
        
        assertEquals(3, vec1.dimension)
        assertEquals(0.0f, vec1.dotProduct(vec2), 0.001f)
        assertEquals(1.0f, vec1.magnitude(), 0.001f)
        assertEquals(1.414f, vec1.euclideanDistance(vec2), 0.01f)
        assertEquals(0.0f, vec1.cosineSimilarity(vec2), 0.001f)
    }
} 