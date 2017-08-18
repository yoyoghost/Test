package test.dao;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.controller.exception.ControllerException;
import ctd.persistence.exception.DAOException;
import eh.bus.dao.RequestMeetClinicDAO;
import eh.entity.bus.MeetClinic;
import eh.entity.bus.MeetClinicResult;
import eh.entity.cdr.Otherdoc;

public class RequestMeetClinicDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	 
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	
	/**
	 * 会诊申请服务
	 * @author LF
	 * @return
	 * @throws DAOException
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void testRequestMeetClinic() throws DAOException, ControllerException{	
		MeetClinic mc = new MeetClinic();
		mc.setMpiid("2c9081814cc3ad35014cc54fca420003");
		mc.setMeetClinicType(1);
		mc.setRequestOrgan(1);
		mc.setRequestDepart(70);
		mc.setRequestDoctor(3846);
		mc.setDiagianCode("101");
		mc.setDiagianName("肺炎");
		mc.setPatientCondition("患者一周前来院就诊，出现咳嗽、低热症状，服用感冒药一周后无效，CT显示肺部有阴影，怀疑患肺炎。");
		mc.setLeaveMess("确定诊断。");
		mc.setAnswerTel("12345678901");
		mc.setPayflag(0);
//		mc.setSessionID("test");
		
		/*MeetClinicResult mr = new MeetClinicResult();
		mr.setTargetOrgan(1);
		mr.setTargetDepart(70);
		mr.setTargetDoctor(1195);*/
		
		List list = new ArrayList();
//		list.add(mr);
		
		MeetClinicResult mr2 = new MeetClinicResult();
		mr2.setTargetOrgan(1);
		mr2.setTargetDepart(70);
		mr2.setTargetDoctor(4728);
		list.add(mr2);
		
		MeetClinicResult mr3 = new MeetClinicResult();
		mr3.setTargetOrgan(2);
		mr3.setTargetDepart(6);
		mr3.setTargetDoctor(39);
		list.add(mr3);
		
		RequestMeetClinicDAO dao = appContext.getBean("requestMeetClinicDAO",RequestMeetClinicDAO.class);
//		dao.requestMeetClinic(mc,list);
		System.out.println(dao.requestMeetClinic(mc, list,new ArrayList<Otherdoc>()));
	}
	
	public void testAddTargetDoctor() {
		Integer meetClinicId=284;
		Integer targetDoctor=625;
		Integer targetOrgan=1;
		Integer targetDepart=16;
		RequestMeetClinicDAO dao = appContext.getBean("requestMeetClinicDAO",RequestMeetClinicDAO.class);
		Boolean boolean1 = dao.addTargetDoctor(meetClinicId, targetDoctor, targetOrgan, targetDepart);
		System.out.println(boolean1);
	}
}
