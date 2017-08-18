package eh.msg.service;

import com.alibaba.fastjson.JSONObject;
import ctd.persistence.DAOFactory;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.entity.msg.Article;
import eh.entity.msg.SessionMessage;
import eh.msg.dao.SessionDetailDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by zhangx on 2016/5/27.
 * 针对各个业务，封装不同的方法
 */
public class SessionDetailService {
    private static final Logger log = LoggerFactory.getLogger(SessionDetailService.class);

    /**
     * @param busId      业务id (如果没有业务Id,传null)
     * @param busType    业务类型 (1转诊；2会诊；3咨询；4预约;5收入;6业务设置;7检查)
     * @param memberType 接收者类型（1医生 2患者）
     * @param tel        推送系统消息手机号
     * @param msgType    消息类别
     * @param title      显示的标题
     * @param msg        显示的消息内容
     * @param url        链接
     * @param hasBtn     是否有按钮可以跳转到详情页(true:有;false:没有)
     * @return void
     * @function 新增业务相关系统提醒消息
     * @author zhangx
     * @date 2015-12-9
     */
//    public void addMsgDetail(Integer busId, Integer busType, int memberType, String tel,
//                             String msgType, String title, String msg, String url, Boolean teams, boolean hasBtn) {
//
//
//        // 构建系统消息
//        SessionMessage sessionMsg = new SessionMessage();
//        sessionMsg.setToUserId(tel);
//        sessionMsg.setCreateTime(new Timestamp(System.currentTimeMillis()));
//        sessionMsg.setMsgType(msgType);
//
//        List<Article> list = new ArrayList<Article>();
//
//        Article art = new Article();
//        art.setContent(msg);
//        art.setTitle(title);
//        art.setUrl(url);
//        if (null != teams) {
//            art.setTeams(teams);
//        }
//        if (hasBtn) {
//            art.setBussType(busType);
//
//            if (busId != null) {
//                art.setBussId(busId);
//            }
//
//        }
//        list.add(art);
//        sessionMsg.setArticles(list);
//
//        // 新增系统消息
//        SessionDetailDAO sessionDetailDAO = DAOFactory
//                .getDAO(SessionDetailDAO.class);
//        sessionDetailDAO.addSysMessageByUserId(JSONUtils.toString(sessionMsg),
//                memberType, "eh", tel);
//    }

    /**
     * @param memberType 接收者类型（1医生 2患者）
     * @param tel        推送系统消息手机号
     * @param msgType    消息类别
     * @param title      显示的标题
     * @param msg        显示的消息内容
     * @param url        链接
     * @param teams      是否团队
     * @param doctorId   个人医生id/团队医生id
     * @param hasBtn     是否有按钮可以跳转到详情页(true:有;false:没有)
     * @return void
     * @function 只提供给[推荐开通(个人医生)/推荐开通(团队医生)]系统提醒消息
     * @author zhangx
     * @date 2015-12-9
     */
    public void addRecommendOpenMsgDetail(int memberType, String tel,
                                          String msgType, String title, String msg, String url, Boolean teams, Integer doctorId, boolean hasBtn) {

        int busType = 6;//业务设置(1转诊；2会诊；3咨询；4预约;5收入;6业务设置;7检查)
        this.addMsgDetailAllBuss(null, busType, memberType, tel, msgType, title, msg, url, teams, hasBtn, null, doctorId, null);
    }

    /**
     * @param memberType 接收者类型（1医生 2患者）
     * @param tel        推送系统消息手机号
     * @param msgType    消息类别
     * @param title      显示的标题
     * @param msg        显示的消息内容
     * @param url        链接
     * @param hasBtn     是否有按钮可以跳转到详情页(true:有;false:没有)
     * @return void
     * @function 新增系统提醒消息(业务不相关)
     * @author zhangx
     * @date 2015-12-9
     */
    public void addSysMsg(int memberType, String tel,
                          String msgType, String title, String msg, String url, boolean hasBtn) {
        this.addMsgDetailAllBuss(null, null, memberType, tel, msgType, title, msg, url, null, hasBtn, null, null, null);
    }

