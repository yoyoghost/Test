package eh.bus.service;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import ctd.account.Client;
import ctd.account.UserRoleToken;
import ctd.account.user.User;
import ctd.account.user.UserController;
import ctd.account.user.UserRoleTokenEntity;
import ctd.controller.exception.ControllerException;
import ctd.controller.updater.ConfigurableItemUpdater;
import ctd.dictionary.DictionaryController;
import ctd.dictionary.DictionaryItem;
import ctd.dictionary.service.DictionaryLocalService;
import ctd.dictionary.service.DictionarySliceRecordSet;
import ctd.mvc.alilife.suppport.LocationRevertUtils;
import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.persistence.support.impl.user.UserDAO;
import ctd.persistence.support.impl.user.UserRoleTokenDAO;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.activity.service.DogDaysService;
import eh.base.constant.ErrorCode;
import eh.base.constant.ServiceType;
import eh.base.constant.SystemConstant;
import eh.base.dao.*;
import eh.base.service.*;
import eh.base.service.doctor.QueryDoctorListService;
import eh.base.service.organ.QueryOrganService;
import eh.bus.dao.*;
import eh.bus.service.common.ClientPlatformEnum;
import eh.bus.service.common.CurrentUserInfo;
import eh.cdr.service.PathologicalDrugService;
import eh.cdr.service.RecipeListService;
import eh.controller.WebLogonManager;
import eh.coupon.service.CouponPushService;
import eh.entity.base.*;
import eh.entity.bus.*;
import eh.entity.bus.msg.SimpleThird;
import eh.entity.bus.msg.SimpleWxAccount;
import eh.entity.cdr.PathologicalDrug;
import eh.entity.cdr.RecipeRollingInfo;
import eh.entity.his.HisDoctorParam;
import eh.entity.mindgift.MindGift;
import eh.entity.mpi.FollowPlan;
import eh.entity.mpi.Patient;
import eh.entity.msg.Banner;
import eh.entity.wx.WXConfig;
import eh.entity.wx.WxSubscribe;
import eh.evaluation.service.EvaluationService;
import eh.mindgift.dao.MindGiftDAO;
import eh.mindgift.service.MindGiftService;
import eh.mpi.constant.FollowConstant;
import eh.mpi.constant.PatientConstant;
import eh.mpi.dao.FollowPlanDAO;
import eh.mpi.dao.PatientDAO;
import eh.mpi.service.FamilyMemberService;
import eh.mpi.service.follow.FollowPlanTriggerService;
import eh.msg.dao.InfoCollectionDAO;
import eh.op.dao.BannerDAO;
import eh.op.dao.WXConfigsDAO;
import eh.op.service.BannerService;
import eh.remote.IWXServiceInterface;
import eh.remote.IYuYueQueryServiceInterface;
import eh.util.ControllerUtil;
import eh.util.Easemob;
import eh.util.RpcAsynchronousUtil;
import eh.util.ServerDateService;
import eh.utils.ValidateUtil;
import eh.utils.params.ParamUtils;
import eh.utils.params.ParameterConstant;
import eh.wxpay.dao.WxSubscribeDAO;
import eh.wxpay.service.SubscribeService;
import org.apache.commons.codec.digest.Md5Crypt;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Query;
import org.hibernate.StatelessSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;

import java.util.*;

public class UnLoginSevice {
    private static final Logger log = LoggerFactory.getLogger(UnLoginSevice.class);
    private static final String pcPatchName = "main.zip";

    /**
     * 健康app患者注册发送验证码
     *
     * @param mobile
     * @return
     */
    @RpcService
    public String sendVCodeForNotRegisterPatient(String mobile) {
        UserDAO userDao = DAOFactory.getDAO(UserDAO.class);
        if (userDao.exist(mobile)) {
            throw new DAOException(609, "该号码已注册，请直接登录");
        }
        ValidateCodeDAO vcDao = DAOFactory.getDAO(ValidateCodeDAO.class);
        return vcDao.sendValidateCodeToPatient(mobile);

    }

