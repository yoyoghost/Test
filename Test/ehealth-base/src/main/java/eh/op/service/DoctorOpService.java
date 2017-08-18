package eh.op.service;

import ctd.access.AccessToken;
import ctd.access.AccessTokenController;
import ctd.account.UserRoleToken;
import ctd.controller.exception.ControllerException;
import ctd.controller.notifier.NotifierCommands;
import ctd.controller.notifier.NotifierMessage;
import ctd.dictionary.*;
import ctd.dictionary.Dictionary;
import ctd.net.broadcast.MQHelper;
import ctd.net.broadcast.Publisher;
import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.persistence.support.impl.access.AccessTokenDAO;
import ctd.util.AppContextHolder;
import ctd.util.BeanUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.constant.SystemConstant;
import eh.base.dao.*;
import eh.base.service.BusActionLogService;
import eh.base.user.UserSevice;
import eh.bus.dao.ConsultDAO;
import eh.bus.dao.ConsultSetDAO;
import eh.bus.service.consult.OnsConfig;
import eh.entity.base.*;
import eh.entity.bus.ConsultAndPatient;
import eh.entity.bus.ConsultSet;
import eh.op.auth.service.SecurityService;
import eh.util.ChinaIDNumberUtil;
import eh.utils.ValidateUtil;
import eh.wxpay.util.Util;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.StatelessSession;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.util.*;

/**
 * Created by houxr on 2016/5/31.
 */
public class DoctorOpService {
    private static final Log logger = LogFactory.getLog(DoctorOpService.class);
    private byte[] mLock = new byte[0];

    /**
     * 查询医生信息和医生执业信息服务 医生审核状态为 1审核通过，状态正常 9注销
     *
     * @param name
     * @param idNumber
     * @param organ
     * @param profession
     * @param department
     * @param start
     * @param limit
     * @return docList
     * @author houxr
     */
    @RpcService
    public List<Doctor> queryDoctorAndEmploymentForOP(final String name,
                                                      final String idNumber, final Integer organ,
                                                      final String profession, final Integer department,
                                                      final Integer start, final Integer limit) {
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        return doctorDAO.queryDoctorAndEmploymentForOP(name, idNumber, organ, profession, department, start, limit);
    }

    /**
     * 查询医生信息和医生执业信息服务 医生审核状态为 1审核通过，状态正常 9注销
     * <p>
     * 运营平台（权限改造）
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
    public QueryResult<Doctor> queryDoctorResultForOP(final String name,
                                                      final String idNumber, final Integer organ,
                                                      final String profession, final Integer department,
                                                      final Integer start, final Integer limit, final Integer status, final Integer userType) {
        Set<Integer> o = new HashSet<Integer>();
        o.add(organ);
        if (!SecurityService.isAuthoritiedOrgan(o)) {
            return null;
        }
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        return doctorDAO.queryDoctorResultForOP(name, idNumber, organ, profession, department, start, limit, status, userType);
    }

    /**
     * 根据机构统计本机构有count位医生
     *
     * @param organId
     * @return
     */
    @RpcService
    public Long countDoctorByOrganId(Integer organId) {
        if (organId != null) {
            DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
            return doctorDAO.getNormalCountDoctorByOrganId(organId);
        }
        return 0L;
    }

