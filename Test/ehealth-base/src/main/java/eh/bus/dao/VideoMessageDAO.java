package eh.bus.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import eh.entity.bus.VideoMessage;

/**
 * Created by Luphia on 2016/11/23.
 */
public abstract class VideoMessageDAO extends HibernateSupportDelegateDAO<VideoMessage> {

    public VideoMessageDAO() {
        super();
        this.setEntityName(VideoMessage.class.getName());
        this.setKeyField("id");
    }

    @DAOMethod(sql = "update VideoMessage set endDate=now() where roomId=:roomId and endDate is null")
    public abstract void updateEndDates(@DAOParam("roomId")String roomId);
}
