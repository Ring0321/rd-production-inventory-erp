package com.ruiding.erp.dal.mysql.workassistant;

import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RdWorkQueryMapper {
    String PACKAGE_FILTER = " FROM rd_package_unit p WHERE p.tenant_id=#{tenantId} AND p.deleted=0 AND p.status IN "
            + "<foreach collection='statuses' item='status' open='(' separator=',' close=')'>#{status}</foreach> ";
    String HEADER_FILTER = " FROM rd_quality_inspection h WHERE h.tenant_id=#{tenantId} AND h.deleted=0 AND h.status='SUBMITTED' ";
    String MEMBER_FILTER = " FROM rd_quality_inspection_member m WHERE m.tenant_id=#{tenantId} AND m.deleted=0 AND m.inspection_id=h.id ";
    String VALID_MEMBER = " AND m.package_id>0 AND m.package_code IS NOT NULL AND TRIM(m.package_code)!='' ";

    @Select("<script>SELECT COUNT(*) AS package_count, SUM(p.package_weight) AS registered_weight, "
            + "COALESCE(SUM(CASE WHEN p.package_weight IS NULL OR p.package_weight&lt;0 THEN 1 ELSE 0 END),0) AS invalid_weight_count, "
            + "COALESCE(SUM(CASE WHEN p.status='QC_PASSED' THEN 1 ELSE 0 END),0) AS qc_passed_count, "
            + "COALESCE(SUM(CASE WHEN p.status='INBOUND_PENDING' THEN 1 ELSE 0 END),0) AS inbound_pending_count"
            + PACKAGE_FILTER + "</script>")
    PackageSummary selectPackageSummary(@Param("tenantId") Long tenantId, @Param("statuses") List<String> statuses);

    @Select("<script>SELECT p.id,p.tenant_id,p.package_code,p.package_weight,p.status,p.version,p.update_time"
            + PACKAGE_FILTER + "ORDER BY p.id LIMIT #{size} OFFSET #{offset}</script>")
    List<RdPackageUnitDO> selectPackages(@Param("tenantId") Long tenantId, @Param("statuses") List<String> statuses,
                                        @Param("offset") long offset, @Param("size") int size);

    @Select("SELECT COUNT(*) AS object_count, "
            + "COALESCE(SUM(CASE WHEN h.submit_time IS NULL THEN 1 ELSE 0 END),0) AS missing_submit_count, "
            + "COALESCE(SUM(CASE WHEN NOT EXISTS (SELECT 1" + MEMBER_FILTER + VALID_MEMBER + ") "
            + "OR EXISTS (SELECT 1" + MEMBER_FILTER
            + " AND (m.package_id IS NULL OR m.package_id<=0 OR m.package_code IS NULL OR TRIM(m.package_code)=''))"
            + " THEN 1 ELSE 0 END),0) AS invalid_coverage_count" + HEADER_FILTER)
    InspectionSummary selectInspectionSummary(@Param("tenantId") Long tenantId);

    @Select("SELECT COUNT(DISTINCT m.package_id) FROM rd_quality_inspection h "
            + "JOIN rd_quality_inspection_member m ON m.inspection_id=h.id AND m.tenant_id=h.tenant_id AND m.deleted=0 "
            + "WHERE h.tenant_id=#{tenantId} AND h.deleted=0 AND h.status='SUBMITTED'" + VALID_MEMBER)
    Long countCoveredPackages(@Param("tenantId") Long tenantId);

    @Select("SELECT h.id,h.tenant_id,h.inspection_no,h.status,h.submit_time,h.version,h.update_time, "
            + "(SELECT COUNT(DISTINCT m.package_id)" + MEMBER_FILTER + VALID_MEMBER + ") AS covered_package_count, "
            + "(SELECT COUNT(DISTINCT m.package_id)" + MEMBER_FILTER + VALID_MEMBER + " AND m.sampled=1) AS sampled_package_count, "
            + "(SELECT COUNT(*)" + MEMBER_FILTER
            + " AND (m.package_id IS NULL OR m.package_id<=0 OR m.package_code IS NULL OR TRIM(m.package_code)='')) AS invalid_member_count"
            + HEADER_FILTER + "ORDER BY h.id LIMIT #{size} OFFSET #{offset}")
    List<InspectionRow> selectInspections(@Param("tenantId") Long tenantId, @Param("offset") long offset, @Param("size") int size);

    // The preview is bounded independently from complete, authorized coverage counts.
    @Select("<script>SELECT inspection_id,package_id,package_code,sampled FROM ("
            + "SELECT covered.*,ROW_NUMBER() OVER (PARTITION BY inspection_id ORDER BY package_id) AS member_rank FROM ("
            + "SELECT m.inspection_id,m.package_id,MIN(m.package_code) AS package_code,MAX(CASE WHEN m.sampled=1 THEN 1 ELSE 0 END) AS sampled "
            + "FROM rd_quality_inspection_member m WHERE m.tenant_id=#{tenantId} AND m.deleted=0" + VALID_MEMBER
            + " AND m.inspection_id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "GROUP BY m.inspection_id,m.package_id) covered) ranked WHERE member_rank&lt;=8 ORDER BY inspection_id,package_id</script>")
    List<MemberRow> selectMemberPreview(@Param("tenantId") Long tenantId, @Param("ids") List<Long> ids);

    @Data class PackageSummary {
        private Long packageCount;
        private BigDecimal registeredWeight;
        private Long invalidWeightCount;
        private Long qcPassedCount;
        private Long inboundPendingCount;
    }
    @Data class InspectionSummary {
        private Long objectCount;
        private Long missingSubmitCount;
        private Long invalidCoverageCount;
    }
    @Data class InspectionRow {
        private Long id;
        private Long tenantId;
        private String inspectionNo;
        private String status;
        private LocalDateTime submitTime;
        private Integer version;
        private LocalDateTime updateTime;
        private Long coveredPackageCount;
        private Long sampledPackageCount;
        private Long invalidMemberCount;
    }
    @Data class MemberRow {
        private Long inspectionId;
        private Long packageId;
        private String packageCode;
        private Boolean sampled;
    }
}
