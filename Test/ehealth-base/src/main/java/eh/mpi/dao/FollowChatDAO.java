package eh.mpi.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import eh.entity.mpi.FollowChat;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.Date;
import java.util.List;

/**
 * @author renzh
 * @date 2017-02-14 下午 13:48
 */
public abstract class FollowChatDAO extends HibernateSupportDelegateDAO<FollowChat> {

    public FollowChatDAO() {
        super();
        this.setEntityName(FollowChat.class.getName());
        this.setKeyField("id");
    }

    @DAOMethod(sql = "select f from FollowChat f, eh.entity.msg.Group g where f.chatType =1 and f.mpiId=:mpiId and f.chatDoctor=:doctorId and f.sessionID=g.groupId and g.nick like :nick order by id desc")
    public abstract List<FollowChat> findByMpiIdAndDoctorIdAndGroupNick(@DAOParam("mpiId") String requestMpi, @DAOParam("doctorId") Integer doctorId, @DAOParam("nick") String nick);

    /**
     * 根据sessionId 查找会话随访
     * @param sessionID
     * @return
     */
    @DAOMethod(orderBy = "id DESC")
    public abstract List<FollowChat> findBySessionID(String sessionID);

    /**
     * 查询未结束的会话随访
     * @return
     */
    @DAOMethod(sql = "from FollowChat where hasEnd = 0 and chatType = 1")
    public abstract List<FollowChat> findNotEndChat();

    @DAOMethod(sql = "update FollowChat set sessionID=:sessionId where id=:id")
    public abstract void updateSessionIdById(@DAOParam("id") Integer id, @DAOParam("sessionId") String sessionId);

    /**
     * 查询医生随访的总人数
     * @param doctorId
     * @return
     */
    public Long getCountThisDocPatientSum(final Integer doctorId){
        HibernateStatelessResultAction<List<FollowChat>> action = new AbstractHibernateStatelessResultAction<List<FollowChat>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder("FROM FollowChat WHERE chatDoctor = :doctorId " +
                        "group by IFNULL(mpiId,''),IFNULL(patientMobile,''),IFNULL(patientName,'')");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("doctorId", doctorId);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (long)action.getResult().size();
    }

    /**
     * 获取医生和患者今日随访记录 以前的数据可能一条产生多天记录 但是3.8.8之后一天只会生成一条会话随访记录
     * zhongzx
     * @param mpiId
     * @param doctorId
     * @param time
     * @return
     */
    public List<FollowChat> findFollowChatWithTime(final String mpiId, final Integer doctorId, final Date time){
        HibernateStatelessResultAction<List<FollowChat>> action = new AbstractHibernateStatelessResultAction<List<FollowChat>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder("from FollowChat where mpiId=:mpiId and chatDoctor=:doctorId and chatType = 1 and sessionID is not null");
                if(null != time){
                    hql.append(" and DATE_FORMAT(:time,'%d-%m-%y') = DATE_FORMAT(requestTime,'%d-%m-%y')");
                }
                hql.append(" order by id desc");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("mpiId", mpiId);
                q.setParameter("doctorId", doctorId);
                if(null != time) {
                    q.setParameter("time", time);
                }
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 本月随访次数统计
     * @param doctorId
     * @param startTime
     * @param endTime
     * @return
     */
    public List<FollowChat> getCountThisMon(final Integer doctorId, final Date startTime, final Date endTime) {
        if (null == startTime || null == endTime) {
            throw new DAOException(DAOException.VALUE_NEEDED, "startTime or endTime is null");
        }
        HibernateStatelessResultAction<List<FollowChat>> action = new AbstractHibernateStatelessResultAction<List<FollowChat>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder("FROM FollowChat WHERE chatDoctor = :doctorId AND requestTime BETWEEN (:startTime) AND (:endTime) GROUP BY IFNULL(mpiId,''),DATE_FORMAT(requestTime,'%d-%m-%y'),IFNULL(patientMobile,''),IFNULL(patientName,'')");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("doctorId", doctorId);
                q.setParameter("endTime", endTime);
                q.setParameter("startTime", startTime);
                List list = q.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }


    /**
     * 所有随访次数统计
     * @param doctorId
     * @return
     */
    public List<FollowChat> getCountThisDocSum(final Integer doctorId){
    	 HibernateStatelessResultAction<List<FollowChat>> action = new AbstractHibernateStatelessResultAction<List<FollowChat>>() {
             @Override
             public void execute(StatelessSession ss) throws Exception {
                 StringBuilder hql = new StringBuilder("FROM FollowChat WHERE chatDoctor = :doctorId GROUP BY IFNULL(mpiId,''),DATE_FORMAT(requestTime,'%d-%m-%y'),IFNULL(patientMobile,''),IFNULL(patientName,'')");
                 Query q = ss.createQuery(hql.toString());
                 q.setParameter("doctorId", doctorId);
                 List list = q.list();
                 setResult(list);
             }
         };
         HibernateSessionTemplate.instance().executeReadOnly(action);
         return action.getResult();
    }


    /**
     * 排名统计
     * @param organId
     * @param startTime
     * @param endTime
     * @return
     */
    public List<Integer> getfollowChatRankList(final Integer organId, final Date startTime, final Date endTime) {
        if (null == startTime || null == endTime) {
            throw new DAOException(DAOException.VALUE_NEEDED, "startTime or endTime is null");
        }
        HibernateStatelessResultAction<List<Integer>> action = new AbstractHibernateStatelessResultAction<List<Integer>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder("SELECT DISTINCT chatDoctor FROM FollowChat WHERE  chatDoctorOrgan = :organId AND requestTime BETWEEN (:startTime) AND (:endTime)");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("organId", organId);
                q.setParameter("endTime", endTime);
                q.setParameter("startTime", startTime);
                List list = q.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }
}