    /**
     * [运营平台]医生信息添加服务。
     * <p>不输入身份证号和手机号也可以添加</p>
     *
     * @param d
     * @author lijiafei
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
        /* 可以不输入身份证号
        if (StringUtils.isEmpty(d.getIdNumber())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "idNumber is required");
        }*/
        if (StringUtils.isEmpty(d.getProfession())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "profession is required");
        }
        /* 可以不输入手机号
        if (StringUtils.isEmpty(d.getMobile())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "mobile is required");
        }*/
        if (d.getOrgan() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "organ is required");
        }

        final DoctorDAO doctorDao = DAOFactory.getDAO(DoctorDAO.class);

        // 如果有手机号需要验证手机号是否被其他医生所使用
        if (!StringUtils.isEmpty(d.getMobile())) {
            Doctor existed = doctorDao.getByMobile(d.getMobile());
            if (existed != null) {
                throw new DAOException(609, "具有手机号[" + d.getMobile() + "]的医生已存在，请使用其他的手机号");
            }
        }

        HibernateStatelessResultAction<Doctor> action = new AbstractHibernateStatelessResultAction<Doctor>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                d.setCreateDt(new Date());
                d.setLastModify(new Date());
                d.setHaveAppoint(0);
                d.setStatus(1);
                d.setSource(0);// 0:后台导入，1：注册
                if (d.getGroupType() == null) {//默认普通
                    d.setGroupType(0);
                }
                d.setRewardFlag(false);
                d.setProTitle(StringUtils.isEmpty(d.getProTitle()) ? "99" : d.getProTitle());
                if (d.getVirtualDoctor() == null) {
                    d.setVirtualDoctor(false);
                }
                Doctor target = doctorDao.save(d);

                if (target != null && target.getDoctorId() != null) {
                    ConsultSet set = new ConsultSet();
                    set.setDoctorId(target.getDoctorId());
                    // set.setOnLineStatus(0);
                    // set.setAppointStatus(0);
                    set.setTransferStatus(0);
                    set.setMeetClinicStatus(0);
                    set.setPatientTransferStatus(0);
                    ConsultSetDAO setDao = DAOFactory
                            .getDAO(ConsultSetDAO.class);
                    setDao.save(set);
                }
                //更新医生搜索权重
                doctorDao.updateDoctorSearchRating(target.getDoctorId());
                setResult(target);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
        Integer key = d.getDoctorId();
        DictionaryItem item = doctorDao.getDictionaryItem(key);
        NotifierMessage msg = new NotifierMessage(NotifierCommands.ITEM_CREATE, "eh.base.dictionary.Doctor");
        msg.setLastModify(System.currentTimeMillis());
        msg.addUpdatedItems(item);
        try {
            DictionaryController.instance().getUpdater().notifyMessage(msg);
        } catch (ControllerException e) {
            logger.error(e);
        }

        return action.getResult();
    }

    /**
     * 医生信息更新服务
     *
     * @param doctor
     * @author houxr
     * @2016-06-24 17:30:21
     */
    @RpcService
    public Boolean updateDoctorByDoctorId(final Doctor doctor) {
        if (doctor.getDoctorId() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "doctorId is required");
        }
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        // 如果修改手机号需要验证手机号是否被其他医生所使用
        if (!StringUtils.isEmpty(doctor.getMobile())) {
            Doctor existed = doctorDAO.getByMobile(doctor.getMobile());
            if (existed != null && !existed.getDoctorId().equals(doctor.getDoctorId())) {
                throw new DAOException(609, "具有手机号[" + doctor.getMobile() + "]的医生已存在，请使用其他的手机号");
            }
        }
        Doctor target = doctorDAO.getByDoctorId(doctor.getDoctorId());
        String oldProTitle = target.getProTitle();
        String newProTitle = doctor.getProTitle();
        doctor.setLastModify(new Date());
        BeanUtils.map(doctor, target);
        target.setProTitle(StringUtils.isEmpty(target.getProTitle()) ? "99" : target.getProTitle());
        Doctor d = doctorDAO.update(target);

        //更新医生日志记录
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        Organ organ = organDAO.getByOrganId(target.getOrgan());
        StringBuffer logMsg =new StringBuffer();
        if(!oldProTitle.equals(newProTitle)){
            Dictionary proTitleDic = null;
            try {
                proTitleDic = DictionaryController.instance().get(
                        "eh.base.dictionary.ProTitle");
            } catch (ControllerException e) {
                e.printStackTrace();
            }
            logMsg.append("职称：").append(proTitleDic.getText(oldProTitle)).append("更新为").append(proTitleDic.getText(newProTitle));
        }

        BusActionLogService.recordBusinessLog("医生信息修改", doctor.getDoctorId().toString(), "Doctor",
                "[" + organ.getShortName() + "]的[" + target.getName() + "](" + doctor.getDoctorId() + ")医生"
                        + (ObjectUtils.nullSafeEquals(doctor.getStatus(), 9) ? "被注销" : "被修改"+logMsg));

        //当医生注销时:同时需要注销这个医生的token信息
        if (ObjectUtils.nullSafeEquals(d.getStatus(), 9)) {
            try {
                String dMobile = d.getMobile();
                Integer u = StringUtils.isEmpty(dMobile) ? null : Util.getUrtForDoctor(dMobile);
                if (u != null) {
                    AccessTokenDAO accessDao = DAOFactory.getDAO(AccessTokenDAO.class);
                    List<AccessToken> tokenList = accessDao.findByUser(dMobile, u);
                    for (AccessToken accessToken : tokenList) {
                        AccessTokenController.instance().getUpdater().remove(accessToken.getId());
                    }
                }
            } catch (ControllerException e) {
                logger.error(e.getMessage());
            }
        }

        Integer key = d.getDoctorId();
        DictionaryItem item = doctorDAO.getDictionaryItem(key);
        NotifierMessage msg = new NotifierMessage(NotifierCommands.ITEM_UPDATE, "eh.base.dictionary.Doctor");
        msg.setLastModify(System.currentTimeMillis());
        msg.addUpdatedItems(item);
        try {
            DictionaryController.instance().getUpdater().notifyMessage(msg);
        } catch (ControllerException e) {
            logger.error(e.getMessage());
        }

        new UserSevice().updateUserCache(d.getMobile(), SystemConstant.ROLES_DOCTOR, "doctor", d);
        this.changeDoctorStatusForOp(doctor);
        return true;
    }

    /**
     * 运营平台 增加医生账户积分
     *
     * @param doctorId
     * @param inCome
     */
    @RpcService
    public void addDoctorIncomeByDoctorId(final int doctorId, BigDecimal inCome, Integer serverPriceId) {
        if (ObjectUtils.isEmpty(doctorId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "doctorId of doctorAccount is required");
        }
        inCome = (inCome == null ? new BigDecimal("0") : inCome);
        DoctorAccountDAO doctorAccountDAO = DAOFactory.getDAO(DoctorAccountDAO.class);
        DoctorAccount account = doctorAccountDAO.getByDoctorId(doctorId);
        if (account == null) {
            doctorAccountDAO.addOrUpdateDoctorAccount(new DoctorAccount(doctorId));
            account = doctorAccountDAO.getByDoctorId(doctorId);
        }
        if (account.getPayOut() == null) {
            account.setPayOut(new BigDecimal("0"));
        }
        if (account.getInCome() == null) {
            account.setInCome(new BigDecimal("0"));
        }
        // 校验 是否相等 accountDetail == account.price
        DoctorAccountDetailDAO doctorAccountDetailDAO = DAOFactory.getDAO(DoctorAccountDetailDAO.class);
        BigDecimal detailSumMoney = doctorAccountDetailDAO.getSumMoneyByDoctorIdAndInout(doctorId, 1);

        BigDecimal doctorIncome = doctorAccountDAO.getInComeByDoctorId(doctorId);
        if ((detailSumMoney == null ? new BigDecimal(0d) : detailSumMoney).compareTo(doctorIncome) != 0) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "doctorId:" + doctorId + " inCome not equal doctorAccountDetail Sum(Money)");
        }
        synchronized (mLock) {
            if ((account.getInCome() == null ? new BigDecimal("0") : account.getInCome()).subtract(account.getPayOut()).add(inCome).doubleValue() < 0) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "积分调整后余额不能小于0!");
            }
        }
        if (serverPriceId == null) {
            serverPriceId = 9;
        }
        String summary = "其他";
        Integer bussTypeId = 0;
        if (serverPriceId.intValue() == 29)//咨询
        {
            bussTypeId = 3;
            summary = "咨询";
        } else if (serverPriceId.intValue() == 30)//关注
        {
            bussTypeId = 8;
            summary = "关注";
        }
        //先查找有 其他 serviceId:9，获取需要bussId:0 添加的 income
        //添加一条accountDetail记录,新增收支明细
        DoctorAccountDetail detail = new DoctorAccountDetail();
        detail.setDoctorId(doctorId);
        detail.setInout(1);
        detail.setCreateDate(new Date());
        detail.setSummary(summary);
        detail.setMoney(inCome);
        detail.setServerId(serverPriceId);
        detail.setBussType(bussTypeId);
        detail.setBussId(0);
        detail.setClosure(0);
        DoctorAccountDetailDAO detailDAO = DAOFactory.getDAO(DoctorAccountDetailDAO.class);
        detailDAO.save(detail);
        // 更新 account中的income值
        doctorAccountDAO.updateDoctorAccountByDoctorIdAndInCome(inCome, doctorId);

        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        Doctor doctor = doctorDAO.get(doctorId);
        Organ organ = organDAO.get(doctor.getOrgan());

        //保存操作日志
        UserRoleToken urt = UserRoleToken.getCurrent();
        BusActionLogDAO actionLogDAO = DAOFactory.getDAO(BusActionLogDAO.class);
        BusActionLog actionLog = new BusActionLog();
        actionLog.setActionTime(new Date());
        actionLog.setUserId(urt.getId());
        actionLog.setUserName(urt.getUserName());
        actionLog.setBizId(String.valueOf(doctorId));
        actionLog.setBizClass("DoctorAccount");
        actionLog.setIpAddress(urt.getLastIPAddress());
        actionLog.setActionType("医生添加积分");
        actionLog.setActionContent("给[" + organ.getShortName() + "]的[" + doctor.getName() + "](" + doctorId + ")医生增加" + inCome + "积分");
        actionLog.setExecuteTime(1);
        actionLogDAO.saveBusActionLog(actionLog);

        // 校验 是否相等 accountDetail == account.price
        BigDecimal newDetailSumMoney = doctorAccountDetailDAO.getSumMoneyByDoctorIdAndInout(doctorId, 1);
        BigDecimal newDoctorIncome = doctorAccountDAO.getInComeByDoctorId(doctorId);
        if (newDetailSumMoney.compareTo(newDoctorIncome) != 0) {
            throw new DAOException(609, "updated doctorId:" + doctorId + " inCome not equal doctorAccountDetail Sum(Money)");
        }


    }

    /**
     * 批量导入医生积分
     *
     * @param doctorId
     * @param inCome
     * @param xlsId
     */
    public void addDoctorIncomeByDoctorIdForImport(final int doctorId, final BigDecimal inCome, Integer xlsId) {
        if (ObjectUtils.isEmpty(doctorId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "doctorId of doctorAccount is required");
        }

        DoctorAccountDAO doctorAccountDAO = DAOFactory.getDAO(DoctorAccountDAO.class);
        DoctorAccount account = doctorAccountDAO.getByDoctorId(doctorId);
        if (account == null) {
            doctorAccountDAO.addOrUpdateDoctorAccount(new DoctorAccount(doctorId));
        }

        // 校验 是否相等 accountDetail == account.price
        DoctorAccountDetailDAO doctorAccountDetailDAO = DAOFactory.getDAO(DoctorAccountDetailDAO.class);
        BigDecimal detailSumMoney = doctorAccountDetailDAO.getSumMoneyByDoctorIdAndInout(doctorId, 1);

        BigDecimal doctorIncome = doctorAccountDAO.getInComeByDoctorId(doctorId);
        if ((detailSumMoney == null ? new BigDecimal(0d) : detailSumMoney).compareTo(doctorIncome) != 0) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "doctorId:" + doctorId + " inCome not equal doctorAccountDetail Sum(Money)");
        }

        //先查找有 其他 serviceId:9，获取需要bussId:0 添加的 income
        //添加一条accountDetail记录,新增收支明细
        DoctorAccountDetail detail = new DoctorAccountDetail();
        detail.setDoctorId(doctorId);
        detail.setInout(1);
        detail.setCreateDate(new Date());
        detail.setSummary("其他");
        detail.setMoney(inCome == null ? new BigDecimal("0") : inCome);
        detail.setServerId(9);
        detail.setBussType(9);
        detail.setBussId(0);
        detail.setClosure(0);
        DoctorAccountDetailDAO detailDAO = DAOFactory.getDAO(DoctorAccountDetailDAO.class);
        detailDAO.save(detail);
        // 更新 account中的income值
        doctorAccountDAO.updateDoctorAccountByDoctorIdAndInCome(inCome, doctorId);

        // 校验 是否相等 accountDetail == account.price
        BigDecimal newDetailSumMoney = doctorAccountDetailDAO.getSumMoneyByDoctorIdAndInout(doctorId, 1);
        BigDecimal newDoctorIncome = doctorAccountDAO.getInComeByDoctorId(doctorId);
        if (newDetailSumMoney.compareTo(newDoctorIncome) != 0) {
            throw new DAOException(609, "updated doctorId:" + doctorId + " inCome not equal doctorAccountDetail Sum(Money)");
        }

        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        Doctor doctor = doctorDAO.get(doctorId);
        Organ organ = organDAO.get(doctor.getOrgan());

        //保存操作日志
        UserRoleToken urt = UserRoleToken.getCurrent();
        BusActionLogDAO actionLogDAO = DAOFactory.getDAO(BusActionLogDAO.class);
        BusActionLog actionLog = new BusActionLog();
        actionLog.setActionTime(new Date());
        actionLog.setUserId(urt.getId());
        actionLog.setUserName(urt.getUserName());
        actionLog.setBizId(String.valueOf(doctorId));
        actionLog.setBizClass("DoctorAccount");
        actionLog.setIpAddress(urt.getLastIPAddress());
        actionLog.setActionType("批量添加医生积分");
        actionLog.setActionContent("批次号：" + xlsId + "，给[" + organ.getShortName() + "]的[" + doctor.getName() + "](" + doctorId + ")医生增加" + inCome + "积分");
        actionLog.setExecuteTime(1);
        actionLogDAO.saveBusActionLog(actionLog);
    }


    /**
     * 运行平台新的医生是否有开户相关查询
     *
     * @param doctorName     医生姓名
     * @param idNumber       身份证号码
     * @param mobile         手机号码
     * @param invitationCode 邀请码
     * @param organ          机构
     * @param department     部门
     * @param status         医生状态
     * @param isOpenAccount  是否已开户
     * @param startDate      起始日期
     * @param endDate        结束日期
     * @return
     * @author houxr
     * @date 2016-08-11
     */
    @RpcService
    public QueryResult<Doctor> queryDoctorByIsOpenAccountAndStartAndLimit(final String doctorName, final String idNumber,
                                                                          final String mobile, final Integer invitationCode,
                                                                          final Integer organ, final Integer department,
                                                                          final Integer status, final Integer isOpenAccount,
                                                                          final Date startDate, final Date endDate,
                                                                          final int start, final int limit, final Boolean transferStatus,
                                                                          final Boolean meetClinicStatus, final Boolean onLineStatus,
                                                                          final Boolean appointStatus, final Boolean patientTransferStatus,
                                                                          final Boolean virtualDoctor) {
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        return doctorDAO.queryDoctorByStartAndLimit(doctorName, idNumber, mobile, invitationCode, organ, department, status, isOpenAccount, startDate, endDate, start, limit,
                transferStatus, meetClinicStatus, onLineStatus, appointStatus, patientTransferStatus, virtualDoctor);
    }

    /**
     * 运营平台按关键字和开户状态查询
     *
     * @param keywords 姓名/手机号
     * @param isUser   是否开户
     * @param organId  所属机构
     * @param start
     * @param limit
     * @return
     */
    @RpcService
    public QueryResult<Doctor> queryDoctorByKeywordsAndIsUserAndOrganId(final String keywords,
                                                                        final Boolean isUser, final Integer organId, final int start, final int limit) {
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        return doctorDAO.queryDoctorByKeywordsAndIsUserAndOrganId(keywords, isUser, organId, start, limit);
    }


    private List<Doctor> validateDoctorsRegistration(List<Doctor> listNeedValidateDoctors) {
        List<Doctor> failedDoctors = new ArrayList<Doctor>();
        Iterator<Doctor> iter = listNeedValidateDoctors.iterator();
        while (iter.hasNext()) {
            Doctor failedDoctor = this.valideDoctorRegistration(iter.next());
            if (failedDoctor != null) {
                //有非法数据
                failedDoctors.add(failedDoctor);
            }
        }
        return failedDoctors;
    }

    private Doctor valideDoctorRegistration(Doctor d) {
        if (d == null) {
            return null;
        }
        StringBuilder errorMessage = new StringBuilder("");
        if (StringUtils.isEmpty(d.getIdNumber())) {
            errorMessage.append("身份证不能为空;");
        }
        if (StringUtils.isEmpty(d.getName())) {
            errorMessage.append("姓名不能为空;");
        }
        if (StringUtils.isEmpty(d.getMobile())) {
            errorMessage.append("手机号不能为空;");
        }
        if (ValidateUtil.nullOrZeroInteger(d.getOrgan())) {
            errorMessage.append("机构不能为空;");
        }
        if (!ValidateUtil.isMobile(d.getMobile())) {
            errorMessage.append("手机号码不正确;");
            // 手机号码不正确
        }
        if (StringUtils.isEmpty(d.getGender())) {
            errorMessage.append("性别不能为空;");
        }
        if (d.getUserType() == null) {
            errorMessage.append("用户类型不能为空;");
        }
        if (d.getBirthDay() == null) {
            errorMessage.append("生日不能为空;");
        }
        if (StringUtils.isEmpty(d.getProfession())) {
            errorMessage.append("专科不能为空;");
        }

        String idNumber = d.getIdNumber().toUpperCase();
        if (idNumber.length() != 15 && idNumber.length() != 18) {
            errorMessage.append("身份证号不合法;");
        } else {
            String sex = "";
            String idCard18;
            try {
                idCard18 = ChinaIDNumberUtil.convert15To18(idNumber);
                sex = ChinaIDNumberUtil.getSexFromIDNumber(idCard18);
            } catch (Exception e) {
                errorMessage.append("身份证号不合法;");
            }
            if (!sex.equals(d.getGender())) {
                errorMessage.append("输入的性别和身份证号获取的性别不一致;");
            }
        }
        List<Employment> employments = d.getEmployments();
        Iterator<Employment> iter = employments.iterator();
        while (iter.hasNext()) {
            Employment e = iter.next();
            if (StringUtils.isEmpty(e.getJobNumber())) {
                errorMessage.append("工号不能为空;");
            }

            if (ValidateUtil.nullOrZeroInteger(e.getDepartment())) {
                errorMessage.append("科室编号不能为空;");
            }
        }
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Doctor doctor = doctorDAO.getByMobile(d.getMobile());
        if (doctor != null) {
            errorMessage.append("该医生已存在，请勿重复添加;");
        }
        Doctor doctorIDNumber = doctorDAO.getByIdNumber(d.getIdNumber());
        if (doctorIDNumber != null) {
            errorMessage.append("该医生已存在，请勿重复添加;");
        }
        if (errorMessage.toString().length() > 0) {
            //验证失败时，用Introduce来作为错误信息变量。
            d.setIntroduce(errorMessage.toString());
            return d;
        } else {
            return null;
        }
    }


    public Integer importDoctor(Doctor doctor, Integer deptCode, String jobNum) {
        doctor = addDoctor(doctor);//保存医生信息并刷新缓存
        int doctorId = doctor.getDoctorId();
        SignSetOpService signSetOpService = AppContextHolder.getBean("signSetOpService", SignSetOpService.class);
        ConsultSet consultSet = new ConsultSet();
        consultSet.setDoctorId(doctorId);
 /*       consultSet.setOnLineStatus(0);
        consultSet.setOnLineConsultPrice(0.0);
        consultSet.setAppointStatus(0);
        consultSet.setAppointConsultPrice(0.0);*/
        consultSet.setRemindInTen(false);
        consultSet.setTransferStatus(0);
        consultSet.setMeetClinicStatus(0);
        consultSet.setPatientTransferStatus(0);
        consultSet.setPatientTransferPrice(0.0);
        consultSet.setSignStatus(false);
        consultSet.setSignPrice(0.0);
        consultSet.setIntervalTime(0);
        consultSet.setAppointDays(0);
        consultSet.setSignTime("1");
        consultSet.setCanSign(false);
        signSetOpService.updateDoctorConsultSetForOp(consultSet);//创建默认consultSet
        EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
        Employment employment = new Employment();
        employment.setDoctorId(doctorId);
        employment.setJobNumber(jobNum);
        employment.setOrganId(doctor.getOrgan());
        employment.setDepartment(deptCode);
        employment.setPrimaryOrgan(true);
        employment.setClinicEnable(false);
        employment.setClinicPrice(0.0);
        employment.setConsultationEnable(false);
        employment.setConsultationPrice(0.0);
        employment.setClinicEnable(false);
        employment.setClinicPrice(0.0);
        employment.setProfClinicPrice(0.0);
        employment.setSpecClinicPrice(0.0);
        employment.setTransferEnable(false);
        employment.setTransferPrice(0.0);
        employment.setApplyTransferEnable(false);
        employmentDAO.addEmployment(employment);
        return doctorId;
    }

    /**
     * 创建一个无手机号且身份证号码为自动生成的医生(请勿随意调用，暂只提供给贵州和咸阳)
     *
     * @param d
     * @param e
     * @return
     */
    @RpcService
    public Doctor createDoctorByTimeMillis(Doctor d, Employment e) {
        if (d == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "doctor is required");
        }
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
        if (StringUtils.isEmpty(d.getProfession())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "profession is required");
        }
        if (d.getOrgan() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "organ is required");
        }
        if (e == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "employment is required");
        }
        if (StringUtils.isEmpty(e.getJobNumber())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "jobNumber is required");
        }
        if (e.getDepartment() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "department is required");
        }
        String cardNum = "WRZ0" + System.currentTimeMillis();
        d.setIdNumber(cardNum);
        d = this.addDoctor(d);
        EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
        e.setOrganId(d.getOrgan());
        e.setDoctorId(d.getDoctorId());
        employmentDAO.addEmployment(e);
        return d;
    }

    /**
     * 运营平台（权限改造）
     *
     * @param doctorId
     * @return
     */
    @RpcService
    public Doctor getByDoctorIdForOp(Integer doctorId) {
        if(doctorId==null){
            throw new DAOException(DAOException.VALUE_NEEDED,"doctorId is require");
        }
        Doctor d = DAOFactory.getDAO(DoctorDAO.class).getByDoctorId(doctorId);
        if(d==null){
            return null;
        }
        Set<Integer> o = new HashSet<Integer>();
        o.add(d.getOrgan());
        EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
        List<Employment> ems = employmentDAO.findByDoctorId(doctorId);
        if(ems!=null){
            for(Employment e:ems){
                o.add(e.getOrganId());
            }
        }
        if(!SecurityService.isAuthoritiedOrgan(o)){
            return null;
        }
        return d;
    }

    /**
     * 消息改造
     * @param id
     * @return
     */
    @RpcService
    public ConsultAndPatient getConsultAndPatientAndCdrOtherdocById(
            Integer id) {
        ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
        ConsultAndPatient cap = consultDAO.getConsultAndPatientAndCdrOtherdocById(id);
        if (cap==null){
            return null;
        }
        Set<Integer> o = new HashSet<Integer>();
        o.add(cap.getConsult().getConsultOrgan());
        if(!SecurityService.isAuthoritiedOrgan(o)){
            return null;
        }
        return cap;
    }

    @RpcService
    public List<Doctor> findAllTeams(){
        return DAOFactory.getDAO(DoctorDAO.class).findAllTeams();
    }

    public void changeDoctorStatusForOp(Doctor doctor){
        try {
            Publisher publisher = MQHelper.getMqPublisher();
            Map<String, Object> data = new HashMap<String, Object>();
            data.put("type", BusActionLogService.CHANGE_STATUS);
            data.put("event", "changedocstatus");
            data.put("doctorId", doctor.getDoctorId());
            data.put("status", doctor.getStatus());
            publisher.publish(OnsConfig.logTopic, data);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

}
