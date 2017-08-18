package eh.remote;

import ctd.mvc.alilife.ResultMap;
import ctd.util.annotation.RpcService;

import java.util.Map;

/**
 * Created by Administrator on 2017/4/13 0013.
 */
public interface IAliServiceInterface {

    @RpcService
    ResultMap sendCustomerMsgWithCallbackLink(String appId, String openId, String msgContent, Map<String, String> kvMap);

    @RpcService
    ResultMap pushTemplateMessageByMap(String appId, String templateId, String openId, Map<String, String> kvMap, Map<String, Object> data);

    @RpcService
    String packCallbackIndexAddress(String appId, Map<String, String> callbackParamMap);

    @RpcService
    String autoPackCallbackUrlForBus(Map<String, String> callbackParamMap, String appId, String busType, String busId);

}
