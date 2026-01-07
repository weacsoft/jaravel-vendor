package com.weacsoft.jaravel.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ListenerService {
    private static ListenerService instance;

    private final Map<Class<? extends Event>, List<Listener<? extends Event>>> listeners;
    private final ExecutorService executorService;
    private boolean queueEnabled;

    private ListenerService() {
        this.listeners = new java.util.concurrent.ConcurrentHashMap<>();
        this.executorService = Executors.newCachedThreadPool();
        this.queueEnabled = false;
    }

    public static ListenerService getInstance() {
        if (instance == null) {
            synchronized (ListenerService.class) {
                if (instance == null) {
                    instance = new ListenerService();
                }
            }
        }
        return instance;
    }

    public <T extends Event> void listen(Class<T> eventClass, Listener<T> listener) {
        listeners.computeIfAbsent(eventClass, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public <T extends Event> void listen(Class<T> eventClass, Listener<T> listener, int priority) {
        List<Listener<? extends Event>> eventListeners = listeners.computeIfAbsent(eventClass, k -> new CopyOnWriteArrayList<>());
        eventListeners.add(priority, listener);
    }

    public void dispatch(Event event) {
        if (event == null) {
            return;
        }

        List<Listener<? extends Event>> eventListeners = listeners.get(event.getClass());
        if (eventListeners == null || eventListeners.isEmpty()) {
            return;
        }

        if (queueEnabled) {
            dispatchAsync(event, eventListeners);
        } else {
            dispatchSync(event, eventListeners);
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Event> void dispatchSync(T event, List<Listener<? extends Event>> eventListeners) {
        for (Listener<? extends Event> listener : eventListeners) {
            try {
                ((Listener<T>) listener).handle(event);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Event> void dispatchAsync(T event, List<Listener<? extends Event>> eventListeners) {
        for (Listener<? extends Event> listener : eventListeners) {
            executorService.submit(() -> {
                try {
                    ((Listener<T>) listener).handle(event);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }

    public void enableQueue() {
        this.queueEnabled = true;
    }

    public void disableQueue() {
        this.queueEnabled = false;
    }

    public boolean isQueueEnabled() {
        return queueEnabled;
    }

    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void clearListeners(Class<? extends Event> eventClass) {
        listeners.remove(eventClass);
    }

    public void clearAllListeners() {
        listeners.clear();
    }
}
