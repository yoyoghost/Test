package test.evaluation.service;

import eh.evaluation.service.SensitiveWordsService;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Created by PC on 2016/11/1.
 */
public class SensitiveWordsServiceTester {
    private static ClassPathXmlApplicationContext appContext;
    static {
        appContext = new ClassPathXmlApplicationContext("test/spring.xml");
    }

    @Test
    public void replaceWords() throws Exception {
        String content = "SB";
        String content_1 = "SB,Sb,sB";
        long beginTime = System.currentTimeMillis();
        new SensitiveWordsService().replaceWords(content);
        new SensitiveWordsService().replaceWords(content_1);
        long endTime = System.currentTimeMillis();
        System.out.println("总共消耗时间为：" + (endTime - beginTime));
    }

    @Test
    public void SensitiveMsgDeal() throws Exception {
        int content = 31;
        new SensitiveWordsService().sensitiveMsgDeal(content);
        System.out.println("111111111111111111111111111");
    }

    @Test
    public void sensitiveWordsDealSchedule() throws Exception {
        new SensitiveWordsService().sensitiveWordsDealSchedule();
        System.out.println("成功。。。。。。。。。。。");
    }
}