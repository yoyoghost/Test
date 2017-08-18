package eh.bus.service.video;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.spring.AppDomainContext;
import ctd.util.annotation.RpcService;
import eh.base.dao.DoctorDAO;
import eh.bus.constant.CloudClinicSetConstant;
import eh.entity.base.Doctor;
import eh.remote.IRTMService;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * Created by Luphia on 2017/2/17.
 */
public class RTMService {


    /**
     * 获取单个医生登陆及视频状态
     *
     * @param doctorId
     * @return
     */
    @RpcService
    public Map<String, Object> getStatusAndFactByDoctorId(int doctorId) {
        IRTMService irtmService = AppDomainContext.getBean("rtm.rtmService", IRTMService.class);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        List<String> userIds = new ArrayList<String>();
        Map<String, Object> result = new HashMap<String, Object>();

        Doctor doctor = doctorDAO.getByDoctorId(doctorId);
        String userId = doctor.getMobile();
        if (userId == null || StringUtils.isEmpty(userId)) {
            return result;
        }
        userIds.add(userId);
        Map<String, Collection<Map<String, Object>>> map = irtmService.getConnectClients(userIds);
        if (map == null || map.isEmpty()) {
            //该医生不在线
            return result;
        }
        Collection<Map<String, Object>> col = map.get(userId);
        String rtcStatus = "0";
        String rtcBusy = "0";
        if (col == null || col.isEmpty()) {
            result.put("doctorId", doctorId);
            result.put("rtcStatus", rtcStatus);
            result.put("rtcBusy", rtcBusy);
            return result;
        }
        for (Map<String, Object> m : col) {
            if (m == null || m.isEmpty()) {
                continue;
            }
            String status = (String) m.get("rtcStatus");
            String busy = (String) m.get("rtcBusy");
            if (status == null || StringUtils.isEmpty(status) || busy == null || StringUtils.isEmpty(busy)) {
                continue;
            }
            if (status.equals("1") || status.equals("2")) {
                rtcStatus = status;
            }
            if (!busy.equals("0")) {
                rtcBusy = busy;
            }
        }
        result.put("doctorId", doctorId);
        result.put("rtcStatus", rtcStatus);
        result.put("rtcBusy", rtcBusy);
        return result;
    }

    /**
     * 获取多个医生在线及视频状态
     *
     * @param ds
     * @return
     */
    @RpcService
    public List<Map<String, Object>> findStatusAndFactByDoctorIds(List<Doctor> ds) {
        IRTMService irtmService = AppDomainContext.getBean("rtm.rtmService", IRTMService.class);
        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();

        List<String> userIds = new ArrayList<String>();
        for (Doctor d : ds) {
            userIds.add(d.getMobile());
        }
        Map<String, Collection<Map<String, Object>>> map = irtmService.getConnectClients(userIds);
        if (map == null || map.isEmpty()) {
            //所有医生均不在线
            return results;
        }
        for (Doctor d : ds) {
            Map<String, Object> result = new HashMap<String, Object>();
            String rtcStatus = "0";
            String rtcBusy = "0";
            String userId = d.getMobile();
            if (userId == null || StringUtils.isEmpty(userId)) {
                result.put("rtcStatus", rtcStatus);
                result.put("rtcBusy", rtcBusy);
                results.add(result);
                continue;
            }
            Collection<Map<String, Object>> col = map.get(userId);
            if (col == null || col.isEmpty()) {
                result.put("rtcStatus", rtcStatus);
                result.put("rtcBusy", rtcBusy);
                results.add(result);
                continue;
            }
            for (Map<String, Object> m : col) {
                if (m == null || m.isEmpty()) {
                    continue;
                }
                String status = (String) m.get("rtcStatus");
                String busy = (String) m.get("rtcBusy");
                if (status == null || StringUtils.isEmpty(status) || busy == null || StringUtils.isEmpty(busy)) {
                    continue;
                }
                if (status.equals("1") || status.equals("2")) {
                    rtcStatus = status;
                }
                if (!busy.equals("0")) {
                    rtcBusy = busy;
                }
            }
            result.put("rtcStatus", rtcStatus);
            result.put("rtcBusy", rtcBusy);
            results.add(result);
        }
        return results;
    }

