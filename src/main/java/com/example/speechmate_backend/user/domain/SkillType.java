package com.example.speechmate_backend.user.domain;

public enum SkillType {
    REDUCE_NERVOUSNESS(GoalCategory.VERBAL),
    BE_CONFIDENT(GoalCategory.VERBAL),
    SPEAK_AT_PROPER_SPEED(GoalCategory.VERBAL),
    CLEAR_PRONUNCIATION(GoalCategory.VERBAL),
    REDUCE_FILLER_WORDS(GoalCategory.VERBAL),

    // Non-Verbal
    NATURAL_EXPRESSION(GoalCategory.NON_VERBAL),
    MAINTAIN_GOOD_POSTURE(GoalCategory.NON_VERBAL),
    REDUCE_HAND_MOVEMENTS(GoalCategory.NON_VERBAL),
    REDUCE_HEAD_MOVEMENTS(GoalCategory.NON_VERBAL),
    CONTROL_ARM_MOVEMENTS(GoalCategory.NON_VERBAL);

    private final GoalCategory category;

    SkillType(GoalCategory category) {
        this.category = category;
    }

    public GoalCategory getCategory() {
        return category;
    }

    public enum GoalCategory {
        VERBAL,
        NON_VERBAL
    }

}
