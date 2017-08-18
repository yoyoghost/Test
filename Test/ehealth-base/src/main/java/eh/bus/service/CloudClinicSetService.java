package eh.bus.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.spring.AppDomainContext;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.dao.DoctorDAO;
import eh.bus.constant.CloudClinicSetConstant;
import eh.bus.dao.CloudClinicSetDAO;
import eh.bus.dao.VideoInfoDAO;
import eh.bus.service.video.RTMService;
import eh.entity.base.Doctor;
import eh.entity.bus.CloudClinicSet;
import eh.entity.bus.VideoInfo;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * update方法只保留-登陆/登出时切换onlineStatus为不接受云门诊，切换医生接收状态时相应的修改OnlineStaus值
 */
public class CloudClinicSetService {
    private static final Logger log = Logger.getLogger(CloudClinicSetService.class);


    /**
     * 获取平台的医生云视频设置
     *
     * @param doctorId 医生id
     * @param platform 视频流平台，值见CloudClinicSetConstant.java
     * @return
     */
    @RpcService
    public CloudClinicSet getDoctorSetByPlatform(int doctorId, String platform) {
        if (platform == null || StringUtils.isEmpty(platform)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "platform is required!");
        }
//        CloudClinicSetDAO dao=DAOFactory.getDAO(CloudClinicSetDAO.class);
//        CloudClinicSet mySet = dao.getByDoctorIdAndPlatform(doctorId,platform);
//        if (mySet == null) {
//            mySet = new CloudClinicSet();
//        }
//        mySet.setSetId(mySet.getSetId());
//        mySet.setDoctorId(mySet.getDoctorId());
//        mySet.setFactStatus(mySet.getFactStatus() == null ? 0 : mySet
//                .getFactStatus());
//        mySet.setOnLineStatus(mySet.getOnLineStatus() == null ? 0 : mySet
//                .getOnLineStatus());
//        return mySet;
        RTMService rtmService = AppDomainContext.getBean("eh.rtmService", RTMService.class);
        Integer rtcStatus = 0;
        Integer rtcBusy = 0;
        if (platform.equals(CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_NGARI)) {
            Map<String, Object> map = rtmService.getStatusAndFactByDoctorId(doctorId);
            if (map != null && map.get("rtcStatus") != null && map.get("rtcBusy") != null) {
                rtcStatus = Integer.valueOf((String) map.get("rtcStatus"));
                rtcBusy = Integer.valueOf((String) map.get("rtcBusy"));
            }
        } else {
            //小鱼端及pc2.9版本之后均只判断对方是否有设备登陆-PC端传all，小鱼传xiaoyu
            Map<String, Object> map = rtmService.getOnlineAndFactByDoctorId(doctorId, platform);
            if (map != null && map.get("online") != null && map.get("fact") != null) {
                rtcStatus = (Integer) map.get("online");
                rtcBusy = (Integer) map.get("fact");
            }
        }
        CloudClinicSet cs = new CloudClinicSet();
        cs.setDoctorId(doctorId);
        cs.setOnLineStatus(rtcStatus);
        cs.setFactStatus(rtcBusy);
        return cs;
    }

    /**
     * 根据平台来源判断对方医生是否可呼叫
     *
     * @param oppDocId 对方医生ID
     * @param fromFlag 0在线云门诊；1预约云门诊
     * @param platform 视频流平台，值见CloudClinicSetConstant.java
     * @return
     * @author zhangx
     * @date 2016-1-4 下午2:12:21
     * @date 2017-2-20 luf：pc2.9版本以后此方法只提供小鱼端使用，platform传xiaoyu。留用兼容pc老版本
     */
    @RpcService
    public void canCallByPlatform(Integer oppDocId, Integer fromFlag, String platform) {
        if (oppDocId == null || fromFlag == null || platform == null || StringUtils.isEmpty("platform")) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "oppDocId or fromFlag or platform is required");
        }

