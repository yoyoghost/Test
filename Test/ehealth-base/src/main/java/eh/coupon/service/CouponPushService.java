package eh.coupon.service;

import ctd.account.session.SessionItemManager;
import ctd.net.broadcast.MQHelper;
import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import ctd.util.context.Context;
import ctd.util.context.ContextUtils;
import eh.base.constant.SystemConstant;
import eh.base.dao.DeviceDAO;
import eh.base.dao.DoctorDAO;
import eh.base.dao.OrganDAO;
import eh.base.user.UserSevice;
import eh.bus.service.common.CurrentUserInfo;
import eh.bus.service.consult.OnsConfig;
import eh.coupon.constant.CouponConstant;
import eh.coupon.dao.CouponInfoDAO;
import eh.entity.base.Device;
import eh.entity.base.Doctor;
import eh.entity.base.Organ;
import eh.entity.bus.AppointRecord;
import eh.entity.bus.msg.SimpleWxAccount;
import eh.entity.coupon.CouponInfo;
import eh.entity.mpi.Patient;
import eh.entity.mpi.RelationDoctor;
import eh.entity.wx.WXConfig;
import eh.mpi.dao.PatientDAO;
import eh.op.dao.WXConfigsDAO;
import eh.utils.ValidateUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息推送改造
 * Created by Administrator on 2016/12/16.
 */
// 业务做埋点，哪些业务需要做埋点
// 系统自动处理时，获取不到appId,获取不到WxConfigId
// APP端获取不到appId
public class CouponPushService {
    private static final Logger logger = LoggerFactory.getLogger(CouponPushService.class);

    private CouponInfoDAO infoDao=DAOFactory.getDAO(CouponInfoDAO.class);


    @RpcService
    public void getCouponMsg() {
        HttpServletRequest request = (HttpServletRequest) ContextUtils.get(Context.HTTP_REQUEST);
        String appid=request.getHeader("X-App-Id");
    }


    /**
     * 注册发放优惠劵
     */
    public void sendRegisteCouponMsg(Integer urt,String desc){
        logger.info(desc+"，urt="+urt);
        CouponInfo info=new CouponInfo();
        info.setUrt(urt);
        info.setBussId(urt);
        info.setServiceType(CouponConstant.COUPON_SERVICETYPE_REGIST);
        info=getManageUnit(info);
        info=getCouponInfoWithProperties(info);
        CouponInfo savedInfo =infoDao.saveCoupon(info);

        logger.info("注册发送优惠劵"+JSONUtils.toString(savedInfo));
        pushCouponMsg(savedInfo);
    }

    /**
     * 关注发送优惠劵
     * 每个患者每天100个劵，开处方的注册医生，同个医生只发一次，取消关注不退回，取消关注重新关注，不再发送，不区分扫码线上关注
     * 当前区分是base项目作区分
     * 关注途径：[未注册扫码关注，注册后扫码关注，注册后直接关注，取消关注重新关注]
     */
    public void sendRelationCouponMsg(RelationDoctor relation){

        Doctor doc=DAOFactory.getDAO(DoctorDAO.class).get(relation.getDoctorId());
        if(doc==null){
            logger.error("找不到关注的医生"+JSONUtils.toString(relation));
            return;
        }

        Patient patient=DAOFactory.getDAO(PatientDAO.class).get(relation.getMpiId());
        if(patient==null){
            logger.error("找不到关注医生的患者"+JSONUtils.toString(relation));
            return;
        }

        //获取医生的urtId
        UserSevice userSevice=AppContextHolder.getBean("eh.userSevice",UserSevice.class);
        Integer docurtId=userSevice.getUrtIdByUserId(doc.getMobile(), SystemConstant.ROLES_DOCTOR);
        if(docurtId!=null && docurtId<0){
            logger.error("医生["+doc.getDoctorId()+"]不是注册的医生，不能发送优惠劵");
            return;
        }

        //获取患者的urtId
        Integer urtId=userSevice.getUrtIdByUserId(patient.getLoginId(), SystemConstant.ROLES_PATIENT);
        if(urtId!=null && urtId<0){
            logger.error("关注医生的患者找不到患者urt:"+JSONUtils.toString(relation));
            return;
        }

        CouponInfo info=new CouponInfo();
        info.setUrt(urtId);
        info.setServiceType(CouponConstant.COUPON_SERVICETYPE_RELATION);
        info.setDoctorId(doc.getDoctorId());
        info.setOrganId(doc.getOrgan());
        info.setBussId(relation.getRelationDoctorId());
        info=getManageUnit(info);
        info=getCouponInfoWithProperties(info);

        List<CouponInfo> list=infoDao.findRelations(urtId,doc.getDoctorId());
        //关注医生后，取消关注，重新关注不发送优惠劵
        if(list.size()==0){
            CouponInfo savedInfo =infoDao.saveCoupon(info);
            logger.info("关注医生发送优惠劵"+JSONUtils.toString(savedInfo));
            pushCouponMsg(savedInfo);
        }
    }

