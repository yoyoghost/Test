package eh.base.service;

import com.alibaba.druid.util.StringUtils;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.annotation.RpcService;
import eh.base.dao.AppDomainRpcUrlDAO;
import eh.entity.base.AppDomainRpcUrl;

/**
 * @author jianghc
 * @create 2017-05-16 15:13
 **/
public class AppDomainRpcUrlService {
    private AppDomainRpcUrlDAO appDomainRpcUrlDAO;

    public AppDomainRpcUrlService() {
        this.appDomainRpcUrlDAO = DAOFactory.getDAO(AppDomainRpcUrlDAO.class);
    }


    @RpcService
    public AppDomainRpcUrl getByAppDomainId(String appDomainId){
        if (StringUtils.isEmpty(appDomainId)){
            throw new DAOException(DAOException.VALUE_NEEDED,"appDomainId is require");
        }
        return appDomainRpcUrlDAO.getByAppDomainId(appDomainId);
    }
    @RpcService
    public AppDomainRpcUrl saveOrUpdateUrl(AppDomainRpcUrl rpcUrl) {
        return appDomainRpcUrlDAO.saveOrUpdateUrl(rpcUrl);
    }
    @RpcService
    public void deleteOne(String appDomainId){
        if (StringUtils.isEmpty(appDomainId)){
            throw new DAOException(DAOException.VALUE_NEEDED,"appDomainId is require");
        }
        AppDomainRpcUrl rpcUrl = getByAppDomainId(appDomainId);
        if(rpcUrl==null){
            throw new DAOException("appDomainId is not exist");
        }
        BusActionLogService.recordBusinessLog("AppDomainRpcUrl管理",rpcUrl.getId().toString(),"AppDomainRpcUrl","新增AppDomainRpcUrl【domain:"+rpcUrl.getAppDomainId()+"url:"+rpcUrl.getRpcUrl()+"】");
        appDomainRpcUrlDAO.deleteByAppDomainId(appDomainId);
    }

    @RpcService
    public AppDomainRpcUrl getByAppDomainIdFromCache(String appDomainId){
        return appDomainRpcUrlDAO.getByAppDomainIdFromCache(appDomainId);
    }
}
