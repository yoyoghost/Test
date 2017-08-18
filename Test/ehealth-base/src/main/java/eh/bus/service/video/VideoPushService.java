package eh.bus.service.video;

import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.dao.DoctorDAO;
import eh.entity.msg.SmsInfo;
import eh.push.SmsPushService;

import java.util.HashMap;
import java.util.List;

/**
 * Created by Luphia on 2016/12/26.
 */
public class VideoPushService {

    @RpcService
    public void pushMsgForAinemo(String roomId, int meetClinicId, int doctorId, String pwd) {
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);

        //发送消息通知
        Integer clientId = null;
        Integer organId = doctorDAO.getOrganByDoctorId(doctorId);
        String busType = "VideoCallMsg";
        String smsType = "VideoCallMsg";
        Integer busId = meetClinicId;
        HashMap<String, Object> map = new HashMap<>();
        map.put("meetClinicId", meetClinicId);
        map.put("doctorId", doctorId);
        map.put("roomId", roomId);
        map.put("pwd", pwd);
        String extendValue = JSONUtils.toString(map);
        SmsInfo info = new SmsInfo();
        info.setBusId(busId);
        info.setBusType(busType);
        info.setClientId(clientId);
        info.setOrganId(organId);
        info.setSmsType(smsType);
        info.setExtendValue(extendValue);
        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
        smsPushService.pushMsgData2OnsExtendValue(info);
    }

    /**
     * 视频二次呼叫推送
     *
     * @param roomId       视频房间号
     * @param meetclinicId 会诊申请单号
     * @param doctorId     呼叫发起医生内码
     * @param doctorIds    呼叫接收医生内码列表
     * @param pwd          视频房间密码
     */
    @RpcService
    public void pushMsgForVideoRecall(String roomId, int meetclinicId, int doctorId, List<Integer> doctorIds, String pwd, String patientName) {
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);

        //发送消息通知
        Integer clientId = null;
        Integer organId = doctorDAO.getOrganByDoctorId(doctorId);
        String busType = "VideoRecallMsg";
        String smsType = "VideoRecallMsg";
        Integer busId = meetclinicId;
        HashMap<String, Object> map = new HashMap<>();
        map.put("meetClinicId", meetclinicId);
        map.put("doctorId", doctorId);
        map.put("doctorIds", doctorIds);
        map.put("roomId", roomId);
        map.put("pwd", pwd);
        map.put("patientName", patientName);
        String extendValue = JSONUtils.toString(map);
        SmsInfo info = new SmsInfo();
        info.setBusId(busId);
        info.setBusType(busType);
        info.setClientId(clientId);
        info.setOrganId(organId);
        info.setSmsType(smsType);
        info.setExtendValue(extendValue);
        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
        smsPushService.pushMsgData2OnsExtendValue(info);
    }
}
