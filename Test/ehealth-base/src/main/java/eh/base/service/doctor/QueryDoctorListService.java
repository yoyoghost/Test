package eh.base.service.doctor;

import ctd.account.UserRoleToken;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import ctd.util.context.Context;
import ctd.util.context.ContextUtils;
import eh.base.constant.BussTypeConstant;
import eh.base.constant.ErrorCode;
import eh.base.constant.ProfessionConstant;
import eh.base.dao.DoctorDAO;
import eh.base.dao.EmploymentDAO;
import eh.base.dao.HisServiceConfigDAO;
import eh.base.dao.QueryDoctorListDAO;
import eh.base.service.DoctorInfoService;
import eh.base.service.DrugListService;
import eh.base.service.organ.QueryOrganService;
import eh.bus.constant.SearchConstant;
import eh.bus.dao.ConsultDAO;
import eh.bus.dao.ConsultSetDAO;
import eh.bus.dao.SearchContentDAO;
import eh.bus.service.common.CurrentUserInfo;
import eh.entity.base.Doctor;
import eh.entity.base.Employment;
import eh.entity.bus.ConsultSet;
import eh.entity.bus.SearchContent;
import eh.utils.DateConversion;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Query;
import org.hibernate.StatelessSession;
import org.springframework.util.ObjectUtils;

import java.util.*;

/**
 * Created by luf on 2016/6/17.
 */

public class QueryDoctorListService {

    /**
     * 转诊会诊医生列表查询服务（原queryDoctorListTransferWithApp）-原生
     * <p>
     * 针对原生端需求将两个医生列表合并为一个接口
     * <p>
     * eh.base.dao
     *
     * @param bussType   业务类型-1转诊，2会诊,3咨询,4预约
     * @param department 科室编码
     * @param organId    机构内码
     * @param flag       标志-0所有有效医生1所有个人医生
     * @return
     * @throws DAOException List<Doctor>
     * @author luf 2016-6-20
     */
    @RpcService
    public List<Doctor> queryDoctorList(int bussType, Integer department, int organId, int flag) throws DAOException {
        QueryDoctorListDAO queryDoctorListDAO = DAOFactory.getDAO(QueryDoctorListDAO.class);
        if (flag == 0) {
            return queryDoctorListDAO.queryDoctorListTransferWithApp(bussType, department, organId);
        } else {
            return queryDoctorListDAO.inDoctorListForRecive(bussType, department, organId);
        }
    }

    /**
     * 查询电话咨询号源页面的推荐医生列表
     *
     * @param doctorId 无电话号源的doctorId
     * @param addrArea 区域
     * @return
     */
    @RpcService
    public List<Doctor> queryDoctorListForAppointConsult(final int doctorId,
                                                         final String addrArea) {
        if (StringUtils.isEmpty(addrArea)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "addrArea is needed");
        }

        if (doctorId == 0) {
            throw new DAOException(DAOException.VALUE_NEEDED, "doctorId is needed");
        }

        List<Doctor> list = new ArrayList<Doctor>();
        List<Doctor> returnList = new ArrayList<Doctor>();

        DoctorDAO docDao = DAOFactory.getDAO(DoctorDAO.class);
        ConsultSetDAO setDao = DAOFactory.getDAO(ConsultSetDAO.class);
        ConsultDAO consultDao = DAOFactory.getDAO(ConsultDAO.class);

        Doctor doctor = docDao.get(doctorId);

        //获取专科
        String profession = doctor.getProfession();

        Map<String, String> map = CurrentUserInfo.getCurrentWxProperties();
        String manageUnitId = "eh";
        if (map != null && !StringUtils.isEmpty(map.get("manageUnitId"))) {
            manageUnitId = map.get("manageUnitId");
        }

        if ("eh".equals(manageUnitId)) {
            //全国性
            list = queryNationalDoctorListForAppointConsult(doctorId, addrArea, profession);
        } else {
            //个性化
            list = docDao.findRemdUnitDoctorListForAppointConsult(doctorId, profession);
        }

