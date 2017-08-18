package test.dao.base.service;

import ctd.util.JSONUtils;
import eh.base.dao.WxConfigDAO;
import eh.base.service.PatientFeedbackService;
import eh.entity.base.WxConfig;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.Date;

public class PatientFeedbackServiceTester extends TestCase{
	private static ClassPathXmlApplicationContext appContext;
	static {
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}

	public void testevaluationConsultForHealth(){
		Integer consultId=674;
		new PatientFeedbackService().evaluationConsultForHealth(consultId);
	}

}
