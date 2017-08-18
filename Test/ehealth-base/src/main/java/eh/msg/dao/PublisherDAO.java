package eh.msg.dao;

import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.annotation.RpcService;
import eh.entity.msg.Publisher;
import eh.entity.msg.Session;
import eh.entity.msg.SessionMember;
import org.apache.commons.collections.CollectionUtils;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.Date;
import java.util.List;

/**
 * 消息订阅号
 *
 * @author ZX
 */
public abstract class PublisherDAO extends HibernateSupportDelegateDAO<Publisher> {

    public PublisherDAO() {
        super();
        this.setEntityName(Publisher.class.getName());
        this.setKeyField("publisherId");
    }

    /**
     * 查询订阅号
     *
     * @param publisherId
     * @return
     */
    @RpcService
    @DAOMethod
    public abstract Publisher getByPublisherId(Integer publisherId);

    /**
     * 创建消息订阅会话服务
     *
     * @param publisherId
     * @param memberType
     * @param memberId
     * @return
     * @throws DAOException
     */
    @RpcService
    public Integer createSysTalk(final Integer publisherId, final Integer memberType, final Integer memberId) throws DAOException {
        //查询订阅号
        Publisher publisher = getByPublisherId(publisherId);
        //判断是否存在该订阅号
        if (publisher == null) {
            //找不到消息订阅号
            return null;
        } else {
            final String publisherName = publisher.getPublisherName();
            final Integer photo = publisher.getPhoto();
            Integer sessionId = publisher.getSessionId();
            if (sessionId != null && sessionId != 0) {
                //统一会话
                return sessionId;
            } else {
                HibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
                    @SuppressWarnings("rawtypes")
                    @Override
                    public void execute(StatelessSession ss) throws Exception {
                        //查询会话是否存在
                        String hql = new String(
                                "from Session s,SessionMember m where s.sessionId=m.sessionId and s.sessionType=0 and s.creater=:publisherId and m.memberType=:memberType and m.memberId=:memberId");
                        Query q = ss.createQuery(hql);
                        q.setParameter("publisherId", publisherId);
                        q.setParameter("memberType", memberType);
                        q.setParameter("memberId", memberId);
                        List list = q.list();

                        SessionMember sessionMember = new SessionMember();
                        Session session = new Session();
                        if (CollectionUtils.isNotEmpty(list)) {
                            //更新会话成员的会话状态
                            Object[] objects = (Object[]) list.get(0);
                            session = (Session) objects[0];
                            sessionMember = (SessionMember) objects[1];
                            DAOFactory.getDAO(SessionMemberDAO.class).updateSessionStatusBySessionMemberId(0, sessionMember.getMemberId());
                            setResult(session.getSessionId());
                        } else {
                            //新增会话
                            session.setMemberNum(1);
                            session.setCreateDate(new Date());
                            session.setSessionType(0);
                            session.setCreater(publisherId);
                            DAOFactory.getDAO(SessionDAO.class).save(session);
                            Integer sessionId = session.getSessionId();
                            sessionMember.setSessionId(sessionId);
                            sessionMember.setMemberType(memberType);
                            sessionMember.setMemberId(memberId);
                            sessionMember.setSessionName(publisherName);
                            sessionMember.setSessionStatus(0);
                            sessionMember.setUnRead(0);
                            sessionMember.setSessionImage(photo);
                            //2016-7-11 luf:增加不同端的未读条数初始值赋值
                            sessionMember.setNativeUnRead(0);//原生app端未读条数
                            sessionMember.setPcUnRead(0);//pc端未读条数
                            sessionMember.setCommonUnRead(0);//公共未读条数
                            DAOFactory.getDAO(SessionMemberDAO.class).save(sessionMember);
                            setResult(sessionId);
                        }
                    }
                };
                HibernateSessionTemplate.instance().executeTrans(action);
                return action.getResult();
            }
        }
    }
}
