package eh.base.service.doctor;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.dao.DoctorDAO;
import eh.entity.base.Doctor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Date;

/**
 * Created by Luphia on 2017/2/13.
 */
public class UpdateDoctorService {

    /**
     * 更新医生手写签名图片
     *
     * @param doctorId  医生内码
     * @param signImage 手写签名图片
     * @return Boolean
     */
    @RpcService
    public Boolean updateSignImage(int doctorId, int signImage) {
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Doctor doctor = new Doctor();
        doctor.setDoctorId(doctorId);
        doctor.setSignImage(signImage);
        Boolean isUpdate = doctorDAO.updateDoctorByDoctorId(doctor);
        return isUpdate;
    }
}
