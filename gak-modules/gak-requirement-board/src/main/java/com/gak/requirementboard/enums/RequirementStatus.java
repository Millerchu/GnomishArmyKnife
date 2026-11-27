package com.gak.requirementboard.enums;

/**
 * 需求看板的固定流转状态。
 */
public enum RequirementStatus {
    PENDING_REVIEW,
    PLANNED,
    IN_PROGRESS,
    COMPLETED,
    DECLINED;

    public boolean canTransitionTo(RequirementStatus target) {
        return switch (this) {
            case PENDING_REVIEW -> target == PLANNED || target == IN_PROGRESS || target == DECLINED;
            case PLANNED -> target == IN_PROGRESS || target == COMPLETED || target == DECLINED;
            case IN_PROGRESS -> target == COMPLETED || target == DECLINED;
            case COMPLETED, DECLINED -> target == PENDING_REVIEW;
        };
    }
}
