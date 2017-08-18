package eh.bus.asyndobuss.listener;

import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import eh.base.constant.BussTypeConstant;
import eh.bus.asyndobuss.bean.BussAcceptEvent;
import eh.bus.asyndobuss.bean.BussCreateEvent;
import eh.bus.dao.ConsultDAO;
import eh.bus.homepage.service.PendingTaskService;
import eh.entity.bus.Consult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * company: ngarihealth
 * author: 0184/yu_yun
 * date:2016/12/19.
 */
public class ConsultEventListener extends DefaultProcessBussEventListener{

    private final Logger logger = LoggerFactory.getLogger(ConsultEventListener.class);

    @Override
    public void createTask(BussCreateEvent event) {
        logger.info("ConsultEventListener createTask :"+ JSONUtils.toString(event));

        Object object = event.getObject();
        if(object instanceof Consult) {
            Consult consult = (Consult) object;
            //保存至首页待处理任务
            PendingTaskService service = AppContextHolder.getBean("eh.pendingTaskService", PendingTaskService.class);
            service.createConsultTask(consult,event.getOtherInfo());
        }
    }

    @Override
    public void acceptTask(BussAcceptEvent event) throws Exception {
        ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
        Consult consult = consultDAO.get(event.getBussId());
        if(null != consult){
            if(new Integer(1).equals(consult.getGroupMode())){
                //非抢单模式下不隐藏其他医生咨询单

            }else{
                super.acceptTask(event);
            }
        }
    }

    @Override
    public Integer getBussType() {
        return BussTypeConstant.CONSULT;
    }
}
