plugins {
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.serialization") version "2.1.21"
    id("com.google.devtools.ksp") version "2.1.21-2.0.1"
    `maven-publish`
    `java-gradle-plugin`
}

group = "dev.gabrielolv"
version = "0.79.0"

repositories {
    mavenCentral()
}

dependencies {
    // KSP API for building the processor
    implementation("com.google.devtools.ksp:symbol-processing-api:2.1.21-2.0.1")

    // Kotlinx Serialization for generated data classes
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    // Kotlinx DateTime for SQL timestamp mapping
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")

    // Kotlin Reflection for validation engine
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.1.21")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("com.h2database:h2:2.2.224") // H2 database for migration testing
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

// Generate version properties file for reliable version detection
tasks.register("generateVersionProperties") {
    val outputDir = file("src/main/resources")
    outputs.dir(outputDir)
    
    doLast {
        outputDir.mkdirs()
        val versionFile = file("$outputDir/ksql-gen-version.properties")
        versionFile.writeText("version=${project.version}\n")
    }
}

// Make sure the version file is generated before processing resources
tasks.named("processResources") {
    dependsOn("generateVersionProperties")
}

// Make sure the version file is generated before creating sources jar
tasks.withType<Jar> {
    if (name == "sourcesJar") {
        dependsOn("generateVersionProperties")
    }
}

// KSP Configuration is now handled by the KotSQL plugin
// Example usage in a consuming project:
// kotsql {
//     sqlFiles.set(listOf("src/main/resources/schema.sql"))
//     // or sqlDirectories("src/main/resources/db")
//     targetPackage.set("dev.gabrielolv.generated")
//     enableValidation.set(true)
//     enableRelationships.set(true)
//     enableSchemaValidation.set(true)
//     generateMigrations.set(true)
//     migrationOutputPath.set("migrations")
//     enableMigrationTracking.set(true)
//     migrationDirectory.set("migrations")
//     // previousSchemaPath.set("src/main/resources/previous_schema.sql")
// }

// Configure sources jar
java {
    withSourcesJar()
}

// Gradle Plugin Configuration
gradlePlugin {
    plugins {
        create("ksql-gen-plugin") {
            id = "dev.gabrielolv.ksql-gen-plugin"
            implementationClass = "dev.gabrielolv.kotsql.gradle.KotSQLPlugin"
            displayName = "KSQL-Gen Gradle Plugin"
            description = "Generate Kotlin code from SQL schema files using KSP"
        }
    }
}

// Publishing Configuration for Reposilite
publishing {
    publications {
        // Ensure the main jar is published (contains the KSP processor)
        create<MavenPublication>("maven") {
            from(components["java"])
            
            artifactId = "ksql-gen-plugin"
            groupId = "dev.gabrielolv"
            version = project.version.toString()
        }
    }
    
    repositories {
        maven {
            name = "Reposilite"
            url = uri("https://maven.gabrielolv.dev/releases")
            
            credentials {
                username = System.getenv("REPOSILITE_USER")
                password = System.getenv("REPOSILITE_TOKEN")
            }
        }
    }
}