package com.auditlog.api.dto;
import jakarta.validation.constraints.*;
public record CreateRedactionRequest(@NotBlank @Pattern(regexp="/.*") String jsonPointer,@NotBlank String reason,@NotBlank String policyVersion,@NotBlank String authorizedBy){ }
