package eh.bus.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.bus.EmergencyDoctor;

import java.util.List;

/**
 * Created by Administrator on 2017/6/2 0002.
 */
public abstract class EmergencyDoctorDao extends HibernateSupportDelegateDAO<EmergencyDoctor> {

    public EmergencyDoctorDao() {
        super();
        setEntityName(EmergencyDoctor.class.getName());
        setKeyField("emergencyDoctorId");
    }

    @DAOMethod(sql = "FROM EmergencyDoctor WHERE doctorId=:doctorId AND urgent=1 ORDER BY id DESC")
    public abstract List<EmergencyDoctor> findEmergencyDoctorList(@DAOParam("doctorId") Integer doctorId);

    @DAOMethod(sql = "FROM EmergencyDoctor WHERE doctorId=:doctorId AND emergencyId=:emergencyId ORDER BY id DESC")
    public abstract List<EmergencyDoctor> findEmergencyDoctorListWithDoctorIdAndEmergencyId(
            @DAOParam("doctorId") Integer doctorId,
            @DAOParam("emergencyId") Integer emergencyId);

    @DAOMethod(sql = "FROM EmergencyDoctor WHERE emergencyId=:emergencyId AND type in (0, 2) ORDER BY id DESC")
    public abstract List<EmergencyDoctor> findTransferedEmergencyDoctorListWithEmergencyId(@DAOParam("emergencyId") Integer emergencyId);

    @DAOMethod(sql = "FROM EmergencyDoctor WHERE emergencyId=:emergencyId AND type=:type ORDER BY id DESC")
    public abstract List<EmergencyDoctor> findUrgentTransferedEmergencyDoctorListWithEmergencyId(
            @DAOParam("emergencyId") Integer emergencyId,
            @DAOParam("type") Integer type);
}
