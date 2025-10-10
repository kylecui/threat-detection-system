package com.threatdetection.assessment.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * Mitigation recommendation entity
 */
@Entity
@Table(name = "recommendations")
public class Recommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "action")
    private MitigationAction action;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "priority")
    private Priority priority;

    @NotBlank
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @ElementCollection
    @CollectionTable(name = "recommendation_parameters",
                     joinColumns = @JoinColumn(name = "recommendation_id"))
    @MapKeyColumn(name = "param_key")
    @Column(name = "param_value")
    private Map<String, String> parameters;

    @Column(name = "executed")
    private boolean executed = false;

    @Column(name = "execution_timestamp")
    private java.time.LocalDateTime executionTimestamp;

    // Constructors
    public Recommendation() {}

    public Recommendation(MitigationAction action, Priority priority, String description) {
        this.action = action;
        this.priority = priority;
        this.description = description;
    }

    public Recommendation(MitigationAction action, Priority priority, String description,
                         Map<String, String> parameters) {
        this.action = action;
        this.priority = priority;
        this.description = description;
        this.parameters = parameters;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public MitigationAction getAction() { return action; }
    public void setAction(MitigationAction action) { this.action = action; }

    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Map<String, String> getParameters() { return parameters; }
    public void setParameters(Map<String, String> parameters) { this.parameters = parameters; }

    public boolean isExecuted() { return executed; }
    public void setExecuted(boolean executed) { this.executed = executed; }

    public java.time.LocalDateTime getExecutionTimestamp() { return executionTimestamp; }
    public void setExecutionTimestamp(java.time.LocalDateTime executionTimestamp) {
        this.executionTimestamp = executionTimestamp;
    }
}