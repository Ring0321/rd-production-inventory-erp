package com.ruiding.erp.service.quality;

import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.dal.dataobject.quality.RdQualityInspectionDO;
import com.ruiding.erp.dal.dataobject.quality.RdQualityInspectionItemDO;
import com.ruiding.erp.dal.mysql.packageunit.RdPackageUnitMapper;
import com.ruiding.erp.dal.mysql.quality.RdQualityInspectionItemMapper;
import com.ruiding.erp.dal.mysql.quality.RdQualityInspectionMapper;
import com.ruiding.erp.enums.RdPackageStatusEnum;
import com.ruiding.erp.enums.RdQualityInspectionStatusEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RdQualityInspectionServiceImplTest {

    @Mock private RdQualityInspectionMapper inspectionMapper;
    @Mock private RdQualityInspectionItemMapper itemMapper;
    @Mock private RdPackageUnitMapper packageMapper;
    @InjectMocks private RdQualityInspectionServiceImpl service;

    @Test
    void shouldApproveSubmittedInspectionAndPassPackage() {
        RdQualityInspectionDO inspection = new RdQualityInspectionDO()
                .setId(10L).setPackageId(20L).setStatus(RdQualityInspectionStatusEnum.SUBMITTED.getStatus());
        inspection.setSubmitterId(100L);
        when(inspectionMapper.selectByIdForUpdate(10L)).thenReturn(inspection);
        when(packageMapper.selectByIdForUpdate(20L)).thenReturn(new RdPackageUnitDO()
                .setId(20L).setStatus(RdPackageStatusEnum.QC_PENDING.getStatus()));
        when(itemMapper.selectListByInspectionId(10L)).thenReturn(List.of(
                new RdQualityInspectionItemDO().setQualified(true)));

        service.approve(10L, 200L, "质检负责人");

        verify(inspectionMapper).updateById((RdQualityInspectionDO) org.mockito.ArgumentMatchers.argThat((RdQualityInspectionDO value) ->
                RdQualityInspectionStatusEnum.PASSED.getStatus().equals(value.getStatus())));
        verify(packageMapper).updateById((RdPackageUnitDO) org.mockito.ArgumentMatchers.argThat((RdPackageUnitDO value) ->
                RdPackageStatusEnum.QC_PASSED.getStatus().equals(value.getStatus())));
    }

    @Test
    void shouldRejectSelfApproval() {
        RdQualityInspectionDO inspection = new RdQualityInspectionDO()
                .setId(10L).setStatus(RdQualityInspectionStatusEnum.SUBMITTED.getStatus());
        inspection.setSubmitterId(200L);
        when(inspectionMapper.selectByIdForUpdate(10L)).thenReturn(inspection);
        assertThrows(RuntimeException.class, () -> service.approve(10L, 200L, "本人"));
    }

    @Test
    void shouldRejectApprovalWhenAnyInspectionItemIsUnqualified() {
        RdQualityInspectionDO inspection = new RdQualityInspectionDO()
                .setId(10L).setPackageId(20L).setSubmitterId(100L)
                .setStatus(RdQualityInspectionStatusEnum.SUBMITTED.getStatus());
        when(inspectionMapper.selectByIdForUpdate(10L)).thenReturn(inspection);
        when(itemMapper.selectListByInspectionId(10L)).thenReturn(List.of(
                new RdQualityInspectionItemDO().setQualified(false)));

        assertThrows(RuntimeException.class, () -> service.approve(10L, 200L, "reviewer"));
    }
}
