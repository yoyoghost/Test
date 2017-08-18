package eh.bus.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.util.annotation.RpcService;
import eh.bus.dao.VideoVodDAO;
import eh.entity.bus.VideoVod;

import java.util.Date;

/**
 * @author jianghc
 * @create 2017-02-23 16:10
 **/
public class VideoVodService {
    private VideoVodDAO videoVodDAO;
    public VideoVodService() {
        videoVodDAO = DAOFactory.getDAO(VideoVodDAO.class);
    }

    @RpcService
    public QueryResult<VideoVod> queryVideoVods(Date startDate,Date endDate,Integer start,Integer  limit){
        return videoVodDAO.queryVideoVods(startDate,endDate,start,limit);
    }


}
