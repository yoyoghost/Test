package eh.base.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.base.OrganProTitle;

import java.util.List;

/**
 * @author jianghc
 * @create 2017-07-13 11:28
 **/
public abstract class OrganProTitleDAO extends HibernateSupportDelegateDAO<OrganProTitle> {

    public OrganProTitleDAO() {
        super();
        setEntityName(OrganProTitle.class.getName());
        setKeyField("Id");
    }

    @DAOMethod
    public abstract List<OrganProTitle> findByOrganId(Integer organId);
    @DAOMethod
    public abstract OrganProTitle getByOrganIdAndProTitleId(Integer organId,Integer proTitleId);



}
