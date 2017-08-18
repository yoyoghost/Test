/*
package test.dao;

import com.google.common.eventbus.Subscribe;
import ctd.controller.exception.ControllerException;
import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.event.support.AbstractDAOEventLisenter;
import ctd.persistence.event.support.BatchUpdateDAOEvent;
import ctd.persistence.event.support.CreateDAOEvent;
import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import ctd.util.converter.ConversionUtils;
import ctd.util.converter.support.StringToDate;
import eh.base.dao.DoctorDAO;
import eh.base.service.PinService;
import eh.bus.dao.AppointRecordDAO;
import eh.entity.base.Doctor;
import eh.entity.base.DoctorAndEmployment;
import eh.entity.base.Employment;
import junit.framework.TestCase;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class DoctorDAOTester extends TestCase {
    private static ClassPathXmlApplicationContext appContext;

    static {
        appContext = new ClassPathXmlApplicationContext("test/spring.xml");
    }

    private static DoctorDAO dao = appContext.getBean("doctorDAO",
            DoctorDAO.class);

    public void testCreate() throws DAOException {
        dao.addEventListener(new AbstractDAOEventLisenter() {
            @Override
            @Subscribe
            public void onCreate(CreateDAOEvent e) {
                System.out.println(e.getTarget() + "," + e.getTargetId());
            }
        });

        @SuppressWarnings("unused")
        int nmr = ThreadLocalRandom.current().nextInt(10000);
        int n4 = ThreadLocalRandom.current().nextInt(1000, 9999);

        Doctor r = new Doctor();
        r.setName("祁成健");
        r.setIdNumber("420984199201097538");
        r.setCreateDt(new Date());
        r.setEmail("qicj@easygroup.net.cn");
        r.setDoctorCertCode("3326" + n4);
        r.setExpert(true);
        r.setUserType(1);
        r.setGender("1");
        r.setHaveAppoint(1);
        r.setStatus(1);
        r.setMobile("18271631735");
        r.setOrgan(2);
        dao.save(r);
        System.out.println("save done");
    }

    public void test() {
        */
/*
         * int nmr = ThreadLocalRandom.current().nextInt(1000, 9999);
		 * System.out.println(nmr);
		 *//*

        // System.out.println(dao.getPhotoByDoctorId(2163));
        System.out.println(JSONUtils.toString(dao.findDoctorIdAndName(1, 52)));
    }

    public void testFindByDoctorIdAfter() throws DAOException {
        QueryResult<Doctor> qr = dao.findByDoctorIdAfter(14);
        System.out.println(qr.getTotal());
    }

    public void testFindByDoctorIdBetween() throws DAOException {
        QueryResult<Doctor> qr = dao.findByDoctorIdBetween(13, 15);
        System.out.println(qr.getTotal());
        System.out.println(JSONUtils.toString(qr.getItems()));
    }

    public void testFindByCreateDtBetween() throws DAOException {
        QueryResult<Doctor> qr = dao.findByCreateDtBetween(
                ConversionUtils.convert("2015-02-03 16:32:21", Date.class),
                ConversionUtils.convert("2015-02-03 17:31:21", Date.class));
        System.out.println(qr.getTotal());
        System.out.println(JSONUtils.toString(qr.getItems()));
    }

    public void testGetByIdNumber() throws DAOException {
        Doctor r = dao.getByIdNumber("33108119920702674X");
        System.out.println(JSONUtils.toString(r));

    }

    public void testExist() {
        // System.out.println(dao.exist(13));
        System.out.println(dao.findByOrgan(1).size());
    }

    public void testUpdateById() throws ControllerException {
        dao.addEventListener(new AbstractDAOEventLisenter() {
            @Override
            @Subscribe
            public void onBatchUpdate(BatchUpdateDAOEvent e) {
                System.out.println(e);
            }
        });
        dao.updateNameByDoctorId(13, "xxx999");

    }

    public void testGetByMobile() throws DAOException {
        Doctor r = dao.getByMobile("13858043673");
        System.out.println(JSONUtils.toString(r));

        dao.updateNameByMobile("xx301", "13858043673");
    }

    public void testGetByDoctorId() throws DAOException {
        Doctor r = dao.getByDoctorId(1182);
        System.out.println(JSONUtils.toString(r));
    }

    public void testFindByDoctorIdIn() {
        List<Integer> ids = new ArrayList<>();
        ids.add(13);
        ids.add(14);
        ids.add(15);

        List<Doctor> rs = dao.findByDoctorIdIn(ids);
        System.out.println(JSONUtils.toString(rs));
    }

    */
