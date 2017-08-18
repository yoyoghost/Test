package eh.msg.dao;

import ctd.account.UserRoleToken;
import ctd.account.user.User;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.persistence.support.impl.user.UserDAO;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.dao.DoctorDAO;
import eh.entity.base.Doctor;
import eh.entity.msg.Article;
import eh.entity.msg.SessionDetail;
import eh.entity.msg.SessionMessage;
import eh.entity.msg.SmsInfo;
import eh.push.SmsPushService;
import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.sql.Timestamp;
import java.util.*;

/**
 * 会话明细
 *
 * @author ZX
 */
public abstract class SessionDetailDAO extends
        HibernateSupportDelegateDAO<SessionDetail> {

    public static final Logger log = Logger.getLogger(SessionDetailDAO.class);

    public SessionDetailDAO() {
        super();
        this.setEntityName(SessionDetail.class.getName());
        this.setKeyField("sessionDetailId");
    }

    /**
     * 未读会话明细查询服务
     *
     * @param sessionId
     * @param memberType
     * @param memberId
     * @return
     * @throws DAOException
     * @date 2016-7-12 luf：原生端及微信端没有在用，将其注释
     */
//	@RpcService
//    public List<SessionDetail> findSessionDetail(final Integer sessionId,
//                                                 final Integer memberType, final Integer memberId)
//            throws DAOException {
//        HibernateStatelessResultAction<List<SessionDetail>> action = new AbstractHibernateStatelessResultAction<List<SessionDetail>>() {
//            @SuppressWarnings("unchecked")
//            @Override
//            public void execute(StatelessSession ss) throws Exception {
//                // 获取未读数
//                Integer unRead = DAOFactory.getDAO(SessionMemberDAO.class)
//                        .getUnRead(sessionId, memberType, memberId);
//                // 获取会话详细列表
//                List<SessionDetail> sessionDetails = new ArrayList<SessionDetail>();
//                if (unRead >= 1) {
//                    String hql = new String(
//                            "from SessionDetail where sessionId=:sessionId order by postTime desc");
//                    Query q = ss.createQuery(hql);
//                    q.setParameter("sessionId", sessionId);
//                    q.setFirstResult(0);
//                    q.setMaxResults(unRead);
//                    sessionDetails = q.list();
//                    // 更新未读数为0
//                    DAOFactory.getDAO(SessionMemberDAO.class).updateUnRead(
//                            sessionId, memberType, memberId);
//                }
//                setResult(sessionDetails);
//            }
//        };
//        HibernateSessionTemplate.instance().execute(action);
//        return action.getResult();
//    }

    /**
     * 会话明细查询服务
     *
     * @param sessionId
     * @param memberType
     * @param memberId
     * @param page
     * @return
     * @throws DAOException
     * @author ZX
     * @date 2015-4-12 下午2:07:05
     * @date 2016-7-13 luf:直接将会话明细改成app端调用值
     */
    @RpcService
    public List<SessionDetail> querySessionDetail(Integer sessionId,
                                                  Integer memberType, Integer memberId, Integer page) {
        return queryNativeSessionDetail(sessionId, memberType, memberId,
                page);
    }

    /**
     * 会话明细查询服务(分页+每页记录数)
     *
     * @param sessionId
     * @param memberType
     * @param memberId
     * @param page
     * @param limit
     * @return 会话明细列表
     * @throws DAOException
     * @author yaozh
     * @date 2016-7-13 luf:修改成只查询pc端系统消息明细
     */
    @RpcService
    public List<SessionDetail> querySessionDetailPageAndLimit(
            Integer sessionId, Integer memberType,
            Integer memberId, Integer page, Integer limit) {
        return this.queryPcSessionDetail(sessionId, memberType, memberId, page, limit);
    }

    /**
     * 根据用户id新增系统消息服务(所有端)
     *
     * @param publisherId 消息订阅号（1系统提醒……）
     * @param message     消息内容（消息体），格式相见下面的说明。
     * @param memberType  接收者类型（1医生 2患者），如果是统一会话号类消息则可为0
     * @param managerUnit
     * @param userId      用户id
     * @return
     * @author ZX
     * @date 2015-4-10 下午2:06:12
     */
    public int addSysMessageByUserId(String message, int memberType,
                                     String managerUnit, String userId) {
        return this.addSysMessageByUserIdWithReceiveType(message, memberType, managerUnit, userId, 0);
    }

    /**
     * 根据用户id新增系统消息服务(原生app端)
     *
     * @param message
     * @param memberType
     * @param managerUnit
     * @param userId
     * @return
     */
    public int addNativeSysMessageByUserId(String message, int memberType,
                                           String managerUnit, String userId) {
        return this.addSysMessageByUserIdWithReceiveType(message, memberType, managerUnit, userId, 1);
    }

    /**
     * 根据用户id新增系统消息服务(pc端)
     *
     * @param message
     * @param memberType
     * @param managerUnit
     * @param userId
     * @return
     */
    public int addPcSysMessageByUserId(String message, int memberType,
                                       String managerUnit, String userId) {
        return this.addSysMessageByUserIdWithReceiveType(message, memberType, managerUnit, userId, 2);
    }

    /**
     * 根据用户id新增系统消息服务
     *
     * @param publisherId 消息订阅号（1系统提醒……）
     * @param message     消息内容（消息体），格式相见下面的说明。
     * @param memberType  接收者类型（1医生 2患者），如果是统一会话号类消息则可为0
     * @param managerUnit
     * @param userId      用户id
     * @param receiveType 接收类型--0所有端 1原生app端 2pc端
     * @return
     * @author ZX
     * @date 2015-4-10 下午2:06:12
     */
    private int addSysMessageByUserIdWithReceiveType(String message, int memberType,
                                                    String managerUnit, String userId, int receiveType) {

        // 获取roleId,
        String roleId = "";
        if (memberType == 1) {
            roleId = "doctor";
        } else if (memberType == 2) {
            roleId = "patient";
        }

        // 获取角色列表
        UserDAO userDAO = DAOFactory.getDAO(UserDAO.class);
        User user = userDAO.get(userId);
        if (user == null) {
            log.info("SessionDetailDAO(addSysMessageByUserId)  : User["
                    + userId + "] not exist");
            return -1;
        }
        List<UserRoleToken> list = user.findUserRoleTokenByRoleId(roleId);
        if (list.size() <= 0) {
            log.info("SessionDetailDAO(addSysMessageByUserId)  : User["
                    + userId + "] not exist");
            return -1;
        }

        // 获取urtId
        UserRoleToken toke = list.get(0);
        int publisherId = 1;
        return addSysMessage(publisherId, message, memberType, toke.getId(), receiveType);
    }

    /**
     * 根据用户id新增系统消息服务
     *
     * @param publisherId 消息订阅号（1系统提醒2今日就诊提醒3明日就诊提醒……）
     * @param message     消息内容（消息体），格式相见下面的说明。
     * @param memberType  接收者类型（1医生 2患者），如果是统一会话号类消息则可为0
     * @param managerUnit
     * @param userId      用户id
     * @return
     * @author ZX
     * @date 2015-4-10 下午2:06:12
     * @date luf：此方法仅供今日/明日就诊系统消息
     */
    public int addSysMessageWithPublisher(int publisherId, String message, int memberType,
                                          String managerUnit, String userId) {

        // 获取roleId,
        String roleId = "";
        if (memberType == 1) {
            roleId = "doctor";
        } else if (memberType == 2) {
            roleId = "patient";
        }

        // 获取角色列表
        UserDAO userDAO = DAOFactory.getDAO(UserDAO.class);
        User user = userDAO.get(userId);
        if (user == null) {
            log.info("SessionDetailDAO(addSysMessageByUserId)  : User["
                    + userId + "] not exist");
            return -1;
        }
        List<UserRoleToken> list = user.findUserRoleTokenByRoleId(roleId);
        if (list.size() <= 0) {
            log.info("SessionDetailDAO(addSysMessageByUserId)  : User["
                    + userId + "] not exist");
            return -1;
        }

        // 获取urtId
        UserRoleToken toke = list.get(0);
        return addSysMessage(publisherId, message, memberType, toke.getId(),0);

    }

    /**
     * 供addSysMessageByUserId/addSysMessageWithPublisher调用
     *
     * @param publisherId 消息订阅号（1系统提醒……）
     * @param message     消息内容（消息体），格式相见下面的说明。
     * @param memberType  接收者类型（1医生 2患者），如果是统一会话号类消息则可为0
     * @param urtid       接收者编号（医生或患者的UrtID），如果是统一会话号类消息则可为0
     *                    <p>
     *                    2015-04-12 zx 将publisherId统一设置为1，为系统消息提醒
     * @param receiveType 接收类型--0所有端 1原生app端 2pc端
     * @author ZX
     * @date 2015-4-9 下午5:34:37
     * @date 2016-7-13 luf:添加接收端标志
     */
    @RpcService
    public int addSysMessage(final int publisherId, final String message, final int memberType,
                             final int urtid, final int receiveType) {

        log.info("addSysMessage.publisherId="+publisherId+",message="+message+",memberType="+memberType+"," +
                "urtid="+urtid+",receiveType="+receiveType);

        // 获取会话序号
        PublisherDAO publisherDAO = DAOFactory.getDAO(PublisherDAO.class);
        final int sessionId = publisherDAO.createSysTalk(publisherId,
                memberType, urtid);

        log.info("addSysMessage.publisherId="+publisherId+",memberType="+memberType+"," +
                "urtid="+urtid+",sessionId="+sessionId);

        AbstractHibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                // 新加一条会话明细
                SessionDetail detail = new SessionDetail();
                detail.setSessionId(sessionId);
                detail.setTalkerType(0);
                detail.setTalkerId(publisherId);
                detail.setPostTime(new Date());
                detail.setMessage(message);
                detail.setTalkerPhoto(0);
                detail.setTalkerName("");
                detail.setReceiveType(receiveType);

                save(detail);

                // 根据会话id将 该会话中的成员未读数量+1，会话状态置为0
                SessionMemberDAO sessionMemberDAO = DAOFactory
                        .getDAO(SessionMemberDAO.class);
                sessionMemberDAO.updateUnReadAndSessionStatus(sessionId, receiveType);

                // 根据 sessionId 更新最后发言者相关信息,时间为当前时间
                SessionDAO sessionDAO = DAOFactory.getDAO(SessionDAO.class);
                sessionDAO.updateSessionBysessionId("", message, sessionId);

                setResult(sessionId);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);

        return action.getResult();
    }

    /**
     * 新增欢迎信息
     *
     * @param memberType 接收者类型（1医生 2患者），如果是统一会话号类消息则可为0
     * @param tel        手机号
     * @author ZX
     * @date 2015-4-24 下午5:29:46
     */
    @RpcService
    public void addWelcomeMessage(int memberType, String tel) {

        //发送消息通知
        Integer clientId = null;
        Integer organ=0;
        if(1==memberType){
            Doctor doc=DAOFactory.getDAO(DoctorDAO.class).getByMobile(tel);
            if(doc==null){
                log.info("不存在医生["+tel+"]");
                return;
            }
            organ=doc.getOrgan()==null?0:doc.getOrgan();
        }

        SmsInfo info = new SmsInfo();
        info.setBusId(0);
        info.setOrganId(organ);
        info.setBusType("WelcomeMsg");
        info.setSmsType("WelcomeMsg");

        Map<String,Object> smsMap = new HashMap<String, Object>();
        smsMap.put("userId",tel);
        smsMap.put("memberType",memberType);
        info.setExtendValue(JSONUtils.toString(smsMap));

        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
        smsPushService.pushMsgData2OnsExtendValue(info);
    }

    /**
     * 添加推荐奖励系统消息
     *
     * @param memberType 接收者类型（1医生 2患者），如果是统一会话号类消息则可为0
     * @param tel        推送系统消息手机号
     * @param msgType    消息类别
     * @param title      显示的标题
     * @param msg        显示的消息内容
     * @param url        链接
     * @param hasBtn     是否有按钮可以跳转到详情页(true:有;false:没有)
     * @author zhangx
     * @date 2015-11-27 上午11:48:38
     */
    @RpcService
    public void addRecommendRewardMessage(int memberType,
                                          String tel, String msgType, String title, String msg, String url,
                                          boolean hasBtn) {

        // 构建系统消息
        SessionMessage sessionMsg = new SessionMessage();
        sessionMsg.setToUserId(tel);
        sessionMsg.setCreateTime(new Timestamp(System.currentTimeMillis()));
        sessionMsg.setMsgType(msgType);

        List<Article> list = new ArrayList<Article>();

        Article art = new Article();
        art.setContent(msg);
        art.setTitle(title);
        art.setUrl(url);
        if (hasBtn) {
            art.setBussType(5);
        }
        list.add(art);

        sessionMsg.setArticles(list);

        // 新增系统消息
        SessionDetailDAO sessionDetailDAO = DAOFactory
                .getDAO(SessionDetailDAO.class);
        sessionDetailDAO.addSysMessageByUserId(JSONUtils.toString(sessionMsg),
                memberType, "eh", tel);
    }

    /**
     * @param busId      业务id (如果没有业务Id,传null)
     * @param busType    业务类型 (1转诊；2会诊；3咨询；4预约;5收入;6业务设置;7检查)
     * @param memberType 接收者类型（1医生 2患者）
     * @param tel        推送系统消息手机号
     * @param msgType    消息类别
     * @param title      显示的标题
     * @param msg        显示的消息内容
     * @param url        链接
     * @param hasBtn     是否有按钮可以跳转到详情页(true:有;false:没有)
     * @return void
     * @function 新增系统提醒消息
     * @author zhangjr
     * @date 2015-12-9
     * @desc 目前[推荐消息]使用
     */
    public void addMsgDetail(Integer busId, int busType, int memberType, String tel,
                             String msgType, String title, String msg, String url, boolean hasBtn) {
        // 构建系统消息
        SessionMessage sessionMsg = new SessionMessage();
        sessionMsg.setToUserId(tel);
        sessionMsg.setCreateTime(new Timestamp(System.currentTimeMillis()));
        sessionMsg.setMsgType(msgType);

        List<Article> list = new ArrayList<Article>();

        Article art = new Article();
        art.setContent(msg);
        art.setTitle(title);
        art.setUrl(url);
        if (hasBtn) {
            art.setBussType(busType);

            if (busId != null) {
                art.setBussId(busId);
            }

        }
        list.add(art);
        sessionMsg.setArticles(list);

        // 新增系统消息
        SessionDetailDAO sessionDetailDAO = DAOFactory
                .getDAO(SessionDetailDAO.class);
        sessionDetailDAO.addSysMessageByUserId(JSONUtils.toString(sessionMsg),
                memberType, "eh", tel);
    }

    /**
     * 会话明细查询服务(区分接收端)
     *
     * @param sessionId
     * @param memberType 0系统；1医生；2患者
     * @param memberId   urtid
     * @param page       第几页
     * @param limit      每页限制条数
     * @param flag       查询类型--0所有会话 1原生app端 2pc端
     * @return 会话明细列表
     * @author luf
     * @date 2016-7-13
     */
    public List<SessionDetail> querySessionDetailWithFlag(
            final Integer sessionId, final Integer memberType,
            final Integer memberId, final int page, final int limit, final int flag) {
        HibernateStatelessResultAction<List<SessionDetail>> action = new AbstractHibernateStatelessResultAction<List<SessionDetail>>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                // 获取会话详细列表
                List<SessionDetail> sessionDetails = new ArrayList<SessionDetail>();

                int start = limit * (page - 1);

                String hql = "from SessionDetail where sessionId=:sessionId and (receiveType=:flag or receiveType=0) order by postTime desc";
                Query q = statelessSession.createQuery(hql);
                q.setParameter("sessionId", sessionId);
                q.setParameter("flag", flag);
                q.setFirstResult(start);
                q.setMaxResults(limit);
                sessionDetails = q.list();

                // 更新未读数为0
                DAOFactory.getDAO(SessionMemberDAO.class).updateUnReadWithFlag(sessionId, memberType, memberId, flag);

                setResult(sessionDetails);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 查询原生app端会话明细
     *
     * @param sessionId
     * @param memberType 0系统；1医生；2患者
     * @param memberId   urtid
     * @param page
     * @return
     */
    @RpcService
    public List<SessionDetail> queryNativeSessionDetail(
            Integer sessionId, Integer memberType,
            Integer memberId, int page) {
        return querySessionDetailWithFlag(sessionId, memberType, memberId,
                page, 10, 1);
    }

    /**
     * 查询pc端会话明细
     *
     * @param sessionId
     * @param memberType 0系统；1医生；2患者
     * @param memberId   urtid
     * @param page
     * @param limit
     * @return
     */
    @RpcService
    public List<SessionDetail> queryPcSessionDetail(
            Integer sessionId, Integer memberType,
            Integer memberId, int page, int limit) {
        return querySessionDetailWithFlag(sessionId, memberType, memberId,
                page, limit, 2);
    }

    /**
     * 查询全部会话明细
     *
     * @param sessionId
     * @param memberType 0系统；1医生；2患者
     * @param memberId   urtid
     * @param page
     * @param limit
     * @return
     */
    @RpcService
    public List<SessionDetail> queryAllSessionDetail(
            Integer sessionId, Integer memberType,
            Integer memberId, int page, int limit) {
        return this.querySessionDetailWithFlag(sessionId, memberType, memberId, page, limit, 0);
    }
}