    /**
     * 根据视频流获取所有端设备状态及视频状态
     *
     * @param doctorId
     * @param platfrom
     * @return
     */
    @RpcService
    public Map<String, Object> getOnlineAndFactByDoctorId(Integer doctorId, String platfrom) {
        if (platfrom == null || StringUtils.isEmpty(platfrom)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "platfrom is required!");
        }
        IRTMService irtmService = AppDomainContext.getBean("rtm.rtmService", IRTMService.class);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Map<String, Object> result = new HashMap<String, Object>();

        Doctor doctor = doctorDAO.getByDoctorId(doctorId);
        String userId = doctor.getMobile();
        Integer online = 0;
        Integer fact = 0;
        if (userId == null || StringUtils.isEmpty(userId)) {
            //该医生不在线
            result.put("online", online);
            result.put("fact", fact);
            return result;
        }
        List<String> userIds = new ArrayList<String>();
        userIds.add(userId);
        Map<String, Collection<Map<String, Object>>> map = irtmService.getConnectClients(userIds);
        if (map == null || map.isEmpty()) {
            //该医生不在线
            result.put("online", online);
            result.put("fact", fact);
            return result;
        }
        Collection<Map<String, Object>> col = map.get(userId);
        if (col == null || col.isEmpty()) {
            //该医生不在线
            result.put("online", online);
            result.put("fact", fact);
            return result;
        }
        for (Map<String, Object> m : col) {
            if (platfrom.equals(CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_XIAOYU)) {
                if ((m.get("client") != null && m.get("client").equals("3")) ||
                        (m.get("version") != null && m.get("version").equals("1"))) {
                    online = 2;
                }
            } else if (platfrom.equals(CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_ALL)) {
                online = 2;
            } else if (platfrom.equals(CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_PC)) {
                if (m.get("client") == null) {
                    //client不传的为pc
                    online = 2;
                }
            }
            if (m.get("rtcBusy") != null && m.get("rtcBusy").equals("1")) {
                fact = 1;
            }
        }
        result.put("online", online);
        result.put("fact", fact);
        return result;
    }

    /**
     * 根据视频流获取所有端设备状态及视频状态列表
     *
     * @param ds
     * @param platfrom
     * @return
     */
    @RpcService
    public List<Map<String, Object>> getOnlineAndFactByDoctorIds(List<Doctor> ds, String platfrom) {
        if (platfrom == null || StringUtils.isEmpty(platfrom)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "platfrom is required!");
        }
        IRTMService irtmService = AppDomainContext.getBean("rtm.rtmService", IRTMService.class);
        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();

        List<String> userIds = new ArrayList<String>();
        for (Doctor d : ds) {
            userIds.add(d.getMobile());
        }
        Map<String, Collection<Map<String, Object>>> map = irtmService.getConnectClients(userIds);
        if (map == null || map.isEmpty()) {
            //所有医生均不在线
            return results;
        }
        for (Doctor d : ds) {
            Map<String, Object> result = new HashMap<String, Object>();
            Integer online = 0;
            Integer fact = 0;
            String userId = d.getMobile();
            if (userId == null || StringUtils.isEmpty(userId)) {
                result.put("online", online);
                result.put("fact", fact);
                results.add(result);
                continue;
            }
            Collection<Map<String, Object>> col = map.get(userId);
            if (col == null || col.isEmpty()) {
                //该医生不在线
                result.put("online", online);
                result.put("fact", fact);
                results.add(result);
                continue;
            }
            for (Map<String, Object> m : col) {
                if (platfrom.equals(CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_XIAOYU)) {
                    if ((m.get("client") != null && m.get("client").equals("3")) ||
                            (m.get("version") != null && m.get("version").equals("1"))) {
                        online = 2;
                    }
                } else if (platfrom.equals(CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_ALL)) {
                    online = 2;
                } else if (platfrom.equals(CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_PC)) {
                    if (m.get("client") == null) {
                        //client不传的为pc
                        online = 2;
                    }
                }
                if (m.get("rtcBusy") != null && m.get("rtcBusy").equals("1")) {
                    fact = 1;
                }
            }
            result.put("online", online);
            result.put("fact", fact);
            results.add(result);
        }
        return results;
    }

    /**
     * 测试信令接口服务
     *
     * @param userIds
     * @return
     */
    @RpcService
    public Map<String, Collection<Map<String, Object>>> testIRMTService(List<String> userIds) {
        IRTMService irtmService = AppDomainContext.getBean("rtm.rtmService", IRTMService.class);
        return irtmService.getConnectClients(userIds);
    }
}
