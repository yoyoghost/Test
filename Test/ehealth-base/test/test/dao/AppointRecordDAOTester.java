package test.dao;

import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import ctd.persistence.annotation.DAOParam;
import ctd.util.context.Context;
import eh.bus.constant.CloudClinicSetConstant;
import eh.bus.his.service.AppointTodayBillService;
import eh.entity.his.PreBillRequest;
import junit.framework.TestCase;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import ctd.controller.exception.ControllerException;
import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import ctd.util.converter.support.StringToDate;
import eh.base.dao.DoctorDAO;
import eh.bus.dao.AppointRecordDAO;
import eh.bus.dao.AppointSourceDAO;
import eh.bus.dao.TransferDAO;
import eh.entity.base.Doctor;
import eh.entity.bus.AppointRecord;
import eh.entity.bus.AppointRecordAndDoctor;
import eh.entity.bus.AppointRecordAndDoctors;
import eh.entity.bus.AppointRecordAndPatient;
import eh.entity.bus.AppointRecordAndPatientAndDoctor;
import eh.entity.bus.AppointRecordBean;
import eh.entity.bus.AppointSource;
import eh.entity.bus.AppointmentResponse;
import eh.entity.bus.HisAppointRecord;
import eh.entity.bus.Transfer;
import eh.entity.mpi.Patient;
import eh.mpi.dao.PatientDAO;
import eh.utils.DateConversion;

public class AppointRecordDAOTester extends TestCase {
    private static ClassPathXmlApplicationContext appContext;
    private static AppointRecordDAO dao;

    static {
        appContext = new ClassPathXmlApplicationContext("spring.xml");
        dao = appContext.getBean("appointRecordDAO", AppointRecordDAO.class);
    }
    public void testGHHGH(){
        List list = new ArrayList();
        list.add(1);
        List<AppointRecord> res = dao.findByAppointSourceIdHasAppoint(list);
        System.out.print(111);
    }
    public void testCreate() throws DAOException {
        AppointRecordDAO dao = appContext.getBean("appointRecordDAO",
                AppointRecordDAO.class);

        int nmr = ThreadLocalRandom.current().nextInt(10000);
        int n4 = ThreadLocalRandom.current().nextInt(1000, 9999);

        AppointRecord r = new AppointRecord();
        r.setAppointName("212" + nmr);
        r.setAppointFailUser("34" + n4);
        r.setAppointOragn("1");
        r.setAppointRecordId(n4);
        r.setAppointSourceId(1);
        r.setAppointStatus(1);
        r.setAppointDepartId("2" + nmr);
        r.setAppointRoad(1);
        Date date = new Date();
        r.setEndTime(date);
        r.setMpiid("sss" + nmr);
        r.setOrderNum(n4);
        r.setOrganId(n4);
        r.setPatientName("ddd" + nmr);
        r.setSourceType(n4);
        r.setStartTime(date);
        r.setWorkDate(date);
        r.setWorkType(n4);
        r.setAppointDate(date);
        r.setOrganSchedulingId("0");
        r.setDoctorId(1);
        r.setConfirmClinicAddr("门诊1楼");
        dao.save(r);
    }

