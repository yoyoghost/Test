package eh.bus.service;

import ctd.persistence.DAOFactory;
import ctd.util.annotation.RpcService;
import eh.bus.asyndobuss.AsyncCommonDaoManager;
import eh.bus.asyndobuss.bean.DaoEvent;
import eh.bus.dao.ModuleUseLogDao;
import eh.bus.service.common.CurrentUserInfo;
import eh.entity.bus.ModuleUseLog;
import eh.entity.bus.msg.SimpleWxAccount;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * 用户模块使用记录
 * company: ngarihealth
 * author: 0184/yu_yun
 * date:2016/10/9.
 */
public class ModuleUseLogService {

    private static final Log logger = LogFactory.getLog(ModuleUseLogService.class);

    /**
     * 判断是否首次使用某模块 (该方法根据mpiId来进行判断)
     * @param mpiId
     * @param moduleId
     * @return
     */
    @RpcService
    public boolean isFirstUseModule(String mpiId, Integer moduleId){
        if(StringUtils.isEmpty(mpiId) || null == moduleId){
            logger.error("isFirstUseModule 参数不全");
            return false;
        }

        boolean isFirstUse = true;
        ModuleUseLogDao moduleUseLogDao = DAOFactory.getDAO(ModuleUseLogDao.class);
        long count = moduleUseLogDao.getCountByMpiId(mpiId, moduleId);
        if(count > 0){
            isFirstUse = false;
        }

        String appId = RandomStringUtils.randomAlphanumeric(10);
        String openId = RandomStringUtils.randomAlphanumeric(10);
        SimpleWxAccount simpleWxAccount = CurrentUserInfo.getSimpleWxAccount();
        if(null != simpleWxAccount) {
            appId = simpleWxAccount.getAppId();
            openId = simpleWxAccount.getOpenId();
        }
        ModuleUseLog log = new ModuleUseLog(appId,openId,mpiId,moduleId);
        if(isFirstUse){
            DaoEvent e = new DaoEvent();
            e.setMethodName("save");
            e.setDao(moduleUseLogDao);
            e.addArgsOrdered(log);
            AsyncCommonDaoManager daoManager = AsyncCommonDaoManager.getInstance();
            daoManager.fireEvent(e);
        }
        return isFirstUse;
    }

    /**
     * 医生端首次模块提示使用
     * @param doctorId
     * @param moduleId
     * @return
     */
    @RpcService
    public boolean isFirstUseModuleForDoctor(Integer doctorId, Integer moduleId){
        if(null == doctorId || null == moduleId){
            logger.error("isFirstUseModuleForDoctor 参数不全");
            return false;
        }

        boolean isFirstUse = true;
        ModuleUseLogDao moduleUseLogDao = DAOFactory.getDAO(ModuleUseLogDao.class);
        String docStr = doctorId.toString();
        long count = moduleUseLogDao.getCountByMpiId(docStr, moduleId);
        if(count > 0){
            isFirstUse = false;
        }

        ModuleUseLog log = new ModuleUseLog(docStr,docStr,docStr,moduleId);
        saveOrUpdateLog(isFirstUse,log);
        return isFirstUse;
    }

    /**
     * APP患者端使用   (未使用)
     * 判断是否首次使用某模块 (该方法根据mpiId来进行判断)
     * @param mpiId
     * @param moduleId
     * @return
     */
    @RpcService
    public boolean isFirstUseModuleForApp(String mpiId, Integer moduleId){
        if(StringUtils.isEmpty(mpiId) || null == moduleId){
            logger.error("isFirstUseModuleForApp 参数不全");
            return false;
        }

        boolean isFirstUse = true;
        ModuleUseLogDao moduleUseLogDao = DAOFactory.getDAO(ModuleUseLogDao.class);
        long count = moduleUseLogDao.getCountByMpiId(mpiId, moduleId);
        if(count > 0){
            isFirstUse = false;
        }

        ModuleUseLog log = new ModuleUseLog(mpiId,mpiId,mpiId,moduleId);
        saveOrUpdateLog(isFirstUse,log);

        return isFirstUse;
    }

    private void saveOrUpdateLog(boolean isFirstUse, ModuleUseLog log){
        ModuleUseLogDao moduleUseLogDao = DAOFactory.getDAO(ModuleUseLogDao.class);
        if(isFirstUse){
            moduleUseLogDao.save(log);
        }else{
            //非首次，取消记录模块使用次数
//            moduleUseLogDao.addOne(log);
        }
    }

    /**
     * 只检验是否第一次使用该模块，不加一
     * @return
     */
    @RpcService
    public boolean checkIsFirstUseModule(String mpiId,Integer moduleId){
        if(StringUtils.isEmpty(mpiId) || null == moduleId){
            logger.error("checkIsFirstUseModule 参数不全");
            return false;
        }

        boolean isFirstUse = true;
        ModuleUseLogDao moduleUseLogDao = DAOFactory.getDAO(ModuleUseLogDao.class);
        long count = moduleUseLogDao.getCountByMpiId(mpiId, moduleId);
        if(count > 0){
            isFirstUse = false;
        }
        return isFirstUse;
    }
}
