package eh.bus.service.transfer;

import ctd.controller.exception.ControllerException;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.constant.ServiceType;
import eh.base.dao.DoctorAccountDAO;
import eh.base.dao.DoctorDAO;
import eh.base.dao.HisServiceConfigDAO;
import eh.base.dao.OrganDAO;
import eh.bus.asyndobuss.service.AsynDoBussService;
import eh.bus.dao.AppointRecordDAO;
import eh.bus.dao.AppointSourceDAO;
import eh.bus.dao.OperationRecordsDAO;
import eh.bus.dao.TransferDAO;
import eh.bus.service.HisRemindService;
import eh.bus.service.ObtainImageInfoService;
import eh.cdr.dao.CdrOtherdocDAO;
import eh.entity.base.Doctor;
import eh.entity.bus.AppointRecord;
import eh.entity.bus.AppointSource;
import eh.entity.bus.Transfer;
import eh.entity.cdr.Otherdoc;
import eh.entity.mpi.Patient;
import eh.mpi.dao.PatientDAO;
import eh.util.SameUserMatching;
import eh.utils.DateConversion;
import eh.wxpay.constant.PayConstant;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.StatelessSession;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by luf on 2016/6/22.
 */

public class RequestTransferService {
    private static final Log log = LogFactory.getLog(RequestTransferService.class);

    //App首页业务个性化
    private AsynDoBussService asynDoBussService= AppContextHolder.getBean("asynDoBussService",AsynDoBussService.class);

    /**
     * 有号转诊服务（包含预约）-不支持云门诊付费
     *
     * @param trans          转诊信息
     * @param otherDocs      其他文档
     * @param appointRecords 预约记录列表
     * @return Boolean
     * @throws DAOException
     * @throws ControllerException
     * @throws 600:前台传入的mpiId为空    602:有未处理的转诊单,不能进行转诊
     * @author luf
     */
    @RpcService
    public Integer requestTransferClinic(final Transfer trans,
                                         final List<Otherdoc> otherDocs,
                                         final List<AppointRecord> appointRecords) throws DAOException,
            ControllerException {
        if(appointRecords.size()>=2){
            OrganDAO organDAO=DAOFactory.getDAO(OrganDAO.class);
            Integer outDoctorId=0;
            for (AppointRecord ar : appointRecords) {
                if (ar.getClinicObject() == 2) {
                    outDoctorId = ar.getDoctorId();
                    ar.setTriggerId(trans.getTriggerId());
                }
            }
            if (organDAO.getCloudClinicPriceByOrgan(outDoctorId) != 0) {//2017-04-17 15:14:16 付费机构老接口不支持
                throw new DAOException(ErrorCode.SERVICE_ERROR,
                        "由于版本过低，无法在线扫码支付，请立即更新");
            }
        } else if (appointRecords.size()==1){
            appointRecords.get(0).setTriggerId(trans.getTriggerId());
        }
        return requestTransferClinicOrderPay(trans,otherDocs,appointRecords);
    }

