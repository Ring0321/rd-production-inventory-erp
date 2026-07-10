# Trial release package

Build backend and frontend, then create a versioned trial ZIP with deployment files, database
scripts, backup tools, verification records, and operator documents:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/release/Build-RdTrialPackage.ps1
```

Use `-SkipBuild` only when the backend JAR and `frontend/dist-prod` were built from the current
commit. Generated packages and SHA-256 files are written to `releases`, which is excluded from Git.

After extracting the ZIP, deploy with `deploy/trial/compose.runtime.yaml`. It builds small runtime
images from the packaged JAR and static frontend assets, so Maven, Node.js, and pnpm are not required
on the trial server.
