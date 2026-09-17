package com.wardrobe.agent.evaluation;

import com.wardrobe.agent.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "ai_evaluation_case_result",
        uniqueConstraints = @UniqueConstraint(name = "uk_eval_case", columnNames = {"run_id", "case_id"}))
public class EvaluationCaseResult extends AuditedEntity {
    @Column(name = "run_id", nullable = false, length = 40) private String runId;
    @Column(name = "case_id", nullable = false, length = 100) private String caseId;
    @Column(nullable = false, length = 30) private String status;
    @Column(nullable = false) private long durationMs;
    @Column(nullable = false, length = 1000) private String assertionSummary;
    @Column(columnDefinition = "text") private String outputSummary;

    protected EvaluationCaseResult() {}

    public EvaluationCaseResult(String runId, String caseId, boolean passed, long durationMs,
                                String assertionSummary, String outputSummary) {
        super("ecase");
        this.runId = runId;
        this.caseId = caseId;
        this.status = passed ? "PASSED" : "FAILED";
        this.durationMs = durationMs;
        this.assertionSummary = assertionSummary;
        this.outputSummary = outputSummary;
    }
}
