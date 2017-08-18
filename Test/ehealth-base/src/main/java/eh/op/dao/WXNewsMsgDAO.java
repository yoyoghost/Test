package eh.op.dao;

import ctd.account.UserRoleToken;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.wx.WXNewsMsg;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Date;
import java.util.List;

/**
 * Created by houxr on 2016/9/15.
 */
public abstract class WXNewsMsgDAO extends HibernateSupportDelegateDAO<WXNewsMsg> {

    private static final Log logger = LogFactory.getLog(WXNewsMsgDAO.class);

    public WXNewsMsgDAO() {
        super();
        this.setEntityName(WXNewsMsg.class.getName());
        this.setKeyField("wxNewsId");
    }


    @DAOMethod
    public abstract WXNewsMsg getByWxNewsId(Integer wxNewsId);

    /**
     * 新建 wxNewsMsg
     *
     * @param wxNewsMsg
     * @return
     * @author houxr
     */
    public WXNewsMsg addOneWXNewsMsg(WXNewsMsg wxNewsMsg) {
        if (StringUtils.isEmpty(wxNewsMsg.getAppId())) {
            new DAOException(DAOException.VALUE_NEEDED, "AppID is required!");
        }
        if (StringUtils.isEmpty(wxNewsMsg.getArticleTitle())) {
            new DAOException(DAOException.VALUE_NEEDED, "ArticleTitle is required!");
        }
        if (StringUtils.isEmpty(wxNewsMsg.getArticleBrief())) {
            new DAOException(DAOException.VALUE_NEEDED, "ArticleBrief is required!");
        }
        if (StringUtils.isEmpty(wxNewsMsg.getArticleUrl())) {
            new DAOException(DAOException.VALUE_NEEDED, "ArticleUrl is required!");
        }
        if (wxNewsMsg.getWxNewsId() != null) {
            new DAOException("wxNewsMsg is exist!");
        }
        wxNewsMsg.setCreateDate(new Date());
        wxNewsMsg.setStatus(1);//默认创建有效

        UserRoleToken urt = UserRoleToken.getCurrent();
        String name = urt.getUserName();//创建人姓名
        Integer urtIs = urt.getId();//userrolesId
        wxNewsMsg.setUserId(urtIs);
        wxNewsMsg.setUserName(name);

        WXNewsMsg target = save(wxNewsMsg);
        if (target.getWxNewsId() > 0) {
            return target;
        }
        return null;
    }


    /**
     * 根据appId 查找 微信自动回复图文消息
     *
     * @param appId
     * @return
     */
    @DAOMethod(sql = "from WXNewsMsg where appId=:appId", limit = 0)
    public abstract List<WXNewsMsg> findAllWXNewsMsgByAppId(@DAOParam("appId") String appId);


    /**
     * 过期原来使用 图文消息
     *
     * @param wxNewsId
     * @return
     */
    @DAOMethod(sql = "update WXNewsMsg set status=:status where wxNewsId =:wxNewsId")
    public abstract void updateWXNewsMsgByStatus(@DAOParam("wxNewsId") int wxNewsId, @DAOParam("status") int status);

    /**
     * 根据appId 查找有效的 微信自动回复图文消息
     *
     * @param appId
     * @param status
     * @return
     */
    @DAOMethod(sql = "from WXNewsMsg where appId=:appId and status=:status", limit = 8)
    public abstract List<WXNewsMsg> findWXNewsMsgByAppIdAndStatus(@DAOParam("appId") String appId,
                                                                  @DAOParam("status") Integer status);


}
