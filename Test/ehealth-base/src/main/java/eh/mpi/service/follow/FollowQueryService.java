package eh.mpi.service.follow;

import com.alibaba.fastjson.JSONObject;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.dao.EmploymentDAO;
import eh.base.dao.OrganDAO;
import eh.base.dao.RelationLabelDAO;
import eh.base.dao.RelationPatientDAO;
import eh.entity.base.Employment;
import eh.entity.base.FollowChatStatistics;
import eh.entity.base.Organ;
import eh.entity.mpi.*;
import eh.mpi.constant.FollowConstant;
import eh.mpi.dao.*;
import eh.utils.DateConversion;
import eh.utils.LocalStringUtil;
import eh.utils.MapValueUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.util.*;

/**
 * @author renzh
 * @date 2016/7/22 0022 上午 10:13
 */
public class FollowQueryService {
    private static final Logger logger = LoggerFactory.getLogger(FollowQueryService.class);

    private FollowPlanDAO followPlanDAO;
    private FollowScheduleDAO followScheduleDAO;
    private RelationDoctorDAO relationDoctorDAO;
    private OrganDAO organDAO;
    private PatientDAO patientDAO;
    private FamilyMemberDAO familyMemberDAO;

    public FollowQueryService(){
        followPlanDAO = DAOFactory.getDAO(FollowPlanDAO.class);
        followScheduleDAO = DAOFactory.getDAO(FollowScheduleDAO.class);
        relationDoctorDAO = DAOFactory.getDAO(RelationDoctorDAO.class);
        organDAO = DAOFactory.getDAO(OrganDAO.class);
        patientDAO = DAOFactory.getDAO(PatientDAO.class);
        familyMemberDAO = DAOFactory.getDAO(FamilyMemberDAO.class);
    }

    /**
     * 根据随访Id查询当前日程对应的随访
     * @param planId
     * @return
     */
    @RpcService
    public List<FollowPlan> findByPlanId(String planId){
        List<FollowPlan> followPlanList = followPlanDAO.findByPlanId(planId);
        return followPlanList;
    }

    /**
     * 根据mpiId分页查询所有日程（app3.8.1）
     * @param mpiId
     * @param doctorId
     * @param start
     * @param limit
     * @return
     */
    @RpcService
    public Map findByMpiIdV1(String mpiId,int doctorId,int start,int limit){
        List<FollowSchedule> followScheduleList = followScheduleDAO.findByMpiId(mpiId,doctorId,start,limit);
        Map map = new HashMap();
        if(!CollectionUtils.isEmpty(followScheduleList)) {
            map = setScheduleParam(followScheduleList.get(0).getPlanNodeId());
        }
        map.put("followScheduleList",followScheduleList);
        return map;
    }

    /**
     * 根据PlanId分页查询所有日程
     * @param planId
     * @param doctorId
     * @param start
     * @param limit
     * @return
     */
    @RpcService
    public Map<String, Object> findByPlanIdAndLimit(String planId,int doctorId,int start,int limit){
        List<FollowSchedule> followScheduleList = followScheduleDAO.findScheduleListByPlanId(planId,doctorId,start,limit);
        Map<String, Object> map = new HashMap();
        if(!CollectionUtils.isEmpty(followScheduleList)) {
            map = setScheduleParam(followScheduleList.get(0).getPlanNodeId());
        }
        map.put("followScheduleList", setScheduleInfo(followScheduleList));
        return map;
    }

    /**
     * 设置日程附件信息
     * @return
     */
    public List<FollowSchedule> setScheduleInfo(List<FollowSchedule> scheduleList){
        if(null != scheduleList && scheduleList.size() > 0){
            HealthAssessService healthAssessService = AppContextHolder.getBean("eh.healthAssessService", HealthAssessService.class);
            for(FollowSchedule followSchedule:scheduleList){
                Map<String, Object> extraMap = new HashMap<>();
                String formInfo = followSchedule.getFormInfo();
                String articleInfo = followSchedule.getArticleInfo();
                Integer needImage = followSchedule.getNeedImage();
                //附件是否大于1 等于1时是哪个类型的判断
                boolean onlyForm = false;
                boolean onlyArticle = false;
                boolean onlyImage = false;
                boolean nothing = false;
                boolean morething = false;
                List<Map<String, Object>> formList = new ArrayList<>();
                List<Map<String, Object>> articleList = new ArrayList<>();
                if(StringUtils.isNotEmpty(articleInfo)){
                    articleList = JSONUtils.parse(articleInfo, List.class);
                    extraMap.put("articleList", articleList);
                }
                if(StringUtils.isNotEmpty(formInfo)){
                    formList = JSONUtils.parse(formInfo, List.class);
                    extraMap.put("formList", formList);
                }
                if(formList.size() == 0 && FollowConstant.NEEDIMAGE_NO == needImage && articleList.size() == 0){
                    nothing = true;
                } else if(formList.size() == 1 && FollowConstant.NEEDIMAGE_NO == needImage && articleList.size() == 0){
                    onlyForm = true;
                } else if(articleList.size() == 1 && FollowConstant.NEEDIMAGE_NO == needImage && formList.size() == 0){
                    onlyArticle = true;
                } else if(formList.size() == 0 && FollowConstant.NEEDIMAGE_YES == needImage && articleList.size() == 0){
                    onlyImage = true;
                } else{
                    morething = true;
                }
                extraMap.put("onlyForm", onlyForm);
                extraMap.put("onlyArticle", onlyArticle);
                extraMap.put("onlyImage", onlyImage);
                extraMap.put("nothing", nothing);
                extraMap.put("morething", morething);
                followSchedule.setExtraMap(extraMap);
            }
        }
        return scheduleList;
    }

