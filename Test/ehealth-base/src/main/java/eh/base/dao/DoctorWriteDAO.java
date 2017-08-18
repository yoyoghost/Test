package eh.base.dao;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportWriteDAO;
import eh.entity.base.AuditPrescriptionOrgan;
import eh.entity.base.Doctor;

/**
 * @author jianghc
 * @create 2017-03-21 16:50
 **/
public class DoctorWriteDAO extends HibernateSupportWriteDAO<Doctor> {
    public DoctorWriteDAO() {
        super();
        setEntityName(Doctor.class.getName());
        setKeyField("doctorId");
    }

    @Override
    protected void beforeSave(Doctor doctor) {
        if (doctor.getChief() == null) {
            doctor.setChief(0);//默认设置为非首席医生
        }
        if (doctor.getHaveAppoint() == null) {
            doctor.setHaveAppoint(0);//默认为无号源;
        }
        if (doctor.getOrgan() == null) {
            doctor.setOrgan(0);//设置默认机构
        }
        if (doctor.getSource() == null) {
            doctor.setSource(0);//设置默认为后台导入
        }
    }

    @Override
    protected void afterSave(Doctor doctor) throws DAOException {
        Integer doctorType = doctor.getUserType();
        if (doctorType != null && doctorType.intValue() == 5) {//药师
            AuditPrescriptionOrganDAO auditPrescriptionOrganDAO = DAOFactory.getDAO(AuditPrescriptionOrganDAO.class);
            auditPrescriptionOrganDAO.save(new AuditPrescriptionOrgan(doctor.getDoctorId(), doctor.getOrgan()));
        }


    }

    @Override
    protected void beforeUpdate(Doctor doctor) throws DAOException {
        Integer doctorType = doctor.getUserType();
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Integer oldDoctorType = doctorDAO.get(doctor.getDoctorId()).getUserType();
        AuditPrescriptionOrganDAO auditPrescriptionOrganDAO = DAOFactory.getDAO(AuditPrescriptionOrganDAO.class);
        if (oldDoctorType != null && oldDoctorType.intValue() == 5 && (doctorType == null || doctorType.intValue() != 5)) {
            auditPrescriptionOrganDAO.deleteByDoctorId(doctor.getDoctorId());
        }
        if ((oldDoctorType == null || oldDoctorType.intValue() != 5) && doctorType == null && doctorType.intValue() == 5) {
            auditPrescriptionOrganDAO.save(new AuditPrescriptionOrgan(doctor.getDoctorId(), doctor.getOrgan()));
        }
    }


}
