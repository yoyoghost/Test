package eh.bus.service.meetclinic;

import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.util.annotation.RpcService;
import eh.base.dao.DoctorGroupDAO;
import eh.bus.constant.CloudClinicSetConstant;
import eh.bus.constant.MeetClinicConstant;
import eh.bus.constant.VideoInfoConstant;
import eh.bus.dao.MeetClinicDAO;
import eh.bus.dao.MeetClinicResultDAO;
import eh.bus.dao.VideoInfoDAO;
import eh.bus.service.VideoService;
import eh.entity.base.DoctorGroup;
import eh.entity.bus.MeetClinic;
import eh.entity.bus.MeetClinicResult;
import eh.entity.mpi.Patient;
import eh.mpi.dao.PatientDAO;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by luf on 2016/11/3.
 */

public class QueryMeetVideoService {

    /**
     * 我的会诊列表-小鱼
     *
     * @param doctorId
     * @return
     * @throws ControllerException
     */
    @RpcService
    public List<Map<String, Object>> myMeetWithVideo(int doctorId) throws ControllerException {
        MeetClinicDAO clinicDAO = DAOFactory.getDAO(MeetClinicDAO.class);
        MeetClinicResultDAO resultDAO = DAOFactory.getDAO(MeetClinicResultDAO.class);
        DoctorGroupDAO dgDao = DAOFactory.getDAO(DoctorGroupDAO.class);
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        VideoInfoDAO infoDAO = DAOFactory.getDAO(VideoInfoDAO.class);
        VideoService service = new VideoService();
        ctd.dictionary.Dictionary dic = DictionaryController.instance().get(
                "eh.base.dictionary.Doctor");
        List<MeetClinic> mcs = clinicDAO.findAllMeetByDoctorId(doctorId);
        List<Map<String, Object>> going = new ArrayList<>();
        List<Map<String, Object>> end = new ArrayList<>();
        List<Map<String, Object>> results = new ArrayList<>();
        for (MeetClinic mc : mcs) {
            Map<String, Object> map = new HashMap<>();
            int meetclinicId = mc.getMeetClinicId();
            List<MeetClinicResult> mrs = resultDAO.findByMeetClinicId(meetclinicId);
            StringBuilder inNames = new StringBuilder();
            inNames.append(dic.getText(mc.getRequestDoctor())).append(",");
            for (MeetClinicResult mr : mrs) {
                Integer effeStatus = mr.getEffectiveStatus();
                if (effeStatus != null && MeetClinicConstant.EFFECTIVESTATUS_INVALID.equals(effeStatus)) {
                    continue;
                }
                Integer target = mr.getTargetDoctor();
                Integer exe = mr.getExeDoctor();
                Integer exeStatus = mr.getExeStatus();
                List<DoctorGroup> dgs = dgDao.findByDoctorId(target);

                if (exeStatus != 8) {
                    if (exe != null && exe > 0) {
                        inNames.append(dic.getText(exe));
                        if (dgs != null && !dgs.isEmpty()) {
                            inNames.append("(");
                            inNames.append(dic.getText(target));
                            inNames.append(")");
                        }
                    } else {
                        inNames.append(dic.getText(target));
                    }
                    inNames.append(",");
                }
            }
            if (inNames.length() > 0) {
                map.put("inNames", inNames.substring(0, inNames.length() - 1));
            } else {
                map.put("inNames", inNames);
            }
            Patient p = patientDAO.get(mc.getMpiid());
            Patient patient = new Patient();
            patient.setMpiId(p.getMpiId());
            patient.setPatientName(p.getPatientName());
            patient.setPhoto(p.getPhoto());
            patient.setBirthday(p.getBirthday());
            patient.setPatientSex(p.getPatientSex());
            patient.setPatientType(p.getPatientType());
            map.put("patient", patient);
            map.put("meetclinic", mc);
            Map<String, Object> detail = service.meetingIsValid(meetclinicId);
            if (detail != null && !StringUtils.isEmpty((String) detail.get("meetingNumber"))) {
                map.put("videoSate", 1);//视频中
                map.put("isValid", true);
                going.add(map);
            } else {
                long count = infoDAO.getVideoCount(VideoInfoConstant.VIDEO_BUSSTYPE_MEETCLINIC,
                        meetclinicId, CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_XIAOYU);
                if (count > 0) {
                    map.put("videoSate", 2);//已会诊
                    map.put("isValid", false);
                    end.add(map);
                } else {
                    map.put("videoSate", 0);//待会诊
                    map.put("isValid", false);
                    going.add(map);
                }
            }
        }
        results.addAll(going);
        results.addAll(end);
        return results;
    }

