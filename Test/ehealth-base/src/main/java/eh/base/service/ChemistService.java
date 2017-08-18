package eh.base.service;

import ctd.account.AccountCenter;
import ctd.account.UserRoleToken;
import ctd.account.user.PasswordUtils;
import ctd.account.user.User;
import ctd.account.user.UserController;
import ctd.account.user.UserRoleTokenEntity;
import ctd.controller.exception.ControllerException;
import ctd.controller.notifier.NotifierCommands;
import ctd.controller.notifier.NotifierMessage;
import ctd.controller.updater.ConfigurableItemUpdater;
import ctd.dictionary.DictionaryController;
import ctd.dictionary.DictionaryItem;
import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.impl.user.UserDAO;
import ctd.persistence.support.impl.user.UserRoleTokenDAO;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.constant.SystemConstant;
import eh.base.dao.ChemistDAO;
import eh.base.dao.OrganDAO;
import eh.base.user.UserSevice;
import eh.entity.base.Chemist;
import eh.util.ChinaIDNumberUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.hibernate.StatelessSession;

import java.util.Date;
import java.util.List;

/**
 * Created by Administrator on 2016/5/26 0026.
 */
public class ChemistService {

    public static final Logger log = Logger.getLogger(ChemistService.class);


    /**
     * zhongzx
     * 保存药师信息
     */
    @RpcService
    public Chemist addChemist(Chemist c) {
        log.info(JSONUtils.toString(c));

        ChemistDAO cDao = DAOFactory.getDAO(ChemistDAO.class);

        if (null == c) {
            throw new DAOException(DAOException.VALUE_NEEDED, "chemist is needed");
        }
        if (StringUtils.isEmpty(c.getIdNumber())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "idNumber is needed");
        }
        //身份证验证
        try {
            c.setIdNumber(ChinaIDNumberUtil.convert15To18(c.getIdNumber()));
        } catch (Exception e) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "身份证不正确");
        }

        if (null != cDao.getByIdNumber(c.getIdNumber())) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该身份证已注册");
        }

        if (StringUtils.isEmpty(c.getName())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "name is needed");
        }
        if (null == c.getGender()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "gender is needed");
        }
        if (null == c.getBirthDay()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "birthDay is needed");
        }
        if (StringUtils.isEmpty(c.getMobile())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "mobile is needed");
        }
        //判断手机号是否已存在
        if (null != cDao.getByMobile(c.getMobile())) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该手机号已注册");
        }
        //新增时 登录名设为手机号
        c.setLoginId(c.getMobile());

        if (null == c.getOrgan()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organ is needed");
        }

        c.setStatus(1);
        c.setCreateDt(new Date());
        c.setLastModify(new Date());

        Chemist chemist = cDao.save(c);

        //刷新缓存
        Integer key = chemist.getChemistId();
        DictionaryItem item = cDao.getDictionaryItem(key);
        //this.fireEvent(new CreateDAOEvent(key,item));//此方法只通知主服务器 config缓存服务器没有通知到
        NotifierMessage msg = new NotifierMessage(NotifierCommands.ITEM_CREATE, "eh.base.dictionary.Chemist");
        msg.setLastModify(System.currentTimeMillis());
        msg.addUpdatedItems(item);
        try {
            DictionaryController.instance().getUpdater().notifyMessage(msg);
        } catch (ControllerException e) {
            log.error(e.getMessage());
        }
        new UserSevice().updateUserCache(c.getMobile(), SystemConstant.ROLES_CHEMIST, "chemist", c);
        return chemist;
    }

    /**
     * 药师开户服务
     * zhongzx
     *
     * @param chemistId
     * @return
     */
    @RpcService
    public String createChemistUser(final Integer chemistId) {

        final ChemistDAO cDao = DAOFactory.getDAO(ChemistDAO.class);
        final OrganDAO organDao = DAOFactory.getDAO(OrganDAO.class);

        if (null == chemistId) {
            throw new DAOException(DAOException.VALUE_NEEDED, "chemistId is needed");
        }
        final Chemist c = cDao.getByChemistId(chemistId);
        if (null == c) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "此药师不存在");
        }
        AbstractHibernateStatelessResultAction<String> action = new AbstractHibernateStatelessResultAction<String>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {

                UserDAO userDao = DAOFactory.getDAO(UserDAO.class);
                UserRoleTokenDAO tokenDao = DAOFactory
                        .getDAO(UserRoleTokenDAO.class);

                // 获取管理员管理机构
                String manageUnit = organDao.getByOrganId(c.getOrgan())
                        .getManageUnit();

                User user = new User();
                user.setId(c.getLoginId());
                user.setPlainPassword(c.getIdNumber().substring(c.getIdNumber().length() - 6));
                user.setName(c.getName());
                user.setCreateDt(new Date());
                user.setStatus("1");

                UserRoleTokenEntity urt = new UserRoleTokenEntity();
                urt.setUserId(user.getId());
                urt.setRoleId("chemist");
                urt.setTenantId("eh");
                urt.setManageUnit(manageUnit);

                // user表中不存在记录
                if (!userDao.exist(c.getLoginId())) {

                    // 创建角色(user，userrole两张表插入数据)
                    userDao.createUser(user, urt);

                } else {
                    // user表中存在记录,角色表中不存在记录
                    Object object = tokenDao.getExist(c.getLoginId(), manageUnit, "chemist");
                    if (object == null) {

                        // userrole插入数据
                        ConfigurableItemUpdater<User, UserRoleToken> up = (ConfigurableItemUpdater<User, UserRoleToken>) UserController
                                .instance().getUpdater();
                        //设置登录时能够获取到的属性
                        urt.setProperty("chemist", cDao.getByChemistId(chemistId));

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

        Integer key = c.getChemistId();
        DictionaryItem item = cDao.getDictionaryItem(key);
        //this.fireEvent(new CreateDAOEvent(key,item));//此方法只通知主服务器 config缓存服务器没有通知到
        NotifierMessage msg = new NotifierMessage(NotifierCommands.ITEM_CREATE, "eh.base.dictionary.Chemist");
        msg.setLastModify(System.currentTimeMillis());
        msg.addUpdatedItems(item);
        try {
            DictionaryController.instance().getUpdater().notifyMessage(msg);
        } catch (ControllerException e) {
            log.error(e.getMessage());
        }

        new UserSevice().updateUserCache(c.getMobile(), SystemConstant.ROLES_CHEMIST, "chemist", c);
        return action.getResult();

    }

    /**
     * 药师审核平台修改密码
     * zhongzx
     *
     * @param userId
     * @param oldPwd
     * @param newPwd
     * @param repPwd
     * @return
     */
    @RpcService
    public boolean changeChemistPwd(String userId, String oldPwd, String newPwd, String repPwd) {
        if (StringUtils.isEmpty(userId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "用户账户不能为空");
        }
        if (StringUtils.isEmpty(oldPwd)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "旧密码不能为空");
        }
        if (StringUtils.isEmpty(newPwd)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "新密码不能为空");
        }
        if (StringUtils.isEmpty(repPwd)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "重复密码不能为空");
        }
        User user = null;
        try {
            user = AccountCenter.getUser(userId);
        } catch (ControllerException e) {
            throw new DAOException(DAOException.EVAL_FALIED, "获取用户失败");
        }
        if (null == user) {
            throw new DAOException(DAOException.VALIDATE_FALIED, "该用户不存在");
        }
        UserSevice service = new UserSevice();
        //校验用户名和原密码
        try {
            boolean result = service.checkPwd(userId, oldPwd);
            if (!result) {
                throw new DAOException(DAOException.VALIDATE_FALIED, "原密码错误");
            }
        } catch (ControllerException e) {
            throw new DAOException(DAOException.EVAL_FALIED, "获取用户失败");
        }
        if (!newPwd.equals(repPwd)) {
            throw new DAOException(DAOException.VALIDATE_FALIED, "新密码和确认密码不一致");
        }
        //修改密码
        String uid = user.getId();
        try {
            UserController
                    .instance()
                    .getUpdater()
                    .setProperty(uid, "password",
                            PasswordUtils.encodeFromMD5(newPwd, uid));
        } catch (ControllerException e) {
            throw new DAOException(DAOException.EVAL_FALIED, "密码修改失败");
        }
        return true;
    }

    /**
     * zhongzx
     * 更新药师信息 同时刷新缓存
     *
     * @param chemist
     * @return
     */
    @RpcService
    public Chemist updateChemist(final Chemist chemist) {
        log.info(JSONUtils.toString(chemist));

        final ChemistDAO cDao = DAOFactory.getDAO(ChemistDAO.class);

        if (null == chemist.getChemistId()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "chemistId is needed");
        }
        Chemist target = cDao.getByChemistId(chemist.getChemistId());
        if (null == target) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "此药师不存在");
        }
        //查询除了自己外的 改手机号有没有被注册
        List<Chemist> list = cDao.findByMobile(chemist.getMobile(), chemist.getChemistId());
        if (null != list && 0 != list.size()) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该手机号已注册");
        }
        chemist.setLastModify(new Date());
        BeanUtils.map(chemist, target);
        Chemist c = cDao.update(target);

        Integer key = c.getChemistId();
        DictionaryItem item = cDao.getDictionaryItem(key);
        //this.fireEvent(new CreateDAOEvent(key,item));//此方法只通知主服务器 config缓存服务器没有通知到
        NotifierMessage msg = new NotifierMessage(NotifierCommands.ITEM_UPDATE, "eh.base.dictionary.Chemist");
        msg.setLastModify(System.currentTimeMillis());
        msg.addUpdatedItems(item);
        try {
            DictionaryController.instance().getUpdater().notifyMessage(msg);
        } catch (ControllerException e) {
            log.error(e.getMessage());
        }

        //刷新缓存
        new UserSevice().updateUserCache(c.getMobile(), SystemConstant.ROLES_CHEMIST, "chemist", c);
        return c;
    }

    /**
     * 运营平台药师查询
     *
     * @param keyword 关键字
     * @param status  状态
     * @param start
     * @param limit
     * @return
     */
    @RpcService
    public QueryResult<Chemist> queryChemistByKeywordAndStatus(String keyword, Integer status, int start, int limit) {
        final ChemistDAO dao = DAOFactory.getDAO(ChemistDAO.class);
        return dao.queryChemistByKeywordAndStatus(keyword, status, start, limit);
    }

    @RpcService
    public Chemist getChemistByChemistId(Integer chemistId) {
        ChemistDAO dao = DAOFactory.getDAO(ChemistDAO.class);
        return dao.getByChemistId(chemistId);
    }

    @RpcService
    public Chemist getChemistByMobile(String mobile) {
        ChemistDAO dao = DAOFactory.getDAO(ChemistDAO.class);
        return dao.getByMobile(mobile);
    }
    
    @RpcService
    public void updatePhotoByChemistId(int photo, Integer chemistId){
        DAOFactory.getDAO(ChemistDAO.class).updatePhotoByChemistId(photo, chemistId);
    }
}