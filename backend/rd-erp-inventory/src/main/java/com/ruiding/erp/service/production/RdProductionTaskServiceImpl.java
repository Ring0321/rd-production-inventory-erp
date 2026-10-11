package com.ruiding.erp.service.production;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import com.ruiding.erp.controller.admin.production.vo.*;
import com.ruiding.erp.dal.dataobject.production.RdProductionTaskDO;
import com.ruiding.erp.dal.dataobject.production.RdProductionOutputDO;
import com.ruiding.erp.dal.mysql.production.RdProductionTaskMapper;
import com.ruiding.erp.dal.mysql.production.RdProductionOutputMapper;
import com.ruiding.erp.dal.mysql.packageunit.RdPackageUnitMapper;
import com.mzt.logapi.context.LogRecordContext;
import com.mzt.logapi.starter.annotation.LogRecord;
import com.ruiding.erp.enums.RdLogRecordConstants;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.ruiding.erp.enums.ErrorCodeConstants.*;

@Service
public class RdProductionTaskServiceImpl implements RdProductionTaskService {
    @Resource private RdProductionTaskMapper taskMapper;
    @Resource private RdProductionOutputMapper outputMapper;
    @Resource private RdPackageUnitMapper packageMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = RdLogRecordConstants.PRODUCTION_TASK_TYPE, subType = "创建生产任务",
            bizNo = RdLogRecordConstants.BIZ_ID_EXPRESSION, success = "创建生产任务【{{#rdBizId}}】")
    public Long create(RdProductionTaskSaveReqVO req, Long userId, String userName) {
        RdProductionTaskDO task = new RdProductionTaskDO().setTaskNo("SC"
                + req.getProductionDate().format(DateTimeFormatter.BASIC_ISO_DATE)
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .setProductionDate(req.getProductionDate()).setCustomerName(req.getCustomerName().trim())
                .setShiftCode(req.getShiftCode().trim()).setStatus("ACTIVE").setVersion(0)
                .setManagerId(userId).setManagerName(userName).setRemark(req.getRemark());
        taskMapper.insert(task);
        LogRecordContext.putVariable(RdLogRecordConstants.BIZ_ID_VARIABLE, task.getId());
        return task.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = RdLogRecordConstants.PRODUCTION_TASK_TYPE, subType = "修改生产任务",
            bizNo = "{{#req.id}}", success = "修改生产任务【{{#req.id}}】")
    public void update(RdProductionTaskSaveReqVO req) {
        RdProductionTaskDO task = lock(req.getId(), req.getVersion());
        requireEmpty(task.getId());
        task.setProductionDate(req.getProductionDate()).setCustomerName(req.getCustomerName().trim())
                .setShiftCode(req.getShiftCode().trim()).setRemark(req.getRemark()).setVersion(task.getVersion() + 1);
        taskMapper.updateById(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = RdLogRecordConstants.PRODUCTION_TASK_TYPE, subType = "结束生产登记",
            bizNo = "{{#req.id}}", success = "结束生产任务登记【{{#req.id}}】")
    public void close(RdProductionTaskActionReqVO req) {
        RdProductionTaskDO task = lock(req.getId(), req.getVersion());
        List<RdProductionOutputDO> outputs = activeOutputs(task.getId());
        if (outputs.isEmpty() || outputs.stream().anyMatch(output ->
                !("CONFIRMED".equals(output.getStatus()) || "INBOUNDED".equals(output.getStatus()))
                        || output.getOutputQty() == null
                        || BigDecimal.valueOf(packageMapper.selectCountByProductionOutputId(output.getId()))
                            .compareTo(output.getOutputQty()) != 0)) {
            throw exception(PRODUCTION_TASK_OUTPUT_INCOMPLETE);
        }
        task.setStatus("CLOSED").setClosedTime(LocalDateTime.now()).setVersion(task.getVersion() + 1);
        taskMapper.updateById(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = RdLogRecordConstants.PRODUCTION_TASK_TYPE, subType = "作废生产任务",
            bizNo = "{{#req.id}}", success = "作废生产任务【{{#req.id}}】")
    public void cancel(RdProductionTaskActionReqVO req) {
        RdProductionTaskDO task = lock(req.getId(), req.getVersion());
        requireEmpty(task.getId());
        task.setStatus("CANCELLED").setCancelledTime(LocalDateTime.now()).setVersion(task.getVersion() + 1);
        taskMapper.updateById(task);
    }

    private RdProductionTaskDO lock(Long id, Integer version) {
        RdProductionTaskDO task = taskMapper.selectByIdForUpdate(id);
        if (task == null) throw exception(PRODUCTION_TASK_NOT_EXISTS);
        if (!Objects.equals(task.getVersion(), version)) throw exception(PRODUCTION_TASK_VERSION_CONFLICT);
        if (!"ACTIVE".equals(task.getStatus())) throw exception(PRODUCTION_TASK_NOT_ACTIVE);
        return task;
    }

    private List<RdProductionOutputDO> activeOutputs(Long taskId) {
        return outputMapper.selectListByTaskId(taskId).stream()
                .filter(output -> !"CANCELLED".equals(output.getStatus())).toList();
    }

    private void requireEmpty(Long taskId) {
        if (!activeOutputs(taskId).isEmpty()) throw exception(PRODUCTION_TASK_HAS_OUTPUT);
    }

    @Override public RdProductionTaskDO get(Long id) {
        RdProductionTaskDO task = taskMapper.selectById(id);
        if (task == null) throw exception(PRODUCTION_TASK_NOT_EXISTS);
        return task;
    }
    @Override public PageResult<RdProductionTaskDO> page(RdProductionTaskPageReqVO req) {
        return taskMapper.selectTaskPage(req);
    }
}
