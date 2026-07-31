package com.weacsoft.jaravel.vendor.migration.multidb;

import com.weacsoft.jaravel.vendor.migration.Migration;
import com.weacsoft.jaravel.vendor.migration.MigrationAnnotation;
import com.weacsoft.jaravel.vendor.migration.Schema;

@MigrationAnnotation
public class Migration_2024_01_02_CreateSqliteTable implements Migration {

    @Override
    public String connection() {
        return "sqlite";
    }

    @Override
    public void up(Schema schema) {
        schema.create("sqlite_table", table -> {
            table.id();
            table.string("title");
        });
    }

    @Override
    public void down(Schema schema) {
        schema.dropIfExists("sqlite_table");
    }
}
