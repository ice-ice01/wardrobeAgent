package com.wardrobe.agent.tryon;

/** 外部异步任务可能经历的完整生命周期，包含超时、迟到成功和提交状态未知等边界状态。 */
public enum TryOnStatus {
    CREATED, SUBMITTING, SUBMITTED, PROCESSING, SUCCEEDED, SUCCEEDED_LATE,
    PARTIALLY_SUCCEEDED, FAILED, TIMEOUT, CANCELLED, SUBMISSION_UNKNOWN
}
