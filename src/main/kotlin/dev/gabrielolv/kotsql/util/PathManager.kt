package dev.gabrielolv.kotsql.util

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isWritable

/**
 * Path management utility for handling output directories, source sets, 
 * and file organization strategies in the KSP processor.
 */
object PathManager {
    
    /**
     * File organization strategies
     */
    enum class OrganizationStrategy {
        FLAT,           // All files in single package directory
        FEATURE_BASED,  // Group by table/entity (users/, messages/, etc.)
        TYPE_BASED,     // Separate directories for entities, keys, metadata
        HYBRID          // Combine approaches based on project size
    }
    
    /**
     * Configuration for path management
     */
    data class PathConfig(
        val outputDir: String = "src/generated/kotlin",
        val sourceSet: String = "main",
        val organizationStrategy: OrganizationStrategy = OrganizationStrategy.FEATURE_BASED,
        val generateIndexFiles: Boolean = true,
        val includeTimestamps: Boolean = true,
        val createDirectoryStructure: Boolean = true
    )
    
    /**
     * Represents a file to be generated with its path information
     */
    data class GeneratedFile(
        val fileName: String,
        val content: String,
        val packageName: String,
        val relativePath: Path,
        val directory: Path,
        val fileCategory: FileCategory
    )
    
    /**
     * Categories of generated files
     */
    enum class FileCategory {
        ENTITY,         // Main data classes
        KEY,            // Primary key classes
        TABLE_METADATA, // Table metadata objects
        QUERIES,        // Query builder classes
        RELATIONSHIPS,  // Relationship definitions
        SCHEMA_METADATA // Schema-wide metadata
    }
    
    /**
     * Validate and normalize the output directory path
     */
    fun validateOutputDirectory(outputDir: String, projectRoot: Path? = null): Path {
        val normalizedPath = if (Paths.get(outputDir).isAbsolute) {
            Paths.get(outputDir)
        } else {
            (projectRoot ?: Paths.get(System.getProperty("user.dir"))).resolve(outputDir)
        }
        
        return normalizedPath.normalize()
    }
    
    /**
     * Convert package name to directory path
     */
    fun packageToPath(packageName: String): Path {
        val parts = packageName.split('.')
        return if (parts.size == 1) {
            Paths.get(parts[0])
        } else {
            Paths.get(parts[0], *parts.drop(1).toTypedArray())
        }
    }
    
    /**
     * Create the full directory structure for a package
     */
    fun createPackageDirectories(baseOutputDir: Path, packageName: String): Path {
        val packagePath = baseOutputDir.resolve(packageToPath(packageName))
        
        if (!packagePath.exists()) {
            Files.createDirectories(packagePath)
        }
        
        return packagePath
    }
    
    /**
     * Get the output directory for a specific file category based on organization strategy
     */
    fun getOutputDirectory(
        config: PathConfig,
        basePackageName: String,
        tableName: String?,
        category: FileCategory
    ): Pair<String, Path> {
        val baseOutputDir = validateOutputDirectory(config.outputDir)
        
        return when (config.organizationStrategy) {
            OrganizationStrategy.FLAT -> {
                val packageName = basePackageName
                val packagePath = createPackageDirectories(baseOutputDir, packageName)
                packageName to packagePath
            }
            
            OrganizationStrategy.FEATURE_BASED -> {
                val featurePackage = if (tableName != null) {
                    "$basePackageName.${tableName.lowercase()}"
                } else {
                    basePackageName
                }
                val packagePath = createPackageDirectories(baseOutputDir, featurePackage)
                featurePackage to packagePath
            }
            
            OrganizationStrategy.TYPE_BASED -> {
                val typePackage = when (category) {
                    FileCategory.ENTITY -> "$basePackageName.entities"
                    FileCategory.KEY -> "$basePackageName.keys"
                    FileCategory.TABLE_METADATA -> "$basePackageName.tables"
                    FileCategory.QUERIES -> "$basePackageName.queries"
                    FileCategory.RELATIONSHIPS -> "$basePackageName.relationships"
                    FileCategory.SCHEMA_METADATA -> basePackageName
                }
                val packagePath = createPackageDirectories(baseOutputDir, typePackage)
                typePackage to packagePath
            }
            
            OrganizationStrategy.HYBRID -> {
                // Use feature-based for entities, type-based for other categories
                if (category == FileCategory.ENTITY && tableName != null) {
                    val featurePackage = "$basePackageName.${tableName.lowercase()}"
                    val packagePath = createPackageDirectories(baseOutputDir, featurePackage)
                    featurePackage to packagePath
                } else {
                    val typePackage = when (category) {
                        FileCategory.KEY -> "$basePackageName.keys"
                        FileCategory.TABLE_METADATA -> "$basePackageName.tables"
                        FileCategory.QUERIES -> "$basePackageName.queries"
                        FileCategory.RELATIONSHIPS -> "$basePackageName.relationships"
                        else -> basePackageName
                    }
                    val packagePath = createPackageDirectories(baseOutputDir, typePackage)
                    typePackage to packagePath
                }
            }
        }
    }
    
