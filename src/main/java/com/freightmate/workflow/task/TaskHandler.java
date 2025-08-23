package com.freightmate.workflow.task;

import java.util.Map;

public interface TaskHandler {
    TaskResult execute(Map<String, Object> input);
}
