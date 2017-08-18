package eh.base.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcService;
import eh.base.dao.AuditPrescriptionOrganDAO;
import eh.base.dao.DoctorDAO;
import eh.entity.base.AuditPrescriptionOrgan;
import eh.entity.base.Doctor;
import org.apache.commons.lang.ObjectUtils;
import org.apache.log4j.Logger;
import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author jianghc
 * @create 2017-01-11 17:46
 **/
public class AuditPrescriptionOrganService {
    private static final Logger log = Logger.getLogger(AuditPrescriptionOrganService.class);

    private AuditPrescriptionOrganDAO auditPrescriptionOrganDAO;

    public AuditPrescriptionOrganService() {
        auditPrescriptionOrganDAO = DAOFactory.getDAO(AuditPrescriptionOrganDAO.class);
    }

    /**
     * 查询医生所有审方机构
     *
     * @param doctorId
     * @return
     */
    @RpcService
    public List<AuditPrescriptionOrgan> findAllAuditPrescriptionOrganByDoctorId(Integer doctorId) {
        return auditPrescriptionOrganDAO.findByDoctorId(doctorId);
    }

    /**
     * 保存更新审方机构
     * @param doctorId
     * @param organList
     */
    @RpcService
    public void saveOrupdateAuditPrescriptionOrganByDoctorId(Integer doctorId, List<Integer> organList) {
        if (doctorId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "doctorId is requrie");
        }
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Doctor doctor = doctorDAO.getByDoctorId(doctorId);
        if (doctor == null) {
            throw new DAOException("doctorId is not exist");
        }
        if (!ObjectUtils.equals(new Integer(5), doctor.getUserType())) {//人员类别不为药师
            throw new DAOException("doctor is not Pharmacist");
        }
        if (organList == null || organList.size() <= 0) {
            auditPrescriptionOrganDAO.deleteByDoctorId(doctorId);
            return;
        }
        Map<Integer,AuditPrescriptionOrgan> updateMap= new HashMap<Integer, AuditPrescriptionOrgan>();
        for (Integer organId : organList) {
            updateMap.put(organId,new AuditPrescriptionOrgan(doctorId, organId));
        }
        List<AuditPrescriptionOrgan> oldList = auditPrescriptionOrganDAO.findByDoctorId(doctorId);
        if(oldList!=null&&oldList.size()>0){
            for (AuditPrescriptionOrgan apo:oldList){
                if (updateMap.get(apo.getOrganId()) == null) {
                    auditPrescriptionOrganDAO.remove(apo.getId());
                }else{
                    updateMap.put(apo.getOrganId(),apo);
                }
            }
        }
        for (Map.Entry<Integer,AuditPrescriptionOrgan> mApo:updateMap.entrySet()){
            AuditPrescriptionOrgan doApo = mApo.getValue();
            if(doApo.getId()==null){
                auditPrescriptionOrganDAO.save(doApo);
            }
        }
    }

    /**
     * 根据机构号查询能审核的医生列表
     * @param organId
     * @return
     */
    @RpcService
    public List<Integer> findDoctorsByOrganId(Integer organId){
        Assert.notNull(organId,"findDoctorsByOrganId organId is null.");
        return auditPrescriptionOrganDAO.findDoctorsByOrganId(organId);
    }
}
