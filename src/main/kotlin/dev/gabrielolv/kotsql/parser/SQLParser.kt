package dev.gabrielolv.kotsql.parser

import dev.gabrielolv.kotsql.model.SQLColumnInfo
import dev.gabrielolv.kotsql.model.SQLConstraint
import dev.gabrielolv.kotsql.model.SQLTableInfo
import java.io.File

/**
 * SQL parser that can read .sql files and extract CREATE TABLE statements.
 * Supports common SQL syntax variations while maintaining simplicity.
 */
class SQLParser {
    
    /**
     * Parse a SQL file and extract table information
     */
    fun parseFile(sqlFile: File): List<SQLTableInfo> {
        if (!sqlFile.exists()) {
            throw IllegalArgumentException("SQL file does not exist: ${sqlFile.absolutePath}")
        }
        
        val content = sqlFile.readText()
        return parseSQLContent(content)
    }
    
    /**
     * Parse SQL content string and extract table information
     */
    fun parseSQLContent(content: String): List<SQLTableInfo> {
        val tables = mutableListOf<SQLTableInfo>()
        
        // Remove comments and normalize whitespace
        val cleanContent = removeComments(content)
        
        // Find all CREATE TABLE statements using a more robust approach
        val createTablePattern = Regex(
            """CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(\w+)\s*\(""",
            setOf(RegexOption.IGNORE_CASE)
        )
        
        var startIndex = 0
        while (true) {
            val match = createTablePattern.find(cleanContent, startIndex) ?: break
            
            val tableName = match.groupValues[1]
            val tableStartIndex = match.range.last + 1 // Start after the opening '('
            
            // Find the matching closing parenthesis
            val tableContent = extractBalancedParentheses(cleanContent, tableStartIndex)
            
            if (tableContent != null) {
                try {
                    val result = parseColumns(tableContent)
                    if (result.columns.isNotEmpty()) {
                        tables.add(SQLTableInfo(tableName, result.columns, result.compositePrimaryKey))
                    }
                } catch (e: Exception) {
                    // Log the error but continue parsing other tables
                    println("Warning: Failed to parse table '$tableName': ${e.message}")
                }
            }
            
            startIndex = match.range.last + 1
        }
        
        return tables
    }
    
    /**
     * Remove SQL comments from content
     */
    private fun removeComments(content: String): String {
        return content
            .lines()
            .map { line ->
                // Remove single-line comments (-- comment)
                val commentIndex = line.indexOf("--")
                if (commentIndex >= 0) {
                    line.substring(0, commentIndex)
                } else {
                    line
                }
            }
            .joinToString("\n")
            // Remove multi-line comments (/* comment */)
            .replace(Regex("/\\*.*?\\*/", setOf(RegexOption.DOT_MATCHES_ALL)), "")
    }
    
    /**
     * Data class to hold parsing results including composite key information
     */
    private data class TableParsingResult(
        val columns: List<SQLColumnInfo>,
        val compositePrimaryKey: List<String>
    )
    
    /**
     * Parse column definitions from table content
     */
    private fun parseColumns(tableContent: String): TableParsingResult {
        val columns = mutableListOf<SQLColumnInfo>()
        val tableLevelConstraints = mutableListOf<String>()
        
        // Split by commas, but be careful about commas inside function calls
        val columnDefinitions = splitColumnDefinitions(tableContent)
        
        columnDefinitions.forEach { definition ->
            val trimmed = definition.trim()
            
            // Separate table-level constraints from column definitions
            if (isConstraintDefinition(trimmed)) {
                tableLevelConstraints.add(trimmed)
                return@forEach
            }
            
            try {
                val column = parseColumnDefinition(trimmed)
                if (column != null) {
                    columns.add(column)
                }
            } catch (e: Exception) {
                println("Warning: Failed to parse column definition '$trimmed': ${e.message}")
            }
        }
        
        // Parse composite primary key from table-level constraints
        val compositePrimaryKey = extractCompositePrimaryKey(tableLevelConstraints)
        
        // Apply table-level constraints to columns (but not composite keys)
        val updatedColumns = applyTableLevelConstraints(columns, tableLevelConstraints, compositePrimaryKey)
        
        return TableParsingResult(updatedColumns, compositePrimaryKey)
    }
    
