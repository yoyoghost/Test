package test.dao;

import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import eh.bus.service.consult.PatientCancelConsultService;
import eh.bus.service.consult.PatientFinishConsultService;
import junit.framework.TestCase;

import org.apache.axis.message.SAXOutputter;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import eh.bus.dao.ConsultDAO;
import eh.entity.bus.Consult;
import eh.entity.bus.ConsultAndPatient;
import eh.utils.DateConversion;

public class ConsultDAOTester extends TestCase {
    private static ClassPathXmlApplicationContext appContext;

    static {
        appContext = new ClassPathXmlApplicationContext("test/spring.xml");
    }

    public void testPatientFinishGraphicTextConsult() {
        try {
            PatientFinishConsultService service = appContext.getBean("patientFinishConsultService", PatientFinishConsultService.class);
            System.out.println(service.patientFinishGraphicTextConsult(1218));
        } catch (DAOException e) {
            e.printStackTrace();
        }
    }

    public void test(){
        ConsultDAO dao = appContext.getBean("consultDAO", ConsultDAO.class);
        System.out.println(JSONUtils.toString(dao.getConsultInfo(186,1197)));;
    }

    public void testPatientCancelGraphicTextConsult() {
        try {
            PatientCancelConsultService service = appContext.getBean("patientCancelConsultService", PatientCancelConsultService.class);
            System.out.println(service.patientCancelGraphicTextConsult(1218));
        } catch (DAOException e) {
            e.printStackTrace();
        }
    }

    public void testQueryConsultWithPage() throws DAOException {
        int doctorid = 1387;
        boolean GroupFlag = true;
        ConsultDAO dao = appContext.getBean("consultDAO", ConsultDAO.class);
        List<ConsultAndPatient> list = dao.queryConsultWithPage(doctorid,
                GroupFlag, 5);
        System.out.println(list.size());
        for (ConsultAndPatient a : list) {
            System.out.println(JSONUtils.toString(a));
        }
    }