    /**
     * @param tel   推送系统消息手机号
     * @param title 显示的标题
     * @param msg   显示的消息内容
     * @return void
     * @function 新增医生用户系统文本提醒消息(业务不相关, 没有点击事件)
     * @author zhangx
     * @date 2016-6-21
     */
    public void addSysTextMsgNoBtnToDoc(String tel, String title, String msg) {
        this.addMsgDetailAllBuss(null, null, 1, tel, "text", title, msg, "", null, false, null, null, null);
    }

    public void addSysTextMsgBussToPat(Integer busId, Integer busType, String tel,
                                       String title, String msg, Boolean teams, Boolean hasBtn) {
        this.addMsgDetailAllBuss(busId, busType, 2, tel, "text", title, msg, "", teams, hasBtn, null, null, null);
    }

    private void addSysTextMsgBussToDoc(Integer busId, Integer busType, String tel,
                                        String title, String msg, Boolean teams, Boolean hasBtn, Integer flag, Integer meetResultId) {
        this.addMsgDetailAllBuss(busId, busType, 1, tel, "text", title, msg, "", teams, hasBtn, flag, null, meetResultId);
    }

    private void addSysTextMsgAllBussToReqDoc(Integer busId, Integer busType, String tel,
                                              String title, String msg, Boolean teams, Boolean hasBtn) {
        this.addSysTextMsgBussToDoc(busId, busType, tel, title, msg, teams, hasBtn, 0, null);
    }

    private void addSysTextMsgAllBussToTarDoc(Integer busId, Integer busType, String tel,
                                              String title, String msg, Boolean teams, Boolean hasBtn, Integer meetResultId) {
        this.addSysTextMsgBussToDoc(busId, busType, tel, title, msg, teams, hasBtn, 1, meetResultId);
    }

    public void addSysTextMsgTransToReceiveDoc(Integer busId, String tel,
                                               String title, String msg, Boolean teams, Boolean hasBtn) {
        this.addSysTextMsgBussToDoc(busId, 1, tel, title, msg, teams, hasBtn, 5, null);
    }

    public void addSysTextMsgPatientTransToTarDoc(Integer busId, String tel,
                                                  String title, String msg, Boolean teams, Boolean hasBtn) {
        this.addSysTextMsgBussToDoc(busId, 1, tel, title, msg, teams, hasBtn, 4, null);
    }

    public void addSysTextMsgTransferToReqDoc(Integer busId, String tel,
                                              String title, String msg, Boolean teams, Boolean hasBtn) {
        this.addSysTextMsgAllBussToReqDoc(busId, 1, tel, title, msg, teams, hasBtn);
    }

    public void addSysTextMsgMeetClinicToReqDoc(Integer busId, String tel,
                                                String title, String msg, Boolean teams, Boolean hasBtn) {
        this.addSysTextMsgAllBussToReqDoc(busId, 2, tel, title, msg, teams, hasBtn);
    }

    public void addSysTextMsgConsultToReqDoc(Integer busId, String tel,
                                             String title, String msg, Boolean teams, Boolean hasBtn) {
        this.addSysTextMsgAllBussToReqDoc(busId, 3, tel, title, msg, teams, hasBtn);
    }

    public void addSysTextMsgAppointToReqDoc(Integer busId, String tel,
                                             String title, String msg, Boolean teams, Boolean hasBtn) {
        this.addSysTextMsgAllBussToReqDoc(busId, 4, tel, title, msg, teams, hasBtn);
    }

    public void addSysTextMsgSummaryToReqDoc(Integer busId, String tel,
                                             String title, String msg, Boolean teams, Boolean hasBtn) {
        this.addSysTextMsgAllBussToReqDoc(busId, 14, tel, title, msg, teams, hasBtn);
    }

