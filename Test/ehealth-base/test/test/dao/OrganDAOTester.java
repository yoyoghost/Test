package test.dao;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.hibernate.annotations.SourceType;
import org.mvel2.ast.Or;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.controller.exception.ControllerException;
import ctd.dictionary.Dictionary;
import ctd.dictionary.DictionaryController;
import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import eh.base.dao.OrganDAO;
import eh.entity.base.Organ;

import junit.framework.TestCase;

public class OrganDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	private static OrganDAO dao;

	static {
		appContext = new ClassPathXmlApplicationContext("test/spring.xml");
		dao = appContext.getBean("organDAO", OrganDAO.class);
	}

	/**
	 * 注销机构
	 * @author LF
	 */
	public void testUpdateStatusToCancellation() throws DAOException {
		Integer organId = 1000005;
		dao.updateStatusToCancellation(organId);
	}

	/**
	 * 删除机构
	 * @author LF
	 */
	public void testLogicallyDeleteOrgan() throws DAOException {
		Integer organId = 1000005;
		dao.logicallyDeleteOrgan(organId);
	}

	public Organ testCreate() throws DAOException {

		int nmr = ThreadLocalRandom.current().nextInt(10000);
		int n4 = ThreadLocalRandom.current().nextInt(1000, 9999);

		Organ r = new Organ();
		r.setName("医院" + nmr);
		r.setOrganizeCode("3301" + n4);
		r.setCreateDt(new Date());
		r.setStatus(1);
		return dao.save(r);
	}

	public void testGetByOrganCode() throws DAOException {
		Organ r = dao.getByOrganizeCode("33011981");
		assertNotNull(r);
		assertEquals((int) r.getOrganId(), 1);
		System.out.println(JSONUtils.toString(r));
	}

	/**
	 * 供前端调取医院列表
	 * @author LF
	 * @return
	 */
	public void testFindByAddrArea() throws DAOException {
		String addrArea = "3301";
		List<Organ> list = dao.findByAddrAreaLike(addrArea);
//		for (int i = 0; i < list.size(); i++) {
			System.out.println(JSONUtils.toString(/*list.get(i)*/list));
//		}
	}

	
	/**
	 * 根据机构分级编码查询机构列表的服务
	 * 
	 * 给后台运营管理系统调用
	 * 
	 * @author LF
	 * @return
	 */
	public void testFindOrgansByManageUnit() throws DAOException {
		String manageUnit = "eh002";
		String type="0";
		String addrArea = "330104";
		Organ organ = new Organ();
		organ.setManageUnit(manageUnit);
		organ.setType(type);
		organ.setAddrArea(addrArea);
		List<Organ> organs = dao.findOrgansByManageUnit(organ, 0);
		System.out.println(JSONUtils.toString(organs));
	}

	/**
	 * 注册医院服务
	 * @author LF
	 * @return
	 */
	public void testRegistOrgan() throws DAOException {
		Organ organ = new Organ();
		organ.setOrganizeCode("1111111111");
		organ.setName("江干区卫生局");
		organ.setShortName("江干区卫生局");
		organ.setType("9");
		organ.setGrade("30");
		organ.setAddrArea("330104");
		organ.setManageUnit("eh002");
		System.out.println(JSONUtils.toString(organ));
		Organ organ2 = dao.registOrgan(organ);
		System.out.println(JSONUtils.toString(organ2));
	}

	/**
	 * 更新医院服务
	 * @author LF
	 * @return
	 */
	public void testUpdateOrgan() throws DAOException {
		Organ organ = new Organ();
//		organ.setOrganizeCode("1111111111");
//		organ.setName("江干区卫生局");
//		organ.setShortName("江干区卫生局");
//		organ.setType("9");
//		organ.setGrade("30");
//		organ.setAddrArea("330104");
		organ.setManageUnit("");
		organ.setOrganId(1000007);
//		organ.setAddress("浙江省桐乡市校场东路1918号");
		Organ organ2 = dao.updateOrgan(organ);
		System.out.println(JSONUtils.toString(organ2));
	}

	public void testGetById() throws DAOException {
		int id = 1;
		String name = dao.getNameById(id);
		System.out.println(JSONUtils.toString(name));
	}

	public void testGetDictionaryItem() {
		System.out.println(JSONUtils.toString(dao.findAllDictionaryItem(9, 7)));
	}

	public void testDictionary() throws ControllerException,
			InterruptedException {
		Dictionary dic = DictionaryController.instance().get(
				"eh.base.dictionary.Organ");
		System.out.println(dic.getText(1));
		Organ o = testCreate();
		TimeUnit.SECONDS.sleep(3);
		System.out.println(dic.getText(o.getOrganId()));
	}

	/**
	 * 测试名:联盟机构查询服务测试
	 * 
	 * @author yxq
	 */
	public void testQueryRelaOrgan() {
		int organId = 1;
		String buesType = "1";
		List<Organ> result = dao.queryRelaOrgan(organId, buesType);
		System.out.println(result.size());
		for (int i = 0; i < result.size(); i++) {
			System.out.println(JSONUtils.toString(result.get(i)));
		}
	}

	
	
	/**
	 * @author Eric
	 * @throws ControllerException
	 */
	public void testGetAddrArea() throws ControllerException {
		Map<Integer, Object> objs = dao.getAddrArea();
		System.out.println(JSONUtils.toString(objs));
	}
	
	/**
	 * 平台机构编号转换成his机构编号
	 * @throws ControllerException
	 */
	public void testGetOrganizeCodeByOrganId() throws ControllerException {
		int organId=1;
		String organizeCode =dao.getOrganizeCodeByOrganId(organId);
		System.out.println(organizeCode);
	}
	
	/**
	 * his机构编号转换成平台机构编号
	 * @throws ControllerException
	 */
	public void testGetOrganIdByOrganizeCode() throws ControllerException {
		String organizeCode="33013764";
		int organId =dao.getOrganIdByOrganizeCode(organizeCode);
		System.out.println(organId);
	}

	/**
	 * 机构筛选
	 * @author LF
	 * @return
	 */
	public void testFindOrgansByDefParam() throws DAOException {
		Organ organ = new Organ();
		organ.setManageUnit("eh002");
		organ.setAddrArea("330105");
		organ.setName("江干区");
		organ.setGrade("30");
		organ.setType("1");
		System.out.println(JSONUtils.toString(dao.findOrgansByDefParam(organ, 0)));
	}
	
	/**
	 * 获取本月新增
	 * @author ZX
	 * @date 2015-5-22  下午4:57:43
	 */
	public void testGetOrganNumByMonth(){
		String manageUnit="eh";
		long num=dao.getOrganNumByMonth(manageUnit);
		System.out.println(num);
	}
	
	/**
	 * 获取昨日新增
	 * @author ZX
	 * @date 2015-5-22  下午5:05:27
	 */
	public void testGetOrganNumByYestoday(){
		String manageUnit="eh";
		long num=dao.getOrganNumByYesterday(manageUnit);
		System.out.println(num);
	}
	
	/**
	 * 获取当前管理机构数量
	 * @author ZX
	 * @date 2015-5-22  下午5:05:27
	 */
	public void testGetAllOrganNumWithManager(){
		String manageUnit="eh";
		long num=dao.getAllOrganNumWithManager(manageUnit);
		System.out.println(num);
	}
	
	public void testGetByManageUnit(){
		String manageUnit="eh001";
		Organ o=dao.getByManageUnit(manageUnit);
		System.out.println(JSONUtils.toString(o));
	}
	
	public void testGetId(){
		System.out.println(JSONUtils.toString(dao.get(1)));
	}
	/**
	 * 联盟机构查询服务
	 * @author zhangx
	 * @date 2015-10-23 上午11:25:25
	 */
	public void testQueryRelaOrganNew(){
		List<Organ> result=dao.queryRelaOrganNew(1, "1", "33");
		System.out.println(result.size());
		for (int i = 0; i < result.size(); i++) {
			System.out.println(JSONUtils.toString(result.get(i)));
		}
	}
	
	/**
	 * 联盟机构查询服务(添加按姓名)
	 * 
	 * @author luf
	 */
	public void testQueryRelaOrganAddName() {
		int organId = 1;
		String bus = "1";
		String addr = "";
		String name = "邵";
		List<Organ> result=dao.queryRelaOrganAddName(organId, bus, addr, name);
		System.out.println(result.size());
		for (int i = 0; i < result.size(); i++) {
			System.out.println(JSONUtils.toString(result.get(i)));
		}
	}
	
	/**
	 * 根据名称查询所有有效医院
	 * 
	 * @author luf
	 * @return List<Organ>
	 */
	public void testFindHospitalByNameLike() {
		String name = "省中";
		List<Organ> organs = dao.findHospitalByNameLike(name);
		System.out.println(JSONUtils.toString(organs));
		System.out.println(organs.size());
	}

	public void testFindByAddrAreaLikeForHealth() {
		String addrArea = "3301";
		List<Organ> list = dao.findByAddrAreaLikeForHealth(addrArea);
		System.out.println(JSONUtils.toString(list));
	}

	public void testFindHospitalByNameLikeForHealth() {
		String name = "省中";
		List<Organ> organs = dao.findHospitalByNameLikeForHealth(name);
		System.out.println(JSONUtils.toString(organs));
		System.out.println(organs.size());
	}

	public void testIsExistCheckOrganNameAndShortNameIsExist(){
		String name = "邵逸夫";
		String shortName = "邵逸夫";
		List<Organ> organs = dao.queryOrganByName(name);
		List<Organ> organs2 = dao.queryOrganByShortName(shortName);
		for (Organ organ:organs) {
			System.out.println("OrganizeCode:"+organ.getOrganizeCode()+",name:"+organ.getName()+",organShort:"+organ.getShortName());
		}
		for (Organ organ:organs2) {
			System.out.println("OrganizeCode:"+organ.getOrganizeCode()+",name:"+organ.getName()+",organShort:"+organ.getShortName());
		}
		boolean houxr=dao.checkOrganIsExistByName(name);
		boolean houxr2=dao.checkOrganIsExistByShortName(shortName);
		assertEquals(true,houxr);
		assertEquals(true,houxr2);
		if(houxr&&houxr2){
			System.out.println("该机构不存在!");
		}else {
			System.out.println("该机构存在!");
		}
	}
	public void testIsExistCheckOrganShortName(){
		String shortName = "邵逸夫";
		List<Organ> organs = dao.queryOrganByShortName(shortName);
		for (Organ organ:organs) {
			System.out.println("OrganizeCode:"+organ.getOrganizeCode()+",name:"+organ.getName()+",organShort:"+organ.getShortName());
		}
		boolean flag=dao.checkOrganIsExistByShortName(shortName);
		assertEquals(false,flag);
		if(flag){
			System.out.println("该机构不存在!");
		}else {
			System.out.println("该机构存在!");
		}
	}

	public void testCheckOrganIsExistByManageUnit(){
		String manageUnit = "eh330001123222";
		Boolean myflag=dao.checkOrganIsExistByManageUnit(manageUnit);
		if(myflag){
			System.out.println("管理单元:"+manageUnit+":对应的机构不存在!");
		}else{
			System.out.println("管理单元:"+manageUnit+":对应的机构已存在!");
		}
	}

	public void testgetOgranAddrArea() {
	    try {
            Organ organ = dao.getOgranAddrArea(1000006);
            Organ addressOrgan=dao.getByOrganId(1000006);
            String province = DictionaryController.instance().get("eh.base.dictionary.AddrArea").getText(organ.getAddrArea());
            String city = DictionaryController.instance().get("eh.base.dictionary.AddrArea").getText(organ.getCity());
            String address = addressOrgan.getAddress();
            System.out.println("province:" + province+",city:"+city+",address:"+address);
            System.out.println(organ.getAddrArea() + ":" + organ.getCity() + ":" + organ.getAddrArea());
            System.out.println(JSONUtils.toString(dao.getOgranAddrArea(1)));
            System.out.println(JSONUtils.toString(dao.getOgranAddrArea(1000080)));
            System.out.println(JSONUtils.toString(dao.getOgranAddrArea(1000093)));
        }catch (Exception e){
            e.printStackTrace();
        }
    }

	/**
	 * 供前端调取医院列表(过滤不能查询缴费信息的医院)
	 */
	public void testFindByAddrAreaAndPaymentLike(){
		List<Organ> organs = dao.findByAddrAreaAndPaymentLike("330104");
		System.out.println(organs.size());
	}

}
