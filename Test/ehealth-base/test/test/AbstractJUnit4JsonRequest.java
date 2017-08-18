package test;

import ctd.mvc.controller.support.JSONRequester;
import ctd.util.JSONUtils;
import org.junit.runner.RunWith;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractTransactionalJUnit4SpringContextTests;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.List;
import java.util.Map;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:spring.xml")
public class AbstractJUnit4JsonRequest extends AbstractTransactionalJUnit4SpringContextTests {
    protected static final String jsonRequesterBeanName = "mvcJSONRequester";
    protected JSONRequester jsonRequester;

    private MockHttpServletRequest createRequest(String serviceId, String method, List<Object> parameters, String tk){
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setContentType("application/json");
        request.addHeader("X-Service-Id", serviceId);
        request.addHeader("X-Service-Method", method);
        request.addHeader("X-Access-Token", tk);
        request.setContent(JSONUtils.toBytes(parameters));
        return request;
    }

    protected Map<String,Object> exec(String serviceId, String method, List<Object> parameters, String tk){
        if(jsonRequester == null){
            jsonRequester = applicationContext.getBean(jsonRequesterBeanName, JSONRequester.class);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        jsonRequester.doJSONRequest(createRequest(serviceId,method,parameters,tk), response);
        return JSONUtils.parse(response.getContentAsByteArray(), Map.class);
    }

}