//        CloudClinicSet set = getDoctorSetByPlatform(oppDocId,platform);
        RTMService rtmService = AppDomainContext.getBean("eh.rtmService", RTMService.class);
        if (platform.equals(CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_NGARI)) {
            Map<String, Object> map = rtmService.getStatusAndFactByDoctorId(oppDocId);
            String rtcStatus = "0";
            String rtcBusy = "0";
            if (map != null && map.get("rtcStatus") != null && map.get("rtcBusy") != null) {
                rtcStatus = (String) map.get("rtcStatus");
                rtcBusy = (String) map.get("rtcBusy");
            }
            if (rtcStatus == null || rtcStatus.equals("0")) {
                //所有设备端均不接受在线云门诊，全清下线
                CloudClinicSetDAO setDAO = DAOFactory.getDAO(CloudClinicSetDAO.class);
                setDAO.updateAllOnLineToOffByDoctorId(oppDocId);
            }

            // onlineStatus(0不上线；1暂时离开；2我在线上)
            // factStatus(0视频空闲；1视频中)
            if (fromFlag == 0) {
                if (rtcStatus == null || rtcStatus.equals("0")) {
                    throw new DAOException(609, "对方医生已离线");
                }

                if (rtcStatus.equals("1")) {
                    throw new DAOException(609, "对方医生正忙，请稍后重试");
                }

                if (rtcStatus.equals("2") && rtcBusy != null && rtcBusy.equals("1")) {
                    throw new DAOException(609, "对方医生正在视频中，请稍后重试");
                }

            }

            if (fromFlag == 1) {
                if (rtcBusy != null && rtcBusy.equals("1")) {
                    throw new DAOException(609, "对方医生正在视频中，请稍后重试");
                }
            }
        } else if (platform.equals(CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_XIAOYU)) {
            //小鱼视频流只判断设备状态及视频状态
            Map<String, Object> map = rtmService.getOnlineAndFactByDoctorId(oppDocId, platform);
            if (map != null && !map.isEmpty()) {
                if (map.get("online").equals(0)) {
                    throw new DAOException(ErrorCode.SERVICE_ERROR, "对方医生已离线");
                }
                if (map.get("fact").equals(1)) {
                    throw new DAOException(ErrorCode.SERVICE_ERROR, "对方医生正在视频中，请稍后重试");
                }
            }
        }
    }

    /**
     * 能否呼叫
     *
     * @param oppDocId 对方医生内码
     * @param fromFlag 0在线云门诊；1预约云门诊
     * @param platform 本地设备端
     * @date 2017-2-20 pc端2.9版本后仅使用此方法判断是否能呼叫
     * @author luf
     */
    @RpcService
    public void canCall(int oppDocId, int fromFlag, String platform) {
        if (platform == null || StringUtils.isEmpty(platform)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "platform is required!");
        }
        RTMService rtmService = AppDomainContext.getBean("eh.rtmService", RTMService.class);
