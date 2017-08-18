package eh.bus.dao;

import ctd.account.UserRoleToken;
import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.BeanUtils;
import ctd.util.annotation.RpcService;
import eh.base.dao.DoctorDAO;
import eh.base.dao.OrganDAO;
import eh.base.dao.PatientFeedbackDAO;
import eh.base.service.BusActionLogService;
import eh.entity.base.Organ;
import eh.entity.base.PatientFeedback;
import eh.entity.bus.ComplaintRecord;
import eh.mpi.dao.PatientDAO;
import org.apache.axis.utils.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Query;
import org.hibernate.StatelessSession;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author jianghc
 * @create 2016-11-15 15:32
 **/
public abstract class ComplaintRecordDAO extends HibernateSupportDelegateDAO<ComplaintRecord> {
    private static final Log logger = LogFactory.getLog(ComplaintRecordDAO.class);

    public ComplaintRecordDAO() {
        super();
        this.setEntityName(ComplaintRecord.class.getName());
        this.setKeyField("id");
    }

    @RpcService
    public ComplaintRecord createOneComplaintRecord(ComplaintRecord record) {
        if (record == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "record is required");
        }
        if (record.getFeedbackId() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "feedbackId is required");
        }
        ComplaintRecord complaintRecord = getByFeedbackId(record.getFeedbackId());
        if (complaintRecord != null) {
            throw new DAOException("该评价已经被申述");
        }
        UserRoleToken urt = UserRoleToken.getCurrent();
        record.setHandler(urt.getId());
        record.setHandName(urt.getUserName());
        record.setHandDate(new Date());
        record.setRecorder(urt.getId());
        record.setRecorderName(urt.getUserName());
        record.setRecordDate(new Date());
        record = this.save(record);
        if (record.getStatus() == 1) {//确认受理
            PatientFeedbackDAO patientFeedbackDAO = DAOFactory.getDAO(PatientFeedbackDAO.class);
            patientFeedbackDAO.upPatientFeedbackByID(record.getFeedbackId());
        }
        BusActionLogService.recordBusinessLog("医生申诉", record.getId().toString(),
                "ComplaintRecord", "添加申述记录,申述内容为" + record.getContent() + ",处理意见为" + record.getOpinion());
        return record;
    }

    @RpcService
    @DAOMethod
    public abstract ComplaintRecord getByFeedbackId(Integer feedbackId);

    /**
     * @param complain     是否投诉
     * @param star         星级
     * @param feedbackType 业务类型
     * @param status       状态
     * @param doctorId     医生
     * @param mpiid        患者
     * @param bEvaDate     开始时间
     * @param eEvaDate     结束时间
     * @param start        分页开始
     * @param limit        长度
     * @return
     */
    @RpcService
    public QueryResult<Map> queryPatientFeedbackAndComplaintRecord(Integer complain, Integer star, final Integer feedbackType, Integer status, Integer doctorId, String mpiid, Date bEvaDate, Date eEvaDate, final int start, final int limit, Integer organ, Boolean haveComment) {
        // final StringBuilder sbHql = new StringBuilder(" from PatientFeedback a,ComplaintRecord b where a.feedbackId=b.feedbackId ");
        StringBuilder sbHql = new StringBuilder(" from PatientFeedback a,Doctor d where a.doctorId=d.doctorId and a.evaluationType=1 ");
        UserRoleToken urt = UserRoleToken.getCurrent();
        if (urt == null) {
            throw new DAOException("用户信息获取失败");
        }
        List<Integer> organs = null;
        if(organ!=null){
            sbHql = new StringBuilder(" from PatientFeedback a,Doctor d,Employment e where a.doctorId=d.doctorId and a.evaluationType=1 and e.doctorId=d.doctorId and e.organId =").append(organ);
        }else {
            if(!"eh".equals(urt.getManageUnit())){
                organs = DAOFactory.getDAO(OrganDAO.class).findOrganIdsByManageUnit(urt.getManageUnit()+"%");
                sbHql = new StringBuilder(" from PatientFeedback a,Doctor d,Employment e where a.doctorId=d.doctorId and a.evaluationType=1 and e.doctorId=d.doctorId and e.organId in(:organs)");
            }
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        if (complain != null) {//是否投诉
            switch (complain) {
                case 0://没投诉
                    sbHql.append(" and not exists(select b from ComplaintRecord b where b.feedbackId=a.feedbackId) ");
                    break;
                case 1://有投诉
                    sbHql.append(" and  exists(select b from ComplaintRecord b where b.feedbackId=a.feedbackId) ");
                    break;
                default:
                    break;
            }
        }
        if (star != null) {
            switch (star) {
                case 1:
                    sbHql.append(" and a.evaValue>=9.2 and a.evaValue<9.4 ");
                    break;
                case 2:
                    sbHql.append(" and a.evaValue>=9.4 and a.evaValue<9.6 ");
                    break;
                case 3:
                    sbHql.append(" and a.evaValue>=9.6 and a.evaValue<9.8 ");
                    break;
                case 4:
                    sbHql.append(" and a.evaValue>=9.8 and a.evaValue<10 ");
                    break;
                case 5:
                    sbHql.append(" and a.evaValue=10 ");
                    break;
                default:
                    break;
            }
        }
        if (feedbackType != null) {
            sbHql.append(" and a.feedbackType =").append(feedbackType);
        }
        if (status != null) {
            sbHql.append(" and a.isDel =").append(status);
        }
        if (doctorId != null) {
            sbHql.append(" and a.doctorId =").append(doctorId);
        }
        if (!StringUtils.isEmpty(mpiid)) {
            sbHql.append(" and a.mpiid='").append(mpiid).append("'");
        }
        if (bEvaDate != null) {
            sbHql.append(" and a.evaDate>='").append(sdf.format(bEvaDate)).append(" 00:00:00'");
        }
        if (eEvaDate != null) {
            sbHql.append(" and a.evaDate<='").append(sdf.format(eEvaDate)).append(" 23:59:59'");
        }
        if (organ != null) {
            sbHql.append(" and d.organ =").append(organ);
        }
        if (haveComment != null) {
            if (haveComment) {
                sbHql.append(" and IFNULL(a.evaText,'')!='' ");
            } else {
                sbHql.append(" and IFNULL(a.evaText,'')='' ");
            }
        }

        final String hql = sbHql.toString();
        final List<Integer> finalOrgans = organs;
        HibernateStatelessResultAction<QueryResult<Map>> action = new AbstractHibernateStatelessResultAction<QueryResult<Map>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                Query countQuery = ss.createQuery("select count(*) " + hql);
                Query query = ss.createQuery("SELECT a" + hql + " order by a.feedbackId desc");
                query.setFirstResult(start);
                query.setMaxResults(limit);
                if(finalOrgans!=null){
                    countQuery.setParameter("organs",finalOrgans);
                    query.setParameter("organs",finalOrgans);
                }
                Long total = (Long) countQuery.uniqueResult();
                List<PatientFeedback> list = query.list();
                if (list == null) {
                    setResult(new QueryResult<Map>(total, start, 0, null));
                } else {
                    List<Map> maps = new ArrayList<Map>();
                    DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
                    PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
                    for (PatientFeedback pfb : list) {
                        Map<String, Object> map = new HashMap<String, Object>();
                        map.put("doctorName", doctorDAO.get(pfb.getDoctorId()).getName());
                        map.put("patient", patientDAO.getByMpiId(pfb.getMpiid()));
                        map.put("patientFeedback", pfb);
                        map.put("complaintRecord", getByFeedbackId(pfb.getFeedbackId()));
                        maps.add(map);
                    }
                    setResult(new QueryResult<Map>(total, start, list.size(), maps));
                }
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }


    /**
     * @param star         星级
     * @param feedbackType 业务类型
     * @param mpiid        患者
     * @param bEvaDate     开始时间
     * @param eEvaDate     结束时间
     * @param start        分页开始
     * @param limit        长度
     * @return
     */
    @RpcService
    public QueryResult<Map> queryOrganPatientFeedbackAndComplaintRecord(Integer star, Integer feedbackType, String mpiid, Date bEvaDate, Date eEvaDate,
                                                                        final int start, final int limit, Integer organ, Boolean haveComment) {
        // final StringBuilder sbHql = new StringBuilder(" from PatientFeedback a,ComplaintRecord b where a.feedbackId=b.feedbackId ");
        final StringBuilder sbHql = new StringBuilder(" from PatientFeedback a,Organ o where a.evaluationTypeId=o.organId and a.evaluationType=2 ");
        UserRoleToken urt = UserRoleToken.getCurrent();
        if (urt == null) {
            throw new DAOException("用户信息获取失败");
        }
        if (!"eh".equals(urt.getManageUnit())) {
            sbHql.append(" and o.manageUnit like'").append(urt.getManageUnit()).append("%' ");
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        if (star != null) {
            switch (star) {
                case 1:
                    sbHql.append(" and a.evaValue>=9.2 and a.evaValue<9.4 ");
                    break;
                case 2:
                    sbHql.append(" and a.evaValue>=9.4 and a.evaValue<9.6 ");
                    break;
                case 3:
                    sbHql.append(" and a.evaValue>=9.6 and a.evaValue<9.8 ");
                    break;
                case 4:
                    sbHql.append(" and a.evaValue>=9.8 and a.evaValue<10 ");
                    break;
                case 5:
                    sbHql.append(" and a.evaValue=10 ");
                    break;
                default:
                    break;
            }
        }
        if (feedbackType != null) {
            sbHql.append(" and a.feedbackType =").append(feedbackType);
        }

        if (!StringUtils.isEmpty(mpiid)) {
            sbHql.append(" and a.mpiid='").append(mpiid).append("'");
        }
        if (bEvaDate != null) {
            sbHql.append(" and a.evaDate>='").append(sdf.format(bEvaDate)).append(" 00:00:00'");
        }
        if (eEvaDate != null) {
            sbHql.append(" and a.evaDate<='").append(sdf.format(eEvaDate)).append(" 23:59:59'");
        }
        if (organ != null) {
            sbHql.append(" and o.organId =").append(organ);
        }
        if (haveComment != null) {
            if (haveComment) {
                sbHql.append(" and IFNULL(a.evaText,'')!='' ");
            } else {
                sbHql.append(" and IFNULL(a.evaText,'')='' ");
            }
        }
        final HibernateStatelessResultAction<QueryResult<Map>> action = new AbstractHibernateStatelessResultAction<QueryResult<Map>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                Query countQuery = ss.createQuery("select count(*) " + sbHql.toString());
                Long total = (Long) countQuery.uniqueResult();

                Query query = ss.createQuery("SELECT a,o" + sbHql.toString() + " order by a.feedbackId desc");
                query.setFirstResult(start);
                query.setMaxResults(limit);
                List<Object[]> list = query.list();
                if (list == null) {
                    setResult(new QueryResult<Map>(total, start, 0, null));
                } else {
                    List<Map> maps = new ArrayList<Map>();
                    PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
                    for (Object[] objs : list) {
                        Map<String, Object> map = new HashMap<String, Object>();
                        PatientFeedback pf = (PatientFeedback) objs[0];
                        BeanUtils.map(pf, map);
                        Organ o = (Organ) objs[1];
                        map.put("organName", o == null ? "" : o.getName());
                        map.put("patientName", patientDAO.getNameByMpiId(pf.getMpiid()));
                        maps.add(map);
                    }
                    setResult(new QueryResult<Map>(total, start, list.size(), maps));
                }
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }


}
