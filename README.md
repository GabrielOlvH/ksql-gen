# KotSQL - SQL to Kotlin Data Class Generator

A comprehensive **Kotlin Symbol Processing (KSP)** tool that automatically generates type-safe Kotlin data classes from SQL schema definitions. Features validation, relationships, schema evolution tracking, and migration generation capabilities.

## 🚀 Features

### **Core Functionality (Phases 1-4)**
- **SQL Schema Parsing**: Automatically parse CREATE TABLE statements from SQL files
- **Kotlin Data Class Generation**: Generate `@Serializable` data classes with proper type mapping
- **Column Constants**: Create typed column references for type-safe queries
- **Type-Safe Query Builder**: Fluent API for building SQL queries with compile-time validation

### **Phase 5: Validation Framework**
- **Automatic Validation Annotations**: Maps SQL constraints to Kotlin validation annotations
- **Runtime Validation Engine**: Reflection-based validation with detailed error reporting
- **Smart Pattern Detection**: Detects emails, usernames, phone numbers from SQL patterns
- **Validation Helper Methods**: Generated validation utilities for each data class

### **Phase 6: Relationship Mapping**
- **Automatic Relationship Detection**: Identifies foreign keys and table relationships
- **JOIN Query Support**: Type-safe JOIN operations with relationship-based queries
- **Many-to-Many Support**: Junction table detection and many-to-many relationship handling
- **Bidirectional Relationships**: Generates both directions of One-to-Many/Many-to-One relationships

### **Phase 7: Schema Validation** ✨ **NEW**
- **Schema Evolution Tracking**: Compare schema versions and detect changes
- **Breaking Change Detection**: Identifies backward compatibility issues
- **Automatic Migration Generation**: Creates SQL migration scripts for schema changes
- **Constraint Validation**: Validates schema integrity and detects potential issues
- **Semantic Versioning Support**: Recommends version bumps based on change severity

## 📦 Installation

### Prerequisites
- Kotlin 2.1.21+
- Gradle 8.0+
- Java 21+

### Gradle Setup

```kotlin
plugins {
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.serialization") version "2.1.21"
    id("com.google.devtools.ksp") version "2.1.21-2.0.1"
}

dependencies {
    // KSP API for building the processor
    implementation("com.google.devtools.ksp:symbol-processing-api:2.1.21-2.0.1")
    
    // Required runtime dependencies
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.1.21")
}

// KSP Configuration
ksp {
    arg("sqlSchemaPath", "src/main/resources/schema.sql")
    arg("targetPackage", "dev.gabrielolv.generated")
    arg("enableValidation", "true")           // Enable validation framework
    arg("enableRelationships", "true")        // Enable relationship detection
    arg("enableSchemaValidation", "true")     // Enable schema validation ✨ NEW
    arg("generateMigrations", "true")         // Generate migration scripts ✨ NEW
    arg("migrationOutputPath", "migrations")  // Directory for migrations ✨ NEW
    // arg("previousSchemaPath", "src/main/resources/previous_schema.sql") // For comparison ✨ NEW
}
```

## 🔧 Configuration Options

| Option | Default | Description |
|--------|---------|-------------|
| `sqlSchemaPath` | `src/main/resources/schema.sql` | Path to SQL schema file |
| `targetPackage` | `generated` | Target package for generated classes |
| `enableValidation` | `true` | Enable validation annotations and engine |
| `enableRelationships` | `true` | Enable relationship detection and JOINs |
| `enableSchemaValidation` | `true` | ✨ Enable schema validation and tracking |
| `generateMigrations` | `false` | ✨ Generate migration scripts |
| `migrationOutputPath` | `migrations` | ✨ Directory for migration files |
| `previousSchemaPath` | `null` | ✨ Previous schema for comparison |

## 📝 Usage Examples

### Basic SQL Schema
```sql
-- src/main/resources/schema.sql
CREATE TABLE users (
    id INTEGER PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL,
    age INTEGER CHECK (age >= 0),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE posts (
    id BIGINT PRIMARY KEY,
    user_id INTEGER NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    published_at TIMESTAMP
);

CREATE TABLE post_categories (
    post_id BIGINT NOT NULL,
    category_id INTEGER NOT NULL,
    PRIMARY KEY (post_id, category_id)
);

CREATE TABLE categories (
    id INTEGER PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);
```

### Generated Data Classes

