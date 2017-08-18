package eh.bus.dao;

import com.alibaba.druid.util.StringUtils;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.annotation.RpcService;
import eh.entity.base.Doctor;
import eh.entity.bus.CallRecord;
import eh.util.CallResult;
import eh.utils.DateConversion;
import org.hibernate.Query;
import org.hibernate.StatelessSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Iterator;
import java.util.List;

public abstract class CallRecordDAO extends
        HibernateSupportDelegateDAO<CallRecord> {
    private static final Logger log = LoggerFactory.getLogger(CallRecordDAO.class);

    public CallRecordDAO() {
        super();
        this.setEntityName(CallRecord.class.getName());
        this.setKeyField("id");
    }

    /**
     * 保存一个电话记录
     */
    @RpcService
    public void addCallRecord(CallRecord callRecord) throws DAOException {
        save(callRecord);
    }

    /**
     * 根据云通讯通话Id获取通话记录
     *
     * @param callSid
     * @return
     * @author LF
     */
    @RpcService
    @DAOMethod
    public abstract CallRecord getByCallSid(String callSid);

    /**
     * 跟据主叫被叫手机号及业务类型业务ID获取通话列表
     *
     * @param from
     * @param to
     * @param bussType
     * @param bussId
     * @return
     * @author LF
     */
    @RpcService
    @DAOMethod(sql = "From CallRecord where fromMobile=:from and toMobile=:to and bussType=:bussType and bussId=:bussId")
    public abstract List<CallRecord> findCallByFour(
            @DAOParam("from") String from, @DAOParam("to") String to,
            @DAOParam("bussType") int bussType, @DAOParam("bussId") int bussId);

    /**
     * @param bussId   业务序号
     * @param bussType 业务类型
     * @return List<CallRecord> 通话记录列表
     * @throws
     * @Class eh.bus.dao.CallRecordDAO.java
     * @Title: getByBussIdAndBussType 通过业务序号和业务类型查找通话列表
     * @Description: TODO
     * @author Zhongzx
     * @Date 2016-2-1下午4:52:57
     */
    @RpcService
    @DAOMethod(sql = "from CallRecord where bussId=:bussId and bussType=:bussType order by createTime desc", limit = 10000)
    public abstract List<CallRecord> findByBussIdAndBussType(
            @DAOParam("bussId") Integer bussId,
            @DAOParam("bussType") Integer bussType);

    /**
     * 获取业务通话总时长
     * <p>
     * eh.bus.dao
     *
     * @param bussType 业务类型-1转诊,2会诊,3咨询,4预约,5推荐奖励,6咨询设置,7检查
     * @param bussId   业务Id
     * @return Integer
     * @author luf 2016-3-12
     */
    @RpcService
    public Integer getMaxBussCallTime(int bussType, int bussId) {
        Integer callTime = 0;
        try {
            List<CallRecord> crs = this.findByBussIdAndBussType(bussId, bussType);
            for (CallRecord cr : crs) {
                String callSid = cr.getCallSid();
                CallResult.SDKCallResult(callSid);
                CallRecord crn = this.get(cr.getId());
                Integer ct = crn.getCallTime();
                if (ct > callTime) {
                    callTime = ct;
                }
            }
        }catch (Exception e){
            log.error("SDKCallResult exception, bussType[{}], bussId[{}], errorMessage[{}], stackTrace[{}]", bussType, bussId, e.getMessage(), com.alibaba.fastjson.JSONObject.toJSONString(e.getStackTrace()));
        }
        return callTime;
    }

    /**
     * 获取未下载的url
     *
     * @return
     * @author xuqh
     */
    @RpcService
    @DAOMethod(sql = "from CallRecord  where ossId ='0' and url is not null")
    public abstract List<CallRecord> findCallOSSISNULL();

    /**
     * 更新ossid
     *
     * @return
     * @author xuqh
     */
    @RpcService
    @DAOMethod(sql = "update CallRecord set ossId=:ossId where id=:id")
    public abstract void updateCallOSSID(@DAOParam("id") Integer id, @DAOParam("ossId") Integer ossId);

    /**
     * 获取业务通话记录数
     *
     * @param bussId
     * @param bussType
     * @return
     */
    @RpcService
    @DAOMethod(sql = "select count(*) from CallRecord where bussId=:bussId and bussType=:bussType")
    public abstract Long getCallRecordNumBuss(
            @DAOParam("bussId") Integer bussId,
            @DAOParam("bussType") Integer bussType);


    /**
     * 运营平台查询通话记录列表
     *
     * @return
     */
    public QueryResult<CallRecord> queryResultForOP(Date bDate, Date eDate, Integer bussType, Integer bussId
            , String fromMobile, String toMobile, Integer callTime, final int start, final int limit) {
        if (bDate == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "bDate is require");
        }
        if (eDate == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "eDate is require");
        }
        StringBuffer sb = new StringBuffer(" from CallRecord where createTime>='")
                .append(DateConversion.getDateFormatter(bDate,DateConversion.YYYY_MM_DD)).append(" 00:00:00'")
                .append(" and createTime<='") .append(DateConversion.getDateFormatter(eDate,DateConversion.YYYY_MM_DD)).append(" 23:59:59' ");

        if(bussType!=null){
            sb.append(" and bussType=").append(bussType);
        }
        if(bussId!=null){
            sb.append(" and bussId=").append(bussId);
        }
        if (fromMobile != null && !StringUtils.isEmpty(fromMobile.trim())) {
            sb.append(" and fromMobile='").append(fromMobile).append("'");
        }
        if (toMobile != null && !StringUtils.isEmpty(toMobile.trim())) {
            sb.append(" and toMobile='").append(toMobile).append("'");
        }
        if(callTime!=null){
            sb.append(" and callTime=").append(callTime);
        }
        final String hql = sb.toString();
        HibernateStatelessResultAction<QueryResult<CallRecord>> action = new AbstractHibernateStatelessResultAction<QueryResult<CallRecord>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                Query query = ss.createQuery("SELECT count(*) " + hql);
                long total = (long) query.uniqueResult();//获取总条数
                query = ss.createQuery(hql + " order by id desc");
                query.setFirstResult(start);
                query.setMaxResults(limit);
                setResult(new QueryResult<CallRecord>(total, start, limit, query.list()));
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 获取最新的limit条记录
     *
     * @return
     * @author andywang
     */
    public List<CallRecord> findLatestRecordsByLimit(final Integer limitCount) {
        HibernateStatelessResultAction<List<CallRecord>> action = new AbstractHibernateStatelessResultAction<List<CallRecord>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = "select a from CallRecord a  order by id Desc ";
                Query q = ss.createQuery(hql);
                q.setFirstResult(0);
                q.setMaxResults(limitCount);
                List<CallRecord> list = q.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
       return  action.getResult();
    }


    //Andywang 2017-06-16
    //根据最新的10条call记录，如果calltime全部是0的，表示电话服务有可能有问题，需要告警
    public Boolean isCallServiceNormal(){
        List<CallRecord> latests = this.findLatestRecordsByLimit(10);
        Boolean isNormal = false;
        Iterator ite =  latests.iterator();
        while (ite.hasNext())
        {
            CallRecord c = (CallRecord)ite.next();
            if (c.getCallTime() > 0)
            {
                isNormal = true;
                break;
            }
        }
        return isNormal;
    }

}
