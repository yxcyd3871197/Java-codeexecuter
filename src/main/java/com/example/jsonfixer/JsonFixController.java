package com.example.jsonfixer;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType; // Keep for error response Content-Type
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
// Removed @Value, Logger, LoggerFactory imports

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@RestController
public class JsonFixController {

    // Keep ObjectMapper for parsing validation and error object creation
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Removed @Value field for expectedApiKey
    // Removed Logger

    @PostMapping("/fix-json")
    public ResponseEntity<String> fixJson(
            @RequestBody String potentiallyMalformedJson,
            @RequestHeader(value = "X-API-KEY", required = false) String apiKey) { // Header optional

        // Read expected key directly from environment variable
        String expectedKey = System.getenv("JSONFIXER_API_KEY");

        // Check API Key (handle nulls)
        // Ensure expectedKey is not blank as well
        if (expectedKey == null || expectedKey.isBlank() || apiKey == null || !expectedKey.equals(apiKey)) {
            // Return simple JSON error string directly
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.APPLICATION_JSON) // Explicitly set Content-Type for error
                    .body("{\"error\": \"Invalid or missing API key\"}");
        }

        // Proceed with fixing if API key is valid
        String fixed = null; // Declare here for scope in catch block
        try {
            fixed = repairJson(potentiallyMalformedJson); // Call repair function

            // Validate the repaired string by attempting to parse it
            objectMapper.readTree(fixed);

            // Return the repaired string (default Content-Type will be text/plain)
            return ResponseEntity.ok(fixed);

        } catch (Exception e) {
            // Handle exceptions during repair or validation parsing
            ObjectNode error = objectMapper.createObjectNode();
            error.put("error", "Failed to parse JSON after attempting repairs.");
            error.put("original_input", potentiallyMalformedJson);
            // Include attempted fix if it was assigned (i.e., repairJson didn't throw exception itself)
            if (fixed != null) {
                 error.put("attempted_fix", fixed);
            }
            error.put("details", e.getMessage());

            // Return error object as JSON string
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON) // Explicitly set Content-Type for error
                    .body(error.toString()); // Use toString() to send the JSON string
            }
        }
    }

    private String repairJson(String input) {
        if (input == null || input.isBlank()) {
            return "{}"; // Return empty JSON object for empty input
        }

        String stringToRepair = input; // Start with the original input

        // --- Attempt to detect and extract from escaped {"data": "..."} structure ---
        // This is a workaround for clients incorrectly sending the JSON structure as text/plain
        try {
            // Basic check for the pattern
            if (stringToRepair.trim().startsWith("{\"") && stringToRepair.trim().endsWith("\"}")) {
                 log.debug("Input looks like it might be an escaped JSON structure: '{}'", stringToRepair);
                 // Use ObjectMapper to parse the *outer* structure
                 ObjectMapper tempMapper = new ObjectMapper();
                 JsonInputDto outerDto = tempMapper.readValue(stringToRepair, JsonInputDto.class);
                 if (outerDto != null && outerDto.getData() != null) {
                     log.info("Successfully extracted inner string from escaped structure.");
                     stringToRepair = outerDto.getData(); // Use the inner string for repairs
                 } else {
                      log.debug("Parsed outer structure but 'data' field was missing or null.");
                 }
            }
        } catch (Exception e) {
            // If parsing the outer structure fails, assume it wasn't the escaped {"data":...} format
            // and proceed with the original input string.
            log.debug("Failed to parse input as outer escaped structure, proceeding with original input. Error: {}", e.getMessage());
        }
        // --- End of extraction attempt ---


        String repaired = stringToRepair; // Use the potentially extracted string

        // --- Attempt basic un-escaping (useful if extraction didn't happen or inner string is still escaped) ---
        // This handles cases where the input might have been accidentally escaped
        // (e.g., sending a JSON string literal as the body)
        log.debug("Before un-escaping: '{}'", repaired);
        repaired = repaired.replace("\\\\\"", "\""); // \\" -> "
        repaired = repaired.replace("\\\\n", "\n");   // \\n -> \n
        repaired = repaired.replace("\\\\r", "\r");   // \\r -> \r
        repaired = repaired.replace("\\\\t", "\t");   // \\t -> \t
        // Add more if needed, e.g., \\/ -> /
        log.debug("After un-escaping: '{}'", repaired);

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