    public void addSysTextMsgCheckToReqDoc(Integer busId, String tel,
                                           String title, String msg, Boolean teams, Boolean hasBtn) {
        this.addSysTextMsgAllBussToReqDoc(busId, 7, tel, title, msg, teams, hasBtn);
    }

    public void addSysTextMsgTransferToTarDoc(Integer busId, String tel,
                                              String title, String msg, Boolean teams, Boolean hasBtn) {
        this.addSysTextMsgAllBussToTarDoc(busId, 1, tel, title, msg, teams, hasBtn, null);
    }

    public void addSysTextMsgMeetClinicToTarDoc(Integer busId, String tel,
                                                String title, String msg, Boolean teams, Boolean hasBtn, int meetResultId) {
        this.addSysTextMsgAllBussToTarDoc(busId, 2, tel, title, msg, teams, hasBtn, meetResultId);
    }

    public void addSysTextMsgConsultToTarDoc(Integer busId, String tel,
                                             String title, String msg, Boolean teams, Boolean hasBtn) {
        this.addSysTextMsgAllBussToTarDoc(busId, 3, tel, title, msg, teams, hasBtn, null);
    }

    public void addSysTextMsgAppointToTarDoc(Integer busId, String tel,
                                             String title, String msg, Boolean teams, Boolean hasBtn) {
        this.addSysTextMsgAllBussToTarDoc(busId, 4, tel, title, msg, teams, hasBtn, null);
    }

    public void addSysTextMsgSignPatToTarDoc(Integer busId, String tel,
                                             String title, String msg, Boolean teams, Boolean hasBtn) {
        SessionMessage sessionMsg = new SessionMessage();
        sessionMsg.setToUserId(tel);
        sessionMsg.setCreateTime(new Timestamp(System.currentTimeMillis()));
        sessionMsg.setMsgType("text");

        List<Article> list = new ArrayList<Article>();

        Article art = new Article();
        art.setContent(msg);
        art.setTitle(title);
        art.setUrl("");
        art.setDoctorId(null);
        if (null != teams) {
            art.setTeams(teams);
        }
        if (hasBtn) {
            art.setBussType(11);

            if (busId != null) {
                art.setBussId(busId);
            }

        }
        art.setFlag(1);
        art.setMeetResultId(null);
        list.add(art);
        sessionMsg.setArticles(list);
        SessionDetailDAO sessionDetailDAO = DAOFactory
                .getDAO(SessionDetailDAO.class);
        sessionDetailDAO.addSysMessageWithPublisher(9,JSONUtils.toString(sessionMsg),1,"eh", tel);
    }

    /**
     * 发送医生资讯群发记录消息
     * @param busId
     * @param tel
     * @param title
     * @param msg
     * @param teams
     * @param hasBtn
     */
    public void addSysTextMsgMassToTarDoc(Integer busId, String tel,
                                             String title, String msg, Boolean teams, Boolean hasBtn) {
        SessionMessage sessionMsg = new SessionMessage();
        sessionMsg.setToUserId(tel);
        sessionMsg.setCreateTime(new Timestamp(System.currentTimeMillis()));
        sessionMsg.setMsgType("text");

        List<Article> list = new ArrayList<>(5);

        Article art = new Article();
        art.setContent(msg);
        art.setTitle(title);
        art.setUrl("");
        art.setDoctorId(null);
        if (null != teams) {
            art.setTeams(teams);
        }
        if (hasBtn) {
            art.setBussType(SystemMsgConstant.SYSTEM_MSG_BUS_TYPE_MASS);

            if (busId != null) {
                art.setBussId(busId);
            }

        }
        art.setFlag(1);
        art.setMeetResultId(null);
        list.add(art);
        sessionMsg.setArticles(list);
        SessionDetailDAO sessionDetailDAO = DAOFactory
                .getDAO(SessionDetailDAO.class);
        sessionDetailDAO.addSysMessageWithPublisher(SystemMsgConstant.SYSTEM_MSG_PUBLISH_TYPE_MASS,JSONUtils.toString(sessionMsg),1,"eh", tel);
    }

