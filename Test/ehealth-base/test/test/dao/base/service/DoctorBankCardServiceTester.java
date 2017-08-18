package test.dao.base.service;

import ctd.util.JSONUtils;
import eh.base.service.BankService;
import eh.base.service.DoctorBankCardService;
import eh.entity.base.DoctorBankCard;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class DoctorBankCardServiceTester extends TestCase{
	private static ClassPathXmlApplicationContext appContext;
	private static DoctorBankCardService service;
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		service = appContext.getBean("doctorBankCardService", DoctorBankCardService.class);
	}


	public void testaddBankCard(){
		DoctorBankCard card=new DoctorBankCard();
		card.setDoctorId(1182);
		card.setBankName("中信银行");
		card.setSubBank("浦沿分行");
		card.setCardNo("331090909090909090");
		card.setCardName("张肖");
		System.out.println(JSONUtils.toString(card));
		service.addBankCard(card);
	}

	public void testdelBankCard(){
		System.out.println(service.delBankCard(94));
	}
	public void testupdateBankCard(){
		DoctorBankCard card=new DoctorBankCard();
		card.setId(94);
		card.setDoctorId(11824);
		card.setBankName("中信银行2");
		card.setSubBank("浦沿分行2");
		card.setCardNo("3310909090909090902sd");
		card.setCardName("张肖2");
		System.out.println(JSONUtils.toString(card));
		System.out.println(JSONUtils.toString(service.updateBankCard(card)));
	}

	public void testfindBankCards(){
		System.out.println(JSONUtils.toString(service.findBankCards(1182)));
	}

	public void testgetLastUseCard(){
		System.out.println(JSONUtils.toString(service.getLastUseCard(1182)));
	}
}