```kotlin
@Serializable
data class Users(
    val id: Int,
    @Length(max = 50) @NotBlank val username: String,
    @Email @Length(max = 100) val email: String,
    @Range(min = 0) val age: Int?,
    val createdAt: Instant?
)

object UsersMetadata {
    const val TABLE_NAME = "users"
    
    object Columns {
        const val ID = "id"
        const val USERNAME = "username"
        const val EMAIL = "email"
        const val AGE = "age"
        const val CREATED_AT = "created_at"
        
        val id = Column<Int>("id")
        val username = Column<String>("username")
        val email = Column<String>("email")
        val age = Column<Int>("age")
        val createdAt = Column<Instant>("created_at")
    }
    
    object Relationships {
        val userPosts = RelationshipInfo.OneToMany(
            fromTable = "users",
            toTable = "posts", 
            fromColumn = "id",
            toColumn = "user_id"
        )
    }
    
    fun query(): TypeSafeQuery<Users> = TypeSafeQuery.from(TABLE_NAME)
    fun joinQuery(): JoinQuery<Users> = JoinQuery.from(TABLE_NAME)
    fun findById(id: Int) = query().where(Columns.id, id)
}
```

### Type-Safe Queries

```kotlin
// Simple queries
val userQuery = UsersMetadata.query()
    .where(UsersMetadata.Columns.age greaterThan 18)
    .orderBy(UsersMetadata.Columns.username)
    .limit(10)

// JOIN queries with relationships
val userPostsQuery = UsersMetadata.joinQuery()
    .innerJoin(UsersMetadata.Relationships.userPosts, "p")
    .where(UsersMetadata.Columns.username like "john%")
    .orderBy("p", PostsMetadata.Columns.publishedAt, SortOrder.DESC)

// Many-to-many JOINs
val postCategoriesQuery = PostsMetadata.joinQuery()
    .manyToManyJoin(PostsMetadata.Relationships.postCategories, "c")
    .where(CategoriesMetadata.Columns.name eq "Technology")
```

### Validation

```kotlin
val user = Users(
    id = 1,
    username = "john_doe",
    email = "invalid-email", // Will fail validation
    age = -5,                // Will fail validation
    createdAt = Clock.System.now()
)

// Validate with detailed results
val validationResult = UsersValidation.validate(user)
if (!validationResult.isValid) {
    validationResult.errors.forEach { error ->
        println("${error.field}: ${error.message}")
    }
}

// Validate and throw on failure
try {
    UsersValidation.validateAndThrow(user)
} catch (e: ValidationException) {
    println("Validation failed: ${e.errors}")
}
```

## 🔄 Schema Validation & Migration ✨ **NEW**

### Schema Evolution Tracking

KotSQL can automatically detect changes between schema versions and generate migration scripts:

```kotlin
// Compare two schema versions
val previousSchema = SchemaVersion(/* ... */)
val currentSchema = SchemaVersion(/* ... */)

val validationResult = SchemaValidator.validateSchemaChange(previousSchema, currentSchema)

println(validationResult.getSummary())
// Output:
// Schema validation from 1.0.0 to 2.0.0:
// - Major changes: 0
// - Minor changes: 3
// - Patch changes: 0
// - Backward compatible: true
// - Recommended version bump: MINOR
```

### Change Detection

KotSQL detects various types of schema changes:

- **Table Changes**: Added, removed, renamed tables
- **Column Changes**: Added, removed, type changes, nullability changes
- **Relationship Changes**: New or removed foreign key relationships
- **Breaking Changes**: Incompatible modifications that break backward compatibility

### Migration Generation

```kotlin
val migration = MigrationGenerator.generateMigration(validationResult)

println(migration.upScript)
// Output:
// -- Forward migration
// -- Table 'posts' was added
// CREATE TABLE posts (
//     id BIGINT PRIMARY KEY,
//     user_id INTEGER NOT NULL,
//     title VARCHAR(200) NOT NULL,
//     content TEXT NOT NULL
// );
//
// -- Column 'email' was added to table 'users'
// ALTER TABLE users ADD COLUMN email VARCHAR(100) NOT NULL;

migration.saveToFiles("migrations/")
// Saves: 20241215_add_posts_table_up.sql
//        20241215_add_posts_table_down.sql
```

### Schema Constraint Validation

```kotlin
val issues = SchemaValidator.validateSchemaConstraints(schema)

issues.forEach { issue ->
    when (issue.severity) {
        IssueSeverity.ERROR -> logger.error(issue.description)
        IssueSeverity.WARNING -> logger.warn(issue.description)
        IssueSeverity.INFO -> logger.info(issue.description)
    }
}
```

Common issues detected:
- Tables without primary keys
- Foreign key references to non-existent tables
- Circular dependency chains
- Orphaned relationships

### KSP Integration

Enable schema validation in your build configuration:

