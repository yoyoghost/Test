package eh.test;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import ctd.access.AccessToken;
import ctd.access.AccessTokenController;
import ctd.account.UserRoleToken;
import ctd.account.user.User;
import ctd.account.user.UserController;
import ctd.controller.exception.ControllerException;
import ctd.mvc.controller.support.JSONRequester;
import ctd.mvc.controller.support.LogonManager;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.context.Context;
import ctd.util.context.ContextUtils;
import eh.base.dao.DoctorDAO;
import eh.bus.service.UnLoginSevice;
import eh.entity.base.Doctor;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractTransactionalJUnit4SpringContextTests;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:spring-test.xml")
public class Junit4SpringDemo1 extends AbstractTransactionalJUnit4SpringContextTests {

    @Test
    public void testLogon(){
        String uid = "15869197715";
        String rid = "doctor";
        String pwd = "111111";
        boolean forAccessToken = true;
        LogonManager logonManager = applicationContext.getBean("mvcLogonManager", LogonManager.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setMethod("POST");
        Map<String, Object> paramters = Maps.newHashMap();
        paramters.put("uid", uid);
        paramters.put("pwd", DigestUtils.md5Hex(pwd));
        paramters.put("rid", rid);
        paramters.put("forAccessToken", forAccessToken);
        request.setContent(JSONUtils.toBytes(paramters));
        request.setContentType("application/json");
        logonManager.logon(request, response);
        assertEquals(200, response.getStatus());
        Map result = JSONUtils.parse(response.getContentAsByteArray(), Map.class);
        System.out.println(result);
        assertEquals(200, result.get("code"));
        assertNotNull(result.get("body"));
        System.out.println(result.get("body"));
    }

    @Test
    public void testRpcService1() {
        UnLoginSevice unLoginSevice = AppContextHolder.getBean("eh.unLoginSevice", UnLoginSevice.class);
        Doctor doctor = unLoginSevice.getByDoctorIdInUnLogin(9546);
        System.out.println(doctor);
        assertNotNull(doctor);
    }

    @Test
    public void testRpcService2() {
        makeLogon("15869197716", "patient");
        makeClient(2692);
        DoctorDAO doctorDAO = AppContextHolder.getBean("eh.doctor", DoctorDAO.class);
        List list = doctorDAO.recommendDoctors("330101", 22, "2", 0);
        System.out.println(list);
        assertNotNull(list);
    }

    @Test
    public void testJsonRequest(){
        JSONRequester jsonRequester = applicationContext.getBean("mvcJSONRequester", JSONRequester.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setMethod("POST");
        request.setContentType("application/json");
        request.addHeader("X-Service-Id", "eh.userRemoteLoader");
        request.addHeader("X-Service-Method", "load");
        request.addHeader("X-Access-Token", getToken("15869197716", "patient").getId());
        List<Object> parameters = Lists.newArrayList();
        parameters.add("15869197716");
        request.setContent(JSONUtils.toBytes(parameters));
        jsonRequester.doJSONRequest(request, response);
        System.out.println(response.getStatus() );
        Map result = JSONUtils.parse(response.getContentAsByteArray(), Map.class);
        System.out.println(result);
        assertNotNull(result);
    }

    protected void makeClient(int clientId){
        ContextUtils.put(Context.RPC_INVOKE_HEADERS, ImmutableMap.of(Context.CLIENT_ID, clientId));
    }

    protected void makeLogon(String uid, String role){
        User user = null;
        try {
            user = UserController.instance().get(uid);
            List<UserRoleToken> urts = user.findUserRoleTokenByRoleId(role);
            if(!urts.isEmpty()){
                ContextUtils.put(Context.USER_ROLE_TOKEN, urts.get(0));
            }
        } catch (ControllerException e) {
            e.printStackTrace();
        }
    }

    protected AccessToken getToken(String uid, String role) {
        makeLogon(uid, role);
        UserRoleToken urt = UserRoleToken.getCurrent();
        if (urt != null){
            AccessToken accessToken = null;
            try {
                accessToken = AccessTokenController.instance().getUpdater().create(new AccessToken(urt.getUserId(),urt.getId(),null));
            } catch (ControllerException e) {
                e.printStackTrace();
            }
            return accessToken;
        }
        return null;
    }


}
