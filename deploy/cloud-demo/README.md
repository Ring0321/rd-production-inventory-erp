# 垛序 ERP 独立云端演示部署

本目录只部署合成演示数据，不接入工厂。应用沿用原 Spring Boot / Java、MySQL、Redis、Vue 架构和已验证的正式构建，不启动另一套演示业务后端。

## 资源与边界

- 实例：Ubuntu 26.04，x86-64，4 vCPU，约 7.5 GiB 内存。
- 独立目录：`/srv/apps/duoxu-erp-demo`；所有权标记：`DUOXU-CLOUD-DEMO-V1`。
- 独立系统账号：`duoxu-erp`，不可交互登录。控制目录由 root 管理，应用只写自己的数据子目录和日志。
- HTTPS：443；ERP 后端：127.0.0.1:14880；独立 MySQL：127.0.0.1:13316；独立 Redis：127.0.0.1:16379；本地 OCR：127.0.0.1:48110。
- 受保护原系统：`cross-studio.service`、`mysql.service`、`nginx.service` 及原网站、原数据库目录、原 Nginx 配置。没有停止、重启、重载或迁移这些服务，没有连接原 3306 数据库。
- 不安装 Docker，不执行全局 apt install、升级、清理或防火墙改写。原系统继续占用 80，ERP 新增独立 443 服务。
- 原 Java/nginx 可执行文件只读复用；MySQL 使用独立复制的可执行文件、独立数据目录和仅匹配该文件的新 AppArmor 强制配置，原配置不变。
- 应用 slice 上限 5 GiB / 250% CPU，避免演示程序无限制占用整机资源。不是生产容量承诺。

## 演示数据

首次部署仅迁移标记 `DUOXU-DEMO-FLOW-V1` 的生产任务 90：30 包 / 29,800 kg，业务日期 2026-10-15。保留逐包、抽检成员、实际任务、入出库单、原始流水和已发布布局/标签版本，不伪造为当前日业务。

2026-10-20 追加 `DUOXU-DEMO-SPACE-V2` 合成数据：20 条产品、20 个任务、60 个产量批次、480 包 / 487,680 kg、96 个库位；上下层各 48，2 库区 × 4 排 × 6 列 × 2 层。当前合计 510 包、103 个库位，不代表全部包件均在库。原任务和原发布画布保留，新仓 `DEMO-SPACE-V2` 的层高/通道是绘图示意，不是实测或承载设计。

不上传本地整个数据库、私有运行环境、原用户密码散列、真实客户主数据或任何第三方模型密钥。清库模板的历史客户选项种子被剔除；迁移仅包含明确的合成客户。岗位账号在云端重新创建，随机密码通过 SSH 单独交付到本机受限文件，不进入 Git。

Windows `mysqldump.exe` 对中文 `--where` 参数存在编码差异。工具先通过查询取得精确 ID，再用仅含 ASCII 的 ID 条件导出，并逐表解析 INSERT 数量与查询数量核对。不能以命令退出 0 替代行数核对。SQL 清理只删除对应客户种子的 token 范围，原 MySQL `DELIMITER` 和过程体不重排。

首次部署的两个导出问题及修复包保留于验收记录，不隐去失败记录。修复器仅向完全空缺的指定演示表补行；遇到部分已有数据立即拒绝，不覆盖现有包件、任务、账号或库存。

## 文件与执行顺序

