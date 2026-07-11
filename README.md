# 瑞鼎生产库存订单ERP

面向单公司的生产后库存管理系统，覆盖炉号炉层产量、成品入库、库存台账、销售订单差额、跨订单调拨、出库上线和每日汇总。

## 工程结构

```text
backend/                  Spring Boot 业务服务
  rd-erp-server/          应用启动与运行配置
  rd-erp-inventory/       瑞鼎生产库存订单业务模块
  yudao-module-system/    精简后的用户、权限与认证内核
  yudao-module-infra/     精简后的审计、文件与 Quartz 内核
frontend/                 Vue 3 + TypeScript 管理端
deploy/                   源码与运行时 Docker Compose
scripts/                  备份、验收与发布脚本
docs/                     架构、开发与运维记录
```

底层稳定框架包名暂保留 `cn.iocoder.yudao.framework`，业务与应用代码使用 `com.ruiding.erp`。系统固定使用 `tenant_id=1`，前端不提供租户管理或切换入口。

## 本地构建

后端使用 Temurin JDK 17 测试、Temurin JDK 21 打包：

```powershell
cd backend
mvn -pl rd-erp-server -am test
mvn -pl rd-erp-server -am -DskipTests package
```

前端：

```powershell
cd frontend
pnpm install
pnpm ts:check
pnpm build:prod
```

## 数据库与启动

- 全新环境：`backend/sql/mysql/rd_erp_clean.sql`
- 已有环境升级：先备份并恢复到隔离库，再执行 `backend/sql/mysql/rd_erp_upgrade_slim.sql`
- 源码 Compose：`deploy/trial/compose.yaml`
- 无源码运行时 Compose：`deploy/trial/compose.runtime.yaml`

首次启动必须通过环境变量设置 `RD_ADMIN_INITIAL_PASSWORD`，数据库基线不包含可直接使用的默认密码。

## 安全约束

凭据只能由环境变量或密钥管理系统注入。禁止把密码、短信密钥、社交客户端密钥、私钥或生产数据写入 SQL、YAML、日志和发布 ZIP。详见 [SECURITY.md](SECURITY.md)。
