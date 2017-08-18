package eh.bus.thread;

import ctd.util.AppContextHolder;
import eh.task.ActionExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 线程池管理
 * company: ngarihealth
 * author: 0184/yu_yun
 * date:2016/6/14.
 */
public abstract class BusEventExecutor implements ActionExecutor{

    public ThreadPoolTaskExecutor getBusTaskExecutor() {
        return AppContextHolder.getBean("busTaskExecutor", ThreadPoolTaskExecutor.class);
    }
}
