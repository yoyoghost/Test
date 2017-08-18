package eh.bus.service.consult;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.base.constant.BussTypeConstant;
import eh.base.constant.ErrorCode;
import eh.base.dao.DoctorDAO;
import eh.bus.asyndobuss.bean.BussAcceptEvent;
import eh.bus.asyndobuss.service.AsynDoBussService;
import eh.bus.constant.ConsultConstant;
import eh.bus.dao.ConsultDAO;
import eh.entity.base.Doctor;
import eh.entity.bus.Consult;
import eh.entity.mpi.Patient;
import eh.entity.msg.Group;
import eh.mpi.dao.PatientDAO;
import eh.msg.dao.GroupDAO;
import eh.push.SmsPushService;
import eh.util.SameUserMatching;
import eh.utils.ValidateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;

/**
 * 咨询开始服务
 * Created by zhangx on 2016/5/24.
 */
public class StartConsultService {
    private static final Logger logger = LoggerFactory.getLogger(StartConsultService.class);
    private AsynDoBussService asynDoBussService= AppContextHolder.getBean("asynDoBussService",AsynDoBussService.class);

    /**
     * 将是否咨询封装成一个新的服务，用于从系统消息跳转到详情单
     * @param consultID
     * @param doctorId
     * @return
     */
    @RpcService
    public HashMap<String,Object> canAcceptConsultInfo(int consultID, int doctorId) {
        HashMap<String, Object> map = new HashMap<String, Object>();

        ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
        PatientDAO pDao = DAOFactory.getDAO(PatientDAO.class);

        //咨询单
        Consult consult = consultDAO.getById(consultID);

        //是否可接受咨询
        Boolean canAcceptConsult=canAcceptConsult(consultID, doctorId);

        //咨询会话最新的咨询单ID
        Integer newestConsultId=consultDAO.getNewestConsultId(consult);

        //申请人姓名：用于显示[XXX的咨询单]
        Patient requestPat = pDao.get(consult.getRequestMpi());
        Patient reqPat = new Patient();
        reqPat.setPatientName(requestPat.getPatientName());
        reqPat.setIdcard(requestPat.getIdcard());

        map.put("canAcceptConsult", canAcceptConsult);
        map.put("consult", consult);
        map.put("requestPatient", reqPat);
        if (null != newestConsultId) {
            map.put("newestConsultId", newestConsultId);
        }

        return map;
    }


    /**
     * 判断一个咨询单能不能被接收
     *
     * @param consultID
     * @param doctorId
     * @return
     */
    @RpcService
    public Boolean canAcceptConsult(int consultID, int doctorId) {
        ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);

        Boolean canAcceptFlag = true;

        Consult consult = consultDAO.getById(consultID);
        Integer status = consult.getConsultStatus();
        Integer detailStatus = consult.getStatus();

        Doctor doc = doctorDAO.get(consult.getConsultDoctor());
        Boolean docTeams = doc.getTeams() == null ? false : doc.getTeams();//咨询医生是否为团队医生:null的话，默认为个人医生
        Boolean teams = consult.getTeams() == null ? docTeams : consult.getTeams();//咨询单中的团队咨询单为null，则以目标医生的团队标记为主


        if (status == 9) {
            if ((ConsultConstant.CONSULT_TYPE_GRAPHIC .equals( consult.getRequestMode())
                    || ConsultConstant.CONSULT_TYPE_RECIPE .equals( consult.getRequestMode())
                    || ConsultConstant.CONSULT_TYPE_PROFESSOR .equals(consult.getRequestMode())) && teams && detailStatus!=null && detailStatus.equals(8)) {
//                logger.error("咨询开始服务canAcceptConsult==>抱歉，患者已取消该图文咨询申请");
                throw new DAOException(ErrorCode.SERVICE_ERROR, "抱歉，患者已取消该图文咨询申请");
            }
//            logger.error("咨询开始服务canAcceptConsult==>抱歉，该咨询单已取消");
            throw new DAOException(609, "抱歉，该咨询单已取消");
        } else if (status == 1) {

            if (consult.getExeDoctor() == doctorId) {
                logger.error("重复开始咨询");
                canAcceptFlag = false;
            } else if (consult.getGroupMode() != null && consult.getGroupMode().equals(1)) {
                // 2016-12-16 luf：非抢单团队
                throw new DAOException(608, "晚了一步，已被其他成员接收！不过你也可以参与回复~");
            } else {
//                logger.error("咨询开始服务canAcceptConsult==>抱歉，您团队内已有其他医生提前接收该图文咨询单");
                throw new DAOException(609, "抱歉，您团队内已有其他医生提前接收该图文咨询单");
            }
        }

        List<Consult> list = consultDAO.findApplyingConsultByPatientsAndDoctorAndRequestMode(consult.getRequestMpi(), doctorId, consult.getRequestMode());
        if(!consult.getRequestMode().equals(ConsultConstant.CONSULT_TYPE_POHONE) && ValidateUtil.notBlankList(list)){
            for(Consult cc :list){
                if(cc.getConsultId()!=consultID && cc.getConsultStatus()!=ConsultConstant.CONSULT_STATUS_PENDING){
                    logger.info("canRequestConsult exists not ended consult, requestMode[{}]", consult.getRequestMode());
                    throw new DAOException(ErrorCode.SERVICE_ERROR, "抱歉，您正在和该患者对话中，暂时不能接收哦");
                }
            }
        }

