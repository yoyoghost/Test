package eh.msg.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcService;
import eh.base.constant.SystemConstant;
import eh.remote.IWXServiceInterface;
import eh.util.Easemob;
import eh.util.RetryTask;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;

public class EasemobIMService {
    private static final Logger log = LoggerFactory.getLogger(EasemobIMService.class);

    private long lastFetchTimestamp;
    private int limit;
    private static final int maxLimit = 1000;

    public EasemobIMService(){
        lastFetchTimestamp = System.currentTimeMillis();
        limit = maxLimit;
    }

    /**
     * 抓取环信聊天记录,并将消息转发给患者公众号
     */
    @RpcService
    public void fetchRecordAndDispatch(){
        ObjectNode node = Easemob.getChatMessages(String.valueOf(lastFetchTimestamp), limit, "");
        if(node.has("entities")){
            ArrayNode arrayNode = (ArrayNode) node.get("entities");
            Iterator<JsonNode> it = arrayNode.iterator();
            while(it.hasNext()){
                ObjectNode objectNode = (ObjectNode) it.next();
                long timestamp = objectNode.path("timestamp").asLong();
                if(timestamp > lastFetchTimestamp){
                    lastFetchTimestamp = timestamp;
                }
                String from = objectNode.path("from").asText();
                if(!from.contains("doctor_")){
                    continue;
                }
                ObjectNode payload = (ObjectNode) objectNode.get("payload");
                if(payload == null || payload.get("bodies") == null){
                    continue;
                }
                ObjectNode ext = (ObjectNode) payload.get("ext");
                if(ext == null){
                    continue;
                }
                if(StringUtils.isEmpty(ext.path("busId").asText()) || StringUtils.isEmpty(ext.path("busType").asText())){
                    continue;
                }
                if(!"2".equals(ext.path("busType").asText())){
                    continue;
                }
                String appId = ext.path("appId").asText();
                String openId = ext.path("openId").asText();
                if(StringUtils.isEmpty(appId) || StringUtils.isEmpty(openId)){
                    continue;
                }
                try {
                    IWXServiceInterface iwxServiceInterface = AppContextHolder.getBean("eh.wxService", IWXServiceInterface.class);
                    iwxServiceInterface.sendMsgToUser(appId, openId, payload.path("bodies").path(0).path("msg").asText());
                } catch (Exception e) {
                   log.error("fetchRecordAndDispatch: "+e);
                }
            }
        }
    }

    @RpcService
    public void sendMsg(String from, String to, String msg, String type, Map<String, String> ext) {
        Easemob.sendSimpleMsg(from, to, msg, type, ext);
    }

    @RpcService
    public void sendMsgToGroup(String from, String groupId, String msg, Map<String, String> ext) {
        Easemob.sendSimpleMsg(from, groupId, msg, "chatgroups", ext);
    }

    @RpcService
    public void sendMsgToGroupByPatientUrt(Integer urt, String groupId, String msg, Map<String, String> ext) {
        final Integer urtF = urt;
        final String groupIdF = groupId;
        final String msgF = msg;
        final Map<String, String> extF = ext;
        RetryTask<Boolean> retryTask = new RetryTask<Boolean>("EasemobIMService") {
            @Override
            public Boolean task() throws Exception {
                ObjectNode resultObjectNode = Easemob.sendSimpleMsg(Easemob.getPatient(urtF), groupIdF, msgF, "chatgroups", extF);
                String resultStr = resultObjectNode.path(groupIdF).asText();
                if(!"success".equals(resultStr)) {
                    throw new Exception("groupIdF=" + groupIdF + ", resultStr=" + resultStr);
                }
                return true;
            }
        };
        Boolean result = retryTask.retryTask();
        log.info("sendMsgToGroupByPatientUrt result[{}], params: urt[{}], groupId[{}], content[{}]", result, urt, groupId, msg);
    }

    @RpcService
    public void sendMsgToGroupByDoctorUrt(Integer urt, String groupId, String msg, Map<String, String> ext) {
        final Integer urtF = urt;
        final String groupIdF = groupId;
        final String msgF = msg;
        final Map<String, String> extF = ext;
        RetryTask<Boolean> retryTask = new RetryTask<Boolean>("EasemobIMService") {
            @Override
            public Boolean task() throws Exception {
                ObjectNode resultObjectNode = Easemob.sendSimpleMsg(Easemob.getDoctor(urtF), groupIdF, msgF, "chatgroups", extF);
                String resultStr = resultObjectNode.path(groupIdF).asText();
                if(!"success".equals(resultStr)) {
                    throw new Exception("groupIdF=" + groupIdF + ", resultStr=" + resultStr);
                }
                return true;
            }
        };
        Boolean result = retryTask.retryTask();
        log.info("sendMsgToGroupByDoctorUrt result[{}], params: urt[{}], groupId[{}], content[{}]", result, urt, groupId, msg);
    }

    @RpcService
    public void registerUser(Integer urt){
        String userName = Easemob.getPatient(urt);
        Easemob.registUser(userName, SystemConstant.EASEMOB_PATIENT_PWD);
    }
}
