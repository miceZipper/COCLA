# 🚀 Champions Online Combat Log Analyzer (COCLA)

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![GitHub downloads](https://img.shields.io/github/downloads/micezipper/cocla/total)](https://github.com/micezipper/cocla/releases)

| | Branch | Status | Description |
|---|--------|--------|-------------|
| ✅ | **Current build** (`release/1.0-rc1`) | [![GitHub release](https://img.shields.io/github/v/release/micezipper/cocla?include_prereleases&sort=semver&cacheSeconds=0)](https://github.com/micezipper/cocla/releases) | The most recent published build |
| 🚧 | **Development** (`release/1.0-rc2`) | [![Build RC2](https://github.com/micezipper/cocla/actions/workflows/maven-rc2.yml/badge.svg?branch=release/1.0-rc2)](https://github.com/micezipper/cocla/actions/workflows/maven-rc2.yml) | Active development, bug fixes |
| 🏷️ | **Release** (`main`) | [![Release](https://github.com/micezipper/cocla/actions/workflows/maven.yml/badge.svg)](https://github.com/micezipper/cocla/actions/workflows/maven.yml) | Stable releases |

Headless Java application for forwarding Champions Online combat logs to MySQL database for Grafana visualization.
![Dashboard Preview](https://github.com/miceZipper/COCLA/blob/release/1.0-rc1/screen-cocla1.png)
## ✨ Features

- 📊 **Real-time combat log monitoring** - Automatically detects and parses new log entries
- 🗄️ **MySQL database storage** - Optimized schema with proper indexing
- 📈 **Grafana integration** - Pre-configured dashboard with 4 panels:
  - DPS Bar Chart (top damagers)
  - DPS Time Series (damage over time)
  - Damage Taken Bar Chart (top tanks)
  - Damage Taken Time Series (damage received over time)
- 🔧 **Simple configuration** - Single config.properties file
- 🎯 **Impact type tracking** - Critical, Kill, Dodge, Immune and more
- 👥 **Entity classification** - Automatically distinguishes Players from Creatures

## 📋 Prerequisites

| Component | Version | Required | Notes |
|-----------|---------|----------|-------|
| ☕ **Java** | 15+ | ✅ Yes | Runtime environment |
| 🏗️ **Maven** | 3.9.x | ❌ No | Only for building from source |
| 🗄️ **MySQL/MariaDB** | 8.0+ / 10.5+ | ✅ Yes | Database storage |
| 📊 **Grafana** | 12.2.1+ | ✅ Yes | Visualization dashboards |
| 🎮 **Champions Online** | any | ✅ Yes | Game with combat logging enabled |

## 🚀 Quick Start
Coming soon in v1.2

## 🛠️ Manual installation Guide

### 1. Database Setup
```bash
# Create database and users
mysql -u root -p < config/mariadb/users.sql

# Create COCLA tables
mysql -u root -p cocla < config/cocla/schema.sql
```

### 2. Build the Application
```bash
# Using Maven
mvn clean package

# The JAR file will be in target/cocla-*.jar
```

### 3. Configure COCLA
```bash
# Copy example configuration
cp config/cocla/config.properties.example config.properties

# Edit with your settings
nano config.properties  # or any text editor
```

### 4. Grafana Setup
1. Navigate to your Grafana instance (e.g., http://localhost:3000)
2. Add a MySQL data source:
   - Name: ``mysql-cocla``
   - Host: ``localhost:3306``
   - Database: ``cocla``
   - User: ``cocla``
   - Password: ``cocla``
   - Session timezone: ``+00:00`` (required to display time correctly)
3. Import the dashboard:
   1. Click + → **Import**
   2. Upload ``config/grafana/dashboard.json``
   3. Select ``mysql-cocla`` as the data source
  
### 5. Run COCLA
```bash
java -jar target/cocla-*.jar
```

### 6. Start Logging
1. Launch Champions Online
2. Put the following command in the chatbox and hit Enter
```bash
/CombatLog 1
```
3. COCLA will automatically detect and process new logs
4. if you want to stop logging put the following command
```bash
/CombatLog 0
```
