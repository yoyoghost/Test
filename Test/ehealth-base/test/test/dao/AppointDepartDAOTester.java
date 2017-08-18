package test.dao;

import java.util.List;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import eh.bus.dao.AppointDepartDAO;
import eh.entity.bus.AppointDepart;

public class AppointDepartDAOTester extends TestCase
{
	private static ClassPathXmlApplicationContext appContext;
	private static AppointDepartDAO bdd;
	
	static
	{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		bdd =appContext.getBean("appointDepartDAO", AppointDepartDAO.class);
	}
	
	/**
	 * 获取dictionary
	 */
	public void testGetDictionaryItem(){
		try {
			String appointDepartName = DictionaryController.instance().get("eh.bus.dictionary.AppointDepart").getText(2);
			System.out.println(appointDepartName);
		} catch (ControllerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * 测试名:获取医院挂号科室列表服务
	 * @author yxq
	 */
	public void testFindByOrganIDAndProfessionCode()
	{
		int organID = 1;
		String professionCode = "13";
		List<AppointDepart> result = bdd.findByOrganIDAndProfessionCode(organID, professionCode);
		System.out.println(result.size());
		for(int i = 0; i<result.size(); i++)
		{
			System.out.println(JSONUtils.toString(result.get(i)));
		}
	}
	
	/**
	 * 挂号科室注册服务测试
	 * @throws DAOException
	 */
	public void testSaveAppointDepart() throws DAOException{
		AppointDepart appointDepart=new AppointDepart();
		appointDepart.setOrganId(1);
		appointDepart.setAppointDepartCode("0103");
		appointDepart.setAppointDepartName("消化内科");
		bdd.saveAppointDepart(appointDepart);
		
	}
	
	public void testGetByOrganIDAndAppointDepartCode() throws DAOException{
		int organID=1;
		String appointDepartCode="0103";
		AppointDepart appointDepart=bdd.getByOrganIDAndAppointDepartCode(organID, appointDepartCode);
		System.out.println(JSONUtils.toString(appointDepart));
	}
	
	public void testGetById(){
		int id=1;
		AppointDepart appointDepart=bdd.getById(id);
		System.out.println(JSONUtils.toString(appointDepart));
	}
	
	/**
	 * 根据挂号科室编码,机构编码查询挂号科室序号
	 * @author ZX
	 */
	public void testGetIdByOrganIdAndAppointDepartCode() throws DAOException{
		int organID=1;
		String appointDepartCode="0701";
		int appointDepartId=bdd.getIdByOrganIdAndAppointDepartCode(organID, appointDepartCode);
		System.out.println(JSONUtils.toString(appointDepartId));
	}
	
	/**
	 * 根据挂号科室序号查询挂号科室编码 
	 * @author ZX
	 */
	public void testGetAppointDepartCodeById() throws DAOException{
		int appointDepartId=3;
		String appointDepartCode=bdd.getAppointDepartCodeById(appointDepartId);
		System.out.println(JSONUtils.toString(appointDepartCode));
	}
	
	public void testFindProfessionCodeByOrganId() throws DAOException{
		int organId=1;
		List<String> list=bdd.findProfessionCodeByOrganId(organId);
		System.out.println("------"+list.size());
		for(int i=0;i<list.size();i++){
			System.out.println(list.get(i));
		}
	}
	/**
	 * 创建普通科室医生信息
	 */
	public void testCreateCommonDept(){
		bdd.createCommonDeptDoctor(1);
	}
	/**
	 * 根据机构号和科室号查询预约预约科室
	 */
	public void testFindByOrganIDAndDepartID() throws DAOException{
		int organID=1;
		int departID=82;
		AppointDepart appointDepart = bdd.findByOrganIDAndDepartID(organID, departID);
			System.out.println(JSONUtils.toString(appointDepart));
	}
	
	public void testAddAppointDepartCodeToOrgans() {
		bdd.addAppointDepartCodeToOrgans();
	}
}
