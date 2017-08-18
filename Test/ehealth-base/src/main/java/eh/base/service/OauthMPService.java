package eh.base.service;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import ctd.account.Client;
import ctd.mvc.alilife.entity.OauthAliMPDAO;
import ctd.mvc.alilife.entity.OauthMP;
import ctd.mvc.weixin.entity.OAuthWeixinMP;
import ctd.mvc.weixin.entity.OAuthWeixinMPDAO;
import ctd.util.BeanUtils;
import ctd.util.annotation.RpcService;
import eh.bus.service.common.ClientPlatformEnum;
import eh.bus.service.common.CurrentUserInfo;
import eh.utils.ValidateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;

/**
 * Created by Administrator on 2017/3/31 0031.
 */
public class OauthMPService {
    private static final Logger log = LoggerFactory.getLogger(OauthMPService.class);
    private OauthAliMPDAO oauthAliMPDAO;
    private OAuthWeixinMPDAO oAuthWeixinMPDAO;


    public OauthMPService(OauthAliMPDAO oauthAliMPDAO, OAuthWeixinMPDAO oAuthWeixinMPDAO) {
        this.oauthAliMPDAO = oauthAliMPDAO;
        this.oAuthWeixinMPDAO = oAuthWeixinMPDAO;
    }

    @RpcService
    public OauthMP getByAppIdAndOpenId(String appId, String openId) {
        Client client = CurrentUserInfo.getCurrentClient();
        OauthMP oauthMP = null;
        if (client == null) {
            oauthMP = oauthAliMPDAO.getByAppIdAndOpenId(appId, openId);
            if (oauthMP == null) {
                OAuthWeixinMP oAuthWeixinMP = oAuthWeixinMPDAO.getByAppIdAndOpenId(appId, openId);
                oauthMP = BeanUtils.map(oAuthWeixinMP, OauthMP.class);
            }
        } else if (client != null && client.getOs().equalsIgnoreCase(ClientPlatformEnum.ALILIFE.getKey())) {
            oauthMP = oauthAliMPDAO.getByAppIdAndOpenId(appId, openId);
        } else if (client != null && client.getOs().startsWith(ClientPlatformEnum.WEIXIN.getKey())) {
            OAuthWeixinMP oAuthWeixinMP = oAuthWeixinMPDAO.getByAppIdAndOpenId(appId, openId);
            oauthMP = BeanUtils.map(oAuthWeixinMP, OauthMP.class);
        } else {
            log.info("getByAppIdAndOpenId with appId[{}], openId[{}], client[{}]", appId, openId, JSONObject.toJSONString(client));
        }
        return oauthMP;
    }

    @RpcService
    public List<OauthMP> findByUrt(int urt) {
        Client client = CurrentUserInfo.getCurrentClient();
        List<OauthMP> oauthMPList = Lists.newArrayList();
        if (client == null) {
            List<OauthMP> aliList = map(oauthAliMPDAO.findByUrt(urt), OauthMP.class);
            List<OauthMP> wxList = map(oAuthWeixinMPDAO.findByUrt(urt), OauthMP.class);
            oauthMPList.addAll(packMpKey(aliList, ClientPlatformEnum.ALILIFE.getKey()));
            oauthMPList.addAll(packMpKey(wxList, ClientPlatformEnum.WEIXIN.getKey()));
        } else if (client != null && client.getOs().equalsIgnoreCase(ClientPlatformEnum.ALILIFE.getKey())) {
            List<OauthMP> aliList = map(oauthAliMPDAO.findByUrt(urt), OauthMP.class);
            oauthMPList.addAll(packMpKey(aliList, ClientPlatformEnum.ALILIFE.getKey()));
        } else if (client != null && client.getOs().startsWith(ClientPlatformEnum.WEIXIN.getKey())) {
            List<OauthMP> wxList = map(oAuthWeixinMPDAO.findByUrt(urt), OauthMP.class);
            oauthMPList.addAll(packMpKey(wxList, ClientPlatformEnum.WEIXIN.getKey()));
        } else {
            log.info("findByUrt with urt[{}], client[{}]", urt, JSONObject.toJSONString(client));
        }
        return oauthMPList;
    }

    private Collection<? extends OauthMP> packMpKey(List<OauthMP> oauthMpList, String key) {
        if (ValidateUtil.notBlankList(oauthMpList)) {
            for (OauthMP o : oauthMpList) {
                o.setMpKey(key);
            }
        }
        return oauthMpList;
    }

    private static <V, T> List<T> map(List<V> sourceList, Class<T> destinationClass) {
        List<T> destList = Lists.newArrayList();
        if (ValidateUtil.blankList(sourceList)) {
            return destList;
        }
        for (V source : sourceList) {
            if (source != null) {
                T dest = BeanUtils.map(source, destinationClass);
                destList.add(dest);
            }
        }
        return destList;
    }


}
