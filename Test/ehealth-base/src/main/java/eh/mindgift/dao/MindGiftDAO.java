package eh.mindgift.dao;

import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import eh.entity.mindgift.MindGift;
import eh.mindgift.constant.MindGiftConstant;
import eh.utils.DateConversion;
import eh.wxpay.constant.PayConstant;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Query;
import org.hibernate.StatelessSession;
import org.springframework.util.StringUtils;

import java.util.*;

public abstract class MindGiftDAO extends
        HibernateSupportDelegateDAO<MindGift> {
    private static final Log logger = LogFactory.getLog(MindGiftDAO.class);

    public MindGiftDAO() {
        super();
        this.setEntityName(MindGift.class.getName());
        this.setKeyField("mindGiftId");
    }


    /**
     * 根据doctorId获取已支付的，敏感词已处理的心意记录列表
     * @param start
     * @param limit
     * @return
     */
    @DAOMethod(sql = "from MindGift where doctorId=:doctorId and payflag=1 and MindGiftStatus=1 ORDER BY createDate desc")
    public abstract List<MindGift> findEffectiveMindGifts(@DAOParam("doctorId")Integer doctorId, @DAOParam(pageStart = true) int start, @DAOParam(pageLimit = true) int limit);

    /**
     * 根据doctorId获取已支付的，敏感词已处理的心意记录数
     * @param doctorId
     * @return
     */
    @DAOMethod(sql = "select count(*) from MindGift where doctorId=:doctorId and payflag=1 and MindGiftStatus=1")
    public abstract Long getEffectiveMindGiftsNum(@DAOParam("doctorId") Integer doctorId);


    /**
     * 根据业务id，业务类型获取已支付的，敏感词已处理的心意记录列表
     * @param start
     * @param limit
     * @return
     */
    @DAOMethod(sql = "from MindGift where busType=:busType and busId=:busId and payflag=1 and MindGiftStatus=1 ORDER BY createDate desc")
    public abstract List<MindGift> findEffectiveMindGiftsByBusTypeAndBusId(@DAOParam("busType")Integer busType,@DAOParam("busId")Integer busId,
                                                                           @DAOParam(pageStart = true) int start, @DAOParam(pageLimit = true) int limit);

    /**
     * 根据业务id，业务类型获取已支付的，敏感词已处理的心意记录数
     * @param doctorId
     * @return
     */
    @DAOMethod(sql = "select count(*) from MindGift where busType=:busType and busId=:busId  and payflag=1 and MindGiftStatus=1")
    public abstract Long getEffectiveMindGiftsNumByBusTypeAndBusId(@DAOParam("busType")Integer busType,@DAOParam("busId")Integer busId);


    /**
     * 查询指定时间的未支付心意单
     * @param deadTime
     * @return
     */
    @DAOMethod(sql = "from MindGift where mindGiftStatus=0 AND payFlag = 0 AND createDate < :deadTime")
    public abstract List<MindGift> findTimeOverNoPayOrder(@DAOParam("deadTime") Date deadTime);

    /**
     * 查询指定时间的已支付，但敏感词未处理的业务单
     * @param deadTime
     * @return
     */
    @DAOMethod(sql = "from MindGift where mindGiftStatus=0 AND payFlag = 1 AND createDate < :deadTime")
    public abstract List<MindGift> findTimeOverHasPayOrder(@DAOParam("deadTime") Date deadTime);


    /**
     * 根据订单号 查询心意单信息
     */
    @DAOMethod
    public abstract MindGift getByOutTradeNo(String outTradeNo);


    /**
     * 支付成功后更新支付标志
     * @param tradeNo
     */
    @DAOMethod(sql = "update MindGift set payFlag=1 , paymentDate=:paymentDate , tradeNo=:tradeNo, mindGiftStatus=0, outTradeNo=:outTradeNo where mindGiftId=:mindGiftId")
    public abstract void updatePayFlagByOutTradeNo(
            @DAOParam("paymentDate") Date paymentDate,
            @DAOParam("tradeNo") String tradeNo,
            @DAOParam("outTradeNo") String outTradeNo,
            @DAOParam("mindGiftId") Integer mindGiftId);


    /**
     * 获取一个医生的最新的心意单列表
     * 查询条件：患者已支付，敏感词已处理，医生未读的最新心意
     * 排序条件：按最后更新时间倒序排列
     * @param deadTime
     * @return
     */
    @DAOMethod(sql = "from MindGift where mindGiftStatus=1 AND payFlag = 1 and readFlag=0 and doctorId=:doctorId order by lastModify desc")
    public abstract List<MindGift> findLatestUnReadMindGiftByDoctorId(@DAOParam("doctorId") Integer doctorId , @DAOParam(pageStart = true) int start, @DAOParam(pageLimit = true) int limit);

    /**
     * 医生端获取心意详情时，则将详情单设置为已读
     * @param tradeNo
     */
    @DAOMethod(sql = "update MindGift set readFlag=1,lastModify=:lastModify where mindGiftId=:mindGiftId and readFlag=0")
    public abstract void updateReadFlagById(
            @DAOParam("lastModify") Date lastModify,
            @DAOParam("mindGiftId") Integer mindGiftId);

    /**
     * 查询一个医生的所有心意积分和
     * 查询条件：患者已支付，敏感词已处理的
     * @param doctorId
     * @return
     */
    @DAOMethod(sql = "select sum(a.doctorAccount) from MindGift a where a.mindGiftStatus=1 AND a.payFlag = 1 and a.doctorId=:doctorId ")
    public abstract Object getMindGiftAccountByDoctorId(@DAOParam("doctorId") Integer doctorId );

    /**
     * 退款回调更新，直接更新结果为退款成功或退款失败
     * @param mindGiftId
     * @param payFlag
     * @param lastModify
     */
    @DAOMethod(sql = "update MindGift set payFlag=:payFlag,lastModify=:lastModify where mindGiftId=:mindGiftId and mindGiftStatus=9 and payFlag=2")
    public abstract void updateMindGiftForRefundById( @DAOParam("payFlag") Integer payFlag,
                                                     @DAOParam("lastModify") Date lastModify,@DAOParam("mindGiftId") Integer mindGiftId);



    /**
     * 根据ID更新敏状态，敏感词过滤后内容，审核通过日期
     *
     * @param filtText
     * @param feedbackId
     * @author zhangsl
     * @Date 2016-11-15 16:59:01
     */
    public Integer updateSensitiveWordsAndAccountInfoById(final Integer status, final String filtText,final Double doctorAccount, final int mindId) {
        final HibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder("update MindGift set filtText=:filtText,mindGiftStatus=:mindGiftStatus,auditDate=:auditDate,doctorAccount=:doctorAccount " +
                        "where mindGiftId =:mindGiftId and mindGiftStatus=0 and payFlag=1");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("filtText", filtText);
                q.setParameter("mindGiftStatus", status);
                q.setParameter("doctorAccount", doctorAccount);
                q.setParameter("auditDate", new Date());
                q.setParameter("mindGiftId", mindId);
                int num = q.executeUpdate();
                if (num == 0) {
                    logger.info("心意[" + mindId + "]敏感词汇处理失败,更新记录为0");
                }
                setResult(num);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    /**
     * 根据ID取消[已支付，但敏感词未处理的业务单]
     *
     * @param filtText
     * @param feedbackId
     * @author zhangsl
     * @Date 2016-11-15 16:59:01
     */
    public Integer cancelMindGiftById( final String cancelCause,final Integer mindGiftId) {
        final HibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder("update MindGift set mindGiftStatus=:mindGiftStatus,payFlag=:payFlag,cancelCause=:cancelCause," +
                        "cancelTime=:cancelTime,lastModify=:lastModify " +
                        "where mindGiftId =:mindGiftId and mindGiftStatus=0 and payFlag=1");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("payFlag", PayConstant.PAY_FLAG_REFUNDING);
                q.setParameter("mindGiftStatus", MindGiftConstant.MINDGIFT_STATUS_CANCEL);
                q.setParameter("cancelCause", cancelCause);
                q.setParameter("cancelTime", new Date());
                q.setParameter("lastModify", new Date());
                q.setParameter("mindGiftId", mindGiftId);
                int num = q.executeUpdate();
                setResult(num);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    public QueryResult<Object> queryMindGiftByStartAndLimit(Date bDate, Date eDate,String mpiId,Integer doctorId,
                                                                Integer department,Integer organ,Integer busType,
                                                                Integer subBusType,Integer giftId,Integer payflag,
                                                                final int start, final int limit) {
        if (bDate == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, " bDate is require");
        }
        if (eDate == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, " eDate is require");
        }
        final Map<String, Object> params = new HashMap<String, Object>();
        StringBuffer sb = new StringBuffer(" from MindGift g,Patient p where g.mpiId=p.mpiId and g.createDate>=:bDate and g.createDate<:eDate");
        params.put("bDate", bDate);
        params.put("eDate", DateConversion.getDateAftXDays(eDate, 1));
        if(StringUtils.hasText(mpiId)){
        	sb.append(" and g.mpiId=:mpiId");
            params.put("mpiId",  mpiId.trim());
        }
		if (doctorId != null) {
			sb.append(" and g.doctorId=:doctorId");
			params.put("doctorId", doctorId);
		}
		if (department != null) {
			sb.append(" and g.department=:deparment");
			params.put("deparment", department);
		}
		if (organ != null) {
			sb.append(" and g.organ=:organ");
			params.put("organ", organ);
		}
		if (busType != null) {
			sb.append(" and g.busType=:busType");
			params.put("busType", busType);
		}
		if (subBusType != null) {
			sb.append(" and g.subBusType=:subBusType");
			params.put("subBusType", subBusType);
		}
		if (giftId != null) {
			sb.append(" and g.giftId=:giftId");
			params.put("giftId", giftId);
		}
		if (payflag != null) {
			sb.append(" and g.payflag=:payflag");
			params.put("payflag", payflag);
		}
        final String hql = sb.toString();
        HibernateStatelessResultAction<QueryResult<Object>> action = new AbstractHibernateStatelessResultAction<QueryResult<Object>>() {
            @SuppressWarnings("unchecked")
			@Override
            public void execute(StatelessSession ss) throws Exception {
                Query query = ss.createQuery("SELECT count(g) " + hql);
                query.setProperties(params);
                long total = (long) query.uniqueResult();//获取总条数
                query = ss.createQuery("SELECT g,p.patientName " + hql + " order by g.mindGiftId desc");
                query.setProperties(params);
                query.setFirstResult(start);
                query.setMaxResults(limit);
                List<Object[]> results = query.list();
                List<Object> list = new ArrayList<Object>(results.size());
                for (Object[] row : results) {
					MindGift o = (MindGift) row[0];
					o.setPatientName((String) row[1]);
					list.add(o);
				}
                setResult(new QueryResult<Object>(total, start, limit, list));
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }
}

