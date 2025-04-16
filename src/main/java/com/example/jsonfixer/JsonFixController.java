package com.example.jsonfixer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType; // Re-add MediaType import
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger; // Import Logger
import org.slf4j.LoggerFactory; // Import LoggerFactory

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@RestController
public class JsonFixController {

    private static final Logger log = LoggerFactory.getLogger(JsonFixController.class); // Logger instance

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Inject the expected API key from application properties
    @Value("${jsonfixer.api.key}")
    private String expectedApiKey;

    @PostMapping("/fix-json") // Revert endpoint definition
    public ResponseEntity<String> fixJson(
            @RequestHeader(value = "X-API-KEY", required = true) String providedApiKey, // Header is now required
            @RequestBody String potentiallyMalformedJson) { // Revert to String RequestBody

        // Log the received and expected keys for debugging (avoid logging sensitive keys in production long-term)
        log.info("Received X-API-KEY: '{}'", providedApiKey); // Log received key
        log.info("Expected API Key configured: '{}'", expectedApiKey != null && !expectedApiKey.isBlank() ? "Present" : "MISSING/BLANK"); // Log if expected key is present

        log.info("Expected API Key configured: '{}'", expectedApiKey != null && !expectedApiKey.isBlank() ? "Present" : "MISSING/BLANK");

        log.info("Expected API Key configured: '{}'", expectedApiKey != null && !expectedApiKey.isBlank() ? "Present" : "MISSING/BLANK");

        // Check API Key
        if (expectedApiKey == null || expectedApiKey.isBlank() || !expectedApiKey.equals(providedApiKey)) {
             log.warn("API Key validation failed. Provided: '{}', Expected: '{}'", providedApiKey, expectedApiKey); // Log failure details
             ObjectNode errorJson = objectMapper.createObjectNode();
             errorJson.put("error", "Unauthorized");
             errorJson.put("details", "Invalid or missing X-API-KEY header."); // Slightly updated details message
             try {
                 // Return error as JSON for consistency, even though input is text/plain
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body(objectMapper.writeValueAsString(errorJson));
             } catch (JsonProcessingException ex) {
                 log.error("Failed to create JSON error response", ex);
                 return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_JSON).body("{\"error\": \"Failed to create error response\"}");
             }
        }

