# RD ERP Backend

后端是 Java 17 编译的 Spring Boot 多模块应用，运行包为 `rd-erp-server.jar`。

保留模块：

- `rd-erp-server`：应用启动、部署配置与管理员密码引导。
- `rd-erp-inventory`：生产、库存、订单、调拨、出库、导入与看板。
- `yudao-module-system`：账号认证、用户、部门、岗位、角色、菜单权限、字典与日志。
- `yudao-module-infra`：审计日志、文件和 Quartz 运行内核。
- `yudao-framework`：隔离保留的稳定框架层。

```powershell
mvn -pl rd-erp-server -am test
mvn -pl rd-erp-server -am -DskipTests package
```

全新数据库只能使用 `sql/mysql/rd_erp_clean.sql`。已有数据升级必须在隔离恢复库验证 `sql/mysql/rd_erp_upgrade_slim.sql` 后执行。
