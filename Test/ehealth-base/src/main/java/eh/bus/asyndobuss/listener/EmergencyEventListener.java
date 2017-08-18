package eh.bus.asyndobuss.listener;

import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import eh.base.constant.BussTypeConstant;
import eh.bus.asyndobuss.bean.BussCreateEvent;
import eh.bus.homepage.service.PendingTaskService;
import eh.entity.bus.Consult;
import eh.entity.bus.Emergency;
import eh.entity.bus.EmergencyDoctor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Administrator on 2017/6/7 0007.
 */
public class EmergencyEventListener extends DefaultProcessBussEventListener {
    private final Logger logger = LoggerFactory.getLogger(EmergencyEventListener.class);

    @Override
    public void createTask(BussCreateEvent event) throws Exception {
        logger.info("createTask :"+ JSONUtils.toString(event));

        Object object = event.getObject();
        if(object instanceof EmergencyDoctor) {
            EmergencyDoctor emergency = (EmergencyDoctor) object;
            //保存至首页待处理任务
            PendingTaskService service = AppContextHolder.getBean("eh.pendingTaskService", PendingTaskService.class);
            service.createEmergencyTask(emergency,event.getOtherInfo());
        }
    }

    @Override
    public Integer getBussType() {
        return BussTypeConstant.REDCALL;
    }
}
