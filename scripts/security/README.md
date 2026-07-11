# RD security verification

Apply the business-role migration once:

```powershell
docker cp backend/sql/mysql/rd_inventory_week10_permissions.sql `
  rd-erp-mysql:/tmp/rd_inventory_week10_permissions.sql
docker exec rd-erp-mysql mysql -uroot -prd_local_2026 rd_erp `
  -e "source /tmp/rd_inventory_week10_permissions.sql"
```

With the local backend running, execute (the endpoint may be the backend port or the local
frontend reverse proxy):

```powershell
powershell -ExecutionPolicy Bypass -File scripts/security/Test-RdPermissions.ps1
```

The test only accepts a `127.0.0.1` or `localhost` endpoint. It creates five temporary users using the local
administrator password hash, validates allowed and denied APIs, then removes the users,
role assignments, tokens, generated logs, and related Redis permission caches.

When applying the role SQL outside this test, restart the backend or evict the
`menu_role_ids`, `role`, and affected `user_role_ids` Redis caches before users log in.

To verify RD business operation logs across the inventory and order outbound flow:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/security/Test-RdOperationLogs.ps1
```

The audit test creates stock, adjusts it, creates an order, allocates stock, creates an outbound
order, and confirms outbound. It expects six successful logs across four operation types, then
removes the test stock, order, allocation, outbound, movements, summaries, and audit records.
