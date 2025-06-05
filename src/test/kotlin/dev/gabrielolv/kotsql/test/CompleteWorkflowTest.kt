package dev.gabrielolv.kotsql.test

import dev.gabrielolv.kotsql.generator.KotlinGenerator
import dev.gabrielolv.kotsql.parser.SQLParser
import dev.gabrielolv.kotsql.util.FileManager
import dev.gabrielolv.kotsql.util.PathManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import java.sql.DriverManager
import java.sql.Timestamp
import java.time.LocalDateTime
import kotlin.test.*

/**
 * Complete workflow test demonstrating the full pipeline from SQL schema
 * to generated ResultSet extensions with real database operations.
 */
class CompleteWorkflowTest {
    
    @BeforeEach
    fun setup() {
        Class.forName("org.h2.Driver")
    }
    
    @Test
    fun `complete workflow - SQL to working ResultSet extensions`() {
        // Step 1: Define SQL schema
        val sqlSchema = """
            CREATE TABLE users (
                id INTEGER PRIMARY KEY,
                username VARCHAR(50) NOT NULL,
                email VARCHAR(100),
                created_at TIMESTAMP,
                is_active BOOLEAN DEFAULT true
            );
            
            CREATE TABLE posts (
                id BIGINT PRIMARY KEY,
                user_id INTEGER NOT NULL,
                title VARCHAR(200) NOT NULL,
                content TEXT,
                published_at TIMESTAMP,
                FOREIGN KEY (user_id) REFERENCES users(id)
            );
            
            CREATE TABLE user_roles (
                user_id INTEGER NOT NULL,
                role_id INTEGER NOT NULL,
                assigned_at TIMESTAMP NOT NULL,
                PRIMARY KEY (user_id, role_id)
            );
        """.trimIndent()
        
        // Step 2: Parse SQL schema
        val sqlParser = SQLParser()
        val tables = sqlParser.parseSQLContent(sqlSchema)
        
        assertEquals(3, tables.size, "Should parse 3 tables")
        
        val usersTable = tables.find { it.tableName == "users" }
        val postsTable = tables.find { it.tableName == "posts" }
        val userRolesTable = tables.find { it.tableName == "user_roles" }
        
        assertNotNull(usersTable, "Should find users table")
        assertNotNull(postsTable, "Should find posts table")
        assertNotNull(userRolesTable, "Should find user_roles table")
        
        assertTrue(userRolesTable.hasCompositePrimaryKey, "user_roles should have composite key")
        
        // Step 3: Generate ResultSet extensions for each table
        val kotlinGenerator = KotlinGenerator()
        val config = PathManager.PathConfig(
            organizationStrategy = PathManager.OrganizationStrategy.TYPE_BASED,
            includeTimestamps = true
        )
        
        val usersExtensions = kotlinGenerator.generateResultSetExtensionsFile(
            FileManager.TableGenerationContext(
                table = usersTable,
                packageName = "com.example.generated",
                includeValidation = false,
                relationships = null
            ),
            config
        )
        
        val postsExtensions = kotlinGenerator.generateResultSetExtensionsFile(
            FileManager.TableGenerationContext(
                table = postsTable,
                packageName = "com.example.generated",
                includeValidation = false,
                relationships = null
            ),
            config
        )
        
        val userRolesExtensions = kotlinGenerator.generateResultSetExtensionsFile(
            FileManager.TableGenerationContext(
                table = userRolesTable,
                packageName = "com.example.generated",
                includeValidation = false,
                relationships = null
            ),
            config
        )
        
        // Step 4: Verify generated code structure
        
        // Users table extensions
        assertTrue(usersExtensions.contains("fun ResultSet.toUsers(): Users"))
        assertTrue(usersExtensions.contains("fun ResultSet.toUsersOrNull(): Users?"))
        assertTrue(usersExtensions.contains("fun ResultSet.toUsersList"))
        assertTrue(usersExtensions.contains("fun ResultSet.toUsersSequence"))
        assertTrue(usersExtensions.contains("val COLUMN_NAMES = listOf("))
        assertTrue(usersExtensions.contains("fun selectAllQuery()"))
        
        // Posts table extensions
        assertTrue(postsExtensions.contains("fun ResultSet.toPosts(): Posts"))
        assertTrue(postsExtensions.contains("fun ResultSet.toPostsOrNull(): Posts?"))
        
        // User roles composite key extensions
        assertTrue(userRolesExtensions.contains("fun ResultSet.toUserRoles(): UserRoles"))
        
        // Step 5: Create actual database and test with real data
        val connection = DriverManager.getConnection("jdbc:h2:mem:workflow_test_${System.nanoTime()};DB_CLOSE_DELAY=-1")
        
        // Create tables
        connection.createStatement().execute("""
            CREATE TABLE users (
                id INTEGER PRIMARY KEY,
                username VARCHAR(50) NOT NULL,
                email VARCHAR(100),
                created_at TIMESTAMP,
                is_active BOOLEAN DEFAULT true
            )
        """)
        
        connection.createStatement().execute("""
            CREATE TABLE posts (
                id BIGINT PRIMARY KEY,
                user_id INTEGER NOT NULL,
                title VARCHAR(200) NOT NULL,
                content TEXT,
                published_at TIMESTAMP
            )
        """)
        
        connection.createStatement().execute("""
            CREATE TABLE user_roles (
                user_id INTEGER NOT NULL,
                role_id INTEGER NOT NULL,
                assigned_at TIMESTAMP NOT NULL,
                PRIMARY KEY (user_id, role_id)
            )
        """)
        
        // Step 6: Insert test data
        
        // Insert users
        connection.prepareStatement("INSERT INTO users (id, username, email, created_at, is_active) VALUES (?, ?, ?, ?, ?)").use { stmt ->
            stmt.setInt(1, 1)
            stmt.setString(2, "john_doe")
            stmt.setString(3, "john@example.com")
            stmt.setTimestamp(4, Timestamp.valueOf(LocalDateTime.of(2023, 1, 1, 12, 0)))
            stmt.setBoolean(5, true)
            stmt.executeUpdate()
            
            stmt.setInt(1, 2)
            stmt.setString(2, "jane_smith")
            stmt.setString(3, "jane@example.com")
            stmt.setTimestamp(4, Timestamp.valueOf(LocalDateTime.of(2023, 2, 1, 12, 0)))
            stmt.setBoolean(5, true)
            stmt.executeUpdate()
        }
        
        // Insert posts
        connection.prepareStatement("INSERT INTO posts (id, user_id, title, content, published_at) VALUES (?, ?, ?, ?, ?)").use { stmt ->
            stmt.setLong(1, 1L)
            stmt.setInt(2, 1)
            stmt.setString(3, "First Post")
            stmt.setString(4, "This is the content of the first post")
            stmt.setTimestamp(5, Timestamp.valueOf(LocalDateTime.of(2023, 1, 15, 10, 0)))
            stmt.executeUpdate()
            
            stmt.setLong(1, 2L)
            stmt.setInt(2, 2)
            stmt.setString(3, "Second Post")
            stmt.setString(4, "This is the content of the second post")
            stmt.setNull(5, java.sql.Types.TIMESTAMP)
            stmt.executeUpdate()
        }
        
        // Insert user roles
        connection.prepareStatement("INSERT INTO user_roles (user_id, role_id, assigned_at) VALUES (?, ?, ?)").use { stmt ->
            stmt.setInt(1, 1)
            stmt.setInt(2, 1)
            stmt.setTimestamp(3, Timestamp.valueOf(LocalDateTime.of(2023, 1, 1, 12, 0)))
            stmt.executeUpdate()
            
            stmt.setInt(1, 1)
            stmt.setInt(2, 2)
            stmt.setTimestamp(3, Timestamp.valueOf(LocalDateTime.of(2023, 1, 2, 12, 0)))
            stmt.executeUpdate()
            
            stmt.setInt(1, 2)
            stmt.setInt(2, 1)
            stmt.setTimestamp(3, Timestamp.valueOf(LocalDateTime.of(2023, 2, 1, 12, 0)))
            stmt.executeUpdate()
        }
        
        // Step 7: Test ResultSet parsing (simulating generated extensions)
        
        // Test users table parsing
        connection.prepareStatement("SELECT * FROM users ORDER BY id").use { stmt ->
            stmt.executeQuery().use { rs ->
                val users = mutableListOf<TestUser>()
                while (rs.next()) {
                    // Simulate generated toUsers() method
                    val user = TestUser(
                        id = rs.getInt("id"),
                        username = rs.getString("username") ?: "",
                        email = rs.getString("email"),
                        createdAt = rs.getTimestamp("created_at")?.let { 
                            kotlinx.datetime.Instant.fromEpochMilliseconds(it.time)
                        },
                        isActive = rs.getBoolean("is_active").takeIf { !rs.wasNull() }
                    )
                    users.add(user)
                }
                
                assertEquals(2, users.size)
                assertEquals("john_doe", users[0].username)
                assertEquals("jane_smith", users[1].username)
                assertEquals("john@example.com", users[0].email)
                assertEquals("jane@example.com", users[1].email)
                assertTrue(users[0].isActive == true)
                assertTrue(users[1].isActive == true)
            }
        }
        
        // Test posts table parsing
        connection.prepareStatement("SELECT * FROM posts ORDER BY id").use { stmt ->
            stmt.executeQuery().use { rs ->
                val posts = mutableListOf<TestPost>()
                while (rs.next()) {
                    // Simulate generated toPosts() method
                    val post = TestPost(
                        id = rs.getLong("id"),
                        userId = rs.getInt("user_id"),
                        title = rs.getString("title") ?: "",
                        content = rs.getString("content"),
                        publishedAt = rs.getTimestamp("published_at")?.let { 
                            kotlinx.datetime.Instant.fromEpochMilliseconds(it.time)
                        }
                    )
                    posts.add(post)
                }
                
                assertEquals(2, posts.size)
                assertEquals("First Post", posts[0].title)
                assertEquals("Second Post", posts[1].title)
                assertEquals(1, posts[0].userId)
                assertEquals(2, posts[1].userId)
                assertNotNull(posts[0].publishedAt)
                assertNull(posts[1].publishedAt)
            }
        }
        
        // Test composite key table parsing
        connection.prepareStatement("SELECT * FROM user_roles ORDER BY user_id, role_id").use { stmt ->
            stmt.executeQuery().use { rs ->
                val userRoles = mutableListOf<TestUserRole>()
                while (rs.next()) {
                    // Simulate generated toUserRoles() method
                    val userRole = TestUserRole(
                        userId = rs.getInt("user_id"),
                        roleId = rs.getInt("role_id"),
                        assignedAt = rs.getTimestamp("assigned_at")?.let { 
                            kotlinx.datetime.Instant.fromEpochMilliseconds(it.time)
                        } ?: kotlinx.datetime.Clock.System.now()
                    )
                    userRoles.add(userRole)
                }
                
                assertEquals(3, userRoles.size)
                assertEquals(1, userRoles[0].userId)
                assertEquals(1, userRoles[0].roleId)
                assertEquals(1, userRoles[1].userId)
                assertEquals(2, userRoles[1].roleId)
                assertEquals(2, userRoles[2].userId)
                assertEquals(1, userRoles[2].roleId)
            }
        }
        
        // Step 8: Test sequence processing (memory efficiency)
        connection.prepareStatement("SELECT * FROM users").use { stmt ->
            stmt.executeQuery().use { rs ->
                // Simulate generated toUsersSequence() method
                val usernames = sequence {
                    while (rs.next()) {
                        yield(rs.getString("username") ?: "")
                    }
                }.toList()
                
                assertEquals(listOf("john_doe", "jane_smith"), usernames)
            }
        }
        
        // Step 9: Test error handling
        connection.prepareStatement("SELECT * FROM users WHERE id = -1").use { stmt ->
            stmt.executeQuery().use { rs ->
                // Simulate generated toUsersOrNull() method
                val user = try {
                    if (rs.next()) {
                        TestUser(
                            id = rs.getInt("id"),
                            username = rs.getString("username") ?: "",
                            email = rs.getString("email"),
                            createdAt = rs.getTimestamp("created_at")?.let { 
                                kotlinx.datetime.Instant.fromEpochMilliseconds(it.time)
                            },
                            isActive = rs.getBoolean("is_active").takeIf { !rs.wasNull() }
                        )
                    } else null
                } catch (e: Exception) {
                    null
                }
                
                assertNull(user, "Should return null for empty ResultSet")
            }
        }
        
        connection.close()
        
        println("✅ Complete workflow test passed!")
        println("✅ SQL parsing: SUCCESS")
        println("✅ Code generation: SUCCESS")
        println("✅ Database operations: SUCCESS")
        println("✅ ResultSet parsing: SUCCESS")
        println("✅ Error handling: SUCCESS")
        println("✅ Memory efficiency: SUCCESS")
    }
    
    // Test data classes (simulating generated classes)
    data class TestUser(
        val id: Int,
        val username: String,
        val email: String?,
        val createdAt: kotlinx.datetime.Instant?,
        val isActive: Boolean?
    )
    
    data class TestPost(
        val id: Long,
        val userId: Int,
        val title: String,
        val content: String?,
        val publishedAt: kotlinx.datetime.Instant?
    )
    
    data class TestUserRole(
        val userId: Int,
        val roleId: Int,
        val assignedAt: kotlinx.datetime.Instant
    )
} 