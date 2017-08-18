package eh.mpi.service.follow;


import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.dao.RelationPatientDAO;
import eh.cdr.dao.DocIndexDAO;
import eh.entity.cdr.DocIndex;
import eh.entity.mpi.FollowPlan;
import eh.entity.mpi.FollowSchedule;
import eh.entity.mpi.RelationDoctor;
import eh.mpi.constant.FollowConstant;
import eh.mpi.dao.FollowPlanDAO;
import eh.mpi.dao.FollowScheduleDAO;
import eh.mpi.dao.RelationDoctorDAO;
import eh.utils.DateConversion;
import eh.wxpay.util.RandomStringGenerator;
import org.apache.commons.lang.StringUtils;
import org.hibernate.StatelessSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

/**
 * @author renzh
 * @date 2016/7/22 0022 上午 10:13
 */
public class FollowAddService {
    private static final Logger log = LoggerFactory.getLogger(FollowAddService.class);

    private FollowPlanDAO followPlanDAO;
    private FollowScheduleDAO followScheduleDAO;
    private RelationDoctorDAO relationDoctorDAO;

    public FollowAddService(){
        followPlanDAO = DAOFactory.getDAO(FollowPlanDAO.class);
        followScheduleDAO = DAOFactory.getDAO(FollowScheduleDAO.class);
        relationDoctorDAO = DAOFactory.getDAO(RelationDoctorDAO.class);
    }

