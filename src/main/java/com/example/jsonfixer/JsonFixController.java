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
            @RequestBody String potentiallyMalformedJson,
            @RequestHeader(value = "X-API-KEY", required = false) String apiKey) {

        String expectedKey = System.getenv("JSONFIXER_API_KEY");

        if (expectedKey == null || apiKey == null || !expectedKey.equals(apiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\": \"Invalid or missing API key\"}");
        }

        try {
            String fixed = repairJson(potentiallyMalformedJson);

            objectMapper.readTree(fixed);

            return ResponseEntity.ok(fixed);

        } catch (Exception e) {
            ObjectNode error = objectMapper.createObjectNode();
            error.put("error", "Failed to parse JSON after attempting repairs.");
            error.put("original_input", potentiallyMalformedJson);
            if (fixed != null) {
                error.put("attempted_fix", fixed);
            }
            error.put("details", e.getMessage());

            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(error.toString());
        }
    }

    private static class JsonInputDto {
        private String data;
        public String getData() { return data; }
        public void setData(String data) { this.data = data; }
    }

    private String repairJson(String input) {
        if (input == null || input.isBlank()) {
            return "{}";
        }

        String stringToRepair = input;

        try {
            if (stringToRepair.trim().startsWith("{\"") && stringToRepair.trim().endsWith("\"}")) {
                ObjectMapper tempMapper = new ObjectMapper();
                JsonInputDto outerDto = tempMapper.readValue(stringToRepair, JsonInputDto.class);
                if (outerDto != null && outerDto.getData() != null) {
                    stringToRepair = outerDto.getData();
                }
            }
        } catch (Exception e) {
        }

        String repaired = stringToRepair;

        repaired = repaired.replace("\\\\\"", "\"");
        repaired = repaired.replace("\\\\n", "\n");
        repaired = repaired.replace("\\\\r", "\r");
        repaired = repaired.replace("\\\\t", "\t");

        repaired = repaired.replace('“', '"').replace('”', '"');
        repaired = repaired.replace('‘', '\'').replace('’', '\'');

        repaired = repaired.replaceAll("(?<!\\\\)\"(?=[^\\s:,\\}\\]])", "\\\\\"");
        repaired = repaired.replaceAll("(?<=[^\\s:,\\{\\[])\"(?=\\s*[,\\}\\]])", "\\\\\"");

        repaired = repaired.replaceAll("(?<!\\\\)\\n", "\\\\n");
        repaired = repaired.replaceAll("(?<!\\\\)\\r", "\\\\r");

        repaired = repaired.trim();

        int firstBrace = repaired.indexOf('{');
        int firstBracket = repaired.indexOf('[');
        int start = -1;

        if (firstBrace == -1 && firstBracket == -1) {
            return repaired;
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
                return repaired;
            }
            repaired = repaired.substring(start, end + 1);
        }

        return repaired;
    }
}
