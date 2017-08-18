package eh.base.logon;


import com.google.common.eventbus.Subscribe;
import ctd.access.AccessToken;
import ctd.access.AccessTokenController;
import ctd.account.Client;
import ctd.account.UserRoleToken;
import ctd.account.session.SessionItemManager;
import ctd.account.session.SessionKey;
import ctd.controller.exception.ControllerException;
import ctd.mvc.controller.support.LogonManager;
import ctd.mvc.logon.event.support.DefaultLogonEventListener;
import ctd.mvc.logon.event.support.LoginEvent;
import ctd.mvc.logon.event.support.LogoutEvent;
import ctd.persistence.DAOFactory;
import ctd.persistence.support.impl.access.AccessTokenDAO;
import ctd.util.AppContextHolder;
import ctd.util.ServletUtils;
import ctd.util.context.Context;
import ctd.util.context.ContextUtils;
import eh.base.constant.SystemConstant;
import eh.base.dao.DeviceDAO;
import eh.base.dao.DoctorDAO;
import eh.base.dao.LogonLogDAO;
import eh.bus.constant.CloudClinicSetConstant;
import eh.bus.service.CloudClinicSetService;
import eh.entity.base.Device;
import eh.entity.base.Doctor;
import eh.util.Easemob;
import org.apache.log4j.Logger;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * Created by sean on 15/10/9.
 */
public class EhLogonEventListener extends DefaultLogonEventListener {
    public static final Logger logger = Logger.getLogger(EhLogonEventListener.class);
    private long adminTokenExpiresSeconds = ServletUtils.ONE_HOUR_SECONDS;

    @Override
    @Subscribe
    public void onLogin(LoginEvent loginEvent) {
        super.onLogin(loginEvent);
        UserRoleToken ur = loginEvent.getUserRoleToken();
        if (ur == null || StringUtils.isEmpty(ur.getUserId())) {
            return;
        }

        // {{{ for client info
        Client client = (Client) ContextUtils.get(Context.CLIENT);
        if (client != null) {
            SessionItemManager.instance().updateSessionItem(SessionKey.of(Context.CLIENT).deviceSupport(true), client, client.getId());
            handlePcClient(client);    //	for PC single login
        }
        // }}}

        //用ons的方式记录登陆日志
        LogonLogDAO logonLogDao = DAOFactory.getDAO(LogonLogDAO.class);
        logonLogDao.recordLogonLog(ur);

        //IOS医生端由于SDK问题，4G环境下无法连接环信服务器进行注册,导致环信账户未注册的平台账户无法登录,
        // 解决方案为：在登录后调取环信注册服务进行注册
        if (SystemConstant.ROLES_PATIENT.equals(ur.getRoleId())) {
            Easemob.registUser(Easemob.getPatient(ur.getId()), SystemConstant.EASEMOB_PATIENT_PWD);
        }
        if (SystemConstant.ROLES_DOCTOR.equals(ur.getRoleId())) {
            Easemob.registUser(Easemob.getDoctor(ur.getId()), SystemConstant.EASEMOB_DOC_PWD);
        }
        if (SystemConstant.ROLES_ADMIN.equals(ur.getRoleId())) {
            AccessTokenDAO accessTokenDAO = DAOFactory.getDAO(AccessTokenDAO.class);
            List<AccessToken> ls = accessTokenDAO.findByUser(ur.getUserId(), ur.getId());
            if (ls != null && !ls.isEmpty()) {
                for (AccessToken ac : ls) {
                    ac.setExpiresIn(adminTokenExpiresSeconds);
                    try {
                        AccessTokenController.instance().getUpdater().update(ac);
                    } catch (ControllerException e) {
                        logger.error("onLogin() error: "+e);
                    }
                }
            }
        }
    }

    @Override
    @Subscribe
    public void onLogout(LogoutEvent logoutEvent) {
        super.onLogout(logoutEvent);
        UserRoleToken ur = logoutEvent.getUserRoleToken();
        if (ur == null || StringUtils.isEmpty(ur.getUserId())) {
            return;
        }
    }

    private void handlePcClient(Client client) {
        if (!"PC".equalsIgnoreCase(client.getOs())) {
            return;
        }
        HttpServletRequest request = (HttpServletRequest) ContextUtils.get(Context.HTTP_REQUEST);
        HttpServletResponse response = (HttpServletResponse) ContextUtils.get(Context.HTTP_RESPONSE);
        if (request == null || response == null) {
            return;
        }
        DeviceDAO deviceDAO = DAOFactory.getDAO(DeviceDAO.class);
        try {

            List<Device> devices = deviceDAO.findByUserIdAndUrtAndOs(client.getUserId(), client.getUrt(), client.getOs());
            if (devices != null && devices.size() > 1) {
                for (Device d : devices) {
                    AccessTokenController.instance().getUpdater().remove(d.getAccesstoken());
                }
                AccessToken accessToken = LogonManager.instance().makeAccessTokenLogon(request, response, client.getUserId(), client.getUrt());
            }
        } catch (ControllerException e) {
            logger.error("handlePcClient() error: "+e);
        }
    }
}
