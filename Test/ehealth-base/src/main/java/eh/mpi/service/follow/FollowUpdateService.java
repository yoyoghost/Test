package eh.mpi.service.follow;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import ctd.mvc.weixin.entity.OAuthWeixinMP;
import ctd.mvc.weixin.entity.OAuthWeixinMPDAO;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.constant.SystemConstant;
import eh.base.dao.DoctorDAO;
import eh.bus.constant.MsgTypeEnum;
import eh.bus.dao.AppointRecordDAO;
import eh.entity.base.Doctor;
import eh.entity.bus.AppointRecord;
import eh.entity.mpi.FollowAssess;
import eh.entity.mpi.FollowChat;
import eh.entity.mpi.FollowPlan;
import eh.entity.mpi.FollowSchedule;
import eh.mpi.constant.FollowConstant;
import eh.mpi.dao.FollowPlanDAO;
import eh.mpi.dao.FollowScheduleDAO;
import eh.msg.dao.MassRootDAO;
import eh.push.SmsPushService;
import eh.utils.LocalStringUtil;
import eh.utils.MapValueUtil;
import eh.utils.params.ParamUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.util.*;

/**
 * @author renzh
 * @date 2016/7/22 0022 上午 10:13
 */
public class FollowUpdateService {

    private static final Logger logger = LoggerFactory.getLogger(FollowUpdateService.class);

    private FollowPlanDAO followPlanDAO;
    private FollowScheduleDAO followScheduleDAO;


    public FollowUpdateService(){
        followPlanDAO = DAOFactory.getDAO(FollowPlanDAO.class);
        followScheduleDAO = DAOFactory.getDAO(FollowScheduleDAO.class);
    }

    /**
     * 随访日程消息推送 定时发送
     * @return
     */
    @RpcService
    public List<FollowSchedule> followMsgPush(){
        List<FollowSchedule> followScheduleList = followScheduleDAO.findShouldPush();
        dealWithScheduleList(followScheduleList);
        return followScheduleList;
    }

    /**
     * 设置立即发送的计划节点 生成的日程立即发送
     * zhongzx
     * @param planNodeId
     */
    public void sendFollowMsgRightNow(Integer planNodeId){
        List<FollowSchedule> followScheduleList = followScheduleDAO.findByPlanNodeId(planNodeId);
        dealWithScheduleList(followScheduleList);
    }

    public void dealWithScheduleList(List<FollowSchedule> followScheduleList){
        if(null != followScheduleList && followScheduleList.size() > 0){
            DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
            SmsPushService pushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
            FollowChatService followChatService = AppContextHolder.getBean("eh.followChatService", FollowChatService.class);
            for (FollowSchedule followSchedule : followScheduleList) {
                try {
                    /**
                     * 获取各种参数值
                     */
                    Integer planNodeId = followSchedule.getPlanNodeId();
                    Integer doctorId = followSchedule.getDoctorId();
                    String mpiId = followSchedule.getMpiId();
                    Integer organ = doctorDAO.getOrganByDoctorId(doctorId);
                    int id = followSchedule.getId();
                    FollowPlan followPlan = followPlanDAO.getByPlanNodeId(planNodeId);
                    Integer appointRecordId = followPlan.getAppointRecordId();
                    /**
                     * 如果是预约转诊创建的随访计划提醒 在提醒前确认一遍预约转诊是否成功
                     * 如果是取消状态的 不发送随访提醒
                     */
                    FollowChat followChat;
                    if (null != appointRecordId && !appointRecordId.equals(0)) {
                        AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
                        AppointRecord appointRecord = appointRecordDAO.getByAppointRecordId(appointRecordId);
                        Integer appointStatus = appointRecord.getAppointStatus();
                        /**
                         * 如果是预约业务 取预约业务的机构号
                         */
                        organ = appointRecord.getOrganId();
                        /**
                         * 2017-07-11 过滤上海六院的预约随访提醒
                         */
                        if (organ.equals(1000899)) {
                            continue;
                        }
                        if (null != appointStatus && appointStatus.equals(2)) {
                            continue;
                        }
                        followChat = followChatService.createFollowChat(mpiId, doctorId, FollowConstant.FOLLOWCHAT_HASEND, FollowConstant.CHATROLE_SYS, null);
                    }else{
                        followChat = followChatService.createFollowChat(mpiId, doctorId, FollowConstant.FOLLOWCHAT_HASEND, FollowConstant.CHATROLE_SYS, new Date());
                    }
                    /**
                     * 发送各种消息到随访会话里
                     */
                    addFollowMsgToChat(followSchedule, doctorId, mpiId, followChat);
                    /**
                     * 发送消息推送
                     */
                    pushService.pushMsgData2Ons(id, organ, "FollowMsg", "FollowMsg", null);
                }catch (Exception e){
                    logger.error("定时发送失败 followScheduleId=[{}], error=[{}]", followSchedule.getId(), e.getMessage());
                    continue;
                }
            }
        }
    }

