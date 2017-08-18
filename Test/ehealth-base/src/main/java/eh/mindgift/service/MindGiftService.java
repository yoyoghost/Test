package eh.mindgift.service;

import ctd.account.UserRoleToken;
import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.base.constant.SystemConstant;
import eh.coupon.service.CouponService;
import eh.entity.base.PatientFeedback;
import eh.entity.mindgift.Gift;
import eh.entity.mindgift.MindGift;
import eh.entity.mpi.Patient;
import eh.evaluation.dao.EvaluationDAO;
import eh.mindgift.constant.MindGiftConstant;
import eh.mindgift.dao.GiftDAO;
import eh.mindgift.dao.MindGiftDAO;
import eh.mpi.dao.PatientDAO;
import eh.mpi.service.PatientService;
import eh.push.SmsPushService;
import eh.task.executor.WxRefundExecutor;
import eh.utils.LocalStringUtil;
import eh.utils.ValidateUtil;
import eh.utils.params.ParamUtils;
import eh.utils.params.ParameterConstant;
import eh.wxpay.constant.PayConstant;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;


public class MindGiftService {
    private static final Log logger = LogFactory.getLog(MindGiftService.class);
    private static final MindGiftDAO mindDao= DAOFactory.getDAO(MindGiftDAO.class);


    /**
     * 查询心意记录，供消息发送使用
     * @param mindGiftId
     * @return
     */
    @RpcService
    public MindGift getById(Integer mindGiftId){
       return mindDao.get(mindGiftId);
    }

    /**
     * 获取心意详情
     * @param mindGiftId
     * @return
     */
    @RpcService
    public MindGift getMindGiftInfo(Integer mindGiftId){

        MindGift mindGift=mindDao.get(mindGiftId);

        //医生端获取详情，则将心意置成已读,
        UserRoleToken urt = UserRoleToken.getCurrent();
        if(SystemConstant.ROLES_DOCTOR.equals(urt.getRoleId())){
            mindDao.updateReadFlagById(new Date(),mindGiftId);
        }

        MindGift returnMind = getMindGiftInfoData(mindGift,false);
        return returnMind;
    }

    /**
     * [医生端]获取最新的心意详情
     * 有返回详情，没有返回null，这个接口 要将心意改成已读。
     * @param mindGiftId
     * @return
     */
    @RpcService
    public MindGift getLatestMindGiftInfo(Integer doctorId){
        List<MindGift> latestList=mindDao.findLatestUnReadMindGiftByDoctorId(doctorId,0,1);
        if(latestList.size()==0){
            return null;
        }

        MindGift mindGift=latestList.get(0);
        MindGift returnMind= getMindGiftInfo(mindGift.getMindGiftId());
        returnMind.setReadFlag(MindGiftConstant.READFLAG_READED);
        return  returnMind;
    }


    /**
     * 获取一个医生的心意
     * [医生端]我的心意列表(第一页)，不含患者头像,不处理患者姓名
     * @param doctorId
     * @return
     */
    @RpcService
    public Map<String,Object> findMyMindGiftByDoctorId(Integer doctorId){
        Map<String,Object> map=new HashMap<String,Object>();

        Object account=mindDao.getMindGiftAccountByDoctorId(doctorId);

        //心意总金额
        map.put("mindAccount",account==null?0l:account);

        //心意总数
        map.put("mindNum",mindDao.getEffectiveMindGiftsNum(doctorId));

        //心意列表(第一页)
        List<MindGift> list=findMindsByDoctorId(doctorId,0,10,false);
        map.put("mindList",list);

        return map;
    }

    /**
     * 获取一个医生的心意(分页)
     * [医生端]我的心意列表，不含患者头像,不处理患者姓名
     * @param doctorId
     * @return
     */
    @RpcService
    public List<MindGift> findMyMindGiftPagesByDoctorId(Integer doctorId,int start,int limit){
        //心意列表
        return findMindsByDoctorId(doctorId,start,limit,false);
    }



    /**
     * 获取礼品列表,包含心意墙内容
     * @return
     */
    @RpcService
    public Map<String,Object> findGifts(Integer doctorId){
        Map<String,Object> map=new HashMap<String,Object>();

        //页面描述
        String mindgift_desc= ParamUtils.getParam(ParameterConstant.KEY_MINDGIFT_PROMPT_DESC, "医生是利用休息时间为你服务的，送个心意感谢下医生的辛苦付出吧！");
        map.put("mindGiftDesc",mindgift_desc);

        //有效锦旗列表
        GiftDAO giftDAO= DAOFactory.getDAO(GiftDAO.class);
        List<Gift> findEffectiveGifts=giftDAO.findEffectiveGifts(0,5);
        map.put("gifts",findEffectiveGifts);

        //心意墙数
        //心意墙内容，排序规则与评价相同,按提交时间倒序
        map.putAll(findMindGiftsByDoctorId(doctorId,0,10));

        return map;
    }

