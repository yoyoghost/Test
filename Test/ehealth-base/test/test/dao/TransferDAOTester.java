/*
package test.dao;

import ctd.controller.exception.ControllerException;
import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.context.Context;
import ctd.util.converter.support.StringToDate;
import eh.bus.dao.AppointInhospDAO;
import eh.bus.dao.AppointRecordDAO;
import eh.bus.dao.TransferDAO;
import eh.entity.base.Doctor;
import eh.entity.bus.AppointInhosp;
import eh.entity.bus.AppointRecord;
import eh.entity.bus.Transfer;
import eh.entity.bus.TransferAndPatient;
import eh.entity.cdr.Otherdoc;
import eh.entity.his.AppointInHosRequest;
import eh.entity.msg.SmsInfo;
import eh.remote.IHisServiceInterface;
import eh.task.executor.AliSmsSendExecutor;
import eh.utils.DateConversion;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class TransferDAOTester extends TestCase {
    private static ClassPathXmlApplicationContext appContext;
    private static TransferDAO bdd;

    static {
        appContext = new ClassPathXmlApplicationContext("spring.xml");
        bdd = appContext.getBean("transferDAO", TransferDAO.class);
    }

    public void testGetPatTransferById() {
//		int transferId = 913;
//		System.out.println(JSONUtils.toString(bdd.getPatTransferById(transferId)));

        String tr = "{answerTel:\"13750840252\",\n" +
                "diagianName:\"未确诊\",\n" +
                "mpiId:\"2c90818253e5268d0153e55735e10000\",\n" +
                "patientCondition:\"区委区为哦in\",\n" +
                "requestMpi:\"2c90818253e5268d0153e55735e10000\",\n" +
                "targetDepart:511,\n" +
                "targetDoctor:9537,\n" +
                "targetOrgan:1000017,\n" +
                "transferCost:0,\n" +
                "transferPrice:0,\n" +
                "transferType:1}";
        Transfer t = JSONUtils.parse(tr, Transfer.class);
        String l = "[]";
        List s = JSONUtils.parse(l, ArrayList.class);
        try {
            bdd.createPatientTransferAndOtherDoc(t, s);
        } catch (ControllerException e) {
            e.printStackTrace();
        }
    }

    public void testGetPatTransferByIdNew() {
//		int transferId = 1329;
        bdd.findPatTransferList("2c90818253e5268d0153e55735e10000", 0, 10);
//		System.out.println(JSONUtils.toString(bdd.getPatTransferByIdNew(
//				transferId, 1292)));
    }

    public void testCreateTransferAndOtherDoc() {
        Transfer tran = new Transfer();
        tran.setRequestDepart(70);
        tran.setRequestOrgan(1);
        tran.setRequestDoctor(1178);
        tran.setTargetOrgan(1);
        tran.setTargetDepart(70);
        tran.setTargetDoctor(1180);
        tran.setMpiId("2c9081814cc3ad35014cc3e0361f0000");

        try {
            bdd.createTransferAndOtherDoc(tran, new ArrayList<Otherdoc>());
        } catch (DAOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (ControllerException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void testgetApplyingTransferRecordByMpiId() {
        System.out.println(JSONUtils.toString(bdd.getApplyingTransferRecordByMpiId("2c9081895307ce7501531cd4a6240007")));
    }

    */
/**
     * 测试名:获取待处理转诊单数测试
     *
     * @author yxq
     *//*

    public void testGetUnTransferNum() throws DAOException {
        int doctorId = 1178;
        boolean groupFlag = true;
        long result = 0;
        result = bdd.getUnTransferNum(doctorId, groupFlag);
        System.out.println("result1 + result2 = " + result);
    }

    */
/**
     * 测试名:查询转诊申请单列表服务测试 备注：显示该医生正在申请中的转诊申请单列表
     *
     * @author yxq
     *//*

    public void testQueryHisTransfer() throws DAOException {
        int doctorId = 1;
        List<TransferAndPatient> result = bdd.queryHisTransfer(doctorId);
        System.out.println("length = " + result.size());
        for (int i = 0; i < result.size(); i++) {
            System.out.println(JSONUtils.toString(result.get(i)));
        }
    }

    */
