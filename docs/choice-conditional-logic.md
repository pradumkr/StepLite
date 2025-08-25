# Choice & Conditional Logic Guide

## Overview

Choice states provide conditional branching logic in workflows, allowing you to route execution based on data values and conditions. This guide covers the currently supported condition types and best practices for implementing conditional workflow logic.

**Note**: The current implementation supports basic single-condition evaluation. Advanced logical operators (AND, OR, NOT) and complex nested conditions are not yet implemented.

## Choice State Fundamentals

### Basic Structure

A Choice state evaluates conditions and routes execution to different states based on the results.

```yaml
choice_state:
  type: Choice
  choices:
    - condition:
        variable: "$.status"
        stringEquals: "approved"
      next: "approved_state"
  default: "default_state"
```

### Choice State Components

#### Required Elements

- **type**: Must be "Choice"
- **choices**: Array of choice conditions
- **default**: Default state if no conditions match

#### Choice Structure

Each choice contains:
- **condition**: Evaluation criteria (single condition only)
- **next**: State to execute if condition is true

## Currently Supported Condition Types

### String Comparisons

Compare string values using equality operator.

#### String Equality

```yaml
condition:
  variable: "$.status"
  stringEquals: "approved"
```

**Note**: String less than, greater than, and pattern matching are not currently supported.

### Numeric Comparisons

Compare numeric values using mathematical operators.

#### Numeric Equality

```yaml
condition:
  variable: "$.amount"
  numericEquals: 100
```

#### Numeric Less Than

```yaml
condition:
  variable: "$.quantity"
  numericLessThan: 10
```

#### Numeric Greater Than

```yaml
condition:
  variable: "$.score"
  numericGreaterThan: 80
```

**Note**: Numeric range conditions (combining greater than AND less than) are not currently supported.

### Boolean Comparisons

Evaluate boolean values and truthiness.

#### Boolean Equality

```yaml
condition:
  variable: "$.isValid"
  booleanEquals: true
```

#### Boolean Negation

```yaml
condition:
  variable: "$.hasInventory"
  booleanEquals: false
```

**Note**: Truthy checks and complex boolean logic are not currently supported.

## Not Currently Supported

### Logical Operators

The following logical operators are **not yet implemented**:

- ❌ **AND Operator** - Multiple conditions must all be true
- ❌ **OR Operator** - At least one condition must be true  
- ❌ **NOT Operator** - Negate a condition
- ❌ **Complex Nested Conditions** - Combining multiple logical operators

### Advanced Condition Types

The following condition types are **not yet implemented**:

- ❌ **String Pattern Matching** - Regex pattern matching
- ❌ **String Comparisons** - Less than, greater than
- ❌ **Existence and Null Checks** - isPresent, isNull, isTruthy
- ❌ **Array Operations** - Length checks, contains, element access
- ❌ **Numeric Range Checks** - Between two values

## Variable References

### JSONPath Expressions

Use JSONPath to reference data in your workflow.

#### Simple Field Reference

```yaml
variable: "$.orderId"
```

#### Nested Field Reference

```yaml
variable: "$.customer.address.city"
```

#### Array Element Reference

```yaml
variable: "$.items[0].productId"
```

**Note**: Advanced JSONPath features like array filtering and dynamic indexing are not currently supported.

### Data Context

Variables are evaluated in the context of the current workflow execution data.

#### Input Data

```yaml
# Reference workflow input
variable: "$.customerId"
variable: "$.orderAmount"
```

#### State Output

```yaml
# Reference previous state output
variable: "$.validationResult.isValid"
variable: "$.inventoryCheck.available"
```

## Current Choice Patterns

### 1. Priority-Based Routing

Route workflows based on priority levels.

```yaml
priority_routing:
  type: Choice
  choices:
    - condition:
        variable: "$.priority"
        stringEquals: "urgent"
      next: "urgent_processing"
    
    - condition:
        variable: "$.priority"
        stringEquals: "high"
      next: "high_priority_processing"
    
    - condition:
        variable: "$.priority"
        stringEquals: "normal"
      next: "normal_processing"
  
  default: "normal_processing"
```

### 2. Amount-Based Routing

Route based on monetary amounts or quantities.

```yaml
amount_routing:
  type: Choice
  choices:
    - condition:
        variable: "$.amount"
        numericGreaterThan: 10000
      next: "high_value_processing"
    
    - condition:
        variable: "$.amount"
        numericGreaterThan: 1000
      next: "medium_value_processing"
  
  default: "basic_processing"
```

### 3. Customer Type Routing

