package eh.bus.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.bus.BusMoneyDistributeProportionConfig;

import java.util.List;

/**
 * @author jianghc
 * @create 2017-04-15 14:41
 **/
public abstract class BusMoneyDistributeProportionConfigDAO
        extends HibernateSupportDelegateDAO<BusMoneyDistributeProportionConfig> {
    public BusMoneyDistributeProportionConfigDAO() {
        super();
        setEntityName(BusMoneyDistributeProportionConfig.class.getName());
        setKeyField("Id");
    }

    /**
     * 按业务类型+机构Id查询分成配置
     * @param busType SubBusType.dic
     * @param organId 机构id
     * @return
     */
    @DAOMethod
    public abstract List<BusMoneyDistributeProportionConfig> findByBusTypeAndOrganId(String busType,
                                                                                     Integer organId);
    @DAOMethod(orderBy = " busType asc,role asc")
    public abstract List<BusMoneyDistributeProportionConfig> findByOrganId(Integer organId);


    @DAOMethod(sql = " delete from BusMoneyDistributeProportionConfig where organId=:organId and busType=:busType")
    public abstract void deleteByOrganIdAndBusType(@DAOParam("organId")Integer organId,@DAOParam("busType")String busType);

}
