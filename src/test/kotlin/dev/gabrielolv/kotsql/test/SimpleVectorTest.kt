package dev.gabrielolv.kotsql.test

import dev.gabrielolv.kotsql.vector.Vector
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class SimpleVectorTest {
    
    @Test
    fun testVectorCreation() {
        val vector = Vector.of(1.0f, 2.0f, 3.0f)
        assertEquals(3, vector.dimension)
        assertArrayEquals(floatArrayOf(1.0f, 2.0f, 3.0f), vector.values, 0.001f)
    }
    
    @Test
    fun testVectorOperations() {
        val vec1 = Vector.of(1.0f, 0.0f, 0.0f)
        val vec2 = Vector.of(0.0f, 1.0f, 0.0f)
        
        assertEquals(0.0f, vec1.dotProduct(vec2), 0.001f)
        assertEquals(1.0f, vec1.magnitude(), 0.001f)
        assertEquals(1.414f, vec1.euclideanDistance(vec2), 0.01f)
    }
} 