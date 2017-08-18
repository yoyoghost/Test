package eh.mpi.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.bus.Agreement;

/**
 * @author renzh
 * @date 2016/9/18 0018 下午 14:24
 */
public abstract class AgreeMentDAO extends HibernateSupportDelegateDAO<Agreement> {

    public AgreeMentDAO(){
        super();
        this.setEntityName(Agreement.class.getName());
        this.setKeyField("agreementId");
    }

    @DAOMethod
    public abstract Agreement getByOrganIdAndAgreementType(Integer organId,Integer agreementType);

}
