package org.jeecgframework.boot.easy_store_boot.app.common;

import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.*;

@Component
public class TaskSchedulerService {

    // 可以自定义线程池参数
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    // 存储任务 key -> ScheduledFuture
    private final Map<String, ScheduledFuture<?>> taskMap = new ConcurrentHashMap<>();

    /**
     * 启动延迟任务
     * @param id    任务唯一标识
     * @param delay 延迟时间
     * @param unit  时间单位
     * @param task  任务内容
     */
    public void schedule(String id, long delay, TimeUnit unit, Runnable task) {
        cancel(id); // 避免重复任务
        ScheduledFuture<?> future = scheduler.schedule(task, delay, unit);
        taskMap.put(id, future);

    }

    /**
     * 取消任务
     */
    public void cancel(String id) {

        ScheduledFuture<?> future = taskMap.get(id);
        if (future != null && !future.isDone()) {
            boolean cancelled = future.cancel(false);
            if (cancelled) {
                System.out.println("任务 [" + id + "] 已取消");
            }
        }
        taskMap.remove(id);
    }

    /**
     * 判断任务是否已调度
     */
    public boolean isScheduled(String id) {
        ScheduledFuture<?> future = taskMap.get(id);
        return future != null && !future.isDone();
    }

    /**
     * 应用关闭时优雅停机
     */
    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
    }
}
