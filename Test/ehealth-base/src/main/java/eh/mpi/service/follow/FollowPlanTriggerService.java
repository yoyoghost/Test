package eh.mpi.service.follow;

import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.dao.*;
import eh.entity.base.Doctor;
import eh.entity.base.Organ;
import eh.entity.mpi.*;
import eh.mpi.constant.FollowConstant;
import eh.mpi.dao.FollowModulePlanDAO;
import eh.mpi.dao.FollowPlanDAO;
import eh.mpi.dao.FollowPlanTriggerDAO;
import eh.mpi.dao.FollowPlanTriggerRuleDAO;
import eh.utils.ValidateUtil;
import eh.utils.params.ParamUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * Created by zhangyq on 2017/4/24.
 */
public class FollowPlanTriggerService {

    private static final Logger logger = LoggerFactory.getLogger(FollowPlanTriggerService.class);

    private DoctorDAO doctorDAO;
    private OrganDAO organDAO;
    private DoctorGroupDAO doctorGroupDAO;
    private FollowPlanTriggerDAO followPlanTriggerDAO;
    private RelationPatientDAO relationPatientDAO;

    public FollowPlanTriggerService() {
        doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        organDAO = DAOFactory.getDAO(OrganDAO.class);
        followPlanTriggerDAO = DAOFactory.getDAO(FollowPlanTriggerDAO.class);
        doctorGroupDAO = DAOFactory.getDAO(DoctorGroupDAO.class);
        relationPatientDAO = DAOFactory.getDAO(RelationPatientDAO.class);
    }

    @RpcService
    public Map autoFollow(Integer doctorId,String triggerEvent){
        List<FollowPlanTrigger> followPlanTriggers = followPlanTriggerDAO.findByDoctorAndEvent(doctorId,triggerEvent);
        Map map = getTargetDoctorInfo(doctorId,triggerEvent);
        if (ValidateUtil.notBlankList(followPlanTriggers)){
            map.put("isOpen", Boolean.TRUE);
        }else {
            map.put("isOpen",Boolean.FALSE);
            map.put("disPlay",Boolean.FALSE);
        }
        return map;
    }

    public boolean canReport(Integer doctorId,String triggerEvent){
        List<FollowPlanTrigger> followPlanTriggers = followPlanTriggerDAO.findByDoctorAndEvent(doctorId,triggerEvent);
        boolean flag = false;
        if (ValidateUtil.notBlankList(followPlanTriggers)){
            flag = true;
        }
        return flag;
    }

