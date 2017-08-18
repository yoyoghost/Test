package eh.msg.service;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;


/**
 * 自定义推送activity属性
 * Created by zhangx on 2016/6/29.
 */
public class AtyAttrService {


    /**
     * 获取转诊业务相关的activity属性
     */
    public static HashMap<String,Object> getTransferAtyAttr(Integer busid,String to,Boolean patientRequest,Boolean teams){
        HashMap<String,Object> atyAttr=new HashMap<>();
        atyAttr.put("busid",busid);//业务单号
        atyAttr.put("to",to);//申请方；接收方
        atyAttr.put("teams",teams);//是否团队
        atyAttr.put("patientRequest",patientRequest);//是否患者申请
        return atyAttr;
    }

    /**
     * 获取预约业务相关的activity属性
     */
    public static HashMap<String,Object> getAppointAtyAttr(Integer busid,String to,Boolean patientRequest){
        HashMap<String,Object> atyAttr=new HashMap<>();
        atyAttr.put("busid",busid);//业务单号
        atyAttr.put("to",to);//申请方；接收方
        atyAttr.put("patientRequest",patientRequest);//是否患者申请
        return atyAttr;
    }

    /**
     * 获取咨询业务相关的activity属性
     */
    public static HashMap<String,Object> getConsultAtyAttr(Integer busid,Boolean teams){
        HashMap<String,Object> atyAttr=new HashMap<>();
        atyAttr.put("busid",busid);//业务单号
        atyAttr.put("teams",teams);//是否团队
        return atyAttr;
    }


    /**
     * 获取会诊业务相关的activity属性
     */
    public static HashMap<String,Object> getMeetClinicAtyAttr(Integer busid,Integer resultid,String to,Boolean teams){
        HashMap<String,Object> atyAttr=new HashMap<>();
        if(busid!=null){
            atyAttr.put("busid",busid);//会诊申请单号
        }
        if(resultid!=null){
            atyAttr.put("resultid",resultid);//会诊执行单
        }

        atyAttr.put("to",to);//申请方；接收方
        atyAttr.put("teams",teams);//是否团队
        return atyAttr;
    }

    /**
     * 获取业务设置相关的activity属性
     */
    public static HashMap<String,Object> getConsultSetAtyAttr(Integer doctorId,Boolean teams){
        HashMap<String,Object> atyAttr=new HashMap<>();
        atyAttr.put("doctorId",doctorId);//医生id
        atyAttr.put("teams",teams);//是否团队
        return atyAttr;
    }

    /**
     * 获取医技检查相关的activity属性
     */
    public static HashMap<String,Object> getCheckAtyAttr(Integer busid){
        HashMap<String,Object> atyAttr=new HashMap<>();
        atyAttr.put("busid",busid);//业务单号
        return atyAttr;
    }

    /**
     * 获取处方相关的activity属性
     */
    public static HashMap<String,Object> getRecipeAtyAttr(Integer busid){
        HashMap<String,Object> atyAttr=new HashMap<>();
        atyAttr.put("busid",busid);//业务单号
        return atyAttr;
    }

    /**
     * 获取评价相关的activity属性
     */
    public static HashMap<String,Object> getEvaluationAtyAttr(Integer busid){
        HashMap<String,Object> atyAttr=new HashMap<>();
        atyAttr.put("busid",busid);//业务单号
        return atyAttr;
    }

    public static HashMap<String,Object> getPatientAtyAttr(String mpiId){
        HashMap<String,Object> atyAttr=new HashMap<>();
        atyAttr.put("mpiId",mpiId);//患者编号
        return atyAttr;
    }



    /**
     * 获取url的activity属性
     * @param url url
     */
    public static HashMap<String,Object> getUrlAtyAttr(String url){
        HashMap<String,Object> atyAttr=new HashMap<>();
        atyAttr.put("url",url);
        return atyAttr;
    }

    /**
     * 获取视频推送的activity属性
     * @param name
     * @param photo
     * @param roomId
     * @param pwd
     * @return
     */
    public static HashMap<String, Object> getVideoCallAtyAttr(String name, Integer photo, String roomId, String pwd) {
        HashMap<String, Object> attr = new HashMap<String, Object>();
        attr.put("callName", name);
        attr.put("callPhoto", photo);
        attr.put("videoCallId", roomId);
        if (!StringUtils.isEmpty(pwd)) {
            attr.put("password", pwd);
        }
        return attr;
    }

}
