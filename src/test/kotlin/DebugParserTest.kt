import dev.gabrielolv.kotsql.parser.SQLParser

fun main() {
    val sqlContent = """
        CREATE TABLE test_table (
            id INTEGER PRIMARY KEY,
            user_name VARCHAR(50) NOT NULL,
            created_at TIMESTAMP,
            is_active BOOLEAN,
            balance DECIMAL(10,2),
            data BLOB
        );
    """.trimIndent()
    
    println("=== DEBUGGING SQL PARSER ===")
    println("Input SQL:")
    println(sqlContent)
    println()
    
    val parser = SQLParser()
    val tables = parser.parseSQLContent(sqlContent)
    
    println("Parsed ${tables.size} tables:")
    tables.forEach { table ->
        println("Table: ${table.tableName}")
        println("Columns (${table.columns.size}):")
        table.columns.forEach { column ->
            println("  - ${column.columnName}: ${column.sqlType} (nullable: ${column.isNullable}, pk: ${column.isPrimaryKey})")
        }
    }
} 