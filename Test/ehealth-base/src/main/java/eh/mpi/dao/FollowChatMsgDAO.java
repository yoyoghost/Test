package eh.mpi.dao;

import com.alibaba.fastjson.JSONObject;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import eh.bus.constant.ConsultConstant;
import eh.entity.mpi.Aticle;
import eh.entity.mpi.FollowAssess;
import eh.entity.mpi.FollowChatMsg;
import eh.entity.mpi.FollowSimpleMsg;
import eh.utils.DateConversion;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author renzh
 * @date 2017-02-14 下午 17:03
 */
public abstract class FollowChatMsgDAO extends HibernateSupportDelegateDAO<FollowChatMsg> {

    public FollowChatMsgDAO() {
        super();
        this.setEntityName(FollowChatMsg.class.getName());
        this.setKeyField("id");
    }

    /**
     * 修改会话随访对应的除语音消息之外的所有消息为已读状态
     * @param requestMpi
     * @param doctorId
     */
    @DAOMethod(sql = "UPDATE FollowChatMsg SET hasRead=1 WHERE mpiId=:requestMpi AND doctorId=:doctorId AND msgType not in (3,94)")
    public abstract void updateDoctorMessageToHasReadExceptAudio(@DAOParam("requestMpi") String requestMpi,@DAOParam("doctorId") Integer doctorId);