    /**
     * 今日待办会诊列表-小鱼
     *
     * @param doctorId
     * @return
     * @throws ControllerException
     */
    @RpcService
    public List<Map<String, Object>> myMeetWithVideoToday(int doctorId) throws ControllerException {
        MeetClinicDAO clinicDAO = DAOFactory.getDAO(MeetClinicDAO.class);
        MeetClinicResultDAO resultDAO = DAOFactory.getDAO(MeetClinicResultDAO.class);
        DoctorGroupDAO dgDao = DAOFactory.getDAO(DoctorGroupDAO.class);
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        VideoService service = new VideoService();
        ctd.dictionary.Dictionary dic = DictionaryController.instance().get(
                "eh.base.dictionary.Doctor");
        List<MeetClinic> mcs = clinicDAO.findAllMeetByDoctorId(doctorId);
        List<Map<String, Object>> going = new ArrayList<>();
        for (MeetClinic mc : mcs) {
            Map<String, Object> map = new HashMap<>();
            int meetclinicId = mc.getMeetClinicId();
            Map<String, Object> detail = service.meetingIsValid(meetclinicId);
            if (detail == null || StringUtils.isEmpty((String) detail.get("meetingNumber"))) {
                continue;
            }
            List<MeetClinicResult> mrs = resultDAO.findByMeetClinicId(meetclinicId);
            StringBuilder inNames = new StringBuilder();
            inNames.append(dic.getText(mc.getRequestDoctor())).append(",");
            for (MeetClinicResult mr : mrs) {
                Integer effeStatus = mr.getEffectiveStatus();
                if (effeStatus != null && MeetClinicConstant.EFFECTIVESTATUS_INVALID.equals(effeStatus)) {
                    continue;
                }
                Integer target = mr.getTargetDoctor();
                Integer exe = mr.getExeDoctor();
                Integer exeStatus = mr.getExeStatus();
                List<DoctorGroup> dgs = dgDao.findByDoctorId(target);

                if (exeStatus != 8) {
                    if (exe != null && exe > 0) {
                        inNames.append(dic.getText(exe));
                        if (dgs != null && !dgs.isEmpty()) {
                            inNames.append("(");
                            inNames.append(dic.getText(target));
                            inNames.append(")");
                        }
                    } else {
                        inNames.append(dic.getText(target));
                    }
                    inNames.append(",");
                }
            }
            if (inNames.length() > 0) {
                map.put("inNames", inNames.substring(0, inNames.length() - 1));
            } else {
                map.put("inNames", inNames);
            }
            Patient p = patientDAO.get(mc.getMpiid());
            Patient patient = new Patient();
            patient.setMpiId(p.getMpiId());
            patient.setPatientName(p.getPatientName());
            patient.setPhoto(p.getPhoto());
            patient.setBirthday(p.getBirthday());
            patient.setPatientSex(p.getPatientSex());
            patient.setPatientType(p.getPatientType());
            map.put("patient", patient);
            map.put("meetclinic", mc);
            map.put("videoSate", 1);//视频中
            map.put("isValid", true);
            going.add(map);
        }
        return going;
    }
}
