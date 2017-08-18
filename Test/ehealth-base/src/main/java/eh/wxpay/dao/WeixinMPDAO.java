package eh.wxpay.dao;

import ctd.mvc.weixin.entity.OAuthWeixinMP;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;

import java.util.List;

/**
 * @author jianghc
 * @create 2017-02-14 13:37
 **/
public abstract class WeixinMPDAO extends HibernateSupportDelegateDAO<OAuthWeixinMP> {
    public WeixinMPDAO() {
        super();
        setEntityName(OAuthWeixinMP.class.getName());
        setKeyField("oauthId");
    }

    @DAOMethod(sql = " select a,b from OAuthWeixinMP a, eh.entity.wx.WXConfig b where a.appId=b.appID and a.userId=:userId order by createDt   ")
    public abstract List<Object> findObjByUserId(@DAOParam("userId") String userId);



    @DAOMethod(sql = "from OAuthWeixinMP where urt=:urt and appId=:appId order by id desc")
    public abstract List<OAuthWeixinMP> findByUrtAndAppId(@DAOParam("urt") int urt,@DAOParam("appId") String appId);


}
