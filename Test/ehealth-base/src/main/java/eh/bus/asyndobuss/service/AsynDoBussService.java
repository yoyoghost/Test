package eh.bus.asyndobuss.service;

import ctd.util.JSONUtils;
import eh.bus.asyndobuss.BussEventManager;
import eh.bus.asyndobuss.bean.*;
import eh.bus.asyndobuss.listener.BussEventSubscribeListener;
import eh.bus.asyndobuss.listener.DefaultProcessBussEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 异步处理业务事件
 * company: ngarihealth
 * author: 0184/yu_yun
 * date:2016/12/19.
 */
public class AsynDoBussService {

    private final Logger logger = LoggerFactory.getLogger(AsynDoBussService.class);

    private final BussEventManager eventManager = new BussEventManager();

    private final Map<Integer,DefaultProcessBussEventListener> eventListener = new HashMap<>();

    public AsynDoBussService() {
        eventManager.addListener(new BussEventSubscribeListener() {
            @Override
            public void createTask(BussCreateEvent event) {
                if(null != event && null != event.getBussType() && null != event.getObject()) {
                    try {
                        eventListener.get(event.getBussType()).createTask(event);
                    } catch (Exception e) {
                        logger.error("createTask error[{}]! event:{}",e.getMessage(),JSONUtils.toString(event));
                    }
                }
            }

            @Override
            public void acceptTask(BussAcceptEvent event) {
                if(null != event && null != event.getBussType() && null != event.getBussId() && null != event.getAcceptDoctorId()) {
                    try {
                        eventListener.get(event.getBussType()).acceptTask(event);
                    } catch (Exception e) {
                        logger.error("acceptTask error[{}]! event:{}",e.getMessage(),JSONUtils.toString(event));
                    }
                }
            }

            @Override
            public void cancelTask(BussCancelEvent event) {
                if(null != event && null != event.getBussType() && null != event.getBussId()) {
                    try {
                        eventListener.get(event.getBussType()).cancelTask(event);
                    } catch (Exception e) {
                        logger.error("cancelTask error[{}]! event:{}",e.getMessage(),JSONUtils.toString(event));
                    }
                }
            }

            @Override
            public void finishTask(BussFinishEvent event) {
                if(null != event && null != event.getBussType() && null != event.getBussId()) {
                    try {
                        eventListener.get(event.getBussType()).finishTask(event);
                    } catch (Exception e) {
                        logger.error("finishTask error[{}]! event:{}",e.getMessage(),JSONUtils.toString(event));
                    }
                }
            }
        });
    }

    public void fireEvent(BussEvent bussEvent){
        eventManager.fireEvent(bussEvent,true);
    }

    public void setEventListener(List<DefaultProcessBussEventListener> listeners){
        for(DefaultProcessBussEventListener l : listeners){
           eventListener.put(l.getBussType(),l);
        }
    }
}
