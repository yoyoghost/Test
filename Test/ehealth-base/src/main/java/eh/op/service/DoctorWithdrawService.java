package eh.op.service;

import ctd.account.UserRoleToken;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessAction;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.dao.*;
import eh.base.service.BusActionLogService;
import eh.entity.base.*;
import eh.op.tonglihr.TongliPaymentDetail;
import eh.op.tonglihr.TonglihrUtil;
import eh.op.tonglihr.UserInfo;
import eh.op.tonglihr.WithdrawInfo;
import eh.utils.MapValueUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.hibernate.StatelessSession;
import org.joda.time.format.DateTimeFormat;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 运营平台医生提现服务 Created by houxr on 2016/10/11.
 */
public class DoctorWithdrawService {

    public static final Logger logger = Logger.getLogger(DoctorWithdrawService.class);

    /**
     * 查询所有申请中医生提现记录
     *
     * @param payMode   提现方式 "1"[1支付宝3银行卡] "2"手机充值
     * @param payStatus 提现状态 0申请中 1完成 2提现申请失败
     * @param organId   医生所在机构
     * @param doctorId  医生内码
     * @return
     */
    @RpcService
    public List<DoctorAccountDetail> findAllWithdrawDetailByPayStatusAndInout(final String payMode, final Integer payStatus,
                                                                              final Integer organId, final Integer doctorId) {
        final DoctorAccountDetailDAO detailDao = DAOFactory.getDAO(DoctorAccountDetailDAO.class);
        List<DoctorAccountDetail> detailList = new ArrayList<DoctorAccountDetail>();
        if (ObjectUtils.nullSafeEquals(payStatus, 0)) {//0申请中的医生提现信息 前端默认传"1"[1支付宝3银行卡] "2"手机充值
            detailList = detailDao.findWithdrawDoctorByOrganIdAndInout(payMode, 0, organId, doctorId);
        } else if (ObjectUtils.nullSafeEquals(payStatus, 2)) {//2提现申请审核失败
            detailList = detailDao.findWithdrawDoctorByOrganIdAndInout(payMode, 2, organId, doctorId);
        } else if (ObjectUtils.nullSafeEquals(payStatus, 4)) {//4支付中
            detailList = detailDao.findWithdrawDoctorByOrganIdAndInout(payMode, 4, organId, doctorId);
        } else if (ObjectUtils.nullSafeEquals(payStatus, 6)) { //6支付成功 德科已打款
            detailList = detailDao.findWithdrawDoctorByOrganIdAndInout(payMode, 6, organId, doctorId);
        }
        return detailList;
    }

    /**
     * 根据打款批次查询打款医生明细及发送德科打款医生明细
     *
     * @param cashBillId
     * @param payStatus
     * @return
     */
    @RpcService
    public QueryResult<CashBills> queryCashBillsByBillIdAndPayStatus(final String cashBillId, final Integer payStatus, final int start, final int limit) {
        CashBillsDAO cashBillsDAO = DAOFactory.getDAO(CashBillsDAO.class);
        return cashBillsDAO.queryCashBillsByBillIdAndPayStatus(cashBillId, payStatus, start, limit);
    }

    /**
     * @param type             提现类型 "1"[1支付宝3银行卡] "2"手机充值
     * @param accountDoctorIds app端生成的医生提现流水号
     * @param paymentDetails   聚合后的医生对象
     * @param accountCount     聚合后的医生数量
     * @return
     * @date 2016-11-3 下午11:40:24
     */
    @RpcService
    public CashBills createWithdrawBillsByAccountDoctorId(final Integer type, final List<Integer> accountDoctorIds, final List<PaymentDetail> paymentDetails,
                                                          final Integer accountCount) {
        CashBills cashBills = null;
        if (ObjectUtils.nullSafeEquals(type, 1)) { //提现类型 "1"[1支付宝3银行卡]
            cashBills = createBankAndAlipayWithdrawBillsByAccountDoctorId(accountDoctorIds, paymentDetails, accountCount);
        } else { //"2"手机充值
            cashBills = createMobileWithdrawBillsByAccountDoctorId(accountDoctorIds, paymentDetails, accountCount);
        }
        return cashBills;
    }


