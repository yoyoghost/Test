package eh.bus.dao;

import com.google.common.collect.Lists;
import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.util.annotation.RpcService;
import eh.base.dao.DoctorDAO;
import eh.bus.constant.ConsultConstant;
import eh.bus.constant.MsgTypeEnum;
import eh.entity.bus.BusConsultMsg;
import eh.entity.bus.TempMsgVo;
import eh.mindgift.constant.MindGiftConstant;
import eh.utils.ValidateUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public abstract class ConsultMessageDAO extends HibernateSupportDelegateDAO<BusConsultMsg> {
    private static final Log logger = LogFactory.getLog(ConsultMessageDAO.class);

    public static final String MsgTitle = "纳里医生";

    public ConsultMessageDAO() {
        super();
        setEntityName(BusConsultMsg.class.getName());
        setKeyField("id");
    }

    @DAOMethod(sql = "FROM BusConsultMsg WHERE mpiId=:mpiId AND msgType=:msgType")
    public abstract List<BusConsultMsg> findMessageListByMpiIdAndMsgType(
            @DAOParam("mpiId") String mpiId,
            @DAOParam("msgType") short msgType);

    /**
     * 查询给定咨询id对应的消息列表
     * @param consultId
     * @param msgType
     * @return
     */
    @DAOMethod(sql = "FROM BusConsultMsg WHERE consultId=:consultId AND msgType=:msgType")
    public abstract List<BusConsultMsg> findMessageListByConsultIdAndMsgType(
            @DAOParam("consultId") Integer consultId,
            @DAOParam("msgType") short msgType);

    /**
     * 根据消息id更新消息内容
     * @param msgId
     * @param msgContent
     */
    @DAOMethod(sql = "UPDATE BusConsultMsg SET msgContent=:msgContent " +
            "WHERE id=:msgId " )
    public abstract void updateSystemNotificationMessage(
            @DAOParam("msgId") Long msgId,
            @DAOParam("msgContent") String msgContent);

    @DAOMethod(sql = "UPDATE BusConsultMsg SET msgContent=:msgContent, msgType=:msgType " +
            "WHERE id=:msgId " )
    public abstract void updateSystemNotificationMessageAndMsgType(
            @DAOParam("msgId") Long msgId, @DAOParam("msgType") short msgType,
            @DAOParam("msgContent") String msgContent);

    /**
     * 查询医生消息列表，包含未读消息数、最后一条消息时间等
     * @param requestMpi
     * @param doctorIds
     * @return
     *
     * 最后一条消息过滤评价通知消息 zhangsl
     * @Date 2016-11-17 14:30:01
     */
    @RpcService
    public List<TempMsgVo> findDoctorNotReadMessageList(final String requestMpi, final List<Integer> doctorIds){
        if(ValidateUtil.blankList(doctorIds)){
            return new ArrayList<>();
        }
        AbstractHibernateStatelessResultAction<List<TempMsgVo>> action = new AbstractHibernateStatelessResultAction<List<TempMsgVo>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String queryConsultMsgList = "SELECT requestMode, doctorId, (COUNT(*)-SUM(hasRead))   " +
                        "FROM BusConsultMsg WHERE deleted <2 AND doctorId in (:doctorIds) AND mpiId = :requestMpi  AND msgType<>:notType  GROUP BY requestMode, mpiId, doctorId";
                String queryMsgId = "SELECT MAX(id) FROM BusConsultMsg WHERE deleted = 0 AND doctorId in (:doctorIds) AND mpiId = :requestMpi AND msgType<>:notType  GROUP BY requestMode, mpiId, doctorId";
                String queryLastestRecord = "SELECT requestMode, doctorId, msgContent, msgType, createTime FROM BusConsultMsg " +
                        "WHERE id in (:msgIds)  ORDER BY createTime DESC";
                Query q1 = ss.createQuery(queryConsultMsgList);
                q1.setParameterList("doctorIds", doctorIds);
                q1.setParameter("requestMpi", requestMpi);
                q1.setParameter("notType",MsgTypeEnum.EVALUATION.getId());
                List<Object[]> consultMsgList = q1.list();

                Query msgIdQuery = ss.createQuery(queryMsgId);
                msgIdQuery.setParameterList("doctorIds", doctorIds);
                msgIdQuery.setParameter("requestMpi", requestMpi);
                msgIdQuery.setParameter("notType",MsgTypeEnum.EVALUATION.getId());
                List<Object> maxMsgIdList = msgIdQuery.list();
                List<Object[]> latestMsgList = Lists.newArrayList();
                if(ValidateUtil.notBlankList(maxMsgIdList)){
                    Query q2 = ss.createQuery(queryLastestRecord);
                    q2.setParameterList("msgIds", maxMsgIdList);
                    latestMsgList = q2.list();
                }

                setResult(packageMsgList(consultMsgList, latestMsgList));
            }

            private List<TempMsgVo> packageMsgList(List<Object[]> consultMsgList, List<Object[]> latestMsgList){
                List<TempMsgVo> msgList = new ArrayList<>();
                DoctorDAO doctorDAO= DAOFactory.getDAO(DoctorDAO.class);
                for(Object[] lo : latestMsgList){
                    int requestMode = lo[0]==null?ConsultConstant.CONSULT_TYPE_GRAPHIC:(int)lo[0];
                    int doctorId = (int)lo[1];
                    String docName=doctorDAO.getNameById(doctorId);
                    TempMsgVo vo = new TempMsgVo();
//                    vo.setConsultId(consultId);
                    vo.setDoctorId(doctorId);
                    //wx2.9 如果赠送医生心意，则在患者端消息列表显示[赠送给[医生姓名]一个心意]
                    if((short)lo[3]==MsgTypeEnum.MIND_GIFT.getId()){
                        vo.setMsgContent(String.format(MindGiftConstant.showPatientMsg,docName));
                    }else{
                        vo.setMsgContent(String.valueOf(lo[2]));
                    }

                    vo.setMsgType(Integer.valueOf((short)lo[3]));
                    vo.setCreateTime((Date)lo[4]);
                    vo.setRequestMode(requestMode);
                    for(Object[] mo : consultMsgList){
                        int lReqeustMode = mo[0]==null? ConsultConstant.CONSULT_TYPE_GRAPHIC:(int)mo[0];
                        int lDoctorId = (int)mo[1];
                        if(lReqeustMode == requestMode && doctorId==lDoctorId){
                            long x = (long)mo[2];
                            vo.setNotReadCount((int)x);
                        }
                    }
                    msgList.add(vo);
                }
                for(Object[] mo : consultMsgList){
                    int oRequestMode = mo[0]==null?ConsultConstant.CONSULT_TYPE_GRAPHIC:(int)mo[0];
                    int oDoctorId = (int)mo[1];
                    boolean exists = false;
                    for(TempMsgVo vo : msgList){
                        if(vo.getRequestMode() == oRequestMode && vo.getDoctorId()==oDoctorId){
                            exists = true;
                        }
                    }
                    if(!exists){
                        TempMsgVo newVo = new TempMsgVo();
                        newVo.setRequestMode(oRequestMode);
                        newVo.setDoctorId(oDoctorId);
                        long xx = (long)mo[1];
                        newVo.setNotReadCount((int)xx);
                        msgList.add(newVo);
                    }
                }
                return msgList;
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 分页查询咨询对应的消息列表
     * @param requestMpi
     * @param doctorId
     * @param startIndex
     * @param pageSize
     * @param requestMode
     * @return
     */
    public List<BusConsultMsg> findDoctorConsultMessageList(
            final String requestMpi,
            final Integer doctorId,
            final Date firstRequestTime,
            final int startIndex,
            final int pageSize,
            final Integer requestMode) {
        AbstractHibernateStatelessResultAction<List<BusConsultMsg>> action = new AbstractHibernateStatelessResultAction<List<BusConsultMsg>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = "FROM BusConsultMsg WHERE deleted=0 AND mpiId=:requestMpi AND doctorId = :doctorId AND createTime<:firstRequestTime AND requestMode=:requestMode ORDER BY id DESC";
                Query query = ss.createQuery(hql);
                query.setParameter("requestMpi", requestMpi);
                query.setParameter("doctorId", doctorId);
                query.setParameter("firstRequestTime", firstRequestTime);
                query.setParameter("requestMode", requestMode);
                query.setFirstResult(startIndex);
                query.setMaxResults(pageSize);
                setResult(query.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 修改该咨询对应的除语音消息之外的所有消息为已读状态
     * @param requestMpi
     * @param doctorId
     */
    @DAOMethod(sql = "UPDATE BusConsultMsg SET hasRead=1 WHERE mpiId=:requestMpi AND doctorId=:doctorId AND requestMode=:requestMode AND msgType!=3" )
    public abstract void updateDoctorMessageToHasReadExceptAudio(
            @DAOParam("requestMpi") String requestMpi,
            @DAOParam("doctorId") Integer doctorId,
            @DAOParam("requestMode") Integer requestMode);

    /**
     * 根据咨询id查询聊天消息
     * @param consultId
     * @return
     */
    @DAOMethod(sql = "FROM BusConsultMsg WHERE consultId=:consultId AND msgType in (1,2,3) AND senderRole='doctor'" )
    public abstract List<BusConsultMsg> findChatMessageListByConsultId(
            @DAOParam("consultId") Integer consultId);


    /**
     * 根据咨询id获取最新的一条消息
     * @param consultId
     * @return
     */
    public BusConsultMsg getLatestMsgByConsultId(
            final Integer consultId) {
        AbstractHibernateStatelessResultAction<BusConsultMsg> action = new AbstractHibernateStatelessResultAction<BusConsultMsg>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = "FROM BusConsultMsg WHERE consultId=:consultId  ORDER BY id DESC";
                Query query = ss.createQuery(hql);
                query.setParameter("consultId", consultId);
                query.setFirstResult(0);
                query.setMaxResults(1);
                setResult((BusConsultMsg)query.uniqueResult());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 查询用户未读消息总数
     * @param requestMpi
     * @return
     */
    public Long findUserNotReadMsgCountWithMpiId(final String requestMpi){
        AbstractHibernateStatelessResultAction<Long> action = new AbstractHibernateStatelessResultAction<Long>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String queryConsultMsgList = "SELECT  (COUNT(*)-SUM(hasRead))   " +
                        "FROM BusConsultMsg WHERE deleted <2  AND mpiId = :requestMpi ";
                Query q1 = ss.createQuery(queryConsultMsgList);
                q1.setParameter("requestMpi", requestMpi);
                Long count  = (Long)q1.uniqueResult();
                setResult(count);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    @DAOMethod(sql = "UPDATE BusConsultMsg SET hasRead=1 WHERE id = CONVERT(:cidOrUUid, SIGNED) OR msgExtra = :cidOrUUid" )
    public abstract void updateConsultMessageToHasRead(@DAOParam("cidOrUUid") String cidOrUUid);

    public List<BusConsultMsg> findLatestChatMessage(
            final Integer requestMode,
            final String sessionId, final Integer startIndex, final  Integer pageSize){
        AbstractHibernateStatelessResultAction<List<BusConsultMsg>> action = new AbstractHibernateStatelessResultAction<List<BusConsultMsg>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String sql = "FROM BusConsultMsg WHERE consultId in (SELECT consultId from Consult WHERE sessionId = :sessionId) AND msgType <> 99 and msgType<>8  AND requestMode = :requestMode ORDER BY id desc ";
                Query query = ss.createQuery(sql);
                query.setParameter("requestMode", requestMode);
                query.setParameter("sessionId", sessionId);
                query.setFirstResult(startIndex);
                query.setMaxResults(pageSize);
                setResult(query.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    @DAOMethod(sql = "select count(*) from BusConsultMsg where consultId=:consultId and msgType in(1,2,3,4,99) ")
    public abstract Long getCountByConsultId(@DAOParam("consultId")Integer consultId);

    @DAOMethod(sql = " from BusConsultMsg where consultId=:consultId and msgType in(1,2,3,4,99) order by createTime asc ")
    public abstract List<BusConsultMsg> findByConsultId(@DAOParam("consultId")Integer consultId,@DAOParam(pageStart = true)int start,@DAOParam(pageLimit = true)int limit);



}
