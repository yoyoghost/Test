package eh.bus.service.common;

import com.alibaba.fastjson.JSONObject;
import ctd.persistence.DAOFactory;
import ctd.util.converter.ConversionUtils;
import eh.bus.dao.AppointRecordDAO;
import eh.entity.bus.msg.MsgCallbackBusTypeEnum;
import eh.entity.bus.msg.WxCustomerMsg;
import eh.entity.bus.msg.WxMsg;
import eh.entity.bus.msg.WxTemplateMsg;
import eh.utils.ValidateUtil;
import eh.wxpay.service.WxPushMessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Administrator on 2016/8/29 0029.
 * 此方法为微信消息执行服务类，send为发送方法，callback为执行成功回调方法，
 * 也可直接将回调逻辑追加在send方法成功之后，但此种方式可能导致其他问题，比如对异常的处理不当导致重复发送消息等
 */
public class WxMsgSenderService extends AbstractMsgSenderService<WxMsg> {
    private static final Logger log = LoggerFactory.getLogger(WxMsgSenderService.class);

    public void doSend(WxMsg wxMsg) throws Exception {
        boolean result = false;
        if (wxMsg instanceof WxTemplateMsg) { // 模板消息
            WxTemplateMsg msg = (WxTemplateMsg) wxMsg;
            result = new WxPushMessService(wxMsg.getAppId(), wxMsg.getOpenId()).sendWxTemplateMessage(msg);
        } else if (wxMsg instanceof WxCustomerMsg) { // 客服消息
            WxCustomerMsg msg = (WxCustomerMsg) wxMsg;
            result = new WxPushMessService(wxMsg.getAppId(), wxMsg.getOpenId()).sendCustomerMessage(msg);
        }
        afterSend(wxMsg);
//        log.info("[{}] send customer msg[{}] result[{}]", this.getClass().getSimpleName(), JSONObject.toJSONString(wxMsg), result);
    }

    /**
     * 消息发送成功回调方法，
     * 若需要执行回调，需在msg中设置withCallBack为true,同时定义对应的MsgCallbackBusTypeEnum、busParams参数，
     * 并在此方法中取到对应busType进行后续处理
     *
     * @param wxMsg
     */
    public void afterSend(WxMsg wxMsg) {
        if (wxMsg != null && wxMsg.isWithCallBack()) {
            switch (wxMsg.getBusTypeEnum()) {
                case FIVE_NUMBER_REMAIN_REMIND_APPOINT:
                    if (wxMsg.getBusParams() == null) {
                        log.info("[{}] callback [{}] busParams null, wxMsg[{}]", this.getClass().getSimpleName(), MsgCallbackBusTypeEnum.FIVE_NUMBER_REMAIN_REMIND_APPOINT, JSONObject.toJSONString(wxMsg));
                        return;
                    }
                    Integer appointRecordId = ConversionUtils.convert(wxMsg.getBusParams(), Integer.class);
                    DAOFactory.getDAO(AppointRecordDAO.class).updateAppointRecordFiveNumberRemindFlagTrue(appointRecordId);
                    break;
                case ONE_DAY_LEFT_REMIND_APPOINT:
                    if (wxMsg.getBusParams() == null) {
                        log.info("[{}] callback [{}] busParams null, wxMsg[{}]", this.getClass().getSimpleName(), MsgCallbackBusTypeEnum.ONE_DAY_LEFT_REMIND_APPOINT, JSONObject.toJSONString(wxMsg));
                        return;
                    }
                    appointRecordId = ConversionUtils.convert(wxMsg.getBusParams(), Integer.class);
                    DAOFactory.getDAO(AppointRecordDAO.class).updateAppointRecordOneDayLeftRemindFlagTrue(appointRecordId);
                    break;
                default:
//                    log.info("[{}] callback execute default case with param[{}]", this.getClass().getSimpleName(), JSONObject.toJSONString(wxMsg));
                    break;
            }
        }
    }

    protected boolean checkParam(WxMsg wxMsg) {
        if (wxMsg == null) {
            log.info("[{}] checkParam: msg is null", this.getClass().getSimpleName());
            return false;
        }
        if (ValidateUtil.blankString(wxMsg.getAppId()) || ValidateUtil.blankString(wxMsg.getOpenId())) {
            log.info("wxMsg appId or openId is null, wxMsg[{}]", JSONObject.toJSONString(wxMsg));
            return false;
        }
        if (wxMsg instanceof WxTemplateMsg) {
            // 模板消息
            WxTemplateMsg msg = (WxTemplateMsg) wxMsg;
            if (ValidateUtil.notBlankString(msg.getTemplateKey())) {
                return true;
            }
        } else if (wxMsg instanceof WxCustomerMsg) {
            // 客服消息
            WxCustomerMsg msg = (WxCustomerMsg) wxMsg;
            if (ValidateUtil.notBlankString(msg.getContent())) {
                return true;
            }
        } else {
            log.error("{} send msg error! msg not match, param[{}]", this.getClass().getSimpleName(), JSONObject.toJSONString(wxMsg));
        }
        return false;
    }
}
