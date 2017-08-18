package eh.bus.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.annotation.RpcService;
import eh.entity.bus.VideoInfo;

import java.util.Date;
import java.util.List;

public abstract class VideoInfoDAO extends HibernateSupportDelegateDAO<VideoInfo> {
    public VideoInfoDAO() {
        super();
        this.setEntityName(VideoInfo.class.getName());
        this.setKeyField("id");
    }

    @DAOMethod(limit = 1, sql = "from VideoInfo where bussType=:bussType and bussId=:bussId and platform=:platform and endDate is null order by createDate desc")
    public abstract List<VideoInfo> findBusVideoInfoByPlatform(@DAOParam("bussType") Integer bussType, @DAOParam("bussId") Integer bussId, @DAOParam("platform") String platform);

    @DAOMethod(sql = "from VideoInfo where roomId=:roomId and platform=:platform and endDate is null")
    public abstract List<VideoInfo> findByRoomIdAndPlatform(@DAOParam("roomId") String roomId, @DAOParam("platform") String platform);

    @DAOMethod(sql = "from VideoInfo where videoCallId=:videoCallId and platform=:platform and endDate is null")
    public abstract List<VideoInfo> findByVideoCallIdAndPlatform(@DAOParam("videoCallId") String videoCallId, @DAOParam("platform") String platform);

    @DAOMethod(sql = "update VideoInfo set endDate=:endDate where roomId=:roomId and platform=:platform and endDate is null")
    public abstract void updateEndDateByRoomId(@DAOParam("endDate") Date endDate, @DAOParam("roomId") String roomId, @DAOParam("platform") String platform);

    @DAOMethod(sql = "update VideoInfo set endDate=:endDate where id=:id and platform=:platform and endDate is null")
    public abstract void updateEndDateById(@DAOParam("endDate") Date endDate,
                                           @DAOParam("id") int id, @DAOParam("platform") String platform);

    @DAOMethod(sql = "select count(*) from VideoInfo where bussType=:bussType and bussId=:bussId and platform=:platform")
    public abstract long getVideoCount(@DAOParam("bussType") Integer bussType, @DAOParam("bussId") Integer bussId, @DAOParam("platform") String platform);

    @DAOMethod(limit = 0, sql = "from VideoInfo where endDate is not null and endDate<=:endDate and (isVod=0 or isVod is null) and platform=:platform")
    public abstract List<VideoInfo> findVideoInfosHasNotVod(@DAOParam("endDate") Date endDate, @DAOParam("platform") String platform);

    @DAOMethod(sql = "update VideoInfo set isVod=:isVod where id=:id")
    public abstract void updateIsVodById(@DAOParam("isVod") boolean isVod, @DAOParam("id") int id);

    @RpcService
    @DAOMethod(sql = "update VideoInfo set endDate=NOW() where createDate<=:createDate AND (endDate is NULL OR endDate='')")
    public abstract void updateEndTimeOneDayAgo(@DAOParam("createDate") Date createDate);

}
