package eh.bus.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.annotation.RpcSupportDAO;
import eh.entity.bus.VideoVod;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by Luphia on 2017/2/22.
 */
@RpcSupportDAO
public abstract class VideoVodDAO extends HibernateSupportDelegateDAO<VideoVod> {

    public VideoVodDAO() {
        super();
        setEntityName(VideoVod.class.getName());
        setKeyField("id");
    }

    public VideoVod save(VideoVod videoVod){
        if(videoVod.getUploadFlag()==null){
            videoVod.setUploadFlag(false);
        }
        return super.save(videoVod);
    }


    public QueryResult<VideoVod> queryVideoVods(final Date startDate, final Date endDate, final Integer start, final Integer  limit){

        if(startDate==null){
            throw new DAOException(DAOException.VALUE_NEEDED," startDate is require");
        }
        if(endDate==null){
            throw new DAOException(DAOException.VALUE_NEEDED," endDate is require");
        }

        HibernateStatelessResultAction<QueryResult<VideoVod>> action = new AbstractHibernateStatelessResultAction<QueryResult<VideoVod>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        " from VideoVod where Date(createDate)>=Date(:startDate) and Date(createDate)<=:endDate");
                Query countQuery = ss.createQuery(" select count (*) "+hql.toString());
                countQuery.setParameter("startDate",startDate);
                countQuery.setParameter("endDate",endDate);
                Long total = (Long) countQuery.uniqueResult();
                Query query = ss.createQuery(hql.toString()+" order by id desc");
                query.setParameter("startDate",startDate);
                query.setParameter("endDate",endDate);
                query.setFirstResult(start);
                query.setMaxResults(limit);
                List<VideoVod> list = query.list();
                if(list==null){
                    list = new ArrayList<VideoVod>();
                }
                setResult(new QueryResult<VideoVod>(total,start,limit,list));
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 获取待上传的视频
     * @return
     */
    @DAOMethod(sql = "from VideoVod where uploadFlag=false and url like 'http%'",limit = 0)
    public abstract List<VideoVod> queryVideoVodsNotUpload();

    /**
     * 获取待上传的视频
     * @return
     */
    @DAOMethod(sql = "update VideoVod set uploadFlag=true,fileId=:fileId where id=:id")
    public abstract void updateVideoVodsToUpload(@DAOParam("id") int id,@DAOParam("fileId") int fileId);

}