	/*
     * public void testCreate() throws DAOException { ConsultDAO dao =
	 * appContext.getBean("consultDAO", ConsultDAO.class);
	 * 
	 * // int nmr = ThreadLocalRandom.current().nextInt(10000); // int n4 =
	 * ThreadLocalRandom.current().nextInt(1000,9999);
	 * 
	 * Consult r = new Consult(); // r.setConsultId(n4); r.setConsultStatus(0);
	 * r.setConsultDoctor(30); r.setConsultDepart(2); r.setConsultOrgan(4);
	 * r.setLeaveMess(
	 * "前两天感冒了，从昨天晚上开始头控制不住的往后仰，用力弄正后，又开始控制不住的后仰，朋友说是落枕，用白酒揉了后，止住了一会儿，下午又开始了。晚上又控制不住的往下沉。没有觉得哪儿痛，请问这是怎么回事呀？"
	 * ); r.setMpiid("2c9081824cc3552a014cc3a9a0120002"); r.setConsultType(1);
	 * r.setEmergency(1); Date date = new Date(); DateFormat sdf = new
	 * SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); String dateStr =
	 * sdf.format(date); Timestamp ts = new
	 * Timestamp(System.currentTimeMillis()); ts = Timestamp.valueOf(dateStr);
	 * r.setAppointTime(ts); r.setRequestTime(ts); r.setVisitedHospital(1);
	 * r.setAnswerTel("13588114320"); r.setConsultCost(20.0); r.setPayflag(0);
	 * // r.setTradeNo(PayUtil.getConsultOutTradeNo());
	 * r.setOutTradeNo(PayUtil.getConsultOutTradeNo()); r.setPaymentDate(new
	 * Date()); r.setPayWay("07"); System.out.println(dao.requestConsult(r)); //
	 * dao.save(r); }
	 *//**
     * 获取待处理咨询申请单数服务测试
     *
     * @throws DAOException
     */
    /*
	 * public void testGetUnConsultNum() throws DAOException { int doctorid = 1;
	 * boolean GroupFlag = true; long result = 0; ConsultDAO dao =
	 * appContext.getBean("consultDAO", ConsultDAO.class); result =
	 * dao.getUnConsultNum(doctorid, GroupFlag); System.out.println(result); }
	 *//**
     * 查询待处理咨询单列表服务测试
     *
     * @author hyj
     * @throws DAOException
     */
	/*
	 * public void testQueryConsult() throws DAOException { int doctorid = 1178;
	 * boolean GroupFlag = true; ConsultDAO dao =
	 * appContext.getBean("consultDAO", ConsultDAO.class);
	 * List<ConsultAndPatient> list = dao.queryConsult(doctorid, GroupFlag);
	 * System.out.println(list.size()); for (ConsultAndPatient a : list) {
	 * System.out.println(JSONUtils.toString(a)); } }
	 *//**
     * 查询待处理咨询单列表服务测试--分页
     *
     * @author hyj
     * @throws DAOException
     */
	/*
	 * public void testQueryConsultWithPage() throws DAOException { int doctorid
	 * = 1178; boolean GroupFlag = true; ConsultDAO dao =
	 * appContext.getBean("consultDAO", ConsultDAO.class);
	 * List<ConsultAndPatient> list = dao.queryConsultWithPage(doctorid,
	 * GroupFlag, 0); System.out.println(list.size()); for (ConsultAndPatient a
	 * : list) { System.out.println(JSONUtils.toString(a)); } }
	 *//**
     * 咨询开始服务测试--hyj
     *
     * @throws DAOException
     */
	/*
	 * public void testStartConsult() throws DAOException { int ConsultID = 9;
	 * int ExeDoctor = 1; int ExeDepart = 1; int ExeOrgan = 1; ConsultDAO dao =
	 * appContext.getBean("consultDAO", ConsultDAO.class); boolean flag =
	 * dao.startConsult(ConsultID, ExeDoctor, ExeDepart, ExeOrgan);
	 * System.out.println(flag); }
	 *//**
     * 获取咨询单信息服务测试--hyj
     *
     * @throws DAOException
     */
	/*
	 * public void textGetById() throws DAOException { int ConsultID = 300;
	 * ConsultDAO dao = appContext.getBean("consultDAO", ConsultDAO.class);
	 * ConsultAndPatient consult = dao.getConsultAndPatientById(ConsultID);
	 * System.out.println(JSONUtils.toString(consult)); }
	 *//**
     * 查询历史咨询单列表服务测试
     *
     * @author hyj
     * @throws DAOException
     */
	/*
	 * public void textQueryConsultHis() throws DAOException { Date startDate =
	 * new StringToDate().convert("2015-01-06 13:25:28"); Date EndDate = new
	 * Date(); int ExeDoctor = 1178; String MPIID = ""; ConsultDAO dao =
	 * appContext.getBean("consultDAO", ConsultDAO.class);
	 * List<ConsultAndPatient> list = dao.queryConsultHis(startDate, EndDate,
	 * ExeDoctor, MPIID); System.out.println(list.size()); for
	 * (ConsultAndPatient c : list) { System.out.println(JSONUtils.toString(c));
	 * } }
	 *//**
     * 查询历史咨询单列表服务测试--分页
     *
     * @author hyj
     * @throws DAOException
     */
	/*
	 * public void textQueryConsultHisWithPage() throws DAOException { Date
	 * startDate = new StringToDate().convert("2015-01-06 13:25:28"); Date
	 * EndDate = new Date(); int ExeDoctor = 1178; String MPIID = ""; ConsultDAO
	 * dao = appContext.getBean("consultDAO", ConsultDAO.class);
	 * List<ConsultAndPatient> list = dao.queryConsultHisWithPage(startDate,
	 * EndDate, ExeDoctor, MPIID, 0); System.out.println(list.size()); for
	 * (ConsultAndPatient c : list) { System.out.println(JSONUtils.toString(c));
	 * } }
	 *//**
     * 上个月历史咨询单列表查询服务测试
     *
     * @author hyj
     * @throws DAOException
     */
	/*
	 * public void textQueryConsultHisLastMonth() throws DAOException { int
	 * ExeDoctor = 1178; String MPIID = ""; ConsultDAO dao =
	 * appContext.getBean("consultDAO", ConsultDAO.class);
	 * List<ConsultAndPatient> list = dao.queryConsultHisLastMonth(ExeDoctor,
	 * MPIID); System.out.println(list.size()); for (ConsultAndPatient c : list)
	 * { System.out.println(JSONUtils.toString(c)); } }
	 *//**
     * 上个月历史咨询单列表查询服务测试--分页
     *
     * @author hyj
     * @throws DAOException
     */
	/*
	 * public void textQueryConsultHisLastMonthWithPage() throws DAOException {
	 * int ExeDoctor = 1178; String MPIID = ""; ConsultDAO dao =
	 * appContext.getBean("consultDAO", ConsultDAO.class);
	 * List<ConsultAndPatient> list = dao.queryConsultHisLastMonthWithPage(
	 * ExeDoctor, MPIID, 0); System.out.println(list.size()); for
	 * (ConsultAndPatient c : list) { System.out.println(JSONUtils.toString(c));
	 * } }
	 *//**
     * 根据病人姓名查询历史咨询单列表服务测试
     *
     * @author hyj
     * @throws DAOException
     */
	/*
	 * public void textQueryConsultHisByPatientName() throws DAOException { Date
	 * startDate = new StringToDate().convert("2015-01-06 13:25:28"); Date
	 * EndDate = new Date(); int ExeDoctor = 1178; String MPIID = ""; String
	 * patientName = "张肖"; ConsultDAO dao = appContext.getBean("consultDAO",
	 * ConsultDAO.class); List<ConsultAndPatient> list =
	 * dao.queryConsultHisByPatientName( startDate, EndDate, ExeDoctor, MPIID,
	 * patientName); System.out.println(list.size()); for (ConsultAndPatient c :
	 * list) { System.out.println(JSONUtils.toString(c)); } }
	 *//**
     * 根据病人姓名查询历史咨询单列表服务测试--分页
     *
     * @author hyj
     * @throws DAOException
     */
	/*
	 * public void textQueryConsultHisByPatientNameWithPage() throws
	 * DAOException { Date startDate = new
	 * StringToDate().convert("2015-01-06 13:25:28"); Date EndDate = new Date();
	 * int ExeDoctor = 1178; String MPIID = ""; String patientName = "张肖";
	 * ConsultDAO dao = appContext.getBean("consultDAO", ConsultDAO.class);
	 * List<ConsultAndPatient> list = dao
	 * .queryConsultHisByPatientNameWithPage(startDate, EndDate, ExeDoctor,
	 * MPIID, patientName, 0); System.out.println(list.size()); for
	 * (ConsultAndPatient c : list) { System.out.println(JSONUtils.toString(c));
	 * } }
	 *
	 * public void textDeleteById() { ConsultDAO dao =
	 * appContext.getBean("consultDAO", ConsultDAO.class); dao.deleteById(15); }
	 */

