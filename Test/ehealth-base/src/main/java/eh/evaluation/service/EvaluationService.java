package eh.evaluation.service;

import ctd.account.UserRoleToken;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessAction;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.constant.SystemConstant;
import eh.base.dao.DeviceDAO;
import eh.base.dao.DoctorDAO;
import eh.base.dao.OrganDAO;
import eh.bus.constant.ConsultConstant;
import eh.bus.dao.AppointRecordDAO;
import eh.bus.dao.ConsultDAO;
import eh.bus.dao.TransferDAO;
import eh.bus.service.common.CurrentUserInfo;
import eh.bus.service.consult.ConsultMessageService;
import eh.entity.base.Device;
import eh.entity.base.Doctor;
import eh.entity.base.PatientFeedback;
import eh.entity.bus.AppointRecord;
import eh.entity.bus.Consult;
import eh.entity.bus.Transfer;
import eh.entity.bus.msg.SimpleWxAccount;
import eh.entity.evaluation.PatientFeedbackCount;
import eh.entity.msg.SmsInfo;
import eh.entity.wx.WXConfig;
import eh.evaluation.constant.EvaluationConstant;
import eh.evaluation.constant.EvaluationLevelTextEnum;
import eh.evaluation.dao.EvaluationDAO;
import eh.mindgift.dao.MindGiftDAO;
import eh.mindgift.service.MindGiftService;
import eh.mindgift.service.RequestMindGiftService;
import eh.mpi.constant.PatientConstant;
import eh.mpi.dao.PatientDAO;
import eh.msg.dao.SessionMemberDAO;
import eh.msg.service.SystemMsgConstant;
import eh.op.dao.WXConfigsDAO;
import eh.op.dao.WxAppPropsDAO;
import eh.push.SmsPushService;
import eh.utils.DateConversion;
import eh.utils.LocalStringUtil;
import eh.utils.params.ParamUtils;
import eh.utils.params.ParameterConstant;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.StatelessSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;

import java.util.*;


public class EvaluationService {
    private static final Logger logger = LoggerFactory.getLogger(EvaluationService.class);

    /**
     * 根据mpiid获取并组装患者姓名（某**）
     *
     * @author zhangsl 2017-02-13 14:51:56
     */
    private String getPatientFeedName(String mpiid) {
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
//     StringBuffer patientName=new StringBuffer(patientDAO.getNameByMpiId(mpiid).substring(0,1));
        String patientName = new String(patientDAO.getNameByMpiId(mpiid).trim());
        return LocalStringUtil.coverName(patientName);
    }

    @RpcService
    public PatientFeedback getByfeedbackId(Integer feedbackId) {
        EvaluationDAO evaDao = DAOFactory.getDAO(EvaluationDAO.class);
        return evaDao.getById(feedbackId);
    }

    /**
     * 查询未读评价总数
     *
     * @param doctorId 医生内码
     * @return
     */
    @RpcService
    public Long getUnReadCountByServiceTypeAndDoctorId(Integer doctorId) {
        EvaluationDAO evaDao = DAOFactory.getDAO(EvaluationDAO.class);
        return evaDao.getUnReadCountByServiceTypeAndDoctorId(doctorId);
    }

    /**
     * 根据医生内码和用户获取有效评价并装配-分页
     *
     * @param doctorId    医生内码
     * @param serviceType 业务类型 1转诊 2会诊 3咨询 4预约 5检查 6处方
     * @param tabTotem    评价对应的标签
     * @param preUserId   当前登录用户id
     * @param preUserType 当前登录用户类型
     * @param start       开始位置
     * @param limit       每页条数
     * @return
     * @author cuill
     * @Date 2017/5/25
     */
    @RpcService
    public List<PatientFeedback> queryValidEvaluationByDoctorId(Integer doctorId, String tabTotem, String serviceType, Integer preUserId,
                                                                String preUserType, int start, int limit) {
        List<PatientFeedback> resultList = queryFeedbackByDoctorIdAndTabTotemForPatient(doctorId, tabTotem, serviceType, start, limit);
        //更新医生端患者评价系统消息未读数量为0
        if (EvaluationConstant.EVALUATION_USERTYPE_DOCTOR.equals(preUserType) && ObjectUtils.nullSafeEquals(doctorId, preUserId)) {
            SessionMemberDAO memberDAO = DAOFactory.getDAO(SessionMemberDAO.class);
            memberDAO.updateUnReadByPublisherIdAndMemberType(SystemMsgConstant.SYSTEM_MSG_PUBLISH_TYPE_EVALUATION,
                    SystemMsgConstant.SYSTEM_MSG_RECIEVER_TYPE_DOCTOR);
        }
        return resultList;
    }

    /**
     * 根据医生内码和标签图腾获取有效评价并装配-分页
     *
     * @param doctorId    医生内码
     * @param serviceType 业务类型 1转诊 2会诊 3咨询 4预约 5检查 6处方
     * @param tabTotem    评价对应的标签
     * @param start       开始位置
     * @param limit       每页条数
     * @return
     * @author cuill
     * @Date 2017/5/25
     */
    @RpcService
    public List<PatientFeedback> queryFeedbackByDoctorIdAndTabTotemForPatient(Integer doctorId, String tabTotem, String serviceType, int start, int limit) {
        List<PatientFeedback> resultList = new ArrayList<>();
        List<PatientFeedback> feedbackList;
        if (tabTotem.equals(EvaluationConstant.EVALUATION_TAB_ALL)) {
            feedbackList = this.findValidEvaByDoctorIdForSelf(doctorId, start, limit);
        } else {
            feedbackList = this.queryEvaluationByDoctorIdAndTabTotem(doctorId, tabTotem, start, limit);
        }
        for (PatientFeedback feedback : feedbackList) {
            PatientFeedback result;
            //没有无评价内容会分别进行数据处理
            result = this.wrapFeedbackForOther(feedback);
            resultList.add(result);
        }
        return resultList;
    }


    /**
     * 获取全部列表,将有评价内容的放在没有评价内容的上面
     *
     * @param doctorId 医生内码
     * @param start    开始位置
     * @param limit    每页条数
     * @return
     * @author cuill
     * @date 2017/7/6
     */
    private List<PatientFeedback> findValidEvaByDoctorIdForSelf(Integer doctorId, int start, int limit) {
        EvaluationDAO evaDao = DAOFactory.getDAO(EvaluationDAO.class);
        List<PatientFeedback> feedbackList = evaDao.findValidEvaByDoctorIdForSelf(doctorId, start, limit);
        //如果获取有评价内容的为空,则在没有评价内容里面获取
        if (ObjectUtils.isEmpty(feedbackList)) {
            int count = (int) evaDao.getValidEvaCountByDoctorIdForSelf(doctorId);
//            int endStart =  Math.round(count / (float) limit);
            return evaDao.findNoEvaCommentByDoctorId(doctorId, (start - count), limit);
        } else if (feedbackList.size() < limit) {
            List<PatientFeedback> noFeedbackCommentList = evaDao.findNoEvaCommentByDoctorId(doctorId,
                    0, (limit - feedbackList.size()));
            feedbackList.addAll(noFeedbackCommentList);
            return feedbackList;
        } else {
            return feedbackList;
        }
    }


    /**
     * 根据标签获取列表将有评价内容的放在没有评价内容的上面
     *
     * @param doctorId 医生内码
     * @param tabTotem 评价标签图腾
     * @param start    开始位置
     * @param limit    每页条数
     * @return
     * @author cuill
     * @date 2017/7/6
     */
    private List<PatientFeedback> queryEvaluationByDoctorIdAndTabTotem(Integer doctorId, String tabTotem, int start, int limit) {
        EvaluationDAO evaDao = DAOFactory.getDAO(EvaluationDAO.class);
        List<PatientFeedback> feedbackList = evaDao.queryEvaluationByDoctorIdAndTabTotem(doctorId, tabTotem, start, limit);
        //如果获取有评价内容的为空,则在没有评价内容里面获取
        if (ObjectUtils.isEmpty(feedbackList)) {
            int count = (int) evaDao.getEvaCountByDoctorIdAndTabTotem(doctorId, tabTotem);
            return evaDao.queryNoEvaCommentByDoctorIdAndTabTotem(doctorId, tabTotem, (start - count), limit);
        } else if (feedbackList.size() < limit) {
            List<PatientFeedback> noFeedbackCommentList = evaDao.queryNoEvaCommentByDoctorIdAndTabTotem(doctorId, tabTotem,
                    0, (limit - feedbackList.size()));
            feedbackList.addAll(noFeedbackCommentList);
            return feedbackList;
        } else {
            return feedbackList;
        }
    }

    /**
     * 根据医生内码和用户获取有效评价并装配-分页
     *
     * @param doctorId    医生内码
     * @param serviceType 业务类型 1转诊 2会诊 3咨询 4预约 5检查 6处方
     * @param preUserId   当前登录用户id
     * @param preUserType 当前登录用户类型
     * @param start       开始位置
     * @param limit       每页条数
     * @return
     * @author zhangsl
     * @Date 2016-11-15 16:59:01
     */
    @RpcService
    public List<PatientFeedback> findValidEvaByDoctorId(Integer doctorId, String serviceType, Integer preUserId,
                                                        String preUserType, int start, int limit) {
        List<PatientFeedback> resultList = new ArrayList<>();
        List<PatientFeedback> list = null;
        if (EvaluationConstant.EVALUATION_USERTYPE_DOCTOR.equals(preUserType) && doctorId.equals(preUserId)) {
            EvaluationDAO evaDao = DAOFactory.getDAO(EvaluationDAO.class);
            list = evaDao.findValidEvaByDoctorIdForSelf(doctorId, start, limit);
        } else {
            EvaluationDAO evaDao = DAOFactory.getDAO(EvaluationDAO.class);
            list = evaDao.findValidEvaByDoctorIdForOther(doctorId, start, limit);
        }
        for (PatientFeedback feedback : list) {
            PatientFeedback result;
            if (EvaluationConstant.EVALUATION_STATUS_DEFAULT.equals(feedback.getStatus())) {
                //处理默认好评的情况
                result = new PatientFeedback(
                        feedback.getFeedbackId(),
                        feedback.getStatus(),
                        EvaluationConstant.EVALUATION_EVATEXT_DEFAULT,
                        feedback.getEvaValue(),
                        feedback.getEvaDate(),
                        feedback.getFeedbackType(),
                        feedback.getReadFlag(),
                        getPatientFeedName(feedback.getMpiid()));
            } else {
                result = new PatientFeedback(
                        feedback.getFeedbackId(),
                        feedback.getStatus(),
                        //处理无评价内容的情况
                        StringUtils.isBlank(feedback.getFiltText()) ? EvaluationConstant.EVALUATION_EVATEXT_NOTEXT : feedback.getFiltText(),
                        feedback.getEvaValue(),
                        feedback.getEvaDate(),
                        feedback.getFeedbackType(),
                        feedback.getReadFlag(),
                        getPatientFeedName(feedback.getMpiid()));
            }
            resultList.add(result);
        }

        //更新医生端患者评价系统消息未读数量为0
        if (EvaluationConstant.EVALUATION_USERTYPE_DOCTOR.equals(preUserType) && ObjectUtils.nullSafeEquals(doctorId, preUserId)) {
            SessionMemberDAO memberDAO = DAOFactory.getDAO(SessionMemberDAO.class);
            memberDAO.updateUnReadByPublisherIdAndMemberType(SystemMsgConstant.SYSTEM_MSG_PUBLISH_TYPE_EVALUATION,
                    SystemMsgConstant.SYSTEM_MSG_RECIEVER_TYPE_DOCTOR);
        }

        return resultList;
    }

