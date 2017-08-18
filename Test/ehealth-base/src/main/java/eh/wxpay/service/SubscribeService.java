package eh.wxpay.service;

import ctd.persistence.DAOFactory;
import ctd.util.annotation.RpcService;
import eh.base.dao.DoctorDAO;
import eh.bus.dao.ConsultSetDAO;
import eh.bus.service.common.CurrentUserInfo;
import eh.entity.base.Doctor;
import eh.entity.bus.ConsultSet;
import eh.entity.bus.msg.SimpleWxAccount;
import eh.entity.wx.WxSubscribe;
import eh.wxpay.dao.WxSubscribeDAO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SubscribeService {

    private WxSubscribeDAO subDao=DAOFactory.getDAO(WxSubscribeDAO.class);

    /**
     * sms使用
     * @return
     */
    @RpcService
    public List<WxSubscribe> findByOpenId(String openId,long start,int limit){
        return subDao.findByOpenId(openId,start,limit);
    }

    /**
     * 获取微信公众号上扫码关注且未真正关注的数据
     * @param openId
     * @return
     */
    @RpcService
    public List<HashMap<String, Object>> findSubscribeDoctors(){
        ConsultSetDAO setDao = DAOFactory.getDAO(ConsultSetDAO.class);
        DoctorDAO doctorDAO=DAOFactory.getDAO(DoctorDAO.class);

        String openId="";
        SimpleWxAccount simpleWxAccount = CurrentUserInfo.getSimpleWxAccount();
        if(null != simpleWxAccount) {
            openId = simpleWxAccount.getOpenId();
        }
        List<WxSubscribe> list=subDao.findSubscribesByOpenId(openId,0,10);
        List<HashMap<String, Object>> targets = new ArrayList<HashMap<String, Object>>();
        for (WxSubscribe sub : list) {
            int doctorId = sub.getDoctorId();

            HashMap<String, Object> result = new HashMap<String, Object>();
            Doctor doctor=doctorDAO.get(doctorId);
            ConsultSet consultSet = setDao.get(doctorId);

            result.put("doctor", doctor);
            result.put("consultSet", consultSet);
            targets.add(result);
        }
        return targets;
    }




}
