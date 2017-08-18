package test.service;

import junit.framework.TestCase;

import org.dom4j.Document;
import org.dom4j.Element;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.util.xml.XMLHelper;
import eh.bus.service.RegReportInfoService;

public class RegReportInfoServiceTester extends TestCase{
	private static ClassPathXmlApplicationContext appContext;
	private static RegReportInfoService service;
	
	static
	{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		service =appContext.getBean("regReportInfoService", RegReportInfoService.class);
	}
	public void testGetByUID(){
		String requestXml = createRequestXml();
		String responseXml = service.regReportInfo(requestXml);
		System.out.println(responseXml);
	}
	public String createRequestXml(){
		Document document = XMLHelper.createDocument();
		Element root = document.addElement("root");
		Element requestElement = root.addElement("Request");
		
		requestElement.addElement("CardType").setText("3310");//病人类型（卡类型）
		requestElement.addElement("PatientType").setText("3310");//病人类型（卡类型）
		requestElement.addElement("CardNo").setText("201503929");//卡号
		requestElement.addElement("LabelID").setText("");//条码号
		requestElement.addElement("PatName").setText("王大江");//病人姓名
		requestElement.addElement("PatNameSpell").setText("wdj");//姓名首拼码
		requestElement.addElement("IDNum").setText("522635197406199848");//身份证号
		requestElement.addElement("HospNo").setText("");//住院号
		requestElement.addElement("Sex").setText("1");//性别
		requestElement.addElement("BirthDay").setText("1973-06-21");//出生日期==========格式需要再确认
		requestElement.addElement("Age").setText("34岁");//年龄 带单位
		requestElement.addElement("PhoneNum").setText("19078304851");//手机号码
		requestElement.addElement("ApplyDeptName").setText("心胸外科");//申请科室
		requestElement.addElement("WardName").setText("");//病区名称
		requestElement.addElement("BedNo").setText("");//床号
		requestElement.addElement("WardOrReg").setText("1");//病人来源
		requestElement.addElement("ReportNo").setText("4564565");//报告唯一号
		requestElement.addElement("HospCode").setText("470003265");//组织机构代码
		requestElement.addElement("ReportDepart").setText("心胸外科");//检查科室名称
		requestElement.addElement("SubSysCode").setText("LIS_SH");//子系统代码
		requestElement.addElement("ReportDesc").setText("X-Ray");//报告类别
		requestElement.addElement("StudyItem").setText("X-Ray照射");//检查项目名称
		requestElement.addElement("TechNo").setText("23542322");//影像号
		requestElement.addElement("HisApplyNo").setText("201510283454");//申请单号
		requestElement.addElement("ReportDoctorName").setText("张青");//报告医生名称
		requestElement.addElement("ReportTime").setText("2015-10-29 14:23:10");//报告时间 =============格式需要再确认
		requestElement.addElement("VerifyDoctorName").setText("刘莉平");//审核医生名称
		requestElement.addElement("AuditingTime").setText("2015-10-28 15:23:10");
		requestElement.addElement("StudyMethod").setText("检查方法3");//检查方法
		requestElement.addElement("StudyObservation").setText("查检所见3");//检查所见
		requestElement.addElement("StudyResult").setText("检查结论3");//检查结论
		return document.asXML();
	}
}
