package com.ruiding.erp.service.quality;

import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.dal.dataobject.quality.RdQualityInspectionDO;
import com.ruiding.erp.dal.dataobject.quality.RdQualityInspectionItemDO;
import com.ruiding.erp.dal.mysql.packageunit.RdPackageUnitMapper;
import com.ruiding.erp.dal.mysql.quality.RdQualityInspectionItemMapper;
import com.ruiding.erp.dal.mysql.quality.RdQualityInspectionMapper;
import com.ruiding.erp.enums.RdPackageStatusEnum;
import com.ruiding.erp.enums.RdQualityInspectionStatusEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.ruiding.erp.enums.ErrorCodeConstants.*;

@Service
public class RdQualityInspectionServiceImpl implements RdQualityInspectionService {
    @Resource private RdQualityInspectionMapper inspectionMapper;
    @Resource private RdQualityInspectionItemMapper itemMapper;
    @Resource private RdPackageUnitMapper packageMapper;

    @Override @Transactional(rollbackFor = Exception.class)
    public Long create(Long packageId, List<RdQualityInspectionItemDO> items, String remark) {
        RdPackageUnitDO unit = packageMapper.selectById(packageId);
        if (unit == null) throw exception(PACKAGE_NOT_EXISTS);
        if (!RdPackageStatusEnum.LABELED.getStatus().equals(unit.getStatus())
                && !RdPackageStatusEnum.QC_REJECTED.getStatus().equals(unit.getStatus())) {
            throw exception(PACKAGE_STATUS_INVALID);
        }
        String inspectionNo = "QC-" + unit.getPackageCode();
        if (RdPackageStatusEnum.QC_REJECTED.getStatus().equals(unit.getStatus())) {
            inspectionNo += "-R" + System.currentTimeMillis();
        }
        RdQualityInspectionDO inspection = new RdQualityInspectionDO()
                .setInspectionNo(inspectionNo).setPackageId(packageId)
                .setStatus(RdQualityInspectionStatusEnum.DRAFT.getStatus()).setVersion(0).setRemark(remark);
        inspectionMapper.insert(inspection);
        if (items != null) items.forEach(item -> {
            item.setQualified(isQualified(item));
            itemMapper.insert(item.setInspectionId(inspection.getId()));
        });
        return inspection.getId();
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void submit(Long id, Long operatorId, String operatorName) {
        RdQualityInspectionDO inspection = requiredForUpdate(id);
        if (!RdQualityInspectionStatusEnum.DRAFT.getStatus().equals(inspection.getStatus())
                && !RdQualityInspectionStatusEnum.CORRECTED.getStatus().equals(inspection.getStatus())) {
            throw exception(QUALITY_INSPECTION_STATUS_INVALID);
        }
        RdPackageUnitDO unit = packageMapper.selectByIdForUpdate(inspection.getPackageId());
        if (unit == null) throw exception(PACKAGE_NOT_EXISTS);
        unit.setStatus(RdPackageStatusEnum.QC_PENDING.getStatus());
        packageMapper.updateById(unit);
        inspection.setStatus(RdQualityInspectionStatusEnum.SUBMITTED.getStatus())
                .setSubmitterId(operatorId).setSubmitterName(operatorName).setSubmitTime(LocalDateTime.now());
        inspectionMapper.updateById(inspection);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, Long reviewerId, String reviewerName) {
        review(id, reviewerId, reviewerName, true, null);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, Long reviewerId, String reviewerName, String reason) {
        review(id, reviewerId, reviewerName, false, reason);
    }

    private void review(Long id, Long reviewerId, String reviewerName, boolean passed, String reason) {
        RdQualityInspectionDO inspection = requiredForUpdate(id);
        if (!RdQualityInspectionStatusEnum.SUBMITTED.getStatus().equals(inspection.getStatus())
                && !RdQualityInspectionStatusEnum.RESUBMITTED.getStatus().equals(inspection.getStatus())) {
            throw exception(QUALITY_INSPECTION_STATUS_INVALID);
        }
        if (reviewerId.equals(inspection.getSubmitterId())) throw exception(QUALITY_INSPECTION_SELF_REVIEW);
        if (passed) {
            List<RdQualityInspectionItemDO> items = itemMapper.selectListByInspectionId(id);
            if (items.isEmpty() || items.stream().anyMatch(item -> !Boolean.TRUE.equals(item.getQualified()))) {
                throw exception(QUALITY_INSPECTION_ITEMS_NOT_QUALIFIED);
            }
        }
        RdPackageUnitDO unit = packageMapper.selectByIdForUpdate(inspection.getPackageId());
        if (unit == null || !RdPackageStatusEnum.QC_PENDING.getStatus().equals(unit.getStatus())) {
            throw exception(PACKAGE_STATUS_INVALID);
        }
        inspection.setStatus(passed ? RdQualityInspectionStatusEnum.PASSED.getStatus()
                        : RdQualityInspectionStatusEnum.REJECTED.getStatus())
                .setConclusion(passed ? "PASSED" : "REJECTED").setRejectReason(reason)
                .setReviewerId(reviewerId).setReviewerName(reviewerName).setReviewTime(LocalDateTime.now());
        unit.setStatus(passed ? RdPackageStatusEnum.QC_PASSED.getStatus() : RdPackageStatusEnum.QC_REJECTED.getStatus());
        inspectionMapper.updateById(inspection);
        packageMapper.updateById(unit);
    }

    private RdQualityInspectionDO requiredForUpdate(Long id) {
        RdQualityInspectionDO value = inspectionMapper.selectByIdForUpdate(id);
        if (value == null) throw exception(QUALITY_INSPECTION_NOT_EXISTS);
        return value;
    }
    private static boolean isQualified(RdQualityInspectionItemDO item) {
        if (item.getMeasuredValue() == null) {
            return false;
        }
        return (item.getStandardMin() == null || item.getMeasuredValue().compareTo(item.getStandardMin()) >= 0)
                && (item.getStandardMax() == null || item.getMeasuredValue().compareTo(item.getStandardMax()) <= 0);
    }
    @Override public RdQualityInspectionDO get(Long id) {
        RdQualityInspectionDO value = inspectionMapper.selectById(id);
        if (value == null) throw exception(QUALITY_INSPECTION_NOT_EXISTS);
        return value;
    }
    @Override public PageResult<RdQualityInspectionDO> page(PageParam pageParam) {
        return inspectionMapper.selectPage(pageParam, new LambdaQueryWrapperX<RdQualityInspectionDO>()
                .orderByDesc(RdQualityInspectionDO::getId));
    }
}
