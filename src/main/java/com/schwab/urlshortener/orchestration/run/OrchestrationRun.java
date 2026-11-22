package com.schwab.urlshortener.orchestration.run;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrchestrationRun {
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String rawRequirement;

    @Enumerated(EnumType.STRING)
    private RunStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /** Policy chosen when the run starts: a person must approve the plan before execution. */
    private Boolean reviewPlan;

    public boolean requiresPlanReview() {
        return Boolean.TRUE.equals(reviewPlan);
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = RunStatus.RUNNING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
