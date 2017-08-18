package eh.bus.service.common;

import com.alibaba.fastjson.JSONObject;
import ctd.account.Client;
import ctd.account.session.ClientSession;
import ctd.account.thirdparty.ThirdPartyMapping;
import ctd.mvc.weixin.WXApp;
import ctd.mvc.weixin.entity.OAuthWeixinMP;
import ctd.mvc.weixin.entity.OAuthWeixinMPDAO;
import ctd.persistence.DAOFactory;
import ctd.persistence.support.impl.thirdparty.ThirdPartyMappingDao;
import ctd.util.context.Context;
import eh.base.dao.DeviceDAO;
import eh.entity.bus.msg.SimpleThird;
import eh.entity.bus.msg.SimpleWxAccount;
import eh.entity.bus.vo.SimpleWxClient;
import eh.utils.ValidateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Created by Administrator on 2016/8/26 0026.
 */
public class CurrentUserInfo {
    private static final Logger log = LoggerFactory.getLogger(CurrentUserInfo.class);

    /**
     * 获取当前用户对应公众号属性信息 （微信spring.xml）中properties的属性
     * 说明：若未用到appId、homeFile、signatureToken等辅助信息，请优先使用此接口获取
     * @return
     */
    public static Map<String,String> getCurrentWxProperties(){
        ClientSession clientSession = ClientSession.getCurrent();
        if (clientSession == null){
            return null;
        }
        return clientSession.<Map>get(WXApp.class.getSimpleName());
    }

    /**
     * 获取当前登录用户简单信息，目前只支持Client OS为WX、WEB、WX_WEB三种类型:
     *    WX    返回值为appId 和 openId
     *    WEB   返回值为appKey和 tid
     *    WX_WEB返回值为该用户（urt)对应的最新的微信appId 和 openId
     *
     * @return
     */
    public static SimpleWxAccount getSimpleWxAccount() {
        Client client = getCurrentClient();
        if (client == null) {
            log.error("getSimpleWxAccount client not exists, please check!");
            return null;
        }
        ClientPlatformEnum clientPlatformEnum = ClientPlatformEnum.fromKey(client.getOs());
        SimpleWxAccount simpleWxAccount = null;
        switch (clientPlatformEnum){
            case WEIXIN:
            case ALILIFE:
                simpleWxAccount = (SimpleWxAccount)clientPlatformEnum.parsePlatformInfoFromClient(client);
                break;
            case WEB:
                simpleWxAccount = (SimpleWxAccount)clientPlatformEnum.parsePlatformInfoFromClient(client);
                break;
            case WX_WEB:
                SimpleThird third = (SimpleThird)clientPlatformEnum.parsePlatformInfoFromClient(client);
                if(third==null){
                    log.error("getSimpleWxAccount third is null, client[{}]", JSONObject.toJSONString(client));
                    break;
                }
                ThirdPartyMapping thirdPartyMapping = DAOFactory.getDAO(ThirdPartyMappingDao.class).getByThirdpartyAndTid(third.getAppkey(), third.getTid());
                if(thirdPartyMapping==null){
                    log.error("getSimpleWxAccount thirdPartyMapping is null, client[{}]", JSONObject.toJSONString(client));
                    break;
                }
                List<OAuthWeixinMP> weixinMpList = DAOFactory.getDAO(OAuthWeixinMPDAO.class).findByUrt(thirdPartyMapping.getUrt());
                if(ValidateUtil.blankList(weixinMpList)){
                    log.error("getSimpleWxAccount weixinMpList is null or size 0, client[{}]", JSONObject.toJSONString(client));
                    break;
                }
                Comparator<OAuthWeixinMP> comparator = new Comparator<OAuthWeixinMP>() {
                    @Override
                    public int compare(OAuthWeixinMP o1, OAuthWeixinMP o2) {
                        return o1.getLastModify()<o2.getLastModify()?1:(o1.getLastModify()>o2.getLastModify()?-1:0);
                    }
                };
                Collections.sort(weixinMpList, comparator);
                OAuthWeixinMP latestWeixinMp = weixinMpList.get(0);
                third.setAppId(latestWeixinMp.getAppId());
                third.setOpenId(latestWeixinMp.getOpenId());
                simpleWxAccount = third;
                break;
        }
        return simpleWxAccount;
    }

    /**
     * 获取微信client信息，若不是微信则返回null
     *
     * @return
     */
    public static SimpleWxClient getSimpleWxClient() {
        Client client = getCurrentClient();
        if (client == null) {
            log.error("getSimpleWxAccount client not exists, please check!");
            return null;
        }
        SimpleWxClient wxClient = new SimpleWxClient();
        wxClient.setId(client.getId());
        wxClient.setUserId(client.getUserId());
        wxClient.setUrt(client.getUrt());
        wxClient.setAccessToken(client.getAccesstoken());
        wxClient.setOs(client.getOs());
        wxClient.setToken(client.getToken());
        if (ValidateUtil.notBlankString(wxClient.getOs()) && ClientPlatformEnum.WEIXIN.getKey().equalsIgnoreCase(wxClient.getOs())) {
            String token = wxClient.getToken();
            String[] strings = token.split("@");
            wxClient.setOpenId(strings[0]);
            wxClient.setAppId(strings[1]);
            return wxClient;
        }
        return null;
    }

    /**
     * 获取当前登录client信息，目前只有微信、PC可以直接通过sessionItemManager获取client信息
     * @return
     */
    public static Client getCurrentClient(){
        ClientSession clientSession = ClientSession.getCurrent();
        if (clientSession == null){
            return null;
        }
        Client client = clientSession.get(Context.CLIENT);
        if (client == null){
            int clientId = clientSession.getId();
            client = DAOFactory.getDAO(DeviceDAO.class).getDeviceById(clientId);
            clientSession.set(Context.CLIENT, client);
        }
        return client;
    }


}
