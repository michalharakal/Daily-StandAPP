package de.jug_da.standapp.mcp.tools

import de.jug_da.standapp.mcp.methods.ToolParameter
import kotlinx.serialization.json.*

/**
 * Utility class for validating tool parameters against JSON schemas.
 * 
 * This class provides comprehensive parameter validation including
 * type checking, required field validation, enum validation,
 * and custom validation rules.
 */
object ParameterValidator {
    
    /**
     * Validate arguments against a parameter schema.
     * 
     * @param arguments The JSON arguments to validate
     * @param parameters Map of parameter definitions
     * @param requiredParams List of required parameter names
     * @return ValidationResult indicating success or failure with details
     */
    fun validateArguments(
        arguments: JsonObject?,
        parameters: Map<String, ToolParameter>,
        requiredParams: List<String>
    ): ValidationResult {
        val errors = mutableListOf<String>()
        
        // Handle null arguments
        if (arguments == null) {
            if (requiredParams.isNotEmpty()) {
                errors.add("Missing required arguments: ${requiredParams.joinToString(", ")}")
            }
            return if (errors.isEmpty()) ValidationResult.success() else ValidationResult.failure(errors)
        }
        
        // Check required parameters
        val missingRequired = requiredParams.filter { !arguments.containsKey(it) }
        if (missingRequired.isNotEmpty()) {
            errors.add("Missing required parameters: ${missingRequired.joinToString(", ")}")
        }
        
        // Validate each provided argument
        arguments.forEach { (paramName, paramValue) ->
            val paramDef = parameters[paramName]
            if (paramDef == null) {
                errors.add("Unknown parameter: '$paramName'")
            } else {
                val paramErrors = validateParameter(paramName, paramValue, paramDef)
                errors.addAll(paramErrors)
            }
        }
        
        return if (errors.isEmpty()) ValidationResult.success() else ValidationResult.failure(errors)
    }
    
    /**
     * Validate a single parameter value against its definition.
     * 
     * @param paramName Name of the parameter
     * @param value JSON value to validate
     * @param definition Parameter definition
     * @return List of validation errors (empty if valid)
     */
    private fun validateParameter(
        paramName: String,
        value: JsonElement,
        definition: ToolParameter
    ): List<String> {
        val errors = mutableListOf<String>()
        
        // Handle null values
        if (value is JsonNull) {
            if (definition.required) {
                errors.add("Parameter '$paramName' is required but was null")
            }
            return errors
        }
        
        // Type validation
        val typeErrors = validateParameterType(paramName, value, definition.type)
        errors.addAll(typeErrors)
        
        // Enum validation
        if (definition.enum != null && value is JsonPrimitive) {
            val stringValue = if (value.isString) value.content else value.toString()
            if (!definition.enum.contains(stringValue)) {
                errors.add("Parameter '$paramName' must be one of: ${definition.enum.joinToString(", ")}")
            }
        }
        
        // Additional type-specific validations
        when (definition.type) {
            "string" -> errors.addAll(validateStringParameter(paramName, value, definition))
            "integer" -> errors.addAll(validateIntegerParameter(paramName, value, definition))
            "number" -> errors.addAll(validateNumberParameter(paramName, value, definition))
            "boolean" -> errors.addAll(validateBooleanParameter(paramName, value, definition))
            "array" -> errors.addAll(validateArrayParameter(paramName, value, definition))
            "object" -> errors.addAll(validateObjectParameter(paramName, value, definition))
        }
        
        return errors
    }
    
    /**
     * Validate parameter type.
     */
    private fun validateParameterType(
        paramName: String,
        value: JsonElement,
        expectedType: String
    ): List<String> {
        val errors = mutableListOf<String>()
        
        when (expectedType) {
            "string" -> {
                if (value !is JsonPrimitive || !value.isString) {
                    errors.add("Parameter '$paramName' must be a string")
                }
            }
            "integer" -> {
                if (value !is JsonPrimitive || (!value.isString && value.longOrNull == null)) {
                    try {
                        value.jsonPrimitive.int
                    } catch (e: Exception) {
                        errors.add("Parameter '$paramName' must be an integer")
                    }
                }
            }
            "number" -> {
                if (value !is JsonPrimitive) {
                    errors.add("Parameter '$paramName' must be a number")
                } else {
                    try {
                        value.double
                    } catch (e: Exception) {
                        errors.add("Parameter '$paramName' must be a valid number")
                    }
                }
            }
            "boolean" -> {
                if (value !is JsonPrimitive) {
                    errors.add("Parameter '$paramName' must be a boolean")
                } else {
                    try {
                        value.boolean
                    } catch (e: Exception) {
                        errors.add("Parameter '$paramName' must be a boolean")
                    }
                }
            }
            "array" -> {
                if (value !is JsonArray) {
                    errors.add("Parameter '$paramName' must be an array")
                }
            }
            "object" -> {
                if (value !is JsonObject) {
                    errors.add("Parameter '$paramName' must be an object")
                }
            }
            else -> {
                errors.add("Unknown parameter type '$expectedType' for parameter '$paramName'")
            }
        }
        
        return errors
    }
    
