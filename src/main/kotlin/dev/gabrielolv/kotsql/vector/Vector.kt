package dev.gabrielolv.kotsql.vector

import kotlinx.serialization.Serializable
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Type-safe wrapper for vector data with utility methods for common vector operations.
 * Represents a vector of floating-point values with dimension checking and calculations.
 */
@JvmInline
@Serializable(with = VectorSerializer::class)
value class Vector(val values: FloatArray) {
    
    /**
     * Get the dimension (number of components) of this vector
     */
    val dimension: Int get() = values.size
    
    /**
     * Compute dot product with another vector
     * @throws IllegalArgumentException if dimensions don't match
     */
    fun dotProduct(other: Vector): Float {
        require(dimension == other.dimension) {
            "Vector dimensions must match: $dimension != ${other.dimension}"
        }
        return values.zip(other.values).sumOf { (a, b) -> (a * b).toDouble() }.toFloat()
    }
    
    /**
     * Calculate the magnitude (Euclidean norm) of this vector
     */
    fun magnitude(): Float {
        return sqrt(values.sumOf { (it * it).toDouble() }.toFloat())
    }
    
    /**
     * Normalize this vector to unit length
     * @return normalized vector, or zero vector if magnitude is zero
     */
    fun normalize(): Vector {
        val mag = magnitude()
        return if (mag == 0f) {
            Vector(FloatArray(dimension) { 0f })
        } else {
            Vector(values.map { it / mag }.toFloatArray())
        }
    }
    
    /**
     * Calculate Euclidean distance (L2 distance) to another vector
     * @throws IllegalArgumentException if dimensions don't match
     */
    fun euclideanDistance(other: Vector): Float {
        require(dimension == other.dimension) {
            "Vector dimensions must match: $dimension != ${other.dimension}"
        }
        return sqrt(values.zip(other.values).sumOf { (a, b) -> 
            (a - b).toDouble().pow(2) 
        }.toFloat())
    }
    
    /**
     * Calculate cosine distance to another vector (1 - cosine similarity)
     * @throws IllegalArgumentException if dimensions don't match
     */
    fun cosineDistance(other: Vector): Float {
        return 1f - cosineSimilarity(other)
    }
    
    /**
     * Calculate cosine similarity with another vector
     * @throws IllegalArgumentException if dimensions don't match
     * @return value between -1 and 1, where 1 means identical direction
     */
    fun cosineSimilarity(other: Vector): Float {
        require(dimension == other.dimension) {
            "Vector dimensions must match: $dimension != ${other.dimension}"
        }
        
        val dotProd = dotProduct(other)
        val magnitudes = magnitude() * other.magnitude()
        
        return if (magnitudes == 0f) 0f else dotProd / magnitudes
    }
    
    /**
     * Calculate negative inner product (for pgvector <#> operator)
     */
    fun negativeInnerProduct(other: Vector): Float {
        return -dotProduct(other)
    }
    
    /**
     * Validate that this vector contains only valid float values
     * @throws IllegalStateException if vector contains NaN or infinity
     */
    fun validate() {
        values.forEachIndexed { index, value ->
            when {
                value.isNaN() -> throw IllegalStateException("Vector contains NaN at index $index")
                value.isInfinite() -> throw IllegalStateException("Vector contains infinity at index $index")
            }
        }
    }
    
    /**
     * Check if this vector is normalized (unit vector)
     * @param tolerance allowed tolerance for magnitude comparison
     */
    fun isNormalized(tolerance: Float = 1e-6f): Boolean {
        val mag = magnitude()
        return kotlin.math.abs(mag - 1f) <= tolerance
    }
    
    companion object {
        /**
         * Create a vector from vararg float values
         */
        fun of(vararg values: Float): Vector {
            return Vector(values)
        }
        
        /**
         * Create a vector from a list of float values
         */
        fun of(values: List<Float>): Vector {
            return Vector(values.toFloatArray())
        }
        
        /**
         * Create a zero vector of specified dimension
         */
        fun zeros(dimension: Int): Vector {
            require(dimension > 0) { "Vector dimension must be positive" }
            return Vector(FloatArray(dimension) { 0f })
        }
        
        /**
         * Create a random vector of specified dimension
         * @param dimension vector dimension
         * @param range random value range (default: -1.0 to 1.0)
         */
        fun random(dimension: Int, range: ClosedFloatingPointRange<Float> = -1f..1f): Vector {
            require(dimension > 0) { "Vector dimension must be positive" }
            val rangeSize = range.endInclusive - range.start
            return Vector(FloatArray(dimension) { 
                range.start + kotlin.random.Random.nextFloat() * rangeSize 
            })
        }
        
        /**
         * Create a unit vector of specified dimension
         * @param dimension vector dimension
         * @param normalize whether to normalize the random vector
         */
        fun randomUnit(dimension: Int): Vector {
            return random(dimension).normalize()
        }
    }
} 