package eh.bus.asyndobuss.listener;

import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import eh.base.constant.BussTypeConstant;
import eh.bus.asyndobuss.bean.BussCreateEvent;
import eh.bus.homepage.service.PendingTaskService;
import eh.entity.bus.Transfer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * company: ngarihealth
 * author: 0184/yu_yun
 * date:2016/12/19.
 */
public class TransferEventListener extends DefaultProcessBussEventListener {

    private final Logger logger = LoggerFactory.getLogger(TransferEventListener.class);

    @Override
    public void createTask(BussCreateEvent event) throws Exception{
        logger.info("TransferEventListener createTask :"+ JSONUtils.toString(event));

        Object object = event.getObject();
        if(object instanceof Transfer){
            Transfer transfer = (Transfer)object;
            //保存至首页待处理任务
            PendingTaskService service = AppContextHolder.getBean("eh.pendingTaskService", PendingTaskService.class);
            service.createTransferTask(transfer,event.getOtherInfo());
        }
    }

    @Override
    public Integer getBussType() {
        return BussTypeConstant.TRANSFER;
    }
}
