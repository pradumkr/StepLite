package com.thesmartway.steplite.dto;

import java.time.OffsetDateTime;
import java.util.Map;

import lombok.Builder;
import lombok.Data;


@Data
@Builder
public class ErrorResponse {
    private OffsetDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
    private Map<String, String> details;
}