/**
     * 测试名：查询待处理转诊单列表服务测试
     *
     * @author yxq
     *//*

    public void testGetQueryTransfer() throws DAOException {
        int doctorId = 40;
        boolean groupFlag = true;
        List<TransferAndPatient> result = bdd
                .queryTransfer(doctorId, groupFlag);
        System.out.println("length = " + result.size());
        for (int i = 0; i < result.size(); i++) {
            System.out.println(JSONUtils.toString(result.get(i)));
        }
    }

    */
/**
     * 测试名：获取转诊单信息服务测试
     *
     * @author yxq
     *//*

    public void testGetTransferByID() throws DAOException {
        int transferId = 326;
        TransferAndPatient result = bdd.getTransferByID(transferId);
        System.out.println(JSONUtils.toString(result));
    }

    */
/**
     * 测试名：获取转诊单信息服务测试
     *
     * @author yxq
     *//*

    public void testGetTransferAndCdrById() throws DAOException {
        int transferId = 1089;
        TransferAndPatient result = bdd.getTransferAndCdrById(transferId);
        System.out.println(JSONUtils.toString(result));
    }

    */
/**
     * 测试名：获取转诊单信息服务测试
     *
     * @author yxq
     *//*

    public void testGetInhospTransfeById() throws DAOException {
        int transferId = 328;
        Map<String, Object> map = bdd.getInhospTransfeByIdNew(transferId, 40);
        System.out.println(JSONUtils.toString(map));
    }

    */
/**
     * 测试名：按照医生查询历史转诊单列表服务测试
     *
     * @author yxq
     *//*

    public void testGetHisByDoctor() {
        int doctorId = 2;
        String start = "2015-01-01 13:15:26";
        String end = "2015-12-14 13:15:26";
        Date startDate = new Date();
        Date endDate = new Date();
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            startDate = df.parse(start);
            endDate = df.parse(end);
        } catch (Exception e) {
            e.printStackTrace();
        }
        List<TransferAndPatient> result = bdd.getHisByDoctor(doctorId,
                startDate, endDate);
        System.out.println(result.size());
        for (int i = 0; i < result.size(); i++) {
            System.out.println(JSONUtils.toString(result.get(i)));
        }
    }

    */
/**
     * 测试名：按照主索引查询历史转诊单列表服务测试
     *
     * @author yxq
     *//*

    public void testGetHisByMpi() {
        String mpiId = "402881834b6d0cfc014b6d0d04f10000";
        String start = "2015-01-01 13:15:26";
        String end = "2015-03-14 13:15:26";
        Date startDate = new Date();
        Date endDate = new Date();
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            startDate = df.parse(start);
            endDate = df.parse(end);
        } catch (Exception e) {
            e.printStackTrace();
        }
        List<Transfer> result = bdd.getHisByMpi(mpiId, startDate, endDate);
        System.out.println(result.size());
        for (int i = 0; i < result.size(); i++) {
            System.out.println(JSONUtils.toString(result.get(i)));
        }
    }

    */
/**
     * 患者申请特需预约
     *
     * @throws DAOException
     * @throws ControllerException
     * @author zhangx
     * @date 2016-1-19 上午10:34:49
     *//*

    public void testCreatePatientTransferAndOtherDoc() throws DAOException,
            ControllerException {
        Transfer trans = new Transfer();
        trans.setRequestMpi("2c9081814cc3ad35014cc3e0361f0000");
        trans.setAnswerTel("1778890568");
        trans.setDiagianName("1");
        trans.setEmergency(0);
        trans.setMpiId("2c9081814cc3ad35014cc3e0361f0000");
        trans.setPatientCondition("1");
        trans.setPatientRequire("3天内");
        trans.setTargetDoctor(40);
        trans.setTargetDepart(70);
        trans.setTargetOrgan(1);
        trans.setTransferPrice(30d);
        trans.setTransferCost(30d);
        trans.setTransferType(1);
        trans.setPayflag(1);

        List<Otherdoc> cdrOtherdocs = new ArrayList<Otherdoc>();

        bdd.createPatientTransferAndOtherDoc(trans, cdrOtherdocs);
    }

    */
/**
     * 测试名：转诊审核开始服务测试
     *
     * @author yxq
     *//*

    public void testStartTransfer() {
        int transferId = 909;
        int agreeDoctor = 11822;
        boolean flag = bdd.startTransfer(transferId, agreeDoctor);
        System.out.println(flag);
    }

    */
