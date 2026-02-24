-- Create database if not exists
CREATE DATABASE IF NOT EXISTS `cocla` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS `grafana` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- Create users with access from any host (for flexibility)
CREATE USER IF NOT EXISTS 'root'@'%' IDENTIFIED BY 'root';
GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION;

CREATE USER IF NOT EXISTS 'cocla'@'%' IDENTIFIED BY 'cocla';
GRANT ALL PRIVILEGES ON `cocla`.* TO 'cocla'@'%';

CREATE USER IF NOT EXISTS 'grafana'@'%' IDENTIFIED BY 'grafana';
GRANT ALL PRIVILEGES ON `grafana`.* TO 'grafana'@'%';

-- Also create local access for security (optional but recommended)
CREATE USER IF NOT EXISTS 'root'@'localhost' IDENTIFIED BY 'root';
GRANT ALL PRIVILEGES ON *.* TO 'root'@'localhost' WITH GRANT OPTION;

CREATE USER IF NOT EXISTS 'cocla'@'localhost' IDENTIFIED BY 'cocla';
GRANT ALL PRIVILEGES ON `cocla`.* TO 'cocla'@'localhost';

CREATE USER IF NOT EXISTS 'grafana'@'localhost' IDENTIFIED BY 'grafana';
GRANT ALL PRIVILEGES ON `grafana`.* TO 'grafana'@'localhost';

FLUSH PRIVILEGES;