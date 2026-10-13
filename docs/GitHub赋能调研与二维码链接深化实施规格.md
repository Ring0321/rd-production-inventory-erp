# GitHub赋能调研与二维码链接深化实施规格

日期2026-10-13，源码起点976cd5731431148a577df0ddf827441b52e8c58a，工作树干净。用户要求先查GitHub适用新技术，再深化二维码内嵌链接。继续垛序ERP原Spring Boot2.7/Java21、Vue/Vite、MySQL/Redis、既有Nayuki/前端二维码生成、统一追溯和AI架构；原纸码身份、仓管确认/司机搬运及GLM边界不变。

## 调研结论与采纳

下面是当日官方仓库/许可核查，按当前代码与业务需要作取舍，不用星数证明可用，不下载照搬第三方源码，不移除既有许可证。Pauli独立研究四仓库已返回并关闭，结果回收；主任务另外查了LangChain4j与RFC9264。下列为外部公开资料，不上传ERP数据。

| 项目 | 可赋能点及依据 | 许可/成熟度与本轮决定 |
| --- | --- | --- |
| GS1 Resolver CE | [官方仓库](https://github.com/gs1/GS1_DigitalLink_Resolver_CE)、[许可](https://github.com/gs1/GS1_DigitalLink_Resolver_CE/blob/master/LICENSE)：持久身份到多种资源，链接类型与语言元数据 | Apache-2.0，社区明确不由GS1维护/不保证完全符合标准；参考行为，不加Python/MongoDB服务，也不宣称当前包号是GS1标识 |
| OpenEPCIS事件哈希 | [官方仓库](https://github.com/openepcis/openepcis-event-hash-generator)、[v0.9.3](https://github.com/openepcis/openepcis-event-hash-generator/releases/tag/v0.9.3)：事件规范化后跨JSON/XML摘要 | Apache-2.0；独立核查main有JDK25要求，Java21直接复用尚未验证；借鉴可核验内容，本轮自定义公开护照摘要不是EPCIS标准哈希或签名 |
| ZXing-WASM | [官方仓库](https://github.com/Sec-ant/zxing-wasm)、[v3.1.5](https://github.com/Sec-ant/zxing-wasm/releases/tag/v3.1.5)：本地图片/视频帧多格式解码，reader-only | wrapper MIT，另有Apache-2.0/BSD-3-Clause组件；有默认CDN加载，实际接入须固定版本、自托管WASM和实机验证。列下一项扫描备选，本轮不替换已用生成器 |
| panva/jose | [官方仓库](https://github.com/panva/jose)、[许可](https://github.com/panva/jose/blob/main/LICENSE.md)：JWS/JWK浏览器校验 | MIT；真签名还需可信公钥、轮换/撤销及Java签发协议。本轮不引入Node签发服务，不把普通SHA摘要称为签名或防伪 |
| LangChain4j | [官方仓库](https://github.com/langchain4j/langchain4j)、[许可](https://github.com/langchain4j/langchain4j/blob/main/LICENSE)：Java模型、RAG与工具接入 | Apache-2.0；可作后续受控知识问答备选，未验证Boot2.7完整兼容。本轮不接自由SQL、外发附件或绕过既有GLM模型/费用和字段边界 |

直接采用的协议思路：[RFC9264](https://www.rfc-editor.org/rfc/rfc9264.html)公开链接集合，原URI作为anchor、资源独立href及固定媒体类型。这里只借标准公开行为独立实现，资源都在自有配置的服务器，不使用GS1公共解析服务；不声称GS1认证、正式法规DPP或跨XML/JSON标准事件哈希。

## 预期用户路径

二维码仍仅保存原稳定/pkg/{packageCode}地址和独立包身份。网页照旧按权限展示实际工作入口。新增公开资源目录、机器可读公开护照/JSON-LD、下载快照与本机摘要校验；用户可明确核对本机文件和服务器当前公开记录。状态变化不重新编号、重新贴码或自动过账。

公开护照只选既有公开身份/产品/重量/生产日期/受控状态和精简公开事件；不含客户、库位、生产/检验/任务ID、单据、人员、备注、测值/附件、同源明细或内部事件。即使内部页下载也必须从强制公开服务读取，不序列化当前私有trace对象。待检/未审核不能表现为合格，未知数据不补零。纯摘要只证明所比字节的一致性，不证明来源签名、物理真货或防克隆；服务器不一致可能是后来更新或文件变化，不自动判造假。

## 明确合同

1. 原build/打印继续保留已存qr_code。新地址采用URI结构校验，仅HTTP(S)、有有效主机/端口、无user-info/query/fragment或点路径，新base只用ASCII字母/数字/下划线/连字符路径段且以/pkg结尾，包号为现有ASCII64范围；总长不超128。旧原链接只读核对，不写库改码，历史点号前缀身份仍保留。
2. RdDigitalLinkService新增checkedIdentity(code,url)静态核对（可接受历史域名，但URL路径必须准确指向该包、无账户/参数/片段）及representation(code, resource)，resource只允许passport/document/links，使用当前配置的解析主机，不用不可信Host或任意next地址。
3. 新公共服务read(code)复用getTraceDetail(code,false)，验证身份/公开视图再明确白名单投影，返回Resources(Passport passport, Map document, Map linkset)。Passport字段schema/algorithm/digest/generatedAt/canonicalJson；schema=DUOXU_PUBLIC_PACKAGE_V1、algorithm=SHA-256。摘要只对canonicalJson的UTF8字节，不含每次查询时间。JSON-LD上下文内嵌固定自有urn词汇，不向外抓context。数据/目录不接受客户端选择私有视图。
4. document为@context/@type/@id/profile/packageCode/product/workflow/events。product含name/model/furnaceNo/furnaceLayer/weightKg（十进制字符串或null）/productionDate/furnaceOutDate/shift；workflow含status/qualityState/confirmedQuality，仅PASSED或REJECTED与结论一致才给confirmedQuality，待核保留null。events仅PRODUCTION/LABEL/QUALITY/STOCK的stage/eventTime/timeBasis=ERP_LOCAL_DATETIME，不带自由明细/人名/源单。
5. 原二维码地址在本地生产静态服务/部署入口按受控resource查询或Accept协商返回HTML、JSON快照、JSON-LD文档或application/linkset+json；无选择默认网页。GET/HEAD只读；非法选择/参数拒绝，媒体类型不反射任意请求值。直接公共API路径为/admin-api/rd/package-link/{code}/passport、document、links，原接口和原页面地址不移除。ETag是表示名＋内容摘要的弱校验，no-store与固定Link/nosniff/Vary。动态数据变动摘要改变，查询时刻不同不虚构数据变更。公开入口仅接受resource/workMode/internal规范查询，不接受URL登录；整个公开API前缀固定部署组织并清除Authorization/Cookie/visit-tenant-id，不改其他内部API。Nginx在安全过滤器前拒绝非法查询，网页用固定内部重定向返回原地址。
6. 页面独立公开护照区：原标签身份/当前资源地址、下载/复制、本机选文件校验和明确服务器比对。上传只留本机，不执行文件里的URL/指令；核对只按验证过的包码请求自有固定API，不自动请求外站。有限大小/明确schema/摘要/包码/公开字段校验；晚到查询或换包不覆盖当前显示。WebCrypto不可用明确无法校验，不冒称通过。

## 工作项与验收

| 项 | 结果与归属 | 前置 | 状态 | 验收证据 |
| --- | --- | --- | --- | --- |
| LINK-0 | GitHub/协议调研及选择/主任务＋支持 | 原代码/外部公开资料 | 已完成 | 四仓库独立结果与主任务RFC/JavaAI源，许可证与成熟度约束保留 |
| LINK-1 | 地址绑定/新地址验证/主任务后端 | LINK-0 | 已完成本地技术验证 | 6项URI测试；原地址/标签、实际数据不改的回归通过 |
| LINK-2 | 公开资源/护照/目录及HTTP服务/主任务实现 | LINK-1合同 | 已完成本地技术验证 | 5项服务＋4项控制器；实际JSON/LD/Linkset、304/HEAD/404及公开投影通过 |
| LINK-3 | 同URI解析/页面下载本机校验/主任务前端＋入口 | LINK-2合同 | 已完成本地技术验证 | 248前端/3入口测试，本地18与Nginx23请求，6整页＋6区块截图；掉网/重算摘要/固定后端/零上传实测通过 |
| LINK-4 | 实际整链/独立复核/总计划和Git/主任务 | LINK-1至3 | 已完成本地技术与实现归档 | 实现fc2de15/409b3f1/bf669ee已推送，远端等于bf669ee完整哈希；干净绑定ZIP1202项结构/字节/版本检查通过。原现场/正式部署门槛不关闭 |

真实HTTPS、旧域名DNS/证书及手机厂区网络是原现场门槛，不因本地解析或文件摘要划掉；设备未采购仍按用户先本地码。既有1.0.0＋Git版本策略与docs记录沿用，不加第二状态体系/第二库存库，不配置GLM钥或真调用。第三方WASM/签名与RAG需后续具体需要和审批，不以调研等同已接入。

当前下一动作：本专项LINK-0至4本地实现归档已闭环，记录提交与实现提交分开。按总计划原业务优先级继续C5商务核定后的任务变更/少装减量与补扫完善；Linux实际容器/HTTPS/设备等部署现场门槛和AI-A5继续开放。

支持与恢复：Pauli（01a133ca-b132-7292-811b-3b1be101c200）研究已回收关闭。Locke（01a133e6-6d1d-7a30-a3d4-28ed1e656c93）因模型容量在初始化失败，原生状态确认errored、两个文件均不存在，已关闭；主任务接管LINK-2，不等待或复用失败上下文，不因容量问题缩减目标。研究证据改变技术选择，上轮有实质进展；本轮直接实现而非重述方案。

Herschel（01a1343d-fc4e-7542-8ccb-d00049436e68）两次只读结果均回收、关闭。后一次发现非规范路径和查询认证可绕过组织绑定；主任务已实际复现并修复，重跑本地17/Nginx23项通过。复核本身是静态检查，不冒称独立实机测试；Docker启动/envsubst、正式HTTPS、旧DNS和厂区设备仍是明确独立门槛。

最终追加固定本地后端origin回归后18项本地请求通过。冻结实现源码bf669ee37726835479b5d851ee4d3f5042f37d27，远端实际核对一致；验证包temp/package-link-release/duoxu-erp-trial-bf669ee_20261013_172320.zip，129027686字节，SHA256=3b3c7dba11af4a39db5fc73cacc83d553b625513ce955ed6dfb92de561b4b33b。VERSION绑定该提交且dirty=false；Verify-RdPackageLinkArchive.ps1实际1202项检查通过，283份前端文件/JAR/公开指南字节与源产物一致。此处是本地验证包，不是正式现场或商用发布。