    /**
     * Extract composite primary key columns from table-level constraints
     */
    private fun extractCompositePrimaryKey(constraints: List<String>): List<String> {
        constraints.forEach { constraint ->
            val upperConstraint = constraint.uppercase()
            
            // Handle both "PRIMARY KEY (...)" and "CONSTRAINT name PRIMARY KEY (...)" syntax
            if (upperConstraint.startsWith("PRIMARY KEY") || upperConstraint.contains("PRIMARY KEY")) {
                val primaryKeyRegex = Regex("""PRIMARY\s+KEY\s*\(([^)]+)\)""", RegexOption.IGNORE_CASE)
                val match = primaryKeyRegex.find(constraint)
                if (match != null) {
                    val columnList = match.groupValues[1]
                        .split(",")
                        .map { it.trim() }
                    
                    // Return composite key if more than one column
                    if (columnList.size > 1) {
                        return columnList
                    }
                }
            }
        }
        return emptyList()
    }
    
    /**
     * Apply table-level constraints (like PRIMARY KEY (col1, col2)) to the appropriate columns
     */
    private fun applyTableLevelConstraints(
        columns: List<SQLColumnInfo>, 
        constraints: List<String>,
        compositePrimaryKey: List<String>
    ): List<SQLColumnInfo> {
        val updatedColumns = columns.toMutableList()
        
        constraints.forEach { constraint ->
            val upperConstraint = constraint.uppercase()
            
            when {
                upperConstraint.startsWith("PRIMARY KEY") || upperConstraint.contains("PRIMARY KEY") -> {
                    // Parse PRIMARY KEY (col1, col2, ...)
                    val primaryKeyRegex = Regex("""PRIMARY\s+KEY\s*\(([^)]+)\)""", RegexOption.IGNORE_CASE)
                    val match = primaryKeyRegex.find(constraint)
                    if (match != null) {
                        val columnList = match.groupValues[1]
                            .split(",")
                            .map { it.trim() }
                        
                        // Only mark as individual primary keys if not a composite key
                        if (compositePrimaryKey.isEmpty()) {
                            // Mark these columns as primary keys
                            for (i in updatedColumns.indices) {
                                val column = updatedColumns[i]
                                if (columnList.contains(column.columnName)) {
                                    updatedColumns[i] = column.copy(
                                        isPrimaryKey = true,
                                        isNullable = false, // Primary keys are always NOT NULL
                                        constraints = column.constraints + SQLConstraint.PrimaryKey(false)
                                    )
                                }
                            }
                        } else {
                            // For composite keys, just ensure the columns are NOT NULL
                            for (i in updatedColumns.indices) {
                                val column = updatedColumns[i]
                                if (columnList.contains(column.columnName)) {
                                    updatedColumns[i] = column.copy(
                                        isNullable = false, // Primary key components are always NOT NULL
                                        constraints = column.constraints + SQLConstraint.NotNull()
                                    )
                                }
                            }
                        }
                    }
                }
                // Add more table-level constraint types here as needed
                upperConstraint.startsWith("UNIQUE") -> {
                    // Parse UNIQUE (col1, col2, ...)
                    val uniqueRegex = Regex("""UNIQUE\s*\(([^)]+)\)""", RegexOption.IGNORE_CASE)
                    val match = uniqueRegex.find(constraint)
                    if (match != null) {
                        val columnList = match.groupValues[1]
                            .split(",")
                            .map { it.trim() }
                        
                        // Mark these columns as unique
                        for (i in updatedColumns.indices) {
                            val column = updatedColumns[i]
                            if (columnList.contains(column.columnName)) {
                                updatedColumns[i] = column.copy(
                                    constraints = column.constraints + SQLConstraint.Unique()
                                )
                            }
                        }
                    }
                }
                upperConstraint.contains("FOREIGN KEY") -> {
                    // Parse FOREIGN KEY (col) REFERENCES table(col)
                    val foreignKeyRegex = Regex(
                        """FOREIGN\s+KEY\s*\(([^)]+)\)\s*REFERENCES\s+(\w+)\s*\((\w+)\)""", 
                        RegexOption.IGNORE_CASE
                    )
                    val match = foreignKeyRegex.find(constraint)
                    if (match != null) {
                        val columnName = match.groupValues[1].trim()
                        val referencedTable = match.groupValues[2]
                        val referencedColumn = match.groupValues[3]
                        
                        // Add foreign key constraint to the appropriate column
                        for (i in updatedColumns.indices) {
                            val column = updatedColumns[i]
                            if (column.columnName == columnName) {
                                updatedColumns[i] = column.copy(
                                    constraints = column.constraints + SQLConstraint.ForeignKey(referencedTable, referencedColumn)
                                )
                            }
                        }
                    }
                }
            }
        }
        
        return updatedColumns
    }
    