    public void addSysTextMsgFollowToDoc(Integer busId, String tel,
                                              String title, String msg) {
        this.addMsgDetailAllBuss(busId, 19, 1, tel, "text", title, msg, "", null, false, null, null, null);
    }

    /**
     * @param busId      业务id (如果没有业务Id,传null)
     * @param busType    业务类型 (1转诊；2会诊；3咨询；4预约;5收入;6业务设置;7检查;11签约;19随访)
     * @param memberType 接收者类型（1医生 2患者）
     * @param tel        推送系统消息手机号
     * @param msgType    消息类别
     * @param title      显示的标题
     * @param msg        显示的消息内容
     * @param url        链接
     * @param hasBtn     是否有按钮可以跳转到详情页(true:有;false:没有)
     * @return void
     * @function 新增业务相关系统提醒消息
     * @author zhangx
     * @date 2015-12-9
     */
    private void addMsgDetailAllBuss(Integer busId, Integer busType, int memberType, String tel,
                                     String msgType, String title, String msg, String url, Boolean teams, Boolean hasBtn, Integer flag, Integer doctorId, Integer meetResultId) {
        this.addMsgDetailAllBussAllReceiver(busId, busType, memberType, tel, msgType, title, msg, url, teams, hasBtn, flag, doctorId, 0, meetResultId);
    }

    /**
     * 新增系统提醒消息-添加申请方/目标方
     *
     * @param transferid
     * @param memberType
     * @param tel
     * @param msgType
     * @param title
     * @param msg
     * @param url
     * @param hasBtn
     * @param flag       跳转标志-0申请1目标
     * @author luf
     * @date 2016-5-19
     */
    public void addMsgDetailRequestOrNot(int transferid, int memberType, String tel,
                                         String msgType, String title, String msg, String url, boolean hasBtn, int flag) {
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
        art.setFlag(flag);
        if (hasBtn) {
            art.setBussType(1);
            art.setBussId(transferid);
        }
        list.add(art);

        sessionMsg.setArticles(list);

        // 新增系统消息
        SessionDetailDAO sessionDetailDAO = DAOFactory
                .getDAO(SessionDetailDAO.class);
        sessionDetailDAO.addSysMessageByUserId(JSONUtils.toString(sessionMsg),
                memberType, "eh", tel);
    }

    /**
     * @param busId      业务id (如果没有业务Id,传null)
     * @param busType    业务类型 (1转诊；2会诊；3咨询；4预约;5收入;6业务设置;7检查;10处方;11签约;19随访)
     * @param memberType 接收者类型（1医生 2患者）
     * @param tel        推送系统消息手机号
     * @param msgType    消息类别
     * @param title      显示的标题
     * @param msg        显示的消息内容
     * @param url        链接
     * @param hasBtn     是否有按钮可以跳转到详情页(true:有;false:没有)
     * @return void
     * @function 新增业务相关系统提醒消息
     * @author zhangx
     * @date 2015-12-9
     */
    private void addMsgDetailAllBussForPC(Integer busId, Integer busType, int memberType, String tel,
                                          String msgType, String title, String msg, String url, Boolean teams, Boolean hasBtn, Integer flag, Integer doctorId, Integer meetResultId) {
        this.addMsgDetailAllBussAllReceiver(busId, busType, memberType, tel, msgType, title, msg, url, teams, hasBtn, flag, doctorId, 2, meetResultId);
    }

    private void addSysTextMsgBussToDocForPC(Integer busId, Integer busType, String tel,
                                             String title, String msg, Boolean teams, Boolean hasBtn, Integer flag, Integer meetRecultId) {
        this.addMsgDetailAllBussForPC(busId, busType, 1, tel, "text", title, msg, "", teams, hasBtn, flag, null, meetRecultId);
    }

