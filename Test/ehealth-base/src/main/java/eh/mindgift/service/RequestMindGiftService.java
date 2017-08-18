package eh.mindgift.service;

import ctd.account.UserRoleToken;
import ctd.account.session.SessionItemManager;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.BussTypeConstant;
import eh.base.constant.ErrorCode;
import eh.base.dao.EmploymentDAO;
import eh.base.dao.OrganConfigExtDAO;
import eh.bus.constant.ConsultConstant;
import eh.bus.dao.AppointRecordDAO;
import eh.bus.dao.ConsultDAO;
import eh.bus.dao.TransferDAO;
import eh.bus.service.common.CurrentUserInfo;
import eh.coupon.constant.CouponConstant;
import eh.entity.base.Employment;
import eh.entity.bus.AppointRecord;
import eh.entity.bus.Consult;
import eh.entity.bus.Transfer;
import eh.entity.bus.msg.SimpleWxAccount;
import eh.entity.bus.pay.BusTypeEnum;
import eh.entity.mindgift.Gift;
import eh.entity.mindgift.MindGift;
import eh.entity.mpi.Patient;
import eh.entity.wx.WXConfig;
import eh.mindgift.constant.GiftConstant;
import eh.mindgift.constant.MindGiftConstant;
import eh.mindgift.dao.GiftDAO;
import eh.mindgift.dao.MindGiftDAO;
import eh.op.dao.WXConfigsDAO;
import eh.op.dao.WxAppPropsDAO;
import eh.utils.LocalStringUtil;
import eh.utils.params.ParamUtils;
import eh.utils.params.ParameterConstant;
import eh.wxpay.constant.PayConstant;
import eh.wxpay.service.NgariPayService;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Date;
import java.util.Map;


public class RequestMindGiftService {
    private static final Log logger = LogFactory.getLog(RequestMindGiftService.class);

    /**
     * 公众号，机构是否支持送心意
     * 用于显示聊天框是否能送心意按钮
     * @param subBusType 业务类型
     * @param busId 业务单ID
     * @return true 可以，false 不可以
     */
    @RpcService
    public Boolean canSendGiftByOrgan(int busType,int busId){

        if(BussTypeConstant.CONSULT==busType){
            if(!canSendGiftByConsult(busId)){
                return  false;
            }

        }

        SimpleWxAccount simpleWxAccount = CurrentUserInfo.getSimpleWxAccount();
        if(simpleWxAccount==null){
            logger.info("用户信息获取失败，不支持送心意busType="+busType+",busId="+busId);
            return  false;
        }
        String appId = simpleWxAccount.getAppId();

        WXConfigsDAO wxConfigsDAO=DAOFactory.getDAO(WXConfigsDAO.class);
        WXConfig wxConfig=wxConfigsDAO.getByAppID(appId);
        if(wxConfig==null){
            logger.info("公众号信息获取失败，不支持送心意busType="+busType+",busId="+busId);
            return  false;
        }

        //公众号是否可以心意
        WxAppPropsDAO propsDAO=DAOFactory.getDAO(WxAppPropsDAO.class);
        if(!propsDAO.canMindGift(wxConfig.getId())){
            logger.info("公众号["+wxConfig.getId()+"]不支持送心意busType="+busType+",busId="+busId);
            return false;
        }


        //判断机构是否支持送心意
        OrganConfigExtDAO organExtDao=DAOFactory.getDAO(OrganConfigExtDAO.class);
        Integer organ=null;

        //获取业务数据
        switch (busType){
            case 3://咨询
                ConsultDAO consultDao=DAOFactory.getDAO(ConsultDAO.class);
                Consult consult=consultDao.get(busId);
                if(consult!=null && consult.getConsultOrgan()!=null){
                    organ=consult.getConsultOrgan();
                }
                break;
            case 1://转诊（特需预约）
                TransferDAO transferDAO=DAOFactory.getDAO(TransferDAO.class);
                Transfer transfer=transferDAO.get(busId);
                if(transfer!=null && transfer.getConfirmOrgan()!=null){
                    organ=transfer.getConfirmOrgan();
                }
                break;
            case 4://预约
                AppointRecordDAO appointDao=DAOFactory.getDAO(AppointRecordDAO.class);
                AppointRecord appoint=appointDao.get(busId);
                if(appoint!=null && appoint.getOrganId()!=null){
                    organ=appoint.getOrganId();
                }
                break;
            default:
                return false;
        }

        //机构是否支持送心意：true 可以 false 不可以
        if(organ!=null && organExtDao.canNotMindGift(organ)){
            return true;
        }

        return false;
    }


