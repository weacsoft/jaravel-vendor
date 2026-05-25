package com.weacsoft.jaravel.auth;

import com.weacsoft.jaravel.auth.drivers.FileDriver;
import com.weacsoft.jaravel.auth.drivers.MemoryDriver;
import com.weacsoft.jaravel.auth.providers.MemoryProvider;
import com.weacsoft.jaravel.contract.auth.Authenticatable;

import java.nio.file.Paths;

public class Example {

    public static void main(String[] args) {
        MemoryProvider<User> memoryProvider = new MemoryProvider<>();

        memoryProvider.add(new User("1", "张三"));
        memoryProvider.add(new User("2", "李四"));

        MemoryDriver memoryDriver = new MemoryDriver();

        FileDriver fileDriver = new FileDriver(Paths.get("").toAbsolutePath().toString(), "file_driver");
        Auth.addGuard("web", new Guard<>("testGuard", memoryDriver, memoryProvider));
        Auth.addGuard("test", new Guard<>("testGuard2", fileDriver, memoryProvider));

        //系统执行初始化
        Auth.init();
        System.out.println("登录前：");
        print();

        User user = new User("1", "张三");

        Auth.login(user);
        System.out.println("登录默认后：");
        print();
//        Auth.guard("test").login(user);
//        System.out.println("登录test后：");
//        print();
        Auth.logout();
        System.out.println("登出默认后：");
        print();
        //系统结束
        Auth.destroy();
    }

    public static void print() {
        System.out.println("默认门面情况：" + Auth.user());
        System.out.println("默认门面情况：" + Auth.check());

        System.out.println("test门面情况：" + Auth.guard("test").user());
        System.out.println("test门面情况：" + Auth.guard("test").check());
    }

    static class User implements Authenticatable {

        public String id;
        public String name;

        public User(String id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String getAuthIdentifier() {
            return this.id;
        }
    }
}
