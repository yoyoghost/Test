package eh.bus.dao;

import com.google.common.collect.Maps;
import ctd.controller.exception.ControllerException;
import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.base.constant.BussTypeConstant;
import eh.base.constant.ErrorCode;
import eh.base.dao.DoctorAccountDAO;
import eh.base.dao.DoctorDAO;
import eh.base.dao.DoctorGroupDAO;
import eh.bus.asyndobuss.bean.BussFinishEvent;
import eh.bus.asyndobuss.service.AsynDoBussService;
import eh.bus.constant.ConsultConstant;
import eh.bus.constant.MeetClinicConstant;
import eh.bus.constant.MsgTypeEnum;
import eh.bus.service.consult.ConsultMessageService;
import eh.bus.service.meetclinic.MeetClinicPushService;
import eh.entity.base.Doctor;
import eh.entity.base.DoctorGroup;
import eh.entity.bus.MeetClinic;
import eh.entity.bus.MeetClinicReportTable;
import eh.entity.bus.MeetClinicResult;
import eh.entity.mpi.Patient;
import eh.mpi.dao.PatientDAO;
import eh.msg.dao.GroupDAO;
import eh.msg.service.EasemobIMService;
import eh.utils.LocalStringUtil;
import eh.wxpay.util.Util;
import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.StatelessSession;
import org.springframework.util.StringUtils;

import java.util.*;

