package dev.gabrielolv.kotsql.test

import dev.gabrielolv.kotsql.generator.KotlinGenerator
import dev.gabrielolv.kotsql.parser.SQLParser
import dev.gabrielolv.kotsql.util.FileManager
import dev.gabrielolv.kotsql.util.PathManager
import org.junit.jupiter.api.Test

class SimpleResultSetTest {
    
    @Test
    fun `simple test to see generated output`() {
        val sqlContent = """
            CREATE TABLE users (
                id INTEGER PRIMARY KEY,
                name VARCHAR(50) NOT NULL
            );
        """.trimIndent()
        
        val sqlParser = SQLParser()
        val kotlinGenerator = KotlinGenerator()
        
        val tables = sqlParser.parseSQLContent(sqlContent)
        val table = tables.first()
        
        val context = FileManager.TableGenerationContext(
            table = table,
            packageName = "com.example.test",
            includeValidation = false,
            relationships = null
        )
        
        val config = PathManager.PathConfig()
        val generatedFile = kotlinGenerator.generateResultSetExtensionsFile(context, config)
        
        println("=== GENERATED RESULTSET EXTENSIONS ===")
        println(generatedFile)
        println("=== END OF GENERATED FILE ===")
        
        // Just check that we get something
        assert(generatedFile.isNotBlank())
        assert(generatedFile.contains("package com.example.test"))
    }
} 