    private String packUrl(String id, Integer doctorId){
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Doctor doctor = doctorDAO.getByDoctorId(doctorId);
        String docName = StringUtils.isEmpty(doctor.getName())?"":doctor.getName();
        Integer organId = null==doctor.getOrgan()?0:doctor.getOrgan();
        Integer departId = null==doctor.getDepartment()?0:doctor.getDepartment();
        StringBuilder beginUrl = new StringBuilder(ParamUtils.getParam("ASSESS_BEGIN_URL"));
        beginUrl.append("?appId=0&appType=4&hospitalId="+organId+"&depId="+departId+"&assessId="+id+"&shareType=0&conType=3&docName="+docName);
        return beginUrl.toString();
    }

    /**
     * 添加各种随访信息到随访会话里
     * @param followSchedule
     * @param doctorId
     * @param mpiId
     */
    public void addFollowMsgToChat(FollowSchedule followSchedule, Integer doctorId, String mpiId, FollowChat followChat){
        Integer followScheduleId = followSchedule.getId();
        String formId = followSchedule.getFormId();
        String formInfo = followSchedule.getFormInfo();
        String articleInfo = followSchedule.getArticleInfo();
        String articleId = followSchedule.getArticleId();
        String remindContent = followSchedule.getRemindContent();
        Integer needImage = followSchedule.getNeedImage();
        Integer sendType = followSchedule.getSendType();
        if(null != sendType && 0 == sendType) {
            FollowChatService followChatService = AppContextHolder.getBean("eh.followChatService", FollowChatService.class);
            HealthAssessService healthAssessService = AppContextHolder.getBean("eh.healthAssessService", HealthAssessService.class);
            if (StringUtils.isNotEmpty(remindContent)) {
                followChatService.addMsg(SystemConstant.ROLES_DOCTOR, SystemConstant.ROLES_PATIENT, mpiId,
                        doctorId, MsgTypeEnum.FOLLOW_SCHEDULE, followScheduleId, remindContent, followChat);
            }
            /**
             * 兼容以前版本 先判断新的表单字段是否有值 如果没有值 再判断之前的字段
             */
            if (StringUtils.isNotEmpty(formInfo)) {
                FollowAssess followAssess = new FollowAssess();
                String msg;
                List<Map<String, Object>> formInfoList = JSONUtils.parse(formInfo, List.class);
                for (Map form : formInfoList) {
                    String assessName = MapValueUtil.getString(form, "title");
                    String id = MapValueUtil.getString(form, "id");
                    String url = packUrl(id, doctorId);
                    /**
                     * 增加一个获取默认参数的步骤
                     */
                    Map<String, Object> param = new HashMap<>();
                    param.put("mpiId", mpiId);
                    param.put("doctorId", doctorId);
                    param.put("url", url);
                    param.put("assessId", id);
                    url = healthAssessService.getDefaultAssessParameters(param);

                    followAssess.setUrl(url);
                    followAssess.setTitle(assessName);
                    followAssess.setFromFlag("");
                    msg = JSON.toJSONString(followAssess);
                    followChatService.addMsg(SystemConstant.ROLES_DOCTOR, SystemConstant.ROLES_PATIENT, mpiId,
                            doctorId, MsgTypeEnum.FOLLOW_ASSESS, followScheduleId, msg, followChat);
                }
            } else if (StringUtils.isNotEmpty(formId)) {
                FollowAssess followAssess = new FollowAssess();
                Map assessMap = healthAssessService.getAssessConclusionInfoByAssessId(formId);
                if(null == assessMap){
                    throw new DAOException(ErrorCode.SERVICE_ERROR, "assessMap is null");
                }
                Map bodyMap = (Map) assessMap.get("body");
                if(null == bodyMap){
                    throw new DAOException(ErrorCode.SERVICE_ERROR, "bodyMap is null");
                }
                String assessName = MapValueUtil.getString(bodyMap, "assessName");
                String url = packUrl(formId, doctorId);
                /**
                 * 增加一个获取默认参数的步骤
                 */
                Map<String, Object> param = new HashMap<>();
                param.put("mpiId", mpiId);
                param.put("doctorId", doctorId);
                param.put("url", url);
                param.put("assessId", formId);
                url = healthAssessService.getDefaultAssessParameters(param);
                followAssess.setUrl(url);
                followAssess.setTitle(assessName);
                followAssess.setFromFlag("");
                String msg = JSON.toJSONString(followAssess);
                followChatService.addMsg(SystemConstant.ROLES_DOCTOR, SystemConstant.ROLES_PATIENT, mpiId,
                        doctorId, MsgTypeEnum.FOLLOW_ASSESS, followScheduleId, msg, followChat);
            }
            /**
             * 兼容以前版本 先判断新的文章字段是否有值 如果没有值 再判断之前的字段
             */
            if (StringUtils.isNotEmpty(articleInfo)) {
                List<Map<String, Object>> articleInfoList = JSONUtils.parse(articleInfo, List.class);
                for (Map article : articleInfoList) {
                    String id = MapValueUtil.getString(article, "id");
                    String msg = healthAssessService.getInformationById(Integer.valueOf(id));
                    followChatService.addMsg(SystemConstant.ROLES_DOCTOR, SystemConstant.ROLES_PATIENT, mpiId,
                            doctorId, MsgTypeEnum.FOLLOW_ARTICLE, followScheduleId, msg, followChat);
                }
            } else if (StringUtils.isNotEmpty(articleId)) {
                String msg = healthAssessService.getInformationById(Integer.valueOf(articleId));
                followChatService.addMsg(SystemConstant.ROLES_DOCTOR, SystemConstant.ROLES_PATIENT, mpiId,
                        doctorId, MsgTypeEnum.FOLLOW_ARTICLE, followScheduleId, msg, followChat);
            }
            if (null != needImage && FollowConstant.NEEDIMAGE_YES.equals(needImage)) {
                String msg = "请点击此处上传图片";
                followChatService.addMsg(SystemConstant.ROLES_DOCTOR, SystemConstant.ROLES_PATIENT, mpiId,
                        doctorId, MsgTypeEnum.FOLLOW_NEED_IMAGE, followScheduleId, msg, followChat);
            }
        }
    }