1. 只读调查服务、监听端口、资源与原系统关键文件 SHA-256，保存基线。
2. `prepare_host.py`：创建标记目录、独立用户、专属 AppArmor 和固定校验值的 Redis / uv / lego 运行工具；命名或端口冲突即停止。
3. `build_bundle.py`：从已有本地演示库只读选取白名单记录，打包原正式 JAR、前端、OCR 代码/固定模型、许可和配置模板；需要本机隔离依赖 `sqlparse==0.5.3`。
4. 通过 SSH 上传到本应用 `incoming/`，以构建产物 SHA-256 核对。密钥留在客户端，不随产物上传。
5. `install_release.py`：核对所有权、摘要、空库条件及 `@@datadir` 后创建独立库，导入系统结构与合成业务记录，生成随机凭据，启用本应用服务。
6. `finalize_accounts.py`：完成仅本应用合成账号的登录名核对、首次管理员凭据轮换；不会修改其他系统账号。
7. `prepare_ocr_native.py` / `install_ocr.py`：固定 Python 3.12.12 和直接依赖，GL 库从主机已签名的 Ubuntu 包索引按 SHA-256 核对后仅解压到本应用私有运行目录，不写全局库或包数据库。
8. lego `run` 使用用户已同意的条款、`shortlived` profile 和 TLS-ALPN-01；先完成 staging 校验，再申请正式 IP 证书。验证只使用空闲 443，不接触原 80。
9. `configure_web.py`：独立 nginx 配置和 systemd 单元，不 include 原站点文件，不 reload 原 nginx 服务。保留原公开二维码的固定租户、去除客户端身份头和参数白名单。
10. `harden_namespace.py`：收紧 root 控制目录与后台日志目录；维护文件使用原子替换，避免跟随应用可写位置中的符号链接。仅在需要切换日志路径时重启 ERP 后端。
11. `operations.py --install --backup --verify --health`：本应用备份、隔离恢复核验、定时健康检查。实际恢复只在 ERP 自有 MySQL 中创建临时库，六张表指纹一致后删除该临时库。
12. `verify_cloud.py` / `verify_ocr.py` / `verify_showcase_images.py`：外网鉴权、角色边界、配吨、追溯/护照、OCR、截图像素和二维码独立解码。
13. 原系统基线复核、凭据单独交付、更新计划与证据记录。

`--restart-empty-import` 只能用于尚未标记导入成功、包件及生产任务均为零、数据目录已核对的本应用失败初始化，不是日常升级参数。已有演示业务后不能用它重置数据库。

## HTTPS 与日常维护

- 公网入口：`https://101.42.182.117`。不要用 HTTP 访问 ERP，80 仍属于原网站。
- 正式 IP 证书采用 Let's Encrypt shortlived，首次有效期以实际证书为准；本轮到期为 2026-10-24 11:15:53 UTC。
- `duoxu-erp-certificate.timer` 每八小时检查一次，剩余不足 72 小时时办理续期。只短暂停止自己的 `duoxu-erp-web.service` 让出 443，`finally` 恢复本服务；如果 443 属于其他进程则拒绝处理。原网站不参与该流程。
- 已测试初次 staging/正式签发、有效期检查不打断网关、CA 失败恢复和其他端口占用拒绝；尚未经过真实时间到期后的整轮定时续期，不写成已发生。
- `duoxu-erp-backup.timer` 每日执行自己的数据库备份；目录 root-only，没有跨应用备份或自动清理其他目录。
- `duoxu-erp-health.timer` 每十五分钟观察自己的五个服务、后端能力接口、数据库/Redis、OCR 可用状态和证书余期，结果为本机日志，不等于已经接入外部告警平台。
- 维护单元均以 `duoxu-erp-` 命名；只管理这些单元。禁止使用不带独立配置的 `nginx -s stop/reload`、全局 Docker prune 或原 mysql 服务重启。
- 停用本演示时可停止本应用 web/backend/ocr/redis/mysql 和三个定时器；保留本应用数据与备份。不得删除原站点、原数据库或执行主机清空操作。

新系统以后使用自己的 `/srv/apps/<system>-demo`、用户、库、端口和服务命名空间。当前只有 ERP 的 HTTPS 入口，不把已有 Cross Studio 迁入本目录；未来有独立域名后再规划共享 HTTPS 分流。

## 已验证与未关闭边界

