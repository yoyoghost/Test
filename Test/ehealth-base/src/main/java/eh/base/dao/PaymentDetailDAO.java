package eh.base.dao;

import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.annotation.RpcService;
import eh.entity.base.DoctorAndAccountAndDetail;
import eh.entity.base.PaymentDetail;
import org.hibernate.Query;
import org.hibernate.StatelessSession;
import org.springframework.util.ObjectUtils;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public abstract class PaymentDetailDAO extends HibernateSupportDelegateDAO<PaymentDetail> {

    public PaymentDetailDAO() {
        super();
        this.setEntityName(PaymentDetail.class.getName());
        this.setKeyField("paymentDetailId");
    }

    public void validPaymentDetailOne(PaymentDetail paymentDetail) {
        if (ObjectUtils.isEmpty(paymentDetail.getDoctorId())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "提现医生Id不能为空~~");
        }
        if (ObjectUtils.isEmpty(paymentDetail.getDoctorName())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "提现医生姓名不能为空~~");
        }
        if (ObjectUtils.isEmpty(paymentDetail.getPaymentAmount())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "提现医生金额不能为空~~");
        }
        if (ObjectUtils.isEmpty(paymentDetail.getPayMode())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "提现方式不能为空~~");
        }
    }

    public void validPaymentDetail(PaymentDetail paymentDetail) {
        validPaymentDetailOne(paymentDetail);

        //1支付宝 3银行卡
        /*if (!ObjectUtils.nullSafeEquals(paymentDetail.getPayMode(), "1") || !ObjectUtils.nullSafeEquals(paymentDetail.getPayMode(), "3")) {
            throw new DAOException(DAOException.VALUE_NEEDED, "未知提现方式~~");
        }*/
        //1支付宝
        if (ObjectUtils.nullSafeEquals(paymentDetail.getPayMode(), "1")) {
            if (ObjectUtils.isEmpty(paymentDetail.getAlipayId())) {
                throw new DAOException(DAOException.VALUE_NEEDED, "提现方式为支付宝，支付宝账号不能为空~~");
            }
        }
        //3银行卡
        if (ObjectUtils.nullSafeEquals(paymentDetail.getPayMode(), "3")) {
            if (ObjectUtils.isEmpty(paymentDetail.getBankName())) {
                throw new DAOException(DAOException.VALUE_NEEDED, "提现方式为银行卡，提现银行名称不能为空~~");
            }
            if (ObjectUtils.isEmpty(paymentDetail.getCardNo())) {
                throw new DAOException(DAOException.VALUE_NEEDED, "提现方式为银行卡，银行卡号不能为空~~");
            }
            if (ObjectUtils.isEmpty(paymentDetail.getCardName())) {
                throw new DAOException(DAOException.VALUE_NEEDED, "提现方式为银行卡，银行卡姓名不能为空~~");
            }
        }

    }

    /**
     * 根据提现单获取提现记录
     *
     * @param billId
     * @return
     * @date 2016-10-18 下午5:17:44
     */
    @RpcService
    public List<PaymentDetail> findByBillId(final String billId) {
        HibernateStatelessResultAction<List<PaymentDetail>> action = new AbstractHibernateStatelessResultAction<List<PaymentDetail>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(" from PaymentDetail where 1=1 ");
                HashMap<String, Object> params = new HashMap<String, Object>();
                if (!ObjectUtils.isEmpty(billId)) {
                    hql.append(" and billId =:billId  ");
                    params.put("billId", billId);
                }
                hql.append(" order by createDate desc ");
                Query query = ss.createQuery(hql.toString());
                query.setProperties(params);
                List<PaymentDetail> list = query.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (List<PaymentDetail>) action.getResult();

    }

    /**
     * 保存 每批打款 医生信息
     *
     * @param paymentDetail
     * @return
     */
    public PaymentDetail addDoctor2PaymentDetail(PaymentDetail paymentDetail) {
        validPaymentDetail(paymentDetail);
        paymentDetail.setCreateDate(new Date());
        paymentDetail.setPayStatus(3);//3打款审核中 2提现申请失败
        PaymentDetail target = save(paymentDetail);
        return target;
    }

    /**
     * 根据提现状态查询医生提现记录(分页)
     *
     * @param payStatus
     * @return
     */
    @RpcService
    public List<DoctorAndAccountAndDetail> findRecordByPayStatusPage(final int payStatus, final int start) {
        HibernateStatelessResultAction<List<DoctorAndAccountAndDetail>> action = new AbstractHibernateStatelessResultAction<List<DoctorAndAccountAndDetail>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = new String(
                        "select new eh.entity.base.DoctorAndAccountAndDetail(b,c,a,a.createDate)  from DoctorAccountDetail a, Doctor b, DoctorAccount c where a.doctorId=b.doctorId  and a.doctorId=c.doctorId  and b.doctorId=c.doctorId and  a.inout =2 and a.payStatus = :payStatus  and ( b.testPersonnel = 0 or b.testPersonnel is null) ");
                Query q = ss.createQuery(hql);
                q.setParameter("payStatus", payStatus);
                q.setMaxResults(10);
                q.setFirstResult(start);
                List<DoctorAndAccountAndDetail> list = q.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<DoctorAndAccountAndDetail> lists = (List<DoctorAndAccountAndDetail>) action.getResult();
        for (DoctorAndAccountAndDetail list : lists) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(list.getLastDate());
            calendar.add(Calendar.DATE, 5);
            list.setLastDate(calendar.getTime());
        }
        return lists;
    }

    /**
     * 查询医生提现历史记录(分页)
     *
     * @param startDate
     * @return
     */
    @RpcService
    public List<DoctorAndAccountAndDetail> findCompleteDetailPage(final Date startDate, final Date endDate, final int start) {
        HibernateStatelessResultAction<List<DoctorAndAccountAndDetail>> action = new AbstractHibernateStatelessResultAction<List<DoctorAndAccountAndDetail>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = null;
                boolean dateLimit = false;
                if (startDate != null && endDate != null) {
                    dateLimit = true;
                }
                if (dateLimit) {
                    hql = new String(
                            "select new eh.entity.base.DoctorAndAccountAndDetail(b,c,a)  from DoctorAccountDetail a, Doctor b, DoctorAccount c where a.doctorId=b.doctorId  and a.doctorId=c.doctorId  and b.doctorId=c.doctorId and  a.inout =2 and a.payStatus = 1  and ( b.testPersonnel = 0 or b.testPersonnel is null) and a.payDate >=:startDate and a.payDate <= :endDate  ");
                } else {
                    hql = new String(
                            "select new eh.entity.base.DoctorAndAccountAndDetail(b,c,a)  from DoctorAccountDetail a, Doctor b, DoctorAccount c where a.doctorId=b.doctorId  and a.doctorId=c.doctorId  and b.doctorId=c.doctorId and  a.inout =2 and a.payStatus = 1  and ( b.testPersonnel = 0 or b.testPersonnel is null)  ");
                }
                Query q = ss.createQuery(hql);
                if (dateLimit) {
                    q.setParameter("startDate", startDate);
                    q.setParameter("endDate", endDate);
                }
                q.setMaxResults(10);
                q.setFirstResult(start);
                @SuppressWarnings("unchecked")
                List<DoctorAndAccountAndDetail> list = q.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (List<DoctorAndAccountAndDetail>) action.getResult();
    }

    /**
     * 根据提现状态查询医师提现记录
     *
     * @param payStatus
     * @return
     */
    @RpcService
    public List<DoctorAndAccountAndDetail> findRecordByPayStatus(
            final int payStatus) {
        HibernateStatelessResultAction<List<DoctorAndAccountAndDetail>> action = new AbstractHibernateStatelessResultAction<List<DoctorAndAccountAndDetail>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = new String(
                        "select new eh.entity.base.DoctorAndAccountAndDetail(b,c,a,a.createDate)  from DoctorAccountDetail a, Doctor b, DoctorAccount c where a.doctorId=b.doctorId  and a.doctorId=c.doctorId  and b.doctorId=c.doctorId and  a.inout =2 and a.payStatus = :payStatus  and ( b.testPersonnel = 0 or b.testPersonnel is null)");
                Query q = ss.createQuery(hql);
                q.setParameter("payStatus", payStatus);
                @SuppressWarnings("unchecked")
                List<DoctorAndAccountAndDetail> list = q.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<DoctorAndAccountAndDetail> lists = (List<DoctorAndAccountAndDetail>) action
                .getResult();
        for (DoctorAndAccountAndDetail list : lists) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(list.getLastDate());
            calendar.add(Calendar.DATE, 5);
            list.setLastDate(calendar.getTime());
        }
        return lists;
    }

    /**
     * 根据提现记录单查询医师提现记录
     *
     * @param billId
     * @return
     * @date 2015-8-11 下午2:39:20
     */
    @RpcService
    public List<DoctorAndAccountAndDetail> findRecordByBillId(final String billId) {
        HibernateStatelessResultAction<List<DoctorAndAccountAndDetail>> action = new AbstractHibernateStatelessResultAction<List<DoctorAndAccountAndDetail>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = new String(
                        "select new eh.entity.base.DoctorAndAccountAndDetail(b,c,a,a.createDate)  from DoctorAccountDetail a, Doctor b, DoctorAccount c where a.doctorId=b.doctorId  and a.doctorId=c.doctorId  and b.doctorId=c.doctorId and a.billId = :billId  and ( b.testPersonnel = 0 or b.testPersonnel is null) ");
                Query q = ss.createQuery(hql);
                q.setParameter("billId", billId);
                @SuppressWarnings("unchecked")
                List<DoctorAndAccountAndDetail> list = q.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<DoctorAndAccountAndDetail> lists = (List<DoctorAndAccountAndDetail>) action.getResult();
        for (DoctorAndAccountAndDetail list : lists) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(list.getLastDate());
            calendar.add(Calendar.DATE, 5);
            list.setLastDate(calendar.getTime());
        }
        return lists;
    }


    /**
     * 根据德科返回结果 查询 打款 医生记录
     *
     * @param payMode  渠道类型 3银行卡 1支付宝
     * @param billId   批次号
     * @param bankName 银行名称
     * @param cardName 银行卡名称 或 用户支付宝姓名
     * @param cardNo   银行卡号 或 支付宝账号
     * @return
     */
    public List<PaymentDetail> findPaymentDetailByResult(final String payMode, final String billId,
                                                         final String bankName, final String cardNo,
                                                         final String cardName) {
        HibernateStatelessResultAction<List<PaymentDetail>> action = new AbstractHibernateStatelessResultAction<List<PaymentDetail>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                HashMap<String, Object> params = new HashMap<String, Object>();
                StringBuilder hql = new StringBuilder("from PaymentDetail where 1=1");
                if (ObjectUtils.nullSafeEquals(payMode, "3")) {
                    hql.append(" and paymode=:payMode ");
                    hql.append(" and cardNo=:cardNo ");
                    params.put("payMode", payMode);
                    params.put("cardNo", cardNo);
                } else {
                    hql.append(" and paymode=:payMode ");
                    hql.append(" and alipayId=:alipayId ");
                    params.put("payMode", payMode);
                    params.put("alipayId", cardNo);
                }
                if (!ObjectUtils.isEmpty(billId)) {
                    hql.append(" and billId=:billId");
                    params.put("billId", billId);
                }
                if (!ObjectUtils.isEmpty(bankName)) {
                    hql.append(" and bankName=:bankName");
                    params.put("bankName", bankName);
                }
                if (!ObjectUtils.isEmpty(cardName)) {
                    hql.append(" and cardName=:cardName");
                    params.put("cardName", cardName);
                }
                hql.append(" order by createDate desc ");
                Query query = ss.createQuery(hql.toString());
                query.setProperties(params);
                List<PaymentDetail> list = query.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

/*
    *//**
     * 审核医师提现记录
     *//*
    @RpcService
    @DAOMethod(sql = "update DoctorAccountDetail set billId=:billId,closure=0,payStatus=2 where payStatus=0 and accountDetailId=:accountDetailId ")
    public abstract void updateBillIdByAccountDetailId(@DAOParam("billId") String billId, @DAOParam("accountDetailId") Integer accountDetailId);

    *//**
     * 完成提现记录
     *//*
    @RpcService
    @DAOMethod(sql = "update DoctorAccountDetail set payStatus=1,payDate=current_timestamp() where payStatus=2 ")
    public abstract void updateRecordComplete();

    *//**
     * 完成提现记录
     *//*
    @RpcService
    @DAOMethod(sql = "update DoctorAccountDetail set payStatus=1,payDate=current_timestamp(),closure=1 where payStatus=2 and billId=:billId")
    public abstract void updateAccountDetailComplete(@DAOParam("billId") String billId);*/

}
