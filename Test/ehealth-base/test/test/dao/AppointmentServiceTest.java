package test.dao;

import java.util.Date;
import java.util.List;

import junit.framework.TestCase;

import org.dom4j.Document;
import org.dom4j.Element;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.xml.XMLHelper;
import eh.base.dao.EmploymentDAO;
import eh.bus.dao.AppointRecordDAO;
import eh.bus.dao.AppointSourceDAO;
import eh.entity.bus.AppointRecord;
import eh.entity.bus.AppointSource;
import eh.entity.bus.AppointmentRequest;
import eh.entity.mpi.Patient;
import eh.mpi.dao.PatientDAO;
import eh.remote.IHisServiceInterface;



public class AppointmentServiceTest extends TestCase{
	private static ClassPathXmlApplicationContext appContext;
	private static IHisServiceInterface service;
	
	static{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");

		

	}
	
	/*public void testRpc(){
		service  = appContext.getBean("demo.echo",IHisServiceInterface.class);
		System.out.println(service.echo("hello")); 
	}*/
	public void text(){
		Date date=(Date) service.regist();
		System.out.println(date);
	}
	
	/**
	 * 预约注册服务
	 * 
	 */
	public void testRegistAppoint(){
		//Patient p=
				//402881f04bc9baec014bc9bbb0c40000
		PatientDAO pd=DAOFactory.getDAO(PatientDAO.class);
		Patient p=pd.getByMpiId("2c9081814cc3ad35014cc3e0361f0000");
		AppointRecordDAO dao=DAOFactory.getDAO(AppointRecordDAO.class);
		AppointRecord ar=dao.getByAppointRecordId(329);
		dao.registAppoint(p, ar);
		if(true)
		return;
		AppointmentRequest appointment=new AppointmentRequest();
		appointment.setId(ar.getAppointRecordId()+"");
		appointment.setPatientName(p.getPatientName());
		appointment.setPatientSex(p.getPatientSex());
		appointment.setPatientType(p.getPatientType());
		appointment.setBirthday(p.getBirthday());
		appointment.setCredentialsType("身份证");//该数据无从获取
		appointment.setCertID(p.getIdcard());
		appointment.setMobile(p.getMobile());
		appointment.setHomeAddr(p.getAddress());
		appointment.setPatientID("999999999");
		appointment.setOperjp("100");

		
		//获取医生执业机构信息（平台-->his）
		EmploymentDAO EmploymentDAO=DAOFactory.getDAO(EmploymentDAO.class);
		List<String> jobNumbers=EmploymentDAO.findJobNumberByDoctorIdAndOrganId(ar.getDoctorId(), ar.getOrganId());
		String jobNumber = jobNumbers.get(0);
		
		//获取号源信息
		AppointSourceDAO AppointSourceDAO=DAOFactory.getDAO(AppointSourceDAO.class);
		AppointSource as=AppointSourceDAO.getById(ar.getAppointSourceId());
		
		if(as!=null){
			//普通预约
			appointment.setSchedulingID(as.getOrganSchedulingId());
			appointment.setAppointSourceID(as.getOrganSourceId());
			appointment.setOrderNum(as.getOrderNum());
		}else{
			//转诊预约
			appointment.setSchedulingID("0");
			appointment.setAppointSourceID("0");
			appointment.setOrderNum(0);
		}
		appointment.setOrganID(ar.getOrganId()+"");
		appointment.setDepartCode(ar.getAppointDepartId());
		appointment.setDepartName(ar.getAppointDepartName());
		appointment.setDoctorID(jobNumber);
		appointment.setWorkDate(ar.getWorkDate());//预约日期
		appointment.setStartTime(ar.getStartTime());//时间点
		appointment.setWorkType(ar.getWorkType()+"");
		if(ar.getAppointRoad().equals(5)){
		   appointment.setAppointRoad(2);//转诊预约，
		}
		if(ar.getAppointRoad().equals(6)){
			   appointment.setAppointRoad(3);//转诊加号预约
		}
		appointment.setSourceLevel(ar.getSourceLevel());
		appointment.setPrice(ar.getClinicPrice());
		String HisServiceId="h"+ar.getOrganId()+".appointmentService";
		IHisServiceInterface appointService=AppContextHolder.getBean(HisServiceId,IHisServiceInterface.class);
		
		appointService.registAppoint(appointment);
		
		System.out.println(JSONUtils.toString(appointment));
		System.out.println(createAppointmentRequest(appointment));
		//service  = appContext.getBean(HisServiceId,IHisServiceInterface.class);
        //String xml=createAppointmentRequest(appointment);
		//AppointmentResponse result=service.registAppoint(appointment);
		//System.out.println(JSONUtils.toString(result));
	}
	public String createAppointmentRequest(AppointmentRequest appointment){
	      Document doc=XMLHelper.createDocument();
	      Element root=doc.addElement("Root");
	      Element head=root.addElement("Head");
	      Element request=root.addElement("Request");
	     
	      head.addElement("TransID").setText("Appointment");
	      head.addElement("OragnID").setText(appointment.getOrganID());
	      head.addElement("DataSource").setText("");
	      head.addElement("OperateInfo").setText("");
	      head.addElement("TerminalInfo").setText("");
	      //
	      request.addElement("PatientName").setText(appointment.getPatientName()==null?"":appointment.getPatientName());
	      request.addElement("PatientSex").setText(appointment.getPatientSex()==null?"":appointment.getPatientSex());
	      request.addElement("PatientType").setText(appointment.getPatientType()==null?"":appointment.getPatientType());
	      request.addElement("Birthday").setText(appointment.getBirthday()==null?"":appointment.getBirthday().toString());
	      request.addElement("CredentialsType").setText(appointment.getCredentialsType()==null?"":appointment.getCredentialsType());
	      request.addElement("CertID").setText(appointment.getCertID()==null?"":appointment.getCertID());
	      request.addElement("SchedulingID").setText(appointment.getSchedulingID()==null?"":appointment.getSchedulingID());
	      request.addElement("AppointSourceID").setText(appointment.getAppointSourceID()==null?"":appointment.getAppointSourceID());
	      request.addElement("OrganID").setText(appointment.getOrganID()==null?"":appointment.getOrganID());
	      request.addElement("DepartCode").setText(appointment.getDepartCode()==null?"":appointment.getDepartCode());
	      request.addElement("DepartName").setText(appointment.getDepartName()==null?"":appointment.getDepartName());
	      request.addElement("DoctorID").setText(appointment.getDoctorID()==null?"":appointment.getDoctorID());
	      request.addElement("WorkDate").setText(appointment.getWorkDate()==null?"":appointment.getWorkDate().toString());
	      request.addElement("WorkType").setText(appointment.getWorkType()==null?"":appointment.getWorkType());
	      request.addElement("StartTime").setText(appointment.getStartTime()==null?"":appointment.getStartTime().toString());
	      request.addElement("EndTime").setText(appointment.getEndTime()==null?"":appointment.getEndTime().toString());
	      request.addElement("OrderNum").setText(appointment.getOrderNum()+"");
	      request.addElement("AppointRoad").setText(appointment.getAppointRoad()+"");
	      request.addElement("Price").setText(appointment.getPrice()==null?"":appointment.getPrice().toString());
	      request.addElement("Mobile").setText(appointment.getMobile()==null?"":appointment.getMobile());
	      request.addElement("HomeAddr").setText(appointment.getHomeAddr()==null?"":appointment.getHomeAddr());
	      request.addElement("PatientID").setText(appointment.getPatientID()==null?"":appointment.getPatientID());
	      
	      
			return doc.asXML();
		}
	
}