//        String platform = CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_PC;
//        if (hasXiaoYu) {
//            platform = CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_ALL;
//        }

        // pc端使用ngari，后期其他端接入再写if-else判断
        String ngariPlatform = CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_NGARI;
        if (platform.equals(CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_ANDROID) ||
                platform.equals(CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_IOS)) {
            ngariPlatform = platform;
            platform = CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_XIAOYU;
        }
        Map<String, Object> map = rtmService.getOnlineAndFactByDoctorId(oppDocId, platform);
        Map<String, Object> allMap = rtmService.getOnlineAndFactByDoctorId(oppDocId, CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_ALL);

        //所有设备端均不接受在线云门诊，全清下线
        if (allMap != null && !allMap.isEmpty() && allMap.get("online").equals(0)) {
            CloudClinicSetDAO setDAO = DAOFactory.getDAO(CloudClinicSetDAO.class);
            setDAO.updateAllOnLineToOffByDoctorId(oppDocId);
        }

        //根据pc端入参判断对方设备及视频状态
        if (map != null && !map.isEmpty()) {
            if (map.get("online").equals(0)) {
                if (platform.equals(CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_PC)) {
                    throw new DAOException(ErrorCode.SERVICE_ERROR, "发起失败，请检查您的网络连接后重试！");
                } else if (platform.equals(CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_XIAOYU)
                        && allMap != null && !allMap.isEmpty() && !allMap.get("online").equals(0)) {
                    //当前医生只有小鱼，但对方医生只有rtc在线
                    throw new DAOException(ErrorCode.SERVICE_ERROR, "对方系统版本过低，呼叫失败");
                } else {
                    throw new DAOException(ErrorCode.SERVICE_ERROR, "对方医生已离线");
                }
            }
            if (map.get("fact").equals(1)) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "对方医生正在视频中，请稍后重试");
            }
        }

        //在线云门诊判断对方医生是否接受云门诊视频
        Map<String, Object> map1 = rtmService.getStatusAndFactByDoctorId(oppDocId);
        String rtcStatus = "0";
        String rtcBusy = "0";
        if (map1 != null && map1.get("rtcStatus") != null && map1.get("rtcBusy") != null) {
            rtcStatus = (String) map1.get("rtcStatus");
            rtcBusy = (String) map1.get("rtcBusy");
        }
        if (rtcStatus == null || rtcStatus.equals("0")) {
            //所有设备端均不接受在线云门诊，全清下线
            CloudClinicSetDAO setDAO = DAOFactory.getDAO(CloudClinicSetDAO.class);
            setDAO.updateAllOnLineToOffByDoctorId(oppDocId);
        }

        if (fromFlag == 0) {
            if (rtcStatus == null || rtcStatus.equals("0")) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "对方医生已离线");
            }

            if (rtcStatus.equals("1")) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "对方医生正忙，请稍后重试");
            }

            if (rtcStatus.equals("2") && rtcBusy != null && rtcBusy.equals("1")) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "对方医生正在视频中，请稍后重试");
            }
        }
    }

    /**
     * 是否能排队
     *
     * @param oppDocId
     */
    @RpcService
    public void canQueue(int oppDocId) {
        RTMService rtmService = AppDomainContext.getBean("eh.rtmService", RTMService.class);

        //在线云门诊判断对方医生是否接受云门诊视频
        Map<String, Object> map1 = rtmService.getStatusAndFactByDoctorId(oppDocId);
        String rtcStatus = "0";
        if (map1 != null && map1.get("rtcStatus") != null) {
            rtcStatus = (String) map1.get("rtcStatus");
        }

        if (rtcStatus == null || rtcStatus.equals("0")) {
            //所有设备端均不接受在线云门诊，全清下线
            CloudClinicSetDAO setDAO = DAOFactory.getDAO(CloudClinicSetDAO.class);
            setDAO.updateAllOnLineToOffByDoctorId(oppDocId);
        }

        if (rtcStatus == null || rtcStatus.equals("0")) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "对方医生已离线");
        }

        if (rtcStatus.equals("1")) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "对方医生正忙，请稍后重试");
        }
    }

    /**
     * 更新接诊方、出诊方的相对应视频平台的视频状态,并更新视频结束时间
     *
     * @param factStatus     0视频空闲；1视频中
     * @param docId          当前医生ID
     * @param oppDocId       对方医生ID
     * @param fromFlag       0在线云门诊；1预约云门诊
     * @param docPlatform    当前医生视频流平台 值见CloudClinicSetConstant.java
     * @param oppDocPlatform 对方医生视频流平台 值见CloudClinicSetConstant.java
     * @author zhangx
     * @date 2015-12-29 下午3:50:33
     */
    @RpcService
    public Boolean updatePlatformFactStatusAndEndVideo(final Integer factStatus, final Integer docId, final Integer oppDocId,
                                                       Integer fromFlag, final String docPlatform, final String oppDocPlatform, final String rommId) {
//        Boolean bool = updatePlatformFactStatus(factStatus, docId, oppDocId, fromFlag, docPlatform, oppDocPlatform);
        VideoInfoDAO videoDao = DAOFactory.getDAO(VideoInfoDAO.class);
        if (CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_XIAOYU.equalsIgnoreCase(docPlatform) ||
                CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_XIAOYU.equalsIgnoreCase(oppDocPlatform)) {
            List<VideoInfo> info = videoDao.findByRoomIdAndPlatform(rommId, CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_XIAOYU);
            if (info != null && info.size() > 0) {
                videoDao.updateEndDateByRoomId(new Date(), rommId, CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_XIAOYU);
            }
        }
//
//        return bool;
        return true;
    }

    /**
     * 更新接诊方、出诊方的相对应视频平台的视频状态
     *
     * @param factStatus     0视频空闲；1视频中
     * @param docId          当前医生ID
     * @param oppDocId       对方医生ID
     * @param fromFlag       0在线云门诊；1预约云门诊
     * @param docPlatform    当前医生视频流平台 值见CloudClinicSetConstant.java
     * @param oppDocPlatform 对方医生视频流平台 值见CloudClinicSetConstant.java
     * @author zhangx
     * @date 2015-12-29 下午3:50:33
     */
    @RpcService
    public Boolean updatePlatformFactStatus(final Integer factStatus, final Integer docId, final Integer oppDocId,
                                            Integer fromFlag, final String docPlatform, final String oppDocPlatform) {
//        if (factStatus == null || docId == null || oppDocId == null
//                || fromFlag == null || StringUtils.isEmpty(docPlatform) || StringUtils.isEmpty(oppDocPlatform)) {
//            throw new DAOException(DAOException.VALUE_NEEDED,
//                    "factStatus,docIds,fromFlag,docPlatform,oppDocPlatform is required");
//        }
//
//        if (factStatus == 1) {
//            canCallByPlatform(oppDocId, fromFlag, oppDocPlatform);
//        }
//
//        HibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {
//            public void execute(StatelessSession ss) throws Exception {
//                CloudClinicSetDAO dao = DAOFactory.getDAO(CloudClinicSetDAO.class);
//                dao.updateFactStatusByDoctorIdAndPlatform(factStatus, docId, docPlatform);
//                dao.updateFactStatusByDoctorIdAndPlatform(factStatus, oppDocId, oppDocPlatform);
//                setResult(true);
//            }
//        };
//        HibernateSessionTemplate.instance().executeTrans(action);
//
//        return action.getResult();
        return true;
    }

    /**
     * 更新平台的在线状态
     *
     * @param onLineStatus 0不上线；1暂时离开；2我在线上
     * @param doctorId     要更新的医生ID
     * @param platform     视频流平台 值见CloudClinicSetConstant.java
     */
    @RpcService
    public void updatePlatformOnlineStatus(Integer onLineStatus, Integer doctorId, String platform) {
//        if (onLineStatus == null || doctorId == null || StringUtils.isEmpty(platform)) {
//            throw new DAOException(DAOException.VALUE_NEEDED,
//                    "doctorId or OnlineStatus or platform is required");
//        }
//        CloudClinicSetDAO dao = DAOFactory.getDAO(CloudClinicSetDAO.class);
//        dao.updateOnLineStatusByDoctorIdAndPlatform(onLineStatus, doctorId, platform);
    }

    /**
     * 设置不同平台医生的默认在线云门诊状态数据
     *
     * @param doctorId
     * @param platform 视频流平台 值见CloudClinicSetConstant.java
     */
    @RpcService
    public void updateDocSetByPlatform(Integer doctorId, String platform) {
//        CloudClinicSetDAO ccsDao = DAOFactory.getDAO(CloudClinicSetDAO.class);
//        CloudClinicSet ccSet = new CloudClinicSet();
//        ccSet.setDoctorId(doctorId);
//        ccSet.setOnLineStatus(0);
//        ccSet.setFactStatus(0);
//        ccSet.setPlatform(platform);
//        addOrUpdateSet(ccSet);
    }

    /**
     * 更新所有平台医生在线状态
     *
     * @param doctorId
     * @param platform
     */
    @RpcService
    public void updateDocSetAllPlatform(Integer doctorId, Integer onlineStatus) {
//        CloudClinicSetDAO ccsDao = DAOFactory.getDAO(CloudClinicSetDAO.class);
//        CloudClinicSet ccSet = new CloudClinicSet();
//        ccSet.setDoctorId(doctorId);
//        ccSet.setOnLineStatus(onlineStatus);
//        ccSet.setFactStatus(0);
//        ccSet.setPlatform(CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_XIAOYU);
//        addOrUpdateSet(ccSet);
//
//        CloudClinicSet ccSet2 = new CloudClinicSet();
//        ccSet2.setDoctorId(doctorId);
//        ccSet2.setOnLineStatus(onlineStatus);
//        ccSet2.setFactStatus(0);
//        ccSet2.setPlatform(CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_NGARI);
//        addOrUpdateSet(ccSet);
    }

    /**
     * 更新或者新增平台的在线状态
     *
     * @param set
     * @author zhangx
     * @date 2015-12-29 下午4:50:10
     */
    public void addOrUpdateSet(CloudClinicSet set) {
//        DoctorDAO docDao = DAOFactory.getDAO(DoctorDAO.class);
//        CloudClinicSetDAO ccsDao = DAOFactory.getDAO(CloudClinicSetDAO.class);
//
//        Integer doctorId = set.getDoctorId();
//        String platform = set.getPlatform();
//        if (doctorId == null || StringUtils.isEmpty(platform)) {
//            throw new DAOException(DAOException.VALUE_NEEDED,
//                    "doctorId, platform is required");
//        }
//        Doctor doc = docDao.getByDoctorId(doctorId);
//        if (doc == null || (doc.getTeams() != null && doc.getTeams())
//                || doc.getVirtualDoctor()) {
//            throw new DAOException(609, "该医生为团队医生或虚拟医生，不能进行设置");
//        }
//
//        CloudClinicSet target = ccsDao.getByDoctorIdAndPlatform(doctorId, platform);
//        if (target == null) {
//            ccsDao.save(set);
//        } else {
//            BeanUtils.map(set, target);
//            ccsDao.update(target);
//        }
    }

