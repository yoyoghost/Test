package eh.mpi.service.follow;

import com.alibaba.fastjson.JSONObject;
import ctd.account.UserRoleToken;
import ctd.mvc.weixin.entity.OAuthWeixinMP;
import ctd.mvc.weixin.entity.OAuthWeixinMPDAO;
import ctd.net.broadcast.MQHelper;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.constant.SystemConstant;
import eh.base.dao.DoctorDAO;
import eh.base.dao.EmploymentDAO;
import eh.base.user.UserSevice;
import eh.bus.constant.ConsultConstant;
import eh.bus.constant.MsgTypeEnum;
import eh.bus.dao.AppointRecordDAO;
import eh.bus.service.consult.ConsultMessageService;
import eh.bus.service.consult.OnsConfig;
import eh.entity.base.Doctor;
import eh.entity.base.Employment;
import eh.entity.base.FollowChatStatistics;
import eh.entity.bus.AppointRecord;
import eh.entity.bus.PageResult;
import eh.entity.bus.vo.WxMsgVo;
import eh.entity.mpi.*;
import eh.mpi.constant.FollowConstant;
import eh.mpi.dao.FollowChatDAO;
import eh.mpi.dao.FollowChatMsgDAO;
import eh.mpi.dao.FollowPlanDAO;
import eh.mpi.dao.PatientDAO;
import eh.msg.dao.GroupDAO;
import eh.push.SmsPushService;
import eh.utils.LocalStringUtil;
import eh.utils.ValidateUtil;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.StatelessSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author renzh
 * @date 2017-02-13 下午 17:15
 */
public class FollowChatService {

    private static final Logger logger = LoggerFactory.getLogger(FollowChatService.class);

    private FollowChatDAO followChatDAO;
    private FollowChatMsgDAO followChatMsgDAO;
    private GroupDAO groupDAO;
    private DoctorDAO doctorDAO;
    private PatientDAO patientDAO;
    private AppointRecordDAO appointRecordDAO;
    private FollowPlanDAO followPlanDAO;
    private EmploymentDAO employmentDAO;
    private UserSevice userService;

    public FollowChatService(){
        followChatDAO = DAOFactory.getDAO(FollowChatDAO.class);
        followChatMsgDAO = DAOFactory.getDAO(FollowChatMsgDAO.class);
        groupDAO = DAOFactory.getDAO(GroupDAO.class);
        doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        patientDAO = DAOFactory.getDAO(PatientDAO.class);
        appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
        followPlanDAO = DAOFactory.getDAO(FollowPlanDAO.class);
        employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
        userService = AppContextHolder.getBean("eh.userSevice", UserSevice.class);
    }

    /**
     * 医生取消关注患者之前判断是否有会话随访
     * @param doctorId
     * @param mpiId
     * @return
     */
    @RpcService
    public Boolean canCancelRelation(Integer doctorId,String mpiId){
        List<FollowChat> todayChatList = followChatDAO.findFollowChatWithTime(mpiId, doctorId, new Date());
        if(!CollectionUtils.isEmpty(todayChatList)){
            return todayChatList.get(0).getHasEnd();
        }else{
            return true;
        }
    }