    /**
     * 新增随访（包括日程提醒等...）
     * followPlan 是一个随访计划的计划节点 一个followPlan列表对应一个随访计划 planNodeId是节点的索引 planId是按照某种规则生成的计划索引
     * 可以给多个患者同时创建 相同的随访计划
     * @param followPlans
     * @param patients
     * @return
     */
    @RpcService
    public boolean addFollowPlan(final List<FollowPlan> followPlans, final List<String> patients) {
        if (null == followPlans) {
            throw new DAOException(DAOException.VALUE_NEEDED, "followPlans is needed");
        }
        if (null == patients || 0 == patients.size()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "patient mpiIdList is needed");
        }
        final FollowChatService followChatService = AppContextHolder.getBean("eh.followChatService", FollowChatService.class);
        final FollowQueryService followQueryService = AppContextHolder.getBean("eh.followQueryService", FollowQueryService.class);
        final HibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {
            public void execute(StatelessSession statelessSession) throws Exception {
                List<Object[]> list;
                FollowPlan fp;
                FollowSchedule followSchedule;
                List<Integer> familyDoctorIdList;
                Integer doctorId = followPlans.get(0).getPlanCreator();
                String fromType = followPlans.get(0).getFromType();
                fromType = StringUtils.isEmpty(fromType) ? "1" : fromType;
                int success = 0;
                int patientSize = patients.size();

                //首先群体校验一遍
                for (String patient : patients) {
                    //患者报道和手动创建的随访有数量限制
                    if ("1".equals(fromType) || "3".equals(fromType)) {
                        boolean bl = followQueryService.canAddFollowPlan(patient, doctorId);
                        //医生对一个患者 手动创建未完成的随访计划至多三个 （多个患者同时创建随访时不提示 单个患者创建随访时提示）
                        if (!bl) {
                            throw new DAOException(ErrorCode.SERVICE_ERROR, "一个患者最多只能创建3个随访计划");
                        }
                    }
                }
                for (String patient : patients) {

                    final String planId = generatePlanId();
                    RelationPatientDAO relationPatientDAO = DAOFactory.getDAO(RelationPatientDAO.class);
                    RelationDoctor relationDoctor = relationPatientDAO.getByMpiidAndDoctorId(patient, doctorId);
                    try {
                        for (FollowPlan followPlan : followPlans) {

                            list = DateConversion.getIntervalStartList(followPlan.getStartDate(), followPlan.getEndDate(), 60 * 24 * followPlan.getIntervalDay());
                            followPlan.setPlanId(planId);
                            followPlan.setPlanTitle(DictionaryController.instance().get("eh.mpi.dictionary.PlanType").getText(followPlan.getPlanType()));
                            followPlan.setCreateDate(new Date());
                            followPlan.setLastModify(new Date());
                            if (followPlan.getRemindPatient() == null) followPlan.setRemindPatient(1);
                            if (followPlan.getRemindSelf() == null) followPlan.setRemindSelf(1);
                            if (followPlan.getRemindSign() == null) followPlan.setRemindSign(0);
                            if (followPlan.getFromType() == null) followPlan.setFromType("1");
                            if (followPlan.getAppointRecordId() == null) followPlan.setAppointRecordId(0);
                            if (followPlan.getNeedImage() == null) followPlan.setNeedImage(0);
                            familyDoctorIdList = relationDoctorDAO.findFamilyDoctorId(patient);
                            if (relationDoctor != null) {
                                followPlan.setRelationDoctorId(relationDoctor.getRelationDoctorId());
                                fp = followPlanDAO.addFollowPlan(followPlan);
                            } else {
                                fp = followPlanDAO.addFollowPlan(followPlan);
                            }

                            Integer sendNow = followPlan.getSendNow();
                            for (Object[] fd : list) {
                                Date followDate = (Date) fd[0];
                                followSchedule = new FollowSchedule();
                                followSchedule.setPlanNodeId(fp.getPlanNodeId());
                                followSchedule.setCreateDate(fp.getCreateDate());
                                followSchedule.setFollowDate(followDate);
                                followSchedule.setLastModify(fp.getLastModify());
                                followSchedule.setMpiId(patient);
                                followSchedule.setPlanId(fp.getPlanId());
                                followSchedule.setPlanTitle(fp.getPlanTitle());
                                followSchedule.setPlanType(fp.getPlanType());
                                followSchedule.setReadStatus(0);
                                followSchedule.setRelationDoctorId(fp.getRelationDoctorId());
                                followSchedule.setRemindContent(fp.getRemindContent());
                                followSchedule.setArticleId(followPlan.getArticleId());
                                followSchedule.setFormId(followPlan.getFormId());
                                //app3.8.5 新增字段
                                followSchedule.setNeedImage(followPlan.getNeedImage());
                                followSchedule.setArticleInfo(fp.getArticleInfo());
                                followSchedule.setFormInfo(fp.getFormInfo());
                                if (fp.getAheadUnit() == 2) {
                                    followSchedule.setRemindDate(DateConversion.getDateBFtHour(followDate, fp.getAheadNum() * 24));
                                } else {
                                    followSchedule.setRemindDate(DateConversion.getDateBFtHour(followDate, fp.getAheadNum()));
                                }
                                if (null != sendNow) {
                                    followSchedule.setRemindFlag(sendNow);
                                } else {
                                    followSchedule.setRemindFlag(0);
                                }
                                followSchedule.setScheduleStatus(0);
                                if (fp.getRemindPatient() == 1) {
                                    followSchedule.setDoctorId(doctorId);
                                    followSchedule.setSendType(0);
                                    followScheduleDAO.addFollowSchedule(followSchedule);
                                }
                                if (fp.getRemindSelf() == 1) {
                                    followSchedule.setDoctorId(doctorId);
                                    followSchedule.setSendType(1);
                                    followScheduleDAO.addFollowSchedule(followSchedule);
                                }
                                if (fp.getRemindSign() == 1) {
                                    followSchedule.setSendType(2);
                                    for (Integer familyDoctorId : familyDoctorIdList) {
                                        followSchedule.setDoctorId(familyDoctorId);
                                        followScheduleDAO.addFollowSchedule(followSchedule);
                                    }
                                }

                            }
                            //如果某个计划节点设置了立即发送 就把这个节点的所有日程立即发送
                            if (null != sendNow && 1 == sendNow) {
                                FollowUpdateService followUpdateService = AppContextHolder.getBean("eh.followUpdateService", FollowUpdateService.class);
                                followUpdateService.sendFollowMsgRightNow(fp.getPlanNodeId());
                            }
                        }
                        success++;
                    } catch (Exception e) {
                        log.error("添加随访失败 error={}", e.getMessage());
                    }
                    try {
                        //新增随访计划的时候就先把环信群组创建好 防止后面创建的时候因为线程问题重复创建
                        followChatService.createFollowChat(patient, doctorId, FollowConstant.FOLLOWCHAT_HASEND, FollowConstant.CHATROLE_SYS, null);
                    } catch (Exception e) {
                        log.error("创建群组失败 error={}", e.getMessage());
                    }
                }
                if(success == patientSize) {
                    setResult(true);
                }else{
                    int fail = patientSize - success;
                    if(1 == patientSize) {
                        throw new DAOException(ErrorCode.SERVICE_ERROR, "保存失败");
                    }else{
                        throw new DAOException(ErrorCode.SERVICE_ERROR, "有"+fail+"个患者保存失败");
                    }
                }
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
        return action.getResult();
    }

    /**
     * 时间戳加4位随机字符串
     * @return
     */
    public String generatePlanId(){
        long time = System.currentTimeMillis();
        String t = String.valueOf(time/1000);
        String s = RandomStringGenerator.getRandomStringByLength(4);
        return t+s;
    }

    /**
     * 患者端上传图片 成功后保存图片Id列表 并且保存到电子病历里面
     * @param followScheduleId
     * @param fileId
     * @return
     */
    @RpcService
    public boolean uploadFollowImage(Integer followScheduleId, String fileId){
        if(StringUtils.isEmpty(fileId)){
            throw new DAOException(DAOException.VALUE_NEEDED, "fileId is needed");
        }
        FollowSchedule followSchedule = followScheduleDAO.get(followScheduleId);
        followSchedule.setFileId(fileId);
        followScheduleDAO.save(followSchedule);
        DocIndexDAO docIndexDAO = DAOFactory.getDAO(DocIndexDAO.class);
        DocIndex docIndex = new DocIndex();
        //// TODO: 2017/5/31 0031
        docIndexDAO.saveDocIndex(docIndex);
        return true;
    }
}