    /**
     * Validate string parameter constraints.
     */
    private fun validateStringParameter(
        paramName: String,
        value: JsonElement,
        definition: ToolParameter
    ): List<String> {
        val errors = mutableListOf<String>()
        
        if (value is JsonPrimitive && value.isString) {
            val stringValue = value.content
            
            // Add string-specific validations here if needed
            // For example: minLength, maxLength, pattern, etc.
            // This can be extended based on requirements
            
            if (stringValue.isBlank() && definition.required) {
                errors.add("Parameter '$paramName' cannot be blank")
            }
        }
        
        return errors
    }
    
    /**
     * Validate integer parameter constraints.
     */
    private fun validateIntegerParameter(
        paramName: String,
        value: JsonElement,
        definition: ToolParameter
    ): List<String> {
        val errors = mutableListOf<String>()
        
        if (value is JsonPrimitive) {
            try {
                val intValue = value.int
                
                // Add integer-specific validations here if needed
                // For example: minimum, maximum, multipleOf, etc.
                // This can be extended based on requirements
                
                if (intValue < 0 && paramName.contains("days")) {
                    errors.add("Parameter '$paramName' must be non-negative")
                }
            } catch (e: Exception) {
                // Error already handled in type validation
            }
        }
        
        return errors
    }
    
    /**
     * Validate number parameter constraints.
     */
    private fun validateNumberParameter(
        paramName: String,
        value: JsonElement,
        definition: ToolParameter
    ): List<String> {
        val errors = mutableListOf<String>()
        
        if (value is JsonPrimitive) {
            try {
                val numberValue = value.double
                
                // Add number-specific validations here if needed
                // For example: minimum, maximum, exclusiveMinimum, etc.
                // This can be extended based on requirements
                
                if (!numberValue.isFinite()) {
                    errors.add("Parameter '$paramName' must be a finite number")
                }
            } catch (e: Exception) {
                // Error already handled in type validation
            }
        }
        
        return errors
    }
    
    /**
     * Validate boolean parameter constraints.
     */
    private fun validateBooleanParameter(
        paramName: String,
        value: JsonElement,
        definition: ToolParameter
    ): List<String> {
        // Boolean validation is handled in type validation
        // This method is here for completeness and future extensions
        return emptyList()
    }
    
    /**
     * Validate array parameter constraints.
     */
    private fun validateArrayParameter(
        paramName: String,
        value: JsonElement,
        definition: ToolParameter
    ): List<String> {
        val errors = mutableListOf<String>()
        
        if (value is JsonArray) {
            // Add array-specific validations here if needed
            // For example: minItems, maxItems, uniqueItems, items schema, etc.
            // This can be extended based on requirements
            
            if (value.isEmpty() && definition.required) {
                errors.add("Parameter '$paramName' array cannot be empty")
            }
        }
        
        return errors
    }
    
    /**
     * Validate object parameter constraints.
     */
    private fun validateObjectParameter(
        paramName: String,
        value: JsonElement,
        definition: ToolParameter
    ): List<String> {
        val errors = mutableListOf<String>()
        
        if (value is JsonObject) {
            // Add object-specific validations here if needed
            // For example: properties, required, additionalProperties, etc.
            // This can be extended based on requirements
            
            if (value.isEmpty() && definition.required) {
                errors.add("Parameter '$paramName' object cannot be empty")
            }
        }
        
        return errors
    }
}

/**
 * Builder class for creating parameter definitions with validation.
 */
class ParameterBuilder(
    private val name: String,
    private val type: String
) {
    private var description: String? = null
    private var required: Boolean = false
    private var default: JsonElement? = null
    private var enum: List<String>? = null
    
    /**
     * Set parameter description.
     */
    fun description(description: String) = apply {
        this.description = description
    }
    
    /**
     * Mark parameter as required.
     */
    fun required(required: Boolean = true) = apply {
        this.required = required
    }
    
    /**
     * Set default value for parameter.
     */
    fun default(value: JsonElement) = apply {
        this.default = value
    }
    
    /**
     * Set default string value.
     */
    fun default(value: String) = apply {
        this.default = JsonPrimitive(value)
    }
    
    /**
     * Set default integer value.
     */
    fun default(value: Int) = apply {
        this.default = JsonPrimitive(value)
    }
    
    /**
     * Set default boolean value.
     */
    fun default(value: Boolean) = apply {
        this.default = JsonPrimitive(value)
    }
    
    /**
     * Set enum values for parameter.
     */
    fun enum(vararg values: String) = apply {
        this.enum = values.toList()
    }
    
    /**
     * Build the parameter definition.
     */
    fun build(): ToolParameter {
        return ToolParameter(
            type = type,
            description = description,
            required = required,
            default = default,
            enum = enum
        )
    }
    
    companion object {
        fun string(name: String) = ParameterBuilder(name, "string")
        fun integer(name: String) = ParameterBuilder(name, "integer")
        fun number(name: String) = ParameterBuilder(name, "number")
        fun boolean(name: String) = ParameterBuilder(name, "boolean")
        fun array(name: String) = ParameterBuilder(name, "array")
        fun obj(name: String) = ParameterBuilder(name, "object")
    }
}