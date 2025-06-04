package dev.gabrielolv.kotsql.vector

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Custom serializer for Vector type that handles JSON array format
 * Serializes vectors as JSON arrays: [0.1, 0.2, 0.3, ...]
 */
object VectorSerializer : KSerializer<Vector> {
    
    private val listSerializer = ListSerializer(Float.serializer())
    
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Vector") {
        element("values", listSerializer.descriptor)
    }
    
    override fun serialize(encoder: Encoder, value: Vector) {
        try {
            // Validate vector before serialization
            value.validate()
            
            // Serialize as list of floats
            encoder.encodeSerializableValue(listSerializer, value.values.toList())
        } catch (e: Exception) {
            throw SerializationException("Failed to serialize vector: ${e.message}", e)
        }
    }
    
    override fun deserialize(decoder: Decoder): Vector {
        try {
            // Deserialize as list of floats
            val floatList = decoder.decodeSerializableValue(listSerializer)
            
            // Validate non-empty
            if (floatList.isEmpty()) {
                throw SerializationException("Vector cannot be empty")
            }
            
            val vector = Vector(floatList.toFloatArray())
            
            // Validate deserialized vector
            vector.validate()
            
            return vector
        } catch (e: SerializationException) {
            throw e
        } catch (e: Exception) {
            throw SerializationException("Failed to deserialize vector: ${e.message}", e)
        }
    }
} 