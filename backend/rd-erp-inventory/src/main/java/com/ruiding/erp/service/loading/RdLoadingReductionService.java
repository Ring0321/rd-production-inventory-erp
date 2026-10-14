package com.ruiding.erp.service.loading;

import com.ruiding.erp.controller.admin.loading.vo.*;
import java.util.List;

public interface RdLoadingReductionService {
    RdLoadingReductionRespVO create(RdLoadingReductionCreateReqVO req, Long actorId, String actorName);
    RdLoadingReductionRespVO review(RdLoadingReductionReviewReqVO req, Long actorId, String actorName);
    List<RdLoadingReductionRespVO> list(Long taskId, Long pickupSlipId, Long actorId);
    RdLoadingReductionRespVO get(Long id, Long taskId, String idempotencyKey, Long actorId);
}
