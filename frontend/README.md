# RD ERP Web

宁夏天净隆鼎碳化硅有限公司生产货物二维码数字化管理系统管理端，基于 Vue 3、TypeScript、Vite、Element Plus 和 ECharts。

页面范围仅包括 ERP 工作台、RD 业务页面、用户/部门/岗位/角色/字典、登录日志、操作日志与个人中心。登录方式由 `/system/auth/capabilities` 动态控制，客户端固定发送 `tenant-id: 1`。

```powershell
pnpm install
pnpm ts:check
pnpm build:prod
```

生产产物输出到 `dist-prod/`。