    /**
     * 预约记录查询服务之情况一测试（根据机构编码和就诊开始时间进行查询）
     *
     * @throws DAOException
     * @author hyj
     */
    public void testQueryByOrganIdAndAppointDate() throws DAOException {
        int organId = 1;
        String strDate = "2015-05-06 13:25:28";
        DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date StartDate = null;
        try {
            StartDate = sdf.parse(strDate);
        } catch (ParseException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        Date EndDate = new Date();
        AppointRecordDAO dao = appContext.getBean("appointRecordDAO",
                AppointRecordDAO.class);
        List<AppointRecordAndPatient> list = dao.queryByOrganIdAndAppointDate(
                organId, StartDate, EndDate);
        System.out.println(list.size());
        for (AppointRecordAndPatient a : list) {
            System.out.println(JSONUtils.toString(a));
        }

    }

    /**
     * 预约记录查询服务之情况一测试（根据机构编码和就诊开始时间进行查询）--分页
     *
     * @throws DAOException
     * @author hyj
     */
    public void testQueryByOrganIdAndAppointDateWithPage() throws DAOException {
        int organId = 1;
        Date startDate = new StringToDate().convert("2015-05-06 13:25:28");
        Date EndDate = new Date();
        AppointRecordDAO dao = appContext.getBean("appointRecordDAO",
                AppointRecordDAO.class);
        List<AppointRecordAndPatient> list = dao
                .queryByOrganIdAndAppointDateWithPage(organId, startDate,
                        EndDate, 40);
        System.out.println(list.size());
        for (AppointRecordAndPatient a : list) {
            System.out.println(JSONUtils.toString(a));
        }

    }

    /**
     * 预约记录查询服务之情况二测试（根据医生编号和就诊开始时间进行查询）
     *
     * @throws DAOException
     * @author hyj
     */
    public void testQueryByDoctorIdAndAppointDate() throws DAOException {
        int doctorId = 625;
        Date startDate = new StringToDate().convert("2015-04-06 13:25:28");
        Date EndDate = new Date();

        AppointRecordDAO dao = appContext.getBean("appointRecordDAO",
                AppointRecordDAO.class);
        List<AppointRecordAndPatient> list = dao.queryByDoctorIdAndAppointDate(
                doctorId, startDate, EndDate);
        System.out.println(list.size());
        for (AppointRecordAndPatient a : list) {
            System.out.println(JSONUtils.toString(a));
        }

    }

    /**
     * 预约记录查询服务之情况二测试（根据医生编号和就诊开始时间进行查询）--分页
     *
     * @throws DAOException
     * @author hyj
     */
    public void testQueryByDoctorIdAndAppointDateWithPage() throws DAOException {
        int doctorId = 1919;
        Date startDate = new StringToDate().convert("2015-10-22 13:25:28");
        Date EndDate = new Date();
        AppointRecordDAO dao = appContext.getBean("appointRecordDAO",
                AppointRecordDAO.class);
        List<AppointRecordAndPatient> list = dao
                .queryByDoctorIdAndAppointDateWithPage(doctorId, startDate,
                        EndDate, 0);
        System.out.println(list.size());
        for (AppointRecordAndPatient a : list) {
            System.out.println(JSONUtils.toString(a));
        }

    }

    /**
     * 查询医生下个月预约记录服务
     *
     * @throws DAOException
     * @author hyj
     */
    public void testQueryByDoctorIdAndAppointDateNextMonth()
            throws DAOException {
        int doctorId = 625;
        AppointRecordDAO dao = appContext.getBean("appointRecordDAO",
                AppointRecordDAO.class);
        List<AppointRecordAndPatient> list = dao
                .queryByDoctorIdAndAppointDateNextMonth(doctorId);
        System.out.println(list.size());
        for (AppointRecordAndPatient a : list) {
            System.out.println(JSONUtils.toString(a));
        }

    }

    /**
     * 查询医生下个月预约记录服务--分页
     *
     * @throws DAOException
     * @author hyj
     */
    public void testQueryByDoctorIdAndAppointDateNextMonthWithPage()
            throws DAOException {
        int doctorId = 625;
        AppointRecordDAO dao = appContext.getBean("appointRecordDAO",
                AppointRecordDAO.class);
        List<AppointRecordAndPatient> list = dao
                .queryByDoctorIdAndAppointDateNextMonthWithPage(doctorId, 0);
        System.out.println(list.size());
        for (AppointRecordAndPatient a : list) {
            System.out.println(JSONUtils.toString(a));
        }

    }

    /**
     * 添加预约服务修改版
     *
     * @throws DAOException
     */
    public void testAddAppointRecordNew() throws DAOException {
        AppointRecord r = new AppointRecord();
        r.setMpiid("2c90818258511a950158512fb0550000");
        r.setPatientName("袁总瑞");
        r.setCertId("330481198909072210");
        r.setLinkTel("18868744478");
        r.setOrganAppointId("20151024|96|2|9");
        r.setAppointSourceId(2162751);
        r.setOrganId(1);
        r.setAppointDepartId("111");
        r.setAppointDepartName("医务科");
        r.setDoctorId(40);
        r.setAppointUser("2c90818258511a950158512fb0550000");
        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String confirmClinicTime = sdf.format(date);
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        Date StartDate = null;
        try {
            StartDate = df.parse(confirmClinicTime);
        } catch (ParseException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        r.setWorkDate(DateConversion.getCurrentDate("2016-11-22","yyyy-MM-dd"));
        r.setWorkType(1);
        r.setSourceType(2);
        r.setStartTime(DateConversion.getCurrentDate("2016-11-22 11:50:00","yyyy-MM-dd hh:mm:ss"));
        r.setEndTime(DateConversion.getCurrentDate("2016-11-22 11:50:00","yyyy-MM-dd hh:mm:ss"));
        r.setOrderNum(1);
        r.setAppointRoad(5);
        r.setAppointStatus(1);
        r.setAppointDate(new Date());
        r.setAppointName("sss");
        r.setAppointOragn("1");
        r.setClinicPrice(33.4);
        r.setTransferId(0);
        r.setSourceLevel(1);

        AppointRecordDAO dao = appContext.getBean("appointRecordDAO",
                AppointRecordDAO.class);
        boolean flag = dao.addAppointRecordNew(r);
        System.out.print(flag);
    }

    public void  testPreaccount(){

        AppointTodayBillService  a = new  AppointTodayBillService();
//        a.canShowPayBtn(64386);
//        a.settlePreBillForBus(64389,"40");
//        PreBillRequest request = new PreBillRequest();
//        request.setOrganId(1);
//        request.setAppointRecordId(64384);
//          dao.cancel(64384,"40","卢芳","取消原因");
        a.settleRegBill(dao.get(64389));
    }

    /**
     * 预约
     *
     * @author zhangx
     * @date 2016-1-19 下午2:23:32
     */
    public void testUpdateAppointId() {
        // AppointRecord ar=new AppointRecord();
        // ar.setAppointRecordId(291);
        AppointmentResponse res = new AppointmentResponse();
        res.setId("48352");
        res.setAppointID("48351");
        res.setClinicArea("儿童1幢3楼儿童心脏中心");
        AppointRecordDAO dao = appContext.getBean("appointRecordDAO",
                AppointRecordDAO.class);
        dao.updateAppointId(res);
    }

    /**
     * 取消
     *
     * @author zhangx
     * @date 2016-1-19 下午2:23:26
     */
    public void testDoCancelAppoint() {
        HisAppointRecord r = new HisAppointRecord();
        r.setOrganId(1);
        // r.setOrganSchedulingId("96");
        // r.setOrganSourceId("523924");
        r.setOrganAppointId("1359");
        AppointRecord ar = dao.getAppointedRecord(r);
        System.out.println(JSONUtils.toString(ar));
        dao.doCancelAppoint(ar.getAppointRecordId(), "system", "system",
                "已通过其他渠道取消该预约");
    }

    public void testGetAppointRecord() throws DAOException {
        AppointRecord r = new AppointRecord();
        r.setMpiid("4028811f4bce1c7f014bce1cc5fa0000");
        r.setPatientName("黄伊瑾1");

        r.setOrganId(1);
        r.setAppointDepartId("37");

        r.setDoctorId(36);
        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String confirmClinicTime = "2015-04-06";// sdf.format(date);
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        Date StartDate = null;
        try {
            StartDate = df.parse(confirmClinicTime);
        } catch (ParseException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        r.setWorkDate(StartDate);
        r.setWorkType(1);
        r.setSourceType(2);
        r.setStartTime(date);
        r.setEndTime(date);
        r.setOrderNum(1);
        r.setAppointRoad(1);
        r.setAppointStatus(1);
        r.setAppointDate(date);
        r.setAppointUser("1");
        r.setAppointName("sss");
        r.setAppointOragn("1");
        r.setClinicPrice(33.4);
        r.setTransferId(22);
        r.setSourceLevel(1);

        AppointRecordDAO dao = appContext.getBean("appointRecordDAO",
                AppointRecordDAO.class);
        List<AppointRecord> list = dao.findByMpiIdAndWorkDateAndOrganId(
                r.getMpiid(), r.getOrganId(), r.getAppointDepartId(),
                r.getDoctorId(), r.getWorkDate());
        System.out.println(list.size());
        for (int i = 0; i < list.size(); i++) {
            System.out.println(JSONUtils.toString(list.get(i)));
        }

    }

    /**
     * 根据主键查询单条预约记录测试
     *
     * @throws DAOException
     */
    public void testGetById() throws DAOException {
        int id = 1;
        AppointRecordDAO dao = appContext.getBean("appointRecordDAO",
                AppointRecordDAO.class);
        AppointRecordAndPatientAndDoctor AppointRecord = dao.getById(id);
        System.out.println(JSONUtils.toString(AppointRecord));
    }

    public void testGetAppointRecordAndPatientAndDoctorById() {
        int appointRecordId = 301;
        AppointRecordDAO dao = appContext.getBean("appointRecordDAO",
                AppointRecordDAO.class);
        System.out.println(JSONUtils.toString(dao
                .getAppointRecordAndPatientAndDoctorById(appointRecordId)));
    }

    /**
     * 测试名:预约挂号记录爽约服务测试
     *
     * @author yxq
     */
    public void testUpdateFail() {
        AppointRecordDAO dao = appContext.getBean("appointRecordDAO",
                AppointRecordDAO.class);
        int appointRecordId = 1;
        String afTime = "2015-02-27 09:15:26";
        Date appointFail = new Date();
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            appointFail = df.parse(afTime);
        } catch (Exception e) {
            e.printStackTrace();
        }
        String appointFailUser = "爽约确定人编号";
        dao.updateFail(appointRecordId, appointFail, appointFailUser);
    }

    /**
     * 服务名:预约挂号记录确认服务测试
     */
    public void testSure() {
        AppointRecordDAO dao = appContext.getBean("appointRecordDAO",
                AppointRecordDAO.class);
        int appointRecordId = 1;
        int organID = 1;
        String organAppointId = "01";
        Date date = new Date();
        Timestamp t = new Timestamp(date.getTime());
        Date registerDate = t;
        String registerUser = "01";
        String registerName = "预约挂号确认人姓名";
        dao.sure(appointRecordId, organID, organAppointId, registerDate,
                registerUser, registerName);
    }

    /**
     * 服务名:预约挂号记录取消服务
     */
    public void testCancel() {
        AppointRecordDAO dao = appContext.getBean("appointRecordDAO",
                AppointRecordDAO.class);
        int appointRecordId = 271;
        String cancelUser = "01";
        String cancelName = "预约挂号记录取消人姓名";
        String cancelResean = "预约挂号记录取消理由";
        boolean flag = dao.doCancelAppoint(appointRecordId, cancelUser,
                cancelName, cancelResean);
        System.out.println(flag);
    }

    /**
     * 预约申请列表查询服务
     *
     * @author hyj
     */
    public void testFindueryRequestAppointRecord() throws DAOException {
        String appointUser = "1192";
        AppointRecordDAO dao = appContext.getBean("appointRecordDAO",
                AppointRecordDAO.class);
        List<AppointRecordAndPatient> list = dao
                .findRequestAppointRecord(appointUser);
        for (int i = 0; i < list.size(); i++) {
            System.out.println(list.size() + JSONUtils.toString(list.get(i)));
        }
    }

    /**
     * 预约申请列表查询服务--分页
     *
     * @author hyj
     */
    public void testFindueryRequestAppointRecordWithPage() throws DAOException {
        String appointUser = "40";
        AppointRecordDAO dao = appContext.getBean("appointRecordDAO",
                AppointRecordDAO.class);
        List<AppointRecordAndPatient> list = dao
                .findRequestAppointRecordWithPage(appointUser, 0);
        for (int i = 0; i < list.size(); i++) {
            System.out.println(list.size() + JSONUtils.toString(list.get(i)));
        }
    }

    /**
     * 病人挂号记录查询
     *
     * @throws DAOException
     * @author hyj
     */
    public void testFindAppointRecord() throws DAOException {
        String mpiId = "2c9081824cc3ae4a014cc4ee8e2c0000";
        AppointRecordDAO dao = appContext.getBean("appointRecordDAO",
                AppointRecordDAO.class);
        List<AppointRecordAndDoctor> list = dao.findAppointRecord(mpiId);
        for (int i = 0; i < list.size(); i++) {
            System.out.println(list.size() + JSONUtils.toString(list.get(i)));
        }

    }

    /**
     * 病人挂号记录查询--分页
     *
     * @throws DAOException
     * @author hyj
     */
    public void testFindAppointRecordWithPage() throws DAOException {
        String mpiId = "2c9081824cc3ae4a014cc4ee8e2c0000";
        AppointRecordDAO dao = appContext.getBean("appointRecordDAO",
                AppointRecordDAO.class);
        List<AppointRecordAndDoctor> list = dao.findAppointRecordWithPage(
                mpiId, 20);
        for (int i = 0; i < list.size(); i++) {
            System.out.println(list.size() + JSONUtils.toString(list.get(i)));
        }

    }

    /**
     * 预约成功发送短信
     *
     * @throws DAOException
     */
    public void testSendAppointmentMsg() throws DAOException {
        /*
         * AppointRecord ar=new AppointRecord(); ar.setPatientName("黄伊瑾");
		 * ar.setLinkTel("18768177768"); ar.setOrganId(1); ar.setDoctorId(1182);
		 * ar.setAppointDepartName("呼吸科"); ar.setOrderNum(2); ar.setWorkType(1);
		 * ar.setWorkDate(new Date()); String address="就诊地点";
		 * ar.setStartTime(new Date()); ar.setTransferId(0);
		 */
        AppointRecordDAO dao = appContext.getBean("appointRecordDAO",
                AppointRecordDAO.class);
        AppointRecord ar = dao.get(1083);
        dao.sendAppointmentMsg(ar);
        try {
            TimeUnit.SECONDS.sleep(10);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

//	/**
//	 * 预约取消发送短信
//	 *
//	 * @throws DAOException
//	 */
//	public void testSendAppointmentCancelMsg() throws DAOException {
//		AppointRecord ar = new AppointRecord();
//		ar.setPatientName("黄伊瑾");
//		ar.setDoctorId(40);
//		ar.setOrganId(1);
//		ar.setAppointDepartName("呼吸科");
//		ar.setOrderNum(2);
//		ar.setWorkType(1);
//		ar.setWorkDate(new Date());
//		String address = "就诊地点";
//		ar.setStartTime(new Date());
//		ar.setTransferId(0);
//		ar.setCancelResean("取消短信测试");
//		AppointRecordDAO dao = appContext.getBean("appointRecordDAO",
//				AppointRecordDAO.class);
//		dao.sendAppointmentCancelMsg(ar);
//	}

    public void testCancelForHisFail() {
        AppointmentResponse res = new AppointmentResponse();
        res.setId("1084");
        res.setOrderNum(14);
        res.setErrCode("1");
        res.setErrMsg("医院服务调用超时,自动取消该预约");
        /*
		 * res.setAppointID(""); res.setClinicArea("");
		 * res.setErrMsg("his转诊失败");
		 */
        dao.cancelForHisFail(res);
    }

    /**
     * 预约失败新增系统消息
     *
     * @author ZX
     * @date 2015-4-30 下午3:53:14
     */
    public void testSendMsgForAppointFail() {
        AppointRecord ar = dao.getByAppointRecordId(395);
        //dao.sendMsgForAppointFail(ar);
    }

    /**
     * 查询当日新增预约记录
     */
    public void testFindTodayAppointRecord() {
        int doctorId = 376;
        List<AppointRecordAndPatientAndDoctor> list = dao
                .findTodayAppointRecord(doctorId);
        for (AppointRecordAndPatientAndDoctor a : list) {
            System.out.println(JSONUtils.toString(a));
        }

    }

    /**
     * 查询当日新增预约记录--分页
     *
     * @author hyj
     */
    public void testFindTodayAppointRecordWithPage() {
        int doctorId = 1919;
        List<AppointRecordAndPatientAndDoctor> list = dao
                .findTodayAppointRecordWithPage(doctorId, 0);
        for (AppointRecordAndPatientAndDoctor a : list) {
            System.out.println(JSONUtils.toString(a));
        }

    }

    public void testFindAppointRecordWithStaticwW() {
        Date startTime = new StringToDate().convert("2015-05-06");
        Date endTime = new StringToDate().convert("2015-08-31");

        AppointRecord ar = new AppointRecord();

        // ar.setAppointOragn("1"); // ar.setOrganId(1);
        // ar.setAppointName("王宁武"); // ar.setDoctorId(1);
        // ar.setAppointStatus(9);

        int start = 0;

        List<AppointRecordAndDoctors> list = dao.findAppointRecordWithStatic2(
                startTime, endTime, ar, start);

        System.out.println(list.size());
        System.out.println(JSONUtils.toString(list));
    }

    /**
     * 按申请机构统计预约数
     *
     * @author hyj
     */
    public void testGetRequestNumFromTo() {
        String manageUnit = "eh001";
        Date startDate = new StringToDate().convert("2015-05-06");
        Date endDate = new StringToDate().convert("2015-05-27");
        Long count = dao.getRequestNumFromTo(manageUnit, startDate, endDate);
        System.out.println(count);
    }

    /**
     * 按预约机构统计预约数
     *
     * @author hyj
     */
    public void testGetTargetNumFromTo() {
        String manageUnit = "eh001";
        Date startDate = new StringToDate().convert("2015-05-06");
        Date endDate = new StringToDate().convert("2015-05-27");
        Long count = dao.getTargetNumFromTo(manageUnit, startDate, endDate);
        System.out.println(count);
    }

    /**
     * 统计申请机构昨日预约数
     *
     * @author hyj
     */
    public void testGetRequestNumForYesterday() {
        String manageUnit = "eh001";
        Long count = dao.getRequestNumForYesterday(manageUnit);
        System.out.println(count);

    }

    /**
     * 统计申请机构今日预约数
     *
     * @author hyj
     */
    public void testGetRequestNumForToday() {
        String manageUnit = "eh001";
        Long count = dao.getRequestNumForToday(manageUnit);
        System.out.println(count);
    }

    /**
     * 统计预约机构昨日预约数
     *
     * @author hyj
     */
    public void testGetTargetNumForYesterday() {
        String manageUnit = "eh001";
        Long count = dao.getTargetNumForYesterday(manageUnit);
        System.out.println(count);

    }

    /**
     * 统计预约机构今日预约数
     *
     * @author hyj
     */
    public void testGetTargetNumForToday() {
        String manageUnit = "eh001";
        Long count = dao.getTargetNumForToday(manageUnit);
        System.out.println(count);
    }

    /**
     * 患者预约成功发给患者
     *
     * @author hyj
     */
    public void testSendPatientAppointmentMsgToPatient() {
        //
//        AppointRecord ar = dao.getByAppointRecordId(739);
//        dao.sendPatientAppointmentMsgToPatient(ar);
//        // 将线程睡眠2秒，否则短信发送不成功
//        try {
//            TimeUnit.SECONDS.sleep(60 * 5);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
    }

    /**
     * 医生预约成功发送给患者
     *
     * @author hyj
     */
    public void testSendDoctorAppointmentMsgToPatient() {
        //
        AppointRecord ar = dao.getByAppointRecordId(607);
//        dao.sendDoctorAppointmentMsgToPatient(ar);
        // 将线程睡眠2秒，否则短信发送不成功
        try {
            TimeUnit.SECONDS.sleep(60 * 4);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 医生预约成功发送给医生
     *
     * @author zsq
     */
    public void testSendDoctorAppointmentMsgToDoctor() {
        //
        AppointRecord ar = dao.getByAppointRecordId(476);
//        dao.sendDoctorAppointmentMsgToDoctor(ar);
        // 将线程睡眠2秒，否则短信发送不成功
        try {
            TimeUnit.SECONDS.sleep(60 * 4);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 患者预约特需门诊成功发送给患者
     *
     * @author zsq
     */
    public void testSendPatientAppointmentVIPClinicMsgToPatient() {
        //
        AppointRecord ar = dao.getByAppointRecordId(477);
//        dao.sendPatientAppointmentVIPClinicMsgToPatient(ar);
        // 将线程睡眠2秒，否则短信发送不成功
        try {
            TimeUnit.SECONDS.sleep(60 * 4);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 医生预约特需门诊成功发送给患者
     *
     * @author zsq
     */
    public void testSendDoctorAppointmentVIPClinicMsgToPatient() {
        //
        AppointRecord ar = dao.getByAppointRecordId(465);
//        dao.sendDoctorAppointmentVIPClinicMsgToPatient(ar);
        // 将线程睡眠2秒，否则短信发送不成功
        try {
            TimeUnit.SECONDS.sleep(2);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 医生预约特需门诊成功发送给医生
     *
     * @author zsq
     */
    public void testSendDoctorAppointmentVIPClinicMsgToDoctor() {
        //
        AppointRecord ar = dao.getByAppointRecordId(442);
//        dao.sendDoctorAppointmentVIPClinicMsgToDoctor(ar);
        // 将线程睡眠2秒，否则短信发送不成功
        try {
            TimeUnit.SECONDS.sleep(60 * 4);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 患者预约取消发送给患者
     *
     * @author zsq
     */
    public void testSendPatientAppointmentCancelMsgToPatient() {
        //
        AppointRecord ar = dao.getByAppointRecordId(487);
//        dao.sendPatientAppointmentCancelMsgToPatient(ar);
        // 将线程睡眠2秒，否则短信发送不成功
        try {
            TimeUnit.SECONDS.sleep(60 * 4);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 医生预约取消发送给患者
     *
     * @author zsq
     */
    public void testSendDoctorAppointmentCancelMsgToPatient() {
        //
        AppointRecord ar = dao.getByAppointRecordId(516);
//        dao.sendDoctorAppointmentCancelMsgToPatient(ar);
        // 将线程睡眠2秒，否则短信发送不成功
        try {
            TimeUnit.SECONDS.sleep(60 * 4);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 医生预约取消发送给医生
     *
     * @author zsq
     */
    public void testSendDoctorAppointmentCancelMsgToDoctor() {
        //
        AppointRecord ar = dao.getByAppointRecordId(454);
//        dao.sendDoctorAppointmentCancelMsgToDoctor(ar);
        // 将线程睡眠2秒，否则短信发送不成功
        try {
            TimeUnit.SECONDS.sleep(60 * 4);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void testReTryAppoint() {
        Integer appointRecordId = 10000222;
        dao.reTryAppoint(appointRecordId);
    }

    /**
     * 云门诊预约记录增加服务
     *
     * @throws DAOException
     * @author hyj
     */
    public void testAddAppointRecordForCloudClinic() throws DAOException {
        AppointSourceDAO aDao = DAOFactory.getDAO(AppointSourceDAO.class);
        DoctorDAO dDao = DAOFactory.getDAO(DoctorDAO.class);
        PatientDAO pDao = DAOFactory.getDAO(PatientDAO.class);
        Doctor appoint = dDao.get(4700);
        AppointSource as = aDao.get(1453132);
        AppointSource as2 = aDao.get(1453132);
        Patient p = pDao.get("2c9081834f5f3775014f602fb45d0000");
        AppointRecord r = new AppointRecord();
        r.setMpiid(p.getMpiId());
        r.setPatientName(p.getPatientName());
        r.setCertId(p.getIdcard());
        r.setLinkTel(p.getMobile());
        r.setAppointSourceId(as.getAppointSourceId());
        r.setOrganId(as.getOrganId());
        r.setAppointDepartId(as.getAppointDepartCode());
        r.setAppointDepartName(as.getAppointDepartName());
        r.setDoctorId(as.getDoctorId());
        r.setAppointUser(appoint.getDoctorId().toString());
        r.setWorkDate(as.getWorkDate());
        r.setWorkType(as.getWorkType());
        r.setSourceType(as.getSourceType());
        r.setStartTime(as.getStartTime());
        r.setEndTime(as.getEndTime());
        r.setOrderNum(as.getOrderNum());
        r.setAppointRoad(1);
        r.setAppointStatus(1);
        r.setAppointDate(new Date());
        r.setAppointName(appoint.getName());
        r.setAppointOragn(appoint.getOrgan().toString());
        r.setClinicPrice(as.getPrice());
        r.setTransferId(0);
        r.setSourceLevel(as.getSourceLevel());
        r.setClinicObject(as.getCloudClinicType());
        r.setOppType(1);
        r.setOppOrgan(as2.getOrganId());
        r.setOppdepart(as2.getAppointDepartCode());
        r.setOppdepartName(as2.getAppointDepartName());
        r.setOppdoctor(as2.getDoctorId());
        List<AppointRecord> list = new ArrayList<AppointRecord>();
        list.add(r);
        AppointRecord rr = new AppointRecord();
        rr.setMpiid(p.getMpiId());
        rr.setPatientName(p.getPatientName());
        rr.setCertId(p.getIdcard());
        rr.setLinkTel(p.getMobile());
        rr.setAppointSourceId(as2.getAppointSourceId());
        rr.setOrganId(as2.getOrganId());
        rr.setAppointDepartId(as2.getAppointDepartCode());
        rr.setAppointDepartName(as2.getAppointDepartName());
        rr.setDoctorId(as2.getDoctorId());
        rr.setAppointUser(appoint.getDoctorId().toString());
        rr.setWorkDate(as2.getWorkDate());
        rr.setWorkType(as2.getWorkType());
        rr.setSourceType(as2.getSourceType());
        rr.setStartTime(as2.getStartTime());
        rr.setEndTime(as2.getEndTime());
        rr.setOrderNum(as2.getOrderNum());
        rr.setAppointRoad(1);
        rr.setAppointStatus(1);
        rr.setAppointDate(new Date());
        rr.setAppointName(appoint.getName());
        rr.setAppointOragn(appoint.getOrgan().toString());
        rr.setClinicPrice(as2.getPrice());
        rr.setTransferId(0);
        rr.setSourceLevel(as2.getSourceLevel());
        rr.setClinicObject(as2.getCloudClinicType());
        rr.setOppType(1);
        rr.setOppOrgan(as.getOrganId());
        rr.setOppdepart(as.getAppointDepartCode());
        rr.setOppdepartName(as.getAppointDepartName());
        rr.setOppdoctor(as.getDoctorId());
        list.add(rr);
        AppointRecordDAO dao = appContext.getBean("appointRecordDAO",
                AppointRecordDAO.class);
        boolean flag = dao.addAppointRecordForCloudClinic(list);
        System.out.print(flag);
    }

    /**
     * 新增在线云门诊
     *
     * @throws DAOException
     * @author zhangx
     * @date 2015-12-28 下午3:56:18
     */
    public void testAddAppointRecordForOnlineCloudClinic() throws DAOException {
        AppointRecord r = new AppointRecord();
        r.setMpiid("2c9081814cd4ca2d014cd4ddd6c90000");
        r.setPatientName("张肖");
        r.setCertId("33108119920702674x");
        r.setLinkTel("18768177768");
        r.setDoctorId(1178);
        r.setAppointUser("1182");
        AppointRecordDAO dao = appContext.getBean("appointRecordDAO",
                AppointRecordDAO.class);
        try {
            dao.addAppointRecordForOnlineCloudClinic(r);
        } catch (ControllerException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    /**
     * 获取预约详情信息
     *
     * @author zhangx
     * @date 2015-12-29 下午5:33:46
     */
    public void testGetFullAppointRecordById() {
        AppointRecordDAO dao = appContext.getBean("appointRecordDAO",
                AppointRecordDAO.class);

        AppointRecordAndPatientAndDoctor r = dao.getFullAppointRecordById(1354);
        System.out.println(JSONUtils.toString(r));
    }

    /**
     * 远程会诊开始服务测试
     *
     * @throws DAOException
     */
    public void testStartRemoteInquiry() throws DAOException {
        String telClinicId = "14513561944421591";
        AppointRecordDAO dao = appContext.getBean("appointRecordDAO",
                AppointRecordDAO.class);
        boolean flag = dao.startRemoteInquiry(telClinicId);
        System.out.println(flag);
    }

    /**
     * 远程会诊结束服务测试
     *
     * @throws DAOException
     */
    public void testEndRemoteInquiry() throws DAOException {
        String telClinicID = "14513561944421591";
        AppointRecordDAO dao = appContext.getBean("appointRecordDAO",
                AppointRecordDAO.class);
        dao.endRemoteInquiry(telClinicID);
    }

    /**
     * Title:测试按就诊机构，就诊日期 查询该就诊日期当天的全部有效的预约记录 Description:
     *
     * @throws DAOException void
     * @author AngryKitty
     * @date 2015-8-21
     */
    public void testFindByOrganIdAndWorkDate() throws DAOException {
        AppointRecordDAO dao = appContext.getBean("appointRecordDAO",
                AppointRecordDAO.class);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Date date = null;
        try {
            date = sdf.parse("2015-07-07");
        } catch (ParseException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        List<AppointRecord> list = dao
                .findByOrganIdAndWorkDate(1, date, 0, 100);
        System.out.println(list.size());
        System.out.println(JSONUtils.toString(list));
    }

    /**
     * Title: 测试预约统计 Description:
     *
     * @author AngryKitty
     * @date 2015-8-31 void
     */
    public void testFindAppointRecordByStatic() {
        Date startTime = new StringToDate().convert("2015-05-06");
        Date endTime = new StringToDate().convert("2015-06-17");
        AppointRecordDAO dao = appContext.getBean("appointRecordDAO",
                AppointRecordDAO.class);
        QueryResult<AppointRecordAndDoctors> qr = dao
                .findAppointRecordAndDoctorsByStatic(startTime, endTime, null,
                        0, null, null, null);

        System.out.println(qr.getTotal());
        System.out.println(JSONUtils.toString(qr.getItems()));

    }

    public void testfindTransferByAppointUserAndAppointStatus() {

        AppointRecordDAO dao = appContext.getBean("appointRecordDAO",
                AppointRecordDAO.class);
        List<AppointRecord> list = dao
                .findTransferByAppointUserAndAppointStatus("11822", 6, 0);
        System.out.println(JSONUtils.toString(list));
    }

    /**
     * 预约申请列表
     *
     * @param appointUser 预约提交人编号
     * @param flag        标志--0：全部，1：待就诊，2：已就诊
     * @param mark        标志-0未就诊1已完成
     * @param start       分页开始位置
     * @param limit       每页限制条数
     * @author luf
     */
    public void testQueryRequestAppointList() {
        String appointUser = "40";
        int flag = 0;
        int mark = 1;
        int start = 0;
        int limit = 10;
        Hashtable<String, List<AppointRecordBean>> apd = dao
                .queryRequestAppointList(appointUser, flag, mark, start, limit);
        System.out.println(JSONUtils.toString(apd));
        System.out.println(JSONUtils.toString(apd.get("unfinished")));
        System.out.println(apd.get("unfinished").size());
        System.out.println(JSONUtils.toString(apd.get("completed")));
        System.out.println(apd.get("completed").size());
    }

    /**
     * 过滤预约记录并进行时间转换
     *
     * @author luf
     */
    public void testConvertAppointRecordForRequestList() {
        AppointRecord appointRecord = new AppointRecord();
        appointRecord.setAppointDate(DateConversion.getCurrentDate(
                "2015-11-06 16:01:00", "yyyy-MM-dd HH:mm:ss"));
        System.out.println((dao
                .convertAppointRecordForRequestList(appointRecord))
                .getRequestDate());
        appointRecord.setAppointDate(DateConversion.getCurrentDate(
                "2015-11-06 00:00:00", "yyyy-MM-dd HH:mm:ss"));
        System.out.println((dao
                .convertAppointRecordForRequestList(appointRecord))
                .getRequestDate());
        appointRecord.setAppointDate(DateConversion.getCurrentDate(
                "2015-11-05 00:00:00", "yyyy-MM-dd HH:mm:ss"));
        System.out.println((dao
                .convertAppointRecordForRequestList(appointRecord))
                .getRequestDate());
        appointRecord.setAppointDate(DateConversion.getCurrentDate(
                "2015-11-05 14:01:00", "yyyy-MM-dd HH:mm:ss"));
        System.out.println((dao
                .convertAppointRecordForRequestList(appointRecord))
                .getRequestDate());
        appointRecord.setAppointDate(DateConversion.getCurrentDate(
                "2015-11-05 22:07:02", "yyyy-MM-dd HH:mm:ss"));
        System.out.println((dao
                .convertAppointRecordForRequestList(appointRecord))
                .getRequestDate());
        appointRecord.setAppointDate(DateConversion.getCurrentDate(
                "2015-11-4 15:01:00", "yyyy-MM-dd HH:mm:ss"));
        System.out.println((dao
                .convertAppointRecordForRequestList(appointRecord))
                .getRequestDate());
        appointRecord.setAppointDate(DateConversion.getCurrentDate(
                "2015-11-06 17:01:00", "yyyy-MM-dd HH:mm:ss"));
        System.out.println((dao
                .convertAppointRecordForRequestList(appointRecord))
                .getRequestDate());
    }

    /**
     * 我的预约列表
     *
     * @param doctorId 医生内码
     * @param flag     标志--0：全部，1：今日就诊，2：明日就诊，3：7天内就诊，4：7天后就诊
     * @param mark     标记--0未就诊1已完成
     * @param start    分页起始位置
     * @param limit    每页限制条数
     * @return Hashtable<String, List<AppointRecordBean>>
     * @author luf
     */
    public void testQueryAppointRecordList() {
        int doctorId = 40;
        int flag = 0;
        int mark = 0;
        int start = 0;
        int limit = 10;
        Hashtable<String, List<AppointRecordBean>> arb = dao
                .queryAppointRecordList(doctorId, flag, mark, start, limit);
        System.out.println(JSONUtils.toString(arb));
        System.out.println(arb.get("unfinished").size());
        System.out.println(JSONUtils.toString(arb.get("unfinished")));
        System.out.println(arb.get("completed").size());
        System.out.println(JSONUtils.toString(arb.get("completed")));
    }

    /**
     * 预约取消服务（同时取消相应的转诊）
     *
     * @param appointRecordId 预约记录主键
     * @param cancelUser      取消人Id
     * @param cancelName      取消人姓名
     * @param cancelResean    取消原因
     * @return Boolean
     * @author luf
     */
    public void testCancelAppointAndTransfer() {
        int appointRecordId = 301;
        String cancelUser = "";
        String cancelName = "";
        String cancelResean = "";
        // Boolean cancelFlag = dao.cancelAppointAndTransfer(appointRecordId,
        // cancelUser, cancelName, cancelResean);
        // System.out.println(cancelFlag);

        AppointRecord appoint = dao.getByAppointRecordId(appointRecordId);
        TransferDAO transferDAO = DAOFactory.getDAO(TransferDAO.class);
        Transfer transfer = transferDAO.get(appoint.getTransferId());
        if (transfer != null) {
            // 取消人是医生
            if (cancelUser.length() < 32 && cancelUser.length() > 0) {
                Integer doctorId = Integer.valueOf(cancelUser);
                Doctor d = DAOFactory.getDAO(DoctorDAO.class).get(doctorId);
                transfer.setCancelDoctor(doctorId);
                transfer.setCancelOrgan(d.getOrgan());
                transfer.setCancelDepart(d.getDepartment());
            }
            transfer.setCancelCause(cancelResean);
            transfer.setTransferStatus(9);
            transfer.setCancelTime(DateConversion
                    .convertFromDateToTsp(new Date()));
            transferDAO.update(transfer);
        }
    }

    public void testSendErrMsgToDoctorForErrAppiont() {
        AppointRecord ar = dao.getByAppointRecordId(658);
        //dao.sendErrMsgToDoctorForErrAppiont(ar);
        // 将线程睡眠2秒，否则短信发送不成功
        try {
            TimeUnit.SECONDS.sleep(60 * 4);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void cancelAppoint() {
        dao.cancelAppoint(744, "1326", "张肖", "患者。。。。");
    }

    public void registeRecord() {
        dao.updateRegisteRecord(301, "1", "111");
    }

    public void testQueryByDoctorIdAndAppointDateNextWeekPageLimit() {
        System.out.println(JSONUtils.toString(dao.queryByDoctorIdAndAppointDateNextWeekPageLimit(40, 0, 10)));
    }

    public void testFindRequestAppointRecordByPage() {
        System.out.println(JSONUtils.toString(dao.findRequestAppointRecordByPage("40", 0, 10)));
    }

    public void testFindTodayAppointRecordWithPageLimit() {
        System.out.println(JSONUtils.toString(dao.findTodayAppointRecordWithPageLimit(1180, 0, 10)));
    }

    public void testFindAppointRecordByAppointSourceId() {
        List<AppointRecord> appointRecords = dao.findAppointRecordByAppointSourceId(594759);
        System.out.println("=====:" + appointRecords.size());
        System.out.println(JSONUtils.toString(appointRecords));
    }

    public void testGetFullAppointRecordByIdWithQueue() {
        int appointRecordId = 5952;
        int doctorId = 1176;
        AppointRecordAndPatientAndDoctor ara = dao.getFullAppointRecordByIdWithQueue(appointRecordId, doctorId);
        System.out.println(JSONUtils.toString(ara));
    }

    public void testUpdateClinicStatusToEnd() {
        dao.updateClinicStatusToEnd();
    }

    public void testfindAppointRecordAndSetByDoctorIdAndPlatform() {
        Integer doctorId = 9566;
        Date now = DateConversion.parseDate("2016-09-25", DateConversion.YYYY_MM_DD);
        String platform = CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_XIAOYU;
    }
    public void testfindTodayOutAppointList(){Date now= Context.instance().get("date.today",Date.class);
        System.out.println(JSONUtils.toString(dao.findTodayOutAppointList(9566,now, "xiaoyu")));
    }

    public void testGetSummary() {
        System.out.println(dao.getSummary("14581043194482595", 1177));
    }

    public void testSddSummary() {
        String telClinicId = "14581043194482595";
        int doctorId = 1177;
        String summary = "面色潮红";
        try {
            dao.addSummary(telClinicId, doctorId, summary);
        }catch (ControllerException e){
            e.printStackTrace();
        }
    }


    public void testUpdateExeRegisteAppointStatus(){
        int appointId = 301;
        //int appointStatus = 4;
        dao.updateExeRegisteAppointStatus(appointId);
    }


    public void testUpdateStatus(){
        int appointRecordId = 1111111112;
        int appointStatus = 2;
        dao.updateStatus(appointStatus,"test11111111",appointRecordId);
    }
}