/**
     * 测试名：转诊审核确认服务测试
     *
     * @author yxq
     *//*

    public void testConfirmTransfer() {
        Transfer tr = new Transfer();
        tr.setTransferId(909);
        tr.setAgreeDoctor(11822);
        tr.setTransferResultType(1);
        tr.setConfirmOrgan(1);
        tr.setConfirmDepart(70);
        tr.setConfirmDoctor(11822);
        tr.setConfirmClinicTime(Timestamp.valueOf("2016-08-28 12:10:12"));
        tr.setConfirmClinicAddr("地址1");
        tr.setReturnMess("");
        tr.setClinicPrice(16d);
        tr.setSourceLevel(2);
        tr.setAppointDepartId("528");

        bdd.confirmTransfer(tr);
    }

    */
/**
     * 测试名：转诊审核拒绝服务测试
     *
     * @author yxq
     *//*

    public void testRefuseTransfer() {
        int TransferId = 1089;
        int AgreeDoctor = 1292;
        String aTime = "2016-01-29 13:15:26";
        Date AgreeTime = new Date();
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            AgreeTime = df.parse(aTime);
        } catch (Exception e) {
            e.printStackTrace();
        }
        String RefuseCause = "拒绝理由";
        bdd.refuseTransfer(TransferId, AgreeDoctor, new Date(), RefuseCause);
    }

    */
/**
     * 测试名:转诊拒绝理由字典查询服务
     *
     * @author yxq
     *//*

    public void testRefuseTransferDic() {
        try {
            Map<Integer, String> result = bdd.refuseTransferDic();
            for (int i = 0; i < result.size(); i++) {
                System.out.println(result.get(i));
            }
        } catch (ControllerException e) {
            e.printStackTrace();
        }
    }

    */
/**
     * 服务名:转诊单执行接收服务
     *//*

    public void testUpdateExeTransfer() {
        int transferId = 1;
        Date date = new Date();
        Timestamp t = new Timestamp(date.getTime());
        Date exeTime = t;
        int transferStatus = 3;
        bdd.updateExeTransfer(transferId, exeTime, transferStatus);
    }

    */
/**
     * 服务名:消息推送
     *//*

    public void testMsgPush() {
        TransferAndPatient tp = new TransferAndPatient();

        Transfer t = new Transfer();
        t.setTransferId(118);

        tp.setTransfer(t);
        tp.setRequestDoctorMobile("15990151091");
        tp.setPatientMobile("15990092533");

//        bdd.MsgPush(tp, "您有一条转诊申请已被处理", "转诊提醒", "详细信息提醒");
    }

    */
/**
     * 根据病人mpiId查询申请中或者审核开始的转诊记录
     *
     * @return
     *//*

    public void testGetApplyingTransferByMpiId() {
        TransferAndPatient tp = bdd
                .getApplyingTransferByMpiId("2c908182528b833901528bf8e6ac0000");
        System.out.println(JSONUtils.toString(tp));
    }

    */
/**
     * 获取当天总转诊数
     *
     * @author ZX
     * @date 2015-4-23 下午5:18:11
     *//*

    public void testGetAllNowTranNum() {
        Date requestTime = new StringToDate().convert("2015-04-20");
        Long num = bdd.getAllNowTranNum(requestTime);
        System.out.println(num);
    }

    */
/**
     * his返回的时候更新状态
     *
     * @author ZX
     * @date 2015-4-23 下午5:18:22
     *//*

    public void testUpdateTransferFromHosp() {

        System.out.println(JSONUtils.toString(bdd.getById(307)));

        // bdd.updateTransferFromHosp(307);
    }

    */
/**
     * 获取当天人均转诊数
     *
     * @author ZX
     * @date 2015-4-23 下午5:18:22
     *//*

    public void testGetAverageNum() {
        Date requestTime = new StringToDate().convert("2015-04-20");
        Double num = bdd.getAverageNum(requestTime);
        System.out.println(num);
    }

    */
