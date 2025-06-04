package dev.gabrielolv.kotsql.validation

import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

/**
 * Validation engine that validates data class instances using reflection
 * and the validation annotations applied to properties.
 */
object ValidationEngine {
    
    /**
     * Validate a data class instance and return a list of validation errors
     */
    fun <T : Any> validate(instance: T): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        val clazz = instance::class
        
        for (property in clazz.memberProperties) {
            @Suppress("UNCHECKED_CAST")
            val prop = property as KProperty1<T, Any?>
            val value = prop.get(instance)
            val fieldName = property.name
            
            // Validate each annotation on the property
            validateProperty(fieldName, value, property, errors)
        }
        
        return ValidationResult(errors)
    }
    
    /**
     * Validate a single property with all its annotations
     */
    private fun validateProperty(
        fieldName: String,
        value: Any?,
        property: KProperty1<*, *>,
        errors: MutableList<ValidationError>
    ) {
        // NotNull validation
        property.findAnnotation<NotNull>()?.let { annotation ->
            if (value == null) {
                errors.add(ValidationError(fieldName, value, annotation.message))
            }
        }
        
        // NotBlank validation (for strings)
        property.findAnnotation<NotBlank>()?.let { annotation ->
            when {
                value == null -> errors.add(ValidationError(fieldName, value, annotation.message))
                value is String && value.isBlank() -> errors.add(ValidationError(fieldName, value, annotation.message))
            }
        }
        
        // Length validation (for strings)
        property.findAnnotation<Length>()?.let { annotation ->
            if (value is String) {
                val length = value.length
                if (length < annotation.min || length > annotation.max) {
                    val message = annotation.message
                        .replace("{min}", annotation.min.toString())
                        .replace("{max}", annotation.max.toString())
                    errors.add(ValidationError(fieldName, value, message))
                }
            }
        }
        
        // Range validation (for numeric types)
        property.findAnnotation<Range>()?.let { annotation ->
            val numericValue = when (value) {
                is Byte -> value.toLong()
                is Short -> value.toLong()
                is Int -> value.toLong()
                is Long -> value
                else -> null
            }
            
            if (numericValue != null && (numericValue < annotation.min || numericValue > annotation.max)) {
                val message = annotation.message
                    .replace("{min}", annotation.min.toString())
                    .replace("{max}", annotation.max.toString())
                errors.add(ValidationError(fieldName, value, message))
            }
        }
        
        // DecimalRange validation (for decimal types)
        property.findAnnotation<DecimalRange>()?.let { annotation ->
            val decimalValue = when (value) {
                is Float -> value.toDouble()
                is Double -> value
                else -> null
            }
            
            if (decimalValue != null && (decimalValue < annotation.min || decimalValue > annotation.max)) {
                val message = annotation.message
                    .replace("{min}", annotation.min.toString())
                    .replace("{max}", annotation.max.toString())
                errors.add(ValidationError(fieldName, value, message))
            }
        }
        
        // Pattern validation (for strings)
        property.findAnnotation<Pattern>()?.let { annotation ->
            if (value is String && !value.matches(Regex(annotation.regex))) {
                errors.add(ValidationError(fieldName, value, annotation.message))
            }
        }
        
        // Email validation (for strings)
        property.findAnnotation<Email>()?.let { annotation ->
            if (value is String && !isValidEmail(value)) {
                errors.add(ValidationError(fieldName, value, annotation.message))
            }
        }
    }
    
    /**
     * Simple email validation using regex
     */
    private fun isValidEmail(email: String): Boolean {
        val emailRegex = Regex(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
        )
        return email.matches(emailRegex)
    }
    
    /**
     * Validate and throw exception if validation fails
     */
    fun <T : Any> validateAndThrow(instance: T) {
        val result = validate(instance)
        if (!result.isValid) {
            throw ValidationException(result.errors)
        }
    }
}

/**
 * Result of validation containing any errors found
 */
data class ValidationResult(
    val errors: List<ValidationError>
) {
    val isValid: Boolean get() = errors.isEmpty()
    
    /**
     * Get errors for a specific field
     */
    fun getErrorsForField(fieldName: String): List<ValidationError> {
        return errors.filter { it.fieldName == fieldName }
    }
    
    /**
     * Get all error messages
     */
    fun getAllMessages(): List<String> {
        return errors.map { it.message }
    }
    
    /**
     * Get formatted error summary
     */
    fun getSummary(): String {
        return if (isValid) {
            "Validation passed"
        } else {
            "Validation failed with ${errors.size} error(s):\n" +
                    errors.joinToString("\n") { "- ${it.fieldName}: ${it.message}" }
        }
    }
}

/**
 * Represents a single validation error
 */
data class ValidationError(
    val fieldName: String,
    val value: Any?,
    val message: String
)

/**
 * Exception thrown when validation fails
 */
class ValidationException(
    val errors: List<ValidationError>
) : Exception("Validation failed: ${errors.joinToString(", ") { "${it.fieldName}: ${it.message}" }}") 