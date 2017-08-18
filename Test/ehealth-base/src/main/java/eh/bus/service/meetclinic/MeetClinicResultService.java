package eh.bus.service.meetclinic;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.spring.AppDomainContext;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.base.constant.BussTypeConstant;
import eh.base.constant.ErrorCode;
import eh.base.dao.DoctorDAO;
import eh.base.dao.DoctorGroupDAO;
import eh.base.user.UserSevice;
import eh.bus.asyndobuss.bean.BussCancelEvent;
import eh.bus.asyndobuss.service.AsynDoBussService;
import eh.bus.constant.MeetClinicConstant;
import eh.bus.dao.EndMeetClinicDAO;
import eh.bus.dao.MeetClinicDAO;
import eh.bus.dao.MeetClinicResultDAO;
import eh.bus.dao.SaveMeetReportDAO;
import eh.entity.base.Doctor;
import eh.entity.bus.MeetClinic;
import eh.entity.bus.MeetClinicResult;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by luf on 2016/6/4.
 */

public class MeetClinicResultService {

    /**
     * 判断能否开始会诊服务
     *
     * @param meetClinicResult 执行单信息
     * @return boolean
     */
    @RpcService
    public boolean canStartMeetClinic(MeetClinicResult meetClinicResult) {
        MeetClinicResultDAO meetClinicResultDAO = DAOFactory.getDAO(MeetClinicResultDAO.class);

        Integer meetClinicResultId = meetClinicResult.getMeetClinicResultId();
        if (meetClinicResultId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "===== meetClinicResultId is required =====");
        }
        Integer meetClinicId = meetClinicResult.getMeetClinicId();
        if (meetClinicId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "===== meetClinicId is required =====");
        }
        Integer doctorId = meetClinicResult.getExeDoctor();
        if (doctorId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "===== doctorId is required =====");
        }

        DoctorGroupDAO groupDAO = DAOFactory.getDAO(DoctorGroupDAO.class);
        List<MeetClinicResult> mrs = meetClinicResultDAO.findByMeetClinicId(meetClinicId);
        int targetCount = 0;
        int groupCount = 0;
        int exeCount = 0;
        int start = 0;
        for (MeetClinicResult mr : mrs) {
            Integer effeStatus = mr.getEffectiveStatus();
            if (effeStatus != null && MeetClinicConstant.EFFECTIVESTATUS_INVALID.equals(effeStatus)) {
                continue;
            }
            Integer target = mr.getTargetDoctor();
            Integer mrId = mr.getMeetClinicResultId();
            if (mrId.equals(meetClinicResultId) && target.equals(doctorId)) {
                start++;
            }
            if (target.equals(doctorId)) {
                targetCount++;
            }
            if (groupDAO.getByDoctorIdAndMemberId(target, doctorId) != null) {
                groupCount++;
                if (doctorId.equals(mr.getExeDoctor())) {
                    exeCount++;
                }
            }
        }

        if (start <= 0) {
            if (targetCount > 0) {
                if (groupCount > 0) {
                    throw new DAOException(609, "您已参与这次会诊");
                }
            } else {
                if (groupCount > 1 && exeCount > 0) {
                    throw new DAOException(609, "您已参与这次会诊");
                }
            }
        }

        MeetClinicResult result = meetClinicResultDAO.get(meetClinicResultId);
        if (result.getExeStatus() == 9) {
            throw new DAOException(609, "抱歉，对方医生已取消该会诊申请");
        } else if (result.getExeDoctor() != null && !result.getExeDoctor().equals(doctorId)) {
            throw new DAOException(609, "啊哦！您慢了一步，已有团队其他成员参与...");
        }
        if (result.getExeStatus() >= 1) {
            return false;
        }
        return true;
    }

    /**
     * 会诊中心完成会诊接口
     *
     * @param meetClinicId
     * @param meetClinicResultId
     * @return
     */
    @RpcService
    public Boolean endMeetClinicForCenter(int meetClinicId, int meetClinicResultId) {
        SaveMeetReportDAO saveMeetReportDAO = DAOFactory.getDAO(SaveMeetReportDAO.class);
        EndMeetClinicDAO endMeetClinicDAO = DAOFactory.getDAO(EndMeetClinicDAO.class);
        MeetClinicResultDAO meetClinicResultDAO = DAOFactory.getDAO(MeetClinicResultDAO.class);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        MeetClinicResult mr = saveMeetReportDAO.getByMeetClinicResultId(meetClinicResultId);
        //保存默认会诊意见
        mr.setMeetReport(MeetClinicConstant.MEETREPORT_MEETCENTER);
        List<MeetClinicResult> results = meetClinicResultDAO.findByMeetClinicId(meetClinicId);
        //必须要有一个以上的非会诊中心医生执行单
        if (results.isEmpty() || results.size() < 2) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "endMeetClinicForCenter meetClinic[" + meetClinicId + "] must has one another exeDoctor as least");
        }
        List<String> notEndDocs = new ArrayList<>();
        for (MeetClinicResult r : results) {
            Integer effeStatus = r.getEffectiveStatus();
            if (effeStatus != null && MeetClinicConstant.EFFECTIVESTATUS_INVALID.equals(effeStatus)) {
                continue;
            }
            if (!r.getMeetCenter() && r.getExeStatus() < 2) {
                notEndDocs.add(doctorDAO.getNameById(r.getExeDoctor() == null ? r.getTargetDoctor() : r.getExeDoctor()));
            }
        }
        if (!notEndDocs.isEmpty()) {
            String docNames = StringUtils.join(notEndDocs.toArray(new String[notEndDocs.size()]), "、");//组装未完成会诊的医生名称
            throw new DAOException(ErrorCode.SERVICE_ERROR, "请告知" + docNames + "医生回复会诊意见后再完成该会诊。");
        }
        saveMeetReportDAO.saveMeetReportNew(mr);
        return endMeetClinicDAO.endMeetClinic(meetClinicId, meetClinicResultId);
    }

    /**
     * 可移出会诊医生列表
     *
     * @param meetclinicId 会诊申请单号
     * @return List<HashMap<String, Object>>
     */
    @RpcService
    public List<HashMap<String, Object>> deletionDoctorList(int meetclinicId) {
        MeetClinicResultDAO resultDAO = DAOFactory.getDAO(MeetClinicResultDAO.class);
        List<MeetClinicResult> results = resultDAO.findByMeetClinicId(meetclinicId);
        if (results == null || results.isEmpty()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "results is required!");
        }
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        List<HashMap<String, Object>> list = new ArrayList<>();
        for (MeetClinicResult result : results) {
            Integer effeStatus = result.getEffectiveStatus();
            if (effeStatus != null && MeetClinicConstant.EFFECTIVESTATUS_INVALID.equals(effeStatus)) {
                continue;
            }
            Boolean meetCenter = result.getMeetCenter();
            if (meetCenter != null && meetCenter) {
                continue;
            }
            Integer exeStatus = result.getExeStatus();
            if (exeStatus == null || exeStatus > 1) {
                continue;
            }
            Integer doctorId = result.getExeDoctor() == null ? result.getTargetDoctor() : result.getExeDoctor();
            if (doctorId == null) {
                continue;
            }
            Doctor doctor = doctorDAO.getByDoctorId(doctorId);
            HashMap<String, Object> map = new HashMap<>();
            map.put("meetClinicResultId", result.getMeetClinicResultId());
            map.put("doctor", doctor);
            list.add(map);
        }
        return list;
    }

    /**
     * 移出会诊
     *
     * @param meetclinicId 会诊申请单号
     * @param resultIds    要移除的执行单号列表
     * @return
     */
    @RpcService
    public Boolean removeDoctorList(int meetclinicId, List<Integer> resultIds) {
        if (resultIds == null || resultIds.isEmpty()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "resultIds is required!");
        }
        MeetClinicResultDAO resultDAO = DAOFactory.getDAO(MeetClinicResultDAO.class);
        Integer count = resultDAO.updateEffectiveStatus(meetclinicId, resultIds);
        if (count != null && count > 0) {
            //发送消息
            MeetClinicPushService pushService = AppDomainContext.getBean("eh.meetClinicPushService", MeetClinicPushService.class);
            pushService.removeMeetClinicPush(meetclinicId, resultIds);
            //剔除首页待处理
            for (Integer meetClinicResultId:resultIds) {
                AsynDoBussService asynDoBussService = AppContextHolder.getBean("asynDoBussService", AsynDoBussService.class);
                asynDoBussService.fireEvent(new BussCancelEvent(meetClinicResultId, BussTypeConstant.MEETCLINIC, null));
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     *根据会诊单查询未拒绝会诊医生列表，供视频二次呼叫调用
     * @param meetclincicId
     * @return List<Doctor>
     */
    @RpcService
    public List<HashMap<String,Object>> callDoctors(int meetclincicId) {
        MeetClinicResultDAO resultDAO = DAOFactory.getDAO(MeetClinicResultDAO.class);
        MeetClinicDAO meetClinicDAO = DAOFactory.getDAO(MeetClinicDAO.class);
        MeetClinic meetClinic = meetClinicDAO.getByMeetClinicId(meetclincicId);
        List<MeetClinicResult> results = resultDAO.findByMeetClinicId(meetclincicId);
        UserSevice sevice = AppDomainContext.getBean("userSevice", UserSevice.class);
        List<HashMap<String, Object>> list = new ArrayList<>();
        if (results == null || results.isEmpty()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "results is required!");
        }
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Integer requestDoctorId = meetClinic.getRequestDoctor();
        Doctor requestDoctor = doctorDAO.getByDoctorId(requestDoctorId);
        HashMap<String, Object> map1 = new HashMap<>();
        map1.put("doctor", requestDoctor);
        map1.put("urt", sevice.getDoctorUrtIdByDoctorId(requestDoctorId));
        list.add(map1);
        for (MeetClinicResult result : results) {
            Integer effeStatus = result.getEffectiveStatus();
            if (effeStatus != null && MeetClinicConstant.EFFECTIVESTATUS_INVALID.equals(effeStatus)) {
                continue;
            }
            //如果是个人医生剔除无效的执行单
            if (result.getExeStatus() >= 8) {
                continue;
            }
            Integer doctorId = result.getTargetDoctor();
            Doctor doctor = doctorDAO.getByDoctorId(doctorId);
            if (result.getExeDoctor() != null) {
                doctorId = result.getExeDoctor();
                doctor = doctorDAO.getByDoctorId(doctorId);
            } else if (doctor.getTeams() != null && doctor.getTeams()) {
                continue;
            }
            Integer urt = sevice.getDoctorUrtIdByDoctorId(doctorId);

            HashMap<String, Object> map = new HashMap<>();
            map.put("doctor", doctor);
            map.put("urt", urt);
            list.add(map);
        }
        return list;
    }
}