    /**
     * 医生（app）端点击会话随访--创建群组，添加会话随访记录
     * @param doctorId
     * @param mpiId
     * @return
     */
    @RpcService
    public Map<String,Object> joinFollowChat(Integer doctorId,String mpiId,Boolean toOpen){
        Map map = new HashMap();
        Patient patient = patientDAO.get(mpiId);
        Integer patientUrt = userService.getUrtIdByUserId(patient.getLoginId(), SystemConstant.ROLES_PATIENT);
        List<OAuthWeixinMP> oAuthWeixinMPList = DAOFactory.getDAO(OAuthWeixinMPDAO.class).findByUrt(patientUrt);
        if(!CollectionUtils.isEmpty(oAuthWeixinMPList)){
            Boolean flag = false;
            for(OAuthWeixinMP oAuthWeixinMP:oAuthWeixinMPList){
                if(StringUtils.equals(oAuthWeixinMP.getSubscribe(),"1")){
                    flag = true;
                    break;
                }
            }
            if(flag){
                List<FollowChat> lastFollowChatList = followChatDAO.findFollowChatWithTime(mpiId, doctorId, null);
                FollowChat followChat;
                //如果toOpen 是false 那么如果以前存在随访会话 就把之前的信息返回不创建新的随访会话
                if(!toOpen && null != lastFollowChatList && lastFollowChatList.size() > 0){
                    followChat = lastFollowChatList.get(0);
                }else {
                    followChat = createFollowChat(mpiId, doctorId, FollowConstant.FOLLOWCHAT_HASOPEN, FollowConstant.CHATROLE_DOC, new Date());
                    if (toOpen && followChat.getHasEnd().equals(FollowConstant.FOLLOWCHAT_HASEND)) {
                        followChat = changeEndStatusFollowChat(followChat, null, FollowConstant.FOLLOWCHAT_HASOPEN);
                    }
                }
                map.put("sessionId", followChat.getSessionID());
                map.put("hasEnd", followChat.getHasEnd());
                return map;
            }
        }
        map.put("sessionId","");
        return map;
    }

    /**
     * 医生结束会话随访
     * @param doctorId
     * @param mpiId
     * @return
     */
    @RpcService
    public Boolean overFollowChat(Integer doctorId, String mpiId){
        List<FollowChat> todayChatList = followChatDAO.findFollowChatWithTime(mpiId,doctorId, new Date());
        if(!CollectionUtils.isEmpty(todayChatList)){
            FollowChat followChat = todayChatList.get(0);
            changeEndStatusFollowChat(followChat, FollowConstant.CHATROLE_DOC, FollowConstant.FOLLOWCHAT_HASEND);
            return true;
        }else{
            return false;
        }
    }

    /**
     * 记录医生发送消息
     * @param hxMsgId
     * @param groupId
     * @param msgType
     * @param msgContent
     * @return
     */
    @RpcService
    public boolean doctorSendMsg(String hxMsgId,String groupId,String msgType,String msgContent){
        if (!OnsConfig.onsSwitch) {
            logger.info("the onsSwitch is set off, ons is out of service.");
            return false;
        }
        final FollowTempMsgType msgBody = new FollowTempMsgType();
        msgBody.setMsgContent(msgContent);
        msgBody.setCreateTime(new Date());
        msgBody.setSendTime(new Date());
        msgBody.setMsgType(msgType);
        msgBody.setHxMsgId(hxMsgId);
        msgBody.setGroupId(groupId);
        MQHelper.getMqPublisher().publish(OnsConfig.doctorTopic, msgBody);
        return true;
    }

    /**
     * 保存医生端发送的环信消息并发微信客服消息（通知患者）
     * @param tMsg
     */
    public void handleDoctorMessage(FollowTempMsgType tMsg) {
        FollowChat followChat = followChatDAO.findBySessionID(tMsg.getGroupId()).get(0);
        FollowChatMsg followChatMsg = new FollowChatMsg();
        followChatMsg.setDoctorId(followChat.getChatDoctor());
        followChatMsg.setFollowChatId(followChat.getId());
        followChatMsg.setCreateTime(tMsg.getCreateTime());
        followChatMsg.setMpiId(followChat.getMpiId());
        followChatMsg.setSenderId(followChat.getChatDoctor().toString());
        followChatMsg.setSenderRole(ConsultConstant.MSG_ROLE_TYPE_DOCTOR);
        followChatMsg.setReceiverId(followChat.getMpiId());
        followChatMsg.setMsgExtra(tMsg.getHxMsgId());
        followChatMsg.setReceiverRole(ConsultConstant.MSG_ROLE_TYPE_PATIENT);
        if(MsgTypeEnum.GRAPHIC.getId() == Short.valueOf(tMsg.getMsgType()) && tMsg.getMsgContent()!=null){
            String tempContent = tMsg.getMsgContent();
            tempContent = tempContent.replaceAll("\\D","");
            tMsg.setMsgContent(tempContent);
        }
        //消息类型是语音，
        if (MsgTypeEnum.AUDIO.getId() == Short.valueOf(tMsg.getMsgType()) && tMsg.getMsgContent()!=null){
            String userId=patientDAO.getMobileByMpiId(followChat.getMpiId());
            tMsg.setUserId(userId);
            handleDoctorVoiceMessage(tMsg);
        }
        followChatMsg.setMsgContent(tMsg.getMsgContent());
        followChatMsg.setMsgType(Short.valueOf(tMsg.getMsgType()));
        followChatMsg.setSendTime(tMsg.getSendTime());
        followChatMsg.setDeleted(0);
        followChatMsg.setHasRead(false);
        followChatMsgDAO.save(followChatMsg);
        AppContextHolder.getBean("eh.smsPushService", SmsPushService.class).pushMsgData2Ons(followChatMsg.getId().intValue(), followChat.getChatDoctorOrgan(), "FollowChatSend", "", 0);
    }

