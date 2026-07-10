# RD database backup and restore

The scripts only use the project-local MySQL container by default.

Create a consistent backup with SHA-256 and JSON manifest:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/backup/Backup-RdErp.ps1
```

Restore into an isolated verification database:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/backup/Restore-RdErp.ps1 `
  -BackupFile backups/mysql/rd_erp_YYYYMMDD_HHMMSS.sql `
  -TargetDatabase rd_erp_restore_verify
```

Run the full backup/restore drill and compare ten critical tables:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/backup/Test-RdBackupRestore.ps1
```

The drill drops only its timestamped temporary restore database. Generated backup files are
kept under `backups/mysql`, which is excluded from Git. Restoring over `rd_erp` is blocked unless
`-AllowPrimaryDatabase` is explicitly supplied; stop the backend and take a second backup before
using that emergency option.
