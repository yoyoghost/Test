package eh.bus.dao;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Maps;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.spring.AppDomainContext;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.BussTypeConstant;
import eh.base.constant.ErrorCode;
import eh.base.dao.*;
import eh.bus.asyndobuss.bean.BussCreateEvent;
import eh.bus.asyndobuss.service.AsynDoBussService;
import eh.bus.constant.ConsultConstant;
import eh.bus.constant.MeetClinicConstant;
import eh.bus.constant.MsgTypeEnum;
import eh.bus.service.ObtainImageInfoService;
import eh.bus.service.consult.ConsultMessageService;
import eh.bus.service.meetclinic.MeetClinicPushService;
import eh.cdr.constant.OtherdocConstant;
import eh.cdr.dao.CdrOtherdocDAO;
import eh.entity.base.Doctor;
import eh.entity.base.Employment;
import eh.entity.bus.MeetClinic;
import eh.entity.bus.MeetClinicResult;
import eh.entity.cdr.Otherdoc;
import eh.entity.mpi.Patient;
import eh.entity.msg.Group;
import eh.mpi.dao.PatientDAO;
import eh.msg.dao.GroupDAO;
import eh.msg.service.EasemobIMService;
import eh.util.SameUserMatching;
import eh.utils.DateConversion;
import eh.utils.LocalStringUtil;
import eh.utils.ValidateUtil;
import eh.wxpay.util.Util;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.hibernate.StatelessSession;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.sql.Timestamp;
import java.util.*;

