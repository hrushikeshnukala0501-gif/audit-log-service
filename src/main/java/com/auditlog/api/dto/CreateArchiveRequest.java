package com.auditlog.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateArchiveRequest(@NotBlank(message = "archivedBy is required") String archivedBy) { }
