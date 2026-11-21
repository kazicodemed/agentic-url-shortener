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
public class DecisionRecord {
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private OrchestrationRun run;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private TaskExecution task;

    @Column(columnDefinition = "TEXT")
    private String question;


    @Column(columnDefinition = "TEXT")
    private String decision;

    private String decidedBy;

    private LocalDateTime decidedAt;

    @PrePersist
    protected void onCreate() {
        if (decidedAt == null) {
            decidedAt = LocalDateTime.now();
        }
    }
}


