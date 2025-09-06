package com.thesmartway.steplite.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
public class ExecutionListRequest {
    private List<String> statuses;
    private String workflowName;
    private OffsetDateTime startDate;
    private OffsetDateTime endDate;
    private Integer limit = 50;
    private Integer offset = 0;
    private String sortBy = "createdAt";
    private String sortOrder = "DESC"; // ASC or DESC
}