    /**
     * 有号转诊服务（包含预约）-支持云门诊付费
     *
     * @param trans          转诊信息
     * @param otherDocs      其他文档
     * @param appointRecords 预约记录列表
     * @return Boolean
     * @throws DAOException
     * @throws ControllerException
     * @throws 600:前台传入的mpiId为空    602:有未处理的转诊单,不能进行转诊
     * @author luf
     */
    @RpcService
    public Integer requestTransferClinicOrderPay(final Transfer trans,
                                         final List<Otherdoc> otherDocs,
                                         final List<AppointRecord> appointRecords) throws DAOException,
            ControllerException {
        log.info("转诊申请前端数据:requestTransferClinic===>trans="
                + JSONUtils.toString(trans) + ";otherDocs="
                + JSONUtils.toString(otherDocs) + ";appointRecords="
                + JSONUtils.toString(appointRecords));
        if (appointRecords.size() <= 0) {
            return null;
        }
        final AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);
        final TransferDAO transferDAO = DAOFactory.getDAO(TransferDAO.class);
        AppointRecord inAppoint = new AppointRecord();
        for (AppointRecord o : appointRecords) {
            //解决旧版本因为wx2.6患者身份证为null，而业务申请不成功
            if (org.apache.commons.lang3.StringUtils.isEmpty(o.getCertId())) {
                throw new DAOException(ErrorCode.SERVICE_ERROR,"该患者还未填写身份证信息，不能转诊");
            }
            o.setAppointRoad(5);
            appointRecordDAO.checkAppointRecordsBeforSave(o);
            boolean haveSource = checkSource(o);
            if (!haveSource) {
                throw new DAOException(609, "该号源已被约走！");
            }
            if (null != o.getClinicObject() && o.getClinicObject() == 1) {
                inAppoint = o;
            }
        }
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        if (null != inAppoint && !StringUtils.isEmpty(inAppoint.getAppointUser()) && 32 != inAppoint.getAppointUser().length()
                && null != inAppoint.getDoctorId() && 0 <= inAppoint.getDoctorId()) {
            Date start = inAppoint.getStartTime();
            Date end = inAppoint.getEndTime();
            if (null == start || start.before(new Date())) {
                throw new DAOException(DAOException.VALUE_NEEDED, "startTime is required!");
            }
            if (null == end || end.before(new Date())) {
                throw new DAOException(DAOException.VALUE_NEEDED, "endTime is required!");
            }
            Integer redoc = Integer.valueOf(inAppoint.getAppointUser());
            Integer indoc = inAppoint.getDoctorId();
            Long count = appointRecordDAO.countTimeConflict(indoc, start, end);
            if (null != count && 0 < count) {
                if (indoc.equals(redoc)) {
                    throw new DAOException(ErrorCode.SERVICE_ERROR, "您同一时间已约远程联合门诊，请更换接诊医生或选择其他号源！");
                } else {
                    String inName = doctorDAO.getNameById(indoc);
                    throw new DAOException(ErrorCode.SERVICE_ERROR, inName + "医生同一时间已约远程联合门诊，请更换接诊医生或选择其他号源！");
                }
            }
        }

        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        OperationRecordsDAO operationRecordsDAO = DAOFactory.getDAO(OperationRecordsDAO.class);
        HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);

        Integer requestOrgan = trans.getRequestOrgan();
        Integer requestDepart = trans.getRequestDepart();
        Integer requestDoctor = trans.getRequestDoctor();
        String mpiId = trans.getMpiId();
        Integer targetDoctor = trans.getTargetDoctor();
        Integer targetOrgan = trans.getTargetOrgan();
        Integer targetDepart = trans.getTargetDepart();
        boolean virtualdoctor = false;// 是否虚拟医生标志true是false否
        Integer insuFlag = trans.getInsuFlag() == null ? 0 : trans.getInsuFlag();
        Boolean accompanyFlag = trans.getAccompanyFlag() == null ? false : trans.getAccompanyFlag();

        Doctor requestDoc = doctorDAO.get(requestDoctor);
        Doctor targetDoc = doctorDAO.get(targetDoctor);

        final AppointRecord appointRecord = appointRecords.get(0);
        if (requestOrgan == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "requestOrgan is required!");
        }
        if (requestDepart == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "requestDepart is required!");
        }
        if (targetOrgan == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "targetOrgan is required!");
        }
        if (targetDepart == null) {
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "targetDepart is required!");
        }
        if (mpiId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "待转诊病人为空");
        }
        if (requestDoc == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "转诊申请医生为空");
        }
        if (targetDoc == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "转诊申请目标医生为空");
        } else {
            // 判断目标医生是否虚拟医生
            virtualdoctor = targetDoc.getVirtualDoctor() == null ? false
                    : targetDoc.getVirtualDoctor();
        }

        Patient patient = patientDAO.get(mpiId);

        //解决旧版本因为wx2.6患者身份证为null，而业务申请不成功
        if (org.apache.commons.lang3.StringUtils.isEmpty(patient.getIdcard())) {
            throw new DAOException(ErrorCode.SERVICE_ERROR,"该患者还未填写身份证信息，不能转诊");
        }

        // 蒋旭辉和王宁武转诊申请不限制
        if (!mpiId.equals("2c9081814cc5cb8a014ccf86ae3d0000")
                && !mpiId.equals("2c9081814cc3ad35014cc3e0361f0000")
                && transferDAO.getApplyingTransferRecordByMpiId(mpiId) != null) {
            throw new DAOException(602, "患者" + patient.getPatientName()
                    + "有一条未处理的转诊申请单，不能再进行转诊");
        }
        // 患者和目标医生不能为同一个人(申请医生不能把目标医生(患者身份)转诊给目标医生)
        if (SameUserMatching.patientAndDoctor(mpiId, targetDoctor)) {
            throw new DAOException(609, "患者和目标医生不能为同一个人");
        }
        if (StringUtils.isEmpty(trans.getAnswerTel())) {
            trans.setAnswerTel(patient.getMobile());// 入参没有手机号，则后台去取
        }


        Date requestTime = new Date();
        trans.setRequestTime(requestTime);
        trans.setTransferStatus(8);
        trans.setTransferResult(1);
        trans.setAgreeDoctor(targetDoctor);
        trans.setAgreeTime(DateConversion.convertFromDateToTsp(requestTime));
        trans.setConfirmOrgan(targetOrgan);
        trans.setConfirmDepart(targetDepart);
        trans.setConfirmDoctor(targetDoctor);
        trans.setConfirmClinicTime(DateConversion.convertFromDateToTsp(appointRecord.getStartTime()));
        trans.setConfirmClinicAddr(appointRecord.getConfirmClinicAddr());
        trans.setClinicPrice(appointRecord.getClinicPrice());
        trans.setSourceLevel(appointRecord.getSourceLevel());
        trans.setAppointDepartId(appointRecord.getAppointDepartId());
        trans.setAccompanyFlag(accompanyFlag);
        trans.setPayflag(1);//TODO 默认支付
        trans.setInsuRecord(0);
        trans.setIsAdd(false);// 有号转诊标识
        trans.setEvaStatus(0);//评价状态 zhangsl 2017-02-13 19:28:49
        if (accompanyFlag) {
            trans.setAccompanyPrice(DAOFactory.getDAO(OrganDAO.class)
                    .get(targetOrgan).getAccompanyPrice());
        }
        new ObtainImageInfoService(patient,requestOrgan).getImageInfo();
        //普通门诊有号预约
        if (appointRecords.size() == 1) {
            //判断经过his的预约是否在维护
            new HisRemindService().saveRemindRecordForHasSourceTransfer(appointRecord);
        }

        // 保存转诊申请信息
        Transfer tr = transferDAO.save(trans);
        if (tr.getTransferId() == null) {
            return null;
        }
