package eh.msg.service;

import ctd.net.broadcast.MQHelper;
import ctd.net.broadcast.Observer;
import ctd.net.broadcast.Subscriber;
import ctd.util.AppContextHolder;
import eh.bus.service.consult.OnsConfig;
import eh.msg.bean.EasemobBase;
import eh.msg.bean.EasemobRegiste;
import eh.util.Easemob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

/**
 * Created by shenhj on 2017/7/10.
 */
public class EasemobBaseService {
    private static final Logger log = LoggerFactory.getLogger(EasemobBaseService.class);

    /**
     * 订阅消息
     */
    @PostConstruct
    public void consumer() {
        if (!OnsConfig.onsSwitch) {
            log.info("the onsSwitch is set off, consumer not subscribe.");
            return;
        }
        Subscriber subscriber = MQHelper.getMqSubscriber();
        subscriber.attach(OnsConfig.easemobTopic, new Observer<EasemobBase>() {
            @Override
            public void onMessage(EasemobBase msg) {
                if (msg instanceof EasemobRegiste) {
                    registe((EasemobRegiste) msg);
                }
            }
        });
    }

    private void registe(EasemobRegiste msg) {
        Easemob.registUser(msg.getUserName(),msg.getPassword());
    }

}