        for (Doctor doc : list) {
            int docId = doc.getDoctorId();
            ConsultSet set = setDao.get(docId);
            int maxDay = set.getAppointDays() == null ? 0 : set.getAppointDays().intValue();

            if (maxDay == 0) {
                continue;
            }
//            Date EffDate = consultDao.getEffAfterForLastEff(DateConversion.getFormatDate(new Date(), "yyyy-MM-dd"), docId,maxDay);
//            if(returnList.size()<4 && EffDate!=null ){
            // 2016-12-2 luf:健康2.7需求001部分
            if (returnList.size() < 4 && (set.getStartTime1() != null || set.getStartTime2() != null ||
                    set.getStartTime3() != null || set.getStartTime4() != null || set.getStartTime5() != null ||
                    set.getStartTime6() != null || set.getStartTime7() != null)) {
                returnList.add(docDao.getPartDocInfo(doc));
            }

            if (returnList.size() >= 4) {
                break;
            }
        }

        Collections.sort(returnList, new Comparator<Doctor>() {
            public int compare(Doctor arg0, Doctor arg1) {
                return arg1.getGoodRating().compareTo(arg0.getGoodRating());
            }
        });

        return returnList;

    }

    private List<Doctor> queryNationalDoctorListForAppointConsult(int doctorId, String addrArea, String profession) {
        List<Doctor> list = new ArrayList<Doctor>();
        DoctorDAO docDao = DAOFactory.getDAO(DoctorDAO.class);

        int length = addrArea.length();
        String area = null;
        String city = null;
        String province = null;

        if (length == 6) {
            area = addrArea;
            city = addrArea.substring(0, 4);
            province = addrArea.substring(0, 2);
        }
        if (length == 4) {
            city = addrArea.substring(0, 4);
            province = addrArea.substring(0, 2);
        }
        if (length == 2) {
            province = addrArea.substring(0, 2);
        }

        if (!StringUtils.isEmpty(area)) {
            List<Doctor> areaDocList = docDao.findRemdDoctorListForAppointConsult(doctorId, area, area, profession);
            if (areaDocList.size() > 0) {
                list.addAll(areaDocList);
            }
        }

        if (!StringUtils.isEmpty(city)) {
            List<Doctor> cityDocList = docDao.findRemdDoctorListForAppointConsult(doctorId, city, area, profession);
            if (cityDocList.size() > 0) {
                list.addAll(cityDocList);
            }
        }

        if (!StringUtils.isEmpty(province)) {
            List<Doctor> provinceDocList = docDao.findRemdDoctorListForAppointConsult(doctorId, province, city, profession);
            if (provinceDocList.size() > 0) {
                list.addAll(provinceDocList);
            }
        }

        return list;
    }

    /**
     * 获取专家解读的专家列表数
     *
     * @param profession 专科科编号
     * @param start      起始页
     * @param limit      每页限制条数
     * @return List<Map<String, Object>>
     * @author cuill 2017-02-14
     */
    @RpcService
    public List<Map<String, Object>> queryDoctorListForProfessorConsult(String profession, int start,
                                                                        int limit) {
        QueryDoctorListDAO queryDoctorListDAO = DAOFactory.getDAO(QueryDoctorListDAO.class);
        List<Doctor> doctorList = null;
        if (profession.equals(ProfessionConstant.PRODESSOR_NEUROLOGY)) {
            doctorList = queryDoctorListDAO.queryDoctorListForExpertConsultAndNeurology(profession,
                    start, limit);
        } else {
            doctorList = queryDoctorListDAO.queryDoctorListForExpertConsult(profession,
                    start, limit);
        }
        List<Map<String, Object>> targets = new ArrayList<Map<String, Object>>();
        for (Doctor doctor : doctorList) {
            Map<String, Object> doctorMessage = new HashMap<String, Object>();
            int doctorId = doctor.getDoctorId();
            ConsultSetDAO dao = DAOFactory.getDAO(ConsultSetDAO.class);
            ConsultSet consultSet = dao.getById(doctorId);
            DoctorInfoService doctorInfoService = AppContextHolder.getBean("eh.doctorInfoService", DoctorInfoService.class);
            doctorMessage.put("doctor", doctorInfoService.professorConsultDoctorMsg(doctor));
            doctorMessage.put("consultSet", professorConsultDoctorConsultSet(consultSet));
            targets.add(doctorMessage);
        }
        return targets;
    }


    /**
     * 返回指定的ConsultSet对象
     *
     * @param consultSet
     * @return
     * @author cuill 2017年2月16日
     */
    private ConsultSet professorConsultDoctorConsultSet(ConsultSet consultSet) {
        ConsultSet result = new ConsultSet();
        result.setDoctorId(consultSet.getDoctorId());
        result.setProfessorConsultStatus(consultSet.getProfessorConsultStatus());
        if (!ObjectUtils.isEmpty(consultSet.getProfessorConsultPrice())) {
            result.setProfessorConsultPrice(consultSet.getProfessorConsultPrice());
        }
        return result;
    }

    /**
     * 根据bae_doctor和bus_consultSet这两张表来判断大科室里面开通专家解读的医生数量.
     * 返回的结果为按照科室医生的数量从多到少排序,如果全科科室有医生的话,全科科室排在第一位。
     *
     * @return List<HashMap<String, Object>>
     * @author cuill 2017年2月15日
     */
    @RpcService
    public List<HashMap<String, Object>> findProfessionList() {
        List<HashMap<String, Object>> target = new ArrayList<HashMap<String, Object>>();
        QueryDoctorListDAO queryDoctorListDAO = DAOFactory.getDAO(QueryDoctorListDAO.class);
        List<Object[]> results = queryDoctorListDAO.findProfessionList();
        for (int i = 0; i < results.size(); i++) {
            try {
                HashMap<String, Object> paramMap = new HashMap<String, Object>();
                Object[] obj = results.get(i);
                String professionId = (String) obj[0];
                Long doctorNumber = (Long) obj[1];
                paramMap.put("professionId", professionId);
                paramMap.put("doctorNumber", doctorNumber);
                paramMap.put("professionText", DictionaryController.instance().get("eh.base.dictionary.Profession").getText(professionId));
                //判断如果专科是全科医室的话,就排在第一位.其他的还是按照科室的医生数量从多到少排序
                if (professionId.equals(ProfessionConstant.PRODESSOR_GENERALPRACTICE)) {
                    target.add(0, paramMap);
                } else if (professionId.equals(ProfessionConstant.PRODESSOR_NEUROLOGY)) { //如果专科是神经专科的时候要做特定的排序{
                    if (target.size() == 0) {
                        target.add(paramMap);
                    } else {
                        for (int j = 0; j < target.size(); j++) {
                            if (doctorNumber > (long) (target.get(j).get("doctorNumber"))) {
                                target.add(j, paramMap);
                                break;
                            } else if (j == (target.size() - 1)) {
                                target.add(paramMap);
                                break;
                            }
                        }
                    }
                } else {
                    target.add(paramMap);
                }
            } catch (ControllerException e) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "获取专科名称错误");
            }
        }
        return target;
    }

    /**
     * @param search
     * @param addrArea
     * @param organId
     * @param profession
     * @param proTitle
     * @param mpiId
     * @param start
     * @param limit
     * @param flag       0-开过处方 1-没有开过处方
     * @param mark       调用入口标志-0咨询，1预约，2在线续方（这里的在线续方是新方法）
     * @param queryParam 扩展参数 drugId 平台药品编号
     * @return
     * @author zhongzx
     */
    @RpcService
    public Map<String, Object> searchDoctorConsultOrCanRecipeExt(String search, String addrArea, Integer organId, String profession, String proTitle, String mpiId, int start,
                                                                 int limit, int flag, int mark, Map<String, Object> queryParam) {
        return searchDoctorConsultOrCanRecipeExtImpl(search, addrArea, organId, profession, proTitle, mpiId, start, limit, flag, mark, queryParam, null);
    }

    /**
     * 在线续方用药指导专用接口，查询医学部医生
     * @param addrArea
     * @param organId
     * @param proTitle
     * @param mpiId
     * @param start
     * @param limit
     * @return
     * @author liuya
     */
    @RpcService
    public Map<String, Object> searchDoctorListForConduct(String addrArea, Integer organId, String proTitle, String mpiId, int start, int limit){
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        int startNext = start + limit;
        List<Map<String, Object>> docInfoList;

        docInfoList = doctorDAO.queryDoctorListForConduct(addrArea, organId, proTitle, start, limit);

        Map<String, Object> resultInfo = new HashMap<>();
        resultInfo.put("docList", docInfoList);
        resultInfo.put("start", startNext);
        return resultInfo;

    }

    /**
     * 按医生姓名搜索结果
     * @param flag       0-开过处方 1-没有开过处方
     * @param mark       调用入口标志-0咨询，1预约，2在线续方（这里的在线续方是新方法）
     * @param queryParam 扩展参数 drugId 平台药品编号
     * @param doctorName 医生姓名
     * @return
     * @author liuya
     */
    @RpcService
    public Map<String, Object> searchDoctorConsultOrCanRecipeByDoctorName(String search, String addrArea, Integer organId, String profession, String proTitle, String mpiId, int start,
                                                                 int limit, int flag, int mark, Map<String, Object> queryParam, String doctorName) {
        /*
          保存搜索记录
         */
        if(StringUtils.isNotEmpty(doctorName) && StringUtils.isNotEmpty(mpiId)){
            DrugListService drugListService = AppContextHolder.getBean("eh.drugListService", DrugListService.class);
            drugListService.saveSearchContendForDrug(doctorName, mpiId);
        }
        return searchDoctorConsultOrCanRecipeExtImpl(search, addrArea, organId, profession, proTitle, mpiId, start, limit, flag, mark, queryParam, doctorName);
    }

    @RpcService
    public Map<String, Object> searchDoctorConsultOrCanRecipeExtImpl(String search, String addrArea, Integer organId, String profession, String proTitle, String mpiId, int start,
                                                                 int limit, int flag, int mark, Map<String, Object> queryParam, String doctorName) {
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        if (2 == mark) {
            int startNext = start + limit;
            List<Map<String, Object>> docInfoList;
            if (0 == flag) {
                docInfoList = doctorDAO.queryDoctorsCanRecipe(addrArea, organId, profession, proTitle, start, limit, flag, queryParam, doctorName);
                //出现开过处方列表
                if (docInfoList.size() < limit) {
                    startNext = limit - docInfoList.size();
                    docInfoList.addAll(doctorDAO.queryDoctorsCanRecipe(addrArea, organId, profession, proTitle, 0, startNext, 1, queryParam, doctorName));
                    flag = 1;
                }
            } else {
                docInfoList = doctorDAO.queryDoctorsCanRecipe(addrArea, organId, profession, proTitle, start, limit, flag, queryParam, doctorName);
            }
            Map<String, Object> resultInfo = new HashMap<>();
            resultInfo.put("docList", docInfoList);
            resultInfo.put("flag", flag);
            resultInfo.put("start", startNext);
            return resultInfo;
        } else {
            return doctorDAO.searchDoctorConsultOrCanRecipe(search, addrArea, organId, profession, proTitle, mpiId, start, limit, flag, mark);
        }
    }

    /**
     * 按条件查询有云诊室排班的医生列表
     *
     * @param bus           业务类型-1转诊2会诊3咨询4预约
     * @param doctorName    医生姓名
     * @param searchOrganId 机构id
     * @param start         页面开始位置
     * @param limit         每页限制条数
     * @return ArrayList<Doctor>
     * @author zhangsl 2017-02-16 13:57:57
     */
    @RpcService
    public List<Doctor> searchDoctorsWithClinic(int bus, String doctorName, Integer searchOrganId, int start, int limit) {
        List<Integer> oList = AppContextHolder.getBean("eh.queryOrganService", QueryOrganService.class)
                .findUnitOpauthorizeOrganIds(BussTypeConstant.APPOINTMENT);
        UserRoleToken ur = (UserRoleToken) ContextUtils
                .get(Context.USER_ROLE_TOKEN);
        Doctor doctor = (Doctor) ur.getProperty("doctor");

        List<Doctor> targets = new ArrayList<>();
        if (oList == null || doctor == null) {
            return targets;
        }

        QueryDoctorListDAO queryDoctorListDAO = DAOFactory.getDAO(QueryDoctorListDAO.class);
        List<Doctor> ds = queryDoctorListDAO.searchDoctorWithClinic(doctorName, searchOrganId, start, limit, doctor.getDoctorId(), oList);
        if (ds == null || ds.isEmpty()) {
            return targets;
        }

        EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
        for (Doctor d : ds) {
            Employment employment = employmentDAO.getPrimaryEmpByDoctorId(d.getDoctorId());
            if (employment != null) {
                d.setDepartment(employment.getDepartment());
            }
            targets.add(d);
        }
        return targets;
    }

    /**
     * 按机构科室或姓名查找会诊中心
     * @param doctorName
     * @param searchOrganId
     * @param departmentId
     * @param start
     * @param limit
     * @return
     * @author zhangsl 2017-06-01 15:08:48
     */
    @RpcService
    public List<Doctor> searchDoctorsForMeetCenter(String doctorName, Integer searchOrganId, Integer departmentId, int start, int limit) {
        List<Integer> oList = AppContextHolder.getBean("eh.queryOrganService", QueryOrganService.class)
                .findUnitOpauthorizeOrganIds(BussTypeConstant.MEETCLINIC);
        UserRoleToken ur = (UserRoleToken) ContextUtils
                .get(Context.USER_ROLE_TOKEN);
        Doctor doctor = (Doctor) ur.getProperty("doctor");

        List<Doctor> targets = new ArrayList<>();
        if (oList == null || doctor == null) {
            return targets;
        }

        QueryDoctorListDAO queryDoctorListDAO = DAOFactory.getDAO(QueryDoctorListDAO.class);
        List<Doctor> ds = queryDoctorListDAO.searchDoctorsForMeetCenter(doctorName, searchOrganId, departmentId, start, limit, doctor.getDoctorId(), oList);
        if (ds == null || ds.isEmpty()) {
            return targets;
        }

        //更新医生机构科室信息为被查询的信息
        EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
        for (Doctor d : ds) {
            if (searchOrganId == 0) {
                Employment employment = employmentDAO.getPrimaryEmpByDoctorId(d.getDoctorId());
                if (employment != null) {
                    d.setOrgan(employment.getOrganId());
                    d.setDepartment(employment.getDepartment());
                }
            } else {
                d.setOrgan(searchOrganId);
                if (departmentId == 0) {
                    List<Employment> employments = employmentDAO.findByDoctorIdAndOrganId(d.getDoctorId(), searchOrganId);
                    if (!employments.isEmpty()) {
                        d.setDepartment(employments.get(0).getDepartment());
                    }
                } else {
                    d.setDepartment(departmentId);
                }
            }
            d.setIsOpen(1);
            targets.add(d);
        }
        return targets;
    }

    /**
     * 按条件查询有云诊室排班的医生列表
     * @param doctorName    医生姓名
     * @param searchOrganId 机构id
     * @param departmentId  科室id
     * @param workDate      号源日期
     * @param start         页面开始位置
     * @param limit         每页限制条数
     * @return ArrayList<Doctor>
     * @author zhangsl 2017-06-01 15:08:56
     */
    @RpcService
    public List<Doctor> searchDoctorsForClinic(String doctorName, Integer searchOrganId, Integer departmentId, Date workDate, int start, int limit) {
        List<Integer> oList = AppContextHolder.getBean("eh.queryOrganService", QueryOrganService.class)
                .findUnitOpauthorizeOrganIds(BussTypeConstant.APPOINTMENT);
        UserRoleToken ur = (UserRoleToken) ContextUtils
                .get(Context.USER_ROLE_TOKEN);
        Doctor doctor = (Doctor) ur.getProperty("doctor");

        List<Doctor> targets = new ArrayList<>();
        if (oList == null || doctor == null) {
            return targets;
        }
        Date now = new Date();
        //获取当天云门诊可约机构列表
        HisServiceConfigDAO hisServiceConfigDAO = DAOFactory.getDAO(HisServiceConfigDAO.class);
        List<Integer> todayOrgans = hisServiceConfigDAO.findByCanAppointToday();
        if ((todayOrgans == null || todayOrgans.isEmpty()) && DateConversion.isSameDay(workDate, now)) {//查询当天号源医生但无可约当天号源机构直接返回空
            return targets;
        }

        QueryDoctorListDAO queryDoctorListDAO = DAOFactory.getDAO(QueryDoctorListDAO.class);
        List<Doctor> ds = queryDoctorListDAO.searchDoctorsForClinic(doctorName, searchOrganId, departmentId, workDate, start, limit, oList, doctor.getDoctorId(), now, todayOrgans);
        if (ds == null || ds.isEmpty()) {
            return targets;
        }

        //更新医生机构科室信息为被查询的信息
        EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
        for (Doctor d : ds) {
            if (searchOrganId == 0) {
                Employment employment = employmentDAO.getPrimaryEmpByDoctorId(d.getDoctorId());
                if (employment != null) {
                    d.setOrgan(employment.getOrganId());
                    d.setDepartment(employment.getDepartment());
                }
            } else {
                d.setOrgan(searchOrganId);
                if (departmentId == 0) {
                    List<Employment> employments = employmentDAO.findByDoctorIdAndOrganId(d.getDoctorId(), searchOrganId);
                    if (!employments.isEmpty()) {
                        d.setDepartment(employments.get(0).getDepartment());
                    }
                } else {
                    d.setDepartment(departmentId);
                }
            }
            d.setIsOpen(1);
            targets.add(d);
        }
        return targets;
    }
}
