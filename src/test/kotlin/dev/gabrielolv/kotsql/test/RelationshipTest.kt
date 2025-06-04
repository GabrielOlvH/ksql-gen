package dev.gabrielolv.kotsql.test

import dev.gabrielolv.kotsql.generator.KotlinGenerator
import dev.gabrielolv.kotsql.model.RelationshipInfo
import dev.gabrielolv.kotsql.parser.SQLParser
import dev.gabrielolv.kotsql.relationship.RelationshipDetector
import dev.gabrielolv.kotsql.query.JoinQuery
import dev.gabrielolv.kotsql.query.Column
import dev.gabrielolv.kotsql.query.ColumnSelection
import dev.gabrielolv.kotsql.query.like
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RelationshipTest {
    
    @Test
    fun testRelationshipDetection() {
        val sqlContent = """
            CREATE TABLE users (
                id INTEGER PRIMARY KEY,
                username VARCHAR(50) NOT NULL,
                email VARCHAR(100) NOT NULL
            );
            
            CREATE TABLE posts (
                id BIGINT PRIMARY KEY,
                user_id INTEGER NOT NULL,
                title VARCHAR(200) NOT NULL,
                content TEXT NOT NULL,
                FOREIGN KEY (user_id) REFERENCES users(id)
            );
            
            CREATE TABLE comments (
                id INTEGER PRIMARY KEY,
                post_id BIGINT NOT NULL,
                user_id INTEGER NOT NULL,
                content TEXT NOT NULL,
                FOREIGN KEY (post_id) REFERENCES posts(id),
                FOREIGN KEY (user_id) REFERENCES users(id)
            );
            
            CREATE TABLE post_categories (
                post_id BIGINT NOT NULL,
                category_id INTEGER NOT NULL,
                PRIMARY KEY (post_id, category_id),
                FOREIGN KEY (post_id) REFERENCES posts(id),
                FOREIGN KEY (category_id) REFERENCES categories(id)
            );
            
            CREATE TABLE categories (
                id INTEGER PRIMARY KEY,
                name VARCHAR(100) NOT NULL
            );
        """.trimIndent()
        
        val sqlParser = SQLParser()
        val tables = sqlParser.parseSQLContent(sqlContent)
        
        assertTrue(tables.size >= 5, "Should parse at least 5 tables")
        
        // Detect relationships
        val relationships = RelationshipDetector.detectRelationships(tables)
        val withReverse = RelationshipDetector.generateReverseRelationships(relationships)
        
        println("Detected relationships:")
        withReverse.relationships.forEach { rel ->
            val cardinality = RelationshipDetector.getCardinalityDescription(rel)
            println("  ${rel.fromTable}.${rel.fromColumn} -> ${rel.toTable}.${rel.toColumn} ($cardinality)")
        }
        
        // Verify relationships exist
        assertTrue(withReverse.relationships.isNotEmpty(), "Should detect relationships")
        
        // Check for specific relationships
        val postToUser = withReverse.findRelationship("posts", "users")
        assertNotNull(postToUser, "Should find posts -> users relationship")
        assertTrue(postToUser is RelationshipInfo.ManyToOne, "Posts to users should be Many-to-One")
        
        val userToPosts = withReverse.findRelationship("users", "posts")
        assertNotNull(userToPosts, "Should find users -> posts relationship")
        assertTrue(userToPosts is RelationshipInfo.OneToMany, "Users to posts should be One-to-Many")
        
        // Check for many-to-many relationships
        val manyToManyRelationships = withReverse.relationships.filterIsInstance<RelationshipInfo.ManyToMany>()
        assertTrue(manyToManyRelationships.isNotEmpty(), "Should detect many-to-many relationships")
        
        val postToCategories = manyToManyRelationships.find { 
            it.fromTable == "posts" && it.toTable == "categories" 
        }
        assertNotNull(postToCategories, "Should find posts -> categories many-to-many relationship")
        assertEquals("post_categories", postToCategories.junctionTable)
    }
    
    @Test
    fun testJunctionTableDetection() {
        val sqlContent = """
            CREATE TABLE users (
                id INTEGER PRIMARY KEY,
                name VARCHAR(50)
            );
            
            CREATE TABLE roles (
                id INTEGER PRIMARY KEY,
                name VARCHAR(50)
            );
            
            CREATE TABLE user_roles (
                user_id INTEGER NOT NULL,
                role_id INTEGER NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (user_id, role_id),
                FOREIGN KEY (user_id) REFERENCES users(id),
                FOREIGN KEY (role_id) REFERENCES roles(id)
            );
        """.trimIndent()
        
        val sqlParser = SQLParser()
        val tables = sqlParser.parseSQLContent(sqlContent)
        
        val relationships = RelationshipDetector.detectRelationships(tables)
        
        // Should detect many-to-many relationship through junction table
        val manyToManyRelationships = relationships.relationships.filterIsInstance<RelationshipInfo.ManyToMany>()
        assertTrue(manyToManyRelationships.isNotEmpty(), "Should detect many-to-many relationships")
        
        val userToRoles = manyToManyRelationships.find { 
            it.fromTable == "users" && it.toTable == "roles" 
        }
        assertNotNull(userToRoles, "Should find users -> roles relationship")
        assertEquals("user_roles", userToRoles.junctionTable)
        assertEquals("user_id", userToRoles.junctionFromColumn)
        assertEquals("role_id", userToRoles.junctionToColumn)
        
        println("Junction table relationship: ${userToRoles.fromTable} -> ${userToRoles.toTable} via ${userToRoles.junctionTable}")
    }
    
    @Test
    fun testJoinQueryGeneration() {
        // Test join query builder
        val userIdColumn = Column<Int>("id")
        val postUserIdColumn = Column<Int>("user_id")
        val postTitleColumn = Column<String>("title")
        
        val relationship = RelationshipInfo.ManyToOne(
            fromTable = "posts",
            toTable = "users",
            fromColumn = "user_id",
            toColumn = "id",
            isOptional = false
        )
        
        val joinQuery = JoinQuery.from<Any>("posts")
            .innerJoin<Any>(relationship, "u")
            .where(postTitleColumn like "%kotlin%")
            .orderBy("u", userIdColumn)
            .limit(10)
        
        val result = joinQuery.build()
        
        println("Generated JOIN SQL:")
        println(result.sql)
        
        assertTrue(result.sql.contains("INNER JOIN users AS u"), "Should generate INNER JOIN")
        assertTrue(result.sql.contains("ON posts.user_id = users.id"), "Should generate correct ON condition")
        assertTrue(result.sql.contains("WHERE title LIKE ?"), "Should include WHERE condition")
        assertTrue(result.sql.contains("ORDER BY u.id"), "Should include ORDER BY with alias")
        assertTrue(result.sql.contains("LIMIT 10"), "Should include LIMIT")
    }
    
    @Test
    fun testManyToManyJoinQuery() {
        val postIdColumn = Column<Int>("id")
        val categoryNameColumn = Column<String>("name")
        
        val manyToManyRelationship = RelationshipInfo.ManyToMany(
            fromTable = "posts",
            toTable = "categories", 
            fromColumn = "id",
            toColumn = "id",
            junctionTable = "post_categories",
            junctionFromColumn = "post_id",
            junctionToColumn = "category_id"
        )
        
        val joinQuery = JoinQuery.from<Any>("posts")
            .manyToManyJoin<Any>(manyToManyRelationship, "c")
            .where(categoryNameColumn eq "Technology")
            .orderBy(postIdColumn)
        
        val result = joinQuery.build()
        
        println("Generated Many-to-Many JOIN SQL:")
        println(result.sql)
        
        assertTrue(result.sql.contains("INNER JOIN post_categories"), "Should join junction table")
        assertTrue(result.sql.contains("INNER JOIN categories AS c"), "Should join target table")
        assertTrue(result.sql.contains("posts.id = post_categories.post_id"), "Should link to junction")
        assertTrue(result.sql.contains("post_categories.category_id = categories.id"), "Should link from junction")
    }
    
    @Test
    fun testSelectSpecificColumnsFromJoin() {
        val userNameColumn = Column<String>("username")
        val postTitleColumn = Column<String>("title")
        
        val relationship = RelationshipInfo.ManyToOne(
            fromTable = "posts",
            toTable = "users",
            fromColumn = "user_id", 
            toColumn = "id",
            isOptional = false
        )
        
        val joinQuery = JoinQuery.from<Any>("posts")
            .leftJoin<Any>(relationship, "u")
        
        val result = joinQuery.select(
            ColumnSelection("posts", postTitleColumn),
            ColumnSelection("u", userNameColumn)
        )
        
        println("Generated SELECT specific columns SQL:")
        println(result.sql)
        
        assertTrue(result.sql.contains("SELECT posts.title, u.username"), "Should select specific columns")
        assertTrue(result.sql.contains("LEFT JOIN users AS u"), "Should use LEFT JOIN")
    }
    
    @Test
    fun testRelationshipCodeGeneration() {
        val sqlContent = """
            CREATE TABLE users (
                id INTEGER PRIMARY KEY,
                username VARCHAR(50) NOT NULL,
                email VARCHAR(100) NOT NULL
            );
            
            CREATE TABLE posts (
                id BIGINT PRIMARY KEY,
                user_id INTEGER NOT NULL,
                title VARCHAR(200) NOT NULL,
                FOREIGN KEY (user_id) REFERENCES users(id)
            );
        """.trimIndent()
        
        val sqlParser = SQLParser()
        val generator = KotlinGenerator()
        
        val tables = sqlParser.parseSQLContent(sqlContent)
        val relationships = RelationshipDetector.detectRelationships(tables)
        val withReverse = RelationshipDetector.generateReverseRelationships(relationships)
        
        // Generate code with relationships
        val generatedFiles = generator.generateAllDataClasses(
            tables = tables,
            packageName = "test.package",
            includeValidation = false,
            relationships = withReverse
        )
        
        assertTrue(generatedFiles.isNotEmpty(), "Should generate files")
        
        val postsFile = generatedFiles["Posts.kt"]
        assertNotNull(postsFile, "Should generate Posts.kt")
        
        println("Generated Posts.kt with relationships:")
        println(postsFile)
        
        // Verify relationship metadata is included
        assertTrue(postsFile.contains("object Relationships"), "Should include Relationships object")
        assertTrue(postsFile.contains("RelationshipInfo.ManyToOne"), "Should include ManyToOne relationship")
        assertTrue(postsFile.contains("joinQuery()"), "Should include join query helper")
        assertTrue(postsFile.contains("JoinQuery"), "Should import JoinQuery")
        
        val usersFile = generatedFiles["Users.kt"]
        assertNotNull(usersFile, "Should generate Users.kt")
        
        println("\nGenerated Users.kt with relationships:")
        println(usersFile)
        
        // Users should have One-to-Many relationship to posts
        assertTrue(usersFile.contains("RelationshipInfo.OneToMany"), "Should include OneToMany relationship")
    }
    
    @Test
    fun testRelationshipCardinalityDescriptions() {
        val oneToOne = RelationshipInfo.OneToOne("users", "profiles", "id", "user_id")
        val oneToMany = RelationshipInfo.OneToMany("users", "posts", "id", "user_id")
        val manyToOne = RelationshipInfo.ManyToOne("posts", "users", "user_id", "id")
        val manyToMany = RelationshipInfo.ManyToMany("posts", "categories", "id", "id", "post_categories", "post_id", "category_id")
        
        assertEquals("1:1", RelationshipDetector.getCardinalityDescription(oneToOne))
        assertEquals("1:N", RelationshipDetector.getCardinalityDescription(oneToMany))
        assertEquals("N:1", RelationshipDetector.getCardinalityDescription(manyToOne))
        assertEquals("N:N", RelationshipDetector.getCardinalityDescription(manyToMany))
    }
} 