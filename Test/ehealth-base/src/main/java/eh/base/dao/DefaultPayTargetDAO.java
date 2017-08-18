package eh.base.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.base.DefaultPayTarget;
import org.apache.log4j.Logger;

import java.util.List;

/**
 * @author jianghc
 * @create 2016-10-12 16:56
 **/
public abstract class DefaultPayTargetDAO extends HibernateSupportDelegateDAO<DefaultPayTarget> {

    public static final Logger log = Logger.getLogger(DefaultPayTargetDAO.class);

    public DefaultPayTargetDAO(){
        super();
        this.setEntityName(DefaultPayTarget.class.getName());
        this.setKeyField("id");
    }

    /**
     * 根据业务类型Id获得业务默认支付方式
     * @param id
     * @return
     */
    @DAOMethod
    public abstract DefaultPayTarget getById(Integer id);

    @DAOMethod
    public abstract DefaultPayTarget getByBusinessTypeKey(String businessTypeKey);

    @DAOMethod(sql=" from DefaultPayTarget order by id")
    public abstract List<DefaultPayTarget> findAll();


}