    /**
     * 患者登录时补全患者信息接口
     *
     * @param loginId
     * @return
     */
    @RpcService
    public Patient createPatientForRegisteredUser(String loginId) {
        Patient patient = null;
        try {
            if (ValidateUtil.blankString(loginId)) {
                log.info("[{}] createPatientForRegisteredUser error, loginId is null", this.getClass().getSimpleName());
                throw new DAOException("loginId为空");
            }
            List<UserRoleToken> urtList = DAOFactory.getDAO(UserRoleTokenDAO.class).findByUserId(loginId);
            if (ValidateUtil.blankList(urtList)) {
                log.info("[{}] createPatientForRegisteredUser error, userRolesList is null, params:loginId[{}]", this.getClass().getSimpleName(), loginId);
                throw new DAOException("数据库中没有对应的用户角色信息");
            }
            boolean existsPatientUrtInfo = false;
            for (UserRoleToken urt : urtList) {
                if (SystemConstant.ROLES_PATIENT.equalsIgnoreCase(urt.getRoleId())) {
                    existsPatientUrtInfo = true;
                }
            }
            PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
            Patient loginIdPatient = patientDAO.getByLoginId(loginId);
            if (loginIdPatient == null) {
                Doctor doctor = DAOFactory.getDAO(DoctorDAO.class).getByMobile(loginId);
                patient = new Patient();
                patient.setLoginId(loginId);
                patient.setPatientName(doctor.getName());
                patient.setPatientSex(doctor.getGender());
                patient.setBirthday(doctor.getBirthDay());
                patient.setPatientType("1");// 1：自费
                patient.setIdcard(doctor.getIdNumber());
                patient.setMobile(doctor.getMobile());
                patient.setHomeArea(SystemConstant.DEFAULT_HOME_AREA);
                patient.setFullHomeArea(patientDAO.getFullHomeArea(patient.getHomeArea()));
                patient.setPhoto(doctor.getPhoto());
                patient.setCreateDate(new Date());
                patient.setLastModify(new Date());
                patient.setRawIdcard(doctor.getIdNumber());
                patient.setHealthProfileFlag(patient.getHealthProfileFlag() == null ? false : patient.getHealthProfileFlag());
                patient.setStatus(PatientConstant.PATIENT_STATUS_NORMAL);
                Boolean guardianFlag = patient.getGuardianFlag() == null ? false : patient.getGuardianFlag();
                patient.setGuardianFlag(guardianFlag);
                patientDAO.save(patient);
            } else {
                patient = loginIdPatient;
            }
            if (!existsPatientUrtInfo) {
                UserRoleTokenEntity dUrt = (UserRoleTokenEntity) urtList.get(0);
                UserRoleTokenEntity pUrt = new UserRoleTokenEntity();
                pUrt.setUserId(loginId);
                pUrt.setRoleId(SystemConstant.ROLES_PATIENT);
                pUrt.setTenantId(dUrt.getTenantId());
                pUrt.setManageUnit(dUrt.getTenantId());
                pUrt.setLastLoginTime(dUrt.getLastLoginTime());
                pUrt.setLastIPAddress(dUrt.getLastIPAddress());
                pUrt.setLastUserAgent(dUrt.getLastUserAgent());
                pUrt.setProperty("patient", patient);
                ConfigurableItemUpdater updater = (ConfigurableItemUpdater) UserController
                        .instance().getUpdater();
                updater.createItem(loginId, pUrt);
            }

        } catch (Exception e) {
            log.error("[{}] createPatientForRegisteredUser error, params:loginId[{}], errorMessage[{}], stackTrace[{}]", this.getClass().getSimpleName(), loginId, e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
        }
        return patient;
    }

    /**
     * 创建患者用户
     *
     * @param p
     * @param password
     * @return
     * @throws DAOException
     * @author ZX
     * @date 2015-4-15  上午10:11:18
     */
    @RpcService
    public Patient createPatientUser(final Patient p, final String password)
            throws DAOException {

        log.info("[{}] createPatientUser, params: p[{}], password[{}]", JSONUtils.toString(p), password);
        HibernateStatelessResultAction<HashMap<String, Object>> action = new AbstractHibernateStatelessResultAction<HashMap<String, Object>>() {
            @SuppressWarnings({"rawtypes", "unchecked"})
            @Override
            public void execute(StatelessSession statelessSession)
                    throws Exception {
                HashMap<String, Object> map = new HashMap<String, Object>();

                UserDAO userDAO = DAOFactory.getDAO(UserDAO.class);
                PatientDAO patDao = DAOFactory.getDAO(PatientDAO.class);
                // 未实现:更新或插入Patient表
                if (org.apache.commons.lang3.StringUtils.isEmpty(p.getFullHomeArea())) {
                    p.setFullHomeArea(patDao.getFullHomeArea(p.getHomeArea()));
                }
                Patient pat = patDao.createWXPatientUser(p);
                p.setMpiId(pat.getMpiId());
                String rid = "patient";
                String uid = p.getMobile();
                UserRoleTokenEntity ure = new UserRoleTokenEntity();
                ure.setRoleId(rid);
                ure.setUserId(uid);
                ure.setTenantId("eh");
                ure.setManageUnit("eh");
                User u = userDAO.get(uid);
                Boolean isNewUser = false;
                ConfigurableItemUpdater updater = (ConfigurableItemUpdater) UserController
                        .instance().getUpdater();

                if (u == null) {
                    u = new User();
                    u.setId(uid);
                    u.setName(p.getPatientName());
                    u.setCreateDt(new Date());
                    u.setLastModify(System.currentTimeMillis());
                    u.setPlainPassword(password);
                    u.setStatus("1");

                    userDAO.createUser(u, ure);
                    isNewUser = true;
                } else {
                    List<UserRoleToken> urts = u.findUserRoleTokenByRoleId(rid);
                    if (urts.size() > 0) {
                        ure = (UserRoleTokenEntity) urts.get(0);
                    } else {
                        ure = (UserRoleTokenEntity) updater
                                .createItem(uid, ure);
                    }
                }
                ure.setProperty("patient", pat);
                if (!isNewUser) {
                    updater.updateItem(uid, ure);
                }
                map.put("ure", ure);
                map.put("isNewUser", isNewUser);
                setResult(map);
            }
        };

        HibernateSessionTemplate.instance().executeTrans(action);
        HashMap<String, Object> map = action.getResult();
        UserRoleToken ure = (UserRoleToken) map.get("ure");
        // 注册患者环信账户
        if (ure != null) {
            String userName = Easemob.getPatient(ure.getId());
            Easemob.registUser(userName, SystemConstant.EASEMOB_PATIENT_PWD);

            p.setUrt(ure.getId());

            if ((Boolean) map.get("isNewUser")) {
                //wx2.7 注册发放优惠劵
                CouponPushService couponService = new CouponPushService();
                couponService.sendRegisteCouponMsg(ure.getId(), "纳里健康APP端注册新用户发送优惠劵");
            }
        }

        //2017-7-1 10:35:42 上海六院就诊人优化v1.0，创建用户的时候，添加自己为就诊人
        FamilyMemberService familyMemberService = AppContextHolder.getBean("eh.familyMemberService", FamilyMemberService.class);
        familyMemberService.saveSelf(p.getMpiId());

        return p;

//        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
//        return patientDAO.createPatientUser(patient, password);
    }

    /**
     * 联盟机构查询服务--未登录时调用
     *
     * @param organId
     * @param buesType
     * @return
     * @author hyj
     */
    @RpcService
    public List<Organ> queryRelaOrganInUnLogin(int organId, String buesType) {
        OrganDAO dao = DAOFactory.getDAO(OrganDAO.class);
        List<Organ> list = dao.queryRelaOrgan(organId, buesType);
        return list;
    }

    /**
     * 医院专科类别查询服务--未登录时调用
     *
     * @param organId
     * @param deptType (1临床科室,2挂号科室)
     * @return
     * @author hyj
     */
    @RpcService
    public List<Object> findValidProfessionInUnLogin(Integer organId,
                                                     int deptType) {
        List<Object> result = new ArrayList<Object>();
        DepartmentDAO dao = DAOFactory.getDAO(DepartmentDAO.class);
        result = dao.findValidProfession(organId, deptType);
        return result;
    }

    /**
     * 医院有效科室查询服务--未登录时调用
     *
     * @param organId
     * @param professionCode
     * @param bussType       (0全部，1转诊，2会诊)
     * @return
     * @author hyj
     */
    @RpcService
    public List<Department> findValidDepartmentInUnLogin(Integer organId,
                                                         String professionCode, int bussType) {
        DepartmentDAO dao = DAOFactory.getDAO(DepartmentDAO.class);
        List<Department> list = dao.findValidDepartment(organId,
                professionCode, bussType);
        return list;
    }

    /**
     * 转诊会诊医生列表查询服务--未登录时调用
     *
     * @param buesType   1转诊，2会诊,0咨询预约
     * @param department
     * @param organId
     * @return
     * @author hyj
     */
    @RpcService
    public List<Doctor> queryDoctorListInUnLogin(int buesType,
                                                 Integer department, int organId) {
        QueryDoctorListDAO dao = DAOFactory.getDAO(QueryDoctorListDAO.class);
        List<Doctor> list = dao.queryDoctorList(buesType, department, organId);
        return list;
    }

    /**
     * 根据手机号获取mpiId wx3.8.2
     * @param doctorId
     * @param mobile
     * @return
     */
    @RpcService
    public boolean finishReport(Integer doctorId, String mobile) {
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Doctor doctor = doctorDAO.getByDoctorId(doctorId);
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        Patient patient = patientDAO.getByLoginId(mobile);
        if(null == patient){
            return false;
        }
        FollowPlanDAO followPlanDAO = DAOFactory.getDAO(FollowPlanDAO.class);
        if(doctor.getTeams()){
            DoctorGroupDAO doctorGroupDAO = DAOFactory.getDAO(DoctorGroupDAO.class);
            doctorId=doctorGroupDAO.getLeaderByDoctorId(doctorId);
        }
        List<FollowPlan> plans = followPlanDAO.findPatientReportPlan(patient.getMpiId(), doctorId);
        if (ValidateUtil.notBlankList(plans)) {
            return true;
        }
        return false;
    }

    /**
     * 医生详情查询服务--未登录时调用
     *
     * @param id
     * @return
     * @author hyj
     */
    @RpcService
    public Doctor getByDoctorIdInUnLogin(int id) {
        DoctorDAO dao = DAOFactory.getDAO(DoctorDAO.class);
        Doctor d = dao.getByDoctorId(id);
        return d;
    }

    /**
     * 获取医生分享页面的相关数据
     *
     * @param id
     * @return
     */
    @RpcService
    public Map<String, Object> getShareDocInfo(int doctorId, String appId) {
        Map<String, Object> map = new HashMap<String, Object>();

        DoctorDAO dao = DAOFactory.getDAO(DoctorDAO.class);
        Doctor d = dao.get(doctorId);
        map.put("doctor", d);


        QRInfoService qrservice = AppContextHolder.getBean("eh.qrInfoService", QRInfoService.class);
        QRInfo info = qrservice.getDocQRInfo(doctorId, appId);
        map.put("qrInfo", info);

        return map;
    }

    /**
     * 职称字典-未登陆情况下调用
     *
     * @return
     * @author LF
     */
    @RpcService
    public List<DictionaryItem> getProTitle() {
        DoctorDAO docDao = DAOFactory.getDAO(DoctorDAO.class);
        return docDao.getProTitle();
    }

    /**
     * 根据医院配置返回医生职称列表,
     * 机构配置职称时返回：hasConfig:true  以及医院配置的职称字典
     * 机构未配置职称时返回：hasConfig:false 以及所有医生职称字典
     */
    @RpcService
    public Map<String,Object> getProTitleByOrganId(Integer organId) {
        ProTitleDAO proTitleDAO = DAOFactory.getDAO(ProTitleDAO.class);
        OrganProTitleService organProTitleService = AppContextHolder.getBean("eh.organProTitleService",OrganProTitleService.class);
        List<OrganProTitle> list = organProTitleService.findByOrganId(organId);
        List<DictionaryItem> dicList = new ArrayList<DictionaryItem>();
        Map<String,Object> paramsMap=Maps.newHashMap();
        if(!list.isEmpty()){
            for(OrganProTitle op:list){
                dicList.add(proTitleDAO.getDictionaryItem(op.getProTitleId()));
            }
            paramsMap.put("hasConfig",!list.isEmpty());
            paramsMap.put("protitleList",dicList);
            return paramsMap;
        }else{
            paramsMap.put("hasConfig",!list.isEmpty());
            paramsMap.put("protitleList",proTitleDAO.findAllDictionaryItem(1,26));
            return paramsMap;

        }
    }

    /**
     * 未登陆时获取属地区域
     *
     * @param parentKey
     * @param sliceType
     */
    @RpcService
    public List<DictionaryItem> getAddrArea(String parentKey, int sliceType) {
        ConsultDAO dao = DAOFactory.getDAO(ConsultDAO.class);
        return dao.getAddrArea(parentKey, sliceType);
    }

    /**
     * 未登录时获取病人类型
     *
     * @param parentKey
     * @param sliceType
     */
    @RpcService
    public List<DictionaryItem> getPatientType(String parentKey, int sliceType) {
        ConsultDAO dao = DAOFactory.getDAO(ConsultDAO.class);
        return dao.getPatientType(parentKey, sliceType);
    }

    /**
     * 医生咨询设置查询服务--未登录时调用
     *
     * @param id
     * @return
     * @author hyj
     */
    @RpcService
    public ConsultSet getByIdInUnLogin(int id) {
        ConsultSetDAO dao = DAOFactory.getDAO(ConsultSetDAO.class);
        ConsultSet c = dao.getById(id);
        return c;
    }

    /**
     * 转诊统计查询
     *
     * @param startTime
     * @param endTime
     * @param tran
     * @return
     * @author ZX
     * @date 2015-5-8 下午2:25:24
     */
    @RpcService
    public List<TransferAndPatient> findTransferWithStatic(Date startTime,
                                                           Date endTime, Transfer tran, int start) {
        TransferDAO dao = DAOFactory.getDAO(TransferDAO.class);
        return dao.findTransferWithStatic(startTime, endTime, tran, start);
    }

    /**
     * 转诊统计查询
     *
     * @param startTime
     * @param endTime
     * @param tran
     * @return
     * @author ZX
     * @date 2015-5-8 下午2:25:24
     */
    @RpcService
    public long getTransferNumWithStatic(Date startTime, Date endTime,
                                         Transfer tran) {
        TransferDAO dao = DAOFactory.getDAO(TransferDAO.class);
        return dao.getNumWithStatic(startTime, endTime, tran);
    }

    /**
     * 会诊统计查询
     *
     * @param startTime
     * @param endTime
     * @param mc
     * @param mr
     * @param start
     * @return
     * @author ZX
     * @date 2015-5-8 下午2:25:24
     */
    @RpcService
    public List<MeetClinicAndResult> findMeetClinicAndResultWithStatic(
            Date startTime, Date endTime, MeetClinic mc, MeetClinicResult mr,
            int start) {
        QueryMeetClinicHisDAO dao = DAOFactory
                .getDAO(QueryMeetClinicHisDAO.class);
        return dao.findMeetClinicAndResultWithStatic(startTime, endTime, mc,
                mr, start);
    }

    /**
     * 会诊统计查询记录数
     *
     * @param startTime
     * @param endTime
     * @param mc
     * @param mr
     * @return
     * @author ZX
     * @date 2015-5-8 下午2:25:24
     */
    @RpcService
    public long getMeetClinicNumWithStatic(Date startTime, Date endTime,
                                           MeetClinic mc, MeetClinicResult mr) {
        QueryMeetClinicHisDAO dao = DAOFactory
                .getDAO(QueryMeetClinicHisDAO.class);
        return dao.getNumWithStatic(startTime, endTime, mc, mr);
    }

    /**
     * 根据姓名查询医生
     *
     * @param name
     * @return
     * @author ZX
     * @date 2015-5-12 下午2:03:34
     */
    @RpcService
    public Doctor getDoctorByNameAndOrgan(String name, int organ) {
        DoctorDAO dao = DAOFactory.getDAO(DoctorDAO.class);
        return dao.getByNameAndOrgan(name, organ);
    }

    /**
     * 根据姓名查询医生
     *
     * @param name
     * @return
     * @author ZX
     * @date 2015-5-12 下午2:03:34
     */
    @RpcService
    public Doctor getDoctorByName(String name) {
        DoctorDAO dao = DAOFactory.getDAO(DoctorDAO.class);
        return dao.getByName(name);
    }

    /**
     * 预约查询
     *
     * @param startTime
     * @param endTime
     * @param ar
     * @param start
     * @return
     * @author ZX
     * @date 2015-5-19 上午9:58:02
     */
    @RpcService
    public List<AppointRecordAndDoctors> findAppointRecordWithStatic(
            Date startTime, Date endTime, AppointRecord ar, int start) {

        AppointRecordDAO dao = DAOFactory.getDAO(AppointRecordDAO.class);
        return dao.findAppointRecordWithStatic(startTime, endTime, ar, start);
    }

    /**
     * 预约统计记录数
     *
     * @param startTime
     * @param endTime
     * @param ar
     * @return
     * @author ZX
     * @date 2015-5-19 上午10:02:12
     */
    @RpcService
    public long getAppointRecordNumWithStatic(final Date startTime,
                                              final Date endTime, final AppointRecord ar) {
        AppointRecordDAO dao = DAOFactory.getDAO(AppointRecordDAO.class);
        return dao.getNumWithStatic(startTime, endTime, ar);
    }

    /**
     * 咨询查询
     *
     * @param startTime
     * @param endTime
     * @param consult
     * @param start
     * @return
     * @author ZX
     * @date 2015-6-23 下午9:06:09
     */
    @RpcService
    public List<ConsultAndPatients> findConsultWithStatic(Date startTime,
                                                          Date endTime, Consult consult, int start) {

        ConsultDAO dao = DAOFactory.getDAO(ConsultDAO.class);
        return dao.findConsultWithStatic(startTime, endTime, consult, start);
    }

    /**
     * 咨询统计记录数
     *
     * @param startTime
     * @param endTime
     * @param consult
     * @return
     * @author ZX
     * @date 2015-5-19 上午10:02:12
     */
    @RpcService
    public long getConsultNumWithStatic(final Date startTime,
                                        final Date endTime, final Consult consult) {
        ConsultDAO dao = DAOFactory.getDAO(ConsultDAO.class);
        return dao.getNumWithStatic(startTime, endTime, consult);
    }

    /**
     * 统计数量
     *
     * @param startTime
     * @param endTime
     * @return
     * @author ZX
     * @date 2015-6-24 上午11:20:26
     */
    @RpcService
    public HashMap<String, Long> getStaticNum(final Date startTime,
                                              final Date endTime) {
        HashMap<String, Long> returnHash = new HashMap<String, Long>();

        // 预约统计
        AppointRecordDAO dao = DAOFactory.getDAO(AppointRecordDAO.class);

        // 总申请数
        AppointRecord ar = new AppointRecord();
        long appointAllNum = dao.getNumWithStatic(startTime, endTime, ar);
        returnHash.put("appointAllNum", appointAllNum);

        // 医院预约确认中
        ar.setAppointStatus(9);
        ar.setAppointRoad(5);
        long appointing = dao.getNumWithStatic(startTime, endTime, ar);
        returnHash.put("appointing", appointing);

        // 医院转诊预约确认中
        ar.setAppointStatus(9);
        ar.setAppointRoad(6);
        long transferAppointing = dao.getNumWithStatic(startTime, endTime, ar);
        returnHash.put("transferAppointing", transferAppointing);

        // 预约成功
        ar.setAppointStatus(0);
        ar.setAppointRoad(5);
        long appointSuccess = dao.getNumWithStatic(startTime, endTime, ar);
        returnHash.put("appointSuccess", appointSuccess);

        // 转诊预约成功
        ar.setAppointStatus(0);
        ar.setAppointRoad(6);
        long transferAppointSuccess = dao.getNumWithStatic(startTime, endTime,
                ar);
        returnHash.put("transferAppointSuccess", transferAppointSuccess);

        // 预约取消
        ar.setAppointStatus(2);
        ar.setAppointRoad(5);
        long appoinFaile = dao.getNumWithStatic(startTime, endTime, ar);
        returnHash.put("appoinFaile", appoinFaile);

        // 转诊预约取消
        ar.setAppointStatus(2);
        ar.setAppointRoad(6);
        long transferAppoinFaile = dao.getNumWithStatic(startTime, endTime, ar);
        returnHash.put("transferAppoinFaile", transferAppoinFaile);

        TransferDAO transferDao = DAOFactory.getDAO(TransferDAO.class);

        // 转诊总数
        Transfer tran = new Transfer();
        long transferAllNum = transferDao.getNumWithStatic(startTime, endTime,
                tran);
        returnHash.put("transferAllNum", transferAllNum);

        // 转诊未处理
        tran.setTransferStatus(0);
        long transferUndo = transferDao.getNumWithStatic(startTime, endTime,
                tran);
        returnHash.put("transferUndo", transferUndo);

        // 转诊处理中
        tran.setTransferStatus(1);
        long transfering = transferDao.getNumWithStatic(startTime, endTime,
                tran);
        returnHash.put("transfering", transfering);

        // 转诊取消
        tran.setTransferStatus(9);
        long transferCancel = transferDao.getNumWithStatic(startTime, endTime,
                tran);
        returnHash.put("transferCancel", transferCancel);

        // 转诊医院确认中
        tran.setTransferStatus(8);
        long transferHosing = transferDao.getNumWithStatic(startTime, endTime,
                tran);
        returnHash.put("transferHosing", transferHosing);

        // 转诊接收
        tran.setTransferStatus(2);
        tran.setTransferResult(1);
        long transferConfirm = transferDao.getNumWithStatic(startTime, endTime,
                tran);
        returnHash.put("transferConfirm", transferConfirm);

        // 转诊拒绝
        tran.setTransferStatus(2);
        tran.setTransferResult(2);
        long transferDeny = transferDao.getNumWithStatic(startTime, endTime,
                tran);
        returnHash.put("transferDeny", transferDeny);

        ConsultDAO consultDao = DAOFactory.getDAO(ConsultDAO.class);

        // 咨询总数
        Consult consult = new Consult();
        long consultAllNum = consultDao.getNumWithStatic(startTime, endTime,
                consult);
        returnHash.put("consultAllNum", consultAllNum);

        // 咨询待处理
        consult.setConsultStatus(0);
        long consultUndo = consultDao.getNumWithStatic(startTime, endTime,
                consult);
        returnHash.put("consultUndo", consultUndo);

        // 咨询处理中
        consult.setConsultStatus(1);
        long consulting = consultDao.getNumWithStatic(startTime, endTime,
                consult);
        returnHash.put("consulting", consulting);

        // 咨询结束
        consult.setConsultStatus(2);
        long consulEnd = consultDao.getNumWithStatic(startTime, endTime,
                consult);
        returnHash.put("consulEnd", consulEnd);

        // 咨询拒绝
        consult.setConsultStatus(3);
        long consultDeny = consultDao.getNumWithStatic(startTime, endTime,
                consult);
        returnHash.put("consultDeny", consultDeny);

        // 咨询取消
        consult.setConsultStatus(9);
        long consultCancel = consultDao.getNumWithStatic(startTime, endTime,
                consult);
        returnHash.put("consultCancel", consultCancel);

        QueryMeetClinicHisDAO MeetClinicRecordDao = DAOFactory
                .getDAO(QueryMeetClinicHisDAO.class);

        // 会诊执行单总数
        MeetClinicResult mr = new MeetClinicResult();
        MeetClinic mc = new MeetClinic();
        long mrAllNum = MeetClinicRecordDao.getNumWithStatic(startTime,
                endTime, mc, mr);
        returnHash.put("mrAllNum", mrAllNum);

        // 执行单未处理
        mr.setExeStatus(0);
        long mrUndo = MeetClinicRecordDao.getNumWithStatic(startTime, endTime,
                mc, mr);
        returnHash.put("mrUndo", mrUndo);

        // 执行单处理中
        mr.setExeStatus(1);
        long mring = MeetClinicRecordDao.getNumWithStatic(startTime, endTime,
                mc, mr);
        returnHash.put("mring", mring);

        // 执行单已会诊
        mr.setExeStatus(2);
        long mrEnd = MeetClinicRecordDao.getNumWithStatic(startTime, endTime,
                mc, mr);
        returnHash.put("mrEnd", mrEnd);

        // 执行单拒绝
        mr.setExeStatus(8);
        long mrDeny = MeetClinicRecordDao.getNumWithStatic(startTime, endTime,
                mc, mr);
        returnHash.put("mrDeny", mrDeny);

        // 执行单取消
        mr.setExeStatus(9);
        long mrCancel = MeetClinicRecordDao.getNumWithStatic(startTime,
                endTime, mc, mr);
        returnHash.put("mrCancel", mrCancel);

        // 会诊申请单总数
        mr = new MeetClinicResult();
        mc = new MeetClinic();
        long mcAllNum = MeetClinicRecordDao.getRequestNumWithStatic(startTime,
                endTime, mc, mr);
        returnHash.put("mcAllNum", mcAllNum);

        // 会诊单待处理
        mc.setMeetClinicStatus(0);
        long mcUndo = MeetClinicRecordDao.getRequestNumWithStatic(startTime,
                endTime, mc, mr);
        returnHash.put("mcUndo", mcUndo);

        // 会诊单会诊中
        mc.setMeetClinicStatus(1);
        long mcing = MeetClinicRecordDao.getRequestNumWithStatic(startTime,
                endTime, mc, mr);
        returnHash.put("mcing", mcing);

        // 会诊单已完成
        mc.setMeetClinicStatus(2);
        long mcEnd = MeetClinicRecordDao.getRequestNumWithStatic(startTime,
                endTime, mc, mr);
        returnHash.put("mcEnd", mcEnd);

        // 会诊单取消
        mc.setMeetClinicStatus(9);
        long mcCancel = MeetClinicRecordDao.getRequestNumWithStatic(startTime,
                endTime, mc, mr);
        returnHash.put("mcCancel", mcCancel);

        return returnHash;
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
        DoctorDAO dao = DAOFactory.getDAO(DoctorDAO.class);
        return dao.intelligentreDoctorsForUnLogin(homeArea);
    }

    /**
     * 判断是否有其他角色
     *
     * @return
     * @author ZX
     * @date 2015-7-13 下午4:20:16
     */
    @RpcService
    public boolean hasOtherUser(String userId, String roles) {
        UserRoleTokenDAO tokenDao = DAOFactory.getDAO(UserRoleTokenDAO.class);
        List<UserRoleToken> list = tokenDao.findByUserId(userId);
        int num = 0;
        for (UserRoleToken userRoleToken : list) {
            if (!userRoleToken.getRoleId().equals(roles)) {
                num = num + 1;
            }
        }

        if (num > 0) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * 供前端调取医院列表--未登陆时调用
     *
     * @param addr
     * @return
     * @author hyj
     */
    @RpcService
    public List<Organ> findByAddrAreaLikeInUnLogin(String addr) {
        List<Organ> list = new ArrayList<Organ>();
        OrganDAO dao = DAOFactory.getDAO(OrganDAO.class);
        list = dao.findByAddrAreaLike(addr);
        return list;
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
     * @author hyj
     */
    @RpcService
    public List<Doctor> searchDoctorInUnLogin(String profession,
                                              String addrArea, String domain, String name, Integer onLineStatus,
                                              Integer haveAppoint, int startPage) {
        List<Doctor> docList = new ArrayList<Doctor>();
        DoctorDAO dao = DAOFactory.getDAO(DoctorDAO.class);
        docList = dao.searchDoctor(profession, addrArea, domain, name,
                onLineStatus, haveAppoint, startPage);
        return docList;
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
     * @author hyj
     */
    @RpcService
    public List<Doctor> searchDoctorBussInUnLogin(String profession,
                                                  String addrArea, String domain, String name, Integer onLineStatus,
                                                  Integer haveAppoint, int startPage, int busId) {
        List<Doctor> docList = new ArrayList<Doctor>();
        DoctorDAO dao = DAOFactory.getDAO(DoctorDAO.class);
        docList = dao.searchDoctorBuss(profession, addrArea, domain, name,
                onLineStatus, haveAppoint, startPage, busId);
        return docList;
    }

    /**
     * 获取专科代码字典服务--未登陆时调用
     *
     * @param parentKey
     * @author hyj
     */
    @RpcService
    public List<DictionaryItem> getProfession(String parentKey) {
        DictionaryLocalService ser = (DictionaryLocalService) AppContextHolder.getBean("dictionaryService");
        List<DictionaryItem> list = new ArrayList<DictionaryItem>();
        try {
            DictionarySliceRecordSet var = ser.getSlice(
                    "eh.base.dictionary.Profession", parentKey, 3, "", 0, 0);
            list = var.getItems();

        } catch (ControllerException e) {
            log.error("getProfession--->" + e);
        }
        return list;
    }

    /**
     * @param @param  parentKey
     * @param @return
     * @return List<DictionaryItem>
     * @throws
     * @Class eh.bus.service.UnLoginSevice.java
     * @Title: getAllProfession
     * @Description: TODO 获取所有专科代码字典服务
     * @author AngryKitty
     * @Date 2016-1-8上午11:19:58
     */
    @RpcService
    public List<DictionaryItem> getAllProfession(String parentKey) {
        DictionaryLocalService ser = AppContextHolder.getBean("dictionaryService", DictionaryLocalService.class);
        List<DictionaryItem> list = new ArrayList<DictionaryItem>();
        try {
            DictionarySliceRecordSet var = ser.getSlice(
                    "eh.base.dictionary.Profession", parentKey, 0, "", 0, 0);
            list = var.getItems();

        } catch (ControllerException e) {
            log.error("getAllProfession--->" + e);
        }
        return list;
    }

    /**
     * 获取一个登陆号所有的登录信息
     *
     * @param userId
     * @return
     * @author ZX
     * @date 2015-7-21 上午10:56:01
     */
    @RpcService
    public HashMap<String, HashMap<String, Object>> getAllUser(String userId) {
        HashMap<String, HashMap<String, Object>> returnHash = new HashMap<String, HashMap<String, Object>>();

        UserRoleTokenDAO tokenDao = DAOFactory.getDAO(UserRoleTokenDAO.class);
        List<UserRoleToken> list = tokenDao.findByUserId(userId);

        for (UserRoleToken userRoleToken : list) {
            String role = userRoleToken.getRoleId();
            HashMap<String, Object> patientInfo = new HashMap<String, Object>();

            if (role.equals("doctor")) {
                Doctor d = DAOFactory.getDAO(DoctorDAO.class).getByMobile(
                        userRoleToken.getUserId());
                if (d == null) {
                    continue;
                }
                // 添加相对应的信息
                String idcard = d.getIdNumber();
                patientInfo.put("idcard", d.getIdNumber());
                patientInfo.put("name", d.getName());

                // 根据身份证取行不
                if (idcard.length() == 15) {
                    int idcardsex = Integer.parseInt(idcard.substring(idcard
                            .length() - 1));
                    String gender = idcardsex % 2 == 0 ? "2" : "1";
                    patientInfo.put("sex", gender);

                    String sexText = gender.equals("2") ? "女" : "男";
                    patientInfo.put("sexText", sexText);

                    String idcardbirthday = "19" + idcard.substring(6, 8) + "-"
                            + idcard.substring(8, 10) + "-"
                            + idcard.substring(10, 12);
                    patientInfo.put("birth", idcardbirthday);

                } else {
                    int idcardsex = Integer.parseInt(idcard.substring(
                            idcard.length() - 2, idcard.length() - 1));
                    String gender = idcardsex % 2 == 0 ? "2" : "1";
                    patientInfo.put("sex", gender);

                    String sexText = gender.equals("2") ? "女" : "男";
                    patientInfo.put("sexText", sexText);

                    String idcardbirthday = idcard.substring(6, 10) + "-"
                            + idcard.substring(10, 12) + "-"
                            + idcard.substring(12, 14);
                    patientInfo.put("birth", idcardbirthday);
                }

            } else if (role.equals("patient")) {
                Patient p = DAOFactory.getDAO(PatientDAO.class).getByLoginId(
                        userRoleToken.getUserId());
                if (p == null) {
                    continue;
                }

                // 添加相对应的信息
                patientInfo.put("idcard", p.getIdcard());
                patientInfo.put("name", p.getPatientName());
                patientInfo.put("sex", p.getPatientSex());
                patientInfo.put("birth", p.getBirthday());

                String sexText = p.getPatientSex().equals("2") ? "女" : "男";
                patientInfo.put("sexText", sexText);

            } else if (role.equals("admin")) {

            }
            returnHash.put(role, patientInfo);
        }

        return returnHash;

    }

    /**
     * 刷新字典缓存
     *
     * @author ZX
     * @date 2015-8-12 下午9:29:34
     */
    @RpcService
    public void reloadAllDictionary() {
        ControllerUtil.reloadAllDictionary();
    }

    /**
     * 刷新指定字典
     *
     * @param id ='eh.base.dictionary.Organ'
     * @author ZX
     * @date 2015-8-17 下午6:00:49
     */
    @RpcService
    public void reloadDictionaryById(String id) throws ControllerException {
        ControllerUtil.reloadDictionaryById(id);
    }

    /**
     * 刷新指定字典(通知其他副服务器)
     *
     * @param id ='eh.base.dictionary.Organ'
     * @author ZX
     * @date 2015-8-17 下午6:00:49
     */
    @RpcService
    public void reloadDictionary(String id) {
        try {
            DictionaryController.instance().getUpdater().reload(id);
        } catch (Exception e) {
            log.error("reloadDictionary--->" + e);
        }
    }

    /**
     * 刷新指定wxapp
     *
     * @param id
     * @throws ControllerException
     */
    @RpcService
    public void reloadWXAppByAppId(String appId) throws ControllerException {
        ControllerUtil.reloadWXAppByAppId(appId);
    }

    /**
     * 获取机构挂号科室列表
     *
     * @param organId
     * @return
     */
    @RpcService
    public List<AppointDepart> findAllAppointDepartByOrganId(final int organId) {
        AppointDepartDAO dao = DAOFactory.getDAO(AppointDepartDAO.class);
        return dao.findAllByOrganId(organId);
    }

    /**
     * 获取医生号源信息
     *
     * @param inAddrArea ,inOrganId,outDoctorId,outWorkDate, workType
     * @return
     */
    @RpcService
    public Map<String, Object> queryDoctorSourceCloud(final String inAddrArea,
                                                      final Integer inOrganId, final Integer outDoctorId,
                                                      final Date outWorkDate, final Integer workType) {
        AppointSourceDAO dao = DAOFactory.getDAO(AppointSourceDAO.class);
        return dao.queryDoctorSourceCloud(inAddrArea, inOrganId, outDoctorId,
                outWorkDate, workType);
    }

    /**
     * 获取医生列表
     *
     * @param profession
     * @return
     */
    @RpcService
    public List<Doctor> findByProfessionLike(String profession) {
        DoctorDAO dao = DAOFactory.getDAO(DoctorDAO.class);
        return dao.findByProfessionLike(profession);
    }

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
        DoctorDAO docDao = DAOFactory.getDAO(DoctorDAO.class);
        return docDao.sendVCode(mobile);
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
     * @Description: 医生注册过程 |houxr 2016-08-01修改为机构是[其他]的机构也能注册医生,并且保存其他机构名称：otherName
     * @author AngryKitty
     * @Date 2015-11-18下午4:39:50
     */
    @RpcService
    public void RegisteredDoctorAccount(String mobile, String name,
                                        String IDCard, int organ, String profession, String proTitle,
                                        Integer invitationCode, String otherName) {
        DoctorDAO docDao = DAOFactory.getDAO(DoctorDAO.class);
        if (ObjectUtils.nullSafeEquals(0, organ)) {//其他机构注册
            docDao.RegisteredDoctorAccountByOtherOrgan(mobile, name, IDCard, profession, proTitle, invitationCode, otherName);
        } else {
            docDao.RegisteredDoctorAccount(mobile, name, IDCard, organ, profession, proTitle, invitationCode);
        }
    }

    /**
     * 医生账号注册接口，供第三方调用，返回医生编号
     *
     * @param mobile
     * @param name
     * @param IDCard
     * @param organ
     * @param profession
     * @param proTitle
     * @param invitationCode
     * @param otherName
     * @return 医生编号
     */
    @RpcService
    public Integer registeredDoctorAccountThird(String mobile, String name,
                                                String IDCard, int organ, String profession, String proTitle,
                                                Integer invitationCode, String otherName) {
        RegisteredDoctorAccount(mobile, name, IDCard, organ, profession, proTitle, invitationCode, otherName);
        DoctorDAO docDao = DAOFactory.getDAO(DoctorDAO.class);
        Doctor doctor = docDao.getByIdNumber(IDCard);
        if (doctor != null) {
            return doctor.getDoctorId();
        } else {
            return null;
        }
    }

    /**
     * 患者端手机验证
     *
     * @param mobile
     * @return
     * @author zhangx
     * @date 2016-2-18 下午3:32:18
     */
    @RpcService
    public String sendPatientVCode(String mobile) {
        ValidateCodeDAO vcDao = DAOFactory.getDAO(ValidateCodeDAO.class);
        return vcDao.sendValidateCodeToPatient(mobile);
    }

    /**
     * 微信注册验证码
     *
     * @param mobile
     * @return
     */
    @RpcService
    public String sendPatientWxRegisterVCode(String mobile) {
        ValidateCodeDAO vcDao = DAOFactory.getDAO(ValidateCodeDAO.class);
        return vcDao.sendRegisterValidateCodeToRoles(mobile, SystemConstant.ROLES_PATIENT);
    }

    /**
     * 未登录状态下推荐医生
     *
     * @param homeArea 属地区域
     * @return Map<String, List<Doctor>>
     * @throws ControllerException
     * @author luf
     */
    @RpcService
    public Map<String, List<Doctor>> doctorsRecommendedUnLogin(String homeArea)
            throws ControllerException {
        DoctorDAO dao = DAOFactory.getDAO(DoctorDAO.class);
        return dao.doctorsRecommendedUnLogin(homeArea);
    }

    /**
     * 未登录状态下推荐医生-找医生
     *
     * @param homeArea 属地区域
     * @param flag     标志-0咨询1预约
     * @return Map<String, List<Doctor>>
     * @author luf
     */
    @RpcService
    public List<HashMap<String, Object>> doctorsRecommendedUnLogin2(
            String homeArea, int flag) {
        DoctorDAO dao = DAOFactory.getDAO(DoctorDAO.class);
        return dao.doctorsRecommendedUnLogin2(homeArea, flag);
    }

    /**
     * 推荐医生-找医生页面
     * 将未登录的推荐医生逻辑和登录后的推荐医生整合
     *
     * @param homeArea
     * @param age
     * @param patientGender
     * @param flag          标志-0咨询1预约
     * @return List<Object>
     * @author luf 2016-2-25
     */
    @RpcService
    public List<HashMap<String, Object>> consultOrAppointRecommended(String homeArea, Integer age, String patientGender, int flag) {
        UserRoleToken urt = UserRoleToken.getCurrent();
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);

        List<HashMap<String, Object>> list = new ArrayList<HashMap<String, Object>>();
        if (urt == null) {
            //未登录
            list = doctorDAO.doctorsRecommendedUnLogin2(homeArea, flag);
        } else {
            //登录
            list = doctorDAO.consultOrAppointRecommended(homeArea, age, patientGender, flag);
        }
        return list;
    }

    /**
     * @param homeArea    地点的区域编码
     * @param serviceType 服务类型 0-> 咨询 1->预约
     * @param start       起始页
     * @param limit       限制条数
     * @return
     * @author cuill
     * @date 2017/6/30
     */
    @RpcService
    public List<HashMap<String, Object>> consultOrAppointExpertTeam(String homeArea, Integer serviceType, int start, int limit) {
      DoctorService doctorService = AppContextHolder.getBean("eh.doctorService", DoctorService.class);
      return doctorService.consultOrAppointExpertTeam(homeArea, serviceType, start, limit);
    }

        /**
         * 首页医生推荐-更多
         * <p>
         * eh.bus.service
         *
         * @param homeArea
         * @param profession
         * @return List<HashMap<String,Object>>
         * @author luf 2016-1-25
         */
    @RpcService
    public List<HashMap<String, Object>> doctorsRecMoreUnLogin(String homeArea,
                                                               String profession) {
        DoctorDAO dao = DAOFactory.getDAO(DoctorDAO.class);
        return dao.doctorsRecMore(homeArea, profession);
    }

    /**
     * 首页医生推荐-更多
     * 为了使前端改小代码编写，同时不影响qpp前端使用，将服务名修改掉
     *
     * @param homeArea
     * @param profession
     * @return List<HashMap<String,Object>>
     * @author luf 2016-1-25
     */
    @RpcService
    public List<HashMap<String, Object>> doctorsRecMore(String homeArea,
                                                        String profession) {
        DoctorDAO dao = DAOFactory.getDAO(DoctorDAO.class);
        return dao.doctorsRecMore(homeArea, profession);
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
     */
    @RpcService
    public List<HashMap<String, Object>> searchDoctorForHealth(
            String profession, String addrArea, String domain, String name,
            Integer haveAppoint, String proTitle, int flag, int start, int limit) {
        DoctorDAO dao = DAOFactory.getDAO(DoctorDAO.class);
        return dao.searchDoctorForHealth(profession, addrArea, domain, name,
                haveAppoint, proTitle, flag, start, limit);
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
        DoctorDAO dao = DAOFactory.getDAO(DoctorDAO.class);
        return dao.appointDoctorsRecommended(doctorId);
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
        DoctorDAO dao = DAOFactory.getDAO(DoctorDAO.class);
        return dao.consultDoctorsRecommended(doctorId);
    }

    /**
     * 服务名：获取医院挂号科室列表服务
     *
     * @param organID
     * @param professionCode
     * @return
     * @throws DAOException
     * @author yxq
     */
    @RpcService
    public List<AppointDepart> findByOrganIDAndProfessionCode(
            int organID, String professionCode) {
        AppointDepartDAO dao = DAOFactory.getDAO(AppointDepartDAO.class);
        return dao.findByOrganIDAndProfessionCode(organID, professionCode);
    }

    /**
     * 根据名称查询所有有效医院
     *
     * @param name 医院名称
     * @return List<Organ>
     * @author luf
     */
    @RpcService
    public List<Organ> findHospitalByNameLike(final String name) {
        OrganDAO dao = DAOFactory.getDAO(OrganDAO.class);
        return dao.findHospitalByNameLike(name);
    }

    /**
     * 根据机构和科室查询医生列表
     *
     * @param department 科室
     * @param organId    机构内码
     * @return List<HashMap<String, Object>>
     * @author luf
     */
    @RpcService
    public List<HashMap<String, Object>> queryDoctorListForHealth(
            int department, int organId, int flag) {
        QueryDoctorListDAO dao = DAOFactory.getDAO(QueryDoctorListDAO.class);
        return dao.queryDoctorListForHealth(department, organId, flag);
    }

    /**
     * 咨询/预约推荐医生-未登陆
     * <p>
     * eh.bus.service
     *
     * @param doctorId 医生内码
     * @param flag     标志-0咨询1预约
     * @return List<HashMap<String,Object>>
     * @author luf 2016-1-26
     */
    @RpcService
    public List<HashMap<String, Object>> consultOrAppointDoctorsRecommendedUnlogin(
            int doctorId, int flag) {
        DoctorDAO docDao = DAOFactory.getDAO(DoctorDAO.class);
        return docDao.consultOrAppointDoctorsRecommended(doctorId, flag);
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
        DoctorDAO docDao = DAOFactory.getDAO(DoctorDAO.class);
        return docDao.getDoctorInfoUnloginForHealth(doctorId);
    }

    /**
     * @param parentKey
     * @param flag      标志--0包含学科团队，1不包含学科团队
     * @return List<DictionaryItem>
     * @throws
     * @Class eh.bus.service.UnLoginSevice.java
     * @Title: getAllProfession
     * @Description: TODO 获取所有专科代码字典服务
     * @author luf
     * @Date 2016-5-23
     */
    @RpcService
    public List<DictionaryItem> getAllProfessionTeam(String parentKey, int flag) {
        DictionaryLocalService ser = AppContextHolder.getBean("dictionaryService", DictionaryLocalService.class);
        List<DictionaryItem> list = new ArrayList<DictionaryItem>();
        try {
            DictionarySliceRecordSet var = ser.getSlice(
                    "eh.base.dictionary.Profession", parentKey, 0, "", 0, 0);
            list = var.getItems();

        } catch (ControllerException e) {
            log.error("getAllProfessionTeam--->" + e);
        }
        if (flag == 0) {
            return list;
        }
        List<DictionaryItem> results = new ArrayList<DictionaryItem>();
        for (DictionaryItem di : list) {
            if (di.getKey().compareTo("00") >= 0 && di.getKey().compareTo("01") < 0) {
                continue;
            }
            results.add(di);
        }
        return results;
    }

    /**
     * 获取url
     *
     * @return
     */
    @RpcService
    public HashMap<String, String> getUrls() {
        return new UrlResourceService().getUrls();
    }

    /**
     * 供前端调取医院列表(健康端个性化)(未登录可用)
     *
     * @param addr
     * @return
     * @author LF
     */
    @RpcService
    public List<Organ> findByAddrAreaLikeForHealth(String addr) {
        OrganDAO organDao = DAOFactory.getDAO(OrganDAO.class);
        return organDao.findByAddrAreaLikeForHealth(addr);
    }

    /**
     * 根据名称查找该地区所有有效医院(未登录可用)
     *
     * @param name 医院名称
     * @param addr 地址名称
     * @return List<Organ>
     * @author zhangz
     */
    @RpcService
    public List<Organ> findHospitalByNameAndAddrLike(String name, String addr) {
        OrganDAO organDao = DAOFactory.getDAO(OrganDAO.class);
        return organDao.findHospitalByNameAndAddrLike(name, addr);
    }

    /**
     * 根据名称查询所有有效医院(健康端个性化,未登录可用)
     *
     * @param name 医院名称
     * @return List<Organ>
     * @author luf
     */
    @RpcService
    public List<Organ> findHospitalByNameLikeForHealth(String name) {
        OrganDAO organDao = DAOFactory.getDAO(OrganDAO.class);
        return organDao.findHospitalByNameLikeForHealth(name);
    }

    /**
     * 分页获取团队成员-未登录情况下
     *
     * @param doctorId
     * @param start    开始页
     * @param limit    每页几条
     * @return 队长显示在第一个位置，其他成员根据职称由高到低排序，同职称的情况下，根据加入团队时间由远及近排序
     */
    @RpcService
    public List<Doctor> getTeamMembersForHealthPages(Integer doctorId, Integer start, Integer limit) {
        DoctorGroupService service = AppContextHolder.getBean("eh.doctorGroupService", DoctorGroupService.class);
        return service.getTeamMembersForHealthPages(doctorId, start, limit);
    }

    /**
     * 推荐医生-新（首页）
     * 将未登录和登录情况两种情况进行分装
     * eh.base.dao
     *
     * @param homeArea      属地区域
     * @param age           患者年龄
     * @param patientGender 患者性别 --1男2女
     *                      02全科医学        12口腔科 10眼科 0502产科 03内科 04外科 07儿科 19肿瘤科 0501妇科
     * @return Map<String, List<Doctor>>
     * @throws ControllerException
     * @author luf 2016-2-25
     */
    @RpcService
    public Map<String, Object> doctorsRecommendedNew(String homeArea,
                                                     int age, String patientGender) throws ControllerException {
        UserRoleToken urt = UserRoleToken.getCurrent();
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);

        Map<String, Object> map = new HashMap<String, Object>();
        if (urt == null) {
            //未登录
            map = doctorDAO.doctorsRecommendedUnLogin2(homeArea);
        } else {
            //登录
            map = doctorDAO.doctorsRecommendedNew(homeArea, age, patientGender);
        }
        return map;
    }

    /**
     * 患者端查看医生信息
     *
     * @param docId 被查看的医生id
     * @param mpi   当前登陆患者的mpi
     * @return
     * @author zhangx
     * @date 2015-12-22 下午5:19:09
     * @date 2016-3-3 luf 修改异常code
     * 016-11-16 zhangx：紧急需求，未登录情况下扫码关注的医生需要展示在首页，点这些医生跳转到医生主页，
     * 关注按钮只显示，不可操作,其他未登录情况下,可操作,登录情况下，可操作
     */
    @RpcService
    public HashMap<String, Object> getDoctorInfoForHealth(Integer docId,
                                                          String mpi) {
        UserRoleToken urt = UserRoleToken.getCurrent();
        DoctorInfoService service = AppContextHolder.getBean("eh.doctorInfoService", DoctorInfoService.class);

        String openId = "";
        String appId = null;
        SimpleWxAccount simpleWxAccount = CurrentUserInfo.getSimpleWxAccount();
        if (null != simpleWxAccount) {
            openId = simpleWxAccount.getOpenId();
            appId = simpleWxAccount.getAppId();
        }

        //点击医生头像,调用实时号源数据   author:hwg 2017-02-27
        //删除同步号源方法，提取到 DoctorInfoService中的updateDocSourceFromHis,不在getDoctorInfoForHealth中访问HIS
        HashMap<String, Object> map = new HashMap<String, Object>();
        if (urt == null) {
            //未登录
            map = service.getDoctorInfoForHealthUnLogin(docId);
            WxSubscribeDAO subDao = DAOFactory.getDAO(WxSubscribeDAO.class);

            WxSubscribe wxsub = subDao.getSubscribeByOpenIdAndDoctor(openId, docId);
            if (wxsub != null) {
                Doctor doc = (Doctor) map.get("doctor");
                doc.setIsRelation(true);//关注标记显示为已关注
                map.put("doctor", doc);
                map.put("operation", false);//设置标记为不可操作
            }
        } else {
            //登录
            map = service.getDoctorInfoForHealth(docId, mpi);
        }

        //获取评价和心意相关内容
        map = getMindGiftInfoAndEvaluationInfo(docId, map);

        try {
            QRInfoService qrservice = AppContextHolder.getBean("eh.qrInfoService", QRInfoService.class);
            qrservice.createDocQRInfo(docId, appId);
        } catch (Exception e) {
            log.error("获取生成二维码[" + docId + "," + appId + "]" + e.getMessage());
        }

        //2017-7-13 15:34:51 zhangx 需要开展三伏天活动，在出参接口里面新增三伏天活动医生相关标记
        DogDaysService dogDaysService = AppContextHolder.getBean("eh.dogDaysService", DogDaysService.class);
        map.put("dogDaysFlag",dogDaysService.getDogDaysFlag(docId));

        return map;
    }

    @RpcService
    public HashMap<String, Object> getDoctorInfoForHealthNew(Integer docId,
                                                             String mpi, Integer departId, Integer organ) {
        UserRoleToken urt = UserRoleToken.getCurrent();
        DoctorInfoService service = AppContextHolder.getBean("eh.doctorInfoService", DoctorInfoService.class);

        String openId = "";
        String appId = null;
        SimpleWxAccount simpleWxAccount = CurrentUserInfo.getSimpleWxAccount();
        if (null != simpleWxAccount) {
            openId = simpleWxAccount.getOpenId();
            appId = simpleWxAccount.getAppId();
        }

        //点击医生头像,通知前置机调用医生实时数据
        EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
        List<Employment> list = employmentDAO.findByDoctorId(docId);
        for (int i = 0; list.size() < i; i++) {
            Employment employment = list.get(i);
            Integer organId = employment.getOrganId();
            String jobNumber = employment.getJobNumber();
            HisDoctorParam doctorParam = new HisDoctorParam();
            doctorParam.setJobNum(jobNumber);
            doctorParam.setDoctorId(docId);
            doctorParam.setOrganID(employment.getOrganId());
            doctorParam.setOrganizeCode(DAOFactory.getDAO(OrganDAO.class).getByOrganId(employment.getOrganId()).getOrganizeCode());
            HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
            boolean f = hisServiceConfigDao.isServiceEnable(organId, ServiceType.SOURCEREAL);
            if (f) {
                new RpcAsynchronousUtil(doctorParam, organId).obtainNowSource();
            }
        }

        HashMap<String, Object> map = new HashMap<String, Object>();
        if (urt == null) {
            //未登录
            map = service.getDoctorInfoForHealthUnLoginNew(docId, organ, departId);
            WxSubscribeDAO subDao = DAOFactory.getDAO(WxSubscribeDAO.class);

            WxSubscribe wxsub = subDao.getSubscribeByOpenIdAndDoctor(openId, docId);
            if (wxsub != null) {
                Doctor doc = (Doctor) map.get("doctor");
                doc.setIsRelation(true);//关注标记显示为已关注
                map.put("doctor", doc);
                map.put("operation", false);//设置标记为不可操作
            }
        } else {
            //登录
            map = service.getDoctorInfoForHealthNew(docId, mpi, organ, departId);
        }

        //获取心意相关内容
        map = this.getMindGiftInfoAndEvaluationInfo(docId, map);

        try {
            QRInfoService qrservice = AppContextHolder.getBean("eh.qrInfoService", QRInfoService.class);
            qrservice.createDocQRInfo(docId, appId);
        } catch (Exception e) {
            log.error("获取生成二维码[" + docId + "," + appId + "]" + e.getMessage());
        }

        return map;
    }


    /**
     * 医生主页获取心意和评价的信息
     *
     * @param docId 医生的主键
     * @param map   医生主页需要的信息
     * @return
     * @author cuill
     * @date 2017/6/7
     */
    private HashMap<String, Object> getMindGiftInfoAndEvaluationInfo(Integer docId, HashMap<String, Object> map) {
        //获取心意相关内容
        HashMap<String, Object> minMap = new HashMap<>();
        try {
            MindGiftService mindGiftService = AppContextHolder.getBean("eh.mindGiftService", MindGiftService.class);
            MindGiftDAO mindGiftDAO = DAOFactory.getDAO(MindGiftDAO.class);
            //心意总数
            minMap.put("mindGiftNum", mindGiftDAO.getEffectiveMindGiftsNum(docId));
            //心意列表
            List<MindGift> listMind = new ArrayList<>();
            minMap.put("mindGiftList", mindGiftService.findMindsByDoctorId(docId, 0, 5, true));

        } catch (Exception e) {
            minMap.put("mindGiftNum", 0);
            minMap.put("mindGiftList", new ArrayList<Map<String, Object>>());
            log.error("获取心意内容失败[" + docId + "]" + e.getMessage());
        }
        map.put("mindGiftInfo", minMap);

        //获取评价相关内容
        HashMap<String, Object> evaInfoMap = new HashMap<String, Object>();
        try {
            MindGiftService mindGiftService = AppContextHolder.getBean("eh.mindGiftService", MindGiftService.class);
            evaInfoMap.putAll(findEvaInfoByDoctorIdForHealth(docId));
        } catch (Exception e) {
            evaInfoMap.put("evaNum", 0);
            evaInfoMap.put("evaList", new ArrayList<Map<String, Object>>());
            log.error("获取评价内容失败[" + docId + "]" + e.getMessage());
        }
        map.put("evaInfo", evaInfoMap);
        return map;
    }

    /**
     * 供前端调取医院列表
     *
     * @param addr
     * @param flag 向上查询标志-0往上查1查本区域
     * @param type 0-按照原来模式不变 1-过滤未出报告的机构 2-过滤出未能支付的机构
     * @return
     * @author LF
     */
    @RpcService
    public List<Organ> findByAddrAreaLikeUp(String addr, int flag, int type) {
        QueryOrganService service = AppContextHolder.getBean("eh.queryOrganService", QueryOrganService.class);
        return service.findByAddrAreaLikeUp(addr, flag, type);
    }

    /**
     * 微信个性化显示当前管理单元的机构列表
     * zhongzx
     *
     * @param flag 0-按照原来模式不变 1-取单搜索 2-支付搜素
     * @return
     */
    @RpcService
    public List<Organ> findByFlagForHealth(Integer flag) {
        OrganDAO organDao = DAOFactory.getDAO(OrganDAO.class);
        return organDao.findByFlagForHealth(flag);
    }

    /**
     * zhongzx
     * 根据名字进行查询有效医院列表
     *
     * @param name
     * @param flag 0-原来的模式 1-取单 2-支付
     * @return
     */
    @RpcService
    public List<Organ> findByFlagAndNameLike(Integer flag, String name, String addr) {
        OrganDAO organDao = DAOFactory.getDAO(OrganDAO.class);
        return organDao.findByFlagAndNameLike(flag, name, addr);
    }

    /**
     * zhongzx
     * 根据名字搜索机构管理单元下的机构 （微信个性化）
     *
     * @param flag 0-按照原来模式不变 1-取单搜索 2-支付搜索
     * @param name
     * @return
     */
    @RpcService
    public List<Organ> findByNameLikeForHealth(Integer flag, String name) {
        OrganDAO organDao = DAOFactory.getDAO(OrganDAO.class);
        return organDao.findByNameLikeForHealth(flag, name);
    }

    /**
     * zsl
     * 根据地区查询医院列表并按医院等级分组（微信健康端）
     *
     * @param addr 区域代码
     * @return
     */
    @RpcService
    public List<Map<String, Object>> findByAddrAreaLikeGroupByGrade(String addr) throws ControllerException {
        QueryOrganService service = AppContextHolder.getBean("eh.queryOrganService", QueryOrganService.class);
        return service.findByAddrAreaLikeGroupByGrade(addr);
    }

    /**
     * 根据地区查询医院列表并按医院等级分组（微信健康端）
     * 处方逻辑 不同加上扩展参数
     * mark == 2 为在线续方 其他入口为空
     * drugId 在线续方入口时 可能会有 药品信息
     *
     * @param addr
     * @param queryParam
     * @return
     * @throws Exception
     * @author zhongzx
     */
    @RpcService
    public List<Map<String, Object>> findByAddrAreaLikeGroupByGradeExt(String addr, Map<String, Object> queryParam) throws Exception {
        QueryOrganService service = AppContextHolder.getBean("eh.queryOrganService", QueryOrganService.class);
        return service.findByAddrAreaLikeGroupByGradeExt(addr, queryParam);
    }

    /**
     * 健康端咨询入口搜索医生
     *
     * @param search     搜索条件
     * @param addrArea   属地区域
     * @param organId    机构内码
     * @param profession 专科编码
     * @param proTitle   职称
     * @param mpiId      患者主索引
     * @param start      起始页
     * @param limit      每页限制条数
     * @param flag       标志-0有咨询1没咨询
     * @param mark       入口-0咨询1预约
     * @return
     * @author luf 2016-10-6 咨询入口找医生模式修改
     */
    @RpcService
    public HashMap<String, Object> searchDoctorInConsult(
            String search, String addrArea, Integer organId, String profession, String proTitle, String mpiId, int start,
            int limit, int flag, int mark) {
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        return doctorDAO.searchDoctorInConsult(search, addrArea, organId, profession, proTitle, mpiId, start, limit, flag, mark);
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
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        return doctorDAO.searchDoctorConsultOrCanRecipe(search, addrArea, organId, profession, proTitle, mpiId, start, limit, flag, mark);
    }

    /**
     * @param search
     * @param addrArea
     * @param organId
     * @param profession
     * @param proTitle
     * @param mpiId
     * @param start
     * @param limit
     * @param flag
     * @param mark       调用入口标志-0咨询，1预约，2在线续方
     * @param queryParam 额外参数 drugId 平台药品编号
     * @return
     * @author zhongzx
     */
    @RpcService
    public Map<String, Object> searchDoctorConsultOrCanRecipeExt(String search, String addrArea, Integer organId, String profession, String proTitle, String mpiId, int start,
                                                                 int limit, int flag, int mark, Map<String, Object> queryParam) {
        QueryDoctorListService queryDoctorListService = AppContextHolder.getBean("queryDoctorListService", QueryDoctorListService.class);
        return queryDoctorListService.searchDoctorConsultOrCanRecipeExt(search, addrArea, organId, profession, proTitle, mpiId, start, limit, flag, mark, queryParam);
    }

    /**
     * 按医生姓名查找
     */
    @RpcService
    public Map<String, Object> searchDoctorConsultOrCanRecipeByDoctorName(String search, String addrArea, Integer organId, String profession, String proTitle, String mpiId, int start,
                                                                          int limit, int flag, int mark, Map<String, Object> queryParam, String doctorName) {
        QueryDoctorListService queryDoctorListService = AppContextHolder.getBean("queryDoctorListService", QueryDoctorListService.class);
        return queryDoctorListService.searchDoctorConsultOrCanRecipeByDoctorName(search, addrArea, organId, profession, proTitle, mpiId, start, limit, flag, mark, queryParam, doctorName);
    }

    /**
     * 用药指导入口
     */
    @RpcService
    public Map<String, Object> searchDoctorListForConduct(String addrArea, Integer organId, String proTitle, String mpiId, int start, int limit) {
        QueryDoctorListService queryDoctorListService = AppContextHolder.getBean("queryDoctorListService", QueryDoctorListService.class);
        return queryDoctorListService.searchDoctorListForConduct(addrArea, organId, proTitle, mpiId, start, limit);
    }

    /**
     * 按一级专科查询二级专科目录(剔除无医生)
     *
     * @param addr           地区
     * @param organIdStr     机构代码
     * @param professionCode 大专科代码（查询小专科时传入）
     * @return
     * @author zsl
     */
    @RpcService
    public List<Object> findValidDepartmentByProfessionCode(final String addr, final String organIdStr, final String professionCode) {
        DepartmentDAO dao = DAOFactory.getDAO(DepartmentDAO.class);
        return dao.findValidDepartmentByProfessionCode(addr, organIdStr, professionCode);
    }

    /**
     * 按一级专科查询二级专科目录
     *
     * @param addr
     * @param organIdStr
     * @param professionCode
     * @param queryParam     扩展参数 mark == 2 在线续方 搜索逻辑不同
     *                       drugId 可能携带药品信息
     * @return
     * @author zhongzx
     */
    @RpcService
    public List<Object> findValidDepartmentByProfessionCodeExt(String addr, String organIdStr, String professionCode,
                                                               Map<String, Object> queryParam) {
        DepartmentDAO dao = DAOFactory.getDAO(DepartmentDAO.class);
        return dao.findValidDepartmentByProfessionCodeExt(addr, organIdStr, professionCode, queryParam);
    }

    /**
     * 前八门热门专科类别查询服务
     * <p>
     * 个性化orderType (0:按医生数量，1按咨询量)
     *
     * @return
     * @author zsl
     */
    @RpcService
    public List<Object> findHotProfession() {
        DepartmentDAO dao = DAOFactory.getDAO(DepartmentDAO.class);
        return dao.findHotProfession();
    }

    /**
     * 微信端个性化 2加载页 和 3 banner
     * 当前管理单元，包括当前管理单元以下的，搜索按%
     * 只限当前管理单元 搜索按=
     *
     * @param bannerType 2加载页 3banner
     * @return
     */
    @RpcService
    public List<Banner> findBannerForWXHealth(final String bannerType) {
        Client client = CurrentUserInfo.getCurrentClient();
        SimpleWxAccount wxAccount = CurrentUserInfo.getSimpleWxAccount();
        if (client == null || wxAccount == null) {
            log.error("findBannerForWXHealth current client or account null!");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "currentClient not exists!");
        }
        List<Banner> wxBannerList = Lists.newArrayList();
        try {
            ClientPlatformEnum clientPlatformEnum = ClientPlatformEnum.fromKey(client.getOs());
            BannerService bannerService = AppContextHolder.getBean("bannerService", BannerService.class);
            int status = 1;
            if (ClientPlatformEnum.WEIXIN.equals(clientPlatformEnum)) {
                String appId = wxAccount.getAppId();
                WXConfigsDAO wxConfigsDAO = DAOFactory.getDAO(WXConfigsDAO.class);
                WXConfig wxConfig = wxConfigsDAO.getByAppID(appId);
                BannerDAO bannerDAO = DAOFactory.getDAO(BannerDAO.class);
                if (StringUtils.equals("2", bannerType)) {//加载页 返回一个优先级最高的且status=1在用
                    List<Banner> wxBannerListOld = bannerDAO.findWxBannerByOrganIdAndTypeAndSta(wxConfig.getId(), bannerType);
                    if (wxBannerListOld.size() > 0) {
                        wxBannerList.add(wxBannerListOld.get(0));
                    }
                } else {//wxBanner 返回一个banner 并且是status=1在用
                    if (ObjectUtils.isEmpty(wxConfig)) {
                        wxBannerList = bannerDAO.findWxBannerByOrganIdAndTypeAndSta(null, bannerType);
                    } else {
                        wxBannerList = bannerDAO.findWxBannerByOrganIdAndTypeAndSta(wxConfig.getId(), bannerType);
                    }
                }
            } else if (ClientPlatformEnum.ALILIFE.equals(clientPlatformEnum)) {
                wxBannerList = bannerService.findBannerByPlatform(wxAccount.getAppId(), bannerType, status);
            } else if (ClientPlatformEnum.WEB.equals(clientPlatformEnum)) {
                wxBannerList = bannerService.findBannerByPlatform(wxAccount.getAppId(), bannerType, status);
            } else if (ClientPlatformEnum.WX_WEB.equals(clientPlatformEnum)) {
                SimpleThird third = (SimpleThird) wxAccount;
                wxBannerList = bannerService.findBannerByPlatform(third.getAppkey(), bannerType, status);
            } else {
                log.warn("findBannerForWXHealth not support client plat, clientPlat[{}]", clientPlatformEnum);
            }
        } catch (Exception e) {
            log.error("findBannerForWXHealth error, errorMessage[{}], strackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            throw new DAOException(ErrorCode.SERVICE_ERROR, e.getMessage());
        }
        return wxBannerList;
    }

    /**
     * 获取医院信息
     *
     * @param organId
     * @return
     */
    @RpcService
    public Organ getOrganByOrganId(Integer organId) {
        OrganDAO dao = DAOFactory.getDAO(OrganDAO.class);
        return dao.getByOrganId(organId);
    }

    /**
     * 获取微信公众号上扫码关注且未真正关注的数据
     *
     * @param openId
     * @return
     */
    @RpcService
    public List<HashMap<String, Object>> findSubscribeDoctors() {
        SubscribeService subscribeService = AppContextHolder.getBean("eh.subscribeService", SubscribeService.class);

        return subscribeService.findSubscribeDoctors();
    }

    /**
     * 获取九宫格模板
     *
     * @param appId  当前APPID
     * @param tempId 模板号
     * @return
     */
    @RpcService
    public List<Scratchable> findModleByAppID() {
        ScratchableService service = AppContextHolder.getBean("eh.scratchableService", ScratchableService.class);
        return service.findModleByAppID();
    }

    /**
     * 获取客服电话
     *
     * @return
     */
    @RpcService
    public String getCustomerTel() {
        return ParamUtils.getParam(ParameterConstant.KEY_CUSTOMER_TEL, SystemConstant.CUSTOMER_TEL);
    }

    /**
     * 获取相关常量接口
     *
     * @return
     */
    @RpcService
    public HashMap<String, Object> getParams() {
        HashMap<String, Object> map = new HashMap<String, Object>();

        UrlResourceService urlService = AppContextHolder.getBean("eh.urlResourceService", UrlResourceService.class);
        HashMap<String, String> urls = urlService.getUrls();
        map.putAll(urls);

        //返回给前端常州评估地址
        map.put("assessUrl", ParamUtils.getParam("ASSESS_BEGIN_URL"));
        map.put("vsUrl", ParamUtils.getParam("VS_URL"));

        map.put("customerTel", ParamUtils.getParam(ParameterConstant.KEY_CUSTOMER_TEL, SystemConstant.CUSTOMER_TEL));
        map.put("doctorEasemobPrefix", Easemob.getDoctorPrefix());
        map.put("patientEasemobPrefix", Easemob.getPatientPrefix());
        return map;
    }

    /**
     * 获取服务设置相关接口
     * 考虑到微信端-支付宝端-web端，医生端，
     * 健康APP端的功能需求不同，分为两个服务分别获取
     *
     * @return
     * @2017/6/15 @author cuill 微信端评价是否显示
     */
    @RpcService
    public HashMap<String, Object> getWXAndWebParam() {
        HashMap<String, Object> map = new HashMap<String, Object>();
        ScratchableService scratchableService = AppContextHolder.getBean("eh.scratchableService", ScratchableService.class);
        ServerDateService serverDateService = AppContextHolder.getBean("eh.serverDateService", ServerDateService.class);
        EvaluationService evaluationService = AppContextHolder.getBean("eh.evaluationService", EvaluationService.class);
        FamilyMemberService familyMemberService = AppContextHolder.getBean("eh.familyMemberService", FamilyMemberService.class);

        try {
            //首页配置
            map.put("homePageModle", scratchableService.findModleByAppID());
        } catch (Exception e) {
            log.error("获取首页配置异常:" + e.getMessage());
            map.put("homePageModle", new ArrayList<Scratchable>());
        }

        try {
            //个人医生主页配置
            map.put("docIndexModle", scratchableService.findDocIndexModle());
        } catch (Exception e) {
            log.error("获取个人医生主页配置异常:" + e.getMessage() + "," + JSONUtils.toString(e.getStackTrace()));
            map.put("docIndexModle", new ArrayList<Scratchable>());
        }
        try {
            //团队医生主页配置
            map.put("teamIndexModle", scratchableService.findTeamDocIndexModle());
        } catch (Exception e) {
            log.error("获取团队医生主页配置异常:" + e.getMessage() + "," + JSONUtils.toString(e.getStackTrace()));
            map.put("teamIndexModle", new ArrayList<Scratchable>());
        }


        //客服电话
        map.put("customerTel", ParamUtils.getParam(ParameterConstant.KEY_CUSTOMER_TEL, SystemConstant.CUSTOMER_TEL));

        //服务器时间
        map.put("serverDate", serverDateService.getServerDate());

        //环信前缀
        map.put("doctorEasemobPrefix", Easemob.getDoctorPrefix());
        map.put("patientEasemobPrefix", Easemob.getPatientPrefix());

        //判断微信端是否需要评价
        map.put("isNeedEvaluation", evaluationService.canShowEvaluationForWx());

        //能否在就诊人列表中删除自己
        map.put("canDelSelf", familyMemberService.canDelSelf());
        return map;
    }

    /**
     * 根据医生内码和用户获取有效评价并装配-分页
     *
     * @param doctorId    医生内码
     * @param serviceType 业务类型 1转诊 2会诊 3咨询 4预约 5检查 6处方
     * @param start       开始位置
     * @param limit       每页条数
     * @return
     * @author zhangsl
     * @Date 2016-12-01 10:41:32
     */
    @RpcService
    public List<PatientFeedback> findValidEvaByDoctorId(Integer doctorId, String serviceType, int start, int limit) throws ControllerException {
        EvaluationService service = AppContextHolder.getBean("eh.evaluationService", EvaluationService.class);
        return service.findValidEvaByDoctorId(doctorId, serviceType, 0, "patient", start, limit);
    }

    /**
     * 获取点评详细，并将医生未读评价标为已读
     *
     * @param feedbackId 点评单id
     * @return 点评详细
     * @author zhangsl
     * @Date 2016-12-01 10:41:32
     */
    @RpcService
    public PatientFeedback getEvaluationById(int feedbackId) {
        EvaluationService service = AppContextHolder.getBean("eh.evaluationService", EvaluationService.class);
        return service.getEvaluationById(feedbackId, 0, "patient");
    }

    /**
     * 根据医生内码和用户获取有效评价总数和医生总评分
     *
     * @param doctorId 医生内码
     * @author zhangsl
     * @Date 2016-12-01 10:41:32
     */
    @RpcService
    public Map<String, Object> getEvaNumAndDocRatingByDoctorIdAndUserId(Integer doctorId) {
        EvaluationService service = AppContextHolder.getBean("eh.evaluationService", EvaluationService.class);
        return service.getEvaNumAndDocRatingByDoctorIdAndUserId(doctorId, 0, "patient");
    }

    /**
     * 根据医生内码获取患者端医生主页评价信息
     *
     * @param doctorId 医生内码
     * @return
     * @author zhangsl
     * @Date 2016-12-01 10:41:32
     */
    @RpcService
    public Map<String, Object> findEvaInfoByDoctorIdForHealth(Integer doctorId) {
        EvaluationService service = AppContextHolder.getBean("eh.evaluationService", EvaluationService.class);
        return service.findEvaInfoByDoctorIdForHealth(doctorId);
    }

    /**
     * 预约挂号首页医院列表----分页
     *
     * @param searchType 查询类型：0非个性化（剔除无医生并向上查找） 1非个性化（剔除无医生） 2按地区查询个性化 3查询个性化
     * @param addr
     * @param start
     * @param limit
     * @author zhangsl
     * @Date 2016-12-19 15:38:21
     */
    @RpcService
    public List<Organ> findByAddrAreaLikeInPage(Integer searchType, String addr, int start, int limit) {
        QueryOrganService service = AppContextHolder.getBean("eh.queryOrganService", QueryOrganService.class);
        return service.findByAddrAreaLikeInPage(searchType, addr, start, limit);
    }

    /**
     * 登陆后的推荐医生-wx2.7（首页轮播）
     *
     * @param homeArea      属地区域
     * @param age           患者年龄
     * @param patientGender 患者性别 --1男2女
     * @author zhangsl 2016-12-19 16:51:12
     */
    @RpcService
    public Map<String, Object> doctorsRecommendedForScroll(String homeArea, Integer age, String patientGender)
            throws ControllerException {
        DoctorService service = AppContextHolder.getBean("eh.doctorService", DoctorService.class);
        return service.doctorsRecommendedForScroll(homeArea, age, patientGender);
    }

    /**
     * 预约/挂号医生列表服务
     *
     * @param department
     * @param organId
     * @param date
     * @return List<Doctor>
     */
    @RpcService
    public List<HashMap<String, Object>> effectiveSourceDoctorsForhealth(final int department, final int organId, final Date date) {
        QueryDoctorListDAO dao = DAOFactory.getDAO(QueryDoctorListDAO.class);
        return dao.effectiveSourceDoctorsForhealth(department, organId, date);
    }

    /**
     * 预约/挂号医生列表日期栏服务
     *
     * @param department
     * @param organId
     * @return List<HashMap<String, Object>>
     */
    @RpcService
    public List<HashMap<String, Object>> effectiveWorkDatesForHealth(final int department, final int organId) {
        QueryDoctorListDAO dao = DAOFactory.getDAO(QueryDoctorListDAO.class);
        return dao.effectiveWorkDatesForHealth(department, organId);
    }

    @RpcService
    public Map<String, Object> getPCPatchFileId(String version) {
        PatchService service = AppContextHolder.getBean("eh.patchService", PatchService.class);
        return service.getPatchFile(pcPatchName, version);
    }

    /**
     * 根据bae_doctor和bus_consultSet这两张表来判断大科室里面开通专家解读的医生数量.
     * 返回的结果为按照科室医生的数量从多到少排序,如果全科科室有医生的话,全科科室排在第一位。
     *
     * @return List<HashMap<String, Object>>
     * @author cuill 2017年2月16日
     */
    @RpcService
    public List<HashMap<String, Object>> findProfessionList() {
        QueryDoctorListService queryDoctorListService = AppContextHolder.getBean("eh.queryDoctorListService", QueryDoctorListService.class);
        return queryDoctorListService.findProfessionList();
    }

    /**
     * 获取专家解读的专家列表数
     *
     * @param profession 专科科编号
     * @param start      起始页
     * @param limit      每页限制条数
     * @return List<Map<String, Object>>
     * @author cuill 2017-02-16
     */
    @RpcService
    public List<Map<String, Object>> queryDoctorListForProfessorConsult(String profession, int start,
                                                                        int limit) {
        QueryDoctorListService queryDoctorListService = AppContextHolder.getBean("eh.queryDoctorListService", QueryDoctorListService.class);
        return queryDoctorListService.queryDoctorListForProfessorConsult(profession, start, limit);
    }

    /**
     * 患者端 查询某个二级药品目录下的药品列表
     *
     * @param drugClass 药品二级目录
     * @param start
     * @param limit
     * @return
     * @author zhongzx
     */
    @RpcService
    public List<DrugList> queryDrugsInDrugClass(String drugClass, int start, int limit) {
        DrugListService service = AppContextHolder.getBean("drugListService", DrugListService.class);
        return service.queryDrugsInDrugClass(drugClass, start, limit);
    }

    /**
     * 患者端 推荐药品列表
     *
     * @param start
     * @param limit
     * @return
     * @author zhongzx
     */
    @RpcService
    public List<DrugList> recommendDrugList(int start, int limit) {
        DrugListService service = AppContextHolder.getBean("drugListService", DrugListService.class);
        return service.recommendDrugList(start, limit);
    }

    /**
     * 患者端查询对症药品
     *
     * @param pDrug
     * @param start
     * @param limit
     * @return
     */
    @RpcService
    public List<DrugList> findPathologicalDrugList(PathologicalDrug pDrug, int start, int limit) {
        PathologicalDrugService service = AppContextHolder.getBean("eh.pathologicalDrugService", PathologicalDrugService.class);
        return service.findDrugList(pDrug, start, limit);
    }

    /**
     * 获取最新开具的处方单前limit条，用于跑马灯显示
     *
     * @param limit
     * @return
     */
    @RpcService
    public List<RecipeRollingInfo> findLastesRecipeList(int limit) {
        RecipeListService recipeListService = AppContextHolder.getBean("eh.recipeListService", RecipeListService.class);
        return recipeListService.findLastesRecipeList(limit);
    }

    /**
     * 处方患者端主页展示推荐医生 (样本采集数量在3个月内)
     *
     * @param start
     * @param limit
     * @return
     */
    @RpcService
    public List<Doctor> findDoctorByCount(int start, int limit) {
        RecipeListService recipeListService = AppContextHolder.getBean("eh.recipeListService", RecipeListService.class);
        return recipeListService.findDoctorByCount(start, limit);
    }

    /**
     * 患者端 获取对应机构的西药 或者 中药的药品有效全目录（现在目录有二级）
     *
     * @return
     * @author zhongzx
     */
    @RpcService
    public List<Map<String, Object>> queryDrugCatalog() {
        DrugListService service = AppContextHolder.getBean("drugListService", DrugListService.class);
        return service.queryDrugCatalog();
    }

    /**
     * 患者端 药品搜索服务 药品名 商品名 拼音 别名
     *
     * @param drugName 搜索的文字或者拼音
     * @param start
     * @param limit
     * @return
     * @author zhongzx
     */
    @RpcService
    public List<DrugList> searchDrugByNameOrPyCode(String drugName, String mpiId, int start, int limit) {
        DrugListService service = AppContextHolder.getBean("drugListService", DrugListService.class);
        return service.searchDrugByNameOrPyCode(drugName, mpiId, start, limit);
    }

    /**
     * 供CMS上调用删除资讯的时候把医生所收藏的该资讯的记录删除
     *
     * @param md5
     * @param ids
     * @return
     */
    @RpcService
    public boolean deleteCollection(String md5, List<Integer> ids) {
        String MD5 = Md5Crypt.apr1Crypt("sdfsdf65fs5daf2sd5f", "qfdsgdfg");
        InfoCollectionDAO dao = DAOFactory.getDAO(InfoCollectionDAO.class);
        if (!StringUtils.equals(MD5, md5)) {
            return false;
        }
        for (Integer id : ids) {
            dao.deleteByInformationId(id);
        }
        return true;
    }

    /**
     * 评价页面+新增心意相关内容
     *
     * @param feedbackId
     * @param preUserId
     * @param preUserType
     * @return
     */
    @RpcService
    public Map<String, Object> getEvaluationWithGiftById(int feedbackId, Integer preUserId, String preUserType) {

        EvaluationService service = AppContextHolder.getBean("eh.evaluationService", EvaluationService.class);
        return service.getEvaluationWithGiftById(feedbackId, preUserId, preUserType);
    }

    /**
     * 患者端查看自己发布的评价.
     *
     * @param serviceType 服务类型
     * @param serviceId   服务对应的主键
     * @param preUserId   患者端：当前用户角色编号
     * @param preUserType 用户类型
     * @return
     * @author cuill
     */
    @RpcService
    public List<Map<String, Object>> getEvaluationWithGiftByServiceId(String serviceType, String serviceId, Integer preUserId, String preUserType) {

        EvaluationService service = AppContextHolder.getBean("eh.evaluationService", EvaluationService.class);
        return service.getEvaluationWithGiftByServiceId(serviceType, serviceId, preUserId, preUserType);
    }


    /**
     * 根据医生内码和用户和评价图腾获取有效评价并装配-分页
     *
     * @param doctorId    医生内码
     * @param serviceType 业务类型 1转诊 2会诊 3咨询 4预约 5检查 6处方
     * @param tabTotem    评价对应的标签
     * @param preUserId   当前登录用户id
     * @param preUserType 当前登录用户类型
     * @param start       开始位置
     * @param limit       每页条数
     * @return
     * @author cuill
     * @Date 2017/6/1
     */
    @RpcService
    public List<PatientFeedback> queryValidEvaluationByDoctorId(Integer doctorId, String tabTotem, String serviceType, Integer preUserId,
                                                                String preUserType, int start, int limit) {
        EvaluationService service = AppContextHolder.getBean("eh.evaluationService", EvaluationService.class);
        return service.queryValidEvaluationByDoctorId(doctorId, tabTotem, serviceType, preUserId, preUserType, start, limit);
    }

    /**
     * 根据医生内码和用户和评价图腾获取有效评价并装配-分页
     *
     * @param doctorId    医生内码
     * @param serviceType 业务类型 1转诊 2会诊 3咨询 4预约 5检查 6处方
     * @param tabTotem    评价对应的标签
     * @param start       开始位置
     * @param limit       每页条数
     * @return
     * @author cuill
     * @Date 2017/6/14
     */
    @RpcService
    public List<PatientFeedback> queryFeedbackByDoctorIdAndTabTotemForPatient(Integer doctorId, String tabTotem, String serviceType, int start, int limit) {
        EvaluationService service = AppContextHolder.getBean("eh.evaluationService", EvaluationService.class);
        return service.queryFeedbackByDoctorIdAndTabTotemForPatient(doctorId, tabTotem, serviceType, start, limit);
    }

    /**
     * 评价页面心意内容-分页
     *
     * @param feedbackId
     * @param preUserId
     * @param preUserType
     * @return
     */
    @RpcService
    public List<MindGift> findPageMindGiftsByEvaluationId(int feedbackId, Integer start, Integer limit) {
        MindGiftService service = AppContextHolder.getBean("eh.mindGiftService", MindGiftService.class);
        return service.findPageMindGiftsByEvaluationId(feedbackId, start, limit);
    }

    /**
     * 同步生成公众号二维码
     *
     * @param materialId
     * @param wxConfigId
     */
    @RpcService
    public Integer createPublicQRInfo(String appId) {
        Integer fileId = 0;
        try {
            QRInfoService qrservice = AppContextHolder.getBean("eh.qrInfoService", QRInfoService.class);
            fileId = qrservice.createPublicQRInfo(appId);
        } catch (Exception e) {
            log.error("获取生成二维码[" + appId + "]" + e.getMessage());
        }
        return fileId;
    }

    /**
     * 第三方接入医生首页获取个性化数据
     *
     * @param appId  当前APPID
     * @param tempId 模板号
     * @return
     */
    @RpcService
    public List<Scratchable> findDocIndexModle() {
        ScratchableService service = AppContextHolder.getBean("eh.scratchableService", ScratchableService.class);
        return service.findDocIndexModle();
    }


    /**
     * 不需要身份证创建患者用户
     *
     * @param p
     * @param password
     * @return
     * @throws DAOException
     */
    @RpcService
    public Patient createPatientUserWithOutCard(final Patient p, final String password)
            throws DAOException {

        log.info("[{}] createPatientUser, params: p[{}], password[{}]", JSONUtils.toString(p), password);
        HibernateStatelessResultAction<HashMap<String, Object>> action = new AbstractHibernateStatelessResultAction<HashMap<String, Object>>() {
            @SuppressWarnings({"rawtypes", "unchecked"})
            @Override
            public void execute(StatelessSession statelessSession)
                    throws Exception {
                if (p == null) {
                    throw new DAOException("patient is require");
                }
                String mobile = p.getMobile();//手机号
                String name = p.getPatientName();//患者姓名
                if (mobile == null || StringUtils.isEmpty(mobile.trim())) {
                    throw new DAOException("patient.mobile is require");
                }
                if (name == null || StringUtils.isEmpty(name.trim())) {
                    throw new DAOException("patient.patientName is require");
                }
                if (StringUtils.isEmpty(p.getPatientSex())) {
                    throw new DAOException("patient.patientSex is require");
                }
                if (p.getBirthday() == null) {
                    throw new DAOException("patient.birthday is require");
                }
                UserDAO userDAO = DAOFactory.getDAO(UserDAO.class);
                PatientDAO patDao = DAOFactory.getDAO(PatientDAO.class);

                if (patDao.getByLoginId(mobile) != null) {
                    throw new DAOException("patient.mobile is exist");
                }
                HashMap<String, Object> map = new HashMap<String, Object>();
                // 未实现:更新或插入Patient表
                if (org.apache.commons.lang3.StringUtils.isEmpty(p.getFullHomeArea())) {
                    p.setFullHomeArea(patDao.getFullHomeArea(p.getHomeArea()));
                }
                p.setLoginId(mobile);
                Boolean guardianFlag = p.getGuardianFlag() == null ? false : p.getGuardianFlag();
                p.setGuardianFlag(guardianFlag);
                p.setIdcard(null);
                p.setRawIdcard(null);
                p.setCreateDate(new Date());
                p.setLastModify(new Date());
                p.setStatus(PatientConstant.PATIENT_STATUS_NORMAL);
                p.setHealthProfileFlag(p.getHealthProfileFlag() == null ? false : p.getHealthProfileFlag());
                p.setPatientType("1");// 1：自费
                Patient savedPatient = patDao.save(p);

                UserRoleTokenEntity ure = new UserRoleTokenEntity();
                ure.setRoleId(SystemConstant.ROLES_PATIENT);
                ure.setUserId(mobile);
                ure.setTenantId("eh");
                ure.setManageUnit("eh");

                User u = new User();
                u.setId(mobile);
                u.setName(name);
                u.setCreateDt(new Date());
                u.setLastModify(System.currentTimeMillis());
                u.setPlainPassword(password);
                u.setStatus("1");

                ure.setProperty("patient", savedPatient);

                // user表中不存在记录
                if (!userDAO.exist(mobile)) {
                    // 创建角色(user，userrole两张表插入数据)
                    userDAO.createUser(u, ure);
                } else {
                    UserRoleTokenDAO tokenDao = DAOFactory
                            .getDAO(UserRoleTokenDAO.class);
                    // user表中存在记录,角色表中不存在记录
                    Object object = tokenDao.getExist(mobile,
                            "eh", SystemConstant.ROLES_PATIENT);

                    if (object == null) {
                        ConfigurableItemUpdater<User, UserRoleToken> up = (ConfigurableItemUpdater<User, UserRoleToken>) UserController
                                .instance().getUpdater();
                        ure = (UserRoleTokenEntity) up.createItem(mobile, ure);

                    } else {
                        // user表中存在记录,角色表中存在记录
                        throw new DAOException(602, "该用户已注册过");
                    }
                }
                log.info("UserRoleTokenEntity:" + JSONUtils.toString(ure));
                map.put("ure", ure);

                setResult(map);
            }
        };

        HibernateSessionTemplate.instance().executeTrans(action);
        HashMap<String, Object> map = action.getResult();
        UserRoleToken ure = (UserRoleToken) map.get("ure");
        // 注册患者环信账户
        if (ure != null) {
            String userName = Easemob.getPatient(ure.getId());
            Easemob.registUser(userName, SystemConstant.EASEMOB_PATIENT_PWD);
            p.setUrt(ure.getId());
            //wx2.7 注册发放优惠劵
            CouponPushService couponService = new CouponPushService();
            couponService.sendRegisteCouponMsg(ure.getId(), "纳里健康APP端注册新用户发送优惠劵");
        }

        return p;
    }

    /**
     * 查询某一医生的排班信息
     *
     * @param doctorId 医生的id
     * @return 医生的排班信息, 结构为机构的职业点
     */
    @RpcService
    public Map<String, Map<Integer, List<AppointSchedule>>> getDoctorScheduling(final Integer doctorId) {
        Map<String, Map<Integer, List<AppointSchedule>>> las = Maps.newHashMap();
        //Map<String, Map<Integer, List<AppointSchedule>>> las = Maps.newHashMap();
        log.info(" 查询医生排班服务,医生id" + doctorId);

        //判断机构是否支持查询医生排班
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Doctor doctor = doctorDAO.getByDoctorId(doctorId);
        OrganConfig organConfig = DAOFactory.getDAO(OrganConfigDAO.class).getByOrganId(doctor.getOrgan());
        if (organConfig != null) {
            if (!organConfig.getDoctorScheduleSupport()) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "医生机构不支持查询排班信息");
            }
        } else if (organConfig == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "医生机构不支持查询排班信息");
        }


        //查询出医生排名的前limit条职业点信息
        HibernateStatelessResultAction<List<String>> action = new AbstractHibernateStatelessResultAction<List<String>>() {
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                StringBuilder queryOrganAndAddrSql = new StringBuilder("SELECT DISTINCT(CONCAT(sc.organId,',',dept.deptID,',',dept.name)) FROM bus_appointschedule sc,base_department dept  , BASE_Employment emp ");
                queryOrganAndAddrSql.append(" WHERE sc.departId =dept.deptId AND emp.doctorId=sc.doctorId and sc.doctorId=:doctorId ORDER BY emp.PrimaryOrgan ,CONCAT(sc.organId,dept.deptId)");
                Query q = ss.createSQLQuery(queryOrganAndAddrSql.toString());
                q.setParameter("doctorId", doctorId);
                setResult(q.list());
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        List<String> doctorOrgans = action.getResult();

        //根据职业点信息查询医生的排班信息
        for (final String doctorOrganString : doctorOrgans) {

            if (doctorOrganString.indexOf(",") > -1) {
                String[] organs = doctorOrganString.split(",");
                if (organs != null && Objects.equals(3, organs.length)) {
                    final String organId = organs[0];
                    final String depId = organs[1];
                    final String appointDepartName = organs[2];

                    AbstractHibernateStatelessResultAction<List<AppointSchedule>> doctorScheduleAction = new AbstractHibernateStatelessResultAction<List<AppointSchedule>>() {
                        @Override
                        public void execute(StatelessSession ss) throws Exception {

                            String sql = "from AppointSchedule where organId=:organId and departID=:depId and doctorId=:doctorId ORDER BY WEEK ,worktype,starttime,endtime";

                            Query appointScheduleQuery = ss.createQuery(sql);
                            appointScheduleQuery.setParameter("organId", Integer.parseInt(organId));
                            appointScheduleQuery.setParameter("depId", depId);
                            appointScheduleQuery.setParameter("doctorId", doctorId);

                            setResult(appointScheduleQuery.list());
                        }
                    };

                    HibernateSessionTemplate.instance().execute(doctorScheduleAction);

                    List<AppointSchedule> result = doctorScheduleAction.getResult();

                    Map<Integer, List<AppointSchedule>> weekMap = Maps.newHashMap();

                    for (int i = 1; i < 8; i++) {
                        List<AppointSchedule> daySechdules = Lists.newArrayList();
                        for (AppointSchedule appointSchedule : result) {
                            Integer week = appointSchedule.getWeek();
                            if (week != null && week == i) {
                                daySechdules.add(appointSchedule);
                            }
                        }
                        weekMap.put(i, daySechdules);
                    }

                    try {
                        String organName = DictionaryController.instance().get("eh.base.dictionary.Organ").getText(organId);
                        las.put(organName + " " + appointDepartName + "", weekMap);
                    } catch (ControllerException e) {
                        log.error(organId + "不存在");
                    }

                }
            }


        }


        return las;

    }


    /**
     * 患者报道 报道信息
     *
     * @param doctorId
     * @return
     */
    @RpcService
    public Map getTargetDoctorInfo(Integer doctorId) {
        FollowPlanTriggerService followPlanTriggerService = AppContextHolder.getBean("eh.followPlanTriggerService", FollowPlanTriggerService.class);
        return followPlanTriggerService.getTargetDoctorInfo(doctorId, FollowConstant.TRIGGEREVENT_SCANCODE);
    }

    /**
     * 一个医生的所有心意内容(不含患者头像)
     * 使用地方：[微信端]医生主页，
     *
     * @return
     */
    @RpcService
    public List<MindGift> findAllMindGiftsListByDoctorId(Integer doctorId, Integer start, Integer limit) {
        MindGiftService mindGiftService = AppContextHolder.getBean("eh.mindGiftService", MindGiftService.class);
        return mindGiftService.findAllMindGiftsListByDoctorId(doctorId, start, limit);
    }

    /**
     * 判断手机号 在患者端是否注册过
     * @param mobile
     * @return
     */
    @RpcService
    public boolean mobileRegisterOrNot(String mobile){
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        Patient patient = patientDAO.getByLoginId(mobile);
        if(null != patient){
            return true;
        }
        return false;
    }

    /**
     * 根据经纬度获取地理位置
     * @param latitude 地理位置纬度
     * @param longitude 地理位置经度
     * @return
     */
    @RpcService
    public Map getLocationInfo(String latitude,String longitude) {
        String result = LocationRevertUtils.parse(latitude, longitude);
        JSONObject object = JSONObject.parseObject(result);
        JSONObject locationObject = object.getJSONObject("result");
        return locationObject.getJSONObject("ad_info");
    }

    /**
     * 医院个性化服务app端
     *
     * @param organId
     * @param busType
     * @return
     */
    @RpcService
    public String queryDescription(Integer organId, Integer busType) {
        BussDescriptionDAO bussDescriptionDAO = DAOFactory.getDAO(BussDescriptionDAO.class);
        return bussDescriptionDAO.queryDescription(organId, busType);
    }

    //预约规则
    @RpcService
    public  BussDescription appointRegulation(int organId,Integer features){
        BussDescriptionDAO bussDescriptionDAO = DAOFactory.getDAO(BussDescriptionDAO.class);
        BussDescription bussDescription = bussDescriptionDAO.getByOrganIdAndFeatures(organId,features);
        return bussDescription;
    }
    @RpcService
    public List<AppointDepart> findAppointDepartUnLogin(int organId){
        AppointDepartDAO appointDepartDAO = DAOFactory.getDAO(AppointDepartDAO.class);
        List<AppointDepart> list = appointDepartDAO.findAllByOrganId(organId);
        return list;
    }
    @RpcService
    public Organ getOrganDetail(int organId){
        IYuYueQueryServiceInterface iYuYueQueryServiceInterface = AppContextHolder.getBean("ehop.yuYueOrganService",IYuYueQueryServiceInterface.class);
        return iYuYueQueryServiceInterface.getOrganDetail(organId);
    }

    @RpcService
    public Map<String,Object> screenOrgan(String addrArea, String organGrade, String sortType ,int start ,int num){
        IYuYueQueryServiceInterface iYuYueQueryServiceInterface = AppContextHolder.getBean("ehop.yuYueOrganService",IYuYueQueryServiceInterface.class);
        Map<String,Object> list = iYuYueQueryServiceInterface.screenOrgan(addrArea,organGrade,sortType,start,num);
        return list;
    }
    @RpcService
    public List<Doctor> getHotDoctors(String addrArea, String organGrade, String profession,int start, int num){
        IYuYueQueryServiceInterface iYuYueQueryServiceInterface = AppContextHolder.getBean("ehop.yuYueDoctorService",IYuYueQueryServiceInterface.class);
        return iYuYueQueryServiceInterface.getHotDoctors(addrArea,organGrade,profession,start,num);
    }

    @RpcService
    public Map<String,Object> getDoctorDetail(int doctorId){
        IYuYueQueryServiceInterface iYuYueQueryServiceInterface = AppContextHolder.getBean("ehop.yuYueDoctorService",IYuYueQueryServiceInterface.class);
        return iYuYueQueryServiceInterface.getDoctorDetail(doctorId);
    }


    @RpcService
    public List<Doctor> getOtherDoctorOfOrgan(Integer doctorId , Integer organId, String profession, Integer department,int max){
        IYuYueQueryServiceInterface iYuYueQueryServiceInterface = AppContextHolder.getBean("ehop.yuYueDoctorService",IYuYueQueryServiceInterface.class);
        return iYuYueQueryServiceInterface.getOtherDoctorOfOrgan(doctorId,organId,profession,department,max);
    }

    @RpcService
    public List<Map<String,Object>> findAppointDepartByOrganId(int organId){
        IYuYueQueryServiceInterface iYuYueQueryServiceInterface = AppContextHolder.getBean("ehop.yuYueDeptmentService",IYuYueQueryServiceInterface.class);
        return iYuYueQueryServiceInterface.findAppointDepartByOrganId(organId);
    }

    @RpcService
    public Map<String,Object> getAppointDepartmentDetail(int appointDepartId){
        IYuYueQueryServiceInterface iYuYueQueryServiceInterface = AppContextHolder.getBean("ehop.yuYueDeptmentService",IYuYueQueryServiceInterface.class);
        return iYuYueQueryServiceInterface.getAppointDepartmentDetail(appointDepartId);
    }

    @RpcService
    public List<AppointDepart> getHotDepartment( String area ,  int num){
        IYuYueQueryServiceInterface iYuYueQueryServiceInterface = AppContextHolder.getBean("ehop.yuYueDeptmentService",IYuYueQueryServiceInterface.class);
        return iYuYueQueryServiceInterface.getHotDepartment(area,num);
    }

    @RpcService
    public QueryResult<Map<String,Object>> screenAppointDepart(String area, String grade, String sort, String professionCode, int start , int limit){
        IYuYueQueryServiceInterface iYuYueQueryServiceInterface = AppContextHolder.getBean("ehop.yuYueDeptmentService",IYuYueQueryServiceInterface.class);
        return iYuYueQueryServiceInterface.screenAppointDepart(area,grade,sort,professionCode,start,limit);
    }
    @RpcService
    public List<Map<String,Object>> getDocAllSchedule(int doctorId) throws ControllerException{
        IYuYueQueryServiceInterface iYuYueQueryServiceInterface = AppContextHolder.getBean("ehop.yuYueAppointScheduleService",IYuYueQueryServiceInterface.class);
        return iYuYueQueryServiceInterface.getDocAllSchedule(doctorId);
    }

    @RpcService
    public List<Doctor> getDoctorScheduleOfDept(Integer organId, String profession, Integer department){
        IYuYueQueryServiceInterface iYuYueQueryServiceInterface = AppContextHolder.getBean("ehop.yuYueAppointScheduleService",IYuYueQueryServiceInterface.class);
        return iYuYueQueryServiceInterface.getDoctorScheduleOfDept(organId,profession,department);
    }

    @RpcService
    public List<Doctor> getDoctorScheduleOfAppointdept(Integer organId, String profession, Integer department,String appointDepart){
        IYuYueQueryServiceInterface iYuYueQueryServiceInterface = AppContextHolder.getBean("ehop.yuYueAppointScheduleService",IYuYueQueryServiceInterface.class);
        return iYuYueQueryServiceInterface.getDoctorScheduleOfAppointdept(organId,profession,department,appointDepart);
    }

    /**
     * 获取找医院总数
     * @param addrArea
     * @param organGrade
     * @return
     */
    @RpcService
    public Long getScreenOrganNum(String addrArea, String organGrade){
        IYuYueQueryServiceInterface iYuYueQueryServiceInterface = AppContextHolder.getBean("ehop.yuYueOrganService",IYuYueQueryServiceInterface.class);
        return iYuYueQueryServiceInterface.getScreenOrganNum(addrArea,organGrade);

    }

    /**
     *找科室筛选总数
     * @param area
     * @param grade
     * @param professionCode
     * @return
     */
    @RpcService
    public Long getScreenAppointDepartNum( String area,  String grade,  String professionCode){
        IYuYueQueryServiceInterface iYuYueQueryServiceInterface = AppContextHolder.getBean("ehop.yuYueDeptmentService",IYuYueQueryServiceInterface.class);
        return iYuYueQueryServiceInterface.getScreenAppointDepartNum(area,grade,professionCode);
    }

    /**
     * 获取挂号科室号源
     * @param organId
     * @param department
     * @param appointDepartCode
     * @param start
     * @return
     */
    @RpcService
    public List<HashMap<String, Object>> findAllDocAndSourcesOfAppointDepart (int organId, String appointDepartCode, int start){
        IYuYueQueryServiceInterface iYuYueQueryServiceInterface = AppContextHolder.getBean("ehop.yuYueAppointSourceService",IYuYueQueryServiceInterface.class);
        return iYuYueQueryServiceInterface.findAllDocAndSourcesOfAppointDepart( organId,appointDepartCode,  start);
    }

    /**
     * 获取医生号源
     * @param doctorId
     * @param start
     * @return
     */
    @RpcService
    public List<HashMap<String, Object>> findAllSourcesOfDoc(int doctorId, int start){
        IYuYueQueryServiceInterface iYuYueQueryServiceInterface = AppContextHolder.getBean("ehop.yuYueAppointSourceService",IYuYueQueryServiceInterface.class);
        return iYuYueQueryServiceInterface.findAllSourcesOfDoc(doctorId,start);
    }

    /**
     * 生成临时二维码
     * @return
     */
    @RpcService
    public HashMap<String, String> createProvisionalQrCode(){
        IWXServiceInterface wxService = AppContextHolder.getBean("eh.wxService", IWXServiceInterface.class);
        return wxService.createProvisionalQrCode("0", WebLogonManager.logonAppId);
    }

    /**
     * 健康app患者注册发送验证码
     *
     * @param mobile
     * @return
     */
    @RpcService
    public void sendVCodeForNotRegisterPatientOfWeb(String mobile){
        this.sendVCodeForNotRegisterPatient(mobile);
    }

    /**
     * 校验验证码并创建用户
     * @param phone
     * @param identify
     * @return
     */
    @RpcService
    public Patient createPatientUserWithOutCardForWeb(final Patient p, final String password,String phone, String identify) {
        ValidateCodeDAO vcDao = DAOFactory.getDAO(ValidateCodeDAO.class);
        boolean machResult =  vcDao.machValidateCode(phone,identify);
        Patient patient = null;
        if (machResult){
            patient =  this.createPatientUserWithOutCard(p,password);
        }
        return patient;
    }
    /**
     * 找回密码  不登录
     */
    @RpcService
    public void getBackPwdSendValidateCodeForWeb(String mobile, String roleId){
        ValidateCodeDAO validateCodeDAO = AppContextHolder.getBean("eh.validateCode",ValidateCodeDAO.class);
        validateCodeDAO.sendValidateCodeForWeb(mobile,roleId);
    }

    /**
     * 3.2.0 yypt
     *
     * 获取检查项目
     *
     * @return
     */
    @RpcService
    public OrganCheckItem getById(Integer id){
        if(id==null){
            throw new DAOException(DAOException.VALUE_NEEDED,"id is require");
        }
        return DAOFactory.getDAO(OrganCheckItemDAO.class).getByOrganItemId(id);
    }

    @RpcService
    public String getDoctorById(Integer doctor) {
        DoctorDAO dao = DAOFactory.getDAO(DoctorDAO.class);
        return dao.getNameById(doctor);
    }


    /**
     * 获取三伏天活动医生列表
     * @param organ 机构id
     * @param start
     * @param limit
     * @return
     */
    @RpcService
    public List<Map<String,Object>> findDogDaysDoctors(Integer organId,Integer start,Integer limit){
        DogDaysService dogDaysService = AppContextHolder.getBean("eh.dogDaysService", DogDaysService.class);
        return dogDaysService.findDogDaysDoctors(organId,start,limit);
    }
}