    @RpcService
    public boolean addFollowByPatientReport(Integer triggerId, List<String> mpiList, Integer doctorId, Date startDate , Integer bussId) {
        if (bussId != null) {
            //防止预诊创建重复计划
            FollowPlanDAO followPlanDAO = DAOFactory.getDAO(FollowPlanDAO.class);
            List<FollowPlan> list = followPlanDAO.findByAppointRecordIdAndFromType(bussId, "4");
            if (null != list && list.size() > 0) {
                return true;
            }
        }
        FollowPlanTrigger trigger = followPlanTriggerDAO.get(triggerId);
        if (trigger==null){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "预诊配置不存在");
        }
        Integer moduleId = trigger.getFollowModuleId();
        String  event = trigger.getTriggerEvent();
        doctorId = getTeamLeader(doctorId);
        boolean flag = createFollowPlanByModule(moduleId, mpiList, doctorId, startDate,bussId,event);
        /**
         * 现在是根据配置来 获取关注和添加标签
         */
        Integer autoRelated = trigger.getAutoRelated();
        //自动关注
        if (FollowConstant.AutoRelated_Yes == autoRelated) {
            String autoRelationLabels = trigger.getAutoRelationLabels();

            RelationDoctor relationPatient = new RelationDoctor();
            relationPatient.setDoctorId(doctorId);
            relationPatient.setMpiId(mpiList.get(0));
            Integer relationPatientId = relationPatientDAO.addRelationPatientReturnId(relationPatient);
            // TODO: 2017/6/8 0008 添加标签
            RelationLabelDAO relationLabelDAO = DAOFactory.getDAO(RelationLabelDAO.class);
            //添加标签
            if(!StringUtils.isEmpty(autoRelationLabels)){
                List<String> labels = Arrays.asList(autoRelationLabels.split("\\|"));
                relationLabelDAO.addRelationLabel(relationPatientId, labels);
            }

        }
        return flag;
    }

    /**
     * 根据模板Id 患者Id  医生Id 开始日期 生成随访计划
     * @param moduleId
     * @param mpiList
     * @param doctorId
     * @param startDate
     * @return
     */
    public boolean createFollowPlanByModule(Integer moduleId, List<String> mpiList, Integer doctorId, Date startDate, Integer bussId,String event) {

        FollowModulePlanDAO followModuleplanDAO = DAOFactory.getDAO(FollowModulePlanDAO.class);
        List<FollowModulePlan> plans = followModuleplanDAO.findByMid(moduleId);
        if(ValidateUtil.blankList(plans)){
            throw new DAOException(ErrorCode.SERVICE_ERROR, "配置的随访模板已被删除");
        }
        FollowAddService followAddService = AppContextHolder.getBean("followAddService", FollowAddService.class);
        Date endDate;
        FollowPlan followPlan;
        List<FollowPlan> followPlans = new ArrayList<>();
        Calendar c = Calendar.getInstance();
        for (FollowModulePlan followModulePlan : plans) {
            c.setTime(startDate);
            Integer intervalDayUnit = followModulePlan.getIntervalDayUnit();
            Integer intervalDay = followModulePlan.getIntervalDay();
            Integer intervalNum = followModulePlan.getIntervalNum();
            Integer intervalUnit = followModulePlan.getIntervalUnit();
            /**
             * 此处是为了前后台统一
             * week = 7
             * month = 30
             * year = 365
             */
            switch (intervalDayUnit) {
                case FollowConstant.Calendar_DAY:
                    c.add(Calendar.DATE, intervalDay);
                    break;
                case FollowConstant.Calendar_WEEK:
                    c.add(Calendar.DATE, intervalDay * 7);
                    break;
                case FollowConstant.Calendar_MONTH:
                    c.add(Calendar.MONTH, intervalDay * 30);
                    break;
                case FollowConstant.Calendar_YEAR:
                    c.add(Calendar.YEAR, intervalDay * 365);
                    break;
                default:
                    logger.error("intervalDayUnit=[{}] is not in FollowConstant", intervalDayUnit);
                    break;
            }
            endDate = c.getTime();
            followPlan = new FollowPlan();
            followPlan.setStartDate(startDate);
            followPlan.setEndDate(endDate);
            followPlan.setPlanCreator(doctorId);
            //患者报道所创建的计划的来源设为3
            followPlan.setFromType(getPlanFromType(event));
            followPlan.setModuleId(moduleId);
            followPlan.setPlanType(followModulePlan.getPlanType());
            followPlan.setIntervalNum(intervalNum);
            followPlan.setIntervalUnit(intervalUnit);
            Boolean remindSign = followModulePlan.getRemindSign();
            Boolean remindSelf = followModulePlan.getRemindSelf();
            Boolean remindPatient = followModulePlan.getRemindPatient();
            /**
             * followPlan的intervalDay 是间隔周期转换的天数 不是followModulePlan的intervalDay
             */
            //followPlan.setIntervalDay(followModulePlan.getIntervalDay());
            followPlan.setIntervalDay(intervalNumTransferToDay(intervalNum, intervalUnit));
            followPlan.setRemindSign(null == remindSign ? 0 : (remindSign ? 1 : 0));
            followPlan.setRemindSelf(null == remindSelf ? 0 : (remindSelf ? 1 : 0));
            followPlan.setRemindPatient( null == remindPatient ? 1 : (remindPatient ? 1 : 0));
            followPlan.setAheadNum(followModulePlan.getAheadNum());
            followPlan.setAheadUnit(followModulePlan.getAheadUnit());
            followPlan.setRemindContent(followModulePlan.getContent());
            followPlan.setFormId(followModulePlan.getFormId());
            followPlan.setArticleId(followModulePlan.getArticleId());
            followPlan.setSendNow(followModulePlan.getSendNow());
            //app3.8.5 新增字段
            followPlan.setFormInfo(followModulePlan.getFormInfo());
            followPlan.setArticleInfo(followModulePlan.getArticleInfo());
            followPlan.setNeedImage(followModulePlan.getNeedImage());
            followPlan.setAppointRecordId(bussId);
            followPlans.add(followPlan);
            startDate = endDate;
        }

        return followAddService.addFollowPlan(followPlans, mpiList);
    }

    /**
     * 患者报道 报道信息
     * @param doctorId
     * @return
     */
    @RpcService
    public Map getTargetDoctorInfo(Integer doctorId,String triggerEvent) {
        Map<String, Object> info = new HashMap<>();
        Doctor doctor = doctorDAO.getByDoctorId(doctorId);
        Organ organ = organDAO.getByOrganId(doctor.getOrgan());
        FollowPlanTriggerRuleDAO followPlanTriggerRuleDAO = DAOFactory.getDAO(FollowPlanTriggerRuleDAO.class);
        //某个业务的触发器列表
        List<FollowPlanTrigger> followPlanTriggers = followPlanTriggerDAO.findByDoctorAndEvent(doctorId,triggerEvent);
        boolean haveDefaultTrigger = false;
        Integer defaultTriggerId = null;
        if(null != followPlanTriggers && followPlanTriggers.size() > 0){
            List<Map<String, Object>> triggerList = new ArrayList<>();
            for(FollowPlanTrigger followPlanTrigger:followPlanTriggers) {
                Map<String, Object> triggerInfo = new HashMap<>();
                Integer triggerId = followPlanTrigger.getTriggerId();
                //每个触发器的规则列表
                List<FollowPlanTriggerRule> ruleList = followPlanTriggerRuleDAO.findByTriggerId(followPlanTrigger.getTriggerId());
                //触发器信息 重新组装
                /**
                 * 判断默认触发器
                 */
                if(ValidateUtil.blankList(ruleList)){
                    defaultTriggerId = triggerId;
                    info.put("defaultTriggerId", defaultTriggerId);
                    haveDefaultTrigger = true;
                    continue;
                }
                StringBuilder ruleString = new StringBuilder();
                for (FollowPlanTriggerRule rule : ruleList) {
                    ruleString.append(rule.getRefs() + " ");
                }

                triggerInfo.put("ruleString", ruleString.toString());
                triggerInfo.put("triggerId", triggerId);
                triggerList.add(triggerInfo);
            }
            //有条件的触发器大于1的时候要显示 以上都不符合
            if(triggerList.size() >= 1){
                Map<String, Object> map = new HashMap<>();
                map.put("ruleString", ParamUtils.getParam("FOLLOW_TRIGGER_RULE_TEXT", ""));
                triggerList.add(map);
                info.put("disPlay",Boolean.TRUE);
            }else {
                info.put("disPlay",Boolean.FALSE);
            }
            info.put("haveDefaultTrigger", haveDefaultTrigger);
            info.put("tipText", followPlanTriggers.get(0).getTipText());
            info.put("triggerList", triggerList);
        }
        if (FollowConstant.TRIGGEREVENT_SCANCODE.equals(triggerEvent)) {
            String proTitleText = "";
            String professionText = "";
            try {
                proTitleText = DictionaryController.instance()
                        .get("eh.base.dictionary.ProTitle")
                        .getText(doctor.getProTitle());
            } catch (ControllerException e) {
                logger.error("获取职称字典出错");
            }
            try {
                professionText = DictionaryController.instance()
                        .get("eh.base.dictionary.Profession")
                        .getText(doctor.getProfession());
            } catch (ControllerException e) {
                logger.error("获取专科字典出错");
            }
            info.put("name", doctor.getName());
            info.put("proTitle", proTitleText);
            info.put("organName", organ.getShortName());
            info.put("dphoto", doctor.getPhoto());
            info.put("profession", professionText);
            info.put("gender", doctor.getGender());
            info.put("isTeam", doctor.getTeams() ? 1 : 0);
        }
        return info;
    }





    @RpcService
    public FollowPlanTrigger saveOneTrigger(FollowPlanTrigger trigger,List<FollowPlanTriggerRule> list) {
        if (trigger == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, " FollowPlanTrigger is require");
        }
        if (trigger.getTargetDoctor() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, " FollowPlanTrigger.targetDoctor is require");
        }
        if (trigger.getTriggerEvent() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, " FollowPlanTrigger.triggerEvent is require");
        }
        if (trigger.getFollowModuleId() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, " FollowPlanTrigger.followModuleId is require");
        }
        if (StringUtils.isEmpty(trigger.getTipText())) {
            throw new DAOException(DAOException.VALUE_NEEDED, " FollowPlanTrigger.tipText is require");
        }

        if(list==null){
            trigger = followPlanTriggerDAO.save(trigger);
        }else{

            Map<String,FollowPlanTriggerRule> map = new HashMap<String,FollowPlanTriggerRule>();
            for(FollowPlanTriggerRule rule:list){
                 String type = rule.getFact()+"#"+rule.getRefs();
                 if(map.get(type)!=null){
                     throw new DAOException(DAOException.VALUE_NEEDED,"条件重复");
                 }else{
                     map.put(type,rule);
                 }
            }
            trigger = followPlanTriggerDAO.save(trigger);
            List<FollowPlanTriggerRule> rules = new ArrayList<FollowPlanTriggerRule>();
            FollowPlanTriggerRuleDAO followPlanTriggerRuleDAO = DAOFactory.getDAO(FollowPlanTriggerRuleDAO.class);
            for(FollowPlanTriggerRule rule:list){
                rule.setTriggerId(trigger.getTriggerId());
                rules.add(followPlanTriggerRuleDAO.save(rule));
            }
            trigger.setRuleList(rules);
        }
        return trigger;
    }
    @RpcService
    public FollowPlanTrigger getById(int id){
        return followPlanTriggerDAO.get(id);
    }
    @RpcService
    public void deleteOneTrigger(Integer id) {
        FollowPlanTrigger trigger = followPlanTriggerDAO.get(id);
        if (trigger==null){
            throw new DAOException("id is not exist");
        }
        FollowPlanTriggerRuleDAO followPlanTriggerRuleDAO = DAOFactory.getDAO(FollowPlanTriggerRuleDAO.class);
        followPlanTriggerDAO.remove(id);
        followPlanTriggerRuleDAO.deleteByTriggerId(id);
    }
    @RpcService
    public List<FollowPlanTrigger> findTriggers(FollowPlanTrigger trigger,int start, int limit){
        return followPlanTriggerDAO.findTriggers(trigger,start,limit);
    }

    /**
     * 把间隔周期转化成 天数为单位的值
     * @return
     */
    public Integer intervalNumTransferToDay(Integer intervalNum, Integer intervalUnit){
        if(null == intervalNum || null == intervalUnit){
            throw new DAOException(DAOException.VALUE_NEEDED, "intervalNum and intervalUnit are needed");
        }
        switch (intervalUnit){
            case FollowConstant.Calendar_DAY:
                return intervalNum;
            case FollowConstant.Calendar_WEEK:
                return intervalNum*7;
            case FollowConstant.Calendar_MONTH:
                return intervalNum*30;
            case FollowConstant.Calendar_YEAR:
                return intervalNum*365;
            default:
                logger.error("intervalDayUnit=[{}] is not in FollowConstant", intervalUnit);
                return null;
        }
    }

    /**
     * 判断是否团队如果是团队返回负责人医生doctorId
     * @param doctorId
     * @return
     */
    private Integer getTeamLeader(Integer doctorId){
        Doctor doctor = doctorDAO.getByDoctorId(doctorId);
        if (doctor != null && doctor.getTeams()) {
            Integer leader = doctorGroupDAO.getLeaderByDoctorId(doctorId);
            if(null != leader) {
                doctorId = leader;
            }
        }
        return doctorId;
    }

    private String getPlanFromType(String event){
        switch (event){
            case "scanDoctorQrCode":
                return FollowConstant.FROMTYPE_REPORT;
            case "doctorAppoint":
                return FollowConstant.FROMTYPE_PREAPPOINT;
            case "patientAppoint":
                return FollowConstant.FROMTYPE_PREAPPOINT;
            case "outpatientTransfer":
                return FollowConstant.FROMTYPE_PREAPPOINT;
            case "remoteOutpatientTransfer":
                return FollowConstant.FROMTYPE_PREAPPOINT;
            case "remoteDoctorAppoint":
                return FollowConstant.FROMTYPE_PREAPPOINT;
            case "specialPatientAppoint":
                return FollowConstant.FROMTYPE_PREAPPOINT;
            default:
                return null;
        }
    }

}
