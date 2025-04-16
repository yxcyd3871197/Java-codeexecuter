package com.example.jsonfixer;

// Simple DTO used internally by JsonFixController to attempt parsing
// an escaped {"data": "..."} structure sent incorrectly as text/plain.
// Not intended for direct API consumption.
public class JsonInputDto {
    private String data;

    // Standard getters and setters are needed for Jackson deserialization
    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}
