package com.thesmartway.steplite.task;

import java.util.Map;

public interface TaskHandler {
    TaskResult execute(Map<String, Object> input);
}