    /**
     * 分页查询咨询对应的消息列表
     * @param requestMpi
     * @param doctorId
     * @param startIndex
     * @param pageSize
     * @return
     */
    public List<FollowChatMsg> findDoctorFollowChatMessageList(
            final String requestMpi,
            final Integer doctorId,
            final Date firstRequestTime,
            final int startIndex,
            final int pageSize) {
        AbstractHibernateStatelessResultAction<List<FollowChatMsg>> action = new AbstractHibernateStatelessResultAction<List<FollowChatMsg>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = "FROM FollowChatMsg WHERE deleted=0 AND mpiId=:requestMpi AND doctorId = :doctorId AND createTime<:firstRequestTime ORDER BY id DESC";
                Query query = ss.createQuery(hql);
                query.setParameter("requestMpi", requestMpi);
                query.setParameter("doctorId", doctorId);
                query.setParameter("firstRequestTime", firstRequestTime);
                query.setFirstResult(startIndex);
                query.setMaxResults(pageSize);
                setResult(query.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 查询用户会话随访未读消息总数
     * @param requestMpi
     * @return
     */
    public Long findUserNotReadFollowChatMsgCountWithMpiId(final String requestMpi){
        AbstractHibernateStatelessResultAction<Long> action = new AbstractHibernateStatelessResultAction<Long>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String queryFolllowChatMsgList = "SELECT  (COUNT(*)-SUM(hasRead))   " +
                        "FROM FollowChatMsg WHERE deleted <2  AND mpiId = :requestMpi ";
                Query q1 = ss.createQuery(queryFolllowChatMsgList);
                q1.setParameter("requestMpi", requestMpi);
                Long count  = (Long)q1.uniqueResult();
                setResult(count);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }


    /**
     * 获取消息未读数
     * @param sessionId
     * @param receiverId
     * @return
     */
    public Integer getNotReadCountBySessionIdAndReceiverId(final String sessionId, final String receiverId){
        AbstractHibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                String sql = "SELECT count(*) FROM mpi_followchat_msg g INNER JOIN mpi_followchat t ON g.followChatId = t.id " +
                        "AND g.hasRead = 0 AND t.sessionID=:sessionId AND g.receiverId=:receiverId AND g.deleted = 0";
                BigInteger count = (BigInteger)statelessSession.createSQLQuery(sql).setParameter("sessionId",sessionId)
                        .setParameter("receiverId",receiverId).list().get(0);
                setResult(count.intValue());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 健康端查看消息数以及最新消息
     * @param receiverId
     * @return
     */
    public List<FollowSimpleMsg> getFollowChatMsgForPatient(final String receiverId,final Integer urt){
        AbstractHibernateStatelessResultAction<List> action = new AbstractHibernateStatelessResultAction<List>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String queryFolllowChatMsgList = "SELECT a.*,gender,photo,name FROM(SELECT count(*),a0.* FROM (SELECT senderId,followChatId,hasEnd,msgType,msgContent,sessionID,hasRead,createTime,receiverId,receiverRole " +
                        "FROM mpi_followchat_msg g INNER JOIN " +
                        "mpi_followchat t ON g.followChatId = t.id AND (g.receiverId=:receiverId OR g.senderId=:receiverId) " +
                        "AND g.deleted = 0  ORDER BY g.id DESC) a0 GROUP BY a0.sessionID) a LEFT JOIN base_doctor b ON (a.senderId = b.DoctorId) OR (a.receiverId = b.DoctorId) " +
                        "ORDER BY a.followChatId DESC";
                List<Object[]> l = (List<Object[]>)ss.createSQLQuery(queryFolllowChatMsgList).setParameter("receiverId",receiverId).list();
                List<FollowSimpleMsg> followSimpleMsgs = new ArrayList<>();
                FollowSimpleMsg followSimpleMsg;
                for(Object[] o: l){
                    followSimpleMsg = new FollowSimpleMsg();
                    if(ConsultConstant.MSG_ROLE_TYPE_PATIENT.equals(o[10]==null?"":o[10].toString())) {
                        followSimpleMsg.setDoctorId(Integer.parseInt(o[1].toString()));
                    }else{
                        followSimpleMsg.setDoctorId(Integer.parseInt(o[9].toString()));
                    }
                    followSimpleMsg.setHasEnd((Boolean)o[3]);
                    int mstType = Integer.parseInt(o[4].toString());
                    followSimpleMsg.setMsgType(mstType);
                    if(mstType==2){
                        followSimpleMsg.setMsgContent("[图片]");
                    }else if(mstType==3){
                        followSimpleMsg.setMsgContent("[语音]");
                    }else if(mstType==91 || mstType==92){
                        followSimpleMsg.setMsgContent("["+JSONObject.parseObject(o[5]==null?"":o[5].toString(), FollowAssess.class).getTitle()+"]");
                    }else if(mstType==93){
                        followSimpleMsg.setMsgContent("[文章]"+JSONObject.parseObject(o[5]==null?"":o[5].toString(), Aticle.class).getTitle());
                    }else{
                        followSimpleMsg.setMsgContent(o[5]==null?"":o[5].toString());
                    }
                    followSimpleMsg.setSessionId(o[6]==null?"":o[6].toString());
                    Integer notReadCount = getNotReadCountBySessionIdAndReceiverId(o[6]==null?"":o[6].toString(),receiverId);
                    followSimpleMsg.setHasRead(0==notReadCount?true:false);
                    followSimpleMsg.setCreateTime((Date)o[8]);
                    followSimpleMsg.setDoctorSex(o[11]==null?"":o[11].toString());
                    followSimpleMsg.setDoctorPhoto(o[12]==null?"":o[12].toString());
                    followSimpleMsg.setDoctorName(o[13]==null?"":o[13].toString());
                    followSimpleMsg.setRequestMode(ConsultConstant.FOLLOW_MSG_TYPE);
                    followSimpleMsg.setUrt(urt);
                    followSimpleMsg.setTimeText(DateConversion.handleTimeText((Date)o[8]));
                    followSimpleMsg.setNotReadCount(notReadCount);
                    followSimpleMsgs.add(followSimpleMsg);
                }
                setResult(followSimpleMsgs);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    @DAOMethod(sql = "SELECT count(*) as notReadCount,senderId as doctorId FROM FollowChatMsg WHERE receiverId=:receiverId AND deleted = 0 AND hasRead = 1 GROUP BY senderId")
    public abstract Object getAllDoctorNotReadMsg(@DAOParam("receiverId") String receiverId);

    /**
     * 根据群组id查询环信最新的消息
     * @param sessionId
     * @param startIndex
     * @param pageSize
     * @return
     */
    public List<FollowChatMsg> findLatestChatMessage(final String sessionId, final Integer startIndex, final  Integer pageSize) {
        AbstractHibernateStatelessResultAction<List<FollowChatMsg>> action = new AbstractHibernateStatelessResultAction<List<FollowChatMsg>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = "FROM FollowChatMsg WHERE followChatId IN(SELECT id from FollowChat WHERE sessionId = :sessionId) AND deleted=0 ORDER BY id DESC";
                Query query = ss.createQuery(hql);
                query.setParameter("sessionId", sessionId);
                query.setFirstResult(startIndex);
                query.setMaxResults(pageSize);
                setResult(query.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    @DAOMethod(sql = "update FollowChatMsg set hasRead = 1 where id = CONVERT(:msgId, SIGNED) or msgExtra = :msgId")
    public abstract void updateVoiceOfFollowMsgRead(@DAOParam("msgId") String msgId);

    @DAOMethod
    public abstract FollowChatMsg getByOtherId(Integer OtherId);

    //查询最近一条患者回复
    @DAOMethod(sql = "from FollowChatMsg where senderRole='patient' and doctorId=:doctorId and mpiId=:mpiId order by sendTime desc")
    public abstract List<FollowChatMsg> findLatestPatientReply(@DAOParam("mpiId") String mpiId, @DAOParam("doctorId") Integer doctorId, @DAOParam(pageStart = true) int start, @DAOParam(pageLimit = true) int limit);

    @DAOMethod(sql = "update FollowChatMsg set hasRead=1 where id=:id")
    public abstract void updateMsgHasRead(@DAOParam("id") Long id);

}
