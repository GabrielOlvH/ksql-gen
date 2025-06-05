package dev.gabrielolv.kotsql.test

import dev.gabrielolv.kotsql.generator.KotlinGenerator
import dev.gabrielolv.kotsql.parser.SQLParser
import dev.gabrielolv.kotsql.util.FileManager
import dev.gabrielolv.kotsql.util.PathManager
import org.junit.jupiter.api.Test
import kotlin.test.*

class VectorImportFixTest {
    
    private val sqlParser = SQLParser()
    private val kotlinGenerator = KotlinGenerator()
    
    @Test
    fun `should include Vector import even for tables without vector columns`() {
        val sqlContent = """
            CREATE TABLE simple_table (
                id INTEGER PRIMARY KEY,
                name VARCHAR(50) NOT NULL,
                description TEXT
            );
        """.trimIndent()
        
        val tables = sqlParser.parseSQLContent(sqlContent)
        val table = tables.first()
        
        // Verify that table has no vector columns
        assertFalse(table.columns.any { it.isVectorType })
        
        val context = FileManager.TableGenerationContext(
            table = table,
            packageName = "com.example.test",
            includeValidation = false,
            relationships = null
        )
        
        val config = PathManager.PathConfig()
        val generatedFile = kotlinGenerator.generateResultSetExtensionsFile(context, config)
        
        println("Generated ResultSet extensions for table without vector columns:")
        println(generatedFile)
        
        // Should include Vector import even though table has no vector columns
        assertTrue(generatedFile.contains("import dev.gabrielolv.kotsql.vector.Vector"))
        
        // Should contain parseVector function
        assertTrue(generatedFile.contains("private fun parseVector(vectorString: String): Vector"))
        
        // Should also contain parseFloatArray function
        assertTrue(generatedFile.contains("private fun parseFloatArray(vectorString: String): FloatArray"))
    }
    
    @Test
    fun `should include Vector import for tables with vector columns`() {
        val sqlContent = """
            CREATE TABLE embeddings_table (
                id INTEGER PRIMARY KEY,
                name VARCHAR(50) NOT NULL,
                embedding VECTOR(1536) NOT NULL
            );
        """.trimIndent()
        
        val tables = sqlParser.parseSQLContent(sqlContent)
        val table = tables.first()
        
        // Verify that table has vector columns
        assertTrue(table.columns.any { it.isVectorType })
        
        val context = FileManager.TableGenerationContext(
            table = table,
            packageName = "com.example.test",
            includeValidation = false,
            relationships = null
        )
        
        val config = PathManager.PathConfig()
        val generatedFile = kotlinGenerator.generateResultSetExtensionsFile(context, config)
        
        println("Generated ResultSet extensions for table with vector columns:")
        println(generatedFile)
        
        // Should include Vector import
        assertTrue(generatedFile.contains("import dev.gabrielolv.kotsql.vector.Vector"))
        
        // Should contain parseVector function
        assertTrue(generatedFile.contains("private fun parseVector(vectorString: String): Vector"))
        
        // Should use parseVector in the extension methods
        assertTrue(generatedFile.contains("parseVector(getString(\"embedding\"))"))
    }
} 