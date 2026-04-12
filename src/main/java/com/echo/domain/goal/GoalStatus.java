package com.echo.domain.goal;

import java.util.List;

public enum GoalStatus {
    PENDING,
    ACTIVE,
    COMPLETED,
    DISMISSED,
    DELETED;

    public boolean isOpen() {
        return this == PENDING || this == ACTIVE;
    }

    public static List<GoalStatus> openStatuses() {
        return List.of(PENDING, ACTIVE);
    }
}
