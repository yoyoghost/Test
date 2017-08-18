package eh.bus.dao;

import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.base.dao.DeviceDAO;
import eh.base.dao.DoctorDAO;
import eh.entity.bus.CheckRequest;
import eh.entity.msg.SmsInfo;
import eh.msg.service.CustomContentService;
import eh.msg.service.MsgPushService;
import eh.msg.service.SessionDetailService;
import eh.push.SmsPushService;
import org.apache.log4j.Logger;

import java.util.HashMap;

public class CheckReqMsg {
    public static final Logger log = Logger.getLogger(CheckReqMsg.class);

    private static int bussType = 7;
    private static int targetType = 1;

    /**
     * 检查相关系统消息及推送
     *
     * @param checkRequestId 检查申请单号
     * @param flag           标志--0预约失败1爽约2退款3报告发布
     * @throws ControllerException
     */
    @RpcService
    public static void checkSysMsgAndPush(int checkRequestId, int flag) {
        DeviceDAO dao = DAOFactory.getDAO(DeviceDAO.class);
        CheckRequestDAO checkRequestDAO = DAOFactory
                .getDAO(CheckRequestDAO.class);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        CheckRequest cr = checkRequestDAO.get(checkRequestId);
        String tel = doctorDAO.getMobileByDoctorId(cr.getRequestDoctorId());
        String organName = null;
        try {
            organName = DictionaryController.instance()
                    .get("eh.base.dictionary.Organ").getText(cr.getOrganId());
        } catch (ControllerException e) {
            log.error(e.getMessage());
        }
        String checkItemName = cr.getCheckItemName();
        String patientName = cr.getPatientName();
        String msgFirst = "您为" + patientName + "在" + organName + "预约的"
                + checkItemName + "检查";
        String title = null;
        StringBuffer msg = new StringBuffer(msgFirst);
        String msgPush = null;
        switch (flag) {
            case 0:
                title = "预约失败";
                msg.append("可能由于医院系统故障，未预约成功");
                msgPush = "急！您有一条检查预约未被医院接收，速来查看";
                break;
            case 1:
                title = "爽约";
                msg.append("由于患者未及时到医院缴费，已被取消预约");
                break;
            case 2:
                title = "退款";
                msg.append("，患者已做退款处理，如有疑问请及时与患者取得联系~");
                break;
            case 3:
                title = "报告发布";
                msg.append("已出报告，请及时查看哦~");
                msgPush = "亲，您有一份检查报告待验收哦~";
            default:
                break;
        }
        SessionDetailService detailService = new SessionDetailService();
        // 系统消息
        detailService.addSysTextMsgCheckToReqDoc(checkRequestId, tel, title, msg.toString(), null, true);
//        addMsgDetail(checkRequestId, targetType, tel, "text", title,
//                msg.toString(), null, true);
        // 推送
        if (flag == 0 || flag == 3) {
            HashMap<String, Object> msgCustom = CustomContentService.getCheckCustomContent(checkRequestId);
            MsgPushService.pushMsgToDoctor(tel, msgPush, msgCustom);
        }
    }

@RpcService
    public static void sendMsg(int busId, String SmsType, String BusType) {
        //短信
        CheckRequest checkRequest = DAOFactory.getDAO(CheckRequestDAO.class).get(busId);
        SmsInfo info = new SmsInfo();
        info.setBusId(busId);// 业务表主键
        info.setBusType(BusType);// 业务类型
        info.setSmsType(SmsType);
        info.setOrganId(checkRequest.getOrganId());// 短信服务对应的机构， 0代表通用机构

        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
//        smsPushService.pushMsgData2Ons(busId, 0, "CheckRequestSucc", "CheckRequestSucc", null);
        smsPushService.pushMsgData2OnsExtendValue(info);
    }
}
