package eh.bus.asyndobuss.listener;

import com.google.common.eventbus.Subscribe;
import eh.bus.asyndobuss.bean.BussAcceptEvent;
import eh.bus.asyndobuss.bean.BussCancelEvent;
import eh.bus.asyndobuss.bean.BussCreateEvent;
import eh.bus.asyndobuss.bean.BussFinishEvent;

/**
 * 事件订阅
 * company: ngarihealth
 * author: 0184/yu_yun
 * date:2016/12/19.
 */
public interface BussEventSubscribeListener {
    @Subscribe
    void createTask(BussCreateEvent event);

    @Subscribe
    void acceptTask(BussAcceptEvent event);

    @Subscribe
    void cancelTask(BussCancelEvent event);

    @Subscribe
    void finishTask(BussFinishEvent event);
}