/**
     * 测试名:推荐医生查询服务测试
     *
     * @author yxq
     *//*

    public void testRecommendDoctor() {
        int organID = 1;
        String profession = "01";
        int doctorID = 1;
        String areaCode = "330104";
        String disease = "微创外科";
        String buesType = "1";
        List<Doctor> result = dao.recommendDoctor(organID, profession,
                doctorID, areaCode, disease, buesType);
        // List<Doctor> result = dao.recommendDoctorNew(organID, profession,
        // doctorID, areaCode, disease, buesType);
        if ((result != null) && (result.size() > 0) && !result.isEmpty()) {
            for (int i = 0; i < result.size(); i++) {
                System.out.println(JSONUtils.toString(result.get(i)));
            }
        }
    }

    */
/**
     * 医生信息更新服务测试
     *//*

    public void testUpdateDoctorByDoctorId() {
        Doctor d = new Doctor();
        d.setDoctorId(1178);
        d.setName("");
        d.setGender("");
        d.setUserType(2);
        d.setBirthDay(new Date());
        d.setIdNumber("");
        d.setOrgan(1);
        d.setProfession("");
        d.setMobile("");
        d.setEmail("");
        d.setWeiXin("");
        d.setIntroduce("");
        d.setDomain("这是我的擅长吗");
        d.setHonour("");
        d.setSpecificSign("");
        d.setProTitle("1");
        d.setJobTitle("");
        d.setEducation("");
        // d.setStarWorkDt();
        d.setPhoto(2);
        d.setDoctorCertImage(1);
        d.setDoctorCertCode("");
        d.setProTitleImage(3);
        d.setTeams(true);
        d.setExpert(true);
        System.out.println(dao.updateDoctorByDoctorId(d));
    }

    */
/**
     * 相关医生查询服务测试
     *//*

    public void testSimilarlyDoctor() {
        int organID = 1;
        String profession = "01";
        int doctorID = 1;
        String areaCode = "330104";
        String buesType = "1";
        List<Doctor> docs = dao.similarlyDoctor(organID, profession, doctorID,
                areaCode, buesType);
        System.out.println(JSONUtils.toString(docs));

    }

    */
/**
     * 查询医生信息（按医生专科代码查询）
     *//*

    public void testFindByProfessionLike() {
        String profession = "02";
        List<Doctor> docs = dao.findByProfessionLike(profession);
        System.out.println(docs.size() + JSONUtils.toString(docs));

    }

    */
/**
     * 查询医生信息（按医生专科代码查询）--分页
     *//*

    public void testFindByProfessionLikeWithPage() {
        String profession = "02";
        List<Doctor> docs = dao.findByProfessionLikeWithPage(profession, 2);
        System.out.println(docs.size());
        for (Doctor d : docs) {
            System.out.println(JSONUtils.toString(d));
        }

    }

    */
/**
     * 更新医生是否有预约号源标志
     *//*

    public void testUpdateHaveAppointByDoctorId() {
        int haveAppoint = 1;
        int doctorId = 1177;
        dao.updateHaveAppointByDoctorId(doctorId, haveAppoint);
    }

    public void testGetAllDoctorNum() {
        Long num = dao.getAllDoctorNum();
        System.out.println(num);
    }

    public void testFindDoctor() {
        List<Doctor> list = dao.findDoctor();
        System.out.println(list.size());
        */
