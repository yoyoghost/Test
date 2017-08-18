package eh.bus.service.information;

import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.base.constant.PagingInfo;
import eh.base.constant.SystemConstant;
import eh.base.dao.DoctorDAO;
import eh.bus.constant.MsgTypeEnum;
import eh.entity.base.Doctor;
import eh.entity.information.InfoCollection;
import eh.entity.mpi.FollowChat;
import eh.entity.msg.MassRoot;
import eh.entity.msg.SmsInfo;
import eh.mpi.constant.FollowConstant;
import eh.mpi.dao.FamilyMemberDAO;
import eh.mpi.dao.RelationDoctorDAO;
import eh.mpi.service.follow.FollowChatService;
import eh.msg.dao.InfoCollectionDAO;
import eh.msg.dao.MassRootDAO;
import eh.msg.dao.SessionMemberDAO;
import eh.msg.service.SystemMsgConstant;
import eh.push.SmsPushService;
import eh.utils.DateConversion;
import eh.utils.MapValueUtil;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.Assert;

import java.util.*;

/**
 * 资讯相关
 * company: ngarihealth
 * author: 0184/yu_yun
 * date:2016/10/11.
 */
public class InformationService {

    private static final Log logger = LogFactory.getLog(InformationService.class);

    private static final String SHARE_INFORMATION = "ShareInformation";

    /**
     * 分享资讯
     * @param doctorId 医生ID
     * @param msg 分享信息
     * @param allFlag true:全选
     * @param type 分组  0:全部患者 1:签约患者 2:未添加标签患者 3:指定标签患者
     * @param labelName 标签名
     * @param checkList 当 allFlag=false时，使用该字段选定的患者
     * @param unCheckList 当 allFlag=true时，该字段表示未选中的患者
     * @param info 资讯其他信息 wxUrl:微信url，msgUrl:短信url,otherUrl:其他url, infoId:文章ID, imgUrl:缩略图, desc:文章摘要, title:标题, golink:外链地址
     *                        isvideo:(0：图文资讯 1：视频资讯 2：外链资讯)
     */
    @RpcService
    public void shareInformation(Integer doctorId, String msg, boolean allFlag,
                                 int type, String labelName, List<String> checkList,
                                 List<String> unCheckList, Map<String,String> info) {
        Assert.notNull(doctorId, "shareInformation doctorId is null");
        Assert.hasLength(msg, "shareInformation msg is null");
        Assert.notNull(info, "shareInformation info is null");

        RelationDoctorDAO relationDoctorDAO = DAOFactory.getDAO(RelationDoctorDAO.class);
        DoctorDAO doctorDAO = DAOFactory.getDAO(DoctorDAO.class);
        MassRootDAO massRootDAO = DAOFactory.getDAO(MassRootDAO.class);
        FamilyMemberDAO familyMemberDAO = DAOFactory.getDAO(FamilyMemberDAO.class);

        List<String> sendPatientList = new ArrayList<>(5);
        Set<String> sendPatientIds = new HashSet<>();
        if(allFlag){
            switch (type){
                case 0:
                    sendPatientList = relationDoctorDAO.findByDoctorId(doctorId);
                    break;
                case 1:
                    sendPatientList = relationDoctorDAO.findSignPatientByDoctorId(doctorId);
                    break;
                case 2:
                    sendPatientList = relationDoctorDAO.findNoGroupByDoctorId(doctorId);
                    break;
                case 3:
                    Assert.hasLength(labelName, "shareInformation labelName is null");

                    sendPatientList = relationDoctorDAO.findByDoctorIdAndLabel(doctorId,labelName);
                    break;
                default:
                    break;
            }

            if (CollectionUtils.isNotEmpty(sendPatientList) && CollectionUtils.isNotEmpty(unCheckList)) {
                sendPatientList.removeAll(unCheckList);
            }
        }else{
            if(CollectionUtils.isNotEmpty(checkList)){
                sendPatientList = checkList;
            }
        }

        if(CollectionUtils.isEmpty(sendPatientList)){
            logger.error("shareInformation send patient is null");
            return;
        }

        int recordCount = sendPatientList.size();

        Integer isvideo = MapValueUtil.getInteger(info,"isvideo");
        String golink = MapValueUtil.getString(info,"golink");

        //是否是浙大邵逸夫医院日间手术出院短信回访问卷 是-不分享给家庭成员
        boolean isDayTimeOperInfo = (Integer.valueOf(2).equals(isvideo) && StringUtils.isNotEmpty(golink));
        if(!isDayTimeOperInfo) {
            //寻找将患者加为家庭成员的人
            List<String> familyMpiList = familyMemberDAO.findMpiidByMemberMpiList(sendPatientList);
            if (CollectionUtils.isNotEmpty(familyMpiList)) {
                sendPatientList.addAll(familyMpiList);
            }
        }
        sendPatientIds.addAll(sendPatientList);

        Doctor doctor = doctorDAO.getByDoctorId(doctorId);
        String docName = doctor.getName();
        info.put("name",docName);
        Integer qrCode = doctor.getQrCode();
        if(null == qrCode){
            //没有二维码，生成一个
            qrCode = doctorDAO.getTicketAndUrlByDoctorId(doctorId);
        }
        if(null != qrCode) {
            info.put("qrCode", qrCode.toString());
        }
        //增加群发记录
        String infoStr = JSONUtils.toString(info);
        MassRoot massRoot = new MassRoot(doctorId,msg,MassRoot.INFORMATION,recordCount,infoStr);
        massRootDAO.save(massRoot);
        int massRootId = massRoot.getId();

        AppContextHolder.getBean("eh.smsPushService", SmsPushService.class).pushMsgData2Ons(massRootId, doctor.getOrgan(), "SendSmsToPatient", "", 0);

        if(CollectionUtils.isNotEmpty(sendPatientIds)){
            //随访会话里发送分享内容放在base 进行发送
            sendShareContent(massRootId, sendPatientIds, doctorId, msg, infoStr);
            sendMsgInfo(massRootId, SHARE_INFORMATION, doctor.getOrgan(), sendPatientIds);
        }
    }

