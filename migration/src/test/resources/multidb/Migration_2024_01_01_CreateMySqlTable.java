package com.weacsoft.jaravel.vendor.migration.multidb;

import com.weacsoft.jaravel.vendor.migration.Migration;
import com.weacsoft.jaravel.vendor.migration.MigrationAnnotation;
import com.weacsoft.jaravel.vendor.migration.Schema;

@MigrationAnnotation
public class Migration_2024_01_01_CreateMySqlTable implements Migration {

    @Override
    public String connection() {
        return "mysql";
    }

    @Override
    public void up(Schema schema) {
        schema.create("mysql_table", table -> {
            table.id();
            table.string("name");
        });
    }

    @Override
    public void down(Schema schema) {
        schema.dropIfExists("mysql_table");
    }
}