    /**
     * 根据医生内码用户id和服务查询是否评价过
     *
     * @param doctorId
     * @param serviceType
     * @param serviceId
     * @param userId
     * @param userType
     * @return
     * @author zhangsl
     * @Date 2016-11-15 16:59:01
     */
    @RpcService
    public Boolean isEvaluation(Integer doctorId, String serviceType, String serviceId, Integer userId, String userType) {
        EvaluationDAO evaDao = DAOFactory.getDAO(EvaluationDAO.class);
        List<PatientFeedback> feedbacks = evaDao.findEvaByServiceAndUser(doctorId, serviceType, serviceId, userId, userType);
        if (feedbacks == null || feedbacks.size() < 1) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * 获取点评详细，并将医生未读评价标为已读
     *
     * @param feedbackId  点评单id
     * @param preUserId   当前登录用户id
     * @param preUserType 当前登录用户类型
     * @return 点评详细
     * @author cuill
     * @Date 2017/5/25
     */
    @RpcService
    public PatientFeedback getEvaluationById(int feedbackId, Integer preUserId, String preUserType) {
        EvaluationDAO evaluationDAO = DAOFactory.getDAO(EvaluationDAO.class);
        PatientFeedback feedback = evaluationDAO.getById(feedbackId);
        logger.info("根据评价的主键获取的评价详情的评价主键为:[{}]", feedbackId);
        if (feedback == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "no PatientFeedback can find");
        }

        //医生查看对自己的评价,如果自己的评价没有显示已查看,更新为已查看.当没有评价内容的时候会赋值为星级标签的值
        if (EvaluationConstant.EVALUATION_USERTYPE_DOCTOR.equals(preUserType) && feedback.getDoctorId().equals(preUserId)) {
            if (feedback.getReadFlag() == EvaluationConstant.EVALUATION_NOT_READ) {
                feedback.setReadFlag(EvaluationConstant.EVALUATION_IS_READ);
                evaluationDAO.update(feedback);
            }
            return this.wrapFeedbackForOther(feedback);
        }
        //患者查看自己发布的评价
        if (feedback.getUserType().equals(preUserType) && feedback.getUserId().equals(preUserId)) {
            return this.wrapFeedbackForSelf(feedback);
        }

        //包装前端需要的patientFeed到的值,并且将关联评价的评价标签获取并且添加到feedback这个对象中去
        return  this.wrapReturnPatientFeedback(feedback);
    }


    /**
     * 评价页面+新增心意相关内容
     *
     * @param feedbackId  评价单的主键
     * @param preUserId   用户的id
     * @param preUserType 用户的类型, 医生或者患者
     * @return
     */
    @RpcService
    public Map<String, Object> getEvaluationWithGiftById(int feedbackId, Integer preUserId, String preUserType) {

        UserRoleToken urt = UserRoleToken.getCurrent();
        if (urt == null) {
            preUserId = 0;
            preUserType = SystemConstant.ROLES_PATIENT;
        } else {
            preUserId = urt.getId();
            preUserType = urt.getRoleId();
        }

        Map<String, Object> map = new HashMap<>();
        map.put("feedback", getEvaluationById(feedbackId, preUserId, preUserType));

        MindGiftService mindService = AppContextHolder.getBean("eh.mindGiftService", MindGiftService.class);

        Map<String, Object> mindMap = mindService.findMindGiftsByEvaluationId(feedbackId);
        map.putAll(mindMap);

        return map;
    }

    /**
     * 评价页面+新增心意相关内容的数组,为咨询接口所写,返回一条记录
     *
     * @param feedbackId  评价单的主键
     * @param preUserId   用户的id
     * @param preUserType 用户的类型, 医生或者患者
     * @return
     * @author cuill
     * @date 2017/6/19
     */
    @RpcService
    public List<Map<String, Object>> getEvaluationWithGiftByFeedbackId(int feedbackId, Integer preUserId, String preUserType) {
        List<Map<String, Object>> evaluationMapList = new ArrayList<>();
        evaluationMapList.add(this.getEvaluationWithGiftById(feedbackId, preUserId, preUserType));
        return evaluationMapList;
    }

    /**
     * 评价页面+新增心意相关内容的数组,因为可能返回的是两组记录,预约的话是两组记录
     *
     * @param serviceType 业务类型 3 ->咨询 4 ->预约
     * @param serviceId   业务单的主键
     * @param preUserId   用户的id
     * @param preUserType 用户的类型, 医生或者患者
     * @return
     * @author cuill
     * @date 2017/5/25
     */
    @RpcService
    public List<Map<String, Object>> getEvaluationWithGiftByServiceId(String serviceType, String serviceId, Integer preUserId, String preUserType) {

        EvaluationDAO evaluationDAO = DAOFactory.getDAO(EvaluationDAO.class);
        List<Map<String, Object>> evaluationMapList = new ArrayList<>();

        //如果是预约的话会出现两条评价一条是对医生的还有一条是对医院的,咨询的只有一条评价是对医生的
        List<Integer> feedbackIdList = evaluationDAO.queryFeedbackByServiceTypeAndServiceId(serviceType, serviceId);

        logger.info("根据业务类型:[{}]和业务单主键:[{}]获取的评价为[{}],", serviceType, serviceId, JSONUtils.toString(feedbackIdList));

        Iterator<Integer> feedbackIdIterator = feedbackIdList.iterator();
        while (feedbackIdIterator.hasNext()) {
            Integer feedbackId = feedbackIdIterator.next();
            evaluationMapList.add(this.getEvaluationWithGiftById(feedbackId, preUserId, preUserType));
        }
        return evaluationMapList;
    }

    /**
     * 根据医生内码和用户获取有效评价总数和医生总评分
     *
     * @param doctorId    医生内码
     * @param preUserId   当前登录用户id
     * @param preUserType 当前登录用户类型
     * @author zhangsl  @Date 2016-11-15 16:59:01
     * @author cuill  @Date 2017/6/7
     */
    @RpcService
    public Map<String, Object> getEvaNumAndDocRatingByDoctorIdAndUserId(Integer doctorId, Integer preUserId, String preUserType) {
        Map<String, Object> doctorInfoMap = new HashMap<>();
        Long num = null;
        String rating = null;
        DoctorDAO docDao = DAOFactory.getDAO(DoctorDAO.class);
        EvaluationDAO evaDao = DAOFactory.getDAO(EvaluationDAO.class);
        PatientFeedbackCountService feedbackCountService = AppContextHolder.getBean(
                "eh.patientFeedbackCountService", PatientFeedbackCountService.class);

        if (EvaluationConstant.EVALUATION_USERTYPE_DOCTOR.equals(preUserType) && doctorId.equals(preUserId)) {
            num = evaDao.getEvaNumByDoctorIdForSelf(doctorId);
            rating = StringUtils.isEmpty(docDao.getRatingByDoctorId(doctorId)) ? "0.0" : docDao.getRatingByDoctorId(doctorId);
        } else {
            num = evaDao.getEvaNumByDoctorIdForSelf(doctorId);
            rating = docDao.getRatingByDoctorId(doctorId);
        }

        doctorInfoMap.put("evaNum", num == null ? Long.valueOf(0) : num);
        doctorInfoMap.put("rating", rating);
        //将医生的评价标签和对应评价标签的数量加上去
        List<PatientFeedbackCount> patientFeedbackCountList = feedbackCountService.queryFeedbackCountByEvaluationTypeAndId(
                EvaluationConstant.EVALUATION_TYPE_DOCTOR, doctorId);
        doctorInfoMap.put("feedbackCountList", patientFeedbackCountList);

        MindGiftDAO mindGiftDAO = DAOFactory.getDAO(MindGiftDAO.class);
        //心意总数
        doctorInfoMap.put("mindGiftNum", mindGiftDAO.getEffectiveMindGiftsNum(doctorId));
        return doctorInfoMap;
    }


    /**
     * 根据医生内码获取患者端医生主页评价信息,增加了评价标签数量的数组
     *
     * @param doctorId 医生内码
     * @return
     * @author zhangsl
     * @Date 2016-11-17 16:25:19
     * @author cuill modify @date 2017/6/7
     */
    @RpcService
    public Map<String, Object> findEvaInfoByDoctorIdForHealth(Integer doctorId) {

        EvaluationDAO evaDao = DAOFactory.getDAO(EvaluationDAO.class);
        PatientFeedbackCountService feedbackCountService = AppContextHolder.getBean(
                "eh.patientFeedbackCountService", PatientFeedbackCountService.class);

        Map<String, Object> map = new HashMap<>();
        Long num = null;//评价总数
        List<PatientFeedback> feedbackList = this.queryValidEvaluationByDoctorId(doctorId, EvaluationConstant.EVALUATION_TAB_ALL, "",
                null, "", 0, 5);
        num = evaDao.getEvaNumByDoctorIdForSelf(doctorId);
        map.put("evaList", feedbackList);
        map.put("evaNum", num == null ? Long.valueOf(0) : num);
        //将医生的评价标签和对应评价标签的数量加上去
        List<PatientFeedbackCount> patientFeedbackCountList = feedbackCountService.queryFeedbackCountByEvaluationTypeAndIdNotHaveAll(
                EvaluationConstant.EVALUATION_TYPE_DOCTOR, doctorId);
        map.put("feedbackCountList", patientFeedbackCountList);

        return map;
    }