```kotlin
ksp {
    // ... other args ...
    arg("enableSchemaValidation", "true")
    arg("generateMigrations", "true")
    arg("migrationOutputPath", "src/main/resources/migrations")
    arg("previousSchemaPath", "src/main/resources/schema_v1.sql")
}
```

When KSP runs, it will:
1. Parse current schema from `sqlSchemaPath`
2. Compare with previous schema (if provided)
3. Generate change analysis and migration scripts
4. Validate schema constraints
5. Output detailed logging of all changes and recommendations

## ⚡ Phase 8: Migration Support

**Phase 8** introduces a comprehensive migration execution and tracking system that safely manages database schema evolution in production environments.

### 🎯 Key Features

- **Migration Tracking**: Track applied migrations in database
- **Safe Execution**: Transaction-based migration execution with rollback
- **Migration History**: Complete history of applied migrations with timing
- **Rollback Support**: Intelligent rollback to specific versions
- **Dry Run**: Validate migrations without executing them
- **Batch Operations**: Execute multiple migrations atomically
- **Migration Validation**: Pre-execution validation of SQL syntax

### Migration Manager

The high-level API for migration management:

```kotlin
import dev.gabrielolv.kotsql.migration.MigrationManager
import java.sql.DriverManager

// Create migration manager
val connection = DriverManager.getConnection("jdbc:postgresql://localhost/mydb", "user", "pass")
val manager = MigrationManager.createDefault(connection)

// Initialize migration tracking
manager.initialize()

// Check current status
val status = manager.getStatus()
println(status)
// Migration Status:
//   Current version: v1.2.3
//   Progress: 5/7 (71.4%)
//   Pending migrations: 2
//   Up to date: false

// Execute all pending migrations
val result = manager.upgrade()
if (result.overallSuccess) {
    println("All migrations completed successfully!")
    println("Applied ${result.successCount} migrations in ${result.totalExecutionTimeMs}ms")
} else {
    println("Migration failed: ${result.failureCount} failures")
}
```

### Migration Execution Modes

```kotlin
val config = MigrationRunner.MigrationConfig(
    transactionMode = TransactionMode.PER_MIGRATION, // Each migration in its own transaction
    dryRun = false,                                  // Actually execute migrations
    continueOnError = false,                         // Stop on first failure
    timeoutSeconds = 300                             // 5 minute timeout per migration
)

val runner = MigrationRunner(connection, tracker, config)

// Execute single migration
val migration = loadMigration("20241215_add_user_table")
val result = runner.executeMigration(migration)

// Execute batch with different transaction modes
val batchConfig = config.copy(transactionMode = TransactionMode.BATCH)
val batchRunner = MigrationRunner(connection, tracker, batchConfig)
val batchResult = batchRunner.executeBatch(migrations)
```

### Migration Tracking

Automatic tracking of migration history:

```kotlin
val tracker = MigrationTracker(connection)
tracker.initialize()

// Check if migration was applied
if (tracker.isMigrationApplied("20241215_add_users")) {
    println("Migration already applied")
}

// Get migration history
val history = tracker.getAppliedMigrations()
history.forEach { record ->
    println("${record.name}: ${record.status} (${record.executionTimeMs}ms)")
}

// Get current schema version
val currentVersion = tracker.getCurrentSchemaVersion()
println("Current schema version: $currentVersion")

// Get migration summary
val summary = tracker.getMigrationSummary()
println(summary)
// Migration Summary:
//   Total migrations: 12
//   Completed: 10
//   Failed: 1
//   Rolled back: 1
//   Success rate: 83.3%
//   Total execution time: 2847ms
//   Average execution time: 237ms
//   Current version: v1.4.2
```

### Rollback Operations

Safe rollback to previous versions:

```kotlin
// Rollback specific migration
val rollbackResult = manager.rollback("20241215_add_posts_table")
if (rollbackResult.success) {
    println("Successfully rolled back migration")
} else {
    println("Rollback failed: ${rollbackResult.error?.message}")
}

// Rollback to specific version
val versionRollback = manager.rollbackTo("v1.2.0")
println("Rolled back ${versionRollback.successCount} migrations")

// Only reversible migrations can be rolled back
val migration = Migration(
    name = "add_index_users_email",
    // ... other properties ...
    isReversible = true,  // Must have valid down script
    downScript = "DROP INDEX idx_users_email;"
)
```

### Migration File Management

Automatic discovery and loading:

