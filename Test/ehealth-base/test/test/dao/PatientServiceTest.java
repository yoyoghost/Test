/*
package test.dao;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import ctd.util.AppContextHolder;
import eh.mpi.service.PatientService;
import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.util.JSONUtils;
import eh.entity.mpi.HealthCard;
import eh.entity.mpi.Patient;
import eh.mpi.dao.PatientDAO;
import eh.remote.IHisServiceInterface;

public class PatientServiceTest extends TestCase{
	private static ClassPathXmlApplicationContext appContext;
//	@SuppressWarnings("unused")
//	private static IHisServiceInterface service;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
//		service  = appContext.getBean("eh.patientService",IHisServiceInterface.class);
	}
	
	public void testqueryPatientInfo(){
		Patient patient = new Patient();
		patient.setMpiId("8a287a564c2fecc8014c2ffeb4040000");
		patient.setIdcard("339011197609025490");
		patient.setPatientName("赵斌强");
		patient.setMobile("15868154289");
		List<HealthCard> healthCards = new ArrayList<HealthCard>();
		HealthCard healthCard = new HealthCard();
		healthCard.setCardType("3");
		healthCard.setCardId("1438052");
		patient.setHealthCards(healthCards);
//		Patient patient = service.queryPatientInfo(getPatientRequest);//调用His
		PatientDAO patientDAO = appContext.getBean("patientDAO",PatientDAO.class);//调用Base
		Patient p = patientDAO.getHisToUpdate(patient);
		System.out.println(JSONUtils.toString(p)+".................");
	}

	public void testperfectPatientUserInfo(){
		Patient patient = new Patient();
		patient.setMpiId("402882ab586b1ede01586b1f139b0000");
		patient.setPatientName("安然微信");
		patient.setIdcard("320507195407184913");
		patient.setPatientType("3301");
		PatientService service= AppContextHolder.getBean("eh.patientService",PatientService.class);
		try{
			service.perfectPatientUserInfo(patient);
		}catch(Exception e){
			e.printStackTrace();
		}

	}

	public void testTime(){
		String s="{\"patientName\":\"安然\",\"patientSex\":\"2\",\"birthday\":\"1985-01-01 00:00:00\",\"patientType\":\"1\",\"idcard\":\"142301198101038414\",\"mobile\":\"18768177768\",\"homeArea\":\"3301\",\"guardianName\":\"安然\",\"guardianFlag\":true,\"fullHomeArea\":\"浙江省 杭州市\",\"homeAreaText\":\"杭州市\",\"birthPlaceText\":\"\",\"statusText\":\"\",\"jobText\":\"\",\"stateText\":\"\",\"patientSexText\":\"女\",\"patientTypeText\":\"自费\"}";
		String idCard18="452129195902184168";
		PatientDAO dao=appContext.getBean("patientDAO",PatientDAO.class);
		for(int i=(int)'A';i<'A'+26;i++){
			Patient p=JSONUtils.parse(s,Patient.class);
			p.setIdcard(idCard18);
			p.setPatientName("安然"+(char)i);
			Long timebefo=new Date().getTime();
			dao.getOrUpdate(p);
			Long timeafter=new Date().getTime();
			System.out.println((char)i+"花费时间:"+(timeafter-timebefo));
		}
	}

	public void testTimeLike(){

		PatientDAO dao=appContext.getBean("patientDAO",PatientDAO.class);
		String s="{\"patientName\":\"安然\",\"patientSex\":\"2\",\"birthday\":\"1985-01-01 00:00:00\",\"patientType\":\"1\",\"idcard\":\"142301198101038414\",\"mobile\":\"18768177768\",\"homeArea\":\"3301\",\"guardianName\":\"安然\",\"guardianFlag\":true,\"fullHomeArea\":\"浙江省 杭州市\",\"homeAreaText\":\"杭州市\",\"birthPlaceText\":\"\",\"statusText\":\"\",\"jobText\":\"\",\"stateText\":\"\",\"patientSexText\":\"女\",\"patientTypeText\":\"自费\"}";
		String idCard18="130827197307084478";

		for(int i=(int)'A';i<'A'+26;i++){
			Patient p=JSONUtils.parse(s,Patient.class);
			p.setIdcard(idCard18);
			p.setPatientName("安冉"+(char)i);
			Long timebefo=new Date().getTime();
			dao.getOrUpdate(p);
			Long timeafter=new Date().getTime();
			System.out.println((char)i+"花费时间:"+(timeafter-timebefo));
		}
	}
}
*/
