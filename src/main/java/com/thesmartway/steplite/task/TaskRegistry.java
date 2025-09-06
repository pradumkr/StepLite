package com.thesmartway.steplite.task;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TaskRegistry {
    
    private final Map<String, TaskHandler> handlers = new ConcurrentHashMap<>();
    
    @Autowired
    public TaskRegistry(ApplicationContext applicationContext) {
        // Auto-register all TaskHandler beans
        Map<String, TaskHandler> handlerBeans = applicationContext.getBeansOfType(TaskHandler.class);
        handlerBeans.forEach((name, handler) -> {
            // Extract the task type from the bean name or use the class name
            String taskType = name.replace("Handler", "").toLowerCase();
            registerHandler(taskType, handler);
        });
        
        // Register mock handlers for testing
        registerHandler("mock", new MockTaskHandler());
        registerHandler("orderService.validate", new MockTaskHandler());
        registerHandler("orderService.process", new MockTaskHandler());
        registerHandler("orderService.complete", new MockTaskHandler());
    }
    
    public void registerHandler(String taskType, TaskHandler handler) {
        handlers.put(taskType, handler);
    }
    
    public TaskHandler getHandler(String taskType) {
        TaskHandler handler = handlers.get(taskType);
        if (handler == null) {
            throw new IllegalArgumentException("No handler found for task type: " + taskType);
        }
        return handler;
    }
    
    public boolean hasHandler(String taskType) {
        return handlers.containsKey(taskType);
    }
}
