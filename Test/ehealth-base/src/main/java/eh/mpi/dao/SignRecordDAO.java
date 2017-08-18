package eh.mpi.dao;

import com.alibaba.fastjson.JSONObject;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.AppContextHolder;
import eh.coupon.service.CouponService;
import eh.entity.mpi.SignRecord;
import eh.mpi.constant.SignRecordConstant;
import eh.utils.ValidateUtil;
import eh.wxpay.constant.PayConstant;
import org.hibernate.Query;
import org.hibernate.StatelessSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

/**
 * 签约申请记录
 */
public abstract class SignRecordDAO extends HibernateSupportDelegateDAO<SignRecord> {
    private static final Logger logger = LoggerFactory.getLogger(SignRecordDAO.class);


    public SignRecordDAO() {
        super();
        this.setEntityName(SignRecord.class.getName());
        this.setKeyField("signRecordId");
    }

    @DAOMethod
    public abstract List<SignRecord> findByDoctorAndRecordStatus(Integer doctor, Integer recordStatus);


    @DAOMethod(sql = "from SignRecord WHERE signRecordId in (SELECT MAX(signRecordId) FROM SignRecord WHERE doctor = :doctor and recordStatus NOT IN(3,9) and fromSign != 1 GROUP BY requestMpiId) ORDER BY signRecordId desc")
    public abstract List<SignRecord> findByDoctorAndRecordStatusPages(@DAOParam("doctor") Integer doctor,
                                                                      @DAOParam(pageStart = true) int start,
                                                                      @DAOParam(pageLimit = true) int limit);

    @DAOMethod(sql = "from SignRecord where doctor=:doctor and requestMpiId=:mpiId and recordStatus=:recordStatus")
    public abstract List<SignRecord> findByDoctorAndMpiIdAndRecordStatus(@DAOParam("doctor") Integer doctor, @DAOParam("mpiId") String mpiId, @DAOParam("recordStatus") Integer recordStatus);

    @DAOMethod(sql = "from SignRecord where doctor=:doctor and requestMpiId=:mpiId and recordStatus in(0,3,4)")
    public abstract List<SignRecord> findByDoctorAndMpiId(@DAOParam("doctor") Integer doctor, @DAOParam("mpiId") String mpiId);
    
    @DAOMethod(sql = "from SignRecord where doctor=:doctor and requestMpiId=:mpiId and recordStatus in(0,1,3,4)")
    public abstract List<SignRecord> findDoctorAndMpiId(@DAOParam("doctor") Integer doctor, @DAOParam("mpiId") String mpiId);
    
    @DAOMethod(sql = "from SignRecord where doctor=:doctor and requestMpiId=:mpiId and recordStatus = 4")
    public abstract List<SignRecord> findDoctorAndMpiIdWithHIS(@DAOParam("doctor") Integer doctor, @DAOParam("mpiId") String mpiId);

    @DAOMethod(sql = "from SignRecord where requestMpiId=:mpiId and recordStatus in(0,1,3,4)")
    public abstract List<SignRecord> findByMpiId(@DAOParam("mpiId") String mpiId);
    
    
    @DAOMethod(sql = "from SignRecord where doctor=:doctor and requestMpiId=:mpiId and recordStatus in (0,3,4) and preSign = 0 order by signRecordId desc")
    public abstract SignRecord getByDoctorAndMpiIdAndRecordStatusNearly(@DAOParam("doctor") Integer doctor, @DAOParam("mpiId") String mpiId, @DAOParam(pageStart = true) int start,
                                                                        @DAOParam(pageLimit = true) int limit);

    /**
     * 更新申请记录表状态，供[拒绝申请/同意申请/取消申请使用]
     *
     * @return
     */
    public SignRecord updateRequestStatus(SignRecord record) {
        record.setLastModify(new Date());
        return update(record);
    }

    /**
     * 保存签约申请记录
     *
     * @param record
     * @return
     */
    public SignRecord saveSignRecord(SignRecord record) {
        Date d = new Date();
        record.setLastModify(d);
        record.setRequestDate(d);

        //2017-4-11 14:53:00 为兼容健康APP老版本，保存前将是否续签标记设置为不续签
        if(record.getRenew()==null){
            record.setRenew(SignRecordConstant.SIGN_IS_NOT_RENEW);
        }
        return save(record);
    }

    /**
     * 获取准备做过期处理的签约申请（超48小时）
     *
     * @param dayBeforeYesterday
     * @return
     */
    @DAOMethod(sql = "FROM SignRecord WHERE RecordStatus = 0 and payFlag != 0 AND RequestDate <= :dayBeforeYesterday and preSign = 0")
    public abstract List<SignRecord> findOverByRequestTime(@DAOParam("dayBeforeYesterday") Date dayBeforeYesterday);

    /**
     * 获取准备做过期处理的签约待支付单子（超24小时）
     *
     * @param dayBeforeYesterday
     * @return
     */
    @DAOMethod(sql = "FROM SignRecord WHERE RecordStatus = 3 AND RequestDate <= :dayBeforeYesterday and preSign = 0")
    public abstract List<SignRecord> findToApplyByRequestTime(@DAOParam("dayBeforeYesterday") Date dayBeforeYesterday);

