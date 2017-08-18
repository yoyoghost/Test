/*
package eh.cdr.service;

import ctd.persistence.DAOFactory;
import ctd.util.JSONUtils;
import ctd.util.converter.ConversionUtils;
import eh.bus.dao.SmsRecordDAO;
import eh.entity.bus.SmsRecord;
import eh.util.AlidayuSms;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Date;
import java.util.HashMap;
import java.util.List;

*/
/**
 * Created by Chuwei on 2017/4/17.
 *//*

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:spring.xml")
public class SmsRecordServiceTest extends AbstractJUnit4SpringContextTests {
    @Test
    public void testSendSMS(){
        SmsRecordDAO dao = DAOFactory.getDAO(SmsRecordDAO.class);
        Date date =ConversionUtils.convert("2017-04-17 00:00:21", Date.class);
        List<SmsRecord> failSms = dao.findFailSms(date,"%余额不足%");

        for( SmsRecord smsRecord : failSms) {
            HashMap<String, String> smsParam = new HashMap<String, String>();
            String mobile=smsRecord.getMobile();
            String smsTemp = smsRecord.getSmsTemplateCode();
            String content = smsRecord.getContent();
            smsParam = JSONUtils.parse(content, HashMap.class);
            AlidayuSms.sendSms(mobile, smsTemp, smsParam);
        }
    }
    @Test
    public void testSendSMS2(){
        HashMap<String, String> smsParam = new HashMap<String, String>();
        String content = "{\"customTel\":\"400-613-8688(如需咨询请致电医院母婴健康热线：0771-2860555，每天8：00-17：00在线)\",\"patientName\":\"刘海燕\",\"appointInfo\":\"广西壮族自治区妇幼保健院(新阳院区) (新阳)产科门诊 宋良04月18日 10:00-10:29的预约号。在就诊当日需持就诊卡（首次就诊者，请持本人身份证（未成年无身份证号患者，请持本人及监护人有效证件））并必须在10:00前在挂号窗口完成预约挂号后到分诊台候诊，否则视为爽约；逾期取号或未提前取消的，按爽约处理\"}";
        smsParam = JSONUtils.parse(content, HashMap.class);

        AlidayuSms.sendSms("18605818616", "SMS_5505346", smsParam);
    }
}
*/
