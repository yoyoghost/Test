package eh.bus.service.common;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.node.ObjectNode;
import eh.entity.bus.msg.EaseMobMsg;
import eh.util.Easemob;
import eh.utils.ValidateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 环信消息服务类
 * Created by Administrator on 2016/8/30 0030.
 */
public class EaseMobMsgSenderService extends AbstractMsgSenderService<EaseMobMsg> {
    private static final Logger log = LoggerFactory.getLogger(EaseMobMsgSenderService.class);

    public void doSend(EaseMobMsg msg) throws Exception {
        if (!checkParam(msg)) {
            log.info("[{}] send suspend, checkParam false", this.getClass().getSimpleName());
            return;
        }
        ObjectNode resultObjectNode = Easemob.sendSimpleMsg(msg.getSender(), msg.getReceiver(), msg.getContent(), msg.getTargetTypeEnum().getValue(), msg.getExt());
        String resultStr = resultObjectNode.path(msg.getReceiver()).asText();
        log.info("[{}] doSend success");
        if (!"success".equals(resultStr)) {
            throw new Exception("receiver=" + msg.getReceiver() + ", resultStr=" + resultStr);
        }
    }

    /**
     * 消息发送成功回调方法，
     * 若需要执行回调，需在msg中设置withCallBack为true,同时定义对应的MsgCallbackBusTypeEnum、busParams参数，
     * 并在此方法中取到对应busType进行后续处理
     *
     * @param msg
     */
    public void afterSend(EaseMobMsg msg) {
        if (msg != null && msg.isWithCallBack()) {
            switch (msg.getBusTypeEnum()) {
                default:
                    log.info("[{}] callback execute default case with param[{}]", this.getClass().getSimpleName(), JSONObject.toJSONString(msg));
                    break;
            }
        }
    }

    protected boolean checkParam(EaseMobMsg msg) {
        if (msg == null) {
            log.info("[{}] checkParam: msg is null", this.getClass().getSimpleName());
            return false;
        }
        if (ValidateUtil.blankString(msg.getSender())
                || ValidateUtil.blankString(msg.getReceiver())
                || ValidateUtil.blankString(msg.getContent())
                || msg.getTargetTypeEnum() == null) {
            log.info("EaseMobMsg sender or receiver or content or targetType is null, EaseMobMsg[{}]", JSONObject.toJSONString(msg));
            return false;
        }
        return true;
    }
}
