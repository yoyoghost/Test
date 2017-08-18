package eh.base.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.base.DoctorOrderManage;

import java.util.List;

/**
 * @author jianghc
 * @create 2017-05-04 16:47
 **/
public abstract class DoctorOrderManageDAO extends
        HibernateSupportDelegateDAO<DoctorOrderManage> {
    public DoctorOrderManageDAO() {
        super();
        setEntityName(DoctorOrderManage.class.getName());
        setKeyField("Id");
    }

    @DAOMethod(sql = " from DoctorOrderManage where configType=:configType and configId=:configId and busType=:busType and doctorId=:doctorId ")
    public abstract DoctorOrderManage getByConfigTypeAndConfigIdAndBusTypeAndDoctorId(@DAOParam("configType") String configType, @DAOParam("configId") String configId,@DAOParam("busType")  String busType, @DAOParam("doctorId") Integer doctorId);

    @DAOMethod(sql = " from DoctorOrderManage where configType=:configType and configId=:configId and busType=:busType order by weight desc ")
    public abstract List<DoctorOrderManage> findByConfigTypeAndConfigIdAndBusType(@DAOParam("configType") String configType, @DAOParam("configId") String configId,@DAOParam("busType")  String busType);

}
