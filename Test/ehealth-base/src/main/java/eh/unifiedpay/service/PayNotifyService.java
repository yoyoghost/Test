package eh.unifiedpay.service;

import ctd.util.annotation.RpcService;

import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * Description:
 * User: xiangyf
 * Date: 2017-04-25 10:30.
 */
public interface PayNotifyService {

    @RpcService
    String notify(Map<String , String> map);
}
