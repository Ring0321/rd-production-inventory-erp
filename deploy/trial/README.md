# 试运行容器部署

## 从源码仓库部署

1. 安装 Docker Engine 24+ 和 Docker Compose v2。
2. 将 `deploy/trial/.env.example` 复制为 `deploy/trial/.env`。
3. 将数据库和 Redis 密码分别替换为不同的长随机密码。
4. 构建并启动服务：

```bash
docker compose --env-file deploy/trial/.env -f deploy/trial/compose.yaml build
docker compose --env-file deploy/trial/.env -f deploy/trial/compose.yaml up -d
docker compose --env-file deploy/trial/.env -f deploy/trial/compose.yaml ps
```

浏览器访问 `http://服务器地址:9000`。只对外开放前端端口，MySQL、Redis 和后端 API 留在 Compose 内网，API 由 Nginx 同源转发。

## 公开二维码入口

`RD_DIGITAL_LINK_BASE_URL` 必须使用本部署实际可达的 HTTP(S) 地址和 `/pkg` 结尾的路径。新路径段只支持字母、数字、下划线和连字符，不带账号、查询或片段；既有包件保存的地址不会被此配置改写。旧域名、HTTPS 证书和手机网络另行验证。

前端必须配置 `RD_PUBLIC_TENANT_ID` 为本独立部署的组织 ID（默认初始化为 1，不是可跨客户共用的默认路由）。容器启动先检查正整数，再只替换此 Nginx 模板变量；缺失/无效时停止启动。公开 `/pkg` 和整个 `/admin-api/rd/package-link` 前缀固定该组织，不接受登录 Cookie、Authorization、访问组织头或查询登录参数。其他内部 API 保持原认证与组织规则，不全局覆盖。

公开链接查询只允许 `resource`、`workMode`、`internal` 的规范 ASCII 参数名和值；不支持把登录凭据、任意跳转地址或编码参数名塞进二维码链接。后台登录仍使用原登录入口。`resource` 只可为 `web`、`passport`、`document`、`links`，重复或非法值拒绝。默认网页由后端选择格式后通过内部 Nginx 路径返回，浏览器原地址不变；机器请求按 `Accept` 返回对应类型，不开放库存写接口。

部署自检须同时验证匿名网页、JSON/JSON-LD/Linkset、HEAD、不同表示的 ETag、非法/重复/编码参数、非规范包路径、组织头伪造和内部接口拒绝。`Verify-RdPackageLinksNginx.mjs` 可对同一模板做本机 Nginx 1.27 验证；它不替代实际 Linux 容器启动、HTTPS、反向代理链和手机实扫。

## 从发布 ZIP 部署

发布 ZIP 已包含后端 JAR、前端静态文件和数据库初始化脚本，无需在服务器下载 Maven 或 pnpm 依赖。解压后在发布包根目录执行：

```bash
cp deploy/trial/.env.example deploy/trial/.env
# 修改 deploy/trial/.env 中的数据库和 Redis 密码
docker compose --env-file deploy/trial/.env -f deploy/trial/compose.runtime.yaml build
docker compose --env-file deploy/trial/.env -f deploy/trial/compose.runtime.yaml up -d
docker compose --env-file deploy/trial/.env -f deploy/trial/compose.runtime.yaml ps
```

`application-trial.yaml` 是可提交的最小试运行配置，只从环境变量读取数据库和 Redis 凭据，不依赖开发人员本机的 `application-local.yaml`。内网试运行默认关闭验证码；外网或正式上线前将 `RD_CAPTCHA_ENABLED` 改为 `true`，并完成验证码登录回归。

## 升级

1. 执行 `scripts/backup/Backup-RdErp.ps1` 或服务器等价的 `mysqldump --single-transaction` 备份。
2. 记录当前 Git 提交号和镜像 ID。
3. 拉取目标提交，先执行新增的幂等升级 SQL，再重新构建后端和前端镜像。
4. 使用 `docker compose up -d --build` 滚动替换应用容器。
5. 验证登录、库存看板、库存分页、订单分页和审计日志。

## 回滚

代码回滚优先切回上一个已验证 Git 提交并重新构建应用镜像。数据库只有在升级 SQL 不向后兼容时才恢复备份；恢复前停止 `backend`，再次备份当前库，并先恢复到隔离库核对 SHA-256、表数量和关键表行数。

日常停止使用 `docker compose stop`。除明确执行灾难重建外，不要使用 `docker compose down -v`，该命令会删除数据库和 Redis 命名卷。
