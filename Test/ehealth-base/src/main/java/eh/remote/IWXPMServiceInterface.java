package eh.remote;

import ctd.util.annotation.RpcService;
import eh.entity.bus.pay.BusTypeEnum;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by zhangx on 2016/4/17.
 */
public interface IWXPMServiceInterface {
    /**
     * 发送推送消息
     *
     * @param appId
     * @param templateId
     * @param openId
     * @param linkUrl
     * @param paramMap
     * @return
     */
    @RpcService
    public String pushMessage(String appId, String templateId, String openId, String linkUrl, HashMap<String, String> paramMap);

    /**
     * 发送客服消息
     *
     * @param appId
     * @param openId
     * @param msgContent
     * @return
     */
    @RpcService
    String sendCustomerMsg(String appId, String openId, String msgContent);

    /**
     * 发送带有微信网页授权回调地址的《客服》消息  <a href="">消息</a>
     *
     * @param appId
     * @param openId
     * @param msgContent
     * @param kvMap
     * @return
     */
    @RpcService
    String sendCustomerMsgWithCallbackLink(String appId, String openId, String msgContent, Map<String, String> kvMap);

    /**
     * 发送多个链接的客服消息
     * @param appId
     * @param openId
     * @param msgContent
     * @param kvMapList
     * @return
     */
    @RpcService
    String sendCustomerMsgWithListLink(String appId, String openId, String msgContent, List<Map<String, String>> kvMapList);

    /**
     * 发送带有微信网页授权回调地址的《模板》消息
     *
     * @param appId
     * @param templateId
     * @param openId
     * @param kvMap      为null或size=0时，自动封装为主页回调地址
     * @param paramMap
     * @return
     */
    @RpcService
    public String pushMessageWithCallbackLink(String appId, String templateId, String openId, Map<String, String> kvMap, HashMap<String, String> paramMap);


    /**
     * 根据appId获取对应公众号素材库中永久图文素材消息
     *
     * @param appId
     * @return
     */
    @RpcService
    public Map findListMediasByStartAndLimit(String appId, int start, int limit);

    /**
     * 根据mediaId获取微信素材库中的永久图文消息
     *
     * @param appId
     * @param mediaId
     * @return
     */
    @RpcService
    public Map findMediaByMediaId(String appId, String mediaId);

    @RpcService
    public String sendCustomerNewsMsg(String appId, String openId, String newsContent,Map<String, String> urlMap);

    /**
     * 第三方支付成功页面同步回调地址获取
     * @return
     */
    @RpcService
    public String getPayRedirectUrl(Map<String, String> callbackParamMap, String appId, BusTypeEnum busTypeEnum, String busId);
}
