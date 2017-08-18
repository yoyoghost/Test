package eh.base.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.base.AuditPrescriptionOrgan;

import java.util.List;

/**
 * @author jianghc
 * @create 2017-01-11 17:42
 **/
public abstract class AuditPrescriptionOrganDAO extends HibernateSupportDelegateDAO<AuditPrescriptionOrgan> {

    public AuditPrescriptionOrganDAO() {
        super();
        this.setEntityName(AuditPrescriptionOrgan.class.getName());
        this.setKeyField("id");
    }


    @DAOMethod(limit = 0)
    public abstract List<AuditPrescriptionOrgan> findByDoctorId(Integer doctorId);

    @DAOMethod
    public abstract AuditPrescriptionOrgan getByDoctorIdAndOrganId(Integer doctorId,Integer organId);

    @DAOMethod(sql = "delete from AuditPrescriptionOrgan where doctorId=:doctorId")
    public abstract void deleteByDoctorId(@DAOParam("doctorId") Integer doctorId);

    @DAOMethod(sql = "select a.doctorId from AuditPrescriptionOrgan a, Doctor d where a.doctorId=d.doctorId" +
            " and organId=:organId and d.profession like '73%' ")
    public abstract List<Integer> findDoctorsByOrganId(@DAOParam("organId") Integer organId);

    @DAOMethod(sql = "select organId from AuditPrescriptionOrgan where doctorId=:doctorId")
    public abstract List<Integer> findOrganIdsByDoctorId(@DAOParam("doctorId") Integer doctorId);
}
