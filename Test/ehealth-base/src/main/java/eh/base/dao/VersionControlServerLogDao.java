package eh.base.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.base.VersionControlServerLog;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Timestamp;

/**
 * company: ngarihealth
 * author: 0184/yu_yun
 * date:2016/4/22.
 */
public abstract class VersionControlServerLogDao extends HibernateSupportDelegateDAO<VersionControlServerLog> {

    private static final Log logger = LogFactory.getLog(VersionControlServerLogDao.class);

    public VersionControlServerLogDao(){
        super();
        this.setEntityName(VersionControlServerLog.class.getName());
        this.setKeyField("id");
    }

    @DAOMethod
    public abstract VersionControlServerLog getByAppIdAndOpenId(String appId, String openId);

    @DAOMethod(sql = "update VersionControlServerLog set version=:version,updateDate=:updateDate where id=:id")
    public abstract void updateVersionInfo(@DAOParam("id") Integer id,
                                           @DAOParam("version") String version,
                                           @DAOParam("updateDate") Timestamp updateDate);
}
