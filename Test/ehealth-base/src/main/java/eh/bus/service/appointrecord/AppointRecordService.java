package eh.bus.service.appointrecord;

import com.alibaba.fastjson.JSONObject;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.spring.AppDomainContext;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.dao.DoctorDAO;
import eh.base.service.organ.OrganConfigService;
import eh.bus.constant.CloudClinicSetConstant;
import eh.bus.dao.AppointRecordDAO;
import eh.bus.dao.CloudClinicQueueDAO;
import eh.bus.dao.OrderDao;
import eh.bus.his.service.AppointTodayBillService;
import eh.bus.service.video.RTMService;
import eh.coupon.service.CouponService;
import eh.entity.base.Doctor;
import eh.entity.bus.*;
import eh.entity.bus.pay.BusTypeEnum;
import eh.entity.his.hisCommonModule.HisResponse;
import eh.entity.mpi.Patient;
import eh.mpi.dao.PatientDAO;
import eh.push.SmsPushService;
import eh.unifiedpay.service.UnifiedPayService;
import eh.unifiedpay.service.UnifiedRefundService;
import eh.utils.DateConversion;
import eh.utils.ValidateUtil;
import eh.wxpay.service.NgariPayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by Luphia on 2016/11/25.
 */
public class AppointRecordService {
    private final static Logger logger = LoggerFactory.getLogger(AppointRecordService.class);

