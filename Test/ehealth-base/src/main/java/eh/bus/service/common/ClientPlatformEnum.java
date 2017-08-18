package eh.bus.service.common;

import com.alibaba.fastjson.JSONObject;
import ctd.account.Client;
import ctd.persistence.exception.DAOException;
import eh.entity.base.Device;
import eh.entity.bus.msg.SimpleThird;
import eh.entity.bus.msg.SimpleWxAccount;
import eh.utils.LocalStringUtil;
import eh.utils.ValidateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Created by Administrator on 2016/7/15 0015.
 * 客户端设备id枚举类，改枚举类型与base_device表的os字段值对应
 */
public enum ClientPlatformEnum {
    WEIXIN(1, "WX", "wechat") {
        @Override
        public SimpleWxAccount parsePlatformInfoFromDevice(Device device) {
            if (device == null) {
                return null;
            }
            if (ValidateUtil.notBlankString(device.getStatus()) && device.getStatus().equals("1")) {
                if (this.getKey().equalsIgnoreCase(device.getOs())) {
                    SimpleWxAccount wx = new SimpleWxAccount();
                    String token = device.getToken();
                    String[] strings = token.split("@");
                    wx.setOpenId(strings[0]);
                    wx.setAppId(strings[1]);
                    return wx;
                }
            }
            return null;
        }

        @Override
        public Object parsePlatformInfoFromClient(Client client) {
            if (client == null || ValidateUtil.blankString(client.getOs())) {
                return null;
            }
            if (this.getKey().equalsIgnoreCase(client.getOs())) {
                SimpleWxAccount wx = new SimpleWxAccount();
                String token = client.getToken();
                String[] strings = token.split("@");
                wx.setOpenId(strings[0]);
                wx.setAppId(strings[1]);
                return wx;
            }
            return null;
        }
    },
    ZHIFUBAO(2, "Alipay", "alipay"),
    ANDROID(3, "Android", ""),
    IOS(4, "IOS", ""),
    PC(5, "PC", ""),
    XIAOYU(6, "XiaoYu", ""),
    WEB(7, "WEB", "thirdparty"){
        @Override
        public SimpleWxAccount parsePlatformInfoFromDevice(Device device) {
            if (device == null) {
                return null;
            }
            if (this.getKey().equalsIgnoreCase(device.getOs())) {
                SimpleWxAccount wx = new SimpleWxAccount();
                String token = device.getToken();
                String[] strings = token.split("@");
                wx.setOpenId(strings[0]);
                wx.setAppId(strings[1]);
                return wx;
            }
            return null;
        }

        @Override
        public Object parsePlatformInfoFromClient(Client client) {
            if (client == null || ValidateUtil.blankString(client.getOs())) {
                return null;
            }
            if (this.getKey().equalsIgnoreCase(client.getOs())) {
                SimpleWxAccount wx = new SimpleWxAccount();
                String token = client.getToken();
                String[] strings = token.split("@");
                wx.setOpenId(strings[0]);
                wx.setAppId(strings[1]);
                return wx;
            }
            return null;
        }
    },
    WX_WEB(8, "WX_WEB", "thirdparty"){

        @Override
        public SimpleThird parsePlatformInfoFromClient(Client client) {
            if (client == null || ValidateUtil.blankString(client.getOs())) {
                return null;
            }
            if (this.getKey().equalsIgnoreCase(client.getOs())) {
                SimpleThird third = new SimpleThird();
                String token = client.getToken();
                String[] strings = token.split("@");
                third.setTid(strings[0]);
                third.setAppkey(strings[1]);
                return third;
            }
            return null;
        }
    },
    ALILIFE(9, "ALILIFE", "alipay") {
        @Override
        public SimpleWxAccount parsePlatformInfoFromDevice(Device device) {
            if (device == null) {
                return null;
            }
            if (ValidateUtil.notBlankString(device.getStatus()) && device.getStatus().equals("1")) {
                if (this.getKey().equalsIgnoreCase(device.getOs())) {
                    SimpleWxAccount wx = new SimpleWxAccount();
                    String token = device.getToken();
                    String[] strings = token.split("@");
                    wx.setOpenId(strings[0]);
                    wx.setAppId(strings[1]);
                    return wx;
                }
            }
            return null;
        }

        @Override
        public Object parsePlatformInfoFromClient(Client client) {
            if (client == null || ValidateUtil.blankString(client.getOs())) {
                return null;
            }
            if (this.getKey().equalsIgnoreCase(client.getOs())) {
                SimpleWxAccount wx = new SimpleWxAccount();
                String token = client.getToken();
                String[] strings = token.split("@");
                wx.setOpenId(strings[0]);
                wx.setAppId(strings[1]);
                return wx;
            }
            return null;
        }
    };


    public Object parsePlatformInfoFromDevice(Device device) {
        return null;
    }

    public Object parsePlatformInfoFromClient(Client client) {
        return null;
    }

    /**
     * 根据client的os类型自动适配对应的clientPlatformEnum对象
     *
     * @param client
     * @return
     */
    public static ClientPlatformEnum adapterPlatForm(Client client) {
        if (client == null || ValidateUtil.blankString(client.getOs())) {
            log.error("ClientPlatformEnum adapterPlatForm null, client[{}]", JSONObject.toJSONString(client));
            return null;
        }
        try {
            return fromKey(client.getOs());
        } catch (Exception e) {
            log.error("ClientPlatformEnum adapterPlatForm exception, client[{}], errorMessage[{}], stackTrace[{}]", JSONObject.toJSONString(client), e.getMessage(), JSONObject.toJSONString(e.getStackTrace()));
            return null;
        }
    }

    private int id;
    private String key;
    private String opPlatKey;  //对应运营平台的PlatformType.dic里面的key值
    private static final Logger log = LoggerFactory.getLogger(ClientPlatformEnum.class);

    ClientPlatformEnum(int id, String key, String opPlatKey) {
        this.id = id;
        this.key = key;
        this.opPlatKey = opPlatKey;
    }

    /**
     * 根据key获取clientPlatformEnum枚举类型
     *
     * @param key
     * @return
     * @throws Exception
     */
    public static ClientPlatformEnum fromKey(String key) throws DAOException {
        if (key == null || "".equals(key.trim())) {
            throw new DAOException(LocalStringUtil.format("parameter illegal exception! Parameter[key={}]", key));
        }
        for (ClientPlatformEnum en : ClientPlatformEnum.values()) {
            if (en.key.equalsIgnoreCase(key)) {
                return en;
            }
        }
        String errorMessage = LocalStringUtil.format("no responding result! Parameter[key={}]", key);
        log.error(errorMessage);
        throw new DAOException(errorMessage);
    }

    public String getKey() {
        return key;
    }

    public String getOpPlatKey(){
        return this.opPlatKey;
    }
}