外网七角色登录、30 包 / 29.8 吨、22 笔流水、布局发布 v12、标签发布 v4、2000 kg 两包 OPTIMAL、2500 kg 零误差 INFEASIBLE、35 节点 / 65 边关系、PNG/JPEG/PDF 实际 CPU OCR、真实浏览器 3D / 标签 / 扫码页与二维码独立解码已验证。

GLM 真调用关闭；原一包一码、仓管扫码与位置维护、司机仅接任务搬运、人工确认与正式过账边界不变。公开护照不含客户/库位/人员；二维码图片不构成实物防伪。

硬件实扫、打印机、厂区网络与岗位签字、正式容量和完整商业许可审查仍属原计划未完成项。云端演示不替代现场验收，也不把企事通/智行导引方案文件记作已接入的功能。

来源：[Let's Encrypt IP certificate](https://letsencrypt.org/2026/01/15/6day-and-ip-general-availability)、[lego](https://go-acme.github.io/lego/)、[uv](https://github.com/astral-sh/uv)、[Redis](https://github.com/redis/redis)。固定发布 URL 和 SHA-256 记录在 `prepare_host.py` 及服务器自身 provenance 文件中。

## 固定 admin 演示入口增量更新（2026-10-19 已发布）

`configure_demo_login.py` 仅适用于已完成上述部署、明确标记为合成演示的实例，不是生产初始化工具。2026-10-18 因 SSH 22 连接超时未执行；2026-10-19 恢复连接后已完成增量发布与外网接口验收。登录页的公开账号为 admin，原 id=1 私有维护账号现为 erpmaintadmin，二者密码及权限相互隔离。

前端从同源 `demo-login.json` 获取专门的公开账号配置，未部署此文件时正常登录不变。不要把私有管理凭据放进前端环境变量或此公开文件。

本次发行包与维护步骤：

2026-10-18 已完成生产依赖补强，2026-10-19 再次生产依赖审计无已知漏洞；全量开发工具链告警和既有样式问题仍保留，见 `docs/acceptance/演示上线前依赖安全复核记录-20261018.md`。已上传并发布 `frontend-security-admin-entry-20261018.tar.gz`，SHA-256 为 `7d436a16e76328d4114533ec54b12c6e563cd70349b7212607a40befd6979c84`。外网入口 HTML 与此包的 index.html 摘要一致；不要使用前次旧依赖包，也不要开放本机开发工具端口。

1. 备份 ERP 自有库及原网关配置；将最新 `frontend/dist-prod` 的内容归档为前端包，计算 SHA-256。
2. 将前端归档和本目录脚本上传到 ERP 专属 `incoming/`，与客户端摘要核对。
3. 执行 `sudo python3 /srv/apps/duoxu-erp-demo/incoming/configure_demo_login.py --frontend /srv/apps/duoxu-erp-demo/incoming/<frontend.tar.gz> --sha256 <SHA256>`。不要重跑空库初始化或覆盖原完整 current 发行版。
4. 脚本通过原 RBAC API 创建独立业务演示账号与角色；原 id=1 管理员改名 `erpmaintadmin`，密码不变；新账号 admin 不含系统/基础设施维护权限。不改变原业务数据或本人/双人复核边界。
5. 网关读取 root 控制的公开配置，仅切换本应用的前端 root，并禁止公网自助改密；私有管理员仍通过原权限控制的密码重置接口管理账号。
6. 立即使用 `export_access.py` 重新导出本机受限登录资料并更新交付文件，验证新私有账号名后再继续。不要在终端/聊天打印该输出。`verify_cloud.py` 将通过更新后的文件使用私有维护账号。
7. 验证公网一键登录、业务动作、系统管理拒绝、改密路径变体拒绝、退出和刷新；回归原二维码/地图/岗位，确认原三个受保护服务及关键文件未变后记录完成。

`test_demo_login.py` 覆盖权限筛选、私有管理员保留、公开密码隔离、重复执行、空目录权限兼容、意外越权拒绝、归档摘要/路径/链接校验及网关配置失败回滚。目录节点只忽略精确空字符串，不能吞掉系统、基础设施或通配符权限。网关校验必须显式使用 `-g 'user duoxu-erp;'`，避免 root 校验把临时目录归属改为 nobody；单独 runuser 会因缺少 443 绑定能力失败。

`verify_cloud.py` 已通过七类岗位、二维码护照、追溯、布局/标签、配吨和真实 HTTPS 图片 OCR 回归；新增 `verify_public_demo.py` 已通过公开 admin 的 103 项非空业务权限、系统管理拒绝、基础设施接口未暴露、五种改密路径拦截、私有维护登录保留及线上 HTML 摘要检查。它不执行库存写入，也不把接口调用当作页面点击。原系统三项服务状态及四个文件摘要、ERP 六张业务表指纹一致。

本轮浏览器控制超时，未重新完成公网一键按钮、退出/刷新、3D 页面截图与实体相机扫描；原先浏览器记录保留为历史证据，不替代此次验收。详见 `docs/acceptance/云端增量上线验收记录-20261019.md`。

## 双层演示扩容（2026-10-20 已发布）

`seed_showcase_expansion.py` 默认只输出计划；`--apply` 只向本隔离 ERP 追加合成记录，`--verify` 只核对数据。运行前需 ERP 自有库的新备份及隔离恢复通过，脚本核对命名空间、`@@datadir`、根目录所有权和互斥锁，不能用于工厂或原系统数据库。

生产、质检提交/独立复核、上架及装车/出库按原 API 和岗位权限执行。写入白名单拒绝系统维护及直接库存更新；司机不扫码或过账。每步先写 PENDING 与请求摘要，成功记录实际 ID；不确定请求必须人工对账，不能自动重发。检查点文件 `data/SHOWCASE_EXPANSION_V2.json` 由 root 独占，禁止删除它后盲目重跑。

已完成一次追加和一次真实重跑：96 库位、480 新包、逐包 QR、状态/重量、原主键范围记录均通过核对，重跑前后六张关键业务表全字段指纹一致。新增包件中在库 352、待入库 32、已出库 24、质检合格待入库 24、待检 24、不合格 24。4 个装车中场景的包件仍在库并保持锁定，不能重复计入已出库。

当前前端 `frontend-warehouse-tiers-20261020.tar.gz`，SHA-256 `26acba42740aa3b3a4ce1c67a2176716f046ba7cd7ff331a3ef079a3335ae407`，发布根目录 `/srv/apps/duoxu-erp-demo/releases/login-26acba42740a/frontend`。仅本应用网关 HUP，原 nginx/mysql/Cross Studio 的 PID、启动时间与关键文件未变。原发行包、独立备份继续保留。

入口 `https://101.42.182.117/warehouse-space?warehouseCode=DEMO-SPACE-V2`。新增叠层、平铺及指定层筛选；仅已登记且无冲突的行列/层级叠放，其他保留原图。已发布画布不擅自重排。库位层级不从炉层或包数推断，显示不写库存。

用户授权后以独立 Edge/Playwright 会话验证了 1440×1000、390×844、320×760 的实际 WebGL 像素、旋转、上下层、点击详情、二维/三维及全屏；15 张截图和结果归档。一键登录、刷新、PDA 返回 ERP、退出重登及未登录地图跳转通过。没有浏览器业务写入或外站请求。此前 10 月 19 日的浏览器阻塞仅作为历史记录，本次已补齐网页范围，不含实体相机或硬件实扫。

复验：`node scripts/verification/Verify-CloudWarehouseTiers.mjs <bundled-node-modules>`，`verify_public_demo.py --frontend <本次归档>`，`verify_cloud.py`。原任务包件核对必须走 `task-packages?productionTaskId=90`，不能给只接受分页参数的 `/page` 附加未实现的任务筛选。503 前端、17 部署单测及七类岗位 HTTPS 回归通过。完整证据见 `docs/acceptance/云端演示扩容与双层库位验收记录-20261020.md`。
