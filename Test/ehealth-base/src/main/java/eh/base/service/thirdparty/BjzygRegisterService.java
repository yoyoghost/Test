package eh.base.service.thirdparty;

import ctd.account.UserRoleToken;
import ctd.account.thirdparty.ThirdPartyMapping;
import ctd.account.user.User;
import ctd.account.user.UserController;
import ctd.account.user.UserRoleTokenEntity;
import ctd.controller.exception.ControllerException;
import ctd.controller.updater.ConfigurableItemUpdater;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.impl.thirdparty.ThirdPartyMappingDao;
import ctd.persistence.support.impl.user.UserDAO;
import ctd.persistence.support.impl.user.UserRoleTokenDAO;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.dao.*;
import eh.base.user.AdminUserSevice;
import eh.base.user.RegisterDoctorSevice;
import eh.base.user.UserSevice;
import eh.bus.dao.ConsultSetDAO;
import eh.entity.base.Department;
import eh.entity.base.Doctor;
import eh.entity.base.Organ;
import eh.entity.base.UserRoles;
import eh.entity.bus.ConsultSet;
import org.hibernate.StatelessSession;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.text.DecimalFormat;
import java.util.Date;
import java.util.List;

/**
 * 北京中医馆相关注册服务
 * Created by wnw on 2016/11/3.
 */
