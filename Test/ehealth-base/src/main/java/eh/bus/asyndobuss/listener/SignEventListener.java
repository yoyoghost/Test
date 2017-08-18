package eh.bus.asyndobuss.listener;

import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import eh.base.constant.BussTypeConstant;
import eh.bus.asyndobuss.bean.BussCreateEvent;
import eh.bus.homepage.service.PendingTaskService;
import eh.entity.mpi.SignRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * company: ngarihealth
 * author: 0184/yu_yun
 * date:2016/12/19.
 */
public class SignEventListener extends DefaultProcessBussEventListener{

    private final Logger logger = LoggerFactory.getLogger(SignEventListener.class);

    @Override
    public void createTask(BussCreateEvent event) throws Exception{
        logger.info("SignEventListener createTask :"+ JSONUtils.toString(event));

        Object object = event.getObject();
        if(object instanceof SignRecord) {
            SignRecord signRecord = (SignRecord) object;
            //保存至首页待处理任务
            PendingTaskService service = AppContextHolder.getBean("eh.pendingTaskService", PendingTaskService.class);
            service.createSignTask(signRecord,event.getOtherInfo());
        }
    }

    @Override
    public Integer getBussType() {
        return BussTypeConstant.SIGN;
    }
}
