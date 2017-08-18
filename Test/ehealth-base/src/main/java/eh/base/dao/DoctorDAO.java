package eh.base.dao;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import ctd.access.AccessToken;
import ctd.access.AccessTokenController;
import ctd.account.Client;
import ctd.account.UserRoleToken;
import ctd.account.user.User;
import ctd.account.user.UserController;
import ctd.account.user.UserRoleTokenEntity;
import ctd.controller.exception.ControllerException;
import ctd.controller.notifier.NotifierCommands;
import ctd.controller.notifier.NotifierMessage;
import ctd.controller.updater.ConfigurableItemUpdater;
import ctd.dictionary.DictionaryController;
import ctd.dictionary.DictionaryItem;
import ctd.dictionary.service.DictionaryLocalService;
import ctd.dictionary.service.DictionarySliceRecordSet;
import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.bean.QueryContext;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessAction;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.persistence.support.impl.access.AccessTokenDAO;
import ctd.persistence.support.impl.dictionary.DBDictionaryItemLoader;
import ctd.persistence.support.impl.user.UserDAO;
import ctd.persistence.support.impl.user.UserRoleTokenDAO;
import ctd.spring.AppDomainContext;
import ctd.util.AppContextHolder;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import ctd.util.context.Context;
import ctd.util.context.ContextUtils;
import ctd.util.converter.ConversionUtils;
import ctd.util.event.GlobalEventExecFactory;
import eh.base.constant.*;
import eh.base.service.DoctorInfoService;
import eh.base.service.QRInfoService;
import eh.base.user.RegisterDoctorSevice;
import eh.base.user.UserSevice;
import eh.bus.constant.CloudClinicSetConstant;
import eh.bus.dao.*;
import eh.bus.service.common.ClientPlatformEnum;
import eh.bus.service.common.CurrentUserInfo;
import eh.bus.service.video.RTMService;
import eh.cdr.service.RecipeService;
import eh.entity.base.*;
import eh.entity.bus.CloudClinicQueue;
import eh.entity.bus.CloudClinicSet;
import eh.entity.bus.ConsultSet;
import eh.entity.bus.SearchContent;
import eh.entity.bus.housekeeper.RecommendDoctorBean;
import eh.entity.bus.msg.SimpleThird;
import eh.entity.bus.msg.SimpleWxAccount;
import eh.entity.his.DoctorDeptRequest;
import eh.entity.mpi.Patient;
import eh.entity.mpi.Recommend;
import eh.entity.mpi.RelationDoctor;
import eh.entity.msg.SmsInfo;
import eh.entity.tx.TxDoctor;
import eh.entity.wh.BaseDoctor;
import eh.entity.wx.WXConfig;
import eh.mpi.dao.RecommendDAO;
import eh.mpi.dao.RelationDoctorDAO;
import eh.op.dao.WXConfigsDAO;
import eh.push.SmsPushService;
import eh.remote.IWXServiceInterface;
import eh.util.EmojiFilter;
import eh.util.UploadFile;
import eh.utils.DateConversion;
import eh.utils.LocalStringUtil;
import eh.utils.MapValueUtil;
import eh.utils.ValidateUtil;
import eh.wxpay.util.Util;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Query;
import org.hibernate.StatelessSession;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;

import java.math.RoundingMode;
import java.text.Collator;
import java.text.DecimalFormat;
import java.util.*;

