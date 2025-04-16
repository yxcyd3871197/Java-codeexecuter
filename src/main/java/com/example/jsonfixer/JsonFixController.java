package com.example.jsonfixer;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@RestController
public class JsonFixController {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/fix-json")
    public ResponseEntity<String> fixJson(
            @RequestBody String input,
            @RequestHeader(value = "X-API-KEY", required = false) String apiKey) {

        String expectedKey = System.getenv("JSONFIXER_API_KEY");
        if (expectedKey == null || apiKey == null || !expectedKey.equals(apiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("{\"error\": \"Invalid or missing API key\"}");
        }

        try {
            // ✅ Versuch: ist der Input bereits gültig?
            objectMapper.readTree(input);
            return ResponseEntity.ok(input); // Nichts zu tun

        } catch (Exception e1) {
            // ❌ reparieren, falls invalide
            try {
                String fixed = repairJson(input);
                objectMapper.readTree(fixed); // nochmal prüfen
                return ResponseEntity.ok(fixed);
            } catch (Exception e2) {
                ObjectNode error = objectMapper.createObjectNode();
                error.put("error", "Failed to parse JSON after attempting repairs.");
                error.put("original_input", input);
                error.put("attempted_fix", repairJson(input));
                error.put("details", e2.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error.toString());
            }
        }
    }

    private String repairJson(String input) {
        if (input == null || input.isBlank()) return "{}";

        return input
                .replace("“", "\"")
                .replace("”", "\"")
                .replace("‘", "'")
                .replace("’", "'")
                .replaceAll("(?<!\\\\)\\n", "\\\\n")
                .replaceAll("(?<!\\\\)\\r", "\\\\r")
                .trim();
    }
}