/**
     * 住院转诊确认
     *
     * @author ZX
     * @date 2015-4-23 下午5:54:31
     *//*

    public void testConfirmTransferWithInHospital() {
        Transfer transfer = new Transfer();
        transfer.setTransferId(307);
        transfer.setAgreeDoctor(1182);
        transfer.setConfirmOrgan(1);
        transfer.setConfirmDepart(70);
        transfer.setConfirmDoctor(1182);
        transfer.setConfirmClinicTime(Timestamp.valueOf("2015-05-20 12:00:00"));
        transfer.setReturnMess("留言");
        transfer.setDiagianName("诊断");

        AppointInhosp appointInhosp = new AppointInhosp();
        appointInhosp.setClinicDepart(71);
        appointInhosp.setNearbyReceive(1);
        appointInhosp.setAdmissionExam(0);
        appointInhosp.setAdmissionExamItem("入院检查项目");
        appointInhosp.setSpecialExam(0);
        appointInhosp.setSpecialExamItem("特殊检查项目");
        appointInhosp.setIsOperation(0);
        appointInhosp.setOperationDate(new StringToDate().convert("2015-06-20"));
        appointInhosp.setPrepayment(5000);
        bdd.confirmTransferWithInHospital(transfer, appointInhosp);
    }

    */
/**
     * 转诊his失败，新增系统消息
     *
     * @author ZX
     * @date 2015-4-30 下午4:43:43
     *//*

    public void testSenMsgForFail() {
        TransferAndPatient tp = bdd.getTransferAndCdrById(335);
        //bdd.sendMsgForTransFail(tp);
    }

    */
/**
     * 获取医生近一个月的转诊记录
     *
     * @author ZX
     * @date 2015-4-30 下午4:47:40
     *//*

    public void tesGetHisByDoctorLastMonth() {
        System.out.println(bdd.getHisByDoctorLastMonth(1182).size());
    }

    */
/**
     * 发送转诊请求到his
     *//*

    public void testSendtoHis() {
        AppointInhospDAO dao = DAOFactory.getDAO(AppointInhospDAO.class);
        IHisServiceInterface appointService = AppContextHolder.getBean(
                "h1.appointInHosService", IHisServiceInterface.class);
        appointService.registInHosAppoint(new AppointInHosRequest());
        System.out.println("");
        // AppointInhosp app=dao.get(13);
        // bdd.sendToHis(app);
    }

    */
/**
     * 统计查询
     *
     * @author ZX
     * @date 2015-5-8 下午1:00:05
     *//*

    public void testFindTransferWithStatic() {
        Date startTime = new StringToDate().convert("2015-05-06");
        Date endTime = new StringToDate().convert("2015-05-17");

        Transfer tran = new Transfer();
        tran.setRequestOrgan(1);
        // tran.setRequestDoctor(1182);
        // tran.setTargetOrgan(1);
        // tran.setTargetDoctor(1178);
        // tran.setTransferStatus(transferStatus);
        int start = 0;

        List<TransferAndPatient> list = bdd.findTransferWithStatic(startTime,
                endTime, tran, start);
        System.out.println(list.size());
        System.out.println(JSONUtils.toString(list));
    }

    */
/**
     * 统计查询条数
     *
     * @author ZX
     * @date 2015-5-8 下午1:00:05
     *//*

    public void testGetNumWithStatic() {
        Date startTime = new StringToDate().convert("2015-05-06");
        Date endTime = new StringToDate().convert("2015-05-17");

        Transfer tran = new Transfer();
        // tran.setRequestOrgan(1);
        // tran.setRequestDoctor(1182);
        // tran.setTargetOrgan(1);
        // tran.setTargetDoctor(1178);
        // tran.setTransferStatus(transferStatus);

        long list = bdd.getNumWithStatic(startTime, endTime, tran);

        System.out.println(JSONUtils.toString(list));
    }

    */
/**
     * 申请方昨日转诊总数统计
     *
     * @author ZX
     * @date 2015-5-25 下午3:56:05
     *//*

    public void testGetRequestNumForYestoday() {
        String manageUnit = "eh";
        long list = bdd.getRequestNumForYesterday(manageUnit);
        System.out.println(JSONUtils.toString(list));
    }

    */
/**
     * 申请方今日转诊总数统计
     *
     * @author ZX
     * @date 2015-5-25 下午3:56:48
     *//*

    public void testGetRequestNumForToday() {
        String manageUnit = "eh";
        long list = bdd.getRequestNumForToday(manageUnit);
        System.out.println(JSONUtils.toString(list));
    }

    */
/**
     * 申请方总转诊数
     *
     * @author ZX
     * @date 2015-5-25 下午3:57:17
     *//*

    public void testGetRequestNum() {
        String manageUnit = "eh";
        long list = bdd.getRequestNum(manageUnit);
        System.out.println(JSONUtils.toString(list));
    }

    */
