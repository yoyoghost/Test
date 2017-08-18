package eh.msg.dao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import eh.entity.msg.Message;
import eh.util.Easemob;
import eh.utils.DateConversion;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Date;

/**
 * 聊天记录
 *
 * @author ZX
 */
public abstract class MessageDAO extends HibernateSupportDelegateDAO<Message> {

    private static final Log logger = LogFactory.getLog(MessageDAO.class);

    public MessageDAO() {
        super();
        this.setEntityName(Message.class.getName());
        this.setKeyField("messageId");
    }

    /**
     * 根据主键获取消息记录
     *
     * @param id
     * @return
     * @author ZX
     * @date 2015-6-26  下午1:34:45
     */
    @RpcService
    @DAOMethod
    public abstract Message getByMessageId(int id);

    /**
     * 保存消息记录
     *
     * @param message
     * @return
     * @author ZX
     * @date 2015-6-26  下午1:47:21
     */
    @RpcService
    public void addMessage(Message message) throws DAOException {
        if (StringUtils.isEmpty(message.getMessageId())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "messageId is required");
        }
        if (StringUtils.isEmpty(message.getFromUser())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "fromUser is required");
        }
        if (StringUtils.isEmpty(message.getToUser())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "toUser is required");
        }
        if (message.getMessageTime() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "messageTime is required");
        }
        if (message.getIsAcked() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "isAcked is required");
        }
        if (message.getIsDelivered() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "isDelivered is required");
        }
        if (message.getIsRead() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "isRead is required");
        }
        if (message.getIsGroup() == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "isGroup is required");
        }
        if (StringUtils.isEmpty(message.getMessageType())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "messageType is required");
        }

        save(message);
    }


    /**
     * 保存聊天记录(从环信服务器上获取)
     *
     * @param
     * @author ZX
     * @date 2015-6-18  下午9:25:06
     */
    @RpcService
    public void saveChatMessage(Date startTime, int limit) {
        String startTime1 = String.valueOf(startTime.getTime());
        ObjectNode messages = Easemob.getChatMessages(startTime1, limit, null);

        Integer count = Integer.parseInt(messages.get("count").toString());
        while (count > 0) {
            Iterable<JsonNode> entities = messages.get("entities");
            for (JsonNode jsonNode : entities) {
                saveMessage(jsonNode);
            }

            //获取游标，准备获取下一页数据
            JsonNode cursor = messages.get("cursor");
            if (cursor == null) {
                break;
            }
            messages = Easemob.getChatMessages(startTime1, limit, cursor.asText());
            String countString = messages.get("count").toString();
            if (!StringUtils.isEmpty(countString)) {
                count = Integer.parseInt(countString);
            } else {
                break;
            }
        }
    }

    /**
     * 每条数据获取类型进行保存
     *
     * @param jsonNode
     * @return
     * @author ZX
     * @date 2015-7-7  下午12:04:18
     */
    private boolean saveMessage(JsonNode jsonNode) {
        Message msg = new Message();

        msg.setIsAcked(0);
        msg.setIsDelivered(0);
        msg.setIsRead(0);

        //谁发起的
        String from = jsonNode.get("from").asText();
        msg.setFromUser(from);

        //发送给谁
        String to = jsonNode.get("to").asText();
        msg.setToUser(to);

        //信息号
        String msg_id = jsonNode.get("msg_id").asText();
        msg.setMessageId(msg_id);

        //是否群聊
        String chat_type = jsonNode.get("chat_type").asText();
        if (chat_type.equals("groupchat")) {
            msg.setIsGroup(1);
        } else {
            msg.setIsGroup(0);
        }

        //消息发送时间
        String timestamp = jsonNode.get("timestamp").asText();
        msg.setMessageTime(Long.parseLong(timestamp));

        Iterable<JsonNode> bodies = jsonNode.get("payload").get("bodies");
        for (JsonNode body : bodies) {
            // 消息类型
            String type = body.get("type").asText();
            msg.setMessageType(type);

            switch (type) {
                case "txt":
                    String txtMsg = body.get("msg").asText();
                    msg.setText(txtMsg);
                    break;
                case "img":
                    String url = body.get("url").asText();
                    msg.setText(url);
                    String secretKey = body.get("secret").asText();
                    msg.setSecretKey(secretKey);
                    String displayName = body.get("filename").asText();
                    msg.setDisplayName(displayName);
                    if (body.get("thumb") != null) {
                        String thumbnailRemotePath = body.get("thumb").asText();
                        msg.setThumbnailRemotePath(thumbnailRemotePath);
                        String thumbnailSecretKey = body.get("thumb_secret").asText();
                        msg.setThumbnailSecretKey(thumbnailSecretKey);
                    }
                    break;
                case "loc":
                    String address = body.get("addr").asText();
                    msg.setAddress(address);
                    String latitude = body.get("lat").asText();
                    msg.setLatitude(latitude);
                    String longitute = body.get("lng").asText();
                    msg.setLongitute(longitute);
                    break;
                case "audio":
                    String aURL = body.get("url").asText();
                    msg.setText(aURL);
                    String aDisplayName = body.get("filename").asText();
                    msg.setDisplayName(aDisplayName);
                    Integer length = body.get("length").asInt();
                    msg.setLength(length);
                    String aSecretKey = body.get("secret").asText();
                    msg.setSecretKey(aSecretKey);
                    break;
                case "video":
                    String vDisplayName = body.get("filename").asText();
                    msg.setDisplayName(vDisplayName);
                    String vThumbnailRemotePath = body.get("thumb").asText();
                    msg.setThumbnailRemotePath(vThumbnailRemotePath);
                    Integer vLength = body.get("length").asInt();
                    msg.setLength(vLength);
                    String vSecretKey = body.get("secret").asText();
                    msg.setSecretKey(vSecretKey);
                    Integer file_length = body.get("file_length").asInt();
                    msg.setLength(file_length);
                    String vthumbnailSecretKey = body.get("thumb_secret").asText();
                    msg.setThumbnailSecretKey(vthumbnailSecretKey);
                    String vURL = body.get("url").asText();
                    msg.setText(vURL);
                    break;
                default:
                    break;
            }
            try {
                save(msg);
            } catch (Exception e) {
                logger.error("saveMessage "+e.getMessage()+"["+ JSONUtils.toString(msg)+"]");
                continue;
            }
        }

        return true;
    }

    /**
     * 保存聊天记录(定时服务）
     * <p>
     * 原saveChatMessage
     *
     * @author luf
     * @date 2016-4-18
     */
    @RpcService
    public void saveChatMessageAfterPull() {
        String startTime = String.valueOf(this.getMaxTimeFromMessage());
        if (startTime == null || startTime.equals("null") || startTime.equals("0")) {
            startTime = DateConversion.getDateFormatter(DateConversion.getCurrentDate("2015-01-01",
                    "yyyy-MM-dd"), "yyyy-MM-dd");
        }
        int limit = 100;

        ObjectNode messages = Easemob.getChatMessages(startTime, limit, null);
        if(null == messages){
            return;
        }

        JsonNode countNode = messages.get("count");
        if(null != countNode) {
            int count = countNode.asInt();
            while (count > 0) {
                logger.info("saveChatMessageAfterPull count:"+count);
                Iterable<JsonNode> entities = messages.get("entities");
                if(null != entities) {
                    for (JsonNode jsonNode : entities) {
                        if(null != jsonNode) {
                            saveMessage(jsonNode);
                        }
                    }
                }

                //获取游标，准备获取下一页数据
                JsonNode cursor = messages.get("cursor");
                if (cursor == null) {
                    break;
                }

                messages = Easemob.getChatMessages(startTime, limit, cursor.asText());
                if(null != messages) {
                    countNode = messages.get("count");
                    if(null != countNode) {
                        count = countNode.asInt();
                    }else {
                        break;
                    }
                }else {
                    break;
                }
            }
        }
    }

    /**
     * 获取当前最大时间（供saveChatMessageAfterPull调用）
     *
     * @return
     */
    @DAOMethod(sql = "SELECT MAX(messageTime) FROM Message")
    public abstract Long getMaxTimeFromMessage();
}
