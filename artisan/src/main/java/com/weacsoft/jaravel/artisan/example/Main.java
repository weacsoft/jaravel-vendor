package com.weacsoft.jaravel.artisan.example;

import com.weacsoft.jaravel.artisan.Artisan;
import com.weacsoft.jaravel.artisan.Command;

public class Main {
    public static void main(String[] args) {
        Artisan artisan = new Artisan("Jaravel Artisan", "1.0.0");

        artisan.register(new DemoCommand());

        artisan.run(args);
    }
}