    private void addSysTextMsgAllBussToTarDocForPC(Integer busId, Integer busType, String tel,
                                                   String title, String msg, Boolean teams, Boolean hasBtn, Integer meetResultId) {
        this.addSysTextMsgBussToDocForPC(busId, busType, tel, title, msg, teams, hasBtn, 1, meetResultId);
    }

    public void addSysTextMsgRecipeToTarDocForPC(Integer busId, String tel,
                                                 String title, String msg, Boolean teams, Boolean hasBtn) {
        this.addSysTextMsgAllBussToTarDocForPC(busId, 10, tel, title, msg, teams, hasBtn, null);
    }

    /**
     *
     * @param busId
     * @param tel
     * @param title
     * @param msg
     * @param url
     * @param teams
     * @param doctorId
     */
    public void addSysTextMsgToAppDocWithDetail(Integer busId,  String tel, String title, String msg, String url, Boolean teams, Integer doctorId, String mpiId, boolean doctorAttentionPatient){
        try {
            // 构建系统消息
            SessionMessage sessionMsg = new SessionMessage();
            sessionMsg.setToUserId(tel);
            sessionMsg.setCreateTime(new Timestamp(System.currentTimeMillis()));
            sessionMsg.setMsgType(SystemMsgConstant.SYSTEM_MSG_TYPE_TEXT);
            List<Article> list = new ArrayList<Article>();
            Article art = new Article();
            art.setContent(msg);
            art.setTitle(title);
            art.setUrl(url);
            art.setDoctorId(doctorId);
            art.setMpiId(mpiId);
            art.setPayAttentionToPatient(doctorAttentionPatient);
            art.setBussType(SystemMsgConstant.SYSTEM_MSG_BUS_TYPE_PATIENT_ATTENTION_DOCTOR);
            if (null != teams) {
                art.setTeams(teams);
            }
            if (busId != null) {
                art.setBussId(busId);
            }
            art.setFlag(SystemMsgConstant.SYSTEM_MSG_FLAG_TARGET);
            art.setMeetResultId(null);
            list.add(art);
            sessionMsg.setArticles(list);
            this.addSessionMessageDetail(sessionMsg, SystemMsgConstant.SYSTEM_MSG_RECIEVER_DEVICE_TYPE_APP, SystemMsgConstant.SYSTEM_MSG_RECIEVER_TYPE_DOCTOR);
        }catch (Exception e){
            log.error("addSysTextMsgToAppDocWithDetail error, errorMessage[{}], stackTrace[{}], requestParameters> busId[{}], " +
                    "tel[{}], title[{}], msg[{}], url[{}], teams[{}], doctorId[{}], mpiId[{}], doctorAttentionPatient[{}]",
                    e.getMessage(), JSONObject.toJSONString(e.getStackTrace()), busId, tel, title, msg, url, teams, doctorId,
                    mpiId, doctorAttentionPatient);
        }
    }

