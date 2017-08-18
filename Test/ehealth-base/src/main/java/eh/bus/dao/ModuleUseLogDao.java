package eh.bus.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.bus.ModuleUseLog;
import org.apache.log4j.Logger;

import java.sql.Timestamp;
import java.util.Calendar;

/**
 * company: ngarihealth
 * author: 0184/yu_yun
 * date:2016/10/9.
 */
public abstract class ModuleUseLogDao extends
        HibernateSupportDelegateDAO<ModuleUseLog> {

    private static final Logger logger = Logger.getLogger(ModuleUseLogDao.class);

    public ModuleUseLogDao() {
        super();
        this.setEntityName(ModuleUseLog.class.getName());
        this.setKeyField("id");
    }

    @DAOMethod(sql = "FROM ModuleUseLog log WHERE log.mpiId=:mpiId and log.appId=:appId and log.openId=:openId " +
            " and log.moduleType = :moduleType")
    public abstract ModuleUseLog getLogByParam(@DAOParam("appId") String appId, @DAOParam("openId") String openId,
                                               @DAOParam("mpiId") String mpiId, @DAOParam("moduleType") Integer moduleType);

    @DAOMethod(sql = "SELECT count(*) FROM ModuleUseLog log WHERE log.mpiId = :mpiId and log.moduleType = :moduleType")
    public abstract long getCountByMpiId(@DAOParam("mpiId") String mpiId, @DAOParam("moduleType") Integer moduleType);

    public ModuleUseLog save(ModuleUseLog log) {
        log.setUpdateDate(new Timestamp(Calendar.getInstance().getTime().getTime()));
        log.setTotal(1);
        return super.save(log);
    }

    /**
     * 对某个模块使用次数+1
     *
     * @param log 对象
     */
    public void addOne(ModuleUseLog log) {
        Timestamp now = new Timestamp(Calendar.getInstance().getTime().getTime());
        if (null != log.getId()) {
            log.setUpdateDate(now);
            log.setTotal(log.getTotal() + 1);
            update(log);
        } else {
            ModuleUseLog dbLog = this.getLogByParam(log.getAppId(), log.getOpenId(), log.getMpiId(), log.getModuleType());
            if (null != dbLog) {
                dbLog.setUpdateDate(now);
                dbLog.setTotal(dbLog.getTotal() + 1);
                update(dbLog);
            } else {
                save(log);
            }
        }
    }

}
