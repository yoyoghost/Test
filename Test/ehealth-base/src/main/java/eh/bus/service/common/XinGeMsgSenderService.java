package eh.bus.service.common;

import com.alibaba.fastjson.JSONObject;
import eh.base.constant.SystemConstant;
import eh.entity.bus.msg.XinGeMsg;
import eh.msg.service.MsgPushService;
import eh.utils.LocalStringUtil;
import eh.utils.ValidateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

/**
 * 信鸽消息发送服务
 * Created by Administrator on 2016/8/30 0030.
 */
public class XinGeMsgSenderService extends AbstractMsgSenderService<XinGeMsg> {
    private static final Logger log = LoggerFactory.getLogger(XinGeMsgSenderService.class);

    public void doSend(XinGeMsg msg) throws Exception {
        HashMap<String, Object> resultMap = null;
        if (SystemConstant.ROLES_DOCTOR.equalsIgnoreCase(msg.getReceiverRole())) {
            resultMap = MsgPushService.pushMsgToDoctor(msg.getUserId(), msg.getContent(), msg.getCustomContent());
        } else if (SystemConstant.ROLES_PATIENT.equalsIgnoreCase(msg.getReceiverRole())) {
            resultMap = MsgPushService.pushMsgToPatient(msg.getUserId(), msg.getContent(), msg.getCustomContent());
        }
        log.info("[{}] doSend resultMap[{}]", this.getClass().getSimpleName(), resultMap);
        if (resultMap == null || (int)resultMap.get("code") != 1) {
            throw new Exception(LocalStringUtil.format("[{}] send failed!", this.getClass().getSimpleName()));
        }
    }

    /**
     * 消息发送成功回调方法，
     * 若需要执行回调，需在msg中设置withCallBack为true,同时定义对应的MsgCallbackBusTypeEnum、busParams参数，
     * 并在此方法中取到对应busType进行后续处理
     *
     * @param msg
     */
    public void afterSend(XinGeMsg msg) {
        if (msg != null && msg.isWithCallBack()) {
            switch (msg.getBusTypeEnum()) {
                default:
//                    log.info("[{}] callback execute default case with param[{}]", this.getClass().getSimpleName(), JSONObject.toJSONString(msg));
                    break;
            }
        }
    }

    protected boolean checkParam(XinGeMsg msg) {
        if (msg == null) {
            log.info("[{}] checkParam: msg is null", this.getClass().getSimpleName());
            return false;
        }
        if (ValidateUtil.blankString(msg.getUserId())
                || ValidateUtil.blankString(msg.getReceiverRole())
                || ValidateUtil.blankString(msg.getContent())) {
            log.info("XinGeMsg userId or receiverRole or content is null, XinGeMsg[{}]", JSONObject.toJSONString(msg));
            return false;
        }
        return true;
    }
}
