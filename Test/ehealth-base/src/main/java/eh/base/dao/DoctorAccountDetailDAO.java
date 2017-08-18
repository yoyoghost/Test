package eh.base.dao;

import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.persistence.support.impl.dictionary.DBDictionaryItemLoader;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.bus.constant.DoctorBankCardConstant;
import eh.entity.base.*;
import eh.utils.DateConversion;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Query;
import org.hibernate.StatelessSession;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;

public abstract class DoctorAccountDetailDAO extends
        HibernateSupportDelegateDAO<DoctorAccountDetail> implements
        DBDictionaryItemLoader<DoctorAccountDetail> {

    public DoctorAccountDetailDAO() {
        super();
        this.setEntityName(DoctorAccountDetail.class.getName());
        this.setKeyField("accountDetailId");
    }

    /**
     * 医生帐户收入明细查询服务
     *
     * @param doctorId
     * @param startDate
     * @param endDate
     * @return
     * @author hyj
     */
    @RpcService
    @DAOMethod(sql = "from DoctorAccountDetail where doctorId=:doctorId and inout=1 and createDate>=:startDate and createDate<:endDate order by createDate desc")
    public abstract List<DoctorAccountDetail> findInDetailByDoctorIdAndCreateDate(
            @DAOParam("doctorId") int doctorId,
            @DAOParam("startDate") Date startDate,
            @DAOParam("endDate") Date endDate);

    /**
     * 医生帐户支出明细查询服务
     *
     * @param doctorId
     * @param startDate
     * @param endDate
     * @return
     * @author hyj
     */
    @RpcService
    @DAOMethod(sql = "from DoctorAccountDetail where doctorId=:doctorId and inout=2 and createDate>=:startDate and createDate<:endDate")
    public abstract List<DoctorAccountDetail> findOutDetailByDoctorIdAndCreateDate(
            @DAOParam("doctorId") int doctorId,
            @DAOParam("startDate") Date startDate,
            @DAOParam("endDate") Date endDate);

    /**
     * 按月份统计【收入支出】服务
     *
     * @param doctorId
     * @param inout
     * @param startDate
     * @param endDate
     * @return
     * @author LF
     */
    @RpcService
    @DAOMethod(sql = "SELECT  CONCAT(YEAR(createDate),'-',MONTH(createDate)),SUM(money) from DoctorAccountDetail WHERE doctorId=:doctorId and inout=:inout AND createDate>=:startDate AND createDate<=:endDate GROUP BY  CONCAT(YEAR(createDate),'-',MONTH(createDate)) ORDER BY YEAR(createDate),MONTH(createDate)")
    public abstract List<DoctorAccountDetail> findTotalByMonth(
            @DAOParam("doctorId") Integer doctorId,
            @DAOParam("inout") Integer inout,
            @DAOParam("startDate") Date startDate,
            @DAOParam("endDate") Date endDate);

    /**
     * 按月份统计【收入】服务
     *
     * @param doctorId
     * @param startDate
     * @param endDate
     * @return
     * @author ZX
     * @date 2015-4-26 下午2:54:22
     */
    @RpcService
    public List<DoctorAccountDetail> findInTotalByMonth(Integer doctorId,
                                                        Date startDate, Date endDate) {
        return findTotalByMonth(doctorId, 1, startDate, endDate);
    }

    /**
     * 按月份统计【支出】服务
     *
     * @param doctorId
     * @param startDate
     * @param endDate
     * @return
     * @author ZX
     * @date 2015-4-26 下午2:54:22
     */
    @RpcService
    public List<DoctorAccountDetail> findOutTotalByMonth(Integer doctorId,
                                                         Date startDate, Date endDate) {
        return findTotalByMonth(doctorId, 2, startDate, endDate);
    }

    /**
     * 按日份统计收入支出服务
     *
     * @param doctorId
     * @param inout
     * @param startDate
     * @param endDate
     * @return
     * @author LF
     */
    @RpcService
    @DAOMethod(sql = "SELECT CONCAT(YEAR(createDate),'-',MONTH(createDate),'-',DAY(createDate)),SUM(money) FROM DoctorAccountDetail WHERE doctorId=:doctorId and inout=:inout AND CreateDate>=:startDate AND CreateDate<=:endDate GROUP BY CONCAT(YEAR(createDate),'-',MONTH(createDate),'-',DAY(createDate)) ORDER BY YEAR(createDate),MONTH(createDate),DAY(createDate)")
    public abstract List<DoctorAccountDetail> findTotalByDay(
            @DAOParam("doctorId") Integer doctorId,
            @DAOParam("inout") Integer inout,
            @DAOParam("startDate") Date startDate,
            @DAOParam("endDate") Date endDate);

    /**
     * 按日份统计【收入】服务
     *
     * @param doctorId
     * @param startDate
     * @param endDate
     * @return
     * @author LF
     */
    @RpcService
    public List<DoctorAccountDetail> findInTotalByDay(Integer doctorId,
                                                      Date startDate, Date endDate) {
        return findTotalByDay(doctorId, 1, startDate, endDate);
    }

    /**
     * 按日份统计【支出】服务
     *
     * @param doctorId
     * @param startDate
     * @param endDate
     * @return
     * @author LF
     */
    @RpcService
    public List<DoctorAccountDetail> findOutTotalByDay(Integer doctorId,
                                                       Date startDate, Date endDate) {
        return findTotalByDay(doctorId, 2, startDate, endDate);
    }

    /**
     * 按日统计当月
     *
     * @param doctorId
     * @param inout
     * @return
     * @author LF
     */
    @RpcService
    public List<DoctorAccountDetail> findTotalByMonthNow(Integer doctorId,
                                                         Integer inout) {
        Date startDate = DateConversion.firstDayOfThisMonth();
        List<DoctorAccountDetail> accountDetails = findTotalByDay(doctorId,
                inout, startDate, new Date());
        return accountDetails;
    }

    /**
     * 服务类别统计【收入支出】方法
     *
     * @param doctorId  医生id
     * @param inout     1：收入，2支出
     * @param startDate 统计开始时间
     * @param endDate   统计结束时间
     * @return
     * @author ZX
     * @date 2015-4-26 下午2:43:10
     */
    @RpcService
    @DAOMethod(sql = "Select a.serverId,b.serverName ,sum(a.money) from DoctorAccountDetail a, ServerPrice b where a.serverId=b.serverId and a.inout=:inout  and  a.doctorId=:doctorId and DATE(a.createDate)>= :startDate and DATE(a.createDate)<= :endDate group by  a.serverId,b.serverName")
    public abstract List<Object> findTotalByType(
            @DAOParam("doctorId") int doctorId,
            @DAOParam("inout") Integer inout,
            @DAOParam("startDate") Date startDate,
            @DAOParam("endDate") Date endDate);

    @DAOMethod(sql = "Select a.bussType,a.serverId,b.serverName ,sum(a.money) from DoctorAccountDetail a, ServerPrice b where a.serverId=b.serverId and a.inout=:inout  and  a.doctorId=:doctorId and DATE(a.createDate)>= :startDate and DATE(a.createDate)<= :endDate group by  a.serverId,b.serverName")
    public abstract List<Object> findTotalAccountByType(
            @DAOParam("doctorId") int doctorId,
            @DAOParam("inout") Integer inout,
            @DAOParam("startDate") Date startDate,
            @DAOParam("endDate") Date endDate);

    /**
     * 服务类别统计【收入】方法
     *
     * @param doctorId  医生id
     * @param startDate 统计开始时间
     * @param endDate   统计结束时间
     * @return
     * @author ZX
     * @date 2015-4-26 下午2:14:14
     */
    @RpcService
    public List<Object> findInTotalByType(int doctorId, Date startDate, Date endDate) {
        return findTotalByType(doctorId, 1, startDate, endDate);
    }

    /**
     * 服务类别统计【收入】方法
     *
     * @param doctorId  医生id
     * @param startDate 统计开始时间
     * @param endDate   统计结束时间
     * @return
     * @author ZX
     * @date 2015-4-26 下午2:14:14
     */
    @RpcService
    public List<Object> findInTotalAccountByType(int doctorId, Date startDate, Date endDate) {
        return findTotalAccountByType(doctorId, 1, startDate, endDate);
    }

    /**
     * 服务类别统计【支出】方法
     *
     * @param doctorId  医生id
     * @param startDate 统计开始时间
     * @param endDate   统计结束时间
     * @return
     * @author ZX
     * @date 2015-4-26 下午2:14:14
     */
    @RpcService
    public List<Object> findOutTotalByType(int doctorId, Date startDate, Date endDate) {
        return findTotalByType(doctorId, 2, startDate, endDate);
    }

    /**
     * 申请提现服务
     *
     * @param d
     * @author hyj
     */
    @RpcService
    public void requestPay(final DoctorAccountDetail d) {
        HibernateStatelessResultAction<DoctorAccount> action = new AbstractHibernateStatelessResultAction<DoctorAccount>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                if (d.getDoctorId() == null) {
                    throw new DAOException(DAOException.VALUE_NEEDED, "doctorId is required");
                }
                if (d.getMoney() == null) {
                    throw new DAOException(DAOException.VALUE_NEEDED, "money is required");
                }
                if (StringUtils.isEmpty(d.getPayMode())) {
                    throw new DAOException(DAOException.VALUE_NEEDED, "payMode is required");
                }
                if (d.getMoney().compareTo(new BigDecimal(0d)) <= 0) {
                    throw new DAOException(609, "请输入大于0的有效金额");
                }
                if (new BigDecimal(d.getMoney().intValue()).compareTo(d.getMoney()) < 0) {
                    throw new DAOException(609, "请输入整数金额");
                }

                DoctorAccountDAO dao = DAOFactory.getDAO(DoctorAccountDAO.class);
                DoctorBankCardDAO cardDao = DAOFactory.getDAO(DoctorBankCardDAO.class);
                DoctorAccount DoctorAccount = dao.getByDoctorId(d.getDoctorId());

                //2016-10-14 app3.6提现修改为只能银行卡提现，为了兼容旧APP的使用，
                // 同时运营平台能正常打款，将接口进行修改:当提现方式为银行卡提现，且入参中没有绑定关系主键，
                // 则从原来的账户信息中取银行卡信息-zx
                Integer bankCardId = d.getBankCardId();
                if (d.getPayMode().equals("3")) {
                    if (bankCardId == null) {
                        d.setCardName(DoctorAccount.getCardName());
                        d.setCardNo(DoctorAccount.getCardNo());
                        d.setSubBank(DoctorAccount.getSubBank());
                        d.setBankName(DoctorAccount.getBankCode());
                    } else {
                        DoctorBankCard card = cardDao.get(bankCardId);
                        if(card==null){
                            throw new DAOException(ErrorCode.SERVICE_ERROR, "银行卡信息无效，请选择新的银行卡");
                        }
                        int status=card.getStatus()==null?DoctorBankCardConstant.BANK_CARD_STATUS_NOEFF:card.getStatus().intValue();

                        if ( DoctorBankCardConstant.BANK_CARD_STATUS_NOEFF == status) {
                            throw new DAOException(ErrorCode.SERVICE_ERROR, "银行卡信息无效，请选择新的银行卡");
                        }
                        d.setCardName(card.getCardName());
                        d.setCardNo(card.getCardNo());
                        d.setSubBank(card.getSubBank());
                        d.setBankName(card.getBankName());
                    }

                }

                // 目前控制每周二才能提现，后期改成配置
                //20160314-需求变更：将周二提现的限制去掉-zx
//				Calendar c = Calendar.getInstance();
//				c.setTime(new Date());
//				if (c.get(Calendar.DAY_OF_WEEK) != 3) {
//					throw new DAOException(609, "请在每周二进行提现操作");
//				}

                d.setCreateDate(new Date());
                d.setInout(2);
                d.setPayStatus(0);
                save(d);

                // 更新账户表中的支出金额

                BigDecimal money = DoctorAccount.getPayOut().add(d.getMoney());
                dao.updatePayOut(money, d.getDoctorId(), DoctorAccount.getPayOut(), d.getMoney());
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
    }

    /**
     * 去除周二验证（供前端测试）
     *
     * @param d
     */
    @RpcService
    public void requestPayToIOS(final DoctorAccountDetail d) {
        HibernateStatelessResultAction<DoctorAccount> action = new AbstractHibernateStatelessResultAction<DoctorAccount>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                if (d.getDoctorId() == null) {
                    throw new DAOException(DAOException.VALUE_NEEDED, "doctorId is required");
                }
                if (d.getMoney() == null) {
                    throw new DAOException(DAOException.VALUE_NEEDED, "money is required");
                }
                if (StringUtils.isEmpty(d.getPayMode())) {
                    throw new DAOException(DAOException.VALUE_NEEDED, "payMode is required");
                }
                if (d.getMoney().compareTo(new BigDecimal(0d)) <= 0) {
                    throw new DAOException(609, "请输入大于0的有效金额");
                }
                if (new BigDecimal(d.getMoney().intValue()).compareTo(d.getMoney()) < 0) {
                    throw new DAOException(609, "请输入整数金额");
                }
                // 目前控制每周二才能提现，后期改成配置
                /*
                 * Calendar c = Calendar.getInstance(); c.setTime(new Date());
				 * if (c.get(Calendar.DAY_OF_WEEK) != 3) { throw new
				 * DAOException(609, "请在每周二进行提现操作"); }
				 */

                d.setCreateDate(new Date());
                d.setInout(2);
                d.setPayStatus(0);
                save(d);

                // 更新账户表中的支出金额
                DoctorAccountDAO dao = DAOFactory.getDAO(DoctorAccountDAO.class);
                DoctorAccount DoctorAccount = dao.getByDoctorId(d.getDoctorId());
                BigDecimal money = DoctorAccount.getPayOut().add(d.getMoney());
                dao.updatePayOut(money, d.getDoctorId(), DoctorAccount.getPayOut(), d.getMoney());
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
    }

    /**
     * 查询申请提现信息
     *
     * @return
     * @author hyj
     */
    @RpcService
    @DAOMethod(sql = "select new eh.entity.base.DoctorAccountDetailAndDoctorAccount(a,b) from DoctorAccountDetail a,DoctorAccount b where a.doctorId=b.doctorId and a.payStatus=0")
    public abstract List<DoctorAccountDetailAndDoctorAccount> findByPayStatus();

    /**
     * 查询申请中的提现信息
     *
     * @return
     * @author ZX
     * @date 2015-8-7 上午11:27:46
     */
    @RpcService
    @DAOMethod(limit = 99999999)
    public abstract List<DoctorAccountDetail> findByPayStatusAndInout(
            int payStatus, int inout);

    /**
     * 查询申请中的提现信息
     *
     * @return
     * @author ZX
     * @date 2015-8-7 上午11:27:46
     */
    @RpcService
    @DAOMethod(sql = "select a from DoctorAccountDetail a, Doctor b where a.doctorId=b.doctorId and a.inout =:inout and a.payStatus =:payStatus and ( b.testPersonnel = 0 or b.testPersonnel is null)", limit = 99999999)
    public abstract List<DoctorAccountDetail> findNotTestDoctorByPayStatusAndInout(@DAOParam("payStatus") int payStatus,
                                                                                   @DAOParam("inout") int inout);

    /**
     * 医生申请提现信息查询服务
     *
     * @param doctorId  --医生内码
     * @param payStatus --提现标记
     * @return
     * @author hyj
     */
    @RpcService
    @DAOMethod
    public abstract List<DoctorAccountDetail> findByDoctorIdAndPayStatus(
            int doctorId, int payStatus);

    /**
     * 医生提现记录查询服务
     *
     * @param doctorId
     * @return
     * @author hyj
     */
    @RpcService
    @DAOMethod(sql = "from DoctorAccountDetail where doctorId=:doctorId and inout=2")
    public abstract List<DoctorAccountDetail> findByDoctorIdAndInout(
            @DAOParam("doctorId") int doctorId);

    /**
     * 医生提现记录查询服务，按提现日期倒序
     *
     * @param doctorId
     * @return
     * @author zhangx
     * @date 2016-3-2 上午9:59:11
     */
    @RpcService
    @DAOMethod(sql = "from DoctorAccountDetail where doctorId=:doctorId and inout=2 order by createDate desc")
    public abstract List<DoctorAccountDetail> findByDoctorIdAndInoutOrder(
            @DAOParam("doctorId") int doctorId);


	/*
     * @DAOMethod(sql=
	 * "select sum(money) from DoctorAccountDetail where doctorId=:doctorId")
	 * public abstract Object addMoney(@DAOParam("doctorId")int doctorId);
	 */

    @RpcService
    public BigDecimal addMoney(final int doctorId) throws DAOException {

        HibernateStatelessResultAction<Object> action = new AbstractHibernateStatelessResultAction<Object>() {
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "select sum(money) from DoctorAccountDetail where doctorId=:doctorId and inout=1");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("doctorId", doctorId);
                BigDecimal totalInCount = (BigDecimal) q.uniqueResult();
                setResult(totalInCount);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        BigDecimal result1 = (BigDecimal) action.getResult();

        HibernateStatelessResultAction<Object> action2 = new AbstractHibernateStatelessResultAction<Object>() {
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        "select sum(money) from DoctorAccountDetail where doctorId=:doctorId and inout=2");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("doctorId", doctorId);
                BigDecimal totalOutCount = (BigDecimal) q.uniqueResult();
                setResult(totalOutCount);
            }
        };
        HibernateSessionTemplate.instance().execute(action2);
        BigDecimal result2 = (BigDecimal) action2.getResult();
        BigDecimal result = new BigDecimal(0d);
        if (result2 != null) {
            result = result1.subtract(result2);
        } else {
            result = result1;
        }

        return result;
    }

    /**
     * 根据提现状态查询医师提现记录(分页)
     *
     * @param payStatus
     * @return
     */
    @RpcService
    public List<DoctorAndAccountAndDetail> findRecordByPayStatusPage(
            final int payStatus, final int start) {
        HibernateStatelessResultAction<List<DoctorAndAccountAndDetail>> action = new AbstractHibernateStatelessResultAction<List<DoctorAndAccountAndDetail>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = new String(
                        "select new eh.entity.base.DoctorAndAccountAndDetail(b,c,a,a.createDate)  from DoctorAccountDetail a, Doctor b, DoctorAccount c where a.doctorId=b.doctorId  and a.doctorId=c.doctorId  and b.doctorId=c.doctorId and  a.inout =2 and a.payStatus = :payStatus  and ( b.testPersonnel = 0 or b.testPersonnel is null) ");
                Query q = ss.createQuery(hql);
                q.setParameter("payStatus", payStatus);
                q.setMaxResults(10);
                q.setFirstResult(start);
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
     * 查询医师提现历史记录(分页)
     *
     * @param startDate
     * @return
     */
    @RpcService
    public List<DoctorAndAccountAndDetail> findCompleteDetailPage(
            final Date startDate, final Date endDate, final int start) {
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
     * @author ZX
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
     * 审核医师提现记录
     */
    @RpcService
    @DAOMethod(sql = "update DoctorAccountDetail set payStatus=2 where payStatus=0 ")
    public abstract void updateRecordChecked();

    /**
     * 审核医师提现记录
     */
    @RpcService
    @DAOMethod(sql = "update DoctorAccountDetail set billId=:billId,closure=0,payStatus=2 where payStatus=0 ")
    public abstract void updateBillId(@DAOParam("billId") String billId);

    /**
     * 审核医师提现记录
     */
    @RpcService
    @DAOMethod(sql = "update DoctorAccountDetail set billId=:billId,closure=:closure,payStatus=:payStatus where payStatus=0 and accountDetailId=:accountDetailId ")
    public abstract void updateBillIdByAccountDetailId(@DAOParam("billId") String billId,
                                                       @DAOParam("closure") Integer closure,
                                                       @DAOParam("payStatus") Integer payStatus,
                                                       @DAOParam("accountDetailId") Integer accountDetailId);

    /**
     * 完成提现记录
     */
    @RpcService
    @DAOMethod(sql = "update DoctorAccountDetail set payStatus=1,payDate=current_timestamp() where payStatus=2 ")
    public abstract void updateRecordComplete();

    /**
     * 完成提现记录
     */
    @RpcService
    @DAOMethod(sql = "update DoctorAccountDetail set payStatus=1,payDate=current_timestamp(),closure=1 where payStatus=2 and billId=:billId")
    public abstract void updateAccountDetailComplete(
            @DAOParam("billId") String billId);

    /**
     * 获取医生当日业务收支数
     *
     * @param doctorId 医生id
     * @param bussType 1转诊；2会诊；3咨询；4预约
     * @param serverId 服务序号(1:转诊申请；2：转诊接收；3：会诊申请；4：会诊普通医生处理；5：健康咨询;6:预约服务;7预约取消;8
     *                 转诊申请首单;10转诊接收首单;11会诊申请首单;12会诊接收首单;13咨询首单;14预约首单;15预约首单取消;16
     *                 会诊副主任医生以上处理 )
     * @return
     * @author ZX
     * @date 2015-6-25 下午3:46:59
     */
    @RpcService
    @DAOMethod(sql = "select count(*) from DoctorAccountDetail where doctorId=:doctorId and date(createDate)=date(now()) and bussType=:bussType and serverId=:serverId and inout=:inout")
    public abstract long getNumForDoctor(@DAOParam("doctorId") int doctorId,
                                         @DAOParam("bussType") int bussType,
                                         @DAOParam("serverId") int serverId, @DAOParam("inout") int inout);

    @DAOMethod(sql = "select count(*) from DoctorAccountDetail where doctorId=:doctorId and bussType=:bussType and serverId=:serverId and inout=:inout")
    public abstract long getAllNumForDoctor(@DAOParam("doctorId") int doctorId,
                                            @DAOParam("bussType") int bussType,
                                            @DAOParam("serverId") int serverId, @DAOParam("inout") int inout);

    @RpcService
    @DAOMethod(sql = "select count(*) from DoctorAccountDetail detail, AppointRecord app where detail.bussId = app.appointRecordId and detail.doctorId =:doctorId and (telClinicFlag=0 or telClinicFlag is null) and date(app.appointDate) = date(now()) and detail.bussType =:bussType and detail.serverId =:serverId and detail.inout=:inout and app.appointRoad = :appointRoad")
    public abstract long getAppointNumForDoctor(
            @DAOParam("doctorId") int doctorId,
            @DAOParam("bussType") int bussType,
            @DAOParam("serverId") int serverId, @DAOParam("inout") int inout,
            @DAOParam("appointRoad") int appointRoad);

    @RpcService
    @DAOMethod(sql = "select count(*) from DoctorAccountDetail detail, AppointRecord app where detail.bussId = app.appointRecordId and detail.doctorId =:doctorId and telClinicFlag=:telClinicFlag and date(app.appointDate) = date(now()) and detail.bussType =:bussType and detail.serverId =:serverId and detail.inout=:inout and app.appointRoad = :appointRoad")
    public abstract long getCloudAppointNumForDoctor(
            @DAOParam("telClinicFlag") int telClinicFlag,
            @DAOParam("doctorId") int doctorId,
            @DAOParam("bussType") int bussType,
            @DAOParam("serverId") int serverId, @DAOParam("inout") int inout,
            @DAOParam("appointRoad") int appointRoad);

    /**
     * 根据提现单获取提现记录
     *
     * @param billId
     * @return
     * @author ZX
     * @date 2015-8-10 下午7:17:44
     */
    @RpcService
    public List<DoctorAccountDetail> findByBillId(final String billId) {
        HibernateStatelessResultAction<List<DoctorAccountDetail>> action = new AbstractHibernateStatelessResultAction<List<DoctorAccountDetail>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(" from DoctorAccountDetail where 1=1 ");
                HashMap<String, Object> params = new HashMap<String, Object>();
                if (!ObjectUtils.isEmpty(billId)) {
                    hql.append(" and billId =:billId  ");
                    params.put("billId", billId);
                }
                hql.append(" order by createDate desc ");
                Query query = ss.createQuery(hql.toString());
                query.setProperties(params);
                List<DoctorAccountDetail> list = query.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (List<DoctorAccountDetail>) action.getResult();
    }

    /**
     * Title: 查询提现记录 Description: 照开始时间结束时间查询提现记录（上限100）
     *
     * @param startDate -----开始时间
     * @param endDate   -----结束时间
     * @param start     -----起始位置
     * @return List<DoctorAccountDetail>
     * @author AngryKitty
     * @date 2015-9-6
     */
    @RpcService
    public List<DoctorAccountDetail> findByDate(final Date startDate,
                                                final Date endDate, final int start) {
        HibernateStatelessResultAction<List<DoctorAccountDetail>> action = new AbstractHibernateStatelessResultAction<List<DoctorAccountDetail>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String hql = null;
                hql = new String(
                        " from DoctorAccountDetail  where createDate >=:startDate and createDate <=:endDate and payStatus=0 and inout=2 ");

                Query q = ss.createQuery(hql);

                q.setParameter("startDate", startDate);
                q.setParameter("endDate", endDate);

                q.setMaxResults(100);
                q.setFirstResult(start);
                @SuppressWarnings("unchecked")
                List<DoctorAccountDetail> list = q.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (List<DoctorAccountDetail>) action.getResult();
    }

    /**
     * Title: 根据账户明细号查询查询账户明细信息
     *
     * @param accountDetailId -----帐户明细序号
     * @return DoctorAccountDetail
     * @author AngryKitty
     * @date 2015-9-6
     */
    @RpcService
    @DAOMethod
    public abstract DoctorAccountDetail getByAccountDetailId(
            Integer accountDetailId);

    /**
     * Title: 根据账户明细号查询更新账户明细信息
     *
     * @param accountDetailId ---帐户明细序号
     *                        <p>
     *                        void
     * @author AngryKitty
     * @date 2015-9-6
     */
    @RpcService
    @DAOMethod(sql = "update DoctorAccountDetail set payStatus=1,payDate=current_timestamp(),closure=1 where payStatus=2 and accountDetailId=:accountDetailId")
    public abstract void updateAccountDetailCompleteByID(
            @DAOParam("accountDetailId") Integer accountDetailId);

    @RpcService
    @DAOMethod(sql = "from DoctorAccountDetail where bussType=:bussType and bussId=:bussId and doctorId=:doctorId")
    public abstract DoctorAccountDetail getByBussTypeAndBussId(
            @DAOParam("bussType") Integer bussType,
            @DAOParam("bussId") Integer bussId,
            @DAOParam("doctorId") Integer doctorId);

    public BigDecimal getByDoctorIdAndServerIdAndBussIdAndBussType(
            final Integer doctorId, final Integer serverId, final Integer bussId, final Integer bussType) {

        HibernateStatelessResultAction<BigDecimal> action = new AbstractHibernateStatelessResultAction<BigDecimal>() {
            public void execute(StatelessSession ss) throws DAOException {
                String hql = new String("select money from DoctorAccountDetail " +
                        "where doctorId=:doctorId and serverId=:serverId and bussId=:bussId and bussType=:bussType");
                Query q = ss.createQuery(hql);
                q.setParameter("doctorId", doctorId);
                q.setParameter("serverId", serverId);
                q.setParameter("bussId", bussId);
                q.setParameter("bussType", bussType);
                q.setMaxResults(1);
                setResult((BigDecimal) q.uniqueResult());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }

    /**
     * 用于检查指定业务是否已经奖励
     *
     * @author zhangx
     * @date 2016-1-18 下午2:25:04
     */
    @RpcService
    @DAOMethod(sql = "select count(*) from DoctorAccountDetail where serverId=:serverId and bussId=:bussId and doctorId=:doctorId and inout=:inout")
    public abstract Long getByServerIdAndBussId(
            @DAOParam("serverId") Integer serverId,
            @DAOParam("bussId") Integer bussId,
            @DAOParam("doctorId") Integer doctorId, @DAOParam("inout") int inout);

    /**
     * 获取最近提现方式
     * <p>
     * eh.base.dao
     *
     * @param doctorId 登陆医生内码
     * @return String
     * @author luf 2016-2-15
     * @date 2016-3-8 luf 修改排序条件及删除提现成功限制
     */
    @RpcService
    public String getLastModeByDoctor(final int doctorId) {
        String mode = "1";
        HibernateStatelessResultAction<String> action = new AbstractHibernateStatelessResultAction<String>() {
            public void execute(StatelessSession ss) throws DAOException {
                String hql = "SELECT payMode FROM DoctorAccountDetail WHERE inout=2 AND doctorId=:doctorId order by createDate DESC";
                Query q = ss.createQuery(hql);
                q.setParameter("doctorId", doctorId);
                q.setMaxResults(1);
                setResult((String) q.uniqueResult());
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        String result = action.getResult();
        if (!StringUtils.isEmpty(result)) {
            mode = result;
        }
        return mode;
    }


    @RpcService
    @DAOMethod(sql = "select sum(money)  from DoctorAccountDetail where doctorId=:doctorId and inout=:inout")
    public abstract BigDecimal getSumMoneyByDoctorIdAndInout(@DAOParam("doctorId") int doctorId, @DAOParam("inout") int inout);

    @DAOMethod(limit = 1, sql = "from DoctorAccountDetail where doctorId=:doctorId and inout=2 and payMode=3 order by createDate desc")
    public abstract List<DoctorAccountDetail> findLastOutDetail(@DAOParam("doctorId") int doctorId);


    /**
     * 运营平台根据医生机构,医生id,支付方式查询医生提现信息
     *
     * @param payMode   提现方式 "1"[1支付宝3银行卡] "2"手机充值
     * @param payStatus 提现状态 0申请中 1完成 2审核中 3审核不通过
     * @param organId   医生所在机构
     * @param doctorId  医生内码
     * @return
     */
    public List<DoctorAccountDetail> findWithdrawDoctorByOrganIdAndInout(final String payMode, final Integer payStatus,
                                                                         final Integer organId, final Integer doctorId) {
        HibernateStatelessResultAction<List<DoctorAccountDetail>> action = new AbstractHibernateStatelessResultAction<List<DoctorAccountDetail>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        " select a from DoctorAccountDetail a, Doctor b where a.doctorId=b.doctorId and a.inout =2 and ( b.testPersonnel = 0 or b.testPersonnel is null)");
                HashMap<String, Object> params = new HashMap<String, Object>();
                if (StringUtils.equalsIgnoreCase(payMode, "1")) {
                    hql.append(" and a.payMode in (1,3) ");
                } else {
                    hql.append(" and a.payMode =2 ");
                }
                if (!ObjectUtils.isEmpty(payStatus)) {
                    hql.append(" and a.payStatus =:payStatus  ");
                    params.put("payStatus", payStatus);
                }
                if (!ObjectUtils.isEmpty(organId)) {
                    hql.append(" and b.organ =:organId ");
                    params.put("organId", organId);
                }
                if (!ObjectUtils.isEmpty(doctorId)) {
                    hql.append(" and a.doctorId =:doctorId  ");
                    params.put("doctorId", doctorId);
                }
                hql.append(" order by a.doctorId,a.createDate desc ");
                Query query = ss.createQuery(hql.toString());
                query.setProperties(params);
                List<DoctorAccountDetail> list = query.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (List<DoctorAccountDetail>) action.getResult();
    }


    /**
     * 医生提现记录
     *
     * @param billId   提现批号
     * @param doctorId 医生内码
     * @return
     */
    @DAOMethod(sql = "from DoctorAccountDetail where billId=:billId and doctorId=:doctorId and inout=2")
    public abstract List<DoctorAccountDetail> findAccountDetailByBillIdAndDoctorId(@DAOParam("billId") String billId, @DAOParam("doctorId") Integer doctorId);

    /**
     * 获取收支次数
     *
     * @param doctorId
     * @param inout
     * @return
     */
    @DAOMethod(sql = "select count(*) from DoctorAccountDetail where doctorId=:doctorId and inout=:inout")
    public abstract Long getCountByDoctorId(
            @DAOParam("doctorId") Integer doctorId, @DAOParam("inout") int inout);

    @DAOMethod(sql = " select inout,sum(money) from DoctorAccountDetail where doctorId=:doctorId and createDate>=:bDate and createDate<=:eDate group by inout order by inout  ")
    public abstract List<Object> findSumGroupByInout(@DAOParam("doctorId") Integer doctorId, @DAOParam("bDate") Date bDate, @DAOParam("eDate") Date eDate);

    /**
     * 分页获取医生账户详细信息
     * @param doctorId
     * @param bDate
     * @param eDate
     * @param start
     * @param limit
     * @return
     */
    public QueryResult<DoctorAccountDetail> queryDoctorAccountDetailByDate(final Integer doctorId, final Date bDate, final Date eDate,final Integer inout, final int start, final int limit) {
        if (doctorId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "doctorId is require");
        }
        if (bDate == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "bDate is require");
        }
        if (eDate == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "eDate is require");
        }
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        HibernateStatelessResultAction<QueryResult<DoctorAccountDetail>> action = new AbstractHibernateStatelessResultAction<QueryResult<DoctorAccountDetail>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(
                        " from DoctorAccountDetail where doctorId=");
                hql.append(doctorId).append(" And createDate>='").append(sdf.format(bDate)).append(" 00:00:00' And createDate<='").append(sdf.format(eDate)).append(" 23:59:59'");
                if(inout!=null&&inout.intValue()!=0){
                   hql.append(" And inout =").append(inout);
               }
                Query countQuery = ss.createQuery(" select count (*) "+hql.toString());
                Long total = (Long) countQuery.uniqueResult();
                Query query = ss.createQuery(hql.toString()+" order by createDate desc");
                query.setFirstResult(start);
                query.setMaxResults(limit);
                List<DoctorAccountDetail> list = query.list();
                if(list==null){
                    list = new ArrayList<DoctorAccountDetail>();
                }
                setResult(new QueryResult<DoctorAccountDetail>(total,start,list.size(),list));
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return action.getResult();
    }


}