/*
		 * for(Doctor d:list){ System.out.println(JSONUtils.toString(d)); }
		 *//*


    }

    */
/**
     * 按姓名模糊查询医生信息服务
     *//*

    public void testGetByNameLike() {
        String name = "黄";
        List<Doctor> list = dao.findByNameLike(name, 4);
        System.out.println(list.size());
        for (Doctor d : list) {
            System.out.println(JSONUtils.toString(d));
        }
    }

    */
/**
     * 按是否在线查询医生信息服务
     *//*

    public void testFindByOnline() {
        int online = 1;
        List<Doctor> list = dao.findByOnlineWithPage(online, 2);
        System.out.println(list.size());
        for (Doctor d : list) {
            System.out.println(JSONUtils.toString(d));
        }
    }

    */
/**
     * 按是否有号查询医生信息服务
     *//*

    public void testFindByHaveAppoint() {
        int haveAppoint = 1;
        List<Doctor> list = dao.findByHaveAppointWithPage(haveAppoint, 0);
        System.out.println(list.size());
        for (Doctor d : list) {
            System.out.println(JSONUtils.toString(d));
        }
    }

    */
/**
     * 按区域查询医生信息服务测试
     *//*

    public void testFindByAddrAreaLike() {
        String addrArea = "330104";
        List<Doctor> list = dao.findByAddrAreaLike(addrArea, 840);
        for (Doctor d : list) {
            System.out.println(JSONUtils.toString(d));
        }
    }

    */
/**
     * 搜索医生优化（专科、区域、擅长疾病、姓名、是否在线、是否有号）服务测试
     *//*

    public void testFindDoctorByCondition() {
        String profession = "";
        String addrArea = "";
        String domain = "";
        String name = "张肖";
        int onLineStatus = 1;
        int haveAppoint = 0;

        List<Doctor> list = dao.searchDoctor(profession, addrArea, domain,
                name, onLineStatus, haveAppoint, 0);
        System.out.println(list.size());
        System.out.println(JSONUtils.toString(list));
    }

    public void testSearchDoctorBuss() {
        String profession = "";
        String addrArea = "";
        String domain = "";
        String name = "";
        int onLineStatus = 1;
        int haveAppoint = 0;

        List<Doctor> list = dao.searchDoctorBuss(profession, addrArea, domain,
                name, onLineStatus, haveAppoint, 0, 4);
        System.out.println(list.size());
        System.out.println(JSONUtils.toString(list));
    }

    public void testSearchDoctorBussNew() {
		*/
/*
		 * String profession = ""; String addrArea = ""; String domain = "";
		 * String name = ""; int onLineStatus = 1; int haveAppoint = 0; String
		 * proTitle = "1";
		 *//*

        // String proTitle = "";

        List<Doctor> list = dao.searchDoctorBussNew("", "33", "", "阳", 1, 0, 0,
                1, "");
        System.out.println(list.size());
        System.out.println(JSONUtils.toString(list));
    }

    */
/**
     * 获取 指定时间内医生数量
     *
     * @author ZX
     * @date 2015-5-21 下午3:54:22
     *//*

    public void testGetDocNumFromTo() {
        Date startTime = new StringToDate().convert("2015-01-01");
        Date endTime = new StringToDate().convert("2016-01-01");
        String manageUnit = "eh001";
        long num = dao.getDocNumFromTo(manageUnit, startTime, endTime);
        System.out.println(num);
    }

    */
/**
     * 获取当月新增的医生数
     *
     * @author ZX
     * @date 2015-5-21 下午4:25:01
     *//*

    public void testGetDocNumByMonth() {
        String manageUnit = "eh001";
        long num = dao.getDocNumByMonth(manageUnit);
        System.out.println(num);
    }

    */
/**
     * 获取昨天新增的医生数
     *
     * @author ZX
     * @date 2015-5-21 下午4:25:01
     *//*

    public void testGetDocNumByYestoday() {
        String manageUnit = "eh001";
        long num = dao.getDocNumByYesterday(manageUnit);
        System.out.println(num);
    }

    */
