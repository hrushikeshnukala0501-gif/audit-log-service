package com.auditlog.api.validation;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class JsonObjectValidator implements ConstraintValidator<ValidJsonObject, JsonNode> {

    @Override
    public boolean isValid(JsonNode value, ConstraintValidatorContext context) {
        return value == null || value.isObject();
    }
}
