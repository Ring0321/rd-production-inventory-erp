package com.ruiding.erp.service.putaway;

import com.ruiding.erp.enums.RdPutawayTaskActionEnum;
import com.ruiding.erp.enums.RdPutawayTaskStatusEnum;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public final class RdPutawayTaskStatusMachine {
    private static final Map<RdPutawayTaskStatusEnum, Map<RdPutawayTaskActionEnum, RdPutawayTaskStatusEnum>>
            TRANSITIONS = new EnumMap<>(RdPutawayTaskStatusEnum.class);

    static {
        allow(RdPutawayTaskStatusEnum.PENDING_PUTAWAY,
                RdPutawayTaskActionEnum.ASSIGN, RdPutawayTaskStatusEnum.ASSIGNED);
        allow(RdPutawayTaskStatusEnum.PENDING_PUTAWAY,
                RdPutawayTaskActionEnum.CANCEL, RdPutawayTaskStatusEnum.CANCELLED);

        allow(RdPutawayTaskStatusEnum.ASSIGNED,
                RdPutawayTaskActionEnum.START, RdPutawayTaskStatusEnum.MOVING);
        allow(RdPutawayTaskStatusEnum.ASSIGNED,
                RdPutawayTaskActionEnum.REPORT_EXCEPTION, RdPutawayTaskStatusEnum.EXCEPTION_PENDING);
        allow(RdPutawayTaskStatusEnum.ASSIGNED,
                RdPutawayTaskActionEnum.CANCEL, RdPutawayTaskStatusEnum.CANCELLED);

        allow(RdPutawayTaskStatusEnum.MOVING,
                RdPutawayTaskActionEnum.COMPLETE_SCANNING, RdPutawayTaskStatusEnum.PENDING_CONFIRMATION);
        allow(RdPutawayTaskStatusEnum.MOVING,
                RdPutawayTaskActionEnum.REPORT_EXCEPTION, RdPutawayTaskStatusEnum.EXCEPTION_PENDING);

        allow(RdPutawayTaskStatusEnum.PENDING_CONFIRMATION,
                RdPutawayTaskActionEnum.CONFIRM_PUTAWAY, RdPutawayTaskStatusEnum.PUTAWAY_COMPLETED);
        allow(RdPutawayTaskStatusEnum.PENDING_CONFIRMATION,
                RdPutawayTaskActionEnum.RETURN_TO_DRIVER, RdPutawayTaskStatusEnum.ASSIGNED);

        allow(RdPutawayTaskStatusEnum.EXCEPTION_PENDING,
                RdPutawayTaskActionEnum.REASSIGN, RdPutawayTaskStatusEnum.ASSIGNED);
        allow(RdPutawayTaskStatusEnum.EXCEPTION_PENDING,
                RdPutawayTaskActionEnum.CANCEL, RdPutawayTaskStatusEnum.CANCELLED);
    }

    private RdPutawayTaskStatusMachine() {
    }

    public static RdPutawayTaskStatusEnum transition(RdPutawayTaskStatusEnum current,
                                                      RdPutawayTaskActionEnum action) {
        RdPutawayTaskStatusEnum target = TRANSITIONS
                .getOrDefault(current, Collections.emptyMap())
                .get(action);
        if (target == null) {
            throw new IllegalStateException("Illegal putaway task transition: " + current + " + " + action);
        }
        return target;
    }

    public static boolean canTransition(RdPutawayTaskStatusEnum current, RdPutawayTaskActionEnum action) {
        return TRANSITIONS.getOrDefault(current, Collections.emptyMap()).containsKey(action);
    }

    public static Set<RdPutawayTaskActionEnum> allowedActions(RdPutawayTaskStatusEnum current) {
        return Set.copyOf(TRANSITIONS.getOrDefault(current, Collections.emptyMap()).keySet());
    }

    private static void allow(RdPutawayTaskStatusEnum current, RdPutawayTaskActionEnum action,
                              RdPutawayTaskStatusEnum target) {
        RdPutawayTaskStatusEnum previous = TRANSITIONS
                .computeIfAbsent(current, ignored -> new EnumMap<>(RdPutawayTaskActionEnum.class))
                .put(action, target);
        if (previous != null) {
            throw new IllegalStateException("Duplicate putaway task transition: " + current + " + " + action);
        }
    }
}