/**
     * 获取活跃用户数
     *
     * @author ZX
     * @date 2015-5-21 下午4:25:01
     *//*

    public void testGetActiveDocNum() {
        String manageUnit = "eh";
        long num = dao.getActiveDocNum(manageUnit);
        System.out.println(num);
    }

    public void testGetAllDoctorNumWithManager() {
        String manageUnit = "eh";
        Long num = dao.getAllDoctorNumWithManager(manageUnit);
        System.out.println(num);
    }

    */
/**
     * 医生信息添加服务
     *
     * @author hyj
     *//*

    public void testAddDoctor() {
        Doctor r = new Doctor();
        r.setName("朱敏明");
        r.setIdNumber("330402199301191210");
        r.setCreateDt(new Date());
        r.setBirthDay(new Date());
        r.setEmail("wangx@sina.com");
        r.setDoctorCertCode("3326");
        r.setProfession("02");
        r.setExpert(true);
        r.setUserType(1);
        r.setGender("1");
        r.setStatus(1);
        r.setMobile("18268209706");
        r.setOrgan(1);
        Doctor rr = dao.addDoctor(r);
        System.out.println(JSONUtils.toString(rr));
    }

    */
/**
     * 医生开户服务
     *
     * @author hyj
     *//*

    public void testCreateDoctorUser() {
        int doctorId = 3934;
        String password = "888888";
        dao.createDoctorUser(doctorId, password);

        try {
            TimeUnit.SECONDS.sleep(60 * 4);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    */
/**
     * 获取医师开户服务
     *
     * @author hyj
     *//*

    public void testCheckDoctorUser() {
        int doctorId = 1178;
        boolean flag = dao.checkDoctorUser(doctorId);
        System.out.println(flag);
    }

    */
/**
     * 生成拼音码服务
     *
     * @author hyj
     *//*

    public void testConvertPin() {
        String words = "黄伊瑾";
        PinService pin = appContext.getBean("pinService", PinService.class);
        System.out.println(pin.convertPin(words));
    }

    */
/**
     * 根据关键字检索医生服务测试（姓名、身份证号、机构代码）
     *
     * @author zsq
     *//*

    public void testSearchDoctorByName() {
        String name = "";
        String idNumber = "";
        Integer organ = null;
        String profession = "02";
        int deptId = 70;

        List<Doctor> list = dao.queryDoctorAndEmployment(name, idNumber, organ,
                profession, deptId, 0);
        System.out.println(list.size());
        System.out.println(JSONUtils.toString(list));
    }

    */
/**
     * 根据医生id和teams查询医生信息测试
     *
     * @author hyj
     *//*

    public void testGetByDoctorIdAndTeams() {
        int doctorId = 1561;
        Doctor d = dao.getByDoctorIdAndTeams(doctorId);
        System.out.println(JSONUtils.toString(d));
    }

    */
/**
     * 根据区域，开始年龄，结束年龄，性别，专科查询医生列表并按点赞数高低返回固定条记录测试
     *
     * @author Qichengjian
     *//*

    public void testQueryByAddrAreaAndProfessionAndAge() {
        List<Doctor> list = dao.queryByAddrAreaAndProfessionAndAge("330104",
                "04", 3);
        for (int i = 0; i < list.size(); i++) {
            System.out.println(JSONUtils.toString(list.get(i)));
        }
    }

    */
/**
     * 智能推荐医生列表服务测试
     *
     * @author Qichengjian
     *//*

    public void testIntelligentreDoctors() {
        List<Doctor> list = dao.intelligentreDoctorsNew("330104", 55, "1", 2);
        System.out.println(list.size());
        for (int i = 0; i < list.size(); i++) {
            System.out.println(JSONUtils.toString(list.get(i)));
        }
    }

    */