        //申请人/患者可以向所在团队发起图文咨询,申请人/患者与接收医生不能为同一个人；
        if ((ConsultConstant.CONSULT_TYPE_GRAPHIC .equals(consult.getRequestMode())
                || ConsultConstant.CONSULT_TYPE_RECIPE .equals(consult.getRequestMode())
                || ConsultConstant.CONSULT_TYPE_PROFESSOR .equals(consult.getRequestMode())) && teams) {
            String requestMpiId = consult.getRequestMpi();
            String mpiId = consult.getMpiid();
            HashMap<String, Boolean> map = SameUserMatching.patientsAndDoctor(mpiId, requestMpiId, doctorId);
            Boolean patSameWithDoc = map.get("patSameWithDoc");//患者是否和医生为同一个人,true为同一个人
            Boolean reqPatSameWithDoc = map.get("reqPatSameWithDoc");//判断申请人是否和医生为同一个人,true为同一个人
            if (reqPatSameWithDoc) {
                canAcceptFlag = false;
//                logger.error("申请人requestMpi[" + requestMpiId + "]与目标医生ConsultDoctor[" + doctorId + "]为同一个人");
                throw new DAOException(ErrorCode.SERVICE_ERROR, "申请人与接收医生不能为同一个人");
            }
            if (patSameWithDoc) {
                canAcceptFlag = false;
//                logger.error("患者mpiId[" + mpiId + "]与目标医生ConsultDoctor[" + doctorId + "]为同一个人");
                throw new DAOException(ErrorCode.SERVICE_ERROR, "患者与接收医生不能为同一个人");
            }
        }


        return canAcceptFlag;
    }

    /**
     * 开始咨询服务
     *
     * @param consultID 咨询单ID
     * @param exeDoctor 接收医生
     * @param exeDepart 接收科室
     * @param exeOrgan  接收机构
     * @return
     */
    @RpcService
    public boolean startConsult(final int consultID, final int exeDoctor,
                                final int exeDepart, final int exeOrgan) {
        logger.info("咨询开始,consultID:" + consultID + ",exeDoctor:" + exeDoctor
                + ",exeDepart:" + exeDepart + ",exeOrgan:" + exeOrgan);

        //判断能否开始咨询
        if (!canAcceptConsult(consultID, exeDoctor)) {
            return false;
        }

        ConsultDAO consultDAO = DAOFactory.getDAO(ConsultDAO.class);

        Consult consult = consultDAO.getById(consultID);
        Boolean b = false;
        if (consult.getConsultStatus() == 0) {
            Integer num = consultDAO.updateConsultForStart(consultID, exeDoctor, exeDepart, exeOrgan);
            b = true;
        } else if (consult.getConsultStatus() == 1) {
            if (consult.getExeDoctor().equals(exeDoctor)) {
                b = true;
            } else {
                b = false;
            }
        } else {
            b = false;
        }
        if (!b) {
            return b;
        }

        // 目标医生是团队医生,且是图文咨询，创建群聊,并以管理员身份发送一条数据
        Boolean teams = consult.getTeams();
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        if (null == teams) {
            Boolean docTeam = doctorDAO.get(consult.getConsultDoctor()).getTeams();
            teams = docTeam == null ? false : docTeam;
        }
        Integer clinicId = consult.getConsultId();
        if ((ConsultConstant.CONSULT_TYPE_GRAPHIC .equals(consult.getRequestMode())
                || ConsultConstant.CONSULT_TYPE_RECIPE .equals(consult.getRequestMode())
                || ConsultConstant.CONSULT_TYPE_PROFESSOR .equals(consult.getRequestMode())) && teams) {
            GroupDAO groupDAO = DAOFactory.getDAO(GroupDAO.class);
            Group group = groupDAO.createConsultGroup(clinicId);
            if (!StringUtils.isEmpty(group.getGroupId())) {
                consultDAO.updateSessionStartTimeByConsultId(consult.getRequestTime(),
                        clinicId);
                consultDAO.updateSessionIDByConsultId(group.getGroupId(), clinicId);
                // 插入患者病情描述/系统消息/默认消息到医患消息表
                consult.setSessionID(group.getGroupId());
                ConsultMessageService msgService = new ConsultMessageService();
                msgService.defaultHandleWhenPatientStartConsult(consult);
                asynDoBussService.fireEvent(new BussAcceptEvent(consult.getConsultId(), BussTypeConstant.CONSULT, exeDoctor));
            }

            //图文咨询被团队医生接收给患者发送推送(测试的时候发现，到这个地方，consult还是未更新的状态,因此数据库，重新查询一遍)
            //pushWxMsgToPatWithStartConsult(consultDAO.getById(consultID));
            consult = consultDAO.getById(consultID);
            Integer clientId = consult.getDeviceId();
            SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService",SmsPushService.class);
            smsPushService.pushMsgData2Ons(consultID,consult.getConsultOrgan(),"PushPatStartConsult","PushPatStartConsult",clientId);
            logger.info("图文咨询被团队医生接收给患者发送推送");
        }
        return b;
    }
}
