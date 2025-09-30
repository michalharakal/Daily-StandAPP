package de.jug_da.standapp.mcp.protocol

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Validator for JSONRPC 2.0 compliance in MCP protocol messages
 */
object MCPValidator {
    
    /**
     * Validate a raw JSON request for JSONRPC 2.0 compliance
     * @param json The raw JSON to validate
     * @return ValidationResult indicating success or failure with details
     */
    fun validateRequest(json: JsonElement): ValidationResult {
        if (json !is JsonObject) {
            return ValidationResult.invalid("Request must be a JSON object")
        }
        
        // Check required jsonrpc field
        val jsonrpc = json["jsonrpc"]?.jsonPrimitive?.content
        if (jsonrpc == null) {
            return ValidationResult.invalid("Missing required field 'jsonrpc'")
        }
        if (jsonrpc != "2.0") {
            return ValidationResult.invalid("Invalid jsonrpc version: '$jsonrpc'. Expected '2.0'")
        }
        
        // Check required method field
        val method = json["method"]?.jsonPrimitive?.content
        if (method == null) {
            return ValidationResult.invalid("Missing required field 'method'")
        }
        if (method.isEmpty()) {
            return ValidationResult.invalid("Method cannot be empty")
        }
        
        // Validate method name format (no internal methods starting with 'rpc.')
        if (method.startsWith("rpc.") && method != "rpc.discover") {
            return ValidationResult.invalid("Method names beginning with 'rpc.' are reserved")
        }
        
        // Check id field (can be string, number, or null, but if present must be valid)
        val id = json["id"]
        if (id != null) {
            val idPrimitive = id.jsonPrimitive
            if (idPrimitive.content.isEmpty() && !idPrimitive.isString) {
                return ValidationResult.invalid("Invalid id format")
            }
        }
        
        // Check params field (must be structured if present)
        val params = json["params"]
        if (params != null) {
            if (params !is JsonObject && params !is kotlinx.serialization.json.JsonArray) {
                return ValidationResult.invalid("Params must be a JSON object or array if present")
            }
        }
        
        // Check for unexpected fields
        val allowedFields = setOf("jsonrpc", "method", "params", "id")
        val unexpectedFields = json.keys - allowedFields
        if (unexpectedFields.isNotEmpty()) {
            return ValidationResult.invalid("Unexpected fields: ${unexpectedFields.joinToString(", ")}")
        }
        
        return ValidationResult.valid()
    }
    
    /**
     * Validate a raw JSON response for JSONRPC 2.0 compliance
     * @param json The raw JSON to validate
     * @return ValidationResult indicating success or failure with details
     */
    fun validateResponse(json: JsonElement): ValidationResult {
        if (json !is JsonObject) {
            return ValidationResult.invalid("Response must be a JSON object")
        }
        
        // Check required jsonrpc field
        val jsonrpc = json["jsonrpc"]?.jsonPrimitive?.content
        if (jsonrpc == null) {
            return ValidationResult.invalid("Missing required field 'jsonrpc'")
        }
        if (jsonrpc != "2.0") {
            return ValidationResult.invalid("Invalid jsonrpc version: '$jsonrpc'. Expected '2.0'")
        }
        
        // Check required id field (must be present in responses)
        if (!json.containsKey("id")) {
            return ValidationResult.invalid("Missing required field 'id' in response")
        }
        
        // Must have either result or error, but not both
        val hasResult = json.containsKey("result")
        val hasError = json.containsKey("error")
        
        if (!hasResult && !hasError) {
            return ValidationResult.invalid("Response must have either 'result' or 'error' field")
        }
        
        if (hasResult && hasError) {
            return ValidationResult.invalid("Response cannot have both 'result' and 'error' fields")
        }
        
        // Validate error structure if present
        if (hasError) {
            val error = json["error"]
            if (error !is JsonObject) {
                return ValidationResult.invalid("Error field must be a JSON object")
            }
            
            val errorValidation = validateError(error)
            if (!errorValidation.isValid) {
                return errorValidation
            }
        }
        
        // Check for unexpected fields
        val allowedFields = setOf("jsonrpc", "result", "error", "id")
        val unexpectedFields = json.keys - allowedFields
        if (unexpectedFields.isNotEmpty()) {
            return ValidationResult.invalid("Unexpected fields: ${unexpectedFields.joinToString(", ")}")
        }
        
        return ValidationResult.valid()
    }
    
    /**
     * Validate error object structure
     */
    private fun validateError(error: JsonObject): ValidationResult {
        // Check required code field
        val code = error["code"]?.jsonPrimitive?.content?.toIntOrNull()
        if (code == null) {
            return ValidationResult.invalid("Error object missing required 'code' field or invalid format")
        }
        
        // Check required message field
        val message = error["message"]?.jsonPrimitive?.content
        if (message == null) {
            return ValidationResult.invalid("Error object missing required 'message' field")
        }
        
        // Data field is optional but if present must be structured
        // (no specific validation needed as per JSONRPC 2.0 spec)
        
        // Check for unexpected fields in error object
        val allowedErrorFields = setOf("code", "message", "data")
        val unexpectedFields = error.keys - allowedErrorFields
        if (unexpectedFields.isNotEmpty()) {
            return ValidationResult.invalid("Unexpected error fields: ${unexpectedFields.joinToString(", ")}")
        }
        
        return ValidationResult.valid()
    }
    
    /**
     * Validate an MCPRequest object for consistency
     */
    fun validateMCPRequest(request: MCPRequest): ValidationResult {
        if (request.jsonrpc != "2.0") {
            return ValidationResult.invalid("Invalid jsonrpc version in MCPRequest")
        }
        
        if (request.method.isEmpty()) {
            return ValidationResult.invalid("Method cannot be empty in MCPRequest")
        }
        
        if (request.id.isEmpty()) {
            return ValidationResult.invalid("ID cannot be empty in MCPRequest")
        }
        
        return ValidationResult.valid()
    }
    
    /**
     * Validate an MCPResponse object for consistency
     */
    fun validateMCPResponse(response: MCPResponse): ValidationResult {
        if (response.jsonrpc != "2.0") {
            return ValidationResult.invalid("Invalid jsonrpc version in MCPResponse")
        }
        
        val hasResult = response.result != null
        val hasError = response.error != null
        
        if (!hasResult && !hasError) {
            return ValidationResult.invalid("MCPResponse must have either result or error")
        }
        
        if (hasResult && hasError) {
            return ValidationResult.invalid("MCPResponse cannot have both result and error")
        }
        
        return ValidationResult.valid()
    }
}

/**
 * Result of validation operation
 */
sealed class ValidationResult {
    abstract val isValid: Boolean
    
    data object Valid : ValidationResult() {
        override val isValid = true
    }
    
    data class Invalid(val reason: String) : ValidationResult() {
        override val isValid = false
    }
    
    companion object {
        fun valid(): ValidationResult = Valid
        fun invalid(reason: String): ValidationResult = Invalid(reason)
    }
}