/**
     * 未登录智能推荐医生列表服务
     *
     * @author Qichengjian
     *//*

    public void testIntelligentreDoctorsForUnLogin() {
        List<Doctor> list = dao.intelligentreDoctorsForUnLogin("330104");
        System.out.println(list.size());
        for (int i = 0; i < list.size(); i++) {
            System.out.println(JSONUtils.toString(list.get(i)));
        }
    }

    */
/**
     * 团队医生信息更新服务测试
     *
     * @author hyj
     *//*

    public void testUpdateGroupDoctor() {
        Doctor d = new Doctor();
        d.setDoctorId(2146);
        d.setName("");
        d.setGender("");
        d.setUserType(2);
        d.setBirthDay(new Date());
        d.setIdNumber("");
        d.setOrgan(1);
        d.setProfession("");
        d.setMobile("");
        d.setEmail("");
        d.setWeiXin("");
        d.setIntroduce("");
        d.setDomain("这是我的擅长吗");
        d.setHonour("");
        d.setSpecificSign("");
        d.setProTitle("1");
        d.setJobTitle("");
        d.setEducation("");
        // d.setStarWorkDt();
        d.setPhoto(2);
        d.setDoctorCertImage(1);
        d.setDoctorCertCode("");
        d.setProTitleImage(3);
        d.setTeams(true);
        d.setExpert(true);
        dao.updateGroupDoctor(d);
    }

    */
/**
     * 医生受关注度
     *
     * @param doctorId
     * @return
     * @author LF
     *//*

    public void testDoctorRelationNumber() {
        System.out.println(dao.doctorRelationNumber(40));
    }

    */
/**
     * 获取学历字典
     *
     * @author ZX
     * @date 2015-8-11 下午3:19:02
     *//*

    public void testGetEducation() {
        System.out.println(JSONUtils.toString(dao.getEducation()));
    }

    public void testfuzzySearchDoctor2() {
        List<Doctor> res = dao.fuzzySearchDoctorWithServiceType("02",
                "这是我的擅长吗", "黄伊瑾", 0, 2);
        System.out.println(JSONUtils.toString(res));
    }

    public void testFuzzySearchDoctor() {
        List<Doctor> res = dao.fuzzySearchDoctor("0401", "肝胆胰外科、腹部外科和微创外科",
                "蔡秀军团队", 0);
        System.out.println(res.size());
        System.out.println(JSONUtils.toString(res));
    }

    public void testGetBusyFlagByDoctorId() {
        System.out.println(JSONUtils.toString(dao.getBusyFlagByDoctorId(2151)));
    }

    public void testfindEffectiveDocByDoctorIdIn() {
        List<Integer> list = new ArrayList<Integer>();
        list.add(1182);
        list.add(1180);
        System.out.println(JSONUtils.toString(dao
                .findEffectiveDocByDoctorIdIn(list)));
    }

    public void testArrayListContents() {
        Doctor doctor = new Doctor();
        doctor.setDoctorId(1);
        doctor.setName("lulu");
        List<Doctor> doctors = new ArrayList<Doctor>();
        doctors.add(doctor);
        // doctors.add(doctor);
        List<DoctorAndEmployment> list = new ArrayList<DoctorAndEmployment>();
        Employment employment = new Employment();
        employment.setEmploymentId(1);
        employment.setDepartment(1);
        DoctorAndEmployment e = new DoctorAndEmployment(doctor, employment);
        list.add(e);

        Doctor doctor2 = new Doctor();
        doctor2.setDoctorId(2);
        doctor2.setName("pipi");
        System.out.println(doctors.size());
        // System.out.println(JSONUtils.toString(list));
        System.out.println(doctors.contains(doctor));
        System.out.println(doctors.contains(list.get(0).getDoctor()));
        System.out.println(doctors.contains(doctor2));

        List<Integer> i = new ArrayList<Integer>();
        i.add(1);
        i.add(2);
        System.out.println(JSONUtils.toString(i));
        System.out.println(i.contains(3));
        System.out.println(i.contains(1));
    }

    public void testGetProTitle() {
        System.out.println(JSONUtils.toString(dao.getProTitle()));
    }

    public void testFindByNameOrMobOrIdN() throws InterruptedException {
		*/
