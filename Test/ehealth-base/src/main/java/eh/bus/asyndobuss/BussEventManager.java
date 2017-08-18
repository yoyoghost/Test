package eh.bus.asyndobuss;

import ctd.util.event.support.AbstractEventManager;
import eh.bus.asyndobuss.bean.BussEvent;
import eh.bus.asyndobuss.listener.BussEventSubscribeListener;

/**
 * company: ngarihealth
 * author: 0184/yu_yun
 * date:2016/12/19
 */
public class BussEventManager extends AbstractEventManager<BussEventSubscribeListener, BussEvent> {

     public BussEventManager(){
         super(true);
     }
}
