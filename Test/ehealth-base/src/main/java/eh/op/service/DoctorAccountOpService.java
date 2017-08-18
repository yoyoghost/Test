package eh.op.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.dao.DoctorAccountDAO;
import eh.base.dao.DoctorAccountDetailDAO;
import eh.base.service.BusActionLogService;
import eh.entity.base.DoctorAccount;
import eh.entity.base.DoctorAccountDetail;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 医生账户信息服务
 *
 * @author jianghc
 * @create 2016-12-21 13:19
 **/
public class DoctorAccountOpService {

    /**
     * 获取医生账户信息，包括提现次数
     *
     * @param doctorId
     * @return
     */
    @RpcService
    public Map getDoctorAccountInfo(Integer doctorId) {
        if (doctorId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "doctorId is require");
        }

        DoctorAccountDAO doctorAccountDAO = DAOFactory.getDAO(DoctorAccountDAO.class);
        DoctorAccount account = doctorAccountDAO.getByDoctorId(doctorId);
        if (account == null) {
            return null;
        }
        DoctorAccountDetailDAO doctorAccountDetailDAO = DAOFactory.getDAO(DoctorAccountDetailDAO.class);

        long count = doctorAccountDetailDAO.getCountByDoctorId(doctorId, 2);

        Map<String, Object> map = new HashMap<String, Object>();

        BeanUtils.map(account, map);
        map.put("withdrawTimes", count);
        return map;
    }

    @RpcService
    public Map findDoctorDetailByDate(Integer doctorId, Date bDate, Date eDate, Integer inout, int start, int limit) {
        DoctorAccountDetailDAO doctorAccountDetailDAO = DAOFactory.getDAO(DoctorAccountDetailDAO.class);
        List<Object> objs = doctorAccountDetailDAO.findSumGroupByInout(doctorId, bDate, eDate);
        QueryResult<DoctorAccountDetail> details = doctorAccountDetailDAO.queryDoctorAccountDetailByDate(doctorId, bDate, eDate, inout, start, limit);
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("summary", objs);
        map.put("details", details);
        return map;
    }

    @RpcService
    public QueryResult<Object> queryDoctorAndAccount(String docName, Integer organId, int start, int limit) {
        DoctorAccountDAO doctorAccountDAO = DAOFactory.getDAO(DoctorAccountDAO.class);
        return doctorAccountDAO.queryDoctorAndAccount(docName, organId, start, limit);
    }

    /**
     * 调整提现金额
     *
     * @param detailId
     * @param amount
     */
    @RpcService
    public void adjustedAmount(Integer detailId, BigDecimal amount) {
        if(detailId==null){
            throw new DAOException(DAOException.VALUE_NEEDED,"detailId is require");
        }
        DoctorAccountDetailDAO doctorAccountDetailDAO = DAOFactory.getDAO(DoctorAccountDetailDAO.class);
        DoctorAccountDetail detail = doctorAccountDetailDAO.getByAccountDetailId(detailId);
        if(detail==null){
            throw new DAOException("detailId is not exist");
        }
        if(detail.getInout().intValue()!=2){
            throw new DAOException("detail's type is not payment");
        }
        // TODO: 2017/2/15 暂时不提供状态限制
        BigDecimal payAmount = detail.getMoney().subtract(amount);
        if (payAmount.doubleValue()<0){
            throw new DAOException("amount is not allowed");
        }
        if(payAmount.doubleValue()==0){//积分全部不符合奖励标准
            detail.setBillId(null);
            detail.setServerId(31);
            detail.setSummary("积分不符合奖励标准");
            detail.setPayMode(null);
            detail.setBankCardId(null);
            detail.setCardNo(null);
            detail.setBankName(null);
            detail.setCardName(null);
            detail.setSubBank(null);
            doctorAccountDetailDAO.update(detail);
        }else{
            detail.setMoney(payAmount);
            doctorAccountDetailDAO.update(detail);
            DoctorAccountDetail adjusteDetail = new DoctorAccountDetail();
            adjusteDetail.setDoctorId(detail.getDoctorId());
            adjusteDetail.setInout(2);
            adjusteDetail.setCreateDate(new Date());
            adjusteDetail.setSummary("积分不符合奖励标准");
            adjusteDetail.setMoney(amount);
            adjusteDetail.setServerId(31);
            adjusteDetail.setPayStatus(2);//状态为提现申请失败
            adjusteDetail.setBussId(detailId);
            doctorAccountDetailDAO.save(adjusteDetail);
        }
        BusActionLogService.recordBusinessLog("提现管理", detailId+"", "DoctorAccountDetail",
                "医生ID【"+detail.getDoctorId()+"】，提现"+detail.getMoney().doubleValue()+",因其中"+amount.doubleValue()+"积分不符合奖励标准,不予提现。");

    }


}

