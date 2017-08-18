package eh.bus.service;

import ctd.persistence.DAOFactory;
import ctd.util.annotation.RpcBean;
import ctd.util.annotation.RpcService;
import eh.bus.dao.CancelAppointReasonDao;
import eh.entity.bus.CancelAppointReason;

import java.util.List;

/**
 * Created by cqian on 2017/6/21.
 */

@RpcBean("cancelAppointReasonService")
public class CancelAppointReasonService {


    @RpcService
    public List findAll() {
        CancelAppointReasonDao cancelAppointReasonDao = DAOFactory.getDAO(CancelAppointReasonDao.class);
        return cancelAppointReasonDao.findAll();
    }

    @RpcService
    public void add(CancelAppointReason t) {
        CancelAppointReasonDao cancelAppointReasonDao = DAOFactory.getDAO(CancelAppointReasonDao.class);
        cancelAppointReasonDao.save(t);
    }


}
