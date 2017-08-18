package eh.cdr.service;

import java.util.List;

import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import eh.base.service.AdviceService;
import eh.entity.bus.Advice;
import eh.entity.his.push.callNum.HisCallNoticeReqMsg;
import eh.entity.his.push.callNum.PushRequestModel;
import eh.mpi.service.sign.SignRecordService;
import eh.push.NgariService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.Assert;

import com.alibaba.fastjson.JSONObject;

/**
 * company: ngarihealth
 * author: hexy	
 * date:2017/5/25
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:spring-test.xml")
public class SignRecordServiceTest extends AbstractJUnit4SpringContextTests {

    @Test
    public void saveDrugAdvice() {
        SignRecordService signRecordService = AppContextHolder.getBean("eh.signRecordService",SignRecordService.class);
        List<String> signPatientLabel = signRecordService.getSignPatientLabel(10);
        System.err.println(JSONObject.toJSONString(signPatientLabel));
    }
}