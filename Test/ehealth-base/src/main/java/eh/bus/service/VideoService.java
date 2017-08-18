package eh.bus.service;


import com.alibaba.fastjson.JSONObject;
import ctd.mvc.upload.FileMetaRecord;
import ctd.mvc.upload.FileService;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import ctd.util.context.Context;
import eh.base.constant.ErrorCode;
import eh.base.dao.DoctorDAO;
import eh.base.user.UserSevice;
import eh.bus.constant.CloudClinicSetConstant;
import eh.bus.constant.MeetClinicConstant;
import eh.bus.constant.VideoInfoConstant;
import eh.bus.dao.*;
import eh.bus.service.video.VideoPushService;
import eh.entity.base.Doctor;
import eh.entity.bus.*;
import eh.entity.mpi.Patient;
import eh.mpi.dao.PatientDAO;
import eh.util.Ainemo;
import eh.utils.DateConversion;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;

public class VideoService {
    private static final Log logger = LogFactory.getLog(VideoService.class);

    /**
     * 根据telClinicId获取视频房间号
     *
     * @param telClinicId 云诊室号
     * @param platform    视频流平台，见CloudClinicSetConstant.java
     * @return
     */
    @RpcService
    public String getAppointMeetingRoom(String telClinicId, String platform) {
        AppointRecordDAO appointDao = DAOFactory.getDAO(AppointRecordDAO.class);
        AppointRecord record = appointDao.getByTelClinicIdAndClinicObject(telClinicId, 2);//获取出诊方预约记录
        if (record == null || record.getTelClinicFlag() == null || (record.getTelClinicFlag() != null && record.getTelClinicFlag() != 1)) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该预约单信息不是远程门诊信息，无法创建视频");
        }
        return getMeetingRoom(VideoInfoConstant.VIDEO_BUSSTYPE_APPOINT, record.getAppointRecordId(), platform);
    }

    /**
     * 在线云门诊获取房间号
     *
     * @param patName 患者姓名
     * @return HashMap<String, Object>
     */
    @RpcService
    public HashMap<String, Object> getOnlineClinicRoom(String patName) {
        VideoInfoDAO videoDao = DAOFactory.getDAO(VideoInfoDAO.class);

        String roomName = "的远程云门诊";
        String preTitle = "患者";

        StringBuilder meeting_name = new StringBuilder(preTitle).append(patName).append(roomName);

        //小鱼平台
        HashMap<String, Object> res = createXiaoYuMeeting(meeting_name.toString(), VideoInfoConstant.VIDEO_FLAG_NGARI);

        String roomId = (String) res.get("roomId");
        String detail = (String) res.get("detail");

        if (!StringUtils.isEmpty(roomId) && !StringUtils.isEmpty(detail)) {
            VideoInfo info = new VideoInfo();
            info.setPlatform(CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_XIAOYU);
            info.setBussId(-1);
            info.setBussType(VideoInfoConstant.VIDEO_BUSSTYPE_APPOINT);
            info.setRoomId(roomId);
            info.setDetail(detail);
            info.setCreateDate(Context.instance().get("date.datetime", Date.class));
            videoDao.save(info);
            return JSONUtils.parse(detail, HashMap.class);
        }

        return new HashMap<>();
    }

    /**
     * 根据telClinicId获取视频房间号-返回map
     *
     * @param telClinicId 云诊室号
     * @param platform    视频流平台，见CloudClinicSetConstant.java
     * @return
     */
    @RpcService
    public HashMap<String, Object> getAppointMeetingRoomToMap(String telClinicId, String platform) {
        String roomDetail = this.getAppointMeetingRoom(telClinicId, platform);
        HashMap<String, Object> result = new HashMap<>();
        if (StringUtils.isEmpty(roomDetail)) {
            return result;
        }
        return JSONUtils.parse(roomDetail, HashMap.class);
    }

    /**
     * 视频会诊发起服务
     * <p>
     * 小鱼及app端发起视频调用
     *
     * @param meetclinicId
     * @param doctorId
     * @return 返回detail，是否新建，信令需推送的医生信息
     */
    @RpcService
    public Map<String, Object> videoCallMeetingAinemo(int meetclinicId, int doctorId) {
        Map<String, Object> result = new HashMap<>();
        MeetClinicDAO meetClinicDAO = DAOFactory.getDAO(MeetClinicDAO.class);
        Integer mcStatus = meetClinicDAO.getMeetClinicStatusById(meetclinicId);
        int bussType = VideoInfoConstant.VIDEO_BUSSTYPE_MEETCLINIC;
        int bussId = meetclinicId;
        String platform = CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_XIAOYU;
        VideoInfoDAO infoDAO = DAOFactory.getDAO(VideoInfoDAO.class);
        List<VideoInfo> infos = infoDAO.findBusVideoInfoByPlatform(bussType, bussId, platform);
        VideoInfo using = null;
        for (VideoInfo info : infos) {
            String roomId = info.getRoomId();
            int id = info.getId();
            boolean state = this.getVideoCallState(id, mcStatus);
            if (!state) {
                Ainemo.conferenceControl(roomId, VideoInfoConstant.VIDEO_FLAG_NGARI);
                infoDAO.updateEndDateById(new Date(), id, platform);
                continue;
            }
            if (using == null) {
                using = info;
            } else {
                Ainemo.conferenceControl(roomId, VideoInfoConstant.VIDEO_FLAG_NGARI);
                infoDAO.updateEndDateById(new Date(), id, platform);
            }
        }
        if (using == null) {
            String res = this.createMeetingRoom(bussType, bussId, platform, doctorId);
            if (StringUtils.isEmpty(res)) {
                return result;
            }
            Map<String, Object> detail = JSONUtils.parse(res, HashMap.class);
            result.put("detail", detail);
            result.put("isNew", true);

            String roomId = (String) detail.get("meetingNumber");
            List<Map<String, Object>> list = this.msgPushForMeetToAll(bussId, doctorId);
            result.put("targets", list);
            VideoPushService pushService = new VideoPushService();
            pushService.pushMsgForAinemo(roomId, bussId, doctorId, (String) detail.get("password"));
            return result;
        }
        result.put("detail", JSONUtils.parse(using.getDetail(), Map.class));
        result.put("isNew", false);
        return result;
    }

    /**
     * 视频会诊是否进行中
     * <p>
     * app端点环信及加入视频时调用
     *
     * @param meetclinicId
     * @return 视频会议室号
     */
    @RpcService
    public Map<String, Object> meetingIsValid(int meetclinicId) {
        MeetClinicDAO meetClinicDAO = DAOFactory.getDAO(MeetClinicDAO.class);
        Integer mcStatus = meetClinicDAO.getMeetClinicStatusById(meetclinicId);
        int bussType = VideoInfoConstant.VIDEO_BUSSTYPE_MEETCLINIC;
        int bussId = meetclinicId;
        String platform = CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_XIAOYU;
        VideoInfoDAO infoDAO = DAOFactory.getDAO(VideoInfoDAO.class);
        List<VideoInfo> infos = infoDAO.findBusVideoInfoByPlatform(bussType, bussId, platform);
        VideoInfo using = null;
        for (VideoInfo info : infos) {
            String roomId = info.getRoomId();
            int id = info.getId();
            boolean state = this.getVideoCallState(id, mcStatus);
            if (!state) {
                Ainemo.conferenceControl(roomId, VideoInfoConstant.VIDEO_FLAG_NGARI);
                infoDAO.updateEndDateById(new Date(), id, platform);
                continue;
            }
            if (using == null) {
                using = info;
            } else {
                Ainemo.conferenceControl(roomId, VideoInfoConstant.VIDEO_FLAG_NGARI);
                infoDAO.updateEndDateById(new Date(), id, platform);
            }
        }
        if (using == null) {
            return null;
        }
        Map<String, Object> detail = JSONUtils.parse(using.getDetail(), Map.class);
        return detail;
    }

    /**
     * 根据视频记录序号查询会议状态
     *
     * @param id
     * @return
     */
    private boolean getVideoCallState(int id, Integer status) {
        if (status == null) {
            return false;
        }
        VideoInfoDAO infoDAO = DAOFactory.getDAO(VideoInfoDAO.class);
        VideoInfo info = infoDAO.get(id);
        if (info == null) {
            return false;
        }
        String roomId = info.getRoomId();
        if (StringUtils.isEmpty(roomId)) {
            return false;
        }
        String result = Ainemo.meetingInfo(roomId, VideoInfoConstant.VIDEO_FLAG_NGARI);
        if (StringUtils.isEmpty(result)) {
            return false;
        }
        Map<String, Object> response = JSONUtils.parse(result, HashMap.class);
        String meetingRoomState = (String) response.get("meetingRoomState");
        if (StringUtils.isEmpty(meetingRoomState)) {
            return false;
        }
        Date createDate = info.getCreateDate();
        if (createDate == null) {
            createDate = DateConversion.getCurrentDate("1990-01-01", "yyyy-MM-dd");
        }
        long minutes = ((new Date()).getTime() - createDate.getTime()) / (1000 * 60);
        long hour = minutes / 60;
        if (meetingRoomState.equals("idle") && minutes >= 2) {
            return false;
        }
        if (meetingRoomState.equals("neverUsed")) {
            if (hour >= 20) {
                return false;
            }
            if (status > 2) {
                //完成的会诊不做限制
                return false;
            }
        }
        return true;
    }

    /**
     * 结束视频会诊
     *
     * @param id
     */
    private void endVideoMeeting(int id) {
        VideoInfoDAO infoDAO = DAOFactory.getDAO(VideoInfoDAO.class);
        VideoInfo info = infoDAO.get(id);
        if (info == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "videoInfo is required!");
        }
        String roomId = info.getRoomId();
        if (StringUtils.isEmpty(roomId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "roomId is required!");
        }
        Ainemo.conferenceControl(roomId, VideoInfoConstant.VIDEO_FLAG_NGARI);
        String platform = CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_XIAOYU;
        infoDAO.updateEndDateById(new Date(), id, platform);
    }

    /**
     * 获取会议房间号
     *
     * @param bussType 业务类型,见VideoInfoConstant.java
     * @param bussId   业务id
     * @param platform 视频流平台，见CloudClinicSetConstant.java
     * @return
     */
    @RpcService
    public String getMeetingRoom(int bussType, int bussId, String platform) {
        return createMeetingRoom(bussType, bussId, platform, null);
    }

    /**
     * 创建会议房间
     *
     * @param bussType 业务类型,见VideoInfoConstant.java
     * @param bussId   业务id
     * @param platform 视频流平台，见CloudClinicSetConstant.java
     * @return
     */
    private String createMeetingRoom(int bussType, int bussId, String platform, Integer doctorId) {
        AppointRecordDAO appointDao = DAOFactory.getDAO(AppointRecordDAO.class);
        MeetClinicDAO meetClinicDAO = DAOFactory.getDAO(MeetClinicDAO.class);
        PatientDAO patientDao = DAOFactory.getDAO(PatientDAO.class);
        VideoInfoDAO videoDao = DAOFactory.getDAO(VideoInfoDAO.class);

        HashMap<String, Object> res = null;
        String roomName = "";
        String patName = "";
        String mpi = "";
        String preTitle = "";
        if (VideoInfoConstant.VIDEO_BUSSTYPE_APPOINT == bussType) {
            AppointRecord appointRecord = appointDao.get(bussId);
            if (appointRecord == null) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "不存在该业务单！");
            }
            Integer telClinicFlag = appointRecord.getTelClinicFlag();
            if (telClinicFlag == null || (!telClinicFlag.equals(1) && !telClinicFlag.equals(2))) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "该预约单信息不是远程门诊信息，无法创建视频");
            }
            Integer ClinicObject = appointRecord.getClinicObject();
            if (ClinicObject != null && ClinicObject != 2) {
                appointRecord = appointDao.getByTelClinicIdAndClinicObject(appointRecord.getTelClinicId(), 2);
                bussId = appointRecord.getAppointRecordId();
            }
            mpi = appointRecord.getMpiid();
            roomName = "的远程云门诊";
            preTitle = "患者";
        } else if (VideoInfoConstant.VIDEO_BUSSTYPE_MEETCLINIC == bussType) {
            MeetClinic meetClinic = meetClinicDAO.getByMeetClinicId(bussId);
            if (meetClinic == null) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "不存在该业务单！");
            }
            mpi = meetClinic.getMpiid();
            roomName = "的会诊";
        }

        Patient patient = patientDao.get(mpi);
        if (patient != null && !StringUtils.isEmpty(patient.getPatientName())) {
            patName = patient.getPatientName();
        }

        StringBuilder meeting_name = new StringBuilder(preTitle).append(patName).append(roomName);

        //小鱼平台
        if (CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_XIAOYU.equals(platform)) {
            res = createXiaoYuMeeting(meeting_name.toString(), VideoInfoConstant.VIDEO_FLAG_NGARI);
        }

        if (res == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "发起失败，请检查您的网络连接后重试！");
        }
        String roomId = (String) res.get("roomId");
        String detail = (String) res.get("detail");

        if (!StringUtils.isEmpty(roomId) && !StringUtils.isEmpty(detail)) {
            VideoInfo info = new VideoInfo();
            info.setPlatform(platform);
            info.setBussId(bussId);
            info.setBussType(bussType);
            info.setRoomId(roomId);
            info.setDetail(detail);
            info.setCreateDate(Context.instance().get("date.datetime", Date.class));
            videoDao.save(info);
        }

        return detail;
    }

    /**
     * 创建小鱼平台视频会议房间,会议房间有效时间为一天
     *
     * @param meeting_name 会议名称
     * @return 成功({"meetingNumber":"910031249680","password":"108352","shareUrl":"http://www.Ainemo.com/page/third/ZLC5BV6Y"})
     * 失败({"developerMessage":"","userMessage":"openapi.invalid.signature","errorCode":60003,"moreInfo":"http://www.ainemo.com/errors/60003"})
     */
    public HashMap<String, Object> createXiaoYuMeeting(String meeting_name, int flag) {
        Date now = Context.instance().get("date.now", Date.class);
        Date oneDay = DateConversion.getDateTimeDaysAgo(-1);//一天后

        HashMap<String, String> params = new HashMap<>();
        params.put("meeting_name", meeting_name);
        params.put("start_time", String.valueOf(now.getTime()));
        params.put("end_time", String.valueOf(oneDay.getTime()));
        params.put("require_password", "false");
        params.put("autoRecord", "true");

        String res = Ainemo.createMeeting(params, flag);


        HashMap<String, Object> map = JSONUtils.parse(res, HashMap.class);
        String roomId = (String) map.get("meetingNumber");

        if (StringUtils.isEmpty(roomId)) {
            logger.error("创建会议失败！" + res);
            throw new DAOException(ErrorCode.SERVICE_ERROR, "发起失败，请检查您的网络连接后重试！");
        }

        HashMap<String, Object> returnMap = new HashMap<>();
        returnMap.put("roomId", roomId);
        returnMap.put("detail", res);
        return returnMap;
    }

    private List<Map<String, Object>> msgPushForMeetToAll(int meetClinicId, int doctorId) {
        MeetClinicDAO meetClinicDAO = DAOFactory.getDAO(MeetClinicDAO.class);
        MeetClinicResultDAO resultDAO = DAOFactory.getDAO(MeetClinicResultDAO.class);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        UserSevice service = new UserSevice();
        MeetClinic meetClinic = meetClinicDAO.getByMeetClinicId(meetClinicId);
        List<MeetClinicResult> mrs = resultDAO.findByMeetClinicId(meetClinicId);
        List<Map<String, Object>> list = new ArrayList<>();

        Integer requestId = meetClinic.getRequestDoctor();
        if (requestId != doctorId) {
            Doctor d = doctorDAO.get(requestId);
            if (d != null && !StringUtils.isEmpty(d.getMobile())) {
                Map<String, Object> map = new HashMap<>();
                map.put("mobile", d.getMobile());
                map.put("doctorId", requestId);
                map.put("tokenId", service.getDoctorUrtIdByDoctorId(requestId));
                list.add(map);
            }
        }
        for (MeetClinicResult mr : mrs) {
            Integer effeStatus = mr.getEffectiveStatus();
            if (effeStatus != null && MeetClinicConstant.EFFECTIVESTATUS_INVALID.equals(effeStatus)) {
                continue;
            }
            //有执行医生直接添加执行医生，若没有执行医生，判断目标医生是否是团队，是团队则跳过
            Integer target = mr.getTargetDoctor();
            Boolean teams = doctorDAO.getTeamsByDoctorId(target);
            Integer exe = mr.getExeDoctor();
            Integer status = mr.getExeStatus();
            if (status > 2) {
                continue;
            }
            if (exe != null && exe > 0) {
                if (exe != doctorId) {
                    Doctor d = doctorDAO.get(exe);
                    if (d != null && !StringUtils.isEmpty(d.getMobile())) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("mobile", d.getMobile());
                        map.put("doctorId", exe);
                        map.put("tokenId", service.getDoctorUrtIdByDoctorId(exe));
                        list.add(map);
                    }
                }
            } else if (teams == null || !teams) {
                if (target != doctorId) {
                    Doctor d = doctorDAO.get(target);
                    if (d != null && !StringUtils.isEmpty(d.getMobile())) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("mobile", d.getMobile());
                        map.put("doctorId", target);
                        map.put("tokenId", service.getDoctorUrtIdByDoctorId(target));
                        list.add(map);
                    }
                }
            }
        }
        return list;
    }

    /**
     * 查询小鱼房间列表状态
     *
     * @param rooms
     * @return
     */
    @RpcService
    public List<Map<String, Object>> getState(List<String> rooms) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (String room : rooms) {
            HashMap<String, Object> map = new HashMap<>();
            map.put("roomId", room);
            String result = Ainemo.meetingInfo(room, VideoInfoConstant.VIDEO_FLAG_NGARI);
            if (StringUtils.isEmpty(result)) {
                map.put("state", null);
                list.add(map);
                continue;
            }
            Map<String, Object> response = JSONUtils.parse(result, HashMap.class);
            String meetingRoomState = (String) response.get("meetingRoomState");
            map.put("state", meetingRoomState);
            list.add(map);
        }
        return list;
    }

    public String getExtIdByDomain(String domainName) {
        String[] domains = domainName.trim().split(",");
        String extId = null;
        for (String domain : domains) {
            if (domain.equals(domainName)) {
                extId = Ainemo.getExtid_other();
            }
        }
        if (StringUtils.isEmpty(extId)) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该域名未授权");
        }
        return extId;
    }

    /**
     * 查询某个会议室的视频列表
     *
     * @param roomNumber
     * @param startTime  起始时间
     * @param endTime    截止时间
     *                   startTime必须小于endTime且若startTime不为0则时间差不能超过24小时
     * @author zhangsl 2017-02-21 14:20:32
     */
    public List<Integer> getVodIdByRoomNumber(String roomNumber, Date startTime, Date endTime, int flag) {
        String response = Ainemo.searchVodIdList(roomNumber, startTime, endTime, VideoInfoConstant.VIDEO_FLAG_NGARI);
        List<Integer> list = new ArrayList<>();
        if (StringUtils.isNotBlank(response)) {
            List<JSONObject> vods = JSONObject.parseArray(response, JSONObject.class);
            for (JSONObject vod : vods) {
                list.add(Integer.parseInt(vod.get("vodId").toString()));
            }
        }
        return list;
    }

    @RpcService
    public List<Integer> testSearchVodIdList(int dayAgo, int hour, String roomNumber) {
        Date start = DateConversion.getDateBFtHour(DateConversion.getDateTimeDaysAgo(dayAgo), hour);
        Date end = DateConversion.getDateTimeDaysAgo(dayAgo);
        return getVodIdByRoomNumber(roomNumber, start, end, VideoInfoConstant.VIDEO_FLAG_NGARI);
    }

    @RpcService
    public List<Map<String, Object>> testGetVodUrlList(List<Integer> vodIds) {
        return Ainemo.getVodUrlList(vodIds, VideoInfoConstant.VIDEO_FLAG_NGARI);
    }

    /**
     * 定时拉取视频下载地址
     */
    @RpcService
    public void updateVideoVodInfo() {
        VideoVodDAO videoVodDAO = DAOFactory.getDAO(VideoVodDAO.class);
        VideoInfoDAO videoInfoDAO = DAOFactory.getDAO(VideoInfoDAO.class);
        Date endTime = new Date();
        Date endDate = eh.utils.DateConversion.getDateAftXDays(endTime, -1);
        List<VideoInfo> infos = videoInfoDAO.findVideoInfosHasNotVod(endDate, CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_XIAOYU);
        logger.info("get videoInfos:" + JSONObject.toJSONString(infos));
        List<VideoVod> vods = new ArrayList<>();
        for (VideoInfo info : infos) {
            String roomId = info.getRoomId();
            List<Integer> vodIds = this.getVodIdByRoomNumber(roomId, null, endTime, VideoInfoConstant.VIDEO_FLAG_NGARI);
            Integer videoInfoId = info.getId();
            Integer bussType = info.getBussType();
            Integer bussID = info.getBussId();
            Date createDate = info.getCreateDate();
            Date endDateinfo = info.getEndDate();
            List<Map<String, Object>> urlList = Ainemo.getVodUrlList(vodIds, VideoInfoConstant.VIDEO_FLAG_NGARI);
            for (Map<String, Object> url : urlList) {
                if (url == null || StringUtils.isEmpty((String) url.get("url"))) {
                    continue;
                }
                VideoVod vod = new VideoVod();
                vod.setVideoInfoId(videoInfoId);
                vod.setRoomId(roomId);
                vod.setBussType(bussType);
                vod.setBussID(bussID);
                vod.setCreateDate(createDate);
                vod.setEndDate(endDateinfo);
                vod.setUrl((String) url.get("url"));
                vod.setUploadFlag(false);//待上传
                vods.add(vod);
            }
            videoInfoDAO.updateIsVodById(true, videoInfoId);
        }
        for (VideoVod vod : vods) {
            videoVodDAO.save(vod);
        }
        logger.info("videoInfo to videoVod vods msg:" + JSONObject.toJSONString(vods));
    }
    /**
     * 定时上传已拉取地址的视频
     * zhangsl 2017-04-20 18:13:34
     */
    @RpcService
    public void uploadVideoVod() {
        VideoVodDAO vodDAO=DAOFactory.getDAO(VideoVodDAO.class);
        List<VideoVod> vods=vodDAO.queryVideoVodsNotUpload();
            AppointRecordDAO appointRecordDAO=DAOFactory.getDAO(AppointRecordDAO.class);
            DoctorDAO doctorDAO=DAOFactory.getDAO(DoctorDAO.class);
            MeetClinicDAO meetClinicDAO=DAOFactory.getDAO(MeetClinicDAO.class);
            for(VideoVod vod:vods) {
                try {
                    String url = vod.getUrl();
                    URL urlObject = new URL(url);
                    URLConnection conn = urlObject.openConnection();
                    conn.setDoOutput(true);
                    conn.setDoInput(true);
                    conn.setConnectTimeout(30000);//设置连接超时时间
                    //conn.setReadTimeout();//设置读取视频流超时时间
                    conn.connect();
                    InputStream is = conn.getInputStream();
                    File f=new File("videoFile.mp4");
                    FileOutputStream fos=new FileOutputStream(f);
                    byte[] data = new byte[20480];
                    int length = 0;
                    while((length = is.read(data)) > 0) {//视频流不是一次全部发送的，因此需要个临时文件做中转站
                        fos.write(data, 0, length);
                    }
                    fos.flush();
                    fos.close();
                    is.close();
                    Long size = f.length();
                    if(size<=0){
                        throw new IOException("Get InputStream error for URL:"+url);
                    }

                    String temp[] = url.split("/");
                    String fileName = "video_" + temp[temp.length - 2] + ".mp4";//组装小鱼的vodId
                    Date now = new Date();
                    FileMetaRecord meta = new FileMetaRecord();
                    meta.setFileName(fileName);
                    meta.setCatalog("ngarimedia");
                    meta.setTenantId("eh");
                    meta.setMode(0);//31是未登录可用,mode=0需要验证
                    meta.setManageUnit("eh");
                    meta.setContentType("video/mp4");
                    meta.setFileSize(size);
                    String mobile = "";
                    if (vod.getBussType() == 1) {//云门诊
                        mobile = doctorDAO.getMobileByDoctorId(appointRecordDAO.getByAppointRecordId(vod.getBussID()).getDoctorId());
                    } else if (vod.getBussType() == 2) {//会诊
                        mobile = doctorDAO.getMobileByDoctorId(meetClinicDAO.getByMeetClinicId(vod.getBussID()).getRequestDoctor());
                    }
                    meta.setOwner(mobile);
                    meta.setLastModify(now);
                    meta.setUploadTime(now);

                    FileService.instance().upload(meta, f);
                    vodDAO.updateVideoVodsToUpload(vod.getId(), meta.getFileId());
                    f.delete();
                    logger.info("upload videoVod[" + vod.getId() + "] success,fileId[" + meta.getFileId() + "],fileSize[" + size + "]");
                } catch (Exception e) {
                    logger.error("videoVod[" + vod.getId() + "] upload falied:" + e);
                }
            }
    }
}