    /**
     * 咨询结束服务测试--hyj
     *
     * @throws DAOException
     */
    public void testEndConsult() throws DAOException {
        int ConsultID = 300;
        ConsultDAO dao = appContext.getBean("consultDAO", ConsultDAO.class);
        dao.endConsult(ConsultID);

    }

     /* 获取待转诊、待会诊、待咨询申请单条数服务
     *
     * @throws DAOException
	*/
	/* public void testFirstPageService() throws DAOException { int doctorid =
	 * 36; boolean GroupFlag = true; Map<String, Object> map = new
	 * HashMap<String, Object>(); ConsultDAO dao =
	 * appContext.getBean("consultDAO", ConsultDAO.class); map =
	 * dao.firstPageService(doctorid, GroupFlag);
	 * System.out.println(map.get("UnConsultNum").toString() + "," +
	 * map.get("UnMeetClinicNum").toString() + "," +
	 * map.get("UnTransferNum").toString()); }
	 *//**
     * 咨询申请服务测试
     *
     * @throws DAOException
     */
	/*
	 * public void testRequestConsult() throws DAOException { int nmr =
	 * ThreadLocalRandom.current().nextInt(10000); int n4 =
	 * ThreadLocalRandom.current().nextInt(1000, 9999);
	 * 
	 * Consult r = new Consult(); Date date = new Date(); DateFormat sdf = new
	 * SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); String dateStr =
	 * sdf.format(date); Timestamp ts = new
	 * Timestamp(System.currentTimeMillis()); ts = Timestamp.valueOf(dateStr);
	 * r.setMpiid("123" + n4); r.setConsultType(1); r.setEmergency(1);
	 * r.setRequestMode(1); r.setRequestMpi("402881834b71a24f014b71a254020000");
	 * r.setRequestTime(ts); r.setConsultOrgan(1); r.setConsultDepart(1);
	 * r.setConsultDoctor(1); r.setLeaveMess("test"); r.setAppointTime(ts);
	 * r.setVisitedHospital(2); r.setAnswerTel("137657643" + nmr);
	 * r.setConsultCost((double) n4); ConsultDAO dao =
	 * appContext.getBean("consultDAO", ConsultDAO.class);
	 * dao.requestConsult(r);
	 * 
	 * }
	 *//**
     * 咨询申请服务(新增其他病历文档保存)测试
     *
     * @throws DAOException
     */
	/*
	 * public void testRequestConsultAndCdrOtherdoc() throws DAOException {
	 * 
	 * Consult r = new Consult(); Date date = new Date(); DateFormat sdf = new
	 * SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); String dateStr =
	 * sdf.format(date); Timestamp ts = new
	 * Timestamp(System.currentTimeMillis()); ts = Timestamp.valueOf(dateStr);
	 * r.setMpiid("2c9081814cc3ad35014cc3e0361f0000"); r.setConsultType(1);
	 * r.setEmergency(0); r.setRequestMode(1);
	 * r.setRequestMpi("2c9081824cc3552a014cc3a9a0120002");
	 * r.setRequestTime(ts); r.setConsultOrgan(1); r.setConsultDepart(7);
	 * r.setConsultDoctor(1178); r.setLeaveMess("测试申请"); r.setAppointTime(null);
	 * r.setVisitedHospital(0); r.setAnswerTel("13858043673");
	 * r.setConsultCost(120.0);
	 * 
	 * List<Otherdoc> cdrOtherdocs = new ArrayList<Otherdoc>();
	 * 
	 * Otherdoc cdrOtherdoc = new Otherdoc(); cdrOtherdoc.setDocType("1");
	 * cdrOtherdoc.setDocName("王谨.jpg"); cdrOtherdoc.setDocFormat("13");
	 * cdrOtherdoc.setDocContent(97);
	 * 
	 * Otherdoc cdrOtherdoc1 = new Otherdoc(); cdrOtherdoc1.setDocType("9");
	 * cdrOtherdoc1.setDocName("患者2.jpg"); cdrOtherdoc1.setDocFormat("13");
	 * cdrOtherdoc1.setDocContent(83);
	 * 
	 * cdrOtherdocs.add(cdrOtherdoc); cdrOtherdocs.add(cdrOtherdoc1);
	 * 
	 * ConsultDAO dao = appContext.getBean("consultDAO", ConsultDAO.class);
	 * dao.requestConsultAndCdrOtherdoc(r, cdrOtherdocs); }
	 *//**
     * 咨询取消服务
     *
     * @throws DAOException
     */
	/*
	 * public void testCancelConsult() throws DAOException { int consultid =
	 * 300; String cancelCause = "取消原因"; ConsultDAO dao =
	 * appContext.getBean("consultDAO", ConsultDAO.class);
	 * dao.updateCancelConsult(cancelCause, consultid); }
	 *//**
     * 咨询拒绝服务
     *
     * @author hyj
     * @throws DAOException
     */
	/*
	 * public void testRefuseConsult() throws DAOException { int consultId =
	 * 300; String cancelCause = "拒绝原因1"; ConsultDAO dao =
	 * appContext.getBean("consultDAO", ConsultDAO.class);
	 * dao.refuseConsult(cancelCause, consultId); }
	 *//**
     * 咨询医生查询服务
     *
     * @throws DAOException
     */
	/*
	 * public void testQueryConsultDoctor() throws DAOException { Integer
	 * organId = null; String profession = null; String addrArea = "33"; Integer
	 * online = 1; ConsultDAO dao = appContext.getBean("consultDAO",
	 * ConsultDAO.class); List<Doctor> list = dao.queryConsultDoctor(organId,
	 * profession, addrArea, online); System.out.println(list.size()); for (int
	 * i = 0; i < list.size(); i++) {
	 * System.out.println(JSONUtils.toString(list.get(i))); } }
	 *//**
     * 病人咨询记录查询服务
     *
     * @author hyj
     * @throws DAOException
     */
	/*
	 * public void testFindConsult() throws DAOException { String mpiId =
	 * "2c9081814cd4ca2d014cd4ddd6c90000"; ConsultDAO dao =
	 * appContext.getBean("consultDAO", ConsultDAO.class);
	 * List<ConsultAndDoctor> list = dao.findConsult(mpiId);
	 * System.out.println(list.size()); for (ConsultAndDoctor c : list) {
	 * System.out.println(JSONUtils.toString(c)); }
	 * 
	 * }
	 *//**
     * 病人咨询记录查询服务--分页
     *
     * @author hyj
     * @throws DAOException
     */
	/*
	 * public void testFindConsultWithPage() throws DAOException { String mpiId
	 * = "2c9081814cd4ca2d014cd4ddd6c90000"; ConsultDAO dao =
	 * appContext.getBean("consultDAO", ConsultDAO.class);
	 * List<ConsultAndDoctor> list = dao.findConsultWithPage(mpiId, 0);
	 * System.out.println(list.size()); for (ConsultAndDoctor c : list) {
	 * System.out.println(JSONUtils.toString(c)); }
	 * 
	 * }
	 */

