package eh.base.service;

import com.google.common.collect.Maps;
import ctd.account.UserRoleToken;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.impl.user.UserRoleTokenDAO;
import ctd.util.BeanUtils;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import ctd.util.context.Context;
import ctd.util.context.ContextUtils;
import eh.base.constant.BussTypeConstant;
import eh.base.dao.*;
import eh.bus.dao.ConsultDAO;
import eh.bus.dao.ConsultSetDAO;
import eh.bus.service.ConsultSetService;
import eh.entity.base.Doctor;
import eh.entity.base.Employment;
import eh.entity.base.Organ;
import eh.entity.bus.ConsultSet;
import eh.utils.params.ParamUtils;
import eh.utils.params.ParameterConstant;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DoctorService {

    @RpcService
    public Doctor getByUIDOld(int uid) {
        Doctor dr = null;
        UserRoleTokenDAO tokenDao = DAOFactory.getDAO(UserRoleTokenDAO.class);
        UserRoleToken urt = tokenDao.get(uid);
        String userId = urt.getUserId();
        DoctorDAO dao = DAOFactory.getDAO(DoctorDAO.class);
        dr = dao.getByMobile(userId);

        return dr;
    }

    @RpcService
    public Doctor getByUID(int doctorId) {
        Doctor doctor = null;
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        if (doctorDAO.getByDoctorId(doctorId) == null) {
            return null;
        }
        doctor = doctorDAO.getByDoctorId(doctorId);
        return doctor;
    }

    /**
     * 搜索医生优化-原生端-添加医生团队
     *
     * @param profession   专科编码
     * @param addrArea     属地区域
     * @param domain       擅长领域
     * @param name         医生姓名
     * @param onLineStatus 在线状态̬
     * @param haveAppoint  预约号源标志
     * @param startPage    起始页
     * @param busId        业务类型--1、转诊 2、会诊 3、咨询 4、预约
     * @param proTitle     职称
     * @param flag         查询标志—0普通医生、1团队医生（当前列表最后一个是普通医生，则传0，否则传1）
     * @return List<Doctor>
     * @author luf
     */
    @RpcService
    public HashMap<String, Object> searchDoctorWithTeam(String profession, String addrArea, String domain,
                                                        String name, Integer onLineStatus, Integer haveAppoint,
                                                        int startPage, int busId, String proTitle, int flag) {
        UserRoleToken ur = (UserRoleToken) ContextUtils
                .get(Context.USER_ROLE_TOKEN);
        Employment eSelf = (Employment) ur.getProperty("employment");
        HashMap<String, Object> result = new HashMap<String, Object>();
        List<Doctor> docList = new ArrayList<Doctor>();

        if (eSelf == null || eSelf.getOrganId() == null) {
            return result;
        }
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        String bussType = "0";
        switch (busId) {
            case 1:
            case 4:
                bussType = "1";
                break;
            case 2:
                bussType = "2";
                break;
            case 3:
            default:
                return null;
        }
        List<Organ> oList = organDAO.queryRelaOrganNew(eSelf.getOrganId(), bussType, addrArea);

        StringBuilder sb = new StringBuilder(" and(");
        for (Organ o : oList) {
            sb.append(" e.organId=").append(o.getOrganId()).append(" OR");
        }
        final String strUO = sb.substring(0, sb.length() - 2) + ")";

        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        DoctorGroupDAO groupDAO = DAOFactory.getDAO(DoctorGroupDAO.class);
        List<Integer> ids = new ArrayList<Integer>();
        int mark = flag;//0普通医生，1团队医生
        int teamCount = 0;
        if (flag == 0) {
            ids = doctorDAO.searchDoctorBussNameAllSql(
                    profession, addrArea, domain, name, onLineStatus, haveAppoint, startPage, 10, busId, proTitle, strUO);
        }
        List<Integer> groups = new ArrayList<Integer>();
        if (busId <= 2 && ((flag == 0 && ids != null && ids.size() > 0 && ids.size() < 10) ||
                (flag == 1 && (ids == null || ids.isEmpty() || ids.size() < 10)))) {
            if (ids==null) {
                ids = new ArrayList<Integer>();
            }
            int limit = 10 - ids.size();
            int start = 0;
            if (flag != 0) {
                start = startPage;
            }
            List<Integer> ps = doctorDAO.searchDoctorBussNameAllSql(
                    profession, addrArea, domain, name, onLineStatus, haveAppoint, 0, 0, busId, proTitle, strUO);
            groups.addAll(groupDAO.findDocIdByMembers(ps, profession, name, busId, strUO, start, limit));
            if (groups != null && !groups.isEmpty()) {
                ids.addAll(groups);
                mark = 1;
                teamCount = groups.size();
            }
        }

        EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
        ConsultSetDAO csDao = DAOFactory.getDAO(ConsultSetDAO.class);
        DoctorTabDAO doctorTabDAO=DAOFactory.getDAO(DoctorTabDAO.class);
        if (ids != null) {
            for (Integer id : ids) {
                Doctor d = doctorDAO.get(id);
                Employment employment = employmentDAO
                        .getPrimaryEmpByDoctorId(id);
                if (employment != null) {
                    d.setDepartment(employment.getDepartment());
                }
                if(busId==BussTypeConstant.MEETCLINIC){//zhangsl 2017-05-26 15:25:51会诊中心标记新增
                    d.setMeetCenter(doctorTabDAO.getMeetTypeByDoctorId(id));
                }
                ConsultSet cs = csDao.get(id);
                Integer isOpen = 0;
                if (cs != null) {
                    switch (busId) {
                        case BussTypeConstant.TRANSFER:
                            isOpen = cs.getTransferStatus();
                            break;
                        case BussTypeConstant.MEETCLINIC:
                            isOpen = cs.getMeetClinicStatus();
                            break;
                        case BussTypeConstant.CONSULT:
                        case BussTypeConstant.APPOINTMENT:
                            if ((cs.getOnLineStatus() != null && cs.getOnLineStatus() == 1) || (cs.getAppointStatus() != null && cs.getAppointStatus() == 1)) {
                                isOpen = 1;
                            }
                            break;
                        default:
                    }
                }
                d.setIsOpen(isOpen);
                docList.add(d);
            }
        }
        result.put("docList", docList);
        result.put("mark", mark);
        result.put("teamCount", teamCount);

        return result;
    }

    /**
     * 根据医生二维码链接返回医生信息
     *
     * @param qrUrl
     * @return
     */
    @RpcService
    public Doctor getDoctorByQrUrl(String qrUrl) {
        DoctorDAO docDao = DAOFactory.getDAO(DoctorDAO.class);

        if (StringUtils.isEmpty(qrUrl)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "url is needed");
        }
        Doctor d = docDao.getByQrUrl(qrUrl);
        if (d == null) {
            throw new DAOException(609, "此医生不存在");
        }

        Doctor doc = new Doctor();
        doc.setTeams(d.getTeams() == null ? false : d.getTeams());
        doc.setDoctorId(d.getDoctorId());
        return doc;
    }

    /**
     * 登陆后的推荐医生-wx2.7（首页轮播）
     *
     * @param homeArea      属地区域
     * @param age           患者年龄
     * @param patientGender 患者性别 --1男2女
     * @author zhangsl 2016-12-19 16:51:12
     */
    @RpcService
    public Map<String, Object> doctorsRecommendedForScroll(String homeArea, Integer age, String patientGender)
            throws ControllerException {
        if (homeArea == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "homeArea is required");
        }
        if (homeArea.length() < 6) {
            StringBuilder tmpArea = new StringBuilder(homeArea);
            for (int l = 0; l < 6 - homeArea.length(); l++) {
                tmpArea = tmpArea.append("0");
            }
            homeArea = tmpArea.toString();
        }
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        List<Doctor> list = new ArrayList<Doctor>();
        UserRoleToken urt = UserRoleToken.getCurrent();
        List<String> professions = new ArrayList<String>();
        if (urt == null) {
            //未登录
            for (int i = 2; i < 5; i++) {
                professions.add("0" + i);
            }
        } else {
            //登录
            if (ObjectUtils.isEmpty(age)) {
                throw new DAOException(DAOException.VALUE_NEEDED,
                        "age is required");
            }
            if (StringUtils.isEmpty(patientGender)) {
                throw new DAOException(DAOException.VALUE_NEEDED,
                        "patientGender is required");
            }
            professions = doctorDAO.doctorsRecommended(age, patientGender);
        }
        String addrArea = "0000000";
        for (String profession : professions) {
            HashMap<String, Object> os = doctorDAO.getDoctorListWithWhile(homeArea, profession, 1);
            if (os == null || os.get("list") == null) {
                continue;
            }
            list.addAll((List<Doctor>) os.get("list"));
            String addr = (String) os.get("home");
            if (addr.length() < addrArea.length()) {
                addrArea = addr;
            }
        }
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("doctors", list);
        result.put("addrArea", addrArea);
        result.put("addrAreaText", DictionaryController.instance()
                .get("eh.base.dictionary.AddrArea")
                .getText(addrArea));
        return result;
    }


    /**
     * 获取医生有多少执业点信息
     *
     * @param doctorId
     * @return
     */
    @RpcService
    public long getCountEmploymentByDoctorId(Integer doctorId) {
        if (doctorId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "doctorId is require");
        }
        EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
        return employmentDAO.getCountByDoctorId(doctorId);
    }

    /**
     * 判断医生是否已添加签名
     *
     * @param doctorId 医生内码
     * @return Boolean
     */
    @RpcService
    public Map<String, Object> isDoctorSign(int doctorId) {
        Map<String, Object> resultInfo = new HashMap<>();
        boolean flag = true;
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Integer signImage = doctorDAO.getSignImageByDoctorId(doctorId);
        if (signImage == null || signImage.intValue() <= 0) {
            flag = false;
        }
        resultInfo.put("signImage", signImage);
        resultInfo.put("flag", flag);
        return resultInfo;
    }

    /**
     * 药师是否能审方判断服务
     * zhongzx
     * @param doctorId
     * @return
     */
    @RpcService
    public Map<String, Object> auditPrescriptionOrNot(Integer doctorId){
        AuditPrescriptionOrganDAO auditDao = DAOFactory.getDAO(AuditPrescriptionOrganDAO.class);
        Map<String, Object> resMap = new HashMap<>();
        List<Integer> organList = auditDao.findOrganIdsByDoctorId(doctorId);
        if(null != organList && organList.size() > 0){
            resMap.put("result", true);
        }else{
            resMap.put("result", false);
            resMap.put("tip", ParamUtils.getParam(ParameterConstant.KEY_CANNOT_AUDIT_RECIPE));
        }
        return resMap;
    }

    /**
     * 自动更新doctor表中consultAmount的数量
     */
    @RpcService
    public void autoCalculateDoctorConsultAmount() {
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        ConsultSetDAO consultSetDAO = DAOFactory.getDAO(ConsultSetDAO.class);
        List<Integer> doctorList = consultSetDAO.queryOpenConsultionDoctor();
        ConsultDAO consultDao = DAOFactory.getDAO(ConsultDAO.class);
        long consultAmount = 0;
        for (Integer doctorId : doctorList) {
            consultAmount = consultDao.getConsultAmountByDoctorId(doctorId);
            doctorDAO.updateConsultAmountByDoctorId(consultAmount, doctorId);
        }
    }

    /**
     * 获取咨询首页的专家团队列表,如果传入的地区找不到医生团队，则按该区域的上一层进行搜素，如果没有则不显示
     *
     * @param homeArea    地址码
     * @param serviceType 业务类型
     * @param start       起始页
     * @param limit       限制条数
     * @return
     * @author cuill
     * @date 2017/7/7
     */
    @RpcService
    public List<HashMap<String, Object>> consultOrAppointExpertTeam(String homeArea, Integer serviceType, int start, int limit) {
        DoctorDAO doctorDao = DAOFactory.getDAO(DoctorDAO.class);
        ConsultSetService consultSetService = AppContextHolder.getBean("eh.consultSetService", ConsultSetService.class);
        if (homeArea == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "homeArea is required");
        }
        int j = 0;
        List<Doctor> doctorList = doctorDao.findDoctorTeamByHomeArea(homeArea, start, limit);
        if (ObjectUtils.isEmpty(doctorList)) {
            int length = homeArea.length();
            homeArea = homeArea.substring(0, (length - 2));
            doctorList = doctorDao.findDoctorTeamByHomeArea(homeArea, start, limit);
        }
        List<HashMap<String, Object>> targets = new ArrayList<>();
        for (Doctor doctor : doctorList) {
            HashMap<String, Object> result = new HashMap<>();
            int doctorId = doctor.getDoctorId();
            ConsultSetDAO dao = DAOFactory.getDAO(ConsultSetDAO.class);
            ConsultSet consultSet = dao.get(doctorId);
            result.put("doctor", this.wrapDoctorForConsultTeam(doctor));
            result.put("consultSet", consultSetService.wrapConsultSetForOnLineStatus(consultSet));
            targets.add(result);
        }
        return targets;
    }

    /**
     * 包装咨询首页医生团队列表所需要的字段
     * @param doctor  数据库读取的doctor信息
     * @return
     */
    public Doctor wrapDoctorForConsultTeam(Doctor doctor) {
        Doctor returnDoctor = new Doctor();
        returnDoctor.setDoctorId(doctor.getDoctorId());
        if (!ObjectUtils.isEmpty(doctor.getProfession())) {
            returnDoctor.setProfession(doctor.getProfession());
        }
        if (!ObjectUtils.isEmpty(doctor.getName())) {
            returnDoctor.setName(doctor.getName());
        }
        if (!ObjectUtils.isEmpty(doctor.getOrgan())) {
            returnDoctor.setOrgan(doctor.getOrgan());
        }
        if (!ObjectUtils.isEmpty(doctor.getPhoto())) {
            returnDoctor.setPhoto(doctor.getPhoto());
        }
        if (!ObjectUtils.isEmpty(doctor.getDomain())) {
            returnDoctor.setDomain(doctor.getDomain());
        }
        return returnDoctor;
    }
}