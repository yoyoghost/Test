package eh.base.service.doctor;

import ctd.persistence.DAOFactory;
import ctd.util.annotation.RpcBean;
import ctd.util.annotation.RpcService;
import eh.base.dao.DoctorExtendDAO;
import eh.entity.base.DoctorExtend;

/**
 * Created by zhangyq on 2017/4/12
 */
@RpcBean
public class DoctorExtendService {

    @RpcService
    public DoctorExtend getByDoctorId(int doctorId){
        DoctorExtendDAO doctorExtendDAO=DAOFactory.getDAO(DoctorExtendDAO.class);
        return doctorExtendDAO.getByDoctorId(doctorId);
    }
}
