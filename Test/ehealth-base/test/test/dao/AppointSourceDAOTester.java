package test.dao;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import ctd.schema.Schema;
import ctd.schema.SchemaController;
import ctd.schema.SchemaItem;
import eh.entity.bus.*;
import eh.entity.his.hisCommonModule.HisAppointSchedule;
import eh.task.executor.SaveHisAppointSourceExecutor;
import junit.framework.TestCase;

import org.joda.time.LocalDate;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.controller.exception.ControllerException;
import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import ctd.util.converter.support.StringToDate;
import eh.bus.dao.AppointSourceDAO;
import eh.entity.base.Doctor;
import eh.utils.DateConversion;

public class AppointSourceDAOTester extends TestCase {
	private static ClassPathXmlApplicationContext appContext;
	private static AppointSourceDAO bdd;

	static {
		appContext = new ClassPathXmlApplicationContext("spring.xml");
		bdd = appContext.getBean("appointSourceDAO", AppointSourceDAO.class);
	}

	public  void testGetFiles() {
		AppointmentResponse res = new AppointmentResponse();
		List<Map<String, String>> resList = getFields(res);

	}

	public void testSetValues(){
		AppointmentResponse res = new AppointmentResponse();
		res.setAppointID("zxqxxxxxxx");
		setValue("appointID","ttttt",res);
	}