    /**
     * 咨询评价业务
     *
     * @param patientFeedback 评价的对象
     * @return
     * @author cuill
     */
    @RpcService
    public PatientFeedback evaluationConsultForHealths(final PatientFeedback patientFeedback) {
        logger.info("新增一条咨询的评价[{}]", patientFeedback);
        ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
        EvaluationDAO evaluationDAO = DAOFactory.getDAO(EvaluationDAO.class);

        Integer consultId = Integer.parseInt(patientFeedback.getServiceId());
        Consult consult = consultDAO.getById(consultId);
        //判断该咨询单是否合理,并且返回该咨询单的执行医生
        Integer exeDocId = this.judgeConsultValid(consult);
        PatientFeedback initFeedback = initThumbUpData(patientFeedback);
        if (initFeedback == null) {
            return null;
        }
        //不同的业务评价对象不同,给咨询业务评价的对象赋予不同的值
        PatientFeedback validFeedback = this.wrapPatientFeedback(initFeedback, consult.getRequestMpi(), exeDocId, consult.getRequestMode());
        PatientFeedbackRelationTabService feedbackRelationTabService = AppContextHolder.getBean(
                "eh.patientFeedbackRelationTabService", PatientFeedbackRelationTabService.class);

        //增加一条评价记录
        PatientFeedback returnPatientFeedback = evaluationDAO.save(validFeedback);
        Integer feedbackId = returnPatientFeedback.getFeedbackId();
        if (feedbackId == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "咨询单[" + consultId + "]评价插入失败");
        }
        //增加评价和标签关联记录
        feedbackRelationTabService.addFeedbackRelationTabByFeedback(returnPatientFeedback, patientFeedback.getPatientFeedbackRelationTabList());

