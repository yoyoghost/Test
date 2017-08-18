package eh.bus.asyndobuss.listener;


import ctd.persistence.DAOFactory;
import eh.bus.asyndobuss.bean.BussAcceptEvent;
import eh.bus.asyndobuss.bean.BussCancelEvent;
import eh.bus.asyndobuss.bean.BussCreateEvent;
import eh.bus.asyndobuss.bean.BussFinishEvent;
import eh.bus.homepage.dao.PendingTaskDAO;

/**
 * 业务处理事件基类
 * company: ngarihealth
 * author: 0184/yu_yun
 * date:2016/12/19
 */
public abstract class DefaultProcessBussEventListener {

    public abstract void createTask(BussCreateEvent event) throws Exception;

    public void acceptTask(BussAcceptEvent event) throws Exception{
        PendingTaskDAO pendingTaskDAO = DAOFactory.getDAO(PendingTaskDAO.class);
        //将该业务所有医生设置为不可见
        //会诊业务中，bussId指的是result对象中的ID
        pendingTaskDAO.updateExceptAcceptDoctor(event.getBussType(),event.getBussId(),event.getAcceptDoctorId());
        pendingTaskDAO.updateAcceptDoctor(event.getBussType(),event.getBussId(),event.getAcceptDoctorId());
    }

    public void cancelTask(BussCancelEvent event) throws Exception{
        PendingTaskDAO pendingTaskDAO = DAOFactory.getDAO(PendingTaskDAO.class);
        pendingTaskDAO.updateCancelBuss(event.getBussType(),event.getBussId());
    }

    public void finishTask(BussFinishEvent event) throws Exception{
        PendingTaskDAO pendingTaskDAO = DAOFactory.getDAO(PendingTaskDAO.class);
        pendingTaskDAO.updateFinishBuss(event.getBussType(),event.getBussId());
    }

    public abstract Integer getBussType();
}