    /**
     * Split column definitions by commas, handling nested parentheses
     */
    private fun splitColumnDefinitions(content: String): List<String> {
        val definitions = mutableListOf<String>()
        var current = StringBuilder()
        var parenthesesDepth = 0
        
        for (char in content) {
            when (char) {
                '(' -> {
                    parenthesesDepth++
                    current.append(char)
                }
                ')' -> {
                    parenthesesDepth--
                    current.append(char)
                }
                ',' -> {
                    if (parenthesesDepth == 0) {
                        val definition = current.toString().trim()
                        if (definition.isNotEmpty()) {
                            definitions.add(definition)
                        }
                        current = StringBuilder()
                    } else {
                        current.append(char)
                    }
                }
                else -> current.append(char)
            }
        }
        
        if (current.isNotEmpty()) {
            val definition = current.toString().trim()
            if (definition.isNotEmpty()) {
                definitions.add(definition)
            }
        }
        
        return definitions
    }
    
    /**
     * Check if a definition is a constraint rather than a column
     */
    private fun isConstraintDefinition(definition: String): Boolean {
        val upperDef = definition.uppercase()
        return upperDef.startsWith("CONSTRAINT") ||
                upperDef.startsWith("FOREIGN KEY") ||
                upperDef.startsWith("PRIMARY KEY") ||
                upperDef.startsWith("UNIQUE") ||
                upperDef.startsWith("CHECK") ||
                upperDef.startsWith("INDEX")
    }
    
    /**
     * Parse a single column definition
     */
    private fun parseColumnDefinition(definition: String): SQLColumnInfo? {
        // Improved regex to match: column_name TYPE_WITH_PARAMS [constraints...]
        // This handles types like DECIMAL(10,2), VARCHAR(50), TIMESTAMP, etc.
        val columnRegex = Regex(
            """(\w+)\s+(\w+(?:\([^)]+\))?)\s*(.*)""",
            RegexOption.IGNORE_CASE
        )
        
        val match = columnRegex.find(definition.trim()) ?: return null
        
        val columnName = match.groupValues[1]
        val typeWithSize = match.groupValues[2]
        val constraintsText = match.groupValues[3]
        
        // Parse type and size
        val (sqlType, maxLength) = parseTypeAndSize(typeWithSize)
        
        // Parse constraints
        val constraints = parseConstraints(constraintsText)
        val isNullable = !constraints.any { it is SQLConstraint.NotNull } && 
                        !constraintsText.contains("NOT NULL", ignoreCase = true)
        val isPrimaryKey = constraints.any { it is SQLConstraint.PrimaryKey } ||
                          constraintsText.contains("PRIMARY KEY", ignoreCase = true)
        
        // Extract default value
        val defaultValue = extractDefaultValue(constraintsText)
        
        return SQLColumnInfo(
            columnName = columnName,
            sqlType = sqlType,
            isNullable = isNullable,
            isPrimaryKey = isPrimaryKey,
            defaultValue = defaultValue,
            maxLength = maxLength,
            constraints = constraints
        )
    }
    
