package eh.bus.asyndobuss.listener;

import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;
import eh.bus.asyndobuss.bean.DaoEvent;

import java.lang.reflect.InvocationTargetException;

/**
 * Created by shenhj on 2017/7/10.
 */
public interface DaoEventSubscribeListener {
    //使asyncEvent真正异步，没有这注解是个伪异步
    @AllowConcurrentEvents
    @Subscribe
    void doTask(DaoEvent event) throws Exception;
}
