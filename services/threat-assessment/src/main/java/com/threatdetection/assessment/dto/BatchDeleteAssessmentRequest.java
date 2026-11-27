package com.threatdetection.assessment.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchDeleteAssessmentRequest {

    @NotEmpty(message = "At least one assessment id is required")
    @Size(max = 500, message = "At most 500 assessments can be deleted at once")
    private List<@Positive(message = "Assessment id must be positive") Long> ids;
}
