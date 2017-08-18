package eh.bus.service.meetclinic;

import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.dao.DoctorDAO;
import eh.bus.dao.MeetClinicDAO;
import eh.entity.bus.MeetClinic;
import eh.entity.msg.SmsInfo;
import eh.push.SmsPushService;

import java.util.HashMap;
import java.util.List;

/**
 * Created by Luphia on 2016/12/27.
 */
public class MeetClinicPushService {

    @RpcService
    public void requestMeetClinicPush(int meetClinicId, int organId) {
        //发送消息通知
        Integer clientId = null;
        String busType = "MeetClinicRequestMsg";
        String smsType = "MeetClinicRequestMsg";
        Integer busId = meetClinicId;
        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
        smsPushService.pushMsgData2Ons(busId, organId, busType, smsType, clientId);
    }

    @RpcService
    public void endMeetClinicByRequestPush(int meetClinicId, int doctorId, List<HashMap<String, Object>> cancelDoctors,
                                           List<HashMap<String, Object>> endDoctors, List<HashMap<String, Object>> cancelCauseDoctors,
                                           String cancelCause) {
        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);

        //发送消息通知
        Integer clientId = null;
        Integer organId = doctorDAO.getOrganByDoctorId(doctorId);
        String busType = "RequestEndMeetClinicMsg";
        String smsType = "RequestEndMeetClinicMsg";
        Integer busId = meetClinicId;
        String extendValue = null;
        SmsInfo info = new SmsInfo();
        info.setBusId(busId);
        info.setBusType(busType);
        info.setClientId(clientId);
        info.setOrganId(organId);
        info.setSmsType(smsType);
        if (endDoctors != null && !endDoctors.isEmpty()) {
            HashMap<String, Object> map = new HashMap<String, Object>();
            map.put("doctorList", endDoctors);
            extendValue = JSONUtils.toString(map);
            info.setExtendValue(extendValue);
            smsPushService.pushMsgData2OnsExtendValue(info);
        }

        if (cancelDoctors != null && !cancelDoctors.isEmpty()) {
            busType = "RequestCancelMeetMsg";
            smsType = "RequestCancelMeetMsg";
            HashMap<String, Object> map = new HashMap<String, Object>();
            map.put("doctorList", cancelDoctors);
            map.put("cancelCause", cancelCause);
            extendValue = JSONUtils.toString(map);
            info.setBusType(busType);
            info.setSmsType(smsType);
            info.setExtendValue(extendValue);
            smsPushService.pushMsgData2OnsExtendValue(info);
        }

        if (cancelCauseDoctors != null && !cancelCauseDoctors.isEmpty()) {
            busType = "RequestCancelStartMeetMsg";
            smsType = "RequestCancelStartMeetMsg";
            HashMap<String, Object> map = new HashMap<String, Object>();
            map.put("doctorList", cancelCauseDoctors);
            map.put("cancelCause", cancelCause);
            extendValue = JSONUtils.toString(map);
            info.setBusType(busType);
            info.setSmsType(smsType);
            info.setExtendValue(extendValue);
            smsPushService.pushMsgData2OnsExtendValue(info);
        }
    }

    @RpcService
    public void endMeetClinicPush(int meetClinicId, int requestDoctor, boolean teams) {
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);

        //发送消息通知
        Integer clientId = null;
        Integer organId = doctorDAO.getOrganByDoctorId(requestDoctor);
        String busType = "EndMeetClinicMsg";
        String smsType = "EndMeetClinicMsg";
        Integer busId = meetClinicId;
        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("teams", teams);
        String extendValue = JSONUtils.toString(map);
        SmsInfo info = new SmsInfo();
        info.setBusId(busId);
        info.setBusType(busType);
        info.setClientId(clientId);
        info.setOrganId(organId);
        info.setSmsType(smsType);
        info.setExtendValue(extendValue);
        smsPushService.pushMsgData2OnsExtendValue(info);
    }

    @RpcService
    public void refuseMeetClinicPush(int meetClinicResultId, int requestDoctor) {
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
        //发送消息通知
        Integer clientId = null;
        Integer organId = doctorDAO.getOrganByDoctorId(requestDoctor);
        String busType = "RefuseMeetClinicMsg";
        String smsType = "RefuseMeetClinicMsg";
        Integer busId = meetClinicResultId;
        smsPushService.pushMsgData2Ons(busId, organId, busType, smsType, clientId);
    }

    @RpcService
    public void addTargetMeetClinicPush(int meetClinicResultId, int organId) {
        //发送消息通知
        Integer clientId = null;
        String busType = "AddTargetMeetClinicMsg";
        String smsType = "AddTargetMeetClinicMsg";
        Integer busId = meetClinicResultId;
        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
        smsPushService.pushMsgData2Ons(busId, organId, busType, smsType, clientId);
    }

    @RpcService
    public void remindMeetClinicPush(int meetClinicId, int organId,List<HashMap<String,Object>> list) {
        //发送消息通知
        String busType = "RemindMeetClinic";
        String smsType = "RemindMeetClinic";
        SmsInfo smsInfo=new SmsInfo();
        smsInfo.setBusId(meetClinicId);
        smsInfo.setBusType(busType);
        smsInfo.setSmsType(smsType);
        smsInfo.setOrganId(organId);
        smsInfo.setExtendValue(JSONUtils.toString(list));
        smsInfo.setClientId(null);
        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
        smsPushService.pushMsgData2OnsExtendValue(smsInfo);
    }

    @RpcService
    public void removeMeetClinicPush(int meetClinicId,List<Integer> resultIds) {
        MeetClinicDAO meetClinicDAO = DAOFactory.getDAO(MeetClinicDAO.class);
        MeetClinic meetClinic = meetClinicDAO.getByMeetClinicId(meetClinicId);
        if (meetClinic==null) {
            return;
        }
        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);

        //发送消息通知
        Integer clientId = null;
        Integer organId = meetClinic.getRequestOrgan();
        String busType = "RemoveMeetClinicMsg";
        String smsType = "RemoveMeetClinicMsg";
        Integer busId = meetClinicId;
        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("resultIds", resultIds);
        String extendValue = JSONUtils.toString(map);
        SmsInfo info = new SmsInfo();
        info.setBusId(busId);
        info.setBusType(busType);
        info.setClientId(clientId);
        info.setOrganId(organId);
        info.setSmsType(smsType);
        info.setExtendValue(extendValue);
        smsPushService.pushMsgData2OnsExtendValue(info);
    }
}