Route based on customer classification.

```yaml
customer_routing:
  type: Choice
  choices:
    - condition:
        variable: "$.customerType"
        stringEquals: "vip"
      next: "vip_processing"
    
    - condition:
        variable: "$.customerType"
        stringEquals: "premium"
      next: "premium_processing"
  
  default: "standard_processing"
```

### 4. Status-Based Routing

Route based on current status or state.

```yaml
status_routing:
  type: Choice
  choices:
    - condition:
        variable: "$.orderStatus"
        stringEquals: "pending"
      next: "process_pending_order"
    
    - condition:
        variable: "$.orderStatus"
        stringEquals: "approved"
      next: "fulfill_approved_order"
  
  default: "handle_unknown_status"
```

### 5. Error-Based Routing

Route based on error conditions or validation results.

```yaml
error_routing:
  type: Choice
  choices:
    - condition:
        variable: "$.validationResult.isValid"
        booleanEquals: true
      next: "continue_processing"
    
    - condition:
        variable: "$.validationResult.errorType"
        stringEquals: "ValidationError"
      next: "handle_validation_error"
  
  default: "handle_unknown_error"
```

## Workarounds for Complex Logic

Since complex logical operators are not yet supported, use these workarounds:

### 1. Multiple Choice States

Break complex logic into multiple choice states:

```yaml
# Instead of: AND(amount > 100, customerType == "premium")
# Use multiple choice states:

check_amount:
  type: Choice
  choices:
    - condition:
        variable: "$.amount"
        numericGreaterThan: 100
      next: "check_customer_type"
  default: "standard_processing"

check_customer_type:
  type: Choice
  choices:
    - condition:
        variable: "$.customerType"
        stringEquals: "premium"
      next: "premium_processing"
  default: "standard_processing"
```

### 2. Pre-compute Flags

Use a task to compute boolean flags:

```yaml
compute_flags:
  type: Task
  resource: "logicService.computeFlags"
  next: "route_by_flags"
  parameters:
    input: "$.input"

route_by_flags:
  type: Choice
  choices:
    - condition:
        variable: "$.computedFlags.isHighValuePremium"
        booleanEquals: true
      next: "premium_processing"
  
  default: "standard_processing"
```

### 3. Sequential Validation

Chain multiple validation steps:

```yaml
validate_order:
  type: Choice
  choices:
    - condition:
        variable: "$.order.isValid"
        booleanEquals: true
      next: "check_inventory"
  default: "handle_invalid_order"

check_inventory:
  type: Choice
  choices:
    - condition:
        variable: "$.inventory.available"
        booleanEquals: true
      next: "process_payment"
  default: "handle_inventory_shortage"
```

## Best Practices

### 1. Condition Ordering

Order conditions from most specific to least specific.

```yaml
# Good: Specific to general
choices:
  - condition:
      variable: "$.customerType"
      stringEquals: "vip"
    next: "vip_processing"
  
  - condition:
      variable: "$.amount"
      numericGreaterThan: 10000
    next: "high_value_processing"
  
  default: "standard_processing"
```

### 2. Default State

Always provide a default state for unmatched conditions.

```yaml
# Good: Always has a fallback
default: "handle_unexpected_condition"

# Avoid: No fallback for unexpected conditions
# Missing default state
```

### 3. Simple Conditions

Keep conditions simple and readable.

```yaml
# Good: Clear and readable
condition:
  variable: "$.isValid"
  booleanEquals: true

# Avoid: Complex conditions (not supported anyway)
# condition:
#   And:
#     - variable: "$.condition1"
#       booleanEquals: true
#     - variable: "$.condition2"
#       booleanEquals: false
```

### 4. Variable Naming

Use descriptive variable names and paths.

```yaml
# Good: Clear variable names
variable: "$.orderValidationResult.isValid"
variable: "$.customerProfile.riskLevel"
variable: "$.paymentProcessing.status"

# Avoid: Unclear variable names
variable: "$.result.flag"
variable: "$.data.value"
variable: "$.status.code"
```

### 5. Error Handling

Include error handling in your choice logic.

```yaml
# Good: Handles error conditions
choices:
  - condition:
      variable: "$.validationResult.isValid"
      booleanEquals: true
    next: "continue_processing"
  
  - condition:
      variable: "$.validationResult.errorType"
      stringEquals: "ValidationError"
    next: "handle_validation_error"
  
  default: "handle_unknown_condition"
```

## Performance Considerations

### 1. Condition Evaluation Order

Conditions are evaluated in order, so place frequently matched conditions first.

