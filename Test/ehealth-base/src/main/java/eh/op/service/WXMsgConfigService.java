package eh.op.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.service.BusActionLogService;
import eh.entity.wx.WXConfig;
import eh.entity.wx.WXMsgConfig;
import eh.entity.wx.WXNewsMsg;
import eh.op.dao.WXConfigsDAO;
import eh.op.dao.WXMsgConfigDAO;
import eh.op.dao.WXNewsMsgDAO;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;

/**
 * Created by houxr on 2016/9/13.
 */
public class WXMsgConfigService {
    private static final Log logger = LogFactory.getLog(WXMsgConfigService.class);

    /**
     * 新建 wxMsgConfig
     *
     * @param wxMsgConfig
     * @return
     * @author houxr
     */
    @RpcService
    public WXMsgConfig addOneWXMsgConfig(WXMsgConfig wxMsgConfig) {
        WXMsgConfigDAO wxMsgConfigDAO = DAOFactory.getDAO(WXMsgConfigDAO.class);
        WXMsgConfig wxMsg = wxMsgConfigDAO.addOneWXMsgConfig(wxMsgConfig);
        BusActionLogService.recordBusinessLog("微信公众号管理", wxMsg.getWxMsgId().toString(), "WXMsgConfig",
                "微信公众号:" + wxMsg.getWxName() + ",appId:" + wxMsg.getAppId() + "添加消息:" + wxMsg.getMsgKey() + ",msg:" + wxMsg.getMsg());
        return wxMsg;
    }

    /**
     * 更新 WXMsgConfig
     *
     * @param wxMsgConfig
     * @return
     * @author houxr
     */
    @RpcService
    public WXMsgConfig updateWXMsgConfig(WXMsgConfig wxMsgConfig) {
        if (StringUtils.isEmpty(wxMsgConfig.getAppId())) {
            new DAOException(DAOException.VALUE_NEEDED, "AppID is required!");
        }

        if (StringUtils.isEmpty(wxMsgConfig.getWxName())) {
            new DAOException(DAOException.VALUE_NEEDED, "WxName is required!");
        }

        WXMsgConfigDAO wxMsgConfigDAO = DAOFactory.getDAO(WXMsgConfigDAO.class);
        WXMsgConfig target = wxMsgConfigDAO.getByWxMsgId(wxMsgConfig.getWxMsgId());
        BeanUtils.map(wxMsgConfig, target);
        BusActionLogService.recordBusinessLog("微信公众号管理", target.getWxMsgId().toString(), "WXMsgConfig",
                "修改微信公众号[" + target.getWxName() + "],[msgKey:" + wxMsgConfig.getMsgKey() + ",msg" + wxMsgConfig.getMsg() + "]改为:"
                        + "[msgKey:" + target.getMsgKey() + ",msg" + target.getMsg() + "]");
        return wxMsgConfigDAO.updateWXMsgConfig(target);
    }

    /**
     * [运营平台] 微信消息查询
     *
     * @param appId
     * @param start
     * @param limit
     * @return
     */
    @RpcService
    public QueryResult<WXMsgConfig> queryWXMsgConfigByStartAndLimit(final String appId, final int start, final int limit) {
        WXMsgConfigDAO wxMsgConfigDAO = DAOFactory.getDAO(WXMsgConfigDAO.class);
        return wxMsgConfigDAO.queryWXConfigByStartAndLimit(appId, start, limit);
    }

    /**
     * 根据appId获取对应公众号的有效文本消息
     *
     * @param appId
     * @return
     */
    @RpcService
    public List<WXMsgConfig> findWXMsgConfigByAppId(final String appId, final Integer status) {
        WXMsgConfigDAO wxMsgConfigDAO = DAOFactory.getDAO(WXMsgConfigDAO.class);
        return wxMsgConfigDAO.findWXMsgConfigByAppIdAndStatus(appId, status);
    }

    /**
     * 根据appId获取对应公众号的有效消息(所有消息包含图文和文本)
     *
     * @param appId
     * @return
     */
    @RpcService
    public List<WXNewsMsg> findWXNewsMsgByAppId(final String appId, final Integer status) {
        WXNewsMsgDAO wxNewsMsgDAO = DAOFactory.getDAO(WXNewsMsgDAO.class);
        return wxNewsMsgDAO.findWXNewsMsgByAppIdAndStatus(appId, status);
    }

    /**
     * 从运营平台后台配置微信 获取有效自动回复文本消息
     *
     * @param newsMsgList
     * @return
     */
    @RpcService
    public List<WXNewsMsg> createReplyWXNewsMsgFromOp(String appId, List<WXNewsMsg> newsMsgList) {
        //logger.info("newsMsgList:" + JSONUtils.toString(newsMsgList));
        if (newsMsgList.size() == 0) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "图文消息不能为空");
        }

        //暂时业务需求支持单个图文自动回复,后期考虑多个图文的时候去掉该判断
        if (newsMsgList.size() > 1) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "自动回复图文消息暂时支持单个图文");
        }

        if (StringUtils.isEmpty(appId)) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "appId不能为空");
        }
        WXNewsMsgDAO wxNewsMsgDAO = DAOFactory.getDAO(WXNewsMsgDAO.class);
        //过期已创建的
        List<WXNewsMsg> oldWxNewsMsgList = wxNewsMsgDAO.findAllWXNewsMsgByAppId(appId);
        for (WXNewsMsg oldwxNewsMsg : oldWxNewsMsgList) {
            wxNewsMsgDAO.updateWXNewsMsgByStatus(oldwxNewsMsg.getWxNewsId(), 0);
        }

        //新建关注图文消息
        for (WXNewsMsg wxNewsMsg : newsMsgList) {
            wxNewsMsg.setAppId(appId);
            wxNewsMsgDAO.addOneWXNewsMsg(wxNewsMsg);
        }
        List<WXNewsMsg> targetList = wxNewsMsgDAO.findWXNewsMsgByAppIdAndStatus(appId, 1);
        WXConfigsDAO wxConfigsDAO = DAOFactory.getDAO(WXConfigsDAO.class);
        WXConfig wxConfig = wxConfigsDAO.getByAppID(appId);
        BusActionLogService.recordBusinessLog("微信公众号管理", "appId:" + appId, "WXNewsMsg",
                "微信公众号[" + wxConfig.getWxName() + "],创建关注图文消息:" + JSONUtils.toString(targetList));
        return targetList;
    }





}
