package dev.gabrielolv.kotsql.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import java.io.File

/**
 * KotSQL Gradle Plugin for processing SQL files and generating Kotlin code
 */
class KotSQLPlugin : Plugin<Project> {
    
    override fun apply(target: Project) {
        // Ensure KSP plugin is applied first
        if (!target.pluginManager.hasPlugin("com.google.devtools.ksp")) {
            target.pluginManager.apply("com.google.devtools.ksp")
        }
        
        // Ensure Kotlin JVM plugin is applied
        if (!target.pluginManager.hasPlugin("org.jetbrains.kotlin.jvm")) {
            target.pluginManager.apply("org.jetbrains.kotlin.jvm")
        }
        
        // Create the extension for configuration
        val extension = target.extensions.create("kotsql", KotSQLExtension::class.java)
        
        // Configure the plugin after evaluation when extension is configured
        target.afterEvaluate {
            configureKsp(target, extension)
            configureSourceSets(target, extension)
            configureDependencies(target)
            registerConvenienceTasks(target, extension)
        }
    }
    
    private fun configureKsp(project: Project, extension: KotSQLExtension) {
        // Configure KSP arguments using proper KSP configuration
        try {
            val kspExtension = project.extensions.findByName("ksp")
            if (kspExtension != null) {
                // Resolve SQL files including directories
                val resolvedSqlFiles = resolveSqlFiles(project, extension)
                
                // Log debugging information about file paths
                project.logger.info("KotSQL Configuration:")
                project.logger.info("  Project directory: ${project.projectDir.absolutePath}")
                project.logger.info("  SQL files/directories configured: ${extension.sqlFiles.get()}")
                project.logger.info("  Resolved SQL files: $resolvedSqlFiles")
                
                // Validate SQL files exist
                resolvedSqlFiles.forEach { sqlFile ->
                    val resolvedFile = File(sqlFile)
                    
                    if (resolvedFile.exists()) {
                        project.logger.info("  ✓ SQL file found: ${resolvedFile.absolutePath}")
                    } else {
                        project.logger.warn("  ✗ SQL file not found: ${resolvedFile.absolutePath}")
                    }
                }
                
                // Create KSP arguments with resolved files
                val kspArgs = extension.getKspArguments(resolvedSqlFiles)
                
                kspArgs.forEach { (key, value) ->
                    try {
                        // Use the proper KSP API to set arguments
                        val argMethod = kspExtension::class.java.getMethod("arg", String::class.java, String::class.java)
                        argMethod.invoke(kspExtension, key, value)
                        project.logger.info("Set KSP argument: $key = $value")
                    } catch (e: Exception) {
                        // Fallback to system properties for older KSP versions
                        System.setProperty("ksp.arg.$key", value)
                        project.logger.warn("Fallback: Set KSP argument via system property: $key = $value")
                    }
                }
            } else {
                project.logger.warn("KSP extension not found. Make sure the KSP plugin is applied.")
            }
        } catch (e: Exception) {
            project.logger.error("Failed to configure KSP: ${e.message}")
        }
    }
    
    private fun resolveSqlFiles(project: Project, extension: KotSQLExtension): List<String> {
        val resolvedFiles = mutableListOf<String>()
        
        extension.sqlFiles.get().forEach { sqlFileOrDir ->
            val resolvedPath = if (File(sqlFileOrDir).isAbsolute) {
                File(sqlFileOrDir)
            } else {
                File(project.projectDir, sqlFileOrDir)
            }
            
            when {
                resolvedPath.isFile && resolvedPath.name.endsWith(".sql") -> {
                    resolvedFiles.add(resolvedPath.absolutePath)
                }
                resolvedPath.isDirectory -> {
                    // Recursively find SQL files in directory
                    val sqlFiles = resolvedPath.walkTopDown()
                        .filter { it.isFile && it.name.endsWith(".sql") }
                        .map { it.absolutePath }
                        .toList()
                    resolvedFiles.addAll(sqlFiles)
                    project.logger.info("Found ${sqlFiles.size} SQL files in directory: ${resolvedPath.absolutePath}")
                    sqlFiles.forEach { project.logger.info("  - $it") }
                }
                else -> {
                    // Try alternative locations for files
                    val alternatives = listOf(
                        File(project.projectDir, "src/main/resources/$sqlFileOrDir"),
                        File(project.projectDir, "src/main/resources/db/${File(sqlFileOrDir).name}"),
                        File(project.projectDir, "src/main/resources/db/$sqlFileOrDir")
                    )
                    
                    val found = alternatives.find { it.exists() }
                    if (found != null) {
                        if (found.isFile) {
                            resolvedFiles.add(found.absolutePath)
                            project.logger.info("Found SQL file at alternative location: ${found.absolutePath}")
                        } else if (found.isDirectory) {
                            val sqlFiles = found.walkTopDown()
                                .filter { it.isFile && it.name.endsWith(".sql") }
                                .map { it.absolutePath }
                                .toList()
                            resolvedFiles.addAll(sqlFiles)
                            project.logger.info("Found ${sqlFiles.size} SQL files in alternative directory: ${found.absolutePath}")
                        }
                    } else {
                        project.logger.warn("SQL file/directory not found: $sqlFileOrDir")
                    }
                }
            }
        }
        
        return resolvedFiles
    }
    
