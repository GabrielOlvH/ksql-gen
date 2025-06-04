package dev.gabrielolv.kotsql.generator

import dev.gabrielolv.kotsql.model.RelationshipInfo
import dev.gabrielolv.kotsql.model.SQLTableInfo
import dev.gabrielolv.kotsql.model.SchemaRelationships
import dev.gabrielolv.kotsql.util.NamingConventions

/**
 * Handles generation of relationship and schema metadata classes
 */
object RelationshipGenerator {
    
    /**
     * Generate relationships object
     */
    fun generateRelationshipsObject(relationships: SchemaRelationships): String {
        return buildString {
            appendLine("/**")
            appendLine(" * All table relationships in the schema")
            appendLine(" */")
            appendLine("object TableRelationships {")
            appendLine("    val all = SchemaRelationships(listOf(")
            
            relationships.relationships.forEachIndexed { index, relationship ->
                val relationshipType = when (relationship) {
                    is RelationshipInfo.OneToOne -> "OneToOne"
                    is RelationshipInfo.OneToMany -> "OneToMany"
                    is RelationshipInfo.ManyToOne -> "ManyToOne"
                    is RelationshipInfo.ManyToMany -> "ManyToMany"
                }
                
                appendLine("        RelationshipInfo.$relationshipType(")
                appendLine("            fromTable = \"${relationship.fromTable}\",")
                appendLine("            fromColumn = \"${relationship.fromColumn}\",")
                appendLine("            toTable = \"${relationship.toTable}\",")
                appendLine("            toColumn = \"${relationship.toColumn}\"")
                
                // Add type-specific parameters
                when (relationship) {
                    is RelationshipInfo.OneToOne -> {
                        appendLine(",")
                        appendLine("            isOptional = ${relationship.isOptional}")
                    }
                    is RelationshipInfo.ManyToOne -> {
                        appendLine(",")
                        appendLine("            isOptional = ${relationship.isOptional}")
                    }
                    is RelationshipInfo.ManyToMany -> {
                        appendLine(",")
                        appendLine("            junctionTable = \"${relationship.junctionTable}\",")
                        appendLine("            junctionFromColumn = \"${relationship.junctionFromColumn}\",")
                        appendLine("            junctionToColumn = \"${relationship.junctionToColumn}\"")
                    }
                    is RelationshipInfo.OneToMany -> {
                        // OneToMany doesn't have additional parameters
                    }
                }
                
                append("        )")
                if (index < relationships.relationships.size - 1) {
                    append(",")
                }
                appendLine()
            }
            
            appendLine("    ))")
            appendLine("}")
        }
    }
    
    /**
     * Generate schema metadata object
     */
    fun generateSchemaMetadataObject(
        tables: List<SQLTableInfo>,
        relationships: SchemaRelationships?
    ): String {
        return buildString {
            appendLine("/**")
            appendLine(" * Generated schema metadata")
            appendLine(" * Contains information about the current database schema")
            appendLine(" */")
            appendLine("object SchemaMetadata {")
            appendLine("    const val TABLE_COUNT = ${tables.size}")
            appendLine("    const val TOTAL_COLUMNS = ${tables.sumOf { it.columns.size }}")
            appendLine("    const val RELATIONSHIP_COUNT = ${relationships?.relationships?.size ?: 0}")
            appendLine()
            appendLine("    val TABLE_NAMES = setOf(")
            tables.forEach { table ->
                appendLine("        \"${table.tableName}\",")
            }
            appendLine("    )")
            appendLine()
            appendLine("    val TABLE_CLASSES = mapOf(")
            tables.forEach { table ->
                val className = NamingConventions.tableNameToClassName(table.tableName)
                appendLine("        \"${table.tableName}\" to $className::class,")
            }
            appendLine("    )")
            appendLine("}")
        }
    }
} 