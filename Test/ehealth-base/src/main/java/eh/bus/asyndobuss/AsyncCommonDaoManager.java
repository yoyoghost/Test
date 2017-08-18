package eh.bus.asyndobuss;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import eh.bus.asyndobuss.bean.DaoEvent;
import eh.bus.asyndobuss.listener.DaoEventSubscribeListener;
import eh.bus.asyndobuss.listener.DefaultDaoEventSubscribeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by shenhj on 2017/7/10.
 */
public class AsyncCommonDaoManager {
    private final Logger logger = LoggerFactory.getLogger(AsyncCommonDaoManager.class);

    private EventBus asyncBus;

    private static AsyncCommonDaoManager instance;

    private static DaoEventSubscribeListener listener = new DefaultDaoEventSubscribeListener();

    private AsyncCommonDaoManager(){}

    public static AsyncCommonDaoManager getInstance(){
        if(null == instance) {
            instance = new AsyncCommonDaoManager();
            instance.init();
        }
        return instance;
    }


    private void init() {
        asyncBus = new AsyncEventBus(new ThreadPoolExecutor(
                5,
                5,
                60,
                TimeUnit.SECONDS,
                new SynchronousQueue(),
                (new ThreadFactoryBuilder()).setNameFormat("AsyncCommonDaoManager-%d").build(),
                new ThreadPoolExecutor.CallerRunsPolicy()));
        asyncBus.register(listener);
    }


    public void fireEvent(DaoEvent event){
        asyncBus.post(event);
    }

}
