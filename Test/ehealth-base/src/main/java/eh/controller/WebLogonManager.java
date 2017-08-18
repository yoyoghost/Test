package eh.controller;

import ctd.account.UserRoleToken;
import ctd.account.user.User;
import ctd.account.user.UserRoleTokenEntity;
import ctd.controller.exception.ControllerException;
import ctd.mvc.controller.OutputSupportMVCController;
import ctd.mvc.logon.event.LogonEventListener;
import ctd.mvc.logon.event.LogonEventManager;
import ctd.mvc.logon.event.support.LoginEvent;
import ctd.mvc.weixin.entity.OAuthWeixinMP;
import ctd.mvc.weixin.entity.OAuthWeixinMPDAO;
import ctd.net.rpc.json.JSONResponseBean;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.impl.user.UserDAO;
import ctd.persistence.support.impl.user.UserRoleTokenDAO;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.ServletUtils;
import ctd.util.codec.RSAUtils;
import ctd.util.context.ContextUtils;
import eh.bus.constant.ConsultConstant;
import eh.entity.mpi.Patient;
import eh.mpi.dao.PatientDAO;
import eh.op.redis.WebVerfyCode;
import eh.op.service.WebLoginInfoService;
import eh.wxpay.util.Util;
import eu.bitwalker.useragentutils.UserAgent;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * @author jianghc
 * @create 2017-06-13 14:19
 **/
@Controller("webLogonManager")
public class WebLogonManager extends OutputSupportMVCController {
    private static final Logger logger = LoggerFactory.getLogger(WebLogonManager.class);
    private final LogonEventManager logonEventManager = new LogonEventManager();
    private String referer;

    public static String logonAppId;

    public String getLogonAppId() {
        return logonAppId;
    }

    public void setLogonAppId(String logonAppId) {
        this.logonAppId = logonAppId;
    }


    public void setLogonEventListener(LogonEventListener ls) {
        this.logonEventManager.addListener(ls);
    }


    @RequestMapping(
            value = {"api/wechat/login"},
            method = {RequestMethod.POST}
    )
    public void logon(HttpServletRequest request, HttpServletResponse response) {
        if (this.checkReferer(request, response)) {
            JSONResponseBean res = new JSONResponseBean();
            try {
                Map<String, String> requestMap = Util.buildRequest(request);
                String ticket = requestMap.get("ticket");
                if (!StringUtils.isEmpty(ticket)) {
                    ticket = Integer.toHexString(ticket.hashCode());
                    WebLoginInfoService webLoginInfoService = AppContextHolder.getBean("eh.webLoginInfoService", WebLoginInfoService.class);
                    String openId = webLoginInfoService.getLoginInfo(ticket);
                    if (!StringUtils.isEmpty(openId)) {
                        webLoginInfoService.remove(ticket);
                        OAuthWeixinMPDAO oAuthWeixinMPDAO = DAOFactory.getDAO(OAuthWeixinMPDAO.class);
                        OAuthWeixinMP mp = oAuthWeixinMPDAO.getByAppIdAndOpenId(logonAppId, openId);
                        if (mp != null) {
                            UserRoleTokenDAO tokenDao = DAOFactory.getDAO(UserRoleTokenDAO.class);
                            UserRoleToken first = tokenDao.get(mp.getUrt());
                            if (first == null || !first.getRoleId().equals(ConsultConstant.MSG_ROLE_TYPE_PATIENT)) {
                                throw new ControllerException(404, "patient role[" + mp.getUrt() + "] not found.");
                            }
                            PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
                            Patient patient = patientDAO.getByLoginId(first.getUserId());
                            if (patient == null) {
                                throw new ControllerException(404, "patient loginId[" + first.getUserId() + "] not found.");
                            }
                            UserRoleTokenEntity entity = (UserRoleTokenEntity) first;
                            entity.setProperty(ConsultConstant.MSG_ROLE_TYPE_PATIENT, patient);
                            first = entity;
                            int urt = first.getId().intValue();
                            String uid = first.getUserId();
                            HttpSession httpSession = request.getSession(false);
                            if (httpSession == null || !uid.equals(httpSession.getAttribute("uid"))) {
                                httpSession = request.getSession();
                            }
                            httpSession.setAttribute("uid", uid);
                            httpSession.setAttribute("token", Integer.valueOf(urt));
                            res.setBody(first);
                            this.afterLogon(request, first);
                        } else {
                            res.setCode(200);
                            Map<String, String> map = new HashMap<String, String>();
                            map.put("openId", openId);
                            res.setBody(map);
                            boolean gzip1 = ServletUtils.isAcceptGzip(request);
                            this.jsonOutputAndClearCtx(response, res, gzip1);
                        }
                    } else {
                        res.setCode(501);
                        res.setMsg("openId is require");
                    }
                } else {
                    res.setCode(501);
                    res.setMsg("ticket is require");
                }
            } catch (ControllerException var17) {
                res.setCode(var17.getCode());
                res.setMsg(var17.getMessage());
                logger.error(var17.getMessage());
            } catch (Exception var18) {
                var18.printStackTrace();
                res.setCode(500);
                res.setMsg(var18.getMessage());
                logger.error(var18.getMessage());
            }

            boolean gzip1 = ServletUtils.isAcceptGzip(request);
            this.jsonOutputAndClearCtx(response, res, gzip1);
        }
    }

