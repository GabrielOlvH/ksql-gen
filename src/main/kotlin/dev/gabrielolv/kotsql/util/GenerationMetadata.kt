package dev.gabrielolv.kotsql.util

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Utility for handling generation metadata, including timestamps, source file tracking,
 * and support for incremental generation.
 */
object GenerationMetadata {
    
    /**
     * Metadata about a generation run
     */
    data class GenerationInfo(
        val timestamp: Instant,
        val sourceFileHash: String,
        val processorVersion: String,
        val generatedFiles: List<String>,
        val sourceFile: String
    )
    
    /**
     * Generate a hash for a source file to detect changes
     */
    fun generateFileHash(file: File): String {
        val content = file.readText()
        val md = MessageDigest.getInstance("SHA-256")
        val hashBytes = md.digest(content.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Generate a hash for a source file path
     */
    fun generateFileHash(filePath: Path): String {
        val content = filePath.readText()
        val md = MessageDigest.getInstance("SHA-256")
        val hashBytes = md.digest(content.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Create generation metadata
     */
    fun createGenerationInfo(
        sourceFile: String,
        sourceFileHash: String,
        generatedFiles: List<String>,
        processorVersion: String = "1.0.0"
    ): GenerationInfo {
        return GenerationInfo(
            timestamp = Clock.System.now(),
            sourceFileHash = sourceFileHash,
            processorVersion = processorVersion,
            generatedFiles = generatedFiles,
            sourceFile = sourceFile
        )
    }
    
    /**
     * Save generation metadata to a .generated file
     */
    fun saveGenerationMetadata(metadata: GenerationInfo, outputDirectory: Path) {
        val metadataFile = outputDirectory.resolve(".generated")
        val content = buildString {
            appendLine("# KotSQL Generation Metadata")
            appendLine("timestamp=${metadata.timestamp}")
            appendLine("sourceFile=${metadata.sourceFile}")
            appendLine("sourceFileHash=${metadata.sourceFileHash}")
            appendLine("processorVersion=${metadata.processorVersion}")
            appendLine("generatedFileCount=${metadata.generatedFiles.size}")
            appendLine()
            appendLine("# Generated Files:")
            metadata.generatedFiles.forEach { file ->
                appendLine("file=$file")
            }
        }
        
        Files.write(metadataFile, content.toByteArray())
    }
    
    /**
     * Load generation metadata from a .generated file
     */
    fun loadGenerationMetadata(outputDirectory: Path): GenerationInfo? {
        val metadataFile = outputDirectory.resolve(".generated")
        if (!metadataFile.exists()) {
            return null
        }
        
        return try {
            val lines = Files.readAllLines(metadataFile)
            val properties = mutableMapOf<String, String>()
            val generatedFiles = mutableListOf<String>()
            
            lines.forEach { line ->
                when {
                    line.startsWith("timestamp=") -> properties["timestamp"] = line.substringAfter("=")
                    line.startsWith("sourceFile=") -> properties["sourceFile"] = line.substringAfter("=")
                    line.startsWith("sourceFileHash=") -> properties["sourceFileHash"] = line.substringAfter("=")
                    line.startsWith("processorVersion=") -> properties["processorVersion"] = line.substringAfter("=")
                    line.startsWith("file=") -> generatedFiles.add(line.substringAfter("="))
                }
            }
            
            GenerationInfo(
                timestamp = Instant.parse(properties["timestamp"] ?: return null),
                sourceFileHash = properties["sourceFileHash"] ?: return null,
                processorVersion = properties["processorVersion"] ?: "unknown",
                generatedFiles = generatedFiles,
                sourceFile = properties["sourceFile"] ?: return null
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Check if regeneration is needed based on source file changes
     */
    fun shouldRegenerate(
        sourceFile: File,
        outputDirectory: Path
    ): Boolean {
        val currentHash = generateFileHash(sourceFile)
        val existingMetadata = loadGenerationMetadata(outputDirectory)
        
        return existingMetadata == null || 
               existingMetadata.sourceFileHash != currentHash ||
               existingMetadata.sourceFile != sourceFile.absolutePath
    }
    
    /**
     * Clean up old generated files that are no longer needed
     */
    fun cleanupOldFiles(
        currentGeneratedFiles: List<String>,
        outputDirectory: Path
    ) {
        val existingMetadata = loadGenerationMetadata(outputDirectory) ?: return
        
        val filesToRemove = existingMetadata.generatedFiles - currentGeneratedFiles.toSet()
        
        filesToRemove.forEach { fileName ->
            val file = outputDirectory.resolve(fileName)
            if (file.exists()) {
                try {
                    Files.delete(file)
                } catch (e: Exception) {
                    // Log warning but continue
                }
            }
        }
    }
    
    /**
     * Generate file header with generation information
     */
    fun generateFileHeader(
        sourceFile: String,
        timestamp: Instant = Clock.System.now(),
        includeWarning: Boolean = true
    ): String {
        return buildString {
            appendLine("/**")
            appendLine(" * Generated by KotSQL")
            appendLine(" * Source: $sourceFile")
            appendLine(" * Generated on: $timestamp")
            if (includeWarning) {
                appendLine(" * ")
                appendLine(" * WARNING: This file is auto-generated. Do not modify directly.")
                appendLine(" * Any changes will be overwritten on next generation.")
            }
            appendLine(" */")
        }
    }
    
    /**
     * Generate build configuration files
     */
    fun generateBuildConfigurationFiles(config: PathManager.PathConfig): Map<String, String> {
        val files = mutableMapOf<String, String>()
        
        // Generate .gitignore entries
        files[".gitignore.generated"] = PathManager.generateGitIgnoreEntries(config).joinToString("\n")
        
        // Generate Gradle configuration snippet
        files["gradle-config.snippet"] = generateGradleConfigSnippet(config)
        
        // Generate README for generated structure
        files["README-generated.md"] = generateStructureDocumentation(config)
        
        return files
    }
    
    /**
     * Generate Gradle configuration snippet
     */
    private fun generateGradleConfigSnippet(config: PathManager.PathConfig): String {
        return buildString {
            appendLine("// KotSQL Generated Source Set Configuration")
            appendLine("// Add this to your build.gradle.kts file")
            appendLine()
            appendLine("kotlin {")
            appendLine("    sourceSets {")
            appendLine("        ${config.sourceSet} {")
            appendLine("            kotlin.srcDirs(\"${config.outputDir}\")")
            appendLine("        }")
            appendLine("    }")
            appendLine("}")
            appendLine()
            appendLine("// KSP Configuration")
            appendLine("ksp {")
            appendLine("    arg(\"outputDir\", \"${config.outputDir}\")")
            appendLine("    arg(\"sourceSet\", \"${config.sourceSet}\")")
            appendLine("    arg(\"organizationStrategy\", \"${config.organizationStrategy.name.lowercase()}\")")
            appendLine("    arg(\"generateIndexFiles\", \"${config.generateIndexFiles}\")")
            appendLine("    arg(\"includeTimestamps\", \"${config.includeTimestamps}\")")
            appendLine("}")
        }
    }
    
    /**
     * Generate documentation for the generated structure
     */
    private fun generateStructureDocumentation(config: PathManager.PathConfig): String {
        return buildString {
            appendLine("# KotSQL Generated Code Structure")
            appendLine()
            appendLine("This directory contains code generated by KotSQL from your SQL schema files.")
            appendLine()
            appendLine("## Configuration")
            appendLine("- **Output Directory:** `${config.outputDir}`")
            appendLine("- **Source Set:** `${config.sourceSet}`")
            appendLine("- **Organization Strategy:** `${config.organizationStrategy}`")
            appendLine("- **Generate Index Files:** `${config.generateIndexFiles}`")
            appendLine("- **Include Timestamps:** `${config.includeTimestamps}`")
            appendLine()
            appendLine("## Organization Strategy: ${config.organizationStrategy}")
            when (config.organizationStrategy) {
                PathManager.OrganizationStrategy.FLAT -> {
                    appendLine("All generated files are placed in a single package directory.")
                }
                PathManager.OrganizationStrategy.FEATURE_BASED -> {
                    appendLine("Files are organized by table/entity in separate subdirectories:")
                    appendLine("```")
                    appendLine("com/example/data/")
                    appendLine("├── users/")
                    appendLine("│   ├── Users.kt")
                    appendLine("│   ├── UsersKey.kt")
                    appendLine("│   └── UsersTable.kt")
                    appendLine("└── messages/")
                    appendLine("    ├── Messages.kt")
                    appendLine("    └── MessagesTable.kt")
                    appendLine("```")
                }
                PathManager.OrganizationStrategy.TYPE_BASED -> {
                    appendLine("Files are organized by type in separate subdirectories:")
                    appendLine("```")
                    appendLine("com/example/data/")
                    appendLine("├── entities/")
                    appendLine("│   ├── Users.kt")
                    appendLine("│   └── Messages.kt")
                    appendLine("├── keys/")
                    appendLine("│   └── UsersKey.kt")
                    appendLine("└── tables/")
                    appendLine("    ├── UsersTable.kt")
                    appendLine("    └── MessagesTable.kt")
                    appendLine("```")
                }
                PathManager.OrganizationStrategy.HYBRID -> {
                    appendLine("Uses feature-based organization for entities, type-based for other files.")
                }
            }
            appendLine()
            appendLine("## Important Notes")
            appendLine("- **Do not modify generated files directly** - they will be overwritten")
            appendLine("- Files marked with `// Generated by KotSQL` are auto-generated")
            appendLine("- Check the `.generated` metadata files for generation information")
            appendLine("- Add the output directory to your `.gitignore` if desired")
            appendLine()
            appendLine("## Regeneration")
            appendLine("Files are automatically regenerated when:")
            appendLine("- The source SQL schema file changes")
            appendLine("- The processor configuration changes")
            appendLine("- Manual clean build is performed")
        }
    }
} 