    /**
     * 获取咨询单信息服务+病人信息+其他资料--lf
     *
     * @throws DAOException
     */
    public void testGetConsultAndPatientAndCdrOtherdocById()
            throws DAOException {
        Integer id = 862;
        ConsultDAO dao = appContext.getBean("consultDAO", ConsultDAO.class);
        ConsultAndPatient consultAndPatient = dao
                .getConsultAndPatientAndCdrOtherdocById(id);
        System.out.println(JSONUtils.toString(consultAndPatient));
    }

    public void testFirstGetAddrArea() {
        ConsultDAO dao = appContext.getBean("consultDAO", ConsultDAO.class);
        String parentKey = "33";
        int sliceType = 0;
        dao.getAddrArea(parentKey, sliceType);
    }

    /**
     * 查询指定日期的咨询总量
     *
     * @author ZX
     * @date 2015-4-21 下午12:01:04
     * @param date
     * @return
     */
	/*
	 * public void testGetConsultTotalNumByDate() { ConsultDAO dao =
	 * appContext.getBean("consultDAO", ConsultDAO.class); Date requestTime =
	 * new StringToDate().convert("2015-04-20"); long num =
	 * dao.getConsultTotalNumByDate(requestTime); System.out.println(num); }
	 *//**
     * 获取当天人均咨询数(患者)
     *
     * @author ZX
     * @date 2015-4-21 下午1:30:13
     */
	/*
	 * public void testGetAverageConsultNumForPatient() { ConsultDAO dao =
	 * appContext.getBean("consultDAO", ConsultDAO.class); Date requestTime =
	 * new StringToDate().convert("2015-04-20"); double num =
	 * dao.getAverageConsultNumForPatient(requestTime); System.out.println(num);
	 * }
	 *//**
     * 昨日咨询总数
     *
     * @author ZX
     * @date 2015-5-26 下午3:30:25
     */
	/*
	 * public void testGetTargetNumForYestoday() { ConsultDAO dao =
	 * appContext.getBean("consultDAO", ConsultDAO.class); String manageUnit =
	 * "eh"; long list = dao.getTargetNumForYesterday(manageUnit);
	 * System.out.println(JSONUtils.toString(list)); }
	 *//**
     * 今日咨询总数
     *
     * @author ZX
     * @date 2015-5-26 下午3:30:47
     */
	/*
	 * public void testGetTargetNumForToday() { ConsultDAO dao =
	 * appContext.getBean("consultDAO", ConsultDAO.class); String manageUnit =
	 * "eh"; long list = dao.getTargetNumForToday(manageUnit);
	 * System.out.println(JSONUtils.toString(list)); }
	 *//**
     * 总咨询数
     *
     * @author ZX
     * @date 2015-5-26 下午3:30:57
     */
	/*
	 * public void testGetTargetNum() { ConsultDAO dao =
	 * appContext.getBean("consultDAO", ConsultDAO.class); String manageUnit =
	 * "eh"; long list = dao.getTargetNum(manageUnit);
	 * System.out.println(JSONUtils.toString(list)); }
	 *//**
     * 更新支付状态
     */
	/*
	 * public void testUpdatePayFlagByTradeNo() { ConsultDAO dao =
	 * appContext.getBean("consultDAO", ConsultDAO.class);
	 * dao.updatePayFlagByOutTradeNo(new Date(), "consult20150616150643192",
	 * "consult20150616150643191"); }
	 * 
	 * public void testGetByOutTradeNo() { ConsultDAO dao =
	 * appContext.getBean("consultDAO", ConsultDAO.class);
	 * System.out.println(JSONUtils.toString(dao
	 * .getByOutTradeNo("consult20150616150643191"))); ;
	 * 
	 * }
	 *//**
     * 咨询统计查询
     *
     * @author ZX
     * @date 2015-6-23 下午8:54:55
     */
	/*
	 * public void testFindConsultWithStatic() { ConsultDAO dao =
	 * appContext.getBean("consultDAO", ConsultDAO.class);
	 * 
	 * Date startTime = new StringToDate().convert("2015-04-06"); Date endTime =
	 * new StringToDate().convert("2015-07-17");
	 * 
	 * Consult consult = new Consult(); consult.setConsultOrgan(1);
	 * consult.setConsultDoctor(1178); // consult.setConsultStatus(1);
	 * 
	 * int start = 0;
	 * 
	 * List<ConsultAndPatients> list = dao.findConsultWithStatic(startTime,
	 * endTime, consult, start); System.out.println(JSONUtils.toString(list)); }
	 *//**
     *
     * Title: Description:
     *
     * @author AngryKitty
     * @date 2015-8-31 void
     */
	/*
	 * public void testfindConsultAndPatientsByStatic() { ConsultDAO dao =
	 * appContext.getBean("consultDAO", ConsultDAO.class);
	 * 
	 * Date startTime = new StringToDate().convert("2015-04-06"); Date endTime =
	 * new StringToDate().convert("2015-09-17"); Consult consult = new
	 * Consult(); consult.setConsultDoctor(1178);
	 * 
	 * QueryResult<ConsultAndPatients> query = dao
	 * .findConsultAndPatientsByStatic(startTime, endTime, null, 0, null, null);
	 * System.out.println(query.getTotal());
	 * System.out.println(JSONUtils.toString(query.getItems()));
	 * 
	 * }
	 */