    /**
     * Parse SQL type and extract size if present
     */
    private fun parseTypeAndSize(typeWithSize: String): Pair<String, Int?> {
        // Handle types like DECIMAL(10,2), VARCHAR(50), VECTOR(1536), etc.
        val sizeRegex = Regex("""(\w+)\(([^)]+)\)""")
        val match = sizeRegex.find(typeWithSize)
        
        return if (match != null) {
            val type = match.groupValues[1]
            val sizeParams = match.groupValues[2]
            
            // Special handling for VECTOR types
            if (type.uppercase() == "VECTOR") {
                val dimension = sizeParams.trim().toIntOrNull()
                if (dimension != null && dimension > 0) {
                    // Return the full type with dimension
                    typeWithSize to dimension
                } else {
                    println("Warning: Invalid vector dimension '$sizeParams', must be a positive integer")
                    typeWithSize to null
                }
            } else {
                // For types like DECIMAL(10,2), take the first number as max length
                // For types like VARCHAR(50), take the number directly
                val size = sizeParams.split(",")[0].trim().toIntOrNull()
                
                // Preserve the full type with size for proper schema comparison
                typeWithSize to size
            }
        } else {
            // Handle VECTOR without explicit dimension
            if (typeWithSize.uppercase() == "VECTOR") {
                typeWithSize to null // Vector without dimension
            } else {
                typeWithSize to null
            }
        }
    }
    
    /**
     * Parse constraints from constraint text
     */
    private fun parseConstraints(constraintsText: String): List<SQLConstraint> {
        val constraints = mutableListOf<SQLConstraint>()
        val upperText = constraintsText.uppercase()
        
        if (upperText.contains("NOT NULL")) {
            constraints.add(SQLConstraint.NotNull())
        }
        
        if (upperText.contains("PRIMARY KEY")) {
            val autoIncrement = upperText.contains("AUTO_INCREMENT") || 
                               upperText.contains("AUTOINCREMENT")
            constraints.add(SQLConstraint.PrimaryKey(autoIncrement))
        }
        
        if (upperText.contains("UNIQUE")) {
            constraints.add(SQLConstraint.Unique())
        }
        
        // Parse FOREIGN KEY constraints
        val foreignKeyRegex = Regex("""FOREIGN\s+KEY\s*\([^)]+\)\s*REFERENCES\s+(\w+)\s*\((\w+)\)""", RegexOption.IGNORE_CASE)
        val foreignKeyMatch = foreignKeyRegex.find(constraintsText)
        if (foreignKeyMatch != null) {
            val referencedTable = foreignKeyMatch.groupValues[1]
            val referencedColumn = foreignKeyMatch.groupValues[2]
            constraints.add(SQLConstraint.ForeignKey(referencedTable, referencedColumn))
        }
        
        return constraints
    }
    
    /**
     * Extract default value from constraints text
     */
    private fun extractDefaultValue(constraintsText: String): String? {
        val defaultRegex = Regex("""DEFAULT\s+([^,\s]+)""", RegexOption.IGNORE_CASE)
        val match = defaultRegex.find(constraintsText)
        return match?.groupValues?.get(1)?.trim('\'', '"')
    }
    
    /**
     * Extract content between balanced parentheses starting from the given index
     */
    private fun extractBalancedParentheses(content: String, startIndex: Int): String? {
        var depth = 1
        var currentIndex = startIndex
        
        while (currentIndex < content.length && depth > 0) {
            when (content[currentIndex]) {
                '(' -> depth++
                ')' -> depth--
            }
            currentIndex++
        }
        
        return if (depth == 0) {
            content.substring(startIndex, currentIndex - 1).trim()
        } else {
            null // Unbalanced parentheses
        }
    }
} 