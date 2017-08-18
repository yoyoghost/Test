package eh.op.service;

import ctd.account.user.UserController;
import ctd.controller.exception.ControllerException;
import ctd.mvc.weixin.entity.OAuthWeixinMP;
import ctd.mvc.weixin.entity.OAuthWeixinMPDAO;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.persistence.support.impl.user.UserDAO;
import ctd.util.annotation.RpcService;
import eh.base.constant.SystemConstant;
import eh.base.dao.UserRolesDAO;
import eh.base.service.BusActionLogService;
import eh.entity.base.UserRoles;
import eh.entity.mpi.Patient;
import eh.mpi.dao.PatientDAO;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.util.List;

/**
 * @author jianghc
 * @create 2017-02-08 17:12
 **/
public class UserOpService {

    @RpcService
    public void logoutWeChatUser(final String userId) {
        if (userId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "userId is require");
        }
        final PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        final UserRolesDAO userRolesDAO = DAOFactory.getDAO(UserRolesDAO.class);
        final List<UserRoles> urts = userRolesDAO.findUrtByUserId(userId);
        if (urts == null || urts.size() <= 0) {
            throw new DAOException("userId is not exist");
        }
        HibernateStatelessResultAction<String> action = new AbstractHibernateStatelessResultAction<String>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                UserRoles oldUrt = null;
                for (UserRoles urt : urts) {
                    if (urt.getRoleId().equals(SystemConstant.ROLES_PATIENT)) {
                        oldUrt = urt;
                    }
                }
                if (oldUrt == null) {
                    throw new DAOException("patient is not exist");
                }
                Patient patient = patientDAO.getByLoginId(userId);
                if(patient!=null) {
                    patient.setLoginId(null);
                    patientDAO.update(patient);
                }
                userRolesDAO.remove(oldUrt.getId());
                OAuthWeixinMPDAO oAuthWeixinMPDAO = DAOFactory.getDAO(OAuthWeixinMPDAO.class);
                List<OAuthWeixinMP> mps = oAuthWeixinMPDAO.findByUrt(oldUrt.getId());
                for (OAuthWeixinMP mp:mps){
                    oAuthWeixinMPDAO.remove(mp.getOauthId());
                }
                if(urts.size()==1){
                    UserDAO userDAO = DAOFactory.getDAO(UserDAO.class);
                    userDAO.remove(userId);
                }
                deleteFromThirdPartyMappingAndAliMPByUrt(oldUrt.getId());
                try {
                    UserController.instance().getUpdater().reload(userId);
                } catch (ControllerException e) {
                    throw new DAOException(500, "刷新用户缓存失败");
                }
                BusActionLogService.recordBusinessLog("微信用户管理", userId, "User", "注销账号："+userId);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);

    }


    private void deleteFromThirdPartyMappingAndAliMPByUrt(final int urt){
        AbstractHibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuffer sb = new StringBuffer("delete from ThirdPartyMapping where urt =:urt");
                StringBuffer sb2 = new StringBuffer("delete from OauthAliMP where urt =:urt");
                Query query = ss.createQuery(sb.toString());
                Query query2 = ss.createQuery(sb2.toString());
                query.setParameter("urt",urt);
                query2.setParameter("urt",urt);
                query.executeUpdate();
                query2.executeUpdate();
                setResult(true);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
    }






}