public abstract class DoctorDAO extends HibernateSupportDelegateDAO<Doctor>
        implements DBDictionaryItemLoader<Doctor> {


    public static final Logger log = LoggerFactory.getLogger(DoctorDAO.class);

    public DoctorDAO() {
        super();
        this.setEntityName(Doctor.class.getName());
        this.setKeyField("doctorId");
    }

    @RpcService
    @DAOMethod()
    public abstract List<Doctor> findByOrgan(Integer organId);

    @RpcService
    @DAOMethod()
    public abstract List<Doctor> findByOrganAndHaveAppoint(Integer organId,
                                                           Integer haveAppoint);

    @RpcService
    @DAOMethod
    public abstract Doctor getByMobile(String mobile);

    @RpcService
    @DAOMethod
    public abstract Doctor getByDoctorId(int id);

    @RpcService
    @DAOMethod
    public abstract Doctor getByIdNumber(String idNumber);

    @DAOMethod
    public abstract Doctor getByIdNumberAndStatus(String idNumber, Integer status);

    @RpcService
    @DAOMethod
    public abstract Doctor getByDoctorCertCode(String certCode);

    @RpcService
    @DAOMethod
    public abstract Doctor getByName(String name);

    @RpcService
    @DAOMethod
    public abstract Doctor getByNameAndOrgan(String name, int Organ);

    @RpcService
    @DAOMethod
    public abstract void updateNameByMobile(String name, String mobile);

    @RpcService
    @DAOMethod
    public abstract void updateSpecificSignByDoctorId(String specificSign,
                                                      int doctorId);// 个人动态签名更新服务

    @RpcService
    @DAOMethod
    public abstract void updateBusyFlagByDoctorId(int busyFlag, int doctorId);// 设置医生为忙闲状态服务

    @RpcService
    public int getBusyFlagByDoctorId(int doctorId) {
        Doctor doc = getByDoctorId(doctorId);
        if (doc.getBusyFlag() == null) {
            return 0;
        } else {
            return doc.getBusyFlag();
        }
    }

    @RpcService
    @DAOMethod
    public abstract QueryResult<Doctor> findByDoctorIdAfter(int id);

    @RpcService
    @DAOMethod
    public abstract QueryResult<Doctor> findByDoctorIdBetween(int startId,
                                                              int endId);

    @RpcService
    @DAOMethod
    public abstract QueryResult<Doctor> findByCreateDtBetween(Date start,
                                                              Date end);

    @RpcService
    @DAOMethod(sql = "update Doctor set name=:name where doctorId =:id")
    public abstract void updateNameByDoctorId(@DAOParam("id") int id,
                                              @DAOParam("name") String name);

    @DAOMethod(sql = "update Doctor set consultAmount=:consultAmount where doctorId =:doctorId")
    public abstract void updateConsultAmountByDoctorId(@DAOParam("consultAmount") long consultAmount,
                                              @DAOParam("doctorId") Integer doctorId);

    @RpcService
    @DAOMethod
    public abstract List<Doctor> findByDoctorIdIn(List<Integer> ids);

    /**
     * 获取有效的医生(医生状态为审核)
     *
     * @param ids
     * @return
     * @author ZX
     * @date 2015-8-26 下午3:30:36
     */
    @RpcService
    public List<Doctor> findEffectiveDocByDoctorIdIn(List<Integer> ids) {
        List<Doctor> docs = new ArrayList<Doctor>();
        List<Doctor> list = findByDoctorIdIn(ids);
        for (Doctor doctor : list) {
            if (doctor.getStatus() != null && doctor.getStatus() == 1) {
                docs.add(doctor);
            }
        }

        return docs;
    }

    @DAOMethod(sql = "select new ctd.dictionary.DictionaryItem(doctorId,name) from Doctor order by doctorId")
    public abstract List<DictionaryItem> findAllDictionaryItem(
            @DAOParam(pageStart = true) int start,
            @DAOParam(pageLimit = true) int limit);

    @DAOMethod(sql = "select new ctd.dictionary.DictionaryItem(doctorId,name) from Doctor where doctorId=:id")
    public abstract DictionaryItem getDictionaryItem(@DAOParam("id") Object key);

    /**
     * 服务名:推荐医生查询服务
     *
     * @param
     * @return List<Doctor>
     * @throws DAOException 2015-10-23 zhangx 授权机构由于前段排序显示问题，对原来的设计进行修改</br>
     *                      修改前：A授权给B，B能看到A，A也能看到B;修改后：A授权给B，B能看到A，A不能看到B</br>
     *                      增加按orderNum排序</br>
     * @author yxq
     */
    @RpcService
    @SuppressWarnings({"rawtypes", "unchecked"})
    public List<Doctor> recommendDoctor(final int organID,
                                        final String profession, final int doctorID, final String areaCode,
                                        final String disease, final String buesType) throws DAOException {
        List<Doctor> docList = new ArrayList<Doctor>();
        HibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                // 1．先查找本机构能转诊机构的医生、并且这个医生是同专科的、同区域编码，并且擅长病人现在疾病的,
                // 如果找不到记录，则去掉擅长病人现在疾病这个条件再查一次。
                StringBuilder hql = new StringBuilder(
                        // "select a.doctorId from Doctor a ,Organ b "
                        // + "where b.organId=a.organ and a.profession=:profession "
                        // + "and b.addrArea=:areaCode and (:disease in a.domain) and "
                        // + "(b.organId in (select organId from UnitOpauthorize "
                        // +
                        // "where accreditOrgan=:organID and accreditBuess=:buesType) "
                        // +
                        // "or b.organId in (select accreditOrgan from UnitOpauthorize "
                        // + "where organId=:organID and accreditBuess=:buesType))");
                        "select a.doctorId from Doctor a ,Organ b "
                                + "where b.organId=a.organ and a.profession=:profession "
                                + "and b.addrArea=:areaCode and (:disease in a.domain) and "
                                + "(b.organId in (select organId from UnitOpauthorize "
                                + "where accreditOrgan=:organID and accreditBuess=:buesType) "
                                + ")");
                Query query = ss.createQuery(hql.toString());
                query.setParameter("organID", organID);
                query.setString("profession", profession);
                query.setString("areaCode", areaCode);
                query.setString("disease", disease);
                query.setString("buesType", buesType);
                List<Integer> docId = new ArrayList<Integer>();
                docId = query.list();
                if (docId.size() < 1) {
                    StringBuilder hqls = new StringBuilder(
                            // "select a.doctorId from Doctor a ,Organ b where b.organId=a.organ "
                            // +
                            // "and a.profession=:profession and b.addrArea=:areaCode and "
                            // + "(b.organId in (select organId from UnitOpauthorize "
                            // +
                            // "where accreditOrgan=:organID and accreditBuess=:buesType) "
                            // +
                            // "or b.organId in (select accreditOrgan from UnitOpauthorize "
                            // +
                            // "where organId=:organID and accreditBuess=:buesType))");
                            "select a.doctorId from Doctor a ,Organ b where b.organId=a.organ "
                                    + "and a.profession=:profession and b.addrArea=:areaCode and "
                                    + "(b.organId in (select organId from UnitOpauthorize "
                                    + "where accreditOrgan=:organID and accreditBuess=:buesType) "
                                    + ")");
                    Query querys = ss.createQuery(hqls.toString());
                    querys.setParameter("organID", organID);
                    querys.setString("profession", profession);
                    querys.setString("areaCode", areaCode);
                    querys.setString("buesType", buesType);
                    docId = query.list();
                }

                // 2．查找本医生最常转诊或会诊的医生。
                StringBuilder hq;
                if (buesType.equals("1")) {
                    hq = new StringBuilder(
                            "Select targetDoctor,count(*) as nums from Transfer "
                                    + "where requestDoctor=:doctorID group BY targetDoctor order by nums DESC");
                } else {
                    hq = new StringBuilder(
                            "Select b.targetDoctor,count(*) as nums from Meetclinic a ,MeetclinicResult b "
                                    + "where a.meetClinicId=b.meetClinicId and a.requestDoctor=:doctorID "
                                    + "group BY b.targetDoctor order by nums DESC");
                }
                Query que = ss.createQuery(hq.toString());
                que.setParameter("doctorID", doctorID);
                List temp = que.list();
                List<Integer> dId = new ArrayList<Integer>();
                for (int i = 0; i < temp.size(); i++) {
                    Object[] ob = (Object[]) temp.get(i);
                    dId.add((Integer) ob[0]);
                }

                // a.不允许两种推荐医生重复 b.推荐医生数不超过4个，且两种情况都应该含有
                // c.如果两种情况都超过三个每种取两个、
                // 一个超过一个没超过没超过的全部取出超过的补足四个、
                // 全部没超过三个全部取出（在不重复的前提下）
                List<Integer> finList = new ArrayList<Integer>();
                int len = docId.size();
                int siz = dId.size();
                if (len >= 3) {
                    int count = 0;
                    finList.add(docId.get(0));
                    finList.add(docId.get(1));
                    for (int i = 0; i < siz; i++) {
                        if (count <= 2) {
                            Integer in = dId.get(i);
                            if (!(in.equals(finList.get(0)) || in
                                    .equals(finList.get(1)))) {
                                finList.add(in);
                                count++;
                            }
                        } else {
                            break;
                        }
                    }
                    if (finList.size() < 4) {
                        for (int i = 0; i < len - 2; i++) {
                            if (finList.size() < 4) {
                                finList.add(docId.get(i + 2));
                            } else {
                                break;
                            }
                        }
                    }
                } else {
                    if (len <= 0) {
                        for (int i = 0; i < siz; i++) {
                            if (i < 4) {
                                finList.add(dId.get(i));
                            } else {
                                break;
                            }
                        }
                    } else if (len == 1) {
                        finList.add(docId.get(0));
                        for (int i = 0; i < siz; i++) {
                            if (finList.size() < 4) {
                                if (!(finList.get(0).equals(dId.get(i)))) {
                                    finList.add(dId.get(i));
                                }
                            } else {
                                break;
                            }
                        }
                    }
                }

                // 查询推荐医生的信息
                int m = finList.size() - 1;
                if (m >= 0) {
                    String h = "from Doctor where doctorId in (";
                    for (int i = 0; i < m; i++) {
                        h += finList.get(i) + ", ";
                    }
                    h += finList.get(m) + ")";
                    Query q = ss.createQuery(h);
                    List<Doctor> tempList = q.list();

                    for (Doctor doctor : tempList) {
                        doctor.setOrgan(organID);
                    }
                    setResult(tempList);
                } else {
                    setResult(null);
                }
            }
        };

        HibernateSessionTemplate.instance().executeReadOnly(action);
        docList = (List) action.getResult();

        return docList;
    }

    /**
     * 医生信息更新服务--hyj
     *
     * @param doctor
     */
    @RpcService
    public Boolean updateDoctorByDoctorId(final Doctor doctor) {
        if (doctor.getDoctorId() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "doctorId is required");
        }
        Doctor target = getByDoctorId(doctor.getDoctorId());
        doctor.setLastModify(new Date());
        BeanUtils.map(doctor, target);
        target.setProTitle(StringUtils.isEmpty(target.getProTitle()) ? "99" : target.getProTitle());
        Doctor d = update(target);

        //当医生注销时:同时需要注销这个医生的token信息
        if (ObjectUtils.nullSafeEquals(d.getStatus(), 9)) {
            AccessTokenDAO accessDao = DAOFactory.getDAO(AccessTokenDAO.class);
            try {
                List<AccessToken> tokenList = accessDao.findByUser(d.getMobile(), Util.getUrtForDoctor(d.getMobile()));
                for (AccessToken accessToken : tokenList) {
                    AccessTokenController.instance().getUpdater().remove(accessToken.getId());
                }
            } catch (ControllerException e) {
                log.error("updateDoctorByDoctorId() error: "+e);
            }
        }

        Integer key = d.getDoctorId();
        DictionaryItem item = getDictionaryItem(key);
        //this.fireEvent(new UpdateDAOEvent(key,item));
        NotifierMessage msg = new NotifierMessage(NotifierCommands.ITEM_UPDATE, "eh.base.dictionary.Doctor");
        msg.setLastModify(System.currentTimeMillis());
        msg.addUpdatedItems(item);
        try {
            DictionaryController.instance().getUpdater().notifyMessage(msg);
        } catch (ControllerException e) {
            log.error("updateDoctorByDoctorId() error: "+e);
        }

        new UserSevice().updateUserCache(d.getMobile(), SystemConstant.ROLES_DOCTOR, "doctor", d);
        return true;
    }

    /**
     * @param organId    目标机构代码
     * @param profession 专科编码
     * @param doctorId   目标医生编码
     * @param areaCode   目标区域编码
     * @param buesType   业务类型: 1 转诊 2会诊
     * @return
     * @author Eric
     */
    @RpcService
    public List<Doctor> similarlyDoctor(final Integer organId,
                                        final String profession, final Integer doctorId,
                                        final String areaCode, final String buesType) {
        List<Doctor> list = null;

        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            // 查找能转诊或会诊到目标医生机构的医生、并且这些医生跟目标医生是同专科的、同区域编码,
            // 如果找不到记录，则去掉同区域编码这个条件再查一次。
            List<Doctor> list = null;
            String hql = " select d from Doctor d,Organ g where d.organ=g.organId "
                    + "and d.status=1 and d.profession=:profession and g.addrArea=:areaCode "
                    + "and (organ in (select accreditOrgan from UnitOpauthorize u "
                    + "where u.organId=:organId and u.accreditBuess=:buesType))";

            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                Query q = ss.createQuery(hql);
                q.setInteger("organId", organId);
                q.setString("profession", profession);
                q.setString("areaCode", areaCode);
                q.setString("buesType", buesType);
                list = q.list();
                if (list == null || list.size() == 0) {
                    hql = "select d from Doctor d,Organ g where d.organ=g.organId "
                            + "and d.status=1 and d.profession=:profession and "
                            + "(organ in (select accreditOrgan from UnitOpauthorize u "
                            + "where u.organId=:organId and u.accreditBuess=:buesType))";
                    Query qu = ss.createQuery(hql);
                    qu.setInteger("organId", organId);
                    qu.setString("profession", profession);
                    qu.setString("buesType", buesType);
                    list = qu.list();
                }
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        list = action.getResult();
        return list;
    }

    /**
     * 查询医生信息（按医生专科代码查询）--hyj
     *
     * @param profession
     * @return
     */
    @RpcService
    @DAOMethod
    public abstract List<Doctor> findByProfessionLike(String profession);

    /**
     * 查询医生信息(按医生专科代码查询)--分页
     *
     * @param profession
     * @return
     * @author hyj
     */
    @RpcService
    public List<Doctor> findByProfessionLikeWithPage(String profession,
                                                     int start) {
        return findByProfessionLikeByStartAndLimit(profession, start, 10);
    }

    @DAOMethod(sql = "from Doctor where profession like :profession")
    public abstract List<Doctor> findByProfessionLikeByStartAndLimit(
            @DAOParam("profession") String profession,
            @DAOParam(pageStart = true) int start,
            @DAOParam(pageLimit = true) int limit);

    /**
     * 更新医生是否有预约号源标志--hyj
     *
     * @param id
     * @param haveAppoint
     */
    @RpcService
    @DAOMethod(sql = "update Doctor set haveAppoint=:haveAppoint "
            + "where doctorId =:id and haveAppoint<>:haveAppoint")
    public abstract void updateHaveAppointByDoctorId(@DAOParam("id") int id,
                                                     @DAOParam("haveAppoint") int haveAppoint);

    /**
     * 更新医生头像信息
     *
     * @param id
     * @param photo
     */
    @RpcService
    @DAOMethod(sql = "update Doctor set photo=:photo where doctorId =:id")
    public abstract void updatePhotoByDoctorId(@DAOParam("id") int id,
                                               @DAOParam("photo") int photo);

    /**
     * 更新医生执业证书照片信息
     *
     * @param id
     * @param doctorCertImage
     */
    @RpcService
    @DAOMethod(sql = "update Doctor set doctorCertImage = :doctorCertImage where doctorId =:id")
    public abstract void updateCertImageByDoctorId(@DAOParam("id") int id,
                                                   @DAOParam("doctorCertImage") int doctorCertImage);

    /**
     * 更新医生执业证书照片2信息
     *
     * @param id
     * @param doctorCertImage
     */
    @RpcService
    @DAOMethod(sql = "update Doctor set doctorCertImage2 = :doctorCertImage where doctorId =:id")
    public abstract void updateCertImage2ByDoctorId(@DAOParam("id") int id,
                                                    @DAOParam("doctorCertImage") int doctorCertImage);

    /**
     * 更新医生职称照片信息
     *
     * @param id
     * @param proTitleImage
     */
    @RpcService
    @DAOMethod(sql = "update Doctor set proTitleImage = :proTitleImage where doctorId =:id")
    public abstract void updateProTitleImageByDoctorId(@DAOParam("id") int id,
                                                       @DAOParam("proTitleImage") int proTitleImage);

    @RpcService
    @DAOMethod
    public abstract void updateWorkCardImageByDoctorId(Integer workCardImage, Integer doctorId);

    /**
     * 统计当前医生总数
     */
    @RpcService
    @DAOMethod(sql = "SELECT COUNT(*) FROM Doctor")
    public abstract Long getAllDoctorNum();

    @DAOMethod(sql = "SELECT COUNT(*) FROM Doctor d,Organ o "
            + "where d.organ=o.organId and o.manageUnit like :manageUnit")
    public abstract Long getAllDocNumWithManager(
            @DAOParam("manageUnit") String manageUnit);

    @DAOMethod(sql = "SELECT COUNT(*) FROM Doctor where teams=0 and LENGTH(Mobile) > 0")
    public abstract Long getAllDoctorNumWithoutTeams();

    /**
     * 统计当前机构的医生总数
     */
    @RpcService
    public Long getAllDoctorNumWithManager(String manageUnit) {
        return getAllDocNumWithManager(manageUnit + "%");
    }

    /**
     * 统计指定时间段内的医生数量
     * @param manageUnit
     * @param startDate 开始时间
     * @param endDate   结束时间
     * @return
     * @author ZX
     * @date 2015-5-21 下午3:58:02
     */
    @RpcService
    @DAOMethod(sql = "SELECT COUNT(*) FROM Doctor d,Organ o "
            + "where d.organ=o.organId and o.manageUnit like :manageUnit "
            + "and date(d.createDt) >=date(:startDate) "
            + "and date(d.createDt) <=date(:endDate) ")
    public abstract Long getDocNumFromTo(
            @DAOParam("manageUnit") String manageUnit,
            @DAOParam("startDate") Date startDate,
            @DAOParam("endDate") Date endDate);

    /**
     * 获取本月新增
     *
     * @return
     * @author ZX
     * @date 2015-5-21 下午4:00:17
     */
    @RpcService
    public Long getDocNumByMonth(String manageUnit) {
        Date startDate = DateConversion.firstDayOfThisMonth();
        Date endDate = new Date();
        return getDocNumFromTo(manageUnit + "%", startDate, endDate);
    }

    /**
     * 获取昨日新增医生数
     *
     * @return
     * @author ZX
     * @date 2015-5-21 下午4:00:17
     */
    @RpcService
    public Long getDocNumByYesterday(String manageUnit) {
        Date date = Context.instance().get("date.getYesterday", Date.class);
        return getDocNumFromTo(manageUnit + "%", date, date);
    }

    @DAOMethod(limit = 1000, sql = "from Doctor where mobile not in (select userId from Device)")
    public abstract List<Doctor> findDoctor();

    /**
     * 分页获取医生手机号
     *
     * @param start
     * @param limit
     * @return
     */
    public List<String> findDotorMobileForPage(final int start, final int limit) {
        HibernateStatelessResultAction<List<String>> action = new AbstractHibernateStatelessResultAction<List<String>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = new String("select mobile from Doctor where teams=0 and LENGTH(Mobile) > 0");
                Query q = ss.createQuery(hql);
                q.setFirstResult(start);
                q.setMaxResults(limit);

                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 按姓名模糊查询医生信息服务
     *
     * @param name --医生模糊姓名
     * @return
     * @author hyj
     */
    @RpcService
    public List<Doctor> findByNameLike(final String name, final int startPage) {
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            List<Doctor> result = new ArrayList<Doctor>();

            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "from Doctor where name like :name");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("name", "%" + name + "%");
                q.setMaxResults(10);
                q.setFirstResult(startPage);
                result = q.list();
                setResult(result);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 按是否在线查询医生信息服务
     *
     * @param online --是否在线
     * @param start  --查询起始页
     * @return
     * @author hyj
     */
    @RpcService
    public List<Doctor> findByOnlineWithPage(int online, int start) {
        return findByOnline(online, start, 10);
    }

    @DAOMethod(sql = "from Doctor where online=:online")
    public abstract List<Doctor> findByOnline(@DAOParam("online") int online,
                                              @DAOParam(pageStart = true) int start,
                                              @DAOParam(pageLimit = true) int limit);

    /**
     * 按是否有号查询医生信息服务
     *
     * @param haveAppoint --是否有号
     * @param start       --查询起始页
     * @return
     * @author hyj
     */
    @RpcService
    public List<Doctor> findByHaveAppointWithPage(int haveAppoint, int start) {
        return findByHaveAppoint(haveAppoint, start, 10);
    }

    @DAOMethod(sql = "from Doctor where haveAppoint=:haveAppoint")
    public abstract List<Doctor> findByHaveAppoint(
            @DAOParam("haveAppoint") int haveAppoint,
            @DAOParam(pageStart = true) int start,
            @DAOParam(pageLimit = true) int limit);

    /**
     * 按区域模糊查询医生
     *
     * @param addrArea
     * @param startPage
     * @return
     * @author zsq
     */
    @RpcService
    public List<Doctor> findByAddrAreaLike(final String addrArea,
                                           final int startPage) {
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            List<Doctor> result = new ArrayList<Doctor>();

            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                // TODO Auto-generated method stub
                StringBuilder hql = new StringBuilder(
                        "select DISTINCT d from Doctor d,Organ o,Employment e "
                                + "where o.addrArea like :addrArea and o.organId=e.organId "
                                + "and e.doctorId=d.doctorId and d.status=1");

                Query q = ss.createQuery(hql.toString());
                q.setParameter("addrArea", "%" + addrArea + "%");
                q.setMaxResults(10);
                q.setFirstResult(startPage);
                result = q.list();
                setResult(result);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 搜素医生优化（专科、区域、擅长疾病、姓名、是否在线、是否有号）
     *
     * @param profession
     * @param addrArea
     * @param domain
     * @param name
     * @param onLineStatus
     * @param haveAppoint
     * @param startPage
     * @return
     * @author zsq
     */
    @RpcService
    @SuppressWarnings({"unchecked"})
    public List<Doctor> searchDoctor(final String profession,
                                     final String addrArea, final String domain, final String name,
                                     final Integer onLineStatus, final Integer haveAppoint,
                                     final int startPage) {
        List<Doctor> docList = new ArrayList<Doctor>();
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            List<Doctor> list = null;

            @Override
            public void execute(StatelessSession ss) throws Exception {
                // TODO Auto-generated method stub
                StringBuilder hql = new StringBuilder(
                        "select distinct d from Doctor d,Organ o,Employment e,ConsultSet c "
                                + "where o.organId=e.organId and d.doctorId=e.doctorId "
                                + "and d.doctorId=c.doctorId and d.status=1");

                if (!profession.equals("")) {
                    hql.append(" and d.profession like :profession");
                }
                if (!addrArea.equals("")) {
                    hql.append(" and o.addrArea like :addrArea");
                }
                if (!domain.equals("")) {
                    hql.append(" and d.domain like:domain");
                }
                if (!name.equals("")) {
                    hql.append(" and d.name like:name");
                }
                if (onLineStatus != null) {
                    hql.append(" and c.onLineStatus=:onLineStatus");
                }
                if (haveAppoint != null) {
                    hql.append(" and d.haveAppoint=:haveAppoint");
                }

                Query q = ss.createQuery(hql.toString());
                if (!profession.equals("")) {
                    q.setParameter("profession", profession + "%");
                }
                if (!addrArea.equals("")) {
                    q.setParameter("addrArea", addrArea + "%");
                }
                if (!domain.equals("")) {
                    q.setParameter("domain", "%" + domain + "%");
                }
                if (!name.equals("")) {
                    q.setParameter("name", "%" + name + "%");
                }
                if (onLineStatus != null) {
                    q.setParameter("onLineStatus", onLineStatus);
                }
                if (haveAppoint != null) {
                    q.setParameter("haveAppoint", haveAppoint);
                }
                q.setMaxResults(10);
                q.setFirstResult(startPage);
                list = q.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        docList = action.getResult();
        return docList;
    }

    /**
     * 搜素医生优化（专科、区域、擅长疾病、姓名、是否在线、是否有号[有号，不限]）
     *
     * @param profession   专科编码
     * @param addrArea     属地区域
     * @param domain       擅长领域
     * @param name         医生姓名
     * @param onLineStatus 在线状态̬
     * @param haveAppoint  预约号源标志
     * @param startPage    起始页
     * @param busId        业务类型--1、转诊 2、会诊 3、咨询 4、预约
     * @return List<Doctor>
     * <p>
     * 2015-10-23 zhangx 授权机构由于前段排序显示问题，对原来的设计进行修改</br>
     * 修改前：A授权给B，B能看到A，A也能看到B;修改后：A授权给B，B能看到A，A不能看到B</br>
     * 增加按orderNum排序</br>
     * @author LF
     */
    @RpcService
    @SuppressWarnings({"unchecked"})
    public List<Doctor> searchDoctorBuss(final String profession,
                                         final String addrArea, final String domain, final String name,
                                         final Integer onLineStatus, final Integer haveAppoint,
                                         final int startPage, final int busId) {
        UserRoleToken ur = (UserRoleToken) ContextUtils
                .get(Context.USER_ROLE_TOKEN);
        Employment eSelf = (Employment) ur.getProperty("employment");
        List<Doctor> docList = new ArrayList<Doctor>();

        if (eSelf == null || eSelf.getOrganId() == null) {
            return docList;
        }
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        String bussType = "0";
        switch (busId) {
            case 1:
            case 4:
                bussType = "1";
                break;
            case 2:
                bussType = "2";
                break;
            case 3:
            default:
                return null;
        }
        final List<Organ> organList = organDAO.queryRelaOrganNew(eSelf.getOrganId(), bussType, addrArea);

        StringBuilder sb = new StringBuilder(" and(");
        for (Organ o : organList) {
            sb.append(" e.organId=").append(o.getOrganId()).append(" OR");
        }
        final String strUO = sb.substring(0, sb.length() - 2) + ")";

        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            List<Doctor> list = null;

            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "select distinct d from Doctor d,Organ o,Employment e,ConsultSet c "
                                + "where o.organId=e.organId and d.doctorId=e.doctorId "
                                + "and d.doctorId=c.doctorId and d.status=1");
                if (organList != null && organList.size() > 0) {
                    hql.append(strUO);
                }
                switch (busId) {
                    case 1:
                        hql.append(" and c.transferStatus=1");
                        break;
                    case 2:
                        hql.append(" and c.meetClinicStatus=1");
                        break;
                    case 3:
                        hql.append(" and (c.onLineStatus=1 or c.appointStatus=1)");
                        break;
                }

                if (!StringUtils.isEmpty(profession)) {
                    hql.append(" and d.profession like :profession");
                }
                if (!StringUtils.isEmpty(addrArea)) {
                    hql.append(" and o.addrArea like :addrArea");
                }
                if (!StringUtils.isEmpty(domain)) {
                    hql.append(" and d.domain like:domain");
                }
                if (!StringUtils.isEmpty(name)) {
                    hql.append(" and d.name like:name");
                }
                if (onLineStatus != null) {
                    hql.append(" and c.onLineStatus=:onLineStatus");
                }
                if (haveAppoint != null && haveAppoint != 2) {
                    hql.append(" and d.haveAppoint=:haveAppoint");
                }

                Query q = ss.createQuery(hql.toString());
                if (!StringUtils.isEmpty(profession)) {
                    q.setParameter("profession", profession + "%");
                }
                if (!StringUtils.isEmpty(addrArea)) {
                    q.setParameter("addrArea", addrArea + "%");
                }
                if (!StringUtils.isEmpty(domain)) {
                    q.setParameter("domain", "%" + domain + "%");
                }
                if (!StringUtils.isEmpty(name)) {
                    q.setParameter("name", "%" + name + "%");
                }
                if (onLineStatus != null) {
                    q.setParameter("onLineStatus", onLineStatus);
                }
                if (haveAppoint != null && haveAppoint != 2) {
                    q.setParameter("haveAppoint", haveAppoint);
                }
                q.setMaxResults(10);
                q.setFirstResult(startPage);
                list = q.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        docList = action.getResult();
        if ( docList == null || docList.size() == 0 ) {
            return new ArrayList<Doctor>();
        }

        List<Doctor> targets = new ArrayList<Doctor>();
        EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
        DoctorTabDAO doctorTabDAO=DAOFactory.getDAO(DoctorTabDAO.class);
        for (Doctor doctor : docList) {
            Employment employment = employmentDAO
                    .getPrimaryEmpByDoctorId(doctor.getDoctorId());
            if (employment != null) {
                doctor.setDepartment(employment.getDepartment());
            }
            if(busId== BussTypeConstant.MEETCLINIC){//zhangsl 2017-05-26 15:25:51会诊中心标记新增
                doctor.setMeetCenter(doctorTabDAO.getMeetTypeByDoctorId(doctor.getDoctorId()));
            }
            targets.add(doctor);
        }

        return targets;
    }

    /**
     * Title: 搜素医生优化（专科、区域、擅长疾病、姓名、是否在线、是否有号[有号，不限]、职称）
     *
     * @param profession   专科编码
     * @param addrArea     属地区域
     * @param domain       擅长领域
     * @param name         医生姓名
     * @param onLineStatus 在线状态̬
     * @param haveAppoint  预约号源标志
     * @param startPage    起始页
     * @param busId        业务类型--1、转诊 2、会诊 3、咨询 4、预约
     * @param proTitle     职称
     * @return List<Doctor></br>
     * <p>
     * 2015-10-23 zhangx 授权机构由于前段排序显示问题，对原来的设计进行修改</br>
     * 修改前：A授权给B，B能看到A，A也能看到B;修改后：A授权给B，B能看到A，A不能看到B</br>
     * 增加按orderNum排序</br>
     * @author AngryKitty
     * @date 2015-9-15
     */
    @RpcService
    @SuppressWarnings({"unchecked"})
    public List<Doctor> searchDoctorBussNew(final String profession,
                                            final String addrArea, final String domain, final String name,
                                            final Integer onLineStatus, final Integer haveAppoint,
                                            final int startPage, final int busId, final String proTitle) {
        UserRoleToken ur = (UserRoleToken) ContextUtils
                .get(Context.USER_ROLE_TOKEN);
        Employment eSelf = (Employment) ur.getProperty("employment");
        List<Doctor> docList = new ArrayList<Doctor>();
        UnitOpauthorizeDAO dao = DAOFactory.getDAO(UnitOpauthorizeDAO.class);
        List<Integer> oList = dao.findByBussId(busId);
        if (oList == null) {
            oList = new ArrayList<Integer>();
        }
        if (eSelf == null || eSelf.getOrganId() == null) {
            return docList;
        }
        oList.add(eSelf.getOrganId());
        StringBuilder sb = new StringBuilder(" and(");
        for (Integer o : oList) {
            sb.append(" e.organId=").append(o).append(" OR");
        }
        final String strUO = sb.substring(0, sb.length() - 2) + ")";

        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            List<Doctor> list = null;

            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "select distinct d from Doctor d,Organ o,Employment e,ConsultSet c,UnitOpauthorize u "
                                + "where o.organId=e.organId and d.doctorId=e.doctorId "
                                + "and d.doctorId=c.doctorId and d.status=1 ");
                hql.append(strUO);
                switch (busId) {
                    case 1:
                        hql.append(" and c.transferStatus=1");
                        break;
                    case 2:
                        hql.append(" and c.meetClinicStatus=1");
                        break;
                    case 3:
                        hql.append(" and (c.onLineStatus=1 or c.appointStatus=1)");
                        break;
                }

                if (!StringUtils.isEmpty(profession)) {
                    hql.append(" and d.profession like :profession");
                }
                if (!StringUtils.isEmpty(addrArea)) {
                    hql.append(" and o.addrArea like :addrArea");
                }
                if (!StringUtils.isEmpty(domain)) {
                    hql.append(" and d.domain like:domain");
                }
                if (!StringUtils.isEmpty(name)) {
                    hql.append(" and d.name like:name");
                }
                if (onLineStatus != null) {
                    hql.append(" and c.onLineStatus=:onLineStatus");
                }
                if (haveAppoint != null && haveAppoint != 2) {
                    hql.append(" and d.haveAppoint=:haveAppoint");
                }
                if (!StringUtils.isEmpty(proTitle)) {
                    hql.append(" and d.proTitle=:proTitle");
                }

                Query q = ss.createQuery(hql.toString());
                if (!StringUtils.isEmpty(profession)) {
                    q.setParameter("profession", profession + "%");
                }
                if (!StringUtils.isEmpty(addrArea)) {
                    q.setParameter("addrArea", addrArea + "%");
                }
                if (!StringUtils.isEmpty(domain)) {
                    q.setParameter("domain", "%" + domain + "%");
                }
                if (!StringUtils.isEmpty(name)) {
                    q.setParameter("name", "%" + name + "%");
                }
                if (onLineStatus != null) {
                    q.setParameter("onLineStatus", onLineStatus);
                }
                if (haveAppoint != null && haveAppoint != 2) {
                    q.setParameter("haveAppoint", haveAppoint);
                }
                if (!StringUtils.isEmpty(proTitle)) {
                    q.setParameter("proTitle", proTitle);
                }
                q.setMaxResults(10);
                q.setFirstResult(startPage);
                list = q.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        docList = action.getResult();
        if ( docList == null || docList.size() == 0 ) {
            return new ArrayList<Doctor>();
        }

        List<Doctor> targets = new ArrayList<Doctor>();
        EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
        for (Doctor doctor : docList) {
            Employment employment = employmentDAO
                    .getPrimaryEmpByDoctorId(doctor.getDoctorId());
            if (employment != null) {
                doctor.setDepartment(employment.getDepartment());
            }
            targets.add(doctor);
        }

        return targets;
    }

    /**
     * 模糊查询医生
     *
     * @param profession 专科
     * @param domain     擅长疾病
     * @param name       姓名
     * @param startPage  第几页
     * @return List<Doctor>
     */
    @RpcService
    @SuppressWarnings({"unchecked"})
    public List<Doctor> fuzzySearchDoctor(final String profession,
                                          final String domain, final String name, final int startPage) {
        List<Doctor> docList;
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            List<Doctor> list = null;

            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "select DISTINCT d from Doctor d,Organ o,Employment e,ConsultSet c "
                                + "where o.organId=e.organId and d.doctorId=e.doctorId "
                                + "and d.doctorId=c.doctorId and d.status=1");
                if (!StringUtils.isEmpty(profession)) {
                    hql.append(" and d.profession like :profession");
                }
                if (!StringUtils.isEmpty(domain)) {
                    hql.append(" and d.domain like:domain");
                }
                if (!StringUtils.isEmpty(name)) {
                    hql.append(" and d.name like:name");
                }

                Query q = ss.createQuery(hql.toString());
                if (!StringUtils.isEmpty(profession)) {
                    q.setParameter("profession", profession + "%");
                }
                if (!StringUtils.isEmpty(domain)) {
                    q.setParameter("domain", "%" + domain + "%");
                }
                if (!StringUtils.isEmpty(name)) {
                    q.setParameter("name", "%" + name + "%");
                }
                q.setMaxResults(10);
                q.setFirstResult(startPage);
                list = q.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        docList = action.getResult();
        return docList;
    }

    /**
     * 模糊查询医生2
     *
     * @param profession 专科
     * @param domain     擅长疾病
     * @param name       姓名
     * @param startPage  第几页
     * @param bussType   1:转诊 2：会诊 3咨询 4预约
     * @return List<Doctor>
     * <p>
     * 2015-10-23 zhangx 授权机构由于前段排序显示问题，对原来的设计进行修改</br>
     * 修改前：A授权给B，B能看到A，A也能看到B;修改后：A授权给B，B能看到A，A不能看到B</br>
     * 增加按orderNum排序</br>
     */
    @RpcService
    @SuppressWarnings({"unchecked"})
    public List<Doctor> fuzzySearchDoctorWithServiceType(
            final String profession, final String domain, final String name,
            final int startPage, final int bussType) {
        UserRoleToken ur = (UserRoleToken) ContextUtils
                .get(Context.USER_ROLE_TOKEN);
        Employment eSelf = (Employment) ur.getProperty("employment");
        List<Doctor> docList = new ArrayList<Doctor>();
        UnitOpauthorizeDAO dao = DAOFactory.getDAO(UnitOpauthorizeDAO.class);
        List<Integer> oList = dao.findByBussId(bussType);
        if (oList == null) {
            oList = new ArrayList<Integer>();

        }
        if (eSelf == null || eSelf.getOrganId() == null) {
            return docList;
        }
        oList.add(eSelf.getOrganId());
        StringBuilder sb = new StringBuilder(" and(");
        for (Integer o : oList) {
            sb.append(" e.organId=").append(o).append(" OR");
        }
        final String strUO = sb.substring(0, sb.length() - 2) + ")";

        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "select distinct d from Doctor d,ConsultSet c,Employment e "
                                + "where d.doctorId=c.doctorId and d.status=1 and e.doctorId=d.doctorId");
                hql.append(strUO);
                switch (bussType) {
                    case 1:
                        hql.append(" and c.transferStatus=1");
                        break;
                    case 2:
                        hql.append(" and c.meetClinicStatus=1");
                        break;
                    case 3:
                        hql.append(" and (c.onLineStatus=1 or c.appointStatus=1)");
                        break;
                }
                if (!StringUtils.isEmpty(profession)) {
                    hql.append(" and d.profession like :profession");
                }
                if (!StringUtils.isEmpty(domain)) {
                    hql.append(" and d.domain like:domain");
                }
                if (!StringUtils.isEmpty(name)) {
                    hql.append(" and d.name like:name");
                }

                Query q = ss.createQuery(hql.toString());
                if (!StringUtils.isEmpty(profession)) {
                    q.setParameter("profession", profession + "%");
                }
                if (!StringUtils.isEmpty(domain)) {
                    q.setParameter("domain", "%" + domain + "%");
                }
                if (!StringUtils.isEmpty(name)) {
                    q.setParameter("name", "%" + name + "%");
                }
                q.setMaxResults(10);
                q.setFirstResult(startPage);
                List<Doctor> list = q.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        docList = action.getResult();
        if ( docList == null || docList.size() == 0 ) {
            return new ArrayList<Doctor>();
        }

        List<Doctor> targets = new ArrayList<Doctor>();
        EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
        for (Doctor doctor : docList) {
            Employment employment = employmentDAO
                    .getPrimaryEmpByDoctorId(doctor.getDoctorId());
            if (employment != null) {
                doctor.setDepartment(employment.getDepartment());
            }
            targets.add(doctor);
        }

        return targets;
    }

    /**
     * 医生信息添加服务
     *
     * @param d
     * @author hyj
     */
    @RpcService
    public Doctor addDoctor(final Doctor d) {
        if (StringUtils.isEmpty(d.getName())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "name is required");
        }
        if (StringUtils.isEmpty(d.getGender())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "gender is required");
        }
        if (d.getUserType() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "userType is required");
        }
        if (d.getBirthDay() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "birthDay is required");
        }
        if (StringUtils.isEmpty(d.getIdNumber())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "idNumber is required");
        }
        if (StringUtils.isEmpty(d.getProfession())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "profession is required");
        }
        if (StringUtils.isEmpty(d.getMobile())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "mobile is required");
        }
        if (d.getOrgan() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "organId is required");
        }

        Doctor doctor = this.getByMobile(d.getMobile());
        if (doctor != null) {
            throw new DAOException(609, "该医生已存在，请勿重复添加");
        }

        HibernateStatelessResultAction<Doctor> action = new AbstractHibernateStatelessResultAction<Doctor>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                d.setCreateDt(new Date());
                d.setLastModify(new Date());
                d.setHaveAppoint(0);
                d.setStatus(1);
                d.setSource(0);// 0:后台导入，1：注册
                d.setRewardFlag(false);
                d.setProTitle(StringUtils.isEmpty(d.getProTitle()) ? "99" : d.getProTitle());
                if (d.getVirtualDoctor() == null) {
                    d.setVirtualDoctor(false);
                }

                Doctor target = save(d);

                if (target != null && target.getDoctorId() != null) {
                    ConsultSet set = new ConsultSet();
                    set.setDoctorId(target.getDoctorId());
                    set.setOnLineStatus(0);
                    set.setAppointStatus(0);
                    set.setTransferStatus(0);
                    set.setMeetClinicStatus(0);
                    set.setPatientTransferStatus(0);
                    ConsultSetDAO setDao = DAOFactory
                            .getDAO(ConsultSetDAO.class);
                    setDao.save(set);
                }

                setResult(target);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
        Integer key = d.getDoctorId();
        DictionaryItem item = getDictionaryItem(key);
        //this.fireEvent(new CreateDAOEvent(key,item));//此方法只通知主服务器 config缓存服务器没有通知到
        NotifierMessage msg = new NotifierMessage(NotifierCommands.ITEM_CREATE, "eh.base.dictionary.Doctor");
        msg.setLastModify(System.currentTimeMillis());
        msg.addUpdatedItems(item);
        try {
            DictionaryController.instance().getUpdater().notifyMessage(msg);
        } catch (ControllerException e) {
            log.error("addDoctor() error: "+e);
        }

        return action.getResult();
    }

    /**
     * 获取指定时间内登录过的用户数
     *
     * @param startDate
     * @param endDate
     * @return
     * @author ZX
     * @date 2015-5-21 下午6:08:54
     */
    @DAOMethod(sql = "SELECT COUNT(*) FROM UserRoleTokenEntity "
            + "where manageUnit like :manageUnit and roleId='doctor' "
            + "and date(lastLoginTime) >=date(:startDate) "
            + "and date(lastLoginTime) <=date(:endDate) ")
    public abstract Long getActiveNum(
            @DAOParam("manageUnit") String manageUnit,
            @DAOParam("startDate") Date startDate,
            @DAOParam("endDate") Date endDate);

    /**
     * 获取活跃用户数
     *
     * @return
     * @author ZX
     * @date 2015-5-21 下午6:01:44
     */
    @RpcService
    public Long getActiveDocNum(String manageUnit) {
        Date startDate = DateConversion.getDaysAgo(2);
        Date endDate = new Date();
        return getActiveNum(manageUnit + "%", startDate, endDate);

    }

    /**
     * 医生开户服务
     *
     * @param doctorId
     * @param password
     * @throws DAOException
     * @author hyj
     */
    @RpcService
    public void createDoctorUser(final Integer doctorId, final String password)
            throws DAOException {
        if (doctorId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "doctorId is required");
        }
        if (StringUtils.isEmpty(password)) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "password is required");
        }

        HibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {

                UserDAO userDao = DAOFactory.getDAO(UserDAO.class);
                UserRoleTokenDAO tokenDao = DAOFactory
                        .getDAO(UserRoleTokenDAO.class);

                DoctorDAO dao = DAOFactory.getDAO(DoctorDAO.class);
                Doctor d = dao.getByDoctorId(doctorId);

                OrganDAO organdao = DAOFactory.getDAO(OrganDAO.class);
                Organ o = organdao.getByOrganId(d.getOrgan());

                User u = new User();
                u.setId(d.getMobile());
                u.setEmail(d.getEmail());
                u.setName(d.getName());
                u.setPlainPassword(password);
                u.setCreateDt(new Date());
                u.setStatus("1");

                UserRoleTokenEntity urt = new UserRoleTokenEntity();
                urt.setUserId(d.getMobile());
                urt.setRoleId("doctor");
                urt.setTenantId("eh");
                urt.setManageUnit(o.getManageUnit());

                // user表中不存在记录
                if (!userDao.exist(d.getMobile())) {

                    // 创建角色(user，userrole两张表插入数据)
                    userDao.createUser(u, urt);

                    setResult(true);
                } else {
                    // user表中存在记录,角色表中不存在记录
                    Object object = tokenDao.getExist(d.getMobile(),
                            o.getManageUnit(), "doctor");
                    if (object == null) {
                        ConfigurableItemUpdater<User, UserRoleToken> up = (ConfigurableItemUpdater<User, UserRoleToken>) UserController
                                .instance().getUpdater();

                        ((UserRoleTokenEntity) urt).setProperty("doctor", d);

                        /**
                         * 2015-09-02 选取医生的第一职业
                         */
                        Employment em = DAOFactory.getDAO(EmploymentDAO.class)
                                .getPrimaryEmpByDoctorId(d.getDoctorId());
                        ((UserRoleTokenEntity) urt).setProperty("employment",
                                em);
                        up.createItem(d.getMobile(), urt);

                        setResult(true);
                    } else {
                        // user表中存在记录,角色表中存在记录
                        throw new DAOException(609, "该用户已存在，请勿重复开户");
                    }
                }

            }

        };
        HibernateSessionTemplate.instance().executeTrans(action);
        if (action.getResult()) {
            // 发送开户短信
            SmsInfo info = new SmsInfo();
            info.setBusId(doctorId);
            info.setBusType("CreateDocUser");
            info.setSmsType("CreateDocUser");
            info.setStatus(0);
            info.setOrganId(0);
            /*AliSmsSendExecutor exe = new AliSmsSendExecutor(info);
            exe.execute();*/
            SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
            smsPushService.pushMsgData2OnsExtendValue(info);
        }
    }

    /**
     * 获取医师开户服务
     *
     * @param doctorId
     * @return
     * @author hyj
     */
    @RpcService
    public boolean checkDoctorUser(Integer doctorId) {
        UserDAO userDao = DAOFactory.getDAO(UserDAO.class);
        UserRoleTokenDAO tokenDao = DAOFactory.getDAO(UserRoleTokenDAO.class);

        Doctor d = this.getByDoctorId(doctorId);

        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        Organ o = organDAO.getByOrganId(d.getOrgan());

        if (!userDao.exist(d.getMobile())) {
            return false;
        } else {
            // user表中存在记录,角色表中不存在记录
            Object object = tokenDao.getExist(d.getMobile(), o.getManageUnit(),
                    "doctor");
            if (object == null) {
                return false;
            } else {
                return true;
            }
        }
    }

    /**
     * 查询医生信息和医生执业信息服务
     *
     * @param name
     * @param idNumber
     * @param organ
     * @param startPage
     * @return docList
     * @author zsq
     */
    @RpcService
    @SuppressWarnings({"unchecked"})
    public List<Doctor> queryDoctorAndEmployment(final String name,
                                                 final String idNumber, final Integer organ,
                                                 final String profession, final Integer department,
                                                 final Integer startPage) {

        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "select DISTINCT a from Doctor a,Employment b "
                                + "where a.doctorId=b.doctorId ");
                if (!StringUtils.isEmpty(name)) {
                    hql.append(" and a.name like :name");
                }
                if (!StringUtils.isEmpty(idNumber)) {
                    hql.append(" and a.idNumber like :idNumber");
                }
                if (organ != null) {
                    hql.append(" and b.organId=:organ");
                }
                if (!StringUtils.isEmpty(profession)) {
                    hql.append(" and a.profession like :profession");
                }
                if (department != null) {
                    hql.append(" and b.department=:department");
                }

                Query q = ss.createQuery(hql.toString());
                if (!StringUtils.isEmpty(name)) {
                    q.setString("name", "%" + name + "%");
                }
                if (!StringUtils.isEmpty(idNumber)) {
                    q.setParameter("idNumber", "%" + idNumber + "%");
                }
                if (organ != null) {
                    q.setParameter("organ", organ);
                }
                if (!StringUtils.isEmpty(profession)) {
                    q.setParameter("profession", profession + "%");
                }
                if (department != null) {
                    q.setParameter("department", department);
                }
                if (startPage != null) {
                    q.setMaxResults(10);
                    q.setFirstResult(startPage);
                }
                List<Doctor> doctors = q.list();

                setResult(doctors);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<Doctor> docs = action.getResult();
        if (docs == null || docs.size() <= 0) {
            return new ArrayList<Doctor>();
        }

        EmploymentDAO dao = DAOFactory.getDAO(EmploymentDAO.class);
        for (int i = 0; i < docs.size(); i++) {
            Doctor d = docs.get(i);
            List<Employment> es = dao.findByDoctorIdAndOrganId(d.getDoctorId(),
                    d.getOrgan());
            if (es != null && es.size() > 0) {
                docs.get(i).setEmployments(es);
            }
        }

        return docs;
    }

    /**
     * 更新医生点评分数
     *
     * @param doctorId
     * @param rating
     * @param serviceLevel
     * @param skillLevel
     * @param ratingOld
     * @param serviceLevelOld
     * @param skillLevelOld
     * @author hyj
     */
    @RpcService
    public void updateRating(final int doctorId, final Double rating,
                             final Double serviceLevel, final Double skillLevel,
                             final Double ratingOld, final Double serviceLevelOld,
                             final Double skillLevelOld) {
        HibernateSessionTemplate.instance().execute(
                new HibernateStatelessAction() {
                    public void execute(StatelessSession ss) throws Exception {
                        String hql = "update Doctor set "
                                + "rating=:rating,serviceLevel=:serviceLevel,skillLevel=:skillLevel "
                                + "where doctorId=:doctorId and rating=:ratingOld "
                                + "and serviceLevel=:serviceLevelOld and skillLevel=:skillLevelOld";

                        Query q = ss.createQuery(hql);
                        q.setDouble("rating", rating);
                        q.setDouble("serviceLevel", serviceLevel);
                        q.setDouble("skillLevel", skillLevel);
                        q.setDouble("ratingOld", ratingOld);
                        q.setDouble("serviceLevelOld", serviceLevelOld);
                        q.setDouble("skillLevelOld", skillLevelOld);
                        q.setInteger("doctorId", doctorId);

                        int num = q.executeUpdate();
                        if (num == 0) {
                            throw new DAOException(609, "点评失败");
                        }
                    }
                });
    }

    /**
     * 更新医生点评分数
     *
     * @param doctorId
     * @param rating
     * @param evaNum
     * @author zhangsl
     */
    public Integer updateRating(final int doctorId, final Double rating,
                                final Integer evaNum, final Double oldRating,
                                final Integer oldEvaNum) {
        final HibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder("update Doctor set rating=:rating,evaNum=:evaNum " +
                        "where doctorId=:doctorId");
                if (oldRating != null) {
                    hql.append(" and rating=:oldRating");
                }
                if (oldEvaNum != null) {
                    hql.append(" and evaNum=:oldEvaNum");
                }
                Query q = ss.createQuery(hql.toString());
                q.setParameter("doctorId", doctorId);
                q.setParameter("rating", rating);
                q.setParameter("evaNum", evaNum);
                if (oldRating != null) {
                    q.setParameter("oldRating", oldRating);
                }
                if (oldEvaNum != null) {
                    q.setParameter("oldEvaNum", oldEvaNum);
                }
                int num = q.executeUpdate();
                if (num == 0) {
                    throw new DAOException(ErrorCode.SERVICE_ERROR, "更新医生[" + doctorId + "]评分失败");
                }
                setResult(num);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    /**
     * 根据医生id和teams查询医生信息
     *
     * @param doctorId
     * @return
     * @author hyj
     */
    @RpcService
    @DAOMethod(sql = " From Doctor where doctorId =:doctorId and teams=1")
    public abstract Doctor getByDoctorIdAndTeams(
            @DAOParam("doctorId") Integer doctorId);

    /**
     * 团队医生信息添加服务
     *
     * @param d
     * @author hyj
     */
    @RpcService
    public Doctor addGroupDoctor(Doctor d) {
        if (StringUtils.isEmpty(d.getName())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "name is required");
        }
        if (d.getUserType() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "userType is required");
        }
        if (StringUtils.isEmpty(d.getProfession())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "profession is required");
        }
        if (d.getOrgan() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "organId is required");
        }

        d.setCreateDt(new Date());
        d.setLastModify(new Date());
        d.setHaveAppoint(0);
        d.setStatus(1);
        d.setTeams(true);
        d.setRewardFlag(false);
        d.setProTitle(StringUtils.isEmpty(d.getProTitle()) ? "99" : d.getProTitle());
        if (d.getSource() == null) {
            d.setSource(0);// 0:后台导入，1：注册
        }
        if (d.getVirtualDoctor() == null) {
            d.setVirtualDoctor(false);
        }
        Doctor dbDoc = save(d);
        Integer key = dbDoc.getDoctorId();
        DictionaryItem item = getDictionaryItem(key);
        //this.fireEvent(new CreateDAOEvent(key,item));
        NotifierMessage msg = new NotifierMessage(NotifierCommands.ITEM_CREATE, "eh.base.dictionary.Doctor");
        msg.setLastModify(System.currentTimeMillis());
        msg.addUpdatedItems(item);
        try {
            DictionaryController.instance().getUpdater().notifyMessage(msg);
        } catch (ControllerException e) {
            log.error("addGroupDoctor() error : "+e);
        }
        return dbDoc;
    }

    /**
     * 更新医生点赞总数
     *
     * @param doctorId
     * @param goodRating
     * @author hyj
     */
    @DAOMethod(sql = "update Doctor set goodRating=:goodRating where doctorId=:doctorId")
    public abstract void updateGoodRating(@DAOParam("doctorId") int doctorId,
                                          @DAOParam("goodRating") int goodRating);

    /**
     * 更新医生点赞总数
     *
     * @param doctorId
     * @param goodRating
     * @param goodRatingOld
     * @author hyj
     */
    @RpcService
    public void updateGoodRating(final int doctorId, final int goodRating,
                                 final int goodRatingOld) {
        HibernateSessionTemplate.instance().execute(
                new HibernateStatelessAction() {
                    public void execute(StatelessSession ss) throws Exception {
                        String hql = "update Doctor set goodRating=:goodRating "
                                + "where doctorId=:doctorId and goodRating=:goodRatingOld";

                        Query q = ss.createQuery(hql);
                        q.setDouble("goodRating", goodRating);
                        q.setDouble("goodRatingOld", goodRatingOld);
                        q.setInteger("doctorId", doctorId);

                        int num = q.executeUpdate();
                        if (num == 0) {
                            throw new DAOException(609, "点赞失败");
                        }
                    }
                });
    }

    /**
     * 批量更新医生haveAppoint字段
     *
     * @param haveAppoint
     * @param doctorIds
     * @author hyj
     */
    @DAOMethod(sql = "update Doctor set haveAppoint=:haveAppoint where doctorId in :doctorId")
    public abstract void updateHaveAppoint(
            @DAOParam("haveAppoint") int haveAppoint,
            @DAOParam("doctorId") List<Integer> doctorIds);

    /**
     * 智能推荐医生列表服务
     *
     * @param homeArea      --病人所在区域
     * @param age           --病人年龄
     * @param patientGender --病人性别1:男 2:女
     * @return 医生信息列表
     * @author Qichengjian
     */
    @RpcService
    public List<Doctor> intelligentreDoctors(String homeArea, Integer age,
                                             String patientGender) {
        if (homeArea == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "homeArea is required");
        }
        if (age == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "age is required");
        }
        if (patientGender == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "patientGender is required");
        }
        List<Doctor> list = new ArrayList<Doctor>();

        if (age < 20) {
            List<Doctor> list1 = queryByAddrAreaAndProfessionAndAge(homeArea,
                    "07", 2);
            List<Doctor> list2 = queryByAddrAreaAndProfessionAndAge(homeArea,
                    "02", 2);
            // 牙科的
            List<Doctor> list3 = queryByAddrAreaAndProfessionAndAge(homeArea,
                    "12", 2);
            List<Doctor> list4 = queryByAddrAreaAndProfessionAndAge(homeArea,
                    "10", 2);
            list.addAll(list1);
            list.addAll(list2);
            list.addAll(list3);
            list.addAll(list4);

            return list;
        } else if (age < 40 && age >= 20) {
            List<Doctor> list1 = null;
            List<Doctor> list2 = null;
            if (patientGender.equals("2")) {
                list1 = queryByAddrAreaAndProfessionAndAge(homeArea, "0501", 2);
                list2 = queryByAddrAreaAndProfessionAndAge(homeArea, "0502", 2);
            } else {
                list1 = queryByAddrAreaAndProfessionAndAge(homeArea, "03", 2);
                list2 = queryByAddrAreaAndProfessionAndAge(homeArea, "04", 2);
            }
            List<Doctor> list3 = queryByAddrAreaAndProfessionAndAge(homeArea,
                    "07", 2);
            List<Doctor> list4 = queryByAddrAreaAndProfessionAndAge(homeArea,
                    "02", 2);
            list.addAll(list1);
            list.addAll(list2);
            list.addAll(list3);
            list.addAll(list4);

            return list;
        } else if (age < 60 && age >= 40) {
            List<Doctor> list1 = null;
            if (patientGender.equals("2")) {
                list1 = queryByAddrAreaAndProfessionAndAge(homeArea, "0501", 2);
            } else {
                list1 = queryByAddrAreaAndProfessionAndAge(homeArea, "19", 2);
            }
            List<Doctor> list2 = queryByAddrAreaAndProfessionAndAge(homeArea,
                    "03", 2);
            List<Doctor> list3 = queryByAddrAreaAndProfessionAndAge(homeArea,
                    "04", 2);
            List<Doctor> list4 = queryByAddrAreaAndProfessionAndAge(homeArea,
                    "02", 2);
            list.addAll(list1);
            list.addAll(list2);
            list.addAll(list3);
            list.addAll(list4);

            return list;
        } else {
            List<Doctor> list1 = queryByAddrAreaAndProfessionAndAge(homeArea,
                    "02", 2);
            List<Doctor> list2 = queryByAddrAreaAndProfessionAndAge(homeArea,
                    "19", 2);
            List<Doctor> list3 = queryByAddrAreaAndProfessionAndAge(homeArea,
                    "03", 2);
            List<Doctor> list4 = queryByAddrAreaAndProfessionAndAge(homeArea,
                    "04", 2);
            list.addAll(list1);
            list.addAll(list2);
            list.addAll(list3);
            list.addAll(list4);

            return list;
        }
    }

    /**
     * 智能推荐医生列表服务2
     *
     * @param homeArea      --病人所在区域
     * @param age           --病人年龄
     * @param patientGender --病人性别1:男 2:女
     * @param bussType      1:转诊；2：会诊；3：咨询; 4:预约
     * @return 医生信息列表
     * @author Qichengjian
     */
    @RpcService
    public List<Doctor> intelligentreDoctorsNew(String homeArea, Integer age,
                                                String patientGender, Integer bussType) {
        if (homeArea == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "homeArea is required");
        }
        if (age == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "age is required");
        }
        if (patientGender == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "patientGender is required");
        }
        if (bussType == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "buesType is required");
        }
        List<Doctor> list = new ArrayList<Doctor>();

        if (age < 20) {
            List<Doctor> list1 = queryByAreaAndProfessionAndBussType(homeArea,
                    "07", 2, bussType);
            List<Doctor> list2 = queryByAreaAndProfessionAndBussType(homeArea,
                    "02", 2, bussType);
            // 牙科的
            List<Doctor> list3 = queryByAreaAndProfessionAndBussType(homeArea,
                    "12", 2, bussType);
            List<Doctor> list4 = queryByAreaAndProfessionAndBussType(homeArea,
                    "10", 2, bussType);
            list.addAll(list1);
            list.addAll(list2);
            list.addAll(list3);
            list.addAll(list4);
        } else if (age < 40 && age >= 20) {
            List<Doctor> list1 = null;
            List<Doctor> list2 = null;
            if (patientGender.equals("2")) {
                list1 = queryByAreaAndProfessionAndBussType(homeArea, "0501",
                        2, bussType);
                list2 = queryByAreaAndProfessionAndBussType(homeArea, "0502",
                        2, bussType);
            } else {
                list1 = queryByAreaAndProfessionAndBussType(homeArea, "03", 2,
                        bussType);
                list2 = queryByAreaAndProfessionAndBussType(homeArea, "04", 2,
                        bussType);
            }
            List<Doctor> list3 = queryByAreaAndProfessionAndBussType(homeArea,
                    "07", 2, bussType);
            List<Doctor> list4 = queryByAreaAndProfessionAndBussType(homeArea,
                    "02", 2, bussType);
            list.addAll(list1);
            list.addAll(list2);
            list.addAll(list3);
            list.addAll(list4);
        } else if (age < 60 && age >= 40) {
            List<Doctor> list1 = null;
            if (patientGender.equals("2")) {
                list1 = queryByAreaAndProfessionAndBussType(homeArea, "0501",
                        2, bussType);
            } else {
                list1 = queryByAreaAndProfessionAndBussType(homeArea, "19", 2,
                        bussType);
            }
            List<Doctor> list2 = queryByAreaAndProfessionAndBussType(homeArea,
                    "03", 2, bussType);
            List<Doctor> list3 = queryByAreaAndProfessionAndBussType(homeArea,
                    "04", 2, bussType);
            List<Doctor> list4 = queryByAreaAndProfessionAndBussType(homeArea,
                    "02", 2, bussType);
            list.addAll(list1);
            list.addAll(list2);
            list.addAll(list3);
            list.addAll(list4);
        } else {
            List<Doctor> list1 = queryByAreaAndProfessionAndBussType(homeArea,
                    "02", 2, bussType);
            List<Doctor> list2 = queryByAreaAndProfessionAndBussType(homeArea,
                    "19", 2, bussType);
            List<Doctor> list3 = queryByAreaAndProfessionAndBussType(homeArea,
                    "03", 2, bussType);
            List<Doctor> list4 = queryByAreaAndProfessionAndBussType(homeArea,
                    "04", 2, bussType);
            list.addAll(list1);
            list.addAll(list2);
            list.addAll(list3);
            list.addAll(list4);
        }

        return list;
    }

    /**
     * 未登录智能推荐医生列表服务
     *
     * @param homeArea 病人所在区域
     * @return 医生信息列表
     * @author Qichengjian
     */
    @RpcService
    public List<Doctor> intelligentreDoctorsForUnLogin(String homeArea) {
        if (homeArea == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "homeArea is required");
        }

        List<Doctor> list = new ArrayList<Doctor>();
        List<Doctor> list1 = queryByAddrAreaAndProfessionAndAge(homeArea, "02",
                4);
        List<Doctor> list2 = queryByAddrAreaAndProfessionAndAge(homeArea, "03",
                2);
        List<Doctor> list3 = queryByAddrAreaAndProfessionAndAge(homeArea, "04",
                2);
        list.addAll(list1);
        list.addAll(list2);
        list.addAll(list3);

        return list;
    }

    /**
     * 根据区域,性别,专科查询医生列表 并按点赞数高低返回固定条记录
     *
     * @param area       --区域
     * @param profession --专科
     * @param num        --返回记录条数
     * @return 医生列表
     * @author Qichengjian
     * @Date 2016年11月14日 15:32:58 zhangsl 修改按评分高低排序
     */
    public List<Doctor> queryByAddrAreaAndProfessionAndAge(final String area,
                                                           final String profession, final Integer num) throws DAOException {
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            List<Doctor> list = new ArrayList<Doctor>();

            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = new String(
                        "select d from Doctor d ,Organ o where d.organ=o.organId "
                                + "and o.addrArea like :area and d.profession like :profession "
                                + "and d.status=1 order by d.rating desc");

                Query q = ss.createQuery(hql);
                q.setParameter("area", area);
                q.setParameter("profession", profession + "%");
                q.setMaxResults(num);
                q.setFirstResult(0);

                list = q.list();
                if (list.size() <= 2) {
                    // 如果返回医生数=0，则取区域入参的上一级从新查一下(330101杭州西湖区)
                    String area1 = area.substring(0, 4);
                    q.setParameter("area", area1 + "%");
                    list = q.list();
                }
                if (list.size() <= 2) {
                    // 如果返回医生数=0，则取区域入参的上上一级从新查一下
                    String area2 = area.substring(0, 2);
                    q.setParameter("area", area2 + "%");
                    list = q.list();
                }
                if (list.size() <= 2) {
                    // 如果返回医生数=0，则取区域入参的上上一级从新查一下
                    q.setParameter("area", "%");
                    list = q.list();
                }

                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 筛选出医生设置打开的医生列表并给department赋值
     *
     * @param area       属地区域
     * @param profession 专科编码
     * @param num        取的条数
     * @param bussType   1:转诊；2：会诊；3：预约咨询或在线咨询 ；4：预约（默认都有这个权限）
     * @return List<Doctor>
     * <p>
     * 2015-10-23 zhangx 授权机构由于前段排序显示问题，对原来的设计进行修改</br>
     * 修改前：A授权给B，B能看到A，A也能看到B;修改后：A授权给B，B能看到A，A不能看到B</br>
     * 增加按orderNum排序</br>
     * @Date 2016年11月14日 15:32:58 zhangsl 修改按评分高低排序
     * @author qichengjian
     */
    public List<Doctor> queryByAreaAndProfessionAndBussType(final String area,
                                                            final String profession, final Integer num, final Integer bussType) {
        if (area == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "区域不能为空");
        }
        if (profession == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "专科编码不能为空");
        }
        if (bussType == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "业务类型不能为空");
        }
        UserRoleToken ur = (UserRoleToken) ContextUtils
                .get(Context.USER_ROLE_TOKEN);
        Employment eSelf = (Employment) ur.getProperty("employment");
        List<Doctor> docList = new ArrayList<Doctor>();
        UnitOpauthorizeDAO dao = DAOFactory.getDAO(UnitOpauthorizeDAO.class);
        List<Integer> oList = dao.findByBussId(bussType);
        if (oList == null) {
            oList = new ArrayList<Integer>();
        }
        if (eSelf == null || eSelf.getOrganId() == null) {
            return docList;
        }
        oList.add(eSelf.getOrganId());

        StringBuilder sb = new StringBuilder(" and(");
        for (Integer o : oList) {
            sb.append(" e.organId=").append(o).append(" OR");
        }
        final String strUO = sb.substring(0, sb.length() - 2) + ")";

        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            List<Doctor> list = new ArrayList<Doctor>();

            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "select distinct d from Doctor d ,Organ o,ConsultSet c,Employment e "
                                + "where d.organ=o.organId and o.addrArea like :area and "
                                + "d.profession like :profession and d.doctorId=c.doctorId "
                                + "and d.status=1 and d.doctorId=e.doctorId");
                hql.append(strUO);
                // 转诊
                if (bussType == 1) {
                    hql.append(" and c.transferStatus=1");
                }
                // 会诊
                if (bussType == 2) {
                    hql.append(" and c.meetClinicStatus=1");
                }
                // 预约咨询或在线咨询
                if (bussType == 3) {
                    hql.append(" and (c.appointStatus=1 or c.onLineStatus=1)");
                }
                hql.append(" order by d.rating desc");

                Query q = ss.createQuery(hql.toString());
                q.setParameter("area", area);
                q.setParameter("profession", profession + "%");
                q.setMaxResults(num);
                q.setFirstResult(0);

                list = q.list();
                if (list.size() <= 2) {
                    // 如果返回医生数=0，则取区域入参的上一级从新查一下(330101杭州西湖区)
                    String area1 = area.substring(0, 4);
                    q.setParameter("area", area1 + "%");
                    list = q.list();
                }
                if (list.size() <= 2) {
                    // 如果返回医生数=0，则取区域入参的上上一级从新查一下
                    String area2 = area.substring(0, 2);
                    q.setParameter("area", area2 + "%");
                    list = q.list();
                }
                if (list.size() <= 2) {
                    // 如果返回医生数=0，则取区域入参的上上一级从新查一下
                    q.setParameter("area", "%");
                    list = q.list();
                }

                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        docList = action.getResult();
        if ( docList == null || docList.size() == 0 ) {
            return new ArrayList<Doctor>();
        }

        List<Doctor> targets = new ArrayList<Doctor>();
        EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
        for (Doctor doctor : docList) {
            Employment employment = employmentDAO
                    .getPrimaryEmpByDoctorId(doctor.getDoctorId());
            if (employment != null) {
                doctor.setDepartment(employment.getDepartment());
            }
            targets.add(doctor);
        }

        return targets;
    }

    /**
     * 团队医生信息更新服务
     *
     * @param doctor
     * @author hyj
     */
    @RpcService
    public void updateGroupDoctor(Doctor doctor) {
        if (doctor.getDoctorId() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "doctorId is required");
        }
        doctor.setLastModify(new Date());
        Doctor target = getByDoctorId(doctor.getDoctorId());
        BeanUtils.map(doctor, target);
        target.setProTitle(StringUtils.isEmpty(target.getProTitle()) ? "99" : target.getProTitle());
        update(target);

        Integer key = target.getDoctorId();
        DictionaryItem item = getDictionaryItem(key);
        //this.fireEvent(new UpdateDAOEvent(key,item));
        NotifierMessage msg = new NotifierMessage(NotifierCommands.ITEM_UPDATE, "eh.base.dictionary.Doctor");
        msg.setLastModify(System.currentTimeMillis());
        msg.addUpdatedItems(item);
        try {
            DictionaryController.instance().getUpdater().notifyMessage(msg);
        } catch (ControllerException e) {
            log.error("updateGroupDoctor() error : "+e);
        }
    }

    /**
     * 医生受关注度
     *
     * @param doctorId
     * @return
     * @author LF
     * <p>
     * 增加患者签约有效性判断 zhangsl
     * @Date 2016-11-16 13:41:26
     */
    @RpcService
    public Integer  doctorRelationNumber(final Integer doctorId) {
        HibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws Exception {
                String hql = "select relationDoctorId from RelationDoctor where doctorId=:doctorId and " +
                        "((relationType=0 and current_timestamp()>=startDate and current_timestamp()<=endDate) or relationType=1) " +
                        "group by mpiId";

                Query q = ss.createQuery(hql);
                q.setParameter("doctorId", doctorId);
                List<Integer> list = q.list();
                Integer num = 0;
                num += list.size();

                String hql2 = "SELECT doctorRelationId FROM DoctorRelationDoctor "
                        + "WHERE relationDoctorId=:relationDoctorId GROUP BY doctorId";

                Query query = ss.createQuery(hql2);
                query.setParameter("relationDoctorId", doctorId);
                List<Integer> list2 = query.list();
                num += list2.size();

                setResult(num);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 根据医生ID将关注数量点赞数和团队信息返回
     */

    @RpcService
    public Map<String, Object> getLoginUserInfo(Integer doctorId) {
        if (!this.exist(doctorId)) {
            throw new DAOException(609, "不存在该医生");
        }
        PatientFeedbackDAO pfDao = DAOFactory.getDAO(PatientFeedbackDAO.class);
        DoctorGroupDAO dgDao = DAOFactory.getDAO(DoctorGroupDAO.class);
        Map<String, Object> map = new HashMap<String, Object>();
        Integer RelationNumber = doctorRelationNumber(doctorId);
        Long PatientFeedbackNum = pfDao.getPatientFeedbackNum(doctorId);
        // List<DoctorGroup> groups = dgDao.findByMemberId(doctorId);
        List<DoctorGroupAndDoctor> groups = dgDao
                .getDoctorGroupAndDoctorByMemberId(doctorId, 0);
        map.put("RelationNumber", RelationNumber);
        map.put("PatientFeedbackNum", PatientFeedbackNum);
        map.put("groups", groups);
        return map;
    }

    /**
     * 获取医生信息
     *
     * @param doctorId         要获取信息的医生ID
     * @param relationDoctorId
     * @return
     * @author ZX
     * @date 2015-8-10 下午5:29:06
     */
    @RpcService
    public Doctor getDoctorInfo(Integer doctorId, Integer relationDoctorId) {
        Doctor doctor = getByDoctorId(doctorId);
        // 获取关注度
        Integer relationNum = doctorRelationNumber(doctorId);
        doctor.setRelationNum(relationNum);
        // 判断是否关注
        DoctorRelationDoctorDAO relationDao = DAOFactory
                .getDAO(DoctorRelationDoctorDAO.class);
        Boolean isRelation = relationDao.getRelationDoctorFlag(
                relationDoctorId, doctorId);
        doctor.setIsRelation(isRelation);

        return doctor;
    }

    /**
     * 学历字典
     *
     * @return
     * @author ZX
     * @date 2015-8-7 上午10:47:10
     */
    @RpcService
    public List<DictionaryItem> getEducation() {
        DictionaryLocalService ser = AppContextHolder.getBean("dictionaryService", DictionaryLocalService.class);
        List<DictionaryItem> list = new ArrayList<DictionaryItem>();
        try {
            DictionarySliceRecordSet var = ser.getSlice(
                    "eh.base.dictionary.Education", "", 0, "", 0, 0);
            list = var.getItems();

        } catch (ControllerException e) {
            log.error("getEducation() error: "+e.getMessage());
        }
        return list;
    }

    /**
     * 职称字典
     *
     * @return
     * @author LF
     */
    @RpcService
    public List<DictionaryItem> getProTitle() {
        DictionaryLocalService ser = AppContextHolder.getBean("dictionaryService", DictionaryLocalService.class);
        List<DictionaryItem> list = new ArrayList<DictionaryItem>();
        try {
            DictionarySliceRecordSet var = ser.getSlice(
                    "eh.base.dictionary.ProTitle", "", 0, "", 0, 0);
            list = var.getItems();

        } catch (ControllerException e) {
            log.error("getProTitle() error: "+e);
        }
        return list;
    }

    /**
     * 模糊查询医生--运营
     *
     * @param name     医生姓名 可空
     * @param mobile   手机号码 可空
     * @param idNumber 身份证号 可空
     * @return List<Doctor>
     * @author luf
     */
    @RpcService
    public List<Doctor> findByNameOrMobOrIdN(final String name,
                                             final String mobile, final String idNumber) {
        List<Doctor> docs;
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws Exception {
                StringBuffer hql = new StringBuffer("From Doctor where 1=1");
                if (!StringUtils.isEmpty(name)) {
                    hql.append(" and name like :name");
                }
                if (!StringUtils.isEmpty(mobile)) {
                    hql.append(" and mobile like :mobile");
                }
                if (!StringUtils.isEmpty(idNumber)) {
                    hql.append(" and idNumber like :idNumber");
                }

                Query q = ss.createQuery(hql.toString());
                if (!StringUtils.isEmpty(name)) {
                    q.setParameter("name", "%" + name + "%");
                }
                if (!StringUtils.isEmpty(mobile)) {
                    q.setParameter("mobile", "%" + mobile + "%");
                }
                if (!StringUtils.isEmpty(idNumber)) {
                    q.setParameter("idNumber", "%" + idNumber + "%");
                }

                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        docs = action.getResult();
        if (docs == null || docs.size() <= 0) {
            return new ArrayList<Doctor>();
        }

        return docs;
    }

    /**
     * Title:根据机构id获取其所有科室，该接口为桐乡医院号源导入 时调用
     *
     * @param organId
     * @return List<TxDoctor>
     * @author zhangjr
     * @date 2015-10-10
     */
    @RpcService
    public List<TxDoctor> findTxDoctorByOrganId(final Integer organId) {
        if (organId == null) {
            new DAOException(DAOException.VALUE_NEEDED, "organId is required!");
        }
        HibernateStatelessResultAction<List<TxDoctor>> action = new AbstractHibernateStatelessResultAction<List<TxDoctor>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                // TODO Auto-generated method stub
                String hql = "select new eh.entity.tx.TxDoctor(d.doctorId,d.name,e.jobNumber,m.deptId,"
                        + "m.code,m.name) from Doctor d,Employment e,Department m where d.doctorId = e.doctorId "
                        + " and e.department = m.deptId and m.organId = :organId ";
                Query query = ss.createQuery(hql);
                query.setParameter("organId", organId);
                List<TxDoctor> objArrList = query.list();
                setResult(objArrList);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * Title:医生基本信息查询
     *
     * @param organId
     * @return List<BaseDoctor>
     * @author zhangjr
     * @date 2015-10-20
     */
    @RpcService
    public List<BaseDoctor> findBaseDoctorByOrganIdNew(final Integer organId) {
        if (organId == null) {
            new DAOException(DAOException.VALUE_NEEDED, "organId is required!");
        }
        HibernateStatelessResultAction<List<BaseDoctor>> action = new AbstractHibernateStatelessResultAction<List<BaseDoctor>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                // TODO Auto-generated method stub
                String hql = "select new eh.entity.wh.BaseDoctor(e.jobNumber,d.name,"
                        + "m.code,m.name) from Doctor d,Employment e,Department m where d.doctorId = e.doctorId "
                        + " and e.department = m.deptId and m.organId = :organId ";
                Query query = ss.createQuery(hql);
                query.setParameter("organId", organId);
                List<BaseDoctor> objArrList = query.list();
                setResult(objArrList);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 根据医生内码获取手机号和团队标志
     *
     * @param doctorId 医生内码
     * @return Object[]
     * @author luf
     */
    @DAOMethod(sql = "SELECT mobile,teams FROM Doctor WHERE doctorId=:doctorId")
    public abstract Object[] getMobileAndTeamsByDoctorId(
            @DAOParam("doctorId") Integer doctorId);

    @RpcService
    @DAOMethod(sql = "select name from Doctor where mobile=:mobile")
    public abstract String getNameByMobile(@DAOParam("mobile") String mobile);

    @RpcService
    @DAOMethod(sql = "select name from Doctor where doctorId = :id")
    public abstract String getNameById(@DAOParam("id") int id);

    @RpcService
    @DAOMethod(sql = "select mobile from Doctor where doctorId=:doctorId")
    public abstract String getMobileByDoctorId(
            @DAOParam("doctorId") Integer doctorId);

    @DAOMethod(sql = "select mobile from Doctor where doctorId in(:doctorIds)")
    public abstract List<String> findMobilesByDoctorIds(
            @DAOParam("doctorIds") List<Integer> doctorIds);

    @RpcService
    @DAOMethod(sql = "SELECT teams FROM Doctor WHERE doctorId=:doctorId")
    public abstract Boolean getTeamsByDoctorId(
            @DAOParam("doctorId") Integer doctorId);

    @DAOMethod(sql = "select photo from Doctor where doctorId=:doctorId")
    public abstract Integer getPhotoByDoctorId(
            @DAOParam("doctorId") Integer doctorId);

    @DAOMethod(sql = "select busyFlag from Doctor where doctorId=:doctorId")
    public abstract Integer getBusyFlagByDoctorId(
            @DAOParam("doctorId") Integer doctorId);

    @DAOMethod(sql = "select groupMode from Doctor where doctorId=:doctorId")
    public abstract Integer getGroupModeByDoctorId(
            @DAOParam("doctorId") Integer doctorId);

    @RpcService
    @DAOMethod(sql = "select organ from Doctor where doctorId=:doctorId")
    public abstract Integer getOrganByDoctorId(
            @DAOParam("doctorId") Integer doctorId);

    @DAOMethod(sql = "select signImage from Doctor where doctorId=:doctorId")
    public abstract Integer getSignImageByDoctorId(
            @DAOParam("doctorId") Integer doctorId);

    /**
     * @param @param mobile 手机号
     * @return void
     * @throws
     * @Title: sendVCode
     * @Description: TODO 验证数据库中是否包含该手机号，若无则可继续进行注册操作
     * @author AngryKitty
     * @Date 2015-11-17上午10:16:34
     */
    @RpcService
    public String sendVCode(String mobile) {
        UserDAO userDao = DAOFactory.getDAO(UserDAO.class);
        Doctor doc = this.getByMobile(mobile);
        if (userDao.exist(mobile) && doc != null) {
            throw new DAOException(609, "该号码已注册，请直接登录");
        }
        ValidateCodeDAO vcDao = DAOFactory.getDAO(ValidateCodeDAO.class);
        return vcDao.sendValidateCodeToRegister(mobile);

    }

    /**
     * @param @param mobile 手机号
     * @param @param name 姓名
     * @param @param IDCard 身份证号码
     * @param @param organ 机构编码
     * @param @param profession 专科编码
     * @param @param proTitle 职称编码
     * @param @param invitationCode 邀请码
     * @param @param otherName 如果机构是其他,otherName必填
     * @return void
     * @throws
     * @Title: RegisteredDoctorAccount
     * @Description: TODO 医生注册过程
     * @author AngryKitty
     * @Date 2015-11-17上午11:04:58
     * @date 2016-3-3 luf 修改异常code
     * @date 2016-08-01 houxr 修改为机构是[其他]的机构也能注册医生,并且保存其他机构名称:otherName
     */
    @RpcService
    public void RegisteredDoctorAccount(String mobile, String name,
                                        String IDCard, int organ, String profession, String proTitle,
                                        Integer invitationCode) {
        if (StringUtils.isEmpty(mobile)) {
            new DAOException(DAOException.VALUE_NEEDED, "mobile is required!");
        }
        if (StringUtils.isEmpty(name)) {
            new DAOException(DAOException.VALUE_NEEDED, "name is required!");
        }
        if (StringUtils.isEmpty(IDCard)) {
            new DAOException(DAOException.VALUE_NEEDED, "IDCard is required!");
        }
        if (StringUtils.isEmpty(profession)) {
            new DAOException(DAOException.VALUE_NEEDED, "profession is required!");
        }
        DepartmentDAO deptDao = DAOFactory.getDAO(DepartmentDAO.class);
        EmploymentDAO empDao = DAOFactory.getDAO(EmploymentDAO.class);
        ConsultSetDAO csDao = DAOFactory.getDAO(ConsultSetDAO.class);
        UserDAO userDao = DAOFactory.getDAO(UserDAO.class);
        if (userDao.exist(mobile) && this.getByMobile(mobile) != null) {
            throw new DAOException(609, "该号码已注册，请直接登录");
        }
        String proText = ""; // 专科名称
        Department dept = null;// 科室信息
        try {
            proText = DictionaryController.instance().get("eh.base.dictionary.Profession").getText(profession);
        } catch (ControllerException e) {
            log.error("RegisteredDoctorAccount() error: "+e);
        }
        Doctor doc = this.getByIdNumber(IDCard);// 根据身份证获取医生信息
        if (doc == null) {
            doc = this.getByNameAndMobile(name, mobile);// 根据姓名和手机号获取医生信息
            if (doc == null) {
                dept = deptDao.getEffByNameAndOrgan(proText, organ);
                if (dept != null) {
                    List<Doctor> docs = this.findByNameAndDeptId(name,
                            dept.getDeptId());// 根据姓名和科室信息获得医生信息
                    if (docs != null && docs.size() > 0) {
                        doc = docs.get(0);
                    }
                }
            }
        }
        if (doc == null) {// 医生不存在
            Doctor oldDoctor = this.getByMobile(mobile);
            if (oldDoctor != null) {// 若原数据库中包含该手机号码，则将原数据库中的手机好前面加上“CF”
                oldDoctor.setMobile("CF" + oldDoctor.getMobile());
                this.update(oldDoctor);
            }
            dept = deptDao.getDeptByProfessionIdAndOrgan(profession, organ);
            int deptId = dept.getDeptId();// 科室ID
            doc = new Doctor();
            RegisterDoctorSevice rdSevice = new RegisterDoctorSevice();
            doc = rdSevice.convertIdcard(IDCard, doc);// 将身份证信息填充进去
            doc.setName(name);
            doc.setMobile(mobile);
            doc.setUserType(1);
            doc.setProfession(profession);
            doc.setProTitle(StringUtils.isEmpty(proTitle) ? "99" : proTitle);
            doc.setTeams(false);
            doc.setStatus(0);
            doc.setCreateDt(new Date());
            doc.setLastModify(new Date());
            doc.setOrgan(organ);
            doc.setDepartment(dept.getDeptId());
            doc.setChief(0);
            doc.setOrderNum(1);
            doc.setVirtualDoctor(false);
            doc.setSource(1);
            doc.setHaveAppoint(0);
            doc.setInvitationCode(invitationCode);
            doc.setRewardFlag(false);
            doc = this.save(doc);
            int doctorId = doc.getDoctorId();// 医生ID
            empDao.RegisteredEmployment(doctorId, organ, deptId);// 更新机构信息
            if (!csDao.exist(doctorId)) {
                ConsultSet cs = new ConsultSet();
                cs.setDoctorId(doctorId);
                cs.setOnLineStatus(0);
                cs.setAppointStatus(0);
                cs.setTransferStatus(0);
                cs.setMeetClinicStatus(0);
                cs.setPatientTransferStatus(0);

                HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
                cs.setCanSign(hisServiceConfigDao.isServiceEnable(organ, ServiceType.CANSIGN));
                csDao.save(cs);
            }
            String password = IDCard.substring(IDCard.length() - 6, IDCard.length());
            this.createDoctorUser(doctorId, password);
        } else {// 医生存在
            if (userDao.exist(doc.getMobile())) {// 数据库中已有医生已经开户
                throw new DAOException(600, "" + doc.getMobile());
            } else {// 未开户
                if (dept == null) {
                    dept = deptDao.saveDeptByProfessionAndOrgan(profession,
                            proText, organ);
                }
                Doctor oldDoctor = this.getByMobile(mobile);
                if (oldDoctor != null) {// 若原数据库中包含该手机号码，则将原数据库中的手机好前面加上“CF”
                    oldDoctor.setMobile("CF" + oldDoctor.getMobile());
                    this.update(oldDoctor);
                }
                RegisterDoctorSevice rdSevice = new RegisterDoctorSevice();
                int doctorId = doc.getDoctorId();// 医生ID
                int deptId = dept.getDeptId();// 科室ID
                doc = rdSevice.convertIdcard(IDCard, doc);// 将身份证信息填充进去
                doc.setName(name);
                doc.setMobile(mobile);
                doc.setUserType(1);
                doc.setProfession(profession);
                doc.setProTitle(StringUtils.isEmpty(proTitle) ? "99" : proTitle);
                doc.setTeams(false);
                doc.setStatus(1);
                doc.setLastModify(new Date());
                doc.setOrgan(organ);
                doc.setDepartment(dept.getDeptId());
                doc.setChief(0);
                doc.setOrderNum(1);
                doc.setVirtualDoctor(false);
                doc.setSource(1);
                doc.setInvitationCode(invitationCode);
                doc.setRewardFlag(false);
                this.update(doc);// 更新医生基础信息
                empDao.RegisteredEmployment(doctorId, organ, deptId);// 更新机构信息
                if (!csDao.exist(doctorId)) {
                    ConsultSet cs = new ConsultSet();
                    cs.setDoctorId(doctorId);
                    cs.setOnLineStatus(0);
                    cs.setAppointStatus(0);
                    cs.setTransferStatus(0);
                    cs.setMeetClinicStatus(0);

                    HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
                    cs.setCanSign(hisServiceConfigDao.isServiceEnable(organ, ServiceType.CANSIGN));
                    csDao.save(cs);
                }
                String password = IDCard.substring(IDCard.length() - 6, IDCard.length());
                this.createDoctorUser(doctorId, password);
            }
        }
    }

    /**
     * @param @param mobile 手机号
     * @param @param name 姓名
     * @param @param IDCard 身份证号码
     * @param @param profession 专科编码
     * @param @param proTitle 职称编码
     * @param @param invitationCode 邀请码
     * @param @param otherName 如果机构是其他,otherName必填
     * @return void
     * @throws
     * @Title: RegisteredDoctorAccount
     * @Description: 医生注册过程
     * @author houxr
     * @date 2016-08-01 修改为机构是[其他]的机构也能注册医生,并且保存其他机构名称:otherOrganName
     */
    @RpcService
    public void RegisteredDoctorAccountByOtherOrgan(String mobile, String name,
                                                    String IDCard, String profession, String proTitle,
                                                    Integer invitationCode, String otherOrganName) {
        if (StringUtils.isEmpty(mobile)) {
            new DAOException(DAOException.VALUE_NEEDED, "mobile is required!");
        }
        if (StringUtils.isEmpty(name)) {
            new DAOException(DAOException.VALUE_NEEDED, "name is required!");
        }
        if (StringUtils.isEmpty(IDCard)) {
            new DAOException(DAOException.VALUE_NEEDED, "IDCard is required!");
        }
        if (StringUtils.isEmpty(profession)) {
            new DAOException(DAOException.VALUE_NEEDED, "profession is required!");
        }
        if (StringUtils.isEmpty(otherOrganName)) {
            new DAOException(DAOException.VALUE_NEEDED, "otherOrganName is required!");
        }
        DepartmentDAO deptDao = DAOFactory.getDAO(DepartmentDAO.class);
        EmploymentDAO empDao = DAOFactory.getDAO(EmploymentDAO.class);
        OrganDAO organDao = DAOFactory.getDAO(OrganDAO.class);
        int organ = organDao.getByManageUnit("ehother").getOrganId();
        ConsultSetDAO csDao = DAOFactory.getDAO(ConsultSetDAO.class);
        UserDAO userDao = DAOFactory.getDAO(UserDAO.class);
        if (userDao.exist(mobile) && this.getByMobile(mobile) != null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该号码已注册，请直接登录");
        }
        String proText = ""; // 专科名称
        Department dept = null;// 科室信息
        try {
            proText = DictionaryController.instance().get("eh.base.dictionary.Profession").getText(profession);
        } catch (ControllerException e) {
            log.error("RegisteredDoctorAccountByOtherOrgan() error:  "+e);
        }
        Doctor doc = this.getByIdNumber(IDCard);// 根据身份证获取医生信息
        if (doc == null) {
            doc = this.getByNameAndMobile(name, mobile);// 根据姓名和手机号获取医生信息
            if (doc == null) {
                dept = deptDao.getByNameAndOrgan(proText, organ);
                if (dept != null) {
                    List<Doctor> docs = this.findByNameAndDeptId(name, dept.getDeptId());//根据姓名和科室信获得医息生信息
                    if (docs != null && docs.size() > 0) {
                        doc = docs.get(0);
                    }
                }
            }
        }
        if (doc == null) {// 医生不存在
            Doctor oldDoctor = this.getByMobile(mobile);
            if (oldDoctor != null) {// 若原数据库中包含该手机号码，则将原数据库中的手机好前面加上“CF”
                oldDoctor.setMobile("CF" + oldDoctor.getMobile());
                this.update(oldDoctor);
            }
            dept = deptDao.getDeptByProfessionIdAndOrgan(profession, organ);
            int deptId = dept.getDeptId();// 科室ID
            doc = new Doctor();
            RegisterDoctorSevice rdSevice = new RegisterDoctorSevice();
            doc = rdSevice.convertIdcard(IDCard, doc);// 将身份证信息填充进去
            doc.setName(name);
            doc.setMobile(mobile);
            doc.setUserType(1);
            doc.setProfession(profession);
            doc.setProTitle(StringUtils.isEmpty(proTitle) ? "99" : proTitle);
            doc.setTeams(false);
            doc.setStatus(0);
            doc.setCreateDt(new Date());
            doc.setLastModify(new Date());
            doc.setOrgan(organ);
            doc.setDepartment(dept.getDeptId());
            doc.setChief(0);
            doc.setOrderNum(1);
            doc.setVirtualDoctor(false);
            doc.setSource(1);
            doc.setHaveAppoint(0);
            doc.setInvitationCode(invitationCode);
            doc.setRewardFlag(false);
            doc.setOtherOrganName(otherOrganName);
            doc = this.save(doc);
            int doctorId = doc.getDoctorId();// 医生ID
            empDao.RegisteredEmployment(doctorId, organ, deptId);// 更新机构信息
            if (!csDao.exist(doctorId)) {
                ConsultSet cs = new ConsultSet();
                cs.setDoctorId(doctorId);
                cs.setOnLineStatus(0);
                cs.setAppointStatus(0);
                cs.setTransferStatus(0);
                cs.setMeetClinicStatus(0);
                cs.setPatientTransferStatus(0);

                HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
                cs.setCanSign(hisServiceConfigDao.isServiceEnable(organ, ServiceType.CANSIGN));
                csDao.save(cs);
            }
            String password = IDCard.substring(IDCard.length() - 6, IDCard.length());
            this.createDoctorUser(doctorId, password);
        } else {// 医生存在
            if (userDao.exist(doc.getMobile())) {// 数据库中已有医生已经开户
                throw new DAOException(ErrorCode.SERVICE_ERROR, "" + doc.getMobile());
            } else {// 未开户
                if (dept == null) {
                    dept = deptDao.saveDeptByProfessionAndOrgan(profession, proText, organ);
                }
                Doctor oldDoctor = this.getByMobile(mobile);
                if (oldDoctor != null) {// 若原数据库中包含该手机号码，则将原数据库中的手机好前面加上“CF”
                    oldDoctor.setMobile("CF" + oldDoctor.getMobile());
                    this.update(oldDoctor);
                }
                RegisterDoctorSevice rdSevice = new RegisterDoctorSevice();
                int doctorId = doc.getDoctorId();// 医生ID
                int deptId = dept.getDeptId();// 科室ID
                doc = rdSevice.convertIdcard(IDCard, doc);// 将身份证信息填充进去
                doc.setName(name);
                doc.setMobile(mobile);
                doc.setUserType(1);
                doc.setProfession(profession);
                doc.setProTitle(StringUtils.isEmpty(proTitle) ? "99" : proTitle);
                doc.setTeams(false);
                doc.setStatus(1);
                doc.setLastModify(new Date());
                doc.setOrgan(organ);
                doc.setDepartment(dept.getDeptId());
                doc.setChief(0);
                doc.setOrderNum(1);
                doc.setVirtualDoctor(false);
                doc.setSource(1);
                doc.setInvitationCode(invitationCode);
                doc.setRewardFlag(false);
                this.update(doc);// 更新医生基础信息
                empDao.RegisteredEmployment(doctorId, organ, deptId);// 更新机构信息
                if (!csDao.exist(doctorId)) {
                    ConsultSet cs = new ConsultSet();
                    cs.setDoctorId(doctorId);
                    cs.setOnLineStatus(0);
                    cs.setAppointStatus(0);
                    cs.setTransferStatus(0);
                    cs.setMeetClinicStatus(0);

                    HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
                    cs.setCanSign(hisServiceConfigDao.isServiceEnable(organ, ServiceType.CANSIGN));
                    csDao.save(cs);
                }
                String password = IDCard.substring(IDCard.length() - 6, IDCard.length());
                this.createDoctorUser(doctorId, password);
            }
        }
    }

    /**
     * @param @return
     * @return Doctor
     * @throws
     * @Title: getByNameAndDeptId
     * @Description: TODO根据姓名和科室信息得到医生信息
     * @author AngryKitty
     * @Date 2015-11-17下午4:00:17
     */
    @RpcService
    @DAOMethod(sql = "select d from  Doctor d,Employment e where d.doctorId=e.doctorId  and d.name=:name and e.department=:department")
    public abstract List<Doctor> findByNameAndDeptId(
            @DAOParam("name") String name,
            @DAOParam("department") Integer department);

    @RpcService
    @DAOMethod(sql = " from Doctor where name =:name and mobile=:mobile")
    public abstract Doctor getByNameAndMobile(@DAOParam("name") String name,
                                              @DAOParam("mobile") String mobile);

    /**
     * @param @param  doctorId 医生ID
     * @param @param  relationDoctorId 对象医生ID
     * @param @return
     * @return Map<String,Object>
     * @throws
     * @Title: getDoctorInfoAndRelationFlag
     * @Description: TODO查询医生信息，点赞数，关注数，和是否关注标志
     * @author AngryKitty
     * @Date 2015-11-18上午10:10:49
     * 修改点赞数只包含医生点赞 zhangsl
     * @Date 2016-11-16 13:56:34
     */
    @RpcService
    public Map<String, Object> getDoctorInfoAndRelationFlag(Integer doctorId,
                                                            Integer relationDoctorId) {
        PatientFeedbackDAO pfDao = DAOFactory.getDAO(PatientFeedbackDAO.class);
        DoctorRelationDoctorDAO drdDao = DAOFactory
                .getDAO(DoctorRelationDoctorDAO.class);

        Map<String, Object> map = new HashMap<String, Object>();

        Doctor d = this.getByDoctorId(relationDoctorId);

        map.put("docInfo", d);
        map.put("patientFeedback",
                pfDao.getNumByDoctorIdAndUserType(relationDoctorId, "doctor"));//只显示医生点赞数
        map.put("relationNumber", this.doctorRelationNumber(relationDoctorId));
        map.put("relationDoctorFlag",
                drdDao.getRelationDoctorFlag(doctorId, relationDoctorId));

        return map;
    }

    /**
     * 供findDoctorsByStatusTwo调用
     *
     * @param status 医生状态
     * @param start  分页起始位置
     * @param limit  每页限制条数
     * @return List<Doctor>
     * @author luf
     */
    @DAOMethod(sql = "from Doctor where status=:status order by lastModify")
    public abstract List<Doctor> findDoctorByStatusAsc(
            @DAOParam("status") Integer status,
            @DAOParam(pageStart = true) int start,
            @DAOParam(pageLimit = true) int limit);

    /**
     * 查询所有审核中的医生
     *
     * @param start 分页起始位置
     * @param limit 每页限制条数
     * @return List<Doctor>
     * @author luf
     */
    @RpcService
    public List<Doctor> findDoctorsByStatusTwo(int start, int limit) {
        return findDoctorByStatusAsc(2, start, limit);
    }

    /* /**
      * 审核医生
      *
      * @param doctorId 医生内码
      * @param status   医生状态
      * @author luf
      */
   /* @RpcService
    public void auditDoctorWithMsg(int doctorId, int status) {
        Doctor doctor = this.get(doctorId);
        if (doctor == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "no such doctor!");
        }
        if (status >= 2) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "status is wrong!");
        }
        doctor.setStatus(status);
        doctor.setLastModify(new Date());
        if (updateDoctorByDoctorId(doctor)) {
            sendValidateCodeByRole(doctor.getMobile(), doctor.getName(), status);
        }
    }*/

    /**
     * 医生审核结果短息
     *
     * @param mobile     医生手机号
     * @param doctorName 医生姓名
     * @param status     医生状态
     * @author luf
     */
    @RpcService
    public void sendValidateCodeByRole(Integer doctorId, Integer organ, String mobile, String doctorName,
                                       int status, String cause) {
        SmsInfo info = new SmsInfo();
        info.setBusId(doctorId);
        info.setStatus(0);
        info.setOrganId(organ == null ? 0 : organ);
        Map<String, Object> smsMap = new HashMap<String, Object>();
        smsMap.put("docMobile", mobile);
        if (status == 0) {
            info.setBusType("AuditDoctorFailure");
            info.setSmsType("AuditDoctorFailure");
            smsMap.put("doc", doctorName);
            smsMap.put("errCause", cause);
        } else {
            info.setBusType("AuditDoctorSuccess");
            info.setSmsType("AuditDoctorSuccess");
            smsMap.put("name", doctorName);
        }
        info.setExtendWithoutPersist(JSONUtils.toString(smsMap));
        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
        smsPushService.pushMsgData2OnsExtendValue(info);
    }

    /**
     * 医生的被点赞数和粉丝数
     *
     * @param doctorId 医生A的doctorId
     * @return
     * @author zhangx
     * @date 2015-12-10 上午11:27:13
     */
    @RpcService
    public HashMap<String, Long> getNumByDoctor(int doctorId) {
        HashMap<String, Long> map = new HashMap<String, Long>();

        // 关注医生A的患者总数
        RelationDoctorDAO relDao = DAOFactory.getDAO(RelationDoctorDAO.class);
        map.put("relationPatientNum", relDao.getRelationNum(doctorId, 1));

        // 关注医生A的医生总数
        DoctorRelationDoctorDAO docRelDao = DAOFactory
                .getDAO(DoctorRelationDoctorDAO.class);
        map.put("relationDocNum",
                docRelDao.getDoctorRelationDoctorNum(doctorId));

        // 患者点赞数
        PatientFeedbackDAO feedDao = DAOFactory
                .getDAO(PatientFeedbackDAO.class);
        map.put("feedbakPatientNum",
                feedDao.getNumByDoctorIdAndUserType(doctorId, "patient"));

        // 医生点赞数
        map.put("feedbakDocNum",
                feedDao.getNumByDoctorIdAndUserType(doctorId, "doctor"));

        DoctorRelationDoctorDAO doctorRelationDoctorDAO = DAOFactory.getDAO(DoctorRelationDoctorDAO.class);
        //医生A关注的医生总数
        map.put("followingDoctorNum", doctorRelationDoctorDAO.getFollowingDoctorNum(doctorId));
        return map;

    }

    /**
     * 健康端按条件查找医生
     * <p>
     * eh.base.dao
     *
     * @param profession  专科编码
     * @param addrArea    属地区域
     * @param domain      擅长领域
     * @param name        医生姓名
     * @param haveAppoint 预约号源标志
     * @param proTitle    职称
     * @param flag        标志-0咨询1预约
     * @param start       起始页
     * @param limit       每页限制条数
     * @return List<HashMap<String,Object>>
     * @author luf 2016-2-26 增加筛选条件-按入口分别查询
     * @Date 2016年11月14日 15:32:58 zhangsl 修改按评分高低排序
     */
    @RpcService
    public List<HashMap<String, Object>> searchDoctorForHealth(
            final String profession, final String addrArea,
            final String domain, final String name, final Integer haveAppoint,
            final String proTitle, final int flag, final int start,
            final int limit) {
        List<Doctor> docList = new ArrayList<Doctor>();
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
                List<Integer> organs = organDAO.findOrgansByUnitForHealth();

                StringBuilder hql = new StringBuilder(
                        "select distinct d from Doctor d,Organ o,ConsultSet c where "
                                // 2016-3-5 luf：患者端只显示有效的正常医生
                                // 2016-4-25 luf:添加个性化  and (d.organ in :organs)
                                // 2016-6-2 luf:放开团队医生限制添加 or d.teams=1
                                + "o.organId=d.organ and c.doctorId=d.doctorId and ((d.idNumber is not null and d.idNumber<>:empty)or d.teams=1) and d.status=1");
                if (organs != null && organs.size() > 0) {
                    hql.append(" and (");
                    for (Integer i : organs) {
                        hql.append("d.organ=").append(i).append(" or ");
                    }
                    hql = new StringBuilder(hql.substring(0, hql.length() - 3)).append(")");
                }
                if (!StringUtils.isEmpty(profession)) {
                    hql.append(" and d.profession like :profession");
                }
                if (!StringUtils.isEmpty(addrArea)) {
                    hql.append(" and o.addrArea like :addrArea");
                }
                if (!StringUtils.isEmpty(domain)) {
                    hql.append(" and d.domain like :domain");
                }
                if (!StringUtils.isEmpty(name)) {
                    hql.append(" and d.name like :name");
                }
                if (haveAppoint != null) {
                    hql.append(" and d.haveAppoint=:haveAppoint");
                }
                if (!StringUtils.isEmpty(proTitle)) {
                    hql.append(" and d.proTitle=:proTitle");
                }

                //2016-7-15 luf:业务设置为null是排序出错bug修复，添加IFNULL(,0)
                if (flag == 0) {
                    hql.append(" order by (IFNULL(c.onLineStatus,0)+IFNULL(c.appointStatus,0)) DESC,d.rating DESC");
                } else {
                    hql.append(" order by (IFNULL(d.haveAppoint,0)+IFNULL(c.patientTransferStatus,0)) DESC,d.rating DESC");
                }
                Query q = ss.createQuery(hql.toString());
                q.setParameter("empty", "");
                if (!StringUtils.isEmpty(profession)) {
                    q.setParameter("profession", profession + "%");
                }
                if (!StringUtils.isEmpty(addrArea)) {
                    q.setParameter("addrArea", addrArea + "%");
                }
                if (!StringUtils.isEmpty(domain)) {
                    q.setParameter("domain", "%" + domain + "%");
                }
                if (!StringUtils.isEmpty(name)) {
                    q.setParameter("name", "%" + name + "%");
                }
                if (haveAppoint != null) {
                    q.setParameter("haveAppoint", haveAppoint);
                }
                if (!StringUtils.isEmpty(proTitle)) {
                    q.setParameter("proTitle", proTitle);
                }
                q.setMaxResults(limit);
                q.setFirstResult(start);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        docList = action.getResult();
        if (docList==null || docList.size()==0) {
            return new ArrayList<HashMap<String, Object>>();
        }
        EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
        List<HashMap<String, Object>> targets = new ArrayList<HashMap<String, Object>>();
        for (Doctor doctor : docList) {
            HashMap<String, Object> result = new HashMap<String, Object>();
            int doctorId = doctor.getDoctorId();
            Employment employment = employmentDAO
                    .getPrimaryEmpByDoctorId(doctorId);
            if (employment != null) {
                doctor.setDepartment(employment.getDepartment());
            }
            ConsultSetDAO dao = DAOFactory.getDAO(ConsultSetDAO.class);
            ConsultSet consultSet = dao.get(doctorId);
            result.put("doctor", doctor);
            result.put("consultSet", consultSet);
            targets.add(result);
        }
        return targets;
    }

    /**
     * 未登录状态下推荐医生-首页
     * 为了保证健康APP能正常使用，将doctorsRecommendedUnLogin重新封装
     *
     * @param homeArea 属地区域
     * @return Map<String, List<Doctor>>
     * @throws ControllerException
     * @author luf
     * @date 2016-2-29 添加按业务数倒序，并修改每个专科下医生数量为4
     */
    @RpcService
    public Map<String, List<Doctor>> doctorsRecommendedUnLogin(String homeArea)
            throws ControllerException {
        Map<String, Object> map = doctorsRecommendedUnLogin2(homeArea);
        Map<String, List<Doctor>> result = (Map<String, List<Doctor>>) map.get("doctors");
        return result;
    }

    /**
     * 未登录状态下推荐医生-首页
     *
     * @param homeArea 属地区域
     * @return Map<String, List<Doctor>>
     * @throws ControllerException
     * @author luf
     * @date 2016-2-29 添加按业务数倒序，并修改每个专科下医生数量为4
     */
    @RpcService
    public Map<String, Object> doctorsRecommendedUnLogin2(String homeArea)
            throws ControllerException {
        if (StringUtils.isEmpty(homeArea)) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "homeArea is required!");
        }
        List<Doctor> list = new ArrayList<Doctor>();
        String addrArea = "0000000";
        for (int i = 2; i < 5; i++) {
            String profession = "0" + i;
            HashMap<String, Object> os = getDoctorListWithWhile(homeArea, profession, 4);
            if (os == null || os.get("list") == null) {
                continue;
            }
            list.addAll((List<Doctor>) os.get("list"));
            String addr = (String) os.get("home");
            if (addr.length() < addrArea.length()) {
                addrArea = addr;
            }
        }
        Map<String, List<Doctor>> map = new HashMap<String, List<Doctor>>();
        for (Doctor doctor : list) {
            String profession = doctor.getProfession();
            Doctor d = new Doctor();
            d.setDoctorId(doctor.getDoctorId());
            d.setName(doctor.getName());
            d.setPhoto(doctor.getPhoto());
            d.setProTitle(doctor.getProTitle());
            d.setGender(doctor.getGender());
            profession = (String) profession.subSequence(0, 2);
            d.setProfession(profession);
            String professionName = DictionaryController.instance()
                    .get("eh.base.dictionary.Profession").getText(profession);
            if (map.containsKey(professionName)) {
                map.get(professionName).add(d);
            } else {
                List<Doctor> ds = new ArrayList<Doctor>();
                ds.add(d);
                map.put(professionName, ds);
            }
        }
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("doctors", map);
        result.put("addrArea", addrArea);
        result.put("addrAreaText", DictionaryController.instance()
                .get("eh.base.dictionary.AddrArea")
                .getText(addrArea));
        return result;
    }


    /**
     * 首页医生推荐-更多
     * <p>
     * eh.base.dao
     *
     * @param homeArea
     * @param profession
     * @return List<HashMap<String,Object>>
     * @author luf 2016-1-25
     */
    @RpcService
    public List<HashMap<String, Object>> doctorsRecMore(String homeArea,
                                                        String profession) {
        if (StringUtils.isEmpty(homeArea)) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "homeArea is required!");
        }
        List<Doctor> list = new ArrayList<Doctor>();
        HashMap<String, Object> os = getDoctorListWithWhile(homeArea, profession, 10);
        if (os == null || os.get("list") == null) return new ArrayList<>();
        list.addAll((List<Doctor>) os.get("list"));
        List<HashMap<String, Object>> targets = new ArrayList<HashMap<String, Object>>();
        for (Doctor doctor : list) {
            HashMap<String, Object> result = new HashMap<String, Object>();
            int doctorId = doctor.getDoctorId();
            ConsultSetDAO dao = DAOFactory.getDAO(ConsultSetDAO.class);
            ConsultSet consultSet = dao.get(doctorId);
            result.put("doctor", doctor);
            result.put("consultSet", consultSet);
            targets.add(result);
        }
        return targets;
    }

    /**
     * 供推荐医生调用
     * <p>
     * eh.base.dao
     *
     * @param homeArea   属地区域
     * @param profession 专科编码
     * @param num        限制条数
     * @return List<Doctor>
     * @author luf 2016-2-25
     */
    public HashMap<String, Object> getDoctorListWithWhile(String homeArea,
                                                          String profession, int num) {
        List<Doctor> list = new ArrayList<Doctor>();
        int j = 0;
        while (j <= 2) {
            //2016-4-27 luf：最高到省级
            int count = 6;
            count = count - j * 2;
            homeArea = homeArea.substring(0, count);
            List<Doctor> ds = this.findDoctorByTwoLikeNew(homeArea, profession,
                    num);
            if (ds.size() == num) {
                list.addAll(ds);
                break;
            }
            if (j == 2) {
                list.addAll(ds);
            }
            j++;
        }
        HashMap<String, Object> daa = new HashMap<String, Object>();
        daa.put("list", list);
        daa.put("home", homeArea);
        return daa;
    }

    /**
     * 供自珍推荐医生调用
     * <p>
     * eh.base.dao
     *
     * @param homeArea   属地区域
     * @param profession 专科编码
     * @param num        限制条数
     * @return List<Doctor>
     * @author luf 2016-2-25
     */
    public List<Doctor> getDoctorsforAutodiagnosis(String homeArea,String profession,int num) {
        return this.findDoctorByTwoLikeNew(homeArea, profession,num);
    }

    /**
     * 供getDoctorListWithWhile调用
     *
     * @param addrArea   属地区域
     * @param profession 专科编码
     * @param max
     *                   <p>
     *                   修改日期--2016-1-19：修改 q.setMaxResults(10)
     * @return List<Doctor>
     * @author luf
     * @Date 2016年11月14日 15:32:58 zhangsl 修改按评分高低排序
     */
    /*
     * public List<Doctor> findDoctorByTwoLike(final String addrArea, final
	 * String profession, final int max) {
	 * HibernateStatelessResultAction<List<Doctor>> action = new
	 * AbstractHibernateStatelessResultAction<List<Doctor>>() {
	 *
	 * @SuppressWarnings("unchecked") public void execute(StatelessSession ss)
	 * throws DAOException { String hql =
	 * "select d From Doctor d,Organ o,ConsultSet c where o.addrArea like :addrArea "
	 * +
	 * "and d.organ=o.organId and c.doctorId=d.doctorId and(d.haveAppoint=1 or "
	 * + "c.onLineStatus=1 or c.appointStatus=1 or c.patientTransferStatus=1) "
	 * +
	 * "and d.status=1 and d.profession like :profession order by d.goodRating desc"
	 * ; Query q = ss.createQuery(hql); q.setParameter("addrArea", addrArea +
	 * "%"); q.setParameter("profession", profession + "%");
	 * q.setMaxResults(max); setResult(q.list()); } };
	 * HibernateSessionTemplate.instance().executeTrans(action); return
	 * action.getResult(); }
	 */
    public List<Doctor> findDoctorByTwoLikeNew(final String addrArea,
                                               final String profession, final int max) {
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
                List<Integer> organs = organDAO.findOrgansByUnitForHealth();
                StringBuilder hql = new StringBuilder("select d From Doctor d,Organ o,ConsultSet c where o.addrArea like :addrArea "
                        + "and d.organ=o.organId and c.doctorId=d.doctorId and(d.haveAppoint=1 or "
                        // 2016-6-2：luf 推荐医生放开团队医生限制，增加 or d.teams=1
                        + "c.onLineStatus=1 or c.appointStatus=1 or c.patientTransferStatus=1 or d.teams=1) "
                        // 2016-3-5:luf 推荐医生里面不显示团队医生，去掉 or d.teams=1
                        + "and d.status=1 and d.profession like :profession and (d.idNumber is not null and d.idNumber<>:empty) AND (d.testPersonnel is null OR d.testPersonnel='0') ");
                // 2016-4-25:luf 添加个性化 and (d.organ in :organs)
                if (organs != null && organs.size() > 0) {
                    hql.append(" and (");
                    for (Integer i : organs) {
                        hql.append("d.organ=").append(i).append(" or ");
                    }
                    hql = new StringBuilder(hql.substring(0, hql.length() - 3)).append(")");
                }
                hql.append(" order by (d.haveAppoint+c.patientTransferStatus+c.onLineStatus+c.appointStatus) DESC,d.rating DESC");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("addrArea", addrArea + "%");
                q.setParameter("profession", profession + "%");
                q.setParameter("empty", "");
                q.setMaxResults(max);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 根据goodRating倒序
     *
     * @param doctors
     * @return
     * @author luf
     */
    public List<Doctor> convertOrderByGoodRating(List<Doctor> doctors) {
        Collections.sort(doctors, new Comparator<Doctor>() {
            public int compare(Doctor arg0, Doctor arg1) {
                if (arg0.getGoodRating() == null) {
                    arg0.setGoodRating(0);
                }
                if (arg1.getGoodRating() == null) {
                    arg1.setGoodRating(0);
                }
                return arg1.getGoodRating().compareTo(arg0.getGoodRating());
            }
        });
        return doctors;
    }

    /**
     * 根据年龄和性别获得专科列表-供 推荐医生 调用
     * <p>
     * eh.base.dao
     *
     * @param age           患者年龄
     * @param patientGender 患者性别 --1男2女
     * 02全科医学 12口腔科 10眼科 0502产科 03内科 04外科 07儿科 19肿瘤科 0501妇科
     * @return List<Object>
     * @author luf 2016-2-25 增加先按业务开通数倒序，后按点赞数倒序
     */
    public List<String> doctorsRecommended(int age, String patientGender) {
        List<String> professions = new ArrayList<String>();
        if (age >= 0) {
            professions.add("02");
            if (age < 60) {
                if (age < 40) {
                    professions.add("07");
                    if (age < 20) {
                        professions.add("12");
                        professions.add("10");
                    } else if (patientGender.equals("2")) {
                        professions.add("0502");
                    } else {
                        professions.add("03");
                        professions.add("04");
                    }
                } else {
                    if (patientGender.equals("1")) {
                        professions.add("19");
                    }
                }
                if (age >= 20 && patientGender.equals("2")) {
                    professions.add("0501");
                }
            } else {
                professions.add("19");
            }
        }
        if (age >= 40) {
            professions.add("03");
            professions.add("04");
        }
        return professions;
    }

    /**
     * 供getConsultOrAppoint调用
     *
     * @param doctorId   医生内码
     * @param addrArea   属地区域
     * @param setType    开通业务条件
     * @param profession 专科编码
     * @return List<Doctor>
     * @author luf
     * @Date 2016年11月14日 15:32:58 zhangsl 修改按评分高低排序
     */
    public List<Doctor> findDoctorListByFour(final int doctorId,
                                             final String addrArea, final String setType,
                                             final String profession, final int start) {
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                String table = setType.substring(0, 1);
                StringBuffer hql = new StringBuffer(
                        "select distinct d from Doctor d,Organ o");
                if (table.equals("c")) {
                    hql.append(",ConsultSet c");
                }
                hql.append(" where d.doctorId<>:doctorId and d.profession like :profession and "
                        + "d.organ=o.organId and o.addrArea like :addrArea and d.status=1 and ");
                if (table.equals("c")) {
                    hql.append("d.doctorId=c.doctorId and ");
                }
                hql.append(setType);
                hql.append(" order by d.rating desc");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("doctorId", doctorId);
                q.setParameter("profession", profession + "%");
                q.setParameter("addrArea", addrArea + "%");
                q.setFirstResult(start);
                q.setMaxResults(5);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 供咨询和预约推荐医生调用
     *
     * @param doctorId   医生内码
     * @param addrArea   属地区域
     * @param setType    开通业务条件
     * @param profession 专科编码
     * @return List<Doctor>
     * @author luf
     */
    public List<Doctor> getConsultOrAppoint(int doctorId, String addrArea,
                                            String setType, String profession, int start) {
        List<Doctor> list = new ArrayList<Doctor>();
        int j = 0;
        int count = 6;
        while (j <= 2) {
            addrArea = addrArea.substring(0, count);
            List<Doctor> ds = this.findDoctorListByFour(doctorId, addrArea,
                    setType, profession, start);
            if (ds.size() == 5) {
                list.addAll(ds);
                break;
            }
            if (j == 1
                    && (setType.equals("d.haveAppoint=1") || setType
                    .equals("c.patientTransferStatus=1"))) {
                list.addAll(ds);
                break;
            }
            if (j == 2) {
                list.addAll(ds);
            }
            count = count - 2;
            j++;
        }
        return list;
    }

    /**
     * 供appointDoctorsRecommended调用
     *
     * @param doctorId   医生内码
     * @param organId    机构内码
     * @param setType    开通业务条件
     * @param profession 专科代码
     * @return List<Doctor>
     * @author luf
     * @Date 2016年11月14日 15:32:58 zhangsl 修改按评分高低排序
     */
    public List<Doctor> findDoctorListByFourOrgan(final int doctorId,
                                                  final int organId, final String setType, final String profession,
                                                  final int start) {
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                String table = setType.substring(0, 1);
                StringBuffer hql = new StringBuffer(
                        "select distinct d from Doctor d");
                if (table.equals("c")) {
                    hql.append(",ConsultSet c");
                }
                hql.append(" where d.doctorId<>:doctorId and d.profession like :profession and "
                        + "d.organ=:organId and d.status=1 and ");
                if (table.equals("c")) {
                    hql.append("d.doctorId=c.doctorId and ");
                }
                hql.append(setType);
                hql.append(" order by d.rating desc");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("doctorId", doctorId);
                q.setParameter("profession", profession + "%");
                q.setParameter("organId", organId);
                q.setFirstResult(start);
                q.setMaxResults(5);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 预约推荐医生
     *
     * @param doctorId 医生内码
     * @return List<Doctor>
     * @author luf
     */
    @RpcService
    public List<HashMap<String, Object>> appointDoctorsRecommended(int doctorId) {
        Doctor doctor = this.get(doctorId);
        if (doctor == null) {
            return new ArrayList<HashMap<String, Object>>();
        }
        int organId = doctor.getOrgan();
        String profession = doctor.getProfession();
        OrganDAO dao = DAOFactory.getDAO(OrganDAO.class);
        Organ organ = dao.get(organId);
        String addrArea = organ.getAddrArea();
        String haveAppoint = "d.haveAppoint=1";
        String patientTransferStatus = "c.patientTransferStatus=1";
        List<Doctor> list = new ArrayList<Doctor>();
        List<Doctor> hao = this.findDoctorListByFourOrgan(doctorId, organId,
                haveAppoint, profession, 0);
        if (hao == null || hao.size() < 5) {
            hao = getConsultOrAppoint(doctorId, addrArea, haveAppoint,
                    profession, 0);
        }
        List<Doctor> pao = this.findDoctorListByFourOrgan(doctorId, organId,
                patientTransferStatus, profession, 0);
        if (pao == null || pao.size() < 5) {
            pao = getConsultOrAppoint(doctorId, addrArea,
                    patientTransferStatus, profession, 0);
        }
        if (hao != null && hao.size() == 5) {
            list.addAll(this.priorityWeightDoctor(pao, hao));
            if (list.size() < 5) {
                hao = getConsultOrAppoint(doctorId, addrArea, haveAppoint,
                        profession, 5);
            }
        }
        List<Doctor> target = this.priorityWeightDoctor(pao, hao);
        int size = list.size();
        for (int i = 0; i < target.size() - size; i++) {
            list.add(target.get(i));
        }
        list.addAll(pao);
        List<Doctor> doctors = this.convertOrderByGoodRating(list);
        List<HashMap<String, Object>> targets = new ArrayList<HashMap<String, Object>>();
        for (Doctor doctor2 : doctors) {
            HashMap<String, Object> result = new HashMap<String, Object>();
            int doctorId1 = doctor2.getDoctorId();
            ConsultSetDAO dao2 = DAOFactory.getDAO(ConsultSetDAO.class);
            ConsultSet consultSet = dao2.get(doctorId1);
            result.put("doctor", doctor2);
            result.put("consultSet", consultSet);
            targets.add(result);
        }
        return targets;
    }

    /**
     * 咨询推荐医生
     *
     * @param doctorId 医生内码
     * @return List<Doctor>
     * @author luf
     */
    @RpcService
    public List<HashMap<String, Object>> consultDoctorsRecommended(int doctorId) {
        Doctor doctor = this.get(doctorId);
        if (doctor == null) {
            return new ArrayList<HashMap<String, Object>>();
        }
        String profession = doctor.getProfession();
        String addrArea = "330100";// 杭州市优先
        String appointStatus = "c.appointStatus=1";
        String onLineStatus = "c.onLineStatus=1";
        List<Doctor> list = new ArrayList<Doctor>();
        List<Doctor> apc = getConsultOrAppoint(doctorId, addrArea,
                appointStatus, profession, 0);
        List<Doctor> onc = getConsultOrAppoint(doctorId, addrArea,
                onLineStatus, profession, 0);
        if (onc != null && onc.size() == 5) {
            list.addAll(this.priorityWeightDoctor(apc, onc));
            if (list.size() < 5) {
                onc = getConsultOrAppoint(doctorId, addrArea, onLineStatus,
                        profession, 5);
            }
        }
        List<Doctor> target = this.priorityWeightDoctor(apc, onc);
        int size = list.size();
        for (int i = 0; i < target.size() - size; i++) {
            list.add(target.get(i));
        }
        list.addAll(apc);
        List<Doctor> doctors = this.convertOrderByGoodRating(list);
        List<HashMap<String, Object>> targets = new ArrayList<HashMap<String, Object>>();
        for (Doctor doctor2 : doctors) {
            HashMap<String, Object> result = new HashMap<String, Object>();
            int doctorId1 = doctor2.getDoctorId();
            ConsultSetDAO dao2 = DAOFactory.getDAO(ConsultSetDAO.class);
            ConsultSet consultSet = dao2.get(doctorId1);
            result.put("doctor", doctor2);
            result.put("consultSet", consultSet);
            targets.add(result);
        }
        return targets;
    }

    /**
     * 咨询/预约医生推荐
     * <p>
     * eh.base.dao
     *
     * @param doctorId 医生内码
     * @param flag     标志-0咨询1预约
     * @return List<HashMap<String,Object>>
     * @author luf 2016-1-26
     */
    @RpcService
    public List<HashMap<String, Object>> consultOrAppointDoctorsRecommended(
            int doctorId, int flag) {
        List<HashMap<String, Object>> list = new ArrayList<HashMap<String, Object>>();
        if (flag == 0) {
            list = this.consultDoctorsRecommended(doctorId);
        } else {
            list = this.appointDoctorsRecommended(doctorId);
        }
        if (list != null && list.size() > 2) {
            List<HashMap<String, Object>> results = new ArrayList<HashMap<String, Object>>();
            results.add(list.get(0));
            results.add(list.get(1));
            return results;
        }
        return list;
    }

    /**
     * 优先去重
     *
     * @param arg0 参照
     * @param arg1 去重
     * @author luf
     * @returnc List<Doctor>
     */
    public List<Doctor> priorityWeightDoctor(List<Doctor> arg0,
                                             List<Doctor> arg1) {
        List<Doctor> targets = new ArrayList<Doctor>();
        for (Doctor d1 : arg1) {
            int i = 0;
            for (Doctor d0 : arg0) {
                int id1 = d1.getDoctorId();
                int id0 = d0.getDoctorId();
                if (id1 == id0) {
                    break;
                } else {
                    i++;
                }
            }
            if (i == arg0.size()) {
                targets.add(d1);
            }
        }
        return targets;
    }

    /**
     * 患者端查看医生信息(个人医生信息)，
     * <p>
     * 现在前端使用doctorInfoService.getDoctorInfoForHealth查询个人团队医生信息
     *
     * @param docId 被查看的医生id
     * @param mpi   当前登陆患者的mpi
     * @return
     * @author zhangx
     * @date 2015-12-22 下午5:19:09
     * @date 2016-3-3 luf 修改异常code
     */
    @RpcService
    public HashMap<String, Object> getDoctorInfoForHealth(Integer docId,
                                                          String mpi) {

        EmploymentDAO empDao = DAOFactory.getDAO(EmploymentDAO.class);
        RelationDoctorDAO relationDao = DAOFactory
                .getDAO(RelationDoctorDAO.class);
        RecommendDAO recommDAO = DAOFactory.getDAO(RecommendDAO.class);

        Doctor doc = this.getByDoctorId(docId);

        if (doc == null) {
            throw new DAOException(600, "医生" + docId + "不存在");
        }

        Employment emp = empDao.getPrimaryEmpByDoctorId(docId);
        doc.setDepartment(emp.getDepartment());

        // 获取签约标记，关注标记，关注ID
        RelationDoctor relation = relationDao
                .getByMpiIdAndDoctorIdAndRelationType(mpi, docId);

        if (relation == null) {
            doc.setIsRelation(false);
            doc.setIsSign(false);
            doc.setRelationId(null);
        } else {
            Integer type = relation.getRelationType();
            Integer relationId = relation.getRelationDoctorId();
            doc.setIsSign(false);
            doc.setIsRelation(true);
            doc.setRelationId(relationId);
            if (type != null && type == 0) {
                doc.setIsSign(true);
            }
        }

        // 获取医生关注数(患者关注+医生关注)
        doc.setRelationNum(doctorRelationNumber(docId));

        // 2016-3-8 luf 根据患者端号源查询接口返回参数判断医生是否有号
        AppointSourceDAO asDao = DAOFactory.getDAO(AppointSourceDAO.class);
        List<Object[]> oss = asDao.findTotalByDcotorId(docId, 1);// 患者端固定传1
        if (oss != null && oss.size() > 0) {
            doc.setHaveAppoint(1);
        } else {
            doc.setHaveAppoint(0);
        }

        //异步更新Doctor字段haveAppoint
        //DoctorDAO docDao=DAOFactory.getDAO(DoctorDAO.class);
        //docDao.updateDoctorHaveAppoint(doc,docDao);

        ConsultSet docSet = new DoctorInfoService().getDoctorDisCountSet(docId, mpi, doc.getIsSign());

        List<Recommend> list = recommDAO.findByMpiIdAndDoctorId(mpi, docId);
        for (Recommend recommend : list) {
            // 0特需预约1图文咨询2电话咨询
            Integer recommendType = recommend.getRecommendType();
            switch (recommendType) {
                case 0:
                    docSet.setPatientTransferRecomFlag(true);
                    break;
                case 1:
                    docSet.setOnLineRecomFlag(true);
                    break;
                case 2:
                    docSet.setAppointRecomFlag(true);
                    break;
            }
        }

        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("doctor", doc);
        map.put("consultSet", docSet);

        return map;
    }


    /**
     * @param organId      机构代码
     * @param profession 专科代码
     * @param department   科室代码
     * @return List<Doctor> 医生列表
     * @throws
     * @Class eh.base.dao.DoctorDAO.java
     * @Title: findDoctorByThree
     * @Description: TODO 根据organId,professional,department 三个条件筛选医生 后面两个条件可空
     * @author Zhongzx
     * @Date 2015-12-28上午10:43:04
     */
    @RpcService
    public List<Doctor> findDoctorByThree(final Integer organId,
                                          final String profession, final Integer department) {
        if (organId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "organId is required");
        }
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "select d from Doctor d,Employment e where d.doctorId=e.doctorId and e.organId=:organId and e.primaryOrgan=1");
                if (!StringUtils.isEmpty(profession)) {
                    hql.append(" and d.profession=:profession");
                }
                if (department != null) {
                    hql.append(" and e.department=:department");
                }
                Query q = ss.createQuery(hql.toString());
                q.setParameter("organId", organId);
                if (!StringUtils.isEmpty(profession)) {
                    q.setParameter("profession", profession);
                }
                if (department != null) {
                    q.setParameter("department", department);
                }
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<Doctor> list = action.getResult();
        final Collator collator = Collator.getInstance(java.util.Locale.CHINA); // collator
        // 实现本地语言排序
        Collections.sort(list, new Comparator<Doctor>() {
            @Override
            public int compare(Doctor d1, Doctor d2) {
                // 正序
                return collator.compare(d1.getName(), d2.getName());
            }
        });
        return list;
    }

    /**
     * @param organId 机构代码
     * @param name    医生姓名 空则查询所有
     * @param flag    null或true 为正排 false为倒排
     * @return List<Doctor> 医生列表
     * @throws
     * @Class eh.base.dao.DoctorDAO.java
     * @Title: findDoctorByNameLike
     * @Description: TODO 按照医生姓氏正排或倒排，同时有名字模糊查询功能
     * @author Zhongzx
     * @Date 2015-12-28上午11:12:16
     */
    @RpcService
    public List<Doctor> findDoctorByNameLike(final Integer organId,
                                             final String name, final Boolean flag) {
        if (organId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "organId is needed");
        }
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "select d from Doctor d,Employment e where d.doctorId=e.doctorId and e.organId=:organId and e.primaryOrgan=1");
                if (!StringUtils.isEmpty(name)) {
                    hql.append(" and name like:name");
                }
                Query q = ss.createQuery(hql.toString());
                q.setParameter("organId", organId);
                if (!StringUtils.isEmpty(name)) {
                    q.setParameter("name", "%" + name + "%");
                }
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<Doctor> list = action.getResult();
        final Collator collator = Collator.getInstance(java.util.Locale.CHINA); // collator
        // 实现本地语言排序
        if ((flag == null) || (flag == true)) {
            Collections.sort(list, new Comparator<Doctor>() {
                @Override
                public int compare(Doctor d1, Doctor d2) {
                    // 正序
                    return collator.compare(d1.getName(), d2.getName());
                }
            });
        } else {
            Collections.sort(list, new Comparator<Doctor>() {
                @Override
                public int compare(Doctor d1, Doctor d2) {
                    // 倒序
                    return collator.compare(d2.getName(), d1.getName());
                }
            });
        }
        return list;
    }

    /**
     * 在线云门诊医生列表
     *
     * @param start 开始
     * @param limit 每页几条数据
     * @return
     * @desc 显示所有已获得机构授权的开启在线云门诊的医生的列表，医生排序根其状态（可呼叫优先，其次视频中，最后暂时离开）状态相同的医生根据职称优先的规则排序
     * @author zhangx
     * @date 2015-12-29 下午9:06:37
     * @desc 20160913 zhangx 新增小鱼视频平台，将原来的接口进行改造，重新封装
     */
    @RpcService
    public List<HashMap<String, Object>> findOnlineCloudClinicDoctorsLimit(
            final int start, final int limit) {

        UserRoleToken ur = (UserRoleToken) ContextUtils
                .get(Context.USER_ROLE_TOKEN);
        Employment emp = (Employment) ur.getProperty("employment");
        final Integer organ = emp.getOrganId();
        Integer doctorId = emp.getDoctorId();

        HibernateStatelessResultAction<List<Object[]>> action = new AbstractHibernateStatelessResultAction<List<Object[]>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws Exception {
                String uohql = "select organId from UnitOpauthorize where accreditOrgan=:organ";
                Query uoq = ss.createQuery(uohql);
                uoq.setParameter("organ",organ);
                List<Integer> organs = uoq.list();
                organs.add(organ);

                StringBuilder hql = new StringBuilder(
                        "select d,s from Doctor d, CloudClinicSet s where length(trim(d.mobile))>0 and s.platform in(:platforms) and  d.doctorId = s.doctorId and s.onLineStatus>0 and d.organ in(:organs) and (d.teams is null or d.teams=0) and (d.virtualDoctor is null or d.virtualDoctor=0) group by d.doctorId order by s.onLineStatus desc,s.factStatus,length(trim(d.proTitle)) desc,d.proTitle");

                List<String> platforms = new ArrayList<String>();
                platforms.add(CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_NGARI);
                platforms.add(CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_ANDROID);
                platforms.add(CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_IOS);

                Query q = ss.createQuery(hql.toString());
                q.setParameterList("organs", organs);
                q.setParameterList("platforms", platforms);
                q.setFirstResult(start);
                q.setMaxResults(limit);
                List<Object[]> list = q.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);

        List<Object[]> list = action.getResult();

        List<HashMap<String, Object>> mapList = new ArrayList<HashMap<String, Object>>();
        CloudClinicQueueDAO queueDAO = DAOFactory.getDAO(CloudClinicQueueDAO.class);
        List<Doctor> ds = new ArrayList<Doctor>();
        for (Object[] objects : list) {
            HashMap<String, Object> map = new HashMap<String, Object>();
            Doctor doctor = (Doctor) objects[0];
            map.put("doctor", doctor);
            map.put("cloudClinicSet", objects[1]);
            if (null != doctor && null != doctor.getDoctorId()) {
                Integer targetDoc = doctor.getDoctorId();
                List<CloudClinicQueue> queues = queueDAO.findAllQueueByTarget(targetDoc);
                int orderNum = -1;
                boolean myself = false;
                for (CloudClinicQueue queue : queues) {
                    Integer request = queue.getRequestDoctor();
                    orderNum++;
                    if (request.equals(doctorId)) {
                        myself = true;
                        break;
                    }
                }
                if (!myself) {
                    orderNum++;
                }
                map.put("orderNum", orderNum);
                map.put("isQueue", myself);
            }

            mapList.add(map);
            ds.add(doctor);
        }
        //从信令获取视频状态
        RTMService rtmService = AppDomainContext.getBean("eh.rtmService", RTMService.class);
        List<Map<String, Object>> statusList = rtmService.findStatusAndFactByDoctorIds(ds);
        for (int i = 0; i < mapList.size(); i++) {
            Integer fact = 0;
            if (statusList != null && statusList.size() > 0) {
                fact = Integer.valueOf((String) statusList.get(i).get("rtcBusy"));
            }
            ((CloudClinicSet) mapList.get(i).get("cloudClinicSet")).setFactStatus(fact);
        }
        return mapList;
    }

    /**
     * 在线云门诊医生列表-机构筛选
     *
     * @param organId 所选机构内码
     * @param name    搜索姓名
     * @param start   开始
     * @param limit   每页几条数据
     * @return
     */
    @RpcService
    public List<HashMap<String, Object>> findOnlineDoctorsWithOrgan(
            final int organId, final String name, final int start, final int limit) {

        UserRoleToken ur = (UserRoleToken) ContextUtils
                .get(Context.USER_ROLE_TOKEN);
        Employment emp = (Employment) ur.getProperty("employment");
        final Integer organ = emp.getOrganId();
        Integer doctorId = emp.getDoctorId();

        HibernateStatelessResultAction<List<Object[]>> action = new AbstractHibernateStatelessResultAction<List<Object[]>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "select distinct d,s from Doctor d, CloudClinicSet s, UnitOpauthorize u where ");
                if (organId > 0) {
                    hql.append("d.organ=:organId and ");
                }
                if (name != null && !StringUtils.isEmpty(name)) {
                    hql.append("d.name=:name and ");
                }
                hql.append("length(trim(d.mobile))>0 and s.platform in(:platforms) and d.doctorId=s.doctorId and s.onLineStatus>0 and ((u.accreditOrgan=:organ and d.organ=u.organId) or d.organ=:organ) and (d.teams is null or d.teams=0) and (d.virtualDoctor is null or d.virtualDoctor=0) GROUP BY d.doctorId order by s.onLineStatus desc,s.factStatus,length(trim(d.proTitle)) desc,d.proTitle");

                List<String> platforms = new ArrayList<String>();
                platforms.add(CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_NGARI);
                platforms.add(CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_ANDROID);
                platforms.add(CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_IOS);

                Query q = ss.createQuery(hql.toString());
                q.setParameter("organ", organ);
                if (organId > 0) {
                    q.setParameter("organId", organId);
                }
                if (name != null && !StringUtils.isEmpty(name)) {
                    q.setParameter("name", name);
                }
                q.setParameterList("platforms", platforms);
                q.setFirstResult(start);
                q.setMaxResults(limit);
                List<Object[]> list = q.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);

        List<Object[]> list = action.getResult();

        List<HashMap<String, Object>> mapList = new ArrayList<HashMap<String, Object>>();
        CloudClinicQueueDAO queueDAO = DAOFactory.getDAO(CloudClinicQueueDAO.class);
        List<Doctor> ds = new ArrayList<Doctor>();
        for (Object[] objects : list) {
            HashMap<String, Object> map = new HashMap<String, Object>();
            Doctor doctor = (Doctor) objects[0];
            map.put("doctor", doctor);
            map.put("cloudClinicSet", objects[1]);
            if (null != doctor && null != doctor.getDoctorId()) {
                Integer targetDoc = doctor.getDoctorId();
                List<CloudClinicQueue> queues = queueDAO.findAllQueueByTarget(targetDoc);
                int orderNum = -1;
                boolean myself = false;
                for (CloudClinicQueue queue : queues) {
                    Integer request = queue.getRequestDoctor();
                    orderNum++;
                    if (request.equals(doctorId)) {
                        myself = true;
                        break;
                    }
                }
                if (!myself) {
                    orderNum++;
                }
                map.put("orderNum", orderNum);
                map.put("isQueue", myself);
            }

            mapList.add(map);
            ds.add(doctor);
        }
        //从信令获取视频状态
        RTMService rtmService = AppDomainContext.getBean("eh.rtmService", RTMService.class);
        List<Map<String, Object>> statusList = rtmService.findStatusAndFactByDoctorIds(ds);
        for (int i = 0; i < mapList.size(); i++) {
            Integer fact = 0;
            if (statusList != null && statusList.size() > 0) {
                fact = Integer.valueOf((String) statusList.get(i).get("rtcBusy"));
            }
            ((CloudClinicSet) mapList.get(i).get("cloudClinicSet")).setFactStatus(fact);
        }
        return mapList;
    }

    /**
     * 在线云门诊医生列表
     *
     * @param start 开始
     * @return
     * @desc 显示所有已获得机构授权的开启在线云门诊的医生的列表，医生排序根其状态（可呼叫优先，其次视频中，最后暂时离开）状态相同的医生根据职称优先的规则排序
     * @author zhangx
     * @date 2015-12-29 下午9:06:37
     */
    @RpcService
    public List<HashMap<String, Object>> findOnlineCloudClinicDoctors(int start) {
        return findOnlineCloudClinicDoctorsLimit(start, 10);
    }

    /**
     * @param organId 机构代码 可空
     * @param name    医生姓名 可空
     * @param mobile  手机号码 可空
     * @return List<Doctor>
     * @throws
     * @Class eh.base.dao.DoctorDAO.java
     * @Title: findDoctorByThreeLike
     * @Description: TODO 停诊通知里的医生条件查询
     * @author Zhongzx
     * @Date 2016-1-5下午5:41:23
     */
    @RpcService
    public List<Doctor> findDoctorByThreeLike(final Integer organId,
                                              final String name, final String mobile) {
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "select d from Doctor d,Employment e where d.doctorId=e.doctorId");
                if (null != organId) {
                    hql.append(" and e.organId=:organId");
                }
                if (!StringUtils.isEmpty(name)) {
                    hql.append(" and d.name like:name");
                }
                if (!StringUtils.isEmpty(mobile)) {
                    hql.append(" and d.mobile like:mobile");
                }
                Query q = ss.createQuery(hql.toString());
                if (null != organId) {
                    q.setParameter("organId", organId);
                }
                if (!StringUtils.isEmpty(name)) {
                    q.setParameter("name", "%" + name + "%");
                }
                if (!StringUtils.isEmpty(mobile)) {
                    q.setParameter("mobile", "%" + mobile + "%");
                }
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * @param organId 机构代码
     * @param deptId  科室代码
     * @return List<Doctor>
     * @throws
     * @Class eh.base.dao.DoctorDAO.java
     * @Title: findDocs
     * @Description: TODO 根据机构、科室查询医生列表（只获取医生的doctorID和名字）
     * @author Zhongzx
     * @Date 2016-1-6下午2:48:56
     */
    @RpcService
    public List<Doctor> findDocs(final Integer organId, final Integer deptId) {
        if (null == organId || null == deptId) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "organId and deptId are needed");
        }
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {

            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                // TODO Auto-generated method stub
                String hql = "select new eh.entity.base.Doctor(d.doctorId,d.name) from Doctor d ,Employment e where d.doctorId=e.doctorId and e.organId=:organId and e.department=:deptId and e.primaryOrgan=1";
                Query q = ss.createQuery(hql);
                q.setParameter("organId", organId);
                q.setParameter("deptId", deptId);

                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 有效医生及urtId列表查询
     *
     * @param ids 医生内码列表
     * @return List<Doctor>
     * @author luf
     */
    @RpcService
    public List<Doctor> findEffDoctorAndUrt(List<Integer> ids) {
        List<Doctor> results = new ArrayList<Doctor>();
        List<Doctor> ds = this.findEffectiveDocByDoctorIdIn(ids);
        UserSevice us = new UserSevice();
        for (Doctor d : ds) {
            if (!StringUtils.isEmpty(d.getMobile())) {
                int urtId = us.getUrtIdByUserId(d.getMobile(), "doctor");
                d.setUrtId(urtId);
            }
            results.add(d);
        }
        return results;
    }

    @RpcService
    // @DAOMethod(limit =10000)
    public List<Doctor> findDoctorIdAndName(Integer organId, Integer deptId) {
        List<String> fields = ImmutableList.of("doctorId", "name");
        QueryContext qc = new QueryContext();
        qc.addParam("organ", organId);
        // qc.addParam("deptId",deptId);
        qc.setFields(fields);
        return find(qc).getItems();
    }

    /**
     * 登陆后的推荐医生-新（首页）
     * <p>
     * eh.base.dao
     *
     * @param homeArea      属地区域
     * @param age           患者年龄
     * @param patientGender 患者性别 --1男2女
     * @return Map<String, List<Doctor>>
     * @throws ControllerException
     * @author luf 2016-2-25
     */
    @RpcService
    public Map<String, Object> doctorsRecommendedNew(String homeArea,
                                                     int age, String patientGender) throws ControllerException {
        if (homeArea == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "homeArea is required");
        }
        if (StringUtils.isEmpty(patientGender)) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "patientGender is required");
        }
        List<Doctor> list = new ArrayList<Doctor>();
        List<String> professions = this.doctorsRecommended(age, patientGender);
        String addrArea = "0000000";
        for (String profession : professions) {
            HashMap<String, Object> os = getDoctorListWithWhile(homeArea, profession, 4);
            if (os == null || os.get("list") == null) {
                continue;
            }
            list.addAll((List<Doctor>) os.get("list"));
            String addr = (String) os.get("home");
            if (addr.length() < addrArea.length()) {
                addrArea = addr;
            }
        }
        Map<String, List<Doctor>> map = new HashMap<String, List<Doctor>>();
        for (Doctor doctor : list) {
            String profession = doctor.getProfession();
            Doctor d = new Doctor();
            d.setDoctorId(doctor.getDoctorId());
            d.setName(doctor.getName());
            d.setPhoto(doctor.getPhoto());
            d.setProTitle(doctor.getProTitle());
            d.setGender(doctor.getGender());
            if (!profession.subSequence(0, 2).equals("05")) {
                profession = (String) profession.subSequence(0, 2);
            }
            d.setProfession(profession);
            d.setTeams(doctor.getTeams());
            String professionName = DictionaryController.instance()
                    .get("eh.base.dictionary.Profession").getText(profession);
            if (map.containsKey(professionName)) {
                map.get(professionName).add(d);
            } else {
                List<Doctor> ds = new ArrayList<Doctor>();
                ds.add(d);
                map.put(professionName, ds);
            }
        }
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("doctors", map);
        result.put("addrArea", addrArea);
        result.put("addrAreaText", DictionaryController.instance()
                .get("eh.base.dictionary.AddrArea")
                .getText(addrArea));
        return result;
    }


    /**
     * 查询历史医生（或推荐）医生给患者
     *
     * @param homeArea
     * @param age
     * @param patientGender
     * @param flag
     * @return
     */
    @RpcService
    public List<HashMap<String, Object>> recommendDoctors(
            String homeArea, int age, String patientGender, int flag) {
        UserRoleToken urt = UserRoleToken.getCurrent();
        Patient patient = (Patient) urt.getProperty("patient");
        String mpiId = patient.getMpiId();
        log.info(LocalStringUtil.format("[{}] recommendDoctors with params: homeArea[{}], age[{}], patientGender[{}], flag[{}], mpiId[{}]", this.getClass().getSimpleName(), homeArea, age, patientGender, flag, mpiId));
        List<HashMap<String, Object>> hisDoctors = DAOFactory.getDAO(OperationRecordsDAO.class).findDocByMpiIdForHealth(mpiId);
        if (ValidateUtil.blankList(hisDoctors)) {
            hisDoctors = this.consultOrAppointRecommended(homeArea, age, patientGender, flag);
        }
        return hisDoctors;
    }


    /**
     * 推荐医生-找医生页面
     * <p>
     * eh.base.dao
     *
     * @param homeArea
     * @param age
     * @param patientGender
     * @param flag          标志-0咨询1预约
     * @return List<Object>
     * @author luf 2016-2-25
     */
    @RpcService
    public List<HashMap<String, Object>> consultOrAppointRecommended(
            String homeArea, int age, String patientGender, int flag) {
        if (homeArea == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "homeArea is required");
        }
        if (homeArea.length() < 6) {
            StringUtils.rightPad(homeArea, 6, '0');
        }
        if (StringUtils.isEmpty(patientGender)) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "patientGender is required");
        }
        List<Doctor> list = new ArrayList<Doctor>();
        List<String> professions = this.doctorsRecommended(age, patientGender);
        for (String profession : professions) {
            list.addAll(getDoctorListWithWhile2(homeArea, profession, flag, 2));
        }
        List<HashMap<String, Object>> targets = new ArrayList<HashMap<String, Object>>();
        for (Doctor doctor : list) {
            HashMap<String, Object> result = new HashMap<String, Object>();
            int doctorId = doctor.getDoctorId();
            DoctorDAO doctorDao = DAOFactory.getDAO(DoctorDAO.class);
            doctor.setHaveAppoint(doctorDao.getRealTimeDoctorHaveAppointStatus(doctorId, 1).getHaveAppoint());
            ConsultSetDAO dao = DAOFactory.getDAO(ConsultSetDAO.class);
            ConsultSet consultSet = dao.get(doctorId);
            Employment employment = DAOFactory.getDAO(EmploymentDAO.class).getPrimaryEmpByDoctorId(doctorId);
            doctor.setDepartment(employment.getDepartment());
            result.put("doctor", doctor);
            result.put("consultSet", consultSet);
            targets.add(result);
        }
        return targets;
    }


    /**
     * 供consultOrAppointRecommended调用
     * <p>
     * eh.base.dao
     *
     * @param homeArea
     * @param profession
     * @param flag       标志-0咨询1预约
     * @param num
     * @return List<Doctor>
     * @author luf 2016-2-26
     */
    public List<Doctor> getDoctorListWithWhile2(String homeArea, String profession, int flag, int num) {
        List<Doctor> list = new ArrayList<Doctor>();
        int j = 0;
        while (j <= 2) {
            //2016-4-27 luf:最高查询到省级
            int count = 6;
            count = count - j * 2;
            homeArea = homeArea.substring(0, count);
            List<Doctor> ds = this.findBussDoctorByTwoLike(homeArea, profession, flag, num);
            if (ds.size() == num) {
                list.addAll(ds);
                break;
            }
            if (j == 2) {
                list.addAll(ds);
            }
            j++;
        }
        return list;
    }

    /**
     * 获取咨询首页专家团队医生列表,搜索开通图文咨询的医生团队,还要根据地址和运营平台配置来判断获取
     *
     * @param addrArea 地址的码
     * @param start    起始页
     * @param limit    限制条数
     * @return
     * @author cuill
     * @date 2017/7/7
     */
    public List<Doctor> findDoctorTeamByHomeArea(final String addrArea, final int start, final int limit) {
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
                List<Integer> organs = organDAO.findOrgansByUnitForHealth();
                StringBuilder hql = new StringBuilder("select DISTINCT d From Doctor d,Organ o,Scratchable s,ConsultSet c,DoctorStatistic ds  where s.boxLink = d.doctorId and" +
                        " d.organ=o.organId and c.doctorId = d.doctorId and d.doctorId = ds.doctorId and d.status=1 and d.teams = 1 and s.configType = 'doctorGroup' and c.onLineStatus = 1 and o.addrArea like :addrArea ");
                if (organs != null && organs.size() > 0) {
                    hql.append(" and (");
                    for (Integer i : organs) {
                        hql.append("d.organ=").append(i).append(" or ");
                    }
                    hql = new StringBuilder(hql.substring(0, hql.length() - 3)).append(")");
                }
                hql.append(" order by d.consultAmount DESC, ds.attentionCount DESC");
                Query query = statelessSession.createQuery(hql.toString());
                query.setParameter("addrArea", addrArea + "%");
                query.setFirstResult(start);
                query.setMaxResults(limit);
                setResult(query.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }
    /**
     * 供getDoctorListWithWhile2调用
     * <p>
     * eh.base.dao
     *
     * @param addrArea
     * @param profession
     * @param flag       标志-0咨询1预约
     * @param max
     * @return List<Doctor>
     * @author luf 2016-2-25
     * @Date 2016年11月14日 15:32:58 zhangsl 修改按评分高低排序
     */
    public List<Doctor> findBussDoctorByTwoLike(final String addrArea,
                                                final String profession, final int flag, final int max) {
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
                List<Integer> organs = organDAO.findOrgansByUnitForHealth();
                StringBuffer hql = new StringBuffer(
                        "select d From Doctor d,Organ o,ConsultSet c where o.addrArea like :addrArea and "
                                + "d.organ=o.organId and c.doctorId=d.doctorId and d.status=1 and "
                                // 2016-3-5 luf：推荐医生里面不显示团队医生去掉 or d.teams=1
                                // 2016-4-25 luf：添加个性化  and (d.organ in :organs)
                                + "d.profession like :profession");
                if (organs != null && organs.size() > 0) {
                    hql.append(" and (");
                    for (Integer i : organs) {
                        hql.append("d.organ=").append(i).append(" or ");
                    }
                    hql = new StringBuffer(hql.substring(0, hql.length() - 3)).append(")");
                }
                hql.append(" and (d.idNumber is not null and d.idNumber<>:empty) and(");
                //2016-7-15 luf:业务设置为null是排序出错bug修复，添加IFNULL(,0)
                if (flag == 0) {
                    hql.append("c.onLineStatus=1 or c.appointStatus=1) order by "
                            + "(IFNULL(c.onLineStatus,0)+IFNULL(c.appointStatus,0)) DESC,d.rating DESC");
                } else {
                    hql.append("d.haveAppoint=1 or c.patientTransferStatus=1) order by "
                            + "(IFNULL(d.haveAppoint,0)+IFNULL(c.patientTransferStatus,0)) DESC,d.rating DESC");
                }
                Query q = ss.createQuery(hql.toString());
                q.setParameter("addrArea", addrArea + "%");
                q.setParameter("profession", profession + "%");
                q.setParameter("empty", "");
                q.setMaxResults(max);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 未登录状态下推荐医生-找医生
     * <p>
     * eh.base.dao
     *
     * @param homeArea 属地区域
     * @param flag     标志-0咨询1预约
     * @return List<HashMap<String,Object>>
     * @author luf 2016-2-29
     */
    @RpcService
    public List<HashMap<String, Object>> doctorsRecommendedUnLogin2(
            String homeArea, int flag) {
        if (StringUtils.isEmpty(homeArea)) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "homeArea is required!");
        }
        List<Doctor> list = new ArrayList<Doctor>();
        for (int i = 2; i < 5; i++) {
            String profession = "0" + i; // 02 03 04
            list.addAll(getDoctorListWithWhile2(homeArea, profession, flag, 2));
        }
        List<HashMap<String, Object>> targets = new ArrayList<HashMap<String, Object>>();
        for (Doctor doctor : list) {
            HashMap<String, Object> result = new HashMap<String, Object>();
            int doctorId = doctor.getDoctorId();
            DoctorDAO doctorDao = DAOFactory.getDAO(DoctorDAO.class);
            doctor.setHaveAppoint(doctorDao.getRealTimeDoctorHaveAppointStatus(doctorId, 1).getHaveAppoint());
            ConsultSetDAO dao = DAOFactory.getDAO(ConsultSetDAO.class);
            ConsultSet consultSet = dao.get(doctorId);
            result.put("doctor", doctor);
            result.put("consultSet", consultSet);
            targets.add(result);
        }
        return targets;
    }

    /**
     * 患者端查看医生信息(未登陆)
     * <p>
     * eh.base.dao
     *
     * @param doctorId 医生内码
     * @return Doctor
     * @author luf 2016-3-9
     */
    @RpcService
    public Doctor getDoctorInfoUnloginForHealth(int doctorId) {
        Doctor doc = this.getByDoctorId(doctorId);

        if (doc == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "医生" + doctorId
                    + "不存在");
        }

        EmploymentDAO empDao = DAOFactory.getDAO(EmploymentDAO.class);
        Employment emp = empDao.getPrimaryEmpByDoctorId(doctorId);
        doc.setDepartment(emp.getDepartment());

        // 获取医生关注数(患者关注+医生关注)
        doc.setRelationNum(doctorRelationNumber(doctorId));
        return doc;
    }

    /**
     * 搜素医生优化（专科、区域、擅长疾病、姓名、是否在线、是否有号[有号，不限]、职称）-原生端
     * <p>
     * -A授权给B，B能看到A，A不能看到B，按姓名搜索时，业务开通不限制
     * <p>
     * eh.base.dao
     *
     * @param profession   专科编码
     * @param addrArea     属地区域
     * @param domain       擅长领域
     * @param name         医生姓名
     * @param onLineStatus 在线状态̬
     * @param haveAppoint  预约号源标志
     * @param startPage    起始页
     * @param busId        业务类型--1、转诊 2、会诊 3、咨询 4、预约
     * @param proTitle     职称
     * @return List<Doctor>
     * @author luf 2016-3-10
     */
    @RpcService
    @SuppressWarnings({"unchecked"})
    public List<Doctor> searchDoctorBussNameAll(final String profession,
                                                final String addrArea, final String domain, final String name,
                                                final Integer onLineStatus, final Integer haveAppoint,
                                                final int startPage, final int busId, final String proTitle) {
        UserRoleToken ur = (UserRoleToken) ContextUtils
                .get(Context.USER_ROLE_TOKEN);
        Employment eSelf = (Employment) ur.getProperty("employment");
        List<Doctor> docList = new ArrayList<Doctor>();
        UnitOpauthorizeDAO dao = DAOFactory.getDAO(UnitOpauthorizeDAO.class);
        List<Integer> oList = dao.findByBussId(busId);
        if (oList == null) {
            oList = new ArrayList<Integer>();
        }
        if (eSelf == null || eSelf.getOrganId() == null) {
            return docList;
        }
        oList.add(eSelf.getOrganId());
        StringBuilder sb = new StringBuilder(" and(");
        for (Integer o : oList) {
            sb.append(" e.organId=").append(o).append(" OR");
        }
        final String strUO = sb.substring(0, sb.length() - 2) + ")";

        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            List<Doctor> list = null;

            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "select distinct d from Doctor d,Organ o,Employment e,ConsultSet c,UnitOpauthorize u "
                                + "where o.organId=e.organId and d.doctorId=e.doctorId "
                                + "and d.doctorId=c.doctorId and d.status=1 ");
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
                    }
                }

                if (!StringUtils.isEmpty(profession)) {
                    hql.append(" and d.profession like :profession");
                }
                if (!StringUtils.isEmpty(addrArea)) {
                    hql.append(" and o.addrArea like :addrArea");
                }
                if (!StringUtils.isEmpty(domain)) {
                    hql.append(" and d.domain like:domain");
                }
                if (!StringUtils.isEmpty(name)) {
                    hql.append(" and d.name like:name");
                }
                if (onLineStatus != null) {
                    hql.append(" and c.onLineStatus=:onLineStatus");
                }
                if (haveAppoint != null && haveAppoint != 2) {
                    hql.append(" and d.haveAppoint=:haveAppoint");
                }
                if (!StringUtils.isEmpty(proTitle)) {
                    hql.append(" and d.proTitle=:proTitle");
                }

                Query q = ss.createQuery(hql.toString());
                if (!StringUtils.isEmpty(profession)) {
                    q.setParameter("profession", profession + "%");
                }
                if (!StringUtils.isEmpty(addrArea)) {
                    q.setParameter("addrArea", addrArea + "%");
                }
                if (!StringUtils.isEmpty(domain)) {
                    q.setParameter("domain", "%" + domain + "%");
                }
                if (!StringUtils.isEmpty(name)) {
                    q.setParameter("name", "%" + name + "%");
                }
                if (onLineStatus != null) {
                    q.setParameter("onLineStatus", onLineStatus);
                }
                if (haveAppoint != null && haveAppoint != 2) {
                    q.setParameter("haveAppoint", haveAppoint);
                }
                if (!StringUtils.isEmpty(proTitle)) {
                    q.setParameter("proTitle", proTitle);
                }
                q.setMaxResults(10);
                q.setFirstResult(startPage);
                list = q.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        docList = action.getResult();
        if ( docList == null || docList.size() == 0 ) {
            return new ArrayList<Doctor>();
        }

        List<Doctor> targets = new ArrayList<Doctor>();
        EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
        ConsultSetDAO csDao = DAOFactory.getDAO(ConsultSetDAO.class);
        for (Doctor doctor : docList) {
            Integer docId = doctor.getDoctorId();
            Employment employment = employmentDAO
                    .getPrimaryEmpByDoctorId(docId);
            if (employment != null) {
                doctor.setDepartment(employment.getDepartment());
            }
            ConsultSet cs = csDao.get(docId);
            Integer isOpen = 0;
            if (cs != null) {
                switch (busId) {
                    case 1:
                        isOpen = cs.getTransferStatus();
                        break;
                    case 2:
                        isOpen = cs.getMeetClinicStatus();
                        break;
                    case 3:
                    case 4:
                        if ((cs.getOnLineStatus() != null && cs.getOnLineStatus() == 1) || (cs.getAppointStatus() != null && cs.getAppointStatus() == 1)) {
                            isOpen = 1;
                        }
                }
            }
            doctor.setIsOpen(isOpen);
            targets.add(doctor);
        }

        return targets;
    }

    /**
     * 获取医生部分信息，主要用于getAppointRecordInfoById
     *
     * @param doctorId
     * @return
     * @author zhangx
     * @date 2016-3-11 下午4:57:19
     */
    public Doctor getDoctorPartInfo(Integer doctorId) {
        Doctor doc = new Doctor();

        Doctor d = get(doctorId);
        if (d != null) {
            doc.setDoctorId(doctorId);
            doc.setGender(d.getGender());
            doc.setName(d.getName());
            doc.setMobile(d.getMobile());
            doc.setPhoto(d.getPhoto());
            doc.setProTitle(d.getProTitle());
            doc.setOrgan(d.getOrgan());
            doc.setProfession(d.getProfession());
        }
        return doc;
    }

    /**
     * 查找有云诊室排班的医生列表
     *
     * @param bus   业务类型-1转诊2会诊3咨询4预约
     * @param start 页面开始位置
     * @param limit 每页限制条数
     * @return ArrayList<Doctor>
     * @Date 2016年11月14日 15:32:58 zhangsl 修改按评分高低排序
     */
    @RpcService
    public List<Doctor> doctorsWithClinic(int bus, final int start, final int limit) {
        List<Doctor> docList = new ArrayList<Doctor>();
        UserRoleToken ur = (UserRoleToken) ContextUtils
                .get(Context.USER_ROLE_TOKEN);
        Doctor doctor = (Doctor) ur.getProperty("doctor");
        UnitOpauthorizeDAO uoDao = DAOFactory.getDAO(UnitOpauthorizeDAO.class);
        List<Integer> oList = uoDao.findByBussId(bus);
        if (oList == null) {
            oList = new ArrayList<Integer>();
        }
        if (doctor == null || doctor.getOrgan() == null) {
            return docList;
        }
        final int doctorId = doctor.getDoctorId();
        int organId = doctor.getOrgan();
        oList.add(organId);
        final List<Integer> organs = oList;
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "SELECT DISTINCT d FROM Doctor d WHERE d.organ in(:organs) AND d.doctorId<>:doctorId AND((SELECT COUNT(*) FROM AppointSource s WHERE s.doctorId=d.doctorId AND s.cloudClinic=1 AND s.cloudClinicType=1 AND s.workDate>NOW() AND (s.sourceNum-s.usedNum)>0 AND s.stopFlag=0)>0 OR ((SELECT COUNT(*) FROM AppointSource ap WHERE ap.doctorId=d.doctorId AND ap.cloudClinic=1 AND ap.cloudClinicType=1 AND ap.workDate=DATE_FORMAT(NOW(),'%y-%m-%d') AND ap.workType=1 AND (ap.sourceNum-ap.usedNum)>0 AND ap.startTime>:startTime AND ap.stopFlag=0)>0 AND(SELECT COUNT(*) FROM AppointSource app WHERE app.doctorId=d.doctorId AND app.cloudClinic=1 AND app.cloudClinicType=1 AND app.workDate=DATE_FORMAT(NOW(),'%y-%m-%d') AND app.workType=1 AND app.usedNum>0 AND app.stopFlag=0)>0) OR ((SELECT COUNT(*) FROM AppointSource aps WHERE aps.doctorId=d.doctorId AND aps.cloudClinic=1 AND aps.cloudClinicType=1 AND aps.workDate=DATE_FORMAT(NOW(),'%y-%m-%d') AND aps.workType=2 AND (aps.sourceNum-aps.usedNum)>0 AND aps.startTime>:startTime AND aps.stopFlag=0)>0 AND (SELECT COUNT(*) FROM AppointSource apps WHERE apps.doctorId=d.doctorId AND apps.cloudClinic=1 AND apps.cloudClinicType=1 AND apps.workDate=DATE_FORMAT(NOW(),'%y-%m-%d') AND apps.workType=2 AND apps.usedNum>0 AND apps.stopFlag=0)>0)) ORDER BY LENGTH(TRIM(d.proTitle)) DESC,d.proTitle,d.rating DESC");
                Query q = ss.createQuery(hql.toString());
                q.setParameterList("organs", organs);
                q.setParameter("doctorId", doctorId);
                //DATE_ADD(NOW(),INTERVAL 1 DAY_HOUR)
                q.setParameter("startTime", DateConversion.getDateAftHour(new Date(), 1));
                q.setFirstResult(start);
                q.setMaxResults(limit);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<Doctor> ds = action.getResult();
        List<Doctor> targets = new ArrayList<Doctor>();
        if (ds == null || ds.size() <= 0) {
            return targets;
        }

        EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
        for (Doctor d : ds) {
            Employment employment = employmentDAO.getPrimaryEmpByDoctorId(d.getDoctorId());
            if (employment != null) {
                d.setDepartment(employment.getDepartment());
            }
            targets.add(d);
        }
        return targets;
    }

    /**
     * 获取医生二维码的阿里云存放地址（如果没有生成二维码，先生成）
     * zhongzx
     *
     * @param doctorId
     * @return
     */
    @RpcService
    public int getTicketAndUrlByDoctorId(int doctorId) {
        log.info("doctorId================" + doctorId);
        Doctor doctor = getByDoctorId(doctorId);
        if (doctor == null) {
            throw new DAOException(609, doctorId + "医生不存在");
        }
        if (doctor.getQrCode() == null || StringUtils.isEmpty(doctor.getQrUrl())) {
            IWXServiceInterface wxService = AppContextHolder.getBean("eh.wxService", IWXServiceInterface.class);
            log.info("doctorId================" + doctorId);
            HashMap<String, String> reMap = wxService.getTicketAndUrl(doctorId);
            String qrUrl = reMap.get("url");
            String ticket = reMap.get("ticket");
            log.info("qrUrl==================" + qrUrl);
            log.info("ticket====================" + ticket);
            Integer fileId = UploadFile.uploadImage(ticket, doctorId);
            log.info("fileId========================" + fileId);
            if (fileId == null) {
                throw new DAOException(609, "图片上传失败");
            }
            reMap.put("qrCode", fileId + "");

            doctor.setQrCode(fileId);
            doctor.setQrUrl(qrUrl);
            update(doctor);

            QRInfoService qRInfoService = AppContextHolder.getBean("eh.qrInfoService", QRInfoService.class);
            qRInfoService.saveDocQRInfo(reMap);
        }


        return doctor.getQrCode();
    }


    /**
     * 根据qrRUrl查询医生
     * zhongzx
     *
     * @param qrUrl
     * @return
     */
    @RpcService
    @DAOMethod
    public abstract Doctor getByQrUrl(String qrUrl);

    /**
     * 根据qrUrl查询医生doctorId
     * zhongzx
     *
     * @param qrUrl
     * @return
     */
    @RpcService
    public Integer getDoctorIdByQrUrl(String qrUrl) {
        if (StringUtils.isEmpty(qrUrl)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "url is needed");
        }
        Doctor d = getByQrUrl(qrUrl);
        if (d == null) {
            throw new DAOException(609, "此医生不存在");
        }
        return d.getDoctorId();
    }


    /**
     * 获取医生所在机构查询机构是否设置有自己的微信公众号
     *
     * @param doctorId
     * @return
     */
    @RpcService
    public WXConfig getOrganWxConfigByDoctorId(int doctorId) {
        OrganConfigDAO organConfigDAO = DAOFactory.getDAO(OrganConfigDAO.class);
        WXConfigsDAO wxConfigsDAO = DAOFactory.getDAO(WXConfigsDAO.class);
        Doctor doctor = getByDoctorId(doctorId);
        OrganConfig OrganConfig = organConfigDAO.getByOrganId(doctor.getOrgan());
        if (!ObjectUtils.isEmpty(OrganConfig.getWxConfigId())) { //医生所在机构有自己的微信公众号
            return wxConfigsDAO.getById(OrganConfig.getWxConfigId());
        }
        return null;
    }


    /**
     * 搜素医生优化（专科、区域、擅长疾病、姓名、是否在线、是否有号[有号，不限]、职称）-原生端-纯sql
     * <p>
     * -A授权给B，B能看到A，A不能看到B，按姓名搜索时，业务开通不限制
     * <p>
     * eh.base.dao
     *
     * @param profession   专科编码
     * @param addrArea     属地区域
     * @param domain       擅长领域
     * @param name         医生姓名
     * @param onLineStatus 在线状态̬
     * @param haveAppoint  预约号源标志
     * @param startPage    起始页
     * @param limit        每页限制条数
     * @param busId        业务类型--1、转诊 2、会诊 3、咨询 4、预约
     * @param proTitle     职称
     * @param strUO        授权机构
     * @return List<Doctor>
     * @author luf 2016-3-10
     */
    public List<Integer> searchDoctorBussNameAllSql(final String profession,
                                                    final String addrArea, final String domain, final String name,
                                                    final Integer onLineStatus, final Integer haveAppoint,
                                                    final int startPage, final int limit, final int busId,
                                                    final String proTitle, final String strUO) {
        HibernateStatelessResultAction<List<Integer>> action = new AbstractHibernateStatelessResultAction<List<Integer>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "select distinct d.doctorId from Doctor d,Organ o,Employment e,ConsultSet c,UnitOpauthorize u "
                                + "where o.organId=e.organId and d.doctorId=e.doctorId "
                                + "and d.doctorId=c.doctorId and d.status=1 ");
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
                    }
                }
                if (!StringUtils.isEmpty(profession)) {
                    hql.append(" and d.profession like :profession");
                }
                if (!StringUtils.isEmpty(addrArea)) {
                    hql.append(" and o.addrArea like :addrArea");
                }
                if (!StringUtils.isEmpty(domain)) {
                    hql.append(" and d.domain like:domain");
                }
                if (!StringUtils.isEmpty(name)) {
                    hql.append(" and d.name like:name");
                }
                if (onLineStatus != null) {
                    hql.append(" and c.onLineStatus=:onLineStatus");
                }
                if (haveAppoint != null && haveAppoint != 2) {
                    hql.append(" and d.haveAppoint=:haveAppoint");
                }
                if (!StringUtils.isEmpty(proTitle)) {
                    hql.append(" and d.proTitle=:proTitle");
                }
                Query q = ss.createQuery(hql.toString());
                if (!StringUtils.isEmpty(profession)) {
                    q.setParameter("profession", profession + "%");
                }
                if (!StringUtils.isEmpty(addrArea)) {
                    q.setParameter("addrArea", addrArea + "%");
                }
                if (!StringUtils.isEmpty(domain)) {
                    q.setParameter("domain", "%" + domain + "%");
                }
                if (!StringUtils.isEmpty(name)) {
                    q.setParameter("name", "%" + name + "%");
                }
                if (onLineStatus != null) {
                    q.setParameter("onLineStatus", onLineStatus);
                }
                if (haveAppoint != null && haveAppoint != 2) {
                    q.setParameter("haveAppoint", haveAppoint);
                }
                if (!StringUtils.isEmpty(proTitle)) {
                    q.setParameter("proTitle", proTitle);
                }
                q.setMaxResults(limit);
                q.setFirstResult(startPage);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    @RpcService
    @DAOMethod(sql = "SELECT count(*) FROM Doctor d WHERE d.doctorId in (select e.doctorId from Employment e where e.organId =:organId )")
    public abstract Long getCountDoctorByOrganId(@DAOParam("organId") Integer organId);


    @DAOMethod(sql = "SELECT count(*) FROM Doctor d WHERE d.status=1 and d.doctorId in (select e.doctorId from Employment e where e.organId =:organId )")
    public abstract Long getNormalCountDoctorByOrganId(@DAOParam("organId") Integer organId);


    /**
     * 查询医生信息和医生执业信息服务 医生审核状态为 1审核通过，状态正常 9注销
     *
     * @param name
     * @param idNumber
     * @param organ
     * @param start
     * @param limit
     * @return docList
     * @author houxr
     */
    @RpcService
    @SuppressWarnings({"unchecked"})
    public List<Doctor> queryDoctorAndEmploymentForOP(final String name,
                                                      final String idNumber, final Integer organ,
                                                      final String profession, final Integer department,
                                                      final Integer start, final Integer limit) {

        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "select DISTINCT a from Doctor a,Employment b "
                                + "where a.doctorId=b.doctorId and a.status in (1,9)");
                if (!StringUtils.isEmpty(name)) {
                    hql.append(" and a.name like :name");
                }
                if (!StringUtils.isEmpty(idNumber)) {
                    hql.append(" and a.idNumber like :idNumber");
                }
                if (organ != null) {
                    hql.append(" and b.organId=:organ");
                }
                if (!StringUtils.isEmpty(profession)) {
                    hql.append(" and a.profession like :profession");
                }
                if (department != null) {
                    hql.append(" and b.department=:department");
                }

                Query q = ss.createQuery(hql.toString());
                if (!StringUtils.isEmpty(name)) {
                    q.setString("name", "%" + name + "%");
                }
                if (!StringUtils.isEmpty(idNumber)) {
                    q.setParameter("idNumber", "%" + idNumber + "%");
                }
                if (organ != null) {
                    q.setParameter("organ", organ);
                }
                if (!StringUtils.isEmpty(profession)) {
                    q.setParameter("profession", profession + "%");
                }
                if (department != null) {
                    q.setParameter("department", department);
                }
                if (start != null) {
                    q.setMaxResults(limit);
                    q.setFirstResult(start);
                }
                List<Doctor> doctors = q.list();
                setResult(doctors);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<Doctor> docs = action.getResult();
        if (docs == null || docs.size() <= 0) {
            return new ArrayList<Doctor>();
        }
        EmploymentDAO dao = DAOFactory.getDAO(EmploymentDAO.class);
        for (int i = 0; i < docs.size(); i++) {
            Doctor d = docs.get(i);
            List<Employment> es = dao.findByDoctorIdAndOrganId(d.getDoctorId(),
                    d.getOrgan());
            if (es != null && es.size() > 0) {
                docs.get(i).setEmployments(es);
            }
        }
        return docs;
    }


    /**
     * 查询医生信息和医生执业信息服务 医生审核状态为 1审核通过，状态正常 9注销
     *
     * @param name
     * @param idNumber
     * @param organ
     * @param start
     * @param limit
     * @return docList
     * @author houxr
     */
    @SuppressWarnings({"unchecked"})
    public QueryResult<Doctor> queryDoctorResultForOP(final String name,
                                                      final String idNumber, final Integer organ,
                                                      final String profession, final Integer department,
                                                      final Integer start, final Integer limit, final Integer status, final Integer userType) {
        HibernateStatelessResultAction<QueryResult<Doctor>> action = new AbstractHibernateStatelessResultAction<QueryResult<Doctor>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                int total = 0;


                StringBuilder hql = new StringBuilder(
                        " from Doctor a,Employment b "
                                + "where a.doctorId=b.doctorId and a.status in (1,9)");
                if (!StringUtils.isEmpty(name)) {
                    hql.append(" and a.name like '%").append(name).append("%'");
                }
                if (!StringUtils.isEmpty(idNumber)) {
                    hql.append(" and a.idNumber like '%").append(idNumber).append("%'");
                }
                if (organ != null) {
                    hql.append(" and b.organId=").append(organ);
                }
                if (!StringUtils.isEmpty(profession)) {
                    hql.append(" and a.profession like '%").append(profession).append("%'");
                }
                if (status != null) {
                    hql.append(" and a.status=").append(status);
                }
                if (userType != null) {
                    hql.append(" and a.userType=").append(userType);
                }
                if (department != null) {
                    hql.append(" and b.department=").append(department);
                }


                Query countQuery = ss.createQuery("select count(DISTINCT a) " + hql.toString());
                total = ((Long) countQuery.uniqueResult()).intValue();//获取总条数

                if (department != null) {
                    hql.append(" order by b.orderNum desc,a.doctorId desc");
                }else{
                    hql.append(" order by a.doctorId desc");
                }

                Query query = ss.createQuery("select DISTINCT a  " + hql.toString() );
                if (start != null) {
                    query.setMaxResults(limit);
                    query.setFirstResult(start);
                }
                List<Doctor> tfList = query.list();
                QueryResult<Doctor> qResult = new QueryResult<Doctor>(
                        total, query.getFirstResult(), query.getMaxResults(),
                        tfList);
                setResult(qResult);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (QueryResult<Doctor>) action.getResult();
    }

    /**
     * 医生注册（广福）
     */
    @RpcService
    public String registerDoctor(Doctor doctor) {
        try {
            this.RegisteredHisDoctor(doctor.getMobile(), doctor.getName(), doctor.getIdNumber(), doctor.getOrgan(),
                    doctor.getProfession(), doctor.getProTitle(), doctor.getInvitationCode(), doctor.getEmail());

            return "";
        } catch (Exception e) {
            log.error("registerDoctor() error: "+e);
            return doctor.getName() + "注册失败";
        }
    }


    public void RegisteredHisDoctor(String mobile, String name,
                                    String IDCard, int organ, String profession, String proTitle,
                                    Integer invitationCode, String jobNum) {
        if (StringUtils.isEmpty(mobile)) {
            new DAOException(DAOException.VALUE_NEEDED, "mobile is required!");
        }
        if (StringUtils.isEmpty(name)) {
            new DAOException(DAOException.VALUE_NEEDED, "name is required!");
        }
        if (StringUtils.isEmpty(IDCard)) {
            new DAOException(DAOException.VALUE_NEEDED, "IDCard is required!");
        }
        if (StringUtils.isEmpty(profession)) {
            new DAOException(DAOException.VALUE_NEEDED,
                    "profession is required!");
        }
        DepartmentDAO deptDao = DAOFactory.getDAO(DepartmentDAO.class);
        EmploymentDAO empDao = DAOFactory.getDAO(EmploymentDAO.class);
        ConsultSetDAO csDao = DAOFactory.getDAO(ConsultSetDAO.class);

        String proText = ""; // 专科名称
        Department dept = null;// 科室信息
        try {
            proText = DictionaryController.instance()
                    .get("eh.base.dictionary.Profession").getText(profession);
        } catch (ControllerException e) {
            log.error("RegisteredHisDoctor() error: "+e);
        }
        Doctor doc = this.getByIdNumber(IDCard);// 根据身份证获取医生信息
        if (doc == null) {
            doc = this.getByNameAndMobile(name, mobile);// 根据姓名和手机号获取医生信息
            if (doc == null) {
                dept = deptDao.getByNameAndOrgan(proText, organ);
                if (dept != null) {
                    List<Doctor> docs = this.findByNameAndDeptId(name,
                            dept.getDeptId());// 根据姓名和科室信息获得医生信息
                    if (docs != null && docs.size() > 0) {
                        doc = docs.get(0);
                    }
                }
            }
        } else {

        }
        if (doc == null) {// 医生不存在
            Doctor oldDoctor = this.getByMobile(mobile);
            if (oldDoctor != null) {// 若原数据库中包含该手机号码，则将原数据库中的手机好前面加上“CF”
                oldDoctor.setMobile("CF" + oldDoctor.getMobile());
                this.update(oldDoctor);
            }
            dept = deptDao.getDeptByProfessionIdAndOrgan(profession, organ);
            int deptId = dept.getDeptId();// 科室ID
            doc = new Doctor();
            RegisterDoctorSevice rdSevice = new RegisterDoctorSevice();
            doc = rdSevice.convertIdcard(IDCard, doc);// 将身份证信息填充进去
            doc.setName(name);
            doc.setMobile(mobile);
            doc.setUserType(1);
            doc.setProfession(profession);
            doc.setProTitle(StringUtils.isEmpty(proTitle) ? "99" : proTitle);
            doc.setTeams(false);
            doc.setStatus(0);
            doc.setCreateDt(new Date());
            doc.setLastModify(new Date());
            doc.setOrgan(organ);
            doc.setDepartment(dept.getDeptId());
            doc.setChief(0);
            doc.setOrderNum(1);
            doc.setVirtualDoctor(false);
            doc.setSource(1);
            doc.setHaveAppoint(0);
            doc.setInvitationCode(invitationCode);
            doc.setRewardFlag(false);
            doc = this.save(doc);
            int doctorId = doc.getDoctorId();// 医生ID
            empDao.RegisteredHisEmployment(doctorId, organ, deptId, jobNum);// 更新机构信息
            if (!csDao.exist(doctorId)) {
                ConsultSet cs = new ConsultSet();
                cs.setDoctorId(doctorId);
                cs.setOnLineStatus(0);
                cs.setAppointStatus(0);
                cs.setTransferStatus(0);
                cs.setMeetClinicStatus(0);
                cs.setPatientTransferStatus(0);
                csDao.save(cs);
            }
        } else {// 医生存在 则更新信息
            empDao.updateJobNumberByDoctorIdAndOrganId(jobNum, doc.getDoctorId(), doc.getOrgan());
        }

    }

    /**
     * 运行平台新的医生是否有开通账号相关查询
     *
     * @param doctorName     医生姓名
     * @param idNumber       身份证号码
     * @param mobile         手机号码
     * @param invitationCode 邀请码
     * @param organ          机构
     * @param department     部门
     * @param status         医生状态
     * @param isOpenAccount  是否已开户
     * @param startTime      起始日期
     * @param endTime        结束日期
     * @param start
     * @param limit
     * @return
     * @author houxr
     * @date 2016-08-11
     */
    public QueryResult<Doctor> queryDoctorByStartAndLimit(final String doctorName, final String idNumber,
                                                          final String mobile, final Integer invitationCode,
                                                          final Integer organ, final Integer department,
                                                          final Integer status, final Integer isOpenAccount,
                                                          final Date startTime, final Date endTime,
                                                          final int start, final int limit, final Boolean transferStatus,
                                                          final Boolean meetClinicStatus, final Boolean onLineStatus, final Boolean appointStatus,
                                                          final Boolean patientTransferStatus, final Boolean virtualDoctor) {
        HibernateStatelessResultAction<QueryResult<Doctor>> action = new AbstractHibernateStatelessResultAction<QueryResult<Doctor>>() {
            @Override
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws Exception {
                long total = 0;
                StringBuilder hql = new StringBuilder(
                        "from Doctor d ,ConsultSet c where d.doctorId=c.doctorId ");
                HashMap<String, Object> params = new HashMap<String, Object>();
                if (!ObjectUtils.isEmpty(department)) {
                    hql = new StringBuilder(
                            "from Doctor d ,ConsultSet c,Employment e where d.doctorId=c.doctorId and e.doctorId=d.doctorId and e.department=:department ");
                    params.put("department", department);
                }else{
                    if (!ObjectUtils.isEmpty(organ)) {
                        hql = new StringBuilder(
                                "from Doctor d ,ConsultSet c,Employment e where d.doctorId=c.doctorId and e.doctorId=d.doctorId and e.organId=:organId ");
                        params.put("organId", organ);
                    }
                }
                if (!StringUtils.isEmpty(doctorName)) {
                    hql.append(" and d.name like :doctorName");
                    params.put("doctorName", "%" + doctorName + "%");
                }
                if (!StringUtils.isEmpty(idNumber)) {
                    hql.append(" and d.idNumber like :idNumber");
                    params.put("idNumber", "%" + idNumber + "%");
                }
                if (!StringUtils.isEmpty(mobile)) {
                    hql.append(" and d.mobile like :mobile");
                    params.put("mobile", "%" + mobile + "%");
                }
                if (!ObjectUtils.isEmpty(invitationCode)) {
                    hql.append(" and d.invitationCode =:invitationCode");
                    params.put("invitationCode", invitationCode);
                }
                if (!ObjectUtils.isEmpty(status)) {
                    hql.append(" and d.status=:status");
                    params.put("status", status);
                }
                if (startTime != null) {
                    hql.append(" and d.createDt>=:startTime");
                    params.put("startTime", startTime);
                }
                if (endTime != null) {
                    hql.append(" and d.createDt<=:endTime");
                    DateTime dt= new DateTime(endTime);
                    params.put("endTime", dt.plusDays(1).toDate());
                }
                /*if (ObjectUtils.nullSafeEquals(isOpenAccount, 1)) {//开通账号
                    hql.append(" and exists ( from UserRoles where userId=d.mobile and roleId='doctor' )");
                }
                if (ObjectUtils.nullSafeEquals(isOpenAccount, 0)) {//未开通账号
                    hql.append(" and not exists ( from UserRoles where userId=d.mobile and roleId='doctor' )");
                }*/
                if (transferStatus != null) {
                    hql.append(" AND IFNULL(c.transferStatus,0)=").append(transferStatus ? "1" : "0");
                }
                if (meetClinicStatus != null) {
                    hql.append(" AND IFNULL(c.meetClinicStatus,0)=").append(meetClinicStatus ? "1" : "0");
                }
                if (onLineStatus != null) {
                    hql.append(" AND IFNULL(c.onLineStatus,0)=").append(onLineStatus ? "1" : "0");
                }
                if (appointStatus != null) {
                    hql.append(" AND IFNULL(c.appointStatus,0)=").append(appointStatus ? "1" : "0");
                }
                if (patientTransferStatus != null) {
                    hql.append(" AND IFNULL(c.patientTransferStatus,0)=").append(patientTransferStatus ? "1" : "0");
                }
                if (virtualDoctor != null) {
                    hql.append(" AND IFNULL(d.virtualDoctor,0)=").append(virtualDoctor ? "1" : "0");
                }
                hql.append(" order by d.createDt desc ");

                Query query = ss.createQuery("select count(DISTINCT d.doctorId) " + hql.toString());
                query.setProperties(params);
                total = (long) query.uniqueResult();//获取总条数

                query = ss.createQuery("select DISTINCT d " + hql.toString());
                query.setProperties(params);
                query.setFirstResult(start);
                query.setMaxResults(limit);
                List<Doctor> doctors = query.list();
                /*for (Object[] objects : list) {
                    Doctor doctor = (Doctor) objects[0];
                    doctor.setIsOpen((long) objects[1] > 0 ? 1 : 0);
                    doctors.add(doctor);
                }*/
                QueryResult<Doctor> result = new QueryResult<Doctor>(total, query.getFirstResult(), query.getMaxResults(), doctors);
                setResult(result);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (QueryResult<Doctor>) action.getResult();
    }

    /**
     * 区-市-省依次查询符合条件的医生列表
     * 供QueryDoctorListService.queryDoctorListForAppointConsult使用
     *
     * @param addrArea    地区
     * @param subAddrArea 截取的地区
     * @param profession  专科代码
     * @return 符合条件的医生对象
     * @Date 2016年11月14日 15:32:58 zhangsl 修改按评分高低排序
     */
    public List<Doctor> findRemdDoctorListForAppointConsult(final int doctorId,
                                                            final String addrArea, final String subAddrArea, final String profession) {
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                StringBuilder hql = new StringBuilder(
                        "select distinct d from Doctor d,Organ o,ConsultSet c");

                hql.append(" where d.doctorId=c.doctorId and d.organ=o.organId and  "
                        + " d.profession = :profession and d.doctorId<>:doctorId  and d.status=1 and c.appointStatus=1 ");


                if (addrArea.length() == 6) {
                    hql.append(" and o.addrArea=:addrArea");
                } else {
                    hql.append(" and o.addrArea like :addrArea ");
                    if (!StringUtils.isEmpty(subAddrArea)) {
                        hql.append(" and substring(o.addrArea, 1, :length) <> :subAddrArea ");
                    }
                }

                hql.append(" order by d.rating desc");

                Query q = ss.createQuery(hql.toString());
                q.setParameter("profession", profession);
                q.setParameter("doctorId", doctorId);

                if (addrArea.length() == 6) {
                    q.setParameter("addrArea", addrArea);
                } else {
                    q.setParameter("addrArea", addrArea + "%");

                    if (!StringUtils.isEmpty(subAddrArea)) {
                        q.setParameter("subAddrArea", subAddrArea);
                        q.setParameter("length", subAddrArea.length());
                    }

                }

                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 个性化管理单元下查询符合条件的医生列表
     * 供QueryDoctorListService.queryDoctorListForAppointConsult使用
     *
     * @param profession 专科代码
     * @return 符合条件的医生对象
     * @Date 2016年11月14日 15:32:58 zhangsl 修改按评分高低排序
     */
    public List<Doctor> findRemdUnitDoctorListForAppointConsult(final int doctorId, final String profession) {
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                StringBuilder hql = new StringBuilder(
                        "select distinct d from Doctor d,Organ o,ConsultSet c");

                hql.append(" where d.doctorId=c.doctorId and d.organ=o.organId and  "
                        + " d.profession = :profession and d.doctorId<>:doctorId  and d.status=1 and c.appointStatus=1 ");


                OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
                List<Integer> organs = organDAO.findOrgansByUnitForHealth();

                if (organs != null && organs.size() > 0) {
                    hql.append(" and (");
                    for (Integer i : organs) {
                        hql.append("d.organ=").append(i).append(" or ");
                    }
                    hql = new StringBuilder(hql.substring(0, hql.length() - 3)).append(")");
                }

                hql.append(" order by d.rating desc");

                Query q = ss.createQuery(hql.toString());
                q.setParameter("profession", profession);
                q.setParameter("doctorId", doctorId);

                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 返回部分医生信息，根据实际情况使用
     * 目前供QueryDoctorListService.queryDoctorListForAppointConsult使用
     *
     * @param doc
     * @return
     */
    public Doctor getPartDocInfo(Doctor doc) {
        Doctor d = new Doctor();
        d.setDoctorId(doc.getDoctorId());
        d.setName(doc.getName());
        d.setGender(doc.getGender());
        d.setProTitle(StringUtils.isEmpty(doc.getProTitle()) ? "99" : doc.getProTitle());
        d.setProfession(doc.getProfession());
        d.setPhoto(doc.getPhoto());
        d.setGoodRating(doc.getGoodRating() == null ? Integer.valueOf(0) : doc.getGoodRating());
        return d;
    }

    /**
     * 获取某机构的签约医生 (根据职级排序)
     *
     * @param organId
     * @return
     */
    public List<RecommendDoctorBean> findSignDoctorsByOrganId(final Integer organId) {
        HibernateStatelessResultAction<List<RecommendDoctorBean>> action = new AbstractHibernateStatelessResultAction<List<RecommendDoctorBean>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder();
                hql.append("select new eh.entity.bus.housekeeper.RecommendDoctorBean(doc.doctorId,doc.name,doc.photo,doc.proTitle,doc.domain,doc.gender) ")
                        .append("from Doctor doc,Organ org,ConsultSet s where doc.doctorId = s.doctorId and doc.organ = org.organId ")
                        .append("and s.canSign=1 and s.signStatus=1 and doc.status=1 and doc.teams=0 and org.organId=:organId ")
                        .append("order by doc.proTitle ");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("organId", organId);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 根据ID获取签约医生对象
     *
     * @param doctorId
     * @return
     */
    public List<RecommendDoctorBean> findRecommendDoctorById(final List<Integer> doctorId) {
        HibernateStatelessResultAction<List<RecommendDoctorBean>> action = new AbstractHibernateStatelessResultAction<List<RecommendDoctorBean>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder();
                hql.append("select new eh.entity.bus.housekeeper.RecommendDoctorBean(doc.doctorId,doc.name,doc.photo,doc.proTitle,doc.domain,doc.gender) ")
                        .append("from Doctor doc where doc.doctorId in :doctorIds ");
                Query q = ss.createQuery(hql.toString());
                q.setParameterList("doctorIds", doctorId);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 根据ID数组获取医生对象
     *
     * @param doctorId
     * @return
     * @author Andywang
     * 2016-12-22
     */
    public List<Doctor> findDoctorListByIdList(final List<Integer> doctorId) {
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder();
                hql.append("from Doctor where doctorId in :doctorIds");
                Query q = ss.createQuery(hql.toString());
                q.setParameterList("doctorIds", doctorId);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 根据签约人数获取推荐医生
     *
     * @param doctorId
     * @param limit
     * @return
     */
    public List<RecommendDoctorBean> sortRecommendDoctorBySignPatientCount(final List<Integer> doctorId, final Integer limit) {
        HibernateStatelessResultAction<List<RecommendDoctorBean>> action = new AbstractHibernateStatelessResultAction<List<RecommendDoctorBean>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder();
                hql.append("select new eh.entity.bus.housekeeper.RecommendDoctorBean(doc.doctorId,doc.name,doc.photo,doc.proTitle,doc.domain,doc.gender) ")
                        .append("from Doctor doc, RelationDoctor rel where doc.doctorId = rel.doctorId and doc.doctorId in :doctorIds ")
                        .append("and rel.familyDoctorFlag=1 and rel.relationType=0 and CURRENT_TIMESTAMP () >= rel.startDate and CURRENT_TIMESTAMP () <= rel.endDate ")
                        .append("GROUP BY doc.doctorId ORDER BY count(*) desc,doc.proTitle");
                Query q = ss.createQuery(hql.toString());
                q.setParameterList("doctorIds", doctorId);
                if (null != limit) {
                    q.setFirstResult(0);
                    q.setMaxResults(limit);
                }

                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 健康管家获取推荐医生,返回对象
     *
     * @param organList
     * @param addrArea
     * @param removeAddr
     * @param addrSymbol
     * @param limit
     * @return
     */
    public List<RecommendDoctorBean> findRecommendDoctor(final List<Integer> organList, final String addrArea, final String removeAddr,
                                                         final String addrSymbol, final Integer limit) {
        HibernateStatelessResultAction<List<RecommendDoctorBean>> action = new AbstractHibernateStatelessResultAction<List<RecommendDoctorBean>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                Query q = ss.createQuery(findRecommendDoctorHql(false, organList, removeAddr, addrSymbol));
                if (addrSymbol.equals(ConditionOperator.LIKE)) {
                    q.setParameter("addrArea", addrArea + "%");
                } else {
                    q.setParameter("addrArea", addrArea);
                }
                if (null != organList && !organList.isEmpty()) {
                    q.setParameterList("organList", organList);
                }
                if (StringUtils.isNotEmpty(removeAddr)) {
                    q.setParameter("removeAddr", removeAddr);
                }
                if (null != limit) {
                    q.setFirstResult(0);
                    q.setMaxResults(limit);
                }

                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    public List<Integer> findRecommendDoctorId(final List<Integer> organList, final String addrArea, final String removeAddr,
                                               final String addrSymbol, final Integer limit) {
        HibernateStatelessResultAction<List<Integer>> action = new AbstractHibernateStatelessResultAction<List<Integer>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                Query q = ss.createQuery(findRecommendDoctorHql(true, organList, removeAddr, addrSymbol));
                if (addrSymbol.equals(ConditionOperator.LIKE)) {
                    q.setParameter("addrArea", addrArea + "%");
                } else {
                    q.setParameter("addrArea", addrArea);
                }
                if (null != organList && !organList.isEmpty()) {
                    q.setParameterList("organList", organList);
                }
                if (StringUtils.isNotEmpty(removeAddr)) {
                    q.setParameter("removeAddr", removeAddr);
                }
                if (null != limit) {
                    q.setFirstResult(0);
                    q.setMaxResults(limit);
                }

                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 健康管家获取推荐医生 (不包括团队)
     *
     * @param searchId
     * @param organList
     * @param removeAddr
     * @param addrSymbol
     * @return
     */
    private String findRecommendDoctorHql(final boolean searchId, final List<Integer> organList,
                                          final String removeAddr, final String addrSymbol) {
        StringBuilder hql = new StringBuilder();
        if (searchId) {
            hql.append("select doc.doctorId ");
        } else {
            hql.append("select new eh.entity.bus.housekeeper.RecommendDoctorBean(doc.doctorId,doc.name,doc.photo,doc.proTitle,doc.domain,doc.gender) ");
        }
        hql.append("from Doctor doc,Organ org,ConsultSet s where doc.doctorId = s.doctorId and doc.organ = org.organId ")
                .append("and s.canSign=1 and s.signStatus=1 and doc.status=1 and doc.teams=0 and org.addrArea " + addrSymbol + " :addrArea ");
        if (null != organList && !organList.isEmpty()) {
            hql.append("and org.organId in :organList ");
        }
        if (StringUtils.isNotEmpty(removeAddr)) {
            hql.append("and org.addrArea " + ConditionOperator.NOT_EQUAL + " :removeAddr ");
        }
        hql.append("ORDER BY doc.proTitle ");
        return hql.toString();
    }

    /**
     * 健康端咨询入口搜索医生
     *
     * @param search     搜索条件
     * @param addrArea   属地区域
     * @param organId    机构内码
     * @param profession 专科编码
     * @param proTitle   职称
     * @param start      起始页
     * @param limit      每页限制条数
     * @param flag       标志-0有咨询1没咨询
     * @param mark       入口-0咨询1预约
     * @return
     * @author luf 2016-10-6 咨询入口找医生模式修改
     */
    public HashMap<String, Object> searchDoctorInConsult(
            String search, String addrArea, Integer organId, String profession, String proTitle, String mpiId, int start,
            int limit, int flag, int mark) {
        List<HashMap<String, Object>> docs = new ArrayList<HashMap<String, Object>>();
        int startNext = start + limit;
        if (mark == 0) {
//            if (flag == 0) {
//                docs = searchDoctorHasConsult(search, addrArea, organId, profession, proTitle, start, limit);
//                if (docs == null || docs.isEmpty() || docs.size() < limit) {
//                    startNext = limit - docs.size();
//                    docs.addAll(searchDoctorNoConsult(search, addrArea, organId, profession, proTitle, 0, startNext, mark));
//                    flag = 1;
//                }
//            } else {
//                docs = searchDoctorNoConsult(search, addrArea, organId, profession, proTitle, start, limit, mark);
//            }
            //2017年3月24日11:41:42 yuanb改变查询医生排列顺序 flag失效  先放着，以后版本去掉
            docs = searchDoctorHasConsult(search, addrArea, organId, profession, proTitle, start, limit);
        } else {
            docs = searchDoctorNoConsult(search, addrArea, organId, profession, proTitle, start, limit, mark);
        }
        HashMap<String, Object> result = new HashMap<String, Object>();
        result.put("docList", docs);
        result.put("flag", flag);
        result.put("start", startNext);

        if (!StringUtils.isEmpty(search) && !StringUtils.isEmpty(mpiId)) {
            SearchContentDAO contentDAO = DAOFactory.getDAO(SearchContentDAO.class);
            SearchContent content = new SearchContent();
            content.setMpiId(mpiId);
            content.setContent(search);
            content.setBussType(mark);
            contentDAO.addSearchContent(content, 0);
        }
        return result;
    }

    /**
     * 健康端咨询入口搜索医生
     *
     * @param input     搜索条件
     * @param addrArea   属地区域
     * @param organId    机构内码
     * @param profession 专科编码
     * @param proTitle   职称
     * @param start      起始页
     * @param limit      每页限制条数
     * @return
     * @author luf 2016-10-6 咨询入口找医生模式修改
     * @Date 2016年11月14日 15:32:58 zhangsl 修改按评分高低排序
     * @Date 2017年3月24日  09:44:39 yuanb 修改以是否开通业务/咨询量/评分
     */
    /*

    public List<HashMap<String, Object>> searchDoctorHasConsult(
            final String search, final String addrArea, final Integer organId, final String profession, final String proTitle, final int start,
            final int limit) {
        List<Doctor> docList = new ArrayList<Doctor>();
        List<Integer> docIdList = new ArrayList<Integer>();
        HibernateStatelessResultAction<List<Integer>> action = new AbstractHibernateStatelessResultAction<List<Integer>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
                ProfessionDAO professionDAO = DAOFactory.getDAO(ProfessionDAO.class);
                List<Integer> organs = organDAO.findOrgansByUnitForHealth();

//                StringBuilder hql = new StringBuilder(
//                        "select distinct d from Doctor d,Organ o,ConsultSet c,Consult s where d.doctorId=s.consultDoctor and "
//                                // 2016-3-5 luf：患者端只显示有效的正常医生
//                                // 2016-4-25 luf:添加个性化  and (d.organ in :organs)
//                                // 2016-6-2 luf:放开团队医生限制添加 or d.teams=1
//                                + "o.organId=d.organ and c.doctorId=d.doctorId and ((d.idNumber is not null and d.idNumber<>:empty)or d.teams=1) and d.status=1");
                //2017年3月24日11:40:53  yuanb修改语句
                StringBuilder hql = new StringBuilder(
                        "SELECT DISTINCT p.doctorId FROM (   " +
                                "SELECT d.doctorId,d.organ,d.rating,d.name,(c.appointStatus>0 or c.onLineStatus > 0) stat FROM(" +
                                "SELECT doc.doctorId,doc.organ,doc.rating,doc.profession,doc.domain,doc.name,doc.proTitle FROM base_doctor doc " +
                                "WHERE(doc.idNumber > '0' OR doc.teams = 1)AND doc.STATUS = 1"
                );

                if (organs != null && organs.size() > 0) {
                    hql.append(" and (");
                    for (Integer i : organs) {
                        hql.append("doc.organ=").append(i).append(" or ");
                    }
                    hql = new StringBuilder(hql.substring(0, hql.length() - 3)).append(")");
                }
                if (!StringUtils.isEmpty(proTitle)) {
                    hql.append(" and doc.proTitle=:proTitle");
                }
                if (!StringUtils.isEmpty(profession)) {
                    hql.append(" and doc.profession like :profession");
                }
                hql.append(" ) d,bus_ConsultSet c,base_Organ o WHERE d.DoctorID = c.DoctorId AND d.Organ = o.OrganId ");
                //咨询页搜索医生
                if (!StringUtils.isEmpty(search)) {
                    hql.append(" and (o.shortName like :search");
                    List<String> pros = professionDAO.findKeysByText(search);
                    if (pros != null && !pros.isEmpty()) {
                        hql.append(" or d.profession in (:pros)");
                    }
                    hql.append(" or d.domain like :search or d.name like :search)");
                }
                if (!StringUtils.isEmpty(addrArea)) {
                    hql.append(" and o.addrArea like :addrArea");
                }
                if (organId != null && organId > 0) {
                    hql.append(" and o.organId=:organId");
                }
                hql.append(") p LEFT JOIN (select consultDoctor,count(*) as count from bus_Consult  GROUP BY ConsultDoctor) s ON p.DoctorId = s.ConsultDoctor");
                // 此方法只针对咨询
                hql.append(" group by p.doctorId order by stat DESC,count DESC,p.rating DESC");
                Query q = ss.createSQLQuery(hql.toString());
                //咨询页搜索医生
                if (!StringUtils.isEmpty(search)) {
                    q.setParameter("search", "%" + search + "%");
                    List<String> pros = professionDAO.findKeysByText(search);
                    if (pros != null && !pros.isEmpty()) {
                        q.setParameterList("pros", pros);
                    }
                }
                if (!StringUtils.isEmpty(profession)) {
                    q.setParameter("profession", profession + "%");
                }
                if (!StringUtils.isEmpty(addrArea)) {
                    q.setParameter("addrArea", addrArea + "%");
                }
                if (organId != null && organId > 0) {
                    q.setParameter("organId", organId);
                }
                if (!StringUtils.isEmpty(proTitle)) {
                    q.setParameter("proTitle", proTitle);
                }
                q.setMaxResults(limit);
                q.setFirstResult(start);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        docIdList = action.getResult();
        if (docIdList.size() <= 0 || docIdList == null) {
            return new ArrayList<HashMap<String, Object>>();
        }
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        for (Integer docId : docIdList) {
            Doctor doctor = doctorDAO.getByDoctorId(docId);
            docList.add(doctor);
        }
        EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
        List<HashMap<String, Object>> targets = new ArrayList<HashMap<String, Object>>();
        for (Doctor doctor : docList) {
            HashMap<String, Object> result = new HashMap<String, Object>();
            int doctorId = doctor.getDoctorId();
            Employment employment = employmentDAO
                    .getPrimaryEmpByDoctorId(doctorId);
            if (employment != null) {
                doctor.setDepartment(employment.getDepartment());
            }
            ConsultSetDAO dao = DAOFactory.getDAO(ConsultSetDAO.class);
            ConsultSet consultSet = dao.get(doctorId);
            result.put("doctor", doctor);
            result.put("consultSet", consultSet);
            targets.add(result);
        }
        return targets;
    }
     */


    public List<HashMap<String, Object>> searchDoctorHasConsult(
            final String input, final String addrArea, final Integer organId, final String profession, final String proTitle, final int start,
            final int limit) {
        final String search = EmojiFilter.filter(input);
        List<Doctor> docList = new ArrayList<>();
        List<Integer> docIdList = new ArrayList<>();
        HibernateStatelessResultAction<List<Integer>> action = new AbstractHibernateStatelessResultAction<List<Integer>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                /** 此段逻辑用于个性化咨询医生排序使用，当运营平台配置某个公众号为个性化排序时，则会优先按照base_doctororder配置的医生排序**/
                Client client = CurrentUserInfo.getCurrentClient();
                SimpleWxAccount wxAccount = CurrentUserInfo.getSimpleWxAccount();
                Map<String, String> properties = CurrentUserInfo.getCurrentWxProperties();
                String busType = "consult";  // 运营平台使用的咨询业务类型  对应 BusType
                String platType = null;      // 运营平台使用的端类型   对应 configType
                String appId = null;         // 运营平台使用的端id    对应configId
                if(client==null || wxAccount==null || properties==null || !"1".equals(properties.get("consultOrderType"))){
                    // 无法获取端类型时，按默认评分给医生排序
                    log.info("searchDoctorHasConsult client or wxAccount or properties is null");
                    defaultQuery(ss);
                    return;
                }
                ClientPlatformEnum clientPlatformEnum = ClientPlatformEnum.fromKey(client.getOs());
                if(clientPlatformEnum==null){
                    log.info(LocalStringUtil.format("searchDoctorHasConsult os is not support, client.os[{}]", client.getOs()));
                    defaultQuery(ss);
                    return;
                }
                platType = clientPlatformEnum.getOpPlatKey();
                switch (clientPlatformEnum){
                    case WEIXIN:
                    case ALILIFE:
                    case WEB:
                        appId = wxAccount.getAppId();
                        break;
                    case WX_WEB:
                        appId = ((SimpleThird)wxAccount).getAppkey();
                        break;
                    default:
                        log.info(LocalStringUtil.format("[searchDoctorHasConsult] other types [{}]", client.getOs()));
                        defaultQuery(ss);
                        return;
                }
                customizeQuery(platType, appId, busType, ss);
            }

            private void customizeQuery(String platType, String appId, String busType, StatelessSession ss) {
                log.info(LocalStringUtil.format("customizeQuery start in with params: platType[{}], appId[{}], busType[{}]", platType, appId, busType));
                WXConfig wxConfig = DAOFactory.getDAO(WXConfigsDAO.class).getByAppID(appId);
                String configId = wxConfig==null?"0":String.valueOf(wxConfig.getId());
                OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
                ProfessionDAO professionDAO = DAOFactory.getDAO(ProfessionDAO.class);
                List<Integer> organs = organDAO.findOrgansByUnitForHealth();
                StringBuffer sb = new StringBuffer(
                        "SELECT d.doctorId FROM base_doctor d JOIN base_organ o ON ( d.Organ = o.OrganId AND d.status=1 AND d.searchRating >= 1000000  "
                );

                if(ValidateUtil.notBlankList(organs)){
                    sb.append(" AND d.organ in (");
                    sb.append(LocalStringUtil.listToIntArrayString(organs));
                    sb.append(") ");
                }

                if (ValidateUtil.notBlankString(proTitle)) {
                    sb.append(" AND d.proTitle=");
                    sb.append("'" + proTitle + "' ");

                }
                sb.append(" and d.virtualDoctor!=1 ");//屏蔽虚拟医生
                if (ValidateUtil.notBlankString(profession)) {
                    sb.append(" AND d.profession like ");
                    sb.append("'" + profession + "%' ");
                }
                if (ValidateUtil.notNullAndZeroInteger(organId)){
                    sb.append(" AND o.organId=");
                    sb.append(organId);
                }
                if (ValidateUtil.notBlankString(addrArea)) {
                    sb.append(" AND o.addrArea like ");
                    sb.append("'" + addrArea + "%' ");
                }
                if(ValidateUtil.notBlankString(search)){
                    sb.append(" AND (");
                    sb.append("o.shortName like ");
                    sb.append("'%" + search + "%' ");
                    sb.append(" OR d.name like ");
                    sb.append("'%" + search + "%' ");
                    sb.append(" OR d.domain like ");
                    sb.append("'%" + search + "%' ");
                    List<String> pros = professionDAO.findKeysByText(search);
                    if (ValidateUtil.notBlankList(pros)) {
                        sb.append(" OR d.profession in (");
                        sb.append(LocalStringUtil.listToStringArrayString(pros));
                        sb.append(") ");
                    }
                    sb.append(") ");
                }else {
                    sb.append(" AND (d.testPersonnel is null OR d.testPersonnel='0') ");
                }
                sb.append(") ");
                sb.append(" LEFT JOIN base_doctororder od ON (d.DoctorId = od.DoctorId AND od.BusType = '" + busType + "' ");
                sb.append(" AND ConfigType = '" + platType + "' ");
                sb.append(" AND ConfigId = '" + configId + "' ) ");

                Map<String, Object> configs = getConfigAndSortType(organId);

                DoctorSortLikeEnumType doctorSortLikeEnumType = ((DoctorSortLikeEnumType) configs.get("doctorSortLikeEnumType"));
                OrganConfig organConfig = ((OrganConfig) configs.get("organConfig"));

                if (doctorSortLikeEnumType.getTypeValue().intValue()==0) {
                    sb.append(" ORDER BY od.Weight DESC, d.searchRating DESC, d.doctorId ASC");
                } else if (doctorSortLikeEnumType.getTypeValue().intValue() == 1) {
                    appointDoctorSort(sb, organConfig);
                }

                //sb.append(" ORDER BY od.Weight DESC, d.searchRating DESC, d.doctorId ASC");
                log.info(LocalStringUtil.format("sb.toString[{}]", sb.toString()));
                Query q = ss.createSQLQuery(sb.toString());
                q.setMaxResults(limit);
                q.setFirstResult(start);
                setResult(q.list());
            }

            private void defaultQuery(StatelessSession ss){
                OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
                ProfessionDAO professionDAO = DAOFactory.getDAO(ProfessionDAO.class);
                List<Integer> organs = organDAO.findOrgansByUnitForHealth();
                StringBuffer sb = new StringBuffer(
                        "SELECT d.doctorId FROM base_doctor d JOIN base_organ o ON ( d.Organ = o.OrganId AND d.status=1 AND d.searchRating >= 1000000  "
                );

                if(ValidateUtil.notBlankList(organs)){
                    sb.append(" AND d.organ in (");
                    sb.append(LocalStringUtil.listToIntArrayString(organs));
                    sb.append(") ");
                }

                if (ValidateUtil.notBlankString(proTitle)) {
                    sb.append(" AND d.proTitle=");
                    sb.append("'" + proTitle + "' ");

                }
                if (ValidateUtil.notBlankString(profession)) {
                    sb.append(" AND d.profession like ");
                    sb.append("'" + profession + "%' ");
                }
                if (ValidateUtil.notNullAndZeroInteger(organId)){
                    sb.append(" AND o.organId=");
                    sb.append(organId);
                }
                if (ValidateUtil.notBlankString(addrArea)) {
                    sb.append(" AND o.addrArea like ");
                    sb.append("'" + addrArea + "%' ");
                }
                sb.append(" and d.virtualDoctor!=1 ");//屏蔽虚拟医生
                if(ValidateUtil.notBlankString(search)){
                    sb.append(" AND (");
                    sb.append("o.shortName like ");
                    sb.append("'%" + search + "%' ");
                    sb.append(" OR d.name like ");
                    sb.append("'%" + search + "%' ");
                    sb.append(" OR d.domain like ");
                    sb.append("'%" + search + "%' ");
                    List<String> pros = professionDAO.findKeysByText(search);
                    if (ValidateUtil.notBlankList(pros)) {
                        sb.append(" OR d.profession in (");
                        sb.append(LocalStringUtil.listToStringArrayString(pros));
                        sb.append(") ");
                    }
                    sb.append(") ");
                }else {
                    sb.append(" AND (d.testPersonnel is null OR d.testPersonnel='0') ");
                }
                sb.append(") ");

                Map<String, Object> configs = getConfigAndSortType(organId);

                DoctorSortLikeEnumType doctorSortLikeEnumType = ((DoctorSortLikeEnumType) configs.get("doctorSortLikeEnumType"));
                OrganConfig organConfig = ((OrganConfig) configs.get("organConfig"));

                if (doctorSortLikeEnumType.getTypeValue().intValue()==0) {
                    sb.append(" ORDER BY searchRating DESC, d.doctorId ASC");
                } else if (doctorSortLikeEnumType.getTypeValue().intValue()==1) {
                    appointDoctorSort(sb,organConfig);
                }


                //sb.append(" ORDER BY searchRating DESC, d.doctorId ASC");
                Query q = ss.createSQLQuery(sb.toString());
                q.setMaxResults(limit);
                q.setFirstResult(start);
                setResult(q.list());
            }

        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        docIdList = action.getResult();
        if (docIdList == null||docIdList.size() <= 0) {
            return new ArrayList<>();
        }
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        for (Integer docId : docIdList) {
            Doctor doctor = doctorDAO.getByDoctorId(docId);
            docList.add(doctor);
        }
        EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
        List<HashMap<String, Object>> targets = new ArrayList<>();
        for (Doctor doctor : docList) {
            HashMap<String, Object> result = new HashMap<>();
            int doctorId = doctor.getDoctorId();
            Employment employment = employmentDAO
                    .getPrimaryEmpByDoctorId(doctorId);
            if (employment != null) {
                doctor.setDepartment(employment.getDepartment());
            }
            ConsultSetDAO dao = DAOFactory.getDAO(ConsultSetDAO.class);
            ConsultSet consultSet = dao.getById(doctorId);
            result.put("doctor", doctor);
            result.put("consultSet", consultSet);
            targets.add(result);
        }
        return targets;
    }

    /**
     * 健康端咨询入口搜索医生
     *
     * @param search     搜索条件
     * @param addrArea   属地区域
     * @param organId    机构内码
     * @param profession 专科编码
     * @param proTitle   职称
     * @param start      起始页
     * @param limit      每页限制条数
     * @param mark       入口-0咨询1预约
     * @return
     * @author luf 2016-10-6 咨询入口找医生模式修改
     * @Date 2016年11月14日 15:32:58 zhangsl 修改按评分高低排序
     */
    public List<HashMap<String, Object>> searchDoctorNoConsult(
            final String search, final String addrArea, final Integer organId, final String profession, final String proTitle, final int start,
            final int limit, final int mark) {
        List<Doctor> docList = new ArrayList<Doctor>();
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
                ProfessionDAO professionDAO = DAOFactory.getDAO(ProfessionDAO.class);
                List<Integer> organs = organDAO.findOrgansByUnitForHealth();

                StringBuffer hql = new StringBuffer(
                        "select distinct d from Doctor d,Organ o,ConsultSet c where "
                                // 2016-3-5 luf：患者端只显示有效的正常医生
                                // 2016-4-25 luf:添加个性化  and (d.organ in :organs)
                                // 2016-6-2 luf:放开团队医生限制添加 or d.teams=1
                                + "o.organId=d.organ and c.doctorId=d.doctorId and ((d.idNumber is not null and d.idNumber<>:empty)or d.teams=1) and d.status=1");
                if (organs != null && organs.size() > 0) {
                    hql.append(" and (");
                    for (Integer i : organs) {
                        hql.append("d.organ=").append(i).append(" or ");
                    }
                    hql = new StringBuffer(hql.substring(0, hql.length() - 3)).append(")");
                }
                if (mark == 0) {
                    List<Integer> docIds = searchDoctorIdHasConsult(search, addrArea, organId, profession, proTitle);
                    for (Integer docId : docIds) {
                        hql.append(" and d.doctorId<>");
                        hql.append(docId);
                    }
                }
                //咨询页搜索医生
                if (!StringUtils.isEmpty(search)) {
                    List<String> pros = professionDAO.findKeysByText(search);
                    hql.append(" and (o.shortName like :search");
                    if (pros != null && !pros.isEmpty()) {
                        hql.append(" or d.profession in (:pros)");
                    }
                    hql.append(" or d.domain like :search or d.name like :search)");
                }
                if (!StringUtils.isEmpty(profession)) {
                    hql.append(" and d.profession like :profession");
                }
                hql.append(" and d.virtualDoctor!=1 ");//屏蔽虚拟医生
                if (!StringUtils.isEmpty(addrArea)) {
                    hql.append(" and o.addrArea like :addrArea");
                }
                if (organId != null && organId > 0) {
                    hql.append(" and o.organId=:organId");
                }
                if (!StringUtils.isEmpty(proTitle)) {
                    hql.append(" and d.proTitle=:proTitle");
                }

                Map<String, Object> configs = getConfigAndSortType(organId);
                log.info("configSortType:{}", JSONUtils.toString(configs));

                DoctorSortLikeEnumType doctorSortLikeEnumType = ((DoctorSortLikeEnumType) configs.get("doctorSortLikeEnumType"));
                OrganConfig organConfig = ((OrganConfig) configs.get("organConfig"));

                if (doctorSortLikeEnumType.getTypeValue().intValue()==0) {
                    defaultDoctorSort(hql);
                } else if (doctorSortLikeEnumType.getTypeValue().intValue()==1) {
                    appointDoctorSort(hql,organConfig);
                }

                Query q = ss.createQuery(hql.toString());
                q.setParameter("empty", "");
                //咨询页搜索医生
                if (!StringUtils.isEmpty(search)) {
                    q.setParameter("search", "%" + search + "%");
                    List<String> pros = professionDAO.findKeysByText(search);
                    if (pros != null && !pros.isEmpty()) {
                        q.setParameterList("pros", pros);
                    }
                }
                if (!StringUtils.isEmpty(profession)) {
                    q.setParameter("profession", profession + "%");
                }
                if (!StringUtils.isEmpty(addrArea)) {
                    q.setParameter("addrArea", addrArea + "%");
                }
                if (organId != null && organId > 0) {
                    q.setParameter("organId", organId);
                }
                if (!StringUtils.isEmpty(proTitle)) {
                    q.setParameter("proTitle", proTitle);
                }
                q.setMaxResults(limit);
                q.setFirstResult(start);
                setResult(q.list());
            }

            private void defaultDoctorSort(StringBuffer hql) {
                if (mark == 0) {
                    hql.append(" order by (IFNULL(c.onLineStatus,0)+IFNULL(c.appointStatus,0)) DESC,d.rating DESC");
                } else {
                    hql.append(" order by (IFNULL(d.haveAppoint,0)+IFNULL(c.patientTransferStatus,0)) DESC,d.rating DESC");
                }
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        docList = action.getResult();
        if ( docList == null || docList.size() == 0 ) {
            return new ArrayList<HashMap<String, Object>>();
        }
        EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
        List<HashMap<String, Object>> targets = new ArrayList<HashMap<String, Object>>();
        for (Doctor doctor : docList) {
            HashMap<String, Object> result = new HashMap<String, Object>();
            int doctorId = doctor.getDoctorId();
            Employment employment = employmentDAO
                    .getPrimaryEmpByDoctorId(doctorId);
            if (employment != null) {
                doctor.setDepartment(employment.getDepartment());
            }
            ConsultSetDAO dao = DAOFactory.getDAO(ConsultSetDAO.class);
            ConsultSet consultSet = dao.getById(doctorId);

            // 2016-3-8 luf 根据患者端号源查询接口返回参数判断医生是否有号
            AppointSourceDAO asDao = DAOFactory.getDAO(AppointSourceDAO.class);
            List<Object[]> oss = asDao.findTotalByDcotorId(doctorId, 1);// 患者端固定传1
            if (oss != null && oss.size() > 0) {
                doctor.setHaveAppoint(1);
            } else {
                doctor.setHaveAppoint(0);
            }

            result.put("doctor", doctor);
            result.put("consultSet", consultSet);
            targets.add(result);
        }
        return targets;
    }

    private Map<String,Object> getConfigAndSortType(Integer organId) {
        Integer doctorSortLikeType=null;
        OrganConfig organConfig=null;

        if (organId != null) {
            organConfig = DAOFactory.getDAO(OrganConfigDAO.class).getByOrganId(organId);
            doctorSortLikeType = organConfig.getDoctorSortLikeType();
        }else{
            HashMap<String, Object> wxOrgansDisplay = DAOFactory.getDAO(OrganDAO.class).getWxOrgansDisplay();
            if (wxOrgansDisplay != null && wxOrgansDisplay.get("type") != null) {
                if (Integer.parseInt(String.valueOf(wxOrgansDisplay.get("type"))) == 2) { //如果微信公众号是个性化机构公众号
                    if (wxOrgansDisplay.get("organConfigDetails") != null) {
                        List<OrganConfig> organConfigs = (List<OrganConfig>) wxOrgansDisplay.get("organConfigDetails");
                        organConfig = organConfigs.get(0);
                        doctorSortLikeType = organConfig.getDoctorSortLikeType();
                    }
                }
            }
        }
        DoctorSortLikeEnumType doctorSortLikeEnumType =  DoctorSortLikeEnumType.fromValue(doctorSortLikeType == null ? 0 : doctorSortLikeType);

        HashMap<String, Object> configMaps = Maps.newHashMap();
        configMaps.put("doctorSortLikeEnumType",doctorSortLikeEnumType);
        configMaps.put("organConfig",organConfig);


        return configMaps;
    }

    public List<Integer> searchDoctorIdHasConsult(
            final String search, final String addrArea, final Integer organId, final String profession, final String proTitle) {
        List<Doctor> docList = new ArrayList<Doctor>();
        HibernateStatelessResultAction<List<Integer>> action = new AbstractHibernateStatelessResultAction<List<Integer>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
                ProfessionDAO professionDAO = DAOFactory.getDAO(ProfessionDAO.class);
                List<Integer> organs = organDAO.findOrgansByUnitForHealth();

                StringBuilder hql = new StringBuilder(
                        "select distinct d.doctorId from Doctor d,Organ o,ConsultSet c,Consult s where d.doctorId=s.consultDoctor and "
                                // 2016-3-5 luf：患者端只显示有效的正常医生
                                // 2016-4-25 luf:添加个性化  and (d.organ in :organs)
                                // 2016-6-2 luf:放开团队医生限制添加 or d.teams=1
                                + "o.organId=d.organ and c.doctorId=d.doctorId and ((d.idNumber is not null and d.idNumber<>:empty)or d.teams=1) and d.status=1");
                if (organs != null && organs.size() > 0) {
                    hql.append(" and (");
                    for (Integer i : organs) {
                        hql.append("d.organ=").append(i).append(" or ");
                    }
                    hql = new StringBuilder(hql.substring(0, hql.length() - 3)).append(")");
                }
                //咨询页搜索医生
                if (!StringUtils.isEmpty(search)) {
                    List<String> pros = professionDAO.findKeysByText(search);
                    hql.append(" and (o.shortName like :search");
                    if (pros != null && !pros.isEmpty()) {
                        hql.append(" or d.profession in (:pros)");
                    }
                    hql.append(" or d.domain like :search or d.name like :search)");
                }
                if (!StringUtils.isEmpty(profession)) {
                    hql.append(" and d.profession like :profession");
                }
                if (!StringUtils.isEmpty(addrArea)) {
                    hql.append(" and o.addrArea like :addrArea");
                }
                if (organId != null && organId > 0) {
                    hql.append(" and o.organId=:organId");
                }
                if (!StringUtils.isEmpty(proTitle)) {
                    hql.append(" and d.proTitle=:proTitle");
                }
                hql.append(" group by d.doctorId order by count(*) desc,(IFNULL(c.onLineStatus,0)+IFNULL(c.appointStatus,0)) DESC,d.rating DESC");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("empty", "");
                //咨询页搜索医生
                if (!StringUtils.isEmpty(search)) {
                    q.setParameter("search", "%" + search + "%");
                    List<String> pros = professionDAO.findKeysByText(search);
                    if (pros != null && !pros.isEmpty()) {
                        q.setParameterList("pros", pros);
                    }
                }
                if (!StringUtils.isEmpty(profession)) {
                    q.setParameter("profession", profession + "%");
                }
                if (!StringUtils.isEmpty(addrArea)) {
                    q.setParameter("addrArea", addrArea + "%");
                }
                if (organId != null && organId > 0) {
                    q.setParameter("organId", organId);
                }
                if (!StringUtils.isEmpty(proTitle)) {
                    q.setParameter("proTitle", proTitle);
                }
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 查询对应机构的药师
     * zhongzx
     *
     * @param organ
     * @param userType
     * @return
     */
    @RpcService
    @DAOMethod(limit = 0)
    public abstract List<Doctor> findByOrganAndUserType(Integer organ, Integer userType);

    /**
     * 根据机构查询所有医生
     *
     * @param organId
     * @return
     */
    @RpcService
    public List<Doctor> findAllDoctorByOrgan(final Integer organId) {
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(" from Doctor where 1=1 ");
                HashMap<String, Object> params = new HashMap<String, Object>();
                if (!ObjectUtils.isEmpty(organId)) {
                    hql.append(" and organ =:organId ");
                    params.put("organId", organId);
                }
                hql.append(" order by createDt desc ");
                Query query = ss.createQuery(hql.toString());
                query.setProperties(params);
                List<Doctor> list = query.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (List<Doctor>) action.getResult();
    }

    /**
     * 根据医生id获取医生总评分
     *
     * @param doctorId
     * @author zhangsl
     * @Date 2016-11-23 13:42:33
     */
    public String getRatingByDoctorId(Integer doctorId) {
        DecimalFormat f = new DecimalFormat("0.0");
        f.setRoundingMode(RoundingMode.HALF_UP);
        Double rating = getByDoctorId(doctorId).getRating();
        String ratingStr = ObjectUtils.isEmpty(rating) ? null : f.format(rating);
        return ratingStr;
    }

    /**
     * 咨询医生列表，剔除不能开处方
     *
     * @param search     搜索条件
     * @param addrArea   属地区域
     * @param organId    机构内码
     * @param profession 专科编码
     * @param proTitle   职称
     * @param start      起始页
     * @param limit      每页限制条数
     * @param flag       标志-0有咨询1没咨询
     * @return
     */
    public HashMap<String, Object> searchDoctorCanRecipe(
            String search, String addrArea, Integer organId, String profession, String proTitle, String mpiId, int start,
            int limit, int flag) {
        RecipeService recipeService = new RecipeService();
        List<HashMap<String, Object>> results = new ArrayList<HashMap<String, Object>>();
        int resNum = 0;
        int startSearch = start;
        int limitSearch = limit;
        int flagSearch = flag;
        while (resNum < limit) {
            HashMap<String, Object> map = this.searchDoctorInConsult(search, addrArea, organId, profession, proTitle, mpiId, startSearch, limitSearch, flagSearch, 0);
            if (map == null || map.get("docList") == null) {
                break;
            }
            ArrayList<HashMap<String, Object>> list = (ArrayList<HashMap<String, Object>>) map.get("docList");
            if (list == null || list.isEmpty()) {
                break;
            }

            for (HashMap<String, Object> docMap : list) {
                Doctor d = (Doctor) docMap.get("doctor");
                if (d == null || d.getDoctorId() == null) {
                    continue;
                }
                Map<String, Object> canRecipe = recipeService.openRecipeOrNot(d.getDoctorId());
                if (canRecipe == null || canRecipe.get("result") == null || !(Boolean) canRecipe.get("result")) {
                    continue;
                }
                HashMap<String, Object> docAndCon = new HashMap<String, Object>();
                docAndCon.put("doctor", d);
                docAndCon.put("consultSet", docMap.get("consultSet"));
                results.add(docAndCon);
            }
            resNum = results.size();
            startSearch = (int) map.get("start");
            limitSearch = limit - resNum;
            flagSearch = (int) map.get("flag");
            if (limitSearch == 0) {
                break;
            }
        }
        HashMap<String, Object> result = new HashMap<String, Object>();
        result.put("start", startSearch);
        result.put("flag", flagSearch);
        result.put("docList", results);
        return result;
    }

    /**
     * 搜索医生-健康端
     *
     * @param search     搜索条件
     * @param addrArea   属地区域
     * @param organId    机构内码
     * @param profession 专科编码
     * @param proTitle   职称
     * @param start      起始页
     * @param limit      每页限制条数
     * @param flag       标志-0有咨询1没咨询
     * @param mark       调用入口标志-0咨询，1预约，2购药
     * @return
     * @author luf 2016-11-28 购药到咨询医生开处方需求
     */
    @RpcService
    public Map<String, Object> searchDoctorConsultOrCanRecipe(
            String search, String addrArea, Integer organId, String profession, String proTitle, String mpiId, int start,
            int limit, int flag, int mark) {
        if (mark == 0) {
            return this.searchDoctorInConsult(search, addrArea, organId, profession, proTitle, mpiId, start, limit, flag, 0);
        } else if (mark == 2) {
            return this.searchDoctorCanRecipe(search, addrArea, organId, profession, proTitle, mpiId, start, limit, flag);
        } else {
            return this.searchDoctorInConsult(search, addrArea, organId, profession, proTitle, mpiId, start, limit, flag, 1);
        }
    }

    public List<Doctor> findByInDocIds(final String docs) {
        if (docs == null) {
            return null;
        }
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder sbHql = new StringBuilder("from Doctor where id in (");
                sbHql.append(docs).append(")");
                Query query = ss.createQuery(sbHql.toString());
                List<Doctor> list = query.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (List<Doctor>) action.getResult();
    }

    /**
     * 获取在线续方首页用药指导医生列表
     * @param addr 地区
     * @param organId 机构id
     * @param proTitle 职称
     * @param start
     * @param limit
     * @return
     */
    public List<Map<String, Object>> queryDoctorListForConduct(final String addr, final Integer organId, final String proTitle,
                                                               final Integer start, final Integer limit) {
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        final List<Integer> organIds = organDAO.findOrganIdsByAddrArea(addr);
        List<Map<String, Object>> docInfoList = new ArrayList<>();
        if(null != organIds && organIds.size() > 0 ){
            HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
                @Override
                public void execute(StatelessSession ss) throws Exception {
                    StringBuilder hql = new StringBuilder("select distinct d from Doctor d where " +
                            "d.status = 1 and d.teams = 0 and d.userType = 5 and " +
                            "d.doctorId in (select DISTINCT e.doctorId from Employment e where e.organId in (:organIds) ");
                    if (null != organId && organId > 0) {
                        hql.append("and e.organId = :organId) ");
                    } else {
                        hql.append(") ");
                    }
                    //医技科药学部医生大专科为73， 医生类型为5（药师）
                    hql.append("and d.profession like '73%' ");
                    if (!StringUtils.isEmpty(proTitle)) {
                        hql.append("and d.proTitle=:proTitle ");
                    }
                    hql.append(" and d.virtualDoctor!=1 ");//屏蔽虚拟医生

                    Query q = ss.createQuery(hql.toString());
                    q.setParameterList("organIds", organIds);
                    if (null != organId && organId > 0) {
                        q.setParameter("organId", organId);
                    }
                    if (!StringUtils.isEmpty(proTitle)) {
                        q.setParameter("proTitle", proTitle);
                    }
                    if (null != start && null != limit) {
                        q.setFirstResult(start);
                        q.setMaxResults(limit);
                    }
                    setResult(q.list());
                }
            };
            HibernateSessionTemplate.instance().execute(action);
            List<Doctor> docList = action.getResult();
            EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
            List<Integer> doctorIds = new ArrayList<>();
            ConsultSetDAO consultSetDAO = DAOFactory.getDAO(ConsultSetDAO.class);

            if (null != docList && !docList.isEmpty())
            {
                // 获取医生Id集合为下面批量查询做准备
                for (Doctor doctor : docList)
                {
                    doctorIds.add(doctor.getDoctorId());
                }

                if (log.isInfoEnabled())
                {
                    log.info("DoctorDao.queryDoctorListForConduct doctorIds = " + doctorIds);
                }

                //优化查询流程减少io交互
                List<ConsultSet> consultSets = consultSetDAO.findByDoctorIds(doctorIds);
                List<Employment> employments = employmentDAO.findPrimaryEmpByDoctorIdList(doctorIds);
                Map<Integer, ConsultSet> consultSetMap = Maps.uniqueIndex(consultSets, new Function<ConsultSet, Integer>() {
                    @Override
                    public Integer apply(ConsultSet input) {
                        return input.getDoctorId();
                    }
                });

                Map<Integer, Employment> employmentMap = Maps.uniqueIndex(employments, new Function<Employment, Integer>() {
                    @Override
                    public Integer apply(Employment input) {
                        return input.getDoctorId();
                    }
                });

                Map<String, Object> docInfo = null;
                Integer doctorId = null;
                for (Doctor doctor : docList)
                {
                    docInfo = new HashMap<>();
                    doctorId = doctor.getDoctorId();
                    if(consultSetMap.containsKey(doctorId)){
                        docInfo.put("consultSet", consultSetMap.get(doctorId));
                    }
                    if(employmentMap.containsKey(doctorId)){
                        doctor.setDepartment(employmentMap.get(doctorId).getDepartment());
                    }

                    docInfo.put("doctor", doctor);
                    docInfoList.add(docInfo);
                }
            }
        }
        return docInfoList;
    }

    /**
     * zhongzx
     * 根据医生id 更新签章数据
     */
    @DAOMethod
    public abstract void updateSealDataByDoctorId(String sealData, int doctorId);

    /**
     * 筛选出开通寻医问药功能的医生（能开处方,不包括团队）列表-分页
     *
     * @param addr
     * @param organId
     * @param profession
     * @param proTitle
     * @param start
     * @param limit
     * @param flag       0-开过处方 1-没开过处方 开过处方按照开处方的数量，评分排序；没开过处方的医生按照评分排序
     * @param queryParam
     * @param doctorName 医生姓名（模糊搜索）
     * @return
     * @author zhongzx
     */
    public List<Map<String, Object>> queryDoctorsCanRecipe(final String addr, final Integer organId, final String profession, final String proTitle, final Integer start,
                                                           final Integer limit, final int flag, final Map<String, Object> queryParam, final String doctorName) {
        final Integer drugId = MapValueUtil.getInteger(queryParam, "drugId");
        //首先获取当前公众号具有的能开处方的机构
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        final List<Integer> organIds = organDAO.queryOragnIdsCanRecipe(addr, drugId);
        final List<Integer> doctorIds;
        //如果是查询 没有开过处方的医生 要去除 在开过处方医生搜索服务里搜出的医生
        if (1 == flag) {
            doctorIds = searchDoctorForRecipe(addr, organId, profession, proTitle, queryParam);
        } else {
            doctorIds = new ArrayList<>();
        }
        //结果集
        List<Map<String, Object>> docInfoList = new ArrayList<>();
        if (null != organIds && organIds.size() > 0) {
            HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
                @Override
                public void execute(StatelessSession ss) throws Exception {
                    StringBuffer hql;
                    //如果开过处方 要跟处方表关联
                    if (0 == flag) {
                        hql = new StringBuffer("select distinct d from Doctor d, ConsultSet c, Recipe r where r.doctor = d.doctorId " +
                                "and r.status = 6 and d.doctorId = c.doctorId and c.recipeConsultStatus = 1 and d.status = 1 " +
                                "and d.teams = 0 and d.doctorId in (select DISTINCT e.doctorId from Employment e " +
                                "where e.organId in (:organIds) ");
                    } else {
                        if (null != doctorIds && doctorIds.size() > 0) {
                            hql = new StringBuffer("select distinct d from Doctor d, ConsultSet c where d.doctorId = c.doctorId " +
                                    "and c.recipeConsultStatus = 1 and d.status = 1 and d.teams = 0 and d.doctorId not in (:doctorIds) " +
                                    "and d.doctorId in (select DISTINCT e.doctorId from Employment e where e.organId in (:organIds) ");
                        } else {
                            hql = new StringBuffer("select distinct d from Doctor d, ConsultSet c where d.doctorId = c.doctorId " +
                                    "and c.recipeConsultStatus = 1 and d.status = 1 and d.teams = 0 and " +
                                    "d.doctorId in (select DISTINCT e.doctorId from Employment e where e.organId in (:organIds) ");
                        }
                    }
                    if (null != organId && organId > 0) {
                        hql.append("and e.organId = :organId) ");
                    } else {
                        hql.append(") ");
                    }
                    if (!StringUtils.isEmpty(profession)) {
                        hql.append("and d.profession like :profession ");
                    }
                    if (!StringUtils.isEmpty(proTitle)) {
                        hql.append("and d.proTitle=:proTitle ");
                    }
                    if(!StringUtils.isEmpty(doctorName)){
                        hql.append("and d.name like '%"+doctorName+"%' ");
                    }
                    hql.append(" and d.virtualDoctor!=1 ");//过滤虚拟医生

                    //自定义医生排序参照
                    //customerDoctorSort(hql);
                    Map<String, Object> configs = getConfigAndSortType(organId);

                    DoctorSortLikeEnumType doctorSortLikeEnumType = ((DoctorSortLikeEnumType) configs.get("doctorSortLikeEnumType"));
                    OrganConfig organConfig = ((OrganConfig) configs.get("organConfig"));

                    if (doctorSortLikeEnumType.getTypeValue().intValue()==0) {
                        defaultDoctorSort(hql);
                    } else if (doctorSortLikeEnumType.getTypeValue().intValue() == 1) {
                        appointDoctorSort(hql, organConfig);
                    } else {
                        defaultDoctorSort(hql);
                    }


                    Query q = ss.createQuery(hql.toString());
                    q.setParameterList("organIds", organIds);
                    if (null != organId && organId > 0) {
                        q.setParameter("organId", organId);
                    }
                    if (!StringUtils.isEmpty(profession)) {
                        q.setParameter("profession", profession + "%");
                    }
                    if (!StringUtils.isEmpty(proTitle)) {
                        q.setParameter("proTitle", proTitle);
                    }
                    if (1 == flag && null != doctorIds && doctorIds.size() > 0) {
                        q.setParameterList("doctorIds", doctorIds);
                    }
                    if (null != start && null != limit) {
                        q.setFirstResult(start);
                        q.setMaxResults(limit);
                    }
                    setResult(q.list());
                }

               /* private void customerDoctorSort(StringBuilder hql) {
                    Integer doctorSortLikeType=null;
                    OrganConfig organConfig=null;

                    if (organId != null) {
                        organConfig = DAOFactory.getDAO(OrganConfigDAO.class).getByOrganId(organId);
                        doctorSortLikeType = organConfig.getDoctorSortLikeType();
                    }else{
                        HashMap<String, Object> wxOrgansDisplay = DAOFactory.getDAO(OrganDAO.class).getWxOrgansDisplay();
                        if (wxOrgansDisplay != null && wxOrgansDisplay.get("type") != null) {
                            if (Integer.parseInt(String.valueOf(wxOrgansDisplay.get("type"))) == 2) { //如果微信公众号是个性化机构公众号
                                if (wxOrgansDisplay.get("organConfigDetails") != null) {
                                    List<OrganConfig> organConfigs = (List<OrganConfig>) wxOrgansDisplay.get("organConfigDetails");
                                    organConfig = organConfigs.get(0);
                                    doctorSortLikeType = organConfig.getDoctorSortLikeType();
                                }
                            }
                        }
                    }

                    DoctorSortLikeEnumType doctorSortLikeEnumType =  DoctorSortLikeEnumType.fromValue(doctorSortLikeType == null ? 0 : doctorSortLikeType);

                    switch (doctorSortLikeEnumType) {
                        case Default:
                            defaultDoctorSort(hql);
                            break;
                        case LikeAppoint:
                            appointDoctorSort(hql,organConfig);
                            break;
                        default:
                            defaultDoctorSort(hql);
                            break;
                    }
                }*/


                private void defaultDoctorSort(StringBuffer hql) {
                    if (0 == flag) {
                        hql.append("group by d.doctorId order by count(d.doctorId) desc, d.rating desc");
                    } else {
                        hql.append("order by d.rating desc");
                    }
                }



            };
            HibernateSessionTemplate.instance().executeReadOnly(action);
            List<Doctor> docList = action.getResult();

            EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
            for (Doctor doctor : docList) {
                Map<String, Object> docInfo = new HashMap<>();
                int doctorId = doctor.getDoctorId();
                Employment employment = employmentDAO.getPrimaryEmpByDoctorId(doctorId);
                if (employment != null) {
                    doctor.setDepartment(employment.getDepartment());
                }
                ConsultSetDAO dao = DAOFactory.getDAO(ConsultSetDAO.class);
                ConsultSet consultSet = dao.getById(doctorId);
                docInfo.put("doctor", doctor);
                docInfo.put("consultSet", consultSet);
                docInfoList.add(docInfo);
            }
        }
        return docInfoList;

    }
    private void  appointDoctorSort(StringBuffer hql,OrganConfig organConfig) {
        hql.append(" order by ");
        //机构自定义科室医生排序功能开启 DoctorOrderType=1
        if (organConfig != null && organConfig.getDoctorOrderType() != null && organConfig.getDoctorOrderType() == 1) {
            hql.append(" d.orderNumEmp DESC,");
        }
        hql.append(" d.haveAppoint DESC,d.virtualDoctor DESC,IFNULL(d.haveAppoint,0) DESC,d.goodRating DESC");
    }


    private List<Integer> searchDoctorForRecipe(final String addr, final Integer organId, final String profession, final String proTitle, Map<String, Object> queryParam) {
        final Integer drugId = MapValueUtil.getInteger(queryParam, "drugId");
        //首先获取当前公众号具有的能开处方的机构
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        final List<Integer> organIds = organDAO.queryOragnIdsCanRecipe(addr, drugId);
        if (null != organIds && organIds.size() > 0) {
            HibernateStatelessResultAction<List<Integer>> action = new AbstractHibernateStatelessResultAction<List<Integer>>() {
                @Override
                public void execute(StatelessSession ss) throws Exception {
                    StringBuilder hql = new StringBuilder("select distinct d.doctorId from Doctor d, ConsultSet c, Recipe r where r.doctor = d.doctorId " +
                            "and r.status = 6 and d.doctorId = c.doctorId and c.recipeConsultStatus = 1 and d.status = 1 " +
                            "and d.teams = 0 and d.doctorId in (select DISTINCT e.doctorId from Employment e " +
                            "where e.organId in (:organIds) ");
                    if (null != organId && organId > 0) {
                        hql.append("and e.organId = :organId) ");
                    } else {
                        hql.append(") ");
                    }
                    if (!StringUtils.isEmpty(profession)) {
                        hql.append("and d.profession like :profession ");
                    }
                    if (!StringUtils.isEmpty(proTitle)) {
                        hql.append("and d.proTitle=:proTitle ");
                    }
                    Query q = ss.createQuery(hql.toString());
                    q.setParameterList("organIds", organIds);
                    if (null != organId && organId > 0) {
                        q.setParameter("organId", organId);
                    }
                    if (!StringUtils.isEmpty(profession)) {
                        q.setParameter("profession", profession + "%");
                    }
                    if (!StringUtils.isEmpty(proTitle)) {
                        q.setParameter("proTitle", proTitle);
                    }
                    setResult(q.list());
                }
            };
            HibernateSessionTemplate.instance().executeReadOnly(action);
            return action.getResult();
        } else {
            return null;
        }
    }

    /**
     * 查询有个性签名的医生
     *
     * @return
     */
    public List<Doctor> findDoctorListHasSignImage(final List<Integer> doctorIds) {
        HibernateStatelessResultAction<List<Doctor>> action = new AbstractHibernateStatelessResultAction<List<Doctor>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder("from Doctor where signImage is not null ");
                if (null != doctorIds && doctorIds.size() > 0) {
                    hql.append("and doctorId in (:doctorIds)");
                }
                Query q = ss.createQuery(hql.toString());
                if (null != doctorIds && doctorIds.size() > 0) {
                    q.setParameterList("doctorIds", doctorIds);
                }
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    public QueryResult<Doctor> queryDoctorByKeywordsAndIsUserAndOrganId(final String keywords, final Boolean isUser,
                                                                        final Integer organId, final int start, final int limit) {
        if (StringUtils.isEmpty(keywords)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "keywords is required.");
        }
        HibernateStatelessResultAction<QueryResult<Doctor>> action = new AbstractHibernateStatelessResultAction<QueryResult<Doctor>>() {
            @Override
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws Exception {
                long total = 0;
                StringBuilder hql = new StringBuilder("from Doctor d ");
                HashMap<String, Object> params = new HashMap<String, Object>();
                hql.append(" where (d.name like :doctorName or d.mobile = :mobile)");
                params.put("doctorName", "%" + keywords + "%");
                params.put("mobile", keywords);
                if (!ObjectUtils.isEmpty(organId)) {
                    hql.append(" and (d.organ=:organId or exists (from Employment where doctorId=d.doctorId and organId=:organId))");
                    params.put("organId", organId);
                }
                if (null != isUser) {//查询是否开通账号
                    if (isUser) {
                        hql.append(" and d.mobile is not null and exists ( from UserRoles where userId=d.mobile and roleId='doctor' )");
                    } else {
                        hql.append(" and (d.mobile is null or not exists ( from UserRoles where userId=d.mobile and roleId='doctor' ))");
                    }
                }
                hql.append(" order by d.createDt desc ");

                Query query = ss.createQuery("select count(d.doctorId) " + hql.toString());
                query.setProperties(params);
                total = (long) query.uniqueResult();//获取总条数

                query = ss.createQuery("select d, (select count(*) from UserRoles where userId=d.mobile and roleId='doctor') as rolesCount " + hql.toString());
                query.setProperties(params);
                query.setFirstResult(start);
                query.setMaxResults(limit);
                List<Object[]> list = query.list();
                List<Doctor> doctors = new ArrayList<Doctor>();
                for (Object[] objects : list) {
                    Doctor doctor = (Doctor) objects[0];
                    doctor.setIsOpen((long) objects[1] > 0 ? 1 : 0);
                    doctors.add(doctor);
                }
                QueryResult<Doctor> result = new QueryResult<Doctor>(total, query.getFirstResult(), query.getMaxResults(), doctors);
                setResult(result);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (QueryResult<Doctor>) action.getResult();
    }


    /**
     * 实时判断医生是否有号标记
     *
     * @param doctorId
     * @param sourceType 患者端固定传1
     * @return
     */
    public Doctor getRealTimeDoctorHaveAppointStatus(final Integer doctorId, final Integer sourceType) {
        AppointSourceDAO appointSourceDAO = DAOFactory.getDAO(AppointSourceDAO.class);
        Doctor doctor = this.getByDoctorId(doctorId);
        List<Object[]> oss = appointSourceDAO.findTotalByDcotorId(doctorId, sourceType);// 患者端固定传1
        if (oss != null && oss.size() > 0) {
            doctor.setHaveAppoint(1);
        } else {
            doctor.setHaveAppoint(0);
        }
        return doctor;
    }


    /**
     * 实时判断医生是否有号标记
     *
     * @param doctorId   医生内码
     * @param sourceType 患者端固定传1
     * @param organId    机构
     * @param departId   行政科室Id
     * @param date       日期
     * @return
     */
    public Doctor getRealTimeAppointDepartDoctorHaveAppointSource(final Integer doctorId, final Integer sourceType,
                                                                  final Integer organId, final Integer departId,
                                                                  final Date date) {
        AppointSourceDAO appointSourceDAO = DAOFactory.getDAO(AppointSourceDAO.class);
        AppointDepartDAO appointDepartDAO = DAOFactory.getDAO(AppointDepartDAO.class);
        Doctor doctor = this.getByDoctorId(doctorId);
        List<String> appointDeparts = appointDepartDAO.findListByOrganIDAndDepartID(organId, departId);
        if (appointDeparts == null || appointDeparts.isEmpty()) {
            doctor.setHaveAppoint(0);
            return doctor;
        }
        List<Object[]> oss = appointSourceDAO.findHaveSourcesByDoctorAndAppointDepartCodes(doctorId, sourceType, organId, appointDeparts, date);// 患者端固定传1
        if (oss != null && oss.size() > 0) {
            doctor.setHaveAppoint(1);
        } else {
            doctor.setHaveAppoint(0);
        }
        return doctor;
    }

    /**
     * 异步更新Doctor有无号源标记
     *
     * @param doctor
     * @param docDao
     */
    public void updateDoctorHaveAppoint(final Doctor doctor, final DoctorDAO docDao) {
        GlobalEventExecFactory.instance().getExecutor().execute(new Runnable() {
            @Override
            public void run() {
                docDao.updateHaveAppointByDoctorId(doctor.getDoctorId(), doctor.getHaveAppoint());
            }
        });
    }


    /**
     * 计算搜索医生排序字段 for patient
     * 规则定义：开关状态 desc, 咨询量 desc, rating desc
     */
    @RpcService
    public void autoCalculateDoctorSearchRating(){
        AbstractHibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String sql = "SELECT p.doctorId, IFNULL(stat, 0)*1000000 + IFNULL(count, 0)*100 + IFNULL(p.rating, 0) searchRating FROM  ( SELECT d.doctorId, d.rating,  (c.appointStatus > 0 OR c.onLineStatus > 0 OR c.professorConsultStatus > 0 OR c.recipeConsultStatus > 0) AS stat  FROM  ( SELECT  doc.doctorId, doc.rating  FROM  base_doctor doc  WHERE  (  doc.idNumber > '0'  OR doc.teams = 1  ) AND doc.STATUS = 1 ) d LEFT JOIN  bus_ConsultSet c  ON d.DoctorID = c.DoctorId ) p LEFT JOIN (SELECT  consultDoctor,  COUNT(*) AS count  FROM  bus_Consult GROUP BY  ConsultDoctor ) s ON p.DoctorId = s.ConsultDoctor";
                Query query = ss.createSQLQuery(sql);
                List<Object[]> objectList = query.list();
                int count = 0;
                String updateSql = "update Doctor set searchRating=:searchRating where doctorId =:doctorId";
                Query onebyone = ss.createQuery(updateSql);
                for(Object[] objects : objectList){
                    Integer doctorId = ConversionUtils.convert(objects[0], Integer.class);
                    Double searchRating = ConversionUtils.convert(objects[1], Double.class);
                    onebyone.setParameter("doctorId", doctorId);
                    onebyone.setParameter("searchRating", searchRating);
                    int row = onebyone.executeUpdate();
                    count = count + row;
                }
                log.info(LocalStringUtil.format("autoCalculateDoctorSearchRating update rows[{}]", count));
            }
        };
        HibernateSessionTemplate.instance().execute(action);
    }

    @RpcService
    public void updateDoctorSearchRating(final int doctorId){
        AbstractHibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String sql = "UPDATE base_doctor SET searchRating = ( SELECT IFNULL(stat, 0) * 1000000 + IFNULL(count, 0)*100 + IFNULL(p.rating, 0) searchRating FROM ( SELECT d.doctorId, d.rating, ( c.appointStatus > 0 OR c.onLineStatus > 0 OR c.professorConsultStatus > 0 OR c.recipeConsultStatus > 0 ) AS stat FROM ( SELECT doc.doctorId, doc.rating FROM base_doctor doc WHERE DoctorId = :doctorId ) d LEFT JOIN bus_ConsultSet c ON d.DoctorID = c.DoctorId ) p LEFT JOIN ( SELECT ConsultDoctor, COUNT(*) AS count FROM bus_Consult WHERE ConsultDoctor = :doctorId ) s ON p.DoctorId = s.ConsultDoctor ) WHERE DoctorId = :doctorId";
                Query query = ss.createSQLQuery(sql);
                query.setParameter("doctorId", doctorId);
                int rows = query.executeUpdate();
                log.info(LocalStringUtil.format("updateDoctorSearchRating update rows[{}]", rows));
                setResult(rows);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
    }


    /**
     * 医生信息同步
     * @param doctorDepts
     */
    @RpcService
    public void syncDoctor(List<DoctorDeptRequest> doctorDepts){
        log.info("医生信息同步开始--------"+doctorDepts.size());
        if(null!=doctorDepts && doctorDepts.size()>0){
            DepartmentDAO departmentDAO = DAOFactory.getDAO(DepartmentDAO.class);
            EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
            ConsultSetDAO consultSetDAO= DAOFactory.getDAO(ConsultSetDAO.class);
            for (DoctorDeptRequest d:doctorDepts){
                //获取内部行政科室ID
                int deptId=departmentDAO.getIdByCode(d.getDepartCode(),d.getOrganId());
                log.info("deptId--------"+deptId);
                Doctor doctorFlag=getByNameAndOrgan(d.getName(),d.getOrganId());
                if(null!=doctorFlag){
                    //有医生的情况下
                    log.info("医生的情况下更新医生信息和职业点---------"+doctorFlag.getDoctorId());
                    if(null!=d.getProfessionCode() && !"".equals(d.getProfessionCode())){
                        doctorFlag.setProfession(d.getProfessionCode());
                    }
                    if(null!=d.getProTitle() && !"".equals(d.getProTitle())){
                        doctorFlag.setProTitle(d.getProTitle());
                    }
                    if(null!=d.getIntroduce() && !"".equals(d.getIntroduce())){
                        doctorFlag.setIntroduce(d.getIntroduce());
                    }
                    update(doctorFlag);
                    //获取职业点
                    Employment employmentFlag=employmentDAO.getPrimaryEmpByDoctorId(doctorFlag.getDoctorId());
                    if(null!=employmentFlag){
                        if(null!=d.getJobNumber() && !"".equals(d.getJobNumber())){
                            employmentFlag.setJobNumber(d.getJobNumber());
                        }
                        if(0<deptId){
                            employmentFlag.setDepartment(deptId);
                        }
                        employmentDAO.update(employmentFlag);
                    }else{
                        employmentFlag=new Employment();
                        employmentFlag.setDoctorId(doctorFlag.getDoctorId());
                        employmentFlag.setOrganId(d.getOrganId());
                        employmentFlag.setJobNumber(d.getJobNumber());
                        employmentFlag.setDepartment(deptId);
                        employmentFlag.setPrimaryOrgan(true);
                        employmentFlag.setConsultationEnable(false);
                        employmentFlag.setConsultationPrice(0d);
                        employmentFlag.setClinicEnable(false);
                        employmentFlag.setClinicPrice(0d);
                        employmentFlag.setProfClinicPrice(0d);
                        employmentFlag.setSpecClinicPrice(0d);
                        employmentFlag.setTransferEnable(false);
                        employmentFlag.setTransferPrice(0d);
                        employmentFlag.setApplyTransferEnable(false);
                        employmentDAO.save(employmentFlag);
                    }
                    ConsultSet consultSet=consultSetDAO.getById(doctorFlag.getDoctorId());
                    if(null==consultSet || null==consultSet.getDoctorId()){
                        consultSet.setDoctorId(doctorFlag.getDoctorId());
                        consultSet.setOnLineStatus(0);
                        consultSet.setAppointStatus(0);
                        consultSet.setProfessorConsultStatus(0);
                        consultSet.setRecipeConsultStatus(0);
                        consultSetDAO.save(consultSet);
                    }
                }else{
                    log.info("无医生的情况下添加医生信息和职业点---------"+d.getName());
                    //没有医生的情况，添加一位新的医生
                    doctorFlag=new Doctor();
                    if(d.getProfessionCode().equalsIgnoreCase("zz")){
                        DepartmentRelationDAO departmentRelationDAO=DAOFactory.getDAO(DepartmentRelationDAO.class);
                        DepartmentRelation departmentRelation=new DepartmentRelation();
                        departmentRelation.setOrganId(d.getOrganId());
                        departmentRelation.setCode(d.getProfessionName());
                        departmentRelationDAO.save(departmentRelation);
                        continue;
                    }else{
                        doctorFlag.setProfession(d.getProfessionCode());
                    }
                    doctorFlag.setProTitle(d.getProTitle());
                    doctorFlag.setIntroduce(d.getIntroduce());
                    doctorFlag.setName(d.getName());
                    doctorFlag.setOrgan(d.getOrganId());
                    doctorFlag.setSource(0);
                    doctorFlag.setHaveAppoint(0);
                    doctorFlag.setGeneralDoctor(1);
                    doctorFlag.setStatus(1);
                    save(doctorFlag);
                    //添加新增医生职业点
                    Employment employmentFlag=new Employment();
                    employmentFlag.setDoctorId(doctorFlag.getDoctorId());
                    employmentFlag.setOrganId(d.getOrganId());
                    employmentFlag.setJobNumber(d.getJobNumber());
                    employmentFlag.setDepartment(deptId);
                    employmentFlag.setPrimaryOrgan(true);
                    employmentFlag.setConsultationEnable(false);
                    employmentFlag.setConsultationPrice(0d);
                    employmentFlag.setClinicEnable(false);
                    employmentFlag.setClinicPrice(0d);
                    employmentFlag.setProfClinicPrice(0d);
                    employmentFlag.setSpecClinicPrice(0d);
                    employmentFlag.setTransferEnable(false);
                    employmentFlag.setTransferPrice(0d);
                    employmentFlag.setApplyTransferEnable(false);
                    employmentDAO.save(employmentFlag);
                    //添加医生咨询状态
                    ConsultSet consultSet=new ConsultSet();
                    consultSet.setDoctorId(doctorFlag.getDoctorId());
                    consultSet.setOnLineStatus(0);
                    consultSet.setAppointStatus(0);
                    consultSet.setProfessorConsultStatus(0);
                    consultSet.setRecipeConsultStatus(0);
                    consultSetDAO.save(consultSet);
                }
            }
        }
    }

    @DAOMethod(sql = " from Doctor where teams =1 and status =1 ",limit = 0)
    public abstract List<Doctor> findAllTeams();

    @DAOMethod(sql = " from Doctor where teams =1 and status =1 and ",limit = 0)
    public abstract List<Doctor> findAllTeamsByName();


    /**
     * 查询机构里有效的医生集合
     * @param organId
     * @return
     */
    @DAOMethod(sql = "select count(*) from Doctor where organ=:organId and status = 1")
    public abstract Long getEffectiveDoctorByOrganId(@DAOParam("organId")Integer organId);


}