/*
		 * String name = "卢"; String mobile = "2533"; String idNumber = "330";
		 * DoctorDAO dao = appContext.getBean("doctorDAO",DoctorDAO.class);
		 * List<Doctor> docs = dao.findByNameOrMobOrIdN(name, mobile, idNumber);
		 * System.out.println(docs.size());
		 * System.out.println(JSONUtils.toString(docs));
		 *//*

        System.out.println("KKKKKK");
        if (!dao.exist("3780")) {
            System.out.println(">>TRUE");
        } else {
            System.out.println(">>FALSE");
        }
        TimeUnit.SECONDS.sleep(5);

    }

    public void testSendVCode() throws InterruptedException {
        dao.sendVCode("18767167524");
    }

    public void testGetLoginUserInfo() {
        System.out.println(JSONUtils.toString(dao.getLoginUserInfo(1198)));
    }

    */
/**
     * 查询所有审核中的医生
     *
     * @param start 分页起始位置
     * @param limit 每页限制条数
     * @return List<Doctor>
     * @author luf
     *//*

    public void testFindDoctorsByStatusTwo() {
        int start = 0;
        int limit = 10;
        System.out.println(JSONUtils.toString(dao.findDoctorsByStatusTwo(start,
                limit)));
    }

    */
/**
     * 审核医生
     *
     * @param doctorId 医生内码
     * @param status   医生状态
     * @author luf
     *//*

    */
/*//*
/public void testAuditDoctorWithMsg() {
        dao.auditDoctorWithMsg(40, 0);
    }
*//*

    public void testAddGroupDoctor() {
        Doctor d = new Doctor();
        d.setChief(0);
        d.setExpert(false);
        d.setName("开发人员测试");
        d.setOrgan(1);
        d.setProfession("00");
        d.setStatus(0);
        d.setTeams(true);
        // d.setUserType("1");
        d.setUserType(1);
        d.setVirtualDoctor(false);
        dao.addGroupDoctor(d);
    }

    */
/**
     * 医生的被点赞数和粉丝数
     *
     * @author zhangx
     * @date 2015-12-10 上午11:26:53
     *//*

    public void testGetNumByDoctor() {
        System.out.println(JSONUtils.toString(dao.getNumByDoctor(1182)));
    }

    */
/**
     * 健康端按条件查找医生
     * <p>
     * eh.base.dao
     *
     * @param profession  专科编码
     * @param addrArea    属地区域
     * @param domain      擅长领域
     * @param name        医生姓名
     * @param haveAppoint 预约号源标志
     * @param proTitle    职称
     * @param flag        标志-0咨询1预约
     * @param start       起始页
     * @param limit       每页限制条数
     * @return List<HashMap<String,Object>>
     * @author luf 2016-2-26 增加筛选条件-按入口分别查询
     *//*

    public void testsearchDoctorForHealth() {
        String profession = "";
        String addrArea = "";
        String domain = "";
        String name = "";
        Integer haveAppoint = null;
        String proTitle = "1";
        int start = 0;
        int limit = 20;
        List<HashMap<String, Object>> targets = dao.searchDoctorForHealth(
                profession, addrArea, domain, name, haveAppoint, proTitle, 0,
                start, limit);
        System.out.println(JSONUtils.toString(targets));
        System.out.println(targets.size());
    }

	*/
/*
	 * public void testFindDoctorByTwoLike() { String addrArea = "330104";
	 * String profession = "0301"; List<Doctor> ds =
	 * dao.findDoctorByTwoLike(addrArea, profession, 2);
	 * System.out.println(JSONUtils.toString(ds)); }
	 *//*


    public void testFindDoctorByTwoLikeNew() {
        String addrArea = "33";
        String profession = "03";
        List<Doctor> ds = dao.findDoctorByTwoLikeNew(addrArea, profession, 20);
        System.out.println(JSONUtils.toString(ds));
    }

    */