    /**
     * 咨询分享 或者 日间手术 分享
     * @param rootId
     * @param mpiList
     * @param doctorId
     * @param msg
     * @param rootInfo
     */
    public void sendShareContent(Integer rootId, Set<String> mpiList, Integer doctorId, String msg, String rootInfo) {
        FollowChatService followChatService = AppContextHolder.getBean("eh.followChatService", FollowChatService.class);
        for (String mpiId : mpiList) {
            FollowChat followChat = followChatService.createFollowChat(mpiId, doctorId, FollowConstant.FOLLOWCHAT_HASEND, FollowConstant.CHATROLE_SYS, null);
            if (StringUtils.isNotEmpty(msg)) {
                followChatService.addMsg(SystemConstant.ROLES_DOCTOR, SystemConstant.ROLES_PATIENT,
                        mpiId, doctorId, MsgTypeEnum.TEXT, rootId, msg, followChat);
            }
            followChatService.addMsg(SystemConstant.ROLES_DOCTOR, SystemConstant.ROLES_PATIENT,
                    mpiId, doctorId, MsgTypeEnum.DOCTOR_SHARE, rootId, rootInfo, followChat);
        }

    }

    /**
     * 获取群发记录详情
     * @param doctorId 医生ID
     * @param start 下标
     * @param limit 数量限制
     * @return List
     */
    @RpcService
    public List<MassRoot> findAllMassInfoWithPage(Integer doctorId, int start, int limit){
        Assert.notNull(doctorId, "findAllMassInfoWithPage doctorId is null");

        MassRootDAO massRootDAO = DAOFactory.getDAO(MassRootDAO.class);

        PagingInfo pagingInfo = new PagingInfo();
        pagingInfo.setCurrentIndex(start);
        pagingInfo.setLimit(limit);
        List<MassRoot> list = massRootDAO.findAllWithPage(doctorId,pagingInfo);
        if(CollectionUtils.isNotEmpty(list)){
            for(MassRoot massRoot : list){
                if(null != massRoot) {
                    if(null == massRoot.getSendNumber()){
                        massRoot.setSendNumber(0);
                    }

                    //时间转换
                    if(null != massRoot.getCreateTime()) {
                        massRoot.setCreateTimeFormat(DateConversion.convertRequestDateForBuss(massRoot.getCreateTime()));
                    }
                }
            }
        }

        //更新未读数量
        SessionMemberDAO memberDAO = DAOFactory.getDAO(SessionMemberDAO.class);
        memberDAO.updateUnReadByPublisherIdAndMemberType(SystemMsgConstant.SYSTEM_MSG_PUBLISH_TYPE_MASS,
                SystemMsgConstant.SYSTEM_MSG_RECIEVER_TYPE_DOCTOR);

        return list;
    }