public class BjzygRegisterService {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(OrganDAO.class);
    private static  final String APPKEY="bjzyg";
    /**
     * 北京中医馆用，机构和科室注册服务
     * @param o 机构信息
     * @param dList 科室列表
     * @return 错误消息
     */
    @RpcService
    public String registerOrganAndDepart(Organ o, List<Department> dList) {
        log.info("机构注册请求，organ[{}],DepartmentList[{}]", JSONUtils.toString(o),JSONUtils.toString(dList));
        DepartmentDAO departmentDAO = DAOFactory.getDAO(DepartmentDAO.class);
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        try {
            Organ existedOrgan= organDAO.getByOrganizeCode(o.getOrganizeCode());
            if(existedOrgan==null){//注册
                this.registOrganForBJ(o);
            }else{//更新
                o.setOrganId(existedOrgan.getOrganId());
                o.setManageUnit(existedOrgan.getManageUnit());
                organDAO.updateOrgan(o);
            }

        } catch (Exception e) {
            // TODO: handle exception
            String error = o.getName() + "机构代码为" + o.getOrganizeCode() + "" + "注册失败"+"，具体原因："+e.getMessage();
            log.error("registerOrganAndDepart()  error : "+error);

            return error;
        }
        for (Department d : dList) {
            try {
                d.setOrganId(o.getOrganId());
                Department existedDepart=departmentDAO.getByCodeAndOrgan(d.getCode(),o.getOrganId());
                if(existedDepart==null){//注册
                    departmentDAO.addDepartment(d);
                }
                else{//更新
                    d.setDeptId(existedDepart.getDeptId());
                    departmentDAO.updateDepartment(d);
                }
            } catch (Exception e) {
                // TODO: handle exception
                String error = o.getName() +  "机构代码为" + o.getOrganizeCode() + "" + "科室" + d.getName()
                        + "科室代码为" + d.getCode() + "注册失败"+"具体原因："+e.getMessage();
                log.error(error);
                return error;
            }
        }
        return "";
    }
    /**
     * 注册医院服务
     *
     * @author LF
     * @param o organ Info
     * @return
     */
    @RpcService
    public Organ registOrganForBJ(Organ o) {
        OrganDAO organDAO= DAOFactory.getDAO(OrganDAO.class);
        Organ organ = getManagerUnit(o);
        // 组织代码
        if (StringUtils.isEmpty(organ.getOrganizeCode())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "OrganizeCode is required!");
        }
        // 机构名称
        if (StringUtils.isEmpty(organ.getName())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "Name is required!");
        }
        // 机构简称
        if (StringUtils.isEmpty(organ.getShortName())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "ShortName is required!");
        }
        // 机构类型
        if (StringUtils.isEmpty(organ.getType())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "Type is required!");
        }
        // 机构等级
        if (StringUtils.isEmpty(organ.getGrade())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "Grade is required!");
        }
        // 属地区域
        if (StringUtils.isEmpty(organ.getAddrArea())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "AddrArea is required!");
        }
        // 机构层级编码
        if (StringUtils.isEmpty(organ.getManageUnit())) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "ManageUnit is required!");
        }
        Organ organ2 = new Organ();
        organ.setCreateDt(new Date());
        if (organ.getAccompanyFlag() == null) {
            organ.setAccompanyFlag(false);
        }
        if (organ.getAccompanyPrice() == null) {
            organ.setAccompanyPrice(0d);
        }
        organ2 = organDAO.save(organ);

        return organ2;
    }
    /**
     * 通过addrArea设定managerUnit
     *
     * @author xiebz
     * @param organ
     * @return organ
     */
    public Organ getManagerUnit(Organ organ) {
        OrganDAO organDAO= DAOFactory.getDAO(OrganDAO.class);
        String mangeUnit = null;
        Organ o = organDAO.getByOrganizeCode(organ.getOrganizeCode());
        if (o != null) {
            throw new DAOException(609, "组织代码为【" + organ.getOrganizeCode()
                    + "】的机构记录已经存在");
        } else {
            DecimalFormat f = new DecimalFormat("000");
            mangeUnit = "eh" + organ.getAddrArea();
            organ.setManageUnit(mangeUnit);
            //自动生成管理单元
            int i = 0;
            int length = 0;
            while(true){
                List<Organ> list = organDAO.findOrgansByManageUnit(organ, i);
                length = length + list.size();
                if(list.size()!=10){
                    break;
                }
                i = i + 10;
            }
            mangeUnit = "eh" + organ.getAddrArea() + f.format(length + 1);
            organ.setManageUnit(mangeUnit);
            return organ;
        }
    }
    /**
     * 中医馆医生注册
     * @param d 医生信息
     * @param userID 中医馆用户id
     * @param roleCode
     * @return
     */
    @RpcService
    public String registerDoctor(Doctor d, String userID, String roleCode) {
        log.info("注册中医馆医生信息，doctor,userId,roleCode "+JSONUtils.toString(d)+userID+"-"+roleCode);
        ThirdPartyMappingDao thirdPartyMappingDao = DAOFactory.getDAO(ThirdPartyMappingDao.class);
        DoctorDAO doctorDAO=DAOFactory.getDAO(DoctorDAO.class);
        ThirdPartyMapping thirdPartyMapping = thirdPartyMappingDao.getByThirdpartyAndTid(APPKEY, userID);
        AdminUserSevice sevice =AppContextHolder.getBean("adminUserSevice",AdminUserSevice.class);

        if(thirdPartyMapping==null){//未绑定则进行相关信息注册
            try {
                if(Integer.valueOf(roleCode) == 0){//省管理员,目前为超级管理员
                    this.createHighAdminUser(d.getMobile(), d.getName(), "111111", d.getOrgan());

                }else if(Integer.valueOf(roleCode) == 1){//中医馆管理员
                    sevice.createAdminUser(d.getMobile(), d.getName(), "111111", d.getOrgan());

                }else {//医生
                    this.RegisteredDoctorAccountForBJ(d.getMobile(), d.getName(),
                            d.getIdNumber(), d.getOrgan(), d.getProfession(),
                            d.getProTitle(), d.getInvitationCode());
                }
                this.addOAuthThirdApp(d, userID,roleCode);
            } catch (Exception e) {
                // TODO: handle exception
                String error = d.getName() + "注册失败";
                log.error(error+e.getMessage());
                return error;
            }
        }else{//已绑定，则进行医生信息更新
            log.info("已绑定进行医生信息更新操作");
            Doctor existedDoctor= doctorDAO.getByMobile(thirdPartyMapping.getUserId());
            if(existedDoctor==null)return "";
            if(thirdPartyMapping.getUserId().equals(d.getMobile())){
                d.setDoctorId(existedDoctor.getDoctorId());
                doctorDAO.updateDoctorByDoctorId(d);
            }else{//更新医生手机号
                UserRoleTokenDAO tokenDao = DAOFactory.getDAO(UserRoleTokenDAO.class);
                List<UserRoleToken> urtList=tokenDao.findByUserId(d.getMobile());
                if(urtList.size()>0){
                    throw new DAOException(ErrorCode.SERVICE_ERROR,"该账户已存在，请输入其他手机号");
                }
                UserSevice service= AppContextHolder.getBean("userSevice",UserSevice.class);
                service.resetDoctorMobile(existedDoctor.getMobile(),d.getMobile());
                d.setDoctorId(existedDoctor.getDoctorId());
                doctorDAO.updateDoctorByDoctorId(d);
                thirdPartyMapping.setUserId(d.getMobile());
                thirdPartyMappingDao.update(thirdPartyMapping);
                log.error("更新了医生手机号");
            }


        }

        return "";
    }

    @RpcService
    public void RegisteredDoctorAccountForBJ(String mobile, String name,
                                             String IDCard, int organ, String profession, String proTitle,
                                             Integer invitationCode) {
        if (org.apache.commons.lang3.StringUtils.isEmpty(mobile)) {
            new DAOException(DAOException.VALUE_NEEDED, "mobile is required!");
        }
        if (org.apache.commons.lang3.StringUtils.isEmpty(name)) {
            new DAOException(DAOException.VALUE_NEEDED, "name is required!");
        }
        if (org.apache.commons.lang3.StringUtils.isEmpty(IDCard)) {
            new DAOException(DAOException.VALUE_NEEDED, "IDCard is required!");
        }
        if (org.apache.commons.lang3.StringUtils.isEmpty(profession)) {
            new DAOException(DAOException.VALUE_NEEDED,
                    "profession is required!");
        }
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
		/*
		 * if (StringUtils.isEmpty(proTitle)) { new
		 * DAOException(DAOException.VALUE_NEEDED, "proTitle is required!"); }
		 */
        DepartmentDAO deptDao = DAOFactory.getDAO(DepartmentDAO.class);
        EmploymentDAO empDao = DAOFactory.getDAO(EmploymentDAO.class);
        ConsultSetDAO csDao = DAOFactory.getDAO(ConsultSetDAO.class);
        UserDAO userDao = DAOFactory.getDAO(UserDAO.class);
        if (userDao.exist(mobile) && doctorDAO.getByMobile(mobile) != null) {
            //如果已存在改用户，则推送人员接口需要更新平台医生信息
            throw new DAOException(609, "该号码已注册，请直接登录");
        }
        String proText = ""; // 专科名称
        Department dept = null;// 科室信息
        try {
            proText = DictionaryController.instance()
                    .get("eh.base.dictionary.Profession").getText(profession);
        } catch (ControllerException e) {
            log.error("RegisteredDoctorAccountForBJ() error : "+e);
        }
        Doctor doc = doctorDAO.getByIdNumber(IDCard);// 根据身份证获取医生信息
        if (doc == null) {
            doc = doctorDAO.getByNameAndMobile(name, mobile);// 根据姓名和手机号获取医生信息
            //下面的逻辑不明确，暂时注释
           /* if (doc == null) {
                dept = deptDao.getByNameAndOrgan(proText, organ);
                if (dept != null) {
                    try {
                        List<Doctor> docs = doctorDAO.getByNameAndDeptId(name,
                                dept.getDeptId());// 根据姓名和科室信息获得医生信息
                        if (docs != null && docs.size() > 0) {
                            doc = docs.get(0);
                        }
                    } catch (Exception e) {
                        // TODO: handle exception
                        e.printStackTrace();
                    }
                }
            }*/
        }

        if (doc == null) {// 医生不存在
            Doctor oldDoctor = doctorDAO.getByMobile(mobile);
            if (oldDoctor != null) {// 若原数据库中包含该手机号码，则将原数据库中的手机好前面加上“CF”
                oldDoctor.setMobile("CF" + oldDoctor.getMobile());
                doctorDAO.update(oldDoctor);
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
            doc.setProTitle(proTitle);
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
            doc.setStatus(1);
            doc = doctorDAO.save(doc);
            int doctorId = doc.getDoctorId();// 医生ID
            empDao.RegisteredEmployment(doctorId, organ, deptId);// 更新机构信息
            if (!csDao.exist(doctorId)) {
                ConsultSet cs = new ConsultSet();
                cs.setDoctorId(doctorId);
                cs.setOnLineStatus(1);
                cs.setAppointStatus(1);
                cs.setTransferStatus(1);
                cs.setMeetClinicStatus(1);
                cs.setPatientTransferStatus(1);
                csDao.save(cs);
            }else{
                ConsultSet cs=csDao.getById(doctorId);
                //默认打开转诊会诊预约
                cs.setDoctorId(doctorId);
                cs.setAppointStatus(1);
                cs.setTransferStatus(1);
                cs.setMeetClinicStatus(1);
                cs.setPatientTransferStatus(1);
                csDao.update(cs);
            }
            String password = IDCard.substring(IDCard.length() - 6,
                    IDCard.length());
            doctorDAO.createDoctorUser(doctorId, password);

        } else {// 医生存在
            if (userDao.exist(doc.getMobile())) {// 数据库中已有医生已经开户
                throw new DAOException(600, "" + doc.getMobile());
            } else {// 未开户
                if (dept == null) {
                    dept = deptDao.saveDeptByProfessionAndOrgan(profession,
                            proText, organ);
                }
                Doctor oldDoctor = doctorDAO.getByMobile(mobile);
                if (oldDoctor != null) {// 若原数据库中包含该手机号码，则将原数据库中的手机好前面加上“CF”
                    oldDoctor.setMobile("CF" + oldDoctor.getMobile());
                    doctorDAO.update(oldDoctor);
                }
                RegisterDoctorSevice rdSevice = new RegisterDoctorSevice();
                int doctorId = doc.getDoctorId();// 医生ID
                int deptId = dept.getDeptId();// 科室ID
                doc = rdSevice.convertIdcard(IDCard, doc);// 将身份证信息填充进去
                doc.setName(name);
                doc.setMobile(mobile);
                doc.setUserType(1);
                doc.setProfession(profession);
                doc.setProTitle(proTitle);
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
                doctorDAO.update(doc);// 更新医生基础信息
                empDao.RegisteredEmployment(doctorId, organ, deptId);// 更新机构信息
                if (!csDao.exist(doctorId)) {
                    ConsultSet cs = new ConsultSet();
                    cs.setDoctorId(doctorId);
                    cs.setOnLineStatus(0);
                    cs.setAppointStatus(0);
                    cs.setTransferStatus(0);
                    cs.setMeetClinicStatus(0);
                    csDao.save(cs);
                }
                String password = IDCard.substring(IDCard.length() - 6,
                        IDCard.length());
                doctorDAO.createDoctorUser(doctorId, password);

            }

        }

    }
    @RpcService
    @SuppressWarnings("unchecked")
    public String createHighAdminUser(final String userId, final String name,
                                      final String password, final int organId)
            throws ControllerException {

        if (org.apache.commons.lang3.StringUtils.isEmpty(userId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "登录账户不能为空");
        }

        if (org.apache.commons.lang3.StringUtils.isEmpty(name)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "姓名不能为空");
        }

        if (org.apache.commons.lang3.StringUtils.isEmpty(password)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "密码不能为空");
        }

        final OrganDAO organDao = DAOFactory.getDAO(OrganDAO.class);

        if (!organDao.exist(organId)) {
            throw new DAOException(602, "不存在这个机构");
        }

        AbstractHibernateStatelessResultAction<String> action = new AbstractHibernateStatelessResultAction<String>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {

                UserDAO userDao = DAOFactory.getDAO(UserDAO.class);
                UserRoleTokenDAO tokenDao = DAOFactory
                        .getDAO(UserRoleTokenDAO.class);

                // 获取管理员管理机构
                String ManageUnit = organDao.getByOrganId(organId)
                        .getManageUnit();

                User user = new User();
                user.setId(userId);
                user.setPlainPassword(password);
                user.setName(name);
                user.setCreateDt(new Date());
                user.setStatus("1");

                UserRoleTokenEntity urt = new UserRoleTokenEntity();
                urt.setUserId(user.getId());
                urt.setRoleId("admin");
                urt.setTenantId("eh");
                String m ="eh";// ManageUnit.substring(0, 5);//省级管理员默认超级管理员权限

                urt.setManageUnit(m);

                // user表中不存在记录
                if (!userDao.exist(userId)) {

                    // 创建角色(user，userrole两张表插入数据)
                    userDao.createUser(user, urt);

                } else {
                    // user表中存在记录,角色表中不存在记录
                    Object object = tokenDao.getExist(userId,m,"admin");
                    if (object == null) {

                        // userrole插入数据
                        ConfigurableItemUpdater<User, UserRoleToken> up = (ConfigurableItemUpdater<User, UserRoleToken>) UserController
                                .instance().getUpdater();
                        up.createItem(user.getId(), urt);

                    } else {
                        // user表中存在记录,角色表中存在记录
                        throw new DAOException(602, "该用户已注册过");
                    }
                }
                setResult(user.getId());
            }

        };
        HibernateSessionTemplate.instance().executeTrans(action);

        return action.getResult();
    }
    public void addOAuthThirdApp(Doctor d, String userID,String roleCode){
        UserRolesDAO urDAO = DAOFactory.getDAO(UserRolesDAO.class);
        ThirdPartyMappingDao thirdPartyMappingDao = DAOFactory.getDAO(ThirdPartyMappingDao.class);
        UserRoles ur = new UserRoles();
        if(roleCode.equals("0")||roleCode.equals("1")){
            ur = urDAO.getByUserIdAndRoleId(d.getMobile(), "admin");
        }else {
            ur = urDAO.getByUserIdAndRoleId(d.getMobile(), "doctor");
        }
        int urt = ur.getId();
        ThirdPartyMapping mapping=thirdPartyMappingDao.getByThirdpartyAndTid(APPKEY, userID);

        if(!StringUtils.isEmpty(mapping)){
            throw new DAOException("该userID已经存在");
        }else{
            ThirdPartyMapping o=new ThirdPartyMapping();
            o.setUserId(d.getMobile());
            o.setUrt(urt);
            o.setThirdparty("bjzyy");
            o.setTid(userID);
            thirdPartyMappingDao.save(o);
        }
    }


}