    /**
     * 当前业务单是否支持送心意
     * @param subBusType 业务类型
     * @param busId 业务单ID
     * @return
     *
     * @desc
     * 1>先判断是否公众号，机构是否支持，
     * 2>每个业务单最多10次(次数配置到常量表)
     * 3>判断当前业务单是否支持(非抢单模式团队医生不支持心意)
     */
    @RpcService
    public Boolean canSendGiftByBus(Integer busType,Integer busId){

        Boolean canSendGiftByOrganFlag=canSendGiftByOrgan(busType,busId);
        if(!canSendGiftByOrganFlag){
            throw new DAOException(ErrorCode.SERVICE_ERROR,"当前机构或公众号不支持送心意功能");
        }

        MindGiftDAO mindGiftDAO=DAOFactory.getDAO(MindGiftDAO.class);
        Long effectiveMindGiftsNum=mindGiftDAO.getEffectiveMindGiftsNumByBusTypeAndBusId(busType,busId);

        //每个业务单最多的心意数
        Integer maxNum= Integer.parseInt(ParamUtils.getParam(ParameterConstant.KEY_EFFECTIVE_MINDGIFTS_NUM,"10") );
        Long effectiveNum=effectiveMindGiftsNum==null?0l:effectiveMindGiftsNum;

        if(effectiveNum.intValue()>=maxNum.intValue()){
            logger.info("送心意的次数已用完,busType="+busType+",busId="+busId);
            throw new DAOException(ErrorCode.SERVICE_ERROR,"您送心意的次数已用完!");
        }


        //判断当前业务单是否支持(非抢单模式团队医生不支持心意)
        if(BussTypeConstant.CONSULT==busType.intValue()){
            if(!canSendGiftByConsult(busId)){
                throw new DAOException(ErrorCode.SERVICE_ERROR,"当前模式不支持送心意");
            }

        }

        return true;
    }

    /**
     * 咨询单是否支持送心意
     * @param busId
     * @return
     */
    private Boolean canSendGiftByConsult(Integer busId){
        Boolean canSendGiftByConsult=true;

        ConsultDAO consultDao=DAOFactory.getDAO(ConsultDAO.class);
        Consult consult=consultDao.get(busId);
        if(consult==null){
            logger.info("获取业务单失败,busId="+busId);
            return false;
        }

        //groupMode=1 非抢单模式,不可以发送心意
        int groupMode = consult.getGroupMode() == null ? 0 : consult.getGroupMode();
        if (groupMode == 1) {
            logger.info("非抢单模式,不可以发送心意,busId="+busId);
            canSendGiftByConsult= false;
        }

        return canSendGiftByConsult;
    }


    /**
     * 提交心意
     * @param mind
     * @return
     */
    @RpcService
    public MindGift requestMindGift(MindGift mind){

        logger.info("心意提交数据："+ JSONUtils.toString(mind));

        validRequestMindGiftData(mind);


        canSendGiftByBus(mind.getBusType(),mind.getBusId());


        MindGift mindGift=resertRequestMindGiftData(mind);

        MindGiftDAO dao=DAOFactory.getDAO(MindGiftDAO.class);

        //保存业务数据

        MindGift savedMindGift=dao.save(mindGift);

        return savedMindGift;
    }

    /**
     * 不跳转确认订单页，直接在当前页面进行支付
     * @param mind
     * @return
     */
    @RpcService
    public Map<String, Object> requestMindGiftAndOrderPay(MindGift mind,String payway){
        MindGift mindgift=requestMindGift(mind);
        NgariPayService ngariPayService = AppContextHolder.getBean("eh.payService", NgariPayService.class);
        return  ngariPayService.order(payway,BusTypeEnum.MINDGIFT.getCode(),mindgift.getMindGiftId().toString(), ""+CouponConstant.COUPON_NONE_ID);
    }


