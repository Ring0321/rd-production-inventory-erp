package com.ruiding.erp.service.ai;

import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.enums.RdPackageStatusEnum;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RdPackageAiDiagnosticsTest {

    @Test
    void mixedStatesProduceIndependentFindingsWithPackageCountsAndClosedMetadata() {
        var members = List.of(
                unit("V1", "VOIDED", "12", null, null),
                unit("V2", "VOIDED", "13", null, null),
                unit("R1", "QC_REJECTED", "17", null, null),
                unit("Q1", "QC_PENDING", "19", null, null),
                unit("M1", "STOCKTAKE_MISSING", "23", null, null),
                unit("U1", "private-invalid-status", "29", null, null),
                unit("S1", "IN_STOCK", null, null, "A"),
                unit("O1", "OUTBOUND_PENDING", "0", 20L, " "),
                unit("S2", "IN_STOCK", "-3", 30L, "C"));

        var diagnosis = RdPackageAiDiagnostics.inspect(members, true);

        assertEquals("PRODUCTION_OUTPUT", diagnosis.scope());
        assertEquals("KNOWN", diagnosis.state());
        assertEquals(9, diagnosis.inspectedPackages());
        assertEquals(List.of("DIAG_VOIDED", "DIAG_QUALITY_REJECTED", "DIAG_QUALITY_PENDING",
                        "DIAG_STOCKTAKE_MISSING", "DIAG_UNKNOWN_STATUS", "DIAG_INVALID_WEIGHT",
                        "DIAG_MISSING_LOCATION", "DIAG_SPLIT_LOCATION"),
                diagnosis.findings().stream().map(RdPackageAiDiagnostics.Finding::key).toList());
        var expected = Map.of(
                "DIAG_VOIDED", List.of("HIGH", "PRODUCTION", 2),
                "DIAG_QUALITY_REJECTED", List.of("HIGH", "QUALITY", 1),
                "DIAG_QUALITY_PENDING", List.of("REVIEW", "QUALITY", 1),
                "DIAG_STOCKTAKE_MISSING", List.of("HIGH", "WAREHOUSE", 1),
                "DIAG_UNKNOWN_STATUS", List.of("REVIEW", "PRODUCTION", 1),
                "DIAG_INVALID_WEIGHT", List.of("REVIEW", "PRODUCTION", 3),
                "DIAG_MISSING_LOCATION", List.of("REVIEW", "WAREHOUSE", 2),
                "DIAG_SPLIT_LOCATION", List.of("INFO", "WAREHOUSE", 2));
        for (var finding : diagnosis.findings()) {
            assertEquals(expected.get(finding.key()),
                    List.of(finding.severity(), finding.owner(), finding.affectedCount()));
            assertFalse(finding.title().isBlank());
            assertFalse(finding.detail().isBlank());
            assertFalse(finding.evidence().isBlank());
            assertTrue(finding.title().matches(".*[\u4e00-\u9fff].*"));
            assertTrue(finding.detail().matches(".*[\u4e00-\u9fff].*"));
            assertTrue(finding.evidence().matches(".*[\u4e00-\u9fff].*"));
        }
        assertEquals(List.of("V1", "V2"), finding(diagnosis, "DIAG_VOIDED").packageCodes());
        assertEquals(List.of("S1", "O1", "S2"), finding(diagnosis, "DIAG_INVALID_WEIGHT").packageCodes());
        assertTrue(finding(diagnosis, "DIAG_QUALITY_PENDING").detail().contains("待检不等于不合格或已放行"));
    }

    @Test
    void unknownSourceDoesNotEvenReadMembersAndNeverTurnsUnknownIntoZero() {
        List<RdPackageUnitDO> unreadable = new AbstractList<>() {
            @Override public RdPackageUnitDO get(int index) { throw new AssertionError("Members must not be read"); }
            @Override public int size() { throw new AssertionError("Members must not be counted"); }
        };

        var diagnosis = RdPackageAiDiagnostics.inspect(unreadable, false);

        assertEquals("PRODUCTION_OUTPUT", diagnosis.scope());
        assertEquals("UNKNOWN", diagnosis.state());
        assertNull(diagnosis.inspectedPackages());
        assertEquals(1, diagnosis.findings().size());
        var finding = finding(diagnosis, "DIAG_SOURCE_UNKNOWN");
        assertEquals("REVIEW", finding.severity());
        assertEquals("PRODUCTION", finding.owner());
        assertNull(finding.affectedCount());
        assertEquals(List.of(), finding.packageCodes());
        assertEquals(diagnosis, RdPackageAiDiagnostics.inspect(List.of(), false));
        assertEquals(diagnosis, RdPackageAiDiagnostics.inspect(null, false));
        assertEquals(diagnosis, RdPackageAiDiagnostics.inspect(
                List.of(unit("PRIVATE", "VOIDED", "-1", 99L, "PRIVATE-LOCATION")), false));
    }

    @Test
    void explicitlyKnownEmptyMembersAreZeroRatherThanUnknown() {
        var diagnosis = RdPackageAiDiagnostics.inspect(List.of(), true);

        assertEquals("KNOWN", diagnosis.state());
        assertEquals(0, diagnosis.inspectedPackages());
        assertTrue(diagnosis.findings().isEmpty());
    }

    @Test
    void absentMembersOrNullRowsCannotProduceACompleteKnownInspection() {
        var expected = RdPackageAiDiagnostics.inspect(null, false);

        assertEquals(expected, RdPackageAiDiagnostics.inspect(null, true));
        assertEquals(expected, RdPackageAiDiagnostics.inspect(
                Arrays.asList(unit("V1", "VOIDED", "10", null, null), null), true));
    }

    @Test
    void allRegisteredStatesAreRecognizedWithoutApprovingAvailabilityOrQualityRelease() {
        var members = Arrays.stream(RdPackageStatusEnum.values())
                .map(status -> unit(status.getStatus(), status.getStatus(), "697.25", 1L, "A"))
                .toList();

        var diagnosis = RdPackageAiDiagnostics.inspect(members, true);

        assertEquals(List.of("DIAG_VOIDED", "DIAG_QUALITY_REJECTED", "DIAG_QUALITY_PENDING",
                        "DIAG_STOCKTAKE_MISSING"),
                diagnosis.findings().stream().map(RdPackageAiDiagnostics.Finding::key).toList());
        var clean = RdPackageAiDiagnostics.inspect(List.of(
                unit("C1", "CREATED", "0.001", null, null),
                unit("I1", "IN_STOCK", "697.25", 1L, "A"),
                unit("O1", "OUTBOUND_PENDING", "1500.5", 1L, "A")), true);
        assertTrue(clean.findings().isEmpty());
        assertEquals("KNOWN", clean.state());
    }

    @Test
    void nullBlankAndUnrecognizedStatusesAreUnknownWithoutEchoingThem() {
        var members = Arrays.asList(
                unit("U1", null, "1", null, null),
                unit("U2", "", "2", null, null),
                unit("U3", " ", "3", null, null),
                unit("U4", "private-status-instruction", "4", null, null),
                unit("U5", "in_stock", "5", null, null));

        var diagnosis = RdPackageAiDiagnostics.inspect(members, true);

        assertEquals(1, diagnosis.findings().size());
        var finding = finding(diagnosis, "DIAG_UNKNOWN_STATUS");
        assertEquals(5, finding.affectedCount());
        assertFalse(finding.title().contains("private-status-instruction"));
        assertFalse(finding.detail().contains("private-status-instruction"));
        assertFalse(finding.evidence().contains("private-status-instruction"));
    }

    @Test
    void incompleteZeroAndNegativeWeightsAreCountedWithoutSubstitutingOneTon() {
        var diagnosis = RdPackageAiDiagnostics.inspect(List.of(
                unit("W1", "CREATED", null, null, null),
                unit("W2", "CREATED", "0.00", null, null),
                unit("W3", "VOIDED", "-0.001", null, null),
                unit("W4", "CREATED", "0.001", null, null),
                unit("W5", "CREATED", "697", null, null),
                unit("W6", "CREATED", "1500", null, null)), true);

        var finding = finding(diagnosis, "DIAG_INVALID_WEIGHT");
        assertEquals(3, finding.affectedCount());
        assertEquals(List.of("W1", "W2", "W3"), finding.packageCodes());
        assertFalse(diagnosis.toString().contains("1000"));
    }

    @Test
    void missingLocationRequiresBothIdentityAndNonblankCodeOnlyForStoredOrOutboundPending() {
        var diagnosis = RdPackageAiDiagnostics.inspect(List.of(
                unit("L1", "IN_STOCK", "1", null, "A"),
                unit("L2", "OUTBOUND_PENDING", "2", 1L, null),
                unit("L3", "IN_STOCK", "3", 1L, ""),
                unit("L4", "OUTBOUND_PENDING", "4", 1L, " \t\n"),
                unit("L5", "IN_STOCK", "5", null, null),
                unit("L6", "IN_STOCK", "6", 1L, "A"),
                unit("L7", "CREATED", "7", null, null),
                unit("L8", "QC_PASSED", "8", null, null),
                unit("L9", "INBOUND_PENDING", "9", null, null),
                unit("L10", "OUTBOUNDED", "10", null, null)), true);

        assertEquals(1, diagnosis.findings().size());
        var finding = finding(diagnosis, "DIAG_MISSING_LOCATION");
        assertEquals(5, finding.affectedCount());
        assertEquals(List.of("L1", "L2", "L3", "L4", "L5"), finding.packageCodes());
        assertEquals("WAREHOUSE", finding.owner());
    }

    @Test
    void spreadAcrossDistinctPresentLocationIdsIsInformationNotAnError() {
        var diagnosis = RdPackageAiDiagnostics.inspect(List.of(
                unit("L1", "IN_STOCK", "1", 10L, "SAME-CODE"),
                unit("L2", "OUTBOUND_PENDING", "2", 10L, "OTHER-CODE"),
                unit("L3", "IN_STOCK", "3", 20L, "SAME-CODE"),
                unit("L4", "CREATED", "4", null, "THIRD-CODE")), true);

        assertEquals(1, diagnosis.findings().size());
        var finding = finding(diagnosis, "DIAG_SPLIT_LOCATION");
        assertEquals("INFO", finding.severity());
        assertEquals("WAREHOUSE", finding.owner());
        assertEquals(3, finding.affectedCount());
        assertEquals(List.of("L1", "L2", "L3"), finding.packageCodes());
        assertTrue(finding.detail().contains("不视为错误"));
    }

    @Test
    void duplicateAndAbsentLocationIdsDoNotImplyLocationSpread() {
        var diagnosis = RdPackageAiDiagnostics.inspect(List.of(
                unit("L1", "CREATED", "1", 10L, "A"),
                unit("L2", "CREATED", "2", 10L, "B"),
                unit("L3", "CREATED", "3", null, "C")), true);

        assertTrue(diagnosis.findings().isEmpty());
    }

    @Test
    void spreadCountsAllMembersWithPresentIdsEvenWhenSomeLocationCodesNeedReview() {
        var diagnosis = RdPackageAiDiagnostics.inspect(List.of(
                unit("L1", "CREATED", "1", 10L, null),
                unit("L2", "IN_STOCK", "2", 20L, " "),
                unit("L3", "IN_STOCK", "3", 20L, "B"),
                unit("L4", "INBOUND_PENDING", "4", null, null)), true);

        assertEquals(1, finding(diagnosis, "DIAG_MISSING_LOCATION").affectedCount());
        assertEquals(3, finding(diagnosis, "DIAG_SPLIT_LOCATION").affectedCount());
        assertEquals(List.of("L1", "L2", "L3"), finding(diagnosis, "DIAG_SPLIT_LOCATION").packageCodes());
    }

    @Test
    void samplesAreUniqueNonblankFirstSeenAndCappedIndependentlyFromAffectedCounts() {
        var members = new ArrayList<RdPackageUnitDO>();
        for (String code : Arrays.asList(null, "", " \t", "P1", "P1", "P2", "P3", "P4", "P5", "P6", "P7")) {
            members.add(unit(code, "VOIDED", null, 10L, "A"));
        }
        members.add(unit("P8", "VOIDED", null, 20L, "B"));

        var diagnosis = RdPackageAiDiagnostics.inspect(members, true);

        assertEquals(12, diagnosis.inspectedPackages());
        assertEquals(3, diagnosis.findings().size());
        for (var finding : diagnosis.findings()) {
            assertEquals(12, finding.affectedCount());
            assertEquals(List.of("P1", "P2", "P3", "P4", "P5"), finding.packageCodes());
            assertEquals(5, Set.copyOf(finding.packageCodes()).size());
        }
    }

    @Test
    void descriptiveTextIsFixedAndCannotIncludeUntrustedBusinessDetails() {
        for (String status : List.of("VOIDED", "QC_REJECTED", "QC_PENDING", "STOCKTAKE_MISSING",
                "IN_STOCK", "OUTBOUND_PENDING", "private-status")) {
            var clean = List.of(unit("P1", status, null, null, "A"), unit("P2", status, "-1", 20L, "B"),
                    unit("P3", status, "1", 30L, "C"));
            var changed = List.of(unit("P1", status, null, null, "private-location"),
                    unit("P2", status, "-1", 20L, "private-location"),
                    unit("P3", status, "1", 30L, "private-location"));
            for (var member : changed) {
                member.setRemark("private-remark-instruction");
                member.setCustomerName("private-client");
                member.setProductName("private-product");
                member.setProductModel("private-model");
                member.setFurnaceNo("private-furnace");
                member.setOperatorName("private-operator");
                member.setTenantId(999L);
                member.setProductionOutputId(999L);
            }

            assertEquals(RdPackageAiDiagnostics.inspect(clean, true),
                    RdPackageAiDiagnostics.inspect(changed, true));
        }
    }

    @Test
    void inspectionDoesNotChangeMembersAndResultListsCannotBeMutated() {
        var member = unit("P1", "IN_STOCK", "-1", null, "PRIVATE-LOCATION");
        member.setId(51L);
        member.setVersion(7);
        member.setProductionOutputId(5L);
        member.setRemark("private-remark");
        var second = unit("P0", "IN_STOCK", "697", 10L, "A");
        var members = new ArrayList<>(List.of(member, second));
        var before = new ArrayList<>(members);
        var originalFields = members.stream().map(RdPackageUnitDO::toString).toList();

        var diagnosis = RdPackageAiDiagnostics.inspect(members, true);

        assertEquals(before, members);
        assertSame(member, members.get(0));
        assertSame(second, members.get(1));
        assertEquals(originalFields, members.stream().map(RdPackageUnitDO::toString).toList());
        assertThrows(UnsupportedOperationException.class, () -> diagnosis.findings().clear());
        for (var finding : diagnosis.findings()) {
            assertThrows(UnsupportedOperationException.class, () -> finding.packageCodes().add("INJECTED"));
        }
        members.clear();
        member.setPackageCode("CHANGED");
        assertEquals(2, diagnosis.inspectedPackages());
        assertEquals(List.of("P1"), finding(diagnosis, "DIAG_INVALID_WEIGHT").packageCodes());
    }

    private static RdPackageAiDiagnostics.Finding finding(RdPackageAiDiagnostics.Diagnosis diagnosis, String key) {
        return diagnosis.findings().stream().filter(finding -> finding.key().equals(key)).findFirst().orElseThrow();
    }

    private static RdPackageUnitDO unit(String code, String status, String weight, Long locationId, String locationCode) {
        var member = new RdPackageUnitDO();
        member.setPackageCode(code);
        member.setStatus(status);
        member.setPackageWeight(weight == null ? null : new BigDecimal(weight));
        member.setLocationId(locationId);
        member.setLocationCode(locationCode);
        return member;
    }
}
