package dev.gabrielolv.kotsql.test

import dev.gabrielolv.kotsql.mapping.TypeMapper
import dev.gabrielolv.kotsql.mapping.VectorConfig
import dev.gabrielolv.kotsql.mapping.VectorTypeOption
import dev.gabrielolv.kotsql.parser.SQLParser
import dev.gabrielolv.kotsql.vector.Vector
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

class VectorSupportTest {
    
    private val parser = SQLParser()
    
    @BeforeEach
    fun setUp() {
        // Reset vector configuration to default
        TypeMapper.vectorConfig = VectorConfig()
    }
    
    @Test
    fun `should parse VECTOR column type with dimension`() {
        val sql = """
            CREATE TABLE embeddings (
                id BIGINT PRIMARY KEY,
                title VARCHAR(255) NOT NULL,
                embedding VECTOR(1536) NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
        """.trimIndent()
        
        val tables = parser.parseSQLContent(sql)
        assertEquals(1, tables.size)
        
        val table = tables[0]
        assertEquals("embeddings", table.tableName)
        assertEquals(4, table.columns.size)
        
        val embeddingColumn = table.columns.find { it.columnName == "embedding" }
        assertNotNull(embeddingColumn)
        assertTrue(embeddingColumn!!.isVectorType)
        assertEquals(1536, embeddingColumn.vectorDimension)
        assertEquals("VECTOR(1536)", embeddingColumn.sqlType)
        assertFalse(embeddingColumn.isNullable)
    }
    
    @Test
    fun `should parse VECTOR column type without dimension`() {
        val sql = """
            CREATE TABLE documents (
                id BIGINT PRIMARY KEY,
                content TEXT,
                embedding VECTOR
            );
        """.trimIndent()
        
        val tables = parser.parseSQLContent(sql)
        val table = tables[0]
        
        val embeddingColumn = table.columns.find { it.columnName == "embedding" }
        assertNotNull(embeddingColumn)
        assertTrue(embeddingColumn!!.isVectorType)
        assertNull(embeddingColumn.vectorDimension)
        assertEquals("VECTOR", embeddingColumn.sqlType)
    }
    
    @Test
    fun `should map VECTOR type to Vector wrapper by default`() {
        val sql = """
            CREATE TABLE test_vectors (
                id INT PRIMARY KEY,
                vec VECTOR(768) NOT NULL,
                nullable_vec VECTOR(512)
            );
        """.trimIndent()
        
        val tables = parser.parseSQLContent(sql)
        val table = tables[0]
        
        val vecColumn = table.columns.find { it.columnName == "vec" }!!
        val nullableVecColumn = table.columns.find { it.columnName == "nullable_vec" }!!
        
        assertEquals("Vector", TypeMapper.mapToKotlinType(vecColumn))
        assertEquals("Vector?", TypeMapper.mapToKotlinType(nullableVecColumn))
    }
    
    @Test
    fun `should map VECTOR type to FloatArray when configured`() {
        TypeMapper.vectorConfig = VectorConfig(vectorType = VectorTypeOption.FLOAT_ARRAY)
        
        val sql = """
            CREATE TABLE test_vectors (
                id INT PRIMARY KEY,
                vec VECTOR(768) NOT NULL
            );
        """.trimIndent()
        
        val tables = parser.parseSQLContent(sql)
        val table = tables[0]
        
        val vecColumn = table.columns.find { it.columnName == "vec" }!!
        assertEquals("FloatArray", TypeMapper.mapToKotlinType(vecColumn))
    }
    
    @Test
    fun `should include Vector import when using Vector wrapper`() {
        val sql = """
            CREATE TABLE test_vectors (
                id INT PRIMARY KEY,
                vec VECTOR(768) NOT NULL
            );
        """.trimIndent()
        
        val tables = parser.parseSQLContent(sql)
        val table = tables[0]
        
        val imports = TypeMapper.getAllRequiredImports(table.columns)
        assertTrue(imports.contains("dev.gabrielolv.kotsql.vector.Vector"))
    }
    