    /**
     * 一个医生的所有心意内容(含心意数+含患者头像)
     * 使用地方：物品选择提交页，医生的心意列表
     * @return
     */
    @RpcService
    public Map<String,Object> findMindGiftsByDoctorId(Integer doctorId,Integer start,Integer limit){
        Map<String,Object> map=new HashMap<String,Object>();
        //心意墙数
        map.put("mindGiftNum",mindDao.getEffectiveMindGiftsNum(doctorId));

        //心意墙内容，排序规则与评价相同,按提交时间倒序
        map.putAll(findMindGiftsListByDoctorId(doctorId,start,limit));

        return map;
    }


    /**
     * 一个医生的所有心意内容(包括患者的头像)
     * 使用地方：物品选择提交页，医生的心意列表
     * @return
     */
    @RpcService
    public Map<String,Object> findMindGiftsListByDoctorId(Integer doctorId,Integer start,Integer limit){
        Map<String,Object> map=new HashMap<String,Object>();
        //心意墙内容，排序规则与评价相同
        List<Map<String,Object>> mindlist=new ArrayList<Map<String,Object>>();

        List<MindGift> list=mindDao.findEffectiveMindGifts(doctorId,start,limit);
        for (MindGift mind:list) {
            mindlist.add(getMindGiftAndPatData(mind));
        }
        map.put("mindGiftList",mindlist);
        return map;
    }

    /**
     * 一个医生的所有心意内容(不含患者头像)
     * 使用地方：[微信端]医生主页，
     * @return
     */
    @RpcService
    public List<MindGift> findAllMindGiftsListByDoctorId(Integer doctorId,Integer start,Integer limit){
        return findMindsByDoctorId(doctorId,start,limit,true);
    }



    /**
     * 根据评价ID获取一个业务单的所有心意内容
     * 使用地方：评价页面使用
     * @param feedbackId
     * @return
     */
    public Map<String,Object> findMindGiftsByEvaluationId(int feedbackId){
        HashMap<String, Object> map = new HashMap<String, Object>();

        EvaluationDAO evaDao=DAOFactory.getDAO(EvaluationDAO.class);
        PatientFeedback eva=evaDao.get(feedbackId);
        map.put("mindGiftNum",mindDao.getEffectiveMindGiftsNumByBusTypeAndBusId(Integer.parseInt( eva.getServiceType()), Integer.parseInt(eva.getServiceId())));
        map.put("mindGiftList",findPageMindGiftsByEvaluationId(feedbackId,0,10));

       return map;
    }

    /**
     * 根据评价ID获取一个业务单的所有心意内容(分页)
     * 使用地方：评价页面使用
     * @param doctorId
     * @param start
     * @param limit
     * @return
     */
    @RpcService
    public List<MindGift> findPageMindGiftsByEvaluationId(Integer feedbackId,Integer start,Integer limit){
        EvaluationDAO evaDao=DAOFactory.getDAO(EvaluationDAO.class);
        PatientFeedback eva=evaDao.get(feedbackId);
        List<MindGift> mindList=mindDao.findEffectiveMindGiftsByBusTypeAndBusId(Integer.parseInt( eva.getServiceType()),
                Integer.parseInt(eva.getServiceId()),start,limit);

        List<MindGift> returnList=new ArrayList<MindGift>();
        for (MindGift mind:mindList ) {
            MindGift returnMind= getMindGiftInfoData(mind,false);
            if(returnMind!=null){
                returnList.add(returnMind);
            }
        }
        return returnList;
    }


    /**
     * 查询一个医生的所有心意列表，不含患者头像，被其他接口调用
     * @param doctorId
     * @param start
     * @param limit
     * @return
     */
    public List<MindGift> findMindsByDoctorId(Integer doctorId,int start,int limit,Boolean coverPatientName){
       //心意列表
        List<MindGift> list=mindDao.findEffectiveMindGifts(doctorId,start,limit);

        List<MindGift> returnList=new ArrayList<MindGift>();
        for (MindGift mind:list ) {
            MindGift returnMind= getMindGiftInfoData(mind,coverPatientName);
            if(returnMind!=null){
                returnList.add(returnMind);
            }
        }
        return returnList;
    }