/**
     * 未登录状态下推荐医生
     *
     * @param homeArea 属地区域
     * @return List<Doctor>
     * @author luf
     *//*

    public void testDoctorsRecommendedUnLogin() {
        String homeArea = "330608";
        Map<String, List<Doctor>> ds;
        try {
            ds = dao.doctorsRecommendedUnLogin(homeArea);
            System.out.println(JSONUtils.toString(ds));
        } catch (ControllerException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    */
/**
     * 未登录状态下推荐医生-找医生
     * <p>
     * eh.base.dao
     *
     * @param homeArea 属地区域
     * @param flag     标志-0咨询1预约
     * @return List<HashMap<String,Object>>
     * @author luf 2016-2-29
     *//*

    public void testDoctorsRecommendedUnLogin2() {
        String homeArea = "330104";
        int flag = 0;
        System.out.println(JSONUtils.toString(dao.doctorsRecommendedUnLogin2(
                homeArea, flag)));
    }

    */
/**
     * 登陆后的推荐医生
     *
     * @param homeArea      属地区域
     * @param age           患者姓名
     * @param patientGender 患者性别
     * @param 02全科医学        12口腔科 10眼科 0502产科 03内科 04外科 07儿科 19肿瘤科 0501妇科
     * @return List<Doctor>
     * @author luf
     *//*

    public void testDoctorsRecommended() {
        String homeArea = "330100";
        int age = 0;
        String patientGender = "";
        Map<String, Object> ds;
        try {
            ds = dao.doctorsRecommendedNew(homeArea, age, patientGender);
            System.out.println(JSONUtils.toString(ds));
            // System.out.println(ds.size());
        } catch (ControllerException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        // Map<String, List<Integer>> map = new HashMap<String,
        // List<Integer>>();
        // List<Integer> ds = new ArrayList<Integer>();
        // ds.add(1);
        // map.put("内科", ds);
        // if(map.containsKey("pu内科")) {
        // map.get("外科").add(2);
        // }
        // System.out.println(JSONUtils.toString(map));
    }

    */
/**
     * 预约推荐医生
     *
     * @param doctorId 医生内码
     * @return List<Doctor>
     * @author luf
     *//*

    public void testAppointDoctorsRecommended() {
        int doctorId = 40;
        List<HashMap<String, Object>> ds = dao
                .appointDoctorsRecommended(doctorId);
        System.out.println(JSONUtils.toString(ds));
        System.out.println(ds.size());
        // Doctor d = dao.get(40);
        // Doctor d2 = dao.get(1177);
        // List<Doctor> ds1 = new ArrayList<Doctor>();
        // List<Doctor> ds2 = new ArrayList<Doctor>();
        // ds1.add(d);
        // ds2.add(d);
        // ds2.add(d2);
        // for(Doctor t:ds1) {
        // if(!ds2.contains(t)) {
        // System.out.println("yes");
        // }
        // }
    }

    */
/**
     * 咨询推荐医生
     *
     * @param doctorId 医生内码
     * @return List<Doctor>
     * @author luf
     *//*

    public void testConsultDoctorsRecommended() {
        int doctorId = 40;
        List<HashMap<String, Object>> ds = dao
                .consultDoctorsRecommended(doctorId);
        System.out.println(JSONUtils.toString(ds));
        System.out.println(ds.size());
    }

    */
/**
     * 患者端查看医生信息
     *
     * @author zhangx
     * @date 2015-12-22 下午5:27:43
     *//*

    public void testGetDoctorInfoForHealth() {
        int doctorId = 1182;
        String mpi = "2c9081824cc3552a014cc3a9a0120002";
        System.out.println(JSONUtils.toString(dao.getDoctorInfoForHealth(
                doctorId, mpi)));
    }

    */