    @Test
    fun `Vector should perform basic operations correctly`() {
        val vec1 = Vector.of(1.0f, 2.0f, 3.0f)
        val vec2 = Vector.of(4.0f, 5.0f, 6.0f)
        
        assertEquals(3, vec1.dimension)
        assertEquals(32.0f, vec1.dotProduct(vec2), 0.001f)
        assertEquals(3.7416575f, vec1.magnitude(), 0.001f)
        assertEquals(5.196152f, vec1.euclideanDistance(vec2), 0.001f)
        assertEquals(0.9746318f, vec1.cosineSimilarity(vec2), 0.001f)
    }
    
    @Test
    fun `Vector should validate dimensions for operations`() {
        val vec1 = Vector.of(1.0f, 2.0f, 3.0f)
        val vec2 = Vector.of(4.0f, 5.0f) // Different dimension
        
        assertThrows(IllegalArgumentException::class.java) {
            vec1.dotProduct(vec2)
        }
        
        assertThrows(IllegalArgumentException::class.java) {
            vec1.euclideanDistance(vec2)
        }
    }
    
    @Test
    fun `Vector should normalize correctly`() {
        val vec = Vector.of(3.0f, 4.0f, 0.0f)
        val normalized = vec.normalize()
        
        assertEquals(1.0f, normalized.magnitude(), 0.001f)
        assertTrue(normalized.isNormalized())
    }
    
    @Test
    fun `Vector should create zero and random vectors`() {
        val zeros = Vector.zeros(5)
        assertEquals(5, zeros.dimension)
        assertEquals(0.0f, zeros.magnitude(), 0.001f)
        
        val random = Vector.random(10)
        assertEquals(10, random.dimension)
        assertTrue(random.magnitude() > 0)
        
        val randomUnit = Vector.randomUnit(8)
        assertEquals(8, randomUnit.dimension)
        assertTrue(randomUnit.isNormalized(0.01f))
    }
    
    @Test
    fun `Vector should serialize and deserialize as JSON array`() {
        val original = Vector.of(0.1f, 0.2f, 0.3f, 0.4f)
        
        val json = Json.encodeToString(Vector.serializer(), original)
        assertEquals("[0.1,0.2,0.3,0.4]", json)
        
        val deserialized = Json.decodeFromString(Vector.serializer(), json)
        assertEquals(original.dimension, deserialized.dimension)
        assertArrayEquals(original.values, deserialized.values, 0.001f)
    }
    
    @Test
    fun `Vector should validate float values`() {
        val validVector = Vector.of(1.0f, 2.0f, 3.0f)
        assertDoesNotThrow { validVector.validate() }
        
        val nanVector = Vector(floatArrayOf(1.0f, Float.NaN, 3.0f))
        assertThrows(IllegalStateException::class.java) { nanVector.validate() }
        
        val infiniteVector = Vector(floatArrayOf(1.0f, Float.POSITIVE_INFINITY, 3.0f))
        assertThrows(IllegalStateException::class.java) { infiniteVector.validate() }
    }
    
    @Test
    fun `should handle case insensitive VECTOR types`() {
        val sql = """
            CREATE TABLE mixed_case (
                id INT PRIMARY KEY,
                vec1 vector(100),
                vec2 Vector(200),
                vec3 VECTOR(300)
            );
        """.trimIndent()
        
        val tables = parser.parseSQLContent(sql)
        val table = tables[0]
        
        val vec1 = table.columns.find { it.columnName == "vec1" }!!
        val vec2 = table.columns.find { it.columnName == "vec2" }!!
        val vec3 = table.columns.find { it.columnName == "vec3" }!!
        
        assertTrue(vec1.isVectorType)
        assertTrue(vec2.isVectorType)
        assertTrue(vec3.isVectorType)
        
        assertEquals(100, vec1.vectorDimension)
        assertEquals(200, vec2.vectorDimension)
        assertEquals(300, vec3.vectorDimension)
    }
} 