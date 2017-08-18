package eh.cdr.dao;

import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.JSONUtils;
import eh.entity.cdr.ClinicEmrDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

public abstract class ClinicEmrDetailDAO extends HibernateSupportDelegateDAO<ClinicEmrDetail> {

    private static final Logger logger = LoggerFactory.getLogger(ClinicEmrDetailDAO.class);

    public ClinicEmrDetailDAO() {
        super();
        this.setEntityName(ClinicEmrDetail.class.getName());
        this.setKeyField("clinicEmrDetailId");
    }

    /**
     * 保持从his导入的门诊病历详情数据
     */
    public void saveClinicEmrDetail(ClinicEmrDetail clinicEmrDetail) {
        logger.info("保存his导入的门诊病历详情数据服务:" + JSONUtils.toString(clinicEmrDetail));

        if (clinicEmrDetail.getClinicEmrId() == null) {
            throw new DAOException("ClinicEmrId is required!");
        }
        if (clinicEmrDetail.getCreateDt() == null) {
            clinicEmrDetail.setCreateDt(new Date());
        }
        clinicEmrDetail.setLastModify(new Date());
        clinicEmrDetail.setStatus(1);

        save(clinicEmrDetail);
    }


}