public abstract class EndMeetClinicDAO extends
        HibernateSupportDelegateDAO<MeetClinicResult> {
    public static final Logger log = Logger.getLogger(EndMeetClinicDAO.class);

    public EndMeetClinicDAO() {
        super();
        this.setEntityName(MeetClinicResult.class.getName());
        this.setKeyField("meetClinicResultId");
    }

    /**
     * 申请医生会诊结束服务
     *
     * @param meetClinicId
     * @param cancelCause
     * @return
     * @author LF
     */
    @RpcService
    public Boolean endMeetClinicByRequest(final Integer meetClinicId,
                                          final String cancelCause) {
        log.info("申请医生会诊结束服务:meetClinicId=" + meetClinicId + "; cancelCause="
                + cancelCause);

        if (meetClinicId == null) {
            new DAOException(DAOException.VALUE_NEEDED,
                    "meetClinicId is required!");
        }
        if (StringUtils.isEmpty(cancelCause)) {
            new DAOException(DAOException.VALUE_NEEDED,
                    "cancelCause is required!");
        }
        final MeetClinicDAO meetClinicDAO = DAOFactory
                .getDAO(MeetClinicDAO.class);
        final MeetClinic mc = meetClinicDAO.get(meetClinicId);
        if (mc.getMeetClinicStatus() >= 2) {
            return false;
        }

        final Date endTime = new Date();
        final String keyDoc = "doctor";
        final String keyTeams = "teams";
        final String mcrId = "meetClinicResultId";

        HibernateStatelessResultAction<Map<String, List<HashMap<String, Object>>>> action = new AbstractHibernateStatelessResultAction<Map<String, List<HashMap<String, Object>>>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                // 定义一个map用于存放需推送的医生列表
                Map<String, List<HashMap<String, Object>>> map = new HashMap<>();
                // 定义一个List用于存放需发送【取消】推送的医生信息
                List<HashMap<String, Object>> cancelDoctors = new ArrayList<>();
                // 定义一个List用于存放需发送【自动完成】推送的医生信息
                List<HashMap<String, Object>> endDoctors = new ArrayList<>();
                // 定义一个List用于存放需发送【取消理由】推送的医生信息
                List<HashMap<String, Object>> cancelCauseDoctors = new ArrayList<>();

                DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                // 查询出该会诊单所对应的所有执行单内容
                List<MeetClinicResult> meetClinicResults = findByMeetClinicId(meetClinicId);
                DoctorAccountDAO doctorAccountDAO = DAOFactory
                        .getDAO(DoctorAccountDAO.class);

                // 计算执行单中状态为结束的数量
                Integer endNum = 0;
                for (MeetClinicResult meetClinicResult : meetClinicResults) {
                    Integer effeStatus = meetClinicResult.getEffectiveStatus();
                    if (effeStatus != null && MeetClinicConstant.EFFECTIVESTATUS_INVALID.equals(effeStatus)) {
                        continue;
                    }
                    // 如果执行单已经结束，统计数加一
                    if (meetClinicResult.getExeStatus() == 2) {
                        endNum++;

                        continue;
                    }
                    // 如果执行单已拒绝或取消，不做任何操作
                    if (meetClinicResult.getExeStatus() > 2) {
                        continue;
                    }

                    // 先默认存放目标医生
                    Integer doctorId = meetClinicResult.getTargetDoctor();
                    Doctor targetDoctor = doctorDAO.getByDoctorId(doctorId);
                    Boolean teams = targetDoctor.getTeams() == null ? false : targetDoctor.getTeams();
                    Integer meetClinicResultId = meetClinicResult.getMeetClinicResultId();

                    // 将填写了报告的执行单结束(自动完成)
                    if (!StringUtils.isEmpty(meetClinicResult.getMeetReport())) {
                        updateExeStatusAndEndTimeByMeetClinicResultId(2,
                                endTime, meetClinicResult.getMeetClinicResultId());
                        HashMap<String, Object> infoMap = new HashMap<>();
                        infoMap.put(keyDoc, meetClinicResult.getExeDoctor());
                        infoMap.put(keyTeams, teams);
                        infoMap.put(mcrId, meetClinicResultId);
                        endDoctors.add(infoMap);

                        // 调取【增加账户收入】服务，给执行医生加钱
                        Date endDate = new Date();
                        Date startDate = meetClinicResult.getStartTime();
                        Long timeDifference = endDate.getTime() - startDate.getTime();
                        Integer exeDoctor = meetClinicResult.getExeDoctor();
                        Integer addFlag = 0;
                        // 允许小额时间差，对外公布为30分钟,追加奖金
                        if ((timeDifference / 60000.0) <= 31) {
                            addFlag = 1;
                        }

                        doctorAccountDAO.addDoctorIncome(exeDoctor, 4,
                                meetClinicId, addFlag);

                        endNum++;

                        continue;
                    }

                    // 对未回复的执行单做强制取消动作
                    updateExeStatusAndEndTimeByMeetClinicResultId(9, endTime,
                            meetClinicResult.getMeetClinicResultId());
                    // 有执行医生(会诊开始，但未回复)，则存放执行医生。
                    if (meetClinicResult.getExeDoctor() != null) {
                        doctorId = meetClinicResult.getExeDoctor();

                        HashMap<String, Object> infoMap = new HashMap<>();
                        infoMap.put(keyDoc, doctorId);
                        infoMap.put(keyTeams, teams);
                        infoMap.put(mcrId, meetClinicResultId);
                        cancelCauseDoctors.add(infoMap);

                        continue;
                    }
                    DoctorGroupDAO doctorGroupDAO = DAOFactory
                            .getDAO(DoctorGroupDAO.class);
                    List<DoctorGroup> dgs = doctorGroupDAO.findByDoctorId(doctorId);

                    // 目标医生不是团队医生,直接存放目标医生
                    if (dgs.isEmpty()) {
                        HashMap<String, Object> infoMap = new HashMap<>();
                        infoMap.put(keyDoc, doctorId);
                        infoMap.put(keyTeams, false);
                        infoMap.put(mcrId, meetClinicResultId);
                        cancelDoctors.add(infoMap);

                        continue;
                    }
                    // 目标医生是团队医生，且没有执行医生，存放团队中所有成员
                    for (int j = 0; j < dgs.size(); j++) {

                        HashMap<String, Object> infoMap = new HashMap<>();
                        infoMap.put(keyDoc, dgs.get(j).getMemberId());
                        infoMap.put(keyTeams, true);
                        infoMap.put(mcrId, meetClinicResultId);
                        cancelDoctors.add(infoMap);
                    }
                }
                // ***********************************************************************************
                // *** 如果部分是0，部分是1或2，则需要录入强制结束原因（和取消原因同一个字段）申请状态改为2 *
                // *** 结束或取消会诊单 *
                // ***********************************************************************************
                if (endNum > 0) {
                    // 结束会诊单，保存理由
                    meetClinicDAO
                            .updateMeetClinicStatusAndCancelCauseByMeetClinicId(
                                    2, cancelCause, meetClinicId);
                    // 调取【增加账户收入】服务,给申请医生加钱
                    Integer requestDoctor = mc.getRequestDoctor();

                    doctorAccountDAO.addDoctorIncome(requestDoctor, 3,
                            meetClinicId, 0);

                } else {
                    mc.setCancelCause(cancelCause);
                    mc.setCancelDepart(mc.getRequestDepart());
                    mc.setCancelDoctor(mc.getRequestDoctor());
                    mc.setCancelOrgan(mc.getRequestOrgan());
                    mc.setCancelTime(endTime);
                    mc.setMeetClinicStatus(9);
                    meetClinicDAO.update(mc);
                    // 添加会话结束时间
                    if (!StringUtils.isEmpty(mc.getSessionID())) {
                        meetClinicDAO.updateSessionEndTimeByMeetClinicId(
                                endTime, meetClinicId);
                    }
                }
                map.put("cancelDoctors", cancelDoctors);
                map.put("endDoctors", endDoctors);
                map.put("cancelCauseDoctors", cancelCauseDoctors);
                setResult(map);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
        Map<String, List<HashMap<String, Object>>> map = action.getResult();
        if (map == null) {
            return false;
        }
        // 定义一个List用于存放需发送【取消】推送的医生信息
        List<HashMap<String, Object>> cancelDoctors;
        // 定义一个List用于存放需发送【自动完成】推送的医生信息
        List<HashMap<String, Object>> endDoctors;
        // 定义一个List用于存放需发送【取消理由】推送的医生信息
        List<HashMap<String, Object>> cancelCauseDoctors;
        cancelDoctors = map.get("cancelDoctors");
        endDoctors = map.get("endDoctors");
        cancelCauseDoctors = map.get("cancelCauseDoctors");
        // 如果没有一条执行单被处理过
        if (cancelDoctors.isEmpty() && endDoctors.isEmpty() && cancelCauseDoctors.isEmpty()) {
            return false;
        }
        // 获取申请医生和患者姓名
        Integer requestDoctor = mc.getRequestDoctor();

        MeetClinicPushService pushService = new MeetClinicPushService();
        pushService.endMeetClinicByRequestPush(meetClinicId, requestDoctor, cancelDoctors, endDoctors, cancelCauseDoctors, cancelCause);
        for (int i = 0; i < endDoctors.size(); i++) {
            HashMap<String, Object> infoMap = endDoctors.get(i);
            Integer exeDoctorId = (Integer) infoMap.get(keyDoc);
            // 给写了报告的会诊医生的推荐医生推荐奖励
            DoctorAccountDAO accDao = DAOFactory.getDAO(DoctorAccountDAO.class);
            accDao.recommendReward(exeDoctorId);
        }

        // 结束群聊
        GroupDAO groupDAO = DAOFactory.getDAO(GroupDAO.class);
        groupDAO.closeMeetClinckGroup(meetClinicId);
        // 添加会话结束时间
        if (!StringUtils.isEmpty(mc.getSessionID())) {
            meetClinicDAO.updateSessionEndTimeByMeetClinicId(endTime,
                    meetClinicId);
        }

        AsynDoBussService asynDoBussService = AppContextHolder.getBean("asynDoBussService", AsynDoBussService.class);
        asynDoBussService.fireEvent(new BussFinishEvent(meetClinicId, BussTypeConstant.MEETCLINIC, meetClinicId));
        return true;
    }

    /**
     * 结束单条执行单（供申请医生结束服务调用）
     *
     * @param exeStatus
     * @param meetClinicResultId
     * @author LF
     */
    @DAOMethod(sql = "update MeetClinicResult set exeStatus=:exeStatus,endTime=:endTime where meetClinicResultId=:meetClinicResultId")
    public abstract void updateExeStatusAndEndTimeByMeetClinicResultId(
            @DAOParam("exeStatus") Integer exeStatus,
            @DAOParam("endTime") Date endTime,
            @DAOParam("meetClinicResultId") Integer meetClinicResultId);

    /**
     * 会诊单完成情况列表查询服务
     *
     * @param meetClinicId
     * @return
     * @author LF
     */
    @RpcService
    public List<MeetClinicReportTable> findListOfResult(Integer meetClinicId) {
        List<MeetClinicReportTable> clinicReportTables = new ArrayList<>();
        // 根据meetClinicId获取执行单列表
        List<MeetClinicResult> clinicResults = findByMeetClinicId(meetClinicId);
        // 根据doctorId获取医生信息
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        // 将执行单中所需要的部分存到clinicReportTables中
        for (int i = 0; i < clinicResults.size(); i++) {
            Integer meetClinicReport = 0;
            String meetClinicReportText = "未回复";
            String exeStatusText = "未进行";
            // 定义存放参数的对象
            MeetClinicReportTable clinicReportTable = new MeetClinicReportTable();
            clinicReportTable.setMeetClinicResultId(clinicResults.get(i)
                    .getMeetClinicResultId());
            Doctor doctor = doctorDAO.getByDoctorId(clinicResults.get(i)
                    .getTargetDoctor());
            clinicReportTable.setTargetDoctorName(doctor.getName());
            if (!StringUtils.isEmpty(clinicResults.get(i).getMeetReport())) {
                meetClinicReport = 1;// 0未回复，1已回复
                meetClinicReportText = "已回复";
            }
            clinicReportTable.setMeetClinicReport(meetClinicReport);
            clinicReportTable.setMeetClinicReportText(meetClinicReportText);
            clinicReportTable.setExeStatus(clinicResults.get(i).getExeStatus());
            if (clinicResults.get(i).getExeStatus() == 1) {
                exeStatusText = "进行中";
            }
            if (clinicResults.get(i).getExeStatus() == 2) {
                exeStatusText = "已完成";
            }
            if (clinicResults.get(i).getExeStatus() == 8) {
                exeStatusText = "已拒绝";
            }
            clinicReportTable.setExeStatusText(exeStatusText);
            // 对列表添加对象
            clinicReportTables.add(clinicReportTable);
        }
        return clinicReportTables;
    }

    @RpcService
    @DAOMethod
    public abstract MeetClinicResult getByMeetClinicResultId(
            int meetClinicResultid);

    @RpcService
    @DAOMethod
    public abstract List<MeetClinicResult> findByMeetClinicId(
            Integer meetClinicId);

    @DAOMethod
    public abstract List<MeetClinicResult> findByMeetClinicIdAndEffectiveStatus(Integer meetClinicId, Integer effectiveStatus);

    /**
     * 会诊结束服务
     *
     * @param meetClinicId
     * @param meetClinicResultId
     * @return
     * @throws DAOException
     * @throws ControllerException
     * @Date 2016-12-16 11:20:41
     * @author zhangsl
     * 回复会诊意见发送至会诊聊天页
     */
    @RpcService
    public Boolean endMeetClinic(final int meetClinicId,
                                 final int meetClinicResultId) {
        log.info("会诊结束服务(endMeetClinic):meetClinicId=" + meetClinicId
                + "; meetClinicResultId=" + meetClinicResultId);
        final DoctorAccountDAO doctorAccountDAO = DAOFactory
                .getDAO(DoctorAccountDAO.class);

        MeetClinicResult meetClinicResult = getByMeetClinicResultId(meetClinicResultId);
        Integer effeStatus = meetClinicResult.getEffectiveStatus();
        if (effeStatus != null && MeetClinicConstant.EFFECTIVESTATUS_INVALID.equals(effeStatus)) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "抱歉，您已被移出该会诊~");
        }
        int exeStatus = 0;
        String meetReport = null;
        meetReport = meetClinicResult.getMeetReport();
        exeStatus = meetClinicResult.getExeStatus();
        final Date endTime = new Date();
        if ((exeStatus == 1 || exeStatus == 2)
                && !StringUtils.isEmpty(meetReport)) {
            HibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {
                @Override
                public void execute(StatelessSession ss) throws Exception {
                    // 结束自己的执行单
                    String hql = "UPDATE MeetClinicResult SET exeStatus=:exeStatus , endTime=:endTime  WHERE MeetClinicResultID =:meetClinicResultId and effectiveStatus=:effectiveStatus";
                    Query q = ss.createQuery(hql);
                    q.setParameter("exeStatus", 2);
                    q.setParameter("meetClinicResultId", meetClinicResultId);
                    q.setParameter("endTime", endTime);
                    q.setParameter("effectiveStatus", MeetClinicConstant.EFFECTIVESTATUS_VALID);

                    if (q.executeUpdate() >= 1) {
                        // 调取【增加账户收入】服务
                        MeetClinicResult meetClinicResult = getByMeetClinicResultId(meetClinicResultId);
                        Date endDate = meetClinicResult.getEndTime();
                        Date startDate = meetClinicResult.getStartTime();
                        Long timeDifference = endDate.getTime()
                                - startDate.getTime();
                        Integer exeDoctor = meetClinicResult.getExeDoctor();
                        // 允许小额时间差，对外公布为30分钟
                        int addFlag = 0;
                        if ((timeDifference / 60000.0) <= 31) {
                            addFlag = 1;
                        } else {
                            addFlag = 0;
                        }

                        doctorAccountDAO.addDoctorIncome(exeDoctor, 4,
                                meetClinicId, addFlag);

                        AsynDoBussService asynDoBussService = AppContextHolder.getBean("asynDoBussService", AsynDoBussService.class);
                        asynDoBussService.fireEvent(new BussFinishEvent(meetClinicResultId, BussTypeConstant.MEETCLINIC));

                    }
                    // 结束会诊单
                    String hql2 = "UPDATE MeetClinic mc SET mc.meetClinicStatus =:meetClinicStatus WHERE (SELECT COUNT(*) FROM MeetClinicResult mr WHERE mr.meetClinicId =:meetClinicId AND mr.exeStatus < 2 AND mr.effectiveStatus=:effectiveStatus)<=0 AND mc.meetClinicId =:meetClinicId";
                    Query q2 = ss.createQuery(hql2);
                    q2.setParameter("meetClinicId", meetClinicId);
                    q2.setParameter("meetClinicStatus", 2);
                    q2.setParameter("effectiveStatus", MeetClinicConstant.EFFECTIVESTATUS_VALID);
                    Integer mcCount = q2.executeUpdate();
                    // 判断是否进行消息推送
                    if (mcCount >= 1) {
                        setResult(true);
                    } else {
                        setResult(false);
                    }
                }
            };
            HibernateSessionTemplate.instance().executeTrans(action);
            Boolean successOrNot = action.getResult();

            // 给执行医生的推荐医生奖励，不考虑会诊是否成功完成
            MeetClinicResult result = getByMeetClinicResultId(meetClinicResultId);
            DoctorAccountDAO accDao = DAOFactory.getDAO(DoctorAccountDAO.class);
            accDao.recommendReward(result.getExeDoctor());

            //发送会诊意见至聊天页
            ConsultMessageService msgService = AppContextHolder.getBean("eh.consultMessageService", ConsultMessageService.class);
            EasemobIMService imService = AppContextHolder.getBean("eh.imService", EasemobIMService.class);
            DoctorDAO doctorDao = DAOFactory.getDAO(DoctorDAO.class);
            PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
            MeetClinicDAO meetClinicDAO = DAOFactory.getDAO(MeetClinicDAO.class);
            MeetClinic mc = meetClinicDAO.getByMeetClinicId(meetClinicId);
            Doctor exeDoc = doctorDao.getByDoctorId(result.getExeDoctor());
            Patient patient = patientDAO.getByMpiId(mc.getMpiid());
            Integer doctorUrt = Util.getUrtForDoctor(exeDoc.getMobile());
            String msgContent = "会诊意见：" + meetReport;
            Map<String, String> ext = Maps.newHashMap();
            ext.put("busId", String.valueOf(meetClinicId));
            ext.put("busType", "1");    //会诊是 1 咨询可以是2
            ext.put("name", exeDoc.getName());
            ext.put("groupName", patient.getPatientName() + "的会诊");
            ext.put("avatar", exeDoc.getPhoto() == null ? null : exeDoc.getPhoto().toString());
            ext.put("gender", exeDoc.getGender());
            ext.put("uuid", String.valueOf(UUID.randomUUID()));
            //发送环信消息
            imService.sendMsgToGroupByDoctorUrt(doctorUrt, mc.getSessionID(), msgContent, ext);
            //消息记录至数据库
            msgService.doctorSendMsgWithConsultId("", ConsultConstant.BUS_TYPE_MEET, meetClinicId, String.valueOf(MsgTypeEnum.TEXT.getId()), msgContent);
            log.info(LocalStringUtil.format("doctor[{}] end MeetClinicResult[{}],report send to huanxin:[{}]", result.getExeDoctor(), result.getMeetClinicResultId(), msgContent));

            // 消息推送
            if (successOrNot) {
                Integer requestDoctor = mc.getRequestDoctor();

                // 调取【增加账户收入】服务
                doctorAccountDAO.addDoctorIncome(requestDoctor, 3,
                        meetClinicId, 0);

                // 添加会话结束时间
                if (!StringUtils.isEmpty(mc.getSessionID())) {
                    meetClinicDAO.updateSessionEndTimeByMeetClinicId(endTime,
                            meetClinicId);
                }

                DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                Doctor targetDoctor = doctorDAO.get(meetClinicResult.getTargetDoctor());
                boolean teams = targetDoctor.getTeams() == null ? false : targetDoctor.getTeams();

                MeetClinicPushService pushService = new MeetClinicPushService();
                pushService.endMeetClinicPush(meetClinicId, requestDoctor, teams);
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * 根据执行医生和执行状态查询会诊执行单列表
     *
     * @param exeDoctor 执行医生id
     * @param exeStatus 执行状态(0:待处理；1：处理中；2：已会诊；8：拒绝；9：取消)
     * @return
     * @author ZX
     * @date 2015-9-7 下午3:12:01
     */
    @RpcService
    @DAOMethod
    public abstract List<MeetClinicResult> findByExeDoctorAndExeStatus(
            int exeDoctor, int exeStatus);

    @DAOMethod(sql = "select count(*) from MeetClinicResult where exeStatus=2 and meetClinicId=:meetClinicId group by endTime order by endTime")
    public abstract List<Long> findNumByTime(
            @DAOParam("meetClinicId") Integer meetClinicId);

    @DAOMethod(sql = "from MeetClinicResult where exeStatus=2 and meetClinicId=:meetClinicId order by endTime")
    public abstract List<MeetClinicResult> findByTime(
            @DAOParam("meetClinicId") Integer meetClinicId);

    @RpcService
    @DAOMethod
    public abstract List<MeetClinicResult> findByExeDoctorAndMeetClinicId(
            int exeDoctor, int meetClinicId);

}