/**
     * 接收方昨日转诊总数统计
     *
     * @author ZX
     * @date 2015-5-25 下午3:56:05
     *//*

    public void testGetTargetNumForYestoday() {
        String manageUnit = "eh";
        long list = bdd.getTargetNumForYesterday(manageUnit);
        System.out.println(JSONUtils.toString(list));
    }

    */
/**
     * 接收方今日转诊总数统计
     *
     * @author ZX
     * @date 2015-5-25 下午3:56:48
     *//*

    public void testGetTargetNumForToday() {
        String manageUnit = "eh";
        long list = bdd.getTargetNumForToday(manageUnit);
        System.out.println(JSONUtils.toString(list));
    }

    */
/**
     * 转诊拒绝测试
     *
     * @author liqifei
     * @date 2015-6-19 上午10:57:17
     *//*


    public void sendSmsForTransferDenyTester() {
        try {
            int transferId = 405;
            TransferAndPatient tp = bdd.getTransferByID(transferId);

            //bdd.sendSmsForTransferDeny(tp);

            // 将线程睡眠2秒，否则短信发送不成功
            TimeUnit.SECONDS.sleep(2);

        } catch (InterruptedException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
    }

    */
/**
     * 转诊申请发给目标医生
     *
     * @author ZX
     * @date 2015-6-15 下午6:26:01
     *//*

    public void testSendSmsForTransferApply() {
        try {
            int transferId = 0;
            TransferAndPatient tp = bdd.getTransferByID(transferId);

//			bdd.sendSmsForTransferApply(tp);

            // 将线程睡眠2秒，否则短信发送不成功
            TimeUnit.SECONDS.sleep(2);

        } catch (InterruptedException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
    }

    */
/**
     * 转诊申请发给目标医生
     *
     * @author ZXQ
     * @date 2015-8-31
     *//*

    public void testSendSmsForTransferApplyNew() {
        try {
            int transferId = 0;
            TransferAndPatient tp = new TransferAndPatient();
            Transfer transfer = new Transfer();
            transfer.setRequestDoctor(1895);
            transfer.setTargetDoctor(1895);
            tp.setTransfer(transfer);

            tp.setPatientName("张宪强1");
            tp.setTargetDoctorMobile("18868744478");

//			bdd.sendSmsForTransferApply(tp);

            // 将线程睡眠2秒，否则短信发送不成功
            TimeUnit.SECONDS.sleep(2);

        } catch (InterruptedException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
    }

    */
/**
     * 转诊特需/普通门诊接收发送给申请医生
     *
     * @author ZX
     * @date 2015-6-19 下午4:12:26
     *//*

    public void testSendSmsForTransferConfirmForRequestDoc() {
        try {
            int transferId = 0;
            TransferAndPatient tp = bdd.getTransferByID(transferId);

            //bdd.sendSmsForTransferConfirmForRequestDoc(tp, null);

            // 将线程睡眠2秒，否则短信发送不成功
            TimeUnit.SECONDS.sleep(2);

        } catch (InterruptedException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
    }

    */
/**
     * 转诊住院接受测试
     *
     * @author liqifei
     * @date 2015-6-19 下午3:57:17
     *//*

    public void testsendSmsForTransferAgreeTester() {
        try {
            int transferId = 60461;
            TransferAndPatient tp = bdd.getTransferByID(transferId);
            System.out.println(JSONUtils.toString(tp));
//            bdd.sendSmsForTransferInHosp(tp);

            // 将线程睡眠2秒，否则短信发送不成功
            TimeUnit.SECONDS.sleep(2);

        } catch (InterruptedException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
    }

    public void testReTryAppointInHosp() {
        Integer appointInHospId = 12;
        bdd.reTryAppointInHosp(appointInHospId);
    }

    public void testQueryHisTransferWithStart() {
        List<TransferAndPatient> list = bdd.queryHisTransferWithStart(1178, 0,
                10);
        System.out.println(JSONUtils.toString(list));
    }

    public void testUpdateTransRecordById() {
        bdd.updateInsuRecordById(1, 288);
    }

    public void testRegistTransfer() {
        Transfer ts = bdd.getById(100);

        bdd.registTransfer(ts);
        try {
            TimeUnit.SECONDS.sleep(20);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    */