    public static boolean isToday(Date date) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
        if (fmt.format(date).toString().equals(fmt.format(new Date()).toString())) {
            return true;
        } else {
            return false;
        }
    }

    @RpcService
    public void sendMsgPushToOutClinicDoctor() {
        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
        //发送消息通知
        Integer clientId = null;
        Integer organId = 0;//仅作通知置零避免抛异常
        String busType = "ClinicRemindToOut";
        String smsType = "ClinicRemindToOut";
        Integer busId = 0;
        smsPushService.pushMsgData2Ons(busId, organId, busType, smsType, clientId);
//        AppointRecordDAO recordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
//        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
//        Date nowDate = new Date();
//        Date lastDate = DateConversion.getDateAftMinute(nowDate, 30);
//        List<Object[]> objects = recordDAO.findOutDoctorsNotReminded(nowDate, lastDate);
//        HashMap<String, Object> customContent = new HashMap<String, Object>();
//        customContent.put("action_type", MessagePushConstant.ACTION_TYPE_OPEN_APP);
//        customContent.put("activity", MessagePushConstant.ACTIVITY_HOME);
//        customContent.put("aty_attr", new HashMap<String, Object>());
//        for (Object[] object : objects) {
//            Long count = (Long) object[0];
//            Integer docId = (Integer) object[1];
//            Date startTime = (Date) object[2];
//            Integer recordId = (Integer) object[3];
//            if (docId == null) {
//                continue;
//            }
//            if (startTime == null) {
//                continue;
//            }
//            String lastTime = DateConversion.getDateFormatter(startTime, "H:mm");
//            String mobile = doctorDAO.getMobileByDoctorId(docId);
//            String content = "您有" + count + "个远程联合门诊需要处理，最近就诊时间为" + lastTime + "。请及时处理~";
//            MsgPushService.pushMsgToDoctor(mobile, content, customContent);
//            if (recordId == null) {
//                continue;
//            }
//            recordDAO.updateRemindFlagByAppointRecordId(true, recordId);
//        }
    }

    /**
     * 获取当前医生的云门诊申请记录，包括自己是接诊方和申请医生的记录
     *
     * @param doctorId
     * @param start
     * @param limit
     * @return zhangsl 2017-03-20 14:49:10
     */
    @RpcService
    public Map<String, Object> findRequestClinicRecord(int doctorId, int start, int limit) {
        AppointRecordDAO recordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
        List<AppointRecordAndPatient> list = recordDAO.findRequestClinicRecordList(doctorId, start, limit);
        Map<String, Object> map = new HashMap<String, Object>();
        List<AppointRecordAndPatient> orderPay = new ArrayList<>();
        List<AppointRecordAndPatient> notStart = new ArrayList<>();
        List<AppointRecordAndPatient> hasStart = new ArrayList<>();
        for (AppointRecordAndPatient arp : list) {
            Integer status = arp.getAppointrecord().getAppointStatus();
            Integer clinicStatus = arp.getAppointrecord().getClinicStatus();
            //待支付
            if (status == 4 && clinicStatus == 0) {
                orderPay.add(arp);
            //未开始
            } else if ((status==0 ||status==5)&& clinicStatus == 0) {
                notStart.add(arp);
            //已结束
            } else {
                hasStart.add(arp);
            }
        }
        map.put("orderPay", orderPay);
        map.put("unfinished", notStart);
        map.put("completed", hasStart);
        return map;
    }

    /**
     * 获取当前医生的云门诊出诊记录（上级医生）
     *
     * @param doctorId
     * @param start
     * @param limit
     * @return zhangsl 2017-03-20 14:49:10
     */
    @RpcService
    public Map<String, Object> findReceiveClinicRecord(int doctorId, int start, int limit) {
        AppointRecordDAO recordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
        List<AppointRecordAndPatient> list = recordDAO.findReceiveClinicRecordList(doctorId, start, limit);
        Map<String, Object> map = new HashMap<String, Object>();
        List<AppointRecordAndPatient> notStart = new ArrayList<>();
        List<AppointRecordAndPatient> hasStart = new ArrayList<>();
        for (AppointRecordAndPatient arp : list) {
            if (arp.getAppointrecord().getClinicStatus() == 0) {
                //arp.setAppointrecord(recordDAO.convertAppointRecordForRequestList(arp.getAppointrecord()));
                notStart.add(arp);
            } else {
                hasStart.add(arp);
            }
        }
        map.put("unfinished", notStart);
        map.put("completed", hasStart);
        return map;
    }

    /**
     * 给上级医生发送当前总待就诊数量信息短信（供定时器调用）
     * zhangsl 2017-03-23 09:54:18
     */
    @RpcService
    public void sendNumRemindMsgToOutClinicDoctor() {
        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
        //发送消息通知
        Integer clientId = null;
        Integer organId = 0;//仅作通知置零避免抛异常
        String busType = "OrderNumRemindToOut";
        String smsType = "OrderNumRemindToOut";
        Integer busId = 0;
        smsPushService.pushMsgData2Ons(busId, organId, busType, smsType, clientId);
        logger.info("clinicNumRemindMsg has seed to ons");
    }

    public HashMap<String, Object> findToDoTodayRequestList(int doctorId, String platform) {
        CloudClinicQueueDAO queueDAO = DAOFactory.getDAO(CloudClinicQueueDAO.class);
        AppointRecordDAO recordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);

        List<Object[]> objectList = queueDAO.findAllQueueAndTargetByRequest(doctorId);
        List<String> telIds = new ArrayList<String>();
        List<Doctor> ds = new ArrayList<Doctor>();
        List<CloudClinicQueue> queues = new ArrayList<CloudClinicQueue>();
        HashMap<String, Object> result = new HashMap<String, Object>();
        for (Object[] objects : objectList) {
            CloudClinicQueue queue = (CloudClinicQueue) objects[1];
            if (queue == null) {
                continue;
            }
            String telClinicId = queue.getTelClinicId();
            if (telClinicId != null && !StringUtils.isEmpty(telClinicId)) {
                telIds.add(telClinicId);
                AppointRecord ar = recordDAO.getByTelClinicIdAndClinicObject(telClinicId, 1);
                if (ar != null) {
                    queue.setAppointRecordId(ar.getAppointRecordId());
                }
            }
            ds.add((Doctor) objects[0]);
            String mpiId = queue.getMpiId();
            if (mpiId != null && !StringUtils.isEmpty(mpiId)) {
                queue.setPatient(patientDAO.get(mpiId));
            }
            Integer target = queue.getTargetDoctor();
            if (target != null && target > 0) {
                queue.setTarDoc(doctorDAO.get(target));
            }
            queues.add(queue);
        }
        //从信令获取视频状态
        RTMService rtmService = AppDomainContext.getBean("eh.rtmService", RTMService.class);
        List<Map<String, Object>> statusList = rtmService.getOnlineAndFactByDoctorIds(ds, platform);
        for (int i = 0; i < queues.size(); i++) {
            Integer fact = 0;
            Integer online = 0;
            if (statusList != null && statusList.size() > 0) {
                fact = (Integer) statusList.get(i).get("fact");
                online = (Integer) statusList.get(i).get("online");
            }
            queues.get(i).setFact(fact);
            queues.get(i).setOnline(online);
        }

        List<Object[]> list = recordDAO.findAllUnClinicToday(doctorId, telIds);
        List<Doctor> doctorlist = new ArrayList<Doctor>();
        List<HashMap<String, Object>> records = new ArrayList<HashMap<String, Object>>();
        for (Object[] objects : list) {
            HashMap<String, Object> map = new HashMap<String, Object>();
            doctorlist.add((Doctor) objects[0]);
            AppointRecord record = (AppointRecord) objects[1];
            map.put("record", record);
            Patient p = new Patient();
            Doctor doctor = new Doctor();
            if (record != null) {
                String mpiId = record.getMpiid();
                if (mpiId != null && !StringUtils.isEmpty(mpiId)) {
                    p = patientDAO.get(mpiId);
                }
                Integer oppDoc = record.getOppdoctor();
                if (oppDoc != null && oppDoc > 0) {
                    doctor = doctorDAO.get(oppDoc);
                }
            }
            map.put("patient", p);
            map.put("tarDoc", doctor);
            records.add(map);
        }
        //从信令获取视频状态
        List<Map<String, Object>> statuses = rtmService.getOnlineAndFactByDoctorIds(doctorlist, platform);
        for (int i = 0; i < records.size(); i++) {
            Integer fact = 0;
            Integer online = 0;
            if (statuses != null && statuses.size() > 0) {
                fact = (Integer) statuses.get(i).get("fact");
                online = (Integer) statuses.get(i).get("online");
            }
            ((AppointRecord) records.get(i).get("record")).setFact(fact);
            ((AppointRecord) records.get(i).get("record")).setOnline(online);
        }

        result.put("queues", queues);
        result.put("records", records);
        return result;
    }

    /**
     * 今日待办
     *
     * @param doctorId
     * @param platform
     * @return
     */
    @RpcService
    public HashMap<String, Object> findToDoToday(int doctorId, String platform) {
        if (platform == null || org.apache.commons.lang3.StringUtils.isEmpty(platform)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "platform is required!");
        }

        HashMap<String, Object> result = new HashMap<String, Object>();
        if (platform.equals(CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_ANDROID) ||
                platform.equals(CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_IOS)) {
            platform = CloudClinicSetConstant.CLOUDCLINICSET_PLATFORM_XIAOYU;
        }

        CloudClinicQueueDAO queueDAO = DAOFactory.getDAO(CloudClinicQueueDAO.class);
        List<CloudClinicQueue> targets = queueDAO.findAllQueueByTargetOrderByType(doctorId, platform);
        HashMap<String, Object> request = findToDoTodayRequestList(doctorId, platform);

        result.put("targets", targets);
        result.put("request", request);
        return result;
    }

    /**
     * 就诊确认服务
     * @param request
     * @return
     */
    @RpcService
    public HisResponse confirmTreatment(AppointTreatmentRequest request){
        AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
        HisResponse response = appointRecordDAO.updateTreatmentConfirm(request);
        return response;
    }

    /**
     * 获取医生是否有待支付云门诊单子
     */
    @RpcService
    public Boolean hasOrderPay(int doctorId){
        AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
        Long count=appointRecordDAO.getCloudClinicOrderPayNum(doctorId, DateConversion.getDateAftMinute(new Date(),-5));
        return count>0?true:false;
    }

    /**
     * 云门诊预约单超时未支付更新状态
     * @param overTime
     */
    @RpcService
    public void updateOrderPayCloudClinicTimeOut(Date overTime) {
        AppointRecordDAO appointRecordDAO=DAOFactory.getDAO(AppointRecordDAO.class);
        List<AppointRecord> list = appointRecordDAO.findCloudClinicTimeOut(overTime);
        for (AppointRecord r : list) {
            logger.info("更新预约未支付记录：" + r.getAppointRecordId());
            try {
                appointRecordDAO.cancelAppoint(r.getAppointRecordId(), "系统", "系统", "超时未支付,系统自动取消");
            }catch (Exception e) {
                logger.error(e.getMessage());
                continue;
            }
        }
//        List<AppointRecord> list = appointRecordDAO.findCloudClinicTimeOut(overTime);
//        for (AppointRecord r : list) {
//            logger.info("更新当天未支付云门诊记录：appointRecordId[{}]", r.getAppointRecordId());
//            appointRecordDAO.updateCancel(r.getAppointRecordId(), new Date(), "系统", "系统", "超时未支付,预约自动取消");
//            if (r.getClinicObject() == 1) {
//                try {
//                    AppContextHolder.getBean("followUpdateService", FollowUpdateService.class).deleteByAppointRecordId(r.getAppointRecordId());
//                } catch (Exception e) {
//                    logger.info("after updateNoPayAppointList deleteByAppointRecordId faild and appointRecordId is [{}]", r.getAppointRecordId());
//                }
//            }
//            AppointSourceDAO appointSourceDAO = DAOFactory.getDAO(AppointSourceDAO.class);
//            AppointSource appointSource = appointSourceDAO.getByAppointSourceId(r.getAppointSourceId());
//            if (appointSource.getUsedNum() > 0) {
//                int usedNum = appointSource.getUsedNum() - 1;
//                appointSourceDAO.updateUsedNumByAppointSourceId(usedNum, r.getAppointSourceId());
//            }
//
//        }
    }

    /**
     * 能否支付云门诊
     *
     * @param telClinicId 云诊室序号
     * @return double
     */
    @RpcService
    public boolean canPayCloudClinic(String telClinicId) {
        if (telClinicId == null || StringUtils.isEmpty(telClinicId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "telClinicId is required!");
        }
        AppointRecordDAO recordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
        AppointRecord appointRecord = recordDAO.getByTelClinicIdAndClinicObject(telClinicId, 2);
        if (appointRecord == null || appointRecord.getOrganId() == null || appointRecord.getAppointStatus() == null || appointRecord.getAppointDate() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "appointRecord is required!");
        }
        Integer status = appointRecord.getAppointStatus();
        if (!status.equals(4)) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "预约信息已变更，请刷新！");
        }
        Integer organId = appointRecord.getOrganId();
        Date workDate = appointRecord.getWorkDate();
        OrganConfigService organConfigService = AppDomainContext.getBean("eh.organConfigService", OrganConfigService.class);
        if (!organConfigService.canShowPayButton(organId, workDate)) {
            throw new DAOException(ErrorCode.SERVICE_ERROR,"请在规定时间内支付");
        }
        cloudClinicPreBill(telClinicId);
        return true;
    }

    /**
     * 需要发送his的云门诊 预结算
     */
    @RpcService
    public void cloudClinicPreBill(String telClinicId){
    	AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
    	List<AppointRecord> appointRecords = appointRecordDAO.findByTelClinicId(telClinicId);
    	RequestAppointService requestAppointService = AppContextHolder.getBean("requestAppointService", RequestAppointService.class);
        AppointRecord appointRecord = requestAppointService.isCloudClinicAppointNeedToHis(appointRecords);
        if(appointRecord != null){
        	if (appointRecord == null || appointRecord.getOrganId() == null || appointRecord.getAppointStatus() == null || appointRecord.getAppointDate() == null) {
                throw new DAOException(DAOException.VALUE_NEEDED, "appointRecord is required!");
            }
            if (appointRecord.getAppointStatus() != 4) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "预约信息已变更，请刷新！");
            }
            Integer organId = appointRecord.getOrganId();
            Date workDate = appointRecord.getWorkDate();
            OrganConfigService organConfigService = AppDomainContext.getBean("eh.organConfigService", OrganConfigService.class);
            if (!organConfigService.canShowPayButton(organId, workDate)) {
                throw new DAOException(ErrorCode.SERVICE_ERROR,"请在规定时间内支付");
            }
            AppointTodayBillService appointTodayBillService = AppContextHolder.getBean("eh.billService", AppointTodayBillService.class);
    		Map<String,Object> resMap = appointTodayBillService.setRequest(appointRecord);
    		if(null != resMap && resMap.get("errorCode").equals("canPay")){
    			double clinicPrice = (Double)resMap.get("preBill");
                for (AppointRecord ar : appointRecords) {
                	ar.setClinicPrice(clinicPrice);
                    appointRecordDAO.update(ar);
                }
    		}else{
    			throw new DAOException(ErrorCode.SERVICE_ERROR, "预结算失败。请稍后重试！");
    		}
        }
    }

    /**
     * 获取支付二维码
     *
     * @param telClinicId
     * @param payway
     * @param price
     * @param appId
     * @return String
     */
    @RpcService
    public String getUrlByTelClinicId(String telClinicId, String payway, double price, String appId) {
        if (payway == null || StringUtils.isEmpty(payway)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "payway is required!");
        }
        if (price <= 0) {
            throw new DAOException(DAOException.VALUE_NEEDED, "price is required!");
        }
        this.canPayCloudClinic(telClinicId);

        NgariPayService payService = AppDomainContext.getBean("eh.payService", NgariPayService.class);
        AppointRecordDAO recordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
        AppointRecord record = recordDAO.getByTelClinicIdAndClinicObject(telClinicId, 2);
        String url = record.getQrCode();
        String oldPayWay = record.getPayWay();

        // 入参支付方式已有二维码
        if (oldPayWay != null && payway.equals(oldPayWay)) {
            // 判断二维码是否失效
            UnifiedPayService unifiedPayService = AppDomainContext.getBean("eh.unifiedPayService", UnifiedPayService.class);
            Map<String, Object> queryMap = unifiedPayService.orderQuery(record.getAppointRecordId(), BusTypeEnum.APPOINTCLOUD.getCode());
            boolean ifNew = false;
            if (queryMap != null) {
                if ("SUCCESS".equals(queryMap.get("code")) && "CLOSED".equals(queryMap.get("trade_status"))) {
                    ifNew = true;
                } else if ("FAIL".equals(queryMap.get("code")) && "交易不存在".equals(queryMap.get("msg"))) {
                    OrderDao orderDao = DAOFactory.getDAO(OrderDao.class);
                    Order order = orderDao.getByOutTradeNo(record.getOutTradeNo());
                    if (order == null || order.getCreateTime() == null) {
                        throw new DAOException(DAOException.VALUE_NEEDED, "order is required!");
                    }
                    Date createTime = order.getCreateTime();
                    Date nMinutesAgo = DateConversion.getDateAftXMinutes(new Date(), -90);
                    if (!createTime.after(nMinutesAgo)) {
                        ifNew = true;
                    }
                } else if ("FAIL".equals(queryMap.get("code"))) {
                    logger.info("order query fail but the msg is not 交易不存在" + JSONUtils.toString(queryMap));
                }
            }
            if (ifNew) {
                // 二维码失效，重新生成二维码
                Map<String, Object> map = payService.appOrder(appId, payway, BusTypeEnum.APPOINTCLOUD.getCode(), String.valueOf(record.getAppointRecordId()), "");
                if (map == null || map.get("qr_code") == null) {
                    logger.error("payService qr_code is required,or map is null...telClinicId=" + telClinicId + ",payway=" + payway + ",price=" + price + ",appId=" + appId);
                    throw new DAOException(ErrorCode.SERVICE_ERROR, "预约信息已变更，请刷新！");
                }
                url = (String) map.get("qr_code");
                recordDAO.updateQrCodeCloud(url, telClinicId);
            }
            return url;
        }

        if (oldPayWay != null && !payway.equals(oldPayWay)) {
            //后期接其他支付方式，取消上一次订单操作使用
        }

        Map<String, Object> map = payService.appOrder(appId, payway, BusTypeEnum.APPOINTCLOUD.getCode(), String.valueOf(record.getAppointRecordId()), "");
        if (map == null || map.get("qr_code") == null) {
            logger.error("payService qr_code is required,or map is null...telClinicId=" + telClinicId + ",payway=" + payway + ",price=" + price + ",appId=" + appId);
            throw new DAOException(ErrorCode.SERVICE_ERROR, "预约信息已变更，请刷新！");
        }
        url = (String) map.get("qr_code");
        recordDAO.updateQrCodeCloud(url, telClinicId);
        return url;
    }

    /**
     * 对支付有异常的单子进行处理
     */
    @RpcService
    public void refundAllCloudNotSuccess() {
        AppointRecordDAO recordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
        Date nowDate = new Date();
        List<AppointRecord> records = recordDAO.findAllNeedRefundCloud(nowDate);
        if (records == null || records.isEmpty()) {
            return;
        }
        for (AppointRecord appointRecord : records) {
            try {
                String outTradeNo = appointRecord.getOutTradeNo();
                if (outTradeNo != null && !StringUtils.isEmpty(outTradeNo)) {
                    //产生交易订单的需要关闭订单或退款
                    UnifiedRefundService refundService = AppDomainContext.getBean("eh.unifiedRefundService", UnifiedRefundService.class);
                    Integer payFlag = appointRecord.getPayFlag();
                    Integer appointRecordId = appointRecord.getAppointRecordId();
                    String telClinicId = appointRecord.getTelClinicId();

                    UnifiedPayService payService = AppDomainContext.getBean("eh.unifiedPayService", UnifiedPayService.class);
                    Map<String, Object> queryMap = payService.orderQuery(appointRecordId, BusTypeEnum.APPOINTCLOUD.getCode());
                    if (queryMap != null && "SUCCESS".equals(queryMap.get("code"))) {
                        if ("SUCCESS".equals(queryMap.get("trade_status"))) {
                            //订单支付成功，做退款操作
                            Map<String, Object> map = refundService.refund(appointRecordId, BusTypeEnum.APPOINTCLOUD.getCode());
                            if (map != null && "SUCCESS".equals(map.get("code"))) {
                                recordDAO.updatePayFlagAndStatusCloud(3, 6, telClinicId);
                            } else {
                                recordDAO.updatePayFlagAndStatusCloud(4, 7, telClinicId);
                            }
                        }else if ("CLOSED".equals(queryMap.get("trade_status"))) {
                            //订单已关闭，表示该订单已退款
                            recordDAO.updatePayFlagAndStatusCloud(3, 6, telClinicId);
                        }
                    }
                }
            } catch (Exception e) {
                logger.error(e.getMessage());
            } finally {
                continue;
            }
        }
    }

    /**
     * 云门诊超过就诊时间未支付订单取消
     */
    @RpcService
    public void cancelOverTimeNoPayCloud() {
        AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
        List<AppointRecord> arList = appointRecordDAO.findTimeOverNoPayCloud(DateConversion.getFormatDate(new Date(), "yyyy-MM-dd"));
        if (ValidateUtil.notBlankList(arList)) {
            for (AppointRecord ar : arList) {
                try {
                    ar.setCancelResean("超时未支付,系统自动取消");
                    ar.setAppointStatus(2);
                    appointRecordDAO.update(ar);
                    if (ValidateUtil.notNullAndZeroInteger(ar.getCouponId()) && ar.getCouponId() != -1) {
                        CouponService couponService = AppContextHolder.getBean("couponService", CouponService.class);
                        couponService.unlockCouponById(ar.getCouponId());
                    }
                } catch (Exception e) {
                    logger.error("cancelOverTimeNoPayOrder error, busId[{}], errorMessage[{}], stackTrace[{}]", ar.getAppointRecordId(), e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
                }
            }
        }
    }

    /**
     * 根据预约单id获取预约单患者医生详情
     * zhangsl 2017-06-05 14:33:20
     * @param appointRecordId
     * @return
     */
    @RpcService
    public Map<String,Object> getAppointRecordInfoById(int appointRecordId){
        Map<String,Object> map=new HashMap<String,Object>();
        AppointRecordDAO recordDAO=DAOFactory.getDAO(AppointRecordDAO.class);
        AppointRecord ar=recordDAO.getByAppointRecordId(appointRecordId);
        if(ar==null){
            return map;
        }
        Patient p=DAOFactory.getDAO(PatientDAO.class).getByMpiId(ar.getMpiid());
        map.put("appointRecord",ar);
        map.put("patient",p);
        DoctorDAO doctorDAO=DAOFactory.getDAO(DoctorDAO.class);
        if(ar.getTelClinicFlag()!=null&&ar.getTelClinicFlag()>0&&ar.getClinicObject()!=null) {
            Integer outDocId=null;
            Integer inDocId=null;
            if (ar.getClinicObject()==1){
                outDocId=ar.getOppdoctor();
                inDocId=ar.getDoctorId();
            }else if(ar.getClinicObject()==2){
                outDocId=ar.getDoctorId();
                inDocId=ar.getOppdoctor();
            }
            if(outDocId!=null&&inDocId!=null){
                Doctor outDoc=doctorDAO.getByDoctorId(outDocId);
                Doctor inDoc=doctorDAO.getByDoctorId(inDocId);
                map.put("outDoc",outDoc);
                map.put("inDoc",inDoc);
            }
        }else{
            map.put("doctor",doctorDAO.getByDoctorId(ar.getDoctorId()));
        }
        return map;
    }

    /**
     * 查询 days 天内符合状态 status  和 机构 organIds的预约单
     *
     * @param days     几天内
     * @param status   预约单的状态
     * @param organIds 机构ID
     * @param seconds  预约已经过了的时间  单位：秒
     * @return
     */
    public List<AppointRecord> findNDaysgAppointRecords(final Integer days, final ArrayList<Integer> status, final List<Integer> organIds, final Long seconds) {
        AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
        List<AppointRecord> nDaysgAppointRecords = appointRecordDAO.findNDaysgAppointRecords(days, status, organIds, seconds);
        return nDaysgAppointRecords;

    }

    @RpcService
    public void cancel3HoursAppointRecord(final Integer days, final ArrayList<Integer> status, final List<Integer> organIds, final Long seconds) {
        List<AppointRecord> nDaysgAppointRecords = findNDaysgAppointRecords(days, status, organIds, seconds);
        logger.info("cancel3HoursAppointRecord ,days {},status :{},organs: {}, seconds : {} ,result: {}", days,
                status, JSONUtils.toString(organIds), seconds, JSONUtils.toString(nDaysgAppointRecords));

        AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
        for (AppointRecord record : nDaysgAppointRecords) {
            appointRecordDAO.cancel(record.getAppointRecordId(), "系统", "超时取消", "预约超时");
            logger.info("cance record success {}", record.getAppointRecordId());
        }

    }
}