    private void sendMsgInfo(Integer id, String bussType, Integer organId,Set<String> sendPatientIds){
        SmsInfo info = new SmsInfo();
        info.setBusId(id);// 业务表主键
        info.setBusType(bussType);// 业务类型
        info.setSmsType(bussType);
        info.setClientId(null);
        //扩展信息里放需要发送的患者ID
        info.setExtendWithoutPersist(JSONUtils.toString(sendPatientIds));
        info.setStatus(0);
        info.setOrganId(organId);//0代表通用机构
        SmsPushService smsPushService = AppContextHolder.getBean("eh.smsPushService", SmsPushService.class);
        smsPushService.pushMsgData2OnsExtendValue(info);
    }

    /**
     *医生收藏患教文章
     *
     * @return
     */
    @RpcService
    public boolean saveInfoCollection(InfoCollection infoCollection,Map<String,String>urlMaps){
        boolean flag=true;
        String shareUrl=JSONUtils.toString(urlMaps,Map.class);
        infoCollection.setShareUrl(shareUrl);
        InfoCollectionDAO dao = DAOFactory.getDAO(InfoCollectionDAO.class);
        InfoCollection info=dao.getByDoctorIdAndInformationId(infoCollection.getDoctorId(),infoCollection.getInformationId());
        if (info!=null){
            return true;
        }
            try {
                dao.save(infoCollection);
            } catch (DAOException e) {
                flag = false;
            }
        return flag;
    }

    /**
     * 根据医生内码查询所收藏的患教文章
     * @param doctorId
     * @return
     */
    @RpcService
    public List<Map<String,Object>>getArticleBydoctorId(Integer doctorId,Integer start,Integer limit){
        List<InfoCollection>collections=DAOFactory.getDAO(InfoCollectionDAO.class).findInformationByDoctorId(doctorId,start,limit);
        List<Map<String,Object>>informations=new ArrayList<>();
        Map<String,Object>map;
        for (InfoCollection collection:collections){
            map=new HashMap<>();
            map.put("id",collection.getInformationId());
            map.put("thumb",collection.getThumb());
            map.put("showtag",collection.getShowTag());
            map.put("title",collection.getTitle());
            map.put("addtime",collection.getAddTime());
            map.put("click",collection.getClick());
            map.put("describe",collection.getDescribes());
            map.put("golink",collection.getGoLink());
            map.put("isvideo",collection.getIsVideo());
            map.put("shareurl",JSONUtils.parse(collection.getShareUrl(),Map.class));
            informations.add(map);
        }
        return informations;
    }

    /**
     * 根据医生内码和资讯Id删除患教文章
     * @param doctorId
     * @param informationId
     * @return
     */
    @RpcService
    public boolean deleteInformation(Integer doctorId,Integer informationId){
        boolean flag=true;
        InfoCollectionDAO dao=DAOFactory.getDAO(InfoCollectionDAO.class);
        try {
            dao.deleteByDoctorIdAndInformationId(doctorId,informationId);
        } catch (Exception e) {
            flag=false;
        }
        return flag;
    }
    /**
     *判断该患教文章是否被收藏
     * @param doctorId
     * @param informationId
     * @return
     */
    @RpcService
    public boolean isOrNotCollect(Integer doctorId, Integer informationId){
        boolean flag = true;
        InfoCollectionDAO dao = DAOFactory.getDAO(InfoCollectionDAO.class);
        InfoCollection info=dao.getByDoctorIdAndInformationId(doctorId,informationId);
        if (info==null){
            flag=false;
        }
        return flag;
    }

}
