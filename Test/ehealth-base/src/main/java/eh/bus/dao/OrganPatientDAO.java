package eh.bus.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcService;
import eh.entity.bus.OrganPatient;
import org.springframework.util.StringUtils;

/**
 * Created by Administrator on 2016-6-23.
 */
public abstract class OrganPatientDAO extends HibernateSupportDelegateDAO<OrganPatient> {

    public OrganPatientDAO() {
        super();
        this.setEntityName(OrganPatient.class.getName());
        this.setKeyField("id");
    }
    @RpcService
    @DAOMethod
    public abstract OrganPatient getByOrganIDAndCertID(Integer organID,String certID);

    @RpcService
    @DAOMethod(sql = "update OrganPatient set patientID=:patientID where mpiid=:mpiid")
    public abstract void updatePatientIDById(@DAOParam("patientID") String organId ,@DAOParam("mpiid") String mpiid);

    @RpcService
    public void saveOrUpdateOrganPatient(OrganPatient organPatient){
        if(StringUtils.isEmpty(organPatient.getCertID())){
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "CertID is required!");
        }
        if(StringUtils.isEmpty(organPatient.getOrganID())){
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "OrganID is required!");
        }
        if(StringUtils.isEmpty(organPatient.getPatientID())){
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "PatientID is required!");
        }
        OrganPatient organPatient_db = getByOrganIDAndCertID(organPatient.getOrganID(),organPatient.getCertID());
        if(organPatient_db==null){
            save(organPatient);
        }else{
            updatePatientIDById(organPatient_db.getPatientID(),organPatient_db.getPatientID());
        }
    }

}
