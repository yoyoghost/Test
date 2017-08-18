package eh.base.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.base.PayConfig;
import org.apache.log4j.Logger;

import java.util.List;

/**
 * 业务机构支付方式
 *
 * @author jianghc
 * @create 2016-10-13 9:36
 **/
public abstract class PayConfigDAO extends HibernateSupportDelegateDAO<PayConfig> {

    public static final Logger log = Logger.getLogger(PayConfigDAO.class);

    public PayConfigDAO() {
        super();
        this.setEntityName(PayConfig.class.getName());
        this.setKeyField("id");
    }

    /**
     * 获取支付方式
     *
     * @param isDefault（1为默认，2为例外）
     * @return
     */
    @DAOMethod(sql = " from PayConfig ppt where ppt.isDefault =:isDefault and ppt.payType=:payType")
    public abstract List<PayConfig> findByDefault(@DAOParam("isDefault") Integer isDefault, @DAOParam("payType") String payType, @DAOParam(pageStart = true) int start, @DAOParam(pageLimit = true) int limit);


    @DAOMethod(sql = " from PayConfig ppt where ppt.baseOrganID=:baseOrganID and payType=:payType and ppt.isDefault=1")
    public abstract PayConfig getByBaseOrganID(@DAOParam("baseOrganID") Integer baseOrganID,@DAOParam("payType") String payType);

    @DAOMethod(sql = " from PayConfig ppt where ppt.originPayConfigID=:originPayConfigID and ppt.baseOrganID=:baseOrganID and ppt.businessTypeID=:businessTypeID and payType=:payType and ppt.isDefault=2")
    public abstract PayConfig getByOriginPayConfigIDAndBaseOrganIDAndbusinessTypeIDAndPayType(@DAOParam("originPayConfigID") Integer originPayConfigID, @DAOParam("baseOrganID") Integer baseOrganID, @DAOParam("businessTypeID") Integer businessTypeID,@DAOParam("payType")String payType);

    @DAOMethod
    public abstract void deleteById(Integer id);

    @DAOMethod(sql = " select count(id) from PayConfig where isDefault =:isDefault and payType =:payType")
    public abstract Long getCountByDefault(@DAOParam("isDefault") Integer isDefault,@DAOParam("payType") String payType);

    @DAOMethod
    public abstract PayConfig getById(Integer id);

    @DAOMethod(sql = " select new eh.entity.base.PayConfig(payType,payTypeName,originPayConfigID,baseOrganID,baseOrganName) from PayConfig  where isDefault =:isDefault and payType=:payType group by payType,payTypeName,originPayConfigID,baseOrganID,baseOrganName)")
    public abstract List<PayConfig> findByGroup(@DAOParam("isDefault") Integer isDefault,@DAOParam("payType") String payType, @DAOParam(pageStart = true) int start, @DAOParam(pageLimit = true) int limit);

/*
    @DAOMethod(sql = " select baseOrganName from PayConfig  where isDefault =:isDefault group by payType,payTypeName,originPayConfigID,baseOrganID,baseOrganName)")
    public abstract Long getCountByGroup(@DAOParam("isDefault") Integer isDefault);
*/

    @DAOMethod(sql = " from PayConfig where originPayConfigID=:originPayConfigID and baseOrganID=:baseOrganID and payType=:payType")
    public abstract List<PayConfig> findByOriginPayConfigIDAndBaseOrganID(@DAOParam("originPayConfigID")Integer originPayConfigID, @DAOParam("baseOrganID")Integer baseOrganID,@DAOParam("payType")String payType);

    @DAOMethod(sql="delete from PayConfig where originPayConfigID=:originPayConfigID and baseOrganID=:baseOrganID and isDefault=:isDefault and payType=:payType")
    public abstract void deleteConfigsByOriginPayConfigIDAndBaseOrganIDAndIsDefault( @DAOParam("originPayConfigID") Integer originPayConfigID, @DAOParam("baseOrganID") Integer baseOrganID ,@DAOParam("isDefault") Integer isDefault,@DAOParam("payType")String payType);

}
