package eh.msg.dao;

import ctd.account.UserRoleToken;
import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.annotation.RpcService;
import eh.entity.msg.Session;
import eh.entity.msg.SessionMember;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.List;

/**
 * 会话成员
 *
 * @author ZX
 */
public abstract class SessionMemberDAO extends HibernateSupportDelegateDAO<SessionMember> {

    public SessionMemberDAO() {
        super();
        this.setEntityName(SessionMember.class.getName());
        this.setKeyField("sessionMemberId");
    }

    /**
     * 未读会话条数服务（供后台调用）
     * @param sessionID
     * @param memberType
     * @param memberId
     * @return
     * @throws DAOException
     */
//	public int getUnRead(final Integer sessionId,final Integer memberType,final Integer memberId) throws DAOException{
//		HibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
//			@Override
//			public void execute(StatelessSession ss) throws Exception{
//				Integer count = null;
//				String hql = new String(
//						"Select unRead from SessionMember where sessionId=:sessionId and memberType=:memberType and memberId=:memberId");
//				Query q = ss.createQuery(hql);
//				q.setParameter("sessionId", sessionId);
//				q.setParameter("memberType", memberType);
//				q.setParameter("memberId", memberId);
//				//取不到数据
//				if(q.uniqueResult()==null) {
//					count=0;
//				}else{
//					count = ((Number) q.uniqueResult()).intValue();
//				}
//				setResult(count);
//			}
//		};
//		HibernateSessionTemplate.instance().execute(action);
//		return ((Number)action.getResult()).intValue();
//	}

    /**
     * 获取一个用户未读取的消息条数服务
     *
     * @param memberType
     * @param urtid
     * @return
     * @author LF
     * @date 2016-7-12 luf：目前只有pc端使用此方法
     */
    @RpcService
    public int getUnRead(final int memberType, final int urtid) {
        return this.getUnReadWithFlag(memberType, urtid, 2);
    }

    /**
     * 更新未读数的值为0(全部端)
     *
     * @param sessionId
     * @param memberType
     * @param memberId
     * @date 2016-7-13 此方法仅供今日/明日就诊系统消息使用
     */
    @RpcService
    public void updateUnRead(Integer sessionId, Integer memberType, Integer memberId) {
        updateUnReadWithFlag(sessionId, memberType, memberId, 0);
    }

    /**
     * 更新会话成员的会话状态
     *
     * @param sessionStatus
     * @param sessionMemberId
     */
    @RpcService
    @DAOMethod
    public abstract void updateSessionStatusBySessionMemberId(Integer sessionStatus, Integer sessionMemberId);

    /**
     * 根据会话ID获取会话成员列表
     *
     * @param sessionId
     * @return
     * @author ZX
     * @date 2015-4-9 下午4:39:27
     */
    @RpcService
    @DAOMethod
    public abstract List<SessionMember> findBySessionId(int sessionId);

    /**
     * 根据会话id将 该会话中的成员未读数量+1，会话状态置为0
     *
     * @param sessionId
     * @param receiveType 查询类型--0所有端 1原生app端 2pc端
     * @author ZX
     * @date 2015-4-9 下午4:43:04
     * @date 216-7-13 luf:增加receiveType入参，区分接收端
     */
    @RpcService
    public void updateUnReadAndSessionStatus(final int sessionId, final int receiveType) {
        final AbstractHibernateStatelessResultAction<SessionMember> action = new AbstractHibernateStatelessResultAction<SessionMember>() {
            public void execute(StatelessSession ss) throws Exception {
                List<SessionMember> list = findBySessionId(sessionId);

                for (int i = 0; i < list.size(); i++) {
                    SessionMember member = list.get(i);
                    member.setSessionStatus(0);
                    member.setUnRead(member.getUnRead() + 1);
                    switch (receiveType) {
                        case 0:
                            member.setCommonUnRead(member.getCommonUnRead() + 1);
                            break;
                        case 1:
                            member.setNativeUnRead(member.getNativeUnRead() + 1);
                            break;
                        case 2:
                            member.setPcUnRead(member.getPcUnRead() + 1);
                            break;
                    }
                    update(member);
                }
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
    }

    /**
     * 根据系统订阅号，用户类型,将未读更新成已读
     *
     * @param publisherId
     * @param memberType
     * @date 2016-7-13 luf:此方法仅供今日/明日就诊
     */
    public void updateUnReadByPublisherIdAndMemberType(Integer publisherId, Integer memberType) {
        SessionDAO sessionDAO = DAOFactory.getDAO(SessionDAO.class);
        SessionMemberDAO memberDAO = DAOFactory.getDAO(SessionMemberDAO.class);

        Integer uid = UserRoleToken.getCurrent().getId();
        if (uid != null) {
            List<Session> list = sessionDAO.getSession(publisherId, memberType, uid);
            if (list != null && list.size() > 0) {
                Session session = list.get(0);
                Integer sessionId = session.getSessionId();
                memberDAO.updateUnRead(sessionId, memberType, uid);
            }
        }
    }

    /**
     * 获取一个用户未读取的消息条数服务(针对多种情况)
     *
     * @param memberType 0系统；1医生；2患者
     * @param urtid      memberId
     * @param flag       查询类型--0所有会话 1原生app端 2pc端
     * @return
     * @date 2016-7-13
     * @author luf
     */
    public int getUnReadWithFlag(final int memberType, final int urtid, final int flag) {
        final HibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                Integer count = null;
                String unReadNum = "unRead";
                StringBuffer hql = new StringBuffer(
                        "SELECT SUM(");
                if (flag == 1) {
                    unReadNum = "nativeUnRead+commonUnRead";
                } else if (flag == 2) {
                    unReadNum = "pcUnRead+commonUnRead";
                }
                hql.append(unReadNum);
                hql.append(") FROM SessionMember WHERE memberType=:memberType AND memberId=:memberId AND sessionStatus=0");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("memberType", memberType);
                q.setParameter("memberId", urtid);
                //取不到数据
                if (q.uniqueResult() == null) {
                    count = 0;
                } else {
                    count = ((Number) q.uniqueResult()).intValue();
                }
                setResult(count);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return ((Number) action.getResult()).intValue();
    }

    /**
     * 更新不同接收端的未读数为0
     *
     * @param sessionId
     * @param memberType 0系统；1医生；2患者
     * @param memberId   urtid
     * @param flag       查询类型--0所有会话 1原生app端 2pc端
     * @date 2016-7-13 luf
     */
    @RpcService
    public Integer updateUnReadWithFlag(final Integer sessionId, final Integer memberType, final Integer memberId, final int flag) {
        HibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                String unRead = "unRead=0,nativeUnRead=0,commonUnRead=0,pcUnRead=0";
                if (flag == 1) {
                    unRead = "unRead=pcUnRead,nativeUnRead=0,commonUnRead=0";
                } else if (flag == 2) {
                    unRead = "unRead=nativeUnRead,pcUnRead=0,commonUnRead=0";
                }
                StringBuffer hql = new StringBuffer("update SessionMember set ");
                hql.append(unRead);
                hql.append(" where sessionId=:sessionId and memberType=:memberType and memberId=:memberId");
                Query q = statelessSession.createQuery(hql.toString());
                q.setParameter("sessionId", sessionId);
                q.setParameter("memberType", memberType);
                q.setParameter("memberId", memberId);
                int num = q.executeUpdate();
                setResult(num);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

}
