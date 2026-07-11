# 本地试运行基础设施

该 Compose 只创建本项目专用的 MySQL 和 Redis，不修改 Windows 已安装的 MySQL 服务。

| 服务 | 地址 | 账号 |
| --- | --- | --- |
| MySQL 8.4 | `127.0.0.1:3307/rd_erp` | `root / rd_local_2026` |
| Redis 7 | `127.0.0.1:6379` | 无密码，仅本机试运行 |

首次启动时，MySQL 会依次执行：

1. `backend/sql/mysql/rd_erp_clean.sql`
2. `backend/sql/mysql/rd_inventory.sql`
3. `backend/sql/mysql/rd_inventory_week10_indexes.sql`
4. `backend/sql/mysql/rd_inventory_week10_permissions.sql`

```powershell
docker compose -f deploy/local-infra/compose.yaml up -d
docker compose -f deploy/local-infra/compose.yaml ps
```

数据保存在 Docker 命名卷中。停止服务使用 `docker compose ... stop`，不要在日常操作中使用 `down -v`。
