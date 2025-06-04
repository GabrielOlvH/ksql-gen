package dev.gabrielolv.kotsql.gradle

import dev.gabrielolv.kotsql.util.PathManager
import org.gradle.api.provider.*
import javax.inject.Inject

/**
 * Configuration extension for the KotSQL Gradle plugin
 */
abstract class KotSQLExtension @Inject constructor() {
    
    /**
     * List of SQL schema files to process
     */
    abstract val sqlFiles: ListProperty<String>
    
    /**
     * Target package for generated Kotlin classes
     */
    abstract val targetPackage: Property<String>
    
    /**
     * Output directory for generated Kotlin files
     */
    abstract val outputDir: Property<String>
    
    /**
     * Source sets to include generated code in
     */
    abstract val sourceSets: ListProperty<String>
    
    /**
     * Enable validation framework generation
     */
    abstract val enableValidation: Property<Boolean>
    
    /**
     * Enable relationship detection and join queries
     */
    abstract val enableRelationships: Property<Boolean>
    
    /**
     * Enable schema validation and evolution tracking
     */
    abstract val enableSchemaValidation: Property<Boolean>
    
    /**
     * Generate migration scripts for schema changes
     */
    abstract val generateMigrations: Property<Boolean>
    
    /**
     * Output path for migration files
     */
    abstract val migrationOutputPath: Property<String>
    
    /**
     * Enable migration tracking and execution
     */
    abstract val enableMigrationTracking: Property<Boolean>
    
    /**
     * Directory containing migration files
     */
    abstract val migrationDirectory: Property<String>
    
    /**
     * Path to previous schema for comparison (optional)
     */
    abstract val previousSchemaPath: Property<String>
    
    /**
     * Organization strategy for generated files
     */
    abstract val organizationStrategy: Property<PathManager.OrganizationStrategy>
    
    /**
     * Custom base output directory
     */
    abstract val baseOutputDirectory: Property<String>
    
    /**
     * Custom generation strategy
     */
    abstract val generationStrategy: Property<String>
    
    /**
     * Custom template path
     */
    abstract val customTemplatePath: Property<String>
    
    /**
     * Enable debug output
     */
    abstract val enableDebugOutput: Property<Boolean>
    
    /**
     * Custom naming conventions configuration
     */
    abstract val customNamingConventions: MapProperty<String, String>
    
    init {
        // Set default values
        sqlFiles.convention(listOf())
        targetPackage.convention("dev.gabrielolv.generated")
        outputDir.convention("src/generated/kotlin")
        sourceSets.convention(listOf("main"))
        enableValidation.convention(true)
        enableRelationships.convention(true)
        enableSchemaValidation.convention(true)
        generateMigrations.convention(true)
        migrationOutputPath.convention("migrations")
        enableMigrationTracking.convention(true)
        migrationDirectory.convention("migrations")
        previousSchemaPath.convention("")
        organizationStrategy.convention(PathManager.OrganizationStrategy.FEATURE_BASED)
        baseOutputDirectory.convention("src/generated/kotlin")
        generationStrategy.convention("data-class")
        customTemplatePath.convention("")
        enableDebugOutput.convention(false)
        customNamingConventions.convention(mapOf())
    }
    
    /**
     * Get KSP arguments based on current configuration
     */
    fun getKspArguments(resolvedSqlFiles: List<String>? = null): Map<String, String> {
        val args = mutableMapOf<String, String>()
        
        // Core configuration - use resolved files if provided, otherwise use configured files
        val sqlFilesJoined = if (resolvedSqlFiles != null) {
            resolvedSqlFiles.joinToString(",")
        } else {
            sqlFiles.get().joinToString(",")
        }
        args["sqlSchemaPath"] = sqlFilesJoined
        args["targetPackage"] = targetPackage.get()
        args["outputDir"] = outputDir.get()
        
        // Debug: Log the exact SQL path being passed
        println("KotSQL Extension - SQL files configured: ${sqlFiles.get()}")
        println("KotSQL Extension - Resolved files: $resolvedSqlFiles")
        println("KotSQL Extension - Joined SQL path for KSP: $sqlFilesJoined")
        
        // Feature flags
        args["enableValidation"] = enableValidation.get().toString()
        args["enableRelationships"] = enableRelationships.get().toString()
        args["enableSchemaValidation"] = enableSchemaValidation.get().toString()
        args["generateMigrations"] = generateMigrations.get().toString()
        args["enableMigrationTracking"] = enableMigrationTracking.get().toString()
        
        // Migration configuration
        args["migrationOutputPath"] = migrationOutputPath.get()
        args["migrationDirectory"] = migrationDirectory.get()
        
        // Organization and generation strategy
        args["organizationStrategy"] = organizationStrategy.get().name
        args["baseOutputDirectory"] = baseOutputDirectory.get()
        args["generationStrategy"] = generationStrategy.get()
        
        // Optional configuration
        if (previousSchemaPath.get().isNotEmpty()) {
            args["previousSchemaPath"] = previousSchemaPath.get()
        }
        
        if (customTemplatePath.get().isNotEmpty()) {
            args["customTemplatePath"] = customTemplatePath.get()
        }
        
        args["enableDebugOutput"] = enableDebugOutput.get().toString()
        
        // Custom naming conventions
        customNamingConventions.get().forEach { (key, value) ->
            args["namingConvention.$key"] = value
        }
        
        return args
    }
    
    /**
     * Configure SQL files
     */
    fun sqlFiles(vararg files: String) {
        sqlFiles.set(files.toList())
    }
    
    /**
     * Configure SQL files from a list
     */
    fun sqlFiles(files: List<String>) {
        sqlFiles.set(files)
    }
    
    /**
     * Add additional SQL files
     */
    fun addSqlFile(file: String) {
        sqlFiles.add(file)
    }
    
    /**
     * Add SQL files from a directory
     */
    fun addSqlDirectory(directory: String) {
        sqlFiles.add(directory)
    }
    
    /**
     * Configure SQL directories (convenience method for multiple directories)
     */
    fun sqlDirectories(vararg directories: String) {
        val currentFiles = sqlFiles.get().toMutableList()
        currentFiles.addAll(directories)
        sqlFiles.set(currentFiles)
    }
    
    /**
     * Configure source sets
     */
    fun sourceSets(vararg sets: String) {
        sourceSets.set(sets.toList())
    }
    
    /**
     * Add a custom naming convention
     */
    fun namingConvention(key: String, value: String) {
        customNamingConventions.put(key, value)
    }
    
    /**
     * Enable all features
     */
    fun enableAllFeatures() {
        enableValidation.set(true)
        enableRelationships.set(true)
        enableSchemaValidation.set(true)
        generateMigrations.set(true)
        enableMigrationTracking.set(true)
        enableDebugOutput.set(true)
    }
    
    /**
     * Disable all optional features (minimal configuration)
     */
    fun minimalConfiguration() {
        enableValidation.set(false)
        enableRelationships.set(false)
        enableSchemaValidation.set(false)
        generateMigrations.set(false)
        enableMigrationTracking.set(false)
        enableDebugOutput.set(false)
    }
} 