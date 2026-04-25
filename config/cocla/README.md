# COCLA Configuration Files

- `config.properties.example` - Example configuration file
  Copy to `config.properties` and edit:
  - Database connection settings
  - Log directory path
  - Watch interval

- `schema.sql` - MySQL database schema
  Import this file to create the required tables:
  ```sql
  mysql -u root -p < schema.sql