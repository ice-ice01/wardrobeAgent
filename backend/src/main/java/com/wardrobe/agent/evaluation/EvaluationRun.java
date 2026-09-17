package com.wardrobe.agent.evaluation;

import com.wardrobe.agent.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "ai_evaluation_run")
public class EvaluationRun extends AuditedEntity {
    @Column(nullable = false, length = 80) private String datasetVersion;
    @Column(nullable = false, length = 80) private String gitCommit;
    @Column(nullable = false, length = 30) private String runMode;
    @Column(nullable = false, length = 30) private String status;
    @Column(nullable = false) private int totalCases;
    @Column(nullable = false) private int passedCases;
    @Column(nullable = false) private int failedCases;
    @Column(nullable = false) private Instant startedAt;
    private Instant completedAt;
    @Column(length = 500) private String reportPath;

    protected EvaluationRun() {}

    public EvaluationRun(String datasetVersion, String gitCommit, String runMode, int totalCases) {
        super("evalrun");
        this.datasetVersion = datasetVersion;
        this.gitCommit = gitCommit;
        this.runMode = runMode;
        this.status = "RUNNING";
        this.totalCases = totalCases;
        this.startedAt = Instant.now();
    }

    public void complete(int passedCases, int failedCases, String reportPath) {
        this.passedCases = passedCases;
        this.failedCases = failedCases;
        this.status = failedCases == 0 ? "PASSED" : "FAILED";
        this.reportPath = reportPath;
        this.completedAt = Instant.now();
    }

    public String getDatasetVersion() { return datasetVersion; }
    public String getRunMode() { return runMode; }
    public String getStatus() { return status; }
    public int getTotalCases() { return totalCases; }
    public int getPassedCases() { return passedCases; }
    public int getFailedCases() { return failedCases; }
}
