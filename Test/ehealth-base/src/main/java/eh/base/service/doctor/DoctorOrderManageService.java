package eh.base.service.doctor;

import com.alibaba.druid.util.StringUtils;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.BeanUtils;
import ctd.util.annotation.RpcService;
import eh.base.dao.DoctorOrderManageDAO;
import eh.entity.base.DoctorOrderManage;

import java.util.List;

/**
 * @author jianghc
 * @create 2017-05-19 09:54
 **/
public class DoctorOrderManageService {
    private DoctorOrderManageDAO doctorOrderManageDAO;

    public DoctorOrderManageService() {
        doctorOrderManageDAO = DAOFactory.getDAO(DoctorOrderManageDAO.class);
    }

    @RpcService
    public DoctorOrderManage saveOrUpdateOne(DoctorOrderManage manage) {
        if (manage == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "manage is require");
        }
        manage.setId(null);
        String configType = manage.getConfigType();
        if (StringUtils.isEmpty(configType)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "manage.ConfigType is require");
        }
        String configId = manage.getConfigId();
        if (StringUtils.isEmpty(configId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "manage.ConfigId is require");
        }
        String busType = manage.getBusType();
        if (StringUtils.isEmpty(busType)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "manage.BusType is require");
        }
        Integer doctorId = manage.getDoctorId();
        if (doctorId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "manage.DoctorId is require");
        }
        manage.setWeight(manage.getWeight() == null ? 0 : manage.getWeight());
        DoctorOrderManage old = doctorOrderManageDAO.getByConfigTypeAndConfigIdAndBusTypeAndDoctorId(configType, configId, busType, doctorId);
        if (old == null) {//add
            return doctorOrderManageDAO.save(manage);
        } else {//update
            BeanUtils.map(manage, old);
            return doctorOrderManageDAO.update(old);
        }
    }

    @RpcService
    public void deleteOne(Integer id) {
        if (id == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "id is require");
        }
        DoctorOrderManage old = doctorOrderManageDAO.get(id);
        if (old == null) {
            throw new DAOException("id is not exist");
        }
        doctorOrderManageDAO.remove(id);
    }

    @RpcService
    public DoctorOrderManage getBye(Integer id) {
        if (id == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "id is require");
        }
        return doctorOrderManageDAO.get(id);
    }

    @RpcService
    public List<DoctorOrderManage> findByConfigTypeAndConfigIdAndBusType(String configType, String configId, String busType) {
        if (StringUtils.isEmpty(configType)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "configType is require");
        }
        if (StringUtils.isEmpty(configId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "ConfigId is require");
        }
        if (StringUtils.isEmpty(busType)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "BusType is require");
        }
        return doctorOrderManageDAO.findByConfigTypeAndConfigIdAndBusType(configType, configId, busType);
    }

}