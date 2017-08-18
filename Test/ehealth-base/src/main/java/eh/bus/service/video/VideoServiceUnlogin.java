package eh.bus.service.video;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.bus.constant.CloudClinicSetConstant;
import eh.bus.constant.VideoInfoConstant;
import eh.bus.dao.VideoMessageDAO;
import eh.bus.service.VideoService;
import eh.entity.bus.VideoMessage;
import eh.util.Ainemo;
import org.apache.commons.lang3.StringUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Luphia on 2016/11/23.
 */
public class VideoServiceUnlogin {

    /**
     * 创建房间返回房间号
     *
     * @param name
     * @param domainName
     * @param meeting_name
     * @return
     */
    @RpcService
    public String createConference(String name, String domainName, String meeting_name) {
        if (StringUtils.isEmpty(name)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "name is required!");
        }
        if (StringUtils.isEmpty(domainName)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "domainName is required!");
        }
        if (StringUtils.isEmpty(meeting_name)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "meeting_name is required!");
        }
        VideoService videoService = new VideoService();
        HashMap<String, Object> map = videoService.createXiaoYuMeeting(meeting_name, VideoInfoConstant.VIDEO_FLAG_OTHER);
        if (map == null || StringUtils.isEmpty((String) map.get("roomId"))) {
            return null;
        }
        String roomId = (String) map.get("roomId");
        VideoMessage videoMessage = new VideoMessage();
        videoMessage.setCreateDate(new Date());
        videoMessage.setDetail((String) map.get("detail"));
        videoMessage.setDomainName(domainName);
        videoMessage.setName(name);
        videoMessage.setPlatform(CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_XIAOYU);
        videoMessage.setRoomId(roomId);
        VideoMessageDAO messageDAO = DAOFactory.getDAO(VideoMessageDAO.class);
        messageDAO.save(videoMessage);
        return roomId;
    }

    /**
     * 获取房间状态
     *
     * @param roomId
     * @return
     */
    @RpcService
    public String getConferenceStatus(String roomId) {
        if (StringUtils.isEmpty(roomId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "roomId is required!");
        }
        String result = Ainemo.meetingInfo(roomId, VideoInfoConstant.VIDEO_FLAG_OTHER);

        if (StringUtils.isEmpty(result)) {
            return "closed";
        }
        Map<String, Object> response = JSONUtils.parse(result, HashMap.class);
        return (String) response.get("meetingRoomState");
    }

    /**
     * 关闭会议室
     *
     * @param roomId
     */
    @RpcService
    public boolean endConference(String roomId) {
        if (StringUtils.isEmpty(roomId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "roomId is required!");
        }
        VideoMessageDAO messageDAO = DAOFactory.getDAO(VideoMessageDAO.class);
        messageDAO.updateEndDates(roomId);
        Ainemo.conferenceControl(roomId, VideoInfoConstant.VIDEO_FLAG_OTHER);
        return true;
    }

    /**
     * 根据域名获取小鱼账号
     *
     * @param domainName
     * @return
     */
    @RpcService
    public String getExtIdByDomain(String domainName) {
        VideoService videoService = new VideoService();
        return videoService.getExtIdByDomain(domainName);
    }
}
