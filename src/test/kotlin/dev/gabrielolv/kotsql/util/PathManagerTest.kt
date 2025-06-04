package dev.gabrielolv.kotsql.util

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PathManagerTest {
    
    @TempDir
    lateinit var tempDir: Path
    
    @Test
    fun testPackageToPath() {
        val packageName = "com.example.data"
        val path = PathManager.packageToPath(packageName)
        
        assertEquals("com", path.getName(0).toString())
        assertEquals("example", path.getName(1).toString())
        assertEquals("data", path.getName(2).toString())
    }
    
    @Test
    fun testValidatePackageName() {
        assertTrue(PathManager.validatePackageName("com.example.data"))
        assertTrue(PathManager.validatePackageName("simple"))
        assertTrue(PathManager.validatePackageName("com.example.data_layer"))
        
        // These should be false due to invalid characters or keywords
        val emptyResult = PathManager.validatePackageName("")
        println("Empty string validation result: $emptyResult")
        assertTrue(!emptyResult)
        
        val numberResult = PathManager.validatePackageName("123invalid")
        println("Number start validation result: $numberResult")
        assertTrue(!numberResult)
        
        val numberInMiddleResult = PathManager.validatePackageName("com.123invalid.data")
        println("Number in middle validation result: $numberInMiddleResult")
        assertTrue(!numberInMiddleResult)
    }
    
    @Test
    fun testFlatOrganizationStrategy() {
        val config = PathManager.PathConfig(
            outputDir = tempDir.toString(),
            organizationStrategy = PathManager.OrganizationStrategy.FLAT
        )
        
        val (packageName, directory) = PathManager.getOutputDirectory(
            config = config,
            basePackageName = "com.example.data",
            tableName = "users",
            category = PathManager.FileCategory.ENTITY
        )
        
        assertEquals("com.example.data", packageName)
        assertTrue(directory.toString().contains("com${java.io.File.separator}example${java.io.File.separator}data"))
    }
    
    @Test
    fun testFeatureBasedOrganizationStrategy() {
        val config = PathManager.PathConfig(
            outputDir = tempDir.toString(),
            organizationStrategy = PathManager.OrganizationStrategy.FEATURE_BASED
        )
        
        val (packageName, directory) = PathManager.getOutputDirectory(
            config = config,
            basePackageName = "com.example.data",
            tableName = "users",
            category = PathManager.FileCategory.ENTITY
        )
        
        assertEquals("com.example.data.users", packageName)
        assertTrue(directory.toString().contains("users"))
    }
    
    @Test
    fun testTypeBasedOrganizationStrategy() {
        val config = PathManager.PathConfig(
            outputDir = tempDir.toString(),
            organizationStrategy = PathManager.OrganizationStrategy.TYPE_BASED
        )
        
        val (entityPackage, entityDir) = PathManager.getOutputDirectory(
            config = config,
            basePackageName = "com.example.data",
            tableName = "users",
            category = PathManager.FileCategory.ENTITY
        )
        
        val (keyPackage, keyDir) = PathManager.getOutputDirectory(
            config = config,
            basePackageName = "com.example.data",
            tableName = "users",
            category = PathManager.FileCategory.KEY
        )
        
        assertEquals("com.example.data.entities", entityPackage)
        assertEquals("com.example.data.keys", keyPackage)
        assertTrue(entityDir.toString().contains("entities"))
        assertTrue(keyDir.toString().contains("keys"))
    }
    
    @Test
    fun testHybridOrganizationStrategy() {
        val config = PathManager.PathConfig(
            outputDir = tempDir.toString(),
            organizationStrategy = PathManager.OrganizationStrategy.HYBRID
        )
        
        // Entities should use feature-based
        val (entityPackage, entityDir) = PathManager.getOutputDirectory(
            config = config,
            basePackageName = "com.example.data",
            tableName = "users",
            category = PathManager.FileCategory.ENTITY
        )
        
        // Keys should use type-based
        val (keyPackage, keyDir) = PathManager.getOutputDirectory(
            config = config,
            basePackageName = "com.example.data",
            tableName = "users",
            category = PathManager.FileCategory.KEY
        )
        
        assertEquals("com.example.data.users", entityPackage)
        assertEquals("com.example.data.keys", keyPackage)
        assertTrue(entityDir.toString().contains("users"))
        assertTrue(keyDir.toString().contains("keys"))
    }
    
    @Test
    fun testGeneratedFileCreation() {
        val config = PathManager.PathConfig(
            outputDir = tempDir.toString(),
            organizationStrategy = PathManager.OrganizationStrategy.FEATURE_BASED
        )
        
        val generatedFile = PathManager.createGeneratedFile(
            config = config,
            basePackageName = "com.example.data",
            fileName = "Users",
            content = "// Test content",
            tableName = "users",
            category = PathManager.FileCategory.ENTITY
        )
        
        assertEquals("Users.kt", generatedFile.fileName)
        assertEquals("com.example.data.users", generatedFile.packageName)
        assertEquals("// Test content", generatedFile.content)
        assertEquals(PathManager.FileCategory.ENTITY, generatedFile.fileCategory)
    }
    
    @Test
    fun testGitIgnoreGeneration() {
        val config = PathManager.PathConfig(
            outputDir = "src/generated/kotlin"
        )
        
        val gitIgnoreEntries = PathManager.generateGitIgnoreEntries(config)
        
        assertTrue(gitIgnoreEntries.contains("# Generated by KotSQL"))
        assertTrue(gitIgnoreEntries.contains("src/generated/kotlin/"))
        assertTrue(gitIgnoreEntries.contains("*.generated"))
    }
    
    @Test
    fun testIndexFileGeneration() {
        val config = PathManager.PathConfig(
            generateIndexFiles = true,
            includeTimestamps = false
        )
        
        val generatedFiles = listOf(
            PathManager.GeneratedFile(
                fileName = "Users.kt",
                content = "",
                packageName = "com.example.data.users",
                relativePath = Path.of("Users.kt"),
                directory = Path.of(""),
                fileCategory = PathManager.FileCategory.ENTITY
            ),
            PathManager.GeneratedFile(
                fileName = "UsersKey.kt",
                content = "",
                packageName = "com.example.data.users",
                relativePath = Path.of("UsersKey.kt"),
                directory = Path.of(""),
                fileCategory = PathManager.FileCategory.KEY
            ),
            PathManager.GeneratedFile(
                fileName = "UsersTable.kt",
                content = "",
                packageName = "com.example.data.users",
                relativePath = Path.of("UsersTable.kt"),
                directory = Path.of(""),
                fileCategory = PathManager.FileCategory.TABLE_METADATA
            ),
            PathManager.GeneratedFile(
                fileName = "UsersQueries.kt",
                content = "",
                packageName = "com.example.data.users",
                relativePath = Path.of("UsersQueries.kt"),
                directory = Path.of(""),
                fileCategory = PathManager.FileCategory.QUERIES
            )
        )
        
        assertTrue(PathManager.shouldCreateIndexFile(config, generatedFiles.size))
        
        val indexContent = PathManager.generateIndexFileContent(
            packageName = "com.example.data.users",
            generatedFiles = generatedFiles,
            config = config
        )
        
        assertTrue(indexContent.contains("package com.example.data.users"))
        assertTrue(indexContent.contains("Generated package index"))
        assertTrue(indexContent.contains("Contains 4 generated files"))
        assertTrue(indexContent.contains("Entity files:"))
        assertTrue(indexContent.contains("- Users"))
    }
} 