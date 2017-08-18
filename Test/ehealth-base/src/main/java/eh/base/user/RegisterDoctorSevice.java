package eh.base.user;

import ctd.account.UserRoleToken;
import ctd.account.user.User;
import ctd.account.user.UserController;
import ctd.account.user.UserRoleTokenEntity;
import ctd.controller.updater.ConfigurableItemUpdater;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.impl.user.UserDAO;
import ctd.persistence.support.impl.user.UserRoleTokenDAO;
import ctd.schema.exception.ValidateException;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.dao.DepartmentDAO;
import eh.base.dao.DoctorDAO;
import eh.base.dao.EmploymentDAO;
import eh.base.dao.OrganDAO;
import eh.bus.dao.ConsultSetDAO;
import eh.entity.base.Department;
import eh.entity.base.Doctor;
import eh.entity.base.Employment;
import eh.entity.base.Organ;
import eh.entity.bus.ConsultSet;
import eh.entity.mpi.Patient;
import eh.mpi.dao.PatientDAO;
import eh.util.ChinaIDNumberUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.StatelessSession;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class RegisterDoctorSevice {

    private static final Log logger = LogFactory.getLog(RegisterDoctorSevice.class);

    /**
     * 保存虚拟科室-将一个医院下的科室拷贝到另一个科室下
     *
     * @param organId
     * @param copyOrgan
     * @author zhangx
     * @date 2015-10-10下午7:27:29
     */
    @RpcService
    public void saveXNDept(int organId, int copyOrgan) {
        DepartmentDAO departDao = DAOFactory.getDAO(DepartmentDAO.class);
        List<Department> depts = departDao.findByOrganId(organId);

        for (Department department : depts) {
            department.setDeptId(null);
            department.setOrganId(copyOrgan);
            departDao.save(department);
        }
    }

    /**
     * 保存一个虚拟医生
     *
     * @param doctor 虚拟医生信息
     * @param e      职业点信息
     * @param code   科室his编号
     * @return
     * @author zhangx
     * @date 2015-10-10下午3:19:47
     */
    @RpcService
    public Boolean saveOneXNDoctor(final Doctor doctor, final Employment e,
                                   final String code) {
        AbstractHibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {

                logger.info("-------rpc.doctor-----" + JSONUtils.toString(doctor));
                logger.info("-------rpc.e-----" + JSONUtils.toString(e));
                logger.info("-------rpc.code-----" + code);

                DepartmentDAO depart = DAOFactory.getDAO(DepartmentDAO.class);
                Department d = depart
                        .getByCodeAndOrgan(code, doctor.getOrgan());

                // 保存医生信息
                doctor.setProfession(d.getProfessionCode());
                doctor.setUserType(1);
                doctor.setTeams(false);
                Doctor target = addXNDoctor(doctor);

                // 保存医生职业信息
                e.setDoctorId(target.getDoctorId());
                e.setOrganId(target.getOrgan());
                e.setDepartment(d.getDeptId());
                // 新增默认值
                e.setPrimaryOrgan(true);
                e.setApplyTransferEnable(false);
                e.setClinicEnable(false);
                e.setConsultationEnable(false);
                e.setConsultEnable(false);
                e.setTransferEnable(true);
                addEmployment(e);

                // 设置consultSet个人设置表为除转诊接收打开，其他全关闭
                ConsultSet set = new ConsultSet();
                set.setDoctorId(target.getDoctorId());
                set.setOnLineStatus(0);
                set.setAppointStatus(0);
                set.setTransferStatus(1);
                set.setMeetClinicStatus(0);
                set.setPatientTransferStatus(0);
                ConsultSetDAO setDao = DAOFactory.getDAO(ConsultSetDAO.class);
                setDao.save(set);

                setResult(true);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);

        return action.getResult();
    }

    /**
     * 保存一个医生
     *
     * @param doctor
     * @param e
     * @param code
     * @return
     */
    @RpcService
    public Boolean saveOneDoctor(final Doctor doctor, final Employment e, final String code) {
        AbstractHibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {

                logger.info("==========rpc医生信息=========" + JSONUtils.toString(doctor));
                logger.info("==========rpc医生所在部门信息====" + JSONUtils.toString(e));
                logger.info("==========rpc医生部门编号=======" + JSONUtils.toString(code));

                DepartmentDAO depart = DAOFactory.getDAO(DepartmentDAO.class);
                Department d = depart.getByCodeAndOrgan(code, doctor.getOrgan());

                // 保存医生信息
                doctor.setProfession(d.getProfessionCode());
                doctor.setUserType(1);//1医生 2护士  3技师 4服务人员  9其他
                doctor.setTeams(false);//团队标志
                Doctor target = addDoctor(doctor);

                // 保存医生职业信息
                e.setDoctorId(target.getDoctorId());//医生内码
                e.setOrganId(target.getOrgan());//执业机构代码
                e.setDepartment(d.getDeptId());
                // 新增默认值
                e.setPrimaryOrgan(true);
                e.setApplyTransferEnable(true);//是否允许转诊申请
                e.setClinicEnable(true);//诊疗权限
                e.setConsultationEnable(true);//会诊权限
                e.setConsultEnable(false);//咨询权限
                e.setTransferEnable(true);//接收转诊权限
                Employment eee = addEmployment(e);//职业持医生数据持久

                ConsultSet set = new ConsultSet();
                set.setDoctorId(target.getDoctorId());
                set.setOnLineStatus(0);
                set.setAppointStatus(0);
                set.setTransferStatus(0);
                set.setMeetClinicStatus(0);
                set.setPatientTransferStatus(0);
                ConsultSetDAO setDao = DAOFactory.getDAO(ConsultSetDAO.class);
                setDao.save(set);

                // 保存医生开户信息
                createDoctorUser(target, eee, target.getIdNumber().substring(target.getIdNumber().length() - 6));
                setResult(true);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);

        return action.getResult();
    }

    /**
     * 保存医生信息
     *
     * @param d
     * @return
     * @author hyj
     */
    @RpcService
    public Doctor addDoctor(Doctor d) {
        logger.info("addDoctor="+JSONUtils.toString(d));
        convertIdcard(d.getIdNumber(), d);
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
        DoctorDAO dao = DAOFactory.getDAO(DoctorDAO.class);
        Doctor doctor = dao.getByMobile(d.getMobile());
        if (doctor != null) {
            throw new DAOException(609, "该医生已存在，请勿重复添加");
        }
        d.setCreateDt(new Date());
        d.setLastModify(new Date());
        d.setStatus(1);
        d.setOnline(1);
        d.setChief(0);
        d.setTestPersonnel(0);
        d.setVirtualDoctor(false);
        d.setHaveAppoint(0);
        d.setSource(0);// 0:后台导入，1：注册
        d.setRewardFlag(false);
        return dao.save(d);
        // return d;
    }

    /**
     * 保存虚拟医生信息
     *
     * @param d
     * @return
     * @author hyj
     */
    @RpcService
    public Doctor addXNDoctor(Doctor d) {
        logger.info("addXNDoctor="+JSONUtils.toString(d));
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
        DoctorDAO dao = DAOFactory.getDAO(DoctorDAO.class);
        d.setCreateDt(new Date());
        d.setLastModify(new Date());
        d.setStatus(1);
        d.setOnline(1);
        d.setHaveAppoint(0);
        d.setChief(0);
        d.setTestPersonnel(0);
        d.setVirtualDoctor(true);
        d.setSource(0);// 0:后台导入，1：注册
        d.setRewardFlag(false);
        return dao.save(d);
    }

    /**
     * 从身份证获取出生年月和性别
     *
     * @param idcard
     * @param d
     * @return
     * @author hyj
     */
    public Doctor convertIdcard(String idcard, Doctor d) {
        try {
            idcard = ChinaIDNumberUtil.convert15To18(idcard);
        } catch (ValidateException e1) {
            logger.error("convertIdcard() error : "+e1);
        }

        if (idcard.length() == 15) {
            int idcardsex = Integer
                    .parseInt(idcard.substring(idcard.length() - 1));
            /*
			 * if(idcardsex%2==0){ System.out.println("---------------女");
			 * }else{ System.out.println("---------------男"); }
			 */
            d.setGender(idcardsex % 2 == 0 ? "2" : "1");
            String idcardbirthday = "19" + idcard.substring(6, 8) + "-"
                    + idcard.substring(8, 10) + "-" + idcard.substring(10, 12);
            DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Date birthday = null;
            try {
                birthday = sdf.parse(idcardbirthday);
                d.setBirthDay(birthday);
            } catch (ParseException e) {
                logger.error("convertIdcard() error : "+e);
            }
        } else {
            int idcardsex = Integer.parseInt(idcard.substring(
                    idcard.length() - 2, idcard.length() - 1));
			/*
			 * if(idcardsex%2==0){ System.out.println("---------------女");
			 * }else{ System.out.println("---------------男"); }
			 */
            d.setGender(idcardsex % 2 == 0 ? "2" : "1");
            String idcardbirthday = idcard.substring(6, 10) + "-"
                    + idcard.substring(10, 12) + "-" + idcard.substring(12, 14);
            DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Date birthday = null;
            try {
                birthday = sdf.parse(idcardbirthday);
                d.setBirthDay(birthday);
            } catch (ParseException e) {
                logger.error("convertIdcard() error : "+e);
            }
        }

        d.setIdNumber(idcard.trim());

        return d;
    }

    /**
     * 医生执业信息新增服务
     *
     * @param e
     * @author hyj
     */
    @RpcService
    public Employment addEmployment(Employment e) {
        if (StringUtils.isEmpty(e.getJobNumber())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "jobNumber is required");
        }
        if (e.getDoctorId() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "doctorId is required");
        }
        if (e.getOrganId() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "organId is required");
        }
        if (e.getDepartment() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "department is required");
        }
        EmploymentDAO dao = DAOFactory.getDAO(EmploymentDAO.class);

        Employment employment_old = dao.getByDocAndOrAndDep(e.getDoctorId(),
                e.getOrganId(), e.getDepartment());

        if (employment_old != null) {
            // 多点执业后，如果改医生已存在执业点，不能再同一个部门
            if (e.getOrganId().equals(employment_old.getOrganId())
                    && e.getDepartment().equals(employment_old.getDepartment())) {
                throw new DAOException(609, "该医生已存在执业信息，请勿重复新增");
            }
            e.setPrimaryOrgan(false);
        }
        logger.info("addEmployment.employment="+JSONUtils.toString(e));
        return dao.save(e);
        // return e;
    }

    /**
     * 医生开户服务
     *
     * @param doctor
     * @param password
     * @throws DAOException
     * @author hyj
     */
    @SuppressWarnings("rawtypes")
    @RpcService
    public void createDoctorUser(final Doctor doctor, final Employment e,
                                 final String password) throws DAOException {
        if (doctor.getDoctorId() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "doctorId is required");
        }
        if (StringUtils.isEmpty(password)) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "password is required");
        }

        AbstractHibernateStatelessResultAction action = new AbstractHibernateStatelessResultAction() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {

                UserDAO userDao = DAOFactory.getDAO(UserDAO.class);
                UserRoleTokenDAO tokenDao = DAOFactory.getDAO(UserRoleTokenDAO.class);

                OrganDAO organdao = DAOFactory.getDAO(OrganDAO.class);
                Organ o = organdao.getByOrganId(doctor.getOrgan());

                User u = new User();
                u.setId(doctor.getMobile());
                u.setEmail(doctor.getEmail());
                u.setName(doctor.getName());
                u.setPlainPassword(password);
                u.setCreateDt(new Date());
                u.setStatus("1");

                UserRoleTokenEntity urt = new UserRoleTokenEntity();
                urt.setUserId(doctor.getMobile());
                urt.setRoleId("doctor");
                urt.setTenantId("eh");
                String manageUnit = o.getManageUnit().equals("") ? "eh" : o
                        .getManageUnit();
                urt.setManageUnit(manageUnit);

                // user表中不存在记录
                if (!userDao.exist(doctor.getMobile())) {

                    // 创建角色(user，userrole两张表插入数据)
                    userDao.createUser(u, urt);

                } else {
                    // user表中存在记录,角色表中不存在记录
                    Object object = tokenDao.getExist(doctor.getMobile(),
                            manageUnit, "doctor");
                    if (object == null) {
                        ConfigurableItemUpdater<User, UserRoleToken> up = (ConfigurableItemUpdater<User, UserRoleToken>) UserController
                                .instance().getUpdater();

                        urt.setProperty("doctor", doctor);

                        //System.out.println(JSONUtils.toString(doctor));
                        // e 可能不是第一执业点，但 登陆人一定是第一执业点
                        EmploymentDAO empDAO = DAOFactory.getDAO(EmploymentDAO.class);
                        // 获取第一执业点
                        Employment e1 = empDAO.getPrimaryEmpByDoctorId(e.getDoctorId());
                        urt.setProperty("employment", e1);
                        up.createItem(doctor.getMobile(), urt);

                        // userrole插入数据
                        // tokenDao.save(urt);
                    } else {
                        // user表中存在记录,角色表中存在记录
                        throw new DAOException(609, "该用户已存在，请勿重复开户");
                    }
                }
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
    }

    /**
     * 医生注册服务
     *
     * @param doctor 医生
     * @param employ 雇员
     * @param code   部门编号
     * @return
     * @auther houxr
     * @date 2016-04-03 16:08:09
     */
    @RpcService
    public Boolean RegisteredDoctorAccount(final Doctor doctor, final Employment employ, final String code) {
        return saveOneDoctor(doctor, employ, code);
    }

    /**
     * 仅仅用于Excel导入签约患者数据校验
     *
     * @param doctor
     * @return
     */
    @RpcService
    public Boolean findByDoctorNameAndOrganId(Doctor doctor) {
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Doctor doctorTarget = doctorDAO.getByNameAndOrgan(doctor.getName(), doctor.getOrgan());
        if (doctorTarget == null) {
            logger.info("~~~签约医生未找到~~~:" + doctor.getName());
            return true;
        }
        return false;
    }

    /**
     * 仅仅用于Excel导入签约患者数据校验
     *
     * @param idCard
     * @return
     * @author houxr 2016-10-19 20:23:50
     */
    @RpcService
    public Boolean findPatientByIdCard(String idCard) {
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        Patient patientTarget = patientDAO.getByIdCard(idCard);
        if (patientTarget != null) {
            logger.info("~~~患者已存在~~~");
            return true;
        }
        return false;
    }


}
