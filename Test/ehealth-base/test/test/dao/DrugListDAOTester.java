package test.dao;

import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.util.JSONUtils;
import eh.base.dao.DrugListDAO;
import eh.base.service.DrugListService;
import eh.entity.base.DrugList;
import eh.utils.DateConversion;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.Date;
import java.util.List;

public class DrugListDAOTester extends TestCase {

	private static ClassPathXmlApplicationContext appContext;

	static {
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
	}
	private static DrugListDAO dao = appContext.getBean("drugListDAO",
			DrugListDAO.class);

	private static DrugListService service = appContext.getBean("drugListService", DrugListService.class);

	public void testGet() {
		System.out.println(JSONUtils.toString(dao.get(1)));
	}

	/*public void testFindDrugAliasByNameOrCode() {
		String drugName = "头孢";
		List<DrugList> drugLists = dao.findDrugListsByNameOrCode(1,1,drugName, 0,
				10);
		System.out.println(drugLists.size());
		System.out.println(JSONUtils.toString(drugLists));
	}*/

	public void testFindDrugListsByNameOrCodePageStaitc() {
		String drugName = "头孢";
//		List<DrugListForHighlight> drugLists = dao.findDrugListsByNameOrCodePageStaitc(
//				1,1,drugName, 0);
//		System.out.println(drugLists.size());
//		System.out.println(JSONUtils.toString(drugLists));
	}

	/**
	 * 全部药品列表服务
	 * 
	 * @author luf
	 * @param organId
	 *            医疗机构代码
	 * @param start
	 *            分页起始位置
	 * @return List<DrugList>
	 */
	public void testFindAllDrugListsByOrgan() {
		int organId = 1;
		List<DrugList> drugLists = dao.findAllInDrugClassByOrgan(organId,1,"",0);
		System.out.println(JSONUtils.toString(drugLists));
		System.out.println(drugLists.size());
	}

	/**
	 * 药品分类下的全部药品列表服务
	 * 
	 * @author luf
	 * @param organId
	 *            医疗机构代码
	 * @param drugClass
	 *            药品分类
	 * @param start
	 *            分页起始位置
	 * @return List<DrugList>
	 */
	public void testFindAllInDrugClassByOrgan() {
		int organId = 1;
		String drugClass = "A100";
		List<DrugList> drugLists = dao.findAllInDrugClassByOrgan(organId,1,
				drugClass, 0);
		System.out.println(JSONUtils.toString(drugLists));
		System.out.println(drugLists.size());
	}

	/**
	 * 常用药品列表服务
	 * 
	 * @author luf
	 * @param doctor
	 *            开方医生
	 * @param start
	 *            分页开始位置
	 * @return List<DrugList>
	 */
	public void testFindCommonDrugListsStart() {
		int doctor = 40;
		List<DrugList> drugLists = dao.findCommonDrugLists(doctor,1,1);
		System.out.println(JSONUtils.toString(drugLists));
	}

	/**
	 * 几月前/几年前
	 * 
	 * @author luf
	 */
	public void testAgo() {
		System.out.println(new Date());
		System.out.println(DateConversion.getMonthsAgo(12));
		System.out.println(DateConversion.getYearsAgo(1));
	}

	public void testDictionary() {
		try {
			String drugClass = DictionaryController.instance()
					.get("eh.base.dictionary.DrugClass").getText("06");
			System.out.println(drugClass);
		} catch (ControllerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * 获取药品类别
	 * 
	 * @author luf
	 * @param parentKey
	 *            父节点值
	 * @param sliceType
	 *            --0所有子节点 1所有叶子节点 2所有文件夹节点 3所有子级节点 4所有子级叶子节点 5所有子级文件夹节点
	 * @return List<DictionaryItem>
	 */
	public void testGetDrugClassSliceByKey() {
		System.out.println(JSONUtils.toString(dao.getDrugClass("0602", 3)));
	}
	
	/**
	 * 获取一个药品类别下面的第一子集和第二子集，重新组装
	 * @author zhangx
	 * @date 2015-12-7 下午7:45:25
	 */
	public void testfindDrugClass() {
		System.out.println(JSONUtils.toString(dao.findDrugClass("06")));
	}


	//设置drugName拼音简码
	public void testPycode() {
		service.setPyCode();
	}

	//设置saleName 全拼
	public void testAllpycode(){
		service.setAllPyCode();
	}
}