    public Map<String, Object> setScheduleParam(int planNodeId){
        Map<String,Object> map = new HashMap();
        FollowPlan followPlan = followPlanDAO.get(planNodeId);
        int remindPatient = followPlan.getRemindPatient();
        int remindSelf = followPlan.getRemindSelf();
        int remindSign = followPlan.getRemindSign();
        map.put("remindPatient",remindPatient);
        map.put("remindSelf",remindSelf);
        map.put("remindSign",remindSign);
        map.put("fromType",Integer.parseInt(followPlan.getFromType()));
        return map;
    }

    /**
     * 根据mpiId分页查询所有日程
     * @param mpiId
     * @param doctorId
     * @param start
     * @param limit
     * @return
     */
    @RpcService
    public List<FollowSchedule> findByMpiId(String mpiId,int doctorId,int start,int limit){
        List<FollowSchedule> followScheduleList = followScheduleDAO.findByMpiId(mpiId,doctorId,start,limit);
        return followScheduleList;
    }

    /**
     * 根据mpiId分页查询所有计划
     * @param mpiId
     * @param doctorId
     * @param start
     * @param limit
     * @return
     */
    @RpcService
    public Map findPlanListByMpiId(String mpiId,int doctorId,int start,int limit){
        Map map = new HashMap();
        List<FollowPlan> followPlanList = followPlanDAO.findFollowPlanListByMpiId(mpiId,doctorId,start,limit);
        List<FollowPlan> followPlanListDone = new ArrayList<>();
        if(!CollectionUtils.isEmpty(followPlanList)){
            for(FollowPlan followPlan:followPlanList){
                if(followScheduleDAO.getEnclosureByPlanId(followPlan.getPlanId())>0){
                    followPlan.setIfHaveEnclosure(true);
                }else{
                    followPlan.setIfHaveEnclosure(false);
                }
                followPlanListDone.add(followPlan);
            }
        }
        map.put("followPlanList",followPlanListDone);
        map.put("canCreateNew",canAddFollowPlan(mpiId,doctorId));
        return map;
    }

    /**
     * 我的日程日历-有无随访
     * @param doctorId
     * @return
     */
    @RpcService
    public List<FollowSchedule> findMyMonthSchedule(int doctorId){
        List<FollowSchedule> followScheduleList = followScheduleDAO.findMyMonthSchedule(doctorId);
        return followScheduleList;
    }

    /**
     * 根据入参的月份 查询本月每天 有无随访
     * @param doctorId
     * @param date
     * @return
     */
    @RpcService
    public List<FollowSchedule> findMyScheduleWithMonth(Integer doctorId, Date date){
        List<FollowSchedule> followScheduleList = followScheduleDAO.findScheduleDateByMonth(doctorId, date);
        return followScheduleList;
    }

