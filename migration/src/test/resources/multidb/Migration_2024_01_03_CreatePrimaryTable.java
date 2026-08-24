package com.weacsoft.jaravel.vendor.migration.multidb;

import com.weacsoft.jaravel.vendor.migration.Migration;
import com.weacsoft.jaravel.vendor.migration.MigrationAnnotation;
import com.weacsoft.jaravel.vendor.migration.Schema;

@MigrationAnnotation
public class Migration_2024_01_03_CreatePrimaryTable implements Migration {

    // 显式指定 "primary" 连接，不依赖默认连接名
    @Override
    public String connection() {
        return "primary";
    }

    @Override
    public void up(Schema schema) {
        schema.create("primary_table", table -> {
            table.id();
            table.string("label");
        });
    }

    @Override
    public void down(Schema schema) {
        schema.dropIfExists("primary_table");
    }
}