    /**
     * 查询历史咨询单列表服务 (纯分页)
     *
     * @param exeDoctor --执行医生
     * @param mpiId     --病人主索引
     * @param start     记录起始位置
     * @param limit     查询记录数
     * @return List<ConsultAndPatient>
     * @author luf
     */
    public void testQueryConsultHisListWithPage() {
        int exeDoctor = 40;
//		Integer exeDoctor = null;
//		String mpiId = "2c9081814cc3ad35014cc4d1b6140002";
        String mpiId = "";
        int start = 0;
        int limit = 10;
        List<ConsultAndPatient> cap = appContext.getBean("consultDAO",
                ConsultDAO.class).queryConsultHisListWithPage(exeDoctor, mpiId,
                start, limit);
        System.out.println(JSONUtils.toString(cap));
        System.out.println(cap.size());
    }

    public void testDateCon() {
        // Date startTime = DateConversion.getCurrentDate("08:00:00",
        // "HH:mm:ss");
        // Date endTime = DateConversion.getCurrentDate("10:00:00", "HH:mm:ss");
        // int intervalTime = 56;
        // List<Object[]> os = DateConversion.getIntervalTimeList(startTime,
        // endTime, intervalTime);
        // System.out.println(JSONUtils.toString(os));
        Date startTime = DateConversion.getCurrentDate("2015-12-09 08:00:00",
                "yyyy-MM-dd HH:mm:ss");
        System.out.println(DateConversion.getWeekOfDateInt(startTime));
    }

