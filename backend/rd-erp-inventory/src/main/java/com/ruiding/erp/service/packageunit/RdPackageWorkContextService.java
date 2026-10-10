package com.ruiding.erp.service.packageunit;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import com.ruiding.erp.controller.admin.packageunit.vo.RdPackageWorkContextRespVO;
import com.ruiding.erp.dal.dataobject.loading.RdLoadingTaskDO;
import com.ruiding.erp.dal.dataobject.loading.RdLoadingTaskLineDO;
import com.ruiding.erp.dal.dataobject.putaway.RdPutawayTaskDO;
import com.ruiding.erp.dal.dataobject.putaway.RdPutawayTaskLineDO;
import com.ruiding.erp.dal.mysql.loading.RdLoadingTaskLineMapper;
import com.ruiding.erp.dal.mysql.loading.RdLoadingTaskMapper;
import com.ruiding.erp.dal.mysql.packageunit.RdPackageUnitMapper;
import com.ruiding.erp.dal.mysql.putaway.RdPutawayTaskLineMapper;
import com.ruiding.erp.dal.mysql.putaway.RdPutawayTaskMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;

@Service
public class RdPackageWorkContextService {
    private static final Set<String> PUTAWAY_ACTIVE = Set.of(
            "ASSIGNED", "MOVING", "PENDING_CONFIRMATION", "EXCEPTION_PENDING");
    private static final Set<String> LOADING_ACTIVE = Set.of(
            "ASSIGNED", "LOADING", "PENDING_REVIEW", "EXCEPTION_PENDING");

    @Resource private RdPackageUnitMapper packageMapper;
    @Resource private RdPutawayTaskLineMapper putawayLineMapper;
    @Resource private RdPutawayTaskMapper putawayTaskMapper;
    @Resource private RdLoadingTaskLineMapper loadingLineMapper;
    @Resource private RdLoadingTaskMapper loadingTaskMapper;
    @Resource private PermissionApi permissionApi;

    public RdPackageWorkContextRespVO getContext(String packageCode, Long userId) {
        if (packageCode == null || packageCode.isBlank() || userId == null) {
            return null;
        }
        String code = packageCode.trim();
        if (packageMapper.selectByPackageCode(code) == null) {
            return null;
        }
        var tasks = new ArrayList<RdPackageWorkContextRespVO.Task>();
        var response = new RdPackageWorkContextRespVO().setPackageCode(code).setTasks(tasks);
        boolean putawayQuery = permissionApi.hasAnyPermissions(userId, "rd:putaway-task:query");
        boolean putawayExecute = permissionApi.hasAnyPermissions(userId, "rd:putaway-task:execute");
        if (putawayQuery || putawayExecute) {
            RdPutawayTaskLineDO line = putawayLineMapper.selectActiveByPackageCode(code);
            RdPutawayTaskDO task = line == null ? null : putawayTaskMapper.selectById(line.getTaskId());
            if (task != null && PUTAWAY_ACTIVE.contains(task.getStatus())
                    && (putawayQuery || Objects.equals(task.getForkliftDriverId(), userId))) {
                tasks.add(new RdPackageWorkContextRespVO.Task().setType("PUTAWAY")
                        .setTaskId(task.getId()).setTaskNo(task.getTaskNo()).setStatus(task.getStatus())
                        .setMyTask(Objects.equals(task.getForkliftDriverId(), userId)));
            }
        }
        boolean loadingQuery = permissionApi.hasAnyPermissions(userId, "rd:loading-task:query");
        boolean loadingExecute = permissionApi.hasAnyPermissions(userId, "rd:loading-task:execute");
        boolean pickupQuery = permissionApi.hasAnyPermissions(userId, "rd:pickup-slip:query");
        if (loadingQuery || loadingExecute || pickupQuery) {
            RdLoadingTaskLineDO line = loadingLineMapper.selectActiveByPackageCode(code);
            RdLoadingTaskDO task = line == null ? null : loadingTaskMapper.selectById(line.getTaskId());
            if (task != null && LOADING_ACTIVE.contains(task.getStatus())) {
                if (loadingQuery || (loadingExecute && Objects.equals(task.getForkliftDriverId(), userId))) {
                    tasks.add(new RdPackageWorkContextRespVO.Task().setType("LOADING")
                            .setTaskId(task.getId()).setTaskNo(task.getTaskNo()).setStatus(task.getStatus())
                            .setMyTask(Objects.equals(task.getForkliftDriverId(), userId)));
                }
                if (pickupQuery) {
                    response.setPickupSlipId(task.getPickupSlipId()).setPickupSlipNo(task.getPickupSlipNo());
                }
            }
        }
        return response;
    }
}