    /**
     * 根据 accountDoctorIds 生成提现记录
     *
     * @param accountDoctorIds
     * @return
     * @date 2016-10-17 上午11:40:24
     */
    @RpcService
    public CashBills createBankAndAlipayWithdrawBillsByAccountDoctorId(final List<Integer> accountDoctorIds, final List<PaymentDetail> paymentDetails,
                                                                       final Integer accountCount) {
        // 获取提现记录
        final CashBills bills = new CashBills();
        final DoctorAccountDetailDAO detailDao = DAOFactory.getDAO(DoctorAccountDetailDAO.class);
        final DoctorAccountDAO doctorAccountDao = DAOFactory.getDAO(DoctorAccountDAO.class);
        final CashBillsDAO billDao = DAOFactory.getDAO(CashBillsDAO.class);
        final DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        final OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        final PaymentDetailDAO paymentDetailDAO = DAOFactory.getDAO(PaymentDetailDAO.class);
        final List<DoctorAccountDetail> detailList = new ArrayList<DoctorAccountDetail>();
        if (accountDoctorIds.size() == 0) {
            throw new DAOException(DAOException.VALUE_NEEDED, "提现医生不能为空~~");
        }
        if (ObjectUtils.isEmpty(accountCount)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "提现医生不能为空~~");
        }
        if (paymentDetails.size() == 0) {
            throw new DAOException(DAOException.VALUE_NEEDED, "打款医生不能为空~~");
        }
        for (Integer accountDoctorId : accountDoctorIds) {
            DoctorAccountDetail doctorAccountDetail = detailDao.getByAccountDetailId(accountDoctorId);
            if (ObjectUtils.nullSafeEquals(doctorAccountDetail.getPayMode(), "2")) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "提现任务中包含有\'手机充值\'类型,暂不支持~谢谢~");
            }
            detailList.add(doctorAccountDetail);
        }

        // 计算提现金额
        BigDecimal allMoney = new BigDecimal(0d);
        for (DoctorAccountDetail doctorAccountDetail : detailList) {
            BigDecimal money = doctorAccountDetail.getMoney();
            allMoney = allMoney.add(money);
        }
        UserRoleToken urt = UserRoleToken.getCurrent();
        final String billId = DateTimeFormat.forPattern("yyyyMMddHHmmss").print(new Date().getTime());
        bills.setBillId(billId);
        bills.setCreateDate(new Date());
        bills.setPaystatus(0);// 单据状态 0提现申请中 1提现完成 2提现申请失败
        bills.setCreator(urt.getId());
        bills.setMoney(allMoney);
        bills.setActualPayment(new BigDecimal(0d));
        bills.setLastModify(new Date());
        bills.setDetailList(detailList);
        bills.setCreatorName(urt.getUserName());
        bills.setAccountCount(accountCount);
        HibernateStatelessResultAction<CashBills> action = new AbstractHibernateStatelessResultAction<CashBills>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                // 保存提现申请记录
                CashBills cashBill = billDao.save(bills);
                for (DoctorAccountDetail doctorAccountDetail : detailList) {
                    detailDao.updateBillIdByAccountDetailId(billId, 0, 3, doctorAccountDetail.getAccountDetailId());//更新打款医生 billId 信息
                }
                //保存 打款记录
                for (PaymentDetail paymentDetail : paymentDetails) {
                    if (ObjectUtils.isEmpty(paymentDetail.getDoctorId())) {
                        throw new DAOException(DAOException.VALUE_NEEDED, "提现医生id不能为空~~");
                    }
                    paymentDetail.setBillId(billId);
                    Doctor doctor = doctorDAO.getByDoctorId(paymentDetail.getDoctorId());
                    if (ObjectUtils.nullSafeEquals(paymentDetail.getPayMode(), "1")) {
                        DoctorAccount doctorAccount = doctorAccountDao.getAccountByDoctorId(doctor.getDoctorId());
                        paymentDetail.setAlipayId(doctorAccount.getAlipayId());
                        paymentDetail.setBankName("支付宝");
                    }
                    paymentDetail.setOrganId(doctor.getOrgan());
                    paymentDetail.setOrganName(organDAO.getByOrganId(doctor.getOrgan()).getShortName());
                    paymentDetail.setBizIds(JSONUtils.toString(accountDoctorIds));
                    PaymentDetail target = paymentDetailDAO.addDoctor2PaymentDetail(paymentDetail);//保存后的医生数据 对接到德科
                }
                setResult(cashBill);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
        CashBills targetCashBill = action.getResult();
        //医生基本信息对接到德科接口对接
        String dekeReturn = this.doctorDataTotonglihr(detailList);
        BusActionLogService.recordBusinessLog("医生提现", targetCashBill.getBillId(), "CashBills", "[" + targetCashBill.getBillId() +
                "]提现申请医生数据对接德科返回值:" + MapValueUtil.ascii2native(dekeReturn));
        return targetCashBill;
    }

    /**
     * 根据 accountDoctorIds 生成提现手机充值提现记录
     *
     * @param accountDoctorIds
     * @return
     * @date 2016-11-3 上午11:40:24
     */
    @RpcService
    public CashBills createMobileWithdrawBillsByAccountDoctorId(final List<Integer> accountDoctorIds, final List<PaymentDetail> paymentDetails,
                                                                final Integer accountCount) {
        // 获取提现记录
        final CashBills bills = new CashBills();
        final DoctorAccountDetailDAO detailDao = DAOFactory.getDAO(DoctorAccountDetailDAO.class);
        final CashBillsDAO billDao = DAOFactory.getDAO(CashBillsDAO.class);
        final DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        final OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        final PaymentDetailDAO paymentDetailDAO = DAOFactory.getDAO(PaymentDetailDAO.class);
        final List<DoctorAccountDetail> detailList = new ArrayList<DoctorAccountDetail>();
        if (accountDoctorIds.size() == 0) {
            throw new DAOException(DAOException.VALUE_NEEDED, "提现医生不能为空~~");
        }
        if (ObjectUtils.isEmpty(accountCount)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "提现医生不能为空~~");
        }
        if (paymentDetails.size() == 0) {
            throw new DAOException(DAOException.VALUE_NEEDED, "提现医生不能为空~~");
        }
        for (Integer accountDoctorId : accountDoctorIds) {
            DoctorAccountDetail doctorAccountDetail = detailDao.getByAccountDetailId(accountDoctorId);
            detailList.add(doctorAccountDetail);
        }

        // 计算提现金额
        BigDecimal allMoney = new BigDecimal(0d);
        for (DoctorAccountDetail doctorAccountDetail : detailList) {
            BigDecimal money = doctorAccountDetail.getMoney();
            allMoney = allMoney.add(money);
        }
        UserRoleToken urt = UserRoleToken.getCurrent();
        final String billId = DateTimeFormat.forPattern("yyyyMMddHHmmss").print(new Date().getTime());
        bills.setBillId(billId);
        bills.setCreateDate(new Date());
        bills.setPaystatus(1);// 单据状态手机号提现直接 1提现完成
        bills.setCreator(urt.getId());
        bills.setOperator(urt.getId());
        bills.setAuditor(urt.getId());
        bills.setMoney(allMoney);
        bills.setActualPayment(allMoney);
        bills.setLastModify(new Date());
        bills.setDetailList(detailList);
        bills.setCreatorName(urt.getUserName());
        bills.setOperatorName(urt.getUserName());
        bills.setAuditName(urt.getUserName());
        bills.setAccountCount(accountCount);
        bills.setPaydate(new Date());
        HibernateStatelessResultAction<CashBills> action = new AbstractHibernateStatelessResultAction<CashBills>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                // 保存提现申请记录
                CashBills cashBill = billDao.save(bills);
                for (DoctorAccountDetail doctorAccountDetail : detailList) {
                    detailDao.updateBillIdByAccountDetailId(billId, 1, 1, doctorAccountDetail.getAccountDetailId());//更新医生提现信息
                    DoctorAccountDetail dad = detailDao.getByAccountDetailId(doctorAccountDetail.getAccountDetailId());
                    dad.setPayDate(new Date());
                    dad.setReason("手机充值完成");
                    detailDao.update(dad);
                }
                //保存 打款记录
                for (PaymentDetail paymentDetail : paymentDetails) {
                    if (ObjectUtils.isEmpty(paymentDetail.getDoctorId())) {
                        throw new DAOException(DAOException.VALUE_NEEDED, "提现医生id不能为空~~");
                    }
                    paymentDetail.setBillId(billId);
                    Doctor doctor = doctorDAO.getByDoctorId(paymentDetail.getDoctorId());
                    paymentDetail.setOrganId(doctor.getOrgan());
                    paymentDetail.setOrganName(organDAO.getByOrganId(doctor.getOrgan()).getShortName());
                    paymentDetail.setBizIds(JSONUtils.toString(accountDoctorIds));
                    paymentDetailDAO.validPaymentDetailOne(paymentDetail);
                    paymentDetail.setCreateDate(new Date());
                    paymentDetail.setLastModify(new Date());
                    paymentDetail.setPayStatus(6);//支付完成
                    paymentDetailDAO.save(paymentDetail);
                }
                setResult(cashBill);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
        CashBills targetCashBill = action.getResult();
        BusActionLogService.recordBusinessLog("医生提现", targetCashBill.getBillId(), "CashBills", "提现批号[" + targetCashBill.getBillId() +
                "]为医生手机充值批次号,医生申请手机充值流水号AccountDetailId:" + JSONUtils.toString(accountDoctorIds));
        return targetCashBill;
    }


    /**
     * 根据提现批次号审核该批次提现单过程
     *
     * @param billId    提现单批号
     * @param payStatus 审核过程状态:0提现申请中 1已打款 2提现申请失败[审核不通过] 3打款审核中 4德科支付中 9打款审核拒绝
     * @return
     */
    @RpcService
    public CashBills updateCashBillsByBillIdAndPayStatus(final String billId, final Integer payStatus, final BigDecimal actualPayment) {
        if (StringUtils.isEmpty(billId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "提现批次号不能为空~~");
        }
        if (ObjectUtils.isEmpty(payStatus)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "payStatus不能为空~~");
        }
        CashBillsDAO cashBillsDAO = DAOFactory.getDAO(CashBillsDAO.class);
        PaymentDetailDAO paymentDetailDAO = DAOFactory.getDAO(PaymentDetailDAO.class);
        DoctorAccountDetailDAO doctorAccountDetailDAO = DAOFactory.getDAO(DoctorAccountDetailDAO.class);
        UserRoleToken urt = UserRoleToken.getCurrent();

        CashBills cashBills = cashBillsDAO.get(billId);
        cashBills.setPaystatus(payStatus);
        cashBills.setLastModify(new Date());
        cashBills.setAuditor(urt.getId());
        cashBills.setAuditName(urt.getUserName());
        CashBills targetCashBills = cashBillsDAO.update(cashBills);

        List<PaymentDetail> paymentDetails = paymentDetailDAO.findByBillId(cashBills.getBillId());
        for (PaymentDetail paymentDetail : paymentDetails) {
            paymentDetail.setPayStatus(payStatus);
            paymentDetail.setLastModify(new Date());
            paymentDetailDAO.update(paymentDetail);
        }
        targetCashBills.setPaymentDetails(paymentDetails);
        //更新doctorAccountDetail表中inout=2的提现状态, 不是1审核状态更新 是1的情况最后调用 德科接口返回值进行更新
        List<DoctorAccountDetail> doctorAccountDetails = null;
        if (ObjectUtils.nullSafeEquals(3, payStatus)) {
            doctorAccountDetails = doctorAccountDetailDAO.findByBillId(cashBills.getBillId());
            for (DoctorAccountDetail doctorAccountDetail : doctorAccountDetails) {
                doctorAccountDetail.setPayStatus(payStatus);//医生提现状态
                doctorAccountDetail.setReason("提现申请审核通过,批量打款单号:" + cashBills.getBillId());
                doctorAccountDetailDAO.update(doctorAccountDetail);
            }
            targetCashBills.setDetailList(doctorAccountDetails);
        }
        //医生提现申请审核不通过 设置状态为提现记录状态为2提现申请失败 设置为医生提现申请记录退回 设置状态为0
        if (ObjectUtils.nullSafeEquals(2, payStatus)) {
            doctorAccountDetails = doctorAccountDetailDAO.findByBillId(cashBills.getBillId());
            for (DoctorAccountDetail doctorAccountDetail : doctorAccountDetails) {
                doctorAccountDetail.setPayStatus(0);//提现申请审核失败后 回退医生提现申请
                doctorAccountDetail.setReason("提现申请打款审核未通过,批量打款单号:" + cashBills.getBillId());
                doctorAccountDetailDAO.update(doctorAccountDetail);
            }
            targetCashBills.setDetailList(doctorAccountDetails);
        }
        BusActionLogService.recordBusinessLog("医生提现", targetCashBills.getBillId(),
                "CashBills", "[" + billId + "]提现申请审核状态修改为:" + (ObjectUtils.nullSafeEquals(payStatus, 2) ? "提现申请失败" : "打款中"));
        return targetCashBills;
    }

    /**
     * 打款给德科支付信息
     *
     * @param billId        批号
     * @param payBankName   转款给德科的支付银行名称
     * @param payBankCode   转款给德科的支付银行账号
     * @param actualPayment 实际转款金额
     * @return
     */
    @RpcService
    public CashBills paymenInfo2TongliApiForPayments(final String billId, final String payBankName, final String payBankCode,
                                                     final BigDecimal actualPayment) {
        if (StringUtils.isEmpty(billId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "提现批次号不能为空~~");
        }
        if (StringUtils.isEmpty(payBankName)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "打款给德科的银行名称不能为空~~");
        }
        if (StringUtils.isEmpty(payBankCode)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "打款给德科的银行账号不能为空~~");
        }
        if (ObjectUtils.isEmpty(actualPayment)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "打款给德科的金额不能为空~~");
        }
        CashBillsDAO cashBillsDAO = DAOFactory.getDAO(CashBillsDAO.class);
        PaymentDetailDAO paymentDetailDAO = DAOFactory.getDAO(PaymentDetailDAO.class);
        DoctorAccountDetailDAO doctorAccountDetailDAO = DAOFactory.getDAO(DoctorAccountDetailDAO.class);
        UserRoleToken urt = UserRoleToken.getCurrent();

        CashBills cashBills = cashBillsDAO.getByBillId(billId);

        //更新打款批号状态为 1已打款
        cashBills.setPaydate(new Date());//实际打款日期
        cashBills.setActualPayment(actualPayment);//实际打款金额
        cashBills.setPaystatus(1);// 1已打款
        cashBills.setPayEndStatus(0);// 0支付中 打款信息发给德科前的暂时状态
        cashBills.setLastModify(new Date());
        cashBills.setOperator(urt.getId());
        cashBills.setOperatorName(urt.getUserName());
        cashBills.setPayBankName(payBankName);//财务给德科打款打款银行
        cashBills.setPayBankCode(payBankCode);//财务给德科打款打款银行账号
        CashBills targetCashBills = cashBillsDAO.update(cashBills);

        List<PaymentDetail> paymentDetails = paymentDetailDAO.findByBillId(cashBills.getBillId());
        for (PaymentDetail paymentDetail : paymentDetails) {
            paymentDetail.setPayStatus(4);//4支付中 打款信息发给德科前的暂时状态
            paymentDetail.setLastModify(new Date());
            paymentDetailDAO.update(paymentDetail);
        }
        targetCashBills.setPaymentDetails(paymentDetails);
        //更新doctorAccountDetail表中inout=1的提现状态, 不是1审核状态更新 是1的情况最后调用 德科接口返回值进行更新
        List<DoctorAccountDetail> doctorAccountDetails = doctorAccountDetailDAO.findByBillId(cashBills.getBillId());
        for (DoctorAccountDetail doctorAccountDetail : doctorAccountDetails) {
            doctorAccountDetail.setPayStatus(4);//医生提现状态 设置为 4支付中
            doctorAccountDetailDAO.update(doctorAccountDetail);
        }
        targetCashBills.setDetailList(doctorAccountDetails);
        //支付医生数据对接到德科
        String result = paymentInfoData2TongliApiPayments(targetCashBills.getBillId());
        BusActionLogService.recordBusinessLog("医生提现", targetCashBills.getBillId(),
                "CashBills", "打款给德科提现批次号:" + targetCashBills.getBillId() + ",德科对接返回数据:" + MapValueUtil.ascii2native(result));
        return targetCashBills;
    }


    /**
     * 添加 医生信息对接到德科API服务
     *
     * @param detailList
     * @return
     */
    public String doctorDataTotonglihr(List<DoctorAccountDetail> detailList) {
        try {
            DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
            List<UserInfo> data = new ArrayList<UserInfo>();
            for (DoctorAccountDetail doctorAccountDetail : detailList) {
                Doctor doctor = doctorDAO.get(doctorAccountDetail.getDoctorId());
                DoctorAccount doctorAccount = DAOFactory.getDAO(DoctorAccountDAO.class).getByDoctorId(doctor.getDoctorId());
                OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
                Organ area = organDAO.getOgranAddrArea(doctor.getOrgan());
                Organ address = organDAO.getByOrganId(doctor.getOrgan());
                String province = DictionaryController.instance().get("eh.base.dictionary.AddrArea").getText(area.getAddrArea());
                String city = DictionaryController.instance().get("eh.base.dictionary.AddrArea").getText(area.getCity());
                Boolean isTeam = Boolean.TRUE.equals(doctor.getTeams());
                if (!isTeam) {
                    UserInfo userInfo = new UserInfo();
                    userInfo.setName(doctor.getName());
                    userInfo.setMobile_number(doctor.getMobile());
                    userInfo.setId_number(doctor.getIdNumber());
                    userInfo.setProvince(province);//医生所在省
                    userInfo.setCity(city);//医生所在市
                    userInfo.setAddress(address.getAddress());//医生街道地址

                    userInfo.setAlipay_id(doctorAccount.getAlipayId());
                    userInfo.setCard_no(doctorAccount.getCardNo());
                    userInfo.setCard_name(doctorAccount.getCardName());
                    userInfo.setBank_code(doctorAccount.getBankCode());
                    userInfo.setBank_branch(doctorAccount.getSubBank());
                    userInfo.setPay_mobile(doctorAccount.getPayMobile());
                    userInfo.setEmployee_number(doctorAccount.getDoctorId());
                    data.add(userInfo);
                }
            }
            return TonglihrUtil.apiTongliAddEmployees(data);
        } catch (Exception e) {
           logger.error(e);
        }
        return null;
    }


    /**
     * 批量支付医生信息数据上传到到德科支付API
     *
     * @param billId 批次号
     * @return
     */
    public String paymentInfoData2TongliApiPayments(String billId) {
        if (StringUtils.isEmpty(billId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "批次号不能为空~~");
        }
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        CashBillsDAO cashBillsDAO = DAOFactory.getDAO(CashBillsDAO.class);
        PaymentDetailDAO paymentDetailDAO = DAOFactory.getDAO(PaymentDetailDAO.class);

        CashBills cashBills = cashBillsDAO.get(billId);
        List<PaymentDetail> paymentDetails = paymentDetailDAO.findByBillId(cashBills.getBillId());

        if (!ObjectUtils.nullSafeEquals(1, cashBills.getPaystatus())) {//状态为1已打款 才能发送打款信息到德科
            throw new DAOException(DAOException.VALUE_NEEDED, "批次号[" + billId + "]未打款给德科,不能发起德科提现业务,请联系财务确认~~");
        }

        WithdrawInfo payInfoData = new WithdrawInfo();
        List<TongliPaymentDetail> tongliPayDetails = new ArrayList<TongliPaymentDetail>();
        payInfoData.setPayment_batch_id(cashBills.getBillId());//批次号
        payInfoData.setFunding_amount(cashBills.getActualPayment());//财务转款总金额
        payInfoData.setFunding_bank_code(cashBills.getPayBankName());//打款银行名称
        payInfoData.setFunding_account_no(cashBills.getPayBankCode());//打款银行账号
        payInfoData.setFunding_date(DateTimeFormat.forPattern("yyyy-MM-dd").print(cashBills.getPaydate().getTime()));//财务转款日期
        payInfoData.setPayment_count(cashBills.getAccountCount());//涉及医生数
        payInfoData.setPayment_total(cashBills.getMoney());//本批给医生发款总金额
        payInfoData.setReply_url("http://www.nagrihealth.com/payment/result");
        for (PaymentDetail paymentDetail : paymentDetails) {
            //System.out.println("==paymentDetail==:" + JSONUtils.toString(paymentDetail));
            TongliPaymentDetail payDetail = new TongliPaymentDetail();
            payDetail.setPayment_channel(ObjectUtils.nullSafeEquals(paymentDetail.getPayMode(), "3") ? "bocomm" : "alipay");
            payDetail.setAccount_bank_code(ObjectUtils.nullSafeEquals(paymentDetail.getPayMode(), "3") ? paymentDetail.getBankName() : "支付宝");
            payDetail.setAccount_bank_no(ObjectUtils.nullSafeEquals(paymentDetail.getPayMode(), "3") ? paymentDetail.getCardNo() : paymentDetail.getAlipayId());
            payDetail.setAccount_bank_name(paymentDetail.getCardName());
            payDetail.setMobile_number(doctorDAO.getByDoctorId(paymentDetail.getDoctorId()).getMobile());//医生手机号
            payDetail.setPayment_amount(paymentDetail.getPaymentAmount());//医生实际提现金额
            payDetail.setWithdraw_order_id(paymentDetail.getBillId());
            tongliPayDetails.add(payDetail);
        }
        payInfoData.setPayment_detail(tongliPayDetails);
        //System.out.println("==发送德科支付信息payInfoData==:" + JSONUtils.toString(payInfoData));
        //医生支付信息给德科
        String result = TonglihrUtil.apiTongliForPayments(payInfoData);
        //System.out.println("==发送德科支付信息payInfoData==:" + result);
        return result;
    }


    /**
     * 根据打款批次查询打款完成明细及德科支付完成情况
     *
     * @param billId
     * @param payEndStatus
     * @return
     */
    @RpcService
    public QueryResult<CashBills> queryEndWithdrawByBillIdAndPayEndStatus(final String billId, final Integer payEndStatus, final int start, final int limit) {
        CashBillsDAO cashBillsDAO = DAOFactory.getDAO(CashBillsDAO.class);
        return cashBillsDAO.queryEndWithdrawByBillIdAndPayEndStatus(billId, payEndStatus, start, limit);
    }

    /**
     * 定时器调用 德科打款结果接口更新提现结果
     */
    @RpcService
    public void updateDoctorWithdrawPayEndStatusFromTongli() {
        logger.info("start schedule updateDoctorWithdrawPayEndStatusFromTongli...");
        final HibernateStatelessAction action = new HibernateStatelessAction() {
            @Override
            public void execute(StatelessSession statelessSession) throws Exception {
                CashBillsDAO cashBillsDAO = DAOFactory.getDAO(CashBillsDAO.class);
                PaymentDetailDAO paymentDetailDAO = DAOFactory.getDAO(PaymentDetailDAO.class);
                DoctorAccountDetailDAO doctorAccountDetailDAO = DAOFactory.getDAO(DoctorAccountDetailDAO.class);
                List<String> billIdList = cashBillsDAO.findCashBillsByPayStatus(1);
                logger.info("get update billIdLists:" + JSONUtils.toString(billIdList));
                for (String billId : billIdList) {
                    Map<String, Object> resultMap = TonglihrUtil.tongliApiForEndPayment(billId);
                    logger.info("get tongli doctorWithdrawDetail resultMap:" + JSONUtils.toString(resultMap));
                    //德科返回结果为200表示成功 否则全批次失败
                    if (ObjectUtils.nullSafeEquals(200, MapValueUtil.getInteger(resultMap, "code"))) {
                        String jsonData = JSONUtils.toString(MapValueUtil.getObject(resultMap, "data"));
                        logger.info("德科支付结果jsonData:" + jsonData);
                        WithdrawInfo withdrawInfo = JSONUtils.parse(jsonData, WithdrawInfo.class);
                        logger.info("deke pay end MapEntity:" + JSONUtils.toString(withdrawInfo));
                        CashBills cashBills = cashBillsDAO.getByBillId(withdrawInfo.getPayment_batch_id());
                        List<TongliPaymentDetail> tongliPaymentDetails = withdrawInfo.getPayment_details();
                        boolean allFlag = true;
                        for (TongliPaymentDetail tongliPayEnd : tongliPaymentDetails) {
                            String payMode = ObjectUtils.nullSafeEquals(tongliPayEnd.getPayment_channel(), "bocomm") ? "3" : "1";
                            PaymentDetail paymentDetail = paymentDetailDAO.findPaymentDetailByResult(payMode, cashBills.getBillId(),
                                    tongliPayEnd.getAccount_bank_code(), tongliPayEnd.getAccount_bank_no(), tongliPayEnd.getAccount_bank_name()).get(0);
                            if(paymentDetail.getPayEndStatus()!=null&&!ObjectUtils.nullSafeEquals(paymentDetail.getPayEndStatus(),0)){
                                continue;
                            }
                            String strStatus = tongliPayEnd.getPayment_detail_status();

                            if (ObjectUtils.nullSafeEquals(strStatus, "发款成功")
                                    || ObjectUtils.nullSafeEquals(strStatus, "发款失败")) {
                                paymentDetail.setPayEndStatus(ObjectUtils.nullSafeEquals(strStatus, "发款成功") ? 1 : 9);//payment_detail_status:发款成功
                                paymentDetail.setPayStatus(ObjectUtils.nullSafeEquals(strStatus, "发款成功") ? 1 : 5);//payment_detail_status:发款成功
                                paymentDetail.setReason(strStatus);
                                paymentDetail.setLastModify(new Date());
                                paymentDetailDAO.update(paymentDetail);
                                //提现德科返回‘发款失败’状态时退回医生积分
                                if (ObjectUtils.nullSafeEquals(strStatus, "发款失败")) {
                                    doctorWithdrawRefundScoreByDoctorId(paymentDetail.getDoctorId(), paymentDetail.getPaymentAmount(), paymentDetail.getBillId());
                                }
                                List<DoctorAccountDetail> accountDetails = doctorAccountDetailDAO.findAccountDetailByBillIdAndDoctorId(billId, paymentDetail.getDoctorId());
                                for (DoctorAccountDetail doctorAccountDetail : accountDetails) {
                                    doctorAccountDetail.setPayStatus(ObjectUtils.nullSafeEquals(strStatus, "发款成功") ? 1 : 9);
                                    doctorAccountDetail.setClosure(1);
                                    doctorAccountDetail.setPayDate(new Date());
                                    doctorAccountDetail.setReason(strStatus);
                                    doctorAccountDetailDAO.update(doctorAccountDetail);
                                }
                            } else {
                                allFlag = false;
                            }
                        }
                        logger.info("是否全部发款完成或失败："+allFlag);
                        if(allFlag){
                            cashBills.setPayEndStatus(ObjectUtils.nullSafeEquals(withdrawInfo.getFailure_count(), 0) ? 1 : 2);//德科返回打款状态
                            cashBills.setPaystatus(ObjectUtils.nullSafeEquals(withdrawInfo.getFailure_count(), 0) ? 6 : 7);//纳里提现流程状态
                            cashBills.setMark(withdrawInfo.getPayment_status());
                            cashBillsDAO.update(cashBills);
                            logger.info("cashBillsDAO.update(cashBills) end:" + JSONUtils.toString(cashBills));
                        }
                    } else if (ObjectUtils.nullSafeEquals(202, MapValueUtil.getInteger(resultMap, "code"))) {
                        CashBills cashBills = cashBillsDAO.get(billId);
                        cashBills.setPayEndStatus(0);//0支付中
                        cashBills.setLastModify(new Date());
                        cashBills.setMark(JSONUtils.toString(MapValueUtil.getObject(resultMap, "data")));
                        cashBillsDAO.update(cashBills);
                    }
                }
            }

        };
        try {
            HibernateSessionTemplate.instance().executeTrans(action);
        }catch (Throwable e){
            logger.error("德科打款失败",e);
        }
        logger.info("end schedule updateDoctorWithdrawPayEndStatusFromTongli");
    }


    /**
     * 根据billId查询德科支付结果
     *
     * @param billId 批次号
     */
    @RpcService
    public CashBills findBillId(String billId) {
        //返回更新结果
        CashBillsDAO cashBillsDAO = DAOFactory.getDAO(CashBillsDAO.class);
        PaymentDetailDAO paymentDetailDAO = DAOFactory.getDAO(PaymentDetailDAO.class);
        CashBills cashBills = cashBillsDAO.get(billId);
        List<PaymentDetail> paymentDetails = paymentDetailDAO.findByBillId(cashBills.getBillId());
        cashBills.setPaymentDetails(paymentDetails);
        return cashBills;
    }


    /**
     * 德科支付 批号
     *
     * @param payStatus 批次号状态
     */
    @RpcService
    public List<String> listBillIdByPayStatus(Integer payStatus) {
        CashBillsDAO cashBillsDAO = DAOFactory.getDAO(CashBillsDAO.class);
        List<String> billIds = cashBillsDAO.findCashBillsByPayStatus(payStatus);
        return billIds;
    }


    @RpcService
    public String testPaymentData2Tongli(String billId) {
        /*DoctorAccountDetailDAO detailDao = DAOFactory.getDAO(DoctorAccountDetailDAO.class);
        DoctorAccountDAO deDao = DAOFactory.getDAO(DoctorAccountDAO.class);
        List<DoctorAccountDetail> detailList = detailDao.findNotTestDoctorByPayStatusAndInout(4, 2);*/
        DoctorWithdrawService doctorWithdrawService = new DoctorWithdrawService();
        String result = doctorWithdrawService.paymentInfoData2TongliApiPayments(billId);
        logger.info("对接德科提现data:" + JSONUtils.toString(MapValueUtil.ascii2native(result)));
        return result;
    }

    /**
     * 运营平台医生提现德科返回提现失败状态后退款医生提现失败积分
     *
     * @param doctorId 医生内码
     * @param amount   退回积分
     */
    @RpcService
    public void doctorWithdrawRefundScoreByDoctorId(final int doctorId, final BigDecimal amount, final String billId) {
        if (ObjectUtils.isEmpty(doctorId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "doctorId is required");
        }

        if (ObjectUtils.isEmpty(amount)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "amount is required");
        }

        DoctorAccountDAO doctorAccountDAO = DAOFactory.getDAO(DoctorAccountDAO.class);
        DoctorAccount account = doctorAccountDAO.getByDoctorId(doctorId);
        if (account == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "DoctorId:" + doctorId + " doctorAccount was not find");
        }

        // 校验医生积分总收入和明细值是否相等 accountDetail == account.price
        DoctorAccountDetailDAO doctorAccountDetailDAO = DAOFactory.getDAO(DoctorAccountDetailDAO.class);
        BigDecimal detailSumMoney = doctorAccountDetailDAO.getSumMoneyByDoctorIdAndInout(doctorId, 1);

        BigDecimal doctorIncome = doctorAccountDAO.getInComeByDoctorId(doctorId);
        if ((detailSumMoney == null ? new BigDecimal(0d) : detailSumMoney).compareTo(doctorIncome) != 0) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "doctorId:" + doctorId + " inCome not equal doctorAccountDetail Sum(Money)");
        }

        //先查找有 其他 serviceId:9，获取需要业务序号bussId=0 的income
        //添加一条退款积分accountDetail记录,新增医生退款收入明细
        DoctorAccountDetail detail = new DoctorAccountDetail();
        detail.setDoctorId(doctorId);
        detail.setInout(1);
        detail.setCreateDate(new Date());
        detail.setSummary("提现失败,积分返还");
        detail.setMoney(amount == null ? new BigDecimal("0") : amount);
        detail.setServerId(9);
        detail.setBussType(10);
        detail.setBussId(0);//业务序号
        detail.setClosure(0);
        DoctorAccountDetailDAO detailDAO = DAOFactory.getDAO(DoctorAccountDetailDAO.class);
        detailDAO.save(detail);
        // 更新 account中的income值
        doctorAccountDAO.updateDoctorAccountByDoctorIdAndInCome(amount, doctorId);

        // 校验 是否相等 accountDetail == account.price
        BigDecimal newDetailSumMoney = doctorAccountDetailDAO.getSumMoneyByDoctorIdAndInout(doctorId, 1);
        BigDecimal newDoctorIncome = doctorAccountDAO.getInComeByDoctorId(doctorId);
        if (newDetailSumMoney.compareTo(newDoctorIncome) != 0) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "refund score [doctorId:" + doctorId + " inCome not equal doctorAccountDetail Sum(Money)]");
        }

        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        OrganDAO organDAO = DAOFactory.getDAO(OrganDAO.class);
        Doctor doctor = doctorDAO.get(doctorId);
        Organ organ = organDAO.get(doctor.getOrgan());

        //保存系统操作日志
        BusActionLogDAO actionLogDAO = DAOFactory.getDAO(BusActionLogDAO.class);
        BusActionLog actionLog = new BusActionLog();
        actionLog.setActionTime(new Date());
        actionLog.setUserId(0);
        actionLog.setUserName("system");
        actionLog.setBizId("提现批次号:" + billId + ",doctorId:" + String.valueOf(doctorId));
        actionLog.setBizClass("DoctorAccount");
        actionLog.setIpAddress(MapValueUtil.getLocalHostIP());
        actionLog.setActionType("提现失败");
        actionLog.setActionContent("提现批次号[" + billId + "]有失败明细,德科返回状态'发款失败',[" + organ.getShortName() + "]的["
                + doctor.getName() + "](" + doctorId + ")医生提现失败退回[" + amount + "]积分");
        actionLog.setExecuteTime(1);
        actionLogDAO.saveBusActionLog(actionLog);
    }

}