    public void testGetEffConsultTime() {
        ConsultDAO dao = appContext.getBean("consultDAO", ConsultDAO.class);
        Date consultDate = DateConversion.getCurrentDate("2016-04-19 18:59:00",
                "yyyy-MM-dd HH:mm:ss");
        int doctorId = 40;
        List<Object[]> os = dao.getEffConsultTime(consultDate, doctorId);
        System.out.println(JSONUtils.toString(os));
    }

    /**
     * 根据mpiId获取咨询列表
     *
     * @param mpiId     主索引
     * @param startPage 分页
     * @return List<Consult>
     * @author xiebz
     * @Date 2015-12-19
     */
    public List<Consult> testFindByMpiId() {
        String mpiId = "2c9081824cc3552a014cc3a9a0120002";
        ConsultDAO dao = appContext.getBean("consultDAO", ConsultDAO.class);
        List<Consult> consults = dao.findByMpiId(mpiId, 0);
        for (Consult c : consults) {
            System.out.println(JSONUtils.toString(c));
        }
        return consults;
    }

    /**
     * 根据requestMpi获取咨询列表
     *
     * @param requestMpi 患者端当前登陆的患者mpi
     * @param startPage  分页
     * @return List<Consult>
     * @author xiebz
     * @Date 2016-1-18
     */
    public List<Consult> testFindByRequestMpi() {
        String mpiId = "2c9081824cc3552a014cc3a9a0120002";
        ConsultDAO dao = appContext.getBean("consultDAO", ConsultDAO.class);
        List<Consult> consults = dao.findByRequestMpiAndPayflag(mpiId, 1, 0);
        for (Consult c : consults) {
            System.out.println(JSONUtils.toString(c));
        }
        return consults;
    }

    /**
     * 健康端根据mpiId获取咨询列表(改变时间格式)
     *
     * @param mpiId     主索引
     * @param startPage 分页
     * @return List<Consult>
     * @throws ParseException
     * @author xiebz
     * @Date 2015-12-19
     */
    public void testDealWithConsults() throws ParseException {
        // String mpiId = "2c9081824cc3552a014cc3a9a0120002";
        String mpiId = "8a287a564efd9e45014f05fd905f0001";
        ConsultDAO dao = appContext.getBean("consultDAO", ConsultDAO.class);
        List<Object> result = dao.dealWithConsults(mpiId, 0);

        System.out.println(JSONUtils.toString(result));
    }