    /**
     * 获取准备做过期处理的预签约数据（超7*24小时）
     *
     * @param dayBeforeYesterday
     * @return
     */
    @DAOMethod(sql = "FROM SignRecord WHERE preSign = 1 AND RecordStatus = 0 AND RequestDate <= :dayBeforeYesterday")
    public abstract List<SignRecord> findToApplyByRequestTimeAndPre(@DAOParam("dayBeforeYesterday") Date dayBeforeYesterday);

    /**
     * 获取当前患者申请中或待付款或者已被同意的签约记录
     *
     * @param mpiId
     * @return
     */
    @DAOMethod(sql = "FROM SignRecord WHERE requestMpiId = :mpiId AND (recordStatus = 0 OR recordStatus = 3 OR recordStatus = 4 OR (recordStatus = 1 AND endDate>=NOW()))")
    public abstract List<SignRecord> findOwnInOrOutRecords(@DAOParam("mpiId") String mpiId);

    @DAOMethod(sql = "SELECT b.addrArea FROM Doctor a,Organ b WHERE a.organ = b.organId AND a.doctorId = :doctorId")
    public abstract String getDoctorArea(@DAOParam("doctorId") Integer doctorId);

    @DAOMethod(sql = "SELECT b.addrArea FROM Patient a,PatientType b WHERE a.patientType = b.key AND a.mpiId = :mpiId")
    public abstract String getPatientArea(@DAOParam("mpiId") String mpiId);

    /**
     * 查询某患者有效的签约申请
     *
     * @param mpiId
     * @return
     */
    @DAOMethod(sql = "FROM SignRecord WHERE requestMpiId = :mpiId AND recordStatus in (0,3,4) and (renew is null or renew <> 1) ")
    public abstract List<SignRecord> findEffectiveSignRecords(@DAOParam("mpiId") String mpiId);

    /**
     * 查询某患者有效的续签签约申请
     *
     * @param mpiId
     * @return
     */
    @DAOMethod(sql = "FROM SignRecord WHERE requestMpiId = :mpiId AND recordStatus in (0,3,4) and renew=1")
    public abstract List<SignRecord> findRenewSignRecords(@DAOParam("mpiId") String mpiId);

    /**
     * 检测家庭成员是否存在签约中的数据
     * @param selfMpi
     * @return
     */
    public boolean haveSigningDoctorForFamily(final String selfMpi){
        HibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                //上海六院就诊人改造-zhangx 添加自己为就诊人，删除改成软删除，不直接删除数据
                StringBuilder hql = new StringBuilder("select count(1) From SignRecord r, FamilyMember m where r.requestMpiId=m.memberMpi and m.mpiid=:mpiId and m.memeberStatus=1 and m.relation>0 AND r.recordStatus in (0,3)");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("mpiId", selfMpi);
                Long count = (Long)q.uniqueResult();
                setResult(count>0);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 根据交易流水号查询签约信息
     *
     * @param tradeNo
     * @return
     */
    @DAOMethod
    public abstract SignRecord getByOutTradeNo(String tradeNo);

    /**
     * 查医生待处理总数
     * @param doctorId
     * @return
     */
    @DAOMethod(sql = "select count(1) FROM SignRecord WHERE signRecordId in (select max(signRecordId) from SignRecord where doctor=:doctorId group by requestMpiId) and recordStatus = 0")
    public abstract Long getCountSignList(@DAOParam("doctorId") Integer doctorId);

    public void cancelOverTimeNoPayOrder(Date deadTime) {
        List<SignRecord> srList = findTimeOverNoPayOrder(deadTime);
        if(ValidateUtil.notBlankList(srList)){
            for(SignRecord sr : srList){
                try {
                    sr.setCause(PayConstant.OVER_TIME_AUTO_CANCEL_TEXT);
                    sr.setRecordStatus(9);
                    update(sr);
                    if(ValidateUtil.notNullAndZeroInteger(sr.getCouponId()) && sr.getCouponId()!=-1){
                        CouponService couponService = AppContextHolder.getBean("couponService", CouponService.class);
                        couponService.unlockCouponById(sr.getCouponId());
                    }
                }catch (Exception e){
                    logger.error("cancelOverTimeNoPayOrder error, busId[{}], errorMessage[{}], stackTrace[{}]", sr.getSignRecordId(), e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
                }
            }
        }
    }

    @DAOMethod(sql = "from SignRecord where recordStatus=3 AND payFlag = 0 AND requestDate < :deadTime")
    public abstract List<SignRecord> findTimeOverNoPayOrder(@DAOParam("deadTime") Date deadTime);

    @DAOMethod(sql = "from SignRecord where recordStatus=0 order by signRecordId asc", limit = 0)
    public abstract List<SignRecord> findAllPendingSIgnRecords();

    /**
     * 查询患者在该时间段之内的有效签约记录
     * */
    public List<SignRecord> findByMpiIdAndDoctorID(final Integer organ,final String mpiID,final Integer doctorID,final  Date startDate,final  Date endDate){
        HibernateStatelessResultAction<List<SignRecord>> action = new AbstractHibernateStatelessResultAction<List<SignRecord>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(" From SignRecord where organ=:organ " +
                        " and doctor=:doctor and requestMpiId=:requestMpiId  and  recordStatus in (0,1,3) " +
                        " and endDate>=:startDate ");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("organ", organ);
                q.setParameter("doctor", doctorID);
                q.setParameter("requestMpiId", mpiID);
                q.setParameter("startDate", startDate);
//                q.setParameter("endDate", endDate);
                List res = q.list();
                setResult(res);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }
}
