package eh.msg.dao;

import com.google.common.collect.Lists;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.annotation.RpcService;
import eh.entity.msg.Session;
import eh.entity.msg.SessionAndMember;
import eh.entity.msg.SessionMember;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 会话
 *
 * @author ZX
 */
public abstract class SessionDAO extends HibernateSupportDelegateDAO<Session> {

    public SessionDAO() {
        super();
        this.setEntityName(Session.class.getName());
        this.setKeyField("sessionId");
    }

    /**
     * 根据 sessionId 更新最后发言者相关信息
     *
     * @param lastTalker
     * @param lastTalkTime
     * @param lastTalkMess
     * @param sessionId
     * @author ZX
     * @date 2015-4-9 下午4:53:48
     */
    @DAOMethod(sql = "update Session set lastTalker=:lastTalker, lastTalkTime=:lastTalkTime,lastTalkMess=:lastTalkMess where sessionId=:sessionId ")
    public abstract void updateSession(
            @DAOParam("lastTalker") String lastTalker,
            @DAOParam("lastTalkTime") Date lastTalkTime,
            @DAOParam("lastTalkMess") String lastTalkMess,
            @DAOParam("sessionId") Integer sessionId);

    /**
     * 根据 sessionId 更新最后发言者相关信息,时间为当前时间
     *
     * @param lastTalker
     * @param lastTalkMess
     * @param sessionId
     * @author ZX
     * @date 2015-4-9 下午5:02:27
     * @date 2016-3-3 luf 修改异常code
     */
    @RpcService
    public void updateSessionBysessionId(String lastTalker,
                                         String lastTalkMess, Integer sessionId) {
        if (!this.exist(sessionId)) {
            throw new DAOException(600, "session[" + sessionId
                    + "] is not exist");
        }

        Date now = new Date();
        updateSession(lastTalker, now, lastTalkMess, sessionId);
    }

    /**
     * 会话列表查询服务(查询所有的)
     *
     * @param memberType
     * @param memberId
     * @return
     * @throws DAOException
     * @author LF
     * @date 2016-7-13 luf:只提供给原生app端使用
     */
    @RpcService
    public List<SessionAndMember> querySessionList(Integer memberType,
                                                   Integer memberId) {
        return this.queryNativeSessions(memberType, memberId);
    }

    /**
     * 获取一个用户对应的一个聊天sessionId
     *
     * @param publisherId
     * @param memberType
     * @param memberId
     * @return
     * @throws DAOException
     */
    public List<Session> getSession(final Integer publisherId, final Integer memberType,
                                    final Integer memberId) throws DAOException {
        HibernateStatelessResultAction<List<Session>> action = new AbstractHibernateStatelessResultAction<List<Session>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql =
                        "select s from Session s,SessionMember m where s.sessionId=m.sessionId and s.sessionType=0 and s.creater=:publisherId and m.memberType=:memberType and m.memberId=:memberId";
                Query q = ss.createQuery(hql);
                q.setParameter("publisherId", publisherId);
                q.setParameter("memberType", memberType);
                q.setParameter("memberId", memberId);
                List<Session> list = q.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }


    /**
     * 会话列表查询服务(查询pc端的会话列表)
     *
     * @param memberType 0系统；1医生；2患者
     * @param memberId   urtId
     * @return
     * @throws DAOException
     * @author zx
     */
    @RpcService
    public List<SessionAndMember> queryPcSessionList(final Integer memberType,
                                                     final Integer memberId) {
        return this.queryPcSessions(memberType, memberId);
    }

