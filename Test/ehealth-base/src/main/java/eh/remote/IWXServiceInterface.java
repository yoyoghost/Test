package eh.remote;

import ctd.util.annotation.RpcService;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by zhangx on 2016/4/17.
 */
public interface IWXServiceInterface {
    /**
     * 使Session过期
     *
     * @param openId
     */
    @RpcService
    public void invalideSession(String openId);

    /**
     * 获取二维码地址和链接
     * zhongzx
     *
     * @param doctorId
     * @return
     */
    @RpcService
    public HashMap<String, String> getTicketAndUrl(Integer doctorId);

    /**
     * 根据机构微信公众号appId获取二维码地址和链接
     *
     * @param doctorId
     * @return
     */
    @RpcService
    public HashMap<String, String> getTicketAndUrlByAppId(String sceneStr, String appId);

    /**
     * 公众号像某一个用户发送文本消息
     *
     * @param appid
     * @param openid
     * @param msg
     */
    @RpcService
    public void sendMsgToUser(String appid, String openid, String msg);


    @RpcService
    public void reloadConsult(String appId, Integer consultId);

    /**
     * 根据appId获取对应公众号的当前菜单
     *
     * @param appId
     * @return
     */
    @RpcService
    public Map getWxMenus(String appId);


    /**
     * 跨公众号支付时获取微信授权支付回调
     *
     * @param appId
     * @param paramsMap
     * @return
     */
    @RpcService
    public String getWxPageAuthorizeUrl(String appId, Map<String, String> paramsMap);

    /**
     * 组装第三方机构访问单页地址
     *
     * @param paramsMap
     * @return
     */
    @RpcService
    public String getSinglePageUrlForThirdPlat(String appId, String busType, Integer busId, Map<String, String> paramsMap);

    /**
     * 组装单页地址
     *
     * @param appId
     * @param paramsMap
     * @return
     */
    @RpcService
    public String getSinglePageUrl(String appId, Map<String, String> paramsMap);

    /**
     * 获取公众号每日用户统计数据
     */
    @RpcService
    public HashMap<String, Object> getDailyUserByAppId(Integer type, Date bDate, Date eDate, String appId)throws Exception;


    @RpcService
    public HashMap<String, String> createProvisionalQrCode(String scene_id, String appId);
}
