package com.ruiding.erp.service.quality;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.ruiding.erp.controller.admin.quality.vo.*;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.dal.dataobject.production.RdProductionOutputDO;
import com.ruiding.erp.dal.dataobject.quality.RdQualityInspectionDO;
import com.ruiding.erp.dal.dataobject.quality.RdQualityInspectionItemDO;
import com.ruiding.erp.dal.dataobject.quality.RdQualityInspectionMemberDO;
import com.ruiding.erp.dal.mysql.packageunit.RdPackageUnitMapper;
import com.ruiding.erp.dal.mysql.production.RdProductionOutputMapper;
import com.ruiding.erp.dal.mysql.production.RdProductionTaskMapper;
import com.ruiding.erp.dal.mysql.quality.RdQualityInspectionItemMapper;
import com.ruiding.erp.dal.mysql.quality.RdQualityInspectionMapper;
import com.ruiding.erp.dal.mysql.quality.RdQualityInspectionMemberMapper;
import com.ruiding.erp.dal.dataobject.quality.RdQualityInspectionEventDO;
import com.ruiding.erp.dal.mysql.quality.RdQualityInspectionEventMapper;
import com.ruiding.erp.enums.RdLogRecordConstants;
import com.mzt.logapi.context.LogRecordContext;
import com.mzt.logapi.starter.annotation.LogRecord;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserNickname;
import static com.ruiding.erp.enums.ErrorCodeConstants.*;

