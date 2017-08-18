package test.dao;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import eh.entity.his.hisCommonModule.HisResponse;
import junit.framework.TestCase;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.StatelessSession;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.persistence.DAOFactory;
import ctd.util.JSONUtils;
import eh.base.dao.CheckItemDAO;
import eh.base.dao.OrganCheckItemDAO;
import eh.bus.dao.CheckRequestDAO;
import eh.bus.dao.CheckSourceDAO;
import eh.entity.base.CheckItem;
import eh.entity.base.OrganCheckItem;
import eh.entity.bus.CheckRequest;
import eh.entity.bus.CheckSource;

public class CheckRequestDAOTester extends TestCase {

	private static final Log logger = LogFactory.getLog(CheckRequestDAO.class);

	private static ClassPathXmlApplicationContext appContext;
	static {
		appContext = new ClassPathXmlApplicationContext("spring.xml");
	}
	private CheckRequestDAO dao = appContext.getBean("checkRequestDAO",
			CheckRequestDAO.class);

	public void testfindTodayCheckListByMpiid(){
//		dao.test();
	}
	public void testRequestCheck() {
		OrganCheckItem item = DAOFactory.getDAO(OrganCheckItemDAO.class).get(2);
		CheckSource source = DAOFactory.getDAO(CheckSourceDAO.class).get(6);
		CheckItem checkItem = DAOFactory.getDAO(CheckItemDAO.class).get(2);
		CheckRequest cr = new CheckRequest();
		cr.setMpiid("2c9081814cc3ad35014cc54fca420003");
		cr.setPatientName("项叶峰");
		cr.setPatientSex("1");
		cr.setPatientType("1");
		cr.setCertId("33252819930131041X");
		cr.setMobile("18989475192");
		cr.setDisease("aaaa");
		cr.setDiseasesHistory("bbb");
		cr.setPurpose("cccc");
		cr.setCheckType(checkItem.getCheckClass());
		cr.setExaminationTypeName("CT");
		cr.setCheckItemName(item.getCheckItemName());
		cr.setCheckBody(checkItem.getCheckBody());
		cr.setBodyPartName("胸部");
		cr.setCheckItemId(item.getCheckItemId());
		cr.setOrganId(item.getOrganId());
		cr.setCheckAppointId(item.getCheckAppointId());
		cr.setChkSourceId(source.getChkSourceId());
		cr.setCheckDate(source.getWorkDate());
		cr.setWorkType(source.getWorkType());
		cr.setStartDate(source.getStartTime());
		cr.setEndDate(source.getEndTime());
		cr.setOrderNum(source.getOrderNum());
		cr.setRequestDoctorId(40);
		cr.setRequestOrgan(1);
		cr.setRequestDepartId(70);
		System.out.println(JSONUtils.toString(cr));
		System.out.println(dao.requestCheck(cr));
	}

	/**
	 * 检查预约记录列表服务
	 * 
	 * @param当前登录医生内码
	 * @param分类--0全部1待检查2待出报告3已出报告4已取消
	 * @param标志（全部）--0未完成1已结束
	 * @return List<Object>
	 */
	public void testFindCheckList() {
		int doctorId = 1377;
		int flag = 0;
		int mark = 1;
		List<Object> os = dao.findCheckList(doctorId, flag, mark,0,10);
		System.out.println(JSONUtils.toString(os));
		System.out.println(os.size());
	}

	/**
	 * 供 检查预约记录列表服务 调用
	 * 
	 * @param当前登录医生内码
	 * @param分类--0全部1待检查2待出报告3已出报告4已取消
	 * @param标志（全部）--0未完成1已结束
	 * @return List<CheckRequest>
	 */
	public void testFindCheckRequestsByThree() {
		// try {
		// String appointName = DictionaryController.instance()
		// .get("eh.base.dictionary.CheckRequestStatus")
		// .getText(1);
		// System.out.println(appointName);
		// } catch (ControllerException e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// }

		int doctorId = 40;
		int flag = 4;
		int mark = 1;
		List<CheckRequest> os = dao.findCheckRequestsByFive(doctorId, flag,
				mark,0,10);
		System.out.println(JSONUtils.toString(os));
		System.out.println(os.size());
	}
	
	public void testGetDetailByCheckRequestId() {
		int checkRequestId = 163;
		HashMap<String, Object> map = dao.getDetailByCheckRequestId(checkRequestId,1377);
		System.out.println(JSONUtils.toString(map));
	}
	
	public void testCancelForOverTime() {
		dao.cancelForOverTime();
	}

	/**
	 * 测试根据审核状态和机构ID查询检查单总条数
	 */
	public void testGetTotalNumByCheckStatusAndOrganId(){
		int checkStatus = 0;

		int requestOrgan = 1000317;
		long totalnum = dao.getTotalNumByCheckStatusAndOrganId(checkStatus, requestOrgan);
		System.err.print(totalnum);
	}

	/**
	 * 测试检查单审核平台根据审核状态和机构ID显示检查单列表 带分页
	 */
	public void testGetCheckListByCheckStatusAndRequestOrgan(){
		int checkStatus = 0;
		int requestOrgan = 1000317;
		int start = 0;
		int limit = 10;
		List<HashMap<String, Object>> list = dao.getCheckListByCheckStatusAndRequestOrgan(
				checkStatus, requestOrgan, start, limit
		);
		if (list != null && list.size() > 0){
			for (int i = 0; i < list.size(); i++){
//				System.err.println(list.get(i));
//				System.err.println(list.size());
			}
				System.err.println(list.toString());
		} else {
			System.err.print("无数据！");
		}
	}

	/**
	 * 测试通过检查单号查询检查单详情
	 */
	public void testGetCheckDetailByRequestId(){
		int requestId = 150;
		HashMap<String, Object> hashMap = dao.getCheckDetailByCheckRequestId(requestId);
		if (hashMap != null && hashMap.size() > 0){
			System.err.println(hashMap);
		} else {
			System.err.print("没有此检查单！");
		}
	}

	/**
	 * 测试保存审核结果
	 */
	public void testSaveVerifyResult(){
		int requestId = 152;
		int checkStatus = 2;
		String auditor = "王丁丁";
		String notPassReason = "诊断有问题";
		Boolean falg = dao.saveVerifyResult(requestId, checkStatus, auditor, notPassReason);
		System.err.print(falg);
	}

	/**
	 * 测试自动审核功能
	 */
	public void testAutoVerifyChecklistByOvertime(){
		dao.autoVerifyChecklistByOvertime();
	}

	/**
	 * 纳里平台向远程影像诊断中心进行远程影像诊断申请
	 */
	public void testRemoteImageDiagApply(){
		CheckRequest checkRequest = dao.getByCheckRequestId(338);
		List<String> list = new ArrayList<>();
		list.add("55555");
		list.add("66666");
		list.add("77777");
		logger.info("纳里平台向远程影像诊断中心进行远程影像诊断申请checkRequset入参" + JSONUtils.toString(checkRequest));
		HisResponse hisResponse = dao.remoteImageDiagApply(checkRequest, list);
		logger.info(JSONUtils.toString(hisResponse));
	}
}
