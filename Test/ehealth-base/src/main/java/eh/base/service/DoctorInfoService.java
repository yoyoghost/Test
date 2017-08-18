package eh.base.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.base.constant.DisCountTypeConstant;
import eh.base.constant.ServiceType;
import eh.base.constant.SystemConstant;
import eh.base.dao.*;
import eh.bus.constant.ConsultConstant;
import eh.bus.constant.OrganConstant;
import eh.bus.constant.RequestModeConstant;
import eh.bus.dao.AppointSourceDAO;
import eh.bus.dao.ConsultSetDAO;
import eh.bus.service.ConsultSetService;
import eh.entity.base.Doctor;
import eh.entity.base.DoctorGroupAndDoctor;
import eh.entity.base.Employment;
import eh.entity.bus.ConsultSet;
import eh.entity.his.HisDoctorParam;
import eh.entity.mpi.Recommend;
import eh.entity.mpi.RelationDoctor;
import eh.mpi.dao.RecommendDAO;
import eh.mpi.dao.RelationDoctorDAO;
import eh.util.RpcAsynchronousUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.ObjectUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DoctorInfoService {


    /**
     * 患者端查看医生信息
     *
     * @param docId 被查看的医生id
     * @param mpi   当前登陆患者的mpi
     * @return
     * @author zhangx
     * @date 2015-12-22 下午5:19:09
     * @date 2016-3-3 luf 修改异常code
     * <p>
     * 2016-11-16 zhangx：紧急需求，未登录情况下扫码关注的医生需要展示在首页，点这些医生跳转到医生主页，
     * 关注按钮只显示，不可操作,其他未登录情况下，可操作,登录情况下，可操作
     */
    @RpcService
    public HashMap<String, Object> getDoctorInfoForHealth(Integer docId,
                                                          String mpi) {
        DoctorDAO docDao = DAOFactory.getDAO(DoctorDAO.class);
        EmploymentDAO empDao = DAOFactory.getDAO(EmploymentDAO.class);
        RelationDoctorDAO relationDao = DAOFactory
                .getDAO(RelationDoctorDAO.class);
        RecommendDAO recommDAO = DAOFactory.getDAO(RecommendDAO.class);

        HashMap<String, Object> returnMap = new HashMap<String, Object>();

        Doctor doc = docDao.getByDoctorId(docId);

        if (doc == null) {
            throw new DAOException(600, "医生" + docId + "不存在");
        }

        if (null == doc.getTeams()) {
            doc.setTeams(false);
        }

        Boolean teamsFlag = doc.getTeams();

        Employment emp = empDao.getPrimaryEmpByDoctorId(docId);
        doc.setDepartment(emp.getDepartment());

        // 获取签约标记，关注标记，关注ID
        RelationDoctor relation = relationDao
                .getByMpiIdAndDoctorIdAndRelationType(mpi, docId);

        if (relation == null) {
            doc.setIsRelation(false);
            doc.setIsSign(false);
            doc.setRelationId(null);
        } else {
            Integer type = relation.getRelationType();
            Integer relationId = relation.getRelationDoctorId();
            doc.setIsSign(false);
            doc.setIsRelation(true);
            doc.setRelationId(relationId);
            if (type != null && type == 0) {
                doc.setIsSign(true);
            }
        }

        // 获取医生关注数(患者关注+医生关注)
        doc.setRelationNum(docDao.doctorRelationNumber(docId));
        ConsultSet docSet = getDoctorDisCountSet(docId, mpi, doc.getIsSign());

        if (teamsFlag) {
            //是团队
            doc.setHaveAppoint(0);

            DoctorGroupDAO groupDAO = DAOFactory.getDAO(DoctorGroupDAO.class);
            List<Doctor> members = new DoctorGroupService().getTeamMembersForHealth(docId, 0, 32);
            Long memberNum = groupDAO.getMemberNum(docId);

            HashMap<String, Object> map = new HashMap<String, Object>();
            map.put("memberNum", memberNum);
            map.put("members", members);

            returnMap.put("teamInfo", map);
        } else {
            //不是团队

            // 2016-3-8 luf 根据患者端号源查询接口返回参数判断医生是否有号
            AppointSourceDAO asDao = DAOFactory.getDAO(AppointSourceDAO.class);
            List<Object[]> oss = null;
            Map currentWxAppProps = getCurrentWxAppProps();
            //如果当前是机构个性化号，需要传入机构ID
            Object wxType = currentWxAppProps==null?null:currentWxAppProps.get("type");
            if (currentWxAppProps != null && wxType != null && ((String) wxType).equalsIgnoreCase("2")) {
                Integer organId = ((List<Integer>) currentWxAppProps.get("organs")).get(0);
                oss = asDao.findTotalByDcotorIdWithOrgan(docId, 1, organId);// 患者端固定传1
            } else {
                oss = asDao.findTotalByDcotorId(docId, 1);// 患者端固定传1
            }

            if (oss != null && oss.size() > 0) {
                doc.setHaveAppoint(1);
            } else {
                doc.setHaveAppoint(0);
            }


        }

        List<Recommend> list = recommDAO.findByMpiIdAndDoctorId(mpi, docId);
        for (Recommend recommend : list) {
            // 0特需预约1图文咨询2电话咨询3寻医问药
            Integer recommendType = recommend.getRecommendType();
            switch (recommendType) {
                case 0:
                    docSet.setPatientTransferRecomFlag(true);
                    break;
                case 1:
                    docSet.setOnLineRecomFlag(true);
                    break;
                case 2:
                    docSet.setAppointRecomFlag(true);
                    break;
                case 3:
                    docSet.setRecipeConsultRecomFlag(true);
                    break;
                default:
                    break;
            }
        }


        returnMap.put("doctor", doc);
        returnMap.put("consultSet", docSet);
        returnMap.put("operation", true);//前端按钮是否可操作，登录情况下可操作
        return returnMap;
    }

    public HashMap<String, Object> getDoctorInfoForHealthNew(Integer docId,
                                                          String mpi, Integer organ , Integer departId) {
        DoctorDAO docDao = DAOFactory.getDAO(DoctorDAO.class);
        EmploymentDAO empDao = DAOFactory.getDAO(EmploymentDAO.class);
        RelationDoctorDAO relationDao = DAOFactory
                .getDAO(RelationDoctorDAO.class);
        RecommendDAO recommDAO = DAOFactory.getDAO(RecommendDAO.class);

        HashMap<String, Object> returnMap = new HashMap<String, Object>();

        Doctor doc = docDao.getByDoctorId(docId);

        if (doc == null) {
            throw new DAOException(600, "医生" + docId + "不存在");
        }

        if (null == doc.getTeams()) {
            doc.setTeams(false);
        }

        Boolean teamsFlag = doc.getTeams();

        Employment emp = empDao.getPrimaryEmpByDoctorId(docId);
        doc.setDepartment(emp.getDepartment());

        // 获取签约标记，关注标记，关注ID
        RelationDoctor relation = relationDao
                .getByMpiIdAndDoctorIdAndRelationType(mpi, docId);

        if (relation == null) {
            doc.setIsRelation(false);
            doc.setIsSign(false);
            doc.setRelationId(null);
        } else {
            Integer type = relation.getRelationType();
            Integer relationId = relation.getRelationDoctorId();
            doc.setIsSign(false);
            doc.setIsRelation(true);
            doc.setRelationId(relationId);
            if (type != null && type == 0) {
                doc.setIsSign(true);
            }
        }

        // 获取医生关注数(患者关注+医生关注)
        doc.setRelationNum(docDao.doctorRelationNumber(docId));
        ConsultSet docSet = getDoctorDisCountSet(docId, mpi, doc.getIsSign());

        if (teamsFlag) {
            //是团队
            doc.setHaveAppoint(0);

            DoctorGroupDAO groupDAO = DAOFactory.getDAO(DoctorGroupDAO.class);
            List<Doctor> members = new DoctorGroupService().getTeamMembersForHealth(docId, 0, 32);
            Long memberNum = groupDAO.getMemberNum(docId);

            HashMap<String, Object> map = new HashMap<String, Object>();
            map.put("memberNum", memberNum);
            map.put("members", members);

            returnMap.put("teamInfo", map);
        } else {
            //不是团队
            DoctorDAO doctorDao = DAOFactory.getDAO(DoctorDAO.class);
            // 2016-3-8 luf 根据患者端号源查询接口返回参数判断医生是否有号
            AppointSourceDAO asDao = DAOFactory.getDAO(AppointSourceDAO.class);
            List<Object[]> oss = asDao.findTotalByDcotorId(docId, 1);// 患者端固定传1
            if (oss != null && oss.size() > 0) {
                doc.setHaveAppoint(1);
            } else {
                doc.setHaveAppoint(0);
            }
            Doctor sourceDoctor = doctorDao.getRealTimeAppointDepartDoctorHaveAppointSource(docId, 1, organ, departId, null);
            doc.setHaveAppoint(sourceDoctor.getHaveAppoint());

        }

        List<Recommend> list = recommDAO.findByMpiIdAndDoctorId(mpi, docId);
        for (Recommend recommend : list) {
            // 0特需预约1图文咨询2电话咨询3寻医问药
            Integer recommendType = recommend.getRecommendType();
            switch (recommendType) {
                case 0:
                    docSet.setPatientTransferRecomFlag(true);
                    break;
                case 1:
                    docSet.setOnLineRecomFlag(true);
                    break;
                case 2:
                    docSet.setAppointRecomFlag(true);
                    break;
                case 3:
                    docSet.setRecipeConsultRecomFlag(true);
                    break;
                default:
                    break;
            }
        }


        returnMap.put("doctor", doc);
        returnMap.put("consultSet", docSet);
        returnMap.put("operation", true);//前端按钮是否可操作，登录情况下可操作
        return returnMap;
    }
    /**
     * 患者端查看医生信息(未登录可用)
     *
     * @param docId 被查看的医生id
     * @return
     * @author zhangx
     * @date 2015-12-22 下午5:19:09
     * @date 2016-3-3 luf 修改异常code
     * 2016-11-16 zhangx：紧急需求，未登录情况下扫码关注的医生需要展示在首页，点这些医生跳转到医生主页，
     * 关注按钮只显示，不可操作,其他未登录情况下，可操作,登录情况下，可操作
     */
    @RpcService
    public HashMap<String, Object> getDoctorInfoForHealthUnLogin(Integer docId) {
        DoctorDAO docDao = DAOFactory.getDAO(DoctorDAO.class);
        EmploymentDAO empDao = DAOFactory.getDAO(EmploymentDAO.class);
        HashMap<String, Object> returnMap = new HashMap<String, Object>();
        Doctor doc = docDao.getByDoctorId(docId);

        if (doc == null) {
            throw new DAOException(600, "医生" + docId + "不存在");
        }

        if (null == doc.getTeams()) {
            doc.setTeams(false);
        }

        Boolean teamsFlag = doc.getTeams();

        Employment emp = empDao.getPrimaryEmpByDoctorId(docId);
        doc.setDepartment(emp.getDepartment());

        //默认未关注
        doc.setIsRelation(false);
        doc.setIsSign(false);
        doc.setRelationId(null);

        // 获取医生关注数(患者关注+医生关注)
        doc.setRelationNum(docDao.doctorRelationNumber(docId));

        //默认无优惠，显示默认价格,默认未推荐开通
        ConsultSet docSet = getNoDisCountSet(docId);

        if (teamsFlag) {
            //是团队
            doc.setHaveAppoint(0);

            DoctorGroupDAO groupDAO = DAOFactory.getDAO(DoctorGroupDAO.class);
            List<Doctor> members = new DoctorGroupService().getTeamMembersForHealth(docId, 0, 32);
            Long memberNum = groupDAO.getMemberNum(docId);

            HashMap<String, Object> map = new HashMap<String, Object>();
            map.put("memberNum", memberNum);
            map.put("members", members);

            returnMap.put("teamInfo", map);
        } else {
            //不是团队

            // 2016-3-8 luf 根据患者端号源查询接口返回参数判断医生是否有号
            AppointSourceDAO asDao = DAOFactory.getDAO(AppointSourceDAO.class);

            List<Object[]> oss = null;

            Map currentWxAppProps = getCurrentWxAppProps();
            //如果当前是机构个性化号，需要传入机构ID
            if (currentWxAppProps != null && currentWxAppProps.get("type") != null) {
                if (((String) currentWxAppProps.get("type")).equalsIgnoreCase("2")) {
                    Integer organId = ((List<Integer>) currentWxAppProps.get("organs")).get(0);
                    oss = asDao.findTotalByDcotorIdWithOrgan(docId, 1, organId);// 患者端固定传1
                }
            } else {
                oss = asDao.findTotalByDcotorId(docId, 1);// 患者端固定传1
            }

            if (oss != null && oss.size() > 0) {
                doc.setHaveAppoint(1);
            } else {
                doc.setHaveAppoint(0);
            }
        }

        //异步更新Doctor字段haveAppoint
        //docDao.updateDoctorHaveAppoint(doc,docDao);

        returnMap.put("doctor", doc);
        returnMap.put("consultSet", docSet);
        //前端按钮是否可操作，未登录情况下默认可操作,扫码关注的医生不可操作,其他情况下可操作
        returnMap.put("operation", true);
        return returnMap;
    }


    public Map getCurrentWxAppProps() {
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        HashMap<String, Object> wxOrgansDisplay = organDAO.getWxOrgansDisplay();
        return wxOrgansDisplay;
    }


    @RpcService
    public void updateDocSourceFromHis(int docId){
        HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
        EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
        List<Employment> list = employmentDAO.findByDoctorId(docId);
        for (int i = 0; list.size() > i; i++){
            Employment employment = list.get(i);
            Integer organId  = employment.getOrganId();
            boolean f = hisServiceConfigDao.isServiceEnable(organId, ServiceType.SOURCEREAL);
            if (f) {
                String jobNumber = employment.getJobNumber();
                HisDoctorParam doctorParam = new HisDoctorParam();
                doctorParam.setJobNum(jobNumber);
                doctorParam.setDoctorId(docId);
                doctorParam.setOrganID(employment.getOrganId());
                doctorParam.setOrganizeCode(DAOFactory.getDAO(OrganDAO.class).getByOrganId(employment.getOrganId()).getOrganizeCode());
                new RpcAsynchronousUtil(doctorParam, organId).obtainNowSource();
            }
        }
    }

    @RpcService
    public HashMap<String, Object> getDoctorInfoForHealthUnLoginNew(Integer docId,Integer organ, Integer departId) {
        DoctorDAO docDao = DAOFactory.getDAO(DoctorDAO.class);
        EmploymentDAO empDao = DAOFactory.getDAO(EmploymentDAO.class);


        HashMap<String, Object> returnMap = new HashMap<String, Object>();

        Doctor doc = docDao.getByDoctorId(docId);

        if (doc == null) {
            throw new DAOException(600, "医生" + docId + "不存在");
        }

        if (null == doc.getTeams()) {
            doc.setTeams(false);
        }

        Boolean teamsFlag = doc.getTeams();

        Employment emp = empDao.getPrimaryEmpByDoctorId(docId);
        doc.setDepartment(emp.getDepartment());

        //默认未关注
        doc.setIsRelation(false);
        doc.setIsSign(false);
        doc.setRelationId(null);

        // 获取医生关注数(患者关注+医生关注)
        doc.setRelationNum(docDao.doctorRelationNumber(docId));

        //默认无优惠，显示默认价格,默认未推荐开通
        ConsultSet docSet = getNoDisCountSet(docId);

        if (teamsFlag) {
            //是团队
            doc.setHaveAppoint(0);

            DoctorGroupDAO groupDAO = DAOFactory.getDAO(DoctorGroupDAO.class);
            List<Doctor> members = new DoctorGroupService().getTeamMembersForHealth(docId, 0, 32);
            Long memberNum = groupDAO.getMemberNum(docId);

            HashMap<String, Object> map = new HashMap<String, Object>();
            map.put("memberNum", memberNum);
            map.put("members", members);

            returnMap.put("teamInfo", map);
        } else {
            //不是团队
            DoctorDAO doctorDao = DAOFactory.getDAO(DoctorDAO.class);
            // 2016-3-8 luf 根据患者端号源查询接口返回参数判断医生是否有号
            AppointSourceDAO asDao = DAOFactory.getDAO(AppointSourceDAO.class);
            List<Object[]> oss = asDao.findTotalByDcotorId(docId, 1);// 患者端固定传1
            if (oss != null && oss.size() > 0) {
                doc.setHaveAppoint(1);
            } else {
                doc.setHaveAppoint(0);
            }
            Doctor sourceDoctor = doctorDao.getRealTimeAppointDepartDoctorHaveAppointSource(docId, 1, organ, departId, null);
            doc.setHaveAppoint(sourceDoctor.getHaveAppoint());
        }

        //异步更新Doctor字段haveAppoint
        //docDao.updateDoctorHaveAppoint(doc,docDao);

        returnMap.put("doctor", doc);
        returnMap.put("consultSet", docSet);
        //前端按钮是否可操作，未登录情况下默认可操作,扫码关注的医生不可操作,其他情况下可操作
        returnMap.put("operation", true);
        return returnMap;
    }
    /**
     * 获取优惠政策下的医生设置信息，优惠信息
     *
     * @param docId    医生ID
     * @param mpi      患者ID
     * @param signFlag 签约标记(true签约；false未签约)
     * @return
     */
    public ConsultSet getDoctorDisCountSet(Integer docId, String mpi, Boolean signFlag) {

        return getNoDisCountSet(docId);//无优惠
    }

    /**
     * 方案一：无优惠活动
     *
     * @param docId 医生ID
     * @return 返回医生个人设置(处理null数据)
     */
    public ConsultSet getNoDisCountSet(Integer docId) {
        ConsultSetDAO setDao = DAOFactory.getDAO(ConsultSetDAO.class);
        ConsultSet set = setDao.get(docId);
        if (set == null) {
            set = new ConsultSet();
        }

        // 获取图文咨询，在线咨询，特需预约价格,寻医问药及设置
        ConsultSet docSet = new ConsultSet();
        docSet.setOnLineStatus(set.getOnLineStatus() == null ? Integer.valueOf(0) : set
                .getOnLineStatus());
        docSet.setOnLineConsultPrice(set.getOnLineConsultPrice() == null ? Double.valueOf(0d)
                : set.getOnLineConsultPrice());

        docSet.setAppointStatus(set.getAppointStatus() == null ? Integer.valueOf(0) : set
                .getAppointStatus());
        docSet.setAppointConsultPrice(set.getAppointConsultPrice() == null ?Double.valueOf(0d)
                : set.getAppointConsultPrice());

        docSet.setPatientTransferStatus(set.getPatientTransferStatus() == null ? Integer.valueOf(0)
                : set.getPatientTransferStatus());
        docSet.setPatientTransferPrice(set.getPatientTransferPrice() == null ? Double.valueOf(0d)
                : set.getPatientTransferPrice());

        docSet.setRecipeConsultStatus(set.getRecipeConsultStatus() == null ? Integer.valueOf(0)
                : set.getRecipeConsultStatus());
        docSet.setRecipeConsultPrice(set.getRecipeConsultPrice() == null ? Double.valueOf(0d)
                : set.getRecipeConsultPrice());

        docSet.setProfessorConsultStatus(set.getProfessorConsultStatus() == null ? Integer.valueOf(0)
                : set.getProfessorConsultStatus());
        docSet.setProfessorConsultPrice(set.getProfessorConsultPrice() == null ? Double.valueOf(0d)
                : set.getProfessorConsultPrice());

        docSet.setAppointDays(set.getAppointDays() == null ? Integer.valueOf(0) : set
                .getAppointDays());

        docSet.setSignStatus(set.getSignStatus() == null ? Boolean.valueOf(false) : set.getSignStatus());
        docSet.setCanSign(set.getCanSign() == null ? Boolean.valueOf(false) : set.getCanSign());
        docSet.setSignPrice(set.getSignPrice() == null ? Integer.valueOf(0) : set.getSignPrice());

        //推荐开通标记
        docSet.setPatientTransferRecomFlag(false);
        docSet.setOnLineRecomFlag(false);
        docSet.setAppointRecomFlag(false);
        docSet.setRecipeConsultRecomFlag(false);
        //优惠数据
        docSet.setAppointDisCountType(DisCountTypeConstant.NO_DISCOUNTTYPE);
        docSet.setOnlineDisCountType(DisCountTypeConstant.NO_DISCOUNTTYPE);
        docSet.setOnLineConsultActualPrice(docSet.getOnLineConsultPrice());
        docSet.setFirstOnLineConsultFlag(false);// 不优惠
        docSet.setAppointConsultActualPrice(docSet.getAppointConsultPrice());
        docSet.setFirstAppointConsultFlag(false);// 不优惠

        //判断机构是否有设置价格
        //wx3.1 2017-5-26 20:17:43 zhangx 机构设置价格，则显示机构设置的价格
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Doctor doctor = doctorDAO.get(docId);

        if(doctor!=null){
            Integer organId=doctor.getOrgan();
            ConsultSetService consultSetService = AppContextHolder.getBean("eh.consultSetService", ConsultSetService.class);
            Double organOnlineConsultPrice=consultSetService.getOrganConsultPrice(organId, ConsultConstant.CONSULT_TYPE_GRAPHIC);
            Double organAppointConsultPrice=consultSetService.getOrganConsultPrice(organId, ConsultConstant.CONSULT_TYPE_POHONE);
            Double organRecipeConsultPrice=consultSetService.getOrganConsultPrice(organId, ConsultConstant.CONSULT_TYPE_RECIPE);
            Double organProfessorConsultPrice=consultSetService.getOrganConsultPrice(organId, ConsultConstant.CONSULT_TYPE_PROFESSOR);

            docSet.setOnLineConsultPrice(Double.valueOf(-1).equals(organOnlineConsultPrice)?docSet.getOnLineConsultPrice():organOnlineConsultPrice);
            docSet.setAppointConsultPrice(Double.valueOf(-1).equals(organAppointConsultPrice)?docSet.getAppointConsultPrice():organAppointConsultPrice);
            docSet.setRecipeConsultPrice(
                    Double.valueOf(-1).equals(organRecipeConsultPrice)?docSet.getRecipeConsultPrice():organRecipeConsultPrice);
            docSet.setProfessorConsultPrice(
                    Double.valueOf(-1).equals(organProfessorConsultPrice)?docSet.getProfessorConsultPrice():organProfessorConsultPrice);
        }


        return docSet;
    }

    /**
     * 根据医生ID将关注数量粉丝数和团队信息返回
     *
     * @author zhangsl
     * @Date 2016-11-15 16:47:25
     */

    @RpcService
    public Map<String, Object> getLoginUserInfoNew(Integer doctorId) {
        DoctorDAO docDao = DAOFactory.getDAO(DoctorDAO.class);
        if (!docDao.exist(doctorId)) {
            throw new DAOException(609, "不存在该医生");
        }
        DoctorGroupDAO dgDao = DAOFactory.getDAO(DoctorGroupDAO.class);
        DoctorRelationDoctorDAO rlDao = DAOFactory.getDAO(DoctorRelationDoctorDAO.class);
        Map<String, Object> map = new HashMap<String, Object>();
        Long FocusNumber = rlDao.getDoctorRelationNum(doctorId);
        String rating = StringUtils.isEmpty(docDao.getRatingByDoctorId(doctorId)) ? "0.0" : docDao.getRatingByDoctorId(doctorId);
        // List<DoctorGroup> groups = dgDao.findByMemberId(doctorId);
        List<DoctorGroupAndDoctor> groups = dgDao
                .getDoctorGroupAndDoctorByMemberId(doctorId, 0);
        map.put("focusNumber", FocusNumber);
        map.put("rating", rating);
        map.put("groups", groups);
        // 关注医生A的患者总数
        RelationDoctorDAO relDao = DAOFactory.getDAO(RelationDoctorDAO.class);
        map.put("relationPatientNum", relDao.getRelationNum(doctorId, 1));

        // 关注医生A的医生总数
        DoctorRelationDoctorDAO docRelDao = DAOFactory
                .getDAO(DoctorRelationDoctorDAO.class);
        map.put("relationDocNum",
                docRelDao.getDoctorRelationDoctorNum(doctorId));
        //医生点赞数
        PatientFeedbackDAO pfDao = DAOFactory.getDAO(PatientFeedbackDAO.class);
        Long PatientFeedbackNum = pfDao.getNumByDoctorIdAndUserType(doctorId, "doctor");//只显示医生点赞数
        map.put("PatientFeedbackNum", PatientFeedbackNum);
        return map;
    }

    /**
     * 返回指定Doctor属性值的对象
     *
     * @param doctor
     * @return
     * @Author cuill 2017年2月16日
     */
    public Doctor professorConsultDoctorMsg(Doctor doctor) {
        Doctor resultDoctor = new Doctor();
        EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
        Employment employment = employmentDAO.getPrimaryEmpByDoctorId(doctor.getDoctorId());
        resultDoctor.setDoctorId(doctor.getDoctorId());
        resultDoctor.setIdNumber(doctor.getIdNumber());
        resultDoctor.setProfession(doctor.getProfession());
        if (!ObjectUtils.isEmpty(doctor.getPhoto())) {
            resultDoctor.setPhoto(doctor.getPhoto());
        }
        if (!ObjectUtils.isEmpty(doctor.getName())) {
            resultDoctor.setName(doctor.getName());
        }
        if (!ObjectUtils.isEmpty(doctor.getGender())) {
            resultDoctor.setGender(doctor.getGender());
        }
        if (!ObjectUtils.isEmpty(doctor.getProTitle())) {
            resultDoctor.setProTitle(doctor.getProTitle());
        }
        if (!ObjectUtils.isEmpty(doctor.getRating())) {
            resultDoctor.setRating(doctor.getRating());
        }
        if (!ObjectUtils.isEmpty(doctor.getOrgan())) {
            resultDoctor.setOrgan(doctor.getOrgan());
        }
        if (!ObjectUtils.isEmpty(doctor.getDomain())) {
            resultDoctor.setDomain(doctor.getDomain());
        }
        if (!ObjectUtils.isEmpty(doctor.getDomain())) {
            resultDoctor.setDomain(doctor.getDomain());
        }
        if (!ObjectUtils.isEmpty(employment) && !ObjectUtils.isEmpty(employment.getDepartment())) {
            resultDoctor.setDepartment(employment.getDepartment());
        }
        return resultDoctor;
    }

}
