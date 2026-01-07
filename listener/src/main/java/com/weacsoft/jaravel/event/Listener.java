package com.weacsoft.jaravel.event;

public interface Listener<T extends Event> {
    void handle(T event);
}