```kotlin
// Migration files follow naming convention:
// migrations/
//   ├── 20241215120000_create_users_up.sql
//   ├── 20241215120000_create_users_down.sql
//   ├── 20241215130000_add_posts_table_up.sql
//   └── 20241215130000_add_posts_table_down.sql

val loader = MigrationManager.MigrationLoader("migrations")
val migrations = loader.loadMigrations()

// Generate migration from schema changes
val validationResult = SchemaValidator.validateSchemaChange(oldSchema, newSchema)
val migration = manager.generateMigration(validationResult, "add_user_preferences")
// Automatically saves to migration directory
```

### Dry Run and Validation

Test migrations before execution:

```kotlin
// Validate all migrations
val validation = manager.validateMigrations()
println(validation)
// Migration Validation Summary:
//   Total migrations: 5
//   Valid: 4
//   Invalid: 1
//   Validation rate: 80.0%
//   Issues found:
//     - Invalid SQL syntax in up script: near "SELEC": syntax error

// Perform dry run
val dryRunResult = manager.dryRun()
if (dryRunResult.overallSuccess) {
    println("All pending migrations are valid and ready to execute")
} else {
    println("Dry run failed - fix issues before migration")
}
```

### Advanced Configuration

```kotlin
val config = MigrationManager.MigrationManagerConfig(
    migrationDirectory = "src/main/resources/migrations",
    autoInit = true,                                    // Auto-initialize tracking
    runnerConfig = MigrationRunner.MigrationConfig(
        validateChecksums = true,                       // Verify migration integrity
        dryRun = false,                                 // Execute for real
        continueOnError = false,                        // Stop on first failure
        transactionMode = TransactionMode.PER_MIGRATION, // Transaction strategy
        timeoutSeconds = 300,                           // Migration timeout
        maxRetries = 0                                  // Retry attempts
    ),
    backupBeforeMigration = false,                      // Future: automatic backups
    validateIntegrity = true                            // Checksum validation
)

val manager = MigrationManager(connection, config)
```

### Migration Status and Monitoring

```kotlin
// Get detailed migration status
val status = manager.getStatus()

println("Schema up to date: ${status.isUpToDate()}")
println("Completion: ${String.format("%.1f", status.getCompletionPercentage())}%")

if (status.lastMigrationAt != null) {
    println("Last migration: ${status.lastMigrationAt}")
}

// Export migration history
manager.exportMigrations("backup/migrations")

// Reset tracking (dangerous - development only)
if (isDevelopmentEnvironment) {
    manager.resetTracking()
}
```

### Error Handling and Recovery

```kotlin
try {
    val result = manager.upgrade()
    if (!result.overallSuccess) {
        // Handle partial failure
        result.results.forEach { migrationResult ->
            if (!migrationResult.success) {
                logger.error("Migration ${migrationResult.migration.name} failed: ${migrationResult.error?.message}")
                
                // Attempt automatic rollback if migration was reversible
                if (migrationResult.migration.isReversible) {
                    val rollback = manager.rollback(migrationResult.migration.name)
                    if (rollback.success) {
                        logger.info("Successfully rolled back failed migration")
                    }
                }
            }
        }
    }
} catch (e: MigrationException) {
    logger.error("Critical migration failure: ${e.message}")
    // Handle critical failure - may require manual intervention
}
```

### Integration with CI/CD

```kotlin
// CI/CD pipeline integration
fun deploymentMigration() {
    val manager = MigrationManager.create(
        jdbcUrl = System.getenv("DATABASE_URL"),
        username = System.getenv("DB_USER"),
        password = System.getenv("DB_PASSWORD")
    )
    
    // Always validate before applying
    val validation = manager.validateMigrations()
    if (!validation.isAllValid()) {
        throw IllegalStateException("Migration validation failed: ${validation.allIssues}")
    }
    
    // Perform dry run in staging
    if (System.getenv("ENVIRONMENT") == "staging") {
        val dryRun = manager.dryRun()
        if (!dryRun.overallSuccess) {
            throw IllegalStateException("Dry run failed")
        }
    }
    
    // Execute migrations
    val result = manager.upgrade()
    if (!result.overallSuccess) {
        throw IllegalStateException("Migration failed: ${result.failureCount} failures")
    }
    
    println("Successfully applied ${result.successCount} migrations")
}
```

## 🏗️ Architecture

### Core Components