	public void setValue(String key,Object value,Object o){
		Class tt = o.getClass();
		try {
			Field field = tt.getDeclaredField(key);
			Class<?> type = field.getType();
			Method method_get = tt.getMethod("getAppointID");
			method_get.invoke(o);
			Method method_set = tt.getMethod("setAppointID",type);
			method_set.invoke(o,value);
			System.out.println(o);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private List<Map<String, String>> getFields(Object res){
		Class tt = res.getClass();
		Field[] fields = tt.getDeclaredFields();
		List<Map<String,String>> resList = new ArrayList<>();
		try {
			Schema schema = SchemaController.instance().get(tt.getName());
			for (Field f : fields){
				Map<String,String> s = new HashMap<>();
				String fieldName = f.getName();
				s.put("key",fieldName);
				Class<?> type = f.getType();
				s.put("type",type.getName());
				SchemaItem file = schema.getItem(fieldName);
				if(file==null){
					s.put("alias","");
					continue;
				}
				String alias = file.getAlias();
				s.put("alias",alias);
				System.out.println(s);
				resList.add(s);
			}
			return resList;
		} catch (ControllerException e) {
			e.printStackTrace();
		}
		return resList;
	}

	public void testHHH(){
		String r = "{\"organId\":1000839,\"organSourceId\":\"20170605|678|2|18\",\"organSchedulingId\":\"678\",\"appointDepartCode\":\"4307\",\"appointDepartName\":\"中医内科\",\"doctorId\":20403,\"workDate\":\"2017-06-05 00:00:00\",\"workType\":2,\"sourceType\":1,\"sourceLevel\":2,\"price\":19.0,\"startTime\":\"2017-06-05 00:00:00\",\"endTime\":\"2017-06-05 23:59:00\",\"sourceNum\":1,\"usedNum\":0,\"orderNum\":18,\"stopFlag\":0,\"createDate\":\"2017-06-05 20:26:11\",\"cloudClinic\":0,\"modifyDate\":\"2017-06-05 20:26:11\",\"fromFlag\":0,\"doctorIdText\":\"文天鹰\",\"organIdText\":\"萍乡市妇幼保健院\",\"cloudClinicTypeText\":\"\",\"fromFlagText\":\"His系统采集的号源\",\"stopFlagText\":\"正常\",\"cloudClinicText\":\"线下门诊\",\"workTypeText\":\"下午\",\"sourceTypeText\":\"普通预约号\",\"sourceLevelText\":\"专家门诊\"}";
		AppointSource s = JSONUtils.parse(r, AppointSource.class);
//		AppointSource ss = bdd.save(s);
		List<AppointSource> list = new ArrayList<>();
		list.add(s);
		SaveHisAppointSourceExecutor executor = new SaveHisAppointSourceExecutor();
		executor.execute(list);
		try {
			Thread.currentThread().sleep(1000000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	public void testUpdateStopFlagForDoctorAndSendSMS(){
		HisAppointSchedule s = new HisAppointSchedule();
		s.setOrganID(1000690);
//		s.setDoctorID(3511);
//		s.setWorkDate(DateConversion.getCurrentDate("2017-05-17",DateConversion.YYYY_MM_DD));
//		s.setWorkType(1);
		s.setSchedulingID("33715");
//		String param = "{\"organID\":1000690,\"workDate\":\"2017-06-10 00:00:00\",\"workType\":2}";
//		HisAppointSchedule ss= JSONUtils.parse(param,HisAppointSchedule.class);
		bdd.updateStopFlagForDoctorAndSendSMS(s);
	}

	/**
	 * 服务名:查询医生分时段号源服务测试
	 * 
	 * @author:yxq
	 */
	public void testQueryDoctorSource() throws DAOException {
		// 医生编号（云平台主键）
		int doctorId = 1377;
		// 号源类型（1：普通预约号；2：内部预约号；3：转诊预约号）前台默认传入为2
		int sourceType = 1;

		String work = "2016-02-24 00:00:00";
		Date workDate = new Date();
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		try {
			// 工作日期
			workDate = df.parse(work);
		} catch (Exception e) {
			e.printStackTrace();
		}
		// 值班类别（1：上午；2：下午；3：晚上)
		int workType = 1;
		// 机构
		int organID = 1;
		// 科室代码
		String appointDepartCode = "239";

		Double price = 3d;

		List<AppointSource> result = bdd.queryDoctorSource(doctorId,
				sourceType, workDate, workType, organID, appointDepartCode,
				price);
		for (int i = 0; i < result.size(); i++) {
			System.out.println(JSONUtils.toString(result.get(i)));
		}
	}

	/**
	 * 可用接诊时段号源查询服务
	 * 
	 * @author LF
	 * @return
	 */
	public void testQueryDoctorSourceCloud() {
		String inAddrArea = "523924";
		// Integer inOrganId = 1;
		Integer inOrganId = null;
		Integer outDoctorId = 2011;
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
		Date outWorkDate = null;
		try {
			outWorkDate = dateFormat.parse("2015-10-24");
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Integer workType = 0;
		System.out.println(JSONUtils.toString(bdd.queryDoctorSourceCloud(
				inAddrArea, inOrganId, outDoctorId, outWorkDate, workType)));
	}

	/**
	 * 查询医生日期号源服务
	 * 
	 * @throws DAOException
	 */
	public void testTotalByDoctorDate() throws DAOException {
		int doctorId = 1387;
		int sourceType = 2;
		List<DoctorDateSource> list = bdd.totalByDoctorDate(doctorId,
				sourceType);
		for (int i = 0; i < list.size(); i++) {
			System.out.println(JSONUtils.toString(list.get(i)));
		}
	}

	/**
	 * 查询医生日期号源服务
	 * 
	 * test.dao
	 * 
	 * @author luf 2016-1-29
	 * 
	 *         void
	 */
	public void testTotalByDoctorDateForHealth() {
		int doctorId = 8468;
		int sourceType = 1;
		List<Map<String, Object>> list;
		try {
			list = bdd.totalByDoctorDateForHealth(doctorId, sourceType);
			// for (int i = 0; i < list.size(); i++) {
			System.out.println(JSONUtils.toString(list));
			// }
		} catch (ControllerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * 按医生统计号源服务测试
	 * 
	 * @throws DAOException
	 */
	public void testFindTotalByDoctor() throws DAOException {
		int oragnID = 1;
		String appointDepartCode = "1";
		Integer sourceType = 1;
		List<DoctorSource> list = bdd.findTotalByDoctor(oragnID,
				appointDepartCode, sourceType);
		for (int i = 0; i < list.size(); i++) {
			System.out.println(JSONUtils.toString(list.get(i)));
		}
	}

	/**
	 * 预约号源增加服务
	 * 
	 * @throws DAOException
	 */
	public void testSaveAppointSource() throws DAOException {
		AppointSource appointSource = new AppointSource();
		appointSource.setOrganId(1);
		appointSource.setAppointDepartCode("0102");
		Date date = new Date();
		DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		String dateStr = sdf.format(date);
		Timestamp ts = new Timestamp(System.currentTimeMillis());
		ts = Timestamp.valueOf(dateStr);
		appointSource.setWorkDate(ts);
		appointSource.setWorkType(1);
		appointSource.setSourceType(1);
		appointSource.setStartTime(ts);
		appointSource.setEndTime(ts);
		appointSource.setOrderNum(1);
		appointSource.setSourceNum(3);
		appointSource.setPrice(16.0);
		appointSource.setDoctorId(1292);
		appointSource.setAppointDepartName("呼吸内科");
		bdd.saveAppointSource(appointSource);

	}

	public void testUpdateUsedNum() throws DAOException {
		int usedNum = 1;
		int orderNum = 1;
		int appointSourceId = 1;
		bdd.updateUsedNum(usedNum, orderNum, appointSourceId);
	}

	public void testGetAppointSource() {
		Date d = getCurrentDate("2015-06-11", "yyyy-MM-dd");
		AppointSource as = bdd.getAppointSource(1, "12787",
				"20150611|12787|1|15", d);

		if (as != null)
			System.out.println(JSONUtils.toString(as));

	}

	public void testSaveOrUpdate() {
		Date d = getCurrentDate("2015-5-21", "yyyy-MM-dd");
		AppointSource as = bdd.getAppointSource(1, "12765", "150371", d);
		as.setWorkDate(getCurrentDate("2015-5-6", "yyyy-MM-dd"));
		if (as != null)
			System.out.println(JSONUtils.toString(as));
		// as.setSourceNum(6);
		// as.setAppointSourceId(null);
		// as.setOrganId(2);
		bdd.saveOrUpdate(as);

	}

	public void testUpdateUsedNumAfterCancel() {
		int usedNum = 2;
		int appointSourceId = 1;
		bdd.updateUsedNumAfterCancel(usedNum, appointSourceId);
	}

	/**
	 * 根据时间字符串 和指定格式 返回日期
	 * 
	 * @param dateStr
	 * @param format
	 * @return
	 */
	public static Date getCurrentDate(String dateStr, String format) {
		Date currentTime = new Date();
		SimpleDateFormat formatter = new SimpleDateFormat(format);
		try {
			currentTime = formatter.parse(dateStr);
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			return null;
		}
		return currentTime;
	}

	/**
	 * 测试 预约该doctor 当天 是否存在排班
	 */
	public void testCheckIsExistScheduling() {
//		bdd.checkIsExistScheduling(625, 1,
//				getCurrentDate("2015-04-28", "yyyy-MM-dd"));
	}

	/**
	 * 按日期期限查询医生是否有号源
	 * 
	 * @author hyj
	 */
	public void testCheckAppointSourceByWorkDateKey() {
		int doctorId = 385;
		int sourceType = 2;
		int workDateKey = 3;
		Doctor d = bdd.checkAppointSourceByWorkDateKey(doctorId, sourceType,
				workDateKey);
		System.out.println(JSONUtils.toString(d));
	}

	/**
	 * 查询云诊室标志 =0的号源数量
	 * 
	 * @author hyj
	 */
	public void testTotalByOrdinary() {
		int doctorId = 2051;
		int sourceType = 1;
		Date date = new StringToDate().convert("2015-07-12");
		Long count = bdd.totalByOrdinary(doctorId, sourceType, date);
		System.out.println(count);
	}

	/**
	 * 查询云诊室标志 =1并且云诊室类别=0或者1的号源数量
	 * 
	 * @author hyj
	 */
	public void testTotalByCloud() {
		int doctorId = 2051;
		int sourceType = 1;
		Date date = new StringToDate().convert("2015-07-12");
		Long count = bdd.totalByCloud(doctorId, sourceType, date);
		System.out.println(count);
	}

	/**
	 * 查询云诊室标志 =2并且云诊室类别=0或者1的号源数量
	 * 
	 * @author hyj
	 */
	public void testTotalByOrdinaryAndCloud() {
		int doctorId = 2051;
		int sourceType = 1;
		Date date = new StringToDate().convert("2015-07-12");
		Long count = bdd.totalByOrdinaryAndCloud(doctorId, sourceType, date);
		System.out.println(count);
	}

	/**
	 * 查询医生日期号源服务--新增云门诊判断
	 * 
	 * @author hyj
	 */
	public void testTotalByDoctorDateAndCloudClinic() {
		int doctorId = 1387;
//		int doctorId = 1895;
		int sourceType = 2;
		List<DoctorDateSource> list = bdd.totalByDoctorDateAndCloudClinic(
				doctorId, sourceType);
		for (int i = 0; i < list.size(); i++) {
			System.out.println(JSONUtils.toString(list.get(i)));
		}
		System.out.println(list.size());
	}

	public void testCheckAndUpateHaveAppoint() {
		bdd.checkAndUpateHaveAppoint();
	}

	public void testFindByOrganIdAndOrganSchedulingId() {
		int organId = 1;
		String organSchedulingId = "188";
		List<AppointSource> list = bdd.findByOrganIdAndOrganSchedulingId(
				organId, organSchedulingId);
		for (AppointSource a : list) {
			System.out.println(JSONUtils.toString(a));
		}
	}

	/**
	 * 判断就诊方是否有排班
	 * 
	 * @author LF
	 */
	public void testHaveSchedulingOrNot() {
		int organId = 1;
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		Date workDate = null;
		try {
			workDate = sdf.parse("2015-09-12");
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		List<AppointSource> list = bdd.findByOrganIdAndWorkDate(organId,
				workDate);
		Boolean b = bdd.haveSchedulingOrNot(organId, workDate);
		System.out.println(b);
		System.out.println(JSONUtils.toString(list));
	}

	public void testGetExistSchedulingSource() {
//		bdd.getExistSchedulingSource(3501, 1,
//				DateConversion.getCurrentDate("2015-10-02", "yyyy-MM-dd"));
	}

	/**
	 * 分页查询号源服务
	 * 
	 * @author luf
	 */
	public void testFindSourcesByFive() {
		int organId = 1;
		int doctorId = 40;
		Date startDate = DateConversion.getCurrentDate("2015-10-14",
				"yyyy-MM-dd");
		Date endDate = DateConversion.getDateOfWeekNow(startDate);
		System.out.println(organId + "," + doctorId + ","
				+ startDate.toString() + "," + endDate.toString());
		List<AppointSource> as = bdd.findSourcesByFive(organId, doctorId,
				startDate, endDate);
		System.out.println(JSONUtils.toString(as));
		System.out.println(as.size());
	}

	/**
	 * 获取该医生该天的号源信息列表服务
	 * 
	 * @author luf
	 */
	@SuppressWarnings("unchecked")
	public void testFindOneDocSourcesByOneDate() {
		int organId = 1;
		int doctorId = 40;
		Date dateNow = DateConversion
				.getCurrentDate("2015-10-22", "yyyy-MM-dd");
		System.out.println(organId + "," + doctorId + "," + dateNow.toString());
		Hashtable<String, Object> as = bdd.findOneDocSourcesByOneDate(organId,
				doctorId, dateNow);
		System.out.println(JSONUtils.toString(as));
		System.out.println(((List<AppointSource>) as.get("appointSources"))
				.size());
	}

	/**
	 * 号源信息列表服务
	 * 
	 * @author luf
	 */
	public void testFindDocAndSourcesBySix() {
		int organId = 1;
		int department = 70;
		String profession = "03";
		String name = "卢";
		int start = 0;
		Date startDate = DateConversion.getCurrentDate("2015-10-14",
				"yyyy-MM-dd");
		List<Doctor> ds = bdd.findDocAndSourcesBySix(organId, profession,
				department, name, start, startDate);
		// for(Doctor d:ds) {
		// System.out.println(JSONUtils.toString(d));
		// }
		System.out.println(JSONUtils.toString(ds));
		System.out.println(ds.get(0).getAppointSources().size());
		System.out.println(ds.size());
	}

	/**
	 * 出/停诊服务
	 * 
	 * @author luf
	 */
	public void testUpdateSourcesStopOrNot() {
		List<Integer> is = new ArrayList<Integer>();
		is.add(586746);
		is.add(-1);
		is.add(0);
		is.add(null);
		is.add(586746000);
		System.out.println(JSONUtils.toString(is));
		int i = bdd.updateSourcesStopOrNot(is, 1);
		System.out.println(i);
	}

	/**
	 * 修改单条号源信息
	 * 
	 * @author luf
	 */
	public void testUpdateOneSource() {
		AppointSource a = new AppointSource();
		a.setAppointSourceId(586746);
		// a.setDoctorId(40);
		// a.setOrganId(1);
		a.setSourceNum(1);
		System.out.println(JSONUtils.toString(a));
		Boolean b = bdd.updateOneSource(a);
		System.out.println(b);
	}

	/**
	 * 删除单条/多条号源
	 * 
	 * @author luf
	 */
	public void testDeleteOneOrMoreSource() {
		List<Integer> ids = new ArrayList<Integer>();
		ids.add(586761);
		ids.add(586761000);
		ids.add(null);
		ids.add(0);
		ids.add(-1);
		Integer i = bdd.deleteOneOrMoreSource(ids);
		System.out.println(i);
	}

	/**
	 * 新增号源信息
	 * 
	 * @author luf
	 */
	public void testAddOneSource() {
		AppointSource a = new AppointSource();
		a.setDoctorId(40);
		a.setOrganId(1);
		a.setSourceNum(3);
		a.setSourceType(0);
		a.setWorkType(4);
		a.setStartNum(50);
		a.setStartTime(DateConversion.getCurrentDate("2016-10-22 09:15:00",
				"yyyy-MM-dd HH:mm:ss"));
		a.setEndTime(DateConversion.getCurrentDate("2016-10-22 10:15:00",
				"yyyy-MM-dd HH:mm:ss"));
		a.setWorkDate(DateConversion.getCurrentDate("2016-10-22", "yyyy-MM-dd"));
		a.setAppointDepartCode("54");
		System.out.println(JSONUtils.toString(a));
		List<Integer> is = bdd.addOneSource(a);
		System.out.println(JSONUtils.toString(is));
	}

	public void testFromScheduleToSource() {
		bdd.fromScheduleToSource();
	}

	/**
	 * 查询医生日期号源服务--新增云门诊判断
	 * 
	 * @author luf
	 * @param doctorId
	 *            --医生编号
	 * @param sourceType
	 *            --号源类别
	 * @return List<AppointSource>
	 */
	public void testQueryTotalByDoctorDateAndCloud() {
		int doctorId = 40;
		int sourceType = 2;
		List<AppointSource> acs = bdd.queryTotalByDoctorDateAndCloud(doctorId,
				sourceType);
		System.out.println(JSONUtils.toString(acs));
		System.out.println(acs.size());
	}

	public void testDoSourceByErrCodeAndSourceId() {
		System.out.println(JSONUtils.toString(bdd.doSourceByErrCodeAndSourceId(
				2, 669266)));
	}

	/**
	 * 查询医生日期号源服务--新增云门诊判断(输出格式改变)
	 * 
	 * @author luf
	 * @param doctorId
	 *            --医生编号
	 * @param sourceType
	 *            --号源类别
	 * @return List<HashMap<String, Object>>
	 * @throws ControllerException
	 */
	public void testConvertTotalForIOS() throws ControllerException {
//		int doctorId = 40;
		int doctorId = 1377;
		int sourceType = 2;
		System.out.println(JSONUtils.toString(bdd.convertTotalForIOS(doctorId,
				sourceType)));
	}

	/**
	 * 可用接诊时段号源查询服务
	 * 
	 * @author luf
	 * @param inAddrArea
	 *            接诊区域地址
	 * @param inOrganId
	 *            接诊机构内码
	 * @param outDoctorId
	 *            出诊医生内码
	 * @param outWorkDate
	 *            出诊工作日期
	 * @param workType
	 *            值班类别
	 * @return Map<String, Object>
	 * @throws Exception
	 */
	public void testQueryDoctorSourceCloudConvertOut() {
		// String inAddrArea = "523924";
		String inAddrArea = null;
		// Integer inOrganId = 1;
		Integer inOrganId = 1;
		Integer outDoctorId = 40;
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
		Date outWorkDate = null;
		try {
			outWorkDate = dateFormat.parse("2015-11-27");
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Integer workType = 0;
		System.out.println("queryDoctorSourceCloud===>"
				+ JSONUtils.toString(bdd.queryDoctorSourceCloud(inAddrArea,
						inOrganId, outDoctorId, outWorkDate, workType)));
		try {
			System.out.println("QueryDoctorSourceCloudConvertOut===>"
					+ JSONUtils.toString(bdd.queryDoctorSourceCloudConvertOut(
							inAddrArea, inOrganId, outDoctorId, outWorkDate,
							workType)));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * 查询医生分时段号源服务
	 * 
	 * @author csy
	 * @param inAddrArea
	 *            接诊区域地址
	 * @param inOrganId
	 *            接诊机构内码
	 * @param outDoctorId
	 *            出诊医生内码
	 * @param outWorkDate
	 *            出诊工作日期
	 * @param workType
	 *            值班类别
	 * @return Map<String, Object>
	 * @throws Exception
	 */
	public void testTotalByDoctorDateAndWorkDate() {
		int doctorId = 1178;
		int sourceType = 1;
		int organId = 1;
		String appointDepartCode = "03";
		int isWeek = 0;
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
		Date outWorkDate = null;
		try {
			outWorkDate = dateFormat.parse("2016-01-11");
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("totalByDoctorDateAndWorkDate===>"
				+ JSONUtils.toString(bdd.totalByDoctorDateAndWorkDate(doctorId,
						sourceType, outWorkDate, organId, appointDepartCode,
						isWeek)));
		try {
			System.out.println("totalByDoctorDateAndWorkDate===>"
					+ JSONUtils.toString(bdd.totalByDoctorDateAndWorkDate(
							doctorId, sourceType, outWorkDate, organId,
							appointDepartCode, isWeek)));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * 查询医生分时段号源服务(省中支付宝用)
	 * 
	 * @author csy
	 * @param inAddrArea
	 *            接诊区域地址
	 * @param inOrganId
	 *            接诊机构内码
	 * @param outDoctorId
	 *            出诊医生内码
	 * @param outWorkDate
	 *            出诊工作日期
	 * @param workType
	 *            值班类别
	 * @return Map<String, Object>
	 * @throws Exception
	 */
	public void testTotalByDoctorDateAndWorkDateSz() {
		int doctorId = 3609;
		int sourceType = 1;
		int organId = 1000024;
		int isWeek = 0;
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
		Date outWorkDate = null;
		try {
			outWorkDate = dateFormat.parse("2015-11-10");
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("totalByDoctorDateAndWorkDate===>"
				+ JSONUtils.toString(bdd.totalByDoctorDateAndWorkDateSz(
						doctorId, sourceType, outWorkDate, organId, isWeek)));
		try {
			System.out
					.println("totalByDoctorDateAndWorkDate===>"
							+ JSONUtils.toString(bdd
									.totalByDoctorDateAndWorkDateSz(doctorId,
											sourceType, outWorkDate, organId,
											isWeek)));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void testC() {
		List<SourceAllot> allots = new ArrayList<SourceAllot>();
		for (int i = 0; i < 3; i++) {
			SourceAllot allot = new SourceAllot();
			allot.setSourceNum(i);
			allots.add(allot);
		}
		System.out.println(">>>" + JSONUtils.toString(allots));
		allots.remove(1);
		System.out.println(">+++>" + JSONUtils.toString(allots));
		/*
		 * int i =0; while (allots.get(i).getSourceNum()>1000) {
		 * System.out.println(">++ss+>"+JSONUtils.toString(allots.get(i)));
		 * break; }
		 */
	}

	public void testTime() {
		int max = 30;
		Date now1 = new Date();// DateConversion.getCurrentDate("2015-11-09",
								// "yyyy-MM-dd");
		long dTime = max * 1000 * 60 * 60 * 24;
		long nowTime = now1.getTime();
		long maxDateTime = nowTime + dTime;
		System.out.println(now1);
		System.out.println(now1.getTime());
		System.out.println(new Date(maxDateTime));
		System.out.println(maxDateTime);

		LocalDate dt = new LocalDate();
		Date now = dt.toDate();
		System.out.println(now);
		Date maxdt = dt.plusDays(max).toDate();// dt.minusDays(max).toDate();
		System.out.println(maxdt);
		System.out.println(maxdt.getTime());
	}

	/**
	 * 查询医生分时段号源服务(省中支付宝用)
	 * 
	 * @author csy
	 * @param inAddrArea
	 *            接诊区域地址
	 * @param inOrganId
	 *            接诊机构内码
	 * @param outDoctorId
	 *            出诊医生内码
	 * @param outWorkDate
	 *            出诊工作日期
	 * @param workType
	 *            值班类别
	 * @return Map<String, Object>
	 * @throws Exception
	 */
	public void testCheckDoctorSourceForWeek() {
		int doctorId = 1883;
		int organId = 1;
		String appointDepartCode = "12";
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
		Date startDay = null;
		Date endDay = null;
		try {
			startDay = dateFormat.parse("2016-01-20");
			endDay = dateFormat.parse("2016-01-27");
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("totalByDoctorDateAndWorkDate===>"
				+ JSONUtils.toString(bdd.checkDoctorSourceForWeek(doctorId,
						startDay, endDay, organId, appointDepartCode)));

	}
	
	public void testUpdateDocHaveAppoint(){
		bdd.updateDocHaveAppoint();
	}
	/**
	 * 添加普通号源
	 */
	public void testCreateAppointSource(){
		bdd.createAppointSource(3511);
	}
	/**
	 * 添加云门诊号源
	 */
	public void testCreateAppointSourceAndCloudClinic(){
		bdd.createAppointSourceAndCloudClinic(2006);
	}
	/**
	 * 供 查询号源医生列表接口(添加范围) 调用
	 * 
	 * eh.bus.dao
	 * 
	 * @author luf 2016-3-4
	 * 
	 * @param organId
	 *            机构内码
	 * @param department
	 *            科室代码
	 * @param name
	 *            医生姓名
	 * @param range
	 *            范围- 0只查无号源医生，1只查有号源医生，-1查询全部医生
	 * @param start
	 *            分页起始位置
	 * @return List<Doctor>
	 */
	public void testFindDocsWithRange() {
		int organId = 1;
		Integer department = null;
		String name = "卢芳";
		int range = 1;
		int start = 0;
		Date startDate = DateConversion.getCurrentDate("2016-12-1",
				"yyyy-MM-dd");
		Date endDate = DateConversion
				.getCurrentDate("2016-12-05", "yyyy-MM-dd");
		System.out.println(JSONUtils.toString(bdd.findDocsWithRange(organId,
				department, name, range, startDate, endDate, start)));
	}

	public void testFindDocAndSourcesWithRange() {
		int organId = 1;
		Integer department = 70;
		String name = "卢";
		int range = 1;
		int start = 0;
		Date startDate = DateConversion.getCurrentDate("2016-11-29",
				"yyyy-MM-dd");
		List<Doctor> ds = bdd.findDocAndSourcesWithRange(organId, department,
				name, range, startDate, start);
		// for(Doctor d:ds) {
		// System.out.println(JSONUtils.toString(d));
		// }
		System.out.println(JSONUtils.toString(ds));
		if (ds != null && ds.size() > 0) {
			System.out.println(ds.get(0).getAppointSources().size());
			System.out.println(ds.size());
		}

	}

	public void testTotalByDoctorDateCloud() {
		int doctorId = 9537;
//		int doctorId = 1895;
		int sourceType = 2;
		List<DoctorDateSource> list = bdd.totalByDoctorDateCloud(
				doctorId, sourceType);
		System.out.println(JSONUtils.toString(list));
//		for (int i = 0; i < list.size(); i++) {
//			System.out.println(JSONUtils.toString(list.get(i)));
//		}
		System.out.println(list.size());
	}

	public void testQueryCloudWithoutTab() {
		System.out.println(JSONUtils.toString(bdd.queryCloudWithoutTab(null,1,9537,DateConversion.getCurrentDate("016-11-16","yyyy-MM-dd"),2,9568)));
	}



	public void testQueryCloudClincForPc(){
		String inAddrArea = "";
		int organId = 100017;
		int outDoctorId = 1425;
		Date outWorkDate = DateConversion.getCurrentDate("2016-08-31","yyyy-MM-dd");
		int workType = 2;
		int doctorId = 9538;
	}
}
