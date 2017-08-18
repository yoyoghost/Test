package eh.base.user;

import ctd.access.AccessToken;
import ctd.account.AccountCenter;
import ctd.account.UserRoleToken;
import ctd.account.user.PasswordUtils;
import ctd.account.user.User;
import ctd.account.user.UserController;
import ctd.account.user.UserRoleTokenEntity;
import ctd.controller.exception.ControllerException;
import ctd.controller.updater.ConfigurableItemUpdater;
import ctd.mvc.weixin.entity.OAuthWeixinMP;
import ctd.mvc.weixin.entity.OAuthWeixinMPDAO;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.persistence.support.impl.access.AccessTokenDAO;
import ctd.persistence.support.impl.user.UserDAO;
import ctd.persistence.support.impl.user.UserRoleTokenDAO;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.constant.SmsConstant;
import eh.base.constant.SystemConstant;
import eh.base.dao.ChemistDAO;
import eh.base.dao.DeviceDAO;
import eh.base.dao.DoctorDAO;
import eh.base.dao.OrganDAO;
import eh.base.service.BusActionLogService;
import eh.entity.base.*;
import eh.entity.mpi.Patient;
import eh.entity.msg.SmsInfo;
import eh.mpi.dao.PatientDAO;
import eh.push.SmsPushService;
import eh.remote.IWXServiceInterface;
import eh.util.AlidayuSms;
import eh.util.ControllerUtil;
import eh.util.Easemob;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserSevice {
    public static final Logger logger = Logger.getLogger(UserSevice.class);

    /**
     * 获取urtId
     *
     * @param idList 医生主键/病人主键
     * @param roleId patient或者doctor
     * @return
     * @throws DAOException
     * @author ZX
     * @date 2015-6-2 下午1:49:31
     */
    @RpcService
    public List<DoctorOrPatientAndUrt> getUrtId(final List<Object> idList,
                                                final String roleId) throws DAOException {

        List<DoctorOrPatientAndUrt> hashList = new ArrayList<DoctorOrPatientAndUrt>();

        DoctorDAO docDao = DAOFactory.getDAO(DoctorDAO.class);
        PatientDAO patientDao = DAOFactory.getDAO(PatientDAO.class);

        // 根据主键号获取相应医生的手机号
        if (roleId.toLowerCase().trim().equals("doctor")) {
            for (Object object : idList) {
                int id = Integer.parseInt(object.toString());
                if (!docDao.exist(id)) {
                    continue;
                }
                Doctor doctor = docDao.getByDoctorId(id);
                int urtId = getUrtIdByUserId(doctor.getMobile(), "doctor");

                DoctorOrPatientAndUrt dau = new DoctorOrPatientAndUrt();
                dau.setId(id);
                dau.setUserName(doctor.getName());
                dau.setUrtId(urtId);

                hashList.add(dau);
            }
        }

        // 根据主键号获取相应患者的手机号
        if (roleId.toLowerCase().trim().equals("patient")) {
            for (Object object : idList) {
                String mpiId = object.toString();
                if (!patientDao.exist(mpiId)) {
                    continue;
                }
                Patient patient = patientDao.getByMpiId(mpiId);
                int urtId = getUrtIdByUserId(patient.getLoginId(), "patient");

                DoctorOrPatientAndUrt dau = new DoctorOrPatientAndUrt();
                dau.setId(mpiId);
                dau.setUserName(patient.getPatientName());
                dau.setUrtId(urtId);

                hashList.add(dau);
            }
        }
        return hashList;
    }

    /**
     * 根据用户名获取urtId
     *
     * @param mobile
     * @param roleId
     * @return
     * @author ZX
     * @date 2015-6-15 下午4:37:40
     */
    @RpcService
    public int getUrtIdByUserId(String userId, String roleId){
        if (StringUtils.isEmpty(userId)) {
            return -1;
        }
        UserDAO userDao = DAOFactory.getDAO(UserDAO.class);
        User user = userDao.get(userId);
        if (user == null) {
            return -1;
        }
        List<UserRoleToken> roleList = user.findUserRoleTokenByRoleId(roleId);
        if (roleList.size() <= 0) {
            return -1;
        }

        // 获取urtId
        UserRoleToken toke = roleList.get(0);
        return toke.getId();
    }

//    /**
//     * 根据用户名获取urtId
//     *
//     * @param mobile
//     * @param roleId
//     * @return
//     * @author ZX
//     * @date 2015-6-15 下午4:37:40
//     */
//    @RpcService
//    public int getUrtIdByMobile(String userId, String roleId) {
//       return getUrtIdByUserId(userId,roleId);
//    }

    /**
     * 根据医生主键获取urtid
     *
     * @param doctorId
     * @return
     * @author ZX
     * @date 2015-6-15 下午4:38:16
     * @date 2016-3-3 luf 修改异常code
     */
    public int getDoctorUrtIdByDoctorId(int doctorId) {
        DoctorDAO docDao = DAOFactory.getDAO(DoctorDAO.class);
        Doctor doc = docDao.get(doctorId);
        if (doc == null) {
            throw new DAOException(600, "不存在医生" + doctorId);
        }
        String mobile = doc.getMobile();
        int doctorUrtId = getUrtIdByUserId(mobile, "doctor");
        return doctorUrtId;
    }

    /**
     * 获取UserRoleToken信息
     *
     * @param userId
     * @param manageUnit
     * @param roleId
     * @return
     */
    @RpcService
    public UserRoleToken getUrtRoleToken(String userId, String manageUnit,
                                         String roleId) {
        if (userId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "userId is required");
        }
        if (roleId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "roleId is required");
        }
        if (StringUtils.isEmpty(manageUnit)) {
            manageUnit = "eh";
        }
        UserRoleTokenDAO tokenDao = DAOFactory.getDAO(UserRoleTokenDAO.class);
        return tokenDao.getExist(userId, manageUnit, roleId);
    }

    /**
     * 获取医生的编号，管理机构，角色获取医生信息和UserRoleToken信息
     *
     * @param doctorId
     * @param roleId
     * @return
     */
    @RpcService
    public DoctorAndUrt getDoctorAndUrt(Integer doctorId, String roleId) {
        if (doctorId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "doctorId is required");
        }
        if (roleId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "roleId is required");
        }
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Doctor doctor = doctorDAO.getByDoctorId(doctorId);
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        Organ organ = organDAO.getByOrganId(doctor.getOrgan());
        UserRoleTokenDAO tokenDao = DAOFactory.getDAO(UserRoleTokenDAO.class);
        UserRoleToken urToken = tokenDao.getExist(doctor.getMobile(),
                organ.getManageUnit(), roleId);
        DoctorAndUrt dau = new DoctorAndUrt(doctor, urToken);
        return dau;
    }

    /**
     * 修改密码(传入非MD5密码--提供给只提供给开发人员用)
     *
     * @param uid
     * @param newpwd
     * @author ZX
     * @date 2015-8-11 下午6:48:21
     */
    @RpcService
    public void resertPassword(String uid, String newpwd) {
        String md5Pwd = DigestUtils.md5Hex(newpwd);
        try {
            UserController
                    .instance()
                    .getUpdater()
                    .setProperty(uid, "password",
                            PasswordUtils.encodeFromMD5(md5Pwd, uid));
        } catch (ControllerException e) {
            throw new DAOException(DAOException.EVAL_FALIED, "密码修改失败");
        }
    }

    /**
     * 修改密码(传入MD5密码--提供给外部)
     *
     * @param phone
     * @param newpwd
     * @author ZX
     * @date 2015-8-11 下午7:47:43
     */
    @RpcService
    public void resertPasswordWithMd5(String phone, String newpwd) {
        User user = null;
        try {
            user = AccountCenter.getUser(phone);
        } catch (ControllerException e) {
            throw new DAOException(DAOException.EVAL_FALIED, "获取用户失败");
        }
        if (user == null) {
            throw new DAOException(DAOException.VALIDATE_FALIED, "用户不存在");
        }
        String uid = user.getId();

        try {
            UserController
                    .instance()
                    .getUpdater()
                    .setProperty(uid, "password",
                            PasswordUtils.encodeFromMD5(newpwd, uid));
        } catch (ControllerException e) {
            throw new DAOException(DAOException.EVAL_FALIED, "密码修改失败");
        }
    }

    /**
     * 判断是否已经有账户了[运营平台]
     *
     * @param uid 用户名
     * @return true:已有账户;false:没有账户
     */
    @RpcService
    public Boolean hasUser(String uid) {
        if (StringUtils.isEmpty(uid)) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "用户名不能为空");
        }
        UserRoleTokenDAO tokenDao = DAOFactory.getDAO(UserRoleTokenDAO.class);

        Boolean hasuser = false;
        List<UserRoleToken> urtList = tokenDao.findByUserId(uid);
        if (urtList.size() > 0) {
            hasuser = true;
        }
        return hasuser;
    }


    /**
     * 判断是否已经有账户了[运营平台]
     *
     * @param uid    用户名
     * @param roleId 用户角色:doctor|admin|chemist|patient|druggist
     * @return true:已有账户;false:没有账户
     */
    @RpcService
    public Boolean hasUserRole(String uid, String roleId) {
        if (StringUtils.isEmpty(uid)) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "用户名不能为空");
        }
        UserRoleTokenDAO tokenDao = DAOFactory.getDAO(UserRoleTokenDAO.class);
        List<UserRoleToken> urtList = tokenDao.findByUserId(uid);
        for (UserRoleToken userRoleToken : urtList) {
            if (userRoleToken.getRoleId().equals(roleId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 用户角色查询[运营平台]
     *
     * @param uid 用户名
     * @return List<UserRoleToken>
     */
    @RpcService
    public List<UserRoleToken> findUserRoleByUid(String uid) {
        if (StringUtils.isEmpty(uid)) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "用户名不能为空");
        }
        UserRoleTokenDAO tokenDao = DAOFactory.getDAO(UserRoleTokenDAO.class);
        List<UserRoleToken> urtList = tokenDao.findByUserId(uid);
        return urtList;
    }

    /**
     * 修改登录名[运营平台]
     *
     * @param oldUid 旧的登录名
     * @param newUid 新的登录名
     * @author ZX
     * @date 2015-8-12 下午9:27:46
     */
    @RpcService
    public void resertUid(final String oldUid, final String newUid) {
        BusActionLogService.recordBusinessLog("医生信息修改", "oldUid:" + oldUid + ",newUid:" + newUid, "UserService",
                "医生登录名从[" + oldUid + "]修改为[" + newUid + "]");
        if (StringUtils.isEmpty(oldUid) || StringUtils.isEmpty(newUid)) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "用户名不能为空");
        }

        UserRoleTokenDAO tokenDao = DAOFactory.getDAO(UserRoleTokenDAO.class);
        AccessTokenDAO accessDao = DAOFactory.getDAO(AccessTokenDAO.class);

        List<UserRoleToken> newUrtList = tokenDao.findByUserId(oldUid);
        List<UserRoleToken> urtList = tokenDao.findByUserId(newUid);
        if (urtList.size() > 0) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该账户已存在，请输入其他手机号");
        }

        //修改用户名，医生账户手机号，患者账户手机号，并重置密码为医生端身份证后6位
        HashMap<String, Object> map = this.updateUid(oldUid, newUid);
        logger.info("user[" + oldUid + "]已更新为user[" + newUid + "]");

        //重新加载缓存
        for (UserRoleToken urt : newUrtList) {
            try {
                AccessToken accessToken = accessDao.getByUser(oldUid, urt.getId());
                if (accessToken != null) {
                    ControllerUtil.removeAccessTokenById(accessToken.getId());
                    ControllerUtil.reloadAccessTokenById(accessToken.getId());
                }
                logger.info("刷新user[" + oldUid + "]的token值");
            } catch (Exception e) {
                logger.error(e.getMessage());
            }
        }

        //关闭微信端session
        String openId = (String) (map.get("openId"));
        if (!StringUtils.isEmpty(openId)) {
            IWXServiceInterface wxService = AppContextHolder.getBean(
                    "eh.wxService", IWXServiceInterface.class);
            wxService.invalideSession(openId);
            logger.info("关闭user[" + oldUid + "]的session");
        }

        try {
            ControllerUtil.reloadUserByUid(oldUid);
        } catch (Exception e) {
            logger.error(e.getMessage());
        }

        //发送短信
        // 短信模板：${successinfo}，登录密码重置为${pwd}，为确保账户安全，请${}
        // 短信例子：您的账户名已修改成功，登录密码重置为身份证后6位，为确保账户安全，请立即登录纳里医生APP修改登录密码
   /*     String templateCode = SmsConstant.PWD_RESERT;
        String successinfo = "您的账户名已修改成功";
        String pwd = "：" + SystemConstant.DEFAULT_PWD;
        String todo = "立即登录纳里医生APP修改登录密码。";
        HashMap<String, String> smsParam = new HashMap<String, String>();
        smsParam.put("successinfo", successinfo);
        smsParam.put("pwd", pwd);
        smsParam.put("todo", todo);
        AlidayuSms.sendSms(SystemConstant.DOCTOR_APP_NAME, newUid, templateCode, smsParam);*/

        this.sendMsgForResetPwd(newUid);


        //强制医生账户下线
        String rolesId = map.get("docUrtId").toString();
        String IMUserName = Easemob.getDoctor(Integer.parseInt(rolesId));
        Easemob.disconnect(IMUserName);
        logger.info("强制环信账户下线[IMUserName]:" + IMUserName + ";[rolesId]:" + rolesId);
    }

    private void sendMsgForResetPwd(String mobile) {
        SmsInfo info = new SmsInfo();
        info.setBusId(0);
        info.setStatus(0);
        info.setOrganId(0);
        info.setBusType("PasswordResert");
        info.setSmsType("PasswordResert");
        Map<String,Object> smsMap = new HashMap<String, Object>();
       // smsMap.put("pwd",SystemConstant.DEFAULT_PWD);
        smsMap.put("mobile",mobile);
        info.setExtendWithoutPersist(JSONUtils.toString(smsMap));//目标手机号
        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
        smsPushService.pushMsgData2OnsExtendValue(info);
    }

    /**
     * 修改所有跟登录名相关联的数据，并重置密码
     *
     * @param oldUid
     * @param newUid
     * @return
     */
    private HashMap<String, Object> updateUid(final String oldUid, final String newUid) {
        HibernateStatelessResultAction<HashMap<String, Object>> action = new AbstractHibernateStatelessResultAction<HashMap<String, Object>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                HashMap<String, Object> map = new HashMap<String, Object>();

                UserRoleTokenDAO tokenDao = DAOFactory.getDAO(UserRoleTokenDAO.class);
                PatientDAO patientDao = DAOFactory.getDAO(PatientDAO.class);
                DoctorDAO docDao = DAOFactory.getDAO(DoctorDAO.class);
                ChemistDAO cDAO = DAOFactory.getDAO(ChemistDAO.class);
                OrganDAO organdao = DAOFactory.getDAO(OrganDAO.class);
                DeviceDAO deviceDao = DAOFactory.getDAO(DeviceDAO.class);
                OAuthWeixinMPDAO mpDao = (OAuthWeixinMPDAO) DAOFactory.getDAO(OAuthWeixinMPDAO.class);

                // 患者
                UserRoleTokenEntity object = (UserRoleTokenEntity) tokenDao.getExist(oldUid, "eh", "patient");

                Patient pat = patientDao.getByLoginId(oldUid);
                if (object != null && pat != null) {

                    String pathql = new String(
                            "update Patient set loginId=:id,mobile=:id where mpiId=:mpiId");
                    Query patq = ss.createQuery(pathql);
                    patq.setParameter("id", newUid);
                    patq.setParameter("mpiId", pat.getMpiId());
                    patq.executeUpdate();

                    //微信关联表
                    OAuthWeixinMP mp = mpDao.getByUrt(object.getId());
                    String openId = new String();
                    if (mp != null) {
                        Integer oauthId = Integer.valueOf(mp.getOauthId());
                        map.put("openId", mp.getOpenId());
                        mpDao.remove(oauthId);
                    }

                }

                // 医生
                Doctor doc = docDao.getByMobile(oldUid);
                if (doc != null) {
                    Integer organId = doc.getOrgan();
                    if (organId != null) {
                        Organ o = organdao.getByOrganId(organId);
                        UserRoleTokenEntity urt = (UserRoleTokenEntity) tokenDao
                                .getExist(oldUid, o.getManageUnit(), "doctor");
                        if (urt != null) {
                            map.put("docUrtId", urt.getId());

                            String dochql = new String(
                                    "update Doctor set mobile=:mobile where doctorId=:doctorId");
                            Query docq = ss.createQuery(dochql);
                            docq.setParameter("mobile", newUid);
                            docq.setParameter("doctorId", doc.getDoctorId());
                            docq.executeUpdate();
                        }
                    }
                }

                /**
                 * zhongzx 加
                 * 修改 药师用户名 手机号没有跟着改
                 */
                Chemist chemist = cDAO.getByLoginId(oldUid);
                if (null != chemist) {
                    Integer organId = chemist.getOrgan();
                    if (organId != null) {
                        Organ o = organdao.getByOrganId(organId);
                        UserRoleTokenEntity urt = (UserRoleTokenEntity) tokenDao
                                .getExist(oldUid, o.getManageUnit(), "chemist");
                        if (urt != null) {
                            String chehql = new String(
                                    "update Chemist set loginId=:loginId where chemistId=:chemistId");
                            Query cheq = ss.createQuery(chehql);
                            cheq.setParameter("loginId", newUid);
                            cheq.setParameter("chemistId", chemist.getChemistId());
                            cheq.executeUpdate();
                        }
                    }
                }

                // user表
                String newpwd = SystemConstant.DEFAULT_PWD;
                String md5Pwd = DigestUtils.md5Hex(newpwd);

                String userhql = new String(
                        "update User set id=:id,password=:pwd where id=:oldId");
                Query userq = ss.createQuery(userhql);
                userq.setParameter("id", newUid);
                userq.setParameter("pwd", PasswordUtils.encodeFromMD5(md5Pwd, newUid));
                userq.setParameter("oldId", oldUid);
                userq.executeUpdate();

                // 角色表
                String roleshql = new String(
                        "update UserRoleTokenEntity set userId=:id where userId=:oldId");
                Query rolesq = ss.createQuery(roleshql);
                rolesq.setParameter("id", newUid);
                rolesq.setParameter("oldId", oldUid);
                rolesq.executeUpdate();

                //设备表
                String devicehql = new String(
                        "update Device set userId=:id where userId=:oldId");
                Query deviceq = ss.createQuery(devicehql);
                deviceq.setParameter("id", newUid);
                deviceq.setParameter("oldId", oldUid);
                deviceq.executeUpdate();

                setResult(map);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);

        return action.getResult();
    }

    /**
     * 刷新特定用户缓存
     *
     * @param uid
     * @author ZX
     * @date 2015-8-12 下午9:29:34
     */
    @RpcService
    public void reload(String uid) throws ControllerException {
        ControllerUtil.reloadUserByUid(uid);
    }

    /**
     * 刷新缓存
     *
     * @author ZX
     * @date 2015-8-12 下午9:29:34
     */
    @RpcService
    public void reloadAll() {
        ControllerUtil.reloadAllUser();
    }

    /**
     * 验证密码
     *
     * @param uid 登录用户名
     * @param pwd md5加密过的密码
     * @return true:密码正确；false:秘密码错误
     * @throws ControllerException
     * @author zhangx
     * @date 2015-12-3 下午8:43:41
     */
    @RpcService
    public Boolean checkPwd(String uid, String pwd) throws ControllerException {
        User user = AccountCenter.getUser(uid);

        int len = pwd.length();
        if (len == 32) {
            return user.validateMD5Password(pwd);
        }
        if (len == 64) {
            return user.validatePassword(pwd);
        }

        return false;
    }


    /**
     * 刷新服务器用户信息缓存
     *
     * @param userId       用户名
     * @param roles        roles 角色(admin 管理员；doctor 医生;druggist 药商;patient 患者；thirdparty 第三方平台)
     * @param propertyName 要更新的属性名,为null 或 "" 则不更新属性(医生有属性-doctor,employment;患者有属性-OAuthWeixinMP,patient,WXApp)
     * @param Property     要更新的属性对象 为null不更新属性
     */
    public static void updateUserCache(String userId, String roles, String propertyName, Object Property) {
        logger.info("updateUserCache[" + userId + "],roles:[" + roles + "],propertyName:[" + propertyName + "],Property:[" + JSONUtils.toString(Property) + "]");
        // 刷新服务器缓存
        if (StringUtils.isEmpty(userId) || StringUtils.isEmpty(roles)) {
            return;
        }
        try {

            UserRoleTokenEntity ure=getAccountCenterUre(userId,roles);
            if(ure==null){
                logger.info("刷新服务器用户信息缓存-未获取到ure信息");
                return;
            }
            if (ure != null) {
                ConfigurableItemUpdater<User, UserRoleToken> up = (ConfigurableItemUpdater<User, UserRoleToken>) UserController
                        .instance().getUpdater();

                if (!StringUtils.isEmpty(userId) && Property != null) {
                    ure.setProperty(propertyName, Property);
                }

                up.updateItem(userId, ure);
                logger.info("刷新服务器用户信息缓存成功:"+JSONUtils.toString(ure));
            }
        } catch (Exception e) {
            logger.error("刷新用户信息异常：" + e.getMessage());
        }
    }

    /**
     * 以下情况下必须使用缓存AccountCenter中获取的Ure,
     * 否则ure和用户缓存体系脱离，是游离状态
     * 1.注册并登陆
     * 2.更新缓存
     * @param userId
     * @param roles
     * @return
     */
    public static UserRoleTokenEntity  getAccountCenterUre(String userId,String roles){
        UserRoleTokenEntity ure=null;
        try{
            User user = AccountCenter.getUser(userId);

            if (user != null) {
                List<UserRoleToken> urts = user.findUserRoleTokenByRoleId(roles);
                if (urts.size() > 0) {
                    ure = (UserRoleTokenEntity) urts.get(0);
                }
            }
        }catch (Exception e){
            logger.error("获取缓存失败:"+e.getMessage());
        }
        return ure;
    }


    /**
     * 药师登录用户名称修改[运营平台]
     * <p>
     * 作废
     *
     * @param oldUid 旧的登录名
     * @param newUid 新的登录名
     * @author houxr
     * @date 2016-7-28 下午9:27:46
     */
    //@RpcService
    @Deprecated
    public void resetChemistUid(final String oldUid, final String newUid) {
        if (StringUtils.isEmpty(newUid) || StringUtils.isEmpty(oldUid)) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "用户名不能为空");
        }
        UserRoleTokenDAO tokenDao = DAOFactory.getDAO(UserRoleTokenDAO.class);
        AccessTokenDAO accessDao = DAOFactory.getDAO(AccessTokenDAO.class);
        List<UserRoleToken> newUrtList = tokenDao.findByUserId(oldUid);
        List<UserRoleToken> urtList = tokenDao.findByUserId(newUid);
        if (urtList.size() > 0) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该账户已存在，请输入其他手机号");
        }

        //修改药师登录用户名，手机号，并重置密码为身份证后6位
        this.updateUid(oldUid, newUid);
        logger.info("user[" + oldUid + "]已更新为user[" + newUid + "]");

        //重新加载缓存
        for (UserRoleToken urt : newUrtList) {
            try {
                AccessToken accessToken = accessDao.getByUser(oldUid, urt.getId());
                if (accessToken != null) {
                    ControllerUtil.removeAccessTokenById(accessToken.getId());
                    ControllerUtil.reloadAccessTokenById(accessToken.getId());
                }
                logger.info("刷新ChemistUser[" + oldUid + "]的token值");
            } catch (Exception e) {
                logger.error(e.getMessage());
            }
        }
        try {
            ControllerUtil.reloadUserByUid(oldUid);
        } catch (Exception e) {
            logger.error(e.getMessage());
        }

        //发送短信
        // 短信模板：${successinfo}，登录密码重置为${pwd}，为确保账户安全，请${}
        // 短信例子：您的账户名已修改成功，登录密码重置为身份证后6位，为确保账户安全，请立即登录纳里医生APP修改登录密码
        String templateCode = SmsConstant.PWD_RESERT;
        String successinfo = "您的账户名已修改成功";
        String pwd = "：" + SystemConstant.DEFAULT_PWD;
        String todo = "立即登录药师平台修改登录密码";
        HashMap<String, String> smsParam = new HashMap<String, String>();
        smsParam.put("successinfo", successinfo);
        smsParam.put("pwd", pwd);
        smsParam.put("todo", todo);
        AlidayuSms.sendSms(SystemConstant.DOCTOR_APP_NAME, newUid, templateCode, smsParam);
    }

    /**
     * 重置医生手机号 -- wnw
     *
     * @param oldUid
     * @param newUid
     * @return
     */

    public HashMap<String, Object> resetDoctorMobile(String oldUid, String newUid) {
        return this.updateUid(oldUid, newUid);
    }
}