    /**
     * 只赋值患者姓名信息
     * @param mindGift
     * @return
     */
    /**
     * 只赋值患者姓名信息
     * @param mindGift
     * @param coverPatientName 是否处理患者姓名，true处理，false处理
     * @return
     */
    private MindGift getMindGiftInfoData(MindGift mindGift,Boolean coverPatientName){

        String mpi=mindGift.getMpiId();
        if(StringUtils.isEmpty(mpi)){
            logger.error("心意单["+mindGift.getMindGiftId()+"]患者mpi为空");
            return null;
        }

        PatientDAO patDao=DAOFactory.getDAO(PatientDAO.class);

        MindGift returnMind=new MindGift();
        returnMind.setMindGiftId(mindGift.getMindGiftId());
        returnMind.setGiftIcon(mindGift.getGiftIcon());
        returnMind.setFiltText(mindGift.getFiltText());
        returnMind.setShowDate(mindGift.getCreateDate());
        returnMind.setSubBusType(mindGift.getSubBusType());
        returnMind.setPatientName(patDao.getNameByMpiId(mpi));
        returnMind.setDoctorAccount(mindGift.getDoctorAccount());
        returnMind.setReadFlag(mindGift.getReadFlag());

        if(coverPatientName){
            returnMind.setPatientName(LocalStringUtil.coverName(returnMind.getPatientName()));
        }

        return returnMind;
    }

    /**
     * 含患者头像
     * @param mindgift
     * @return
     */
    private Map<String,Object> getMindGiftAndPatData(MindGift mindgift){
        Map<String,Object> map=new HashMap<String,Object>();

        MindGift mind=new MindGift();
        mind.setFiltText(mindgift.getFiltText());
        mind.setShowDate(mindgift.getCreateDate());
        mind.setSubBusType(mindgift.getSubBusType());
        map.put("mind",mind);

        PatientService patientService= AppContextHolder.getBean("eh.patientService",PatientService.class);
        Patient p=patientService.getPatientCoverData(mindgift.getMpiId());
        map.put("patient",p);

        return map;
    }

    /**
     * 取消超过24小时的待支付订单,用于支付订单取消
     * @param deadTime
     */
    public void cancelOverTimeNoPayOrder(Date deadTime) {

        List<MindGift> mindList = mindDao.findTimeOverNoPayOrder(deadTime);
        if (ValidateUtil.notBlankList(mindList)) {
            for (MindGift mind : mindList) {
                try {
                    //更新心意状态
                    mind.setCancelCause(PayConstant.OVER_TIME_AUTO_CANCEL_TEXT);
                    mind.setMindGiftStatus(MindGiftConstant.MINDGIFT_STATUS_CANCEL);
                    mind.setCancelTime(new Date());
                    mind.setLastModify(new Date());
                    mindDao.update(mind);
                    //解锁优惠劵
                    if (ValidateUtil.notNullAndZeroInteger(mind.getCouponId()) && mind.getCouponId() != -1) {
                        CouponService couponService = AppContextHolder.getBean("couponService", CouponService.class);
                        couponService.unlockCouponById(mind.getCouponId());
                    }
                } catch (Exception e) {
                    logger.error("cancelMindGiftOverTimeNoPayOrder error, busId[{"+mind.getMindGiftId()+"}], errorMessage[{"+e.getMessage()+"}]");

                }
            }
        }
    }

    /**
     * 查询指定时间的已支付，但敏感词未处理的业务单,
     * 主要用于ons出现问题，无法正常使用
     * @param deadTime
     */
    @RpcService
    public void cancelOverTimeHasPayOrder(Date deadTime) {

        List<MindGift> mindList = mindDao.findTimeOverHasPayOrder(deadTime);
        if (ValidateUtil.notBlankList(mindList)) {
            for (MindGift mind : mindList) {
                try {
                    //更新心意状态
                    Integer mindId=mind.getMindGiftId();
                    Integer updateNum= mindDao.cancelMindGiftById(MindGiftConstant.OVER_TIME_AUTO_CANCEL_TEXT,mindId);

                    //更新心意状态成功
                    if(updateNum!=null && updateNum.intValue()>0){
                        //解锁优惠劵
                        if (ValidateUtil.notNullAndZeroInteger(mind.getCouponId()) && mind.getCouponId() != -1) {
                            CouponService couponService = AppContextHolder.getBean("couponService", CouponService.class);
                            couponService.unlockCouponById(mind.getCouponId());
                        }

                        //发起退款
                        if(ValidateUtil.notBlankString(mind.getTradeNo()) && ValidateUtil.notNullAndZeroInteger(mind.getPayFlag())
                                && mind.getPayFlag()== PayConstant.PAY_FLAG_PAY_SUCCESS){
                            // 执行微信退款
                            WxRefundExecutor executor = new WxRefundExecutor(
                                    mind.getMindGiftId(), "mindgift");
                            executor.execute();
                        }

                        //发送客服消息
                        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
                        smsPushService.pushMsgData2Ons(mindId, mind.getOrgan(), "MindGiftCancel", "MindGiftCancel", mind.getClientId());
                    }else{
                        logger.info("心意单["+mindId+"]已审核或未支付");
                    }

                } catch (Exception e) {
                    logger.error("cancelOverTimeHasPayOrder error, busId[{"+mind.getMindGiftId()+"}], errorMessage[{"+e.getMessage()+"}]");

                }
            }
        }
    }
}
