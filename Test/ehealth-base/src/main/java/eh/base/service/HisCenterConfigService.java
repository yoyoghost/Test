package eh.base.service;/**
 * Created by Administrator on 2017-05-26.
 */

import com.ngari.base.serviceconfig.mode.RpcServiceReqTO;
import com.ngari.base.serviceconfig.mode.ServiceConfigReqTO;
import com.ngari.base.serviceconfig.service.IHisServiceConfigService;
import com.ngari.base.serviceconfig.service.IRpcServiceInfoService;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;

/**
 * @author zhuangyq
 * @create 2017-05-26 上午 11:33
 **/
public class HisCenterConfigService {

    @RpcService
    public boolean saveHisServiceConfig(ServiceConfigReqTO serviceConfigReqTO){
        IHisServiceConfigService hisServiceConfigService=AppContextHolder.getBean("his.hisServiceConfig",IHisServiceConfigService.class);
        return hisServiceConfigService.saveHisServiceConfig(serviceConfigReqTO);
    }

    @RpcService
    public boolean saveRpcServiceInfo(RpcServiceReqTO rpcServiceReqTO){
        IRpcServiceInfoService rpcServiceInfoService=AppContextHolder.getBean("his.rpcServiceInfoService",IRpcServiceInfoService.class);
        return rpcServiceInfoService.saveRpcServiceInfo(rpcServiceReqTO);
    }
}