    /**
     * 根据consultId获取咨询单、医生及病人信息
     *
     * @param consultId --咨询单号
     * @return Map<String, Object>
     * @author xiebz
     */
    public Map<String, Object> testGetConsultAndPatientAndDoctorById() {
        Integer consultId = 202;
        ConsultDAO dao = appContext.getBean("consultDAO", ConsultDAO.class);
        Map<String, Object> res = dao.getConsultAndPatientAndDoctorById(
                consultId, 1198);
        System.out.println(JSONUtils.toString(res));

        return res;
    }

    /**
     * @return void
     * @function 根据支付状态获取列表
     * @author zhangjr
     * @date 2015-12-23
     */
	/*
	 * public void testFindByPayflag(){ ConsultDAO dao =
	 * appContext.getBean("consultDAO",ConsultDAO.class); List<Consult> list =
	 * dao.findByPayflag(2); System.out.println(JSONUtils.toString(list)); }
	 */
    public void tesUpdateSinglePayFlagByOutTradeNo() {
        ConsultDAO dao = appContext.getBean("consultDAO", ConsultDAO.class);
        dao.updateSinglePayFlagByOutTradeNo(2, "consult20150616150643192");
    }

    public void testFindUnfinishedConsultAndPatientByDoctorId() {
        ConsultDAO dao = appContext.getBean("consultDAO", ConsultDAO.class);
        System.out.println(JSONUtils.toString(dao
                .findUnfinishedConsultAndPatientByDoctorId(40, 0, 10)));
    }

    /**
     * 最近咨询有效时间段
     *
     * @param consultDate 资询时间
     * @param doctorId    医生内码
     * @return Map<String, Object>
     * @author luf
     */
    public void testGetEffConsultTimeLast() {
        ConsultDAO dao = appContext.getBean("consultDAO", ConsultDAO.class);
        int doctorId = 40;
        Map<String, Object> os = dao.getEffConsultTimeLast(doctorId);
        System.out.println(JSONUtils.toString(os));
    }

    /**
     * 最近咨询有效时间段--翻页
     * <p>
     * eh.bus.dao
     *
     * @param consultDate 资询时间
     * @param doctorId    医生内码
     * @param page        翻页 1下一天，-1上一天
     * @return Map<String,Object>
     * @author luf 2016-2-18
     */
    public void testGetEffConsultTimeNew() {
        ConsultDAO dao = appContext.getBean("consultDAO", ConsultDAO.class);
        Date consultDate = DateConversion.getCurrentDate("2016-02-29 00:00:00",
                "yyyy-MM-dd HH:mm:ss");
        // Date consultDate2 = DateConversion.getFormatDate(consultDate,
        // "yyyy-MM-dd");
        // Date now = DateConversion.getFormatDate(new Date(), "yyyy-MM-dd");
        // System.out.println(consultDate2.compareTo(now));
        int doctorId = 40;
        Map<String, Object> os = dao.getEffConsultTimeNew(consultDate,
                doctorId, 1);
        System.out.println(JSONUtils.toString(os));
    }

