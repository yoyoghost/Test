package eh.base.dao;

import ctd.account.UserRoleToken;
import ctd.net.broadcast.*;
import ctd.persistence.DAOFactory;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessAction;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.AppContextHolder;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.service.DoctorAccountService;
import eh.base.service.DoctorRevenueService;
import eh.bus.service.consult.OnsConfig;
import eh.entity.base.*;
import eh.entity.msg.SmsInfo;
import eh.push.SmsPushService;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.StatelessSession;
import org.joda.time.format.DateTimeFormat;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.*;

public abstract class DoctorAccountDAO extends
        HibernateSupportDelegateDAO<DoctorAccount> {
    public static final Logger log = Logger.getLogger(DoctorAccountDAO.class);


    public DoctorAccountDAO() {
        super();
        this.setEntityName(DoctorAccount.class.getName());
        this.setKeyField("doctorId");
    }

    /**
     * 添加新的医生账户
     *
     * @param doctorId
     * @return
     * @author LF
     */
    public void saveNewDoctorAccount(Integer doctorId) {
        DoctorAccount account = new DoctorAccount();
        account.setDoctorId(doctorId);
        account.setInCome(new BigDecimal(0d));
        account.setPayOut(new BigDecimal(0d));

        if (!exist(doctorId)) {
            save(account);
        }
    }

    /**
     * 根据主键更新收入
     *
     * @param inCome
     * @author ZX
     * @date 2015-4-26 下午6:02:41
     */
    @DAOMethod
    public abstract void updateInComeByDoctorId(BigDecimal inCome, int doctorId);

    /**
     * 帐户查询方法
     *
     * @param doctorId
     * @return
     * @author LF
     */
    @RpcService
    @DAOMethod
    public abstract DoctorAccount getByDoctorId(Integer doctorId);

    /**
     * 推荐奖励
     *
     * @param doctorId 医生B的doctorId
     * @desc 医生A给予邀请码给医生B，医生B注册并且成功进行了一次有效业务操作，则医生A给予推荐奖励，医生B标记为已奖励
     * @author zhangx
     * @date 2015-11-18 上午11:57:09
     */
    public void recommendReward(int doctorId) {

        final DoctorDAO docDao = DAOFactory.getDAO(DoctorDAO.class);
        final Doctor doc = docDao.getByDoctorId(doctorId);
        if (doc == null) {
            log.error("医生[" + doctorId + "]不存在,无法进行相应的推荐奖励");
            return;
        }

        if (doc.getSource() == null || doc.getSource() != 1
                || doc.getInvitationCode() == null
                || (doc.getRewardFlag() != null && doc.getRewardFlag())) {
            log.info("医生[" + doctorId + "]不符合奖励条件，无法进行相应的推荐奖励");
            return;
        }

        final int rewardDoctorId = doc.getInvitationCode();
        final int newServerId = 19;

        if (docDao.exist(rewardDoctorId) && doc.getSource() == 1
                && !doc.getRewardFlag()) {
            HibernateStatelessResultAction<DoctorAccount> action = new AbstractHibernateStatelessResultAction<DoctorAccount>() {
                @Override
                public void execute(StatelessSession ss) throws Exception {

                    // 判断是否存在医生账户，不存在的话，新增账户
                    if (!exist(rewardDoctorId)) {
                        saveNewDoctorAccount(rewardDoctorId);
                    }

                    // 查询服务价格
                    ServerPriceDAO serverPriceDAO = DAOFactory
                            .getDAO(ServerPriceDAO.class);
                    ServerPrice serverPrice = serverPriceDAO
                            .getByServerId(newServerId);
                    if (serverPrice == null) {
                        throw new DAOException(404, "ServerPrice["
                                + newServerId + "] not exist");
                    }

                    // 计算此次收入
                    BigDecimal price = serverPrice.getPrice();

                    // 如果收入为0元，则不更新账户，不添加收入
                    if (price.compareTo(BigDecimal.ZERO) == 0) {
                        return;
                    }

                    // 新增收支明细
                    DoctorAccountDetail detail = new DoctorAccountDetail();
                    detail.setDoctorId(rewardDoctorId);
                    detail.setInout(1);
                    detail.setCreateDate(new Date());
                    detail.setSummary(serverPrice.getServerName());
                    detail.setMoney(price);
                    detail.setServerId(newServerId);
                    detail.setBussType(serverPrice.getBussType());
                    detail.setBussId(0);

                    DoctorAccountDetailDAO detailDAO = DAOFactory
                            .getDAO(DoctorAccountDetailDAO.class);
                    detailDAO.save(detail);

                    // 更新医生账户
                    DoctorAccount account = getByDoctorId(rewardDoctorId);
                    BigDecimal newPrice = account.getInCome().add(price);

                    updateInComeByDoctorId(newPrice, rewardDoctorId);

                    // 更新医生B状态为已奖励
                    doc.setRewardFlag(true);
                    docDao.update(doc);

                }
            };
            HibernateSessionTemplate.instance().executeTrans(action);

            pushMsgForRecommendReward(rewardDoctorId,doctorId);

        }

    }

    /**
     * 增加患者付费的，医生账户收入
     *
     * @param doctorId 医生序号
     * @param serverId 服务序号(1转诊申请；2转诊接收；3会诊申请；4会诊普通医生处理；5健康咨询;6预约服务;7预约取消;8
     *                 转诊申请首单;10转诊接收首单;11会诊申请首单;12会诊接收首单;13咨询首单;14预约首单;15预约首单取消;16
     *                 会诊副主任医生以上处理 17活动奖励；18预约取消-转诊自己转给自己
     *                 ;19推荐奖励;20电话咨询;21图文咨询;22特需预约;23远程门诊预约服务;24远程门诊预约取消;
     *                 25首单奖励;26远程门诊预约取消33专家解读完成收入
     *            32寻医问药完成收入)
     * @param bussId   业务序号（预约、咨询、转诊、会诊等业务单的序号）
     * @param docPrice 业务设置的收入
     * @author zhangx
     * @date 2016-1-18 上午10:53:48
     */
    public void addDoctorRevenue(final int doctorId, final int serverId,
                                 final int bussId, Double docPrice) {
        log.info("DoctorAccountDAO.addDoctorRevenue doctorId[" + doctorId + "],ServerPrice[" + serverId + "]，" +
                "bussId[" + bussId + "],docPrice["+docPrice+"]");
        try {
            AccountInfo info=new AccountInfo();
            info.setDoctorId(doctorId);
            info.setServerId(serverId);
            info.setBussId(bussId);
            info.setCost(docPrice);
            AccountInfoDAO infoDao=DAOFactory.getDAO(AccountInfoDAO.class);
            AccountInfo savedInfo =infoDao.save(info);

            log.info("发送account消息队列:"+ JSONUtils.toString(savedInfo));
            if(StringUtils.isEmpty(OnsConfig.accountTopic)){
                log.info("ons消息队列accountTopic为空,不发消息");
                return;
            }
            MQHelper.getMqPublisher().publish(OnsConfig.accountTopic, savedInfo);
        } catch (Exception e) {
            log.error("AccountInfo:serverId="+serverId + ",bussId="+bussId+" send to ons failed:" + e.getMessage());
        }

    }

    /**
     * 供其他方法使用
     * @param doctorId
     * @param serverId
     * @param bussId
     * @param docPrice
     */
    public void addDoctorAccount( final int doctorId, final int serverId,
                                  final int bussId, final BigDecimal price,final ServerPrice serverPrice){

        // 如果收入为0元，则不更新账户，不添加收入
        if (price.compareTo(BigDecimal.ZERO) == 0) {
            log.info("doctorId["+doctorId+"],serverId["+serverId+"],bussId["+bussId+"]," +
                    "serverPrice["+serverPrice.getServerId()+"]积分为0，不添加至数据库");
            return;
        }

        HibernateStatelessResultAction<DoctorAccount> action = new AbstractHibernateStatelessResultAction<DoctorAccount>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {

                // 判断是否存在医生账户，不存在的话，新增账户
                if (!exist(doctorId)) {
                    saveNewDoctorAccount(doctorId);
                }

                // 新增收支明细
                DoctorAccountDetail detail = new DoctorAccountDetail();
                detail.setDoctorId(doctorId);
                detail.setInout(1);
                detail.setCreateDate(new Date());
                detail.setSummary(serverPrice.getServerName());
                detail.setMoney(price);
                detail.setServerId(serverId);
                detail.setBussType(serverPrice.getBussType());
                detail.setBussId(bussId);

                DoctorAccountDetailDAO detailDAO = DAOFactory
                        .getDAO(DoctorAccountDetailDAO.class);
                detailDAO.save(detail);

                // 更新医生账户
                DoctorAccount account = getByDoctorId(doctorId);
                BigDecimal newPrice = account.getInCome().add(price);

                updateInComeByDoctorId(newPrice, doctorId);

            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
    }

    /**
     * 增加账户奖励
     *
     * @param doctorId 医生序号
     * @param serverId 服务序号(1转诊申请；2转诊接收；3会诊申请；4会诊普通医生处理；5健康咨询;6预约服务;7预约取消;8
     *                 转诊申请首单;10转诊接收首单;11会诊申请首单;12会诊接收首单;13咨询首单;14预约首单;15预约首单取消;16
     *                 会诊副主任医生以上处理 17活动奖励；18预约取消-转诊自己转给自己
     *                 ;19推荐奖励;20电话咨询;21图文咨询;22特需预约;23远程门诊预约服务;24远程门诊预约取消;
     *                 25首单奖励;26远程门诊预约取消)
     * @param bussId   业务序号（预约、咨询、转诊、会诊等业务单的序号）
     * @param addFlag  是否追加奖励 (1：是；0：否)
     * @desc 医生之间发起的业务，给医生的业务奖励
     * @author ZX
     * @date 2015-4-26 下午6:10:32
     */
    @RpcService
    public void addDoctorIncome(final int doctorId, int serverId,
                                final int bussId, final int addFlag) {
        log.error("doctorId[" + doctorId + "],ServerPrice[" + serverId + "]，bussId[" + bussId + "],addFlag[" + addFlag + "]进行奖励");

        //获取奖励的serverId,serverId=null，则不进行奖励
        DoctorAccountService accountSerrvice = new DoctorAccountService();
        final Integer newServerId = accountSerrvice.addDoctorIncome(doctorId, serverId, bussId, addFlag);

        if (newServerId == null) {
            return;
        }

        //判断是否同等级的机构发起的业务(true:同级间业务;false:不同级间业务,默认为不同级)
        Boolean isSameGradeOrgan = accountSerrvice.isSameGradeOrgan(serverId, bussId, doctorId);

        //获取服务价格
        final ServerPrice serverPrice = accountSerrvice.getActualServerPrice(newServerId, bussId, doctorId, addFlag, isSameGradeOrgan);

        HibernateStatelessResultAction<DoctorAccount> action = new AbstractHibernateStatelessResultAction<DoctorAccount>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {

                // 判断是否存在医生账户，不存在的话，新增账户
                if (!exist(doctorId)) {
                    saveNewDoctorAccount(doctorId);
                }

                BigDecimal price = serverPrice.getPrice();

                // 如果收入为0元，则不更新账户，不添加收入
                if (price.compareTo(BigDecimal.ZERO) == 0) {
                    return;
                }

                // 新增收支明细
                DoctorAccountDetail detail = new DoctorAccountDetail();
                detail.setDoctorId(doctorId);
                detail.setInout(1);
                detail.setCreateDate(new Date());
                detail.setSummary(serverPrice.getServerName());
                detail.setMoney(price);
                detail.setServerId(newServerId);
                detail.setBussType(serverPrice.getBussType());
                detail.setBussId(bussId);

                DoctorAccountDetailDAO detailDAO = DAOFactory
                        .getDAO(DoctorAccountDetailDAO.class);
                detailDAO.save(detail);

                // 更新医生账户
                DoctorAccount account = getByDoctorId(doctorId);
                BigDecimal newPrice = account.getInCome().add(price);

                updateInComeByDoctorId(newPrice, doctorId);

            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);

    }


    /**
     * 新增账户设置服务
     *
     * @param d
     * @author hyj
     */
    @RpcService
    public void addOrUpdateDoctorAccount(DoctorAccount d) {
        if (d.getDoctorId() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "doctorId is required");
        }
        if (!exist(d.getDoctorId())) {
            d.setInCome(new BigDecimal(0d));
            d.setPayOut(new BigDecimal(0d));
            save(d);
        } else {
            DoctorAccount target = getByDoctorId(d.getDoctorId());
            BeanUtils.map(d, target);
            update(target);
        }
    }

    @RpcService
    public void updatePayOut(final BigDecimal payOut, final int doctorId,
                             final BigDecimal payOutOld, final BigDecimal money) {
        HibernateSessionTemplate.instance().execute(
                new HibernateStatelessAction() {
                    public void execute(StatelessSession ss) throws Exception {
                        String hql = "update DoctorAccount set payOut=:payOut where doctorId=:doctorId and payOut=:payOutOld and (inCome-payOut)>=:money";

                        Query q = ss.createQuery(hql);
                        q.setBigDecimal("payOut", payOut);
                        q.setInteger("doctorId", doctorId);
                        q.setBigDecimal("payOutOld", payOutOld);
                        q.setBigDecimal("money", money);
                        int num = q.executeUpdate();
                        if (num == 0) {
                            throw new DAOException(609, "提现失败");
                        }
                    }
                });
    }

    @DAOMethod(limit = 1000, sql = "from DoctorAccount")
    public abstract List<DoctorAccount> findDoctorAccount();

    public List<DoctorAccount> checkMoney() {
        List<DoctorAccount> list = this.findDoctorAccount();
        List<DoctorAccount> result = new ArrayList<DoctorAccount>();
        DoctorAccountDetailDAO dao = DAOFactory
                .getDAO(DoctorAccountDetailDAO.class);
        for (DoctorAccount d : list) {
            BigDecimal money = dao.addMoney(d.getDoctorId());
            if (money != null
                    && money.compareTo(d.getInCome().subtract(d.getPayOut())) != 0) {
                result.add(d);
            }
        }
        return result;

    }

    /**
     * 生成提现记录
     *
     * @return
     * @author ZX
     * @date 2015-8-7 上午11:40:24
     */
    @RpcService
    public CashBills createCashBills() {
        // 获取提现记录
        final CashBills bills = new CashBills();

        final DoctorAccountDetailDAO detailDao = DAOFactory.getDAO(DoctorAccountDetailDAO.class);
        final CashBillsDAO billDao = DAOFactory.getDAO(CashBillsDAO.class);

        List<DoctorAccountDetail> detailList = detailDao.findNotTestDoctorByPayStatusAndInout(0, 2);

        //同力人力数据接口对接
        //this.doctorDataTotonglihr(detailList);

        // 计算提现金额
        BigDecimal allMoney = new BigDecimal(0d);
        for (DoctorAccountDetail doctorAccountDetail : detailList) {
            BigDecimal money = doctorAccountDetail.getMoney();
            allMoney = allMoney.add(money);
        }

        int creator = UserRoleToken.getCurrent().getId();
        String creatorName = UserRoleToken.getCurrent().getUserName();

        final String billId = DateTimeFormat.forPattern("yyyyMMddHHmmss")
                .print(new Date().getTime());

        bills.setBillId(billId);
        bills.setCreateDate(new Date());
        bills.setPaystatus(0);// 单据状态 0提现申请中 1提现完成
        bills.setCreator(creator);
        bills.setMoney(allMoney);
        bills.setActualPayment(new BigDecimal(0d));
        bills.setLastModify(new Date());
        bills.setDetailList(detailList);
        bills.setCreatorName(creatorName);

        HibernateStatelessResultAction<CashBills> action = new AbstractHibernateStatelessResultAction<CashBills>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                // 保存提现申请记录
                CashBills cashBill = billDao.save(bills);
                detailDao.updateBillId(billId);

                setResult(cashBill);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);

        return action.getResult();
    }

    /**
     * 完成提现
     *
     * @param billId
     * @author ZX
     * @date 2015-8-11 下午1:34:51
     */
    @RpcService
    public void completeCashBills(final String billId) {
        final DoctorAccountDetailDAO detailDao = DAOFactory
                .getDAO(DoctorAccountDetailDAO.class);
        final CashBillsDAO billDao = DAOFactory.getDAO(CashBillsDAO.class);

        // 申请提现的记录
        BigDecimal acMoney = new BigDecimal(0d);
        List<DoctorAccountDetail> list = detailDao.findByBillId(billId);
        for (DoctorAccountDetail doctorAccountDetail : list) {
            if (doctorAccountDetail.getPayStatus() == 2) {
                BigDecimal money = doctorAccountDetail.getMoney();
                acMoney = acMoney.add(money);
            }
        }

        final CashBills bills = billDao.get(billId);
        bills.setLastModify(new Date());
        bills.setActualPayment(acMoney);
        bills.setPaydate(new Date());
        bills.setPaystatus(1);

        UserRoleToken operator = UserRoleToken.getCurrent();
        bills.setOperator(operator.getId());
        bills.setOperatorName(operator.getUserName());

        HibernateStatelessResultAction<CashBills> action = new AbstractHibernateStatelessResultAction<CashBills>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                // 保存提现申请记录
                CashBills cashBill = billDao.update(bills);
                detailDao.updateAccountDetailComplete(billId);

                setResult(cashBill);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);

    }

    /**
     * Title: 生成提现记录 Description: 按照开始时间结束时间生成提现记录(上限100个)
     *
     * @param startDate ----开始日期
     * @param endDate   ----结束日期
     * @return CashBills
     * @author AngryKitty
     * @date 2015-9-6
     */
    @RpcService
    public CashBills createCashBillsByDate(final Date startDate,
                                           final Date endDate) {
        if (startDate == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "startDate is required");
        }
        if (endDate == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "endDate is required");
        }
        // 获取提现记录
        final CashBills bills = new CashBills();

        final DoctorAccountDetailDAO detailDao = DAOFactory
                .getDAO(DoctorAccountDetailDAO.class);
        final CashBillsDAO billDao = DAOFactory.getDAO(CashBillsDAO.class);

        List<DoctorAccountDetail> detailList = detailDao.findByDate(startDate,
                endDate, 0);

        // 计算提现金额
        BigDecimal allMoney = new BigDecimal(0d);
        for (DoctorAccountDetail doctorAccountDetail : detailList) {
            BigDecimal money = doctorAccountDetail.getMoney();
            allMoney = allMoney.add(money);
        }

        int creator = UserRoleToken.getCurrent().getId();
        String creatorName = UserRoleToken.getCurrent().getUserName();

        final String billId = DateTimeFormat.forPattern("yyyyMMddHHmmss")
                .print(new Date().getTime());

        bills.setBillId(billId);
        bills.setCreateDate(new Date());
        bills.setPaystatus(0);// 单据状态 0提现申请中 1提现完成
        bills.setCreator(creator);
        bills.setMoney(allMoney);
        bills.setActualPayment(new BigDecimal(0d));
        bills.setLastModify(new Date());
        bills.setDetailList(detailList);
        bills.setCreatorName(creatorName);

        HibernateStatelessResultAction<CashBills> action = new AbstractHibernateStatelessResultAction<CashBills>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                // 保存提现申请记录
                CashBills cashBill = billDao.save(bills);
                detailDao.updateBillId(billId);

                setResult(cashBill);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);

        return action.getResult();
    }

    /**
     * Title: 生成提现记录 Description: 勾选生成提现记录(传入提现单号列表)
     *
     * @param detailList ----需要生成体现的账户明细数据
     * @return CashBills
     * @author AngryKitty
     * @date 2015-9-6
     */
    @RpcService
    public CashBills createCashBillsByDetails(
            final List<DoctorAccountDetail> detailList) {
        if (detailList == null || detailList.size() <= 0) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "detailList is required");
        }
        // 获取提现记录
        final CashBills bills = new CashBills();

        final DoctorAccountDetailDAO detailDao = DAOFactory
                .getDAO(DoctorAccountDetailDAO.class);
        final CashBillsDAO billDao = DAOFactory.getDAO(CashBillsDAO.class);

        // 计算提现金额
        BigDecimal allMoney = new BigDecimal(0d);
        for (DoctorAccountDetail doctorAccountDetail : detailList) {
            BigDecimal money = doctorAccountDetail.getMoney();
            allMoney = allMoney.add(money);
        }

        int creator = UserRoleToken.getCurrent().getId();
        String creatorName = UserRoleToken.getCurrent().getUserName();

        final String billId = DateTimeFormat.forPattern("yyyyMMddHHmmss")
                .print(new Date().getTime());

        bills.setBillId(billId);
        bills.setCreateDate(new Date());
        bills.setPaystatus(0);// 单据状态 0提现申请中 1提现完成
        bills.setCreator(creator);
        bills.setMoney(allMoney);
        bills.setActualPayment(new BigDecimal(0d));
        bills.setLastModify(new Date());
        bills.setDetailList(detailList);
        bills.setCreatorName(creatorName);

        HibernateStatelessResultAction<CashBills> action = new AbstractHibernateStatelessResultAction<CashBills>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                // 保存提现申请记录
                CashBills cashBill = billDao.save(bills);
                detailDao.updateBillId(billId);

                setResult(cashBill);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);

        return action.getResult();
    }

    /**
     * Title: 完成提现(根据提现记录单号和详情单号)
     *
     * @param billId  ---提现记录单号
     * @param details ---详情单号(集合) void
     * @author AngryKitty
     * @date 2015-9-6
     */
    @RpcService
    public void completeCashBillsByStatic(final String billId, List<Integer> details) {
        if (billId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "billId is required");
        }
        if (details == null || details.size() <= 0) {
            throw new DAOException(DAOException.VALUE_NEEDED, "details is required");
        }
        final DoctorAccountDetailDAO detailDao = DAOFactory
                .getDAO(DoctorAccountDetailDAO.class);
        final CashBillsDAO billDao = DAOFactory.getDAO(CashBillsDAO.class);

        // 申请提现的记录
        BigDecimal acMoney = new BigDecimal(0d);

        final List<DoctorAccountDetail> list = new ArrayList<DoctorAccountDetail>();
        for (Integer i : details) {
            DoctorAccountDetail doctorAccountDetail = detailDao
                    .getByAccountDetailId(i);
            if (doctorAccountDetail.getPayStatus() == 2) {
                BigDecimal money = doctorAccountDetail.getMoney();
                acMoney = acMoney.add(money);
                list.add(doctorAccountDetail);
            }
        }

        final CashBills bills = billDao.get(billId);
        bills.setLastModify(new Date());
        bills.setActualPayment(acMoney);
        bills.setPaydate(new Date());
        bills.setPaystatus(1);

        UserRoleToken operator = UserRoleToken.getCurrent();
        bills.setOperator(operator.getId());
        bills.setOperatorName(operator.getUserName());

        HibernateStatelessResultAction<CashBills> action = new AbstractHibernateStatelessResultAction<CashBills>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                // 保存提现申请记录
                CashBills cashBill = billDao.update(bills);
                for (DoctorAccountDetail item : list) {
                    detailDao.updateAccountDetailCompleteByID(item
                            .getAccountDetailId());
                }

                setResult(cashBill);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);

    }

    /**
     * 获得医生账户
     *
     * @param doctorId
     * @return
     * @author Jhc
     */
    @RpcService
    public DoctorAccount getDoctorAcc(Integer doctorId) {
        DoctorAccount account = new DoctorAccount();
        account = this.getByDoctorId(doctorId);
        if (account == null) {
            account = new DoctorAccount();
            account.setDoctorId(doctorId);
            account.setInCome(new BigDecimal(0d));
            account.setPayOut(new BigDecimal(0d));
            if (!exist(doctorId)) {
                account = save(account);
            }
        }
        return account;
    }

    /**
     * 推荐奖励系统消息，推送消息
     *
     * @param tel 推送目标的手机号
     * @param msg 系统消息
     * @author zhangx
     * @date 2015-11-27 下午12:03:44
     */
    public void pushMsgForRecommendReward(Integer rewardDoctorId,Integer doctorId) {

        Doctor rewardDoc = DAOFactory.getDAO(DoctorDAO.class).getByDoctorId(doctorId);
        if (rewardDoc == null
                || StringUtils.isEmpty(rewardDoc.getMobile())) {
            log.error("推荐奖励未查询到医生信息["+doctorId+"]");
            return;
        }

        SmsInfo info = new SmsInfo();
        info.setBusId(rewardDoctorId);
        info.setOrganId(rewardDoc.getOrgan());
        info.setBusType("RecommendRewardMsg");
        info.setSmsType("RecommendRewardMsg");

        Map<String,Object> smsMap = new HashMap<String, Object>();
        smsMap.put("doctorId",doctorId);
        info.setExtendValue(JSONUtils.toString(smsMap));

        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
        smsPushService.pushMsgData2OnsExtendValue(info);
    }


    /**
     * 根据主键更新收入
     *
     * @param inCome
     * @author
     * @date 2015-4-26 下午6:02:41
     */
    @DAOMethod(sql = "update DoctorAccount set inCome = inCome + :inCome where doctorId=:doctorId ")
    public abstract void updateDoctorAccountByDoctorIdAndInCome(
            @DAOParam("inCome") BigDecimal inCome,
            @DAOParam("doctorId") int doctorId);

    @RpcService
    @DAOMethod(sql = "select inCome from DoctorAccount where doctorId=:doctorId ")
    public abstract BigDecimal getInComeByDoctorId(@DAOParam("doctorId") int doctorId);


    /**
     * app3.6 如果查询出来没有账户，则新建账户且返回相关信息给前端
     *
     * @param doctorId
     * @return
     */
    @RpcService
    public DoctorAccount getAccountByDoctorId(Integer doctorId) {
        DoctorDAO docDAO = DAOFactory.getDAO(DoctorDAO.class);
        if (!docDAO.exist(doctorId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "doctorId is needed");
        }
        DoctorAccount account = get(doctorId);
        if (account == null) {
            account = new DoctorAccount();
            account.setDoctorId(doctorId);
            account.setInCome(new BigDecimal(0d));
            account.setPayOut(new BigDecimal(0d));
            account = save(account);
        }
        return account;
    }


    /**
     * 运营平台 查询医生账户
     * @param docName
     * @param organId
     * @param start
     * @param limit
     * @return
     */
    public  QueryResult<Object> queryDoctorAndAccount(final String docName,final Integer organId,final int start,final int limit){

        HibernateStatelessResultAction<QueryResult<Object>> action = new AbstractHibernateStatelessResultAction<QueryResult<Object>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder(" from DoctorAccount a,Doctor d where a.doctorId=d.doctorId ");
                if (organId!=null){
                    hql.append(" And d.organ =").append(organId);
                }
                if(docName!=null&&!StringUtils.isEmpty(docName.trim())){
                    hql.append(" And d.name like '%").append(docName.trim()).append("%' ");
                }
                Query countQuery = ss.createQuery("select count(*) "+hql.toString());
                long total = (long) countQuery.uniqueResult();//获取总条数
                Query query = ss.createQuery(" select a,d "+hql.toString()+" order by a.doctorId");
                query.setMaxResults(limit);
                query.setFirstResult(start);
                List<Object> list = query.list();
                if(list==null){
                    list = new ArrayList<Object>();
                }
                setResult(new QueryResult<Object>(total,start,list.size(),list));
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);

        return action.getResult();
    }



}
