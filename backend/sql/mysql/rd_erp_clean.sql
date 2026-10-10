
/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
DROP TABLE IF EXISTS `infra_api_access_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `infra_api_access_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志主键',
  `trace_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '链路追踪编号',
  `user_id` bigint NOT NULL DEFAULT '0' COMMENT '用户编号',
  `user_type` tinyint NOT NULL DEFAULT '0' COMMENT '用户类型',
  `application_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '应用名',
  `request_method` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '请求方法名',
  `request_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '请求地址',
  `request_params` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '请求参数',
  `response_body` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '响应结果',
  `user_ip` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户 IP',
  `user_agent` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '浏览器 UA',
  `operate_module` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作模块',
  `operate_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作名',
  `operate_type` tinyint DEFAULT '0' COMMENT '操作分类',
  `begin_time` datetime NOT NULL COMMENT '开始请求时间',
  `end_time` datetime NOT NULL COMMENT '结束请求时间',
  `duration` int NOT NULL COMMENT '执行时长',
  `result_code` int NOT NULL DEFAULT '0' COMMENT '结果码',
  `result_msg` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '结果提示',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_create_time` (`create_time`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=38073 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='API 访问日志表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `infra_api_error_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `infra_api_error_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `trace_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '链路追踪编号',
  `user_id` bigint NOT NULL DEFAULT '0' COMMENT '用户编号',
  `user_type` tinyint NOT NULL DEFAULT '0' COMMENT '用户类型',
  `application_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '应用名',
  `request_method` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '请求方法名',
  `request_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '请求地址',
  `request_params` varchar(8000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '请求参数',
  `user_ip` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户 IP',
  `user_agent` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '浏览器 UA',
  `exception_time` datetime NOT NULL COMMENT '异常发生时间',
  `exception_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '异常名',
  `exception_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '异常导致的消息',
  `exception_root_cause_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '异常导致的根消息',
  `exception_stack_trace` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '异常的栈轨迹',
  `exception_class_name` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '异常发生的类全名',
  `exception_file_name` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '异常发生的类文件',
  `exception_method_name` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '异常发生的方法名',
  `exception_line_number` int NOT NULL COMMENT '异常发生的方法所在行',
  `process_status` tinyint NOT NULL COMMENT '处理状态',
  `process_time` datetime DEFAULT NULL COMMENT '处理时间',
  `process_user_id` int DEFAULT '0' COMMENT '处理用户编号',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_create_time` (`create_time`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=24454 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统异常日志';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `infra_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `infra_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '参数主键',
  `category` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '参数分组',
  `type` tinyint NOT NULL COMMENT '参数类型',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '参数名称',
  `config_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '参数键名',
  `value` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '参数键值',
  `visible` bit(1) NOT NULL COMMENT '是否可见',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_config_key` (`config_key`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='参数配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `infra_file`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `infra_file` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '文件编号',
  `config_id` bigint DEFAULT NULL COMMENT '配置编号',
  `name` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '文件名',
  `path` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件路径',
  `url` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件 URL',
  `type` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '文件类型',
  `size` int NOT NULL COMMENT '文件大小',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=2260 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `infra_file_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `infra_file_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(63) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置名',
  `storage` tinyint NOT NULL COMMENT '存储器',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `master` bit(1) NOT NULL COMMENT '是否为主配置',
  `config` varchar(4096) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '存储配置',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=36 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件配置表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `infra_file_content`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `infra_file_content` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `config_id` bigint NOT NULL COMMENT '配置编号',
  `path` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件路径',
  `content` mediumblob NOT NULL COMMENT '文件内容',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_config_id_path` (`config_id`,`path`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=288 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `infra_job`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `infra_job` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务编号',
  `name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务名称',
  `status` tinyint NOT NULL COMMENT '任务状态',
  `handler_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '处理器的名字',
  `handler_param` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处理器的参数',
  `cron_expression` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'CRON 表达式',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '重试次数',
  `retry_interval` int NOT NULL DEFAULT '0' COMMENT '重试间隔',
  `monitor_timeout` int NOT NULL DEFAULT '0' COMMENT '监控超时时间',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=600002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='定时任务表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `infra_job_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `infra_job_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志编号',
  `job_id` bigint NOT NULL COMMENT '任务编号',
  `handler_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '处理器的名字',
  `handler_param` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处理器的参数',
  `execute_index` tinyint NOT NULL DEFAULT '1' COMMENT '第几次执行',
  `begin_time` datetime NOT NULL COMMENT '开始执行时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束执行时间',
  `duration` int DEFAULT NULL COMMENT '执行时长',
  `status` tinyint NOT NULL COMMENT '任务状态',
  `result` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '结果数据',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_job_id` (`job_id`) USING BTREE,
  KEY `idx_create_time` (`create_time`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=987 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='定时任务日志表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_dept`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_dept` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '部门id',
  `name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '部门名称',
  `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '父部门id',
  `sort` int NOT NULL DEFAULT '0' COMMENT '显示顺序',
  `leader_user_id` bigint DEFAULT NULL COMMENT '负责人',
  `phone` varchar(11) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系电话',
  `email` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '邮箱',
  `status` tinyint NOT NULL COMMENT '部门状态（0正常 1停用）',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=118 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部门表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_dict_data`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_dict_data` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '字典编码',
  `sort` int NOT NULL DEFAULT '0' COMMENT '字典排序',
  `label` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '字典标签',
  `value` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '字典键值',
  `dict_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '字典类型',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `color_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '颜色类型',
  `css_class` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT 'css 样式',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1061147 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典数据表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_dict_type`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_dict_type` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '字典主键',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '字典名称',
  `type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '字典类型',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `deleted_time` datetime DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1061100 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典类型表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_login_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_login_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '访问ID',
  `log_type` bigint NOT NULL COMMENT '日志类型',
  `trace_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '链路追踪编号',
  `user_id` bigint NOT NULL DEFAULT '0' COMMENT '用户编号',
  `user_type` tinyint NOT NULL DEFAULT '0' COMMENT '用户类型',
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '用户账号',
  `result` tinyint NOT NULL COMMENT '登陆结果',
  `user_ip` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户 IP',
  `user_agent` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '浏览器 UA',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_username` (`username`) USING BTREE,
  KEY `idx_create_time` (`create_time`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=5374 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统访问记录';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '菜单名称',
  `permission` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '权限标识',
  `type` tinyint NOT NULL COMMENT '菜单类型',
  `sort` int NOT NULL DEFAULT '0' COMMENT '显示顺序',
  `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '父菜单ID',
  `path` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '路由地址',
  `icon` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '#' COMMENT '菜单图标',
  `component` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '组件路径',
  `component_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '组件名',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '菜单状态',
  `visible` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否可见',
  `keep_alive` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否缓存',
  `always_show` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否总是显示',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=600504 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜单权限表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_oauth2_access_token`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_oauth2_access_token` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `user_id` bigint NOT NULL COMMENT '用户编号',
  `user_type` tinyint NOT NULL COMMENT '用户类型',
  `user_info` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户信息',
  `access_token` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '访问令牌',
  `refresh_token` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '刷新令牌',
  `client_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '客户端编号',
  `scopes` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '授权范围',
  `expires_time` datetime NOT NULL COMMENT '过期时间',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_access_token` (`access_token`) USING BTREE,
  KEY `idx_refresh_token` (`refresh_token`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=108494 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OAuth2 访问令牌';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_oauth2_refresh_token`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_oauth2_refresh_token` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `user_id` bigint NOT NULL COMMENT '用户编号',
  `refresh_token` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '刷新令牌',
  `user_type` tinyint NOT NULL COMMENT '用户类型',
  `client_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '客户端编号',
  `scopes` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '授权范围',
  `expires_time` datetime NOT NULL COMMENT '过期时间',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_refresh_token` (`refresh_token`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=3389 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OAuth2 刷新令牌';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_operate_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_operate_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志主键',
  `trace_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '链路追踪编号',
  `user_id` bigint NOT NULL COMMENT '用户编号',
  `user_type` tinyint NOT NULL DEFAULT '0' COMMENT '用户类型',
  `type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作模块类型',
  `sub_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作名',
  `biz_id` bigint NOT NULL COMMENT '操作数据模块编号',
  `action` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '操作内容',
  `success` bit(1) NOT NULL DEFAULT b'1' COMMENT '操作结果',
  `extra` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '拓展字段',
  `request_method` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '请求方法名',
  `request_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '请求地址',
  `user_ip` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户 IP',
  `user_agent` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '浏览器 UA',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE,
  KEY `idx_create_time` (`create_time`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=9208 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志记录 V2 版本';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_post`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_post` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '岗位ID',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '岗位编码',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '岗位名称',
  `sort` int NOT NULL COMMENT '显示顺序',
  `status` tinyint NOT NULL COMMENT '状态（0正常 1停用）',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='岗位信息表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色名称',
  `code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色权限字符串',
  `sort` int NOT NULL COMMENT '显示顺序',
  `data_scope` tinyint NOT NULL DEFAULT '1' COMMENT '数据范围（1：全部数据权限 2：自定数据权限 3：本部门数据权限 4：本部门及以下数据权限）',
  `data_scope_dept_ids` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '数据范围(指定部门数组)',
  `status` tinyint NOT NULL COMMENT '角色状态（0正常 1停用）',
  `type` tinyint NOT NULL COMMENT '角色类型',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=601006 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色信息表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_role_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_role_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增编号',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `menu_id` bigint NOT NULL COMMENT '菜单ID',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_role_id` (`role_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=6811 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色和菜单关联表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_sms_channel`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_sms_channel` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `signature` varchar(12) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '短信签名',
  `code` varchar(63) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '渠道编码',
  `status` tinyint NOT NULL COMMENT '开启状态',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `api_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '短信 API 的账号',
  `api_secret` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '短信 API 的秘钥',
  `callback_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '短信发送回调 URL',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='短信渠道';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_sms_code`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_sms_code` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `mobile` varchar(11) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '手机号',
  `code` varchar(6) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '验证码',
  `create_ip` varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '创建 IP',
  `scene` tinyint NOT NULL COMMENT '发送场景',
  `today_index` tinyint NOT NULL COMMENT '今日发送的第几条',
  `used` tinyint NOT NULL COMMENT '是否使用',
  `used_time` datetime DEFAULT NULL COMMENT '使用时间',
  `used_ip` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '使用 IP',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_mobile` (`mobile`) USING BTREE COMMENT '手机号'
) ENGINE=InnoDB AUTO_INCREMENT=694 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='手机验证码';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_sms_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_sms_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `channel_id` bigint NOT NULL COMMENT '短信渠道编号',
  `channel_code` varchar(63) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '短信渠道编码',
  `template_id` bigint NOT NULL COMMENT '模板编号',
  `template_code` varchar(63) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板编码',
  `template_type` tinyint NOT NULL COMMENT '短信类型',
  `template_content` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '短信内容',
  `template_params` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '短信参数',
  `api_template_id` varchar(63) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '短信 API 的模板编号',
  `mobile` varchar(11) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '手机号',
  `user_id` bigint DEFAULT NULL COMMENT '用户编号',
  `user_type` tinyint DEFAULT NULL COMMENT '用户类型',
  `send_status` tinyint NOT NULL DEFAULT '0' COMMENT '发送状态',
  `send_time` datetime DEFAULT NULL COMMENT '发送时间',
  `api_send_code` varchar(63) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '短信 API 发送结果的编码',
  `api_send_msg` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '短信 API 发送失败的提示',
  `api_request_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '短信 API 发送返回的唯一请求 ID',
  `api_serial_no` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '短信 API 发送返回的序号',
  `receive_status` tinyint NOT NULL DEFAULT '0' COMMENT '接收状态',
  `receive_time` datetime DEFAULT NULL COMMENT '接收时间',
  `api_receive_code` varchar(63) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'API 接收结果的编码',
  `api_receive_msg` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'API 接收结果的说明',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1568 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='短信日志';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_sms_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_sms_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `type` tinyint NOT NULL COMMENT '模板类型',
  `status` tinyint NOT NULL COMMENT '开启状态',
  `code` varchar(63) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板编码',
  `name` varchar(63) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板名称',
  `content` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板内容',
  `params` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '参数数组',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `api_template_id` varchar(63) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '短信 API 的模板编号',
  `channel_id` bigint NOT NULL COMMENT '短信渠道编号',
  `channel_code` varchar(63) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '短信渠道编码',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='短信模板';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_social_client`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_social_client` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '应用名',
  `social_type` tinyint NOT NULL COMMENT '社交平台的类型',
  `user_type` tinyint NOT NULL COMMENT '用户类型',
  `client_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '客户端编号',
  `client_secret` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '客户端密钥',
  `agent_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '代理编号',
  `public_key` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'publicKey 公钥',
  `status` tinyint NOT NULL COMMENT '状态',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=48 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='社交客户端表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_social_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_social_user` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键(自增策略)',
  `type` tinyint NOT NULL COMMENT '社交平台的类型',
  `openid` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '社交 openid',
  `token` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '社交 token',
  `raw_token_info` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '原始 Token 数据，一般是 JSON 格式',
  `nickname` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户昵称',
  `avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户头像',
  `raw_user_info` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '原始用户数据，一般是 JSON 格式',
  `code` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '最后一次的认证 code',
  `state` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最后一次的认证 state',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_type_openid` (`type`,`openid`) USING BTREE,
  KEY `idx_type_code_state` (`type`,`code`,`state`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=40 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='社交用户表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_social_user_bind`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_social_user_bind` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键(自增策略)',
  `user_id` bigint NOT NULL COMMENT '用户编号',
  `user_type` tinyint NOT NULL COMMENT '用户类型',
  `social_type` tinyint NOT NULL COMMENT '社交平台的类型',
  `social_user_id` bigint NOT NULL COMMENT '社交用户的编号',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_type_social_user_id` (`user_type`,`social_user_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=165 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='社交绑定表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_tenant`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_tenant` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '租户编号',
  `name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '租户名',
  `contact_user_id` bigint DEFAULT NULL COMMENT '联系人的用户编号',
  `contact_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '联系人',
  `contact_mobile` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系手机',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '租户状态',
  `websites` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '绑定域名数组',
  `expire_time` datetime NOT NULL COMMENT '过期时间',
  `account_count` int NOT NULL COMMENT '账号数量',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=162 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_user_post`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_user_post` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `user_id` bigint NOT NULL DEFAULT '0' COMMENT '用户ID',
  `post_id` bigint NOT NULL DEFAULT '0' COMMENT '岗位ID',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=130 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户岗位表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_user_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增编号',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=55 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户和角色关联表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_users` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户账号',
  `password` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '密码',
  `nickname` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户昵称',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `dept_id` bigint DEFAULT NULL COMMENT '部门ID',
  `post_ids` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '岗位编号数组',
  `email` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '用户邮箱',
  `mobile` varchar(11) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '手机号码',
  `sex` tinyint DEFAULT '0' COMMENT '用户性别',
  `avatar` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '头像地址',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '帐号状态（0正常 1停用）',
  `login_ip` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '最后登录IP',
  `login_date` datetime DEFAULT NULL COMMENT '最后登录时间',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_username` (`username`) USING BTREE,
  KEY `idx_mobile` (`mobile`) USING BTREE,
  KEY `idx_email` (`email`) USING BTREE,
  KEY `idx_dept_id` (`dept_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=225 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户信息表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `rd_daily_stock_summary`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rd_daily_stock_summary` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Daily summary ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT 'Tenant ID',
  `biz_date` date NOT NULL COMMENT 'Business date',
  `stock_lot_id` bigint NOT NULL COMMENT 'Stock lot ID',
  `location_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT 'Location code',
  `customer_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Customer',
  `product_model` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Product model',
  `furnace_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Production furnace number',
  `furnace_layer` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Furnace layer',
  `production_qty` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Daily production output',
  `inbound_qty` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Daily inbound',
  `outbound_qty` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Daily outbound',
  `transfer_in_qty` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Daily transfer inbound',
  `transfer_out_qty` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Daily transfer outbound',
  `return_qty` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Daily return',
  `net_change_qty` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Daily signed net change',
  `warehouse_qty` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'End warehouse quantity',
  `stock_weight` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'End stock weight',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rd_daily_summary` (`tenant_id`,`deleted`,`biz_date`,`stock_lot_id`),
  KEY `idx_rd_daily_summary_query` (`tenant_id`,`deleted`,`biz_date`,`customer_name`,`product_model`,`location_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD daily stock summary';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `rd_excel_import_error`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rd_excel_import_error` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Import error ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT 'Tenant ID',
  `task_id` bigint NOT NULL COMMENT 'Import task ID',
  `row_no` int NOT NULL COMMENT 'Row number',
  `field_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Field name',
  `raw_value` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Raw value',
  `error_message` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Error message',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_rd_import_error_task` (`tenant_id`,`task_id`,`deleted`,`row_no`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD Excel import error';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `rd_excel_import_row`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rd_excel_import_row` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Import staging row ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT 'Tenant ID',
  `task_id` bigint NOT NULL COMMENT 'Import task ID',
  `row_no` int NOT NULL COMMENT 'Excel row number',
  `location_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Location code',
  `customer_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Customer',
  `product_model` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Product model',
  `furnace_no` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Production furnace number',
  `furnace_layer` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Furnace layer',
  `source_opening_qty` decimal(24,6) DEFAULT NULL COMMENT 'Original Excel opening quantity',
  `warehouse_qty` decimal(24,6) DEFAULT NULL COMMENT 'Current warehouse quantity to import',
  `package_weight` decimal(24,6) DEFAULT NULL COMMENT 'Single package weight',
  `stock_weight` decimal(24,6) DEFAULT NULL COMMENT 'Current stock weight',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'VALID/ERROR/SKIPPED/IMPORTED',
  `error_message` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Combined row validation message',
  `stock_lot_id` bigint DEFAULT NULL COMMENT 'Created stock lot ID',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rd_import_row` (`tenant_id`,`task_id`,`row_no`,`deleted`),
  KEY `idx_rd_import_row_task_status` (`tenant_id`,`task_id`,`status`,`deleted`,`row_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD Excel import staging row';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `rd_excel_import_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rd_excel_import_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Import task ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT 'Tenant ID',
  `file_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'File name',
  `sheet_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Sheet name',
  `biz_date` date NOT NULL COMMENT 'Current stock baseline date',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PRECHECKED' COMMENT 'PRECHECKED/CONFIRMING/SUCCESS/FAILED',
  `total_rows` int NOT NULL DEFAULT '0' COMMENT 'Total rows',
  `valid_rows` int NOT NULL DEFAULT '0' COMMENT 'Valid positive-stock rows',
  `failed_rows` int NOT NULL DEFAULT '0' COMMENT 'Failed rows',
  `skipped_rows` int NOT NULL DEFAULT '0' COMMENT 'Skipped zero-stock rows',
  `imported_rows` int NOT NULL DEFAULT '0' COMMENT 'Confirmed imported rows',
  `confirm_time` datetime DEFAULT NULL COMMENT 'Confirmation time',
  `remark` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Remark',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_rd_import_task_status` (`tenant_id`,`status`,`deleted`,`create_time`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD Excel import task';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `rd_furnace_layer`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rd_furnace_layer` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Furnace layer ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT 'Tenant ID',
  `layer_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Layer code, e.g. X/Z/S/P/C',
  `layer_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Layer name',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT 'Status: 0 enabled, 1 disabled',
  `sort` int NOT NULL DEFAULT '0' COMMENT 'Sort',
  `remark` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Remark',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rd_furnace_layer_code` (`tenant_id`,`layer_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD furnace layer dictionary';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `rd_location`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rd_location` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Location ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT 'Tenant ID',
  `location_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Location code, e.g. 3-22',
  `location_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Location name',
  `warehouse_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'FINISHED' COMMENT 'Warehouse code',
  `warehouse_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Warehouse name',
  `area_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Area code',
  `area_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Area name',
  `row_code` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Map row code',
  `column_code` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Map column code',
  `level_code` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Map level code',
  `capacity_packages` decimal(24,6) DEFAULT NULL COMMENT 'Capacity in packages',
  `capacity_weight` decimal(24,6) DEFAULT NULL COMMENT 'Capacity weight in kg',
  `occupied_packages` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Posted package quantity',
  `occupied_weight` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Posted weight in kg',
  `reserved_packages` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Active putaway reserved package quantity',
  `reserved_weight` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Active putaway reserved weight in kg',
  `allowed_category` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Allowed product category',
  `frozen` bit(1) NOT NULL DEFAULT b'0' COMMENT 'Whether inbound/outbound is frozen',
  `stocktake_order_id` bigint DEFAULT NULL COMMENT 'Active stocktake order owning the freeze',
  `enabled` bit(1) NOT NULL DEFAULT b'1' COMMENT 'Whether location is enabled',
  `location_status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'AVAILABLE' COMMENT 'AVAILABLE/RESTRICTED/MAINTENANCE',
  `version` int NOT NULL DEFAULT '0' COMMENT 'Optimistic lock version',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT 'Status: 0 enabled, 1 disabled',
  `sort` int NOT NULL DEFAULT '0' COMMENT 'Sort',
  `remark` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Remark',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rd_location_code` (`tenant_id`,`location_code`,`deleted`),
  KEY `idx_rd_location_map` (`tenant_id`,`warehouse_code`,`area_code`,`row_code`,`column_code`,`level_code`,`deleted`),
  KEY `idx_rd_location_availability` (`tenant_id`,`location_status`,`enabled`,`frozen`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD warehouse location';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `rd_product_master`;
CREATE TABLE `rd_product_master` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1',
  `product_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL, `product_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `customer_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL, `product_model` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `category` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL, `unit` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '包',
  `default_package_weight` decimal(24,6) NOT NULL, `quality_standard` text COLLATE utf8mb4_unicode_ci,
  `enabled` bit(1) NOT NULL DEFAULT b'1', `remark` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rd_product_master_code` (`tenant_id`,`product_code`,`deleted`),
  KEY `idx_rd_product_master_option` (`tenant_id`,`enabled`,`deleted`,`customer_name`,`product_model`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD product master';

DROP TABLE IF EXISTS `rd_package_unit`;
DROP TABLE IF EXISTS `rd_package_code_sequence`;
CREATE TABLE `rd_package_code_sequence` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '1',
  `biz_date` date NOT NULL,
  `current_value` int NOT NULL DEFAULT '0',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rd_package_sequence_date` (`tenant_id`,`biz_date`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD package daily code sequence';

CREATE TABLE `rd_package_unit` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1',
  `package_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL, `qr_code` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `production_output_id` bigint NOT NULL, `production_date` date NOT NULL, `furnace_out_date` date DEFAULT NULL,
  `shift_code` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL, `customer_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `product_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL, `product_model` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `furnace_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL, `furnace_layer` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `package_weight` decimal(24,6) NOT NULL, `location_id` bigint DEFAULT NULL, `location_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `stock_lot_id` bigint DEFAULT NULL, `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL, `version` int NOT NULL DEFAULT 0,
  `remark` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL, `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rd_package_code` (`tenant_id`,`package_code`,`deleted`), UNIQUE KEY `uk_rd_package_qr` (`tenant_id`,`qr_code`,`deleted`),
  KEY `idx_rd_package_production` (`tenant_id`,`production_output_id`,`deleted`,`status`),
  KEY `idx_rd_package_stock` (`tenant_id`,`stock_lot_id`,`location_code`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD package unit';

DROP TABLE IF EXISTS `rd_quality_inspection`;
CREATE TABLE `rd_quality_inspection` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1', `inspection_no` varchar(64) NOT NULL,
  `package_id` bigint NOT NULL, `status` varchar(32) NOT NULL, `conclusion` varchar(32) DEFAULT NULL, `reject_reason` varchar(512) DEFAULT NULL,
  `submitter_id` bigint DEFAULT NULL, `submitter_name` varchar(64) DEFAULT NULL, `submit_time` datetime DEFAULT NULL,
  `reviewer_id` bigint DEFAULT NULL, `reviewer_name` varchar(64) DEFAULT NULL, `review_time` datetime DEFAULT NULL,
  `version` int NOT NULL DEFAULT 0, `remark` varchar(512) DEFAULT NULL, `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rd_quality_no` (`tenant_id`,`inspection_no`,`deleted`), KEY `idx_rd_quality_package` (`tenant_id`,`package_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD quality inspection';

DROP TABLE IF EXISTS `rd_quality_inspection_item`;
CREATE TABLE `rd_quality_inspection_item` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1', `inspection_id` bigint NOT NULL,
  `item_code` varchar(64) NOT NULL, `item_name` varchar(128) NOT NULL, `standard_min` decimal(24,6) DEFAULT NULL,
  `standard_max` decimal(24,6) DEFAULT NULL, `measured_value` decimal(24,6) DEFAULT NULL, `unit` varchar(32) DEFAULT NULL,
  `qualified` bit(1) DEFAULT NULL, `remark` varchar(512) DEFAULT NULL, `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`), KEY `idx_rd_quality_item` (`tenant_id`,`inspection_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD quality inspection item';

DROP TABLE IF EXISTS `rd_inbound_order`;
CREATE TABLE `rd_inbound_order` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1', `order_no` varchar(64) NOT NULL, `biz_date` date NOT NULL,
  `source_type` varchar(32) DEFAULT NULL, `production_output_id` bigint DEFAULT NULL, `total_packages` decimal(24,6) NOT NULL DEFAULT 0,
  `total_weight` decimal(24,6) NOT NULL DEFAULT 0, `status` varchar(32) NOT NULL, `submitter_id` bigint DEFAULT NULL,
  `submitter_name` varchar(64) DEFAULT NULL, `submit_time` datetime DEFAULT NULL, `reviewer_id` bigint DEFAULT NULL,
  `reviewer_name` varchar(64) DEFAULT NULL, `review_time` datetime DEFAULT NULL, `poster_id` bigint DEFAULT NULL,
  `poster_name` varchar(64) DEFAULT NULL, `post_time` datetime DEFAULT NULL, `reverser_id` bigint DEFAULT NULL,
  `reverser_name` varchar(64) DEFAULT NULL, `reverse_time` datetime DEFAULT NULL, `reverse_reason` varchar(512) DEFAULT NULL,
  `version` int NOT NULL DEFAULT 0, `remark` varchar(512) DEFAULT NULL, `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`), UNIQUE KEY `uk_rd_inbound_no` (`tenant_id`,`order_no`,`deleted`),
  KEY `idx_rd_inbound_status_date` (`tenant_id`,`status`,`biz_date`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD inbound order';

DROP TABLE IF EXISTS `rd_inbound_order_line`;
CREATE TABLE `rd_inbound_order_line` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1', `order_id` bigint NOT NULL, `package_id` bigint NOT NULL,
  `location_id` bigint DEFAULT NULL, `location_code` varchar(64) NOT NULL, `quantity` decimal(24,6) NOT NULL DEFAULT 1,
  `weight` decimal(24,6) NOT NULL, `stock_lot_id` bigint DEFAULT NULL, `stock_transaction_id` bigint DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL, `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`), UNIQUE KEY `uk_rd_inbound_package` (`tenant_id`,`package_id`,`deleted`),
  KEY `idx_rd_inbound_line_order` (`tenant_id`,`order_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD inbound order line';

DROP TABLE IF EXISTS `rd_package_task_event`;
CREATE TABLE `rd_package_task_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT 1,
  `package_id` bigint NOT NULL,
  `package_code` varchar(32) NOT NULL,
  `stage` varchar(24) NOT NULL,
  `event_code` varchar(40) NOT NULL,
  `source_type` varchar(40) NOT NULL,
  `source_id` bigint NOT NULL,
  `source_no` varchar(64) NOT NULL,
  `source_version` int NOT NULL,
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT NULL,
  `event_time` datetime(6) NOT NULL,
  `location_code` varchar(64) DEFAULT NULL,
  `previous_location_code` varchar(64) DEFAULT NULL,
  `related_package_code` varchar(32) DEFAULT NULL,
  `detail` text,
  `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_package_task_event_source` (`tenant_id`,`source_type`,`source_id`,`source_version`,`event_code`,`package_id`),
  KEY `idx_package_task_event_time` (`tenant_id`,`package_id`,`event_time`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Append-only package task history';

DROP TABLE IF EXISTS `rd_label_print_log`;
CREATE TABLE `rd_label_print_log` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1', `package_id` bigint NOT NULL,
  `template_code` varchar(64) NOT NULL, `print_sequence` int NOT NULL, `reprint` bit(1) NOT NULL DEFAULT b'0',
  `reprint_reason` varchar(512) DEFAULT NULL, `printer_id` bigint DEFAULT NULL, `printer_name` varchar(64) DEFAULT NULL,
  `print_time` datetime NOT NULL, `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`), KEY `idx_rd_label_package` (`tenant_id`,`package_id`,`deleted`,`print_sequence`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD label print log';

DROP TABLE IF EXISTS `rd_scan_event`;
CREATE TABLE `rd_scan_event` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1', `biz_type` varchar(32) NOT NULL,
  `biz_id` bigint DEFAULT NULL, `package_id` bigint DEFAULT NULL, `package_code` varchar(32) NOT NULL, `device_code` varchar(64) DEFAULT NULL,
  `operator_id` bigint DEFAULT NULL, `operator_name` varchar(64) DEFAULT NULL, `success` bit(1) NOT NULL, `error_message` varchar(512) DEFAULT NULL,
  `scan_time` datetime NOT NULL, `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`), KEY `idx_rd_scan_package_time` (`tenant_id`,`package_code`,`scan_time`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD scan event';

DROP TABLE IF EXISTS `rd_putaway_exception`;
DROP TABLE IF EXISTS `rd_putaway_batch_scan_record`;
DROP TABLE IF EXISTS `rd_putaway_scan_record`;
DROP TABLE IF EXISTS `rd_putaway_task_line`;
DROP TABLE IF EXISTS `rd_putaway_task`;
CREATE TABLE `rd_putaway_task` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1',
  `task_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL, `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING_PUTAWAY',
  `planned_location_id` bigint DEFAULT NULL, `planned_location_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `final_location_id` bigint DEFAULT NULL, `final_location_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `package_count` int NOT NULL DEFAULT '0', `scanned_package_count` int NOT NULL DEFAULT '0',
  `exception_package_count` int NOT NULL DEFAULT '0', `total_weight` decimal(24,6) NOT NULL DEFAULT '0.000000',
  `warehouse_keeper_id` bigint DEFAULT NULL, `warehouse_keeper_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `forklift_driver_id` bigint DEFAULT NULL, `forklift_driver_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `assigned_time` datetime DEFAULT NULL, `start_time` datetime DEFAULT NULL, `scan_complete_time` datetime DEFAULT NULL,
  `confirm_time` datetime DEFAULT NULL, `cancel_time` datetime DEFAULT NULL,
  `cancel_reason` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL, `version` int NOT NULL DEFAULT '0',
  `remark` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL, `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rd_putaway_task_no` (`tenant_id`,`task_no`,`deleted`),
  KEY `idx_rd_putaway_task_status` (`tenant_id`,`status`,`deleted`,`create_time`,`id`),
  KEY `idx_rd_putaway_task_driver` (`tenant_id`,`forklift_driver_id`,`status`,`deleted`,`id`),
  KEY `idx_rd_putaway_task_keeper` (`tenant_id`,`warehouse_keeper_id`,`status`,`deleted`,`id`),
  KEY `idx_rd_putaway_task_plan_location` (`tenant_id`,`planned_location_id`,`status`,`deleted`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD putaway task';

CREATE TABLE `rd_putaway_task_line` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1', `task_id` bigint NOT NULL,
  `line_no` int NOT NULL, `package_id` bigint NOT NULL, `package_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `line_status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `customer_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL, `product_model` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `furnace_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL, `furnace_layer` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `package_weight` decimal(24,6) NOT NULL, `scan_count` int NOT NULL DEFAULT '0',
  `first_scan_time` datetime DEFAULT NULL, `last_scan_time` datetime DEFAULT NULL,
  `inbound_order_id` bigint DEFAULT NULL, `inbound_order_line_id` bigint DEFAULT NULL,
  `stock_lot_id` bigint DEFAULT NULL, `stock_transaction_id` bigint DEFAULT NULL, `version` int NOT NULL DEFAULT '0',
  `remark` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL, `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rd_putaway_line_no` (`tenant_id`,`task_id`,`line_no`,`deleted`),
  UNIQUE KEY `uk_rd_putaway_line_package` (`tenant_id`,`task_id`,`package_id`,`deleted`),
  KEY `idx_rd_putaway_line_task` (`tenant_id`,`task_id`,`line_status`,`deleted`,`line_no`),
  KEY `idx_rd_putaway_line_package` (`tenant_id`,`package_id`,`line_status`,`deleted`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD putaway task package line';

CREATE TABLE `rd_putaway_scan_record` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1', `task_id` bigint NOT NULL,
  `task_line_id` bigint DEFAULT NULL, `package_id` bigint DEFAULT NULL,
  `package_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `idempotency_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `scan_result` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL, `success` bit(1) NOT NULL,
  `error_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `error_message` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `task_status_before` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `task_status_after` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `device_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `operator_id` bigint DEFAULT NULL, `operator_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `device_time` datetime DEFAULT NULL, `scan_time` datetime NOT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rd_putaway_scan_idempotency` (`tenant_id`,`task_id`,`idempotency_key`,`deleted`),
  KEY `idx_rd_putaway_scan_task` (`tenant_id`,`task_id`,`scan_time`,`deleted`,`id`),
  KEY `idx_rd_putaway_scan_package` (`tenant_id`,`package_code`,`scan_time`,`deleted`,`id`),
  KEY `idx_rd_putaway_scan_operator` (`tenant_id`,`operator_id`,`scan_time`,`deleted`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD putaway scan record';

CREATE TABLE `rd_putaway_batch_scan_record` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1', `task_id` bigint NOT NULL,
  `representative_package_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `idempotency_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_digest` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL, `task_version` int NOT NULL,
  `confirmed_line_ids` text COLLATE utf8mb4_unicode_ci NOT NULL, `confirmed_count` int NOT NULL,
  `operator_id` bigint NOT NULL, `operator_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `device_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL, `scan_time` datetime NOT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rd_putaway_batch_key` (`tenant_id`,`task_id`,`idempotency_key`,`deleted`),
  KEY `idx_rd_putaway_batch_task` (`tenant_id`,`task_id`,`scan_time`,`deleted`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD warehouse batch putaway verification';

CREATE TABLE `rd_putaway_exception` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1',
  `exception_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL, `task_id` bigint NOT NULL,
  `task_line_id` bigint DEFAULT NULL, `package_id` bigint DEFAULT NULL,
  `package_code` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `exception_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `exception_status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OPEN',
  `description` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `attachment_refs` text COLLATE utf8mb4_unicode_ci,
  `reporter_id` bigint DEFAULT NULL, `reporter_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reporter_role` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL, `reported_time` datetime NOT NULL,
  `handler_id` bigint DEFAULT NULL, `handler_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `handle_time` datetime DEFAULT NULL, `handling_result` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `resolution_action` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `previous_location_id` bigint DEFAULT NULL, `previous_location_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `resolved_location_id` bigint DEFAULT NULL, `resolved_location_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0', `remark` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rd_putaway_exception_no` (`tenant_id`,`exception_no`,`deleted`),
  KEY `idx_rd_putaway_exception_task` (`tenant_id`,`task_id`,`exception_status`,`deleted`,`reported_time`,`id`),
  KEY `idx_rd_putaway_exception_handler` (`tenant_id`,`handler_id`,`exception_status`,`deleted`,`id`),
  KEY `idx_rd_putaway_exception_package` (`tenant_id`,`package_id`,`deleted`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD putaway exception';

DROP TABLE IF EXISTS `rd_stocktake_order_line`;
DROP TABLE IF EXISTS `rd_stocktake_order`;
CREATE TABLE `rd_stocktake_order` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1',
  `stocktake_no` varchar(64) NOT NULL, `stocktake_date` date NOT NULL, `location_id` bigint NOT NULL,
  `location_code` varchar(64) NOT NULL, `status` varchar(32) NOT NULL, `version` int NOT NULL DEFAULT 0,
  `snapshot_time` datetime DEFAULT NULL, `book_package_qty` decimal(24,6) NOT NULL DEFAULT 0,
  `counted_package_qty` decimal(24,6) NOT NULL DEFAULT 0, `shortage_qty` decimal(24,6) NOT NULL DEFAULT 0,
  `surplus_qty` decimal(24,6) NOT NULL DEFAULT 0, `book_weight` decimal(24,6) NOT NULL DEFAULT 0,
  `counted_weight` decimal(24,6) NOT NULL DEFAULT 0, `business_creator_id` bigint DEFAULT NULL,
  `business_creator_name` varchar(64) DEFAULT NULL, `submitter_id` bigint DEFAULT NULL,
  `submitter_name` varchar(64) DEFAULT NULL, `submit_time` datetime DEFAULT NULL, `reviewer_id` bigint DEFAULT NULL,
  `reviewer_name` varchar(64) DEFAULT NULL, `review_time` datetime DEFAULT NULL, `adjuster_id` bigint DEFAULT NULL,
  `adjuster_name` varchar(64) DEFAULT NULL, `adjust_time` datetime DEFAULT NULL, `closer_id` bigint DEFAULT NULL,
  `closer_name` varchar(64) DEFAULT NULL, `close_time` datetime DEFAULT NULL, `canceller_id` bigint DEFAULT NULL,
  `canceller_name` varchar(64) DEFAULT NULL, `cancel_time` datetime DEFAULT NULL, `remark` varchar(512) DEFAULT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rd_stocktake_no` (`tenant_id`,`stocktake_no`,`deleted`),
  KEY `idx_rd_stocktake_status_date` (`tenant_id`,`status`,`stocktake_date`,`deleted`),
  KEY `idx_rd_stocktake_location` (`tenant_id`,`location_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD stocktake order';

CREATE TABLE `rd_stocktake_order_line` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1', `order_id` bigint NOT NULL,
  `package_id` bigint NOT NULL, `package_code` varchar(32) NOT NULL, `stock_lot_id` bigint NOT NULL,
  `customer_name` varchar(128) NOT NULL,
  `product_model` varchar(128) NOT NULL, `furnace_no` varchar(64) NOT NULL, `furnace_layer` varchar(32) NOT NULL,
  `package_weight` decimal(24,6) NOT NULL, `book_qty` decimal(24,6) NOT NULL DEFAULT 0,
  `counted_qty` decimal(24,6) NOT NULL DEFAULT 0, `difference_qty` decimal(24,6) NOT NULL DEFAULT 0,
  `result` varchar(32) NOT NULL DEFAULT 'PENDING', `scanner_id` bigint DEFAULT NULL,
  `scanner_name` varchar(64) DEFAULT NULL, `scan_time` datetime DEFAULT NULL, `stock_transaction_id` bigint DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL, `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rd_stocktake_package` (`tenant_id`,`order_id`,`package_id`,`deleted`),
  KEY `idx_rd_stocktake_line_order` (`tenant_id`,`order_id`,`result`,`deleted`,`id`),
  KEY `idx_rd_stocktake_line_package` (`tenant_id`,`package_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD stocktake package line';

DROP TABLE IF EXISTS `rd_order_fulfillment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rd_order_fulfillment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Order fulfillment ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT 'Tenant ID',
  `order_id` bigint NOT NULL COMMENT 'RD sales order ID',
  `stock_lot_id` bigint NOT NULL COMMENT 'Stock lot ID',
  `matched_qty` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Matched quantity',
  `allocated_qty` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Allocated quantity',
  `transfer_qty` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Transfer quantity',
  `outbound_qty` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Outbound quantity',
  `source_type` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'PRODUCTION/STOCK/TRANSFER/RETURN',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MATCHED' COMMENT 'MATCHED/ALLOCATED/OUTBOUNDED/CANCELLED',
  `remark` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Remark',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_rd_fulfillment_order_lot` (`tenant_id`,`order_id`,`stock_lot_id`,`status`,`deleted`),
  KEY `idx_rd_fulfillment_status` (`tenant_id`,`status`,`deleted`),
  KEY `idx_rd_fulfillment_lot_status` (`tenant_id`,`stock_lot_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD order to stock lot fulfillment';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `rd_outbound_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rd_outbound_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Outbound order ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT 'Tenant ID',
  `outbound_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Outbound number',
  `outbound_date` date NOT NULL COMMENT 'Outbound date',
  `order_id` bigint NOT NULL COMMENT 'Sales order ID',
  `fulfillment_id` bigint NOT NULL COMMENT 'Order fulfillment ID',
  `stock_lot_id` bigint NOT NULL COMMENT 'Stock lot ID',
  `quantity` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Outbound quantity',
  `package_weight` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Single package weight',
  `weight` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Outbound weight',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/SCANNING/SUBMITTED/APPROVED/POSTED/REVERSED/CANCELLED',
  `version` int NOT NULL DEFAULT '0' COMMENT 'Optimistic version',
  `submitter_id` bigint DEFAULT NULL COMMENT 'Submitter user ID',
  `submitter_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Submitter name',
  `submit_time` datetime DEFAULT NULL COMMENT 'Submit time',
  `reviewer_id` bigint DEFAULT NULL COMMENT 'Reviewer user ID',
  `reviewer_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Reviewer name',
  `review_time` datetime DEFAULT NULL COMMENT 'Review time',
  `poster_id` bigint DEFAULT NULL COMMENT 'Posting user ID',
  `poster_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Posting user name',
  `post_time` datetime DEFAULT NULL COMMENT 'Posting time',
  `reverser_id` bigint DEFAULT NULL COMMENT 'Reversal user ID',
  `reverser_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Reversal user name',
  `reverse_time` datetime DEFAULT NULL COMMENT 'Reversal time',
  `reverse_reason` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Reversal reason',
  `remark` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Remark',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rd_outbound_no` (`tenant_id`,`outbound_no`,`deleted`),
  KEY `idx_rd_outbound_date_status` (`tenant_id`,`deleted`,`outbound_date`,`id`,`status`),
  KEY `idx_rd_outbound_order_fulfillment` (`tenant_id`,`order_id`,`fulfillment_id`,`status`,`deleted`),
  KEY `idx_rd_outbound_fulfillment_status` (`tenant_id`,`fulfillment_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD outbound order';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `rd_outbound_order_line`;
CREATE TABLE `rd_outbound_order_line` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Outbound line ID',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT 'Tenant ID',
  `order_id` bigint NOT NULL COMMENT 'Outbound order ID',
  `package_id` bigint NOT NULL COMMENT 'Package unit ID',
  `package_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Package code',
  `stock_lot_id` bigint NOT NULL COMMENT 'Stock lot ID',
  `stock_transaction_id` bigint DEFAULT NULL COMMENT 'Posting transaction ID',
  `location_id` bigint DEFAULT NULL COMMENT 'Location ID',
  `location_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Location code',
  `customer_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Customer',
  `product_model` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Product model',
  `furnace_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Furnace number',
  `furnace_layer` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Furnace layer',
  `package_weight` decimal(24,6) NOT NULL COMMENT 'Actual package weight',
  `scan_device` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Scan device',
  `scanner_id` bigint DEFAULT NULL COMMENT 'Scanner user ID',
  `scanner_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Scanner name',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rd_outbound_line_package` (`tenant_id`,`order_id`,`package_id`,`deleted`),
  KEY `idx_rd_outbound_line_order` (`tenant_id`,`order_id`,`deleted`,`id`),
  KEY `idx_rd_outbound_line_package` (`tenant_id`,`package_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD scanned outbound packages';
DROP TABLE IF EXISTS `rd_loading_package_claim`;
DROP TABLE IF EXISTS `rd_loading_exception`;
DROP TABLE IF EXISTS `rd_loading_scan_record`;
DROP TABLE IF EXISTS `rd_loading_task_line`;
DROP TABLE IF EXISTS `rd_loading_task`;
DROP TABLE IF EXISTS `rd_pickup_slip_line`;
DROP TABLE IF EXISTS `rd_pickup_slip`;
CREATE TABLE `rd_pickup_slip` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1',
  `slip_no` varchar(64) NOT NULL, `status` varchar(32) NOT NULL DEFAULT 'ISSUED',
  `customer_name` varchar(128) NOT NULL, `pickup_date` date DEFAULT NULL,
  `vehicle_plate` varchar(32) DEFAULT NULL, `transport_driver_name` varchar(64) DEFAULT NULL,
  `transport_driver_phone` varchar(32) DEFAULT NULL, `requested_package_count` int DEFAULT NULL,
  `requested_weight` decimal(24,6) DEFAULT NULL, `source_attachment_ref` varchar(512) DEFAULT NULL,
  `issuer_id` bigint DEFAULT NULL, `issuer_name` varchar(64) DEFAULT NULL, `issued_time` datetime DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0', `remark` varchar(512) DEFAULT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rd_pickup_slip_no` (`tenant_id`,`slip_no`,`deleted`),
  KEY `idx_rd_pickup_slip_customer` (`tenant_id`,`customer_name`,`status`,`deleted`,`id`),
  KEY `idx_rd_pickup_slip_date` (`tenant_id`,`pickup_date`,`status`,`deleted`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD business pickup slip';

CREATE TABLE `rd_pickup_slip_line` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1',
  `slip_id` bigint NOT NULL, `line_no` int NOT NULL, `sale_order_id` bigint DEFAULT NULL,
  `product_name` varchar(128) DEFAULT NULL, `product_model` varchar(128) DEFAULT NULL,
  `furnace_no` varchar(64) DEFAULT NULL, `furnace_layer` varchar(32) DEFAULT NULL,
  `requested_package_count` int DEFAULT NULL, `requested_weight` decimal(24,6) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL, `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rd_pickup_slip_line_no` (`tenant_id`,`slip_id`,`line_no`,`deleted`),
  KEY `idx_rd_pickup_slip_line_order` (`tenant_id`,`sale_order_id`,`deleted`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD requested pickup items';

CREATE TABLE `rd_loading_task` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1',
  `task_no` varchar(64) NOT NULL, `pickup_slip_id` bigint NOT NULL,
  `pickup_slip_no` varchar(64) NOT NULL, `status` varchar(32) NOT NULL DEFAULT 'PENDING_ASSIGNMENT',
  `customer_name` varchar(128) NOT NULL, `vehicle_plate` varchar(32) DEFAULT NULL,
  `transport_driver_name` varchar(64) DEFAULT NULL, `transport_driver_phone` varchar(32) DEFAULT NULL,
  `warehouse_keeper_id` bigint DEFAULT NULL, `warehouse_keeper_name` varchar(64) DEFAULT NULL,
  `forklift_driver_id` bigint DEFAULT NULL, `forklift_driver_name` varchar(64) DEFAULT NULL,
  `reviewer_id` bigint DEFAULT NULL, `reviewer_name` varchar(64) DEFAULT NULL,
  `planned_package_count` int NOT NULL DEFAULT '0', `scanned_package_count` int NOT NULL DEFAULT '0',
  `planned_weight` decimal(24,6) NOT NULL DEFAULT '0.000000',
  `scanned_weight` decimal(24,6) NOT NULL DEFAULT '0.000000',
  `plan_version` int NOT NULL DEFAULT '0', `version` int NOT NULL DEFAULT '0',
  `assigned_time` datetime DEFAULT NULL, `start_time` datetime DEFAULT NULL,
  `scan_complete_time` datetime DEFAULT NULL, `confirm_time` datetime DEFAULT NULL,
  `cancel_time` datetime DEFAULT NULL, `cancel_reason` varchar(512) DEFAULT NULL,
  `remark` varchar(512) DEFAULT NULL, `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rd_loading_task_no` (`tenant_id`,`task_no`,`deleted`),
  KEY `idx_rd_loading_task_slip` (`tenant_id`,`pickup_slip_id`,`status`,`deleted`,`id`),
  KEY `idx_rd_loading_task_driver` (`tenant_id`,`forklift_driver_id`,`status`,`deleted`,`id`),
  KEY `idx_rd_loading_task_keeper` (`tenant_id`,`warehouse_keeper_id`,`status`,`deleted`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD single loading task per pickup operation';

CREATE TABLE `rd_loading_task_line` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1',
  `task_id` bigint NOT NULL, `line_no` int NOT NULL, `pickup_slip_line_id` bigint NOT NULL,
  `package_id` bigint NOT NULL, `package_code` varchar(32) NOT NULL,
  `sale_order_id` bigint DEFAULT NULL, `fulfillment_id` bigint DEFAULT NULL, `stock_lot_id` bigint NOT NULL,
  `planned_location_id` bigint NOT NULL, `planned_location_code` varchar(64) NOT NULL,
  `customer_name` varchar(128) NOT NULL, `product_model` varchar(128) NOT NULL,
  `furnace_no` varchar(64) NOT NULL, `furnace_layer` varchar(32) NOT NULL,
  `package_weight` decimal(24,6) NOT NULL, `line_status` varchar(32) NOT NULL DEFAULT 'PLANNED',
  `plan_version` int NOT NULL DEFAULT '0', `scan_time` datetime DEFAULT NULL,
  `outbound_order_id` bigint DEFAULT NULL, `outbound_order_line_id` bigint DEFAULT NULL,
  `stock_transaction_id` bigint DEFAULT NULL, `version` int NOT NULL DEFAULT '0',
  `remark` varchar(512) DEFAULT NULL, `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rd_loading_line_no` (`tenant_id`,`task_id`,`line_no`,`deleted`),
  UNIQUE KEY `uk_rd_loading_line_package` (`tenant_id`,`task_id`,`package_id`,`deleted`),
  KEY `idx_rd_loading_line_task` (`tenant_id`,`task_id`,`line_status`,`deleted`,`line_no`),
  KEY `idx_rd_loading_line_package` (`tenant_id`,`package_id`,`deleted`,`id`),
  KEY `idx_rd_loading_line_location` (`tenant_id`,`planned_location_id`,`line_status`,`deleted`,`id`),
  KEY `idx_rd_loading_line_outbound` (`tenant_id`,`outbound_order_id`,`deleted`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD loading task planned package';

CREATE TABLE `rd_loading_package_claim` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1',
  `package_id` bigint NOT NULL, `task_id` bigint NOT NULL, `task_line_id` bigint NOT NULL,
  `claimed_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rd_loading_claim_package` (`tenant_id`,`package_id`),
  UNIQUE KEY `uk_rd_loading_claim_line` (`tenant_id`,`task_line_id`),
  KEY `idx_rd_loading_claim_task` (`tenant_id`,`task_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD active package claim; hard-delete on release';

CREATE TABLE `rd_loading_scan_record` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1',
  `task_id` bigint NOT NULL, `task_line_id` bigint DEFAULT NULL, `package_id` bigint DEFAULT NULL,
  `package_code` varchar(32) NOT NULL, `plan_version` int NOT NULL,
  `idempotency_key` varchar(128) NOT NULL, `scan_result` varchar(32) NOT NULL,
  `success` bit(1) NOT NULL, `error_code` varchar(64) DEFAULT NULL,
  `error_message` varchar(512) DEFAULT NULL, `task_status_before` varchar(32) DEFAULT NULL,
  `task_status_after` varchar(32) DEFAULT NULL, `observed_location_code` varchar(64) DEFAULT NULL,
  `device_code` varchar(64) DEFAULT NULL, `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(64) DEFAULT NULL, `device_time` datetime DEFAULT NULL,
  `scan_time` datetime NOT NULL, `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rd_loading_scan_idempotency` (`tenant_id`,`task_id`,`idempotency_key`,`deleted`),
  KEY `idx_rd_loading_scan_task` (`tenant_id`,`task_id`,`scan_time`,`deleted`,`id`),
  KEY `idx_rd_loading_scan_package` (`tenant_id`,`package_code`,`scan_time`,`deleted`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD loading scan attempts';

CREATE TABLE `rd_loading_exception` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1',
  `exception_no` varchar(64) NOT NULL, `task_id` bigint NOT NULL,
  `task_line_id` bigint DEFAULT NULL, `package_id` bigint DEFAULT NULL,
  `package_code` varchar(32) DEFAULT NULL, `plan_version` int NOT NULL,
  `exception_type` varchar(64) NOT NULL, `exception_status` varchar(32) NOT NULL DEFAULT 'OPEN',
  `description` varchar(1000) NOT NULL, `attachment_refs` text,
  `observed_location_code` varchar(64) DEFAULT NULL,
  `reporter_id` bigint DEFAULT NULL, `reporter_name` varchar(64) DEFAULT NULL,
  `reported_time` datetime NOT NULL, `handler_id` bigint DEFAULT NULL,
  `handler_name` varchar(64) DEFAULT NULL, `handle_time` datetime DEFAULT NULL,
  `handling_result` varchar(1000) DEFAULT NULL, `resolution_action` varchar(64) DEFAULT NULL,
  `resolved_location_id` bigint DEFAULT NULL, `resolved_location_code` varchar(64) DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0', `remark` varchar(512) DEFAULT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rd_loading_exception_no` (`tenant_id`,`exception_no`,`deleted`),
  KEY `idx_rd_loading_exception_task` (`tenant_id`,`task_id`,`exception_status`,`deleted`,`reported_time`,`id`),
  KEY `idx_rd_loading_exception_package` (`tenant_id`,`package_id`,`deleted`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD loading field exception';
DROP TABLE IF EXISTS `rd_production_output`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rd_production_output` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Production output ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT 'Tenant ID',
  `production_date` date NOT NULL COMMENT 'Production date',
  `location_id` bigint DEFAULT NULL COMMENT 'Default location ID',
  `location_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Location code',
  `customer_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Customer',
  `product_model` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Product model',
  `furnace_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Production furnace number',
  `furnace_layer` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Furnace layer',
  `output_qty` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Actual output quantity',
  `package_weight` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Single package weight',
  `output_weight` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Output weight',
  `inbound_qty` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Inbound quantity generated from this output',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/CONFIRMED/INBOUNDED/CANCELLED',
  `source_sheet` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Source sheet or Excel file',
  `remark` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Remark',
  `version` int NOT NULL DEFAULT '1' COMMENT 'Optimistic lock version',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_rd_output_date` (`tenant_id`,`deleted`,`production_date`,`id`),
  KEY `idx_rd_output_core` (`tenant_id`,`customer_name`,`product_model`,`furnace_no`,`furnace_layer`,`location_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD furnace-layer production output';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `rd_sale_order_extend`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rd_sale_order_extend` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'RD sales order ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT 'Tenant ID',
  `order_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Sales order number',
  `order_date` date NOT NULL COMMENT 'Sales order date',
  `customer_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Customer',
  `product_model` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Product model',
  `required_qty` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Required order quantity',
  `manual_output_qty` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Historical/manual output quantity',
  `split_output_qty` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Output quantity from active split bindings',
  `actual_output_qty` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Actual production output quantity',
  `difference_qty` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'required_qty - actual_output_qty',
  `allocated_qty` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Allocated quantity from stock matching',
  `transfer_in_qty` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Transfer inbound quantity',
  `transfer_out_qty` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Transfer outbound quantity',
  `adjusted_difference_qty` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'required_qty - actual_output_qty - allocated_qty - transfer_in_qty + transfer_out_qty',
  `fulfilled_qty` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Fulfilled quantity',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/PARTIAL/FULFILLED/CLOSED/CANCELLED',
  `close_time` datetime DEFAULT NULL COMMENT 'Close time',
  `close_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Close operator',
  `remark` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Remark',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rd_sale_order_no` (`tenant_id`,`order_no`,`deleted`),
  KEY `idx_rd_sale_order_date_status` (`tenant_id`,`deleted`,`order_date`,`id`,`status`),
  KEY `idx_rd_sale_order_customer_status` (`tenant_id`,`customer_name`,`product_model`,`status`,`deleted`,`order_date`,`id`),
  KEY `idx_rd_sale_order_status_date` (`tenant_id`,`status`,`deleted`,`order_date`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD sales order fulfillment extension';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `rd_order_split_output_bind`;
DROP TABLE IF EXISTS `rd_order_split_result_line`;
DROP TABLE IF EXISTS `rd_order_split_result`;
DROP TABLE IF EXISTS `rd_order_split_rule_line`;
DROP TABLE IF EXISTS `rd_order_split_rule`;
CREATE TABLE `rd_order_split_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1',
  `rule_code` varchar(64) NOT NULL, `rule_name` varchar(128) NOT NULL, `customer_name` varchar(128) NOT NULL,
  `product_model` varchar(128) NOT NULL, `default_furnace_load_qty` decimal(24,6) NOT NULL,
  `yield_rate_percent` decimal(12,6) NOT NULL, `enabled` bit(1) NOT NULL DEFAULT b'1', `version` int NOT NULL DEFAULT 1,
  `remark` varchar(512) DEFAULT NULL, `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rd_split_rule_code` (`tenant_id`,`rule_code`,`deleted`),
  KEY `idx_rd_split_rule_customer` (`tenant_id`,`customer_name`,`product_model`,`enabled`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Order split rule';
CREATE TABLE `rd_order_split_rule_line` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1', `rule_id` bigint NOT NULL,
  `sequence_no` int NOT NULL, `furnace_layer` varchar(32) NOT NULL, `allocation_type` varchar(16) NOT NULL,
  `allocation_value` decimal(24,6) NOT NULL, `remark` varchar(512) DEFAULT NULL, `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_rd_split_rule_line_seq` (`tenant_id`,`rule_id`,`sequence_no`,`deleted`),
  KEY `idx_rd_split_rule_line_rule` (`tenant_id`,`rule_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Order split rule line';
CREATE TABLE `rd_order_split_result` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1', `split_no` varchar(64) NOT NULL,
  `split_date` date NOT NULL, `sale_order_id` bigint NOT NULL, `sale_order_no` varchar(64) NOT NULL, `rule_id` bigint NOT NULL,
  `rule_name_snapshot` varchar(128) NOT NULL, `customer_name_snapshot` varchar(128) NOT NULL,
  `product_model_snapshot` varchar(128) NOT NULL, `furnace_load_qty` decimal(24,6) NOT NULL,
  `yield_rate_percent` decimal(12,6) NOT NULL, `expected_output_qty` decimal(24,6) NOT NULL,
  `final_planned_output_qty` decimal(24,6) NOT NULL, `variance_qty` decimal(24,6) NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT', `version` int NOT NULL DEFAULT 1,
  `confirmer_id` bigint DEFAULT NULL, `confirmer_name` varchar(64) DEFAULT NULL, `confirm_time` datetime DEFAULT NULL,
  `canceller_id` bigint DEFAULT NULL, `canceller_name` varchar(64) DEFAULT NULL, `cancel_time` datetime DEFAULT NULL,
  `cancel_reason` varchar(512) DEFAULT NULL, `remark` varchar(512) DEFAULT NULL, `creator` varchar(64) DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_rd_split_result_no` (`tenant_id`,`split_no`,`deleted`),
  KEY `idx_rd_split_result_order` (`tenant_id`,`sale_order_id`,`status`,`deleted`,`split_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Order split result';
CREATE TABLE `rd_order_split_result_line` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1', `result_id` bigint NOT NULL,
  `sequence_no` int NOT NULL, `furnace_layer` varchar(32) NOT NULL, `allocation_type_snapshot` varchar(16) NOT NULL,
  `allocation_value_snapshot` decimal(24,6) NOT NULL, `calculated_base_qty` decimal(24,6) NOT NULL,
  `manual_adjustment_qty` decimal(24,6) NOT NULL DEFAULT 0, `final_planned_qty` decimal(24,6) NOT NULL,
  `actual_bound_qty` decimal(24,6) NOT NULL DEFAULT 0, `adjustment_reason` varchar(512) DEFAULT NULL,
  `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `updater` varchar(64) DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`), UNIQUE KEY `uk_rd_split_result_line_seq` (`tenant_id`,`result_id`,`sequence_no`,`deleted`),
  KEY `idx_rd_split_result_line_result` (`tenant_id`,`result_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Order split result line';
CREATE TABLE `rd_order_split_output_bind` (
  `id` bigint NOT NULL AUTO_INCREMENT, `tenant_id` bigint NOT NULL DEFAULT '1', `sale_order_id` bigint NOT NULL,
  `result_id` bigint NOT NULL, `result_line_id` bigint NOT NULL, `production_output_id` bigint NOT NULL,
  `output_qty_snapshot` decimal(24,6) NOT NULL, `status` varchar(16) NOT NULL DEFAULT 'ACTIVE',
  `binder_id` bigint DEFAULT NULL, `binder_name` varchar(64) DEFAULT NULL, `bind_time` datetime NOT NULL,
  `unbinder_id` bigint DEFAULT NULL, `unbinder_name` varchar(64) DEFAULT NULL, `unbind_time` datetime DEFAULT NULL,
  `unbind_reason` varchar(512) DEFAULT NULL, `creator` varchar(64) DEFAULT '', `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) DEFAULT '', `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0', PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rd_split_bind_output` (`tenant_id`,`production_output_id`,`deleted`),
  KEY `idx_rd_split_bind_order` (`tenant_id`,`sale_order_id`,`status`,`deleted`),
  KEY `idx_rd_split_bind_line` (`tenant_id`,`result_line_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Order split production output binding';
DROP TABLE IF EXISTS `rd_customer_product`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rd_customer_product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Customer product relation ID',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT 'Tenant ID',
  `customer_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Customer',
  `product_model` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Product model',
  `source_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'BUSINESS' COMMENT 'EXCEL/BUSINESS',
  `last_used_time` datetime DEFAULT NULL COMMENT 'Last successful business use time',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0 enabled, 1 disabled',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rd_customer_product` (`tenant_id`,`customer_name`,`product_model`,`deleted`),
  KEY `idx_rd_customer_product_customer` (`tenant_id`,`status`,`deleted`,`customer_name`),
  KEY `idx_rd_customer_product_model` (`tenant_id`,`status`,`deleted`,`product_model`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD customer and product model options';

INSERT INTO `rd_customer_product`
(`tenant_id`, `customer_name`, `product_model`, `source_type`, `last_used_time`, `status`,
 `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(1, '江苏贝特瑞', '026', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '江苏贝特瑞', '035', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '江苏贝特瑞', '035JC', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '江苏贝特瑞', '038/CC-H', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '江苏贝特瑞', '038', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '江苏贝特瑞', '041', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '江苏贝特瑞', '052', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '江苏贝特瑞', '080', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '江苏贝特瑞', '092', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '贝特瑞产品一部', '015', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '贝特瑞产品一部', '052', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '贝特瑞产品一部', '13031', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '贝特瑞产品二部', '027', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '贝特瑞产品二部', '035', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '贝特瑞产品二部', '035JC', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '贝特瑞产品二部', '092', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '贝特瑞产品二部', '001', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '贝特瑞产品二部', '001D1L', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '贝特瑞产品三部', '035', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '贝特瑞产品三部', '065', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '瑞安贝特瑞', '031', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '恒鼎新材', '027', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '恒鼎新材', '035', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '恒鼎新材', 'A1-GR-ZC-001J', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '恒鼎新材', 'A1-GR-ZC-001-K', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '湖州启源金灿', 'YG15', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '恒鼎新材/江苏贝特瑞', '035', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '中科星城', '5PA', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '中科星城', '1CC-2M', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '中科星城', '1CY-2M', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '中科星城', '1CY-2H', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '中科星城', '1CF-2H', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '中科星城', '1SO-3G-M', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '中科星城', '1SI-3G-M', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '中科星城', '1SA-2G-M', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '中科星城', '1SL-3G-M', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '中科星城', '1C083-2M', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '中科星城', '1C090-2H', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '中科星城', '1SC-5G-M', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '中科星城', '1S139-3G-M', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '湖北皇恩烨', 'S11石墨化-混011&02', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '重庆渝盛星', 'CC-H', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '重庆渝盛星', 'NGC15', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '重庆渝盛星', 'C1', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '云南坤天', '石墨化A料XJ-11', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '四川坤天', '石墨化A料P6-9', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '湖南镕锂', 'G-PC071', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '瑞鼎新材料', '026', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '瑞鼎新材料', '035', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '瑞鼎新材料', '035JC', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '瑞鼎新材料', '038', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '瑞鼎新材料', '038/CC-H', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '瑞鼎新材料', '041', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '瑞鼎新材料', '049', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '瑞鼎新材料', '052', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '瑞鼎新材料', '062', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '瑞鼎新材料', '065', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '瑞鼎新材料', '077', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '瑞鼎新材料', '081', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '瑞鼎新材料', '13031', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '瑞鼎新材料', '磁性物', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '瑞鼎新材料', '混合料', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '瑞鼎新材料', '破碎料', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '贝特瑞产品三部退货', '035', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '江苏贝特瑞退货', '035', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0'),
(1, '江苏贝特瑞退货', '041', 'EXCEL', NOW(), 0, 'system', NOW(), 'system', NOW(), b'0');

DROP TABLE IF EXISTS `rd_stock_lot`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rd_stock_lot` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Stock lot ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT 'Tenant ID',
  `location_id` bigint DEFAULT NULL COMMENT 'Location ID',
  `location_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Location code',
  `customer_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Customer',
  `product_model` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Product model',
  `furnace_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Production furnace number',
  `furnace_layer` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Furnace layer',
  `opening_qty` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Opening quantity',
  `transfer_in_qty` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Transfer inbound quantity',
  `transfer_out_qty` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Transfer outbound quantity',
  `inbound_qty` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Inbound quantity',
  `outbound_qty` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Outbound quantity',
  `return_qty` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Return quantity',
  `warehouse_qty` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Current warehouse quantity',
  `locked_qty` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Quantity locked by order matching',
  `package_weight` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Single package weight',
  `stock_weight` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Current stock weight',
  `last_biz_date` date DEFAULT NULL COMMENT 'Last movement date',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'AVAILABLE' COMMENT 'AVAILABLE/LOCKED/EMPTY/CLOSED',
  `remark` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Remark',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_rd_stock_lot_core` (`tenant_id`,`customer_name`,`product_model`,`furnace_no`,`furnace_layer`,`location_code`,`deleted`),
  KEY `idx_rd_stock_lot_match` (`tenant_id`,`product_model`,`status`,`deleted`,`last_biz_date`,`id`),
  KEY `idx_rd_stock_lot_customer_match` (`tenant_id`,`customer_name`,`product_model`,`status`,`deleted`,`last_biz_date`,`id`),
  KEY `idx_rd_stock_lot_location` (`tenant_id`,`location_id`,`status`,`deleted`),
  KEY `idx_rd_stock_lot_status_date` (`tenant_id`,`deleted`,`status`,`last_biz_date`,`id`),
  KEY `idx_rd_stock_lot_timeline` (`tenant_id`,`deleted`,`last_biz_date`,`id`,`status`),
  KEY `idx_rd_stock_lot_status_qty` (`tenant_id`,`status`,`deleted`,`warehouse_qty`,`locked_qty`,`stock_weight`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD inventory lot ledger';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `rd_stock_transaction`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rd_stock_transaction` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Stock transaction ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT 'Tenant ID',
  `biz_date` date NOT NULL COMMENT 'Business date',
  `transaction_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'OPENING/PRODUCTION_IN/INBOUND/OUTBOUND/TRANSFER_IN/TRANSFER_OUT/RETURN_IN/ADJUST_IN/ADJUST_OUT',
  `stock_lot_id` bigint DEFAULT NULL COMMENT 'Stock lot ID',
  `package_id` bigint DEFAULT NULL COMMENT 'Package unit ID',
  `location_id` bigint DEFAULT NULL COMMENT 'Location ID',
  `location_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Location code',
  `customer_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Customer',
  `product_model` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Product model',
  `furnace_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Production furnace number',
  `furnace_layer` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Furnace layer',
  `quantity` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Movement quantity',
  `package_weight` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Single package weight',
  `weight` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Movement weight',
  `before_qty` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Quantity before movement',
  `after_qty` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Quantity after movement',
  `source_type` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Source type',
  `source_no` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Source number',
  `source_line_no` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Source line number',
  `related_order_id` bigint DEFAULT NULL COMMENT 'Related sales order ID',
  `operator_id` bigint DEFAULT NULL COMMENT 'Operator user ID',
  `operator_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Operator name',
  `idempotency_key` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Posting idempotency key',
  `reversal_transaction_id` bigint DEFAULT NULL COMMENT 'Original transaction ID reversed by this row',
  `reviewer_id` bigint DEFAULT NULL COMMENT 'Reviewer user ID',
  `reviewer_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Reviewer name',
  `remark` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Remark',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_rd_stock_txn_date_type` (`tenant_id`,`biz_date`,`transaction_type`,`deleted`,`id`),
  KEY `idx_rd_stock_txn_lot_time` (`tenant_id`,`stock_lot_id`,`deleted`,`create_time`,`id`),
  KEY `idx_rd_stock_txn_source` (`tenant_id`,`source_type`,`source_no`,`deleted`),
  KEY `idx_rd_stock_txn_order` (`tenant_id`,`related_order_id`,`deleted`,`biz_date`,`id`),
  KEY `idx_rd_stock_txn_matrix` (`tenant_id`,`customer_name`,`product_model`,`biz_date`,`stock_lot_id`,`deleted`)
  ,UNIQUE KEY `uk_rd_stock_txn_idempotency` (`tenant_id`,`idempotency_key`,`deleted`)
  ,KEY `idx_rd_stock_txn_package` (`tenant_id`,`package_id`,`biz_date`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD inventory movement ledger';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `rd_transfer_order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rd_transfer_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Transfer order ID',
  `tenant_id` bigint NOT NULL DEFAULT '0' COMMENT 'Tenant ID',
  `transfer_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Transfer number',
  `transfer_date` date NOT NULL COMMENT 'Transfer date',
  `from_order_id` bigint DEFAULT NULL COMMENT 'Source order ID',
  `to_order_id` bigint DEFAULT NULL COMMENT 'Target order ID',
  `from_stock_lot_id` bigint DEFAULT NULL COMMENT 'Source stock lot ID',
  `to_stock_lot_id` bigint DEFAULT NULL COMMENT 'Target stock lot ID',
  `quantity` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Transfer quantity',
  `package_weight` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Single package weight',
  `weight` decimal(24,6) NOT NULL DEFAULT '0.000000' COMMENT 'Transfer weight',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/CONFIRMED/CANCELLED',
  `remark` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Remark',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rd_transfer_no` (`tenant_id`,`transfer_no`,`deleted`),
  KEY `idx_rd_transfer_date_status` (`tenant_id`,`deleted`,`transfer_date`,`id`,`status`),
  KEY `idx_rd_transfer_link` (`tenant_id`,`to_order_id`,`from_stock_lot_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD order transfer';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `rd_transfer_order_package`;
CREATE TABLE `rd_transfer_order_package` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Transfer package link ID',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT 'Tenant ID',
  `transfer_order_id` bigint NOT NULL COMMENT 'Transfer order ID',
  `package_id` bigint NOT NULL COMMENT 'Package ID',
  `package_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Package code',
  `from_stock_lot_id` bigint NOT NULL COMMENT 'Source stock lot ID',
  `to_stock_lot_id` bigint NOT NULL COMMENT 'Target stock lot ID',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rd_transfer_package` (`tenant_id`,`transfer_order_id`,`package_id`,`deleted`),
  KEY `idx_rd_transfer_package_code` (`tenant_id`,`package_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RD transfer package trace';
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

SET NAMES utf8mb4;

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(600000, '生产货物管理', '', 1, 35, 0, '/rd', 'ep:box', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600010, '库存看板', 'rd:dashboard:query', 2, 1, 600000, 'dashboard', 'ep:data-board', 'rd/dashboard/index', 'RdInventoryDashboard', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600100, '生产管理', '', 1, 10, 600000, 'production', 'ep:cpu', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600101, '生产炉层产量', 'rd:production-output:query', 2, 10, 600100, 'output', 'ep:list', 'rd/production/output/index', 'RdProductionOutput', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600102, '生产炉层产量新增', 'rd:production-output:create', 3, 10, 600101, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600103, '生产炉层产量编辑', 'rd:production-output:update', 3, 20, 600101, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600104, '生产炉层产量作废', 'rd:production-output:cancel', 3, 30, 600101, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600105, '生产炉层产量导出', 'rd:production-output:export', 3, 40, 600101, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600106, '生产炉层产量入库', 'rd:production-output:inbound', 3, 50, 600101, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600107, '生产炉层产量确认', 'rd:production-output:confirm', 3, 25, 600101, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600200, '仓库管理', '', 1, 20, 600000, 'stock', 'ep:house', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600201, '库存台账', 'rd:stock-lot:query', 2, 10, 600200, 'lot', 'ep:collection', 'rd/stock/lot/index', 'RdStockLot', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600202, '库存台账导出', 'rd:stock-lot:export', 3, 20, 600201, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600205, '库存期初录入', 'rd:stock-lot:opening', 3, 30, 600201, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600206, '库存调整', 'rd:stock-lot:adjust', 3, 40, 600201, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600203, '库存流水', 'rd:stock-transaction:query', 2, 20, 600200, 'transaction', 'ep:list', 'rd/stock/transaction/index', 'RdStockTransaction', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600204, '库存流水导出', 'rd:stock-transaction:export', 3, 10, 600203, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600207, '出库上线', 'rd:outbound-order:query', 2, 30, 600200, 'outbound', 'ep:upload', 'rd/outbound/index', 'RdOutboundOrder', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600208, '出库单新增', 'rd:outbound-order:create', 3, 10, 600207, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600209, '出库单编辑', 'rd:outbound-order:update', 3, 20, 600207, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600210, '出库单确认', 'rd:outbound-order:confirm', 3, 30, 600207, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600211, '出库单撤销', 'rd:outbound-order:cancel', 3, 40, 600207, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600212, '出库单导出', 'rd:outbound-order:export', 3, 50, 600207, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600250, '出库扫码', 'rd:outbound-scan:scan', 3, 60, 600207, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600251, '出库提交', 'rd:outbound-scan:submit', 3, 70, 600207, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600252, '出库审核', 'rd:outbound-scan:approve', 3, 80, 600207, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600253, '出库过账', 'rd:outbound-scan:post', 3, 90, 600207, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600254, '出库反向撤销', 'rd:outbound-scan:reverse', 3, 100, 600207, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600213, '库存动态明细', 'rd:stock-matrix:query', 2, 25, 600200, 'matrix', 'ep:grid', 'rd/stock/matrix/index', 'RdStockMatrix', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600215, '入库上架', 'rd:putaway-task:query', 2, 38, 600200, 'putaway', 'ep:position', 'rd/stock/putaway/index', 'RdPutawayWorkbench', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600216, '叉车上架', 'rd:putaway-task:execute', 2, 39, 600200, 'driver-putaway', 'lucide:forklift', 'rd/pda/putaway/index', 'RdDriverPutaway', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600300, '订单管理', '', 1, 30, 600000, 'order', 'ep:tickets', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600301, '销售订单', 'rd:sale-order:query', 2, 10, 600300, 'sale', 'ep:document', 'rd/order/sale/index', 'RdSaleOrder', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600302, '销售订单新增', 'rd:sale-order:create', 3, 10, 600301, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600303, '销售订单编辑', 'rd:sale-order:update', 3, 20, 600301, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600304, '销售订单取消', 'rd:sale-order:cancel', 3, 30, 600301, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600305, '销售订单重算', 'rd:sale-order:recalculate', 3, 40, 600301, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600306, '销售订单导出', 'rd:sale-order:export', 3, 50, 600301, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600310, '订单匹配', 'rd:order-fulfillment:query', 2, 20, 600300, 'fulfillment', 'ep:connection', 'rd/order/fulfillment/index', 'RdOrderFulfillment', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600311, '订单手动匹配', 'rd:order-fulfillment:allocate', 3, 10, 600310, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600312, '订单自动匹配', 'rd:order-fulfillment:auto', 3, 20, 600310, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600313, '订单取消匹配', 'rd:order-fulfillment:cancel', 3, 30, 600310, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600320, '订单拆分', 'rd:order-split:query', 2, 30, 600300, 'split', 'ep:share', 'rd/order/split/index', 'RdOrderSplit', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600321, '订单拆分新增', 'rd:order-split:create', 3, 10, 600320, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600322, '订单拆分调整', 'rd:order-split:adjust', 3, 20, 600320, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600323, '订单拆分确认', 'rd:order-split:confirm', 3, 30, 600320, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600324, '订单拆分取消', 'rd:order-split:cancel', 3, 40, 600320, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600325, '订单拆分绑定', 'rd:order-split:bind', 3, 50, 600320, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600326, '拆分规则查询', 'rd:order-split:rule-query', 3, 60, 600320, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600327, '拆分规则管理', 'rd:order-split:rule-manage', 3, 70, 600320, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600400, '调拨管理', '', 1, 40, 600000, 'transfer', 'ep:sort', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600401, '调拨单', 'rd:transfer-order:query', 2, 10, 600400, 'list', 'ep:switch', 'rd/transfer/index', 'RdTransferOrder', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600402, '调拨单新增', 'rd:transfer-order:create', 3, 10, 600401, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600403, '调拨单编辑', 'rd:transfer-order:update', 3, 20, 600401, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600404, '调拨单确认', 'rd:transfer-order:confirm', 3, 30, 600401, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600405, '调拨单撤销', 'rd:transfer-order:cancel', 3, 40, 600401, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600406, '调拨单导出', 'rd:transfer-order:export', 3, 50, 600401, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600500, '报表中心', '', 1, 50, 600000, 'report', 'ep:trend-charts', NULL, NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600501, '每日出入库统计', 'rd:daily-stock-summary:query', 2, 10, 600500, 'daily', 'ep:data-analysis', 'rd/report/daily/index', 'RdDailyStockSummary', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600502, '每日统计重算', 'rd:daily-stock-summary:rebuild', 3, 10, 600501, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600503, '每日统计导出', 'rd:daily-stock-summary:export', 3, 20, 600501, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600220, '库存 Excel 导入', 'rd:excel-import:query', 2, 40, 600200, 'import', 'ep:upload-filled', 'rd/stock/import/index', 'RdStockImport', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600221, '库存 Excel 预检', 'rd:excel-import:create', 3, 10, 600220, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600222, '确认库存 Excel 导入', 'rd:excel-import:confirm', 3, 20, 600220, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600223, '导出库存 Excel 错误', 'rd:excel-import:export', 3, 30, 600220, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600224, '客户型号选项查询', 'rd:customer-product:query', 3, 90, 600000, '', '', '', NULL, 0, b'0', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'),
(600214, '库存盘点', 'rd:stocktake:query', 2, 45, 600200, 'stocktake', 'ep:document-checked', 'rd/stock/stocktake/index', 'RdStocktake', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(600260, '盘点单新增', 'rd:stocktake:create', 3, 10, 600214, '', '', '', NULL, 0, b'0', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'),
(600261, '盘点冻结', 'rd:stocktake:freeze', 3, 20, 600214, '', '', '', NULL, 0, b'0', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'),
(600262, '盘点扫码', 'rd:stocktake:scan', 3, 30, 600214, '', '', '', NULL, 0, b'0', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'),
(600263, '盘点提交', 'rd:stocktake:submit', 3, 40, 600214, '', '', '', NULL, 0, b'0', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'),
(600264, '盘点审核', 'rd:stocktake:approve', 3, 50, 600214, '', '', '', NULL, 0, b'0', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'),
(600265, '盘点差异调整', 'rd:stocktake:adjust', 3, 60, 600214, '', '', '', NULL, 0, b'0', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'),
(600266, '盘点关闭', 'rd:stocktake:close', 3, 70, 600214, '', '', '', NULL, 0, b'0', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'),
(600267, '盘点取消', 'rd:stocktake:cancel', 3, 80, 600214, '', '', '', NULL, 0, b'0', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'),
(600270, '创建上架任务', 'rd:putaway-task:create', 3, 10, 600215, '', '', '', NULL, 0, b'0', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'),
(600271, '调整上架派单', 'rd:putaway-task:assign', 3, 20, 600215, '', '', '', NULL, 0, b'0', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'),
(600272, '执行上架任务', 'rd:putaway-task:execute', 3, 30, 600215, '', '', '', NULL, 0, b'0', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0'),
(600273, '确认上架入库', 'rd:putaway-task:confirm', 3, 40, 600215, '', '', '', NULL, 0, b'0', b'0', b'0', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `permission` = VALUES(`permission`),
  `type` = VALUES(`type`),
  `sort` = VALUES(`sort`),
  `parent_id` = VALUES(`parent_id`),
  `path` = VALUES(`path`),
  `icon` = VALUES(`icon`),
  `component` = VALUES(`component`),
  `component_name` = VALUES(`component_name`),
  `status` = VALUES(`status`),
  `visible` = VALUES(`visible`),
  `keep_alive` = VALUES(`keep_alive`),
  `always_show` = VALUES(`always_show`),
  `deleted` = b'0',
  `updater` = 'admin',
  `update_time` = NOW();

INSERT INTO `infra_job`
(`id`, `name`, `status`, `handler_name`, `handler_param`, `cron_expression`, `retry_count`, `retry_interval`, `monitor_timeout`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(600001, 'RD 每日库存汇总', 1, 'rdDailyStockSummaryJob', NULL, '0 10 0 * * ?', 2, 60, 600, 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `status` = VALUES(`status`),
  `handler_name` = VALUES(`handler_name`),
  `handler_param` = VALUES(`handler_param`),
  `cron_expression` = VALUES(`cron_expression`),
  `retry_count` = VALUES(`retry_count`),
  `retry_interval` = VALUES(`retry_interval`),
  `monitor_timeout` = VALUES(`monitor_timeout`),
  `updater` = VALUES(`updater`),
  `update_time` = VALUES(`update_time`),
  `deleted` = VALUES(`deleted`);

-- -----------------------------------------------------------------------------
-- Ruiding ERP controlled baseline data
-- No external channel, social client, demo user, or plaintext credential is seeded.
-- The bootstrap administrator is activated from RD_ADMIN_INITIAL_PASSWORD at startup.
-- -----------------------------------------------------------------------------

INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(1, '系统管理', '', 1, 90, 0, '/system', 'ep:setting', NULL, NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(100, '用户管理', 'system:user:list', 2, 10, 1, 'user', 'ep:user', 'system/user/index', 'SystemUser', 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(101, '角色管理', '', 2, 20, 1, 'role', 'ep:user-filled', 'system/role/index', 'SystemRole', 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(103, '部门管理', '', 2, 30, 1, 'dept', 'ep:office-building', 'system/dept/index', 'SystemDept', 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(104, '岗位管理', '', 2, 40, 1, 'post', 'ep:briefcase', 'system/post/index', 'SystemPost', 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(105, '字典管理', '', 2, 50, 1, 'dict', 'ep:collection', 'system/dict/index', 'SystemDict', 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(108, '审计日志', '', 1, 60, 1, 'log', 'ep:document', NULL, NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(500, '操作日志', '', 2, 10, 108, 'operate-log', 'ep:document-checked', 'system/operatelog/index', 'SystemOperateLog', 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(501, '登录日志', '', 2, 20, 108, 'login-log', 'ep:key', 'system/loginlog/index', 'SystemLoginLog', 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(1001, '用户查询', 'system:user:query', 3, 10, 100, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(1002, '用户新增', 'system:user:create', 3, 20, 100, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(1003, '用户修改', 'system:user:update', 3, 30, 100, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(1004, '用户删除', 'system:user:delete', 3, 40, 100, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(1005, '用户导出', 'system:user:export', 3, 50, 100, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(1006, '用户导入', 'system:user:import', 3, 60, 100, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(1007, '重置密码', 'system:user:update-password', 3, 70, 100, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(1008, '角色查询', 'system:role:query', 3, 10, 101, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(1009, '角色新增', 'system:role:create', 3, 20, 101, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(1010, '角色修改', 'system:role:update', 3, 30, 101, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(1011, '角色删除', 'system:role:delete', 3, 40, 101, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(1012, '角色导出', 'system:role:export', 3, 50, 101, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(1063, '设置角色菜单权限', 'system:permission:assign-role-menu', 3, 60, 101, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(1064, '设置角色数据权限', 'system:permission:assign-role-data-scope', 3, 70, 101, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(1065, '设置用户角色', 'system:permission:assign-user-role', 3, 80, 101, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(1017, '部门查询', 'system:dept:query', 3, 10, 103, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(1018, '部门新增', 'system:dept:create', 3, 20, 103, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(1019, '部门修改', 'system:dept:update', 3, 30, 103, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(1020, '部门删除', 'system:dept:delete', 3, 40, 103, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(1021, '岗位查询', 'system:post:query', 3, 10, 104, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(1022, '岗位新增', 'system:post:create', 3, 20, 104, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(1023, '岗位修改', 'system:post:update', 3, 30, 104, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(1024, '岗位删除', 'system:post:delete', 3, 40, 104, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(1025, '岗位导出', 'system:post:export', 3, 50, 104, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(1026, '字典查询', 'system:dict:query', 3, 10, 105, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(1027, '字典新增', 'system:dict:create', 3, 20, 105, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(1028, '字典修改', 'system:dict:update', 3, 30, 105, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(1029, '字典删除', 'system:dict:delete', 3, 40, 105, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(1030, '字典导出', 'system:dict:export', 3, 50, 105, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(1040, '操作日志查询', 'system:operate-log:query', 3, 10, 500, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(1042, '操作日志导出', 'system:operate-log:export', 3, 20, 500, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(1043, '登录日志查询', 'system:login-log:query', 3, 10, 501, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0'),
(1045, '登录日志导出', 'system:login-log:export', 3, 20, 501, '', '', '', NULL, 0, b'1', b'1', b'1', 'system', NOW(), 'system', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `permission`=VALUES(`permission`), `type`=VALUES(`type`), `sort`=VALUES(`sort`), `parent_id`=VALUES(`parent_id`), `path`=VALUES(`path`), `icon`=VALUES(`icon`), `component`=VALUES(`component`), `component_name`=VALUES(`component_name`), `status`=VALUES(`status`), `visible`=VALUES(`visible`), `keep_alive`=VALUES(`keep_alive`), `always_show`=VALUES(`always_show`), `deleted`=b'0', `updater`='system', `update_time`=NOW();

INSERT INTO `system_dict_type` (`id`, `name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(1, '用户性别', 'system_user_sex', 0, 'ERP 用户性别', 'system', NOW(), 'system', NOW(), b'0'),
(10, '系统状态', 'common_status', 0, '通用启用状态', 'system', NOW(), 'system', NOW(), b'0'),
(104, '登录结果', 'system_login_result', 0, '登录结果', 'system', NOW(), 'system', NOW(), b'0'),
(116, '登录类型', 'system_login_type', 0, '登录类型', 'system', NOW(), 'system', NOW(), b'0'),
(145, '角色类型', 'system_role_type', 0, '角色类型', 'system', NOW(), 'system', NOW(), b'0'),
(146, '数据权限', 'system_data_scope', 0, '角色数据权限范围', 'system', NOW(), 'system', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `status`=VALUES(`status`), `remark`=VALUES(`remark`), `deleted`=b'0';

INSERT INTO `system_dict_data` (`id`, `sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(1, 1, '男', '1', 'system_user_sex', 0, 'primary', '', '男', 'system', NOW(), 'system', NOW(), b'0'),
(2, 2, '女', '2', 'system_user_sex', 0, 'success', '', '女', 'system', NOW(), 'system', NOW(), b'0'),
(27, 1, '启用', '0', 'common_status', 0, 'success', '', '启用', 'system', NOW(), 'system', NOW(), b'0'),
(28, 2, '停用', '1', 'common_status', 0, 'info', '', '停用', 'system', NOW(), 'system', NOW(), b'0'),
(32, 1, '内置', '1', 'system_role_type', 0, 'danger', '', '内置角色', 'system', NOW(), 'system', NOW(), b'0'),
(33, 2, '自定义', '2', 'system_role_type', 0, 'primary', '', '自定义角色', 'system', NOW(), 'system', NOW(), b'0'),
(34, 1, '全部数据权限', '1', 'system_data_scope', 0, '', '', '全部数据', 'system', NOW(), 'system', NOW(), b'0'),
(35, 2, '指定部门数据权限', '2', 'system_data_scope', 0, '', '', '指定部门', 'system', NOW(), 'system', NOW(), b'0'),
(36, 3, '本部门数据权限', '3', 'system_data_scope', 0, '', '', '本部门', 'system', NOW(), 'system', NOW(), b'0'),
(37, 4, '本部门及以下数据权限', '4', 'system_data_scope', 0, '', '', '本部门及以下', 'system', NOW(), 'system', NOW(), b'0'),
(38, 5, '仅本人数据权限', '5', 'system_data_scope', 0, '', '', '仅本人', 'system', NOW(), 'system', NOW(), b'0'),
(39, 0, '成功', '0', 'system_login_result', 0, 'success', '', '登录成功', 'system', NOW(), 'system', NOW(), b'0'),
(40, 10, '账号或密码不正确', '10', 'system_login_result', 0, 'danger', '', '凭据错误', 'system', NOW(), 'system', NOW(), b'0'),
(41, 20, '用户被禁用', '20', 'system_login_result', 0, 'warning', '', '用户停用', 'system', NOW(), 'system', NOW(), b'0'),
(42, 30, '验证码不存在', '30', 'system_login_result', 0, 'info', '', '验证码不存在', 'system', NOW(), 'system', NOW(), b'0'),
(43, 31, '验证码不正确', '31', 'system_login_result', 0, 'info', '', '验证码错误', 'system', NOW(), 'system', NOW(), b'0'),
(44, 100, '未知异常', '100', 'system_login_result', 0, 'danger', '', '未知异常', 'system', NOW(), 'system', NOW(), b'0'),
(80, 100, '账号登录', '100', 'system_login_type', 0, 'primary', '', '账号登录', 'system', NOW(), 'system', NOW(), b'0'),
(81, 101, '社交登录', '101', 'system_login_type', 0, 'info', '', '社交登录', 'system', NOW(), 'system', NOW(), b'0'),
(83, 200, '主动登出', '200', 'system_login_type', 0, 'primary', '', '主动登出', 'system', NOW(), 'system', NOW(), b'0'),
(85, 202, '强制登出', '202', 'system_login_type', 0, 'danger', '', '强制登出', 'system', NOW(), 'system', NOW(), b'0'),
(1155, 103, '短信登录', '103', 'system_login_type', 0, 'default', '', '短信登录', 'system', NOW(), 'system', NOW(), b'0')
ON DUPLICATE KEY UPDATE `sort`=VALUES(`sort`), `label`=VALUES(`label`), `value`=VALUES(`value`), `dict_type`=VALUES(`dict_type`), `status`=VALUES(`status`), `color_type`=VALUES(`color_type`), `remark`=VALUES(`remark`), `deleted`=b'0';


INSERT INTO `system_tenant` (`id`, `name`, `contact_user_id`, `contact_name`, `contact_mobile`, `status`, `websites`, `expire_time`, `account_count`, `creator`, `create_time`, `updater`, `update_time`, `deleted`) VALUES
(1, '宁夏天净隆鼎碳化硅有限公司', 1, '系统管理员', NULL, 0, '[]', '2099-12-31 23:59:59', 500, 'system', NOW(), 'system', NOW(), b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `contact_user_id`=VALUES(`contact_user_id`), `status`=VALUES(`status`), `expire_time`=VALUES(`expire_time`), `account_count`=VALUES(`account_count`), `deleted`=b'0';

INSERT INTO `system_dept` (`id`, `name`, `parent_id`, `sort`, `leader_user_id`, `phone`, `email`, `status`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`) VALUES
(1, '宁夏天净隆鼎碳化硅有限公司', 0, 1, 1, NULL, NULL, 0, 'system', NOW(), 'system', NOW(), b'0', 1)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `status`=VALUES(`status`), `tenant_id`=1, `deleted`=b'0';

INSERT INTO `system_post` (`id`, `code`, `name`, `sort`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`) VALUES
(1, 'RD_PRODUCTION', '生产录入岗', 10, 0, '生产产量录入与确认', 'system', NOW(), 'system', NOW(), b'0', 1),
(2, 'RD_WAREHOUSE', '库房管理岗', 20, 0, '入库、调拨、出库与库存调整', 'system', NOW(), 'system', NOW(), b'0', 1),
(3, 'RD_SALES', '销售订单岗', 30, 0, '销售订单与订单匹配', 'system', NOW(), 'system', NOW(), b'0', 1),
(4, 'RD_AUDIT', '审核报表岗', 40, 0, '审核、日志与报表', 'system', NOW(), 'system', NOW(), b'0', 1),
(5, 'RD_READONLY', '只读查询岗', 50, 0, '业务只读查询', 'system', NOW(), 'system', NOW(), b'0', 1)
ON DUPLICATE KEY UPDATE `code`=VALUES(`code`), `name`=VALUES(`name`), `sort`=VALUES(`sort`), `status`=VALUES(`status`), `remark`=VALUES(`remark`), `tenant_id`=1, `deleted`=b'0';

INSERT INTO `system_role` (`id`, `name`, `code`, `sort`, `data_scope`, `data_scope_dept_ids`, `status`, `type`, `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`) VALUES
(1, '超级管理员', 'super_admin', 1, 1, '', 0, 1, '系统内置管理员', 'system', NOW(), 'system', NOW(), b'0', 1),
(600001, '生产录入', 'rd_production', 10, 1, '', 0, 2, '生产产量录入与查询', 'system', NOW(), 'system', NOW(), b'0', 1),
(600002, '库房管理', 'rd_warehouse', 20, 1, '', 0, 2, '库存、调拨、出库管理', 'system', NOW(), 'system', NOW(), b'0', 1),
(600003, '销售订单', 'rd_sales', 30, 1, '', 0, 2, '销售订单与订单匹配', 'system', NOW(), 'system', NOW(), b'0', 1),
(600004, '审核报表', 'rd_audit', 40, 1, '', 0, 2, '审核、报表与日志查询', 'system', NOW(), 'system', NOW(), b'0', 1),
(600005, '只读查询', 'rd_readonly', 50, 1, '', 0, 2, '业务数据只读', 'system', NOW(), 'system', NOW(), b'0', 1)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `code`=VALUES(`code`), `sort`=VALUES(`sort`), `data_scope`=VALUES(`data_scope`), `status`=VALUES(`status`), `type`=VALUES(`type`), `remark`=VALUES(`remark`), `tenant_id`=1, `deleted`=b'0';

INSERT INTO `system_users` (`id`, `username`, `password`, `nickname`, `remark`, `dept_id`, `post_ids`, `email`, `mobile`, `sex`, `avatar`, `status`, `login_ip`, `login_date`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`) VALUES
(1, 'admin', '!BOOTSTRAP_REQUIRED!', '系统管理员', '首次启动必须通过 RD_ADMIN_INITIAL_PASSWORD 激活', 1, '[5]', '', '', 0, '', 0, '', NULL, 'system', NOW(), 'system', NOW(), b'0', 1)
ON DUPLICATE KEY UPDATE `nickname`=VALUES(`nickname`), `dept_id`=VALUES(`dept_id`), `tenant_id`=1, `deleted`=b'0';

INSERT INTO `system_user_role` (`id`, `user_id`, `role_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`) VALUES
(1, 1, 1, 'system', NOW(), 'system', NOW(), b'0', 1)
ON DUPLICATE KEY UPDATE `user_id`=VALUES(`user_id`), `role_id`=VALUES(`role_id`), `tenant_id`=1, `deleted`=b'0';

INSERT INTO `system_user_post` (`id`, `user_id`, `post_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`) VALUES
(1, 1, 5, 'system', NOW(), 'system', NOW(), b'0', 1)
ON DUPLICATE KEY UPDATE `user_id`=VALUES(`user_id`), `post_id`=VALUES(`post_id`), `tenant_id`=1, `deleted`=b'0';

INSERT INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
(600230,'产品资料查询','rd:product-master:query',3,230,600000,'','','',NULL,0,b'0',b'0',b'0','system',NOW(),'system',NOW(),b'0'),
(600231,'产品资料新增','rd:product-master:create',3,231,600000,'','','',NULL,0,b'0',b'0',b'0','system',NOW(),'system',NOW(),b'0'),
(600232,'产品资料修改','rd:product-master:update',3,232,600000,'','','',NULL,0,b'0',b'0',b'0','system',NOW(),'system',NOW(),b'0'),
(600233,'仓库货位查询','rd:warehouse-location:query',3,233,600000,'','','',NULL,0,b'0',b'0',b'0','system',NOW(),'system',NOW(),b'0'),
(600234,'仓库货位新增','rd:warehouse-location:create',3,234,600000,'','','',NULL,0,b'0',b'0',b'0','system',NOW(),'system',NOW(),b'0'),
(600235,'仓库货位修改','rd:warehouse-location:update',3,235,600000,'','','',NULL,0,b'0',b'0',b'0','system',NOW(),'system',NOW(),b'0'),
(600236,'包码查询','rd:package-unit:query',3,236,600000,'','','',NULL,0,b'0',b'0',b'0','system',NOW(),'system',NOW(),b'0'),
(600237,'包码生成','rd:package-unit:generate',3,237,600000,'','','',NULL,0,b'0',b'0',b'0','system',NOW(),'system',NOW(),b'0'),
(600238,'标签打印','rd:label-print:print',3,238,600000,'','','',NULL,0,b'0',b'0',b'0','system',NOW(),'system',NOW(),b'0'),
(600239,'标签补打','rd:label-print:reprint',3,239,600000,'','','',NULL,0,b'0',b'0',b'0','system',NOW(),'system',NOW(),b'0'),
(600240,'质检查询','rd:quality-inspection:query',3,240,600000,'','','',NULL,0,b'0',b'0',b'0','system',NOW(),'system',NOW(),b'0'),
(600241,'质检创建','rd:quality-inspection:create',3,241,600000,'','','',NULL,0,b'0',b'0',b'0','system',NOW(),'system',NOW(),b'0'),
(600242,'质检提交','rd:quality-inspection:submit',3,242,600000,'','','',NULL,0,b'0',b'0',b'0','system',NOW(),'system',NOW(),b'0'),
(600243,'质检审核','rd:quality-inspection:approve',3,243,600000,'','','',NULL,0,b'0',b'0',b'0','system',NOW(),'system',NOW(),b'0'),
(600244,'入库单查询','rd:inbound-order:query',3,244,600000,'','','',NULL,0,b'0',b'0',b'0','system',NOW(),'system',NOW(),b'0'),
(600245,'入库单创建','rd:inbound-order:create',3,245,600000,'','','',NULL,0,b'0',b'0',b'0','system',NOW(),'system',NOW(),b'0'),
(600246,'入库单提交','rd:inbound-order:submit',3,246,600000,'','','',NULL,0,b'0',b'0',b'0','system',NOW(),'system',NOW(),b'0'),
(600247,'入库单复核','rd:inbound-order:verify',3,247,600000,'','','',NULL,0,b'0',b'0',b'0','system',NOW(),'system',NOW(),b'0'),
(600248,'入库单过账','rd:inbound-order:post',3,248,600000,'','','',NULL,0,b'0',b'0',b'0','system',NOW(),'system',NOW(),b'0'),
(600249,'入库单撤销','rd:inbound-order:reverse',3,249,600000,'','','',NULL,0,b'0',b'0',b'0','system',NOW(),'system',NOW(),b'0');

INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT 600001, id, 'system', NOW(), 'system', NOW(), b'0', 1 FROM `system_menu` WHERE id IN (600000,600010,600100,600101,600102,600103,600104,600105,600224,600500,600501)
UNION ALL SELECT 600002, id, 'system', NOW(), 'system', NOW(), b'0', 1 FROM `system_menu` WHERE id IN (600000,600010,600200,600201,600202,600203,600204,600205,600206,600207,600208,600209,600210,600211,600212,600213,600214,600220,600221,600222,600223,600224,600260,600261,600262,600263,600264,600265,600266,600267,600400,600401,600402,600403,600404,600405,600406,600500,600501,600502,600503)
UNION ALL SELECT 600003, id, 'system', NOW(), 'system', NOW(), b'0', 1 FROM `system_menu` WHERE id IN (600000,600010,600224,600300,600301,600302,600303,600304,600305,600306,600310,600311,600312,600313,600400,600401,600402,600403,600404,600405,600406,600500,600501)
UNION ALL SELECT 600004, id, 'system', NOW(), 'system', NOW(), b'0', 1 FROM `system_menu` WHERE id IN (1,108,500,501,1040,1042,1043,1045,600000,600010,600100,600101,600200,600201,600203,600207,600213,600214,600224,600300,600301,600310,600400,600401,600500,600501,600502,600503)
UNION ALL SELECT 600005, id, 'system', NOW(), 'system', NOW(), b'0', 1 FROM `system_menu` WHERE id IN (600000,600010,600100,600101,600200,600201,600203,600207,600213,600214,600220,600224,600300,600301,600310,600400,600401,600500,600501);

INSERT INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 600001,id,'system',NOW(),'system',NOW(),b'0',1 FROM system_menu WHERE id IN (600230,600233,600236,600237,600238,600239,600240,600241,600242,600244,600245,600246)
UNION ALL SELECT 600002,id,'system',NOW(),'system',NOW(),b'0',1 FROM system_menu WHERE id BETWEEN 600230 AND 600249
UNION ALL SELECT 600004,id,'system',NOW(),'system',NOW(),b'0',1 FROM system_menu WHERE id IN (600230,600233,600236,600240,600243,600244,600247,600248,600249)
UNION ALL SELECT 600005,id,'system',NOW(),'system',NOW(),b'0',1 FROM system_menu WHERE id IN (600230,600233,600236,600240,600244);

INSERT INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
(600225,'基础资料','',1,60,600000,'masterdata','ep:files',NULL,NULL,0,b'1',b'1',b'1','system',NOW(),'system',NOW(),b'0'),
(600226,'质量管理','',1,70,600000,'quality','ep:checked',NULL,NULL,0,b'1',b'1',b'1','system',NOW(),'system',NOW(),b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`type`=VALUES(`type`),`sort`=VALUES(`sort`),`parent_id`=VALUES(`parent_id`),`path`=VALUES(`path`),`icon`=VALUES(`icon`),`status`=0,`visible`=b'1',`deleted`=b'0',`update_time`=NOW();

UPDATE `system_menu` SET `name`='产品资料',`type`=2,`sort`=10,`parent_id`=600225,`path`='product',`icon`='ep:goods',`component`='rd/masterdata/product/index',`component_name`='RdProductMaster',`visible`=b'1',`keep_alive`=b'1' WHERE `id`=600230;
UPDATE `system_menu` SET `name`='仓库货位',`type`=2,`sort`=20,`parent_id`=600225,`path`='location',`icon`='ep:location',`component`='rd/masterdata/location/index',`component_name`='RdWarehouseLocation',`visible`=b'1',`keep_alive`=b'1' WHERE `id`=600233;
UPDATE `system_menu` SET `name`='包码库存',`type`=2,`sort`=35,`parent_id`=600200,`path`='package',`icon`='ep:postcard',`component`='rd/stock/package/index',`component_name`='RdPackageUnit',`visible`=b'1',`keep_alive`=b'1' WHERE `id`=600236;
UPDATE `system_menu` SET `name`='质量检验',`type`=2,`sort`=10,`parent_id`=600226,`path`='inspection',`icon`='ep:circle-check',`component`='rd/quality/inspection/index',`component_name`='RdQualityInspection',`visible`=b'1',`keep_alive`=b'1' WHERE `id`=600240;
UPDATE `system_menu` SET `name`='入库单',`type`=2,`sort`=40,`parent_id`=600200,`path`='inbound',`icon`='ep:download',`component`='rd/stock/inbound/index',`component_name`='RdInboundOrder',`visible`=b'1',`keep_alive`=b'1' WHERE `id`=600244;

INSERT IGNORE INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT role_id, menu_id, 'system', NOW(), 'system', NOW(), b'0', 1
FROM (SELECT 600001 role_id,600225 menu_id UNION ALL SELECT 600001,600226 UNION ALL SELECT 600002,600225 UNION ALL SELECT 600002,600226 UNION ALL SELECT 600004,600225 UNION ALL SELECT 600004,600226 UNION ALL SELECT 600005,600225 UNION ALL SELECT 600005,600226) x;

INSERT INTO `system_post` (`id`,`code`,`name`,`sort`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`) VALUES
(600006,'RD_QUALITY','质量检验岗',25,0,'质量数据录入、提交、审核与追溯','system',NOW(),'system',NOW(),b'0',1)
ON DUPLICATE KEY UPDATE `code`=VALUES(`code`),`name`=VALUES(`name`),`status`=0,`deleted`=b'0',`tenant_id`=1;

INSERT INTO `system_role` (`id`,`name`,`code`,`sort`,`data_scope`,`data_scope_dept_ids`,`status`,`type`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`) VALUES
(600006,'质量管理','rd_quality',25,1,'',0,2,'质量检验录入、审核与追溯','system',NOW(),'system',NOW(),b'0',1)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`code`=VALUES(`code`),`status`=0,`type`=2,`deleted`=b'0',`tenant_id`=1;

INSERT IGNORE INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 600006,id,'system',NOW(),'system',NOW(),b'0',1 FROM system_menu
WHERE id IN (600000,600225,600226,600230,600233,600236,600240,600241,600242,600243);

INSERT IGNORE INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 600001,600200,'system',NOW(),'system',NOW(),b'0',1
UNION ALL SELECT 600006,600200,'system',NOW(),'system',NOW(),b'0',1
UNION ALL SELECT 600002,600300,'system',NOW(),'system',NOW(),b'0',1;

INSERT INTO `system_post` (`id`,`code`,`name`,`sort`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`) VALUES
(600007,'RD_WAREHOUSE_OPERATOR','库房操作岗',22,0,'包码扫码、出入库制单与盘点录入','system',NOW(),'system',NOW(),b'0',1)
ON DUPLICATE KEY UPDATE `code`=VALUES(`code`),`name`=VALUES(`name`),`status`=0,`deleted`=b'0',`tenant_id`=1;

INSERT INTO `system_role` (`id`,`name`,`code`,`sort`,`data_scope`,`data_scope_dept_ids`,`status`,`type`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`) VALUES
(600007,'库房操作','rd_warehouse_operator',22,1,'',0,2,'扫码入库、扫码出库和盘点录入','system',NOW(),'system',NOW(),b'0',1)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`code`=VALUES(`code`),`status`=0,`type`=2,`deleted`=b'0',`tenant_id`=1;

INSERT IGNORE INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 600007,id,'system',NOW(),'system',NOW(),b'0',1 FROM system_menu
WHERE id IN (600000,600200,600201,600203,600207,600208,600209,600214,600236,600244,600250,600251,600260,600261,600262,600263,600267);

INSERT INTO `system_post` (`id`,`code`,`name`,`sort`,`status`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`) VALUES
(600008,'RD_FORKLIFT_DRIVER','叉车司机岗',24,0,'接收上架与装车任务、按派单搬运和反馈异常','system',NOW(),'system',NOW(),b'0',1)
ON DUPLICATE KEY UPDATE `code`=VALUES(`code`),`name`=VALUES(`name`),`status`=0,`deleted`=b'0',`tenant_id`=1;

INSERT INTO `system_role` (`id`,`name`,`code`,`sort`,`data_scope`,`data_scope_dept_ids`,`status`,`type`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`) VALUES
(600008,'叉车司机','rd_forklift_driver',24,5,'',0,2,'仅执行分配给本人的上架与装车任务','system',NOW(),'system',NOW(),b'0',1)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`code`=VALUES(`code`),`status`=0,`type`=2,`data_scope`=5,`deleted`=b'0',`tenant_id`=1;

INSERT IGNORE INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 600002,id,'system',NOW(),'system',NOW(),b'0',1 FROM system_menu WHERE id IN (600215,600270,600271,600273)
UNION ALL SELECT 600008,id,'system',NOW(),'system',NOW(),b'0',1 FROM system_menu WHERE id IN (600000,600200,600216,600272)
UNION ALL SELECT 600004,id,'system',NOW(),'system',NOW(),b'0',1 FROM system_menu WHERE id = 600215
UNION ALL SELECT 600005,id,'system',NOW(),'system',NOW(),b'0',1 FROM system_menu WHERE id = 600215;

INSERT IGNORE INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 600002,id,'system',NOW(),'system',NOW(),b'0',1 FROM system_menu WHERE id IN (600250,600251,600252,600253,600254)
UNION ALL SELECT 600004,id,'system',NOW(),'system',NOW(),b'0',1 FROM system_menu WHERE id IN (600252,600254);

INSERT IGNORE INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 600001,id,'system',NOW(),'system',NOW(),b'0',1 FROM system_menu WHERE id IN (600107)
UNION ALL SELECT 600003,id,'system',NOW(),'system',NOW(),b'0',1 FROM system_menu WHERE id IN (600320,600321,600322,600323,600324,600325,600326,600327)
UNION ALL SELECT 600004,id,'system',NOW(),'system',NOW(),b'0',1 FROM system_menu WHERE id IN (600107,600320,600321,600322,600323,600324,600325,600326,600327);

INSERT INTO `system_menu`
(`id`,`name`,`permission`,`type`,`sort`,`parent_id`,`path`,`icon`,`component`,`component_name`,`status`,`visible`,`keep_alive`,`always_show`,`creator`,`create_time`,`updater`,`update_time`,`deleted`) VALUES
(600280,'提货单','rd:pickup-slip:query',2,35,600300,'pickup','ep:document','rd/order/pickup/index','RdPickupSlip',0,b'1',b'1',b'1','system',NOW(),'system',NOW(),b'0'),
(600281,'提货单录入','rd:pickup-slip:create',3,10,600280,'','','',NULL,0,b'0',b'0',b'0','system',NOW(),'system',NOW(),b'0'),
(600282,'出库装车','rd:loading-task:query',2,40,600200,'loading','ep:van','rd/stock/loading/index','RdLoadingDispatch',0,b'1',b'1',b'1','system',NOW(),'system',NOW(),b'0'),
(600283,'装车派单','rd:loading-task:dispatch',3,10,600282,'','','',NULL,0,b'0',b'0',b'0','system',NOW(),'system',NOW(),b'0'),
(600284,'执行装车任务','rd:loading-task:execute',3,20,600282,'','','',NULL,0,b'0',b'0',b'0','system',NOW(),'system',NOW(),b'0'),
(600285,'叉车装车','rd:loading-task:execute',2,41,600200,'driver-loading','lucide:truck','rd/pda/loading/index','RdDriverLoading',0,b'1',b'1',b'1','system',NOW(),'system',NOW(),b'0')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`permission`=VALUES(`permission`),`type`=VALUES(`type`),
`sort`=VALUES(`sort`),`parent_id`=VALUES(`parent_id`),`path`=VALUES(`path`),`icon`=VALUES(`icon`),
`component`=VALUES(`component`),`component_name`=VALUES(`component_name`),`status`=0,
`visible`=VALUES(`visible`),`deleted`=b'0',`update_time`=NOW();
INSERT IGNORE INTO `system_role_menu` (`role_id`,`menu_id`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
SELECT 600003,id,'system',NOW(),'system',NOW(),b'0',1 FROM system_menu WHERE id IN (600280,600281)
UNION ALL SELECT 600002,id,'system',NOW(),'system',NOW(),b'0',1 FROM system_menu WHERE id IN (600280,600282,600283)
UNION ALL SELECT 600004,id,'system',NOW(),'system',NOW(),b'0',1 FROM system_menu WHERE id BETWEEN 600280 AND 600285
UNION ALL SELECT 600008,id,'system',NOW(),'system',NOW(),b'0',1 FROM system_menu WHERE id IN (600284,600285);

-- Preserve sub-second event ordering for new trace records. Existing times are not reconstructed.
ALTER TABLE `rd_package_unit` MODIFY `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
ALTER TABLE `rd_quality_inspection` MODIFY `submit_time` datetime(6) DEFAULT NULL,
  MODIFY `review_time` datetime(6) DEFAULT NULL;
ALTER TABLE `rd_label_print_log` MODIFY `print_time` datetime(6) NOT NULL;
ALTER TABLE `rd_putaway_scan_record` MODIFY `scan_time` datetime(6) NOT NULL;
ALTER TABLE `rd_loading_scan_record` MODIFY `scan_time` datetime(6) NOT NULL;
ALTER TABLE `rd_stock_transaction` MODIFY `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
