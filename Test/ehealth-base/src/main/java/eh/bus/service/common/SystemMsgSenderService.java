package eh.bus.service.common;

import com.alibaba.fastjson.JSONObject;
import ctd.persistence.DAOFactory;
import eh.entity.bus.msg.SystemMsg;
import eh.entity.msg.Article;
import eh.entity.msg.SessionMessage;
import eh.msg.dao.SessionDetailDAO;
import eh.msg.service.SystemMsgConstant;
import eh.utils.ValidateUtil;
import eh.wxpay.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.List;

/**
 * 系统消息发送服务类
 * Created by Administrator on 2016/8/31 0031.
 */
public class SystemMsgSenderService extends AbstractMsgSenderService<SystemMsg> {
    private static final Logger log = LoggerFactory.getLogger(SystemMsgSenderService.class);
    private static SessionDetailDAO sessionDetailDAO = DAOFactory.getDAO(SessionDetailDAO.class);

    /**
     * 发送（添加）系统消息
     *
     * @param msg 系统消息包装类
     * @return
     */
    public void doSend(SystemMsg msg) {
        if (!checkParam(msg)) {
            log.error("[{}] send msg error! msg check fail, param[{}]", this.getClass().getSimpleName(), JSONObject.toJSONString(msg));
        }
        int urt = fetchUrt(msg);
        String sessionMsgJsonString = packageSessionMsgJsonString(msg.getLoginId(), msg.getMsgType(), msg.getArticles());
        int sessionId = sessionDetailDAO.addSysMessage(msg.getPublisherId(), sessionMsgJsonString, msg.getReceiverType(), urt, msg.getReceiverDeviceType());
        log.info("[{}] doSend sessionId[{}]", this.getClass().getSimpleName(), sessionId);
        msg.setSessionId(sessionId);
    }

    /**
     * 消息发送成功回调方法，
     * 若需要执行回调，需在msg中设置withCallBack为true,同时定义对应的MsgCallbackBusTypeEnum、busParams参数，
     * 并在此方法中取到对应busType进行后续处理
     *
     * @param systemMsg
     */
    @Override
    public void afterSend(SystemMsg systemMsg) {
        if (systemMsg != null && systemMsg.isWithCallBack()) {
            switch (systemMsg.getBusTypeEnum()) {
                default:
//                    log.info("[{}] callback execute default case with param[{}]", this.getClass().getSimpleName(), JSONObject.toJSONString(systemMsg));
                    break;
            }
        }
    }

    private String packageSessionMsgJsonString(String loginId, String msgType, List<Article> articles) {
        SessionMessage sessionMsg = new SessionMessage();
        sessionMsg.setMsgType(msgType);
        sessionMsg.setToUserId(loginId);
        sessionMsg.setCreateTime(new Timestamp(System.currentTimeMillis()));
        sessionMsg.setArticles(articles);
        return JSONObject.toJSONString(sessionMsg);
    }

    private int fetchUrt(SystemMsg msg) {
        Integer urt = null;
        if (msg.getReceiverType() == SystemMsgConstant.SYSTEM_MSG_RECIEVER_TYPE_PATIENT) {
            urt = Util.getUrtByMobileForPatient(msg.getLoginId());
        } else if (msg.getReceiverType() == SystemMsgConstant.SYSTEM_MSG_RECIEVER_TYPE_DOCTOR) {
            urt = Util.getUrtForDoctor(msg.getLoginId());
        } else {
            urt = 0;
        }
        log.info("[{}] send msg urt[{}] with msg[{}]", this.getClass().getSimpleName(), urt, JSONObject.toJSONString(msg));
        int urtId = urt == null ? 0 : urt;
        return urtId;
    }

    protected boolean checkParam(SystemMsg msg) {
        if (msg == null) {
            log.info("[{}] checkParam: msg is null", this.getClass().getSimpleName());
            return false;
        }
        if (ValidateUtil.blankString(msg.getMsgType())
                || ValidateUtil.blankString(msg.getLoginId())) {
            log.info("msgType or loginId is null, msg[{}]", JSONObject.toJSONString(msg));
            return false;
        }
        return true;
    }
}