```yaml
# Optimize for common cases
choices:
  - condition:
      variable: "$.status"
      stringEquals: "approved"  # Most common case
    next: "process_approved"
  
  - condition:
      variable: "$.status"
      stringEquals: "pending"   # Second most common
    next: "process_pending"
  
  - condition:
      variable: "$.status"
      stringEquals: "rejected"  # Less common
    next: "process_rejected"
```

### 2. Avoid Expensive Operations

Don't perform expensive operations in conditions.

```yaml
# Good: Use pre-calculated values
condition:
  variable: "$.preCalculatedScore"
  numericGreaterThan: 80

# Avoid: Expensive operations in conditions
condition:
  variable: "$.expensiveCalculation.result"  # This may be slow
  numericGreaterThan: 80
```

## Testing Choice Logic

### 1. Unit Testing

Test individual conditions and choice logic.

```yaml
test_choice_workflow:
  startAt: test_choice
  
  states:
    test_choice:
      type: Choice
      choices:
        - condition:
            variable: "$.testValue"
            stringEquals: "expected"
          next: "success"
      
      default: "failure"
    
    success:
      type: Success
    
    failure:
      type: Fail
      error: "TestFailed"
      cause: "Choice condition did not match expected value"
```

### 2. Edge Case Testing

Test boundary conditions and edge cases.

```yaml
test_edge_cases:
  startAt: test_boundaries
  
  states:
    test_boundaries:
      type: Choice
      choices:
        - condition:
            variable: "$.amount"
            numericEquals: 0
          next: "zero_amount"
        
        - condition:
            variable: "$.amount"
            numericLessThan: 0
          next: "negative_amount"
        
        - condition:
            variable: "$.amount"
            numericGreaterThan: 0
          next: "positive_amount"
      
      default: "invalid_amount"
```

## Common Pitfalls

### 1. Missing Default State

Always provide a default state to handle unexpected conditions.

```yaml
# Problem: No default state
choices:
  - condition:
      variable: "$.status"
      stringEquals: "approved"
    next: "approved_processing"

# Solution: Include default state
choices:
  - condition:
      variable: "$.status"
      stringEquals: "approved"
    next: "approved_processing"

default: "handle_unexpected_status"
```

### 2. Overlapping Conditions

Avoid conditions that can match simultaneously.

```yaml
# Problem: Overlapping conditions
choices:
  - condition:
      variable: "$.amount"
      numericGreaterThan: 100
    next: "high_value"
  
  - condition:
      variable: "$.amount"
      numericGreaterThan: 50
    next: "medium_value"  # This will never be reached for amounts > 100

# Solution: Non-overlapping conditions
choices:
  - condition:
      variable: "$.amount"
      numericGreaterThan: 100
    next: "high_value"
  
  - condition:
      variable: "$.amount"
      numericEquals: 50
    next: "medium_value"
```

### 3. Expecting Complex Logic

Don't expect complex logical operators that aren't implemented.

```yaml
# Problem: Expecting AND logic (not implemented)
condition:
  And:
    - variable: "$.amount"
      numericGreaterThan: 100
    - variable: "$.customerType"
      stringEquals: "premium"

# Solution: Use multiple choice states or pre-computed flags
# See "Workarounds for Complex Logic" section above
```

## Future Enhancements

The following features are planned for future releases:

### Planned Logical Operators
- **AND Operator** - Multiple conditions must all be true
- **OR Operator** - At least one condition must be true
- **NOT Operator** - Negate a condition

### Planned Advanced Conditions
- **String Pattern Matching** - Regex pattern matching
- **String Comparisons** - Less than, greater than
- **Existence Checks** - isPresent, isNull, isTruthy
- **Array Operations** - Length checks, contains

### Planned Complex Conditions
- **Nested Logical Operators** - Combining AND, OR, NOT
- **Condition Groups** - Logical grouping of conditions
- **Condition Reuse** - Named, reusable condition blocks

## Conclusion

Choice states provide basic conditional logic for workflow routing. While the current implementation supports fundamental condition types, advanced logical operators and complex nested conditions are not yet available.

Key takeaways:
- **Choice states** enable basic conditional workflow routing
- **Supported condition types** include string, numeric, and boolean comparisons
- **JSONPath expressions** reference workflow data
- **Workarounds exist** for complex logic using multiple choice states
- **Best practices** ensure maintainable choice logic
- **Future enhancements** will add logical operators and advanced conditions

For complex conditional logic, use the workarounds described in this guide or consider breaking complex decisions into multiple simpler choice states.