        // Proceed with fixing if API key is valid
        String fixedJson = null; // Declare fixedJson outside the try block
        try {
            log.info("Attempting to fix raw input string: '{}'", potentiallyMalformedJson); // Log the raw input

            fixedJson = repairJson(potentiallyMalformedJson); // Assign inside try
            log.info("Repaired JSON string: '{}'", fixedJson); // Log the result of repairJson

            // Try to parse the fixed string to ensure it's valid JSON now
            objectMapper.readTree(fixedJson);
            // Return the fixed string as plain text
            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(fixedJson);
        } catch (JsonProcessingException e) {
            // If parsing still fails after repair attempts
            log.warn("Parsing failed even after repair attempts for input: '{}'. Error: {}", potentiallyMalformedJson, e.getMessage());
            ObjectNode errorJson = objectMapper.createObjectNode();
            errorJson.put("error", "Failed to parse JSON after attempting repairs.");
            errorJson.put("original_input", potentiallyMalformedJson);
            errorJson.put("attempted_fix", fixedJson);
            errorJson.put("details", e.getMessage());
            try {
                // Return error as JSON
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body(objectMapper.writeValueAsString(errorJson));
            } catch (JsonProcessingException ex) {
                 log.error("Failed to create JSON error response", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_JSON).body("{\"error\": \"Failed to create error response\"}");
            }
        } catch (Exception e) {
            // Catch other unexpected errors during repair
            log.error("Unexpected error during JSON repair for input: '{}'", potentiallyMalformedJson, e);
             ObjectNode errorJson = objectMapper.createObjectNode();
            errorJson.put("error", "An unexpected error occurred during JSON repair.");
            errorJson.put("original_input", potentiallyMalformedJson);
            errorJson.put("details", e.getMessage());
             try {
                 // Return error as JSON
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_JSON).body(objectMapper.writeValueAsString(errorJson));
            } catch (JsonProcessingException ex) {
                 log.error("Failed to create JSON error response", ex);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_JSON).body("{\"error\": \"Failed to create error response\"}");
            }
        }
    }

    private String repairJson(String input) {
        if (input == null || input.isBlank()) {
            return "{}"; // Return empty JSON object for empty input
        }

        String repaired = input;

        // 1. Replace typographic quotes with standard double quotes
        repaired = repaired.replace('“', '"').replace('”', '"');
        repaired = repaired.replace('‘', '\'').replace('’', '\''); // Replace typographic single quotes if needed, though JSON uses double

        // 2. Attempt to fix unescaped double quotes within strings.
        // This is complex. A simple approach: escape quotes that are likely *inside* strings.
        // This regex looks for quotes preceded by a non-escape character (\) and not part of typical JSON structure (like ," or ": or "} or {")
        // It's not perfect but covers many common cases from AI outputs.
        // We look for a character that is NOT a backslash, followed by a quote, followed by a character that is NOT a comma, colon, brace or bracket.
        // This is still heuristic and might fail on complex nested structures or specific edge cases.
        // A more robust solution might involve a state machine or a more sophisticated parser.
         repaired = repaired.replaceAll("(?<!\\\\)\"(?=[^\\s:,\\}\\]])", "\\\\\""); // Positive lookahead for non-structural chars
         // Also handle quotes at the very end of a string value before a comma/brace
         repaired = repaired.replaceAll("(?<=[^\\s:,\\{\\[])\"(?=\\s*[,\\}\\]])", "\\\\\""); // Positive lookbehind for non-structural chars


        // 3. Replace literal newlines within strings with escaped newlines \\n
        // This regex finds \n that are not already escaped (i.e., not preceded by \)
        // Again, this assumes newlines *within* JSON string values need escaping.
        // It might incorrectly escape newlines that are part of the raw input but outside intended JSON strings.
        repaired = repaired.replaceAll("(?<!\\\\)\\n", "\\\\n");
        // Also replace carriage returns if present
        repaired = repaired.replaceAll("(?<!\\\\)\\r", "\\\\r");


        // 4. Trim leading/trailing whitespace that might interfere with parsing
        repaired = repaired.trim();

        // Basic check: Ensure it starts with { or [ and ends with } or ]
        // Sometimes AI output includes introductory text or trailing explanations.
        int firstBrace = repaired.indexOf('{');
        int firstBracket = repaired.indexOf('[');
        int start = -1;

        if (firstBrace == -1 && firstBracket == -1) {
             // No JSON structure found, return error indication or original string?
             // Let's try returning the repaired string and let the parser catch it.
             return repaired; // Or throw new IllegalArgumentException("Input does not appear to contain JSON structure.");
        } else if (firstBrace == -1) {
            start = firstBracket;
        } else if (firstBracket == -1) {
            start = firstBrace;
        } else {
            start = Math.min(firstBrace, firstBracket);
        }

        int lastBrace = repaired.lastIndexOf('}');
        int lastBracket = repaired.lastIndexOf(']');
        int end = Math.max(lastBrace, lastBracket);

        if (start > 0 || (end != -1 && end < repaired.length() - 1)) {
             if (end == -1 || start > end) {
                 // Malformed structure, e.g., starts with { but no } found after it.
                 // Return repaired string and let parser fail.
                 return repaired; // Or throw new IllegalArgumentException("Mismatched JSON delimiters.");
             }
             // Extract the potential JSON part
             repaired = repaired.substring(start, end + 1);
        }


        // Add more repair steps here if needed (e.g., removing trailing commas - complex)

        return repaired;
    }
}