    /**
     * 根据日程id修改状态（取消提醒）
     * @param id
     */
    @RpcService
    public boolean cancelScheduleStatusById(int id){
        FollowSchedule followSchedule = followScheduleDAO.get(id);
        if(followSchedule.getScheduleStatus()==1){
            throw new DAOException(609, "该日程已提醒，无法取消。");
        }else {
            followScheduleDAO.updateScheduleStatusGoneById(id);
        }
        return true;
    }

    /**
     * 取消预约时删除预约生成的随访计划
     * @param appointRecordId
     * @return
     */
    @RpcService
    public boolean deleteByAppointRecordId(Integer appointRecordId){
        boolean flag = true;
        List<FollowPlan> followPlanList = followPlanDAO.findByAppointRecordId(appointRecordId);
        for(FollowPlan followPlan:followPlanList){
            flag = deletePlan(followPlan.getPlanId());
        }
        if(!flag){
            logger.error("after cancelAppoint deleteByAppointRecordId failed and appointRecordId is [{}]", appointRecordId);
        }
        return flag;
    }

    /**
     * 根据计划Id删除整个随访计划（包括日程）
     * @param planId
     * @return
     */
    @RpcService
    public boolean deletePlan(String planId){
        try {
            followPlanDAO.deleteByPlanId(planId);
            followScheduleDAO.deleteByPlanId(planId);
        }catch (Exception e){
            logger.error("deletePlan()取消日程失败"+planId);
            return false;
        }
        return true;
    }

