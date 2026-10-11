package com.ruiding.erp.service.quality;

import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.dal.dataobject.quality.RdQualityInspectionDO;
import com.ruiding.erp.dal.dataobject.quality.RdQualityInspectionItemDO;
import com.ruiding.erp.dal.mysql.packageunit.RdPackageUnitMapper;
import com.ruiding.erp.dal.mysql.quality.RdQualityInspectionItemMapper;
import com.ruiding.erp.dal.mysql.quality.RdQualityInspectionMapper;
import com.ruiding.erp.dal.mysql.quality.RdQualityInspectionMemberMapper;
import com.ruiding.erp.dal.mysql.quality.RdQualityInspectionEventMapper;
import com.ruiding.erp.dal.dataobject.quality.RdQualityInspectionEventDO;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.ruiding.erp.enums.RdPackageStatusEnum;
import com.ruiding.erp.enums.RdQualityInspectionStatusEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.ruiding.erp.enums.ErrorCodeConstants.*;
import cn.iocoder.yudao.framework.common.exception.ServiceException;

@ExtendWith(MockitoExtension.class)
class RdQualityInspectionServiceImplTest {

    @Mock private RdQualityInspectionMapper inspectionMapper;
    @Mock private RdQualityInspectionItemMapper itemMapper;
    @Mock private RdPackageUnitMapper packageMapper;
    @Mock private RdQualityInspectionMemberMapper memberMapper;
    @Mock private RdQualityInspectionEventMapper eventMapper;
    @InjectMocks private RdQualityInspectionServiceImpl service;

    @BeforeEach void login() {
        org.mockito.Mockito.lenient().when(eventMapper.insert(any(RdQualityInspectionEventDO.class))).thenReturn(1);
        LoginUser user = new LoginUser().setId(200L).setInfo(java.util.Map.of("nickname", "server reviewer"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null));
    }

    @AfterEach void clearLogin() { SecurityContextHolder.clearContext(); }

    @Test
    void shouldAbortReviewWhenPackageUpdateFails() {
        RdQualityInspectionDO inspection = new RdQualityInspectionDO()
                .setId(10L).setPackageId(20L).setSubmitterId(100L).setStatus("SUBMITTED");
        when(inspectionMapper.selectByIdForUpdate(10L)).thenReturn(inspection);
        when(packageMapper.selectByIdForUpdate(20L)).thenReturn(new RdPackageUnitDO()
                .setId(20L).setStatus("QC_PENDING"));
        when(itemMapper.selectListByInspectionId(10L)).thenReturn(List.of(
                new RdQualityInspectionItemDO().setItemCode("D").setItemName("Density")
                        .setMeasuredValue(BigDecimal.ONE).setQualified(true)));

        ServiceException error = assertThrows(ServiceException.class, () -> service.approve(10L, 200L, "reviewer"));
        assertEquals(QUALITY_INSPECTION_WRITE_CONFLICT.getCode(), error.getCode());
    }

    @Test
    void shouldApproveSubmittedInspectionAndPassPackage() {
        RdQualityInspectionDO inspection = new RdQualityInspectionDO()
                .setId(10L).setPackageId(20L).setStatus(RdQualityInspectionStatusEnum.SUBMITTED.getStatus());
        inspection.setSubmitterId(100L);
        when(inspectionMapper.selectByIdForUpdate(10L)).thenReturn(inspection);
        when(packageMapper.selectByIdForUpdate(20L)).thenReturn(new RdPackageUnitDO()
                .setId(20L).setStatus(RdPackageStatusEnum.QC_PENDING.getStatus()));
        when(itemMapper.selectListByInspectionId(10L)).thenReturn(List.of(
                new RdQualityInspectionItemDO().setItemCode("D").setItemName("Density")
                        .setMeasuredValue(BigDecimal.ONE).setQualified(true)));
        when(packageMapper.updateById(any(RdPackageUnitDO.class))).thenReturn(1);
        when(inspectionMapper.updateById(any(RdQualityInspectionDO.class))).thenReturn(1);

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
        ServiceException error = assertThrows(ServiceException.class, () -> service.approve(10L, 200L, "本人"));
        assertEquals(QUALITY_INSPECTION_SELF_REVIEW.getCode(), error.getCode());
    }

    @Test
    void shouldRejectApprovalWhenAnyInspectionItemIsUnqualified() {
        RdQualityInspectionDO inspection = new RdQualityInspectionDO()
                .setId(10L).setPackageId(20L).setSubmitterId(100L)
                .setStatus(RdQualityInspectionStatusEnum.SUBMITTED.getStatus());
        when(inspectionMapper.selectByIdForUpdate(10L)).thenReturn(inspection);
        when(itemMapper.selectListByInspectionId(10L)).thenReturn(List.of(
                new RdQualityInspectionItemDO().setItemCode("D").setItemName("Density")
                        .setMeasuredValue(BigDecimal.ONE).setStandardMax(BigDecimal.ZERO).setQualified(true)));

        ServiceException error = assertThrows(ServiceException.class, () -> service.approve(10L, 200L, "reviewer"));
        assertEquals(QUALITY_INSPECTION_ITEMS_NOT_QUALIFIED.getCode(), error.getCode());
    }
}