    private void handleDoctorVoiceMessage(FollowTempMsgType tMsg) {
        try {
            new ConsultMessageService().handleDoctorVoiceMessage(tMsg);
        }catch (Exception e){
            logger.error("handleDoctorVoiceMessage error, ttMsg[{}], errorMessage[{}], stackTrace[{}]", JSONObject.toJSONString(tMsg), e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
        }
    }


    /**
     * 记录并发送患者发送消息
     * @param hxMsgId
     * @param groupId
     * @param msgType
     * @param msgContent
     * @return
     */
    @RpcService
    public boolean patientSendMsg(String hxMsgId,String groupId,String msgType,String msgContent){
        if (!OnsConfig.onsSwitch) {
            logger.info("the onsSwitch is set off, ons is out of service.");
            return false;
        }
        final FollowTempMsgType msgBody = new FollowTempMsgType();
        msgBody.setMsgContent(msgContent);
        msgBody.setCreateTime(new Date());
        msgBody.setSendTime(new Date());
        msgBody.setMsgType(msgType);
        msgBody.setHxMsgId(hxMsgId);
        msgBody.setGroupId(groupId);
        MQHelper.getMqPublisher().publish(OnsConfig.patientTopic, msgBody);
        return true;
    }

    /**
     * 保存患者端发送的环信消息
     * @param tMsg
     */
    public void handlePatientMessage(FollowTempMsgType tMsg) {
        FollowChat followChat = followChatDAO.findBySessionID(tMsg.getGroupId()).get(0);
        FollowChatMsg followChatMsg = new FollowChatMsg();
        followChatMsg.setDoctorId(followChat.getChatDoctor());
        followChatMsg.setFollowChatId(followChat.getId());
        followChatMsg.setCreateTime(tMsg.getCreateTime());
        followChatMsg.setMpiId(followChat.getMpiId());
        followChatMsg.setSenderId(followChat.getMpiId());
        followChatMsg.setSenderRole(ConsultConstant.MSG_ROLE_TYPE_PATIENT);
        followChatMsg.setReceiverId(followChat.getChatDoctor().toString());
        followChatMsg.setReceiverRole(ConsultConstant.MSG_ROLE_TYPE_DOCTOR);
        followChatMsg.setMsgContent(tMsg.getMsgContent());
        followChatMsg.setMsgExtra(tMsg.getHxMsgId());
        followChatMsg.setMsgType(Short.valueOf(tMsg.getMsgType()));
        followChatMsg.setSendTime(tMsg.getSendTime());
        followChatMsg.setDeleted(0);
        followChatMsg.setHasRead(true);
        followChatMsgDAO.save(followChatMsg);
    }

    /**
     * 健康端获取历史消息
     * @param doctorId
     * @param startIndex
     * @param pageSize
     * @param version
     * @return
     */
    @RpcService
    public PageResult<Map<String, Object>> findFollowChatMsgDetailWithDoctorId(Integer doctorId, int startIndex, int pageSize, String version) {
        if(startIndex!=0 && ValidateUtil.isNotNum(version)){
            logger.error(LocalStringUtil.format("findFollowChatMsgDetailWithDoctorId parameters invalid! doctorId[{}]startIndex[{}]version[{}]", doctorId, startIndex, version));
            throw new DAOException(609, "请求参数[version]有误");
        }
        Date firstRequestTime = new Date();
        if(startIndex!=0){
            firstRequestTime = new Date(Long.parseLong(version.trim()));
        }
        UserRoleToken urt = UserRoleToken.getCurrent();
        Patient patient = (Patient) urt.getProperty("patient");
        String requestMpi = patient.getMpiId();
        followChatMsgDAO.updateDoctorMessageToHasReadExceptAudio(requestMpi, doctorId);
        PageResult<Map<String, Object>> pageResult = new PageResult<>();
        pageResult.setPageSize(pageSize);
        List<FollowChatMsg> followChatMsgList = followChatMsgDAO.findDoctorFollowChatMessageList(requestMpi, doctorId, firstRequestTime, startIndex, pageSize);
        try {
            pageResult.setNextIndex(startIndex + followChatMsgList.size());
            pageResult.setVersion(firstRequestTime.getTime());
            FollowChat followChat = followChatDAO.findFollowChatWithTime(requestMpi,doctorId, null).get(0);
            pageResult.setBusId(followChat.getId());
            pageResult.setHasEnd(followChat.getHasEnd());
            List<Map<String, Object>> data = packageFollowChatMessageList(followChatMsgList);
            pageResult.setData(data);
        }catch (Exception e){
            logger.error(LocalStringUtil.format("beanMapper exception! errorMsg[{}], errorStack[{}]", e.getMessage(), e.getStackTrace()[0]));
        }catch (Throwable e){
            logger.error(LocalStringUtil.format("beanMapper throwable! errorMsg[{}], errorStack[{}]", e.getMessage(), e.getStackTrace()[0]));
        }
        return pageResult;
    }

    public List<Map<String, Object>> packageFollowChatMessageList(List<FollowChatMsg> followChatMsgList) {
        List<WxMsgVo> msgVoList = new ArrayList<>();
        for(FollowChatMsg bc : followChatMsgList){
            WxMsgVo vo = new WxMsgVo();
            vo.setId(bc.getId());
            vo.setMsgContent(bc.getMsgContent());
            vo.setSendTime(bc.getSendTime());
            vo.setCreateTime(bc.getCreateTime());
            vo.setMsgType(bc.getMsgType());
            vo.setReceiverId(bc.getReceiverId());
            vo.setReceiverRole(bc.getReceiverRole());
            vo.setSenderId(bc.getSenderId());
            vo.setSenderRole(bc.getSenderRole());
            vo.setHxMsgId(bc.getMsgExtra());
            vo.setHasRead((bc.getHasRead())?1:0);
            vo.setOtherBusId(bc.getOtherId());
            msgVoList.add(vo);
        }
        logger.info(LocalStringUtil.format("after beanMapper, msgVoList[{}]", msgVoList));
        TempDataList<WxMsgVo> dataList = new TempDataList<>();
        Collections.reverse(msgVoList);
        String lastGroupTimeStr = null;
        for (WxMsgVo vo : msgVoList) {
            try {
                if (vo.getMsgType() == 91 && vo.getMsgContent() != null) {
                    FollowAssess pdd = JSONObject.parseObject((String) vo.getMsgContent(), FollowAssess.class);
                    vo.setMsgContent(pdd);
                    Integer bussId = vo.getOtherBusId();
                    if (bussId!=null){
                        setActualInfo(bussId,vo);
                    }
                }
                if (vo.getMsgType() == 92 && vo.getMsgContent() !=null){
                    Integer bussId = vo.getOtherBusId();
                    if (bussId!=null){
                        setActualInfo(bussId,vo);
                    }
                }
                lastGroupTimeStr = getTimeGroupByCompareSendTimeAndLast(vo, lastGroupTimeStr);
                dataList.add(lastGroupTimeStr, vo);
            } catch (Exception e) {
                logger.error(LocalStringUtil.format("bean mapper failed! msgType:{}, sourceStr:{}, " +
                        "errorMessage:{}", vo.getMsgType(), vo.getMsgContent(), e.getMessage()));
            }
        }
        return dataList.getList();
    }

    /**
     * 预诊发送表单时健康端拉取聊天记录时set对应实际病人信息
     * @param bussId
     * @param vo
     */
    private void setActualInfo(Integer bussId ,WxMsgVo vo){
        FollowPlan followPlan = followPlanDAO.getFollowPlanByScheduleId(bussId);
        if (followPlan==null) {
            return ;
        }
        Integer appointRecordId =  followPlan.getAppointRecordId();
        if (appointRecordId!=null && appointRecordId!=0) {
            AppointRecord appointRecord = appointRecordDAO.get(appointRecordId);
            String mpiId = appointRecord.getMpiid();
            String patName = patientDAO.getNameByMpiId(mpiId);
            vo.setActuallyMpiId(mpiId);
            vo.setActuallyPatientName(patName);
        }
    }

    static class TempDataList<T> {
        private static String DATE_KEY = "date";
        private static String LIST_KEY = "msgList";
        private List<Map<String, Object>> list = new ArrayList<>();

        public void add(String key, T t) {
            boolean exists = false;
            for (Map<String, Object> map : list) {
                if (key.equals(map.get(DATE_KEY))) {
                    List<T> subList = (List<T>) map.get(LIST_KEY);
                    subList.add(t);
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                Map<String, Object> map = new HashMap<>();
                List<T> subList = new ArrayList<>();
                subList.add(t);
                map.put(DATE_KEY, key);
                map.put(LIST_KEY, subList);
                list.add(map);
            }

        }

        public List<Map<String, Object>> getList() {
            return list;
        }
    }

    private String getTimeGroupByCompareSendTimeAndLast(WxMsgVo vo, String lastGroupTimeStr) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        try {
            if (vo.getSendTime() != null) {
                return sdf.format(vo.getSendTime());
            }
            if (ValidateUtil.notBlankString(lastGroupTimeStr)) {
                Date lastTime = sdf.parse(lastGroupTimeStr);
                int fiveMinuteMillisSeconds = 5 * 60 * 1000;
                long minusMillisSeconds = vo.getCreateTime().getTime() - lastTime.getTime();
                if (minusMillisSeconds < fiveMinuteMillisSeconds && minusMillisSeconds >= 0) {
                    return sdf.format(lastTime);
                }
            }
        } catch (ParseException e) {
            logger.error(LocalStringUtil.format("getTimeGroupByCompareSendTimeAndLast error, errorMessage[{}],lastGroupTimeStr[{}],vo[{}]", e.getMessage(), lastGroupTimeStr, vo));
        }
        return sdf.format(vo.getCreateTime());
    }

    @RpcService
    public FollowChatMsg getMsgById(Long id){
        return followChatMsgDAO.get(id);
    }

    @RpcService
    public FollowChatMsg getMsgByOtherId(Integer busId){
        return followChatMsgDAO.getByOtherId(busId);
    }

    @RpcService
    public FollowChat getFollowChatById(Integer id){
        return followChatDAO.get(id);
    }

    @RpcService
    public void followChatOverTime(){
        List<FollowChat> followChatList = followChatDAO.findNotEndChat();
        if(!CollectionUtils.isEmpty(followChatList)){
            for(FollowChat followChat:followChatList){
                changeEndStatusFollowChat(followChat, FollowConstant.CHATROLE_SYS, FollowConstant.FOLLOWCHAT_HASEND);
            }
        }
    }

    /**
     * 更新状态
     * @param followChat
     * @param endRole
     * @param endFlag
     */
    private FollowChat changeEndStatusFollowChat(FollowChat followChat, Integer endRole, Boolean endFlag){
        followChat.setHasEnd(endFlag);
        if(null != endRole) {
            followChat.setChatEnd(endRole);
        }
        if(endFlag.equals(FollowConstant.FOLLOWCHAT_HASEND)){
            followChat.setEndTime(new Date());
        }
        return followChatDAO.update(followChat);
    }

    /**
     * 往会话随访里插入消息 插入前会先生成会话随访 或者返回已存在的随访
     * @param senderRole
     * @param receiverRole
     * @param mpiId
     * @param doctorId
     * @param msgType
     * @param busId
     * @param msgContent
     */
    @RpcService
    public void addMsgOfDocShare(String senderRole, String receiverRole, String mpiId, Integer doctorId, MsgTypeEnum msgType, Integer busId, String msgContent) {
        FollowChatMsg followChatMsg = new FollowChatMsg();
        FollowChat followChat = createFollowChat(mpiId, doctorId, FollowConstant.FOLLOWCHAT_HASEND, FollowConstant.CHATROLE_SYS, new Date());
        followChatMsg.setFollowChatId(followChat.getId());
        followChatMsg.setOtherId(busId);
        followChatMsg.setHasRead(false);
        followChatMsg.setMpiId(mpiId);
        followChatMsg.setDoctorId(doctorId);
        followChatMsg.setSenderRole(senderRole);
        followChatMsg.setSenderId(("doctor".equals(senderRole))?doctorId.toString():mpiId);
        followChatMsg.setReceiverRole(receiverRole);
        followChatMsg.setReceiverId(("doctor".equals(receiverRole))?doctorId.toString():mpiId);
        followChatMsg.setMsgExtra(null);
        followChatMsg.setMsgType(msgType.getId());
        followChatMsg.setMsgContent(msgContent);
        followChatMsg.setAppId(null);
        followChatMsg.setOpenId(null);
        followChatMsg.setSendTime(new Date());
        followChatMsg.setDeleted(0);
        followChatMsg.setCreateTime(new Date());
        followChatMsgDAO.save(followChatMsg);
    }

    /**
     * 仅仅是往会话里 增加消息记录
     * @param senderRole
     * @param receiverRole
     * @param mpiId
     * @param doctorId
     * @param msgType
     * @param busId
     * @param msgContent
     * @param followChat
     */
    public void addMsg(String senderRole, String receiverRole, String mpiId, Integer doctorId, MsgTypeEnum msgType, Integer busId, String msgContent, FollowChat followChat){
        FollowChatMsg followChatMsg = new FollowChatMsg();
        followChatMsg.setFollowChatId(followChat.getId());
        followChatMsg.setOtherId(busId);
        followChatMsg.setHasRead(false);
        followChatMsg.setMpiId(mpiId);
        followChatMsg.setDoctorId(doctorId);
        followChatMsg.setSenderRole(senderRole);
        followChatMsg.setSenderId(("doctor".equals(senderRole))?doctorId.toString():mpiId);
        followChatMsg.setReceiverRole(receiverRole);
        followChatMsg.setReceiverId(("doctor".equals(receiverRole))?doctorId.toString():mpiId);
        followChatMsg.setMsgExtra(null);
        followChatMsg.setMsgType(msgType.getId());
        followChatMsg.setMsgContent(msgContent);
        followChatMsg.setAppId(null);
        followChatMsg.setOpenId(null);
        followChatMsg.setSendTime(new Date());
        followChatMsg.setDeleted(0);
        followChatMsg.setCreateTime(new Date());
        followChatMsgDAO.save(followChatMsg);
    }

    @RpcService
    public String getSessionId(Integer doctorId,String mpiId){
        FollowChat followChat = createFollowChat(mpiId, doctorId, FollowConstant.FOLLOWCHAT_HASEND, FollowConstant.CHATROLE_SYS, null);
        return followChat.getSessionID();
    }

    /**
     * 查找指定患者 和医生 的sessionId 如果不存在返回空字符串
     * zhongzx
     * @param doctorId
     * @param mpiId
     * @return
     */
    @RpcService
    public String querySessionId(Integer doctorId,String mpiId) {
        FollowChat followChat = createFollowChat(mpiId, doctorId, FollowConstant.FOLLOWCHAT_HASEND, FollowConstant.CHATROLE_SYS, null);
        return followChat.getSessionID();
    }

    /**
     * 患者点击语音设为已读
     * @param msgId
     */
    @RpcService
    public void setVoiceOfFollowMsgRead(String msgId){
        followChatMsgDAO.updateVoiceOfFollowMsgRead(msgId);
    }

    /**
     * 将某条随访消息设置为已读状态
     * @param followChatMsgId
     */
    @RpcService
    public void updateMsgHasRead(Long followChatMsgId){
        followChatMsgDAO.updateMsgHasRead(followChatMsgId);
    }


    /**
     * 创建新的会话随访记录 规则：一天产生一条
     * @param mpiId
     * @param doctorId
     * @return
     */
    public FollowChat createFollowChat(final String mpiId, final Integer doctorId, final Boolean endFlag, final Integer createRole, Date date){
        List<FollowChat> ChatList = followChatDAO.findFollowChatWithTime(mpiId, doctorId, date);
        if(CollectionUtils.isEmpty(ChatList)){
            HibernateStatelessResultAction<FollowChat> action = new AbstractHibernateStatelessResultAction<FollowChat>() {
                @Override
                public void execute(StatelessSession statelessSession) throws Exception {
                    Doctor doctor = doctorDAO.get(doctorId);
                    Patient patient = patientDAO.get(mpiId);
                    if(null == doctor){
                        throw new DAOException(ErrorCode.SERVICE_ERROR, "doctorId=["+doctorId+"] can not find doctor");
                    }
                    if(null == patient){
                        throw new DAOException(ErrorCode.SERVICE_ERROR, "mpiId=["+mpiId+"] can not find patient");
                    }
                    Integer patientUrt = userService.getUrtIdByUserId(patient.getLoginId(), SystemConstant.ROLES_PATIENT);
                    Integer doctorUrt = userService.getUrtIdByUserId(doctor.getMobile(), SystemConstant.ROLES_DOCTOR);
                    FollowChat followChat = new FollowChat();
                    followChat.setChatDoctor(doctorId);
                    followChat.setChatDoctorDepart(doctor.getDepartment());
                    followChat.setChatDoctorOrgan(doctor.getOrgan());
                    followChat.setChatFrom(createRole);
                    followChat.setMpiUrt(patientUrt);
                    followChat.setDoctorUrt(doctorUrt);
                    followChat.setMpiId(mpiId);
                    followChat.setRequestTime(new Date());
                    followChat.setChatType(FollowConstant.CHATFOLLOW);
                    followChat.setHasEnd(endFlag);
                    followChatDAO.save(followChat);
                    String groupId = groupDAO.getGroupIdForFollowChat(followChat);
                    followChatDAO.updateSessionIdById(followChat.getId(), groupId);
                    followChat.setSessionID(groupId);
                    setResult(followChat);
                }
            };
            HibernateSessionTemplate.instance().executeTrans(action);
            return action.getResult();
        }else{
            return ChatList.get(0);
        }
    }
    
    /**
     * <h1>根据当前医生做随访统计
     * <li>本月随访次数
     * <li>随访总次数
     * <li>累计服务患者数
     * <li>医生随访排名
     * <li>击败百分比
     * <br>
     * @author hexy
     */
    @RpcService 
    public FollowChatStatistics statisticsFollow(Integer doctorId){
    	FollowChatStatistics vo = new FollowChatStatistics();
        Date startTime = getTime(0);
        Date endTime = getTime(1);
        List<FollowChat> countThisMonlist = followChatDAO.getCountThisMon(doctorId, startTime, endTime);
		Long countThisMon = Long.valueOf(countThisMonlist.size());
		
		List<FollowChat> countThisDocSumlist = followChatDAO.getCountThisDocSum(doctorId);
		Long countThisDocSum = Long.valueOf(countThisDocSumlist.size());
		Long countThisDocPatientSum = followChatDAO.getCountThisDocPatientSum(doctorId);
		Long followChatRank = getfollowChatRank(doctorId,countThisMon,startTime, endTime);
		String percentPep = getPercentPep(doctorId,followChatRank,startTime, endTime);
		
		vo.setPercentPep(percentPep);
		vo.setFollowChatRank(followChatRank);
		vo.setFollowChatSum(countThisDocSum);
		vo.setFollowChatThisMonSum(countThisMon);
		vo.setFollowChatPatientSum(countThisDocPatientSum);
    	return vo;
    }


	private Date getTime(int dayFlag) {
		try {
			  Calendar cale = Calendar.getInstance(); 
			  SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd"); 
			  cale.add(Calendar.MONTH, dayFlag);
			  cale.set(Calendar.DAY_OF_MONTH,1);
			  String sTime = format.format(cale.getTime());  
			  return format.parse(sTime);
		} catch (Exception e) {
			logger.error("init Time err"+e.getMessage());
		}
		return null;
	}

    /**
     * 获取排名
     * @param doctorId
     * @param countThisMon
     * @param startTime
     * @param endTime
     * @return
     */
	private Long getfollowChatRank(Integer doctorId, Long countThisMon, Date startTime, Date endTime) {
		Employment employment = employmentDAO.getPrimaryEmpByDoctorId(doctorId);
		if (null != employment) {
			List<Integer> listDoc = followChatDAO.getfollowChatRankList(employment.getOrganId(),startTime, endTime);
			List<Long> allDocMonSum = new ArrayList<>();
			for (Integer doc : listDoc) {
				List<FollowChat> docMonSum = followChatDAO.getCountThisMon(doc, startTime, endTime);
				if (!CollectionUtils.isEmpty(docMonSum)) {
					allDocMonSum.add(Long.valueOf(docMonSum.size()));
				}
			}
			//对list进行排序
			Collections.sort(allDocMonSum,Collections.reverseOrder());
			if (!CollectionUtils.isEmpty(allDocMonSum)) {
                for (int i = 0, len = allDocMonSum.size(); i < len; i++) {
                    if (allDocMonSum.get(i).equals(countThisMon)) {
                        return Long.valueOf(i) + 1;
                    }
                }
            }
            return Long.valueOf(listDoc.size() + 1);
		}else {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "doctorId="+doctorId+" employment is null");
        }
	}

