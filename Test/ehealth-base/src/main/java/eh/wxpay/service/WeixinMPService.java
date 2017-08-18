package eh.wxpay.service;

import com.alibaba.druid.util.StringUtils;
import ctd.mvc.weixin.entity.OAuthWeixinMP;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.BeanUtils;
import ctd.util.annotation.RpcService;
import eh.entity.wx.WXConfig;
import eh.wxpay.dao.WeixinMPDAO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WeixinMPService {

    @RpcService
    public List<Map> findMapByLoginId(String userId) {
        if (StringUtils.isEmpty(userId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "userId is require");
        }
        List<Object> objs = DAOFactory.getDAO(WeixinMPDAO.class).findObjByUserId(userId);

        List<Map> maps = new ArrayList<Map>();
        for (Object object : objs) {
            Object[] objects = (Object[]) object;
            OAuthWeixinMP mp = (OAuthWeixinMP) objects[0];
            WXConfig wxConfig = (WXConfig) objects[1];
            Map<String, Object> map = new HashMap<String, Object>();
            BeanUtils.map(mp, map);
            map.put("wxName", wxConfig.getWxName());
            maps.add(map);
        }
        return maps;
    }


}
