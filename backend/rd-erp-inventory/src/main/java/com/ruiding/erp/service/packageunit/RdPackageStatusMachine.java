package com.ruiding.erp.service.packageunit;

import com.ruiding.erp.enums.RdPackageStatusEnum;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class RdPackageStatusMachine {
    private static final Map<RdPackageStatusEnum, Set<RdPackageStatusEnum>> TRANSITIONS =
            new EnumMap<>(RdPackageStatusEnum.class);

    static {
        allow(RdPackageStatusEnum.CREATED, RdPackageStatusEnum.LABELED, RdPackageStatusEnum.VOIDED);
        allow(RdPackageStatusEnum.LABELED, RdPackageStatusEnum.QC_PENDING, RdPackageStatusEnum.VOIDED);
        allow(RdPackageStatusEnum.QC_PENDING, RdPackageStatusEnum.QC_PASSED, RdPackageStatusEnum.QC_REJECTED);
        allow(RdPackageStatusEnum.QC_REJECTED, RdPackageStatusEnum.QC_PENDING, RdPackageStatusEnum.VOIDED);
        allow(RdPackageStatusEnum.QC_PASSED, RdPackageStatusEnum.INBOUND_PENDING, RdPackageStatusEnum.VOIDED);
        allow(RdPackageStatusEnum.INBOUND_PENDING, RdPackageStatusEnum.IN_STOCK, RdPackageStatusEnum.QC_PASSED);
        allow(RdPackageStatusEnum.IN_STOCK, RdPackageStatusEnum.OUTBOUND_PENDING, RdPackageStatusEnum.QC_PASSED);
        allow(RdPackageStatusEnum.OUTBOUND_PENDING, RdPackageStatusEnum.OUTBOUNDED, RdPackageStatusEnum.IN_STOCK);
    }

    private RdPackageStatusMachine() {}

    private static void allow(RdPackageStatusEnum from, RdPackageStatusEnum... to) {
        TRANSITIONS.put(from, EnumSet.of(to[0], to));
    }

    public static void requireTransition(RdPackageStatusEnum from, RdPackageStatusEnum to) {
        if (!TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new IllegalStateException("Illegal package status transition: " + from + " -> " + to);
        }
    }
}