/**
     * 新的短信发送方式 wnw
     *
     * @throws InterruptedException
     *//*

    public void testSendSmsToDoctorForTransferApply()
            throws InterruptedException {
        SmsInfo info = new SmsInfo();
        info.setBusId(312);// 业务表主键
        info.setBusType("transfer");// 业务类型
        info.setSmsType("transferapply");// 转诊申请提醒
        info.setStatus(0);
        info.setOrganId(0);// 短信服务对应的机构， 0代表通用机构
        AliSmsSendExecutor exe = new AliSmsSendExecutor(info);
        TimeUnit.SECONDS.sleep(3);// 单元测试时 此处sleep3秒 确保所有rpc服务online,正式环境不需要
        exe.execute();
        TimeUnit.SECONDS.sleep(300);
    }

    public void testConfirmTransferForZzyy() throws InterruptedException {

        Transfer tr = bdd.getById(902);
        bdd.confirmTransfer(tr);
        TimeUnit.SECONDS.sleep(200);
    }

    */
/**
     * 获取历史会诊单列表
     *
     * @author luf
     *//*

    public void testQueryTransferHisWithPage() {
        Integer doctorId = 40;
        int start = 0;
        int limit = 10;
        // List<TransferAndPatient> taps =
        bdd.queryTransferHisWithPage(doctorId, start, limit);
        // System.out.println(JSONUtils.toString(taps));
        // System.out.println(taps.size());
    }

    */
/**
     * 获取历史会诊单列表
     *
     * @author luf
     *//*

    public void testGetHisByDoctorWithStart() {
        Date startDate = DateConversion
                .getCurrentDate("2010-1-1", "yyyy-MM-dd");
        Date endDate = DateConversion
                .getCurrentDate("2050-12-31", "yyyy-MM-dd");
        Integer doctorId = 40;
        int start = 0;
        // List<TransferAndPatient> transferAndPatients =
        bdd.getHisByDoctorWithStart(doctorId, startDate, endDate, start);
    }

    */
/**
     * 获取转诊详情单（包括预约信息）
     *
     * @return TransferAndPatient
     * @author luf
     *//*

    public void testGetTransferAndAppointById() {
        Integer transferId = 1357;
        TransferAndPatient // tap = bdd.getTransferAndAppointById(transferId);
                tap = bdd.getTransferAndAppointByIdNew(transferId, 1292);
        System.out.println(JSONUtils.toString(tap));
    }

    public void testInsuRecordEnableOrDis() {
        Transfer trans = new Transfer();
        // trans.setMpiId("");
        // trans.setMpiId("2c9081814d48badc014d48cf97c80000");
        // trans.setMpiId("2c9081814d689a20014d6b6c4ad80001");
        trans.setMpiId("2c9081834f49cd83014f50a2350c0010");
        trans.setRequestOrgan(1);
        trans.setTargetOrgan(2);
        System.out.println(bdd.insuRecordEnableOrDis(trans));
    }

    */
/**
     * 有号转诊服务（包含预约）
     *
     * @return Boolean
     * @throws DAOException
     * @throws ControllerException
     * @throws 600:前台传入的mpiId为空    602:有未处理的转诊单,不能进行转诊
     * @author luf
     *//*

    public void testRequestTransferClinic() {
        Transfer tran = new Transfer();
        tran.setRequestOrgan(2);
        tran.setRequestDepart(5);
        tran.setRequestDoctor(40);
        tran.setMpiId("2c9081814d48badc014d48cf97c80000");
        tran.setTargetDoctor(1182);
        tran.setTargetOrgan(1);
        tran.setTargetDepart(70);
        tran.setInsuRecord(0);
        tran.setTransferType(1);
        tran.setEmergency(0);
        tran.setDiagianCode("0");
        tran.setDiagianName("上呼吸道感染");
        tran.setPatientCondition("测试");
        tran.setLeaveMess("测试");
        tran.setAnswerTel("15067128799");
        tran.setPatientRequire("1天内(接受特需门诊)");
        tran.setInsuRecord(0);
        List<Otherdoc> otherDocs = new ArrayList<Otherdoc>();
        List<AppointRecord> appointRecords = new ArrayList<AppointRecord>();
        AppointRecord appointRecord = new AppointRecord();
        appointRecord.setStartTime(DateConversion.getCurrentDate(
                "2015-11-13 13:35:00", "yyyy-MM-dd HH:mm:ss"));
        appointRecord.setConfirmClinicAddr("啊啊啊啊");
        appointRecord.setClinicPrice(16);
        appointRecord.setSourceLevel(2);
        appointRecord.setAppointDepartId("51");
        appointRecords.add(appointRecord);
        AppointRecord appointRecor = new AppointRecord();
        appointRecor.setStartTime(DateConversion.getCurrentDate(
                "2015-11-13 13:35:00", "yyyy-MM-dd HH:mm:ss"));
        appointRecor.setConfirmClinicAddr("啊啊啊啊");
        appointRecor.setClinicPrice(16);
        appointRecor.setSourceLevel(2);
        appointRecor.setAppointDepartId("51");
        appointRecords.add(appointRecor);
        try {
            System.out.println(bdd.requestTransferClinic(tran, otherDocs,
                    appointRecords));
        } catch (DAOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (ControllerException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    */
