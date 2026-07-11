# RD performance verification

These scripts only target the local Docker environment. Synthetic SQL data is written to
`rd_erp_perf`; the API concurrency test uses `PERF-*` records in local `rd_erp` and removes
them in a `finally` block.

## Excel parser

```powershell
mvn -pl rd-erp-inventory -Drd.performance.tests=true `
  -Dtest=RdInventoryExcelParserPerformanceTest test
```

The budgets are 15 seconds for 5,000 rows and 30 seconds for 20,000 rows.

## SQL plans

```powershell
docker cp scripts/performance/prepare-rd-performance-database.sql `
  rd-erp-mysql:/tmp/prepare-rd-performance-database.sql
docker cp scripts/performance/explain-rd-performance.sql `
  rd-erp-mysql:/tmp/explain-rd-performance.sql
docker exec rd-erp-mysql mysql -uroot -prd_local_2026 `
  -e "source /tmp/prepare-rd-performance-database.sql"
docker exec rd-erp-mysql mysql -uroot -prd_local_2026 `
  -e "source /tmp/explain-rd-performance.sql"
```

## Concurrent outbound

Start the local backend, then run:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/performance/Test-RdOutboundConcurrency.ps1
```

The test accepts only `127.0.0.1:48080`. It verifies concurrent order matching, two outbound
orders against one stock lot, and two simultaneous confirmations of one outbound order.
