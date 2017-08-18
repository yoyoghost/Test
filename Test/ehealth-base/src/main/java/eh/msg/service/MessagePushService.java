package eh.msg.service;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import eh.base.dao.DoctorDAO;
import eh.bus.dao.ConsultDAO;
import eh.cdr.thread.RecipeBusiThreadPool;
import eh.entity.base.Doctor;
import eh.entity.bus.Consult;
import eh.entity.msg.Article;
import eh.entity.msg.SessionMessage;
import eh.entity.msg.SmsInfo;
import eh.mpi.dao.PatientDAO;
import eh.msg.dao.SessionDetailDAO;
import eh.msg.thread.PushInformationToAllDoctorsCallable;
import eh.push.SmsPushService;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


/**
 * Created by w on 2016/5/19.
 */
public class MessagePushService {
    private static final Log logger = LogFactory.getLog(MessagePushService.class);

    /**
     * 胎心判读消息推送
     * [常州调用]
     * @param userId
     * @param content
     * @param param
     * @return
     */
    @RpcService
    public void pushCardiacMsg(String userId,HashMap<String,String> param){

        DoctorDAO doctorDAO=DAOFactory.getDAO(DoctorDAO.class);
        Doctor doctor=doctorDAO.getByMobile(userId);
        if(doctor==null){
            throw new DAOException(ErrorCode.SERVICE_ERROR,"无该医生信息");
        }
        isValidPushCardiacMsgData(userId,param);

        SmsInfo smsInfo = new SmsInfo();
        smsInfo.setBusId(doctor.getDoctorId());
        smsInfo.setBusType("CardiacInterpretation");
        smsInfo.setSmsType("CardiacInterpretation");
        smsInfo.setClientId(null);
        smsInfo.setOrganId(doctor.getOrgan());
        smsInfo.setExtendValue(JSONUtils.toString(param));

        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
        smsPushService.pushMsgData2OnsExtendValue(smsInfo);
    }


    private void isValidPushCardiacMsgData(String userId,HashMap<String,String> param){
        if(StringUtils.isEmpty(userId)){
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "userId is required");
        }
        String sysMsgTitle=param.get("sysMsgTitle");
        if(StringUtils.isEmpty(sysMsgTitle)){
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "sysMsgTitle is required");
        }

        String sysMsgContent=param.get("sysMsgContent");
        if(StringUtils.isEmpty(sysMsgContent)){
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "sysMsgContent is required");
        }

        String url=param.get("url");
        if(StringUtils.isEmpty(url)){
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "url is required");
        }

        String xinGeMsgContent=param.get("xinGeMsgContent");
        if(StringUtils.isEmpty(xinGeMsgContent)){
            throw new DAOException(DAOException.VALUE_NEEDED,
                    "xinGeMsgContent is required");
        }
    }


    /**
     * @param mobile
     * @param content //消息内容
     * @param param //自定义消息内容，参见消息定义文档
     * @return
     */
    @RpcService
    public HashMap<String,Object> pushMsg(String mobile,String content,HashMap<String,Object> param){
        return MsgPushService.pushMsgToDoctor(mobile,content,param);
    }

    @RpcService
    public void pushInformationToAllDoctors(String content, String url){
        if(StringUtils.isNotEmpty(content) && StringUtils.isNotEmpty(url)){
            //自定义参数
            HashMap<String,Object> map=new HashMap<>();
            map.put("action_type","1");// 动作类型，1打开activity或app本身
            map.put("activity","INFORMATION_DETAIL");//指定模块
            HashMap<String,Object> attr=new HashMap<>(); // activity属性，只针对action_type=1的情况
            attr.put("url",url);
            map.put("aty_attr",attr);

            DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
            long count = doctorDAO.getAllDoctorNumWithoutTeams();
            float limit = 1000.0f;
            Integer limit_int = (int)limit;
            int sendNum = (int)Math.ceil(count/limit);
            List<PushInformationToAllDoctorsCallable> callables = new ArrayList<>(5);
            for(int i=0;i<sendNum;i++){
                int start = i*limit_int;
                callables.add(new PushInformationToAllDoctorsCallable(start,limit_int,map,content));
            }

            if(!callables.isEmpty()) {
                try {
                    new RecipeBusiThreadPool(callables).execute();
                } catch (InterruptedException e) {
                    logger.error("pushInformationToAllDoctors 线程池异常");
                }
            }
        }
    }

    @RpcService
    public void pushMsgForRemindConsultInTen() {
        ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
        PatientDAO patientDAO = DAOFactory.getDAO(PatientDAO.class);
        List<Consult> consults = consultDAO.findConsultsNeedRemind();

//        String msg = "您有一个电话咨询需要处理~";//推送消息
//        String title = "电话咨询提醒";
        for(Consult c:consults) {
//            String appointTime = DateConversion.getDateFormatter(c.getAppointTime(),"M月d日H:mm");
//            String patientName = patientDAO.getNameByMpiId(c.getRequestMpi());
//            //您在4月3日8：30（月+日+时+分）有一条来自xxx患者的电话咨询需要处理，请及时查看！
//            String detailMsg = "您在"+appointTime+"有一条来自"+patientName+"患者的电话咨询需要处理，请及时查看！";
            //msgPushRemindInTen(c,msg,title,detailMsg);
            Integer clientId = c.getDeviceId();
            SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
            smsPushService.pushMsgData2Ons(c.getConsultId(), c.getConsultOrgan(), "sendMsgRemindConsultInTen", "sendMsgRemindConsultInTen", clientId);
            c.setRemindFlag(true);
            consultDAO.update(c);
        }
    }

    public void msgPushRemindInTen(Consult consult, String msg,
                                                   String title, String detailMsg) {
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        Integer consultId = consult.getConsultId();
        Integer doctorId = consult.getConsultDoctor();
        Boolean teams=consult.getTeams()==null?false:consult.getTeams();
        String docTel = doctorDAO.getMobileByDoctorId(doctorId);// 咨询医生电话

        // 申请医生医生新增系统信息
        addMsgDetailRemindInTen(consultId, 1, docTel, "text", title, detailMsg, "", true);

        //推送微信推送消息
        HashMap<String,Object> msgCustom= CustomContentService.getConsultCustomContent(consultId,teams);
        MsgPushService.pushMsgToDoctor(docTel,msg,msgCustom);

    }

    /**
     * 新增系统提醒消息-添加申请方/目标方
     *
     * @param consultId
     * @param memberType
     * @param tel
     * @param msgType
     * @param title
     * @param msg
     * @param url
     * @param hasBtn
     * @author luf
     * @date 2016-5-19
     */
    public void addMsgDetailRemindInTen(int consultId, int memberType, String tel,
                                         String msgType, String title, String msg, String url, boolean hasBtn) {
        // 构建系统消息
        SessionMessage sessionMsg = new SessionMessage();
        sessionMsg.setToUserId(tel);
        sessionMsg.setCreateTime(new Timestamp(System.currentTimeMillis()));
        sessionMsg.setMsgType(msgType);

        List<Article> list = new ArrayList<Article>();

        Article art = new Article();
        art.setContent(msg);
        art.setTitle(title);
        art.setUrl(url);
        if (hasBtn) {
            art.setBussType(3);
            art.setBussId(consultId);
        }
        list.add(art);

        sessionMsg.setArticles(list);

        // 新增系统消息
        SessionDetailDAO sessionDetailDAO = DAOFactory
                .getDAO(SessionDetailDAO.class);
        sessionDetailDAO.addSysMessageByUserId(JSONUtils.toString(sessionMsg),
                memberType, "eh", tel);
    }
}
