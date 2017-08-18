package eh.bus.asyndobuss.listener;

import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import eh.base.constant.BussTypeConstant;
import eh.bus.asyndobuss.bean.BussCancelEvent;
import eh.bus.asyndobuss.bean.BussCreateEvent;
import eh.bus.asyndobuss.bean.BussFinishEvent;
import eh.bus.dao.MeetClinicResultDAO;
import eh.bus.homepage.dao.PendingTaskDAO;
import eh.bus.homepage.service.PendingTaskService;
import eh.entity.bus.MeetClinic;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static ctd.persistence.DAOFactory.getDAO;

/**
 * company: ngarihealth
 * author: 0184/yu_yun
 * date:2016/12/19.
 */
public class MeetClinicEventListener extends DefaultProcessBussEventListener{

    private final Logger logger = LoggerFactory.getLogger(MeetClinicEventListener.class);

    @Override
    public void createTask(BussCreateEvent event) {
        logger.info("MeetClinicEventListener createTask :"+ JSONUtils.toString(event));

        Object object = event.getObject();
        if(object instanceof MeetClinic){
            MeetClinic meetClinic = (MeetClinic)object;
            //保存至首页待处理任务
            PendingTaskService service = AppContextHolder.getBean("eh.pendingTaskService", PendingTaskService.class);
            service.createMeetClinicTask(meetClinic,event.getOtherInfo());
        }
    }

    @Override
    public void cancelTask(BussCancelEvent event) {
        PendingTaskDAO pendingTaskDAO = getDAO(PendingTaskDAO.class);
        if(null != event.getMeetClinicId()){
            //存在申请单ID，则表示对整个申请单的操作
            List<Integer> resultIds = getResultIdByMeetClinicId(event.getMeetClinicId());
            if(CollectionUtils.isNotEmpty(resultIds)){
                for(Integer resultId : resultIds){
                    pendingTaskDAO.updateCancelBuss(event.getBussType(), resultId);
                }
            }
        }else {
            pendingTaskDAO.updateCancelBuss(event.getBussType(), event.getBussId());
        }
    }

    @Override
    public void finishTask(BussFinishEvent event) {
        PendingTaskDAO pendingTaskDAO = getDAO(PendingTaskDAO.class);
        if(null != event.getMeetClinicId()){
            //存在申请单ID，则表示对整个申请单的操作
            List<Integer> resultIds = getResultIdByMeetClinicId(event.getMeetClinicId());
            if(CollectionUtils.isNotEmpty(resultIds)){
                for(Integer resultId : resultIds){
                    pendingTaskDAO.updateFinishBuss(event.getBussType(),resultId);
                }
            }
        }else {
            pendingTaskDAO.updateFinishBuss(event.getBussType(),event.getBussId());
        }
    }

    @Override
    public Integer getBussType() {
        return BussTypeConstant.MEETCLINIC;
    }


    private List<Integer> getResultIdByMeetClinicId(Integer meetClinicId){
        MeetClinicResultDAO meetClinicResultDAO = getDAO(MeetClinicResultDAO.class);
        return meetClinicResultDAO.findResultIdBymeetClinicId(meetClinicId);
    }

}