    /**
     * Validate that a directory is writable and accessible
     */
    fun validateDirectoryAccess(directory: Path): Boolean {
        return try {
            if (!directory.exists()) {
                Files.createDirectories(directory)
            }
            
            directory.isDirectory() && directory.isWritable()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Generate a safe filename from a class name
     */
    fun generateSafeFileName(className: String): String {
        return className.replace(Regex("[^a-zA-Z0-9_]"), "_") + ".kt"
    }
    
    /**
     * Get the relative path from base output directory to a specific file
     */
    fun getRelativePath(baseOutputDir: Path, filePath: Path): Path {
        return baseOutputDir.relativize(filePath)
    }
    
    /**
     * Create a GeneratedFile instance with proper path information
     */
    fun createGeneratedFile(
        config: PathConfig,
        basePackageName: String,
        fileName: String,
        content: String,
        tableName: String?,
        category: FileCategory
    ): GeneratedFile {
        val (packageName, directory) = getOutputDirectory(config, basePackageName, tableName, category)
        val safeFileName = generateSafeFileName(fileName.removeSuffix(".kt"))
        val filePath = directory.resolve(safeFileName)
        val baseOutputDir = validateOutputDirectory(config.outputDir)
        val relativePath = getRelativePath(baseOutputDir, filePath)
        
        return GeneratedFile(
            fileName = safeFileName,
            content = content,
            packageName = packageName,
            relativePath = relativePath,
            directory = directory,
            fileCategory = category
        )
    }
    
    /**
     * Ensure proper path separators for the current operating system
     */
    fun normalizePath(path: String): String {
        return path.replace('/', File.separatorChar).replace('\\', File.separatorChar)
    }
    
    /**
     * Validate package name follows Java/Kotlin naming conventions
     */
    fun validatePackageName(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        
        val parts = packageName.split('.')
        
        // Check if any part is empty (happens when there are consecutive dots or leading/trailing dots)
        if (parts.any { it.isBlank() }) return false
        
        return parts.all { part ->
            // Each part must be a valid Java identifier (letters, digits, underscore, starting with letter or underscore)
            part.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_]*$"))
            // Note: We don't check for Kotlin keywords because they are valid in package names
        }
    }
    
    /**
     * Ensure all necessary directories exist for the given configuration
     * This method should be called before any file generation begins
     */
    fun ensureDirectoryStructure(
        config: PathConfig,
        basePackageName: String,
        logger: ((String) -> Unit)? = null
    ): Boolean {
        try {
            val baseOutputDir = validateOutputDirectory(config.outputDir)
            logger?.invoke("Ensuring directory structure exists for output: $baseOutputDir")
            
            // Create base output directory
            if (!baseOutputDir.exists()) {
                Files.createDirectories(baseOutputDir)
                logger?.invoke("Created base output directory: $baseOutputDir")
            }
            
            // Create base package directory
            val basePackageDir = createPackageDirectories(baseOutputDir, basePackageName)
            logger?.invoke("Ensured base package directory: $basePackageDir")
            
            // Create directories for each organization strategy
            when (config.organizationStrategy) {
                OrganizationStrategy.TYPE_BASED -> {
                    val typeDirs = listOf("entities", "keys", "tables", "queries", "relationships")
                    typeDirs.forEach { type ->
                        val typePackage = "$basePackageName.$type"
                        createPackageDirectories(baseOutputDir, typePackage)
                        logger?.invoke("Created type-based directory: $type")
                    }
                }
                OrganizationStrategy.HYBRID -> {
                    val typeDirs = listOf("keys", "tables", "queries", "relationships")
                    typeDirs.forEach { type ->
                        val typePackage = "$basePackageName.$type"
                        createPackageDirectories(baseOutputDir, typePackage)
                        logger?.invoke("Created hybrid directory: $type")
                    }
                }
                // FLAT and FEATURE_BASED create directories on demand
                else -> {
                    logger?.invoke("Using ${config.organizationStrategy} strategy - directories created on demand")
                }
            }
            
            // Validate final directory access
            if (!validateDirectoryAccess(baseOutputDir)) {
                logger?.invoke("ERROR: Cannot write to base output directory: $baseOutputDir")
                return false
            }
            
            logger?.invoke("Directory structure validation completed successfully")
            return true
            
        } catch (e: Exception) {
            logger?.invoke("ERROR: Failed to ensure directory structure: ${e.message}")
            return false
        }
    }
    
    /**
     * Generate .gitignore entries for generated directories
     */
    fun generateGitIgnoreEntries(config: PathConfig): List<String> {
        val baseDir = config.outputDir.trimEnd('/', '\\')
        return listOf(
            "# Generated by KotSQL",
            "$baseDir/",
            "*.generated",
            "# End KotSQL generated"
        )
    }
    
    /**
     * Create index files for packages when beneficial
     */
    fun shouldCreateIndexFile(config: PathConfig, fileCount: Int): Boolean {
        return config.generateIndexFiles && fileCount > 3
    }
    
    /**
     * Generate index file content for a package
     */
    fun generateIndexFileContent(
        packageName: String,
        generatedFiles: List<GeneratedFile>,
        config: PathConfig
    ): String {
        return buildString {
            appendLine("package $packageName")
            appendLine()
            appendLine("/**")
            appendLine(" * Generated package index")
            if (config.includeTimestamps) {
                appendLine(" * Generated on: ${java.time.LocalDateTime.now()}")
            }
            appendLine(" * Contains ${generatedFiles.size} generated files")
            appendLine(" */")
            appendLine()
            
            val filesByCategory = generatedFiles.groupBy { it.fileCategory }
            
            filesByCategory.forEach { (category, files) ->
                appendLine("// ${category.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }} files:")
                files.forEach { file ->
                    val className = file.fileName.removeSuffix(".kt")
                    appendLine("// - $className")
                }
                appendLine()
            }
        }
    }
} 