package test.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import eh.bus.service.appointrecord.RequestAppointService;
import eh.entity.bus.AppointRecord;
import eh.utils.DateConversion;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.text.SimpleDateFormat;
import java.util.Date;


/**
 * Created by zhangsl on 2016/8/28.
 */
public class RequestAppointServiceTest extends TestCase {

    private static ClassPathXmlApplicationContext appContext;
    private static RequestAppointService service;

    static
    {
        appContext = new ClassPathXmlApplicationContext("test/spring.xml");
        service =appContext.getBean("requestAppointService", RequestAppointService.class);
    }

    public void testAddAppointRecordNew() throws Exception {
        String json="{\"mpiid\":\"2c90818956d9281e0156d93835d50001\",\"patientName\":\"阿狸\",\"certId\":\"310230200702089491\",\"linkTel\"" +
                ":\"15511111111\",\"organAppointId\":\"\",\"appointSourceId\":1680742,\"organId\":1,\"appointDepartId\":\"58A\",\"appointDepartName\"" +
                ":\"疝病外科门诊\",\"doctorId\":9138,\"workDate\":\"2016-08-30 00:00:00\",\"workType\":1,\"sourceType\":1,\"startTime\":\"2016-08-30" +
                " 11:36:00\",\"endTime\":\"2016-08-30 12:00:00\",\"orderNum\":1,\"appointRoad\":4,\"appointStatus\":0,\"appointUser\"" +
                ":\"2c90818956d9281e0156d93835d50001\",\"appointName\":\"阿狸\",\"appointOragn\":\"\",\"clinicPrice\":30,\"transferId\"" +
                ":0,\"sourceLevel\":3}";
        AppointRecord record=(JSON.toJavaObject((JSONObject)JSON.parse(json),AppointRecord.class));
        System.out.println(service.addAppointRecordNew(record));
    }

    public static void main(String[] args){
        SimpleDateFormat df=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            Date workDate = df.parse("2016-08-30 00:00:00");
            System.out.println(df.format(new Date()));
            System.out.println(DateConversion.getDaysBetween(workDate,new Date()));
        }catch (Exception e){
            e.printStackTrace();
        }
    }

}