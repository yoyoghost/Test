package test.dao;

import java.util.List;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.util.JSONUtils;

import eh.bus.dao.AdviceDAO;
import eh.entity.bus.Advice;

public class AdviceDAOTester extends TestCase
{
	private static ClassPathXmlApplicationContext appContext;
	private static AdviceDAO dao;
	
	static
	{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		dao =appContext.getBean("adviceDAO", AdviceDAO.class);
	}
	
	
	/**
	 * 保存意见
	 * @author ZX
	 * @date 2015-5-19  下午10:49:34
	 */
	public void testSaveAdvice(){
		 Advice ad = new  Advice();
		 ad.setAdviceContent("测试意见");
		 ad.setAdviceType("doctor");
		 ad.setUserId("18768177768");
		 
		 dao.saveAdvice(ad);
	}
	
	public void testFindByAdviceTypeAndUserId(){
		List<Advice> list = dao.findByAdviceTypeAndUserId("doctor", "18768177768");
		System.out.println(JSONUtils.toString(list));
	}
}