@Service
public class RdQualityInspectionServiceImpl implements RdQualityInspectionService {
    @Resource private RdQualityInspectionMapper inspectionMapper;
    @Resource private RdQualityInspectionItemMapper itemMapper;
    @Resource private RdQualityInspectionMemberMapper memberMapper;
    @Resource private RdQualityInspectionEventMapper eventMapper;
    @Resource private RdPackageUnitMapper packageMapper;
    @Resource private RdProductionOutputMapper outputMapper;
    @Resource private RdProductionTaskMapper productionTaskMapper;

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    @LogRecord(type = RdLogRecordConstants.QUALITY_TYPE, subType = RdLogRecordConstants.QUALITY_CREATE_SUB_TYPE,
            bizNo = RdLogRecordConstants.BIZ_ID_EXPRESSION, success = RdLogRecordConstants.QUALITY_CREATE_SUCCESS)
    public Long create(Long packageId, List<RdQualityInspectionItemDO> items, String remark) {
        Long actor = requireActor(null);
        if (packageId == null || packageId <= 0 || tooLong(remark, 512)) {
            throw exception(QUALITY_INSPECTION_SELECTION_INVALID);
        }
        List<RdQualityInspectionItemDO> cleanItems = new ArrayList<>();
        if (items != null) for (RdQualityInspectionItemDO item : items) {
            if (item == null || item.getSamplePackageId() != null && !packageId.equals(item.getSamplePackageId())) {
                throw exception(QUALITY_INSPECTION_ITEMS_INVALID);
            }
            cleanItems.add(copyItem(item).setSamplePackageId(packageId));
        }
        validateItems(cleanItems, Set.of(packageId), false);
        RdPackageUnitDO before = requiredPackage(packageId);
        RdProductionOutputDO output = lockSource(before);
        List<RdPackageUnitDO> units = lockPackages(List.of(packageId));
        validateCreateUnits(before, units, output);
        return insertInspection(units.get(0), units, Set.of(packageId), cleanItems, remark, null, null, actor);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    @LogRecord(type = RdLogRecordConstants.QUALITY_TYPE, subType = RdLogRecordConstants.QUALITY_CREATE_SUB_TYPE,
            bizNo = RdLogRecordConstants.BIZ_ID_EXPRESSION, success = RdLogRecordConstants.QUALITY_CREATE_SUCCESS)
    public Long createBatch(RdQualityInspectionBatchCreateReqVO req) {
        Long actor = requireActor(null);
        if (req == null || invalidIds(req.getPackageIds()) || invalidIds(req.getSamplePackageIds())
                || req.getPackageId() == null || !req.getPackageIds().contains(req.getPackageId())
                || !req.getPackageIds().containsAll(req.getSamplePackageIds())
                || !hasText(req.getClientRequestId()) || tooLong(req.getClientRequestId(), 128)
                || tooLong(req.getRemark(), 512)) {
            throw exception(QUALITY_INSPECTION_SELECTION_INVALID);
        }
        List<RdQualityInspectionItemDO> items = new ArrayList<>();
        if (req.getItems() != null) for (RdQualityInspectionItemReqVO item : req.getItems()) {
            if (item == null) throw exception(QUALITY_INSPECTION_ITEMS_INVALID);
            items.add(new RdQualityInspectionItemDO().setSamplePackageId(item.getSamplePackageId())
                    .setItemCode(item.getItemCode()).setItemName(item.getItemName())
                    .setStandardMin(item.getStandardMin()).setStandardMax(item.getStandardMax())
                    .setMeasuredValue(item.getMeasuredValue()).setUnit(item.getUnit()).setRemark(item.getRemark()));
        }
        Set<Long> samples = new HashSet<>(req.getSamplePackageIds());
        validateItems(items, samples, false);
        String digest = requestDigest(req, items, actor);
        RdQualityInspectionDO replay = inspectionMapper.selectByClientRequestId(req.getClientRequestId());
        if (replay != null) return validateReplay(req.getClientRequestId(), digest, actor, replay);

        RdPackageUnitDO before = requiredPackage(req.getPackageId());
        RdProductionOutputDO output = lockSource(before);
        List<RdPackageUnitDO> units = lockPackages(req.getPackageIds());
        // Re-read after waiting for source/package locks, before checking states changed by an identical request.
        replay = inspectionMapper.selectByClientRequestId(req.getClientRequestId());
        if (replay != null) return validateReplay(req.getClientRequestId(), digest, actor, replay);
        validateCreateUnits(before, units, output);
        RdPackageUnitDO representative = units.stream().filter(unit -> req.getPackageId().equals(unit.getId()))
                .findFirst().orElseThrow(() -> exception(QUALITY_INSPECTION_SELECTION_INVALID));
        return insertInspection(representative, units, samples, items, req.getRemark(),
                req.getClientRequestId(), digest, actor);
    }

    private Long insertInspection(RdPackageUnitDO representative, List<RdPackageUnitDO> units, Set<Long> samples,
                                  List<RdQualityInspectionItemDO> items, String remark,
                                  String requestId, String digest, Long actor) {
        RdQualityInspectionDO inspection = new RdQualityInspectionDO()
                .setInspectionNo(inspectionNo(representative, requestId != null))
                .setPackageId(representative.getId()).setProductionOutputId(representative.getProductionOutputId())
                .setClientRequestId(requestId).setRequestDigest(digest)
                .setRegistrarId(actor).setRegistrarName(getLoginUserNickname())
                .setStatus("DRAFT").setVersion(0).setRemark(remark);
        try {
            requireWrite(inspectionMapper.insert(inspection));
        } catch (DuplicateKeyException error) {
            throw exception(QUALITY_INSPECTION_REQUEST_CONFLICT);
        }
        if (inspection.getId() == null) throw exception(QUALITY_INSPECTION_WRITE_CONFLICT);
        for (RdPackageUnitDO unit : units) {
            RdQualityInspectionMemberDO member = new RdQualityInspectionMemberDO()
                    .setInspectionId(inspection.getId()).setPackageId(unit.getId())
                    .setPackageCode(unit.getPackageCode()).setPackageWeight(unit.getPackageWeight())
                    .setSampled(samples.contains(unit.getId())).setActiveKey(unit.getId());
            try {
                requireWrite(memberMapper.insert(member));
            } catch (DuplicateKeyException error) {
                throw exception(QUALITY_INSPECTION_PACKAGE_CLAIMED);
            }
        }
        for (RdQualityInspectionItemDO item : items) {
            requireWrite(itemMapper.insert(item.setInspectionId(inspection.getId()).setQualified(isQualified(item))));
        }
        appendAudit(inspection, "CREATE", null, actor, units.size(), samples.size(), LocalDateTime.now(), null);
        LogRecordContext.putVariable(RdLogRecordConstants.BIZ_ID_VARIABLE, inspection.getId());
        return inspection.getId();
    }

    private RdProductionOutputDO lockSource(RdPackageUnitDO before) {
        if (before.getProductionOutputId() == null) return null;
        RdProductionOutputDO source = outputMapper.selectById(before.getProductionOutputId());
        if (source == null) throw exception(PRODUCTION_OUTPUT_NOT_EXISTS);
        // Production uses task -> output -> ascending packages. Closed registration tasks remain inspectable.
        if (source.getProductionTaskId() != null
                && productionTaskMapper.selectByIdForUpdate(source.getProductionTaskId()) == null) {
            throw exception(PRODUCTION_TASK_NOT_EXISTS);
        }
        RdProductionOutputDO locked = outputMapper.selectByIdForUpdate(source.getId());
        if (locked == null) throw exception(PRODUCTION_OUTPUT_NOT_EXISTS);
        if (!Objects.equals(source.getProductionTaskId(), locked.getProductionTaskId())) {
            throw exception(PRODUCTION_OUTPUT_VERSION_CONFLICT);
        }
        if (!outputEligible(locked)) throw exception(PRODUCTION_OUTPUT_STATUS_INVALID);
        return locked;
    }

    private List<RdPackageUnitDO> lockPackages(List<Long> ids) {
        List<RdPackageUnitDO> units = new ArrayList<>();
        for (Long id : ids.stream().sorted().toList()) {
            RdPackageUnitDO unit = packageMapper.selectByIdForUpdate(id);
            if (unit == null) throw exception(PACKAGE_NOT_EXISTS);
            units.add(unit);
        }
        return units;
    }

    private void validateCreateUnits(RdPackageUnitDO before, List<RdPackageUnitDO> units, RdProductionOutputDO output) {
        RdPackageUnitDO representative = units.stream().filter(unit -> before.getId().equals(unit.getId()))
                .findFirst().orElseThrow(() -> exception(QUALITY_INSPECTION_SELECTION_INVALID));
        if (!sameSource(before, representative) || before.getProductionOutputId() == null && units.size() != 1
                || output != null && !matchesOutput(representative, output)) {
            throw exception(QUALITY_INSPECTION_SOURCE_MISMATCH);
        }
        for (RdPackageUnitDO unit : units) {
            if (!sameSource(representative, unit)) throw exception(QUALITY_INSPECTION_SOURCE_MISMATCH);
            if (!packageEligible(unit)) throw exception(PACKAGE_STATUS_INVALID);
            if (!validSnapshot(unit)) {
                throw exception(QUALITY_INSPECTION_SOURCE_MISMATCH);
            }
            if (inspectionMapper.selectActiveByPackageId(unit.getId(), null) != null) {
                throw exception(QUALITY_INSPECTION_PACKAGE_CLAIMED);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    @LogRecord(type = RdLogRecordConstants.QUALITY_TYPE, subType = RdLogRecordConstants.QUALITY_SUBMIT_SUB_TYPE,
            bizNo = "{{#id}}", success = RdLogRecordConstants.QUALITY_SUBMIT_SUCCESS)
    public void submit(Long id, Long operatorId, String operatorName) {
        Long actor = requireActor(operatorId);
        RdQualityInspectionDO inspection = requiredForUpdate(id);
        if (!Set.of("DRAFT", "CORRECTED", "SUBMITTED", "RESUBMITTED", "PASSED", "REJECTED").contains(inspection.getStatus())) {
            throw exception(QUALITY_INSPECTION_STATUS_INVALID);
        }
        if (!Set.of("DRAFT", "CORRECTED").contains(inspection.getStatus())) {
            if (Set.of("SUBMITTED", "RESUBMITTED", "PASSED", "REJECTED").contains(inspection.getStatus())
                    && inspection.getSubmitTime() != null && actor.equals(inspection.getSubmitterId())) return;
            throw exception(QUALITY_INSPECTION_REQUEST_CONFLICT);
        }
        List<RdQualityInspectionMemberDO> members = lifecycleMembers(inspection);
        String beforeStatus = inspection.getStatus();
        if (!members.isEmpty()) validateItems(itemMapper.selectListByInspectionId(id), sampleIds(members), false);
        List<RdPackageUnitDO> units = lockLifecyclePackages(inspection, members, true);
        updatePackages(units, "QC_PENDING");
        inspection.setStatus("SUBMITTED").setSubmitterId(actor).setSubmitterName(getLoginUserNickname())
                .setSubmitTime(LocalDateTime.now()).setVersion(nextVersion(inspection.getVersion()));
        requireWrite(inspectionMapper.updateById(inspection));
        appendAudit(inspection, "SUBMIT", beforeStatus, actor, members.isEmpty() ? 1 : members.size(),
                members.isEmpty() ? null : sampleIds(members).size(), inspection.getSubmitTime(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    @LogRecord(type = RdLogRecordConstants.QUALITY_TYPE, subType = RdLogRecordConstants.QUALITY_APPROVE_SUB_TYPE,
            bizNo = "{{#id}}", success = RdLogRecordConstants.QUALITY_APPROVE_SUCCESS)
    public void approve(Long id, Long reviewerId, String reviewerName) {
        review(id, requireActor(reviewerId), true, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    @LogRecord(type = RdLogRecordConstants.QUALITY_TYPE, subType = RdLogRecordConstants.QUALITY_REJECT_SUB_TYPE,
            bizNo = "{{#id}}", success = RdLogRecordConstants.QUALITY_REJECT_SUCCESS)
    public void reject(Long id, Long reviewerId, String reviewerName, String reason) {
        if (!hasText(reason) || tooLong(reason, 512)) throw exception(QUALITY_INSPECTION_REJECT_REASON_REQUIRED);
        review(id, requireActor(reviewerId), false, reason);
    }

    private void review(Long id, Long actor, boolean passed, String reason) {
        RdQualityInspectionDO inspection = requiredForUpdate(id);
        String result = passed ? "PASSED" : "REJECTED";
        if (!Set.of("SUBMITTED", "RESUBMITTED").contains(inspection.getStatus())) {
            if (result.equals(inspection.getStatus()) && result.equals(inspection.getConclusion())
                    && actor.equals(inspection.getReviewerId()) && inspection.getReviewTime() != null
                    && Objects.equals(reason, inspection.getRejectReason())) return;
            if (!"PASSED".equals(inspection.getStatus()) && !"REJECTED".equals(inspection.getStatus())) {
                throw exception(QUALITY_INSPECTION_STATUS_INVALID);
            }
            throw exception(QUALITY_INSPECTION_REQUEST_CONFLICT);
        }
        if (actor.equals(inspection.getSubmitterId())) throw exception(QUALITY_INSPECTION_SELF_REVIEW);
        String beforeStatus = inspection.getStatus();
        if (inspection.getSubmitterId() == null) throw exception(QUALITY_INSPECTION_OPERATOR_INVALID);
        List<RdQualityInspectionMemberDO> members = lifecycleMembers(inspection);
        List<RdQualityInspectionItemDO> items = itemMapper.selectListByInspectionId(id);
        if (!members.isEmpty()) validateItems(items, sampleIds(members), false);
        else if (passed) validateItems(items, Set.of(inspection.getPackageId()), true);
        if (passed && (items.isEmpty() || items.stream().anyMatch(item -> !isQualified(item)))) {
            throw exception(QUALITY_INSPECTION_ITEMS_NOT_QUALIFIED);
        }
        List<RdPackageUnitDO> units = lockLifecyclePackages(inspection, members, false);
        updatePackages(units, passed ? "QC_PASSED" : "QC_REJECTED");
        inspection.setStatus(result).setConclusion(result).setRejectReason(reason)
                .setReviewerId(actor).setReviewerName(getLoginUserNickname()).setReviewTime(LocalDateTime.now())
                .setVersion(nextVersion(inspection.getVersion()));
        requireWrite(inspectionMapper.updateById(inspection));
        if (!members.isEmpty() && memberMapper.releaseActiveByInspectionId(id) != members.size()) {
            throw exception(QUALITY_INSPECTION_WRITE_CONFLICT);
        }
        appendAudit(inspection, passed ? "APPROVE" : "REJECT", beforeStatus, actor,
                members.isEmpty() ? 1 : members.size(), members.isEmpty() ? null : sampleIds(members).size(),
                inspection.getReviewTime(), reason);
    }

    private void appendAudit(RdQualityInspectionDO inspection, String action, String beforeStatus,
                             Long actor, Integer coverageCount, Integer sampleCount,
                             LocalDateTime time, String detail) {
        requireWrite(eventMapper.insert(new RdQualityInspectionEventDO().setInspectionId(inspection.getId())
                .setAction(action).setActorId(actor).setActorName(getLoginUserNickname())
                .setBeforeStatus(beforeStatus).setAfterStatus(inspection.getStatus())
                .setCoverageCount(coverageCount).setSampleCount(sampleCount).setEventTime(time)
                .setClientRequestId(inspection.getClientRequestId()).setDetail(detail)));
    }

    private List<RdQualityInspectionMemberDO> lifecycleMembers(RdQualityInspectionDO inspection) {
        List<RdQualityInspectionMemberDO> members = memberMapper.selectListByInspectionId(inspection.getId());
        if (members.isEmpty()) {
            if (inspection.getClientRequestId() != null || inspection.getRegistrarId() != null) {
                throw exception(QUALITY_INSPECTION_SELECTION_INVALID);
            }
            return members;
        }
        List<Long> ids = members.stream().map(RdQualityInspectionMemberDO::getPackageId).toList();
        if (invalidIds(ids) || !ids.contains(inspection.getPackageId()) || sampleIds(members).isEmpty()
                || members.stream().anyMatch(member -> member.getSampled() == null
                || !Objects.equals(member.getInspectionId(), inspection.getId())
                || !Objects.equals(member.getActiveKey(), member.getPackageId()))) {
            throw exception(QUALITY_INSPECTION_SELECTION_INVALID);
        }
        return members;
    }

    private List<RdPackageUnitDO> lockLifecyclePackages(RdQualityInspectionDO inspection,
                                                     List<RdQualityInspectionMemberDO> members, boolean submitting) {
        List<Long> ids = members.isEmpty() ? List.of(inspection.getPackageId())
                : members.stream().map(RdQualityInspectionMemberDO::getPackageId).toList();
        List<RdPackageUnitDO> units = lockPackages(ids);
        for (RdPackageUnitDO unit : units) {
            if (submitting ? !packageEligible(unit) : !"QC_PENDING".equals(unit.getStatus())) {
                throw exception(PACKAGE_STATUS_INVALID);
            }
            if (!members.isEmpty()) {
                RdQualityInspectionMemberDO member = members.stream()
                        .filter(value -> unit.getId().equals(value.getPackageId())).findFirst().orElseThrow();
                if (!Objects.equals(unit.getProductionOutputId(), inspection.getProductionOutputId())
                        || !sameSource(units.get(0), unit)
                        || !Objects.equals(unit.getPackageCode(), member.getPackageCode())
                        || unit.getPackageWeight() == null || member.getPackageWeight() == null
                        || unit.getPackageWeight().compareTo(member.getPackageWeight()) != 0
                        || inspection.getProductionOutputId() == null && units.size() != 1) {
                    throw exception(QUALITY_INSPECTION_SOURCE_MISMATCH);
                }
            }
            if (inspectionMapper.selectActiveByPackageId(unit.getId(), inspection.getId()) != null) {
                throw exception(QUALITY_INSPECTION_PACKAGE_CLAIMED);
            }
        }
        return units;
    }

    private void updatePackages(List<RdPackageUnitDO> units, String status) {
        for (RdPackageUnitDO unit : units) {
            unit.setStatus(status).setVersion(nextVersion(unit.getVersion()));
            requireWrite(packageMapper.updateById(unit));
        }
    }

    @Override
    public RdQualityInspectionScopeRespVO scope(RdQualityInspectionScopeReqVO req) {
        RdPackageUnitDO representative = packageMapper.selectByPackageCode(packageCode(req.getPackageCode()));
        if (representative == null) throw exception(PACKAGE_NOT_EXISTS);
        PageResult<RdPackageUnitDO> page;
        RdProductionOutputDO output = representative.getProductionOutputId() == null ? null
                : outputMapper.selectById(representative.getProductionOutputId());
        if (representative.getProductionOutputId() == null) {
            page = new PageResult<>(req.getPageNo() == 1 ? List.of(representative) : List.of(), 1L);
        } else {
            page = packageMapper.selectPage(req, new LambdaQueryWrapperX<RdPackageUnitDO>()
                    .eq(RdPackageUnitDO::getProductionOutputId, representative.getProductionOutputId())
                    .orderByAsc(RdPackageUnitDO::getId));
        }
        List<RdQualityInspectionScopeRespVO.Package> rows = new ArrayList<>();
        for (RdPackageUnitDO unit : page.getList()) {
            rows.add(scopePackage(unit, representative, output));
        }
        RdQualityInspectionScopeRespVO.Package current = rows.stream()
                .filter(row -> representative.getId().equals(row.getId())).findFirst()
                .orElseGet(() -> scopePackage(representative, representative, output));
        return new RdQualityInspectionScopeRespVO().setRepresentative(current)
                .setProductionOutputId(representative.getProductionOutputId()).setPackages(new PageResult<>(rows, page.getTotal()));
    }

    private RdQualityInspectionScopeRespVO.Package scopePackage(RdPackageUnitDO unit,
                                                               RdPackageUnitDO representative,
                                                               RdProductionOutputDO output) {
        RdQualityInspectionDO active = inspectionMapper.selectActiveByPackageId(unit.getId(), null);
        RdQualityInspectionScopeRespVO.Package row = BeanUtils.toBean(unit, RdQualityInspectionScopeRespVO.Package.class);
        row.setEligible(packageEligible(unit) && validSnapshot(unit) && sameSource(representative, unit) && active == null
                && (representative.getProductionOutputId() == null || outputEligible(output) && matchesOutput(unit, output)));
        if (active != null) row.setActiveInspectionId(active.getId()).setActiveInspectionNo(active.getInspectionNo());
        return row;
    }

    @Override
    public RdQualityInspectionDetailRespVO getDetail(Long id) {
        RdQualityInspectionDO inspection = get(id);
        RdQualityInspectionDetailRespVO detail = BeanUtils.toBean(inspection, RdQualityInspectionDetailRespVO.class);
        detail.setItems(itemMapper.selectListByInspectionId(id));
        detail.setEvents(eventMapper.selectListByInspectionId(id));
        List<RdQualityInspectionMemberDO> members = memberMapper.selectListByInspectionId(id);
        if (!members.isEmpty()) {
            detail.setMembers(BeanUtils.toBean(members, RdQualityInspectionMemberRespVO.class));
            detail.setCoverageCount(members.size()).setSampleCount(sampleIds(members).size());
        } else {
            RdPackageUnitDO unit = packageMapper.selectById(inspection.getPackageId());
            detail.setMembers(List.of(new RdQualityInspectionMemberRespVO().setPackageId(inspection.getPackageId())
                    .setPackageCode(unit == null ? null : unit.getPackageCode())
                    .setPackageWeight(unit == null ? null : unit.getPackageWeight())));
            detail.setCoverageCount(1).setSampleCount(null);
        }
        return detail;
    }

    @Override public RdQualityInspectionDO get(Long id) {
        RdQualityInspectionDO value = inspectionMapper.selectById(id);
        if (value == null) throw exception(QUALITY_INSPECTION_NOT_EXISTS);
        return value;
    }

    @Override public PageResult<RdQualityInspectionDO> page(PageParam param) {
        LambdaQueryWrapperX<RdQualityInspectionDO> query = new LambdaQueryWrapperX<>();
        if (param instanceof RdQualityInspectionPageReqVO req) {
            query.eqIfPresent(RdQualityInspectionDO::getStatus, req.getStatus())
                    .likeIfPresent(RdQualityInspectionDO::getInspectionNo, req.getInspectionNo());
        }
        return inspectionMapper.selectPage(param, query.orderByDesc(RdQualityInspectionDO::getId));
    }

    private RdPackageUnitDO requiredPackage(Long id) {
        RdPackageUnitDO unit = packageMapper.selectById(id);
        if (unit == null) throw exception(PACKAGE_NOT_EXISTS);
        return unit;
    }

    private RdQualityInspectionDO requiredForUpdate(Long id) {
        RdQualityInspectionDO value = inspectionMapper.selectByIdForUpdate(id);
        if (value == null) throw exception(QUALITY_INSPECTION_NOT_EXISTS);
        return value;
    }

    private static Long requireActor(Long suppliedId) {
        Long actor = getLoginUserId();
        if (actor == null || suppliedId != null && !actor.equals(suppliedId)) {
            throw exception(QUALITY_INSPECTION_OPERATOR_INVALID);
        }
        return actor;
    }

    private static boolean invalidIds(List<Long> ids) {
        return ids == null || ids.isEmpty() || ids.size() > 200
                || ids.stream().anyMatch(id -> id == null || id <= 0) || new HashSet<>(ids).size() != ids.size();
    }

    private static boolean packageEligible(RdPackageUnitDO unit) {
        return "LABELED".equals(unit.getStatus()) || "QC_REJECTED".equals(unit.getStatus());
    }

    private static boolean outputEligible(RdProductionOutputDO output) {
        return output != null && ("CONFIRMED".equals(output.getStatus()) || "INBOUNDED".equals(output.getStatus()));
    }

    private static boolean validSnapshot(RdPackageUnitDO unit) {
        return hasText(unit.getPackageCode()) && !tooLong(unit.getPackageCode(), 32)
                && unit.getPackageWeight() != null && unit.getPackageWeight().signum() > 0;
    }

    private static boolean matchesOutput(RdPackageUnitDO unit, RdProductionOutputDO output) {
        return Objects.equals(unit.getProductionOutputId(), output.getId())
                && Objects.equals(unit.getCustomerName(), output.getCustomerName())
                && Objects.equals(unit.getProductModel(), output.getProductModel())
                && Objects.equals(unit.getFurnaceNo(), output.getFurnaceNo())
                && Objects.equals(unit.getFurnaceLayer(), output.getFurnaceLayer());
    }

    private static boolean sameSource(RdPackageUnitDO a, RdPackageUnitDO b) {
        return Objects.equals(a.getProductionOutputId(), b.getProductionOutputId())
                && Objects.equals(a.getCustomerName(), b.getCustomerName())
                && Objects.equals(a.getProductModel(), b.getProductModel())
                && Objects.equals(a.getFurnaceNo(), b.getFurnaceNo())
                && Objects.equals(a.getFurnaceLayer(), b.getFurnaceLayer());
    }

    private static Set<Long> sampleIds(List<RdQualityInspectionMemberDO> members) {
        Set<Long> samples = new HashSet<>();
        for (RdQualityInspectionMemberDO member : members) {
            if (Boolean.TRUE.equals(member.getSampled())) samples.add(member.getPackageId());
        }
        return samples;
    }

    private static void validateItems(List<RdQualityInspectionItemDO> items, Set<Long> samples, boolean legacy) {
        if (items == null || items.isEmpty() || samples.isEmpty()) throw exception(QUALITY_INSPECTION_ITEMS_INVALID);
        Set<Long> measured = new HashSet<>();
        for (RdQualityInspectionItemDO item : items) {
            if (item == null || !hasText(item.getItemCode()) || tooLong(item.getItemCode(), 64)
                    || !hasText(item.getItemName()) || tooLong(item.getItemName(), 128)
                    || item.getMeasuredValue() == null || !validDecimal(item.getMeasuredValue())
                    || !validDecimal(item.getStandardMin()) || !validDecimal(item.getStandardMax())
                    || tooLong(item.getUnit(), 32) || tooLong(item.getRemark(), 512)
                    || item.getStandardMin() != null && item.getStandardMax() != null
                    && item.getStandardMin().compareTo(item.getStandardMax()) > 0) {
                throw exception(QUALITY_INSPECTION_ITEMS_INVALID);
            }
            Long sampleId = legacy && item.getSamplePackageId() == null ? samples.iterator().next() : item.getSamplePackageId();
            if (!samples.contains(sampleId)) throw exception(QUALITY_INSPECTION_ITEMS_INVALID);
            measured.add(sampleId);
        }
        if (!measured.equals(samples)) throw exception(QUALITY_INSPECTION_ITEMS_INVALID);
    }

    private static RdQualityInspectionItemDO copyItem(RdQualityInspectionItemDO item) {
        return new RdQualityInspectionItemDO().setItemCode(item.getItemCode()).setItemName(item.getItemName())
                .setStandardMin(item.getStandardMin()).setStandardMax(item.getStandardMax())
                .setMeasuredValue(item.getMeasuredValue()).setUnit(item.getUnit()).setRemark(item.getRemark());
    }

    private static boolean isQualified(RdQualityInspectionItemDO item) {
        return item.getMeasuredValue() != null
                && (item.getStandardMin() == null || item.getMeasuredValue().compareTo(item.getStandardMin()) >= 0)
                && (item.getStandardMax() == null || item.getMeasuredValue().compareTo(item.getStandardMax()) <= 0);
    }

    private static boolean validDecimal(BigDecimal value) {
        if (value == null) return true;
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.scale() <= 6 && normalized.precision() - normalized.scale() <= 18;
    }

    private static Long validateReplay(String requestId, String digest, Long actor, RdQualityInspectionDO replay) {
        if (!Objects.equals(requestId, replay.getClientRequestId()) || !actor.equals(replay.getRegistrarId())
                || !Objects.equals(digest, replay.getRequestDigest())) throw exception(QUALITY_INSPECTION_REQUEST_CONFLICT);
        LogRecordContext.putVariable(RdLogRecordConstants.BIZ_ID_VARIABLE, replay.getId());
        return replay.getId();
    }

    private static String requestDigest(RdQualityInspectionBatchCreateReqVO req, List<RdQualityInspectionItemDO> items, Long actor) {
        List<List<Object>> itemContent = items.stream().map(item -> Arrays.<Object>asList(item.getSamplePackageId(),
                item.getItemCode(), item.getItemName(), item.getStandardMin(), item.getStandardMax(),
                item.getMeasuredValue(), item.getUnit(), item.getRemark())).toList();
        return DigestUtil.sha256Hex(JsonUtils.toJsonString(Arrays.asList(req.getPackageId(), req.getPackageIds(),
                req.getSamplePackageIds(), itemContent, req.getRemark(), req.getClientRequestId(), actor)));
    }

    private static String packageCode(String raw) {
        String value = raw == null ? "" : raw.trim();
        try {
            if (value.startsWith("http://") || value.startsWith("https://")) {
                String path = URI.create(value).getPath();
                value = path.substring(path.lastIndexOf('/') + 1);
            } else {
                int marker = value.toLowerCase(Locale.ROOT).lastIndexOf("/pkg/");
                if (marker >= 0) value = value.substring(marker + 5);
                value = value.split("[?#]", 2)[0];
            }
        } catch (IllegalArgumentException error) {
            throw exception(PACKAGE_NOT_EXISTS);
        }
        return value.trim();
    }

    private static String inspectionNo(RdPackageUnitDO unit, boolean batch) {
        String number = "QC-" + unit.getPackageCode();
        if (batch || "QC_REJECTED".equals(unit.getStatus())) {
            number += (batch ? "-B" : "-R") + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        }
        return number;
    }

    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
    private static boolean tooLong(String value, int max) { return value != null && value.length() > max; }
    private static int nextVersion(Integer version) { return (version == null ? 0 : version) + 1; }
    private static void requireWrite(int updated) {
        if (updated != 1) throw exception(QUALITY_INSPECTION_WRITE_CONFLICT);
    }
}