    private fun configureSourceSets(project: Project, extension: KotSQLExtension) {
        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        
        extension.sourceSets.get().forEach { sourceSetName ->
            val sourceSet = sourceSets.findByName(sourceSetName) ?: return@forEach
            
            // KSP automatically generates files to build/generated/ksp/main/kotlin
            // This is the standard location and we should use it instead of fighting it
            val kspGeneratedDir = File(project.layout.buildDirectory.asFile.get(), "generated/ksp/$sourceSetName/kotlin")
            
            // Ensure the KSP output directory exists to prevent silent failures
            try {
                if (!kspGeneratedDir.exists()) {
                    kspGeneratedDir.mkdirs()
                    project.logger.info("Created KSP output directory: $kspGeneratedDir")
                }
                
                if (!kspGeneratedDir.canWrite()) {
                    project.logger.warn("KSP output directory is not writable: $kspGeneratedDir")
                }
            } catch (e: Exception) {
                project.logger.warn("Failed to create KSP output directory: ${e.message}")
            }
            
            // Add the KSP generated directory to the source set so it's included in compilation
            sourceSet.java.srcDir(kspGeneratedDir)
            
            project.logger.info("Added KSP generated directory to $sourceSetName source set: $kspGeneratedDir")
        }
    }
    
    private fun configureDependencies(project: Project) {
        // Get the plugin version using multiple strategies for reliability
        val pluginVersion = getPluginVersion(project)
        
        // Construct the dependency coordinates
        val dependencyCoordinates = "dev.gabrielolv:ksql-gen-plugin:${pluginVersion}"
        
        project.logger.info("Configuring KSP processor dependency:")
        project.logger.info("  Coordinates: $dependencyCoordinates")
        project.logger.info("  Plugin version resolved as: $pluginVersion")
        
        // Add the KSP processor dependency using the main artifact coordinates
        project.dependencies.add("ksp", dependencyCoordinates)
        project.logger.info("Added KSP processor dependency: $dependencyCoordinates")
    }
    
    private fun getPluginVersion(project: Project): String {
        // Strategy 1: Try to get version from project properties
        project.findProperty("ksql-gen.version")?.toString()?.let { version ->
            project.logger.info("Using version from project property: $version")
            return version
        }
        
        // Strategy 2: Try to get version from system properties
        System.getProperty("ksql-gen.version")?.let { version ->
            project.logger.info("Using version from system property: $version")
            return version
        }
        
        // Strategy 3: Try to get version from the plugin's own project version
        try {
            // This should get the version from the plugin jar manifest
            val pluginVersion = this.javaClass.`package`.implementationVersion
            if (pluginVersion != null && pluginVersion.isNotBlank()) {
                project.logger.info("Using version from plugin manifest: $pluginVersion")
                return pluginVersion
            }
        } catch (e: Exception) {
            project.logger.debug("Could not get version from plugin manifest: ${e.message}")
        }
        
        // Strategy 4: Try to get version from resources (build-time generated)
        try {
            this::class.java.classLoader.getResourceAsStream("ksql-gen-version.properties")?.use { stream ->
                val props = java.util.Properties()
                props.load(stream)
                val version = props.getProperty("version")
                if (version != null && version.isNotBlank()) {
                    project.logger.info("Using version from properties file: $version")
                    return version
                }
            }
        } catch (e: Exception) {
            project.logger.debug("Could not load version from ksql-gen-version.properties: ${e.message}")
        }
        
        // Strategy 5: Fallback to a known working version
        val fallbackVersion = "0.28.0"
        project.logger.warn("Could not determine plugin version, using fallback: $fallbackVersion")
        return fallbackVersion
    }
    
