package eh.evaluation.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.entity.base.PatientFeedback;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Query;
import org.hibernate.StatelessSession;
import org.springframework.util.ObjectUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.List;

public abstract class EvaluationDAO extends
        HibernateSupportDelegateDAO<PatientFeedback> {
    private static final Log logger = LogFactory.getLog(EvaluationDAO.class);

    public EvaluationDAO() {
        super();
        this.setEntityName(PatientFeedback.class.getName());
        this.setKeyField("feedbackId");
    }

    @DAOMethod
    public abstract PatientFeedback getById(int id);

    /**
     * 根据用户id和评价单查询评价记录
     *
     * @param serviceType
     * @param serviceId
     * @param userId
     * @param userType
     * @return
     * @author zhangsl
     * @Date 2016-11-15 16:59:01
     */
    @DAOMethod(sql = "from PatientFeedback where doctorId=:doctorId and serviceType=:serviceType and serviceId=:serviceId and ((userId=:userId and userType=:userType) or userType='system') and IFNULL(feedbackType,0)<>0")
    public abstract List<PatientFeedback> findEvaByServiceAndUser(
            @DAOParam("doctorId") Integer doctorId,
            @DAOParam("serviceType") String serviceType,
            @DAOParam("serviceId") String serviceId,
            @DAOParam("userId") Integer userId,
            @DAOParam("userType") String userType);

    /**
     * 根据用户id和评价单查询评价记录
     *
     * @param doctorId       该医生的主键
     * @param serviceType    业务单的类型
     * @param serviceId      业务单的主键
     * @param userId         评价者的主键
     * @param userType       评价者的类型
     * @param evaluationType 评价对象的类型 1-> 是医生 2->是机构
     * @return
     * @author cuill
     * @Date 2017/5/27
     */
    @DAOMethod(sql = "from PatientFeedback where doctorId=:doctorId and evaluationType=:evaluationType and serviceType=:serviceType and serviceId=:serviceId and ((userId=:userId and userType=:userType) or userType='system') and IFNULL(feedbackType,0)<>0")
    public abstract List<PatientFeedback> findEvaByServiceAndUserAndEvaluationType(
            @DAOParam("doctorId") Integer doctorId,
            @DAOParam("serviceType") String serviceType,
            @DAOParam("serviceId") String serviceId,
            @DAOParam("userId") Integer userId,
            @DAOParam("userType") String userType,
            @DAOParam("evaluationType") Integer evaluationType);
    /**
     * 根据医生内码获取有效评价总数(查看其他人不包含默认好评和无内容评价)
     *
     * @param doctorId
     * @return
     * @author zhangsl
     * @Date 2016-11-15 16:59:01
     */
    @DAOMethod(sql = "select count(*) from PatientFeedback where doctorId=:doctorId and evaluationType = 1 and IFNULL(feedbackType,0)<>0 and isDel=0 and status=1 and evaText<>''")
    public abstract Long getEvaNumByDoctorIdForOther(
            @DAOParam("doctorId") Integer doctorId);

    /**
     * 根据医生内码获取有效评价总数(查看自己包含所有有效评价)
     *
     * @param doctorId
     * @return
     * @author zhangsl
     * 默认好评不显示 @date 2017/7/5
     * @Date 2016-11-15 16:59:01
     */
    @DAOMethod(sql = "select count(*) from PatientFeedback where doctorId=:doctorId and evaluationType = 1 and IFNULL(feedbackType,0)<>0 and isDel=0 and status=1")
    public abstract Long getEvaNumByDoctorIdForSelf(
            @DAOParam("doctorId") Integer doctorId);

    /**
     * 根据医生内码获取有效评价-分页(查看其他人不显示默认好评和无内容评价)
     *
     * @param doctorId
     * @param start
     * @return
     * @author zhangsl
     * @Date 2016-11-15 16:59:01
     */
    @DAOMethod(sql = "from PatientFeedback where doctorId=:doctorId and evaluationType = 1 and IFNULL(feedbackType,0)<>0 and isDel=0 and status=1 and evaText<>'' order by evaDate desc")
    public abstract List<PatientFeedback> findValidEvaByDoctorIdForOther(
            @DAOParam("doctorId") Integer doctorId,
            @DAOParam(pageStart = true) int start,
            @DAOParam(pageLimit = true) int limit);

    /**
     * 根据医生内码获取有效评价-分页(查看自己显示所有有效评价)
     *
     * @param doctorId 医生主键
     * @param start    起始页
     * @param limit    限制条数
     * @return
     * @author zhangsl
     * @author cuill wx3.2 modify -> 默认好评不显示 @date 2017/7/5
     * @Date 2016-11-15 16:59:01
     */
    @DAOMethod(sql = "from PatientFeedback where doctorId=:doctorId and evaluationType = 1 and IFNULL(feedbackType,0)<>0 " +
            "and isDel=0 and status=1 AND evaText != '' AND evaText IS NOT NULL  order by evaDate desc")
    public abstract List<PatientFeedback> findValidEvaByDoctorIdForSelf(
            @DAOParam("doctorId") Integer doctorId,
            @DAOParam(pageStart = true) int start,
            @DAOParam(pageLimit = true) int limit);


    /**
     * 计算该医生有效评价的数目
     *
     * @param doctorId 医生主键
     * @param start    起始页
     * @param limit    限制条数
     * @return
     * @author cuill
     * @date 2017/7/6
     */
    @DAOMethod(sql = "select count(*) from PatientFeedback where doctorId=:doctorId and evaluationType = 1 and IFNULL(feedbackType,0)<>0 " +
            "and isDel=0 and status=1 AND evaText != '' AND evaText IS NOT NULL  order by evaDate desc")
    public abstract long getValidEvaCountByDoctorIdForSelf(
            @DAOParam("doctorId") Integer doctorId);

    /**
     * 根据医生内码获取有效评价-分页 显示的是没有评价内容的评价
     *
     * @param doctorId 医生主键
     * @param start    起始页
     * @param limit    限制条数
     * @return
     * @author cuill
     * @date 2017/7/6
     */
    @DAOMethod(sql = "from PatientFeedback where doctorId=:doctorId and evaluationType = 1 and IFNULL(feedbackType,0)<>0 " +
            "and isDel=0 and status=1 AND evaText = '' order by evaDate desc")
    public abstract List<PatientFeedback> findNoEvaCommentByDoctorId(
            @DAOParam("doctorId") Integer doctorId,
            @DAOParam(pageStart = true) int start,
            @DAOParam(pageLimit = true) int limit);


    @DAOMethod(sql = "from PatientFeedback where evaValue<=:evaValue and isDel=0 and status=0 and filtText is not null")
    public abstract List<PatientFeedback> findUncheckedList(@DAOParam("evaValue") Double evaValue, @DAOParam(pageStart = true) int start,
            @DAOParam(pageLimit = true) int limit);

    @DAOMethod(sql = "select count(*) from PatientFeedback where evaValue<=:evaValue and isDel=0 and status=0 and filtText is not null")
    public abstract Long getUncheckedCount(@DAOParam("evaValue") Double evaValue);

    @DAOMethod(sql = "select feedbackId from PatientFeedback where  isDel=0 and status=0 and filtText is null and IFNULL(feedbackType,0)<>0")
    public abstract List<Integer> findOnsFailedList();

    /**
     * 医生点评增加服务--按分数点评
     *
     * @param patientFeedback
     * @return 评价主键
     * @author zhangsl
     * @Date 2016-11-15 16:59:01
     */
    public Integer addEvaluation(final PatientFeedback patientFeedback) {
        PatientFeedback back = save(patientFeedback);
        return back.getFeedbackId();
    }


    /**
     * 查询一个周期内相同点评人对某个医生的点评次数
     *
     * @param doctorId  医生内码
     * @param userId    用户id
     * @param startDate 周期开始时间
     * @author zhangsl
     * @Date 2016-11-15 16:59:01
     */
    @DAOMethod(sql = "select count(*) from PatientFeedback where doctorId=:doctorId and evaluationType = 1 and status<>0 and userType=:userType and userId=:userId and evaDate>=:startDate and IFNULL(feedbackType,0)<>0")
    public abstract Long getSameNumByDoctorIdAndUserId(
            @DAOParam("doctorId") Integer doctorId,
            @DAOParam("userId") Integer userId,
            @DAOParam("userType") String userType,
            @DAOParam("startDate") Date startDate);


    /**
     * 更新敏感词过滤后的文本
     */
    @DAOMethod
    public abstract void updateFiltTextByFeedbackId(String filtText,Integer feedbackId);


    /**
     * 根据ID更新敏状态，敏感词过滤后内容，审核通过日期
     *
     * @param status
     * @param feedbackId
     * @author zhangsl
     * @Date 2016-11-15 16:59:01
     */
    public Integer updateStatusById(final Integer status, final int feedbackId) {
        final HibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder("update PatientFeedback set status=:status,auditDate=:auditDate " +
                        "where feedbackId =:feedbackId and status=0");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("status", status);
                q.setParameter("auditDate", new Date());
                q.setParameter("feedbackId", feedbackId);
                int num = q.executeUpdate();
                if (num == 0) {
                    throw new DAOException(ErrorCode.SERVICE_ERROR, "评价[" + feedbackId + "]更新评分处理失败");
                }
                setResult(num);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    /**
     * 查询ONS未审核的记录，定时任务进行处理
     *
     * @return
     * @author fanql
     * @Date 2016-11-16 09:38:00
     */
    @DAOMethod(sql = "from PatientFeedback where status =:status and IFNULL(feedbackType,0)<>0")
    public abstract List<PatientFeedback> findByStatus(@DAOParam("status") int status);


    /**
     * 根据医生内码获取评价(查看自己显示所有有效评价)
     *
     * @param doctorId     医生内码
     * @param serviceType  业务类型 1转诊 2会诊 3咨询 4预约 5检查 6处方
     * @param feedbackType 评价类型 点评类型：0点赞1电话咨询 2图文咨询
     * @param isDel        运营平台删除标记:0未删除 1已删除
     * @param start        分页起始位置
     * @param limit        每页条数
     * @return
     * @author houxr
     * @Date 2016-11-16 09:38:00
     */
    @RpcService
    public List<PatientFeedback> findAllPatientFeedbackByDoctorIdAndServiceType(final Integer doctorId,
                                                                                final String serviceType,
                                                                                final Integer feedbackType,
                                                                                final Integer isDel,
                                                                                final int start, final int limit) {
        HibernateStatelessResultAction<List<PatientFeedback>> action = new AbstractHibernateStatelessResultAction<List<PatientFeedback>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(" from PatientFeedback where  evaluationType = 1 and (status=1 or status=2) ");
                HashMap<String, Object> params = new HashMap<String, Object>();
                if (!ObjectUtils.isEmpty(doctorId)) {
                    hql.append(" and doctorId =:doctorId ");
                    params.put("doctorId", doctorId);
                }
                if (!ObjectUtils.isEmpty(serviceType)) {
                    hql.append(" and serviceType =:serviceType ");
                    params.put("serviceType", serviceType);
                }
                if (!ObjectUtils.isEmpty(feedbackType)) {
                    hql.append(" and feedbackType =:feedbackType ");
                    params.put("feedbackType", feedbackType);
                }
                if (!ObjectUtils.isEmpty(isDel)) {
                    hql.append(" and isDel =:isDel ");
                    params.put("isDel", isDel);
                }
                hql.append(" order by evaDate desc ");
                Query query = ss.createQuery(hql.toString());
                query.setProperties(params);
                if (!ObjectUtils.isEmpty(start)) {
                    query.setFirstResult(start);
                }
                if (!ObjectUtils.isEmpty(limit)) {
                    query.setMaxResults(limit);
                }
                List<PatientFeedback> list = query.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (List<PatientFeedback>) action.getResult();
    }

    /**
     * 查询对应业务类型的评价
     *
     * @param serviceType 评价业务类型 1转诊 2会诊 3咨询 4预约 5检查 6处方
     * @return
     */
    @DAOMethod(limit = 0, sql = "select serviceId from PatientFeedback where serviceType = :serviceType AND IFNULL(feedbackType, 0) <> 0 ")
    public abstract List<String> findPatientFeedbackByServiceType(@DAOParam("serviceType") String serviceType);

    /**
     * 查询新的未读评价信息
     *
     * @param doctorId    医生内码
     * @return
     */
    @DAOMethod(limit = 0, sql = "select count(*) from PatientFeedback where doctorId=:doctorId and evaluationType = 1 and readFlag=0 and IFNULL(feedbackType, 0)<>0 and status<>0 and evaDate is not NULL and serviceId is not null ")
    public abstract Long getUnReadCountByServiceTypeAndDoctorId(@DAOParam("doctorId") Integer doctorId);

    /**
     * 统计医生对应评分星级的数量，用于计算医生总评分
     * @param doctorId
     * @param evaValue
     * @return
     * @Date 2016-12-06 14:30:49
     * @author zhangsl
     */
    @DAOMethod(sql = "select count(*) from PatientFeedback where IFNULL(feedbackType,0)<>0 and doctorId=:doctorId and evaluationType = 1 and evaValue=:evaValue and status<>0")
    public abstract Long getEvaCountByDoctorIdAndEvaValue(@DAOParam("doctorId") Integer doctorId,
                                                          @DAOParam("evaValue") Double evaValue);

    /**
     * 根据医生的主键和评价的标签来获取有评价内容的列表
     *
     * @param doctorId 医生的id
     * @param tabTotem 评价的标签
     * @param start    起始
     * @param limit    限制
     * @return
     * @author cuill
     * @author cuill wx3.2 modify -> 默认好评不显示 @date 2017/7/5
     * @date 2017/5/25
     */
    @DAOMethod(sql = "SELECT p FROM PatientFeedback p, PatientFeedbackRelationTab t WHERE t.evaluationType = 1 AND " +
            "t.evaluationTypeId =:doctorId AND t.tabTotem = :tabTotem AND t.status = 1 AND t.patientFeedbackId = p.feedbackId AND " +
            "IFNULL(feedbackType, 0) <> 0 AND isDel = 0 AND p.status = 1 AND p.evaText != '' ORDER BY evaDate DESC")
    public abstract List<PatientFeedback> queryEvaluationByDoctorIdAndTabTotem(
            @DAOParam("doctorId") Integer doctorId,
            @DAOParam("tabTotem") String tabTotem,
            @DAOParam(pageStart = true) int start,
            @DAOParam(pageLimit = true) int limit);

    /**
     * 根据医生的主键和评价的标签来获取有评价内容的列表的数量
     *
     * @param doctorId 医生的id
     * @param tabTotem 评价的标签
     * @return
     * @author cuill
     * @date 2017/7/6
     */
    @DAOMethod(sql = "select count(*) FROM PatientFeedback p, PatientFeedbackRelationTab t WHERE t.evaluationType = 1 AND " +
            "t.evaluationTypeId =:doctorId AND t.tabTotem = :tabTotem AND t.status = 1 AND t.patientFeedbackId = p.feedbackId AND " +
            "IFNULL(feedbackType, 0) <> 0 AND isDel = 0 AND p.status = 1 AND p.evaText != '' ORDER BY evaDate DESC")
    public abstract long getEvaCountByDoctorIdAndTabTotem(
            @DAOParam("doctorId") Integer doctorId,
            @DAOParam("tabTotem") String tabTotem);


    /**
     * 根据医生的主键和评价的标签来获取无评价内容的列表
     *
     * @param doctorId 医生的id
     * @param tabTotem 评价的标签
     * @param start    起始
     * @param limit    限制
     * @return
     * @author cuill
     * @date 2017/7/6
     */
    @DAOMethod(sql = "SELECT p FROM PatientFeedback p, PatientFeedbackRelationTab t WHERE t.evaluationType = 1 AND " +
            "t.evaluationTypeId =:doctorId AND t.tabTotem = :tabTotem AND t.status = 1 AND t.patientFeedbackId = p.feedbackId AND " +
            "IFNULL(feedbackType, 0) <> 0 AND isDel = 0 AND p.status = 1 AND p.evaText = '' ORDER BY evaDate DESC")
    public abstract List<PatientFeedback> queryNoEvaCommentByDoctorIdAndTabTotem(
            @DAOParam("doctorId") Integer doctorId,
            @DAOParam("tabTotem") String tabTotem,
            @DAOParam(pageStart = true) int start,
            @DAOParam(pageLimit = true) int limit);

    /**
     * 通过业务单的类型和主键来查找评价,并且获取的结果集按照医生和医院排序,医生的评价在医院的评价前面
     *
     * @param serviceType 评价业务类型 1转诊 2会诊 3咨询 4预约 5检查 6处方
     * @param serviceId 业务单的主键
     * @return
     * @author cuill
     */
    @DAOMethod(sql = "select feedbackId from PatientFeedback where serviceType =:serviceType and serviceId =:serviceId order by evaluationType asc")
    public abstract List<Integer> queryFeedbackByServiceTypeAndServiceId(
            @DAOParam("serviceType") String serviceType,
            @DAOParam("serviceId") String serviceId);

}