```
kotsql/
├── model/               # Data models (SQLTableInfo, RelationshipInfo, etc.)
├── parser/              # SQL parsing engine
├── mapping/             # Type mapping and validation mapping
├── generator/           # Kotlin code generation
├── query/               # Type-safe query framework
├── validation/          # Validation annotations and engine
├── relationship/        # Relationship detection and JOIN queries
├── schema/              # Schema validation and migration generation
│   ├── SchemaVersion.kt     # Schema version models
│   ├── SchemaValidator.kt   # Change detection and validation
│   └── MigrationGenerator.kt # SQL migration generation
├── migration/           # Migration execution and tracking ✨ NEW
│   ├── MigrationTracker.kt  # Migration history tracking
│   ├── MigrationRunner.kt   # Safe migration execution
│   └── MigrationManager.kt  # High-level migration API
├── processor/           # KSP processor integration
└── util/                # Utilities and naming conventions
```

### Type Mapping

| SQL Type | Kotlin Type | Validation |
|----------|-------------|------------|
| `INTEGER`, `INT` | `Int` | `@Range` |
| `BIGINT` | `Long` | `@Range` |
| `VARCHAR(n)` | `String` | `@Length`, `@Pattern`, `@Email` |
| `TEXT` | `String` | `@NotBlank` |
| `DECIMAL(p,s)` | `BigDecimal` | `@DecimalRange` |
| `BOOLEAN` | `Boolean` | - |
| `TIMESTAMP` | `Instant` | - |
| `DATE` | `LocalDate` | - |
| `TIME` | `LocalTime` | - |

## 🧪 Testing

Run the comprehensive test suite:

```bash
./gradlew test
```

### Test Coverage

- **SQL Parsing**: Various SQL dialects and edge cases
- **Code Generation**: Generated code validation and compilation
- **Type Mapping**: All supported SQL to Kotlin type mappings
- **Validation Framework**: Annotation mapping and runtime validation
- **Relationship Detection**: Foreign keys, junction tables, circular dependencies
- **JOIN Queries**: All join types and many-to-many relationships
- **Schema Validation**: Change detection, migration generation, constraint validation
- **Migration Support**: Migration tracking, execution, rollback, validation ✨ NEW

## 🔄 Migration from Earlier Versions

### From Phase 7 to Phase 8

Phase 8 is fully backward compatible. Simply update your KSP configuration to enable the new features:

```kotlin
ksp {
    // Existing configuration...
    arg("enableSchemaValidation", "true")        // Phase 7
    arg("generateMigrations", "true")            // Phase 7
    arg("migrationOutputPath", "migrations")     // Phase 7
    arg("enableMigrationTracking", "true")       // ✨ NEW Phase 8
    arg("migrationDirectory", "migrations")      // ✨ NEW Phase 8
}
```

No changes to generated code are required. All new functionality is additive.

## 🗺️ Roadmap

### Completed ✅
- **Phase 1-2**: Basic SQL parsing and data class generation
- **Phase 3-4**: Column constants and type-safe queries
- **Phase 5**: Validation framework with runtime engine
- **Phase 6**: Relationship mapping and JOIN queries
- **Phase 7**: Schema validation and migration generation
- **Phase 8**: Migration execution and tracking system ✅

### Upcoming 🚧
- **Phase 9**: Performance Optimization
  - Query optimization hints
  - Index recommendations
  - Bulk operation support
  - Connection pooling integration
  - Query result caching
  - Performance monitoring

### Future Considerations 🌟
- **Database-Specific Features**: PostgreSQL arrays, JSON columns, custom types
- **Multi-Database Support**: Oracle, SQL Server, SQLite optimizations
- **Advanced Relationships**: Polymorphic associations, inheritance mapping
- **GraphQL Integration**: Automatic GraphQL schema generation
- **Microservice Support**: Cross-service relationship mapping

## 🤝 Contributing

1. **Fork the repository**
2. **Create a feature branch**: `git checkout -b feature/amazing-feature`
3. **Commit your changes**: `git commit -m 'Add amazing feature'`
4. **Push to the branch**: `git push origin feature/amazing-feature`
5. **Open a Pull Request**

### Development Setup

```bash
git clone https://github.com/yourusername/kotsql.git
cd kotsql
./gradlew build
./gradlew test
```

## 📄 License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- **Kotlin Symbol Processing (KSP)** team for the excellent compile-time processing framework
- **kotlinx.serialization** for JSON serialization support
- **kotlinx.datetime** for modern date/time handling
- **JetBrains** for the amazing Kotlin ecosystem

## 📞 Support

- **Issues**: [GitHub Issues](https://github.com/yourusername/kotsql/issues)
- **Discussions**: [GitHub Discussions](https://github.com/yourusername/kotsql/discussions)
- **Documentation**: [Wiki](https://github.com/yourusername/kotsql/wiki)

---

**KotSQL** - Making SQL and Kotlin work together seamlessly with type safety, validation, relationships, intelligent schema evolution, and production-ready migration management! 🚀