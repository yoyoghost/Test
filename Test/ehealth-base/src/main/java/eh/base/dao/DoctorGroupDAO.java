package eh.base.dao;

import ctd.account.UserRoleToken;
import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import ctd.util.context.Context;
import ctd.util.context.ContextUtils;
import eh.bus.dao.ConsultSetDAO;
import eh.entity.base.Doctor;
import eh.entity.base.DoctorGroup;
import eh.entity.base.DoctorGroupAndDoctor;
import eh.entity.base.Employment;
import eh.entity.bus.ConsultSet;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class DoctorGroupDAO extends
        HibernateSupportDelegateDAO<DoctorGroup> {

    public static final Logger log = Logger.getLogger(DoctorGroupDAO.class);

    public DoctorGroupDAO() {
        super();
        this.setEntityName(DoctorGroup.class.getName());
        this.setKeyField("doctorGroupId");
    }

    /**
     * 根据团队成员查询所有所属团队
     *
     * @param memberId
     * @return
     */
    @RpcService
    @DAOMethod
    public abstract List<DoctorGroup> findByMemberId(Integer memberId);

    @DAOMethod(sql = "select g from DoctorGroup g,Doctor d where d.doctorId = g.doctorId AND memberId=:memberId AND d.status <> 9 ")
    public abstract List<DoctorGroup> findByMemberIdStartAndLimit(
            @DAOParam("memberId") Integer memberId,
            @DAOParam(pageStart = true) int start,
            @DAOParam(pageLimit = true) int limit);

    /**
     * 根据团队医生id，成员医生id查询记录
     *
     * @param doctorId
     * @param memberId
     * @return
     * @author ZX
     * @date 2015-6-16 下午3:29:41
     */
    @RpcService
    @DAOMethod
    public abstract DoctorGroup getByDoctorIdAndMemberId(Integer doctorId,
                                                         Integer memberId);

    /**
     * 根据团队医生， 查团队成员列表
     *
     * @param doctorId
     * @return
     */
    @RpcService
    @DAOMethod(limit = 10000)
    public abstract List<DoctorGroup> findByDoctorId(Integer doctorId);

    @DAOMethod(sql = "from DoctorGroup where doctorId=:doctorId order by leader desc, doctorGroupId asc")
    public abstract List<DoctorGroup> findByDoctorIdStartAndLimit(
            @DAOParam("doctorId") Integer doctorId,
            @DAOParam(pageStart = true) int start,
            @DAOParam(pageLimit = true) int limit);


    /**
     * 获取团队成员
     *
     * 排序:队长显示在第一个位置，其他成员根据职称由高到低排序，同职称的情况下，根据加入团队时间由远及近排序
     *
     * 供DoctorGroupService.getTeamMembersForHealth使用
     * @param doctorId
     * @param start
     * @param limit
     * @author zsq
     */
    public List<Object[]> findMembersByDoctorIdForHealth(final Integer doctorId, final Integer start,final Integer limit) {
        HibernateStatelessResultAction<List<Object[]>> action = new AbstractHibernateStatelessResultAction<List<Object[]>>() {
            public void execute(StatelessSession ss) throws Exception {
                Object[] obs=null;

                if(start==null || start==0){
                    //获取团队长
                    String hql="select g,d from DoctorGroup g,Doctor d where g.doctorId=:doctorId and g.memberId=d.doctorId and g.leader=1";
                    Query q = ss.createQuery(hql.toString());
                    q.setParameter("doctorId", doctorId);
                    obs=(Object[])q.uniqueResult();
                }

                String hql2="select g,d from DoctorGroup g,Doctor d where g.doctorId=:doctorId and g.memberId=d.doctorId and (g.leader is null or g.leader<>1) order by d.proTitle,g.doctorGroupId ";
                Query q2 = ss.createQuery(hql2.toString());
                q2.setParameter("doctorId", doctorId);
                if (start!=null && limit !=null) {
                    int actStart=start;
                    int actLimit=limit;

                    if(obs!=null && start==0){
                        actLimit=actLimit-1;
                    }

                    if(obs==null && start!=0){
                        actStart=actStart-1;
                    }

                    q2.setMaxResults(actLimit);
                    q2.setFirstResult(actStart);
                }

                List<Object[]> returnList=q2.list();
                if(obs!=null){
                    returnList.add(0, obs);
                }

                setResult(returnList);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);

        return action.getResult();
    }

    /**
     * 根据团队医生id，获取团队成员信息
     * @param doctorId
     * @return
     */
    public List<Doctor> findAllMembersByDoctorId(final Integer doctorId) {
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            public void execute(StatelessSession ss) throws Exception {
                String hql="select d from DoctorGroup g,Doctor d where g.doctorId=:doctorId and g.memberId=d.doctorId ";

                Query q = ss.createQuery(hql.toString());
                q.setParameter("doctorId", doctorId);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);

        return action.getResult();
    }

    /**
     * 获取团队成员总数
     *
     * @param doctorId
     * @return
     * @author zhangx
     * @date 2015-11-2 下午6:00:06
     */
    @RpcService
    @DAOMethod(sql = "select count(*) from DoctorGroup where doctorId=:doctorId")
    public abstract Long getMemberNum(@DAOParam("doctorId") Integer doctorId);

    /**
     * 获取医生团队成员服务
     *
     * @param doctorGroupId
     * @return
     * @author zsq
     */
    @RpcService
    @DAOMethod
    public abstract List<DoctorGroup> findByDoctorGroupId(Integer doctorGroupId);

    /**
     * 新增团队成员
     *
     * @param doctorId
     * @param memberId
     * @author zsq
     */
    @RpcService
    public void saveDoctorGroup(final Integer doctorId, final Integer memberId) {
        HibernateStatelessResultAction<Doctor> action = new AbstractHibernateStatelessResultAction<Doctor>() {
            public void execute(StatelessSession ss) throws Exception {
                if (doctorId == null) {
//                    log.error("doctorId is required");
                    throw new DAOException(DAOException.VALUE_NEEDED,
                            "doctorId is required");
                }
                if (memberId == null) {
//                    log.error("memberId is required");
                    throw new DAOException(DAOException.VALUE_NEEDED,
                            "memberId is required");
                }
                DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                if (doctorDAO.getByDoctorIdAndTeams(doctorId) == null) {
//                    log.info("该团队不存在,无法添加团队成员");
                    throw new DAOException(609, "该团队不存在,无法添加团队成员");
                }

                // 判断指定团队中是否存在成员
                DoctorGroup doctorGroup = getByDoctorIdAndMemberId(doctorId,
                        memberId);
                if (doctorGroup != null) {
                    log.info("该团队中已经存在该成员");
                    throw new DAOException(609, "该团队中已经存在该成员");
                }

                // 添加第一个团队成员，将咨询开关打开
                DoctorGroupDAO doctorGroupDAO = DAOFactory
                        .getDAO(DoctorGroupDAO.class);
                if (doctorGroupDAO.findByDoctorId(doctorId).size() == 0) {
                    ConsultSetDAO consultSetDAO = DAOFactory
                            .getDAO(ConsultSetDAO.class);
                    ConsultSet consultSet = new ConsultSet();
                    consultSet.setDoctorId(doctorId);
                    consultSet.setTransferStatus(1);
                    consultSet.setMeetClinicStatus(1);
                    consultSet.setOnLineStatus(1);
                    consultSetDAO.addOrupdateConsultSet(consultSet);
                }
                DoctorGroup dg = new DoctorGroup();
                dg.setDoctorId(doctorId);
                dg.setMemberId(memberId);
                doctorGroupDAO.save(dg);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
    }

    /**
     * 删除医生团队成员服务
     *
     * @param doctorGroupId
     * @param doctorId
     * @author zsq
     */
    @RpcService
    @DAOMethod
    public abstract void deleteByDoctorGroupIdAndDoctorId(int doctorGroupId,
                                                          int doctorId);

    /**
     * 删除团队成员
     *
     * @param doctorId
     * @param memberId
     * @author ZX
     * @date 2015-6-16 下午3:34:13
     */
    @RpcService
    public void delMember(Integer doctorId, Integer memberId) {
        // 判断指定团队中是否存在成员
        DoctorGroup doctorGroup = getByDoctorIdAndMemberId(doctorId, memberId);
        if (doctorGroup == null) {
            log.info("该团队中不存存在该成员");
            throw new DAOException(609, "该团队中不存在该成员");
        }

        // 获取主键
        int doctorGroupId = doctorGroup.getDoctorGroupId();
        this.remove(doctorGroupId);
        log.info("删除团队成员：" + JSONUtils.toString(doctorGroup));
    }

    /**
     * 新增团队医生基本信息服务
     *
     * @param doctor
     * @param employment
     * @return
     * @author hyj
     */
    @RpcService
    public Doctor addGroupDoctor(final Doctor doctor,
                                 final Employment employment) {
        HibernateStatelessResultAction<Doctor> action = new AbstractHibernateStatelessResultAction<Doctor>() {
            public void execute(StatelessSession ss) throws Exception {
                // 新增团队医生信息
                DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                log.info("doctor:" + JSONUtils.toString(doctor));
                Doctor target = doctorDAO.addGroupDoctor(doctor);
                // 新增团队医生职业信息
                EmploymentDAO employmentDAO = DAOFactory
                        .getDAO(EmploymentDAO.class);
                employment.setDoctorId(target.getDoctorId());
                log.info("employment:" + JSONUtils.toString(employment));
                employmentDAO.addEmployment(employment);
                setResult(target);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
        return action.getResult();
    }

    /**
     * 获取团队医生所有成员详细信息--给运营平台调用
     *
     * @param doctorId
     * @return
     * @author ZX
     * @date 2015-6-16 下午12:12:03
     */
    @RpcService
    public List<DoctorGroupAndDoctor> getDoctorInfoByDoctorId(Integer doctorId,
                                                              int start, int limit) {

        List<DoctorGroupAndDoctor> list = new ArrayList<DoctorGroupAndDoctor>();
        DoctorDAO doctorDao = DAOFactory.getDAO(DoctorDAO.class);

        // 获取团队医生列表
        List<DoctorGroup> groupList = findByDoctorIdStartAndLimit(doctorId,
                start, limit);
        for (DoctorGroup doctorGroup : groupList) {
            int memberId = doctorGroup.getMemberId();
            if (!doctorDao.exist(memberId)) {
                log.info("获取团队医生信息：不存在医生ID为[" + memberId + "]的医生");
            }
            Doctor memberDoc = doctorDao.getByDoctorId(memberId);

            DoctorGroupAndDoctor docGroupAndDoc = new DoctorGroupAndDoctor();
            docGroupAndDoc.setDoctorGroup(doctorGroup);
            docGroupAndDoc.setMemberDoctor(memberDoc);
            list.add(docGroupAndDoc);
        }

        return list;
    }

    /**
     * 获取团队医生所有成员详细信息--给前端调用默认一页10条记录
     *
     * @param doctorId
     * @param start
     * @return
     * @author hyj
     */
    @RpcService
    public List<DoctorGroupAndDoctor> getDoctorGroupAndDoctorByDoctorId(
            Integer doctorId, int start) {
        return this.getDoctorInfoByDoctorId(doctorId, start, 10);
    }

    /**
     * @param @param  doctorId
     * @param @param  start
     * @param @return
     * @return Map<String,Object>
     * @throws
     * @Class eh.base.dao.DoctorGroupDAO.java
     * @Title: getDoctorGroupAndDoctorAndTotalByDoctorId
     * @Description: TODO 获取团队医生所有成员详细信息 返回总数
     * @author AngryKitty
     * @Date 2016-2-18上午10:43:14
     */
    @RpcService
    public Map<String, Object> getDoctorGroupAndDoctorAndTotalByDoctorId(
            Integer doctorId, int start) {
        UserRoleToken ur = (UserRoleToken) ContextUtils.get(Context.USER_ROLE_TOKEN);
        Doctor d = (Doctor) ur.getProperty("doctor");
        Integer docId = 0;
        if (d != null && d.getDoctorId() != null) {
            docId = d.getDoctorId();
        }
        Map<String, Object> params = new HashMap<String, Object>();
        List<DoctorGroup> g = this.findByDoctorId(doctorId);
        int total = 0;
        if (g != null) {
            total = g.size();
        }
        List<DoctorGroupAndDoctor> groups = this.getDoctorInfoByDoctorId(
                doctorId, start, 10);
        Boolean isIn = false;
        if (g != null) {
            for (DoctorGroup dg : g) {
                Integer memberId = dg.getMemberId();
                if (docId.equals(memberId)) {
                    isIn = true;
                    break;
                }
            }
        }
        params.put("total", total);
        params.put("group", groups);
        params.put("isIn", isIn);
        return params;
    }

    /**
     * 获取医生所属团队列表信息服务
     *
     * @param memberId
     * @param start
     * @return
     * @author hyj
     */
    @RpcService
    public List<DoctorGroupAndDoctor> getDoctorGroupAndDoctorByMemberId(
            Integer memberId, int start) {
        List<DoctorGroupAndDoctor> list = new ArrayList<DoctorGroupAndDoctor>();
        DoctorDAO dao = DAOFactory.getDAO(DoctorDAO.class);
        // 获取医生团队列表
        List<DoctorGroup> doctorGrouplist = this.findByMemberIdStartAndLimit(
                memberId, start, 10);

        for (DoctorGroup d : doctorGrouplist) {
            DoctorGroupAndDoctor doctorGroupAndDoctor = new DoctorGroupAndDoctor();
            int doctorId = d.getDoctorId();
            if (!dao.exist(doctorId)) {
                log.info("获取医生团队信息：不存在医生ID为[" + doctorId + "]的医生");
            }
            Doctor doctor = dao.getByDoctorId(doctorId);
            Long memberNum = this.getMemberNum(doctorId);
            d.setMemberNum(memberNum);
            doctorGroupAndDoctor.setDoctorGroup(d);
            doctorGroupAndDoctor.setMemberDoctor(doctor);
            list.add(doctorGroupAndDoctor);
        }
        return list;
    }

    /**
     * 修改团队成员的团队组长标志
     *
     * @param doctorGroupId 医生团队内码
     * @param leader 团队组长标志
     * @author yaozh
     */
    @RpcService
    @DAOMethod(sql = "update DoctorGroup set leader= :leader where  doctorGroupId = :doctorGroupId")
    public abstract void updateLeaderByMember(
            @DAOParam("leader") boolean leader,
            @DAOParam("doctorGroupId") int doctorGroupId);

    @RpcService
    @DAOMethod(sql = "update DoctorGroup set leader= :leader where doctorGroupId= :doctorGroupId")
    public abstract void updateLeaderByDoctorGroupId(
            @DAOParam("leader") int leader,
            @DAOParam("doctorGroupId") int doctorGroupId);

    /**
     * 修改团队成员角色
     *
     * @param doctorGroupId
     * @param leader
     * @return
     * @author Qichengjian
     */
    @RpcService
    @DAOMethod(sql = "update DoctorGroup set leader= :leader where doctorGroupId = :doctorGroupId")
    public abstract void updateLeaderByGroupId(@DAOParam("leader") int leader,
                                               @DAOParam("doctorGroupId") int doctorGroupId);

    @DAOMethod
    public abstract DoctorGroup getByDoctorIdAndLeader(int doctorId, int leader);

    /**
     * 设置团队成员为团队组长
     *
     * @param doctorId  医生团队代码
     * @param memberId       团队队员编码
     * @author yaozh
     */
    @RpcService
    public void updateMemberToLeader(final Integer doctorId,
                                     final Integer memberId) {
        HibernateStatelessResultAction<Doctor> action = new AbstractHibernateStatelessResultAction<Doctor>() {
            public void execute(StatelessSession ss) throws Exception {
                // 判断指定团队中是否存在成员
                DoctorGroup doctorGroup = getByDoctorIdAndMemberId(doctorId,
                        memberId);
                if (doctorGroup == null) {
                    log.info("该团队中不存存在该成员");
                    throw new DAOException(609, "该团队中不存在该成员");
                }
                // 获取团队原先队长
                // DoctorGroup d=getByDoctorIdAndLeader(doctorId, true);
                DoctorGroup d = getByDoctorIdAndLeader(doctorId, 1);
                if (d != null) {
                    updateLeaderByMember(false, d.getDoctorGroupId());
                }
                // 获取主键
                int doctorGroupId = doctorGroup.getDoctorGroupId();
                updateLeaderByMember(true, doctorGroupId);
                log.info("设置团队[" + doctorGroupId + "]的成员[" + memberId
                        + "]为团队组长");
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);

    }

    /**
     * 设置团队医生成员角色类型
     *
     * @param doctorId 医生团队代码
     * @param memberId         团队队员编码
     * @param leader         团队角色 0成员 1负责人 2管理员
     * @author Qichengjian
     */
    @RpcService
    public void updateGroupRole(final Integer doctorId, final Integer memberId,
                                final Integer leader) {
        HibernateStatelessResultAction<Doctor> action = new AbstractHibernateStatelessResultAction<Doctor>() {
            public void execute(StatelessSession ss) throws Exception {
                // 判断指定团队中是否存在成员
                DoctorGroup doctorGroup = getByDoctorIdAndMemberId(doctorId,
                        memberId);
                if (doctorGroup == null) {
                    log.info("该团队中不存在该成员");
                    throw new DAOException(609, "该团队中不存在该成员");
                }
                // 负责人角色只能最多有一人,获取团队原负责人
                DoctorGroup d = getByDoctorIdAndLeader(doctorId, 1);
                if (d != null && leader == 1) {
                    updateLeaderByGroupId(0, d.getDoctorGroupId()); // 修改原负责人为普通成员
                }
                // 获取主键
                int doctorGroupId = doctorGroup.getDoctorGroupId();
                updateLeaderByDoctorGroupId(leader, doctorGroupId);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
    }

    /**
     * 根据医生内码获取所有团队医生内码列表
     *
     * @param doctorId 医生内码
     * @return List<String>
     * @author luf
     */
    @DAOMethod(sql = "select doctorId from DoctorGroup where memberId=:doctorId")
    public abstract List<Integer> findDoctorIdsByMemberId(
            @DAOParam("doctorId") Integer doctorId);

    /**
     * 根据医生内码获取所有团队成员医生内码列表
     *
     * @param doctorId 医生内码
     * @return List<String>
     * @author luf
     */
    @DAOMethod(sql = "select memberId from DoctorGroup where doctorId=:doctorId")
    public abstract List<Integer> findMemberIdsByDoctorId(
            @DAOParam("doctorId") Integer doctorId);

    /**
     * 团队医生详情页信息（添加团队医生信息）
     *
     * @param doctorId
     * @param start
     * @return
     */
    @RpcService
    public Map<String, Object> findMembersAndDoctor(int doctorId, int start) {
        Map<String, Object> map = this.getDoctorGroupAndDoctorAndTotalByDoctorId(doctorId, start);
        DoctorDAO dDao = DAOFactory.getDAO(DoctorDAO.class);
        Doctor d = dDao.get(doctorId);
        map.put("doctor", d);
        return map;
    }

    /**
     * 团队医生 新增团队成员
     *
     * @param doctorId 医生团队代码
     * @param memberId 团队队员编码
     * @param leader   团队角色 0成员 1负责人 2管理员
     * @date 2016-05-05 16:34:23
     * @author houxr
     * @desc modify新增团队医生时要求有:团队角色标志
     */
    @RpcService
    public void saveDoctorGroupByGroupRole(final Integer doctorId, final Integer memberId, final Integer leader) {
        HibernateStatelessResultAction<Doctor> action = new AbstractHibernateStatelessResultAction<Doctor>() {
            public void execute(StatelessSession ss) throws Exception {
                if (null == doctorId) {
//                    log.error("doctorId is required");
                    throw new DAOException(DAOException.VALUE_NEEDED,
                            "doctorId is required");
                }
                if (null == memberId) {
//                    log.error("memberId is required");
                    throw new DAOException(DAOException.VALUE_NEEDED,
                            "memberId is required");
                }
                if (null == leader) {
//                    log.error("leader is required");
                    throw new DAOException(DAOException.ACCESS_DENIED, "leader is required");
                }
                DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                if (null == doctorDAO.getByDoctorIdAndTeams(doctorId)) {
                    log.info("该团队不存在,无法添加团队成员");
                    throw new DAOException(609, "该团队不存在,无法添加团队成员");
                }
                // 判断指定团队中是否存在该成员
                DoctorGroup doctorGroup = getByDoctorIdAndMemberId(doctorId, memberId);
                if (null != doctorGroup) {
                    log.info("团队中已经存在该成员");
                    throw new DAOException(609, "团队中已经存在该成员");
                }
                // 添加第一个团队成员，将咨询开关打开
                DoctorGroupDAO doctorGroupDAO = DAOFactory.getDAO(DoctorGroupDAO.class);
                ConsultSetDAO consultSetDAO = DAOFactory.getDAO(ConsultSetDAO.class);
                if (doctorGroupDAO.findByDoctorId(doctorId).size() == 0) {
                    ConsultSet consultSet = new ConsultSet();
                    consultSet.setDoctorId(doctorId);
                    consultSet.setMeetClinicStatus(1);
                    consultSet.setTransferStatus(1);
                    consultSet.setOnLineStatus(1);
                    consultSetDAO.addOrupdateConsultSet(consultSet);
                }
                DoctorGroup dg = new DoctorGroup();
                dg.setDoctorId(doctorId);
                dg.setMemberId(memberId);
                dg.setLeader(leader);
                doctorGroupDAO.save(dg);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
    }

    /**
     * 搜素医生优化（添加团队医生）
     *
     * @param members    医生内码列表
     * @param profession 专科
     * @param name       姓名搜索内容
     * @param busId      业务类型--1、转诊 2、会诊 3、咨询 4、预约
     * @param strUO      授权机构
     * @param start      页面开始位置
     * @param limit      没也限制条数
     * @return List<Integer>
     * @author luf
     */
    public List<Integer> findDocIdByMembers(final List<Integer> members, final String profession, final String name, final int busId, final String strUO, final int start, final int limit) {
        HibernateStatelessResultAction<List<Integer>> action = new AbstractHibernateStatelessResultAction<List<Integer>>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "select distinct g.doctorId from Doctor d,Organ o,Employment e,ConsultSet c,UnitOpauthorize u,DoctorGroup g "
                                + "where (g.memberId in (:members)) and g.doctorId=d.doctorId and o.organId=e.organId and "
                                + "d.doctorId=e.doctorId and d.doctorId=c.doctorId and d.status=1 and (g.doctorId not in(:members))");
                hql.append(strUO);
                if (StringUtils.isEmpty(name)) {
                    switch (busId) {
                        case 1:
                            hql.append(" and c.transferStatus=1");
                            break;
                        case 2:
                            hql.append(" and c.meetClinicStatus=1");
                            break;
                        case 3:
                        case 4:
                            hql.append(" and (c.onLineStatus=1 or c.appointStatus=1)");
                            break;
                        default:
                            break;
                    }
                }
                if (!StringUtils.isEmpty(profession)) {
                    hql.append(" and d.profession like :profession");
                }
                Query q = statelessSession.createQuery(hql.toString());
                if (!StringUtils.isEmpty(profession)) {
                    q.setParameter("profession", profession + "%");
                }
                q.setParameterList("members", members);
                q.setMaxResults(limit);
                q.setFirstResult(start);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 根据团队Id添加团队成员
     *
     * @param doctorId
     * @param doctorGroups
     */
    @RpcService
    public void saveDoctorGroups(final Integer doctorId, final List<DoctorGroup> doctorGroups) {
        if (doctorId == null) {
//            log.error("doctorId is required");
            throw new DAOException(DAOException.VALUE_NEEDED, "doctorId is required");
        }
        if (doctorGroups == null || doctorGroups.size() == 0) {
//            log.error("doctorGroups is required");
            throw new DAOException(DAOException.VALUE_NEEDED, "doctorGroups is required");
        }

        HibernateStatelessResultAction<Doctor> action = new AbstractHibernateStatelessResultAction<Doctor>() {
            public void execute(StatelessSession ss) throws Exception {
                DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                if (doctorDAO.getByDoctorIdAndTeams(doctorId) == null) {
//                    log.info("该团队不存在,无法添加团队成员");
                    throw new DAOException(609, "该团队不存在,无法添加团队成员");
                }
                for (DoctorGroup doctorGroup : doctorGroups) {
                    if (doctorGroup.getMemberId() == null) {
//                        log.error("MemberId of doctorGroup is required");
                        throw new DAOException(DAOException.VALUE_NEEDED, "MemberId of doctorGroup is required");
                    }
                    if (doctorGroup.getLeader() == null) {
//                        log.error("Leader of doctorGroup is required");
                        throw new DAOException(DAOException.VALUE_NEEDED, "Leader of doctorGroup is required");
                    }
                    // 判断指定团队中是否存在成员
                    DoctorGroup targetDoctorGroup = getByDoctorIdAndMemberId(doctorId, doctorGroup.getMemberId());
                    if (targetDoctorGroup != null) {
//                        log.info("该团队中已经存在该成员");
                        throw new DAOException(609, "该团队中已经存在该成员");
                    }
                    doctorGroup.setDoctorId(doctorId);
                    save(doctorGroup);
                }
                // 如果是第一次添加团队成员，将咨询开关打开
                if (findByDoctorId(doctorId).size() == doctorGroups.size()) {
                    ConsultSetDAO consultSetDAO = DAOFactory.getDAO(ConsultSetDAO.class);
                    ConsultSet consultSet = new ConsultSet();
                    consultSet.setDoctorId(doctorId);
                    consultSet.setTransferStatus(1);
                    consultSet.setMeetClinicStatus(1);
                    consultSet.setOnLineStatus(1);
                    consultSetDAO.addOrupdateConsultSet(consultSet);
                }
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
    }

    /**
     * zhongzx
     * 根据医生团队Id查找团队的管理员和队长
     * @param doctorId
     * @return
     */
    @DAOMethod(sql = "from DoctorGroup where doctorId = :doctorId and leader in (1,2)")
    public abstract List<DoctorGroup> findLeader(@DAOParam("doctorId") Integer doctorId);

    /**
     * zhongzx
     * 根据医生团队Id查找团队的管理员和队长的医生信息
     * @param doctorId
     * @return
     */
    @RpcService
    public List<Doctor> findLeaderByDoctorId(Integer doctorId){
        List<DoctorGroup> doctorGroupList = this.findLeader(doctorId);
        List<Doctor> doctorList = null;
        if(null != doctorGroupList){
            DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
            doctorList = new ArrayList<>();
            for(DoctorGroup doctorGroup:doctorGroupList){
                Doctor d = doctorDAO.getByDoctorId(doctorGroup.getMemberId());
                doctorList.add(d);
            }
        }
        return doctorList;
    }

    /**
     * zhongzx
     * 查询作为管理员或队长的医生团队的doctorId
     * @param memberId
     * @return
     */
    @DAOMethod(sql = "select doctorId from DoctorGroup where memberId=:memberId and leader in (1,2)")
    public abstract List<Integer> findDoctorIdsByLeaderId(@DAOParam("memberId") Integer memberId);

    /**
     * 根据模式获取团队医生内码列表
     *
     * @param doctorId  医生内码
     * @param groupMode 团队接受模式-0抢单1非抢单
     * @return List<String>
     * @author luf
     */
    public List<Integer> findDoctorIdsByMemberIdAndMode(final Integer doctorId, final Integer groupMode) {
        HibernateStatelessResultAction<List<Integer>> action = new AbstractHibernateStatelessResultAction<List<Integer>>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                StringBuffer hql = new StringBuffer("select g.doctorId from DoctorGroup g,Doctor d where g.memberId=:doctorId and d.doctorId=g.doctorId");
                if (groupMode != null && groupMode.equals(1)) {
                    //非抢单模式
                    hql.append(" and d.groupMode=1");
                } else {
                    hql.append(" and (d.groupMode is null or d.groupMode='' or d.groupMode=0)");
                }
                Query q = statelessSession.createQuery(hql.toString());
                q.setParameter("doctorId", doctorId);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    @RpcService
    @DAOMethod(sql = "select memberId from DoctorGroup where doctorId=:doctorId and leader = 2")
    public abstract List<Integer> findAdministratorByDoctorId(@DAOParam("doctorId") Integer doctorId);

    @DAOMethod(sql = "select memberId from DoctorGroup where doctorId=:doctorId and leader = 1")
    public abstract Integer getLeaderByDoctorId(@DAOParam("doctorId") Integer doctorId);
}
