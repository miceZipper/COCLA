SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
SET time_zone = "+00:00";

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;


CREATE TABLE `combat_logs` (
  `id` bigint NOT NULL,
  `log_timestamp` datetime NOT NULL,
  `source_name` varchar(255) DEFAULT NULL,
  `source_id` varchar(255) DEFAULT NULL,
  `source_owner` varchar(255) DEFAULT NULL,
  `summoner_name` varchar(255) DEFAULT NULL,
  `summoner_id` varchar(255) DEFAULT NULL,
  `victim_name` varchar(255) DEFAULT NULL,
  `victim_id` varchar(255) DEFAULT NULL,
  `power_name` varchar(255) DEFAULT NULL,
  `power_id` varchar(100) DEFAULT NULL,
  `attack_type` varchar(100) DEFAULT NULL,
  `actual_impact` float DEFAULT NULL,
  `pure_impact` float DEFAULT NULL,
  `raw_line` text,
  `line_hash` char(64) DEFAULT NULL,
  `file_name` varchar(500) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `combat_log_impacts` (
  `log_id` bigint NOT NULL,
  `impact_type_id` int NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `entity_types` (
  `entity_id` varchar(255) NOT NULL,
  `entity_name` varchar(255) NOT NULL,
  `entity_type` enum('Player','Creature') NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `impact_types` (
  `id` int NOT NULL,
  `name` varchar(100) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


ALTER TABLE `combat_logs`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `line_hash` (`line_hash`),
  ADD KEY `idx_timestamp` (`log_timestamp`),
  ADD KEY `idx_source` (`source_name`),
  ADD KEY `idx_victim` (`victim_name`),
  ADD KEY `idx_line_hash` (`line_hash`),
  ADD KEY `idx_source_owner` (`source_owner`),
  ADD KEY `idx_attack_type` (`attack_type`),
  ADD KEY `idx_timestamp_source` (`log_timestamp`,`source_name`),
  ADD KEY `idx_victim_id` (`victim_id`),
  ADD KEY `idx_power` (`power_name`,`power_id`),
  ADD KEY `idx_time_source` (`log_timestamp`,`source_name`,`actual_impact`),
  ADD KEY `idx_time_victim` (`log_timestamp`,`victim_name`,`actual_impact`),
  ADD KEY `idx_source_lookup` (`source_name`,`log_timestamp`),
  ADD KEY `idx_victim_lookup` (`victim_name`,`log_timestamp`);

ALTER TABLE `combat_log_impacts`
  ADD PRIMARY KEY (`log_id`,`impact_type_id`),
  ADD KEY `idx_log` (`log_id`),
  ADD KEY `idx_impact` (`impact_type_id`);

ALTER TABLE `entity_types`
  ADD PRIMARY KEY (`entity_id`),
  ADD KEY `idx_name` (`entity_name`),
  ADD KEY `idx_type` (`entity_type`);

ALTER TABLE `impact_types`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `name` (`name`),
  ADD KEY `idx_name` (`name`);


ALTER TABLE `combat_logs`
  MODIFY `id` bigint NOT NULL AUTO_INCREMENT;

ALTER TABLE `impact_types`
  MODIFY `id` int NOT NULL AUTO_INCREMENT;


ALTER TABLE `combat_log_impacts`
  ADD CONSTRAINT `combat_log_impacts_ibfk_1` FOREIGN KEY (`log_id`) REFERENCES `combat_logs` (`id`) ON DELETE CASCADE,
  ADD CONSTRAINT `combat_log_impacts_ibfk_2` FOREIGN KEY (`impact_type_id`) REFERENCES `impact_types` (`id`) ON DELETE RESTRICT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
