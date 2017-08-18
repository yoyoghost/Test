package test.dao;

import java.util.List;

import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.util.JSONUtils;
import eh.base.dao.OrganMedicalItemDAO;
import eh.entity.base.OrganMedicalItem;

public class OrganMedicalItemDAOTester extends TestCase
{
	private static ClassPathXmlApplicationContext appContext;
	private static OrganMedicalItemDAO dao;
	
	static
	{
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		dao =appContext.getBean("organMedicalItemDAO", OrganMedicalItemDAO.class);
	}
	
	/**
	 * 科室常用项目列表查询服务
	 * 备注：住院转诊确认时，选择检查项目时使用
	 * @author ZX
	 * @date 2015-4-14  下午3:24:19
	 * @param organId 机构序号
	 * @param departId 科室序号
	 * @param useType 项目使用类别（1入院检查项目2特殊检查项目）
	 * @return
	 */
	public void testFindByOrganIdAndDepartIdAndUseType(){
		int organId=1;
		int departId=1;
		int useType=1;
		List<OrganMedicalItem> list = dao.findByOrganIdAndDepartIdAndUseType(organId, departId, useType);
		System.out.println(JSONUtils.toString(list));
	}
	
}
