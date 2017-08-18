package eh.op.service;

import com.alibaba.druid.util.StringUtils;
import ctd.mvc.weixin.reply.support.ArticleContent;
import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.entity.wx.WXConfig;
import eh.entity.wx.WeChatAutoMsg;
import eh.op.dao.WXConfigsDAO;
import eh.op.dao.WeChatAutoMsgDAO;

import java.util.List;

/**
 * @author jianghc
 * @create 2017-02-21 14:11
 **/
public class WeChatAutoMsgService {
    private WeChatAutoMsgDAO weChatAutoMsgDAO;

    public WeChatAutoMsgService() {
        weChatAutoMsgDAO = DAOFactory.getDAO(WeChatAutoMsgDAO.class);
    }


    @RpcService
    public WeChatAutoMsg getAutoMsgContent(String appId, Integer replyType) {
        if (appId == null || StringUtils.isEmpty(appId.trim())) {
            throw new DAOException(DAOException.VALUE_NEEDED, " appId is require");
        }
        if (replyType == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, " replyType is require");
        }
        if(replyType.intValue()==5||replyType.intValue()==6){
            throw new DAOException( " replyType is not allowed");
        }
        WXConfigsDAO wxConfigsDAO = DAOFactory.getDAO(WXConfigsDAO.class);
        WXConfig wxConfig = wxConfigsDAO.getByAppID(appId);
        if (wxConfig == null) {
            throw new DAOException("appId is not exist");
        }
        WeChatAutoMsg weChatAutoMsg = weChatAutoMsgDAO.getByConfigIdAndReplyType(wxConfig.getId(), replyType);
        return weChatAutoMsg;
    }

    @RpcService
    public WeChatAutoMsg saveOrUpdateWeChatAutoMsg(WeChatAutoMsg weChatAutoMsg) {
        if (weChatAutoMsg == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, " weChatAutoMsg is require");
        }
        if (weChatAutoMsg.getConfigId() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, " weChatAutoMsg.ConfigId is require");
        }
        if (weChatAutoMsg.getReplyType() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, " weChatAutoMsg.ReplyType is require");
        }
        if (weChatAutoMsg.getMsgType() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, " weChatAutoMsg.MsgType is require");
        }

        String msgContent = weChatAutoMsg.getMsgContent();
        if (msgContent == null || StringUtils.isEmpty(msgContent.trim())) {
            throw new DAOException(DAOException.VALUE_NEEDED, " weChatAutoMsg.MsgContent is require");
        }

        if (weChatAutoMsg.getMsgType().intValue() == 2) {//消息内容为图文
            ArticleContent articleContent = JSONUtils.parse(weChatAutoMsg.getMsgContent(), ArticleContent.class);

            //todo ArticleContent格式要求

        }
        int replyType = weChatAutoMsg.getReplyType().intValue();
        if (replyType == 5 || replyType == 6) {//回复规则类型为关键字
            String replyParam = weChatAutoMsg.getReplyParam();
            replyParam =replyParam==null?"":replyParam.trim();
            if(StringUtils.isEmpty(replyParam)){
                throw new DAOException(DAOException.VALUE_NEEDED, " weChatAutoMsg.ReplyParam is require");
            }
            weChatAutoMsg.setReplyParam(replyParam);
            if (weChatAutoMsg.getId() == null) {//add
                List<WeChatAutoMsg> msgs = weChatAutoMsgDAO.findByConfigIdAndReplyParam(weChatAutoMsg.getConfigId(),replyParam);
                if (msgs != null&&msgs.size()>0){
                    throw new DAOException(" this keyWord is exist ");
                }
                weChatAutoMsg = weChatAutoMsgDAO.save(weChatAutoMsg);

            }else{
                weChatAutoMsg = weChatAutoMsgDAO.update(weChatAutoMsg);
            }
        } else {
            if (weChatAutoMsg.getId() == null) {//add
                if (weChatAutoMsgDAO.getByConfigIdAndReplyType(weChatAutoMsg.getConfigId(), replyType) != null) {
                    throw new DAOException(" weChatAutoMsg is exist");
                }
                weChatAutoMsg = weChatAutoMsgDAO.save(weChatAutoMsg);
            } else {//update
                weChatAutoMsg = weChatAutoMsgDAO.update(weChatAutoMsg);
            }
        }
        return weChatAutoMsg;
    }


    @RpcService
    public void deleteWeChatAutoMsg(Integer id) {
        if (id == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, " id is require");
        }
        WeChatAutoMsg weChatAutoMsg = weChatAutoMsgDAO.get(id);
        if(weChatAutoMsg==null){
            throw new DAOException(" id is not exist");
        }
        weChatAutoMsgDAO.remove(id);
    }

    @RpcService
    public QueryResult<WeChatAutoMsg> queryWeChatAutoMsgs( Integer configId,  Boolean keyFlag,  String keyWord,  Integer start,  Integer  limit){
        return weChatAutoMsgDAO.queryWeChatAutoMsgs(configId,keyFlag,keyWord,start,limit);
    }

    @RpcService
    public WeChatAutoMsg getKeyWordReply(String appId,String keyWord){
        if (appId == null || StringUtils.isEmpty(appId.trim())) {
            throw new DAOException(DAOException.VALUE_NEEDED, " appId is require");
        }
        WXConfigsDAO wxConfigsDAO = DAOFactory.getDAO(WXConfigsDAO.class);
        WXConfig wxConfig = wxConfigsDAO.getByAppID(appId);
        if (wxConfig == null) {
            throw new DAOException("appId is not exist");
        }
        return weChatAutoMsgDAO.getKeyWordReply(wxConfig.getId(),keyWord);
    }




}

