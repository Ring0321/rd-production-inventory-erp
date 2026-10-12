# 垛序 ERP

生产与仓储协同系统。围绕批次和独立包件，连接生产、质检、库位、库存、入库、装车出库和追溯；平台品牌不绑定某一家使用企业。当前仍为单组织部署，品牌化不代表已经扩展为多组织SaaS，也不清除既有客户、货物和单据资料。

- [产品部署与品牌配置](docs/product/垛序ERP部署与品牌配置.md)
- [品牌化开发与兼容规格](docs/垛序ERP品牌化实施规格.md)

## 工程结构

```text
backend/                  Spring Boot 业务服务
  rd-erp-server/          应用启动与运行配置
  rd-erp-inventory/       生产货物、质检、库存与出入库业务模块
  yudao-module-system/    精简后的用户、权限与认证内核
  yudao-module-infra/     精简后的审计、文件与 Quartz 内核
frontend/                 Vue 3 + TypeScript 管理端
deploy/                   源码与运行时 Docker Compose
scripts/                  备份、验收与发布脚本
docs/                     架构、开发与运维记录
```

底层稳定框架包名暂保留 `cn.iocoder.yudao.framework`，业务与应用代码使用 `com.ruiding.erp`。系统固定使用 `tenant_id=1`，前端不提供租户管理或切换入口。

## 关键文档

- [全业务详细逻辑架构](docs/瑞鼎ERP全业务详细逻辑架构.md)：区分当前已实现闭环与采购、原料仓、生产领退料等下一阶段扩展。
- [既有瘦身后开发与运维基线](docs/瑞鼎ERP瘦身后开发与运维基线.md)：保留历史技术与数据口径，不作为新品牌固定公司标识。
- [岗位操作培训手册](docs/岗位操作培训手册.md)：现有生产、仓库、订单、审核和管理员岗位操作要求。

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

新包链接使用 `RD_DIGITAL_LINK_BASE_URL` 显式配置企业实际可达的HTTP(S)地址；本机默认值不是公网发布。历史包优先保留已存二维码地址。公开品牌变量在 `frontend/.env`，仅包含产品文字和本地Logo路径，不含密钥。

## 安全约束

凭据只能由环境变量或密钥管理系统注入。禁止把密码、短信密钥、社交客户端密钥、私钥或生产数据写入 SQL、YAML、日志和发布 ZIP。详见 [SECURITY.md](SECURITY.md)。
