package com.ruiding.erp.service.loading;

import com.ruiding.erp.enums.RdLoadingTaskActionEnum;
import com.ruiding.erp.enums.RdLoadingTaskActorEnum;
import com.ruiding.erp.enums.RdLoadingTaskStatusEnum;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class RdLoadingTaskStatusMachine {
    private static final Map<RdLoadingTaskStatusEnum, Map<RdLoadingTaskActionEnum, RdLoadingTaskStatusEnum>>
            TRANSITIONS = new EnumMap<>(RdLoadingTaskStatusEnum.class);

    static {
        allow(RdLoadingTaskStatusEnum.PENDING_ASSIGNMENT,
                RdLoadingTaskActionEnum.ASSIGN, RdLoadingTaskStatusEnum.ASSIGNED);
        allow(RdLoadingTaskStatusEnum.PENDING_ASSIGNMENT,
                RdLoadingTaskActionEnum.CANCEL, RdLoadingTaskStatusEnum.CANCELLED);

        allow(RdLoadingTaskStatusEnum.ASSIGNED,
                RdLoadingTaskActionEnum.ADJUST_ASSIGNMENT, RdLoadingTaskStatusEnum.ASSIGNED);
        allow(RdLoadingTaskStatusEnum.ASSIGNED,
                RdLoadingTaskActionEnum.START, RdLoadingTaskStatusEnum.LOADING);
        allow(RdLoadingTaskStatusEnum.ASSIGNED,
                RdLoadingTaskActionEnum.REPORT_EXCEPTION, RdLoadingTaskStatusEnum.EXCEPTION_PENDING);
        allow(RdLoadingTaskStatusEnum.ASSIGNED,
                RdLoadingTaskActionEnum.CANCEL, RdLoadingTaskStatusEnum.CANCELLED);

        allow(RdLoadingTaskStatusEnum.LOADING,
                RdLoadingTaskActionEnum.COMPLETE_SCANNING, RdLoadingTaskStatusEnum.PENDING_REVIEW);
        allow(RdLoadingTaskStatusEnum.LOADING,
                RdLoadingTaskActionEnum.REPORT_EXCEPTION, RdLoadingTaskStatusEnum.EXCEPTION_PENDING);

        allow(RdLoadingTaskStatusEnum.PENDING_REVIEW,
                RdLoadingTaskActionEnum.CONFIRM_OUTBOUND, RdLoadingTaskStatusEnum.OUTBOUNDED);
        allow(RdLoadingTaskStatusEnum.PENDING_REVIEW,
                RdLoadingTaskActionEnum.RETURN_TO_DRIVER, RdLoadingTaskStatusEnum.ASSIGNED);

        allow(RdLoadingTaskStatusEnum.EXCEPTION_PENDING,
                RdLoadingTaskActionEnum.REASSIGN, RdLoadingTaskStatusEnum.ASSIGNED);
        allow(RdLoadingTaskStatusEnum.EXCEPTION_PENDING,
                RdLoadingTaskActionEnum.CANCEL, RdLoadingTaskStatusEnum.CANCELLED);
    }

    private RdLoadingTaskStatusMachine() {
    }

    public static RdLoadingTaskStatusEnum transition(RdLoadingTaskStatusEnum current,
                                                      RdLoadingTaskActionEnum action,
                                                      RdLoadingTaskActorEnum actor) {
        RdLoadingTaskStatusEnum target = TRANSITIONS
                .getOrDefault(current, Collections.emptyMap())
                .get(action);
        if (target == null || actor == null || action.getActor() != actor) {
            throw new IllegalStateException("Illegal loading task transition: " + current + " + " + action
                    + " by " + actor);
        }
        return target;
    }

    public static boolean canTransition(RdLoadingTaskStatusEnum current, RdLoadingTaskActionEnum action,
                                        RdLoadingTaskActorEnum actor) {
        return action != null && actor != null && action.getActor() == actor
                && TRANSITIONS.getOrDefault(current, Collections.emptyMap()).containsKey(action);
    }

    public static Set<RdLoadingTaskActionEnum> allowedActions(RdLoadingTaskStatusEnum current,
                                                               RdLoadingTaskActorEnum actor) {
        if (actor == null) {
            return Set.of();
        }
        return TRANSITIONS.getOrDefault(current, Collections.emptyMap()).keySet().stream()
                .filter(action -> action.getActor() == actor)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static void allow(RdLoadingTaskStatusEnum current, RdLoadingTaskActionEnum action,
                              RdLoadingTaskStatusEnum target) {
        RdLoadingTaskStatusEnum previous = TRANSITIONS
                .computeIfAbsent(current, ignored -> new EnumMap<>(RdLoadingTaskActionEnum.class))
                .put(action, target);
        if (previous != null) {
            throw new IllegalStateException("Duplicate loading task transition: " + current + " + " + action);
        }
    }
}
