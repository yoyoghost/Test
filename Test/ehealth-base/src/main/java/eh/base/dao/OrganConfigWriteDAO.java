package eh.base.dao;

import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportWriteDAO;
import eh.entity.base.OrganConfig;

import java.math.BigDecimal;

/**
 * @author jianghc
 * @create 2017-03-02 13:56
 **/
public class OrganConfigWriteDAO extends HibernateSupportWriteDAO<OrganConfig> {
    public OrganConfigWriteDAO() {
        setEntityName(OrganConfig.class.getName());
        setKeyField("organId");
    }

    @Override
    protected void beforeSave(OrganConfig organConfig) throws DAOException {
        organConfig.setPayAhead(organConfig.getPayAhead()==null?-2:organConfig.getPayAhead());//预约支付提前量为不可支付
        organConfig.setPreContract(organConfig.getPreContract()==null?false:organConfig.getPreContract());//不允许预签约
        organConfig.setSigningAhead(organConfig.getSigningAhead()==null?8:organConfig.getSigningAhead());//签约提前量为8
        organConfig.setRecipeDecoctionPrice(organConfig.getRecipeDecoctionPrice()==null?new BigDecimal(-1):organConfig.getRecipeDecoctionPrice());//代煎费-1
        organConfig.setRecipeCreamPrice(organConfig.getRecipeCreamPrice()==null?new BigDecimal(-1):organConfig.getRecipeCreamPrice());//膏方制作费-1
        organConfig.setJfGroupMode(organConfig.getJfGroupMode()==null?0:organConfig.getJfGroupMode());
        organConfig.setJfPayMode(organConfig.getJfPayMode()==null?0:organConfig.getJfPayMode());
        organConfig.setDoctorSortLikeType(organConfig.getDoctorSortLikeType()==null?0:organConfig.getDoctorSortLikeType());
        organConfig.setDisplayMultiDepartsSources(organConfig.getDisplayMultiDepartsSources()==null?false:organConfig.getDisplayMultiDepartsSources());
        organConfig.setDisplaySourceUsedDoctor(organConfig.getDisplaySourceUsedDoctor() == null ? false : organConfig.getDisplaySourceUsedDoctor());
    }

}