    private boolean checkReferer(HttpServletRequest request, HttpServletResponse response) {
        if (!StringUtils.isEmpty(this.referer)) {
            String refHeader = request.getHeader("Referer");
            if (!this.referer.equals(refHeader)) {
                response.setStatus(403);
                return false;
            }
        }

        return true;
    }

    private void jsonOutputAndClearCtx(HttpServletResponse response, JSONResponseBean res, boolean gzip) {
        try {
            this.jsonOutput(response, res, gzip);
        } catch (IOException var8) {
            response.setStatus(500);
            logger.error(var8.getMessage());
        } finally {
            ContextUtils.clear();
        }

    }

    public void afterLogon(HttpServletRequest request, UserRoleToken token) throws ControllerException {
        UserRoleTokenEntity ure = (UserRoleTokenEntity) token;
        String lastIp = ServletUtils.getIpAddress(request);
        String lastUserAgent = request.getHeader("User-Agent");
        UserAgent userAgent = UserAgent.parseUserAgentString(lastUserAgent);
        ure.setLastIPAddress(lastIp);
        ure.setLastUserAgent(userAgent.getBrowser() + "," + userAgent.getBrowserVersion() + "," + userAgent.getOperatingSystem());
        ure.setLastLoginTime(new Date());
        this.logonEventManager.fireEvent(new LoginEvent(ure), false);
    }

    @RequestMapping(
            value = {"api/verfycode/create"},
            method = {RequestMethod.GET}
    )
    public void createVerfyCode(@RequestParam Map<String, String> map, HttpServletRequest request, HttpServletResponse response) {
        if (map == null) {
            throw new DAOException("map is require");
        }
        String userid = map.get("userid");
        if (StringUtils.isEmpty(userid)) {
            throw new DAOException("userid is require");
        }
        response.setContentType("image/jpeg");

        int width = 80;
        int height = 40;
        int lines = 11;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        Graphics g = img.getGraphics();

        // 设置背景色
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);

        // 设置字体
        g.setFont(new Font("宋体", Font.BOLD, 20));

        // 随机数字
        Random r = new Random(new Date().getTime());
        StringBuffer vc = new StringBuffer();
        for (int i = 0; i < 4; i++) {
            int a = r.nextInt(10);
            int y = 15 + r.nextInt(20);// 10~30范围内的一个整数，作为y坐标

            Color c = new Color(r.nextInt(255), r.nextInt(255), r.nextInt(255));
            g.setColor(c);

            g.drawString(a + "", 5 + i * width / 4, y);
            vc.append(a);
        }
        new WebVerfyCode().putEx(userid, vc.toString());

        // 干扰线
        for (int i = 0; i < lines; i++) {
            Color c = new Color(r.nextInt(255), r.nextInt(255), r.nextInt(255));
            g.setColor(c);
            g.drawLine(r.nextInt(width), r.nextInt(height), r.nextInt(width), r.nextInt(height));
        }

