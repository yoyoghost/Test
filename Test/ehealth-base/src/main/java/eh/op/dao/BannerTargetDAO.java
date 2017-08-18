
package eh.op.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.msg.BannerTarget;
import org.apache.log4j.Logger;

import java.util.List;

/**
 * Created by houxr on 2016/6/20.
 * banner和Organ之间关系
 */
public abstract class BannerTargetDAO extends HibernateSupportDelegateDAO<BannerTarget> {

    public static final Logger log = Logger.getLogger(BannerTargetDAO.class);

    public BannerTargetDAO() {
        super();
        this.setEntityName(BannerTarget.class.getName());
        this.setKeyField("id");
    }

    @DAOMethod(sql = "From BannerTarget where bannerId=:bannerId" , limit = 0)
    public abstract List<BannerTarget> findBannerTargetByBannerId(@DAOParam("bannerId") Integer bannerId);

    @DAOMethod(sql = "delete from BannerTarget where bannerId=:bannerId")
    public abstract void removeBannerTargetByBannerId(@DAOParam("bannerId") Integer bannerId);

    @DAOMethod(limit = 0)
    public abstract List<BannerTarget> findByOrganId(Integer organId);
}