//    /**
//     * 切换医生接收云门诊状态
//     *
//     * @param doctorId
//     * @param platform
//     * @param online
//     */
//    @RpcService
//    public void addOrUpdateSetByThree(int doctorId, String platform, Integer online) {
//        CloudClinicSet ccSet = new CloudClinicSet();
//        ccSet.setDoctorId(doctorId);
//        if (online != null) {
//            ccSet.setOnLineStatus(online);
//        }
//        if (online != null && (online == 0 || online == 1)) {
//            ccSet.setFactStatus(0);
//        }
//        ccSet.setPlatform(platform);
//        this.addOrUpdateSet(ccSet);
//    }

    /**
     * 仅供信令调用
     *
     * @param userId
     * @param platform
     * @return
     */
    @RpcService
    public CloudClinicSet getByUserIdAndPlatform(String userId, String platform) {
        if (StringUtils.isEmpty(userId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "userId is empty");
        }
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Doctor doctor = doctorDAO.getByMobile(userId);
        if (doctor == null || doctor.getDoctorId() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "doctor is required，userId=" + userId);
        }
        CloudClinicSetDAO setDAO = DAOFactory.getDAO(CloudClinicSetDAO.class);
        return setDAO.getByDoctorIdAndPlatform(doctor.getDoctorId(), platform);
    }

    /**
     * 供信令调用
     *
     * @param onLineStatus
     * @param userId
     * @param platform
     */
    @RpcService
    public void updateOnLineStatusByUserIdAndPlatform(Integer onLineStatus, String userId, String platform) {
        if (StringUtils.isEmpty(userId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "userId is empty");
        }
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Doctor doctor = doctorDAO.getByMobile(userId);
        if (doctor == null || doctor.getDoctorId() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "doctor is required");
        }
        Integer doctorId = doctor.getDoctorId();
        CloudClinicSetDAO setDAO = DAOFactory.getDAO(CloudClinicSetDAO.class);
        CloudClinicSet target = setDAO.getByDoctorIdAndPlatform(doctorId, platform);
        if (target == null) {
            CloudClinicSet set = new CloudClinicSet();
            set.setOnLineStatus(onLineStatus);
            set.setDoctorId(doctorId);
            set.setPlatform(platform);
            set.setFactStatus(0);
            setDAO.save(set);
        } else {
            target.setOnLineStatus(onLineStatus);
            setDAO.update(target);
        }
    }

}
