package test.message;

import eh.entity.msg.SmsInfo;
import eh.msg.dao.SmsInfoDAO;
import eh.task.executor.AliSmsSendExecutor;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.concurrent.TimeUnit;

import static test.message.MassRootDAOTester.dao;

/**
 * Created by w on 2016/5/13.
 */
public class AliSmsSendExecutorTester extends TestCase {
    private static ClassPathXmlApplicationContext appContext;

    static{
        appContext = new ClassPathXmlApplicationContext("test/spring.xml");

    }
    public  void testSend() throws InterruptedException {
        SmsInfo info = new SmsInfo();
        info.setBusId(550);// 业务表主键
        info.setBusType("appointrecord");// 业务类型
        info.setSmsType("appSuccMsg");// 预约成功 info.setStatus(0);
        info.setOrganId(1);// 短信服务对应的机构， 0代表通用机构
        AliSmsSendExecutor sender=new AliSmsSendExecutor(info);
        sender.execute();
        TimeUnit.SECONDS.sleep(10);
    }
}