    /**
     * 选择日历日期查询该医生所选日期所有日程
     * @param doctorId
     * @param date
     * @return
     */
    @RpcService
    public Map<String, Object> findScheduleByDate(int doctorId, Date date, int start,int limit){
        Map<String, Object> map = new HashMap<>();
        List<FollowScheduleAndPatient> followScheduleAndPatientList = new ArrayList();
        List<FollowScheduleAndPatient> followScheduleAndPatientListManual = new ArrayList();
        List<FollowScheduleAndPatient> followScheduleAndPatientListAuto = new ArrayList();
        List<FollowSchedule> followScheduleList = followScheduleDAO.findScheduleByDate(doctorId,date,start,limit);
        for (FollowSchedule followSchedule : followScheduleList){
            String mpiId = followSchedule.getMpiId();
            Patient patient = patientDAO.getPatientByMpiId(mpiId);
            //添加患者标签
            RelationPatientDAO relationPatientDAO = DAOFactory.getDAO(RelationPatientDAO.class);
            RelationDoctor relationDoctor = relationPatientDAO.getByMpiidAndDoctorId(mpiId, doctorId);
            if(null != relationDoctor) {
                Integer relationPatientId = relationDoctor.getRelationDoctorId();
                RelationLabelDAO labelDao = DAOFactory.getDAO(RelationLabelDAO.class);
                List<String> rLabelList = labelDao.findLabelNamesByRPId(relationPatientId);
                patient.setLabelNames(rLabelList);
            }
            //2016-12-12 11:23:48 zhangx wx2.6 由于注册时患者不填写身份证，app前端崩溃，
            //解决方案，idcard字段赋值为空字符串
            if(StringUtils.isEmpty(patient.getIdcard())){
                patient.setIdcard("");
            }
            FollowPlan followPlan = followPlanDAO.get(followSchedule.getPlanNodeId());

            followScheduleAndPatientList.add(setFollowScheduleAndPatient(followSchedule,patient,followPlan));
            String fromType = followPlan.getFromType();
            //就诊患者
            if(FollowConstant.FROMTYPE_PREAPPOINT.equals(fromType) || FollowConstant.FROMTYPE_APPOINT.equals(fromType) || FollowConstant.FROMTYPE_REPORT.equals(fromType)) {
                followScheduleAndPatientListAuto.add(setFollowScheduleAndPatient(followSchedule,patient,followPlan));
            }
            //随访患者
            if(FollowConstant.FROMTYPE_MANUAL.equals(fromType)){
                followScheduleAndPatientListManual.add(setFollowScheduleAndPatient(followSchedule,patient,followPlan));
            }
        }
        map.put("manualCount", followScheduleAndPatientListManual.size());
        map.put("autoCount", followScheduleAndPatientListAuto.size());
        //当天全部的患者
        map.put("completed", sortPatient(followScheduleAndPatientList));
        //随访患者
        map.put("manualData", sortPatient(followScheduleAndPatientListManual));
        //就诊患者
        map.put("autoData", sortPatient(followScheduleAndPatientListAuto));
        return map;
    }