    public void testFindConsultTwoDayAgo() {
        ConsultDAO dao = appContext.getBean("consultDAO", ConsultDAO.class);
		/*
		 * try { dao.dealWithUnfinishedConsult(); } catch (Exception e) { //
		 * TODO Auto-generated catch block e.printStackTrace(); }
		 */
        try {
            List<Consult> list = dao.findConsultTwoDayAgo();
            System.out.println(">>" + list.size());
            for (Consult c : list) {
                System.out.println(JSONUtils.toString(c));
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    public void testFindFinishedConsultAndPatientByDoctorId() {
        ConsultDAO dao = appContext.getBean("consultDAO", ConsultDAO.class);
        int doctorId = 1482;
        int start = 0;
        int limit = 2;
        System.out
                .println(JSONUtils.toString(dao
                        .findFinishedConsultAndPatientByDoctorId(doctorId,
                                start, limit)));
    }

    public void testGetEffAfterForLastEff() {
        ConsultDAO dao = appContext.getBean("consultDAO", ConsultDAO.class);
        Date paramDate = DateConversion.getCurrentDate("2016-3-13",
                "yyyy-MM-dd");
        int doctorId = 40;
        System.out.println(JSONUtils.toString(dao.getEffAfterForLastEff(
                paramDate, doctorId,8)));
    }

    public void testGetEffBeforeLastEff() {
        ConsultDAO dao = appContext.getBean("consultDAO", ConsultDAO.class);
        Date paramDate = DateConversion
                .getCurrentDate("2016-3-7", "yyyy-MM-dd");
        int doctorId = 40;
        System.out.println(JSONUtils.toString(dao.getEffBeforeForLastEff(
                paramDate, doctorId)));
    }

    public void testGetLastEffConsultTime() {
        ConsultDAO dao = appContext.getBean("consultDAO", ConsultDAO.class);
        Date consultDate = DateConversion.getCurrentDate("2016-4-26 16:00:00",
                "yyyy-MM-dd");// new Date();
        int doctorId = 40;
        int page = -1;
        System.out.println(JSONUtils.toString(dao.getLastEffConsultTime(
                consultDate, doctorId, page)));
    }

    public void testUpdateConsultForRefuse() {
        ConsultDAO dao = appContext.getBean("consultDAO", ConsultDAO.class);
        dao.updateConsultForRefuse2(new Date(), "48小时拒绝", 687, 3, 0);
    }

    public void testGetConsultDetailById() {
        int consultId = 561;
        ConsultDAO dao = appContext.getBean("consultDAO", ConsultDAO.class);
        System.out.println(JSONUtils.toString(dao
                .getConsultDetailById(consultId)));
    }

    public void testQueryConsultWithPageLimit() {
        ConsultDAO dao = appContext.getBean("consultDAO", ConsultDAO.class);
        System.out.println(JSONUtils.toString(dao.queryConsultWithPageLimit(40, false, 0, 10)));
    }

	public void testGroupEnable() {
		ConsultDAO dao = appContext.getBean("consultDAO", ConsultDAO.class);
		System.out.println(dao.groupEnable(1505));
	}

    public void testGetConsultByAppIdAndOpenId() {
        ConsultDAO dao = appContext.getBean("consultDAO", ConsultDAO.class);
//		System.out.println(JSONUtils.toString(dao.getConsultByAppIdAndOpenId("wx6a80dd109228fd4b","ogG6UtxsiwvLjsFORyNLtA3U_dKw")));
    }

	public void testGetConsultAndPatientInfo(){
		ConsultDAO dao = appContext.getBean("consultDAO", ConsultDAO.class);
		HashMap<String,Object> map = dao.getConsultAndPatientInfo("wx6a80dd109228fd4b","ogG6Ut5YCpoelUy_ddYtQWKdcSAc");
		System.out.println(JSONUtils.toString(map));
	}

    public void testfindApplyingConsultByPatientsAndDoctor(){
        ConsultDAO dao = appContext.getBean("consultDAO", ConsultDAO.class);
        List<Consult> map = dao.findApplyingConsultByPatientsAndDoctor("402885f2548efbce01548f4186770001","402885f2548efbce01548f4186770001",3988,2);
        System.out.println(JSONUtils.toString(map));
    }


    public void testGetConsultAndPatientAndCdrOtherdocByIdAndDoctorId() {
        ConsultDAO dao = appContext.getBean("consultDAO", ConsultDAO.class);
        System.out.println(JSONUtils.toString(dao.getConsultAndPatientAndCdrOtherdocByIdAndDoctorId(1341, 9538)));
    }

    public void testGetHasChatByConsultId() {
        ConsultDAO dao = appContext.getBean("consultDAO", ConsultDAO.class);
        System.out.println(JSONUtils.toString(dao.getHasChatByConsultId(942)));
    }

    public void testgetNewestConsultId(){
        ConsultDAO dao = appContext.getBean("consultDAO", ConsultDAO.class);
        System.out.println(JSONUtils.toString(dao.getNewestConsultId(dao.get(561))));
    }

    public void testGetConsultDateTime() {
        ConsultDAO dao = appContext.getBean("consultDAO", ConsultDAO.class);
//        System.out.println(JSONUtils.toString(dao.getConsultDateTime(DateConversion.getCurrentDate("2017-01-16 00:00:00","yyyy-MM-dd"),40,1,7)));
        System.out.println(JSONUtils.toString(dao.getConsultDateTime(new Date(),40,0,7)));
//        HashMap<Integer,String> map = new HashMap<Integer, String>();
//        map.put(1,"aaa");
//        System.out.println(map.get(1));
    }

    public void testUpdateNewSessionToOld() {
        ConsultDAO dao = appContext.getBean("consultDAO", ConsultDAO.class);
        dao.updateNewSessionToOld();
    }
}
