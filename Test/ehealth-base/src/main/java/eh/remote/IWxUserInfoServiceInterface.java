package eh.remote;

import ctd.util.annotation.RpcService;

/**
 * Created by zxx on 2017/7/7 0007.
 */
public interface IWxUserInfoServiceInterface {
    /**
     * 微信用户关注 保存到微信关注用户表
     * @param openId
     */
    @RpcService
    public void subscribeUserInfo(String openId, String appId);

    /**
     * 已关注的用户，取消关注，更新关注状态
     * @param openId
     */
    @RpcService
    public void unSubscribeUserInfo(String openId);
}
