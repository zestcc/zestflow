ZestFlow 公网试玩部署包（Admin + Demo 同机）
==========================================

MySQL（已预填，与线上一致）
---------------------------
  实例：127.0.0.1:2882  用户：root
  库名：
    zestflow_admin          — Admin 包
    zestflow_app_bussiness  — Demo 业务/链
    zestflow_app_log        — Demo 日志/Collector

  口令见 config/application-demo.yml（含特殊字符已加引号）

登录 Admin
----------
  用户名：zestflow
  密码：  zestflow
  demo profile 下不会强制首次改密（新装与升级后自动关闭 must_change_password）

令牌
----
  两包 config/application-secrets.yml 已对齐，与当前线上一致，一般无需再改。

Flyway
----
  demo 启动自动 repair + migrate，升级 jar 后直接 restart，无需手改 MySQL。
  仅当自动修复仍失败时，才用 config/repair-flyway-admin.sql（应急）。

Flyway
----
  Admin / Demo 业务库均在 demo 启动时自动 repair + migrate，升级 jar 后直接 restart。
  Demo 业务库会补 chain_key 等增量列；日志库自动跑 init.sql（IF NOT EXISTS）。

启动顺序
--------
  1. ./start-admin.sh start   （Admin 包）
  2. ./start-demo.sh start    （Demo 包）

构建
----
  mvn package -pl zestflow-admin,zestflow-demo -am -Pdemo-dist -DskipTests
