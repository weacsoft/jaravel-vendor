package com.weacsoft.jaravel.cache.migration;

public class CacheMigrationGenerator {

    public static void generateCacheTableMigration(String outputDirectory, String packageName) {
        String className = "CreateCacheTable";
        try {
            Class<?> generatorClass = Class.forName("com.weacsoft.migration.runner.MigrationGenerator");
            Class<?> migrationTypeClass = Class.forName("com.weacsoft.migration.runner.MigrationGenerator$MigrationType");
            Object migrationType = migrationTypeClass.getField("CREATE").get(null);
            generatorClass.getMethod("generate", String.class, String.class, migrationTypeClass, String.class)
                    .invoke(null, outputDirectory, packageName, migrationType, className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("database-migration dependency not found. Please add it to your pom.xml.", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate cache table migration", e);
        }
    }
}
