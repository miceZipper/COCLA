CREATE DATABASE IF NOT EXISTS `cocla` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `grafana` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'root'@'%' IDENTIFIED BY 'root';
GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION;

CREATE USER IF NOT EXISTS 'cocla'@'%' IDENTIFIED BY 'cocla';
GRANT ALL PRIVILEGES ON `cocla`.* TO 'cocla'@'%';

CREATE USER IF NOT EXISTS 'grafana'@'%' IDENTIFIED BY 'grafana';
GRANT ALL PRIVILEGES ON `grafana`.* TO 'grafana'@'%';

CREATE USER IF NOT EXISTS 'root'@'localhost' IDENTIFIED BY 'root';
GRANT ALL PRIVILEGES ON *.* TO 'root'@'localhost' WITH GRANT OPTION;

CREATE USER IF NOT EXISTS 'cocla'@'localhost' IDENTIFIED BY 'cocla';
GRANT ALL PRIVILEGES ON `cocla`.* TO 'cocla'@'localhost';

CREATE USER IF NOT EXISTS 'grafana'@'localhost' IDENTIFIED BY 'grafana';
GRANT ALL PRIVILEGES ON `grafana`.* TO 'grafana'@'localhost';

FLUSH PRIVILEGES;

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
SET time_zone = "+00:00";

USE `cocla`;

-- 1. Справочник сущностей
CREATE TABLE `entities` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `entity_id` varchar(255) NOT NULL,
  `entity_name` varchar(255) DEFAULT NULL,
  `entity_handle` varchar(255) DEFAULT NULL,
  `entity_type` enum('Player','Creature','Environment') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_id` (`entity_id`),
  KEY `idx_entity_name` (`entity_name`),
  KEY `idx_entity_type` (`entity_type`),
  KEY `idx_entity_handle` (`entity_handle`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `entities` (`id`, `entity_id`, `entity_name`, `entity_handle`, `entity_type`) 
VALUES (0, '0', 'Environment', NULL, 'Environment');

-- 2. Справочник способностей
CREATE TABLE `powers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `power_id` varchar(100) NOT NULL,
  `power_name` varchar(255) DEFAULT NULL,
  `attack_type` varchar(100) DEFAULT NULL,
  `is_manual` tinyint(1) DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_power_id` (`power_id`),
  KEY `idx_power_name` (`power_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Справочник типов воздействия
CREATE TABLE `impact_types` (
  `id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `name` (`name`),
  KEY `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Основная таблица логов (с оптимизированными индексами)
CREATE TABLE `combat_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `log_timestamp` datetime NOT NULL,
  `source_id` bigint DEFAULT NULL,
  `summoner_id` bigint DEFAULT NULL,
  `victim_id` bigint DEFAULT NULL,
  `power_id` bigint DEFAULT NULL,
  `actual_impact` float DEFAULT 0,
  `pure_impact` float DEFAULT 0,
  `raw_line` text,
  `line_hash` char(64) DEFAULT NULL,
  `file_name` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `line_hash` (`line_hash`),
  -- Одиночные индексы для JOIN'ов
  KEY `idx_timestamp` (`log_timestamp`),
  KEY `idx_source` (`source_id`),
  KEY `idx_victim` (`victim_id`),
  KEY `idx_power` (`power_id`),
  KEY `idx_summoner` (`summoner_id`),
  KEY `idx_line_hash` (`line_hash`),
  -- Составные индексы для фильтрации по времени + группировке
  KEY `idx_timestamp_source` (`log_timestamp`, `source_id`),
  KEY `idx_timestamp_victim` (`log_timestamp`, `victim_id`),
  KEY `idx_timestamp_power` (`log_timestamp`, `power_id`),
  -- Составные индексы для быстрого поиска по ID сущностей
  KEY `idx_source_timestamp` (`source_id`, `log_timestamp`),
  KEY `idx_victim_timestamp` (`victim_id`, `log_timestamp`),
  -- Составные индексы для агрегации actual_impact
  KEY `idx_time_source_impact` (`log_timestamp`, `source_id`, `actual_impact`),
  KEY `idx_time_victim_impact` (`log_timestamp`, `victim_id`, `actual_impact`),
  -- Внешние ключи
  CONSTRAINT `fk_combat_source` FOREIGN KEY (`source_id`) REFERENCES `entities` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_combat_summoner` FOREIGN KEY (`summoner_id`) REFERENCES `entities` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_combat_victim` FOREIGN KEY (`victim_id`) REFERENCES `entities` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_combat_power` FOREIGN KEY (`power_id`) REFERENCES `powers` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Связка логов с типами воздействия
CREATE TABLE `combat_log_impacts` (
  `log_id` bigint NOT NULL,
  `impact_type_id` int NOT NULL,
  PRIMARY KEY (`log_id`,`impact_type_id`),
  KEY `idx_log` (`log_id`),
  KEY `idx_impact` (`impact_type_id`),
  CONSTRAINT `combat_log_impacts_ibfk_1` FOREIGN KEY (`log_id`) REFERENCES `combat_logs` (`id`) ON DELETE CASCADE,
  CONSTRAINT `combat_log_impacts_ibfk_2` FOREIGN KEY (`impact_type_id`) REFERENCES `impact_types` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;