    /**
     * 患者按照标签分类
     * @param list
     * @return
     */
    private List<FollowScheduleAndPatient> sortPatient(List<FollowScheduleAndPatient> list){
        List<FollowScheduleAndPatient> returnList = new ArrayList<>();
        List<String> allLabel = new ArrayList<>();
        Set<String> labelSet = new HashSet<>();
        List<Map<String, Object>> numList = new ArrayList<>();
        for(FollowScheduleAndPatient scheduleAndPatient:list){
            List<String> labelList = scheduleAndPatient.getPatient().getLabelNames();
            if(null != labelList && labelList.size() > 0) {
                allLabel.addAll(labelList);
                labelSet.addAll(labelList);
            }
        }
        for(String label:labelSet){
            Map<String, Object> map = new HashMap<>();
            map.put("label", label);
            map.put("num", Collections.frequency(allLabel, label));
            numList.add(map);
        }
        Collections.sort(numList, new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> o1, Map<String, Object> o2) {
                return (int) o2.get("num") - (int) o1.get("num");
            }
        });

        List<FollowScheduleAndPatient> noLabelList = new ArrayList<>();
        if(numList != null && numList.size() > 0) {
            for (Map<String, Object> map : numList) {
                String label = (String) map.get("label");
                List<FollowScheduleAndPatient> sortList = new ArrayList<>();
                Iterator<FollowScheduleAndPatient> it = list.iterator();
                while (it.hasNext()) {
                    FollowScheduleAndPatient scheduleAndPatient = it.next();
                    List<String> labelList = scheduleAndPatient.getPatient().getLabelNames();
                    if (null != labelList && labelList.size() > 0) {
                        if (labelList.contains(label)) {
                            sortList.add(scheduleAndPatient);
                            it.remove();
                        }
                    } else {
                        noLabelList.add(scheduleAndPatient);
                        it.remove();
                    }
                }
                returnList.addAll(sortList);
            }
            returnList.addAll(noLabelList);
            return returnList;
        }else{
            return list;
        }
    }

    //设置给前端的返回类
    public FollowScheduleAndPatient setFollowScheduleAndPatient(FollowSchedule followSchedule,Patient patient,FollowPlan followPlan){
        String mpiId = followSchedule.getMpiId();
        Integer doctorId = followSchedule.getDoctorId();
        FollowScheduleAndPatient followScheduleAndPatient = new FollowScheduleAndPatient();
        FollowChatMsgDAO followChatMsgDAO = DAOFactory.getDAO(FollowChatMsgDAO.class);
        List<FollowChatMsg> followChatMsgList = followChatMsgDAO.findLatestPatientReply(mpiId, doctorId,0,1);
        //查询患者最新一条回复消息
        if (null != followChatMsgList && followChatMsgList.size() > 0){
            FollowChatMsg followChatMsg = followChatMsgList.get(0);
            followScheduleAndPatient.setHasReply(true);
            followScheduleAndPatient.setFollowChatId(followChatMsg.getFollowChatId());
            followScheduleAndPatient.setSendTime(followChatMsg.getSendTime());
        }
        followScheduleAndPatient.setFollowSchedule(followSchedule);
        followScheduleAndPatient.setPatient(patient);
        followScheduleAndPatient.setRemindPatient(followPlan.getRemindPatient());
        followScheduleAndPatient.setRemindSelf(followPlan.getRemindSelf());
        followScheduleAndPatient.setRemindSign(followPlan.getRemindSign());
        followScheduleAndPatient.setFromType(Integer.parseInt(("".equals(followPlan.getFromType()))?"1":followPlan.getFromType()));
        return followScheduleAndPatient;
    }

    /**
     * 根据所选日期查询当日随访人数
     * @param doctorId
     * @param date
     * @return
     */
    @RpcService
    public Integer getScheduleByDateCount(int doctorId, Date date){
        return followScheduleDAO.getScheduleByDateCount(doctorId,date).size();
    }

    /**
     * 查看患者信息-显示最近两条患者日程
     * @param mpiId
     * @return
     */
    @RpcService
    public Map findNearTwoSchedule(String mpiId,int doctorId){
        Map map = new HashMap();
        //0-有随访 1随访已完成 2无随访
        int flag = 2;
        try {
            List<FollowSchedule> followScheduleList = followScheduleDAO.findNearTwoSchedule(mpiId,doctorId,0,2);
            /*if(followScheduleList!=null&&followScheduleList.size()>0){
                FollowSchedule followSchedule = followScheduleList.get(0);
                Integer isCompleteSchedule = followPlanDAO.getIsCompleteSchedule(followSchedule.getPlanId());
                if (isCompleteSchedule != null) {
                    flag = isCompleteSchedule.intValue();
                }
            }*/
            //判断有无未完成的随访--剔除预约生成的随访
            List<FollowPlan> list = followPlanDAO.findUnfinishedFollowPlanByDoctorIdAndMpiId(mpiId, doctorId);
            if(list != null && list.size() > 0){
                flag = 0;
            }
            Patient patient = relationDoctorDAO.getPatientRelation(mpiId,doctorId);

            //2016-12-12 11:23:48 zhangx wx2.6 由于注册时患者不填写身份证，app前端奔溃，
            //解决方案，idcard字段赋值为空字符串
            if(StringUtils.isEmpty(patient.getIdcard())){
                patient.setIdcard("");
            }

            map.put("guardianFlag",patient.getGuardianFlag());
            map.put("followScheduleList",followScheduleList);
            map.put("patient",patient);
            map.put("flag",flag);
            //app3.8.4健康档案-监护人信息
            if(patient.getGuardianFlag()){//如果有监护人
                Patient guardPatient = patientDAO.getByIdCard(patient.getIdcard().substring(0,18));
                if(guardPatient==null){
                    throw new DAOException(ErrorCode.SERVICE_ERROR, "该监护人不存在");
                }
                Patient target = null;
                map.put("guardianName",guardPatient.getPatientName());
                map.put("guardPatientIdcard",guardPatient.getIdcard());
                FamilyMember familyMember = familyMemberDAO.getByMpiIdAndMemberMpi(guardPatient.getMpiId(),patient.getMpiId());
                if(familyMember!=null){
                    ctd.dictionary.Dictionary dictionary = DictionaryController.instance().get("eh.mpi.dictionary.Relation");
                    map.put("relation",dictionary.getText(familyMember.getRelation()));
                }
                map.put("guardMobile",guardPatient.getMobile());
                map.put("guardFullHomeArea",guardPatient.getFullHomeArea());

            }
        }catch (Exception e){
            logger.error(LocalStringUtil.format("error, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace())));
        }
        return map;
    }

    /**
     * 查询该患者最新的随访
     * @param planCreator
     * @return
     */
    @RpcService
    public Map findByPlanCreatorNearly(int planCreator,String mpiId){
        Map map = new HashMap();
        try {
            String planId = followPlanDAO.findPlanCreatorNearly(planCreator, mpiId).get(0);
            List<FollowPlan> followPlanList = findByPlanId(planId);
            map.put("followPlanList", followPlanList);
            if (followPlanDAO.getIfEnd(planId) == 0) {
                map.put("flag", 0);//计划不可删除 任务还没结束
            } else {
                map.put("flag", 1);//计划可删除 任务结束
            }
        } catch (Exception e){
            logger.error(LocalStringUtil.format("error, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace())));
        }
        return map;
    }

    /**
     * 判断该患者是否有未完成的随访
     * @param mpiId
     * @return
     */
    @RpcService
    public boolean getExisSchedules(int doctorId,String mpiId){
        if (followScheduleDAO.getExisSchedules(doctorId,mpiId)>0){
            return true;
        }else {
            return false;
        }
    }

    /**
     * 设置患者随访信息
     * @param patientList
     */
    public void setPatientFollowInfo(int doctorId,List<Patient> patientList){
        if(null != patientList && !patientList.isEmpty()){
            for(Patient p : patientList){
                if(StringUtils.isNotEmpty(p.getMpiId())) {
                    /**
                     * 能添加最多三个随访
                     */
                    p.setHaveUnfinishedFollow(!this.canAddFollowPlan(p.getMpiId(), doctorId));
                }
            }
        }
    }

    /**
     * 判断患者是否存在签约医生
     * @param mpiId
     * @return
     */
    @RpcService
    public boolean getExisRelationDoctor(int doctorId,String mpiId){
        Boolean flag = false;
        try {
            Organ organ = organDAO.getByDoctorId(doctorId);
            List<Integer> i = relationDoctorDAO.findFamilyDoctorId(mpiId);
            if (!"30".equals(organ.getGrade())&&i.size()>0)
                flag = true;
        } catch (Exception e){
            logger.error(LocalStringUtil.format("error, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace())));
        }
        return flag;
    }

    /**
     * 获取医生当前未读的日程
     * @param doctorId
     * @return
     */
    @RpcService
    public Integer getTodayUnreadScheduleCount(int doctorId){
        return followScheduleDAO.getTodayUnreadScheduleCount(doctorId).size();
    }

    /**
     * 判断医生能否手动添加随访计划（最多可手动添加3个随访计划 App3.8.3）
     * 已完成的随访计划不算在3个名额中 患者报道生成的随访也算在其中
     * @param mpiId
     * @param doctorId
     * @return
     */
    @RpcService
    public boolean canAddFollowPlan(String mpiId,Integer doctorId){
        List<FollowPlan> followPlanList=followPlanDAO.findUnfinishedFollowPlanByDoctorIdAndMpiId(mpiId,doctorId);
        if (followPlanList!=null){
           if (followPlanList.size()<3){
               return true;
           }else {
               return false;
           }
        }
        return true;
    }

    /**
     * 医生app 我的模板/全部模板 功能接口
     * 我的模板：最多展示 5条 使用过的按照使用次数排序。
     * 全部模板：分页查询所有的模板，使用过的按照使用次数排序。
     * @author zhongzx
     * @param doctorId 医生Id
     * @param start    每页起始数
     * @param limit    每页限制条数
     * @return
     */
    @RpcService
    public List<Map<String, Object>> findMyModuleList(Integer doctorId, Integer start, Integer limit) {
        FollowModuleDAO followModuleDAO = DAOFactory.getDAO(FollowModuleDAO.class);
        FollowModulePlanDAO followModulePlanDAO = DAOFactory.getDAO(FollowModulePlanDAO.class);
        List<Map<String, Object>> list = new ArrayList<>();

        EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
        Employment employment = employmentDAO.getPrimaryEmpByDoctorId(doctorId);
        if (null == employment) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "primary Employment is null");
        }
        Integer organId = employment.getOrganId();
        Integer deptId = employment.getDepartment();
        List<FollowModule> moduleList = followModuleDAO.findModuleListByDoctorIdWithPage(doctorId,organId,deptId,start, limit);
        if (moduleList != null && moduleList.size() > 0) {
            for (FollowModule followModule : moduleList) {
                List<FollowModulePlan> followModulePlanList = followModulePlanDAO.findByMid(followModule.getMid());
                Map<String, Object> map = new HashMap<>();
                map.put("followModule", followModule);
                map.put("followModulePlanList", setFollowModulePlanInfo(followModulePlanList));
                list.add(map);
            }
        }
        return list;
    }

    private List<FollowModulePlan> setFollowModulePlanInfo(List<FollowModulePlan> modulePlanList){
        if(null != modulePlanList && modulePlanList.size() > 0){
            for(FollowModulePlan followModulePlan:modulePlanList){
                Map<String, Object> extraMap = new HashMap<>();
                String formInfo = followModulePlan.getFormInfo();
                String articleInfo = followModulePlan.getArticleInfo();
                Integer needImage = followModulePlan.getNeedImage();
                //附件是否大于1 等于1时是哪个类型的判断
                boolean onlyForm = false;
                boolean onlyArticle = false;
                boolean onlyImage = false;
                boolean nothing = false;
                boolean morething = false;
                List<Map<String, Object>> formList = new ArrayList<>();
                List<Map<String, Object>> articleList = new ArrayList<>();
                if(StringUtils.isNotEmpty(articleInfo)){
                    try {
                        articleList = JSONUtils.parse(articleInfo, List.class);
                    }catch (Exception e){
                        logger.error("articleInfo=[{}] parse error=[{}]", articleInfo, e.getMessage());
                    }
                    extraMap.put("articleList", articleList);
                }
                if(StringUtils.isNotEmpty(formInfo)){
                    try {
                        formList = JSONUtils.parse(formInfo, List.class);
                    }catch (Exception e){
                        logger.error("formInfo=[{}] parse error=[{}]", formInfo, e.getMessage());
                    }
                    extraMap.put("formList", formList);
                }
                if(formList.size() == 0 && FollowConstant.NEEDIMAGE_NO == needImage && articleList.size() == 0){
                    nothing = true;
                } else if(formList.size() == 1 && FollowConstant.NEEDIMAGE_NO == needImage && articleList.size() == 0){
                    onlyForm = true;
                } else if(articleList.size() == 1 && FollowConstant.NEEDIMAGE_NO == needImage && formList.size() == 0){
                    onlyArticle = true;
                } else if(formList.size() == 0 && FollowConstant.NEEDIMAGE_YES == needImage && articleList.size() == 0){
                    onlyImage = true;
                } else{
                    morething = true;
                }
                extraMap.put("onlyForm", onlyForm);
                extraMap.put("onlyArticle", onlyArticle);
                extraMap.put("onlyImage", onlyImage);
                extraMap.put("nothing", nothing);
                extraMap.put("morething", morething);
                followModulePlan.setExtraMap(extraMap);
            }
        }
        return modulePlanList;
    }


    /**
     * 返回没有携带表单 数量统计
     * @param doctorId
     * @param start
     * @param limit
     * @return
     */
    @RpcService
    public List<Map<String, Object>> findMyFormList(String doctorId, Integer start, Integer limit) {
        HealthAssessService healthAssessService = AppContextHolder.getBean("eh.healthAssessService", HealthAssessService.class);
        Map<String, Object> resultMap = healthAssessService.getHistoryListByDoctorId(FollowConstant.APPID_ALL, doctorId, FollowConstant.STRING_EMPTY, Integer.valueOf(FollowConstant.FOLLOW_FORM), start, limit);
        Map<String, Object> bodyMap = (Map) resultMap.get("body");
        List<Map<String, Object>> historyInfoList = MapValueUtil.getList(bodyMap, "historyInfoList");
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);

        List<Map<String, Object>> returnList = new ArrayList<>();
        if (historyInfoList != null && historyInfoList.size() > 0) {
            //处理数据 同一天的数据整合到一起
            Map<String, Object> dateKeyMap = new HashMap<>();
            for (Map<String, Object> historyInfo : historyInfoList) {
                String createTime = MapValueUtil.getString(historyInfo, "createTime");
                historyInfo.put("pName", patientDAO.getPatientNameByMpiId(MapValueUtil.getString(historyInfo, "userId")));
                if (StringUtils.isNotEmpty(createTime)) {
                    Date date = DateConversion.getCurrentDate(createTime, "yyyy-MM-dd");
                    String dateStr = DateConversion.getDateFormatter(date, "yyyy年MM月dd日");
                    String monthStr = DateConversion.getDateFormatter(date, "M月");
                    String dayStr = DateConversion.getDateFormatter(date, "d日");
                    Map<String, Object> dMap = new HashMap<>();
                    dMap.put("monthInfo", monthStr);
                    dMap.put("dayInfo", dayStr);
                    dMap.put("date", date);
                    historyInfo.put("createDate", dateStr);
                    //这里会保留同一日期 最开始保存进去的键值对
                    dateKeyMap.put(dateStr, dMap);
                }else{
                    historyInfo.put("createDate", "");
                }
            }
            Set<Map.Entry<String, Object>> dateEntrySet = dateKeyMap.entrySet();

            List<Map<String, Object>> dateFormList;

            //根据相同的日期进行分组
            for (Map.Entry<String, Object> entry : dateEntrySet) {
                Map<String, Object> dateFormMap = new HashMap<>();
                String key = entry.getKey();
                Map<String, Object> dMap = (Map) entry.getValue();
                dateFormMap.put("dateInfo", dMap);
                dateFormMap.put("date", dMap.get("date"));
                dateFormList = new ArrayList<>();
                for (Map<String, Object> historyInfo : historyInfoList) {
                    if (MapValueUtil.getString(historyInfo, "createDate").equals(key)) {
                        dateFormList.add(historyInfo);
                    }
                }
                dateFormMap.put("formList", dateFormList);
                returnList.add(dateFormMap);
            }
            //根据创建时间倒序
            Collections.sort(returnList, new Comparator<Map<String, Object>>() {
                @Override
                public int compare(Map<String, Object> o1, Map<String, Object> o2) {
                    return ((Date) o2.get("date")).compareTo(((Date) o1.get("date")));
                }
            });
        }

        return returnList;
    }


    /**
     * 返回没有携带表单 数量统计
     * @param doctorId
     * @return
     */
    @RpcService
    public Map<String, Object> findFormListAndModuleList(Integer doctorId) {
        return findFormListAndModuleListWithLimit(doctorId, 5, 3);
    }

    /**
     * 随访首页 获取一定数量的模板和表单限制数 限制数由前端传入
     * 返回没有携带表单 数量统计
     * @param doctorId
     * @param moduleLimit
     * @param formLimit
     * @return
     */
    @RpcService
    public Map<String, Object> findFormListAndModuleListWithLimit(Integer doctorId, Integer moduleLimit, Integer formLimit) {
        if(null == moduleLimit || null == formLimit){
            throw new DAOException(DAOException.VALUE_NEEDED, "moduleLimit and formLimit are needed");
        }
        if(null == doctorId){
            throw new DAOException(DAOException.VALUE_NEEDED, "doctorId is needed");
        }
        Map<String, Object> map = new HashMap<>();
        map.put("moduleTotalNum", findMyModuleList(doctorId, null, null).size());
        map.put("myModuleList", findMyModuleList(doctorId, 0, moduleLimit));
        map.put("myFormList", findMyFormList(String.valueOf(doctorId), 0, formLimit));
        return map;
    }

    /**
     * 返回携带表单 数量统计
     * @param doctorId
     * @param assessType
     * @param fillType
     * @param start
     * @param limit
     * @return
     */
    @RpcService
    public Map<String, Object> findAndCountMyFormList(Integer doctorId, String assessType, String fillType, Date time, Integer start, Integer limit) {
        Date timeBegin = null;
        Date timeEnd = null;
        if(null != time){
            List<Date> list = DateConversion.getStartAndEndDateByMonth(time);
            timeBegin = list.get(0);
            timeEnd = list.get(1);
        }
        List<Map<String, Object>> returnList = new ArrayList<>();
        Map<String, Object> resMap = new HashMap<>();
        HealthAssessService healthAssessService = AppContextHolder.getBean("eh.healthAssessService", HealthAssessService.class);
        Map<String, Object> resultMap = healthAssessService.queryHistoryAssessList(FollowConstant.APPID_ALL, doctorId, FollowConstant.STRING_EMPTY, assessType, fillType, timeBegin, timeEnd, start, limit);
        Map<String, Object> bodyMap = (Map) resultMap.get("body");
        if(null != bodyMap) {

            List<Map<String, Object>> historyInfoList = MapValueUtil.getList(bodyMap, "historyInfoList");
            Integer countNum = MapValueUtil.getInteger(bodyMap, "countNum");
            PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
            if(null == countNum){
                countNum = 0;
            }
            resMap.put("totalNum", countNum);

            if (historyInfoList != null && historyInfoList.size() > 0) {
                //处理数据 同一天的数据整合到一起
                Map<String, Object> dateKeyMap = new HashMap<>();
                for (Map<String, Object> historyInfo : historyInfoList) {
                    String createTime = MapValueUtil.getString(historyInfo, "createTime");
                    historyInfo.put("pName", patientDAO.getPatientNameByMpiId(MapValueUtil.getString(historyInfo, "userId")));
                    if (StringUtils.isNotEmpty(createTime)) {
                        Date date = DateConversion.getCurrentDate(createTime, "yyyy-MM-dd");
                        String dateStr = DateConversion.getDateFormatter(date, "yyyy年MM月dd日");
                        String monthStr = DateConversion.getDateFormatter(date, "M月");
                        String dayStr = DateConversion.getDateFormatter(date, "d日");
                        Map<String, Object> dMap = new HashMap<>();
                        dMap.put("monthInfo", monthStr);
                        dMap.put("dayInfo", dayStr);
                        dMap.put("date", date);
                        historyInfo.put("createDate", dateStr);
                        //这里会保留同一日期 最开始保存进去的键值对
                        dateKeyMap.put(dateStr, dMap);
                    } else {
                        historyInfo.put("createDate", "");
                    }
                }
                Set<Map.Entry<String, Object>> dateEntrySet = dateKeyMap.entrySet();

                List<Map<String, Object>> dateFormList;

                //根据相同的日期进行分组
                for (Map.Entry<String, Object> entry : dateEntrySet) {
                    Map<String, Object> dateFormMap = new HashMap<>();
                    String key = entry.getKey();
                    Map<String, Object> dMap = (Map) entry.getValue();
                    dateFormMap.put("dateInfo", dMap);
                    dateFormMap.put("date", dMap.get("date"));
                    dateFormList = new ArrayList<>();
                    for (Map<String, Object> historyInfo : historyInfoList) {
                        if (MapValueUtil.getString(historyInfo, "createDate").equals(key)) {
                            dateFormList.add(historyInfo);
                        }
                    }
                    dateFormMap.put("formList", dateFormList);
                    returnList.add(dateFormMap);
                }
                //根据创建时间倒序
                Collections.sort(returnList, new Comparator<Map<String, Object>>() {
                    @Override
                    public int compare(Map<String, Object> o1, Map<String, Object> o2) {
                        return ((Date) o2.get("date")).compareTo(((Date) o1.get("date")));
                    }
                });
            }else{
                logger.error("queryHistoryAssessList historyInfoList is null");
            }
        }else{
            logger.error("queryHistoryAssessList bodyMap is null");
        }

        Map<String, Object> numMap = healthAssessService.queryAssessCountInfo(FollowConstant.APPID_ALL, doctorId, FollowConstant.STRING_EMPTY, assessType, fillType);
        if(null != numMap){
            resMap.put("thisMonthNum", MapValueUtil.getInteger(numMap, "thisMonthNum"));
            resMap.put("thisWeekNum", MapValueUtil.getInteger(numMap, "thisWeekNum"));
        }else{
            logger.error("queryAssessCountInfo bodyMap is null");
        }
        resMap.put("list", returnList);
        return resMap;
    }

    /**
     * 携带表单 数量统计
     * @param doctorId
     * @param moduleLimit
     * @param formLimit
     * @return
     */
    @RpcService
    public Map<String, Object> findFormAndModuleWithLimitAndCount(Integer doctorId, Integer moduleLimit, Integer formLimit) {
        if(null == moduleLimit || null == formLimit){
            throw new DAOException(DAOException.VALUE_NEEDED, "moduleLimit and formLimit are needed");
        }
        if(null == doctorId){

            throw new DAOException(DAOException.VALUE_NEEDED, "doctorId is needed");
        }
        FollowChatService followChatService = AppContextHolder.getBean("eh.followChatService", FollowChatService.class);
        FollowChatStatistics statistics = followChatService.statisticsFollow(doctorId);
        Map<String, Object> resMap = getTextByRank(doctorId, statistics);
        Map<String, Object> map = new HashMap<>();
        map.put("moduleTotalNum", findMyModuleList(doctorId, null, null).size());
        map.put("myModuleList", findMyModuleList(doctorId, 0, moduleLimit));
        map.put("myFormList", findAndCountMyFormList(doctorId, FollowConstant.FOLLOW_ALL, "0", null, 0, formLimit));
        map.put("followChatThisMonSum", statistics.getFollowChatThisMonSum());
        map.put("followChatSum", statistics.getFollowChatSum());
        map.put("followChatPatientSum", statistics.getFollowChatPatientSum());
        map.put("followChatRankText", MapValueUtil.getString(resMap, "followChatRankText"));
        map.put("level", MapValueUtil.getInteger(resMap, "level"));
        return map;
    }

    private Map<String, Object> getTextByRank(Integer doctorId, FollowChatStatistics statistics){
        Map<String, Object> map = new HashMap<>();
        String text = "";
        int level;
        Long rank = statistics.getFollowChatRank();
        String percentRep = statistics.getPercentPep();
        Long monthSum = statistics.getFollowChatThisMonSum();
        /*DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Integer organId = doctorDAO.getOrganByDoctorId(doctorId);
        String organName = organDAO.getShortNameById(organId);*/
        if(null == monthSum){
            throw new DAOException(DAOException.VALUE_NEEDED, "followChatThisMonSum is null");
        }
        if(StringUtils.isEmpty(percentRep)){
            throw new DAOException(DAOException.VALUE_NEEDED, "percentRep is null");
        }
        if(null == rank){
            throw new DAOException(DAOException.VALUE_NEEDED, "rank is null");
        }
        Double percent = Double.valueOf(percentRep);
        if(60 <= percent.doubleValue()){
            level = 1;
            text = "本月超越了本院"+percentRep+"%的用户，排名第"+rank;
        }else if(1 <= monthSum){
            level = 2;
            text = "本月超越了本院"+percentRep+"%的用户，再接再厉哦！";
        }else{
            level = 3;
            text = "“良好的开端是成功的一半”—柏拉图";
        }
        map.put("followChatRankText", text);
        map.put("level", level);
        return map;
    }
}