//        //app端首页个性化转诊申请成功
//        asynDoBussService.fireEvent(new BussCreateEvent(tr, BussTypeConstant.TRANSFER));

        final int transferId = tr.getTransferId();
        HibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {
            public void execute(StatelessSession ss) throws DAOException {
                if (appointRecords.size() == 1) {
                    appointRecord.setTransferId(transferId);
                    appointRecord.setAppointRoad(5);
                    appointRecordDAO.addAppointRecordNew(appointRecord);
                }
                if (appointRecords.size() >= 2) {
                    for (int i = 0; i < appointRecords.size(); i++) {
                        appointRecords.get(i).setAppointRoad(5);
                        appointRecords.get(i).setTransferId(transferId);
                    }
                    appointRecordDAO
                            .addAppointRecordForCloudClinic(appointRecords);
                }
                setResult(true);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
        if (action.getResult()) {
            // 给转诊申请医生的推荐医生推荐奖励，不考虑该转诊单是否成功完成
            DoctorAccountDAO accDao = DAOFactory.getDAO(DoctorAccountDAO.class);
            accDao.recommendReward(tr.getRequestDoctor());

            // 保存日志
            operationRecordsDAO.saveOperationRecordsForTransfer(tr);

            // 保存图片
            if (otherDocs != null && otherDocs.size() > 0) {
                DAOFactory.getDAO(CdrOtherdocDAO.class).saveOtherDocList(1,
                        transferId, otherDocs);
            }
            if (!virtualdoctor) {
                // desc_2016.3.10 改为审核 完成后发送系统消息给目标医生
                // pushMessageToTargetDoc(trans, targetDoc);// 消息推送
                // TransferAndPatient tp = getTransferByID(transferId);
                // if (tp != null) {
                // sendSmsForTransferApply(tp);// 给目标医生发送短信
                // }
                // 创建群聊

                // 2017-2-7 luf:关闭转诊创建群聊入口
//                GroupDAO groupDAO = DAOFactory.getDAO(GroupDAO.class);
//                Group group = groupDAO.createTransferGroup(transferId);
//                String groupId = group.getGroupId();
//                if (!StringUtils.isEmpty(groupId)) {
//                    transferDAO.updateSessionStartTimeByTransferId(requestTime, transferId);
//                    transferDAO.updateSessionIDByTransferId(groupId, transferId);
//                }
            } else {
                transferDAO.autoConfirmTransferForVitural(tr);// 如果目标医生是虚拟医生,则将该转诊申请直接确认
            }
            if (insuFlag == 1
                    && hisServiceConfigDao.isServiceEnable(requestOrgan,
                    ServiceType.MEDFILING)) {
                log.info("发起医保备案服务");
                transferDAO.registTransfer(trans);
            }
            return transferId;
        }
        return null;
    }

    private boolean checkSource(AppointRecord o) {
        AppointSourceDAO appointSourceDAO = DAOFactory
                .getDAO(AppointSourceDAO.class);
        Integer sourceid = o.getAppointSourceId();
        if (sourceid == null || Integer.valueOf(0).equals(sourceid)) {
            return true;
        }
        AppointSource source = appointSourceDAO.get(sourceid);
        if (source == null) {
            return true;
        } else {
            return source.getSourceNum() > source.getUsedNum();
        }
    }

    /**
     * 生成待支付的特需预约单
     * @param trans
     * @param otherDocs
     * @return
     */
    @RpcService
    public Map<String, Object> requestUnPayTransfer(final Transfer trans,
                                                    final List<Otherdoc> otherDocs){
        Map<String, Object> map = new HashMap<>();
        trans.setPayflag(PayConstant.PAY_FLAG_NOT_PAY);
        // 患者转诊申请提交
        try {
            TransferDAO transferDAO = DAOFactory.getDAO(TransferDAO.class);
            Transfer transfer = transferDAO.createPatientTransferAndOtherDoc(trans, otherDocs);
            map.put("busId",transfer.getTransferId());
            //方法里抛出DAOException,不能捕获，需要给前端错误提示信息
        }catch(DAOException daoExe){
            throw daoExe;
        }catch (Exception e){
            log.error(e.getMessage());
        }
        return map;
    }
}
