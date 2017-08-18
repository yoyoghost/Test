package eh.remote;

import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;

public class WXConsultStoreLoader {

    @RpcService
    public void reloadConsult(String appId, Integer consultId){
        AppContextHolder.getBean("eh.wxService", IWXServiceInterface.class).reloadConsult(appId, consultId);
    }

}