        g.dispose();// 类似于流中的close()带动flush()---把数据刷到img对象当中

        try {
            ImageIO.write(img, "JPG", response.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @RequestMapping(
            value = {"api/wechat/login2"},
            method = {RequestMethod.POST}
    )
    public void logonByPwd(HttpServletRequest request, HttpServletResponse response) {
        if (this.checkReferer(request, response)) {
            JSONResponseBean res = new JSONResponseBean();
            try {
                Map map = (Map) JSONUtils.parse(request.getInputStream(), HashMap.class);
                if (map == null) {
                    throw new DAOException("map is require");
                }
                String uid = (String) map.get("uid");
                String verfyCode = (String) map.get("verfycode");
                String pwd = (String) map.get("pwd");
                if (StringUtils.isEmpty(uid)) {
                    throw new DAOException("用户名不能为空");
                }
                if (StringUtils.isEmpty(verfyCode)) {
                    throw new DAOException("验证码不能为空");
                }
                WebVerfyCode webVerfyCode = AppContextHolder.getBean("eh.webVerfyCode", WebVerfyCode.class);
                if(!webVerfyCode.checkVerfyCode(uid,verfyCode)){
                    return;
                }
                UserDAO userDAO = DAOFactory.getDAO(UserDAO.class);
                User user = userDAO.get(uid);
                if (user == null) {
                    throw new DAOException("用户不存在");
                }
                //pwd = this.decrypt(pwd);
                if (this.validatePwd(user, pwd)) {
                    UserRoleTokenDAO tokenDao = DAOFactory.getDAO(UserRoleTokenDAO.class);
                    java.util.List<UserRoleToken> urtList = tokenDao.findByUserId(uid);
                    if (urtList == null) {
                        throw new ControllerException(404, "patient uid[" + uid + "] not found.");
                    }
                    UserRoleToken first = null;
                    for (UserRoleToken u : urtList) {
                        if (u.getRoleId().equals(ConsultConstant.MSG_ROLE_TYPE_PATIENT)) {
                            first = u;
                        }
                    }
                    if (first == null) {
                        throw new ControllerException(404, "patient uid[" + uid + "] not found.");
                    }
                    PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
                    Patient patient = patientDAO.getByLoginId(first.getUserId());
                    if (patient == null) {
                        throw new ControllerException(404, "patient loginId[" + first.getUserId() + "] not found.");
                    }
                    UserRoleTokenEntity entity = (UserRoleTokenEntity) first;
                    entity.setProperty(ConsultConstant.MSG_ROLE_TYPE_PATIENT, patient);
                    first = entity;
                    int urt = first.getId().intValue();
                    HttpSession httpSession = request.getSession(false);
                    if (httpSession == null || !uid.equals(httpSession.getAttribute("uid"))) {
                        httpSession = request.getSession();
                    }
                    httpSession.setAttribute("uid", uid);
                    httpSession.setAttribute("token", Integer.valueOf(urt));
                    res.setBody(first);
                    this.afterLogon(request, first);
                } else {
                    res.setCode(501);
                    res.setMsg("PasswordNotRight");
                }
            } catch (ControllerException var17) {
                res.setCode(var17.getCode());
                res.setMsg(var17.getMessage());
                logger.error(var17.getMessage());
            } catch (Exception var18) {
                var18.printStackTrace();
                res.setCode(500);
                res.setMsg(var18.getMessage());
                logger.error(var18.getMessage());
            }
            boolean gzip1 = ServletUtils.isAcceptGzip(request);
            this.jsonOutputAndClearCtx(response, res, gzip1);
        }

    }

    private String decrypt(String input) {
        return RSAUtils.decryptStringByJs(input);
    }

    private boolean validatePwd(User user, String pwd) {
        int len = pwd.length();
        if (len == 32) {
            return user.validateMD5Password(pwd);
        } else if (len == 64) {
            return user.validatePassword(pwd);
        } else {
            throw new IllegalArgumentException("pwd format invalid.");
        }
    }
}
