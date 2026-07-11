# ERP 深度瘦身验收记录

日期：2026-07-11

## 已通过

- Temurin JDK 17：`mvn -q -pl rd-erp-server -am clean test` 全量通过。
- Temurin JDK 21：`mvn -pl rd-erp-server -am -DskipTests clean package` 编译打包通过。
- 前端：`pnpm ts:check` 与 `pnpm build:prod` 通过，生产构建转换 2894 个模块。
- 前端源码：由整改前 766 个文件降至 339 个文件，减少 55.7%。
- 数据库：MySQL 8.4 干净基线初始化通过，共 43 张表、1 个固定租户、1 个管理员、5 个 RD 角色，无短信渠道和社交客户端。
- 数据库升级：幂等升级演练通过，重复执行不改变 RD 业务数据。
- 安全：Gitleaks 8.30.1 工作区扫描约 38.34 MB，未发现密钥。
- 品牌：生产前端、RD 后端、部署配置与根 README 未发现旧产品名或演示宣传文本。

## 运行验收进展

隔离环境使用 Compose 项目 `rd-erp-slim-verify`、独立数据卷和 Web 端口 9001，不接触旧 9000 环境。启动演练已发现并修复：

1. 应用错误扫描整个框架包导致处理器 Bean 重复注册。
2. Redis-only MQ 瘦身后自动配置清单仍引用 RabbitMQ 类。
3. 未使用的管理端 UI 地址被错误设为必填配置。
4. 短信功能关闭时验证码参数仍被错误设为必须外部配置。

## 待复验

本机 Docker Desktop 的 WSL 数据盘在镜像导出阶段出现 `mounting disk: input/output error`，容器引擎无法继续挂载。以下项目必须在 Docker Desktop 恢复或主机重启后执行，不能以本次编译结果替代：

- 四容器健康检查。
- 登录、认证能力和看板接口冒烟。
- 桌面与移动视口浏览器验收。
- 隔离库备份、恢复与校验。
- 运行镜像 Gitleaks 扫描。

不得删除 `rd-erp-slim-verify` 的隔离数据卷，复验成功后再清理。
