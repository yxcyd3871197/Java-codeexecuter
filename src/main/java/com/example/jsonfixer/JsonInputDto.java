package com.example.jsonfixer;

// Simple DTO to map the incoming JSON request body {"data": "..."}
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
