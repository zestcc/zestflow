ZestFlow Admin 生产部署包
========================

目录结构
--------
  start-admin.sh / start-admin.bat   启动 Admin
  zestflow-admin-{version}.jar
  config/
    application-prod.yml             含 spring.datasource（请改数据库口令）
    application-secrets.yml
    start-admin.env
    secret / registry-token / executor-access-token / collector.access-token
    bootstrap-admin.password
  log/

数据库（自行准备）
------------------
  1. MySQL 8+ 上手工建库，例如：
       CREATE DATABASE zestflow_admin DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
  2. 修改 config/application-prod.yml 中 spring.datasource.url / username / password
     （password 含 ! @ # 等字符须加双引号，如 password: "!Fy@xxx"）
  3. 启动 Admin → Flyway 自动执行 db/migration/V*.sql 建表

  部署包不含 init-db / init.sql / db.env / initData.sql。

快速启动（Linux）
-----------------
  1. JDK 17+、MySQL 已建库且 yml 口令正确
  2. chmod +x start-admin.sh
  3. ./start-admin.sh start

快速启动（Windows）
-------------------
  1. JDK 17+、MySQL 已建库且 yml 口令正确
  2. start-admin.bat start

默认账号
--------
  用户名: admin
  初始口令: config/bootstrap-admin.password

Executor / Collector 令牌
-------------------------
  registry-token / executor-access-token / collector.access-token 见 config/

公网部署前请修改数据库口令、重新生成令牌，并配置 HTTPS 反向代理。
