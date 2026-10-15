package com.ruiding.erp.service.visual;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruiding.erp.dal.dataobject.stock.RdStockTransactionDO;
import com.ruiding.erp.dal.mysql.stock.RdStockTransactionMapper;
import com.ruiding.erp.service.packageunit.RdPackageUnitService;
import com.ruiding.erp.service.production.RdProductionTaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

@Service
public class RdWarehouseReplayService {
    @Resource private PermissionApi permissions;
    @Resource private RdPackageUnitService packages;
    @Resource private RdProductionTaskService tasks;
    @Resource private RdStockTransactionMapper transactions;

    public record Member(Long id, String packageCode, String status) { }
    public record Event(Long id, Long packageId, String packageCode, String transactionType, String eventTime,
                        String bizDate, Long locationId, String locationCode, BigDecimal quantity,
                        BigDecimal packageWeight, BigDecimal weight, String sourceType, String sourceNo) { }
    public record View(Long taskId, String taskNo, String customerName, List<Member> members, List<Event> events,
                       String timeZone, String timeBasis, boolean complete) { }

    @Transactional(readOnly = true)
    public View task(Long taskId) {
        Long user = SecurityFrameworkUtils.getLoginUserId();
        if (user == null || permissions.hasAnyRoles(user, "rd_forklift_driver")
                || !permissions.hasAnyPermissions(user, "rd:production-task:query")
                || !permissions.hasAnyPermissions(user, "rd:package-unit:query")
                || !permissions.hasAnyPermissions(user, "rd:stock-transaction:query")) throw exception(FORBIDDEN);
        if (taskId == null || taskId <= 0) throw new IllegalArgumentException("生产任务编号无效");
        var task = tasks.get(taskId);
        if (task == null) throw new IllegalArgumentException("生产任务不存在");
        var rows = packages.getByProductionTaskId(taskId);
        if (rows.size() > 2000 || rows.stream().anyMatch(p -> !taskId.equals(p.getProductionTaskId()))
                || rows.stream().map(p -> p.getId()).distinct().count() != rows.size())
            throw new IllegalArgumentException("任务包件范围过大或不完整，请分批核对");
        var members = rows.stream().map(p -> new Member(p.getId(), p.getPackageCode(), p.getStatus())).toList();
        Map<Long, String> codes = members.stream().collect(Collectors.toMap(Member::id, Member::packageCode));
        if (members.isEmpty()) return new View(taskId, task.getTaskNo(), task.getCustomerName(), members, List.of(), "Asia/Shanghai", "LEDGER_CREATE_TIME", true);
        var ledger = transactions.selectList(new LambdaQueryWrapper<RdStockTransactionDO>()
                .in(RdStockTransactionDO::getPackageId, codes.keySet())
                .orderByAsc(RdStockTransactionDO::getCreateTime).orderByAsc(RdStockTransactionDO::getId).last("LIMIT 5001"));
        if (ledger.size() > 5000) throw new IllegalArgumentException("流水超过5000条，请在原库存流水页按业务日期核对");
        var events = ledger.stream().map(t -> new Event(t.getId(), t.getPackageId(), codes.get(t.getPackageId()), t.getTransactionType(),
                t.getCreateTime() == null ? null : t.getCreateTime().atOffset(ZoneOffset.ofHours(8)).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX")),
                t.getBizDate() == null ? null : t.getBizDate().toString(), t.getLocationId(), t.getLocationCode(),
                t.getQuantity(), t.getPackageWeight(), t.getWeight(), t.getSourceType(), t.getSourceNo())).toList();
        return new View(taskId, task.getTaskNo(), task.getCustomerName(), members, events, "Asia/Shanghai", "LEDGER_CREATE_TIME", true);
    }
}