    /**
     * zhongzx
     * 发送系统消息详情给医生
     * @param busId
     * @param tel
     * @param title
     * @param msg
     * @param url
     * @param teams
     * @param doctorId
     */
    public void addSysTextMsgDocWithDetail(Integer busId, Integer terminal, String tel, String title, String msg, String url, Boolean teams, Integer doctorId, String mpiId, boolean doctorAttentionPatient){
        try {
            // 构建系统消息
            SessionMessage sessionMsg = new SessionMessage();
            sessionMsg.setToUserId(tel);
            sessionMsg.setCreateTime(new Timestamp(System.currentTimeMillis()));
            sessionMsg.setMsgType(SystemMsgConstant.SYSTEM_MSG_TYPE_TEXT);
            List<Article> list = new ArrayList<Article>();
            Article art = new Article();
            art.setContent(msg);
            art.setTitle(title);
            art.setUrl(url);
            art.setDoctorId(doctorId);
            art.setMpiId(mpiId);
            art.setPayAttentionToPatient(doctorAttentionPatient);
            art.setBussType(SystemMsgConstant.SYSTEM_MSG_BUS_TYPE_PATIENT_ATTENTION_DOCTOR);
            if (null != teams) {
                art.setTeams(teams);
            }
            if (busId != null) {
                art.setBussId(busId);
            }
            art.setFlag(SystemMsgConstant.SYSTEM_MSG_FLAG_TARGET);
            art.setMeetResultId(null);
            list.add(art);
            sessionMsg.setArticles(list);
            this.addSessionMessageDetail(sessionMsg, terminal, SystemMsgConstant.SYSTEM_MSG_RECIEVER_TYPE_DOCTOR);
        }catch (Exception e){
            log.error("addSysTextMsgToAppDocWithDetail error, errorMessage[{}], stackTrace[{}], requestParameters> busId[{}], " +
                            "terminal[{}], tel[{}], title[{}], msg[{}], url[{}], teams[{}], doctorId[{}], mpiId[{}], doctorAttentionPatient[{}]",
                    e.getMessage(), JSONObject.toJSONString(e.getStackTrace()), busId, terminal, tel, title, msg, url, teams, doctorId,
                    mpiId, doctorAttentionPatient);
        }
    }

    /**
     * 新增系统消息（区分接收端和跳转标志）
     *
     * @param busId       业务id (如果没有业务Id,传null)
     * @param busType     业务类型 (1转诊；2会诊；3咨询；4预约;5收入;6业务设置;7检查;11签约;12患者关注医生;19随访)
     * @param memberType  接收者类型（1医生 2患者）
     * @param tel         推送系统消息手机号
     * @param msgType     消息类别
     * @param title       显示的标题
     * @param msg         显示的消息内容
     * @param url         链接
     * @param teams       团队标志
     * @param hasBtn      是否有按钮可以跳转到详情页(true:有;false:没有)
     * @param flag        跳转标志-0申请1目标2今日目标医生3明日申请医生4特需目标5转诊接收医生
     * @param doctorId    今日/明日就诊医生
     * @param receiveType 接收类型--0所有端 1原生app端 2pc端
     * @date 2016-7-18
     * @author luf
     */
    private void addMsgDetailAllBussAllReceiver(Integer busId, Integer busType, int memberType, String tel,
                                                String msgType, String title, String msg, String url, Boolean teams,
                                                Boolean hasBtn, Integer flag, Integer doctorId, int receiveType, Integer meetResultId) {
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
        art.setDoctorId(doctorId);
        if (null != teams) {
            art.setTeams(teams);
        }
        if (hasBtn) {
            art.setBussType(busType);

            if (busId != null) {
                art.setBussId(busId);
            }

        }
        art.setFlag(flag);
        art.setMeetResultId(meetResultId);
        list.add(art);
        sessionMsg.setArticles(list);
        this.addSessionMessageDetail(sessionMsg, receiveType, memberType);
    }

    private void addSessionMessageDetail(SessionMessage sessionMsg, int receiveType, int memberType){
        if(sessionMsg==null){
            log.error("addSessionMessageDetail error sessionMsg is null!");
            return;
        }
        // 新增系统消息
        SessionDetailDAO sessionDetailDAO = DAOFactory
                .getDAO(SessionDetailDAO.class);
        switch (receiveType) {
            case 0:
                sessionDetailDAO.addSysMessageByUserId(JSONUtils.toString(sessionMsg), memberType, "eh", sessionMsg.getToUserId());
                break;
            case 1:
                sessionDetailDAO.addNativeSysMessageByUserId(JSONUtils.toString(sessionMsg), memberType, "eh", sessionMsg.getToUserId());
                break;
            case 2:
                sessionDetailDAO.addPcSysMessageByUserId(JSONUtils.toString(sessionMsg), memberType, "eh", sessionMsg.getToUserId());
                break;
        }
    }

    @RpcService
    public void sendTempleteMsg(){

    }
}
