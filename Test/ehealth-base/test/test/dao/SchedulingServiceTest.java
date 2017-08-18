package test.dao;

import eh.bus.service.SchedulingService;
import eh.entity.bus.AppointSchedule;
import eh.entity.cdr.Recipe;
import eh.entity.mpi.Patient;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by xuqh on 2016/5/16.
 */
public class SchedulingServiceTest extends TestCase {
    private static ClassPathXmlApplicationContext appContext;

    static {
        appContext = new ClassPathXmlApplicationContext("spring.xml");
    }

    public void testDoctorSchedule() throws InterruptedException, ParseException {
        SchedulingService schedulingService = appContext.getBean("schedulingService",SchedulingService.class);
        String organID="1";
        String startTimes="2016-05-30";
        String endTimes="2016-05-30";
        SimpleDateFormat  df2 = new SimpleDateFormat("yyyy-MM-dd");
        Date startTime = df2.parse(startTimes);
        Date endTime = df2.parse(endTimes);
        df2.format( startTime );
        df2.format( endTime );
        String param=null;
        param="{OrganID: \"1\", StartTime: \"2016-05-18\", EndTime: \"2016-05-30\"}";
        //param="{OrganID: \"1000311\", StartTime: "+startTime + ", EndTime: "+endTime+"}";
        String result;
        result = schedulingService.getDoctorSchedule(param);
        System.out.println(result);
    }
}