        //将新插入的评价记录内码发送到评价消息队列,默认好评不需要审核,所以不需要发送
        if (returnPatientFeedback.getStatus() != EvaluationConstant.EVALUATION_STATUS_DEFAULT) {
            this.insertFeedbackToCheckQueue(feedbackId);
        }
        return returnPatientFeedback;
    }


    /**
     * 患者端给医生评价(团队/个人咨询单只给执行医生评价)
     *
     * @param consultId
     * @param evaValue
     * @param evaText
     * @return
     * @author zhangsl
     * @Date 2016-11-15 16:47:59
     */
    @RpcService
    public Boolean evaluationConsultForHealth(Integer consultId, Double evaValue, String evaText) {
        ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
        EvaluationDAO evaDao = DAOFactory.getDAO(EvaluationDAO.class);

        Integer exeDocId = null;
        Consult consult = consultDAO.getById(consultId);
        if (consult == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "不存在该咨询单");
        }
        if (consult.getExeDoctor() == null) {
            logger.error("咨询单[" + consultId + "]无执行医生，不能评价");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "咨询单信息不完整，不能评价");
        } else {
            exeDocId = consult.getExeDoctor();
        }
        if (consult.getConsultStatus() != 2 && consult.getExeDoctor() == null && consult.getRefuseFlag() != null) {
            logger.error("咨询单[" + consultId + "]未完成,不能评价");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "咨询单未完成,不能评价");
        }

        PatientFeedback sigleFeed = initThumbUpData();
        if (sigleFeed == null) {
            return false;
        }

        //给执行医生评价
        sigleFeed.setServiceType(EvaluationConstant.EVALUATION_SERVICETYPE_CONSULT);
        sigleFeed.setServiceId(String.valueOf(consultId));
        sigleFeed.setEvaText(evaText);
        sigleFeed.setEvaValue(evaValue);
        sigleFeed.setMpiid(consult.getRequestMpi());
        sigleFeed.setDoctorId(exeDocId);
        sigleFeed.setFeedbackType(consult.getRequestMode().equals(ConsultConstant.CONSULT_TYPE_POHONE) ? EvaluationConstant.EVALUATION_FEEDBACKTYPE_DHZX :
                consult.getRequestMode().equals(ConsultConstant.CONSULT_TYPE_GRAPHIC) ? EvaluationConstant.EVALUATION_FEEDBACKTYPE_TWZX :
                        consult.getRequestMode().equals(ConsultConstant.CONSULT_TYPE_PROFESSOR) ? EvaluationConstant.EVALUATION_FEEDBACKTYPE_ZJJD :
                                consult.getRequestMode().equals(ConsultConstant.CONSULT_TYPE_RECIPE) ? EvaluationConstant.EVALUATION_FEEDBACKTYPE_XYWY : null);
        PatientFeedback validFeedback = validFeedback(sigleFeed, EvaluationConstant.EVALUATION_STATUS_NON_CHECKED);
        Integer feedbackId = evaDao.addEvaluation(validFeedback);
        if (feedbackId == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "咨询单[" + consultId + "]评价插入失败");
        }
        consultDAO.updateStatusByConsultId(6, consultId);

        //咨询会话页显示评价
        ConsultMessageService msgService = new ConsultMessageService();
        String notificationtext = String.format(EvaluationConstant.NOTIFICATIONTEXT, feedbackId);
        msgService.updateEvaluationNotificationMessage(consultId, evaValue, notificationtext);

        try {
            //此处将新插入的评价记录内码发送至评价审核消息队列
            SensitiveWordsService sensitiveWordsServiceService = AppContextHolder.getBean("eh.sensitiveWordsService", SensitiveWordsService.class);
            Boolean sendOns = sensitiveWordsServiceService.sensitiveMsgDeal(feedbackId);
            if (sendOns) {
                logger.info("evaluation:" + feedbackId + " send to ons success");
            } else {
                logger.error("evaluation:" + feedbackId + " send to ons failed");
            }
        } catch (Exception e) {
            logger.error("evaluation:" + feedbackId + " send to ons failed:" + e.getMessage());
        }
        return true;
    }

    /**
     * 初始化[评价]数据(供evaluationConsultForHealth使用)
     *
     * @author zhangsl
     * @Date 2016-11-15 16:47:59
     */
    private PatientFeedback initThumbUpData() {
        UserRoleToken urt = UserRoleToken.getCurrent();
        if (urt == null) {
            return null;
        }
        Integer urtId = urt.getId();
        String roleId = urt.getRoleId();
        if (!SystemConstant.ROLES_PATIENT.equals(roleId) && !SystemConstant.ROLES_DOCTOR.equals(roleId)) {
            return null;
        }
        PatientFeedback feedback = new PatientFeedback();
        feedback.setEvaDate(new Date());
        feedback.setUserId(urtId);
        feedback.setUserType(roleId);
        feedback.setStatus(EvaluationConstant.EVALUATION_STATUS_NON_CHECKED);
        feedback.setReadFlag(EvaluationConstant.EVALUATION_NOT_READ);
        feedback.setIsDel(EvaluationConstant.EVALUATION_NOT_DEL);
        return feedback;
    }

    /**
     * 计算评分方法供敏感词处理后调用
     * 事务控制
     * <p>
     * 2017-4-20 21:01:18  zhangx  健康3.0-3星及3星以下的评价，先通过运营平台人工审核，审核通过后再放开；
     * 修改后效果：针对3星及3星以下的评价，患者提交后能在聊天页面，业务单的评价页面，查看评价
     * 以下操作在运营平台审核通过以后才可执行： 计算医生的评分，健康端医生主页的评价，医生的系统消息页面关于评价的系统消息，个人中心的患者评价页面
     * <p>
     * 2017/6/1 wx3.1 @author cuill 当审核通过的时候,base_patientfeedback_count,增加插入数据
     * <p/>
     *
     * @author zhangsl
     * @Date 2016-11-15 16:47:59
     */
    public void countEvaValueForONS(final PatientFeedback feedback) {
        final HibernateStatelessAction action = new HibernateStatelessAction() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                EvaluationDAO evaDao = DAOFactory.getDAO(EvaluationDAO.class);
                countEvaValueToDoc(feedback);
                logger.info("审核成功的评价主键为[{}]为:", feedback.getFeedbackId());
                evaDao.updateStatusById(EvaluationConstant.EVALUATION_STATUS_BEEN_CHECKED, feedback.getFeedbackId());
                //审核通过后插入标签数量统计 weixin3.1 @author cuill @date 2017/6/1
                PatientFeedbackCountService feedbackCountService = AppContextHolder.getBean(
                        "eh.patientFeedbackCountService", PatientFeedbackCountService.class);
                feedbackCountService.addFeedbackRelationCountByFeedbackId(feedback.getFeedbackId());
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
        //敏感词处理后给医生推送系统消息
        this.pushMessageToDoctorEvaluationInfo(feedback.getFeedbackId(), feedback.getServiceType(), feedback.getDoctorId());//咨询类型评价
    }


    /**
     * 根据评价记录计算医生评分
     *
     * @author zhangsl
     * @Date 2016-12-06 13:47:09
     * 优化评分计算方式
     */
    public void countEvaValueToDoc(PatientFeedback feedback) {
        /*EvaluationDAO dao = DAOFactory.getDAO(EvaluationDAO.class);
        //判断是否要更新医生总评分(暂不做入)
        Integer userTimes=0;
        Date nowDate=new Date();
        Date startCountDate=DateConversion.getCurrentDate(EvaluationConstant.EVALUATION_STARTCOUNTDATE,"yyyy-MM-dd");//设定默认好评定时任务第一次执行时间，之后可删除
        if(DateConversion.getDaysBetween(nowDate,startCountDate)>0&&feedback.getStatus()==EvaluationConstant.EVALUATION_STATUS_DEFAULT) {
            Integer doctorId = feedback.getDoctorId();
            Integer userId = EvaluationConstant.EVALUATION_USERID_SYSTEM;
            String userType = EvaluationConstant.EVALUATION_USERTYPE_SYSTEM;
            Date startDate = DateConversion.getDaysAgo(EvaluationConstant.CYCLE_DEFALUT);
            userTimes =  dao.getSameNumByDoctorIdAndUserId(doctorId, userId, userType, startDate).intValue();
        }
        //小于限制次数更新评分
        if (userTimes < EvaluationConstant.REPEAT_TIMES) {*/
        DoctorDAO doctorDao = DAOFactory.getDAO(DoctorDAO.class);
        Doctor d = doctorDao.getByDoctorId(feedback.getDoctorId());

        double evaValue = feedback.getEvaValue();
        if (d.getRating() == null || d.getEvaNum() == null || d.getEvaNum() == 0 || d.getRating() == 0) {

            doctorDao.updateRating(feedback.getDoctorId(),
                    evaValue, 1, d.getRating(), d.getEvaNum());
        } else {
            EvaluationDAO evaDao = DAOFactory.getDAO(EvaluationDAO.class);
            Long countOne = evaDao.getEvaCountByDoctorIdAndEvaValue(feedback.getDoctorId(), EvaluationConstant.EVALUATION_EVAVALUE_ONE);
            Long countTwo = evaDao.getEvaCountByDoctorIdAndEvaValue(feedback.getDoctorId(), EvaluationConstant.EVALUATION_EVAVALUE_TWO);
            Long countThree = evaDao.getEvaCountByDoctorIdAndEvaValue(feedback.getDoctorId(), EvaluationConstant.EVALUATION_EVAVALUE_THREE);
            Long countFour = evaDao.getEvaCountByDoctorIdAndEvaValue(feedback.getDoctorId(), EvaluationConstant.EVALUATION_EVAVALUE_FOUR);
            Long countFive = evaDao.getEvaCountByDoctorIdAndEvaValue(feedback.getDoctorId(), EvaluationConstant.EVALUATION_EVAVALUE_FIVE);
            Integer countNum = (int) (countOne + countTwo + countThree + countFour + countFive);
            Double newRating = Math.round(((countOne * EvaluationConstant.EVALUATION_EVAVALUE_ONE + countTwo * EvaluationConstant.EVALUATION_EVAVALUE_TWO
                    + countThree * EvaluationConstant.EVALUATION_EVAVALUE_THREE + countFour * EvaluationConstant.EVALUATION_EVAVALUE_FOUR
                    + countFive * EvaluationConstant.EVALUATION_EVAVALUE_FIVE) / (countNum * 1.0)) * 10) / 10.0;
            //Double newRating = ((evaValue + d.getRating() * d.getEvaNum()) / (d.getEvaNum() + 1));
            doctorDao.updateRating(feedback.getDoctorId(), newRating,
                    countNum, d.getRating(), d.getEvaNum());
        }
        //}
    }


    /**
     * 向医生推送患者评价信息
     *
     * @param serviceType 评价业务类型 1转诊 2会诊 3咨询 4预约 5检查 6处方
     * @param doctorId    接受医生
     * @date 2016-11-19
     * @author houxr
     * @author zhangsl
     * @Date 2017-02-15 15:44:02
     * 业务扩展
     */
    @RpcService
    public void pushMessageToDoctorEvaluationInfo(final Integer feedbackId, final String serviceType, final Integer doctorId) {
        EvaluationDAO evaluationDAO = DAOFactory.getDAO(EvaluationDAO.class);
        Integer unReadcount = evaluationDAO.getUnReadCountByServiceTypeAndDoctorId(doctorId).intValue();
        PatientFeedback patientFeedback = evaluationDAO.get(feedbackId);
        if (patientFeedback != null && unReadcount > 0) {
            //发送消息通知
            Integer organId = 0;
            if (EvaluationConstant.EVALUATION_SERVICETYPE_CONSULT.equals(serviceType)) {
                ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
                Consult consult = consultDAO.getById(Integer.parseInt(patientFeedback.getServiceId()));
                if (consult == null) {
                    logger.error("PatientFeedback[" + feedbackId + "] can not find consult");
                    return;
                }
                organId = consult.getExeOrgan() == null ? 0 : consult.getExeOrgan();
            }
            if (EvaluationConstant.EVALUATION_SERVICETYPE_APPOINTRECORD.equals(serviceType)) {
                AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
                AppointRecord appointRecord = appointRecordDAO.getByAppointRecordId(Integer.parseInt(patientFeedback.getServiceId()));
                if (appointRecord == null) {
                    logger.error("PatientFeedback[" + feedbackId + "] can not find appointRecord");
                    return;
                }
                organId = appointRecord.getOrganId() == null ? 0 : appointRecord.getOrganId();
            }
            if (EvaluationConstant.EVALUATION_SERVICETYPE_TRANSFER.equals(serviceType)) {
                TransferDAO transferDAO = DAOFactory.getDAO(TransferDAO.class);
                Transfer transfer = transferDAO.getById(Integer.parseInt(patientFeedback.getServiceId()));
                if (transfer == null) {
                    logger.error("PatientFeedback[" + feedbackId + "] can not find consult");
                    return;
                }
                organId = transfer.getConfirmOrgan() == null ? 0 : transfer.getConfirmOrgan();
            }
            Integer clientId = null;
            SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
            smsPushService.pushMsgData2Ons(feedbackId, organId, "EvaluationMsg", "EvaluationMsg", clientId);
            logger.info("PushEvaluationToDoc:" + JSONUtils.toString(patientFeedback));
            /*String msg = "您有一条新的评价，请及时查看~";
            String title = "患者评价";
            String detailMsg = "收到新的评价";
            msgPushEvaluationToDoctor(patientFeedback, msg, title, detailMsg, unReadcount);*/
        }
    }


    /**
     * 对超过7天未评价的已完成业务单默认好评，直接写入评价表数据计算评分无需进行敏感词处理
     *
     * @throws Exception
     * @author houxr
     * @Date 2016-11-16 下午3:22:42
     * @author zhangsl 2017-02-15 15:14:09
     * @author cuill
     * @date 2017/6/6 转诊和预约增加对医院的默认好评
     * 业务扩展
     */
    @RpcService
    public void evaluationOvertimeDefaultGoodForSchedule() throws Exception {
        Date currentDate = new Date();
        int areaNum = -5; //区间长度

        Integer advanceDay = Integer.parseInt(ParamUtils.getParam(ParameterConstant.KEY_EVALUATION_ADVANCE_DAY, "7"));
        int day = -advanceDay.intValue(); //默认-7

        // 当前时间往前推7天
        final Date sevenDate = DateConversion.getDateAftXDays(currentDate, day);
//        final Date sevenDate = DateConversion.getDateAftXDays(currentDate, -1);
        final Date startDate = DateConversion.getCurrentDate(EvaluationConstant.EVALUATION_STARTCOUNTDATE, "yyyy-MM-dd")
                .getTime() > DateConversion.getDateAftXDays(sevenDate, areaNum).getTime() ?
                DateConversion.getCurrentDate(EvaluationConstant.EVALUATION_STARTCOUNTDATE, "yyyy-MM-dd") :
                DateConversion.getDateAftXDays(sevenDate, areaNum); //每次只取一个区间内的数据
        //咨询默认好评
        ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
        EvaluationDAO evaDao = DAOFactory.getDAO(EvaluationDAO.class);
        List<String> consultIds = consultDAO.findConsultIdSevenDayAgo(sevenDate, startDate);
        List<String> serviceIds = evaDao.findPatientFeedbackByServiceType(EvaluationConstant.EVALUATION_SERVICETYPE_CONSULT);
        if (consultIds != null && consultIds.removeAll(serviceIds)) {
            for (String consultId : consultIds) {
                try {
                    this.publishEvaluationByTypeAndIdForSchedule(EvaluationConstant.EVALUATION_SERVICETYPE_CONSULT, consultId);
                } catch (Exception e) {
                    logger.error("consult[{}] evaluationOvertimeDefaultGood error\n[{}]", consultId, e);
                }
            }
        }
        //预约默认好评
        AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
        List<Integer> appointRecordIds = appointRecordDAO.findOverTimeNeedEvaAppointByWorkDate(sevenDate, startDate);
        if (appointRecordIds != null) {
            for (Integer appointRecordId : appointRecordIds) {
                try {
                    this.publishEvaluationByTypeAndIdForSchedule(EvaluationConstant.EVALUATION_SERVICETYPE_APPOINTRECORD,
                            appointRecordId.toString());
                } catch (Exception e) {
                    logger.error("appoint[{}] evaluationOvertimeDefaultGood error\n[{}]", appointRecordId, e);
                }
            }
        }
        //转诊默认好评
        TransferDAO transferDAO = DAOFactory.getDAO(TransferDAO.class);
        List<Integer> transferIds = transferDAO.findOverTimeNeedEvaTransferByWorkDate(sevenDate, startDate);
        if (transferIds != null) {
            for (Integer transferId : transferIds) {
                try {
                    this.publishEvaluationByTypeAndIdForSchedule(EvaluationConstant.EVALUATION_SERVICETYPE_TRANSFER,
                            transferId.toString());
                } catch (Exception e) {
                    logger.error("transfer[{}] evaluationOvertimeDefaultGood error\n[{}]", transferId, e);
                }
            }
        }
    }

    /**
     * 根据业务类型和业务单号发布默认评价
     *
     * @param serviceType 业务类型
     * @param serviceId   业务类型对应的业务单主键
     * @author cuill
     * @date 2017/6/6
     */
    private void publishEvaluationByTypeAndIdForSchedule(String serviceType, String serviceId) {

        List<PatientFeedback> feedbackList = new ArrayList<>();
        if (serviceType.equals(EvaluationConstant.EVALUATION_SERVICETYPE_CONSULT)) {
            feedbackList.add(this.wrapFeedbackForSchedule(serviceType, serviceId, EvaluationConstant.EVALUATION_TYPE_DOCTOR));
        } else if (serviceType.equals(EvaluationConstant.EVALUATION_SERVICETYPE_APPOINTRECORD)
                || serviceType.equals(EvaluationConstant.EVALUATION_SERVICETYPE_TRANSFER)) {
            feedbackList.add(this.wrapFeedbackForSchedule(serviceType, serviceId, EvaluationConstant.EVALUATION_TYPE_DOCTOR));
            feedbackList.add(this.wrapFeedbackForSchedule(serviceType, serviceId, EvaluationConstant.EVALUATION_TYPE_ORGAN));
        }
        this.publishEvaluationsByServiceType(feedbackList);
    }

    /**
     * 根据业务类型,业务单号和评价对象类型初始化默认好评发布参数
     *
     * @param serviceType    业务类型
     * @param serviceId      业务单号
     * @param evaluationType 评价对象类型
     * @return
     * @author cuill
     * @date 2017/6/6
     */
    private PatientFeedback wrapFeedbackForSchedule(String serviceType, String serviceId, Integer evaluationType) {
        PatientFeedback patientFeedback = new PatientFeedback();
        patientFeedback.setStatus(EvaluationConstant.EVALUATION_STATUS_DEFAULT);
        patientFeedback.setEvaluationType(evaluationType);
        patientFeedback.setServiceType(serviceType);
        patientFeedback.setEvaValue(EvaluationConstant.EVALUATION_VALUE_DEFAULT);
        patientFeedback.setEvaText("");
        patientFeedback.setServiceId(serviceId);
        return patientFeedback;
    }

    /**
     * 增加评价数据校验
     *
     * @param patientFeedback
     * @param validType       校验类型
     * @return
     * @author zhangsl
     * @Date 2016-11-15 16:59:01
     */
    public PatientFeedback validFeedback(PatientFeedback patientFeedback, Integer validType) {
        if (patientFeedback.getDoctorId() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "doctorId is required");
        }
        if (StringUtils.isEmpty(patientFeedback.getMpiid())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "mpiid is required");
        }
        if (StringUtils.isEmpty(patientFeedback.getServiceId())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "serviceId is required");
        }
        if (StringUtils.isEmpty(patientFeedback.getServiceType())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "serviceType is required");
        }
        if (patientFeedback.getUserId() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "userId is required");
        }
        if (StringUtils.isEmpty(patientFeedback.getUserType())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "userType is required");
        }
        if (patientFeedback.getEvaValue() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "evaValue is required");
        }
        if (patientFeedback.getFeedbackType() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "feedbackType is required");
        }
        if (validType != EvaluationConstant.EVALUATION_STATUS_DEFAULT) {
            if (isEvaluationForService(patientFeedback.getDoctorId(),
                    patientFeedback.getServiceType(),
                    patientFeedback.getServiceId(),
                    patientFeedback.getUserId(),
                    patientFeedback.getUserType(),
                    patientFeedback.getEvaluationType())) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "你已评价该医生");
            }
        }
        if (patientFeedback.getStatus() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "status is required");
        }
        if (patientFeedback.getEvaDate() == null) {
            patientFeedback.setEvaDate(new Date());
        }
        if (StringUtils.isBlank(patientFeedback.getEvaText())) {
            patientFeedback.setEvaText("");
        }
        return patientFeedback;
    }

    /**
     * 当天就诊的预约转诊单状态患者端置为待评价或已评价（不包含无平台帐号的患者数据）
     *
     * @author zhangsl 2017-02-13 14:53:24
     */
    @RpcService
    public Boolean transforEvaStatusForSchedule() {
        final HibernateStatelessAction action = new HibernateStatelessAction() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
                TransferDAO transferDAO = DAOFactory.getDAO(TransferDAO.class);
                Date workDate = DateConversion.getDaysAgo(1);
//                Date workDate=new Date();
                appointRecordDAO.updateAppointEvaStatusByWorkDate(workDate);
                transferDAO.updateTransferEvaStatusByWorkDate(workDate);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
        return true;
    }

    /**
     * 就诊日第二天微信推送评价通知（供定时器调用）
     *
     * @author zhangsl 2017-02-13 18:34:04
     */
    @RpcService
    public void pushNotifyEvaMessagetoPatientForSchedule() {
        AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
        TransferDAO transferDAO = DAOFactory.getDAO(TransferDAO.class);
        Date oneDayAgo = DateConversion.getFormatDate(DateConversion.getDaysAgo(1), DateConversion.YYYY_MM_DD);
        List<AppointRecord> appointRecords = appointRecordDAO.findNeedEvaAppointByWorkDate(oneDayAgo);
        List<Transfer> transfers = transferDAO.findNeedEvaTransferByWorkDate(DateConversion.getDaysAgo(1));
        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        DeviceDAO deviceDAO = DAOFactory.getDAO(DeviceDAO.class);
        SmsInfo smsInfo = new SmsInfo();
        smsInfo.setBusType("EvaNotifyForPatMsg");
        smsInfo.setSmsType("EvaNotifyForPatMsg");
        smsInfo.setExtendValue(EvaluationConstant.EVALUATION_SERVICETYPE_APPOINTRECORD);
        for (AppointRecord ar : appointRecords) {
            if (ar.getAppointUser().length() == 32 && ar.getDeviceId() != null) {//患者预约
                smsInfo.setClientId(ar.getDeviceId());
            } else {
                String requestMpiid = ar.getAppointUser().length() == 32 ? ar.getAppointUser() : ar.getMpiid();
                String ptUserId = patientDAO.getLoginIdByMpiId(requestMpiid);
                if (StringUtils.isNotBlank(ptUserId)) {
                    Device ptDevice = deviceDAO.getLastWxLoginDevice(ptUserId, EvaluationConstant.EVALUATION_USERTYPE_PATIENT);
                    if (ptDevice != null) {
                        smsInfo.setClientId(ptDevice.getId());
                    } else {
                        logger.error("mpiid[{}] in appointRecordId[{}] can not get deviceId for pushNotifyEvaMessagetoPatientForSchedule", requestMpiid, ar.getAppointRecordId());
                        continue;
                    }
                } else {//患者在平台没有帐号则不发送
                    continue;
                }
            }
            smsInfo.setBusId(ar.getAppointRecordId());
            smsInfo.setOrganId(ar.getOrganId() == null ? 0 : ar.getOrganId());
            smsPushService.pushMsgData2OnsExtendValue(smsInfo);
        }
        smsInfo.setExtendValue(EvaluationConstant.EVALUATION_SERVICETYPE_TRANSFER);
        for (Transfer tr : transfers) {
            if (tr.getRequestMpi() != null && tr.getDeviceId() != null) {//特需预约
                smsInfo.setClientId(tr.getDeviceId());
            } else {
                String requestMpiid = tr.getRequestMpi() != null ? tr.getRequestMpi() : tr.getMpiId();
                String ptUserId = patientDAO.getLoginIdByMpiId(requestMpiid);
                if (StringUtils.isNotBlank(ptUserId)) {
                    Device ptDevice = deviceDAO.getLastWxLoginDevice(ptUserId, EvaluationConstant.EVALUATION_USERTYPE_PATIENT);
                    if (ptDevice != null) {
                        smsInfo.setClientId(ptDevice.getId());
                    } else {
                        logger.error("mpiid[{}] in transferId[{}] can not get deviceId for pushNotifyEvaMessagetoPatientForSchedule", requestMpiid, tr.getTransferId());
                        continue;
                    }
                } else {
                    continue;
                }
            }
            smsInfo.setBusId(tr.getTransferId());
            smsInfo.setOrganId(tr.getConfirmOrgan() == null ? 0 : tr.getConfirmOrgan());
            smsPushService.pushMsgData2OnsExtendValue(smsInfo);
        }
        logger.info("pushNotifyEvaMessagetoPatient has send to ons");
    }

    /**
     * 按业务类型发布评价
     *
     * @author zhangsl
     */
    @RpcService
    public Boolean publishEvaluationByServiceType(Integer serviceType, Integer serviceId, Double evaValue, String evaText) {
        switch (serviceType) {
            case 3://咨询
                return evaluationConsultForHealth(serviceId, evaValue, evaText);
            case 1://转诊（特需预约）
                return evaluationTransferForHealth(serviceId, evaValue, evaText);
            case 4://预约
                return evaluationAppointForHealth(serviceId, evaValue, evaText);
            default:
                break;

        }
        return true;
    }


    /**
     * 按业务类型发表评价,现在是处理咨询和预约业务
     *
     * @param patientFeedback 前端传给的评价的评价对象
     * @return
     * @author cuill
     */
    @RpcService
    public PatientFeedback publishEvaluationByServiceTypes(PatientFeedback patientFeedback) {
        switch (patientFeedback.getServiceType()) {
            case EvaluationConstant.EVALUATION_SERVICETYPE_CONSULT://咨询
                return evaluationConsultForHealths(patientFeedback);
            case EvaluationConstant.EVALUATION_SERVICETYPE_APPOINTRECORD://预约
                return evaluationAppointForHealths(patientFeedback);
            case EvaluationConstant.EVALUATION_SERVICETYPE_TRANSFER: //转诊
                return evaluationTransferForHealths(patientFeedback);
            default:
                break;
        }
        return null;
    }

    /**
     * 按业务类型发布评价 并返回告知当前业务单是否能够送心意
     *
     * @param serviceType
     * @param serviceId
     * @param evaValue
     * @param evaText
     * @return
     */
    @RpcService
    public Map<String, Object> publishEvaluationByServiceTypeWithGift(Integer serviceType, Integer serviceId, Double evaValue, String evaText) {


        Map<String, Object> map = new HashMap<String, Object>();
        //表示评价提交是否成功状态
        map.put("status", publishEvaluationByServiceType(serviceType, serviceId, evaValue, evaText));


        //当前业务单是否可以送心意(包括机构是否可送，业务单是否可送)
        try {
            RequestMindGiftService reqMindGiftService = AppContextHolder.getBean("eh.requestMindGiftService", RequestMindGiftService.class);
            Boolean canSendGift = reqMindGiftService.canSendGiftByBus(serviceType, serviceId);
            map.put("canSendGift", canSendGift);
        } catch (Exception e) {
            map.put("canSendGift", false);
            logger.error("评价提交-查询当前评价单是否支持送心意[false]" + e.getMessage());
        }

        return map;
    }


    /**
     * 根据不同的业务类型来发布评价
     *
     * @param patientFeedbackList 从前端获取的评价数组
     * @return
     * @author cuill
     * @date @2017/6/6
     */
    private PatientFeedback publishEvaluationsByServiceType(final List<PatientFeedback> patientFeedbackList) {

        final Iterator<PatientFeedback> patientFeedbackIterator = patientFeedbackList.iterator();
        final HibernateStatelessResultAction<PatientFeedback> action = new AbstractHibernateStatelessResultAction<PatientFeedback>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                PatientFeedback patientFeedback;
                PatientFeedback returnFeedback = null;
                while (patientFeedbackIterator.hasNext()) {
                    patientFeedback = patientFeedbackIterator.next();
                    returnFeedback = publishEvaluationByServiceTypes(patientFeedback);
                }
                if (!patientFeedbackIterator.hasNext() && returnFeedback != null) {

                    //评价记录插入完了以后,对于预约业务更新预约单的信息
                    updateServiceAfterEvalution(returnFeedback);
                }
                setResult(returnFeedback);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
        return action.getResult();
    }

    /**
     * 根据不同的业务类型来发布评价
     *
     * @param patientFeedbackList 从前端获取的评价数组
     * @return
     * @author cuill
     * @date 2017/5/24
     */
    @RpcService
    public Map<String, Object> publishEvaluationsByServiceTypeWithGift(final List<PatientFeedback> patientFeedbackList) {

        logger.info("发布评价传入的值为[{}]", patientFeedbackList);
        Map<String, Object> returnMap = new HashMap<>();
        boolean evaluationFlag = false;

        //根据业务类型发布评价,但是不更新业务单信息
        PatientFeedback patientFeedback = this.publishEvaluationsByServiceType(patientFeedbackList);

        if (!ObjectUtils.isEmpty(patientFeedback)) {
            evaluationFlag = true;
        }
        returnMap.put("status", evaluationFlag);

        //如果是对医生的评价判断能不能送心意,当前业务单是否可以送心意(包括机构是否可送，业务单是否可送)
        try {
            RequestMindGiftService reqMindGiftService = AppContextHolder.getBean("eh.requestMindGiftService", RequestMindGiftService.class);
            Boolean canSendGift = reqMindGiftService.canSendGiftByBus(Integer.parseInt(patientFeedback.getServiceType()),
                    Integer.parseInt(patientFeedback.getServiceId()));
            returnMap.put("canSendGift", canSendGift);
        } catch (Exception e) {
            returnMap.put("canSendGift", false);
            logger.error("评价提交-查询当前评价单是否支持送心意[false]" + e.getMessage());
        }
        return returnMap;
    }

    /**
     * 将评价插入到评价表中去以后,更新业务单
     *
     * @param patientFeedback 评价插入成功后返回的评价对象
     * @author cuill
     * @date 2017/6/1
     */
    private void updateServiceAfterEvalution(PatientFeedback patientFeedback) {
        Integer serviceId = Integer.parseInt(patientFeedback.getServiceId());
        //更新预约单的状态
        if (EvaluationConstant.EVALUATION_SERVICETYPE_APPOINTRECORD.equals(patientFeedback.getServiceType())) {
            AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
            if (appointRecordDAO.updateAppointEvaStatusById(serviceId) < 1) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "预约单[" + serviceId + "]评价状态更新失败");
            }

            //咨询完成,默认评价后给医生推送系统消息
            if (EvaluationConstant.EVALUATION_STATUS_DEFAULT.equals(patientFeedback.getStatus())) {
                this.pushMessageToDoctorEvaluationInfo(patientFeedback.getFeedbackId(),
                        EvaluationConstant.EVALUATION_SERVICETYPE_APPOINTRECORD, patientFeedback.getDoctorId());//咨询类型评价
            }
        } else if (EvaluationConstant.EVALUATION_SERVICETYPE_TRANSFER.equals(patientFeedback.getServiceType())) {
            TransferDAO transferDAO = DAOFactory.getDAO(TransferDAO.class);
            if (transferDAO.updateTransferEvaStatusById(serviceId) < 1) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "转诊单[" + serviceId + "]评价状态更新失败");
            }

            //咨询完成,默认评价后给医生推送系统消息
            if (EvaluationConstant.EVALUATION_STATUS_DEFAULT.equals(patientFeedback.getStatus())) {
                this.pushMessageToDoctorEvaluationInfo(patientFeedback.getFeedbackId(),
                        EvaluationConstant.EVALUATION_SERVICETYPE_APPOINTRECORD, patientFeedback.getDoctorId());//咨询类型评价
            }
        } else if (EvaluationConstant.EVALUATION_SERVICETYPE_CONSULT.equals(patientFeedback.getServiceType())) {
            //更新咨询单评价的状态
            ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
            consultDAO.updateStatusByConsultId(ConsultConstant.CONSULT_STATUS_HAVING_EVALUATION, serviceId);

            //咨询会话页显示评价,在咨询聊天会话中插入一条聊天记录
            ConsultMessageService consultMessageService = AppContextHolder.getBean("eh.consultMessageService", ConsultMessageService.class);
            String notificationText = String.format(EvaluationConstant.NOTIFICATIONTEXT, patientFeedback.getFeedbackId());
            consultMessageService.updateEvaluationNotificationMessage(serviceId, patientFeedback.getEvaValue(), notificationText);

            //咨询完成,默认评价后给医生推送系统消息
            if (EvaluationConstant.EVALUATION_STATUS_DEFAULT.equals(patientFeedback.getStatus())) {
                this.pushMessageToDoctorEvaluationInfo(patientFeedback.getFeedbackId(),
                        EvaluationConstant.EVALUATION_SERVICETYPE_CONSULT, patientFeedback.getDoctorId());//咨询类型评价
            }
        }
    }

    /**
     * 患者端评价预约单
     *
     * @param patientFeedback 获取的评价列表
     * @return
     * @author cuill
     * @Date 2017/5/24
     */
    private PatientFeedback evaluationAppointForHealths(final PatientFeedback patientFeedback) {

        AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
        EvaluationDAO evaluationDAO = DAOFactory.getDAO(EvaluationDAO.class);
        PatientFeedbackRelationTabService feedbackRelationTabService = AppContextHolder.getBean(
                "eh.patientFeedbackRelationTabService", PatientFeedbackRelationTabService.class);

        Integer appointRecordId = Integer.parseInt(patientFeedback.getServiceId());
        AppointRecord appointRecord = appointRecordDAO.getByAppointRecordId(appointRecordId);

        Integer exeDocId = judgeAppointRecordValid(appointRecord); //判断预约单是否已经完成,有效,可以被评价

        //如果是对医院的评价,那么evaluationId就是医院的机构号,如果是医生的话,evaluationId就是医生的主键
        if (patientFeedback.getEvaluationType() != null
                && patientFeedback.getEvaluationType() == EvaluationConstant.EVALUATION_TYPE_ORGAN) {
            patientFeedback.setEvaluationTypeId(appointRecord.getOrganId());
        }
        //初始化插入评价的对象
        PatientFeedback initFeedback = initThumbUpData(patientFeedback);
        if (initFeedback == null) {
            return null;
        }

        //包装插入数据库的评价对象,并且判断该业务单是否可以评价
        String mpiId = appointRecord.getAppointUser().length() == PatientConstant.PATIENT_MPIID_LENGTH ? appointRecord.getAppointUser() : appointRecord.getMpiid();
        PatientFeedback validFeedback = this.wrapPatientFeedback(initFeedback, mpiId, exeDocId, initFeedback.getFeedbackType());

        PatientFeedback returnPatientFeedback = evaluationDAO.save(validFeedback);

        //增加评价和标签关联记录
        feedbackRelationTabService.addFeedbackRelationTabByFeedback(returnPatientFeedback, patientFeedback.getPatientFeedbackRelationTabList());
        Integer feedbackId = returnPatientFeedback.getFeedbackId();
        if (feedbackId == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "预约单[" + appointRecordId + "]评价插入失败");
        }

        //将新插入的评价记录内码发送到评价消息队列,默认好评不需要审核,所以不需要发送
        if (!EvaluationConstant.EVALUATION_STATUS_DEFAULT.equals(returnPatientFeedback.getStatus())) {
            this.insertFeedbackToCheckQueue(feedbackId);
        }
        return returnPatientFeedback;
    }


    /**
     * 患者端评价预约单
     *
     * @param appointRecordId
     * @param evaValue
     * @param evaText
     * @return
     * @author zhangsl
     * @Date 2017-02-14 16:19:36
     */
    public Boolean evaluationAppointForHealth(final Integer appointRecordId, Double evaValue, String evaText) {
        AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
        AppointRecord appointRecord = appointRecordDAO.getByAppointRecordId(appointRecordId);
        Integer doctorId = null;
        if (appointRecord == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "不存在该预约单");
        }
        if (appointRecord.getDoctorId() == null) {
            logger.error("预约单[" + appointRecordId + "]信息不完整，不能评价");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "预约单信息不完整，不能评价");
        } else {
            doctorId = appointRecord.getDoctorId();
        }
        if (appointRecord.getAppointStatus() != 1 && appointRecord.getAppointStatus() != 0 && appointRecord.getCancelResean() != null && appointRecord.getEvaStatus() != 1) {
            logger.error("预约单[" + appointRecordId + "]未预约成功或未就诊,不能评价");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "预约单未预约成功或未就诊,不能评价");
        }

        PatientFeedback sigleFeed = initThumbUpData();
        if (sigleFeed == null) {
            return false;
        }

        //给执行医生评价
        sigleFeed.setServiceType(EvaluationConstant.EVALUATION_SERVICETYPE_APPOINTRECORD);//预约
        sigleFeed.setServiceId(String.valueOf(appointRecordId));
        sigleFeed.setEvaText(evaText);
        sigleFeed.setEvaValue(evaValue);
        sigleFeed.setMpiid(appointRecord.getAppointUser().length() == 32 ? appointRecord.getAppointUser() : appointRecord.getMpiid());
        sigleFeed.setDoctorId(doctorId);
        sigleFeed.setFeedbackType(EvaluationConstant.EVALUATION_FEEDBACKTYPE_YYGH);
        final PatientFeedback validFeedback = validFeedback(sigleFeed, EvaluationConstant.EVALUATION_STATUS_NON_CHECKED);
        final HibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
                EvaluationDAO evaDao = DAOFactory.getDAO(EvaluationDAO.class);
                Integer result = evaDao.addEvaluation(validFeedback);
                if (appointRecordDAO.updateAppointEvaStatusById(appointRecordId) < 1) {
                    throw new DAOException(ErrorCode.SERVICE_ERROR, "预约单[" + appointRecordId + "]评价状态更新失败");
                }
                setResult(result);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
        Integer feedbackId = action.getResult();
        if (feedbackId == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "预约单[" + appointRecordId + "]评价插入失败");
        }
        ///此处将新插入的评价记录内码发送至评价审核消息队列
        this.insertFeedbackToCheckQueue(feedbackId);
        return true;
    }


    /**
     * 患者端评价转诊单
     *
     * @param patientFeedback 传入的评价对象
     * @return
     * @author cuill
     * @Date 2017/5/24
     */
    public PatientFeedback evaluationTransferForHealths(final PatientFeedback patientFeedback) {
        Integer transferId = Integer.parseInt(patientFeedback.getServiceId());
        TransferDAO transferDAO = DAOFactory.getDAO(TransferDAO.class);
        Transfer transfer = transferDAO.getById(transferId);
        Integer doctorId = this.judgeTransferValid(transfer);
        PatientFeedback initFeedback = initThumbUpData(patientFeedback);
        if (initFeedback == null) {
            return initFeedback;
        }

        //判断在评价表的合理性,并且包装成要插入评价表的对象数据
        String mpiId = StringUtils.isBlank(transfer.getRequestMpi()) ? transfer.getMpiId() : transfer.getRequestMpi();
        //如果是对医院的评价,那么evaluationId就是医院的机构号,如果是医生的话,evaluationId就是医生的主键
        if (patientFeedback.getEvaluationType() != null
                && EvaluationConstant.EVALUATION_TYPE_ORGAN.equals(patientFeedback.getEvaluationType())) {
            patientFeedback.setEvaluationTypeId(transfer.getConfirmOrgan());
        }

        PatientFeedback validFeedback = this.wrapPatientFeedback(patientFeedback, mpiId, doctorId, initFeedback.getFeedbackType());
        //给执行医生评价
        EvaluationDAO evaluationDAO = DAOFactory.getDAO(EvaluationDAO.class);
        PatientFeedbackRelationTabService feedbackRelationTabService = AppContextHolder.getBean(
                "eh.patientFeedbackRelationTabService", PatientFeedbackRelationTabService.class);
        PatientFeedback returnPatientFeedback = evaluationDAO.save(validFeedback);

        //增加评价和标签关联记录
        feedbackRelationTabService.addFeedbackRelationTabByFeedback(returnPatientFeedback, patientFeedback.getPatientFeedbackRelationTabList());

        Integer feedbackId = returnPatientFeedback.getFeedbackId();
        if (feedbackId == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "转诊单[" + transferId + "]评价插入失败");
        }

        //将新插入的评价记录内码发送到评价消息队列,默认好评不需要审核,所以不需要发送
        if (!EvaluationConstant.EVALUATION_STATUS_DEFAULT.equals(returnPatientFeedback.getStatus())) {
            this.insertFeedbackToCheckQueue(feedbackId);
        }
        return returnPatientFeedback;
    }

    /**
     * 患者端评价转诊单
     *
     * @param transferId
     * @param evaValue
     * @param evaText
     * @return
     * @author zhangsl
     * @Date 2017-02-14 16:19:36
     */
    public Boolean evaluationTransferForHealth(final Integer transferId, Double evaValue, String evaText) {
        TransferDAO transferDAO = DAOFactory.getDAO(TransferDAO.class);
        Transfer transfer = transferDAO.getById(transferId);
        Integer doctorId = null;
        if (transfer == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "不存在该转诊单");
        }
        if (transfer.getConfirmDoctor() == null) {
            logger.error("转诊单[" + transferId + "]信息不完整，不能评价");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "转诊单信息不完整，不能评价");
        } else {
            doctorId = transfer.getConfirmDoctor();
        }
        if (transfer.getTransferStatus() != 2 && transfer.getTransferResult() != 1 && transfer.getRefuseCause() != null && transfer.getEvaStatus() != 1) {
            logger.error("转诊单[" + transferId + "]未接收成功或未就诊,不能评价");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "转诊单未接受成功或未就诊,不能评价");
        }

        PatientFeedback sigleFeed = initThumbUpData();
        if (sigleFeed == null) {
            return false;
        }

        //给执行医生评价
        sigleFeed.setServiceType(EvaluationConstant.EVALUATION_SERVICETYPE_TRANSFER);//转诊
        sigleFeed.setServiceId(String.valueOf(transferId));
        sigleFeed.setEvaText(evaText);
        sigleFeed.setEvaValue(evaValue);
        sigleFeed.setMpiid(StringUtils.isBlank(transfer.getRequestMpi()) ? transfer.getMpiId() : transfer.getRequestMpi());
        sigleFeed.setDoctorId(doctorId);
        sigleFeed.setFeedbackType(EvaluationConstant.EVALUATION_FEEDBACKTYPE_TXYY);
        final PatientFeedback validFeedback = validFeedback(sigleFeed, EvaluationConstant.EVALUATION_STATUS_NON_CHECKED);
        final HibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                TransferDAO transferDAO = DAOFactory.getDAO(TransferDAO.class);
                EvaluationDAO evaDao = DAOFactory.getDAO(EvaluationDAO.class);
                Integer result = evaDao.addEvaluation(validFeedback);
                if (transferDAO.updateTransferEvaStatusById(transferId) < 1) {
                    throw new DAOException(ErrorCode.SERVICE_ERROR, "转诊单[" + transferId + "]评价状态更新失败");
                }
                setResult(result);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
        Integer feedbackId = action.getResult();
        if (feedbackId == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "转诊单[" + transferId + "]评价插入失败");
        }
        this.insertFeedbackToCheckQueue(feedbackId);
        return true;
    }

    /**
     * 运营平台审核通过接口
     *
     * @param feedbackId
     */
    @RpcService
    public void checkedByOP(Integer feedbackId) {
        logger.info("运营平台审核通过接口-" + feedbackId);

        EvaluationDAO evaDao = DAOFactory.getDAO(EvaluationDAO.class);
        PatientFeedback feedback = evaDao.get(feedbackId);

        if (feedback == null) {
            logger.error("评价单[" + feedbackId + "]不存在");
            return;
        }
        //审核通过，计算评分，发送消息
        countEvaValueForONS(feedback);
    }

    /**
     * 运营平台查询已处理敏感词，未审核列表
     *
     * @return
     */
    @RpcService
    public List<PatientFeedback> findUncheckedList(int start, int limit) {
        EvaluationDAO evaDao = DAOFactory.getDAO(EvaluationDAO.class);
        PatientDAO patDao = DAOFactory.getDAO(PatientDAO.class);
        DoctorDAO docDao = DAOFactory.getDAO(DoctorDAO.class);
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        List<PatientFeedback> list = evaDao.findUncheckedList(EvaluationConstant.EVALUATION_EVAVALUE_THREE, start, limit);
        String evaluationTypeName = "";
        for (PatientFeedback back : list) {
            back.setPatientName(patDao.getNameByMpiId(back.getMpiid()));
            if (EvaluationConstant.EVALUATION_TYPE_ORGAN.equals(back.getEvaluationType())) {
                evaluationTypeName = organDAO.getNameById(back.getEvaluationTypeId());
            } else if (EvaluationConstant.EVALUATION_TYPE_DOCTOR.equals(back.getEvaluationType())) {
                evaluationTypeName = docDao.getNameById(back.getDoctorId());
                back.setDocName(evaluationTypeName);
            }
            back.setEvaluationTypeName(evaluationTypeName);

        }
        return list;
    }

    /**
     * 待审核评价总数
     *
     * @return
     */
    @RpcService
    public Long findUncheckedCount() {
        EvaluationDAO evaDao = DAOFactory.getDAO(EvaluationDAO.class);
        return evaDao.getUncheckedCount(EvaluationConstant.EVALUATION_EVAVALUE_THREE);
    }

    /**
     * 将新插入的评价记录内码发送到评价消息队列
     *
     * @param feedbackId 评价记录的内码
     * @author cuill
     */
    private void insertFeedbackToCheckQueue(Integer feedbackId) {
        try {
            //此处将新插入的评价记录内码发送至评价审核消息队列
            SensitiveWordsService sensitiveWordsServiceService = AppContextHolder.getBean("eh.sensitiveWordsService", SensitiveWordsService.class);
            Boolean sendOns = sensitiveWordsServiceService.sensitiveMsgDeal(feedbackId);
            if (sendOns) {
                logger.info("evaluation:" + feedbackId + " send to ons success");
            } else {
                logger.error("evaluation:" + feedbackId + " send to ons failed");
            }
        } catch (Exception e) {
            logger.error("evaluation:" + feedbackId + " send to ons failed:\n" + e);
        }
    }

    /**
     * 包装患者自己查看自己的评价
     * @param feedback
     * @return
     */
    private PatientFeedback wrapFeedbackForSelf(PatientFeedback feedback) {
        feedback.setFiltText(feedback.getEvaText());
        return this.wrapReturnPatientFeedback(feedback);
    }

    /**
     * 包装其他人查看评价,没有评价内容显示为星级标签的值
     * @param feedback 传入的评价对象
     * @return
     * @author cuill
     * @date 2017/7/14
     */
    private PatientFeedback wrapFeedbackForOther(PatientFeedback feedback){
        if (StringUtils.isBlank(feedback.getFiltText())) {   //处理无评价内容的情况
            feedback.setFiltText(dealNoFeedbackComment(feedback));
        } else {
            feedback.setFiltText(feedback.getFiltText());
        }
        return this.wrapReturnPatientFeedback(feedback);
    }
    /**
     * 根据评价的主键获取该条评价,包装类
     *
     * @param feedback 数据库获取的评价
     * @return 前端需要的评价字段
     * @author cuill
     * @date 2017/5/25
     */
    private PatientFeedback wrapReturnPatientFeedback(PatientFeedback feedback) {
        PatientFeedback resultFeedback = new PatientFeedback();
        resultFeedback.setFeedbackId(feedback.getFeedbackId());
        resultFeedback.setStatus(feedback.getStatus());
        resultFeedback.setFiltText(feedback.getFiltText());
        //处理默认好评的情况,默认好评的时候系统赋值
        if ( EvaluationConstant.EVALUATION_STATUS_DEFAULT.equals(feedback.getStatus())) {
            resultFeedback.setFiltText(EvaluationConstant.EVALUATION_EVATEXT_DEFAULT);
        }
        resultFeedback.setEvaValue(feedback.getEvaValue());
        resultFeedback.setEvaDate(feedback.getEvaDate());
        resultFeedback.setFeedbackType(feedback.getFeedbackType());
        resultFeedback.setReadFlag(feedback.getReadFlag());
        resultFeedback.setFeedName(getPatientFeedName(feedback.getMpiid()));
        //将关联评价的评价标签获取并且添加到feedback这个对象中去
        PatientFeedbackRelationTabService feedbackRelationTabService = AppContextHolder.getBean(
                "eh.patientFeedbackRelationTabService", PatientFeedbackRelationTabService.class);
        resultFeedback.setPatientFeedbackRelationTabList(feedbackRelationTabService.patientFeedbackRelationTabList(feedback.getFeedbackId()));
        return resultFeedback;
    }


    /**
     * 1有星级、有标签、无文字：显示星级文本; 1、有星级、无标签、无文字：显示星级文本
     *
     * @param feedback 传入的评价对象
     * @return
     * @date 2017/7/5
     * @author cuill
     */
    private String dealNoFeedbackComment(PatientFeedback feedback) {
        int evaluationLevel = 0;
        Double evaValue = feedback.getEvaValue();
        if (EvaluationConstant.EVALUATION_EVAVALUE_ONE.equals(evaValue)) {
            evaluationLevel = EvaluationConstant.EVALUATION_EVAVALUE_LEVEL_ONE;
        } else if (EvaluationConstant.EVALUATION_EVAVALUE_TWO.equals(evaValue)) {
            evaluationLevel = EvaluationConstant.EVALUATION_EVAVALUE_LEVEL_TWO;
        } else if (EvaluationConstant.EVALUATION_EVAVALUE_THREE.equals(evaValue)) {
            evaluationLevel = EvaluationConstant.EVALUATION_EVAVALUE_LEVEL_THREE;
        } else if (EvaluationConstant.EVALUATION_EVAVALUE_FOUR.equals(evaValue)) {
            evaluationLevel = EvaluationConstant.EVALUATION_EVAVALUE_LEVEL_FOUR;
        } else if (EvaluationConstant.EVALUATION_EVAVALUE_FIVE.equals(evaValue)) {
            evaluationLevel = EvaluationConstant.EVALUATION_EVAVALUE_LEVEL_FIVE;
        }
        return EvaluationLevelTextEnum.findByEvaluationLevel((evaluationLevel)).getEvaluationLevelText();
    }

    /**
     * 根据医生内码用户id和服务查询是否评价过
     *
     * @param doctorId    医生的id
     * @param serviceType 服务类型
     * @param serviceId   服务类型对应的id
     * @param userId      评价对象类型对应的id
     * @param userType    评价对象的类型
     * @return
     * @author cuill
     * @Date 2017/5/27
     */
    private boolean isEvaluationForService(Integer doctorId, String serviceType, String serviceId, Integer userId, String userType, Integer evaluationType) {
        EvaluationDAO evaDao = DAOFactory.getDAO(EvaluationDAO.class);
        List<PatientFeedback> feedbacks;
        //兼容老版本,防止前端没有传evaluationType这个值
        if (ObjectUtils.isEmpty(evaluationType)) {
            feedbacks = evaDao.findEvaByServiceAndUser(doctorId, serviceType,
                    serviceId, userId, userType);
        } else {
            feedbacks = evaDao.findEvaByServiceAndUserAndEvaluationType(doctorId, serviceType, serviceId,
                    userId, userType, evaluationType);
        }
        return (!ObjectUtils.isEmpty(feedbacks));
    }

    /**
     * 判断预约单是否已经完成,有效,可以被评价.
     *
     * @param appointRecord 预约单的对象
     * @return 执行医生的id
     * @author cuill
     */
    private Integer judgeAppointRecordValid(AppointRecord appointRecord) {
        Integer doctorId;
        if (appointRecord == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "不存在该预约单");
        }
        if (appointRecord.getDoctorId() == null) {
            logger.error("预约单[" + appointRecord.getAppointRecordId() + "]信息不完整，不能评价");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "预约单信息不完整，不能评价");
        } else {
            doctorId = appointRecord.getDoctorId();
        }
        if (appointRecord.getAppointStatus() != 1 && appointRecord.getAppointStatus() != 0 && appointRecord.getCancelResean() != null && appointRecord.getEvaStatus() != 1) {
            logger.error("预约单[" + appointRecord.getAppointRecordId() + "]未预约成功或未就诊,不能评价");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "预约单未预约成功或未就诊,不能评价");
        }
        return doctorId;
    }

    /**
     * 判断咨询单是否已经完成,可以被评价
     *
     * @param consult 咨询单的对象
     * @return 执行医生的id
     * @author cuill
     */
    private Integer judgeConsultValid(Consult consult) {
        Integer exeDoctorId = null;
        if (consult == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "不存在该咨询单");
        }
        if (consult.getExeDoctor() == null) {
            logger.error("咨询单[" + consult.getConsultId() + "]无执行医生，不能评价");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "咨询单信息不完整，不能评价");
        } else {
            exeDoctorId = consult.getExeDoctor();
        }
        if (consult.getConsultStatus() != ConsultConstant.CONSULT_STATUS_FINISH && exeDoctorId == null
                && consult.getRefuseFlag() != null) {
            logger.error("咨询单[" + consult.getConsultId() + "]未完成,不能评价");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "咨询单未完成,不能评价");
        }
        return exeDoctorId;
    }

    /**
     * 不同的业务包装不同的评价类型
     *
     * @param patientFeedback 需要包装的评价对象
     * @param mpiId           患者的主键
     * @param doctorId        医生的主键
     * @param feedbackType    评价类型
     * @return 经过包装可以直接用的PatientFeedback对象
     * @author cuill
     */
    private PatientFeedback wrapPatientFeedback(PatientFeedback patientFeedback,
                                                String mpiId, Integer doctorId, Integer feedbackType) {

        // 当添加的评价类型是医生的时候
        if (patientFeedback.getEvaluationType() != null
                && patientFeedback.getEvaluationType() == EvaluationConstant.EVALUATION_TYPE_DOCTOR) {
            patientFeedback.setEvaluationTypeId(doctorId);
        }
        patientFeedback.setDoctorId(doctorId);

        patientFeedback.setMpiid(mpiId);
        switch (patientFeedback.getServiceType()) {
            case EvaluationConstant.EVALUATION_SERVICETYPE_CONSULT://咨询
                patientFeedback.setFeedbackType(feedbackType.equals(ConsultConstant.CONSULT_TYPE_POHONE) ? EvaluationConstant.EVALUATION_FEEDBACKTYPE_DHZX :
                        feedbackType.equals(ConsultConstant.CONSULT_TYPE_GRAPHIC) ? EvaluationConstant.EVALUATION_FEEDBACKTYPE_TWZX : feedbackType.equals(ConsultConstant.CONSULT_TYPE_PROFESSOR) ? EvaluationConstant.EVALUATION_FEEDBACKTYPE_ZJJD :
                                feedbackType.equals(ConsultConstant.CONSULT_TYPE_RECIPE) ? EvaluationConstant.EVALUATION_FEEDBACKTYPE_XYWY : null);
                break;
            case EvaluationConstant.EVALUATION_SERVICETYPE_TRANSFER://转诊（特需预约）
                patientFeedback.setFeedbackType(EvaluationConstant.EVALUATION_FEEDBACKTYPE_TXYY);
                break;
            case EvaluationConstant.EVALUATION_SERVICETYPE_APPOINTRECORD://预约
                patientFeedback.setFeedbackType(EvaluationConstant.EVALUATION_FEEDBACKTYPE_YYGH);
                break;
            default:
                break;
        }

        //判断该业务单是否可以评价,并且返回对象
        return validFeedback(patientFeedback, EvaluationConstant.EVALUATION_STATUS_NON_CHECKED);
    }

    /**
     * 判断转诊单是否正确
     *
     * @param transfer
     * @return 执行医生的id
     * @author cuill
     */
    private Integer judgeTransferValid(Transfer transfer) {
        if (transfer == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "不存在该转诊单");
        }
        Integer transferId = transfer.getTransferId();
        Integer doctorId = null;
        if (transfer.getConfirmDoctor() == null) {
            logger.error("转诊单[" + transferId + "]信息不完整，不能评价");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "转诊单信息不完整，不能评价");
        } else {
            doctorId = transfer.getConfirmDoctor();
        }
        if (transfer.getTransferStatus() != 2 && transfer.getTransferResult() != 1 && transfer.getRefuseCause() != null && transfer.getEvaStatus() != 1) {
            logger.error("转诊单[" + transferId + "]未接收成功或未就诊,不能评价");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "转诊单未接受成功或未就诊,不能评价");

        }
        return doctorId;
    }

    /**
     * 初始化[评价]数据
     *
     * @param feedback 需要赋值的对象
     * @author cuill
     * @Date 2017/6/6
     */
    private PatientFeedback initThumbUpData(PatientFeedback feedback) {

        //如果没有状态码的话,主要初始化默认好评
        if (feedback.getStatus() == null) {
            UserRoleToken urt = UserRoleToken.getCurrent();
            if (urt == null) {
                return null;
            }
            Integer urtId = urt.getId();
            String roleId = urt.getRoleId();
            if (!SystemConstant.ROLES_PATIENT.equals(roleId) && !SystemConstant.ROLES_DOCTOR.equals(roleId)) {
                return null;
            }
            feedback.setEvaDate(new Date());
            feedback.setUserId(urtId);
            feedback.setUserType(roleId);
            feedback.setStatus(EvaluationConstant.EVALUATION_STATUS_NON_CHECKED);
        } else {
            feedback.setUserId(EvaluationConstant.EVALUATION_USERID_SYSTEM);
            feedback.setUserType(EvaluationConstant.EVALUATION_USERTYPE_SYSTEM);
            feedback.setEvaDate(new Date());
            feedback.setAuditDate(new Date());
        }
        feedback.setReadFlag(EvaluationConstant.EVALUATION_NOT_READ);
        feedback.setIsDel(EvaluationConstant.EVALUATION_NOT_DEL);
        return feedback;
    }

    /**
     * @return
     */
    public boolean canShowEvaluationForWx() {
        SimpleWxAccount simpleWxAccount = CurrentUserInfo.getSimpleWxAccount();
        if (simpleWxAccount == null) {
            logger.info("用户信息获取失败,判断评价显示功能失败");
            return false;
        }
        String appId = simpleWxAccount.getAppId();

        WXConfigsDAO wxConfigsDAO = DAOFactory.getDAO(WXConfigsDAO.class);
        WXConfig wxConfig = wxConfigsDAO.getByAppID(appId);
        if (wxConfig == null) {
            logger.info("用户信息获取失败,判断评价显示功能失败");
            return false;
        }

        //公众号是否显示评价
        WxAppPropsDAO propsDAO = DAOFactory.getDAO(WxAppPropsDAO.class);
        if (!propsDAO.canShowEvaluationForWx(wxConfig.getId())) {
            logger.info("公众号[" + wxConfig.getId() + "]不支持显示评价");
            return false;
        }
        return true;
    }

}
