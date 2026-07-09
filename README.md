# 瑞鼎生产库存订单 ERP

_基于 `ruoyi-vue-pro` 后端与 `yudao-ui-admin-vue3` 前端二次开发，用于管理生产炉层产出、成品入库、库存台账、订单差额、调拨、出库和日报表。_

---

## 项目结构

```text
rd-production-inventory-erp/
├── backend/   # ruoyi-vue-pro 后端基线，后续新增 yudao-module-rd-inventory
├── frontend/  # yudao-ui-admin-vue3 前端基线，后续新增 src/views/rd 与 src/api/rd
└── docs/      # 架构方案与详细开发计划
```

---

## 当前阶段

当前执行《ERP生产库存订单系统详细开发计划》的第 1 步：项目初始化、开发仓库建立、源码基线导入、GitHub 私有仓库推送。

下一步是创建后端 `yudao-module-rd-inventory` 模块骨架，并创建前端 `src/views/rd`、`src/api/rd` 目录骨架。

---

## 开发约束

- 不上传业务 Excel 原始文件
- 库存变化必须写流水，不能直接改余额
- 订单完成只改状态，不删除历史记录
- 前端页面必须保持现有 ERP 后台风格
- 所有列表必须服务端分页
- 大批量导入导出必须可追溯、可重试

---

## 主要文档

- [ERP生产库存订单管理系统开发方案](docs/ERP生产库存订单管理系统开发方案.md)
- [ERP生产库存订单系统详细开发计划](docs/ERP生产库存订单系统详细开发计划.md)