/**
     * 在线云门诊医生列表
     *
     * @author zhangx
     * @date 2015-12-29 下午9:09:02
     *//*

    public void testFindOnlineCloudClinicDoctorsLimit() {
        List<HashMap<String, Object>> list = dao
                .findOnlineCloudClinicDoctorsLimit(0, 10);
        System.out.println(JSONUtils.toString(list));
    }

    public void testFindEffDoctorAndUrt() {
        List<Integer> ids = new ArrayList<Integer>();
        ids.add(5);
        ids.add(40);
        ids.add(1918);
        ids.add(1919);
        System.out.println(JSONUtils.toString(dao.findEffDoctorAndUrt(ids)));
    }

    public void testDoctorsRecMore() {
        String homeArea = "330104";
        String profession = "07";
        System.out.println(JSONUtils.toString(dao.doctorsRecMore(homeArea,
                profession)));
    }

    public void testConsultOrAppointDoctorsRecommended() {
        int doctorId = 40;
        Doctor oldD = dao.getByDoctorId(doctorId);
        oldD.setName("卢芳Test");
        dao.updateDoctorByDoctorId(oldD);
        int flag = 1;
        System.out.println(JSONUtils.toString(dao.consultOrAppointDoctorsRecommended(doctorId, flag)));
    }

    */
/**
     * 供getDoctorListWithWhile2调用
     * <p>
     * eh.base.dao
     *
     * @param addrArea
     * @param profession
     * @param flag       标志-0咨询1预约
     * @param max
     * @return List<Doctor>
     * @author luf 2016-2-25
     *//*

    public void testFindBussDoctorByTwoLike() {
        String addrArea = "330104";
        String profession = "03";
        int flag = 0;
        int max = 2;
        System.out.println(JSONUtils.toString(dao.findBussDoctorByTwoLike(
                addrArea, profession, flag, max)));
    }

    public void testConsultOrAppointRecommended() {
        String homeArea = "330104";
        int age = 30;
        int flag = 0;
        String patientGender = "2";
        System.out.println(JSONUtils.toString(dao.consultOrAppointRecommended(
                homeArea, age, patientGender, flag)));
    }

    public void testFindByOrganAndHaveAppoint() {
        int organId = 1;
        Integer haveAppoint = 1;
        System.out.println(JSONUtils.toString(dao.findByOrganAndHaveAppoint(organId, haveAppoint)));
    }

    public void testUpdateTheDoctor() {
        Doctor oldD = dao.getByDoctorId(2236);
        oldD.setName("侯秀荣团队");
        dao.updateDoctorByDoctorId(oldD);
        System.out.println(JSONUtils.toString(dao.getByDoctorId(2236)));
    }

    public void testUpdateDoctorTextDic() {
        int doctorId = 40;
        Doctor oldD = dao.getByDoctorId(doctorId);
        oldD.setName("卢芳团队2");
        dao.updateDoctorByDoctorId(oldD);
        AppointRecordDAO arDAO = DAOFactory.getDAO(AppointRecordDAO.class);
        System.out.println(JSONUtils.toString(arDAO.getById(309)));
    }

    public void testfindRemdDoctorListForAppointConsult() {
        int doctorId = 1182;
        String addrArea = "330104";
        String subAddrArea = "330104";
        String profession = "02";

        System.out.println(JSONUtils.toString(dao.findRemdDoctorListForAppointConsult(doctorId,
                addrArea, subAddrArea, profession)));
    }

    public void testSearchDoctorInConsult() {
        String search = "";
        String addrArea = "";
        Integer organId = null;
        String profession = "";
        String proTitle = "";
        int start = 14;
        int limit = 10;
        int flag = 0;
        int mark = 1;
        System.out.println(JSONUtils.toString(dao.searchDoctorInConsult(search, addrArea, organId, profession, proTitle, null, start, limit, flag,mark)));
//        System.out.println(JSONUtils.toString(dao.searchDoctorCanRecipe(search, addrArea, organId, profession, proTitle, null, start, limit, flag,mark)));
    }

    public void testFindMobilesByDoctorIds() {
        List<Integer> docIds = new ArrayList<Integer>();
        docIds.add(40);
        docIds.add(1182);
        System.out.println(dao.findMobilesByDoctorIds(docIds));
    }

}*/
