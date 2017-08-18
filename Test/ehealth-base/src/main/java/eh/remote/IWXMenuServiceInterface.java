package eh.remote;

import ctd.util.annotation.RpcService;

import java.util.Map;

/**
 * Created by houxr on 2016/9/11.
 */
public interface IWXMenuServiceInterface {

    /**
     * 根据appId获取对应环境的微信菜单[主页] url
     *
     * @param appId
     * @return
     */
    @RpcService
    public String getWxHomePageUrl(String appId);

    /**
     * 根据appId获取对应公众号的当前菜单
     *
     * @param appId
     * @return
     */
    @RpcService
    public Map<String, Object> getWxMenus(String appId);

    /**
     * 根据appId创建微信公众号的菜单
     *
     * @param appId 需要创建的appId
     * @param jsonMenu json格式的菜单值
     */
    @RpcService
    public Map createWXMenu(String appId, String jsonMenu);

}
