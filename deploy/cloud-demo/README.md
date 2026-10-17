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

仅迁移标记 `DUOXU-DEMO-FLOW-V1` 的生产任务 90：30 包 / 29,800 kg，业务日期 2026-10-15。保留逐包、抽检成员、实际任务、入出库单、原始流水和已发布布局/标签版本，不伪造为当前日业务。

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
