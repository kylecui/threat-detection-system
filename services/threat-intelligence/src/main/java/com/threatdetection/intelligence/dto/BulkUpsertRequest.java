package com.threatdetection.intelligence.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BulkUpsertRequest {

    @Valid
    @NotEmpty
    private List<CreateIndicatorRequest> indicators;
}