    private fun registerConvenienceTasks(project: Project, extension: KotSQLExtension) {
        // Register a task to run KSP generation
        project.tasks.register("generateKotSQL") { task ->
            task.group = "kotsql"
            task.description = "Generate Kotlin code from SQL schema files using KSP"
            
            // Make this task depend on the KSP task to actually generate files
            task.dependsOn("kspKotlin")
            
            // Add inputs for proper up-to-date checking
            val resolvedSqlFiles = resolveSqlFiles(project, extension)
            resolvedSqlFiles.forEach { sqlFile ->
                val file = File(sqlFile)
                if (file.exists()) {
                    task.inputs.file(file)
                }
            }
            
            // Add output directory
            val kspGeneratedDir = File(project.layout.buildDirectory.asFile.get(), "generated/ksp/main/kotlin")
            task.outputs.dir(kspGeneratedDir)
            
            task.doFirst {
                project.logger.lifecycle("Running KotSQL code generation...")
                project.logger.lifecycle("SQL files: ${resolvedSqlFiles}")
                project.logger.lifecycle("Target package: ${extension.targetPackage.get()}")
                project.logger.lifecycle("Output directory: $kspGeneratedDir")
            }
            
            task.doLast {
                val outputDir = File(project.layout.buildDirectory.asFile.get(), "generated/ksp/main/kotlin")
                if (outputDir.exists()) {
                    val generatedFiles = outputDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
                    project.logger.lifecycle("Generated ${generatedFiles.size} Kotlin files in $outputDir")
                    generatedFiles.take(10).forEach { file ->
                        project.logger.lifecycle("  - ${file.relativeTo(outputDir)}")
                    }
                    if (generatedFiles.size > 10) {
                        project.logger.lifecycle("  ... and ${generatedFiles.size - 10} more files")
                    }
                } else {
                    project.logger.warn("KSP output directory not found: $outputDir")
                }
            }
        }
        
        // Register task to force regeneration (clears KSP cache first)
        project.tasks.register("forceGenerateKotSQL") { task ->
            task.group = "kotsql"
            task.description = "Force regenerate Kotlin code from SQL schema files (clears KSP cache)"
            
            task.dependsOn("cleanKotSQL", "generateKotSQL")
            
            task.doFirst {
                project.logger.lifecycle("Force regenerating KotSQL code (clearing cache)...")
            }
        }
        
        // Register clean task
        project.tasks.register("cleanKotSQL") { task ->
            task.group = "kotsql"
            task.description = "Clean generated KotSQL files"
            
            task.doLast {
                val outputDir = File(project.layout.buildDirectory.asFile.get(), "generated/ksp")
                if (outputDir.exists()) {
                    val deleted = outputDir.deleteRecursively()
                    if (deleted) {
                        project.logger.lifecycle("Cleaned KSP generated files from: $outputDir")
                    } else {
                        project.logger.warn("Failed to clean KSP generated files")
                    }
                } else {
                    project.logger.info("No KSP generated files to clean")
                }
            }
        }
        
        // Register debug task to check directory setup
        project.tasks.register("debugKotSQL") { task ->
            task.group = "kotsql"
            task.description = "Debug KotSQL configuration and directory setup"
            
            task.doLast {
                project.logger.lifecycle("=== KotSQL Debug Information ===")
                
                // Resolve SQL files
                val resolvedSqlFiles = resolveSqlFiles(project, extension)
                
                // Configuration
                project.logger.lifecycle("Configuration:")
                project.logger.lifecycle("  SQL files/directories configured: ${extension.sqlFiles.get()}")
                project.logger.lifecycle("  Resolved SQL files: ${resolvedSqlFiles.size} files")
                project.logger.lifecycle("  Target package: ${extension.targetPackage.get()}")
                project.logger.lifecycle("  Organization strategy: ${extension.organizationStrategy.get()}")
                project.logger.lifecycle("  Enable validation: ${extension.enableValidation.get()}")
                project.logger.lifecycle("  Enable relationships: ${extension.enableRelationships.get()}")
                
                // Directory checks
                project.logger.lifecycle("\nDirectory Status:")
                
                // KSP output directory (standard location)
                val kspDir = File(project.layout.buildDirectory.asFile.get(), "generated/ksp/main/kotlin")
                project.logger.lifecycle("  KSP output dir (standard): $kspDir")
                project.logger.lifecycle("    Exists: ${kspDir.exists()}")
                project.logger.lifecycle("    Writable: ${if (kspDir.exists()) kspDir.canWrite() else "N/A"}")
                if (kspDir.exists()) {
                    val generatedFiles = kspDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.count()
                    project.logger.lifecycle("    Generated Kotlin files: $generatedFiles")
                }
                
                // SQL files resolution
                project.logger.lifecycle("\nSQL Files Resolution:")
                extension.sqlFiles.get().forEach { sqlFileOrDir ->
                    project.logger.lifecycle("  Configured: $sqlFileOrDir")
                    
                    val resolvedPath = if (File(sqlFileOrDir).isAbsolute) {
                        File(sqlFileOrDir)
                    } else {
                        File(project.projectDir, sqlFileOrDir)
                    }
                    
                    when {
                        resolvedPath.isFile -> {
                            project.logger.lifecycle("    Type: File")
                            project.logger.lifecycle("    Resolved: ${resolvedPath.absolutePath}")
                            project.logger.lifecycle("    Exists: ${resolvedPath.exists()}")
                        }
                        resolvedPath.isDirectory -> {
                            project.logger.lifecycle("    Type: Directory")
                            project.logger.lifecycle("    Resolved: ${resolvedPath.absolutePath}")
                            project.logger.lifecycle("    Exists: ${resolvedPath.exists()}")
                            if (resolvedPath.exists()) {
                                val sqlFiles = resolvedPath.walkTopDown()
                                    .filter { it.isFile && it.name.endsWith(".sql") }
                                    .toList()
                                project.logger.lifecycle("    SQL files found: ${sqlFiles.size}")
                                sqlFiles.take(5).forEach { 
                                    project.logger.lifecycle("      - ${it.name}")
                                }
                                if (sqlFiles.size > 5) {
                                    project.logger.lifecycle("      ... and ${sqlFiles.size - 5} more")
                                }
                            }
                        }
                        else -> {
                            project.logger.lifecycle("    Type: Not found")
                            project.logger.lifecycle("    Tried: ${resolvedPath.absolutePath}")
                        }
                    }
                }
                
                // Resolved SQL files list
                project.logger.lifecycle("\nResolved SQL Files (${resolvedSqlFiles.size} total):")
                resolvedSqlFiles.forEach { sqlFile ->
                    val file = File(sqlFile)
                    project.logger.lifecycle("  File: ${file.name}")
                    project.logger.lifecycle("    Path: ${file.absolutePath}")
                    project.logger.lifecycle("    Exists: ${file.exists()}")
                    project.logger.lifecycle("    Readable: ${if (file.exists()) file.canRead() else "N/A"}")
                    if (file.exists()) {
                        project.logger.lifecycle("    Size: ${file.length()} bytes")
                    }
                }
                
                // Working directory information
                project.logger.lifecycle("\nPath Resolution:")
                project.logger.lifecycle("  Project directory: ${project.projectDir.absolutePath}")
                project.logger.lifecycle("  Current working directory: ${System.getProperty("user.dir")}")
                project.logger.lifecycle("  Are they the same: ${project.projectDir.absolutePath == System.getProperty("user.dir")}")
                
                // KSP arguments
                project.logger.lifecycle("\nKSP Arguments:")
                val kspArgs = extension.getKspArguments(resolvedSqlFiles)
                kspArgs.forEach { (key, value) ->
                    project.logger.lifecycle("  $key = $value")
                }
                
                project.logger.lifecycle("\n=== End Debug Information ===")
            }
        }
    }
} 