    /**
     * 计算百分比
     * @param doctorId
     * @param followChatRank
     * @param startTime
     * @param endTime
     * @return
     */
	private String getPercentPep(Integer doctorId, Long followChatRank,
			Date startTime, Date endTime) {
		Employment employment = employmentDAO.getPrimaryEmpByDoctorId(doctorId);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
		if (null != employment) {
            Integer rankSize = followChatDAO.getfollowChatRankList(employment.getOrganId(),startTime, endTime).size();
            //如果排名大于 最后一名
            if(followChatRank > rankSize){
                return "0";
            }
            Long size = doctorDAO.getEffectiveDoctorByOrganId(employment.getOrganId());
            if(null != size && size > 0) {
                Integer defeatNum = size.intValue() - followChatRank.intValue();
                String format = String.valueOf((int) Math.floor((float) defeatNum / (float) size * 100));
                return format;
            }else{
                logger.error("doctor size=[{}]", size);
            }
		}else{
            logger.error("employment is null, doctorId=[{}]", doctorId);
        }
		return "0";
	}
	

    /**
     * 保存电话随访记录
     * @param followChat
     * 电话随访 mpiId 和 手机姓名 传其中一个患者标识信息
     * 有mpiId 只要传mpiId 没有mpiId 传手机号和姓名信息
     * @return
     */
    @RpcService
    public boolean saveFollowPhoneChat(FollowChat followChat){
        if(null == followChat){
            throw new DAOException(DAOException.VALUE_NEEDED, "followChat is null");
        }
        String mpiId = followChat.getMpiId();
        String pName = followChat.getPatientName();
        String pMobile = followChat.getPatientMobile();
        Integer doctorId = followChat.getChatDoctor();
        if(null == doctorId){
            throw new DAOException(DAOException.VALUE_NEEDED, "doctorId is null");
        }
        if(StringUtils.isEmpty(mpiId)){
            if(StringUtils.isEmpty(pName)){
                throw new DAOException(DAOException.VALUE_NEEDED, "patientName is null");
            }
            if(StringUtils.isEmpty(pMobile)){
                throw new DAOException(DAOException.VALUE_NEEDED, "pMobile is null");
            }
        }
        //2-电话随访
        followChat.setChatType(FollowConstant.PHONEFOLLOW);
        followChat.setRequestTime(new Date());
        //1-发起方医生
        followChat.setChatFrom(FollowConstant.CHATROLE_DOC);
        followChatDAO.save(followChat);
        return true;
    }

}
