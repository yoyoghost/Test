package eh.base.dao;

import ctd.account.UserRoleToken;
import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.persistence.support.impl.user.UserRoleTokenDAO;
import ctd.util.annotation.RpcService;
import eh.entity.base.CashBills;
import eh.entity.base.DoctorAccountDetail;
import eh.entity.base.PaymentDetail;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Query;
import org.hibernate.StatelessSession;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public abstract class CashBillsDAO extends HibernateSupportDelegateDAO<CashBills> {

    public CashBillsDAO() {
        super();
        this.setEntityName(CashBills.class.getName());
        this.setKeyField("billId");
    }

    /**
     * 根据条件查询提现单详情
     *
     * @param startTime
     * @param endTime
     * @param start
     * @param limit
     * @param payStatus
     * @return
     */
    @RpcService
    public List<CashBills> getCashBillsListWithStartAndLimit(final Date startTime, final Date endTime, final int start, final int limit,
                                                             final List<Integer> payStatus) {
        if (startTime == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "统计开始时间不能为空");
        }
        if (endTime == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "统计结束时间不能为空");
        }
        HibernateStatelessResultAction<List<CashBills>> action = new AbstractHibernateStatelessResultAction<List<CashBills>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "from CashBills where (operator=:operator or creator=:creator) and DATE(createDate)>=DATE(:startTime) and DATE(createDate)<=DATE(:endTime)");

                // 添加提现单状态
                if (payStatus.size() > 0) {
                    hql.append(" and (");
                    for (int status : payStatus) {
                        hql.append(" payStatus= " + status + " or ");
                    }
                    hql.delete(hql.length() - 4, hql.length());
                    hql.append(")");
                }

                int creator = UserRoleToken.getCurrent().getId();

                Query query = ss.createQuery(hql.toString());
                query.setTimestamp("startTime", startTime);
                query.setTimestamp("endTime", endTime);
                query.setParameter("operator", creator);
                query.setParameter("creator", creator);
                query.setFirstResult(start);
                query.setMaxResults(limit);
                List<CashBills> list = query.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<CashBills> list = action.getResult();
        DoctorAccountDetailDAO detailDao = DAOFactory.getDAO(DoctorAccountDetailDAO.class);
       // UserRoleTokenDAO tokenDao = DAOFactory.getDAO(UserRoleTokenDAO.class);
        for (CashBills cashBills : list) {
           /* String creatorName = tokenDao.get(cashBills.getCreator()).getUserName();
            cashBills.setCreatorName(creatorName);
            if (cashBills.getOperator() != null) {
                String operatorName = tokenDao.get(cashBills.getOperator()).getUserName();
                cashBills.setOperatorName(operatorName);
            }*/
            List<DoctorAccountDetail> detailList = detailDao.findByBillId(cashBills.getBillId());
            cashBills.setDetailList(detailList);
        }
        return list;
    }


    /**
     * 获取提现单列表服务
     *
     * @param startTime
     * @param endTime
     * @param start
     * @param paystatus
     * @return
     * @author ZX
     * @date 2015-8-10  下午7:19:45
     */
    @RpcService
    public List<CashBills> getCashBillsList(
            Date startTime, Date endTime, int start,
            List<Integer> paystatus) {
        return getCashBillsListWithStartAndLimit(startTime, endTime, start, 10, paystatus);
    }


    /**
     * 获取体现单记录数
     *
     * @param startTime
     * @param endTime
     * @param paystatus
     * @return
     */
    @RpcService
    public long getCashBillsListNum(final Date startTime, final Date endTime, final List<Integer> paystatus) {
        if (startTime == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "统计开始时间不能为空");
        }

        if (endTime == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "统计结束时间不能为空");
        }

        HibernateStatelessResultAction<Long> action = new AbstractHibernateStatelessResultAction<Long>() {
            public void execute(StatelessSession ss) throws Exception {

                StringBuilder hql = new StringBuilder(
                        "select count(*) from CashBills where (operator=:operator or creator=:creator) and DATE(createDate)>=DATE(:startTime) and DATE(createDate)<=DATE(:endTime)");

                // 添加提现单状态
                if (paystatus.size() > 0) {
                    hql.append(" and (");
                    for (int status : paystatus) {
                        hql.append(" paystatus= " + status + " or ");
                    }
                    hql.delete(hql.length() - 4, hql.length());
                    hql.append(")");
                }
                int creator = UserRoleToken.getCurrent().getId();

                Query query = ss.createQuery(hql.toString());
                query.setTimestamp("startTime", startTime);
                query.setTimestamp("endTime", endTime);
                query.setParameter("operator", creator);
                query.setParameter("creator", creator);

                long num = (long) query.uniqueResult();
                setResult(num);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * Title: 查询提现单
     * Description: 根据开始时间  结束时间  查询申请状态下的提现单
     *
     * @param startTime ----起始时间
     * @param endTime   ----结束时间
     * @param start     ----分页，起始数据
     * @return List<CashBills>
     * @author AngryKitty
     * @date 2015-9-6
     */
    @RpcService
    public List<CashBills> getApplyCashBillsList(Date startTime, Date endTime, int start) {
        List<Integer> paystatus = new ArrayList<Integer>();
        paystatus.add(0);//状态为提现申请中
        return getCashBillsListWithStartAndLimit(startTime, endTime, start, 10, paystatus);
    }


    /**
     * 查询所有提现单详情
     *
     * @param cashBillId
     * @param payStatus
     * @return
     */
    @RpcService
    public QueryResult<CashBills> queryCashBillsByBillIdAndPayStatus(final String cashBillId, final Integer payStatus, final int start, final int limit) {
        HibernateStatelessResultAction<QueryResult<CashBills>> action = new AbstractHibernateStatelessResultAction<QueryResult<CashBills>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                long total = 0;
                StringBuilder hql = new StringBuilder("from CashBills b where 1=1 ");
                HashMap<String, Object> params = new HashMap<String, Object>();
                if (!StringUtils.isEmpty(cashBillId)) {
                    hql.append(" and b.billId = :cashBillId");
                    params.put("cashBillId", cashBillId);
                }
                if (!ObjectUtils.isEmpty(payStatus)) {
                    if (ObjectUtils.nullSafeEquals(-1, payStatus)) {
                        hql.append(" and b.paystatus in (1,3)");//当运营平台前端传值为-1的时候只查询1
                    } else {
                        hql.append(" and b.paystatus = :payStatus");
                        params.put("payStatus", payStatus);
                    }
                } else {
                    hql.append(" ");
                }
                hql.append(" order by b.createDate desc ");

                Query query = ss.createQuery("select count(b.billId) " + hql.toString());
                query.setProperties(params);
                total = (long) query.uniqueResult();//获取总条数

                query = ss.createQuery(hql.toString());
                query.setProperties(params);
                query.setFirstResult(start);
                query.setMaxResults(limit);
                List<CashBills> list = query.list();
                DoctorAccountDetailDAO detailDao = DAOFactory.getDAO(DoctorAccountDetailDAO.class);
                PaymentDetailDAO paymentDetailDAO = DAOFactory.getDAO(PaymentDetailDAO.class);
                //UserRoleTokenDAO tokenDao = DAOFactory.getDAO(UserRoleTokenDAO.class);
                List<CashBills> targetCashBills = new ArrayList<CashBills>();
                for (CashBills cashBills : list) {
                   /* String creatorName = tokenDao.get(cashBills.getCreator()).getUserName();
                    cashBills.setCreatorName(creatorName);*/
                    List<DoctorAccountDetail> detailList = detailDao.findByBillId(cashBills.getBillId());
                    List<PaymentDetail> paymentDetails = paymentDetailDAO.findByBillId(cashBills.getBillId());
                    cashBills.setDetailList(detailList);
                    cashBills.setPaymentDetails(paymentDetails);
                    targetCashBills.add(cashBills);
                }
                QueryResult<CashBills> result = new QueryResult<CashBills>(total, query.getFirstResult(), query.getMaxResults(), targetCashBills);
                setResult(result);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (QueryResult<CashBills>) action.getResult();
    }


    /**
     * 查询所有提现支付完成详情
     *
     * @param billId
     * @param payEndStatus
     * @return
     */
    @RpcService
    public QueryResult<CashBills> queryEndWithdrawByBillIdAndPayEndStatus(final String billId, final Integer payEndStatus, final int start, final int limit) {
        HibernateStatelessResultAction<QueryResult<CashBills>> action = new AbstractHibernateStatelessResultAction<QueryResult<CashBills>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                long total = 0;
                HashMap<String, Object> params = new HashMap<String, Object>();
                StringBuilder hql = new StringBuilder(
                        "from CashBills where 1=1 " +
                                " and (paystatus=1 or paystatus =4 or paystatus =5 or paystatus =6 or paystatus =7) " +
                                " and payBankCode is not null " +
                                " and payBankName is not null ");
                if (!StringUtils.isEmpty(billId)) {
                    hql.append(" and billId = :billId");
                    params.put("billId", billId);
                }
                if (!ObjectUtils.isEmpty(payEndStatus)) {
                    hql.append(" and payEndStatus = :payEndStatus");
                    params.put("payEndStatus", payEndStatus);
                }
                hql.append(" order by createDate desc ");

                Query query = ss.createQuery("select count(billId) " + hql.toString());
                query.setProperties(params);
                total = (long) query.uniqueResult();//获取总条数

                query = ss.createQuery(hql.toString());
                query.setProperties(params);
                query.setFirstResult(start);
                query.setMaxResults(limit);
                List<CashBills> list = query.list();
                DoctorAccountDetailDAO detailDao = DAOFactory.getDAO(DoctorAccountDetailDAO.class);
                PaymentDetailDAO paymentDetailDAO = DAOFactory.getDAO(PaymentDetailDAO.class);
                //UserRoleTokenDAO tokenDao = DAOFactory.getDAO(UserRoleTokenDAO.class);
                List<CashBills> targetCashBills = new ArrayList<CashBills>();
                for (CashBills cashBills : list) {
                   /* String creatorName = tokenDao.get(cashBills.getCreator()).getUserName();
                    cashBills.setCreatorName(creatorName);
                    if (cashBills.getOperator() != null) {
                        String operatorName = tokenDao.get(cashBills.getOperator()).getUserName();
                        cashBills.setOperatorName(operatorName);
                    }*/
                    List<DoctorAccountDetail> detailList = detailDao.findByBillId(cashBills.getBillId());
                    List<PaymentDetail> paymentDetails = paymentDetailDAO.findByBillId(cashBills.getBillId());
                    cashBills.setDetailList(detailList);
                    cashBills.setPaymentDetails(paymentDetails);
                    targetCashBills.add(cashBills);
                }
                QueryResult<CashBills> result = new QueryResult<CashBills>(total, query.getFirstResult(), query.getMaxResults(), targetCashBills);
                setResult(result);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (QueryResult<CashBills>) action.getResult();
    }

    /**
     * 根据打款状态 查询 获取打款批号
     *
     * @param payStatus paystatus为1已打款 payEndStatus 0德科支付中 获取已打德科的支付批号
     * @return
     */
    public List<String> findCashBillsByPayStatus(final Integer payStatus) {
        HibernateStatelessResultAction<List<String>> action = new AbstractHibernateStatelessResultAction<List<String>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "select billId from CashBills where 1=1 " +
                                " and payBankCode is not null " +
                                " and payBankName is not null " +
                                " and payEndStatus=0 ");
                if (!ObjectUtils.isEmpty(payStatus)) {
                    hql.append(" and payStatus = :payStatus");
                }
                hql.append(" order by createDate desc ");
                Query query = ss.createQuery(hql.toString());

                if (!ObjectUtils.isEmpty(payStatus)) {
                    query.setParameter("payStatus", payStatus);
                }
                List<String> list = query.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        List<String> billIdList = action.getResult();
        return billIdList;
    }

    @DAOMethod
    public abstract CashBills getByBillId(String billId);
}
