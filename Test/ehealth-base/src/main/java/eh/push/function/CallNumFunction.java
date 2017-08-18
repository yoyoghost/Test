package eh.push.function;

import ctd.persistence.DAOFactory;
import ctd.util.JSONUtils;
import eh.base.constant.ServiceType;
import eh.base.dao.HisServiceConfigDAO;
import eh.bus.dao.AppointRecordDAO;
import eh.bus.service.seeadoctor.CallNumberService;
import eh.entity.bus.AppointRecord;
import eh.entity.his.push.callNum.HisCallNumReqMsg;
import eh.entity.his.push.callNum.PushRequestModel;
import eh.entity.his.push.callNum.PushResponseModel;
import eh.entity.mpi.Patient;
import eh.mpi.dao.PatientDAO;
import eh.utils.ValidateUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

//import eh.entity.his.push.callNum.PushTask;

//import java.util.concurrent.BlockingQueue;
//import java.util.concurrent.Executors;
//import java.util.concurrent.LinkedBlockingQueue;
//import java.util.concurrent.ScheduledExecutorService;


/**
 * 叫号推送服务
 * @author zxq
 *
 */
public class CallNumFunction implements Function{
    private static final Log logger = LogFactory.getLog(CallNumFunction.class);

//    private static final BlockingQueue<PushTask> queue = new LinkedBlockingQueue<>();
//    private static final ScheduledExecutorService schedule = Executors.newScheduledThreadPool(2);
//    boolean running = true;
//    public void init(){
//        running=true;
//        schedule.execute(new Runnable(){
//            @Override
//            public void run() {
//                System.out.println("started CallNumFunction "+"is running? "+running);
//                while(running){
//                    try {
//                        PushTask  task = queue.take();
//                        PushRequestModel req = (PushRequestModel)task.getRequest();
//                        PushResponseModel dao = perform(req);
//                        task.addTrycount();
//                        if(dao.isSuccess()){
//                            queue.remove(task);
//                        }else{
//                            if(task.getTrycount()>=5){
//                                queue.remove(task);
//                            }else{
//                                queue.add(task);
//                            }
//                        }
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                }
//            }
//        });
//    }
//
//    public void shutDown(){
//        schedule.shutdown();
//    }
    @Override
    public PushResponseModel perform(PushRequestModel req) {
        //获取参数
        Object data = req.getData();
        HisCallNumReqMsg callNumReqMsg = (HisCallNumReqMsg)data;
        logger.info("叫号推送参数："+ JSONUtils.toString(callNumReqMsg));
        //校验参数
        PushResponseModel response = validatePara(callNumReqMsg);
        if(!response.isSuccess()){
            return response;
        }
        //业务处理
        Patient patient = DAOFactory.getDAO(PatientDAO.class).getByIdCard(callNumReqMsg.getIdCard());
        if(patient==null){
            //发送短信
            sendSMS(callNumReqMsg);
            response.setSuccess("发送短信成功");
        }else {
         //患者在我们平台
            //没关注   发短信
            if(ValidateUtil.blankString(patient.getLoginId())){
                sendSMS(callNumReqMsg);
                response.setSuccess("发送短信成功");
            }else{
            //关注了   推送
                CallNumberService callNumberService = new CallNumberService();
                callNumberService.sendWxTemplateMessageHis(callNumReqMsg);
                response.setSuccess("发送微信推送成功");
            }
        }
        return response;
    }

    private void sendSMS( HisCallNumReqMsg callNumReqMsg){
        HisServiceConfigDAO hisServiceConfigDao = DAOFactory.getDAO(HisServiceConfigDAO.class);
        boolean f = hisServiceConfigDao.isServiceEnable(callNumReqMsg.getOrganID(), ServiceType.organSMS);
        if(!f){
            logger.info("机构不支持发送短信推送！");
            return;
        }
        CallNumberService callNumberService = new CallNumberService();
        AppointRecordDAO appointRecordDAO = DAOFactory.getDAO(AppointRecordDAO.class);

//        Organ organ = DAOFactory.getDAO(OrganDAO.class).getByOrganId(callNumReqMsg.getOrganID());
        Integer busID = 0;
        String  appointID = callNumReqMsg.getAppointId();
        if(!ValidateUtil.blankString(appointID)){
            AppointRecord ar = appointRecordDAO.getByOrganAppointIdAndOrganId(appointID, callNumReqMsg.getOrganID());
            if(ar!=null){
                busID = ar.getAppointRecordId();
            }
        }
        int callNum = callNumReqMsg.getCallNum();
        int orderNum = callNumReqMsg.getOrderNum();
        int remainNum = orderNum-callNum;
//        callNumberService.sendMsg(busID,callNumReqMsg.getMobile(), organ,
//                callNumReqMsg.getDepartName(),callNumReqMsg.getDoctorName(),String.valueOf(remainNum),String.valueOf(callNumReqMsg.getOrderNum()));
        callNumberService.sendMsg(busID, remainNum);
    }

    private PushResponseModel validatePara(HisCallNumReqMsg callNumReqMsg) {
        PushResponseModel response = new PushResponseModel();

        if (ValidateUtil.blankString(callNumReqMsg.getIdCard())) {
            response.setMsgCode("1");
            response.setMsg("身份证为空");
        } else if (ValidateUtil.blankString(callNumReqMsg.getPatientName())) {
            response.setMsgCode("2");
            response.setMsg("患者姓名为空");
        } else if (ValidateUtil.blankString(callNumReqMsg.getMobile())) {
            response.setMsgCode("3");
            response.setMsg("手机号码为空");
        } else if (ValidateUtil.blankString(callNumReqMsg.getDepartName())) {
            response.setMsgCode("4");
            response.setMsg("挂号科室为空");
        } else if (callNumReqMsg.getCallNum() == 0) {
            response.setMsgCode("6");
            response.setMsg("当前叫号结果为空");
        } /*else if (callNumReqMsg.getOrderNum() == 0) {
            dao.setMsgCode("7");
            dao.setMsg("预约序号为空");
        } */else {
            response.setSuccess("");
        }
        return response;

    }
}
