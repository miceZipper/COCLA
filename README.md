# 🚀 Champions Online Combat Log Analyzer (COCLA)

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java Version](https://img.shields.io/badge/Java-15-blue.svg)](https://adoptium.net/)
[![GitHub release](https://img.shields.io/github/v/release/micezipper/cocla?include_prereleases&sort=semver&cacheSeconds=0)](https://github.com/micezipper/cocla/releases)
[![GitHub downloads](https://img.shields.io/github/downloads/micezipper/cocla/total)](https://github.com/micezipper/cocla/releases)

**Development Branch**
[![Build RC2](https://github.com/micezipper/cocla/actions/workflows/build-rc2.yml/badge.svg?branch=rc2)](https://github.com/micezipper/cocla/actions/workflows/maven-rc2.yml)

**Release**
[![Release](https://github.com/micezipper/cocla/actions/workflows/release.yml/badge.svg)](https://github.com/micezipper/cocla/actions/workflows/maven.yml)

Headless Java application for forwarding Champions Online combat logs to MySQL database for Grafana visualization.

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

## 🚦 Current Status

| Version | Status | Description |
|---------|--------|-------------|
| v1.0 RC1| ✅ Stable | Initial release |
| v1.0 RC2 | 🚧 In Development | Bug fixes and testing |
| v1.0 | 📅 Planned | Full core functionality |
| v1.1 | 📅 Planned | Performance improvements |
| v1.2 | 📅 Planned | Installers and Docker |

## 📋 Prerequisites

- Java 15 or higher
- MySQL 8.0+ or MariaDB 10.5+
- Grafana 9.0+ (optional, for dashboards)
- Champions Online (with combat logging enabled)