/**
     * 加号转诊服务（修改备案部分）
     *
     * @throws DAOException
     * @throws ControllerException
     * @throws 600:前台传入的mpiId为空    602:有未处理的转诊单,不能进行转诊
     * @author luf
     *//*

    public void testCreateTransferAdd() {
        List<Otherdoc> otherDocs = new ArrayList<Otherdoc>();
        Transfer tran = new Transfer();
        tran.setRequestDepart(3);
        tran.setRequestOrgan(1);
        tran.setRequestDoctor(1003);
        tran.setTargetOrgan(1);
        tran.setTargetDepart(3);
        tran.setTargetDoctor(949);
        tran.setMpiId("2c9081815a284242015a4097b76a0651");
        tran.setTransferType(1);
        tran.setEmergency(0);
        tran.setDiagianCode("0");
        tran.setDiagianName("不孕病");
        tran.setPatientCondition("2.16明接");
        tran.setLeaveMess("");
        tran.setAccompanyFlag(false);
        tran.setConnectCallNumberSystem(false);
        tran.setPatientRequire("3天内");
        tran.setInsuFlag(0);
        System.out.println(JSONUtils.toString(tran));
        try {
            bdd.createTransferAdd(tran, otherDocs);
        } catch (DAOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (ControllerException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    */
/**
     * 我的转诊申请列表
     * <p>
     * mark 标记--0未完成1已完成2未处理3待就诊4已结束（0，1表示全部）
     *
     * @return Hashtable<String, List<TransferAndPatient>>
     * @author luf
     *//*

    public void testQueryRequestTransferList() {
        int doctorId = 1387;
        int mark = 1;
        int start = 0;
        int limit = 10;
        Hashtable<String, List<TransferAndPatient>> andPatients = bdd
                .queryRequestTransferList(doctorId, mark, start, limit);
        System.out.println(JSONUtils.toString(andPatients));
    }

    */
/**
     * 我的转诊列表
     * mark     标记--0未完成1已完成2未处理3待就诊4已结束（0，1表示全部）
     *
     * @return Hashtable<String, List<TransferAndPatient>>
     * @author luf
     *//*

    public void testQueryTransferList() {
        int doctorId = 1387;
        int mark = 3;
        int start = 0;
        int limit = 10;
        Hashtable<String, List<TransferAndPatient>> andPatients = bdd
                .queryTransferList(doctorId, mark, start, limit);
        System.out.println(JSONUtils.toString(andPatients));
    }

    */
/**
     * 转诊取消（处理中也可取消）
     *
     * transfer 转诊信息
     * @throws DAOException
     * @author luf
     *//*

    public void testCancelTransferIn() {
        Transfer transfer = new Transfer();
        transfer.setCancelOrgan(2);
        transfer.setCancelDepart(5);
        transfer.setCancelDoctor(40);
        transfer.setCancelCause("aaaaavvbdvdfedfcs");
        transfer.setTransferId(1153);
        // System.out.println(JSONUtils.toString(transfer));
        bdd.cancelTransferIn(transfer);
    }

    */
