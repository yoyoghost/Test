package eh.base.service;

import ctd.persistence.DAOFactory;
import ctd.util.annotation.RpcService;
import eh.base.dao.DoctorDAO;
import eh.base.dao.OrganConfigDAO;
import eh.entity.base.Doctor;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;


public class UrlResourceService {
    /**
     * url资源,配置在spring.xml
     */
    private static HashMap<String,String> urls=new HashMap<>();

    @RpcService
    public HashMap<String, String> getUrls() {
        return urls;
    }

    @RpcService
    public void setUrls(HashMap<String, String> urls) {
        UrlResourceService.urls = urls;
    }

    public static String getUrlByName(String name){
        if(urls.containsKey(name)){
            return urls.get(name);
        }else{
            return "";
        }
    }

    @RpcService
    public String getUrlByParam(String name){
        if(urls.containsKey(name)){
            return urls.get(name);
        }else{
            return "";
        }
    }

    /**
     * 访问视频教学服务地址
     * @param doctorId
     * @return
     */
    @RpcService
    public String getTeachUrl(int doctorId) {

        DoctorDAO dao = DAOFactory.getDAO(DoctorDAO.class);
        String url = getUrlByName("teachUrl");
        Doctor doctor = dao.getByDoctorId(doctorId);
        if (doctor == null) {
            return url;
        }
        if (doctor.getOrgan() != null) {
            String teachUrl = DAOFactory.getDAO(OrganConfigDAO.class).getTeachUrlByOrganId(doctor.getOrgan());
            if (!StringUtils.isEmpty(teachUrl)) {
                url = teachUrl;
            }
        }
        if (url.contains("appLogin/id")) {
            return url + doctor.getMobile();
        } else {
            return url;
        }

    }

    @Override
    public String toString() {
        return "UrlResourceService{" +
                "urls=" + urls +
                '}';
    }
}