    /**
     * 预约成功发送优惠劵(普通预约成功、当天挂号支付成功并医院成功、特需预约被医生接收并医院成功)
     */
    public void sendAppointSuccessCouponMsg(AppointRecord record){

        String reqMpi=record.getAppointUser();
        if(reqMpi.length()<32){
            logger.error("预约单为医生端发起的，不进行发送业务"+record.getAppointRecordId());
            return;
        }
        PatientDAO patDao=DAOFactory.getDAO(PatientDAO.class);
        Patient patient=patDao.get(reqMpi);
        if(patient==null){
            logger.error("找不到相对应的患者用户"+reqMpi);
            return;
        }
        //获取患者的urtId
        UserSevice userSevice=AppContextHolder.getBean("eh.userSevice",UserSevice.class);
        Integer urtId=userSevice.getUrtIdByUserId(patient.getLoginId(), SystemConstant.ROLES_PATIENT);

        CouponInfo info=new CouponInfo();
        info.setUrt(urtId);
        info.setClientId(record.getDeviceId());
        info.setServiceType(CouponConstant.COUPON_SERVICETYPE_APPOINT);
        info.setBussId(record.getAppointRecordId());
        info.setDoctorId(record.getDoctorId());
        info.setOrganId(record.getOrganId());
        info=getManageUnit(info);
        info=getCouponInfoWithProperties(info);
        CouponInfo savedInfo =infoDao.saveCoupon(info);

        logger.info("预约成功发送优惠劵"+JSONUtils.toString(savedInfo));
        pushCouponMsg(savedInfo);
    }

    private CouponInfo getManageUnit(CouponInfo info){
        String manageUnitId = "eh";
        Integer organId=info.getOrganId();
        Organ organ=DAOFactory.getDAO(OrganDAO.class).get(organId);
        if(organ!=null){
            manageUnitId=organ.getManageUnit();
        }
        info.setManageUnit(manageUnitId);
        return info;
    }

    private CouponInfo getCouponInfoWithProperties(CouponInfo info){
        String appId=null;

        try {
            if(info.getClientId()==null){
                info.setClientId(SessionItemManager.instance().checkClientAndGet());
            }

            SimpleWxAccount simpleWxAccount = CurrentUserInfo.getSimpleWxAccount();
            if (simpleWxAccount != null && !ValidateUtil.blankString(simpleWxAccount.getAppId())) {
                appId=simpleWxAccount.getAppId();
            }

        }catch (Exception e){
            logger.error("获取用户信息失败："+e.getMessage());
        }

        //如果无法直接获取到appId，则通过设备信息获取
        //有活动需要针对公众号，所有服务请求必须有wxconfig信息
        if(StringUtils.isEmpty(appId)){
            DeviceDAO deviceDAO=DAOFactory.getDAO(DeviceDAO.class);
            Integer clientId=info.getClientId();
            Device device=deviceDAO.get(clientId);
            if(device!=null && SystemConstant.CLIENT_OS_WX.equalsIgnoreCase(device.getOs())){
                appId=StringUtils.substringAfter(device.getToken(),"@");
            }
        }

        WXConfigsDAO configsDAO= DAOFactory.getDAO(WXConfigsDAO.class);
        WXConfig configs=configsDAO.getByAppID(appId);
        if(configs!=null){
            info.setWxConfigId(configs.getId());
        }

        return info;
    }

    private void pushCouponMsg(CouponInfo info){
        logger.info("发送ons消息队列:"+ JSONUtils.toString(info));
        Map<String,Object> map=new HashMap<String,Object>();
        BeanUtils.copy(info,map);
        if(StringUtils.isEmpty(OnsConfig.couponTopic)){
            logger.info("ons消息队列Topic为空,不发消息");
            return;
        }
        MQHelper.getMqPublisher().publish(OnsConfig.couponTopic, map);
    }
}