/**
     * 转诊申请超过24小时未处理，自动取消
     *
     * @author luf
     *//*

    public void testCancelOverTimeWithPush() {
        bdd.cancelOverTimeWithPush();
    }

    public void testSendSmsForTransferConfirmForPatient() {
        TransferAndPatient tp = bdd.getTransferByID(306);
        AppointRecordDAO dao = DAOFactory.getDAO(AppointRecordDAO.class);
        AppointRecord ar = dao.get(1);
        //bdd.sendSmsForTransferConfirmForPatient(tp, ar);
        try {
            TimeUnit.SECONDS.sleep(60 * 4);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void testCancelInsuRecord() {
        bdd.cancelInsuRecord(1407);
    }

    public void testRetryRegistTransfer() throws InterruptedException {
        bdd.retryRegistTransfer(1407);
        TimeUnit.SECONDS.sleep(100);
    }

    public void testPushMsgForCancelToTargetDoc() {
        AppointRecord ar = DAOFactory.getDAO(AppointRecordDAO.class).get(1297);
        //bdd.pushMsgForCancelToTargetDoc(ar);
    }

    public void testFindTransferAndPatientByStatic() {
        Transfer transfer = new Transfer();
//		transfer.setAccompanyFlag(false);
//		transfer.setEmergency(0);
        QueryResult<TransferAndPatient> qr = bdd.findTransferAndPatientByStatic(
                DateConversion.getCurrentDate("2016-03-17", "yyyy-MM-dd"),
                DateConversion.getCurrentDate("2016-03-17", "yyyy-MM-dd"),
                transfer, 0, null, 3, null, null);
        System.out.println(JSONUtils.toString(qr));
    }

    public void testStartTransferAddExc() {
        System.out.println(bdd.startTransferAddExc(1329, 1292));
    }

    */
/**
     * 转诊取消（添加提示）-原生
     *
     *  transfer 转诊信息
     * @throws DAOException
     * @author luf
     *//*

    public void testCancelTransferInAddExc() {
        Transfer transfer = new Transfer();
        transfer.setCancelOrgan(2);
        transfer.setCancelDepart(5);
        transfer.setCancelDoctor(40);
        transfer.setCancelCause("aaaaavvbdvdfedfcs");
        transfer.setTransferId(1153);
        // System.out.println(JSONUtils.toString(transfer));
        bdd.cancelTransferInAddExc(transfer);
    }

    public void testFindTransferList() {
        System.out.println(JSONUtils.toString(bdd.findTransferList(40, 1, 0, 10)));
    }

    //查找申请医生今日就诊的转诊患者列表 不包括 远程门诊转诊
    //每日早上七点向目标医生推送今日就诊的转诊病人列表信息
    public void testfindTodayTransferByTargetDoctor() {
        Integer docId = 1425;
        Date today = Context.instance().get("date.getToday", Date.class);//今日
        System.out.println(JSONUtils.toString(bdd.findTodayTransferByTargetDoctor(today, 1425, 0, 10)));
        System.out.println(JSONUtils.toString(bdd.findTodayTransferByTargetDoctorAndLimit(1425, 0, 10)));
    }

    //每日下午五点向申请医生推送明日就诊的转诊病人列表信息
    public void testfindTomorrowTransferByRequestDoctor() {
        Integer docId = 1425;
        Date tomorrow = Context.instance().get("date.getToday", Date.class);//明日
        System.out.println(JSONUtils.toString(bdd.findTomorrowTransferByRequestDoctor(tomorrow, docId, 0, 10)));
        System.out.println(JSONUtils.toString(bdd.findTomorrowTransferByRequestDoctorAndLimit(docId, 0, 10)));
    }

    */
/**
     * 获取今日有转诊患者的接收医生列表
     *//*

    public void testfindTodayTransferByConfirmClinicTime() {
        List<Doctor> lists = bdd.findTodayTransferByConfirmClinicTime();
        System.out.println(JSONUtils.toString(lists));
    }

    */
/**
     * 获取明日有转诊患者的申请医生列表
     *//*

    public void testfindTomorrowTransferByConfirmClinicTime() {
        List<Doctor> lists = bdd.findTomorrowTransferByConfirmClinicTime();
        System.out.println(JSONUtils.toString(lists));
    }


    */
/**
     * 向目标医生推送今日就诊的转诊病人信息(推送消息)
     *//*

    public void testTodayPushMessageToTargetDoctorTransferInfo() {

        bdd.todayPushMessageToTargetDoctorTransferInfo();
    }


    */
/**
     * 向申请医生推送明日就诊的转诊病人信息
     *//*

    public void testtomorrowPushMessageToRequestDoctorTransferInfo() {
        bdd.tomorrowPushMessageToRequestDoctorTransferInfo();
    }

    public void testFindAllPendingTransfer() {
        List<Transfer> transferList = bdd.findAllPendingTransfer();
        System.out.println("未处理和处理中的转诊单总数:" + transferList.size());
        System.out.println("未处理和处理中的转诊单:\n" + JSONUtils.toString(transferList));
    }
}
*/