    /**
     * 会话列表查询服务
     *
     * @param memberType 0系统；1医生；2患者
     * @param memberId   urtId
     * @param flag       查询类型--0所有会话 1原生app端 2pc端
     * @return List<SessionAndMember>
     * @date 2016-7-11 luf:系统消息区分接收端
     */
    public List<SessionAndMember> querySessions(final Integer memberType, final Integer memberId, final int flag) {
        HibernateStatelessResultAction<List<SessionAndMember>> action = new AbstractHibernateStatelessResultAction<List<SessionAndMember>>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                StringBuffer hql = new StringBuffer("select new eh.entity.msg.SessionAndMember(s,m) from Session s,SessionMember m where ");
                if (flag == 2) {
                    hql.append("s.creater in (:creater) and ");
                }
                hql.append("s.sessionId=m.sessionId and m.memberType=:memberType and m.memberId=:memberId and m.sessionStatus=0 order by s.lastTalkTime desc");
                Query q = statelessSession.createQuery(hql.toString());
                q.setParameter("memberType", memberType);
                q.setParameter("memberId", memberId);
                if (flag == 2) {
                    ArrayList<Integer> publishList = new ArrayList<Integer>();
                    publishList.add(1);//将订阅号[系统消息]添加到PC端显示列表中

                    q.setParameterList("creater", publishList);
                }
                List<SessionAndMember> list = q.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<SessionAndMember> list = action.getResult();
        if (null == list || list.isEmpty()) {
            return new ArrayList<SessionAndMember>();
        }
        if(flag == 2){
            List<SessionAndMember> list1 = setPcSysLastTalkTime(list);
            return convertUnReadAllSessions(list1, flag);
        }
        return convertUnReadAllSessions(list, flag);
    }

    /**
     * 设置pc端系统消息最后时间
     * @param list
     */
    public List<SessionAndMember> setPcSysLastTalkTime(List<SessionAndMember> list){

        final List<Integer> sessionIds = Lists.newArrayList();
        for(SessionAndMember sam : list){
            Session  s= sam.getSession();
            sessionIds.add(s.getSessionId());
        }
        HibernateStatelessResultAction<List<Date>> action = new AbstractHibernateStatelessResultAction<List<Date>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = "select postTime from SessionDetail where sessionId in(:sessionIds) and receiveType<>1 order by postTime desc";
                Query q= ss.createQuery(hql);
                q.setParameterList("sessionIds", sessionIds);
                q.setMaxResults(1);
                List<Date> dateList = q.list();
                setResult(dateList);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        //获取除在线续方外的最后一次对话时间
        List<Date> lastTimes = action.getResult();
        List<SessionAndMember> newList = Lists.newArrayList();
        for(SessionAndMember sam1 : list){
            sam1.getSession().setLastTalkTime(lastTimes.get(0));
            newList.add(sam1);
        }

        return newList;
    }



    /**
     * 计算不同需求下未读数
     *
     * @param list 会话列表
     * @param flag 查询类型--0所有会话 1原生app端 2pc端
     * @return List<SessionAndMember>
     */
    public List<SessionAndMember> convertUnReadAllSessions(List<SessionAndMember> list, int flag) {
        List<SessionAndMember> results = new ArrayList<SessionAndMember>();
        for (SessionAndMember sam : list) {
            if (null != sam.getSessionMember()) {
                SessionMember sm = sam.getSessionMember();
                if (null != sm) {
                    Integer nativeUnRead = sm.getNativeUnRead();
                    Integer pcUnRead = sm.getPcUnRead();
                    Integer common = sm.getCommonUnRead();
                    if (null == nativeUnRead || 0 > nativeUnRead) {
                        nativeUnRead = 0;
                    }
                    if (null == pcUnRead || 0 > pcUnRead) {
                        pcUnRead = 0;
                    }
                    if (null == common || 0 > common) {
                        common = 0;
                    }
                    Integer unRead = 0;
                    switch (flag) {
                        case 0:
                            unRead = sm.getUnRead();
                            break;
                        case 1:
                            unRead = nativeUnRead + common;
                            break;
                        case 2:
                            unRead = pcUnRead + common;
                            break;
                        default:
                            break;
                    }
                    sm.setUnRead(unRead);
                    sam.setSessionMember(sm);
                    results.add(sam);
                }
            }
        }
        return results;
    }

    /**
     * pc端会话列表服务
     *
     * @param memberType
     * @param memberId
     * @return List<SessionAndMember>
     */
    @RpcService
    public List<SessionAndMember> queryPcSessions(Integer memberType, Integer memberId) {
        return this.querySessions(memberType, memberId, 2);
    }

    /**
     * 原生app端会话列表服务
     *
     * @param memberType
     * @param memberId
     * @return List<SessionAndMember>
     */
    @RpcService
    public List<SessionAndMember> queryNativeSessions(Integer memberType, Integer memberId) {
        return this.querySessions(memberType, memberId, 1);
    }
}