    /**
     * 校验数据
     * @param mind
     */
    private void validRequestMindGiftData(MindGift mind){

        if(mind==null){
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "mind is required");
        }

        if(mind.getBusType()==null){
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "busType is required");
        }

        if(mind.getBusId()==null){
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "busId is required");
        }

        if(mind.getGiftId()==null){
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "giftId is required");
        }

        if(StringUtils.isEmpty(mind.getMindText())){
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "giftId is required");
        }
    }

    /**
     * 根据几个主键填充默认值
     * @param mind
     * @return
     */
    private MindGift resertRequestMindGiftData(MindGift mind){

        //设置提交用户
        UserRoleToken urt = UserRoleToken.getCurrent();
        if (urt == null || urt.getProperty("patient") == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR,
                    "账户未登录，无法送心意");
        }
        Patient patient = (Patient) urt.getProperty("patient");

        logger.info("心意提交账户：mpi=["+ patient.getMpiId()+"],userId=["+patient.getLoginId()+"]");

        mind.setMpiId(patient.getMpiId());

        //设置业务数据
        switch (mind.getBusType()){
            case 3://咨询
                mind=getConsultMindGift(mind);
                break;
            case 1://转诊（特需预约）
                mind=getTransferMindGift(mind);
                break;
            case 4://预约
                mind=getAppointMindGift(mind);
                break;
            default:
               throw new DAOException(ErrorCode.SERVICE_ERROR,"当前功能不支持心意功能");
        }


        GiftDAO giftDAO=DAOFactory.getDAO(GiftDAO.class);
        Gift gift=giftDAO.get(mind.getGiftId());
        if(gift==null ){
            throw new DAOException(ErrorCode.SERVICE_ERROR,
                    "所选物品无效");
        }

        int giftStatus=gift.getGiftStatus()==null? GiftConstant.GIFTSTATUS_AVAILABLE:gift.getGiftStatus().intValue();
        if(GiftConstant.GIFTSTATUS_AVAILABLE!=giftStatus){
            throw new DAOException(ErrorCode.SERVICE_ERROR,
                    "所选物品无效");
        }

        //物品(锦旗)赋值
        mind.setGiftIcon(gift.getGiftIcon());
        mind.setGiftType(gift.getGiftType());

        //订单价格赋值
        Double giftPrice=gift.getGiftPrice()==null?Double.valueOf(0d):gift.getGiftPrice();
        mind.setPrice(giftPrice);
        mind.setAmount(giftPrice);
        mind.setActualPrice(giftPrice);

        Date now=new Date();

        mind.setPayFlag(PayConstant.PAY_FLAG_NOT_PAY);

        //物品(锦旗)>0，则显示未支付
        if(giftPrice.intValue()>0){
            mind.setOutTradeNo(BusTypeEnum.MINDGIFT.getApplyNo());// 支付商户订单号
        }

        //其他常量
        mind.setReadFlag(MindGiftConstant.READFLAG_UNREAD);
        mind.setMindGiftStatus(MindGiftConstant.MINDGIFT_STATUS_UNAUDIT);
        mind.setCreateDate(now);
        mind.setLastModify(now);

        //客户端ID
        try {
            SimpleWxAccount wxAccount = CurrentUserInfo.getSimpleWxAccount();

            Integer clientId = SessionItemManager.instance().checkClientAndGet();
            mind.setClientId(clientId);
        } catch (Exception e) {
            logger.info(LocalStringUtil.format("resertRequestMindGiftData exception, errorMessage[{}]", e.getMessage()));
        }


        return  mind;
    }



    /**
     * 组装预约心意单
     * @param mind
     * @return
     */
    private MindGift getAppointMindGift(MindGift mind){
        AppointRecordDAO appointRecordDAO=DAOFactory.getDAO(AppointRecordDAO.class);
        Integer busId=mind.getBusId();
        AppointRecord appointRecord = appointRecordDAO.getByAppointRecordId(busId);
        Integer doctorId = null;
        if (appointRecord == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "不存在该预约单");
        }
        if (appointRecord.getDoctorId()== null) {
            logger.error("预约单[" + busId + "]信息不完整，不能送心意");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "预约单信息不完整，不能送心意");
        }else{
            doctorId=appointRecord.getDoctorId();
        }
        if (appointRecord.getAppointStatus() != 1 && appointRecord.getAppointStatus() != 0 && appointRecord.getCancelResean() != null && appointRecord.getEvaStatus()!=1) {
            logger.error("预约单[" + busId + "]未预约成功或未就诊,不能送心意");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "预约单未预约成功或未就诊,不能送心意");
        }

        //给执行医生送心意
        mind.setDoctorId(doctorId);
        mind.setOrgan(appointRecord.getOrganId());
        mind.setSubBusType(MindGiftConstant.SUBBUSTYPE_YYGH);

        //获取科室
        EmploymentDAO empDao=DAOFactory.getDAO(EmploymentDAO.class);
        Employment emp=empDao.getPrimaryEmpByDoctorId(doctorId);
        if(emp!=null){
            mind.setDepartment(emp.getDepartment());
        }

        return mind;
    }

    /**
     * 组装转诊心意单
     * @param mind
     * @return
     */
    private MindGift getTransferMindGift(MindGift mind){
        TransferDAO transferDAO=DAOFactory.getDAO(TransferDAO.class);
        Integer busId=mind.getBusId();
        Transfer transfer = transferDAO.getById(busId);
        Integer doctorId = null;
        if (transfer == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "不存在该转诊单");
        }
        if (transfer.getConfirmDoctor()== null) {
            logger.error("转诊单[" + busId + "]信息不完整，不能送心意");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "转诊单信息不完整，不能送心意");
        }else{
            doctorId=transfer.getConfirmDoctor();
        }
        if (transfer.getTransferStatus() != 2 && transfer.getTransferResult() != 1 && transfer.getRefuseCause() != null && transfer.getEvaStatus()!=1) {
            logger.error("转诊单[" + busId + "]未接收成功或未就诊,不能送心意");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "转诊单未接受成功或未就诊,不能送心意");
        }

        //给执行医生送心意
        mind.setDoctorId(doctorId);
        mind.setOrgan(transfer.getConfirmOrgan());
        mind.setDepartment(transfer.getConfirmDepart());
        mind.setSubBusType(MindGiftConstant.SUBBUSTYPE_TXYY);

        return mind;
    }

    /**
     * 组装咨询心意单
     * @param mind
     * @return
     */
    private MindGift getConsultMindGift(MindGift mind){
        ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
        Integer busId=mind.getBusId();
        Integer exeDocId = null;

        Consult consult = consultDAO.getById(mind.getBusId());
        if (consult == null) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "不存在该咨询单");
        }

        Boolean teams=consult.getTeams()==null?Boolean.valueOf(false):consult.getTeams();

        //团队医生
        if(teams){
            if (consult.getExeDoctor() == null) {
                logger.error("咨询单[" + busId + "]无执行医生，不能送心意");
                throw new DAOException(ErrorCode.SERVICE_ERROR, "咨询单信息不完整，不能送心意");
            } else {
                exeDocId = consult.getExeDoctor();
            }
        }else{
            exeDocId=consult.getConsultDoctor();
        }


        //给执行医生送心意
        mind.setDoctorId(exeDocId);
        mind.setOrgan(consult.getConsultOrgan());
        mind.setDepartment(consult.getConsultDepart());
        mind.setSubBusType(ConsultConstant.CONSULT_TYPE_POHONE.equals(consult.getRequestMode())?Integer.valueOf(MindGiftConstant.SUBBUSTYPE_DHZX):
                ConsultConstant.CONSULT_TYPE_GRAPHIC.equals(consult.getRequestMode())?Integer.valueOf(MindGiftConstant.SUBBUSTYPE_TWZX):
                        ConsultConstant.CONSULT_TYPE_PROFESSOR.equals(consult.getRequestMode())?Integer.valueOf(MindGiftConstant.SUBBUSTYPE_ZJJD):
                                ConsultConstant.CONSULT_TYPE_RECIPE.equals(consult.getRequestMode())?Integer.valueOf(MindGiftConstant.SUBBUSTYPE_XYWY):null);

        return mind;
    }

}
