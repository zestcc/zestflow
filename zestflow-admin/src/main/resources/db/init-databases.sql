-- ZestFlow Docker 部署辅助脚本：创建额外数据库
-- zestflow_admin 由 MYSQL_DATABASE 环境变量自动创建

CREATE DATABASE IF NOT EXISTS `zestflow_test_bussiness` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `zestflow_test_log` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