    /**
     * 删除对应医生和患者下的所有日程
     * @param doctorId
     * @param mpiId
     * @return
     */
    @RpcService
    public boolean deleteAllPlanByDocAndPa(Integer doctorId,String mpiId){
        try {
            List<String> planIdList = followPlanDAO.findByDocAndPa(doctorId,mpiId);
            for(String planId:planIdList){
                followPlanDAO.deleteByPlanId(planId);
                followScheduleDAO.deleteByPlanId(planId);
            }
        }catch (Exception e){
            return false;
        }
        return true;
    }

    /**
     * 根据id查日程
     * @param id
     * @return
     */
    @RpcService
    public FollowSchedule getById(int id){
        FollowSchedule followSchedule = followScheduleDAO.get(id);
        return followSchedule;
    }

    /**
     * 改日程状态为已提醒
     * @param id
     */
    @RpcService
    public void overScheduleStatusById(int id){
        followScheduleDAO.updateScheduleStatusOverById(id);
    }

    /**
     * 查日程创建人
     * @param planNodeId
     * @return
     */
    @RpcService
    public int getPlanCreator(int planNodeId){
        return followPlanDAO.get(planNodeId).getPlanCreator();
    }

    /**
     * 日程设为已读
     * @param id
     */
    @RpcService
    public boolean updateAfterRead(int id){
        try {
            followScheduleDAO.updateAfterReadP(id);
            return true;
        } catch (Exception e) {
            logger.error(LocalStringUtil.format("error, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace())));
            return false;
        }
    }

    /**
     * 随访 医生群发短信
     * @param doctorId
     * @param date
     * @param msg
     */
    @RpcService
    public void sendMessageFs(int doctorId, Date date, String msg){
        MassRootDAO massRootDAO = DAOFactory.getDAO(MassRootDAO.class);
        List<FollowSchedule> followScheduleList = followScheduleDAO.findAllScheduleByDate(doctorId,date);
        List<String> mpiIdList = new ArrayList<String>();
        for (FollowSchedule followSchedule : followScheduleList){
            String mpiId = followSchedule.getMpiId();
            mpiIdList.add(mpiId);
        }
        massRootDAO.sendMassMsgToPatient(doctorId,msg,mpiIdList);
    }

    /**
     * 点击查看日程把当前医生当天所有日程设为已读
     * @param doctorId
     * @return
     */
    @RpcService
    public boolean setCreaterHasRead(int doctorId){
        try {
            followScheduleDAO.updateAfterReadC(doctorId);
            return true;
        } catch (Exception e) {
            logger.error(LocalStringUtil.format("error, errorMessage[{}], stackTrace[{}]", e.getMessage(), JSONObject.toJSONString(e.getStackTrace())));
            return false;
        }
    }

    /**
     * 根据节点Id获取随访计划
     * @param planNodeId
     * @return
     */
    @RpcService
    public FollowPlan getByPlanNodeId(Integer planNodeId){
       return followPlanDAO.getByPlanNodeId(planNodeId);
    }

    /**
     * 判断患者是否关注公众号
     *
     * @param urt
     * @return
     */
    @RpcService
    public boolean isOrNotFocus(Integer urt) {
        Boolean flag = false;
        List<OAuthWeixinMP> oAuthWeixinMPList = DAOFactory.getDAO(OAuthWeixinMPDAO.class).findByUrt(urt);
        if (!CollectionUtils.isEmpty(oAuthWeixinMPList)) {
            for (OAuthWeixinMP oAuthWeixinMP : oAuthWeixinMPList) {
                if (StringUtils.equals(oAuthWeixinMP.getSubscribe(), "1")) {
                    flag = true;
                }
            }
        }
        return flag;
    }
}
