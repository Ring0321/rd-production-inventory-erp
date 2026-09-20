# 本地试运行基础设施

启动脚本优先使用已经就绪的 Docker Desktop；Docker 不可用时，会自动切换到项目隔离的本机运行方式：调用电脑已安装的 MySQL 程序创建独立数据目录，并使用 Microsoft Garnet 提供 Redis 兼容服务。两种方式都不修改 Windows 正在运行的 MySQL 服务。Java 后端和前端代理在 Windows 本机运行，便于持续开发和调试。

| 服务 | 地址 | 账号 |
| --- | --- | --- |
| MySQL 8.x | `127.0.0.1:3307/rd_erp` | `root`，密码由启动脚本首次生成 |
| Redis/兼容服务 | `127.0.0.1:6379` | 密码由启动脚本首次生成 |
| Java 后端 | `127.0.0.1:48080` | 仅本机访问 |
| Web 页面 | `http://127.0.0.1:9000/login` | `admin`，密码由启动脚本首次生成 |

首次启动时，MySQL 会执行 `backend/sql/mysql/rd_erp_clean.sql`，创建系统和 RD 业务的干净数据库基线。

## 一键启动

```powershell
powershell -ExecutionPolicy Bypass -File scripts/local/Start-RdErpLocal.ps1
```

首次运行会构建后端和前端，并在 `deploy/local-infra/.env.local` 生成仅本机使用的随机密码。该文件已被 Git 忽略。再次启动且源码未变化时可以使用：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/local/Start-RdErpLocal.ps1 -SkipBackendBuild -SkipFrontendBuild
```

默认参数 `-InfrastructureMode Auto` 会自动选择可用方式。需要强制指定时可使用 `-InfrastructureMode Docker` 或 `-InfrastructureMode Local`。本机后备方式首次运行会从微软已验证的 NuGet 包安装固定版本的 Garnet 到忽略目录 `temp/local-runtime/garnet`。

查看状态：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/local/Get-RdErpLocalStatus.ps1
```

停止服务：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/local/Stop-RdErpLocal.ps1
```

启动脚本会自动检查 MySQL、Redis、后端健康状态、登录接口和库存看板接口。Docker 方式的数据保存在命名卷中；本机后备方式的 MySQL 数据保存在 `%LOCALAPPDATA%/RdErp/mysql-data`，用于避开 MySQL 在中文数据目录下的启动限制。停止脚本只停止本项目进程并保留数据；日常操作禁止使用 `down -v`。
