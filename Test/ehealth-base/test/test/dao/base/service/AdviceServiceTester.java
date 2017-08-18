package test.dao.base.service;

import ctd.util.JSONUtils;
import eh.base.service.AdviceService;
import eh.bus.dao.AdviceDAO;
import eh.entity.bus.Advice;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.List;

public class AdviceServiceTester extends TestCase{
	private static ClassPathXmlApplicationContext appContext;
	private static AdviceService service;
    private static AdviceDAO dao;
	static{
		appContext = new ClassPathXmlApplicationContext("spring.xml");
		service = appContext.getBean("adviceService", AdviceService.class);
        dao =appContext.getBean("adviceDAO", AdviceDAO.class);
	}

	public void testsaveAdvice(){
		Advice ad = new  Advice();
		ad.setAdviceContent("测试意见");
		ad.setAdviceType("doctor");
		ad.setUserId("18768177768");

		service.saveAdvice(ad);
	}

	public void testSaveDrugAdvice(){
		Advice ad = new  Advice();
		ad.setAdviceContent("测试无药品反馈2");
		ad.setAdviceType("doctor");
		ad.setUserId("13738049559");

		service.saveDrugAdvice(ad);
	}

    public void testFindByAdviceTypeAndUserId(){
        List<Advice> list = dao.findByAdviceTypeAndUserId("doctor", "13738049559");
        System.out.println(JSONUtils.toString(list));
    }
}