public abstract class RequestMeetClinicDAO extends
        HibernateSupportDelegateDAO<MeetClinicResult> {
    public static final Logger log = Logger
            .getLogger(RequestMeetClinicDAO.class);

    public RequestMeetClinicDAO() {
        super();
        this.setEntityName(MeetClinicResult.class.getName());
        this.setKeyField("meetClinicResultId");
    }

    /**
     * 添加会诊医生服务（添加执行单）
     *
     * @param meetClinicId
     * @param targetDoctor
     * @param targetOrgan
     * @param targetDepart
     * @return
     * @author LF
     */
    @RpcService
    public Boolean addTargetDoctor(Integer meetClinicId, Integer targetDoctor,
                                   Integer targetOrgan, Integer targetDepart) {
        log.info("添加会诊医生服务（添加执行单）(addTargetDoctor):meetClinicId="
                + meetClinicId + "; targetDoctor=" + targetDoctor
                + ";targetOrgan=" + targetOrgan + ";targetDepart="
                + targetDepart);
        if (meetClinicId == null || targetDoctor == null || targetOrgan == null
                || targetDepart == null) {
            return false;
        }
        MeetClinicDAO meetClinicDAO = DAOFactory.getDAO(MeetClinicDAO.class);
        MeetClinic meetClinic = meetClinicDAO.getByMeetClinicId(meetClinicId);
        // 加Status为0或者1的限制
        if (meetClinic.getMeetClinicStatus() >= 2) {
            return false;
        }
        // 判断该医生是否有被申请过
        EndMeetClinicDAO endMeetClinicDAO = DAOFactory
                .getDAO(EndMeetClinicDAO.class);
        List<MeetClinicResult> meetClinicResults = endMeetClinicDAO
                .findByMeetClinicId(meetClinicId);
        int size = 0;
        for (MeetClinicResult mr : meetClinicResults) {
            Integer effectiveStatus = mr.getEffectiveStatus();
            if (effectiveStatus != null && MeetClinicConstant.EFFECTIVESTATUS_INVALID.equals(effectiveStatus)) {
                continue;
            }
            if (mr.getExeStatus() != null && mr.getExeStatus() <= 2) {
                size++;
            }
        }
        // 会诊医生（包括申请医生）最多6人
        if (size > 4) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "会诊医生至多可以邀请5个");
        }
        for (int i = 0; i < meetClinicResults.size(); i++) {
            Integer effectiveStatus = meetClinicResults.get(i).getEffectiveStatus();
            if (effectiveStatus != null && MeetClinicConstant.EFFECTIVESTATUS_INVALID.equals(effectiveStatus)) {
                continue;
            }
            if (SameUserMatching.targetAndTarget(targetDoctor,
                    meetClinicResults.get(i).getTargetDoctor())) {
                throw new DAOException(609, "不能重复申请同一个医生会诊");
            }
        }
        // 判断患者和目标医生是否为同一人
        if (SameUserMatching.patientAndDoctor(meetClinic.getMpiid(),
                targetDoctor)) {
            throw new DAOException(609, "患者和目标医生不能为同一人");
        }
        // 判断申请医生和目标医生是否为同一人
        if (SameUserMatching.requestAndTarget(meetClinic.getRequestDoctor(),
                targetDoctor)) {
            throw new DAOException(609, "申请医生和目标医生不能为同一人");
        }
        MeetClinicResult meetClinicResult = new MeetClinicResult();
        Double meetClinicCost = meetClinic.getMeetClinicCost();
        EmploymentDAO employmentDAO = DAOFactory.getDAO(EmploymentDAO.class);
        Employment employment = employmentDAO.getByDocAndOrAndDep(targetDoctor,
                targetOrgan, targetDepart);
        if (employment != null) {
            Double meetClinicPrice = employment.getConsultationPrice();// 会诊价格
            Double quickCost = (double) 0;// 加急服务费
            if (meetClinicPrice == null) {
                meetClinicPrice = (double) 0;
            }
            if (meetClinic.getMeetClinicType() == 2) {
                // 加急会诊
                meetClinicCost = meetClinicCost + meetClinicPrice + quickCost;
            } else {
                // 普通会诊
                meetClinicCost = meetClinicCost + meetClinicPrice;
            }
            meetClinicResult.setMeetClinicPrice(meetClinicPrice);
            meetClinicResult.setQuickCost(quickCost);
            meetClinicResult.setExeStatus(0);
            meetClinicResult.setMeetClinicId(meetClinicId);
            // 添加目标医生信息
            meetClinicResult.setTargetDoctor(targetDoctor);
            meetClinicResult.setTargetDepart(targetDepart);
            meetClinicResult.setTargetOrgan(targetOrgan);
            meetClinicResult.setEffectiveStatus(MeetClinicConstant.EFFECTIVESTATUS_VALID);
            // 保存会诊执行单
            meetClinicResult = DAOFactory.getDAO(SaveMeetReportDAO.class).save(meetClinicResult);
            meetClinic.setMeetClinicCost(meetClinicCost);
            meetClinicDAO.update(meetClinic);

            //环信群组加人
            GroupDAO groupDAO = AppDomainContext.getBean("eh.group", GroupDAO.class);
            groupDAO.addUserToGroup(2, meetClinicId, targetDoctor);

            //发送消息
            MeetClinicPushService pushService = AppDomainContext.getBean("eh.meetClinicPushService", MeetClinicPushService.class);
            pushService.addTargetMeetClinicPush(meetClinicResult.getMeetClinicResultId(), meetClinic.getRequestOrgan());

            //加上首页待处理
            Map<String, Object> otherInfo = new HashMap<>();
            List<MeetClinicResult> results = new ArrayList<>();
            results.add(meetClinicResult);
            otherInfo.put("results", results);
            AsynDoBussService asynDoBussService = AppContextHolder.getBean("asynDoBussService", AsynDoBussService.class);
            asynDoBussService.fireEvent(new BussCreateEvent(meetClinic, BussTypeConstant.MEETCLINIC, otherInfo));
        }
        return true;
    }

    /**
     * 会诊申请服务(新增其他病历文档保存)
     *
     * @param mc
     * @param list
     * @return
     * @throws DAOException
     * @author ZX
     * @date 2015-4-12 下午3:44:22
     * 2017-05-25 15:31:52 zhnagsl 抽取重复方法
     */
    @RpcService
    public Boolean requestMeetClinicAndCdrOtherdoc(final MeetClinic mc,
                                                   final List<MeetClinicResult> list, final List<Otherdoc> cdrOtherdocs) {
        return requestMeetClinic(mc, list, cdrOtherdocs) != null;
    }

    /**
     * 会诊申请服务
     *
     * @param mc           会诊申请信息
     * @param list         会诊单信息列表
     * @param cdrOtherdocs 照片信息列表
     * @return Integer 会诊申请单号
     * @throws DAOException
     * @author LF
     * @Date 2016-12-15 14:04:42
     * @author zhangsl
     * app3.8会诊接口改造
     */
    @RpcService
    public Integer requestMeetClinic(final MeetClinic mc,
                                     final List<MeetClinicResult> list, final List<Otherdoc> cdrOtherdocs) {
        log.info("会诊申请服务(requestMeetClinic):mc=" + JSONUtils.toString(mc)
                + ";list=" + JSONUtils.toString(list) + ";cdrOtherdocs="
                + JSONUtils.toString(cdrOtherdocs));
        //app3.8会诊去掉会诊类型和会诊目的字段
        if (mc == null || mc.getMpiid() == null
                //|| mc.getMeetClinicType() == null
                || mc.getRequestOrgan() == null
                || mc.getRequestDepart() == null
                || mc.getRequestDoctor() == null
                || mc.getDiagianCode() == null
                || mc.getDiagianName() == null
                || mc.getPatientCondition() == null
                //|| mc.getLeaveMess() == null
                || mc.getPayflag() == null
                || list.isEmpty()) {
            return null;
        }
        if (mc.getRequestMode() == null) {
            mc.setRequestMode(MeetClinicConstant.MEETREQUESTMODE_DMHZ);
        }
        if (mc.getMeetClinicType() == null) {
            mc.setMeetClinicType(1);
        }

        PatientDAO patDao = DAOFactory.getDAO(PatientDAO.class);
        Patient patient = patDao.getByMpiId(mc.getMpiid());
        //解决旧版本因为wx2.6患者身份证为null，而业务申请不成功
        if (patient == null || StringUtils.isEmpty(patient.getIdcard())) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该患者还未填写身份证信息，不能进行会诊");
        }

        // 入参没有手机号，则后台去取
        if (StringUtils.isEmpty(mc.getAnswerTel())) {
            mc.setAnswerTel(DAOFactory.getDAO(PatientDAO.class)
                    .getMobileByMpiId(mc.getMpiid()));
        }
        // 判断患者和申请医生是否为同一人
        if (SameUserMatching.patientAndDoctor(mc.getMpiid(),
                mc.getRequestDoctor())) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "患者和申请医生不能为同一人");
        }

        // 判断期望时间大于当前时间
        if (mc.getExpectTime() != null && mc.getExpectTime().before(new Date())) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "期望时间需大于当前时间！");
        }

        Integer organId = mc.getRequestOrgan();

        new ObtainImageInfoService(patient, organId).getImageInfo();
        final List<Integer> tarOrganIds = new ArrayList<>();
        HibernateStatelessResultAction<List<MeetClinicResult>> action = new AbstractHibernateStatelessResultAction<List<MeetClinicResult>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                Integer meetClinicType = mc.getMeetClinicType();
                Double meetClinicCost = (double) 0;// 会诊费用
                Timestamp requestTime = new Timestamp(
                        System.currentTimeMillis());
                mc.setRequestTime(requestTime);
                mc.setMeetClinicStatus(0);
                DAOFactory.getDAO(MeetClinicDAO.class).save(mc);

                List<MeetClinicResult> mrList = new ArrayList<>();

                Integer meetClinicId = mc.getMeetClinicId();
                for (int i = 0; i < list.size(); i++) {
                    MeetClinicResult mr = list.get(i);
                    if (mr.getTargetOrgan() == null
                            || mr.getTargetDepart() == null
                            || mr.getTargetDoctor() == null) {
                        throw new DAOException(DAOException.VALUE_NEEDED,
                                "targetOrgan or targetDepart or targetDoctor is required");
                    }
                    // 判断患者和目标医生是否为同一人
                    if (SameUserMatching.patientAndDoctor(mc.getMpiid(),
                            mr.getTargetDoctor())) {
                        throw new DAOException(ErrorCode.SERVICE_ERROR, "患者和目标医生不能为同一人");
                    }
                    // 判断申请医生和目标医生是否为同一人
                    if (SameUserMatching.requestAndTarget(
                            mc.getRequestDoctor(), mr.getTargetDoctor())) {
                        throw new DAOException(ErrorCode.SERVICE_ERROR, "申请医生和目标医生不能为同一人");
                    }
                    mrList.add(mr);
                    EmploymentDAO employmentDAO = DAOFactory
                            .getDAO(EmploymentDAO.class);
                    Employment employment = employmentDAO.getByDocAndOrAndDep(
                            mr.getTargetDoctor(), mr.getTargetOrgan(),
                            mr.getTargetDepart());
                    if (employment != null) {
                        Double meetClinicPrice = employment
                                .getConsultationPrice();// 会诊价格
                        Double quickCost = (double) 0;// 加急服务费
                        if (meetClinicPrice == null) {
                            meetClinicPrice = (double) 0;
                        }
                        if (meetClinicType == 2) {
                            // 加急会诊
                            meetClinicCost = meetClinicCost + meetClinicPrice
                                    + quickCost;
                        } else {
                            // 普通会诊
                            meetClinicCost = meetClinicCost + meetClinicPrice;
                        }
                        mr.setMeetClinicPrice(meetClinicPrice);
                        mr.setQuickCost(quickCost);
                        mr.setExeStatus(0);
                        mr.setMeetClinicId(meetClinicId);
                        mr.setEffectiveStatus(MeetClinicConstant.EFFECTIVESTATUS_VALID);
                        if (mc.getRequestMode() == 2) {
                            //zhangsl 2017-05-25 15:38:45 会诊中心模式新增
                            mr.setMeetCenter(DAOFactory.getDAO(DoctorTabDAO.class).getMeetTypeByDoctorId(mr.getTargetDoctor()));
                            mr.setMeetCenterStatus(0);//待处理
                        }
                        // 保存会诊执行单
                        DAOFactory.getDAO(SaveMeetReportDAO.class).save(mr);
                        if (!tarOrganIds.contains(mr.getTargetOrgan())) {
                            tarOrganIds.add(mr.getTargetOrgan());
                        }
                    }
                }
                MeetClinicDAO meetClinicDAO = DAOFactory
                        .getDAO(MeetClinicDAO.class);
                meetClinicDAO.updateMeetClinicCostByMeetClinicId(
                        meetClinicCost, meetClinicId);
                // 保存图片
                if (!cdrOtherdocs.isEmpty()) {
                    // 获取会诊单号
                    Integer clinicId = mc.getMeetClinicId();
                    DAOFactory.getDAO(CdrOtherdocDAO.class).saveOtherDocList(2,
                            clinicId, cdrOtherdocs);
                }
                setResult(mrList);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
        List<MeetClinicResult> mrList = action.getResult();

        // 获取申请医生姓名
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Integer requestDoctorId = mc.getRequestDoctor();
        Doctor requestDoctor = doctorDAO.getByDoctorId(requestDoctorId);

        // 给申请医生的推荐医生奖励，不考虑是否成功
        DoctorAccountDAO accDao = DAOFactory.getDAO(DoctorAccountDAO.class);
        accDao.recommendReward(requestDoctorId);

        if (mrList == null || mrList.isEmpty()) {
            return null;
        }

        Integer meetClinicId = mrList.get(0).getMeetClinicId();
        //消息推送
        MeetClinicPushService pushService = AppDomainContext.getBean("eh.meetClinicPushService", MeetClinicPushService.class);
        pushService.requestMeetClinicPush(meetClinicId, requestDoctor.getOrgan());

        // 创建群聊
        MeetClinicDAO meetClinicDAO = DAOFactory
                .getDAO(MeetClinicDAO.class);
        GroupDAO groupDAO = DAOFactory.getDAO(GroupDAO.class);
        Group group = groupDAO.creatMeetClinckGroup(meetClinicId);
        MeetClinic meetClinic = meetClinicDAO.get(meetClinicId);
        if (!StringUtils.isEmpty(group.getGroupId())) {
            // mc.setSessionStartTime(requestTime);//会话开始时间
            meetClinicDAO.updateSessionStartTimeByMeetClinicId(
                    meetClinic.getRequestTime(), meetClinicId);
            meetClinicDAO.updateSessionIDByMeetClinicId(group.getGroupId(),
                    meetClinicId);
            EasemobIMService imService = new EasemobIMService();
            // 向医生发送该会诊单病情描述消息
            meetClinic.setSessionID(group.getGroupId());
            String content = packageConsultPDD(meetClinic);
            Map<String, String> ext = Maps.newHashMap();
            ext.put("busId", meetClinicId + "");
            ext.put("busType", "1");    //会诊是 1 咨询可以是2
            ext.put("name", requestDoctor.getName());
            ext.put("groupName", patient.getPatientName() + "的会诊");
            ext.put("avatar", requestDoctor.getPhoto() == null ? null : requestDoctor.getPhoto().toString());
            ext.put("gender", requestDoctor.getGender());
            ext.put("uuid", String.valueOf(UUID.randomUUID()));
            log.info(LocalStringUtil.format("requestMeetClinic send huanxin IM msg to app, parameters: requestDoctor[{}],meetClinic[{}], content[{}], ext[{}]", JSONObject.toJSONString(requestDoctor), JSONObject.toJSONString(meetClinic), content, ext));
            Integer doctorUrt = Util.getUrtForDoctor(requestDoctor.getMobile());
            if (ValidateUtil.notBlankString(content) && ValidateUtil.notNullAndZeroInteger(doctorUrt)) {
                imService.sendMsgToGroupByDoctorUrt(doctorUrt, meetClinic.getSessionID(), content, ext);
                //消息记录至数据库
                ConsultMessageService msgService = AppContextHolder.getBean("eh.consultMessageService", ConsultMessageService.class);
                msgService.doctorSendMsgWithConsultId("", ConsultConstant.BUS_TYPE_MEET, meetClinicId, String.valueOf(MsgTypeEnum.TEXT.getId()), content);
            } else {
                log.error(LocalStringUtil.format("requestMeetClinic urt or content is null, huanxin msg send error. urt[{}], content[{}]", doctorUrt, content));
            }
        }

        AsynDoBussService asynDoBussService = AppContextHolder.getBean("asynDoBussService", AsynDoBussService.class);
        asynDoBussService.fireEvent(new BussCreateEvent(meetClinic, BussTypeConstant.MEETCLINIC));

        if (mc.getRequestMode().equals(MeetClinicConstant.MEETREQUESTMODE_DMHZ)) {//点名会诊
            //机构提醒消息发送
            OrganConfigDAO organConfigDAO = DAOFactory.getDAO(OrganConfigDAO.class);
            List<HashMap<String,Object>> result = new ArrayList<>();
            HashMap<String,Object> map;
            for (Integer tarOrgan : tarOrganIds) {
                map = new HashMap<>();
                //如果有提醒人则发送提醒消息
                String remindMobile = organConfigDAO.getRemindMobileByOrganId(tarOrgan);
                if (StringUtils.isNotBlank(remindMobile)) {
                    map.put("tarOrgan",tarOrgan);
                    map.put("remindMobile",remindMobile);
                    result.add(map);
                }
            }
            pushService.remindMeetClinicPush(meetClinicId, organId, result);
        }

        // 保存日志
        OperationRecordsDAO operationRecordsDAO = DAOFactory
                .getDAO(OperationRecordsDAO.class);
        operationRecordsDAO.saveOperationRecordsForMeetClinic(mc, mrList);

        return meetClinicId;
    }

    private String packageConsultPDD(MeetClinic meetClinic) {
        Patient patient = DAOFactory.getDAO(PatientDAO.class).getByMpiId(meetClinic.getMpiid());
        List<Otherdoc> cdrOtherdocList = DAOFactory.getDAO(CdrOtherdocDAO.class)
                .findByClinicTypeAndClinicId(2, meetClinic.getMeetClinicId());
        String prefix = "text://consultation?json=";
        List<Integer> imgList = new ArrayList<>();
        List<Integer> pdfList = new ArrayList<>();
        if (ValidateUtil.notBlankList(cdrOtherdocList)) {
            for (Otherdoc o : cdrOtherdocList) {
                if (ValidateUtil.notNullAndZeroInteger(o.getDocContent())) {
                    if (OtherdocConstant.DOC_FORMAT_PDF.equals(o.getDocFormat())) {
                        pdfList.add(o.getDocContent());
                    } else {
                        imgList.add(o.getDocContent());
                    }
                }
            }
        }
        Map<String, Object> map = new HashMap<>();
        map.put("id", meetClinic.getMeetClinicId());
        map.put("name", patient.getPatientName());
        map.put("age", DateConversion.getAge(patient.getBirthday()));
        map.put("sex", patient.getPatientSex());
        map.put("desc", meetClinic.getPatientCondition());
        map.put("diagianName", meetClinic.getDiagianName());
        map.put("imgUrl", imgList);
        map.put("pdfUrl", pdfList);
        String jsonString = com.alibaba.druid.support.json.JSONUtils.toJSONString(map);
        try {
            return prefix + URLEncoder.encode(jsonString, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            log.info(LocalStringUtil.format("class[{}] method[{}] encode exception! message[{}]", this.getClass().getSimpleName(), "packageConsultPDD", e.getMessage()));
        }
        return null;
    }

}
